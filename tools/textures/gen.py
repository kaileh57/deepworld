import json
import sys
from pathlib import Path

from PIL import Image

root = Path(__file__).resolve().parent
assets = root.parent.parent / "src" / "main" / "resources" / "assets" / "deepworld" / "textures"


def hex_color(value):
    value = value.lstrip("#")
    alpha = int(value[6:8], 16) if len(value) == 8 else 255
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def draw(sprite, palettes):
    palette = {key: hex_color(value) for key, value in palettes[sprite["palette"]].items()}
    palette["."] = (0, 0, 0, 0)
    rows = sprite["pixels"]
    image = Image.new("RGBA", (len(rows[0]), len(rows)))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            image.putpixel((x, y), palette[key])
    return image


def main():
    palettes = json.loads((root / "palettes.json").read_text())
    for sprite_file in sorted((root / "sprites").rglob("*.json")):
        sprite = json.loads(sprite_file.read_text())
        out_file = assets / sprite_file.parent.name / f"{sprite_file.stem}.png"
        out_file.parent.mkdir(parents=True, exist_ok=True)
        draw(sprite, palettes).save(out_file)
        print(out_file.relative_to(root.parent.parent))
    if "--preview" in sys.argv:
        preview_dir = root.parent.parent / "build" / "texture_preview"
        preview_dir.mkdir(parents=True, exist_ok=True)
        for png in assets.rglob("*.png"):
            Image.open(png).resize((256, 256), Image.NEAREST).save(preview_dir / png.name)


if __name__ == "__main__":
    main()
