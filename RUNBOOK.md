# RUNBOOK сопровождения

Документ предназначен для дежурного инженера, 2-й линии и разработчика, который разбирает инциденты **Med Doctor Assignment Service** на стенде или в production.



## 0. Быстрые документы для сопровождения

- `docs/roles/SUPPORT_GUIDE.md` - расширенная карта симптомов, команды Linux/Windows и правила эскалации.
- `docs/TEST_STAND_SETUP.md` - безопасные профили тестового стенда и smoke test после старта.
- `CONFIGURATION_REFERENCE.md` - справочник параметров `application.yml`.
- `ORCHESTRA_REST_CONTRACTS.md` - REST-контракты чтения/назначения/перевода визита.

## 1. Главные логи

| Файл | Назначение |
|---|---|
| `log/med-robot.log` | общий runtime-лог службы |
| `log/med-robot-busines.log` | бизнес-решения: выбор услуги, обработка визитов, assign/transfer |
| `log/med-robot-http.log` | HTTP TRACE/DEBUG запросы к Orchestra и med-robot |
| `log/archived/*` | архивные логи по датам |

Для анализа почти всегда нужны два файла за один и тот же период:

1. business log - почему алгоритм принял решение;
2. HTTP log - какой запрос реально ушел во внешнюю систему и какой ответ пришел.

## 2. Проверка после старта

Найдите строку runtime-аудита:

```text
Runtime configuration marker=2026-04-28-route-step-dedup-fix
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
| `medRobotRequireKnownQueue` | обычно `true` |
| `medRobotRequireDoctorAvailableService` | обычно `false` для режима доверия robot-выбору |
| `resolvedSourceEntryPointIds` | например `1->1` |
| `replayMutationCookiesForPut` | обычно `false` |

Если runtime marker старый или `gatewayCodeSource` указывает не на ожидаемый jar, сначала перезапустите правильный артефакт. Не анализируйте бизнес-ошибки на старой сборке.

## 3. Базовый health-check цикла

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

## 4. Диагностика med-robot

### 4.1. Проверить, вызывается ли робот

Ищите строку запроса:

```text
Request med-robot optimal service branch=1 currentService=117 bodyMode=TICKET_NUMBER_PLAIN_TEXT contentType=text/plain accept=application/json ticketNumber=Р002 policy=default
```

Если такой строки нет, проверьте возможные причины:

| Причина | Признак в логе | Что делать |
|---|---|---|
| med-robot отключен | `medRobotEnabled=false` в runtime-аудите | включить `application.med-robot.enabled=true` |
| запущен старый jar | старый runtime marker | перезапустить свежий артефакт |
| JSON-режим без локального выбора | `local pre-selection did not find a service in JSON-array mode` | включить `TICKET_NUMBER_PLAIN_TEXT` |
| нет номера талона | `plain text selection is skipped ... has no ticket number` | проверить `ticketId` в ответе Orchestra |
| нет текущей услуги | `plain text selection is skipped ... has no current service id` | проверить `currentVisitService.serviceId` |
| тот же маршрутный шаг уже обработан | `already processed recently ... processingFingerprint=...` | сравнить `currentVisitService.id` в HTTP log |
| визит ушел из unknown queue до transfer | `Skip visit ... because queue already changed` | проверить гонки и второй обработчик |

### 4.2. Сценарий «визит в Врач не в системе, currentService=117, но робот не вызывается»

Для этого сценария правильная конфигурация:

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
```

Ожидаемые поля в HTTP-ответе Orchestra по визиту:

```json
{
  "id": 30354,
  "ticketId": "Р002",
  "currentVisitService": {
    "id": 227634,
    "serviceId": 117,
    "serviceInternalName": "Врач не в системе"
  },
  "unservedVisitServices": []
}
```

Ожидаемый business log:

```text
Request med-robot optimal service branch=1 currentService=117 bodyMode=TICKET_NUMBER_PLAIN_TEXT contentType=text/plain accept=application/json ticketNumber=Р002 policy=default
Med-robot selected service=140 queue=384 visit=30354 routeOrder=null localService=null localQueue=null currentService=117
```

Если вместо запроса видна строка:

```text
Visit 30354 already processed recently for doctor 1 processingFingerprint=currentVisitServiceRecordId=227634
```

значит сработала защита от повторной обработки того же маршрутного шага. Сравните `currentVisitService.id`:

- если `currentVisitService.id` тот же - это дубль polling/event в пределах `processed-visit-ttl-seconds`;
- если `currentVisitService.id` новый, но fingerprint в логе старый - запущен неактуальный jar или визит не перечитался перед dedup;
- если `currentVisitService.id` отсутствует в ответе Orchestra, dedup использует fallback fingerprint по `currentServiceId`, `queueId` и `unservedServices`.

### 4.3. 415 Unsupported Media Type

Симптом в HTTP log:

```text
Content-Type: application/json
Accept: text/plain
Request Body: Р002
Unsupported Media Type. Allowed types: [text/plain]
```

Причина: plain text body ушел с JSON `Content-Type`; для Micronaut client это означает, что на методе были перепутаны `@Consumes` и `@Produces`.

Действия:

1. Проверить runtime-аудит: `medRobotRequestBodyMode=TICKET_NUMBER_PLAIN_TEXT`.
2. Проверить HTTP log: должно быть `Content-Type: text/plain`, `Accept: application/json`.
3. Проверить, что запущен свежий jar с исправленным `MedRobotRestClient`.

### 4.4. Ошибка med-robot и продолжение без робота

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

## 5. Маршрутная дедупликация визитов

`ProcessedVisitRegistry` защищает не просто `visitId`, а конкретный маршрутный шаг визита.

Ключ обработки включает:

- `branchId`;
- `visitId`;
- `staffId`;
- `processingFingerprint`.

Fingerprint строится так:

1. если есть `currentVisitService.id`, используется `currentVisitServiceRecordId=<id>`;
2. если его нет, используется fallback: `currentServiceId=<id>|queueId=<id>|unserved=<serviceId:externalKey:routeOrder,...>`.

Практический смысл: один и тот же `visitId` может повторно вернуться в **«Врач не в системе»** после прохождения очередной услуги. Если Orchestra выдала новый `currentVisitService.id`, это новый маршрутный шаг, и med-robot должен вызываться снова.

TTL задается параметром:

```yaml
application:
  assignment:
    processed-visit-ttl-seconds: 900
```

Уменьшать TTL нужно осторожно: слишком маленькое значение увеличит риск повторной обработки дублей событий; слишком большое значение при отсутствии `currentVisitService.id` может дольше блокировать легитимный повтор.

## 6. Диагностика assign-service

### 6.1. `userState=INACTIVE`

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

### 6.2. `NO_STARTED_SERVICE_POINT_SESSION`

Обрабатывается аналогично `INACTIVE`.

```yaml
treat-no-started-service-point-session-as-failure: true
```

### 6.3. Массовые 403 после первого отказа

Если mutating REST начал возвращать 403, лучше остановить текущий цикл:

```yaml
abort-cycle-on-forbidden-mutation: true
```

Это не исправляет причину 403, но предотвращает серию одинаковых ошибок по всем визитам очереди.

## 7. Диагностика transfer-visit

### 7.1. Проверить `fromId`

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

Если в ошибке фигурирует неожиданный `14`, проверьте, не является ли `14` на самом деле `serviceId`, а не `entryPointId`. В логах рядом всегда есть подписи `serviceId`, `queueId`, `sourceEntryPointId`.

### 7.2. 204 No Content

Это штатный успешный ответ transfer:

```text
Transfer visit response ... status=204 bodyReadMode=exchange-byte-array responseBody=<empty>
```

Не нужно считать пустое тело ошибкой.

## 8. Диагностика «визит не обработан»

Проверьте по порядку:

1. Визит действительно находится в очереди `unknown-doctor-queue-id`.
2. Branch входит в `allowed-branches`.
3. Не сработал `ProcessedVisitRegistry`; если сработал - проверить `processingFingerprint` и `currentVisitService.id`.
4. Не занят branch-level lock.
5. Есть `DoctorContext`: staff, servicePoint, workProfile.
6. Для врача найдены доступные услуги.
7. Для визита прочитались детали, включая `ticketId` и `currentVisitService.serviceId`.
8. med-robot или локальный алгоритм вернул `SelectedDoctorService`.
9. Не было ошибки assign/transfer.
10. Post-check не показал, что визит остался в старой очереди.

## 9. Быстрая таблица симптомов

| Симптом | Вероятная причина | Где смотреть | Действие |
|---|---|---|---|
| med-robot вообще не вызывается | выключен флаг, старый jar, нет ticket/current service, дедупликация | runtime audit + business log | идти по разделу 4 |
| `already processed recently ... currentVisitServiceRecordId=...` | дубль того же маршрутного шага | business + HTTP log | сравнить `currentVisitService.id` |
| `local pre-selection did not find a service in JSON-array mode` | выбран JSON-режим при пустом маршруте | business log + runtime audit | включить `TICKET_NUMBER_PLAIN_TEXT` |
| `415 Unsupported Media Type` от med-robot | неверный `Content-Type` | HTTP log | проверить plain text client и jar |
| `userState=INACTIVE` | не готов server-side операторский контекст | assign response body | ждать корректной сессии, проверить activation |
| `NO_STARTED_SERVICE_POINT_SESSION` | service point session не стартовала в EntryPoint-контуре | assign response body | проверить порядок событий и session start |
| transfer ушел с неожиданным `fromId` | неверная карта source entry point | runtime audit + transfer log | исправить `source-entry-point-id-by-branch` |
| processed=0 при наличии визитов | визиты пропущены фильтрами или ошибками выбора | business log | идти по чек-листу «визит не обработан» |

## 10. Какие данные приложить к инциденту

Для анализа нужны:

1. `application.yml` без паролей.
2. Строка runtime-аудита после старта.
3. Business log за 2-3 минуты вокруг инцидента.
4. HTTP log за тот же период.
5. `visitId`, `ticketId`, `branchId`, `staffId`, `servicePointId`, `workProfileId`.
6. Для визита: `currentVisitService.id`, `currentVisitService.serviceId`, `queueId`, `unservedVisitServices`.
7. Ожидаемая услуга/очередь и фактическая услуга/очередь.
8. Версия jar или путь `gatewayCodeSource`.
