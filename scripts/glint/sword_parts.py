"""Sword effects that combine. Every enchantment owns a part of the sword, so they stack without fighting:

    blade body   Sharpness (blood from the tip), Smite (gold inlay in the fuller), Bane (venom on the lower edge)
    blade core   Fire Aspect (the metal glows hot from the guard up)
    cutting edge Sweeping Edge (a honed, bright edge)
    guard        Looting (gold guard, amethyst gems)
    handle       Knockback (wind-wrapped grip, a swirl in the pommel)

The aura is one aura: Fire Aspect's flames take the colour of the damage enchantment (blood fire, holy fire, venom
fire); without fire the damage enchantment has its own aura. Knockback, Looting and Sweeping add small touches to it.
"""
import math

import numpy as np

import sword_aura as A

BLOOD = [(34, 2, 6), (58, 4, 10), (88, 8, 14), (120, 12, 18), (150, 18, 22), (178, 28, 30), (214, 60, 54)]
HEAT = [(64, 16, 8), (112, 26, 10), (164, 46, 12), (212, 82, 18), (246, 132, 30), (255, 184, 64), (255, 238, 176)]
GOLD = [(74, 46, 8), (112, 74, 12), (152, 106, 20), (198, 146, 34), (232, 188, 62), (250, 216, 98), (255, 246, 196)]
VENOM = [(12, 42, 10), (24, 72, 16), (40, 112, 24), (70, 152, 36), (110, 198, 56), (152, 228, 82), (212, 255, 152)]
HEATS = {None: HEAT,
         'sharp': [(54, 6, 8), (96, 12, 12), (150, 24, 18), (200, 48, 26), (236, 90, 44), (250, 150, 90), (255, 220, 190)],
         'smite': [(96, 60, 10), (140, 94, 18), (190, 136, 30), (232, 182, 54), (252, 214, 96), (255, 238, 160), (255, 254, 236)],
         'bane': [(16, 56, 12), (34, 92, 18), (60, 140, 28), (100, 190, 40), (150, 228, 64), (196, 248, 120), (240, 255, 200)]}
WIND = [(52, 66, 84), (84, 102, 124), (122, 142, 164), (162, 182, 202), (196, 214, 230), (224, 236, 246), (248, 252, 255)]
AMETHYST = (198, 120, 255)

WINGS = A.GUARD - {(5, 9), (6, 9), (5, 10), (6, 10)}
HILT_GEM = [(6, 9), (5, 10)]


def rank(img):
    """Each colour of the sword's palette by brightness, 0 (darkest) .. 6 (brightest), so a recolour keeps the shading."""
    cols = sorted({img.getpixel((x, y))[:3] for y in range(16) for x in range(16) if img.getpixel((x, y))[3]},
                  key=lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return {c: round(k * 6 / max(1, len(cols) - 1)) for k, c in enumerate(cols)}


def blade(x, y):
    """Along the blade (s: hilt -2 .. tip 15) and across it (c: 13 upper outline, 14 bright edge, 15 core, 16-17 lower)."""
    return x - y, x + y


def build(img, e):
    """e: {'sharp'|'smite'|'bane': level, 'fire': 1-2, 'loot': 1-3, 'knock': 1-2, 'sweep': 1-3}.
    Returns (sprite 16x16, glow {pixel: mode}, aura frames)."""
    out = img.copy()
    px = out.load()
    rk = rank(img)
    glow = {}
    blade_px = set(A.blade_pixels(img))

    def recolour(p, ramp, shift=0):
        r, g, b, a = px[p]
        orig = img.getpixel(p)[:3]
        px[p] = ramp[max(0, min(6, rk.get(orig, 3) + shift))] + (255,)

    if 'sharp' in e:
        lv = e['sharp']
        tip = {1: 99, 2: 12, 3: 9, 4: 6, 5: 3}[lv]
        drips = {1: {}, 2: {14: 2}, 3: {14: 3, 16: 2}, 4: {14: 4, 16: 3, 13: 1}, 5: {14: 7, 16: 4, 15: 2, 13: 2}}[lv]
        splat = {(15, 0), (14, 1), (13, 2), (15, 1)} if lv == 1 else set()
        for p in blade_px:
            s, c = blade(*p)
            if s >= tip - drips.get(c, 0) or p in splat:
                recolour(p, BLOOD)
        if lv >= 3:  # the drip ends shine wet
            for c, d in drips.items():
                ends = [p for p in blade_px if blade(*p)[1] == c and blade(*p)[0] == tip - d]
                for p in ends:
                    px[p] = BLOOD[6] + (255,)
                    glow[p] = 'pulse'
        if lv >= 5:
            for p in HILT_GEM:
                recolour(p, BLOOD, 1)
    if 'smite' in e:
        lv = e['smite']
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
        lv = e['bane']
        start = {1: 11, 2: 8, 3: 3, 4: -2, 5: -2}[lv]
        for p in blade_px:
            s, c = blade(*p)
            if c >= 16 and s >= start:
                recolour(p, VENOM, 1)
                glow[p] = 'pulse' if c == 16 else glow.get(p)
            if lv >= 4 and c == 15 and s % 3 == 1 and s >= start:
                recolour(p, VENOM, 1)
        for p in [q for q in glow if glow[q] is None]:
            del glow[p]
        if lv >= 4:  # drips hanging under the lower edge
            for x, y in ((11, 5), (13, 3), (9, 7))[:1 if lv == 4 else 3]:
                if not img.getpixel((x, y + 1))[3]:
                    px[x, y + 1] = VENOM[5] + (255,)
                    glow[(x, y + 1)] = 'pulse'
    if 'fire' in e:
        lv = e['fire']
        HEAT_ = HEATS[damage(e)[0]]
        top = {1: 5, 2: 11}[lv]
        wide = {1: 1, 2: 6}[lv]
        for p in blade_px:
            s, c = blade(*p)
            band = (14, 16) if s <= wide else (15, 15)
            if band[0] <= c <= band[1] and s <= top:
                heat = 6 - int(5 * max(0, s + 2) / (top + 3))
                px[p] = HEAT_[max(1, min(6, heat if c == 15 else heat - 2))] + (255,)
                glow[p] = 'flicker'
            if lv >= 2 and c in (13, 17) and s in (1, 4, 8):  # glowing cracks at the edges
                px[p] = HEAT_[4] + (255,)
                glow[p] = 'flicker'
        if 'smite' not in e and 'loot' not in e:
            for p in HILT_GEM:
                px[p] = HEAT_[5] + (255,)
                glow[p] = 'flicker'
    if 'sweep' in e:
        lv = e['sweep']
        # chevrons pointing at the tip (a swoosh), and from II a honed edge
        for s0 in (12, 8, 4)[:lv]:
            for s, c in ((s0, 14), (s0 + 1, 15), (s0, 16)):
                p = ((s + c) // 2, (c - s) // 2)
                if p in blade_px:
                    px[p] = (236, 250, 255, 255)
                    glow[p] = 'flow'

    if 'loot' in e:
        lv = e['loot']
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
        lv = e['knock']
        for k, p in enumerate(sorted(A.HANDLE, key=lambda q: q[0] - q[1])):
            recolour(p, WIND, 1 if k % 2 == 0 else -1)
        if lv >= 2:
            for p in A.POMMEL:
                recolour(p, WIND)
            px[1, 14] = (200, 240, 255, 255)
            glow[(1, 14)] = 'pulse'
    return out, glow, aura(img, e)


# --- the aura ----------------------------------------------------------------------------------------------------
FLAMES = {
    None: [(0.72, (255, 244, 190, 245)), (0.48, (255, 200, 60, 245)), (0.24, (250, 118, 24, 235)), (0.0, (200, 46, 16, 180))],
    'sharp': [(0.72, (255, 214, 190, 245)), (0.48, (240, 92, 60, 240)), (0.24, (186, 24, 30, 230)), (0.0, (96, 8, 18, 190))],
    'smite': [(0.72, (255, 255, 240, 245)), (0.48, (255, 232, 120, 240)), (0.24, (252, 186, 52, 230)), (0.0, (210, 124, 30, 180))],
    'bane': [(0.72, (240, 255, 196, 245)), (0.48, (176, 242, 72, 240)), (0.24, (84, 194, 40, 230)), (0.0, (32, 112, 30, 180))],
}
BLOODLUST = [(0.6, (230, 70, 60, 235)), (0.3, (160, 18, 26, 215)), (0.0, (90, 8, 16, 170))]


def damage(e):
    for k in ('sharp', 'smite', 'bane'):
        if k in e:
            return k, e[k]
    return None, 0


def aura(img, e):
    kind, lv = damage(e)
    blank = [np.zeros((A.SIZE, A.SIZE, 4), np.uint8) for _ in range(A.N)]
    if 'fire' in e:
        reach = {1: 1.1, 2: 2.0}[e['fire']] + 0.08 * lv
        frames = A.tongues(img, reach, up=0.42, down=2.6, seed=3, sx=0.55, sy=0.42, speed=1, base=0.12, cut=0.32,
                           power=1.6, stops=FLAMES[kind])
        embers = {None: (255, 190, 70), 'sharp': (240, 80, 60), 'smite': (255, 240, 170), 'bane': (180, 250, 90)}[kind]
        if e['fire'] >= 2:
            r_ = np.random.default_rng(5)
            spots = [(r_.uniform(6, 16), r_.uniform(-1, 7)) for _ in range(7)]
            for f, a in enumerate(frames):
                for k, (x0, y0) in enumerate(spots):
                    ph = (f / A.N + k / 7) % 1
                    if ph < 0.85:
                        A.put(a, x0 + 0.6 * math.sin(ph * 6.28 + k), y0 - ph * 6, embers + (245 if ph < 0.5 else 200,))
    elif kind == 'smite' and lv >= 2:
        frames = A.holy(img, lv)
    elif kind == 'bane' and lv >= 2:
        frames = A.venom(img, lv)
    elif kind == 'sharp' and lv >= 4:
        reach = {4: 0.6, 5: 1.0}[lv]
        frames = A.tongues(img, reach, up=0.5, down=1.2, seed=61, sx=0.9, sy=0.35, speed=0.7, base=0.0, cut=0.45,
                           power=1.2, stops=BLOODLUST, warp=0.3)
    else:
        frames = blank
    frames = [f.copy() for f in frames]
    if kind == 'sharp' and lv >= 3:  # drops gathering at the guard and falling
        for f, a in enumerate(frames):
            for k, (x0, y0) in enumerate(((7.5, 10.2), (9.4, 13.6))[:1 if lv < 5 else 2]):
                ph = (f / A.N + k * 0.5) % 1
                if ph < 0.5:
                    A.put(a, x0, y0, (170, 20, 26, 240))
                elif ph < 0.85:
                    t = (ph - 0.5) / 0.35
                    A.put(a, x0, y0 + 0.4 + t * t * 6, (190, 30, 34, 245))
                    A.put(a, x0, y0 - 0.1 + t * t * 6, (120, 14, 20, 200))
    if e.get('knock', 0) >= 2:  # gusts circling the hilt
        for f, a in enumerate(frames):
            for k in range(2):
                ang = f / A.N * 6.283 + k * 3.1416
                for j in range(9):
                    t = ang - j * 0.16
                    A.put(a, 3.0 + 3.6 * math.cos(t), 11.8 + 1.8 * math.sin(t), (226, 240, 255, 241 - j * 18))
    if e.get('loot', 0) >= 3:  # a coin glint now and then
        for f, a in enumerate(frames):
            for k, (x0, y0) in enumerate(((1.4, 5.2), (10.6, 14.4), (4.0, 3.4))):
                ph = (f / A.N * 1.5 + k * 0.33) % 1
                if ph < 0.22:
                    A.star(a, x0, y0, 2 if 0.06 < ph < 0.16 else 1, (255, 226, 110, 245))
    if e.get('sweep', 0) >= 3:  # a crescent gleam sweeps past the tip once per loop
        u, v = A.grid()
        for f, a in enumerate(frames):
            ph = f / A.N
            if ph < 0.3:
                ang = np.arctan2(v - 8, u - 8)
                rad = np.hypot(u - 8, v - 8)
                lead = -2.4 + ph / 0.3 * 2.2
                m = (abs(rad - 10.4) < 0.35) & (ang > lead - 0.5) & (ang < lead) & ~A.inside(A.whole_pixels(img))
                a[m] = (236, 248, 255, 225)
    return frames
