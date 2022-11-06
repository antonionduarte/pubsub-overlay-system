import subprocess
import time
import os
import sys

BOOTSTRAP_PORT = 5000


def create_container(port: int, bi, ps):
    print(f"Creating container kad_{port}")
    cwd = os.getcwd()
    args = [
        "docker",
        "run",
        f"--name=kad_{port}",
        # "--rm",
        "-itd",
        "--network=host",
        "--security-opt",
        "label=disable",
        "-v",
        f"{cwd}/asdProj.jar:/usr/local/app.jar",
        "-v",
        f"{cwd}/babel_config.properties:/usr/local/babel_config.properties",
        "-v",
        f"{cwd}/log4j2.xml:/usr/local/log4j2.xml",
        "-v",
        f"{cwd}/analysis/metrics_kadpubsub/{bi}ms_{ps}b:/usr/local/metrics/",
        "--workdir=/usr/local/",
        "docker.io/amazoncorretto:19",
    ]
    subprocess.run(args)


def run_container(port: int, bi, ps):
    print(f"Spawning process in port {port}")
    args = [
        "docker",
        "exec",
        f"kad_{port}",
        "java",
        "-Xmx1G",
        f"-DlogFilename=log/node_{port}.log",
        "-ea",
        "-cp",
        "/usr/local/app.jar",
        "asd.KadPubSubMain",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
        f"broadcast_interval={bi}",
        f"payload_size={ps}"
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"kad_bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")
    subprocess.Popen(
        args,
        start_new_session=True,
    )


def main():
    num = int(sys.argv[1])
    bi = 100 if len(sys.argv) < 3 else sys.argv[2]
    ps = 1024 if len(sys.argv) < 4 else sys.argv[3]
    for i in range(0, num):
        create_container(BOOTSTRAP_PORT + i, bi, ps)
    run_container(BOOTSTRAP_PORT,  bi, ps)
    time.sleep(2)
    for i in range(1, num):
        # time.sleep(0.2)
        run_container(BOOTSTRAP_PORT + i, bi, ps)


if __name__ == "__main__":
    main()
