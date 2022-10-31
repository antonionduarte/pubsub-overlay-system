import json

messages = list(map(lambda x: json.loads(x), open('metrics/metrics_5050.json').readlines()))

message_ids = set(map(lambda x: x["message"]["messageId"], messages))

print("Repeated Messages: ", len(messages) - len(message_ids))
print("nMessages: ", len(messages))
