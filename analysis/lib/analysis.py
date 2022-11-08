from __future__ import annotations
from collections import defaultdict

import pandas as pd

from typing import Dict, Set

from lib.experiment import PubSubExperiment, PubSubExperimentResults
from lib.message import (
    BroadcastHave,
    BroadcastMessage,
    BroadcastWant,
    from_dict as message_from_dict,
)
from lib.metrics2 import *


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


class KadPubSubAnalyzer:
    def __init__(self, experiment: PubSubExperiment, metrics: list[Metric]):
        self.__experiment = experiment
        self.__metrics = metrics
        self.__message_id_to_topic = KadPubSubAnalyzer.__compute_message_id_to_topic(
            self.__metrics
        )
        self.__messages_reliability = self.__compute_messages_reliability()

    @property
    def experiment(self) -> PubSubExperiment:
        return self.__experiment

    @property
    def metrics(self) -> list[Metric]:
        return self.__metrics

    def metrics_with(self, *args) -> list[Metric]:
        m = []
        for metric in self.__metrics:
            for filter in args:
                if filter(metric):
                    m.append(metric)
        return m

    def metrics_of_type(self, t) -> list[Metric]:
        return self.metrics_with(lambda m: isinstance(m, t))

    def topics(self) -> Set[str]:
        """
        Compute the set of all known topics.
        """
        return set([m.topic for m in self.metrics_of_type(PubSubSubscribe)])

    def nodes(self) -> Set[str]:
        """
        Compute the set of all known nodes.
        """
        return set([m.node for m in self.metrics])

    def messages(self) -> Set[str]:
        """
        Compute the set of all known messages.
        """
        return set([m.message_id for m in self.messages_timestamped()])

    def messages_timestamped(self) -> list[TimeStampedMessageID]:
        """
        Compute the set of all known messages with their timestamps.
        """
        bcasts = self.metrics_with(
            lambda m: isinstance(m, MessageSent)
            and isinstance(m.message, BroadcastMessage)
        )
        message_ids = {}
        timestamped = []
        for m in bcasts:
            if m.message.message_id not in message_ids:
                message_ids[m.message.message_id] = m.timestamp
                timestamped.append(
                    TimeStampedMessageID(m.timestamp, m.message.message_id)
                )
            else:
                assert message_ids[m.message.message_id] <= m.timestamp
        return timestamped

    def topic_subscribers(self, topic: str) -> list[str]:
        """
        Compute the set of nodes that are subscribed to a given topic.
        This will run over the subscriptions/unsubscriptions of a particular node so it gives the state after the last subscription/unsubscription.
        """
        subscriptions = self.metrics_with(
            lambda m: isinstance(m, (PubSubSubscribe, PubSubUnsubscribe))
            and m.topic == topic
        )
        subscribers = set()
        for m in subscriptions:
            if isinstance(m, PubSubSubscribe):
                subscribers.add(m.node)
            elif isinstance(m, PubSubSubscribe):
                subscribers.remove(m.node)
        return subscribers

    def topic_of_message(self, message: str) -> str:
        """
        Get the topic of a given message.
        """
        return self.__message_id_to_topic[message]

    def timestamp_of_message(self, message: str) -> int:
        """
        Get the timestamp of a given message.
        """
        return self.metrics_with(
            lambda m: isinstance(m, MessageSent) and m.message.message_id == message
        )[0].timestamp

    def routing_table_snapshot(
        self, node: str, topic: str, timestamp: int
    ) -> list[str]:
        """
        Compute the routing table of a given node at a given timestamp.
        """
        rts = self.metrics_with(
            lambda m: isinstance(m, RoutingTableSnapshot)
            and m.node == node
            and m.topic == topic
            and m.timestamp <= timestamp
        )
        return rts[-1]

    def messages_reliability(self) -> Dict[str, float]:
        """
        Get the reliability of each message.
        """
        return self.__messages_reliability.copy()

    def message_reliaibility(self, message: str) -> float:
        """
        Get the reliability of a given message.
        """
        return self.__messages_reliability[message]

    def reliability(self) -> pd.DataFrame:
        timestamped = self.messages_timestamped()
        timestamps = [m.timestamp for m in timestamped]
        reliabilities = [self.message_reliaibility(m.message_id) for m in timestamped]
        return pd.DataFrame(
            pd.Series(reliabilities, index=timestamps, name="reliability")
        )

    def __compute_messages_reliability(self) -> Dict[str, float]:
        """
        Compute the reliability of each message.
        """
        messages = self.messages()
        messages_reliability = defaultdict(lambda: 0)
        messages_expected = {}
        expected_per_topic = {
            topic: len(self.topic_subscribers(topic)) for topic in self.topics()
        }
        for m in messages:
            message_topic = self.topic_of_message(m)
            messages_reliability[m] = 0.0
            messages_expected[m] = expected_per_topic[message_topic]
        for m in self.metrics_of_type(PubSubMessageReceived):
            if m.delivered:
                messages_reliability[m.message_id] += 1.0
        for m in messages:
            messages_reliability[m] /= messages_expected[m]
        return messages_reliability

    @staticmethod
    def from_experiment_results(
        results: PubSubExperimentResults, ignore_unknown_metrics=True
    ) -> KadPubSubAnalyzer:
        """
        Create an analyzer from the results of an experiment
        """

        experiment = results.experiment
        metrics = []
        for node_id, node_metrics in results.results.items():
            metrics.extend(
                KadPubSubAnalyzer.__parse_node_metrics(
                    node_id, node_metrics, ignore_unknown_metrics
                )
            )
        metrics.sort(key=lambda m: m.timestamp)
        KadPubSubAnalyzer.__fix_timestamps(metrics)

        return KadPubSubAnalyzer(experiment, metrics)

    @staticmethod
    def __fix_timestamps(m: list[Metric]):
        lowest_timestamp = min([m.timestamp for m in m])
        for metric in m:
            metric.timestamp -= lowest_timestamp
            metric.timestamp = pd.Timestamp(metric.timestamp / 1000, unit="s")

    @staticmethod
    def __parse_node_metrics(
        node_id: str, metrics: list[dict], ignore_unknown_metrics: bool
    ) -> list[Metric]:
        parsers = {
            "PubSubMessageSent": KadPubSubAnalyzer.__parse_metric_pubsub_message_sent,
            "PubSubMessageReceived": KadPubSubAnalyzer.__parse_metric_pubsub_message_received,
            "PubSubSubscription": KadPubSubAnalyzer.__parse_metric_pubsub_subscribe,
            "PubSubUnsubscription": KadPubSubAnalyzer.__parse_metric_pubsub_unsubscribe,
            "Span": KadPubSubAnalyzer.__parse_metric_span,
            "MessageSent": KadPubSubAnalyzer.__parse_metric_message_sent,
            "MessageReceived": KadPubSubAnalyzer.__parse_metric_message_received,
            "RoutingTable": KadPubSubAnalyzer.__parse_metric_routing_table,
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

    @staticmethod
    def __parse_metric_pubsub_message_sent(
        node_id: str, timestamp: int, metric: dict
    ) -> Metric:
        message_id = metric["message_id"]
        topic = metric["topic"]
        delivered = metric["delivered"]
        return PubSubMessageSent(node_id, timestamp, topic, message_id, delivered)

    @staticmethod
    def __parse_metric_pubsub_message_received(
        node_id: str, timestamp: int, metric: dict
    ) -> Metric:
        message_id = metric["message_id"]
        topic = metric["topic"]
        hop_count = metric["hop_count"]
        delivered = metric["delivered"]
        return PubSubMessageReceived(
            node_id, timestamp, topic, hop_count, message_id, delivered
        )

    @staticmethod
    def __parse_metric_pubsub_subscribe(
        node_id: str, timestamp: int, metric: dict
    ) -> Metric:
        topic = metric["topic"]
        return PubSubSubscribe(node_id, timestamp, topic)

    @staticmethod
    def __parse_metric_pubsub_unsubscribe(
        node_id: str, timestamp: int, metric: dict
    ) -> Metric:
        topic = metric["topic"]
        return PubSubUnsubscribe(node_id, timestamp, topic)

    @staticmethod
    def __parse_metric_span(node_id: str, timestamp: int, metric: dict) -> Metric:
        name = metric["name"]
        duration = metric["duration"]
        return Span(node_id, timestamp, name, duration)

    @staticmethod
    def __parse_metric_message_sent(
        node_id: str, timestamp: int, metric: dict
    ) -> Metric:
        message_type = metric["message_type"]
        destination = metric["destination"]
        properties = metric["properties"]
        message = message_from_dict(message_type, properties)
        return MessageSent(
            node_id,
            timestamp,
            message_type,
            KadPubSubAnalyzer.__node_ipaddr_to_node_id(destination),
            message,
        )

    @staticmethod
    def __parse_metric_message_received(
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
            KadPubSubAnalyzer.__node_ipaddr_to_node_id(source),
            message,
        )

    @staticmethod
    def __parse_metric_routing_table(
        node_id: str, timestamp: int, metric: dict
    ) -> Metric:
        return RoutingTableSnapshot(
            node_id, timestamp, metric["topic"], metric["buckets"]
        )

    @staticmethod
    def __node_ipaddr_to_node_id(ipaddr: str) -> str:
        return ipaddr.split(":")[1]

    @staticmethod
    def __compute_message_id_to_topic(metrics: list[Metric]) -> dict[str, str]:
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
