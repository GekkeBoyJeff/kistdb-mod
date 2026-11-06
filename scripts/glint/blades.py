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
    # name: (colour, highest grade)
    'sharp': ((226, 238, 255), 5),
    'fire': ((255, 136, 40), 2),
    'smite': ((255, 238, 178), 5),
    'bane': ((126, 232, 92), 5),
}


def main():
    sword = Image.open(REF).convert('RGBA')
    opaque = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and sword.getpixel((x, y))[3] > 0
    blade = lambda x, y: opaque(x, y) and x + y >= 13 and y <= 10
    rim = lambda x, y: blade(x, y) and any(not opaque(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    edge = sorted([(x, y) for y in range(16) for x in range(16) if rim(x, y)], key=lambda p: p[0] - p[1])  # hilt first, tip last
    ridge = [(x, y) for y in range(16) for x in range(16) if blade(x, y) and not rim(x, y) and x + y in (15, 16)]
    tex = os.path.join(ROOT, 'textures/item/fx/blade')
    mod = os.path.join(ROOT, 'models/item/fx')
    itm = os.path.join(ROOT, 'items/fx')
    for d in (tex, mod, itm):
        os.makedirs(d, exist_ok=True)
    for name, (col, top) in KINDS.items():
        for g in range(1, top + 1):
            # how far up from the tip the edge glows, how bright, and how it moves, per grade
            step = g if top == 5 else (3 if g == 1 else 5)
            frac = [0, 0.35, 0.55, 0.8, 1.0, 1.0][step]
            code = [0, 254, 253, 251, 251, 251][step]
            bright = [0, 0.7, 0.85, 1.0, 1.0, 1.0][step]
            c = tuple(min(255, int(v * bright)) for v in col)
            out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
            lit = edge[int(len(edge) * (1 - frac)):]
            for x, y in lit:
                out.putpixel((x, y), c + (code,))
            if step >= 4:
                # the ridge down the middle of the blade glows too; at the top it twinkles
                for x, y in ridge:
                    out.putpixel((x, y), tuple(int(v * 0.8) for v in col) + (254 if step == 4 else 250,))
            out.save(os.path.join(tex, f'{name}_{g}.png'))
            json.dump({'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'minecraft:item/diamond_sword',
                                                                        'layer1': f'libreria:item/fx/blade/{name}_{g}'}},
                      open(os.path.join(mod, f'sword_{name}_{g}.json'), 'w'), indent=1)
            json.dump({'model': {'type': 'minecraft:model', 'model': f'libreria:item/fx/sword_{name}_{g}'}},
                      open(os.path.join(itm, f'sword_{name}_{g}.json'), 'w'), indent=1)
    print('blades', ', '.join(KINDS))


if __name__ == '__main__':
    main()
