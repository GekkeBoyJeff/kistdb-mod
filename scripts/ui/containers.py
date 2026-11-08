"""Minecraft's own screens (chests, furnaces, the crafting table, anvils, villagers, the inventory…) in Libreria's
look: warm parchment with a bronze edge and walnut slots, instead of grey. Only the colours change, every pixel stays
where it was, so the screens work exactly as before; Minecraft draws their titles in dark grey, so the panel stays
light enough to read them.

Recolours every picture under textures/gui/container and the container sprites that are drawn in the grey palette.

    python3 scripts/ui/containers.py [vanilla_textures_gui] [out_dir]
"""
import os
import random
import sys

from PIL import Image

VANILLA = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/ci-out/vanilla/assets/minecraft/textures/gui'
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '../../core/src/main/resources')

MAP = {
    (198, 198, 198): (214, 197, 163),  # the panel: warm parchment
    (255, 255, 255): (246, 236, 212),  # its light edge
    (85, 85, 85): (134, 100, 60),      # its shaded edge: bronze
    (0, 0, 0): (58, 38, 20),           # the outline: dark walnut
    (139, 139, 139): (150, 128, 96),   # a slot
    (55, 55, 55): (84, 62, 38),        # a slot's shadow
    (160, 160, 160): (170, 150, 116),  # disabled bits
    (113, 113, 113): (122, 100, 70),
}
# Sprites drawn on top of what we recolour already, or coloured on purpose: left alone
SKIP = {'slot_highlight_back.png', 'slot_highlight_front.png'}


def share(im):
    """How much of the opaque picture is in the grey palette."""
    px = im.getdata() if not hasattr(im, 'get_flattened_data') else im.get_flattened_data()
    n = grey = 0
    for p in px:
        if p[3] == 0:
            continue
        n += 1
        if p[:3] in MAP:
            grey += 1
    return grey / n if n else 0


def recolor(im, seed):
    im = im.convert('RGBA')
    px = im.load()
    rnd = random.Random(seed)
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            c = MAP.get((r, g, b))
            if c is None:
                continue
            if (r, g, b) == (198, 198, 198):
                # a faint grain, like paper fibres
                n = rnd.choice((0, 0, 0, 0, -3, 3, -5, 4))
                c = tuple(max(0, min(255, v + n)) for v in c)
            px[x, y] = c + (a,)
    return im


def main():
    done = 0
    for sub, need in (('container', 0.0), ('sprites/container', 0.6)):
        base = os.path.join(VANILLA, sub)
        for root, _, files in os.walk(base):
            for f in sorted(files):
                if not f.endswith('.png') or f in SKIP:
                    continue
                src = os.path.join(root, f)
                rel = os.path.relpath(src, VANILLA)
                im = Image.open(src).convert('RGBA')
                if share(im) < need or share(im) == 0:
                    continue
                dst = os.path.join(OUT, 'assets/minecraft/textures/gui', rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                recolor(im, hash(rel) & 0xFFFF).save(dst, optimize=True)
                mcmeta = src + '.mcmeta'
                if os.path.exists(mcmeta):
                    with open(mcmeta) as a, open(dst + '.mcmeta', 'w') as b:
                        b.write(a.read())
                done += 1
    # The recipe book beside the inventory and the crafting table
    src = os.path.join(VANILLA, 'recipe_book.png')
    if os.path.exists(src):
        recolor(Image.open(src), 1).save(os.path.join(OUT, 'assets/minecraft/textures/gui/recipe_book.png'), optimize=True)
        done += 1
    print(done, 'pictures in the Libreria look')


if __name__ == '__main__':
    main()
