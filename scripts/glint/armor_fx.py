#!/usr/bin/env python3
"""
Enchant effects on worn armour (proof of concept): an extra equipment layer per material, family and level whose
pixels carry a code in their alpha channel (248-254). The Libreria core shader (assets/minecraft/shaders/core/entity.fsh)
lights those pixels up and animates them: a flicker for flames, a pulse for runes, a flow for veins, a twinkle for
frost. No vanilla pixels are copied: the vanilla armour textures are only read as a mask (where the armour is).

Usage: python3 armor_fx.py <vanilla assets/minecraft dir> <output: glint/src/main/resources>
"""
import json
import math
import os
import random
import sys

from PIL import Image

VANILLA = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/ref/vanilla26/assets/minecraft'
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources')
NS = 'libreria'

# Alpha codes (see entity.fsh): what the shader does with the pixel
STEADY, PULSE, FLICKER, FLOW, TWINKLE = 254, 253, 252, 251, 250

MATERIALS = ['leather', 'chainmail', 'copper', 'iron', 'gold', 'diamond', 'netherite', 'turtle_scute']
TIERS = [1, 2, 3]

# The vertical strips of the 64x32 armour texture (the four sides of a box, side by side), y going down.
# name: (x0, x1, y0, y1)
STRIPS = {'head': (0, 32, 8, 16), 'body': (16, 40, 20, 32), 'arm': (40, 56, 20, 32), 'leg': (0, 16, 20, 32)}
# Front faces, for runes: (x0, x1, y0, y1)
FRONTS = {'head': (8, 16, 8, 16), 'body': (20, 28, 20, 32), 'leg': (4, 8, 20, 32)}


def rng(*seed):
    return random.Random('|'.join(map(str, seed)))


def mask_of(path):
    im = Image.open(path).convert('RGBA')
    a = im.split()[3]
    return [[a.getpixel((x, y)) > 0 for x in range(64)] for y in range(32)]


def noise(x, seed):
    r = rng(seed, x // 2)
    r2 = rng(seed, x // 2 + 1)
    t = (x % 2) / 2
    return r.random() * (1 - t) + r2.random() * t


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(t, stops):
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        (p0, c0), (p1, c1) = stops[i], stops[i + 1]
        if t <= p1:
            return mix(c0, c1, (t - p0) / max(1e-6, p1 - p0))
    return stops[-1][1]


def columns(m):
    """Per strip and column: the opaque run (top, bottom) of the armour there, or nothing."""
    out = []
    for name, (x0, x1, y0, y1) in STRIPS.items():
        for x in range(x0, x1):
            ys = [y for y in range(y0, y1) if m[y][x]]
            if ys:
                out.append((name, x, min(ys), max(ys)))
    return out


def flames(m, tier, stops, code, seed, height=(1.6, 2.4, 3.4)):
    img = {}
    h = height[tier - 1]
    for name, x, top, bottom in columns(m):
        n = noise(x, seed + name)
        # tongues, not a wall: some columns only glow at the rim
        tongue = h * (0.25 + 1.1 * n * n)
        for k in range(int(round(tongue))):
            y = bottom - k
            if y < top or not m[y][x]:
                break
            img[(x, y)] = ramp(k / max(1, tongue - 1), stops) + (code,)
    # embers: a few lone pixels above the flames
    r = rng(seed, 'embers', tier)
    for name, x, top, bottom in columns(m):
        if r.random() < 0.10 * tier:
            y = bottom - int(h) - 1 - r.randrange(3)
            if top <= y and m[y][x] and (x, y) not in img:
                img[(x, y)] = stops[-1][1] + (TWINKLE,)
    return img


def frost(m, tier, seed):
    img = {}
    r = rng(seed, tier)
    ice, deep = (205, 240, 255), (130, 200, 255)
    for name, x, top, bottom in columns(m):
        # rime along the bottom rim, thicker at higher levels
        for k in range(tier):
            y = bottom - k
            if y >= top and m[y][x] and r.random() < 0.85 - 0.2 * k:
                img[(x, y)] = (ice if k == 0 else deep) + (STEADY if k == 0 else PULSE,)
    # crystals: short diagonal lines that twinkle
    for _ in range(10 * tier):
        name = r.choice(list(STRIPS))
        x0, x1, y0, y1 = STRIPS[name]
        x, y = r.randrange(x0, x1), r.randrange(y0, y1)
        d = r.choice([-1, 1])
        for i in range(r.randrange(2, 4)):
            xx, yy = x + i * d, y - i
            if x0 <= xx < x1 and y0 <= yy < y1 and m[yy][xx]:
                img[(xx, yy)] = (ice if i == 0 else deep) + (TWINKLE,)
    return img


RUNES = [
    ['.#.', '###', '.#.'], ['#.#', '.#.', '#.#'], ['##.', '#.#', '.##'], ['###', '#..', '###'], ['.#.', '#.#', '.#.'],
    ['#..', '###', '..#'], ['.##', '.#.', '##.']]


def runes(m, tier, color, seed):
    img = {}
    r = rng(seed, tier)
    for name, (x0, x1, y0, y1) in FRONTS.items():
        spots = []
        for _ in range(40):
            x, y = r.randrange(x0, x1 - 2), r.randrange(y0, y1 - 2)
            if all(m[y + j][x + i] for i in range(3) for j in range(3)) and all(abs(x - a) > 3 or abs(y - b) > 3 for a, b in spots):
                spots.append((x, y))
            if len(spots) >= tier:
                break
        for (x, y) in spots:
            g = r.choice(RUNES)
            for j in range(3):
                for i in range(3):
                    if g[j][i] == '#':
                        img[(x + i, y + j)] = color + (PULSE,)
    # a thin line round the rim, at the highest level
    if tier >= 3:
        for name, x, top, bottom in columns(m):
            if m[bottom][x] and (x, bottom) not in img:
                img[(x, bottom)] = mix(color, (255, 255, 255), 0.2) + (FLOW,)
    return img


def veins(m, tier, dark, bright, seed):
    img = {}
    r = rng(seed, tier)
    for _ in range(4 * tier):
        name = r.choice(list(STRIPS))
        x0, x1, y0, y1 = STRIPS[name]
        x, y = r.randrange(x0, x1), r.randrange(y0, y1)
        for i in range(r.randrange(4, 9)):
            if x0 <= x < x1 and y0 <= y < y1 and m[y][x]:
                img[(x, y)] = (bright if i % 3 == 0 else dark) + (FLOW,)
            x += r.choice([-1, 0, 1])
            y += r.choice([-1, 0, 1])
    return img


def thorns(m, tier, seed):
    img = {}
    r = rng(seed, tier)
    vine, leaf = (58, 42, 30), (70, 92, 40)
    for name, x, top, bottom in columns(m):
        # the vine winds along the lower rim (plain pixels: lit like the armour)
        y = bottom - 1 - (1 if math.sin(x * 0.9 + len(name)) > 0.3 else 0)
        if top <= y and m[y][x]:
            img[(x, y)] = (leaf if r.random() < 0.25 else vine) + (255,)
            # thorns stick out of it, their tips glow
            if r.random() < 0.16 + 0.1 * tier and y - 1 >= top and m[y - 1][x]:
                img[(x, y - 1)] = (225, 40, 50) + (PULSE,)
    return img


def bubbles(m, tier, seed):
    img = {}
    r = rng(seed, tier)
    for name, x, top, bottom in columns(m):
        # a wave along the rim that flows, and bubbles above it
        y = bottom - (1 if math.sin(x * 0.8) > 0 else 0)
        if m[y][x]:
            img[(x, y)] = (70, 150, 255) + (FLOW,)
        if tier >= 2 and r.random() < 0.1 * tier:
            yy = y - 2 - r.randrange(4)
            if yy >= top and m[yy][x]:
                img[(x, yy)] = (170, 225, 255) + (TWINKLE,)
    return img


def soot(m, tier, seed):
    """Blast Protection: soot smudged along the rims, thin cracks glowing like embers in it, more per level."""
    img = {}
    r = rng(seed, tier)
    dark, ember, hot = (54, 50, 52), (214, 100, 36), (255, 170, 70)
    for name, x, top, bottom in columns(m):
        for k in range(1 + (tier >= 2)):
            y = bottom - k
            if y >= top and m[y][x] and r.random() < 0.7 - 0.25 * k:
                img[(x, y)] = dark + (255,)
    for _ in range(3 * tier):
        name = r.choice(list(STRIPS))
        x0, x1, y0, y1 = STRIPS[name]
        x, y = r.randrange(x0, x1), r.randrange(y0, y1)
        for i in range(r.randrange(2, 5)):
            if x0 <= x < x1 and y0 <= y < y1 and m[y][x]:
                img[(x, y)] = (hot if i == 0 else ember) + (FLICKER,)
            x += r.choice([-1, 0, 1])
            y -= 1
    return img


def gusts(m, tier, seed):
    """Projectile Protection: streaks of wind running across the armour (a glint flowing along them)."""
    img = {}
    r = rng(seed, tier)
    pale, soft = (226, 248, 240), (170, 224, 206)
    for _ in range(3 + 3 * tier):
        name = r.choice(list(STRIPS))
        x0, x1, y0, y1 = STRIPS[name]
        x, y = r.randrange(x0, x1), r.randrange(y0, y1)
        for i in range(r.randrange(3, 6)):
            xx, yy = x + i, y + (i // 2) * r.choice([0, 1])
            if x0 <= xx < x1 and y0 <= yy < y1 and m[yy][xx]:
                img[(xx, yy)] = (pale if i == 0 else soft) + (FLOW,)
    return img


def feathers(m, tier, seed):
    """Feather Falling: small soft feathers, glowing gently."""
    img = {}
    r = rng(seed, tier)
    white, grey = (244, 246, 252), (196, 204, 222)
    for _ in range(2 + 2 * tier):
        name = r.choice(list(STRIPS))
        x0, x1, y0, y1 = STRIPS[name]
        x, y = r.randrange(x0 + 1, x1 - 1), r.randrange(y0, y1 - 2)
        for i, (dx, dy) in enumerate(((0, 0), (0, 1), (-1, 1), (1, 1), (0, 2))):
            xx, yy = x + dx, y + dy
            if x0 <= xx < x1 and y0 <= yy < y1 and m[yy][xx]:
                img[(xx, yy)] = (white if i < 2 else grey) + (PULSE,)
    return img


def renewal(m, tier, seed):
    """Mending: green-gold glints of experience scattered over it, a line of light round the rim."""
    img = {}
    r = rng(seed, tier)
    for name, x, top, bottom in columns(m):
        if r.random() < 0.06 + 0.04 * tier:
            y = r.randrange(top, bottom + 1)
            if m[y][x]:
                img[(x, y)] = ((220, 255, 120) if r.random() < 0.5 else (150, 230, 80)) + (TWINKLE,)
        if tier >= 2 and m[bottom][x] and (x, bottom) not in img:
            img[(x, bottom)] = (130, 210, 70) + (FLOW,)
    return img


def sheen(m, tier, seed):
    """Unbreaking: a cool steel sheen, a few bright lines a glint flows along (subtle: most armour has it)."""
    img = {}
    r = rng(seed, tier)
    bright, soft = (226, 236, 250), (176, 192, 214)
    for _ in range(2 + 2 * tier):
        name = r.choice(list(STRIPS))
        x0, x1, y0, y1 = STRIPS[name]
        x, y = r.randrange(x0, x1), r.randrange(y0, y1)
        for i in range(r.randrange(2, 4)):
            xx, yy = x + i, y - i
            if x0 <= xx < x1 and y0 <= yy < y1 and m[yy][xx]:
                img[(xx, yy)] = (bright if i == 0 else soft) + (FLOW,)
    return img


FAMILIES = {
    'fireguard': lambda m, t, s: flames(m, t, [(0, (210, 60, 15)), (0.5, (255, 140, 30)), (1, (255, 225, 110))], FLICKER, s),
    'soul': lambda m, t, s: flames(m, t, [(0, (20, 70, 200)), (0.5, (50, 170, 255)), (1, (190, 250, 255))], FLICKER, s),
    'frost': frost,
    'protection': lambda m, t, s: runes(m, t, (110, 225, 255), s),
    'sculk': lambda m, t, s: veins(m, t, (10, 90, 110), (60, 235, 235), s),
    'thorns': thorns,
    'water': bubbles,
    'blast': soot,
    'arrowguard': gusts,
    'feather': feathers,
    'mending': renewal,
    'curse': lambda m, t, s: veins(m, t + 1, (96, 40, 150), (206, 150, 255), s),
    'sturdy': sheen,
}


def main():
    written = 0
    for layer in ['humanoid', 'humanoid_leggings']:
        for mat in MATERIALS:
            src = os.path.join(VANILLA, 'textures/entity/equipment', layer, mat + '.png')
            if not os.path.exists(src):
                continue
            m = mask_of(src)
            for fam, fn in FAMILIES.items():
                for tier in TIERS:
                    pix = fn(m, tier, f'{fam}|{mat}|{layer}')
                    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
                    for (x, y), c in pix.items():
                        if 0 <= x < 64 and 0 <= y < 32:
                            img.putpixel((x, y), c)
                    out = os.path.join(OUT, 'assets', NS, 'textures/entity/equipment', layer, 'fx', f'{mat}_{fam}_{tier}.png')
                    os.makedirs(os.path.dirname(out), exist_ok=True)
                    img.save(out)
                    written += 1
    # equipment assets: the vanilla layers, then ours on top
    for mat in MATERIALS:
        base = json.load(open(os.path.join(VANILLA, 'equipment', mat + '.json')))
        for fam in FAMILIES:
            for tier in TIERS:
                layers = {}
                for kind in ['humanoid', 'humanoid_leggings']:
                    if kind in base['layers']:
                        layers[kind] = base['layers'][kind] + [{'texture': f'{NS}:fx/{mat}_{fam}_{tier}'}]
                asset = {'layers': layers}
                if 'trim_overrides' in base:
                    asset['trim_overrides'] = base['trim_overrides']
                out = os.path.join(OUT, 'assets', NS, 'equipment', f'fx_{mat}_{fam}_{tier}.json')
                os.makedirs(os.path.dirname(out), exist_ok=True)
                json.dump(asset, open(out, 'w'), indent=1)
    print('armour effects:', written, 'textures')


if __name__ == '__main__':
    main()
