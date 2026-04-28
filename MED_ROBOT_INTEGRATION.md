# Интеграция Doctor Assistant с med-robot

## Назначение

Doctor Assistant может работать в двух режимах выбора услуги и очереди для визита из очереди «врач не назначен»:

1. **Локальный режим** — старая схема: услуга выбирается по маршруту визита, доступным врачу услугам и локальным приоритетам.
2. **Режим med-robot** — Doctor Assistant отправляет в med-robot либо JSON-массив непройденных услуг, либо номер талона в `text/plain` режиме и получает оптимальную пару `serviceId`/`queueId`. После этого штатный executor назначает выбранную услугу визиту и переводит визит в выбранную очередь.

Интеграция сделана обратимо: при отключенном `application.med-robot.enabled` сервис ведет себя по прежней схеме.

## REST-контракт med-robot

Используется одна REST-точка:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
```

Где:

- `branchId` — отделение Orchestra/Doctor Assistant;
- `serviceId` в path — предварительная текущая услуга;
- формат тела определяется настройкой `application.med-robot.request-body-mode`.

### JSON-режим

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
Content-Type: application/json
Accept: application/json

[301, 302, 303]
```

### Plain text режим по номеру талона

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}?policy=default
Content-Type: text/plain
Accept: application/json

Щ028
```

В Micronaut declarative client для исходящего запроса важно не путать аннотации: `@Produces`
задает `Content-Type`, а `@Consumes` задает `Accept`. Поэтому plain text метод клиента должен
быть объявлен как `@Produces(text/plain)` + `@Consumes(application/json)`.

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
    optimal-service-path: /prorobot/optimalqueue/{branchId}/service/{serviceId}

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
| Локальный алгоритм не нашел предварительную услугу | не требуется | med-robot не вызывается, потому что в path REST-точки нужен текущий `serviceId`. |
| med-robot вернул HTTP/сетевую ошибку | `fallback-to-local-on-error=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул HTTP/сетевую ошибку | `fallback-to-local-on-error=false` | визит не назначается в этом цикле. |
| med-robot вернул `null/null`, `0/0` или неполную пару | `fallback-to-local-on-empty-response=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул `null/null`, `0/0` или неполную пару | `fallback-to-local-on-empty-response=false` | визит не назначается в этом цикле. |
| med-robot вернул услугу, которой нет в непройденном маршруте визита | `fallback-to-local-on-empty-response=true` | используется локально выбранная услуга и очередь. |
| med-robot вернул очередь, которой нет в кэше отделения | `require-known-queue=true` | результат med-robot отклоняется, дальше применяется логика возврата к локальному алгоритму. |
| med-robot вернул услугу вне доступных услуг текущего врача | `require-doctor-available-service=true` | результат med-robot отклоняется, дальше применяется логика возврата к локальному алгоритму. |

## Детерминированность запроса

Doctor Assistant передает в med-robot не исходный `HashSet`, а отсортированный набор service id. Это не меняет семантику
контракта, потому что med-robot принимает множество услуг, но делает логи и тесты повторяемыми: один и тот же визит
формирует одинаковое тело запроса `[301, 302, 303]` и одинаковый диагностический вывод.

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
