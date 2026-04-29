# DEPLOYMENT

Документ описывает сборку, настройку и запуск **Med Doctor Assignment Service** в стендовом и production-контуре.

## Требования

| Компонент | Требование |
|---|---|
| Java Runtime | Java 8 |
| Сборка | Maven Wrapper из проекта или локальный Maven |
| Сеть | доступ к Orchestra REST, med-robot REST, при необходимости SockJS/STOMP |
| Время | синхронизированное время на сервере службы и Orchestra желательно для анализа логов |

## Сборка

Linux:

```bash
./mvnw clean package
```

Windows:

```bat
mvnw.cmd clean package
```

Артефакт:

```text
target/med-doctor-assignment-service-1.0.0-SNAPSHOT.jar
```

## Рекомендуемая структура каталога на сервере

```text
/opt/med-doctor-assignment/
├── app/
│   └── med-doctor-assignment-service-1.0.0-SNAPSHOT.jar
├── config/
│   └── application.yml
├── log/
└── backup/
```

## Внешний конфиг

Запускайте jar с внешним `application.yml`:

```bash
java \
  -Dmicronaut.config.files=/opt/med-doctor-assignment/config/application.yml \
  -jar /opt/med-doctor-assignment/app/med-doctor-assignment-service-1.0.0-SNAPSHOT.jar
```

Пароли лучше передавать через переменные окружения или секреты. В Micronaut-конфиге можно использовать placeholders окружения, если они поддержаны runtime-конфигурацией:

```yaml
application:
  orchestra:
    username: ${ORCHESTRA_USER}
    password: ${ORCHESTRA_PASSWORD}
```



## Усиленная настройка тестового стенда

Первый запуск на тестовом стенде должен доказывать корректность конфигурации, а не сразу менять визиты. Поэтому начальный профиль обязан быть ограниченным:

```yaml
application:
  websocket:
    enabled: false
  med-robot:
    enabled: false
  assignment:
    dry-run: true
    polling-enabled: true
    max-visits-per-cycle: 3
    allowed-branches: [1]
```

Порядок стендовой проверки:

1. Подтвердить `branchId`, `unknown-doctor-queue-id`, `source entry point id`.
2. Запустить сервис с внешним `application-test.yml`.
3. Проверить refresh branch cache по логам.
4. Проверить чтение очереди «Врач не назначен».
5. Проверить dry-run решение без реальных REST PUT/POST мутаций.
6. Включить websocket и проверить связку `USER_SERVICE_POINT_SESSION_START -> SET_WORK_PROFILE`.
7. Включить med-robot, если он нужен, и проверить формат тела запроса.
8. Включить `dry-run=false` только для одного отделения и одного визита.
9. Сохранить логи успешного сценария.

Контрольные строки:

```text
Start cache refresh for branch
Finish cache refresh for branch
Start assignment cycle
Visits in unknown-doctor queue
Dry-run enabled
Finish assignment cycle
```

Подробный документ по стенду: `docs/TEST_STAND_SETUP.md`.

## Первый запуск на стенде

Рекомендуемый порядок:

1. `dry-run: true`.
2. `websocket.enabled: false`.
3. `polling-enabled: true`.
4. `med-robot.enabled: true`, если тестируется режим робота.
5. Проверить runtime-аудит.
6. Проверить, что служба читает кэш и визиты.
7. Проверить HTTP-заголовки med-robot.
8. Проверить med-robot, assign или transfer-only, затем transfer на одном тестовом визите.
9. Переключить `dry-run: false`.

## Production-конфигурация: пример

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
    common-rest-path: /rest
    configuration-rest-path: /qsystem/rest/config
    branches-for-cache: "1"
    replay-mutation-cookies: false

  med-robot:
    enabled: true
    url: http://med-robot-host:8082
    optimal-service-path: /prorobot/optimalqueue/{branchId}/service/{serviceId}
    request-body-mode: TICKET_NUMBER_PLAIN_TEXT
    plain-text-policy: default
    error-handling-mode: FALLBACK_TO_LOCAL
    fallback-to-local-on-empty-response: true
    require-doctor-available-service: false
    require-known-queue: true

  websocket:
    enabled: false
    send-cookies-in-handshake: false

  assignment:
    enabled: true
    unknown-doctor-queue-id: 312
    max-visits-per-cycle: 50
    polling-enabled: true
    polling-cron: "0 */1 * * * ?"
    dry-run: false
    allowed-branches: [ 1 ]
    user-service-point-session-start-trigger-enabled: true
    work-profile-expanded-trigger-enabled: true
    service-point-open-trigger-enabled: false
    set-work-profile-trigger-enabled: false
    recheck-visit-before-transfer: true
    abort-cycle-on-forbidden-mutation: true
    treat-inactive-user-state-as-failure: true
    treat-no-started-service-point-session-as-failure: true
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

## systemd пример

```ini
[Unit]
Description=Med Doctor Assignment Service
After=network.target

[Service]
Type=simple
User=meddoctor
WorkingDirectory=/opt/med-doctor-assignment
Environment=ORCHESTRA_USER=superadmin
Environment=ORCHESTRA_PASSWORD=change-me
ExecStart=/usr/bin/java -Dmicronaut.config.files=/opt/med-doctor-assignment/config/application.yml -jar /opt/med-doctor-assignment/app/med-doctor-assignment-service-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

Команды:

```bash
sudo systemctl daemon-reload
sudo systemctl enable med-doctor-assignment
sudo systemctl start med-doctor-assignment
sudo systemctl status med-doctor-assignment
journalctl -u med-doctor-assignment -f
```

## Rollback

Перед обновлением сохраните:

```text
app/old.jar
config/application.yml
log/*
```

Rollback:

1. Остановить службу.
2. Вернуть старый jar.
3. Вернуть старый `application.yml`, если менялись параметры.
4. Запустить службу.
5. Проверить runtime-аудит и `gatewayCodeSource`.

## Smoke test после выкладки

1. Найти runtime marker.
2. Проверить `resolvedSourceEntryPointIds`.
3. Проверить, что кэш branch обновился без ошибок.
4. Убедиться, что polling cycle стартует.
5. На одном тестовом визите проверить:
   - чтение визита из unknown queue;
   - вызов med-robot;
   - отсутствие 415;
   - корректный `currentService=117` для plain text режима;
   - assign или transfer-only;
   - transfer в ожидаемую очередь.
6. Проверить, что повторная обработка блокирует только тот же `currentVisitService.id`, а новый маршрутный шаг того же `visitId` допускает новый вызов med-robot.

## Безопасность

- Не храните реальные пароли в репозитории.
- Ограничьте сервисной учетной записи Orchestra доступ только необходимыми REST-правами.
- Ограничьте сетевой доступ к med-robot и Orchestra по IP/сетевым правилам.
- В production не включайте чрезмерный HTTP TRACE надолго: он может содержать чувствительные заголовки и тела запросов.
- Перед передачей логов на анализ маскируйте Authorization и персональные данные.
