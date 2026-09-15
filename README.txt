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

IMPORTANT when adding grafana.com dashboards (4701, 12900, 9628, etc.) here:
JSON files downloaded from grafana.com reference their datasource via a template
variable "${DS_PROMETHEUS}" (or a hardcoded UID from the dashboard author's own Grafana
instance). That variable only gets resolved if you import through the Grafana UI's
"+ Import" screen AND actually pick "Prometheus" in the datasource dropdown before
clicking Import. If you instead drop the raw downloaded JSON straight into this folder
(file-based provisioning, like we do for gatling-custom.json), that variable is never
resolved, and every panel shows "Datasource ${DS_PROMETHEUS} was not found".

Fix: run the downloaded JSON through ../fix-dashboard-datasource.py before placing it
here. It rewrites every datasource reference (both the "${DS_PROMETHEUS}" template var
and any hardcoded author UID) to the plain string "Prometheus" - the same pattern
gatling-custom.json already uses, which matches `name: Prometheus` in
grafana/provisioning/datasources/datasource.yml:

    python3 grafana/fix-dashboard-datasource.py grafana/dashboards/4701-jvm-micrometer.json

Then restart the grafana container (or wait up to 30s for the provider's
updateIntervalSeconds poll) and the dashboard will render correctly with no manual
datasource selection needed - ever, even after `docker compose down -v`.
