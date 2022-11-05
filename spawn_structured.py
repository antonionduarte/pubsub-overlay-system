import shutil
import subprocess
import time
import os
import sys

BOOTSTRAP_PORT = 5000


def spawn_kad_java_docker(port: int):
    cwd = os.getcwd()
    args = [
        "docker",
        "run",
        f"--name=kad_{port}",
        "--rm",
        "-itd",
        "--network=host",
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
        "java",
        "-Xmx96M",
        f"-DlogFilename=log/node_{port}.log" "-ea",
        "-cp",
        "/usr/local/app.jar",
        "asd.StructuredMain",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"kad_bootstrap=127.0.0.1:{port - 1}")
    subprocess.run(args)


def spawn_kad_java_podman(port: int):
    args = [
        "podman",
        "run",
        f"--name=kad_{port}",
        # "--rm",
        "-itd",
        "--network=host",
        "-v",
        "./target/asdProj.jar:/usr/local/app.jar:z",
        "-v",
        "./babel_config.properties:/usr/local/babel_config.properties:z",
        "-v",
        "./log4j2.xml:/usr/local/log4j2.xml:z",
        "-v",
        "./log/:/usr/local/log/:z",
        "--workdir=/usr/local/",
        "docker.io/amazoncorretto:19",
        "java",
        "-Xmx96M",
        f"-DlogFilename=log/node_{port}.log" "-ea",
        "-cp",
        "/usr/local/app.jar",
        "asd.StructuredMain",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"kad_bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")
    subprocess.run(args)


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
        "asd.StructuredMain",
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
    print(f"Creating container kad_{port}")
    cwd = os.getcwd()
    args = [
        "docker",
        "run",
        f"--name=kad_{port}",
        # "--rm",
        "-itd",
        "--network=host",
        "-v",
        f"{cwd}/asdProj.jar:/usr/local/app.jar",
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


def run_container(port: int):
    print(f"Spawning process in port {port}")
    args = [
        "docker",
        "exec",
        f"kad_{port}",
        "java",
        "-Xmx96M",
        f"-DlogFilename=log/node_{port}.log",
        "-ea",
        "-cp",
        "/usr/local/app.jar",
        "asd.StructuredMain",
        f"babel_port={port}",
        "babel_address=127.0.0.1"]
    if port != BOOTSTRAP_PORT:
        args.append(f"kad_bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")
    subprocess.Popen(
        args,
        start_new_session=True,
    )


def main():
    num = int(sys.argv[1])
    for i in range(0, num):
        create_container(BOOTSTRAP_PORT + i)
    run_container(BOOTSTRAP_PORT)
    time.sleep(2)
    for i in range(1, num):
        #time.sleep(0.2)
        run_container(BOOTSTRAP_PORT + i)

    # for i in range(1, int(sys.argv[1])):
    #     print("Spawning kad ", BOOTSTRAP_PORT + i)
    #     # spawn_kad_java_docker(BOOTSTRAP_PORT + i)
    #     spawn_kad_java_native(BOOTSTRAP_PORT + i)
    # if i < 10:
    #    spawn_kad_rust_podman(5050 + i)
    # else:
    #    spawn_kad_rust_native(5050 + i)


if __name__ == "__main__":
    main()
