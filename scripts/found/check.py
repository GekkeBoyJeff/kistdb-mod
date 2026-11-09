#!/usr/bin/env python3
"""Checks the found texts: every story part on one item that is really found (in loot or on monsters), and found
where the part says. Usage: python3 check.py [vanilla data/minecraft dir]. Exit code 1 on a problem."""
import json, os, re, sys, collections
HERE = os.path.dirname(__file__)
D = os.path.join(HERE, '../../info/src/main/resources/libreria/found')
DATA = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/ref/data/data/minecraft'
# Must match Found.FINDABLE
FINDABLE = set('''leather_helmet leather_chestplate leather_leggings leather_boots chainmail_helmet chainmail_chestplate chainmail_leggings
chainmail_boots copper_chestplate copper_leggings copper_boots iron_helmet iron_chestplate iron_leggings iron_boots golden_helmet
golden_chestplate golden_leggings golden_boots diamond_helmet diamond_chestplate diamond_leggings diamond_boots wooden_axe wooden_pickaxe
wooden_hoe stone_axe stone_pickaxe stone_sword stone_spear copper_axe copper_sword copper_spear iron_axe iron_pickaxe iron_shovel
iron_sword iron_spear golden_axe golden_pickaxe golden_shovel golden_hoe golden_sword diamond_axe diamond_pickaxe diamond_shovel
diamond_hoe diamond_sword diamond_spear bow crossbow trident fishing_rod flint_and_steel shears shield'''.split())
PLACES = [('chests/bastion', 'bastion', 'nether'), ('chests/nether_bridge', 'fortress', 'nether'), ('chests/ruined_portal', 'ruined_portal', 'both'),
          ('chests/end_city', 'end_city', 'end'), ('chests/ancient_city', 'ancient_city', 'overworld'), ('chests/stronghold', 'stronghold', 'overworld'),
          ('chests/woodland_mansion', 'mansion', 'overworld'), ('chests/pillager_outpost', 'outpost', 'overworld'), ('chests/shipwreck', 'shipwreck', 'overworld'),
          ('chests/underwater_ruin', 'shipwreck', 'overworld'), ('chests/buried_treasure', 'buried', 'overworld'), ('chests/desert_pyramid', 'desert', 'overworld'),
          ('chests/jungle_temple', 'jungle', 'overworld'), ('chests/igloo', 'igloo', 'overworld'), ('chests/abandoned_mineshaft', 'mineshaft', 'overworld'),
          ('chests/simple_dungeon', 'dungeon', 'overworld'), ('chests/trial_chambers', 'trial', 'overworld'), ('chests/village', 'village', 'overworld'),
          ('chests/spawn_bonus_chest', 'village', 'overworld'), ('gameplay/fishing', 'fishing', 'overworld'), ('gameplay/piglin_bartering', 'barter', 'nether'),
          ('archaeology', 'archaeology', 'overworld'), ('equipment', 'trial', 'overworld'), ('chests/', '', 'overworld')]
MOB = {'overworld': set('''iron_sword iron_shovel bow trident fishing_rod iron_axe crossbow leather_helmet leather_chestplate leather_leggings leather_boots
chainmail_helmet chainmail_chestplate chainmail_leggings chainmail_boots iron_helmet iron_chestplate iron_leggings iron_boots golden_helmet
golden_chestplate golden_leggings golden_boots diamond_helmet diamond_chestplate diamond_leggings diamond_boots'''.split()),
       'nether': set('golden_sword crossbow golden_axe stone_sword bow golden_helmet golden_chestplate golden_leggings golden_boots'.split())}
where_items = collections.defaultdict(set)  # place or realm -> items
for root, _, files in os.walk(os.path.join(DATA, 'loot_table')):
    for f in files:
        path = os.path.relpath(os.path.join(root, f), os.path.join(DATA, 'loot_table'))[:-5]
        place = next(((p, r) for pre, p, r in PLACES if path.startswith(pre)), None)
        if place is None: continue
        items = set(re.findall(r'"name":\s*"minecraft:([a-z_]+)"', open(os.path.join(root, f)).read())) & FINDABLE
        p, realm = place
        if p: where_items[p] |= items
        for rl in (['overworld', 'nether'] if realm == 'both' else [realm]): where_items[rl] |= items
for realm, items in MOB.items():
    where_items['mob'] |= items
    where_items[realm] |= items
found = set().union(*where_items.values())
problems = []
for item in sorted(FINDABLE - found): problems.append(f'{item} is listed as findable but nothing gives it')
stories = 0; parts = 0; per_item = collections.Counter()
# Must match Showcase: a mannequin wears one of each armour piece and holds six things (hands, back, hips); two more lie at
# its feet. A story that doesn't fit goes on a pedestal, which holds eight.
def fits(items):
    c = collections.Counter(next((a for a in ('helmet', 'chestplate', 'leggings', 'boots') if i.endswith('_' + a)), 'hand') for i in items)
    dup = sum(max(0, c[a] - 1) for a in ('helmet', 'chestplate', 'leggings', 'boots'))
    return dup + max(0, c['hand'] - 6) <= 2 or len(items) <= 8
def done(f, story, intro, items, ids):
    if story is None: return
    if story in ids: problems.append(f'{f}: {story} is there twice')
    ids.add(story)
    if not intro: problems.append(f'{f}: {story} has no introduction (a "> " line)')
    if not fits(items): problems.append(f'{f}: {story} fits neither a mannequin nor a pedestal: {items}')
ids = set()
for f in sorted(os.listdir(D)):
    if not f.startswith('arcs'): continue
    story = None; intro = False; items = []
    for n, raw in enumerate(open(os.path.join(D, f)), 1):
        line = raw.strip()
        if line.startswith('@'):
            done(f, story, intro, items, ids)
            story = line[1:].split('|')[0].strip(); stories += 1; intro = False; items = []; continue
        if line.startswith('>'): intro = True; continue
        if not line or line.startswith('#') or line.count('|') < 2: continue
        head, name, text = [x.strip() for x in line.split('|', 2)]
        item, _, where = head.partition('@')
        parts += 1; per_item[item] += 1; items.append(item)
        if item not in FINDABLE: problems.append(f'{f}:{n} {story}: "{item}" is never found')
        elif where and item not in where_items.get(where, set()): problems.append(f'{f}:{n} {story}: {item} is never found @{where}')
        if len(text) > 300: problems.append(f'{f}:{n} {story}: text too long ({len(text)})')
    done(f, story, intro, items, ids)
for p in problems: print('PROBLEM', p)
print(f'{stories} stories, {parts} parts; parts per item:', ', '.join(f'{k} {v}' for k, v in per_item.most_common()))
sys.exit(1 if problems else 0)
