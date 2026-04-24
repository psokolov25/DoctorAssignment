# Интеграция Doctor Assistant с med-robot

## Назначение

Doctor Assistant может работать в двух режимах выбора услуги и очереди для визита из очереди «врач не назначен»:

1. **Локальный режим** — старая схема: услуга выбирается по маршруту визита, доступным врачу услугам и локальным приоритетам.
2. **Режим med-robot** — Doctor Assistant сначала выполняет локальный предварительный выбор текущей услуги, затем отправляет в med-robot список непройденных услуг визита и получает оптимальную пару `serviceId`/`queueId`. После этого штатный executor назначает выбранную услугу визиту и переводит визит в выбранную очередь.

Интеграция сделана обратимо: при отключенном `application.med-robot.enabled` сервис ведет себя по прежней схеме.

## REST-контракт med-robot

По исходникам `med-robot-Revision2` используется следующий endpoint:

```http
POST /prorobot/optimalqueue/{branchId}/service/{serviceId}
Content-Type: application/json

[301, 302, 303]
```

Где:

- `branchId` — отделение Orchestra/Doctor Assistant;
- `serviceId` в path — текущая услуга, выбранная локальным алгоритмом как предварительный кандидат;
- тело запроса — JSON-массив идентификаторов непройденных услуг визита.

Ответ:

```json
{
  "serviceId": 302,
  "queueId": 902
}
```

Если med-robot вернул `null/null`, `0/0`, ошибку или недопустимую пару, поведение зависит от fallback-флагов.

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

    # Опционально, если REST API med-robot закрыт Basic Auth.
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

    # Проверять, что очередь из ответа med-robot есть в branch cache.
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

Основной запуск идет по событиям Orchestra, polling остается страховкой от потерянных событий.

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

Если проверка не пройдена, используется fallback-логика.

## Измененные/добавленные компоненты

- `MedRobotProperties` — настройки `application.med-robot`.
- `MedRobotRestClient` — Micronaut REST-клиент к endpoint-у med-robot.
- `MedRobotRestConfiguration` — опциональный Basic Auth filter.
- `MedRobotOptimalServiceGateway` / `MedRobotOptimalServiceGatewayImpl` — gateway-слой интеграции.
- `DoctorServiceSelectionService` — доменная абстракция выбора услуги.
- `MedRobotAwareDoctorServiceSelectionService` — wrapper над старым `DoctorServiceMatcher` с обращением к med-robot.
- `AssignmentProperties.pollingEnabled` — отдельный флаг включения/отключения расписания.
- `PollingReconciliationJob` — теперь проверяет `assignment.enabled` и `assignment.polling-enabled`.
- `RuntimeConfigurationLogger` — на старте пишет эффективные флаги med-robot и polling.
