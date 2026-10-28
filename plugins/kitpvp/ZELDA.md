# Story of Zelda 2.0 — De Glazen Zee

Ontwerpdocument (oktober 2026). Hoofdstuk 1 «Het Lindewoud» staat live en viel goed ("erg leuk, verrast hoe goed het
is"). Zelda 2.0 maakt er een groter avontuur van: **vijf eilanden** in de Glazen Zee, elk een hub met meerdere dingen
erop, verbonden door het schip de Zilvermeeuw. Dit document is het plan voor de bouwsessies: verhaal, eilanden,
mechanics en de verdeling in sessies. De gedeelde motor is gebouwd (zie «De motor»), eiland 1 ook (sessie
`zelda-eiland-1`, kitpvp 2.4; en opnieuw als **open eiland** in sessie `zelda-lindewoud-v2`, kitpvp 2.6) en heel eiland 2
(de westhelft in sessie `zelda-eiland-2a`, kitpvp 2.5; de Getijdentempel in sessie `zelda-eiland-2b`, kitpvp 2.7), heel
eiland 3 (het dal in sessie `zelda-eiland-3a`, kitpvp 2.8; het IJspaleis in sessie `zelda-eiland-3b`, kitpvp 2.9) en heel
eiland 4 (Sintelgat en de mijnen in sessie `zelda-eiland-4a`, kitpvp 2.10; de Smidse in sessie `zelda-eiland-4b`, kitpvp
2.11: de Nevelring is gebroken, Nachtrots is aangekondigd); de
andere eilanden bouwen latere sessies, één per (half) eiland, in de volgorde onderaan. **Terugkerende personages** (Tijmen de wegwijzer, Brecht, Hanne, …) knopen de eilanden aan
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
| later je eigen huisdier | geen vrij huisdier (Jelle: afleidend; `ZeldaDier.AAN` blijft uit), wel het **sneeuwvosje** uit het verhaal: je redt hem op de Witte Toppen en kiest zijn naam; hij loopt rustig mee, vecht niet, zegt niets, **snuffelt verstopte dingen op** en graaft (graafplekken) | motor (`ZeldaDier`) + eiland 3a |
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
| 3 De Witte Toppen | `e3a` · `zelda-e3a`, `e3b` · `zelda-e3b` | 1400 … 1720 · −480 … −180 (3a tot x 1579, 3b vanaf 1580) | rust `steiger` (e3a) | `baken2` | `baken3` |
| 4 De Gloedberg | `e4a` · `zelda-e4a`, `e4b` · `zelda-e4b` | 1060 … 1400 · 180 … 480 (4a tot x 1259, 4b vanaf 1260) | rust `baai` (e4a) | `baken3` | `baken4` |
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
| 2 Zilverstrand | **bommen** (vak 4, vlag `bommen`): gooien, gebarsten muren, baas verdoven | `harnas1` maliënhemd (smid Hendrik) | `hart2` (Zoutbaard) | `fles3` (Lotje, boodschap over zee) | `maanparel` (Tijmen: het getij), `ankersleutel` (Teun) |
| 3 Witte Toppen | **klauwhaak** (vak 5, vlag `klauwhaak`): naar een haakpunt trekken (gebouwd in 3a) | `harnas2` wachtersharnas (het bevroren harnas uit het IJspaleis, Hagen warmt het) | `hart3` (Grimmel) | `fles4` (Teun, de brief van Ys), `fles5` (Hanne, de sneeuwbloem) | **sneeuwvos** (`dier:sneeuwvos`), `lantaarn` (Tijmen, 3a), `zonnesteen` (Tijmen, 3b) |
| 4 Gloedberg | **vuurstaf** (vak 6, vlag `vuurstaf`): een vlam op afstand (vuurschalen, lampen), betoverd ijs smelten | `harnas3` gloedharnas (Okker smeedt het van gloedijzer, met zijn esse weer aan; tegen de hitte) | `hart4` (Asgrim, 4b) | `fles6` (Hagen, de gloedkool), `fles7` (Kees, het bijlblad, 4b) | `koelsteen` (Tijmen), `gloedijzer`, `gloedschild` (Okker smeedt de schildplaat uit de Smidse: vuur ketst erop af, 4b), drie `vonk-…` (4b) |
| 5 Nachtrots | **lichtpijlen** (boog, vak 2, vlag `lichtpijlen`): nevel en Morgal | `harnas4` lichtharnas (Zelda's wachters) | — | — | het einde |

De vlag van een voorwerp is vast: de vondsten op eerdere eilanden die erop wachten (`voorwerp = "bommen"`, …) gebruiken
hem al. Flesjes: de nummers zijn alleen namen (`ZeldaRegels.flessen` telt `fles1` … `fles8`); het Lindewoud geeft er
sinds versie 4 twee (Tijmen meteen `fles2`, zodat je vanaf het begin kunt genezen), dus 2b geeft `fles3` en de Witte
Toppen `fles4`. Sinds 3b is er een vijfde: Hanne brouwt het van een sneeuwbloem uit de IJstuin (`fles5`, een
boodschap over zee; Jelle wil overal kunnen genezen). Sinds 4a een zesde: de veldfles van Hagen, voor een gloedkool van
Okker (`fles6`). Sinds 4b een zevende: de oude veldfles van Kees op het Lindewoud, voor een bijlblad uit de Smidse
(`fles7`).

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
| 213, 62 | **de Heksenkring**: baas **Grauwmoer** (60 leven + 30 per extra speler, één mosschutter bij twee derde en bij een derde); de hartcontainer ligt net buiten de oostpoort, aan het kringpad, en is pas te zien en te pakken als je haar versloeg (`vereist = "k:heksenkring"`) | kamer + baas, vondst `hart1` |
| 225 … 303 | **het kringpad** van de Heksenkring naar de havenweg | — |
| 319 … 381, 0 (oost) | **de haven**: de kade (twee soldaten), de pier met **Brecht** en **Lotje**, de Zilvermeeuw; boven zee hangt Grauwmoers **nevel** (rook, alleen voor wie haar nog niet versloeg) | rust 5 `haven`, kamer `kade`, verhaal: einde (`h1`, vereist `hart1`) |

- **Hoofdlijn** (doelen voor `/hint` en Tijmen): naar het dorp en praat met Tijmen → **in elke volgorde** het schild,
  het zwaard en de boog (doelgroep `drie`: de hint noemt wat het dichtst bij is) → terug naar Tijmen (het lichtwoord) →
  de doelen (de brug) → de doornen → Grauwmoer → het hart van licht (`hart1`) → de kade → Brecht. Wie de haven eerder
  haalt (langs de Kloof), hoort van Brecht dat er niemand vaart zolang Grauwmoers nevel er hangt. Het einde (`h1`)
  vraagt `hart1` én de gewonnen kade (`k:kade`): wie om de kademuur heen zwemt, moet toch eerst de soldaten wegjagen.
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
  versloeg, hoort Tijmen anders (`tijmen-oud`: alleen het flesje) en kan door de doornen; wie zwaard, schild en boog
  al heeft (oude stand, of eerst rondgelopen), hoort `tijmen-ver` (geen «je hebt een zwaard nodig») en daarna meteen
  het lichtwoord. **Opnieuw vanaf hier** (rechtsklik op een zielvuur) vergeet op een open eiland alleen het stuk tot
  de volgende rustplaats in de volgorde (`ZeldaHoofdstuk.open()`), niet het hele eiland.
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
  Het haakpunt `rots` steekt aan de zuidrand van de top een blok uit over de rand (1198, G+21, −390; `zelda-e2a`
  versie 2, sessie 3a: op de rand zelf was het van onderaf niet te raken), ±21 blokken van het pleintje ervoor.
- **Muziek**: *Zoutkom* (steiger, dorp, strand, dijk; xylofoon en gitaar), *Duinen* (fluit, wind) en *Vuurtoren*
  (kaap, toren, kelder; klokken). Hemel: ochtend bij de steiger, middag in dorp en duinen, avondzon op de kaap.
- **Afwijkingen van het plan, en waarom**: de puzzel voor de bommenzak is een **spiegelpuzzel** (draaien, geen tijd)
  in plaats van drie doelen met de boog: dat hadden we al op het Lindewoud, en spiegels zijn een nieuwe vaardigheid
  die het IJspaleis (3b, «een spiegelzaal») kan hergebruiken. Het gevecht is óp het bordes vóór de deur van de toren,
  niet bovenop (vallen is geen leuk gevecht). Mira's lamp ligt niet in de vuurtoren maar in het nest op de
  Meeuwenrots: zo krijgen de duinen een doel en wordt de bom meteen twee keer gebruikt. Het einde van 2a is het licht
  van de vuurtoren; het baken (`baken2`) is voor 2b.

#### 2b · oost, de Getijdentempel (gebouwd: sessie `zelda-eiland-2b`, deel `zelda-e2b`, klasse `ZeldaEiland2b`)

Een half eiland met een eigen hub. Vak **x 1230 … 1360, z −480 … −180** (2b zet elke kolom zelf; 2a bouwt daar geen zee
meer). Coördinaten: het plein (1268, −330), de Getijdenzaal (midden 1300, −330), de Draaikolk (1332, −330), de toren met
het baken (1348, −330), het wrak (1316, −404), de poelen (1288 … 1322, −284).

| plek | wat | soort |
|---|---|---|
| x 1230 … 1256, z −332 … −328 | **de dijk** verder vanaf Zoutkom: eerst nog onder water (waden), dan net erboven, met palen en lantaarns | verhaal |
| 1268, −330 | **het Getijdenplein** (de hub): een oud plein van de getijdenwachters, een getijdenklok in het midden, gebroken zuilen, bordjes (Tempel, Het wrak, De poelen, Zoutkom); **Tijmen** onder zijn luifel | rust 0 `plein`, persoon |
| 1296 … 1336, −420 … −362 | **het wad** (platen en prielen, overal te belopen) met **het wrak van de Kraai**, Zoutbaards schip: **Lorre** de papegaai in het ruim, het logboek in de kajuit | verhaal, persoon |
| 1272 … 1332, −302 … −272 | **de getijdenpoelen**: rotsen met poeltjes; **Roos** de duikster en **de parelduik** (startplaat, hekje, een gang onder de rotsen door van de ene poel naar de andere, 25 s); de zingende schelp voor Lotje | uitdaging `parelduik`, persoon, vondst |
| 1284, −330 | de portiek voor de tempel | rust 1 `tempelpoort` |
| 1288 … 1312, −342 … −318 | **de Getijdenzaal**: een galerij (west, noord, zuid) rond een bak van zes diep; in rust **droog** (eb: Morgals nevel); een trap naar beneden; de hendel op de galerij (werkt pas als het raderwerk draait) | getij `getij`, bron `bron-zaal` |
| 1299 … 1301, −339 | **de sluis** (een rooster onderin de bak): open bij eb | getij (sluis) |
| 1291 … 1303, −357 … −348 | **de Spoelkamer** onder het terras: twee verdronken matrozen en een harpoenier (de bel = nog eens, met vier) — het enige gevecht | kamer `spoelkamer` |
| 1308, −354 … −352 | **de pijpgang** met een gebarsten muur | barst `pijpgang` (op het pad) |
| 1309 … 1319, −358 … −346 | **het raderwerk**: raderen en de grote hendel; met de maanparel stijgt het water (vlag `r:getij`) | getij (hendel), verhaal |
| 1309 … 1311, −342 | **de vloeddeur** in de hoek van de bak: open bij vloed (een wand van water; zwem omhoog) | getij (vloeddeur) |
| 1308 … 1311, −332 … −328 | **de drempel** aan de oostkant van de bak: alleen bij vloed zwemmend te halen | rust 2 `ankerpoort`, verhaal |
| 1312, −330 | **de Ankerpoort**: open voor wie de `ankersleutel` heeft (van Teun in Zoutkom) | poort `ankerpoort` |
| 1332, −330 | **de Draaikolk**: een ronde zaal om een kolk, drie kruittonnen; **Kapitein Zoutbaard**; op het eilandje in de kolk het hart van licht (`vereist = "k:draaikolk"`) | kamer + baas, vondst `hart2` |
| 1348, −330 | **de toren**: een wenteltrap naar het platform (G+24) met **het Getijdenbaken** | rust 3 `baken`, vondst `baken2`, verhaal: einde (`e2b`) |

- **Hoofdlijn** (tien doelen voor `/hint` en Tijmen): naar het plein en praten met Tijmen (de maanparel) → de droge
  Getijdenzaal → door de sluis naar de Spoelkamer → een bom op de pijpgang → in het raderwerk de grote hendel (vloed)
  → door de vloeddeur omhoog en naar de drempel → de Ankerpoort zit op slot: de sleutel heeft **Teun** (terug over de
  dijk naar Zoutkom) → Zoutbaard → het hart → het baken. Wie Lorre al hoorde («Teun! Teun!»), kan de sleutel ook eerder
  halen.
- **Eb en vloed**: de zaal staat in rust droog. De sluis gaat alleen open bij eb (het water drukt hem dicht), de
  vloeddeur alleen bij vloed, en de drempel haal je alleen zwemmend (bij eb is hij zes blokken hoog). De grote hendel
  in het raderwerk werkt met de maanparel; daarna ook de hendel op de galerij (wie terugkomt, hoeft niet weer door de
  kelders). Het water gaat laag voor laag (een halve seconde per laag), met geluid; een minuut niemand in de zaal = weer
  droog. Wie in een sluis of vloeddeur staat die dichtgaat, komt op de bodem van de bak.
- **Zoutbaard**: 60 leven (+30 per extra speler), één matroos bij twee derde en bij een derde. Hij staat telkens bij een
  van zijn drie kruittonnen en gooit drietanden (hoogstens twee harten per stuk); om de acht tellen springt hij naar
  een andere ton (onder een derde om de zes). Zijn jas zit vol zeepokken: een klap doet maar een derde. Een bom bij hem
  of bij zijn ton laat de ton springen en dan staat hij zes tellen **versuft** (gloeit, sterretjes): dan doet een klap
  anderhalf keer zoveel. Een kapotte ton spoelt na vijftien tellen weer aan. Teun en het logboek verklappen het («een
  vonk in zijn ton»), Wiske zegt het bij het begin, en na doodgaan als tip. Verslagen = hij vergaat tot zeeschuim («Zeg
  Teun gedag, jong»).
- **Personen**: Tijmen (plein: de maanparel; na het baken «Op de Witte Toppen zien we elkaar weer»), Roos (*De
  parelduik* → `hs:zilverstrand-4`), Lorre (de papegaai: «Mijn schat! Teun!», na Zoutbaard «Kapitein vrij!»). Op 2a
  erbij: Teun (de ankersleutel, en na Zoutbaard «Dan heeft hij nu rust»), Mira en Brecht (na het baken: naar het
  noorden). Op het Lindewoud: **Lotje** (de boodschap over zee).
- **Boodschap over zee**: wie op het Getijdenplein was, hoort van Lotje (op de pier van het Lindewoud) over zingende
  schelpen; de schelp ligt bij de poelen (`vereist = "g:lotje-schelp"`); terug bij Lotje krijg je `fles3`. Zo is er
  een opdracht terug naar Zoutkom (Teun, op de hoofdlijn) en een naar het Lindewoud (Lotje).
- **Genezen**: vier rustplaatsen (de drempel vlak voor de Ankerpoort: na doodgaan bij Zoutbaard sta je daar), een bron
  op de bodem van de zaal (bij de Spoelkamer), de flesjes, en buiten gevechten op adem komen.
- **Hartstukjes**: `hs:zilverstrand-4` (Roos). Het eiland heeft er daarmee vier: 1 Pim, 2 de smokkelgrot (bommen), 3 de
  Meeuwenrots (klauwhaak), 4 Roos.
- **Muziek**: *Wad* (buiten: fluit en gitaar, in F), *Getijdentempel* (binnen: klokken, ijzeren xylofoon, druppels, in
  fis-klein) en baas *Zoutbaard* (een zeemansjig in d-klein). **Hemel**: avondzon buiten, zonsondergang in de tempel,
  nacht in de Draaikolk; brandt het baken, dan breekt voor jou de ochtend aan. Wie het baken ontstak, ziet het branden
  (een kern van licht en een bundel omhoog, ook vanuit Zoutkom), en Wiske wordt feller (iets groter, meer glans).
- **De Witte Toppen gaan open**: `baken2` is de `klaar`-vlag van het Zilverstrand. Omdat eiland 3 er nog niet is, staat
  het als **aangekondigd** in `Zelda.KOMT` (`ZeldaEiland.Komt`): grijs op de zeekaart en in Brechts menu («nog even
  geduld»), en `/hint` en Tijmen zeggen «Straks varen we naar de Witte Toppen». Sessie 3a heeft het daar weggehaald en
  het echte eiland in `Zelda.EILANDEN` gezet (open na `baken2`); de zelftest meldt het als een eiland op beide plekken
  staat. Sinds 3a leidt ook een ladder met een luik uit het raderwerk naar het terras (het luik gaat alleen van
  binnenuit open): wie daar samen met een ander zonder de Spoelkamer kwam, kan eruit (`zelda-e2b` versie 5).
- **Afwijkingen van het plan, en waarom**: de Getijdentempel heeft **één** verplichte kamer plus de baas (niet twee
  kamers: Jelle wil weinig vechten). Het getij gaat met **hendels** (zoals het plan zei), maar de eerste hendel zit
  diep in de tempel en vraagt de maanparel van Tijmen: zo is Tijmen nodig en is de zaal een echte puzzel (eerst droog
  naar beneden, dan vloed om erover te zwemmen). De **tijdrace onder water** is een zijmissie (Roos) buiten de tempel,
  niet in de kerker. De **sleutel** van de Ankerpoort ligt niet in de kerker maar bij Teun: zo moet je terug naar
  Zoutkom en hoor je wie Zoutbaard was. `fles3` is de beloning van de boodschap over zee (Lotje), niet van de kerker.
  De rovers van 2a zijn **zombies** geworden: een drowned valt overdag niemand op het droge aan (`Drowned.okTarget`;
  op kitpvp staat de tijd stil), dus op het bordes vochten ze niet.

### 3 · De Witte Toppen

Sneeuw, sparren, een bevroren meer, het bergdorp **IJshoeve**, en hoog op de berg het **IJspaleis** van de oude
bergwachters.

#### 3a · het dal en het dorp (gebouwd: sessie `zelda-eiland-3a`, deel `zelda-e3a` versie 1, klasse `ZeldaEiland3a`)

Een half eiland met een eigen hub. Vak **x 1400 … 1579, z −480 … −180** (zee tot de rand; de berg in het oosten loopt tot
x 1579, daar sluit 3b aan). Coördinaten: IJshoeve (1480, −294), het meer (1432, −300, eilandje 1430, −300), de kuil
(1436, −380), de deur in de rotswand (1455, −425), de Wachterspost (1504, −443), de Bergpoort (1551, −294).

| plek | wat | soort |
|---|---|---|
| 1460 … 1476, −236 … −204 | **de fjord**: een steiger op palen, de Zilvermeeuw (boeg naar het noorden), **Brecht** | rust 0 `steiger` (aanleg) |
| 1480, −294 | **IJshoeve** (de hub): een plein met een sneeuwvlok, zielvuur, bron, lantaarns, bordjes (Het meer, Het Sparrenbos, De Uitkijk, De Bergpoort, De fjord); **Tijmen** onder zijn luifel; de geitenhoeve van **Saar en Sven** (stal, weide); de smidse van **Hagen**; een blok betoverd ijs | rust 1 `hoeve`, bron, personen |
| 1412 … 1452, −314 … −286 | **het bevroren meer**: **Grootvader Ys** aan zijn wak, **Noor** en **de ijsbaan** (blauw ijs om het eilandje, 20 s, alleen geldig met de bocht aan de overkant: `Uitdaging.via`); op het eilandje onder de spar een verstopt hartstukje | uitdaging `ijsbaan`, vondst (verborgen) |
| 1404 … 1476, −402 … −342 | **het Sparrenbos**: een sneeuwstorm (veel sneeuw en as om je heen; zonder de lantaarn sta je na een paar stappen weer aan de bosrand); met de lantaarn zie je **pootjes** in de sneeuw; een feeënkring halverwege | rust 2 `bosrand`, bron `bron-bos` |
| 1432 … 1440, −384 … −376 | **de kuil** onder de grote spar: twee **nevelwolven** (de bel = nog eens, met drie) — het enige gevecht; het **sneeuwvosje** zit in een holletje onder de wortels | kamer `kuil`, persoon `Vosje` |
| 1455, −422 | **de rotswand**: het vosje graaft een oude deur van de bergwachters op (graafplek `deur`, vlag `wachtersdeur`) | graafplek, poort `wachtersdeur` |
| 1454 … 1483, −433 … −425 | **de Wachtersgang** onder de rots door, omlaag naar de kloof | — |
| 1484 … 1530, −446 … −430 | **de kloof met de Wachterspost** (hal met torentje): de **klauwhaak**; drie haakpunten met plankieren in de zuidwand (de les) naar de rand | rust 3 `wachterspost`, vondst `klauwhaak`, haakpunten `kloof1` … `kloof3` |
| 1490 … 1549, −429 … −345 | **de Uitkijk**: een vlakte boven de kloof (met een hek langs de rand), een helling terug naar het dorp; geit **Mekker** | — |
| 1541 … 1562, −305 … −288 | **de Bergpoort**: een ingestorte wachterstrap; in de poortboog een haakpunt; het plateau en het begin van het bergpad (een lawine bij x 1575) | haakpunt `bergpoort`, rust 4 `bergpoort`, verhaal: einde (`e3a`) |

- **Hoofdlijn** (acht doelen voor `/hint` en Tijmen): Tijmen (de sterrenlantaarn) → de storm in, de pootjes volgen →
  de kuil (de wolven) → het vosje (naam kiezen) → het vosje graaft de deur op → door de gang naar de Wachterspost (de
  klauwhaak) → met drie haakpunten de kloof uit → de Bergpoort (haakpunt in de boog). `wacht()`: «Het bergpad is nog
  dicht gesneeuwd. Nog even geduld.»
- **Rondjes**: dorp → Sparrenbos → kuil → rotswand → gang → kloof → Uitkijk → dorp; dorp → meer → (langs de oever)
  bosrand → dorp. De kloof is dicht: zonder de opgegraven deur kom je niet in de gang, en zonder klauwhaak niet uit de
  kloof (behalve terug door de gang) en niet op de Bergpoort (`kitpvp zelda lopen witte-toppen dicht wachtersdeur
  zonder klauwhaak`).
- **Personen** (twaalf): Brecht, Tijmen (de **sterrenlantaarn**: «In het Sparrenbos raast een storm»; na de klauwhaak:
  het Vorstbaken), Saar en Sven (*Zoekgeraakte geiten*: Bles bij het meer, Sokje langs de weg naar de fjord, Mekker op
  de Uitkijk; wie gevonden is staat in de stal → `hs:witte-toppen-1`), Grootvader **Ys** (oude bergwachter, vist aan
  zijn wak; *De brief van Ys* voor zijn jeugdvriend Teun), **Noor** (zijn kleindochter, de ijsbaan), **Hagen** de smid
  (smeedde klauwhaken voor de wachters; het ijsblok), het vosje, en drie geiten.
- **Het sneeuwvosje**: na de kuil praat je met hem → `spel.dier(p, "sneeuwvos")` → een klein menu «Hoe heet het
  vosje?» (Vlok, Pluis, Sneeuw, Vonk, Kiek). Daarna loopt hij mee op elk eiland, zit als je stilstaat, gaat in een
  gevecht opzij zitten, snuffelt verborgen vondsten op (ook de holle Oude Eik op het Lindewoud: terugkomen loont) en
  graaft graafplekken op. Het survival-huisdier blijft uit (`kitpvp-stand` `geen`), vrije Zelda-dieren ook.
- **De klauwhaak** (vak 5): rechtsklik; grijpt een haakpunt binnen 24 blokken waar je op mikt (6° ernaast mag: kinderen
  mikken niet precies), de ketting vliegt uit, dan word je erheen getrokken en sta je op de landing. Mis = de haak
  tikt tegen de rots en komt terug (de eerste keer legt Wiske het uit). Werkt ook op de oude haakpunten: de richel in
  de Kloof van het Lindewoud (`hs:lindewoud-3`) en de Meeuwenrots (`hs:zilverstrand-3`; dat haakpunt steekt sinds
  `zelda-e2a` versie 2 een blok uit over de rand, anders was het van onderaf niet te raken).
- **Boodschap over zee / terugkeer-opdracht**: *De brief van Ys* begint op de Witte Toppen en stuurt je terug naar
  Zoutkom: **Teun** leest hem («Je speelt nog steeds vals met kaarten.») en geeft `fles4` (een fles van de Kraai). De
  missie staat bij 2a (daar is de beloning). Daarnaast lonen de oude haakpunten en het snuffelende vosje.
- **Genezen**: vijf rustplaatsen, bronnen op het plein en in het bos (vlak bij de kuil), de flesjes, op adem komen.
- **Hartstukjes** (3 van de 4 van het eiland; 4 is voor 3b): `hs:witte-toppen-1` (de geiten), `-2` (verstopt op het
  eilandje, het vosje snuffelt hem op), `-3` (in betoverd ijs bij de smidse: pas met de **vuurstaf**, eiland 4a).
- **Muziek**: *IJshoeve* (klokjes en harp, in D), *Sneeuwstorm* (didgeridoo, fluit, in e) en *Wachterspost* (ijzeren
  xylofoon, in a; ook bij de Bergpoort). **Hemel** schuift op met wat je deed (een open eiland): vroeg licht bij
  aankomst, later op de dag na de lantaarn, het vosje en de klauwhaak, een gouden middag na de Bergpoort.
- **Afwijkingen van het plan, en waarom**: de pootjes zie je alleen met Tijmens **sterrenlantaarn** (zo heeft Tijmen
  weer een gave die je nodig hebt, en de storm een puzzel); het vosje is geen huisdier maar een verhaal-metgezel die
  ook op de hoofdlijn nodig is (hij graaft de deur op: snuffelen is meteen spel); de klauwhaak ligt in de verlaten
  Wachterspost (geen bergwachter die hem geeft: Ys en Hagen vertellen erover), met de les **ná** het vinden (je moet
  de kloof uit); de smid van de wachters heet Hagen en geeft (nog) niets: het wachtersharnas is voor 3b. De terugkeer-
  opdracht is een brief aan Teun in plaats van Hanne's sneeuwbloem (Teun en Ys knopen 2 en 3 aan elkaar; Hanne kan
  later nog).

#### 3b · berg en paleis: het IJspaleis (gebouwd: sessie `zelda-eiland-3b`, deel `zelda-e3b` versie 1, klasse `ZeldaEiland3b`)

Het tweede halve eiland van de Witte Toppen. Vak **x 1580 … 1720, z −480 … −180** (elke kolom zelf; de berg is nergens lager
dan G+30, zo loop je niet via de berg om de haakpunten heen). Het paleis ligt op een plateau op G+26 (`F`): de as is
z −294, de Kristalzaal (midden 1658, −294), de Troonzaal (midden 1692, −294), de toren met het baken (1692, −313).
IJshoeve (3a) blijft de hub van het eiland; het voorplein is de hub van het paleis.

| plek | wat | soort |
|---|---|---|
| 1580 … 1593, −304 … −282 | **het bergpad** (vanaf de Bergpoort, door de rots) en de **berghut** van de wachters: **Ida** de klimster | rust 0 `berghut`, persoon |
| 1593 … 1601, −301 … −286 | **de ijswand** (8 hoog): een haakpunt steekt over de rand; een afstapje terug omlaag (alleen omlaag) | haakpunt `ijswand` |
| 1602 … 1610, −301 … −286 | **de ijskloof**: haakpunten aan palen aan beide kanten (heen en terug), een ladder uit de kloof (alleen aan de bergkant) | haakpunten `ijskloof`, `ijskloof-terug` |
| 1611 … 1628, −308 … −282 | **het voorplein**: zielvuur, bron, twee stenen wachters, bordjes (Het IJspaleis, De IJstuin, De sleebaan, Het bergpad) | rust 1 `voorplein`, bron |
| 1610 … 1629, −335 … −309 | **de IJstuin** onder glas, achter een gebarsten muur: een tweede glijvloer (moeilijker, 7 zetten, terug te glijden), sneeuwbloemen, een balkon met **de hoorn van Ys** (haakpunt) | barst `ijstuin`, glijvloer `glijtuin`, vondsten, haakpunt `balkon` |
| 1611 → 1588, −268 → −282 | **de oude sleebaan**: van het plateau omlaag naar de berghut (een rondje terug), tijdrace 15 s | uitdaging `sleebaan` |
| 1629 … 1640, −300 … −288 | **de gracht en de poort**: de valbrug is op; haak je naar de poorttoren (haakpunt boven de poort), binnen een hendel: de brug zakt (en blijft voortaan zakken voor wie hem neerliet) | haakpunt `poort`, puzzel `valbrug` |
| 1635 … 1645, −306 … −289 | **de Poortzaal**; achter een gebarsten muur de wachtkamer met **Tijmen** (opgesloten: «ik was er te vroeg») → de **zonnesteen** | barst `wachtkamer`, persoon |
| 1647 … 1669, −305 … −283 | **de Kristalzaal** (de hub van het paleis): een bevroren fontein, zuilen van ijs, deuren noord, zuid en oost | rust 2 `kristalzaal`, bron |
| 1647 … 1669, −333 … −309 | **de Glijzaal**: glad ijs (5 zetten); aan de overkant kun je niet meer terug over het ijs, een kuil met het **Noorderkristal**, een zielvuur en een bron, een trap naar de Wachtzaal | glijvloer `glijzaal`, vondst `kristal-noord`, rust 3 `kristalkuil` |
| 1671 … 1685, −333 … −319 | **de Wachtzaal**: ijsknechten (drie gemengde golven; de bel = nog eens) — het enige gevecht, daarna de Oostgang in | kamer `wachtzaal` |
| 1671 … 1677, −317 … −283 | **de Oostgang**: rustplaats, bron, de **troondeur** van blauw ijs (open met twee kristallen én het wachtersharnas) | rust 4 `troondrempel`, poort `troonpoort` |
| 1647 … 1669, −280 … −258 | **de Spiegelzaal**: donker tot iemand met de zonnesteen binnen is; vier draaispiegels (een lokspiegel), het licht smelt de ijsdeur | spiegels `spiegelzaal` |
| 1671 … 1689, −279 … −263 | **een kloof in het paleis** (haakpunten heen en terug) naar de **Wapenkamer**: het **Zuiderkristal** en het **bevroren harnas** | haakpunten `wapenkamer`, `spiegelzaal`, vondsten |
| 1500, −300 (3a) | **Hagen** in IJshoeve warmt het harnas: het **wachtersharnas** (`harnas2`); terug naar de hub (de kaart reist van zielvuur naar zielvuur) | praten (Hagen) |
| 1681 … 1709, −305 … −283 | **de Troonzaal** (een achthoek, vier ijszuilen, vier haakpunten aan de muren): **Grimmel**; in het oosten zijn hol | kamer + baas, vondst `hart3`, persoon Grimmel |
| 1690 … 1700, −319 … −307 | **de toren**: een wenteltrap naar het platform (F+28) met **het Vorstbaken** | rust 5 `vorstbaken`, vondst `baken3`, verhaal: einde (`e3b`) |

- **Hoofdlijn** (elf doelen voor `/hint` en Tijmen, na die van 3a): het bergpad op (ijswand, ijskloof) → de poort
  (haakpunt, hendel) → Tijmen (een bom tegen de muur: de zonnesteen) → **in elke volgorde** (doelgroep `kristal`) de
  Glijzaal met daarna de Wachtzaal, en de Spiegelzaal met de Wapenkamer → het harnas naar Hagen → de troondeur →
  Grimmel → `hart3` → het Vorstbaken. Wiske legt uit wat er nog mist bij de troondeur.
- **Rondjes**: dorp → Bergpoort → bergpad → voorplein → sleebaan → berghut; in het paleis Kristalzaal → Glijzaal →
  Wachtzaal → Oostgang → Kristalzaal; de valbrug is na de eerste keer een kortere weg naar buiten; de kaart reist tussen
  de zielvuren (IJshoeve ↔ Kristalzaal). Wie de Glijzaal deed, gaat daarna vrij door de Wachtzaal (de bel = nog eens).
- **Glijvloeren** (motor, nieuw): op glad ijs (blauw ijs) loop je niet: zet je een stap (of spring je), dan glijd je
  rechtdoor tot je ergens tegenaan botst (een rots van pakijs, een muur) of van het ijs af bent. Geen tijd, geen vlag;
  Wiske legt het één keer uit, bij het eerste ijs waar je komt. De Glijzaal is
  verplicht en eenrichting (de kuil erachter ligt drie lager), de IJstuin is moeilijker en optioneel.
- **Grimmel** (`Grimmel`, een `ZeldaBaas`): de reuzenijsbeer van de wachters, betoverd. Hij sjokt en slaat; om de acht
  tellen (onder een derde om de zes) gaat hij op zijn achterpoten staan (dan ligt de richting vast) en stormt hij rechtdoor.
  Wie opzij gaat of zich wegtrekt met de klauwhaak (werkt in zijn zaal: `Kamer.haken`), laat hem tegen een ijszuil lopen:
  de zuil breekt (20 s later terug) en hij duizelt vijf tellen; dan doet een klap 1,2 keer zoveel, anders maar een
  kwart (dikke vacht: `ZeldaRegels.grimmelSchade`): zo duurt het een paar zuilen lang. Tegen een muur schudt hij alleen zijn kop. Wie hij raakt krijgt een duw
  (en weinig schade: de balans en het wachtersharnas). 60 leven (+30 per extra speler, ×1,5 balans), een ijskobold bij
  twee derde en bij een derde. Ys en Hagen verklappen het («recht tegen de zuilen»), Wiske zegt het bij het begin en na
  doodgaan. Bevrijd = de nevel trekt uit hem weg en hij sjokt naar zijn hol (daar slaapt hij voortaan: aaien mag).
- **Vechten**: alleen de Wachtzaal (ijsknecht, vorstschutter, ijskobold: drie gemengde golven) en Grimmel. Pijlen van
  vorstschutters maken je in Zelda niet traag.
- **Genezen en bescherming**: zes rustplaatsen (ook in de kristalkuil: wie in de Wachtzaal valt, hoeft niet opnieuw
  te glijden), bronnen op het voorplein, in de Kristalzaal, in de kuil (bij de Wachtzaal) en in de Oostgang (vlak voor
  Grimmel); het **wachtersharnas** moet je hebben voor de troondeur opengaat.
- **Personen**: **Ida** (berghut: de wand, de sleebaan), **Tijmen** (opgesloten in de wachtkamer: de zonnesteen, daarna de
  wegwijzer van het paleis; na het baken «Op de Gloedberg zien we elkaar weer»), **Grimmel** (na de strijd, in zijn hol).
  Op 3a praten mee: **Ys** (de hoorn, zijn oude harnas, de zuilen, Grimmel, het baken), **Hagen** (het harnas, eerst),
  **Tijmen**, **Brecht** en **Noor**.
- **Zijmissies**: *De hoorn van Ys* (Ys vraagt erom, of je vindt hem eerst: op het balkon in de IJstuin; terug bij Ys →
  `hs:witte-toppen-4`, en hij blaast erop) en **boodschap over zee** *De sneeuwbloem* (pluk hem in de IJstuin; Hanne op het
  Lindewoud brouwt er een **vijfde flesje** van, `fles5`). Daarnaast de sleebaan (record) en het oude ijsblok bij de smidse
  (vuurstaf).
- **Hartstukjes**: het eiland heeft er vier: 1 de geiten, 2 het eilandje, 3 het ijsblok (vuurstaf, 4a), 4 de hoorn van Ys.
- **Muziek**: *Bergpad* (fluit, harp, wind; berghut, voorplein, IJstuin, sleebaan), *IJspaleis* (ijzeren xylofoon en
  klokjes) en baas *Grimmel*. **Hemel**: late middag op het bergpad, zonsondergang op het voorplein, schemer in het
  paleis, nacht in de Troonzaal; brandt het Vorstbaken, dan wordt het ochtend op de hele Witte Toppen.
- **De Gloedberg gaat open**: `baken3` is de `klaar`-vlag van de Witte Toppen; de Gloedberg staat als **aangekondigd** in
  `Zelda.KOMT` (grijs op de kaart en bij Brecht, `/hint` en Tijmen: «Straks varen we naar de Gloedberg»).
- **3a versie 2**: de lawine op het bergpad is weg (Ys en Hagen hebben geschept), het pad is open naar boven.
- **Afwijkingen van het plan, en waarom**: de glijpuzzels laten **jou** glijden (zoals gepland), en het tweede ijsveld is
  een zijpad in de IJstuin; «bevroren hendels» zijn er niet (die passen bij de vuurstaf, 4a): wel een hendel voor de
  valbrug. De spiegelzaal vraagt de **zonnesteen** van Tijmen (zo heeft hij weer een gave die je nodig hebt, net als de
  maanparel), en Tijmen zit deze keer zelf opgesloten (een bom bevrijdt hem). Het **wachtersharnas** ligt bevroren in de
  Wapenkamer en Hagen warmt het: zo moet je terug naar IJshoeve (de hub) en heb je bescherming vóór Grimmel. Grimmel
  wordt niet weggetrokken door de klauwhaak, maar jij trekt jezelf weg terwijl hij stormt. De terugkeer-opdracht is
  de sneeuwbloem voor Hanne (zoals gepland bij «Terugkerende personages»), met een vijfde flesje als beloning.

Muziek van het hele eiland: *IJshoeve*, *Sneeuwstorm*, *Wachterspost* (3a), *Bergpad*, *IJspaleis*, baas *Grimmel* (3b).

**Hoe 4a aanhaakte** (sessie `zelda-eiland-4a`): de Gloedberg staat sinds kitpvp 2.10 in `Zelda.EILANDEN` (open na
`baken3`); het ijsblok bij Hagen is een `Smelt` (betoverd ijs) in `ZeldaEiland3a` en smelt met de vuurstaf. Zie «4 · De
Gloedberg».

### 4 · De Gloedberg

Zwart zand, as, lava die langzaam sputtert, een mijnwerkersdorp **Sintelgat** tegen de vulkaan, en binnenin de oude
**Smidse** van de eerste smeden.

#### 4a · Sintelgat en de mijnen (gebouwd: sessie `zelda-eiland-4a`, deel `zelda-e4a` versie 1, klasse `ZeldaEiland4a`)

Een half eiland met een eigen hub. Vak **x 1060 … 1259, z 180 … 480** (zee tot de rand). Tot de Smidse (4b) er is, bouwt
dit deel ook de kegel van de vulkaan in het vak van 4b (x 1260 … 1400, de krater op 1312, 330), met dezelfde vormen
(`ZeldaEiland4a.land`, `berg`): zodra het eiland een tweede deel heeft, doet 4a dat niet meer (`vulkaanErbij`).
Hoogtes: G = de pad-hoogte bij zee (je staat op G+1), **D = G+2** (Sintelgat en de bovenmijn), **F2 = G−5** (de hete
gangen). Coördinaten: de Zwarte Baai (1095, 335), het plein van Sintelgat (1135, 330), de ingang van de mijn (1166, 330),
de Lorriehal (1196, 330), de Vonkkamer (1197, 265), de Gloedader (1230, 323), de Askamer (1248, 306), de Smidsepoort
(de oostwand, x 1259, z 284), het Uitzicht (1243, 232).

| plek | wat | soort |
|---|---|---|
| 1074 … 1106, 326 … 346 | **de Zwarte Baai**: zwart zand, een steiger de baai in, de Zilvermeeuw, **Brecht** | rust 0 `baai` (aanleg) |
| 1105 … 1167, 299 … 361 | **Sintelgat** (de hub, een terras op D): een plein met een monument (een vuurschaal), zielvuur, bron, lantaarns, bordjes (De Zwarte Baai, De mijn, De Laadkade, Het Lavaveld); **Tijmen** onder zijn luifel; de smidse van **Meester Okker** (de esse is koud); het huis van **Dina** en Dorus; de koerierspost van **Fenna**; **Kobus** op zijn bankje; drie hoge basaltrotsen met de **mijnvuren** | rust 1 `sintelgat`, bron, personen |
| 1126 … 1142, 210 … 233 | **de Laadkade** (noordkust), de sporen van het dorp ernaartoe: **de race van Fenna** (20 s) | uitdaging `sporen` |
| 1117 … 1142, 384 … 405 | **het Lavaveld** (zuid): poelen lava met een stenen rand | — |
| 1165 … 1185, 328 … 332 | **de mijningang**, **Dorus** ervoor | persoon |
| 1186 … 1206, 319 … 341 | **de Lorriehal** (de hub binnen): sporen naar alle kanten, deuren noord (de Oude Gang), zuid (de Zijgang), oost (de Daaltrap) | rust 2 `lorriehal`, bron |
| 1194 … 1198, 297 … 318 | **de Oude Gang**: ingestort, gebarsten stenen | barst `oudegang` (op het pad) |
| 1188 … 1206, 283 … 296 | **de Schachtput**: een put over de hele breedte (water op de bodem, een ladder terug naar de zuidrand); haakpunten aan balken (heen en terug) | haakpunten `schacht`, `schacht-terug` |
| 1188 … 1206, 259 … 282 | **de Vonkkamer**: het altaar van de eerste smeden met de **vuurstaf**; drie lampen hoog aan de wanden en boven de put: alle drie aan (20 s) = het hek naar beneden | rust 3 `vonkkamer`, vondst `vuurstaf`, vuurpuzzel `lampen` |
| 1208 … 1236, 262 … 313 | **de hete gangen** (F2): de Vonktrap omlaag, gang A (een koelbak, lava achter een muurtje, bevroren water `koel1`), gang B (bevroren water `koel2`), gang C | hitte `hetegangen`, ijs `koel1`, `koel2` (op het pad) |
| 1222 … 1238, 314 … 332 | **de Gloedader**: muren vol gloed, een lavaval achter tralies, een koelbak, het **gloedijzer** | rust 4 `gloedader`, bron, vondst `gloedijzer` |
| 1207 … 1221, 323 … 327 | **de Daaltrap**: van de Gloedader omhoog naar de Lorriehal; het hek gaat alleen open voor wie gloedijzer heeft (de korte weg terug) | poort `daaltrap` |
| 1142 … 1153, 310 … 321 | terug in Sintelgat: **de esse** aansteken met de vuurstaf, en **Okker** smeedt het **gloedharnas** (`harnas3`) | vuurpuzzel `esse`, praten |
| 1239 … 1254, 318 … 330 | **de gloed**: de Gloedgang omhoog, de gloedtrap (rust, bron) voor de Askamer; zonder gloedharnas te heet | hitte `gloed` (fel), rust 5 `gloedtrap`, bron |
| 1240 … 1256, 296 … 316 | **de Askamer**: asknecht, vonkschutter, sintelmijt (drie gemengde golven; de bel = nog eens) — het enige gevecht | kamer `askamer` |
| 1240 … 1258, 274 … 294 | **de Smidsepoort**: een poort van gloeiend steen in de oostwand, twee smeden van steen; daarachter de Smidse (4b) | rust 6 `smidsepoort`, verhaal: einde (`e4a`, vereist `k:askamer`) |
| 1240 … 1244, 238 … 273 | **de Asgang** naar buiten (het hek opent voor wie de Askamer won) en **het Uitzicht**: **Ida** de klimster; over het Asveld terug naar Sintelgat | poort `asgang`, rust 7 `uitzicht`, persoon |
| 1191 … 1201, 342 … 364 | **de Zijgang** en de oude koelkelder: een muur van betoverd ijs, erachter **de lorrie van Dina** | ijs `koelgang`, vondst `lorrie` |

- **Hoofdlijn** (elf doelen voor `/hint` en Tijmen): Tijmen (de **koelsteen**) → Okker (de esse is koud; de vuurstaf
  ligt in de oude mijn) → de mijn in: een bom op de Oude Gang → haak je over de Schachtput → de **vuurstaf** → de lampen →
  **in elke volgorde** (doelgroep `smeden`) de esse in de smidse aansteken, en de hete gangen door naar het
  **gloedijzer** → Okker: het **gloedharnas** → de gloed in, de Askamer → de Smidsepoort (`e4a`). `wacht()`: «De
  Smidsepoort gloeit nog dicht. Nog even geduld.»
- **Rondjes**: Lorriehal → Oude Gang → Schachtput → Vonkkamer → Vonktrap → hete gangen → Gloedader → Daaltrap →
  Lorriehal; Gloedader → de gloed → Askamer → Smidsepoort → Asgang → Uitzicht → Asveld → Sintelgat; Sintelgat → de sporen →
  de Laadkade. Met het gloedijzer is de Daaltrap een korte weg, na de Askamer de Asgang. De kaart reist tussen de acht
  zielvuren. Wat de volgorde bewaakt: zonder klauwhaak kom je niet in de Vonkkamer, zonder de lampen (vuurstaf) niet
  naar beneden, zonder gloedijzer niet door het hek van de Daaltrap, zonder gloedharnas niet door de gloed
  (`kitpvp zelda lopen gloedberg zonder klauwhaak dicht daaltrap dicht asgang`).
- **De vuurstaf** (vak 6, `ZeldaSpel.Vuurstaf`): rechtsklik = een vlam die rechtdoor vliegt (24 blokken; een paar graden
  ernaast mag, net als de klauwhaak). Een vuurschaal of lamp gaat aan (`Reeks.Hoe.VUUR`, en ook de gewone vuurschalen
  `KLIK`, zoals de drie vuren van de tempelruïne op het Lindewoud), betoverd ijs smelt (`Smelt`), een vijand krijgt een
  tik (ijsvijanden het dubbele). Wiske legt het uit bij het pakken.
- **Hitte** (`ZeldaHoofdstuk.Hitte`, `ZeldaRegels.hitte`): een rustige druk, geen schade. In de hete gangen vult de balk
  onderin (de ervaringsbalk) zich; zonder koelsteen in ±2 s, met de koelsteen van Tijmen in ±9 s. In water (een koelbak,
  gesmolten ijs), bij een zielvuur of in een bron koel je af. Vol = «Pff, te heet!» en je staat weer waar je het laatst
  afkoelde. De **gloed** (fel) bij de kern: daar helpt alleen het gloedharnas, en koelt niets. Met het gloedharnas deert
  hitte je nergens meer. Wiske legt het één keer uit (bij 60 %).
- **Betoverd ijs** (`Smelt`, blok `ICE`, altijd hetzelfde): smelt met de vuurstaf tot lucht (een doorgang) of water (een
  koelplek); wie het smolt (`s:<id>`) vindt het gesmolten; na een minuut niemand weer bevroren. Gewoon ijs smelt ook bij
  fel licht: hou lampen en lava vijf blokken weg (soul lanterns mogen: licht 10).
- **Personen** (acht): Brecht, Tijmen (de koelsteen; daarna de wegwijzer), Okker (de smid: de esse, het gloedharnas,
  de gloedkool voor Hagen; na de Askamer: Asgrim), Dina (*De verdwaalde lorrie*), Dorus (de mijningang), Fenna (*De
  sporen*), Kobus (*De mijnvuren*; vertelt over de Smidse), Ida (het Uitzicht).
- **Zijmissies en terugkomen**: *De verdwaalde lorrie* (Dina: de Zijgang, het ijs smelten → `hs:gloedberg-1`), *De sporen*
  (Fenna: de race naar de Laadkade → `hs:gloedberg-2`), *De mijnvuren* (Kobus: drie vuurschalen hoog op de rotsen bij het
  plein, met de vuurstaf → `hs:gloedberg-3`; het monument brandt dan ook). **Boodschap over zee** *De gloedkool*: wie op
  de Gloedberg was, hoort van **Hagen** in IJshoeve dat hij met Okker leerde smeden; Okker geeft een gloedkool, en terug
  bij Hagen krijg je een veldfles (`fles6`). De missie staat bij 3a (daar is de beloning). En **de vuurstaf op de Witte
  Toppen**: het betoverde ijsblok bij de smidse van Hagen smelt (`hs:witte-toppen-3`; Hagen zegt het als hij de staf ziet).
- **Vechten**: alleen de Askamer, en pas met het gloedharnas (de gloed): bescherming vooraf en een zielvuur en een bron op
  de gloedtrap vlak ervoor. Nieuwe vijanden: de **asknecht** (zombie met een houweel), de **vonkschutter** (skelet met
  een boog) en de **sintelmijt** (een klein magmaklodder, snel en niet taai; splitst niet).
- **Genezen**: acht zielvuren, bronnen in Sintelgat, de Lorriehal, de Gloedader en op de gloedtrap, de flesjes, op adem
  komen.
- **Hartstukjes**: drie (`hs:gloedberg-1` … `-3`); `hs:gloedberg-4` is voor 4b.
- **Muziek**: *Sintelgat* (banjo, gitaar, het tikken van een aambeeld; de baai, het dorp en buiten), *Mijnen* (bas,
  druppels, een harp in de verte; de bovenmijn, de hete gangen en de Asgang) en *Gloed* (een brommende grondtoon en een
  ijzeren melodie; de Gloedader, de gloed, de Smidsepoort). **Hemel** schuift op met wat je deed: een gouden middag, later
  de avond (vuurstaf, harnas), na de Smidsepoort de schemer. Buiten valt as en rookt de krater (van ver te zien).
- **Afwijkingen van het oude plan, en waarom**: de smid heet **Okker** (Gerrit is de herder op het Lindewoud); de
  mijnwerkers zijn Dina en Dorus, maar alleen Dina heeft de zijmissie (Dorus staat bij de mijn); Tijmens gave is de
  **koelsteen** (zonder hou je het in de hete gangen geen drie tellen uit: zo is hij nodig, net als de sterrenlantaarn).
  Het gloedharnas vraagt twee dingen (de esse aan en gloedijzer uit de hete gangen): zo moet je terug naar de hub en heb je
  bescherming vóór het enige gevecht. Het ijs op de Gloedberg is betoverd ijs uit de oude koelkelder van de mijnwerkers
  (ijs van de Witte Toppen): gesmolten wordt het een koelplek. De Smidse is in `Zelda.KOMT` aangekondigd als *deel* van
  de Gloedberg (`ZeldaEiland.Komt.eiland`): grijs op de kaart, niet bij Brecht.

#### 4b · de Smidse (gebouwd: sessie `zelda-eiland-4b`, deel `zelda-e4b` versie 1, klasse `ZeldaEiland4b`)

Het tweede halve eiland van de Gloedberg: de kerker in de vulkaan. Vak **x 1260 … 1400, z 180 … 480**; dit deel zet elke
kolom zelf (de kegel en de krater, met `ZeldaEiland4a.land`/`berg`; 4a bouwt de kegel niet meer). Rond de top is de kegel
zes blokken hoger (`ZeldaEiland4b.RAND`, r < 29,5): een wand die je van buiten niet op klimt, zodat je alleen door de
Smidse bij de krater komt. Hoogtes: **D = G+2** (de Smidse, net als de Smidsepoort), **KF = G+50** (de kratervloer, r < 11).
Coördinaten: de Smidsehal (1273, 284), de Kettingzaal (1277, 257), de Stuwdam (1314, 260), de Gietzaal (1315, 288), de
voorkamer (1282, 330), het Hart van de Smidse (midden 1312, 330, onder de krater), de Schoorsteen (1333, 330), de
uitkijk (1335, 321, KF+10).

| plek | wat | soort |
|---|---|---|
| 1259, 281 … 287 | **de Smidsepoort** (in de wand van 4a): gaat open voor wie de Askamer won | poort `smidsepoort` |
| 1260 … 1286, 272 … 296 | **de Smidsehal** (de hub van de Smidse): zuilen, beelden van smeden, op het grote aambeeld een **oude schildplaat**; **Tijmen** leest het schrift boven de Hartdeur («drie vonken wekken het Hart»); bordjes (De Kettingzaal, De Gietzaal, Sintelgat) | rust 0 `smidsehal`, bron, vondst `schildplaat`, persoon |
| 1260 … 1294, 246 … 268 | **de Kettingzaal** (noord): een kloof met lava, een pijler met een **vonk**; haak je naar de pijler en naar de overkant; daar hangt de kettingbrug aan een touw: brand het door met de vuurstaf en de brug valt (de weg terug) | haakpunten `pijler`, `kettingnoord`, `kettingzuid`, vondst `vonk-keten`, vuurpuzzel `kettingbrug` |
| 1298 … 1330, 247 … 272 | **de Stuwdam** (noordoost, door de Kettingzaal): een meer van lava met de **vonk** op een eilandje; een bom tegen de gebarsten dam: het water stroomt over de lava en dat wordt steen (een pad van obsidiaan); achter de dam de waterkelder (het bijlblad voor Kees) | barst `stuwdam` (op het pad), poort `koelpad` (voor wie de dam brak), vondsten `vonk-dam`, `bijlblad` |
| 1298 … 1331, 276 … 300 | **de Gietzaal** (oost, ook vanaf de hal): de gietrivier (lava); drie ovens (twee op de oever, één hoog in de muur) met de vuurstaf binnen 25 s = de rivier zakt weg; daarna **lava die stijgt en zakt** over vier stenen naar de nis met de **vonk** | bron, vuurpuzzel `ovens`, ritme `gietvloer`, vondst `vonk-giet` |
| Sintelgat (4a) | **Okker** smeedt de schildplaat tot het **Gloedschild** (`gloedschild`: Asgrims sintels ketsen erop af) | praten (Okker) |
| 1271 … 1275, 297 | **de Hartdeur** (zuid in de hal): drie vuurschalen erboven, één per vonk (voor jou); open met drie vonken én het Gloedschild | poort `hartdeur` |
| 1270 … 1294, 298 … 343 | **de Hartgang** en **de voorkamer**: zielvuur, bron; een nis vol zwarte nevel (een hartstukje, pas met de lichtpijlen) | rust 1 `hartvoorkamer`, bron, vondst `hs:gloedberg-4` |
| 1298 … 1326, 316 … 344 | **het Hart van de Smidse**: rond, een lavaput met een eilandje en vier bruggen, vier koelkuipen aan kettingen: **Asgrim de Gloedwachter** | kamer `smidshart` + baas, vondst `hart4`, persoon Asgrim |
| 1328 … 1338, 324 … 336 | **de Schoorsteen**: onderin een poel; vier balkons om en om, elk met een haakpunt aan een balk; bovenaan een ladder en de gang naar de krater. Terug = springen (in het water) | rust 2 `schoorsteen`, haakpunten `schoorsteen1` … `4` |
| krater (r < 11, KF) | **de krater**: een ring van lava om een eilandje met het **Gloedbaken**; steek het aan met de vuurstaf, pak zijn licht | rust 3 `krater`, vuurpuzzel `gloedbaken`, vondst `baken4` |
| 1332 … 1338, 316 … 326 | **de uitkijk** (een trap door de kraterwand): hier breekt de **Nevelring**; **Tijmen** staat er al | verhaal: einde (`e4b`, vereist `baken4`), persoon |

- **Hoofdlijn** (veertien doelen voor `/hint` en Tijmen, na die van 4a): de Smidse in → Tijmen (het schrift) → **in elke
  volgorde** (doelgroep `smidse`) de schildplaat, de drie vonken en het Gloedschild bij Okker → de Hartdeur → Asgrim →
  `hart4` → de Schoorsteen op → het Gloedbaken aansteken → `baken4` → de uitkijk (de Nevelring breekt).
- **Rondjes**: Smidsehal → Kettingzaal → Stuwdam → Gietzaal → Smidsehal (in beide richtingen; de vonken liggen naast de
  doorgang, dus de volgorde maakt niet uit); de Smidsehal ↔ Sintelgat via de Smidsepoort, de gloed en de Asgang of de
  Daaltrap; de kaart reist tussen de zielvuren. Wat de volgorde bewaakt: zonder klauwhaak geen vonk in de Kettingzaal en
  niet de schoorsteen op; zonder bom geen pad naar de vonk bij de dam; zonder vuurstaf zakt de gietrivier niet en brandt
  het baken niet; zonder drie vonken en het Gloedschild gaat de Hartdeur niet open (`kitpvp zelda lopen gloedberg dicht
  hartdeur`, `… zonder klauwhaak`, `… dicht koelpad`).
- **Lava doet geen echte schade** (motor, alle eilanden): wie in lava stapt, springt er meteen uit, terug naar waar hij
  veilig stond (geen lava vlakbij), met één hart schade (`ZeldaSpel.LAVA_SCHADE`), nooit dood,
  zonder brand. Wiske zegt het één keer. Zo is lava overal een puzzel zonder dat kinderen doodgaan.
- **Lava die stijgt en zakt** (`ZeldaHoofdstuk.Ritme`): 4,5 s liggen de stenen droog, 1,8 s staat de lava erover; een
  tel ervoor borrelt het. Alleen als er iemand in de buurt is.
- **Asgrim** (`Asgrim`, een `ZeldaBaas`): een groot vlammenwezen (blaze, ×1,9) dat over de lavaput zweeft, telkens onder
  een van de vier koelkuipen, en om de paar tellen naar een andere kuip vliegt. Hij gooit sintels (langzaam: opzij, of het
  Gloedschild ervoor: dan ketsen ze af; een gewoon schild houdt de helft tegen). Door zijn vlammenschild doet een klap een
  tiende (`ZeldaRegels.asgrimSchade`). Kantel de kuip boven hem (een **pijl** op de kuip, of een **bom** ertegen): het
  water dooft hem, hij ploft neer op de rand en is vijf tellen gedoofd: dan doet een klap alles. Een kuip is na twaalf
  tellen weer vol. De vuurstaf maakt hem alleen feller (Wiske zegt het). 70 leven (+35 per extra speler, ×1,5), één helper
  bij twee derde (sintelmijt) en bij een derde (vonkschutter). Kobus en Okker verklappen het, Wiske zegt het bij het
  begin en na doodgaan. Bevrijd = de nevel trekt uit hem weg; hij is weer de kleine Gloedwachter op het eilandje
  («Steek het Gloedbaken aan. Boven, in de krater.»).
- **Vechten**: alleen Asgrim (geen andere verplichte kamer: de Smidse is een puzzelkerker). **Genezen en bescherming**:
  vier zielvuren, bronnen in de hal, de Gietzaal en de voorkamer (vlak voor Asgrim), de flesjes; het **Gloedschild** is
  nodig voor de Hartdeur opengaat, en het gloedharnas houdt de gloed tegen.
- **De Nevelring breekt** (op de uitkijk, een paar tellen, alleen voor jou): het Gloedbaken zendt een bundel omhoog, uit
  het noorden komen de bundels van het Getijdenbaken en het Vorstbaken, samen raken ze een muur van zwarte nevel aan de
  horizon en die spat uiteen; dan breekt de ochtend aan (op de hele Gloedberg). Daarna brandt het baken voor wie het
  aanstak (een bundel, van overal op het eiland te zien). Op het Lindewoud hangt de Nevelring als zwarte rook om Nachtrots
  aan de horizon, tot het Gloedbaken brandt. Brecht, Ida en Tijmen merken het op.
- **Personen**: Tijmen (de hal: het schrift; na het baken op de uitkijk: «Daar zien we elkaar weer»), Asgrim (na het
  gevecht). Op 4a praten mee: **Okker** (het Gloedschild; «Asgrim is vrij?»), **Kobus** («hij kan niet tegen water»),
  **Ida** en **Brecht** (de nevel is weg).
- **Boodschap over zee** *Het bijlblad*: wie in de Smidse was, hoort van **Kees** (Lindewoud) dat zijn bijl bot is; het
  bijlblad van de eerste smeden ligt in de waterkelder achter de dam (`vereist = "g:kees-blad"`); terug bij Kees krijg je
  zijn oude veldfles (`fles7`). De missie staat bij het Lindewoud (`ZeldaHoofdstuk1.BIJLBLAD`), daar is de beloning.
- **Hartstukjes**: `hs:gloedberg-4` in de nevelnis (pas met de lichtpijlen van Nachtrots: later terug). De Gloedberg heeft
  er daarmee vier.
- **Muziek**: *Smidse* (een dreun als van een hamer, een lage bas, een ver aambeeld; in de hele Smidse), *Gloed* (de krater
  en de uitkijk) en baas *Asgrim*. Buiten op de helling *Sintelgat*. **Hemel**: schemer; als de Nevelring breekt, de
  ochtend.
- **Nachtrots gaat open**: `baken4` is de `klaar`-vlag van de Gloedberg; Nachtrots staat als **aangekondigd** eiland in
  `Zelda.KOMT` (grijs op de kaart en bij Brecht, `/hint` en Tijmen: «Straks varen we naar Nachtrots»).
- **Afwijkingen van het oude plan, en waarom**: de lava «stijgt en zakt» op twee manieren: de gietrivier zakt weg met de
  ovens (vuurstaf) en de gietvloer stijgt en zakt op een ritme; de kettingbrug valt als je het touw doorbrandt. Asgrim
  zweeft niet boven de lava om met pijlen te raken: je kantelt de **kuipen** boven hem (pijl of bom), en dan sla je hem
  met het zwaard (zo telt alles wat je hebt). Het Gloedbaken staat niet «boven op de berg» maar in de krater, en de
  Nevelring breek je op de uitkijk ernaast. De bescherming vooraf is het **Gloedschild** (Okker smeedt het: terug naar de
  hub, zoals Hagen in 3b), omdat het gloedharnas al nodig was om binnen te komen. Tijmen heeft hier geen nieuwe gave (de
  koelsteen was de gave van de Gloedberg): hij leest het schrift en staat er als de Nevelring breekt. De terugkeer-opdracht
  is Kees en zijn bijl (zoals bij «Terugkerende personages» stond), met een zevende flesje.

Muziek van het hele eiland: *Sintelgat*, *Mijnen*, *Gloed* (4a); *Smidse* en baas *Asgrim* (4b).

**Hoe 4b aanhaakte** (sessie `zelda-eiland-4b`): de Gloedberg heeft twee delen (`List.of(new ZeldaEiland4a(plugin), new
ZeldaEiland4b(plugin))`), «De Smidse» is uit `Zelda.KOMT` en Nachtrots staat erin (open na `baken4`). De Smidsepoort is
een `Poort` van 4b in de wand van 4a (x 1259); 4a bleef op versie 1 (zonder blokken erbij: Okker smeedt het Gloedschild,
een paar gesprekken, de ochtend na de Nevelring). Op het Lindewoud (ook zonder herbouw): Kees en het bijlblad, de Nevelring
om Nachtrots aan de horizon.

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

**Hoe 5a aanhaakt** (sessie `zelda-eiland-5a`):
- Haal «Nachtrots» uit `Zelda.KOMT` en zet het eiland in `Zelda.EILANDEN` (open na `baken4`, aanleg bij de kreek). Het vak
  is x 1640 … 2000, z −150 … 150 (de zee voor de overtocht ligt op x 1840 … 2040, z −510 … −330: blijf daar weg).
- De Nevelring is gebroken voor wie `baken4` heeft (vlag `nevelring` als hij het zag); op het Lindewoud hangt hij tot dan
  als zwarte rook om de burcht aan de horizon (`ZeldaHoofdstuk1.tik`).
- De lichtpijlen (vlag `lichtpijlen`, staat al in `ZeldaVoorwerp.LATER`): de nevelnis in de voorkamer van de Smidse heeft
  `hs:gloedberg-4` met `voorwerp = "lichtpijlen"` (en Morgals nevel als deeltjes, `ZeldaEiland4b.tik`). Zodra het voorwerp
  er is, is hij te pakken; de test haalt hem dan bij «later terug».
- Wat er is: lava doet geen echte schade (terug naar waar je veilig stond), `Ritme` (lava die stijgt en zakt), een baas
  kan iets met een pijl op een blok (`ZeldaBaas.raakBlok`) en met de vuurstaf (`ZeldaBaas.vlam`); deeltjes voor één speler
  van ver (`ZeldaSpel.deeltje`). Tijmen en Ida kunnen terugkomen; Hanne heeft nog een opdracht tegoed.

## Terugkerende personages

Jelle (27-10): eilanden zo ontwerpen dat je er later naar terug moet of wilt, omdat je bij een bekend karakter iets
moet regelen; een vaste wegwijzer bij wie je steeds terugkomt. Zo knopen we de eilanden aan elkaar:

| wie | wat | Lindewoud | Zilverstrand | later |
|---|---|---|---|---|
| **Tijmen**, reizende tovenaar (wandering trader, blauwe mantel) | de **wegwijzer**: praten = je volgende opdracht (`spel.wegwijzer(p, "Tijmen")`), Wiske vliegt die kant op; hij geeft wat je nodig hebt om verder te kunnen | sterrentoren in Lindedorp: flesje `fles2`, het **lichtwoord** (doornen) | plein van Zoutkom (2a): «ik was er al»; het Getijdenplein (2b): de **maanparel** (het getij) | IJshoeve (3a): de **sterrenlantaarn** (de storm); het IJspaleis (3b): zelf opgesloten in de wachtkamer, een bom bevrijdt hem: de **zonnesteen** (de spiegelzaal); Sintelgat (4a): de **koelsteen** (de hete gangen); de Smidse (4b): leest het schrift boven de Hartdeur, en staat op de uitkijk als de Nevelring breekt; verder op elk eiland bij de hub of steiger (de visserskreek op Nachtrots), met één eigen gave per eiland; Wiske verbaast zich er elke keer over («Hij is er alwéér!»); op Nachtrots helpt hij Wolf |
| **Brecht**, visser en kapitein | varen (`spel.varen`), op elke steiger | pier | steiger; zijn zus Mira; na het baken: «naar het noorden» | de fjord van de Witte Toppen («hier vriest zelfs mijn baard»); de Zwarte Baai («mijn zeilen worden zwart»); op elke steiger; na Morgal vaart hij iedereen naar huis |
| **Hanne**, kruidenvrouw | brouwt met wat je van andere eilanden meebrengt | *Moeraskruid* → `fles1` | *Zeekraal* (aan de dijk) → `sterkwater` | *De sneeuwbloem* uit de IJstuin (3b) → `fles5`, een vijfde flesje; later nog iets (Nachtrots?) |
| **Kees**, houthakker | heeft altijd iets nodig van ver weg | *De verloren bijl* | — | 4b: *Het bijlblad* uit de Smidse van de Gloedberg → zijn oude veldfles (`fles7`) |
| **Lotje**, kind op de pier | telt meeuwen, wil weten hoe het elders is | praatje | (vraagt naar de meeuwen); *De zingende schelp* uit de getijdenpoelen (2b) → `fles3` | kan een kleine boodschap krijgen (een veer van de Meeuwenrots, een sneeuwbal die niet smelt) |
| **Teun**, oude visser (Zoutkom) | weet van vroeger | — | de bakens (2a); voer met Zoutbaard: de **ankersleutel** en de ton buskruit (2b) | jeugdvriend van **Ys** op de Witte Toppen: *De brief van Ys* → `fles4` (3a) |
| **Ys**, oude bergwachter (IJshoeve) | vist aan zijn wak, weet van het IJspaleis | — | — | 3a: de brief voor Teun; 3b: zijn hoorn (`hs:witte-toppen-4`), zijn oude harnas, «Grimmel loopt recht tegen de zuilen» |
| **Hagen**, smid (IJshoeve) | smeedde voor de bergwachters | — | — | 3a: de klauwhaak (verhaal), het ijsblok; 3b: warmt het bevroren harnas → `harnas2`; 4a: leerde smeden met **Okker** in Sintelgat: *De gloedkool* → `fles6`; met de vuurstaf smelt het ijsblok (`hs:witte-toppen-3`) |
| **Ida**, klimster (berghut, 3b) | klimt overal | — | — | 3b: de wand, de sleebaan; 4a: op het Uitzicht van de Gloedberg («ik klim alle bergen»; na 4b: de nevel is weg); misschien ook op Nachtrots |
| **Okker**, smid (Sintelgat, 4a) | de smid van het mijnwerkersdorp | — | — | 4a: de esse, het gloedharnas, de gloedkool voor Hagen; 4b: smeedt de schildplaat uit de Smidse tot het **Gloedschild** |
| **Kobus**, oude mijnwerker (Sintelgat, 4a) | weet van vroeger | — | — | 4a: de mijnvuren; vertelt over de Smidse; 4b: «Asgrim kan niet tegen water» |
| **Wiske**, de fee | hint, uitleg, wijst | overal | overal | wordt feller bij elk baken (`ZeldaMetgezel.licht`: iets groter, meer glans) |

**Bouwregel voor elk volgend eiland:** zet Tijmen bij de hub (met `wegwijzer` als laatste gesprek), en maak minstens
één **boodschap over zee**: een zijmissie van een bekende op een eerder eiland die pas begint als je op het nieuwe
eiland was (`s.heeft("e:<eiland>")`), met de vondst op het nieuwe eiland (`vereist = "g:<gesprek>"`) en de beloning
bij de bekende. `/hint` noemt zo'n missie ook op het andere eiland, en `kitpvp zelda test … alles` vaart aan het eind
twee rondes heen en weer om ze af te maken.

## Balans van gevechten (kitpvp 2.8.2, Jelle 27-10)
- Gevechten langer maar lichter, meer variatie in tegenstanders, heelplek en bescherming voor elk zwaar gevecht (Jelle 27-10). Gebruik de gedeelde balans in `ZeldaRegels` (`SCHADE` 0.55 op alle vijandschade, `BAAS_LEVEN` 1.5, `GOLF_PAUZE` 4 s): geef een kamer liever 3 gemengde golven (vechter + schutter + snel klein beestje, bijv. `SPIN`) dan 1 zware; richting 1-3 minuten, zelden dood. Bijstellen = die drie getallen, niet per kamer.
- De Smidse (4b): alleen Asgrim (lang maar licht: een vlammenschild tot een kuip hem dooft; zijn sintels doen weinig en ketsen af op het Gloedschild), met een zielvuur en een bron in de voorkamer en het Gloedschild als voorwaarde.
- De Gloedberg (4a) ook: de Askamer is het enige gevecht (asknecht + sintelmijt, asknecht + vonkschutter, alle drie), pas met het gloedharnas (de gloed ervoor is te heet zonder), met een zielvuur en een bron op de gloedtrap vlak voor de deur.
- Het IJspaleis (3b) volgt dit: de Wachtzaal heeft drie gemengde golven (ijsknecht + ijskobold, ijsknecht + vorstschutter, alle drie), Grimmel is lang maar licht (een dikke vacht tot hij tegen een zuil loopt; zijn storm doet weinig), met een bron en een zielvuur vlak voor de troondeur en het wachtersharnas als voorwaarde.

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
| dier | `ZeldaDier` | vlag `dier:<soort>`; alleen het **sneeuwvosje** (`VERHAAL`) loopt mee, vrije huisdieren staan uit (`AAN = false`); naam kiezen (menu), loopt mee (eigen pad, geen eigen wil), gaat in een gevecht opzij, snuffelt verborgen vondsten op, graaft graafplekken op |
| bronnen | `ZeldaHoofdstuk.Bron` | feeënkring (licht, geen blokken): geneest snel, vult flesjes; ook zielvuren vullen flesjes |
| flesjes | `ZeldaSpel.Flesje` | vak 3, rechtsklik = vijf harten; `fles1` … `fles6` (elk flesje telt, `ZeldaVoorwerp.heeft`: Tijmens `fles2` staat er ook zonder `fles1`) |
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
| **de Getijdentempel** (eiland 2b) | | |
| eb en vloed | `ZeldaHoofdstuk.Getij` (+ `Hendel`), `getij(…)`, `tij(g, hendel, vloed)`, `spel.trek`, `spel.zetGetij` | een bak met waterlagen (van onder naar boven); een hendel (blok `LEVER`, `mag`: wie mag trekken, `nee`: wat Wiske zegt als het niet mag) zet het om, laag voor laag; `sluizen` open bij eb, `vloeddeuren` bij vloed; `onthoud` = vlag voor wie het omzette; `binnen` = waar wie in een dichtgaande deur staat terechtkomt; een minuut leeg = terug naar `rustVloed`; `zelda lopen` telt het water als water en de deuren als open; de test trekt met een echte klik (`tij` in de volgorde) |
| een baas naar keuze | `ZeldaBaas.soort`, `helper`, `kleur`, `beweeg`, `schade`, `boem`, `weg` | een subklasse doet het anders: wat hij elke seconde doet, hoeveel een klap doet, wat een bom in zijn kamer doet, hoe hij verdwijnt; `golf()` roept de baas en zijn helpers op met deze soorten |
| Kapitein Zoutbaard | `Zoutbaard` | staat bij een van zijn kruittonnen (blokken), springt om de paar tellen; een bom bij hem of zijn ton = de ton springt, hij is versuft (`ZeldaRegels.zoutbaardSchade`) |
| drietanden | `ZeldaSpel.DRIETAND`, `onWorp` | een drietand van een vijand doet hoogstens twee harten en verdwijnt na het neerkomen; nieuwe vijanden `MATROOS` (zombie), `HARPOENIER` en `KAPITEIN` (drowned met drietand) |
| een eiland dat eraan komt | `ZeldaEiland.Komt`, `Zelda.KOMT`, `spel.komt` | na `openNa` grijs op de kaart en bij Brecht («nog even geduld»); `/hint` en Tijmen zeggen dat je er straks heen vaart |
| uiterlijk van een persoon | `Persoon.uiterlijk` | na het verschijnen (de kleur van Lorre de papegaai, zittend) |
| **de Witte Toppen** (eiland 3a) | | |
| klauwhaak | `ZeldaSpel.Klauwhaak`, `spel.haak`, `ZeldaRegels.mik`, `HAAK_BEREIK` | vak 5; rechtsklik = de haak naar het haakpunt waar je op mikt (24 blokken, 6° ernaast mag, niets ertussen: `spel.zicht`); de ketting vliegt uit, dan word je erheen getrokken (geen val- of stikschade onderweg) en sta je op de landing; mis = hij komt terug |
| haken op het pad | `haak(haakpunt, van)`, `ZeldaHoofdstuk.Haak` | in de volgorde: hier haak je je omhoog (de test schiet de klauwhaak echt, vanaf `van`); `zelda lopen` volgt haakpunten die je ergens vandaan kunt raken |
| graafplek | `ZeldaHoofdstuk.Graaf`, `graaf(…)`, `spel.gegraven` | iets onder de sneeuw dat alleen een dierenneus vindt: je dier loopt erheen, graaft, en wie erbij staat krijgt de vlag (bv. voor een `Poort`) |
| persoon voor sommigen | `Persoon.zichtbaar` | alleen te zien (en te praten) voor wie dit klopt: een geit in het veld tot je hem vond, daarna in de stal |
| een dier krijgen | `spel.dier(p, soort)` | vlag, nog geen naam, deuntje, en meteen het naammenu |
| nevelwolven | `Soort.NEVELWOLF` | betoverde sneeuwwolven (rode ogen, snel, niet taai) |
| wat ligt erachter? | `kitpvp zelda lopen <eiland> zonder <voorwerp> dicht <poort>` | de loopcontrole met een poort dicht of zonder een voorwerp: zo zie je of je ergens omheen kunt |
| **het IJspaleis** (eiland 3b) | | |
| glijvloer | `ZeldaHoofdstuk.Glij`, `glij(…)`, `spel.glij`, `ZeldaRegels.glij` / `glijOplossing` | glad ijs: een stap erop = rechtdoor glijden tot je ergens tegenaan botst (een halve blok per tik, kijken kan gewoon) of van het ijs af bent; `oplossing` voor de test (de eerste zet is een echte stap) en de zelftest (met de blokken van de wereld); `zelda lopen` glijdt mee |
| klauwhaak in een gevecht | `Kamer.haken` | in deze kamer werkt de klauwhaak ook tijdens het gevecht (wegtrekken als Grimmel stormt) |
| licht voor spiegels | `Spiegels.licht` | het licht van een spiegelpuzzel brandt alleen als er iemand is voor wie dit klopt (de zonnesteen) |
| Grimmel | `Grimmel` | een baas met ijszuilen: stormt (aanloop op zijn achterpoten, dan rechtdoor per tik), een zuil = duizelig (`ZeldaRegels.grimmelSchade`), de zuil groeit terug; sjokt na afloop naar zijn hol |
| nieuwe vijanden | `Soort.IJSKNECHT`, `VORSTSCHUTTER`, `IJSKOBOLD`, `GRIMMEL` | een zombie met ijs, een stray (zijn pijlen maken je niet traag), een kleine snelle zombie, een ijsbeer (×2) |
| kaartplaatjes van één deel | `kitpvp kaart <begin>` | alleen de plaatjes waarvan de naam zo begint (bv. `zelda-e3b`): lichter voor de server |
| **de Gloedberg** (eiland 4a) | | |
| vuurstaf | `ZeldaSpel.Vuurstaf`, `spel.vlam`, `ZeldaRegels.VUUR_BEREIK` | vak 6; rechtsklik = een vlam (24 blokken, 6° ernaast mag, niets ertussen: `spel.zichtpunt`); steekt vuurschalen en lampen aan, smelt betoverd ijs, een tik voor vijanden (ijs het dubbele) |
| vuurpuzzel | `Reeks.Hoe.VUUR`, `Reeks.voor` | dingen die alleen de vuurstaf aansteekt (hoog of ver weg); `voor` = de plek om te mikken (de zelftest kijkt of je elk ding ziet, de test schiet echt); ook `KLIK`-vuurschalen gaan aan met de vuurstaf |
| betoverd ijs | `ZeldaHoofdstuk.Smelt`, `smelt(…)`, `smeltOpPad(…)`, `spel.smelt` | blok `ICE`; smelt tot lucht of water (een koelplek), `s:<id>`, na een minuut weer bevroren; `voor` voor de test; `zelda lopen` telt het als open met de vuurstaf |
| hitte | `ZeldaHoofdstuk.Hitte`, `hitte(…)`, `ZeldaRegels.hitte`, `hittevast` | een gebied waar je warm wordt (de ervaringsbalk); koelsteen = langzamer, water/zielvuur/bron = afkoelen, vol = terug naar je koelplek (geen schade); `fel`: alleen het gloedharnas (`harnas3`) helpt |
| een deel dat eraan komt | `ZeldaEiland.Komt.eiland` | een deel van een eiland in `Zelda.KOMT` (de Smidse na `e4a`): grijs op de kaart, niet bij Brecht, Wiske: «… gaat straks open» |
| nieuwe vijanden | `Soort.ASKNECHT`, `VONKSCHUTTER`, `SINTELMIJT` | een zombie met een houweel, een skelet met een boog, een kleine magmaklodder (grootte 1: splitst niet) |
| **de Smidse** (eiland 4b) | | |
| lava | `ZeldaSpel.onLava`, `LAVA_SCHADE`, `Sessie.veilig` | wie in lava stapt, springt er meteen uit naar waar hij veilig stond (geen lava vlakbij), één hart schade, nooit dood, geen brand; `zelda lopen`: in lava sta je niet, over een gat met lava spring je wel |
| lava op een ritme | `ZeldaHoofdstuk.Ritme`, `ritme(…)`, `spel.zetRitme` | stenen die op een ritme onder de lava gaan (`laag` ms droog, `hoog` ms lava), een tel ervoor borrelt het; alleen met iemand in de buurt; de test kijkt of hij stijgt en zakt en of je er veilig uit springt |
| baas en een pijl op een blok, de vuurstaf | `ZeldaBaas.raakBlok`, `ZeldaBaas.vlam` | een baas doet iets met een pijl op een blok in zijn kamer (Asgrims kuipen) of met een vlam (Asgrim laait op) |
| Asgrim | `Asgrim` | zweeft onder vier koelkuipen; een pijl of bom kantelt de kuip: gedoofd (`ZeldaRegels.asgrimSchade`), ploft neer op de rand; gooit sintels (het Gloedschild houdt ze tegen) |
| Gloedschild | `ZeldaSpel.GLOEDSCHILD` | vlag `gloedschild`: het schild in je andere hand heet zo en heeft een eigen wapen (zwart, oranje) |
| deeltjes van ver | `ZeldaSpel.deeltje` | voor één speler, ook ver weg te zien (de Nevelring aan de horizon) |
| nieuwe vijand | `Soort.ASGRIM` | een blaze (Asgrim zelf) |

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
   roept `boem()` van het deel aan. Klauwhaak (gebouwd in 3a): een `Haakpunt` (blok `HAAKPUNT` + landing) is vanzelf te
   gebruiken; zet het zo dat het van een plek om te staan te raken is (het blok mag niet weggestopt zitten achter een
   rand: `zelda lopen` meldt «nergens vandaan te raken»), en zet een `haak(…)` in de volgorde als je er op het pad
   langs moet. Graafplekken (`Graaf`) voor het vosje. Vuurstaf (gebouwd in 4a): een `Reeks` met `Hoe.VUUR` (dingen die
   alleen een vlam aansteekt; zet `voor`, de plek om te mikken: de zelftest kijkt of je elk ding ziet) of een gewone
   vuurschaal (`KLIK`); betoverd ijs is een `Smelt` (blok `ICE`, dicht = ijs, open = lucht of water; `smeltOpPad` als je
   erdoor moet, met `voor`); hitte is een `Hitte` (met een koelplek erin en `terug`). Lava (gebouwd in 4b) mag overal: wie erin stapt, springt eruit;
   lava die stijgt en zakt is een `Ritme` (dicht = lava, open = de stenen); houd hout drie blokken bij lava vandaan.
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
3. **`zelda-eiland-2b`** (gebouwd, kitpvp 2.7) — De Getijdentempel: het Getijdenplein als hub, het wad met het wrak,
   de getijdenpoelen, eb en vloed in de Getijdenzaal (motor `Getij`), de Spoelkamer, het raderwerk, de Ankerpoort
   (sleutel bij Teun), Kapitein Zoutbaard (motor: baas naar keuze), `hart2`, Getijdenbaken (`baken2`, de Witte Toppen
   aangekondigd: `Zelda.KOMT`), Tijmen (maanparel), Roos, Lorre, Lotje (`fles3`). **Klaar als** de kerker te halen is
   met alleen wat 2a geeft, de baas te verslaan is, `baken2` het Witte Toppen opent, er een opdracht terug naar een
   ander eiland is, een stand van 2.6.0 zonder verlies werkt, en zelftest, `test … alles`, `motor` en `zelda lopen`
   OK zijn.
4. **`zelda-eiland-3a`** (gebouwd, kitpvp 2.8) — Witte Toppen dal en dorp: de fjord, IJshoeve, het meer, de storm,
   de kuil, het sneeuwvosje (verhaal-metgezel, naam kiezen, snuffelt en graaft), de Wachterspost met de klauwhaak, de
   Bergpoort; Tijmen (sterrenlantaarn), Saar en Sven, Ys (de brief voor Teun → `fles4`), Hagen, Noor. **Klaar als**
   het vosje meeloopt en snuffelt, de klauwhaak werkt op haakpunten (ook het oude punt op het Lindewoud), en zelftest
   en test OK zijn.
5. **`zelda-eiland-3b`** (gebouwd, kitpvp 2.9) — Het IJspaleis: het bergpad, het voorplein, de IJstuin, de sleebaan,
   glijvloeren, de Spiegelzaal met de zonnesteen, Grimmel, `hart3`, wachtersharnas (Hagen), Vorstbaken (`baken3`, de
   Gloedberg aangekondigd); Ys (de hoorn), Hanne (de sneeuwbloem, `fles5`). **Klaar als** het paleis met klauwhaak en
   bommen te halen is, `baken3` de Gloedberg opent, er een terugkeer-opdracht is, zelftest en test OK.
6. **`zelda-eiland-4a`** (gebouwd, kitpvp 2.10) — Gloedberg: de Zwarte Baai, Sintelgat (hub), de mijnen met hitte en
   koelplekken, Tijmen (koelsteen), Okker, Dina, Fenna, Kobus, Ida; de vuurstaf, het gloedharnas, de Askamer, de
   Smidsepoort (de Smidse aangekondigd); Hagen en de gloedkool (`fles6`). **Klaar als** de vuurstaf vuur op afstand
   aansteekt en ijs smelt (ook op de Witte Toppen), zelftest en test OK.
7. **`zelda-eiland-4b`** (gebouwd, kitpvp 2.11) — De Smidse: de Smidsehal, drie vonken (Kettingzaal, Stuwdam,
   Gietzaal), het Gloedschild van Okker, de Hartdeur, Asgrim, `hart4`, de Schoorsteen, Gloedbaken (`baken4`), de
   Nevelring breekt (Nachtrots aangekondigd); Kees en het bijlblad (`fles7`); motor: lava, `Ritme`. **Klaar als** de Smidse
   te halen is met alle voorwerpen tot nu toe, `baken4` Nachtrots opent, zelftest en test OK.
8. **`zelda-eiland-5a`** — Nachtrots, de muren: Wolf, sluipen, de kerkers, lichtpijlen, lichtharnas, één gevecht.
   **Klaar als** je van de kreek tot de torenpoort komt, lichtpijlen nevel weghalen, zelftest en test OK.
9. **`zelda-eiland-5b`** — De toren en Morgal (drie fases), Zelda bevrijd, de terugvaart en het feest op het
   Lindewoud, vlag `zelda`. **Klaar als** het hele spel van Thuis tot het feest uit te spelen is (`kitpvp zelda test
   <speler> alles` over alle eilanden), zelftest OK.
10. **`zelda-afronding`** — Speel alles na: balans (te veel gevechten? te moeilijk?), alle «later-terug»-plekken
    bereikbaar, twintig hartstukjes, muziek overal, teksten kort, kaartplaatjes. **Klaar als** een volledige
    testronde zonder fouten loopt en de lijst met losse eindjes leeg is.

**Losse eindjes** (gevonden, nog niet opgelost):
- IJshoeve (3a): de schoorstenen van de huisjes lopen door tot y 1 (in `ZeldaEiland3a.huis` begint `nok` op 0, en in de
  vlakke wereld is G negatief). Oplossing: `int nok = Integer.MIN_VALUE;` en `zelda-e3a` versie 3 (dan herbouwt 3a).
  Gezien in sessie `zelda-eiland-4a` (4a had dezelfde code en is opgelost); 3a is niet aangeraakt (buiten het vak).
