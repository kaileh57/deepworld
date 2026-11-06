import argparse
import json
import re
import secrets
import shutil
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
survey_spots = [(0, 0), (60, -40), (-70, 50), (120, 120), (-150, -90), (200, -10), (30, 180), (-220, 160)]
open_layers = {"molten echoes": (0.15, 0.45), "nether stratum": (0.15, 0.5)}
wet_layers = {"deepslate boundary": 0.03}


def read_props(props_file):
    props = {}
    if props_file.exists():
        for line in props_file.read_text().splitlines():
            if "=" in line and not line.startswith("#"):
                key, value = line.split("=", 1)
                props[key] = value
    return props


def prepare_run_dir(fresh):
    run_dir.mkdir(exist_ok=True)
    (run_dir / "eula.txt").write_text("eula=true\n")
    props_file = run_dir / "server.properties"
    props = read_props(props_file)
    props["enable-rcon"] = "true"
    props.setdefault("rcon.port", "25575")
    if not props.get("rcon.password"):
        props["rcon.password"] = secrets.token_urlsafe(18)
    props["online-mode"] = "false"
    props["level-seed"] = "deepworld"
    props_file.write_text("".join(f"{key}={value}\n" for key, value in props.items()))
    if fresh:
        shutil.rmtree(run_dir / "world", ignore_errors=True)
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


def run_survey():
    totals = {}
    for chunk_x, chunk_z in survey_spots:
        rcon.run(f"dw survey {chunk_x} {chunk_z} 1")
        report = json.loads((run_dir / "deepworld_survey.json").read_text())
        for name, layer in report.items():
            entry = totals.setdefault(name, {"blocks": 0, "air": 0.0, "water": 0.0, "biomes": set(), "ores": set()})
            entry["blocks"] += layer["blocks"]
            entry["air"] += layer["air"] * layer["blocks"]
            entry["water"] += layer["water"] * layer["blocks"]
            entry["biomes"].update(layer["biomes"])
            entry["ores"].update(ore for ore, count in layer["ores"].items() if count > 0)
    for entry in totals.values():
        entry["air"] /= entry["blocks"]
        entry["water"] /= entry["blocks"]
    return totals


def worldgen_gates():
    layers = json.loads((root / "src" / "main" / "generated" / "deepworld_layers.json").read_text())
    totals = run_survey()
    results = []
    air_fail, water_fail, biome_fail, ore_fail = [], [], [], []
    for layer in layers:
        name = layer["name"]
        entry = totals.get(name)
        if entry is None:
            air_fail.append(f"{name}: no data")
            continue
        low, high = open_layers.get(name, (0.05, 0.2))
        if not low <= entry["air"] <= high:
            air_fail.append(f"{name}: {entry['air']:.3f} not in {low}..{high}")
        if entry["water"] > wet_layers.get(name, 0.005):
            water_fail.append(f"{name}: {entry['water']:.4f}")
        own = [b for b in layer["biomes"] if b in entry["biomes"]]
        if len(own) < min(2, len(layer["biomes"])):
            biome_fail.append(f"{name}: found {own}")
        missing = [ore for ore in layer["ores"] if ore not in entry["ores"]]
        if missing:
            ore_fail.append(f"{name}: missing {missing}")
    results.append(("caves exist", not air_fail, air_fail))
    results.append(("caves are dry", not water_fail, water_fail))
    results.append(("layer identity", not biome_fail, biome_fail))
    results.append(("ores land", not ore_fail, ore_fail))
    rcon.run("dw landmarks")
    landmark_text = (run_dir / "deepworld_landmarks.txt").read_text() if (run_dir / "deepworld_landmarks.txt").exists() else ""
    kinds = re.findall(r"([a-z_]+)=\d+", landmark_text)
    real = [k for k in kinds if k not in ("pump", "cathedral")]
    results.append(("landmarks generate", len(real) >= 4, [landmark_text.strip(), f"{len(real)} kinds"]))
    fortress = rcon.run("dw fortress")
    match = re.search(r"fortress (-?\d+) (-?\d+) y (-?\d+)\.\.(-?\d+)", fortress)
    fortress_ok = bool(match) and int(match.group(4)) < 0 and abs(int(match.group(1))) <= 2000 and abs(int(match.group(2))) <= 2000
    results.append(("nether fortress below 0", fortress_ok, [fortress]))
    return results


def selftest_gates():
    out = run_dir / "deepworld_selftest.txt"
    if out.exists():
        out.unlink()
    rcon.run("dw selftest")
    results = []
    for text in out.read_text().splitlines() if out.exists() else []:
        if text.startswith("pass "):
            results.append(("selftest " + text[5:], True, []))
        elif text.startswith("FAIL "):
            name, _, reason = text[5:].partition(": ")
            results.append(("selftest " + name, False, [reason]))
    if not results:
        results.append(("selftest", False, ["no selftest output"]))
    return results


def recipe_gate():
    check = subprocess.run([sys.executable, str(root / "tools" / "content" / "check_recipes.py")], capture_output=True, text=True)
    lines = check.stdout.strip().splitlines()
    return ("recipes reachable", check.returncode == 0, lines[:-1] if check.returncode else [])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--worldgen", action="store_true")
    args = parser.parse_args()
    prepare_run_dir(fresh=args.worldgen)
    (root / "build").mkdir(exist_ok=True)
    server_log = open(root / "build" / "verify_server.log", "w")
    server = subprocess.Popen(
        [str(root / "gradlew"), "runServer", "--args=nogui", "--console=plain"],
        cwd=root,
        stdout=server_log,
        stderr=subprocess.STDOUT,
    )
    booted = wait_for_boot(server)
    results = [recipe_gate()]
    if booted:
        if args.worldgen:
            results.extend(worldgen_gates())
        results.extend(selftest_gates())
        log_text = log_file.read_text(errors="replace")
        passed, detail = gate_clean_boot(log_text)
        results.insert(1, ("clean boot", passed, detail))
        try:
            rcon.run("stop")
        except OSError:
            server.terminate()
    else:
        results.append(("boot", False, ["server did not reach done, see build/verify_server.log"]))
        server.terminate()
    try:
        server.wait(timeout=180)
    except subprocess.TimeoutExpired:
        server.kill()
    failed = False
    for name, passed, detail in results:
        print(f"{'pass' if passed else 'FAIL'}  {name}")
        for line in detail if not passed else []:
            print(f"      {line}")
        failed = failed or not passed
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
