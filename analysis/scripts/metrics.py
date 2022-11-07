import json
import os
import sys

import numpy as np
from collections import defaultdict as dd
import matplotlib.pyplot as plt
import re

system_name = {
    "structured": "GossipSub-Kademlia",
    "unstructured": "PlumTree-HyParView",
    "kadpubsub": "KadPubSub-Kademlia"
}

PLOTS_OUT_PATH = f"{os.path.dirname(os.path.realpath(__file__))}/../plots/"


# Print iterations progress
def print_progress_bar(iteration, total, prefix='', suffix='', decimals=1, length=100, fill='█', print_end="\r"):
    """
    Call in a loop to create terminal progress bar
    @params:
        iteration   - Required  : current iteration (Int)
        total       - Required  : total iterations (Int)
        prefix      - Optional  : prefix string (Str)
        suffix      - Optional  : suffix string (Str)
        decimals    - Optional  : positive number of decimals in percent complete (Int)
        length      - Optional  : character length of bar (Int)
        fill        - Optional  : bar fill character (Str)
        print_end    - Optional  : end character (e.g. "\r", "\r\n") (Str)
    """
    percent = ("{0:." + str(decimals) + "f}").format(100 * (iteration / float(total)))
    filled_length = int(length * iteration // total)
    bar = fill * filled_length + '-' * (length - filled_length)
    print(f'\r{prefix} |{bar}| {percent}% {suffix}', end=print_end)
    # Print New Line on Complete
    if iteration == total:
        print()


def calc_redundancy(metrics):
    received = len(list(filter(lambda x: x["type"] == "pubReceived", metrics)))
    received_not_delivered = len(list(filter(lambda x: x["type"] == "pubReceived" and not x["message"]["delivered"],
                                             metrics)))
    return received, received_not_delivered


def print_redundancy_results(received, not_delivered):
    redundancy = 0 if received == 0 else (not_delivered / received) * 100
    print(
        "Redundancy: %s, (received: %d, not delivered: %d)" % ("{0:.2f}%".format(redundancy), received, not_delivered))


def plot_redundancy_results(redundancy_results):
    fig, ax = plt.subplots(num=1, clear=True)
    i = 0
    max_num = -1
    for bi, ps in sorted(redundancy_results.keys(), key=lambda x: (x[0], x[1])):
        received, not_delivered = redundancy_results[bi, ps]
        redundancy = 0 if received == 0 else (not_delivered / received) * 100
        ax.bar(f"{bi}ms\n{ps}bytes", not_delivered, label="Redundant Messages", color="red")
        ax.text(i, not_delivered / 2, "{0:.1f}%".format(redundancy), color='white',
                ha='center', va='center', fontweight='bold')
        ax.bar(f"{bi}ms\n{ps}bytes", received - not_delivered, bottom=not_delivered, label="Relevant Messages",
               color="green")
        ax.text(i, not_delivered + (received - not_delivered) / 2, "{0:.1f}%".format(100 - redundancy), color='white',
                ha='center', va='center', fontweight='bold')
        max_num = received if max_num < received else max_num
        if i == 0:
            ax.legend()
        i += 1
    ax.set(xlabel='Configuration', ylabel='Nº of Messages', ylim=(0, max_num * 1.05))
    fig.tight_layout()
    fig.savefig(PLOTS_OUT_PATH + "Redundancy_" + system_name[typ] + ".pdf")


def calc_latency(all_sorted_by_time):
    only_delivered_pubs = list(
        filter(lambda x: x["type"] == "pubReceived" and x["message"]["delivered"], all_sorted_by_time))
    sends = list(filter(lambda x: x["type"] == "pubSent", all_sorted_by_time))
    lst_avgs = []
    for e in sends:
        avg_ts = list(map(lambda x: x["timestamp"],
                          filter(lambda x: x["message"]["messageId"] == e["message"]["messageId"],
                                 only_delivered_pubs)))
        if len(avg_ts) > 0:
            lst_avgs.append(max(0, np.mean(avg_ts) - e["timestamp"]))
    return np.mean(lst_avgs)


def print_latency_results(avg_latency):
    print("Avg latency of delivered messages: %sms" % "{0:.2f}".format(avg_latency))


def plot_latency_results(latency_results):
    fig, ax = plt.subplots(num=1, clear=True)
    max_val = -1
    for bi, ps in sorted(latency_results.keys(), key=lambda x: (x[0], x[1])):
        avg_lat = latency_results[bi, ps]
        ax.bar(f"{bi}ms\n{ps}bytes", avg_lat, color="blue")
        max_val = avg_lat if avg_lat > max_val else max_val
    ax.set(xlabel='Configuration', ylabel='Latency (ms)', ylim=(0, max_val * 1.05))
    fig.tight_layout()
    fig.savefig(PLOTS_OUT_PATH + "Latency_" + system_name[typ] + ".pdf")


def calc_hop_latency(metrics):
    return np.mean(list(map(lambda x: x["message"]["hopCount"],
                            filter(lambda x: x["type"] == "pubReceived" and x["message"]["delivered"], metrics))))


def print_hop_latency_results(avg_hops):
    print("Avg hop latency of delivered messages: %s hops" % "{0:.2f}".format(avg_hops))


def plot_hop_latency_results(hop_latency_results):
    fig, ax = plt.subplots(num=1, clear=True)
    max_val = -1
    for bi, ps in sorted(hop_latency_results.keys(), key=lambda x: (x[0], x[1])):
        avg_hops = hop_latency_results[bi, ps]
        ax.bar(f"{bi}ms\n{ps}bytes", avg_hops, color="blue")
        max_val = avg_hops if avg_hops > max_val else max_val
    ax.set(xlabel='Configuration', ylabel='Average Nº of Hops', ylim=(0, max_val * 1.05))
    fig.tight_layout()
    fig.savefig(PLOTS_OUT_PATH + "Hop_Latency_" + system_name[typ] + ".pdf")

def calc_reliability(all_sorted_by_time):
    only_delivered_pubs = list(
        filter(lambda x: x["type"] == "pubReceived" and x["message"]["delivered"], all_sorted_by_time))
    subs_and_sends = list(
        filter(lambda x: x["type"] == "pubSent" or x["type"] == "subscribedTopic" or x["type"] == "unsubscribedTopic",
               all_sorted_by_time))

    rel_per_second = []
    second_start = None
    s = -1

    total_recv_pubs, total_expected_pubs = 0, 0
    second_recv_pubs, second_expected_pubs = 0, 0
    subs = dd(lambda: 0)
    i = 0
    for e in subs_and_sends:
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
            recv_pubs = len(
                list(filter(lambda x: x["message"]["messageId"] == e["message"]["messageId"], only_delivered_pubs))) + (
                            1 if e["message"]["delivered"] else 0)
            expected_pubs = subs[e["message"]["topic"]]
            second_recv_pubs += recv_pubs
            second_expected_pubs += expected_pubs
            total_recv_pubs += recv_pubs
            total_expected_pubs += expected_pubs
        i += 1
        print_progress_bar(i + 1, len(subs_and_sends), prefix='Progress:', suffix='Complete', length=50)

    return total_recv_pubs, total_expected_pubs, rel_per_second


def print_reliability_results(recv_pubs, expected_pubs):
    print("Avg reliability of delivered messages: %s, (received: %d, expected: %d)" % (
        "{0:.2f}%".format(100 if expected_pubs == 0 else (recv_pubs / expected_pubs) * 100), recv_pubs, expected_pubs))


def plot_reliability_results(reliability_results):
    fig, ax = plt.subplots(num=1, clear=True)
    min_len = 10000000
    for bi, ps in sorted(reliability_results.keys(), key=lambda x: (x[0], x[1])):
        rel_per_second = reliability_results[bi, ps]
        ax.plot(rel_per_second, label=f"{bi}ms {ps}bytes")
        min_len = len(rel_per_second) if len(rel_per_second) < min_len else min_len
    ax.set(xlabel='Time (s)', ylabel='Avg Reliability (%)', xlim=(0, min_len - 1), ylim=(0, 105))
    ax.legend()
    fig.tight_layout()
    fig.savefig(PLOTS_OUT_PATH + "Reliability_" + system_name[typ] + ".pdf")


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

            # print(filename + ":")
            # print_redundancy_results(pubs_received, pubs_not_delivered)
            # print_hop_latency_results(avg_hops)
            # print("-" * 80)

        print("=" * 100)
        print(f"Overall ({bi}ms {ps}bytes):")
        print_redundancy_results(sum_received, sum_not_delivered)
        results["redundancy"][bi, ps] = sum_received, sum_not_delivered

        avg_hop_latency = np.mean(list_avg_hops)
        print_hop_latency_results(avg_hop_latency)
        results["hop_latency"][bi, ps] = avg_hop_latency

        all_sorted_by_time = sorted([e for m in list_node_metrics for e in m], key=lambda x: x["timestamp"])

        avg_latency = calc_latency(all_sorted_by_time)
        print_latency_results(avg_latency)
        results["latency"][bi, ps] = avg_latency

        # recv_pubs, expected_pubs, rel_per_second = calc_reliability(all_sorted_by_time)

        recv_pubs, expected_pubs, rel_per_second = calc_reliability_fast((all_sorted_by_time)

        print_reliability_results(recv_pubs, expected_pubs)
        results["reliability"][bi, ps] = rel_per_second
    plot_redundancy_results(results["redundancy"])
    plot_hop_latency_results(results["hop_latency"])
    plot_latency_results(results["latency"])
    plot_reliability_results(results["reliability"])
