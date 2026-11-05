# Enchant effects – the rules

The single source of truth is `src/main/resources/glint/effects.json`. This file explains how its parts fit
together. The Java code and the web mock both read the JSON. Change a look there, not in the code.

## Goals

- **Readable.** Every effect has its own place on the body (zones). You can tell boots from a helmet at a glance.
- **Never on the ground.** Nothing idles below the ankles. Only footprints (when you step) and a landing touch the ground.
- **Never in your own first-person view.** Your own armour shows in third person only (just behind the camera plane).
- **Calm.** A budget per player per run. More gear does not mean more noise: it means a richer mix.
- **Many looks, few rules.** Looks are built from layers. A few hundred lines of data make thousands of looks.
- **Server-side.** Vanilla particles only. Colour comes from colourable particles (`dust`, `dust_color_transition`,
  `trail`, `tinted_leaves`, `flash`, `entity_effect`).

## What is read from the gear

Per armour piece: material (from the equippable asset: leather, chainmail, copper, iron, gold, diamond,
netherite, turtle_scute, elytra), leather dye colour, trim (pattern and material), enchantments with levels.

Per hand: the kind of item (sword, axe, spear, mace, trident, pickaxe, shovel, hoe, bow, crossbow, fishing rod,
enchanted book, other) and its enchantments with levels.

Per player: walking, sneaking, falling, under water, on fire, gliding.

## The layers, in order

Each layer adds to the previous one. A later layer never removes an earlier one, except combinations
(which replace what they are made of).

1. **Enchantment → family.** Every enchantment belongs to a family (`families`). A family has a *motif*: a
   particle and a motion (`motions`). Unbreaking and Mending are *silent* (no idle look). Protection and the
   curses are *accent-only*: they show only as the second effect of a piece, or when a piece has nothing else.
2. **Level → tier.** Level divided by the maximum level gives a tier (`tiers`): *hint*, *clear*, *max*,
   *overcharged* (above the vanilla maximum). The tier scales rate and size. At *max* and up the family's
   *flourish* joins in (embers with flames, a glint with Sharpness…). One-level enchantments count as *max*.
3. **Several enchantments on one item.** The *primary* is the first family in `order` (most striking first).
   The *secondary* is the next one that isn't silent: it shows at a third of the rate, alternating with the
   primary, in the same place. Other enchantments still count for combinations.
4. **Where.** Each armour piece shows in its *zone*: helmet → `head` (a halo above), chestplate → `chest`,
   leggings → `legs`, boots → `feet` (at the ankles). Items in hand show along the item (`items` → anchor).
   The zones take turns (round robin), so four pieces never fire at once.
5. **Armour material → colour and character.** The material gives the accent colour (leather: its dye) and a
   character: speed and size (netherite heavy and slow, chainmail small and crisp, gold quick with a glint).
6. **Trim material → colour.** A trim's material overrides the accent colour of that piece and adds a small
   *sparkle* (emerald: green stars, redstone: red dust, lapis: glyphs…).
7. **Trim pattern → shape.** A trim's pattern changes the *shape* of that piece's zone: a ring becomes a
   square (shaper), sentinels at four points (sentry), a pulse (host, coast), vertical ribs (rib), a column (spire),
   a zigzag (vex, bolt), a spiral (wild, tide, raiser)…
8. **Sets.** Four pieces that share something make one bigger look, on top of the zones:
   - the same enchantment family (not Protection, not a curse) → a helix of that family round the body;
   - the same material → the material's set look (`materials.*.set`);
   - the same trim pattern → the pattern's set look (`trimPatterns.*.set`), in the trim colour(s).
   A family set replaces the zone look of that family on the four pieces; the other families on those pieces
   keep showing (a set never hides the rest).
9. **Combinations.** Named recipes (`combos`) that look at the whole kit: families (with tiers and how many
   pieces), materials, trims. The first match per class wins (`comboRules`): at most one *legend*, one
   *material*, one *trim* and one *enchant* combination, two at once in total. A combination replaces the idle
   look of what it is made of and shows in the `aura` zone. Events stay those of the parts.
10. **Events.** Short, clear moments (`events`): a swing draws an arc of the weapon's family, a kill bursts, an
    arrow leaves a trail, Thorns spikes towards whoever hit you, Protection absorbs a hit (glyphs in, a ripple out),
    Feather Falling spirals while you fall and puffs when you land, Frost and Soul Speed leave footprints,
    Swift Sneak breathes sculk while you sneak, water families bubble under water. Each has a cooldown.

## The budget

Every run (`budget.runTicks`) a player gives off at most `pointsPerRun` points for each viewer (by the viewer's
intensity: subtle, normal, rich). A point is one emission (up to `maxCount` particles). Who goes first is
`budget.priority`: a combination, the hand, sets, zones in turn, accents, sparkles. Events don't use the budget,
but have cooldowns.

## The viewer decides

Everyone chooses what they see (You › Effects): intensity, your own hand in first person, your own armour in
third person, other players, per family on/off, and three switches: combinations, armour and trims (colour and
shape), events.

## Where it lives in the code

- `core` – `nl.libreria.core.fx`: shapes (points of a ring, helix, arc, square, zigzag…), sending to viewers with
  colour, and a small timeline for events that take a few ticks. Ideas for the shapes come from EffectLib and
  ParticleAnimationLib (both MIT).
- `glint` – reads `effects.json`, reads the gear into a kit, composes the layers per wearer (cached until the
  gear changes), sends per viewer within the budget, and listens for the events.

## Adding a look

- A new enchantment (or one from a datapack): add it to a family's `enchants`, or make a family.
- A new trim pattern or material: add it to `trimPatterns` / `trimMaterials`.
- A new combination: add it to `combos`, above the ones it should beat.
- Then run the self-tests and the screenshot client; every combination and set has a screenshot.
