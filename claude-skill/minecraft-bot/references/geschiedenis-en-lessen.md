# Geschiedenis, onderzochte opties en lessen

## Tijdlijn

| Wanneer | Wat |
|---|---|
| 25-10 middag | Jelle brengt een bouwopdracht die Grok zelf schreef (Velocity + Paper, Bot beheert alles, blue/green) en vraagt «maak het beter, maak een GitHub-repo». Claude Code bouwt `grok-minecraft`: `mc`, box-modus (Velocity + lobby + survival + staging, watchdog), GitHub als bron (`server.json`), CI met een echte server. |
| 25-10 | Online onderzoek: veel Grok-boxen laten sinds ±7-10 alleen HTTPS naar buiten en pauzeren zonder verbonden app. Daarop: tunnel automatisch (bore, anders ngrok via 443), GitHub via HTTPS met een fine-grained token, dagelijkse wereldbackup als release in de privé-repo `grok-minecraft-backups`, startersdorp, Essentials in de taal van de client, VaultUnlocked, Chunky-pregeneratie. |
| 25-10 ±16:45 | Jelle plakt `PROMPT.md` in een nieuwe Grok Bot. De Bot installeert, pusht zelf een fix (ngrok: CRL-controle uit, want plain HTTP is dicht). Whitelist uit op Jelles woord. |
| 25-10 avond | De Bot bouwt een stroom eigen plugins (chatopmaak, groepen, slapen met één speler, honger, pijl, kist, kistzicht, huisdieren, veilige AFK, /admin-menu) en een parkourlobby. De CI staat intussen een dag rood zonder dat het opvalt. |
| 26-10 nacht | Plugin `netwerk` op Velocity (eerst de lobby, aftellen naar je laatste server), tuinlobby met parkour, KitPvP-server, gedeelde chat en TAB. |
| 26-10 | Hosting-onderzoek (hieronder). Keuze: **Sparked Host Neptune**. 17:59 gaan de servers op de box uit; paneel-modus live. Regels: Velocity alleen herstarten bij 0 spelers; centrale admins en beroepen. Claude Code repareert de CI; de Bot zet de paneel-modus in GitHub, laat `mc ensure` de CI meelezen en splitst grote GitHub-backups. |
| 27–30-10 | **Story of Zelda 2.0 «De Glazen Zee»**: vijf eilanden, kerkers en bazen, een kaart, vier niveaus, samen spelen, meerdere spellen. 30-10 wordt het een eigen plugin met een eigen wereld en een eigen poort in de lobby (Jelle: «apart ervan maken … zodat het voelt alsof het iets anders is dan kitpvp»). |
| 30-10 → 16-11 | Geen nieuwe commits; de dagelijkse GitHub-backup loopt door (±1,3 GB per dag). |

## Hosting: wat er onderzocht is (26-10)

Jelles eisen: de Bot moet alles zelf kunnen («never nooit iets hoeven te doen»), betalen met iDEAL, maandelijks
opzegbaar, eerst ≤ €25, daarna «rond de 30 mag ook».

| Optie | Uitkomst |
|---|---|
| Op de Grok-box blijven (tunnel) | Werkt alleen zolang de Grok-app open staat (de box pauzeert) en met ngrok (account + kaartverificatie). Spelers vlogen eruit; verlaten. |
| **TransIP V3** (2 vCPU, 4 GB, €24,20 incl.) | Voldoet aan alles: iDEAL + incasso, maandelijks opzegbaar, API met cloud-init (`installFlavour cloudinit`, `base64InstallText`). Maar 4 GB is krap. V4 (8 GB) ±€60,50: te duur. |
| mijn.host Unmanaged M (2 cores, 8 GB, €30,25 incl.) | Geen cloud-init (eerste installatie vraagt SSH, dat de box niet kan), API-toegang alleen via een ticket, opzegtermijn niet bevestigd. |
| Hetzner, Hostinger | Geen iDEAL. |
| **Sparked Host** (gekozen) | Minecraft-host met Pterodactyl-API: de Bot doet alles via HTTPS (console, bestanden, power, backups, schema's, startvariabelen). Vast adres, 24/7, ±10–15 ms vanuit NL. Splits = subservers uit één pakket. Geen root; splitsen en bestellen alleen in het paneel. Neptune 8 GB ±€18,51. |

Wil Jelle ooit 24/7 meer ruimte: Sparked Milky Way (16 GB, 4 splits) of een VPS met cloud-init (TransIP); `mc` werkt
op een gewone Linux-machine in box-modus.

## Lessen (die je niet opnieuw wilt leren)

- **Grok-box:** alleen 443 naar buiten, geen inkomende poorten, pauze zonder verbonden app, «Computer Update» wist
  pakketten (alles onder `/workspace`), skills worden stil herschreven. Niet om de netwerkinrichting heen werken.
- **De CI moet iemand lezen.** Een dag rood zonder melding. Nu meldt `mc ensure` rood; na elke push zelf kijken.
- **Sparked:** splits hebben geen internet en geen DNS (Paperclip, plugin-libraries, `op <naam>`); node-poort 8080 is
  vanaf de box dicht (upload via `files/write` in delen < 80–100 MB, uitvoer uit `logs/latest.log`).
- **GitHub-releases:** max 2 GiB per bestand → delen met manifest en sha256, eerst een concept-release.
- **Startersdorp:** `/place jigsaw` bouwt alleen straten (geen «expansion hack»); `/place structure` met een eigen
  datapack (dorpen grootte 4 in plaats van 6) geeft echte huisjes en dorpelingen. Chunks vasthouden met
  plugin-tickets, anders «That position is not loaded». Bomen kappen rond de plek; open biomes voorkeur.
- **Minecraft 26.x:** Java 25; gamerules heten nu snake_case (`keep_inventory`, `advance_time`, `spawn_mobs`,
  `respawn_radius`); `white-list=true` is standaard in een nieuwe server.properties; werelden hebben dimensies in
  `world/dimensions`.
- **LuckPerms:** zonder rechten mag een gewone speler geen Essentials-commando's (/home, /tpa): rechten staan in
  `server.json` → `rechten` en worden live gezet.
- **RCON in 26.x:** het «leeg pakket erachteraan»-trucje sluit soms de verbinding; lees één antwoord (lange antwoorden
  in meerdere pakketten). Paper met `-Duser.language=en -Duser.country=US` zodat uitvoer te parsen is.
- **Plugins voor 26.3:** EssentialsX (officieel tot 26.1.2) werkt; CoreProtect 24.1 weigert («not supported»): op de
  wachtlijst. De staging-/CI-test vangt zoiets af.
- **Achter een tunnel** komt iedereen van 127.0.0.1: Velocity `login-ratelimit = 0`, bans op naam.

## Open punten (stand 16-11)

- CoreProtect staat op de wachtlijst tot er een 26.3-versie is (`mc update check` meldt het).
- Geen staging meer in paneel-modus: wijzigingen worden vooraf getest in de CI of op kitpvp.
- Transfer-packet (spelers bij een proxy-herstart verhuizen) heeft een tweede proxy nodig; bewust niet gebouwd.
- `BOT.md` noemt de namen van admins in een publieke repo; als Jelle dat liever niet heeft, die regel weghalen.
- Losse eindjes van het Zelda-spel staan per eiland in `plugins/zelda/ZELDA.md`.
