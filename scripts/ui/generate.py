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
VANILLA = sys.argv[2] if len(sys.argv) > 2 else '/home/claude/ref/assets/assets/minecraft'
# Top-of-screen tooltip (a boss bar title): number of lines and where the panel starts (screen pixels)
HUD_LINES = 12
HUD_NAME_Y = 3
HUD_PANEL_TOP = 2
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
    'flag': (['#.......', '#rrrr...', '#rRrrr..', '#rrrr...', '#.......', '#.......', '#.......', '##......'],
             {'#': (110, 80, 50), 'r': (230, 60, 50), 'R': (255, 150, 140)}),
    'mouse': (['.##w#...', '#ww#w#..', '#ww#w#..', '#####...', '#wwww#..', '#wwww#..', '.####...', '........'],
              {'#': (60, 60, 60), 'w': (225, 225, 225)}),
    'key': (['.###....', '#y.y#...', '#yyy#...', '.###yyyy', '....y.y.', '....y...', '........', '........'],
            {'#': (120, 80, 10), 'y': (250, 200, 60)}),
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


def empty_item():
    """An item that draws nothing (the land map puts it in your hands so no arm hangs in front of the view)."""
    base = os.path.join(OUT, 'assets', NS)
    os.makedirs(os.path.join(base, 'items/gui'), exist_ok=True)
    json.dump({'model': {'type': 'minecraft:empty'}}, open(os.path.join(base, 'items/gui/empty.json'), 'w'))


WALLS = {'mine': (85, 255, 85), 'trusted': (85, 255, 255), 'other': (255, 85, 85), 'cursor': (255, 214, 110)}


def claim_buildings():
    """The land map shows claimed land as see-through glass buildings: walls, a flat roof and bright frame rods along
    the edges. One flat square model each (a rod is two crossed squares), stretched by the server."""
    base = os.path.join(OUT, 'assets', NS)
    for d in ('textures/item/claim', 'models/item/claim', 'items/claim'):
        os.makedirs(os.path.join(base, d), exist_ok=True)

    def save(name, img, elements):
        img.save(os.path.join(base, f'textures/item/claim/{name}.png'))
        tex = f'{NS}:item/claim/{name}'
        json.dump({'textures': {'t': tex, 'particle': tex}, 'elements': elements},
                  open(os.path.join(base, f'models/item/claim/{name}.json'), 'w'))
        json.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/claim/{name}'}},
                  open(os.path.join(base, f'items/claim/{name}.json'), 'w'))

    face = {'texture': '#t', 'uv': [0, 0, 16, 16]}
    standing = {'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False, 'faces': {'north': dict(face), 'south': dict(face)}}
    crossed = {'from': [8, 0, 0], 'to': [8, 16, 16], 'shade': False, 'faces': {'east': dict(face), 'west': dict(face)}}
    lying = {'from': [0, 8, 0], 'to': [16, 8, 16], 'shade': False, 'faces': {'up': dict(face), 'down': dict(face)}}
    for name, c in WALLS.items():
        glass = Image.new('RGBA', (16, 16))
        for y in range(16):
            t = y / 15  # 0 at the top
            for x in range(16):
                glass.putpixel((x, y), lerp(c, (255, 255, 255), 0.16 - 0.08 * t) + (int(84 - 30 * t),))
        save(f'wall_{name}', glass, [standing])
        roof = Image.new('RGBA', (16, 16), lerp(c, (255, 255, 255), 0.12) + (58,))
        save(f'roof_{name}', roof, [lying])
        rod = Image.new('RGBA', (16, 16), lerp(c, (255, 255, 255), 0.5) + (235,))
        save(f'rod_{name}', rod, [standing, crossed])


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


# ------------------------------------------------------------------ top-of-screen tooltip (boss bar title)

def resolve_textures(model_id, depth=0):
    """Texture variables of a vanilla model, following parents."""
    if depth > 12 or not model_id or model_id.startswith('builtin/') or model_id.startswith('minecraft:builtin/'):
        return {}
    path = model_id.split(':')[-1]
    f = os.path.join(VANILLA, 'models', path + '.json')
    if not os.path.exists(f):
        return {}
    m = json.load(open(f))
    tex = dict(resolve_textures(m.get('parent'), depth + 1)) if m.get('parent') else {}
    tex.update(m.get('textures', {}))
    return tex


def first_model(node):
    """The first plain model a (possibly nested) item model definition points at."""
    if not isinstance(node, dict):
        return None
    t = node.get('type', '').replace('minecraft:', '')
    if t == 'model':
        return node.get('model')
    if t == 'special':
        return node.get('base')
    order = []
    if t == 'select':
        order = [node.get('fallback')] + [c.get('model') for c in node.get('cases', [])]
    elif t == 'condition':
        order = [node.get('on_false'), node.get('on_true')]
    elif t == 'composite':
        order = node.get('models', [])
    elif t == 'range_dispatch':
        order = [node.get('fallback')] + [e.get('model') for e in node.get('entries', [])]
    for n in order:
        r = first_model(n)
        if r:
            return r
    return None


def icon_texture(item):
    d = json.load(open(os.path.join(VANILLA, 'items', item + '.json')))
    mid = first_model(d['model'])
    if not mid:
        return None, False
    tinted = False
    tex = resolve_textures(mid)

    def deref(v, n=0):
        while isinstance(v, str) and v.startswith('#') and n < 8:
            v = tex.get(v[1:])
            n += 1
        return v

    # A texture named after the item itself is the best picture (beacon, not the glass around it)
    for v in list(tex.values()):
        v = deref(v)
        if isinstance(v, str) and not v.startswith('#') and v.split('/')[-1] == item:
            v = v if ':' in v else 'minecraft:' + v
            if os.path.exists(os.path.join(VANILLA, 'textures', v.split(':')[1] + '.png')):
                return v, any(w in v for w in ('leaves', 'vine', 'lily_pad', 'fern', 'short_grass', 'tall_grass'))
    for key in ('layer0', 'all', 'side', 'front', 'texture', 'cross', 'plant', 'pattern', 'top', 'end', 'particle'):
        v = deref(tex.get(key))
        if isinstance(v, str) and not v.startswith('#'):
            v = v if ':' in v else 'minecraft:' + v
            png = os.path.join(VANILLA, 'textures', v.split(':')[1] + '.png')
            if os.path.exists(png):
                if any(w in v for w in ('leaves', 'grass_block_top', 'vine', 'lily_pad', 'fern', 'short_grass', 'tall_grass')):
                    tinted = True
                return v, tinted
    return None, False


def hud_fonts():
    """libreria:hud0..hudN: the vanilla font and our pictures moved down one tooltip line each, so a boss bar title
    (a single line of text at the top of the screen) can draw a whole card. Same characters as libreria:ui."""
    default = json.load(open(os.path.join(VANILLA, 'font/include/default.json')))['providers']
    ui = [p for p in providers if p['type'] == 'bitmap' and not p['file'].split('/')[-1].startswith(('panel_', 'frame_'))]
    space = [p for p in providers if p['type'] == 'space']
    base = os.path.join(OUT, 'assets', NS, 'font')
    icons = {}
    # Block and item icons (vanilla textures, referenced, not copied) for the slot on the first line
    icon_providers = []
    items_dir = os.path.join(VANILLA, 'items')
    for fname in sorted(os.listdir(items_dir)):
        item = fname[:-5]
        tex, tinted = icon_texture(item)
        if not tex:
            continue
        png = os.path.join(VANILLA, 'textures', tex.split(':')[1] + '.png')
        im = Image.open(png)
        if im.width == 0 or im.height % im.width:
            continue
        frames = im.height // im.width
        cp = next_cp()
        chars = [chr(cp)] + ['\u0000'] * (frames - 1)
        icon_providers.append({'type': 'bitmap', 'file': tex + '.png', 'height': 16, 'ascent': 0, 'chars': chars})
        # Advance like Minecraft: rightmost non-transparent column of the first frame, scaled to 16 px, plus 1
        first = im.convert('RGBA').crop((0, 0, im.width, im.width))
        w = actual_width(first, 0, first.width, first.height)
        icons[item] = [cp, 1 if tinted else 0, int(0.5 + w * 16 / im.width) + 1]
    pad_top = PAD_TOP
    for k in range(HUD_LINES):
        shift = (HUD_PANEL_TOP + pad_top + LH * k) - HUD_NAME_Y
        provs = [{'type': 'space', 'advances': {' ': 4, '\u200c': 0}}] + space
        for p in default:
            q = dict(p)
            q['ascent'] = p.get('ascent', 7) - shift
            provs.append(q)
        for p in ui:
            q = dict(p)
            q['ascent'] = p['ascent'] - shift
            provs.append(q)
        if k == 0:
            # The panel: its top just below the top of the screen
            for p in providers:
                if p['type'] == 'bitmap' and p['file'].split('/')[-1].startswith('panel_'):
                    q = dict(p)
                    q['ascent'] = HUD_NAME_Y + 7 - HUD_PANEL_TOP
                    provs.append(q)
            # Icons: 16x16 in the middle of the slot (slot top = line top - 1, so icon top = line top + 2)
            for p in icon_providers:
                q = dict(p)
                q['ascent'] = HUD_NAME_Y + 7 - (HUD_PANEL_TOP + pad_top + 2)
                provs.append(q)
        json.dump({'providers': provs}, open(os.path.join(base, f'hud{k}.json'), 'w'))
    # libreria:icons: the same item pictures at full size inside a 20 px button of a window (the button's text line
    # starts 6 px below its top; a 16 px picture with its top 2 px below the button's top)
    provs = []
    for p in icon_providers:
        q = dict(p)
        q['ascent'] = 11
        provs.append(q)
    json.dump({'providers': provs}, open(os.path.join(base, 'icons.json'), 'w'))
    return icons


# ------------------------------------------------------------------ buttons in Minecraft's own style
# The bevelled grey button of the vanilla recipe book button (and its blue hover state), so our buttons look native
VANILLA_BTN = {False: dict(border=(0, 0, 0), light=(255, 255, 255), fill=(198, 198, 198), shadow=(85, 85, 85), corner=(12, 5, 0)),
               True: dict(border=(0, 7, 62), light=(255, 255, 255), fill=(136, 146, 201), shadow=(52, 62, 117), corner=(0, 7, 62))}

ICON_PAL = {
    '#': (55, 55, 55, 255), 'g': (60, 60, 60, 255),
    'w': (168, 220, 255, 255), 'b': (104, 164, 216, 255), 'h': (255, 255, 255, 255),
    'd': (122, 86, 48, 255), 'D': (72, 50, 26, 255),
    'u': (52, 120, 52, 255), 'U': (30, 76, 30, 255), 'r': (150, 52, 44, 255), 'R': (96, 30, 24, 255),
}

MAGNIFIER = ['..####......', '.#hhww#.....', '#hwwwwb#....', '#hwwwwb#....', '#wwwwbb#....', '#wwwbbb#....',
             '.#wbbb#.....', '..####dD....', '......ddD...', '.......ddD..', '........ddD.', '.........dD.']
SORT = ['..uU........', '.uuUU.......', 'uuuuUU..rR..', '..uU....rR..', '..uU....rR..', '..uU....rR..',
        '..uU....rR..', '..uU....rR..', '..uU....rR..', '..uU..rrrrRR', '.......rrRR.', '........rR..']
MENU = ['..........', 'gggggggggg', 'gggggggggg', '..........', 'gggggggggg', 'gggggggggg', '..........',
        'gggggggggg', 'gggggggggg', '..........']


def vanilla_button(w, h, hi=False):
    c = VANILLA_BTN[hi]
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            if y in (0, h - 1):
                if not 2 <= x <= w - 3:
                    continue
                col = c['border']
            elif y in (1, h - 2):
                if x in (0, w - 1):
                    continue
                if x == 1:
                    col = c['border']
                elif x == w - 2:
                    col = c['corner']
                else:
                    col = c['light'] if y == 1 else c['shadow']
            else:
                if x in (0, w - 1):
                    col = c['border']
                elif x == 1:
                    col = c['light']
                elif x == w - 2:
                    col = c['shadow']
                else:
                    col = c['fill']
            img.putpixel((x, y), col + (255,))
    return img


def draw_icon(img, rows, ox, oy):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in ICON_PAL:
                img.putpixel((ox + x, oy + y), ICON_PAL[ch])


def search_button():
    """The recipe book button in the inventory becomes a search button: Minecraft's own grey button with a
    magnifying glass (and the same blue when you hover it)."""
    d = os.path.join(OUT, 'assets', 'minecraft', 'textures/gui/sprites/recipe_book')
    os.makedirs(d, exist_ok=True)
    for name, hi in (('button', False), ('button_highlighted', True)):
        img = vanilla_button(20, 18, hi)
        draw_icon(img, MAGNIFIER, 4, 3)
        img.save(os.path.join(d, name + '.png'))


# Every language Minecraft ships, so the sort tab shows whatever language you play in
LANGS = ('af_za ar_sa ast_es az_az ba_ru bar be_by be_latn bg_bg br_fr brb bs_ba ca_es cs_cz cy_gb da_dk de_at de_ch de_de '
         'el_gr en_au en_ca en_gb en_nz en_pt en_ud en_us enp enws eo_uy es_ar es_cl es_ec es_es es_mx es_uy es_ve esan et_ee '
         'eu_es fa_ir fi_fi fil_ph fo_fo fr_ca fr_fr fra_de fur_it fy_nl ga_ie gd_gb gl_es haw_us he_il hi_in hn_no hr_hr hu_hu '
         'hy_am id_id ig_ng io_en is_is isv it_it ja_jp jbo_en ka_ge kk_kz kn_in ko_kr ksh kw_gb ky_kg la_la lb_lu li_li lmo '
         'lo_la lol_us lt_lt lv_lv lzh mk_mk mn_mn ms_my mt_mt nah nds_de nl_be nl_nl nn_no no_no oc_fr ovd pl_pl pls pt_br '
         'pt_pt qya_aa ro_ro rpr ru_ru ry_ua sah_sah se_no sk_sk sl_si so_so sq_al sr_cs sr_sp sv_se sxu szl ta_in th_th tl_ph '
         'tlh_aa tok tr_tr tt_ru tzo_mx uk_ua val_es vec_it vi_vn vp_vl yi_de yo_ng zh_cn zh_hk zh_tw zlm_arab').split()
# The inventory screen draws its title ("Crafting") at (97, 6). The tab hangs off the left edge of the window, on the
# row of the search button: x -24..-4, y 61..79.
TAB_X, TAB_Y, LABEL_X, LABEL_Y = -24, 61, 97, 6


def inventory_tab():
    """A sort button on the left edge of your inventory, in Minecraft's own style. The inventory screen has room for
    just one real button (the recipe book one, our search), so this one is drawn by the screen's title: the
    "Crafting" text is replaced by a picture that hangs outside the window. A click outside the window with nothing
    in your hand reaches the server (vanilla ignores it), and that is the sort."""
    tex = os.path.join(OUT, 'assets', NS, 'textures/font/ui/inv_tab.png')
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    img = vanilla_button(20, 18)
    draw_icon(img, SORT, 4, 3)
    img.save(tex)
    tab, back = '\ue9f0', '\ue9f1'
    font = {'providers': [
        {'type': 'space', 'advances': {back: TAB_X - LABEL_X}},
        # baseline of the title = its top + 7; the picture's top must be at TAB_Y
        {'type': 'bitmap', 'file': f'{NS}:font/ui/inv_tab.png', 'height': 18, 'ascent': LABEL_Y + 7 - TAB_Y, 'chars': [tab]},
    ]}
    d = os.path.join(OUT, 'assets', 'minecraft', 'font')
    os.makedirs(d, exist_ok=True)
    json.dump(font, open(os.path.join(d, 'default.json'), 'w'))
    ld = os.path.join(OUT, 'assets', 'minecraft', 'lang')
    os.makedirs(ld, exist_ok=True)
    for code in LANGS:
        # white (a title is drawn dark grey), back to the tab's place, the tab
        json.dump({'container.crafting': '\u00a7f' + back + tab}, open(os.path.join(ld, code + '.json'), 'w'))


def inventory_buttons():
    """The Libreria menu button in the crafting output slot: Minecraft's own grey button (18x18, over the slot)."""
    base = os.path.join(OUT, 'assets', NS)
    for d in ('textures/item/gui', 'models/item/gui', 'items/gui'):
        os.makedirs(os.path.join(base, d), exist_ok=True)
    img = vanilla_button(18, 18)
    draw_icon(img, MENU, 4, 4)
    img.save(os.path.join(base, 'textures/item/gui/btn_menu.png'))
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/gui/btn_menu'},
               'gui_light': 'front',
               'display': {'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.125, 1.125, 1.125]}}},
              open(os.path.join(base, 'models/item/gui/btn_menu.json'), 'w'))
    # Only in menus: in a hand (or the off-hand slot of the HUD) it draws nothing
    json.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                         'cases': [{'when': 'gui', 'model': {'type': 'minecraft:model', 'model': f'{NS}:item/gui/btn_menu'}}],
                         'fallback': {'type': 'minecraft:empty'}},
               'oversized_in_gui': True},
              open(os.path.join(base, 'items/gui/btn_menu.json'), 'w'))


def hotbar_font():
    """libreria:hotbar, for a button right of the hotbar drawn by the action bar text (which sits 50 px higher):
    a slot like the hotbar's, a picture in it and the key to press in its corner, all moved down onto the hotbar."""
    base = os.path.join(OUT, 'assets', NS)
    provs = [{'type': 'space', 'advances': dict(a)} for a in (p['advances'] for p in providers if p['type'] == 'space')]
    # The action bar line's top is 72 px above the screen bottom; a glyph's top = line top + 7 - ascent
    def asc(top_from_bottom):
        return top_from_bottom - 65

    slot = Image.new('RGBA', (22, 22), (0, 0, 0, 0))
    for y in range(22):
        for x in range(22):
            if x in (0, 21) or y in (0, 21):
                c = (24, 24, 24, 255)
            elif x == 1 or y == 1:
                c = (140, 140, 140, 255)
            elif x == 20 or y == 20:
                c = (62, 62, 62, 255)
            else:
                c = (0, 0, 0, 120)
            slot.putpixel((x, y), c)
    shift = Image.new('RGBA', (5, 7), (0, 0, 0, 0))
    for y, row in enumerate(['..#..', '.###.', '#####', '.###.', '.###.', '.###.', '.....']):
        for x, ch in enumerate(row):
            if ch == '#':
                shift.putpixel((x, y), (255, 255, 255, 255))
    for name, im, top in (('hb.slot', slot, 22), ('hb.shift', shift, 10)):
        path = os.path.join(base, 'textures/font/ui', name.replace('.', '_') + '.png')
        im.save(path)
        cp = next_cp()
        glyphs[name] = [cp, actual_width(im, 0, im.width, im.height) + 1]
        provs.append({'type': 'bitmap', 'file': f'{NS}:font/ui/' + name.replace('.', '_') + '.png', 'height': im.height,
                      'ascent': asc(top), 'chars': [chr(cp)]})
    # Pictures: vanilla item textures, 16 px, 3 px below the slot's top
    for name, tex in (('hb.map', 'minecraft:item/filled_map.png'),):
        cp = next_cp()
        glyphs[name] = [cp, 17]
        provs.append({'type': 'bitmap', 'file': tex, 'height': 16, 'ascent': asc(19), 'chars': [chr(cp)]})
    # The key: the normal font, its top 10 px above the bottom (like the count on an item)
    default = json.load(open(os.path.join(VANILLA, 'font/include/default.json')))['providers']
    for p in default:
        q = dict(p)
        q['ascent'] = p.get('ascent', 7) - 7 + asc(10)
        provs.append(q)
    json.dump({'providers': provs}, open(os.path.join(base, 'font/hotbar.json'), 'w'))


def boss_bar():
    """The boss bar of the top tooltip uses the white colour; the pack makes white bars invisible (only the title)."""
    d = os.path.join(OUT, 'assets', 'minecraft', 'textures/gui/sprites/boss_bar')
    os.makedirs(d, exist_ok=True)
    for name in ('white_background', 'white_progress'):
        Image.new('RGBA', (182, 5), (0, 0, 0, 0)).save(os.path.join(d, name + '.png'))


def main():
    base = os.path.join(OUT, 'assets', NS)
    for sub in ('textures/font/ui', 'font', 'items/gui', 'models/item/gui', 'textures/item/gui', 'items/claim',
                'models/item/claim', 'textures/item/claim'):
        shutil.rmtree(os.path.join(base, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(OUT, 'assets', 'minecraft'), ignore_errors=True)
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
    empty_item()
    inventory_buttons()
    claim_buildings()
    os.makedirs(os.path.join(base, 'font'), exist_ok=True)
    json.dump({'providers': providers}, open(os.path.join(base, 'font/ui.json'), 'w'), indent=1)
    icon_map = hud_fonts()
    boss_bar()
    search_button()
    inventory_tab()
    hotbar_font()
    meta = {"hudLines": HUD_LINES, "icons": icon_map, 'lineHeight': LH, 'padX': PAD_X, 'padTop': PAD_TOP, 'padBottom': PAD_BOTTOM,
            'panelLines': [min(PANEL_LINES), max(PANEL_LINES)], 'themes': list(THEMES), 'glyphs': glyphs}
    os.makedirs(os.path.join(OUT, NS), exist_ok=True)
    json.dump(meta, open(os.path.join(OUT, NS, 'ui_glyphs.json'), 'w'), indent=1)
    print(len(glyphs), 'glyphs,', len(providers), 'providers')


if __name__ == '__main__':
    main()
