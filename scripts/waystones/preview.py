"""A small software renderer for block/item model elements (cuboids and planes, no rotations), to look at a design
before it goes into the game: perspective camera, z-buffer, Minecraft's face shading, textures 1:1, tinted faces in a
colour, faces that shine unlit. Used by grand.py (the grand waystone); not part of the game.

A face is a dict: corners (4 points in blocks: top-left, top-right, bottom-right, bottom-left as seen from outside),
tex (an RGBA numpy array), uv (u0, v0, u1, v1 in texture pixels), shade (a factor), tint (rgb or None), glow (bool).
"""
import math

import numpy as np
from PIL import Image


class Camera:
    def __init__(self, eye, target, fov=50, size=(960, 640)):
        self.eye = np.array(eye, float)
        self.w, self.h = size
        f = np.array(target, float) - self.eye
        f /= np.linalg.norm(f)
        r = np.cross(f, [0, 1, 0])
        r /= np.linalg.norm(r)
        u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u
        self.k = (self.h / 2) / math.tan(math.radians(fov) / 2)

    def project(self, p):
        d = np.asarray(p, float) - self.eye
        z = d @ self.f
        return (self.w / 2 + (d @ self.r) * self.k / z, self.h / 2 - (d @ self.u) * self.k / z, z)


def sky(size, night=False):
    w, h = size
    top, bottom = ((14, 18, 40), (40, 46, 78)) if night else ((120, 168, 236), (196, 220, 248))
    t = np.linspace(0, 1, h)[:, None, None]
    img = (np.array(top) * (1 - t) + np.array(bottom) * t) * np.ones((1, w, 1))
    return img.astype(np.float32)


def render(faces, cam, night=False, ground=0.0, ground_colour=(92, 138, 58)):
    w, h = cam.w, cam.h
    img = sky((w, h), night)
    zbuf = np.full((h, w), np.inf, np.float32)
    light = 0.38 if night else 1.0
    # The ground: a plane at y = ground, in tiles (what lies behind the camera is left out)
    gtex = np.zeros((16, 16, 4), np.uint8)
    rng = np.random.default_rng(3)
    for y in range(16):
        for x in range(16):
            n = rng.integers(-10, 10)
            gtex[y, x] = (max(0, ground_colour[0] + n), max(0, ground_colour[1] + n), max(0, ground_colour[2] + n), 255)
    for gx in range(-30, 30, 2):
        for gz in range(-30, 30, 2):
            g = [(gx, ground, gz), (gx + 2, ground, gz), (gx + 2, ground, gz + 2), (gx, ground, gz + 2)]
            _quad(img, zbuf, cam, g, gtex, (0, 0, 32, 32), light, None, False, tile=True)
    for f in faces:
        normal = np.cross(np.subtract(f["corners"][1], f["corners"][0]), np.subtract(f["corners"][3], f["corners"][0]))
        centre = np.mean(f["corners"], axis=0)
        if normal @ (cam.eye - centre) >= 0:
            continue
        lit = 1.0 if f["glow"] else f["shade"] * light
        _quad(img, zbuf, cam, f["corners"], f["tex"], f["uv"], lit, f["tint"], f["glow"])
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def _quad(img, zbuf, cam, corners, tex, uv, lit, tint, glow, tile=False):
    u0, v0, u1, v1 = uv
    uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    pts = [cam.project(c) for c in corners]
    if any(p[2] <= 0.05 for p in pts):
        return
    for tri in ((0, 1, 2), (0, 2, 3)):
        _tri(img, zbuf, [pts[i] for i in tri], [uvs[i] for i in tri], tex, lit, tint, tile)


def _tri(img, zbuf, p, t, tex, lit, tint, tile):
    h, w = zbuf.shape
    xs, ys = [q[0] for q in p], [q[1] for q in p]
    x0, x1 = max(0, int(math.floor(min(xs)))), min(w - 1, int(math.ceil(max(xs))))
    y0, y1 = max(0, int(math.floor(min(ys)))), min(h - 1, int(math.ceil(max(ys))))
    if x0 > x1 or y0 > y1:
        return
    X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = p
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9:
        return
    l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
    l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
    l3 = 1 - l1 - l2
    inside = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
    if not inside.any():
        return
    iz = l1 / az + l2 / bz + l3 / cz
    z = 1 / iz
    u = (l1 * t[0][0] / az + l2 * t[1][0] / bz + l3 * t[2][0] / cz) * z
    v = (l1 * t[0][1] / az + l2 * t[1][1] / bz + l3 * t[2][1] / cz) * z
    th, tw = tex.shape[:2]
    if tile:
        ui, vi = np.floor(u).astype(int) % tw, np.floor(v).astype(int) % th
    else:
        ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
        vi = np.clip(np.floor(v).astype(int), 0, th - 1)
    col = tex[vi, ui].astype(np.float32)
    ok = inside & (col[..., 3] > 127)
    sub = zbuf[y0:y1 + 1, x0:x1 + 1]
    ok &= z < sub
    rgb = col[..., :3]
    if tint is not None:
        rgb = rgb * (np.array(tint, np.float32) / 255)
    rgb = rgb * lit
    region = img[y0:y1 + 1, x0:x1 + 1]
    region[ok] = rgb[ok]
    sub[ok] = z[ok]


def bloom(im, strength=0.6):
    """A soft glow round what's bright (for the night pictures)."""
    from PIL import ImageFilter
    a = np.asarray(im).astype(np.float32)
    bright = np.clip(a - 200, 0, 255) * 3
    blur = np.asarray(Image.fromarray(np.clip(bright, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(12))).astype(np.float32)
    return Image.fromarray(np.clip(a + blur * strength, 0, 255).astype(np.uint8))
