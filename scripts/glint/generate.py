#!/usr/bin/env python3
"""
Generates the assets of the Libreria Glint module (animated enchantment effects on tools, weapons and armour, and a
custom "skill book" texture per enchantment). Only the SHAPE of vanilla items is used (as a mask); no vanilla pixels
are copied. Everything runs through conditions in item definitions (minecraft:component + enchantments), so the
server never has to modify items: the client picks the effect itself.

Usage: python3 generate.py <vanilla assets/minecraft dir> <output: glint/src/main/resources>

(Original Dutch notes below.)
Genereert de assets van de Glans-module: geanimeerde enchant-effecten op gereedschap, wapens en harnas, en eigen
"skill book"-plaatjes per betovering. Alleen de VORM van vanilla-items wordt gebruikt (als masker), er worden geen
vanilla-pixels gekopieerd. Alles gaat via voorwaarden in de item-definities (minecraft:component + enchantments), dus de
server hoeft geen items aan te passen: de client kiest zelf het effect.

Gebruik: python3 generate.py <vanilla-assets-map> <uitvoer: glans/src/main/resources>
"""
import io
import json
import math
import os
import random
import shutil
import sys
import zipfile
import zlib

from PIL import Image

VANILLA = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/ref/assets/assets/minecraft'
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources')
NS = 'libreria'
FRAMES = 8
PACK_FORMAT = 97

# ------------------------------------------------------------------ families en betoveringen

# familie → (groep, uitleg). Groep A = rondom (aura), B = op het item, C = losse deeltjes. Per groep is er
# hooguit één effect tegelijk zichtbaar, zodat een item nooit een kerstboom wordt.
FAMILIES = {
    # A: rondom het item
    'fire': ('A', 'Flames', 'Fire Aspect, Flame'),
    'lightning': ('A', 'Lightning', 'Channeling'),
    'frost': ('A', 'Frost crystals', 'Frost Walker'),
    'soul': ('A', 'Soul fire', 'Soul Speed'),
    'holy': ('A', 'Holy light', 'Smite'),
    'poison': ('A', 'Poison drops', 'Bane of Arthropods'),
    'tide': ('A', 'Waves', 'Riptide'),
    'water': ('A', 'Bubbles', 'Respiration, Aqua Affinity, Depth Strider'),
    'sculk': ('A', 'Sculk', 'Swift Sneak'),
    'blast': ('A', 'Smoke puffs', 'Blast Protection'),
    'sweep': ('A', 'Sweeping arc', 'Sweeping Edge'),
    'feather': ('A', 'Feathers', 'Feather Falling'),
    'loyalty': ('A', 'Returning light', 'Loyalty'),
    # B: op het item
    'sharp': ('B', 'Sharpness', 'Sharpness, Piercing'),
    'impale': ('B', 'Sea shine', 'Impaling'),
    'heavy': ('B', 'Gravity', 'Density'),
    'breach': ('B', 'Cracks', 'Breach'),
    'power': ('B', 'Power', 'Power'),
    'multishot': ('B', 'Triple', 'Multishot'),
    'haste': ('B', 'Speed', 'Efficiency, Quick Charge'),
    'wind': ('B', 'Wind', 'Knockback, Punch, Wind Burst, Lunge'),
    'infinity': ('B', 'Stars', 'Infinity'),
    'thorns': ('B', 'Thorns', 'Thorns'),
    'fireguard': ('B', 'Fire guard', 'Fire Protection'),
    'arrowguard': ('B', 'Arrow guard', 'Projectile Protection'),
    'protection': ('B', 'Shield', 'Protection'),
    'silk': ('B', 'Silk', 'Silk Touch'),
    'sturdy': ('B', 'Steel sheen', 'Unbreaking'),
    # C: losse deeltjes
    'curse': ('C', 'Curse smoke', 'Curse of Vanishing, Curse of Binding'),
    'luck': ('C', 'Lucky stars', 'Fortune, Looting'),
    'sea': ('C', 'Sea foam', 'Luck of the Sea, Lure'),
    'mending': ('C', 'Experience orbs', 'Mending'),
}
ORDER = list(FAMILIES)  # ook de prioriteit binnen een groep

ENCHANT_FAMILY = {
    'fire_aspect': 'fire', 'flame': 'fire', 'channeling': 'lightning', 'frost_walker': 'frost', 'soul_speed': 'soul',
    'smite': 'holy', 'bane_of_arthropods': 'poison', 'riptide': 'tide', 'respiration': 'water',
    'aqua_affinity': 'water', 'depth_strider': 'water', 'swift_sneak': 'sculk', 'blast_protection': 'blast',
    'sweeping_edge': 'sweep', 'feather_falling': 'feather', 'loyalty': 'loyalty',
    'sharpness': 'sharp', 'piercing': 'sharp', 'impaling': 'impale', 'density': 'heavy', 'breach': 'breach',
    'power': 'power', 'multishot': 'multishot', 'efficiency': 'haste', 'quick_charge': 'haste', 'knockback': 'wind',
    'punch': 'wind', 'wind_burst': 'wind', 'lunge': 'wind', 'infinity': 'infinity', 'thorns': 'thorns',
    'fire_protection': 'fireguard', 'projectile_protection': 'arrowguard', 'protection': 'protection',
    'silk_touch': 'silk', 'unbreaking': 'sturdy',
    'vanishing_curse': 'curse', 'binding_curse': 'curse', 'fortune': 'luck', 'looting': 'luck',
    'luck_of_the_sea': 'sea', 'lure': 'sea', 'mending': 'mending',
}

COMMON = ['sturdy', 'curse', 'mending']
ARMOR = ['blast', 'fireguard', 'arrowguard', 'protection', 'thorns']
CATEGORIES = {
    'sword': ['fire', 'holy', 'poison', 'sweep', 'sharp', 'wind', 'luck'],
    'axe': ['holy', 'poison', 'sharp', 'haste', 'silk', 'luck'],
    'pickaxe': ['haste', 'silk', 'luck'],
    'shovel': ['haste', 'silk', 'luck'],
    'hoe': ['haste', 'silk', 'luck'],
    'mace': ['fire', 'holy', 'poison', 'heavy', 'breach', 'wind'],
    'spear': ['fire', 'holy', 'poison', 'sharp', 'wind', 'luck'],
    'bow': ['fire', 'power', 'wind', 'infinity'],
    'crossbow': ['sharp', 'multishot', 'haste'],
    'trident': ['lightning', 'tide', 'loyalty', 'impale'],
    'helmet': ['water'] + ARMOR,
    'chestplate': ARMOR,
    'leggings': ['sculk'] + ARMOR,
    'boots': ['frost', 'soul', 'water', 'feather'] + ARMOR,
    'fishing_rod': ['sea'],
    'shears': ['haste'],
    'flint_and_steel': [],
}
# Items die in de hand een ander model hebben: effect alleen in inventory, op de grond en in lijsten
GUI_ONLY = ('spear', 'trident')


def category(item):
    for c in sorted(CATEGORIES, key=len, reverse=True):
        if item == c or item.endswith('_' + c):
            return c
    return None


# ------------------------------------------------------------------ hulpmiddelen

def rng(*seed):
    # Stabiel tussen runs (Python's hash() is per proces anders)
    return random.Random(zlib.crc32(repr(seed).encode()))


def mask_of(png):
    im = Image.open(png).convert('RGBA')
    if im.size != (16, 16):
        im = im.resize((16, 16), Image.NEAREST)
    return [[im.getpixel((x, y))[3] > 0 for x in range(16)] for y in range(16)]


def inside(m, x, y):
    return 0 <= x < 16 and 0 <= y < 16 and m[y][x]


def neighbours(x, y):
    return ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))


def distance_map(m):
    """Afstand (4-buren) van elke pixel tot het item; 0 = op het item."""
    INF = 99
    d = [[0 if m[y][x] else INF for x in range(16)] for y in range(16)]
    frontier = [(x, y) for y in range(16) for x in range(16) if m[y][x]]
    while frontier:
        nxt = []
        for x, y in frontier:
            for nx, ny in neighbours(x, y):
                if 0 <= nx < 16 and 0 <= ny < 16 and d[ny][nx] > d[y][x] + 1:
                    d[ny][nx] = d[y][x] + 1
                    nxt.append((nx, ny))
        frontier = nxt
    return d


def edge_in(m):
    return [(x, y) for y in range(16) for x in range(16) if m[y][x] and any(not inside(m, nx, ny) for nx, ny in neighbours(x, y))]


def noise2(x, y, seed):
    """Gladde waardenruis (periodiek in y over 16), 0..1."""
    def h(ix, iy):
        return rng(seed, ix, iy % 16).random()
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = h(x0, y0) * (1 - sx) + h(x0 + 1, y0) * sx
    b = h(x0, y0 + 1) * (1 - sx) + h(x0 + 1, y0 + 1) * sx
    return a * (1 - sy) + b * sy


def blank():
    return Image.new('RGBA', (16, 16 * FRAMES), (0, 0, 0, 0))


def put(img, f, x, y, rgba):
    if 0 <= x < 16 and 0 <= y < 16:
        r, g, b, a = rgba
        if a <= 0:
            return
        img.putpixel((x, f * 16 + y), (int(r), int(g), int(b), int(min(255, a))))


def ramp(t, stops):
    """Kleurverloop; stops = [(t, (r,g,b,a)), ...] oplopend."""
    if t <= stops[0][0]:
        return stops[0][1]
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0)
            return tuple(c0[i] + (c1[i] - c0[i]) * k for i in range(4))
    return stops[-1][1]


# ------------------------------------------------------------------ effecten

def fire_like(m, name, stops, rise=1.0):
    d = distance_map(m)
    img = blank()
    for f in range(FRAMES):
        for y in range(16):
            for x in range(16):
                dist = d[y][x]
                if dist > 2:
                    continue
                # Vlammen stijgen: boven het item (er zit item onder) is het feller, eronder bijna niets
                above = any(inside(m, x, y + k) for k in range(1, 4))
                below = any(inside(m, x, y - k) for k in range(1, 3)) and not above
                if dist == 0:
                    # Op het item zelf alleen een zachte gloed aan de bovenrand
                    edge = any(not inside(m, nx, ny) for nx, ny in neighbours(x, y)) and not inside(m, x, y - 1)
                    base = 0.55 if edge else 0.0
                else:
                    base = {1: 0.7, 2: 0.35}[dist]
                if base == 0:
                    continue
                if above:
                    base += 0.3
                if below:
                    base -= 0.3
                n = noise2(x * 1.1, y * 0.9 + f * 2.0 * rise, (name, 1))
                v = base * (0.2 + 1.1 * n)
                if v < 0.55:
                    continue
                c = ramp(min(1, v), stops)
                if dist == 0:
                    c = c[:3] + (min(c[3], 150),)
                put(img, f, x, y, c)
    return img


def fire(m, item):
    return fire_like(m, 'fire', [(0.55, (190, 40, 10, 170)), (0.6, (245, 110, 20, 225)), (0.8, (255, 190, 40, 245)),
                                 (1.0, (255, 245, 150, 255))])


def soul(m, item):
    return fire_like(m, 'soul', [(0.55, (20, 90, 120, 160)), (0.6, (40, 170, 200, 220)), (0.8, (90, 230, 240, 245)),
                                 (1.0, (210, 255, 255, 255))])


def frost(m, item):
    d = distance_map(m)
    r = rng('frost', item)
    spikes = [(x, y) for y in range(16) for x in range(16) if d[y][x] == 1 and r.random() < 0.28]
    img = blank()
    for f in range(FRAMES):
        for y in range(16):
            for x in range(16):
                if d[y][x] == 1 and (x + y) % 2 == 0:
                    put(img, f, x, y, (190, 235, 255, 120))
        for i, (x, y) in enumerate(spikes):
            tw = 0.5 + 0.5 * math.sin((f / FRAMES) * 2 * math.pi + i * 1.7)
            put(img, f, x, y, (230, 250, 255, 150 + 100 * tw))
            if tw > 0.7:
                for nx, ny in neighbours(x, y):
                    if d[ny % 16][nx % 16] >= 1 and 0 <= nx < 16 and 0 <= ny < 16:
                        put(img, f, nx, ny, (160, 220, 255, 110))
    return img


def lightning(m, item):
    d = distance_map(m)
    ring = [(x, y) for y in range(16) for x in range(16) if d[y][x] == 1]
    img = blank()
    r = rng('lightning', item)
    for f in range(FRAMES):
        if f not in (0, 1, 4) or not ring:
            # Zwakke statische lading
            for x, y in ring:
                if r.random() < 0.08:
                    put(img, f, x, y, (200, 220, 255, 120))
            continue
        x, y = ring[r.randrange(len(ring))]
        for step in range(7):
            put(img, f, x, y, (255, 255, 190, 255))
            for nx, ny in neighbours(x, y):
                if 0 <= nx < 16 and 0 <= ny < 16 and d[ny][nx] >= 1 and r.random() < 0.25:
                    put(img, f, nx, ny, (170, 200, 255, 170))
            options = [(nx, ny) for nx, ny in neighbours(x, y) if 0 <= nx < 16 and 0 <= ny < 16 and 1 <= d[ny][nx] <= 2]
            if not options:
                break
            x, y = options[r.randrange(len(options))]
    return img


def halo(m, item, color):
    d = distance_map(m)
    img = blank()
    for f in range(FRAMES):
        pulse = 0.5 + 0.5 * math.sin(f / FRAMES * 2 * math.pi)
        for y in range(16):
            for x in range(16):
                if d[y][x] == 1:
                    wave = 0.5 + 0.5 * math.sin((x + y) * 0.8 - f / FRAMES * 2 * math.pi)
                    put(img, f, x, y, color[:3] + (60 + 140 * pulse * wave,))
    return img


def holy(m, item):
    return halo(m, item, (255, 225, 120))


def sculk(m, item):
    img = halo(m, item, (40, 200, 210))
    d = distance_map(m)
    for f in range(FRAMES):
        for y in range(16):
            for x in range(16):
                if d[y][x] == 1 and (x * 3 + y) % 5 == 0:
                    put(img, f, x, y, (10, 70, 80, 200))
    return img


def particles(m, item, name, color, count, motion, size=1, around=True):
    """Losse deeltjes die rondom of op het item bewegen. motion = (dx, dy) per frame."""
    d = distance_map(m)
    r = rng(name, item)
    spots = [(x, y) for y in range(16) for x in range(16) if (1 <= d[y][x] <= 2 if around else d[y][x] == 0)]
    if not spots:
        return blank()
    seeds = [spots[r.randrange(len(spots))] + (r.random(),) for _ in range(count)]
    img = blank()
    for f in range(FRAMES):
        for sx, sy, ph in seeds:
            t = (f / FRAMES + ph) % 1.0
            x = round(sx + motion[0] * t * FRAMES)
            y = round(sy + motion[1] * t * FRAMES)
            fade = math.sin(t * math.pi)
            put(img, f, x % 16 if motion[0] else x, y, color[:3] + (color[3] * fade,))
            if size > 1 and fade > 0.6:
                for nx, ny in neighbours(x, y):
                    put(img, f, nx, ny, color[:3] + (color[3] * fade * 0.45,))
    return img


def poison(m, item):
    return particles(m, item, 'poison', (120, 225, 60, 255), 6, (0, 0.5))


def bubbles(m, item):
    return particles(m, item, 'water', (140, 190, 255, 230), 7, (0, -0.6))


def curse(m, item):
    return particles(m, item, 'curse', (90, 30, 120, 230), 7, (0, -0.5), size=2)


def mending(m, item):
    return particles(m, item, 'mending', (190, 255, 90, 255), 5, (0, -0.4))


def stars(m, item, name, colors, count):
    d = distance_map(m)
    r = rng(name, item)
    spots = [(x, y) for y in range(16) for x in range(16) if d[y][x] <= 1]
    img = blank()
    for i in range(count):
        x, y = spots[r.randrange(len(spots))]
        start = r.randrange(FRAMES)
        col = colors[i % len(colors)]
        for k in range(3):
            f = (start + k) % FRAMES
            a = (255, 200, 90)[k]
            put(img, f, x, y, col + (a,))
            if k == 0:
                for nx, ny in neighbours(x, y):
                    put(img, f, nx, ny, col + (120,))
    return img


def luck(m, item):
    return stars(m, item, 'luck', [(80, 255, 140), (255, 215, 60)], 5)


def infinity(m, item):
    return stars(m, item, 'infinity', [(210, 140, 255), (255, 255, 255)], 6)


def sweep(m, item, name, color, width, alpha, slope=(1, -1), speed=2):
    """Een lichtband die schuin over het item glijdt."""
    img = blank()
    for f in range(FRAMES):
        phase = f * speed
        for y in range(16):
            for x in range(16):
                if not m[y][x]:
                    continue
                v = (x * slope[0] + y * slope[1] - phase) % 16
                dist = min(v, 16 - v)
                if dist < width:
                    put(img, f, x, y, color + (alpha * (1 - dist / width),))
    return img


def sharp(m, item):
    img = sweep(m, item, 'sharp', (255, 255, 255), 1.6, 190)
    # Een klein sterretje dat meeloopt aan de rand
    edges = edge_in(m)
    r = rng('scherp-ster', item)
    if edges:
        x, y = edges[r.randrange(len(edges))]
        for f in (2, 3):
            put(img, f, x, y, (255, 255, 255, 255))
    return img


def power(m, item):
    img = blank()
    for f in range(FRAMES):
        for y in range(16):
            for x in range(16):
                if m[y][x]:
                    v = 0.5 + 0.5 * math.sin((x + y) * 0.7 - f / FRAMES * 2 * math.pi)
                    put(img, f, x, y, (255, 50, 40, 120 * v * v))
    return img


def speed(m, item):
    img = blank()
    for f in range(FRAMES):
        for y in range(16):
            for x in range(16):
                if m[y][x] and (x - y + f * 2) % 6 == 0:
                    put(img, f, x, y, (120, 250, 255, 150))
    return img


def wind(m, item):
    return sweep(m, item, 'wind', (235, 245, 255), 1.2, 140, slope=(1, 1), speed=3)


def thorns(m, item):
    img = blank()
    edges = edge_in(m)
    for f in range(FRAMES):
        pulse = 0.6 + 0.4 * math.sin(f / FRAMES * 2 * math.pi)
        for i, (x, y) in enumerate(edges):
            if i % 3 == 0:
                put(img, f, x, y, (200, 30, 40, 210 * pulse))
            elif i % 3 == 1:
                put(img, f, x, y, (60, 140, 40, 90 * pulse))
    return img


def protection(m, item):
    return sweep(m, item, 'protection', (110, 170, 255), 3.0, 110, slope=(1, 0), speed=2)


def silk(m, item):
    return sweep(m, item, 'silk', (255, 190, 235), 2.5, 120, slope=(1, -1), speed=2)


def ripple(m, item):
    return halo(m, item, (70, 150, 255))


def smoke(m, item):
    img = particles(m, item, 'blast', (150, 150, 155, 220), 6, (0, -0.4), size=2)
    return img


def sweep_arc(m, item):
    return halo(m, item, (235, 240, 255))


def feathers(m, item):
    return particles(m, item, 'feather', (255, 255, 255, 240), 5, (0.15, 0.5))


def orbit(m, item):
    """Een lichtje dat rond het item draait (Loyalty: hij komt altijd terug)."""
    d = distance_map(m)
    ring = [(x, y) for y in range(16) for x in range(16) if d[y][x] == 1]
    img = blank()
    if not ring:
        return img
    cx = sum(x for x, _ in ring) / len(ring)
    cy = sum(y for _, y in ring) / len(ring)
    ring.sort(key=lambda p: math.atan2(p[1] - cy, p[0] - cx))
    for f in range(FRAMES):
        for k, a in ((0, 255), (1, 150), (2, 70)):
            i = (f * len(ring) // FRAMES - k * max(1, len(ring) // 24)) % len(ring)
            x, y = ring[i]
            put(img, f, x, y, (90, 235, 205, a))
            j = (i + len(ring) // 2) % len(ring)
            x, y = ring[j]
            put(img, f, x, y, (90, 235, 205, a * 0.7))
    return img


def impaling(m, item):
    return sweep(m, item, 'impale', (90, 220, 255), 1.8, 170)


def heavy(m, item):
    img = blank()
    for f in range(FRAMES):
        pulse = 0.5 + 0.5 * math.sin(f / FRAMES * 2 * math.pi)
        for y in range(16):
            for x in range(16):
                if m[y][x]:
                    depth = y / 15
                    put(img, f, x, y, (80, 30, 130, 150 * pulse * depth))
    return img


def cracks(m, item):
    r = rng('breach', item)
    pts = [(x, y) for y in range(16) for x in range(16) if m[y][x]]
    img = blank()
    lines = []
    for _ in range(3):
        if not pts:
            break
        x, y = pts[r.randrange(len(pts))]
        line = [(x, y)]
        for _ in range(3):
            x += r.choice((-1, 0, 1))
            y += r.choice((-1, 1))
            if inside(m, x, y):
                line.append((x, y))
        lines.append(line)
    for f in range(FRAMES):
        glow = f in (2, 3, 6)
        for line in lines:
            for x, y in line:
                put(img, f, x, y, (255, 140, 40, 230) if glow else (35, 20, 25, 200))
    return img


def multishot(m, item):
    return stars(m, item, 'multishot', [(255, 120, 120), (120, 255, 120), (120, 160, 255)], 6)


def fire_prot(m, item):
    return sweep(m, item, 'fireguard', (255, 120, 40), 3.0, 120, slope=(1, 0), speed=2)


def proj_prot(m, item):
    return sweep(m, item, 'arrowguard', (225, 225, 235), 1.5, 150, slope=(0, 1), speed=4)


def unbreaking(m, item):
    return sweep(m, item, 'sturdy', (205, 215, 235), 4.0, 70, slope=(1, -1), speed=1)


def sea(m, item):
    return stars(m, item, 'sea', [(70, 210, 230), (140, 255, 210)], 5)


EFFECTS = {
    'fire': fire, 'lightning': lightning, 'frost': frost, 'soul': soul, 'holy': holy, 'poison': poison, 'water': bubbles,
    'sculk': sculk, 'sharp': sharp, 'power': power, 'haste': speed, 'wind': wind, 'infinity': infinity,
    'thorns': thorns, 'protection': protection, 'silk': silk, 'curse': curse, 'luck': luck, 'mending': mending,
    'tide': ripple, 'blast': smoke, 'sweep': sweep_arc, 'feather': feathers, 'loyalty': orbit, 'impale': impaling,
    'heavy': heavy, 'breach': cracks, 'multishot': multishot, 'fireguard': fire_prot, 'arrowguard': proj_prot,
    'sturdy': unbreaking, 'sea': sea,
}

# ------------------------------------------------------------------ skill books

EMBLEMS = {
    'flame': ["...#...", "..##...", "..###..", ".####..", ".#####.", ".##.##.", "..###.."],
    'sword': ["......#", ".....#.", "....#..", ".#.#...", "..#....", ".#.#...", "#......"],
    'sun': ["#..#..#", ".#####.", ".#####.", "#######", ".#####.", ".#####.", "#..#..#"],
    'spider': ["#.#.#.#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#.#.#.#"],
    'wind': [".####..", "#....#.", "...##..", "#####..", "......#", ".####.#", ".....#."],
    'clover': [".##.##.", "#######", ".#####.", "..###..", ".#####.", "#######", ".##.##."],
    'pick': [".#####.", "#..#..#", "...#...", "...#...", "...#...", "...#...", "...#..."],
    'feather': [".....##", "....###", "...###.", "..###..", ".###...", ".#.....", "#......"],
    'shield': ["#######", "#.....#", "#.###.#", "#.###.#", ".#...#.", "..#.#..", "...#..."],
    'drop': ["...#...", "..###..", ".#####.", ".#####.", "#######", "#######", ".#####."],
    'snow': ["#.....#", ".#.#.#.", "..###..", ".##.##.", "..###..", ".#.#.#.", "#.....#"],
    'skull': [".#####.", "#######", "#.#.#.#", "#######", ".#####.", ".#.#.#.", "......."],
    'bolt': ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
    'heart': [".##.##.", "#######", "#######", "#######", ".#####.", "..###..", "...#..."],
    'up': ["...#...", "..###..", ".#####.", "...#...", "...#...", "...#...", "...#..."],
    'inf': [".......", ".##.##.", "#..#..#", "#..#..#", ".##.##.", ".......", "......."],
    'gem': ["...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."],
    'spiral': [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.#...#", "#..####", ".#....."],
    'spikes': ["#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"],
    'ghost': ["..###..", ".#####.", ".#.#.#.", ".#####.", ".#####.", ".#####.", ".#.#.#."],
    'eye': [".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."],
    'trident': ["#..#..#", "#..#..#", "#######", "...#...", "...#...", "...#...", "...#..."],
    'hook': ["...#...", "...#...", "...#...", "...#...", "#..#...", "#..#...", ".##...."],
    'hourglass': ["#######", ".#####.", "..###..", "...#...", "..###..", ".#####.", "#######"],
    'arrows': ["#..#..#", ".#.#.#.", "..###..", "...#...", "...#...", "...#...", "...#..."],
    'weight': [".......", "..###..", ".#####.", "#######", "#######", "#######", "......."],
    'lock': ["..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"],
    'arc': ["....###", "......#", ".......", "#......", "#......", ".#.....", "..##..."],
    'right': ["...#...", "....#..", "#######", "....#..", "...#...", ".......", "......."],
    'wave': [".......", ".##....", "#..#..#", "....##.", ".##....", "#..#..#", "....##."],
}

WEAPON, TOOL, ARMOR, BOW, TRIDENT, FISH, MACE, ANY, CURSE = (
    (165, 40, 40), (190, 125, 30), (40, 75, 175), (45, 130, 60), (30, 140, 150), (110, 160, 40),
    (110, 85, 150), (200, 160, 40), (55, 25, 65))

BOOKS = {
    'sharpness': (WEAPON, 'sword'), 'smite': (WEAPON, 'sun'), 'bane_of_arthropods': (WEAPON, 'spider'),
    'knockback': (WEAPON, 'wind'), 'fire_aspect': ((205, 75, 20), 'flame'), 'looting': (WEAPON, 'clover'),
    'sweeping_edge': (WEAPON, 'arc'),
    'efficiency': (TOOL, 'pick'), 'silk_touch': (TOOL, 'spiral'), 'fortune': (TOOL, 'clover'),
    'protection': (ARMOR, 'shield'), 'fire_protection': (ARMOR, 'flame'), 'blast_protection': (ARMOR, 'weight'),
    'projectile_protection': (ARMOR, 'arrows'), 'feather_falling': (ARMOR, 'feather'), 'respiration': (ARMOR, 'drop'),
    'aqua_affinity': (ARMOR, 'wave'), 'thorns': (ARMOR, 'spikes'), 'depth_strider': (ARMOR, 'drop'),
    'frost_walker': ((90, 170, 220), 'snow'), 'soul_speed': ((40, 120, 140), 'ghost'), 'swift_sneak': ((20, 80, 90), 'eye'),
    'power': (BOW, 'up'), 'punch': (BOW, 'wind'), 'flame': (BOW, 'flame'), 'infinity': (BOW, 'inf'),
    'multishot': (BOW, 'arrows'), 'piercing': (BOW, 'right'), 'quick_charge': (BOW, 'hourglass'),
    'impaling': (TRIDENT, 'trident'), 'riptide': (TRIDENT, 'wave'), 'loyalty': (TRIDENT, 'heart'),
    'channeling': (TRIDENT, 'bolt'),
    'luck_of_the_sea': (FISH, 'clover'), 'lure': (FISH, 'hook'),
    'density': (MACE, 'weight'), 'breach': (MACE, 'spikes'), 'wind_burst': (MACE, 'wind'), 'lunge': (MACE, 'right'),
    'mending': (ANY, 'heart'), 'unbreaking': (ANY, 'gem'),
    'vanishing_curse': (CURSE, 'ghost'), 'binding_curse': (CURSE, 'lock'),
}


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def mix(c, d, k):
    return tuple(int(c[i] + (d[i] - c[i]) * k) for i in range(3))


def book(color, emblem):
    """Een eigen boekje: kaft in kleur, randen, bladzijden rechts en onder, embleem in het midden."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    dark, light = shade(color, 0.55), mix(color, (255, 255, 255), 0.65)
    paper, paper2 = (238, 228, 200), (205, 192, 160)
    gold = (240, 200, 70)
    for y in range(2, 15):
        img.putpixel((2, y), dark + (255,))  # rug
    for y in range(1, 14):
        for x in range(3, 13):
            border = x in (3, 12) or y in (1, 13)
            img.putpixel((x, y), (shade(color, 0.75) if border else color) + (255,))
    for y in range(2, 15):
        img.putpixel((13, y), (paper if y % 2 else paper2) + (255,))
    for x in range(4, 14):
        img.putpixel((x, 14), (paper if x % 2 else paper2) + (255,))
    for x, y in ((3, 1), (12, 1), (3, 13), (12, 13)):
        img.putpixel((x, y), gold + (255,))
    rows = EMBLEMS[emblem]
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == '#':
                img.putpixel((4 + dx, 4 + dy - 1), light + (255,))
    # Leeslint
    img.putpixel((10, 14), (200, 40, 40, 255))
    img.putpixel((10, 15), (160, 30, 30, 255))
    return img


# ------------------------------------------------------------------ uitvoer

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=1)


def write_png(path, img, animated):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    if animated:
        write_json(path + '.mcmeta', {'animation': {'frametime': 2}})


def enchant_condition(family, on_true, on_false, stored=False):
    enchants = sorted(e for e, fam in ENCHANT_FAMILY.items() if fam == family)
    return {
        'type': 'minecraft:condition',
        'property': 'minecraft:component',
        'predicate': 'minecraft:stored_enchantments' if stored else 'minecraft:enchantments',
        'value': [{'enchantments': ['minecraft:' + e for e in enchants]}],
        'on_true': on_true,
        'on_false': on_false,
    }


def main():
    assets = os.path.join(OUT, 'assets')
    for sub in ('minecraft/items', NS):
        shutil.rmtree(os.path.join(assets, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(OUT, 'glint'), ignore_errors=True)

    items_dir = os.path.join(VANILLA, 'items')
    models_dir = os.path.join(VANILLA, 'models/item')
    tex_dir = os.path.join(VANILLA, 'textures/item')

    empty = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    write_png(os.path.join(assets, NS, 'textures/item/glint/empty.png'), empty, False)

    overlay_models = {f: [] for f in FAMILIES}
    count_items = 0
    for fname in sorted(os.listdir(items_dir)):
        item = fname[:-5]
        cat = category(item)
        if cat is None or item.startswith('waxed_') or item in ('bowl',):
            continue
        model_path = os.path.join(models_dir, item + '.json')
        if not os.path.exists(model_path):
            continue
        model = json.load(open(model_path))
        layer0 = (model.get('textures') or {}).get('layer0')
        if not layer0:
            continue
        tex = layer0.split(':')[-1].replace('item/', '', 1)
        png = os.path.join(tex_dir, tex + '.png')
        if not os.path.exists(png):
            continue
        parent = model.get('parent', 'minecraft:item/generated')
        if ':' not in parent:
            parent = 'minecraft:' + parent
        m = mask_of(png)
        families = CATEGORIES[cat] + COMMON

        chains = {'A': [], 'B': [], 'C': []}
        for fam in ORDER:
            if fam not in families:
                continue
            img = EFFECTS[fam](m, item)
            tex_id = f'{NS}:item/glint/{fam}/{item}'
            write_png(os.path.join(assets, NS, f'textures/item/glint/{fam}/{item}.png'), img, True)
            model_id = f'{NS}:item/glint/{fam}/{item}'
            write_json(os.path.join(assets, NS, f'models/item/glint/{fam}/{item}.json'),
                       {'parent': parent, 'textures': {'layer0': tex_id}})
            overlay_models[fam].append(f'assets/{NS}/models/item/glint/{fam}/{item}.json')
            chains[FAMILIES[fam][0]].append((fam, model_id))

        definition = json.load(open(os.path.join(items_dir, fname)))
        layers = [definition['model']]
        for group in 'ABC':
            node = {'type': 'minecraft:empty'}
            for fam, model_id in reversed(chains[group]):
                node = enchant_condition(fam, {'type': 'minecraft:model', 'model': model_id}, node)
            if node['type'] != 'minecraft:empty':
                if any(item.endswith(g) for g in GUI_ONLY):
                    node = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                            'cases': [{'when': ['gui', 'ground', 'fixed', 'on_shelf'], 'model': node}],
                            'fallback': {'type': 'minecraft:empty'}}
                layers.append(node)
        definition['model'] = {'type': 'minecraft:composite', 'models': layers}
        write_json(os.path.join(assets, 'minecraft/items', fname), definition)
        count_items += 1

    # Skill books: eerste betovering in de lijst bepaalt het plaatje
    node = json.load(open(os.path.join(items_dir, 'enchanted_book.json')))['model']
    vanilla_book = node
    for ench, (color, emblem) in reversed(list(BOOKS.items())):
        write_png(os.path.join(assets, NS, f'textures/item/glint/book/{ench}.png'), book(color, emblem), False)
        write_json(os.path.join(assets, NS, f'models/item/glint/book/{ench}.json'),
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/glint/book/{ench}'}})
        node = {'type': 'minecraft:condition', 'property': 'minecraft:component',
                'predicate': 'minecraft:stored_enchantments',
                'value': [{'enchantments': 'minecraft:' + ench}],
                'on_true': {'type': 'minecraft:model', 'model': f'{NS}:item/glint/book/{ench}'},
                'on_false': node}
    write_json(os.path.join(assets, 'minecraft/items/enchanted_book.json'), {'model': node})

    # "Uit"-pakketjes: per familie de overlay-modellen vervangen door een leeg model
    off_dir = os.path.join(OUT, 'glint/off')
    os.makedirs(off_dir, exist_ok=True)
    mcmeta = {'pack': {'description': 'Libreria Glint – effect off', 'min_format': PACK_FORMAT, 'max_format': 1000}}
    empty_model = json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/glint/empty'}})
    for fam, paths in overlay_models.items():
        if not paths:
            continue
        with zipfile.ZipFile(os.path.join(off_dir, fam + '.zip'), 'w', zipfile.ZIP_DEFLATED) as z:
            z.writestr('pack.mcmeta', json.dumps(mcmeta))
            for p in paths:
                z.writestr(p, empty_model)
    with zipfile.ZipFile(os.path.join(off_dir, 'books.zip'), 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('pack.mcmeta', json.dumps(mcmeta))
        z.writestr('assets/minecraft/items/enchanted_book.json', json.dumps({'model': vanilla_book}))

    write_json(os.path.join(OUT, 'glint/families.json'),
               {f: {'group': g, 'name': n, 'enchantments': e, 'items': len(overlay_models[f]),
                    'ids': sorted(k for k, v in ENCHANT_FAMILY.items() if v == f)} for f, (g, n, e) in FAMILIES.items()})
    print(f'{count_items} items, {sum(len(v) for v in overlay_models.values())} effect textures, {len(BOOKS)} books')


if __name__ == '__main__':
    main()
