# Libreria – a server-side mod framework for Minecraft 26.3 (Fabric)

Libreria is a collection of small quality-of-life mods that work together as one framework. Everything runs **on the
server only**: players join with a plain vanilla client and install nothing. Buttons, menus, beams and floating text are
built from vanilla screens and virtual entities, and the Libreria resource pack (prettier menus, tooltips and item
effects) is built, hosted and sent by the server itself.

Pick the modules you want. Each works on its own, and together they complement each other. A few examples:
- Click a collected item in your Collector menu and Chests beams you to the chest it is in.
- Your achievements decide how many chunks you can claim and which colors your nickname may use.
- Graves adds its line to the Waypoints death message.

## Installation

1. Put these in your server's `mods` folder:
   - **Fabric API** for 26.3
   - **libreria-core-1.0.0.jar** (required; also bundles sgui and Polymer)
   - the modules you want (see below)
2. If you used the old standalone **KistDB** mod, remove that jar. `libreria-chests` replaces it, and your chest data
   moves along automatically from `world/kistdb` to `world/libreria/chests`.
3. Start the server. Players open everything with **`/libreria`** (or `/lib`), the **Quick Actions key (G)** (a
   Libreria menu with the land map, your items, sorting, waypoints …; also a button in the pause menu). The crafting grid
   in the inventory is left alone. Per-player settings are under **`/libreria settings`**.

## Modules

| Jar | What it does | Commands |
|---|---|---|
| **libreria-core** | Menu, per-player settings, beams to locations, shared action-bar line, name plates, storage, the resource pack (themed menu frames per tool) | `/libreria`, `/lib`, `/libreria settings` |
| **libreria-chests** | An index of your chests: search (with beam and distance), a **⇅ Sort** button in every chest, barrel and shulker box (left-click sorts, right-click deposits matching items), middle-click sorting, quick stack, and noIndex per chest (sneak + left-click) | `/chest` (your items), `/chest find <text>`, `/chest menu`, `/chest search <item>`, `/chest where <item>`, `/chest sort`, `/chest noindex` |
| **libreria-tools** | Switches to your next tool before the current one breaks (adjustable threshold; by default tools may break), always-visible durability, refills blocks and food, and warns about nearly broken armor and a full inventory | – |
| **libreria-waypoints** | Shared waypoints with a beam: everyone can make them, and each one is visible to everyone (default), only you, or selected players. Your last death point gets a clickable message. Optional: send waypoints to Xaero's Minimap | `/waypoint` (`/wp`), `/waypoint set <name>`, `/waypoint go <name>`, `/waypoint private\|public <name>`, `/waypoint remove <name>`, `/waypoint xaero <name\|all>`, `/deathpoint` |
| **libreria-info** | A tooltip for whatever you look at within reach (blocks: at the top of your screen with the pack; animals and players: floating above them) (crop growth, honey, chest fill, animal health, horse stats, and other players' achievements and completion), compass and clock info in your hand, a horse stats card (sneak + right-click), and a statistics menu with top lists | `/stats`, `/statistics` |
| **libreria-comfort** | Double doors open together, sit on stairs and slabs, and right-click harvesting with automatic replanting | – |
| **libreria-graves** | *Opt-in, off by default.* When you die, your items go into a virtual grave: explosion- and lava-proof, only you can open it, and everything returns to the same slots | `/grave`, `/grave beam` |
| **libreria-collector** | Achievements without rewards: 27 collections ("x of y"), milestones up to *Item Completionist*, breeding (villagers and curing included), playtime and adventure. They also appear as tabs in the vanilla advancements screen (L), with toasts. Six rarities with colors; pick a title (or click **[Use as title]** in chat) and it floats above your name | `/achievements`, `/collection`, `/titles` |
| **libreria-nicknames** | Nicknames above your head, in the tab list and in chat (real name on hover). Colours unlock with achievements | `/nick`, `/nick <name>`, `/nick reset` |
| **libreria-claims** | Stand in your base and type `/claim`. Your budget grows with playtime and achievement points. The land map (**sneak + swap hands (F)**, `/claim map`, or the map button right of the hotbar shows the key) swoops your camera up into a third-person view of the real world: claimed land stands there as see-through glass buildings with bright frames (green = yours, blue = trusted, red = others), with names and sizes. A golden box marks the chunk you'd claim; a card at the top says whose land it is and how much you may still claim. WASD glides, click claims (the building grows out of the ground), right-click releases, scroll zooms, sneak, Q or the key again goes back (getting hurt closes it; Escape can't, it only opens the pause menu on the player's own computer). The key can be set to F alone or off. Protects against building, opening containers and touching animals; trusted players; doors for visitors are configurable | `/claim`, `/claim map`, `/claim show`, `/claim info`, `/claim release`, `/claim trust <player>`, `/claim untrust <player>`, `/claim admin` (ops) |
| **libreria-glint** | Light rarity frames around uncommon/rare/epic items in your inventory (rare and epic ones sparkle). Animated enchantment effects on weapons, tools and armor. Every enchantment has one, in 32 effect types (flames, bubbles, frost, lightning…). Also a custom texture per enchanted book, and particles around players with enchanted gear. Toggle per player and per effect | via `/libreria` → Glint effects |

## The resource pack

When the server starts, core builds the Libreria resource pack from the assets of every installed module and hosts it
itself, on the same port as the server (via Polymer AutoHost). Players are asked once when they join whether to use it.
Anyone who picks "No" just plays on with the plain look; everything keeps working.

With the pack you get:
- **Menus** with a frame per tool: parchment, a bronze border and a coloured title ribbon with an emblem; empty slots
  disappear into the parchment and coloured panes become little plaques.
- **The look-at tooltip** for blocks at the top of the screen (drawn in a boss bar title; the pack makes *white*
  boss bars invisible, so a custom `/bossbar` in white would only show its name), and as a card above animals: dark leather, bronze frame, the thing's icon in a slot, a gem in the
  colour of its grade, an ornamental rule and rows with small pictures and pips.
- **Your items** from your inventory: the recipe book button becomes a magnifying glass (Minecraft's own grey button) that opens one simple search screen in Minecraft's own style: a search field on top (type right away; the results change with every letter) and below it everything you carry and everything in your chests, most first, with real totals (200 oak logs show 200; up to 999 in a slot). Click an item: the screen closes and its nearest chest lights up, with a beam; right-click: every chest with it. It's an anvil screen underneath (the only screen with a text field), drawn over by the pack. Also `/chest` and `/chest find`.
- **Sort button** on the left edge of the inventory window, in the same style: left-click sorts, right-click quick-stacks into nearby chests. The inventory screen has room for only one real button, so this one is drawn by the screen's title (the pack replaces the "Crafting" text with it; crafting tables get "Crafting Table" as their title instead) and works through a click outside the window with an empty hand, which vanilla ignores. So any such click outside the window sorts.
- **Land map button** right of the hotbar with its key, drawn by the action bar text in a font that moves it down.
- **Windows from the server** (dialogs: your items, the quick menu) without the warning icon Minecraft puts beside
  their title.
- **Achievement banner** (Collector): when anyone earns an achievement, everyone gets a card at the top of the
  screen (icon, name, rarity, who earned it) and fireworks: everyone hears them, players nearby see rockets burst in
  the rarity's colours (the rockets exist only on their screens). Without the pack it's a coloured boss bar.
- **Glint**: rarity frames, enchant effects and book covers.

- On first start, core writes `config/polymer/auto-host.json` with `enabled: true` and `required: false`. Set
  `required` to `true` if you want to make the pack mandatory.
- Per player: `/libreria settings` → General → Libreria resource pack; Glint's parts under `/libreria settings` →
  Glint, or `/libreria` → Glint effects. Switching makes the player's screen reload briefly.

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
