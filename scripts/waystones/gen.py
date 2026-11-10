"""Draws the waystone's textures and writes its models (the waystones module's part of the pack).

The rune obelisk stands two blocks high on a plinth, with glowing runes on all four sides (a second model, shown at full
light); in front of it the altar: a stone slab (the game's own) with a small pedestal and bowl, and a purple pearl
floating over it. The pearl item has its own sprite.

Run: python3 scripts/waystones/gen.py
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "waystones", "src", "main", "resources", "assets", "libreria")
TEX = os.path.join(ROOT, "textures")
MODELS = os.path.join(ROOT, "models", "item", "waystone")
ITEMS = os.path.join(ROOT, "items")

random.seed(7)


def save(img, *path):
    p = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def noise(base, spread):
    return tuple(max(0, min(255, c + random.randint(-spread, spread))) for c in base)


def bricks():
    img = Image.new("RGBA", (16, 16))
    mortar = (92, 92, 96)
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            edge_y = y % 4 == 3
            edge_x = (x + off) % 8 == 7
            if edge_y or edge_x:
                img.putpixel((x, y), (*noise(mortar, 4), 255))
            else:
                top = y % 4 == 0
                base = (146, 146, 150) if top else (128, 128, 132)
                img.putpixel((x, y), (*noise(base, 7), 255))
    return img


def plain(base, spread, border=None):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = noise(base, spread)
            if border and (x in (0, 15) or y in (0, 15)):
                c = border
            img.putpixel((x, y), (*c, 255))
    return img


def runes():
    """Glyphs in a column (x 3..12, y 1..14), teal with a darker rim; the rest see-through."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glow, rim = (95, 242, 224, 255), (36, 168, 156, 255)
    shapes = [
        [(6, 1), (7, 1), (8, 1), (9, 1), (7, 2), (8, 2), (7, 3), (8, 3)],
        [(5, 5), (6, 5), (9, 5), (10, 5), (6, 6), (9, 6), (7, 7), (8, 7), (6, 8), (9, 8)],
        [(7, 10), (8, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (7, 12), (8, 12), (7, 13), (8, 13), (6, 14), (9, 14)],
    ]
    for shape in shapes:
        for p in shape:
            img.putpixel(p, glow)
        for (x, y) in shape:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if 3 <= q[0] <= 12 and 0 <= q[1] <= 15 and img.getpixel(q)[3] == 0:
                    img.putpixel(q, rim)
    return img


def pearl_block():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), (*noise((123, 63, 179), 18), 255))
    for p in ((3, 3), (4, 3), (3, 4), (11, 9), (12, 9), (8, 12)):
        img.putpixel(p, (217, 182, 255, 255))
    return img


def obelisk_icon():
    """The waystone as a flat 16x16 picture, for the icon slot of cards and buttons (Libreria's font, in core)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    outline = (40, 40, 46, 255)
    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                img.putpixel((x, y), c)
    # plinth, shaft, taper, cap (light from the left)
    rect(3, 13, 12, 15, (94, 94, 100, 255))
    rect(3, 13, 12, 13, (112, 112, 118, 255))
    rect(5, 5, 10, 12, (138, 138, 142, 255))
    rect(5, 5, 6, 12, (156, 156, 160, 255))
    rect(6, 2, 9, 4, (150, 150, 154, 255))
    rect(7, 0, 8, 1, (178, 178, 182, 255))
    for (x, y) in ((7, 6), (8, 6), (7, 7), (7, 8), (8, 8), (7, 10), (8, 10), (8, 11)):
        img.putpixel((x, y), (95, 242, 224, 255))
    # a dark rim around it all
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3]
                                                     for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), outline)
    return img


def pearl_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy, r = 7.5, 8.0, 5.6
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                shade = 1 - (d / r) * 0.45
                base = (int(140 * shade), int(70 * shade), int(200 * shade))
                img.putpixel((x, y), (*base, 255))
            elif d <= r + 0.9:
                img.putpixel((x, y), (40, 18, 64, 255))
    for p in ((5, 5), (6, 5), (5, 6)):
        img.putpixel(p, (236, 214, 255, 255))
    for p in ((9, 9), (10, 10), (8, 11), (10, 8)):
        img.putpixel(p, (95, 242, 224, 255))
    return img


def element(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), shade=True):
    return {"from": frm, "to": to, "shade": shade, "faces": {f: {"texture": tex} for f in faces}}


def plane(frm, to, face, uv):
    return {"from": frm, "to": to, "shade": False, "faces": {face: {"texture": "#r", "uv": uv}}}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)


def main():
    save(bricks(), "block", "waystone_bricks.png")
    save(plain((94, 94, 100), 5, border=(70, 70, 76)), "block", "waystone_base.png")
    save(plain((170, 170, 174), 6), "block", "waystone_cap.png")
    save(runes(), "block", "waystone_runes.png")
    save(pearl_block(), "block", "waystone_pearl.png")
    save(pearl_item(), "item", "waystone_pearl.png")
    # Icons for cards and buttons live in core (its font points at them; core is always there)
    icons = os.path.join(os.path.dirname(__file__), "..", "..", "core", "src", "main", "resources", "assets", "libreria", "textures", "item", "icon")
    os.makedirs(icons, exist_ok=True)
    obelisk_icon().save(os.path.join(icons, "waystone.png"))
    pearl_item().save(os.path.join(icons, "waystone_pearl.png"))

    textures = {"s": "libreria:block/waystone_bricks", "b": "libreria:block/waystone_base", "c": "libreria:block/waystone_cap",
                "particle": "libreria:block/waystone_bricks"}
    obelisk = {"textures": textures, "elements": [
        element([1, 0, 1], [15, 3, 15], "#b"),
        element([2, 3, 2], [14, 4, 14], "#b"),
        element([3, 4, 3], [13, 20, 13], "#s"),
        element([4, 20, 4], [12, 27, 12], "#s"),
        element([3.5, 27, 3.5], [12.5, 28, 12.5], "#c"),
        element([5, 28, 5], [11, 30, 11], "#c"),
        element([6.5, 30, 6.5], [9.5, 32, 9.5], "#c"),
    ]}
    write(os.path.join(MODELS, "obelisk.json"), obelisk)
    uv = [3, 1, 13, 15]
    runes_model = {"textures": {"r": "libreria:block/waystone_runes", "particle": "libreria:block/waystone_runes"}, "elements": [
        plane([3, 5, 2.97], [13, 19, 2.97], "north", uv),
        plane([3, 5, 13.03], [13, 19, 13.03], "south", uv),
        plane([2.97, 5, 3], [2.97, 19, 13], "west", uv),
        plane([13.03, 5, 3], [13.03, 19, 13], "east", uv),
    ]}
    write(os.path.join(MODELS, "runes.json"), runes_model)
    altar = {"textures": textures, "elements": [
        element([6, 8, 6], [10, 11, 10], "#s"),
        element([4.5, 11, 4.5], [11.5, 12.5, 11.5], "#b"),
        element([5.5, 12.5, 5.5], [10.5, 12.6, 10.5], "#c", faces=("up",)),
    ]}
    write(os.path.join(MODELS, "altar.json"), altar)
    pearl = {"textures": {"p": "libreria:block/waystone_pearl", "particle": "libreria:block/waystone_pearl"},
             "elements": [element([6.5, 6.5, 6.5], [9.5, 9.5, 9.5], "#p", shade=False)]}
    write(os.path.join(MODELS, "pearl.json"), pearl)
    # The waystone in the inventory: the obelisk, small and turned
    write(os.path.join(MODELS, "item.json"), {"parent": "libreria:item/waystone/obelisk", "display": {
        "gui": {"rotation": [25, 45, 0], "translation": [0, -5.5, 0], "scale": [0.42, 0.42, 0.42]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -4, 0], "scale": [0.45, 0.45, 0.45]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.25, 0.25, 0.25]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.25, 0.25, 0.25]}}})
    write(os.path.join(ROOT, "models", "item", "waystone_pearl.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "libreria:item/waystone_pearl"}})

    for name, model in (("waystone", "libreria:item/waystone/item"), ("waystone_pearl", "libreria:item/waystone_pearl"),
                        ("waystone/obelisk", "libreria:item/waystone/obelisk"), ("waystone/runes", "libreria:item/waystone/runes"),
                        ("waystone/altar", "libreria:item/waystone/altar"), ("waystone/pearl", "libreria:item/waystone/pearl")):
        write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": model}})

    write(os.path.join(ROOT, "lang", "en_us.json"), {
        "block.libreria.waystone": "Waystone", "block.libreria.waystone_altar": "Waystone Altar",
        "item.libreria.waystone": "Waystone", "item.libreria.waystone_pearl": "Waystone Pearl"})


if __name__ == "__main__":
    main()
