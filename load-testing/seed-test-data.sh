#!/usr/bin/env bash
# Seeds N test clients+accounts (directly via SQL, see seed_load_test_data.sql for why)
# and writes the Gatling feeder CSV to src/test/resources/data/clients.csv.
#
# Usage:
#   ./seed-test-data.sh [N]
#   N defaults to 200.
#
# DB connection is read from env vars (same names used by docker-compose/app):
#   DB_HOST (default localhost), DB_PORT (default 5432), DB_NAME (default minibank),
#   DB_USER (default minibank), DB_PASSWORD (default minibank)
#
# Requires the `psql` client on PATH.
#
# Idempotent: safe to re-run - each run just adds another batch of N clients/accounts on
# top of whatever is already seeded (see seed_load_test_data.sql), and the CSV feeder is
# always re-exported fresh for whichever batch was seeded most recently.

set -euo pipefail

N="${1:-200}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-minibank}"
DB_USER="${DB_USER:-minibank}"
export PGPASSWORD="${DB_PASSWORD:-minibank}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$PROJECT_ROOT/src/test/resources/data"
OUT_FILE="$OUT_DIR/clients.csv"

mkdir -p "$OUT_DIR"

if ! command -v psql >/dev/null 2>&1; then
    echo "psql not found on PATH. Install the postgresql-client package, or run this" >&2
    echo "against the containerized postgres via: docker compose exec -T postgres psql ..." >&2
    exit 1
fi

echo "Seeding $N test clients into $DB_NAME@$DB_HOST:$DB_PORT ..."

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 \
    -v n="$N" \
    -f "$SCRIPT_DIR/sql/seed_load_test_data.sql"

echo "Clients and accounts seeded. Exporting CSV for the Gatling feeder..."

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --csv -q \
    -v ON_ERROR_STOP=1 \
    -v n="$N" \
    -f "$SCRIPT_DIR/sql/export_load_test_feeder.sql" > "$OUT_FILE"

LINES=$(($(wc -l < "$OUT_FILE") - 1))
echo "Done. Wrote $LINES rows to $OUT_FILE"
