# Story of Zelda 2.0 — De Glazen Zee

Ontwerpdocument (oktober 2026). Hoofdstuk 1 «Het Lindewoud» staat live en viel goed ("erg leuk, verrast hoe goed het
is"). Zelda 2.0 maakt er een groter avontuur van: **vijf eilanden** in de Glazen Zee, elk een hub met meerdere dingen
erop, verbonden door het schip de Zilvermeeuw. Dit document is het plan voor de bouwsessies: verhaal, eilanden,
mechanics en de verdeling in sessies. De gedeelde motor is gebouwd (zie «De motor»), eiland 1 ook (sessie
`zelda-eiland-1`, kitpvp 2.4); de andere eilanden bouwen latere sessies, één per (half) eiland, in de volgorde onderaan.

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
| genezen bij elke arena | **bron** (feeënkring) of zielvuur bij elke kamer; de zelftest eist het | motor + zelftest |
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

- **Hoofdstuk 1 / eiland 1 — Het Lindewoud** (bestaat): je leert vechten, puzzelen en schieten, verslaat de moerasheks
  Grauwmoer en jaagt Morgals soldaten van de kade. Brecht neemt je mee aan boord.
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
| 1 Het Lindewoud | `h1` · `zelda` | 1092 … 1592 · −64 … 64 | rust `haven` | — | `h1` |
| 2 Het Zilverstrand | `e2a` · `zelda-e2a`, `e2b` · `zelda-e2b` | 1060 … 1360 · −480 … −180 | rust `steiger` (e2a) | `h1` | `baken2` |
| 3 De Witte Toppen | `e3a`, `e3b` | 1400 … 1720 · −480 … −180 | `steiger` (e3a) | `baken2` | `baken3` |
| 4 De Gloedberg | `e4a`, `e4b` | 1060 … 1400 · 180 … 480 | `steiger` (e4a) | `baken3` | `baken4` |
| 5 Nachtrots | `e5a`, `e5b` | 1640 … 2000 · −150 … 150 | `steiger` (e5a) | `baken4` | `zelda` |
| de zee (overtocht) | `zee` · `zelda-zee` | 1840 … 2040 · −510 … −330 | — | — | — |

Nachtrots ligt achter de silhouet-burcht aan de horizon van het Lindewoud (x 1520 … 1572), zodat je hem vanaf de haven
al ziet liggen. Vrij voor later: x 1450 … 2040, z 180 … 510.

### Bouwregels voor elk eiland

1. **Eerst rondkijken, dan vechten.** Aankomst bij de steiger met een zielvuur, een dorp of plein met personen, en
   pas na een paar minuten verkennen het eerste gevecht. Per half eiland hoogstens **één verplichte kamer** (plus de
   baas in de kerker; de zelftest eist hoogstens twee vóór de baas per deel). Extra gevechten alleen als zijmissie of
   uitdaging (`k.vrijwillig = true`). Wie terugkomt, gaat vrij door een gewonnen kamer; geef elke gewone kamer een
   **bel** (`k.bel`) en een zwaardere **extra** golf (`k.extra(...)`) voor wie nog eens wil.
2. **Genezen bij elke kamer**: een zielvuur of bron binnen 32 blokken van de ingang (zelftest controleert dit).
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
| 1 Lindewoud | zwaard, schild, boog (bestaan) | groene tuniek | `hart1` (Grauwmoer) | `fles1` (zijmissie Hanne) | — |
| 2 Zilverstrand | **bommen** (vak 4, vlag `bommen`): gooien, gebarsten muren, baas verdoven | `harnas1` maliënhemd (smid Hendrik) | `hart2` | `fles2` | — |
| 3 Witte Toppen | **klauwhaak** (vak 5, vlag `klauwhaak`): naar een haakpunt trekken | `harnas2` wachtersharnas (bergwachters) | `hart3` | `fles3` | **sneeuwvos** (`dier:sneeuwvos`) |
| 4 Gloedberg | **vuurstaf** (vak 6, vlag `vuurstaf`): vuur op afstand, ijs smelten | `harnas3` gloedharnas (smeden; hitte in de mijn) | `hart4` | `fles4` | — |
| 5 Nachtrots | **lichtpijlen** (boog, vak 2, vlag `lichtpijlen`): nevel en Morgal | `harnas4` lichtharnas (Zelda's wachters) | — | — | het einde |

De vlag van een voorwerp is vast: de vondsten op eerdere eilanden die erop wachten (`voorwerp = "bommen"`, …) gebruiken
hem al.

Een voorwerp is een `ZeldaVoorwerp` (zie het flesje in `ZeldaSpel.Flesje`), geregistreerd in `Zelda`; de sessie van
dat eiland bouwt het. Harten: 8 aan het begin, +4 containers, +5 uit twintig stukjes = hoogstens 17.

### 1 · Het Lindewoud (gebouwd: sessie `zelda-eiland-1`, deel `zelda` versie 3)

Bos, tempelruïne, kloof, veen, heksenkring, haven (zie `MODI.md` voor de kaart van het pad). Wat de eilandsessie
toevoegde, en de keuzes:

- **Rustiger**: de open plek is één golf van twee veenlopers, het Veen twee golven (was drie), de kade één golf (was
  twee). Verplicht vóór de baas: de open plek en het Veen. Een kamer die je al won (`k:<kamer>`) laat je **vrij door**
  (afwijking van het plan, waar de tweede golf bij terugkomst kwam: steeds opnieuw vechten past niet bij «niet zo
  vaak»); de **bel** in de open plek, het Veen en op de kade = nog eens, nu mét de extra golven. Oude standen krijgen
  de `k:`-vlaggen van wat ze al voorbij zijn (`ZeldaHoofdstuk1.aanvullen`).
- **Kees** de houthakker bij Thuis (houtstapel, leeg hakblok): zijmissie *De verloren bijl*. De bijl ligt op het
  hakblok in de nieuwe **houtkap**, een open plek achter de Oude Eik (pad naar het noorden). Beloning: een
  hartstukje. Daarna verklapt hij dat er iets in de Oude Eik zit.
- **Hanne** onder haar moerashut (ketel, brouwsel): zijmissie *Moeraskruid*: veenbes, zonnedauw en moerasmunt in het
  Veen (pas te zien als ze erom vroeg) → het eerste **flesje** (`fles1`), en Wiske legt uit hoe je het vult.
- **Lotje** (een kind) telt meeuwen aan het eind van de pier; een praatje, en een vraag als je op het Zilverstrand
  was (`e:zilverstrand`). Een missie met de bommen kan eiland 2a erbij zetten.
- **Brecht** op de pier: het eindverhaal (zonder «wordt vervolgd»: «Op het Zilverstrand weten ze meer. Zeg maar
  wanneer.»), daarna praten = `spel.varen`: een klein menu «Waarheen?» met elk eiland dat open is; is er nog maar één,
  dan «Nog even geduld. Zodra de wind draait, varen we.» Komt hij je weer tegen na een ander eiland, dan één
  welkomstregel.
- **Vier hartstukjes** (`hs:lindewoud-1` … `4`): in de **holle Oude Eik** (stam 4 × 4, gat aan de achterkant;
  verborgen, het huisdier snuffelt hem op), de beloning van **Kees**, op een **richel in de Kloof** (oostwand, drie
  blokken boven het water, haakpunt erboven; `voorwerp = "klauwhaak"`) en achter een **gebarsten muur** in het
  heiligdom van de tempel (een kamertje in de zuidmuur; `voorwerp = "bommen"`). Nu te halen: twee. Het stukje onder de
  paddenstoelen van de Heksenkring uit het plan is vervallen (dan waren het er vijf).
- **Uitleg erbij**: de bel (eerste vrije doorgang), het eerste hartstukje, het flesje. Plagerijtjes van Wiske bij de
  richel en de gebarsten muur (verhaal, geen uitleg).
- **De overstap**: zodra eiland 2 in `Zelda.EILANDEN` staat met `openNa = "h1"`, staat het in Brechts menu en op de
  kaart; er hoeft op het Lindewoud niets te veranderen.

### 2 · Het Zilverstrand

Zon, duinen, helmgras, een vissersdorp **Zoutkom** met netten en rokerijen, een vuurtoren zonder licht, en voor de kust
de **Getijdentempel** die bij eb boven water komt.

- **2a (west, het dorp)**: steiger en zielvuur, het dorpsplein (bron), de vuurtoren, de duinen. Personen: **Mira** de
  vuurtorenwachter (hoofdlijn: haar lamp is gestolen door Morgals meeuwen, de vuurtoren wijst de weg naar de tempel);
  **Hendrik** de smid (zijmissie *Zilverschelpen*: vijf schelpen langs het strand → maliënhemd `harnas1`); **Pim** met
  zijn vlieger in de duinen (vrijwillige tijdrace: de vlieger achterna over de duinen); **Ouwe Teun** op het bankje
  (vertelt over de bakens; uitleg). Voorwerp: de **bommenzak** (Mira's oude mijnwerkersbommen, in de kelder van de
  vuurtoren, achter een puzzel met spiegels: licht met de boog op drie doelen). Eén gevecht: de meeuwenrovers op het
  vuurtorenbordes (vrijwillig te herhalen). Later terug: de gebarsten muur in de tempel van het Lindewoud
  (`barst tempel`, hartstukje erachter).
- **2b (oost, de tempel)**: de dijk naar de Getijdentempel; puzzels met **eb en vloed** (hendels die water laten
  zakken en stijgen: deuren van water), gebarsten muren (bommen), een tijdrace onder water door. Kerker met twee
  kamers en de baas **Kapitein Zoutbaard**, een verdronken zeerover die door Morgals nevel ronddoolt: hij gooit
  drietanden; gooi een bom in zijn ton, dan staat hij versuft. Bevrijd = hij bedankt je en vergaat tot zeeschuim.
  `hart2`, het **Getijdenbaken** ontsteekt (`baken2`), Wiske wordt feller. Het Witte Toppen gaat open.

Muziek: *Zoutkom* (vrolijk, gitaar), *Duinen* (wind, fluit), *Getijdentempel* (druppels, ijzer), baas *Zoutbaard*.

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
| hint | `spel.hint`, `/hint`, klik op Wiske | doelen in volgorde; tweede keer binnen 2 min = `verder` + Wiske wijst; geen doel = zijmissie of «verder varen»; zelf aanbieden na 6 min zonder voortgang |
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
| beheer | `kitpvp zelda vaar|vlag|hint <speler> …`, `kitpvp zelda test <speler> motor` | zie MODI.md |

### Hoe een eilandsessie aanhaakt

1. Per half eiland een klasse `ZeldaEiland2a extends ZeldaHoofdstuk` (`id()` `e2a`, `deelId()` `zelda-e2a`, eigen
   `versie()`), in zijn vak (tabel hierboven). In `bouw`: `leeg(b.w)`, `speelgebied`, wereld (alleen als `b.plaats`,
   met chunk-tickets zoals hoofdstuk 1), dan de onderdelen in de volgorde van het pad: `rust`, `bron`, `kamer`,
   `uitdaging`, `reeks`, `vondst`, `verhaal`, `persoon(...).gesprek(...)`, `missie`, en de `doel`en.
   `muziek(Location)` en `plekNaam(Location)` per streek, `hemel(Location)` voor de tijd van de dag.
   Personen: een **Brecht** bij je steiger met `gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.varen(p))`
   (dan kun je terugvaren). Gewone kamers: een `bel` en een `extra` golf. Plekken voor later: `Barst`/`Haakpunt` en
   vondsten met `voorwerp`. Hartstukjes als beloning: `stukje(vlag)`.
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
7. Bommen (2a): roep `spel.breek(p, b)` aan als een bom ontploft bij `spel.barstBij(plek)`; het Lindewoud heeft er een
   in de tempel (`barst tempel`, met `hs:lindewoud-4` erachter). Klauwhaak (3a): trek naar `spel.haakpunt(blok)` en
   zet de speler op `landing()`; het Lindewoud heeft er een boven de richel in de Kloof (`hs:lindewoud-3`).

## De bouwsessies (in volgorde)

Elke sessie: één (half) eiland, de regels hierboven, eigen code alleen in `plugins/kitpvp`, testen volgens de
lichte-testregel, `MODI.md`/`BOT.md` bijwerken, pluginversie omhoog, niet deployen.

1. **`zelda-eiland-1`** (gebouwd, kitpvp 2.4) — Het Lindewoud als eiland: rustiger begin (lichtere eerste kamer en
   kade), Hanne (*Moeraskruid* → `fles1`), Kees (*De verloren bijl* → hartstukje), Lotje, Brecht met gesprekken en de
   overstap naar eiland 2 (alleen als het bestaat), vier hartstukjes (twee nu te halen). **Klaar als** het hoofdstuk
   van begin tot eind speelt met hoogstens twee verplichte kamers vóór de baas, er twee zijmissies werken, `kitpvp
   zelftest` OK is en `kitpvp zelda test <speler> alles` en `motor` OK zijn, en bestaande voortgang blijft werken.
2. **`zelda-eiland-2a`** — Zilverstrand west: steiger, Zoutkom, vuurtoren, duinen, Mira/Hendrik/Pim/Teun, de
   bommenzak (voorwerp + puzzel, vlag `bommen`), maliënhemd, één gevecht, eiland in `Zelda.EILANDEN` (open na `h1`).
   **Klaar als** je van het Lindewoud kunt varen (Brecht of kaart), alles op 2a te doen is, bommen gebarsten muren
   openen (ook die in de tempel van het Lindewoud), en zelftest en test OK zijn.
3. **`zelda-eiland-2b`** — De Getijdentempel: eb-en-vloedpuzzels, kerker, Kapitein Zoutbaard, `hart2`, Getijdenbaken
   (`baken2`). **Klaar als** de kerker te halen is met alleen wat 2a geeft, de baas te verslaan is, `baken2` het Witte
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
