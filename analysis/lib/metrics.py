from __future__ import annotations

from dataclasses import dataclass
import json
import os

# Internal metrics helpers

__registered_metrics__ = {}


def __metric__(name):
    def wrapper(wrapped):
        __registered_metrics__[name] = wrapped
        return wrapped

    return wrapper


@dataclass
class NodeMetrics:
    # Node identifier
    id: str
    metrics: list[Metric]


@dataclass
class Metric:
    # Timestamp in milliseconds
    timestamp: int


@dataclass
@__metric__("PubSubMessageSent")
class PubSubMessageSent(Metric):
    source: str
    topic: str
    message_id: str
    delivered: bool


@dataclass
@__metric__("PubSubMessageReceived")
class PubSubMessageReceived(Metric):
    source: str
    topic: str
    hop_count: int
    message_id: str
    delivered: bool


@dataclass
@__metric__("PubSubSubscription")
class PubSubSubscription(Metric):
    topic: str


@dataclass
@__metric__("PubSubUnsubscription")
class PubSubUnsubscription(Metric):
    topic: str


@dataclass
@__metric__("Span")
class Span(Metric):
    name: str
    # Duration of the span in seconds
    duration: float


@dataclass
@__metric__("Network")
class Network(Metric):
    inbound: int
    outbound: int


@dataclass
@__metric__("RoutingTable")
class RoutingTable(Metric):
    topic: str
    buckets: list[list[str]]


def from_dict(d) -> Metric:
    metric_class = __registered_metrics__[d["metric_type"]]
    if metric_class is None:
        raise ValueError(f"Unknown metric type {d['metric_type']}")
    d["metric"]["timestamp"] = d["timestamp"]
    return metric_class(**d["metric"])


def decode_metric(line: str) -> Metric:
    raw = json.loads(line)
    metric_class = __registered_metrics__[raw["type"]]
    if metric_class is None:
        raise ValueError(f"Unknown metric type {raw['type']}")
    raw["message"]["timestamp"] = raw["timestamp"]
    metric = metric_class(**raw["message"])
    return metric


def load_metrics_file(file: str) -> list[Metric]:
    with open(file) as f:
        return list(map(decode_metric, f.readlines()))


def load_metrics_dir(dir: str) -> list[Metric]:
    metrics = []
    for filename in os.listdir(dir):
        metrics.extend(load_metrics_file(os.path.join(dir, filename)))
    return metrics


def load_node_metrics_directory(dir: str) -> list[NodeMetrics]:
    """
    Loads the metrics from a directory containing the metrics of each node in the experiment.
    Each file in the directory must be named as the node identifier and contain the metrics.
    Example directry structure:
    ```
    metrics/
        node1.json
        node2.json
    ```

    :param dir: The directory containing the metrics of each node
    """
    metrics = []
    for filename in os.listdir(dir):
        raw_metrics = load_metrics_file(os.path.join(dir, filename))
        node_id = filename.strip(".json")
        metrics.append(NodeMetrics(node_id, raw_metrics))
    return metrics


if __name__ == "__main__":
    metrics = load_node_metrics_directory("metrics_kadpubsub")
    print(metrics)
