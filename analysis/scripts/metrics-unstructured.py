# I made this script because it's probably
# going to be dumb looking code and therefore I don't want to polute
# the other one.


import json
import os
from typing import Tuple


METRICS_PATH = '../metrics/'


def parse_metrics(path: str) -> list:
    lst = []

    for filename in os.listdir(path):
        file = open(path + filename)
        json_lst = list(map(lambda x: json.loads(x), file.readlines()))
        lst.append(json_lst)

    return lst


def redundancy_metrics(json_lst) -> Tuple[int, int]:
    repeated_messages = 0
    number_messages = 0

    for json_obj in json_lst:
        messages = list(filter(lambda x: x["type"] == "messageReceived", json_obj))
        message_ids = set(map(lambda x: x["message"]["messageId"], messages))

        number_messages += len(messages)
        repeated_messages += len(messages) - len(message_ids)
    
    return repeated_messages, number_messages


def latency_metrics(json_lst) -> int:
    global_messages = 0
    global_hops = 0

    for json_obj in json_lst:
        subscribed = []
        messages = 0
        hop_count = 0

        for message in json_obj:
            if message['type'] == 'subscribedTopic':
                if not subscribed.count(message['message']['topic']):
                    subscribed.append(message['message']['topic'])

            elif message['type'] == 'unsubscribedTopic':
                subscribed.remove(message['message']['topic'])

            elif message['type'] == 'messageReceived':
                if subscribed.count(message['message']['topic']):
                    messages += 1
                    hop_count += int(message['message']['hopCount'])

        global_messages += messages 
        global_hops += hop_count

    return int(global_hops / global_messages)


if __name__ == "__main__":
    parsed_metrics = parse_metrics(METRICS_PATH)
    
    # Redundancy:
    repeated_messages, number_messages = redundancy_metrics(parsed_metrics)
    print('Number Messages: ', number_messages)
    print('Repeated Messages: ', repeated_messages)

    # Latency in Hops:
    avg_hop_latency = latency_metrics(parsed_metrics)
    print('Avg Hops: ', avg_hop_latency)