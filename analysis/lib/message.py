import dataclasses
from typing import Any


@dataclasses.dataclass
class BroadcastHave:
    topic: str
    message_id: str

    @staticmethod
    def from_properties(properties: dict) -> "BroadcastHave":
        return BroadcastHave(
            topic=properties["topic"],
            message_id=properties["message_id"],
        )


@dataclasses.dataclass
class BroadcastMessage:
    topic: str
    message_id: str
    payload: int
    hop_count: int

    @staticmethod
    def from_properties(properties: dict) -> "BroadcastMessage":
        return BroadcastMessage(
            topic=properties["topic"],
            message_id=properties["message_id"],
            payload=properties["payload"],
            hop_count=properties["hop_count"],
        )


@dataclasses.dataclass
class BroadcastWant:
    topic: str
    message_id: str

    @staticmethod
    def from_properties(properties: dict) -> "BroadcastWant":
        return BroadcastWant(
            topic=properties["topic"],
            message_id=properties["message_id"],
        )


def from_dict(message_type: str, d: dict, ignore_unkown=True) -> Any:
    match message_type:
        case "BroadcastHave":
            return BroadcastHave.from_properties(d)
        case "BroadcastMessage":
            return BroadcastMessage.from_properties(d)
        case "BroadcastWant":
            return BroadcastWant.from_properties(d)
        case _:
            if ignore_unkown:
                return d
            else:
                raise ValueError(f"Unknown message type: {message_type}")
