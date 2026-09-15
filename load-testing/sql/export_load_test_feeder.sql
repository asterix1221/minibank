-- Exports the Gatling feeder CSV for the most recently seeded batch of :n LOADTEST-*
-- clients (see seed_load_test_data.sql).
--
-- Deliberately does NOT use \copy: writing to a file path (even /dev/stdout) inside the
-- postgres container turned out to be unreliable to read back across separate `docker exec`
-- invocations on some Docker Desktop backends. Instead this is a plain SELECT, and the
-- caller (seed-test-data.sh / seed-test-data.ps1) runs psql with the standard `--csv` flag,
-- which simply formats this query's normal result set as CSV on psql's own STDOUT - the
-- same STDOUT that `docker exec` already forwards back. Nothing is written inside the
-- container at all.
--
-- Usage:
--   psql -h localhost -U minibank -d minibank --csv -v n=200 -f export_load_test_feeder.sql > clients.csv

WITH recent_clients AS (
    SELECT c.id AS client_id, c.phone, c.created_at
    FROM clients c
    WHERE c.passport_data LIKE 'LOADTEST-%'
    ORDER BY c.created_at DESC, c.id DESC
    LIMIT :n
),
recent AS (
    SELECT rc.phone, a.id AS account_id, a.account_number, rc.created_at, rc.client_id
    FROM recent_clients rc
    JOIN accounts a ON a.client_id = rc.client_id
),
numbered AS (
    SELECT recent.*,
           row_number() OVER (ORDER BY created_at, client_id) AS rn,
           count(*) OVER () AS total
    FROM recent
)
SELECT n1.phone           AS "phone",
       n1.account_id      AS "accountId",
       n1.account_number  AS "accountNumber",
       n2.account_number  AS "toAccountNumber"
FROM numbered n1
JOIN numbered n2 ON n2.rn = (n1.rn % n1.total) + 1
ORDER BY n1.rn;
