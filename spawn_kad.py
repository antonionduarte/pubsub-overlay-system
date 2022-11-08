import shutil
import subprocess
import time

BOOTSTRAP_PORT = 5050


def spawn_kad_java_podman(port: int):
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
        "-v",
        "./metrics:/usr/local/metrics:z",
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


def spawn_kad_java_native(port: int):
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


def spawn_kad_rust_podman(port: int):
    args = [
        "podman",
        "run",
        "--rm",
        "-itd",
        "-v",
        "/home/diogo464/.cargo-target/debug/asd:/usr/local/asd:z",
        "--network=host",
        f"--name=kad_{port}",
        "-e",
        "RUST_BACKTRACE=1",
        "-e",
        "RUST_LOG=error",
        # "RUST_LOG=babel=trace,asd=trace",
        "fedora:latest",
        "/usr/local/asd",
        f"--port={port}",
    ]
    if port != BOOTSTRAP_PORT:
        args.append(f"--kad-bootstrap=127.0.0.1:{BOOTSTRAP_PORT}")
    subprocess.run(args)


def spawn_kad_rust_native(port: int):
    args = [
        "/home/diogo464/.cargo-target/debug/asd",
        "--port",
        str(port),
    ]
    if port != BOOTSTRAP_PORT:
        args.extend(["--kad-bootstrap", f"127.0.0.1:{BOOTSTRAP_PORT}"])
    subprocess.Popen(
        args,
        start_new_session=True,
        close_fds=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        stdin=subprocess.DEVNULL,
    )


def main():
    # spawn_kad_rust_podman(BOOTSTRAP_PORT)
    spawn_kad_java_podman(BOOTSTRAP_PORT)
    time.sleep(2)
    for i in range(1, 10):
        print("Spawning kad ", 5050 + i)
        spawn_kad_java_podman(5050 + i)
        # if i < 10:
        #    spawn_kad_rust_podman(5050 + i)
        # else:
        #    spawn_kad_rust_native(5050 + i)


if __name__ == "__main__":
    main()
