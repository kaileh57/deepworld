import json
import shutil
import zipfile
import glob
from pathlib import Path

root = Path(__file__).resolve().parent.parent.parent
out = root / "src" / "main" / "generated_content" / "data"
jar = [p for p in glob.glob(str(root / ".gradle" / "loom-cache" / "minecraftMaven" / "**" / "minecraft-merged-*.jar"), recursive=True) if "sources" not in p][0]
z = zipfile.ZipFile(jar)

remove = {
    "farmer": ["wheat_emerald", "potato_emerald", "carrot_emerald", "beetroot_emerald", "pumpkin_emerald", "melon_emerald"],
    "toolsmith": ["emerald_enchanted_diamond_axe", "emerald_enchanted_diamond_shovel", "emerald_enchanted_diamond_pickaxe", "emerald_diamond_hoe"],
    "armorer": ["emerald_enchanted_diamond_boots", "emerald_enchanted_diamond_leggings", "emerald_enchanted_diamond_chestplate", "emerald_enchanted_diamond_helmet"],
    "weaponsmith": ["emerald_enchanted_diamond_axe", "emerald_enchanted_diamond_sword"],
}


def trade(wants, wants_count, gives, gives_count, max_uses=12, xp=5):
    entry = {"gives": {"id": gives}, "max_uses": max_uses, "reputation_discount": 0.05, "wants": {"id": wants}, "xp": xp}
    if gives_count != 1:
        entry["gives"]["count"] = gives_count
    if wants_count != 1:
        entry["wants"]["count"] = wants_count
    return entry


def notes(tier, price):
    entry = trade("minecraft:emerald", price, "deepworld:cooking_notes", 1, max_uses=4)
    entry["gives"]["components"] = {"minecraft:custom_data": {"deepworld": {"tier": tier}}}
    return entry


add = {
    ("farmer", 1): {"emerald_rice": trade("minecraft:emerald", 1, "deepworld:rice", 6), "emerald_beans": trade("minecraft:emerald", 1, "deepworld:beans", 6), "emerald_oats": trade("minecraft:emerald", 1, "deepworld:oats", 6)},
    ("farmer", 2): {"emerald_chili": trade("minecraft:emerald", 2, "deepworld:chili", 4), "emerald_mint": trade("minecraft:emerald", 2, "deepworld:mint", 4), "compost_emerald": trade("deepworld:compost", 8, "minecraft:emerald", 1)},
    ("farmer", 3): {"emerald_tea": trade("minecraft:emerald", 3, "deepworld:tea_leaves", 4), "emerald_coffee": trade("minecraft:emerald", 3, "deepworld:coffee_beans", 4)},
    ("butcher", 1): {"emerald_salt": trade("minecraft:emerald", 1, "deepworld:salt", 8)},
    ("butcher", 2): {"emerald_butter": trade("minecraft:emerald", 2, "deepworld:butter", 4), "emerald_herbs": trade("minecraft:emerald", 1, "deepworld:herbs", 6), "emerald_notes_1": notes(1, 3)},
    ("butcher", 3): {"emerald_ash_pepper": trade("minecraft:emerald", 4, "deepworld:ash_pepper", 3), "emerald_cheese": trade("minecraft:emerald", 3, "deepworld:cheese", 2), "emerald_notes_2": notes(2, 5)},
    ("butcher", 4): {"emerald_blaze_spice": trade("minecraft:emerald", 8, "deepworld:blaze_spice", 2), "emerald_notes_4": notes(4, 12)},
    ("butcher", 5): {"emerald_notes_3": notes(3, 8)},
    ("toolsmith", 1): {"emerald_rail": trade("minecraft:emerald", 1, "minecraft:rail", 16)},
    ("toolsmith", 2): {"emerald_iron_pot": trade("minecraft:emerald", 6, "deepworld:iron_pot", 1), "emerald_iron_skillet": trade("minecraft:emerald", 5, "deepworld:iron_skillet", 1)},
    ("toolsmith", 3): {"emerald_powered_rail": trade("minecraft:emerald", 4, "minecraft:powered_rail", 6), "emerald_flux": trade("minecraft:emerald", 3, "deepworld:flux", 4)},
    ("toolsmith", 4): {"emerald_lift_station": trade("minecraft:emerald", 16, "deepworld:lift_station", 1)},
    ("toolsmith", 5): {"emerald_smithing_bench": trade("minecraft:emerald", 12, "deepworld:smithing_bench", 1)},
    ("cleric", 2): {"emerald_sulfur": trade("minecraft:emerald", 2, "minecraft:sulfur", 4)},
    ("cleric", 3): {"emerald_sulfur_salt": trade("minecraft:emerald", 3, "deepworld:sulfur_salt", 4), "emerald_glowcap_essence": trade("minecraft:emerald", 6, "deepworld:glowcap_essence", 1)},
    ("cartographer", 2): {"emerald_survey_kit": trade("minecraft:emerald", 8, "deepworld:survey_kit", 1)},
    ("librarian", 2): {"emerald_logbook": trade("minecraft:emerald", 5, "deepworld:logbook", 1)},
    ("librarian", 3): {"emerald_guild_journal": trade("minecraft:emerald", 4, "deepworld:guild_journal", 1)},
    ("armorer", 3): {"emerald_safety_lamp": trade("minecraft:emerald", 6, "deepworld:safety_lamp", 1)},
    ("mason", 2): {"emerald_amethyst_lamp": trade("minecraft:emerald", 5, "deepworld:amethyst_lamp", 2)},
}


def main():
    touched = {}
    for name in z.namelist():
        if not name.startswith("data/minecraft/tags/villager_trade/") or not name.endswith(".json"):
            continue
        rel = name[len("data/minecraft/tags/villager_trade/"):-5]
        if "/" not in rel:
            continue
        profession, level = rel.split("/", 1)
        tail = level.split("_")[-1]
        if not tail.isdigit():
            continue
        level_number = int(tail)
        values = json.loads(z.read(name))["values"]
        kept = [v for v in values if not any(v.endswith("/" + r) for r in remove.get(profession, []))]
        extra = add.get((profession, level_number), {})
        if kept == values and not extra:
            continue
        for trade_id, body in extra.items():
            path = out / "deepworld" / "villager_trade" / profession / str(level_number) / f"{trade_id}.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(body, indent=1) + "\n")
            kept.append(f"deepworld:{profession}/{level_number}/{trade_id}")
        target = out / "minecraft" / "tags" / "villager_trade" / profession / f"{level}.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({"replace": True, "values": kept}, indent=1) + "\n")
        touched[rel] = len(kept)
    print(f"trades: {len(touched)} level tags changed")


if __name__ == "__main__":
    main()
