"""The grand waystone for the game: grand.shrine5 (the plinth a whole block, the heart apart) as item models, shown by
display entities. Writes under waystones/src/main/resources/assets/libreria/:

  models/item/waystone/grand/{light,dark}.json  the stone (everything that doesn't shine), a quarter size: shown 4x
  models/item/waystone/grand/shine.json         what shines (runes, pool, the crystals on the roof), tinted by the dye
  models/item/waystone/grand/heart.json         the heart crystal on four crossed planes (it turns)
  models/item/waystone/grand/shards.json        the three shards round it (they circle: the whole model turns)
  items/waystone/grand/*.json                   (the shining ones with a dye tint)
  textures/block/waystone/shine/heart_{crystal,shard,shard_small}.png
  models/item/waystone_heart.json, items/waystone_heart.json, textures/item/waystone_heart.png (the item)

In the game: the holder stands at the middle of the waystone's own block (the floor's top); the stone and shine models
are scaled 4x and moved up {STATIC_Y} blocks, the heart and shards (1x) up {HEART_Y}. Design pixels: x, z 0..80 with the
waystone's block at 32..48; y from the waystone's block's bottom (the sculk floor's top) up. The plinth is half a block
(real slabs under it, so you step onto it), the sculk floor below it in the ground.

python3 scripts/waystones/grand_export.py
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import grand  # noqa: E402
import heart  # noqa: E402

ROOT = grand.ASSETS
MODELS = os.path.join(ROOT, "models", "item", "waystone", "grand")
ITEMS = os.path.join(ROOT, "items", "waystone", "grand")
SHINE_TEX = os.path.join(ROOT, "textures", "block", "waystone", "shine")

SCALE = 4
# a design point -> model units (quarter size): x, z round the middle, y from the waystone's block's bottom at -16
def m_xyz(p):
    x, y, z = p
    return [round((x - 40) / SCALE + 8, 5), round(y / SCALE - 16, 5), round((z - 40) / SCALE + 8, 5)]

STATIC_Y = 5.5    # display translation (blocks) for the 4x models, from the holder at the middle of the waystone's block
HEART_Y = 3.125   # the heart's middle: design y 58

# the pixel-art textures: their size in texels (the model's uv is 0..16 over the whole picture)
SPRITES = {
    "x:crystal": ("heart_crystal", heart.crystal, (16, 48)),
    "x:shard": ("heart_shard", heart.shard, (16, 16)),
    "x:shard_small": ("heart_shard_small", heart.shard_small, (16, 16)),
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, separators=(",", ":"))


def var(key):
    """The model's texture variable for a design texture key."""
    if key.startswith("@"):
        return key[1:]
    if key.startswith("v:"):
        return "v_" + key[2:]
    if key.startswith("x:"):
        return key[2:]
    return key


def texture_of(key, variant):
    if key in ("s", "t", "r", "p", "b"):
        return "libreria:block/waystone/%s/%s" % (variant, {"s": "stone", "t": "top", "r": "trim", "p": "panel", "b": "basin"}[key])
    if key in ("g", "c", "w", "u"):
        return "libreria:block/waystone/shine/" + {"g": "glyph", "c": "crystal", "w": "pool", "u": "rune"}[key]
    if key.startswith("x:"):
        return "libreria:block/waystone/shine/" + SPRITES[key][0]
    return "minecraft:block/" + grand.vanilla(variant, key)


def uv_of(key, uv):
    """A texel window -> the model's uv (0..16 over the whole picture)."""
    if key in SPRITES:
        w, h = SPRITES[key][2]
        return [round(uv[0] * 16 / w, 4), round(uv[1] * 16 / h, 4), round(uv[2] * 16 / w, 4), round(uv[3] * 16 / h, 4)]
    return [round(v, 4) for v in uv]


def flat(face, tex, tint, xyz=m_xyz):
    """An axis-aligned face -> an element of no thickness with that one face."""
    a, b = xyz(face["a"]), xyz(face["b"])
    frm = [min(a[i], b[i]) for i in range(3)]
    to = [max(a[i], b[i]) for i in range(3)]
    f = {"texture": "#" + var(tex), "uv": uv_of(tex, face["uv"])}
    if tint:
        f["tintindex"] = 0
    return {"from": frm, "to": to, "shade": bool(face["shade"]), "faces": {face["face"]: f}}


def plane_of(pts, tex, uv, tint, xyz=m_xyz):
    """A quad pair (an upright sprite plane, seen from both sides) -> one element: along x (turned 0 or +-45 about y) or
    along z, its two faces."""
    (x0, y1, z0), (x1, _, z1), (_, y0, _), _ = pts
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    w = math.hypot(x1 - x0, z1 - z0)
    ang = math.degrees(math.atan2(z1 - z0, x1 - x0)) % 180
    ang = round(ang / 45) * 45 % 180
    f = {"texture": "#" + var(tex), "uv": uv_of(tex, uv)}
    if tint:
        f["tintindex"] = 0
    if ang == 90:
        a, b = xyz((cx, y0, cz - w / 2)), xyz((cx, y1, cz + w / 2))
        el = {"from": [a[0], a[1], a[2]], "to": [a[0], b[1], b[2]], "shade": False, "faces": {"east": dict(f), "west": dict(f)}}
    else:
        a, b = xyz((cx - w / 2, y0, cz)), xyz((cx + w / 2, y1, cz))
        el = {"from": [a[0], a[1], a[2]], "to": [b[0], b[1], a[2]], "shade": False, "faces": {"north": dict(f), "south": dict(f)}}
        if ang in (45, 135):
            o = xyz((cx, (y0 + y1) / 2, cz))
            # minecraft turns +45 from +x towards -z: a plane running to +x +z is -45
            el["rotation"] = {"origin": o, "axis": "y", "angle": -45 if ang == 45 else 45}
    return el


AXIS = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}
OUT = {"north": -1, "south": 1, "east": 1, "west": -1, "up": 1, "down": -1}


def solid(b):
    return b["kind"] in ("stone", "trim", "local") and not b["tint"]


def samples(face):
    """The middle and the corners (a little inside) of an axis-aligned face."""
    ax = AXIS[face["face"]]
    other = [i for i in range(3) if i != ax]
    lo = [min(face["a"][i], face["b"][i]) for i in other]
    hi = [max(face["a"][i], face["b"][i]) for i in other]
    pts = []
    for u in (lo[0] + 0.05, (lo[0] + hi[0]) / 2, hi[0] - 0.05):
        for v in (lo[1] + 0.05, (lo[1] + hi[1]) / 2, hi[1] - 0.05):
            p = [0.0, 0.0, 0.0]
            p[ax] = face["a"][ax]
            p[other[0]], p[other[1]] = u, v
            pts.append(p)
    return pts


def inside(p, b, eps=0.001):
    return all(b["frm"][i] + eps < p[i] < b["to"][i] - eps for i in range(3))


def hidden(face, index, boxes):
    """Behind other stone (every sample just in front of it is inside another box), or the same face of a box drawn later
    lies over it."""
    ax, out = AXIS[face["face"]], OUT[face["face"]]
    pts = samples(face)
    front = [p[:ax] + [p[ax] + out * 0.02] + p[ax + 1:] for p in pts]
    if all(any(j != index and solid(b) and inside(q, b) for j, b in enumerate(boxes)) for q in front):
        return True
    for j in range(index + 1, len(boxes)):
        b = boxes[j]
        if not solid(b) or face["face"] not in b["faces"]:
            continue
        plane = b["to"][ax] if out > 0 else b["frm"][ax]
        if abs(plane - face["a"][ax]) > 0.005:
            continue
        if all(all(b["frm"][i] - 0.001 <= p[i] <= b["to"][i] + 0.001 for i in range(3) if i != ax) for p in pts):
            return True
    return False


def elements(design, want_tint, xyz=m_xyz):
    out = []
    seen_quads = set()
    boxes = design.boxes
    for index, b in enumerate(boxes):
        assert not b.get("move"), "turned boxes can't be item model elements"
        for face in grand.faces_of(b):
            tint = bool(face["tint"])
            if tint != want_tint:
                continue
            if face["face"] != "quad" and not face.get("plane") and b["plane"] is None and solid(b) and hidden(face, index, boxes):
                continue
            if face["face"] == "quad":
                k = id(b)
                if k in seen_quads:
                    continue
                seen_quads.add(k)
                out.append(plane_of(b["pts"], b["tex"], b["uv"], tint, xyz))
            else:
                out.append(flat(face, face["tex"], tint, xyz))
    return out


def textures(els, variant):
    keys = set()
    for e in els:
        for f in e["faces"].values():
            keys.add(f["texture"][1:])
    back = {}
    for b in grand.shrine5("slab", heart=False).boxes:
        for k in (b["tex"], b["top"]):
            if k:
                back[var(k)] = k
    for k in SPRITES:
        back[var(k)] = k
    tx = {k: texture_of(back[k], variant) for k in sorted(keys)}
    tx["particle"] = texture_of("@B", variant) if variant else texture_of("g", "light")
    return tx


def check_range(name, els):
    for e in els:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (name, e)


def coplanar(design):
    """Faces in the same plane, the same way round and overlapping (they'd flicker): reported, for the design to fix."""
    faces = []
    for index, b in enumerate(design.boxes):
        for f in grand.faces_of(b):
            if f["face"] == "quad":
                continue
            if b["plane"] is None and solid(b) and hidden(f, index, design.boxes):
                continue
            axis = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[f["face"]]
            other = [i for i in range(3) if i != axis]
            lo = [min(f["a"][i], f["b"][i]) for i in other]
            hi = [max(f["a"][i], f["b"][i]) for i in other]
            faces.append((f["face"], round(f["a"][axis], 3), lo, hi, f["tex"], bool(f["tint"])))
    by = {}
    for fc in faces:
        by.setdefault((fc[0], fc[1]), []).append(fc)
    found = []
    for (face, at), group in by.items():
        for i in range(len(group)):
            for j in range(i + 1, len(group)):
                a, b = group[i], group[j]
                if all(min(a[3][k], b[3][k]) - max(a[2][k], b[2][k]) > 0.01 for k in range(2)):
                    if a[4] != b[4] or a[5] != b[5]:
                        found.append((face, at, a[4], b[4], a[2], a[3], b[2], b[3]))
    return found


def heart_models():
    """The heart (1x, its middle at the model's middle) and the three shards round it."""
    def one(x):
        return [round(x[0], 4), round(x[1], 4), round(x[2], 4)]
    hd = grand.Design()
    grand.sprite(hd, 8, 8, 8, 15, 38, "x:crystal", planes=4, spin=0)
    sd = grand.Design()
    for ang, dy in ((20, 4), (140, -6), (260, 10)):
        a = math.radians(ang)
        grand.sprite(sd, 8 + math.cos(a) * 15, 8 + dy, 8 + math.sin(a) * 15, 7, 15, "x:shard", planes=2, spin=0)
    return elements(hd, True, one), elements(sd, True, one)


def icon():
    """The Waystone Heart item: a small crystal in the heart's purple, with two glints."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    c = heart.crystal(9, 16, 5, 11)
    tint = (199, 155, 240)
    px = c.load()
    for y in range(c.height):
        for x in range(c.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = tuple(min(255, int(r * t / 255 * 1.08)) for t in tint) + (255,)
    im.paste(c, (3, 0), c)
    for x, y in ((13, 3), (2, 12)):
        im.putpixel((x, y), (255, 240, 255, 255))
    return im


def main():
    os.makedirs(SHINE_TEX, exist_ok=True)
    for key, (name, fn, (w, h)) in SPRITES.items():
        art = fn()
        pad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        pad.paste(art, (0, 0))
        pad.save(os.path.join(SHINE_TEX, name + ".png"))

    design = grand.shrine5("slab", heart=False)
    for issue in coplanar(design):
        print("coplanar:", issue)
    stone = elements(design, False)
    shine = elements(design, True)
    check_range("stone", stone)
    check_range("shine", shine)
    for variant in ("light", "dark"):
        write(os.path.join(MODELS, variant + ".json"), {"textures": textures(stone, variant), "elements": stone})
        write(os.path.join(ITEMS, variant + ".json"), {"model": {"type": "minecraft:model", "model": "libreria:item/waystone/grand/" + variant}})
    write(os.path.join(MODELS, "shine.json"), {"textures": textures(shine, None), "elements": shine})
    h, s = heart_models()
    check_range("heart", h)
    check_range("shards", s)
    write(os.path.join(MODELS, "heart.json"), {"textures": textures(h, None), "elements": h})
    write(os.path.join(MODELS, "shards.json"), {"textures": textures(s, None), "elements": s})
    for name in ("shine", "heart", "shards"):
        write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "libreria:item/waystone/grand/" + name,
                                                               "tints": [{"type": "minecraft:dye", "default": -1}]}})
    icon().save(os.path.join(ROOT, "textures", "item", "waystone_heart.png"))
    write(os.path.join(ROOT, "models", "item", "waystone_heart.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "libreria:item/waystone_heart"}})
    write(os.path.join(ROOT, "items", "waystone_heart.json"), {"model": {"type": "minecraft:model", "model": "libreria:item/waystone_heart"}})
    print("grand waystone: %d stone faces, %d shining, heart %d, shards %d" % (len(stone), len(shine), len(h), len(s)))


if __name__ == "__main__":
    main()
