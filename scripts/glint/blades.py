"""Proof of concept: a glowing edge on the sword itself. An extra layer over the diamond sword whose edge pixels carry a
code in their alpha, so the core shader lights and animates them (flickering fire, a glint that runs along the edge,
a slow golden pulse).

    python3 scripts/glint/blades.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources/assets/libreria')
REF = '/home/claude/ref/assets/assets/minecraft/textures/item/diamond_sword.png'
KINDS = {
    # name: (colour of the edge, alpha code, also the inside of the blade, faintly)
    'fire': ((255, 136, 40), 252, None),
    'sharp': ((226, 238, 255), 251, None),
    'smite': ((255, 214, 96), 253, None),
}


def main():
    sword = Image.open(REF).convert('RGBA')
    opaque = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and sword.getpixel((x, y))[3] > 0
    blade = lambda x, y: opaque(x, y) and x + y >= 13 and y <= 10
    tex = os.path.join(ROOT, 'textures/item/fx/blade')
    mod = os.path.join(ROOT, 'models/item/fx')
    itm = os.path.join(ROOT, 'items/fx')
    for d in (tex, mod, itm):
        os.makedirs(d, exist_ok=True)
    for name, (edge, code, inner) in KINDS.items():
        out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                if not blade(x, y):
                    continue
                rim = any(not opaque(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if rim:
                    out.putpixel((x, y), edge + (code,))
                elif inner is not None:
                    out.putpixel((x, y), inner + (code,))
        out.save(os.path.join(tex, name + '.png'))
        json.dump({'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'minecraft:item/diamond_sword',
                                                                    'layer1': f'libreria:item/fx/blade/{name}'}},
                  open(os.path.join(mod, f'sword_{name}.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': f'libreria:item/fx/sword_{name}'}},
                  open(os.path.join(itm, f'sword_{name}.json'), 'w'), indent=1)
    print('blades', ', '.join(KINDS))


if __name__ == '__main__':
    main()
