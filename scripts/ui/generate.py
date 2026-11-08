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
PANEL_LINES = range(2, 25)

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

# Domain themes for the menu frames (Items, Land & places, Progress, You): id -> (ribbon colour, emblem)
THEMES = {
    'libreria': ((150, 98, 34), 'book'),
    'items': ((34, 122, 136), 'chest'),
    'places': ((60, 122, 50), 'map'),
    'progress': ((170, 124, 22), 'trophy'),
    'you': ((136, 70, 150), 'tag'),
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


def glass_piece(w, h, part):
    """The card at the top of the screen in the glass look: smoked glass with a thin light edge, rounded at the caps."""
    L = LOOK
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    edge, fill = L['panel_rim'][0], (14, 16, 21, 230)
    for y in range(h):
        for x in range(w):
            if part == 'l' and rounded(x, y, w * 2, h, 3):
                continue
            if part == 'r' and rounded(x + w, y, w * 2, h, 3):
                continue
            put(img, x, y, fill[:3] + (230,))
            ring = y in (0, h - 1) or (part == 'l' and x == 0) or (part == 'r' and x == w - 1)
            if part == 'l' and x + min(y, h - 1 - y) == 2:
                ring = True
            if part == 'r' and (w - 1 - x) + min(y, h - 1 - y) == 2:
                ring = True
            if ring:
                put(img, x, y, edge)
            elif y == 1:
                put(img, x, y, (255, 255, 255, 14))
    return img


def panel_cap(h, right):
    if LOOK['outline'][3] == 0:
        return glass_piece(6, h, 'r' if right else 'l')
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
    if LOOK['outline'][3] == 0:
        return glass_piece(w, h, 'm')
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
    if LOOK['outline'][3] == 0:
        for x in range(20):
            for y in range(20):
                if rounded(x, y, 20, 20, 2):
                    continue
                edge = x in (0, 19) or y in (0, 19)
                img.putpixel((x, y), (255, 255, 255, 44) if edge else (0, 0, 0, 110))
        bitmap('slot', [('slot', img)], 20, 8)
        return
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
            if LOOK['outline'][3] == 0:
                px(img, x, 2, (255, 255, 255, a // 4))
                continue
            px(img, x, 1, BRONZE_HI[:3] + (a // 3,))
            px(img, x, 2, BRONZE[:3] + (a,))
            px(img, x, 3, (14, 9, 5, a * 2 // 3))
        return img

    center = Image.new('RGBA', (7, h), (0, 0, 0, 0))
    shape = ['...#...', '..#h#..', '-#hbl#-', '..#l#..', '...#...']
    pal = {'#': OUTLINE, 'h': BRONZE_HI, 'b': BRONZE, 'l': BRONZE_LO, '-': BRONZE}
    if LOOK['outline'][3] == 0:
        pal = {'#': (0, 0, 0, 0), 'h': (255, 255, 255, 150), 'b': (255, 255, 255, 110), 'l': (255, 255, 255, 80),
               '-': (255, 255, 255, 64)}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in pal and pal[ch][3]:
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
            if LOOK['outline'][3] == 0:
                px(empty, x, y, (255, 255, 255, 70) if rim else (0, 0, 0, 120))
            else:
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
    'book': ['...#....', '..#w#...', '.#www#..', '#wwwww#.', '.#www#..', '..#w#...', '...#....', '........'],
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


def panel_box(img, x0, y0, w, h):
    """The outer panel of a menu, in the current look: rim, rounded corners, fill from top to bottom (and the little
    rivets in the corners of the wooden look)."""
    L = LOOK
    tl, br = L['panel_rim'], L['panel_rim_lo']
    has_outline = L['outline'][3] > 0
    r = L['radius'] + 1
    for y in range(h):
        for x in range(w):
            if rounded(x, y, w, h, r + (1 if has_outline else 0)):
                continue
            c = lerp4(L['panel_top'], L['panel_bottom'], y / max(1, h - 1))
            ring = None
            k = min(x, y, w - 1 - x, h - 1 - y)
            if has_outline and k == 0:
                ring = L['outline']
            else:
                i = k - (1 if has_outline else 0)
                if 0 <= i < 3:
                    bottom_right = (x >= w - 4 or y >= h - 4) and not (x < 4 or y < 4)
                    ring = (br if bottom_right else tl)[i]
                    if ring[3] == 0:
                        ring = None
            put(img, x0 + x, y0 + y, c)
            if ring is not None:
                put(img, x0 + x, y0 + y, ring)
    if L['studs']:
        for cx, cy in ((5, 5), (w - 6, 5), (5, h - 6), (w - 6, h - 6)):
            stud(img, x0 + cx, y0 + cy)


def tray(img, x0, y0, w, h, accent=None):
    """A recessed area where the menu's items float (no grid of slots): shadow at the top, light at the bottom."""
    L = LOOK
    for y in range(h):
        for x in range(w):
            if rounded(x, y, w, h, L['radius']):
                continue
            if y == 0 or x == 0:
                c = L['tray_shadow']
            elif y == h - 1 or x == w - 1:
                c = L['tray_light']
            else:
                c = L['tray']
            put(img, x0 + x, y0 + y, c)
    if accent is not None:
        # The domain's colour along the top of the tray, fading out to the right
        for x in range(1, w - 1):
            t = x / (w - 2)
            a = max(0.0, 1 - t * 1.25)
            if a > 0:
                put(img, x0 + x, y0, accent + (int(255 * a),))


def sockets(img, x0, y0, cols, rows_):
    """Real slots (your inventory): a grid of recessed squares, 18 px apart like Minecraft's."""
    L = LOOK
    for j in range(rows_):
        for i in range(cols):
            sx, sy = x0 + 18 * i, y0 + 18 * j
            for y in range(18):
                for x in range(18):
                    if x == 0 or y == 0:
                        c = L['socket_shadow']
                    elif x == 17 or y == 17:
                        c = L['socket_light']
                    else:
                        c = L['socket']
                    put(img, sx + x, sy + y, c)


def tag(img, x0, y0, w, h):
    """A small light label plate (behind the "Inventory" line, which Minecraft writes in dark grey)."""
    L = LOOK
    for y in range(h):
        for x in range(w + h // 2):
            # pointed right end
            if x >= w and (x - w) > min(y, h - 1 - y):
                continue
            if rounded(x, y, w + h // 2, h, 1) and x < w:
                continue
            c = L['tag_hi'] if y == 0 else L['tag_lo'] if y == h - 1 else L['tag']
            put(img, x0 + x, y0 + y, c)


def emblem(img, name, ex, ey, color):
    dark = lerp(color, (0, 0, 0), 0.35) + (255,)
    light = lerp(color, (255, 255, 255), 0.45) + (255,)
    for y, row in enumerate(EMBLEMS[name]):
        for x, ch in enumerate(row):
            if ch == '#':
                px(img, ex + x, ey + y, dark)
            elif ch in 'wg':
                px(img, ex + x, ey + y, light)


def frame(rows, theme):
    """A whole menu window, drawn by its title over Minecraft's chest screen (Minecraft draws the title before the slots
    and the items, so the items stay on top). The menu's own part is one recessed tray without a grid: its items float
    on it; the bottom row (the menu's buttons) is a band of its own. Your inventory keeps real slots. (The pack takes away the
    "Inventory" line above it, see inventory_tab.) Coordinates are the chest screen's own."""
    color, emb = THEMES[theme]
    W, H = 176, 114 + 18 * rows
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    panel_box(img, 0, 0, W, H)
    # The menu's slots: x 7..168, y 17..17+18*rows. The tray has a pixel of room around them.
    top, bottom = 16, 18 + 18 * rows
    tray(img, 6, top, 164, bottom - top + 1, color)
    if rows > 1:
        y = 17 + 18 * (rows - 1)
        for x in range(7, 169):
            put(img, x, y - 1, LOOK['tray_light'])
            for yy in range(y, bottom):
                put(img, x, yy, LOOK['toolbar'])
    emblem(img, emb, W - 17, 5, color)
    sockets(img, 7, 30 + 18 * rows, 9, 3)
    sockets(img, 7, 88 + 18 * rows, 9, 1)
    return img


# ------------------------------------------------------------------ own windows (the window kit)
# A Libreria window is a chest screen whose whole picture is drawn by its title: one panel over the window, cards on
# the grid of cells (Minecraft's clickable spots, 18 px apart, invisible here), text and pictures anywhere. The cells
# hold invisible items, so a card is clickable wherever you point at it.
#
# The title line is drawn at y 6 of the window. libreria:y<N> is Minecraft's font (and the big emblems) with its top
# at y N of the window; libreria:row<K> holds the cards, their top 2 px below the top of cell row K.

WIN_W, WIN_H = 176, 222
CANVAS_Y = range(5, 220)
# Top of each row of cells (the slot's frame): 6 rows of the chest, 3 of your inventory, the hotbar
ROW_TOP = [17 + 18 * k for k in range(6)] + [139 + 18 * k for k in range(3)] + [197]
CARD_M = 2


def big_emblem(name, scale=3):
    rows_e = EMBLEMS[name]
    img = Image.new('RGBA', (8 * scale, 8 * scale), (0, 0, 0, 0))
    for y, row in enumerate(rows_e):
        for x, ch in enumerate(row):
            c = {'#': (70, 70, 70, 255), 'w': (255, 255, 255, 255), 'g': (200, 200, 200, 255)}.get(ch)
            if c:
                for dy in range(scale):
                    for dx in range(scale):
                        img.putpixel((x * scale + dx, y * scale + dy), c)
    return img


EMBLEMS.update({
    'gear': ['..#..#..', '.##ww##.', '.#w##w#.', '#w#..#w#', '#w#..#w#', '.#w##w#.', '.##ww##.', '..#..#..'],
    'help': ['.######.', '#wwwwww#', '#ww##ww#', '.#..#ww#', '...#ww#.', '...#ww#.', '........', '...#ww#.'],
    'compass': ['..####..', '.#wwww#.', '#ww#www#', '#w###ww#', '#ww###w#', '#www#ww#', '.#wwww#.', '..####..'],
    'scroll': EMBLEMS['scroll'],
    # the line under the active tab (in the last two rows, tinted by the text colour)
    'bar': ['........'] * 6 + ['wwwwwwww', 'wwwwwwww'],
})
BIG_EMBLEMS = ['chest', 'map', 'trophy', 'tag', 'book', 'gear', 'help', 'compass', 'scroll', 'heart', 'grave', 'bar']
canvas_pictures = []


def canvas_pictures_sheet():
    """Big emblems (24 px), drawn white and grey so the text colour tints them."""
    cells = [(f'big.{n}', big_emblem(n)) for n in BIG_EMBLEMS]
    cw, ch = 24, 24
    sheet = Image.new('RGBA', (cw * len(cells), ch), (0, 0, 0, 0))
    chars = ''
    for i, (name, im) in enumerate(cells):
        sheet.paste(im, (i * cw, 0))
        cp = next_cp()
        chars += chr(cp)
        glyphs[name] = [cp, actual_width(sheet, i * cw, cw, ch) + 1]
    path = os.path.join(OUT, 'assets', NS, 'textures/font/ui/big_emblems.png')
    sheet.save(path)
    canvas_pictures.append({'file': f'{NS}:font/ui/big_emblems.png', 'height': ch, 'chars': [chars]})
    # The same emblems small (8 px), for little buttons and list rows
    cells = [(f'sm.{n}', big_emblem(n, 1)) for n in BIG_EMBLEMS]
    sheet = Image.new('RGBA', (8 * len(cells), 8), (0, 0, 0, 0))
    chars = ''
    for i, (name, im) in enumerate(cells):
        sheet.paste(im, (i * 8, 0))
        cp = next_cp()
        chars += chr(cp)
        glyphs[name] = [cp, actual_width(sheet, i * 8, 8, 8) + 1]
    sheet.save(os.path.join(OUT, 'assets', NS, 'textures/font/ui/small_emblems.png'))
    canvas_pictures.append({'file': f'{NS}:font/ui/small_emblems.png', 'height': 8, 'chars': [chars]})


def card_piece(w, h, part):
    """A card: greyscale, tinted by the text colour (white = bright rim, the fill stays dark). part: l, m or r."""
    if LOOK['outline'][3] == 0:
        return glass_card_piece(w, h, part)
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    r = 2
    for y in range(h):
        for x in range(w):
            # position in the whole card: only the caps know the corners
            left = part == 'l' and x == 0
            right = part == 'r' and x == w - 1
            left2 = part == 'l' and x == 1
            right2 = part == 'r' and x == w - 2
            corner = (part == 'l' and x + min(y, h - 1 - y) < r) or (part == 'r' and (w - 1 - x) + min(y, h - 1 - y) < r)
            if corner:
                continue
            if y == 0 or left:
                c = (232, 232, 232, 255)          # rim, light side
            elif y == h - 1 or right:
                c = (150, 150, 150, 255)          # rim, shaded side
            elif y == 1 or left2:
                c = (96, 96, 96, 255)             # inner light
            elif y == h - 2 or right2:
                c = (40, 40, 40, 255)
            else:
                t = (y - 2) / max(1, h - 5)
                v = int(round(70 - 22 * t))       # the fill, darker towards the bottom
                c = (v, v, v, 246)
            img.putpixel((x, y), c)
    return img


def glass_card_piece(w, h, part):
    """A card in the glass look: a thin edge in the card's colour around a dark, see-through fill in that colour."""
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            cx = x if part == 'l' else x + 100 if part == 'm' else x + 200
            W2 = 200 + w
            if part == 'l' and rounded(x, y, 400, h, 3):
                continue
            if part == 'r' and rounded(cx, y, W2, h, 3):
                continue
            put(img, x, y, (64, 64, 64, 170))
            ring = y in (0, h - 1) or (part == 'l' and x == 0) or (part == 'r' and x == w - 1)
            if part == 'l' and x + min(y, h - 1 - y) == 2:
                ring = True
            if part == 'r' and (w - 1 - x) + min(y, h - 1 - y) == 2:
                ring = True
            if ring:
                img.putpixel((x, y), (235, 235, 235, 200))
            elif y == 1:
                img.putpixel((x, y), (120, 120, 120, 190))
    return img


card_sheets = []


def cards():
    """Card pieces for 1 to 4 rows of cells, in libreria:row<K> (their top on cell row K). And "line": the card of a
    list row, 16 px high like the item picture beside it (its top 1 px below the row's top, level with the item)."""
    for hc in range(0, 5):
        h = 16 if hc == 0 else 18 * hc - 2 * CARD_M
        pre = 'line' if hc == 0 else f'card{hc}'
        cells = [(f'{pre}.l', card_piece(4, h, 'l')), (f'{pre}.r', card_piece(4, h, 'r'))]
        cells += [(f'{pre}.m{w}', card_piece(w, h, 'm')) for w in (64, 32, 16, 8, 4, 2, 1)]
        cw = max(im.width for _, im in cells)
        sheet = Image.new('RGBA', (cw * len(cells), h), (0, 0, 0, 0))
        chars = ''
        for i, (name, im) in enumerate(cells):
            sheet.paste(im, (i * cw, 0))
            cp = next_cp()
            chars += chr(cp)
            glyphs[name] = [cp, im.width + 1]
        path = os.path.join(OUT, 'assets', NS, f'textures/font/ui/card_{hc}.png')
        sheet.save(path)
        card_sheets.append({'file': f'{NS}:font/ui/card_{hc}.png', 'height': h, 'chars': [chars], 'top': 1 if hc == 0 else CARD_M})
    # The fill of a bar inside a list row ("fill.m…"): 10 px high, 3 px below the row card's top, tinted by the text
    # colour (a muted shade, so white text on it stays readable)
    h = 10
    cells = []
    for w in (64, 32, 16, 8, 4, 2, 1):
        im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        for y in range(h):
            v = 210 if y == 0 else 120 if y == h - 1 else 150
            for x in range(w):
                put(im, x, y, (v, v, v, 255))
        cells.append((f'fill.m{w}', im))
    cw = max(im.width for _, im in cells)
    sheet = Image.new('RGBA', (cw * len(cells), h), (0, 0, 0, 0))
    chars = ''
    for i, (name, im) in enumerate(cells):
        sheet.paste(im, (i * cw, 0))
        cp = next_cp()
        chars += chr(cp)
        glyphs[name] = [cp, im.width + 1]
    sheet.save(os.path.join(OUT, 'assets', NS, 'textures/font/ui/bar_fill.png'))
    card_sheets.append({'file': f'{NS}:font/ui/bar_fill.png', 'height': h, 'chars': [chars], 'top': 4})
    # A thin bar under the text of a list row ("thin.m…"): 2 px high, 13 px below the row card's top (the text sits a
    # pixel higher above it), tinted: once in a dark shade for the track, then in the colour for how far
    h = 2
    cells = []
    for w in (64, 32, 16, 8, 4, 2, 1):
        im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        for x in range(w):
            put(im, x, 0, (235, 235, 235, 255))
            put(im, x, 1, (170, 170, 170, 255))
        cells.append((f'thin.m{w}', im))
    cw = max(im.width for _, im in cells)
    sheet = Image.new('RGBA', (cw * len(cells), h), (0, 0, 0, 0))
    chars = ''
    for i, (name, im) in enumerate(cells):
        sheet.paste(im, (i * cw, 0))
        cp = next_cp()
        chars += chr(cp)
        glyphs[name] = [cp, im.width + 1]
    sheet.save(os.path.join(OUT, 'assets', NS, 'textures/font/ui/bar_thin.png'))
    card_sheets.append({'file': f'{NS}:font/ui/bar_thin.png', 'height': h, 'chars': [chars], 'top': 13})


def window_panel():
    """The window itself: the panel over the whole chest screen (176 x 222), top at y 0."""
    img = Image.new('RGBA', (WIN_W, WIN_H), (0, 0, 0, 0))
    panel_box(img, 0, 0, WIN_W, WIN_H)
    # a thin line under the header
    for x in range(7, WIN_W - 7):
        t = abs(x - WIN_W / 2) / (WIN_W / 2 - 7)
        put(img, x, 15, LOOK['accent_lo'][:3] + (int(200 * (1 - t ** 2)),))
    return img


# Minecraft's own item pictures, twice as big (32 px), for the cards of a window. They are referenced, not copied, and
# exist only at a few heights (a card's top + 4, + 6 or + 8), so the pack stays light.
BIG_ICONS = ['bundle', 'filled_map', 'nether_star', 'name_tag', 'compass_00', 'comparator', 'book', 'writable_book',
             'lantern', 'paper', 'ender_eye', 'knowledge_book', 'recovery_compass_00', 'clock_00', 'spyglass', 'map']
ICON_Y = sorted({top + CARD_M + d for top in ROW_TOP for d in (4, 6, 8)})
big_icon_chars = {}


small_icon_chars = {}


def big_icons():
    """The same pictures at 32 px ("ico.…") and at their own 16 px ("ico16.…")."""
    for name in BIG_ICONS:
        im = Image.open(os.path.join(VANILLA, 'textures/item', name + '.png')).convert('RGBA')
        first = im.crop((0, 0, im.width, im.width))
        w = actual_width(first, 0, first.width, first.height)
        cp = next_cp()
        big_icon_chars[name] = chr(cp)
        # Minecraft's advance for a picture: its width at this size, plus one
        glyphs['ico.' + name] = [cp, int(0.5 + w * 32 / im.width) + 1]
        cp = next_cp()
        small_icon_chars[name] = chr(cp)
        glyphs['ico16.' + name] = [cp, int(0.5 + w * 16 / im.width) + 1]


def tooltip_style():
    """libreria:panel, the tooltip of Libreria's windows and menus: the look's panel (Minecraft draws the sprite 12 px
    beyond the text on every side; the panel starts 5 px in, so there are 3 px of room around the text)."""
    d = os.path.join(OUT, 'assets', NS, 'textures/gui/sprites/tooltip')
    os.makedirs(d, exist_ok=True)
    L = LOOK
    img = Image.new('RGBA', (100, 100), (0, 0, 0, 0))
    o = 5
    box = Image.new('RGBA', (100 - 2 * o, 100 - 2 * o), (0, 0, 0, 0))
    panel_box(box, 0, 0, box.width, box.height)
    # flat fill: the middle of the sprite is repeated, a gradient would show stripes
    for y in range(4, box.height - 4):
        for x in range(4, box.width - 4):
            box.putpixel((x, y), lerp4(L['panel_top'], L['panel_bottom'], 0.5)[:3] + (248,))
    img.alpha_composite(box, (o, o))
    img.save(os.path.join(d, 'panel_background.png'))
    json.dump({'gui': {'scaling': {'type': 'nine_slice', 'width': 100, 'height': 100, 'border': 12}}},
              open(os.path.join(d, 'panel_background.png.mcmeta'), 'w'))
    Image.new('RGBA', (100, 100), (0, 0, 0, 0)).save(os.path.join(d, 'panel_frame.png'))
    json.dump({'gui': {'scaling': {'type': 'nine_slice', 'width': 100, 'height': 100, 'border': 12}}},
              open(os.path.join(d, 'panel_frame.png.mcmeta'), 'w'))


def switches():
    """Item models for a window's switches: a pill with its knob right (on, green) or left (off, dark)."""
    base = os.path.join(OUT, 'assets', NS)
    for sub in ('textures/item/gui', 'models/item/gui', 'items/gui'):
        os.makedirs(os.path.join(base, sub), exist_ok=True)
    for name, on in (('switch_on', True), ('switch_off', False)):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        track = (76, 170, 72) if on else (46, 36, 28)
        rim = (40, 96, 38) if on else (18, 11, 7)
        for y in range(4, 12):
            for x in range(1, 15):
                r = 4
                # rounded ends
                cx = 5 if x < 5 else 10 if x > 10 else x
                if (x - cx) ** 2 + (y - 7.5) ** 2 > 16.5:
                    continue
                edge = (x - cx) ** 2 + (y - 7.5) ** 2 > 10 or y in (4, 11)
                img.putpixel((x, y), (rim if edge else track) + (255,))
        kx = 10 if on else 5
        for y in range(4, 12):
            for x in range(kx - 3, kx + 4):
                d = (x - kx) ** 2 + (y - 7.5) ** 2
                if d <= 10:
                    c = (250, 244, 228) if d <= 5 else (200, 190, 170)
                    img.putpixel((x, y), c + (255,))
        img.save(os.path.join(base, f'textures/item/gui/{name}.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/gui/{name}'}, 'gui_light': 'front'},
                  open(os.path.join(base, f'models/item/gui/{name}.json'), 'w'))
        json.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/gui/{name}'}},
                  open(os.path.join(base, f'items/gui/{name}.json'), 'w'))


CANVAS_CHARS = []


def check_font(name, provs):
    """Minecraft drops a whole font (every letter becomes a box) if one bitmap's ascent is above its height."""
    for p in provs:
        if p['type'] == 'bitmap':
            assert p['ascent'] <= p.get('height', 8), (name, p['file'], p['ascent'], p.get('height', 8))


def canvas_fonts():
    """libreria:y<N> (text and big emblems with their top at y N) and libreria:row<K> (cards)."""
    default = json.load(open(os.path.join(VANILLA, 'font/include/default.json')))['providers']
    # What a window's text can show: a character outside these would be drawn as a box on the title line
    chars = {' '}
    for p in default:
        for row in p.get('chars', []):
            chars.update(ch for ch in row if ch != '\u0000')
    for p in providers:
        if p['type'] == 'bitmap' and p['file'].split('/')[-1] in ('icons.png', 'marks.png'):
            for row in p['chars']:
                chars.update(row)
    CANVAS_CHARS[:] = sorted(chars)
    space = [p for p in providers if p['type'] == 'space']
    base = os.path.join(OUT, 'assets', NS, 'font')
    os.makedirs(base, exist_ok=True)
    for n in CANVAS_Y:
        shift = n - 6
        provs = [{'type': 'space', 'advances': {' ': 4}}] + space
        for p in default:
            q = dict(p)
            q['ascent'] = p.get('ascent', 7) - shift
            provs.append(q)
        for p in canvas_pictures:
            provs.append({'type': 'bitmap', 'file': p['file'], 'height': p['height'], 'ascent': 13 - n, 'chars': p['chars']})
        # the small pictures text may hold (icon.…, mark.…), moved like the text
        for p in providers:
            if p['type'] == 'bitmap' and p['file'].split('/')[-1] in ('icons.png', 'marks.png'):
                q = dict(p)
                q['ascent'] = p['ascent'] - shift
                if q['ascent'] <= q['height']:
                    provs.append(q)
        if n in ICON_Y:
            for name, ch in big_icon_chars.items():
                provs.append({'type': 'bitmap', 'file': f'minecraft:item/{name}.png', 'height': 32, 'ascent': 13 - n, 'chars': [ch]})
            for name, ch in small_icon_chars.items():
                provs.append({'type': 'bitmap', 'file': f'minecraft:item/{name}.png', 'height': 16, 'ascent': 13 - n, 'chars': [ch]})
        check_font(f'y{n}', provs)
        json.dump({'providers': provs}, open(os.path.join(base, f'y{n}.json'), 'w'))
    for k, top in enumerate(ROW_TOP):
        provs = list(space)
        for p in card_sheets:
            provs.append({'type': 'bitmap', 'file': p['file'], 'height': p['height'], 'ascent': 13 - (top + p['top']), 'chars': p['chars']})
        check_font(f'row{k}', provs)
        json.dump({'providers': provs}, open(os.path.join(base, f'row{k}.json'), 'w'))


# ------------------------------------------------------------------ the item search screen

ASCII = None


def _ascii():
    global ASCII
    if ASCII is None:
        ASCII = Image.open(os.path.join(VANILLA, 'textures/font/ascii.png')).convert('RGBA')
    return ASCII


def char_width(c):
    if c == ' ':
        return 4
    code = ord(c)
    return actual_width(_ascii().crop(((code % 16) * 8, (code // 16) * 8, (code % 16) * 8 + 8, (code // 16) * 8 + 8)), 0, 8, 8) + 1


def text_width(text):
    return sum(char_width(c) for c in text) - 1


def draw_text(img, x, y, text, color):
    """Minecraft's own font, pixel for pixel (for words that never change)."""
    a = _ascii()
    for c in text:
        if c != ' ':
            code = ord(c)
            cx, cy = (code % 16) * 8, (code // 16) * 8
            for yy in range(8):
                for xx in range(8):
                    if a.getpixel((cx + xx, cy + yy))[3] > 0:
                        px(img, x + xx, y + yy, color + (255,))
        x += char_width(c)


# The search screen is a bit bigger than the anvil under it: this much on each side (left/right, top/bottom)
SEARCH_OX, SEARCH_OY = 8, 6


def search_frame():
    """Your items: a search screen in the Libreria look, drawn by the title of an anvil screen (the only screen with a
    text field) over the whole anvil and a little beyond. One hole, where the anvil draws the text you type (it does so
    before the title); everything else is ours, and the items stay on top. Coordinates below are the anvil screen's
    own (0, 0 = its top left)."""
    W, H = 176 + 2 * SEARCH_OX, 166 + 2 * SEARCH_OY
    ox, oy = SEARCH_OX, SEARCH_OY
    L = LOOK
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    panel_box(img, 0, 0, W, H)
    muted, faint = LOOK['muted_rgb'], LOOK['faint_rgb']

    def hole(hx, hy, hw, hh):
        for yy in range(hy, hy + hh):
            for xx in range(hx, hx + hw):
                img.putpixel((ox + xx, oy + yy), (0, 0, 0, 0))

    # A thin line under the title
    for xx in range(-2, 178):
        t = abs(xx - 88) / 90
        put(img, ox + xx, oy + 16, L['accent_lo'][:3] + (int(200 * (1 - t ** 2)),))
    # The search bar: a magnifying glass and "Search" on the left, then the field you type in
    for yy in range(20, 36):
        for xx in range(-3, 180):
            edge = xx in (-3, 179) or yy in (20, 35)
            put(img, ox + xx, oy + yy, L['field_rim'] if edge else L['field'])
    for yy in range(21, 35):
        for xx in range(-2, 52):
            put(img, ox + xx, oy + yy, L['socket'])
        put(img, ox + 52, oy + yy, L['field_rim'])
    draw_icon(img, MAGNIFIER, ox + 0, oy + 22)
    draw_text(img, ox + 15, oy + 24, 'Search', muted)
    hole(60, 21, 108, 14)
    # Hints, where the anvil's first two slots were; the filter button sits in the anvil's result slot
    tray(img, ox - 3, oy + 40, 132, 26)
    for i, line in enumerate(('Click: light up its chest', 'Right-click: all  Shift: pin')):
        draw_text(img, ox + 2, oy + 44 + 10 * i, line, muted if i == 0 else faint)
    tray(img, ox + 131, oy + 40, 49, 26)
    draw_text(img, ox + 153, oy + 51, 'Show', muted)
    # The bar with the totals (the server writes them in) and the results, floating on one tray
    tray(img, ox - 3, oy + 68, 183, 13)
    tray(img, ox + 6, oy + 82, 164, 78)
    return img


def anvil_field():
    """The anvil's text field in the look's colours: the search screen shows it through its hole, and real anvils get
    the same field."""
    d = os.path.join(OUT, 'assets', 'minecraft', 'textures/gui/sprites/container/anvil')
    os.makedirs(d, exist_ok=True)
    for name, fill in (('text_field', LOOK['field']), ('text_field_disabled', LOOK['socket'])):
        # opaque: the anvil's own picture lies under it
        img = Image.new('RGBA', (110, 16), lerp4(LOOK['panel_bottom'], (0, 0, 0, 255), 0.4)[:3] + (255,))
        for y in range(16):
            for x in range(110):
                put(img, x, y, fill)
        img.save(os.path.join(d, name + '.png'))


def frames():
    for rows in range(1, 7):
        cells = [(f'frame{rows}.{t}', frame(rows, t)) for t in THEMES]
        bitmap(f'frame_{rows}', cells, 114 + 18 * rows, 13)


def filler_tile():
    """Item model for empty menu slots. The menu's frame already covers Minecraft's slots, so it draws nothing."""
    base = os.path.join(OUT, 'assets', NS)
    os.makedirs(os.path.join(base, 'items/gui'), exist_ok=True)
    json.dump({'model': {'type': 'minecraft:empty'}}, open(os.path.join(base, 'items/gui/tile.json'), 'w'))


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
        # The cursor's frame is a saturated gold, so it stands out against the paler frames of the buildings
        rod = Image.new('RGBA', (16, 16), lerp(c, (255, 255, 255), 0.08 if name == 'cursor' else 0.5) + (245,))
        save(f'rod_{name}', rod, [standing, crossed])


def plaques():
    """Coloured glass panes in menus (headers, on/off lamps) become little coloured plaques on the menu's tray."""
    base = os.path.join(OUT, 'assets', NS)
    for sub in ('textures/item/gui', 'models/item/gui', 'items/gui'):
        os.makedirs(os.path.join(base, sub), exist_ok=True)
    for name, c in DYES.items():
        img = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
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
            img.putpixel((x, 16), (0, 0, 0, 90))
        for y in range(3, 16):
            img.putpixel((16, y), (0, 0, 0, 90))
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


MARKS = {
    'diamond': ['...#...', '..###..', '.#####.', '#######', '.#####.', '..###..', '...#...'],
    'house': ['...#...', '..###..', '.#####.', '#######', '.##.##.', '.##.##.', '.##.##.'],
    'skull': ['.#####.', '#######', '#..#..#', '#######', '.#####.', '.#.#.#.', '.......'],
    'grave': ['..###..', '.##.##.', '.#...#.', '.##.##.', '.##.##.', '.#####.', '#######'],
    'target': ['..###..', '.#...#.', '#.....#', '#..#..#', '#.....#', '.#...#.', '..###..'],
    'person': ['..###..', '..###..', '...#...', '.#####.', '...#...', '..#.#..', '.#...#.'],
    'paw': ['..#.#..', '..#.#..', '#.....#', '#.###.#', '.#####.', '.#####.', '..###..'],
}


def marks():
    """Small white marker shapes the server tints (waypoints, your land, death point, graves, the beam you follow),
    for the labels in the land map."""
    cells = []
    for name, rows in MARKS.items():
        im = Image.new('RGBA', (7, 7), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == '#':
                    im.putpixel((x, y), (255, 255, 255, 255))
        cells.append(('mark.' + name, im))
    bitmap('marks', cells, 7, 7)


# Every language Minecraft ships, so the sort tab shows whatever language you play in
LANGS = ('af_za ar_sa ast_es az_az ba_ru bar be_by be_latn bg_bg br_fr brb bs_ba ca_es cs_cz cy_gb da_dk de_at de_ch de_de '
         'el_gr en_au en_ca en_gb en_nz en_pt en_ud en_us enp enws eo_uy es_ar es_cl es_ec es_es es_mx es_uy es_ve esan et_ee '
         'eu_es fa_ir fi_fi fil_ph fo_fo fr_ca fr_fr fra_de fur_it fy_nl ga_ie gd_gb gl_es haw_us he_il hi_in hn_no hr_hr hu_hu '
         'hy_am id_id ig_ng io_en is_is isv it_it ja_jp jbo_en ka_ge kk_kz kn_in ko_kr ksh kw_gb ky_kg la_la lb_lu li_li lmo '
         'lo_la lol_us lt_lt lv_lv lzh mk_mk mn_mn ms_my mt_mt nah nds_de nl_be nl_nl nn_no no_no oc_fr ovd pl_pl pls pt_br '
         'pt_pt qya_aa ro_ro rpr ru_ru ry_ua sah_sah se_no sk_sk sl_si so_so sq_al sr_cs sr_sp sv_se sxu szl ta_in th_th tl_ph '
         'tlh_aa tok tr_tr tt_ru tzo_mx uk_ua val_es vec_it vi_vn vp_vl yi_de yo_ng zh_cn zh_hk zh_tw zlm_arab').split()
# The inventory screen draws its title ("Crafting") at (97, 6). The tab hangs off the left edge of the window, around
# the row of the recipe book button: two buttons, x -24..-4, y 52..69 and 71..88.
TAB_X, TAB_Y, LABEL_X, LABEL_Y = -24, 52, 97, 6
TAB_H = 37


def inventory_tab():
    """Two buttons on the left edge of your inventory, in Minecraft's own style: deposit (the arrows) over search (the
    magnifying glass). The inventory screen's one real button stays the recipe book, so these are drawn by the
    screen's title: the "Crafting" text is replaced by a picture that hangs outside the window. A click outside the
    window with nothing in your hand reaches the server (vanilla ignores it), but not where: left-click deposits,
    right-click searches, middle-click sorts. The picture says which button is which."""
    tex = os.path.join(OUT, 'assets', NS, 'textures/font/ui/inv_tab.png')
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    img = Image.new('RGBA', (20, TAB_H), (0, 0, 0, 0))
    top = plank(20, 18, 'on', 0, 0)
    draw_icon(top, SORT, 4, 3)
    low = plank(20, 18, 'on', 0, 0)
    draw_icon(low, MAGNIFIER, 4, 3)
    img.alpha_composite(top, (0, 0))
    img.alpha_composite(low, (0, TAB_H - 18))
    img.save(tex)
    tab, back = '\ue9f0', '\ue9f1'
    font = {'providers': [
        {'type': 'space', 'advances': {back: TAB_X - LABEL_X}},
        # baseline of the title = its top + 7; the picture's top must be at TAB_Y
        {'type': 'bitmap', 'file': f'{NS}:font/ui/inv_tab.png', 'height': TAB_H, 'ascent': LABEL_Y + 7 - TAB_Y, 'chars': [tab]},
    ]}
    d = os.path.join(OUT, 'assets', 'minecraft', 'font')
    os.makedirs(d, exist_ok=True)
    json.dump(font, open(os.path.join(d, 'default.json'), 'w'))
    ld = os.path.join(OUT, 'assets', 'minecraft', 'lang')
    os.makedirs(ld, exist_ok=True)
    for code in LANGS:
        # white (a title is drawn dark grey), back to the tab's place, the tab. And no "Inventory" line above your
        # inventory in chests and other screens: it says nothing you don't see, and Libreria's windows use that spot
        lang = {'container.crafting': '\u00a7f' + back + tab, 'container.inventory': ''}
        # Words the game writes itself on its (now dark) screens: light, in English (other languages keep their own)
        if code.startswith('en_'):
            lang['merchant.trades'] = '\u00a77Trades'
        json.dump(lang, open(os.path.join(ld, code + '.json'), 'w'))


def dialog_warning():
    """Windows a server sends (dialogs) get a warning button beside their title; the pack makes it invisible."""
    d = os.path.join(OUT, 'assets', 'minecraft', 'textures/gui/sprites/dialog')
    os.makedirs(d, exist_ok=True)
    for name in ('warning_button', 'warning_button_disabled', 'warning_button_highlighted'):
        Image.new('RGBA', (20, 20), (0, 0, 0, 0)).save(os.path.join(d, name + '.png'))


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
    # Someone crouching: "sneak" (with whatever key the player put it on), not the Shift key itself
    for y, row in enumerate(['..##.', '..##.', '.##..', '#.##.', '..#..', '.#.#.', '#..#.']):
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


# ------------------------------------------------------------------ the Libreria look for Minecraft's own widgets
# Buttons, checkboxes, sliders, text fields and scroll bars are Minecraft's own (a window the server sends is built from
# them), so they get the look of the cards at the top of the screen. That holds for every screen while you play here:
# one look, from the pause menu to the windows of Libreria.
#
# Minecraft puts only 2 px between the buttons of a window, so every widget is drawn with a see-through margin inside
# its own box: what you see has room around it (MARGIN_X / MARGIN_Y on each side).

MARGIN_X, MARGIN_Y = 2, 1

LOOKS = {
    # Warm dark wood with a bronze rim, like the cards at the top of the screen
    'wood': dict(
        outline=(18, 11, 7, 255), radius=1,
        on=dict(rim_tl=(160, 120, 64, 255), rim_br=(110, 79, 40, 255), top=(76, 53, 35, 255), bottom=(52, 36, 24, 255),
                hi=(98, 70, 45, 255), shade=(36, 25, 16, 255)),
        hover=dict(rim_tl=(240, 204, 120, 255), rim_br=(214, 166, 84, 255), top=(104, 74, 47, 255), bottom=(78, 55, 35, 255),
                   hi=(150, 110, 66, 255), shade=(58, 41, 26, 255)),
        off=dict(rim_tl=(66, 51, 38, 255), rim_br=(66, 51, 38, 255), top=(42, 33, 26, 255), bottom=(42, 33, 26, 255),
                 hi=None, shade=None),
        socket=(26, 17, 11, 255), socket_shadow=(12, 8, 5, 255), socket_light=(82, 58, 36, 255),
        field=(14, 9, 6, 255), field_rim=(122, 86, 38, 255),
        accent=(240, 204, 120, 255), accent_mid=(198, 152, 74, 255), accent_lo=(122, 86, 38, 255),
        background=(18, 11, 6, 104),
        panel_top=(50, 35, 24, 255), panel_bottom=(30, 21, 14, 255), panel_rim=((240, 204, 120, 255), (198, 152, 74, 255), (40, 26, 16, 255)),
        panel_rim_lo=((122, 86, 38, 255), (198, 152, 74, 255), (40, 26, 16, 255)), studs=True,
        tray=(22, 15, 9, 255), tray_shadow=(12, 8, 5, 255), tray_light=(66, 47, 30, 255), toolbar=(30, 21, 13, 255),
        tag=(205, 188, 150, 255), tag_hi=(222, 208, 174, 255), tag_lo=(176, 158, 120, 255),
        title=0xECDCB4, title_shadow=0x140C06, neutral=0xC8A060, muted=0xB9AD95,
        muted_rgb=(200, 190, 170), faint_rgb=(150, 140, 122),
    ),
    # Modern and flat: smoked glass with a thin light edge; the world shows through a little
    'glass': dict(
        outline=(0, 0, 0, 0), radius=2,
        on=dict(rim_tl=(255, 255, 255, 52), rim_br=(255, 255, 255, 34), top=(16, 18, 24, 196), bottom=(10, 11, 15, 210),
                hi=(255, 255, 255, 22), shade=None),
        hover=dict(rim_tl=(255, 255, 255, 200), rim_br=(255, 255, 255, 160), top=(46, 50, 60, 214), bottom=(34, 37, 45, 222),
                   hi=(255, 255, 255, 40), shade=None),
        off=dict(rim_tl=(255, 255, 255, 20), rim_br=(255, 255, 255, 20), top=(10, 10, 12, 130), bottom=(10, 10, 12, 130),
                 hi=None, shade=None),
        socket=(0, 0, 0, 120), socket_shadow=(0, 0, 0, 120), socket_light=(255, 255, 255, 26),
        field=(0, 0, 0, 170), field_rim=(255, 255, 255, 60),
        accent=(255, 255, 255, 235), accent_mid=(205, 210, 220, 255), accent_lo=(120, 126, 140, 255),
        background=(6, 8, 12, 120),
        # Windows are drawn over Minecraft's own chest picture, so their glass must not let that show through
        panel_top=(20, 22, 28, 255), panel_bottom=(13, 14, 18, 255), panel_rim=((255, 255, 255, 56), (0, 0, 0, 0), (0, 0, 0, 0)),
        panel_rim_lo=((255, 255, 255, 36), (0, 0, 0, 0), (0, 0, 0, 0)), studs=False,
        tray=(0, 0, 0, 90), tray_shadow=(0, 0, 0, 90), tray_light=(255, 255, 255, 18), toolbar=(255, 255, 255, 12),
        tag=(232, 234, 240, 255), tag_hi=(250, 250, 252, 255), tag_lo=(200, 204, 214, 255),
        title=0xFFFFFF, title_shadow=0x000000, neutral=0xC4CAD6, muted=0xA6ACB8,
        muted_rgb=(214, 218, 226), faint_rgb=(150, 156, 168),
    ),
    # Cool deepslate with a thin gold edge: crisp, a little more serious
    'slate': dict(
        outline=(14, 15, 18, 255), radius=1,
        on=dict(rim_tl=(92, 98, 110, 255), rim_br=(58, 62, 72, 255), top=(62, 67, 78, 255), bottom=(46, 50, 59, 255),
                hi=(80, 86, 98, 255), shade=(36, 39, 46, 255)),
        hover=dict(rim_tl=(236, 196, 110, 255), rim_br=(196, 150, 70, 255), top=(78, 84, 97, 255), bottom=(60, 65, 76, 255),
                   hi=(100, 107, 121, 255), shade=(46, 50, 59, 255)),
        off=dict(rim_tl=(48, 51, 58, 255), rim_br=(48, 51, 58, 255), top=(36, 38, 44, 255), bottom=(36, 38, 44, 255),
                 hi=None, shade=None),
        socket=(28, 30, 36, 255), socket_shadow=(16, 17, 21, 255), socket_light=(74, 79, 90, 255),
        field=(16, 17, 21, 255), field_rim=(92, 98, 110, 255),
        accent=(236, 196, 110, 255), accent_mid=(196, 150, 70, 255), accent_lo=(120, 90, 40, 255),
        background=(8, 10, 14, 104),
        panel_top=(44, 48, 56, 255), panel_bottom=(32, 35, 41, 255), panel_rim=((110, 116, 128, 255), (70, 75, 86, 255), (24, 26, 31, 255)),
        panel_rim_lo=((50, 54, 62, 255), (70, 75, 86, 255), (24, 26, 31, 255)), studs=False,
        tray=(26, 28, 33, 255), tray_shadow=(16, 17, 21, 255), tray_light=(64, 69, 80, 255), toolbar=(34, 37, 43, 255),
        tag=(208, 212, 222, 255), tag_hi=(228, 231, 238, 255), tag_lo=(170, 175, 188, 255),
        title=0xE8EBF2, title_shadow=0x0A0B0E, neutral=0xB8BECA, muted=0xA0A6B2,
        muted_rgb=(210, 214, 222), faint_rgb=(150, 156, 168),
    ),
}
LOOK = LOOKS[os.environ.get('LIBRERIA_LOOK', 'glass')]


def sprite_dir(*sub):
    d = os.path.join(OUT, 'assets', 'minecraft', 'textures/gui/sprites', *sub)
    os.makedirs(d, exist_ok=True)
    return d


def save_sprite(img, d, name, scaling=None):
    img.save(os.path.join(d, name + '.png'))
    if scaling:
        json.dump({'gui': {'scaling': scaling}}, open(os.path.join(d, name + '.png.mcmeta'), 'w'))


def put(img, x, y, c):
    """Like px, but draws over what's there (for see-through colours)."""
    if c is None or not (0 <= x < img.width and 0 <= y < img.height):
        return
    if len(c) == 3:
        c = c + (255,)
    img.alpha_composite(Image.new('RGBA', (1, 1), c), (x, y))


def lerp4(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def rounded(x, y, w, h, r):
    """Is (x, y) outside the rounded corners of a w×h box?"""
    for cx, cy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        dx, dy = abs(x - cx), abs(y - cy)
        if dx + dy < r:
            return True
    return False


def plank(w, h, state, mx=MARGIN_X, my=MARGIN_Y):
    """A button. state: 'on', 'hover' (mouse over or focused) or 'off' (can't be pressed)."""
    st = LOOK[state]
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    bw, bh = w - 2 * mx, h - 2 * my
    r = LOOK['radius']
    has_outline = LOOK['outline'][3] > 0
    for y in range(bh):
        for x in range(bw):
            if rounded(x, y, bw, bh, r + (1 if has_outline else 0)):
                continue
            ring = 0 if has_outline else 1
            if has_outline and (x in (0, bw - 1) or y in (0, bh - 1)):
                c = LOOK['outline']
            elif x in (ring, bw - 1 - ring) or y in (ring, bh - 1 - ring):
                bottom_right = (x == bw - 1 - ring or y == bh - 1 - ring) and not (x == ring or y == ring)
                c = st['rim_br'] if bottom_right else st['rim_tl']
                put(img, mx + x, my + y, lerp4(st['top'], st['bottom'], 0.5))
            else:
                c = lerp4(st['top'], st['bottom'], (y - ring - 1) / max(1, bh - 2 * ring - 3))
                if st['hi'] is not None and y == ring + 1:
                    put(img, mx + x, my + y, c)
                    c = st['hi']
                elif st['shade'] is not None and y == bh - 2 - ring:
                    c = st['shade']
            put(img, mx + x, my + y, c)
    return img


def socket(w, h, rim=None, fill=None, m=1):
    """A recessed box (text fields, checkboxes, slider tracks): shadow at the top left, light at the bottom right."""
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    fill = fill or LOOK['socket']
    rim = rim or (LOOK['outline'] if LOOK['outline'][3] else LOOK['socket_light'])
    bw, bh = w - 2 * m, h - 2 * m
    for y in range(bh):
        for x in range(bw):
            if rounded(x, y, bw, bh, LOOK['radius'] + 1):
                continue
            if x in (0, bw - 1) or y in (0, bh - 1):
                c = rim
            elif x == 1 or y == 1:
                c = LOOK['socket_shadow']
            elif x == bw - 2 or y == bh - 2:
                c = LOOK['socket_light']
            else:
                c = fill
            put(img, m + x, m + y, c)
    return img


CHECK = ['........##', '.......###', '......###.', '##...###..', '###.###...', '.#####....', '..###.....', '...#......']


def look_widgets():
    d = sprite_dir('widget')
    btn = {'type': 'nine_slice', 'width': 200, 'height': 20,
           'border': {'left': MARGIN_X + 3, 'right': MARGIN_X + 3, 'top': MARGIN_Y + 3, 'bottom': MARGIN_Y + 3}}
    save_sprite(plank(200, 20, 'on'), d, 'button', btn)
    save_sprite(plank(200, 20, 'hover'), d, 'button_highlighted', btn)
    save_sprite(plank(200, 20, 'off'), d, 'button_disabled', btn)

    # Checkbox: Minecraft draws it 17 px square (font height + 8), so it is drawn at exactly that size
    for name, focus, checked in (('checkbox', False, False), ('checkbox_highlighted', True, False),
                                 ('checkbox_selected', False, True), ('checkbox_selected_highlighted', True, True)):
        img = socket(17, 17, LOOK['accent'] if focus else None)
        if checked:
            for y, row in enumerate(CHECK):
                for x, ch in enumerate(row):
                    if ch == '#':
                        put(img, 3 + x, 5 + y, (0, 0, 0, 140))
            for y, row in enumerate(CHECK):
                for x, ch in enumerate(row):
                    if ch == '#':
                        put(img, 3 + x, 4 + y, LOOK['accent'] if y < 4 else LOOK['accent_mid'])
        save_sprite(img, d, name)

    # Text fields and slider tracks: a recessed box; the accent colour when it has focus
    flat = {'type': 'nine_slice', 'width': 200, 'height': 20, 'border': 3}
    save_sprite(socket(200, 20, LOOK['field_rim'], LOOK['field']), d, 'text_field', flat)
    save_sprite(socket(200, 20, LOOK['accent'], LOOK['field']), d, 'text_field_highlighted', flat)
    save_sprite(socket(200, 20), d, 'slider', flat)
    save_sprite(socket(200, 20, LOOK['accent_lo']), d, 'slider_highlighted', flat)

    # The slider's handle: a small knob in the accent colour with three grip lines
    def knob(hover):
        img = Image.new('RGBA', (8, 20), (0, 0, 0, 0))
        top = LOOK['accent'] if hover else LOOK['accent_mid']
        bottom = LOOK['accent_mid'] if hover else LOOK['accent_lo']
        for y in range(1, 19):
            for x in range(8):
                if rounded(x, y - 1, 8, 18, 2):
                    continue
                if x in (0, 7) or y in (1, 18):
                    c = (0, 0, 0, 200)
                else:
                    c = lerp4(top, bottom, (y - 2) / 15)
                    if y in (7, 9, 11) and 2 <= x <= 5:
                        c = lerp4(c, (0, 0, 0, 255), 0.35)
                put(img, x, y, c)
        return img

    handle = {'type': 'nine_slice', 'width': 8, 'height': 20, 'border': {'left': 2, 'top': 3, 'right': 2, 'bottom': 3}}
    save_sprite(knob(False), d, 'slider_handle', handle)
    save_sprite(knob(True), d, 'slider_handle_highlighted', handle)

    # Scroll bar: a thumb in the accent colour in a dark groove
    bar = {'type': 'nine_slice', 'width': 6, 'height': 32, 'border': 1}
    thumb = Image.new('RGBA', (6, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(1, 5):
            put(thumb, x, y, LOOK['accent'] if x == 1 else LOOK['accent_mid'] if x < 4 else LOOK['accent_lo'])
    save_sprite(thumb, d, 'scroller', bar)
    groove = Image.new('RGBA', (6, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(1, 5):
            put(groove, x, y, LOOK['socket'])
    save_sprite(groove, d, 'scroller_background', bar)


def look_slot_highlight():
    """The square under the mouse in every inventory: corners in the accent colour around the slot and a soft glow,
    all drawn behind the item (the front part is empty, so nothing lies over the item itself)."""
    d = sprite_dir('container')
    scaling = {'type': 'nine_slice', 'width': 24, 'height': 24, 'border': 4}
    a = LOOK['accent']
    back = Image.new('RGBA', (24, 24), (0, 0, 0, 0))
    for y in range(4, 20):
        for x in range(4, 20):
            back.putpixel((x, y), a[:3] + (46,))
    arm = 5
    for (cx, cy, sx, sy) in ((3, 3, 1, 1), (20, 3, -1, 1), (3, 20, 1, -1), (20, 20, -1, -1)):
        for i in range(1, arm - 1):
            for (x, y) in ((cx + sx * i, cy + sy), (cx + sx, cy + sy * i)):
                back.putpixel((x, y), (0, 0, 0, 110))
        for i in range(arm):
            for (x, y) in ((cx + sx * i, cy), (cx, cy + sy * i)):
                back.putpixel((x, y), a[:3] + (240,))
    save_sprite(back, d, 'slot_highlight_back', scaling)
    save_sprite(Image.new('RGBA', (24, 24), (0, 0, 0, 0)), d, 'slot_highlight_front', scaling)


def look_background():
    """Behind windows and the pause menu: the world a little darker, so the widgets read well on it."""
    d = os.path.join(OUT, 'assets', 'minecraft', 'textures/gui')
    os.makedirs(d, exist_ok=True)
    Image.new('RGBA', (16, 16), LOOK['background']).save(os.path.join(d, 'inworld_menu_background.png'))


def boss_bar():
    """The boss bar of the top tooltip uses the white colour; the pack makes white bars invisible (only the title)."""
    d = os.path.join(OUT, 'assets', 'minecraft', 'textures/gui/sprites/boss_bar')
    os.makedirs(d, exist_ok=True)
    for name in ('white_background', 'white_progress'):
        Image.new('RGBA', (182, 5), (0, 0, 0, 0)).save(os.path.join(d, name + '.png'))


def main():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    base = os.path.join(OUT, 'assets', NS)
    for sub in ('textures/font/ui', 'font', 'items/gui', 'models/item/gui', 'textures/item/gui', 'items/claim',
                'models/item/claim', 'textures/item/claim'):
        shutil.rmtree(os.path.join(base, sub), ignore_errors=True)
    # Minecraft's screens are recoloured from the game's own pictures (containers.py); without them at hand, keep the
    # ones made before
    import containers as container_screens
    keep = None
    if not os.path.isdir(os.path.join(container_screens.VANILLA, 'container')):
        keep = os.path.join(OUT, '..', 'kept_screens')
        shutil.rmtree(keep, ignore_errors=True)
        gui = os.path.join(OUT, 'assets', 'minecraft', 'textures', 'gui')
        for rel in ('container', 'sprites/container', 'recipe_book.png'):
            src = os.path.join(gui, rel)
            if os.path.isdir(src):
                shutil.copytree(src, os.path.join(keep, rel))
            elif os.path.exists(src):
                os.makedirs(keep, exist_ok=True)
                shutil.copy(src, os.path.join(keep, rel))
    shutil.rmtree(os.path.join(OUT, 'assets', 'minecraft'), ignore_errors=True)
    if keep:
        gui = os.path.join(OUT, 'assets', 'minecraft', 'textures', 'gui')
        os.makedirs(gui, exist_ok=True)
        shutil.copytree(keep, gui, dirs_exist_ok=True)
        shutil.rmtree(keep, ignore_errors=True)
    spaces()
    panels()
    gem()
    slot()
    divider()
    pips()
    icons()
    frames()
    canvas_pictures_sheet()
    big_icons()
    cards()
    tooltip_style()
    switches()
    bitmap('window', [('win.panel', window_panel())], WIN_H, 13)
    # Its top is SEARCH_OY above the anvil's top; the title's baseline is 13 below that top
    bitmap('search_frame', [('frame.search', search_frame())], 166 + 2 * SEARCH_OY, 13 + SEARCH_OY)
    filler_tile()
    plaques()
    empty_item()
    claim_buildings()
    marks()
    os.makedirs(os.path.join(base, 'font'), exist_ok=True)
    json.dump({'providers': providers}, open(os.path.join(base, 'font/ui.json'), 'w'), indent=1)
    icon_map = hud_fonts()
    canvas_fonts()
    boss_bar()
    look_widgets()
    look_slot_highlight()
    look_background()
    dialog_warning()
    anvil_field()
    inventory_tab()
    hotbar_font()
    # Minecraft's own screens and the book in the same look (they live under assets/minecraft too, wiped above)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import book as book_screen
    container_screens.OUT = OUT
    if keep is None:
        container_screens.main()
    path = os.path.join(OUT, 'assets/minecraft/textures/gui/book.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    book_screen.book().save(path)
    meta = {"hudLines": HUD_LINES, "icons": icon_map, 'lineHeight': LH, 'padX': PAD_X, 'padTop': PAD_TOP, 'padBottom': PAD_BOTTOM,
            'panelLines': [min(PANEL_LINES), max(PANEL_LINES)], 'themes': list(THEMES), 'glyphs': glyphs,
            'look': {'title': LOOK['title'], 'titleShadow': LOOK['title_shadow'], 'neutral': LOOK['neutral'], 'muted': LOOK['muted']}, 'iconY': ICON_Y,
            'canvasChars': ''.join(CANVAS_CHARS)}
    os.makedirs(os.path.join(OUT, NS), exist_ok=True)
    json.dump(meta, open(os.path.join(OUT, NS, 'ui_glyphs.json'), 'w'), indent=1)
    print(len(glyphs), 'glyphs,', len(providers), 'providers')


if __name__ == '__main__':
    main()
