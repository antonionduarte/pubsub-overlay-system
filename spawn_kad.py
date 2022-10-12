import shutil
import subprocess

BOOTSTRAP_PORT = 5050


def spawn_kad(port: int):
    args = [
        "podman",
        "run",
        f"--name=kad_{port}",
        "--rm",
        "-itd",
        "--network=host",
        "-v",
        "./target/asdProj.jar:/usr/local/app.jar:z",
        "-v",
        "./babel_config.properties:/usr/local/babel_config.properties:z",
        "-v",
        "./log4j2.xml:/usr/local/log4j2.xml:z",
        "--workdir=/usr/local/",
        "docker.io/amazoncorretto:19",
        "java",
        "-Xmx96M",
        "-ea",
        "-cp",
        "/usr/local/app.jar",
        "Main",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"kad_bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")
    subprocess.run(args)


def spawn_kad2(port: int):
    args = [
        shutil.which("java"),
        "-ea",
        "-XX:NativeMemoryTracking=summary",
        "-Xmx96M",
        "-cp",
        "./target/asdProj.jar",
        "Main",
        f"babel_port={port}",
        "babel_address=127.0.0.1",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"kad_bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")
    subprocess.Popen(
        args,
        start_new_session=True,
        close_fds=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def main():
    spawn_kad(BOOTSTRAP_PORT)
    for i in range(1, 100):
        print("Spawning kad ", 5050 + i)
        spawn_kad(5050 + i)


if __name__ == "__main__":
    main()
