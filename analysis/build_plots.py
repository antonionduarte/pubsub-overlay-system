from lib.experiment import PubSubExperimentResults
from lib.analysis import PubSubAnalyzer
from lib.message import BroadcastHave
from lib.metrics import MessageSent, PubSubMessageSent


def load_experiment(path: str) -> PubSubAnalyzer:
    return PubSubAnalyzer.from_experiment_results(
        PubSubExperimentResults.load_from_file(path)
    )


configs = {
    "config1": {
        "GossipSub": load_experiment("experiments/gossipsub-config1.json"),
        "KadPubSub": load_experiment("experiments/kadpubsub-config1.json"),
        "Plumtree": load_experiment("experiments/plumtree-config1.json"),
    }
}


