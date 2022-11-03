import json
import os
import numpy as np
from collections import defaultdict as dd

METRICS_PATH = '../metrics/'


def calc_redundancy(metrics):
    received = len(list(filter(lambda x: x["type"] == "pubReceived", metrics)))
    received_not_delivered = len(list(filter(lambda x: x["type"] == "pubReceived" and not x["message"]["delivered"],
                                             metrics)))
    return received, received_not_delivered


def print_redundancy_results(received, not_delivered):
    print("Redundancy: %s, (received: %d, not delivered: %d)" % ("{0:.2f}%".format((not_delivered / received) * 100),
                                                                 received, not_delivered))


def calc_hop_latency(metrics):
    return np.mean(list(map(lambda x: x["message"]["hopCount"],
                            filter(lambda x: x["type"] == "pubReceived" and x["message"]["delivered"], metrics))))


def print_hop_latency_results(avg_hops):
    print("Avg hop latency of delivered messages: %s hops" % "{0:.2f}".format(avg_hops))


def calc_reliability(list_node_metrics):
    all_sorted_by_time = sorted([e for m in list_node_metrics for e in m], key=lambda x: x["timestamp"])

    recv_pubs, expected_pubs = 0, 0
    subs = dd(lambda: 0)
    for e in all_sorted_by_time:
        if e["type"] == "subscribedTopic":
            subs[e["message"]["topic"]] += 1
        elif e["type"] == "unsubscribedTopic":
            subs[e["message"]["topic"]] -= 1
        elif e["type"] == "pubSent":
            init_diff = expected_pubs - recv_pubs
            recv_pubs += len(list(filter(
                lambda x: x["type"] == "pubReceived" and x["message"]["delivered"] and x["message"]["messageId"] ==
                          e["message"]["messageId"], all_sorted_by_time)))
            expected_pubs += subs[e["message"]["topic"]]

    return recv_pubs, expected_pubs


def print_reliability_results(recv_pubs, expected_pubs):
    print("Avg reliability of delivered messages: %s, (received: %d, expected: %d)" % (
        "{0:.2f}%".format((recv_pubs / expected_pubs) * 100), recv_pubs, expected_pubs))


if __name__ == "__main__":
    path = METRICS_PATH
    sum_received, sum_not_delivered = 0, 0
    list_avg_hops = []
    list_node_metrics = []

    for filename in os.listdir(path):
        print(filename + ":")
        file = open(path + filename)
        node_metrics = list(map(lambda x: json.loads(x), file.readlines()))
        list_node_metrics.append(node_metrics)

        pubs_received, pubs_not_delivered = calc_redundancy(node_metrics)
        sum_received += pubs_received
        sum_not_delivered += pubs_not_delivered

        avg_hops = calc_hop_latency(node_metrics)
        list_avg_hops.append(avg_hops)

        print_redundancy_results(pubs_received, pubs_not_delivered)
        print_hop_latency_results(avg_hops)
        print("-" * 80)

    recv_pubs, expected_pubs = calc_reliability(list_node_metrics)
    print("=" * 100)
    print("Overall:")
    print_redundancy_results(sum_received, sum_not_delivered)
    print_hop_latency_results(np.mean(list_avg_hops))
    print_reliability_results(recv_pubs, expected_pubs)
