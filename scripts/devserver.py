import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rcon

root = Path(__file__).resolve().parent.parent
log_file = root / "run" / "logs" / "latest.log"


def start():
    if log_file.exists():
        log_file.unlink()
    (root / "build").mkdir(exist_ok=True)
    subprocess.Popen([str(root / "gradlew"), "runServer", "--args=nogui", "--console=plain"], cwd=root, stdout=open(root / "build" / "devserver.log", "w"), stderr=subprocess.STDOUT, start_new_session=True)
    started = time.time()
    while time.time() - started < 900:
        if log_file.exists() and "Done (" in log_file.read_text(errors="replace"):
            print("up")
            return
        time.sleep(2)
    print("timeout")
    sys.exit(1)


def stop():
    try:
        rcon.run("stop")
    except OSError:
        pass
    started = time.time()
    while time.time() - started < 120:
        if subprocess.run(["pgrep", "-f", "net.fabricmc.devlaunchinjector"], capture_output=True).returncode != 0:
            print("down")
            return
        time.sleep(1)


if __name__ == "__main__":
    {"start": start, "stop": stop}[sys.argv[1]]()
