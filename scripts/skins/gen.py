#!/usr/bin/env python3
"""Makes the mannequin skins: a few thousand people, each different (skin, face, hair, beard, clothes, trousers, boots,
and now and then glasses, freckles, a scar, a hood or a headband), drawn as Minecraft skins (64x64, classic or slim arms).

Usage: python3 gen.py <count> <out dir> [--sheet sheet.png N] [--seed S]
Writes <out dir>/0000.png ... and <out dir>/../../../../libreria/skins.txt ("<n> <wide|slim>" per line) next to the
textures' resources root when --list is given."""
import os, random, sys, colorsys
from PIL import Image

# ---------------------------------------------------------------- colours

def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def shade(c, f):
    """Lighter (f > 1) or darker (f < 1), keeping the hue."""
    h, l, s = colorsys.rgb_to_hls(*(x / 255 for x in c[:3]))
    l = max(0, min(1, l * f))
    return tuple(int(round(x * 255)) for x in colorsys.hls_to_rgb(h, l, s))

def jitter(c, rnd, amount=0.05):
    """Now and then a pixel a shade lighter or darker: cloth and hair with a little grain, and a small file."""
    r = rnd.random()
    if r < 0.18:
        return shade(c, 1 - amount)
    if r < 0.3:
        return shade(c, 1 + amount * 0.8)
    return c

SKIN = [hexrgb(h) for h in ('#F6D5C3', '#EFC6A8', '#E8B48F', '#D9A07A', '#C68A64', '#B47552', '#9C6244', '#845137', '#6C412B', '#56321F', '#F3CFA0',
                            '#E2B27C')]
HAIR = [hexrgb(h) for h in ('#1C1410', '#2E1F16', '#3F2A1C', '#5A3A24', '#6E4A2E', '#8A5A33', '#A0622D', '#B5743A', '#C98E4E', '#D9B46E',
                            '#E8CF8F', '#F0E2B6', '#8E8E8E', '#B9B9B9', '#E2E2E2', '#7A2E1E', '#A13A1C', '#C2552B')]
RARE_HAIR = [hexrgb(h) for h in ('#3B5BA8', '#7C3FA0', '#2F8F5B', '#C2457A', '#2E2E6E')]
EYES = [hexrgb(h) for h in ('#3B2A1E', '#5A3B22', '#2F5B8C', '#3E7BB6', '#4E7A3A', '#6C8A3E', '#5E6E7A', '#2A2A2A', '#7A5A2E')]
CLOTH = [hexrgb(h) for h in ('#7A2E2E', '#9C3B30', '#B5533A', '#C47A2C', '#C9A13A', '#8A8A3A', '#5E7A35', '#3F6B3A', '#2F5E52', '#2E5E7A',
                             '#2E4A7A', '#3A3A7A', '#5A3A7A', '#7A3A6A', '#6E4A32', '#8A6A4A', '#A8906E', '#C8B89A', '#E2DCCB', '#5E5E5E',
                             '#3A3A3A', '#24242A', '#4A5A6A', '#6A7A5A', '#8C5A5A')]
TROUSERS = [hexrgb(h) for h in ('#3A2A1E', '#4A3626', '#5E4632', '#2E2E2E', '#3A3A44', '#2E3A5A', '#3A4A5E', '#4A5A3A', '#6A5A44', '#8A7A5E',
                                '#5E5E5E', '#44342A')]
BOOTS = [hexrgb(h) for h in ('#2A1E16', '#3A2A1E', '#4E3826', '#222222', '#3A3A3A', '#5E4430', '#6E5236')]
METAL = hexrgb('#C9A13A')

# ---------------------------------------------------------------- the skin layout

class Skin:
    def __init__(self, slim):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.slim = slim

    def put(self, x, y, c, a=255):
        self.px[x, y] = (c[0], c[1], c[2], a)

    def get(self, x, y):
        return self.px[x, y]

    def fill(self, x0, y0, w, h, colour_at):
        for y in range(h):
            for x in range(w):
                c = colour_at(x, y)
                if c is not None:
                    self.put(x0 + x, y0 + y, c)

# A box's faces in the texture (u, v of the box; w, h, d): name -> (x, y, width, height)
def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}

def boxes(slim):
    a = 3 if slim else 4
    return {
        'head': faces(0, 0, 8, 8, 8), 'hat': faces(32, 0, 8, 8, 8),
        'body': faces(16, 16, 8, 12, 4), 'jacket': faces(16, 32, 8, 12, 4),
        'rarm': faces(40, 16, a, 12, 4), 'rsleeve': faces(40, 32, a, 12, 4),
        'larm': faces(32, 48, a, 12, 4), 'lsleeve': faces(48, 48, a, 12, 4),
        'rleg': faces(0, 16, 4, 12, 4), 'rpants': faces(0, 32, 4, 12, 4),
        'lleg': faces(16, 48, 4, 12, 4), 'lpants': faces(0, 48, 4, 12, 4),
    }

SIDE_SHADE = {'front': 1.0, 'left': 0.9, 'right': 0.9, 'back': 0.84, 'top': 1.06, 'bottom': 0.8}

# ---------------------------------------------------------------- one person

def person(rnd, slim):
    s = Skin(slim)
    B = boxes(slim)
    skin = rnd.choice(SKIN)
    hair = rnd.choice(RARE_HAIR) if rnd.random() < 0.04 else rnd.choice(HAIR)
    old = rnd.random() < 0.15
    if old and rnd.random() < 0.7:
        hair = rnd.choice([hexrgb('#9A9A9A'), hexrgb('#BDBDBD'), hexrgb('#DADADA'), hexrgb('#EDEDED')])
    eye = rnd.choice(EYES)
    shirt = rnd.choice(CLOTH)
    second = rnd.choice([c for c in CLOTH if c != shirt])
    trousers = rnd.choice(TROUSERS)
    boots = rnd.choice(BOOTS)
    noise = lambda c, a=0.04: jitter(c, rnd, a)

    def paint(box, colour, faces_=None, a=0.04):
        for name, (x, y, w, h) in B[box].items():
            if faces_ and name not in faces_:
                continue
            f = SIDE_SHADE[name]
            s.fill(x, y, w, h, lambda i, j: noise(shade(colour, f), a))

    # Skin everywhere first (head, arms, legs, body)
    for box in ('head', 'body', 'rarm', 'larm', 'rleg', 'lleg'):
        paint(box, skin, a=0.02)

    # ---------------- the face
    fx, fy = B['head']['front'][:2]
    eye_row = fy + 4
    wide_eyes = rnd.random() < 0.3
    for ex, white_first in ((fx + 1, True), (fx + 5, False)):
        if wide_eyes:
            s.put(ex, eye_row, eye if not white_first else (245, 245, 245))
            s.put(ex + 1, eye_row, (245, 245, 245) if not white_first else eye)
        else:
            s.put(ex + (1 if white_first else 0), eye_row, eye)
            s.put(ex + (0 if white_first else 1), eye_row, (240, 240, 240))
    brow = shade(hair, 0.8)
    if rnd.random() < 0.85:
        for bx in (fx + 1, fx + 2, fx + 5, fx + 6):
            s.put(bx, eye_row - 1, brow)
    if rnd.random() < 0.5:
        s.put(fx + 3, eye_row + 1, shade(skin, 0.93))
        s.put(fx + 4, eye_row + 1, shade(skin, 0.93))
    mix = lambda a, b, t: tuple(int(round(a[k] * (1 - t) + b[k] * t)) for k in range(3))
    mouth = mix(skin, hexrgb('#4A2E28'), rnd.uniform(0.3, 0.42))
    mw = rnd.choice([(3, 4), (2, 5), (3, 4), (3, 5)])
    for mx in range(mw[0], mw[1] + 1):
        s.put(fx + mx, eye_row + 2, mouth)
    if rnd.random() < 0.12:  # freckles
        for p in ((1, 5), (2, 5), (5, 5), (6, 5), (2, 6), (5, 6)):
            if rnd.random() < 0.7:
                s.put(fx + p[0], fy + p[1], shade(skin, 0.82))
    if rnd.random() < 0.08:  # blush
        s.put(fx + 1, eye_row + 1, shade(hexrgb('#D88A7A'), 1.0))
        s.put(fx + 6, eye_row + 1, shade(hexrgb('#D88A7A'), 1.0))
    if rnd.random() < 0.06:  # a scar
        sx = fx + rnd.choice([1, 5])
        for k in range(3):
            s.put(sx + (k % 2), fy + 3 + k, shade(skin, 0.7))

    # ---------------- hair (head and hat layer)
    style = rnd.choices(['short', 'long', 'bald', 'tied', 'curly', 'shaved', 'fringe', 'braids', 'spiky'],
                        [26, 20, 6, 10, 10, 5, 10, 7, 6])[0]
    if slim:
        style = rnd.choices(['short', 'long', 'tied', 'curly', 'fringe', 'braids', 'bald'], [12, 34, 18, 12, 10, 12, 2])[0]
    H = lambda: noise(hair, 0.07)
    hx, hy = B['head']['front'][:2]
    tx, ty, _, _ = B['head']['top']
    if style != 'bald':
        s.fill(tx, ty, 8, 8, lambda i, j: H() if style != 'shaved' or 2 <= i <= 5 else None)
        # front: top rows
        depth = {'short': 1, 'long': 1, 'tied': 1, 'curly': 2, 'shaved': 0, 'fringe': 2, 'braids': 1, 'spiky': 1}[style]
        for j in range(depth):
            for i in range(8):
                if style == 'fringe' and j == 1 and i in (0, 7):
                    continue
                s.put(hx + i, hy + j, H())
        if style == 'curly':
            for i in range(0, 8, 2):
                s.put(hx + i, hy + 2, H())
        # hair at the temples, framing the face (not on a shaved head)
        if style != 'shaved':
            for j in (1, 2) if style in ('short', 'spiky', 'tied', 'fringe') else (1, 2, 3):
                s.put(hx, hy + j, H())
                s.put(hx + 7, hy + j, H())
        if style == 'fringe':
            side = rnd.choice([(0, 1, 2, 3), (4, 5, 6, 7)])
            for i in side:
                s.put(hx + i, hy + 2, H())
        if style == 'spiky':
            for i in range(1, 7, 2):
                s.put(hx + i, hy + 1, H())
        # sides and back
        for side in ('right', 'left', 'back'):
            x0, y0, w, h = B['head'][side]
            down = {'short': 3, 'long': 8, 'tied': 4, 'curly': 5, 'shaved': 1, 'fringe': 3, 'braids': 8, 'spiky': 3}[style]
            if side == 'back':
                down = {'short': 5, 'long': 8, 'tied': 8, 'curly': 7, 'shaved': 2, 'fringe': 6, 'braids': 8, 'spiky': 5}[style]
            for j in range(min(h, down)):
                for i in range(w):
                    # leave the ear free on short hair
                    if side != 'back' and style in ('short', 'spiky', 'fringe') and j == 2 and i in (3, 4):
                        continue
                    s.put(x0 + i, y0 + j, H())
        if style in ('long', 'braids'):
            for j in range(2, 8):
                s.put(hx, hy + j, H())
                s.put(hx + 7, hy + j, H())
        if style == 'braids':
            x0, y0, w, h = B['head']['back']
            for j in range(8):
                for i in (1, 6):
                    s.put(x0 + i, y0 + j, shade(hair, 0.75))
        # volume on the hat layer
        hat = B['hat']
        if style in ('curly', 'long', 'spiky', 'tied', 'braids') or rnd.random() < 0.3:
            x0, y0, w, h = hat['top']
            s.fill(x0, y0, w, h, lambda i, j: H() if rnd.random() < (0.9 if style != 'spiky' else 0.6) else None)
            for side in ('right', 'left', 'back', 'front'):
                x0, y0, w, h = hat[side]
                rows = {'front': 1, 'right': 3, 'left': 3, 'back': 4}[side]
                if style in ('long', 'braids') and side != 'front':
                    rows = 8
                if style == 'tied' and side == 'back':
                    rows = 7
                for j in range(rows):
                    for i in range(w):
                        if side == 'front' and style not in ('curly', 'spiky'):
                            continue
                        if style == 'tied' and side == 'back' and j >= 4 and i not in (3, 4):
                            continue
                        if rnd.random() < 0.85:
                            s.put(x0 + i, y0 + j, H())
            if style == 'tied':
                x0, y0, w, h = hat['back']
                s.put(x0 + 3, y0 + 3, shade(rnd.choice(CLOTH), 1.0))
                s.put(x0 + 4, y0 + 3, shade(rnd.choice(CLOTH), 1.0))
    else:
        for side in ('right', 'left', 'back'):
            x0, y0, w, h = B['head'][side]
            for i in range(w):
                if rnd.random() < 0.8:
                    s.put(x0 + i, y0 + 3, H())

    # ---------------- beard (men, mostly)
    if not slim and rnd.random() < (0.45 if old else 0.3):
        kind = rnd.choice(['full', 'moustache', 'goatee', 'stubble', 'full'])
        beard = shade(hair, 0.95)
        if kind == 'full':
            for (i, j) in [(0, 5), (1, 5), (6, 5), (7, 5), (0, 6), (1, 6), (2, 6), (5, 6), (6, 6), (7, 6), (1, 7), (2, 7), (3, 7), (4, 7), (5, 7),
                           (6, 7), (0, 7), (7, 7), (2, 5), (5, 5)]:
                s.put(hx + i, hy + j, noise(beard, 0.06))
            for side in ('right', 'left'):
                x0, y0, w, h = B['head'][side]
                for j in (5, 6, 7):
                    for i in range(w):
                        if (side == 'right' and i >= 4) or (side == 'left' and i <= 3):
                            s.put(x0 + i, y0 + j, noise(beard, 0.06))
            x0, y0, w, h = B['head']['bottom']
            s.fill(x0, y0, w, h, lambda i, j: noise(beard, 0.06))
        elif kind == 'moustache':
            for i in (2, 3, 4, 5):
                s.put(hx + i, hy + 5, beard)
        elif kind == 'goatee':
            for (i, j) in [(3, 7), (4, 7), (2, 5), (3, 5), (4, 5), (5, 5)]:
                s.put(hx + i, hy + j, beard)
        else:
            for (i, j) in [(1, 6), (2, 7), (3, 7), (4, 7), (5, 7), (6, 6), (1, 7), (6, 7)]:
                if rnd.random() < 0.8:
                    s.put(hx + i, hy + j, shade(skin, 0.78))

    # ---------------- glasses, eyepatch, headwear
    extra = rnd.random()
    if extra < 0.06:
        frame = rnd.choice([hexrgb('#2A2A2A'), hexrgb('#8A6A3A'), hexrgb('#C0C0C0')])
        x0, y0, w, h = B['hat']['front']
        for i in (1, 2, 5, 6):
            s.put(x0 + i, y0 + 3, frame)
            s.put(x0 + i, y0 + 5, frame)
        for i in (0, 3, 4, 7):
            s.put(x0 + i, y0 + 4, frame)
    elif extra < 0.08:
        x0, y0, w, h = B['hat']['front']
        for i in range(8):
            s.put(x0 + i, y0 + 2 + (1 if i in (5, 6) else 0) if i < 7 else y0 + 2, hexrgb('#1E1E1E'))
        s.put(x0 + 5, y0 + 4, hexrgb('#1E1E1E'))
        s.put(x0 + 6, y0 + 4, hexrgb('#1E1E1E'))
    head_wear = rnd.random()
    if head_wear < 0.12:
        # a hood
        hood = rnd.choice([shirt, second, hexrgb('#4A3A2A'), hexrgb('#3A4A3A'), hexrgb('#5A2A2A')])
        for side, rows in (('top', 8), ('right', 8), ('left', 8), ('back', 8), ('front', 2)):
            x0, y0, w, h = B['hat'][side]
            for j in range(min(h, rows)):
                for i in range(w):
                    if side == 'front' and j == 1 and 1 <= i <= 6:
                        continue
                    s.put(x0 + i, y0 + j, noise(shade(hood, SIDE_SHADE[side]), 0.05))
    elif head_wear < 0.2:
        band = rnd.choice(CLOTH)
        for side in ('front', 'right', 'left', 'back'):
            x0, y0, w, h = B['hat'][side]
            for i in range(w):
                s.put(x0 + i, y0 + 1, noise(band))
    elif head_wear < 0.26:
        # a cap / hat brim
        cap = rnd.choice(CLOTH)
        x0, y0, w, h = B['hat']['top']
        s.fill(x0, y0, w, h, lambda i, j: noise(cap))
        for side in ('front', 'right', 'left', 'back'):
            x0, y0, w, h = B['hat'][side]
            for j in range(2):
                for i in range(w):
                    s.put(x0 + i, y0 + j, noise(shade(cap, 0.9 if j else 1.0)))

    # ---------------- clothes
    top_kind = rnd.choices(['tunic', 'vest', 'striped', 'apron', 'robe', 'jacket', 'plain'], [20, 15, 10, 8, 10, 15, 22])[0]
    collar = rnd.random() < 0.5
    belt = rnd.random() < 0.55 and top_kind != 'robe'
    long_sleeves = rnd.random() < 0.7
    for side, (x0, y0, w, h) in B['body'].items():
        f = SIDE_SHADE[side]
        def body_at(i, j, side=side, f=f, w=w):
            c = shirt
            if top_kind == 'striped' and j % 3 == 0:
                c = second
            if top_kind in ('vest', 'jacket') and side == 'front' and (i <= 1 or i >= 6):
                c = second
            if top_kind == 'jacket' and side in ('left', 'right', 'back'):
                c = second
            if top_kind == 'apron' and side == 'front' and 1 <= i <= 6 and j >= 3:
                c = hexrgb('#E2DCCB')
            if side == 'front' and collar and j == 0 and i in (3, 4):
                return noise(skin, 0.02)
            if side == 'front' and collar and j == 1 and i in (3, 4) and top_kind != 'striped':
                return noise(shade(c, 0.85))
            if belt and j in (8, 9) and side in ('front', 'back', 'left', 'right') and j == 8:
                return noise(hexrgb('#3A2618'), 0.03)
            if belt and side == 'front' and j == 8 and i in (3, 4):
                return METAL
            if side == 'front' and top_kind == 'tunic' and i in (3, 4) and j in (2, 4, 6):
                return shade(second, 1.0)
            return noise(shade(c, f))
        if side in ('top', 'bottom'):
            s.fill(x0, y0, w, h, lambda i, j, f=f: noise(shade(shirt, f)))
        else:
            s.fill(x0, y0, w, h, body_at)
    if belt:
        x0, y0, w, h = B['body']['front']
        s.put(x0 + 3, y0 + 8, METAL)
        s.put(x0 + 4, y0 + 8, METAL)
    # sleeves (arms) and hands
    sleeve = second if top_kind == 'jacket' else shirt
    for arm in ('rarm', 'larm'):
        for side, (x0, y0, w, h) in B[arm].items():
            f = SIDE_SHADE[side]
            if side == 'top':
                s.fill(x0, y0, w, h, lambda i, j, f=f: noise(shade(sleeve, f)))
                continue
            if side == 'bottom':
                continue
            rows = 9 if long_sleeves else 4
            s.fill(x0, y0, w, h, lambda i, j, f=f, rows=rows: noise(shade(sleeve, f)) if j < rows else
                   (noise(shade(sleeve, f * 0.85)) if j == rows and long_sleeves else None))
    # a robe goes down over the legs
    boot_rows = rnd.choice([3, 4, 5])
    for leg in ('rleg', 'lleg'):
        for side, (x0, y0, w, h) in B[leg].items():
            f = SIDE_SHADE[side]
            if side in ('top',):
                s.fill(x0, y0, w, h, lambda i, j, f=f: noise(shade(trousers, f)))
                continue
            if side == 'bottom':
                s.fill(x0, y0, w, h, lambda i, j: noise(shade(boots, 0.7)))
                continue
            def leg_at(i, j, f=f):
                if j >= 12 - boot_rows:
                    return noise(shade(boots, f))
                if top_kind == 'robe' and j < 8:
                    return noise(shade(shirt, f))
                return noise(shade(trousers, f))
            s.fill(x0, y0, w, h, leg_at)
    # a robe's skirt flares a little (the pants layer)
    if top_kind == 'robe':
        for leg in ('rpants', 'lpants'):
            for side, (x0, y0, w, h) in B[leg].items():
                if side in ('top', 'bottom'):
                    continue
                f = SIDE_SHADE[side]
                s.fill(x0, y0, w, h, lambda i, j, f=f: noise(shade(shirt, f * 0.95)) if j < 6 else None)
    # a jacket's open front on the jacket layer
    if top_kind == 'jacket' and rnd.random() < 0.6:
        for side, (x0, y0, w, h) in B['jacket'].items():
            if side in ('top', 'bottom'):
                continue
            f = SIDE_SHADE[side]
            s.fill(x0, y0, w, h, lambda i, j, f=f, side=side: noise(shade(second, f)) if (side != 'front' or i <= 1 or i >= 6) and j < 11 else None)
        for arm in ('rsleeve', 'lsleeve'):
            for side, (x0, y0, w, h) in B[arm].items():
                if side in ('top', 'bottom'):
                    continue
                f = SIDE_SHADE[side]
                s.fill(x0, y0, w, h, lambda i, j, f=f: noise(shade(second, f)) if j < (9 if long_sleeves else 4) else None)
    return s.img

def small(img):
    """The skin with at most 255 colours (median cut, no dithering) and one more for see-through: a small palette PNG."""
    alpha = img.getchannel('A')
    q = img.convert('RGB').quantize(colors=40, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    pal = q.getpalette()[:40 * 3]
    pal += [0, 0, 0] * (256 - len(pal) // 3)
    q.putpalette(pal)
    px, a = q.load(), alpha.load()
    for y in range(q.height):
        for x in range(q.width):
            if a[x, y] < 128:
                px[x, y] = 255
    return q

# ---------------------------------------------------------------- a sheet to look at (front views)

def front_view(img, slim, scale=6):
    B = boxes(slim)
    a = 3 if slim else 4
    W, H = 16, 32
    out = Image.new('RGBA', (W * scale, H * scale), (0, 0, 0, 0))
    def blit(face, dx, dy):
        x, y, w, h = face
        part = img.crop((x, y, x + w, y + h)).resize((w * scale, h * scale), Image.NEAREST)
        out.alpha_composite(part, (dx * scale, dy * scale))
    for base, over, dx, dy in (('head', 'hat', 4, 0), ('body', 'jacket', 4, 8), ('rarm', 'rsleeve', 4 - a, 8), ('larm', 'lsleeve', 12, 8),
                               ('rleg', 'rpants', 4, 20), ('lleg', 'lpants', 8, 20)):
        blit(B[base]['front'], dx, dy)
        blit(B[over]['front'], dx, dy)
    return out

def main():
    count = int(sys.argv[1])
    out = sys.argv[2]
    seed = 1
    sheet = None
    if '--seed' in sys.argv:
        seed = int(sys.argv[sys.argv.index('--seed') + 1])
    if '--sheet' in sys.argv:
        sheet = (sys.argv[sys.argv.index('--sheet') + 1], int(sys.argv[sys.argv.index('--sheet') + 2]))
    os.makedirs(out, exist_ok=True)
    listing = []
    views = []
    for n in range(count):
        rnd = random.Random(seed * 100003 + n)
        slim = rnd.random() < 0.42
        img = person(rnd, slim)
        # Few colours: a small file (a few thousand of them go into the resource pack)
        small(img).save(os.path.join(out, f'{n:04d}.png'), optimize=True, transparency=255)
        listing.append(f'{n:04d} {"slim" if slim else "wide"}')
        if sheet and n < sheet[1]:
            views.append(front_view(img, slim))
    if '--list' in sys.argv:
        with open(sys.argv[sys.argv.index('--list') + 1], 'w') as f:
            f.write('\n'.join(listing) + '\n')
    if sheet:
        cols = 12
        w, h = views[0].size
        rows = (len(views) + cols - 1) // cols
        page = Image.new('RGBA', (cols * (w + 12) + 12, rows * (h + 12) + 12), (58, 62, 72, 255))
        for i, v in enumerate(views):
            page.alpha_composite(v, (12 + (i % cols) * (w + 12), 12 + (i // cols) * (h + 12)))
        page.save(sheet[0])

if __name__ == '__main__':
    main()
