# Режимы работы Med Doctor Assignment Service

Документ описывает рабочие режимы службы назначения врача для автономных медосмотров: как запускается цикл, как выбирается услуга, как используется `med-robot`, что означает обход очереди «Врач не назначен», как работает обработка по расписанию и какие защитные режимы должны быть включены в production.

## 1. Основные понятия

| Термин | Значение |
|---|---|
| **Очередь «Врач не назначен»** | Стартовая очередь, из которой служба забирает визиты для автоматического распределения. Задаётся настройкой `application.assignment.unknown-doctor-queue-id`. |
| **Текущая услуга визита** | Услуга, которая сейчас указана в карточке визита как активная. В логах обычно видна как `currentVisitService`. |
| **Непройденные услуги** | Локально прочитанный маршрут визита: список `unservedVisitServices`. В старой схеме именно он определял, какую услугу можно назначить врачу. |
| **Целевая услуга** | Услуга, выбранная локальным алгоритмом или `med-robot`. |
| **Целевая очередь** | Очередь, в которую нужно перевести визит после выбора услуги. |
| **Transfer-only** | Ветка, когда целевая услуга уже является текущей услугой визита, поэтому повторный `assign-service` не выполняется. |

## 2. Режимы запуска цикла назначения

### 2.1. Event-driven режим

Рекомендуемая цепочка событий Orchestra:

```text
USER_SERVICE_POINT_SESSION_START -> SET_WORK_PROFILE -> USER_SESSION_READY -> assignment cycle
```

Настройки:

```yaml
application:
  websocket:
    enabled: true
    subscribed-events:
      - USER_SERVICE_POINT_SESSION_START
      - SET_WORK_PROFILE
  assignment:
    user-service-point-session-start-trigger-enabled: true
    set-work-profile-trigger-enabled: false
    service-point-open-trigger-enabled: false
```

Режим нужен, чтобы распределение начиналось сразу после реальной посадки врача на рабочее место. `SERVICE_POINT_OPEN` не рекомендуется использовать как основной mutating-trigger: по логам Orchestra он может приходить до стабилизации рабочего профиля и до готовности серверного operator/service-point context.

### 2.2. Режим только по расписанию

В этом режиме websocket отключён, а служба периодически перечитывает состояние точек обслуживания и очередь «Врач не назначен».

```yaml
application:
  websocket:
    enabled: false
  assignment:
    polling-enabled: true
    polling-cron: "0 */10 * * * ?"
```

Особенности:

- задержка назначения равна интервалу cron;
- режим подходит для начального внедрения и для стендов с нестабильным websocket;
- branch-level lock защищает отделение от параллельной обработки несколькими потоками.

### 2.3. Смешанный режим: события + расписание

Рекомендуемый production-вариант после первичной отладки:

```yaml
application:
  websocket:
    enabled: true
  assignment:
    polling-enabled: true
    polling-cron: "0 */5 * * * ?"
```

События дают быстрый запуск, polling остаётся страховкой от потерянных событий, рестарта службы или временного разрыва SockJS/STOMP.

### 2.4. Режим расширения рабочего профиля

```yaml
application:
  assignment:
    work-profile-expanded-trigger-enabled: true
```

Если врач уже работает, а новый `SET_WORK_PROFILE` расширил набор доступных услуг, служба может повторно обработать очередь «Врач не назначен». Это полезно, когда профиль врача меняется без полного выхода и повторного входа.

### 2.5. Диагностические raw-trigger режимы

```yaml
application:
  assignment:
    service-point-open-trigger-enabled: false
    set-work-profile-trigger-enabled: false
```

В production эти флаги обычно выключены. Их включают только для диагностики конкретной инсталляции Orchestra, чтобы проверить, какое событие несёт корректный контекст врача.

## 3. Режимы выбора услуги и очереди

### 3.1. Локальный режим без med-robot

```yaml
application:
  med-robot:
    enabled: false
```

Алгоритм:

1. прочитать визиты из очереди «Врач не назначен»;
2. прочитать `unservedVisitServices`;
3. сопоставить непройденные услуги с услугами, доступными врачу;
4. выбрать услугу по порядку маршрута и приоритетам;
5. определить целевую очередь через кэш отделения.

Ограничение: если `unservedVisitServices` пустой или содержит только служебную услугу, локальный алгоритм не сможет выбрать медицинскую цель.

### 3.2. Med-robot с JSON-массивом услуг

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: UNSERVED_SERVICE_IDS_JSON_ARRAY
```

HTTP-контракт:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
Content-Type: application/json
Accept: application/json

[147, 148, 149]
```

Это режим совместимости со старой REST-точкой. Перед вызовом med-robot нужен локальный кандидат; если локальная pre-selection не нашла услугу, вызов робота пропускается.

### 3.3. Med-robot с номером талона как `text/plain`

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
```

HTTP-контракт:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}?policy=default
Content-Type: text/plain
Accept: application/json

Щ028
```

В этом режиме номер талона считается ключом для med-robot / МИС. Локальная pre-selection больше не является обязательной: если `unservedVisitServices` пустой, служба всё равно может вызвать med-robot по талону и получить фактическую целевую услугу.

`serviceId` в path выбирается так:

1. локальный кандидат, если он найден;
2. `currentVisitService.serviceId`, если локального кандидата нет;
3. если нет ни локального кандидата, ни текущей услуги, med-robot не вызывается, потому что endpoint требует service id в URL.

## 4. Обход очереди «Врач не назначен» и неполного маршрута визита

Под обходом очереди «Врач не назначен» здесь понимается не чтение произвольных очередей вместо настроенной стартовой очереди, а обход старого ограничения: целевая услуга обязательно должна была присутствовать в локальном списке `unservedVisitServices`.

Типовой проблемный визит:

```text
ticketId = Р004
currentVisitService.serviceId = 117 / "Врач не в системе"
unservedVisitServices = []
```

В старой схеме такой визит пропускался. В режиме `TICKET_NUMBER_PLAIN_TEXT` служба передаёт талон в med-robot, получает целевую услугу и продолжает workflow.

### 4.1. Отсутствующая услуга в `unservedVisitServices`

В текущей реализации отдельный `POST add-service` перед назначением услуги не выполняется. Если med-robot в plain text режиме вернул услугу, которой нет в `unservedVisitServices`, Doctor Assistant передает выбранный `serviceId` в обычный `assign-service`, а затем выполняет `transfer-visit`.

Правило:

| Ситуация | Действие текущего кода |
|---|---|
| Услуги нет в `unservedVisitServices` и она не является `currentVisitService` | `PUT assign-service` выбранной услуги, затем `transfer-visit`. |
| Услуга уже является `currentVisitService` | `assign-service` не нужен; выполняется `transfer-only`. |
| Услуга уже есть в `unservedVisitServices` | Обычная ветка `assign-service -> transfer-visit`. |

Если конкретная инсталляция Orchestra требует сначала добавить услугу в маршрут, это отдельная доработка `VisitWorkflowGateway`/`VisitAssignmentExecutor` и отдельный контрактный тест на `POST add-service`.

### 4.2. Граница режима

Текущий provider по-прежнему берёт визиты из `unknown-doctor-queue-id`. Если нужно читать несколько исходных очередей, это отдельная доработка: потребуется список очередей, приоритеты, защита от повторов и новый provider.

## 5. Режимы REST-мутаций Orchestra

| Режим | Когда применяется | REST-цепочка |
|---|---|---|
| `assign + transfer` | Целевая услуга отличается от текущей | `PUT assign-service` -> `PUT/POST transfer-visit` |
| `transfer-only` | Целевая услуга уже является текущей | только `transfer-visit` |
| `assign + transfer` для услуги вне локального маршрута | Робот в plain text режиме вернул услугу, отсутствующую в `unservedVisitServices` | `PUT assign-service` -> `transfer-visit` |
| `dry-run` | Проверка без изменений | мутации не отправляются |

Перед мутациями рекомендуется включать defensive recheck:

```yaml
application:
  assignment:
    recheck-visit-before-transfer: true
```

Если визит уже ушёл из очереди «Врач не назначен», служба пропускает его в текущем цикле.

## 6. Режимы обработки ошибок

### 6.1. Ошибка med-robot

```yaml
application:
  med-robot:
    error-handling-mode: FALLBACK_TO_LOCAL
```

| Значение | Поведение |
|---|---|
| `FALLBACK_TO_LOCAL` | Продолжить текущий визит по локальной схеме без робота. |
| `SKIP_VISIT` | Пропустить текущий визит в этом цикле и перейти к следующему. |

`fallback-to-local-on-error` оставлен только как совместимый алиас старой boolean-настройки.

### 6.2. Пустой ответ med-robot

```yaml
application:
  med-robot:
    fallback-to-local-on-empty-response: true
```

Если med-robot вернул `null/null`, `0/0` или неполную пару `serviceId`/`queueId`, служба возвращается к локальному алгоритму. Если локального кандидата нет, визит пропускается в текущем цикле.

### 6.3. Услуга med-robot отсутствует в локальном маршруте

В plain text режиме это штатная ситуация: локальный маршрут может быть пустым, а med-robot выбирает следующую услугу по номеру талона. Текущий код не делает отдельный `POST add-service`; он сразу вызывает `assign-service` выбранного `serviceId`.

Если `assign-service` на такой услуге падает из-за ограничений конкретной Orchestra, это не ошибка med-robot. Нужно либо доработать endpoint добавления услуги в маршрут, либо согласовать с Orchestra корректный контракт назначения услуги вне `unservedVisitServices`.

### 6.4. Ошибки контекста Orchestra

```yaml
application:
  assignment:
    abort-cycle-on-forbidden-mutation: true
    treat-inactive-user-state-as-failure: true
    treat-no-started-service-point-session-as-failure: true
```

Эти настройки не дают службе продолжать transfer после признаков неактивного operator/service-point context.

## 7. Защитные режимы

### 7.1. Dry-run

```yaml
application:
  assignment:
    dry-run: true
```

Служба читает данные, строит решение и пишет его в лог, но не отправляет mutating REST.

### 7.2. Ограничение отделений

```yaml
application:
  assignment:
    allowed-branches: [1]
```

Реальные мутации разрешены только для перечисленных branch.

### 7.3. Ограничение визитов на цикл

```yaml
application:
  assignment:
    max-visits-per-cycle: 3
    visit-processing-sort-order: OLDEST_FIRST
```

Защищает Orchestra от слишком длинной обработки одного цикла.

### 7.4. Дедупликация событий и визитов

```yaml
application:
  assignment:
    event-deduplication-ttl-seconds: 120
    processed-visit-ttl-seconds: 900
```

Используется для защиты от повторных событий и повторной обработки одного талона.

## 8. Рекомендуемые профили внедрения

### 8.1. Безопасный старт

```yaml
application:
  med-robot:
    enabled: false
  websocket:
    enabled: false
  assignment:
    polling-enabled: true
    dry-run: true
    polling-cron: "0 */10 * * * ?"
```

Проверяются кэш, чтение очереди, определение врача и выбор услуги без мутаций.

### 8.2. Production без med-robot

```yaml
application:
  med-robot:
    enabled: false
  websocket:
    enabled: true
  assignment:
    polling-enabled: true
    dry-run: false
```

Подходит, если маршрут визита в Orchestra содержит полный список непройденных услуг.

### 8.3. Production с med-robot по номеру талона

```yaml
application:
  med-robot:
    enabled: true
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
    error-handling-mode: FALLBACK_TO_LOCAL
    fallback-to-local-on-empty-response: true
  websocket:
    enabled: true
  assignment:
    polling-enabled: true
```

Подходит, если номер талона является надёжным ключом для med-robot / МИС, а локальный маршрут визита в Orchestra может быть неполным.

## 9. Контрольные строки в логах

| Что проверить | Пример |
|---|---|
| Запуск по расписанию | `Polling reconciliation for branch=... servicePoint=... staff=...` |
| Запуск по событию | `trigger=USER_SESSION_READY` или `trigger=WORK_PROFILE_EXPANDED` |
| Вызов med-robot plain text | `Content-Type: text/plain`, тело содержит номер талона |
| Transfer-only | `Skip redundant assign ... workflow can continue with transfer-only` |
| Визит уже ушёл из исходной очереди | `Skip visit ... because queue already changed from unknown-doctor queue` |
| Ошибка контекста Orchestra | `INACTIVE`, `NO_STARTED_SERVICE_POINT_SESSION`, `403` |
