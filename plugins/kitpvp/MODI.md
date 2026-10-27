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

Een avontuur over de eilanden van de Glazen Zee, alleen of samen. **Ontwerp, verhaal, eilandenplan en de bouwsessies:
`ZELDA.md`.** De grote poort in het westen van de hub is **open**: erdoor lopen (of het menu) = `Zelda.join` →
`plugin.naarSpel(p, this, "<deel>")` en je staat bij je **laatste rustplaats**. `/arena` of de kaart («Terug naar de
hub») brengt je terug; dan ruimt `leave` alles op (items, harten, hemel, muziek, Wiske, huisdier).

### Klassen

| klasse | wat |
|---|---|
| `Zelda` | de modus: poort, `EILANDEN`, `delen()` (één bouwdeel per eilanddeel + de zee), join/leave/respawn/stand/plek, zelftest, `kitpvp zelda …` |
| `ZeldaEiland` | een eiland: id, naam, icoon, delen, aanleg-rustplaats (steiger), `openNa`-vlag, `klaar`-vlag, overtochtregels |
| `ZeldaHoofdstuk` | basis van een eilanddeel + de onderdelen: `Rust`, `Bron`, `Kamer`, `Deur`, `Uitdaging`, `Reeks`, `Vondst`, `Verhaal`, `Persoon` (+ `Gesprek`), `Missie`, `Doel`, `Barst` (gebarsten muur), `Haakpunt`, `Box` |
| `ZeldaHoofdstuk1` | «Het Lindewoud» (eiland 1): de wereld en zijn onderdelen, in de volgorde van het pad, met Kees, Hanne, Lotje en Brecht; `motor()` = bronnen, uitleg, tips, doelen; `aanvullen` = oude standen |
| `ZeldaZee` | bouwdeel `zelda-zee`: open zee met de Zilvermeeuw, voor de overtocht tussen eilanden |
| `ZeldaSpel` | de motor: sessies, zones, kamers en golven, baas, uitdagingen, puzzels, vondsten, bronnen, verteller, uitrusting, kaart, hint, gesprekken, missies, overtocht, listeners |
| `ZeldaMetgezel` | Wiske, de fee: een kleine allay die naast je vliegt (alleen voor jou zichtbaar), praat en wijst |
| `ZeldaDier` | je eigen huisdier (vlag `dier:<soort>`): loopt mee, snuffelt verborgen vondsten op |
| `ZeldaVoorwerp` | een voorwerp in de hotbar (nu: het flesje; later bommen, klauwhaak, vuurstaf, lichtpijlen) |
| `ZeldaRegels` | regels zonder wereld (unit-tests): harten, hartstukjes, harnas, flesjes, welk doel, opnieuw vanaf hier |
| `ZeldaBaas` | baasgedrag (Grauwmoer: springt tussen paddenstoelen, valt eraf door een pijl, roept hulp, levensbalk) |
| `ZeldaMuziek` | nootblokmuziek in een klein notenschrift; drie lagen: achtergrond (altijd), lus (gevecht, baas), deuntjes; categorie «Nootblokken» |
| `ZeldaVoortgang` | opslag per speler in `plugins/KitPvP/zelda.yml` (laatste en bezochte rustplaatsen, verste per deel, vlaggen, missies, tellers, huisdiernaam, beste tijden) |
| `ZeldaTest` | `kitpvp zelda test|verder <speler>`: loopt met een echte speler door alle eilanden (varen, elk deel, zijmissies); `test <speler> motor`: alle motoronderdelen |

### Hoe het speelt

- **Rustplaats** (zielvuur): loop erlangs = je **laatste** rustplaats (daar kom je binnen en na doodgaan terug); de
  eerste keer «Rustplaats · Oude Eik» en een deuntje. Staan bij een rustplaats geneest en vult je flesjes. Index 0 =
  het begin van het deel. Oude standen (zonder laatste) beginnen bij hun verste rustplaats.
- **Bron** (feeënkring, alleen licht): erin staan geneest snel en vult je flesjes. Bij elke kamer een rustplaats of
  bron binnen 32 blokken (de zelftest eist het).
- **Kamer**: wie de `start`-zone in loopt, sluit beide deuren (schrik-deuntje + gevechtsmuziek voor wie binnen is).
  Golven vijanden (1 extra per extra speler); alles verslagen = deuren open, deuntje, +3 harten, vlag `k:<kamer>`.
  Iedereen binnen dood of weg = na 4 s terug naar het begin. Gewonnen kamers gaan na een minuut zonder spelers in de
  buurt weer dicht. Doodgaan in een kamer = na de respawn een **tip** van Wiske (`k.tips`, de 1e, dan de 2e).
  **Vrije doorgang**: won iedereen die de kamer instapt hem al (`k:<kamer>`), dan gaan de deuren gewoon open; ook als
  je van de uitgangskant terugkomt (zijmissies). Stapt er iemand binnen die hem nog niet won, dan begint het gevecht
  gewoon (ook voor wie erbij is). Wie nog eens wil, rechtsklikt de **bel** in de kamer (`k.bel`): dan het gevecht met
  de extra golven erachter (`k.extra`).
  De zelftest eist hoogstens twee verplichte kamers (niet `vrijwillig`) vóór de eerste baas per deel.
- **Uitdaging op tijd**: stap op de startplaat, de poort gaat open, de tijd loopt in de **XP-balk** (getal = seconden,
  tikjes, de laatste vijf hoger). Haal je de finish: tijd en (bij verbetering) «record», bewaard per speler. Bij de
  start hangt een bord met de vijf beste tijden (alleen als er iemand in de buurt is).
- **Puzzel (reeks)**: zet alle dingen aan binnen de tijd vanaf het eerste (vuurschalen: rechtsklik; doelwitten: pijl).
  Opgelost = deur open (of brug erbij) tot het gebied een minuut leeg is. Wie hem ooit oploste (`r:<id>`), vindt hem
  open (handig als je na een dood bij de baas terugkomt).
- **Vondst**: loop over de sokkel = je hebt het (vlag); de uitrusting volgt altijd uit de vlaggen: zwaard, schild,
  boog met oneindig pijlen, voorwerpen (vak 3 … 7), **harten** (8 + hartcontainers `hart…` + een hart per vier
  **hartstukjes** `hs:…`), **harnas** (`harnas1` … `harnas4`, zichtbaar), **flesjes** (`fles1` … `fles4`, vak 3,
  rechtsklik = vijf harten). Rustplaatsen geven de vlaggen van ervoor ook (`geeft`). Een vondst met `verborgen` wordt
  door je huisdier opgesnuffeld; met `vereist` alleen te zien als je die vlag hebt; met `voorwerp` wel te zien, maar
  pas te pakken met dat voorwerp (vlag `bommen`, `klauwhaak`, …). `daarna` = een actie na het vinden (missiestap).
  Hartstukjes heten `hs:<eiland>-<n>`; de kaart toont hoeveel je er op dit eiland hebt.
- **Gebarsten muur** (`Barst`, blok `BARST`): dicht tot een bom hem openbreekt (`spel.breek`, de bommen komen op
  eiland 2); wie erbij was (`b:<id>`) vindt hem daarna open; een minuut niemand in de buurt = weer dicht.
  **Haakpunt** (`Haakpunt`, blok `HAAKPUNT`): blok + landingsplek voor de klauwhaak (eiland 3).
- **Verhaal**: de eerste keer in een zone een paar korte regels (`v:<id>`, dus nooit dubbel). Met `uitleg` = uitleg
  (hoe iets werkt), overgeslagen als je op de kaart «Uitleg: uit» kiest.
- **Personen**: rechtsklik = praten. Het eerste `Gesprek` waarvan de voorwaarde klopt, anders het gewone praatje;
  daarna een `Actie` (zijmissie beginnen of verder: `spel.missie`, beloning: `spel.beloon`). Heeft iemand iets nieuws
  (een gesprek met id dat je nog niet hoorde), dan glinstert hij zacht, alleen voor jou. `eenmalig` = een gesprek dat
  je één keer hoort. **Brecht** vaart je: praten = een menu «Waarheen?» met de eilanden die open zijn (`spel.varen`),
  of «nog even geduld» als er nog geen ander eiland is.
- **Wiske**: een kleine fee naast je schouder; ze komt voor je zweven als ze praat. **Klik op haar of `/hint`**: eerst
  een duwtje (het eerste doel van het eiland dat nog niet klaar is), binnen twee minuten nog eens = wat je moet doen en
  ze vliegt even die kant op. Geen doel meer = je zijmissies, «verder varen», of wie er iets nieuws te vertellen heeft
  («Kees wil je iets vertellen», nog eens = ze wijst). Zes minuten zonder voortgang: ze biedt één keer hulp aan.
- **Huisdier**: vlag `dier:<soort>`; de eerste keer kies je een naam (klein menu). Loopt mee, zit als je stilstaat,
  snuffelt verborgen vondsten op (loopt erheen en gaat erbij zitten). Onkwetsbaar, vecht niet.
- **Tekst**: alles op **één plek**, de actiebalk, één grijze regel tegelijk (spreker in groen), elke regel 2,5–6 s.
  Geen titels, geen chat. 
- **Muziek, altijd**: een achtergrondlied per streek (`muziek(Location)`: Thuis, Woud, Tempel, Veen, Haven, Zee),
  daarboven de gevechts- of baaslus en losse deuntjes. Zachter met de schuif «Nootblokken».
- **Dood**: items blijven, geen doodsmelding; respawn bij je rustplaats. **Honger** vast op 17 (geen vanzelf genezen;
  wel bij rustplaatsen, bronnen, flesjes, gewonnen kamers en soms een hartje van een vijand). **Samen**: geen schade
  aan elkaar; de wolf (Huisdieren) vecht mee tegen monsters (`stand` = `bots`).
- **Kaart** (vak 9): de zielvuren van dit eiland (klik = reizen, **rechtsklik = opnieuw vanaf hier**: verhaal, uitleg
  en puzzels erna komen terug, spullen blijven), de eilanden die open zijn (klik = **varen**), opdrachten, harten en
  hartstukjes, harnas, flesjes, **Uitleg: aan/uit**, terug naar de hub.
- **Overtocht**: varen = een paar tellen aan dek van de Zilvermeeuw op open zee (zeemansmuziek, schuim, Brecht aan
  het roer, een regel van Wiske of Brecht; de eerste keer de overtochtregels van het eiland), dan aan land bij de
  aanleg van het eiland (vlag `e:<eiland>`).
- **Hemel**: per speler; de dag schuift op van ochtend tot zonsondergang naarmate je verder komt (de baaskamer is nacht).
- **Grenzen**: wie buiten de delen van zijn eiland komt, gaat terug naar zijn rustplaats.
- **Entiteiten**: vijanden alleen tijdens een gevecht (tag `zelda`, niet opgeslagen, geen drops, branden niet);
  zwevende vondsten en personen alleen als er iemand in de buurt is; één Wiske (en eventueel één huisdier) per
  speler in Zelda. Geen vaste chunk-tickets in het Zelda-gebied.

### Eiland 1: «Het Lindewoud» (`h1`, deel `zelda`, x 1092 … 1592, z −64 … 64)

Morgal, de Nachtvorst, heeft prinses Zelda meegenomen naar zijn kasteel Nachtrots, over de zee. Wiske, een
fee, gaat met je mee. Het pad loopt naar het oosten, de dag schuift op:

| rx (x = 1100 + rx) | wat | soort |
|---|---|---|
| 0 … 30 | huisje aan de bosrand, moestuin, put; begin; **Kees** bij zijn houtstapel (zijmissie *De verloren bijl*) | rust 0 «Thuis», verhaal, persoon |
| 30 … 90 | bospad, **de open plek** (palissade, 1 golf van 2 veenlopers; bel = nog eens met spinnen) | kamer |
| 47 | feeënkring (bron, alleen licht) | bron |
| 91 | Eikenschild | vondst |
| 100 | de Oude Eik (stam 4 × 4, **hol**: aan de noordkant een gat, hartstukje `hs:lindewoud-1`, verborgen) | rust 1, vondst |
| 87 … 98, z −25 … −15 | **de houtkap** (pad vanaf de Oude Eik naar het noorden): stronken, stammen, hakblok met de bijl van Kees | vondst (na Kees) |
| 114 … 152 | **tempelruïne**: drie vuurschalen (grond, torenvoet via puin, zuil in een vijver), 25 s | puzzel |
| 147 | Wachterszwaard in het heiligdom; in de zuidmuur een **gebarsten muur** met een kamertje (`hs:lindewoud-4`, pas met bommen) | vondst, barst |
| 157 | | rust 2 «Tempelruïne» |
| 164 … 205 | **de Kloof**: startplaat, pilaren en een stam boven het water, poort aan de overkant, 25 s; in de oostwand (z −8 … −6) een **richel** met `hs:lindewoud-3` en een haakpunt erboven (klauwhaak), verderop een waterval | uitdaging, haakpunt |
| 208 | | rust 3 «Kloofrand» |
| 215 … 251 | **het Veen** (wortelpoorten, 2 golven: mosschutters, veenloper en spin; veenreus; bel = nog eens); drie kruiden voor Hanne | kamer, vondsten (na Hanne) |
| 253 | Jagersboog | vondst |
| 258 | moerashut; eronder **Hanne** met haar ketel (zijmissie *Moeraskruid* → `fles1`) | rust 4 «Moerashut», persoon |
| 262 … 276 | **drie doelen** over een kanaal, 15 s, dan komt de brug | puzzel |
| 279 | feeënkring voor de Heksenkring | bron |
| 282 … 310 | **de Heksenkring**: baas Grauwmoer (nacht, baasmuziek, levensbalk) | kamer + baas |
| 313 | Hartcontainer (+1 hart) | vondst |
| 319 | | rust 5 «Haventrap» |
| 324 … 346 | **de kade**: Morgals soldaten (vindicators, een kruisboogschutter), 1 golf; bel = nog eens | kamer |
| 347 … 381 | pier, visser **Brecht** (varen: menu met de open eilanden), **Lotje** telt meeuwen, het schip de Zilvermeeuw (je kunt aan boord) | verhaal: einde (`h1`), personen |
| 420 … 472 | Nachtrots op een eiland aan de horizon | versiering |

### Een eiland toevoegen

Het plan (welk eiland, welk vak, welke voorwerpen, in welke volgorde) staat in `ZELDA.md`. Kort:

1. Per half eiland een klasse `ZeldaEiland2a extends ZeldaHoofdstuk` met eigen `id()` (`e2a`), `naam()`, `deelId()`
   (`zelda-e2a`) en `versie()`, in zijn eigen vak van `Zelda.GEBIED` (tabel in ZELDA.md).
2. In `bouw(Bouwer b)`: `leeg(b.w)`, zet `speelgebied`, bouw de wereld (alleen als `b.plaats`; chunk-tickets aan het
   begin erbij en aan het eind weer weg, zie hoofdstuk 1 en `ZeldaZee`) en maak de onderdelen aan met `rust(…)`,
   `bron(…)`, `kamer(…)`, `uitdaging(…)`, `reeks(…)`, `vondst(…)`, `verhaal(…)`, `persoon(…).gesprek(…)`, `missie(…)`
   en `doel(…)`, in de volgorde van het pad (de test loopt ze in die volgorde af; iets later toegevoegds zet je met
   `na("<id>", …)` op zijn plek). Zet de begintoestand van deuren en puzzels altijd (zie het eind van
   `ZeldaHoofdstuk1.bouw`). Geef `muziek(Location)`, `plekNaam(Location)` en `hemel(Location)` per streek.
3. Eén regel in `Zelda.EILANDEN`: `new ZeldaEiland("zilverstrand", "Het Zilverstrand", Material.SAND, List.of(deel2a,
   deel2b), "steiger", "h1", "baken2", List.of(r("Brecht", "…")))`. Dan staat het op de kaart van wie `h1` heeft en
   werkt varen (`spel.vaar`). Brecht op het vorige eiland kan in een gesprek `spel.vaar(p, spel.eiland("…"))` doen.
4. Nieuwe vijanden: een waarde in `ZeldaHoofdstuk.Soort` en een geval in `ZeldaSpel.spawn`. Nieuwe muziek: een `Lied`
   in `ZeldaMuziek` en in `alle()` (de unit-test controleert bereik, lengte en volume). Een nieuwe baas: een `ZeldaBaas`
   (of subklasse) op de kamer. Een nieuw voorwerp: een `ZeldaVoorwerp` in `spel.voorwerpen`.
5. Een gewijzigd deel: hoog `versie()` op, dan herbouwt alleen dat deel bij de volgende start. Teksten, doelen,
   gesprekken en bronnen zonder blokken: geen versie nodig (ze worden bij elke start opnieuw aangemaakt).
6. `kitpvp zelda test <speler> alles` loopt alle eilanden af in de volgorde van `EILANDEN` (varen, elk deel, het einde
   van elk deel, Brecht, de zijmissies door te praten met wie iets nieuws heeft); een nieuw eiland doet dus vanzelf
   mee als het opengaat met de vlag van het eiland ervoor. Vondsten met `voorwerp` moeten blijven liggen.

### Beheer (console of OP)

- `kitpvp zelda` — status (spelers, voortgang, vijanden, fase van elke kamer). `kitpvp zelda in <speler>` — naar binnen (zoals door de poort).
- `kitpvp zelda reset <speler>` — voortgang wissen. `kitpvp zelda rust <speler> <n>` — op rustplaats n zetten (helpen).
- `kitpvp zelda test <speler> [alles]` — loopt met een echte speler door de poort en het hoofdstuk (kamers verslaan,
  puzzels, uitdaging, vondsten, rustplaatsen, opslag op schijf); zonder `alles` tot de eerste rustplaats na de eerste
  kamer; met `alles` ook vrije doorgang en de bel, vondsten voor later (blijven liggen), Brecht, de zijmissies en de
  volgende eilanden; `dood` = sterven in de eerste kamer (respawn bij je rustplaats, spullen houden, kamer terug). **Wist eerst de
  voortgang van die speler** (alleen voor tests; zijn testtijd komt op het tijdenbord, `reset` haalt hem weg). `kitpvp zelda verder <speler>` — door de poort,
  en sta je op je verste rustplaats? (na uit- en inloggen).
- `kitpvp zelda test <speler> motor` — de motor met een echte speler: Wiske, muziek, `/hint` (twee keer), bron,
  flesje, hartstukjes, harnas, huisdier (verschijnt, loopt mee), gesprek en zijmissie met beloning, opnieuw vanaf een
  rustplaats, uitleg uit, varen en aankomen, tip na doodgaan in een kamer, de kaart, en weg = alles weg. Wist eerst en
  na afloop de voortgang van die speler (alleen voor tests).
- `kitpvp zelda vaar <speler> <eiland>` — de overtocht naar een eiland. `kitpvp zelda vlag <speler> <vlag>` — een
  vlag aan (nog eens = uit), bv. `fles1`, `hs:test`, `harnas2`, `dier:vos`, `o:uitleg-uit` (helpen en testen).
  `kitpvp zelda hint <speler>` — de hint die hij nu zou krijgen.
- `kitpvp bouw zelda` — hoofdstuk 1 opnieuw bouwen, `kitpvp bouw zelda-zee` de zee. `kitpvp kaart` maakt ook `zelda-*.png`.

## Ruimte in de wereld

| gebied | x | z | wat |
|---|---|---|---|
| hub | −195 … −125 | −35 … 35 | midden −160/0, binnenkomst −140,5/0,5 (kijkt west) |
| colosseum + kitruimte | −50 … 50 | −60 … 50 | midden 0/0, kitruimte boven de noordmuur (z −57 … −35) |
| oefenarena + kitruimte | −24 … 24 | 51 … 134 | midden 0/110, kitruimte z 63 … 87 |
| kasteel + kitruimte | −42 … 42 | −188 … −94 | midden 0/−150, gracht rondom, kitruimte z −119 … −95 |
| **Zelda** | **1024 … 2047** | **−512 … 511** | gereserveerd (`Zelda.GEBIED`); hoofdstuk 1 gebruikt x 1092 … 1592, z −64 … 64; de zee (overtocht) x 1840 … 2040, z −510 … −330; de vakken van de andere eilanden: tabel in `ZELDA.md` |

Alle huidige gebieden liggen in de chunks die de plugin geladen houdt (`onEnable`). Het Zelda-gebied niet: alleen
tijdens het bouwen staan er tijdelijk tickets, verder laden spelers zelf wat ze zien (de server heeft 1–2 GB).

## Bouwen en beheer

- `plugins/KitPvP/gebouwd.yml`: `grond` (vloerhoogte) en per deel de gebouwde versie (`hub`, `arena`, `kasteel`,
  `zelda`, `zelda-zee`). Een oude `gebouwd.txt` (tot versie 3) wordt gelezen voor de grondhoogte en daarna vervangen.
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
  teruglezen, niet te veel entiteiten; Zelda 2.0: eilanden met een aanleg, delen die elkaar niet overlappen, doelen
  op het eiland, genezen bij elke kamer, bronnen met vloer, personen binnen het speelgebied, de zee gebouwd met vrije
  dekplekken, niet meer feeën dan spelers; eiland 1: hoogstens twee verplichte kamers vóór de baas, bellen op hun
  plek, gebarsten muren dicht, haakpunten met een vrije landing, personen niet vast, hoogstens vier hartstukjes per
  eiland, geen dubbele vondsten of missies).
