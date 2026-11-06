"""Proof of concept: an armour set's light following the lines of its trim. The trim itself stays exactly as vanilla
draws it (pattern and material); our layer is a seam of light right round the trim's lines, its pixels carrying a code
for the core shader (flicker for the ember mantle, a glint flowing along for the ward).

Per set kind and trim pattern an equipment asset: the vanilla diamond layers, then ours.

    python3 scripts/glint/trims_fx.py [vanilla 26.3 dir]
"""
import json
import os
import sys

from PIL import Image

VAN = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/ref/vanilla26/assets/minecraft'
OUT = os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources/assets/libreria')
KINDS = {'fire': ((196, 84, 26), 252), 'protection': ((100, 116, 206), 251)}


def main():
    patterns = sorted(f[:-4] for f in os.listdir(f'{VAN}/textures/trims/entity/humanoid') if f.endswith('.png'))
    for layer in ('humanoid', 'humanoid_leggings'):
        os.makedirs(f'{OUT}/textures/entity/equipment/{layer}/fxtrim', exist_ok=True)
    os.makedirs(f'{OUT}/equipment', exist_ok=True)
    for pat in patterns:
        for layer in ('humanoid', 'humanoid_leggings'):
            trim = Image.open(f'{VAN}/textures/trims/entity/{layer}/{pat}.png').convert('RGBA')
            armour = Image.open(f'{VAN}/textures/entity/equipment/{layer}/diamond.png').convert('RGBA')
            w, h = trim.size
            on = lambda x, y: 0 <= x < w and 0 <= y < h and trim.getpixel((x, y))[3] > 0 and trim.getpixel((x, y))[0] > 0
            worn = lambda x, y: armour.getpixel((x, y))[3] > 0
            seam = [(x, y) for y in range(h) for x in range(w)
                    if worn(x, y) and not on(x, y) and any(on(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
            for kind, (rgb, code) in KINDS.items():
                img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
                for p in seam:
                    img.putpixel(p, rgb + (code,))
                img.save(f'{OUT}/textures/entity/equipment/{layer}/fxtrim/{kind}_{pat}.png')
        for kind in KINDS:
            json.dump({'layers': {
                'humanoid': [{'texture': 'minecraft:diamond'}, {'texture': f'libreria:fxtrim/{kind}_{pat}'}],
                'humanoid_leggings': [{'texture': 'minecraft:diamond'}, {'texture': f'libreria:fxtrim/{kind}_{pat}'}]}},
                open(f'{OUT}/equipment/fxtrim_{kind}_{pat}.json', 'w'), indent=1)
    print(len(patterns), 'patterns:', ' '.join(patterns))


if __name__ == '__main__':
    main()
