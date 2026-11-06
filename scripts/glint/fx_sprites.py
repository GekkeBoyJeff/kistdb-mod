"""Sprites for Libreria's own particles: soft, glowing pictures that the server shows with item displays (the client
moves, turns and shrinks them by itself). White, so the server can colour each one (the item's custom_model_data
colour), on a flat card model that's lit by itself.

    python3 scripts/glint/fx_sprites.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources/assets/libreria')
N = 32  # texture size


def blank():
    return Image.new('RGBA', (N, N), (0, 0, 0, 0))


def put(img, x, y, a):
    """Adds alpha a (0..1) as white at a pixel (keeps the brightest). Alpha 249..254 is left out: the core shader
    reads those as codes (glowing, pulsing armour)."""
    if 0 <= x < N and 0 <= y < N:
        r, g, b, old = img.getpixel((x, y))
        v = max(old, int(round(min(1.0, a) * 255)))
        if 248 < v < 255:
            v = 255 if v >= 252 else 248
        img.putpixel((x, y), (255, 255, 255, v))


def soft_disc(img, cx, cy, radius, power=2.2, peak=1.0):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / radius
            if d < 1:
                put(img, x, y, peak * (1 - d) ** power)


def glow():
    img = blank()
    soft_disc(img, N / 2, N / 2, N / 2, power=2.0, peak=0.85)
    return img


def haze():
    img = blank()
    soft_disc(img, N / 2, N / 2, N / 2, power=1.6, peak=0.32)
    return img


def ember():
    img = blank()
    soft_disc(img, N / 2, N / 2, N / 2 - 2, power=2.6, peak=0.7)
    soft_disc(img, N / 2, N / 2, 4.5, power=0.6, peak=1.0)
    return img


def mote():
    img = blank()
    soft_disc(img, N / 2, N / 2, 6, power=1.2, peak=1.0)
    return img


def spark():
    """A four-pointed star: two thin rays and a bright middle."""
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            dx, dy = abs(x + 0.5 - c), abs(y + 0.5 - c)
            ray = max(0, 1 - dx / 1.6) * max(0, 1 - dy / (N / 2)) + max(0, 1 - dy / 1.6) * max(0, 1 - dx / (N / 2))
            core = max(0, 1 - math.hypot(dx, dy) / 5) ** 1.2
            put(img, x, y, min(1, ray ** 1.3 + core))
    return img


def flake():
    """A snowflake: six arms with little branches, a soft rim."""
    img = blank()
    c = N / 2
    for arm in range(6):
        a = arm * math.pi / 3
        for t in range(0, 140):
            r = t / 10.0
            if r > 13:
                break
            x, y = c + math.cos(a) * r, c + math.sin(a) * r
            put(img, int(x), int(y), 1.0)
            if r in (5.0, 9.0):
                for s in (-1, 1):
                    b = a + s * math.pi / 4
                    for u in range(1, 5):
                        put(img, int(x + math.cos(b) * u), int(y + math.sin(b) * u), 0.95)
    # soft rim
    halo = blank()
    soft_disc(halo, c, c, 15, power=2.5, peak=0.35)
    return Image.alpha_composite(halo, img)


def wisp():
    """A soul wisp: a round head with a tail that thins out downwards."""
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            dx = x + 0.5 - c
            dy = y + 0.5 - (c - 4)
            head = max(0, 1 - math.hypot(dx, dy) / 6.5) ** 1.1
            tail = 0
            if dy > 0:
                width = max(0.5, 5.5 - dy * 0.33)
                tail = max(0, 1 - abs(dx + math.sin(dy * 0.35) * 1.5) / width) * max(0, 1 - dy / 18) * 0.85
            put(img, x, y, max(head, tail))
    halo = blank()
    soft_disc(halo, c, c - 3, 13, power=2.4, peak=0.3)
    return Image.alpha_composite(halo, img)


RUNES = {
    'a': [((8, 4), (8, 28)), ((8, 10), (22, 4)), ((8, 18), (22, 12))],
    'b': [((16, 4), (16, 28)), ((8, 10), (24, 22)), ((24, 10), (8, 22))],
    'c': [((8, 4), (8, 28)), ((24, 4), (24, 28)), ((8, 16), (24, 8)), ((8, 16), (24, 24))],
    'd': [((16, 4), (8, 16)), ((8, 16), (16, 28)), ((16, 28), (24, 16)), ((24, 16), (16, 4))],
}


def line(img, p, q, a=1.0, w=1.2):
    (x0, y0), (x1, y1) = p, q
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5, y + 0.5
            vx, vy = x1 - x0, y1 - y0
            t = max(0, min(1, ((px - x0) * vx + (py - y0) * vy) / (vx * vx + vy * vy)))
            d = math.hypot(px - (x0 + t * vx), py - (y0 + t * vy))
            if d < w + 1:
                put(img, x, y, a * max(0, min(1, w + 0.5 - d)))


def rune(key):
    img = blank()
    halo = blank()
    for p, q in RUNES[key]:
        line(halo, p, q, 0.35, 3.2)
        line(img, p, q, 1.0, 1.1)
    return Image.alpha_composite(halo, img)


def arc():
    """A slash: a crescent, bright at its edge, fading towards both ends."""
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            # the top half of a ring, thickest in the middle
            if dy > 2:
                continue
            along = 1 - abs(ang + math.pi / 2) / (math.pi / 2)
            if along <= 0:
                continue
            width = 1.0 + 3.5 * along
            d = abs(r - 12)
            put(img, x, y, max(0, 1 - d / width) ** 1.4 * along ** 0.6)
    return img


def ring():
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            d = abs(math.hypot(x + 0.5 - c, y + 0.5 - c) - 13)
            put(img, x, y, max(0, 1 - d / 2.5) ** 1.5)
    return img


def twinkle():
    """A sharp sparkle: long thin rays, a short diagonal pair, a hot middle and a soft glow."""
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            ax, ay = abs(dx), abs(dy)
            long_ = max(0, 1 - ax / 1.1) * max(0, 1 - ay / 15) ** 1.6 + max(0, 1 - ay / 1.1) * max(0, 1 - ax / 15) ** 1.6
            u, v = abs(dx + dy) / 1.414, abs(dx - dy) / 1.414
            diag = (max(0, 1 - u / 0.9) * max(0, 1 - v / 6) + max(0, 1 - v / 0.9) * max(0, 1 - u / 6)) * 0.7
            core = max(0, 1 - math.hypot(dx, dy) / 3.2)
            glow_ = max(0, 1 - math.hypot(dx, dy) / 10) ** 2 * 0.45
            put(img, x, y, min(1, long_ + diag + core + glow_))
    return img


def coin():
    """A gold coin, seen a little from the side, with a bright edge."""
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            dx, dy = (x + 0.5 - c) / 9.0, (y + 0.5 - c) / 12.0
            d = math.hypot(dx, dy)
            if d < 1:
                a = 1.0 if d > 0.78 or d < 0.4 else 0.82
                put(img, x, y, a)
    return img


def bubble():
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            rim = max(0, 1 - abs(d - 11) / 1.8) ** 1.3
            inside = 0.12 if d < 11 else 0
            hl = max(0, 1 - math.hypot(x + 0.5 - (c - 5), y + 0.5 - (c - 5)) / 3.2)
            put(img, x, y, max(rim, inside, hl))
    return img


def feather():
    """A soft feather: a thin shaft and a vane that thins out to the tip."""
    img = blank()
    for t in range(0, 260):
        k = t / 260.0
        x = 7 + k * 18
        y = 25 - k * 18 + math.sin(k * 3.0) * 1.5
        put(img, int(x), int(y), 1.0)
        w = 5.5 * math.sin(math.pi * min(1, k * 1.15)) if k > 0.12 else 0
        for s in (-1, 1):
            for u in range(1, int(w) + 1):
                px, py = x + s * u * 0.7, y + s * u * 0.7
                put(img, int(px), int(py), 0.75 * (1 - u / (w + 1)) + 0.15)
    return img


def thorn():
    """A thorn: wide at the base, bending to a sharp tip, with a little glow."""
    img = blank()
    bx, by, tx, ty = 10.0, 28.0, 24.0, 4.0
    L = math.hypot(tx - bx, ty - by)
    ux, uy = (tx - bx) / L, (ty - by) / L
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - bx, y + 0.5 - by
            t = (px * ux + py * uy) / L
            if not 0 <= t <= 1:
                continue
            bend = math.sin(t * math.pi) * 3.0
            perp = px * -uy + py * ux - bend
            w = 5.0 * (1 - t) ** 1.2 + 0.35
            put(img, x, y, max(0, 1 - abs(perp) / w) ** 0.7)
    halo = blank()
    soft_disc(halo, 15, 17, 14, power=2.4, peak=0.25)
    return Image.alpha_composite(halo, img)


def hexshield():
    """A hexagon outline, like a shield flaring up where you're hit."""
    img = blank()
    c = N / 2
    pts = [(c + 13 * math.cos(math.pi / 3 * i + math.pi / 6), c + 13 * math.sin(math.pi / 3 * i + math.pi / 6)) for i in range(6)]
    for i in range(6):
        line(img, pts[i], pts[(i + 1) % 6], 1.0, 1.0)
    halo = blank()
    for i in range(6):
        line(halo, pts[i], pts[(i + 1) % 6], 0.3, 3.0)
    fill = blank()
    soft_disc(fill, c, c, 13, power=0.8, peak=0.18)
    return Image.alpha_composite(Image.alpha_composite(fill, halo), img)


def flame_frames():
    """A flame tongue, 8 frames (a strip; the .mcmeta makes it flicker)."""
    frames = []
    rnd = random.Random(7)
    seeds = [rnd.random() * 10 for _ in range(4)]
    for f in range(8):
        img = blank()
        ph = f / 8 * math.pi * 2
        for y in range(N):
            for x in range(N):
                h = (N - 1 - y) / (N - 1)  # 0 bottom .. 1 top
                sway = math.sin(h * 4 + ph + seeds[0]) * 2.2 * h + math.sin(h * 9 + ph * 2 + seeds[1]) * 0.9 * h
                cx = N / 2 + sway
                width = 9.5 * (1 - h) ** 0.75 * (0.9 + 0.1 * math.sin(ph + h * 6)) + 0.3
                d = abs(x + 0.5 - cx) / width
                if d < 1 and h < 0.95:
                    a = (1 - d) ** 0.9 * (1 - h) ** 0.35
                    put(img, x, y, a)
        frames.append(img)
    return frames


def circle():
    """A rune circle for the ground: two rings, runes between them, ticks on the outside. 64 px."""
    M = 64
    img = Image.new('RGBA', (M, M), (0, 0, 0, 0))
    c = M / 2

    def p(x, y, a):
        if 0 <= x < M and 0 <= y < M:
            o = img.getpixel((x, y))[3]
            v = max(o, int(round(min(1, a) * 255)))
            if 248 < v < 255:
                v = 255 if v >= 252 else 248
            img.putpixel((x, y), (255, 255, 255, v))

    for y in range(M):
        for x in range(M):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            a = max(0, 1 - abs(d - 29) / 1.1) + max(0, 1 - abs(d - 22) / 0.9) * 0.9
            a += max(0, 1 - abs(d - 25.5) / 6) ** 2 * 0.18
            ang = math.atan2(y + 0.5 - c, x + 0.5 - c)
            if 29.5 < d < 31.5 and abs(math.sin(ang * 12)) > 0.93:
                a = max(a, 0.8)
            p(x, y, a)
    # little runes between the rings
    for k in range(8):
        a0 = k * math.pi / 4
        rx, ry = c + math.cos(a0) * 25.5, c + math.sin(a0) * 25.5
        for (x0, y0), (x1, y1) in RUNES['abcd'[k % 4]]:
            sx0, sy0 = rx + (x0 - 16) * 0.2, ry + (y0 - 16) * 0.2
            sx1, sy1 = rx + (x1 - 16) * 0.2, ry + (y1 - 16) * 0.2
            for t in range(12):
                u = t / 11
                p(int(sx0 + (sx1 - sx0) * u), int(sy0 + (sy1 - sy0) * u), 1.0)
    return img


def thinarc():
    """A thin crescent: a fine bright edge that fades towards both ends (a blade's trail)."""
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            if dy > 3:
                continue
            ang = math.atan2(dy, dx)
            along = 1 - abs(ang + math.pi / 2) / (math.pi / 1.7)
            if along <= 0:
                continue
            d = abs(math.hypot(dx, dy) - 13)
            put(img, x, y, max(0, 1 - d / (0.8 + 1.4 * along)) ** 1.6 * along ** 0.8)
    return img


def streak():
    """A speed line: thin, bright in the middle, soft at both ends."""
    img = blank()
    c = N / 2
    for y in range(N):
        for x in range(N):
            dx, dy = abs(x + 0.5 - c), abs(y + 0.5 - c)
            put(img, x, y, max(0, 1 - dy / 1.3) * max(0, 1 - dx / 15.5) ** 1.2)
    return img


def chain():
    """Two chain links, slanted."""
    img = blank()
    for cx, cy in ((12, 12), (20, 20)):
        for y in range(N):
            for x in range(N):
                u, v = x + 0.5 - cx, y + 0.5 - cy
                a, b = (u + v) / 1.414, (u - v) / 1.414
                d = math.hypot(a / 7.0, b / 4.2)
                put(img, x, y, max(0, 1 - abs(d - 1) / 0.22) ** 1.2)
    halo = blank()
    soft_disc(halo, 16, 16, 15, power=2.4, peak=0.22)
    return Image.alpha_composite(halo, img)


def smoke():
    """A soft, lumpy puff of smoke."""
    img = blank()
    rnd = random.Random(3)
    blobs = [(16 + rnd.uniform(-5, 5), 16 + rnd.uniform(-5, 5), rnd.uniform(6, 10)) for _ in range(5)]
    for cx, cy, r in blobs:
        soft_disc(img, cx, cy, r, power=1.6, peak=0.38)
    return img


SPRITES = {
    'glow': glow, 'haze': haze, 'ember': ember, 'mote': mote, 'spark': spark, 'flake': flake, 'wisp': wisp, 'arc': arc, 'ring': ring,
    'thinarc': thinarc, 'streak': streak, 'chain': chain, 'smoke': smoke,
    'twinkle': twinkle, 'coin': coin, 'bubble': bubble, 'feather': feather, 'thorn': thorn, 'hex': hexshield,
    'rune_a': lambda: rune('a'), 'rune_b': lambda: rune('b'), 'rune_c': lambda: rune('c'), 'rune_d': lambda: rune('d'),
}


def card_model(name):
    face = {'uv': [0, 0, 16, 16], 'texture': '#s'}
    return {
        'textures': {'s': f'libreria:item/fx/p/{name}', 'particle': f'libreria:item/fx/p/{name}'},
        'elements': [{'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False, 'light_emission': 15,
                      'faces': {'north': dict(face, tintindex=0), 'south': dict(face, tintindex=0)}}],
        'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}},
    }


def main():
    tex = os.path.join(ROOT, 'textures/item/fx/p')
    mod = os.path.join(ROOT, 'models/item/fx/p')
    itm = os.path.join(ROOT, 'items/fx/p')
    for d in (tex, mod, itm):
        os.makedirs(d, exist_ok=True)
    for name, make in SPRITES.items():
        img = make()
        px = img.load()
        for y in range(N):
            for x in range(N):
                r, g, b, a = px[x, y]
                if 248 < a < 255:
                    px[x, y] = (r, g, b, 255 if a >= 252 else 248)
        img.save(os.path.join(tex, name + '.png'))
        json.dump(card_model(name), open(os.path.join(mod, name + '.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': f'libreria:item/fx/p/{name}',
                             'tints': [{'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xFFFFFF}]}},
                  open(os.path.join(itm, name + '.json'), 'w'), indent=1)
    # a shaft of light: soft at the sides, fading out at the top and the bottom (16 x 64)
    beam = Image.new('RGBA', (16, 64), (0, 0, 0, 0))
    for y in range(64):
        for x in range(16):
            side = max(0.0, 1 - abs(x + 0.5 - 8) / 8) ** 1.8
            ends = min(1.0, y / 10, (63 - y) / 22)
            a = int(round(min(1.0, side * max(0.0, ends)) * 230))
            if 248 < a < 255:
                a = 248
            beam.putpixel((x, y), (255, 255, 255, a))
    beam.save(os.path.join(tex, 'beam.png'))
    json.dump({'textures': {'s': 'libreria:item/fx/p/beam', 'particle': 'libreria:item/fx/p/beam'},
               'elements': [{'from': [4, 0, 8], 'to': [12, 16, 8], 'shade': False, 'light_emission': 15,
                             'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#s', 'tintindex': 0},
                                       'south': {'uv': [0, 0, 16, 16], 'texture': '#s', 'tintindex': 0}}}],
               'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}},
              open(os.path.join(mod, 'beam.json'), 'w'), indent=1)
    json.dump({'model': {'type': 'minecraft:model', 'model': 'libreria:item/fx/p/beam',
                         'tints': [{'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xFFFFFF}]}},
              open(os.path.join(itm, 'beam.json'), 'w'), indent=1)
    # the flame (animated) and the ground circle (bigger)
    fr = flame_frames()
    strip = Image.new('RGBA', (N, N * len(fr)), (0, 0, 0, 0))
    for k, f in enumerate(fr):
        strip.paste(f, (0, k * N))
    strip.save(os.path.join(tex, 'flame.png'))
    json.dump({'animation': {'frametime': 2, 'interpolate': True}}, open(os.path.join(tex, 'flame.png.mcmeta'), 'w'))
    circle().save(os.path.join(tex, 'circle.png'))
    for name in ('flame', 'circle'):
        json.dump(card_model(name), open(os.path.join(mod, name + '.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': f'libreria:item/fx/p/{name}',
                             'tints': [{'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xFFFFFF}]}},
                  open(os.path.join(itm, name + '.json'), 'w'), indent=1)
    # a sheet to look at
    sheet = Image.new('RGBA', (len(SPRITES) * (N + 4), N + 4), (20, 20, 28, 255))
    for k, make in enumerate(SPRITES.values()):
        sheet.alpha_composite(make(), (k * (N + 4) + 2, 2))
    sheet.save('/tmp/fx_sheet.png')
    print(len(SPRITES), 'sprites')


if __name__ == '__main__':
    random.seed(1)
    main()
