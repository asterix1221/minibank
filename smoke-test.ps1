# Смоук-тест для Windows PowerShell: регистрация -> подтверждение -> логин -> перевод,
# от начала до конца, без ручного копирования id/кодов.
#
# Требуется: только сама PowerShell (Invoke-RestMethod встроен, ничего ставить не нужно).
#
# Запуск:
#   .\smoke-test.ps1
#   .\smoke-test.ps1 -Base "http://localhost:8080"

param(
    [string]$Base = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Step($msg) {
    Write-Host ""
    Write-Host "=== $msg ===" -ForegroundColor Cyan
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Body = $null,
        [hashtable]$Headers = @{}
    )
    try {
        if ($Body) {
            return Invoke-RestMethod -Uri $Url -Method $Method -ContentType "application/json" -Body $Body -Headers $Headers
        } else {
            return Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers
        }
    } catch {
        Write-Host "ОШИБКА при запросе $Method $Url" -ForegroundColor Red
        if ($_.ErrorDetails.Message) {
            Write-Host "Тело ответа сервера:" -ForegroundColor Red
            Write-Host $_.ErrorDetails.Message -ForegroundColor Red
        } else {
            Write-Host $_.Exception.Message -ForegroundColor Red
        }
        throw
    }
}

# Случайные телефоны, чтобы скрипт можно было перезапускать много раз подряд
# без ошибки "клиент с таким телефоном уже существует".
$randA = Get-Random -Minimum 1000000 -Maximum 9999999
$randB = Get-Random -Minimum 1000000 -Maximum 9999999
$phoneA = "+7900$randA"
$phoneB = "+7900$randB"

Step "1/9 Регистрируем клиента A ($phoneA)"
$body = @{ fullName = "Smoke Test A"; phone = $phoneA; passportData = "4500 000001" } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/clients/register" -Body $body
$resp | ConvertTo-Json
$regIdA = $resp.registrationId
$regCodeA = $resp.debugCode
Write-Host "-> registrationId=$regIdA code=$regCodeA"

Step "2/9 Подтверждаем регистрацию клиента A"
$body = @{ code = $regCodeA } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/clients/register/$regIdA/confirm" -Body $body
$resp | ConvertTo-Json
$accountNumberA = $resp.accountNumber
Write-Host "-> accountNumber=$accountNumberA"

Step "3/9 Регистрируем клиента B ($phoneB) - он получит перевод"
$body = @{ fullName = "Smoke Test B"; phone = $phoneB; passportData = "4500 000002" } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/clients/register" -Body $body
$regIdB = $resp.registrationId
$regCodeB = $resp.debugCode

Step "4/9 Подтверждаем регистрацию клиента B"
$body = @{ code = $regCodeB } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/clients/register/$regIdB/confirm" -Body $body
$accountNumberB = $resp.accountNumber
Write-Host "-> accountNumber=$accountNumberB"

Step "5/9 Логинимся клиентом A (запрос кода)"
$body = @{ phone = $phoneA } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/auth/login/initiate" -Body $body
$sessionId = $resp.sessionId
$loginCode = $resp.debugCode

Step "6/9 Подтверждаем логин, получаем токен"
$body = @{ sessionId = $sessionId; code = $loginCode } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/auth/login/confirm" -Body $body
$token = $resp.token
Write-Host "-> token=$token"

$authHeaders = @{ "X-Session-Token" = $token }

Step "7/9 Смотрим счета клиента A (нужен accountId - UUID, не accountNumber)"
$resp = Invoke-Json -Method GET -Url "$Base/accounts/me" -Headers $authHeaders
$accountIdA = $resp[0].id
Write-Host "-> accountId=$accountIdA"

Step "7.5/9 Пополняем счёт клиента A (свежий счёт всегда с балансом 0 - в спеке нет способа положить деньги иначе; см. README)"
$body = @{ amount = 100000.00 } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/accounts/$accountIdA/deposit" -Body $body -Headers $authHeaders
$resp | ConvertTo-Json

Step "8/9 Инициируем и подтверждаем перевод A -> B на 100.00"
$body = @{ fromAccountId = $accountIdA; toAccountNumber = $accountNumberB; amount = 100.00 } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/transfers/internal/initiate" -Body $body -Headers $authHeaders
$txId = $resp.transactionId
$txCode = $resp.debugCode

$body = @{ code = $txCode } | ConvertTo-Json
$resp = Invoke-Json -Method POST -Url "$Base/transfers/internal/$txId/confirm" -Body $body -Headers $authHeaders
$resp | ConvertTo-Json

Step "9/9 Исполняем перевод"
$resp = Invoke-Json -Method POST -Url "$Base/transfers/internal/$txId/execute" -Headers $authHeaders
$resp | ConvertTo-Json
$status = $resp.status

Write-Host ""
if ($status -eq "COMPLETED") {
    Write-Host "УСПЕХ: перевод $txId завершён со статусом COMPLETED" -ForegroundColor Green
    Write-Host ""
    Write-Host "Дальше можешь проверить метрики (открой в браузере или так):"
    Write-Host "  Invoke-RestMethod -Uri `"$Base/actuator/prometheus`" | Select-String transactions_total"
    Write-Host ""
    Write-Host "И события транзакции:"
    Write-Host "  Invoke-RestMethod -Uri `"$Base/transactions/$txId/events`" -Headers @{'X-Session-Token'='$token'}"
} else {
    Write-Host "ОШИБКА: ожидался статус COMPLETED, получен $status" -ForegroundColor Red
    exit 1
}
