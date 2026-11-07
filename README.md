# Libreria – a server-side mod framework for Minecraft 26.3 (Fabric)

Libreria is a collection of small quality-of-life mods that work together as one framework. Everything runs **on the
server only**: players join with a plain vanilla client and install nothing. Buttons, menus, beams and floating text are
built from vanilla screens and virtual entities, and the Libreria resource pack (prettier menus, tooltips and item
effects) is built, hosted and sent by the server itself.

Pick the modules you want. Each works on its own, and together they read as one product. Players never see which jar
does what: everything is in one of **four places**, the same everywhere:

| Place | What's in it |
|---|---|
| **Items** | Your items (search), your chests, quick stack, sorting, tools and refills |
| **Land & places** | Your places (waypoints, death point and graves, pets, journal, your land), the land map, trusted players |
| **Progress** | Achievements, statistics, the chronicle |
| **You** | Nickname, title, effects, and how things look |

One start screen (**G**, the Libreria button in the pause menu, or **`/l`**) leads to a page per place; every window
there says where you are ("Land & places › Journal") and goes **Back** one level. Settings follow the same four places,
grouped by task ("When you die", "Search & sorting"). Menus carry the colour and emblem of their place. Chat lines start
with a ◆ in that colour and only carry what you may need later (coordinates, buttons, other players' news); everything
else is a card at the top, a short line in the action bar, or a quiet place name. Small tips show up once, at the moment
they're useful, and can all be read back under Help › Tips.

And the modules help each other. A few examples:
- Click a collected item in your achievements and you're shown the way to the chest it is in.
- Your achievements decide how many chunks you can claim and which colors your nickname may use.
- Waypoints, graves, pets, the journal and your land all show up in one list, **Your places**: click = show the way.

## Installation

1. Put these in your server's `mods` folder:
   - **Fabric API** for 26.3
   - **libreria-core-1.0.0.jar** (required; also bundles sgui and Polymer)
   - the modules you want (see below)
2. If you used the old standalone **KistDB** mod, remove that jar. `libreria-chests` replaces it, and your chest data
   moves along automatically from `world/kistdb` to `world/libreria/chests`.
3. Start the server. Players open everything with the **Quick Actions key (G)**, the Libreria button in the pause
   menu, or **`/l`** (also `/lib`, `/libreria`): the start screen with Items, Land & places, Progress and You, Settings
   and Help. `/l items`, `/l land`, `/l progress` and `/l me` go straight to a page; `/l help` lists every
   command by place. New players get a quiet welcome card at the top of the screen (no popup). The crafting grid in the
   inventory is left alone. Per-player settings are under **`/l settings`**.
4. Operators set the house rules in the **admin window**: `/l admin` (or Settings › Server settings). Claim budgets,
   search and supply ranges, the sleep percentage, AFK time, the tab list title, the welcome line, links (Discord,
   website, rules, live map, vote), leaderboards, how many foes a weapon needs to earn a name, and whether villagers and
   pets get names. Changes apply at once; no config files, no restart.

## Modules

| Jar | What it does | Commands |
|---|---|---|
| **libreria-core** | The framework: the four places and the start screen, **Your places** (one list for everything you can be shown the way to), real windows (dialogs) for settings and admin, one-time tips, per-player and server-wide settings, short notification cards at the top instead of chat, a quiet line at the top for place names, beams to locations, the tab list (server name, who's online, your time here, AFK markers), a death recap window, server links, a quiet welcome and Help, name plates, storage and the resource pack. And the **chronicle**: the story of the world, written as it happens (who arrived when, who was first in the Nether or the End, who slew the Wither or the Ender Dragon, who found which village, peak or lake, which weapon earned a name, which pet was tamed or lost, epic achievements, challenges, playtime milestones, the age of the world). What happened before the chronicle was there counts too: at start it reads the players' own advancement and statistics files, so nobody is called the first to do what somebody did earlier. Read it in its own window, grouped by day, filtered to "Only me" or "Big moments"; big moments also show as one quiet chat line (admins can turn that off). **Profiles**: You › Your profile and You › Players (everybody who ever played here, also before Libreria): a showcase of three achievements, playtime, achievements, points, records, and in the tooltip since when, items, monsters slain, server goals and first discoveries; also offline, and from a click on a name in chat, `/profile <name>` or sneak + right-click on a player. **While you were away**: back after half an hour or more, a card with what others did meanwhile (only when nothing else is at the top) and a chat line with [Read] | `/l` (`/lib`, `/libreria`), `/l items\|land\|progress\|me`, `/l settings`, `/l admin`, `/l links`, `/l start`, `/l help`, `/places`, `/chronicle`, `/profile`, `/players` |
| **libreria-chests** | An index of your chests: search with filters and pinned items (shown the way, with distance), a **⇅ Sort** button in every chest, barrel and shulker box (left-click sorts, right-click deposits matching items, shift-click: the chest's options), sorting with a double-click on an empty slot, quick stack (only into your own chests: never on someone else's land or into a chest someone else placed), and leaving a chest out of search (sneak + left-click). Small labels float above your chests (what each holds most of; only you see them). Turn them off for all chests in the settings and give a single chest one anyway in its options, or the other way round. **Supply chests**: chests on your own land that you mark (off by default) supply the recipe book (click a recipe, missing items come from them) and refill your hotbar | `/chest` (your items), `/chest find <text>`, `/chest search <item>`, `/chest where <item>`, `/chest sort`, `/chest noindex` |
| **libreria-tools** | Switches to your next tool before the current one breaks (adjustable threshold; by default tools may break), always-visible durability, refills blocks and food, and warns about nearly broken armor and a full inventory | – |
| **libreria-waypoints** | Shared waypoints, in Your places (click = show the way): everyone can make them, and each one is visible to everyone (default), only you, or selected players. Your last death point is in Your places too, and gets a clickable chat line. Optional: send waypoints to Xaero's Minimap | `/waypoint` (`/wp`), `/waypoint set <name>`, `/waypoint go <name>`, `/waypoint private\|public <name>`, `/waypoint remove <name>`, `/waypoint xaero <name\|all>`, `/deathpoint` |
| **libreria-info** | A tooltip for whatever you look at within reach (blocks: at the top of your screen with the pack; animals and players: floating above them) (crop growth, honey, chest fill, animal health, horse stats, and other players' achievements and completion), compass and clock info in your hand, a horse stats card (sneak + right-click), a statistics menu with top lists, floating leaderboards that operators place (time played, achievement points, mobs, distance), and place names: walk into a village, a temple or another stretch of land and its name fades in, small, at the top of your screen. Every village and temple and every stretch of forest, desert or sea has its own English name (Oakhaven, Ravenford, the Whispering Woods), and no name is ever used twice. Peaks, big caverns and lakes are found by the shape of the land and get names too (Mount Ravenfell, the Echoing Hollow, Lake Silvermere). Every villager gets a unique name too (rename one with a name tag if you like). Everything you find goes in your journal (a tab of Your places). Every place has one discoverer for the whole server: the first one there sees "first discovery!" in gold (and it goes in the chronicle), whoever comes later sees "found by Anna", and where you've been before it's just the name. The journal says who found each place first and how many you found before anyone else (also on your profile). **Weapons with a story**: swords, axes, bows, crossbows, tridents, maces and spears count what they felled, in their tooltip; after 150 foes (adjustable) or one boss they earn a name of their own that's never given twice (a name a player gave is never changed). The colour of a named weapon shows how good its enchantments are: white, green, aqua, purple, gold. **Found gear with a story**: anything enchantable from a chest, a vault, the sea, the sand, a piglin or a monster gets a text of its own the first time a player takes it, never the same one twice on a server: some 1,420 written lines (dark, sketchy, corny, plain funny, quiet lore and nods to the games a lot of us grew up with, per kind of item and per place, the Nether and the End too), 65 stories told in some 350 named parts (people, places and whole sets: a knight's armour, a miner's kit, a fisher's, an archer's, a pilot's…; the tooltip says which story and which part), and some 7,800 combinations of sentence families; under it, where, on which day and by whom it was found. Holding every part of a story is an achievement (Collector). The **Lore book** (Items › Lore book, `/lore`) has every story you know a part of to read in order (missing parts as "???", with what and roughly where), and everything you found yourself. **Tooltip details** (a setting, on): what every potion effect does (Bad Omen: a raid in a village, an ominous trial at a trial spawner…), what some 200 blocks and items are for when that isn't obvious (a magma block on land and underwater, soul sand, honey, lodestones, respawn anchors, workstations, brewing ingredients…), under every enchantment what it does, next to the attack damage what enchantments add in yellow ("(+3)", "(+12.5 vs undead)"), and an enchantment's own attribute as a line ("+1 Water Movement Efficiency (Depth Strider)"); only in what your own client sees, the items stay as they are. **Your own words**: hold something, sneak and right-click an anvil to write up to three lines in its tooltip, under your name, for a level. **Pets**: tamed wolves, cats, parrots, horses, donkeys, mules and llamas get a unique name; Your places shows where each is (click = show the way), the land maps mark them, and when one dies you hear where and how, also when you were offline | `/stats`, `/statistics`, `/journal`, `/pets`, `/leaderboard` (ops), `/leaderboard remove` (ops) |
| **libreria-comfort** | Double doors open together, sit on stairs and slabs, right-click harvesting with automatic replanting, and sleeping together: a card shows how many sleep and how many are needed (the percentage is an admin setting). The night doesn't jump to morning: the time runs fast (a whole night in 15 seconds, an admin setting) with a card showing the time, and leaving your bed stops it there. **Napping** (an admin setting): in the day, click a bed and a card says a second click naps until evening; that needs as many players in bed as at night and works once every few days for the whole world (3 by default), so it can't speed up crops | – |
| **libreria-graves** | *Opt-in, off by default.* When you die, your items go into a virtual grave: explosion- and lava-proof, only you can open it, and everything returns to the same slots. Your graves are in Your places, next to your death point, with the on/off switch | `/grave`, `/grave beam` |
| **libreria-collector** | About 1,400 achievements without rewards, in nine tabs: Collecting (50+ collections, "x of y", up to *Item Completionist*), Mining, Combat, Farming & animals, Exploring (distances, and with Info: places, biomes, villages, peaks, lakes, caverns, every kind of structure), Crafting & building, Playtime & life, Together and Secrets. Most come as ladders of grades (mine 100 stone → 1,000 → 10,000 → 50,000), each grade with its own name, and every achievement is a title. Everything counts from Minecraft's own statistics, so what players did before still counts; when new achievements arrive, what you already did is given quietly. They also appear as tabs in the vanilla advancements screen (L), with toasts. Six rarities with colors; others get one chat line for everything you earn (hover an achievement for what it is) and banners for rare and better. Pick a title (or click **[Use as title]** in chat) and it floats above your name. Funny ones (*Creeper Snack*, *Butterfingers*, *Why So Many Tables?*: what killed you, what you ate, threw, dropped and made), corny ones (*One in a Melon*, *Can't Beet This*, *Ewe Hungry?*, *Full of Hot Air*) and very long ones (*Planet Cracker*, *Extinction Event*, *Part of the Furniture*). **Secrets**: 83 hidden achievements, shown as "???" with a vague clue until you find them (die in a berry bush, name a sheep jeb_, stand on the Nether roof, play at 4 in the night, finish the dragon with a bed), plus a ladder for how many you found. **Records**: the server's records (longest fall survived, longest elytra flight, fastest, farthest out, longest without dying, longest session, hardest hit, most levels, deepest) in Progress › Records; taking one gives you a card; the others hear who has it now in chat and the chronicle keeps it, at most once every 15 minutes per record, so players travelling together who keep passing each other don't fill the chat (and taking one counts for the ladder once per record and per holder you take it from, so passing one back and forth with a friend counts once each). **Trophies**: a record that's a real feat (a fall of 150 m or more, a flight of 10 km, 80 m/s, 25 km out, 48 hours without dying, a hit of 50 hearts, 100 levels; the longest session and the deepest point have none) is a trophy, and only its holder has it: a small gold emblem you wear before your title, chosen with a click in Records. Beat someone's record and the trophy is yours. Taking records is a ladder of titles (*Record Breaker* and up). **Showcase**: three achievements of your choice (or your rarest) on your profile, chosen with a click on your own profile or `/showcase`. **Server goals**: one goal at a time for everybody together, chosen from 145 (mine, fight, farm, travel, make, build, live), a week by default: an amount per player per week × the players seen in the last two weeks × the length × the size the admin sets, rounded to a friendly number. Only what you do while it runs counts (from the statistics). Reached in time: everyone with at least 1% gets the goal's achievement, the one who did the most its *Champion* one (common and rare; uncommon and epic for a hard goal), in the **Together** tab, plus ladders for goals helped and won. Progress › Server goal shows the bar and the top five; cards and the action bar only show when nothing else is there | `/achievements`, `/collection`, `/titles`, `/goal`, `/records`, `/showcase` |
| **libreria-nicknames** | Nicknames above your head, in the tab list and in chat (real name on hover). Colours unlock with achievements | `/nick`, `/nick <name>`, `/nick reset` |
| **libreria-claims** | Stand in your base and type `/claim`. Your budget grows with playtime and achievement points. The land map (**sneak + swap hands (F)**, `/claim map`, or the map button right of the hotbar shows the key) swoops your camera up into a third-person view of the real world: claimed land stands there as see-through glass buildings with bright frames (green = yours, blue = trusted, red = others), with names and sizes. A golden box marks the chunk you'd claim; a card at the top says whose land it is and how much you may still claim. The mouse turns and tilts the view, WASD glides, click claims (the building grows out of the ground), right-click releases, jump names the piece of your land in the middle, scroll zooms, sneak, Q or the key again goes back (getting hurt closes it; Escape can't, it only opens the pause menu on the player's own computer). The key can be set to F alone or off. Waypoints, your death point, graves and friends who opted in show up in it too. A **land map item** to hold draws the claims around you on a real map. Every connected piece of your land can have a name of its own (`/claim name`, or jump on the land map); anyone who walks in sees it fade in at the top, like a village. Protects against building, opening containers and touching animals; trusted players; doors for visitors are configurable | `/claim`, `/claim map`, `/claim name [name]`, `/claim mapitem`, `/claim show`, `/claim info`, `/claim release`, `/claim trust <player>`, `/claim untrust <player>`, `/claim admin` (ops) |
| **libreria-glint** | Light rarity frames around uncommon/rare/epic items in your inventory (rare and epic ones sparkle). Every tool and weapon shows its enchantments itself: a texture part per enchantment and level (blood on a Sharpness sword, gold on Smite, heat for Fire Aspect, a honed edge for Efficiency…) and in your hand an animated aura around it (flames, holy light, venom, wind…); every combination shows, and such an item has no enchantment glint (its effect takes its place). Worn armour glows with its enchantment for others (embers, runes, rime, smoke, wind…, stronger per level), a trimmed piece with a seam of light round its trim instead. Four pieces sharing a protection (or Thorns, Mending, a curse) make an armour set: one effect around you (a ward of turning rings, an ember mantle, a bulwark of smoke and stone, wind, thorns flying off you…), graded by the weakest piece, with the material's accent; only in third person for yourself. Also a custom texture per enchanted book, particles around players with other enchanted gear, and **seamless glass**: glass, tinted and stained glass and their panes without lines between the blocks, so a wall of glass is one window (the items keep their vanilla look); with OptiFine or Continuity (also with Iris) real connected glass, a frame round the whole window, inner corners included. Each player can switch it off (Seamless glass). With shaders (Iris, OptiFine): a tool or weapon with an effect still has no enchantment glint (the client is told so, which every shader pack respects), and **Glow with shaders** (a setting, off) sends glow maps (LabPBR) so the effects light up in shader packs that read resource pack PBR, the same pixels that glow without shaders; the pack has to be told to read them (Complementary: IPBR+ Emissive Mode on "labPBR > IPBR+", or RP Support on labPBR; BSL: Advanced Materials on). Checked with Iris 1.11.7 on 26.3 in the shaders workflow (Complementary both ways, BSL). The Effects window sets how much you see and where; every effect can be switched off on its own | via G › You › Effects |

## The resource pack

When the server starts, core builds the Libreria resource pack from the assets of every installed module and hosts it
itself, on the same port as the server (via Polymer AutoHost). Players are asked once when they join whether to use it.
Anyone who picks "No" just plays on with the plain look; everything keeps working.

With the pack you get:
- **One look for everything**: dark wood with a bronze edge, like the cards at the top of the screen. Minecraft's own
  buttons, checkboxes, sliders, text fields and scroll bars get it too (in every screen while you play here, from the
  pause menu to the server's windows), each with room around it; the square under the mouse in an inventory becomes
  bronze corners. `LIBRERIA_LOOK=glass` or `slate` in front of `generate.py` draws everything in another look.
- **Windows of our own**: the start screen, the page of each place and the settings are drawn completely by the pack
  over a chest screen (Minecraft draws a screen's title before its slots and items, so the title can be the whole
  window). No slots in sight: cards in the colour of their place, Minecraft's pictures at twice the size, a list with a
  picture beside each row, switches you flip with one click. The cells underneath are invisible click spots, so a card
  works wherever you point at it. Your inventory is hidden while such a window is open. Without the pack the same
  pages are dialogs.
- **Menus** (chests, achievements, statistics …) on the same dark panel: the menu's items float on a tray, the bottom
  row of buttons is a band of its own, your inventory keeps real slots, and tooltips get the same panel. Coloured panes
  become little plaques. The "Inventory" line above your inventory is gone in every screen.
- **Keys as you have them**: wherever a window or tooltip names a key (the land map key, sneak to sit up, the key that
  opens Libreria), the client fills in your own key from Controls, so someone with sneak on Control reads "Left
  Control + F". Pictures the server draws, which can't know your key, say what it does ("Sneak + Swap hands").
- **The look-at tooltip** for blocks at the top of the screen (drawn in a boss bar title; the pack makes *white*
  boss bars invisible, so a custom `/bossbar` in white would only show its name), and as a card above animals: dark leather, bronze frame, the thing's icon in a slot, a gem in the
  colour of its grade, an ornamental rule and rows with small pictures and pips.
- **Your items** from your inventory: right-click the magnifying glass beside the window (under the deposit arrows) for one simple search screen in the same look: a search field on top (type right away; the results change with every letter) and below it everything you carry and everything in your chests, most first, with real totals (200 oak logs show 200; up to 999 in a slot). Click an item: the screen closes and its nearest chest lights up, with a beam; right-click: every chest with it. It's an anvil screen underneath (the only screen with a text field), drawn over by the pack. Also `/chest` and `/chest find`.
- **Two buttons** on the left edge of the inventory window, in the same look: the arrows (deposit) over the magnifying glass (search). The inventory screen has only one real button, the recipe book, and that stays the recipe book. These two are drawn by the screen's title (the pack replaces the "Crafting" text with them; crafting tables get "Crafting Table" as their title instead) and work through a click outside the window with an empty hand, which vanilla ignores. The game doesn't tell the server where you clicked, so the mouse button decides: left-click puts your items into your own nearby chests, right-click searches. Any such click beside the window does the same. (A middle-click the game only sends in creative, so sorting your inventory is a double-click on an empty slot.)
- **Land map button** right of the hotbar with its key, drawn by the action bar text in a font that moves it down.
- **Windows from the server** (dialogs, where you type or confirm: names, admin, effects, links, Help,
  the death recap) in the same look, without the warning icon Minecraft puts beside their title.
- **Menu frames** in the colour and emblem of their place: Items (teal, chest), Land & places (green, map), Progress
  (gold, trophy), You (purple, name tag).
- **Achievement banner** (Collector): when anyone earns an achievement, everyone gets a card at the top of the
  screen (icon, name, rarity, who earned it) and fireworks: everyone hears them, players nearby see rockets burst in
  the rarity's colours (the rockets exist only on their screens). Without the pack it's a coloured boss bar.
- **Glint**: rarity frames, enchant effects and book covers.

- On first start, core writes `config/polymer/auto-host.json` with `enabled: true` and `required: false`. Set
  `required` to `true` if you want to make the pack mandatory.
- Per player: Settings › You › What you see › Libreria resource pack; Glint's parts under Settings › You › Effects,
  or You › Effects. Switching makes the player's screen reload briefly.

Generated assets (only needed to change them):

```
python3 scripts/ui/generate.py                       # the look: widgets, windows, menu frames, tooltips, icons (core)
python3 scripts/ui/widths.py <vanilla assets/minecraft> core/src/main/resources/libreria/font_widths.json
python3 scripts/glint/generate.py <vanilla 26.3 assets/minecraft> glint/src/main/resources <item_components data.json>
python3 scripts/glint/armor_fx.py <vanilla 26.3 assets/minecraft>   # worn armour's glows, per material, effect and level
python3 scripts/glint/trims_fx.py <vanilla 26.3 assets/minecraft>   # the seams of light round trims
python3 scripts/glint/fx_sprites.py                                 # the sprites of the armour sets
```

(`generate.py` uses `tools_fx.py` for the tools and weapons: their texture parts and auras, layered per enchantment and
level, and `glass.py` for seamless glass and its connected textures (OptiFine format). It only replaces what it writes itself, so run it in any order with the others.)

## Building

Every push builds everything with GitHub Actions. CI then starts a real server with all modules and runs the self-tests
(`LIBRERIA-SELFTEST OK`), and starts a real client (headless) that takes screenshots of the tooltips, menus and
inventory. The jars, logs, screenshots and the generated resource pack are published to the `ci-out` branch.

Locally: `./gradlew build` (Java 25). The jars end up in `<module>/build/libs/`.

## Storage

Everything lives in the world folder under `libreria/` (settings, chests, collector, graves, claims, nicknames…), as
readable JSON files.
