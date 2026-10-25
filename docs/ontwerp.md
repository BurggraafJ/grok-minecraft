# Ontwerp en waarom

Startpunt was een bouwopdracht die Grok zelf schreef (Velocity + Paper, Bot beheert alles, blue/green). De richting
klopte; dit zijn de punten waar de echte box en de echte versies anders bleken (gemeten en getest op 25 oktober 2026).

| Plan | Werkelijkheid | Gekozen |
|---|---|---|
| Publiek IP van de VM, firewall met ufw | De box is een Firecracker-microVM achter het netwerk van Cursor: uitgaand verkeer via gedeelde IP's, geen inkomende poorten. ufw opent niets. | Een **tunnel**: bore.pub (direct, geen account) of playit.gg (vast adres, ook UDP, één klik van Jelle). Velocity luistert alleen op 127.0.0.1. |
| Java 21+ | Minecraft 26.x en Velocity 4 eisen **Java 25** | Temurin 25 in `/workspace/minecraft/tools` (de Fill-API zegt welke Java nodig is) |
| Paper 26.3 | Klopt (build 223 stabiel) | Versies vast in `server.json`; `mc update` zoekt nieuwere |
| Blue/green met twee backends | Twee servers kunnen niet tegelijk dezelfde wereld openen (session.lock); een kopie loopt meteen uit de pas. | **Lobby als wachtkamer** (spelers blijven verbonden, ~10–30 s) plus **staging** (kopie van de wereld) om elke wijziging vooraf te testen. |
| systemd of tmux | Niet zeker op de box, en "Computer Update" wist geïnstalleerde pakketten | Eigen **watchdog** in Python, alles onder `/workspace` (blijft staan), plus één routine die hem na een herstart weer start |
| RCON of een MCP-brug | RCON op localhost is genoeg; Velocity heeft geen RCON | RCON voor Paper, de console van Velocity via een FIFO |
| Alleen premium | Klopt | online-mode op Velocity, modern forwarding met geheim, backends offline op 127.0.0.1 |
| — (ontbrak) | Een publiek tunneladres wordt gescand door bots | **Whitelist** altijd aan; OP alleen voor admins die Jelle noemt |
| — (ontbrak) | Via een tunnel komt iedereen van 127.0.0.1: Velocity's login-limiet per IP blokkeert dan vrienden die tegelijk joinen | `login-ratelimit = 0`, bans op naam |
| — (ontbrak) | Grok Bot herschrijft skills stil; elke beurt kost usage | Bron in Git, skill is een dunne verwijzing; wachten en bewaken in een script, niet in Bot-beurten |
| — (ontbrak) | Idle boxen kunnen slapen | De watchdog meet het (hartslag); de Bot meldt het en stelt dan een extra routine voor speeltijden voor |

## GitHub als bron

Jelle wil dat alles in GitHub beheerd wordt, ook wat de Bot verandert. Daarom is `server/server.json` de gewenste
stand en volgt de box die (`mc apply`), en niet andersom. Gevolgen:

- Elke wijziging is een commit, dus terug kan altijd (`git revert` + `mc apply`; de wereld zelf via backups).
- CI start bij elke push een echte server met precies de plugins uit `server.json`: een plugin die niet start, valt
  al op GitHub af, en op de box nog een keer op staging.
- De whitelist (namen van vrienden), werelden en geheimen staan níet in Git.
- Pushen van de box gaat met een deploy key die alleen voor deze repo geldt; ophalen kan zonder sleutel (publieke repo).

## Getest

Lokaal (macOS, Java 25) en in CI (Linux): install, alle plugins aan, testlogin door Velocity tot in de backend,
herstart via de lobby, crash → watchdog herstart → melding, backup en restore (met een markeerblok), staging-test
die een incompatibele plugin (CoreProtect 24.1) tegenhoudt en `server.json` terugzet, plugin toevoegen en weer weghalen.
