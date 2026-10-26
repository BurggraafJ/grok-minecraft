# KitPvP: modi en de hub

Op kitpvp kom je binnen in de **hub** (`Hub.java`). Daar kies je een **modus** (poort of menu). Een kitmodus brengt
je naar zijn **kitruimte** (`Kitruimte.java`): klik een pantserstandaard (of de ster / `/kit`) en je springt een
**arena** in. Dit document is voor wie een modus of een Zelda-hoofdstuk toevoegt.

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
| `ArenaModus`, `KasteelModus`, `Zelda` | de drie modi nu (Zelda: zie hieronder) |

Registreren gebeurt in `KitPvP.onEnable`, de volgorde is de menuvolgorde:

```java
modi.registreer(new Zelda(this));        // poort west (de grote, recht tegenover de binnenkomst), open
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
| `stand(Player, Toestand)` | metadata `kitpvp-stand` voor Huisdieren in `SPEL` (standaard `wacht` = wolf vecht niet; Zelda: `bots`) |
| `plek(Player, Toestand)` | plek in de sidebar in `SPEL` (standaard de modusnaam; Zelda: het stuk van het pad) |
| `respawn(Player, Toestand)` | respawnplek na doodgaan in `SPEL` (null = hub) |
| `zelftest(fouten, info)` | eigen controles in `kitpvp zelftest` |

### Kitmodus of eigen spel

- **Kitmodus** (zoals PvP Arena en PvP Castle): `arenas()` + `kitruimtes()`. KitPvP doet de rest: kits, gevecht-tag,
  stats, bots (arena met stand `bots`), respawn in de kitruimte.
- **Eigen spel** (zoals Zelda): roep in `join` eerst `plugin.naarSpel(p, this, "<ruimte>")` aan, en teleporteer
  dan zelf. In `SPEL` laat KitPvP de speler met rust: geen schade-, honger-, drop- of grensregels, geen doodsmelding;
  dat regelt de modus met eigen listeners. `/arena` brengt je altijd terug naar de hub (KitPvP roept dan `leave`).
  De sidebar toont de modusnaam als plek.

## Story of Zelda

Een avontuur in hoofdstukken, alleen of samen. De grote poort in het westen van de hub is **open**: erdoor lopen (of
het menu) = `Zelda.join` → `plugin.naarSpel(p, this, "<hoofdstuk>")` en je staat bij je **verste rustplaats**.
`/arena` of de kaart («Terug naar de hub») brengt je terug; dan ruimt `leave` alles op (items, harten, hemel, muziek).

### Klassen

| klasse | wat |
|---|---|
| `Zelda` | de modus: poort, `delen()` (één deel per hoofdstuk), join/leave/respawn/stand/plek, zelftest, `kitpvp zelda …` |
| `ZeldaHoofdstuk` | basis van een hoofdstuk + de onderdelen: `Rust`, `Kamer`, `Deur`, `Uitdaging`, `Reeks`, `Vondst`, `Verhaal`, `Persoon`, `Box` |
| `ZeldaHoofdstuk1` | «Het Lindewoud»: de wereld (terrein, bos, gebouwen) en zijn onderdelen, in de volgorde van het pad |
| `ZeldaSpel` | de motor: sessies, zones, kamers en golven, baas, uitdagingen, puzzels, vondsten, verteller, uitrusting, kaart, listeners |
| `ZeldaBaas` | baasgedrag (Grauwmoer: springt tussen paddenstoelen, valt eraf door een pijl, roept hulp, levensbalk) |
| `ZeldaMuziek` | nootblokmuziek in een klein notenschrift; lussen (gevecht, baas) en losse deuntjes; categorie «Nootblokken» |
| `ZeldaVoortgang` | opslag per speler in `plugins/KitPvP/zelda.yml` (verste rustplaats per hoofdstuk, vlaggen, beste tijden) |
| `ZeldaTest` | `kitpvp zelda test|verder <speler>`: loopt met een echte speler door het hoofdstuk |

### Hoe het speelt

- **Rustplaats** (zielvuur): loop erlangs = voortgang bewaard («Rustplaats · Oude Eik», deuntje), vol leven, en hier
  kom je terug na doodgaan. Staan bij een rustplaats geneest. Index 0 = het begin van het hoofdstuk.
- **Kamer**: wie de `start`-zone in loopt, sluit beide deuren (schrik-deuntje + gevechtsmuziek voor wie binnen is).
  Golven vijanden (1 extra per extra speler); alles verslagen = deuren open, deuntje, +3 harten. Iedereen binnen dood
  of weg = na 4 s terug naar het begin. Gewonnen kamers gaan na een minuut zonder spelers in de buurt weer dicht.
- **Uitdaging op tijd**: stap op de startplaat, de poort gaat open, de tijd loopt in de **XP-balk** (getal = seconden,
  tikjes, de laatste vijf hoger). Haal je de finish: tijd en (bij verbetering) «record», bewaard per speler.
- **Puzzel (reeks)**: zet alle dingen aan binnen de tijd vanaf het eerste (vuurschalen: rechtsklik; doelwitten: pijl).
  Opgelost = deur open (of brug erbij) tot het gebied een minuut leeg is.
- **Vondst**: loop over de sokkel = je hebt het (vlag); de uitrusting volgt altijd uit de vlaggen (zwaard, schild,
  boog met oneindig pijlen, harten). Rustplaatsen geven de vlaggen van ervoor ook (`geeft`), zodat niemand vastloopt.
- **Verhaal**: de eerste keer in een zone een paar korte regels (`v:<id>` in de voortgang, dus nooit dubbel).
- **Tekst**: alles op **één plek**, de actiebalk, één grijze regel tegelijk (spreker in groen), elke regel 2,5–6 s.
  Geen titels, geen chat. Muziek kan zachter met de schuif «Nootblokken».
- **Dood**: items blijven, geen doodsmelding; respawn bij je rustplaats. **Honger** vast op 17 (geen vanzelf genezen;
  wel bij rustplaatsen, gewonnen kamers en soms een hartje van een vijand). **Samen**: geen schade aan elkaar; de
  wolf (Huisdieren) vecht mee tegen monsters (`stand` = `bots`).
- **Kaart** (vak 9): reizen naar elke bereikte rustplaats (zo help je een vriend die verder terug is) en terug naar de hub.
- **Hemel**: per speler; de dag schuift op van ochtend tot zonsondergang naarmate je verder komt (de baaskamer is nacht).
- **Grenzen**: wie buiten het speelgebied komt, gaat terug naar zijn rustplaats.
- **Entiteiten**: vijanden alleen tijdens een gevecht (tag `zelda`, niet opgeslagen, geen drops, branden niet);
  zwevende vondsten en personen alleen als er iemand in de buurt is. Geen vaste chunk-tickets in het Zelda-gebied.

### Hoofdstuk 1: «Het Lindewoud» (`h1`, deel `zelda`, x 1092 … 1592, z −64 … 64)

Morgal, de Nachtvorst, heeft prinses Zelda meegenomen naar zijn kasteel Nachtrots, over de zee. Wiske, een
vuurvliegje, gaat met je mee. Het pad loopt naar het oosten, de dag schuift op:

| rx (x = 1100 + rx) | wat | soort |
|---|---|---|
| 0 … 30 | huisje aan de bosrand, moestuin, put; begin | rust 0 «Thuis», verhaal |
| 30 … 90 | bospad, **de open plek** (palissade, 2 golven veenlopers en spinnen) | kamer |
| 91 | Eikenschild | vondst |
| 100 | de Oude Eik | rust 1 |
| 114 … 152 | **tempelruïne**: drie vuurschalen (grond, torenvoet via puin, zuil in een vijver), 25 s | puzzel |
| 147 | Wachterszwaard in het heiligdom | vondst |
| 157 | | rust 2 «Tempelruïne» |
| 164 … 205 | **de Kloof**: startplaat, pilaren en een stam boven het water, poort aan de overkant, 25 s | uitdaging |
| 208 | | rust 3 «Kloofrand» |
| 215 … 251 | **het Veen** (wortelpoorten, 3 golven: mosschutters, spinnen, veenreus) | kamer |
| 253 | Jagersboog | vondst |
| 258 | moerashut | rust 4 «Moerashut» |
| 262 … 276 | **drie doelen** over een kanaal, 15 s, dan komt de brug | puzzel |
| 282 … 310 | **de Heksenkring**: baas Grauwmoer (nacht, baasmuziek, levensbalk) | kamer + baas |
| 313 | Hartcontainer (+1 hart) | vondst |
| 319 | | rust 5 «Haventrap» |
| 324 … 346 | **de kade**: Morgals soldaten (vindicators, kruisboogschutters), 2 golven | kamer |
| 347 … 381 | pier, visser Brecht, het schip de Zilvermeeuw (je kunt aan boord) | verhaal: einde (`h1`) |
| 420 … 472 | Nachtrots op een eiland aan de horizon | versiering |

### Een hoofdstuk toevoegen

1. Maak `ZeldaHoofdstuk2 extends ZeldaHoofdstuk` met een eigen `id()` (`h2`), `naam()`, `deelId()` (`zelda2`) en
   `versie()`. Kies een eigen stuk van `Zelda.GEBIED` (bv. het schip en de zee: x 1600 … 2040).
2. In `bouw(Bouwer b)`: `leeg(b.w)`, zet `speelgebied`, bouw de wereld (alleen als `b.plaats`; chunk-tickets aan het
   begin erbij en aan het eind weer weg, zie hoofdstuk 1) en maak de onderdelen aan met `rust(…)`, `kamer(…)`,
   `uitdaging(…)`, `reeks(…)`, `vondst(…)`, `verhaal(…)`, `persoon(…)` — in de volgorde van het pad (de test loopt ze
   in die volgorde af). Zet de begintoestand van deuren en puzzels altijd (zie het eind van `ZeldaHoofdstuk1.bouw`).
3. Zet hem in `Zelda` in `HOOFDSTUKKEN` (`List.of(new ZeldaHoofdstuk1(plugin), new ZeldaHoofdstuk2(plugin))`).
4. Doorgang: het `einde`-verhaal van hoofdstuk 1 zet de vlag `h1`. Laat dan (bv. aan boord van het schip, een zone of
   persoon) `stand.hoofdstuk = "h2"` zetten en naar `h2.rust.get(0).plek` teleporteren; `join` neemt daarna vanzelf
   `stand.hoofdstuk`. (Die overstap zit er nog niet in: er is nog geen hoofdstuk 2.)
5. Nieuwe vijanden: een waarde in `ZeldaHoofdstuk.Soort` en een geval in `ZeldaSpel.spawn`. Nieuwe muziek: een `Lied`
   in `ZeldaMuziek` (de unit-test controleert het bereik). Een nieuwe baas: een `ZeldaBaas` (of subklasse) op de kamer.
6. Een gewijzigd hoofdstuk: hoog `versie()` op, dan herbouwt alleen dat deel bij de volgende start.

### Beheer (console of OP)

- `kitpvp zelda` — status (spelers, voortgang, vijanden, fase van elke kamer). `kitpvp zelda in <speler>` — naar binnen (zoals door de poort).
- `kitpvp zelda reset <speler>` — voortgang wissen. `kitpvp zelda rust <speler> <n>` — op rustplaats n zetten (helpen).
- `kitpvp zelda test <speler> [alles]` — loopt met een echte speler door de poort en het hoofdstuk (kamers verslaan,
  puzzels, uitdaging, vondsten, rustplaatsen, opslag op schijf); zonder `alles` tot de eerste rustplaats na de eerste
  kamer. **Wist eerst de voortgang van die speler** (alleen voor tests). `kitpvp zelda verder <speler>` — door de poort,
  en sta je op je verste rustplaats? (na uit- en inloggen).
- `kitpvp bouw zelda` — hoofdstuk 1 opnieuw bouwen. `kitpvp kaart` maakt ook `zelda-*.png`.

## Ruimte in de wereld

| gebied | x | z | wat |
|---|---|---|---|
| hub | −195 … −125 | −35 … 35 | midden −160/0, binnenkomst −140,5/0,5 (kijkt west) |
| colosseum + kitruimte | −50 … 50 | −60 … 50 | midden 0/0, kitruimte boven de noordmuur (z −57 … −35) |
| oefenarena + kitruimte | −24 … 24 | 51 … 134 | midden 0/110, kitruimte z 63 … 87 |
| kasteel + kitruimte | −42 … 42 | −188 … −94 | midden 0/−150, gracht rondom, kitruimte z −119 … −95 |
| **Zelda** | **1024 … 2047** | **−512 … 511** | gereserveerd (`Zelda.GEBIED`); hoofdstuk 1 gebruikt x 1092 … 1592, z −64 … 64 |

Alle huidige gebieden liggen in de chunks die de plugin geladen houdt (`onEnable`). Het Zelda-gebied niet: alleen
tijdens het bouwen staan er tijdelijk tickets, verder laden spelers zelf wat ze zien (de server heeft 1–2 GB).

## Bouwen en beheer

- `plugins/KitPvP/gebouwd.yml`: `grond` (vloerhoogte) en per deel de gebouwde versie (`hub`, `arena`, `kasteel`,
  `zelda`). Een oude `gebouwd.txt` (tot versie 3) wordt gelezen voor de grondhoogte en daarna vervangen.
  `plugins/KitPvP/zelda.yml`: voortgang in Story of Zelda.
- Console: `kitpvp zelftest`, `kitpvp bouw [hub|arena|kasteel|zelda|alles]`, `kitpvp status`, `kitpvp aftellen <s>`,
  `kitpvp zelda …` (zie hierboven), `kitpvp kaart` (PNG's van hub, arena's, kasteel en Zelda in
  `plugins/KitPvP/kaart/`: boven- en vooraanzichten van wat er echt staat) en `kitpvp test <speler> [dood|arena]`
  (loopt met een online speler de hele route: hub, door de Zelda-poort naar binnen en met `/arena` terug (kompas
  terug, gewone harten), poort PvP Arena, zijdeur, kit, `/arena`, PvP Castle via het menu, gevecht-tag houdt `/arena`
  en `/modus` tegen; `dood` = daarna sterven in het kasteel en controleren dat je in de kitruimte respawnt (telt een
  dood in stats.yml, alleen voor tests); `arena` = daarna in het kasteel blijven staan).
  `arena bouw` / `arena zelftest` / `arena aftellen` werken nog (mc gebruikt `arena aftellen 5`).
- De zelftest controleert register (drie modi met precies deze namen, Zelda open), hub (vloer, fontein, muur,
  binnenkomst), elke poort (omlijsting, open doorgang die naar de modus stuurt, of tralies + bordje + melding),
  elke kitruimte (7 standaards, deuren, binnenkomst), alle spawns (vrij, met vloer), ranglijsten, of het bouwen klaar
  is, en Zelda (deel gebouwd in de goede versie, rustplaatsen vrij met vloer, kamers: spawns, deuren in de
  begintoestand, puzzels en uitdaging in rust, alles binnen het gebied, een einde, muziek, voortgang schrijven en
  teruglezen, niet te veel entiteiten).
