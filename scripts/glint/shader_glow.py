"""Glow maps for shader packs (LabPBR): glint/off/shaderglow.zip, a pack sent only to players who turn on "Glow with
shaders". For every effect texture (tools' texture parts and auras, worn armour's glows and seams) a "<name>_s.png" whose
alpha says how much each pixel lights up (LabPBR: 1-254 = emission, 0 = none). Shader packs that read resource pack PBR
(Complementary, BSL and others, with that option on) then make the effects glow. Without shaders nothing reads these.

Usage: python3 shader_glow.py [glint/src/main/resources]  (generate.py runs it at the end)
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

DIRS = ['assets/libreria/textures/item/glint', 'assets/libreria/textures/item/fx', 'assets/libreria/textures/entity/equipment']


def glow_map(img, armour):
    img = img.convert('RGBA')
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    src, dst = img.load(), out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            if 249 <= a <= 254:
                e = int(254 * (0.7 + 0.3 * lum))      # the pixels that glow without shaders too
            elif armour:
                e = int(30 + 110 * lum)               # seams and marks on worn armour: a soft light
            else:
                e = int(70 + 170 * lum)               # an effect on a tool: it's light, mostly
            # smoothness low, a dull dielectric, emission in alpha
            dst[x, y] = (40, 20, 0, max(1, min(254, e)))
    return out


def build(out_dir, pack_format=97):
    path = os.path.join(out_dir, 'glint/off/shaderglow.zip')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    n = 0
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('pack.mcmeta', json.dumps({'pack': {'description': 'Libreria Glint – glow maps for shader packs (LabPBR)',
                                                       'min_format': pack_format, 'max_format': 1000}}))
        for d in DIRS:
            root = os.path.join(out_dir, d)
            if not os.path.isdir(root):
                continue
            for dirpath, _, files in sorted(os.walk(root)):
                for f in sorted(files):
                    if not f.endswith('.png') or f.endswith('_s.png') or f.endswith('_n.png'):
                        continue
                    full = os.path.join(dirpath, f)
                    rel = os.path.relpath(full, out_dir).replace(os.sep, '/')
                    buf = io.BytesIO()
                    glow_map(Image.open(full), 'entity/equipment' in rel).save(buf, 'PNG', optimize=True)
                    z.writestr(rel[:-4] + '_s.png', buf.getvalue())
                    if os.path.exists(full + '.mcmeta'):
                        z.write(full + '.mcmeta', rel[:-4] + '_s.png.mcmeta')
                    n += 1
    return n, os.path.getsize(path)


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources')
    print('%d glow maps, %d bytes' % build(out))
