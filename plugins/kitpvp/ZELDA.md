# Story of Zelda 2.0 — De Glazen Zee

Ontwerpdocument (oktober 2026). Hoofdstuk 1 «Het Lindewoud» staat live en viel goed ("erg leuk, verrast hoe goed het
is"). Zelda 2.0 maakt er een groter avontuur van: **vijf eilanden** in de Glazen Zee, elk een hub met meerdere dingen
erop, verbonden door het schip de Zilvermeeuw. Dit document is het plan voor de bouwsessies: verhaal, eilanden,
mechanics en de verdeling in sessies. De gedeelde motor is gebouwd (zie «De motor»), eiland 1 ook (sessie
`zelda-eiland-1`, kitpvp 2.4; en opnieuw als **open eiland** in sessie `zelda-lindewoud-v2`, kitpvp 2.6) en de westhelft
van eiland 2 (sessie `zelda-eiland-2a`, kitpvp 2.5); de andere eilanden bouwen latere sessies, één per (half) eiland,
in de volgorde onderaan. **Terugkerende personages** (Tijmen de wegwijzer, Brecht, Hanne, …) knopen de eilanden aan
elkaar: zie «Terugkerende personages».

Techniek en klassen: `MODI.md` (hoe het in elkaar zit) en de javadoc van `Zelda*.java`.

## Jelles wensen → wat het spel doet

| wens | in het spel | waar |
|---|---|---|
| poorten open/dicht, boog, muziek per situatie | blijft; elk eiland heeft eigen poorten, boogpuzzels en eigen muziek | motor + eilanden |
| meer uitleg, ook halverwege | Wiske legt uit als je iets nieuws ziet of krijgt, bij een nieuw voorwerp, en geeft **tips na doodgaan** in een kamer | motor (`Verhaal.uitleg`, `Kamer.tips`) |
| muziek altijd aan | elke streek heeft een eigen **achtergrondlied** dat altijd loopt; gevecht/baas eroverheen; deuntjes daarbovenop | motor (`ZeldaMuziek`) |
| niet meteen en niet zo vaak vechten | bouwregel: per half eiland hoogstens één verplichte gevechtskamer, nooit in de eerste vijf minuten; liever puzzels, praten, zoeken. Een kamer die je al won laat je **vrij door**; wie nog eens wil, luidt de **bel** (dan met extra golven) | eilanden (regel hieronder) + motor |
| eilanden als hub, later terug | eilanden met aanlegsteiger, kaart: **varen** naar elk eiland dat open is; alles blijft speelbaar | motor (`ZeldaEiland`, `ZeldaZee`) |
| wel steeds vooruit | elk eiland heeft één hoofdopdracht (het baken); die opent het volgende eiland | motor + eilanden |
| genezen bij elke arena, "je moet kunnen rehealen" | **bron** (feeënkring) of zielvuur bij elke kamer (de zelftest eist het); een flesje vanaf het begin (Tijmen); buiten een gevecht kom je **vanzelf op adem** (na 6 s zonder klap elke 2 s een half hart); de bakker in Lindedorp maakt je helemaal beter | motor + zelftest + eiland 1 |
| "kunnen teruglopen, het is nu een recht pad" | elk eiland is **open**: een dorp of hub in het midden, wegen die je in elke volgorde loopt en die in **rondjes** terugkomen; de doelen van een groep mag je in elke volgorde doen (de hint noemt het dichtstbijzijnde) | eilanden + motor (`doel(groep, …)`) |
| "meer ruimtes tussen de arena's met meer mensen, storyline" | personen langs de wegen (een jager bij het Veen, een herder aan de havenweg, de bakker en een kind in het dorp) die iets zeggen over waar je bent en wat je deed | eilanden |
| "een magier die altijd weet waar je volgende opdracht is" | **Tijmen**, een reizende tovenaar: op elk eiland, praten = hij zegt je volgende opdracht en Wiske vliegt die kant op; bij hem kom je steeds terug (hij geeft wat je nodig hebt, zoals het lichtwoord) | motor (`spel.wegwijzer`) + elk eiland |
| "opdrachten die heen-en-weer reizen vragen" | per eiland minstens één **boodschap over zee**: een bekende op een eerder eiland vraagt iets van het nieuwe (Hanne: zeekraal van het Zilverstrand) | eilanden |
| extra hartjes | **hartcontainer** na elke baas, **hartstukjes** (vier = een hart) verstopt en als beloning | motor |
| harnas als progressie | vijf trappen, zichtbaar aan je lijf: tuniek → maliënhemd → wachtersharnas → gloedharnas → lichtharnas | motor (`ZeldaRegels.harnas`) |
| zichtbare fee die praat en meeloopt | **Wiske** vliegt als kleine fee naast je schouder, komt voor je zweven als ze praat, wijst de weg | motor (`ZeldaMetgezel`) |
| later je eigen huisdier | op de Witte Toppen vind je een sneeuwvosje; je kiest zijn naam; hij loopt mee en **snuffelt verstopte dingen op** | motor (`ZeldaDier`) + eiland 3 |
| NPC's, zijmissies, dialoog | personen met gesprekken die van je voortgang afhangen; zijmissies met stappen en beloning | motor (`Persoon.gesprek`, `Missie`) |
| helper als je vastzit | `/hint` of klik op Wiske: eerst een duwtje, nog eens vragen = wat je moet doen en ze vliegt die kant op; na zes minuten zonder voortgang biedt ze één keer hulp aan | motor |
| een stuk opnieuw spelen | kaart: **rechtsklik op een zielvuur = opnieuw vanaf hier** (uitleg en puzzels erna komen terug, spullen blijven); **Uitleg: aan/uit** | motor |

**Smaak (hard):** alles wat spelers lezen is één korte grijze regel op de actiebalk (spreker in groen), nooit chat of
titels, nooit dubbel. Dialoog is kort: hoogstens vier regels per gesprek, een regel hoogstens ±60 tekens.

## Het verhaal

Morgal, de Nachtvorst, wil een nacht die nooit ophoudt. Hij nam prinses Zelda mee naar zijn burcht **Nachtrots**,
omdat zij het licht van de Glazen Zee bewaart. Om Nachtrots legde hij de **Nevelring**: een muur van zwarte mist waar
geen schip doorheen komt. Alleen het licht van de drie **bakens** kan de nevel breken, maar Morgal heeft ze gedoofd.

Jij bent een kind uit een huisje aan de bosrand. Met **Wiske**, een fee die eerst een vonkje was en met elk baken een
beetje groter en feller wordt, ga je op weg. Visser **Brecht** vaart je met de **Zilvermeeuw** van eiland naar eiland.

- **Hoofdstuk 1 / eiland 1 — Het Lindewoud** (bestaat, open eiland): in Lindedorp ontmoet je Tijmen, de reizende
  tovenaar. Je haalt in elke volgorde een schild (wachter Bram), een zwaard (de tempel) en een boog (het Veen), krijgt
  van Tijmen het lichtwoord, verslaat de moerasheks Grauwmoer achter haar doornen en jaagt Morgals soldaten van de kade.
  Brecht neemt je mee aan boord.
- **Eiland 2 — Het Zilverstrand**: een vissersdorp aan zee, een vuurtoren zonder licht, de Getijdentempel. Het
  **Getijdenbaken**.
- **Eiland 3 — De Witte Toppen**: sneeuw, een bergdorp, een ijspaleis. Je vindt je **sneeuwvosje**. Het **Vorstbaken**.
- **Eiland 4 — De Gloedberg**: een vulkaan met een mijnwerkersdorp en een oude smidse. Het **Gloedbaken**.
- **Eiland 5 — Nachtrots**: met drie bakens breekt de Nevelring. De burcht, de gevangenen, de toren, Morgal. Zelda.
- **Thuis**: na Morgal vaart iedereen mee terug naar het Lindewoud: feest op de open plek, alle personen die je
  hielp staan er. Daarna is alles vrij te bezoeken (records, hartstukjes, zijmissies).

Elk baken dat weer brandt is van ver te zien (een lichtbundel) en maakt Wiske feller (zichtbaar: meer glans). Toon:
licht en warm, een beetje grappig (Wiske), nooit eng; vijanden zijn betoverd door Morgals nevel en lossen op in
licht als je ze verslaat. De bazen zijn wezens die je bevrijdt, niet doodt (behalve Morgal, die verdwijnt in zijn
eigen nevel).

## De eilanden

Ruimte: alles in `Zelda.GEBIED` (x 1024 … 2047, z −512 … 511). Elk half eiland is een eigen bouwdeel met een eigen
versie. Zee om elk eiland tot de rand van zijn vak (zichtafstand op kitpvp is 6 chunks).

| eiland | delen (id · bouwdeel) | vak (x · z) | aanleg | open na | klaar |
|---|---|---|---|---|---|
| 1 Het Lindewoud | `h1` · `zelda` | 1024 … 1639 · −179 … 179 (bouwvak, vooral zee); land 1045 … 1492 · −105 … 105 | rust `haven` | — | `h1` |
| 2 Het Zilverstrand | `e2a` · `zelda-e2a`, `e2b` · `zelda-e2b` | 1060 … 1360 · −480 … −180 (2a tot x 1229, 2b vanaf 1230) | rust `steiger` (e2a) | `h1` | `baken2` |
| 3 De Witte Toppen | `e3a`, `e3b` | 1400 … 1720 · −480 … −180 | `steiger` (e3a) | `baken2` | `baken3` |
| 4 De Gloedberg | `e4a`, `e4b` | 1060 … 1400 · 180 … 480 | `steiger` (e4a) | `baken3` | `baken4` |
| 5 Nachtrots | `e5a`, `e5b` | 1640 … 2000 · −150 … 150 | `steiger` (e5a) | `baken4` | `zelda` |
| de zee (overtocht) | `zee` · `zelda-zee` | 1840 … 2040 · −510 … −330 | — | — | — |

Nachtrots ligt achter de silhouet-burcht aan de horizon van het Lindewoud (x 1520 … 1572), zodat je hem vanaf de haven
al ziet liggen. Vrij voor later: x 1450 … 2040, z 180 … 510. Het bouwvak van het Lindewoud loopt tot aan de vakken van
de buren (z −179 / 179, x 1639): daar bouwt het alleen zee, zodat de zeeën van de eilanden op elkaar aansluiten.

### Bouwregels voor elk eiland

0. **Open, met een hub en rondjes** (Jelle 27-10). Een dorp of plein in het midden (zielvuur, bron, een bordje per
   uitgang), wegen in elke volgorde, en wegen die in een rondje terugkomen: nooit één recht pad. Wat je in elke
   volgorde mag doen, krijgt een doelgroep (`doel("groep", …)`). Een poort voor wie iets heeft (`Poort`) of de
   uitgang van een kamer houdt de volgorde waar het moet. Bij elke weg een of twee personen met iets te zeggen.
   **Tijmen** staat op elk eiland bij de hub (zie «Terugkerende personages»), en elk eiland heeft een **boodschap over
   zee** voor een bekende van een eerder eiland.
1. **Eerst rondkijken, dan vechten.** Aankomst bij de steiger met een zielvuur, een dorp of plein met personen, en
   pas na een paar minuten verkennen het eerste gevecht. Per half eiland hoogstens **één verplichte kamer** (plus de
   baas in de kerker; de zelftest eist hoogstens twee vóór de baas per deel). Extra gevechten alleen als zijmissie of
   uitdaging (`k.vrijwillig = true`), of als **oefenplek** (`k.alleenBel = true`: deuren open, gevochten wordt pas als
   je de bel luidt). Wie terugkomt, gaat vrij door een gewonnen kamer; geef elke gewone kamer een **bel** (`k.bel`) en
   een zwaardere **extra** golf (`k.extra(...)`) voor wie nog eens wil. Gevechten licht houden: twee of drie vijanden,
   een baas met weinig helpers (`baas.leven`, `baas.oproepen`).
2. **Genezen bij elke kamer**: een zielvuur of bron binnen 32 blokken van de ingang (zelftest controleert dit), het
   liefst er vlak voor.
3. **Hub**: vanaf de steiger zijn er minstens drie dingen te doen (hoofdopdracht, een zijmissie, iets om te
   zoeken). Een rustplaats bij elk knooppunt.
4. **Eén nieuw voorwerp per eiland**, en de kerker draait om dat voorwerp. Op elk eiland liggen ook **één of twee
   plekken die pas met een later voorwerp opengaan** (een gebarsten muur, een haakpunt, ijs): reden om terug te komen.
   Bouw ze er meteen in; het voorwerp komt later. Altijd dezelfde blokken, zodat spelers ze leren herkennen: een
   **gebarsten muur** is `ZeldaHoofdstuk.BARST` (cracked deepslate bricks) en een `Barst`-onderdeel; een
   **haakpunt** is `ZeldaHoofdstuk.HAAKPUNT` (lodestone) en een `Haakpunt`-onderdeel (met de landingsplek). Een vondst
   erachter krijgt `voorwerp = "<vlag>"`: hij is te zien, maar pas te pakken met dat voorwerp.
5. **Vier hartstukjes per eiland** (`hs:<eiland>-<n>`; de zelftest eist hoogstens vier), waarvan één als
   zijmissiebeloning (`spel.beloon`, en `stukje(vlag)` bij het bouwen zodat de kaart hem meetelt) en één of twee pas
   met een later voorwerp. Een hartstukje buiten het pad krijgt `verborgen = true` (je huisdier snuffelt hem op).
6. **Muziek**: twee of drie eigen achtergrondliedjes (dorp, buiten, kerker) en de baasmuziek; `muziek(Location)`
   geeft er altijd één.
7. **Doelen** (`doel(...)`) voor elke stap van de hoofdopdracht, met een duwtje en een `verder`-zin, zodat `/hint`
   overal werkt. Zijmissies met korte stapzinnen.
8. **Uitleg** van iets nieuws als `Verhaal` met `uitleg = true` (wordt overgeslagen met «Uitleg: uit»); verhaal
   zonder. Bij elke kamer twee tips (`k.tips`).
9. **Vier tot zes personen** met een eigen stem; minstens twee met een zijmissie.
10. **Licht op de entiteiten**: vijanden alleen tijdens een gevecht, personen en vondsten alleen als er iemand in de
    buurt is (de motor regelt dat). Bouwen verdeeld over ticks, chunk-tickets alleen tijdens het bouwen.

### Voorwerpen, harnas, harten, flesjes (de hele reis)

| eiland | voorwerp (vak, vlag) | harnas | hart | flesje | ook |
|---|---|---|---|---|---|
| 1 Lindewoud | zwaard, schild, boog (bestaan) | groene tuniek | `hart1` (Grauwmoer) | `fles1` (zijmissie Hanne), `fles2` (Tijmen, meteen) | `lichtwoord` (Tijmen), `sterkwater` (Hanne, met zeekraal van het Zilverstrand) |
| 2 Zilverstrand | **bommen** (vak 4, vlag `bommen`): gooien, gebarsten muren, baas verdoven | `harnas1` maliënhemd (smid Hendrik) | `hart2` | `fles3` | — |
| 3 Witte Toppen | **klauwhaak** (vak 5, vlag `klauwhaak`): naar een haakpunt trekken | `harnas2` wachtersharnas (bergwachters) | `hart3` | `fles4` | **sneeuwvos** (`dier:sneeuwvos`) |
| 4 Gloedberg | **vuurstaf** (vak 6, vlag `vuurstaf`): vuur op afstand, ijs smelten | `harnas3` gloedharnas (smeden; hitte in de mijn) | `hart4` | — | — |
| 5 Nachtrots | **lichtpijlen** (boog, vak 2, vlag `lichtpijlen`): nevel en Morgal | `harnas4` lichtharnas (Zelda's wachters) | — | — | het einde |

De vlag van een voorwerp is vast: de vondsten op eerdere eilanden die erop wachten (`voorwerp = "bommen"`, …) gebruiken
hem al. Flesjes: de nummers zijn alleen namen (`ZeldaRegels.flessen` telt `fles1` … `fles8`); het Lindewoud geeft er
sinds versie 4 twee (Tijmen meteen `fles2`, zodat je vanaf het begin kunt genezen), dus 2b geeft `fles3` en de Witte
Toppen `fles4`; dan zijn het er vier.

Een voorwerp is een `ZeldaVoorwerp` (zie het flesje in `ZeldaSpel.Flesje`), geregistreerd in `Zelda`; de sessie van
dat eiland bouwt het. Harten: 8 aan het begin, +4 containers, +5 uit twintig stukjes = hoogstens 17.

### 1 · Het Lindewoud (open eiland: sessie `zelda-lindewoud-v2`, deel `zelda` versie 4)

Jelle (27-10): "Lindewoud zelf vind ik nog iets teveel vechten, en overigens te moeilijk, je moet kunnen rehealen. En
meer ruimtes tussen de arena's met meer mensen, storyline etc. Kunnen teruglopen, het is nu een recht pad." Versie 4
maakt er een **open eiland** van: een dorp in het midden, drie wegen die je in elke volgorde loopt, rondjes terug, veel
minder vechten en overal genezen. De oude stukken (eik, tempel, kloof, veen, kring, haven) staan op een nieuwe plek
(`ZeldaHoofdstuk1.op(dx, dz)` verschuift hun bouwcode), de rustplaatsen houden hun nummer.

Kaart (rx, z; x = 1100 + rx; eiland ongeveer rx −45 … 348, z −98 … 100; Nachtrots aan de horizon op rx 432 … 472):

| plek (rx, z) | wat | soort |
|---|---|---|
| 16, 0 (west) | **Thuis**: het huisje aan de bosrand, moestuin, put; **Kees** bij zijn houtstapel (*De verloren bijl*) | rust 0 `thuis`, verhaal, persoon |
| 28 … 88, 0 … 4 | het bospad naar het dorp, met een feeënkring | bron `bron-bospad` |
| 112, 2 (midden) | **Lindedorp**: de grote linde, plein, put met feeënkring, bordjes bij elke uitgang; de **sterrentoren van Tijmen** (97, −10), de bakkerij van **Fien**, twee huizen, een marktkraam; **Sem** speelt ridder | rust 6 `dorp`, bron `bron-dorp`, personen |
| 98, −50 (noord) | **de Oude Eik** (hol: `hs:lindewoud-1`, verborgen); erachter de **houtkap** (89, −70) met de bijl van Kees | rust 1 `eik`, vondsten |
| 140, −60 (noord) | **het oefenveld** (de palissade van de open plek): wachter **Bram** geeft het **schild**; de bel = oefenen tegen veenlopers | kamer `open-plek` (alleen met de bel), bron |
| 168 … 206, 0 (oost) | **de tempelruïne**: drie vuurschalen (30 s) → het **Wachterszwaard**; de **tempelmuur** met `hs:lindewoud-4` (bommen); een doorgang in de noordmuur naar het bospad van het oefenveld | puzzel, vondst, barst, rust 2 `ruine` |
| 220 … 253, 0 | **de Kloof**: de tijdrace over de pilaren (25 s), of langzaam: ladder omlaag, door de beek, ladder op (oostwand, z 11); de **richel** met `hs:lindewoud-3` (klauwhaak) | uitdaging, haakpunt, rust 3 `kloofrand` |
| 268 … 325, 0 | **de havenweg**, met de weide van schaapherder **Gerrit** (287, −13) | persoon |
| 150, 62 (zuid) | **het Veen** (wortelpoorten): jager **Wout** bij de ingang; de **boog** op het eilandje in het midden; de drie kruiden voor Hanne; de bel = jagen (twee golven) | kamer `veen` (alleen met de bel), bron, vondsten |
| 174, 62 | **de moerashut** van **Hanne** (*Moeraskruid* → `fles1`; later *Zeekraal* → `sterkwater`) | rust 4 `moerashut`, persoon |
| 183 … 193, 62 | **de drie doelen** aan het kanaal → de brug | puzzel `doelen` |
| 197, 62 | **de doornen**: wijken alleen voor wie het **lichtwoord** van Tijmen kent (of Grauwmoer al versloeg) | poort `doornen`, bron `bron-kring` |
| 213, 62 | **de Heksenkring**: baas **Grauwmoer** (60 leven + 30 per extra speler, één mosschutter bij twee derde en bij een derde) | kamer + baas, vondst `hart1` |
| 225 … 303 | **het kringpad** van de Heksenkring naar de havenweg | — |
| 319 … 381, 0 (oost) | **de haven**: de kade (twee soldaten), de pier met **Brecht** en **Lotje**, de Zilvermeeuw; boven zee hangt Grauwmoers **nevel** (rook, alleen voor wie haar nog niet versloeg) | rust 5 `haven`, kamer `kade`, verhaal: einde (`h1`, vereist `hart1`) |

- **Hoofdlijn** (doelen voor `/hint` en Tijmen): naar het dorp en praat met Tijmen → **in elke volgorde** het schild,
  het zwaard en de boog (doelgroep `drie`: de hint noemt wat het dichtst bij is) → terug naar Tijmen (het lichtwoord) →
  de doelen (de brug) → de doornen → Grauwmoer (`hart1`) → de kade → Brecht. Wie de haven eerder haalt (langs de
  Kloof), hoort van Brecht dat er niemand vaart zolang Grauwmoers nevel er hangt.
- **Rondjes**: dorp → Oude Eik → oefenveld → bospad → tempel → dorp; tempel → Kloof → havenweg → kringpad →
  Heksenkring → doelen → moerashut → Veen → dorp. De Heksenkring kun je alleen van de westkant in (de uitgang aan de
  oostkant gaat pas open als je hem won), zo blijft Grauwmoer nodig.
- **Vechten**: verplicht alleen Grauwmoer en de kade (twee soldaten; de bel = nog eens met kruisbogen). Het oefenveld
  en het Veen zijn **oefenplekken** (`Kamer.alleenBel`): de deuren staan open en er wordt pas gevochten als je de bel
  luidt (de eerste keer de gewone golven, daarna met de extra). Het eerste verplichte gevecht komt na het halen van
  drie dingen en een tweede gesprek met Tijmen.
- **Genezen**: zeven rustplaatsen, een bron bij elke kamer (bospad, dorp, oefenveld, Veen, voor de doornen), Fien de
  bakker (praten = helemaal beter), een flesje van Tijmen meteen (`fles2`) en een van Hanne (`fles1`), en buiten een
  gevecht kom je vanzelf op adem. Een vijand laat vaker een hartje vallen (de helft van de keren).
- **Personen** (tien): Tijmen (wegwijzer), Kees, Fien, Sem, Bram, Wout, Gerrit, Hanne, Brecht, Lotje. Wie iets nieuws
  heeft glinstert. Zijmissies: *De verloren bijl* (Kees → `hs:lindewoud-2`), *Moeraskruid* (Hanne → `fles1`),
  *Zeekraal* (Hanne, pas als je op het Zilverstrand was: zeekraal aan de voet van de dijk daar → `sterkwater`).
- **Vier hartstukjes** blijven: de holle Oude Eik (verborgen; Kees verklapt het), Kees, de richel in de Kloof
  (klauwhaak) en achter de tempelmuur (bommen).
- **Hemel**: de dag schuift op met wat je deed (Tijmen, schild, zwaard, boog, lichtwoord, Grauwmoer, kade), niet met
  waar je staat: op een open eiland loop je ook terug. De Heksenkring blijft nacht.
- **Muziek**: *Thuis*, *Lindedorp* (nieuw: banjo en gitaar), *Woud* (noord, de Kloof, de havenweg), *Tempel*, *Veen*
  (Veen en Heksenkring), *Haven*.
- **Bordjes** (staande borden, alleen namen) bij de uitgangen van het dorp en op de kruisingen.
- **Oude standen**: de rustplaatsen houden hun nummer (`thuis` 0, `eik` 1, `ruine` 2, `kloofrand` 3, `moerashut` 4,
  `haven` 5; nieuw: `dorp` 6), dus `laatste` blijft kloppen. Eén keer (vlag `o:lindewoud2`) krijgt een stand van het
  rechte pad wat er vóór zijn verste rustplaats lag (`schild` vanaf 1, `zwaard` vanaf 2, `boog` en `k:veen` vanaf 4,
  `hart1` vanaf 5) en de `k:`-vlaggen; daarna telt het nummer niet meer als «verder op het pad». Wie Grauwmoer al
  versloeg, hoort Tijmen anders (`tijmen-oud`: alleen het flesje) en kan door de doornen.
- **Afwijkingen van het oude plan, en waarom**: het schild komt van Bram (praten, geen gevecht: het eerste uur is
  rustig); de open plek en het Veen zijn oefenplekken; de rustplaatsen hebben geen `geeft` meer (op een open eiland
  kun je elke rustplaats bereiken zonder wat ervoor lag); Grauwmoer is lichter; de Kloof is ook zonder race over te
  steken (de race blijft voor het record); `fles2` hoort nu bij het Lindewoud.

### 2 · Het Zilverstrand

Zon, duinen, helmgras, een vissersdorp **Zoutkom** met netten en een rokerij, een vuurtoren zonder licht, en voor de
kust de **Getijdentempel** die bij eb boven water komt.

#### 2a · west, het dorp (gebouwd: sessie `zelda-eiland-2a`, deel `zelda-e2a` versie 1, klasse `ZeldaEiland2a`)

Het pad (de test loopt het in deze volgorde af; rustplaatsen 0 … 3, nooit tussenvoegen):

| plek (x, z) | wat | soort |
|---|---|---|
| 1125 … 1137, −223 … −213 | **de steiger**: een lange houten pier met een stenen kop, de Zilvermeeuw ligt ernaast, **Brecht** (varen) | rust 0 `steiger` (aanleg), verhaal |
| 1148, −296 | **Zoutkom**: plein met een put, vier witte huisjes met blauwgroene daken, een rokerij, netten en bootjes op het strand; **Ouwe Teun** op het bankje (de bakens; wat er met Mira is); **Hendrik** in zijn open smidse (zijmissie *Zilverschelpen*) | rust 1 `plein`, personen |
| 1095, −304 | **de smokkelgrot** onder de kaap: een gebarsten muur aan het strand, `hs:zilverstrand-2` erachter (pas met bommen) | barst, vondst |
| 1120, −322 | voet van de trap naar de kaap | rust 2 `kaap` |
| 1095 … 1106, −327 … −317 | **het bordes** voor de vuurtoren: meeuwenrovers (twee rovers en een schutter, één golf; de bel = nog eens, met vier) — het enige gevecht | kamer `bordes` |
| 1089, −322 | **de vuurtoren** (rood-wit, 24 hoog, omloop en lantaarnkamer, ladder naar boven): **Mira** zat opgesloten | verhaal, persoon |
| 1081 … 1097, −331 … −313 | **de kelder** (ladder omlaag): licht valt door een glazen tegel in het bordes en een gat in de oostwand; vier **draaispiegels** (één lokspiegel) leiden het naar een lamp boven de tralies | spiegels `spiegels` |
| 1089, −308 | het kamertje achter de tralies: Mira's **bommenzak** | vondst `bommen` |
| 1088, −332 | **gebarsten kelderwand** (noord): de eerste bom; een gang naar het noordstrand | barst `kelder` (op het pad) |
| 1117, −355 | het noordstrand met een wrak | (schelp) |
| 1150, −372 | **de duinpan**: **Pim** (kind) met zijn vlieger; de **duinbaan** (startplaat, hekje, vlaggetjes, 22 s, de vlieger vliegt voor je uit) | rust 3 `duinpan`, uitdaging `vlieger` |
| 1198, −398 | **de Meeuwenrots**: een rots van 20 hoog vol meeuwennesten; een gebarsten muur aan de zuidkant, erachter het nest met **het Zeelicht** (Mira's lamp); een bron ervoor; boven op de rots `hs:zilverstrand-3` en een haakpunt (pas met de klauwhaak) | barst `rots` (op het pad), vondst, haakpunt, bron |
| 1206 … 1229, −332 … −328 | **de dijk** naar de tempel, vanaf x 1221 onder water, met een dijkhuisje en een peilschaal | verhaal |
| 1089, −322 | terug bij Mira met het Zeelicht: de vuurtoren brandt (lamp en een lichtbundel naar de tempel, alleen voor wie hem terugbracht) | verhaal: einde (`e2a`) |

- **Hoofdlijn**: Teun → de vuurtoren → het bordes → Mira → de spiegels → de bommen → de kelderwand → de
  Meeuwenrots → het Zeelicht terug. Acht doelen voor `/hint`. Is alles op 2a gedaan en is 2b er nog niet, dan zegt
  Wiske «De tempel komt pas bij eb boven. Nog even geduld.» (`wacht()`).
- **Personen** (zes): Brecht, Teun, Mira (de zus van Brecht, zegt Brecht tijdens de eerste overtocht), Hendrik, Pim,
  en sinds `zelda-lindewoud-v2` ook **Tijmen** op het plein (1142, −297; de wegwijzer, zonder blokken). Aan de voet
  van de dijk (1209, −327) groeit **zeekraal** voor Hanne op het Lindewoud (vondst met `vereist = "g:hanne-3"`).
  Zijmissies: *Zilverschelpen* (Hendrik: vijf `schelp:` langs het strand, op het wrak, op een rotsje in de branding en
  bij het dijkhuisje → `harnas1`, het maliënhemd) en *De vlieger* (Pim: de duinbaan halen → `hs:zilverstrand-1`).
- **Hartstukjes**: 1 (Pim), 2 (smokkelgrot, bommen), 3 (Meeuwenrots, klauwhaak). `hs:zilverstrand-4` is voor 2b.
  Het haakpunt `rots` zit aan de zuidrand van de top (1198, G+21, −391), ±21 blokken van het pleintje ervoor: geef de
  klauwhaak (3a) genoeg bereik, of zet halverwege een tweede haakpunt (dan de versie van `zelda-e2a` ophogen).
- **Muziek**: *Zoutkom* (steiger, dorp, strand, dijk; xylofoon en gitaar), *Duinen* (fluit, wind) en *Vuurtoren*
  (kaap, toren, kelder; klokken). Hemel: ochtend bij de steiger, middag in dorp en duinen, avondzon op de kaap.
- **Afwijkingen van het plan, en waarom**: de puzzel voor de bommenzak is een **spiegelpuzzel** (draaien, geen tijd)
  in plaats van drie doelen met de boog: dat hadden we al op het Lindewoud, en spiegels zijn een nieuwe vaardigheid
  die het IJspaleis (3b, «een spiegelzaal») kan hergebruiken. Het gevecht is óp het bordes vóór de deur van de toren,
  niet bovenop (vallen is geen leuk gevecht). Mira's lamp ligt niet in de vuurtoren maar in het nest op de
  Meeuwenrots: zo krijgen de duinen een doel en wordt de bom meteen twee keer gebruikt. Het einde van 2a is het licht
  van de vuurtoren; het baken (`baken2`) is voor 2b.

#### Hoe 2b aanhaakt (sessie `zelda-eiland-2b`)

- Een klasse `ZeldaEiland2b` (`e2b`, deel `zelda-e2b`) met vak **x 1230 … 1360, z −480 … −180**, als tweede deel in de
  regel van het Zilverstrand: `List.of(new ZeldaEiland2a(plugin), new ZeldaEiland2b(plugin))`. Zolang 2b er niet is,
  bouwt 2a daar alleen zee (`ZeldaEiland2a.OOST`); met 2b erbij bouwt 2a dat niet meer, dus 2b moet elke kolom van
  zijn vak zelf zetten. Speelgebied van 2a: x 1062 … 1228 (2b begint op 1229).
- **De dijk** (`ZeldaEiland2a.DIJK_X` = 1229, z −332 … −328): bovenkant op G tot x 1220, daarna G − 2 onder water.
  2b gaat verder vanaf x 1230; eb en vloed kan 2b in `tik()` doen (blokken omzetten met een `Deur`, ook de stukjes van
  2a op x 1221 … 1229: dat is spel, geen bouwen). De lichtbundel van de vuurtoren wijst naar x 1300, z −330: zet de
  tempel daar in de buurt.
- **Bommen** zijn er: `ZeldaHoofdstuk.boem(spel, speler, plek)` wordt aangeroepen als er een bom op dit deel ontploft
  (na de gebarsten muren en de klap voor vijanden): daar kan de ton van Zoutbaard springen. Een muur die je óp het pad
  moet openbreken: `barstOpPad(...)` met `voor` (de plek waar je staat; de test gooit daar een echte bom).
- 2b mag `hs:zilverstrand-4` gebruiken; de zelftest eist hoogstens vier per eiland. Ook `fles3` (niet meer `fles2`:
  die geeft Tijmen nu op het Lindewoud) en `hart2` zijn voor 2b. `wacht()` van 2a vervalt vanzelf (het laatste deel
  telt). Teun heeft al een regel over eb (`teun-licht`).
- **Tijmen** staat al op het plein van 2a met `spel.wegwijzer`: hij noemt vanzelf het eerste doel van 2b dat nog niet
  klaar is. Geef hem op 2b een eigen plek bij de tempel (een tweede `Persoon` «Tijmen» in 2b mag: hij reist met de
  sterren) en een gesprek als het baken brandt («Op de Witte Toppen zien we elkaar weer»). Een boodschap over zee voor
  2b staat hieronder bij «Terugkerende personages».

#### 2b · oost, de tempel (plan)

De dijk naar de Getijdentempel; puzzels met **eb en vloed** (hendels die water laten zakken en stijgen: deuren van
water), gebarsten muren (bommen), een tijdrace onder water door. Kerker met twee kamers en de baas **Kapitein
Zoutbaard**, een verdronken zeerover die door Morgals nevel ronddoolt: hij gooit drietanden; gooi een bom in zijn ton,
dan staat hij versuft. Bevrijd = hij bedankt je en vergaat tot zeeschuim. `hart2`, het **Getijdenbaken** ontsteekt
(`baken2`), Wiske wordt feller. Het Witte Toppen gaat open. Muziek: *Getijdentempel* (druppels, ijzer), baas
*Zoutbaard*.

### 3 · De Witte Toppen

Sneeuw, sparren, een bevroren meer, het bergdorp **IJshoeve**, en hoog op de berg het **IJspaleis** van de oude
bergwachters.

- **3a (dal en dorp)**: steiger in een fjord, dorp met **Saar en Sven** (herders, zijmissie *Zoekgeraakte geiten*),
  **Grootvader Ys** (verhalen, uitleg over het paleis), de **smid van de wachters**. Het **sneeuwvosje**: in een
  sneeuwstorm hoor je gepiep; pootafdrukken (deeltjes) leiden naar een kuil onder een spar waar twee wolven het
  vosje opjagen (het enige gevecht, kort). Daarna kies je zijn naam (motor: menu) en loopt hij mee; Wiske legt uit dat
  hij verstopte dingen ruikt. Voorwerp: de **klauwhaak** (bij de bergwachters, na een klim met haakpunten als les).
- **3b (berg en paleis)**: het bergpad met haakpunten, het **IJspaleis**: glijpuzzels op ijs (je glijdt tot je
  ergens tegenaan komt), bevroren hendels, een spiegelzaal. Baas **Grimmel**, een reuzenijsbeer in de ban van de
  nevel: hij stormt op je af; trek je weg met de klauwhaak, laat hem tegen de ijspilaren lopen. Bevrijd = hij sjokt
  weg naar zijn holen. `hart3`, `harnas2` (wachtersharnas), **Vorstbaken** (`baken3`).

Muziek: *IJshoeve* (klokjes), *Sneeuwstorm* (zacht, didgeridoo), *IJspaleis* (ijzeren xylofoon), baas *Grimmel*.

### 4 · De Gloedberg

Zwart zand, as, lava die langzaam stroomt, een mijnwerkersdorp **Sintelgat** tegen de vulkaan, en binnenin de oude
**Smidse** van de eerste smeden.

- **4a (dorp en mijnen)**: steiger in een baai van zwart zand, de mijnwerkers **Dorus en Dina** (zijmissie
  *De verdwaalde lorrie*), **Meester Gerrit** van het smidsgilde (het gloedharnas, dat tegen de hitte beschermt: zonder
  word je in de diepe mijn langzaam warm), **Fenna** de koerierster (tijdrace over de mijnsporen). Voorwerp: de
  **vuurstaf** (steekt vuurschalen op afstand aan, smelt ijs: terug naar de Witte Toppen voor een hartstukje).
- **4b (de Smidse)**: lava die stijgt en zakt (deuren van lava), kettingbruggen, vuurpuzzels op afstand. Baas **Asgrim
  de Gloedwachter**, een vlammenwezen dat boven de lava zweeft: doof zijn schilden met water uit de koelbakken (bommen
  in de bakken), dan pijlen. `hart4`, **Gloedbaken** (`baken4`). De Nevelring breekt: Nachtrots gaat open.

Muziek: *Sintelgat* (banjo, aambeeld), *Mijnen* (bas, druppels), *Smidse* (dreun), baas *Asgrim*.

### 5 · Nachtrots

Zwart steen, onweer in de verte, Morgals soldaten. Geen dorp: één verstopte bondgenoot.

- **5a (de muren)**: aan land bij de oude visserskreek; **Wolf** (een gevangen wachter van Zelda) helpt je binnen te
  komen. Sluipen langs de wachtposten (gezien worden = het hek gaat dicht en je begint bij het zielvuur, geen gevecht),
  de kerkers met gevangenen uit alle eilanden (Mira's lamp, Saars geiten: kleine verrassingen). Voorwerp: de
  **lichtpijlen** (de boog krijgt licht: nevel verdwijnt waar je hem raakt). Het **lichtharnas** van Zelda's wachters.
  Eén gevecht: de binnenplaats.
- **5b (de toren en Morgal)**: de toren in nevel (lichtpijlen maken bruggen zichtbaar), de troonzaal. **Morgal** in drie
  fases: (1) hij roept nevelgeesten, (2) hij gooit duistere bollen die je met je zwaard terugslaat, (3) lichtpijlen in
  zijn nevelhart. Zelda bevrijd; terug naar huis met de Zilvermeeuw; **feest op de open plek** van het Lindewoud met
  iedereen die je hielp. Vlag `zelda`; daarna «Story of Zelda · voltooid» en alles blijft open.

Muziek: *Nachtrots* (dreigend, laag), *Toren* (spanning), baas *Morgal* (drie delen), *Thuis* in feestversie.

## Terugkerende personages

Jelle (27-10): eilanden zo ontwerpen dat je er later naar terug moet of wilt, omdat je bij een bekend karakter iets
moet regelen; een vaste wegwijzer bij wie je steeds terugkomt. Zo knopen we de eilanden aan elkaar:

| wie | wat | Lindewoud | Zilverstrand | later |
|---|---|---|---|---|
| **Tijmen**, reizende tovenaar (wandering trader, blauwe mantel) | de **wegwijzer**: praten = je volgende opdracht (`spel.wegwijzer(p, "Tijmen")`), Wiske vliegt die kant op; hij geeft wat je nodig hebt om verder te kunnen | sterrentoren in Lindedorp: flesje `fles2`, het **lichtwoord** (doornen) | plein van Zoutkom (2a): «ik was er al» | op elk eiland bij de hub of steiger (IJshoeve, Sintelgat, de visserskreek op Nachtrots), met één eigen gave per eiland; Wiske verbaast zich er elke keer over («Hoe komt hij hier nou weer?»); op Nachtrots helpt hij Wolf |
| **Brecht**, visser en kapitein | varen (`spel.varen`), op elke steiger | pier | steiger; zijn zus Mira | op elke steiger; na Morgal vaart hij iedereen naar huis |
| **Hanne**, kruidenvrouw | brouwt met wat je van andere eilanden meebrengt | *Moeraskruid* → `fles1` | *Zeekraal* (aan de dijk) → `sterkwater` | *Sneeuwbloem* van de Witte Toppen (een brouwsel tegen de kou in de mijn, of nog sterker bronwater) |
| **Kees**, houthakker | heeft altijd iets nodig van ver weg | *De verloren bijl* | — | 4a: ijzerhout of een nieuw bijlblad van de smeden van de Gloedberg → iets voor de Kloof (een brug?) |
| **Lotje**, kind op de pier | telt meeuwen, wil weten hoe het elders is | praatje | (vraagt naar de meeuwen) | kan een kleine boodschap krijgen (een veer van de Meeuwenrots, een sneeuwbal die niet smelt) |
| **Wiske**, de fee | hint, uitleg, wijst | overal | overal | wordt feller bij elk baken |

**Bouwregel voor elk volgend eiland:** zet Tijmen bij de hub (met `wegwijzer` als laatste gesprek), en maak minstens
één **boodschap over zee**: een zijmissie van een bekende op een eerder eiland die pas begint als je op het nieuwe
eiland was (`s.heeft("e:<eiland>")`), met de vondst op het nieuwe eiland (`vereist = "g:<gesprek>"`) en de beloning
bij de bekende. `/hint` noemt zo'n missie ook op het andere eiland, en `kitpvp zelda test … alles` vaart aan het eind
twee rondes heen en weer om ze af te maken.

## De motor (gebouwd in deze sessie)

Alles zit in `plugins/kitpvp` en werkt al op het Lindewoud; de eilanden haken erop aan.

| onderdeel | klasse | wat |
|---|---|---|
| eilanden | `ZeldaEiland` | id, naam, icoon, delen (hoofdstukken), aanleg-rustplaats, `openNa`-vlag, `klaar`-vlag, overtochtregels |
| overtocht | `ZeldaZee`, `ZeldaSpel.vaar` | bouwdeel `zelda-zee`: open zee met de Zilvermeeuw; 9 s aan dek (16 s de eerste keer met overtochtregels), dan bij de aanleg |
| voortgang | `ZeldaVoortgang` | `laatste` rustplaats (daar kom je binnen), `bezocht` (kaart), vlaggen, `missies`, `tellers`, `dier`; oude standen blijven werken |
| regels | `ZeldaRegels` | harten, hartstukjes, harnas, flesjes, welk doel, «opnieuw vanaf hier» (unit-tests) |
| muziek | `ZeldaMuziek` | drie lagen: achtergrond (altijd, wisselt op een maatstreep), lus (gevecht/baas), deuntjes; nieuwe liedjes *Thuis, Woud, Tempel, Veen, Haven, Zee, Bron, Hint, Opdracht* |
| Wiske | `ZeldaMetgezel` | kleine allay, alleen voor jou zichtbaar, vliegt naast je schouder, komt voor je zweven als ze praat, wijst bij een tweede hint |
| huisdier | `ZeldaDier` | vlag `dier:<soort>` (vos, sneeuwvos, kat, konijn), naam kiezen, loopt mee (eigen pad, geen eigen wil), snuffelt verborgen vondsten op |
| bronnen | `ZeldaHoofdstuk.Bron` | feeënkring (licht, geen blokken): geneest snel, vult flesjes; ook zielvuren vullen flesjes |
| flesjes | `ZeldaSpel.Flesje` | vak 3, rechtsklik = vijf harten; `fles1` … `fles4` |
| harnas | `ZeldaSpel.harnas` | vlaggen `harnas1` … `harnas4`, zichtbaar, met versiering vanaf trap 3 |
| personen | `ZeldaHoofdstuk.Persoon` + `Gesprek` | gesprek per voorwaarde (eerste dat klopt), `Actie` daarna (missie, beloning); glinstert zacht als er iets nieuws is; elk wezen kan (dorpeling met streek, of `soort`) |
| zijmissies | `ZeldaHoofdstuk.Missie`, `spel.missie`, `spel.beloon` | stappen 1 … n, `KLAAR`; staan op de kaart onder «Opdrachten» en in `/hint` |
| hint | `spel.hint`, `/hint`, klik op Wiske | doelen in volgorde; tweede keer binnen 2 min = `verder` + Wiske wijst; geen doel = zijmissie, «verder varen», iemand met nieuws, iets dat je met een nieuw voorwerp kunt halen (hier of op een ander eiland: «vaar er eens heen»), `wacht()`; zelf aanbieden na 6 min zonder voortgang |
| uitleg | `Verhaal.uitleg`, kaart «Uitleg: aan/uit» | uitleg overslaan; `Kamer.tips` na doodgaan |
| opnieuw | kaart: rechtsklik op een zielvuur | vergeet `v:`/`r:` na die rustplaats, reist erheen |
| kaart | `spel.kaart` | zielvuren van dit eiland, eilanden (varen), opdrachten, harten, harnas, flesjes, uitleg, hub |
| voorwerpen | `ZeldaVoorwerp` | haak voor bommen, klauwhaak, vuurstaf, lichtpijlen: vlag, vak, item, rechtsklik, projectiel raakt |
| gewonnen kamers | vlag `k:<kamer>` | voor missies en doelen («ben je hier al geweest»); **vrije doorgang** als iedereen binnen hem al won |
| bel | `Kamer.bel`, `Kamer.extra`, `spel.luid` | rechtsklik op de bel in een kamer = nog eens vechten, met de extra golven |
| gebarsten muur | `ZeldaHoofdstuk.Barst`, `spel.breek`, `spel.barstBij` | dicht (`BARST`) tot een bom hem openbreekt; wie dat deed (`b:<id>`) vindt hem open, na een minuut leeg weer dicht |
| haakpunt | `ZeldaHoofdstuk.Haakpunt`, `spel.haakpunt(Block)` | blok (`HAAKPUNT`) + landingsplek voor de klauwhaak (eiland 3 bouwt het trekken) |
| later te halen | `Vondst.voorwerp` | te zien, pas te pakken met dat voorwerp (de test controleert dat hij blijft liggen) |
| na een vondst | `Vondst.daarna` | een `Actie` (bv. missiestap verder als alle kruiden er zijn) |
| eenmalig gesprek | `Persoon.eenmalig` | een gesprek dat je één keer hoort (welkom terug, een geheimpje); `Persoon.kind` = een kind |
| varen met Brecht | `spel.varen` | menu «Waarheen?» met de open eilanden, of «nog even geduld» |
| hartstukjes per eiland | `ZeldaHoofdstuk.stukjes`, `stukje(vlag)` | de kaart toont «Het Lindewoud: 2 van 4 gevonden» |
| oude standen | `ZeldaHoofdstuk.aanvullen(Stand)` | bij binnenkomen: vlaggen van wat je zeker al deed (bv. `k:`) |
| bommen (eiland 2) | `ZeldaSpel.Bommen`, `spel.ontplof` | vak 4, rechtsklik = gooien (een tel ertussen, raken nooit op); sist even, dan knal: gebarsten muur open, vijanden een klap, `boem()` van het deel |
| spiegels | `ZeldaHoofdstuk.Spiegels`, `spel.draai`, `ZeldaRegels.straal` | licht uit een bron, draaispiegels (rechtsklik = kwartslag), het doel raken = deur open; geen tijd; `r:<id>`; de zelftest volgt het licht met de oplossing |
| muur op het pad | `barstOpPad`, `Barst.voor` | een gebarsten muur waar je door moet (de test gooit er een echte bom tegen) |
| haken voor een deel | `tik(spel, spelers, nu)`, `boem(spel, p, plek)`, `wacht()` | elke seconde iets laten bewegen (vlieger, lichtbundel, eb en vloed); een bom op dit deel; wat Wiske zegt als het eiland nog niet af is |
| na een uitdaging | `Uitdaging.daarna` | een `Actie` na het halen (missiestap) |
| beheer | `kitpvp zelda vaar|vlag|hint <speler> …`, `kitpvp zelda test <speler> motor` | zie MODI.md |
| **open eiland** (versie 4 van het Lindewoud) | | |
| poort voor wie iets heeft | `ZeldaHoofdstuk.Poort`, `poort(…)` | opent als er iemand in het gebied staat voor wie `mag` klopt (bv. het lichtwoord), voor iedereen; na een minuut leeg weer dicht; telt als open in `zelda lopen` |
| oefenplek | `Kamer.alleenBel` | deuren open in rust; pas als je de bel luidt wordt er gevochten (eerst de gewone golven, daarna met de extra); telt niet als verplichte kamer |
| doelen in elke volgorde | `doel(groep, id, …)`, `ZeldaRegels.doel(doelen, s, x, z)` | doelen met dezelfde groep: de hint (en de wegwijzer) noemt het dichtstbijzijnde dat nog niet klaar is |
| wegwijzer | `spel.wegwijzer(p, naam)` | een persoon zegt je volgende opdracht (de tweede hint, in zijn stem) en Wiske vliegt die kant op; anders een eiland waar je nog niet was, een zijmissie (ook van een ander eiland), iets voor later, `wacht()` |
| praten op het pad | `praten(persoon)`, `ZeldaHoofdstuk.Praten` | voor de test: hier praat je met iemand (Tijmen, Bram) tot hij niets nieuws meer heeft |
| vaste rustnummers | `rust(index, Rust)` | een rustplaats houdt zijn nummer (de opslag) maar staat op zijn plek in het pad; de zelftest eist 0 … n−1 |
| op adem komen | `ZeldaSpel.BIJKOMEN_NA/ELK` | buiten een gevecht: na 6 s zonder klap elke 2 s een half hart |
| hemel naar voortgang | `hemel(Location, Stand)` | een open eiland laat de dag opschuiven met wat je deed, niet met waar je staat |
| verhaal met voorwaarde | `Verhaal.als` | alleen als dit klopt (bv. de doornen-uitleg alleen zonder lichtwoord) |
| lichtere baas | `ZeldaBaas.leven`, `levenPerSpeler`, `oproepen` | leven en hulp per baas |
| sterk bronwater | vlag `sterkwater`, `ZeldaRegels.flesHp` | een flesje geneest 7 harten in plaats van 5 |
| genezen bij een persoon | `spel.genees(p)` | helemaal beter (de bakker) |

### Hoe een eilandsessie aanhaakt

1. Per half eiland een klasse `ZeldaEiland2a extends ZeldaHoofdstuk` (`id()` `e2a`, `deelId()` `zelda-e2a`, eigen
   `versie()`), in zijn vak (tabel hierboven). In `bouw`: `leeg(b.w)`, `speelgebied`, wereld (alleen als `b.plaats`,
   met chunk-tickets zoals hoofdstuk 1), dan de onderdelen in de volgorde van het pad: `rust`, `bron`, `kamer`,
   `uitdaging`, `reeks`, `vondst`, `verhaal`, `persoon(...).gesprek(...)`, `missie`, en de `doel`en.
   `muziek(Location)` en `plekNaam(Location)` per streek, `hemel(Location)` voor de tijd van de dag.
   Personen: een **Brecht** bij je steiger met `gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.varen(p))`
   (dan kun je terugvaren), en **Tijmen** bij de hub (`soort = EntityType.WANDERING_TRADER`, als laatste gesprek
   `(spel, p, s) -> spel.wegwijzer(p, "Tijmen")`). Gewone kamers: een `bel` en een `extra` golf; een gevecht dat niet
   hoeft: `alleenBel`. Plekken voor later: `Barst`/`Haakpunt` en vondsten met `voorwerp`. Hartstukjes als beloning:
   `stukje(vlag)`. Een open eiland: doelen die in elke volgorde mogen een `groep`, een `Poort` waar een persoon iets
   voor moet geven, `praten(persoon)` op het pad waar je met hem moet praten, `rust(index, …)` als je rustplaatsen
   niet in de volgorde van hun nummer op het pad liggen.
2. Eén regel in `Zelda.EILANDEN`:
   `new ZeldaEiland("zilverstrand", "Het Zilverstrand", Material.SAND, List.of(new ZeldaEiland2a(plugin), new ZeldaEiland2b(plugin)), "steiger", "h1", "baken2", List.of(r("Brecht", "…"), r("Wiske", "…")))`.
   Het eiland verschijnt vanzelf op de kaart van wie `h1` heeft, de overtocht werkt, de zelftest controleert het.
3. Een nieuw voorwerp: een `ZeldaVoorwerp` in `spel.voorwerpen` (in `Zelda`), met een vlag die een vondst of beloning
   zet.
4. Nieuwe vijanden: `ZeldaHoofdstuk.Soort` + `ZeldaSpel.spawn`; een baas: een `ZeldaBaas` (of subklasse).
5. Nieuwe liedjes in `ZeldaMuziek` (en in `alle()`); de unit-test controleert bereik, lengte en volume.
6. Test: `kitpvp zelftest`, `kitpvp zelda test <speler> alles` (loopt alle eilanden af in de volgorde van
   `EILANDEN`: varen, elk deel in de volgorde van het pad, het einde van elk deel, Brecht, de zijmissies van het eiland
   door te praten met wie iets nieuws heeft; vondsten met `voorwerp` moeten blijven liggen), `kitpvp zelda test
   <speler> motor`, en `kitpvp kaart` voor plaatjes. Een nieuw eiland komt dus vanzelf in de route, als het open gaat
   met de vlag van het eiland ervoor.
7. Bommen (gebouwd in 2a): `spel.ontplof` breekt de gebarsten muur bij de knal open (`spel.barstBij`, `spel.breek`) en
   roept `boem()` van het deel aan. Klauwhaak (3a): trek naar `spel.haakpunt(blok)` en zet de speler op `landing()`;
   er zijn er twee: boven de richel in de Kloof (`hs:lindewoud-3`) en boven op de Meeuwenrots (`hs:zilverstrand-3`).
8. Is alles te belopen? `kitpvp zelda lopen <eiland>` (ook in `test … alles`, voor elk eiland, ook het Lindewoud): de
   test teleporteert, dus deze controle volgt vanaf de steiger elke plek waar een speler kan staan en noemt wat je
   zonder teleporteren niet haalt. Deuren, poorten en bruggen tellen als open (een brug als planken).
9. De test vaart van eiland naar eiland met een klik in het menu van Brecht, haalt na elk eiland wat met de voorwerpen
   van nu te halen is (een echte bom tegen de muur ervoor, dan pakken), en vaart aan het eind via de kaart terug naar
   eerdere eilanden om daar hetzelfde te doen (het Lindewoud: `hs:lindewoud-4` achter de tempelmuur). Daarna twee
   rondes langs alle eilanden voor de boodschappen over zee (Hanne en de zeekraal), en pas dan moeten alle zijmissies
   klaar zijn.

## De bouwsessies (in volgorde)

Elke sessie: één (half) eiland, de regels hierboven, eigen code alleen in `plugins/kitpvp`, testen volgens de
lichte-testregel, `MODI.md`/`BOT.md` bijwerken, pluginversie omhoog, niet deployen.

1. **`zelda-eiland-1`** (gebouwd, kitpvp 2.4) — Het Lindewoud als eiland: rustiger begin (lichtere eerste kamer en
   kade), Hanne (*Moeraskruid* → `fles1`), Kees (*De verloren bijl* → hartstukje), Lotje, Brecht met gesprekken en de
   overstap naar eiland 2 (alleen als het bestaat), vier hartstukjes (twee nu te halen). **Klaar als** het hoofdstuk
   van begin tot eind speelt met hoogstens twee verplichte kamers vóór de baas, er twee zijmissies werken, `kitpvp
   zelftest` OK is en `kitpvp zelda test <speler> alles` en `motor` OK zijn, en bestaande voortgang blijft werken.
1b. **`zelda-lindewoud-v2`** (gebouwd, kitpvp 2.6) — Het Lindewoud opnieuw, als **open eiland**: Lindedorp in het
   midden met Tijmen (de wegwijzer), drie wegen in elke volgorde, rondjes, de doornen en het lichtwoord, alleen
   Grauwmoer (lichter) en de kade verplicht, oefenplekken met de bel, genezen overal, Hanne's boodschap over zee; motor:
   poort, oefenplek, doelgroepen, wegwijzer, op adem komen. **Klaar als** er geen recht pad meer is, gevechten licht
   zijn, je bij elke kamer kunt genezen, een stand van 2.5.0 zonder verlies werkt, en zelftest, `test … alles`,
   `motor` en `zelda lopen lindewoud` OK zijn.
2. **`zelda-eiland-2a`** (gebouwd, kitpvp 2.5) — Zilverstrand west: steiger, Zoutkom, vuurtoren, duinen,
   Mira/Hendrik/Pim/Teun, de bommenzak (voorwerp + spiegelpuzzel, vlag `bommen`), maliënhemd, één gevecht, eiland in
   `Zelda.EILANDEN` (open na `h1`). **Klaar als** je van het Lindewoud kunt varen (Brecht of kaart), alles op 2a te
   doen is, bommen gebarsten muren openen (ook die in de tempel van het Lindewoud), en zelftest en test OK zijn.
3. **`zelda-eiland-2b`** — De Getijdentempel: eb-en-vloedpuzzels, kerker, Kapitein Zoutbaard, `hart2`, `fles3`,
   Getijdenbaken (`baken2`), Tijmen bij de tempel, een boodschap over zee. **Klaar als** de kerker te halen is met alleen wat 2a geeft, de baas te verslaan is, `baken2` het Witte
   Toppen opent, en zelftest en test OK zijn.
4. **`zelda-eiland-3a`** — Witte Toppen dal en dorp: IJshoeve, personen, het sneeuwvosje (motor `ZeldaDier`, naam
   kiezen), de klauwhaak (voorwerp). **Klaar als** het vosje meeloopt en snuffelt, de klauwhaak werkt op haakpunten
   (ook het oude punt op het Lindewoud), en zelftest en test OK zijn.
5. **`zelda-eiland-3b`** — Het IJspaleis: glijpuzzels, Grimmel, `hart3`, wachtersharnas, Vorstbaken (`baken3`).
   **Klaar als** het paleis met klauwhaak en bommen te halen is, `baken3` de Gloedberg opent, zelftest en test OK.
6. **`zelda-eiland-4a`** — Gloedberg: Sintelgat, mijnen met hitte, personen, de vuurstaf, het gloedharnas. **Klaar als**
   de vuurstaf vuur op afstand aansteekt en ijs smelt (ook op de Witte Toppen), zelftest en test OK.
7. **`zelda-eiland-4b`** — De Smidse: lavapuzzels, Asgrim, `hart4`, Gloedbaken (`baken4`), de Nevelring breekt.
   **Klaar als** de Smidse te halen is met alle voorwerpen tot nu toe, `baken4` Nachtrots opent, zelftest en test OK.
8. **`zelda-eiland-5a`** — Nachtrots, de muren: Wolf, sluipen, de kerkers, lichtpijlen, lichtharnas, één gevecht.
   **Klaar als** je van de kreek tot de torenpoort komt, lichtpijlen nevel weghalen, zelftest en test OK.
9. **`zelda-eiland-5b`** — De toren en Morgal (drie fases), Zelda bevrijd, de terugvaart en het feest op het
   Lindewoud, vlag `zelda`. **Klaar als** het hele spel van Thuis tot het feest uit te spelen is (`kitpvp zelda test
   <speler> alles` over alle eilanden), zelftest OK.
10. **`zelda-afronding`** — Speel alles na: balans (te veel gevechten? te moeilijk?), alle «later-terug»-plekken
    bereikbaar, twintig hartstukjes, muziek overal, teksten kort, kaartplaatjes. **Klaar als** een volledige
    testronde zonder fouten loopt en de lijst met losse eindjes leeg is.
