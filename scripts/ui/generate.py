#!/usr/bin/env python3
"""
Generates the Libreria UI assets for the server resource pack (core module): an "adventure" tooltip panel, menu frames
per tool, little icons and progress pips. Everything is drawn here from scratch (no vanilla pixels) and lives in the
font libreria:ui, so the server can use it in text (tooltip text displays, container titles).

Also writes libreria/ui_glyphs.json: glyph name -> [codepoint, advance], read by nl.libreria.core.gui.Glyphs.

Usage: python3 generate.py [output: core/src/main/resources]
"""
import json
import os
import shutil
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '../../core/src/main/resources')
NS = 'libreria'

# Line height of a text display (font line height + 1)
LH = 10
# Tooltip panel: padding around the content box (in font pixels)
PAD_X = 6
PAD_TOP = 6
PAD_BOTTOM = 5
PANEL_LINES = range(2, 15)

# ------------------------------------------------------------------ palette (warm "adventure" wood, bronze, parchment)
OUTLINE = (18, 11, 7, 255)
BRONZE_HI = (240, 204, 120, 255)
BRONZE = (198, 152, 74, 255)
BRONZE_LO = (122, 86, 38, 255)
INNER = (40, 26, 16, 255)
FILL_TOP = (50, 35, 24)
FILL_BOTTOM = (30, 21, 14)
FILL_ALPHA = 248
PARCH = (205, 188, 150, 255)
PARCH_LO = (176, 158, 120, 255)
PARCH_HI = (222, 208, 174, 255)

# Tool themes for the menu frames: id -> (ribbon colour, emblem)
THEMES = {
    'libreria': ((150, 98, 34), 'book'),
    'chests': ((148, 84, 36), 'chest'),
    'tools': ((78, 92, 110), 'pick'),
    'waypoints': ((40, 118, 112), 'flag'),
    'info': ((52, 86, 140), 'scroll'),
    'comfort': ((132, 74, 102), 'heart'),
    'graves': ((78, 70, 92), 'grave'),
    'collector': ((160, 120, 28), 'trophy'),
    'nicknames': ((140, 58, 120), 'tag'),
    'claims': ((62, 120, 50), 'map'),
    'glint': ((104, 64, 160), 'sparkle'),
}

glyphs = {}
providers = []
_cp = [0xE000]


def next_cp():
    c = _cp[0]
    _cp[0] += 1
    return c


def actual_width(img, x0, w, h):
    """Like Minecraft: the glyph is as wide as its rightmost non-transparent column."""
    for x in range(w - 1, -1, -1):
        for y in range(h):
            if img.getpixel((x0 + x, y))[3] != 0:
                return x + 1
    return 0


def bitmap(file, cells, height, ascent):
    """cells: list of (name, image). All images get the same cell size; one row."""
    cw = max(im.width for _, im in cells)
    ch = height
    sheet = Image.new('RGBA', (cw * len(cells), ch), (0, 0, 0, 0))
    chars = ''
    for i, (name, im) in enumerate(cells):
        sheet.paste(im, (i * cw, 0))
        cp = next_cp()
        chars += chr(cp)
        w = actual_width(sheet, i * cw, cw, ch)
        glyphs[name] = [cp, w + 1]
    path = os.path.join(OUT, 'assets', NS, 'textures/font/ui', file + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path, optimize=True)
    assert ascent <= height, (file, ascent, height)
    providers.append({'type': 'bitmap', 'file': f'{NS}:font/ui/{file}.png', 'height': height, 'ascent': ascent, 'chars': [chars]})


def spaces():
    adv = {}
    for k in range(0, 10):
        for sign in (1, -1):
            v = sign * (1 << k)
            cp = next_cp()
            adv[chr(cp)] = v
            glyphs[f'space{v:+d}'] = [cp, v]
    providers.append({'type': 'space', 'advances': adv})


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


# ------------------------------------------------------------------ tooltip panel

def fill_color(y, h):
    t = 0 if h <= 1 else y / (h - 1)
    c = lerp(FILL_TOP, FILL_BOTTOM, t)
    return c + (FILL_ALPHA,)


def edge_rows(img, x, h):
    """Top and bottom border + the fill for one column x."""
    px(img, x, 0, OUTLINE)
    px(img, x, 1, BRONZE_HI)
    px(img, x, 2, BRONZE)
    px(img, x, 3, INNER)
    for y in range(4, h - 4):
        c = fill_color(y - 4, h - 8)
        if y == 4:
            c = lerp(c, (90, 64, 40), 0.35) + (FILL_ALPHA,)
        px(img, x, y, c)
    px(img, x, h - 4, INNER)
    px(img, x, h - 3, BRONZE)
    px(img, x, h - 2, BRONZE_LO)
    px(img, x, h - 1, OUTLINE)


def stud(img, cx, cy):
    """A little bronze rivet in a corner."""
    for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
        px(img, cx + dx, cy + dy, BRONZE)
    px(img, cx - 1, cy - 1, BRONZE_HI)
    px(img, cx + 1, cy + 1, BRONZE_LO)
    px(img, cx + 1, cy - 1, BRONZE)
    px(img, cx - 1, cy + 1, BRONZE)
    px(img, cx, cy, (255, 236, 170, 255))


def panel_cap(h, right):
    img = Image.new('RGBA', (6, h), (0, 0, 0, 0))
    for x in range(6):
        edge_rows(img, x, h)
    # vertical border: columns 0..3 (mirrored for the right cap)
    for y in range(h):
        cols = [OUTLINE, BRONZE_HI if not right else BRONZE_LO, BRONZE, INNER]
        for i, c in enumerate(cols):
            px(img, 5 - i if right else i, y, c)
    # round the outer corners
    ox = 5 if right else 0
    for y in (0, h - 1):
        px(img, ox, y, (0, 0, 0, 0))
    for y in (1, h - 2):
        px(img, ox, y, OUTLINE)
    px(img, 4 if right else 1, 0, OUTLINE)
    px(img, 4 if right else 1, h - 1, OUTLINE)
    stud(img, 3 if right else 2, 2)
    stud(img, 3 if right else 2, h - 3)
    if not right:
        return img
    return img


def panel_mid(w, h):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for x in range(w):
        edge_rows(img, x, h)
    return img


def panels():
    widths = [64, 32, 16, 8, 4, 2, 1]
    for n in PANEL_LINES:
        hc = n * LH
        h = PAD_TOP + hc + PAD_BOTTOM
        ascent = (n - 1) * LH + PAD_TOP + 7
        cells = [(f'panel{n}.l', panel_cap(h, False)), (f'panel{n}.r', panel_cap(h, True))]
        cells += [(f'panel{n}.m{w}', panel_mid(w, h)) for w in widths]
        bitmap(f'panel_{n}', cells, h, ascent)


def gem():
    """A grade gem on the top border (tinted with the grade colour by the text colour)."""
    h = 7 + PAD_TOP + 2
    img = Image.new('RGBA', (7, h), (0, 0, 0, 0))
    shape = ['...#...', '..#w#..', '.#wvv#.', '#wvvvs#', '.#vvs#.', '..#s#..', '...#...']
    pal = {'#': (60, 60, 60, 255), 'w': (255, 255, 255, 255), 'v': (205, 205, 205, 255), 's': (140, 140, 140, 255)}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in pal:
                px(img, x, y, pal[ch])
    bitmap('gem', [('gem', img)], h, h)


def slot():
    """Dark recessed square behind the icon (spans the first two lines)."""
    img = Image.new('RGBA', (20, 20), (0, 0, 0, 0))
    for x in range(20):
        for y in range(20):
            edge = x in (0, 19) or y in (0, 19)
            if edge:
                c = BRONZE_LO
                if (x, y) in ((0, 0), (19, 0), (0, 19), (19, 19)):
                    continue
            elif x == 1 or y == 1:
                c = (12, 8, 5, 255)
            elif x == 18 or y == 18:
                c = (58, 40, 26, 255)
            else:
                c = (22, 15, 10, 255)
            px(img, x, y, c)
    bitmap('slot', [('slot', img)], 20, 8)


def divider():
    """Ornamental rule ───◆─── under the header; pieces so the server can make it exactly as wide as the card."""
    h = 5

    def piece(w, fade=None):
        img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        for x in range(w):
            a = 255
            if fade == 'in':
                a = int(255 * (x + 1) / (w + 1))
            elif fade == 'out':
                a = int(255 * (w - x) / (w + 1))
            px(img, x, 1, BRONZE_HI[:3] + (a // 3,))
            px(img, x, 2, BRONZE[:3] + (a,))
            px(img, x, 3, (14, 9, 5, a * 2 // 3))
        return img

    center = Image.new('RGBA', (7, h), (0, 0, 0, 0))
    shape = ['...#...', '..#h#..', '-#hbl#-', '..#l#..', '...#...']
    pal = {'#': OUTLINE, 'h': BRONZE_HI, 'b': BRONZE, 'l': BRONZE_LO, '-': BRONZE}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in pal:
                px(center, x, y, pal[ch])
    cells = [('div.l', piece(10, 'in')), ('div.r', piece(10, 'out')), ('div.c', center)]
    cells += [(f'div.m{w}', piece(w)) for w in (64, 32, 16, 8, 4, 2, 1)]
    bitmap('divider', cells, h, 7 - 3)


def pips():
    full = Image.new('RGBA', (4, 6), (0, 0, 0, 0))
    empty = Image.new('RGBA', (4, 6), (0, 0, 0, 0))
    shade = [255, 235, 220, 205, 185, 150]
    for y in range(6):
        for x in range(4):
            corner = (x in (0, 3)) and (y in (0, 5))
            if corner:
                continue
            v = shade[y]
            px(full, x, y, (v, v, v, 255))
            rim = x in (0, 3) or y in (0, 5)
            px(empty, x, y, (96, 76, 54, 255) if rim else (34, 25, 18, 255))
    bitmap('pips', [('pip.full', full), ('pip.empty', empty)], 6, 6)


# ------------------------------------------------------------------ icons (8x8 pixel art, full colour)

ICONS = {
    'heart': (['.##.##..', '#hr#rr#.', '#rrrrr#.', '#rrrrr#.', '.#rrr#..', '..#r#...', '...#....', '........'],
              {'#': (70, 8, 12), 'r': (220, 38, 48), 'h': (255, 170, 170)}),
    'love': (['.##.##..', '#hp#pp#.', '#ppppp#.', '#ppppp#.', '.#ppp#..', '..#p#...', '...#....', '........'],
             {'#': (90, 20, 70), 'p': (255, 110, 200), 'h': (255, 220, 240)}),
    'sprout': (['...gg...', '..gGg.g.', '.g.G.gG.', '....Gg..', '...G....', '...G....', '.bbbbbb.', 'bBBBBBBb'],
               {'g': (120, 200, 70), 'G': (60, 130, 40), 'b': (120, 80, 45), 'B': (80, 50, 28)}),
    'drop': (['...#....', '..#y#...', '..#yy#..', '.#yYyy#.', '.#yyYy#.', '.#yyyy#.', '..####..', '........'],
             {'#': (110, 70, 10), 'y': (250, 190, 40), 'Y': (255, 240, 160)}),
    'bolt': (['....##..', '...#y#..', '..#y#...', '.#yyyy#.', '...#y#..', '..#y#...', '..##....', '........'],
             {'#': (110, 40, 10), 'y': (255, 210, 60)}),
    'chest': (['########', '#wWWWWw#', '#wwwwww#', '##gGGg##', '#wwggww#', '#wwwwww#', '########', '........'],
              {'#': (50, 30, 15), 'w': (170, 110, 55), 'W': (200, 140, 75), 'g': (230, 200, 90), 'G': (255, 240, 150)}),
    'stack': (['........', '.####...', '.#ww#...', '.####...', '...####.', '...#ww#.', '...####.', '........'],
              {'#': (70, 70, 80), 'w': (200, 200, 215)}),
    'speed': (['........', '#..##...', '.#..##..', '..#..##.', '.#..##..', '#..##...', '........', '........'],
              {'#': (120, 200, 255)}),
    'jump': (['...#....', '..###...', '.#####..', '...#....', '...#....', '...#....', '........', '........'],
             {'#': (140, 230, 120)}),
    'baby': (['..p.p...', '.ppypp..', '..pyp...', '...g....', '..gg....', '...g....', '........', '........'],
             {'p': (255, 170, 210), 'y': (255, 230, 90), 'g': (90, 170, 70)}),
    'time': (['#######.', '.#www#..', '..#s#...', '...#....', '..#s#...', '.#sss#..', '#######.', '........'],
             {'#': (120, 90, 50), 'w': (220, 220, 230), 's': (240, 200, 90)}),
    'star': (['...#....', '...y....', '#yyYyy#.', '.#yyy#..', '.y#.#y..', '#.....#.', '........', '........'],
             {'#': (160, 110, 10), 'y': (255, 215, 60), 'Y': (255, 250, 200)}),
    'check': (['........', '......#.', '.....#g.', '#...#g..', 'g#.#g...', '.g#g....', '..g.....', '........'],
              {'#': (30, 90, 30), 'g': (110, 230, 90)}),
    'pick': (['.#####..', '#iiiii#.', '.#.wb#..', '...wb...', '...w....', '..w.....', '.w......', 'w.......'],
             {'#': (40, 40, 50), 'i': (180, 190, 205), 'w': (150, 100, 55), 'b': (100, 65, 35)}),
    'trophy': (['#yyyyy#.', '#yYyyy#.', '.#yyy#..', '..#y#...', '...y....', '..###...', '.#yyy#..', '........'],
               {'#': (150, 100, 10), 'y': (250, 200, 60), 'Y': (255, 245, 190)}),
    'eye': (['........', '..####..', '.#wwww#.', '#wwbbww#', '.#wwww#.', '..####..', '........', '........'],
            {'#': (60, 60, 70), 'w': (235, 235, 240), 'b': (40, 120, 200)}),
}


def icons():
    cells = []
    for name, (rows, pal) in ICONS.items():
        img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    px(img, x, y, pal[ch] + (255,))
        cells.append(('icon.' + name, img))
    bitmap('icons', cells, 8, 7)


# ------------------------------------------------------------------ menu frames (container title background)

EMBLEMS = {
    'book': ['.######.', '#wwwwww#', '#w#ww#w#', '#wwwwww#', '#w####w#', '#wwwwww#', '.######.', '........'],
    'chest': ['########', '#wwwwww#', '##gggg##', '#ww##ww#', '#wwwwww#', '########', '........', '........'],
    'pick': ['.#####..', '#wwwww#.', '.#.w##..', '...w....', '..w.....', '.w......', 'w.......', '........'],
    'flag': ['#.......', '#wwww...', '#wwwww..', '#wwww...', '#.......', '#.......', '#.......', '........'],
    'scroll': ['.######.', '#wwwwww#', '.w####w.', '.wwwwww.', '.w###.w.', '#wwwwww#', '.######.', '........'],
    'heart': ['.##.##..', '#ww#ww#.', '#wwwww#.', '.#www#..', '..#w#...', '...#....', '........', '........'],
    'grave': ['..###...', '.#www#..', '#wwwww#.', '#w#w#w#.', '#wwwww#.', '#wwwww#.', '#######.', '........'],
    'trophy': ['#wwwww#.', '#wwwww#.', '.#www#..', '..#w#...', '...w....', '..###...', '.#####..', '........'],
    'tag': ['..#####.', '.#wwwww#', '#w#wwww#', '.#wwwww#', '..#####.', '........', '........', '........'],
    'map': ['#.##.##.', '#w#ww#w#', '#ww#w#w#', '#w#ww#w#', '#ww#w#w#', '.##.##.#', '........', '........'],
    'sparkle': ['...#....', '...w....', '.#.w.#..', '##www##.', '.#.w.#..', '...w....', '...#....', '........'],
}


def frame(rows, theme):
    color, emblem = THEMES[theme]
    W, H = 176, 114 + 18 * rows
    img = Image.new('RGBA', (W, H), PARCH)
    # subtle parchment grain, repeating every 18 px so filler tiles line up
    for y in range(H):
        for x in range(W):
            gx, gy = (x - 7) % 18, (y - 17) % 18
            if (gx * 7 + gy * 13) % 29 == 0:
                px(img, x, y, PARCH_LO)
            elif (gx * 11 + gy * 5) % 37 == 0:
                px(img, x, y, PARCH_HI)
    # holes for the slots
    holes = [(7, 17, 162, 18 * rows), (7, 30 + 18 * rows, 162, 54), (7, 88 + 18 * rows, 162, 18)]
    for hx, hy, hw, hh in holes:
        for y in range(hy, hy + hh):
            for x in range(hx, hx + hw):
                img.putpixel((x, y), (0, 0, 0, 0))
        # a thin dark line around each slot area
        for x in range(hx - 1, hx + hw + 1):
            px(img, x, hy - 1, PARCH_LO)
            px(img, x, hy + hh, PARCH_HI)
        for y in range(hy - 1, hy + hh + 1):
            px(img, hx - 1, y, PARCH_LO)
            px(img, hx + hw, y, PARCH_HI)
    # outer frame (like the tooltip: outline, bronze, dark)
    for x in range(W):
        for i, c in enumerate([OUTLINE, BRONZE_HI, BRONZE, INNER]):
            px(img, x, i, c)
        for i, c in enumerate([OUTLINE, BRONZE_LO, BRONZE, INNER]):
            px(img, x, H - 1 - i, c)
    for y in range(H):
        for i, c in enumerate([OUTLINE, BRONZE_HI, BRONZE, INNER]):
            px(img, i, y, c)
        for i, c in enumerate([OUTLINE, BRONZE_LO, BRONZE, INNER]):
            px(img, W - 1 - i, y, c)
    for (cx, cy) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
        px(img, cx, cy, (0, 0, 0, 0))
    # title ribbon in the tool's colour
    top, bottom = 4, 15
    for y in range(top, bottom + 1):
        t = (y - top) / (bottom - top)
        c = lerp(lerp(color, (255, 255, 255), 0.18), lerp(color, (0, 0, 0), 0.25), t)
        for x in range(4, W - 4):
            px(img, x, y, c + (255,))
    for x in range(4, W - 4):
        px(img, x, bottom + 1, lerp(color, (0, 0, 0), 0.55) + (255,))
    # emblem on the right of the ribbon
    rows_e = EMBLEMS[emblem]
    ex, ey = W - 17, 6
    dark = lerp(color, (0, 0, 0), 0.6) + (255,)
    light = lerp(color, (255, 255, 255), 0.75) + (255,)
    for y, row in enumerate(rows_e):
        for x, ch in enumerate(row):
            if ch == '#':
                px(img, ex + x, ey + y, dark)
            elif ch in 'wg':
                px(img, ex + x, ey + y, light)
    # studs in the corners and where the ribbon meets the frame
    for cx, cy in ((2, 2), (W - 3, 2), (2, H - 3), (W - 3, H - 3)):
        stud(img, cx, cy)
    return img


def frames():
    for rows in range(1, 7):
        cells = [(f'frame{rows}.{t}', frame(rows, t)) for t in THEMES]
        bitmap(f'frame_{rows}', cells, 114 + 18 * rows, 13)


def filler_tile():
    """Item model for empty menu slots: an 18x18 piece of parchment that hides the slot (needs oversized_in_gui)."""
    img = Image.new('RGBA', (18, 18), PARCH)
    for y in range(18):
        for x in range(18):
            if (x * 7 + y * 13) % 29 == 0:
                img.putpixel((x, y), PARCH_LO)
            elif (x * 11 + y * 5) % 37 == 0:
                img.putpixel((x, y), PARCH_HI)
    base = os.path.join(OUT, 'assets', NS)
    os.makedirs(os.path.join(base, 'textures/item/gui'), exist_ok=True)
    img.save(os.path.join(base, 'textures/item/gui/tile.png'))
    os.makedirs(os.path.join(base, 'models/item/gui'), exist_ok=True)
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/gui/tile'},
               'gui_light': 'front',
               'display': {'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.125, 1.125, 1.125]}}},
              open(os.path.join(base, 'models/item/gui/tile.json'), 'w'), indent=1)
    os.makedirs(os.path.join(base, 'items/gui'), exist_ok=True)
    json.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/gui/tile'}, 'oversized_in_gui': True},
              open(os.path.join(base, 'items/gui/tile.json'), 'w'), indent=1)


DYES = {
    'white': (226, 228, 228), 'orange': (226, 116, 30), 'magenta': (178, 70, 168), 'light_blue': (62, 160, 210),
    'yellow': (232, 188, 46), 'lime': (106, 176, 34), 'pink': (222, 132, 164), 'gray': (78, 84, 88),
    'light_gray': (148, 148, 140), 'cyan': (30, 132, 140), 'purple': (118, 50, 168), 'blue': (56, 64, 160),
    'brown': (120, 78, 46), 'green': (82, 110, 34), 'red': (168, 46, 40), 'black': (36, 36, 42),
}


def plaques():
    """Coloured glass panes in menus (headers, on/off lamps) become little coloured plaques on the parchment."""
    base = os.path.join(OUT, 'assets', NS)
    for name, c in DYES.items():
        img = Image.new('RGBA', (18, 18), PARCH)
        dark = lerp(c, (0, 0, 0), 0.45) + (255,)
        light = lerp(c, (255, 255, 255), 0.35) + (255,)
        for y in range(2, 16):
            for x in range(2, 16):
                corner = (x in (2, 15)) and (y in (2, 15))
                if corner:
                    continue
                edge = x in (2, 15) or y in (2, 15)
                if edge:
                    col = dark
                elif y == 3 or x == 3:
                    col = light
                else:
                    t = (y - 4) / 11
                    col = lerp(c, lerp(c, (0, 0, 0), 0.18), t) + (255,)
                img.putpixel((x, y), col)
        # soft shadow under the plaque
        for x in range(3, 16):
            img.putpixel((x, 16), PARCH_LO)
        for y in range(3, 16):
            img.putpixel((16, y), PARCH_LO)
        img.save(os.path.join(base, f'textures/item/gui/pane_{name}.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/gui/pane_{name}'},
                   'gui_light': 'front',
                   'display': {'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.125, 1.125, 1.125]}}},
                  open(os.path.join(base, f'models/item/gui/pane_{name}.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/gui/pane_{name}'}, 'oversized_in_gui': True},
                  open(os.path.join(base, f'items/gui/pane_{name}.json'), 'w'), indent=1)


def main():
    base = os.path.join(OUT, 'assets', NS)
    for sub in ('textures/font/ui', 'font', 'items/gui', 'models/item/gui', 'textures/item/gui'):
        shutil.rmtree(os.path.join(base, sub), ignore_errors=True)
    spaces()
    panels()
    gem()
    slot()
    divider()
    pips()
    icons()
    frames()
    filler_tile()
    plaques()
    os.makedirs(os.path.join(base, 'font'), exist_ok=True)
    json.dump({'providers': providers}, open(os.path.join(base, 'font/ui.json'), 'w'), indent=1)
    meta = {'lineHeight': LH, 'padX': PAD_X, 'padTop': PAD_TOP, 'padBottom': PAD_BOTTOM,
            'panelLines': [min(PANEL_LINES), max(PANEL_LINES)], 'themes': list(THEMES), 'glyphs': glyphs}
    os.makedirs(os.path.join(OUT, NS), exist_ok=True)
    json.dump(meta, open(os.path.join(OUT, NS, 'ui_glyphs.json'), 'w'), indent=1)
    print(len(glyphs), 'glyphs,', len(providers), 'providers')


if __name__ == '__main__':
    main()
