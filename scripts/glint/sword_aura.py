"""Sword effects in two parts: a texture part painted into the sword (16 px, the sword stays recognisable) and an aura
around it (finer: 2 texels per sword pixel, animated, its width changing per piece like a real flame or aura).

The aura lives on a flat plane behind the sword that is larger than the item (units -4..20), so flames can rise past
the tip. Frames loop seamlessly (noise that scrolls by exactly one period per loop).
"""
import math
import os

import numpy as np
from PIL import Image

REF = '/home/claude/ref/assets/assets/minecraft/textures/item'
N = 24          # frames per loop (1 tick each in game: 1.2 s)
RES = 2         # aura texels per sword pixel
LO, HI = -4, 20  # aura plane in model units
SIZE = (HI - LO) * RES

GUARD = {(2, 6), (3, 6), (2, 7), (3, 7), (4, 7), (3, 8), (4, 8), (5, 8), (3, 9), (4, 9), (5, 9), (6, 9), (4, 10), (5, 10),
         (6, 10), (7, 10), (5, 11), (6, 11), (7, 11), (8, 11), (6, 12), (7, 12), (8, 12), (9, 12), (8, 13), (9, 13)}
GEMS = [(3, 7), (6, 9), (5, 10)]
HANDLE = {(3, 11), (4, 11), (2, 12), (3, 12), (4, 12), (2, 13), (3, 13)}
POMMEL = {(0, 13), (1, 13), (0, 14), (1, 14), (2, 14), (0, 15), (1, 15), (2, 15)}
CORE = [(7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 3), (13, 2)]           # the line down the middle, hilt -> tip
EDGE = [(9, 5), (10, 4), (11, 3), (12, 2), (13, 1), (14, 1), (14, 2), (13, 3)]  # the bright cutting edge, hilt -> tip


def sword(material='diamond'):
    return Image.open(f'{REF}/{material}_sword.png').convert('RGBA')


def blade_pixels(img):
    out = []
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] and (x, y) not in GUARD | HANDLE | POMMEL:
                out.append((x, y))
    return out


def whole_pixels(img):
    return [(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y))[3]]


# --- noise -------------------------------------------------------------------------------------------------------
class Noise:
    """Value noise, periodic in y with period P lattice cells, so scrolling by P loops seamlessly."""

    def __init__(self, seed, period=8, w=64):
        r = np.random.default_rng(seed)
        self.g = r.random((period, w))
        self.p, self.w = period, w

    def __call__(self, x, y):
        x = np.asarray(x, float) + 0.0
        y = np.asarray(y, float) + 0.0
        x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
        fx, fy = x - x0, y - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        g = lambda xx, yy: self.g[yy % self.p, xx % self.w]
        a = g(x0, y0) * (1 - fx) + g(x0 + 1, y0) * fx
        b = g(x0, y0 + 1) * (1 - fx) + g(x0 + 1, y0 + 1) * fx
        return a * (1 - fy) + b * fy

    def fbm(self, x, y, octaves=2):
        v, amp, tot = 0, 1.0, 0
        for o in range(octaves):
            v = v + amp * self(x * (2 ** o) + 17 * o, y * (2 ** o))
            tot += amp
            amp *= 0.5
        return v / tot


# --- geometry ----------------------------------------------------------------------------------------------------
def grid():
    c = LO + (np.arange(SIZE) + 0.5) / RES
    return np.meshgrid(c, c)  # u (x), v (y) in model units, y down like the texture


def distance(pixels, up=1.0, down=1.0):
    """Distance from each aura texel to the nearest pixel square; vertical distance scaled by `up` above a pixel and
    `down` below it (flames reach further up than down)."""
    u, v = grid()
    best = np.full(u.shape, 99.0)
    for x, y in pixels:
        dx = np.maximum(np.maximum(x - u, u - (x + 1)), 0)
        above = np.maximum(y - v, 0)
        below = np.maximum(v - (y + 1), 0)
        d = np.sqrt(dx * dx + (above * up + below * down) ** 2)
        best = np.minimum(best, d)
    return best


def inside(pixels):
    u, v = grid()
    m = np.zeros(u.shape, bool)
    for x, y in pixels:
        m |= (u >= x) & (u < x + 1) & (v >= y) & (v < y + 1)
    return m


def ramp(i, stops):
    """i in 0..1 (1 = closest to the blade) -> rgba from a list of (threshold, rgba), highest threshold first."""
    out = np.zeros(i.shape + (4,), np.uint8)
    done = np.zeros(i.shape, bool)
    for th, col in stops:
        m = (i >= th) & ~done
        out[m] = col
        done |= m
    return out


# --- auras -------------------------------------------------------------------------------------------------------
def tongues(img, reach, *, up, down, seed, sx, sy, speed, base, cut, power, stops, sway=0.0, warp=0.35):
    """The shared shape: around the blade, flames/wisps whose reach follows scrolling noise, so the width changes per
    piece and over time. `base` is the reach where the noise is low (0 = gaps), `cut` how much of the noise is gap."""
    blade = blade_pixels(img)
    d = distance(blade, up=up, down=down)
    solid = inside(whole_pixels(img))
    u, v = grid()
    nz, nz2 = Noise(seed), Noise(seed + 1)
    frames = []
    for f in range(N):
        s = f / N * 8 * speed
        w = warp * nz2(u * 0.3, v * 0.3 + s * 0.5)
        n = nz.fbm(u * sx + w + sway * math.sin(f / N * 6.283), v * sy + s)
        t = np.clip((n - cut) / (1 - cut), 0, 1) ** power
        r = reach * (base + (1.6 - base) * t)
        i = 1 - d / np.maximum(r, 1e-3)
        a = ramp(np.clip(i, 0, 1), stops)
        a[~((d < r) & ~solid)] = 0
        frames.append(a)
    return frames


FIRE = [(0.72, (255, 244, 190, 245)), (0.48, (255, 200, 60, 245)), (0.24, (250, 118, 24, 235)), (0.0, (200, 46, 16, 180))]
FIRE_LOW = [(0.6, (255, 210, 80, 245)), (0.3, (248, 124, 28, 230)), (0.0, (196, 50, 18, 170))]


def fire(img, level):
    """Flames licking up from the blade: thin at the base, tongues that come and go, taller at the tip."""
    reach = {1: 1.1, 2: 2.0}[level]
    frames = tongues(img, reach, up=0.42, down=2.6, seed=3, sx=0.55, sy=0.42, speed=1, base=0.12, cut=0.32,
                     power=1.6, stops=FIRE if level >= 2 else FIRE_LOW)
    if level >= 2:  # embers drifting up from the flames
        r_ = np.random.default_rng(5)
        spots = [(r_.uniform(6, 16), r_.uniform(-1, 7)) for _ in range(7)]
        for f, a in enumerate(frames):
            for k, (x0, y0) in enumerate(spots):
                ph = (f / N + k / 7) % 1
                if ph < 0.85:
                    put(a, x0 + 0.6 * math.sin(ph * 6.28 + k), y0 - ph * 6,
                        (255, 190, 70, 245) if ph < 0.5 else (230, 100, 30, 210))
    return frames


def holy(img, level):
    """Smite: light that rises off the blade in thin wavering strands, slower and softer than fire; twinkles on top."""
    reach = {1: 0.0, 2: 0.8, 3: 1.2, 4: 1.55, 5: 1.9}[level]
    stops = [(0.62, (255, 252, 222, 245)), (0.32, (255, 222, 96, 235)), (0.0, (240, 172, 40, 175))]
    frames = tongues(img, reach, up=0.35, down=3.0, seed=21, sx=0.65, sy=0.3, speed=0.5, base=0.0, cut=0.42,
                     power=1.2, stops=stops, warp=0.2)
    if level >= 5:
        for f, a in enumerate(frames):
            for k, (x0, y0) in enumerate(((16.2, -1.4), (12.5, -0.8), (17.0, 3.0), (9.0, 2.6), (6.0, 5.4))):
                ph = (f / N * 2 + k * 0.37) % 1
                if ph < 0.4:
                    star(a, x0, y0, 1 if ph < 0.1 or ph > 0.3 else 2, (255, 248, 210, 245))
    return frames


def venom(img, level):
    """Bane: green wisps curling off the blade, broken up, and drops that gather under the edge and fall."""
    reach = {1: 0.0, 2: 0.7, 3: 1.0, 4: 1.25, 5: 1.5}[level]
    stops = [(0.6, (200, 255, 110, 245)), (0.3, (104, 214, 52, 230)), (0.0, (44, 130, 30, 175))]
    frames = tongues(img, reach, up=0.5, down=1.6, seed=33, sx=0.45, sy=0.32, speed=0.5, base=0.0, cut=0.45,
                     power=1.0, stops=stops, sway=0.8, warp=0.6)
    drops = ((10.6, 7.2), (13.4, 4.4), (8.2, 9.4))[:{3: 1, 4: 2}.get(level, 3)] if level >= 3 else ()
    for f, a in enumerate(frames):
        for k, (x0, y0) in enumerate(drops):
            ph = (f / N + k * 0.41) % 1
            if ph < 0.45:  # gathering under the edge
                put(a, x0, y0 + 0.1, (150, 232, 90, 240))
            elif ph < 0.8:  # falling
                t = (ph - 0.45) / 0.35
                put(a, x0, y0 + 0.4 + t * t * 6, (170, 240, 100, 245))
                put(a, x0, y0 - 0.1 + t * t * 6, (110, 200, 70, 200))
    return frames


def edge(img, level):
    """Sharpness: fine cold spikes hugging the edge that flicker quickly, plus a gleam on the tip at the top grade."""
    reach = {1: 0.0, 2: 0.45, 3: 0.7, 4: 0.95, 5: 1.2}[level]
    stops = [(0.6, (246, 252, 255, 245)), (0.3, (160, 214, 255, 230)), (0.0, (90, 140, 236, 170))]
    frames = tongues(img, reach, up=0.8, down=1.0, seed=45, sx=1.1, sy=0.5, speed=2, base=0.0, cut=0.4,
                     power=1.3, stops=stops, warp=0.1)
    if level >= 5:
        for f, a in enumerate(frames):
            ph = f / N
            if ph < 0.25:
                star(a, 15.6, 0.3, 1 if ph < 0.06 or ph > 0.19 else 2, (255, 255, 255, 245))
    return frames


def put(a, x, y, col):
    i, j = int((x - LO) * RES), int((y - LO) * RES)
    if 0 <= i < SIZE and 0 <= j < SIZE:
        a[j, i] = col


def star(a, x, y, r, col):
    put(a, x, y, col)
    dim = col[:3] + (int(col[3] * 0.7),)
    for k in range(1, r + 1):
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            put(a, x + dx / RES, y + dy / RES, dim if k == r and r > 1 else col)


# --- texture parts (16 px, painted onto the sword) ---------------------------------------------------------------
def mix(c, d, t):
    return tuple(int(c[k] + (d[k] - c[k]) * t) for k in range(3))


def paint(img, kind, level):
    """Returns the sword with its texture part, plus a dict pixel -> glow code (for the shader: these pixels glow and
    move). The diamond (or any material) stays visible on the blade; colour goes into the core line and the guard."""
    out = img.copy()
    glow = {}
    px = out.load()
    if kind == 'fire':
        n = {1: 3, 2: 5}[level]
        for k, (x, y) in enumerate(CORE[:n]):
            t = k / max(1, n - 1)
            for q in ((x, y), (x + 1, y)):
                px[q] = mix((236, 72, 20), (255, 206, 90), t) + (255,)
                glow[q] = 'flicker'
        for x, y in GEMS:
            px[x, y] = (255, 140, 40, 255)
            glow[(x, y)] = 'flicker' 
    elif kind == 'holy':
        for x, y in GEMS:
            px[x, y] = (255, 226, 120, 255)
            glow[(x, y)] = 'pulse'
        if level >= 3:
            for x, y in GUARD:
                r, g, b, a = px[x, y]
                l = (r + g + b) / 3 / 255
                px[x, y] = mix((92, 62, 12), (250, 212, 96), min(1, l * 1.7)) + (a,)
        if level >= 3:
            for k, (x, y) in enumerate(CORE[:{3: 4, 4: 5, 5: 7}[level]]):
                px[x, y] = (255, 246, 206, 255)
                glow[(x, y)] = 'pulse'
        if level >= 5:
            for x, y in POMMEL:
                r, g, b, a = px[x, y]
                l = (r + g + b) / 3 / 255
                px[x, y] = mix((92, 62, 12), (250, 212, 96), min(1, l * 1.7)) + (a,)
    elif kind == 'venom':
        for x, y in GUARD:
            r, g, b, a = px[x, y]
            l = (r + g + b) / 3 / 255
            px[x, y] = mix((10, 40, 12), (90, 220, 70), min(1, l * 1.6)) + (a,)
        for x, y in GEMS:
            px[x, y] = (170, 255, 100, 255)
            glow[(x, y)] = 'pulse'
        if level >= 3:
            n = {3: 3, 4: 4, 5: 5}[level]
            for k, (x, y) in enumerate(CORE[:n]):
                for q in ((x, y), (x + 1, y)):
                    px[q] = mix((60, 170, 44), (190, 255, 120), k / max(1, n - 1)) + (255,)
                    glow[q] = 'pulse'
    elif kind == 'edge':
        for x, y in EDGE[:{1: 4, 2: 6}.get(level, 8)]:
            px[x, y] = (238, 255, 255, 255)
            glow[(x, y)] = 'flow'
        if level >= 3:
            for x, y in GEMS:
                px[x, y] = (150, 210, 255, 255)
                glow[(x, y)] = 'pulse'
        if level >= 5:
            for x, y in GUARD:
                r, g, b, a = px[x, y]
                l = (r + g + b) / 3 / 255
                px[x, y] = mix((16, 30, 70), (120, 170, 255), min(1, l * 1.5)) + (a,)
    return out, glow


AURAS = {'fire': fire, 'holy': holy, 'venom': venom, 'edge': edge}
