"""De watchdog: één Python-proces dat de diensten in de lucht houdt, zonder Bot-beurten.

Elke 5 s: draait wat moet draaien? Elke minuut: hartslag (slaapdetectie), spelers tellen, tunneladres.
Elk uur met spelers: backup. Alles wat de Bot moet weten, schrijft hij als MELD in data/state/events.log;
`mc ensure` geeft die door. Draait los van de Bot (eigen sessie), overleeft dus het einde van een beurt.
"""
import calendar
import os
import subprocess
import sys
import time
import traceback

from . import core
from . import ops
from .base import (HOME, REPO, STATE, Fout, disk, lock, now_iso, proc_alive, proc_cmdline, read_json, say,
                   write_json)

TICK = 5
SUP_JSON = os.path.join(STATE, "supervisor.json")
LOG = os.path.join(STATE, "supervisor.log")


def pid():
    info = read_json(SUP_JSON, {})
    p = info.get("pid")
    if p and proc_alive(p) and "_supervisor" in proc_cmdline(p):
        return p
    return None


def spawn():
    """Start de supervisor los van deze shell. Geeft de pid."""
    p = pid()
    if p:
        return p
    os.makedirs(STATE, exist_ok=True)
    log = open(LOG, "ab")
    env = dict(os.environ, MC_HOME=HOME, PYTHONPATH=REPO)
    proc = subprocess.Popen([sys.executable, "-m", "mctool", "_supervisor"], cwd=REPO, stdin=subprocess.DEVNULL,
                            stdout=log, stderr=subprocess.STDOUT, start_new_session=True, close_fds=True, env=env)
    log.close()
    for _ in range(20):
        if pid():
            return proc.pid
        time.sleep(0.25)
    return proc.pid


def stop():
    p = pid()
    if not p:
        return False
    os.kill(p, 15)
    for _ in range(40):
        if not proc_alive(p):
            return True
        time.sleep(0.25)
    os.kill(p, 9)
    return True


class Supervisor:
    def __init__(self):
        self.crashes = {}       # dienst → tijden van herstarts na een crash
        self.failed = set()     # diensten die we opgaven (te vaak gecrasht)
        self.seen_up = set()    # diensten die we draaiend zagen
        self.last_beat = 0
        self.last_minute = 0
        self.last_players = 0   # laatste moment met spelers online
        self.need_send_back = False
        self.offsite_proc = None
        self.stop = False

    def log(self, msg):
        print(f"{now_iso()} {msg}", flush=True)

    # ── hoofdlus ──
    def run(self):
        import signal
        signal.signal(signal.SIGTERM, lambda *_: setattr(self, "stop", True))
        with lock("supervisor", wait=False):
            write_json(SUP_JSON, {"pid": os.getpid(), "start": now_iso()})
            self.log("supervisor gestart")
            self.beat(first=True)
            while not self.stop:
                for step in (self.services, self.minute):
                    try:
                        step()
                    except Exception:  # de watchdog mag nooit omvallen
                        self.log("fout:\n" + traceback.format_exc())
                time.sleep(TICK)
            self.log("supervisor gestopt (diensten blijven draaien)")

    def services(self):
        cfg = core.load()
        want = core.desired()
        for svc in core.ORDER:
            if svc == "tunnel" and cfg["tunnel"]["modus"] in ("geen", "direct"):
                continue
            if core.held(svc):
                continue
            up = core.running(svc)
            if want.get(svc) == "down":
                if up and svc == "staging":
                    core.stop(cfg, svc)
                continue
            if svc == "staging":
                until = (cfg.get("staging_tot") or 0)
                if up and until and time.time() > until:
                    self.log("staging: tijd om, stoppen")
                    core.stop(cfg, "staging")
                    core.set_desired("staging", "down")
                continue
            if up:
                self.seen_up.add(svc)
                continue
            if svc in self.failed:
                continue
            crashed = svc in self.seen_up
            self.seen_up.discard(svc)
            times = [t for t in self.crashes.get(svc, []) if t > time.time() - 900]
            if len(times) >= 5:
                self.failed.add(svc)
                core.event("MELD", f"{svc} start niet of crasht steeds (5× in 15 min); ik stop met herstarten. "
                                   f"Kijk naar `mc errors {svc}`, los het op en draai `mc ensure`")
                continue
            if svc == "tunnel" and times and time.time() - times[-1] < 60 and cfg["tunnel"].get("bore_poort"):
                cfg["tunnel"]["bore_poort"] = None  # de oude poort is bezet: neem een nieuwe
                core.save(cfg)
            times.append(time.time())
            self.crashes[svc] = times
            self.log(f"{svc}: {'gecrasht, herstarten' if crashed else 'starten'}")
            if crashed:
                core.event("MELD" if svc != "tunnel" else "INFO", f"{svc} was gestopt en is herstart")
                if svc == "survival":
                    self.need_send_back = True
            try:
                core.start(cfg, svc)
            except Fout as e:
                core.event("MELD", f"{svc} start niet: {e}")
                self.failed.add(svc)
        if self.need_send_back and core.ready(cfg, "survival") and core.running("velocity"):
            if core.players(cfg, "lobby"):
                ops.velocity_cmd("send all survival")
                self.log("spelers uit de lobby terug naar survival")
            self.need_send_back = False
        if core.running("tunnel"):
            self.tunnel(cfg)

    def tunnel(self, cfg):
        adres = core.tunnel_address(cfg) or core.parse_tunnel_output(cfg)
        if not adres:
            fout = read_json(os.path.join(STATE, "tunnel-fout.json"))
            if fout:
                self.once_per_day("tunnel-" + fout["code"], f"tunnel ({cfg['tunnel']['modus']}) werkt niet: {fout['uitleg']}")
            return
        t = cfg["tunnel"]
        if adres != t.get("adres"):
            old = t.get("adres")
            t["adres"] = adres
            if t["modus"] == "bore":
                t["bore_poort"] = int(adres.rsplit(":", 1)[1])
            core.save(cfg)
            core.event("MELD", f"serveradres {'gewijzigd' if old else 'klaar'}: {adres}" + (f" (was {old})" if old else ""))

    def minute(self):
        if time.time() - self.last_minute < 60:
            return
        self.last_minute = time.time()
        self.beat()
        cfg = core.load()
        online = [p for s in ("survival", "lobby") for p in (core.players(cfg, s) or [])]
        n = len(online)
        self.strangers(online)
        if n:
            self.last_players = time.time()
            write_json(os.path.join(STATE, "spelers-gezien.json"), {"tijd": now_iso(), "aantal": n})
        self.backups(cfg)
        self.pregen(cfg, n)
        self.offsite(cfg)
        wacht = os.path.join(STATE, "velocity-wacht.json")
        if n == 0 and os.path.exists(wacht) and not core.held("velocity"):
            from . import apply as applymod
            try:
                applymod.apply(cfg, nu=True)
                os.remove(wacht)
                core.event("INFO", "Velocity bijgewerkt nu niemand online was")
            except Fout as e:
                core.event("MELD", f"uitgestelde Velocity-update mislukt: {e}")
                os.remove(wacht)
        free, total = disk()
        if free / total < 0.10:
            self.once_per_day("schijf", f"schijf bijna vol: {free // 2**30} GB vrij; oude backups worden kleiner gehouden")
            ops.prune(dict(cfg, backup=dict(cfg.get("backup") or {}, max_gb=max(2, (cfg.get('backup') or {}).get('max_gb', 20) // 2))))

    def backups(self, cfg):
        last = read_json(os.path.join(STATE, "laatste-backup.json"))
        last_t = calendar.timegm(time.strptime(last["tijd"], "%Y-%m-%dT%H:%M:%SZ")) if last else 0
        age = time.time() - last_t
        played_since = self.last_players > last_t
        if (played_since and age > 3600) or age > 24 * 3600:
            if core.ready(cfg, "survival") and not core.held("survival"):
                try:
                    ops.backup(cfg)
                    self.log("backup gemaakt")
                except Fout as e:
                    core.event("MELD", f"backup mislukt: {e}")

    def strangers(self, online):
        """Zonder whitelist kan iedereen met het adres erin: meld elke naam die niet in data/spelers.json staat, één keer."""
        if not online:
            return
        known = {s["naam"].lower() for s in core.spelers()["spelers"]}
        path = os.path.join(STATE, "gemelde-spelers.json")
        seen = set(read_json(path, []))
        new = [p for p in online if p.lower() not in known and p.lower() not in seen]
        if new:
            core.event("MELD", f"onbekende speler(s) online: {', '.join(new)} (niet in spelers.json). "
                               "Vraag Jelle: toevoegen (mc spelers add) of bannen (mc cmd survival ban <naam>)")
            write_json(path, sorted(seen | {p.lower() for p in new}))

    def pregen(self, cfg, n):
        """Chunky genereert de wereld vooraf; pauzeer als er gespeeld wordt, ga door als het leeg is."""
        path = os.path.join(STATE, "pregen.json")
        st = read_json(path, {})
        if st.get("status") not in ("bezig", "pauze") or not core.ready(cfg, "survival"):
            return
        try:
            if n and st["status"] == "bezig":
                core.rcon(cfg, "survival", "chunky pause")
                st["status"] = "pauze"
            elif not n and st["status"] == "pauze":
                out = core.rcon(cfg, "survival", "chunky continue")
                st["status"] = "klaar" if "no tasks" in out.lower() else "bezig"
            elif st["status"] == "bezig":
                out = core.rcon(cfg, "survival", "chunky progress")
                if "no tasks" in out.lower() or "not running" in out.lower():
                    st["status"] = "klaar"
                    core.event("INFO", "de wereld rond de spawn is vooraf gegenereerd (Chunky klaar)")
        except (Fout, OSError):
            return
        write_json(path, st)

    def offsite(self, cfg):
        """Eén keer per dag de nieuwste backup naar GitHub, in een los proces (uploaden mag even duren)."""
        from . import offsite
        if self.offsite_proc and self.offsite_proc.poll() is None:
            return
        if not offsite.due(cfg):
            return
        offsite.mark_attempt()
        log = open(os.path.join(STATE, "offsite.log"), "ab")
        self.offsite_proc = subprocess.Popen([sys.executable, "-m", "mctool", "backup", "--github", "--stil"],
                                             cwd=REPO, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                             env=dict(os.environ, MC_HOME=HOME, PYTHONPATH=REPO))
        log.close()
        self.log("backup naar GitHub gestart")

    def beat(self, first=False):
        """Hartslag elke minuut. Een gat van meer dan 3 minuten betekent dat de box sliep (of dat wij niet draaiden)."""
        path = os.path.join(STATE, "hartslag")
        now = time.time()
        try:
            prev = float(open(path).read().strip())
        except (OSError, ValueError):
            prev = None
        if prev and now - prev > 180:
            gap = (now - prev) / 60
            gaps = read_json(os.path.join(STATE, "slaap.json"), [])
            gaps.append({"begin": prev, "eind": now, "min": round(gap, 1), "bij_start": first})
            write_json(os.path.join(STATE, "slaap.json"), gaps[-200:])
            if not first:
                self.once_per_day("slaap", f"de box heeft {gap:.0f} min stilgestaan (slaap?); de server was toen onbereikbaar")
        with open(path, "w") as f:
            f.write(str(now))
        self.last_beat = now

    def once_per_day(self, key, msg):
        path = os.path.join(STATE, f"gemeld-{key}")
        try:
            if time.time() - os.path.getmtime(path) < 86400:
                return
        except OSError:
            pass
        open(path, "w").close()
        core.event("MELD", msg)


def main():
    try:
        Supervisor().run()
    except Fout as e:
        say(f"supervisor: {e}")
        sys.exit(1)
