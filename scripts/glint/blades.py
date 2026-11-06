"""Proof of concept: a sword's effect in two parts. A texture part painted into the sword (core line, guard, gems; its
glowing pixels carry a code in their alpha for the core shader), and an aura: an animated plane behind the sword,
larger than the item so flames can rise past the tip, whose width changes per piece and over time.

In the inventory only the texture part shows; in a hand, on the ground and in a frame the aura is added.

    python3 scripts/glint/blades.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import sword_aura as aura  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources/assets/libreria')
KINDS = {'sharp': ('edge', 5), 'fire': ('fire', 2), 'smite': ('holy', 5), 'bane': ('venom', 5)}
CODE = {'flicker': 252, 'pulse': 253, 'flow': 251, 'steady': 254}
HAND = json.load(open('/home/claude/ref/assets/assets/minecraft/models/item/handheld.json'))['display']
GEN = json.load(open('/home/claude/ref/assets/assets/minecraft/models/item/generated.json'))['display']


def plane_model(texture):
    lo, hi = aura.LO, aura.HI
    face = lambda uv: {'uv': uv, 'texture': '#a'}
    return {'textures': {'a': texture, 'particle': texture},
            'elements': [{'from': [lo, 16 - hi, 8], 'to': [hi, 16 - lo, 8], 'shade': False, 'light_emission': 15,
                          'faces': {'south': face([0, 0, 16, 16]), 'north': face([16, 0, 0, 16])}}],
            'display': {**GEN, **HAND}}


def main():
    tex = os.path.join(ROOT, 'textures/item/fx/blade')
    atex = os.path.join(ROOT, 'textures/item/fx/aura')
    mod = os.path.join(ROOT, 'models/item/fx')
    itm = os.path.join(ROOT, 'items/fx')
    for d in (tex, atex, mod, itm):
        os.makedirs(d, exist_ok=True)
    base = aura.sword()
    for name, (kind, top) in KINDS.items():
        for g in range(1, top + 1):
            painted, glow = aura.paint(base, kind, g)
            over = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
            for y in range(16):
                for x in range(16):
                    p, q = painted.getpixel((x, y)), base.getpixel((x, y))
                    if p != q or (x, y) in glow:
                        over.putpixel((x, y), p[:3] + (CODE[glow[(x, y)]] if (x, y) in glow else 255,))
            over.save(os.path.join(tex, f'{name}_{g}.png'))
            sword = f'libreria:item/fx/sword_{name}_{g}'
            json.dump({'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'minecraft:item/diamond_sword',
                                                                        'layer1': f'libreria:item/fx/blade/{name}_{g}'}},
                      open(os.path.join(mod, f'sword_{name}_{g}.json'), 'w'), indent=1)
            frames = aura.AURAS[kind](base, g)
            model = {'type': 'minecraft:model', 'model': sword}
            if any(f[..., 3].any() for f in frames):
                strip = Image.new('RGBA', (aura.SIZE, aura.SIZE * len(frames)), (0, 0, 0, 0))
                for k, f in enumerate(frames):
                    f = f.copy()
                    a = f[..., 3].astype(int)
                    # the aura's mark for the shader: alpha 1 (mod 16) = lit by itself, no enchantment glint
                    f[..., 3] = np.where(a > 0, np.clip(16 * np.round((a - 1) / 16) + 1, 17, 241), 0).astype(np.uint8)
                    strip.paste(Image.fromarray(f, 'RGBA'), (0, k * aura.SIZE))
                strip.save(os.path.join(atex, f'{name}_{g}.png'))
                json.dump({'animation': {'frametime': 1, 'interpolate': False}},
                          open(os.path.join(atex, f'{name}_{g}.png.mcmeta'), 'w'))
                json.dump(plane_model(f'libreria:item/fx/aura/{name}_{g}'),
                          open(os.path.join(mod, f'aura_{name}_{g}.json'), 'w'), indent=1)
                model = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                         'cases': [{'when': 'gui', 'model': model}],
                         'fallback': {'type': 'minecraft:composite', 'models': [
                             model, {'type': 'minecraft:model', 'model': f'libreria:item/fx/aura_{name}_{g}'}]}}
            json.dump({'model': model}, open(os.path.join(itm, f'sword_{name}_{g}.json'), 'w'), indent=1)
    print('blades', ', '.join(KINDS))


if __name__ == '__main__':
    main()
