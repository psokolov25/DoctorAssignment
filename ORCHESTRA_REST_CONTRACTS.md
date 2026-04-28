# REST-контракты Orchestra

Документ фиксирует REST-вызовы, которые **Med Doctor Assignment Service** выполняет в Orchestra 6. Пути вынесены в `application.assignment.experimental-endpoints`, потому что на разных инсталляциях Orchestra они могут отличаться.

## Общие правила вызовов

- Все REST-вызовы идут на `application.orchestra.url`.
- Basic Auth добавляется ко всем REST-запросам, если задан `application.orchestra.username`.
- Для mutating-запросов `PUT`/`POST` cookie replay по умолчанию выключен: `application.orchestra.replay-mutation-cookies=false`.
- Для `PUT`/`POST` используется `Content-Type: application/json` и `Accept: application/json`.
- `transfer-visit` читает ответ как `byte[]`, чтобы штатный `204 No Content` не превращался в ошибку пустого тела.

## Чтение справочников

Эти endpoint-ы используются при построении `BranchAssignmentCache`.

| Операция | Метод | Path | DTO |
|---|---:|---|---|
| Список отделений | GET | `/qsystem/rest/config/branches` | `Set<SmallBranch>` |
| Услуги branch | GET | `/rest/servicepoint/branches/{branchId}/services` | `Set<ServiceData>` |
| Очередь услуги | GET | `/rest/entrypoint/branches/{branchId}/services/{serviceId}/queue` | `TinyQueue` |
| Точки обслуживания | GET | `/rest/managementinformation/v2/branches/{branchId}/servicePoints` | `Set<ServicePointData>` |
| Рабочие профили | GET | `/rest/servicepoint/branches/{branchId}/workProfiles` | `Set<WorkProfileData>` |
| Очереди рабочего профиля | GET | `/rest/servicepoint/branches/{branchId}/workProfiles/{workProfileId}/queues` | `Set<TinyQueue>` |
| Все очереди branch | GET | `/rest/servicepoint/branches/{branchId}/queues/` | `Set<TinyQueue>` |

## Чтение визитов очереди

```http
GET /rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/full/
Accept: application/json
Authorization: Basic ...
```

Используется для чтения визитов очереди **«врач не назначен»**.

Конфигурация:

```yaml
application:
  assignment:
    experimental-endpoints:
      queue-visits-path: "/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/full/"
```

Ожидаемая модель совместима с `VisitSummary`:

```json
{
  "id": 30283,
  "queueId": 312,
  "ticketId": "Щ028"
}
```

## Чтение деталей визита

```http
GET /rest/entrypoint/branches/{branchId}/visits/{visitId}/
Accept: application/json
Authorization: Basic ...
```

Конфигурация:

```yaml
visit-details-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/"
```

Ожидаемые важные поля:

```json
{
  "id": 30283,
  "ticketId": "Щ028",
  "currentVisitService": {
    "id": 227634,
    "serviceId": 117,
    "serviceInternalName": "Врач не в системе"
  },
  "unservedVisitServices": [
    {
      "serviceId": 147,
      "serviceInternalName": "Терапевт",
      "routeOrder": 1
    }
  ]
}
```

Служба использует:

- `id` - id визита;
- `ticketId`/ticket number - body для med-robot plain text режима;
- `currentVisitService.id` - id текущей записи услуги визита; используется как fingerprint маршрутного шага для защиты от дублей;
- `currentVisitService.serviceId` - определение transfer-only и path service для med-robot;
- `unservedVisitServices[*].serviceId` - локальный маршрут визита;
- `routeOrder` - приоритет локального выбора.

## Маршрутный fingerprint визита

Для визитов, которые многократно возвращаются в служебную услугу **«Врач не в системе»** (`serviceId=117`), важно различать старый дубль и новый маршрутный шаг. Поэтому служба читает `currentVisitService.id` из ответа Orchestra.

```json
{
  "id": 30354,
  "ticketId": "Р002",
  "currentVisitService": {
    "id": 227634,
    "serviceId": 117,
    "serviceInternalName": "Врач не в системе"
  }
}
```

Если после прохождения очередной услуги тот же `visitId` снова получил новый `currentVisitService.id`, сервис имеет право снова вызвать med-robot. Если `currentVisitService.id` тот же самый, повтор в пределах `processed-visit-ttl-seconds` считается дублем.

## Отдельный `POST add-service`

Текущая реализация не вызывает отдельный `POST add-service` перед назначением услуги. Если med-robot в plain text режиме вернул услугу вне `unservedVisitServices`, следующий шаг - обычный `assign-service` по выбранному `serviceId`, затем `transfer-visit` в выбранную очередь.

Если на конкретной инсталляции Orchestra `assign-service` не может назначить услугу, отсутствующую в маршруте, нужно отдельно добавлять поддержку `POST add-service` в `VisitWorkflowGateway` и фиксировать контракт этого endpoint-а тестами.

## Назначение услуги визиту

```http
PUT /rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceId}/
Content-Type: application/json
Accept: application/json
Authorization: Basic ...
Content-Length: 0
```

Конфигурация:

```yaml
assign-service-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceId}/"
```

Служба намеренно не передает `staffId` и `servicePointId` в payload, потому что на проверенной интеграции Orchestra назначение услуги работает через PUT по URL ресурса, а дополнительные поля могут игнорироваться или ломать контракт.

Контекст все равно пишется в лог:

```text
Assign service request branchId=1 visitId=30283 serviceId=39 staffId=258 servicePointId=1420000000001 path=... payload=<empty>
```

### Анализ ответа assign

HTTP 200 сам по себе не всегда означает, что серверный контекст оператора готов. Служба анализирует body:

- если `userState=INACTIVE` и `currentVisitService.serviceId` не совпал с requested service - контекстная ошибка;
- если `userState=NO_STARTED_SERVICE_POINT_SESSION` и `currentVisitService.serviceId` не совпал с requested service - контекстная ошибка;
- если `currentVisitService.serviceId` уже совпал с requested service - assign считается эффективно примененным, и workflow идет дальше к transfer.

Параметры:

```yaml
treat-inactive-user-state-as-failure: true
treat-no-started-service-point-session-as-failure: true
```

## Перевод визита в очередь

```http
PUT /rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/
Content-Type: application/json
Accept: application/json
Authorization: Basic ...

{
  "fromBranchId": 1,
  "fromId": 1,
  "visitId": 30283
}
```

Конфигурация:

```yaml
transfer-visit-path: "/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/"
```

Где:

- `{branchId}` - branch назначения;
- `{queueId}` - целевая очередь выбранной услуги;
- `fromBranchId` - исходный branch;
- `fromId` - source entry point id, взятый из `source-entry-point-id-by-branch` или `default-source-entry-point-id`;
- `visitId` - id визита.

### Source entry point id

```yaml
source-entry-point-id-by-branch:
  "1": 1
```

На старте служба пишет:

```text
resolvedSourceEntryPointIds=1->1
```

Если в логах или ошибке виден неожиданный id, нужно различать:

- `serviceId` - id услуги;
- `queueId` - id очереди;
- `entryPointId` - id source entry point, попадает в `fromId`;
- `branchId` - id отделения;
- `visitId` - id визита.

## Defensive recheck и post-check

Перед transfer служба может перечитать визит:

```yaml
recheck-visit-before-transfer: true
```

Если визит уже ушел из очереди «врач не назначен», workflow по нему пропускается.

После transfer служба снова читает визит и проверяет фактическую очередь. Если очередь не совпала с `targetQueueId`, пишется warning.

## Activation step

Если конкретная инсталляция Orchestra требует отдельной активации operator/service point контекста, можно включить activation:

```yaml
activation:
  enabled: true
  method: POST
  path: "/some/orchestra/activation/{branchId}/{servicePointId}"
  payload-template: '{"staffId":{staffId},"workProfileId":{workProfileId}}'
  fail-cycle-on-error: true
```

Activation выполняется перед mutating workflow. Универсальный endpoint Orchestra для этого шага не подтвержден, поэтому по умолчанию он выключен.

## Минимальная трасса успешной обработки

```text
Request med-robot optimal service branch=1 currentService=147 bodyMode=TICKET_NUMBER_PLAIN_TEXT ticketNumber=Щ028 policy=default
Med-robot selected service=39 queue=901 visit=30283 routeOrder=null localService=null localQueue=null currentService=147
Selected med-robot service 39 is absent in visit 30283 unserved route and is not current service. Add service to visit before assign/transfer.
Add service to visit request branchId=1 visitId=30283 serviceId=39 path=/rest/entrypoint/branches/1/visits/30283/services/39/ payload=<empty>
Assign service request branchId=1 visitId=30283 serviceId=39 staffId=258 servicePointId=1420000000001 path=/rest/entrypoint/branches/1/visits/30283/services/39/ payload=<empty>
Transfer visit request branchId=1 visitId=30283 sourceQueueId=312 targetQueueId=901 sourceEntryPointId=1 path=/rest/entrypoint/branches/1/queues/901/visits/ payload={visitId=30283, fromBranchId=1, fromId=1}
Transfer visit response branchId=1 visitId=30283 targetQueueId=901 sourceEntryPointId=1 status=204 bodyReadMode=exchange-byte-array responseBody=<empty>
```
