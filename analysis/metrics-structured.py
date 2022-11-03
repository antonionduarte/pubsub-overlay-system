import json
import os

METRICS_PATH = '../metrics/'


def parse_metrics(path: str) -> list:
    lst = []

    for filename in os.listdir(path):
        file = open(path + filename)
        json_lst = list(map(lambda x: json.loads(x), file.readlines()))
        lst.append(json_lst)

    return lst


def calc_redundancy(metrics):
    received = len(list(filter(lambda x: x["type"] == "pubReceived", metrics)))
    received_not_delivered = len(list(filter(lambda x: x["type"] == "pubReceived" and not x["message"]["delivered"],
                                             metrics)))
    return received, received_not_delivered


if __name__ == "__main__":
    path = METRICS_PATH
    for filename in os.listdir(path):
        print(filename + ":")
        file = open(path + filename)
        node_metrics = list(map(lambda x: json.loads(x), file.readlines()))

        pubs_received, pubs_not_delivered = calc_redundancy(node_metrics)
        print("redundancy: %s, (received: %d, not delivered: %d)", "{0:.1f}%".format(pubs_not_delivered/pubs_received),
              pubs_received, pubs_not_delivered)

