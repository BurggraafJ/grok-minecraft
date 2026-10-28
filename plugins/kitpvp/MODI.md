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
| `stand(Player, Toestand)` | metadata `kitpvp-stand` voor Huisdieren in `SPEL` (standaard `wacht` = wolf vecht niet; Zelda: `geen` = geen huisdier mee) |
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
| `ZeldaHoofdstuk` | basis van een eilanddeel + de onderdelen: `Rust`, `Bron`, `Kamer`, `Deur`, `Uitdaging`, `Reeks` (ook `VUUR`: met de vuurstaf), `Vondst`, `Verhaal`, `Persoon` (+ `Gesprek`), `Missie`, `Doel`, `Barst` (gebarsten muur), `Haakpunt`, `Spiegels` (draaispiegels), `Poort` (opent voor wie iets heeft), `Praten` (praten op het pad), `Glij` (glijvloer), `Smelt` (betoverd ijs), `Hitte`, `Ritme`, `Nevel` (weg met een lichtpijl), `Sluip` (wachters met een lantaarn), `Box`; haken per deel: `tik`, `boem`, `wacht`, `hemel(plek, stand)` |
| `ZeldaHoofdstuk1` | «Het Lindewoud» (eiland 1, versie 4: een open eiland): Thuis, Lindedorp in het midden, drie wegen (noord, oost, zuid), de Heksenkring en de haven; Tijmen, Kees, Fien, Sem, Bram, Wout, Gerrit, Hanne, Brecht, Lotje; de oude stukken staan verschoven (`op(dx, dz)`); `motor()` = tips en doelen; `aanvullen` = oude standen (één keer omzetten) |
| `ZeldaEiland2a` | «Het Zilverstrand» west (eiland 2a, deel `zelda-e2a`): steiger, Zoutkom, de vuurtoren met de spiegelkelder, de duinen, de Meeuwenrots, de dijk; Brecht, Teun (ook de ankersleutel voor 2b), Mira, Hendrik, Pim en Tijmen; zeekraal voor Hanne; de vlieger en het licht van de vuurtoren in `tik()` |
| `ZeldaEiland2b` | «Het Zilverstrand» oost, de Getijdentempel (eiland 2b, deel `zelda-e2b`): de dijk, het Getijdenplein (hub, Tijmen: de maanparel), het wad met het wrak (Lorre), de getijdenpoelen (Roos, de parelduik), de Getijdenzaal met eb en vloed, de Spoelkamer, de pijpgang, het raderwerk, de Ankerpoort, de Draaikolk (Zoutbaard), de toren met het Getijdenbaken; de kolk en het baken in `tik()` |
| `Zoutbaard` | de baas van 2b (subklasse van `ZeldaBaas`): kruittonnen, springen, versuft na een bom, zeepokken |
| `ZeldaEiland3a` | «De Witte Toppen», het dal en het dorp (eiland 3a, deel `zelda-e3a`): de fjord met de steiger, IJshoeve (hub: Tijmen met de sterrenlantaarn, Saar en Sven, Hagen), het bevroren meer (Ys, Noor, de ijsbaan), het Sparrenbos met de sneeuwstorm en de pootjes, de kuil (nevelwolven, het sneeuwvosje), de rotswand (graafplek), de Wachtersgang, de Wachterspost met de klauwhaak en drie haakpunten, de Uitkijk, de Bergpoort; de storm, de sneeuw en de pootjes in `tik()` |
| `ZeldaEiland3b` | «De Witte Toppen», de berg en het IJspaleis (eiland 3b, deel `zelda-e3b`): het bergpad met de berghut (Ida), de ijswand en de ijskloof (haakpunten), het voorplein (hub van het paleis), de IJstuin (tweede glijvloer, sneeuwbloem, hoorn van Ys), de sleebaan, de valbrug, de Poortzaal (Tijmen opgesloten: de zonnesteen), de Kristalzaal, de Glijzaal, de Wachtzaal, de Oostgang, de Spiegelzaal, de Wapenkamer, de Troonzaal (Grimmel), de toren met het Vorstbaken; sneeuw, de zonnesteen en het baken in `tik()` |
| `Grimmel` | de baas van 3b (subklasse van `ZeldaBaas`): een reuzenijsbeer die stormt; tegen een ijszuil = duizelig (dan doet een klap veel), de zuil groeit terug; sjokt na afloop naar zijn hol |
| `ZeldaEiland4a` | «De Gloedberg», Sintelgat en de mijnen (eiland 4a, deel `zelda-e4a`): de Zwarte Baai, Sintelgat (hub: Tijmen met de koelsteen, Okker, Dina, Fenna, Kobus; de mijnvuren), de Laadkade (de race), de mijn (de Lorriehal, de Oude Gang, de Schachtput, de Vonkkamer met de vuurstaf en de lampen, de hete gangen met koelplekken en betoverd ijs, de Gloedader, de Daaltrap, de gloed, de Askamer, de Smidsepoort, de Asgang), het Uitzicht (Ida), de Zijgang (de lorrie); de kegel van de vulkaan in het vak van 4b zolang 4b er niet is (nu niet meer); as, rook en lava in `tik()` |
| `ZeldaEiland4b` | «De Gloedberg», de Smidse (eiland 4b, deel `zelda-e4b`): de kegel en de krater (elke kolom van x 1260 … 1400, een wand rond de top), de Smidsepoort (een `Poort` in de wand van 4a), de Smidsehal (hub: Tijmen, de schildplaat), de Kettingzaal (klauwhaak, kettingbrug), de Stuwdam (bom: water op lava wordt steen), de Gietzaal (ovens, lava op een ritme), de Hartdeur (drie vonken + het Gloedschild van Okker), de voorkamer (nevelnis), het Hart (Asgrim), de Schoorsteen (vier haakpunten), de krater (het Gloedbaken) en de uitkijk (de Nevelring breekt); vuurschalen, water, nevel en het baken in `tik()` |
| `ZeldaEiland5a` | «Nachtrots», de muren (eiland 5a, deel `zelda-e5a`): de visserskreek (hub: Brecht, Tijmen verstopt, Wolf achter zijn raampje met de muursleutel), het klifpad (haak, bom), de muurvoet, de weergang (sluipen langs vier nachtwachten), de Kerkertoren, de kerkers (hub binnen), de cellengang (sluipen langs drie cipiers; gevangenen van alle eilanden, de slapende kerkermeester met de sleutelbos, Wolfs cel), het Vissersgat, de kapel (de lichtpijlen), de wachtersgang, de wapenkamer (het zwarte harnas), de kerkertrap, het wachthuis, de binnenplaats (het enige gevecht), de voorburcht en de hoofdpoort, de torenpoort; tot 5b er is ook de rots met de toren (x 1880 … 2000) als decor; onweer en nevel in `tik()` |
| `ZeldaEiland5b` | «Nachtrots», de toren en Morgal (eiland 5b, deel `zelda-e5b`): de torenpoort (Tijmen breekt het zegel; een `Poort` in de muur van 5a) en de brugpoort, de Nevelbrug (drie nevelbruggen boven zee, twee pijlers), de voorhof (hub), de torenhal (Ida; daaronder Morgals schatkamer, open na het einde), de trap om de toren (west een trap van licht, noord de ingestorte trap met een haakpunt en Zelda's kamertje, oost drie wachtersvuren, zuid puin en weer een trap van licht), het torenbalkon (de laatste rust, Tijmen) met de ladder terug, de troonzaal (Morgal), het dak (Zelda in een kooi van nevel); het vangnet om de toren; de terugvaart (`vaart`); nevel en licht om de toren in `tik()` |
| `Morgal` | de baas van 5b (subklasse van `ZeldaBaas`): drie fases met adem ertussen: nevelgeesten (hij zweeft in zijn nevel), duistere bollen (sla ze terug met je zwaard: versuft), licht in zijn nevelhart (alleen lichtpijlen); eigen muziek per fase |
| `ZeldaFeest` | het feest op de open plek van het Lindewoud (het einde, vanuit `ZeldaHoofdstuk1`): Zelda en wie hielp (alleen voor wie Zelda bevrijdde), Zelda's gesprek = de vlag `zelda` en vuurwerk, wat de mensen van het Lindewoud daarna zeggen, de opdracht *De schat van Morgal*, lichtjes en de feestmuziek |
| `Asgrim` | de baas van 4b (subklasse van `ZeldaBaas`): zweeft onder vier koelkuipen, gooit sintels; een pijl of bom kantelt de kuip boven hem = gedoofd (dan doet een klap alles), de kuip loopt weer vol |
| `ZeldaZee` | bouwdeel `zelda-zee`: open zee met de Zilvermeeuw, voor de overtocht tussen eilanden |
| `ZeldaSpel` | de motor: sessies, zones, kamers en golven, baas, uitdagingen, puzzels, vondsten, bronnen, verteller, uitrusting, kaart, hint, gesprekken, missies, overtocht, listeners |
| `ZeldaKaart` | de kaart in vak 9 (sessie `zelda-kaart`): een gevulde kaart per speler met een eigen tekenaar; het eilandblad (land van boven, mist, zielvuren, dorpen en kerkers, jij, medespelers, de X van `/hint`) en de zeekaart (eilanden, vaarroutes, je schip op zee); meet elk eiland één keer per versie (cache in `plugins/KitPvP/zelda-kaart/`), ontdekken per speler |
| `ZeldaKaartBeeld` | de kaart zonder wereld (unit-tests): het raster van wat je ontdekte (en hoe het in `zelda.yml` staat), zee, kust en reliëf, perkament over wat je niet zag, de zeekaart, vlammetjes, stippellijnen, de naam, plaatjes |
| `ZeldaMetgezel` | Wiske, de fee: een kleine allay die naast je vliegt (alleen voor jou zichtbaar), praat en wijst |
| `ZeldaDier` | een dier dat meeloopt (vlag `dier:<soort>`): alleen het sneeuwvosje uit het verhaal (`VERHAAL`), vrije huisdieren uit (`AAN`); snuffelt verborgen vondsten op, graaft graafplekken op, gaat in een gevecht opzij |
| `ZeldaVoorwerp` | een voorwerp in de hotbar: het flesje, de bommen, de klauwhaak, de vuurstaf en de lichtpijlen |
| `ZeldaRegels` | regels zonder wereld (unit-tests): harten, hartstukjes, harnas, flesjes, welk doel, opnieuw vanaf hier, glijden over een glijvloer (en de kortste oplossing), de schade bij Zoutbaard en Grimmel, de hitte in de mijn |
| `ZeldaBaas` | baasgedrag (Grauwmoer: springt tussen paddenstoelen, valt eraf door een pijl, roept hulp, levensbalk); een andere baas = een subklasse met eigen `soort`, `helper`, `beweeg`, `schade`, `boem`, `weg` |
| `ZeldaMuziek` | nootblokmuziek in een klein notenschrift; drie lagen: achtergrond (altijd), lus (gevecht, baas), deuntjes; categorie «Nootblokken» |
| `ZeldaVoortgang` | opslag per speler in `plugins/KitPvP/zelda.yml` (laatste en bezochte rustplaatsen, verste per deel, vlaggen, missies, tellers, huisdiernaam, beste tijden, wat je ontdekte voor de kaart) |
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
- **Eb en vloed** (`Getij`, eiland 2b): een bak met waterlagen. Een **hendel** (`LEVER`; rechtsklik) zet het getij om:
  het water zakt of stijgt laag voor laag (een halve seconde per laag). Mag het niet (`Hendel.mag`, bv. zonder de
  maanparel), dan zit hij vast en zegt Wiske waarom (`Hendel.nee`). **Sluizen** zijn open bij eb (bij vloed drukt het
  water ze dicht), **vloeddeuren** alleen bij vloed; bij vloed zwem je naar plekken die bij eb te hoog liggen. Een
  minuut niemand in de buurt = terug naar de rust (`rustVloed`). Samen: het getij is van iedereen in de zaal.
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
  door je vosje opgesnuffeld; met `vereist` alleen te zien als je die vlag hebt; met `voorwerp` wel te zien, maar
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
- **Het sneeuwvosje** (`ZeldaDier`, eiland 3a): vlag `dier:sneeuwvos`; je krijgt hem met `spel.dier(p, "sneeuwvos")` en
  kiest meteen een naam (klein menu; dicht = de eerste). Loopt mee op elk eiland, zit als je stilstaat, botst niet,
  zegt niets (alleen een snuffel), gaat in een gevecht opzij zitten, snuffelt verborgen vondsten op (loopt erheen en
  gaat erbij zitten) en graaft **graafplekken** op (`Graaf`: hij loopt erheen, graaft even, en wie erbij staat krijgt de
  vlag). Onkwetsbaar, vecht niet. Vrije huisdieren (`dier:vos`, `kat`, `konijn`) blijven uit (`ZeldaDier.AAN`).
- **Klauwhaak** (vlag `klauwhaak`, vak 5, eiland 3a): rechtsklik; grijpt het haakpunt (`Haakpunt`, blok `HAAKPUNT`) waar
  je op mikt, binnen `ZeldaRegels.HAAK_BEREIK` (24) blokken en met niets ertussen; een paar graden ernaast mag
  (`ZeldaRegels.mik`). De ketting vliegt uit, dan word je erheen getrokken (onderweg geen val- of stikschade) en sta je
  op de landing. Mis = hij tikt ergens tegen en komt terug. Werkt op alle haakpunten van het eiland waar je bent.
- **Vuurstaf** (vlag `vuurstaf`, vak 6, eiland 4a): rechtsklik = een vlam (24 blokken; een paar graden ernaast mag). Een
  vuurschaal of lamp gaat aan (`Reeks` met `Hoe.KLIK` of `Hoe.VUUR`; bij `VUUR` is `voor` de plek om te mikken, de zelftest
  kijkt of je van daar elke schaal ziet), betoverd ijs (`Smelt`) smelt, een vijand krijgt een tik (ijsvijanden het dubbele).
- **Betoverd ijs** (`Smelt`, blok `ICE`): dicht tot de vuurstaf het smelt; dan lucht (een doorgang) of water (een koelplek).
  Wie het smolt (`s:<id>`) vindt het gesmolten; een minuut niemand in de buurt = weer bevroren. Gewoon ijs smelt bij fel
  licht (11+): geen lampen of lava binnen vijf blokken (soul lanterns mogen).
- **Hitte** (`Hitte`, eiland 4a): in het gebied word je warm, de ervaringsbalk vult zich (`snel` per seconde, met de
  koelsteen `koel`); in water, bij een zielvuur of in een bron koel je af. Vol = terug naar waar je het laatst afkoelde
  (of `terug`), zonder schade. `fel`: alleen het gloedharnas helpt (`ZeldaRegels.hittevast`: harnas 3 of beter), niets koelt.
- **Lava** (motor, eiland 4b): wie in lava stapt, springt er meteen uit naar waar hij het laatst veilig stond (`Sessie.veilig`:
  op de grond, geen lava vlakbij), met één hart schade (`ZeldaSpel.LAVA_SCHADE`), nooit dood en zonder brand; Wiske zegt
  het één keer. **Lava op een ritme** (`Ritme`): stenen die `laag` ms droog liggen en `hoog` ms onder de lava staan; een tel
  ervoor borrelt het; alleen als er iemand in de buurt is. `zelda lopen`: in lava sta je niet, de stenen tellen als droog.
- **Lichtpijlen** (vlag `lichtpijlen`, vak 7, eiland 5a): rechtsklik = een pijl van licht (zo hard als een volle boog); ook de
  boog schiet nu licht (hij heet dan Lichtboog). Elke pijl van wie de lichtpijlen heeft, is een lichtpijl (`ZeldaSpel.LICHT`,
  een spoor van licht): raakt hij **Morgals nevel** (`Nevel`, blok `TINTED_GLASS` met zwarte deeltjes; binnen een blok ernaast
  telt ook), dan verdwijnt die (`spel.verdrijf`); wie erbij was (`n:<id>`) vindt hem voortaan weg, een minuut niemand in de
  buurt = hij kruipt terug. Vijanden met de tag `zelda-nevel` (de nevelmijt) krijgen van licht het dubbele. Verder doet een
  lichtpijl wat een pijl doet (een kuip van Asgrim, een doelwit). Pijlen pak je in Zelda niet op.
- **Sluipen** (`Sluip`, eiland 5a): wachters met een lantaarn (een pillager zonder AI, onkwetsbaar) lopen of draaien op een vast
  ritme (`periode`, standen {fractie, x, y, z, yaw}, `ZeldaRegels.pose`); hun lichtkegel (`bereik` 6, `hoek` 32°, niets
  ertussen: `spel.ziet`) staat als warme stipjes op de vloer. Wie erin komt (in het `gebied`) is gezien: een alarm, het `hek`
  gaat vier tellen dicht, je staat weer bij `terug` (het zielvuur) en Wiske zegt één regel (`gezien`: de eerste, tweede, …
  keer). Bukken (shift): ze zien je pas van dichtbij. Wie de `finish` haalt, krijgt `l:<id>`. Het ritme loopt alleen als er
  iemand in de buurt is (anders geen wachters in de wereld); `actief` = voor wie ze er zijn (op Nachtrots: tot de
  binnenplaats). `pad` (rechte stukken) en `schuil` (de punten waar niemand je ooit ziet) zijn voor de test en de zelftest.
- **Nevelbrug** (`Nevel.brug`, eiland 5b): een brug of trap van wit steen, verstopt in Morgals nevel (je ziet alleen zwarte
  mist). Een lichtpijl hoeft nergens tegenaan: vliegt hij door de nevel, dan verschijnt het stuk, blok voor blok. Wie hem zag
  (`n:<id>`) vindt hem voortaan; een minuut niemand = weer nevel.
- **Vangnet** (`Val`, eiland 5b): wie in het gebied valt (meer dan een paar blokken) of in zee belandt (onder `laag`), staat
  meteen weer waar hij veilig stond, zonder schade; Wiske zegt het één keer. Vallen doet daar ook nooit pijn.
- **Morgal** (eiland 5b): zie ZELDA.md («5b · de toren en Morgal»). Een zwaai van een speler in de kamer gaat naar de baas
  (`ZeldaBaas.zwaai`); een baas weet wat hem raakte (`ZeldaBaas.schade(schade, door)`).
- **Het einde** (eiland 5b): Zelda bevrijd (`vrij:zelda`, het einde van `e5b`), de terugvaart (een bijzondere overtocht:
  `ZeldaHoofdstuk.vaart`) en het feest op het Lindewoud (`ZeldaFeest`): praten met Zelda zet de klaar-vlag van Nachtrots
  (`zelda`) = Story of Zelda voltooid (`spel.voltooid`): op de kaart «Story of Zelda · voltooid» met de hartstukjes, en
  `/hint` en Tijmen noemen hoeveel er nog liggen. Alles blijft open. De achtergrondmuziek kan van je voortgang afhangen
  (`ZeldaHoofdstuk.muziek(plek, stand)`: het feest).
- **Gloedschild** (vlag `gloedschild`, eiland 4b): het schild heet zo (zwart met gloed); Asgrims sintels ketsen erop af als
  je het omhoog houdt (een gewoon schild houdt de helft tegen).
- **Persoon voor sommigen** (`Persoon.zichtbaar`): alleen te zien (en dus te praten) voor wie het klopt; bv. een geit in
  het veld tot je hem vond, daarna in de stal.
- **Tekst**: alles op **één plek**, de actiebalk, één grijze regel tegelijk (spreker in groen), elke regel 2,5–6 s.
  Geen titels, geen chat. 
- **Muziek, altijd**: een achtergrondlied per streek (`muziek(Location)`: Thuis, Woud, Tempel, Veen, Haven, Zee,
  Zoutkom, Duinen, Vuurtoren, Lindedorp, Wad, Getijdentempel, IJshoeve, Sneeuwstorm, Wachterspost, Bergpad, IJspaleis,
  Sintelgat, Mijnen, Gloed, Smidse, Nachtrots, Muren, Kerkers, Toren, Feest; bazen Zoutbaard, Grimmel, Asgrim en Morgal in drie
  delen; gevecht Nachtwacht),
  daarboven de gevechts- of baaslus en losse deuntjes. Zachter met de schuif «Nootblokken».
- **Dood**: items blijven, geen doodsmelding; respawn bij je rustplaats. **Honger** vast op 17 (geen gewoon
  bijkomen door eten); genezen bij rustplaatsen, bronnen, flesjes, gewonnen kamers, de bakker in Lindedorp, de helft
  van de keren een hartje van een vijand, en **op adem komen**: buiten een gevecht, na 6 s zonder klap, elke 2 s een
  half hart. **Samen**: geen schade
  aan elkaar; geen huisdier van de plugin Huisdieren mee (`stand` = `geen`).
- **Kaart** (vak 9, `ZeldaKaart`): een echte kaart. **Vasthouden** = het eiland van boven: het land zoals het er staat
  (met reliëf), de zee met schuim, **perkament** over land waar je nog niet was, de zielvuren waar je was (blauw: waar je
  terugkomt), dorpen en kerkers zodra je er was, **jij** (een witte pijl), medespelers (groen) en je **volgende doel**
  (een rode X, dezelfde bron als `/hint`: `spel.wijzer`). Zolang je de kaart vasthoudt is je andere hand leeg (twee
  handen), daarna komt je schild terug. **Linksklik** = omslaan naar de **zeekaart**: alle eilanden waar je was (in
  kleur), waar je heen kunt (een vorm op perkament), Nachtrots in Morgals nevel zolang hij dicht is, de vaarroutes van je
  eiland (stippels) en de X; **op zee** slaat hij vanzelf om en vaart je pijl mee. Wat je ontdekte staat per speler in
  `zelda.yml` (vakjes van 8 × 8 blokken, 32 blokken om je heen); een oude stand wordt aangevuld uit je rustplaatsen,
  verhalen, vondsten en kamers, een eiland dat af is helemaal. In beheer toont de kaart alles. Licht: elk eiland wordt
  één keer per versie gemeten (chunks op de achtergrond, bewaard in `plugins/KitPvP/zelda-kaart/`), het blad alleen
  opnieuw getekend als er iets veranderde. **Rechtsklik** = het kaartmenu: de zielvuren van dit eiland, in de volgorde
  van het eiland (klik = reizen, **rechtsklik = opnieuw vanaf hier**: verhaal, uitleg en puzzels erna komen terug,
  spullen blijven; op een open eiland alleen tot de volgende rustplaats), de eilanden die open zijn (klik = **varen**),
  opdrachten, harten en hartstukjes, harnas, flesjes, **Uitleg: aan/uit**, terug naar de hub.
- **Overtocht**: varen = een paar tellen aan dek van de Zilvermeeuw op open zee (zeemansmuziek, schuim, Brecht aan
  het roer, een regel van Wiske of Brecht; de eerste keer de overtochtregels van het eiland), dan aan land bij de
  aanleg van het eiland (vlag `e:<eiland>`).
- **Hemel**: per speler; de dag schuift op van ochtend tot zonsondergang naarmate je verder komt (de baaskamer is nacht).
  Op het Lindewoud volgt hij wat je deed (een open eiland: je loopt ook terug); op het Zilverstrand de plek.
- **Grenzen**: wie buiten de delen van zijn eiland komt, gaat terug naar zijn rustplaats.
- **Entiteiten**: vijanden alleen tijdens een gevecht (tag `zelda`, niet opgeslagen, geen drops, branden niet);
  zwevende vondsten en personen alleen als er iemand in de buurt is; één Wiske (en eventueel één sneeuwvosje) per
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

### Eiland 3a: «De Witte Toppen», het dal en het dorp (`e3a`, deel `zelda-e3a`, x 1400 … 1579, z −480 … −180)

Zie de tabel in `ZELDA.md` («3a · het dal en het dorp»). Kort: de fjord (rust 0, Brecht) → IJshoeve (rust 1: Tijmen
geeft de sterrenlantaarn; Saar en Sven, Hagen) → het bevroren meer (Ys, Noor, de ijsbaan) → de bosrand (rust 2) → de
storm in, de pootjes volgen (alleen te zien met de lantaarn; zonder sta je weer aan de bosrand) → de kuil (twee
nevelwolven, het enige gevecht) → het sneeuwvosje (naam kiezen) → het vosje graaft bij de rotswand de oude deur op
(graafplek `deur`, poort `wachtersdeur`) → de Wachtersgang → de Wachterspost (rust 3, de klauwhaak) → drie haakpunten de
kloof uit → de Uitkijk (terug naar het dorp) → de Bergpoort: haakpunt in de boog (rust 4, einde `e3a`). Boodschap over
zee: Ys' brief voor Teun in Zoutkom → `fles4`. Sinds versie 2 is de lawine op het bergpad weg (3b sluit aan).

### Eiland 3b: «De Witte Toppen», het IJspaleis (`e3b`, deel `zelda-e3b`, x 1580 … 1720, z −480 … −180)

Zie de tabel in `ZELDA.md` («3b · berg en paleis: het IJspaleis»). Kort: het bergpad naar de berghut (rust 0, Ida) →
haak je de ijswand op en over de ijskloof → het voorplein (rust 1; de IJstuin achter een gebarsten muur: glijvloer,
sneeuwbloem, de hoorn van Ys; de sleebaan terug naar de berghut) → haak je naar de poorttoren, de hendel: de valbrug →
de Poortzaal: een bom bevrijdt Tijmen (de zonnesteen) → de Kristalzaal (rust 2) → in elke volgorde: de Glijzaal (glad
ijs, het Noorderkristal, de kristalkuil: rust 3) en de Wachtzaal (het enige gevecht), en de Spiegelzaal (licht van de
zonnesteen) met de kloof naar de Wapenkamer (het Zuiderkristal, het bevroren harnas) → Hagen in IJshoeve warmt het harnas
(`harnas2`) → de Oostgang (rust 4) en de troondeur → Grimmel → `hart3` → de toren op naar het Vorstbaken (rust 5,
`baken3`, einde `e3b`): de Gloedberg is aangekondigd (`Zelda.KOMT`). Boodschap over zee: de sneeuwbloem voor Hanne → `fles5`.

### Eiland 4a: «De Gloedberg», Sintelgat en de mijnen (`e4a`, deel `zelda-e4a`, x 1060 … 1259, z 180 … 480)

Zie de tabel in `ZELDA.md` («4a · Sintelgat en de mijnen»). Kort: de Zwarte Baai (rust 0, Brecht) → Sintelgat (rust 1:
Tijmen geeft de koelsteen; Okker, Dina, Fenna, Kobus) → de mijn: de Lorriehal (rust 2) → een bom op de Oude Gang → haak
je over de Schachtput → de Vonkkamer (rust 3): de vuurstaf, drie lampen = het hek → de hete gangen (hitte; bevroren
water smelten = koelplekken) → de Gloedader (rust 4, gloedijzer; de Daaltrap terug) → in Sintelgat de esse aansteken →
Okker: het gloedharnas → de gloed: de gloedtrap (rust 5), de Askamer (het enige gevecht) → de Smidsepoort (rust 6,
einde `e4a`; daarachter de Smidse, 4b) → de Asgang naar het Uitzicht (rust 7, Ida). Zijmissies: de lorrie (Dina),
de sporen (Fenna, race naar de Laadkade), de mijnvuren (Kobus). Boodschap over zee: de gloedkool van Okker voor Hagen
(IJshoeve) → `fles6`; en de vuurstaf smelt het ijsblok bij Hagen (`hs:witte-toppen-3`).

### Eiland 4b: «De Gloedberg», de Smidse (`e4b`, deel `zelda-e4b`, x 1260 … 1400, z 180 … 480)

Zie de tabel in `ZELDA.md` («4b · de Smidse»). Kort: de Smidsepoort (open voor wie de Askamer won) → de Smidsehal (rust 0,
bron; Tijmen leest het schrift; de schildplaat op het aambeeld) → in elke volgorde, in een rondje: de Kettingzaal (haak je
naar de pijler: een vonk; de kettingbrug: brand het touw door), de Stuwdam (een bom tegen de dam: water op lava = een pad
van obsidiaan naar de vonk; het bijlblad voor Kees in de waterkelder), de Gietzaal (drie ovens met de vuurstaf: de
gietrivier zakt; lava op een ritme over de stenen naar de vonk), en Okker in Sintelgat (het Gloedschild) → de Hartdeur
(drie vonken + het Gloedschild) → de voorkamer (rust 1, bron; de nevelnis: `hs:gloedberg-4` met de lichtpijlen) → het Hart:
Asgrim (kantel de kuip boven hem) → `hart4` → de Schoorsteen (rust 2; vier haakpunten) → de krater (rust 3): het
Gloedbaken (vuurstaf) → `baken4` → de uitkijk: de Nevelring breekt (einde `e4b`); Nachtrots staat in `Zelda.KOMT`.
Boodschap over zee: Kees (Lindewoud) en het bijlblad → `fles7`.

### Eiland 5a: «Nachtrots», de muren (`e5a`, deel `zelda-e5a`, x 1640 … 1879, z −150 … 150; het vak van 5b x 1880 … 2000)

Zie de tabel in `ZELDA.md` («5a · de muren»). Kort: de visserskreek (rust 0, Brecht; Tijmen verstopt) → Wolf achter zijn
raampje (de muursleutel) → de klippen (een haak over de kloof, een bom in de smokkelgang) → de muurvoet (rust 1, de muurdeur)
→ de weergang: sluipen langs vier nachtwachten (balkonnetjes om je te verstoppen) → de Kerkertoren omlaag → de kerkers (rust
2) → in elke volgorde: de cellengang (sluipen langs drie cipiers, nissen) naar de wachtkamer (de sleutelbos van de slapende
kerkermeester; de cellen open: Wolf, Wolkje, Bas, Kiek, Joppe), en de kapel (een bom, een haak over de put, drie vuren met de
vuurstaf: de lichtpijlen; nevel voor de deur) → de wapenkamer (nevel; het zwarte harnas) → Wolf in de kreek (het wachtwoord
«Maanloos»; het Vissersgat is de korte weg) → Mira op het Zilverstrand: het lichtharnas → de kerkertrap (nevel) → het
wachthuis (rust 3) → de binnenpoort → de binnenplaats (het enige gevecht) → de torenpoort (rust 4, einde `e5a`): de toren
(5b) staat in `Zelda.KOMT`. Boodschap over zee: Hanne en het maanmos → `fles8`. Na de binnenplaats zijn de wachters weg en
gaan de westpoort en de hoofdpoort open (een rondje terug naar de muurvoet).

### Eiland 5b: «Nachtrots», de toren en Morgal (`e5b`, deel `zelda-e5b`, x 1880 … 2000, z −150 … 150)

Zie de tabel in `ZELDA.md` («5b · de toren en Morgal»). Kort: Tijmen breekt het zegel van de torenpoort → de brugpoort en de
Nevelbrug (drie lichtpijlen door de nevel) → de voorhof (rust 0, bron; de torenhal met Ida) → de trap om de toren: west een trap
van licht, noord de ingestorte trap (klauwhaak; Zelda's kamertje met een bron), oost drie wachtersvuren (vuurstaf: de trap
verschijnt), zuid puin (bom) en weer een trap van licht → het torenbalkon (rust 1, bron, Tijmen; de ladder terug) → de
troonzaal: Morgal (drie fases) → de ladder naar het dak: Zelda (einde `e5b`) → Brecht in de kreek: de terugvaart → het feest
op het oefenveld van het Lindewoud: praten met Zelda = `zelda`, «Story of Zelda · voltooid». Daarna: Zelda's kroontje uit
Morgals schatkamer (`hs:nachtrots-4`). Rond de toren vangt de nevel je op als je valt.

### Eiland 2b: «Het Zilverstrand» oost, de Getijdentempel (`e2b`, deel `zelda-e2b`, x 1230 … 1360, z −480 … −180)

Zie de tabel in `ZELDA.md` («2b · oost, de Getijdentempel»). Kort: de dijk → het Getijdenplein (rust 0, Tijmen: de
maanparel; bordjes naar het wrak, de poelen, de tempel en Zoutkom) → het wad met het wrak van de Kraai (Lorre, het
logboek) en de getijdenpoelen (Roos, de parelduik) → de tempel (rust 1): de Getijdenzaal staat droog; door de sluis
(open bij eb) naar de Spoelkamer (het enige gevecht) → een bom op de pijpgang → het raderwerk: de grote hendel
(maanparel) = vloed → door de vloeddeur omhoog naar de drempel (rust 2) → de Ankerpoort (de sleutel heeft Teun in
Zoutkom) → de Draaikolk: Kapitein Zoutbaard (een bom bij hem of zijn ton = versuft) → `hart2` → de toren op naar het
Getijdenbaken (rust 3, `baken2`, einde `e2b`). Daarna staan de Witte Toppen als aangekondigd eiland op de kaart
(`Zelda.KOMT`). Boodschap over zee: Lotje (Lindewoud) en de zingende schelp → `fles3`.

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
  gebarsten muren op het pad (een echte bom), eb en vloed (een echte klik op de hendel, dan het water, de sluizen en de
  vloeddeuren nagekeken), een echte bom in de ton van Zoutbaard (hij moet versuft zijn), wat met de voorwerpen van nu te
  halen is, de zijmissies, Brecht (klik in zijn menu = varen naar het volgende eiland), een aangekondigd eiland op de
  kaart (`Zelda.KOMT`), op de Witte Toppen de klauwhaak (een echte haak naar elk haakpunt op het pad), het vosje (naam
  kiezen in het menu) en de graafplek (het vosje moet hem echt opgraven), de glijvloeren (de eerste zet een echte stap
  op het ijs, daarna de oplossing; je moet bij het doel uitkomen) en Grimmel (hij stormt naar je toe, je haakt je echt
  weg tijdens het gevecht, hij moet tegen een zuil lopen en duizelen), op de Gloedberg de vuurstaf (echte vlammen op de
  lampen, de esse en de mijnvuren; op betoverd ijs, dat moet smelten) en de hitte (zonder koelsteen snel te heet en terug
  op de koele plek, met de koelsteen langzamer, afkoelen in water, met het gloedharnas niets; in de gloed alleen het
  harnas; verder houdt de test je koel, want hij teleporteert), in de Smidse de lava op een ritme (hij moet stijgen en
  zakken; de test zet je op een steen als de lava erover staat: je moet eruit springen, met hoogstens één hart schade) en
  Asgrim (een echte pijl op de kuip boven hem: hij moet gedoofd neerploffen en dan gewoon schade krijgen), op Nachtrots het
  sluipen (eerst wordt hij gezien: terug bij het zielvuur, het hek dicht, Wiske zegt iets; dan sluipt hij écht van schuilplek
  naar schuilplek en gaat pas als niemand hem onderweg ziet, op het ritme van de wachters), Morgals nevel (een echte
  lichtpijl) en praten met iemand op een ander eiland (hij vaart naar Mira en terug), in de toren de nevelbruggen (een echte
  lichtpijl door de nevel), het vangnet (hij valt van hoog: de nevel vangt hem op, zonder schade) en Morgal (zijn geesten
  verslaan; een echte zwaai vlak voor een duistere bol: Morgal moet versuft zijn en een klap alles doen; in zijn nevelhart een
  echte lichtpijl: die moet raken, een zwaard niet), Zelda op het dak, de terugvaart en het feest (hij vaart naar het
  Lindewoud, praat met Zelda: `zelda`, en de kaart zegt «voltooid»), de kaart op elk eiland (gemeten; je pijl op je plek
  en in je kijkrichting, waar je staat is ontdekt, je zielvuur blauw, de X precies waar `/hint` heen wijst en anders geen;
  op de zeekaart het eiland in kleur) en op zee (de zeekaart met je schip en de vaarroute), dan drie rondes langs alle eilanden voor de
  boodschappen over zee, en aan het eind via de kaart terug naar een eerder eiland om daar met de bommen, de
  klauwhaak en de vuurstaf te halen wat kon (`hs:lindewoud-4`, de richel `hs:lindewoud-3`, de Meeuwenrots
  `hs:zilverstrand-3`, het ijsblok bij Hagen `hs:witte-toppen-3`, met de lichtpijlen de nevelnis in de Smidse `hs:gloedberg-4`); `dood` = sterven in de eerste kamer (respawn bij je rustplaats, spullen houden, kamer terug). **Wist eerst de
  voortgang van die speler** (alleen voor tests; zijn testtijd komt op het tijdenbord, `reset` haalt hem weg). Met `alles vanaf
  <deel>` (bv. `vanaf e5b`, voor wie aan één deel werkt) telt alles vóór dat deel als gedaan en begint de route bij de aanleg
  van zijn eiland (de rondes gaan dan alleen langs dat eiland en het Lindewoud). `kitpvp zelda verder <speler>` — door de poort,
  en sta je op je verste rustplaats? (na uit- en inloggen).
- `kitpvp zelda test <speler> motor` — de motor met een echte speler: Wiske, muziek, `/hint` (twee keer), de wegwijzer
  (Tijmen noemt het dichtstbijzijnde doel van een groep), bron (met alleen Tijmens flesje),
  flesje, hartstukjes, harnas, geen vrij huisdier (een `dier:vos` loopt niet mee), het sneeuwvosje (verschijnt, loopt
  mee, snuffelt de holle Oude Eik op), gesprek en zijmissie met beloning, opnieuw vanaf een
  rustplaats, uitleg uit, de bommenzak (vak 4) en een echte bom tegen de tempelmuur, het getij (zonder maanparel zit de
  hendel vast en zegt Wiske waarom; met: het water stijgt laag voor laag), de klauwhaak (vak 5, een echte haak naar de
  richel in de Kloof, en mis: dan blijf je staan), de vuurstaf (vak 6, een echte vlam op een vuurschaal van de
  tempelruïne, van een afstand), de lichtpijlen (vak 7, een pijl van licht, en Morgals nevel verdwijnt), varen en aankomen, tip na
  doodgaan in een kamer, het kaartmenu, de kaart in vak 9 (vasthouden: het schild weg, linksklik: omslaan en terug,
  rechtsklik: het menu, Wiske legt hem uit; weer weg: het schild terug), en weg = alles weg. Wist eerst en
  na afloop de voortgang van die speler (alleen voor tests).
- `kitpvp zelda lopen <eiland> [zonder <voorwerp>] [dicht <poort>]` — is alles te belopen? Volgt vanaf de aanleg elke
  plek waar je kunt staan (stap ≤ 1 omhoog, ≤ 3 omlaag, een gat van één blok, ladders, zwemmen; deuren en gebarsten
  muren open; met de klauwhaak: van elk haakpunt dat je ergens vandaan kunt raken naar zijn landing; op een glijvloer
  glijd je, zoals in het spel) en noemt wat je
  niet haalt (rustplaats, persoon, vondst, kamer, uitdaging, verhaal, bron, spiegel, bel, graafplek, haakpunt).
  `zonder klauwhaak` / `dicht wachtersdeur` = alsof je dat voorwerp niet hebt of die poort dicht blijft: zo zie je wat
  erachter ligt en of je er omheen kunt. Deuren, poorten en bruggen tellen
  als open. Laadt de chunks van het eiland; doet ook mee in `test … alles` (voor elk eiland; over de Kloof van het
  Lindewoud kom je ook via de ladders en de beek).
- `kitpvp zelda kaart` — PNG's van elk eilandblad (zonder mist, met alle zielvuren en tekens) en van de zeekaart in
  `plugins/KitPvP/kaart/zelda-kaart-*.png`; `kitpvp zelda kaart <speler>` — zijn kaart zoals hij hem ziet (beide kanten);
  `kitpvp zelda kaart opnieuw [eiland]` — opnieuw meten.
- `kitpvp zelda vaar <speler> <eiland>` — de overtocht naar een eiland. `kitpvp zelda vlag <speler> <vlag>` — een
  vlag aan (nog eens = uit), bv. `fles1`, `hs:test`, `harnas2`, `dier:vos`, `o:uitleg-uit` (helpen en testen).
  `kitpvp zelda hint <speler>` — de hint die hij nu zou krijgen.
- `kitpvp bouw zelda` — het Lindewoud opnieuw bouwen, `kitpvp bouw zelda-e2a` het Zilverstrand west, `kitpvp bouw
  zelda-e2b` de Getijdentempel, `kitpvp bouw zelda-e3a` het dal van de Witte Toppen, `kitpvp bouw zelda-e3b` het
  IJspaleis, `kitpvp bouw zelda-e4a` Sintelgat en de mijnen, `kitpvp bouw zelda-e4b` de Smidse, `kitpvp bouw zelda-e5a` Nachtrots, `kitpvp bouw zelda-e5b` de toren, `kitpvp bouw zelda-zee` de zee. `kitpvp kaart [begin]` maakt ook `zelda-*.png` (met `begin` alleen de plaatjes
  waarvan de naam zo begint, bv. `zelda-e3b`: lichter) (het Lindewoud: boven, thuis, dorp, noord, tempel, veen,
  haven en zijaanzichten; `zelda-e2a-*`: boven, dorp, kelder, kaap, duinen, rots; `zelda-e2b-*`: boven, tempel, kelders
  (en van opzij), de zaal van opzij, wrak, poelen, de duikgang van opzij; `zelda-e3a-*`: boven, dorp, meer, bos, kuil,
  gang, kloof (boven en beide wanden van opzij), bergpoort (boven en van opzij); `zelda-e3b-*`: boven, bergpad, paleis
  (net onder de plafonds), glijzaal, tuin, troon, het geheel en de toren van opzij; `zelda-e4a-*`: boven (met de kegel
  van 4b), dorp, baai, mijn (de bovenmijn en de gloed, net onder de plafonds), hete gangen, de mijn van opzij (door de
  Daaltrap), de Vonkkamer van opzij en het geheel van opzij; `zelda-e4b-*`: boven, de zalen (net boven de vloer), de kloof
  van de Kettingzaal, de krater, het Hart en de schoorsteen van opzij, de Smidsehal en de Kettingzaal van opzij; `zelda-e5a-*`:
  boven (met de toren van 5b), de kreek, de weergang, de burcht (op het plateau), de kerkers, en van opzij door de cellengang,
  door de kapel en het geheel; `zelda-e5b-*`: boven, de brug, de trap om de toren per richel (`trap1` … `trap5`, net boven de
  vloer), de torenhal, de schatkamer, de troonzaal, het dak, de toren van vier kanten en het geheel van opzij; `zelda-feest`: het
  oefenveld van het Lindewoud).
- `/zelda ga <plek>` kent ook puzzels, uitdagingen en poorten (`vuren`, `kloof`, `doelen`, `spiegels`, `doornen`,
  `parelduik`, `ankerpoort`, `ijsbaan`, `wachtersdeur`, `luik`, `valbrug`, `sleebaan`, `troonpoort`, `spiegelzaal`,
  `lampen`, `esse`, `mijnvuren`, `sporen`, `daaltrap`, `asgang`, `smidsepoort`, `kettingbrug`, `koelpad`, `ovens`,
  `hartdeur`, `gloedbaken`, `muurdeur`, `vissersgat`, `cel-wolf`, `cel-wolkje`, `cel-bas`, `cel-kiek`, `cel-joppe`, `cel-mos`,
  `binnenpoort`, `westpoort`, `hoofdpoort`, `kapelvuren`, `torenpoort`, `torenvuren`, `kooi`, `torenladder`, `schatkamer`),
  de toren (`voorhof`, `balkon`, `nachttroon`, `zelda-e5b` op het dak, `haak-torenrichel`, `muur-torenpuin`, nevelbruggen
  `nevel-brug1` … `nevel-brug3`, `nevel-trap1`, `nevel-trap2`), het feest op het Lindewoud (`feest` = Zelda daar, de gasten
  `tijmen-feest`, `hendrik-feest`, `wolf-feest`, …), sluipstukken (`weergang`, `kerkerwacht`: bij het begin), Morgals
  nevel (`nevel-<id>`: `nevel-kapeldeur`, `nevel-wapenkamer`, `nevel-wapenkist`, `nevel-nevelgang`, `nevel-kerkertrap`,
  `nevel-nevelnis`; voor de nevel), lava op een ritme (`gietvloer`), betoverd ijs (`ijs-<id>`: `ijs-ijsblok`, `ijs-koel1`,
  `ijs-koel2`, `ijs-koelgang`; voor het ijs),
  getijden en hun hendels (`getij`, `hendel1`, `hendel2`), gebarsten muren (`muur-<id>`), haakpunten (`haak-<id>`, bij de
  landing), graafplekken (`graaf-<id>`), glijvloeren (`glijzaal`, `glijtuin`: voor het ijs), en een persoon die op meer
  delen staat met het deel erbij (`tijmen-e2b`, `tijmen-e3a`, `tijmen-e3b`, `tijmen-e4a`, `tijmen-e4b`, `tijmen-e4b-2` (op de uitkijk), `tijmen-e5a`, `tijmen-e5a-2` (bij de torenpoort), `wolf-e5a` (in de kreek),
  `brecht-e5a`,
  `brecht-e3a`, `brecht-e4a`, `ida-e4a`; de geiten in de stal: `bles-e3a`); het zoekt daar een plek om te staan.

## Ruimte in de wereld

| gebied | x | z | wat |
|---|---|---|---|
| hub | −195 … −125 | −35 … 35 | midden −160/0, binnenkomst −140,5/0,5 (kijkt west) |
| colosseum + kitruimte | −50 … 50 | −60 … 50 | midden 0/0, kitruimte boven de noordmuur (z −57 … −35) |
| oefenarena + kitruimte | −24 … 24 | 51 … 134 | midden 0/110, kitruimte z 63 … 87 |
| kasteel + kitruimte | −42 … 42 | −188 … −94 | midden 0/−150, gracht rondom, kitruimte z −119 … −95 |
| **Zelda** | **1024 … 2047** | **−512 … 511** | gereserveerd (`Zelda.GEBIED`); het Lindewoud bouwt x 1024 … 1639, z −179 … 179 (land x 1045 … 1492, z −105 … 105, de rest zee); het Zilverstrand x 1060 … 1360, z −480 … −180 (2a tot 1229, 2b vanaf 1230); de Witte Toppen x 1400 … 1720, z −480 … −180 (3a tot 1579, 3b vanaf 1580); de Gloedberg x 1060 … 1400, z 180 … 480 (4a tot 1259, 4b vanaf 1260: de kegel en de krater); Nachtrots x 1640 … 2000, z −150 … 150 (5a tot 1879, 5b vanaf 1880: de toren); de zee (overtocht) x 1840 … 2040, z −510 … −330; de vakken van de andere eilanden: tabel in `ZELDA.md` |

Alle huidige gebieden liggen in de chunks die de plugin geladen houdt (`onEnable`). Het Zelda-gebied niet: alleen
tijdens het bouwen staan er tijdelijk tickets, verder laden spelers zelf wat ze zien (de server heeft 1–2 GB).

## Bouwen en beheer

- `plugins/KitPvP/gebouwd.yml`: `grond` (vloerhoogte) en per deel de gebouwde versie (`hub`, `arena`, `kasteel`,
  `zelda`, `zelda-e2a`, `zelda-e2b`, `zelda-e3a`, `zelda-e3b`, `zelda-e4a`, `zelda-e4b`, `zelda-e5a`, `zelda-e5b`, `zelda-zee`). Een oude `gebouwd.txt` (tot versie 3) wordt gelezen voor de grondhoogte en daarna vervangen.
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
  gebarsten muur, elk voorwerp in een eigen vak; eiland 2b: het getij in rust (water, sluizen, vloeddeuren), hendels op
  hun plek en niet voor iedereen, de kruittonnen van Zoutbaard en een vrije plek bij elke ton, een aangekondigd eiland
  dat niet ook echt bestaat; eiland 3a: graafplekken vrij en met vloer, niet meer dieren dan spelers, vrije huisdieren
  uit, haakpunten met een vrije landing; eiland 3b: glijvloeren (ijs onder elk ijsvakje, start en doel om te staan, en
  met de oplossing kom je bij het doel), de ijszuilen van Grimmel in rust, de klauwhaak werkt in zijn zaal en er is een
  haakpunt om je weg te trekken, Hagen (3a) staat op het pad; eiland 5a: Morgals nevel (dicht in rust, te zien vanaf de plek
ervoor), sluipen (het hek open in rust, een pad om te staan, wachters niet in een muur, schuilplekken die nooit gezien worden,
en elk stuk tussen twee schuilplekken is met de echte blokken op minstens 5 % van het ritme ongezien te lopen), Mira (2a) staat
op het pad; eiland 5b: nevelbruggen (lucht in de nevel en een brug in het licht, dicht in rust, vanaf de plek ervoor te zien),
vangnetten (in het gebied, een vrije proefplek), Morgals plekken (in de troonzaal en vrij; wie op de vloer staat heeft een vloer),
Tijmen bij de torenpoort (5a) en Zelda op het feest (het Lindewoud) staan op het pad).
