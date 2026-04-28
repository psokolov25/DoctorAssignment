# RUNBOOK сопровождения

Документ предназначен для дежурного инженера, 2-й линии и разработчика, который разбирает инциденты **Med Doctor Assignment Service** на стенде или в production.

## Главные логи

| Файл | Назначение |
|---|---|
| `log/med-robot.log` | общий runtime-лог службы |
| `log/med-robot-busines.log` | бизнес-решения: выбор услуги, обработка визитов, assign/transfer |
| `log/med-robot-http.log` | HTTP TRACE/DEBUG запросы к Orchestra и med-robot |
| `log/archived/*` | архивные логи по датам |

В спорных ситуациях всегда нужны минимум два файла за один период времени:

1. business log;
2. HTTP log.

Business log показывает решение алгоритма, HTTP log показывает фактический запрос и ответ внешней системы.

## Проверка после старта

Найдите строку:

```text
Runtime configuration marker=2026-04-27-entrypoint-source-id-diagnostics
```

Проверьте поля:

| Поле | Что должно быть |
|---|---|
| `gatewayClass` | `ConfigurableVisitWorkflowGateway` |
| `gatewayCodeSource` | путь к реально запущенному jar |
| `activationEnabled` | обычно `false`, если activation endpoint не подтвержден |
| `pollingEnabled` | `true`, если используется polling |
| `medRobotEnabled` | `true`, если должен работать med-robot |
| `medRobotRequestBodyMode` | `TICKET_NUMBER_PLAIN_TEXT` для передачи номера талона |
| `medRobotPlainTextPolicy` | обычно `default` |
| `medRobotErrorHandlingMode` | `FALLBACK_TO_LOCAL` или `SKIP_VISIT` |
| `addMissingRobotServiceToVisit` | `true`, если нужно добавлять выбранную robot услугу в маршрут |
| `addMissingRobotServiceFailureMode` | обычно `CONTINUE_WITH_ASSIGN` |
| `sourceEntryPointIdByBranch` | карта branch -> entry point id |
| `resolvedSourceEntryPointIds` | например `1->1` |
| `replayMutationCookiesForPut` | обычно `false` |

Если runtime marker старый или `gatewayCodeSource` указывает не на ожидаемый jar, сначала перезапустите правильный артефакт. Не анализируйте бизнес-ошибки на старой сборке.

## Базовый health-check цикла

В business log должны появляться строки:

```text
Polling reconciliation for branch=1 servicePoint=... staff=... workProfile=...
Start assignment cycle source=POLLING branchId=1 ...
Finish assignment cycle source=POLLING branchId=1 processed=...
```

Если их нет:

1. проверьте `application.assignment.enabled=true`;
2. проверьте `application.assignment.polling-enabled=true`;
3. проверьте `application.assignment.polling-cron`;
4. проверьте, что branch входит в `allowed-branches`;
5. проверьте, что кэш branch успешно построен.

## Диагностика med-robot

### Проверить, вызывается ли робот

Ищите:

```text
Request med-robot optimal service branch=1 currentService=... bodyMode=TICKET_NUMBER_PLAIN_TEXT contentType=text/plain accept=application/json ticketNumber=... policy=default
```

Если такой строки нет:

- `application.med-robot.enabled=false`;
- текущий jar старый;
- для JSON-режима локальный предварительный выбор не нашел услугу;
- для plain text режима у визита нет номера талона;
- текущий визит был пропущен раньше: не тот branch, уже обработан, queue changed, lock занят.

### 415 Unsupported Media Type

Симптом в HTTP log:

```text
Content-Type: application/json
Accept: text/plain
Request Body: Щ028
Unsupported Media Type. Allowed types: [text/plain]
```

Причина в старом jar: plain text body ушел с JSON `Content-Type`; для Micronaut client это означает, что на методе был перепутан `@Consumes` и `@Produces`.

Действия:

1. Проверить runtime-аудит: `medRobotRequestBodyMode=TICKET_NUMBER_PLAIN_TEXT`.
2. Проверить HTTP log: должно быть `Content-Type: text/plain`, `Accept: application/json`.
3. Проверить, что запущен свежий jar с исправленным `MedRobotRestClient`.

### Ошибка med-robot и продолжение без робота

Если включено:

```yaml
error-handling-mode: FALLBACK_TO_LOCAL
```

то при ошибке med-robot ожидаемы строки:

```text
Med-robot optimal service request failed ...
Fallback to local service selection for visit ... after med-robot error
```

Если локального выбора нет, визит не будет обработан:

```text
No local service selection for visit ... after med-robot error; return empty selection
```

Если нужно не пытаться локально обрабатывать визит после ошибки робота:

```yaml
error-handling-mode: SKIP_VISIT
```

## Диагностика добавления услуги в визит

### Когда должен быть POST add-service

POST add-service выполняется только если:

- выбор пришел от med-robot;
- `add-missing-robot-service-to-visit=true`;
- выбранной услуги нет в `unservedVisitServices`;
- выбранная услуга не равна `currentVisitService`.

Лог:

```text
Selected med-robot service 39 is absent in visit 30283 unserved route and is not current service. Add service to visit before assign/transfer.
Add service to visit request branchId=1 visitId=30283 serviceId=39 path=... payload=<empty>
```

Если med-robot вернул текущую услугу, add-service не нужен и не должен вызываться.

### 500 `Integer cannot be cast to Long`

Симптом:

```text
POST /rest/entrypoint/branches/1/visits/30283/services/39/
500 Internal Server Error
ERROR_MESSAGE: java.lang.Integer cannot be cast to java.lang.Long
```

Это ошибка Orchestra endpoint-а `POST add service to visit`, а не med-robot.

Рекомендуемый режим:

```yaml
add-missing-robot-service-failure-mode: CONTINUE_WITH_ASSIGN
```

Тогда служба пишет warning и продолжает assign/transfer:

```text
Continue visit 30283 with assign/transfer after failed add-service ... because addMissingRobotServiceFailureMode=CONTINUE_WITH_ASSIGN
```

Если безопаснее пропускать такие визиты:

```yaml
add-missing-robot-service-failure-mode: SKIP_VISIT
```

Для жесткой отладки контракта:

```yaml
add-missing-robot-service-failure-mode: PROPAGATE_ERROR
```

## Диагностика assign-service

### `userState=INACTIVE`

Если assign вернул HTTP 200, но body содержит:

```json
"userState": "INACTIVE"
```

служба проверяет, применился ли assign фактически:

- если `currentVisitService.serviceId` совпал с requested service - продолжает transfer;
- если не совпал - выбрасывает `MutationContextException`.

Параметр:

```yaml
treat-inactive-user-state-as-failure: true
```

### `NO_STARTED_SERVICE_POINT_SESSION`

Обрабатывается аналогично `INACTIVE`.

Параметр:

```yaml
treat-no-started-service-point-session-as-failure: true
```

### Массовые 403 после первого отказа

Если mutating REST начал возвращать 403, лучше остановить текущий цикл:

```yaml
abort-cycle-on-forbidden-mutation: true
```

Это не исправляет причину 403, но предотвращает серию одинаковых ошибок по всем визитам очереди.

## Диагностика transfer-visit

### Проверить `fromId`

В логах должно быть:

```text
Transfer visit request branchId=1 ... sourceEntryPointId=1 ... payload={visitId=..., fromBranchId=1, fromId=1}
```

`fromId` берется из:

```yaml
source-entry-point-id-by-branch:
  "1": 1
```

или из:

```yaml
default-source-entry-point-id: 1
```

Если в ошибке фигурирует `14`, проверьте, не является ли `14` на самом деле `serviceId`, а не `entryPointId`. В логах рядом всегда есть подписи `serviceId`, `queueId`, `sourceEntryPointId`.

### 204 No Content

Это штатный успешный ответ transfer:

```text
Transfer visit response ... status=204 bodyReadMode=exchange-byte-array responseBody=<empty>
```

Не нужно считать пустое тело ошибкой.

## Диагностика «робот пропускается»

Сообщение:

```text
Med-robot selection is skipped because local pre-selection did not find a service
```

Допустимо только для `UNSERVED_SERVICE_IDS_JSON_ARRAY`.

Для режима `TICKET_NUMBER_PLAIN_TEXT` проверьте:

1. `medRobotRequestBodyMode=TICKET_NUMBER_PLAIN_TEXT` в runtime-аудите.
2. Есть ли `ticketNumber` у визита.
3. Не старый ли jar запущен.
4. Не выключен ли `application.med-robot.enabled`.

## Диагностика «визит не обработан»

Проверьте по порядку:

1. Визит действительно находится в очереди `unknown-doctor-queue-id`.
2. Branch входит в `allowed-branches`.
3. Не сработал `ProcessedVisitRegistry` по `processed-visit-ttl-seconds`.
4. Не занят branch-level lock.
5. Есть `DoctorContext`: staff, servicePoint, workProfile.
6. Для врача найдены доступные услуги.
7. Для визита прочитались детали.
8. med-robot или локальный алгоритм вернул `SelectedDoctorService`.
9. Не было ошибки add-service/assign/transfer.
10. Post-check не показал, что визит остался в старой очереди.

## Быстрая таблица симптомов

| Симптом | Вероятная причина | Где смотреть | Действие |
|---|---|---|---|
| `415 Unsupported Media Type` от med-robot | неверный `Content-Type` | HTTP log | проверить plain text client и jar |
| `local pre-selection did not find a service` | JSON-режим или старый jar | business log + runtime audit | включить `TICKET_NUMBER_PLAIN_TEXT`, перезапустить свежий jar |
| `Integer cannot be cast to Long` | ошибка Orchestra add-service endpoint-а | HTTP log | `CONTINUE_WITH_ASSIGN` или проверка контракта add-service |
| `userState=INACTIVE` | не готов server-side операторский контекст | assign response body | ждать корректной сессии, проверить activation |
| `NO_STARTED_SERVICE_POINT_SESSION` | service point session не стартовала в EntryPoint-контуре | assign response body | проверить порядок событий и session start |
| transfer ушел с неожиданным `fromId` | неверная карта source entry point | runtime audit + transfer log | исправить `source-entry-point-id-by-branch` |
| processed=0 при наличии визитов | визиты пропущены фильтрами или ошибками выбора | business log | идти по чек-листу «визит не обработан» |

## Какие данные приложить к инциденту

Для анализа нужны:

1. `application.yml` без паролей.
2. Строка runtime-аудита после старта.
3. Business log за 2-3 минуты вокруг инцидента.
4. HTTP log за тот же период.
5. `visitId`, `ticketId`, `branchId`, `staffId`, `servicePointId`, `workProfileId`.
6. Ожидаемая услуга/очередь и фактическая услуга/очередь.
7. Версия jar или путь `gatewayCodeSource`.
