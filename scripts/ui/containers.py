"""Minecraft's own screens (chests, furnaces, the crafting table, anvils, villagers, the inventory…) in Libreria's
look: the same dark panel as Libreria's windows, a thin light edge, and dark slots with a faint rim. Only the colours
change, every pixel stays where it was, so the screens work exactly as before. Arrows, flames and other drawings on
the panel (grey in Minecraft, like the slots) are found by their shape and kept light, so they still show.

Minecraft draws a screen's title in dark grey: the server sends those titles in a light colour instead
(OpenScreenTitleMixin), and the few words the game writes itself get a light colour in the language files.

The creative inventory and the horse's screen keep Minecraft's grey (their words come from the game itself).

    python3 scripts/ui/containers.py [vanilla_textures_gui] [out_dir]
"""
import os
import sys

from PIL import Image

VANILLA = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/ci-out/vanilla/assets/minecraft/textures/gui'
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '../../core/src/main/resources')

PANEL_TOP, PANEL_BOTTOM = (22, 24, 30), (13, 14, 18)
EDGE = (44, 47, 55)        # Minecraft's white bevel, and a slot's light corner: a faint light line
SHADE = (8, 9, 12)         # the dark bevel
OUTLINE = (62, 66, 76)     # the outline: a thin light edge, like Libreria's panels
SLOT = (7, 8, 11)          # inside a slot
SLOT_EDGE = (40, 42, 48)   # a slot's dark corner: the same faint rim all round
DRAWING = (96, 101, 114)   # arrows, flames, plus signs… drawn in the slot grey: kept visible
DISABLED = {(160, 160, 160): (70, 74, 84), (113, 113, 113): (34, 36, 42)}
GREY = {(198, 198, 198), (255, 255, 255), (85, 85, 85), (0, 0, 0), (139, 139, 139), (55, 55, 55)} | set(DISABLED)
SKIP_FILES = {'slot_highlight_back.png', 'slot_highlight_front.png', 'gamemode_switcher.png', 'horse.png'}
SKIP_DIRS = {'creative_inventory', 'horse'}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def share(im):
    n = grey = 0
    w, h = im.size
    px = im.load()
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if p[3] == 0:
                continue
            n += 1
            if p[:3] in GREY:
                grey += 1
    return grey / n if n else 0


def slot_cells(im):
    """The slot-grey pixels that are inside a slot (or a channel of slots, like a brewing stand's tubes): walking from
    them, you reach a slot's dark edge (left, up) or its light edge (right, down) before the panel. An arrow or a
    flame drawn on the panel meets the panel itself on every side."""
    w, h = im.size
    px = im.load()
    G = (139, 139, 139)

    def stop(x, y, dx, dy):
        while 0 <= x < w and 0 <= y < h and px[x, y][3] and px[x, y][:3] == G:
            x += dx
            y += dy
        return px[x, y][:3] if 0 <= x < w and 0 <= y < h and px[x, y][3] else None

    inside = set()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0 or px[x, y][:3] != G:
                continue
            across = stop(x, y, -1, 0) == (55, 55, 55) or stop(x, y, 1, 0) == (255, 255, 255)
            down = stop(x, y, 0, -1) == (55, 55, 55) or stop(x, y, 0, 1) == (255, 255, 255)
            if across and down:
                inside.add((x, y))
    return inside


def recolor(im):
    im = im.convert('RGBA')
    px = im.load()
    w, h = im.size
    slots = slot_cells(im)
    # the panel's own height (screens are drawn from the top of the texture)
    tall = max(1, max((y for y in range(h) for x in range(0, w, 8) if px[x, y][3]), default=h))
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            c = (r, g, b)
            if c == (198, 198, 198):
                out = lerp(PANEL_TOP, PANEL_BOTTOM, min(1, y / tall))
            elif c == (255, 255, 255):
                out = EDGE
            elif c == (85, 85, 85):
                out = SHADE
            elif c == (0, 0, 0):
                out = OUTLINE
            elif c == (139, 139, 139):
                out = SLOT if (x, y) in slots else DRAWING
            elif c == (55, 55, 55):
                out = SLOT_EDGE
            elif c in DISABLED:
                out = DISABLED[c]
            else:
                continue
            px[x, y] = out + (a,)
    return im


def main():
    done = 0
    for sub, need in (('container', 0.0), ('sprites/container', 0.6)):
        base = os.path.join(VANILLA, sub)
        for root, dirs, files in os.walk(base):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for f in sorted(files):
                if not f.endswith('.png') or f in SKIP_FILES:
                    continue
                src = os.path.join(root, f)
                rel = os.path.relpath(src, VANILLA)
                im = Image.open(src).convert('RGBA')
                s = share(im)
                if s == 0 or s < need:
                    continue
                dst = os.path.join(OUT, 'assets/minecraft/textures/gui', rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                recolor(im).save(dst, optimize=True)
                if os.path.exists(src + '.mcmeta'):
                    with open(src + '.mcmeta') as a, open(dst + '.mcmeta', 'w') as b:
                        b.write(a.read())
                done += 1
    src = os.path.join(VANILLA, 'recipe_book.png')
    if os.path.exists(src):
        recolor(Image.open(src)).save(os.path.join(OUT, 'assets/minecraft/textures/gui/recipe_book.png'), optimize=True)
        done += 1
    print(done, 'pictures in the Libreria look')


if __name__ == '__main__':
    main()
