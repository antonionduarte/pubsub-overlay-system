import subprocess
import time
import os

BOOTSTRAP_PORT = 5050

def spawn_hypv_java_docker(port: int):
    cwd = os.getcwd()
    args = [
        "docker",
        "run",
        f"--name=hypar_{port}",
        # "--rm",
        "-itd",
        "--network=host",
        "-v",
        f"{cwd}/target/asdProj.jar:/usr/local/app.jar",
        "-v",
        f"{cwd}/babel_config.properties:/usr/local/babel_config.properties",
        "-v",
        f"{cwd}/log4j2.xml:/usr/local/log4j2.xml",
        "-v",
        f"{cwd}/metrics:/usr/local/metrics/",
        "--workdir=/usr/local/",
        "docker.io/amazoncorretto:19",

        "java",
        "-Xmx96M",
        f"-DlogFilename=log/node_{port}.log" "-ea",
        "-cp",
        "/usr/local/app.jar",
        "asd.HyparviewMain",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"hypar_bootstrap=127.0.0.1:{port - 1}")
    subprocess.run(args)

    if port != BOOTSTRAP_PORT:
        args.append(f"hypar_bootstrap=127.0.0.1:{port - 1}")
    subprocess.Popen(
        args,
        start_new_session=True,
        close_fds=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def main():
    spawn_hypv_java_docker(BOOTSTRAP_PORT)
    time.sleep(2)
    for i in range(1, 50):
        print("Spawning hyparview ", BOOTSTRAP_PORT + i)
        spawn_hypv_java_docker(BOOTSTRAP_PORT + i)
        time.sleep(0.2)
        # if i < 10:
        #    spawn_kad_rust_podman(5050 + i)
        # else:
        #    spawn_kad_rust_native(5050 + i)


if __name__ == "__main__":
    main()

