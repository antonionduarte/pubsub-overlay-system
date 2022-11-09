from __future__ import annotations

from typing import Any, Dict
import time
import dataclasses
import os
import logging
import subprocess
import json
import yaml

BOOTSTRAP_PORT = 5000

PROTOCOL_KADPUBSUB = "kadpubsub"
PROTOCOL_GOSSIPSUB = "gossipsub"
PROTOCOL_PLUMTREE = "plumtree"


@dataclasses.dataclass
class PubSubExperiment:
    # The identifier of the protocol being used
    protocol: str
    # Number of nodes spawned to run the experiment
    number_nodes: int
    # Number of seconds until starting to send subscriptions
    bootstrap_time: float
    # Number of seconds until starting to publish messages
    prepare_time: float
    # Number of seconds to run the experiment for
    run_time: float
    # Number of seconds to wait before stopping the experiment after publishing the last message
    cooldown_time: float
    # Number of bytes of the payload of each message
    payload_size: int
    # Total number of generated topics
    number_topics: int
    # Number of topic subscriptions per node
    number_topics_to_subscribe: int
    # Number of topics to publish
    number_topics_to_publish: int
    # Number of messages to send per second
    broadcast_rate: float
    # Random seed used to run the experiment
    random_seed: int
    # Metrics logging level
    # off - no metrics are logged
    # basic - only pubsub and network metrics are logged
    # detailed - all metrics are logged
    metric_level: str
    # Protocol specific properties
    protocol_parameters: Dict[str, Any]

    def is_equivalent(self, other: PubSubExperiment) -> bool:
        filds_to_skip = ["protocol", "metric_level"]
        for field in dataclasses.fields(self):
            if field.name in filds_to_skip:
                continue
            if field.name == "protocol_parameters":
                for k, v in self.protocol_parameters.items():
                    if (
                        k in other.protocol_parameters
                        and other.protocol_parameters[k] != v
                    ):
                        return False
                continue
            if getattr(self, field.name) != getattr(other, field.name):
                return False
        return True


@dataclasses.dataclass
class PubSubExperimentResults:
    experiment: PubSubExperiment
    # Metrics from every node in the experiment
    results: Dict[str, list[dict]]

    @staticmethod
    def load_from_file(path: str) -> PubSubExperimentResults:
        """
        Load the results of an experiment from a file containing the output of the experiment

        :param path: The path to the file containing the experiment results
        """
        with open(path) as f:
            return PubSubExperimentResults.load_from_dict(json.load(f))

    @staticmethod
    def load_from_dict(d: dict) -> PubSubExperimentResults:
        """
        Load the results of an experiment from a dictionary containing the output of the experiment

        :param d: The dictionary containing the experiment results
        """
        experiment = PubSubExperiment(**d["experiment"])
        results = d["results"]
        return PubSubExperimentResults(experiment=experiment, results=results)


def load_experiments(path: str) -> Dict[str, PubSubExperiment]:
    """
    Load a set of experiments from a JSON file

    :param path: The path to the JSON file containing the experiments
    """

    def _merge_configs(configs):
        merged = {}
        for config in configs:
            mpp = config.get("protocol_parameters", {})
            cpp = merged.get("protocol_parameters", {})
            merged = merged | config
            merged["protocol_parameters"] = mpp | cpp
        return merged

    def _load_template(name, templates, visited=set()):
        if name in visited:
            raise ValueError(f"Recursive template definition")
        if name not in templates:
            raise ValueError(f"Template {name} not found")
        template = templates[name]
        derived = [
            _load_template(t, templates, visited.union({name}))
            for t in template.get("derive", [])
        ]
        parameters = template.get("parameters", {})
        return _merge_configs(derived + [parameters])

    d = yaml.safe_load(open(path))
    e = {}
    templates = d.get("templates", {})
    experiments = d.get("experiments", {})

    for name, experiment in experiments.items():
        derived = [_load_template(t, templates) for t in experiment.get("derive", [])]
        parameters = experiment.get("parameters", {})
        exp = _merge_configs(derived + [parameters])
        e[name] = PubSubExperiment(**exp)

    return e


def load_experiment_results(path: str) -> PubSubExperimentResults:
    """
    Load the results of an experiment from a file containing the output of the experiment

    :param path: The path to the file containing the experiment results
    """
    obj = json.load(open(path))
    experiment = PubSubExperiment(**obj["experiment"])
    results = obj["results"]
    return PubSubExperimentResults(experiment=experiment, results=results)


def run_experiment(name: str, experiment: PubSubExperiment, jarpath: str):
    logging.info("Running experiment")
    logging.info(json.dumps(dataclasses.asdict(experiment), indent=4))

    logging.info("Creating metrics directory")
    os.makedirs("metrics", exist_ok=True)

    logging.info("Removing old metrics files")
    for filename in os.listdir("metrics"):
        os.remove(os.path.join("metrics", filename))

    logging.info("Removing all existing containers")
    subprocess.run(["sh", "-c", "docker rm -f $(docker ps -aq)"])

    logging.info(f"Creating {experiment.number_nodes} containers")
    protocol_to_main_class = {
        PROTOCOL_KADPUBSUB: "asd.KadPubSubMain",
        PROTOCOL_GOSSIPSUB: "asd.StructuredMain",
        PROTOCOL_PLUMTREE: "asd.UnstructuredMain",
    }
    metrics_level_to_number = {
        "off": 0,
        "basic": 1,
        "detailed": 2,
    }
    metrics_level = metrics_level_to_number[experiment.metric_level]
    abs_jar_path = os.getcwd() + "/" + jarpath
    abs_log4j_path = os.getcwd() + "/../log4j2.xml"
    abs_metrics_path = os.getcwd() + "/metrics"
    for i in range(experiment.number_nodes):
        port = BOOTSTRAP_PORT + i
        args = [
            "docker",
            "create",
            f"--name=asd_{port}",
            "-it",
            "--network=host",
            "--security-opt",
            "label=disable",
            "-v",
            f"{abs_jar_path}:/usr/local/app.jar",
            "-v",
            f"{os.getcwd()}/../babel_config.properties:/usr/local/babel_config.properties",
            "-v",
            f"{abs_log4j_path}:/usr/local/log4j2.xml",
            "-v",
            f"{abs_metrics_path}:/usr/local/metrics/",
            "--workdir=/usr/local/",
            "docker.io/amazoncorretto:19",
            "java",
            "-Xmx1G",
            "-ea",
            "-cp",
            "/usr/local/app.jar",
            f"{protocol_to_main_class[experiment.protocol]}",
            f"babel_port={port}",
            "babel_address=127.0.0.1",
            # Experiment parameters
            f"prepare_prepare_time={experiment.bootstrap_time}",
            f"prepare_time={experiment.prepare_time}",
            f"run_time={experiment.run_time}",
            f"cooldown_time={experiment.cooldown_time}",
            f"payload_size={experiment.payload_size}",
            f"n_topics={experiment.number_topics}",
            f"sub_topics={experiment.number_topics_to_subscribe}",
            f"pub_topics={experiment.number_topics_to_publish}",
            f"broadcast_interval={int((1 / experiment.broadcast_rate) * 1000)}",
            f"random_seed={experiment.random_seed}",
            f"metrics_level={metrics_level}",
            "automated=true",
        ]
        if port != BOOTSTRAP_PORT:
            args.append(f"hypar_bootstrap=127.0.0.1:{port - 1}")
            args.append(f"kad_bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")

        # Protocol parameters
        for key, value in experiment.protocol_parameters.items():
            args.append(f"{key}={value}")

        subprocess.run(args)

    logging.info("Starting containers")
    for i in range(experiment.number_nodes):
        port = BOOTSTRAP_PORT + i
        args = [
            "docker",
            "start",
            f"asd_{port}",
        ]
        subprocess.Popen(args, stdout=subprocess.DEVNULL, start_new_session=True)
        if i == 0:
            time.sleep(2)

    procs = []
    for i in range(experiment.number_nodes):
        port = BOOTSTRAP_PORT + i
        args = [
            "docker",
            "wait",
            f"asd_{port}",
        ]
        procs.append(
            subprocess.Popen(args, stdout=subprocess.DEVNULL, start_new_session=True)
        )

    logging.info("Waiting for containers to finish")
    for p in procs:
        p.wait()

    logging.info("Loading all metrics and joining results")
    node_metrics = {}
    for filename in os.listdir("metrics"):
        with open(os.path.join("metrics", filename), "r") as f:
            id = filename.strip(".json")
            m = list(map(json.loads, f.readlines()))
            node_metrics[id] = m

    experiment_results = {
        "experiment": dataclasses.asdict(experiment),
        "results": node_metrics,
    }

    logging.info("Creating experiments output directory")
    os.makedirs("experiments", exist_ok=True)
    output_path = f"experiments/{name}.json"
    output = json.dumps(experiment_results)
    logging.info(f"Writing experiment results to file at {output_path}")
    with open(output_path, "w") as f:
        f.write(output)
