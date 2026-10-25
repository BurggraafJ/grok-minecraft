# Plugins

Getest op Paper 26.3 (build 223) op 25 oktober 2026: alles in de standaardset start zonder fouten.

## Standaard (staat in `server/server.json`)

| Plugin | Op | Waarvoor |
|---|---|---|
| **ViaVersion** + **ViaBackwards** | survival, lobby | Joinen met een andere Minecraft-versie: oudere clients (tot 1.10) en nieuwere dan de server. Velocity laat alles van 1.7 tot 26.3 binnen. |
| **EssentialsX** | survival | De snelle commando's: `/home`, `/sethome`, `/tpa`, `/back`, `/warp`, `/msg`, `/kit`, `/afk`, `/nick`, `/heal` (admin) en meer. Niet officieel voor 26.3, maar start en werkt; de staging-test bewaakt het. |
| **EssentialsX Spawn** | survival | `/spawn` en `/setspawn`, nieuwe spelers op de spawn. |
| **LuckPerms** | survival | Rechten per groep (wie mag `/fly`, `/gamemode`, warps zetten). Standaard mogen alle spelers de normale Essentials-commando's. |
| **WorldEdit** | survival | Bouwen met selecties (`//set`, `//copy`, `//paste`) voor admins. |
| **WorldGuard** | survival | De spawn en andere plekken beschermen tegen slopen en PvP. |
| **Chunky** | survival | De wereld vooraf genereren, zodat er geen lag is als iemand nieuw terrein ontdekt. |
| **lobby-melding** (eigen) | lobby | Wie in de wachtkamer belandt, ziet dat de server herstart en dat hij vanzelf teruggaat. Ook het sjabloon voor eigen plugins. |
| spark | ingebouwd in Paper | Lag onderzoeken (`/spark profiler`, `/spark tps`). |

**Op de wachtlijst:** **CoreProtect** (wie brak welk blok, alles terugdraaien bij griefing). Versie 24.1 weigert 26.3
("not supported"). `mc update check` meldt het zodra er een versie voor 26.3 is; dan zet de Bot hem erbij.

## Er nog bij kunnen (zeg het de Bot)

| Plugin | Wat het geeft | Let op |
|---|---|---|
| **GravesX** | Je spullen in een graf als je doodgaat, in plaats van op de grond | Getest: werkt op 26.3 |
| **GriefPrevention** | Spelers claimen hun eigen land met een gouden schep | Vooral nuttig met vreemden; onder vrienden vaak niet nodig |
| **Simple Voice Chat** | Proximity voice: je hoort wie dichtbij is | Moet UDP: werkt via playit, niet via bore |
| **BlueMap** | 3D-kaart van de wereld in je browser | Heeft een tweede (http-)tunnel nodig |
| **DiscordSRV** | Chat tussen de game en een Discord-kanaal | Discord-bot-token nodig (komt in de geheimen, niet in Git) |
| **Geyser + Floodgate** | Vrienden op Bedrock (console, telefoon) | Moet UDP (playit); Floodgate laat Xbox-accounts binnen |
| **TAB** | Mooie tablijst en naamlabels | Cosmetisch |
| **DecentHolograms** | Zwevende teksten (regels bij de spawn) | Cosmetisch |
| **Vault** | Brug voor prefixes en economie tussen plugins | Pas nodig met een chat- of economieplugin |
| **ViaRewind** | Ook 1.7–1.9-clients | Alleen als iemand op een heel oude versie speelt |

Een plugin van buiten komt er alleen in via `mc plugin add` (Modrinth, vaste versie, hashcontrole, eerst getest op een
kopie van de wereld). Eigen plugins in Java schrijft de Bot zelf of laat hij door Cursor maken.
