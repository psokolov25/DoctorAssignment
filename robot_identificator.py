import json
from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict


class IdentificatorMode(str, Enum):
    TICKET_ID = "ticket_id"
    VISIT_JSON = "visit_json"


@dataclass(frozen=True)
class RobotPayload:
    identificator: str


def build_robot_payload(value: str, mode: IdentificatorMode) -> RobotPayload:
    """Build robot payload with configurable identificator format.

    Args:
        value: Ticket number or visit JSON string.
        mode: How `value` should be interpreted.
    """
    if mode == IdentificatorMode.TICKET_ID:
        if not value.strip():
            raise ValueError("Ticket ID cannot be empty")
        return RobotPayload(identificator=value.strip())

    if mode == IdentificatorMode.VISIT_JSON:
        visit_obj: Dict[str, Any] = json.loads(value)
        return RobotPayload(
            identificator=json.dumps(visit_obj, ensure_ascii=False, separators=(",", ":"))
        )

    raise ValueError(f"Unsupported mode: {mode}")


if __name__ == "__main__":
    ticket_payload = build_robot_payload("Д048", IdentificatorMode.TICKET_ID)
    print(ticket_payload)

    visit_json = '{"id":3401,"checksum":"730554213","ticketId":"Д048"}'
    visit_payload = build_robot_payload(visit_json, IdentificatorMode.VISIT_JSON)
    print(visit_payload)
