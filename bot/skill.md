---
name: minecraft-beheer
description: Gebruik dit bij alles rond de Minecraft-server van Jelle: status en adres, spelers en whitelist, plugins zoeken en installeren, eigen Java-plugins bouwen, updates, backups en terugzetten, herstarten, meldingen van de routine minecraft-wacht, en spelers die niet kunnen joinen.
---
# Minecraft-server beheren

Lees eerst `/workspace/minecraft/repo/BOT.md` (het handboek, uit GitHub) en volg het. De servers draaien nu bij Sparked
Host (paneel-modus, adres `45.43.163.23:25576`); `mc` stuurt ze via de paneel-API aan (BOT.md, Paneel-modus). Ontbreekt de map:
`git clone https://github.com/BurggraafJ/grok-minecraft /workspace/minecraft/repo` en daarna
`/workspace/minecraft/repo/mc ensure`.

De kern:

- Gereedschap: `/workspace/minecraft/repo/mc` (`mc help`). Status: `mc status`. Alles weer goed: `mc ensure`.
- GitHub is de waarheid: wijzig `server/server.json` (of gebruik `mc plugin add/remove`, `mc update apply`), dan
  `mc apply` (test op staging, zet live via de lobby) en `mc vastleggen "<wat>"`.
- Eigen plugins: klein zelf (`mc plugin nieuw <naam>`), groot via een Cursor Cloud Agent op de repo, daarna
  `mc git merge <branch>`.
- Harde regels: online-mode aan, whitelist zoals server.json zegt (nu uit; meld onbekende spelers); OP alleen voor admins die Jelle noemt; geen geheimen in chat of Git;
  plugins alleen via `mc plugin add` of eigen code; een nieuwe Minecraft-versie alleen op Jelles woord.
- De routine: `mc ensure --stil`; geen uitvoer = niets doen en niets sturen.

Bron: `bot/skill.md` in de repo. Wijkt deze skill daarvan af, zet hem terug.
