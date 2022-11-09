import dataclasses
import pandas as pd

from typing import Any


@dataclasses.dataclass
class Metric:
    node: str
    timestamp: pd.Timestamp


@dataclasses.dataclass
class Boot(Metric):
    pass


@dataclasses.dataclass
class PubSubMessageSent(Metric):
    topic: str
    message_id: str
    delivered: bool


@dataclasses.dataclass
class PubSubMessageReceived(Metric):
    source: str
    topic: str
    hop_count: int
    message_id: str
    delivered: bool


@dataclasses.dataclass
class PubSubSubscribe(Metric):
    topic: str


@dataclasses.dataclass
class PubSubUnsubscribe(Metric):
    topic: str


@dataclasses.dataclass
class Span(Metric):
    name: str
    duration: float


@dataclasses.dataclass
class Network(Metric):
    inbound: int
    outbound: int


@dataclasses.dataclass
class MessageSent(Metric):
    # Message Type (e.g. "BroadcastHave", "FindNodeRequest", ...)
    message_type: str
    # Node Identifier of the destination
    destination: str
    message: Any


@dataclasses.dataclass
class MessageReceived(Metric):
    # Message Type (e.g. "BroadcastHave", "FindNodeRequest", ...)
    message_type: str
    # Node Identifier of the source
    source: str
    message: Any


@dataclasses.dataclass
class RoutingTableSnapshot(Metric):
    topic: str
    buckets: list[list[str]]

    def __repr__(self):
        repr = f"RoutingTableSnapshot({self.node}, {self.timestamp}, {self.topic})"
        for bucket in self.buckets:
            repr += f"\n\t{bucket}"
        return repr
