# Справочник конфигурации

Документ описывает параметры `application.yml` для **Med Doctor Assignment Service**. Все параметры указываются в дереве `application.*`, если явно не сказано иное.

## Micronaut

| Параметр | Тип | Пример | Назначение |
|---|---|---|---|
| `micronaut.server.port` | number | `8085` | HTTP-порт самой службы |
| `micronaut.http.client.read-timeout` | duration | `60000ms` | таймаут ожидания ответа REST-клиента |

## `application.orchestra`

| Параметр | Тип | Значение по умолчанию / пример | Назначение |
|---|---|---|---|
| `url` | URL | `http://192.168.7.135:8080` | базовый URL Orchestra без завершающего слэша |
| `username` | string | `superadmin` | сервисная учетная запись Orchestra |
| `password` | string | `***` | пароль сервисной учетной записи |
| `common-rest-path` | path | `/rest` | общий base path подтвержденных REST endpoint-ов |
| `configuration-rest-path` | path | `/qsystem/rest/config` | base path configuration API |
| `branches-for-cache` | string | `"1"`, `"6,7"`, `"*"` | какие отделения прогревать в кэше |
| `replay-mutation-cookies` | boolean | `false` | переиспользовать ли cookie, полученные на PUT/POST |

### Рекомендации

- Для production не храните пароль в jar. Передавайте его через внешний конфиг или секреты окружения.
- `replay-mutation-cookies=false` оставлен по умолчанию, потому что в логах Orchestra повтор mutating-cookie приводил к 403.
- Для GET-запросов cookie могут сохраняться и использоваться отдельно от mutating-запросов.

## `application.med-robot`

| Параметр | Тип | Значения | Назначение |
|---|---|---|---|
| `enabled` | boolean | `true`, `false` | включает внешний выбор услуги через med-robot |
| `url` | URL | `http://192.168.7.135:8082` | базовый URL med-robot |
| `optimal-service-path` | path template | `/prorobot/optimalqueue/{branchId}/service/{serviceId}` | endpoint выбора услуги |
| `request-body-mode` | enum | `UNSERVED_SERVICE_IDS_JSON_ARRAY`, `TICKET_NUMBER_PLAIN_TEXT` | формат тела запроса |
| `plain-text-policy` | string | `default` | query-параметр `policy` для plain text endpoint-а |
| `username` | string | optional | Basic Auth user для med-robot |
| `password` | string | optional | Basic Auth password для med-robot |
| `error-handling-mode` | enum | `FALLBACK_TO_LOCAL`, `SKIP_VISIT` | что делать при ошибке med-robot |
| `fallback-to-local-on-error` | boolean | deprecated | старый алиас для `error-handling-mode` |
| `fallback-to-local-on-empty-response` | boolean | `true` | fallback к локальному выбору, если робот не выбрал услугу/очередь |
| `require-doctor-available-service` | boolean | `false` | требовать, чтобы услуга робота входила в услуги врача |
| `require-known-queue` | boolean | `true` | требовать, чтобы очередь робота была известна branch cache |

### `request-body-mode`

| Значение | HTTP-заголовки | Body | Особенность |
|---|---|---|---|
| `UNSERVED_SERVICE_IDS_JSON_ARRAY` | `Content-Type: application/json`, `Accept: application/json` | `[147,148]` | требует локальный предварительный выбор услуги |
| `TICKET_NUMBER_PLAIN_TEXT` | `Content-Type: text/plain`, `Accept: application/json` | `Щ028` | вызывает med-robot по номеру талона даже без локального совпадения |

### `error-handling-mode`

| Значение | Поведение |
|---|---|
| `FALLBACK_TO_LOCAL` | при ошибке med-robot использовать локальный выбор, если он есть |
| `SKIP_VISIT` | при ошибке med-robot пропустить текущий визит в этом цикле |

## `application.websocket`

| Параметр | Тип | Пример | Назначение |
|---|---|---|---|
| `enabled` | boolean | `false` | включает event-driven режим через SockJS/STOMP |
| `topic` | string | `/topic/event` | базовый STOMP topic |
| `subscribed-events` | list | `USER_SERVICE_POINT_SESSION_START`, `SET_WORK_PROFILE` | события, на которые подписывается служба |
| `delay-before-reconnect-in-milliseconds` | number | `10000` | задержка переподключения |
| `send-cookies-in-handshake` | boolean | `false` | передавать ли REST-cookie в websocket/SockJS handshake |

### Рекомендации

- Для стабильной production-схемы можно оставить `websocket.enabled=false` и использовать polling.
- Если включаете websocket, оставляйте `SERVICE_POINT_OPEN` как диагностический сигнал, а не как основной trigger.
- `send-cookies-in-handshake=false` безопаснее: websocket авторизуется Basic Auth, а REST-cookie не смешиваются с SockJS transport.

## `application.assignment` - общие параметры

| Параметр | Тип | Пример | Назначение |
|---|---|---|---|
| `enabled` | boolean | `true` | глобально включает алгоритм назначения |
| `unknown-doctor-queue-id` | number | `312` | queue id очереди «врач не назначен» |
| `max-visits-per-cycle` | number | `3` | максимум визитов за один цикл; лимит применяется после сортировки |
| `visit-processing-sort-order` | enum | `OLDEST_FIRST` | порядок обработки визитов: `AS_RETURNED`, `OLDEST_FIRST`, `NEWEST_FIRST`, `ID_ASC`, `ID_DESC` |
| `polling-enabled` | boolean | `true` | включает scheduled reconciliation |
| `polling-cron` | cron | `0 */1 * * * ?` | расписание polling |
| `branch-lock-timeout-ms` | number | `5000` | ожидание lock на branch |
| `dry-run` | boolean | `false` | режим без реальных мутаций в Orchestra |
| `allowed-branches` | list | `[1]` | белый список branch id; пустой список означает все |
| `stale-cache-duration-seconds` | number | `300` | TTL branch cache |
| `event-deduplication-ttl-seconds` | number | `120` | TTL дедупликации событий |
| `processed-visit-ttl-seconds` | number | `900` | TTL защиты от повторной обработки одного маршрутного шага визита |
| `service-priority-by-key` | map | `{ "4": 10 }` | приоритеты услуг при локальном выборе |

## Trigger flags

| Параметр | Рекомендуемое значение | Назначение |
|---|---:|---|
| `service-point-open-trigger-enabled` | `false` | не запускать мутации по преждевременному `SERVICE_POINT_OPEN` |
| `set-work-profile-trigger-enabled` | `false` | не запускать мутации по сырому `SET_WORK_PROFILE` |
| `work-profile-expanded-trigger-enabled` | `false` | повторно запускать цикл, если новый профиль расширил услуги врача |
| `user-service-point-session-start-trigger-enabled` | `true` | основной event trigger через корреляцию `USER_SERVICE_POINT_SESSION_START -> SET_WORK_PROFILE` |
| `user-session-settle-window-ms` | `2000` | окно ожидания стабилизации профиля |

## Проверки и предохранители workflow

| Параметр | Тип | Рекомендуемое значение | Назначение |
|---|---|---:|---|
| `recheck-visit-before-transfer` | boolean | `true` | перед transfer перечитать визит и проверить, что он еще в unknown queue |
| `abort-cycle-on-forbidden-mutation` | boolean | `true` | остановить цикл после первого 403/контекстной ошибки mutation |
| `treat-inactive-user-state-as-failure` | boolean | `true` | считать `userState=INACTIVE` контекстной ошибкой assign |
| `treat-no-started-service-point-session-as-failure` | boolean | `true` | считать `NO_STARTED_SERVICE_POINT_SESSION` контекстной ошибкой assign |

## Маршрутная дедупликация обработанных визитов

`processed-visit-ttl-seconds` не означает запрет на повторную обработку всего `visitId`. Сервис защищает от дублей конкретный маршрутный шаг.

Ключ `ProcessedVisitRegistry` строится из:

- `branchId`;
- `visitId`;
- `staffId`;
- `processingFingerprint`.

Fingerprint:

1. если в ответе Orchestra есть `currentVisitService.id`, используется `currentVisitServiceRecordId=<id>`;
2. если `currentVisitService.id` отсутствует, используется fallback по `currentServiceId`, `queueId` и `unservedServices`.

Практический эффект: один и тот же визит может снова попасть в очередь **«Врач не в системе»** после очередной услуги. Если `currentVisitService.id` изменился, это новый маршрутный шаг и med-robot может быть вызван повторно.

Диагностический лог:

```text
Visit 30354 already processed recently for doctor 1 processingFingerprint=currentVisitServiceRecordId=227634
```

## Статус отдельного `POST add-service`

В текущей реализации отдельный `POST add-service` перед назначением услуги **не выполняется**. Если med-robot в plain text режиме вернул услугу, которой нет в `unservedVisitServices`, Doctor Assistant передает этот `serviceId` в обычный `assign-service`, а затем выполняет `transfer-visit`.

Следовательно, параметры вида `add-missing-robot-service-to-visit` и `add-missing-robot-service-failure-mode` не являются рабочими настройками текущего кода. Если конкретная инсталляция Orchestra требует обязательного добавления услуги в маршрут, нужно отдельно доработать `VisitWorkflowGateway` и `VisitAssignmentExecutor`, а затем вернуть эти параметры в справочник конфигурации.

## Activation step

```yaml
application:
  assignment:
    activation:
      enabled: false
      fail-cycle-on-error: true
      method: POST
      path: ""
      payload-template: ""
```

| Параметр | Тип | Назначение |
|---|---|---|
| `activation.enabled` | boolean | включает отдельный REST-вызов перед assign/transfer |
| `activation.fail-cycle-on-error` | boolean | прерывать цикл при ошибке activation |
| `activation.method` | string | HTTP-метод: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` |
| `activation.path` | path template | endpoint activation |
| `activation.payload-template` | string | body template activation-запроса |

Доступные placeholders:

```text
{branchId}, {servicePointId}, {staffId}, {workProfileId}, {servicePointName}, {workProfileName}, {userName}
```

Activation step нужен только для инсталляций Orchestra, где перед mutating REST нужно явно активировать server-side operator/service point context. По умолчанию выключен, потому что универсальный endpoint не подтвержден.

## Source entry point id для transfer

```yaml
application:
  assignment:
    default-source-entry-point-id: 1
    source-entry-point-id-by-branch:
      "1": 1
```

| Параметр | Тип | Назначение |
|---|---|---|
| `default-source-entry-point-id` | number | fallback `fromId` для transfer, если branch-specific значение не задано |
| `source-entry-point-id-by-branch` | map string -> number | `fromId` для каждого branch |

Ключи карты рекомендуется писать строками:

```yaml
source-entry-point-id-by-branch:
  "1": 1
```

Это исключает неоднозначность YAML binding numeric map keys. В runtime-аудите проверяйте поле:

```text
resolvedSourceEntryPointIds=1->1
```

## `application.assignment.experimental-endpoints`

| Параметр | Метод | Путь по умолчанию | Назначение |
|---|---:|---|---|
| `enabled` | - | `true` | включает конфигурируемый visit workflow adapter |
| `queue-visits-path` | GET | `/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/full/` | получить визиты очереди |
| `visit-details-path` | GET | `/rest/entrypoint/branches/{branchId}/visits/{visitId}/` | получить детали визита |
| `visit-by-id-path` | GET | `/rest/entrypoint/branches/{branchId}/visits/{visitId}/` | перечитать визит по id |
| `assign-service-path` | PUT | `/rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceId}/` | назначить услугу визиту |
| `transfer-visit-path` | PUT | `/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/` | перевести визит в очередь |

Поддерживаемые placeholders:

```text
{branchId}, {queueId}, {targetQueueId}, {visitId}, {serviceId}
```

## Проверочный production-профиль

```yaml
micronaut:
  server:
    port: 8085
  http:
    client:
      read-timeout: 60000ms

application:
  orchestra:
    url: http://orchestra-host:8080
    username: ${ORCHESTRA_USER}
    password: ${ORCHESTRA_PASSWORD}
    branches-for-cache: "1"
    replay-mutation-cookies: false

  med-robot:
    enabled: true
    url: http://med-robot-host:8082
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
    error-handling-mode: FALLBACK_TO_LOCAL
    require-known-queue: true

  websocket:
    enabled: false

  assignment:
    enabled: true
    dry-run: false
    unknown-doctor-queue-id: 312
    allowed-branches: [ 1 ]
    polling-enabled: true
    polling-cron: "0 */1 * * * ?"
    source-entry-point-id-by-branch:
      "1": 1
    experimental-endpoints:
      enabled: true
      queue-visits-path: "/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/full/"
      visit-details-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/"
      visit-by-id-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/"
      assign-service-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceId}/"
      transfer-visit-path: "/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/"
```

## Профили конфигурации для тестового стенда

Полная процедура описана в `docs/TEST_STAND_SETUP.md`. Здесь приведены только конфигурационные профили, которые удобно копировать в `application-test.yml`.

### Профиль 1: первый безопасный запуск

```yaml
application:
  websocket:
    enabled: false
  med-robot:
    enabled: false
  assignment:
    enabled: true
    dry-run: true
    polling-enabled: true
    polling-cron: "0 */1 * * * ?"
    max-visits-per-cycle: 3
    allowed-branches: [1]
    unknown-doctor-queue-id: 292
    source-entry-point-id-by-branch:
      1: 1
```

Назначение профиля - проверить учетную запись, REST-доступ к Orchestra, refresh кэша отделения и чтение очереди без изменения состояния визитов.

### Профиль 2: проверка websocket-корреляции

```yaml
application:
  websocket:
    enabled: true
    subscribed-events:
      - USER_SERVICE_POINT_SESSION_START
      - SET_WORK_PROFILE
  assignment:
    dry-run: true
    service-point-open-trigger-enabled: false
    set-work-profile-trigger-enabled: false
    user-service-point-session-start-trigger-enabled: true
    work-profile-expanded-trigger-enabled: false
    user-session-settle-window-ms: 2000
```

Назначение профиля - подтвердить, что реальная посадка врача формирует безопасный trigger `USER_SESSION_READY`, а не преждевременный raw-trigger.

### Профиль 3: проверка med-robot

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    error-handling-mode: FALLBACK_TO_LOCAL
    fallback-to-local-on-empty-response: true
    require-doctor-available-service: false
    require-known-queue: true
  assignment:
    dry-run: true
```

Этот профиль нужен для стендов, где `unservedVisitServices` у визита часто пустой, а med-robot должен выбирать очередь по номеру талона.

### Профиль 4: один реальный тестовый визит

```yaml
application:
  assignment:
    dry-run: false
    max-visits-per-cycle: 1
    allowed-branches: [1]
    recheck-visit-before-transfer: true
    abort-cycle-on-forbidden-mutation: true
```

Включайте профиль только после подтверждения `unknown-doctor-queue-id`, `source-entry-point-id-by-branch`, рабочих REST endpoint-ов и успешного dry-run.

### Обязательные защитные настройки тестового стенда

| Параметр | Рекомендуемо | Причина |
|---|---:|---|
| `assignment.dry-run` | `true` на первом запуске | исключает случайные мутации. |
| `assignment.allowed-branches` | только тестовое отделение | ограничивает область воздействия. |
| `assignment.max-visits-per-cycle` | `1..3` | предотвращает массовую обработку при ошибке. |
| `assignment.recheck-visit-before-transfer` | `true` | защищает от гонок. |
| `assignment.abort-cycle-on-forbidden-mutation` | `true` | не допускает шквал одинаковых 403/500. |
| `assignment.service-point-open-trigger-enabled` | `false` | `SERVICE_POINT_OPEN` считается диагностическим, не основным trigger. |
| `websocket.send-cookies-in-handshake` | `false` | не смешивать REST-cookie и SockJS. |
| `orchestra.replay-mutation-cookies` | `false` | не повторять проблемные mutating-cookie. |
