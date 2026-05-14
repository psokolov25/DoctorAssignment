# Расширенное описание конфигурации службы (`application.yml`)

Документ описывает параметры `application.yml` в разрезе блоков и практики эксплуатации.

## 1. `application.orchestra`

- `url` — базовый URL Orchestra (REST + websocket/SockJS).
- `username`, `password` — сервисная учетная запись.
- `common-rest-path` — общий REST base path (обычно `/rest`).
- `configuration-rest-path` — REST path конфигурационных endpoint-ов.
- `branches-for-cache` — какие отделения прогревать в кэше (`*`, `6`, `6,7`).
- `replay-mutation-cookies` — повтор cookie для mutating-запросов (обычно `false`).
- `reconnect-delay-ms` — задержка повторной попытки при недоступной Orchestra.

## 2. `application.med-robot`

- `enabled` — включение внешнего выбора услуги/очереди.
- `url` — базовый URL med-robot.
- `optimal-service-path` — path для расчета оптимальной услуги/очереди.
- `request-body-mode` — формат body (`TICKET_NUMBER_PLAIN_TEXT` или др. режим).
- `plain-text-policy` — политика plain-text запроса.
- `error-handling-mode` — поведение при ошибках med-robot.
- `fallback-to-local-on-error` — fallback на локальный алгоритм при ошибке.
- `fallback-to-local-on-empty-response` — fallback при пустом ответе.
- `require-doctor-available-service` — принимать только услуги, доступные врачу.
- `require-known-queue` — проверять наличие очереди в branch cache.
- `username`, `password` (опционально) — Basic Auth.

## 3. `application.websocket`

- `enabled` — event-driven режим через STOMP.
- `topic` — базовый topic Orchestra.
- `subscribed-events` — список подписываемых событий.
- `delay-before-reconnect-in-milliseconds` — retry delay.
- `send-cookies-in-handshake` — передача REST-cookie в websocket handshake.

## 4. `application.assignment` (основная бизнес-конфигурация)

### 4.1 Базовые флаги

- `enabled` — включение алгоритма назначения.
- `dry-run` — безопасный режим без реальных мутаций.
- `unknown-doctor-queue-id` — id очереди «Врач не назначен».
- `max-visits-per-cycle` — лимит визитов за цикл.
- `visit-processing-sort-order` — порядок выбора визитов.

### 4.2 Режим запуска циклов

- `polling-enabled`, `polling-cron` — расписание fallback polling.
- `service-point-open-trigger-enabled` — запуск по `SERVICE_POINT_OPEN`.
- `set-work-profile-trigger-enabled` — запуск по raw `SET_WORK_PROFILE`.
- `work-profile-expanded-trigger-enabled` — запуск при расширении профиля.
- `user-service-point-session-start-trigger-enabled` — запуск по корреляции `USER_SERVICE_POINT_SESSION_START -> SET_WORK_PROFILE`.
- `user-session-settle-window-ms` — окно стабилизации профиля.

### 4.3 Безопасность и отказоустойчивость

- `branch-lock-timeout-ms` — timeout branch lock.
- `recheck-visit-before-transfer` — повторная проверка визита перед transfer.
- `abort-cycle-on-forbidden-mutation` — остановка цикла после 403/контекстной ошибки.
- `treat-inactive-user-state-as-failure` — считать `INACTIVE` ошибкой цикла.
- `treat-no-started-service-point-session-as-failure` — считать `NO_STARTED_SERVICE_POINT_SESSION` ошибкой.

### 4.4 Ограничения prod-скоупа

- `allowed-branches` — белый список отделений (пустой список = все).
- `stale-cache-duration-seconds` — TTL branch cache.
- `event-deduplication-ttl-seconds` — TTL дедупликации событий.
- `processed-visit-ttl-seconds` — TTL повторной обработки визита.

### 4.5 Бизнес-маппинг Orchestra (branch → entry point)

- `default-source-entry-point-id` — резервный `fromId`.
- `source-entry-point-id-by-branch` — приоритетная карта `branchId -> entryPointId`.

> Для production это ключевая настройка, т.к. `transfer` использует `fromId`, который в конкретной Orchestra может отличаться между отделениями.

### 4.6 Приоритизация услуг

- `service-priority-by-key` — карта приоритетов по `serviceId`/`internalName`/`externalName`.

### 4.7 `activation` (опциональный шаг перед мутациями)

- `activation.enabled` — включить предварительный activation REST-вызов.
- `activation.fail-cycle-on-error` — завершать цикл при ошибке activation.
- `activation.method` — HTTP метод.
- `activation.path` — path шаблон (поддерживает placeholders).
- `activation.payload-template` — шаблон body.

## 5. `application.assignment.experimental-endpoints`

Конфигурируемые REST-контракты workflow:

- `enabled`
- `queue-visits-path`
- `visit-details-path`
- `visit-by-id-path`
- `assign-service-path`
- `transfer-visit-path`

Рекомендуется валидировать каждый endpoint в целевом контуре до go-live.

## 6. `micronaut.*`

- `micronaut.server.port` — порт HTTP-сервера сервиса.
- `micronaut.http.client.read-timeout` — timeout клиента к Orchestra/med-robot.

## 7. Практика эксплуатации

1. Для prod хранить креды только в секретах/ENV.
2. Изменяемые контурные параметры: URL, креды, queue id, allowed branches, mapping branch→entry point.
3. Любые изменения `source-entry-point-id-by-branch` и `unknown-doctor-queue-id` оформлять как change request с подтверждением от владельца Orchestra.
