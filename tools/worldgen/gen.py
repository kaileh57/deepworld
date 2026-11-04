import copy
import glob
import json
import shutil
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent.parent
out_root = root / "src" / "main" / "generated"
data_root = out_root / "data"

surface_shift = 1137
nether_shift = -256
copper_vein_shift = 1010
iron_vein_shift = 960
world_min_y = -256
world_height = 1792

layers = json.loads((Path(__file__).parent / "layers.json").read_text())


def find_vanilla_jar():
    matches = [p for p in glob.glob(str(root / ".gradle" / "loom-cache" / "minecraftMaven" / "**" / "minecraft-merged-*.jar"), recursive=True) if "sources" not in p]
    if not matches:
        raise SystemExit("run ./gradlew build once so loom caches the minecraft jar")
    return matches[0]


class Vanilla:
    def __init__(self, jar_path):
        self.zip = zipfile.ZipFile(jar_path)
        self.names = set(self.zip.namelist())

    def path(self, registry, ident):
        namespace, name = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
        return f"data/{namespace}/{registry}/{name}.json"

    def has(self, registry, ident):
        return self.path(registry, ident) in self.names

    def load(self, registry, ident):
        return json.loads(self.zip.read(self.path(registry, ident)))


vanilla = Vanilla(find_vanilla_jar())
written = set()


def write(registry, ident, value):
    namespace, name = ident.split(":", 1)
    target = data_root / namespace / registry / f"{name}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=1) + "\n")
    written.add((registry, ident))


def y_dependent(node, registry, seen=None):
    seen = seen if seen is not None else {}
    if isinstance(node, str):
        if node == "minecraft:y":
            return True
        if vanilla.has(registry, node):
            if node not in seen:
                seen[node] = False
                seen[node] = y_dependent(vanilla.load(registry, node), registry, seen)
            return seen[node]
        if registry == "worldgen/material_rule" and vanilla.has("worldgen/density_function", node):
            return y_dependent(node, "worldgen/density_function", seen)
        return False
    if isinstance(node, dict):
        if node.get("type") in ("minecraft:gradient",) and node.get("axis") == "y":
            return True
        if node.get("type") in ("minecraft:find_top_surface", "minecraft:y_above", "minecraft:vertical_gradient"):
            return True
        if "absolute" in node or "above_bottom" in node or "below_top" in node:
            return True
        return any(y_dependent(v, registry, seen) for v in node.values())
    if isinstance(node, list):
        return any(y_dependent(v, registry, seen) for v in node)
    return False


class Shifter:
    def __init__(self, prefix, shift, drop=()):
        self.prefix = prefix
        self.shift = shift
        self.drop = set(drop)
        self.cache = {}

    def ref(self, ident, registry):
        if ident == "minecraft:y" or not vanilla.has(registry, ident):
            return ident
        if not y_dependent(ident, registry):
            return ident
        name = ident.split(":", 1)[1]
        new_id = f"deepworld:shifted/{self.prefix}/{name}"
        key = (registry, ident)
        if key not in self.cache:
            self.cache[key] = new_id
            write(registry, new_id, self.tree(vanilla.load(registry, ident), registry))
        return new_id

    def anchor(self, node):
        node = dict(node)
        if "absolute" in node:
            node["absolute"] = node["absolute"] + self.shift
        return node

    def tree(self, node, registry):
        if isinstance(node, str):
            if registry == "worldgen/material_rule":
                if vanilla.has("worldgen/material_rule", node):
                    return self.ref(node, "worldgen/material_rule")
                if vanilla.has("worldgen/density_function", node):
                    return self.ref(node, "worldgen/density_function")
                return node
            return self.ref(node, registry)
        if isinstance(node, list):
            out = []
            for item in node:
                if isinstance(item, str) and item in self.drop:
                    continue
                out.append(self.tree(item, registry))
            return out
        if not isinstance(node, dict):
            return node
        node_type = node.get("type")
        if node_type == "minecraft:gradient" and node.get("axis") == "y":
            node = dict(node)
            node["from_coordinate"] += self.shift
            node["to_coordinate"] += self.shift
            return node
        if node_type == "minecraft:range_choice" and node.get("input") == "minecraft:y":
            node = {k: self.tree(v, registry) for k, v in node.items()}
            node["min_inclusive"] += self.shift
            node["max_exclusive"] += self.shift
            return node
        if node_type == "minecraft:find_top_surface":
            node = {k: self.tree(v, registry) for k, v in node.items()}
            node["lower_bound"] += self.shift
            bound = node["upper_bound"]
            if isinstance(bound, dict) and bound.get("type") == "minecraft:clamp":
                bound["max"] += self.shift
                bound["min"] += self.shift
                if isinstance(bound["input"], dict) and isinstance(bound["input"].get("right"), (int, float)):
                    bound["input"]["right"] += self.shift
            return node
        if "absolute" in node and len(node) == 1:
            return self.anchor(node)
        out = {}
        for key, value in node.items():
            if registry == "worldgen/material_rule" and key == "density":
                out[key] = Shifter.tree(self, value, "worldgen/density_function") if not isinstance(value, str) else self.ref(value, "worldgen/density_function")
            elif registry == "worldgen/material_rule" and key in ("filler_gap", "richness"):
                out[key] = self.ref(value, "worldgen/density_function") if isinstance(value, str) else self.tree(value, "worldgen/density_function")
            else:
                out[key] = self.tree(value, registry)
        return out


surface = Shifter("surface", surface_shift)
nether = Shifter("nether", nether_shift, drop={"minecraft:bedrock_roof"})
copper_vein = Shifter("copper_vein", copper_vein_shift)
iron_vein = Shifter("iron_vein", iron_vein_shift)


def df(ident):
    return surface.ref(ident, "worldgen/density_function")


def noise(name, xz, y):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def fmin(a, b):
    return {"type": "minecraft:min", "left": a, "right": b}


def fmax(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}


def fabs(a):
    return {"type": "minecraft:abs", "input": a}


def y_between(lo, hi, inside, outside):
    return {"type": "minecraft:range_choice", "input": "minecraft:y", "min_inclusive": float(lo), "max_exclusive": float(hi), "when_in_range": inside, "when_out_of_range": outside}


def y_dist(center, half):
    return mul(fabs(add("minecraft:y", -float(center))), 1.0 / half)


def cheese(noise_id, xz, y, threshold):
    return add(threshold, mul(noise(noise_id, xz, y), -1.0))


def tunnel(noise_a, noise_b, xz, y, width):
    return add(add(fabs(noise(noise_a, xz, y)), fabs(noise(noise_b, xz, y))), -width)


def shell(lo, hi, thickness):
    mid = (lo + hi) / 2
    half = (hi - lo) / 2
    return add(mul(add(y_dist(mid, half), -1.0), half / thickness), 1.0)


def cave_shape(spec):
    kind = spec["kind"]
    p = spec.get("params", {})
    if kind == "classic":
        return fmin(cheese("deepworld:cave_a", p.get("xz", 1.0), p.get("y", 0.66), p.get("t", 0.55)), tunnel("deepworld:cave_b", "deepworld:cave_c", 1.6, 1.6, p.get("w", 0.08)))
    if kind == "seams":
        return fmin(cheese("deepworld:cave_b", 0.5, 4.0, p.get("t", 0.42)), tunnel("deepworld:cave_a", "deepworld:cave_c", 1.2, 3.0, p.get("w", 0.1)))
    if kind == "halls":
        return fmin(cheese("deepworld:cave_a", 0.6, 0.22, p.get("t", 0.4)), tunnel("deepworld:cave_b", "deepworld:cave_c", 1.4, 1.4, p.get("w", 0.08)))
    if kind == "chimneys":
        return fmin(tunnel("deepworld:cave_b", "deepworld:cave_c", 3.0, 0.08, p.get("w", 0.11)), cheese("deepworld:cave_a", 1.0, 1.0, p.get("t", 0.4)))
    if kind == "lava_sea":
        roof = add(add(y_dist(p["center"], p["half"]), -1.0), mul(noise("deepworld:cave_a", 0.8, 0.0), 0.35))
        pillars = add(mul(fabs(noise("deepworld:cave_c", 3.0, 0.2)), -12.0), 0.9)
        sea = fmax(roof, pillars)
        return fmin(sea, cheese("deepworld:cave_b", 0.9, 0.8, p.get("t", 0.4)))
    if kind == "noodle":
        return fmin(tunnel("deepworld:cave_a", "deepworld:cave_b", 2.6, 2.6, p.get("w", 0.06)), cheese("deepworld:cave_c", 0.8, 0.8, p.get("t", 0.42)))
    if kind == "rivers":
        channel = fmax(add(fabs(noise("deepworld:river", 1.0, 0.0)), -p.get("river_w", 0.1)), add(y_dist(p["river_y"], 8.0), add(mul(noise("deepworld:cave_c", 1.0, 0.0), 0.3), -1.0)))
        upper = y_between(p["cavern_lo"], p["cavern_hi"], cheese("deepworld:cave_a", 0.8, 0.6, p.get("t", 0.3)), 1.0)
        return fmin(channel, upper)
    if kind == "nether_halls":
        return cheese("deepworld:cave_a", 0.7, 0.5, p.get("t", 0.3))
    raise ValueError(kind)


def layer_density(layer):
    lo, hi = layer.get("cave_y", layer["y"])
    shape = cave_shape(layer["caves"])
    edge = add(shell(lo, hi, 6.0), mul(noise("deepworld:cave_c", 2.0, 2.0), -0.9))
    return fmax(shape, edge)


def deep_caves():
    result = 1.0
    for layer in layers:
        lo, hi = layer.get("cave_y", layer["y"])
        result = y_between(lo, hi, layer_density(layer), result)
    return result


def nether_final():
    final = copy.deepcopy(vanilla.load("worldgen/noise_settings", "minecraft:nether")["noise_router"]["final_density"])
    return nether.tree(final, "worldgen/density_function")


def column_final():
    shifted_surface = y_between(1060, 4096, df("minecraft:overworld/final_density"), 1.0)
    nether_part = y_between(-256, -120, nether_final(), 1.0)
    deep = {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 4, "input": deep_caves()}
    return fmin(fmin(shifted_surface, deep), nether_part)


def depth_points():
    points = []
    for index, boundary in enumerate([1100, 1000, 900, 800, 700, 600, 450, 300, 150, 0, -256]):
        points.append({"location": float(boundary), "value": 3.0 + index, "derivative": 0.0})
    return sorted(points, key=lambda p: p["location"])


def column_depth():
    deep = add({"type": "minecraft:spline", "spline": {"coordinate": "minecraft:y", "points": depth_points()}}, mul(noise("deepworld:layer_wobble", 1.0, 0.0), 0.12))
    return y_between(1100, 4096, df("minecraft:overworld/depth"), deep)


def write_noises():
    noises = {
        "cave_a": {"base_octave": -7, "octave_count": 3, "amplitude_modifiers": [1.0, 0.5, 0.25], "base_amplitude": 1.0},
        "cave_b": {"base_octave": -7, "octave_count": 3, "amplitude_modifiers": [1.0, 0.5, 0.25], "base_amplitude": 1.0},
        "cave_c": {"base_octave": -6, "octave_count": 2, "amplitude_modifiers": [1.0, 0.5], "base_amplitude": 1.0},
        "river": {"base_octave": -8, "octave_count": 2, "amplitude_modifiers": [1.0, 0.5], "base_amplitude": 1.0},
        "layer_wobble": {"base_octave": -7, "octave_count": 2, "amplitude_modifiers": [1.0, 1.0], "base_amplitude": 1.0},
        "stone_band": {"base_octave": -5, "octave_count": 2, "amplitude_modifiers": [1.0, 0.5], "base_amplitude": 1.0},
    }
    for name, value in noises.items():
        write("worldgen/noise", f"deepworld:{name}", value)


def write_noise_settings():
    settings = vanilla.load("worldgen/noise_settings", "minecraft:overworld")
    settings["noise"] = {"min_y": world_min_y, "height": world_height}
    settings["sea_level"] = 1200
    router = settings["noise_router"]
    router["final_density"] = "deepworld:column/final_density"
    router["depth"] = "deepworld:column/depth"
    router["chunk_surface_level"] = df(router["chunk_surface_level"])
    aquifers = surface.tree(settings["aquifers"], "worldgen/density_function")
    settings["aquifers"] = aquifers
    settings["material_rule"] = "deepworld:column"
    settings.pop("debug_functions", None)
    write("worldgen/density_function", "deepworld:column/final_density", column_final())
    write("worldgen/density_function", "deepworld:column/depth", column_depth())
    write("worldgen/noise_settings", "minecraft:overworld", settings)


def write_dimension_type():
    dim = vanilla.load("dimension_type", "minecraft:overworld")
    dim["min_y"] = world_min_y
    dim["height"] = world_height
    dim["logical_height"] = world_height
    dim["attributes"]["minecraft:visual/cloud_height"] = 1424.33
    write("dimension_type", "minecraft:overworld", dim)


def block(state):
    return {"type": "minecraft:block", "result_state": state}


def condition(cond, then):
    return {"type": "minecraft:condition", "if_true": cond, "then_run": then}


def below_gradient(name, top, fade=6):
    return {"type": "minecraft:vertical_gradient", "random_name": f"deepworld:{name}", "true_at_and_below": {"absolute": top - fade}, "false_at_and_above": {"absolute": top + fade}}


def noise_band(lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": "deepworld:stone_band", "min_threshold": lo, "max_threshold": hi, "is_3d": True}


def biome_cond(biomes):
    return {"type": "minecraft:biome", "biome_is": biomes}


def layer_material(layer):
    rules = []
    for biome in layer["biomes"]:
        for floor_block in biome.get("floor", []):
            rules.append(condition(biome_cond([biome["id"]]), condition("minecraft:on_floor", block(floor_block))))
        for band in biome.get("bands", []):
            rules.append(condition(biome_cond([biome["id"]]), condition(noise_band(band["min"], band["max"]), block(band["block"]))))
    for band in layer.get("bands", []):
        rules.append(condition(noise_band(band["min"], band["max"]), block(band["block"])))
    rules.append(block(layer["stone"]))
    return {"type": "minecraft:sequence", "sequence": rules}


def write_material_rule():
    deep = []
    ordered = sorted(layers, key=lambda l: l["y"][0])
    for layer in ordered:
        if layer.get("material") == "nether":
            continue
        lo, hi = layer["y"]
        deep.append(condition(below_gradient(f"layer_{lo}", hi), layer_material(layer)))
    nether_rule = nether.tree(vanilla.load("worldgen/material_rule", "minecraft:nether"), "worldgen/material_rule")
    surface_rule = surface.tree(vanilla.load("worldgen/material_rule", "minecraft:overworld/surface"), "worldgen/material_rule")
    underground = surface.tree(vanilla.load("worldgen/material_rule", "minecraft:overworld/underground"), "worldgen/material_rule")
    copper_rule = copper_vein.tree(vanilla.load("worldgen/material_rule", "minecraft:overworld/copper_ore_vein"), "worldgen/material_rule")
    iron_rule = iron_vein.tree(vanilla.load("worldgen/material_rule", "minecraft:overworld/iron_ore_vein"), "worldgen/material_rule")
    rule = {
        "type": "minecraft:sequence",
        "sequence": [
            "minecraft:bedrock_floor",
            copper_rule,
            iron_rule,
            condition(below_gradient("nether_top", 0, 4), {"type": "minecraft:sequence", "sequence": [nether_rule, block("minecraft:netherrack")]}),
            condition({"type": "minecraft:above_preliminary_surface"}, surface_rule),
            *deep,
            underground,
        ],
    }
    write("worldgen/material_rule", "deepworld:column", rule)


def placed(feature, count, lo, hi, extra=None, shape="uniform"):
    placement = [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"}]
    height = {"type": f"minecraft:{shape}", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}
    placement.append({"type": "minecraft:height_range", "height": height})
    placement.extend(extra or [])
    placement.append({"type": "minecraft:biome"})
    return {"feature": feature, "placement": placement}


def ore_feature(name, hosts, ore_state, size, discard=0.0):
    targets = [{"target": {"predicate_type": "minecraft:block_match", "block": host}, "state": ore_state} for host in hosts]
    write("worldgen/feature", f"deepworld:ore/{name}", {"type": "minecraft:ore", "size": size, "discard_chance_on_air_exposure": discard, "targets": targets})
    return f"deepworld:ore/{name}"


def relocated_vanilla(spec, lo, hi, tag):
    placed_id = spec["id"]
    source = vanilla.load("worldgen/placed_feature", placed_id)
    height = {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}
    new_placement = []
    had_height = False
    dropped_heightmap = False
    for step in source["placement"]:
        step_type = step.get("type")
        if step_type == "minecraft:height_range":
            new_placement.append(height)
            had_height = True
        elif step_type in ("minecraft:heightmap", "minecraft:surface_water_depth_filter", "minecraft:surface_relative_threshold_filter"):
            dropped_heightmap = True
        elif step_type in ("minecraft:count", "minecraft:rarity_filter") and "count" in spec and not any(p.get("type") == "minecraft:count" and p.get("count") == spec["count"] for p in new_placement):
            new_placement.append({"type": "minecraft:count", "count": spec["count"]})
        else:
            new_placement.append(step)
    if not had_height:
        index = next((i for i, step in enumerate(new_placement) if step.get("type") == "minecraft:in_square"), -1) + 1
        new_placement.insert(index, height)
        if dropped_heightmap:
            new_placement.insert(index + 1, {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 24, "target_condition": {"type": "minecraft:solid"}, "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}})
            new_placement.insert(index + 2, {"type": "minecraft:offset", "x": 0, "y": 1, "z": 0})
    if "count" in spec and not any(step.get("type") == "minecraft:count" for step in new_placement):
        new_placement.insert(0, {"type": "minecraft:count", "count": spec["count"]})
    new_id = f"deepworld:{tag}/{placed_id.split(':')[1]}"
    write("worldgen/placed_feature", new_id, {"feature": source["feature"], "placement": new_placement})
    return new_id


def wrap_attributes(attributes):
    out = {}
    for key, value in attributes.items():
        if key == "minecraft:visual/ambient_particles" and isinstance(value, list):
            value = {"argument": value, "modifier": "append"}
        out[key] = value
    return out


def biome_json(layer, biome):
    base = vanilla.load("worldgen/biome", biome["base"])
    lo, hi = layer["y"]
    tag = biome["id"].split(":")[1]
    steps = [[] for _ in range(11)]
    for spec in biome.get("decor", []):
        steps[spec["step"]].append(relocated_vanilla(spec, lo, hi, tag))
    for extra in biome.get("extra_features", []):
        steps[extra["step"]].append(extra["id"])
    for ore in layer.get("ores", []) + biome.get("ores", []):
        feature_id = ore_feature(ore["name"], ore["hosts"], ore["block"], ore["size"], ore.get("discard", 0.0))
        placed_id = f"deepworld:{tag}/ore_{ore['name']}"
        write("worldgen/placed_feature", placed_id, placed(feature_id, ore["count"], ore.get("lo", lo), ore.get("hi", hi), shape=ore.get("shape", "uniform")))
        steps[6].append(placed_id)
    attributes = dict(base.get("attributes", {}))
    attributes.update(wrap_attributes(layer.get("attributes", {})))
    attributes.update(wrap_attributes(biome.get("attributes", {})))
    if "spawns" in biome:
        attributes["minecraft:gameplay/natural_mob_spawns"] = {"argument": {"spawn_costs": {}, "spawns_by_category": biome["spawns"]}, "modifier": "override"}
    result = {
        "has_precipitation": False,
        "temperature": base["temperature"],
        "downfall": base["downfall"],
        "attributes": attributes,
        "effects": base["effects"],
        "carvers": [],
        "features": steps,
    }
    write("worldgen/biome", biome["id"], result)


def write_nether_biomes():
    extra = {
        "minecraft:gameplay/piglins_zombify": False,
        "minecraft:gameplay/water_evaporates": True,
        "minecraft:gameplay/respawn_anchor_works": True,
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
        "minecraft:visual/fog_start_distance": 10.0,
        "minecraft:visual/fog_end_distance": 96.0,
    }
    for name in ["nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas"]:
        biome = vanilla.load("worldgen/biome", f"minecraft:{name}")
        biome["attributes"].update(extra)
        write("worldgen/biome", f"minecraft:{name}", biome)


def write_biomes():
    params = []
    for index, layer in enumerate(sorted(layers, key=lambda l: -l["y"][0])):
        depth_lo = 3.0 + index
        depth_hi = depth_lo + 1.0
        for biome in layer["biomes"]:
            if not biome["id"].startswith("minecraft:"):
                biome_json(layer, biome)
            params.append({
                "biome": biome["id"],
                "temperature": biome.get("temperature", [-1.0, 1.0]),
                "humidity": biome.get("humidity", [-1.0, 1.0]),
                "depth": [depth_lo, depth_hi],
            })
    summary = []
    for layer in sorted(layers, key=lambda l: -l["y"][0]):
        summary.append({
            "name": layer["name"],
            "y": layer["y"],
            "biomes": [b["id"] for b in layer["biomes"]],
            "ores": sorted({o["block"] for o in layer.get("ores", [])}),
        })
    (out_root / "deepworld_layers.json").write_text(json.dumps(summary, indent=1) + "\n")
    target = out_root / "deepworld_biome_params.json"
    target.write_text(json.dumps(params, indent=1) + "\n")


def nether_anchor(node):
    if "absolute" in node:
        return {"absolute": node["absolute"] + nether_shift}
    if "above_bottom" in node:
        return {"absolute": node["above_bottom"] + nether_shift}
    if "below_top" in node:
        return {"absolute": 127 - node["below_top"] + nether_shift}
    return node


def shift_anchors(node, fn):
    if isinstance(node, dict):
        if len(node) == 1 and next(iter(node)) in ("absolute", "above_bottom", "below_top"):
            return fn(node)
        return {k: shift_anchors(v, fn) for k, v in node.items()}
    if isinstance(node, list):
        return [shift_anchors(v, fn) for v in node]
    return node


def write_nether_features():
    nether_biomes = ["nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas"]
    seen = set()
    for biome in nether_biomes:
        for feature_ids in vanilla.load("worldgen/biome", f"minecraft:{biome}")["features"]:
            for feature_id in feature_ids:
                if feature_id in seen:
                    continue
                seen.add(feature_id)
                source = vanilla.load("worldgen/placed_feature", feature_id)
                write("worldgen/placed_feature", feature_id, shift_anchors(source, nether_anchor))


def write_carvers():
    for name in ["cave", "cave_extra_underground", "canyon"]:
        source = vanilla.load("worldgen/carver", f"minecraft:{name}")
        shifted = shift_anchors(source, lambda node: {"absolute": node["absolute"] + surface_shift} if "absolute" in node else {"absolute": node["above_bottom"] + surface_shift - 64} if "above_bottom" in node else node)
        if "lava_level" in shifted:
            shifted["lava_level"] = {"absolute": 1080}
        write("worldgen/carver", f"minecraft:{name}", shifted)


def write_structures():
    overrides = {
        "bastion_remnant": lambda s: s.update({"start_height": {"absolute": 33 + nether_shift}}),
        "nether_fossil": lambda s: s.update({"height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 32 + nether_shift}, "max_inclusive": {"absolute": 120 + nether_shift}}}),
        "ancient_city": lambda s: s.update({"start_height": {"absolute": 340}}),
        "trial_chambers": lambda s: s.update({"start_height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 1020}, "max_inclusive": {"absolute": 1060}}}),
    }
    for name, change in overrides.items():
        structure = vanilla.load("worldgen/structure", f"minecraft:{name}")
        change(structure)
        write("worldgen/structure", f"minecraft:{name}", structure)


def write_tags():
    tag_root = data_root / "minecraft" / "tags" / "worldgen" / "biome" / "has_structure"
    tag_root.mkdir(parents=True, exist_ok=True)
    extra = {}
    for layer in layers:
        for biome in layer["biomes"]:
            for structure in biome.get("structures", []):
                extra.setdefault(structure, []).append(biome["id"])
    for structure, biomes in extra.items():
        (tag_root / f"{structure}.json").write_text(json.dumps({"replace": False, "values": biomes}, indent=1) + "\n")


def main():
    if out_root.exists():
        shutil.rmtree(out_root)
    data_root.mkdir(parents=True)
    write_noises()
    write_dimension_type()
    write_noise_settings()
    write_material_rule()
    write_biomes()
    write_nether_features()
    write_nether_biomes()
    write_carvers()
    write_structures()
    write_tags()
    print(f"wrote {len(written)} files")


if __name__ == "__main__":
    main()
