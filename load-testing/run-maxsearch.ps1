# Запускает Gatling MaxSearchSimulation (тест 1 - поиск максимума), не требуя копировать
# длинную команду mvn руками - именно ручное копирование длинной однострочной команды и
# было причиной ошибки "Unknown lifecycle phase ...simulationClass=..." (при копипасте
# из чата/браузера пробел или дефис между -Dgatling и .simulationClass=... иногда
# теряется/заменяется на невидимый символ, и PowerShell передаёт Maven'у два отдельных,
# "оторванных" друг от друга аргумента вместо одного -Dgatling.simulationClass=...).
#
# Запуск (из папки mini-bank):
#   .\load-testing\run-maxsearch.ps1
#   .\load-testing\run-maxsearch.ps1 -BaseUrl http://localhost:8080 -StartUsersPerSec 2 -Increment 2 -Steps 15 -StepDurationSeconds 30 -RampDurationSeconds 5 -MaxFailedPercent 1.0 -P95ThresholdMs 1000

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$StartUsersPerSec = 2,
    [int]$Increment = 2,
    [int]$Steps = 15,
    [int]$StepDurationSeconds = 30,
    [int]$RampDurationSeconds = 5,
    [double]$MaxFailedPercent = 1.0,
    [int]$P95ThresholdMs = 1000
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
Push-Location $ProjectRoot
try {
    # Preflight (Bugfix, Stage 3 take 3): раньше при рассинхронизации clients.csv с
    # реальными данными в БД (например, после `docker compose down -v` / пересоздания
    # контейнера postgres без повторного запуска seed-test-data.ps1) весь прогон - 9-20
    # минут - падал со 100% "status.find.is(200), but actually found 404" на самом первом
    # шаге (Login - initiate), и это было видно только в самом конце по HTML-отчёту.
    # Проверяем первую строку фидера здесь, за секунду, до старта mvn.
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

    # Аргументы переданы отдельными элементами массива - никакого копипаста одной строкой,
    # поэтому невидимые символы/потерянные пробелы здесь в принципе невозможны.
    $mvnArgs = @(
        "gatling:test",
        "-Dgatling.simulationClass=gatling.simulations.MaxSearchSimulation",
        "-DbaseUrl=$BaseUrl",
        "-DstartUsersPerSec=$StartUsersPerSec",
        "-DusersPerSecIncrement=$Increment",
        "-Dsteps=$Steps",
        "-DstepDurationSeconds=$StepDurationSeconds",
        "-DrampDurationSeconds=$RampDurationSeconds",
        "-DmaxFailedPercent=$MaxFailedPercent",
        "-Dp95ThresholdMs=$P95ThresholdMs"
    )
    Write-Host "Запуск: mvn $($mvnArgs -join ' ')" -ForegroundColor Cyan
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Gatling завершился с ошибкой/проваленными assertions - смотри вывод и HTML-отчёт выше." -ForegroundColor Yellow
    }
} finally {
    Pop-Location
}
