# Ontwerp en waarom

Startpunt was een bouwopdracht die Grok zelf schreef (Velocity + Paper, Bot beheert alles, blue/green). De richting
klopte; dit zijn de punten waar de echte box en de echte versies anders bleken (gemeten en getest op 25 oktober 2026).

| Plan | Werkelijkheid | Gekozen |
|---|---|---|
| Publiek IP van de VM, firewall met ufw | De box is een Firecracker-microVM achter het netwerk van Cursor: uitgaand verkeer via gedeelde IP's, geen inkomende poorten. ufw opent niets. Sinds ~7 oktober 2026 laten veel boxen bovendien alleen HTTPS (443) naar buiten ([forum](https://forum.cursor.com/t/grok-bot-computer-all-outbound-non-https-traffic-blocked-since-oct-1-mysql-ssh-imap-smtp-ftp-time-out/173504)). | Een **tunnel**, automatisch gekozen: bore.pub (geen account) als de box dat toelaat, anders **ngrok** (via 443; gratis account met kaartverificatie). Velocity luistert alleen op 127.0.0.1. |
| Java 21+ | Minecraft 26.x en Velocity 4 eisen **Java 25** | Temurin 25 in `/workspace/minecraft/tools` (de Fill-API zegt welke Java nodig is) |
| Paper 26.3 | Klopt (build 223 stabiel) | Versies vast in `server.json`; `mc update` zoekt nieuwere |
| Blue/green met twee backends | Twee servers kunnen niet tegelijk dezelfde wereld openen (session.lock); een kopie loopt meteen uit de pas. | **Lobby als wachtkamer** (spelers blijven verbonden, ~10–30 s) plus **staging** (kopie van de wereld) om elke wijziging vooraf te testen. |
| systemd of tmux | Niet zeker op de box, en "Computer Update" wist geïnstalleerde pakketten | Eigen **watchdog** in Python, alles onder `/workspace` (blijft staan), plus één routine die hem na een herstart weer start |
| RCON of een MCP-brug | RCON op localhost is genoeg; Velocity heeft geen RCON | RCON voor Paper, de console van Velocity via een FIFO |
| Alleen premium | Klopt | online-mode op Velocity, modern forwarding met geheim, backends offline op 127.0.0.1 |
| — (ontbrak) | Een publiek tunneladres wordt gescand door bots | Eerst whitelist aan; Jelle koos 25-10 voor **geen whitelist**. Dan: alleen premium-accounts, OP alleen voor admins, de watchdog meldt onbekende spelers, backups elk uur dat er gespeeld wordt |
| — (ontbrak) | Via een tunnel komt iedereen van 127.0.0.1: Velocity's login-limiet per IP blokkeert dan vrienden die tegelijk joinen | `login-ratelimit = 0`, bans op naam |
| — (ontbrak) | Grok Bot herschrijft skills stil; elke beurt kost usage | Bron in Git, skill is een dunne verwijzing; wachten en bewaken in een script, niet in Bot-beurten |
| — (ontbrak) | De box pauzeert een paar minuten nadat de app niet meer verbonden is; processen tellen niet als activiteit (staff, [forum](https://forum.cursor.com/t/grok-bot-computer-freezes-reboots-repeatedly-and-kills-24-7-background-scripts/174272)) | Spelen = de Grok Bot-app open laten. De watchdog meet pauzes; voor 24/7 zonder app is een eigen VPS de route (zelfde repo en `mc`) |
| — (ontbrak) | Pushen via SSH (deploy key) kan niet als alleen 443 open is | GitHub via HTTPS met één fine-grained token (alleen deze twee repo's) |
| — (ontbrak) | Een backup alleen op de box is weg als de box weg is | Dagelijks de nieuwste backup als release in een privé-repo, 7 dagen |
| "Spawn" | Een willekeurige wereld, maar een leuk begin | Plugin `startersdorp`: Minecrafts eigen dorpsstukken, verkleind via een datapack (grootte 4), op een vlakke open plek; spawn bij de dorpsbel |

## GitHub als bron

Jelle wil dat alles in GitHub beheerd wordt, ook wat de Bot verandert. Daarom is `server/server.json` de gewenste
stand en volgt de box die (`mc apply`), en niet andersom. Gevolgen:

- Elke wijziging is een commit, dus terug kan altijd (`git revert` + `mc apply`; de wereld zelf via backups).
- CI start bij elke push een echte server met precies de plugins uit `server.json`: een plugin die niet start, valt
  al op GitHub af, en op de box nog een keer op staging.
- De whitelist (namen van vrienden), werelden en geheimen staan níet in Git.
- Pushen van de box gaat via HTTPS met een fine-grained token voor alleen deze repo en de backup-repo; ophalen kan zonder
  token (publieke repo). De wereldbackups staan in de privé-repo `grok-minecraft-backups`, als releases.

## Getest

Lokaal (macOS, Java 25) en in CI (Linux): install, alle plugins aan, startersdorp op een verse wereld, testlogin door Velocity tot in de backend, backups naar GitHub heen en terug (zelfde hash),
herstart via de lobby, crash → watchdog herstart → melding, backup en restore (met een markeerblok), staging-test
die een incompatibele plugin (CoreProtect 24.1) tegenhoudt en `server.json` terugzet, plugin toevoegen en weer weghalen.
