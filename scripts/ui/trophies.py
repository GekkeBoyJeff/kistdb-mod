"""The trophies: one small gold emblem per record that's a feat (worn beside your title, shown in Records).

Drawn smooth at 8x, brought down to 16x16, then lit like polished gold: bright at the top, deep at the bottom, a dark
rim all round and a glint at the top left.

    python3 scripts/ui/trophies.py
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '../../core/src/main/resources/assets/libreria')
S = 8  # drawn at S x 16
N = 16 * S
KINDS = ['fall', 'flight', 'speed', 'distance', 'alive', 'session', 'hit', 'levels', 'depth']


def canvas():
    return Image.new('L', (N, N), 0)


def poly(d, pts):
    d.polygon([(x * S, y * S) for x, y in pts], fill=255)


def feather():
    im = canvas(); d = ImageDraw.Draw(im)
    # a broad vane along the diagonal, a quill at the bottom left, the shaft as a fine line through it
    pts, back = [], []
    for i in range(21):
        t = i / 20
        x, y = 4.0 + t * 10.0, 12.0 - t * 10.0
        w1 = 3.6 * math.sin(math.pi * min(1.0, 0.15 + t * 0.95)) ** 0.8
        w2 = 2.8 * math.sin(math.pi * min(1.0, 0.15 + t * 0.95)) ** 0.8
        pts.append((x - w1 * 0.7, y - w1 * 0.7))
        back.append((x + w2 * 0.7, y + w2 * 0.7))
    poly(d, pts + back[::-1])
    d.line([(1.2 * S, 14.8 * S), (5.0 * S, 11.0 * S)], fill=255, width=int(1.4 * S))
    # the shaft as a fine line through the vane
    d.line([(4.6 * S, 11.4 * S), (11.6 * S, 4.4 * S)], fill=0, width=int(0.7 * S))
    return im


def wings():
    im = canvas(); d = ImageDraw.Draw(im)
    for side in (-1, 1):
        c = 8.0
        f = lambda x: c + side * x
        pts = [(f(0.6), 7.6), (f(2.4), 4.2), (f(5.0), 2.2), (f(7.6), 1.2), (f(7.4), 3.6), (f(6.2), 4.4), (f(7.0), 5.6),
               (f(5.4), 6.4), (f(6.0), 7.8), (f(4.2), 8.4), (f(4.6), 10.0), (f(2.8), 10.2), (f(2.6), 12.0), (f(1.0), 11.2)]
        poly(d, pts)
    return im


def bolt():
    im = canvas(); d = ImageDraw.Draw(im)
    poly(d, [(10.4, 0.6), (3.0, 9.2), (7.2, 9.2), (5.0, 15.4), (13.2, 6.0), (8.8, 6.0), (12.0, 0.6)])
    return im


def compass():
    im = canvas(); d = ImageDraw.Draw(im)
    c = 8.0
    d.ellipse([((c - 6.3) * S, (c - 6.3) * S), ((c + 6.3) * S, (c + 6.3) * S)], fill=255)
    d.ellipse([((c - 4.9) * S, (c - 4.9) * S), ((c + 4.9) * S, (c + 4.9) * S)], fill=0)
    poly(d, [(c, 0.6), (c + 1.5, c - 1.5), (15.4, c), (c + 1.5, c + 1.5), (c, 15.4), (c - 1.5, c + 1.5), (0.6, c), (c - 1.5, c - 1.5)])
    return im


def heart():
    im = canvas(); d = ImageDraw.Draw(im)
    d.ellipse([(1.2 * S, 2.4 * S), (8.4 * S, 9.6 * S)], fill=255)
    d.ellipse([(7.6 * S, 2.4 * S), (14.8 * S, 9.6 * S)], fill=255)
    poly(d, [(1.6, 7.4), (14.4, 7.4), (8.0, 14.6)])
    return im


def hourglass():
    im = canvas(); d = ImageDraw.Draw(im)
    poly(d, [(3.0, 1.2), (13.0, 1.2), (13.0, 2.8), (9.0, 7.4), (9.0, 8.6), (13.0, 13.2), (13.0, 14.8), (3.0, 14.8), (3.0, 13.2),
             (7.0, 8.6), (7.0, 7.4), (3.0, 2.8)])
    return im


def sword():
    im = canvas(); d = ImageDraw.Draw(im)
    # blade along the diagonal, a cross guard and a pommel
    poly(d, [(14.8, 1.2), (14.6, 3.4), (6.4, 11.6), (4.4, 9.6), (12.6, 1.4)])
    poly(d, [(2.2, 9.4), (3.6, 8.0), (8.0, 12.4), (6.6, 13.8)])
    d.line([(2.0 * S, 14.0 * S), (4.6 * S, 11.4 * S)], fill=255, width=int(1.7 * S))
    d.ellipse([(0.6 * S, 13.0 * S), (2.8 * S, 15.2 * S)], fill=255)
    return im


def star():
    im = canvas(); d = ImageDraw.Draw(im)
    c, R, r = (8.0, 8.6), 7.4, 3.0
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rad = R if i % 2 == 0 else r
        pts.append((c[0] + math.cos(a) * rad, c[1] + math.sin(a) * rad))
    poly(d, pts)
    return im


def pick():
    im = canvas(); d = ImageDraw.Draw(im)
    # a pickaxe: a curved head and a handle
    d.arc([(1.6 * S, 1.6 * S), (14.4 * S, 12.0 * S)], 200, 340, fill=255, width=int(2.2 * S))
    d.line([(8.0 * S, 3.2 * S), (8.0 * S, 15.0 * S)], fill=255, width=int(1.8 * S))
    return im


SHAPES = {'fall': feather, 'flight': wings, 'speed': bolt, 'distance': compass, 'alive': heart, 'session': hourglass, 'hit': sword,
          'levels': star, 'depth': pick}

TOP, MID, LOW = (255, 244, 190), (255, 200, 76), (186, 116, 22)
RIM = (66, 40, 10)


def lit(mask16, glint=True):
    """Gold, lit from the top left, with a dark rim."""
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    m = mask16.load()
    inside = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and m[x, y] >= 128
    ys = [y for y in range(16) for x in range(16) if inside(x, y)]
    y0, y1 = (min(ys), max(ys)) if ys else (0, 15)
    for y in range(16):
        for x in range(16):
            if inside(x, y):
                t = (y - y0) / max(1, y1 - y0)
                # brighter towards the top left
                t = max(0.0, min(1.0, t * 0.85 + (x / 15) * 0.15))
                if t < 0.5:
                    k = t / 0.5
                    col = tuple(int(TOP[i] + (MID[i] - TOP[i]) * k) for i in range(3))
                else:
                    k = (t - 0.5) / 0.5
                    col = tuple(int(MID[i] + (LOW[i] - MID[i]) * k) for i in range(3))
                # an edge facing down/right is a little deeper
                if not inside(x + 1, y + 1):
                    col = tuple(int(c * 0.82) for c in col)
                out.putpixel((x, y), col + (255,))
            elif any(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), RIM + (255,))
    if glint:
        # the first two inside pixels from the top left get a white glint
        n = 0
        for s in range(32):
            for x in range(s + 1):
                y = s - x
                if inside(x, y) and n < 2:
                    out.putpixel((x, y), (255, 255, 240, 255))
                    n += 1
    return out


def make(kind):
    big = SHAPES[kind]()
    small = big.resize((16, 16), Image.LANCZOS)
    return lit(small)


def main():
    tex = os.path.join(ROOT, 'textures/item/trophy')
    os.makedirs(tex, exist_ok=True)
    for k in KINDS:
        make(k).save(os.path.join(tex, k + '.png'))
    # the font: one character per kind, a line high, sitting on the baseline like a letter
    font = {'providers': [{'type': 'bitmap', 'file': f'libreria:item/trophy/{k}.png', 'height': 9, 'ascent': 8,
                           'chars': [chr(0xE000 + i)]} for i, k in enumerate(KINDS)]}
    fd = os.path.join(ROOT, 'font')
    os.makedirs(fd, exist_ok=True)
    json.dump(font, open(os.path.join(fd, 'trophy.json'), 'w'), indent=1)
    # as items too (for the Records window)
    md, idf = os.path.join(ROOT, 'models/item/trophy'), os.path.join(ROOT, 'items/trophy')
    os.makedirs(md, exist_ok=True)
    os.makedirs(idf, exist_ok=True)
    for k in KINDS:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'libreria:item/trophy/{k}'}},
                  open(os.path.join(md, k + '.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': f'libreria:item/trophy/{k}'}}, open(os.path.join(idf, k + '.json'), 'w'), indent=1)
    sheet = Image.new('RGBA', (len(KINDS) * 20, 20), (35, 28, 22, 255))
    for i, k in enumerate(KINDS):
        sheet.alpha_composite(make(k), (i * 20 + 2, 2))
    sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST).save('/tmp/trophies.png')
    print(len(KINDS), 'trophies')


if __name__ == '__main__':
    main()
