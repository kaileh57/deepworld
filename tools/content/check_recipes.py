import json
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
content = json.loads((root / "src/main/generated_content/deepworld_content.json").read_text())
id_pattern = re.compile(r'"(deepworld:[a-z0-9_/]+)"')
java_pattern = re.compile(r'(?:Content\.item|Content\.items\.get|item)\("([a-z0-9_]+)"\)')


def ids_in(path):
    found = set()
    for file in path.rglob("*.json"):
        found.update(id_pattern.findall(file.read_text()))
    return found


def seeds():
    out = set()
    data = root / "src/main/generated_content/data"
    for folder in data.glob("*/loot_table"):
        out |= ids_in(folder)
    for folder in data.glob("*/villager_trade"):
        out |= ids_in(folder)
    for folder in (root / "src/main/generated/data").glob("*/loot_table"):
        out |= ids_in(folder)
    for file in (root / "src/main/java").rglob("*.java"):
        text = file.read_text()
        out.update(id_pattern.findall(text))
        out.update("deepworld:" + name for name in java_pattern.findall(text))
    for ore in content["ores"]:
        out.add(ore["drop"])
    for plant in content["plants"]:
        out.add(plant["drop"])
    for entry in content.get("obtainable", []):
        out.add(entry)
    return out


def ingredient_ids(value):
    if isinstance(value, str):
        return set() if value.startswith("#") else {value}
    if isinstance(value, list):
        out = set()
        for entry in value:
            out |= ingredient_ids(entry)
        return out
    if isinstance(value, dict):
        out = set()
        for key in ("item", "id", "items"):
            if key in value:
                out |= ingredient_ids(value[key])
        return out
    return set()


def recipes():
    out = []
    for file in (root / "src/main/generated_content/data/deepworld/recipe").glob("*.json"):
        r = json.loads(file.read_text())
        inputs = set()
        if "key" in r:
            for value in r["key"].values():
                inputs |= ingredient_ids(value)
        for key in ("ingredients", "ingredient", "base", "addition", "template"):
            if key in r:
                inputs |= ingredient_ids(r[key])
        result = r.get("result")
        result_id = result if isinstance(result, str) else result.get("id")
        out.append((inputs, [result_id], None))
    for r in content["forging"]:
        out.append(({i[0] for i in r["inputs"]}, [r["result"][0]], "forge:" + r["station"]))
    for r in content["processing"]:
        out.append(({r["input"]}, [r["output"]], "station:" + r["station"]))
    for d in content["dishes"]:
        results = ["deepworld:dish/" + d["id"], "drink:" + d["id"], "deepworld:" + d["id"]]
        if d.get("result"):
            results.append(d["result"])
        out.append((set(d["ingredients"]), results, "station:" + d["station"]))
    for t in content["tools"]:
        out.append(({t["repair"], "minecraft:stick"}, ["deepworld:" + t["id"]], None))
    return out


def station_items():
    by_station = {}
    for s in content["cook_stations"]:
        by_station.setdefault(s["station"], []).append("deepworld:" + s["id"])
    for s in content["stations"]:
        by_station.setdefault(s["id"], []).append("deepworld:" + s["id"])
    for item in content["items"]:
        by_station.setdefault(item["id"], []).append("deepworld:" + item["id"])
    return by_station


def main():
    have = seeds()
    stations = station_items()
    pending = recipes()

    def ok(item):
        return item.startswith("minecraft:") or item == "#meat" or item in have

    changed = True
    while changed:
        changed = False
        for inputs, results, needs in pending:
            if all(ok(i) for i in inputs) and (needs is None or needs.startswith("forge:") and any(ok(s) for s in stations.get(needs[6:], [])) or needs.startswith("station:") and any(ok(s) for s in stations.get(needs[8:], []))):
                for result in results:
                    if not ok(result):
                        have.add(result)
                        changed = True
    bad = []
    for d in content["dishes"]:
        if not ok("deepworld:" + d["id"]):
            missing = [i for i in d["ingredients"] if not ok(i)]
            if not any(ok(s) for s in stations.get(d["station"], [])):
                missing.append("station " + d["station"])
            bad.append(d["id"] + ": " + ", ".join(missing))
    for item in content["items"]:
        if not ok("deepworld:" + item["id"]):
            bad.append("item " + item["id"])
    for line in bad:
        print(line)
    print(f"recipes: {len(content['dishes'])} dishes, {len(bad)} unreachable")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
