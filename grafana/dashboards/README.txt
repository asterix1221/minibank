gatling-custom.json is auto-provisioned (see grafana/provisioning/dashboards/dashboard.yml) -
this is a hand-built dashboard, not downloaded, since no ready-made Grafana dashboard for
Gatling+Prometheus exists on grafana.com (only InfluxDB-based ones do). See README "Stage 3 -
Part 3" for details.

The three Stage 2 dashboards (ids 4701, 12900, 9628) are still NOT here - the build
environment used to write Stage 2 had no network access to grafana.com to download their
JSON. See the main README's "Grafana dashboards" section for the manual-import steps.
If you add them here later (e.g. 4701-jvm-micrometer.json), the provider already active
in grafana/provisioning/dashboards/dashboard.yml will pick them up automatically on the
next Grafana restart / updateIntervalSeconds poll - no other changes needed.
