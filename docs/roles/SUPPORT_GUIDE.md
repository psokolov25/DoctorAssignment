# Руководство технической поддержки

## 1. Назначение документа

Документ нужен для первичной диагностики Med Doctor Assignment Service на тестовом или промышленном стенде. Основная задача поддержки - быстро определить, проблема в доступности Orchestra, websocket-событиях, конфигурации id, med-robot, REST-контракте workflow или состоянии конкретного визита.

## 2. Какие логи смотреть

| Лог | Что содержит |
|---|---|
| `med-doctor-assignment-service.log` | общий жизненный цикл сервиса, старт, ошибки конфигурации, reconnect. |
| `med-doctor-assignment-service-http.log` | HTTP-вызовы к Orchestra и med-robot, статусы, URL. |
| `med-doctor-assignment-service-busines.log` | бизнес-решения: врач, визит, услуга, очередь, результат цикла. |
| stdout/systemd journal | ошибки запуска JVM, отсутствующий config, проблемы прав на каталог логов. |

Если нет разделения логов по файлам, ищите сообщения по пакетам `com.qsystems.meddoctorassignment`.

## 3. Базовая проверка после старта

| Шаг | Что искать | Норма |
|---|---|---|
| 1 | порт приложения | `8085` слушает. |
| 2 | старт Micronaut | нет аварийного завершения JVM. |
| 3 | конфигурация | залогированы основные параметры без паролей. |
| 4 | refresh branch cache | `Start cache refresh for branch`, затем `Finish cache refresh for branch`. |
| 5 | websocket | `Connecting to Orchestra websocket`, `Subscribed to ...` при включенном websocket. |
| 6 | polling | `Start assignment cycle ... trigger=POLLING` при включенном polling. |
| 7 | очередь | `Visits in unknown-doctor queue ... count=...`. |

## 4. Быстрая карта симптомов

| Симптом | Вероятная зона | Первое действие |
|---|---|---|
| Сервис не стартует | JVM/config/порт | проверить путь к jar, порт, YAML, права на logs. |
| Сервис стартует и сразу не видит Orchestra | сеть/URL/учетка | проверить `application.orchestra.url`, логин/пароль, firewall. |
| Кэш не прогревается | справочники/права | проверить REST configuration API и `branches-for-cache`. |
| Websocket не подключается | `/qpevents/events` | проверить SockJS info endpoint и Basic Auth. |
| Врач вошел, но цикл не стартует | события/корреляция | искать `USER_SERVICE_POINT_SESSION_START`, `SET_WORK_PROFILE`. |
| Цикл стартует, но визитов нет | queue id | проверить `unknown-doctor-queue-id`. |
| Визит есть, med-robot не вызывается | конфиг/дедупликация/данные визита | проверить `med-robot.enabled`, `ticketId`, fingerprint. |
| med-robot вызывается, но результат игнорируется | service/queue validation | проверить `require-known-queue`, `require-doctor-available-service`. |
| assign возвращает `INACTIVE` | контекст оператора | дождаться сессии или проверить trigger. |
| transfer падает | `fromId`/entry point/REST contract | проверить `source-entry-point-id-by-branch`. |
| После ошибки много повторов | предохранители | проверить `abort-cycle-on-forbidden-mutation=true`. |

## 5. Диагностика Orchestra REST

### 5.1. Недоступность Orchestra

Контрольные строки:

```text
Cannot resolve configured branches from Orchestra. Service will stay alive and retry later
Schedule Orchestra REST reconnect/cache bootstrap retry in ... ms
Branch cache refresh failed for branch ... Service will continue and retry later
```

Нормальное поведение: сервис не падает, сохраняет процесс живым и повторяет bootstrap/refresh. Если процесс завершается, вероятно используется старая сборка или ошибка происходит до инициализации обработки отказа.

### 5.2. Ошибки авторизации

Признаки:

- массовые `401` или `403` в HTTP-логе;
- кэш не строится;
- mutating-запросы падают после первого успешного запроса.

Проверить:

- логин/пароль сервисной учетной записи;
- права учетной записи на REST API и entrypoint operations;
- `replay-mutation-cookies=false`;
- `send-cookies-in-handshake=false`;
- что Basic Auth отправляется во все запросы.

## 6. Диагностика websocket

Сервис должен подписываться на события:

```yaml
application:
  websocket:
    subscribed-events:
      - USER_SERVICE_POINT_SESSION_START
      - SET_WORK_PROFILE
```

`SERVICE_POINT_OPEN` не является основным безопасным trigger. Он может приходить до стабилизации рабочего профиля. Нормальный сценарий - связка `USER_SERVICE_POINT_SESSION_START -> SET_WORK_PROFILE`, после которой появляется `TriggerSource=USER_SESSION_READY`.

Что проверить:

- доступен ли `/qpevents/events/info`;
- не режет ли reverse proxy XHR streaming;
- совпадает ли Basic Auth;
- есть ли в логах `Subscribed to`;
- публикует ли Orchestra события при реальной посадке врача.

## 7. Диагностика med-robot

### 7.1. Проверить, должен ли med-robot вызываться

Условия:

- `application.med-robot.enabled=true`;
- визит подходит для обработки;
- нет блокировки route fingerprint в `ProcessedVisitRegistry`;
- для `TICKET_NUMBER_PLAIN_TEXT` есть номер талона;
- текущий маршрутный шаг не был недавно обработан тем же врачом.

### 7.2. Основные режимы тела запроса

| Режим | Body | Когда нужен |
|---|---|---|
| `UNSERVED_SERVICE_IDS_JSON_ARRAY` | `[147,148]` | когда Orchestra надежно возвращает непройденные услуги. |
| `TICKET_NUMBER_PLAIN_TEXT` | `Щ028` | когда `unservedVisitServices` пустой, но med-robot сам определяет маршрут по талону. |

### 7.3. Ошибка med-robot

При `error-handling-mode=FALLBACK_TO_LOCAL` сервис должен продолжить локальный выбор, если есть локальное совпадение. При `SKIP_VISIT` он пропустит визит до следующего цикла.

## 8. Диагностика «визит не обработан»

Проверить по порядку:

1. `application.assignment.enabled=true`.
2. Branch входит в `allowed-branches`.
3. `unknown-doctor-queue-id` соответствует фактической очереди.
4. Визит находится в этой очереди на момент recheck.
5. Визит не был обработан недавно с тем же fingerprint.
6. Врач имеет рабочий профиль.
7. Рабочий профиль связан с очередями и услугами.
8. med-robot не вернул пустой/неизвестный результат без fallback.
9. `dry-run=false`, если ожидается реальная мутация.
10. `assign-service` не вернул `INACTIVE` или `NO_STARTED_SERVICE_POINT_SESSION`.
11. `transfer-visit` получил корректный `fromId`.

## 9. Диагностика повторного попадания визита в «Врач не назначен»

Повторная обработка того же `visitId` допустима, если изменился маршрутный шаг. Сервис блокирует не весь `visitId`, а fingerprint:

- `currentVisitService.id`, если он есть;
- иначе fallback по `currentServiceId`, `queueId`, `unservedServices`.

Если визит после завершения одной услуги снова попал в «Врач не назначен» с новым `currentVisitService.id`, med-robot может быть вызван снова.

## 10. Данные для эскалации разработчику

В обращении обязательно приложить:

- время инцидента с часовым поясом;
- версия jar;
- branch id;
- queue id «Врач не назначен»;
- source entry point id;
- staff id;
- service point id;
- work profile id;
- visit id и номер талона;
- фрагмент `application.yml` без паролей;
- фрагмент бизнес-лога от `Start assignment cycle` до `Finish assignment cycle`;
- HTTP-лог проблемных REST-вызовов;
- ответ med-robot, если участвовал;
- скрин/описание состояния визита в Orchestra до и после.

## 11. Команды для Linux-стенда

```bash
# Проверить процесс
ps aux | grep med-doctor-assignment

# Проверить порт
ss -ltnp | grep 8085

# Смотреть журнал systemd
journalctl -u med-doctor-assignment -n 300 --no-pager

# Смотреть новые строки бизнес-лога
tail -f /opt/med-doctor-assignment/logs/med-doctor-assignment-service-busines.log

# Найти ошибки за период
grep -E "ERROR|WARN|403|500|INACTIVE|NO_STARTED_SERVICE_POINT_SESSION" /opt/med-doctor-assignment/logs/*.log
```

## 12. Команды для Windows-стенда

```powershell
# Найти процесс
Get-Process java

# Проверить порт
netstat -ano | findstr 8085

# Смотреть последние строки лога
Get-Content .\logs\med-doctor-assignment-service-busines.log -Tail 200 -Wait

# Найти ошибки
Select-String -Path .\logs\*.log -Pattern "ERROR","WARN","403","500","INACTIVE","NO_STARTED_SERVICE_POINT_SESSION"
```

## 13. Чек-лист закрытия инцидента

- [ ] Найдена точная зона проблемы.
- [ ] Зафиксированы branch/visit/staff/servicePoint/workProfile id.
- [ ] Проверено, был ли `dry-run`.
- [ ] Проверены REST-статусы Orchestra.
- [ ] Проверено, вызывался ли med-robot.
- [ ] Проверен `fromId` для transfer.
- [ ] Сохранены фрагменты логов.
- [ ] Описано фактическое и ожидаемое поведение.
- [ ] Дано решение: конфиг, эксплуатационное действие или передача разработчику.
