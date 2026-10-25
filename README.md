# grok-minecraft

Een Minecraft-server die één Grok Bot helemaal zelf bouwt en beheert. Jelle plakt één prompt ([PROMPT.md](PROMPT.md)),
de Bot installeert alles op zijn box, en daarna vraag je hem gewoon wat je wilt: spelers erbij, een plugin, een
update, een eigen plugin in Java.

## Hoe het werkt

```
vrienden ─► tunnel (bore.pub, of ngrok als de box alleen HTTPS naar buiten laat)
              └─► Velocity  (premium-accounts, clients 1.10 → nieuwste via ViaVersion/ViaBackwards)
                    ├─► survival  de wereld (Paper)
                    ├─► lobby     wachtkamer: bij een herstart wachten spelers hier en gaan vanzelf terug
                    └─► staging   kopie van de wereld waarop elke wijziging eerst getest wordt
```

- **GitHub is de waarheid.** [`server/server.json`](server/server.json) beschrijft de hele server: Minecraft- en
  Paper-versie, Velocity, elke plugin met vaste versie en hash, instellingen en plugin-patches. Elke commit is een
  serverversie. De Bot wijzigt alleen via deze repo; `mc apply` zet de box gelijk (eerst staging, dan live).
- **`mc`** is het gereedschap van de Bot (Python, alleen standaardbibliotheek): installeren, `apply`, herstarten
  zonder dat spelers eruit vliegen, backups, plugins, updates, zelftest. `./mc help`.
- **De watchdog** houdt alles in de lucht, maakt backups en schrijft meldingen, zonder Bot-beurten. Eén routine per
  uur (`mc ensure --stil`) is de brug naar de Bot.
- **Backups** elk uur dat er gespeeld is op de box, en dagelijks als release in de privé-repo `grok-minecraft-backups`.
- **Startersdorp**: een willekeurige wereld met een klein Minecraft-dorp bij de spawn (eigen plugin + datapack).
- **Eigen plugins** staan in [`plugins/`](plugins/). Klein schrijft de Bot zelf; groter gaat naar een Cursor Cloud
  Agent die een PR opent. CI bouwt elke plugin en start een echte server met alle plugins.

| Bestand | Voor wie |
|---|---|
| [PROMPT.md](PROMPT.md) | Jelle: de ene prompt voor een nieuwe Bot |
| [BOT.md](BOT.md) | de Bot: zijn handboek (de skill verwijst ernaar) |
| [AGENTS.md](AGENTS.md), [plugins/README.md](plugins/README.md) | Cursor en andere coding agents |
| [docs/plugins.md](docs/plugins.md) | welke plugins erin zitten en wat je er nog bij kunt zetten |
| [docs/ontwerp.md](docs/ontwerp.md) | waarom het zo gebouwd is |

## Zelf proberen (zonder Bot)

```bash
MC_HOME=/tmp/mc ./mc install --eula-akkoord --offline-test --tunnel geen --spelers Test --admins Test
MC_HOME=/tmp/mc ./mc selftest --login Test
MC_HOME=/tmp/mc ./mc down
```

`--offline-test` is alleen voor tests en CI (geen Microsoft-login, geen tunnel naar buiten).
