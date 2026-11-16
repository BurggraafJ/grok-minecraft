# Stand en architectuur

Stand 16-11-2026 (laatste commit van de Bot 30-10). Controleer live: `BOT.md` → «Paneel-modus: zo draait het nu».

## Servers bij Sparked Host (paneel-modus, sinds 26-10-2026)

| Dienst | Waar | RAM | Bereikbaar |
|---|---|---|---|
| **velocity** (proxy, server «Burggraafs House») | hoofdserver van pakket Neptune | 1792 MB (gebruikt ±150) | publiek `45.43.163.23:25576`: **het adres voor spelers** |
| **lobby** | split van Neptune | 1024 MB | alleen intern, `172.18.0.1:<poort>` (127.0.0.1-allocatie) |
| **survival** | split van Neptune | 5376 MB | alleen intern, `172.18.0.1:<poort>` |
| **kitpvp** (+ wereld `zelda`) | losse server van 2 GB (Jelle kocht erbij), andere node | 2048 MB | publiek ip:poort; weigert directe joins («requires Velocity») |

- **Pakket:** Sparked Host Neptune (8 GB, Parijs, ±$20,72/mnd) plus een losse 2 GB-server voor kitpvp. Neptune geeft
  **maar 2 splits**; een los split-slot kun je niet bijkopen (pas Milky Way, 16 GB, heeft er 4). Splitten, RAM verdelen
  en een server hernoemen kan alleen in het paneel, niet via de API. Een nieuwe server bestelt Jelle zelf.
- **Paneel:** https://control.sparkedhost.us (Apollo = aangepaste Pterodactyl). Client-API `…/api/client`, sleutel
  `SPARKED_API_KEY` (Secrets van de Bot, op de box `data/secrets/sparked_api_key`). Hij geldt voor álle servers van
  Jelles paneelaccount; facturatie kan de Bot niet in.
- **Server-ids en interne poorten** staan in `BOT.md` en in `data/config.json` op de box (`paneel.servers`), niet hier.

## Netwerkgrenzen (de bron van bijna elk raar probleem)

**De Grok-box** (waar de Bot en `mc` draaien):
- Uitgaand **alleen HTTPS/443** (veel Grok-boxen sinds ±7-10-2026; hostnamen gaan via een HTTPS-proxy). SSH, RCON,
  bore (7835), plain HTTP (CRL-controle van ngrok) en poort 8080 lopen vast. Niet omheen werken met IP- of DNS-trucs.
- Gevolgen voor het paneel: de node-poort 8080 (signed upload-URL, console-websocket, downloads) is dicht. Daarom
  uploadt `mc` met `POST /files/write` (< 100 MB per keer, Cloudflare), grote mappen in tar.gz-delen van < 80 MB die op
  het paneel worden uitgepakt; commando's met `POST /command`, uitvoer teruglezen uit `logs/latest.log`; bestanden
  > ~100 MB lezen geeft 403.
- **Geen inkomende poorten**, en de box **pauzeert** een paar minuten nadat de Grok-app niet meer verbonden is
  (processen tellen niet als activiteit). Daarom draaien de servers niet meer op de box.

**De splits (lobby, survival) hebben geen internet (geen DNS):**
- Paperclip kan bij een nieuwe Paper-build de Mojang-jar en libraries niet halen: `mc paneel deploy` maakt ze op de
  box (`-Dpaperclip.patchonly=true`) en zet `cache/`, `libraries/`, `versions/` erop.
- Plugins die zelf libraries downloaden (LuckPerms `libs/`, GravesX `lib/`) moeten die map al hebben. Een nieuwe
  plugin die bij de eerste start iets downloadt: eerst lokaal of op kitpvp laten draaien, dan de map meesturen.
- `op <naam>` op de console werkt niet (geen naamopzoeking): `mc apply` schrijft `ops.json` en herstart.
- `enforce-secure-profile=false` op de backends (Velocity controleert al met `force-key-authentication`).

## Veiligheid

- Velocity: `online-mode = true`, modern forwarding met het geheim uit `data/secrets/secrets.json`. Backends:
  `online-mode=false` + `proxies.velocity` in `config/paper-global.yml`. Directe join op kitpvp → geweigerd (getest).
- RCON uit. `broadcast-console-to-ops` en `broadcast-rcon-to-ops` uit (anders zien OP's elk mc-commando).
- Whitelist uit (Jelles keuze). Admins: `data/spelers.json` op de box (`admin: true`); `mc apply` maakt daaruit
  `ops.json` op elke backend, de LuckPerms-groep `admin` op survival en `beheerders.json` op de proxy.

## `mc` in het kort

| Module | Wat |
|---|---|
| `cli.py` | alle opdrachten (`mc help`) |
| `paneel.py` | paneel-modus: status, cmd, logs, power, deploy (alleen wat anders is, hash-administratie in `data/state/paneel.json`), push/spiegel, vars, backups, schema's, rechten |
| `apply.py`, `spec.py` | `server.json` lezen en valideren, plugins downloaden (Modrinth, sha512) en bouwen (Gradle), plannen, uitrollen |
| `offsite.py` | backups als GitHub-release, in delen > 1,9 GiB, met manifest en sha256 per deel |
| `gitops.py` | git via HTTPS met token, pull/rebase, commit, push |
| `setup.py` | `install` (box-modus) en `ensure` (de routine) |
| `core.py`, `ops.py`, `supervisor.py`, `net.py` | box-modus: diensten, herstart via de lobby, backups, watchdog, RCON, serverping |
| `downloads.py` | Java (Temurin), Paper en Velocity (Fill-API), Gradle, bore/ngrok |

Belangrijkste opdrachten in paneel-modus: `mc status` · `mc cmd <dienst> <commando>` · `mc logs <dienst> -n 100` ·
`mc apply` (= `mc paneel deploy`) · `mc vastleggen "<wat>"` · `mc paneel start|stop|restart <dienst|all>` ·
`mc paneel push|spiegel <dienst>` · `mc paneel vars <dienst> K=V` · `mc backup [--github]` · `mc backups [--github]` ·
`mc restore github[:JJJJMMDD]` · `mc paneel rechten` · `mc ensure [--stil]`.

## Backups

- **Op het paneel:** elke nacht via paneel-schema's (survival 03:00, lobby 03:20, kitpvp 03:40 UTC, velocity zondag),
  max 3 per server. Terugzetten: paneel → Backups → Restore (eerst een nieuwe backup).
- **Naar GitHub:** `mc ensure` doet 1x per dag (04–08 uur box-tijd) `mc backup --github`: survival op het paneel inpakken
  (opslaan ±4 min uit, daarna altijd `save-on`), naar de box halen, als release `dag-JJJJMMDD` in
  `grok-minecraft-backups` (laatste 7 dagen). Groter dan 1,9 GiB → delen + `manifest.json`; eerst een concept-release,
  pas na een volledige upload gaat de oude van die dag weg. Stand 16-11: ±1,3 GB per dag, dagelijks aanwezig.
- `mc restore github` haalt en controleert (sha256 per deel en geheel); in paneel-modus zet hij niets over de draaiende
  wereld terug: dat gaat bewust met de hand (paneel of push).

## Box-modus (de oude manier, zit nog in de code)

Alles op de Grok-box: Velocity + lobby + survival + staging als processen, een Python-watchdog, tunnel via bore of ngrok,
herstart via de lobby als wachtkamer, staging-test op een kopie van de wereld vóór elke wijziging. Terug naar box-modus
= `"modus"` weghalen uit `data/config.json` en `mc up`. Niet aan te raden zolang de box alleen HTTPS heeft en pauzeert.
