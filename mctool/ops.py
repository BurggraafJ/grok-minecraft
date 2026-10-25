"""Dagelijks beheer: herstarten zonder dat spelers eruit vliegen, backups, spelers, status en zelftest."""
import glob
import json
import os
import re
import shutil
import subprocess
import time

from . import core
from . import net
from .base import (BACKUPS, GB, STATE, Fout, cpus, dir_size, disk, human, lock, meminfo, now_iso, proc_rss,
                   read_json, say, stamp, tail, write_json)


# ── herstart met de lobby als wachtkamer ───────────────────────────────────────────────────────

def velocity_cmd(line):
    net.console_send(core.fifo("velocity"), line)


# ── aankondigingen in de game: [Server] Herladen gestart… / [Server] Klaar! ────────────────────

_AANKONDIGING = {"diepte": 0}


def meld_spelers(cfg, tekst, kleur="yellow", servers=("survival", "lobby")):
    """Chatregel `[Server] <tekst>` voor iedereen op de draaiende Paper-servers (stil als er niemand is)."""
    regel = json.dumps([{"text": "[", "color": "gray"}, {"text": "Server", "color": "gold"},
                        {"text": "] ", "color": "gray"}, {"text": tekst, "color": kleur}], ensure_ascii=False)
    for svc in servers:
        try:
            if core.running(svc) and core.players(cfg, svc):
                core.rcon(cfg, svc, f"tellraw @a {regel}")
        except (Fout, OSError):
            pass


class aankondiging:
    """Om elke herstart of herlaadbeurt: spelers zien het begin en het einde in de chat.

    Genest (apply herstart lobby en survival na elkaar) telt alleen de buitenste: één keer 'gestart', één keer 'klaar'.
    """

    def __init__(self, cfg, aan=True):
        self.cfg = cfg
        self.aan = aan

    def __enter__(self):
        if not self.aan:
            return self
        _AANKONDIGING["diepte"] += 1
        if _AANKONDIGING["diepte"] == 1:
            meld_spelers(self.cfg, "Herladen gestart… even geduld.")
        return self

    def __exit__(self, exc_type, exc, tb):
        if not self.aan:
            return False
        _AANKONDIGING["diepte"] -= 1
        if _AANKONDIGING["diepte"] == 0:
            for _ in range(30):  # wie in de lobby wachtte, eerst terug laten komen
                if not _lobby_terug(self.cfg):
                    break
                time.sleep(0.5)
            time.sleep(1)
            if exc_type is None:
                meld_spelers(self.cfg, "Klaar!", kleur="green")
            else:
                meld_spelers(self.cfg, "Herladen mislukt; de beheerder kijkt ernaar.", kleur="red")
        return False


def _lobby_terug(cfg):
    """Wie in de lobby moet terug naar survival? Niet wie /blijf typte of via /parkour kwam (plugin lobby-melding)."""
    wachtend = core.players(cfg, "lobby") or []
    try:
        out = core.rcon(cfg, "lobby", "lobbymelding wie-terug")
        if "terug:" in out:
            namen = {n.strip().lower() for n in out.split("terug:", 1)[1].split(",") if n.strip()}
            return [n for n in wachtend if n.lower() in namen]
    except (Fout, OSError):
        pass
    return wachtend


def terug_naar_survival(cfg, aftellen=True, ook_blijvers=False):
    """Iedereen in de lobby (behalve wie daar wil blijven) terug naar survival: eerst groot aftellen (plugin
    lobby-melding: titels 5-4-3-2-1 met geluid, daarna stuurt die zelf via Velocity); wie na een paar seconden nog in
    de lobby staat stuurt Velocity. ook_blijvers (na een herstart): wie blijft krijgt het aftellen ook mee, met aan het
    eind «Survival is weer open», maar blijft staan."""
    wachtend = _lobby_terug(cfg)
    if not wachtend and not (ook_blijvers and core.players(cfg, "lobby")):
        return []
    if aftellen:
        try:
            out = core.rcon(cfg, "lobby", "lobbymelding terug alle" if ook_blijvers else "lobbymelding terug")
            if "aftellen" in out:
                for _ in range(20):  # 5 s aftellen + marge
                    time.sleep(0.5)
                    if not _lobby_terug(cfg):
                        return wachtend
                time.sleep(1)
        except (Fout, OSError):
            pass
    for naam in _lobby_terug(cfg):
        velocity_cmd(f"send {naam} survival")
    return wachtend


def restart(cfg, svc, als_leeg=False, between=None):
    """Herstart een dienst. Survival: spelers wachten in de lobby en gaan daarna vanzelf terug.

    between: optionele functie die draait terwijl de dienst uit staat (jar of plugins wisselen).
    """
    if svc == "all":
        with aankondiging(cfg):
            for s in ("lobby", "survival"):
                restart(cfg, s, between=between if s == "survival" else None)
            if core.running("velocity"):
                restart(cfg, "velocity", als_leeg=als_leeg)
        return
    with lock(f"svc-{svc}"), aankondiging(cfg):
        core.hold(svc, 1800)
        try:
            if svc == "velocity":
                _restart_velocity(cfg, als_leeg)
            elif svc == "lobby":
                _restart_plain(cfg, svc, between)
            elif svc == "survival":
                _restart_survival(cfg, between)
            else:
                _restart_plain(cfg, svc, between)
        finally:
            core.unhold(svc)


def _restart_plain(cfg, svc, between):
    core.stop(cfg, svc)
    if between:
        between()
    core.start(cfg, svc)
    if not core.wait_ready(cfg, svc, 600 if svc in core.PAPER else 60):
        raise Fout(f"{svc} komt niet terug; laatste log:\n" + "\n".join(core._tail_log(svc)))
    say(f"  {svc} herstart")


def _restart_survival(cfg, between):
    spelers = core.players(cfg, "survival") or []
    moved = False
    if spelers and core.running("velocity") and core.ready(cfg, "lobby"):
        try:  # plugin Lobbyreis: groot aftellen 5-4-3-2-1 op survival, dan pas naar de lobby
            if "aftellen" in core.rcon(cfg, "survival", "lobbyreis aftellen 5"):
                time.sleep(5.3)
        except (Fout, OSError):
            pass
        try:  # plugin Opmaak: geen [-]-regels nu iedereen even naar de lobby gaat
            core.rcon(cfg, "survival", "opmaak stil 60")
        except (Fout, OSError):
            pass
        time.sleep(1)
        velocity_cmd("send all lobby")
        moved = True
        for _ in range(20):
            if not core.players(cfg, "survival"):
                break
            time.sleep(0.5)
    core.stop(cfg, "survival")
    if between:
        between()
    if moved:  # plugin Opmaak: wie zo terugkomt krijgt geen [+]-regel
        odir = os.path.join(core.server_dir("survival"), "plugins", "Opmaak")
        os.makedirs(odir, exist_ok=True)
        with open(os.path.join(odir, "terugkeer.txt"), "w") as fh:
            fh.write("\n".join(spelers) + "\n")
    core.start(cfg, "survival")
    if not core.wait_ready(cfg, "survival", 600):
        raise Fout("survival komt niet terug; spelers staan in de lobby. Laatste log:\n" + "\n".join(core._tail_log("survival")))
    if moved or core.players(cfg, "lobby"):
        terug_naar_survival(cfg, ook_blijvers=True)
    say(f"  survival herstart{f' ({len(spelers)} spelers via de lobby teruggezet)' if spelers else ''}")


def _restart_velocity(cfg, als_leeg):
    if als_leeg:
        deadline = time.time() + 6 * 3600
        while time.time() < deadline:
            n = sum(len(core.players(cfg, s) or []) for s in ("survival", "lobby"))
            if n == 0:
                break
            time.sleep(30)
        else:
            raise Fout("velocity niet herstart: er bleven spelers online")
    _restart_plain(cfg, "velocity", None)


# ── backups ────────────────────────────────────────────────────────────────────────────────────

BACKUP_ITEMS = ["plugins", "config", "server.properties", "bukkit.yml", "spigot.yml", "commands.yml",
                "whitelist.json", "ops.json", "banned-players.json", "banned-ips.json", "usercache.json"]


def backup(cfg, label=None, svc="survival"):
    d = core.server_dir(svc)
    worlds = sorted(os.path.basename(p) for p in glob.glob(os.path.join(d, "world*")) if os.path.isdir(p))
    if not worlds:
        raise Fout(f"geen wereld gevonden in {d}")
    items = worlds + [i for i in BACKUP_ITEMS if os.path.exists(os.path.join(d, i))]
    os.makedirs(BACKUPS, exist_ok=True)
    name = f"{svc}-{stamp()}{('-' + re.sub(r'[^a-z0-9-]', '', label.lower())) if label else ''}.tar.gz"
    path = os.path.join(BACKUPS, name)
    live = core.ready(cfg, svc)
    with lock(f"backup-{svc}"):
        if live:
            core.rcon(cfg, svc, "save-off")
            core.rcon(cfg, svc, "save-all flush", timeout=120)
        try:
            r = subprocess.run(["tar", "-czf", path + ".part", "--exclude=*.jar", "--exclude=session.lock",
                                "-C", d, *items], capture_output=True, text=True)
            if r.returncode not in (0, 1):  # 1 = bestand veranderde tijdens lezen (bij save-off zeldzaam)
                raise Fout(f"tar faalde: {r.stderr[-400:]}")
            os.replace(path + ".part", path)
        finally:
            if live:
                try:
                    core.rcon(cfg, svc, "save-on")
                except (Fout, OSError):
                    core.event("MELD", f"save-on na backup mislukt op {svc}; draai `mc cmd {svc} save-on`")
    write_json(os.path.join(STATE, "laatste-backup.json"), {"bestand": name, "tijd": now_iso(), "grootte": os.path.getsize(path)})
    prune(cfg)
    return path


def list_backups():
    out = []
    for p in sorted(glob.glob(os.path.join(BACKUPS, "*.tar.gz")), reverse=True):
        out.append({"naam": os.path.basename(p), "grootte": os.path.getsize(p), "tijd": os.path.getmtime(p)})
    return out


def prune(cfg):
    """Houd de laatste N uurbackups, één per dag voor M dagen, de laatste K met label, en blijf onder max_gb."""
    b = cfg.get("backup") or {}
    keep_hour, keep_day, keep_label = b.get("uur", 24), b.get("dag", 7), b.get("label", 5)
    items = list_backups()
    keep = set()
    plain = [i for i in items if re.fullmatch(r"[a-z]+-\d{8}-\d{6}\.tar\.gz", i["naam"])]
    labeled = [i for i in items if i not in plain]
    keep.update(i["naam"] for i in plain[:keep_hour])
    keep.update(i["naam"] for i in labeled[:keep_label])
    days = {}
    for i in plain:
        day = i["naam"].split("-")[1]
        days.setdefault(day, i["naam"])
    keep.update(list(days.values())[:keep_day])
    removed = []
    for i in items:
        if i["naam"] not in keep:
            os.remove(os.path.join(BACKUPS, i["naam"]))
            removed.append(i["naam"])
    max_bytes = (b.get("max_gb") or 20) * GB
    items = list_backups()
    total = sum(i["grootte"] for i in items)
    while total > max_bytes and len(items) > 1:
        oldest = items.pop()
        os.remove(os.path.join(BACKUPS, oldest["naam"]))
        total -= oldest["grootte"]
        removed.append(oldest["naam"])
    return removed


def restore(cfg, name):
    items = list_backups()
    if not items:
        raise Fout("er zijn geen backups")
    if name in ("nieuwste", "laatste"):
        item = next((i for i in items if not i["naam"].endswith("-pre-restore.tar.gz")), items[0])
    else:
        item = next((i for i in items if i["naam"] == name or i["naam"].startswith(name)), None)
        if not item:
            raise Fout(f"backup {name} niet gevonden; `mc backups` toont ze")
    path = os.path.join(BACKUPS, item["naam"])
    svc = item["naam"].split("-")[0]
    d = core.server_dir(svc)
    say(f"  eerst een backup van de huidige stand (label pre-restore)…")
    if glob.glob(os.path.join(d, "world*")):
        backup(cfg, "pre-restore", svc)

    def swap():
        trash = os.path.join(STATE, f"restore-oud-{stamp()}")
        os.makedirs(trash)
        for p in glob.glob(os.path.join(d, "world*")):
            shutil.move(p, trash)
        r = subprocess.run(["tar", "-xzf", path, "-C", d], capture_output=True, text=True)
        if r.returncode != 0:
            for p in glob.glob(os.path.join(trash, "*")):
                shutil.move(p, d)
            raise Fout(f"uitpakken mislukt, oude wereld teruggezet: {r.stderr[-300:]}")
        shutil.rmtree(trash, ignore_errors=True)

    restart(cfg, svc, between=swap)
    return item["naam"]


# ── spelers ────────────────────────────────────────────────────────────────────────────────────

def spelers_add(naam, admin=False):
    if not re.fullmatch(r"[A-Za-z0-9_]{3,16}", naam):
        raise Fout(f"'{naam}' is geen geldige Minecraft-naam (3–16 tekens: letters, cijfers, _)")
    d = core.spelers()
    for s in d["spelers"]:
        if s["naam"].lower() == naam.lower():
            s["admin"] = s.get("admin") or admin
            break
    else:
        d["spelers"].append({"naam": naam, "admin": admin})
    core.save_spelers(d)


def spelers_remove(naam):
    d = core.spelers()
    d["spelers"] = [s for s in d["spelers"] if s["naam"].lower() != naam.lower()]
    core.save_spelers(d)


def spelers_sync(cfg):
    """Zet whitelist en OP op de draaiende servers gelijk aan data/spelers.json. Geeft regels met de uitkomst."""
    d = core.spelers()
    want = {s["naam"].lower(): s for s in d["spelers"]}
    report = []
    for svc in ("survival", "lobby"):
        if not core.ready(cfg, svc):
            report.append(f"{svc}: draait niet, later opnieuw")
            continue
        current = _json_names(os.path.join(core.server_dir(svc), "whitelist.json"))
        for naam_l, s in want.items():
            if naam_l not in current:
                out = core.rcon(cfg, svc, f"whitelist add {s['naam']}")
                if "not exist" in out.lower() or "bestaat niet" in out.lower():
                    report.append(f"{svc}: {s['naam']} bestaat niet bij Mojang (typfout?)")
                else:
                    report.append(f"{svc}: {s['naam']} op de whitelist")
        for naam_l in current - set(want):
            core.rcon(cfg, svc, f"whitelist remove {naam_l}")
            report.append(f"{svc}: {naam_l} van de whitelist")
        if svc == "survival":
            ops = _json_names(os.path.join(core.server_dir(svc), "ops.json"))
            for naam_l, s in want.items():
                if s.get("admin") and naam_l not in ops:
                    core.rcon(cfg, svc, f"op {s['naam']}")
                    report.append(f"survival: {s['naam']} is admin (OP)")
            for naam_l in ops:
                if not (want.get(naam_l) or {}).get("admin"):
                    core.rcon(cfg, svc, f"deop {naam_l}")
                    report.append(f"survival: {naam_l} is geen admin meer")
        core.rcon(cfg, svc, "whitelist reload")
    return report


def _json_names(path):
    try:
        return {e["name"].lower() for e in json.load(open(path))}
    except (OSError, ValueError, KeyError):
        return set()


# ── status ─────────────────────────────────────────────────────────────────────────────────────

def _tps(cfg):
    try:
        out = core.rcon(cfg, "survival", "tps", timeout=3)
        m = re.search(r":\s*\*?([\d.]+),\s*\*?([\d.]+),\s*\*?([\d.]+)", out)
        return float(m.group(1)) if m else None
    except (Fout, OSError):
        return None


def sleep_gaps(hours=24):
    path = os.path.join(STATE, "slaap.json")
    gaps = read_json(path, [])
    cutoff = time.time() - hours * 3600
    return [g for g in gaps if g["eind"] > cutoff]


def status(cfg):
    st = {"tijd": now_iso(), "diensten": {}, "spelers": {}}
    for svc in core.ORDER:
        pid = core.pid_of(svc)
        if not pid and (svc == "staging" or (svc == "tunnel" and cfg["tunnel"]["modus"] in ("geen", "direct"))):
            continue
        info = {"draait": bool(pid), "klaar": bool(pid) and core.ready(cfg, svc)}
        if pid:
            info["pid"] = pid
            info["ram"] = human(proc_rss(pid))
            started = core.started_at(svc)
            if started:
                info["uptime_min"] = round((time.time() - started) / 60)
        st["diensten"][svc] = info
    for svc in ("survival", "lobby"):
        p = core.players(cfg, svc)
        if p is not None:
            st["spelers"][svc] = p
    st["tps"] = _tps(cfg)
    st["adres"] = core.tunnel_address(cfg)
    st["tunnel"] = cfg["tunnel"]["modus"]
    total, avail = meminfo()
    free, dtotal = disk()
    st["box"] = {"ram": f"{human(total - avail)} van {human(total)} in gebruik", "schijf_vrij": human(free),
                 "schijf_pct_vrij": round(100 * free / dtotal), "cpus": cpus(), "load": os.getloadavg()[0]}
    last = read_json(os.path.join(STATE, "laatste-backup.json"))
    st["backup"] = {"laatste": last, "aantal": len(list_backups()), "totaal": human(sum(i["grootte"] for i in list_backups()))}
    st["versies"] = {"minecraft": cfg.get("mc"), "paper": (cfg.get("paper") or {}).get("build"),
                     "velocity": (cfg.get("velocity") or {}).get("version"), "java": cfg.get("java")}
    gaps = sleep_gaps()
    st["slaap_24u"] = {"keer": len(gaps), "langste_min": max([round(g["min"]) for g in gaps], default=0)}
    st["meldingen_open"] = len([l for l in core.new_events(peek=True) if " MELD " in l])
    sup = read_json(os.path.join(STATE, "supervisor.json"), {})
    st["supervisor"] = bool(sup.get("pid")) and _alive(sup.get("pid"))
    return st


def _alive(pid):
    from .base import proc_alive, proc_cmdline
    return bool(pid) and proc_alive(pid) and "_supervisor" in proc_cmdline(pid)


def print_status(st):
    say(f"Adres: {st['adres'] or '— (tunnel niet klaar)'}   (tunnel: {st['tunnel']})")
    say(f"Versies: Minecraft {st['versies']['minecraft']} · Paper build {st['versies']['paper']} · "
        f"Velocity {st['versies']['velocity']} · Java {st['versies']['java']}")
    for svc, i in st["diensten"].items():
        if i["draait"] and i.get("klaar"):
            say(f"  ✓ {svc:<9} draait  · {i.get('ram', '?')} RAM · {i.get('uptime_min', '?')} min op")
        elif i["draait"]:
            say(f"  … {svc:<9} start op · {i.get('ram', '?')} RAM")
        else:
            say(f"  ✗ {svc:<9} UIT")
    online = [n for v in st["spelers"].values() for n in v]
    tps = f" · TPS {st['tps']:.1f}" if st.get("tps") else ""
    where = ", ".join(f"{svc}: {', '.join(n)}" for svc, n in st["spelers"].items() if n)
    say(f"Spelers online: {len(online)}{(' (' + where + ')') if where else ''}{tps}")
    b = st["box"]
    say(f"Box: {b['ram']} · schijf {b['schijf_vrij']} vrij ({b['schijf_pct_vrij']}%) · {b['cpus']} cpu · load {b['load']:.1f}")
    lb = st["backup"]["laatste"]
    say(f"Backups: {st['backup']['aantal']} ({st['backup']['totaal']}), laatste {lb['tijd'] if lb else 'nog geen'}")
    s = st["slaap_24u"]
    if s["keer"]:
        say(f"Box sliep {s['keer']}× in 24 u (langste {s['langste_min']} min)")
    say(f"Supervisor: {'draait' if st['supervisor'] else 'UIT — draai mc ensure'} · open meldingen: {st['meldingen_open']}")


# ── zelftest ───────────────────────────────────────────────────────────────────────────────────

def selftest(cfg, login=None, extern=True):
    """Controleer de hele keten. Geeft (ok, regels)."""
    from . import configfiles as cf
    lines, ok = [], True

    def check(cond, good, bad):
        nonlocal ok
        lines.append(("✓ " + good) if cond else ("✗ " + bad))
        ok = ok and bool(cond)

    s = core.secrets()["forwarding"]
    vsec = open(os.path.join(core.server_dir("velocity"), "forwarding.secret")).read().strip()
    check(vsec == s, "forwarding-geheim van Velocity klopt", "forwarding-geheim van Velocity wijkt af (mc ensure herstelt)")
    for svc in ("lobby", "survival"):
        pg = os.path.join(core.server_dir(svc), "config", "paper-global.yml")
        check(cf.yaml_get(pg, ["proxies", "velocity", "enabled"]) == "true" and cf.yaml_get(pg, ["proxies", "velocity", "secret"]) == s,
              f"{svc}: Velocity-forwarding aan met hetzelfde geheim", f"{svc}: Velocity-forwarding niet goed ingesteld")
        props = cf.props_read(os.path.join(core.server_dir(svc), "server.properties"))
        check(props.get("server-ip") == "127.0.0.1", f"{svc}: alleen bereikbaar op localhost", f"{svc}: server-ip is niet 127.0.0.1")
        from . import spec as specmod
        want_wl = bool(specmod.load().get("whitelist", True))
        if want_wl:
            check(props.get("white-list") == "true", f"{svc}: whitelist aan", f"{svc}: whitelist staat UIT")
        elif svc == "survival":
            lines.append("· whitelist uit (keuze in server.json): elke premium-speler met het adres kan erin; "
                         "de watchdog meldt onbekende namen")
        check(core.ready(cfg, svc), f"{svc}: draait en antwoordt op RCON", f"{svc}: antwoordt niet")
    vel_online = cf.toml_get(os.path.join(core.server_dir("velocity"), "velocity.toml"), None, "online-mode")
    if not cfg.get("offline_test"):
        check(vel_online == "true", "Velocity: online-mode aan (alleen premium-accounts)", "Velocity: online-mode staat UIT")
    try:
        st = net.status("127.0.0.1", cfg["poorten"]["velocity"])
        check(True, f"Velocity antwoordt: {net.describe(st)}", "")
    except (OSError, ValueError, Fout) as e:
        check(False, "", f"Velocity antwoordt niet op de ping ({e})")
    adres = core.tunnel_address(cfg)
    if cfg["tunnel"]["modus"] != "geen":
        check(bool(adres), f"tunneladres: {adres}", "geen tunneladres (tunnel draait niet of start nog)")
        if adres and extern:
            good, how = extern_check(adres)
            check(good, f"bereikbaar via internet ({adres}): {how}", f"niet bereikbaar via {adres} ({how})")
    if login:
        if not cfg.get("offline_test"):
            lines.append("· testlogin overgeslagen (alleen in offline-test; echte accounts loggen in via Microsoft)")
        else:
            try:
                proto = net.status("127.0.0.1", cfg["poorten"]["survival"]).get("version", {}).get("protocol", -1)
                good, why, trace = net.login_test("127.0.0.1", cfg["poorten"]["velocity"], login, proto)
            except (OSError, ValueError, Fout) as e:
                good, why, trace = False, f"geen verbinding ({e})", []
            check(good, f"testlogin {login} via Velocity → backend: {why}", f"testlogin mislukt: {why} [{' '.join(trace[-8:])}]")
    errs = [l for l in tail(core.latest_log("velocity"), 200) if "nable to connect" in l or "orwarding" in l and "ERROR" in l]
    check(not errs, "Velocity-log zonder verbindingsfouten", "Velocity-log: " + (errs[-1][-200:] if errs else ""))
    return ok, lines


def extern_check(adres):
    """Is de server van buitenaf te zien? Eerst zelf pingen; lukt dat niet (de box laat vaak alleen HTTPS naar
    buiten), dan vragen we het mcsrvstat.us, dat via HTTPS van buitenaf kijkt (antwoorden tot 5 min oud)."""
    host, port = adres.rsplit(":", 1)
    try:
        st = net.status(host, int(port), timeout=8)
        return True, f"zelf gepingd, {st.get('ms')} ms"
    except (OSError, ValueError, Fout):
        pass
    from .base import http_json
    try:
        d = http_json(f"https://api.mcsrvstat.us/3/{adres}", timeout=30, tries=2)
    except Fout as e:
        return False, f"zelf pingen kan niet en mcsrvstat.us faalt: {e}"
    dbg = d.get("debug") or {}
    if d.get("online"):
        return True, f"gezien door mcsrvstat.us ({(d.get('players') or {}).get('online', '?')} spelers)"
    return False, "mcsrvstat.us ziet hem niet" + (" (antwoord uit cache, probeer over 5 min)" if dbg.get("cachehit") else "")
