#!/usr/bin/env python3
"""
Gatling -> Prometheus exporter, take 2.

WHY THIS EXISTS (read this before touching parsing logic):
Gatling's OSS "graphite" DataWriter - the officially supported way to stream
live metrics out of a running simulation - was removed in Gatling 3.12 along
with the Akka dependency. Real-time streaming metrics are now an Enterprise
Edition-only feature. See gatling.conf in this project for the same note.

The first version of this exporter worked around that by tailing
simulation.log as plain, tab-separated text (USER/REQUEST lines). That
assumption was wrong for this Gatling version (3.14.3): simulation.log is now
a compact, undocumented BINARY format (confirmed by hexdump - the file starts
with garbage bytes, not readable "RUN ..." text). Trying to parse that binary
format by reverse-engineering it byte-by-byte is fragile and could silently
produce wrong numbers on the next Gatling upgrade.

So instead: after each run, Gatling itself writes the same JSON/JS files its
own HTML report renders from - target/gatling/<run>/js/global_stats.json
(pure JSON) and target/gatling/<run>/js/stats.js (a "var stats = {...};"
wrapper around JSON, per-request breakdown). Those are documented, stable,
and exactly what the official report uses - so we read those instead.

TRADE-OFF: this means metrics update once per completed run (as soon as
Gatling finishes writing the report), not continuously every few seconds
while the test is running. That's an inherent OSS limitation, not a bug here.
"""
import argparse
import http.server
import json
import re
import threading
import time
from pathlib import Path

STATS_VAR_RE = re.compile(r"var\s+stats\s*=\s*(\{.*\});?\s*$", re.DOTALL)

# global_stats.json / per-request "stats" blocks share this shape:
#   { "total": <num>, "ok": <num>, "ko": <num> }   (or "-" when not applicable)
PERCENTILE_LABELS = {
    "percentiles1": "p50",
    "percentiles2": "p75",
    "percentiles3": "p95",
    "percentiles4": "p99",
}


def _num(block, key="total"):
    if not isinstance(block, dict):
        return None
    v = block.get(key)
    if v in (None, "-", ""):
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


class State:
    def __init__(self):
        self.lock = threading.Lock()
        self.run_dir = None
        self.run_name = None
        self.run_mtime = 0.0
        self.parsed_at = 0.0
        self.rows = []  # list of dicts: {request, total, ok, ko, min, max, mean, p50, p75, p95, p99, rps}


state = State()


def find_latest_run_dir(gatling_root: Path):
    if not gatling_root.exists():
        return None
    candidates = [d for d in gatling_root.iterdir() if d.is_dir()]
    if not candidates:
        return None
    return max(candidates, key=lambda d: d.stat().st_mtime)


def parse_block(name, stats):
    row = {"request": name}
    row["total"] = _num(stats.get("numberOfRequests", {}), "total") or 0
    row["ok"] = _num(stats.get("numberOfRequests", {}), "ok") or 0
    row["ko"] = _num(stats.get("numberOfRequests", {}), "ko") or 0
    row["min"] = _num(stats.get("minResponseTime", {}))
    row["max"] = _num(stats.get("maxResponseTime", {}))
    row["mean"] = _num(stats.get("meanResponseTime", {}))
    for src, label in PERCENTILE_LABELS.items():
        row[label] = _num(stats.get(src, {}))
    row["rps"] = _num(stats.get("meanNumberOfRequestsPerSecond", {}))
    return row


def walk_contents(node, out):
    """Recursively collect leaf REQUEST rows from stats.js's 'contents' tree."""
    if not isinstance(node, dict):
        return
    if node.get("type") == "REQUEST" and "stats" in node:
        out.append(parse_block(node.get("name", "unknown"), node["stats"]))
    for child in (node.get("contents") or {}).values():
        walk_contents(child, out)


def try_parse_run(run_dir: Path):
    global_stats_path = run_dir / "js" / "global_stats.json"
    stats_js_path = run_dir / "js" / "stats.js"
    if not global_stats_path.exists() or not stats_js_path.exists():
        return None  # report not fully written yet

    global_raw = global_stats_path.read_text(encoding="utf-8")
    global_json = json.loads(global_raw)  # raises if incomplete/mid-write -> caller retries later

    stats_raw = stats_js_path.read_text(encoding="utf-8")
    m = STATS_VAR_RE.search(stats_raw)
    if not m:
        return None
    stats_json = json.loads(m.group(1))

    rows = [parse_block("ALL", global_json)]
    walk_contents(stats_json, rows)
    return rows


def poll_loop(gatling_root: Path, poll_interval: float):
    print(f"[gatling-exporter] watching {gatling_root} for completed runs (polling every {poll_interval}s)")
    while True:
        try:
            latest = find_latest_run_dir(gatling_root)
            if latest is not None:
                mtime = latest.stat().st_mtime
                with state.lock:
                    is_new = latest != state.run_dir or mtime != state.run_mtime
                if is_new:
                    rows = try_parse_run(latest)
                    if rows is not None:
                        with state.lock:
                            state.run_dir = latest
                            state.run_name = latest.name
                            state.run_mtime = mtime
                            state.rows = rows
                            state.parsed_at = time.time()
                        print(f"[gatling-exporter] parsed results from {latest.name} "
                              f"({len(rows)} request rows)")
        except (json.JSONDecodeError, OSError):
            # Report is still being written (partial JSON) - just retry next tick.
            pass
        time.sleep(poll_interval)


def render_metrics():
    lines = [
        "# HELP gatling_exporter_up Whether the exporter is running and watching target/gatling",
        "# TYPE gatling_exporter_up gauge",
        "gatling_exporter_up 1",
    ]
    with state.lock:
        run_name = state.run_name
        parsed_at = state.parsed_at
        rows = list(state.rows)

    if run_name:
        lines.append('# HELP gatling_last_run_info Metadata about the most recently parsed run (value is always 1)')
        lines.append('# TYPE gatling_last_run_info gauge')
        lines.append(f'gatling_last_run_info{{run="{run_name}"}} 1')
        lines.append('# HELP gatling_last_run_parsed_timestamp_seconds Unix time when results were last (re)parsed')
        lines.append('# TYPE gatling_last_run_parsed_timestamp_seconds gauge')
        lines.append(f'gatling_last_run_parsed_timestamp_seconds {parsed_at:.3f}')

    if rows:
        lines.append("# HELP gatling_requests_total Requests in the last completed run, by request name and status")
        lines.append("# TYPE gatling_requests_total gauge")
        for r in rows:
            req = r["request"].replace('"', '\\"')
            lines.append(f'gatling_requests_total{{request="{req}",status="ok"}} {r["ok"]}')
            lines.append(f'gatling_requests_total{{request="{req}",status="ko"}} {r["ko"]}')

        lines.append("# HELP gatling_error_ratio Share of failed requests in the last completed run (0-1), by request name")
        lines.append("# TYPE gatling_error_ratio gauge")
        for r in rows:
            req = r["request"].replace('"', '\\"')
            ratio = (r["ko"] / r["total"]) if r["total"] else 0
            lines.append(f'gatling_error_ratio{{request="{req}"}} {ratio:.6f}')

        lines.append("# HELP gatling_response_time_ms Response time stats in ms from the last completed run, by request name and stat")
        lines.append("# TYPE gatling_response_time_ms gauge")
        for r in rows:
            req = r["request"].replace('"', '\\"')
            for stat in ("min", "max", "mean", "p50", "p75", "p95", "p99"):
                v = r.get(stat)
                if v is not None:
                    lines.append(f'gatling_response_time_ms{{request="{req}",stat="{stat}"}} {v}')

        lines.append("# HELP gatling_mean_requests_per_second Mean throughput of the last completed run, by request name")
        lines.append("# TYPE gatling_mean_requests_per_second gauge")
        for r in rows:
            if r.get("rps") is not None:
                req = r["request"].replace('"', '\\"')
                lines.append(f'gatling_mean_requests_per_second{{request="{req}"}} {r["rps"]}')

    return "\n".join(lines) + "\n"


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?", 1)[0] != "/metrics":
            self.send_response(404)
            self.end_headers()
            return
        body = render_metrics().encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        pass  # keep stdout free for our own [gatling-exporter] lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=9302)
    ap.add_argument("--gatling-root", default="target/gatling")
    ap.add_argument("--poll-interval", type=float, default=5.0)
    args = ap.parse_args()

    gatling_root = Path(args.gatling_root)
    t = threading.Thread(target=poll_loop, args=(gatling_root, args.poll_interval), daemon=True)
    t.start()

    server = http.server.ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"[gatling-exporter] serving /metrics on :{args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
