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
| `ZeldaHoofdstuk` | basis van een eilanddeel + de onderdelen: `Rust`, `Bron`, `Kamer`, `Deur`, `Uitdaging`, `Reeks`, `Vondst`, `Verhaal`, `Persoon` (+ `Gesprek`), `Missie`, `Doel`, `Barst` (gebarsten muur), `Haakpunt`, `Spiegels` (draaispiegels), `Poort` (opent voor wie iets heeft), `Praten` (praten op het pad), `Box`; haken per deel: `tik`, `boem`, `wacht`, `hemel(plek, stand)` |
| `ZeldaHoofdstuk1` | «Het Lindewoud» (eiland 1, versie 4: een open eiland): Thuis, Lindedorp in het midden, drie wegen (noord, oost, zuid), de Heksenkring en de haven; Tijmen, Kees, Fien, Sem, Bram, Wout, Gerrit, Hanne, Brecht, Lotje; de oude stukken staan verschoven (`op(dx, dz)`); `motor()` = tips en doelen; `aanvullen` = oude standen (één keer omzetten) |
| `ZeldaEiland2a` | «Het Zilverstrand» west (eiland 2a, deel `zelda-e2a`): steiger, Zoutkom, de vuurtoren met de spiegelkelder, de duinen, de Meeuwenrots, de dijk; Brecht, Teun, Mira, Hendrik, Pim en Tijmen; zeekraal voor Hanne; de vlieger en het licht van de vuurtoren in `tik()` |
| `ZeldaZee` | bouwdeel `zelda-zee`: open zee met de Zilvermeeuw, voor de overtocht tussen eilanden |
| `ZeldaSpel` | de motor: sessies, zones, kamers en golven, baas, uitdagingen, puzzels, vondsten, bronnen, verteller, uitrusting, kaart, hint, gesprekken, missies, overtocht, listeners |
| `ZeldaMetgezel` | Wiske, de fee: een kleine allay die naast je vliegt (alleen voor jou zichtbaar), praat en wijst |
| `ZeldaDier` | je eigen huisdier (vlag `dier:<soort>`): loopt mee, snuffelt verborgen vondsten op |
| `ZeldaVoorwerp` | een voorwerp in de hotbar (nu: het flesje en de bommen; later klauwhaak, vuurstaf, lichtpijlen) |
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
  De zelftest eist hoogstens twee verplichte kamers (niet `vrijwillig`) vóór de eerste baas per deel, en hoogstens
  één verplichte kamer per half eiland (plus de baas).
- **Oefenplek** (`k.alleenBel`): de deuren staan open; wie erdoor loopt, loopt er gewoon door. Pas als je de bel luidt
  gaan ze dicht en wordt er gevochten (de eerste keer de gewone golven, daarna met de extra). Telt niet als verplichte
  kamer. Op het Lindewoud: het oefenveld en het Veen.
- **Poort** (`Poort`): een deur die opengaat als er iemand in het gebied staat voor wie hij mag (`mag`, bv. de vlag
  `lichtwoord` van Tijmen), en dan voor iedereen open is; een minuut niemand in de buurt = weer dicht. Op het
  Lindewoud: de doornen voor de Heksenkring.
- **Wegwijzer** (`spel.wegwijzer(p, naam)`): een persoon (Tijmen) zegt je volgende opdracht, zoals de tweede hint maar
  in zijn stem, en Wiske vliegt die kant op. Doelen in dezelfde `groep` mag je in elke volgorde doen: de hint en de
  wegwijzer noemen dan het dichtstbijzijnde.
- **Uitdaging op tijd**: stap op de startplaat, de poort gaat open, de tijd loopt in de **XP-balk** (getal = seconden,
  tikjes, de laatste vijf hoger). Haal je de finish: tijd en (bij verbetering) «record», bewaard per speler. Bij de
  start hangt een bord met de vijf beste tijden (alleen als er iemand in de buurt is).
- **Puzzel (reeks)**: zet alle dingen aan binnen de tijd vanaf het eerste (vuurschalen: rechtsklik; doelwitten: pijl).
  Opgelost = deur open (of brug erbij) tot het gebied een minuut leeg is. Wie hem ooit oploste (`r:<id>`), vindt hem
  open (handig als je na een dood bij de baas terugkomt).
- **Vondst**: loop over de sokkel = je hebt het (vlag); de uitrusting volgt altijd uit de vlaggen: zwaard, schild,
  boog met oneindig pijlen, voorwerpen (vak 3 … 7), **harten** (8 + hartcontainers `hart…` + een hart per vier
  **hartstukjes** `hs:…`), **harnas** (`harnas1` … `harnas4`, zichtbaar), **flesjes** (`fles1` … `fles4`, vak 3,
  rechtsklik = vijf harten; met `sterkwater` zeven). Rustplaatsen kunnen de vlaggen van ervoor ook geven (`geeft`; op
  een open eiland niet, daar kun je elke rustplaats zonder bereiken). Een vondst met `verborgen` wordt
  door je huisdier opgesnuffeld; met `vereist` alleen te zien als je die vlag hebt; met `voorwerp` wel te zien, maar
  pas te pakken met dat voorwerp (vlag `bommen`, `klauwhaak`, …). `daarna` = een actie na het vinden (missiestap).
  Hartstukjes heten `hs:<eiland>-<n>`; de kaart toont hoeveel je er op dit eiland hebt.
- **Bommen** (vlag `bommen`, vak 4, eiland 2a): rechtsklik = een bom gooien (een tel tussen twee bommen, ze raken
  nooit op). Waar hij neerkomt sist hij anderhalve tel, dan een knal: een gebarsten muur binnen twee blokken breekt
  open, vijanden binnen 3,5 blok krijgen een klap (12) en het deel kan zelf iets doen (`boem`). Blokken gaan nooit echt
  kapot.
- **Gebarsten muur** (`Barst`, blok `BARST`): dicht tot een bom hem openbreekt (`spel.breek`); wie erbij was
  (`b:<id>`) vindt hem daarna open; een minuut niemand in de buurt = weer dicht. Moet je erdoor op het pad, gebruik
  `barstOpPad` en zet `voor` (waar je staat om te gooien).
- **Spiegels** (`Spiegels`, eiland 2a): licht komt uit een bron en gaat rechtdoor; een spiegel (amethistkristal) stuurt
  het in de richting waar hij naar wijst, rechtsklik = een kwartslag draaien. Raakt het licht het doel, dan gaat de
  deur open (tot het gebied een minuut leeg is); `r:<id>` = voortaan opgelost. Geen tijd. Het licht is te zien als
  gele stipjes voor wie in de buurt is. Lokspiegels (`goed` = null) mogen. Samen puzzelen mag: komt er iemand bij die
  hem al eens oploste, dan staat hij meteen goed (zoals bij de reeksen).
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
  ze vliegt even die kant op. Geen doel meer = je zijmissies, «verder varen», wie er iets nieuws te vertellen heeft
  («Kees wil je iets vertellen», nog eens = ze wijst), wat je met een nieuw voorwerp nu kunt halen (hier, of «Op het
  Lindewoud kun je nu iets halen»), en anders wat het eiland nog laat wachten (`wacht()`, bv. «nog even geduld»). Zes
  minuten zonder voortgang: ze biedt één keer hulp aan.
- **Huisdier**: vlag `dier:<soort>`; de eerste keer kies je een naam (klein menu). Loopt mee, zit als je stilstaat,
  snuffelt verborgen vondsten op (loopt erheen en gaat erbij zitten). Onkwetsbaar, vecht niet.
- **Tekst**: alles op **één plek**, de actiebalk, één grijze regel tegelijk (spreker in groen), elke regel 2,5–6 s.
  Geen titels, geen chat. 
- **Muziek, altijd**: een achtergrondlied per streek (`muziek(Location)`: Thuis, Woud, Tempel, Veen, Haven, Zee,
  Zoutkom, Duinen, Vuurtoren, Lindedorp),
  daarboven de gevechts- of baaslus en losse deuntjes. Zachter met de schuif «Nootblokken».
- **Dood**: items blijven, geen doodsmelding; respawn bij je rustplaats. **Honger** vast op 17 (geen gewoon
  bijkomen door eten); genezen bij rustplaatsen, bronnen, flesjes, gewonnen kamers, de bakker in Lindedorp, de helft
  van de keren een hartje van een vijand, en **op adem komen**: buiten een gevecht, na 6 s zonder klap, elke 2 s een
  half hart. **Samen**: geen schade
  aan elkaar; de wolf (Huisdieren) vecht mee tegen monsters (`stand` = `bots`).
- **Kaart** (vak 9): de zielvuren van dit eiland, in de volgorde van het eiland (klik = reizen, **rechtsklik = opnieuw
  vanaf hier**: verhaal, uitleg en puzzels erna komen terug, spullen blijven; op een open eiland alleen tot de volgende
  rustplaats), de eilanden die open zijn (klik = **varen**), opdrachten, harten en
  hartstukjes, harnas, flesjes, **Uitleg: aan/uit**, terug naar de hub.
- **Overtocht**: varen = een paar tellen aan dek van de Zilvermeeuw op open zee (zeemansmuziek, schuim, Brecht aan
  het roer, een regel van Wiske of Brecht; de eerste keer de overtochtregels van het eiland), dan aan land bij de
  aanleg van het eiland (vlag `e:<eiland>`).
- **Hemel**: per speler; de dag schuift op van ochtend tot zonsondergang naarmate je verder komt (de baaskamer is nacht).
  Op het Lindewoud volgt hij wat je deed (een open eiland: je loopt ook terug); op het Zilverstrand de plek.
- **Grenzen**: wie buiten de delen van zijn eiland komt, gaat terug naar zijn rustplaats.
- **Entiteiten**: vijanden alleen tijdens een gevecht (tag `zelda`, niet opgeslagen, geen drops, branden niet);
  zwevende vondsten en personen alleen als er iemand in de buurt is; één Wiske (en eventueel één huisdier) per
  speler in Zelda. Geen vaste chunk-tickets in het Zelda-gebied.

### Eiland 1: «Het Lindewoud» (`h1`, deel `zelda` versie 4, bouwvak x 1024 … 1639, z −179 … 179)

Morgal, de Nachtvorst, heeft prinses Zelda meegenomen naar zijn kasteel Nachtrots, over de zee. Wiske, een fee, gaat
met je mee. Een **open eiland** (land x 1045 … 1492, z −105 … 105; de rest van het vak is zee tot aan de buren). Kaart
in `ZELDA.md` («1 · Het Lindewoud»); kort, met rx = x − 1100:

| rx, z | wat | rust |
|---|---|---|
| 16, 0 | Thuis (huisje, Kees) | 0 `thuis` |
| 112, 2 | **Lindedorp**: linde, plein, put met bron; Tijmen (sterrentoren), Fien (bakker), Sem | 6 `dorp` |
| 98, −50 | de Oude Eik (hol), de houtkap (89, −70) | 1 `eik` |
| 140, −60 | het oefenveld: Bram (schild), oefenplek met bel | — |
| 168 … 206, 0 | de tempelruïne: vuurschalen, zwaard, tempelmuur (bommen) | 2 `ruine` |
| 220 … 253, 0 | de Kloof: race over de pilaren of ladders en beek; richel (klauwhaak) | 3 `kloofrand` |
| 268 … 325, 0 | de havenweg, de weide van Gerrit | — |
| 150, 62 | het Veen: Wout, de boog op het eilandje, kruiden; oefenplek met bel | — |
| 174, 62 | de moerashut van Hanne | 4 `moerashut` |
| 183 … 197, 62 | de drie doelen (brug), de doornen (poort: lichtwoord) | — |
| 213, 62 | de Heksenkring: Grauwmoer (baas), hartcontainer | — |
| 319 … 381, 0 | de haven: kade (twee soldaten), pier met Brecht en Lotje, de Zilvermeeuw | 5 `haven` |
| 432 … 472, 0 | Nachtrots aan de horizon | — |

### Eiland 2a: «Het Zilverstrand» west (`e2a`, deel `zelda-e2a`, x 1060 … 1229, z −480 … −180)

Zie de tabel in `ZELDA.md` («2a · west, het dorp»). Kort: steiger (rust 0) → Zoutkom (rust 1, Teun, Hendrik) →
smokkelgrot (bommen) → kaap (rust 2) → het bordes (het enige gevecht) → de vuurtoren (Mira) → de kelder (spiegels,
de bommenzak, de gebarsten wand) → het noordstrand → de duinpan (rust 3, Pim, de duinbaan) → de Meeuwenrots (een bom,
het Zeelicht) → terug naar Mira: het licht brandt (einde `e2a`). De dijk naar 2b staat nog onder water.

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
- `kitpvp zelda reset <speler>` — voortgang wissen. `kitpvp zelda rust <speler> <n>` — op rustplaats n zetten (helpen;
  op het open Lindewoud geeft een rustplaats geen spullen meer: die geef je met `kitpvp zelda vlag`).
- `kitpvp zelda test <speler> [alles]` — loopt met een echte speler door de poort en het hoofdstuk (kamers verslaan,
  puzzels, uitdaging, vondsten, rustplaatsen, opslag op schijf); zonder `alles` tot de eerste rustplaats na de eerste
  kamer; met `alles` ook vrije doorgang en de bel, vondsten voor later (blijven liggen), spiegels (echte klikken),
  gebarsten muren op het pad (een echte bom), wat met de voorwerpen van nu te halen is, de zijmissies, Brecht (klik in
  zijn menu = varen naar het volgende eiland), en aan het eind via de kaart terug naar een eerder eiland om daar met de
  bommen te halen wat kon (`hs:lindewoud-4`); `dood` = sterven in de eerste kamer (respawn bij je rustplaats, spullen houden, kamer terug). **Wist eerst de
  voortgang van die speler** (alleen voor tests; zijn testtijd komt op het tijdenbord, `reset` haalt hem weg). `kitpvp zelda verder <speler>` — door de poort,
  en sta je op je verste rustplaats? (na uit- en inloggen).
- `kitpvp zelda test <speler> motor` — de motor met een echte speler: Wiske, muziek, `/hint` (twee keer), de wegwijzer
  (Tijmen noemt het dichtstbijzijnde doel van een groep), bron (met alleen Tijmens flesje),
  flesje, hartstukjes, harnas, huisdier (verschijnt, loopt mee), gesprek en zijmissie met beloning, opnieuw vanaf een
  rustplaats, uitleg uit, de bommenzak (vak 4) en een echte bom tegen de tempelmuur, varen en aankomen, tip na
  doodgaan in een kamer, de kaart, en weg = alles weg. Wist eerst en
  na afloop de voortgang van die speler (alleen voor tests).
- `kitpvp zelda lopen <eiland>` — is alles te belopen? Volgt vanaf de aanleg elke plek waar je kunt staan (stap ≤ 1
  omhoog, ≤ 3 omlaag, een gat van één blok, ladders, zwemmen; deuren en gebarsten muren open) en noemt wat je niet
  haalt (rustplaats, persoon, vondst, kamer, uitdaging, verhaal, bron, spiegel, bel). Deuren, poorten en bruggen tellen
  als open. Laadt de chunks van het eiland; doet ook mee in `test … alles` (voor elk eiland; over de Kloof van het
  Lindewoud kom je ook via de ladders en de beek).
- `kitpvp zelda vaar <speler> <eiland>` — de overtocht naar een eiland. `kitpvp zelda vlag <speler> <vlag>` — een
  vlag aan (nog eens = uit), bv. `fles1`, `hs:test`, `harnas2`, `dier:vos`, `o:uitleg-uit` (helpen en testen).
  `kitpvp zelda hint <speler>` — de hint die hij nu zou krijgen.
- `kitpvp bouw zelda` — het Lindewoud opnieuw bouwen, `kitpvp bouw zelda-e2a` het Zilverstrand west, `kitpvp bouw
  zelda-zee` de zee. `kitpvp kaart` maakt ook `zelda-*.png` (het Lindewoud: boven, thuis, dorp, noord, tempel, veen,
  haven en zijaanzichten; `zelda-e2a-*`: boven, dorp, kelder, kaap, duinen, rots).
- `/zelda ga <plek>` kent ook puzzels, uitdagingen en poorten (`vuren`, `kloof`, `doelen`, `spiegels`, `doornen`) en
  zoekt daar een plek om te staan.

## Ruimte in de wereld

| gebied | x | z | wat |
|---|---|---|---|
| hub | −195 … −125 | −35 … 35 | midden −160/0, binnenkomst −140,5/0,5 (kijkt west) |
| colosseum + kitruimte | −50 … 50 | −60 … 50 | midden 0/0, kitruimte boven de noordmuur (z −57 … −35) |
| oefenarena + kitruimte | −24 … 24 | 51 … 134 | midden 0/110, kitruimte z 63 … 87 |
| kasteel + kitruimte | −42 … 42 | −188 … −94 | midden 0/−150, gracht rondom, kitruimte z −119 … −95 |
| **Zelda** | **1024 … 2047** | **−512 … 511** | gereserveerd (`Zelda.GEBIED`); het Lindewoud bouwt x 1024 … 1639, z −179 … 179 (land x 1045 … 1492, z −105 … 105, de rest zee); het Zilverstrand x 1060 … 1360, z −480 … −180 (2a tot 1229); de zee (overtocht) x 1840 … 2040, z −510 … −330; de vakken van de andere eilanden: tabel in `ZELDA.md` |

Alle huidige gebieden liggen in de chunks die de plugin geladen houdt (`onEnable`). Het Zelda-gebied niet: alleen
tijdens het bouwen staan er tijdelijk tickets, verder laden spelers zelf wat ze zien (de server heeft 1–2 GB).

## Bouwen en beheer

- `plugins/KitPvP/gebouwd.yml`: `grond` (vloerhoogte) en per deel de gebouwde versie (`hub`, `arena`, `kasteel`,
  `zelda`, `zelda-e2a`, `zelda-zee`). Een oude `gebouwd.txt` (tot versie 3) wordt gelezen voor de grondhoogte en daarna vervangen.
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
  eiland, geen dubbele vondsten of missies; het open eiland: rustnummers 0 … n−1, oefenplekken open en met een bel,
  poorten dicht in rust en niet voor iedereen open; eiland 2: hoogstens één verplichte kamer per half eiland (nu voor
  elk eiland), spiegels in de
  begintoestand en met de oplossing komt het licht bij het doel (maar niet al aan het begin), een vrije plek vóór elke
  gebarsten muur, elk voorwerp in een eigen vak).
