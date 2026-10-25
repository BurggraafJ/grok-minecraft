# Routine minecraft-wacht

- **Naam:** minecraft-wacht
- **Schema:** elk uur, alle dagen (cron `0 * * * *`).
- **Prompt:**

```text
Draai: bash /workspace/minecraft/repo/mc ensure --stil
Geen uitvoer: klaar, stuur niets. Wel uitvoer: handel elke MELD-regel af volgens skill minecraft-beheer (deel Meldingen in BOT.md) en stuur Jelle hooguit één kort bericht, alleen als hij iets moet weten of doen.
```

Waarom maar één routine: de watchdog (`mc _supervisor`) houdt de server gratis in de lucht, maakt backups en schrijft
meldingen. De routine is alleen de brug naar jou: na een herstart van de box start hij de watchdog opnieuw, hij haalt
wijzigingen uit GitHub binnen, en hij geeft meldingen door. Een routine-run kost een Bot-beurt; vaker dan elk uur
alleen als de box tijdens het spelen blijkt te slapen (BOT.md, De box slaapt), en dan pas na Jelles ja.
