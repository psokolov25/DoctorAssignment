# Краткая prod-документация для внедренца

## Цель

Этот документ — минимальный чек-лист для production-внедрения. Заполняются только параметры, которые в каждом контуре могут отличаться: адреса, креды, бизнес-маппинг (отделение → entry point), id очереди «Врач не назначен».

> Важно: значения `branchId`, `queueId`, `entryPointId` — это просто целые идентификаторы конкретной инсталляции Orchestra. Они не фиксированы и могут полностью отличаться между тестом и продом.

## 1) Что запросить у заказчика

- URL Orchestra (пример: `https://orchestra.company.local`)
- логин/пароль сервисной учетной записи
- URL med-robot (если используется)
- список `branchId`, которые входят в prod-скоуп
- `unknownDoctorQueueId` (очередь «Врач не назначен») для каждого нужного отделения
- `sourceEntryPointId` для каждого нужного отделения
- список отделений, где разрешен автопроцессинг (`allowed-branches`)

## 2) Фрагменты `application.yml` для prod

```yaml
application:
  orchestra:
    url: https://orchestra.company.local
    username: service_user
    password: strong_password
    branches-for-cache: "11,23"
```

```yaml
application:
  med-robot:
    enabled: true
    url: https://med-robot.company.local
```

```yaml
application:
  assignment:
    enabled: true
    dry-run: false
    unknown-doctor-queue-id: 502
    allowed-branches: [11, 23]
    source-entry-point-id-by-branch:
      11: 4
      23: 9
```

## 3) Критичные проверки перед включением

1. `unknown-doctor-queue-id` — это именно **queue id**, не service id.
2. `source-entry-point-id-by-branch.<branchId>` соответствует ожиданию Orchestra в поле `fromId` при transfer.
3. Если включен `med-robot`, его `queueId/serviceId` существуют в кэше отделения.
4. На старте сервиса кэш отделений успешно прогревается.

## 4) Отдельный фрагмент маппинга branch → entry point

```yaml
application:
  orchestra:
    branches-for-cache: "11,23"
  assignment:
    allowed-branches: [11, 23]
    source-entry-point-id-by-branch:
      11: 4
      23: 9
```

## 5) Что чаще всего отличается между тестом и prod

- реальные URL (`application.orchestra.url`, `application.med-robot.url`);
- учетные данные;
- `unknown-doctor-queue-id`;
- `allowed-branches`;
- `source-entry-point-id-by-branch` (branch → entry point).


## 6) Запуск

### 6.1 Как отдельное jar-приложение

```bash
java -jar med-doctor-assignment-service.jar
```

Если `application.yml` лежит вне jar, можно явно указать путь:

```bash
java -jar med-doctor-assignment-service.jar \
  -Dmicronaut.config.files=/opt/med-doctor-assignment/config/application.yml
```

### 6.2 Как Linux-службу (`systemd`)

Пример unit-файла `/etc/systemd/system/med-doctor-assignment.service`:

```ini
[Unit]
Description=Med Doctor Assignment Service
After=network.target

[Service]
Type=simple
User=medsvc
WorkingDirectory=/opt/med-doctor-assignment
ExecStart=/usr/bin/java -jar /opt/med-doctor-assignment/med-doctor-assignment-service.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Команды управления:

```bash
sudo systemctl daemon-reload
sudo systemctl enable med-doctor-assignment
sudo systemctl start med-doctor-assignment
sudo systemctl status med-doctor-assignment
```


### 6.3 Варианты без `systemctl`

#### Вариант A: `service` (SysV init)

Пример скрипта `/etc/init.d/med-doctor-assignment`:

```bash
#!/bin/sh
### BEGIN INIT INFO
# Provides:          med-doctor-assignment
# Required-Start:    $network
# Required-Stop:     $network
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
### END INIT INFO

APP_DIR=/opt/med-doctor-assignment
JAR=$APP_DIR/med-doctor-assignment-service.jar
PIDFILE=/var/run/med-doctor-assignment.pid
LOGFILE=$APP_DIR/service.log

start() {
  nohup java -jar "$JAR" >> "$LOGFILE" 2>&1 &
  echo $! > "$PIDFILE"
}

stop() {
  [ -f "$PIDFILE" ] && kill "$(cat "$PIDFILE")" && rm -f "$PIDFILE"
}

case "$1" in
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  *) echo "Usage: $0 {start|stop|restart}"; exit 1 ;;
esac
```

Команды управления:

```bash
sudo chmod +x /etc/init.d/med-doctor-assignment
sudo service med-doctor-assignment start
sudo service med-doctor-assignment stop
sudo service med-doctor-assignment restart
```

#### Вариант B: `supervisord`

Пример `/etc/supervisor/conf.d/med-doctor-assignment.conf`:

```ini
[program:med-doctor-assignment]
command=/usr/bin/java -jar /opt/med-doctor-assignment/med-doctor-assignment-service.jar
directory=/opt/med-doctor-assignment
autostart=true
autorestart=true
stderr_logfile=/opt/med-doctor-assignment/log/stderr.log
stdout_logfile=/opt/med-doctor-assignment/log/stdout.log
user=medsvc
```

Команды управления:

```bash
sudo supervisorctl reread
sudo supervisorctl update
sudo supervisorctl start med-doctor-assignment
sudo supervisorctl status med-doctor-assignment
```

#### Вариант C: запуск в фоне через `nohup` (без менеджера служб)

```bash
cd /opt/med-doctor-assignment
nohup java -jar med-doctor-assignment-service.jar > run.log 2>&1 &
echo $! > med-doctor-assignment.pid
```

Остановка:

```bash
kill "$(cat /opt/med-doctor-assignment/med-doctor-assignment.pid)"
```

## 7) Быстрый go-live чек-лист

- [ ] Указаны prod URL и креды.
- [ ] Подтвержден id очереди «Врач не назначен».
- [ ] Подтвержден branch → entry point mapping для всех prod-отделений.
- [ ] В `allowed-branches` только согласованные отделения.
- [ ] После старта есть успешный cache refresh.
