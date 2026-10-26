"""`mc <opdracht>`: de enige ingang voor de Bot. `mc help` toont alles."""
import argparse
import json
import os
import re
import shutil
import sys
import time

from . import apply as applymod
from . import core
from . import downloads as dl
from . import gitops
from . import net
from . import ops
from . import setup as setupmod
from . import spec as specmod
from . import supervisor
from .base import REPO, Fout, read_json, say, tail, write_json

SPEC_REL = "server/server.json"


def build_parser():
    p = argparse.ArgumentParser(prog="mc", description="Beheer van de Minecraft-server (Velocity + Paper) op de box.")
    sub = p.add_subparsers(dest="cmd", metavar="<opdracht>")

    s = sub.add_parser("install", help="eenmalige installatie (idempotent: opnieuw draaien mag)")
    s.add_argument("--eula-akkoord", action="store_true", help="de eigenaar accepteert de Minecraft EULA")
    s.add_argument("--spelers", default="", help="Minecraft-namen voor de whitelist, komma-gescheiden")
    s.add_argument("--admins", default="", help="namen die admin (OP) worden, komma-gescheiden")
    s.add_argument("--tunnel", default="auto", choices=["auto", "bore", "ngrok", "playit", "direct", "geen"],
                   help="auto: bore als de box niet-HTTPS naar buiten mag, anders ngrok (poort 443)")
    s.add_argument("--offline-test", action="store_true", help="alleen voor CI: geen Microsoft-login, geen tunnel")
    s.add_argument("--herbereken-geheugen", action="store_true")

    s = sub.add_parser("ensure", help="alles weer goed zetten (watchdog, GitHub-updates, meldingen)")
    s.add_argument("--stil", action="store_true", help="niets printen als alles goed is")

    s = sub.add_parser("status", help="hoe staat de server ervoor")
    s.add_argument("--json", action="store_true")
    sub.add_parser("up", help="watchdog en alle diensten starten")
    sub.add_parser("down", help="alles stoppen (watchdog eerst)")

    s = sub.add_parser("restart", help="herstarten; survival via de lobby, spelers blijven verbonden")
    s.add_argument("dienst", choices=["survival", "lobby", "kitpvp", "velocity", "tunnel", "all"])
    s.add_argument("--als-leeg", action="store_true", help="wacht tot niemand online is (velocity)")
    s.add_argument("--aankondigen", action="store_true", help="geen effect meer op velocity (die herstart alleen bij 0 spelers)")
    s.add_argument("--forceer", action="store_true", help="velocity herstarten zonder countdown (alleen in nood)")

    s = sub.add_parser("cmd", help="servercommando (survival/lobby/kitpvp via RCON, velocity via console)")
    s.add_argument("dienst", choices=["survival", "lobby", "kitpvp", "staging", "velocity"])
    s.add_argument("commando", nargs=argparse.REMAINDER)

    s = sub.add_parser("logs", help="laatste regels van een log")
    s.add_argument("dienst", choices=list(core.ORDER) + ["supervisor"])
    s.add_argument("-n", type=int, default=60)
    s = sub.add_parser("errors", help="fouten en waarschuwingen uit de logs")
    s.add_argument("dienst", nargs="?", default=None)

    s = sub.add_parser("spelers", help="whitelist en admins (data/spelers.json, niet in Git)")
    s.add_argument("actie", nargs="?", default="lijst", choices=["lijst", "add", "remove", "sync"])
    s.add_argument("naam", nargs="?")
    s.add_argument("--admin", action="store_true")

    s = sub.add_parser("backup", help="backup van de survivalwereld (--github: de nieuwste ook naar GitHub)")
    s.add_argument("--label")
    s.add_argument("--github", action="store_true", help="zet de nieuwste backup als release in de privé backup-repo")
    s.add_argument("--dienst", default="survival", choices=["survival", "lobby", "kitpvp"], help="welke server (standaard survival)")
    s.add_argument("--stil", action="store_true")
    s = sub.add_parser("backups", help="lijst van backups (--github: die op GitHub)")
    s.add_argument("--github", action="store_true")
    s = sub.add_parser("restore", help="backup terugzetten (maakt eerst een backup van nu)")
    s.add_argument("naam", help="bestandsnaam(begin), 'nieuwste', of 'github' / 'github:JJJJMMDD'")

    s = sub.add_parser("apply", help="zet de box gelijk aan server/server.json (staging-test, dan live)")
    s.add_argument("--zonder-staging", action="store_true")
    s.add_argument("--nu", action="store_true", help="velocity ook herstarten als er spelers online zijn")
    s.add_argument("--aankondigen", action="store_true", help="geen effect meer op velocity (die herstart alleen bij 0 spelers)")
    s.add_argument("--forceer", action="store_true", help="paneel: velocity herstarten zonder countdown (alleen in nood)")
    s.add_argument("--dry-run", action="store_true")

    s = sub.add_parser("vastleggen", help="alle wijzigingen in de repo committen en pushen")
    s.add_argument("bericht")

    s = sub.add_parser("plugin", help="plugins: zoek, add, remove, lijst, nieuw, build")
    s.add_argument("actie", choices=["zoek", "add", "remove", "lijst", "nieuw", "build"])
    s.add_argument("naam", nargs="?")
    s.add_argument("--op", default="survival", help="survival, lobby, kitpvp, velocity (komma-gescheiden)")
    s.add_argument("--zonder-staging", action="store_true")

    s = sub.add_parser("staging", help="testserver met een kopie van de wereld")
    s.add_argument("actie", choices=["start", "stop", "test"])
    s.add_argument("--uur", type=float, default=2.0)

    s = sub.add_parser("update", help="nieuwere versies: check of apply")
    s.add_argument("actie", choices=["check", "apply"])
    s.add_argument("wat", nargs="?", default="alles", choices=["alles", "paper", "velocity", "plugins"])
    s.add_argument("--mc", help="naar een nieuwe Minecraft-versie (alleen op Jelles woord)")
    s.add_argument("--jelle-akkoord", action="store_true")

    s = sub.add_parser("tunnel", help="het publieke adres: status, herstart, modus <m>, token (ngrok)")
    s.add_argument("actie", nargs="?", default="status", choices=["status", "herstart", "modus", "token"])
    s.add_argument("modus", nargs="?", choices=["bore", "ngrok", "playit", "direct", "geen"])

    s = sub.add_parser("git", help="GitHub: status, pull, push, token, merge <branch>")
    s.add_argument("actie", nargs="?", default="status", choices=["status", "pull", "push", "token", "merge"])
    s.add_argument("branch", nargs="?", help="bij merge: de branch (bv. van een Cursor-agent)")

    s = sub.add_parser("selftest", help="controleer de hele keten")
    s.add_argument("--login", help="testlogin met deze naam (alleen offline-test)")
    s.add_argument("--geen-extern", action="store_true", help="niet via het internetadres pingen")

    s = sub.add_parser("paneel", help="paneel-modus (Sparked/Pterodactyl): status, deploy, cmd, logs, start, stop, "
                                      "restart, backup, backups, push, vars")
    s.add_argument("actie", choices=["status", "deploy", "cmd", "logs", "start", "stop", "restart", "kill", "backup",
                                     "backups", "push", "vars", "ensure", "schema", "spiegel", "github-backup", "rechten"])
    s.add_argument("dienst", nargs="?", help="survival, lobby, kitpvp, velocity (of all)")
    s.add_argument("rest", nargs=argparse.REMAINDER, help="bij cmd: het commando; bij vars: SLEUTEL=waarde")
    s.add_argument("--geen-herstart", action="store_true")
    s.add_argument("--aankondigen", action="store_true", help="geen effect meer op velocity (die herstart alleen bij 0 spelers)")
    s.add_argument("--forceer", action="store_true", help="velocity zonder countdown (alleen in nood)")
    s.add_argument("-n", type=int, default=60)

    sub.add_parser("help", help="deze hulp")
    return p


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if argv[:1] == ["_supervisor"]:  # de watchdog zelf (gestart door `mc up`/`mc ensure`)
        supervisor.main()
        return 0
    p = build_parser()
    a = p.parse_args(argv)
    if not a.cmd or a.cmd == "help":
        p.print_help()
        return 0
    try:
        if a.cmd in PANEEL_ROUTES and _paneel_actief():
            return PANEEL_ROUTES[a.cmd](a) or 0
        return HANDLERS[a.cmd](a) or 0
    except Fout as e:
        say(f"FOUT: {e}")
        return 1
    except KeyboardInterrupt:
        return 130


# ── handlers ───────────────────────────────────────────────────────────────────────────────────

def _names(s):
    return [x.strip() for x in (s or "").split(",") if x.strip()]


def h_install(a):
    ok = setupmod.install(a.eula_akkoord, _names(a.spelers), _names(a.admins), a.tunnel, a.offline_test,
                          a.herbereken_geheugen)
    return 0 if ok else 2


def h_ensure(a):
    return setupmod.ensure(a.stil)


def h_status(a):
    st = ops.status(core.load())
    if a.json:
        print(json.dumps(st, indent=2, ensure_ascii=False))
    else:
        ops.print_status(st)


def h_up(a):
    core.load()
    pid = supervisor.spawn()
    say(f"watchdog draait (pid {pid}); diensten starten binnen een minuut. `mc status` om te kijken.")


def h_down(a):
    cfg = core.load()
    supervisor.stop()
    for svc in ("tunnel", "velocity", "staging", "survival", "kitpvp", "lobby"):
        if core.stop(cfg, svc):
            say(f"  {svc} gestopt")
    say("alles uit. `mc up` start het weer.")


def h_restart(a):
    cfg = core.load()
    ops.restart(cfg, a.dienst, als_leeg=a.als_leeg)


def h_cmd(a):
    cfg = core.load()
    command = " ".join(a.commando).strip().lstrip("/")
    if not command:
        raise Fout("geen commando")
    if a.dienst == "velocity":
        log = core.latest_log("velocity")
        before = len(tail(log, 100000))
        net.console_send(core.fifo("velocity"), command)
        time.sleep(1.5)
        for line in tail(log, 100000)[before:]:
            say(line)
    else:
        say(core.rcon(cfg, a.dienst, command, timeout=30) or "(geen uitvoer)")


def h_logs(a):
    path = os.path.join(core.STATE, "supervisor.log") if a.dienst == "supervisor" else core.latest_log(a.dienst)
    for line in tail(path, a.n):
        say(line)


def h_errors(a):
    svcs = [a.dienst] if a.dienst else ["survival", "lobby", "kitpvp", "velocity", "tunnel", "supervisor"]
    for svc in svcs:
        path = os.path.join(core.STATE, "supervisor.log") if svc == "supervisor" else core.latest_log(svc)
        hits = [l for l in tail(path, 3000) if re.search(r"ERROR|SEVERE|Exception|fout:|WARN.*(plugin|Plugin)", l)]
        say(f"── {svc}: {len(hits)} regels" + (" (laatste 15)" if len(hits) > 15 else ""))
        for l in hits[-15:]:
            say("  " + l[-300:])


def h_spelers(a):
    if a.actie == "lijst":
        for s in core.spelers()["spelers"]:
            say(f"  {s['naam']}{' (admin)' if s.get('admin') else ''}")
        return
    if a.actie in ("add", "remove") and not a.naam:
        raise Fout("geef een naam")
    if a.actie == "add":
        ops.spelers_add(a.naam, a.admin)
    elif a.actie == "remove":
        ops.spelers_remove(a.naam)
    cfg = core.load()
    for line in ops.spelers_sync(cfg):
        say("  " + line)
    from . import apply as applymod  # groepen met leden "admins" volgen spelers.json
    from . import spec as specmod
    spec = specmod.load()
    if spec.get("groepen") and core.ready(cfg, "survival"):
        acties = applymod._groepen_plan(spec)
        if acties:
            say("  " + ", ".join(applymod.runtime_apply(cfg, spec, acties, alleen_groepen=True)))


def h_backup(a):
    from . import offsite
    cfg = core.load()
    if not a.github:
        say(f"backup: {ops.backup(cfg, a.label, svc=a.dienst)}")
        return
    try:
        if not offsite._newest_local():
            ops.backup(cfg)
        tag, removed = offsite.push(cfg)
        if not a.stil:
            say(f"op GitHub: {tag} in {offsite.repo(cfg)}" + (f" (weg: {', '.join(removed)})" if removed else ""))
    except Fout as e:
        if a.stil:
            core.event("MELD", f"backup naar GitHub mislukt: {e}")
            st_path = os.path.join(core.STATE, "offsite.json")
            st = read_json(st_path, {})
            st["fout"] = str(e)[:300]
            write_json(st_path, st)
            return 1
        raise


def h_backups(a):
    from . import offsite
    from .base import human
    if a.github:
        offsite.lijst(core.load())
        return
    for b in ops.list_backups():
        say(f"  {b['naam']}  {human(b['grootte'])}")


def h_restore(a):
    from . import offsite
    cfg = core.load()
    name = a.naam
    if name.startswith("github"):
        which = name.split(":", 1)[1] if ":" in name else "nieuwste"
        path = offsite.fetch(cfg, which, log=say)
        say(f"van GitHub gehaald en gecontroleerd: {os.path.basename(path)}")
        name = os.path.basename(path)
    say(f"teruggezet: {ops.restore(cfg, name)}")


def h_apply(a):
    for line in applymod.apply(core.load(), zonder_staging=a.zonder_staging, nu=a.nu, dry_run=a.dry_run):
        say(line)


def _commit_push(message, paths):
    if not gitops.is_repo():
        return
    sha = gitops.commit(message, paths)
    if not sha:
        return
    ok, why = gitops.push()
    say(f"vastgelegd: {sha} {message} · {'op GitHub' if ok else 'nog niet op GitHub: ' + why}")


def h_vastleggen(a):
    _commit_push(a.bericht, ["."])


def _spec_change(mutator, message, zonder_staging=False):
    """Wijzig server.json, pas toe, en leg vast. Mislukt het toepassen, dan gaat server.json terug."""
    original = open(specmod.path()).read()
    s = specmod.load()
    mutator(s)
    specmod.save(s)
    try:
        for line in applymod.apply(core.load(), s, zonder_staging=zonder_staging):
            say(line)
    except Fout:
        open(specmod.path(), "w").write(original)
        raise
    _commit_push(message, ["server", "plugins"])


def h_plugin(a):
    if a.actie == "lijst":
        s = specmod.load()
        dep = applymod.deployed()["plugins"]
        for p in s["plugins"]:
            live = all(p["naam"] in (dep.get(t) or {}) for t in p["op"])
            versie = p.get("versie") or "eigen"
            say(f"  {'✓' if live else '·'} {p['naam']:<22} {versie:<26} op {', '.join(p['op'])}"
                f"{'' if p.get('officieel', True) else '  (niet officieel voor deze MC-versie)'}")
        return
    if not a.naam:
        raise Fout("geef een naam")
    if a.actie == "zoek":
        s = specmod.load()
        for h in dl.modrinth_search(a.naam, s["minecraft"]):
            say(f"  {h['slug']:<28} {h['downloads']:>10} downloads · {h['omschrijving']}")
        return
    op = [t.strip() for t in a.op.split(",") if t.strip()]
    if a.actie == "add":
        s = specmod.load()
        if any(p.get("project") == a.naam.lower() or p["naam"].lower() == a.naam.lower() for p in s["plugins"]):
            raise Fout(f"{a.naam} staat al in server.json")
        entry = specmod.resolve_modrinth(a.naam.lower(), s["minecraft"], op[0])
        naam = specmod.modrinth_title(a.naam.lower()).split(" - ")[0].strip()
        entry = dict({"naam": naam, "op": op}, **entry)

        def add(sp):
            sp["plugins"].append(entry)
            sp["wachtlijst"] = [w for w in sp.get("wachtlijst") or [] if w["project"] != entry["project"]]
        _spec_change(add, f"plugin {naam} {entry['versie']} toegevoegd ({', '.join(op)})", a.zonder_staging)
    elif a.actie == "remove":
        def rm(s):
            before = len(s["plugins"])
            s["plugins"] = [p for p in s["plugins"] if a.naam.lower() not in (p["naam"].lower(), p.get("project"))]
            if len(s["plugins"]) == before:
                raise Fout(f"{a.naam} staat niet in server.json")
        _spec_change(rm, f"plugin {a.naam} verwijderd", a.zonder_staging)
    elif a.actie == "nieuw":
        naam = a.naam.lower()
        if not re.fullmatch(r"[a-z][a-z0-9-]{2,30}", naam):
            raise Fout("naam: kleine letters, cijfers en '-', 3–31 tekens")
        dst = os.path.join(REPO, "plugins", naam)
        if os.path.exists(dst):
            raise Fout(f"plugins/{naam} bestaat al")
        _scaffold(naam, dst)
        s = specmod.load()
        s["plugins"].append({"naam": naam, "bron": "eigen", "pad": f"plugins/{naam}", "op": op})
        specmod.save(s)
        say(f"plugins/{naam} gemaakt en in server.json gezet (op {', '.join(op)}). Schrijf de code, dan "
            f"`mc plugin build {naam}`, `mc apply` en `mc vastleggen \"plugin {naam}\"`.")
    elif a.actie == "build":
        s = specmod.load()
        p = next((p for p in s["plugins"] if p["naam"].lower() == a.naam.lower() and p["bron"] == "eigen"), None)
        pad = p["pad"] if p else f"plugins/{a.naam}"
        say(f"gebouwd: {applymod.build_plugin(pad, s['minecraft'], s['java'])}")


def _scaffold(naam, dst):
    src = os.path.join(REPO, "plugins", "lobby-melding")
    camel = "".join(w.capitalize() for w in naam.split("-"))
    pkg = naam.replace("-", "")
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns("build", ".gradle"))
    for root, _dirs, files in os.walk(dst):
        for f in files:
            path = os.path.join(root, f)
            text = open(path, encoding="utf-8").read()
            text = text.replace("lobby-melding", naam).replace("LobbyMelding", camel).replace("lobbymelding", pkg)
            open(path, "w", encoding="utf-8").write(text)
    old = os.path.join(dst, "src", "main", "java", "nl", "burggraaf", "mc", "lobbymelding")
    new = os.path.join(dst, "src", "main", "java", "nl", "burggraaf", "mc", pkg)
    os.rename(old, new)
    os.rename(os.path.join(new, "LobbyMelding.java"), os.path.join(new, f"{camel}.java"))


def h_staging(a):
    cfg = core.load()
    if a.actie == "stop":
        core.set_desired("staging", "down")
        core.stop(cfg, "staging")
        shutil.rmtree(core.server_dir("staging"), ignore_errors=True)
        say("staging gestopt en opgeruimd")
        return
    s = specmod.load()
    arts = applymod.fetch_artifacts(s, s["java"])
    paper = dl.fill_build("paper", s["minecraft"], s["paper_build"])
    jar = dl.ensure_jar(paper)
    if a.actie == "test":
        ok, problems, warnings = applymod.staging_test(cfg, s, arts, jar)
        say("staging-test: " + ("OK" if ok else "MISLUKT"))
        for line in problems + [f"waarschuwing: {w}" for w in warnings]:
            say("  " + line)
        return 0 if ok else 1
    applymod.prepare_staging(cfg, s, arts, jar)
    cfg["staging_tot"] = time.time() + a.uur * 3600
    core.set_desired("staging", "up")
    core.start(cfg, "staging")
    saved = dict(cfg)
    saved.pop("staging_override", None)
    core.save(saved)
    ok = core.wait_ready(cfg, "staging", 900)
    say(f"staging {'draait' if ok else 'start NIET'}; admins komen erin met /server staging. Stopt vanzelf na {a.uur:g} uur.")


def h_update(a):
    s = specmod.load()
    if a.actie == "check":
        rows = applymod.update_check(s)
        if not rows:
            say("alles is bij")
        for wat, nu, nieuw, opm in rows:
            say(f"  {wat:<22} {nu:<24} → {nieuw:<24} {opm}")
        return
    if a.mc and not a.jelle_akkoord:
        raise Fout("een nieuwe Minecraft-versie zet de wereld onomkeerbaar om en vrienden moeten hun client "
                   "bijwerken: alleen met --jelle-akkoord, na Jelles letterlijke ja")
    changed = []
    _spec_change(lambda sp: changed.extend(applymod.update_bump(sp, a.wat, a.mc)),
                 "update: " + (a.mc or a.wat))
    say("bijgewerkt: " + ("; ".join(changed) if changed else "niets"))


def h_tunnel(a):
    cfg = core.load()
    if a.actie == "status":
        say(f"modus {cfg['tunnel']['modus']} · adres {core.tunnel_address(cfg) or '—'}")
        for line in tail(core.outfile("tunnel"), 8):
            say("  " + line)
        return
    if a.actie == "herstart":
        ops.restart(cfg, "tunnel")
        time.sleep(3)
        say(f"adres: {core.parse_tunnel_output(cfg) or core.tunnel_address(cfg) or '— (nog niet klaar)'}")
        return
    if a.actie == "token":
        tok = os.environ.get("NGROK_AUTHTOKEN", "").strip()
        if not tok:
            raise Fout("zet NGROK_AUTHTOKEN in de Secrets van de Bot (dashboard.ngrok.com → Your Authtoken) en draai opnieuw")
        core.save_secret_file("ngrok_token", tok)
        say("ngrok-token bewaard. Nu: mc tunnel modus ngrok")
        return
    if not a.modus:
        raise Fout("geef een modus: bore, ngrok, playit, direct of geen")
    core.stop(cfg, "tunnel")
    cfg["tunnel"]["modus"] = a.modus
    cfg["tunnel"]["adres"] = None
    core.save(cfg)
    if core.configure_velocity(cfg):
        ops.restart(cfg, "velocity", als_leeg=True)
    say(f"tunnelmodus nu {a.modus}; de watchdog start hem. `mc tunnel status` voor het adres.")


def h_git(a):
    if a.actie == "status":
        st = gitops.status()
        say(json.dumps(st, ensure_ascii=False))
    elif a.actie == "pull":
        new = gitops.pull()
        say("nieuw: " + ("; ".join(new) if new else "niets"))
        if new:
            for line in applymod.apply(core.load()):
                say(line)
    elif a.actie == "push":
        ok, why = gitops.push()
        say(why)
        return 0 if ok else 1
    elif a.actie == "merge":
        if not a.branch:
            raise Fout("geef de branch: mc git merge <branch>")
        prev = gitops.git("rev-parse", "HEAD")
        gitops.git("fetch", "origin", a.branch)
        gitops.git("merge", "--no-edit", f"origin/{a.branch}", check=False)
        if gitops.git("diff", "--name-only", "--diff-filter=U", check=False):
            gitops.git("merge", "--abort", check=False)
            raise Fout(f"merge van {a.branch} botst; los het op in de branch")
        try:
            for line in applymod.apply(core.load()):
                say(line)
        except Fout:
            gitops.git("reset", "--hard", prev)
            raise
        ok, why = gitops.push()
        say(f"{a.branch} samengevoegd en live · {'op GitHub' if ok else 'nog niet op GitHub: ' + why}")
    elif a.actie == "token":
        from . import offsite
        for line in gitops.save_token(os.environ.get("GITHUB_TOKEN", ""), [gitops.slug(), offsite.repo(core.load())]):
            say("  " + line)
        ok, why = gitops.push()
        say(f"token bewaard; pushen: {why}")


def h_selftest(a):
    ok, lines = ops.selftest(core.load(), login=a.login, extern=not a.geen_extern)
    for line in lines:
        say(line)
    return 0 if ok else 1


# ── paneel-modus (data/config.json → modus: paneel) ────────────────────────────────────────────

def _paneel_actief():
    from . import paneel
    try:
        return paneel.actief(core.load())
    except Fout:
        return False


def h_paneel(a):
    from . import paneel
    cfg = core.load()
    rest = list(getattr(a, "rest", None) or [])
    if "--geen-herstart" in rest or a.dienst == "--geen-herstart":   # REMAINDER slokt de vlag op
        rest = [r for r in rest if r != "--geen-herstart"]
        a.geen_herstart = True
        if a.dienst == "--geen-herstart":
            a.dienst = rest.pop(0) if rest else None
    for vlag in ("--aankondigen", "--forceer"):          # REMAINDER slokt ook deze op
        if vlag in rest or a.dienst == vlag:
            rest = [r for r in rest if r != vlag]
            setattr(a, vlag[2:], True)
            if a.dienst == vlag:
                a.dienst = rest.pop(0) if rest else None
    aank, forc = getattr(a, "aankondigen", False), getattr(a, "forceer", False)
    if a.actie == "logs" and "-n" in rest:   # idem voor -n
        i = rest.index("-n")
        a.n = int(rest[i + 1])
        del rest[i:i + 2]
    a.rest = rest
    actie, dienst = a.actie, a.dienst
    alle = paneel.diensten(cfg)
    doel = alle if dienst in (None, "all") else [dienst]
    if actie == "status":
        paneel.print_status(paneel.status(cfg))
    elif actie == "ensure":
        return paneel.ensure(cfg, stil=True)
    elif actie == "deploy":
        nieuw = paneel.deploy(cfg, herstart=not a.geen_herstart, alleen=None if dienst in (None, "all") else [dienst],
                              aankondigen=aank, forceer=forc)
        say("uitgerold: " + (", ".join(nieuw) if nieuw else "niets nieuw (paneel = server.json)"))
    elif actie == "cmd":
        if not dienst or not a.rest:
            raise Fout("mc paneel cmd <dienst> <commando>")
        out = paneel.cmd_met_uitvoer(cfg, dienst, " ".join(a.rest).strip().lstrip("/"))
        say(out.rstrip() or "(geen uitvoer in de log)")
    elif actie == "logs":
        for line in paneel.logs(cfg, dienst or "survival", a.n):
            say(line)
    elif actie in ("start", "stop", "restart", "kill"):
        if "velocity" in doel and actie in ("stop", "restart", "kill") and not paneel.velocity_akkoord(cfg, aank, forc):
            raise Fout("velocity: spelers online, geweigerd. De proxy herstart alleen bij 0 spelers; mc ensure doet het zodra hij leeg is.")
        for svc in (doel if actie != "stop" else list(reversed(doel))):
            if actie == "restart":
                paneel.herstart_dienst(cfg, svc)
            else:
                paneel.power(cfg, svc, actie)
                say(f"  {svc}: {actie}")
    elif actie == "backup":
        for svc in (doel if dienst else ["survival"]):
            b = paneel.backup(cfg, svc)
            say(f"  {svc}: backup {b.get('name')} gestart")
    elif actie == "backups":
        for svc in (doel if dienst else ["survival", "lobby"]):
            for b in paneel.backups(cfg, svc):
                say(f"  {svc}: {b['name']} · {round((b.get('bytes') or 0) / 1048576)} MB · {b.get('created_at')}"
                    f"{' · bezig' if not b.get('completed_at') else ''}")
    elif actie == "push":
        if not dienst or dienst == "all":
            raise Fout("mc paneel push <dienst> [lokale map]: zet een map (zonder logs/jars) op het paneel")
        src = a.rest[0] if a.rest else core.server_dir(dienst)
        n, tot = paneel.push_tree(cfg, dienst, src, "/")
        say(f"{dienst}: {n} delen, {tot // 1048576} MB")
    elif actie == "spiegel":
        for svc in [d for d in doel if d != "velocity"] if dienst else ["survival"]:
            paneel.spiegel(cfg, svc)
    elif actie == "rechten":   # centrale rechten: ops.json per server + beroepen (spiegel in spelers.json)
        paneel.beroepen_spiegel(cfg)
        for svc, namen in paneel.ops_controle(cfg).items():
            say(f"{svc}: OP = {', '.join(namen) or '-'}")
        for s_ in core.spelers()["spelers"]:
            say(f"  {s_['naam']}: {'admin · ' if s_.get('admin') else ''}{s_.get('beroep', 'wildvreemde')}")
    elif actie == "github-backup":
        paneel.github_backup(cfg, dienst if dienst and dienst != "all" else "survival")
    elif actie == "schema":
        paneel.schemas_instellen(cfg)
    elif actie == "vars":
        for svc in doel:
            for kv in a.rest:
                k, v = kv.split("=", 1)
                paneel.set_var(cfg, svc, k, v)
            say(f"  {svc}: {paneel.info(cfg, svc)['invocation']}")


def _p_status(a):
    from . import paneel
    st = paneel.status(core.load())
    if a.json:
        print(json.dumps(st, indent=2, ensure_ascii=False))
    else:
        paneel.print_status(st)


def _p_route(actie, dienst_attr="dienst", rest_attr=None):
    def h(a):
        ns = argparse.Namespace(actie=actie, dienst=getattr(a, dienst_attr, None) if dienst_attr else None,
                                rest=getattr(a, rest_attr, []) if rest_attr else [], geen_herstart=False,
                                n=getattr(a, "n", 60))
        return h_paneel(ns)
    return h


def _p_ensure(a):
    from . import paneel
    return paneel.ensure(core.load(), stil=a.stil)


def _p_apply(a):
    from . import paneel
    if a.dry_run:
        raise Fout("--dry-run bestaat niet in paneel-modus")
    nieuw = paneel.deploy(core.load(), aankondigen=getattr(a, "aankondigen", False), forceer=getattr(a, "forceer", False))
    say("uitgerold op het paneel: " + (", ".join(nieuw) if nieuw else "niets nieuw"))


def _p_backup(a):
    if a.github:
        return h_paneel(argparse.Namespace(actie="github-backup", dienst=a.dienst, rest=[], geen_herstart=False, n=60))
    return h_paneel(argparse.Namespace(actie="backup", dienst=a.dienst, rest=[], geen_herstart=False, n=60))


def _p_restart(a):
    return h_paneel(argparse.Namespace(actie="restart", dienst=a.dienst, rest=[], geen_herstart=False, n=60,
                                       aankondigen=getattr(a, "aankondigen", False), forceer=getattr(a, "forceer", False)))


def _p_restore(a):
    """Paneel-modus: `mc restore github[:JJJJMMDD]` haalt de backup op, voegt de delen samen en controleert de
    sha256, maar zet niets over een draaiende wereld op het paneel. Terugzetten op het paneel: zie BOT.md."""
    from . import offsite
    if not a.naam.startswith("github"):
        raise Fout("in paneel-modus zet je een paneel-backup terug in het paneel (Backups → Restore) of via de API; "
                   "`mc restore github[:JJJJMMDD]` haalt een GitHub-backup op en controleert hem")
    which = a.naam.split(":", 1)[1] if ":" in a.naam else "nieuwste"
    path = offsite.fetch(core.load(), which, log=say)
    say(f"van GitHub gehaald en gecontroleerd (delen: sha256 per deel en geheel; oud: grootte): {path}")
    say("terugzetten op het paneel: eerst `mc backup` (paneel), dan uitpakken op de box en "
        "`mc paneel stop survival` → `mc paneel push survival <map>` → `mc paneel start survival` (BOT.md)")


def _p_backups(a):
    from . import offsite
    if a.github:
        offsite.lijst(core.load())
        return
    return h_paneel(argparse.Namespace(actie="backups", dienst=None, rest=[], geen_herstart=False, n=60))


def _p_niet(a):
    raise Fout(f"`mc {a.cmd}` hoort bij de box-modus; in paneel-modus: mc paneel … (zie BOT.md, Paneel-modus)")


PANEEL_ROUTES = {
    "status": _p_status, "ensure": _p_ensure, "apply": _p_apply, "restart": _p_restart,
    "up": _p_route("start", None), "down": _p_route("stop", None),
    "cmd": _p_route("cmd", "dienst", "commando"), "logs": _p_route("logs"), "backup": _p_backup,
    "backups": _p_backups, "tunnel": _p_niet, "staging": _p_niet, "restore": _p_restore,
    "selftest": _p_route("status", None),
}


HANDLERS = {
    "install": h_install, "ensure": h_ensure, "status": h_status, "up": h_up, "down": h_down,
    "restart": h_restart, "cmd": h_cmd, "logs": h_logs, "errors": h_errors, "spelers": h_spelers,
    "backup": h_backup, "backups": h_backups, "restore": h_restore, "apply": h_apply, "vastleggen": h_vastleggen,
    "plugin": h_plugin, "staging": h_staging, "update": h_update, "tunnel": h_tunnel, "git": h_git,
    "selftest": h_selftest, "paneel": h_paneel,
}
