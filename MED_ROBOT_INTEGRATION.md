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

Полная матрица режимов запуска, расписания, dry-run, transfer-only и обхода неполного маршрута вынесена в `OPERATION_MODES.md`.


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

    # Если med-robot недоступен или вернул HTTP/сетевую ошибку,
    # продолжить старым локальным алгоритмом.
    fallback-to-local-on-error: true

    # Если med-robot не выбрал услугу/очередь, продолжить старым локальным алгоритмом.
    fallback-to-local-on-empty-response: true

    # true — принимать только услугу, доступную текущему врачу;
    # false — доверять med-robot как оптимизатору очереди отделения.
    require-doctor-available-service: false

    # Проверять, что очередь из ответа med-robot есть в кэш отделения.
    require-known-queue: true

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

### 1. Старая схема без med-robot

```yaml
application:
  med-robot:
    enabled: false
```

Поведение:

- med-robot не вызывается;
- услуга выбирается только по локальному маршруту визита и доступным врачу услугам;
- если `unservedVisitServices` пустой или не пересекается с услугами врача, визит пропускается в текущем цикле.

Этот режим удобен для первой проверки интеграции с Orchestra: кэш, чтение очереди, `assign-service` и `transfer-visit` можно отладить без внешнего робота.

### 2. Med-robot с JSON-массивом непройденных услуг

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: UNSERVED_SERVICE_IDS_JSON_ARRAY
```

HTTP-запрос:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
Content-Type: application/json
Accept: application/json

[147, 148, 149]
```

Особенности:

- это режим совместимости со старой REST-точкой;
- Doctor Assistant сначала должен локально найти хотя бы одну подходящую услугу;
- если локальная pre-selection не нашла услугу, обращение к med-robot пропускается;
- результат med-robot дополнительно проверяется по настройкам `require-known-queue` и `require-doctor-available-service`.

### 3. Med-robot с номером талона (`text/plain`)

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
```

HTTP-запрос:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}?policy=default
Content-Type: text/plain
Accept: application/json

Щ028
```

Особенности:

- в теле запроса передаётся строка номера талона, а не JSON;
- правильный request `Content-Type` должен быть именно `text/plain`;
- `Accept` остаётся `application/json`, так как med-robot возвращает структуру с `serviceId` и `queueId`;
- локальная pre-selection не обязательна: номер талона считается источником маршрута на стороне med-robot / МИС;
- если локального кандидата нет, `serviceId` в path выбирается из текущей услуги визита или доступных врачу услуг.

### 4. Только события

```yaml
application:
  websocket:
    enabled: true
  assignment:
    polling-enabled: false
    user-service-point-session-start-trigger-enabled: true
```

Поведение:

- цикл запускается только от событий Orchestra;
- задержка минимальная;
- при потере websocket-события страхующего перечитывания очереди не будет.

Режим подходит только после подтверждения стабильности SockJS/STOMP на конкретной инсталляции.

### 5. Только расписание

```yaml
application:
  websocket:
    enabled: false
  assignment:
    polling-enabled: true
    polling-cron: "0 */10 * * * ?"
```

Поведение:

- служба не зависит от событий Orchestra;
- каждая итерация перечитывает доступный контекст и очередь «Врач не назначен»;
- задержка назначения равна интервалу cron.

Режим полезен для начального внедрения, ночных сверок и стендов, где websocket недоступен.

### 6. События + расписание

```yaml
application:
  websocket:
    enabled: true
  assignment:
    polling-enabled: true
    polling-cron: "0 */5 * * * ?"
```

Это основной рекомендуемый production-режим:

1. события обеспечивают быстрый запуск;
2. polling страхует потерянные события и рестарт службы;
3. дедупликация предотвращает повторную обработку одного визита.

### 7. Обход неполного маршрута визита через med-robot

Режим включается сочетанием настроек:

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
  assignment:
    add-missing-robot-service-to-visit: true
```

Сценарий:

1. визит находится в очереди «Врач не назначен»;
2. в локальном `unservedVisitServices` нет фактической медицинской услуги;
3. Doctor Assistant отправляет номер талона в med-robot;
4. med-robot возвращает целевую услугу и очередь;
5. если услуги нет в `unservedVisitServices` и она не является `currentVisitService`, служба добавляет её в визит;
6. затем выполняется обычный `assign-service` и `transfer-visit`.

Если выбранная услуга уже является текущей услугой визита, добавлять её повторно нельзя: используется ветка `transfer-only`.

### 8. Dry-run

```yaml
application:
  assignment:
    dry-run: true
```

Служба читает Orchestra, строит решение и логирует действия, но не отправляет mutating REST. Это самый безопасный режим для проверки новой конфигурации.

### 9. Обработка ошибок med-robot

```yaml
application:
  med-robot:
    error-handling-mode: FALLBACK_TO_LOCAL
```

| Режим | Поведение |
|---|---|
| `FALLBACK_TO_LOCAL` | Ошибка med-robot не останавливает визит: служба продолжает по локальной схеме, если локальный кандидат есть. |
| `SKIP_VISIT` | Визит пропускается в текущем цикле, чтобы не принимать решение без робота. |

Старый флаг `fallback-to-local-on-error` оставлен только для совместимости. В новых конфигурациях используйте `error-handling-mode`.


## Правила безопасности выбора

При включенном med-robot результат дополнительно проверяется:

- выбранная услуга должна присутствовать в непройденном маршруте визита;
- если `require-doctor-available-service=true`, услуга должна входить в доступные услуги текущего врача;
- если `require-known-queue=true`, очередь должна присутствовать в кэше отделения.

Если проверка не пройдена, используется логика возврата к локальному алгоритму.

## Измененные/добавленные компоненты

- `MedRobotProperties` — настройки `application.med-robot`.
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
| med-robot вернул HTTP/сетевую ошибку | `fallback-to-local-on-error=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул HTTP/сетевую ошибку | `fallback-to-local-on-error=false` | визит не назначается в этом цикле. |
| med-robot вернул `null/null`, `0/0` или неполную пару | `fallback-to-local-on-empty-response=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул `null/null`, `0/0` или неполную пару | `fallback-to-local-on-empty-response=false` | визит не назначается в этом цикле. |
| med-robot вернул услугу, которой нет в непройденном маршруте визита в режиме `UNSERVED_SERVICE_IDS_JSON_ARRAY` | `fallback-to-local-on-empty-response=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул услугу, которой нет в локально прочитанном маршруте в режиме `TICKET_NUMBER_PLAIN_TEXT` | не требуется | ответ принимается, потому что источником маршрута является med-robot/МИС по номеру талона. |
| med-robot вернул очередь, которой нет в кэше отделения | `require-known-queue=true` | результат med-robot отклоняется, дальше применяется логика возврата к локальному алгоритму. |
| med-robot вернул услугу вне доступных услуг текущего врача | `require-doctor-available-service=true` | результат med-robot отклоняется, дальше применяется логика возврата к локальному алгоритму. |

## Детерминированность запроса

В режиме `UNSERVED_SERVICE_IDS_JSON_ARRAY` Doctor Assistant передает в med-robot не исходный `HashSet`, а отсортированный набор service id. Это не меняет семантику контракта, потому что med-robot принимает множество услуг, но делает логи и тесты повторяемыми: один и тот же визит формирует одинаковое тело запроса `[301, 302, 303]` и одинаковый диагностический вывод.

В режиме `TICKET_NUMBER_PLAIN_TEXT` Doctor Assistant берет номер талона из ответа очереди ожидания, переносит его в детальную модель визита и отправляет как `text/plain` body. В отличие от JSON-режима, отсутствие локального совпадения между `unservedVisitServices` визита и услугами врача не блокирует обращение к med-robot. Если локального кандидата нет, `serviceId` в path выбирается так: `currentVisitService.serviceId`, если он входит в доступные врачу услуги; иначе первая услуга из отсортированного набора доступных врачу услуг. Если номер талона отсутствует, вызов med-robot не выполняется; дальнейшее поведение определяется флагом `fallback-to-local-on-error`.

## Оформление и терминология в документации

Чтобы документация была единообразной:

- названия настроек всегда пишутся в backticks, например `request-body-mode`;
- значения enum и режимов пишутся как `TICKET_NUMBER_PLAIN_TEXT`, `FALLBACK_TO_LOCAL`, `SKIP_VISIT`;
- REST-методы и заголовки оформляются отдельными блоками `http`;
- «очередь "Врач не назначен"» используется как бизнес-термин, а `unknown-doctor-queue-id` - как имя настройки;
- текстовые примеры body для `text/plain` не оборачиваются в JSON-кавычки.


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
