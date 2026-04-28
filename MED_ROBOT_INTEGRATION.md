# Интеграция Doctor Assistant с med-robot

Документ описывает фактический контракт и эксплуатационное поведение интеграции **Med Doctor Assignment Service** с внешним сервисом **med-robot**. Особый акцент сделан на сценарии, где визит находится в очереди **«Врач не в системе»**, а его текущая услуга также равна служебной услуге **«Врач не в системе»** (`serviceId=117`).

## 1. Назначение интеграции

Doctor Assistant может выбирать следующую услугу для визита двумя способами.

| Режим | Что делает | Когда использовать |
|---|---|---|
| Локальный выбор | выбирает услугу из `unservedVisitServices` по доступным врачу услугам, `routeOrder` и локальным приоритетам | когда маршрут визита полностью и корректно приходит из Orchestra |
| med-robot | отправляет в med-robot контекст визита и получает оптимальную пару `serviceId`/`queueId` | когда следующую услугу должен выбирать внешний алгоритм, в том числе при пустом `unservedVisitServices` |

Интеграция обратима: при `application.med-robot.enabled=false` сервис не обращается к med-robot и использует только локальный алгоритм.

## 2. REST-контракт med-robot

Используется endpoint:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
```

Где:

- `{branchId}` - id отделения Orchestra;
- `{serviceId}` в path - исходная услуга для выбора следующей очереди;
- формат тела запроса определяется `application.med-robot.request-body-mode`.

### 2.1. Как выбирается `serviceId` для path

Код использует следующий порядок:

1. если локальный алгоритм уже нашел предварительную услугу, в path передается эта услуга;
2. если локальный выбор пустой, но у визита есть `currentVisitService.serviceId`, в path передается текущая услуга визита;
3. если нет ни локального выбора, ни текущей услуги, med-robot вызвать нельзя, потому что endpoint требует `{serviceId}` в URL.

Для проблемного сценария **«Врач не в системе»** это означает:

```text
currentVisitService.serviceId=117
request-body-mode=TICKET_NUMBER_PLAIN_TEXT
=> POST /prorobot/optimalqueue/{branchId}/service/117?policy=default
```

Именно этот режим позволяет спросить med-robot о следующей услуге даже тогда, когда `unservedVisitServices=[]`.

## 3. Форматы тела запроса

### 3.1. `UNSERVED_SERVICE_IDS_JSON_ARRAY`

```http
POST /prorobot/optimalqueue/1/service/301
Content-Type: application/json
Accept: application/json

[301,302,303]
```

JSON-режим предназначен для случаев, когда Orchestra возвращает полноценный список непройденных услуг. В этом режиме Doctor Assistant сначала обязан найти локальную предварительную услугу. Если `unservedVisitServices` пустой или локальный выбор не найден, med-robot не вызывается.

Типовой диагностический лог:

```text
Med-robot selection is skipped because local pre-selection did not find a service in JSON-array mode. Enable application.med-robot.request-body-mode=TICKET_NUMBER_PLAIN_TEXT if med-robot must be called by ticket number.
```

### 3.2. `TICKET_NUMBER_PLAIN_TEXT`

```http
POST /prorobot/optimalqueue/1/service/117?policy=default
Content-Type: text/plain
Accept: application/json

Р002
```

Plain text режим предназначен для текущей интеграции с Orchestra, где визит может находиться в очереди **«Врач не в системе»**, иметь `currentVisitService.serviceId=117`, но при этом не иметь списка `unservedVisitServices`.

В этом режиме Doctor Assistant вызывает med-robot, если выполнены условия:

- `application.med-robot.enabled=true`;
- `application.med-robot.request-body-mode=TICKET_NUMBER_PLAIN_TEXT`;
- у визита есть `currentVisitService.serviceId`;
- у визита есть `ticketId` / номер талона.

Типовой успешный лог запроса:

```text
Request med-robot optimal service branch=1 currentService=117 bodyMode=TICKET_NUMBER_PLAIN_TEXT contentType=text/plain accept=application/json ticketNumber=Р002 policy=default
```

Важно для Micronaut declarative client: `@Produces(text/plain)` задает исходящий `Content-Type`, а `@Consumes(application/json)` задает `Accept`. Если эти аннотации перепутать, med-robot вернет `415 Unsupported Media Type`.

## 4. Ответ med-robot

Ожидаемый ответ:

```json
{
  "serviceId": 140,
  "queueId": 384
}
```

Doctor Assistant проверяет ответ перед мутациями Orchestra.

| Проверка | Поведение |
|---|---|
| `serviceId` и `queueId` заполнены и больше нуля | ответ можно рассматривать дальше |
| `require-known-queue=true` | `queueId` должен присутствовать в кэше очередей отделения |
| `require-doctor-available-service=true` | `serviceId` должен входить в услуги текущего врача |
| JSON-режим | `serviceId` должен входить в `unservedVisitServices` |
| plain text режим | `serviceId` может отсутствовать в `unservedVisitServices`, потому что выбор идет по номеру талона |

Типовой лог успешного выбора:

```text
Med-robot selected service=140 queue=384 visit=30354 routeOrder=null localService=null localQueue=null currentService=117
Visit 30354 matchFound=true selectedService=140 targetQueue=384 success=true reason=med-robot-current-service-117
```

## 5. Исполнение выбранной услуги в Orchestra

После выбора `SelectedDoctorService` текущий executor работает так:

1. перечитывает визит перед переводом, если включен `recheck-visit-before-transfer`;
2. если `currentVisitService.serviceId` уже равен выбранной услуге, пропускает `assign-service` и выполняет только `transfer-visit`;
3. если текущая услуга отличается, вызывает `assign-service`;
4. вызывает `transfer-visit` в очередь `queueId`, выбранную med-robot;
5. выполняет post-check фактической очереди, если endpoint перечитывания визита доступен.

Текущая реализация **не выполняет отдельный `POST add-service` перед assign**. Если конкретная инсталляция Orchestra требует обязательного добавления услуги в маршрут до `assign-service`, это отдельная доработка `VisitWorkflowGateway`/`VisitAssignmentExecutor`. В текущем коде med-robot может вернуть услугу вне `unservedVisitServices` в plain text режиме, и далее выполняется обычный `assign-service` по выбранному `serviceId`.

## 6. Повторное попадание того же визита в «Врач не в системе»

В автономных медосмотрах один и тот же `visitId` может несколько раз возвращаться в очередь **«Врач не в системе»** после прохождения очередной услуги. Для сервиса это не дубль, а новый маршрутный шаг.

Для различения дубля и нового шага используется fingerprint обработки:

1. если Orchestra вернула `currentVisitService.id`, fingerprint строится как `currentVisitServiceRecordId=<id>`;
2. если `currentVisitService.id` отсутствует, fallback fingerprint строится по `currentServiceId`, `queueId` и списку `unservedServices`.

Пример полей из Orchestra:

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

Если тот же `visitId` снова вернулся в `117`, но `currentVisitService.id` изменился, Doctor Assistant должен снова обратиться к med-robot. Если `currentVisitService.id` тот же самый и TTL еще не истек, это считается дублем события или polling-цикла.

Диагностический лог дубля:

```text
Visit 30354 already processed recently for doctor 1 processingFingerprint=currentVisitServiceRecordId=227634
```

## 7. Настройки `application.med-robot`

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

| Параметр | Назначение |
|---|---|
| `enabled` | включает или полностью отключает обращение к med-robot |
| `url` | базовый URL med-robot без завершающего слэша |
| `optimal-service-path` | path template endpoint-а выбора оптимальной очереди |
| `request-body-mode` | `UNSERVED_SERVICE_IDS_JSON_ARRAY` или `TICKET_NUMBER_PLAIN_TEXT` |
| `plain-text-policy` | query-параметр `policy` в plain text режиме |
| `error-handling-mode` | `FALLBACK_TO_LOCAL` или `SKIP_VISIT` при ошибке med-robot |
| `fallback-to-local-on-empty-response` | использовать локальный выбор, если med-robot вернул пустую пару |
| `require-doctor-available-service` | строгая проверка, что выбранная услуга доступна текущему врачу |
| `require-known-queue` | проверка, что очередь из ответа med-robot есть в кэше отделения |

Для текущего сценария с `serviceId=117` рекомендуется:

```yaml
request-body-mode: TICKET_NUMBER_PLAIN_TEXT
plain-text-policy: default
require-doctor-available-service: false
require-known-queue: true
```

## 8. Матрица поведения

| Ситуация | Режим | Поведение |
|---|---|---|
| `application.med-robot.enabled=false` | любой | med-robot не вызывается |
| `unservedVisitServices=[]`, есть `ticketId`, есть `currentVisitService.serviceId=117` | `TICKET_NUMBER_PLAIN_TEXT` | med-robot вызывается по номеру талона |
| `unservedVisitServices=[]`, JSON-режим | `UNSERVED_SERVICE_IDS_JSON_ARRAY` | med-robot не вызывается, потому что нечего передать в JSON-массиве |
| нет `ticketId` | `TICKET_NUMBER_PLAIN_TEXT` | med-robot не вызывается, остается локальный выбор или пропуск |
| med-robot вернул HTTP/сетевую ошибку | `FALLBACK_TO_LOCAL` | используется локальный выбор, если он есть |
| med-robot вернул HTTP/сетевую ошибку | `SKIP_VISIT` | визит пропускается в текущем цикле |
| med-robot вернул `serviceId`, но `queueId` неизвестен кэшу | `require-known-queue=true` | ответ отклоняется, включается fallback |
| med-robot вернул услугу вне услуг врача | `require-doctor-available-service=true` | ответ отклоняется, включается fallback |
| тот же `visitId` вернулся в `117` с новым `currentVisitService.id` | любой robot-режим | это новый маршрутный шаг, med-robot может быть вызван повторно |
| тот же `visitId`, тот же `currentVisitService.id`, TTL не истек | любой | считается дублем, визит пропускается |

## 9. Диагностика: почему med-robot не вызывается

Проверяйте по порядку.

1. Runtime-аудит после старта:

```text
Runtime configuration marker=2026-04-28-route-step-dedup-fix ... medRobotEnabled=true medRobotRequestBodyMode=TICKET_NUMBER_PLAIN_TEXT ...
```

2. Business log должен содержать одну из строк:

```text
Request med-robot optimal service ...
Med-robot plain text selection is skipped because visit ... has no ticket number
Med-robot plain text selection is skipped because visit ... has no current service id
Med-robot selection is skipped because local pre-selection did not find a service in JSON-array mode ...
Visit ... already processed recently for doctor ... processingFingerprint=...
```

3. Если видите `already processed recently`, сравните `currentVisitService.id` в HTTP log. Новый `currentVisitService.id` должен приводить к новому fingerprint и повторному вызову робота.

4. Если видите `Content-Type: application/json` при plain text body, запущен старый jar или неверный `MedRobotRestClient`.

## 10. Компоненты интеграции

- `MedRobotProperties` - настройки `application.med-robot`.
- `MedRobotRequestBodyMode` - enum форматов тела запроса.
- `MedRobotRestClient` - Micronaut REST-клиент med-robot.
- `MedRobotRestConfiguration` - Basic Auth фильтр для med-robot.
- `MedRobotOptimalServiceGateway` / `MedRobotOptimalServiceGatewayImpl` - gateway-слой вызова med-robot.
- `MedRobotAwareDoctorServiceSelectionService` - доменный выбор услуги с med-robot и fallback.
- `VisitDetails.currentVisitServiceRecordId` - id текущей записи услуги визита для маршрутной дедупликации.
- `ProcessedVisitRegistry` - TTL-защита от повторной обработки одного и того же маршрутного шага.
- `RuntimeConfigurationLogger` - стартовый аудит effective-конфигурации.

## 11. Тестовое покрытие

Основные группы тестов:

- `MedRobotAwareDoctorServiceSelectionServiceTest` - выбор через med-robot, fallback, plain text режим, пустой маршрут и валидация очереди;
- `MedRobotRestClientContractTest` - фиксация `Content-Type`/`Accept` для JSON и plain text вызова;
- `MedRobotRestConfigurationTest` - Basic Auth для med-robot;
- `MedRobotPropertiesTest` - binding и значения по умолчанию;
- `VisitDetailsJsonMappingTest` - чтение `currentVisitService.serviceId` и `currentVisitService.id` из ответа Orchestra;
- `AutonomousMedicalExamAssignmentServiceTest` - сквозной доменный цикл выбора, назначения и перевода визита;
- `RuntimeConfigurationLoggerTest` - наличие диагностических параметров в стартовом аудите.
