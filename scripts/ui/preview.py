#!/usr/bin/env python3
"""Local preview of the UI assets (not shipped): renders a tooltip card and a menu frame like Minecraft would,
using the vanilla font from a local assets checkout. Usage: python3 preview.py <vanilla assets/minecraft> <out dir>"""
import json
import os
import sys

from PIL import Image

VAN = sys.argv[1]
OUT = sys.argv[2]
RES = os.path.join(os.path.dirname(__file__), '../../core/src/main/resources')
META = json.load(open(os.path.join(RES, 'libreria/ui_glyphs.json')))
FONT = json.load(open(os.path.join(RES, 'assets/libreria/font/ui.json')))

# ---- vanilla ascii font (for preview only)
ascii_img = Image.open(f'{VAN}/textures/font/ascii.png').convert('RGBA')
ascii_rows = json.load(open(f'{VAN}/font/include/default.json'))['providers'][-1]['chars']
ASCII = {}
for r, row in enumerate(ascii_rows):
    for c, ch in enumerate(row):
        cell = ascii_img.crop((c * 8, r * 8, c * 8 + 8, r * 8 + 8))
        w = 0
        for x in range(7, -1, -1):
            if any(cell.getpixel((x, y))[3] for y in range(8)):
                w = x + 1
                break
        ASCII[ch] = (cell, w + 1)

# ---- our glyphs
UI = {}
for p in FONT['providers']:
    if p['type'] == 'space':
        for ch, adv in p['advances'].items():
            UI[ch] = (None, adv, 0, 0)
        continue
    sheet = Image.open(os.path.join(RES, 'assets/libreria/textures', p['file'].split(':')[1].replace('font/', 'font/', 1)))
    chars = p['chars'][0]
    cw = sheet.width // len(chars)
    for i, ch in enumerate(chars):
        cell = sheet.crop((i * cw, 0, i * cw + cw, sheet.height))
        UI[ch] = (cell, None, p['ascent'], p['height'])
G = META['glyphs']


def g(name):
    return chr(G[name][0])


def tint(img, color):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, gg, b, a = px[x, y]
            px[x, y] = (r * color[0] // 255, gg * color[1] // 255, b * color[2] // 255, a)
    return out


def draw_line(canvas, x, y, runs, shadow=True):
    """runs: list of (text, color, font) with font 'ui' or 'default'."""
    for text, color, font in runs:
        for ch in text:
            if font == 'ui':
                cell, adv, ascent, height = UI[ch]
                if cell is None:
                    x += adv
                    continue
                w = G[[k for k, v in G.items() if v[0] == ord(ch)][0]][1]
                canvas.alpha_composite(tint(cell, color), (x, y + 7 - ascent))
                x += w
            else:
                cell, adv = ASCII.get(ch, ASCII['?'])
                if shadow:
                    canvas.alpha_composite(tint(cell, tuple(c // 4 for c in color)), (x + 1, y + 1))
                canvas.alpha_composite(tint(cell, color), (x, y))
                x += adv
    return x


def space(px):
    out = ''
    for k in range(9, -1, -1):
        v = 1 << k
        while abs(px) >= v:
            s = v if px > 0 else -v
            out += g(f'space{s:+d}')
            px -= s
    return out


def tiles(prefix, w):
    out = ''
    for t in (64, 32, 16, 8, 4, 2, 1):
        while w >= t:
            out += g(f'{prefix}{t}') + g('space-1')
            w -= t
    return out


WHITE, GRAY, GOLD, RED, GREEN, AQUA, YELLOW, PURPLE = (255, 255, 255), (170, 170, 170), (255, 170, 0), (255, 85, 85), \
    (85, 255, 85), (85, 255, 255), (255, 255, 85), (255, 85, 255)


def card(lines, inner_w, name, gem_color):
    LH = META['lineHeight']
    n = len(lines)
    pad_x, pad_top, pad_bottom = META['padX'], META['padTop'], META['padBottom']
    pw = inner_w + 2 * pad_x
    ph = pad_top + n * LH + pad_bottom
    img = Image.new('RGBA', (pw + 40, ph + 40), (110, 150, 200, 255))
    ox, oy = 20, 20
    # panel line: one line whose top is the content top minus... (panel glyph ascent places it)
    panel = g(f'panel{n}.l') + g('space-1') + tiles(f'panel{n}.m', pw - 12) + g(f'panel{n}.r')
    # content box top = oy + pad_top; panel display line top is the last content line's top
    draw_line(img, ox, oy + pad_top + (n - 1) * LH, [(panel, WHITE, 'ui')])
    for i, runs in enumerate(lines):
        draw_line(img, ox + pad_x, oy + pad_top + i * LH, runs)
    img = img.resize((img.width * 4, img.height * 4), Image.NEAREST)
    img.save(os.path.join(OUT, name))


def main():
    os.makedirs(OUT, exist_ok=True)
    inner = 110
    gem_x = inner // 2 - 3
    lines = [
        [(space(gem_x) + g('gem') + space(-(gem_x + 8)) + g('slot') + space(3), AQUA, 'ui'), ('Wheat Crops', WHITE, 'default')],
        [(space(24), WHITE, 'ui'), ('Crop', GRAY, 'default')],
        [(g('div.l') + g('space-1') + tiles('div.m', (inner - 27) // 2) + g('div.c') + g('space-1')
          + tiles('div.m', inner - 27 - (inner - 27) // 2) + g('div.r'), WHITE, 'ui')],
        [(g('icon.sprout') + space(2), WHITE, 'ui'), ('Growth ', GRAY, 'default'),
         (''.join(g('pip.full') for _ in range(5)), YELLOW, 'ui'), (''.join(g('pip.empty') for _ in range(3)), WHITE, 'ui'),
         (' 57%', YELLOW, 'default')],
        [(g('icon.heart') + space(2), WHITE, 'ui'), (''.join(g('pip.full') for _ in range(6)), RED, 'ui'),
         (''.join(g('pip.empty') for _ in range(2)), WHITE, 'ui'), (' 7/10', WHITE, 'default')],
    ]
    card(lines, inner, 'tooltip.png', AQUA)

    # menu frame
    rows = 6
    fr = Image.new('RGBA', (176 + 40, 114 + 18 * rows + 40), (40, 40, 40, 255))
    slot_img = Image.new('RGBA', (18, 18), (139, 139, 139, 255))
    for i in range(18):
        slot_img.putpixel((i, 0), (55, 55, 55, 255)); slot_img.putpixel((0, i), (55, 55, 55, 255))
        slot_img.putpixel((i, 17), (255, 255, 255, 255)); slot_img.putpixel((17, i), (255, 255, 255, 255))
    for r in range(rows):
        for c in range(9):
            fr.alpha_composite(slot_img, (20 + 7 + c * 18, 20 + 17 + r * 18))
    for r in range(3):
        for c in range(9):
            fr.alpha_composite(slot_img, (20 + 7 + c * 18, 20 + 30 + 18 * rows + r * 18))
    for c in range(9):
        fr.alpha_composite(slot_img, (20 + 7 + c * 18, 20 + 88 + 18 * rows))
    for t in ('chests', 'collector', 'claims'):
        f2 = fr.copy()
        draw_line(f2, 20 + 8, 20 + 6, [(space(-8) + g(f'frame{rows}.{t}') + space(-(G[f"frame{rows}.{t}"][1] - 8)), WHITE, 'ui'),
                                        ('Libreria menu', WHITE, 'default')], shadow=False)
        draw_line(f2, 20 + 8, 20 + 114 + 18 * rows - 94, [('Inventory', (64, 64, 64), 'default')], shadow=False)
        f2.resize((f2.width * 3, f2.height * 3), Image.NEAREST).save(os.path.join(OUT, f'frame_{t}.png'))


main()
