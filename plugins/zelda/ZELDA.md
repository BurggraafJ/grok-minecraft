# Story of Zelda 2.0 — De Glazen Zee

Ontwerpdocument (oktober 2026). Hoofdstuk 1 «Het Lindewoud» staat live en viel goed ("erg leuk, verrast hoe goed het
is"). Zelda 2.0 maakt er een groter avontuur van: **vijf eilanden** in de Glazen Zee, elk een hub met meerdere dingen
erop, verbonden door het schip de Zilvermeeuw. Dit document is het plan voor de bouwsessies: verhaal, eilanden,
mechanics en de verdeling in sessies. De gedeelde motor is gebouwd (zie «De motor»), eiland 1 ook (sessie
`zelda-eiland-1`, kitpvp 2.4; en opnieuw als **open eiland** in sessie `zelda-lindewoud-v2`, kitpvp 2.6) en heel eiland 2
(de westhelft in sessie `zelda-eiland-2a`, kitpvp 2.5; de Getijdentempel in sessie `zelda-eiland-2b`, kitpvp 2.7), heel
eiland 3 (het dal in sessie `zelda-eiland-3a`, kitpvp 2.8; het IJspaleis in sessie `zelda-eiland-3b`, kitpvp 2.9) en heel
eiland 4 (Sintelgat en de mijnen in sessie `zelda-eiland-4a`, kitpvp 2.10; de Smidse in sessie `zelda-eiland-4b`, kitpvp
2.11: de Nevelring is gebroken) en heel eiland 5 (Nachtrots, de muren, in sessie `zelda-eiland-5a`, kitpvp 2.12; de toren en
Morgal in sessie `zelda-eiland-5b`, kitpvp 2.13: Zelda is vrij, het feest op het Lindewoud, het verhaal is uit), en de
kaart in vak 9 (sessie `zelda-kaart`, kitpvp 2.14: het eiland van boven en de zeekaart, zie «De kaart»), en de afronding
(sessie `zelda-afronding`, kitpvp 2.15: naspelen, losse eindjes, zie «De afronding»), en niveaus, samen spelen en
meerdere spellen (sessie `zelda-moeilijkheid`, kitpvp 2.16, zie «Niveaus, samen spelen en meerdere spellen»). **Terugkerende personages** (Tijmen de wegwijzer, Brecht, Hanne, …) knopen de eilanden aan
elkaar: zie «Terugkerende personages».

**Sinds sessie `zelda-apart` (Zelda 1.0.0, kitpvp 2.17) is Story of Zelda een eigen spel**: een eigen plugin
(`plugins/zelda`), een eigen wereld en een eigen poort in de lobby. Zie «Een eigen spel» hieronder. Alles daarvóór in dit
document noemt nog kitpvp-versies: zo heette het toen.

Techniek en klassen: `TECHNIEK.md` (stond tot 2.16 in `plugins/kitpvp/MODI.md`) en de javadoc van `Zelda*.java` (begin bij
`ZeldaPlugin` en `Zelda`).

## Een eigen spel (sessie `zelda-apart`, Zelda 1.0.0)

Jelle 30-10: *"Story of zelda word een aparte portal. Dus apart ervan maken aub met eigen ding zodat het voelt alsof het iets
anders is dan kitpvp…."*

**De vorm, en waarom.**
- **Een eigen plugin `Zelda`** (`plugins/zelda`, pakket `nl.burggraaf.mc.zelda`): alle `Zelda*.java`, de bazen en de tests
  verhuisden uit kitpvp (met `git mv`, de geschiedenis blijft). KitPvP weet niets meer van Zelda.
- **Een eigen wereld `zelda` op de kitpvp-server**, niet een eigen backend. Een extra backend kost geld of een handeling van
  Jelle (Neptune heeft maar twee splits, beide in gebruik; kitpvp is al een losse server van 2 GB). Een eigen wereld kost
  bijna niets extra: alleen de chunks rond spelers zijn geladen, net als toen Zelda in de kitpvp-wereld lag. Dezelfde regels
  als kitpvp (moeilijkheid *easy*: de schade is daarop afgesteld; geen tijd, weer of mobs; meteen respawnen; geen
  doodsberichten), die zet de plugin zelf.
- **De eilanden liggen op dezelfde coördinaten** (x 1024…2047, z −512…511). Daardoor kon de migratie gewoon de vier
  regiobestanden overzetten: er wordt niets herbouwd en alle code met coördinaten bleef gelijk.
- **Een eigen poort in de lobby**: een oude, bemoste ruïne van een boog, midden achter Survival en KitPvP (waar de spar
  stond), met een groen licht in de sluitsteen, klimop en stapstenen door het tulpenperk. Erboven «Story of Zelda» in de
  kleur van de Glazen Zee (`#7fd1c4`, ook de deeltjes). In het stermenu: Survival · Story of Zelda · KitPvP · Parkour, de
  brink eronder.
- **Binnenkomen zonder KitPvP te zien**: de lobby zet het cookie `burggraaf:plek` = `zelda` en stuurt je naar de
  kitpvp-server. Die leest het cookie terwijl je nog laadt (`AsyncPlayerSpawnLocationEvent`), dus je verschijnt meteen in de
  Zelda-wereld en het spel zet je op je laatste rustplaats. Nooit eerst de KitPvP-hub. Zonder cookie (bv. `/terug` met een
  oude proxy) kom je terug waar je wegging; wie onder kitpvp 2.16 in Zelda uitlogde (oude wereld, Zelda-gebied), komt ook in
  Zelda. Komt het cookie niet op tijd (1 s), dan kijkt de plugin na het binnenkomen nog eens en zet je alsnog goed.

**Een eigen identiteit, overal.**

| waar | KitPvP | Story of Zelda |
|---|---|---|
| sidebar | ⚔ KitPvP: Kit, Kills · Doden, Reeks, Plek | «Story of Zelda»: waar je bent, hartstukjes x / 20, donkergrijs «Lobby: vak 8» |
| tablijst | `K Boer Jelle` | `Z Boer Jelle` (opmaak `wereld-zelda-tablijst`; op de andere servers de proxy, plek `zelda`) |
| chat | `[KitPvP] …` | `[Zelda] …` (opmaak `wereld-zelda-server-tag`) |
| wissel | `» naam → [KitPvP]` | `» naam → [Zelda]`; zelf zie je alleen de regel van het spel (eiland · rustplaats · vak 8 = lobby) |
| vak 8 | «Terug naar de hub» | «Naar de lobby» (herstelkompas; ook onderaan de kaart) |
| /help | /modus · /kit · /arena · /stats | /hint, de kaart, vak 8 (opmaak `wereld-zelda-hulp`) |
| commando's | /kit /modus /arena /stats | /zelda (naar Zelda; met beheer: zoals altijd), /hint |
| huisdier | gaat mee | nooit (`kitpvp-stand` = `geen`) |

Typt iemand in Zelda toch `/arena`, `/kit` of `/modus`, dan gaat hij naar de KitPvP-hub (dat vroeg hij).

**Zelda en KitPvP op één server.** Elke plugin bemoeit zich alleen met spelers in zijn eigen wereld. Uit Zelda naar de
KitPvP-wereld (`/kitpvp` via de proxy, `zelda uit <speler>`): Zelda ruimt eerst op (spullen, harten, muziek, sidebar), dan zet
KitPvP je in de hub. Andersom: `ZeldaPlugin.binnen` (lobbypoort, `/zelda`, beheer, de test).

**De proxy (plugin netwerk 1.1).** Zelda is daar een *plek* op kitpvp (`config.json` → `plekken`: `zelda` → `kitpvp`). De
backend meldt waar je bent (`plek`); daarmee: letter Z, tag [Zelda], `/spelers`, `/terug`, het terugkeren na inloggen
(«Naar Story of Zelda in 3») en na een herstart van kitpvp. `/zelda` = erheen (met het cookie; ben je al op kitpvp, dan
wisselt de backend van wereld: `plek-naar`). `/zelda beheer` en zo gaan door naar de server. Zonder de nieuwe proxy werkt
alles ook (de lobbypoort zet het cookie zelf); alleen ziet iedereen dan nog «→ [KitPvP]» en een K.

**De KitPvP-hub** houdt al zijn arena's en modi. De westplek (de grote Zelda-poort) is nu een dichtgemetselde, bemoste
boog met een bordje: «Story of Zelda · heeft nu een · eigen poort · in de lobby» (hub versie 2).

**De verhuizing van de voortgang** (`ZeldaMigratie`, eenmalig bij de eerste start van Zelda 1.0.0, idempotent):
- `plugins/KitPvP/zelda.yml` (alle spellen, niveaus, hartstukjes, de kaart, samen-spellen), `zelda-kaarten.yml` en
  `zelda-kaart/` → `plugins/Zelda/`. Eerst een backup in `plugins/Zelda/migratie/kitpvp-<tijd>/`; de oude bestanden blijven
  staan. Staat er al een `plugins/Zelda/zelda.yml`, dan wordt niets overschreven.
- De vier regiobestanden (`region`, `entities`, `poi`: `r.2.-1`, `r.2.0`, `r.3.-1`, `r.3.0`) van de kitpvp-wereld → de
  Zelda-wereld (nooit over een bestaand bestand heen), en de bouwversies van de Zelda-delen en de grondhoogte naar
  `plugins/Zelda/gebouwd.yml`. Het oude gebied blijft in de kitpvp-wereld liggen (niemand komt er; handig om terug te vallen).
- Wat er gebeurde staat in `plugins/Zelda/migratie.yml`; `zelda migratie` laat het zien.

**Commando's** (console `zelda …` = vroeger `kitpvp zelda …`; `kitpvp zelda …` werkt nog en stuurt door):
- spelers: `/zelda` (erheen), `/hint`;
- beheer in de game (OP, `zelda.beheer`): `/zelda`, `/zelda beheer [aan|uit]`, `/zelda ga <plek>`, `/zelda plekken`,
  `/zelda reset <speler> [spel]`, bijsturen (`spelers`, `niveau`, `steun`, `volg`, `doden`, `spel`, `samen`), en alles van de
  console;
- console: `zelda zelftest`, `zelda status`, `zelda test <speler> [alles …|dood|motor|niveau|spellen]`, `zelda lopen <eiland>`,
  `zelda in|uit <speler>`, `zelda reset <speler> [spel]`, `zelda verder|rust|vaar|vlag|hint|beheer <speler> …`,
  `zelda kaart [<speler>|opnieuw]`, `zelda bouw [deel|alles]`, `zelda plaatjes [begin]`, `zelda migratie`.

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
| "difficulty level, met hoeveel players, meerdere journeys naast elkaar; niet sneller door een arena, wel sneller helen, meer armor, extra potions; ik wil kunnen bijsturen" (28-10) | vier **niveaus** die alleen de hulp veranderen (kaart: «Niveau», ook midden in een gevecht), **sterren** en de **heldenrand** voor wie het hoog houdt, **hulp na doodgaan**; samen net iets zwaarder; drie **spellen** per speler en een **samen-spel**; **bijsturen** voor beheer (niveau, steun, wie vastloopt) | `ZeldaRegels.Niveau`, `ZeldaSpellen`, `ZeldaBijsturen`, zie «Niveaus, samen spelen en meerdere spellen» |
| "een kaart zoals in Legend of Zelda, om het eiland te overzien" (28-10) | vak 9 is een **echte kaart**: vasthouden = het eiland van boven, met jou (een pijl), de zielvuren waar je was, dorpen en kerkers, medespelers en je volgende doel (een rode X, dezelfde bron als `/hint`); wat je nog niet zag ligt onder perkament; linksklik = de **zeekaart** (alle eilanden, de vaarroutes, op zee je schip) | motor (`ZeldaKaart`), zie «De kaart» |

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
| 5 Nachtrots | `e5a` · `zelda-e5a`, `e5b` · `zelda-e5b` | 1640 … 2000 · −150 … 150 (5a tot x 1879, 5b vanaf 1880) | rust `kreek` (e5a) | `baken4` | `zelda` (het feest, op het Lindewoud) |
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
| 5 Nachtrots | **lichtpijlen** (vak 7, vlag `lichtpijlen`; ook de boog schiet licht): nevel, nevelbruggen en Morgal | `harnas4` lichtharnas (het zwarte harnas van Zelda's wachters, Mira maakt het schoon) | — | `fles8` (Hanne, het maanmos) | `muursleutel` (Wolf), `sleutelbos`, `wachtwoord` (Wolf), `zwartharnas`; 5b: `zegel` (Tijmen breekt het), `vrij:zelda`, `kroontje` (na het einde), het einde `zelda` |

De vlag van een voorwerp is vast: de vondsten op eerdere eilanden die erop wachten (`voorwerp = "bommen"`, …) gebruiken
hem al. Flesjes: de nummers zijn alleen namen (`ZeldaRegels.flessen` telt `fles1` … `fles8`); het Lindewoud geeft er
sinds versie 4 twee (Tijmen meteen `fles2`, zodat je vanaf het begin kunt genezen), dus 2b geeft `fles3` en de Witte
Toppen `fles4`. Sinds 3b is er een vijfde: Hanne brouwt het van een sneeuwbloem uit de IJstuin (`fles5`, een
boodschap over zee; Jelle wil overal kunnen genezen). Sinds 4a een zesde: de veldfles van Hagen, voor een gloedkool van
Okker (`fles6`). Sinds 4b een zevende: de oude veldfles van Kees op het Lindewoud, voor een bijlblad uit de Smidse
(`fles7`). Sinds 5a een achtste: Hanne brouwt het van maanmos uit de kerkers van Nachtrots (`fles8`).

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
  kloof (behalve terug door de gang) en niet op de Bergpoort (`zelda lopen witte-toppen dicht wachtersdeur
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
  (`zelda lopen gloedberg zonder klauwhaak dicht daaltrap dicht asgang`).
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
  het baken niet; zonder drie vonken en het Gloedschild gaat de Hartdeur niet open (`zelda lopen gloedberg dicht
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
  iedereen die je hielp. Vlag `zelda`; daarna «Story of Zelda · voltooid» en alles blijft open. Gebouwd: zie «5b · de toren
  en Morgal» hieronder.

Muziek: *Nachtrots* (dreigend, laag), *Toren* (spanning), baas *Morgal* (drie delen), *Thuis* in feestversie (*Feest*).

#### 5a · de muren (gebouwd: sessie `zelda-eiland-5a`, deel `zelda-e5a` versie 1, klasse `ZeldaEiland5a`)

Een half eiland met twee hubs: de kreek (buiten) en de kerkerhal (binnen). Vak **x 1640 … 1879, z −150 … 150**; het vak van
de toren (5b) is **x 1880 … 2000**: zolang 5b er niet is, bouwt 5a daar de rots met de toren als decor (`torenErbij`, zoals 4a
de kegel van 4b). Hoogtes: G = zeeniveau + 1, **K = G+2** (de kerkers), **P = G+14** (het plateau, de burcht), **W = P+10**
(de weergang). Op Nachtrots is het altijd nacht (Morgal), onweer rommelt in de verte.

| plek | wat | soort |
|---|---|---|
| 1686 … 1727, −11 … 11 | **de visserskreek** (de hub): een steiger met de Zilvermeeuw, **Brecht**; drie verlaten vissershuisjes, **Tijmen** verstopt onder een afdakje («Sst!»); zielvuur, bron; bordjes (De klippen, Het Vissersgat, De Zilvermeeuw); boven op de klif de Kreektoren | rust 0 `kreek`, bron, personen |
| 1711 … 1713, −14 … −10 | **Wolfs raampje**: een richel onder een gat in de rotswand; achter het raam zit **Wolf**, een wachter van Zelda: hij geeft de **muursleutel** en vertelt hoe je binnenkomt | persoon (praten op het pad) |
| 1723 … 1747, 12 … 44 | **de klippen**: een trap uit de kreek, een richel, een kloof (water onderin, een ladder terug) met een **haakpunt** op een pilaar, een spleet (verstopt hartstukje), en de **smokkelgang** achter een gebarsten muur, omhoog naar de muurvoet | haakpunt `klippen`, vondst `hs:nachtrots-2` (verborgen), barst `smokkelgang` |
| 1742 … 1767, 40 … 62 | **de muurvoet**: zielvuur; de **muurdeur** onder in de SW-toren (open met de muursleutel); een wenteltrap naar de weergang | rust 1 `muurvoet`, poort `muurdeur` |
| 1768 … 1772, −17 … 45 | **de weergang**: vier **nachtwachten** met een lantaarn lopen op een vast ritme heen en weer (14 s); vier **balkonnetjes** aan de binnenkant om je te verstoppen; aan het eind de Kerkertoren (het hek) | sluipen `weergang` |
| 1765 … 1775, −28 … −18 | **de Kerkertoren**: een wenteltrap van de weergang diep naar beneden, de kerkers in | — |
| 1760 … 1776, −30 … −12 | **de kerkers** (hub binnen): zielvuur, bron; west de cellengang, zuid de zuidgang, oost de nevelgang (nevel) | rust 2 `kerkerhal`, bron, nevel `nevelgang` |
| 1709 … 1758, −26 … −14 | **de cellengang**: drie **cipiers** met een lantaarn (hetzelfde ritme), drie nissen om je te verstoppen; cellen aan beide kanten met **gevangenen van alle eilanden**: **Wolkje** (het schaap van Gerrit, Lindewoud), **Bas** (de leerjongen van Hendrik, Zoutkom), **Kiek** (een geitje van Saar en Sven, IJshoeve), **Joppe** (de zoon van Dina, Sintelgat); kleine verrassingen in de lege cellen (een hoed van Zoutbaard, een tekening van jou en Wiske, streepjes op de muur); het **maanmos** voor Hanne; aan het eind **Wolfs cel** | sluipen `kerkerwacht`, poorten `cel-…`, personen, vondst `maanmos` |
| 1729 … 1741, −17 … −8 | **de wachtkamer**: de kerkermeester slaapt (en snurkt); op zijn tafel de **sleutelbos** | vondst `sleutelbos` |
| 1704 … 1706, −21 … −12 | **het Vissersgat**: een oude gang van de cellengang naar de kreek; het hek gaat alleen open voor wie in de kerkers was (de korte weg terug) | poort `vissersgat` |
| 1761 … 1789, 5 … 31 | **de kapel van Zelda's wachters** (wit steen, zwart van de nevel): achter een gebarsten muur (zuidgang); een put (water, een ladder terug); haak je naar het altaar, steek de drie vuren aan (vuurstaf, 20 s): de **lichtpijlen**; de deur oost zit dicht met nevel (de eerste lichtpijl) | barst `kapelmuur`, haakpunt `kapel`, vuurpuzzel `kapelvuren`, vondst `lichtpijlen`, nevel `kapeldeur` |
| 1790 … 1806, −24 … 27 | **de wachtersgang** en de nevelgang terug naar de kerkers (een rondje) | nevel `nevelgang` |
| 1782 … 1793, −16 … −4 | **de wapenkamer** (achter nevel): het **zwarte harnas** van Zelda's wachters; in de noordwand een kist achter nevel | nevel `wapenkamer`, vondst `zwartharnas`, nevel `wapenkist`, vondst `hs:nachtrots-3` |
| Zoutkom (2a) | **Mira** zet het harnas in het licht van haar vuurtoren: het **lichtharnas** (`harnas4`) | praten (Mira, op het pad) |
| 1801 … 1807, −31 … −25 | **de kerkertrap** (achter nevel): een wenteltrap omhoog naar het wachthuis | nevel `kerkertrap` |
| 1798 … 1812, −38 … −24 | **het wachthuis** (leeg): zielvuur, bron; de **binnenpoort** gaat open met het wachtwoord van Wolf («Maanloos») én het lichtharnas | rust 3 `wachthuis`, bron, poort `binnenpoort` |
| 1800 … 1840, −20 … 20 | **de binnenplaats**: Morgals soldaten (nachtsoldaat, nachtschutter, nevelmijt: drie gemengde golven; de bel = nog eens, met de wachtmeester) — het enige gevecht | kamer `binnenplaats` |
| 1769 … 1799, −2 … 2 | na de binnenplaats: de westpoort, de voorburcht en de **hoofdpoort** naar buiten (een rondje terug naar de muurvoet en de klippen) | poorten `westpoort`, `hoofdpoort` |
| 1842 … 1858, −3 … 3 | **de torenpoort**: zielvuur, **Tijmen**; de poort zit dicht met Morgals zegel (de toren is 5b) | rust 4 `torenpoort`, verhaal: einde (`e5a`, vereist `k:binnenplaats`) |

- **Hoofdlijn** (veertien doelen voor `/hint` en Tijmen): Tijmen → Wolf (de muursleutel) → de klippen → de weergang → de
  kerkers → **in elke volgorde** (doelgroep `kerker`) de sleutelbos, de lichtpijlen, Wolf bevrijden en de wapenkamer → Wolf
  in de kreek (het wachtwoord) → het lichtharnas (Mira) → de kerkertrap → de binnenplaats → de torenpoort. `wacht()`: «De
  torenpoort zit nog dicht. Nog even geduld.»
- **Rondjes**: kreek → klippen → muurvoet → weergang → kerkers → Vissersgat → kreek; kerkers → zuidgang → kapel → wachtersgang
  → nevelgang → kerkers; na de binnenplaats binnenplaats → voorburcht → hoofdpoort → muurvoet → klippen → kreek. Wat de
  volgorde bewaakt: de muurdeur (Wolf), de weergang (de enige weg naar binnen tot je in de kerkers was), de nevel
  (lichtpijlen), de binnenpoort (wachtwoord en lichtharnas). `zelda lopen nachtrots dicht muurdeur dicht vissersgat
  dicht hoofdpoort dicht westpoort` laat zien dat er geen andere weg is.
- **Sluipen** (motor `ZeldaHoofdstuk.Sluip`): een wachter ziet wat in zijn lichtkegel komt (6 blokken, 32° opzij, niets
  ertussen); de kegel staat als warme stipjes op de vloer en houdt op waar het licht tegen iets schijnt. Gezien = een alarm,
  het hek slaat even dicht en je staat weer bij het zielvuur (geen gevecht); Wiske zegt één regel (de eerste, tweede, derde
  keer iets anders). Bukken helpt (dan zien ze je pas van dichtbij). Wie de finish haalt, is erlangs (`l:<id>`). Elke
  wachter loopt van 6 vóór tot 5 achter zijn schuilplek; buren lopen een kwart ritme verschoven: wacht in een balkon of nis
  tot hij langs is en loop achter hem aan. De unit-test en de zelftest rekenen uit dat elk stuk tussen twee schuilplekken
  ongezien te lopen is (de unit-test met een model: op minstens een vijfde van het ritme; de zelftest met de echte blokken:
  minstens 5 %). Na de binnenplaats zijn de wachters gevlucht.
- **Lichtpijlen** (vak 7, vlag `lichtpijlen`, `ZeldaSpel.Lichtpijlen`): rechtsklik = een pijl van licht, en ook de boog
  schiet nu licht (de **Lichtboog**). Waar een lichtpijl Morgals nevel raakt (`ZeldaHoofdstuk.Nevel`, blok `TINTED_GLASS` met
  zwarte deeltjes), verdwijnt die; wie hem wegschoot (`n:<id>`) vindt hem weg, zonder iemand in de buurt kruipt hij na een
  minuut terug. Een nevelmijt krijgt van licht het dubbele. Ook de **nevelnis in de Smidse** (4b, `hs:gloedberg-4`) is nu
  echte nevel: schiet hem weg, dan is het hartstukje te pakken.
- **Personen**: Brecht, Tijmen (kreek; na de binnenplaats bij de torenpoort), Wolf (in zijn cel; na zijn bevrijding in de
  kreek, met Tijmen die zijn arm verbond), de kerkermeester (slaapt), Wolkje, Bas, Kiek en Joppe.
- **Zijmissies**: *De gevangenen* (de sleutelbos, de vier cellen, terug naar Wolf → `hs:nachtrots-1`); **terugkeer** *Het
  zwarte harnas* (Mira, op het pad → `harnas4`); **boodschap over zee** *Het maanmos* (Hanne op het Lindewoud vraagt erom
  als je op Nachtrots was; het groeit in een lege cel → een achtste flesje, `fles8`). En **thuis**: wie je bevrijdde, staat
  weer op zijn eiland (Wolkje in de weide van Gerrit, Bas bij Hendrik, Kiek in de stal, Joppe bij Dina), en wie op hem
  wachtte zegt iets (Dina geeft je een broodje: helemaal beter).
- **Vechten**: alleen de binnenplaats, pas met het lichtharnas (bescherming vooraf) en met een zielvuur en een bron in het
  wachthuis vlak voor de poort. Nieuwe vijanden: de **nachtsoldaat** (vindicator met een stenen bijl), de **nachtschutter**
  (pillager met een kruisboog), de **nevelmijt** (een endermite: klein, snel, niet taai) en de **wachtmeester** (alleen met
  de bel).
- **Genezen**: vijf zielvuren, bronnen in de kreek, de kerkers en het wachthuis, de flesjes, op adem komen.
- **Hartstukjes**: `hs:nachtrots-1` (Wolf, de gevangenen), `-2` (verstopt op de klippen, het vosje snuffelt hem op), `-3`
  (de kist in de wapenkamer, achter nevel); `-4` is voor 5b.
- **Muziek**: *Nachtrots* (buiten: bas, fluit, golven), *Muren* (de weergang, het wachthuis, de binnenplaats: op de tenen),
  *Kerkers* (druppels, sleutels) en de gevechtslus *Nachtwacht*; *Gezien* als je gezien wordt. **Hemel**: late avond, met de
  lichtpijlen de nacht, na de binnenplaats middernacht.
- **Afwijkingen van het oude plan, en waarom**: Wolf zit zelf nog vast (achter het raampje): hij helpt je binnen met de
  sleutel en zijn raad, en je bevrijdt hem in de kerkers (zo is de cel aan het eind van de gang een doel). Er zijn twee
  sluipstukken (de weergang en de cellengang): het tweede is dezelfde truc binnen, met de beloning ervoor (de sleutelbos).
  «Mira's lamp» uit het plan is Mira's vuurtoren geworden: zij maakt het zwarte harnas schoon (de terugkeer-opdracht op de
  hoofdlijn, zoals Hagen in 3b en Okker in 4b), en het lichtharnas is de bescherming vóór de binnenplaats. Tijmen heeft geen
  nieuwe gave (de lichtpijlen liggen in de kapel): hij zit verstopt in de kreek, wijst je naar Wolf en verbindt later Wolfs
  arm. De lichtpijlen zijn zowel een voorwerp in vak 7 (rechtsklik) als licht in de boog: kinderen hoeven niet te spannen.

Muziek van het hele eiland: *Nachtrots*, *Muren*, *Kerkers*, *Nachtwacht*, *Gezien* (5a); *Toren*, baas *Morgal* (drie delen),
*Dageraad* en op het Lindewoud *Feest* (5b).

#### 5b · de toren en Morgal: het einde (gebouwd: sessie `zelda-eiland-5b`, deel `zelda-e5b` versie 1, klasse `ZeldaEiland5b`)

Het tweede halve eiland van Nachtrots: Morgals toren op een eigen rots oost van de burcht, en het einde van het verhaal.
Vak **x 1880 … 2000, z −150 … 150** (elke kolom zelf: zee, twee pijlers, de torenrots; 5a bouwt hier niets meer). De toren
is vierkant (x 1930 … 1950, z −10 … 10, midden 1940, 0), van het plateau (**P** = G+14, net als de burcht) tot het dak
(P+53). Je beklimt hem **buiten**, om de muur heen, in de nevel: elke kant een ander voorwerp. Morgals nevel hangt eromheen
(deeltjes), tot hij verslagen is.

| plek | wat | soort |
|---|---|---|
| 1842 … 1858, −3 … 3 (5a) | de torenpoort: **Tijmen** kent een oude spreuk en breekt Morgals zegel (vlag `zegel`) | praten (op het pad), poort `torenpoort` |
| 1859 … 1874, −2 … 2 (5a) | **de brugpoort**: een overdekte gang door de muur en de rots tot aan de klif (zo kom je er alleen door de torenpoort) | — |
| 1873 … 1908, −1 … 1 | **de Nevelbrug**: drie stukken brug boven zee, verstopt in nevel (je ziet alleen zwarte mist); een lichtpijl die erdoorheen vliegt laat het stuk zien, blok voor blok. Twee oude pijlers ertussen (1883 … 1885, 1896 … 1898) | nevelbruggen `brug1` … `brug3` |
| 1909 … 1927, −7 … 7 | **de voorhof** (de hub van de toren): zielvuur, bron, bordjes (De Nevelbrug, De toren) | rust 0 `voorhof`, bron |
| 1932 … 1948, −8 … 8 | **de torenhal** (binnen, de begane grond): leeg, met **Ida** (ze klom de rots op vanuit zee: «de trap is buiten») en een bron; een trap omlaag naar een hek | persoon, bron |
| 1933 … 1939, −5 … 5 (P−6) | **Morgals schatkamer**: goud, kisten, **Zelda's kroontje** en `hs:nachtrots-4`; het hek gaat pas open als Zelda erom vraagt (na het einde) | poort `schatkamer`, vondsten |
| west, 1928 … 1929, z 10 … 1 | **de westtrap**: een trap van licht in de nevel (tien treden, P+1 … P+10); daarna een richel en de overloop (noordwest, P+10) | nevelbrug `trap1` |
| noord, z −12 … −11 | **de ingestorte trap**: vier treden en dan niets; hoog boven de richel aan de overkant een **haakpunt** (alleen vanaf de trap te raken) → de noordrichel (P+20) | haakpunt `torenrichel` |
| 1943 … 1948, −9 … −4 (P+20) | **Zelda's kamertje** (achter een deurtje in de noordmuur): haar bed, haar tekeningen (de bakens en een fee), een bron; haar raam brandt (van ver te zien) | verhaal, bron |
| oost, 1951 … 1952, z −10 … −1 | **drie wachtersvuren** op witte zuilen naast de muur: alle drie aan met de vuurstaf (20 s) en de **trap van licht** verschijnt (P+21 … P+30) | vuurpuzzel `torenvuren` |
| zuid, 1947 … 1950, z 11 … 12 (P+30) | **puin** op de richel (gebarsten): een bom; daarachter weer een trap van licht (P+31 … P+40) | barst `torenpuin`, nevelbrug `trap2` |
| 1920 … 1929, 9 … 17 (P+40) | **het torenbalkon**: de laatste rust (zielvuur, bron), **Tijmen** («sla zijn bollen terug; zijn hart: alleen licht»); een **ladder** langs het balkon naar beneden (het hek onderaan gaat alleen open voor wie boven was: een rondje) | rust 1 `balkon`, bron, persoon, poort `torenladder` |
| 1932 … 1948, −8 … 8 (P+40) | **de troonzaal**: Morgal (drie fases, zie hieronder); een ladder naar het dak (het luik gaat open als hij verslagen is) | kamer `nachttroon` + baas |
| het dak (P+53) | **Zelda** in een kooi van nevel: hij lost op, de zon komt op (`vrij:zelda`); Zelda gaat vooruit naar Brecht | poort `kooi`, persoon, verhaal: einde (`e5b`) |
| de kreek (5a) | **Zelda** wacht bij Brecht; Brecht: «Prinses Zelda, aan boord!» en hij vaart naar het Lindewoud | praten |
| op zee | **de terugvaart**: Zelda aan dek, de drie bakens branden, het lied van thuis | (een bijzondere overtocht) |
| het oefenveld van het Lindewoud (1240, −60) | **het feest op de open plek**: iedereen die hielp; Zelda in het midden: praten = het einde (`zelda`), vuurwerk, «Story of Zelda · voltooid» | personen, verhaal, doel |

- **Hoofdlijn** (tien doelen voor `/hint` en Tijmen, na die van 5a; en één op het Lindewoud): Tijmen breekt het zegel → de
  Nevelbrug (lichtpijlen) → de westtrap (lichtpijl) → de ingestorte trap (klauwhaak) → de wachtersvuren (vuurstaf) → het puin
  (een bom) → de zuidtrap (lichtpijl) → het balkon → Morgal → Zelda op het dak → naar huis (Brecht in de kreek, of de kaart) →
  het feest (praat met Zelda). Wat de volgorde bewaakt: het zegel (Tijmen, na de binnenplaats), de nevel (lichtpijlen), de
  haak (de klauwhaak; het haakpunt hangt zo hoog dat je het alleen vanaf de ingestorte trap raakt), de vuren en het puin, en
  de ladder (alleen open voor wie boven was). `zelda lopen nachtrots dicht torenpoort` laat zien dat je er alleen door
  de poort komt; `… dicht trap1 dicht torenladder`, `… zonder klauwhaak dicht torenladder` en `… dicht trap2 dicht
  torenladder` dat elke kant nodig is.
- **Rondjes en zijwegen**: voorhof → de trap om de toren → balkon → de ladder → voorhof; de torenhal (Ida) en Zelda's
  kamertje (een bron halverwege) liggen ernaast; na het einde de schatkamer. Terug naar de kreek: de Nevelbrug, de burcht
  (binnenplaats, voorburcht, hoofdpoort, muurvoet, klippen), of de kaart (van zielvuur naar zielvuur).
- **Nevelbruggen** (motor, `Nevel.brug`): een brug of trap van wit steen die in Morgals nevel verstopt zit (dicht = lucht met
  zwarte mist, open = de brug). Een lichtpijl hoeft nergens tegenaan te komen: vliegt hij door de nevel, dan verschijnt het
  stuk, blok voor blok vanaf de kant van de schutter. Wie hem zag (`n:<id>`), vindt hem voortaan; een minuut niemand =
  weer nevel. Wiske legt het één keer uit (op de brugpoort).
- **Het vangnet** (motor, `Val`): rond de toren en boven zee vangt de nevel je op. Wie valt (meer dan een paar blokken) of in
  zee belandt, staat meteen weer waar hij het laatst veilig stond, zonder schade (Wiske zegt het één keer). Zo is een trap
  op veertig hoog geen gevaar voor kinderen, en zwem je niet om de poort heen (`zelda lopen` telt zwemmen daar niet).
- **Morgal** (`Morgal`, een `ZeldaBaas`; een evoker, ×1,6, zonder eigen wil): drie fases, met **even adem** ertussen (zes
  tellen: hij vliegt naar zijn nieuwe plek, niemand valt aan, en je krijgt drie harten terug).
  1. *Nevelgeesten*: hij zweeft hoog boven zijn troon, in zijn nevel (een klap doet bijna niets). Hij roept twee golven
     geesten op (twee, dan drie; een per extra speler erbij): vliegende vexen van nevel, licht doet ze dubbel.
  2. *Duistere bollen*: hij komt naar beneden, loopt heen en weer voor zijn troon en gooit om de vier tellen een langzame bol.
     Zwaai met je zwaard als hij vlakbij is (een paar blokken, ongeveer voor je: kinderen zwaaien niet precies) en de bol
     wordt licht en vliegt terug: raakt hij Morgal, dan is die vijf tellen **versuft** en doet een klap alles; anders houdt
     zijn mantel bijna alles tegen. Je schild houdt een bol tegen. Bij de helft van zijn leven stopt fase 2.
  3. *Licht in zijn nevelhart*: hij zweeft in een kringetje midden in de zaal, in een kloppend hart van nevel. Alleen licht
     komt erdoor: een lichtpijl doet alles (en tegen nevel het dubbele). Hij gooit om de drie tellen; een teruggeslagen bol
     laat hem neerploffen (dan mag het zwaard ook). Bij een kwart nog één geest.

  Balans: 80 leven (+40 per extra speler) × `BAAS_LEVEN`, bollen 5 × `SCHADE`, geesten weinig; met het lichtharnas en een
  bron en zielvuur vlak voor de deur (het balkon) ga je zelden dood. Muziek in drie delen (*Morgal* 1, 2, 3; de derde met
  het lied van thuis erin). Verslagen = hij verdwijnt in zijn eigen nevel (een draaikolk van zwart, dan een flits van licht),
  zijn geesten lossen op. Tijmen verklapt het op het balkon, Wiske bij elke fase, en na doodgaan als tip.
- **Het einde**: op het dak lost de kooi op; Zelda: «Morgal is weg. Kijk, de zon komt op!» (`vrij:zelda`, het deuntje
  *Dageraad*), Wiske: «Zelda is vrij!», «Nachtrots · de nacht is voorbij» (`e5b`). Op heel Nachtrots wordt het ochtend, het
  onweer stopt, de nevel om de toren is weg. Zelda gaat vooruit naar de kreek; Brecht vaart (de eerste keer meteen) naar het
  Lindewoud: **de terugvaart** (een bijzondere overtocht, `ZeldaHoofdstuk.vaart`: Zelda aan dek, het lied van thuis). In de
  haven hoort Wiske muziek: **het feest op de open plek** (het oefenveld, `ZeldaFeest`): Zelda in het midden, eromheen Tijmen,
  Wolf, Mira, Teun, Hendrik en Bas, Ys, Hagen, Okker, Dina en Joppe, Ida (en Bram, die er al stond). Lichtjes langs de
  palissade, af en toe een vonk in de lucht, het lied van thuis als feest (*Feest*). Praten met Zelda = het einde: de vlag
  `zelda`, vuurwerk, «Story of Zelda · voltooid» (één grijze regel). Op de kaart staat daarna «Story of Zelda · voltooid»
  met het aantal hartstukjes.
- **Daarna blijft alles open**: de gasten zeggen elk iets nieuws (Dina geeft een warm broodje), en op het Lindewoud Kees,
  Hanne, Fien, Sem, Bram, Gerrit, Brecht en Lotje (één keer, vooraan). Tijmen op het feest: «Vroeger leerde ik Zelda de
  sterren lezen.» Zelda vraagt om haar kroontje (*De schat van Morgal*): het hek van Morgals schatkamer onder de toren gaat
  open, daar liggen het kroontje en `hs:nachtrots-4`; terug bij Zelda draagt ze het (alleen voor wie het bracht) en is er nog
  wat vuurwerk. `/hint` en Tijmen zeggen daarna hoeveel hartstukjes er nog liggen.
- **Personen** (5b): Tijmen (de torenpoort in 5a: het zegel; het balkon: de wegwijzer, «sla zijn bollen terug»; het feest),
  Ida (de torenhal; het feest), Zelda (het dak; de kreek; aan dek; het feest). In 5a erbij: Brecht (de terugvaart), Wolf
  («Zelda is vrij!»).
- **Genezen en bescherming**: twee zielvuren (voorhof, balkon), vier bronnen (voorhof, torenhal, Zelda's kamertje, balkon),
  de flesjes, op adem komen, drie harten tussen de fases; het lichtharnas (5a) is de bescherming. Het enige gevecht is Morgal.
- **Hartstukjes**: `hs:nachtrots-4` in de schatkamer (na het einde). Nachtrots heeft er daarmee vier; het spel twintig.
- **Muziek**: *Toren* (de klim: een hartslag, een harp die omhoog klimt), baas *Morgal* in drie delen, het deuntje *Dageraad*,
  *Feest* (het lied van thuis als feest, op het Lindewoud); in de schatkamer *Kerkers*; is Zelda vrij, dan buiten *Thuis*.
  **Hemel**: nacht in de toren, de dageraad na Morgal, de ochtend na het feest.
- **Afwijkingen van het oude plan, en waarom**: de toren beklim je buiten in plaats van binnen (in de nevel: je ziet de trappen
  van licht van ver verschijnen, en elke kant vraagt een ander voorwerp: het hele spel komt nog één keer langs). De bruggen
  van licht zijn nevelbruggen (een lichtpijl door de nevel, niet ergens op). Zelda zit niet in de troonzaal maar op het dak
  (de zon komt op als je haar bevrijdt). Tijmen breekt het zegel (zijn rol in 5b) en vertelt op het feest dat hij Zelda's
  leraar was. Na het feest een opdracht terug naar Nachtrots (Morgals schatkamer), zodat er na het einde nog iets te doen is.

**Hoe 5b aanhaakte** (sessie `zelda-eiland-5b`):
- «De toren» is uit `Zelda.KOMT` (nu leeg) en `new ZeldaEiland5b(plugin, muren, lindewoud)` staat als tweede deel bij
  Nachtrots. 5a bouwt de toren-rots in x 1880 … 2000 niet meer (`torenErbij`) en is niet opgehoogd: zijn nieuwe gesprekken
  (Tijmen en het zegel, Zelda en Brecht in de kreek, Wolf), de naam en muziek van de brugpoort, de dageraad en het einde van
  het onweer zijn code zonder blokken.
- In het vak van 5a zet 5b bij elke start (zonder herbouw): de torenpoort (een `Poort` op x 1858, dicht = crying obsidian),
  de gang door de muur en de brugpoort tot de klif (x 1859 … 1874).
- Het feest staat op het Lindewoud (`ZeldaFeest`, vanuit `ZeldaHoofdstuk1.bouw`), zonder blokken (dus zonder herbouw): personen
  die alleen zien wie Zelda bevrijdde, deeltjes, muziek. Tijmen in zijn sterrentoren is er dan niet (hij is op het feest).
- De zee (`ZeldaZee`) heeft Zelda aan dek, voor wie haar bevrijdde en nog niet thuis is (zonder herbouw).

**Hoe 5a aanhaakte** (sessie `zelda-eiland-5a`; hieronder ook wat er vóór stond):
- Nachtrots staat sinds kitpvp 2.12 in `Zelda.EILANDEN` (open na `baken4`, aanleg rust `kreek`, klaar-vlag `zelda` voor 5b);
  in `Zelda.KOMT` staat nu «De toren» (deel van Nachtrots, open na `e5a`). Het vak is x 1640 … 2000, z −150 … 150, 5a tot x 1879
  (de zee voor de overtocht ligt op x 1840 … 2040, z −510 … −330: daar komt 5a niet).
- De Nevelring is gebroken voor wie `baken4` heeft (vlag `nevelring` als hij het zag); op het Lindewoud hangt hij tot dan
  als zwarte rook om de burcht aan de horizon (`ZeldaHoofdstuk1.tik`).
- De lichtpijlen (vlag `lichtpijlen`, staat al in `ZeldaVoorwerp.LATER`): de nevelnis in de voorkamer van de Smidse heeft
  `hs:gloedberg-4` met `voorwerp = "lichtpijlen"` (en Morgals nevel als deeltjes, `ZeldaEiland4b.tik`). Zodra het voorwerp
  er is, is hij te pakken; de test haalt hem dan bij «later terug». Sinds 5a staat er een echte `Nevel` voor de nis (gezet
  door de begintoestand van 4b, zonder herbouw): eerst wegschieten, dan pakken.
- Wat er is: lava doet geen echte schade (terug naar waar je veilig stond), `Ritme` (lava die stijgt en zakt), een baas
  kan iets met een pijl op een blok (`ZeldaBaas.raakBlok`) en met de vuurstaf (`ZeldaBaas.vlam`); deeltjes voor één speler
  van ver (`ZeldaSpel.deeltje`). Tijmen en Ida kunnen terugkomen; Hanne heeft nog een opdracht tegoed.

## De kaart (sessie `zelda-kaart`, kitpvp 2.14)

Jelle (28-10): "Een kaart zoals in Legend of Zelda, om het eiland te overzien." Vak 9 is sindsdien een **gevulde kaart** met
een eigen tekenaar (`ZeldaKaart`, de tekenregels in `ZeldaKaartBeeld`, unit-tests in `ZeldaKaartTest`).

**Wat je ziet.** Houd je vak 9 vast, dan zie je het blad van het eiland waar je bent, van boven:
- **Het land** in de kleuren van wat er echt staat (zoals een Minecraft-kaart), met reliëf (een helling naar het noorden is
  licht, naar het zuiden donker) en zonder losse spikkels. Paden, daken en meertjes zie je gewoon.
- **De zee** rustig blauw: schuim langs de kust, lichter in het ondiepe, hier en daar een golfje (︵).
- **De mist.** Land waar je nog niet was is **perkament** (lichtgrijs), met een donkerder rand langs de kust: de vorm van
  het eiland ken je al van zee. De grens is zacht (om en om). De zee is altijd te zien.
- **De zielvuren** waar je was, als vlammetjes; het zielvuur waar je na doodgaan terugkomt is **blauw**.
- **Dorpen en kerkers** als de bekende tekens van een Minecraft-kaart, zodra je er in de buurt was: een dorp (in de kleur
  van het eiland: vlakte, zand, sneeuw, as, hout), een kerker (de tempel) en Morgals toren (het landhuis).
- **Jij**: een witte pijl die wijst waar je kijkt. **Medespelers** op hetzelfde eiland: een groene pijl.
- **Je volgende doel**: een rode **X**, op de plek die `/hint` noemt (`ZeldaSpel.wijzer`: een doel van het eiland, iemand
  met iets nieuws, iets dat je met een nieuw voorwerp kunt halen). Noemt `/hint` een zijmissie zonder plek, dan staat er
  geen X. Moet je naar een ander eiland, dan staat de X op de zeekaart, bij de steiger van dat eiland.
- **De naam** van het eiland klein bovenaan, wit op een donkere band. Verder geen tekst.

**Omslaan: de zeekaart.** Linksklik met de kaart = de andere kant: de hele Glazen Zee (8 blokken per pixel):
- eilanden waar je was in kleur (met dezelfde mist);
- een eiland dat open is maar waar je nog niet was als vorm op perkament;
- Nachtrots, zolang hij niet open is, als een donkere vlek: Morgals nevel;
- de **vaarroutes** als stippellijnen, van je eiland naar elk eiland waar Brecht je heen kan varen; ligt er land tussen,
  dan varen ze eromheen (`ZeldaKaartBeeld.vaarweg`: het kortste pad over zee, strakgetrokken, liever niet langs de kust);
- jij, je medespelers (op een eiland dat je kent) en de X.

**Op zee** (een overtocht) slaat de kaart vanzelf om: je ziet alleen de route van nu, en je pijl vaart mee van steiger
naar steiger. Bij aankomst is het weer het eiland.

**Rechtsklik** met de kaart = het kaartmenu van vroeger (reizen naar een zielvuur, opnieuw vanaf hier, varen, opdrachten,
harten, harnas, flesjes, voltooid, uitleg aan/uit, terug naar de hub). Vak 8 blijft «Terug naar de hub».

**Twee handen.** Zolang je de kaart vasthoudt, is je andere hand leeg: dan houd je de kaart groot met twee handen, zoals
in Zelda. Wissel je weer, dan komt je schild terug. F (wissel van hand) doet in Zelda niets (sinds 2.15; in beheer wel),
en een zwaai met de kaart in de hand telt bij Morgal niet als zwaai (linksklik = omslaan).

**Uitleg.** De eerste keer dat je de kaart pakt (vlag `v:kaart`) zegt Wiske: «Je kaart! De pijl ben jij, de X is je
doel.» en «Linksklik: de hele zee.» Bij de tempelruïne van het Lindewoud zegt ze nu «Rechtsklik op je kaart: reizen
naar elk zielvuur.» Met «Uitleg: uit» zegt ze niets.

**Ontdekken.** Per speler een raster over heel `Zelda.GEBIED` in vakjes van 8 × 8 blokken (`Stand.kaart`, in
`zelda.yml` onder `kaart`, ingepakt: een paar honderd tekens). Elke seconde komt alles binnen 32 blokken om je heen
erbij; naar `zelda.yml` gaat het hoogstens eens per minuut (en als je weggaat). Zo heeft in co-op **iedereen zijn eigen
kaart en voortgang**. In **beheer** toont de kaart alles: geen mist, alle
zielvuren, alle tekens.

**Oude voortgang** (een stand van 2.13 heeft nog geen raster). Bij binnenkomen vult `ZeldaKaart.aanvullen` het raster
aan met wat je zeker al zag:
- de omgeving van elke rustplaats waar je was (32 blokken);
- elk verhaal dat je hoorde;
- elke vondst;
- elke gewonnen kamer;
- een eiland dat af is (zijn `klaar`-vlag) helemaal.

Dat geldt ook voor nieuwe spelers: wie een eiland afmaakt, krijgt de hele kaart ervan. Er gaat niets verloren, en een
stand zonder raster werkt gewoon.

**Licht voor de server.**
- **Eén keer meten per eiland.** Elk eiland wordt één keer van boven gemeten: per pixel van het blad één kolom. De chunks
  laden op de achtergrond, hoogstens drie tegelijk, nooit nieuw gemaakt. Dat gebeurt pas als het bouwen klaar is, ±10 s
  per eiland. Het resultaat staat in `plugins/Zelda/zelda-kaart/<eiland>.dat`, met als sleutel de versies van de delen
  en `ZeldaKaart.TEKENWIJZE`. Pas als een deel een nieuwe versie krijgt (of de tekenwijze verandert), wordt dat eiland
  opnieuw gemeten.
- **Tekenen.** Minecraft vraagt de tekenaar elke tik om de kaart. Er gebeurt pas iets als je de kaart vasthoudt, en dan
  is er meestal niets nieuws:
  - de pijlen en de X gaan vijf keer per seconde;
  - het blad wordt alleen opnieuw getekend als er iets veranderde (een nieuw vakje, een zielvuur, een vlag, omslaan),
    hoogstens eens per seconde;
  - naar de speler gaan alleen de pixels die anders zijn (Minecraft zelf).
- **Een eigen kaart per speler.** Elke speler heeft een eigen `MapView`; het nummer staat in `zelda-kaarten.yml` en blijft
  ook na `/zelda reset`. Eén gedeelde kaart voor iedereen zou in Paper per inlog een stukje geheugen vasthouden.
- **Geen entiteiten, geen blokken.**

**Keuzes, en waarom.**
- **De echte wereld, niet getekend.** Zo klopt de kaart altijd, ook als een eiland verandert; een nieuw eiland staat er
  vanzelf op.
- **Rustiger dan een gewone Minecraft-kaart**: schuim, golfjes, reliëf over gemiddelde hoogtes, geen spikkels, en perkament
  in plaats van zwart. Het moet een kaart zijn om graag naar te kijken.
- **De mist ligt alleen over land.** Een eiland zie je van zee al liggen, dus de kustlijn is geen geheim. En een eiland
  dat je helemaal kent, staat dan niet in een zee van perkament.
- **Zielvuren als pixels, dorpen en kerkers als tekens**: weinig tekens die groot zijn (8 × 8), veel kleine vlammetjes.
  Dorpen en kerkers zijn de bekende tekens van een Minecraft-kaart, overal dezelfde (alleen het dorp heeft de kleur van
  het eiland). Zo leren kinderen ze snel herkennen.
- **Linksklik slaat om, rechtsklik is het menu**: zo blijft alles van vroeger op dezelfde plek. Het kaartmenu blijft
  nodig voor reizen en «opnieuw vanaf hier».
- **De X volgt `/hint`** (dezelfde functie `ZeldaSpel.wijzer`, ook gebruikt door `/hint` zelf), zodat ze nooit iets anders
  zeggen.

**Een eiland erbij**: het staat vanzelf op de kaart, want het blad volgt de speelgebieden van zijn delen. Dorpen en kerkers
zet je erop met `tekens()` in het deel (de zelftest kijkt of ze in het deel liggen).

**Beheer.**
- `zelda kaart` maakt PNG's van elk eilandblad zonder mist en van de zeekaart, in `plugins/Zelda/kaart/`.
- `zelda kaart <speler>` maakt zijn twee kanten zoals hij ze ziet (met eenvoudige tekentjes voor de pijlen).
- `zelda kaart opnieuw [eiland]` meet opnieuw.

## De afronding (sessie `zelda-afronding`, kitpvp 2.15)

Het hele spel nagespeeld (de testroute van Thuis tot het feest, gesprekken en hints per stap nagelopen) en afgemaakt.
Geen nieuwe eilanden; wat er veranderde, en waarom:

- **Zee overal.** De zee (`zelda-zee` versie 2) zet zee in heel `Zelda.GEBIED` en 112 blokken eromheen, maar alleen waar nog
  de vlakke wereld lag (gras met lucht erboven): tussen de vakken van de eilanden en voorbij hun randen. Je zag vanaf
  Nachtrots, de Gloedberg en het Lindewoud nog gras zeven blokken onder zeeniveau; nu is het overal de Glazen Zee
  (`Bouwer.perKolom`: chunk voor chunk, ±25 s bij de eerste start).
- **De Witte Toppen** (`zelda-e3b` versie 2): de oosthoeken rond en de noord- en zuidkust golvend; het eiland was van zee en
  op de kaart een ovaal met een rechthoek ernaast. IJshoeve (`zelda-e3a` versie 3): de schoorstenen lopen niet meer tot y 1.
- **Waar gaat het verder?** Op een eiland dat af is zei Wiske «Ik weet het ook niet» als het verhaal op een eiland verder
  ging waar je al was. Nu: «Het verhaal gaat verder op de Gloedberg. Vaar er heen.» (de kaart zet de X bij dat eiland),
  Tijmen: «Hier is het licht terug. Op de Gloedberg nog niet.»
- **Twintig hartstukjes, met een hint.** Alle twintig zijn te halen (vier per eiland, `test … alles` controleert het). Is er
  niets anders te doen, dan zeggen Wiske en Tijmen waar je er nog een mist: «Hier op het eiland ligt nog een hartstukje»
  (nog eens vragen = ze wijst, de X staat erop), «Hier ligt nog een hartstukje. Wie vraagt je nog iets?» (een beloning), of
  «Op de Witte Toppen ligt nog een hartstukje voor je.» (`ZeldaSpel.stukje`). Een stukje achter een voorwerp dat je nog
  niet hebt telt niet mee.
- **Brecht heeft nieuws** na elke grote stap, één keer, op de steiger waar je hem het eerst ziet: binnen in Morgals burcht,
  het kroontje («Onder in Morgals toren, zei ze»), Zelda met haar kroontje, de brieven (`ZeldaFeest.brechtNieuws`).
- **Na het feest: Zelda's brieven** (`brieven`). Na het kroontje vraagt Zelda haar dank rond te brengen: vier brieven, voor
  Teun (Zoutkom), Ys (IJshoeve), Okker (Sintelgat) en Wolf (de kreek). Ieder zegt er iets over («Brieven · 2 van 4»), en
  terug bij Zelda is er vuurwerk. Zo stuurt het einde je nog één keer langs alle eilanden.
- **Het feest klopt**: Bas en Joppe staan er alleen als je ze bevrijdde (Hendrik zegt dan iets anders), Hagen zegt niets
  meer over een harnas dat je misschien niet hebt, Gerrit niet over Wolkje. Na hun eerste regel zeggen de gasten iets
  nieuws dat je terugstuurt («Kom eens langs in Zoutkom», «Het meer is weer glad. Kom eens glijden.») in plaats van
  «Praat met Zelda». Zelda zelf: «Kom je nog eens? Het feest gaat door.»
- **Kleine gesprekfouten**: Ida in de torenhal zei na de dageraad weer «Deze toren nog niet»; Tijmen in IJshoeve zei
  «Hoog op de berg slaapt het Vorstbaken» ook als het al brandde; Hagen was na Okkers kool nog steeds koud.
- **Balans**: de kade (het eerste verplichte gevecht met schutters) heeft in de derde golf één kruisboog in plaats van
  twee (een spin erbij). Verder bleef `ZeldaRegels` zoals het was: elke kamer heeft drie gemengde golven, een heelplek
  binnen 32 blokken (zelftest), en bescherming vóór elk zwaar gevecht (de smidshelm van Kees vóór Grauwmoer en de kade;
  het maliënhemd op het Zilverstrand; wachtersharnas, gloedharnas, Gloedschild en lichtharnas als voorwaarde vóór
  Grimmel, de Askamer, Asgrim, de binnenplaats en Morgal). Zonder een echte speler is meer bijstellen gokken.
- **Muziek**: de test meldt elke stap waarop er geen muziek is (achtergrond of gevecht). Er was nergens stilte: elk deel
  heeft voor elke plek een lied, op zee *Zee* (of *Thuis* bij de terugvaart), het kaartmenu stopt niets, na het feest
  *Feest* op de open plek en verder de liedjes van het Lindewoud.
- **Teksten**: alle regels zijn al kort (hoogstens ±60 tekens, op de actiebalk, één tegelijk); niets in de chat of in een
  titel. De nieuwe regels volgen dat.
- **De kaart**: vaarroutes om de eilanden heen; met de kaart in de hand zwaai je niet; F doet niets.
- **Zelda's uiterlijk**: een mannequin met de huid «Alex» uit het spel zelf (smal model, lang haar; een sleutel naar een
  textuur van de client, geen download) en haar witte jurk; daaronder geen tekst.
- **`zelda lopen`**: `zonder bommen` houdt gebarsten muren dicht en `zonder vuurstaf` vuurpuzzels die alleen een vlam
  aansteekt; `dicht <id>` werkt ook voor een puzzel of muur. En: op een muurtje of hek (anderhalf hoog) stapt de controle
  niet meer (een speler kan dat ook niet). Zo bleek dat `nachtrots zonder bommen dicht torenladder` alles haalde: hij liep
  over het muurtje langs het puin. Nu is het puin echt nodig, en zijn alle eilanden nog steeds «alles te belopen».

## Niveaus, samen spelen en meerdere spellen (sessie `zelda-moeilijkheid`, kitpvp 2.16)

Jelle (28-10): "Een grote uitdaging is de difficulty level. En met hoeveel players je hem speelt en of je als één persoon
meerdere journeys naast elkaar kan hebben. […] Ik wil niet dat je sneller het spel af hebt of sneller door een arena
komt, maar wel dat je bijvoorbeeld sneller heelt, meer armor hebt, extra potions. […] zodat ik kan managen dat ze
blijven spelen, want anders wordt het te moeilijk en stoppen we." Het ontwerp in één zin: **een niveau verandert alleen
hoeveel hulp je krijgt, nooit wat je moet doen**; elk spel heeft zijn eigen niveau, je wisselt wanneer je wilt, en wie
het lager zet wordt niet gestraft, maar wie het hoog houdt verdient iets om te laten zien.

### Vier niveaus (`ZeldaRegels.Niveau`)

Vijanden, golven, bazen, puzzels en het verhaal zijn op elk niveau precies hetzelfde (geen minder leven, geen minder
golven: je komt nergens sneller door). Alleen de steun voor de speler verschilt:

| | **Verhaal** | **Avontuur** (begin, = 2.15) | **Held** | **Legende** |
|---|---|---|---|---|
| voor wie | jonge kinderen, wie vooral wil ontdekken | iedereen | wie het spannend wil | wie het al kent |
| schade die je krijgt | × 0,5 | × 1 | × 1,35 | × 1,75 |
| schildje van licht (gouden harten bij het begin van elk gevecht) | 4 | — | — | — |
| op adem komen buiten een gevecht | na 3 s, elke s een half hart | na 6 s, elke 2 s | na 8 s, elke 3 s | na 12 s, elke 4 s |
| op adem komen ín een gevecht | na 5 s, elke 3 s | nee | nee | nee |
| flesjes | 2 extra | wat je vond | wat je vond | wat je vond |
| een flesje geneest | 6 harten (sterk 8) | 5 (7) | 5 (7) | 4 (5½) |
| een hartje van een vijand | 3 van de 4 keer | 1 van de 2 | 1 van de 3 | 1 van de 5 |
| adem tussen twee golven | 6 s | 4 s | 4 s | 4 s |
| hulp na doodgaan in hetzelfde gevecht (gouden harten per keer, hoogstens 3 keer) | 2 | 2 | 1 | — |

- **Kiezen en wisselen**: op de kaart (rechtsklik met vak 9) staat **«Niveau: Avontuur»**. Klik = een klein menu met de vier
  niveaus, elk met twee korte regels («Wiske helpt veel», «Vijanden slaan harder»). Wisselen kan altijd, ook midden in
  een gevecht: het geldt meteen. Geen straf, geen bevestiging; één grijze regel «Niveau · Held».
- **Een nieuw spel** begint met de keuze van het niveau (hetzelfde menu; staat je niveau vast, dan krijgt het nieuwe spel
  dat niveau); het eerste spel van een speler (en elke oude stand) is **Avontuur**: dat is precies de balans van 2.15.
  Wiske noemt het menu één keer als ze de kaart uitlegt («Rechtsklik: het menu, ook je niveau.»).
- **Hulp na doodgaan** (doorzetten): ga je dood in een gevecht, dan begint je volgende poging met een schildje van licht
  (Wiske, de eerste keer dat je een schildje krijgt: «Ik geef je een schildje van licht.», op Verhaal «Een schildje van
  licht, van mij.»). Na drie keer dood in hetzelfde gevecht zegt Wiske één keer
  «Te zwaar? Op de kaart kun je het makkelijker zetten.» (niet op Verhaal, niet als het niveau vaststaat). Winnen zet de
  teller terug.

### Sterren en de heldenrand (gamification)

- **Sterren**: elk gevecht (kamer of baas) onthoudt het hoogste niveau waarop je het won (teller `ster:<kamer>`, 1 … 4).
  Telt het laagste niveau dat je **tijdens** dat gevecht had: midden in een gevecht lager zetten mag, de ster is dan van
  dat lagere niveau. Op de kaart: «Niveau: Held» met «gevechten op Held of hoger: 7 van 24». Win je een gevecht voor het
  eerst op Held of Legende, dan één grijze regel «Gewonnen op Held».
- **Heldenrand**: alle vijf bazen (Grauwmoer, Zoutbaard, Grimmel, Asgrim, Morgal) gewonnen op Held of hoger = een gouden
  rand op je harnas, op Legende een paarse. Iedereen ziet het (ook je vrienden), het doet verder niets. Wiske zegt het één
  keer. Zo is het hoge niveau iets om trots op te zijn, en het lage niveau nooit een straf.
- **Records** (de tijden van de uitdagingen) blijven per speler, over al je spellen heen.

### Samen spelen (schaling op het aantal spelers)

- Iedereen in een gevecht heeft **zijn eigen niveau**: een kind op Verhaal en een vader op Held vechten in dezelfde kamer,
  ieder met zijn eigen schade, schildje en flesjes.
- **Samen is net iets zwaarder** (`ZeldaRegels.extraVijanden`, `baasLeven`): per extra speler in de kamer één vijand per
  golf erbij (hoogstens 8), een baas heeft per extra speler meer leven (`levenPerSpeler`) en roept met drie of meer één
  helper extra. Dat was er al; het staat nu op één plek, met unit-tests.
- De adem tussen golven is die van het niveau met de meeste hulp in de kamer (wie het het zwaarst heeft, bepaalt de pauze).

### Meerdere spellen (opslagplekken) en een samen-spel

- Elke speler heeft hoogstens **drie spellen** («Spel 1», «Spel 2», «Spel 3»), elk met een eigen voortgang, niveau en
  kaart. Op de kaart: **«Spellen»** = een klein menu met de drie plekken: waar je bent, harten en niveau; klik = verder in
  dat spel (je komt binnen op zijn laatste rustplaats), een lege plek = **nieuw spel** (eerst het niveau kiezen),
  rechtsklik op een ander spel = wissen (met «Ja, wissen» / «Nee»). Het spel waarin je speelt, kun je niet wissen.
- **Samen-spel**: in «Spellen» staat **«Samen spelen»**: de vrienden die nu in Zelda zijn. Klik = een uitnodiging (zij
  zien één grijze regel en in hun «Spellen» een glinsterende «Uitnodiging van …»; vijf minuten geldig, en alleen zolang
  jij in dat spel speelt). Wie hem aanneemt, krijgt een nieuw
  spel (op een vrije plek) dat precies bij jouw voortgang begint, en komt binnen op jouw laatste rustplaats. Vanaf dan is
  het **één avontuur met twee (of meer) spelers**: wat de één vindt, wint, oplost of hoort, krijgt de ander ook (binnen een
  seconde, ook als hij er niet is). Zo raakt niemand achter en kun je samen verder waar je gebleven was. Ieder houdt
  zijn eigen niveau, flesjes, rustplaats, records, de naam van zijn vosje en zijn opties (uitleg aan/uit). Je eigen spel
  blijft er gewoon naast staan: tegelijk kan de één samen spelen en de ander in zijn eigen spel (iedereen speelt in
  dezelfde wereld, een kamer is van wie erin staat).
- Wat er gedeeld wordt (`ZeldaVoortgang.deel`): nieuwe vlaggen (behalve `o:`), de verste rustplaats per deel, bezochte
  rustplaatsen (reizen kan naar waar een ander was), zijmissies (de verste stap) en wat je op de kaart ontdekte. Alleen
  **nieuwe** vlaggen gaan over: wie «opnieuw vanaf hier» kiest, krijgt de verhalen erna niet meteen terug, en wie later
  aansluit begint bij wat de groep nu heeft. Wie in beheer is, doet even niet mee en krijgt het daarna. Een nieuwe vlag
  krijgt je maat binnen een seconde, en vóór elke keer bewaren; een flesje dat zo binnenkomt is leeg tot een bron of
  zielvuur (zoals altijd).

### Bijsturen (Jelle; alleen OP/beheer, `zelda.beheer`)

| commando (in de game `/zelda …`, console `zelda …`) | wat |
|---|---|
| `spelers` | wie er in Zelda is: spel, waar, niveau (en steun), harten, doden in het gevecht waar hij nu vastzit, minuten zonder voortgang; in de game als menu met een hoofd per speler (klik = bijsturen) |
| `niveau <wie> <verhaal\|avontuur\|held\|legende> [vast]` | het niveau van zijn spel; `vast` = hij kan het zelf niet meer veranderen; `niveau <wie> los` |
| `steun <wie> <-2 … 2>` | stil bijsturen binnen een niveau: per stap 15 % minder (of meer) schade en bij plus een flesje extra; de speler ziet het niet |
| `volg` | aan/uit: een grijze regel in jouw chat als iemand drie keer doodgaat in hetzelfde gevecht, of tien minuten niet verder komt |
| `doden` | de gevechten waar het meest wordt doodgegaan (alle spelers, alle spellen): waar de balans knelt |
| `spel <speler> [n]` | zijn spellen, of zet hem in spel n |
| `samen <speler> <met>` / `samen <speler> los` | zet hem in het spel van een ander (zoals een aangenomen uitnodiging), of maak zijn spel weer los |
| `reset <speler> [n]` | alles wissen (zoals altijd), of alleen spel n |

`<wie>` = een speler, `hier` (iedereen binnen 48 blokken van jou: je groep) of `allen` (iedereen in Zelda).

### Samen: wat er nog bij kwam

- **Door een maat heen**: een bom, pijl of lichtpijl van een speler gaat door een medespeler heen (`ZeldaSpel.onDoorMaat`).
  De co-op-test vond het: een vriend die voor je staat, ving je bom, en de gebarsten muur bleef dicht.
- **Testen met twee** (`zelda test <speler> alles mee <speler2>`): één script speelt, de tweede neemt een samen-spel
  aan en volgt overal (op een vloer: een testspeler valt niet vanzelf, anders kickt de server hem wegens vliegen), ook op
  zee. Aan het eind moet hij alles hebben wat de eerste heeft (ruim 500 vlaggen). Bij Grimmel mag de eerste dan wel geraakt
  worden: met twee spelers heeft Grimmel een helper.

### Oude voortgang

`zelda.yml` van 2.15 blijft geldig: `spelers.<uuid>` is het spel waarin je nu speelt (met `spel: 1` en `niveau`), de
andere spellen staan eronder in `spellen.<n>`. Een oude stand is dus zonder omzetten «Spel 1» op **Avontuur**, met alles
erin (ook midden in het spel). Terug naar 2.15 kan: die leest het spel waarin je het laatst speelde (de andere spellen
vallen dan weg bij het bewaren).

## Terugkerende personages

Jelle (27-10): eilanden zo ontwerpen dat je er later naar terug moet of wilt, omdat je bij een bekend karakter iets
moet regelen; een vaste wegwijzer bij wie je steeds terugkomt. Zo knopen we de eilanden aan elkaar:

| wie | wat | Lindewoud | Zilverstrand | later |
|---|---|---|---|---|
| **Wolf**, wachter van Zelda (Nachtrots) | helpt je binnen | — | — | 5a: achter een raampje in de rots: de muursleutel; bevrijd in de kerkers, daarna in de kreek: het wachtwoord, het harnas van licht, de gevangenen (`hs:nachtrots-1`); 5b: «Zelda is vrij!», en op het feest |
| **Zelda**, de prinses | bewaart het licht van de Glazen Zee | — | — | 5b: in een kooi van nevel op het dak van Morgals toren; dan in de kreek bij Brecht, aan dek, en op het feest op het Lindewoud (het einde); daarna vraagt ze om haar kroontje uit Morgals schatkamer, en dan om haar brieven rond te brengen (2.15) |
| **Tijmen**, reizende tovenaar (wandering trader, blauwe mantel) | de **wegwijzer**: praten = je volgende opdracht (`spel.wegwijzer(p, "Tijmen")`), Wiske vliegt die kant op; hij geeft wat je nodig hebt om verder te kunnen | sterrentoren in Lindedorp: flesje `fles2`, het **lichtwoord** (doornen) | plein van Zoutkom (2a): «ik was er al»; het Getijdenplein (2b): de **maanparel** (het getij) | IJshoeve (3a): de **sterrenlantaarn** (de storm); het IJspaleis (3b): zelf opgesloten in de wachtkamer, een bom bevrijdt hem: de **zonnesteen** (de spiegelzaal); Sintelgat (4a): de **koelsteen** (de hete gangen); de Smidse (4b): leest het schrift boven de Hartdeur, en staat op de uitkijk als de Nevelring breekt; verder op elk eiland bij de hub of steiger (de visserskreek op Nachtrots), met één eigen gave per eiland; Wiske verbaast zich er elke keer over («Hij is er alwéér!»); op Nachtrots (5a) zit hij verstopt in de kreek, wijst je naar Wolf en verbindt Wolfs arm; na de binnenplaats staat hij bij de torenpoort; 5b: hij breekt Morgals zegel met een oude spreuk, staat op het torenbalkon («sla zijn bollen terug») en op het feest («vroeger leerde ik Zelda de sterren lezen») |
| **Brecht**, visser en kapitein | varen (`spel.varen`), op elke steiger | pier | steiger; zijn zus Mira (5a: zij maakt in het licht van haar vuurtoren het zwarte harnas schoon: het lichtharnas); na het baken: «naar het noorden» | de fjord van de Witte Toppen («hier vriest zelfs mijn baard»); de Zwarte Baai («mijn zeilen worden zwart»); op elke steiger; na Morgal vaart hij Zelda en jou naar huis (de terugvaart, 5b) |
| **Hanne**, kruidenvrouw | brouwt met wat je van andere eilanden meebrengt | *Moeraskruid* → `fles1` | *Zeekraal* (aan de dijk) → `sterkwater` | *De sneeuwbloem* uit de IJstuin (3b) → `fles5`, een vijfde flesje; *Het maanmos* uit de kerkers van Nachtrots (5a) → `fles8` |
| **Kees**, houthakker | heeft altijd iets nodig van ver weg | *De verloren bijl* | — | 4b: *Het bijlblad* uit de Smidse van de Gloedberg → zijn oude veldfles (`fles7`) |
| **Lotje**, kind op de pier | telt meeuwen, wil weten hoe het elders is | praatje | (vraagt naar de meeuwen); *De zingende schelp* uit de getijdenpoelen (2b) → `fles3` | kan een kleine boodschap krijgen (een veer van de Meeuwenrots, een sneeuwbal die niet smelt) |
| **Teun**, oude visser (Zoutkom) | weet van vroeger | — | de bakens (2a); voer met Zoutbaard: de **ankersleutel** en de ton buskruit (2b) | jeugdvriend van **Ys** op de Witte Toppen: *De brief van Ys* → `fles4` (3a) |
| **Ys**, oude bergwachter (IJshoeve) | vist aan zijn wak, weet van het IJspaleis | — | — | 3a: de brief voor Teun; 3b: zijn hoorn (`hs:witte-toppen-4`), zijn oude harnas, «Grimmel loopt recht tegen de zuilen» |
| **Hagen**, smid (IJshoeve) | smeedde voor de bergwachters | — | — | 3a: de klauwhaak (verhaal), het ijsblok; 3b: warmt het bevroren harnas → `harnas2`; 4a: leerde smeden met **Okker** in Sintelgat: *De gloedkool* → `fles6`; met de vuurstaf smelt het ijsblok (`hs:witte-toppen-3`) |
| **Ida**, klimster (berghut, 3b) | klimt overal | — | — | 3b: de wand, de sleebaan; 4a: op het Uitzicht van de Gloedberg («ik klim alle bergen»; na 4b: de nevel is weg); 5b: in de torenhal van Morgal («de trap is buiten»), en op het feest |
| **Okker**, smid (Sintelgat, 4a) | de smid van het mijnwerkersdorp | — | — | 4a: de esse, het gloedharnas, de gloedkool voor Hagen; 4b: smeedt de schildplaat uit de Smidse tot het **Gloedschild** |
| **Kobus**, oude mijnwerker (Sintelgat, 4a) | weet van vroeger | — | — | 4a: de mijnvuren; vertelt over de Smidse; 4b: «Asgrim kan niet tegen water» |
| **Wiske**, de fee | hint, uitleg, wijst | overal | overal | wordt feller bij elk baken (`ZeldaMetgezel.licht`: iets groter, meer glans) |
| **Gerrit, Hendrik, Saar, Dina** | wachten op wie Morgal meenam | Gerrit: Wolkje | Hendrik: Bas | 5a: in de cellen van Nachtrots zitten Wolkje (schaap), Bas (leerjongen), Kiek (geitje) en Joppe (Dina's zoon); bevrijd staan ze thuis en zegt wie wachtte iets (Dina: een warm broodje, helemaal beter); 5b: Hendrik, Bas, Dina en Joppe op het feest |
| **het feest** (5b, het oefenveld van het Lindewoud) | iedereen die hielp | Kees, Hanne, Fien, Sem, Bram, Gerrit, Brecht en Lotje zeggen na het einde iets nieuws | Mira, Teun, Hendrik, Bas | Ys, Hagen, Okker, Dina, Joppe, Ida, Wolf, Tijmen en Zelda staan op het feest (Bas en Joppe alleen als je ze bevrijdde); daarna stuurt elke gast je terug naar zijn eiland, en Zelda's brieven gaan naar Teun, Ys, Okker en Wolf (2.15) |

**Bouwregel voor elk volgend eiland:** zet Tijmen bij de hub (met `wegwijzer` als laatste gesprek), en maak minstens
één **boodschap over zee**: een zijmissie van een bekende op een eerder eiland die pas begint als je op het nieuwe
eiland was (`s.heeft("e:<eiland>")`), met de vondst op het nieuwe eiland (`vereist = "g:<gesprek>"`) en de beloning
bij de bekende. `/hint` noemt zo'n missie ook op het andere eiland, en `zelda test … alles` vaart aan het eind
twee rondes heen en weer om ze af te maken.

## Balans van gevechten (kitpvp 2.8.2, Jelle 27-10)

Sinds 2.16 is dit de balans van **Avontuur**; de andere niveaus veranderen alleen de hulp voor de speler (zie «Niveaus, samen
spelen en meerdere spellen»), nooit de golven of de bazen hieronder.
- Gevechten langer maar lichter, meer variatie in tegenstanders, heelplek en bescherming voor elk zwaar gevecht (Jelle 27-10). Gebruik de gedeelde balans in `ZeldaRegels` (`SCHADE` 0.55 op alle vijandschade, `BAAS_LEVEN` 1.5, `GOLF_PAUZE` 4 s): geef een kamer liever 3 gemengde golven (vechter + schutter + snel klein beestje, bijv. `SPIN`) dan 1 zware; richting 1-3 minuten, zelden dood. Bijstellen = die drie getallen, niet per kamer.
- De kade (afronding, 2.15): de derde golf heeft één kruisboog (kruisboog, soldaat, spin) in plaats van twee: het eerste
  gevecht met schutters, met alleen de smidshelm en de tuniek.
- Nachtrots (5b): alleen Morgal, het zwaarste gevecht van het spel maar licht: drie fases met zes tellen adem ertussen (en drie harten terug), zijn bollen doen weinig (een schild houdt ze tegen), zijn geesten zijn niet taai; het lichtharnas, en een zielvuur en een bron op het balkon vlak voor de deur. Bijstellen = de drie getallen in `ZeldaRegels` (en de verhoudingen in `ZeldaRegels.morgalSchade`).
- Nachtrots (5a): alleen de binnenplaats (nachtsoldaat + nevelmijt, nachtsoldaat + nachtschutter, alle drie; de bel = met de wachtmeester), pas met het lichtharnas (de binnenpoort) en met een zielvuur en een bron in het wachthuis ervoor. Sluipen is nooit vechten: gezien = terug naar het zielvuur.
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
| kaartmenu | `spel.kaart` (rechtsklik met de kaart) | zielvuren van dit eiland, eilanden (varen), opdrachten, harten, harnas, flesjes, uitleg, hub; de kaart zelf: «De kaart» |
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
| beheer | `zelda vaar|vlag|hint <speler> …`, `zelda test <speler> motor` | zie TECHNIEK.md |
| **open eiland** (versie 4 van het Lindewoud) | | |
| poort voor wie iets heeft | `ZeldaHoofdstuk.Poort`, `poort(…)` | opent als er iemand in het gebied staat voor wie `mag` klopt (bv. het lichtwoord), voor iedereen; na een minuut leeg weer dicht; telt als open in `zelda lopen` |
| oefenplek | `Kamer.alleenBel` | deuren open in rust; pas als je de bel luidt wordt er gevochten (eerst de gewone golven, daarna met de extra); telt niet als verplichte kamer |
| doelen in elke volgorde | `doel(groep, id, …)`, `ZeldaRegels.doel(doelen, s, x, z)` | doelen met dezelfde groep: de hint (en de wegwijzer) noemt het dichtstbijzijnde dat nog niet klaar is |
| wegwijzer | `spel.wegwijzer(p, naam)` | een persoon zegt je volgende opdracht (de tweede hint, in zijn stem) en Wiske vliegt die kant op; anders een eiland waar je nog niet was, een zijmissie (ook van een ander eiland), iets voor later, `wacht()` |
| praten op het pad | `praten(persoon)`, `ZeldaHoofdstuk.Praten` | voor de test: hier praat je met iemand (Tijmen, Bram) tot hij niets nieuws meer heeft |
| vaste rustnummers | `rust(index, Rust)` | een rustplaats houdt zijn nummer (de opslag) maar staat op zijn plek in het pad; de zelftest eist 0 … n−1 |
| op adem komen | `ZeldaRegels.bijkomen` | buiten een gevecht: na 6 s zonder klap elke 2 s een half hart (Avontuur; per niveau anders, op Verhaal ook in een gevecht) |
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
| wat ligt erachter? | `zelda lopen <eiland> zonder <voorwerp> dicht <poort>` | de loopcontrole met een poort dicht of zonder een voorwerp: zo zie je of je ergens omheen kunt |
| **het IJspaleis** (eiland 3b) | | |
| glijvloer | `ZeldaHoofdstuk.Glij`, `glij(…)`, `spel.glij`, `ZeldaRegels.glij` / `glijOplossing` | glad ijs: een stap erop = rechtdoor glijden tot je ergens tegenaan botst (een halve blok per tik, kijken kan gewoon) of van het ijs af bent; `oplossing` voor de test (de eerste zet is een echte stap) en de zelftest (met de blokken van de wereld); `zelda lopen` glijdt mee |
| klauwhaak in een gevecht | `Kamer.haken` | in deze kamer werkt de klauwhaak ook tijdens het gevecht (wegtrekken als Grimmel stormt) |
| licht voor spiegels | `Spiegels.licht` | het licht van een spiegelpuzzel brandt alleen als er iemand is voor wie dit klopt (de zonnesteen) |
| Grimmel | `Grimmel` | een baas met ijszuilen: stormt (aanloop op zijn achterpoten, dan rechtdoor per tik), een zuil = duizelig (`ZeldaRegels.grimmelSchade`), de zuil groeit terug; sjokt na afloop naar zijn hol |
| nieuwe vijanden | `Soort.IJSKNECHT`, `VORSTSCHUTTER`, `IJSKOBOLD`, `GRIMMEL` | een zombie met ijs, een stray (zijn pijlen maken je niet traag), een kleine snelle zombie, een ijsbeer (×2) |
| kaartplaatjes van één deel | `zelda plaatjes <begin>` | alleen de plaatjes waarvan de naam zo begint (bv. `zelda-e3b`): lichter voor de server |
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
| **Nachtrots, de muren** (eiland 5a) | | |
| lichtpijlen | `ZeldaSpel.Lichtpijlen`, `spel.schiet`, `ZeldaSpel.LICHT` | vak 7; rechtsklik = een pijl van licht; elke pijl van wie ze heeft (ook uit de boog, de Lichtboog) is licht: een spoor, verdwijnt na de klap, dubbel tegen `zelda-nevel` |
| Morgals nevel | `ZeldaHoofdstuk.Nevel`, `nevel(…)`, `nevelOpPad(…)`, `spel.verdrijf` | blok `TINTED_GLASS` met zwarte deeltjes; een lichtpijl (binnen een blok) = weg, `n:<id>`; na een minuut niemand terug; `zelda lopen` telt hem als open met de lichtpijlen; de test schiet een echte lichtpijl |
| sluipen | `ZeldaHoofdstuk.Sluip` (+ `Wachter`), `sluip(…)`, `ZeldaRegels.pose`, `inKegel`, `sluipt`, `vensters`, `spel.ziet`, `spel.gezien`, `spel.zichtTabel` | wachters op een vast ritme met een lichtkegel (6 blokken, 32°, niets ertussen; als stipjes op de vloer); gezien = alarm, het hek even dicht, terug naar het zielvuur; finish = `l:<id>`; bukken = minder ver; `actief` = voor wie; de zelftest en een unit-test rekenen uit dat elk stuk tussen twee schuilplekken te sluipen is; de test wordt eerst gezien en sluipt dan echt |
| praten op een ander eiland | `praten(persoon)` met een persoon van een ander eiland | de test vaart erheen (kaart) en terug (Mira en het lichtharnas) |
| nieuwe vijanden | `Soort.NACHTSOLDAAT`, `NACHTSCHUTTER`, `NEVELMIJT`, `WACHTMEESTER` | een vindicator met een stenen bijl, een pillager, een endermite (nevel: licht doet dubbel), een grote vindicator (alleen met de bel) |
| **Nachtrots, de toren** (eiland 5b) | | |
| nevelbrug | `Nevel.brug`, `spel.brugLangs`, `spel.toon` | een brug of trap in de nevel (dicht = lucht met zwarte mist, open = de brug); een lichtpijl die erdoorheen vliegt (ergens tussen twee tikken) laat hem zien, blok voor blok; `zelda lopen` telt hem als brug met de lichtpijlen; de zelftest kijkt of je hem ziet vanaf `voor`; de test schiet door de nevel |
| trap van licht | `ZeldaRegels.trap` | de blokken van een trap: n treden, elk een verder en een hoger, twee breed (de nevelbruggen van de toren, de trap van de wachtersvuren) |
| vangnet | `ZeldaHoofdstuk.Val`, `val(…)`, `spel.opvangen`, `onVal` | wie in het gebied valt (meer dan 3,5 blok) of onder `laag` in het water is, staat weer waar hij veilig stond, zonder schade; `zelda lopen` zwemt daar niet; met een `proef` laat de test je vallen |
| een baas en een zwaai | `ZeldaBaas.zwaai`, `spel.onZwaai` | een zwaai van een speler in de kamer gaat naar de baas (Morgal: een bol terugslaan; `ZeldaRegels.terugslaan`) |
| schade met wie | `ZeldaBaas.schade(schade, door)` | een baas weet wat hem raakte (Morgal: alleen licht komt door zijn nevelhart) |
| Morgal | `Morgal`, `ZeldaRegels.morgalSchade` | drie fases met adem ertussen (geesten, bollen, nevelhart), eigen muziek per fase (`Kamer.muziek` wisselt), versuft na een teruggeslagen bol, verdwijnt in zijn nevel |
| muziek per speler | `ZeldaHoofdstuk.muziek(plek, stand)` | de achtergrond kan van je voortgang afhangen (het feest alleen voor wie Zelda bevrijdde; *Thuis* op Nachtrots na Morgal) |
| bijzondere overtocht | `ZeldaHoofdstuk.vaart(stand, naar)` | een deel kan een overtocht anders maken (de terugvaart: Zelda aan dek, het lied van thuis, 16 s) |
| een speler-figuur | `EntityType.MANNEQUIN` als `Persoon.soort` | Zelda: zoals een speler, met een jurk en (op het feest, per speler) een kroontje (`sendEquipmentChange`) |
| voltooid | `spel.voltooid`, `spel.stukjes` | de klaar-vlag van het laatste eiland; op de kaart «Story of Zelda · voltooid» met de hartstukjes; `/hint` en Tijmen zeggen hoeveel er nog liggen |
| het feest | `ZeldaFeest` | personen, verhalen en een doel op het Lindewoud, zichtbaar voor wie Zelda bevrijdde; lichtjes en vuurwerk (getagd: doet niemand pijn) |
| een naam bij beheer | `Persoon.beheerNaam` | zo heet hij bij `/zelda ga` (de gasten op het feest: `hendrik-feest`; Zelda daar: `feest`) |
| test vanaf een deel | `zelda test <speler> alles vanaf <deel>` | alles ervoor telt als gedaan; voor wie aan één deel werkt (de volledige route blijft de echte controle) |
| nieuwe vijanden | `Soort.NEVELGEEST`, `MORGAL` | een vex van nevel (licht doet dubbel), een evoker (Morgal zelf) |
| **de kaart** (sessie `zelda-kaart`) | | |
| kaart in vak 9 | `ZeldaKaart`, `ZeldaKaartBeeld` | een gevulde kaart per speler (`MapView`, nummer in `zelda-kaarten.yml`); vasthouden = het eilandblad, linksklik = de zeekaart, rechtsklik = het kaartmenu; met de kaart in de hand is je andere hand leeg (`spel.schild`) |
| een eiland meten | `ZeldaKaart.Meting`, `TEKENWIJZE` | één keer per versie van de delen: de bovenkant van het eiland, chunks op de achtergrond; bewaard in `plugins/Zelda/zelda-kaart/` |
| ontdekken | `Stand.kaart`, `ZeldaKaart.ontdek`, `aanvullen` | vakjes van 8 × 8 blokken, 32 blokken om je heen; bij binnenkomen aangevuld uit je voortgang (oude standen); een eiland dat af is helemaal |
| wat er op het blad staat | `ZeldaKaartBeeld.basis`, `blad`, `zeekaart`, `vlam`, `stippel`, `titel` | zee met schuim, reliëf, perkament over wat je niet zag, zielvuren, vaarroutes, de naam |
| dorpen en kerkers | `ZeldaHoofdstuk.Teken`, `tekens()` | per deel; op de kaart zodra je er in de buurt was |
| wat /hint noemt | `ZeldaSpel.wijzer`, `Wijzer.plek`, `Wijzer.eiland` | dezelfde volgorde als `/hint`; de kaart zet er een X |
| **niveaus en spellen** (sessie `zelda-moeilijkheid`) | | |
| niveau | `ZeldaRegels.Niveau`, `niveau(s)`, `schade(s)`, `bijkomen`, `flessen`, `flesHp`, `golfPauze`, `schildje` | per spel (`Stand.niveau`); alleen de hulp; `spel.zetNiveau` geldt meteen |
| schildje van licht | `ZeldaSpel.schildje` | bij het begin van een gevecht gouden harten (absorptie): Verhaal altijd, en hulp na doodgaan (`Sessie.pogingen`) |
| sterren en heldenrand | `ZeldaRegels.ster`, `sterren`, `heldenrand`, `ZeldaSpel.ster`, `rand` | teller `ster:<kamer>`: het laagste niveau tijdens het gevecht (`Sessie.laagste`); alle bazen op Held = een gouden rand |
| samen zwaarder | `ZeldaRegels.samen`, `baasLeven`, `extraHelpers` | per extra speler een vijand per golf, een baas meer leven en (vanaf drie) een helper |
| spellen | `ZeldaVoortgang.Speler`, `spellen`, `kies`, `nieuw`, `wis(id, n)`, `spel.wissel` | drie per speler; `van` = het spel waarin hij speelt; wisselen = opnieuw binnenkomen |
| samen-spel | `Stand.groep`, `ZeldaVoortgang.deel`, `ZeldaSpellen.samen`, `nodigUit`, `neemAan` | nieuwe vlaggen (niet `o:`), verste rustplaats, bezocht, missies en kaart gaan elke seconde naar de groep |
| bijsturen | `ZeldaBijsturen` | `spelers` (menu), `niveau`, `steun`, `volg`, `doden`, `spel`, `samen`; doodteller `dood:<kamer>`, `Sessie.vastGemeld` |

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
6. Test: `zelda zelftest`, `zelda test <speler> alles` (loopt alle eilanden af in de volgorde van
   `EILANDEN`: varen, elk deel in de volgorde van het pad, het einde van elk deel, Brecht, de zijmissies van het eiland
   door te praten met wie iets nieuws heeft; vondsten met `voorwerp` moeten blijven liggen), `zelda test
   <speler> motor`, en `zelda plaatjes` voor plaatjes. Een nieuw eiland komt dus vanzelf in de route, als het open gaat
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
   Lichtpijlen (gebouwd in 5a): Morgals nevel is een `Nevel` (blok `Nevel.BLOK`; `nevelOpPad` met `voor` als je erdoor moet).
   Sluipen is een `Sluip` met wachters (`Sluip.wachter(naam, standen)`, of `ZeldaEiland5a.standen` voor heen-en-weer), een
   `pad` met `schuil`-punten, een `hek` en `terug`: de zelftest controleert of het te doen is. Een brug of trap die een
   lichtpijl laat zien is een `Nevel` met `brug = true` (dicht = lucht, open = de brug; `ZeldaRegels.trap` voor treden), en
   een plek waar je hoog loopt krijgt een vangnet (`val(new Val(…))`: wie valt, staat weer waar hij veilig stond).
8. Op de kaart: het eiland staat er vanzelf op (de speelgebieden van de delen). Zet dorpen en kerkers erop met `tekens()`
   (`new Teken("dorp", x, z)`, `"kerker"`, …); de zelftest kijkt of ze in het deel liggen. Na een nieuwe versie van een
   deel wordt het eiland vanzelf opnieuw gemeten.
9. Is alles te belopen? `zelda lopen <eiland>` (ook in `test … alles`, voor elk eiland, ook het Lindewoud): de
   test teleporteert, dus deze controle volgt vanaf de steiger elke plek waar een speler kan staan en noemt wat je
   zonder teleporteren niet haalt. Deuren, poorten en bruggen tellen als open (een brug als planken).
10. De test vaart van eiland naar eiland met een klik in het menu van Brecht, haalt na elk eiland wat met de voorwerpen
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
   zelftest` OK is en `zelda test <speler> alles` en `motor` OK zijn, en bestaande voortgang blijft werken.
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
8. **`zelda-eiland-5a`** (gebouwd, kitpvp 2.12) — Nachtrots, de muren: de visserskreek, Wolf, twee sluipstukken (de weergang,
   de cellengang), de kerkers met gevangenen van alle eilanden, de kapel (lichtpijlen), Morgals nevel (ook de nevelnis in de
   Smidse), het lichtharnas (Mira), de binnenplaats, de torenpoort (de toren aangekondigd); Hanne en het maanmos (`fles8`);
   motor: `Nevel`, `Sluip`, lichtpijlen. **Klaar als** je van de kreek tot de torenpoort komt, lichtpijlen nevel weghalen,
   zelftest en test OK.
9. **`zelda-eiland-5b`** (gebouwd, kitpvp 2.13) — De toren en Morgal: de Nevelbrug, de trap om de toren (licht, klauwhaak,
   vuurstaf, bom, licht), het balkon, Morgal in drie fases, Zelda op het dak, de terugvaart en het feest op het Lindewoud
   (vlag `zelda`, «voltooid»); daarna Morgals schatkamer; motor: nevelbruggen, een vangnet, muziek per speler, een bijzondere
   overtocht. **Klaar als** het hele spel van Thuis tot het feest uit te spelen is (`zelda test <speler> alles` over
   alle eilanden), zelftest OK.
9b. **`zelda-kaart`** (gebouwd, kitpvp 2.14) — Een eilandkaart zoals in Legend of Zelda: vak 9 is een echte kaart (het eiland
    van boven, mist over wat je niet zag, zielvuren, dorpen en kerkers, jij, medespelers en de X van `/hint`), linksklik =
    de zeekaart met vaarroutes en je schip op zee; zie «De kaart». **Klaar als** de kaart op alle eilanden en op zee werkt,
    oude voortgang blijft werken, en zelftest, `motor`, `test … alles` en `zelda lopen` OK zijn.
10. **`zelda-afronding`** (gedaan, kitpvp 2.15, zie «De afronding») — Speel alles na: balans (te veel gevechten? te moeilijk?), alle «later-terug»-plekken
    bereikbaar, twintig hartstukjes, muziek overal, teksten kort, kaartplaatjes. **Klaar als** een volledige
    testronde zonder fouten loopt en de lijst met losse eindjes leeg is.

**Losse eindjes** (stand na `zelda-afronding`: opgelost, of met een reden blijven staan):
- *Opgelost* in `zelda-afronding`: de schoorstenen van IJshoeve, de vlakke wereld rond de eilanden, `zelda lopen` met
  vuurpuzzels en gebarsten muren, de vaarroutes die bij een kust stopten, zwaaien met de kaart bij Morgal, F (wissel van
  hand), Zelda's uiterlijk (een eigen huid, «NPC» stond er al niet meer onder). Zie «De afronding».
- *Blijft, met reden*: de kaart, de tekens, de huid van Zelda, de nevelbruggen en het feest zijn niet met een echte client
  bekeken (er is op de box geen client; de testspeler ziet niets). Jelle kijkt: de tekens (`ZeldaKaart.type`), het zicht
  (`ZeldaKaartBeeld.ZICHT`, 32), het perkament (`PERKAMENT`), twee handen, de pagina-geluidjes, en of Zelda met de huid
  «Alex» goed oogt (één regel in `ZeldaEiland5b.zelda`; zonder die regel is ze Steve in een jurk).
- *Blijft, met reden*: een `Poort` is van iedereen (de ladder van de toren gaat voor één speler open en dan die minuut ook
  voor een ander). Een poort is een echt blok in de wereld; per speler kan dat niet zonder neppe blokken. Samen spelen
  hoort zo te werken (wie boven was, laat zijn maat erlangs), en alleen merk je het niet.
- *Blijft, met reden*: de Witte Toppen (3b) houden hun berg tot de rand van het vak in het westen (waar 3a aansluit) en
  langs het paleis (oost): daar hangt het paleis aan (en de regel «nergens lager dan G+30», zodat je niet om de haakpunten
  heen loopt). Alleen de hoeken en de noord- en zuidkust zijn rond en golvend gemaakt.
