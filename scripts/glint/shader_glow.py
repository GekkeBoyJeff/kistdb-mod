"""Glow maps for shader packs (LabPBR), in the main resource pack: for every effect texture (tools' texture parts and
auras, worn armour's glows and seams) a "<name>_s.png" next to it, whose alpha says how much each pixel lights up
(LabPBR: 1-254 = emission, 0 = none): the same pixels that light up without shaders. Shader packs that read resource
pack PBR (Complementary: IPBR+ Emissive Mode "labPBR > IPBR+" or RP Support labPBR; BSL: Advanced Materials) make the
effects glow. Without shaders nothing reads them, and an atlas filter keeps them out of the item atlas (the game
would otherwise stitch them in as sprites of their own).

Usage: python3 shader_glow.py [glint/src/main/resources]  (generate.py runs it at the end)
"""
import shutil
import json
import math
import os
import sys

from PIL import Image

DIRS = ['assets/libreria/textures/item/glint', 'assets/libreria/textures/item/fx', 'assets/libreria/textures/entity/equipment']


def glow_map(img, armour):
    """What glows with shaders is what glows without them: the pixels marked 249-254 (the core shaders light those up),
    and the auras and glow planes (alpha below 255: they light themselves). Pixels at 255 are painted parts, lit like
    the item they're on (a gold hilt from Looting): no light of their own."""
    img = img.convert('RGBA')
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    src, dst = img.load(), out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            if a == 255:
                e = 0                                 # painted, lit like the item: no light of its own
            elif 249 <= a <= 254:
                e = int(254 * (0.75 + 0.25 * lum))    # the pixels that glow without shaders
            else:
                e = int(110 + 144 * lum)              # an aura's pixel (alpha 17-241): it lights itself
            # smoothness low, a dull dielectric, emission in alpha (0: none)
            dst[x, y] = (40, 20, 0, max(0, min(254, e)))
    return out


# A static texture part with moving glow (the codes the core shaders animate) gets a moving map: FRAMES frames of
# FRAMETIME ticks (a 48-tick loop). Shader packs animate a map on its own, so the light pulses, flickers, runs along
# and twinkles with shaders too (the colour shift of 249 can't: a map only says how bright).
FRAMES, FRAMETIME = 16, 3
MOVING = (250, 251, 252, 253)


def _hash(x, y, k=0):
    v = math.sin(x * 12.9898 + y * 78.233 + k * 37.719) * 43758.5453
    return v - math.floor(v)


def brightness(code, x, y, k):
    """How bright a pixel with this code is in frame k (0..FRAMES-1), as the core shaders do it: 0.27..1."""
    t = k / FRAMES
    h = _hash(x, y)
    if code == 253:      # pulse
        b = 0.62 + 0.38 * math.sin(2 * math.pi * t + h * 2 * math.pi)
    elif code == 252:    # flicker (flames)
        b = 0.55 + 0.45 * _hash(x, y, k + 1)
    elif code == 251:    # flow: a glint running along the edge (twice a loop)
        b = 0.25 + 0.75 * (0.5 + 0.5 * math.sin((x - y) * 0.55 - 4 * math.pi * t)) ** 6
    elif code == 250:    # twinkle
        b = 0.3 + 0.7 * max(0.0, math.sin(2 * math.pi * t + h * 40)) ** 10
    else:
        b = 1.0
    return (0.35 + 0.95 * b) / 1.3


def moving_map(img):
    """The frames of a moving map (one under the other), or None when nothing on it moves."""
    img = img.convert('RGBA')
    src = img.load()
    if not any(src[x, y][3] in MOVING for y in range(img.height) for x in range(img.width)):
        return None
    w, h = img.size
    out = Image.new('RGBA', (w, h * FRAMES), (0, 0, 0, 0))
    dst = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a == 0 or a == 255:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            e0 = 254 * (0.75 + 0.25 * lum) if a >= 249 else 110 + 144 * lum
            for k in range(FRAMES):
                e = e0 * brightness(a, x, y, k)
                # in steps of 8: the frames pack much smaller, and nobody sees the difference
                dst[x, y + k * h] = (40, 20, 0, max(1, min(254, int(round(e / 8) * 8))))
    return out


def build(out_dir, pack_format=None):
    n = size = 0
    moving = [0]
    for d in DIRS:
        root = os.path.join(out_dir, d)
        if not os.path.isdir(root):
            continue
        # the maps of an earlier run go first (a texture that no longer glows keeps none)
        for dirpath, _, files in os.walk(root):
            for f in files:
                if f.endswith('_s.png') or f.endswith('_s.png.mcmeta'):
                    os.remove(os.path.join(dirpath, f))
        for dirpath, _, files in sorted(os.walk(root)):
            for f in sorted(files):
                if not f.endswith('.png') or f.endswith('_n.png'):
                    continue
                full = os.path.join(dirpath, f)
                dst = full[:-4] + '_s.png'
                # a static item texture whose glow moves: a moving map (worn armour isn't in an atlas, and a shader
                # pack doesn't animate maps there)
                if '/item/' in full.replace(os.sep, '/') and not os.path.exists(full + '.mcmeta'):
                    frames = moving_map(Image.open(full))
                    if frames is not None:
                        frames.save(dst, 'PNG', optimize=True)
                        with open(dst + '.mcmeta', 'w') as fh:
                            json.dump({'animation': {'frametime': FRAMETIME}}, fh)
                        size += os.path.getsize(dst)
                        n += 1
                        moving[0] += 1
                        continue
                glow = glow_map(Image.open(full), 'entity/equipment' in full.replace(os.sep, '/'))
                if not glow.getchannel('A').getbbox():
                    continue                      # nothing on it lights up (a painted part): no map
                glow.save(dst, 'PNG', optimize=True)
                size += os.path.getsize(dst)
                if os.path.exists(full + '.mcmeta'):
                    shutil.copyfile(full + '.mcmeta', dst + '.mcmeta')
                n += 1
    # The item atlas takes every texture under item/, so without this the maps would be sprites of their own
    atlas = os.path.join(out_dir, 'assets/minecraft/atlases/items.json')
    os.makedirs(os.path.dirname(atlas), exist_ok=True)
    with open(atlas, 'w') as fh:
        json.dump({'sources': [{'type': 'minecraft:filter', 'pattern': {'namespace': 'libreria', 'path': '_s$'}}]}, fh)
    old = os.path.join(out_dir, 'glint/off/shaderglow.zip')
    if os.path.exists(old):
        os.remove(old)
    build.moving = moving[0]
    return n, size


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources')
    print('%d glow maps, %d bytes' % build(out), '(%d moving)' % build.moving)
