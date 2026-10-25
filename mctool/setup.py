"""`mc install` (eenmalig, idempotent) en `mc ensure` (elk uur via de routine: alles weer goed zetten)."""
import os
import shutil
import socket
import subprocess
import sys
import time

from . import apply as applymod
from . import core
from . import downloads as dl
from . import gitops
from . import ops
from . import spec as specmod
from . import supervisor
from .base import (GB, HOME, JARS, STATE, Fout, cpus, disk, ensure_dirs, http, human, meminfo, read_json, say, system,
                   write_json)

SHARED_MARKERS = ("/workspace/fleet", "/workspace/maestro-fleet")


def preflight(tunnel):
    """Meet de box. Geeft (regels, fatale fouten, gedeeld)."""
    rows, fatal = [], []
    osn, arch = system()
    rows.append(f"platform: {osn}/{arch}, Python {sys.version.split()[0]}")
    if sys.version_info < (3, 9):
        fatal.append("Python 3.9 of nieuwer nodig")
    total, avail = meminfo()
    rows.append(f"geheugen: {human(total)} totaal, {human(avail)} beschikbaar · {cpus()} cpu")
    if total < 4 * GB:
        fatal.append(f"te weinig geheugen ({human(total)}); minimaal 4 GB")
    free, dtotal = disk()
    rows.append(f"schijf: {human(free)} vrij van {human(dtotal)} op {HOME}")
    if free < 5 * GB:
        fatal.append(f"te weinig schijf ({human(free)} vrij); minimaal 5 GB")
    shared = [m for m in SHARED_MARKERS if os.path.exists(m)]
    if shared:
        rows.append(f"LET OP: de box wordt gedeeld ({', '.join(shared)}): kleiner geheugenplan")
    for tool in ("git", "tar", "ssh-keygen"):
        if not shutil.which(tool):
            (fatal if tool != "ssh-keygen" else rows).append(f"{tool} ontbreekt" + ("" if tool != "ssh-keygen" else " (pushen naar GitHub lukt dan niet)"))
    for url in ("https://fill.papermc.io/v3/projects/paper", "https://api.modrinth.com/v2/tag/loader", "https://api.adoptium.net/v3/info/available_releases"):
        try:
            http(url, timeout=15, tries=2)
        except Fout as e:
            fatal.append(f"geen internet naar {url.split('/')[2]} ({e})")
    if tunnel == "bore":
        try:
            socket.create_connection(("bore.pub", 7835), timeout=8).close()
            rows.append("bore.pub bereikbaar (tunnel kan)")
        except OSError as e:
            rows.append(f"LET OP: bore.pub:7835 niet bereikbaar ({e}); gebruik --tunnel playit")
    return rows, fatal, bool(shared)


EULA = ("Minecraft-servers draaien alleen als de eigenaar de Minecraft EULA accepteert "
        "(https://aka.ms/MinecraftEULA). Draai `mc install --eula-akkoord` als Jelle daarmee akkoord is.")


def install(eula, spelers, admins, tunnel, offline_test, herbereken=False):
    ensure_dirs()
    say("1/7 De box meten")
    rows, fatal, shared = preflight(tunnel)
    for r in rows:
        say("  " + r)
    if fatal:
        raise Fout("installatie kan niet:\n  " + "\n  ".join(fatal))
    cfg = core.load_or_default()
    if eula:
        cfg["eula"] = True
    if not cfg["eula"]:
        raise Fout(EULA)
    if offline_test and tunnel != "geen":
        raise Fout("--offline-test mag alleen met --tunnel geen (dan kan niemand van buiten erbij)")
    cfg["offline_test"] = bool(offline_test)
    cfg["tunnel"]["modus"] = tunnel
    cfg["gedeeld"] = shared
    if herbereken or not cfg.get("geheugen_mb"):
        cfg["geheugen_mb"] = core.memory_plan(meminfo()[0], shared)
    say(f"  geheugenplan (MB): {cfg['geheugen_mb']}")
    core.save(cfg)

    say("2/7 Java, Paper en Velocity downloaden (met hashcontrole)")
    spec = specmod.load()
    dl.ensure_java(spec["java"])
    cfg["java"] = spec["java"]
    paper = dl.fill_build("paper", spec["minecraft"], spec["paper_build"])
    velocity = dl.fill_build("velocity", spec["velocity"]["versie"], spec["velocity"]["build"])
    dl.ensure_jar(paper)
    dl.ensure_jar(velocity)
    if not cfg.get("paper"):
        cfg["paper"] = {k: paper[k] for k in ("version", "build", "name", "sha256", "java", "flags")}
        cfg["mc"] = spec["minecraft"]
    if not cfg.get("velocity"):
        cfg["velocity"] = {k: velocity[k] for k in ("version", "build", "name", "sha256", "java")}
    core.save(cfg)

    say("3/7 Servers inrichten (lobby, survival, Velocity)")
    for svc in ("lobby", "survival"):
        if core.running(svc):
            continue
        core.configure_paper(cfg, svc)
        applymod.apply_settings(cfg, spec, svc)
        if not os.path.exists(os.path.join(core.server_dir(svc), "config", "paper-global.yml")):
            core.first_start(cfg, svc)
    core.configure_velocity(cfg)

    say("4/7 Plugins uit server.json (downloaden, eigen plugins bouwen)")
    for line in applymod.apply(cfg, spec, first=True):
        say("  " + line)

    say("5/7 Alles starten (watchdog)")
    for s in spelers:
        ops.spelers_add(s)
    for a in admins:
        ops.spelers_add(a, admin=True)
    supervisor.spawn()
    for svc in ("lobby", "survival", "velocity") + (("tunnel",) if tunnel in ("bore", "playit") else ()):
        ok = core.wait_ready(cfg, svc, 900 if svc != "tunnel" else 90)
        say(f"  {'✓' if ok else '✗'} {svc}")
        if not ok and svc != "tunnel":
            raise Fout(f"{svc} komt niet op; laatste log:\n" + "\n".join(core._tail_log(svc)))
    for line in ops.spelers_sync(cfg):
        say("  " + line)

    say("6/7 GitHub koppelen")
    pub = None
    if gitops.is_repo():
        gitops.setup()
        try:
            pub = gitops.ensure_key()
        except Fout as e:
            say(f"  {e}")
    say("7/7 Zelftest")
    cfg = core.load()
    ok, lines = ops.selftest(cfg, login="Testspeler" if offline_test else None)
    for line in lines:
        say("  " + line)
    adres = core.tunnel_address(cfg)
    say("")
    say(f"KLAAR: {'alles groen' if ok else 'met fouten (zie ✗ hierboven)'}")
    say(f"Serveradres voor je vrienden: {adres or '— (tunnel niet klaar; mc tunnel status)'}")
    say(f"Whitelist: {', '.join(s['naam'] for s in core.spelers()['spelers']) or 'nog leeg (mc spelers add <naam>)'}")
    if pub:
        s = gitops.slug()
        say("Deploy key (publiek, voor pushen naar GitHub; één keer toevoegen met 'Allow write access'):")
        say(f"  {pub}")
        say(f"  https://github.com/{s}/settings/keys/new" if s else "")
    return ok


def ensure(stil=False):
    """Zet alles weer goed. Print niets bij --stil als alles in orde is en er niets te melden is."""
    lines = []
    try:
        cfg = core.load()
    except Fout as e:
        say(f"MELD {e}")
        return 1
    try:
        dl.ensure_java(cfg["java"])
        for key in ("paper", "velocity"):
            if cfg.get(key) and not os.path.exists(os.path.join(JARS, cfg[key]["name"])):
                dl.ensure_jar(dl.fill_build(key, cfg[key]["version"], cfg[key]["build"]))
    except Fout as e:
        lines.append(f"MELD tools of jars ontbreken en downloaden lukt niet: {e}")
    if not supervisor.pid():
        supervisor.spawn()
        core.event("INFO", "watchdog was uit (box herstart of Computer Update?) en is gestart")
        time.sleep(3)
    if gitops.is_repo():
        lines += _git_sync(cfg)
    lines += _weekly_update_check()
    for line in core.new_events():
        if " MELD " in line:
            lines.append("MELD " + line.split(" MELD ", 1)[1] + f"  ({line[:16].replace('T', ' ')} UTC)")
    if stil and not lines:
        return 0
    st = ops.status(cfg)
    online = [n for v in st["spelers"].values() for n in v]
    down = [s for s, i in st["diensten"].items() if not i["draait"] and s != "staging"]
    say(f"{'OK' if not down else 'LET OP'}: {('uit: ' + ', '.join(down) + ' · ') if down else ''}"
        f"{len(online)} spelers online · adres {st['adres'] or '—'}")
    for line in lines:
        say(line)
    return 0


def _git_sync(cfg):
    out = []
    failed = read_json(os.path.join(STATE, "apply-mislukt.json"), {})
    try:
        new = gitops.pull()
    except Fout as e:
        return [f"MELD GitHub ophalen mislukt: {e}"]
    head = gitops.git("rev-parse", "--short", "HEAD", check=False)
    dep = applymod.deployed()
    if new or (dep.get("spec_commit") and dep.get("spec_commit") != head and failed.get("commit") != head):
        try:
            report = applymod.apply(cfg)
            if new:
                out.append("MELD nieuw uit GitHub toegepast: " + "; ".join(new[:5]) + " → " + "; ".join(report[:4]))
        except Fout as e:
            write_json(os.path.join(STATE, "apply-mislukt.json"), {"commit": head, "fout": str(e)[:2000]})
            out.append(f"MELD wijziging uit GitHub ({head}) niet toegepast: {str(e)[:600]}")
    try:
        ahead, _ = gitops.fetch()
        if ahead:
            ok, why = gitops.push()
            if not ok:
                flag = os.path.join(STATE, "gemeld-push")
                if not os.path.exists(flag) or time.time() - os.path.getmtime(flag) > 86400:
                    open(flag, "w").close()
                    out.append(f"MELD {ahead} commit(s) nog niet op GitHub: {why}. Publieke sleutel: `mc git sleutel`")
    except Fout as e:
        out.append(f"MELD git: {e}")
    return out


def _weekly_update_check():
    """Eens per week: zijn er nieuwere versies? Eén MELD met de lijst; de Bot beslist volgens BOT.md, Updates."""
    path = os.path.join(STATE, "update-check.json")
    last = read_json(path, {})
    if time.time() - last.get("t", 0) < 7 * 86400:
        return []
    try:
        rows = applymod.update_check(specmod.load())
    except Fout as e:
        return [f"MELD update-check mislukt: {e}"]
    write_json(path, {"t": time.time(), "rijen": rows})
    if not rows:
        return []
    return ["MELD updates beschikbaar: " + "; ".join(f"{w} {n} → {m}{(' (' + o + ')') if o else ''}" for w, n, m, o in rows)]
