import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

root = Path(__file__).resolve().parent
project = root.parent.parent
assets = project / "src" / "main" / "resources" / "assets" / "deepworld"
textures = assets / "textures"


def hex_color(value):
    value = value.lstrip("#")
    alpha = int(value[6:8], 16) if len(value) == 8 else 255
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


palettes = {name: {key: hex_color(value) for key, value in entry.items()} for name, entry in json.loads((root / "palettes.json").read_text()).items()}
templates = json.loads((root / "templates.json").read_text())


def draw_grid(rows, palette):
    image = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                image.putpixel((x, y), palette[key])
    return image


def save(image, kind, name):
    out = textures / kind / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    return out


def ramp(palette):
    return [palette[k] for k in ("o", "d", "m", "l", "h")]


def value_noise(width, height, cell, seed):
    rng = random.Random(seed)
    grid_w = width // cell + 2
    grid_h = height // cell + 2
    grid = [[rng.random() for _ in range(grid_w)] for _ in range(grid_h)]

    def sample(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx), int(gy)
        tx, ty = gx - x0, gy - y0
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        a = grid[y0 % grid_h][x0 % grid_w]
        b = grid[y0 % grid_h][(x0 + 1) % grid_w]
        c = grid[(y0 + 1) % grid_h][x0 % grid_w]
        d = grid[(y0 + 1) % grid_h][(x0 + 1) % grid_w]
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty

    return sample


def stone(spec):
    size = 16
    palette = [hex_color(c) for c in spec["shades"]]
    rng = random.Random(spec["seed"])
    coarse = value_noise(size, size, 4, spec["seed"])
    fine = value_noise(size, size, 2, spec["seed"] + 1)
    image = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            v = 0.65 * coarse(x, y) + 0.35 * fine(x, y)
            v += spec.get("strata", 0.0) * math.sin((y + coarse(x, 0) * 4) * 1.1)
            index = max(0, min(len(palette) - 1, int(v * len(palette))))
            image.putpixel((x, y), palette[index])
    for _ in range(spec.get("pores", 0)):
        x, y = rng.randrange(size), rng.randrange(size)
        image.putpixel((x, y), palette[0])
        if x + 1 < size:
            image.putpixel((x + 1, y), palette[min(1, len(palette) - 1)])
    for fleck in spec.get("flecks", []):
        color = hex_color(fleck["color"])
        for _ in range(fleck["count"]):
            image.putpixel((rng.randrange(size), rng.randrange(size)), color)
    for vein in spec.get("veins", []):
        color = hex_color(vein["color"])
        x, y = rng.randrange(size), rng.randrange(size)
        for _ in range(vein["length"]):
            image.putpixel((x % size, y % size), color)
            x += rng.choice((-1, 0, 1, 1))
            y += rng.choice((0, 1))
    return image


def ore_overlay(spec):
    size = 16
    shades = ramp(palettes[spec["palette"]])
    rng = random.Random(spec["seed"])
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shapes = [
        [(0, 0), (1, 0), (0, 1), (1, 1)],
        [(0, 0), (1, 0), (2, 0), (1, 1), (0, 1)],
        [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)],
        [(0, 0), (1, 0), (1, 1), (2, 1), (2, 2)],
        [(0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (2, 2)],
    ]
    placed = []
    cluster_count = 0
    attempts = 0
    while cluster_count < spec.get("clusters", 5) and attempts < 400:
        attempts += 1
        shape = rng.choice(shapes)
        ox, oy = rng.randrange(1, size - 3), rng.randrange(1, size - 3)
        cells = [(ox + dx, oy + dy) for dx, dy in shape]
        if any(abs(cx - px) < 2 and abs(cy - py) < 2 for cx, cy in cells for px, py in placed):
            continue
        for cx, cy in cells:
            image.putpixel((cx, cy), shades[2])
        for cx, cy in cells:
            if (cx - 1, cy) not in cells or (cx, cy - 1) not in cells:
                image.putpixel((cx, cy), shades[3])
        top_left = min(cells, key=lambda c: c[0] + c[1])
        image.putpixel(top_left, shades[4])
        for cx, cy in cells:
            for nx, ny in ((cx + 1, cy), (cx, cy + 1)):
                if (nx, ny) not in cells and 0 <= nx < size and 0 <= ny < size and image.getpixel((nx, ny))[3] == 0:
                    image.putpixel((nx, ny), shades[1])
        placed.extend(cells)
        cluster_count += 1
    return image


def bricks(spec):
    size = 16
    palette = [hex_color(c) for c in spec["shades"]]
    mortar = hex_color(spec["mortar"])
    rng = random.Random(spec["seed"])
    image = Image.new("RGBA", (size, size), mortar)
    for row in range(4):
        offset = 0 if row % 2 == 0 else 4
        for col in range(-1, 3):
            x0 = col * 8 + offset
            shade = rng.randrange(len(palette) - 1)
            for y in range(row * 4, row * 4 + 3):
                for x in range(x0, x0 + 7):
                    if 0 <= x < size:
                        pick = shade + (1 if y == row * 4 else 0)
                        if rng.random() < 0.12:
                            pick = max(0, pick - 1)
                        image.putpixel((x, y), palette[min(pick, len(palette) - 1)])
    return image


def forge_front(spec):
    image = bricks(spec)
    glow = [hex_color(c) for c in spec["glow"]]
    for y in range(7, 14):
        for x in range(4, 12):
            depth = min(x - 4, 11 - x, y - 7, 13 - y)
            image.putpixel((x, y), glow[min(depth, len(glow) - 1)])
    edge = hex_color(spec["mortar"])
    for x in range(3, 13):
        image.putpixel((x, 6), edge)
    return image


face_boxes = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
}


def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_face(image, rect, shades, rng, rows=None, trim=None):
    x0, y0, w, h = rect
    for y in range(h):
        if rows is not None and y not in rows:
            continue
        for x in range(w):
            edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
            index = 2
            if edge:
                index = 1
            elif y == 1 or x == 1:
                index = 3
            if rng.random() < 0.08:
                index = max(1, index - 1)
            image.putpixel((x0 + x, y0 + y), shades[index])
    if trim is not None and w > 2:
        mid = y0 + h // 2
        if rows is None or (mid - y0) in rows:
            for x in range(1, w - 1):
                image.putpixel((x0 + x, mid), trim)


def armor_textures(spec):
    shades = ramp(palettes[spec["palette"]])
    trim = hex_color(spec["trim"]) if "trim" in spec else None
    rng = random.Random(spec["seed"])
    humanoid = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for name, rect in box_faces(*face_boxes["head"]).items():
        paint_face(humanoid, rect, shades, rng, trim=trim if name != "top" else None)
    visor_x, visor_y = 8 + 1, 8 + 3
    for x in range(6):
        humanoid.putpixel((visor_x + x, visor_y), (0, 0, 0, 0))
        humanoid.putpixel((visor_x + x, visor_y + 1), shades[0])
    for rect in box_faces(*face_boxes["body"]).values():
        paint_face(humanoid, rect, shades, rng, trim=trim)
    for rect in box_faces(*face_boxes["arm"]).values():
        paint_face(humanoid, rect, shades, rng, rows=set(range(0, 6)), trim=None)
    for name, rect in box_faces(*face_boxes["leg"]).items():
        rows = set(range(8, 12)) if name not in ("top",) else set()
        if name == "bottom":
            rows = set(range(0, 4))
        paint_face(humanoid, rect, shades, rng, rows=rows)
    leggings = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for name, rect in box_faces(*face_boxes["body"]).items():
        if name in ("top",):
            continue
        rows = set(range(7, 12)) if name in ("front", "back", "left", "right") else set(range(0, 4))
        paint_face(leggings, rect, shades, rng, rows=rows)
    for name, rect in box_faces(*face_boxes["leg"]).items():
        rows = set(range(0, 9)) if name in ("front", "back", "left", "right") else set(range(0, 4))
        if name == "bottom":
            rows = set()
        paint_face(leggings, rect, shades, rng, rows=rows, trim=trim)
    out_h = textures / "entity" / "equipment" / "humanoid" / f"{spec['name']}.png"
    out_l = textures / "entity" / "equipment" / "humanoid_leggings" / f"{spec['name']}.png"
    out_h.parent.mkdir(parents=True, exist_ok=True)
    out_l.parent.mkdir(parents=True, exist_ok=True)
    humanoid.save(out_h)
    leggings.save(out_l)
    return [out_h, out_l]


def main():
    sprites = json.loads((root / "sprites.json").read_text())
    outputs = []
    for sprite_file in sorted((root / "sprites").rglob("*.json")):
        sprite = json.loads(sprite_file.read_text())
        outputs.append(save(draw_grid(sprite["pixels"], palettes[sprite["palette"]] | {"o": palettes[sprite["palette"]]["o"]}), sprite_file.parent.name, sprite_file.stem))
    for entry in sprites.get("templated", []):
        palette = dict(palettes[entry["palette"]])
        palette.update(palettes.get(entry.get("extra", "handle"), {}))
        palette.setdefault("x", palette["h"])
        palette.setdefault("c", palette["m"])
        palette.setdefault("b", palette["d"])
        outputs.append(save(draw_grid(templates[entry["template"]], palette), "item", entry["name"]))
    for entry in sprites.get("stones", []):
        outputs.append(save(stone(entry), "block", entry["name"]))
    for entry in sprites.get("ores", []):
        outputs.append(save(ore_overlay(entry), "block", f"{entry['name']}_overlay"))
    for entry in sprites.get("bricks", []):
        outputs.append(save(bricks(entry), "block", entry["name"]))
        if "glow" in entry:
            outputs.append(save(forge_front(entry), "block", f"{entry['name']}_front"))
    for entry in sprites.get("armor", []):
        outputs.extend(armor_textures(entry))
    for entry in sprites.get("plants", []):
        outputs.append(save(draw_grid(templates[entry["template"]], palettes[entry["palette"]]), "block", entry["name"]))
    for entry in sprites.get("dishes", []) + sprites.get("generic_dishes", []):
        ramp_colors = [hex_color(c) for c in entry["ramp"]]
        darkest = tuple(max(0, int(c * 0.45)) for c in ramp_colors[0][:3]) + (255,)
        palette = {"o": darkest, "d": ramp_colors[0], "m": ramp_colors[1], "l": ramp_colors[2], "h": ramp_colors[3], "x": ramp_colors[4]}
        palette.update(palettes.get(entry["template"], {}))
        palette.setdefault("b", ramp_colors[0])
        palette.setdefault("c", ramp_colors[1])
        outputs.append(save(draw_grid(templates[entry["template"]], palette), "item/dish", entry["name"]))
    print(f"wrote {len(outputs)} textures")
    if "--preview" in sys.argv:
        preview_dir = project / "build" / "texture_preview"
        preview_dir.mkdir(parents=True, exist_ok=True)
        icons = [p for p in outputs if p.parent.name in ("item", "block", "dish")]
        sheet = Image.new("RGBA", (8 * 132, ((len(icons) + 7) // 8) * 132), (40, 40, 48, 255))
        for i, path in enumerate(icons):
            icon = Image.open(path).convert("RGBA").resize((128, 128), Image.NEAREST)
            sheet.alpha_composite(icon, ((i % 8) * 132 + 2, (i // 8) * 132 + 2))
        sheet.save(preview_dir / "sheet.png")
        for path in outputs:
            if "equipment" in path.parts:
                Image.open(path).resize((512, 256), Image.NEAREST).save(preview_dir / f"{path.parent.name}_{path.name}")


if __name__ == "__main__":
    main()
