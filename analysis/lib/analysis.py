from __future__ import annotations
from collections import defaultdict
import graphviz

import pandas as pd

from typing import Callable, Dict, Set, Tuple, Union

from lib.kademlia import KadID

from .experiment import PubSubExperiment, PubSubExperimentResults
from .message import (
    BroadcastHave,
    BroadcastMessage,
    BroadcastWant,
    from_dict as message_from_dict,
)
from .metrics import *


class TimeStampedMessageID:
    def __init__(self, timestamp: int, message_id: str):
        self.timestamp = timestamp
        self.message_id = message_id

    def __hash__(self):
        return hash((self.timestamp, self.message_id))

    def __eq__(self, other):
        return self.timestamp == other.timestamp and self.message_id == other.message_id

    def __repr__(self):
        return f"{self.timestamp}-{self.message_id}"


class PubSubAnalyzer:
    def __init__(self, experiment: PubSubExperiment, metrics: list[Metric]):
        self._experiment = experiment
        self._metrics = metrics
        self._nodes = frozenset([m.node for m in self._metrics])
        self._metrics_per_type = _compute_metrics_per_type(self._metrics)
        self._message_id_to_topic = _compute_message_id_to_topic(self._metrics)
        self._topic_to_subscribers = self._compute_topic_to_subscribers()
        self._messages_reliability = self._compute_messages_reliability()

    @property
    def experiment(self) -> PubSubExperiment:
        return self._experiment

    def metrics(
        self, ty: Union[type, tuple] = None, filter: Callable = None
    ) -> list[Metric]:
        type_filter = None
        if ty is None:
            type_filter = lambda t: True
        elif isinstance(ty, type):
            type_filter = lambda t: t == ty
        elif isinstance(ty, tuple):
            type_list = list(ty)
            type_filter = lambda t: t in type_list
        elif ty is not None:
            raise ValueError("Invalid ty parameter")

        accessed_metric_types = 0
        filtered_metrics = []
        for t, metrics in self._metrics_per_type.items():
            if not type_filter(t):
                continue
            accessed_metric_types += 1
            if filter is not None:
                filtered_metrics.extend([m for m in metrics if filter(m)])
            else:
                filtered_metrics.extend(metrics)

        if accessed_metric_types > 1:
            filtered_metrics.sort(key=lambda m: m.timestamp)

        return filtered_metrics

    def metrics_with(self, *args) -> list[Metric]:
        m = []
        for metric in self._metrics:
            for filter in args:
                if filter(metric):
                    m.append(metric)
        return m

    def topics(self) -> Set[str]:
        """
        Compute the set of all known topics.
        """
        return set([m.topic for m in self.metrics(ty=PubSubSubscribe)])

    def nodes(self) -> frozenset[str]:
        """
        Compute the set of all known nodes.
        """
        return self._nodes

    def messages(self) -> Set[str]:
        """
        Compute the set of all known messages.
        """
        return set([m.message_id for m in self.messages_timestamped()])

    def messages_timestamped(self) -> list[TimeStampedMessageID]:
        """
        Compute the set of all known messages with their timestamps.
        """
        bcasts = self.metrics_with(lambda m: isinstance(m, PubSubMessageSent))
        message_ids = {}
        timestamped = []
        for m in bcasts:
            if m.message_id not in message_ids:
                message_ids[m.message_id] = m.timestamp
                timestamped.append(TimeStampedMessageID(m.timestamp, m.message_id))
            else:
                assert message_ids[m.message_id] <= m.timestamp
        return timestamped

    def hops(self) -> pd.Series:
        return pd.Series(
            map(lambda m: m.hop_count, self.metrics(ty=PubSubMessageReceived))
        )

    def topic_subscribers(self, topic: str) -> Set[str]:
        """
        Compute the set of nodes that are subscribed to a given topic.
        This will run over the subscriptions/unsubscriptions of a particular node so it gives the state after the last subscription/unsubscription.
        """
        return self._topic_to_subscribers[topic].copy()

    def topic_of_message(self, message: str) -> str:
        """
        Get the topic of a given message.
        """
        return self._message_id_to_topic[message]

    def timestamp_of_message(self, message: str) -> pd.Timedelta:
        """
        Get the timestamp of a given message.
        """
        return self.metrics(
            ty=PubSubMessageSent, filter=lambda m: m.message_id == message
        )[0].timestamp

    def messages_reliability(self) -> Dict[str, float]:
        """
        Get the reliability of each message.
        """
        return self._messages_reliability.copy()

    def message_reliability(self, message: str) -> float:
        """
        Get the reliability of a given message.
        """
        # If the message is not present in the map that means either it
        # doesnt exist or there was no one subscribed to its topic, in that case we return 1.
        return self._messages_reliability.get(message, 1)

    def message_graph(self, message: str) -> graphviz.Digraph:
        """
        Get the graph of the message propagation.
        """
        topic = self.topic_of_message(message)
        subscribers = self.topic_subscribers(topic)
        receives = self.metrics_with(
            lambda m: isinstance(m, PubSubMessageReceived) and m.message_id == message
        )

        colors = ["red", "green", "blue", "orange", "purple", "yellow", "pink"]

        dot = graphviz.Digraph(name="Message path")
        for sub in subscribers:
            dot.node(sub, label=sub)

        for receive in receives:
            dot.edge(
                receive.source,
                receive.node,
                label=str(receive.hop_count),
                color=colors[receive.hop_count % len(colors)],
            )

        return dot

    def boottimes(self) -> pd.Series:
        node_boottimes = {}
        for metric in self.metrics(ty=Boot):
            node_boottimes[metric.node] = metric.timestamp
        return pd.Series(
            [1 for _ in range(len(node_boottimes))], index=list(node_boottimes.values())
        )

    def message_publish_latency(self, message: str) -> pd.DataFrame:
        receives = self.metrics(
            ty=PubSubMessageReceived,
            filter=lambda m: m.message_id == message and m.delivered,
        )
        hops = pd.Series(map(lambda m: m.hop_count, receives))
        return pd.DataFrame({"latency": hops})

    def publish_latency(self) -> pd.DataFrame:
        receives = self.metrics(ty=PubSubMessageReceived, filter=lambda m: m.delivered)
        hops = pd.Series(map(lambda m: m.hop_count, receives))
        return pd.DataFrame({"latency": hops})

    def network_usage(self) -> int:
        """
        Compute the total network usage in bytes.
        """
        usages = self._last_network_metrics_per_node()
        return sum(map(lambda m: m.outbound, usages.values()))

    def network_usage_usefullness_fraction(self) -> float:
        total_network_usage = self.network_usage()
        delivered_messages = len(
            self.metrics(ty=PubSubMessageReceived, filter=lambda m: m.delivered)
        )
        total_payload_size = delivered_messages * self.experiment.payload_size
        if total_network_usage == 0:
            return 0
        return total_payload_size / total_network_usage

    def reliability(self) -> pd.DataFrame:
        """
        Compute the reliability of every message sent during the experiment.
        The reliability is the fraction of nodes that received the message.
        Message of topics that had no subscribers are not included.
        """
        timestamped = list(
            filter(
                lambda m: len(
                    self.topic_subscribers(self.topic_of_message(m.message_id))
                )
                > 0,
                self.messages_timestamped(),
            )
        )
        timestamps = [m.timestamp for m in timestamped]
        # Only consider messages of topics that had subscribers
        reliabilities = [self.message_reliability(m.message_id) for m in timestamped]
        assert len(timestamps) == len(reliabilities)
        return pd.DataFrame(
            pd.Series(reliabilities, index=timestamps, name="reliability")
        )

    def redundancy(self) -> float:
        """
        Compute the redundancy of the experiment.
        The redundancy is the fraction of messages that were received more than once.
        """
        received_messages = self.metrics(ty=PubSubMessageReceived)
        delivered = len(list(filter(lambda m: m.delivered, received_messages)))
        total = len(received_messages)
        if total == 0:
            return 0
        return (total - delivered) / total

    def _last_network_metrics_per_node(self) -> Dict[str, Network]:
        network_metrics = self.metrics(ty=Network)
        last_network_metrics_per_node = {}
        for metric in network_metrics:
            last_network_metrics_per_node[metric.node] = metric
        return last_network_metrics_per_node

    def _compute_messages_reliability(self) -> Dict[str, float]:
        """
        Compute the reliability of each message.
        """
        messages_reliability = defaultdict(lambda: 0)
        expected_per_topic = {
            topic: len(self.topic_subscribers(topic)) for topic in self.topics()
        }
        for m in self.metrics(ty=PubSubMessageReceived):
            if m.delivered:
                messages_reliability[m.message_id] += 1.0
        for m in messages_reliability.keys():
            message_topic = self.topic_of_message(m)
            if message_topic not in expected_per_topic:
                del messages_reliability[m]
            else:
                messages_reliability[m] /= expected_per_topic[message_topic]
        return dict(messages_reliability)

    def _compute_topic_to_subscribers(self) -> Dict[str, Set[str]]:
        topic_to_subs = defaultdict(lambda: set())
        subscriptions = self.metrics_with(
            lambda m: isinstance(m, (PubSubSubscribe, PubSubUnsubscribe))
        )
        for m in subscriptions:
            if isinstance(m, PubSubSubscribe):
                topic_to_subs[m.topic].add(m.node)
            elif isinstance(m, PubSubSubscribe):
                topic_to_subs[m.topic].remove(m.node)
        return topic_to_subs

    @staticmethod
    def from_experiment_results(
        results: PubSubExperimentResults, ignore_unknown_metrics=True
    ) -> PubSubAnalyzer:
        """
        Create an analyzer from the results of an experiment
        """
        experiment, metrics = _parse_experiment_results(results, ignore_unknown_metrics)
        return PubSubAnalyzer(experiment, metrics)

    @staticmethod
    def from_experiment_results_file(path: str) -> PubSubAnalyzer:
        """
        Create an analyzer from the results of an experiment
        """
        results = PubSubExperimentResults.load_from_file(path)
        return PubSubAnalyzer.from_experiment_results(results)


class KadPubSubAnalyzer(PubSubAnalyzer):
    def __init__(self, experiment: PubSubExperiment, metrics: list[Metric]):
        super().__init__(experiment, metrics)
        self._kadids = self._compute_kadids()

    def kadid(self, node: str) -> KadID:
        return self._kadids[node]

    def routing_table_snapshot(
        self, node: Union[str, list[str], set[str]], topic: str, timestamp: pd.Timedelta
    ) -> Union[RoutingTableSnapshot, dict[str, RoutingTableSnapshot]]:
        """
        Obtain the routing table of a given node at a given timestamp.
        This will get the latest routing table snapshot before the given timestamp.

        :param node: The node to get the routing table of. This can be a single node or a list of nodes.
        :param topic: The topic to get the routing table of.
        :param timestamp: The timestamp to get the routing table at.
        :return: The routing table snapshot or a dictionary of routing table snapshots if multiple nodes were given.
        """
        if isinstance(node, str):
            return self.metrics(
                ty=RoutingTableSnapshot,
                filter=lambda m: m.topic == topic
                and m.node == node
                and m.timestamp <= timestamp,
            )[-1]
        else:
            snapshots = self.metrics(
                ty=RoutingTableSnapshot,
                filter=lambda m: m.topic == topic and m.timestamp <= timestamp,
            )
            node_snapshots = {}
            for snapshot in snapshots:
                if (
                    snapshot.node not in node_snapshots
                    or snapshot.timestamp > node_snapshots[snapshot.node].timestamp
                ):
                    node_snapshots[snapshot.node] = snapshot
            return {n: node_snapshots[n] for n in node}

    def message_graph(self, message: str) -> graphviz.Digraph:
        # Check if this type of metrics were captured, if not default to the PubSubAnalyzer method
        if not any(map(lambda m: isinstance(m, MessageSent), self._metrics)):
            return super().message_graph(message)

        topic = self.topic_of_message(message)
        subscribers = self.topic_subscribers(topic)
        broadcasts = self.metrics_with(
            lambda m: isinstance(m, MessageSent)
            and isinstance(m.message, BroadcastMessage)
            and m.message.topic == topic
            and m.message.message_id == message
        )
        haves = self.metrics_with(
            lambda m: isinstance(m, MessageSent)
            and isinstance(m.message, BroadcastHave)
            and m.message.topic == topic
            and m.message.message_id == message
        )
        wanthaves = self.metrics_with(
            lambda m: isinstance(m, MessageSent)
            and isinstance(m.message, BroadcastWant)
            and m.message.topic == topic
            and m.message.message_id == message
        )
        delivers = set(
            map(
                lambda m: m.node,
                self.metrics(
                    ty=PubSubMessageReceived,
                    filter=lambda m: m.message_id == message and m.delivered,
                ),
            )
        )

        dot = graphviz.Digraph("Message Broadcast Tree")
        dot.node(broadcasts[0].node)
        for sub in subscribers:
            node_color = "green" if sub in delivers else "red"
            dot.node(sub, color=node_color)
        for bcast in broadcasts:
            dot.edge(
                bcast.node,
                bcast.destination,
                label=str(bcast.message.ceil),
                color="green",
            )
        for have in haves:
            dot.edge(have.node, have.destination, color="blue")
        for wanthave in wanthaves:
            dot.edge(wanthave.node, wanthave.destination, color="red")

        return dot

    def topic_connectivity_graph_at(
        self, topic: str, timestamp: pd.Timestamp
    ) -> graphviz.Digraph:
        subscribers = self.topic_subscribers(topic)
        snapshots = {
            n: self.routing_table_snapshot(n, topic, timestamp) for n in subscribers
        }

        dot = graphviz.Digraph("Topic Connectivity Graph")
        for node in subscribers:
            dot.node(node)

        for node, snapshot in snapshots.items():
            for bucket_index, bucket in enumerate(snapshot.buckets):
                for peer in bucket:
                    dot.edge(node, peer, label=bucket_index)

        return dot

    def _compute_kadids(self) -> Dict[str, KadID]:
        kadids = {}
        for metric in self.metrics(ty=KadIdentifier):
            kadids[metric.node] = KadID.from_hex(metric.identifier)
        return kadids

    @staticmethod
    def from_experiment_results(
        results: PubSubExperimentResults, ignore_unknown_metrics=True
    ) -> KadPubSubAnalyzer:
        """
        Create an analyzer from the results of an experiment
        """
        experiment, metrics = _parse_experiment_results(results, ignore_unknown_metrics)
        return KadPubSubAnalyzer(experiment, metrics)

    @staticmethod
    def from_experiment_results_file(path: str) -> KadPubSubAnalyzer:
        """
        Create an analyzer from the results of an experiment
        """
        results = PubSubExperimentResults.load_from_file(path)
        return KadPubSubAnalyzer.from_experiment_results(results)


def _parse_experiment_results(
    results: PubSubExperimentResults, ignore_unknown_metrics: bool
) -> Tuple[PubSubExperiment, list[Metric]]:
    experiment = results.experiment
    metrics = []
    for node_id, node_metrics in results.results.items():
        metrics.extend(
            _parse_node_metrics(node_id, node_metrics, ignore_unknown_metrics)
        )
    metrics.sort(key=lambda m: m.timestamp)
    _fix_timestamps(metrics)
    return experiment, metrics


def _fix_timestamps(m: list[Metric]):
    lowest_timestamp = min([m.timestamp for m in m])
    for metric in m:
        metric.timestamp -= lowest_timestamp
        metric.timestamp = pd.Timestamp(metric.timestamp / 1_000_000_000, unit="s")


def _parse_node_metrics(
    node_id: str, metrics: list[dict], ignore_unknown_metrics: bool
) -> list[Metric]:
    parsers = {
        "Boot": _parse_metric_boot,
        "Shutdown": _parse_metric_shutdown,
        "PubSubMessageSent": _parse_metric_pubsub_message_sent,
        "PubSubMessageReceived": _parse_metric_pubsub_message_received,
        "PubSubSubscription": _parse_metric_pubsub_subscribe,
        "PubSubUnsubscription": _parse_metric_pubsub_unsubscribe,
        "Span": _parse_metric_span,
        "Network": _parse_metric_network,
        "MessageSent": _parse_metric_message_sent,
        "MessageReceived": _parse_metric_message_received,
        "KademliaIdentifier": _parse_metric_kad_identifier,
        "RoutingTable": _parse_metric_routing_table,
    }

    parsed_metrics = []
    for metric in metrics:
        timestamp = metric["timestamp"]
        metric_type = metric["metric_type"]
        metric_data = metric["metric"]
        parser = parsers.get(metric_type)
        if parser is not None:
            parsed_metrics.append(parser(node_id, timestamp, metric_data))
        elif not ignore_unknown_metrics:
            raise Exception(f"Unknown metric type: {metric_type}")

    return parsed_metrics


def _parse_metric_boot(node_id: str, timestamp: int, metric: dict) -> Metric:
    return Boot(node_id, timestamp)


def _parse_metric_shutdown(node_id: str, timestamp: int, metric: dict) -> Metric:
    return Shutdown(node_id, timestamp)


def _parse_metric_pubsub_message_sent(
    node_id: str, timestamp: int, metric: dict
) -> Metric:
    message_id = metric["message_id"]
    topic = metric["topic"]
    delivered = metric["delivered"]
    return PubSubMessageSent(node_id, timestamp, topic, message_id, delivered)


def _parse_metric_pubsub_message_received(
    node_id: str, timestamp: int, metric: dict
) -> Metric:
    message_id = metric["message_id"]
    source = _node_ipaddr_to_node_id(metric["source"])
    topic = metric["topic"]
    hop_count = metric["hop_count"]
    delivered = metric["delivered"]
    return PubSubMessageReceived(
        node_id, timestamp, source, topic, hop_count, message_id, delivered
    )


def _parse_metric_pubsub_subscribe(
    node_id: str, timestamp: int, metric: dict
) -> Metric:
    topic = metric["topic"]
    return PubSubSubscribe(node_id, timestamp, topic)


def _parse_metric_pubsub_unsubscribe(
    node_id: str, timestamp: int, metric: dict
) -> Metric:
    topic = metric["topic"]
    return PubSubUnsubscribe(node_id, timestamp, topic)


def _parse_metric_span(node_id: str, timestamp: int, metric: dict) -> Metric:
    name = metric["name"]
    duration = metric["duration"]
    return Span(node_id, timestamp, name, duration)


def _parse_metric_network(node_id: str, timestamp: int, metric: dict) -> Metric:
    return Network(node_id, timestamp, metric["inbound"], metric["outbound"])


def _parse_metric_message_sent(node_id: str, timestamp: int, metric: dict) -> Metric:
    message_type = metric["message_type"]
    destination = metric["destination"]
    properties = metric["properties"]
    message = message_from_dict(message_type, properties)
    return MessageSent(
        node_id,
        timestamp,
        message_type,
        _node_ipaddr_to_node_id(destination),
        message,
    )


def _parse_metric_message_received(
    node_id: str, timestamp: int, metric: dict
) -> Metric:
    message_type = metric["message_type"]
    source = metric["source"]
    properties = metric["properties"]
    message = message_from_dict(message_type, properties)
    return MessageReceived(
        node_id,
        timestamp,
        message_type,
        _node_ipaddr_to_node_id(source),
        message,
    )


def _parse_metric_kad_identifier(node_id: str, timestamp: int, metric: dict) -> Metric:
    return KadIdentifier(node_id, timestamp, metric["identifier"])


def _parse_metric_routing_table(node_id: str, timestamp: int, metric: dict) -> Metric:
    return RoutingTableSnapshot(node_id, timestamp, metric["topic"], metric["buckets"])


def _node_ipaddr_to_node_id(ipaddr: str) -> str:
    return ipaddr.split(":")[1]


def _compute_metrics_per_type(metrics: list[Metric]) -> dict[type, list[Metric]]:
    metrics_per_type = {}
    for metric in metrics:
        metric_type = type(metric)
        if metric_type not in metrics_per_type:
            metrics_per_type[metric_type] = []
        metrics_per_type[type(metric)].append(metric)
    return metrics_per_type


def _compute_message_id_to_topic(metrics: list[Metric]) -> dict[str, str]:
    message_id_to_topic = {}
    for metric in metrics:
        if isinstance(metric, PubSubMessageSent):
            message_id_to_topic[metric.message_id] = metric.topic
        elif isinstance(metric, PubSubMessageReceived):
            message_id_to_topic[metric.message_id] = metric.topic
        elif isinstance(metric, (MessageSent, MessageReceived)) and isinstance(
            metric.message, (BroadcastHave, BroadcastWant, BroadcastMessage)
        ):
            message_id_to_topic[metric.message.message_id] = metric.message.topic
    return message_id_to_topic
