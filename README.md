# Med Doctor Assignment Service

Служба автоматического назначения визитов из очереди **«врач не назначен»** на врача, который занял рабочее место в **QMatic Orchestra 6**. Основной сценарий - автономные медицинские осмотры: пациент уже находится в маршруте визита, но конкретный врач еще не назначен, и служба должна быстро определить подходящую услугу/очередь и перевести визит без участия оператора.

## Быстрая навигация

| Документ | Для кого | Что внутри |
|---|---|---|
| `README.md` | аналитик, разработчик, внедрение, сопровождение | назначение службы, архитектура, основной алгоритм, ключевые настройки |
| `MED_ROBOT_INTEGRATION.md` | разработчик, интегратор med-robot | REST-контракт med-robot, режим `text/plain`, fallback, добавление отсутствующей услуги |
| `CONFIGURATION_REFERENCE.md` | DevOps, внедрение | полный справочник параметров `application.yml` |
| `ORCHESTRA_REST_CONTRACTS.md` | разработчик, интегратор Orchestra | какие REST-точки Orchestra вызывает служба, методы, payload, типовые ошибки |
| `DEPLOYMENT.md` | DevOps | сборка, запуск, production-конфигурация, systemd-пример, rollout |
| `RUNBOOK.md` | сопровождение, 2-я линия | ежедневные проверки, диагностика по логам, известные инциденты |
| `FIXES_2026_04_15.md` | разработчик, сопровождение | история важных исправлений по логам апреля 2026 |

## Назначение и границы ответственности

Служба не заменяет Orchestra и не меняет справочники услуг/очередей. Она выполняет узкий orchestration workflow:

1. Определяет, что врач готов принимать визиты на конкретной точке обслуживания.
2. Находит очередь **«врач не назначен»** в отделении.
3. Берет из нее ожидающие визиты.
4. Для каждого визита читает текущую услугу, номер талона и список необслуженных услуг.
5. Выбирает оптимальную услугу и целевую очередь локально или через med-robot.
6. При необходимости добавляет выбранную med-robot услугу в маршрут визита.
7. Выполняет `assign-service`, если выбранная услуга не является текущей.
8. Выполняет `transfer-visit` в очередь выбранной услуги.
9. Пишет в бизнес-лог все ключевые решения и REST-мутации.

Служба сознательно не хранит долговременное состояние визитов в собственной БД. Основной источник истины - Orchestra; локально используются только кэши справочников, runtime-состояние service point и кратковременная дедупликация.

## Ключевые возможности текущей версии

- Работа с Orchestra 6 через REST и опционально через SockJS/STOMP события.
- Polling fallback: служба может работать только по расписанию, даже если websocket выключен или нестабилен.
- Локальный выбор услуги по маршруту визита и доступным врачу услугам.
- Интеграция с med-robot в двух режимах:
  - `UNSERVED_SERVICE_IDS_JSON_ARRAY`: тело запроса - JSON-массив id необслуженных услуг;
  - `TICKET_NUMBER_PLAIN_TEXT`: тело запроса - plain text строка номера талона.
- Корректный `Content-Type` для plain text REST-точки med-robot: `Content-Type: text/plain`, `Accept: application/json`.
- Настраиваемое поведение при ошибке med-robot: fallback к локальной схеме или пропуск визита.
- Добавление услуги в маршрут визита, если med-robot выбрал услугу, которой нет в `unservedVisitServices`, и она не является `currentVisitService`.
- Настраиваемое поведение при ошибке `POST add service to visit`: продолжить assign/transfer, пропустить визит или пробросить ошибку.
- Защита от повторной обработки одного визита и от параллельной обработки одного branch.
- Стартовый аудит effective-конфигурации, включая marker сборки, source entry point id и режимы med-robot.

## Технологический стек

| Компонент | Версия / подход |
|---|---|
| Java | 8 |
| Framework | Micronaut 3.5.2 |
| Сборка | Maven / Maven Wrapper |
| REST-клиент | Micronaut HTTP Client / Netty |
| Websocket | Spring SockJS/STOMP client |
| JSON | Jackson |
| Логи | SLF4J + Logback |

## Структура проекта

```text
src/main/java/com/qsystems/meddoctorassignment
├── adapter
│   ├── gateway            # интерфейсы внешних интеграций
│   ├── gateway/impl       # REST-реализации gateway-слоя
│   ├── medrobot           # REST-клиент и конфигурация med-robot
│   ├── medrobot/dto       # DTO ответа med-robot
│   ├── orchestra          # REST-клиент Orchestra и cookie/auth фильтры
│   └── orchestra/dto      # DTO справочников Orchestra
├── branchgetter           # выбор branch id для кэширования
├── cache                  # построение и хранение branch cache
├── cache/model            # модели кэша справочников
├── cache/service          # сервис обновления кэша
├── config                 # typed configuration properties
├── diagnostics            # стартовый аудит конфигурации
├── domain/model           # доменные модели визита и выбранной услуги
├── domain/service         # доменные интерфейсы
├── domain/service/impl    # реализация алгоритма назначения
├── event                  # обработка событий Orchestra
├── model/event            # нормализованные события и контекст врача
├── schedule               # polling reconciliation job
├── util                   # locks, deduplication, processed visit registry
└── websocket              # SockJS/STOMP подключение и parsing кадров
```

## Общая архитектура

![Общая архитектура сервиса](docs/diagrams/architecture-overview.svg)

Исходник диаграммы: `docs/plantuml/architecture-overview.puml`.

Сервис разделен на несколько контуров:

- **Trigger контур** - websocket события и polling job запускают assignment cycle.
- **Cache контур** - справочники Orchestra собираются в `BranchAssignmentCache`.
- **Selection контур** - `DoctorServiceSelectionService` выбирает `serviceId`/`queueId` локально или через med-robot.
- **Mutation контур** - `VisitWorkflowGateway` выполняет REST-мутации над визитом в Orchestra.
- **Diagnostics контур** - стартовый аудит и бизнес-логи позволяют проверить, какой код и какая конфигурация реально запущены.

## Диаграммы

Диаграммы лежат в двух форматах:

- `docs/plantuml/*.puml` - редактируемые исходники PlantUML;
- `docs/diagrams/*.svg` - готовые SVG для README, wiki и передачи во внедрение.

| Диаграмма | SVG | PlantUML |
|---|---|---|
| Общая архитектура | `docs/diagrams/architecture-overview.svg` | `docs/plantuml/architecture-overview.puml` |
| Диаграмма пакетов | `docs/diagrams/package-dependency-map.svg` | `docs/plantuml/package-dependency-map.puml` |
| Доменная диаграмма классов | `docs/diagrams/domain-class-diagram.svg` | `docs/plantuml/domain-class-diagram.puml` |
| Назначение визита врачу | `docs/diagrams/assignment-sequence.svg` | `docs/plantuml/assignment-sequence.puml` |
| Выбор через med-robot | `docs/diagrams/med-robot-selection-sequence.svg` | `docs/plantuml/med-robot-selection-sequence.puml` |
| Пересборка кэша | `docs/diagrams/cache-refresh-sequence.svg` | `docs/plantuml/cache-refresh-sequence.puml` |
| Polling reconciliation | `docs/diagrams/polling-reconciliation-sequence.svg` | `docs/plantuml/polling-reconciliation-sequence.puml` |
| Deployment view | `docs/diagrams/deployment-view.svg` | `docs/plantuml/deployment-view.puml` |
| Swimlane процесса | `docs/diagrams/orchestration-swimlane.svg` | `docs/plantuml/orchestration-swimlane.puml` |
| Состояния визита | `docs/diagrams/visit-lifecycle-state.svg` | `docs/plantuml/visit-lifecycle-state.puml` |
| Дерево решений med-robot/fallback | `docs/diagrams/med-robot-fallback-decision.svg` | `docs/plantuml/med-robot-fallback-decision.puml` |
| Режимы запуска | `docs/diagrams/operation-modes-map.svg` | `docs/plantuml/operation-modes-map.puml` |
| Карта REST-контракта med-robot | `docs/diagrams/data-contract-map.svg` | `docs/plantuml/data-contract-map.puml` |
| Отказы и восстановление | `docs/diagrams/failure-recovery-flow.svg` | `docs/plantuml/failure-recovery-flow.puml` |
| REST-мутации Orchestra | `docs/diagrams/rest-mutation-flow.svg` | `docs/plantuml/rest-mutation-flow.puml` |
| Наблюдаемость | `docs/diagrams/observability-checklist.svg` | `docs/plantuml/observability-checklist.puml` |

## Основной алгоритм назначения

![Последовательность назначения визита врачу](docs/diagrams/assignment-sequence.svg)

Упрощенный алгоритм одного цикла:

1. Проверить `application.assignment.enabled`.
2. Проверить, разрешен ли trigger: `POLLING`, `USER_SESSION_READY`, `WORK_PROFILE_EXPANDED` и т.д.
3. Захватить branch-level lock, чтобы два потока не меняли визиты одного отделения одновременно.
4. Убедиться, что branch входит в `allowed-branches`.
5. Проверить актуальность `BranchAssignmentCache`; при необходимости перестроить кэш.
6. Восстановить `DoctorContext`: `branchId`, `servicePointId`, `staffId`, `workProfileId`.
7. Найти услуги, доступные врачу через очереди рабочего профиля.
8. Прочитать визиты из очереди `unknown-doctor-queue-id`.
9. Для каждого визита:
   - прочитать `VisitDetails`;
   - выбрать услугу локально или через med-robot;
   - при необходимости добавить отсутствующую услугу в маршрут;
   - выполнить `assign-service`, если выбранная услуга не равна текущей;
   - выполнить `transfer-visit`;
   - выполнить post-check фактической очереди визита.
10. Освободить lock и записать итог цикла в лог.

## Как выбирается услуга

### Локальная схема

Локальная схема анализирует:

- `unservedVisitServices` визита;
- `currentVisitService`;
- доступные врачу услуги по `workProfileId`;
- очередь услуги из branch cache;
- `routeOrder` и `service-priority-by-key`.

Она подходит для сценария, когда вся информация о маршруте визита уже корректно отражена в Orchestra.

### Схема через med-robot

Если `application.med-robot.enabled=true`, локальный выбор становится предварительным, а итоговую пару `serviceId`/`queueId` может вернуть med-robot.

В режиме `TICKET_NUMBER_PLAIN_TEXT` med-robot получает номер талона. Этот режим важен для случаев, когда `unservedVisitServices` пустой или не содержит услугу, которую реально должен выбрать робот.

Подробно: `MED_ROBOT_INTEGRATION.md`.

## Правило добавления услуги, выбранной med-robot

Если med-robot вернул услугу, которой нет в `unservedVisitServices`, служба может добавить ее в маршрут визита перед assign/transfer.

Услуга добавляется только если одновременно выполняются условия:

1. выбранная услуга пришла именно от med-robot (`selectionReason` начинается с `med-robot-`);
2. `application.assignment.add-missing-robot-service-to-visit=true`;
3. выбранной услуги нет в `VisitDetails.unservedServices`;
4. выбранная услуга **не равна** `VisitDetails.currentServiceId`.

Если услуга уже является текущей, `POST add service to visit` не выполняется: служба сразу переходит к transfer-only workflow.

## REST-мутации Orchestra

![REST-мутации Orchestra при назначении визита](docs/diagrams/rest-mutation-flow.svg)

Текущие endpoint-ы задаются через `application.assignment.experimental-endpoints`:

| Операция | Метод | Путь по умолчанию | Назначение |
|---|---:|---|---|
| Список визитов очереди | GET | `/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/full/` | прочитать кандидатов из очереди «врач не назначен» |
| Детали визита | GET | `/rest/entrypoint/branches/{branchId}/visits/{visitId}/` | прочитать маршрут, текущую услугу, номер талона |
| Добавить услугу | POST | `/rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceOrigId}/` | добавить услугу, выбранную med-robot, в маршрут визита |
| Назначить услугу | PUT | `/rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceId}/` | сделать услугу текущей для визита |
| Перевести визит | PUT | `/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/` | перевести визит в очередь выбранной услуги |
| Перечитать визит | GET | `/rest/entrypoint/branches/{branchId}/visits/{visitId}/` | defensive recheck и post-check |

Подробно: `ORCHESTRA_REST_CONTRACTS.md`.

## Минимальная рабочая конфигурация

```yaml
application:
  orchestra:
    url: http://192.168.7.135:8080
    username: superadmin
    password: 11November2024
    branches-for-cache: "1"
    replay-mutation-cookies: false

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

  websocket:
    enabled: false

  assignment:
    enabled: true
    unknown-doctor-queue-id: 312
    polling-enabled: true
    polling-cron: "0 */1 * * * ?"
    dry-run: false
    allowed-branches: [ 1 ]
    source-entry-point-id-by-branch:
      "1": 1
    add-missing-robot-service-to-visit: true
    add-missing-robot-service-failure-mode: CONTINUE_WITH_ASSIGN
    experimental-endpoints:
      enabled: true
      queue-visits-path: "/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/full/"
      visit-details-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/"
      visit-by-id-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/"
      assign-service-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceId}/"
      add-service-path: "/rest/entrypoint/branches/{branchId}/visits/{visitId}/services/{serviceOrigId}/"
      transfer-visit-path: "/rest/entrypoint/branches/{branchId}/queues/{queueId}/visits/"
```

Полный справочник параметров: `CONFIGURATION_REFERENCE.md`.

## Сборка и запуск

### Сборка

```bash
./mvnw clean package
```

На Windows:

```bat
mvnw.cmd clean package
```

### Запуск jar

```bash
java -jar target/med-doctor-assignment-service-1.0.0-SNAPSHOT.jar
```

### Запуск с внешней конфигурацией

```bash
java \
  -Dmicronaut.config.files=/opt/med-doctor-assignment/application.yml \
  -jar target/med-doctor-assignment-service-1.0.0-SNAPSHOT.jar
```

Для первого запуска в реальном контуре рекомендуется выставить:

```yaml
application:
  assignment:
    dry-run: true
```

После проверки логов и REST-контрактов перевести в:

```yaml
application:
  assignment:
    dry-run: false
```

## Что проверять после старта

В обычном логе должен быть стартовый аудит вида:

```text
Runtime configuration marker=2026-04-27-entrypoint-source-id-diagnostics ...
```

В этой строке особенно важны поля:

- `gatewayClass` и `gatewayCodeSource` - подтверждают, какой jar реально запущен;
- `resolvedSourceEntryPointIds=1->1` - показывает, какой `fromId` будет передан в transfer для branch 1;
- `medRobotRequestBodyMode=TICKET_NUMBER_PLAIN_TEXT` - подтверждает режим передачи номера талона;
- `medRobotErrorHandlingMode=FALLBACK_TO_LOCAL` или `SKIP_VISIT` - подтверждает стратегию ошибки med-robot;
- `addMissingRobotServiceToVisit=true` - подтверждает включение добавления услуги в маршрут;
- `addMissingRobotServiceFailureMode=CONTINUE_WITH_ASSIGN` - подтверждает поведение при ошибке `POST add-service`.

## Типовые бизнес-логи

### Вызов med-robot по номеру талона

```text
Request med-robot optimal service branch=1 currentService=148 bodyMode=TICKET_NUMBER_PLAIN_TEXT ticketNumber=Щ044 policy=default
```

В HTTP-логе для такого вызова должны быть заголовки:

```text
Content-Type: text/plain
Accept: application/json
```

### med-robot выбрал услугу

```text
Med-robot selected service=39 queue=901 visit=30283 routeOrder=null localService=null localQueue=null currentService=148
```

### Добавление отсутствующей услуги

```text
Selected med-robot service 39 is absent in visit 30283 unserved route and is not current service. Add service to visit before assign/transfer.
Add service to visit request branchId=1 visitId=30283 serviceId=39 path=/rest/entrypoint/branches/1/visits/30283/services/39/ payload=<empty>
```

### Transfer с source entry point

```text
Transfer visit request branchId=1 visitId=30283 sourceQueueId=312 targetQueueId=901 sourceEntryPointId=1 path=/rest/entrypoint/branches/1/queues/901/visits/ payload={visitId=30283, fromBranchId=1, fromId=1}
```

Если в ошибке фигурирует число, сначала проверьте, к какой сущности оно относится: `serviceId`, `queueId`, `entryPointId`, `branchId` или `visitId`. Для transfer именно `sourceEntryPointId` попадает в payload как `fromId`.

## Рекомендуемый порядок внедрения

1. Собрать jar на целевой версии Java 8.
2. Подготовить отдельный `application.yml` для стенда.
3. Запустить с `dry-run: true`.
4. Проверить стартовый runtime-аудит.
5. Проверить кэш справочников branch.
6. Проверить, что polling видит визиты из очереди «врач не назначен».
7. Проверить вызов med-robot и заголовки `Content-Type`/`Accept`.
8. Проверить `add-service`, `assign-service`, `transfer-visit` на одном тестовом визите.
9. Включить `dry-run: false`.
10. Первые циклы сопровождать по `med-robot-busines.log` и HTTP TRACE-логу.

Подробный runbook: `RUNBOOK.md`.

## Известные интеграционные особенности Orchestra 6

- `SERVICE_POINT_OPEN` может приходить раньше полной готовности рабочего профиля; поэтому по умолчанию он не запускает mutating workflow.
- `USER_SERVICE_POINT_SESSION_START -> SET_WORK_PROFILE` считается более надежной связкой для старта обработки.
- Ответ `assign-service` с HTTP 200 может содержать `userState=INACTIVE` или `NO_STARTED_SERVICE_POINT_SESSION`; служба анализирует body и может считать такой ответ контекстной ошибкой.
- `transfer-visit` может штатно возвращать `204 No Content`; это считается успехом.
- Cookie, полученные на mutating PUT/POST, по умолчанию не переиспользуются, потому что на части инсталляций это приводит к 403.
- Поле `fromId` в payload transfer в текущей интеграции трактуется как source entry point id и задается через `source-entry-point-id-by-branch`.

## Контрольные файлы логов

В архиве есть логи, которые использовались для анализа интеграции:

- `log/med-robot.log` - общий лог службы;
- `log/med-robot-busines.log` - бизнес-решения и ключевые события алгоритма;
- `log/med-robot-http.log` - HTTP-вызовы Orchestra/med-robot;
- `log/archived/*` - архивные логи по датам;
- `around_1509_business.log` - выделенный фрагмент с проблемой пропуска med-robot до исправления plain text режима.

## Правила изменения документации

При каждом изменении алгоритма или REST-контракта нужно обновлять как минимум:

1. `README.md` - если меняется общий workflow или quick start.
2. `CONFIGURATION_REFERENCE.md` - если добавлен/изменен параметр `application.yml`.
3. `MED_ROBOT_INTEGRATION.md` - если меняется med-robot контракт, fallback или selection rule.
4. `ORCHESTRA_REST_CONTRACTS.md` - если меняется REST-вызов Orchestra.
5. `RUNBOOK.md` - если появился новый типовой инцидент или диагностический log marker.
