# Как правильно всё проверить

Точные команды по шагам, для Windows (PowerShell) и для Linux/macOS/Git Bash. Выполняй всё
из корня проекта (`mini-bank/`), если не сказано иначе.

## Что нужно установить заранее

- Docker Desktop (Windows/Mac) или Docker Engine + Compose v2 (Linux) —
  `docker compose version` должен отработать
- Java 21 + Maven (`java -version`, `mvn -v`)
- Python (для сборщика метрик Gatling) — **на Windows команда `python`, не `python3`**
  (см. ниже почему)
- Postman, или консольный `newman` (`npm install -g newman`), если не хочешь открывать GUI

## Если ты на Windows

Раньше в проекте были только bash-скрипты (`.sh`), которые в чистом PowerShell не
запускаются — это не баг, а просто другой язык команд. Теперь у каждого скрипта есть
`.ps1`-версия специально для PowerShell — используй её, ничего дополнительно ставить не
нужно (кроме Git Bash, если вдруг захочешь запускать `.sh`-версии — но для Windows это
уже не обязательно).

Три момента, которые важно знать заранее:

1. **`python3` не существует на Windows — команда называется `python`.** Windows ставит
   только `python.exe`; при наборе `python3` Windows подсовывает заглушку Microsoft Store
   вместо ошибки «команда не найдена» — именно поэтому это выглядит как «Python не
   установлен», хотя на самом деле установлен. Везде, где в этом файле или в README
   написано `python3 ...`, на Windows набирай `python ...`.
2. **`curl` в PowerShell — это псевдоним для `Invoke-WebRequest`**, который (а) прячет
   тело ответа и выбрасывает исключение при любом статусе не 2xx, и (б) не понимает
   многострочный `-d 'json'` так же, как bash. Чтобы увидеть, что реально ответил
   сервер при ошибке:
   ```powershell
   try {
       Invoke-RestMethod -Uri "http://localhost:8080/clients/register" -Method POST `
           -ContentType "application/json" `
           -Body '{"fullName":"Ivan Petrov","phone":"+79991234567","passportData":"4500 123456"}'
   } catch {
       $_.ErrorDetails.Message
   }
   ```
   Именно блок `catch` показывает настоящее тело JSON-ошибки (например,
   `PHONE_ALREADY_REGISTERED`, если этот телефон уже был зарегистрирован раньше) — без
   него PowerShell покажет только «(422)» и больше ничего. Скрипт `smoke-test.ps1` уже
   умеет это делать сам.
3. **`grep` не существует в PowerShell** — используй `Select-String` (или его псевдоним
   `sls`):
   ```powershell
   (Invoke-RestMethod -Uri "http://localhost:8080/actuator/prometheus") -split "`n" `
       | Select-String "transactions_total"
   ```
   Или проще — просто открой `http://localhost:8080/actuator/prometheus` в браузере и
   найди текст через Ctrl+F.

---

## 1. Собрать и поднять стек

```bash
docker compose up --build
```

Дождись, пока `app` и `postgres` покажут healthy. В новом окне терминала:

```bash
docker compose ps
```

**Ожидается:** все 5 сервисов `running`; `postgres` и `app` конкретно показывают
`(healthy)`. (У `prometheus`/`grafana`/`postgres-exporter` нет Docker healthcheck'ов — см.
README, раздел «Архитектурные решения (этап 2)», почему — их работоспособность проверяется
в шаге 3 ниже.)

Если `app` никак не становится healthy, посмотри его логи:

```bash
docker compose logs app --tail 100
```

**Если раньше у тебя была ошибка «Error querying page data» на `localhost:9090/targets`
или «No data» на всех графиках Grafana** — это была реальная проблема с прошлой версией
`docker-compose.yml` (строка `extra_hosts` ломала контейнер Prometheus на Docker Desktop
для Windows). В этой версии она убрана — просто пересобери стек заново
(`docker compose up --build`) и проверь ещё раз.

---

## 2. Юнит-тесты (не зависят от поднятого стека)

```bash
mvn clean verify
```

**Ожидается:** `BUILD SUCCESS`, и где-то в выводе строка вида
`Tests run: X, Failures: 0, Errors: 0, Skipped: 0`. Если не так — пришли мне вывод целиком.

---

## 3. Happy path: регистрация → логин → перевод

### Самый простой способ — готовый скрипт

**Windows (PowerShell):**
```powershell
.\smoke-test.ps1
```

**Linux / macOS / Git Bash:**
```bash
chmod +x smoke-test.sh
./smoke-test.sh
```

Скрипт сам зарегистрирует двух клиентов, залогинится, пополнит счёт (свежий счёт всегда с
балансом 0 — без этого перевод не пройдёт, см. README) и сделает перевод. В конце — либо
`УСПЕХ`, либо чёткое сообщение, что именно сломалось и на каком шаге.

### Что происходит внутри (если хочется понимать, а не только запускать)

Каждый запрос к API возвращает JSON, из которого нужно взять одно-два значения (например,
`registrationId` или `debugCode`) и подставить их в **следующий** запрос — так и
называется «пробросить между шагами». `smoke-test.ps1`/`.sh` делают это автоматически;
вручную (PowerShell) это выглядит так:

```powershell
$resp = Invoke-RestMethod -Uri "http://localhost:8080/clients/register" -Method POST `
    -ContentType "application/json" `
    -Body '{"fullName":"Ivan Petrov","phone":"+79991234567","passportData":"4500 123456"}'
$resp
# -> registrationId и debugCode в выводе

Invoke-RestMethod -Uri "http://localhost:8080/clients/register/$($resp.registrationId)/confirm" `
    -Method POST -ContentType "application/json" `
    -Body (@{code=$resp.debugCode} | ConvertTo-Json)
```

`$resp.registrationId` и `$resp.debugCode` — это как раз то самое «взять значение из
предыдущего ответа», просто PowerShell делает это без ручного копирования (Invoke-RestMethod
сам разбирает JSON в объект).

**Как понять, что всё в порядке:** `Invoke-RestMethod` при ошибке сразу выбрасывает
исключение (см. раздел «Если ты на Windows» про `try`/`catch`) — если команда просто
отработала и напечатала объект с нужными полями, значит, всё хорошо.

---

## 4. Метрики → Prometheus → Grafana

**Шаг 1. Проверить, что метрики вообще появляются:**

```powershell
# Windows
(Invoke-RestMethod -Uri "http://localhost:8080/actuator/prometheus") -split "`n" | Select-String transactions_total
```
```bash
# Linux / macOS / Git Bash
curl -s http://localhost:8080/actuator/prometheus | grep transactions_total
```

До первого перевода команда, скорее всего, ничего не покажет — метрика появляется только
после того, как хотя бы один перевод дошёл до `COMPLETED`/`FAILED`. Запусти
`smoke-test.ps1`/`.sh`, потом повтори команду — должна появиться строка вида
`transactions_total{type="TRANSFER_INTERNAL",status="COMPLETED",...} 1.0`.

**Шаг 2. Prometheus (сайт на порту 9090):**
1. Открой `http://localhost:9090/targets`
2. У `mini-bank-app` и `postgres` в колонке State должно быть зелёное **UP**. Если видишь
   «Error querying page data» вообще на всей странице — сам Prometheus недоступен, смотри
   раздел про фикс `extra_hosts` в шаге 1 выше. Если конкретно таргет красный **DOWN** —
   пришли мне текст ошибки, которая показана под ним.

**Шаг 3. Grafana (сайт на порту 3000):**
1. Открой `http://localhost:3000`, логин `admin` / пароль `admin`
2. Слева меню → **Connections** → **Data sources** — там уже должен быть `Prometheus`
   (подключается автоматически, руками ничего делать не нужно)
3. Импорт трёх готовых дашбордов — по очереди для чисел `4701`, `12900`, `9628`:
   - Слева меню → **Dashboards** → кнопка **New** → **Import**
   - В поле "Import via grafana.com" впиши число, например `4701`, нажми **Load**
   - Внизу выбери datasource `Prometheus` из выпадающего списка
   - Нажми **Import**
   - Повтори для `12900` и `9628`
4. Открой дашборд `4701` (JVM) — там должны идти живые движущиеся графики. Если вместо
   графиков `N/A` — почти наверняка `mini-bank-app` не `UP` в Prometheus, вернись к шагу 2.

---

## 5. Postman

1. Открой Postman → **Import** → выбери оба файла:
   `postman/mini-bank.postman_collection.json` и
   `postman/mini-bank.postman_environment.json`
2. Справа вверху выбери окружение `mini-bank (local)` (по умолчанию там «No Environment»)
3. Слева найди коллекцию `mini-bank`, наведи на неё → **Run** (или три точки → Run
   collection)
4. Нажми большую кнопку **Run mini-bank**
5. Зелёная галочка = тест прошёл, красный крестик = не прошёл

**Важно:** в папке «Negative cases» коды 401/403/422 — это **ожидаемый, правильный**
результат (тест специально проверяет, что сервер отказывает). Не пугайся, если видишь эти
коды именно там.

Консольный вариант (нужен `npm install -g newman`):
```bash
newman run postman/mini-bank.postman_collection.json -e postman/mini-bank.postman_environment.json
```

---

## 6. Gatling (нагрузочное тестирование)

Здесь нужно два открытых окна терминала одновременно (обе программы должны работать
параллельно, ни одна не завершается сама — это нормально).

### 6a. Сидируем тестовые данные (один раз)

**Windows (PowerShell)** — идёт через уже запущенный контейнер, локальный `psql` не
нужен:
```powershell
.\load-testing\seed-test-data.ps1 -N 200
```

**Linux / macOS / Git Bash:**
```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=minibank DB_USER=minibank DB_PASSWORD=minibank \
  ./load-testing/seed-test-data.sh 200
```

**Ожидается:** «Готово. Записано 200 строк в .../clients.csv». Открой этот файл — там
должна быть заголовочная строка плюс 200 строк данных.

### 6b. Запускаем сборщик метрик Gatling (отдельное окно терминала, оставь работать)

```powershell
# Windows
python load-testing\gatling_metrics_exporter.py --port 9302
```
```bash
# Linux / macOS / Git Bash
python3 load-testing/gatling_metrics_exporter.py --port 9302
```

**Ожидается:** `[gatling-exporter] serving /metrics on :9302` и следом
`[gatling-exporter] watching target/gatling for completed runs (polling every 5.0s)`.
Строку `[gatling-exporter] parsed results from <run> (N request rows)` напечатает
не сразу, а только когда нагрузочный тест ниже полностью завершится и Gatling допишет
HTML-отчёт (`js/global_stats.json` + `js/stats.js`) — метрики в Grafana обновятся
в этот момент, не раньше. Это не баг: Gatling OSS не даёт live-метрик во время
прогона (см. `README.md`, раздел "Stage 3 - Part 3").

### 6c. Запускаем поиск максимума (второе окно терминала)

**Windows (PowerShell)** — используй готовый скрипт вместо ручного набора длинной команды
(копипаст длинной однострочной `mvn`-команды в PowerShell ненадёжен: пробел или дефис
между `-Dgatling` и `.simulationClass=...` иногда теряется при вставке, и получаешь ошибку
`Unknown lifecycle phase ...simulationClass=...`, если она у тебя уже была — поэтому и
появился этот скрипт):

```powershell
.\load-testing\run-maxsearch.ps1
```

Параметры при желании переопределяются флагами, например
`.\load-testing\run-maxsearch.ps1 -Steps 20 -Increment 3`.

**Linux / macOS / Git Bash:**

```bash
mvn gatling:test -Dgatling.simulationClass=gatling.simulations.MaxSearchSimulation -DbaseUrl=http://localhost:8080 -DstartUsersPerSec=2 -DusersPerSecIncrement=2 -Dsteps=15 -DstepDurationSeconds=30 -DrampDurationSeconds=5 -DmaxFailedPercent=1.0 -Dp95ThresholdMs=1000
```

Пока тест идёт, проверяй в браузере:
- `http://localhost:9090/targets` — должен появиться третий таргет `gatling` со статусом
  **UP**
- `http://localhost:3000` → **Dashboards** → папка **mini-bank** → дашборд
  **Gatling Load Test (custom)** — там должны двигаться графики RPS/пользователей

**Ожидается:** тест закончится (15 ступеней × 30 секунд ≈ 7.5 минут), напечатает в конце,
на какой ступени впервые не прошла проверка (по error rate или p95) — это число users/sec
и есть искомый максимум. Также появится ссылка на HTML-отчёт вида
`target/gatling/maxsearchsimulation-.../index.html`.

Если тест ни разу не упал даже на верхней ступени — перезапусти с большим `-Dsteps=` или
`-DusersPerSecIncrement=`.

### 6d. Подтверждающий тест

Возьми ~70-80% от числа, найденного в 6c.

**Windows (PowerShell):**

```powershell
.\load-testing\run-confirmation.ps1 -TargetUsersPerSec <твоё число>
```

**Linux / macOS / Git Bash:**

```bash
mvn gatling:test -Dgatling.simulationClass=gatling.simulations.ConfirmationSimulation -DbaseUrl=http://localhost:8080 -DtargetUsersPerSec=<твоё число> -DdurationMinutes=20 -DmaxFailedPercent=1.0 -Dp95ThresholdMs=1000
```

Идёт ровно 20 минут — просто подожди. **Ожидается:** ассерты не проваливаются на всём
протяжении; сравни начало и конец теста в HTML-отчёте или в графике Grafana — error rate
не должен расти, p95 не должен уползать вверх к концу.

Впиши оба найденных числа (максимум из 6c и то, что подтвердилось в 6d) в README, раздел
«Часть 2» этапа 3 — это единственные два реально непроверенных пункта во всём проекте.

---

## Если что-то не работает

Пришли мне:
- точную команду, которую запускал(а)
- **полный** вывод ошибки (не только последнюю строку)
- для проблем с Docker — ещё и `docker compose logs <имя сервиса> --tail 100`
- для Grafana/Prometheus — скриншот, как в этот раз (это правда помогает быстро найти
  причину)

Этого достаточно, чтобы разобраться без гаданий.
