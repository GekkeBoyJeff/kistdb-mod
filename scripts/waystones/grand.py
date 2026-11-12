"""The grand waystone: a shrine 5 x 5 blocks and over 10 high in the waystones' own look (their textures, one texture
pixel per model pixel). A stepped plinth; four slender pillars with rune panels; a ring beam with a frieze of runes; a
stepped roof with a crystal capstone; in the middle a pedestal with a pool and, floating above it, a great rune crystal
with four small ones round it. What shines is drawn grey and coloured by the stone's dye.

The design is made of boxes in pixels (0..80 across, from the ground up), cut along the 16-pixel grid so every face maps
its texture 1:1, then (for the game) gathered per 3 x 3 x 3 blocks into item models shown by display entities.

python3 scripts/waystones/grand.py preview   -> pictures in /home/claude/mock/grand/
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import preview  # noqa: E402

ASSETS = os.path.join(os.path.dirname(__file__), "..", "..", "waystones", "src", "main", "resources", "assets", "libreria")
TEX = os.path.join(ASSETS, "textures", "block", "waystone")
PANEL = (5, 2, 11, 15)
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
FACES = ("north", "south", "east", "west", "up", "down")
SIDES = ("north", "south", "east", "west")


class Design:
    def __init__(self):
        self.boxes = []

    def box(self, frm, to, tex, kind="stone", top=None, faces=FACES, tint=False, uv=None, move=None):
        """A cuboid. kind: how its sides take the texture ("stone": tiled on the grid, "trim": a band, "stretch": the whole
        texture over each face, as the crystals). move: a function on points (pixels), for what is turned."""
        self.boxes.append(dict(frm=list(frm), to=list(to), tex=tex, kind=kind, top=top, faces=faces, tint=tint, uv=uv, plane=None,
                               move=move))

    def quad(self, pts, tex, window, tint=True):
        """A free quad (corners in pixels: top left, top right, bottom right, bottom left), seen from both sides."""
        self.boxes.append(dict(frm=None, to=None, tex=tex, kind="quad", top=None, faces=(), tint=tint, uv=list(window), plane=None,
                               move=None, pts=pts))

    def plane(self, frm, to, face, tex, window, tint=False):
        self.boxes.append(dict(frm=list(frm), to=list(to), tex=tex, kind="plane", top=None, faces=(face,), tint=tint, uv=list(window),
                               plane=face, move=None))


# ---------------------------------------------------------------- the design

def grand():
    d = Design()
    # The plinth: three steps
    d.box([0, 0, 0], [80, 4, 80], "r", "trim", "t")
    d.box([4, 4, 4], [76, 8, 76], "s", "stone", "t")
    d.box([8, 8, 8], [72, 12, 72], "r", "trim", "t")
    # Four pillars
    for cx in (16, 64):
        for cz in (16, 64):
            d.box([cx - 7, 12, cz - 7], [cx + 7, 18, cz + 7], "r", "trim", "t")
            d.box([cx - 6, 18, cz - 6], [cx + 6, 22, cz + 6], "s", "stone", "t")
            d.box([cx - 4, 22, cz - 4], [cx + 4, 100, cz + 4], "s", "stone", faces=SIDES)
            d.box([cx - 6, 100, cz - 6], [cx + 6, 104, cz + 6], "r", "trim", "t", faces=FACES)
            d.box([cx - 7, 104, cz - 7], [cx + 7, 108, cz + 7], "r", "trim", "t")
            # a rune panel on every side of the shaft, at eye height, and its runes
            for face in SIDES:
                rune_panel(d, cx, cz, 4, 30, face)
    # The ring beam on the pillars, a cornice over it, runes along it
    d.box([8, 108, 8], [72, 110, 72], "r", "trim", faces=SIDES + ("down",))
    d.box([8, 110, 8], [72, 116, 24], "s", "stone", faces=FACES)
    d.box([8, 110, 56], [72, 116, 72], "s", "stone", faces=FACES)
    d.box([8, 110, 24], [24, 116, 56], "s", "stone", faces=FACES)
    d.box([56, 110, 24], [72, 116, 56], "s", "stone", faces=FACES)
    d.box([6, 116, 6], [74, 118, 74], "r", "trim", "t")
    for i in range(5):
        u = 18 + i * 10
        for face in SIDES:
            frieze(d, u, face)
    # The roof: steps up to the capstone
    d.box([12, 118, 12], [68, 124, 68], "s", "stone", "t")
    d.box([18, 124, 18], [62, 128, 62], "r", "trim", "t")
    d.box([24, 128, 24], [56, 134, 56], "s", "stone", "t")
    d.box([30, 134, 30], [50, 138, 50], "r", "trim", "t")
    d.box([34, 138, 34], [46, 146, 46], "s", "stone", "t")
    crystal(d, 40, 146, 40, [(8, 4), (6, 4), (4, 6), (2, 6)], "c")
    # The pedestal in the middle, a pool on it
    d.box([29, 12, 29], [51, 14, 51], "r", "trim", "t")
    d.box([32, 14, 32], [48, 26, 48], "s", "stone", faces=SIDES)
    for face in SIDES:
        rune_panel(d, 40, 40, 8, 13, face, height=12)
    d.box([30, 26, 30], [50, 28, 50], "r", "trim", "t")
    d.box([29, 28, 29], [51, 32, 51], "s", "stone", "b")
    d.plane([33, 32.02, 33], [47, 32.02, 47], "up", "w", [1, 1, 15, 15], True)
    # The great rune, floating, and four small ones round it
    crystal(d, 40, 42, 40, [(4, 4), (8, 6), (12, 8), (14, 10, "u"), (12, 8), (8, 6), (4, 4)], "c")
    for x, z in ((40, 14), (40, 66), (14, 40), (66, 40)):
        crystal(d, x, 64, z, [(2, 1), (4, 2), (2, 1)], "c")
    return d


def monument():
    """The waystone itself, grand: a stepped plinth with four little obelisks, a body with nine runes on every side between
    pilasters, an open frame with the great rune turning in it over a pool, a slimmer top and a crystal capstone."""
    d = Design()
    # The plinth: four steps
    d.box([0, 0, 0], [80, 3, 80], "r", "trim", "t")
    d.box([3, 3, 3], [77, 6, 77], "s", "stone", "t")
    d.box([6, 6, 6], [74, 9, 74], "r", "trim", "t")
    d.box([9, 9, 9], [71, 12, 71], "s", "stone", "t")
    # Four little obelisks on its corners
    for cx in (6, 74):
        for cz in (6, 74):
            d.box([cx - 4, 3, cz - 4], [cx + 4, 7, cz + 4], "r", "trim", "t")
            d.box([cx - 3, 7, cz - 3], [cx + 3, 26, cz + 3], "s", "stone", faces=SIDES)
            d.box([cx - 4, 26, cz - 4], [cx + 4, 28, cz + 4], "r", "trim", "t")
            for face in SIDES:
                rune_strip(d, cx, cz, 3, 12, 21, face)
            crystal(d, cx, 28, cz, [(4, 2), (2, 3)], "c")
    # The body: pilasters at its corners, three columns of rune panels on each side between them
    d.box([14, 12, 14], [66, 15, 66], "r", "trim", "t")
    d.box([16, 15, 16], [64, 54, 64], "s", "stone", faces=SIDES)
    for cx in (18, 62):
        for cz in (18, 62):
            d.box([cx - 4, 15, cz - 4], [cx + 4, 54, cz + 4], "s", "stone", faces=SIDES)
    for face in SIDES:
        for u in (28, 40, 52):
            for y0 in (15.5, 28.5, 41.5):
                body_panel(d, u, y0, face)
    d.box([12, 54, 12], [68, 58, 68], "r", "trim", "t")
    # The pool under the frame
    d.plane([24, 58.02, 24], [56, 58.02, 56], "up", "w", [0, 0, 16, 16], True)
    # The open frame: four posts with collars, the great rune turning in it, four small ones round it
    for cx in (18, 62):
        for cz in (18, 62):
            d.box([cx - 5, 58, cz - 5], [cx + 5, 61, cz + 5], "r", "trim", "t")
            d.box([cx - 4, 61, cz - 4], [cx + 4, 115, cz + 4], "s", "stone", faces=SIDES)
            d.box([cx - 5, 86, cz - 5], [cx + 5, 88, cz + 5], "r", "trim", "t")
            d.box([cx - 5, 115, cz - 5], [cx + 5, 118, cz + 5], "r", "trim", faces=SIDES + ("down",))
    crystal(d, 40, 64, 40, [(4, 4), (8, 6), (12, 8), (14, 10, "u"), (12, 8), (8, 6), (4, 4)], "c")
    for x, z in ((40, 15), (40, 65), (15, 40), (65, 40)):
        crystal(d, x, 84, z, [(2, 1), (4, 2), (2, 1)], "c")
    d.box([12, 118, 12], [68, 122, 68], "r", "trim", "t")
    # The top: a step, a slimmer tower with a rune panel on each side, a cornice, a stepped cap, the capstone
    d.box([16, 122, 16], [64, 126, 64], "s", "stone", "t")
    d.box([20, 126, 20], [60, 148, 60], "s", "stone", faces=SIDES)
    for face in SIDES:
        body_panel(d, 40, 131, face, half=20)
    d.box([18, 148, 18], [62, 151, 62], "r", "trim", "t")
    y = 151
    for w, h, tex in ((36, 5, "s"), (30, 4, "r"), (24, 5, "s"), (18, 4, "r"), (12, 5, "s"), (8, 3, "r")):
        d.box([40 - w / 2, y, 40 - w / 2], [40 + w / 2, y + h, 40 + w / 2], tex, "stone" if tex == "s" else "trim", "t")
        y += h
    crystal(d, 40, y, 40, [(6, 4), (4, 5), (2, 6)], "c")
    return d


def rot(axis, degrees):
    a = math.radians(degrees)
    c, s_ = math.cos(a), math.sin(a)
    x, y, z = np.array(axis, float) / np.linalg.norm(axis)
    return np.array([[c + x * x * (1 - c), x * y * (1 - c) - z * s_, x * z * (1 - c) + y * s_],
                     [y * x * (1 - c) + z * s_, c + y * y * (1 - c), y * z * (1 - c) - x * s_],
                     [z * x * (1 - c) - y * s_, z * y * (1 - c) + x * s_, c + z * z * (1 - c)]])


def turned(centre, *rotations):
    """A move: turn about {centre} by the rotations (matrices), the first applied first."""
    m = np.eye(3)
    for r in rotations:
        m = r @ m
    c = np.array(centre, float)

    def move(p):
        return tuple(m @ (np.array(p, float) - c) + c)
    return move


def gem(d, cx, cy, cz, size, tex="c"):
    """A cube stood on its point (a gem), its middle at cx, cy, cz."""
    h = size / 2
    tilt = math.degrees(math.atan(1 / math.sqrt(2)))
    d.box([cx - h, cy - h, cz - h], [cx + h, cy + h, cz + h], tex, "stretch", tint=True,
          move=turned((cx, cy, cz), rot((0, 1, 0), 45), rot((1, 0, 0), tilt)))


def ring(d, cx, cy, cz, radius, tilt_axis, tilt, spin=0, bars=12, thick=1.5, tex="c"):
    """A thin ring of {bars} bars round cx, cy, cz, tilted (it turns in the game)."""
    side = 2 * radius * math.tan(math.pi / bars) + 0.4
    for i in range(bars):
        ang = 360 / bars * i + spin
        frm = [cx - side / 2, cy - thick / 2, cz + radius - thick / 2]
        to = [cx + side / 2, cy + thick / 2, cz + radius + thick / 2]
        d.box(frm, to, tex, "stretch", tint=True, move=turned((cx, cy, cz), rot((0, 1, 0), ang), rot(tilt_axis, tilt)))


def shrine():
    """Walls at the back and the sides, open in front: you walk up the steps to the pedestal in the middle; over the walls
    a gem floats in two turning rings, seen from all round. Four pillars carry a ring beam with runes and a pointed roof."""
    d = Design()
    # The plinth: three steps
    d.box([0, 0, 0], [80, 3, 80], "r", "trim", "t")
    d.box([3, 3, 3], [77, 6, 77], "s", "stone", "t")
    d.box([6, 6, 6], [74, 9, 74], "r", "trim", "t")
    # Four pillars with collars
    for cx in (13, 67):
        for cz in (13, 67):
            d.box([cx - 6, 9, cz - 6], [cx + 6, 13, cz + 6], "r", "trim", "t")
            d.box([cx - 4, 13, cz - 4], [cx + 4, 86, cz + 4], "s", "stone", faces=SIDES)
            d.box([cx - 5, 52, cz - 5], [cx + 5, 55, cz + 5], "r", "trim", "t")
            d.box([cx - 5, 86, cz - 5], [cx + 5, 89, cz + 5], "r", "trim", "t", faces=FACES)
    # Walls at the back and the sides (open in front): two rows of runes inside and out, a ledge on top
    walls = [([17, 9, 64], [63, 52, 70], "south"), ([10, 9, 17], [16, 52, 63], "west"), ([64, 9, 17], [70, 52, 63], "east")]
    for frm, to, outside in walls:
        d.box(frm, to, "s", "stone", faces=SIDES)
        d.box([frm[0] - 1, 52, frm[2] - 1], [to[0] + 1, 55, to[2] + 1], "r", "trim", "t")
        for u in (28, 40, 52):
            for y0 in (16, 32):
                wall_panel(d, frm, to, u, y0, outside, True)
                wall_panel(d, frm, to, u, y0, outside, False)
    # The ring beam on the pillars, runes along it, a cornice
    d.box([8, 89, 8], [72, 91, 72], "r", "trim", faces=SIDES + ("down",))
    for frm, to in (([8, 91, 8], [72, 97, 18]), ([8, 91, 62], [72, 97, 72]), ([8, 91, 18], [18, 97, 62]), ([62, 91, 18], [72, 97, 62])):
        d.box(frm, to, "s", "stone", faces=FACES)
    d.box([6, 97, 6], [74, 99, 74], "r", "trim", "t")
    for i in range(5):
        for face in SIDES:
            frieze(d, 18 + i * 10, face, y0=92.5, inset=8)
    # The roof, pointed, and its crystal
    y = 99
    for w, h, tex in ((58, 5, "s"), (48, 4, "r"), (40, 5, "s"), (32, 4, "r"), (24, 5, "s"), (16, 4, "r"), (9, 4, "s")):
        d.box([40 - w / 2, y, 40 - w / 2], [40 + w / 2, y + h, 40 + w / 2], tex, "stone" if tex == "s" else "trim", "t")
        y += h
    crystal(d, 40, y, 40, [(6, 4), (4, 5), (2, 6)], "c")
    # The pedestal: as the waystone's altar, larger; a pool on it
    d.box([28, 9, 28], [52, 12, 52], "r", "trim", "t")
    d.box([30, 12, 30], [50, 14, 50], "s", "stone", "t")
    d.box([34, 14, 34], [46, 28, 46], "s", "stone", faces=SIDES)
    for face in SIDES:
        rune_panel(d, 40, 40, 6, 15, face, height=12)
    d.box([32, 28, 32], [48, 30, 48], "r", "trim", faces=SIDES + ("down", "up"))
    d.box([30, 30, 30], [50, 34, 50], "s", "stone", "b")
    d.plane([34, 34.02, 34], [46, 34.02, 46], "up", "w", [1, 1, 15, 15], True)
    # Over it, above the walls: a gem in two turning rings
    gem(d, 40, 66, 40, 12)
    ring(d, 40, 66, 40, 15, (1, 0, 0), 24, spin=8, bars=20, thick=1.2)
    ring(d, 40, 66, 40, 19, (0, 0, 1), -30, spin=0, bars=24, thick=1.2)
    return d


def wall_panel(d, frm, to, u, y0, outside, out):
    """A rune panel on a low wall, on its outside or its inside."""
    y1 = y0 + 13
    win, glyph = list(PANEL), [6, 3, 10, 14]
    off, g = 0.01, 0.03
    if outside in ("south",):
        z = to[2] + off if out else frm[2] - off
        zg = to[2] + g if out else frm[2] - g
        face = "south" if out else "north"
        d.plane([u - 3, y0, z], [u + 3, y1, z], face, "p", win)
        d.plane([u - 2, y0 + 1, zg], [u + 2, y1 - 1, zg], face, "g", glyph, True)
    else:
        west = outside == "west"
        lo, hi = frm[0], to[0]
        x = (lo - off if out else hi + off) if west else (hi + off if out else lo - off)
        xg = (lo - g if out else hi + g) if west else (hi + g if out else lo - g)
        face = ("west" if out else "east") if west else ("east" if out else "west")
        d.plane([x, y0, u - 3], [x, y1, u + 3], face, "p", win)
        d.plane([xg, y0 + 1, u - 2], [xg, y1 - 1, u + 2], face, "g", glyph, True)


def prism(d, cx, cy, cz, w, h, tips, rotations, tex="c"):
    """A crystal: a square prism turned 45 degrees (an edge to the front), pointed at both ends ({tips}: (width, height)
    layers outwards from the body), turned as a whole by {rotations} about its middle cx, cy, cz."""
    move = turned((cx, cy, cz), rot((0, 1, 0), 45), *rotations)
    layers = [(w, h)]
    y_top, y_bot = cy + h / 2, cy - h / 2
    d.box([cx - w / 2, y_bot, cz - w / 2], [cx + w / 2, y_top, cz + w / 2], tex, "stretch", tint=True, move=move)
    for tw, th in tips:
        d.box([cx - tw / 2, y_top, cz - tw / 2], [cx + tw / 2, y_top + th, cz + tw / 2], tex, "stretch", tint=True, move=move)
        d.box([cx - tw / 2, y_bot - th * 0.7, cz - tw / 2], [cx + tw / 2, y_bot, cz + tw / 2], tex, "stretch", tint=True, move=move)
        y_top += th
        y_bot -= th * 0.7


def cluster(d, cx, cy, cz):
    """The heart: a cluster of crystals floating over the pedestal, a tall one in the middle, five round it leaning out."""
    prism(d, cx, cy, cz, 6, 14, [(4, 4), (2, 4)], [])
    for i, (ang, lean, size, dy) in enumerate(((0, 28, 1.0, -3), (72, 34, 0.8, -1), (144, 26, 0.9, -4), (216, 32, 0.75, -2), (288, 30, 0.85, -3))):
        a = math.radians(ang)
        ox, oz = math.sin(a) * 4, math.cos(a) * 4
        w, h = 4 * size, 9 * size
        prism(d, cx + ox, cy + dy, cz + oz, w, h, [(w * 0.6, 3 * size), (w * 0.3, 3 * size)],
              [rot((math.cos(a), 0, -math.sin(a)), lean)])


def shrine4():
    """Version 4: depth and more than one stone. Pillars on two-step bases with collars and carved capitals; walls with a
    polished foot, pilasters between recessed rune panels and a coping that overhangs; an entablature in three layers; a
    steep roof of a darker stone with crystal finials; steps up to the open front; a crystal cluster over the pedestal."""
    d = Design()
    # The plinth: a polished foot all round, the platform of bricks with a band, steps up in front
    d.box([0, 0, 0], [80, 4, 80], "@A", "stone", "@A")
    d.box([2, 4, 6], [78, 8, 78], "@B", "stone", "@M")
    d.box([1, 8, 5], [79, 10, 79], "r", "trim", "@M")
    for i, (z0, y1) in enumerate(((0.5, 6), (2.5, 8), (4.5, 10))):
        d.box([22, 4, z0], [58, y1, 6], "@A", "stone", "@M")
    # Floor: smooth stone with a carved inlay round the pedestal
    d.box([24, 10, 24], [56, 10.4, 56], "@C", "stone", "@C", faces=("up",))
    # Pillars
    for cx in (14, 66):
        for cz in (14, 66):
            d.box([cx - 7, 10, cz - 7], [cx + 7, 13, cz + 7], "@A", "stone", "@A")
            d.box([cx - 6, 13, cz - 6], [cx + 6, 15, cz + 6], "r", "trim", "t")
            d.box([cx - 5, 15, cz - 5], [cx + 5, 76, cz + 5], "s", "stone", faces=SIDES)
            # carved corners: a darker strip down each corner of the shaft
            for sx in (-1, 1):
                for sz in (-1, 1):
                    x0, z0 = (cx + 4 if sx > 0 else cx - 5), (cz + 4 if sz > 0 else cz - 5)
                    d.box([x0 - 0.01, 18, z0 - 0.01], [x0 + 1.01, 73, z0 + 1.01], "r", "stone", faces=SIDES)
            d.box([cx - 6, 46, cz - 6], [cx + 6, 49, cz + 6], "r", "trim", "t")
            d.box([cx - 6, 76, cz - 6], [cx + 6, 78, cz + 6], "r", "trim", "t")
            d.box([cx - 7, 78, cz - 7], [cx + 7, 82, cz + 7], "@C", "stone", "@M")
    # Walls at the back and the sides: a polished foot, recessed rune panels between pilasters, a coping that overhangs
    for wall in (("south", [21, 59], 63, 69), ("west", [21, 59], 11, 17), ("east", [21, 59], 63, 69)):
        side, (a, b), lo, hi = wall
        def B(x0, y0, x1, y1, inner, outer, tex, kind="stone", top=None, faces=FACES):
            if side == "south":
                d.box([x0, y0, lo - inner], [x1, y1, hi + outer], tex, kind, top, faces=faces)
            elif side == "west":
                d.box([lo - outer, y0, x0], [hi + inner, y1, x1], tex, kind, top, faces=faces)
            else:
                d.box([lo - inner, y0, x0], [hi + outer, y1, x1], tex, kind, top, faces=faces)
        B(a, 10, b, 15, 1, 1, "@A", "stone", "@A")
        B(a, 15, b, 44, 0, 0, "s", "stone", faces=SIDES)
        for u in (a, 33.5, 44.5, b - 3):
            B(u, 15, u + 3, 44, 1, 1, "@B", "stone", faces=SIDES)
        B(a, 44, b, 46, 1.5, 1.5, "r", "trim", "t")
        B(a - 1, 46, b + 1, 49, 2, 2, "@M", "stone", "@M")
        for u in (28, 40, 51.5):
            for y0 in (16.5, 30):
                wall_panel4(d, side, lo, hi, u, y0)
    # The entablature: architrave, a frieze of runes set back, a cornice in two overhangs
    for frm, to in (([7, 82, 7], [73, 85, 21]), ([7, 82, 59], [73, 85, 73]), ([7, 82, 21], [21, 85, 59]), ([59, 82, 21], [73, 85, 59])):
        d.box(frm, to, "r", "trim", "t")
    for frm, to in (([8, 85, 8], [72, 92, 20]), ([8, 85, 60], [72, 92, 72]), ([8, 85, 20], [20, 92, 60]), ([60, 85, 20], [72, 92, 60])):
        d.box(frm, to, "@B", "stone", faces=FACES)
    for i in range(6):
        for face in SIDES:
            frieze(d, 13 + i * 10, face, y0=87, inset=8)
    d.box([6, 92, 6], [74, 94, 74], "r", "trim", "t")
    d.box([4, 94, 4], [76, 96, 76], "@M", "stone", "@M")
    # The roof: steep, of the darker stone, a ridge of trim near the top; crystal finials on the corners and the top
    y, w = 96, 66
    while w > 8:
        d.box([40 - w / 2, y, 40 - w / 2], [40 + w / 2, y + 3, 40 + w / 2], "@R", "stone", "@R")
        y += 3
        w -= 6
    d.box([36, y, 36], [44, y + 3, 44], "r", "trim", "t")
    crystal(d, 40, y + 3, 40, [(6, 4), (4, 5), (2, 7)], "c")
    for cx in (9, 71):
        for cz in (9, 71):
            d.box([cx - 3, 96, cz - 3], [cx + 3, 99, cz + 3], "@C", "stone", "@M")
            crystal(d, cx, 99, cz, [(3, 2), (2, 3), (1, 3)], "c")
    # The pedestal: polished foot, a column with runes, a basin with a pool
    d.box([29, 10, 29], [51, 13, 51], "@A", "stone", "@A")
    d.box([31, 13, 31], [49, 15, 49], "r", "trim", "t")
    d.box([34, 15, 34], [46, 28, 46], "s", "stone", faces=SIDES)
    for face in SIDES:
        rune_panel(d, 40, 40, 6, 15.5, face, height=12)
    d.box([32, 28, 32], [48, 30, 48], "r", "trim", faces=SIDES + ("down", "up"))
    d.box([30, 30, 30], [50, 34, 50], "@C", "stone", "b")
    d.plane([34, 34.02, 34], [46, 34.02, 46], "up", "w", [1, 1, 15, 15], True)
    # Over it: the crystal cluster in a turning ring
    cluster(d, 40, 62, 40)
    ring(d, 40, 62, 40, 17, (1, 0, 0.4), 18, spin=6, bars=24, thick=1.0)
    return d


def sprite(d, cx, cy, cz, w, h, tex, planes=4, spin=0):
    """Pixel art on {planes} crossed upright planes round cx, cz, its middle at cy (w x h pixels, one per texel)."""
    for i in range(planes):
        a = math.radians(spin + 180 / planes * i)
        dx, dz = math.cos(a) * w / 2, math.sin(a) * w / 2
        top, bot = cy + h / 2, cy - h / 2
        d.quad([(cx - dx, top, cz - dz), (cx + dx, top, cz + dz), (cx + dx, bot, cz + dz), (cx - dx, bot, cz - dz)], tex, [0, 0, w, h])


def heart_sprite(d, cx, cy, cz):
    """The heart: the crystal on four crossed planes, three shards circling round it."""
    sprite(d, cx, cy, cz, 15, 38, "x:crystal", planes=4, spin=10)
    for i, (ang, dy) in enumerate(((20, 4), (140, -6), (260, 10))):
        a = math.radians(ang)
        sprite(d, cx + math.cos(a) * 15, cy + dy, cz + math.sin(a) * 15, 7, 15, "x:shard", planes=2, spin=ang)


def arch(d, side):
    """An arch between two pillars on one side: a band of trim along its curve, bricks above, a carved keystone."""
    for x in range(19, 61, 2):
        t = (x + 1 - 40) / 21
        yi = 60 + 18 * math.sqrt(max(0.0, 1 - t * t))
        yi = math.floor(yi)
        parts = (([x, yi, 11], [x + 2, yi + 2, 17], "r", "trim"), ([x, yi + 2, 11], [x + 2, 82, 17], "@B", "stone"))
        for frm, to, tex, kind in parts:
            if to[1] <= frm[1]:
                continue
            d.box(*along(side, frm, to), tex, kind, faces=SIDES + ("down",))
    d.box(*along(side, [37, 74, 10.5], [43, 82, 17.5]), "@C", "stone", faces=SIDES + ("down",))
    gx = along(side, [38.5, 76, 10.47], [41.5, 80, 10.47])
    # a rune on the keystone, outside
    if side == "north":
        d.plane([38, 76, 10.45], [42, 79, 10.45], "north", "g", [6, 7, 10, 10], True)
    elif side == "south":
        d.plane([38, 76, 69.55], [42, 79, 69.55], "south", "g", [6, 7, 10, 10], True)
    elif side == "west":
        d.plane([10.45, 76, 38], [10.45, 79, 42], "west", "g", [6, 7, 10, 10], True)
    else:
        d.plane([69.55, 76, 38], [69.55, 79, 42], "east", "g", [6, 7, 10, 10], True)


def along(side, frm, to):
    """A box drawn for the north side (x along it, z 0..20 the depth), placed on {side} (turned about the middle)."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    if side == "north":
        return [x0, y0, z0], [x1, y1, z1]
    if side == "south":
        return [80 - x1, y0, 80 - z1], [80 - x0, y1, 80 - z0]
    if side == "west":
        return [z0, y0, 80 - x1], [z1, y1, 80 - x0]
    return [80 - z1, y0, x0], [80 - z0, y1, x1]


def balustrade(d, side, gap=None):
    """A low balustrade between two pillars: a foot, small balusters, a rail on top ({gap}: an opening along it)."""
    spans = [(19, 61)] if gap is None else [(19, gap[0]), (gap[1], 61)]
    for a, b in spans:
        d.box(*along(side, [a, 10, 11.5], [b, 12, 16.5]), "@A", "stone", "@A")
        d.box(*along(side, [a, 24, 11], [b, 27, 17]), "@M", "stone", "@M")
        d.box(*along(side, [a, 23, 12], [b, 24, 16]), "r", "trim", faces=SIDES + ("down",))
        x = a + 2
        while x + 2 <= b - 1:
            d.box(*along(side, [x, 12, 13], [x + 2, 23, 15]), "@M", "stone", faces=SIDES)
            x += 4
        # end posts
        for e in (a, b - 3):
            d.box(*along(side, [e, 12, 12], [e + 3, 24, 16]), "@B", "stone", faces=SIDES)


def shrine5():
    """Version 5: open on every side, with arches between the pillars and a low balustrade (an opening in front for the
    steps), runes on the pillars and the keystones; the heart a pixel-art crystal on crossed planes with shards round it."""
    d = Design()
    d.box([0, 0, 0], [80, 4, 80], "@A", "stone", "@A")
    d.box([2, 4, 6], [78, 8, 78], "@B", "stone", "@M")
    d.box([1, 8, 5], [79, 10, 79], "r", "trim", "@M")
    for z0, y1 in ((0.5, 6), (2.5, 8), (4.5, 10)):
        d.box([28, 4, z0], [52, y1, 6], "@A", "stone", "@M")
    d.box([24, 10, 24], [56, 10.4, 56], "@C", "stone", "@C", faces=("up",))
    for cx in (14, 66):
        for cz in (14, 66):
            d.box([cx - 7, 10, cz - 7], [cx + 7, 13, cz + 7], "@A", "stone", "@A")
            d.box([cx - 6, 13, cz - 6], [cx + 6, 15, cz + 6], "r", "trim", "t")
            d.box([cx - 5, 15, cz - 5], [cx + 5, 78, cz + 5], "s", "stone", faces=SIDES)
            for sx in (-1, 1):
                for sz in (-1, 1):
                    x0, z0 = (cx + 4 if sx > 0 else cx - 5), (cz + 4 if sz > 0 else cz - 5)
                    d.box([x0 - 0.01, 18, z0 - 0.01], [x0 + 1.01, 75, z0 + 1.01], "r", "stone", faces=SIDES)
            for face in SIDES:
                rune_panel(d, cx, cz, 5, 34, face)
            d.box([cx - 6, 56, cz - 6], [cx + 6, 59, cz + 6], "r", "trim", "t")
            d.box([cx - 6, 78, cz - 6], [cx + 6, 80, cz + 6], "r", "trim", "t")
            d.box([cx - 7, 80, cz - 7], [cx + 7, 82, cz + 7], "@C", "stone", "@M")
    for side in ("north", "south", "west", "east"):
        arch(d, side)
        balustrade(d, side, gap=(30, 50) if side == "north" else None)
    for frm, to in (([7, 82, 7], [73, 85, 21]), ([7, 82, 59], [73, 85, 73]), ([7, 82, 21], [21, 85, 59]), ([59, 82, 21], [73, 85, 59])):
        d.box(frm, to, "r", "trim", "t")
    for frm, to in (([8, 85, 8], [72, 92, 20]), ([8, 85, 60], [72, 92, 72]), ([8, 85, 20], [20, 92, 60]), ([60, 85, 20], [72, 92, 60])):
        d.box(frm, to, "@B", "stone", faces=FACES)
    for i in range(6):
        for face in SIDES:
            frieze(d, 13 + i * 10, face, y0=87, inset=8)
    d.box([6, 92, 6], [74, 94, 74], "r", "trim", "t")
    d.box([4, 94, 4], [76, 96, 76], "@M", "stone", "@M")
    y, w = 96, 66
    while w > 8:
        d.box([40 - w / 2, y, 40 - w / 2], [40 + w / 2, y + 3, 40 + w / 2], "@R", "stone", "@R")
        y += 3
        w -= 6
    d.box([36, y, 36], [44, y + 3, 44], "r", "trim", "t")
    sprite(d, 40, y + 3 + 7.5, 40, 7, 15, "x:shard", planes=2, spin=45)
    for cx in (9, 71):
        for cz in (9, 71):
            d.box([cx - 3, 96, cz - 3], [cx + 3, 99, cz + 3], "@C", "stone", "@M")
            sprite(d, cx, 99 + 5.5, cz, 5, 11, "x:shard", planes=2, spin=45)
    d.box([29, 10, 29], [51, 13, 51], "@A", "stone", "@A")
    d.box([31, 13, 31], [49, 15, 49], "r", "trim", "t")
    d.box([34, 15, 34], [46, 28, 46], "s", "stone", faces=SIDES)
    for face in SIDES:
        rune_panel(d, 40, 40, 6, 15.5, face, height=12)
    d.box([32, 28, 32], [48, 30, 48], "r", "trim", faces=SIDES + ("down", "up"))
    d.box([30, 30, 30], [50, 34, 50], "@C", "stone", "b")
    d.plane([34, 34.02, 34], [46, 34.02, 46], "up", "w", [1, 1, 15, 15], True)
    heart_sprite(d, 40, 60, 40)
    return d


def wall_panel4(d, side, lo, hi, u, y0):
    """A rune panel on both faces of a wall (between its pilasters)."""
    y1 = y0 + 13
    win, glyph = list(PANEL), [6, 3, 10, 14]
    off, g = 0.01, 0.03
    if side == "south":
        for z, zg, face in ((hi + off, hi + g, "south"), (lo - off, lo - g, "north")):
            d.plane([u - 3, y0, z], [u + 3, y1, z], face, "p", win)
            d.plane([u - 2, y0 + 1, zg], [u + 2, y1 - 1, zg], face, "g", glyph, True)
    else:
        for x, xg, face in ((lo - off, lo - g, "west"), (hi + off, hi + g, "east")):
            d.plane([x, y0, u - 3], [x, y1, u + 3], face, "p", win)
            d.plane([xg, y0 + 1, u - 2], [xg, y1 - 1, u + 2], face, "g", glyph, True)


def body_panel(d, u, y0, face, half=24):
    """A rune panel (6 x 13) with its runes, centred at {u} along one side of a block of half-width {half} round 40, 40."""
    y1 = y0 + 13
    off, g = 0.01, 0.03
    lo, hi = 40 - half, 40 + half
    win, glyph = list(PANEL), [6, 3, 10, 14]
    if face == "north":
        d.plane([u - 3, y0, lo - off], [u + 3, y1, lo - off], "north", "p", win)
        d.plane([u - 2, y0 + 1, lo - g], [u + 2, y1 - 1, lo - g], "north", "g", glyph, True)
    elif face == "south":
        d.plane([u - 3, y0, hi + off], [u + 3, y1, hi + off], "south", "p", win)
        d.plane([u - 2, y0 + 1, hi + g], [u + 2, y1 - 1, hi + g], "south", "g", glyph, True)
    elif face == "west":
        d.plane([lo - off, y0, u - 3], [lo - off, y1, u + 3], "west", "p", win)
        d.plane([lo - g, y0 + 1, u - 2], [lo - g, y1 - 1, u + 2], "west", "g", glyph, True)
    else:
        d.plane([hi + off, y0, u - 3], [hi + off, y1, u + 3], "east", "p", win)
        d.plane([hi + g, y0 + 1, u - 2], [hi + g, y1 - 1, u + 2], "east", "g", glyph, True)


def rune_strip(d, cx, cz, half, y0, y1, face):
    """A thin line of runes (2 wide) up one side of a little obelisk."""
    g = 0.02
    glyph = [7, 3, 9, 14]
    if face == "north":
        d.plane([cx - 1, y0, cz - half - g], [cx + 1, y1, cz - half - g], "north", "g", glyph, True)
    elif face == "south":
        d.plane([cx - 1, y0, cz + half + g], [cx + 1, y1, cz + half + g], "south", "g", glyph, True)
    elif face == "west":
        d.plane([cx - half - g, y0, cz - 1], [cx - half - g, y1, cz + 1], "west", "g", glyph, True)
    else:
        d.plane([cx + half + g, y0, cz - 1], [cx + half + g, y1, cz + 1], "east", "g", glyph, True)


def rune_panel(d, cx, cz, half, y0, face, height=13):
    """An inset rune panel (6 wide) on one side of a square shaft of half-width {half}, from y0, with its runes over it."""
    y1 = y0 + height
    win = [PANEL[0], 15 - height, PANEL[2], 15] if height < 13 else list(PANEL)
    glyph = [6, 16 - height, 10, 16] if height < 13 else [6, 3, 10, 14]
    off, g = 0.01, 0.03
    if face == "north":
        d.plane([cx - 3, y0, cz - half - off], [cx + 3, y1, cz - half - off], "north", "p", win)
        d.plane([cx - 2, y0 + 1, cz - half - g], [cx + 2, y1 - 1, cz - half - g], "north", "g", glyph, True)
    elif face == "south":
        d.plane([cx - 3, y0, cz + half + off], [cx + 3, y1, cz + half + off], "south", "p", win)
        d.plane([cx - 2, y0 + 1, cz + half + g], [cx + 2, y1 - 1, cz + half + g], "south", "g", glyph, True)
    elif face == "west":
        d.plane([cx - half - off, y0, cz - 3], [cx - half - off, y1, cz + 3], "west", "p", win)
        d.plane([cx - half - g, y0 + 1, cz - 2], [cx - half - g, y1 - 1, cz + 2], "west", "g", glyph, True)
    else:
        d.plane([cx + half + off, y0, cz - 3], [cx + half + off, y1, cz + 3], "east", "p", win)
        d.plane([cx + half + g, y0 + 1, cz - 2], [cx + half + g, y1 - 1, cz + 2], "east", "g", glyph, True)


def frieze(d, u, face, y0=111.5, inset=8):
    """One rune (4 x 3) on the outside of the ring beam, {u} along it."""
    win = [6, 7, 10, 10]
    y1 = y0 + 3
    lo, hi = inset - 0.03, 80 - inset + 0.03
    if face == "north":
        d.plane([u, y0, lo], [u + 4, y1, lo], "north", "g", win, True)
    elif face == "south":
        d.plane([80 - u - 4, y0, hi], [80 - u, y1, hi], "south", "g", win, True)
    elif face == "west":
        d.plane([lo, y0, u], [lo, y1, u + 4], "west", "g", win, True)
    else:
        d.plane([hi, y0, 80 - u - 4], [hi, y1, 80 - u], "east", "g", win, True)


def crystal(d, cx, y, cz, layers, tex):
    """A crystal of stacked square layers (width, height) from y up, centred on cx, cz; tinted, the texture over each face."""
    for layer in layers:
        w, h = layer[0], layer[1]
        t = layer[2] if len(layer) > 2 else tex
        d.box([cx - w / 2, y, cz - w / 2], [cx + w / 2, y + h, cz + w / 2], t, "stretch", tint=True)
        y += h


# ---------------------------------------------------------------- faces, cut on the 16-pixel grid

def corners(face, a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def cuts(lo, hi, grid=16):
    out, a = [], lo
    while a < hi - 1e-9:
        b = min(hi, (math.floor(a / grid + 1e-9) + 1) * grid)
        out.append((a, b))
        a = b
    return out


def faces_of(b):
    """Every face of a design part, cut on the grid, with its texture window."""
    out = []
    if b["kind"] == "quad":
        out.append(dict(face="quad", pts=b["pts"], tex=b["tex"], uv=b["uv"], tint=b["tint"], shade=False))
        out.append(dict(face="quad", pts=[b["pts"][1], b["pts"][0], b["pts"][3], b["pts"][2]], tex=b["tex"],
                        uv=[b["uv"][2], b["uv"][1], b["uv"][0], b["uv"][3]], tint=b["tint"], shade=False))
        return out
    frm, to = b["frm"], b["to"]
    if b["plane"]:
        out.append(dict(face=b["plane"], a=frm, b=to, tex=b["tex"], uv=b["uv"], tint=b["tint"], shade=False))
        return out
    for face in b["faces"]:
        a, z = list(frm), list(to)
        if face in ("north", "south"):
            fixed = 2
        elif face in ("east", "west"):
            fixed = 0
        else:
            fixed = 1
        if face in ("north", "west", "down"):
            z[fixed] = a[fixed]
        else:
            a[fixed] = z[fixed]
        axes = [i for i in range(3) if i != fixed]
        if b["kind"] == "stretch":
            out.append(dict(face=face, a=a, b=z, tex=b["tex"], uv=[0, 0, 16, 16], tint=b["tint"], shade=False))
            continue
        for p0, p1 in cuts(a[axes[0]], z[axes[0]]):
            for q0, q1 in cuts(a[axes[1]], z[axes[1]]):
                aa, zz = list(a), list(z)
                aa[axes[0]], zz[axes[0]], aa[axes[1]], zz[axes[1]] = p0, p1, q0, q1
                tex = b["top"] if (face == "up" and b["top"]) else b["tex"]
                out.append(dict(face=face, a=aa, b=zz, tex=tex, uv=window(face, aa, zz, b["kind"] if tex == b["tex"] else "top"),
                                tint=b["tint"], shade=True))
    return out


def local(v):
    m = v % 16
    return 16.0 if (m < 1e-9 and v > 0) else m


def window(face, a, z, kind):
    x0, y0, z0 = a
    x1, y1, z1 = z
    bx = math.floor(min(x0, x1) / 16 + 1e-9) * 16
    by = math.floor(min(y0, y1) / 16 + 1e-9) * 16
    bz = math.floor(min(z0, z1) / 16 + 1e-9) * 16
    lx0, lx1, ly0, ly1, lz0, lz1 = x0 - bx, x1 - bx, y0 - by, y1 - by, z0 - bz, z1 - bz
    if face in ("up", "down"):
        return [lx0, lz0, lx1, lz1]
    h = ly1 - ly0
    if face in ("north", "south"):
        w0, w1 = (16 - lx1, 16 - lx0) if face == "north" else (lx0, lx1)
    else:
        w0, w1 = (lz0, lz1) if face == "west" else (16 - lz1, 16 - lz0)
    if kind == "trim":
        v0 = 0 if h >= 2 else 1
        return [w0, v0, w1, v0 + h]
    return [w0, 16 - ly1, w1, 16 - ly0]


# ---------------------------------------------------------------- preview

VANILLA = "/home/claude/ref/assets/assets/minecraft/textures/block"
# The other stones each look uses (vanilla's own textures, the client has them): A a polished stone for bases and
# steps, B bricks, C a carved stone, M a smooth stone for floors and caps, R the roof
PALETTE = {
    "light": {"A": "polished_andesite", "B": "stone_bricks", "C": "chiseled_stone_bricks", "M": "smooth_stone", "R": "deepslate_tiles"},
    "dark": {"A": "polished_deepslate", "B": "deepslate_bricks", "C": "chiseled_deepslate", "M": "polished_blackstone", "R": "oxidized_cut_copper"},
}


def vanilla(variant, key):
    """The vanilla texture a key stands for ("@A": the palette's, "v:name": that one)."""
    return PALETTE[variant][key[1:]] if key.startswith("@") else key[2:]


class Textures(dict):
    def __init__(self, variant):
        super().__init__()
        self.variant = variant
        names = {"s": "stone", "t": "top", "r": "trim", "p": "panel", "b": "basin"}
        for k, n in names.items():
            self[k] = np.asarray(Image.open(os.path.join(TEX, variant, n + ".png")).convert("RGBA"))
        for k, n in {"g": "glyph", "c": "crystal", "w": "pool", "u": "rune"}.items():
            self[k] = np.asarray(Image.open(os.path.join(TEX, "shine", n + ".png")).convert("RGBA"))

    def __missing__(self, key):
        if key.startswith("x:"):
            import heart
            arr = np.asarray(getattr(heart, key[2:])())
            self[key] = arr
            return arr
        im = Image.open(os.path.join(VANILLA, vanilla(self.variant, key) + ".png")).convert("RGBA")
        arr = np.asarray(im)[:16, :16]
        self[key] = arr
        return arr


def textures(variant):
    return Textures(variant)


def preview_faces(design, variant, tint, at=(0, 0, 0)):
    tx = textures(variant)
    out = []
    for b in design.boxes:
        for f in faces_of(b):
            pts = f["pts"] if f["face"] == "quad" else corners(f["face"], f["a"], f["b"])
            if b.get("move"):
                pts = [b["move"](p) for p in pts]
            cs = [((p[0] + at[0]) / 16, (p[1] + at[1]) / 16, (p[2] + at[2]) / 16) for p in pts]
            out.append(dict(corners=cs, tex=tx[f["tex"]], uv=f["uv"], shade=SHADE.get(f["face"], 1.0) if f["shade"] else 1.0,
                            tint=tint if f["tint"] else None, glow=bool(f["tint"])))
    return out


def main():
    out = "/home/claude/mock/grand"
    os.makedirs(out, exist_ok=True)
    designs = {"shrine5": shrine5(), "shrine4": shrine4(), "shrine": shrine(), "monument": monument(), "pavilion": grand()}
    only = sys.argv[2] if len(sys.argv) > 2 else None
    for dname, d in designs.items():
        if only and dname != only:
            continue
        for variant, tint in (("light", (199, 155, 240)), ("dark", (95, 242, 224))):
            faces = preview_faces(d, variant, tint, at=(-40, 0, -40))
            for name, eye, night in (("front", (-8.5, 4.5, -11.5), False), ("low", (5.0, 1.7, -7.0), False), ("night", (-8.5, 4.5, -11.5), True),
                                     ("open", (-2.5, 2.6, -9.5), False), ("back", (8.0, 4.0, 10.0), True)):
                cam = preview.Camera(eye, (0, 5.0, 0), fov=60)
                im = preview.render(faces, cam, night=night)
                if night:
                    im = preview.bloom(im)
                im.save(os.path.join(out, f"{dname}_{variant}_{name}.png"))
    print("previews in", out)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "preview":
        main()
