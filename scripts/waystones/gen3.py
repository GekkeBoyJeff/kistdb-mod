"""Waystone, third design: three blocks high. A dressed-stone body with an inset rune panel on each side, then an open
frame of four corner posts with a rune crystal floating and turning inside it, then a slimmer top with a crystal
capstone. In front: the altar (stepped base, column, basin with a pool, a floating pearl).

Two stones (light and dark). Everything that shines (runes, the floating rune, the capstone, the pool, the pearl) is
drawn in grey and coloured in the game by the dye the stone was given (the item's dyed colour, a "dye" tint), so every
colour works without a picture per colour; whether it glows is up to the display (full light or the world's light).

Models above y 32 can't be: the top of the obelisk is its own model, shown a block higher.
Every face is one texture pixel per model pixel.

Writes under waystones/src/main/resources/assets/libreria/:
  textures/block/waystone/{light,dark}/{stone,top,trim,panel,basin}.png, textures/block/waystone/shine/*.png
  models/item/waystone/{light,dark}/{low,top,altar}.json, models/item/waystone/shine/{low,top,rune,altar,pearl}.json
  items/waystone/... (the shine ones with a dye tint)

Run: python3 scripts/waystones/gen3.py
"""
import json
import os
import random
import shutil

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "waystones", "src", "main", "resources", "assets", "libreria")

STONES = {
    "light": dict(stone=(182, 178, 190), course=(158, 154, 168), joint=(150, 146, 160), lit=(204, 200, 212),
                  trim=(128, 122, 140), trim_lit=(160, 154, 172), trim_dark=(100, 94, 112),
                  panel=(64, 58, 76), panel_dark=(44, 40, 54), panel_lip=(150, 144, 160)),
    "dark": dict(stone=(92, 98, 108), course=(76, 81, 90), joint=(70, 75, 84), lit=(114, 121, 132),
                 trim=(60, 64, 72), trim_lit=(84, 90, 100), trim_dark=(44, 47, 54),
                 panel=(30, 34, 40), panel_dark=(20, 22, 27), panel_lip=(104, 110, 120)),
}


def noise(rng, c, spread):
    return tuple(max(0, min(255, v + rng.randint(-spread, spread))) for v in c) + (255,)


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def grey(v):
    return (v, v, v)


# ---------------------------------------------------------------- stone

def stone(p, rng):
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
    im = img()
    for y in range(16):
        for x in range(16):
            c = p["trim_lit"] if y == 0 else p["trim_dark"] if y == 15 else p["trim"]
            im.putpixel((x, y), noise(rng, c, 3))
    return im


PANEL = (5, 2, 11, 15)


def panel(p, rng):
    im = img()
    x0, y0, x1, y1 = PANEL
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = p["panel_dark"] if (y == y0 or x == x0) else p["panel_lip"] if (y == y1 - 1 or x == x1 - 1) else p["panel"]
            im.putpixel((x, y), noise(rng, c, 2))
    return im


def basin(p, rng):
    im = top(p, rng)
    for y in range(4, 12):
        for x in range(4, 12):
            im.putpixel((x, y), noise(rng, p["panel_dark"] if (y == 4 or x == 4) else p["panel"], 2))
    return im


# ---------------------------------------------------------------- what shines (grey, coloured by the dye)

RUNES = [
    [(6, 3), (9, 3), (7, 4), (8, 4), (6, 5), (9, 5)],
    [(7, 7), (8, 7), (6, 8), (9, 8), (7, 9), (8, 9)],
    [(6, 11), (9, 11), (6, 12), (7, 12), (8, 12), (9, 12), (7, 13), (8, 13)],
]


def glyph():
    im = img()
    for r in RUNES:
        for q in r:
            im.putpixel(q, grey(255) + (255,))
    return im


def crystal(rng):
    """Facets: bright at the top left, darker at the bottom right, a few white sparkles."""
    im = img()
    for y in range(16):
        for x in range(16):
            v = int(250 - (x + y) * 3.2)
            im.putpixel((x, y), noise(rng, grey(v), 5))
    for q in ((3, 2), (9, 5), (5, 11), (12, 9)):
        im.putpixel(q, (255, 255, 255, 255))
    return im


def pool():
    im = img()
    for y in range(5, 11):
        for x in range(5, 11):
            d = max(abs(x - 7.5), abs(y - 7.5)) / 3
            im.putpixel((x, y), grey(int(255 - d * 110)) + (255,))
    return im


def pearl(rng):
    im = img()
    for y in range(16):
        for x in range(16):
            t = min(1.0, ((x - 3) ** 2 + (y - 3) ** 2) ** 0.5 / 14)
            im.putpixel((x, y), noise(rng, grey(int(255 - t * 120)), 2))
    return im


def rune(rng):
    """The floating crystal: bright with a darker rune carved in its middle band (rows 6..8)."""
    im = crystal(rng)
    for q in ((1, 7), (2, 6), (2, 8), (3, 7), (5, 6), (5, 7), (5, 8), (6, 7)):
        im.putpixel(q, grey(110) + (255,))
    return im


# ---------------------------------------------------------------- models

FACES = ("north", "south", "east", "west", "up", "down")


def uv(face, frm, to, kind):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    if face in ("up", "down"):
        return [x0, z0, x1, z1]
    w0, w1 = (x0, x1) if face in ("north", "south") else (z0, z1)
    h = y1 - y0
    if kind == "trim":
        v0 = 0 if h >= 2 else 1
        return [w0, v0, w1, v0 + h]
    return [w0, 0, w1, h]


def box(frm, to, tex, kind="stone", top_tex=None, faces=FACES, shade=True, tint=False):
    out = {}
    for f in faces:
        t = top_tex if (f == "up" and top_tex) else tex
        face = {"texture": t, "uv": uv(f, frm, to, kind)}
        if tint:
            face["tintindex"] = 0
        out[f] = face
    return {"from": frm, "to": to, "shade": shade, "faces": out}


def plane(frm, to, face, tex, window, tint=False):
    f = {"texture": tex, "uv": window}
    if tint:
        f["tintindex"] = 0
    return {"from": frm, "to": to, "shade": False, "faces": {face: f}}


def panels(z_off, tex, tint=False, x0=5, x1=11, y0=6, y1=19, lo=3, hi=13):
    win = list(PANEL)
    return [
        plane([x0, y0, lo - z_off], [x1, y1, lo - z_off], "north", tex, win, tint),
        plane([x0, y0, hi + z_off], [x1, y1, hi + z_off], "south", tex, win, tint),
        plane([lo - z_off, y0, x0], [lo - z_off, y1, x1], "west", tex, win, tint),
        plane([hi + z_off, y0, x0], [hi + z_off, y1, x1], "east", tex, win, tint),
    ]


def posts(y0, y1):
    """The four corner posts of the open frame, from y0 to y1."""
    return [box([x, y0, z], [x + 2, y1, z + 2], "#s") for x in (3, 11) for z in (3, 11)]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)


def item(name, tinted):
    model = {"type": "minecraft:model", "model": "libreria:item/waystone/" + name}
    if tinted:
        # The colour of the dye the waystone was given (the display's dyed colour); white without one
        model["tints"] = [{"type": "minecraft:dye", "default": -1}]
    write(os.path.join(ROOT, "items", "waystone", name + ".json"), {"model": model})


def main():
    rng = random.Random(23)
    # What shines: grey pictures, the dye gives the colour
    shine = os.path.join(ROOT, "textures", "block", "waystone", "shine")
    os.makedirs(shine, exist_ok=True)
    for name, im in (("glyph", glyph()), ("crystal", crystal(rng)), ("pool", pool()), ("pearl", pearl(rng)), ("rune", rune(rng))):
        im.save(os.path.join(shine, name + ".png"))
    st = lambda n: "libreria:block/waystone/shine/" + n
    stx = {"g": st("glyph"), "c": st("crystal"), "w": st("pool"), "e": st("pearl"), "r": st("rune"), "particle": st("crystal")}
    models = os.path.join(ROOT, "models", "item", "waystone")

    write(os.path.join(models, "shine", "low.json"), {"textures": stx, "elements": panels(0.02, "#g", True)})
    # The capstone, in the top model (y - 16)
    write(os.path.join(models, "shine", "top.json"), {"textures": stx, "elements": [
        box([5, 28, 5], [11, 30, 11], "#c", "trim", shade=False, tint=True),
        box([6, 30, 6], [10, 31, 10], "#c", "trim", shade=False, tint=True),
        box([7, 31, 7], [9, 32, 9], "#c", "trim", shade=False, tint=True),
    ]})
    # The floating rune: a crystal 6 px across and 9 high, around the model's centre
    write(os.path.join(models, "shine", "rune.json"), {"textures": stx, "elements": [
        box([7, 3.5, 7], [9, 4.5, 9], "#r", "trim", shade=False, tint=True),
        box([6, 4.5, 6], [10, 6.5, 10], "#r", "trim", shade=False, tint=True),
        {"from": [5, 6.5, 5], "to": [11, 9.5, 11], "shade": False,
         "faces": {f: {"texture": "#r", "uv": [0, 6, 6, 9] if f not in ("up", "down") else [5, 5, 11, 11], "tintindex": 0} for f in FACES}},
        box([6, 9.5, 6], [10, 11.5, 10], "#r", "trim", shade=False, tint=True),
        box([7, 11.5, 7], [9, 12.5, 9], "#r", "trim", shade=False, tint=True),
    ]})
    write(os.path.join(models, "shine", "altar.json"), {"textures": stx, "elements": [
        plane([5, 12.01, 5], [11, 12.01, 11], "up", "#w", [5, 5, 11, 11], True),
        plane([6, 5, 4.98], [10, 8, 4.98], "north", "#g", [6, 7, 10, 10], True),
    ]})
    write(os.path.join(models, "shine", "pearl.json"), {"textures": stx, "elements": [
        {"from": [6.5, 6.5, 6.5], "to": [9.5, 9.5, 9.5], "shade": False,
         "faces": {f: {"texture": "#e", "uv": [4, 4, 7, 7], "tintindex": 0} for f in FACES}},
        box([7, 7, 6], [9, 9, 10], "#e", "trim", shade=False, tint=True),
        box([6, 7, 7], [10, 9, 9], "#e", "trim", shade=False, tint=True),
        box([7, 6, 7], [9, 10, 9], "#e", "trim", shade=False, tint=True),
    ]})
    for name in ("low", "top", "rune", "altar", "pearl"):
        item("shine/" + name, True)

    for v, p in STONES.items():
        tex_dir = os.path.join(ROOT, "textures", "block", "waystone", v)
        os.makedirs(tex_dir, exist_ok=True)
        for name, im in (("stone", stone(p, rng)), ("top", top(p, rng)), ("trim", trim(p, rng)), ("panel", panel(p, rng)),
                         ("basin", basin(p, rng))):
            im.save(os.path.join(tex_dir, name + ".png"))
        t = lambda n: "libreria:block/waystone/" + v + "/" + n
        tx = {"s": t("stone"), "t": t("top"), "r": t("trim"), "p": t("panel"), "b": t("basin"), "particle": t("stone")}
        # Lower two blocks: plinth, body with its rune panels, the ledge, the lower part of the frame
        write(os.path.join(models, v, "low.json"), {"textures": tx, "elements": [
            box([1, 0, 1], [15, 2, 15], "#r", "trim", "#t"),
            box([2, 2, 2], [14, 4, 14], "#s", "stone", "#t"),
            box([3, 4, 3], [13, 20, 13], "#s", "stone", faces=("north", "south", "east", "west")),
            box([2, 20, 2], [14, 22, 14], "#r", "trim", "#t"),
        ] + posts(22, 32) + panels(0.01, "#p")})
        # Third block (drawn 16 lower, shown a block higher): the rest of the frame, the ledge it carries, the top
        write(os.path.join(models, v, "top.json"), {"textures": tx, "elements": posts(16, 18) + [
            box([2, 18, 2], [14, 20, 14], "#r", "trim", "#t"),
            box([4, 20, 4], [12, 27, 12], "#s", "stone", faces=("north", "south", "east", "west")),
            box([3, 27, 3], [13, 28, 13], "#r", "trim", "#t"),
        ]})
        write(os.path.join(models, v, "altar.json"), {"textures": tx, "elements": [
            box([1, 0, 1], [15, 2, 15], "#r", "trim", "#t"),
            box([2, 2, 2], [14, 3, 14], "#s", "stone", "#t"),
            box([5, 3, 5], [11, 9, 11], "#s", "stone", faces=("north", "south", "east", "west")),
            box([4, 9, 4], [12, 10, 12], "#r", "trim"),
            box([3, 10, 3], [13, 12, 13], "#s", "stone", "#b"),
            plane([5, 4, 4.99], [11, 9, 4.99], "north", "#p", [5, 6, 11, 11]),
        ]})
        for name in ("low", "top", "altar"):
            item(v + "/" + name, False)

    # The second design's mock-up pictures are no longer needed
    for old in ("amethyst", "ender"):
        for sub in (("textures", "block", "waystone", old), ("models", "item", "waystone", old), ("items", "waystone", old)):
            shutil.rmtree(os.path.join(ROOT, *sub), ignore_errors=True)
    print("waystone v3: light, dark + shine")


if __name__ == "__main__":
    main()
