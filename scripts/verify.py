import re
import secrets
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rcon

root = Path(__file__).resolve().parent.parent
run_dir = root / "run"
log_file = run_dir / "logs" / "latest.log"
boot_timeout = 900
ignored_errors = re.compile(r"watchdog|ServerHangWatchdog", re.IGNORECASE)


def prepare_run_dir():
    run_dir.mkdir(exist_ok=True)
    (run_dir / "eula.txt").write_text("eula=true\n")
    props_file = run_dir / "server.properties"
    props = {}
    if props_file.exists():
        for line in props_file.read_text().splitlines():
            if "=" in line and not line.startswith("#"):
                key, value = line.split("=", 1)
                props[key] = value
    props["enable-rcon"] = "true"
    props.setdefault("rcon.port", "25575")
    if not props.get("rcon.password"):
        props["rcon.password"] = secrets.token_urlsafe(18)
    props["online-mode"] = "false"
    props_file.write_text("".join(f"{key}={value}\n" for key, value in props.items()))
    if log_file.exists():
        log_file.unlink()


def wait_for_boot(server):
    started = time.time()
    while time.time() - started < boot_timeout:
        if server.poll() is not None:
            return False
        if log_file.exists() and "Done (" in log_file.read_text(errors="replace"):
            return True
        time.sleep(2)
    return False


def gate_clean_boot(log_text):
    error_lines = [line for line in log_text.splitlines() if "ERROR" in line and not ignored_errors.search(line)]
    return len(error_lines) == 0, error_lines[:20]


def main():
    prepare_run_dir()
    server_log = open(root / "build" / "verify_server.log", "w") if (root / "build").exists() else subprocess.DEVNULL
    server = subprocess.Popen(
        [str(root / "gradlew"), "runServer", "--args=nogui", "--console=plain"],
        cwd=root,
        stdout=server_log,
        stderr=subprocess.STDOUT,
    )
    booted = wait_for_boot(server)
    results = []
    if booted:
        log_text = log_file.read_text(errors="replace")
        passed, detail = gate_clean_boot(log_text)
        results.append(("clean boot", passed, detail))
        try:
            rcon.run("stop")
        except OSError:
            server.terminate()
    else:
        results.append(("boot", False, ["server did not reach done, see build/verify_server.log"]))
        server.terminate()
    try:
        server.wait(timeout=120)
    except subprocess.TimeoutExpired:
        server.kill()
    failed = False
    for name, passed, detail in results:
        print(f"{'pass' if passed else 'FAIL'}  {name}")
        for line in detail:
            print(f"      {line}")
        failed = failed or not passed
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
