#!/usr/bin/env python3
"""Advance widths of Minecraft's default font (vanilla 26.3), so the server can size tooltips to the pixel.
Only widths are stored (no glyph pixels). Usage: python3 widths.py <vanilla assets/minecraft> <out.json>"""
import io, json, sys, zipfile
from PIL import Image

VAN = sys.argv[1]
OUT = sys.argv[2]
EXTRA = "❤✔✘✿⌛⚡▮▯▣»⤒◆★☆✦⛏⚔⛨⏳⌚☀✎⚑♦•·–—…✚⬆⬇←→↑↓▲▼◀▶"

def bitmap(file, rows, height=None, ascent=7):
    im = Image.open(f"{VAN}/textures/font/{file}").convert('RGBA')
    cols = len(rows[0])
    cw, ch = im.width // cols, im.height // len(rows)
    h = height if height else 8
    scale = h / ch
    out = {}
    for r, line in enumerate(rows):
        for c, chr_ in enumerate(line):
            if chr_ in ('\u0000', ' '):
                continue
            w = 0
            for x in range(cw - 1, -1, -1):
                if any(im.getpixel((c * cw + x, r * ch + y))[3] != 0 for y in range(ch)):
                    w = x + 1
                    break
            out[chr_] = int(0.5 + w * scale) + 1
    return out

d = json.load(open(f"{VAN}/font/include/default.json"))
widths = {}
for p in d['providers']:
    f = p['file'].split(':')[1].replace('font/', '')
    got = bitmap(f, p['chars'], p.get('height'), p.get('ascent', 7))
    for k, v in got.items():
        widths.setdefault(k, v)  # first provider wins
widths[' '] = 4

# unifont: only a few symbols we use
need = {ord(c) for c in EXTRA if c not in widths}
with zipfile.ZipFile(f"{VAN}/font/unifont.zip") as z:
    name = [n for n in z.namelist() if n.endswith('.hex')][0]
    for line in io.TextIOWrapper(z.open(name), 'ascii'):
        cp_s, bits = line.strip().split(':')
        cp = int(cp_s, 16)
        if cp not in need:
            continue
        wpx = len(bits) * 4 // 16
        rows = [int(bits[i:i + wpx // 4], 16) for i in range(0, len(bits), wpx // 4)]
        left, right = wpx, -1
        for row in rows:
            for x in range(wpx):
                if row & (1 << (wpx - 1 - x)):
                    left = min(left, x); right = max(right, x)
        w = 0 if right < 0 else right - left + 1
        widths[chr(cp)] = (w + 1) // 2 + 1
json.dump({'default': 8, 'widths': ''.join(f"{k}{v:02d}" for k, v in sorted(widths.items()) if k not in '\n\r')}, open(OUT, 'w'))
print(len(widths), 'widths;', {c: widths.get(c) for c in 'aAiIl!.,:W@ ❤✔»·–'})
