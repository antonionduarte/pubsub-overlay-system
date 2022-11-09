import argparse
import logging
import lib.experiment as experiment
import re

DEFAULT_EXPERIMENTS_PATH = "experiments.yaml"


def entrypoint_run(args):
    experiments = experiment.load_experiments(DEFAULT_EXPERIMENTS_PATH)
    experiment_names = list(experiments.keys())
    matched_names = [
        name for name in experiment_names if re.match(args.experiment, name)
    ]
    if len(matched_names) == 0:
        raise ValueError(f"No experiments matched {args.experiment}")
    logging.info(f"Running experiments: {matched_names}")
    for name in matched_names:
        experiment.run_experiment(name, experiments[name], args.jarpath)


if __name__ == "__main__":
    logging.basicConfig(format="%(levelname)s: %(message)s", level=logging.DEBUG)

    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(required=True)

    # Subcommand package
    package = subparsers.add_parser(
        "run", help="Run the experiment and generate the results"
    )
    package.add_argument(
        "experiment",
        help="The experiment to run, this can be regex to match on experiment names",
    )
    package.add_argument(
        "--jarpath", default="../target/asdProj.jar", help="The path to the jar file"
    )
    package.set_defaults(func=entrypoint_run)

    args = parser.parse_args()
    args.func(args)
