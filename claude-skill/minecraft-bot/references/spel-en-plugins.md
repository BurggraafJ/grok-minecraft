# Het spel en de plugins

Stand 16-11-2026. De actuele lijst staat in `server/server.json` (`mc plugin lijst`); de details in `BOT.md`
(«Chat, groepen en meldingen», «Gemak in survival», «De wereld en het startersdorp») en de docs per plugin.

## Hoe spelers het beleven

- Join op `45.43.163.23:25576` met Minecraft **Java Edition**, elke versie van ±1.10 tot de nieuwste (ViaVersion +
  ViaBackwards op de backends; Velocity laat 1.7–26.3 binnen). Alleen premium-accounts, geen whitelist.
- Je komt altijd eerst in de **lobby** (tuindorp, tien parkour-levels, veilig: geen schade of honger), daarna telt het
  af (5-4-3-2-1) naar de server waar je het laatst was; annuleren kan. Poorten en `/lobby`, `/survival`, `/kitpvp`,
  `/zelda`. Eén chat en één tablijst over het hele netwerk, met een servertag.
- **Beroepen** (proxy, plugin `netwerk`): iedereen begint als «Wildvreemde»; admins geven er een (`/beroep <speler>
  <beroep>`: Bouwer, Dokter, Boer, Mijnwerker, Smid, Handelaar, Ontdekker, Ondernemer, Dierentemmer). Het beroep staat
  als prefix in chat en tablijst op elke server. `/spelers` toont wie het netwerk kent.

## Plugins van buiten (Modrinth, vaste versie + hash)

| Plugin | Op | Waarvoor |
|---|---|---|
| ViaVersion + ViaBackwards | survival, lobby, kitpvp | andere Minecraft-versies binnenlaten |
| LuckPerms + VaultUnlocked | survival | rechten (staan in `server.json` → `rechten`) en de brug voor Essentials |
| EssentialsX (+ Spawn) | survival | /home, /tpa, /back, /warp, /spawn, /msg …; berichten in de taal van de client |
| WorldEdit, WorldGuard | survival | bouwen voor admins, gebieden beschermen |
| Chunky | survival | de wereld vooraf genereren (op TPS) |
| ChestSort+, TreeFeller, Veinminer, GravesX | survival | sorteren, bomen in één keer, ertsaders, graf bij doodgaan |
| CoreProtect | (wachtlijst) | weigert 26.3; komt erbij zodra er een versie is |

## Eigen plugins (`plugins/<naam>`, Java, Gradle, gebouwd door `mc`)

| Plugin | Op | Wat |
|---|---|---|
| `netwerk` | velocity | lobby eerst + aftellen naar je laatste server, /lobby /survival /kitpvp /zelda, gedeelde chat en TAB, join/leave, beroepen, /spelers, /help-tips |
| `lobby-melding` | lobby | de tuinlobby: poorten, parkour (10 levels, records, top 5), veilig, wachtkamer bij herstarten |
| `opmaak` | alle backends | join/leave-regel, chat met groepsprefix, advancement-meldingen, tablijst, eigen /help |
| `startersdorp` (+ datapack) | survival | één keer per wereld een klein Minecraft-dorp op een vlakke plek bij de spawn, spawn bij de bel, kaartje |
| `extras` | survival | /admin: extra's live aan/uit, instant oven |
| `honger` | survival | honger 25% langzamer, niemand vliegt in survival |
| `pijl` | survival | richtingspijl naar de dichtstbijzijnde speler en eigen plekken, /pijl-menu |
| `kist`, `kistzicht` | survival | /kist (54 vakken per speler); hologram met kistinhoud, /unload |
| `huisdieren` | alle backends | getemde dieren raken KO in plaats van dood; je huisdier loopt mee naar lobby en kitpvp |
| `veiligafk` | survival | /afk: na 10 s stilstaan beschermd |
| `handlicht` | survival | licht om je heen met een lichtgevend item in je hand |
| `lobbyreis` | survival | /parkour: je plek onthouden, naar de lobby-parkour en met /terug weer terug |
| `kitpvp` | kitpvp | KitPvP met een hub, modi (PvP Arena, PvP Castle), kitruimtes, arena's, stats; wereld uit code (versies per deel) |
| `zelda` | kitpvp | **Story of Zelda**: een eigen avonturenspel in de wereld `zelda` |

Een eigen plugin maken: `plugins/README.md` en `AGENTS.md` (opbouw zoals `lobby-melding`, `paper-api` als compileOnly,
release 25, plugin.yml met `${apiVersion}`), op de box `mc plugin nieuw <naam>`, klaar = CI groen.

## KitPvP (`plugins/kitpvp/MODI.md`)

Binnenkomen in de **hub**, een modus kiezen via een poort of het kompas (`/modus`), dan de **kitruimte** (galerij met
7 pantserstandaards) en de **arena**. Modi nu: PvP Arena (poort zuid) en PvP Castle (poort noord); de westpoort verwijst
naar de Zelda-poort in de lobby. Een modus toevoegen: klasse die `Modus` of `KitModus` implementeert, registreren in
`KitPvP.onEnable`; het register vult poorten, menu, respawn, sidebar en `kitpvp zelftest` vanzelf. De wereld wordt uit
code gebouwd (`Deel` + `Bouwer`, versie per deel in `plugins/KitPvP/gebouwd.yml`, verdeeld over ticks).

## Story of Zelda 2.0 «De Glazen Zee» (`plugins/zelda/ZELDA.md`, techniek in `TECHNIEK.md`)

- Een avontuur over **vijf eilanden** (Lindewoud, Zilverstrand, Witte Toppen, Gloedberg, Nachtrots), verbonden door het
  schip de Zilvermeeuw; kerkers, puzzels, bazen, het verhaal eindigt met Zelda vrij en een feest. Hoofdstuk 1 viel goed
  bij Jelle («erg leuk, verrast hoe goed het is»).
- Een **kaart** in vak 9 (eiland van boven met mist, zeekaart met vaarroutes), **vier niveaus** met sterren en een
  heldenrand, **samen spelen** (uitnodigen, delen, zwaarder met meer spelers), **drie spellen per speler**, en
  bijsturen voor beheer (`zelda.beheer`).
- Sinds 30-10 een **eigen plugin met een eigen wereld `zelda` op de kitpvp-server** (geen extra backend: dat kost geld
  of een handeling), een eigen poort in de lobby, eigen tag `[Zelda]` en sidebar. De lobby zet het cookie
  `burggraaf:plek` = `zelda`, zodat je meteen in Zelda verschijnt.
- Testen: `kitpvp zelda test …`-modi en `zelda lopen` (een script dat het spel doorloopt); losse eindjes staan per eiland
  in `ZELDA.md`.

## Wereld en spawn

Survival is een gewone willekeurige wereld met een klein startersdorp bij de spawn. Gamerules, rechten en
server.properties-instellingen staan in `server.json` en worden live gezet. Jelles keuzes onderweg (25-10): geen
whitelist, geen startkit, slapen met één speler, honger langzamer, nicknames overal, geen vliegen in survival.
