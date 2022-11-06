import subprocess
import time

types = ["structured", "kadpubsub"]  # , "unstructured"]
broadcast_intervals = [100, 400]
payload_sizes = [1024, 256]
sleep = 10 + 10 + 30 + 60 + 5  # pp + prepare + run + cooldown + extra
num_processes = 100

if __name__ == '__main__':
    for typ in types:
        for bi in broadcast_intervals:
            for ps in payload_sizes:
                cmd = f"python3 spawn_{typ}.py {num_processes} {bi} {ps}"
                subprocess.run(cmd, shell=True)
                time.sleep(sleep)
                cmd = f"docker kill $(docker ps -aq); docker rm $(docker ps -aq)"
                subprocess.run(cmd, shell=True)

    for typ in types:
        cmd = f"python3 analysis/scripts/metrics.py {typ}"
        subprocess.run(cmd, shell=True)
