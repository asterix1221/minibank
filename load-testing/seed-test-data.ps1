# Сидирует N тестовых клиентов+счетов напрямую через SQL (в обход API регистрации - см.
# README, почему) и выгружает Gatling-feeder в src/test/resources/data/clients.csv.
#
# В отличие от seed-test-data.sh, эта версия НЕ требует установленного локально psql -
# она выполняет SQL внутри уже запущенного контейнера postgres через `docker exec`.
# Поэтому стек (`docker compose up`) должен быть уже поднят.
#
# Запуск:
#   .\seed-test-data.ps1
#   .\seed-test-data.ps1 -N 200
#
# Идемпотентен: можно запускать сколько угодно раз подряд, каждый раз добавится ещё один
# батч из N клиентов (см. комментарии в sql/seed_load_test_data.sql), а CSV-фидер всегда
# перевыгружается заново под именно последний добавленный батч.

param(
    [int]$N = 200
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$OutDir = Join-Path $ProjectRoot "src\test\resources\data"
$OutFile = Join-Path $OutDir "clients.csv"
$SeedSqlFile = Join-Path $ScriptDir "sql\seed_load_test_data.sql"
$ExportSqlFile = Join-Path $ScriptDir "sql\export_load_test_feeder.sql"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# Берём ID контейнера ОДИН раз и дальше работаем только с ним напрямую через `docker exec`
# (а не `docker compose exec`). Это принципиально: если из-за одинакового имени проекта
# Compose (оно берётся из имени папки с docker-compose.yml, а она в разных распаковках
# архива называется одинаково - "mini-bank") под тем же именем случайно оказалось запущено
# НЕСКОЛЬКО стеков/контейнеров postgres, `docker compose exec` может от вызова к вызову
# резолвить имя сервиса "postgres" в РАЗНЫЕ контейнеры. Фиксируя ID один раз, все дальнейшие
# `docker exec` физически не могут разъехаться по разным контейнерам.
$ContainerId = (docker compose ps -q postgres | Select-Object -First 1)
if (-not $ContainerId) {
    Write-Host "Не найден запущенный контейнер сервиса 'postgres'. Проверь: docker compose up -d" -ForegroundColor Red
    exit 1
}

$RunningCount = (docker compose ps -q postgres | Measure-Object -Line).Lines
if ($RunningCount -gt 1) {
    Write-Host "Внимание: найдено несколько ($RunningCount) контейнеров сервиса 'postgres' с одним именем проекта." -ForegroundColor Yellow
    Write-Host "Скорее всего, где-то остался запущенным старый стек (например, из другой распакованной копии проекта)." -ForegroundColor Yellow
    Write-Host "Рекомендуется: docker compose down (во всех старых копиях проекта), затем заново docker compose up -d." -ForegroundColor Yellow
    Write-Host "Продолжаю с первым найденным контейнером: $ContainerId" -ForegroundColor Yellow
}

Write-Host "Сидируем $N тестовых клиентов через контейнер postgres ($ContainerId)..."

# Шаг 1: только вставка данных в БД. Идемпотентно - продолжает нумерацию от текущего
# максимума, поэтому повторный запуск никогда не упрётся в duplicate key.
Get-Content $SeedSqlFile -Raw | docker exec -i $ContainerId tee /tmp/seed_load_test_data.sql | Out-Null

docker exec $ContainerId psql -U minibank -d minibank -q -v ON_ERROR_STOP=1 -v n=$N -f /tmp/seed_load_test_data.sql
if ($LASTEXITCODE -ne 0) {
    Write-Host "Ошибка при добавлении тестовых клиентов - смотри вывод выше." -ForegroundColor Red
    exit 1
}

Write-Host "Клиенты и счета добавлены. Выгружаем CSV для Gatling feeder..."

# Шаг 2: отдельная сессия, читает уже закоммиченные данные и отдаёт их через штатный режим
# `psql --csv`. Никакого \copy и никаких файлов внутри контейнера - просто обычный STDOUT
# psql, который docker exec и так пробрасывает нам напрямую. Это надёжнее, чем предыдущий
# вариант через /dev/stdout: там psql отрабатывал без ошибок, но результат почему-то не
# доходил до PowerShell - --csv, в отличие от \copy, не завязан ни на какой файл вообще.
Get-Content $ExportSqlFile -Raw | docker exec -i $ContainerId tee /tmp/export_load_test_feeder.sql | Out-Null

$RawOutput = docker exec $ContainerId psql -U minibank -d minibank --csv -q `
    -v ON_ERROR_STOP=1 -v n=$N -f /tmp/export_load_test_feeder.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "Ошибка при экспорте CSV - смотри вывод выше." -ForegroundColor Red
    exit 1
}

if (-not $RawOutput -or $RawOutput.Count -le 1) {
    Write-Host "psql отработал без ошибок, но вернул пустой результат - проверь, что клиенты реально есть в БД." -ForegroundColor Red
    exit 1
}

# Пишем без BOM явно (Out-File -Encoding utf8 в Windows PowerShell 5.1 добавляет BOM,
# который Gatling-фидер воспримет как часть имени первой колонки "phone" и сломает #{phone}).
$CsvText = ($RawOutput -join "`n") + "`n"
[System.IO.File]::WriteAllText($OutFile, $CsvText, (New-Object System.Text.UTF8Encoding($false)))

$lineCount = (Get-Content $OutFile | Measure-Object -Line).Lines - 1
Write-Host "Готово. Записано $lineCount строк в $OutFile" -ForegroundColor Green
