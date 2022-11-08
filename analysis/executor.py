import argparse
import logging
import lib.experiment as experiment

DEFAULT_EXPERIMENTS_PATH = "experiments.yaml"


def entrypoint_run(args):
    experiments = experiment.load_experiments(DEFAULT_EXPERIMENTS_PATH)
    exp = experiments[args.experiment]
    if experiment is None:
        print(experiments)
        raise ValueError(f"Experiment {args.experiment} not found")
    experiment.run_experiment(args.experiment, exp, args.jarpath)


if __name__ == "__main__":
    logging.basicConfig(format="%(levelname)s: %(message)s", level=logging.DEBUG)

    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(required=True)

    # Subcommand package
    package = subparsers.add_parser(
        "run", help="Run the experiment and generate the results"
    )
    package.add_argument("experiment", help="The experiment to run")
    package.add_argument(
        "--jarpath", default="../target/asdProj.jar", help="The path to the jar file"
    )
    package.add_argument("--output", help="The output file with the results")
    package.set_defaults(func=entrypoint_run)

    args = parser.parse_args()
    args.func(args)
