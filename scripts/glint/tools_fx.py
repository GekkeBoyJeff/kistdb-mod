"""Enchantment effects on tools and weapons, layered so every combination shows: a texture part painted into the item
per enchantment and level (its glowing pixels carry a code in their alpha for the core shader), and auras: animated
planes behind the item, larger than it, so flames can rise past the tip.

Everything is chosen by the client from the item's enchantments (conditions in the vanilla item definitions; see
generate.py), so the server never changes an item. Layers in the order they are painted:

    texture parts   one per enchantment that has one, at its level; some take the colour of another (Fire Aspect's
                    heat takes the colour of the damage enchantment)
    main aura       the one big aura, by priority (fire first, coloured by the damage enchantment)
    touches         small extras on top (sparks, coins, gusts, petals, bubbles…)

In the inventory only the texture parts show; in a hand, on the ground and in a frame the auras are added. Only the
SHAPE of vanilla items is used (as regions); no vanilla pixels are copied: every texture part pixel is a colour of its
own, picked by how bright the vanilla pixel under it is, so the shading follows the item.
"""
import colorsys
import json
import math
import os

import numpy as np
from PIL import Image

import sword_aura as A

NS = 'libreria'
MATS = ['wooden', 'stone', 'copper', 'iron', 'golden', 'diamond', 'netherite']
CODE = {'flicker': 252, 'pulse': 253, 'flow': 251, 'twinkle': 250, 'steady': 254}
NO_GLINT = 0xFEFEFE  # a tint that marks a quad for the item shader: no enchantment glint (the effect takes its place)

# short name -> (enchantment id, highest level)
ENCH = {
    'sharp': ('sharpness', 5), 'smite': ('smite', 5), 'bane': ('bane_of_arthropods', 5), 'fire': ('fire_aspect', 2),
    'knock': ('knockback', 2), 'loot': ('looting', 3), 'sweep': ('sweeping_edge', 3), 'eff': ('efficiency', 5),
    'fortune': ('fortune', 3), 'silk': ('silk_touch', 1), 'lunge': ('lunge', 3), 'power': ('power', 5),
    'punch': ('punch', 2), 'flame': ('flame', 1), 'infinity': ('infinity', 1), 'quick': ('quick_charge', 3),
    'multishot': ('multishot', 1), 'piercing': ('piercing', 4), 'loyalty': ('loyalty', 3), 'riptide': ('riptide', 3),
    'channeling': ('channeling', 1), 'impaling': ('impaling', 5), 'density': ('density', 5), 'breach': ('breach', 4),
    'windburst': ('wind_burst', 3), 'lure': ('lure', 3), 'luck': ('luck_of_the_sea', 3),
    'unbreak': ('unbreaking', 3), 'mend': ('mending', 1), 'vanish': ('vanishing_curse', 1),
}
# the quiet ones: on almost every tool, so they leave the enchantment glint on (only a small mark of their own)
QUIET = ('unbreak', 'mend', 'vanish')
# the effect family of each short name (families.json: what the "choose effects" window switches off)
FAMILY = {
    'sharp': 'sharp', 'piercing': 'sharp', 'smite': 'holy', 'bane': 'poison', 'fire': 'fire', 'flame': 'fire',
    'knock': 'wind', 'punch': 'wind', 'windburst': 'wind', 'lunge': 'wind', 'loot': 'luck', 'fortune': 'luck',
    'sweep': 'sweep', 'eff': 'haste', 'quick': 'haste', 'silk': 'silk', 'power': 'power', 'infinity': 'infinity',
    'multishot': 'multishot', 'loyalty': 'loyalty', 'riptide': 'tide', 'channeling': 'lightning', 'impaling': 'impale',
    'density': 'heavy', 'breach': 'breach', 'lure': 'sea', 'luck': 'sea', 'unbreak': 'sturdy', 'mend': 'mending',
    'vanish': 'curse',
}

# kind -> sprites (name -> texture pattern, {m} = material), materials, enchantments
KINDS = {
    'sword': ({'sword': '{m}_sword'}, MATS, ['sharp', 'smite', 'bane', 'fire', 'sweep', 'loot', 'knock', 'unbreak', 'mend', 'vanish']),
    'axe': ({'axe': '{m}_axe'}, MATS, ['sharp', 'smite', 'bane', 'eff', 'silk', 'fortune', 'unbreak', 'mend', 'vanish']),
    'pickaxe': ({'pickaxe': '{m}_pickaxe'}, MATS, ['eff', 'silk', 'fortune', 'unbreak', 'mend', 'vanish']),
    'shovel': ({'shovel': '{m}_shovel'}, MATS, ['eff', 'silk', 'fortune', 'unbreak', 'mend', 'vanish']),
    'hoe': ({'hoe': '{m}_hoe'}, MATS, ['eff', 'silk', 'fortune', 'unbreak', 'mend', 'vanish']),
    'shears': ({'shears': 'shears'}, [None], ['eff', 'unbreak', 'mend', 'vanish']),
    'spear': ({'spear': '{m}_spear', 'hand': '{m}_spear_in_hand'}, MATS,
              ['sharp', 'smite', 'bane', 'fire', 'knock', 'lunge', 'loot', 'unbreak', 'mend', 'vanish']),
    'bow': ({'bow': 'bow', 'pull0': 'bow_pulling_0', 'pull1': 'bow_pulling_1', 'pull2': 'bow_pulling_2'}, [None],
            ['flame', 'infinity', 'power', 'punch', 'unbreak', 'mend', 'vanish']),
    'crossbow': ({'crossbow': 'crossbow_standby', 'pull0': 'crossbow_pulling_0', 'pull1': 'crossbow_pulling_1',
                  'pull2': 'crossbow_pulling_2', 'arrow': 'crossbow_arrow', 'rocket': 'crossbow_firework'}, [None],
                 ['piercing', 'quick', 'multishot', 'unbreak', 'mend', 'vanish']),
    'trident': ({'trident': 'trident'}, [None], ['impaling', 'channeling', 'loyalty', 'riptide', 'unbreak', 'mend', 'vanish']),
    'mace': ({'mace': 'mace'}, [None], ['smite', 'bane', 'density', 'breach', 'fire', 'windburst', 'unbreak', 'mend', 'vanish']),
    'rod': ({'rod': 'fishing_rod', 'cast': 'fishing_rod_cast'}, [None], ['lure', 'luck', 'unbreak', 'mend', 'vanish']),
}
# the 16 px sprite of each kind a 32 px one borrows its regions' meaning from (the spear in hand is drawn bigger)
BIG = {'hand'}

BLOOD = [(34, 2, 6), (58, 4, 10), (88, 8, 14), (120, 12, 18), (150, 18, 22), (178, 28, 30), (214, 60, 54)]
HEAT = [(64, 16, 8), (112, 26, 10), (164, 46, 12), (212, 82, 18), (246, 132, 30), (255, 184, 64), (255, 238, 176)]
HEATS = {None: HEAT,
         'sharp': [(54, 6, 8), (96, 12, 12), (150, 24, 18), (200, 48, 26), (236, 90, 44), (250, 150, 90), (255, 220, 190)],
         'smite': [(96, 60, 10), (140, 94, 18), (190, 136, 30), (232, 182, 54), (252, 214, 96), (255, 238, 160), (255, 254, 236)],
         'bane': [(16, 56, 12), (34, 92, 18), (60, 140, 28), (100, 190, 40), (150, 228, 64), (196, 248, 120), (240, 255, 200)]}
GOLD = [(74, 46, 8), (112, 74, 12), (152, 106, 20), (198, 146, 34), (232, 188, 62), (250, 216, 98), (255, 246, 196)]
VENOM = [(12, 42, 10), (24, 72, 16), (40, 112, 24), (70, 152, 36), (110, 198, 56), (152, 228, 82), (212, 255, 152)]
WIND = [(52, 66, 84), (84, 102, 124), (122, 142, 164), (162, 182, 202), (196, 214, 230), (224, 236, 246), (248, 252, 255)]
NIGHT = [(10, 6, 26), (22, 12, 48), (38, 20, 76), (56, 32, 104), (78, 46, 132), (104, 66, 160), (140, 100, 196)]
WATER = [(8, 30, 70), (14, 52, 110), (22, 80, 150), (36, 112, 190), (62, 150, 220), (110, 192, 240), (190, 236, 255)]
GRAV = [(8, 4, 16), (18, 8, 32), (30, 14, 52), (44, 22, 74), (62, 32, 98), (84, 46, 126), (120, 72, 170)]
SILK = [(150, 110, 140), (190, 140, 176), (220, 172, 206), (238, 200, 226), (248, 222, 238), (255, 238, 248), (255, 250, 254)]
AMETHYST = (198, 120, 255)
STEEL = [(60, 66, 78), (92, 100, 116), (126, 136, 154), (162, 172, 190), (196, 206, 222), (222, 230, 242), (246, 250, 255)]
MEND = (150, 240, 90)
VIOLET = (150, 96, 230)
DAMAGE = ('sharp', 'smite', 'bane', 'density', 'breach')


def hsv(c):
    return colorsys.rgb_to_hsv(*[v / 255 for v in c])


def rank(img):
    """Each colour of the item's palette by brightness, 0 (darkest) .. 6 (brightest), so a recolour keeps the shading."""
    w, h = img.size
    cols = sorted({img.getpixel((x, y))[:3] for y in range(h) for x in range(w) if img.getpixel((x, y))[3]},
                  key=lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return {c: round(k * 6 / max(1, len(cols) - 1)) for k, c in enumerate(cols)}


def regions(kind, img):
    """Splits the item's pixels into parts: head (the working end), grip, string (bow, crossbow, fishing line), and for
    the rod the bobber (end) and reel. Read from the diamond (or only) version: every material has the same shape."""
    w, h = img.size
    px = [(x, y) for y in range(h) for x in range(w) if img.getpixel((x, y))[3]]
    col = lambda p: img.getpixel(p)[:3]
    wood = lambda p: 12 <= hsv(col(p))[0] * 360 <= 45 and hsv(col(p))[1] > 0.55
    grey = lambda p: hsv(col(p))[1] < 0.06
    r = {'head': [], 'grip': [], 'string': [], 'end': [], 'reel': []}
    for p in px:
        if kind == 'shears':
            r['head' if grey(p) else 'grip'].append(p)
        elif kind in ('bow', 'crossbow'):
            r['string' if grey(p) else 'head'].append(p)
        elif kind == 'trident':
            r['head' if grey(p) else 'grip'].append(p)
        elif kind == 'mace':
            hh, s, v = hsv(col(p))
            r['head' if s < 0.3 and 0.55 < hh < 0.65 and p[1] <= 8 else 'grip'].append(p)
        elif kind == 'rod':
            r['grip' if wood(p) else ('end' if p[1] >= 11 and p[0] >= 9 else 'reel' if p[0] < 9 and p[1] >= 10 else 'string')].append(p)
        else:
            r['grip' if wood(p) else 'head'].append(p)
    if kind in ('bow', 'crossbow'):
        r['grip'] = []
    anchor = r['grip'] or [(w // 2, h // 2)]
    if kind == 'bow':
        anchor = [(7, 8), (8, 7)]
    r['dist'] = {p: min(math.hypot(p[0] - a[0], p[1] - a[1]) for a in anchor) for p in r['head']}
    hs = set(r['head'])
    r['edge'] = [p for p in r['head'] if any((p[0] + dx, p[1] + dy) not in hs for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    r['core'] = [p for p in r['head'] if p not in set(r['edge'])] or list(r['head'])
    hd = r['head'] or [(w - 1, 0)]
    r['grip'].sort(key=lambda p: min(math.hypot(p[0] - q[0], p[1] - q[1]) for q in hd))
    return r


def far_first(r, part='head'):
    return sorted(r[part], key=lambda p: -r['dist'].get(p, 0))


def near_first(r, part='head'):
    return sorted(r[part], key=lambda p: r['dist'].get(p, 0))


# ------------------------------------------------------------------------------------------------ texture parts
def paint(kind, img, ref, e):
    """The item with its texture parts for the enchantments e ({short name: level}). img: this material's sprite (for
    the shading), ref: the diamond one (for the regions). Returns (sprite, {pixel: glow mode})."""
    if kind == 'sword':
        return paint_sword(img, e)
    out = img.copy()
    px = out.load()
    rk = rank(img)
    r = regions(kind, ref)
    glow = {}
    top = {k: ENCH[k][1] for k in ENCH}

    def frac(k):
        return min(1.0, e[k] / max(1, top[k]))

    def recolour(p, ramp_, shift=0):
        px[p] = ramp_[max(0, min(6, rk.get(img.getpixel(p)[:3], 3) + shift))] + (255,)

    def take(lst, f, lo=0.15, hi=0.85):
        return lst[:max(1, round(len(lst) * (lo + (hi - lo) * f)))] if lst else []

    dmg = next((k for k in DAMAGE if k in e), None)
    # the quiet ones first, so anything else paints over them: Unbreaking a steel sheen along the edge, Mending two
    # green glints of experience, Curse of Vanishing a violet vein
    if 'unbreak' in e:
        # (a rod has no head: its reel and line take it)
        edge = sorted(r['edge'], key=lambda p: -r['dist'].get(p, 0)) or r['reel'] + r['string']
        for i, p in enumerate(take(edge, frac('unbreak'), 0.15, 0.5)):
            if i % 2 == 0:
                recolour(p, STEEL, 1)
                glow[p] = 'flow'
    if 'mend' in e:
        for p in r['grip'][:1] + (near_first(r, 'core')[1:2] if r['head'] else r['end'][:1]):
            px[p] = MEND + (255,)
            glow[p] = 'twinkle'
    if 'vanish' in e:
        mids = [p for p in far_first(r) if 2 <= rk.get(img.getpixel(p)[:3], 0) <= 4][:4] if r['head'] else r['grip'][2:8:2]
        for p in mids:
            px[p] = VIOLET + (255,)
            glow[p] = 'flow'
    if 'sharp' in e or 'piercing' in e:
        k = 'sharp' if 'sharp' in e else 'piercing'
        part = 'string' if kind == 'crossbow' else 'head'
        lst = far_first(r, part) if part == 'head' else sorted(r['string'], key=lambda p: -(p[0] - p[1]))
        for p in take(lst, frac(k), 0.12, 0.7):
            recolour(p, BLOOD)
            if rk.get(img.getpixel(p)[:3], 0) >= 5:
                glow[p] = 'pulse'
    if 'smite' in e:
        for k, p in enumerate(take(near_first(r, 'core'), frac('smite'), 0.2, 1.0)):
            px[p] = (GOLD[6] if k % 2 == 0 else GOLD[5]) + (255,)
            glow[p] = 'pulse'
        if e['smite'] >= 4:
            for p in r['grip'][:3]:
                recolour(p, GOLD, 1)
    if 'bane' in e:
        edge = sorted(r['edge'], key=lambda p: -r['dist'].get(p, 0))
        for p in take(edge, frac('bane'), 0.15, 0.8):
            recolour(p, VENOM, 1)
            glow[p] = 'pulse'
    if 'density' in e:
        for p in take(far_first(r), frac('density'), 0.1, 0.85):
            recolour(p, GRAV, 1)
        for p in take(sorted(r['edge'], key=lambda p: -r['dist'].get(p, 0)), frac('density'), 0.1, 0.5):
            px[p] = (176, 120, 255, 255)
            glow[p] = 'pulse'
    if 'breach' in e:
        rnd = np.random.default_rng(4)
        cracks = [r['head'][i] for i in rnd.permutation(len(r['head']))]
        for p in cracks[:2 + 3 * e['breach']]:
            px[p] = HEAT[5 if rnd.random() < 0.5 else 4] + (255,)
            glow[p] = 'flicker'
    if 'fire' in e or 'flame' in e:
        k = 'fire' if 'fire' in e else 'flame'
        heat = HEATS.get(dmg, HEAT) if dmg in HEATS else HEAT
        lst = near_first(r) if kind != 'bow' else far_first(r)
        sel = take(lst, frac(k) if k == 'fire' else 1.0, 0.25, 0.55)
        for i, p in enumerate(sel):
            px[p] = heat[max(1, 6 - int(5 * i / max(1, len(sel))))] + (255,)
            glow[p] = 'flicker'
    if 'eff' in e or 'quick' in e:
        # a honed working edge that glows warm white, spreading from the far end per level. Only the light pixels of
        # the head: the outline stays, so a diamond pick stays a diamond pick. Grey metal gets a warmer gleam
        k = 'eff' if 'eff' in e else 'quick'
        light = [p for p in far_first(r) if rk.get(img.getpixel(p)[:3], 0) >= 4] or far_first(r)
        if kind == 'crossbow':
            light = sorted(r['string'], key=lambda p: -(p[0] - p[1]))
        if light:
            honed = (255, 226, 140) if hsv(img.getpixel(light[0])[:3])[1] < 0.1 else (255, 250, 222)
            for p in take(light, frac(k), 0.2, 1.0):
                px[p] = honed + (255,)
                glow[p] = 'flow'
    if 'multishot' in e:
        for p in sorted(r['head'], key=lambda p: p[0] - p[1])[-6::2]:
            px[p] = (150, 240, 255, 255)
            glow[p] = 'pulse'
    if 'impaling' in e:
        mids = [p for p in far_first(r) if 2 <= rk.get(img.getpixel(p)[:3], 0) <= 4]
        for i, p in enumerate(take(mids, frac('impaling'), 0.2, 1.0)):
            px[p] = (110, 236, 214, 255) if i % 2 else (160, 255, 236, 255)
            glow[p] = 'pulse'
    if 'channeling' in e:
        for i, p in enumerate(far_first(r)):
            if i % 3 == 0:
                px[p] = (200, 236, 255, 255)
                glow[p] = 'flicker'
    if 'loyalty' in e or 'riptide' in e:
        k = 'loyalty' if 'loyalty' in e else 'riptide'
        for i, p in enumerate(take(r['grip'], frac(k), 0.3, 1.0)):
            if k == 'loyalty':
                if i % 2 == 0:
                    px[p] = (120, 236, 255, 255)
                    glow[p] = 'pulse'
            else:
                recolour(p, WATER, 1)
                glow[p] = 'flow'
    if 'infinity' in e:
        for p in r['head']:
            recolour(p, NIGHT, 1)
        for p in [r['head'][i] for i in np.random.default_rng(8).permutation(len(r['head']))[:6]]:
            px[p] = (255, 250, 255, 255)
            glow[p] = 'twinkle'
    if 'power' in e:
        for p in r['string']:
            px[p] = GOLD[6 if e['power'] >= 3 else 5] + (255,)
            glow[p] = 'pulse'
        bands = sorted(r['head'], key=lambda p: -r['dist'].get(p, 0))
        for i, p in enumerate(take(bands, frac('power'), 0.0, 0.5)):
            if i % 3 == 0:
                recolour(p, GOLD, 2)
    if 'lure' in e or 'luck' in e:
        for i, p in enumerate(r['string']):
            if 'lure' in e:
                px[p] = WATER[5 if i % 2 else 4] + (255,)
                glow[p] = 'flow'
            elif i % 3 == 0:
                px[p] = GOLD[5] + (255,)
                glow[p] = 'pulse'
        for p in r['end']:
            if 'luck' in e:
                recolour(p, GOLD, 1)
                glow[p] = 'twinkle'
    if 'silk' in e:
        # a silk ribbon wound round the top half of the grip (the wood shows between the turns), the head's highlights pink
        half = r['grip'][:max(2, len(r['grip']) // 2)]
        for i, p in enumerate(half):
            if i % 3 != 2:
                recolour(p, SILK, 1 if i % 3 == 0 else -1)
        for p in r['head']:
            if rk.get(img.getpixel(p)[:3], 0) >= 5:
                px[p] = (255, 226, 244, 255)
                glow[p] = 'pulse'
    if 'windburst' in e:
        marks = [p for i, p in enumerate(near_first(r)) if i % 3 == 1][:2 + 2 * e['windburst']]
        for p in marks:
            px[p] = (214, 240, 255, 255)
            glow[p] = 'flow'
    for k in ('knock', 'punch', 'windburst'):
        if k in e:
            grip = r['grip'] if kind != 'bow' else []
            for i, p in enumerate(grip[3 if ('loot' in e or 'fortune' in e) else 0:]):
                recolour(p, WIND, 1 if i % 2 == 0 else -1)
            if kind == 'bow':
                mid = sorted(r['head'], key=lambda p: r['dist'].get(p, 0))[:4]
                for i, p in enumerate(mid):
                    recolour(p, WIND, 1 if i % 2 == 0 else -1)
            break
    if 'lunge' in e:
        light = [p for p in far_first(r) if rk.get(img.getpixel(p)[:3], 0) >= 3]
        for p in take(light, frac('lunge'), 0.25, 1.0):
            px[p] = (236, 250, 255, 255)
            glow[p] = 'flow'
    for k in ('fortune', 'loot'):
        if k in e:
            # gold flecks in the head (like ore), more per level, a gold top on the grip from II, amethyst set in it.
            # The flecks sit in the mid tones, where no other effect paints, so they never cover one
            rnd = np.random.default_rng(6)
            pool = [p for p in r['head'] if 2 <= rk.get(img.getpixel(p)[:3], 0) <= 3] or list(r['head'])
            flecks = [pool[i] for i in rnd.permutation(len(pool))][:2 * e[k] + 1]
            for i, p in enumerate(flecks):
                px[p] = GOLD[6 if i % 2 == 0 else 5] + (255,)
                glow[p] = 'pulse'
            if e[k] >= 2:
                for p in r['grip'][:3]:
                    recolour(p, GOLD, 2)
            for p in near_first(r, 'core')[:max(1, e[k] - 1)]:
                px[p] = AMETHYST + (255,)
                glow[p] = 'twinkle'
    return out, glow


# the sword: every enchantment owns a part of it (see sword_parts.py)
WINGS = A.GUARD - {(5, 9), (6, 9), (5, 10), (6, 10)}
HILT_GEM = [(6, 9), (5, 10)]


def blade(x, y):
    return x - y, x + y


def paint_sword(img, e):
    out = img.copy()
    px = out.load()
    rk = rank(img)
    glow = {}
    blade_px = set(A.blade_pixels(img))

    def recolour(p, ramp_, shift=0):
        orig = img.getpixel(p)[:3]
        px[p] = ramp_[max(0, min(6, rk.get(orig, 3) + shift))] + (255,)

    if 'unbreak' in e:
        lv = min(3, e['unbreak'])
        for p in blade_px:
            s, c = blade(*p)
            if c == 13 and s % 4 == 1 and s >= 13 - 4 * lv:
                recolour(p, STEEL, 1)
                glow[p] = 'flow'
    if 'mend' in e:
        for p in (sorted(A.HANDLE)[0], (1, 14)):
            px[p] = MEND + (255,)
            glow[p] = 'twinkle'
    if 'vanish' in e:
        for p in blade_px:
            s, c = blade(*p)
            if c == 16 and s in (2, 6, 10):
                px[p] = VIOLET + (255,)
                glow[p] = 'flow'
    if 'sharp' in e:
        lv = min(5, e['sharp'])
        tip = {1: 99, 2: 12, 3: 9, 4: 6, 5: 3}[lv]
        drips = {1: {}, 2: {14: 2}, 3: {14: 3, 16: 2}, 4: {14: 4, 16: 3, 13: 1}, 5: {14: 7, 16: 4, 15: 2, 13: 2}}[lv]
        splat = {(15, 0), (14, 1), (13, 2), (15, 1)} if lv == 1 else set()
        for p in blade_px:
            s, c = blade(*p)
            if s >= tip - drips.get(c, 0) or p in splat:
                recolour(p, BLOOD)
                if rk.get(img.getpixel(p)[:3], 0) >= 5:  # the wet shine catches the light: you see it at night too
                    glow[p] = 'pulse'
        if lv >= 3:
            for c, d in drips.items():
                for p in [p for p in blade_px if blade(*p)[1] == c and blade(*p)[0] == tip - d]:
                    px[p] = BLOOD[6] + (255,)
                    glow[p] = 'pulse'
        if lv >= 5:
            for p in HILT_GEM:
                recolour(p, BLOOD, 1)
    if 'smite' in e:
        lv = min(5, e['smite'])
        for p in HILT_GEM:
            recolour(p, GOLD, 2)
            glow[p] = 'pulse'
        reach = {1: -9, 2: 2, 3: 7, 4: 15, 5: 15}[lv]
        for p in blade_px:
            s, c = blade(*p)
            if c == 15 and s <= reach:
                px[p] = (GOLD[6] if s % 3 == 0 else GOLD[5]) + (255,)
                glow[p] = 'pulse'
        if lv >= 4:
            for p in A.POMMEL:
                recolour(p, GOLD)
        if lv >= 5:
            for p in blade_px:
                if blade(*p)[1] == 14:
                    px[p] = (255, 248, 214, 255)
                    glow[p] = 'flow'
    if 'bane' in e:
        lv = min(5, e['bane'])
        start = {1: 11, 2: 8, 3: 3, 4: -2, 5: -2}[lv]
        for p in blade_px:
            s, c = blade(*p)
            if c >= 16 and s >= start:
                recolour(p, VENOM, 1)
                if c == 16:
                    glow[p] = 'pulse'
            if lv >= 4 and c == 15 and s % 3 == 1 and s >= start:
                recolour(p, VENOM, 1)
        if lv >= 4:  # drips hanging under the lower edge
            for x, y in ((11, 5), (13, 3), (9, 7))[:1 if lv == 4 else 3]:
                if not img.getpixel((x, y + 1))[3]:
                    px[x, y + 1] = VENOM[5] + (255,)
                    glow[(x, y + 1)] = 'pulse'
    if 'fire' in e:
        lv = min(2, e['fire'])
        heat = HEATS[next((k for k in ('sharp', 'smite', 'bane') if k in e), None)]
        top = {1: 5, 2: 11}[lv]
        wide = {1: 1, 2: 6}[lv]
        for p in blade_px:
            s, c = blade(*p)
            band = (14, 16) if s <= wide else (15, 15)
            if band[0] <= c <= band[1] and s <= top:
                h = 6 - int(5 * max(0, s + 2) / (top + 3))
                px[p] = heat[max(1, min(6, h if c == 15 else h - 2))] + (255,)
                glow[p] = 'flicker'
            if lv >= 2 and c in (13, 17) and s in (1, 4, 8):  # glowing cracks at the edges
                px[p] = heat[4] + (255,)
                glow[p] = 'flicker'
        if 'smite' not in e:
            for p in HILT_GEM:
                px[p] = heat[5] + (255,)
                glow[p] = 'flicker'
    if 'sweep' in e:
        lv = min(3, e['sweep'])
        for s0 in (12, 8, 4)[:lv]:  # chevrons pointing at the tip, a swoosh per level
            for s, c in ((s0, 14), (s0 + 1, 15), (s0, 16)):
                p = ((s + c) // 2, (c - s) // 2)
                if p in blade_px:
                    px[p] = (236, 250, 255, 255)
                    glow[p] = 'flow'
    if 'loot' in e:
        lv = min(3, e['loot'])
        wings = WINGS if lv >= 2 else {p for p in WINGS if p[0] - p[1] in (-4, -5) and (p[0] <= 3 or p[0] >= 8)}
        for p in wings:
            recolour(p, GOLD, 2)
        gems = {1: [(3, 7)], 2: [(3, 7), (8, 12)], 3: [(3, 7), (8, 12)] + HILT_GEM}[lv]
        for p in gems:
            if 'smite' in e and p in HILT_GEM:
                continue
            px[p] = AMETHYST + (255,)
            glow[p] = 'twinkle'
    if 'knock' in e:
        lv = min(2, e['knock'])
        for k, p in enumerate(sorted(A.HANDLE, key=lambda q: q[0] - q[1])):
            recolour(p, WIND, 1 if k % 2 == 0 else -1)
        if lv >= 2:
            for p in A.POMMEL:
                recolour(p, WIND)
            px[1, 14] = (200, 240, 255, 255)
            glow[(1, 14)] = 'pulse'
    return out, glow


# ------------------------------------------------------------------------------------------------ auras
def tongues(pixels, solid, reach, *, up, down, seed, sx, sy, speed, base, cut, power, stops, sway=0.0, warp=0.35):
    d = A.distance(pixels, up=up, down=down)
    inside = A.inside(solid)
    u, v = A.grid()
    nz, nz2 = A.Noise(seed), A.Noise(seed + 1)
    frames = []
    for f in range(A.N):
        s = f / A.N * 8 * speed
        w = warp * nz2(u * 0.3, v * 0.3 + s * 0.5)
        n = nz.fbm(u * sx + w + sway * math.sin(f / A.N * 6.283), v * sy + s)
        t = np.clip((n - cut) / (1 - cut), 0, 1) ** power
        rr = reach * (base + (1.6 - base) * t)
        i = 1 - d / np.maximum(rr, 1e-3)
        a = A.ramp(np.clip(i, 0, 1), stops)
        a[~((d < rr) & ~inside)] = 0
        frames.append(a)
    return frames


FLAMES = {
    None: [(0.72, (255, 244, 190, 245)), (0.48, (255, 200, 60, 245)), (0.24, (250, 118, 24, 235)), (0.0, (200, 46, 16, 180))],
    'sharp': [(0.72, (255, 214, 190, 245)), (0.48, (240, 92, 60, 240)), (0.24, (186, 24, 30, 230)), (0.0, (96, 8, 18, 190))],
    'smite': [(0.72, (255, 255, 240, 245)), (0.48, (255, 232, 120, 240)), (0.24, (252, 186, 52, 230)), (0.0, (210, 124, 30, 180))],
    'bane': [(0.72, (240, 255, 196, 245)), (0.48, (176, 242, 72, 240)), (0.24, (84, 194, 40, 230)), (0.0, (32, 112, 30, 180))],
    'density': [(0.72, (230, 200, 255, 245)), (0.48, (176, 110, 250, 240)), (0.24, (110, 50, 190, 230)), (0.0, (50, 20, 100, 180))],
    'breach': [(0.72, (255, 240, 200, 245)), (0.48, (255, 160, 60, 240)), (0.24, (210, 70, 20, 230)), (0.0, (110, 30, 12, 180))],
}
HOLY = [(0.62, (255, 252, 222, 245)), (0.32, (255, 222, 96, 235)), (0.0, (240, 172, 40, 175))]
VEN = [(0.6, (200, 255, 110, 245)), (0.3, (104, 214, 52, 230)), (0.0, (44, 130, 30, 175))]
BLOODLUST = [(0.6, (230, 70, 60, 235)), (0.3, (160, 18, 26, 215)), (0.0, (90, 8, 16, 170))]
TIDE = [(0.6, (220, 246, 255, 245)), (0.3, (90, 180, 240, 230)), (0.0, (30, 90, 170, 175))]
LOYAL = [(0.6, (220, 252, 255, 240)), (0.3, (130, 230, 255, 215)), (0.0, (60, 160, 220, 160))]
GRAVITY = [(0.6, (200, 160, 255, 240)), (0.3, (120, 70, 210, 220)), (0.0, (50, 24, 110, 170))]
POWER = [(0.6, (255, 250, 220, 240)), (0.3, (255, 220, 110, 215)), (0.0, (220, 160, 50, 160))]
SPARK = [(0.55, (236, 248, 255, 241)), (0.25, (130, 190, 255, 225)), (0.0, (70, 110, 230, 177))]


def blank():
    return [np.zeros((A.SIZE, A.SIZE, 4), np.uint8) for _ in range(A.N)]


def line(a, x0, y0, x1, y1, col, fade=True):
    n = max(2, int(max(abs(x1 - x0), abs(y1 - y0)) * A.RES * 1.5))
    for k in range(n + 1):
        t = k / n
        c = col[:3] + (int(col[3] * (1 - 0.6 * t)) if fade else col[3],)
        A.put(a, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c)


def main_aura(kind, ref, r, key, lv, dmg=None):
    """The big aura of one enchantment (key) at a level; fire takes the colour of the damage enchantment dmg."""
    head = r['head'] or r['grip']
    solid = [(x, y) for y in range(16) for x in range(16) if ref.getpixel((x, y))[3]]
    f = lv / ENCH[key][1]
    if kind == 'sword':
        if key == 'fire':
            reach = {1: 1.1, 2: 2.0}[lv] + (0.4 if dmg else 0.0)
            frames = A.tongues(ref, reach, up=0.42, down=2.6, seed=3, sx=0.55, sy=0.42, speed=1, base=0.12, cut=0.32,
                               power=1.6, stops=FLAMES[dmg])
            if lv >= 2:
                embers = {None: (255, 190, 70), 'sharp': (240, 80, 60), 'smite': (255, 240, 170), 'bane': (180, 250, 90)}[dmg]
                r_ = np.random.default_rng(5)
                spots = [(r_.uniform(6, 16), r_.uniform(-1, 7)) for _ in range(7)]
                for fi, a in enumerate(frames):
                    for k, (x0, y0) in enumerate(spots):
                        ph = (fi / A.N + k / 7) % 1
                        if ph < 0.85:
                            A.put(a, x0 + 0.6 * math.sin(ph * 6.28 + k), y0 - ph * 6, embers + (245 if ph < 0.5 else 200,))
            return frames
        if key == 'smite':
            return A.holy(ref, lv)
        if key == 'bane':
            return A.venom(ref, lv)
        if key == 'sharp':
            return A.tongues(ref, {4: 0.6, 5: 1.0}[lv], up=0.5, down=1.2, seed=61, sx=0.9, sy=0.35, speed=0.7, base=0.0,
                             cut=0.45, power=1.2, stops=BLOODLUST, warp=0.3)
    if key in ('fire', 'flame'):
        src = head if kind != 'bow' else far_first(r)[:max(4, len(head) // 3)]
        return tongues(src, solid, 0.9 + 1.1 * (f if key == 'fire' else 1.0), up=0.42, down=2.6, seed=3, sx=0.55, sy=0.42,
                       speed=1, base=0.12, cut=0.32, power=1.6, stops=FLAMES.get(dmg, FLAMES[None]))
    if key == 'riptide':
        return tongues(r['grip'] + head, solid, 0.5 + 0.9 * f, up=0.6, down=1.0, seed=71, sx=0.8, sy=0.4, speed=1.4,
                       base=0.1, cut=0.35, power=1.2, stops=TIDE, sway=1.0)
    if key == 'density':
        return tongues(head, solid, 0.4 + 0.9 * f, up=1.0, down=1.0, seed=81, sx=0.7, sy=0.7, speed=0.4, base=0.0,
                       cut=0.42, power=1.0, stops=GRAVITY)
    if key == 'smite':
        return tongues(head, solid, 0.5 + 1.2 * f, up=0.35, down=3.0, seed=21, sx=0.65, sy=0.3, speed=0.5, base=0.0,
                       cut=0.42, power=1.2, stops=HOLY, warp=0.2)
    if key == 'bane':
        return tongues(head, solid, 0.4 + 1.0 * f, up=0.5, down=1.6, seed=33, sx=0.45, sy=0.32, speed=0.5, base=0.0,
                       cut=0.45, power=1.0, stops=VEN, sway=0.8, warp=0.6)
    if key in ('sharp', 'piercing'):
        return tongues(head, solid, 0.9 if key == 'sharp' and lv >= 5 else 0.6, up=0.5, down=1.2, seed=61, sx=0.9, sy=0.35,
                       speed=0.7, base=0.0, cut=0.45, power=1.2, stops=BLOODLUST, warp=0.3)
    if key == 'channeling':
        return tongues(far_first(r)[:10], solid, 0.6, up=0.7, down=1.0, seed=111, sx=1.4, sy=0.9, speed=2.5, base=0.0,
                       cut=0.5, power=1.0, stops=SPARK)
    if key == 'loyalty':
        return tongues(r['grip'] + head, solid, 0.3 + 0.5 * f, up=0.8, down=0.8, seed=91, sx=0.5, sy=0.4, speed=0.4,
                       base=0.1, cut=0.4, power=1.0, stops=LOYAL)
    if key == 'power':
        return tongues(r['string'] or head, solid, 0.3 + 0.6 * f, up=0.7, down=0.7, seed=101, sx=0.8, sy=0.5, speed=0.8,
                       base=0.0, cut=0.4, power=1.2, stops=POWER)
    raise ValueError(key)


# which enchantments have a big aura, from which level, in priority order (the first one present is the one shown)
MAIN = {
    'sword': [('fire', 1), ('smite', 2), ('bane', 2), ('sharp', 4)],
    'generic': [('fire', 1), ('flame', 1), ('riptide', 1), ('density', 2), ('smite', 2), ('bane', 2), ('sharp', 4),
                ('piercing', 3), ('channeling', 1), ('loyalty', 2), ('power', 3)],
}


def touch(kind, ref, r, key, lv):
    """A small extra on its own plane: sparks off a pick, coins, gusts, petals… (blank when this one has none)."""
    frames = blank()
    head = r['head'] or r['grip']
    any_ = False
    rng = np.random.default_rng(12)
    far = far_first(r)[:6] or head[:6]
    for fi, a in enumerate(frames):
        ph = fi / A.N
        if kind == 'sword':
            if key == 'sharp' and lv >= 3:  # drops gathering at the guard and falling
                any_ = True
                for k, (x0, y0) in enumerate(((7.5, 10.2), (9.4, 13.6))[:1 if lv < 5 else 2]):
                    q = (ph + k * 0.5) % 1
                    if q < 0.5:
                        A.put(a, x0, y0, (170, 20, 26, 240))
                    elif q < 0.85:
                        t = (q - 0.5) / 0.35
                        A.put(a, x0, y0 + 0.4 + t * t * 6, (190, 30, 34, 245))
                        A.put(a, x0, y0 - 0.1 + t * t * 6, (120, 14, 20, 200))
            if key == 'knock' and lv >= 2:  # gusts circling the hilt
                any_ = True
                for k in range(2):
                    ang = ph * 6.283 + k * 3.1416
                    for j in range(9):
                        t = ang - j * 0.16
                        A.put(a, 3.0 + 3.6 * math.cos(t), 11.8 + 1.8 * math.sin(t), (226, 240, 255, 241 - j * 18))
            if key == 'loot' and lv >= 3:  # a coin glint now and then
                any_ = True
                for k, (x0, y0) in enumerate(((1.4, 5.2), (10.6, 14.4), (4.0, 3.4))):
                    q = (ph * 1.5 + k * 0.33) % 1
                    if q < 0.22:
                        A.star(a, x0, y0, 2 if 0.06 < q < 0.16 else 1, (255, 226, 110, 245))
            if key == 'sweep' and lv >= 3:  # a crescent gleam sweeps past the tip once per loop
                any_ = True
                if ph < 0.3:
                    u, v = A.grid()
                    ang = np.arctan2(v - 8, u - 8)
                    rad = np.hypot(u - 8, v - 8)
                    lead = -2.4 + ph / 0.3 * 2.2
                    m = (abs(rad - 10.4) < 0.35) & (ang > lead - 0.5) & (ang < lead) & ~A.inside(A.whole_pixels(ref))
                    a[m] = (236, 248, 255, 225)
            continue
        if key == 'eff' and lv >= 2:  # sparks flying off the working edge (a pick striking stone), more per level
            any_ = True
            for j in range(2 * (lv - 1)):
                x0, y0 = far[j % len(far)]
                t = (ph * 1.6 + j * 0.19) % 1
                if t < 0.55:
                    ang = -0.6 + j * 1.1
                    d = 0.6 + t * 6
                    sx, sy = x0 + 0.5 + math.cos(ang) * d, y0 + 0.5 + math.sin(ang) * d + t * t * 4
                    col = (255, 244, 190, 241) if t < 0.25 else (255, 170, 60, 209)
                    line(a, sx, sy, sx - math.cos(ang) * 0.9, sy - math.sin(ang) * 0.9 - t * 0.8, col)
            if lv >= 5:  # the far ends of the head flash
                for j, (x0, y0) in enumerate(far[:2]):
                    t = (ph * 2 + j * 0.5) % 1
                    if t < 0.2:
                        A.star(a, x0 + 0.5, y0 + 0.5, 2 if 0.05 < t < 0.15 else 1, (255, 250, 222, 241))
        if key == 'lunge':  # whoosh lines either side of the shaft, running back from the tip (one more per level)
            any_ = True
            for j in range(lv + 1):
                t = (ph * 2 + j * 0.37) % 1
                side = 1.6 if j % 2 == 0 else -1.6
                x0, y0 = 11 - t * 7 + side * 0.7, 5 + t * 7 + side * 0.7
                line(a, x0, y0, x0 - 2.2, y0 + 2.2, (236, 248, 255, 225))
        if key == 'quick' and lv >= 3:  # speed lines trailing behind the mechanism
            any_ = True
            for j in range(2 + lv // 2):
                x0, y0 = far[j % len(far)]
                t = (ph * 2 + j * 0.31) % 1
                if t < 0.5:
                    dx = 1.5 + 3 * t
                    line(a, x0 + 0.5 - dx, y0 + 0.5 + dx, x0 + 0.5 - dx - 1.6, y0 + 0.5 + dx + 1.6, (255, 248, 214, 220))
        if key in ('fortune', 'loot', 'luck') and lv >= 3:  # a coin glint now and then
            any_ = True
            for j, (x0, y0) in enumerate(((head[0][0] - 2.5, head[0][1] - 1.5), (head[-1][0] + 2, head[-1][1] + 2.5))):
                t = (ph * 1.5 + j * 0.5) % 1
                if t < 0.22:
                    A.star(a, x0, y0, 2 if 0.06 < t < 0.16 else 1, (255, 226, 110, 241))
        if key == 'silk':  # petals drifting down
            any_ = True
            for j in range(3):
                x0 = 4 + j * 4.5
                t = (ph + j * 0.33) % 1
                A.put(a, x0 + math.sin(t * 6.28 + j) * 1.2, 2 + t * 14, (255, 214, 236, 225))
                A.put(a, x0 + 0.5 + math.sin(t * 6.28 + j) * 1.2, 2 + t * 14, (240, 170, 210, 193))
        if key in ('knock', 'punch', 'windburst') and lv >= 2:  # gusts circling the grip
            any_ = True
            grip = r['grip'] or head
            cx, cy = (grip[len(grip) // 2][0] + 0.5, grip[len(grip) // 2][1] + 0.5) if kind != 'mace' else (11, 4)
            rx, ry = (3.6, 1.8) if kind != 'mace' else (6.0, 3.0)
            n = 2 if lv < 3 else 3
            for j in range(n):
                ang = ph * 6.283 * (1.5 if kind == 'mace' else 1) + j * 6.283 / n
                for q in range(11):
                    t = ang - q * 0.14
                    A.put(a, cx + rx * math.cos(t), cy + ry * math.sin(t), (226, 240, 255, 241 - q * 16))
        if key == 'infinity':  # stars twinkling round it
            any_ = True
            for j in range(5):
                x0, y0 = rng.uniform(-2, 18), rng.uniform(-2, 18)
                t = (ph * 2 + j * 0.2) % 1
                if t < 0.25:
                    A.star(a, x0, y0, 2 if 0.08 < t < 0.17 else 1, (236, 220, 255, 241))
        if key == 'channeling' and fi % 2 == 0:  # sparks jumping off the prongs
            any_ = True
            for j in range(4):
                x0, y0 = far[(fi // 3 + j) % len(far)]
                pts = [(x0 + 0.5, y0 + 0.5)]
                for _ in range(3):
                    pts.append((pts[-1][0] + rng.uniform(-0.2, 1.4), pts[-1][1] + rng.uniform(-1.4, 0.2)))
                for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
                    line(a, xa, ya, xb, yb, (220, 240, 255, 241), fade=False)
        if key in ('impaling', 'lure') and lv >= 2:  # bubbles rising
            any_ = True
            src = far if key == 'impaling' else (r['end'] or far)
            for j in range(1 + lv // 2):
                x0, y0 = src[j % len(src)]
                t = (ph + j * 0.37) % 1
                bx, by = x0 + 0.5 + math.sin(t * 9 + j) * 0.6, y0 - t * 6
                for dx, dy in ((0.5, 0), (-0.5, 0), (0, 0.5), (0, -0.5)):
                    A.put(a, bx + dx, by + dy, (200, 245, 255, 209))
        if key == 'breach' and lv >= 2:  # crumbs falling off the head
            any_ = True
            for j in range(lv):
                x0, y0 = head[(j * 7) % len(head)]
                t = (ph + j * 0.29) % 1
                A.put(a, x0 + 0.5, y0 + 1 + t * t * 8, (150, 140, 150, 225) if j % 2 else (255, 150, 60, 225))
        if key == 'multishot':  # two ghost bolts beside it
            any_ = True
            for j, off in enumerate((-2.2, 2.2)):
                t = 0.6 + 0.4 * math.sin(ph * 6.283 + j * 3.1)
                line(a, 3 + off, 13 + off, 13 + off, 3 + off, (150, 240, 255, int(120 + 80 * t)))
        if key == 'density' and lv >= 3:  # motes drawn in towards the head
            any_ = True
            cx, cy = (sum(p[0] for p in head) / len(head) + 0.5, sum(p[1] for p in head) / len(head) + 0.5)
            for j in range(4):
                ang = j * 1.57 + 0.4
                t = (ph * 1.5 + j * 0.25) % 1
                rr = 6 * (1 - t)
                A.put(a, cx + rr * math.cos(ang), cy + rr * math.sin(ang), (200, 160, 255, 225))
    return frames if any_ else None


# ------------------------------------------------------------------------------------------------ layering
def touch_levels(kind, key):
    """The levels from which a touch looks different (a new one from each of these levels up)."""
    top = ENCH[key][1]
    if kind == 'sword':
        return {'sharp': [3, 5], 'knock': [2], 'loot': [3], 'sweep': [3]}.get(key, [])
    return {'eff': [2, 3, 4, 5], 'lunge': [1, 2, 3], 'quick': [3], 'fortune': [3], 'loot': [3], 'luck': [3], 'silk': [1],
            'knock': [2], 'punch': [2], 'windburst': [2, 3], 'infinity': [1], 'channeling': [1], 'impaling': [2, 4],
            'lure': [2], 'breach': [2, 3, 4], 'multishot': [1], 'density': [3]}.get(key, [l for l in [] if l <= top])


def contexts(kind, key):
    """What else changes how one enchantment's texture part looks, as (label, enchantments that make it so), checked
    in order: the first one present wins. Empty: nothing does."""
    if key in ('fire', 'flame'):
        dm = [k for k in DAMAGE if k in KINDS[kind][2]] if kind != 'sword' else ['sharp', 'smite', 'bane']
        return [(k, [k]) for k in dm] + [(None, [])]
    if kind == 'sword' and key == 'loot':
        return [('smite', ['smite']), (None, [])]
    if key in ('knock', 'punch', 'windburst') and kind != 'bow':
        lk = [k for k in ('loot', 'fortune') if k in KINDS[kind][2]]
        return [('loot', lk), (None, [])] if lk else []
    if key == 'luck':
        return [('lure', ['lure']), (None, [])]
    return []


def level_steps(key):
    return list(range(1, ENCH[key][1] + 1))


def overlay(kind, img, ref, key, lv, ctx_label, ctx_keys):
    """The pixels one enchantment at one level paints (given what else is there), as an image of its own: colour as
    painted, alpha its glow code or 255. Diffed against the item without it, in every case of its context."""
    over = Image.new('RGBA', img.size, (0, 0, 0, 0))
    cases = [{}]
    if ctx_label is not None:
        cases = [{k: l} for k in ctx_keys for l in level_steps(k)]
    for e0 in cases:
        e1 = dict(e0)
        e1[key] = lv
        a, ga = paint(kind, img, ref, e0)
        b, gb = paint(kind, img, ref, e1)
        w, h = img.size
        for y in range(h):
            for x in range(w):
                p, q = b.getpixel((x, y)), a.getpixel((x, y))
                if p != q or ga.get((x, y)) != gb.get((x, y)):
                    over.putpixel((x, y), p[:3] + (CODE[gb[(x, y)]] if (x, y) in gb else 255,))
    return over if over.getbbox() else None


def cond(key, lv, on_true, on_false):
    return {'type': 'minecraft:condition', 'property': 'minecraft:component', 'predicate': 'minecraft:enchantments',
            'value': [{'enchantments': f'minecraft:{ENCH[key][0]}', 'levels': {'min': lv}}],
            'on_true': on_true, 'on_false': on_false}


def cond_any(keys, on_true, on_false):
    if not keys:
        return on_false
    return {'type': 'minecraft:condition', 'property': 'minecraft:component', 'predicate': 'minecraft:enchantments',
            'value': [{'enchantments': [f'minecraft:{ENCH[k][0]}' for k in keys]}], 'on_true': on_true, 'on_false': on_false}


EMPTY = {'type': 'minecraft:empty'}


def model_ref(mid, tint=True):
    m = {'type': 'minecraft:model', 'model': mid}
    if tint:
        m['tints'] = [{'type': 'minecraft:constant', 'value': NO_GLINT}]
    return m


def by_level(key, levels, pick):
    """A chain from the highest level down: the model for the highest level the item has (pick(level) -> node)."""
    node = EMPTY
    for lv in levels:
        node = cond(key, lv, pick(lv), node)
    return node


class Writer:
    """Writes textures and models under assets/libreria and remembers which effect family each model belongs to."""

    def __init__(self, assets):
        self.assets = assets
        self.family_models = {}

    def tex(self, rel, img, frames=False):
        path = os.path.join(self.assets, NS, 'textures/item', rel + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        save_png(img, path)
        if frames:
            with open(path + '.mcmeta', 'w') as f:
                json.dump({'animation': {'frametime': 1, 'interpolate': False}}, f, separators=(',', ':'))
        return f'{NS}:item/{rel}'

    def model(self, rel, data, family):
        path = os.path.join(self.assets, NS, 'models/item', rel + '.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            json.dump(data, f, separators=(',', ':'))
        self.family_models.setdefault(family, []).append(f'assets/{NS}/models/item/{rel}.json')
        return f'{NS}:item/{rel}'


def save_png(img, path):
    """As a palette PNG when that's exactly the same picture (these have a handful of colours): about half the size."""
    n = img.getcolors(1 << 16)
    if n is not None and len(n) <= 256:
        q = img.quantize(len(n), method=Image.Quantize.FASTOCTREE)
        if np.array_equal(np.array(q.convert('RGBA')), np.array(img.convert('RGBA'))):
            q.save(path, optimize=True)
            return
    img.save(path, optimize=True)


def strip(frames):
    """The aura's frames as one tall texture; alpha carries the mark 1 (mod 16): lit by itself, no glint."""
    out = Image.new('RGBA', (A.SIZE, A.SIZE * len(frames)), (0, 0, 0, 0))
    for k, f in enumerate(frames):
        f = f.copy()
        a = f[..., 3].astype(int)
        f[..., 3] = np.where(a > 0, np.clip(16 * np.round((a - 1) / 16) + 1, 17, 241), 0).astype(np.uint8)
        out.paste(Image.fromarray(f, 'RGBA'), (0, k * A.SIZE))
    return out


def split(over):
    """An overlay as (its plain pixels, its glowing ones): either None when empty."""
    lit = Image.new('RGBA', over.size, (0, 0, 0, 0))
    glows = Image.new('RGBA', over.size, (0, 0, 0, 0))
    w, h = over.size
    for y in range(h):
        for x in range(w):
            p = over.getpixel((x, y))
            if p[3] == 255:
                lit.putpixel((x, y), p)
            elif p[3]:
                glows.putpixel((x, y), p)
    return (lit if lit.getbbox() else None), (glows if glows.getbbox() else None)


def refs(models):
    """One model, or several drawn together."""
    if len(models) == 1:
        return model_ref(models[0])
    return {'type': 'minecraft:composite', 'models': [model_ref(m) for m in models]}


def glow_plane(texture, display):
    """The glowing pixels of a texture part on the item's front and back (the same planes as the item's own faces,
    drawn after it): lit by themselves, also where a shader pack draws the items instead of our item shader."""
    face = lambda uv: {'uv': uv, 'texture': '#a', 'tintindex': 0}
    return {'textures': {'a': texture, 'particle': texture},
            'elements': [{'from': [0, 0, 7.5], 'to': [16, 16, 8.5], 'shade': False, 'light_emission': 15,
                          'faces': {'south': face([0, 0, 16, 16]), 'north': face([16, 0, 0, 16])}}],
            'display': display}


def plane(texture, display):
    lo, hi = A.LO, A.HI
    face = lambda uv: {'uv': uv, 'texture': '#a'}
    return {'textures': {'a': texture, 'particle': texture},
            'elements': [{'from': [lo, 16 - hi, 8], 'to': [hi, 16 - lo, 8], 'shade': False, 'light_emission': 15,
                          'faces': {'south': face([0, 0, 16, 16]), 'north': face([16, 0, 0, 16])}}],
            'display': display}


class Kind:
    """Everything of one kind of item (sword, pickaxe, bow…): its layers per sprite and material."""

    def __init__(self, name, vanilla, writer, display_of):
        self.name = name
        self.sprites, self.mats, self.enchants = KINDS[name]
        self.tex_dir = os.path.join(vanilla, 'textures/item')
        self.w = writer
        self.display_of = display_of
        self.textures = {}  # (sprite, material) -> list of texture part nodes
        self.auras = {}  # sprite -> list of aura layer nodes (built once per sprite: auras are the same for every material)

    def sprite(self, sprite, mat):
        return Image.open(os.path.join(self.tex_dir, self.sprites[sprite].format(m=mat) + '.png')).convert('RGBA')

    def ref(self, sprite):
        return self.sprite(sprite, 'diamond' if self.mats[0] else None)

    def texture_layers(self, sprite, mat, parent):
        """The texture parts of one sprite in one material: a node per enchantment, in the order they are painted."""
        if (sprite, mat) not in self.textures:
            self.textures[(sprite, mat)] = self._texture_layers(sprite, mat, parent)
        return self.textures[(sprite, mat)]

    def _texture_layers(self, sprite, mat, parent):
        img, ref = self.sprite(sprite, mat), self.ref(sprite)
        order = [k for k in PAINT_ORDER[self.name == 'sword'] if k in self.enchants]
        nodes = []
        tag = mat or 'x'
        for key in order:
            ctx = contexts(self.name, key) or [(None, [])]
            variants = {}
            for label, keys in ctx:
                for lv in level_steps(key):
                    over = overlay(self.name, img, ref, key, lv, label, keys)
                    if over is None:
                        continue
                    rel = f'fx/{self.name}/{tag}/{sprite}/{key}{lv}' + (f'_{label}' if label else '')
                    # two parts: what's only painted (a layer like the item's own, lit like it), and what glows (a
                    # plane that lights itself, so it glows with shaders too, where our item shader isn't used)
                    lit, glows = split(over)
                    parts = []
                    if lit is not None:
                        t = self.w.tex(rel, lit)
                        parts.append(self.w.model(rel, {'parent': parent, 'textures': {'layer0': t}}, FAMILY[key]))
                    if glows is not None:
                        t = self.w.tex(rel + '_glow', glows)
                        parts.append(self.w.model(rel + '_glow', glow_plane(t, self.display_of(sprite)), FAMILY[key]))
                    variants[(label, lv)] = parts
            if not variants:
                continue

            def for_label(label):
                return by_level(key, level_steps(key), lambda lv: refs(variants[(label, lv)]) if (label, lv) in variants else EMPTY)

            node = for_label(None) if (None, 1) in variants or any(k[0] is None for k in variants) else EMPTY
            for label, keys in reversed(ctx):
                if label is None:
                    continue
                node = cond_any(keys, for_label(label), node)
            nodes.append(node)
        return nodes

    def aura_layers(self, sprite):
        """The auras of one sprite: the main one by priority, then the touches. Shared by every material."""
        if sprite in self.auras:
            return self.auras[sprite]
        nodes = []
        if sprite in BIG:  # drawn bigger than the plane is made for: texture parts only
            self.auras[sprite] = nodes
            return nodes
        ref = self.ref(sprite)
        r = regions(self.name, ref)
        display = self.display_of(sprite)
        style = 'sword' if self.name == 'sword' else 'generic'
        mains = [(k, lv0) for k, lv0 in MAIN[style] if k in self.enchants]
        node = EMPTY
        for key, lv0 in reversed(mains):
            dmgs = [None]
            if key in ('fire', 'flame'):
                dmgs = [c[0] for c in contexts(self.name, key)]
            per = {}
            for dmg in dmgs:
                for lv in [l for l in level_steps(key) if l >= lv0]:
                    frames = main_aura(self.name, ref, r, key, lv, dmg)
                    rel = f'fx/{self.name}/aura/{sprite}/{key}{lv}' + (f'_{dmg}' if dmg else '')
                    t = self.w.tex(rel, strip(frames), frames=True)
                    per[(dmg, lv)] = self.w.model(rel, plane(t, display), FAMILY[key])
            levels = [l for l in level_steps(key) if l >= lv0]

            def chain(dmg):
                return by_level(key, levels, lambda lv: model_ref(per[(dmg, lv)], tint=False))

            inner = chain(None)
            if key in ('fire', 'flame'):
                for label, keys in reversed(contexts(self.name, key)):
                    if label is not None:
                        inner = cond_any(keys, chain(label), inner)
            node = cond(key, lv0, inner, node)
        if node is not EMPTY:
            nodes.append(node)
        for key in self.enchants:
            steps = touch_levels(self.name, key)
            per = {}
            for lv in steps:
                frames = touch(self.name, ref, r, key, lv)
                if frames is None:
                    continue
                rel = f'fx/{self.name}/aura/{sprite}/{key}{lv}_touch'
                t = self.w.tex(rel, strip(frames), frames=True)
                per[lv] = self.w.model(rel, plane(t, display), FAMILY[key])
            if per:
                nodes.append(by_level(key, sorted(per), lambda lv: model_ref(per[lv], tint=False)))
        self.auras[sprite] = nodes
        return nodes

    def leaf(self, sprite, mat, base_model, parent):
        """What a vanilla model of this item becomes: the item (no glint once it has an effect), its texture parts, and
        out of the inventory its auras."""
        tex = self.texture_layers(sprite, mat, parent)
        auras = self.aura_layers(sprite)
        loud = [k for k in self.enchants if k not in QUIET]
        base = cond_any(loud, model_ref(base_model), model_ref(base_model, tint=False))
        models = [base] + tex
        if auras:
            models.append({'type': 'minecraft:select', 'property': 'minecraft:display_context',
                           'cases': [{'when': 'gui', 'model': EMPTY}],
                           'fallback': {'type': 'minecraft:composite', 'models': auras}})
        return {'type': 'minecraft:composite', 'models': models}


# the order texture parts are painted in (later ones on top)
PAINT_ORDER = {
    True: ['unbreak', 'mend', 'vanish', 'sharp', 'smite', 'bane', 'fire', 'sweep', 'loot', 'knock'],
    False: ['unbreak', 'mend', 'vanish', 'sharp', 'piercing', 'smite', 'bane', 'density', 'breach', 'fire', 'flame', 'eff', 'quick', 'multishot',
            'impaling', 'channeling', 'loyalty', 'riptide', 'infinity', 'power', 'lure', 'luck', 'silk', 'windburst',
            'knock', 'punch', 'lunge', 'fortune', 'loot'],
}
