# Werken aan het project

## Wie doet wat

| Wie | Rol |
|---|---|
| **De Minecraft Bot** (Grok, eigen account) | Beheert alles: bouwt, zet live, beantwoordt Jelle, schrijft de meeste plugins. Werkt in `/workspace/minecraft` op zijn box. Commit en pusht naar `main` met een fine-grained token (alleen deze repo en de backup-repo). |
| **Claude Code** (jij, op Jelles Mac) | Architectuur, `mc`, CI, onderzoek, grote reparaties, en berichten voor de Bot. Pusht ook naar `main` (Jelle vertrouwt dat), maar alleen met groene tests. |
| **Cursor Cloud Agent** | Groter Java-werk op verzoek van de Bot: werkt op een branch, opent een PR; de Bot test en doet `mc git merge`. |
| **Jelle** | Wensen, akkoord op onomkeerbare dingen, accounts en betalingen. Plakt berichten; reviewt of merget nooit zelf. |

## Zelf iets wijzigen

1. `gh repo clone BurggraafJ/grok-minecraft` (of `git pull --ff-only`). De Bot pusht vaak: altijd eerst verversen.
2. Wijzig. Houd je aan de stijl van de code: Nederlandse namen en teksten voor spelers, alleen standaardbibliotheek in
   `mctool/`, commentaar zoals eromheen.
3. Test: `python3 -m unittest discover -s tests`. Bij werk aan `mc` of plugins ook lokaal draaien kan met box-modus:
   `MC_HOME=/tmp/mc ./mc install --eula-akkoord --offline-test --tunnel geen --spelers Test --admins Test`
   (offline-test = geen Microsoft-login, geen tunnel; nooit op de echte box).
4. Commit met een korte Nederlandse onderwerpregel, push naar `main`. **Wacht op de CI** (`gh run watch`); rood = repareren.
5. Botst je push (de Bot pushte intussen): `git fetch && git rebase origin/main`, tests opnieuw, push. Kijk eerst wat de
   Bot deed (`git log HEAD..origin/main`): soms repareerde hij zelf iets (bv. de ngrok-CRL-fix op 25-10).
6. Raak liever geen bestanden aan waar de Bot op dat moment aan werkt (zie de laatste commits). Iets in `mctool/` dat
   de Bot net bouwt: geef hem een bericht in plaats van zelf te pushen.
7. De Bot haalt jouw commit binnen het uur zelf binnen (`mc ensure` → pull + apply). Moet het meteen: bericht
   «haal de nieuwste versie uit GitHub: mc git pull».

## De CI

`.github/workflows/ci.yml`: unit-tests, plus een echte Paper + Velocity-server (box-modus, offline-test) met álle
plugins uit `server.json`, testlogin door Velocity, herstart, backup/restore, staging, crash → watchdog, idempotent.
Duurt ±5–9 minuten. Les van 25-10: de CI stond een dag rood zonder dat iemand het zag (verouderde test, daarna geen
geheugen voor kitpvp in de CI-preset). Sindsdien leest `mc ensure` de CI-stand van main mee en meldt rood.

## Een bericht voor de Bot schrijven

Jelle plakt het in de chat van de Minecraft Bot. Regels: alles in één bericht, «zonder tussenvragen», concreet wat klaar
betekent, en «meld me daarna in één zin …». Nooit een geheim erin; tokens gaan via de Secrets van de Bot.

**Sjabloon, iets nieuws bouwen:**
```text
<Wat Jelle wil, in zijn woorden.> Doe het zonder tussenvragen volgens BOT.md: server.json of plugins/<naam> → mc apply → mc vastleggen, en kijk daarna of de CI op GitHub groen wordt (rood = eerst repareren). Groter Java-werk mag via een Cursor Cloud Agent op de repo. Meld me daarna in één zin wat er nu kan en hoe spelers het gebruiken.
```

**Sjabloon, nieuwste versie ophalen:**
```text
Haal de nieuwste versie uit GitHub en zet hem live: /workspace/minecraft/repo/mc git pull. Meld me in één zin dat het gelukt is, met wat er veranderde.
```

**Sjabloon, de stand vragen (voor een snapshot):**
```text
Laat me weten hoe het nu werkt voor onze snapshot: opbouw, servers en adres, werkwijze, wat er sinds de vorige keer veranderde, en wat er open staat. Kort, als markdown.
```

## Een nieuwe Bot (of een verse box)

`PROMPT.md` in de repo is de ene prompt waarmee een schone Grok Bot alles opbouwt (box-modus, dan de skill
`minecraft-beheer` uit `bot/skill.md`, de description uit `bot/description.md` en de routine uit `bot/routine.md`). Voor
de paneel-modus heeft hij daarna nog `SPARKED_API_KEY` en `GITHUB_TOKEN` in zijn Secrets nodig, en
`data/config.json` → `"modus": "paneel"` met de server-ids (zie `BOT.md`). De wereld komt uit de backups.

## Grok Bot-eigenaardigheden die hier spelen

- De skill van de Bot (`minecraft-beheer`) kan stil herschreven worden; de bron is `bot/skill.md`, het handboek `BOT.md`.
- Elke beurt kost usage: wachten en bewaken doet een script (`mc ensure` in één uurroutine), niet de Bot zelf.
- Approvals van onbemand werk verlopen na ±10 minuten; de routine moet zonder goedkeuring kunnen.
- Meer over Grok Bot in het algemeen: de skill `grok-bot` (die is voor Jelles Maestro-fleet, maar het productmodel geldt
  hier ook).
