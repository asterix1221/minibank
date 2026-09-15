# Запускает Gatling ConfirmationSimulation (тест 2 - подтверждение), не требуя копировать
# длинную команду mvn руками - см. комментарий в run-maxsearch.ps1 про причину ошибки
# "Unknown lifecycle phase ...simulationClass=...", которая случается при копипасте.
#
# TargetUsersPerSec обязателен - возьми ~70-80% от числа, найденного run-maxsearch.ps1.
#
# Запуск (из папки mini-bank):
#   .\load-testing\run-confirmation.ps1 -TargetUsersPerSec 8
#   .\load-testing\run-confirmation.ps1 -TargetUsersPerSec 8 -DurationMinutes 20

param(
    [Parameter(Mandatory = $true)]
    [int]$TargetUsersPerSec,
    [string]$BaseUrl = "http://localhost:8080",
    [int]$DurationMinutes = 20,
    [double]$MaxFailedPercent = 1.0,
    [int]$P95ThresholdMs = 1000
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
Push-Location $ProjectRoot
try {
    # Preflight (Bugfix, Stage 3 take 3) - см. подробный комментарий в run-maxsearch.ps1:
    # без этой проверки рассинхронизация clients.csv с БД обнаруживалась только через
    # $DurationMinutes минут по 100% "found 404" в HTML-отчёте, а не сразу.
    $FeederFile = Join-Path $ProjectRoot "src\test\resources\data\clients.csv"
    if (-not (Test-Path $FeederFile)) {
        Write-Host "Не найден $FeederFile. Сначала запусти .\load-testing\seed-test-data.ps1" -ForegroundColor Red
        exit 1
    }
    $FirstRow = (Get-Content $FeederFile | Select-Object -Skip 1 -First 1)
    $FirstPhone = ($FirstRow -split ",")[0]
    Write-Host "Preflight: проверяю, что телефон из фидера ($FirstPhone) реально есть в БД..." -ForegroundColor Cyan
    try {
        $Resp = Invoke-WebRequest -Uri "$BaseUrl/auth/login/initiate" -Method Post `
            -ContentType "application/json" -Body "{`"phone`":`"$FirstPhone`"}" `
            -UseBasicParsing -ErrorAction Stop
        $StatusCode = $Resp.StatusCode
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
    }
    if ($StatusCode -ne 200) {
        Write-Host "Preflight FAILED: /auth/login/initiate вернул $StatusCode для $FirstPhone (ожидался 200)." -ForegroundColor Red
        Write-Host "Это значит, что clients.csv рассинхронизирован с текущей БД приложения (клиент не найден / БД пересоздана после сидирования)." -ForegroundColor Red
        Write-Host "Исправление: .\load-testing\seed-test-data.ps1 -N 200   (сразу перед этим запуском, а не заранее)" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Preflight OK (200)." -ForegroundColor Green

    $mvnArgs = @(
        "gatling:test",
        "-Dgatling.simulationClass=gatling.simulations.ConfirmationSimulation",
        "-DbaseUrl=$BaseUrl",
        "-DtargetUsersPerSec=$TargetUsersPerSec",
        "-DdurationMinutes=$DurationMinutes",
        "-DmaxFailedPercent=$MaxFailedPercent",
        "-Dp95ThresholdMs=$P95ThresholdMs"
    )
    Write-Host "Запуск ($DurationMinutes мин): mvn $($mvnArgs -join ' ')" -ForegroundColor Cyan
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Gatling завершился с ошибкой/проваленными assertions - смотри вывод и HTML-отчёт выше." -ForegroundColor Yellow
    }
} finally {
    Pop-Location
}
