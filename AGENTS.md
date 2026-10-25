# Voor coding agents (Cursor, Claude Code)

Deze repo beschrijft een Minecraft-server die een Grok Bot beheert. `server/server.json` is de gewenste stand; de Bot
zet de box daarmee gelijk (`mc apply`). Jij werkt meestal aan een **eigen plugin** in `plugins/<naam>`.

## Regels

- Werk op een branch en open een PR. Klaar = CI groen (`.github/workflows/ci.yml`: unit-tests, de plugins bouwen, en
  een echte Paper + Velocity-server met álle plugins uit `server.json` starten en controleren).
- Raak alleen aan wat de opdracht vraagt: `plugins/<naam>/` en de regel voor die plugin in `server/server.json`
  (datapacks: `server/datapacks/<naam>/` en `datapacks.survival` in `server.json`).
  `mctool/` en de andere plugins alleen als de opdracht daarover gaat.
- Geen plugins van buiten toevoegen. Externe plugins komen via `mc plugin add` op de box (Modrinth, met hash).
- Geen geheimen, IP-adressen, tokens of spelersnamen in de repo.
- Teksten die spelers zien: Nederlands. Code en identifiers mogen Engels.

## Een plugin maken

Lees `plugins/README.md`. Kort: kopieer de opbouw van `plugins/lobby-melding` (Gradle, Java, `paper-api` als
`compileOnly`), zet de plugin in `server/server.json`:

```json
{"naam": "<naam>", "bron": "eigen", "pad": "plugins/<naam>", "op": ["survival"]}
```

Lokaal bouwen kan met `./mc plugin build <naam>` (downloadt zelf Java 25 en Gradle naar `MC_HOME`) of met een eigen
Gradle: `gradle build -PpaperApiVersion=<versie> -PmcVersion=26.3`. De paper-api-versie zoekt `mc` zelf op; voor
26.3 is dat bijvoorbeeld `26.3.build.223-stable`.
