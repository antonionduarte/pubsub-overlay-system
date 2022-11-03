import json
import os
import numpy as np

METRICS_PATH = '../metrics/'


def calc_redundancy(metrics):
    received = len(list(filter(lambda x: x["type"] == "pubReceived", metrics)))
    received_not_delivered = len(list(filter(lambda x: x["type"] == "pubReceived" and not x["message"]["delivered"],
                                             metrics)))
    return received, received_not_delivered


def print_redundancy_results(received, not_delivered):
    print("redundancy: %s, (received: %d, not delivered: %d)" % ("{0:.2f}%".format((not_delivered / received) * 100),
                                                                 received, not_delivered))


def calc_hop_latency(metrics):
    return np.mean(list(map(lambda x: x["message"]["hopCount"],
                            filter(lambda x: x["type"] == "pubReceived" and x["message"]["delivered"], metrics))))


def print_hop_latency_results(avg_hops):
    print("average hop latency of received messages: %s hops" % "{0:.2f}".format(avg_hops))


if __name__ == "__main__":
    path = METRICS_PATH
    sum_received, sum_not_delivered = 0, 0
    list_avg_hops = []

    for filename in os.listdir(path):
        print(filename + ":")
        file = open(path + filename)
        node_metrics = list(map(lambda x: json.loads(x), file.readlines()))

        pubs_received, pubs_not_delivered = calc_redundancy(node_metrics)
        sum_received += pubs_received
        sum_not_delivered += pubs_not_delivered

        avg_hops = calc_hop_latency(node_metrics)
        list_avg_hops.append(avg_hops)

        print_redundancy_results(pubs_received, pubs_not_delivered)
        print_hop_latency_results(avg_hops)

    print("Overall:")
    print_redundancy_results(sum_received, sum_not_delivered)
    print_hop_latency_results(np.mean(list_avg_hops))
