"""Waystone, second design: a dressed-stone obelisk on a stepped plinth with an inset, glowing rune panel on each side
and a crystal capstone; in front of it a matching altar (stepped base, column with one rune, a basin with a glowing
pool and a pearl floating over it). Everything is our own: no blocks of the game are part of it.

Every face is mapped one texture pixel to one model pixel (no stretched or half pixels). Two colourways:
  amethyst: pale stone, violet glow;  ender: dark slate, teal glow.
Writes, per colourway <v>, under waystones/src/main/resources/assets/libreria/:
  textures/block/waystone/<v>/{stone,top,trim,panel,glyph,crystal,basin,pool,pearl}.png
  models/item/waystone/<v>/{obelisk,obelisk_glow,altar,altar_glow,pearl}.json  and  items/waystone/<v>/*.json

Run: python3 scripts/waystones/gen2.py
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "waystones", "src", "main", "resources", "assets", "libreria")

PALETTES = {
    "amethyst": dict(stone=(182, 178, 190), course=(158, 154, 168), joint=(150, 146, 160), lit=(204, 200, 212),
                     trim=(128, 122, 140), trim_lit=(160, 154, 172), trim_dark=(100, 94, 112),
                     panel=(64, 56, 80), panel_dark=(44, 38, 58), panel_lip=(150, 144, 160),
                     glow=(226, 190, 255), halo=(160, 104, 226), crystal=(176, 118, 238), crystal_lit=(232, 206, 255),
                     crystal_dark=(112, 66, 176), pool=(236, 210, 255), pool_edge=(140, 86, 212),
                     pearl=(150, 92, 216), pearl_lit=(222, 190, 255), pearl_dark=(92, 50, 150)),
    "ender": dict(stone=(92, 98, 108), course=(76, 81, 90), joint=(70, 75, 84), lit=(114, 121, 132),
                  trim=(60, 64, 72), trim_lit=(84, 90, 100), trim_dark=(44, 47, 54),
                  panel=(30, 34, 40), panel_dark=(20, 22, 27), panel_lip=(104, 110, 120),
                  glow=(140, 255, 232), halo=(36, 168, 156), crystal=(64, 206, 186), crystal_lit=(176, 255, 240),
                  crystal_dark=(28, 118, 108), pool=(170, 255, 240), pool_edge=(34, 150, 140),
                  pearl=(40, 140, 120), pearl_lit=(150, 255, 230), pearl_dark=(18, 70, 62)),
}


def noise(rng, c, spread):
    return tuple(max(0, min(255, v + rng.randint(-spread, spread))) for v in c) + (255,)


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def stone(p, rng):
    """Dressed stone in courses of 8 rows (a joint line under each), the vertical joints staggered."""
    im = img()
    for y in range(16):
        for x in range(16):
            row = y % 8
            joint_x = 4 if y < 8 else 12
            if row == 7:
                c = noise(rng, p["course"], 3)
            elif x == joint_x:
                c = noise(rng, p["joint"], 3)
            elif row == 0 or x == (joint_x + 1) % 16:
                c = noise(rng, p["lit"], 3)
            else:
                c = noise(rng, p["stone"], 5)
            im.putpixel((x, y), c)
    return im


def top(p, rng):
    im = img()
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), noise(rng, p["lit"], 4))
    return im


def trim(p, rng):
    """Row 0: the lit edge; rows 1-14: the band; row 15: its shadow."""
    im = img()
    for y in range(16):
        for x in range(16):
            c = p["trim_lit"] if y == 0 else p["trim_dark"] if y == 15 else p["trim"]
            im.putpixel((x, y), noise(rng, c, 3))
    return im


PANEL = (5, 2, 11, 15)  # x0, y0, x1, y1 (exclusive) of the rune panel inside its texture


def panel(p, rng):
    """The recessed panel: shadow along the top and left, a lit lip along the bottom and right."""
    im = img()
    x0, y0, x1, y1 = PANEL
    for y in range(y0, y1):
        for x in range(x0, x1):
            if y == y0 or x == x0:
                c = p["panel_dark"]
            elif y == y1 - 1 or x == x1 - 1:
                c = p["panel_lip"]
            else:
                c = p["panel"]
            im.putpixel((x, y), noise(rng, c, 2))
    return im


RUNES = [
    # three glyphs in the 4 x 11 inside of the panel (x 6..9, y 3..13), a dark row between them
    [(6, 3), (9, 3), (7, 4), (8, 4), (6, 5), (9, 5)],                 # a cross
    [(7, 7), (8, 7), (6, 8), (9, 8), (7, 9), (8, 9)],                 # a ring
    [(6, 11), (9, 11), (6, 12), (7, 12), (8, 12), (9, 12), (7, 13), (8, 13)],  # a cup
]


def glyph(p):
    im = img()
    for r in RUNES:
        for q in r:
            im.putpixel(q, p["glow"] + (255,))
    return im


def crystal(p, rng):
    """Facets: lit towards the top left, dark towards the bottom right, a few sparkles."""
    im = img()
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30
            c = tuple(int(a * (1 - t) + b * t) for a, b in zip(p["crystal_lit"], p["crystal_dark"]))
            im.putpixel((x, y), noise(rng, c, 6))
    for q in ((3, 2), (9, 5), (5, 11), (12, 9)):
        im.putpixel(q, (255, 255, 255, 255))
    return im


def basin(p, rng):
    """The top of the basin (x/y 3..12): a lit rim, then a dark hollow."""
    im = top(p, rng)
    for y in range(4, 12):
        for x in range(4, 12):
            edge = y == 4 or x == 4
            im.putpixel((x, y), noise(rng, p["panel_dark"] if edge else p["panel"], 2))
    return im


def pool(p):
    """The glowing water in the basin (x/y 5..10), brightest in the middle."""
    im = img()
    for y in range(5, 11):
        for x in range(5, 11):
            d = max(abs(x - 7.5), abs(y - 7.5)) / 3
            c = tuple(int(a * (1 - d) + b * d) for a, b in zip(p["pool"], p["pool_edge"]))
            im.putpixel((x, y), c + (255,))
    return im


def pearl(p, rng):
    """Smooth: lit at the top left, darker towards the bottom right, one bright fleck."""
    im = img()
    for y in range(16):
        for x in range(16):
            t = min(1.0, ((x - 3) ** 2 + (y - 3) ** 2) ** 0.5 / 14)
            c = tuple(int(a * (1 - t) + b * t) for a, b in zip(p["pearl_lit"], p["pearl_dark"]))
            im.putpixel((x, y), noise(rng, c, 2))
    return im


# ---------------------------------------------------------------- models

FACES = ("north", "south", "east", "west", "up", "down")


def uv(face, frm, to, kind):
    """One texture pixel per model pixel: a window of the face's size, placed where the face is."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    if face in ("up", "down"):
        return [x0, z0, x1, z1]
    w0, w1 = (x0, x1) if face in ("north", "south") else (z0, z1)
    h = y1 - y0
    if kind == "trim":
        v0 = 0 if h >= 2 else 1
        return [w0, v0, w1, v0 + h]
    # stone: courses run from the top of each piece
    return [w0, 0, w1, h]


def box(frm, to, tex, kind="stone", top_tex=None, faces=FACES, shade=True):
    out = {}
    for f in faces:
        t = top_tex if (f == "up" and top_tex) else tex
        out[f] = {"texture": t, "uv": uv(f, frm, to, kind)}
    return {"from": frm, "to": to, "shade": shade, "faces": out}


def plane(frm, to, face, tex, window):
    return {"from": frm, "to": to, "shade": False, "faces": {face: {"texture": tex, "uv": window}}}


def panels(z_off, tex, x0=5, x1=11, y0=7, y1=19, lo=3, hi=13):
    """A rune panel on each side of a shaft from lo to hi, just in front of its faces."""
    win = [PANEL[0], PANEL[1], PANEL[2], PANEL[3]]
    return [
        plane([x0, y0, lo - z_off], [x1, y1, lo - z_off], "north", tex, win),
        plane([x0, y0, hi + z_off], [x1, y1, hi + z_off], "south", tex, win),
        plane([lo - z_off, y0, x0], [lo - z_off, y1, x1], "west", tex, win),
        plane([hi + z_off, y0, x0], [hi + z_off, y1, x1], "east", tex, win),
    ]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)


def main():
    for v, p in PALETTES.items():
        rng = random.Random(11)
        tex_dir = os.path.join(ROOT, "textures", "block", "waystone", v)
        os.makedirs(tex_dir, exist_ok=True)
        for name, im in (("stone", stone(p, rng)), ("top", top(p, rng)), ("trim", trim(p, rng)), ("panel", panel(p, rng)),
                         ("glyph", glyph(p)), ("crystal", crystal(p, rng)), ("basin", basin(p, rng)), ("pool", pool(p)),
                         ("pearl", pearl(p, rng))):
            im.save(os.path.join(tex_dir, name + ".png"))
        t = lambda n: "libreria:block/waystone/" + v + "/" + n
        tx = {"s": t("stone"), "t": t("top"), "r": t("trim"), "p": t("panel"), "g": t("glyph"), "c": t("crystal"),
              "b": t("basin"), "w": t("pool"), "e": t("pearl"), "particle": t("stone")}
        models = os.path.join(ROOT, "models", "item", "waystone", v)

        obelisk = {"textures": tx, "elements": [
            box([1, 0, 1], [15, 2, 15], "#r", "trim", "#t"),
            box([2, 2, 2], [14, 4, 14], "#s", "stone", "#t"),
            box([3, 4, 3], [13, 20, 13], "#s", "stone", faces=("north", "south", "east", "west")),
            box([3, 20, 3], [13, 21, 13], "#r", "trim", "#t"),
            box([4, 21, 4], [12, 27, 12], "#s", "stone", faces=("north", "south", "east", "west")),
            box([4, 27, 4], [12, 28, 12], "#r", "trim", "#t"),
        ] + panels(0.01, "#p", y0=6, y1=19)}
        write(os.path.join(models, "obelisk.json"), obelisk)
        glow = {"textures": tx, "elements": [
            box([5, 28, 5], [11, 30, 11], "#c", "trim", shade=False),
            box([6, 30, 6], [10, 31, 10], "#c", "trim", shade=False),
            box([7, 31, 7], [9, 32, 9], "#c", "trim", shade=False),
        ] + panels(0.02, "#g", y0=6, y1=19)}
        write(os.path.join(models, "obelisk_glow.json"), glow)

        altar = {"textures": tx, "elements": [
            box([1, 0, 1], [15, 2, 15], "#r", "trim", "#t"),
            box([2, 2, 2], [14, 3, 14], "#s", "stone", "#t"),
            box([5, 3, 5], [11, 9, 11], "#s", "stone", faces=("north", "south", "east", "west")),
            box([4, 9, 4], [12, 10, 12], "#r", "trim"),
            box([3, 10, 3], [13, 12, 13], "#s", "stone", "#b"),
            # the column's rune, facing away from the stone (north in the model, turned with the block)
            plane([5, 4, 4.99], [11, 9, 4.99], "north", "#p", [5, 6, 11, 11]),
        ]}
        write(os.path.join(models, "altar.json"), altar)
        altar_glow = {"textures": tx, "elements": [
            plane([5, 12.01, 5], [11, 12.01, 11], "up", "#w", [5, 5, 11, 11]),
            plane([6, 5, 4.98], [10, 8, 4.98], "north", "#g", [6, 7, 10, 10]),
        ]}
        write(os.path.join(models, "altar_glow.json"), altar_glow)
        # A round pearl of voxels: a 3 px core with a 2 px cap on each side (4 px across, no corners)
        pearl_model = {"textures": tx, "elements": [
            {"from": [6.5, 6.5, 6.5], "to": [9.5, 9.5, 9.5], "shade": False,
             "faces": {f: {"texture": "#e", "uv": [4, 4, 7, 7]} for f in FACES}},
            box([7, 7, 6], [9, 9, 10], "#e", "trim", shade=False),
            box([6, 7, 7], [10, 9, 9], "#e", "trim", shade=False),
            box([7, 6, 7], [9, 10, 9], "#e", "trim", shade=False),
        ]}
        write(os.path.join(models, "pearl.json"), pearl_model)

        for name in ("obelisk", "obelisk_glow", "altar", "altar_glow", "pearl"):
            write(os.path.join(ROOT, "items", "waystone", v, name + ".json"),
                  {"model": {"type": "minecraft:model", "model": "libreria:item/waystone/" + v + "/" + name}})
    print("waystone v2: " + ", ".join(PALETTES))


if __name__ == "__main__":
    main()
