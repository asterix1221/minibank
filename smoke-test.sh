#!/usr/bin/env bash
# Smoke test: регистрация -> подтверждение -> логин -> перевод, от начала до конца,
# без ручного копирования id/кодов - скрипт делает это сам и печатает каждый шаг.
#
# Требуется: curl, python3 (python3 уже нужен для остальной части проекта).
#
# Запуск:
#   ./smoke-test.sh
#   BASE=http://localhost:8080 ./smoke-test.sh   (если приложение не на localhost:8080)

set -euo pipefail

BASE="${BASE:-http://localhost:8080}"

# На Windows обычно есть только `python`, а не `python3` (даже под Git Bash) - подбираем сами.
PYTHON_BIN="python3"
command -v python3 >/dev/null 2>&1 || PYTHON_BIN="python"

# Случайные телефоны, чтобы можно было перезапускать скрипт много раз подряд
# без ошибки "клиент с таким телефоном уже существует".
RAND_A=$((RANDOM % 9000000 + 1000000))
RAND_B=$((RANDOM % 9000000 + 1000000))
PHONE_A="+7900${RAND_A}"
PHONE_B="+7900${RAND_B}"

# Достаёт поле из JSON-ответа. Пример: echo "$RESP" | json_get "['registrationId']"
json_get() {
    "$PYTHON_BIN" -c "import sys, json; d=json.load(sys.stdin); print(d$1)"
}

step() { echo; echo "=== $1 ==="; }
fail() { echo; echo "❌ ОШИБКА на шаге: $1"; echo "Полный ответ сервера был:"; echo "$2"; exit 1; }

step "1/9 Регистрируем клиента A ($PHONE_A)"
RESP=$(curl -sS -X POST "$BASE/clients/register" -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"Smoke Test A\",\"phone\":\"$PHONE_A\",\"passportData\":\"4500 000001\"}")
echo "$RESP"
REG_ID_A=$(echo "$RESP" | json_get "['registrationId']") || fail "регистрация клиента A" "$RESP"
REG_CODE_A=$(echo "$RESP" | json_get "['debugCode']")
echo "-> registrationId=$REG_ID_A code=$REG_CODE_A"

step "2/9 Подтверждаем регистрацию клиента A"
RESP=$(curl -sS -X POST "$BASE/clients/register/$REG_ID_A/confirm" -H 'Content-Type: application/json' \
    -d "{\"code\":\"$REG_CODE_A\"}")
echo "$RESP"
ACCOUNT_NUMBER_A=$(echo "$RESP" | json_get "['accountNumber']") || fail "подтверждение регистрации A" "$RESP"
echo "-> accountNumber=$ACCOUNT_NUMBER_A"

step "3/9 Регистрируем клиента B ($PHONE_B) - он получит перевод"
RESP=$(curl -sS -X POST "$BASE/clients/register" -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"Smoke Test B\",\"phone\":\"$PHONE_B\",\"passportData\":\"4500 000002\"}")
REG_ID_B=$(echo "$RESP" | json_get "['registrationId']") || fail "регистрация клиента B" "$RESP"
REG_CODE_B=$(echo "$RESP" | json_get "['debugCode']")

step "4/9 Подтверждаем регистрацию клиента B"
RESP=$(curl -sS -X POST "$BASE/clients/register/$REG_ID_B/confirm" -H 'Content-Type: application/json' \
    -d "{\"code\":\"$REG_CODE_B\"}")
ACCOUNT_NUMBER_B=$(echo "$RESP" | json_get "['accountNumber']") || fail "подтверждение регистрации B" "$RESP"
echo "-> accountNumber=$ACCOUNT_NUMBER_B"

step "5/9 Логинимся клиентом A (запрос кода)"
RESP=$(curl -sS -X POST "$BASE/auth/login/initiate" -H 'Content-Type: application/json' \
    -d "{\"phone\":\"$PHONE_A\"}")
echo "$RESP"
SESSION_ID=$(echo "$RESP" | json_get "['sessionId']") || fail "login initiate" "$RESP"
LOGIN_CODE=$(echo "$RESP" | json_get "['debugCode']")

step "6/9 Подтверждаем логин, получаем токен"
RESP=$(curl -sS -X POST "$BASE/auth/login/confirm" -H 'Content-Type: application/json' \
    -d "{\"sessionId\":\"$SESSION_ID\",\"code\":\"$LOGIN_CODE\"}")
echo "$RESP"
TOKEN=$(echo "$RESP" | json_get "['token']") || fail "login confirm" "$RESP"
echo "-> token=$TOKEN"

step "7/9 Смотрим счета клиента A (нужен accountId - UUID, не accountNumber)"
RESP=$(curl -sS "$BASE/accounts/me" -H "X-Session-Token: $TOKEN")
echo "$RESP"
ACCOUNT_ID_A=$(echo "$RESP" | "$PYTHON_BIN" -c "import sys,json; print(json.load(sys.stdin)[0]['id'])") \
    || fail "GET /accounts/me" "$RESP"
echo "-> accountId=$ACCOUNT_ID_A"

step "7.5/9 Пополняем счёт клиента A (свежий счёт всегда с балансом 0 - в спеке нет способа положить деньги иначе; см. README)"
RESP=$(curl -sS -X POST "$BASE/accounts/$ACCOUNT_ID_A/deposit" -H "X-Session-Token: $TOKEN" -H 'Content-Type: application/json' \
    -d '{"amount": 100000.00}')
echo "$RESP"

step "8/9 Инициируем и подтверждаем перевод A -> B на 100.00"
RESP=$(curl -sS -X POST "$BASE/transfers/internal/initiate" -H "X-Session-Token: $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"fromAccountId\":\"$ACCOUNT_ID_A\",\"toAccountNumber\":\"$ACCOUNT_NUMBER_B\",\"amount\":100.00}")
echo "$RESP"
TX_ID=$(echo "$RESP" | json_get "['transactionId']") || fail "transfer initiate" "$RESP"
TX_CODE=$(echo "$RESP" | json_get "['debugCode']")

RESP=$(curl -sS -X POST "$BASE/transfers/internal/$TX_ID/confirm" -H "X-Session-Token: $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"code\":\"$TX_CODE\"}")
echo "$RESP"

step "9/9 Исполняем перевод"
RESP=$(curl -sS -X POST "$BASE/transfers/internal/$TX_ID/execute" -H "X-Session-Token: $TOKEN")
echo "$RESP"
STATUS=$(echo "$RESP" | json_get "['status']") || fail "transfer execute" "$RESP"

echo
if [ "$STATUS" = "COMPLETED" ]; then
    echo "✅ УСПЕХ: перевод $TX_ID завершён со статусом COMPLETED"
    echo
    echo "Дальше можешь проверить метрики:"
    echo "  curl -s $BASE/actuator/prometheus | grep transactions_total"
    echo "  curl -s $BASE/actuator/prometheus | grep operation_duration_seconds"
    echo
    echo "И события транзакции:"
    echo "  curl -s $BASE/transactions/$TX_ID/events -H \"X-Session-Token: $TOKEN\""
else
    fail "финальный статус перевода (ожидался COMPLETED)" "$RESP"
fi
