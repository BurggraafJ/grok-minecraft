# KitPvP: modi en de hub

Op kitpvp kom je binnen in de **hub** (`Hub.java`). Daar kies je een **modus** (poort of menu). Een kitmodus brengt
je naar zijn **kitruimte** (`Kitruimte.java`): klik een pantserstandaard (of de ster / `/kit`) en je springt een
**arena** in. Dit document is voor wie een modus toevoegt. (Story of Zelda is sinds 2.17 een eigen plugin: zie onder.)

## Hoe het in elkaar zit

| klasse | wat |
|---|---|
| `Modus` | interface van een modus (id, naam, kleur, icoon, status, poortstijl, delen, arena's, kitruimtes, hooks) |
| `KitModus` | basis voor modi met kits: `join` = naar de eerste kitruimte |
| `ModusRegister` | alle modi in menuvolgorde; controleert ids, poortplekken, arena's en kitruimtes |
| `Toestand` / `Spelers` | per speler precies één van: `HUB`, `KITRUIMTE` (modus, ruimte), `ARENA` (modus, arena, kit), `SPEL` (modus, ruimte) |
| `Hub` | bouwt de hub en de poorten; `raak()` = welke poort raakt een speler |
| `Kitruimte` | generieke galerij met 7 standaards, deur naar de hub en optioneel een zijdeur |
| `Arena` | id, naam, stand (`pvp`/`bots`), spawns |
| `Deel` + `Bouwer` | wereld uit code: elk deel heeft een versie (in `plugins/KitPvP/gebouwd.yml`), bouwen gaat verdeeld over ticks |
| `ArenaModus`, `KasteelModus` | de twee modi nu (Story of Zelda is een eigen plugin, zie onder) |

Registreren gebeurt in `KitPvP.onEnable`, de volgorde is de menuvolgorde:

```java
// west (de grote poort, recht tegenover de binnenkomst) is vrij: daar staat de verwijzing naar de Zelda-poort in de lobby
modi.registreer(new ArenaModus(this));   // poort zuid (links)
modi.registreer(new KasteelModus(this)); // poort noord (rechts)
```

Het register vult vanzelf: de poorten in de hub (op `PoortStijl.plek`), het modusmenu (kompas, `/modus`), `/modus <naam>`,
de respawn na een ronde (`kitruimteVoor(arena)`), de sidebar en `kitpvp zelftest`.

## Een modus toevoegen

1. Maak een klasse die `Modus` implementeert (of `KitModus` voor een kit-modus). Kies een vrij `id` (kleine letters).
2. `poort()` geeft een `PoortStijl` met een vrije plek (`west`, `zuid`, `noord`), materialen, banieren en de kleur van de deeltjes.
3. Eigen wereld: `delen()` geeft een `Deel("<id>", versie, b -> {...})`. Gebruik `b.zet/b.vul` (alleen als het deel
   herbouwd wordt) en zet labels/standaards altijd (ze worden niet opgeslagen). Hoog de versie op = bij de volgende
   start herbouwt alleen dat deel. Maak eerst je eigen gebied leeg; raak andermans gebied niet.
4. Eén regel in `KitPvP.onEnable`: `modi.registreer(new JouwModus(this));`
5. Zet de chunks die altijd geladen moeten zijn (labels, standaards) in `onEnable` erbij als je buiten de huidige
   gebieden bouwt, of laad ze zelf in `aan()`.

### Hooks (`Modus`)

| hook | wanneer |
|---|---|
| `aan(KitPvP)` / `uit()` | plugin aan/uit: eigen listeners (`getServer().getPluginManager().registerEvents(...)`) en taken |
| `tik()` | elke seconde |
| `join(Player)` | speler kiest de modus (poort of menu), alleen als `status()` OPEN is |
| `leave(Player)` | speler verlaat de modus (hub, andere modus, uitloggen) |
| `stand(Player, Toestand)` | metadata `kitpvp-stand` voor Huisdieren in `SPEL` (standaard `wacht` = wolf vecht niet; `geen` = geen huisdier mee) |
| `plek(Player, Toestand)` | plek in de sidebar in `SPEL` (standaard de modusnaam) |
| `respawn(Player, Toestand)` | respawnplek na doodgaan in `SPEL` (null = hub) |
| `zelftest(fouten, info)` | eigen controles in `kitpvp zelftest` |

### Kitmodus of eigen spel

- **Kitmodus** (zoals PvP Arena en PvP Castle): `arenas()` + `kitruimtes()`. KitPvP doet de rest: kits, gevecht-tag,
  stats, bots (arena met stand `bots`), respawn in de kitruimte.
- **Eigen spel in de hub** (zo zat Zelda erin tot 2.16; iets dat echt anders moet voelen krijgt beter een eigen plugin
  en wereld, zoals `plugins/zelda`): roep in `join` eerst `plugin.naarSpel(p, this, "<ruimte>")` aan, en teleporteer
  dan zelf. In `SPEL` laat KitPvP de speler met rust: geen schade-, honger-, drop- of grensregels, geen doodsmelding;
  dat regelt de modus met eigen listeners. `/arena` brengt je altijd terug naar de hub (KitPvP roept dan `leave`).
  De sidebar toont de modusnaam als plek.

## Story of Zelda (tot 2.16 een modus hier)

Story of Zelda is sinds kitpvp 2.17 een **eigen plugin** (`plugins/zelda`, Zelda 1.0.0) met een **eigen wereld** (`zelda`) op
deze server en een **eigen poort in de lobby**. Ontwerp: `plugins/zelda/ZELDA.md` (sectie «Een eigen spel»); techniek
(klassen, eilanden, beheer): `plugins/zelda/TECHNIEK.md`.

Wat KitPvP er nog mee te maken heeft:
- KitPvP bemoeit zich alleen met spelers in **zijn eigen wereld** (`KitPvP.hier`): schade, honger, drops, sidebar, de
  stand voor Huisdieren, `/kit` `/modus` `/arena`. Wie de KitPvP-wereld in komt (bv. `/kitpvp` vanuit Zelda), staat in de
  hub; wie hem verlaat, vergeet KitPvP (zijn spullen laat het aan Zelda).
- `/kit`, `/modus` of `/arena` vanuit Zelda = naar de KitPvP-hub.
- In de hub is de westplek leeg: een dichtgemetselde, bemoste boog met een bordje «Story of Zelda · heeft nu een · eigen
  poort · in de lobby» (`Hub.verwijzing`, hub versie 2). De zelftest controleert het bordje.
- `kitpvp zelda …` op de console stuurt door naar `zelda …`.
- `Toestand.SPEL` en `KitPvP.naarSpel` bestaan nog voor een modus met een eigen spel in de hub; nu gebruikt niemand ze.

## Ruimte in de wereld

| gebied | x | z | wat |
|---|---|---|---|
| hub | −195 … −125 | −35 … 35 | midden −160/0, binnenkomst −140,5/0,5 (kijkt west) |
| colosseum + kitruimte | −50 … 50 | −60 … 50 | midden 0/0, kitruimte boven de noordmuur (z −57 … −35) |
| oefenarena + kitruimte | −24 … 24 | 51 … 134 | midden 0/110, kitruimte z 63 … 87 |
| kasteel + kitruimte | −42 … 42 | −188 … −94 | midden 0/−150, gracht rondom, kitruimte z −119 … −95 |
| (oud Zelda-gebied) | 1024 … 2047 | −512 … 511 | tot 2.16 lagen hier de eilanden; ze blijven liggen (niemand komt er, handig om terug te vallen). Zelda staat nu in de wereld `zelda` op dezelfde coördinaten (plugin Zelda). Toen: het Lindewoud bouwt x 1024 … 1639, z −179 … 179 (land x 1045 … 1492, z −105 … 105, de rest zee); het Zilverstrand x 1060 … 1360, z −480 … −180 (2a tot 1229, 2b vanaf 1230); de Witte Toppen x 1400 … 1720, z −480 … −180 (3a tot 1579, 3b vanaf 1580); de Gloedberg x 1060 … 1400, z 180 … 480 (4a tot 1259, 4b vanaf 1260: de kegel en de krater); Nachtrots x 1640 … 2000, z −150 … 150 (5a tot 1879, 5b vanaf 1880: de toren); de zee (overtocht) x 1840 … 2040, z −510 … −330; de vakken van de andere eilanden: tabel in `ZELDA.md` |

Alle huidige gebieden liggen in de chunks die de plugin geladen houdt (`onEnable`).

## Bouwen en beheer

- `plugins/KitPvP/gebouwd.yml`: `grond` (vloerhoogte) en per deel de gebouwde versie (`hub`, `arena`, `kasteel`; de
  `zelda…`-regels van vroeger blijven staan, de plugin Zelda heeft een eigen `gebouwd.yml`). Een oude `gebouwd.txt` (tot
  versie 3) wordt gelezen voor de grondhoogte en daarna vervangen. `plugins/KitPvP/zelda.yml` en de andere `zelda*` zijn van
  kitpvp 2.16: de plugin Zelda heeft ze bij zijn eerste start gekopieerd en ze blijven als terugval staan.
- Console: `kitpvp zelftest`, `kitpvp bouw [hub|arena|kasteel|alles]`, `kitpvp status`, `kitpvp aftellen <s>`,
  `kitpvp kaart` (PNG's van hub, arena's en kasteel in `plugins/KitPvP/kaart/`: boven- en vooraanzichten van wat er echt
  staat) en `kitpvp test <speler> [dood|arena]` (loopt met een online speler de hele route: hub (geen Zelda-poort,
  KitPvP-sidebar), poort PvP Arena, zijdeur, kit, `/arena`, PvP Castle via het menu, gevecht-tag houdt `/arena` en
  `/modus` tegen; `dood` = daarna sterven in het kasteel en controleren dat je in de kitruimte respawnt (telt een dood in
  stats.yml, alleen voor tests); `arena` = daarna in het kasteel blijven staan).
  `arena bouw` / `arena zelftest` / `arena aftellen` werken nog (mc gebruikt `arena aftellen 5`).
- De zelftest controleert register (twee modi met precies deze namen, west leeg), hub (vloer, fontein, muur,
  binnenkomst, het bordje naar de Zelda-poort in de lobby), elke poort (omlijsting, open doorgang die naar de modus stuurt,
  of tralies + bordje + melding), elke kitruimte (7 standaards, deuren, binnenkomst), alle spawns (vrij, met vloer),
  ranglijsten en of het bouwen klaar is. Zelda heeft een eigen zelftest: `zelda zelftest` (zie `plugins/zelda/TECHNIEK.md`).
