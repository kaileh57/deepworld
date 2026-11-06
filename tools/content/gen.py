import json
import shutil
from pathlib import Path

root = Path(__file__).resolve().parent
project = root.parent.parent
out_root = project / "src" / "main" / "generated_content"
assets = out_root / "assets" / "deepworld"
data = out_root / "data"

content = json.loads((root / "content.json").read_text())
extra_sources = sorted((root / "parts").glob("*.json")) if (root / "parts").exists() else []
for part_file in extra_sources:
    part = json.loads(part_file.read_text())
    for key, value in part.items():
        if isinstance(value, list):
            content.setdefault(key, []).extend(value)
        elif isinstance(value, dict):
            content.setdefault(key, {}).update(value)

lang = {}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=1) + "\n")


def item_model(item_id, model):
    write(assets / "items" / f"{item_id}.json", {"model": {"type": "minecraft:model", "model": model}})


def flat_item(item_id, texture=None):
    write(assets / "models" / "item" / f"{item_id}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture or f"deepworld:item/{item_id}"}})
    item_model(item_id, f"deepworld:item/{item_id}")


def handheld_item(item_id):
    write(assets / "models" / "item" / f"{item_id}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"deepworld:item/{item_id}"}})
    item_model(item_id, f"deepworld:item/{item_id}")


def overlay_model(name, host, overlay):
    faces = {d: {"texture": "#base", "cullface": d} for d in ("down", "up", "north", "south", "west", "east")}
    over = {d: {"texture": "#overlay", "cullface": d} for d in ("down", "up", "north", "south", "west", "east")}
    write(assets / "models" / "block" / f"{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": host, "base": host, "overlay": overlay},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": over},
        ],
    })


def loot_self(block_id):
    write(data / "deepworld" / "loot_table" / "blocks" / f"{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"deepworld:{block_id}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"deepworld:blocks/{block_id}",
    })


def loot_ore(block_id, drop, count):
    modifiers = []
    if count != [1, 1]:
        modifiers.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    modifiers.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"})
    modifiers.append({"function": "minecraft:explosion_decay"})
    write(data / "deepworld" / "loot_table" / "blocks" / f"{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"deepworld:{block_id}"},
            {"type": "minecraft:item", "functions": modifiers, "name": drop},
        ]}]}],
        "random_sequence": f"deepworld:blocks/{block_id}",
    })


def tag(registry, namespace, name, values, replace=False):
    write(data / namespace / "tags" / registry / f"{name}.json", {"replace": replace, "values": values})


def tier_tags():
    tiers = content["tiers"]
    custom_tiers = ["steel", "mantle_alloy", "cobalt_steel", "hellsteel"]
    vanilla_tiers = {"wood": "wooden", "stone": "stone", "copper": "copper", "iron": "iron", "diamond": "diamond", "netherite": "netherite", "gold": "gold"}
    needs = {tier: [] for tier in tiers}
    for block, tier in content["vanilla_needs"].items():
        needs[tier].append(block)
    for entry in content["stones"] + content["ores"]:
        needs[entry["needs"]].append(f"deepworld:{entry['id']}")
    for entry in content.get("stations", []):
        needs["stone"].append(f"deepworld:{entry['id']}")
    own_needs = {"copper": "deepworld:needs_copper_tool", "steel": "deepworld:needs_steel_tool", "mantle_alloy": "deepworld:needs_mantle_alloy_tool", "cobalt_steel": "deepworld:needs_cobalt_steel_tool", "hellsteel": "deepworld:needs_hellsteel_tool"}
    for tier, blocks in needs.items():
        if tier in ("wood", "netherite") or not blocks:
            continue
        if tier in own_needs:
            tag("block", "deepworld", own_needs[tier].split(":")[1], blocks)
        else:
            tag("block", "minecraft", f"needs_{tier}_tool", blocks)
    for tier in ["copper", "steel", "mantle_alloy", "cobalt_steel", "hellsteel"]:
        if not needs[tier]:
            tag("block", "deepworld", own_needs[tier].split(":")[1], [])
    for index, tier in enumerate(tiers):
        harder = [t for t in tiers[index + 1:] if t in own_needs]
        values = [f"#{own_needs[t]}" for t in harder]
        if tier in custom_tiers:
            base = []
            if tiers.index("diamond") > index:
                base.append("#minecraft:needs_diamond_tool")
            tag("block", "deepworld", f"incorrect_for_{tier}_tool", base + values)
        elif tier in vanilla_tiers:
            tag("block", "minecraft", f"incorrect_for_{vanilla_tiers[tier]}_tool", values)
    tag("block", "minecraft", "incorrect_for_gold_tool", [f"#{v}" for v in own_needs.values()])
    pickaxe = [f"deepworld:{e['id']}" for e in content["stones"] + content["ores"] + content.get("stations", [])]
    tag("block", "minecraft", "mineable/pickaxe", pickaxe)
    tag("block", "minecraft", "mineable/axe", [f"deepworld:{e['id']}" for e in content.get("cook_stations", [])])


def write_recipes():
    recipe_dir = data / "deepworld" / "recipe"
    for entry in content.get("smelting", []):
        name = entry["result"].split(":")[1] + "_from_" + entry["input"].split(":")[1]
        write(recipe_dir / f"{name}.json", {"type": "minecraft:smelting", "category": "misc", "cookingtime": 200, "experience": entry["xp"], "ingredient": entry["input"], "result": {"id": entry["result"]}})
        write(recipe_dir / f"{name}_blasting.json", {"type": "minecraft:blasting", "category": "misc", "cookingtime": 100, "experience": entry["xp"], "ingredient": entry["input"], "result": {"id": entry["result"]}})
    for index, entry in enumerate(content.get("shaped", [])):
        name = entry.get("name", entry["result"].split(":")[1])
        recipe = {"type": "minecraft:crafting_shaped", "category": entry.get("category", "misc"), "key": entry["key"], "pattern": entry["pattern"], "result": {"id": entry["result"]}}
        if entry.get("count", 1) != 1:
            recipe["result"]["count"] = entry["count"]
        write(recipe_dir / f"{name}.json", recipe)
    for entry in content.get("shapeless", []):
        name = entry.get("name", entry["result"].split(":")[1])
        recipe = {"type": "minecraft:crafting_shapeless", "category": entry.get("category", "misc"), "ingredients": entry["ingredients"], "result": {"id": entry["result"]}}
        if entry.get("count", 1) != 1:
            recipe["result"]["count"] = entry["count"]
        if "components" in entry:
            recipe["result"]["components"] = entry["components"]
        write(recipe_dir / f"{name}.json", recipe)
    for tool in content["tools"]:
        write(recipe_dir / f"{tool['id']}.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "key": {"#": "minecraft:stick", "X": tool["repair"]}, "pattern": ["XXX", " # ", " # "], "result": {"id": f"deepworld:{tool['id']}"}})
    patterns = {"helmet": ["XXX", "X X"], "chestplate": ["X X", "XXX", "XXX"], "leggings": ["XXX", "X X", "X X"], "boots": ["X X", "X X"]}
    for armor in content["armor"]:
        for piece, pattern in patterns.items():
            write(recipe_dir / f"{armor['set']}_{piece}.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "key": {"X": armor["repair"]}, "pattern": pattern, "result": {"id": f"deepworld:{armor['set']}_{piece}"}})
    forging_dir = data / "deepworld" / "forging"
    for entry in content.get("forging", []):
        write(forging_dir / f"{entry['id']}.json", {"station": entry["station"], "inputs": [{"item": i, "count": c} for i, c in entry["inputs"]], "result": {"item": entry["result"][0], "count": entry["result"][1]}, "time": entry["time"]})


def main():
    if out_root.exists():
        shutil.rmtree(out_root)
    for item in content["items"]:
        flat_item(item["id"])
        lang[f"item.deepworld.{item['id']}"] = item["name"]
    for stone in content["stones"]:
        write(assets / "models" / "block" / f"{stone['id']}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": stone["texture"]}})
        item_model(stone["id"], f"deepworld:block/{stone['id']}")
        lang[f"block.deepworld.{stone['id']}"] = stone["name"]
        loot_self(stone["id"])
    for ore in content["ores"]:
        overlay_model(ore["id"], ore["host"], f"deepworld:block/{ore['id']}_overlay")
        item_model(ore["id"], f"deepworld:block/{ore['id']}")
        lang[f"block.deepworld.{ore['id']}"] = ore["name"]
        loot_ore(ore["id"], ore["drop"], ore.get("count", [1, 1]))
    for station in content.get("stations", []):
        write(assets / "models" / "block" / f"{station['id']}.json", {"parent": "minecraft:block/orientable", "textures": {"top": f"deepworld:block/{station['id']}", "front": f"deepworld:block/{station['id']}_front", "side": f"deepworld:block/{station['id']}"}})
        item_model(station["id"], f"deepworld:block/{station['id']}")
        lang[f"block.deepworld.{station['id']}"] = station["name"]
        loot_self(station["id"])
    for tool in content["tools"]:
        handheld_item(tool["id"])
        lang[f"item.deepworld.{tool['id']}"] = tool["name"]
    for armor in content["armor"]:
        write(assets / "equipment" / f"{armor['set']}.json", {"layers": {"humanoid": [{"texture": f"deepworld:{armor['set']}"}], "humanoid_leggings": [{"texture": f"deepworld:{armor['set']}"}]}})
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            item_id = f"{armor['set']}_{piece}"
            flat_item(item_id)
            lang[f"item.deepworld.{item_id}"] = f"{armor['name']} {piece}"
        tag("item", "deepworld", f"repairs_{armor['set']}_armor", [armor["repair"]])
    for tool in content["tools"]:
        tag("item", "deepworld", f"{tool['tier']}_tool_materials", [tool["repair"]])
    for plant in content.get("plants", []):
        write(assets / "models" / "block" / f"{plant['id']}.json", {"parent": "minecraft:block/cross", "textures": {"cross": f"deepworld:block/{plant['id']}"}})
        flat_item(plant["id"], f"deepworld:block/{plant['id']}")
        lang[f"block.deepworld.{plant['id']}"] = plant["name"]
        write(data / "deepworld" / "loot_table" / "blocks" / f"{plant['id']}.json", {
            "type": "minecraft:block",
            "pools": [
                {"rolls": 1, "conditions": [{"condition": "minecraft:block_state_property", "block": f"deepworld:{plant['id']}", "properties": {"age": "3"}}], "entries": [{"type": "minecraft:item", "name": plant["drop"], "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": plant["count"][0], "max": plant["count"][1]}}, {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}}]}]},
                {"rolls": 1, "conditions": [{"condition": "minecraft:inverted", "term": {"condition": "minecraft:block_state_property", "block": f"deepworld:{plant['id']}", "properties": {"age": "3"}}}], "entries": [{"type": "minecraft:item", "name": plant["drop"]}]},
            ],
        })
        state = f"deepworld:{plant['id']}"
        write(data / "deepworld" / "worldgen" / "feature" / "forage" / f"{plant['id']}.json", {"type": "minecraft:simple_block", "to_place": {"id": state}})
        spread = lambda xz, y: {"type": "minecraft:offset", "x": {"type": "minecraft:trapezoid", "max": xz, "min": -xz, "plateau": 0}, "y": {"type": "minecraft:trapezoid", "max": y, "min": -y, "plateau": 0}, "z": {"type": "minecraft:trapezoid", "max": xz, "min": -xz, "plateau": 0}}
        fits = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [{"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}, {"type": "minecraft:would_survive", "state": {"id": state}}]}}
        if "deep" in plant:
            placement = [{"type": "minecraft:count", "count": plant.get("count_per_chunk", 3)}, {"type": "minecraft:in_square"},
                         {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": plant["y"][0]}, "max_inclusive": {"absolute": plant["y"][1]}}},
                         {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 24, "target_condition": {"type": "minecraft:solid"}, "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
                         {"type": "minecraft:offset", "x": 0, "y": 1, "z": 0}, {"type": "minecraft:biome"},
                         {"type": "minecraft:count", "count": plant.get("tries", 12) // 2}, spread(4, 1), fits]
        else:
            placement = [{"type": "minecraft:rarity_filter", "chance": 4}, {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"},
                         {"type": "minecraft:count", "count": plant.get("tries", 12)}, spread(6, 2), fits]
        write(data / "deepworld" / "worldgen" / "placed_feature" / "forage" / f"{plant['id']}.json", {"feature": f"deepworld:forage/{plant['id']}", "placement": placement})
    write(assets / "models" / "block" / "sprout.json", {"parent": "minecraft:block/cross", "textures": {"cross": "deepworld:block/sprout"}})
    for station in content.get("cook_stations", []):
        lang[f"block.deepworld.{station['id']}"] = station["name"]
        loot_self(station["id"])
    for dish in content.get("dishes", []):
        write(assets / "models" / "item" / "dish" / f"{dish['id']}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"deepworld:item/dish/{dish['id']}"}})
        item_model(f"dish/{dish['id']}", f"deepworld:item/dish/{dish['id']}")
    for generic in ["stew", "fry", "bake", "smoke", "pickle", "jerky", "tonic", "chill", "roast", "burnt"]:
        write(assets / "models" / "item" / "dish" / f"generic_{generic}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"deepworld:item/dish/generic_{generic}"}})
        item_model(f"dish/generic_{generic}", f"deepworld:item/dish/generic_{generic}")
    for landmark in content.get("landmarks", []):
        write(data / "deepworld" / "worldgen" / "feature" / "landmark" / f"{landmark['kind']}.json", {"type": "deepworld:landmark", "kind": landmark["kind"]})
        write(data / "deepworld" / "worldgen" / "placed_feature" / "landmark" / f"{landmark['kind']}.json", {"feature": f"deepworld:landmark/{landmark['kind']}", "placement": [
            {"type": "minecraft:rarity_filter", "chance": max(2, landmark["rarity"] // 2)}, {"type": "minecraft:count", "count": 8}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": landmark["y"][0]}, "max_inclusive": {"absolute": landmark["y"][1]}}},
            {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 32, "target_condition": {"type": "minecraft:solid"}, "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
            {"type": "minecraft:offset", "x": 0, "y": 1, "z": 0}, {"type": "minecraft:biome"}]})
    pages_by_landmark = {}
    for page in content.get("journal_pages", []):
        pages_by_landmark.setdefault(page["landmark"], []).append(page)
    for landmark in content.get("landmarks", []):
        entries = []
        for page in pages_by_landmark.get(landmark["kind"], []):
            entries.append({"type": "minecraft:item", "name": "minecraft:written_book", "weight": 1, "functions": [
                {"function": "minecraft:set_book_cover", "title": {"raw": f"deepline journal, page {page['page']}"}, "author": "deepline transit co.", "generation": 2},
                {"function": "minecraft:set_written_book_pages", "pages": [{"raw": page["text"]}], "mode": "replace_all"},
            ]})
        pools = []
        if entries:
            pools.append({"rolls": 1, "entries": entries})
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": "deepworld:cooking_notes", "functions": [{"function": "minecraft:set_custom_data", "tag": {"deepworld": {"tier": landmark.get("note_tier", 2)}}}]}]})
        pools.append({"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": landmark.get("loot", [
            {"type": "minecraft:item", "name": "minecraft:torch", "weight": 10, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 10}}]},
            {"type": "minecraft:item", "name": "deepworld:salt", "weight": 8, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
            {"type": "minecraft:item", "name": "minecraft:iron_ingot", "weight": 6, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
            {"type": "minecraft:item", "name": "deepworld:steel_ingot", "weight": 3},
            {"type": "minecraft:item", "name": "minecraft:map", "weight": 3},
            {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 1},
        ])})
        write(data / "deepworld" / "loot_table" / "landmarks" / f"{landmark['kind']}.json", {"type": "minecraft:chest", "pools": pools})
    write(data / "deepworld" / "loot_table" / "archaeology" / "fossil_dig.json", {"type": "minecraft:archaeology", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": "deepworld:fossil_marrow", "weight": 6},
        {"type": "minecraft:item", "name": "minecraft:bone", "weight": 6},
        {"type": "minecraft:item", "name": "minecraft:archer_pottery_sherd", "weight": 1},
        {"type": "minecraft:item", "name": "minecraft:skull_pottery_sherd", "weight": 1},
        {"type": "minecraft:item", "name": "minecraft:iron_pickaxe", "weight": 2},
        {"type": "minecraft:item", "name": "deepworld:rock_salt", "weight": 4},
    ]}]})
    lang.update(content.get("lang", {}))
    tier_tags()
    write_recipes()
    write(assets / "lang" / "en_us.json", dict(sorted(lang.items())))
    write(out_root / "deepworld_content.json", content)
    print(f"content: {len(lang)} names")
    import subprocess, sys
    subprocess.run([sys.executable, str(root / "trades.py")], check=True)


if __name__ == "__main__":
    main()
