import json
import os
import numpy as np
from collections import defaultdict as dd
import matplotlib.pyplot as plt

METRICS_PATH = 'metrics/'
PLOTS_OUT_PATH = 'plots/'
TEXT_OUT_PATH = 'text/'

FIRST_PROTOCOL = 'GossipSub'
SECOND_PROTOCOL = 'Kademlia'
NAME = FIRST_PROTOCOL + "-" + SECOND_PROTOCOL


def calc_redundancy(metrics):
    received = len(list(filter(lambda x: x["type"] == "pubReceived", metrics)))
    received_not_delivered = len(list(filter(lambda x: x["type"] == "pubReceived" and not x["message"]["delivered"],
                                             metrics)))
    return received, received_not_delivered


def print_redundancy_results(received, not_delivered):
    print("Redundancy: %s, (received: %d, not delivered: %d)" % (
        "{0:.2f}%".format(0 if received == 0 else (not_delivered / received) * 100),
        received, not_delivered))


def calc_hop_latency(metrics):
    return np.mean(list(map(lambda x: x["message"]["hopCount"],
                            filter(lambda x: x["type"] == "pubReceived" and x["message"]["delivered"], metrics))))


def print_hop_latency_results(avg_hops):
    print("Avg hop latency of delivered messages: %s hops" % "{0:.2f}".format(avg_hops))


def create_conjoined_metrics_file(lines):
    f = open(TEXT_OUT_PATH + "metrics_all.json", "w")
    for line in lines:
        f.write("%s\n" % line)


def calc_reliability(list_node_metrics):
    all_sorted_by_time = sorted([e for m in list_node_metrics for e in m], key=lambda x: x["timestamp"])
    create_conjoined_metrics_file(all_sorted_by_time)  # for debug
    rel_per_second = []
    rel_per_msg = []
    second_start = None
    s = -1

    total_recv_pubs, total_expected_pubs = 0, 0
    second_recv_pubs, second_expected_pubs = 0, 0
    subs = dd(lambda: 0)
    i = 0
    for e in all_sorted_by_time:
        if e["type"] == "subscribedTopic":
            subs[e["message"]["topic"]] += 1
        elif e["type"] == "unsubscribedTopic":
            subs[e["message"]["topic"]] -= 1
        elif e["type"] == "pubSent":
            if second_start is None or e["timestamp"] >= second_start + 1000:
                if second_start is not None:
                    rel_per_second.append(
                        (second_recv_pubs / second_expected_pubs) * 100 if second_expected_pubs > 0 else 100)
                second_start = e["timestamp"]
                second_recv_pubs, second_expected_pubs = 0, 0
                s += 1
            recv_pubs = len(list(filter(
                lambda x: x["type"] == "pubReceived" and x["message"]["delivered"] and x["message"]["messageId"] ==
                          e["message"]["messageId"], all_sorted_by_time[max(0, i - 100)::]))) + (
                            1 if e["message"]["delivered"] else 0)
            expected_pubs = subs[e["message"]["topic"]]
            second_recv_pubs += recv_pubs
            second_expected_pubs += expected_pubs
            total_recv_pubs += recv_pubs
            total_expected_pubs += expected_pubs
            rel_per_msg.append((recv_pubs / expected_pubs) * 100 if expected_pubs > 0 else 100)
        i += 1

    return total_recv_pubs, total_expected_pubs, rel_per_second, rel_per_msg


def print_reliability_results(recv_pubs, expected_pubs, rel_per_second, rel_per_msg):
    print("Avg reliability of delivered messages: %s, (received: %d, expected: %d)" % (
        "{0:.2f}%".format(100 if expected_pubs == 0 else (recv_pubs / expected_pubs) * 100), recv_pubs, expected_pubs))

    fig, ax = plt.subplots(num=1, clear=True)
    os.makedirs(PLOTS_OUT_PATH, exist_ok=True)
    ax.plot(rel_per_second, label=NAME, color="blue")
    ax.set(xlabel='Time (s)', ylabel='Avg Reliability (%)', xlim=(0, len(rel_per_second) - 1), ylim=(0, 105))
    ax.legend()
    fig.tight_layout()
    fig.savefig(PLOTS_OUT_PATH + "Rel_per_sec_" + NAME + ".pdf")

    fig, ax = plt.subplots(num=1, clear=True)
    os.makedirs(PLOTS_OUT_PATH, exist_ok=True)
    ax.plot(rel_per_msg, label=NAME, color="blue")
    ax.set(xlabel='Message', ylabel='Avg Reliability (%)', xlim=(0, len(rel_per_msg) - 1), ylim=(0, 105))
    ax.legend()
    fig.tight_layout()
    fig.savefig(PLOTS_OUT_PATH + "Rel_per_msg_" + NAME + ".pdf")


if __name__ == "__main__":
    os.makedirs(PLOTS_OUT_PATH, exist_ok=True)
    os.makedirs(TEXT_OUT_PATH, exist_ok=True)

    sum_received, sum_not_delivered = 0, 0
    list_avg_hops = []
    list_node_metrics = []

    for filename in os.listdir(METRICS_PATH):
        print(filename + ":")
        file = open(METRICS_PATH + filename)
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

    print("=" * 100)
    print("Overall:")
    print_redundancy_results(sum_received, sum_not_delivered)
    print_hop_latency_results(np.mean(list_avg_hops))
    recv_pubs, expected_pubs, rel_per_second, rel_per_msg = calc_reliability(list_node_metrics)
    print_reliability_results(recv_pubs, expected_pubs, rel_per_second, rel_per_msg)
