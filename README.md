# Handig – een server-side modframework voor Minecraft 26.3 (Fabric)

Handig is een set kleine mods die samen één geheel vormen. Alles draait **alleen op de server**: spelers joinen met een
gewone Minecraft-client en installeren niets. Knoppen, menu's, stralen en zwevende tekst werken via slimme trucs met
gewone vanilla-schermen en virtuele entities. Het Glans-pakket stuurt de server zelf mee.

Je kiest zelf welke modules je installeert. Ze werken los van elkaar, en als ze samen draaien vullen ze elkaar aan.
Voorbeelden: klik in je verzameling op een item en KistDB wijst de kist aan waar het ligt. Je prestaties bepalen
hoeveel chunks je mag claimen en welke kleuren je nickname mag hebben. Graf zet zijn regel in het sterfbericht van
Wegwijzer.

## Installeren

1. Zet in de `mods`-map van je server:
   - **Fabric API** voor 26.3
   - **handig-kern-1.0.0.jar** (verplicht; bevat ook sgui en Polymer)
   - de modules die je wilt (zie hieronder)
2. Gebruikte je eerder de losse **KistDB**-mod? Haal die oude jar weg. `handig-kistdb-1.1.0.jar` vervangt hem, en je
   kistdata blijft gewoon bewaard.
3. Start de server. Spelers openen alles via **`/handig`**, of door met de **knop in het crafting-uitvak** van hun
   inventory te klikken: linksklik = sorteren, rechtsklik = het Handig-menu.
   Instellingen per speler staan onder **`/opties`**.

## Modules

| Jar | Wat het doet | Commando's |
|---|---|---|
| **handig-kern** | Menu, instellingen per speler, stralen naar plekken, HUD-regel, naamplaatjes, opslag | `/handig`, `/opties` |
| **handig-kistdb** | Database van je kisten: zoeken (met straal en afstand), sorteren (knop, middelklik of dubbelklik in kisten), snel opbergen, noIndex per kist (sneak + linksklik) | `/kist`, `/kist zoek`, `/kist sorteer`, `/kist noindex` |
| **handig-gereedschap** | Wisselt automatisch naar je volgende tool voordat de huidige breekt (instelbare grens, standaard mogen tools breken), durability altijd in beeld, blokken en eten bijvullen, waarschuwing bij bijna kapot harnas en een volle inventory | – |
| **handig-wegwijzer** | Waypoints met straal, je laatste sterfplek met een klikbare melding | `/punt`, `/punt zet <naam>`, `/punt ga <naam>`, `/sterfplek` |
| **handig-info** | Kijk-info (groei, honing, kistvulling, levens, paardwaarden…), kompas/klok-info in je hand, paard-statistieken (sneak + rechtsklik), een statistieken-menu met top-lijstjes | `/statistieken`, `/stats` |
| **handig-gemak** | Dubbele deuren gaan samen open, zitten op trappen en halve platen, rechtsklik-oogsten met herplanten | – |
| **handig-graf** | *Opt-in, standaard uit.* Je spullen gaan bij doodgaan in een virtueel graf (explosie- en lavabestendig, alleen voor jou) en komen terug in dezelfde vakken | `/graf`, `/graf straal` |
| **handig-verzamelaar** | Prestaties zonder beloningen: 27 verzamelingen ("x van x"), mijlpalen tot *Item Completionist*, fokken (ook dorpelingen en genezen), speeltijd, avontuur. Zes zeldzaamheden met kleuren. Je titel zweeft boven je naam | `/prestaties`, `/titel` |
| **handig-naam** | Nicknames boven je hoofd, in de tablijst en in de chat (echte naam in de hover). Kleuren ontgrendel je met prestaties | `/nick`, `/nick <naam>`, `/nick reset` |
| **handig-gebied** | Chunks claimen via een kaart-GUI. Je budget groeit met speeltijd en prestatiepunten. Bescherming tegen bouwen, kisten openen en dieren aanraken; vertrouwde spelers; deuren voor bezoekers instelbaar | `/gebied`, `/gebied claim`, `/gebied vrij`, `/gebied vertrouw <speler>`, `/gebied beheer` (op) |
| **handig-glans** | Geanimeerde enchant-effecten op wapens, gereedschap en harnas: elke betovering heeft er een, 32 soorten (vlammen, bubbels, sneeuw, bliksem…). Een eigen plaatje per betoverd boek, deeltjes rond spelers met betoverde spullen. Per speler en per effect aan/uit | via `/handig` → Glans-effecten |

## Glans en het resourcepack

Glans bouwt bij het starten van de server automatisch een resourcepack en host het zelf, op dezelfde poort als de
server (via Polymer AutoHost). Spelers krijgen bij het joinen één keer de vraag of ze het willen gebruiken. Kies je
"Nee", dan speel je gewoon verder met de vanilla look.

- De eerste keer schrijft Glans `config/polymer/auto-host.json` met `enabled: true` en `required: false`. Wil je het
  pakket verplicht maken, zet dan `required` op `true`.
- Per speler: `/opties` → Glans (pakket, boeken, deeltjes), of `/handig` → Glans-effecten (per effect). Wisselen laadt het
  scherm van de speler heel even opnieuw.
- Zit je server achter een proxy of een host die maar één poort doorlaat, dan werkt de standaardinstelling van AutoHost
  meestal gewoon. Zie anders de Polymer-documentatie over `auto-host.json`.

De plaatjes worden gemaakt door `tools/glans/generate.py` (alleen nodig als je effecten wilt aanpassen):

```
python3 tools/glans/generate.py <vanilla 26.3 assets/minecraft> glans/src/main/resources
```

## Bouwen

Elke push bouwt alles met GitHub Actions. Daarna start de CI een echte server met alle modules en draait de
zelftests (`HANDIG-SELFTEST OK`). De jars, logs en het gegenereerde resourcepack komen op de branch `ci-out`.

Lokaal: `./gradlew build` (Java 25). De jars staan daarna in `<module>/build/libs/`.

## Opslag

Alles staat in de wereldmap onder `handig/` (instellingen, verzamelaar, graven, gebieden, nicknames…) en `kistdb/`.
Het zijn leesbare JSON-bestanden.
