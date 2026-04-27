# Интеграция с med-robot

Документ описывает, как **Med Doctor Assignment Service** обращается к внешнему сервису **med-robot** для выбора оптимальной услуги и очереди при автоматическом назначении визитов из очереди **«врач не назначен»**.

## Роль med-robot в общем workflow

Med-robot не выполняет мутации в Orchestra. Он только возвращает рекомендацию:

```text
visit/ticket -> med-robot -> serviceId + queueId
```

Все изменения визита выполняет Doctor Assignment Service через REST API Orchestra:

1. при необходимости добавляет услугу в маршрут визита;
2. назначает услугу визиту;
3. переводит визит в очередь услуги.

## REST-контракт med-robot

Используется один path:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
```

Разные REST-точки med-robot выбираются по `Content-Type` запроса.

| Режим | Content-Type запроса | Body | Accept | Когда использовать |
|---|---|---|---|---|
| `UNSERVED_SERVICE_IDS_JSON_ARRAY` | `application/json` | JSON-массив id необслуженных услуг, например `[147,148]` | `application/json` | когда маршрут визита полностью и корректно отражен в `unservedVisitServices` |
| `TICKET_NUMBER_PLAIN_TEXT` | `text/plain` | строка номера талона, например `Щ028` | `application/json` | когда робот должен сам определить маршрут/услугу по номеру талона |

Для режима `TICKET_NUMBER_PLAIN_TEXT` может передаваться query-параметр:

```http
?policy=default
```

Текущая настройка:

```yaml
application:
  med-robot:
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
```

## Важный нюанс Micronaut client

В декларативном Micronaut client для исходящего запроса:

- `@Produces(...)` задает request `Content-Type`;
- `@Consumes(...)` задает request `Accept`.

Поэтому plain text метод клиента должен быть объявлен так:

```java
@Post("${application.med-robot.optimal-service-path}")
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.APPLICATION_JSON)
Publisher<HttpResponse<MedRobotOptimalServiceResponse>> selectOptimalServiceByPlainText(...)
```

Если перепутать эти аннотации, запрос уйдет как:

```text
Content-Type: application/json
Accept: text/plain
Body: Щ028
```

И med-robot вернет `415 Unsupported Media Type`.

Правильный HTTP-вызов в режиме номера талона:

```text
POST /prorobot/optimalqueue/1/service/147?policy=default
Content-Type: text/plain
Accept: application/json

Щ028
```

## Настройки med-robot

```yaml
application:
  med-robot:
    enabled: true
    url: http://192.168.7.135:8082
    optimal-service-path: /prorobot/optimalqueue/{branchId}/service/{serviceId}
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
    error-handling-mode: FALLBACK_TO_LOCAL
    fallback-to-local-on-empty-response: true
    require-doctor-available-service: false
    require-known-queue: true
```

| Параметр | Значения | Назначение |
|---|---|---|
| `enabled` | `true` / `false` | включает/отключает внешний выбор через med-robot |
| `url` | URL | базовый URL med-robot |
| `optimal-service-path` | path template | REST path med-robot |
| `request-body-mode` | `UNSERVED_SERVICE_IDS_JSON_ARRAY`, `TICKET_NUMBER_PLAIN_TEXT` | формат тела запроса |
| `plain-text-policy` | `default`, `exclude`, `priority` и др. | query policy для text/plain endpoint-а |
| `username`, `password` | строки | Basic Auth, если REST API med-robot закрыт |
| `error-handling-mode` | `FALLBACK_TO_LOCAL`, `SKIP_VISIT` | поведение при HTTP/сетевой/контрактной ошибке |
| `fallback-to-local-on-empty-response` | `true` / `false` | fallback, если робот вернул пустой выбор |
| `require-doctor-available-service` | `true` / `false` | требовать, чтобы serviceId робота входил в доступные врачу услуги |
| `require-known-queue` | `true` / `false` | требовать, чтобы queueId робота был известен branch cache |

## Как выбирается path serviceId для вызова med-robot

Path содержит `{serviceId}`:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
```

В JSON-режиме этот `serviceId` берется из локального предварительного выбора. Это необходимо, потому что body содержит массив необслуженных услуг, и локальный алгоритм должен заранее найти рабочий кандидат.

В режиме `TICKET_NUMBER_PLAIN_TEXT` локальный предварительный выбор не обязателен. Если он отсутствует, служба выбирает `serviceId` для path по следующему правилу:

1. Если локальный выбор есть - используется его `serviceId`.
2. Иначе, если `currentVisitService.serviceId` присутствует в доступных врачу услугах - используется текущая услуга визита.
3. Иначе используется первая услуга из отсортированного набора доступных врачу услуг.

Это нужно только для совместимости с path med-robot. Источником истины для выбора в plain text режиме является номер талона.

## Как формируется body

### JSON-массив необслуженных услуг

```yaml
request-body-mode: UNSERVED_SERVICE_IDS_JSON_ARRAY
```

Body:

```json
[147, 148, 301]
```

Источник данных - `VisitDetails.unservedServices` после разрешения `serviceId` или `externalKey` через branch cache.

Если локальный предварительный выбор не найден или список необслуженных услуг пуст, вызов med-robot пропускается и возвращается локальный результат.

### Plain text номер талона

```yaml
request-body-mode: TICKET_NUMBER_PLAIN_TEXT
```

Body:

```text
Щ028
```

Источник данных - `VisitDetails.ticketNumber`, который заполняется из `VisitSummary.ticketId`/деталей визита.

В этом режиме med-robot вызывается даже если локальный алгоритм не нашел услугу по `unservedVisitServices`. Если номер талона отсутствует, вызов невозможен, и дальнейшее поведение определяется `error-handling-mode`.

## Обработка ответа med-robot

Ожидаемый ответ:

```json
{
  "serviceId": 39,
  "queueId": 901
}
```

Ответ принимается, если:

1. `serviceId` и `queueId` присутствуют и больше нуля;
2. если `require-known-queue=true`, `queueId` есть в branch cache;
3. если `require-doctor-available-service=true`, `serviceId` входит в услуги текущего врача;
4. для JSON-режима `serviceId` должен быть в локальном `unservedVisitServices`;
5. для plain text режима `serviceId` может отсутствовать в локальном `unservedVisitServices`, потому что med-robot использует номер талона как более полный источник.

Если ответ принят, формируется доменный выбор:

```text
SelectedDoctorService(serviceId, queueId, routeOrder, selectionReason)
```

`selectionReason` имеет вид:

```text
med-robot-current-service-<pathServiceId>
```

Именно по этому признаку executor понимает, что услуга была выбрана med-robot.

## Ошибки med-robot

Под ошибкой med-robot понимается:

- HTTP 4xx/5xx;
- сетевая ошибка;
- некорректный/неполный ответ;
- отсутствие номера талона в plain text режиме.

Настройка:

```yaml
application:
  med-robot:
    error-handling-mode: FALLBACK_TO_LOCAL
```

| Значение | Поведение |
|---|---|
| `FALLBACK_TO_LOCAL` | продолжить текущий визит по локальному выбору, если он есть; если локального выбора нет - визит не обрабатывается |
| `SKIP_VISIT` | пропустить текущий визит в этом цикле и перейти к следующему |

Старый параметр `fallback-to-local-on-error` оставлен как совместимый алиас:

```yaml
fallback-to-local-on-error: true   # равно FALLBACK_TO_LOCAL
fallback-to-local-on-error: false  # равно SKIP_VISIT
```

## Пустой ответ med-robot

Если med-robot вернул `null/null`, `0/0` или пустое тело, поведение задается отдельно:

```yaml
fallback-to-local-on-empty-response: true
```

| Значение | Поведение |
|---|---|
| `true` | использовать локальный выбор, если он есть |
| `false` | считать, что услуга не выбрана, и пропустить визит |

## Добавление услуги, которую выбрал med-robot

В режиме номера талона med-robot может вернуть услугу, которой нет в `unservedVisitServices`, потому что локально прочитанный маршрут визита неполный или еще не синхронизирован.

Для этого есть настройка:

```yaml
application:
  assignment:
    add-missing-robot-service-to-visit: true
```

Служба выполнит:

```http
POST /rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceOrigId}/
Content-Type: application/json
Accept: application/json
Content-Length: 0
```

Услуга добавляется только если:

1. выбор пришел от med-robot;
2. выбранной услуги нет в `unservedVisitServices`;
3. выбранная услуга не является `currentVisitService`.

Если med-robot вернул текущую услугу визита, добавление не выполняется. Служба переходит к `transfer-only`, потому что повторное добавление текущей услуги может дать ошибку Orchestra.

## Ошибка добавления услуги в Orchestra

Orchestra может вернуть ошибку на `POST add service to visit`, например:

```text
500 Internal Server Error
ERROR_MESSAGE: java.lang.Integer cannot be cast to java.lang.Long
```

Это не ошибка med-robot. Это ошибка REST-точки Orchestra, которая добавляет услугу в маршрут.

Поведение задается параметром:

```yaml
application:
  assignment:
    add-missing-robot-service-failure-mode: CONTINUE_WITH_ASSIGN
```

| Значение | Поведение |
|---|---|
| `CONTINUE_WITH_ASSIGN` | залогировать ошибку `POST add-service` и продолжить `assign-service`/`transfer-visit` |
| `SKIP_VISIT` | пропустить текущий визит в этом цикле |
| `PROPAGATE_ERROR` | пробросить исключение наверх и обработать как ошибку цикла |

Рекомендуемое значение для опытной эксплуатации - `CONTINUE_WITH_ASSIGN`, потому что некоторые инсталляции Orchestra не требуют явного добавления услуги перед assign или возвращают 500 на повторном/нестандартном добавлении.

## Типовые инциденты

### 415 Unsupported Media Type от med-robot

Симптом:

```text
Content-Type: application/json
Accept: text/plain
Request Body: Щ028
Unsupported Media Type. Allowed types: [text/plain]
```

Причина: plain text body отправлен с JSON Content-Type.

Что проверить:

1. В конфигурации стоит `request-body-mode: TICKET_NUMBER_PLAIN_TEXT`.
2. В коде plain text метод клиента использует `@Produces(MediaType.TEXT_PLAIN)`.
3. В HTTP-логе запрос содержит `Content-Type: text/plain`, `Accept: application/json`.

### med-robot не вызывается: local pre-selection did not find a service

Симптом:

```text
Med-robot selection is skipped because local pre-selection did not find a service
```

Нормально только для режима `UNSERVED_SERVICE_IDS_JSON_ARRAY`. Для `TICKET_NUMBER_PLAIN_TEXT` это поведение недопустимо: робот должен вызываться по номеру талона даже без локального совпадения.

Что проверить:

1. `medRobotRequestBodyMode=TICKET_NUMBER_PLAIN_TEXT` в runtime-аудите.
2. Наличие `ticketNumber` у визита.
3. Что запущен свежий jar: `gatewayCodeSource` и runtime marker.

### Робот вернул услугу, которой нет в маршруте

Симптом:

```text
Med-robot plain-text response service 39 is absent in locally read visit 30283 unserved route []. Accept response because text/plain mode uses ticket-number source of truth.
```

Это ожидаемое поведение для plain text режима. Дальше должен быть один из вариантов:

- если услуга не текущая - `POST add service to visit`;
- если услуга текущая - сразу `transfer-only`.

### Ошибка `Integer cannot be cast to Long` при add-service

Симптом:

```text
POST /rest/entrypoint/branches/1/visits/30283/services/39/
500 Internal Server Error
ERROR_MESSAGE: java.lang.Integer cannot be cast to java.lang.Long
```

Это ошибка на стороне Orchestra REST endpoint-а добавления услуги. Для продолжения обработки используйте:

```yaml
add-missing-robot-service-failure-mode: CONTINUE_WITH_ASSIGN
```

## Контрольный чек-лист интеграции

Перед production-включением med-robot проверьте:

- [ ] В стартовом аудите `medRobotEnabled=true`.
- [ ] В стартовом аудите `medRobotRequestBodyMode=TICKET_NUMBER_PLAIN_TEXT`.
- [ ] HTTP-запрос med-robot идет с `Content-Type: text/plain`.
- [ ] В body уходит номер талона без кавычек и JSON-обертки.
- [ ] Ответ med-robot содержит корректные `serviceId` и `queueId`.
- [ ] Если услуга отсутствует в `unservedVisitServices`, выполняется или сознательно пропускается `POST add-service` согласно правилам.
- [ ] При ошибке med-robot поведение соответствует `error-handling-mode`.
- [ ] При ошибке add-service поведение соответствует `add-missing-robot-service-failure-mode`.
