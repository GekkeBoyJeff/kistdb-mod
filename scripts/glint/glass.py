"""Seamless glass, and connected glass for OptiFine and Continuity.

Everyone: glass, tinted glass, the 16 stained glasses and their panes get a texture without a frame, so a wall of glass
reads as one window instead of a grid. The colour and see-through-ness come from vanilla (its most common inner and
border colours); the pixels are our own: a flat fill and a few soft glints. Only the placed blocks change (the block
models point at the new textures): the items in your inventory and the break particles keep the vanilla look.

With OptiFine or Continuity (Iris users usually have Continuity): real connected textures in OptiFine's format
(method=ctm, 47 tiles), a frame round the outside of the whole window, inner corners included. They match only our own
textures, so with the setting off (the off-pack puts the vanilla block models back) nothing of ours applies.

Called from generate.py (which wipes glint/off first); writes:
  assets/minecraft/textures/block/libreria_<glass>.png
  assets/minecraft/models/block/<glass>.json, <glass>_pane_*.json        (the placed blocks)
  assets/libreria/models/block/glass_item/<glass>.json                  (vanilla look for the item)
  assets/minecraft/optifine/ctm/libreria/<glass>/                        (47 tiles + properties)
  glint/off/glass.zip                                                    (the vanilla block models)
"""
import json
import os
import shutil
import zipfile
from collections import Counter

from PIL import Image

COLOURS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
           'blue', 'brown', 'green', 'red', 'black']
GLASSES = ['glass', 'tinted_glass'] + [f'{c}_stained_glass' for c in COLOURS]
PANE_PARTS = ['noside', 'noside_alt', 'post', 'side', 'side_alt']
# Two soft glints top left, one bottom right: k 0 is the bright one, k 1 the soft one
GLINT = [((3, 3), 0), ((4, 2), 0), ((2, 4), 1), ((5, 1), 1), ((11, 13), 0), ((12, 12), 1), ((10, 14), 1)]

# Continuity's table (the same as OptiFine's template): connection bits -> tile. Bits, as seen on the face:
#   128 64  32
#     1  *  16
#     2  4   8
# A corner bit only counts when both edges next to it connect.
SPRITE_INDEX_MAP = [
    0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
    1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
    0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
    1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
    36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
    16, 18, 16, 18, 6, 46, 6, 21, 16, 18, 16, 18, 28, 9, 28, 22,
    36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
    37, 40, 37, 40, 30, 8, 30, 34, 37, 40, 37, 40, 25, 23, 25, 45,
    0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
    1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
    0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
    1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
    36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
    16, 42, 16, 42, 6, 20, 6, 10, 16, 42, 16, 42, 28, 35, 28, 44,
    36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
    37, 38, 37, 38, 30, 11, 30, 32, 37, 38, 37, 38, 25, 33, 25, 26,
]
LEFT, DOWN_LEFT, DOWN, DOWN_RIGHT, RIGHT, UP_RIGHT, UP, UP_LEFT = 1, 2, 4, 8, 16, 32, 64, 128


def tex(name):
    return f'libreria_{name}'


class Palette:
    """The colours of one glass, read from vanilla: the inside, each side of the frame, the four corners."""

    def __init__(self, vanilla, name):
        v = Image.open(os.path.join(vanilla, f'textures/block/{name}.png')).convert('RGBA')
        px = lambda x, y: v.getpixel((x, y))  # noqa: E731
        inside = [px(x, y) for y in range(1, 15) for x in range(1, 15) if px(x, y)[3]]
        self.clear = name == 'glass'
        # clear glass is see-through inside: only the glints show
        self.inner = Counter(inside).most_common(1)[0][0] if inside and not self.clear else (0, 0, 0, 0)
        side = lambda pts: Counter(px(x, y) for x, y in pts).most_common(1)[0][0]  # noqa: E731
        self.top = side([(x, 0) for x in range(1, 15)])
        self.bottom = side([(x, 15) for x in range(1, 15)])
        self.left = side([(0, y) for y in range(1, 15)])
        self.right = side([(15, y) for y in range(1, 15)])
        self.corner = {UP_LEFT: px(0, 0), UP_RIGHT: px(15, 0), DOWN_LEFT: px(0, 15), DOWN_RIGHT: px(15, 15)}
        if self.clear:
            self.glints = [(208, 234, 233, 200), (168, 208, 217, 150)]
        else:
            hi = self.top
            self.glints = [hi, self.inner[:3] + ((self.inner[3] + hi[3]) // 2,)]


def seamless(p):
    out = Image.new('RGBA', (16, 16), p.inner)
    for (x, y), k in GLINT:
        out.putpixel((x, y), p.glints[k])
    return out


def tile(p, mask):
    """The seamless texture with a frame on every side that doesn't connect, and a corner pixel where two sides connect
    but the block between them (diagonally) doesn't."""
    img = seamless(p)
    if not mask & UP:
        for x in range(16):
            img.putpixel((x, 0), p.top)
    if not mask & DOWN:
        for x in range(16):
            img.putpixel((x, 15), p.bottom)
    if not mask & LEFT:
        for y in range(16):
            img.putpixel((0, y), p.left)
    if not mask & RIGHT:
        for y in range(16):
            img.putpixel((15, y), p.right)
    # A corner of the frame: both sides open (outside corner), or both connected round a missing block (inside
    # corner). With one side open, that side's line simply runs on into the next block.
    for corner, (a, b), xy in ((UP_LEFT, (UP, LEFT), (0, 0)), (UP_RIGHT, (UP, RIGHT), (15, 0)),
                               (DOWN_LEFT, (DOWN, LEFT), (0, 15)), (DOWN_RIGHT, (DOWN, RIGHT), (15, 15))):
        ca, cb = bool(mask & a), bool(mask & b)
        if (not ca and not cb) or (ca and cb and not mask & corner):
            img.putpixel(xy, p.corner[corner])
    return img


def tiles(p):
    """The 47 tiles in OptiFine's order. Every connection mask that maps to a tile must draw the same picture."""
    out = {}
    for mask in range(256):
        t = SPRITE_INDEX_MAP[mask]
        img = tile(p, mask)
        if t in out:
            assert out[t].tobytes() == img.tobytes(), f'tile {t}: mask {mask} draws something else'
        else:
            out[t] = img
    assert sorted(out) == list(range(47))
    return [out[t] for t in range(47)]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, separators=(',', ':'))


def sprite(s):
    return {'force_translucent': True, 'sprite': s}


def block_models(vanilla):
    """Our placed-block models (path -> json) and the vanilla ones they replace (for the off-pack)."""
    ours, theirs = {}, {}
    for g in GLASSES:
        path = f'assets/minecraft/models/block/{g}.json'
        theirs[path] = json.load(open(os.path.join(vanilla, f'models/block/{g}.json')))
        ours[path] = {'parent': 'minecraft:block/cube_all',
                      'textures': {'all': sprite(f'minecraft:block/{tex(g)}'), 'particle': f'minecraft:block/{g}'}}
        if g == 'tinted_glass':
            continue
        for part in PANE_PARTS:
            path = f'assets/minecraft/models/block/{g}_pane_{part}.json'
            van = json.load(open(os.path.join(vanilla, f'models/block/{g}_pane_{part}.json')))
            theirs[path] = van
            mine = json.loads(json.dumps(van))
            mine['textures']['pane'] = sprite(f'minecraft:block/{tex(g)}')
            mine['textures']['particle'] = f'minecraft:block/{g}'
            ours[path] = mine
    return ours, theirs


# The glass items keep the vanilla look: their item definitions point at these instead of the (changed) block models
ITEM_SWAP = {f'minecraft:block/{g}': f'libreria:block/glass_item/{g}' for g in GLASSES}


def swap_items(node):
    """An item definition with the glass block models swapped for the vanilla-looking copies."""
    if isinstance(node, dict):
        return {k: swap_items(v) for k, v in node.items()}
    if isinstance(node, list):
        return [swap_items(v) for v in node]
    if isinstance(node, str):
        return ITEM_SWAP.get(node, node)
    return node


def ctm_properties(glass, block, pane):
    lines = ['method=ctm', f'matchTiles={tex(glass)}', f'matchBlocks={block}', 'tiles=0-46']
    if pane:
        lines.append('faces=sides')
    lines.append('connect=block')
    return '\n'.join(lines) + '\n'


def build(vanilla, out, pack_format):
    assets = os.path.join(out, 'assets')
    for sub in ('minecraft/optifine/ctm/libreria', 'libreria/models/block/glass_item'):
        shutil.rmtree(os.path.join(assets, sub), ignore_errors=True)
    tex_dir = os.path.join(assets, 'minecraft/textures/block')
    if os.path.isdir(tex_dir):
        for f in os.listdir(tex_dir):
            if f.startswith('libreria_'):
                os.remove(os.path.join(tex_dir, f))
    os.makedirs(tex_dir, exist_ok=True)

    for g in GLASSES:
        p = Palette(vanilla, g)
        seamless(p).save(os.path.join(tex_dir, tex(g) + '.png'), optimize=True)
        mcmeta = os.path.join(vanilla, f'textures/block/{g}.png.mcmeta')
        if os.path.exists(mcmeta):
            shutil.copyfile(mcmeta, os.path.join(tex_dir, tex(g) + '.png.mcmeta'))
        d = os.path.join(assets, 'minecraft/optifine/ctm/libreria', g)
        os.makedirs(d, exist_ok=True)
        for i, img in enumerate(tiles(p)):
            img.save(os.path.join(d, f'{i}.png'), optimize=True)
        with open(os.path.join(d, f'{g}.properties'), 'w') as f:
            f.write(ctm_properties(g, g, False))
        if g != 'tinted_glass':
            with open(os.path.join(d, f'{g}_pane.properties'), 'w') as f:
                f.write(ctm_properties(g, g + '_pane', True))
        write_json(os.path.join(assets, f'libreria/models/block/glass_item/{g}.json'),
                   json.load(open(os.path.join(vanilla, f'models/block/{g}.json'))))

    ours, theirs = block_models(vanilla)
    for path, model in ours.items():
        write_json(os.path.join(out, path), model)
    off = os.path.join(out, 'glint/off')
    os.makedirs(off, exist_ok=True)
    mcmeta = {'pack': {'description': 'Libreria Glint – seamless glass off', 'min_format': pack_format, 'max_format': 1000}}
    with zipfile.ZipFile(os.path.join(off, 'glass.zip'), 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('pack.mcmeta', json.dumps(mcmeta))
        for path, model in theirs.items():
            z.writestr(path, json.dumps(model, separators=(',', ':')))
    return len(GLASSES), len(ours)
