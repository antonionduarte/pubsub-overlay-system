import json
import os
import sys
import numpy as np
import matplotlib.pyplot as plt
import re

from collections import defaultdict as dd


system_name = {
    "structured": "GossipSub-Kademlia",
    "unstructured": "PlumTree-HyParView",
    "kadpubsub": "KadPubSub-Kademlia"
}


PLOTS_OUT_PATH = f"{os.path.dirname(os.path.realpath(__file__))}/../plots/"


def calc_redundancy(metrics):
    received = len(list(filter(lambda x: x["type"] == "pubReceived", metrics)))
    received_not_delivered = len(list(filter(lambda x: x["type"] == "pubReceived" and not x["message"]["delivered"],
                                             metrics)))
    return received, received_not_delivered


def print_redundancy_results(received, not_delivered):
    redundancy = 0 if received == 0 else (not_delivered / received) * 100
    print(
        "Redundancy: %s, (received: %d, not delivered: %d)" % ("{0:.2f}%".format(redundancy), received, not_delivered))


def calc_hop_latency(metrics):
    return np.mean(list(map(lambda x: x["message"]["hopCount"],
                            filter(lambda x: x["type"] == "pubReceived" and x["message"]["delivered"], metrics))))


def print_hop_latency_results(avg_hops):
    print("Avg hop latency of delivered messages: %s hops" % "{0:.2f}".format(avg_hops))


def calc_reliability_fast(all_sorted_by_time):
    curr_subs = dd(lambda: []) # map topic -> amountSubsToTopic
    total_expected_pubs = 0
    total_delivered_pubs = 0

    for metric in all_sorted_by_time:
        if metric["type"] == "pubSent":
            total_expected_pubs += len(curr_subs[metric["message"]["topic"]])

        elif metric["type"] == "pubReceived":
            if metric["message"]["delivered"]:
                if curr_subs[metric["message"]["topic"]].count(metric["nodeId"]) > 0:
                    total_delivered_pubs += 1

        elif metric["type"] == "subscribedTopic":
            curr_subs[metric["message"]["topic"]].append(metric["nodeId"])

        elif metric["type"] == "unsubscribedTopic":
            curr_subs[metric["message"]["topic"]].remove(metric["nodeId"])

    return total_delivered_pubs, total_expected_pubs


def print_reliability_results(recv_pubs, expected_pubs):
    print("Avg reliability of delivered messages: %s, (received: %d, expected: %d)" % (
        "{0:.2f}%".format(100 if expected_pubs == 0 else (recv_pubs / expected_pubs) * 100), recv_pubs, expected_pubs))


if __name__ == "__main__":
    typ = "structured" if len(sys.argv) < 2 else sys.argv[1]
    METRICS_PATH = f"{os.path.dirname(os.path.realpath(__file__))}/../metrics_{typ}/"
    os.makedirs(PLOTS_OUT_PATH, exist_ok=True)

    sum_received, sum_not_delivered = 0, 0
    list_avg_hops = []
    list_node_metrics = []
    results = dd(lambda: {})

    for folder in sorted(os.listdir(METRICS_PATH)):
        reres = re.search(r"(\d+)ms_(\d+)b", folder)
        bi = int(reres.group(1))
        ps = int(reres.group(2))
        for filename in sorted(os.listdir(METRICS_PATH + folder + "/")):
            file = open(METRICS_PATH + folder + "/" + filename)
            node_metrics = list(map(lambda x: json.loads(x), file.readlines()))
            list_node_metrics.append(node_metrics)

            pubs_received, pubs_not_delivered = calc_redundancy(node_metrics)
            sum_received += pubs_received
            sum_not_delivered += pubs_not_delivered

            avg_hops = calc_hop_latency(node_metrics)
            list_avg_hops.append(avg_hops)

        print("=" * 100)
        print(f"Overall ({bi}ms {ps}bytes):")
        print_redundancy_results(sum_received, sum_not_delivered)
        results["redundancy"][bi, ps] = sum_received, sum_not_delivered

        avg_hop_latency = np.mean(list_avg_hops)
        print_hop_latency_results(avg_hop_latency)
        results["hop_latency"][bi, ps] = avg_hop_latency

        all_sorted_by_time = sorted([e for m in list_node_metrics for e in m], key=lambda x: x["timestamp"])
        recv_pubs, expected_pubs = calc_reliability_fast((all_sorted_by_time))
        print_reliability_results(recv_pubs, expected_pubs)
