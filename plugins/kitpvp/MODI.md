# KitPvP: modi en de hub

Op kitpvp kom je binnen in de **hub** (`Hub.java`). Daar kies je een **modus** (poort of menu). Een kitmodus brengt
je naar zijn **kitruimte** (`Kitruimte.java`): klik een pantserstandaard (of de ster / `/kit`) en je springt een
**arena** in. Dit document is voor wie een modus toevoegt — als eerste Story of Zelda (sessie 2).

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
| `ArenaModus`, `KasteelModus`, `Zelda` | de drie modi nu |

Registreren gebeurt in `KitPvP.onEnable`, de volgorde is de menuvolgorde:

```java
modi.registreer(new Zelda(this));        // poort west (de grote, recht tegenover de binnenkomst)
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
| `stand(Player, Toestand)` | metadata `kitpvp-stand` voor Huisdieren in `SPEL` (standaard `wacht` = wolf vecht niet) |
| `respawn(Player, Toestand)` | respawnplek na doodgaan in `SPEL` (null = hub) |
| `zelftest(fouten, info)` | eigen controles in `kitpvp zelftest` |

### Kitmodus of eigen spel

- **Kitmodus** (zoals PvP Arena en PvP Castle): `arenas()` + `kitruimtes()`. KitPvP doet de rest: kits, gevecht-tag,
  stats, bots (arena met stand `bots`), respawn in de kitruimte.
- **Eigen spel** (zoals Zelda straks): roep in `join` eerst `plugin.naarSpel(p, this, "<ruimte>")` aan, en teleporteer
  dan zelf. In `SPEL` laat KitPvP de speler met rust: geen schade-, honger-, drop- of grensregels, geen doodsmelding;
  dat regelt de modus met eigen listeners. `/arena` brengt je altijd terug naar de hub (KitPvP roept dan `leave`).
  De sidebar toont de modusnaam als plek.

## Story of Zelda: de poort

- Klasse `Zelda.java`, `status()` = `BINNENKORT`. De poort is de **grote poort in het westen** van de hub, recht
  tegenover de binnenkomst: mossige steen met gebeitelde accenten, groene banieren en groen licht, in een lichtschacht
  (geen dak ervoor). Doorgang 7 breed en 10 hoog, nu dicht met tralies en een bordje «Binnenkort».
- Wie ertegen loopt (en ernaartoe kijkt) krijgt één grijze actiebalkregel «Story of Zelda · binnenkort» (hooguit eens
  per 4 s). In het menu staat hij grijs met «binnenkort»; klikken doet niets.
- **Openzetten (sessie 2):** zet `status()` op `OPEN`, implementeer `join` en (meestal) `delen()`. Bij de volgende start
  zet de hub de doorgang open (`Hub.poortStaat`, gebeurt elke start, dus de hub hoeft **niet** herbouwd te worden):
  tralies en bordje weg, groene deeltjes erin. Door de poort lopen roept dan `Zelda.join` aan.

## Ruimte in de wereld

| gebied | x | z | wat |
|---|---|---|---|
| hub | −195 … −125 | −35 … 35 | midden −160/0, binnenkomst −140,5/0,5 (kijkt west) |
| colosseum + kitruimte | −50 … 50 | −60 … 50 | midden 0/0, kitruimte boven de noordmuur (z −57 … −35) |
| oefenarena + kitruimte | −24 … 24 | 51 … 134 | midden 0/110, kitruimte z 63 … 87 |
| kasteel + kitruimte | −42 … 42 | −188 … −94 | midden 0/−150, gracht rondom, kitruimte z −119 … −95 |
| **gereserveerd voor Zelda** | **1024 … 2047** | **−512 … 511** | 1024 × 1024 blokken, vier regiobestanden, ver van de rest (`Zelda.GEBIED`) |

Alle huidige gebieden liggen in de chunks die de plugin geladen houdt (`onEnable`). Het Zelda-gebied niet: laad daar
alleen wat nodig is (of wat spelers al laden), want de server heeft 2 GB.

## Bouwen en beheer

- `plugins/KitPvP/gebouwd.yml`: `grond` (vloerhoogte) en per deel de gebouwde versie (`hub`, `arena`, `kasteel`).
  Een oude `gebouwd.txt` (tot versie 3) wordt gelezen voor de grondhoogte en daarna vervangen.
- Console: `kitpvp zelftest`, `kitpvp bouw [hub|arena|kasteel|alles]`, `kitpvp status`, `kitpvp aftellen <s>`,
  `kitpvp kaart` (PNG's van hub, arena's en kasteel in `plugins/KitPvP/kaart/`: boven- en vooraanzichten van wat er
  echt staat) en `kitpvp test <speler> [dood|arena]` (loopt met een online speler de hele route: hub, dichte
  Zelda-poort, poort PvP Arena, zijdeur, kit, `/arena`, PvP Castle via het menu, gevecht-tag houdt `/arena` en `/modus`
  tegen; `dood` = daarna sterven in het kasteel en controleren dat je in de kitruimte respawnt (telt een dood in
  stats.yml, alleen voor tests); `arena` = daarna in het kasteel blijven staan).
  `arena bouw` / `arena zelftest` / `arena aftellen` werken nog (mc gebruikt `arena aftellen 5`).
- De zelftest controleert register (drie modi met precies deze namen, Zelda binnenkort), hub (vloer, fontein, muur,
  binnenkomst), elke poort (omlijsting, open doorgang die naar de modus stuurt, of tralies + bordje + melding),
  elke kitruimte (7 standaards, deuren, binnenkomst), alle spawns (vrij, met vloer), ranglijsten en of het bouwen klaar is.
