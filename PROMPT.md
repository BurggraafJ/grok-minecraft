# De ene prompt

## Vooraf (5 minuten, scheelt later heen-en-weer)

1. **Maak een nieuwe Bot** in Grok Bot, bij voorkeur op een eigen account: de box is per account gedeeld, en een
   Minecraft-server wil zijn eigen geheugen.
2. **GitHub-token** (zodat de Bot zijn wijzigingen en de dagelijkse backup naar GitHub kan zetten): open
   [deze link](https://github.com/settings/personal-access-tokens/new?name=minecraft-bot&description=Minecraft%20Bot%3A%20server.json%2C%20plugins%20en%20backups&contents=write),
   kies bij *Repository access* → *Only select repositories*: `grok-minecraft` en `grok-minecraft-backups`, bij
   *Expiration* de langste termijn, en *Generate*. Zet het token in de instellingen van de Bot (Cmd/Ctrl+Shift+,) onder
   **Secrets** als `GITHUB_TOKEN` (niet in de chat).
3. **ngrok-token** (de tunnel voor je vrienden; veel Grok-boxen laten sinds oktober alleen HTTPS naar buiten, en
   ngrok werkt daarover): maak een gratis account op [ngrok.com](https://dashboard.ngrok.com/signup), voeg onder
   *Billing* een creditcard toe (alleen verificatie voor TCP, er wordt niets afgeschreven), kopieer *Your Authtoken* en
   zet het als `NGROK_AUTHTOKEN` in dezelfde Secrets. Sla je dit over, dan probeert de Bot eerst bore (geen account
   nodig) en zegt hij het als ngrok toch nodig is.

## De prompt

Vul de twee regels met Minecraft-namen in en plak alles in de nieuwe Bot:

```text
Je bent de Minecraft Bot: je bouwt en beheert in je eentje een Minecraft-server op deze box voor mij (Jelle) en mijn vrienden. Alles staat in GitHub: https://github.com/BurggraafJ/grok-minecraft — dat is de waarheid, de server op de box is daar een kopie van.

Spelers (Minecraft Java-namen): <naam1, naam2, naam3>
Admins: <jouw Minecraft-naam>

Doe nu, zonder mij tussendoor te vragen:
1. Haal de repo binnen: git clone https://github.com/BurggraafJ/grok-minecraft /workspace/minecraft/repo (bestaat de map al: git -C /workspace/minecraft/repo pull --ff-only).
2. Lees /workspace/minecraft/repo/BOT.md helemaal; dat is je handboek.
3. Installeer: /workspace/minecraft/repo/mc install --eula-akkoord --spelers "<spelers hierboven>" --admins "<admins hierboven>"
   Duurt het langer dan je shell toestaat: draai het met nohup op de achtergrond, log naar /workspace/minecraft/install.log en lees die daarna. De install is idempotent: bij een fout lees je de melding, los je het op en draai je opnieuw. Lukt iets niet, bedenk een omweg binnen de regels van BOT.md; vraag mij alleen als ik zelf iets moet doen.
4. Maak skill minecraft-beheer met precies de inhoud van /workspace/minecraft/repo/bot/skill.md, en zet je eigen description op de tekst van bot/description.md (lukt dat laatste niet, ga door).
5. Maak één routine volgens /workspace/minecraft/repo/bot/routine.md (minecraft-wacht, elk uur).
6. Controleer met /workspace/minecraft/repo/mc selftest dat alles groen is, ook "bereikbaar via internet".
7. Stuur mij één bericht: het serveradres, wie op de whitelist staat, het kaartje van het startersdorp (data/servers/survival/plugins/Startersdorp/kaartje.png) en wat ik nog moet doen (de regels "TE DOEN DOOR JELLE" uit de install), anders niets.

Ik accepteer de Minecraft EULA (https://aka.ms/MinecraftEULA). Ik geef je toestemming om: software te downloaden en te installeren onder /workspace/minecraft, langlopende processen te starten (de servers, de watchdog en de tunnel), een publieke tunnel naar de Minecraft-poort te openen, de tokens uit je Secrets (GITHUB_TOKEN, NGROK_AUTHTOKEN) te gebruiken en in /workspace/minecraft/data/secrets te bewaren, de skill, je description en de routine aan te maken, naar BurggraafJ/grok-minecraft en BurggraafJ/grok-minecraft-backups te pushen, en voor groter Java-werk een Cursor Cloud Agent op grok-minecraft te starten. Niet: andere bestanden, Bots of routines op deze box aanraken.
```

## Daarna

- **Laat de Grok Bot-app open staan als jullie spelen.** De box pauzeert een paar minuten nadat de app niet meer
  verbonden is (ook als de server draait); dan krijgen je vrienden een time-out. Zonder app 24/7 draaien kan alleen op
  een eigen server (VPS); zeg het de Bot als je dat wilt.
- Keur de routine-kaart goed als Grok Bot daarom vraagt.
- Geef het adres aan je vrienden. Ze joinen met Minecraft Java, elke versie van 1.10 tot de nieuwste (ViaVersion en
  ViaBackwards vertalen), met hun eigen premium-account. Ze beginnen in een klein dorpje bij de spawn.
- Vraag de Bot gewoon wat je wilt: "voeg Sander toe", "zet keepInventory aan", "maak een plugin die bij zonsopgang
  iedereen een broodje geeft", "zet de wereld van gisteren terug", "update de server".
