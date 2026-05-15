# DoctorAssignment

Добавлена настраиваемая опция передачи `identificator` в робота:

- `ticket_id` — передаётся номер талона визита (например, `Д048`).
- `visit_json` — передаётся JSON тела визита строкой (как в вашем примере).

## Пример

```python
from robot_identificator import build_robot_payload, IdentificatorMode

payload_by_ticket = build_robot_payload("Д048", IdentificatorMode.TICKET_ID)

payload_by_visit = build_robot_payload(
    '{"id":3401,"checksum":"730554213","ticketId":"Д048"}',
    IdentificatorMode.VISIT_JSON,
)
```

Оба варианта возвращают объект с полем `identificator`, готовым для отправки в робота.
