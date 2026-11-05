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
they're useful, and can all be read back under Getting started › Tips.

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
   and Getting started. `/l items`, `/l land`, `/l progress` and `/l me` go straight to a page; `/l help` lists every
   command by place. New players get a quiet welcome card at the top of the screen (no popup). The crafting grid in the
   inventory is left alone. Per-player settings are under **`/l settings`**.
4. Operators set the house rules in the **admin window**: `/l admin` (or Settings › Server settings). Claim budgets,
   search and supply ranges, the sleep percentage, AFK time, the tab list title, the welcome line, links (Discord,
   website, rules, live map, vote), leaderboards, how many foes a weapon needs to earn a name, and whether villagers and
   pets get names. Changes apply at once; no config files, no restart.

## Modules

| Jar | What it does | Commands |
|---|---|---|
| **libreria-core** | The framework: the four places and the start screen, **Your places** (one list for everything you can be shown the way to), real windows (dialogs) for settings and admin, one-time tips, per-player and server-wide settings, short notification cards at the top instead of chat, a quiet line at the top for place names, beams to locations, the tab list (server name, who's online, your time here, AFK markers), a death recap window, server links, a quiet welcome and Getting started, name plates, storage and the resource pack. And the **chronicle**: the story of the world, written as it happens (who arrived when, who was first in the Nether or the End, who slew the Wither or the Ender Dragon, who found which village, peak or lake, which weapon earned a name, which pet was tamed or lost, epic achievements, challenges, playtime milestones, the age of the world). Read it in its own window, grouped by day, filtered to "Only me" or "Big moments"; big moments also show as one quiet chat line (admins can turn that off) | `/l` (`/lib`, `/libreria`), `/l items\|land\|progress\|me`, `/l settings`, `/l admin`, `/l links`, `/l start`, `/l help`, `/places`, `/chronicle` |
| **libreria-chests** | An index of your chests: search with filters and pinned items (shown the way, with distance), a **⇅ Sort** button in every chest, barrel and shulker box (left-click sorts, right-click deposits matching items, shift-click: the chest's options), middle-click sorting, quick stack (only into your own chests: never on someone else's land or into a chest someone else placed), and leaving a chest out of search (sneak + left-click). Small labels float above your chests (what each holds most of; only you see them). **Supply chests**: chests on your own land that you mark (off by default) supply the recipe book (click a recipe, missing items come from them) and refill your hotbar | `/chest` (your items), `/chest find <text>`, `/chest search <item>`, `/chest where <item>`, `/chest sort`, `/chest noindex` |
| **libreria-tools** | Switches to your next tool before the current one breaks (adjustable threshold; by default tools may break), always-visible durability, refills blocks and food, and warns about nearly broken armor and a full inventory | – |
| **libreria-waypoints** | Shared waypoints, in Your places (click = show the way): everyone can make them, and each one is visible to everyone (default), only you, or selected players. Your last death point is in Your places too, and gets a clickable chat line. Optional: send waypoints to Xaero's Minimap | `/waypoint` (`/wp`), `/waypoint set <name>`, `/waypoint go <name>`, `/waypoint private\|public <name>`, `/waypoint remove <name>`, `/waypoint xaero <name\|all>`, `/deathpoint` |
| **libreria-info** | A tooltip for whatever you look at within reach (blocks: at the top of your screen with the pack; animals and players: floating above them) (crop growth, honey, chest fill, animal health, horse stats, and other players' achievements and completion), compass and clock info in your hand, a horse stats card (sneak + right-click), a statistics menu with top lists, floating leaderboards that operators place (time played, achievement points, mobs, distance), and place names: walk into a village, a temple or another stretch of land and its name fades in, small, at the top of your screen. Every village and temple and every stretch of forest, desert or sea has its own English name (Oakhaven, Ravenford, the Whispering Woods), and no name is ever used twice. Peaks, big caverns and lakes are found by the shape of the land and get names too (Mount Ravenfell, the Echoing Hollow, Lake Silvermere). Every villager gets a unique name too (rename one with a name tag if you like). Everything you find goes in your journal (a tab of Your places). **Weapons with a story**: swords, axes, bows, crossbows, tridents, maces and spears count what they felled, in their tooltip; after 150 foes (adjustable) or one boss they earn a name of their own that's never given twice (a name a player gave is never changed). The colour of a named weapon shows how good its enchantments are: white, green, aqua, purple, gold. **Pets**: tamed wolves, cats, parrots, horses, donkeys, mules and llamas get a unique name; Your places shows where each is (click = show the way), the land maps mark them, and when one dies you hear where and how, also when you were offline | `/stats`, `/statistics`, `/journal`, `/pets`, `/leaderboard` (ops), `/leaderboard remove` (ops) |
| **libreria-comfort** | Double doors open together, sit on stairs and slabs, right-click harvesting with automatic replanting, and sleeping together: a card shows how many sleep and how many are needed (the percentage is an admin setting) | – |
| **libreria-graves** | *Opt-in, off by default.* When you die, your items go into a virtual grave: explosion- and lava-proof, only you can open it, and everything returns to the same slots. Your graves are in Your places, next to your death point, with the on/off switch | `/grave`, `/grave beam` |
| **libreria-collector** | About 400 achievements without rewards, in seven tabs: Collecting (50+ collections, "x of y", up to *Item Completionist*), Mining, Combat, Farming & animals, Exploring (distances, and with Info: places, biomes, villages, peaks, lakes, caverns, every kind of structure), Crafting & building, and Playtime & life. Most come as ladders of grades (mine 100 stone → 1,000 → 10,000 → 50,000), each grade with its own name, and every achievement is a title. Everything counts from Minecraft's own statistics, so what players did before still counts; when new achievements arrive, what you already did is given quietly. They also appear as tabs in the vanilla advancements screen (L), with toasts. Six rarities with colors; others see banners and chat only for rare and better. Pick a title (or click **[Use as title]** in chat) and it floats above your name | `/achievements`, `/collection`, `/titles` |
| **libreria-nicknames** | Nicknames above your head, in the tab list and in chat (real name on hover). Colours unlock with achievements | `/nick`, `/nick <name>`, `/nick reset` |
| **libreria-claims** | Stand in your base and type `/claim`. Your budget grows with playtime and achievement points. The land map (**sneak + swap hands (F)**, `/claim map`, or the map button right of the hotbar shows the key) swoops your camera up into a third-person view of the real world: claimed land stands there as see-through glass buildings with bright frames (green = yours, blue = trusted, red = others), with names and sizes. A golden box marks the chunk you'd claim; a card at the top says whose land it is and how much you may still claim. The mouse turns and tilts the view, WASD glides, click claims (the building grows out of the ground), right-click releases, jump names the piece of your land in the middle, scroll zooms, sneak, Q or the key again goes back (getting hurt closes it; Escape can't, it only opens the pause menu on the player's own computer). The key can be set to F alone or off. Waypoints, your death point, graves and friends who opted in show up in it too. A **land map item** to hold draws the claims around you on a real map. Every connected piece of your land can have a name of its own (`/claim name`, or jump on the land map); anyone who walks in sees it fade in at the top, like a village. Protects against building, opening containers and touching animals; trusted players; doors for visitors are configurable | `/claim`, `/claim map`, `/claim name [name]`, `/claim mapitem`, `/claim show`, `/claim info`, `/claim release`, `/claim trust <player>`, `/claim untrust <player>`, `/claim admin` (ops) |
| **libreria-glint** | Light rarity frames around uncommon/rare/epic items in your inventory (rare and epic ones sparkle). Animated enchantment effects on weapons, tools and armor. Every enchantment has one, in 32 effect types (flames, bubbles, frost, lightning…). Also a custom texture per enchanted book, and particles around players with enchanted gear: one calm effect per item (stronger with a higher enchantment level), a slow orbit for a full armour set, never more than three sources per player. Your own armour only shows in third person (F5); the Effects window sets the intensity and where you see them | via G › You › Effects |

## The resource pack

When the server starts, core builds the Libreria resource pack from the assets of every installed module and hosts it
itself, on the same port as the server (via Polymer AutoHost). Players are asked once when they join whether to use it.
Anyone who picks "No" just plays on with the plain look; everything keeps working.

With the pack you get:
- **Menus** in Minecraft's own grey style, the same for every tool: the tool's colour as a thin line under the title
  with its emblem; empty slots melt into the panel and coloured panes become little plaques.
- **The look-at tooltip** for blocks at the top of the screen (drawn in a boss bar title; the pack makes *white*
  boss bars invisible, so a custom `/bossbar` in white would only show its name), and as a card above animals: dark leather, bronze frame, the thing's icon in a slot, a gem in the
  colour of its grade, an ornamental rule and rows with small pictures and pips.
- **Your items** from your inventory: the recipe book button becomes a magnifying glass (Minecraft's own grey button) that opens one simple search screen in Minecraft's own style: a search field on top (type right away; the results change with every letter) and below it everything you carry and everything in your chests, most first, with real totals (200 oak logs show 200; up to 999 in a slot). Click an item: the screen closes and its nearest chest lights up, with a beam; right-click: every chest with it. It's an anvil screen underneath (the only screen with a text field), drawn over by the pack. Also `/chest` and `/chest find`.
- **Sort button** on the left edge of the inventory window, in the same style: left-click sorts, right-click quick-stacks into nearby chests. The inventory screen has room for only one real button, so this one is drawn by the screen's title (the pack replaces the "Crafting" text with it; crafting tables get "Crafting Table" as their title instead) and works through a click outside the window with an empty hand, which vanilla ignores. So any such click outside the window sorts.
- **Land map button** right of the hotbar with its key, drawn by the action bar text in a font that moves it down.
- **Windows from the server** (dialogs: the start screen and its pages, settings, admin, effects, links, Getting
  started, the death recap) without the warning icon Minecraft puts beside their title.
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
python3 scripts/ui/generate.py                       # tooltip, menu frames, icons (core)
python3 scripts/ui/widths.py <vanilla assets/minecraft> core/src/main/resources/libreria/font_widths.json
python3 scripts/glint/generate.py <vanilla 26.3 assets/minecraft> glint/src/main/resources <item_components data.json>
```

## Building

Every push builds everything with GitHub Actions. CI then starts a real server with all modules and runs the self-tests
(`LIBRERIA-SELFTEST OK`), and starts a real client (headless) that takes screenshots of the tooltips, menus and
inventory. The jars, logs, screenshots and the generated resource pack are published to the `ci-out` branch.

Locally: `./gradlew build` (Java 25). The jars end up in `<module>/build/libs/`.

## Storage

Everything lives in the world folder under `libreria/` (settings, chests, collector, graves, claims, nicknames…), as
readable JSON files.
