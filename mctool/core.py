"""Configuratie, geheimen, gebeurtenissen en de diensten zelf (starten, stoppen, gereed)."""
import os
import secrets as _secrets
import shutil
import signal
import subprocess
import time

from . import configfiles as cf
from . import downloads as dl
from . import net
from .base import (CONFIG, EVENTS, JARS, MB, REPO, SECRETS, SERVERS, SPELERS, STATE, Fout, meminfo,
                   now_iso, proc_alive, proc_cmdline, read_json, read_text, say, write_json, write_text)

PAPER = ("lobby", "survival", "staging")
ORDER = ("lobby", "survival", "velocity", "tunnel", "staging")

DEFAULT = {
    "versie": 1,
    "eula": False,
    "mc": None,
    "paper": None,
    "velocity": None,
    "java": 25,
    "geheugen_mb": {},
    "poorten": {"velocity": 25565, "lobby": 30066, "survival": 30067, "staging": 30068},
    "rcon": {"lobby": 30076, "survival": 30077, "staging": 30078},
    "tunnel": {"modus": "bore", "bore_server": "bore.pub", "bore_poort": None, "adres": None},
    "plugins": {"survival": {}, "lobby": {}, "velocity": {}},
    "motd": "<gold>Burggraaf Craft</gold> <gray>— alleen op uitnodiging</gray>",
    "offline_test": False,
    "gedeeld": False,
    "backup": {"uur": 24, "dag": 7, "label": 5, "max_gb": 20},
}

LOCALE = ["-Duser.language=en", "-Duser.country=US"]  # vaste taal: de tool leest de uitvoer van commando's
SMALL_FLAGS = ["-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+DisableExplicitGC"]
VELOCITY_FLAGS = ["-XX:+UseG1GC", "-XX:G1HeapRegionSize=4M", "-XX:+UnlockExperimentalVMOptions",
                  "-XX:+ParallelRefProcEnabled", "-XX:+AlwaysPreTouch", "-XX:MaxInlineLevel=15"]


# ── configuratie ───────────────────────────────────────────────────────────────────────────────

def load():
    cfg = read_json(CONFIG)
    if cfg is None:
        raise Fout("nog niet geïnstalleerd: draai eerst `mc install`")
    for k, v in DEFAULT.items():
        cfg.setdefault(k, v if not isinstance(v, dict) else dict(v))
    return cfg


def load_or_default():
    cfg = read_json(CONFIG) or {}
    for k, v in DEFAULT.items():
        cfg.setdefault(k, v if not isinstance(v, dict) else dict(v))
    return cfg


def save(cfg):
    write_json(CONFIG, cfg)


def secrets():
    path = os.path.join(SECRETS, "secrets.json")
    s = read_json(path)
    if not s:
        s = {"forwarding": _secrets.token_urlsafe(24),
             "rcon": {k: _secrets.token_urlsafe(18) for k in PAPER}}
        write_json(path, s, mode=0o600)
    return s


def spelers():
    return read_json(SPELERS, {"spelers": []})


def save_spelers(d):
    write_json(SPELERS, d)


def memory_plan(total_bytes, gedeeld=False):
    """Geheugen in MB per dienst. Reserve voor het OS, de Bot en zijn browser; meer als de box gedeeld is."""
    total = total_bytes // MB
    reserve = 3072 + (6144 if gedeeld else 0)
    if total < 8192:
        plan = {"velocity": 384, "lobby": 512, "staging": 1024}
    else:
        plan = {"velocity": 512, "lobby": 768, "staging": 2048}
    overhead = 450  # per JVM buiten de heap (metaspace, netty, threads)
    rest = total - reserve - sum(v + overhead for v in plan.values()) - overhead
    survival = max(1536, min(10240, rest))
    plan["survival"] = survival // 256 * 256
    return plan


# ── gebeurtenissen (de brug tussen het gratis script en een betaalde Bot-beurt) ──────────────────

def event(kind, msg):
    os.makedirs(STATE, exist_ok=True)
    with open(EVENTS, "a", encoding="utf-8") as f:
        f.write(f"{now_iso()} {kind} {msg}\n")


def new_events(peek=False):
    off_path = os.path.join(STATE, "events.offset")
    try:
        off = int(read_text(off_path, "0") or 0)
    except ValueError:
        off = 0
    try:
        with open(EVENTS, "rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            if off > size:
                off = 0
            f.seek(off)
            lines = f.read().decode("utf-8", "replace").splitlines()
    except FileNotFoundError:
        return []
    if not peek:
        write_text(off_path, str(size))
    return lines


# ── diensten ───────────────────────────────────────────────────────────────────────────────────

def server_dir(svc):
    return os.path.join(STATE, "tunnel") if svc == "tunnel" else os.path.join(SERVERS, svc)


def _pidfile(svc):
    return os.path.join(STATE, f"{svc}.pid")


def fifo(svc):
    return os.path.join(STATE, f"{svc}.stdin")


def outfile(svc):
    return os.path.join(STATE, f"{svc}.out")


def latest_log(svc):
    if svc == "tunnel":
        return outfile(svc)
    return os.path.join(server_dir(svc), "logs", "latest.log")


def _marker_ok(svc, cmdline):
    if svc == "tunnel":
        return any(f"tools/{t}" in cmdline for t in ("bore", "ngrok", "playit"))
    return f"-Dmc.service={svc}" in cmdline


def secret_file(name):
    """Een geheim uit data/secrets (één regel), of None."""
    v = (read_text(os.path.join(SECRETS, name), "") or "").strip()
    return v or None


def save_secret_file(name, value):
    write_text(os.path.join(SECRETS, name), value.strip() + "\n", mode=0o600)


def pid_of(svc):
    info = read_json(_pidfile(svc))
    if not info:
        return None
    pid = info.get("pid")
    if pid and proc_alive(pid) and _marker_ok(svc, proc_cmdline(pid)):
        return pid
    return None


def running(svc):
    return pid_of(svc) is not None


def started_at(svc):
    info = read_json(_pidfile(svc)) or {}
    return info.get("start")


def paper_jar(cfg, svc):
    override = (cfg.get("staging_override") or {}).get("jar") if svc == "staging" else None
    return override or os.path.join(JARS, cfg["paper"]["name"])


def command(cfg, svc):
    java = dl.java_bin(cfg["java"])
    if svc in PAPER:
        mem = cfg["geheugen_mb"][svc]
        flags = list(cfg["paper"].get("flags") or []) if svc == "survival" else SMALL_FLAGS
        if svc == "survival" and cfg.get("gedeeld"):
            flags = [f for f in flags if f != "-XX:+AlwaysPreTouch"]
        return [java, f"-Xms{mem}M", f"-Xmx{mem}M", *flags, f"-Dmc.service={svc}", *LOCALE,
                "-jar", paper_jar(cfg, svc), "--nogui"], server_dir(svc)
    if svc == "velocity":
        mem = cfg["geheugen_mb"]["velocity"]
        return [java, f"-Xms{mem}M", f"-Xmx{mem}M", *VELOCITY_FLAGS, "-Dmc.service=velocity", *LOCALE,
                "-jar", os.path.join(JARS, cfg["velocity"]["name"])], server_dir(svc)
    if svc == "tunnel":
        t = cfg["tunnel"]
        if t["modus"] == "bore":
            cmd = [dl.ensure_bore(), "local", str(cfg["poorten"]["velocity"]), "--local-host", "127.0.0.1",
                   "--to", t.get("bore_server") or "bore.pub"]
            if t.get("bore_poort"):
                cmd += ["--port", str(t["bore_poort"])]
            return cmd, server_dir(svc)
        if t["modus"] == "ngrok":
            # crl_noverify: de box blokkeert het ophalen van de CRL (alleen HTTPS naar buiten), anders verbindt ngrok nooit
            ngrok_cfg = os.path.join(STATE, "ngrok.yml")
            os.makedirs(STATE, exist_ok=True)
            with open(ngrok_cfg, "w") as fh:
                fh.write('version: "3"\nagent:\n  crl_noverify: true\n')
            return [dl.ensure_ngrok(), "tcp", f"127.0.0.1:{cfg['poorten']['velocity']}", "--config", ngrok_cfg,
                    "--log", "stdout", "--log-format", "json"], server_dir(svc)
        if t["modus"] == "playit":
            return [dl.ensure_playit(), *(t.get("playit_args") or ["--stdout"])], server_dir(svc)
    raise Fout(f"onbekende dienst of tunnelmodus: {svc}")


def start(cfg, svc):
    pid = pid_of(svc)
    if pid:
        return pid
    cmd, cwd = command(cfg, svc)
    os.makedirs(cwd, exist_ok=True)
    os.makedirs(STATE, exist_ok=True)
    f = fifo(svc)
    if not os.path.exists(f):
        os.mkfifo(f, 0o600)
    fd = os.open(f, os.O_RDWR)  # houdt de FIFO open: de dienst ziet nooit EOF op stdin
    out_path = outfile(svc)
    if os.path.exists(out_path) and os.path.getsize(out_path) > 20 * MB:
        os.replace(out_path, out_path + ".1")
    out = open(out_path, "ab")
    out.write(f"\n--- start {now_iso()}: {' '.join(cmd)}\n".encode())
    out.flush()
    env = dict(os.environ)
    if svc == "tunnel":
        env.setdefault("RUST_LOG", "info")
        if cfg["tunnel"]["modus"] == "ngrok":
            tok = secret_file("ngrok_token") or os.environ.get("NGROK_AUTHTOKEN")
            if not tok:
                raise Fout("geen ngrok-token: zet NGROK_AUTHTOKEN in de Secrets van de Bot en draai `mc tunnel token`")
            env["NGROK_AUTHTOKEN"] = tok
    try:
        p = subprocess.Popen(cmd, cwd=cwd, stdin=fd, stdout=out, stderr=subprocess.STDOUT,
                             start_new_session=True, close_fds=True, env=env)
    finally:
        os.close(fd)
        out.close()
    write_json(_pidfile(svc), {"pid": p.pid, "start": time.time()})
    return p


def stop(cfg, svc, timeout=90):
    pid = pid_of(svc)
    if not pid:
        return False
    try:
        if svc in PAPER:
            try:
                net.rcon(cfg["rcon"][svc], secrets()["rcon"][svc], "stop", timeout=5)
            except (Fout, OSError):
                net.console_send(fifo(svc), "stop")
        elif svc == "velocity":
            net.console_send(fifo(svc), "shutdown")
        else:
            os.killpg(pid, signal.SIGTERM)
    except (Fout, OSError):
        try:
            os.killpg(pid, signal.SIGTERM)
        except OSError:
            pass
    deadline = time.time() + timeout
    while time.time() < deadline and proc_alive(pid):
        time.sleep(0.5)
    if proc_alive(pid):
        say(f"  {svc} reageert niet op stop; SIGTERM")
        try:
            os.killpg(pid, signal.SIGTERM)
        except OSError:
            pass
        for _ in range(30):
            if not proc_alive(pid):
                break
            time.sleep(0.5)
    if proc_alive(pid):
        try:
            os.killpg(pid, signal.SIGKILL)
        except OSError:
            pass
        time.sleep(1)
    try:
        os.remove(_pidfile(svc))
    except FileNotFoundError:
        pass
    return True


def rcon(cfg, svc, command, timeout=10, raw=False):
    return net.rcon(cfg["rcon"][svc], secrets()["rcon"][svc], command, timeout=timeout, raw=raw)


def ready(cfg, svc):
    if not running(svc):
        return False
    try:
        if svc in PAPER:
            rcon(cfg, svc, "minecraft:list", timeout=3)
            return True
        if svc == "velocity":
            net.status("127.0.0.1", cfg["poorten"]["velocity"], timeout=3)
            return True
        if svc == "tunnel":
            return bool(tunnel_address(cfg))
    except (Fout, OSError, ValueError):
        return False
    return False


def wait_ready(cfg, svc, timeout=300):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if ready(cfg, svc):
            return True
        if not running(svc):
            time.sleep(2)
            if not running(svc):
                return False
        time.sleep(1)
    return False


def players(cfg, svc):
    """Namen van de spelers op een Paper-server (leeg als hij niet draait)."""
    try:
        out = rcon(cfg, svc, "minecraft:list", timeout=3)  # niet Essentials' /list (groepen, nicknames)
    except (Fout, OSError):
        return None
    if ":" not in out:
        return []
    names = out.split(":", 1)[1].strip()
    return [n.strip() for n in names.split(",") if n.strip()]


# ── wensen en pauzes (de supervisor respecteert ze) ────────────────────────────────────────────

def desired():
    d = read_json(os.path.join(STATE, "desired.json"), {})
    out = {"lobby": "up", "survival": "up", "velocity": "up", "tunnel": "up", "staging": "down"}
    out.update(d)
    return out


def set_desired(svc, value):
    path = os.path.join(STATE, "desired.json")
    d = read_json(path, {})
    d[svc] = value
    write_json(path, d)


def hold(svc, seconds=900):
    write_text(os.path.join(STATE, f"hold-{svc}"), str(time.time() + seconds))


def unhold(svc):
    try:
        os.remove(os.path.join(STATE, f"hold-{svc}"))
    except FileNotFoundError:
        pass


def held(svc):
    try:
        return float(read_text(os.path.join(STATE, f"hold-{svc}"), "0") or 0) > time.time()
    except ValueError:
        return False


# ── tunneladres ────────────────────────────────────────────────────────────────────────────────

def tunnel_address(cfg):
    t = cfg["tunnel"]
    if t["modus"] == "direct":
        return t.get("adres")
    if t["modus"] == "geen":
        return None
    if not running("tunnel"):
        return None
    st = read_json(os.path.join(STATE, "tunnel.json"), {})
    if st.get("pid") == pid_of("tunnel"):
        return st.get("adres")
    return None


def parse_tunnel_output(cfg):
    """Lees het adres uit de uitvoer van de tunnel (bore: 'listening at bore.pub:12345')."""
    import re
    pid = pid_of("tunnel")
    if not pid:
        return None
    text = "\n".join(_since_last_start(outfile("tunnel")))
    m = re.findall(r"listening at ([\w.\-]+:\d+)", text)
    if cfg["tunnel"]["modus"] == "ngrok":
        m = re.findall(r'"url":"tcp://([^"]+)"', text)
        for code, uitleg in (("ERR_NGROK_8013", "ngrok wil eerst een creditcard ter verificatie (niets wordt afgeschreven): dashboard.ngrok.com → Billing"),
                             ("ERR_NGROK_105", "het ngrok-token klopt niet"), ("ERR_NGROK_108", "ngrok staat al op een andere plek aan (gratis: één tegelijk)")):
            if code in text:
                write_json(os.path.join(STATE, "tunnel-fout.json"), {"code": code, "uitleg": uitleg, "tijd": now_iso()})
    if cfg["tunnel"]["modus"] == "playit":
        m = re.findall(r"([\w\-]+\.(?:gl\.)?(?:joinmc\.link|ply\.gg|playit\.gg)(?::\d+)?)", text)
    if not m:
        return None
    adres = m[-1]
    write_json(os.path.join(STATE, "tunnel.json"), {"pid": pid, "adres": adres, "sinds": now_iso()})
    return adres


def _since_last_start(path):
    lines = []
    for line in (read_text(path, "") or "").splitlines():
        if line.startswith("--- start "):
            lines = []
        else:
            lines.append(line)
    return lines


# ── serverconfiguratie ─────────────────────────────────────────────────────────────────────────

def configure_paper(cfg, svc):
    """server.properties, eula en (na de eerste start) paper-global.yml voor een Paper-dienst."""
    d = server_dir(svc)
    os.makedirs(os.path.join(d, "plugins"), exist_ok=True)
    s = secrets()
    if not cfg.get("eula"):
        raise Fout("de Minecraft EULA is niet geaccepteerd (mc install --eula-akkoord)")
    write_text(os.path.join(d, "eula.txt"), "# geaccepteerd door de eigenaar via mc install --eula-akkoord\neula=true\n")
    props = {
        "server-ip": "127.0.0.1", "server-port": cfg["poorten"][svc], "online-mode": "false",
        "enable-rcon": "true", "rcon.port": cfg["rcon"][svc], "rcon.password": s["rcon"][svc],
        "broadcast-rcon-to-ops": "false",
        "network-compression-threshold": "-1", "enable-query": "false", "prevent-proxy-connections": "false",
        "max-players": "20", "enable-status": "true", "management-server-enabled": "false",
        "motd": {"lobby": "Lobby", "survival": "Survival", "staging": "STAGING"}[svc],
    }
    # spelinstellingen (moeilijkheid, zichtafstand, lobbywereld) komen uit server/server.json
    cf.props_set(os.path.join(d, "server.properties"), props)
    pg = os.path.join(d, "config", "paper-global.yml")
    if os.path.exists(pg):
        patch_paper_global(cfg, svc)


def patch_paper_global(cfg, svc):
    pg = os.path.join(server_dir(svc), "config", "paper-global.yml")
    changed = cf.yaml_set(pg, ["proxies", "velocity", "enabled"], "true")
    changed |= cf.yaml_set(pg, ["proxies", "velocity", "online-mode"], "false" if cfg.get("offline_test") else "true")
    changed |= cf.yaml_set(pg, ["proxies", "velocity", "secret"], cf.yaml_quote(secrets()["forwarding"]))
    return changed


def velocity_toml_template():
    return read_text(os.path.join(REPO, "templates", "velocity.toml"))


def configure_velocity(cfg):
    d = server_dir("velocity")
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, "velocity.toml")
    if not os.path.exists(path):
        write_text(path, velocity_toml_template())
    write_text(os.path.join(d, "forwarding.secret"), secrets()["forwarding"], mode=0o600)
    icon = os.path.join(REPO, "templates", "server-icon.png")
    if os.path.exists(icon) and not os.path.exists(os.path.join(d, "server-icon.png")):
        shutil.copy2(icon, os.path.join(d, "server-icon.png"))
    online = "false" if cfg.get("offline_test") else "true"
    bind_host = "0.0.0.0" if cfg["tunnel"]["modus"] == "direct" else "127.0.0.1"
    p = cfg["poorten"]
    sets = [
        (None, "bind", cf.toml_str(f"{bind_host}:{p['velocity']}")),
        (None, "motd", cf.toml_str(cfg["motd"])),
        (None, "show-max-players", "20"),
        (None, "online-mode", online),
        (None, "force-key-authentication", online),
        (None, "player-info-forwarding-mode", cf.toml_str("modern")),
        (None, "forwarding-secret-file", cf.toml_str("forwarding.secret")),
        (None, "sample-players-in-ping", "false"),
        ("advanced", "login-ratelimit", "0"),  # via de tunnel komt iedereen van 127.0.0.1
        ("advanced", "failover-on-unexpected-server-disconnect", "true"),
        ("advanced", "haproxy-protocol", "false"),
        ("advanced", "log-player-connections", "true"),
        ("query", "enabled", "false"),
    ]
    changed = False
    for section, key, val in sets:
        changed |= cf.toml_set(path, section, key, val)
    changed |= cf.toml_replace_section(path, "servers", [
        f'survival = "127.0.0.1:{p["survival"]}"',
        f'lobby = "127.0.0.1:{p["lobby"]}"',
        f'staging = "127.0.0.1:{p["staging"]}"',
        'try = ["survival", "lobby"]',
    ])
    changed |= cf.toml_replace_section(path, "forced-hosts", [])
    return changed


def first_start(cfg, svc):
    """Eerste start zonder Velocity-forwarding: Paper maakt zijn configs en de wereld; daarna patchen."""
    say(f"  {svc}: eerste start (wereld en configs maken)…")
    start(cfg, svc)
    if not wait_ready(cfg, svc, timeout=600):
        raise Fout(f"{svc} start niet; laatste log:\n" + "\n".join(_tail_log(svc)))
    stop(cfg, svc)
    patch_paper_global(cfg, svc)


def _tail_log(svc, n=25):
    from .base import tail
    return tail(latest_log(svc), n) or tail(outfile(svc), n)


def copy_tree(src, dst, ignore=("logs", "session.lock", "cache")):
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns(*ignore), symlinks=True)
