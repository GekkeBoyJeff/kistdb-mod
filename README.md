# KistDB – server-side Fabric-mod (Minecraft 26.3)

Sorteren, een kisten-database met iconen-GUI en een lichtstraal naar de juiste kist. **Alleen op de server installeren**: spelers hebben een gewone vanilla client nodig.

## Installeren
1. Download `kistdb-x.y.z.jar` (zie *Actions* → laatste run → *Artifacts*, of de branch `ci-out`).
2. Zet hem samen met **Fabric API voor 26.3** in de `mods`-map van je Fabric-server.
3. Herstart de server.

## Gebruik
| Wat | Hoe |
|---|---|
| **Inventory sorteren** | E-scherm: klik op het gloeiende trechter-icoon in het crafting-uitvak (zichtbaar als het 2×2-grid leeg is). |
| **Kist sorteren** | In een kist, ton of shulker box: **middelklik** (of dubbelklik op een leeg slot). In het onderste deel sorteer je zo je eigen inventory. |
| **Zoeken** | Rechtsklik op het icoon in het E-scherm, of `/kist`. Je krijgt een menu met iconen van alles in je kisten. |
| **Straal naar de kist** | Klik een item in het menu: een gloeiende lijn vanaf je borst naar de kist, een omlijning en lichtzuil, het item met aantal en afstand erboven, en een pijl met afstand in je actiebalk. Alleen jij ziet dit. |
| **Alle kisten met een item** | Rechtsklik op het item in het menu. |
| **noIndex** | Sneak + linksklik met lege hand op een kist, of shift-klik in *Mijn kisten*, of `/kist noindex`. |
| **Mijn kisten** | Overzicht van alle kisten in KistDB: inhoud op afstand bekijken (rechtsklik), straal (klik), noIndex (shift-klik), vergeten (Q). |

### Commando's (geen op-rechten nodig)
`/kist` · `/kist zoek <item>` · `/kist sorteer` · `/kist noindex` · `/kist stop`

## Hoe het werkt
- Elke kist (ook gevangen en koperen kisten), ton en shulker box die **jij** opent, komt in **jouw** database (`<wereld>/kistdb/<uuid>.json`).
- De inhoud wordt live gelezen zodra de chunk geladen is. Is de kist ver weg, dan zie je de laatst geziene inhoud.
- Zoeken op naam werkt met de Engelse naam of het item-ID (de server kent geen Nederlandse namen). In het menu zelf zie je wel de Nederlandse namen.

## Bouwen
`./gradlew build` (Java 25). Gebruikt [sgui](https://github.com/Patbox/sgui) en [Polymer virtual entities](https://github.com/Patbox/polymer) (meegebundeld in de jar).
