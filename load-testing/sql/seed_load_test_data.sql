-- Seeds N test clients + accounts directly into the DB (bypasses the registration API -
-- see README "Stage 3 - test data seeding" for why: seeding through the real API would
-- mix registration load into the transfer load test and make results uninterpretable).
--
-- These rows do NOT go through RegisterRequest's Bean Validation (@Pattern on phone etc.) -
-- they are constructed to already match that format, but that was never actually checked
-- by the application for this data. Documented explicitly per the spec's own requirement.
--
-- Idempotent by design: instead of always starting the sequence at 1 (which collided with
-- uq_clients_phone / uq_accounts_account_number on every re-run), it continues from
-- MAX(existing LOADTEST seq) + 1. Re-running this script just adds another batch of N
-- clients/accounts on top of whatever is already there - it never fails on a second run.
--
-- This script ONLY seeds data. The CSV feeder for Gatling is a separate, later step - see
-- export_load_test_feeder.sql - which reads back whatever the most recently seeded batch
-- is; it does not need to run in the same psql session as this file.
--
-- Usage:
--   psql -h localhost -U minibank -d minibank -v n=200 -f seed_load_test_data.sql
-- (the wrapping seed-test-data.sh / seed-test-data.ps1 scripts do this for you)

BEGIN;

CREATE TEMP TABLE seed_clients AS
WITH existing AS (
    SELECT coalesce(max(substring(passport_data from 10)::bigint), 0) AS max_seq
    FROM clients
    WHERE passport_data LIKE 'LOADTEST-%'
)
SELECT
    gen_random_uuid()                                          AS client_id,
    existing.max_seq + gs                                      AS seq,
    '+7900' || lpad((existing.max_seq + gs)::text, 7, '0')     AS phone,
    'Load Test Client ' || (existing.max_seq + gs)             AS full_name,
    'LOADTEST-' || lpad((existing.max_seq + gs)::text, 6, '0') AS passport_data
FROM existing, generate_series(1, :n) AS gs;

INSERT INTO clients (id, full_name, phone, passport_data, status, created_at)
SELECT client_id, full_name, phone, passport_data, 'ACTIVE', now()
FROM seed_clients;

-- Large balance so "insufficient funds" never interferes with a max-search load test.
INSERT INTO accounts (id, client_id, account_number, balance, held_amount, currency, version)
SELECT gen_random_uuid(), client_id, '408' || lpad(seq::text, 17, '0'), 100000000.00, 0, 'RUB', 0
FROM seed_clients;

COMMIT;
