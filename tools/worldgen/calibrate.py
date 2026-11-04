import json
import subprocess
import sys
import time
from pathlib import Path

root = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(root / "scripts"))
import rcon

spots = [(0, 0), (60, -40), (-70, 50), (120, 120), (-150, -90), (200, -10), (30, 180), (-220, 160)]


def wait_for(text, log, timeout=600):
    started = time.time()
    while time.time() - started < timeout:
        if log.exists() and text in log.read_text(errors="replace"):
            return True
        time.sleep(2)
    return False


def main():
    subprocess.run([sys.executable, str(root / "tools" / "worldgen" / "gen.py")], check=True)
    subprocess.run(["rm", "-rf", str(root / "run" / "world")])
    log = root / "build" / "calibrate.log"
    if log.exists():
        log.unlink()
    server = subprocess.Popen([str(root / "gradlew"), "runServer", "--args=nogui", "--console=plain"], cwd=root, stdout=open(log, "w"), stderr=subprocess.STDOUT)
    if not wait_for("Done (", log):
        print(log.read_text()[-3000:])
        server.kill()
        return
    totals = {}
    for chunk_x, chunk_z in spots:
        rcon.run(f"dw survey {chunk_x} {chunk_z} 1")
        report = json.loads((root / "run" / "deepworld_survey.json").read_text())
        for name, layer in report.items():
            entry = totals.setdefault(name, {"air": [], "water": [], "lava": [], "biomes": set(), "ores": {}})
            entry["air"].append(layer["air"])
            entry["water"].append(layer["water"])
            entry["lava"].append(layer["lava"])
            entry["biomes"].update(layer["biomes"])
            for ore, count in layer["ores"].items():
                entry["ores"][ore] = entry["ores"].get(ore, 0) + count
    print(rcon.run("dw fortress"))
    rcon.run("stop")
    server.wait(timeout=120)
    (root / "build" / "calibrate.json").write_text(json.dumps({k: {**v, "biomes": sorted(v["biomes"])} for k, v in totals.items()}, indent=1))
    for name, entry in totals.items():
        air = sum(entry["air"]) / len(entry["air"])
        water = sum(entry["water"]) / len(entry["water"])
        lava = sum(entry["lava"]) / len(entry["lava"])
        print(f"{name:22s} air {air:.3f} water {water:.4f} lava {lava:.3f} biomes {len(entry['biomes'])} {','.join(b.split(':')[1] for b in sorted(entry['biomes']))}")


if __name__ == "__main__":
    main()
