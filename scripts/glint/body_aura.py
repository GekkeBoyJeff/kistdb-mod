"""Proof of concept: armour sets in the style of the weapon auras. An animated aura shaped round the player's outline
(flames licking up, a sheath of light, smoke, wind, vines…), on a flat card through the middle of the body that turns
to face whoever looks (billboard). The body hides the card where it's in front of it, so the aura shows round you, not
over you. Its alpha carries the far-only mark (2 mod 16): it fades out near the camera, so never in first person.

Units are skin pixels (1/16 of a block before the player's 0.9375 scale): the outline is 16 wide and 32 tall (feet at
32), the card from x -8..24, y -12..36, two texels per skin pixel.

    python3 scripts/glint/body_aura.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from sword_aura import Noise, ramp  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources/assets/libreria')
N = 24
RES = 2
X0, X1, Y0, Y1 = -8, 24, -12, 36
W, H = (X1 - X0) * RES, (Y1 - Y0) * RES


# The worn outline per view, from the player model itself: its boxes (skin pixels, y down, z towards the back), armour
# standing proud of them, posed standing or crouching the way the game poses them, then seen from the view's angles.
# A view is a 15 degree step of the angle between where the body faces and where its viewer stands: s0..s5 standing
# (the standing body is the same from the front and the back, and from left and right), c0..c5 crouching, and
# c1m..c5m for crouching seen from the other side (the body leans the other way). The outline is the union over the
# step's angles, so it holds for every angle the step is used for.
PARTS = {  # name: box from, size, pivot, armour
    'head': ((-4, -8, -4), (8, 8, 8), (0, 0, 0), 1.0),
    'body': ((-4, 0, -2), (8, 12, 4), (0, 0, 0), 1.0),
    'rarm': ((-3, -2, -2), (4, 12, 4), (-5, 2, 0), 1.0),
    'larm': ((-1, -2, -2), (4, 12, 4), (5, 2, 0), 1.0),
    'rleg': ((-2, 0, -2), (4, 7, 4), (-1.9, 12, 0), 0.5),
    'lleg': ((-2, 0, -2), (4, 7, 4), (1.9, 12, 0), 0.5),
    'rboot': ((-2, 7, -2), (4, 5, 4), (-1.9, 12, 0), 1.0),
    'lboot': ((-2, 7, -2), (4, 5, 4), (1.9, 12, 0), 1.0),
}
# HumanoidModel when crouching: the body leans (its bottom goes back), the arms too, the legs move back, the head and
# shoulders come down; the renderer then lowers the whole model by 2/16 of a block (2.13 skin pixels).
CROUCH = {'head': (0, 4.2, 0, 0), 'body': (0, 3.2, 0, 0.5), 'rarm': (0, 3.2, 0, 0.4), 'larm': (0, 3.2, 0, 0.4),
          'rleg': (0, 0, 4, 0), 'lleg': (0, 0, 4, 0), 'rboot': (0, 0, 4, 0), 'lboot': (0, 0, 4, 0)}
CROUCH_DROP = 2 / 0.9375
STEP = 15


def vertices(crouch):
    """Every part's corners in card space: x across (skin pixels, 8 the middle), y down (32 the feet), z forward."""
    out = {}
    for name, ((x, y, z), (w, h, d), (px, py, pz), arm) in PARTS.items():
        dy, rot, dz = 0.0, 0.0, 0.0
        if crouch:
            _, dy, dz, rot = CROUCH[name]
        c, s_ = math.cos(rot), math.sin(rot)
        pts = []
        for vx in (x - arm, x + w + arm):
            for vy in (y - arm, y + h + arm):
                for vz in (z - arm, z + d + arm):
                    ry, rz = vy * c - vz * s_, vy * s_ + vz * c
                    mx, my, mz = px + vx, py + dy + ry, pz + dz + rz
                    # model space to the entity's: x stays, y up becomes the card's y down, model +z (back) is -z
                    pts.append((mx, my + 8 + (CROUCH_DROP if crouch else 0), -mz))
        out[name] = pts
    return out


def hull(pts):
    pts = sorted(set(pts))
    if len(pts) < 3:
        return pts
    cross = lambda o, a, b: (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lo, hi = [], []
    for p_ in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p_) <= 0:
            lo.pop()
        lo.append(p_)
    for p_ in reversed(pts):
        while len(hi) >= 2 and cross(hi[-2], hi[-1], p_) <= 0:
            hi.pop()
        hi.append(p_)
    return lo[:-1] + hi[:-1]


def view_angles(view):
    """The angles (degrees, sign = which side the viewer is on) a view stands for."""
    k = int(view[1])
    sign = -1 if view.endswith('m') else 1
    return [sign * (STEP * k + t) for t in np.linspace(0, STEP, 7)]


def outline(view):
    """The outline's mask on the card's grid for a view: the model's parts, projected along each of the view's angles."""
    u, v = grid()
    m = np.zeros(u.shape, bool)
    parts = vertices(view[0] == 'c')
    for ang in view_angles(view):
        th = math.radians(ang)
        # the viewer stands at (dx, dz) from the body (which faces +z); the card's x runs to the viewer's right
        dx, dz = -math.sin(th), math.cos(th)
        for pts in parts.values():
            poly = hull([(8 + ex * dz - ez * dx, ey) for ex, ey, ez in pts])
            inside_poly = np.ones(u.shape, bool)
            for k in range(len(poly)):
                (ax, ay), (bx, by) = poly[k], poly[(k + 1) % len(poly)]
                inside_poly &= (bx - ax) * (v - ay) - (by - ay) * (u - ax) >= 0
            m |= inside_poly
    return m


def depth(view):
    """How far the card sits behind the body's middle, away from its viewer (blocks): behind the arm and leg on the far
    side, so the card is never drawn over a part of you the viewer can see."""
    worst = 0.0
    parts = vertices(view[0] == 'c')
    for ang in view_angles(view):
        th = math.radians(ang)
        dx, dz = -math.sin(th), math.cos(th)
        for name, pts in parts.items():
            if name in ('head', 'body'):
                continue
            cx = sum(p_[0] for p_ in pts) / 8
            cz = sum(p_[2] for p_ in pts) / 8
            worst = max(worst, -(cx * dx + cz * dz))
    return round((worst + 1.0) * 0.9375 / 16, 3)


VIEWS = ['s0', 's1', 's2', 's3', 's4', 's5', 'c0'] + [f'c{k}{m}' for k in range(1, 6) for m in ('', 'm')]
INSET = 2
CELL = 1 / RES


def grid():
    u = X0 + (np.arange(W) + 0.5) / RES
    v = Y0 + (np.arange(H) + 0.5) / RES
    return np.meshgrid(u, v)


def cells(mask):
    j, i = np.nonzero(mask)
    return list(zip((X0 + i / RES).tolist(), (Y0 + j / RES).tolist()))


VIEW = None
SIL = CORE = []
SIL_MASK = None
TOP = FOOT = 0.0


def set_view(view):
    """Everything after this is drawn for that view: the outline, and the core the aura fills from."""
    global VIEW, SIL, CORE, SIL_MASK, SOLID, TOP, FOOT
    from scipy.ndimage import distance_transform_edt
    VIEW = view
    SIL_MASK = outline(view)
    SIL = cells(SIL_MASK)
    CORE = cells(distance_transform_edt(SIL_MASK) / RES > INSET) or SIL
    SOLID = SIL_MASK
    rows = np.nonzero(SIL_MASK.any(axis=1))[0]
    TOP, FOOT = Y0 + rows[0] / RES, Y0 + (rows[-1] + 1) / RES


def level(y):
    """A height given for the standing body (0 the top of the head, 32 the feet) at the same place on this view's body."""
    return FOOT - (33 - y) * (FOOT - TOP) / 34


def distance(pixels, up=1.0, down=1.0):
    u, v = grid()
    best = np.full(u.shape, 99.0, np.float32)
    if not pixels:
        return best
    P = np.array(pixels, np.float32)
    uu, vv = u.ravel().astype(np.float32), v.ravel().astype(np.float32)
    flat = best.ravel()
    for k in range(0, len(P), 256):
        x, y = P[k:k + 256, 0:1], P[k:k + 256, 1:2]
        dx = np.maximum(np.maximum(x - uu, uu - (x + CELL)), 0)
        above = np.maximum(y - vv, 0)
        below = np.maximum(vv - (y + CELL), 0)
        flat = np.minimum(flat, np.sqrt(dx * dx + (above * up + below * down) ** 2).min(axis=0))
    return flat.reshape(u.shape)


def inside(pixels):
    u, v = grid()
    m = np.zeros(u.shape, bool)
    for x, y in pixels:
        m |= (u >= x) & (u < x + CELL) & (v >= y) & (v < y + CELL)
    return m


SOLID = None


def tongues(src, reach, *, up, down, seed, sx, sy, speed, base, cut, power, stops, sway=0.0, warp=0.35, sideways=0.0):
    """Like the weapons' aura: reach following scrolling noise, so the width changes per piece and over time. It grows
    from the core of the body (src: the outline's pixels it starts at, moved in to the core), so it is filled all the
    way in: your body hides the middle, and wherever your body isn't (an arm swinging in, turned a little) it reaches
    right up to you, never a gap or a rim. Inside the outline it is held at a mid colour, so what shows there is glow,
    not a white edge."""
    u, v = grid()
    # the core from the height the aura starts at, all of it (not just its edge, or the middle would be left empty)
    top = min(p[1] for p in src) - INSET if src else -99
    pick = (distance_transform(SIL_MASK) > INSET) & (v >= top)
    srcs = cells(pick) if pick.any() else CORE
    d = distance(srcs, up=up, down=down)
    u, v = grid()
    nz, nz2 = Noise(seed, w=96), Noise(seed + 1, w=96)
    nz3 = Noise(seed + 2, w=96)
    hot = [np.clip((nz3.fbm(u * 0.5, v * 0.5 + f / N * 8 * speed) - 0.45) / 0.3, 0, 1) for f in range(N)]
    inner = distance_transform(SIL_MASK)
    frames = []
    for f in range(N):
        s = f / N * 8 * speed
        w = warp * nz2(u * 0.3, v * 0.3 + s * 0.5)
        n = nz.fbm(u * sx + w + sway * math.sin(f / N * 6.283) + sideways * s, v * sy + s * (1 - abs(sideways)))
        t = np.clip((n - cut) / (1 - cut), 0, 1) ** power
        # always at least out to the outline (INSET from the core), then the reach on top of that
        rr = INSET + reach * (base + (1.6 - base) * t)
        i = 1 - d / np.maximum(rr, 1e-3)
        # the hottest colour comes in licks, not as a line along your outline
        i = np.minimum(i, 0.58 + 0.42 * hot[f])
        # behind you (the outline): only a low glow that flickers with the flames, for where an arm or leg moves off it;
        # the last pixel and a half inside the outline still as bright as outside, should the card peek out from behind
        low = np.minimum(i, 0.16 + 0.22 * t)
        i = np.where(SOLID, low + (i - low) * np.clip(1 - inner / 1.5, 0, 1), i)
        a = ramp(np.clip(i, 0, 1), stops)
        a[~(d < rr)] = 0
        a[SOLID, 3] = (a[SOLID, 3] * 0.8).astype(np.uint8)
        frames.append(a)
    return frames


def distance_transform(mask):
    from scipy.ndimage import distance_transform_edt
    return distance_transform_edt(mask) / RES


def put(a, x, y, col):
    i, j = int((x - X0) * RES), int((y - Y0) * RES)
    if 0 <= i < W and 0 <= j < H:
        a[j, i] = col


def mix(c, t, by):
    return tuple(int(c[k] * (1 - by) + t[k] * by) for k in range(3))


FIRE = [(0.72, (255, 244, 190, 241)), (0.48, (255, 200, 60, 241)), (0.24, (250, 118, 24, 225)), (0.0, (200, 46, 16, 177))]
FROST = [(0.72, (250, 254, 255, 241)), (0.48, (200, 236, 255, 241)), (0.24, (120, 190, 250, 225)), (0.0, (70, 120, 220, 177))]
SOUL = [(0.72, (230, 255, 255, 241)), (0.48, (120, 236, 255, 241)), (0.24, (40, 190, 220, 225)), (0.0, (20, 110, 150, 177))]
ARCANE = [(0.6, (236, 240, 255, 241)), (0.3, (150, 170, 255, 225)), (0.0, (120, 96, 230, 161))]
SMOKE = [(0.6, (190, 190, 200, 209)), (0.3, (140, 140, 152, 193)), (0.0, (96, 96, 108, 145))]
WIND = [(0.6, (244, 255, 250, 225)), (0.3, (200, 240, 222, 193)), (0.0, (150, 210, 186, 145))]
BRIAR = [(0.6, (150, 220, 90, 241)), (0.3, (78, 150, 48, 225)), (0.0, (40, 92, 30, 177))]
RENEW = [(0.6, (250, 255, 200, 241)), (0.3, (180, 250, 100, 225)), (0.0, (90, 200, 60, 161))]
SHACKLE = [(0.6, (200, 160, 255, 225)), (0.3, (120, 80, 190, 209)), (0.0, (60, 36, 100, 177))]
FADE = [(0.6, (250, 246, 255, 225)), (0.3, (214, 200, 250, 193)), (0.0, (170, 150, 230, 145))]
TINTS = {'golden': ((255, 212, 90), 0.35), 'netherite': ((106, 30, 38), 0.4), 'copper': ((79, 200, 168), 0.3),
         'leather': ((192, 138, 90), 0.3)}


def tinted(stops, material):
    if material not in TINTS:
        return stops
    t, by = TINTS[material]
    return [(th, mix(c[:3], t, by) + (c[3],)) for th, c in stops]


def below(y):
    return [p for p in SIL if p[1] >= y]


def edge_of(pixels):
    s = set(pixels)
    return [p for p in pixels if any((p[0] + dx, p[1] + dy) not in s for dx, dy in ((CELL, 0), (-CELL, 0), (0, CELL), (0, -CELL)))]


def body(kind, g, material='diamond', feet=None, view='s0'):
    """One aura: kind (fire, protection, blast, projectile, thorns, mending, binding, vanishing), grade, the armour's
    material (a tint), what the boots do at the feet (frost, soul), and the view (front, quarter, side, crouching)."""
    set_view(view)
    edge = edge_of(SIL)
    rng = np.random.default_rng(3)
    if kind == 'fire':
        # flames licking up the outline: the legs at I, to the waist at II, all of you from III, tall over your head at IV
        src = [p for p in edge if p[1] >= level((20, 13, 0, -2)[g - 1])]
        reach = (2.2, 3.0, 3.8, 5.2)[g - 1]
        frames = tongues(src, reach, up=0.38, down=2.6, seed=3, sx=0.42, sy=0.3, speed=1.1, base=0.14, cut=0.3, power=1.5,
                         stops=tinted(FIRE, material))
        if feet:
            low = tongues([p for p in edge if p[1] >= level(24)], 3.0, up=0.4, down=2.6, seed=5, sx=0.42, sy=0.3, speed=1.1, base=0.14,
                          cut=0.3, power=1.5, stops=FROST if feet == 'frost' else SOUL)
            u, v = grid()
            for a, b in zip(frames, low):
                m = (v > level(22)) & (b[..., 3] > 0)
                a[m] = b[m]
                a[(v > level(26)) & (b[..., 3] == 0)] = 0
        if g >= 3:  # embers rising off it
            spots = [(rng.uniform(-3, 19), level(rng.uniform(0, 30))) for _ in range(5 + 3 * g)]
            for f, a in enumerate(frames):
                for k, (x0, y0) in enumerate(spots):
                    ph = (f / N + k / len(spots)) % 1
                    if ph < 0.8:
                        put(a, x0 + 0.8 * math.sin(ph * 6.28 + k), y0 - ph * 14, (255, 200, 80, 241) if ph < 0.4 else (240, 110, 30, 209))
        return frames
    if kind == 'protection':
        # a thin sheath of light hugging the outline, breathing; runes rising along your sides from III
        reach = (1.2, 1.8, 2.4, 3.0)[g - 1]
        src = [p for p in edge if p[1] >= level((18, 8, 0, -2)[g - 1])]
        frames = tongues(src, reach, up=0.7, down=1.2, seed=21, sx=0.6, sy=0.25, speed=0.5, base=0.25, cut=0.35, power=1.0,
                         stops=tinted(ARCANE, material), warp=0.2)
        if g >= 3:
            glyphs = [[(0, 0), (1, 0), (2, 0), (1, 1), (1, 2)], [(0, 0), (0, 1), (0, 2), (1, 1), (2, 0), (2, 2)],
                      [(1, 0), (0, 1), (2, 1), (1, 2)], [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)]]
            left = min(p[0] for p in SIL) - 3.5
            right = max(p[0] for p in SIL) + 2.5
            spots = [(left if k % 2 else right, level(28 - k * 3)) for k in range(2 * g)]
            for f, a in enumerate(frames):
                for k, (x0, y0) in enumerate(spots):
                    ph = (f / N + k * 0.29) % 1
                    if ph < 0.75:
                        for gx, gy in glyphs[k % 4]:
                            put(a, x0 + gx * 0.5, y0 - ph * 10 + gy * 0.5, (210, 220, 255, 241) if ph < 0.5 else (150, 140, 240, 193))
        return frames
    if kind == 'blast':
        # heavy smoke round your legs that curls up and thins, higher per grade; sparks glowing in it from III
        src = [p for p in edge if p[1] >= level((24, 18, 10, 4)[g - 1])]
        frames = tongues(src, (2.6, 3.2, 3.8, 4.4)[g - 1], up=0.6, down=1.5, seed=41, sx=0.25, sy=0.2, speed=0.45, base=0.3,
                         cut=0.3, power=1.0, stops=tinted(SMOKE, material), sway=1.0, warp=0.8)
        if g >= 3:
            for f, a in enumerate(frames):
                for k in range(3 * g):
                    x0, y0 = rng.uniform(-3, 19), level(rng.uniform(14, 34))
                    if (f + k * 5) % 12 < 4:
                        put(a, x0, y0, (255, 160, 60, 241))
        return frames
    if kind == 'projectile':
        # wind sweeping round you: streaks running sideways along the outline, more of you per grade
        src = [p for p in edge if p[1] >= level((22, 14, 4, -2)[g - 1])]
        frames = tongues(src, (2.4, 3.0, 3.6, 4.2)[g - 1], up=1.0, down=1.0, seed=51, sx=0.12, sy=0.9, speed=1.6, base=0.0,
                         cut=0.45, power=1.2, stops=tinted(WIND, material), sideways=1.0, warp=0.1)
        return frames
    if kind == 'thorns':
        # vines and thorns standing out along the outline, sharp, slow; red glints where one bites
        src = [p for p in edge if p[1] >= level((18, 8, -2)[g - 1])]
        frames = tongues(src, (2.6, 3.4, 4.2)[g - 1], up=0.8, down=0.8, seed=61, sx=0.9, sy=0.9, speed=0.25, base=0.15, cut=0.45,
                         power=2.0, stops=BRIAR, warp=0.5)
        for f, a in enumerate(frames):
            for k in range(2 + 2 * g):
                x0, y0 = edge[(k * 37) % len(edge)]
                if (f + k * 7) % 16 < 5:
                    put(a, x0 + (1.5 if x0 > 8 else -1.0), y0 + 0.5, (255, 70, 80, 241))
        return frames
    if kind == 'mending':
        # soft green-gold light rising off you, and orbs of experience drifting in
        frames = tongues(edge, 3.0, up=0.4, down=2.0, seed=71, sx=0.5, sy=0.22, speed=0.6, base=0.1, cut=0.38, power=1.2,
                         stops=RENEW, warp=0.3)
        for f, a in enumerate(frames):
            for k in range(8):
                ang = k * 0.785 + 0.3
                ph = (f / N + k * 0.125) % 1
                rr = 14 * (1 - ph) + 3
                x0, y0 = 8 + rr * math.cos(ang), level(16) + rr * 1.2 * math.sin(ang)
                for dx, dy in ((0, 0), (0.5, 0), (0, 0.5), (0.5, 0.5)):
                    put(a, x0 + dx, y0 + dy, (220, 255, 120, 241) if ph < 0.8 else (255, 255, 220, 241))
        return frames
    if kind == 'binding':
        # violet-black smoke seeping off you and sinking, pooling round your feet
        frames = tongues(edge, 3.4, up=2.2, down=0.45, seed=81, sx=0.35, sy=0.3, speed=-0.5, base=0.15, cut=0.35, power=1.0,
                         stops=SHACKLE, warp=0.6)
        return frames
    if kind == 'vanishing':
        # the outline comes apart: flakes of you drifting off and winking out, a faint haze where they leave
        frames = tongues(edge, 1.6, up=1.0, down=1.0, seed=91, sx=0.7, sy=0.7, speed=0.4, base=0.0, cut=0.5, power=1.0,
                         stops=FADE)
        for f, a in enumerate(frames):
            for k in range(26):
                x0, y0 = edge[(k * 53) % len(edge)]
                ph = (f / N + k * 0.137) % 1
                dx = (x0 - 8) / 8
                if ph < 0.7:
                    put(a, x0 + 0.5 + dx * ph * 7, y0 + 0.5 - ph * 3, (250, 246, 255, 241) if ph < 0.35 else (200, 186, 245, 193))
        return frames
    raise ValueError(kind)


def save(img, path):
    """As a palette PNG when that's exactly the same picture (the auras have a handful of colours): half the size."""
    n = img.getcolors(1 << 16)
    if n is not None and len(n) <= 256:
        q = img.quantize(len(n), method=Image.Quantize.FASTOCTREE)
        if np.array_equal(np.array(q.convert('RGBA')), np.array(img)):
            q.save(path, optimize=True)
            return
    img.save(path, optimize=True)


def write(name, frames):
    tex = os.path.join(ROOT, 'textures/item/fx/body')
    mod = os.path.join(ROOT, 'models/item/fx/body')
    itm = os.path.join(ROOT, 'items/fx/body')
    for d in (tex, mod, itm):
        os.makedirs(d, exist_ok=True)
    strip = Image.new('RGBA', (W, H * len(frames)), (0, 0, 0, 0))
    for k, f in enumerate(frames):
        f = f.copy()
        a = f[..., 3].astype(int)
        # far-only: alpha 2 (mod 16), fades out near the camera
        f[..., 3] = np.where(a > 0, np.clip(16 * np.round((a - 2) / 16) + 2, 18, 242), 0).astype(np.uint8)
        strip.paste(Image.fromarray(f, 'RGBA'), (0, k * H))
    save(strip, os.path.join(tex, name + '.png'))
    # frames are 64 x 96, not square: say so, or the game cuts the strip into 64 x 64 frames
    json.dump({'animation': {'frametime': 1, 'interpolate': False, 'width': W, 'height': H}}, open(os.path.join(tex, name + '.png.mcmeta'), 'w'))
    t = f'libreria:item/fx/body/{name}'
    face = lambda uv: {'uv': uv, 'texture': '#a'}
    # 16 x 24 model units; the display scales it to the card's size (see Fx.bodyAura)
    json.dump({'textures': {'a': t, 'particle': t},
               'elements': [{'from': [0, 0, 8], 'to': [16, 24, 8], 'shade': False, 'light_emission': 15,
                             'faces': {'south': face([0, 0, 16, 16]), 'north': face([16, 0, 0, 16])}}],
               'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}},
              open(os.path.join(mod, name + '.json'), 'w'), indent=1)
    json.dump({'model': {'type': 'minecraft:model', 'model': f'libreria:item/fx/body/{name}'}},
              open(os.path.join(itm, name + '.json'), 'w'), indent=1)


WANT = [(k, g, 'diamond', None) for k in ('fire', 'protection', 'blast', 'projectile') for g in (1, 2, 3, 4)] + \
       [('thorns', g, 'diamond', None) for g in (1, 2, 3)] + [(k, 1, 'diamond', None) for k in ('mending', 'binding', 'vanishing')] + \
       [('fire', 4, m, None) for m in ('golden', 'netherite')] + [('protection', 4, m, None) for m in ('golden', 'netherite')] + \
       [('fire', 4, 'diamond', 'frost'), ('fire', 4, 'diamond', 'soul')]


def name_of(kind, g, material, feet):
    return f'{kind}_{g}' + ('' if material == 'diamond' else f'_{material}') + (f'_{feet}' if feet else '')


if __name__ == '__main__':
    for k, g, m, f in WANT:
        for view in VIEWS:
            write(name_of(k, g, m, f) + '__' + view, body(k, g, m, f, view))
    print(len(WANT), 'body auras x', len(VIEWS), 'views')
