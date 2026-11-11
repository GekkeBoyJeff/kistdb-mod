"""The recipes that colour a waystone in the crafting grid: a waystone (light or dark) with a dye, with or without a glow
ink sac (glowing runes), or with only a glow ink sac (its own colour, glowing). The result carries its look as
Stones.lookOn() writes it (custom data, the colour for the item's tint, a lore line), so crafted and dropped waystones
are the same item.

Usage: python3 dye_recipes.py  (writes waystones/src/main/resources/data/libreria/recipe/dye/*.json)
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '../../waystones/src/main/resources/data/libreria/recipe/dye')
# WaystoneLook.shine: the dyes' colours, a little brighter
SHINE = {'white': 0xF4F6FF, 'orange': 0xFF9A3C, 'magenta': 0xF07CE6, 'light_blue': 0x7FD8FF, 'yellow': 0xFFE45C,
         'lime': 0xA6F04A, 'pink': 0xFFA6C8, 'gray': 0x8C959C, 'light_gray': 0xC8CCD0, 'cyan': 0x5FF2E0,
         'purple': 0xC79BF0, 'blue': 0x6C82FF, 'brown': 0xC08A5A, 'green': 0x7FC24A, 'red': 0xFF5A50, 'black': 0x4A4A5A}
BASES = {'waystone': 'purple', 'dark_waystone': 'cyan'}


def words(dye):
    return dye.replace('_', ' ')


def result(base, dye, glow):
    """What Stones.lookOn writes: keep the two in step."""
    colour = dye or BASES[base]
    look = {'glow': glow}
    if dye:
        look['dye'] = dye
    line = 'Runes: ' + words(colour) + (', glowing' if glow else '')
    return {'id': 'libreria:' + base, 'count': 1, 'components': {
        'minecraft:custom_data': {'libreria_waystone_look': look},
        'minecraft:custom_model_data': {'colors': [SHINE[colour]]},
        'minecraft:lore': [{'text': line, 'color': 'gray', 'italic': False}]}}


def recipe(base, extra, res):
    return {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'group': 'libreria_waystone_look',
            'ingredients': ['libreria:' + base] + extra, 'result': res}


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))
    n = 0
    for base in BASES:
        for dye in SHINE:
            for glow in (False, True):
                extra = ['minecraft:' + dye + '_dye'] + (['minecraft:glow_ink_sac'] if glow else [])
                name = f'{base}_{dye}' + ('_glow' if glow else '')
                json.dump(recipe(base, extra, result(base, dye, glow)), open(os.path.join(OUT, name + '.json'), 'w'), indent='\t')
                n += 1
        json.dump(recipe(base, ['minecraft:glow_ink_sac'], result(base, '', True)), open(os.path.join(OUT, base + '_glow.json'), 'w'), indent='\t')
        n += 1
    print(n, 'recipes')


main()
