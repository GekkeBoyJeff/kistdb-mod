"""A trimmed armour piece's light follows the lines of its trim. The trim itself stays exactly as vanilla draws it
(pattern and material); our layer is a seam of light right round the trim's lines, its pixels carrying a code for the
core shader, in the colour of the piece's effect (embers for Fire Protection, a flowing glint for Protection…).

Per material, effect family and trim pattern an equipment asset: the vanilla layers of that material, then ours. The
server picks it per piece for whoever looks (EquipmentFx); an untrimmed piece gets its glow (armor_fx.py) instead.

    python3 scripts/glint/trims_fx.py [vanilla 26.3 assets/minecraft dir]
"""
import json
import os
import shutil
import sys

from PIL import Image

VAN = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/ref/vanilla26/assets/minecraft'
OUT = os.path.join(os.path.dirname(__file__), '../../glint/src/main/resources/assets/libreria')
MATERIALS = ['leather', 'chainmail', 'copper', 'iron', 'gold', 'diamond', 'netherite', 'turtle_scute']
FLICKER, PULSE, FLOW, TWINKLE = 252, 253, 251, 250
# family -> (colour, code): the same families as the armour glows
KINDS = {
    'fireguard': ((196, 84, 26), FLICKER), 'protection': ((100, 116, 206), FLOW), 'blast': ((176, 112, 70), FLICKER),
    'arrowguard': ((150, 214, 192), FLOW), 'thorns': ((178, 40, 56), PULSE), 'frost': ((170, 222, 255), TWINKLE),
    'soul': ((60, 180, 232), FLICKER), 'water': ((70, 150, 250), FLOW), 'sculk': ((40, 190, 200), FLOW),
    'feather': ((226, 232, 246), PULSE), 'mending': ((140, 220, 84), PULSE), 'curse': ((176, 112, 246), FLOW),
    'sturdy': ((200, 214, 232), FLOW),
}


def main():
    patterns = sorted(f[:-4] for f in os.listdir(f'{VAN}/textures/trims/entity/humanoid') if f.endswith('.png'))
    shutil.rmtree(f'{OUT}/textures/entity/equipment/humanoid/fxtrim', ignore_errors=True)
    shutil.rmtree(f'{OUT}/textures/entity/equipment/humanoid_leggings/fxtrim', ignore_errors=True)
    for f in os.listdir(f'{OUT}/equipment') if os.path.isdir(f'{OUT}/equipment') else []:
        if f.startswith('fxtrim_'):
            os.remove(f'{OUT}/equipment/{f}')
    for layer in ('humanoid', 'humanoid_leggings'):
        os.makedirs(f'{OUT}/textures/entity/equipment/{layer}/fxtrim', exist_ok=True)
    os.makedirs(f'{OUT}/equipment', exist_ok=True)
    bases = {m: json.load(open(f'{VAN}/equipment/{m}.json')) for m in MATERIALS}
    count = 0
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
                img.save(f'{OUT}/textures/entity/equipment/{layer}/fxtrim/{kind}_{pat}.png', optimize=True)
        for mat, base in bases.items():
            for kind in KINDS:
                layers = {}
                for lk, ls in base['layers'].items():
                    # every vanilla layer (a baby's, a horse's…), our seam on the body's
                    layers[lk] = ls + ([{'texture': f'libreria:fxtrim/{kind}_{pat}'}] if lk in ('humanoid', 'humanoid_leggings') else [])
                asset = {'layers': layers}
                if 'trim_overrides' in base:
                    asset['trim_overrides'] = base['trim_overrides']
                with open(f'{OUT}/equipment/fxtrim_{mat}_{kind}_{pat}.json', 'w') as f:
                    json.dump(asset, f, separators=(',', ':'))
                count += 1
    print(len(patterns), 'patterns,', count, 'assets:', ' '.join(patterns))


if __name__ == '__main__':
    main()
