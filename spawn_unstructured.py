import shutil
import subprocess
import time
import os
import sys

BOOTSTRAP_PORT = 5000


def spawn_kad_java_native(port: int):
    cwd = os.getcwd()
    args = [
        shutil.which("java"),
        "-ea",
        "-XX:NativeMemoryTracking=summary",
        "-Xmx96M",
        f"-DlogFilename=log/node_{port}.log",
        "-cp",
        f"{cwd}/target/asdProj.jar",
        "asd.UnstructuredMain",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"kad_bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")
    subprocess.Popen(
        args,
        start_new_session=True,
    )


def create_container(port: int):
    print(f"Creating container hypar_{port}")
    cwd = os.getcwd()
    args = [
        "docker",
        "run",
        f"--name=hypar_{port}",
        # "--rm",
        "-itd",
        "--network=host",
        "--security-opt",
        "label=disable",
        "-v",
        f"{cwd}/target/asdProj.jar:/usr/local/app.jar",
        "-v",
        f"{cwd}/babel_config.properties:/usr/local/babel_config.properties",
        "-v",
        f"{cwd}/log4j2.xml:/usr/local/log4j2.xml",
        "-v",
        f"{cwd}/analysis/metrics:/usr/local/metrics/",
        "--workdir=/usr/local/",
        "docker.io/amazoncorretto:19",
    ]
    subprocess.run(args)


def run_container(port: int, main_class):
    print(f"Spawning process in port {port}")
    args = [
        "docker",
        "exec",
        f"hypar_{port}",
        "java",
        "-Xmx96M",
        f"-DlogFilename=log/node_{port}.log",
        "-ea",
        "-cp",
        "/usr/local/app.jar",
        f"asd.{main_class}",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"hypar_bootstrap=127.0.0.1:{port - 1}")
    subprocess.Popen(
        args,
        start_new_session=True,
    )


def main():
    num = int(sys.argv[1])
    main_class = sys.argv[2]
    for i in range(0, num):
        create_container(BOOTSTRAP_PORT + i)
    run_container(BOOTSTRAP_PORT, main_class)
    time.sleep(2)
    for i in range(1, num):
        time.sleep(0.2)
        run_container(BOOTSTRAP_PORT + i, main_class)


if __name__ == "__main__":
    main()
