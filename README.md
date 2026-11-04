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
3. Start the server. Players open everything with **`/libreria`** (or `/lib`), or with the **button in the crafting
   output slot** of their inventory: left-click sorts, right-click opens the Libreria menu.
   Per-player settings are under **`/libreria settings`**.

## Modules

| Jar | What it does | Commands |
|---|---|---|
| **libreria-core** | Menu, per-player settings, beams to locations, shared action-bar line, name plates, storage, the resource pack (themed menu frames per tool) | `/libreria`, `/lib`, `/libreria settings` |
| **libreria-chests** | An index of your chests: search (with beam and distance), a **⇅ Sort** button in every chest, barrel and shulker box (left-click sorts, right-click deposits matching items), middle-click sorting, quick stack, and noIndex per chest (sneak + left-click) | `/chest`, `/chest search <item>`, `/chest where <item>`, `/chest sort`, `/chest noindex` |
| **libreria-tools** | Switches to your next tool before the current one breaks (adjustable threshold; by default tools may break), always-visible durability, refills blocks and food, and warns about nearly broken armor and a full inventory | – |
| **libreria-waypoints** | Shared waypoints with a beam: everyone can make them, and each one is visible to everyone (default), only you, or selected players. Your last death point gets a clickable message. Optional: send waypoints to Xaero's Minimap | `/waypoint` (`/wp`), `/waypoint set <name>`, `/waypoint go <name>`, `/waypoint private\|public <name>`, `/waypoint remove <name>`, `/waypoint xaero <name\|all>`, `/deathpoint` |
| **libreria-info** | A floating tooltip above whatever you look at within reach (crop growth, honey, chest fill, animal health, horse stats, and other players' achievements and completion), compass and clock info in your hand, a horse stats card (sneak + right-click), and a statistics menu with top lists | `/stats`, `/statistics` |
| **libreria-comfort** | Double doors open together, sit on stairs and slabs, and right-click harvesting with automatic replanting | – |
| **libreria-graves** | *Opt-in, off by default.* When you die, your items go into a virtual grave: explosion- and lava-proof, only you can open it, and everything returns to the same slots | `/grave`, `/grave beam` |
| **libreria-collector** | Achievements without rewards: 27 collections ("x of y"), milestones up to *Item Completionist*, breeding (villagers and curing included), playtime and adventure. They also appear as tabs in the vanilla advancements screen (L), with toasts. Six rarities with colors; pick a title (or click **[Use as title]** in chat) and it floats above your name | `/achievements`, `/collection`, `/titles` |
| **libreria-nicknames** | Nicknames above your head, in the tab list and in chat (real name on hover). Colours unlock with achievements | `/nick`, `/nick <name>`, `/nick reset` |
| **libreria-claims** | Stand in your base and type `/claim`. Your budget grows with playtime and achievement points. A map menu shows your land; borders light up. Protects against building, opening containers and touching animals; trusted players; doors for visitors are configurable | `/claim`, `/claim map`, `/claim show`, `/claim info`, `/claim release`, `/claim trust <player>`, `/claim untrust <player>`, `/claim admin` (ops) |
| **libreria-glint** | Light rarity frames around uncommon/rare/epic items in your inventory (rare and epic ones sparkle). Animated enchantment effects on weapons, tools and armor. Every enchantment has one, in 32 effect types (flames, bubbles, frost, lightning…). Also a custom texture per enchanted book, and particles around players with enchanted gear. Toggle per player and per effect | via `/libreria` → Glint effects |

## The resource pack

When the server starts, core builds the Libreria resource pack from the assets of every installed module and hosts it
itself, on the same port as the server (via Polymer AutoHost). Players are asked once when they join whether to use it.
Anyone who picks "No" just plays on with the plain look; everything keeps working.

With the pack you get:
- **Menus** with a frame per tool: parchment, a bronze border and a coloured title ribbon with an emblem; empty slots
  disappear into the parchment and coloured panes become little plaques.
- **The look-at tooltip** as an adventure card: dark leather, bronze frame, the thing's icon in a slot, a gem in the
  colour of its grade, an ornamental rule and rows with small pictures and pips.
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
