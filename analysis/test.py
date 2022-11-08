from lib.experiment import PubSubExperimentResults
from lib.analysis import KadPubSubAnalyzer
from lib.message import BroadcastHave
from lib.metrics2 import MessageSent, PubSubMessageSent

results = PubSubExperimentResults.load_from_file("experiments/experiment_1.json")
analyzer = KadPubSubAnalyzer.from_experiment_results(results)
print(
    analyzer.metrics_with(
        lambda m: isinstance(m, MessageSent) and isinstance(m.message, BroadcastHave)
    )
)
