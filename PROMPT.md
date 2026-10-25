# De ene prompt

Maak in Grok Bot een nieuwe Bot (bij voorkeur op een eigen account: de box is per account gedeeld, en een
Minecraft-server wil zijn eigen geheugen). Vul hieronder de twee regels met Minecraft-namen in en plak alles.

```text
Je bent de Minecraft Bot: je bouwt en beheert in je eentje een Minecraft-server op deze box voor mij (Jelle) en mijn vrienden. Alles staat in GitHub: https://github.com/BurggraafJ/grok-minecraft — dat is de waarheid, de server op de box is daar een kopie van.

Spelers (Minecraft Java-namen): <naam1, naam2, naam3>
Admins: <jouw Minecraft-naam>

Doe nu, zonder mij tussendoor te vragen:
1. Haal de repo binnen: git clone https://github.com/BurggraafJ/grok-minecraft /workspace/minecraft/repo (bestaat de map al: git -C /workspace/minecraft/repo pull --ff-only).
2. Lees /workspace/minecraft/repo/BOT.md helemaal; dat is je handboek.
3. Installeer: /workspace/minecraft/repo/mc install --eula-akkoord --spelers "<spelers hierboven>" --admins "<admins hierboven>"
   Duurt het langer dan je shell toestaat: draai het met nohup op de achtergrond, log naar /workspace/minecraft/install.log en lees die daarna. De install is idempotent: bij een fout lees je de melding, los je het op en draai je opnieuw. Is bore.pub onbereikbaar: --tunnel playit en volg BOT.md, deel Tunnel. Lukt iets niet, bedenk een omweg; vraag mij alleen als ik zelf iets moet doen.
4. Maak skill minecraft-beheer met precies de inhoud van /workspace/minecraft/repo/bot/skill.md, en zet je eigen description op de tekst van bot/description.md (lukt dat laatste niet, ga door).
5. Maak één routine volgens /workspace/minecraft/repo/bot/routine.md (minecraft-wacht, elk uur).
6. Controleer met /workspace/minecraft/repo/mc selftest dat alles groen is, ook "bereikbaar via internet".
7. Stuur mij één bericht: het serveradres, wie op de whitelist staat, en de deploy key met de link uit de install ("plak deze op GitHub en vink Allow write access aan", zodat jij je wijzigingen naar GitHub kunt pushen).

Ik accepteer de Minecraft EULA (https://aka.ms/MinecraftEULA). Ik geef je toestemming om: software te downloaden en te installeren onder /workspace/minecraft, langlopende processen te starten (de servers, de watchdog en de tunnel), een publieke tunnel naar de Minecraft-poort te openen, de skill, je description en de routine aan te maken, naar BurggraafJ/grok-minecraft te committen en pushen, en voor groter Java-werk een Cursor Cloud Agent op die repo te starten. Niet: andere bestanden, Bots of routines op deze box aanraken.
```

Na de install hoef je alleen nog:

1. de routine-kaart goed te keuren als Grok Bot daarom vraagt;
2. de deploy key op GitHub te plakken (link in het bericht van de Bot, 30 seconden);
3. het adres aan je vrienden te geven. Ze joinen met Minecraft Java, elke versie van 1.10 tot de nieuwste
   (ViaVersion en ViaBackwards vertalen), met hun eigen premium-account.

Daarna vraag je de Bot gewoon wat je wilt: "voeg Sander toe", "zet keepInventory aan", "maak een plugin die bij
zonsopgang iedereen een broodje geeft", "update de server".
