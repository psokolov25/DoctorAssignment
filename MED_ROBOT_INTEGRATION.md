# Интеграция Doctor Assistant с med-robot

## Назначение

Doctor Assistant может работать в двух режимах выбора услуги и очереди для визита из очереди «врач не назначен»:

1. **Локальный режим** — старая схема: услуга выбирается по маршруту визита, доступным врачу услугам и локальным приоритетам.
2. **Режим med-robot** — Doctor Assistant обращается к med-robot и получает оптимальную пару `serviceId`/`queueId`. Для JSON-контракта перед вызовом по-прежнему нужен локальный предварительный выбор. Для `TICKET_NUMBER_PLAIN_TEXT` локальное совпадение услуги больше не является обязательным: номер талона передается в med-robot как источник маршрута, а `serviceId` для path выбирается из локального кандидата, текущей услуги визита или доступных врачу услуг.

Интеграция сделана обратимо: при отключенном `application.med-robot.enabled` сервис ведет себя по прежней схеме.

## REST-контракт med-robot

По исходникам `med-robot-Revision2` используются две перегруженные REST-точки с одним path. Выбор точки выполняется по `Content-Type`.

Старый режим `UNSERVED_SERVICE_IDS_JSON_ARRAY`:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
Content-Type: application/json

[301, 302, 303]
```

Новый режим `TICKET_NUMBER_PLAIN_TEXT`:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}?policy=default
Content-Type: text/plain

A-1001
```

Где:

- `branchId` — отделение Orchestra/Doctor Assistant;
- `serviceId` в path — текущая услуга для контекста med-robot: сначала локально выбранная услуга, если она есть; иначе в `TICKET_NUMBER_PLAIN_TEXT` используется `currentVisitService.serviceId`, если он доступен врачу; иначе детерминированно берется первая услуга из доступных врачу услуг;
- для `UNSERVED_SERVICE_IDS_JSON_ARRAY` тело запроса — JSON-массив идентификаторов непройденных услуг визита;
- для `TICKET_NUMBER_PLAIN_TEXT` тело запроса — строка номера талона визита;
- `plain-text-policy` передается как query-параметр `policy` во вторую точку med-robot.

Ответ:

```json
{
  "serviceId": 302,
  "queueId": 902
}
```

Если med-robot вернул `null/null`, `0/0`, ошибку или недопустимую пару, поведение зависит от флагов возврата к локальному алгоритму.

## Документационные диаграммы

Для внедрения и сопровождения интеграции подготовлен расширенный набор диаграмм. Все подписи даны на русском языке; программные имена оставлены только там, где они нужны для сопоставления с кодом, REST-точками и настройками.

Визуальное оформление приведено к единому UML-приближенному профилю: прямоугольные элементы без декоративного скругления, ортогональные связи, фиксированные размеры блоков, ручные переносы длинных подписей, отдельные зоны для `alt`/`loop` на sequence-диаграммах и вынесенные примечания. Это сделано специально, чтобы при просмотре в Markdown, XWiki, GitLab или IDE не было наездов подписей, скрытых текстов и пересечений стрелок с элементами.

| Диаграмма | Что показывает |
|---|---|
| `docs/diagrams/architecture-overview.svg` | общую архитектуру Doctor Assistant, Orchestra и med-robot |
| `docs/diagrams/assignment-sequence.svg` | последовательность назначения визита врачу |
| `docs/diagrams/cache-refresh-sequence.svg` | пересборку локального кэша отделения перед принятием решений |
| `docs/diagrams/deployment-view.svg` | схему внедрения Doctor Assistant рядом с Orchestra и med-robot |
| `docs/diagrams/med-robot-selection-sequence.svg` | детальную последовательность локального предварительного выбора, REST-вызова med-robot, проверок ответа и fallback |
| `docs/diagrams/polling-reconciliation-sequence.svg` | плановую reconciliation-обработку по cron и защиту от конкуренции с event-driven циклом |
| `docs/diagrams/domain-class-diagram.svg` | ключевые классы доменного контура назначения и их связи |
| `docs/diagrams/package-dependency-map.svg` | пакеты сервиса и допустимые направления зависимостей между ними |
| `docs/diagrams/orchestration-swimlane.svg` | распределение ответственности между Orchestra, Doctor Assistant и med-robot |
| `docs/diagrams/visit-lifecycle-state.svg` | состояния визита от очереди «врач не назначен» до готовности к вызову |
| `docs/diagrams/med-robot-fallback-decision.svg` | дерево решений при ответе med-robot и возврате к локальному алгоритму |
| `docs/diagrams/operation-modes-map.svg` | режимы запуска: события, расписание, смешанный режим, с роботом и без робота |
| `docs/diagrams/data-contract-map.svg` | REST-контракт med-robot, тело запроса, ответ и проверки валидности |
| `docs/diagrams/failure-recovery-flow.svg` | поведение при отказах без остановки всей службы |
| `docs/diagrams/rest-mutation-flow.svg` | REST-мутации Orchestra: активация контекста, назначение услуги и перевод визита |
| `docs/diagrams/observability-checklist.svg` | какие признаки должны быть видны в логах при эксплуатации |

Исходники лежат рядом в `docs/plantuml/*.puml`, поэтому диаграммы можно редактировать как код и версионировать вместе с изменениями интеграции.

## Настройки application.yml

```yaml
application:
  med-robot:
    # false — старая локальная схема без обращения к med-robot.
    # true  — уточнять услугу и очередь через med-robot.
    enabled: false

    # Базовый URL REST API med-robot.
    url: http://localhost:8082

    # Контракт из med-robot.
    # Оба режима используют один path, но разные Content-Type.
    optimal-service-path: /prorobot/optimalqueue/{branchId}/service/{serviceId}

    # UNSERVED_SERVICE_IDS_JSON_ARRAY — JSON-массив услуг.
    # TICKET_NUMBER_PLAIN_TEXT       — text/plain строка номера талона.
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT

    # Query-параметр policy для text/plain точки med-robot.
    plain-text-policy: default

    # Опционально, если REST API med-robot закрыт базовой HTTP-авторизацией (Basic Auth).
    # username: robot
    # password: secret

    # Действие при HTTP/сетевой/контрактной ошибке med-robot:
    #   FALLBACK_TO_LOCAL — продолжить текущий визит по локальной схеме без робота;
    #   SKIP_VISIT       — пропустить текущий визит и перейти к следующему.
    error-handling-mode: FALLBACK_TO_LOCAL

    # Deprecated-алиас старого boolean-свойства.
    # fallback-to-local-on-error: true

    # Если med-robot не выбрал услугу/очередь, продолжить старым локальным алгоритмом.
    fallback-to-local-on-empty-response: true

    # true — принимать только услугу, доступную текущему врачу;
    # false — доверять med-robot как оптимизатору очереди отделения.
    require-doctor-available-service: false

    # Проверять, что очередь из ответа med-robot есть в кэш отделения.
    require-known-queue: true

  assignment:
    # Если med-robot вернул новую для маршрута услугу, сначала добавить ее в визит.
    add-missing-robot-service-to-visit: true
    add-missing-robot-service-failure-mode: CONTINUE_WITH_ASSIGN
    source-entry-point-id-by-branch:
      "1": 1
    experimental-endpoints:
      # POST /rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceOrigId}/
      add-service-path: /rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceOrigId}/

  websocket:
    # Event-driven режим через события Orchestra.
    # Для режима только по расписанию выключить.
    enabled: true

  assignment:
    # Включает периодическую reconciliation-задачу.
    polling-enabled: true

    # Пример запуска раз в 10 минут.
    polling-cron: "0 */10 * * * ?"
```

## Типовые режимы эксплуатации

### Старая схема без med-robot

```yaml
application:
  med-robot:
    enabled: false
```

Doctor Assistant не обращается к med-robot и назначает визит как раньше.

### События + med-robot

```yaml
application:
  med-robot:
    enabled: true
  websocket:
    enabled: true
  assignment:
    polling-enabled: true
```

Основной запуск идет по событиям Orchestra, опрос по расписанию остается страховкой от потерянных событий.

### Только события, без расписания

```yaml
application:
  websocket:
    enabled: true
  assignment:
    polling-enabled: false
```

`PollingReconciliationJob` не выполняет доменную обработку.

### Только расписание, например раз в 10 минут

```yaml
application:
  websocket:
    enabled: false
  assignment:
    polling-enabled: true
    polling-cron: "0 */10 * * * ?"
```

Doctor Assistant не подписывается на websocket-события, а периодически проходит по runtime cache рабочих мест и запускает assignment cycle.

## Правила безопасности выбора

При включенном med-robot результат дополнительно проверяется:

- в режиме `UNSERVED_SERVICE_IDS_JSON_ARRAY` выбранная услуга должна присутствовать в локально прочитанном непройденном маршруте визита;
- в режиме `TICKET_NUMBER_PLAIN_TEXT` отсутствие услуги в локально прочитанном маршруте не блокирует ответ med-robot, потому что источником маршрута считается med-robot/МИС по номеру талона;
- если принятый ответ med-robot указывает услугу, которой нет в `unservedVisitServices`, выбранная услуга **не равна текущей `currentVisitService`** и включено `application.assignment.add-missing-robot-service-to-visit=true`, Doctor Assistant перед `assign-service` добавляет эту услугу в визит через `POST /rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceOrigId}/`; при ошибке этого POST поведение задается `application.assignment.add-missing-robot-service-failure-mode`;
- если `require-doctor-available-service=true`, услуга должна входить в доступные услуги текущего врача;
- если `require-known-queue=true`, очередь должна присутствовать в кэше отделения.

Если проверка не пройдена, используется логика возврата к локальному алгоритму.

## Измененные/добавленные компоненты

- `MedRobotProperties` / `MedRobotErrorHandlingMode` — настройки `application.med-robot`, включая поведение при ошибке робота.
- `MedRobotRestClient` — Micronaut REST-клиент к REST-точке med-robot.
- `MedRobotRestConfiguration` — опциональный фильтр базовой HTTP-авторизации (Basic Auth).
- `MedRobotOptimalServiceGateway` / `MedRobotOptimalServiceGatewayImpl` — gateway-слой интеграции.
- `DoctorServiceSelectionService` — доменная абстракция выбора услуги.
- `MedRobotAwareDoctorServiceSelectionService` — wrapper над старым `DoctorServiceMatcher` с обращением к med-robot.
- `AssignmentProperties.pollingEnabled` — отдельный флаг включения/отключения расписания.
- `PollingReconciliationJob` — теперь проверяет `assignment.enabled` и `assignment.polling-enabled`.
- `RuntimeConfigurationLogger` — на старте пишет эффективные флаги med-robot и опроса по расписанию.

## Матрица возврата к локальному алгоритму

| Ситуация | Настройка | Поведение |
| --- | --- | --- |
| `application.med-robot.enabled=false` | не требуется | med-robot не вызывается, работает старый локальный алгоритм. |
| Локальный алгоритм не нашел предварительную услугу в режиме `UNSERVED_SERVICE_IDS_JSON_ARRAY` | не требуется | med-robot не вызывается, потому что JSON-контракт зависит от локально прочитанного маршрута визита. |
| Локальный алгоритм не нашел предварительную услугу в режиме `TICKET_NUMBER_PLAIN_TEXT` | не требуется | med-robot вызывается по номеру талона, если есть `ticketId` и удалось определить `serviceId` для path из `currentVisitService` или доступных врачу услуг. |
| med-robot вернул HTTP/сетевую/контрактную ошибку, включая 415 | `error-handling-mode=FALLBACK_TO_LOCAL` | используется локально выбранная услуга и очередь, то есть текущий визит продолжается по схеме «без робота». |
| med-robot вернул HTTP/сетевую/контрактную ошибку, включая 415 | `error-handling-mode=SKIP_VISIT` | текущий визит не назначается в этом цикле, цикл переходит к следующему визиту очереди. |
| med-robot вернул HTTP/сетевую/контрактную ошибку | `fallback-to-local-on-error=true/false` | deprecated-алиас: `true` соответствует `FALLBACK_TO_LOCAL`, `false` соответствует `SKIP_VISIT`. |
| med-robot вернул `null/null`, `0/0` или неполную пару | `fallback-to-local-on-empty-response=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул `null/null`, `0/0` или неполную пару | `fallback-to-local-on-empty-response=false` | визит не назначается в этом цикле. |
| med-robot вернул услугу, которой нет в непройденном маршруте визита в режиме `UNSERVED_SERVICE_IDS_JSON_ARRAY` | `fallback-to-local-on-empty-response=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул услугу, которой нет в локально прочитанном маршруте в режиме `TICKET_NUMBER_PLAIN_TEXT`, и она не является текущей услугой визита | `add-missing-robot-service-to-visit=true` | ответ принимается; перед назначением Doctor Assistant добавляет услугу в визит через `POST /entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceOrigId}/`, затем продолжает `assign-service` и `transfer-visit`. |
| med-robot вернул текущую `currentVisitService`, но этой услуги нет в `unservedVisitServices` | любое значение `add-missing-robot-service-to-visit` | дополнительный `POST add service to visit` не выполняется, потому что услуга уже текущая; сервис использует `transfer-only`. |
| med-robot вернул услугу, которой нет в локально прочитанном маршруте в режиме `TICKET_NUMBER_PLAIN_TEXT` | `add-missing-robot-service-to-visit=false` | ответ принимается без предварительного добавления услуги; используется прежнее поведение. |
| med-robot вернул очередь, которой нет в кэше отделения | `require-known-queue=true` | результат med-robot отклоняется, дальше применяется логика возврата к локальному алгоритму. |
| med-robot вернул услугу вне доступных услуг текущего врача | `require-doctor-available-service=true` | результат med-robot отклоняется, дальше применяется логика возврата к локальному алгоритму. |

## Детерминированность запроса

В режиме `UNSERVED_SERVICE_IDS_JSON_ARRAY` Doctor Assistant передает в med-robot не исходный `HashSet`, а отсортированный набор service id. Это не меняет семантику контракта, потому что med-robot принимает множество услуг, но делает логи и тесты повторяемыми: один и тот же визит формирует одинаковое тело запроса `[301, 302, 303]` и одинаковый диагностический вывод.

В режиме `TICKET_NUMBER_PLAIN_TEXT` Doctor Assistant берет номер талона из ответа очереди ожидания, переносит его в детальную модель визита и отправляет как `text/plain` body. Для Micronaut declarative client это зафиксировано как `@Produces(text/plain)` на plain-text методе клиента: именно эта аннотация формирует request `Content-Type`. `@Consumes(application/json)` оставлен для `Accept`, потому что med-robot возвращает JSON-ответ с выбранными `serviceId`/`queueId`. В отличие от JSON-режима, отсутствие локального совпадения между `unservedVisitServices` визита и услугами врача не блокирует обращение к med-robot. Если локального кандидата нет, `serviceId` в path выбирается так: `currentVisitService.serviceId`, если он входит в доступные врачу услуги; иначе первая услуга из отсортированного набора доступных врачу услуг. Если номер талона отсутствует, вызов med-robot не выполняется; дальнейшее поведение определяется настройкой `error-handling-mode`.

## Тестовое покрытие

Интеграция закрыта следующими группами тестов:

- `MedRobotAwareDoctorServiceSelectionServiceTest` — unit-тесты локального/robot выбора и режимов возврата к локальному алгоритму, валидации
  неизвестной очереди, проверки услуги вне маршрута и строгого режима `require-doctor-available-service`;
- `AutonomousMedicalExamAssignmentServiceTest` — сквозные тесты доменного цикла назначения с med-robot и возврат к локальному алгоритму при
  ошибке робота;
- `MedRobotPropertiesTest` — безопасные значения по умолчанию для `application.med-robot`;
- `PollingReconciliationJobTest` — явное включение/выключение расписания через `application.assignment.polling-enabled`;
- `MedRobotRestClientContractTest` — фиксация REST-контракта клиента med-robot;
- `MedRobotRestConfigurationTest` — проверка Basic Auth фильтра для REST-вызовов med-robot;
- `DocumentationAssetsTest` — контроль UTF-8, русских подписей, единого визуального стиля диаграмм, наличия SVG/PUML-файлов и ссылок на них из `README.md` и документа по med-robot.
