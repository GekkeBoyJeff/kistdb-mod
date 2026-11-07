#!/usr/bin/env python3
"""One-off: gives every part of the existing stories one exact item (a story is a set of things one person or place
had: always the same items), and rewrites the parts that were on things that are never found (mace, elytra, brush,
carrot on a stick)."""
import os, sys
D = os.path.join(os.path.dirname(__file__), '../../info/src/main/resources/libreria/found')
ITEMS = {
 'lantern': 'iron_sword bow iron_axe trident crossbow shield leather_helmet',
 'ninth': 'golden_helmet iron_pickaxe iron_shovel leather_boots wooden_hoe shears shield',
 'pell': 'wooden_hoe iron_axe iron_sword iron_helmet leather_boots fishing_rod leather_chestplate iron_shovel',
 'crumb': 'copper_sword leather_boots bow iron_helmet wooden_pickaxe fishing_rod shield flint_and_steel',
 'torch': 'iron_sword iron_chestplate shield iron_helmet iron_leggings iron_boots crossbow',
 'choir': 'golden_helmet leather_chestplate golden_sword leather_boots shears bow',
 'cartographer': 'stone_pickaxe leather_boots iron_shovel iron_helmet fishing_rod leather_chestplate',
 'twins': 'golden_sword golden_axe leather_boots chainmail_helmet shield flint_and_steel iron_chestplate',
 'quiet': 'iron_sword iron_helmet bow leather_boots iron_shovel leather_chestplate',
 'halvard': 'trident golden_helmet golden_chestplate iron_sword leather_boots@fishing shield',
 'reginald': 'iron_sword shield iron_helmet iron_chestplate iron_boots iron_leggings',
 'gilded': 'golden_sword@bastion golden_axe@nether golden_helmet@bastion golden_boots@nether crossbow@bastion golden_chestplate',
 'ashgrove': 'golden_sword@fortress leather_helmet golden_chestplate@fortress leather_boots flint_and_steel wooden_hoe',
 'wren': 'leather_chestplate crossbow iron_sword iron_helmet leather_boots fishing_rod',
 'glasswidow': 'iron_pickaxe iron_shovel chainmail_helmet iron_sword iron_boots@nether',
 'wellroot': 'wooden_hoe iron_shovel iron_sword shield bow',
 'goat': 'iron_helmet leather_boots shield stone_pickaxe wooden_hoe',
 'marrow': 'iron_sword leather_boots iron_chestplate bow iron_helmet shield iron_axe',
 'tide': 'fishing_rod trident leather_boots leather_helmet@shipwreck leather_leggings',
 'obsidianheart': 'diamond_pickaxe diamond_chestplate diamond_sword diamond_boots diamond_shovel',
 'hollowfield': 'iron_pickaxe leather_chestplate iron_shovel iron_helmet leather_boots',
 'whisperwood': 'iron_axe bow shears wooden_hoe leather_boots',
 'amberlight': 'crossbow golden_helmet iron_sword leather_boots shears iron_chestplate',
 'voidsailor': 'diamond_leggings@end_city iron_helmet@end diamond_sword@end_city iron_boots@end diamond_chestplate@end',
 'beekeeper': 'shears leather_helmet wooden_hoe iron_sword leather_boots',
 'oldbuilders': 'iron_pickaxe@stronghold iron_helmet iron_shovel iron_boots@stronghold iron_chestplate wooden_hoe@archaeology',
 'phantomwatch': 'leather_leggings bow iron_helmet leather_chestplate leather_boots',
 'crimsonbard': 'crossbow@nether golden_helmet@bastion golden_sword@nether golden_boots@nether shield',
 'endkeepers': 'diamond_sword@end diamond_helmet@end_city diamond_chestplate@end diamond_boots@end_city diamond_leggings@end_city',
 'bluecheese': 'iron_pickaxe leather_boots iron_sword shears shield',
 'sculkchild': 'leather_helmet leather_boots shield iron_sword bow',
 'pigrace': 'leather_chestplate leather_helmet leather_boots leather_leggings',
 'mire': 'shears iron_helmet wooden_hoe iron_sword leather_boots fishing_rod',
 'clocktower': 'iron_pickaxe iron_helmet iron_sword leather_boots shield',
 'lostlegion': 'iron_sword shield iron_helmet iron_boots crossbow iron_leggings',
 'hearthmother': 'flint_and_steel stone_axe leather_chestplate leather_helmet leather_boots',
 'deepfisher': 'fishing_rod leather_boots iron_helmet shears iron_sword',
 'odric': 'iron_helmet iron_chestplate iron_leggings iron_boots iron_sword shield',
 'bramble': 'iron_pickaxe@mineshaft iron_shovel iron_helmet leather_boots iron_axe',
 'saltwife': 'fishing_rod leather_boots@fishing leather_helmet leather_leggings trident',
 'greenhood': 'bow crossbow leather_helmet leather_boots leather_chestplate',
 'hollowfarm': 'wooden_hoe shears leather_boots stone_axe leather_helmet',
 'skyrider': 'leather_leggings leather_helmet leather_boots crossbow leather_chestplate',
 'digsite': 'wooden_hoe@archaeology iron_shovel leather_boots leather_helmet iron_pickaxe',
 'nethergold': 'golden_helmet@bastion golden_chestplate@nether golden_leggings@nether golden_boots@bastion golden_sword@nether',
 'voidwarden': 'diamond_helmet@end diamond_chestplate@end_city diamond_leggings@end diamond_boots@end_city diamond_sword@end',
 'chef': 'iron_axe wooden_hoe shears leather_helmet iron_sword',
 'wolfpack': 'leather_helmet leather_chestplate leather_boots stone_axe bow',
 'courier': 'leather_boots leather_helmet leather_chestplate leather_leggings shield',
 'beekeepers2': 'chainmail_helmet copper_chestplate copper_leggings copper_boots copper_sword',
 'stormcaller': 'trident golden_helmet copper_chestplate copper_boots copper_leggings',
 'deepdelver': 'iron_pickaxe leather_boots iron_helmet shears iron_leggings@ancient_city',
 'hatstand': 'leather_helmet leather_chestplate leather_boots shield iron_sword',
 'moonfarmer': 'wooden_hoe leather_boots leather_helmet shears stone_axe',
 'knee': 'iron_helmet iron_chestplate iron_leggings iron_boots bow shield',
 'greenhero': 'iron_sword shield bow iron_boots leather_helmet',
 'testsubject': 'iron_boots iron_helmet leather_chestplate shield iron_pickaxe',
 'greensoldier': 'golden_helmet iron_chestplate diamond_sword crossbow iron_boots',
 'ashen': 'iron_sword shield iron_helmet iron_chestplate leather_boots',
 'vaultdweller': 'iron_helmet leather_chestplate leather_boots crossbow shield',
 'hood': 'leather_helmet leather_boots iron_sword leather_chestplate leather_leggings',
 'witcher': 'iron_sword iron_axe iron_helmet leather_chestplate leather_boots',
 'plan': 'crossbow leather_helmet leather_boots leather_chestplate fishing_rod',
 'leeroy': 'iron_helmet iron_chestplate iron_sword shield iron_boots',
 'crowbar': 'iron_axe copper_chestplate leather_helmet copper_boots copper_leggings',
}
# (story, part) -> (name or None, text or None)
REWRITE = {
 ('lantern', 4): (None, 'Carried out of the sea by a drowned, from the very spot where the lantern fell. It is still faintly warm, as if something down there is keeping it lit.'),
 ('lantern', 6): ('The Hammered Bell', 'The lighthouse bell, hammered flat and strapped to an arm. Some nights it still rings when something strikes it, softly, three times. Three ships never came home.'),
 ('cartographer', 1): ("Ilse's Rock Hammer", 'Ilse Vorn mapped the world to its edges and chipped a sample from every ruin she passed. She marked each one with a pin hole. She always said the last map would be the most important.'),
 ('cartographer', 6): ("Ilse's Flying Coat", 'She wanted to map the world from above, and wore this coat to do it. She flew so high the world looked like one of her maps. She circled back to check a mistake. There was no mistake. There was a new island.'),
 ('wren', 1): ("Holloway's Flight Coat", 'Captain Wren Holloway was the only pirate who sailed the sky. She stole her wings from an End ship, her rockets from a festival and this coat from an admiral. She stole everything, really.'),
 ('hollowfield', 2): ('The Guild Apron', "Fennick's apron, the pockets still full of shards. He dug through every ruin from here to the badlands, looking for the treasure of the Old Builders. He found a lot of pots. He found one very important pot."),
 ('voidsailor', 1): ('Breeches of the Far Ship', None),
 ('oldbuilders', 6): ("The Last Builder's Hoe", 'Their final work wasn\'t a stronghold. It was a single pot, buried in the sand with this old hoe beside it, and a message inside: "We were not the first either."'),
 ('phantomwatch', 1): ("Night-Rider's Breeches", None),
 ('endkeepers', 5): (None, 'The last Keeper left these behind, took a pair of wings and flew out past the islands to see what the Gate was keeping. The Gate is still open. The Keeper is not back.'),
 ('pigrace', 1): ('The Winning Silks', 'The Great Pig Race of the Plains had one rule: no rules. The rider in these silks won it, holding a carrot on a stick. The pig ate the carrot at the finish line. The rider still hasn\'t recovered his dignity.'),
 ('pigrace', 4): ('Strider Division', 'After the pigs unionised, the race moved to the Nether, on striders. These breeches did the very first lap. The striders were very slow and very happy. Nobody finished. Everyone had a lovely time.'),
 ('skyrider', 1): ("Talia's Flying Trousers", None),
 ('digsite', 1): ("Professor Quill's Trowel", 'Professor Quill scraped the sand off a single pot with this little hoe for three weeks. It was the most important pot in history. It was also, technically, a chamber pot.'),
 ('courier', 4): (None, 'The Courier found a pair of wings halfway through her route and finished the rest in an afternoon, in these. The other couriers called it cheating. There were no other couriers.'),
 ('stormcaller', 2): ('The Storm Crown', None),
 ('saltwife', 5): (None, "On her last morning she hooked something heavy. It was a drowned, and it held this, and on it were her husband's initials. He'd gone out with it thirty years before."),
}
seen = set()
for f in sorted(os.listdir(D)):
    if not f.startswith('arcs'): continue
    out, story, part = [], None, 0
    for raw in open(os.path.join(D, f)).read().split('\n'):
        line = raw.strip()
        if line.startswith('@'):
            story = line[1:].split('|')[0].strip(); part = 0; seen.add(story)
            out.append(raw); continue
        if not line or line.startswith('#') or line.count('|') < 2:
            out.append(raw); continue
        part += 1
        kinds, name, text = [x.strip() for x in line.split('|', 2)]
        items = ITEMS[story].split()
        assert len(items) >= part, (story, part)
        name2, text2 = REWRITE.get((story, part), (None, None))
        out.append(f'{items[part - 1]} | {name2 or name} | {text2 or text}')
    for story_id in []: pass
    open(os.path.join(D, f), 'w').write('\n'.join(out))
missing = set(ITEMS) - seen
assert not missing, missing
print('retagged', len(seen), 'stories')
