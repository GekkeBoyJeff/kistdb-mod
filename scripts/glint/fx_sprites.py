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


SPRITES = {
    'glow': glow, 'haze': haze, 'ember': ember, 'mote': mote, 'spark': spark, 'flake': flake, 'wisp': wisp, 'arc': arc, 'ring': ring,
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
    # a sheet to look at
    sheet = Image.new('RGBA', (len(SPRITES) * (N + 4), N + 4), (20, 20, 28, 255))
    for k, make in enumerate(SPRITES.values()):
        sheet.alpha_composite(make(), (k * (N + 4) + 2, 2))
    sheet.save('/tmp/fx_sheet.png')
    print(len(SPRITES), 'sprites')


if __name__ == '__main__':
    random.seed(1)
    main()
