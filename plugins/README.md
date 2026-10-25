# Eigen plugins

Elke map hier is een Paper-plugin (Java, Gradle). De Bot bouwt ze op de box en zet ze live via `mc apply`; CI bouwt
ze bij elke push en start ze in een echte server.

## Opbouw (zie `lobby-melding/`)

```
plugins/<naam>/
  settings.gradle                rootProject.name = '<naam>'
  build.gradle                   java-plugin, paper-api compileOnly, release 25, plugin.yml-expansie
  src/main/resources/plugin.yml  name, version '${version}', main, api-version '${apiVersion}'
  src/main/resources/config.yml  (optioneel) instelbare teksten
  src/main/java/nl/burggraaf/mc/<naam zonder streepjes>/<Naam>.java
```

- `build.gradle` krijgt drie eigenschappen mee van `mc`: `paperApiVersion` (bv. `26.3.build.223-stable`),
  `mcVersion` (wordt `api-version` in plugin.yml) en `javaRelease` (25). Hardcodeer ze niet.
- Gebruik de Paper-API (Adventure-componenten voor tekst, `Title`, `Component.text`). Geen NMS, geen
  reflection op servercode: dat breekt bij elke Minecraft-update.
- Commando's: in `plugin.yml` onder `commands:` met `permission:`; registreer met `getCommand("x").setExecutor(...)`.
- Opslag: `getDataFolder()` (YAML/JSON). Geen eigen database-server.
- Teksten voor spelers in het Nederlands, liefst in `config.yml` zodat de Bot ze kan aanpassen via `patches`.

## Nieuwe plugin

Op de box: `mc plugin nieuw <naam> --op survival` (kopieert het sjabloon, zet hem in `server.json`). Daarna code
schrijven, `mc plugin build <naam>` tot het compileert, `mc apply` (staging-test en live), `mc vastleggen "…"`.

Als coding agent: dezelfde opbouw met de hand, de regel in `server/server.json` erbij, PR openen; CI test hem.
