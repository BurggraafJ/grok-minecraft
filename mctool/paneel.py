"""Paneel-modus: de servers draaien bij een host met een Pterodactyl-paneel (Sparked Host / Apollo).

De box blijft de bouwplaats: hij bouwt plugins en configs uit server/server.json en zet ze via de client-API
(HTTPS, poort 443) op de servers van het paneel. Geen tunnel, geen RCON, geen watchdog op de box: het paneel houdt
de servers in de lucht (crash = herstart) en heeft een vast adres.

Beperkingen van de box (alleen 443 naar buiten): de signed upload-URL en de console-websocket van de node draaien op
poort 8080 en zijn dus onbereikbaar. Daarom:
  - uploaden gaat met POST /files/write (ruwe bytes, < ~95 MB per keer door Cloudflare); grote mappen gaan in
    losse tar.gz-delen die elk apart op het paneel worden uitgepakt;
  - een commando gaat met POST /command; de uitvoer lezen we terug uit logs/latest.log.

data/config.json → "modus": "paneel" en "paneel": {"api", "servers": {dienst: id}, "intern_host", "adres"}.
De API-sleutel staat in data/secrets/sparked_api_key (of env SPARKED_API_KEY) en komt nooit in een log of commit.
"""
import io
import json
import os
import re
import tarfile
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

from . import configfiles as cf
from . import core
from .base import REPO, STATE, UA, Fout, file_hash, now_iso, read_json, say, write_json

API = "https://control.sparkedhost.us/api/client"
PSTATE = os.path.join(STATE, "paneel.json")
MAX_DEEL = 80 * 1024 * 1024           # Cloudflare voor het paneel weigert verzoeken > 100 MB
BACKENDS = ("lobby", "survival", "kitpvp")
UITSLUIT = ("re:^(logs|cache|libraries|versions|crash-reports)$", "session.lock", ".console_history", ".paper-remapped", "re:^plugins/[^/]+\\.jar$", "re:^[^/]+\\.jar$")
# plugin-jars en server.jar zet deploy erop (met hash-administratie); jars ín pluginmappen (LuckPerms/libs,
# GravesX/lib) gaan wel mee: de splits hebben geen internet om ze zelf te downloaden


# ── configuratie ───────────────────────────────────────────────────────────────────────────────

def actief(cfg):
    return cfg.get("modus") == "paneel" and bool((cfg.get("paneel") or {}).get("servers"))


def pcfg(cfg):
    p = cfg.get("paneel") or {}
    if not p.get("servers"):
        raise Fout("geen paneel ingesteld (data/config.json → paneel.servers)")
    return p


def diensten(cfg):
    """De diensten die op het paneel een server hebben, in startvolgorde (backends eerst, velocity laatst)."""
    s = pcfg(cfg)["servers"]
    return [d for d in ("lobby", "survival", "kitpvp", "velocity") if d in s]


def backends(cfg):
    return [d for d in diensten(cfg) if d in BACKENDS]


def _key():
    k = core.secret_file("sparked_api_key") or os.environ.get("SPARKED_API_KEY", "").strip()
    if not k:
        raise Fout("geen paneel-API-sleutel: zet SPARKED_API_KEY in de Secrets van de Bot (paneel → API Credentials)")
    return k


def state():
    return read_json(PSTATE, {}) or {}


def save_state(st):
    write_json(PSTATE, st)


# ── API ────────────────────────────────────────────────────────────────────────────────────────

def req(cfg, method, path, body=None, data=None, timeout=120, tries=3, ctype=None):
    base = pcfg(cfg).get("api") or API
    headers = {"Authorization": "Bearer " + _key(), "Accept": "application/json", "User-Agent": UA}
    if data is None and body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    elif data is not None:
        headers["Content-Type"] = ctype or "application/octet-stream"
    last = None
    for attempt in range(tries):
        r = urllib.request.Request(base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(r, timeout=timeout) as resp:
                raw = resp.read()
                return resp.status, raw
        except urllib.error.HTTPError as e:
            raw = e.read()
            last = f"HTTP {e.code}: {raw[:300].decode('utf-8', 'replace')}"
            if e.code in (400, 401, 403, 404, 409, 413, 422):
                return e.code, raw
            if e.code == 429:
                time.sleep(10 * (attempt + 1))
                continue
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
            last = str(e)
        time.sleep(3 * (attempt + 1))
    raise Fout(f"paneel-API {method} {path.split('?')[0]} mislukt: {last}")


def api_json(cfg, method, path, body=None, ok=(200, 201, 202, 204), **kw):
    code, raw = req(cfg, method, path, body=body, **kw)
    if code not in ok:
        raise Fout(f"paneel-API {method} {path.split('?')[0]}: HTTP {code} {raw[:300].decode('utf-8', 'replace')}")
    return json.loads(raw) if raw.strip() else {}


def sid(cfg, svc):
    s = pcfg(cfg)["servers"]
    if svc not in s:
        raise Fout(f"{svc} heeft (nog) geen server op het paneel")
    return s[svc]


def q(path):
    return urllib.parse.quote(path if path.startswith("/") else "/" + path)


def info(cfg, svc):
    return api_json(cfg, "GET", f"/servers/{sid(cfg, svc)}")["attributes"]


def resources(cfg, svc):
    return api_json(cfg, "GET", f"/servers/{sid(cfg, svc)}/resources")["attributes"]


def toestand(cfg, svc):
    try:
        return resources(cfg, svc).get("current_state")
    except Fout:
        return None


def allocatie(cfg, svc):
    a = info(cfg, svc)
    for x in a["relationships"]["allocations"]["data"]:
        if x["attributes"]["is_default"]:
            return x["attributes"]["ip"], x["attributes"]["port"]
    raise Fout(f"{svc}: geen standaard-allocatie")


def power(cfg, svc, signaal):
    api_json(cfg, "POST", f"/servers/{sid(cfg, svc)}/power", {"signal": signaal})


def command(cfg, svc, line):
    code, raw = req(cfg, "POST", f"/servers/{sid(cfg, svc)}/command", body={"command": line})
    if code not in (200, 204):
        raise Fout(f"{svc}: commando niet verstuurd (HTTP {code}; staat de server aan?)")


def set_var(cfg, svc, key, value):
    api_json(cfg, "PUT", f"/servers/{sid(cfg, svc)}/startup/variable", {"key": key, "value": str(value)})


def set_image(cfg, svc, image):
    api_json(cfg, "PUT", f"/servers/{sid(cfg, svc)}/settings/docker-image", {"docker_image": image})


# ── bestanden ──────────────────────────────────────────────────────────────────────────────────

def ls(cfg, svc, directory="/"):
    code, raw = req(cfg, "GET", f"/servers/{sid(cfg, svc)}/files/list?directory={q(directory)}")
    if code == 404:
        return None
    if code != 200:
        raise Fout(f"{svc}: map {directory} lezen: HTTP {code}")
    return [f["attributes"] for f in json.loads(raw)["data"]]


def read(cfg, svc, path):
    code, raw = req(cfg, "GET", f"/servers/{sid(cfg, svc)}/files/contents?file={q(path)}", timeout=600)
    if code in (404, 422):  # 422: bestaat niet / is een map
        return None
    if code != 200:
        raise Fout(f"{svc}: {path} lezen: HTTP {code}")
    return raw


def write(cfg, svc, path, data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    if len(data) > MAX_DEEL + 10 * 1024 * 1024:
        raise Fout(f"{path}: te groot voor één upload ({len(data) // 1048576} MB); gebruik push_tree")
    code, raw = req(cfg, "POST", f"/servers/{sid(cfg, svc)}/files/write?file={q(path)}", data=data, timeout=900)
    if code not in (200, 204):
        raise Fout(f"{svc}: {path} schrijven: HTTP {code} {raw[:200].decode('utf-8', 'replace')}")


def mkdir(cfg, svc, root, name):
    req(cfg, "POST", f"/servers/{sid(cfg, svc)}/files/create-folder", body={"root": root, "name": name})


def delete(cfg, svc, root, files):
    if files:
        api_json(cfg, "POST", f"/servers/{sid(cfg, svc)}/files/delete", {"root": root, "files": list(files)})


def rename(cfg, svc, root, pairs):
    if pairs:
        api_json(cfg, "PUT", f"/servers/{sid(cfg, svc)}/files/rename",
                 {"root": root, "files": [{"from": a, "to": b} for a, b in pairs]})


def decompress(cfg, svc, root, file):
    api_json(cfg, "POST", f"/servers/{sid(cfg, svc)}/files/decompress", {"root": root, "file": file}, timeout=900)


def compress(cfg, svc, root, files):
    return api_json(cfg, "POST", f"/servers/{sid(cfg, svc)}/files/compress", {"root": root, "files": list(files)},
                    timeout=1800)["attributes"]["name"]


def _members(local_dir, exclude):
    """Alle bestanden onder local_dir (relatief), zonder wat op een patroon uit exclude past (naam of pad)."""
    import fnmatch

    def uit(name, rel):
        for pat in exclude:
            if pat.startswith("re:"):
                if re.search(pat[3:], rel):
                    return True
            elif fnmatch.fnmatch(name, pat) or fnmatch.fnmatch(rel, pat):
                return True
        return False
    out = []
    for root, dirs, files in os.walk(local_dir):
        rel_root = os.path.relpath(root, local_dir)
        dirs[:] = sorted(d for d in dirs if not uit(d, os.path.normpath(os.path.join(rel_root, d))))
        for f in sorted(files):
            rel = os.path.normpath(os.path.join(rel_root, f))
            if not uit(f, rel):
                out.append((rel, os.path.getsize(os.path.join(root, f))))
    return out


def _pack(local_dir, names, prefix=""):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz", compresslevel=6) as t:
        for n in names:
            t.add(os.path.join(local_dir, n), arcname=(prefix + "/" + n) if prefix else n, recursive=False)
    return buf.getvalue()


def push_tree(cfg, svc, local_dir, remote_root="/", exclude=UITSLUIT, label=None):
    """Zet een hele map op het paneel: in tar.gz-delen van < 80 MB, elk apart uitgepakt in remote_root."""
    files = _members(local_dir, set(exclude))
    groups, cur, cur_size = [], [], 0
    for rel, size in files:
        if cur and cur_size + size > 2 * MAX_DEEL:   # wereldbestanden halveren ongeveer bij gzip
            groups.append(cur)
            cur, cur_size = [], 0
        cur.append(rel)
        cur_size += size
    if cur:
        groups.append(cur)
    tmp = "_upload"
    mkdir(cfg, svc, "/", tmp)
    say(f"  {label or svc}: {len(files)} bestanden, {sum(s for _, s in files) // 1048576} MB")
    n, total = 0, 0
    todo = list(groups)
    while todo:
        g = todo.pop(0)
        data = _pack(local_dir, g, remote_root.strip("/"))
        if len(data) > MAX_DEEL and len(g) > 1:
            half = len(g) // 2
            todo[:0] = [g[:half], g[half:]]
            continue
        n += 1
        name = f"deel{n:03d}.tar.gz"
        write(cfg, svc, f"/{tmp}/{name}", data)
        decompress(cfg, svc, "/", f"{tmp}/{name}")
        delete(cfg, svc, f"/{tmp}", [name])
        total += len(data)
        say(f"  {label or svc}: deel {n} ({len(g)} bestanden, {len(data) // 1048576} MB) staat erop")
    delete(cfg, svc, "/", [tmp])
    return n, total


# ── bouwen en uitrollen (deploy) ───────────────────────────────────────────────────────────────

def _hash_bytes(b):
    import hashlib
    return hashlib.sha256(b).hexdigest()


def _put_if_changed(cfg, svc, st, remote, data, force=False):
    """Upload als de inhoud anders is dan wat we het laatst uploadden (of als hij op het paneel ontbreekt)."""
    h = _hash_bytes(data)
    known = st.setdefault("bestanden", {}).setdefault(svc, {})
    if not force and known.get(remote) == h:
        return False
    write(cfg, svc, remote, data)
    known[remote] = h
    return True


def _patch_remote(cfg, svc, path, patcher):
    """Lees een bestand van het paneel, patch het lokaal (patcher(tmp_pad) → changed), schrijf terug als anders."""
    old = read(cfg, svc, path)
    if old is None:
        return None
    with tempfile.NamedTemporaryFile("wb", delete=False, suffix=os.path.splitext(path)[1]) as f:
        f.write(old)
        tmp = f.name
    try:
        patcher(tmp)
        new = open(tmp, "rb").read()
    finally:
        os.remove(tmp)
    if new != old:
        write(cfg, svc, path, new)
        return True
    return False


def paperclip_bestanden(jar, java_feature):
    """cache/, libraries/ en versions/ die Paperclip bij de eerste start downloadt, hier op de box gemaakt."""
    import subprocess
    from . import downloads as dl
    d = os.path.join(STATE, "paperclip", os.path.basename(jar)[:-4])
    if not os.path.isdir(os.path.join(d, "libraries")):
        os.makedirs(d, exist_ok=True)
        r = subprocess.run([dl.java_bin(java_feature), "-Dpaperclip.patchonly=true", "-jar", jar], cwd=d,
                           capture_output=True, text=True, timeout=900)
        if r.returncode != 0 or not os.path.isdir(os.path.join(d, "libraries")):
            raise Fout("Paperclip-bestanden maken mislukt: " + (r.stdout + r.stderr)[-400:])
    return d


def _ys(path, keys, literal, plain):
    """yaml_set, maar alleen als de waarde echt anders is (Paper schrijft quotes soms anders terug)."""
    cur = cf.yaml_get(path, keys)
    if cur is not None and str(cur).strip().strip("'\"") == str(plain):
        return False
    return cf.yaml_set(path, keys, literal)


def backend_props(cfg, spec, svc, port):
    from . import apply as applymod
    props = {"max-players": "20", "enable-status": "true", "network-compression-threshold": "-1",
             "motd": {"lobby": "Lobby", "survival": "Survival", "kitpvp": "KitPvP"}[svc]}
    props.update(applymod.settings_for(spec, svc))
    props.update({  # vast in paneel-modus: alleen via Velocity (modern forwarding), geen RCON/query
        "server-ip": "0.0.0.0", "server-port": port, "online-mode": "false",
        "enable-rcon": "false", "rcon.password": "", "broadcast-rcon-to-ops": "false",
        "enable-query": "false", "prevent-proxy-connections": "false", "management-server-enabled": "false",
        # splits hebben geen internet (geen DNS): Mojang-sleutels ophalen lukt niet; Velocity controleert ze al
        "enforce-secure-profile": "false",
    })
    return props


def velocity_toml(cfg, current):
    """velocity.toml voor het paneel: bind op de allocatie, servers via het interne adres, alleen wat bestaat."""
    p = pcfg(cfg)
    host = p.get("intern_host") or "172.18.0.1"
    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".toml") as f:
        f.write(current or core.velocity_toml_template())
        path = f.name
    try:
        _ip, port = allocatie(cfg, "velocity")
        sets = [
            (None, "bind", cf.toml_str(f"0.0.0.0:{port}")),
            (None, "motd", cf.toml_str(cfg["motd"])),
            (None, "show-max-players", "20"),
            (None, "online-mode", "true"),
            (None, "force-key-authentication", "true"),
            (None, "player-info-forwarding-mode", cf.toml_str("modern")),
            (None, "forwarding-secret-file", cf.toml_str("forwarding.secret")),
            (None, "sample-players-in-ping", "false"),
            ("advanced", "login-ratelimit", "3000"),          # echte IP's nu (geen tunnel): Velocity-standaard
            ("advanced", "failover-on-unexpected-server-disconnect", "true"),
            ("advanced", "haproxy-protocol", "false"),
            ("advanced", "log-player-connections", "true"),
            ("query", "enabled", "false"),
        ]
        for section, key, val in sets:
            cf.toml_set(path, section, key, val)
        servers = []
        for svc in backends(cfg):
            servers.append(f'{svc} = "{backend_adres(cfg, svc)}"')
        servers.append('try = ["lobby", "survival"]')
        cf.toml_replace_section(path, "servers", servers)
        cf.toml_replace_section(path, "forced-hosts", [])
        return open(path, encoding="utf-8").read()
    finally:
        os.remove(path)


def backend_adres(cfg, svc):
    """Adres waarop Velocity een backend bereikt: 127.0.0.1-allocatie (split op dezelfde node) = via de
    Docker-gateway (intern_host, standaard 172.18.0.1); een eigen server elders = zijn publieke ip:poort
    (veilig doordat Paper alleen Velocity-verbindingen met het geheim toelaat)."""
    vast = (pcfg(cfg).get("adressen") or {}).get(svc)
    if vast:
        return vast
    ip, port = allocatie(cfg, svc)
    host = (pcfg(cfg).get("intern_host") or "172.18.0.1") if ip in ("127.0.0.1", "0.0.0.0") else ip
    return f"{host}:{port}"


def intern_port(cfg, svc):
    cache = pcfg(cfg).setdefault("poorten", {})
    if svc not in cache:
        cache[svc] = allocatie(cfg, svc)[1]
    return cache[svc]


def deploy(cfg, spec=None, herstart=True, alleen=None, log=say):
    """Zet het paneel gelijk aan server/server.json. Geeft de diensten die iets nieuws kregen."""
    from . import apply as applymod
    from . import downloads as dl
    from . import spec as specmod
    spec = spec or specmod.load()
    st = state()
    dl.ensure_java(spec["java"])
    paper = dl.fill_build("paper", spec["minecraft"], spec["paper_build"])
    velocity = dl.fill_build("velocity", spec["velocity"]["versie"], spec["velocity"]["build"])
    jars = {"paper": dl.ensure_jar(paper), "velocity": dl.ensure_jar(velocity)}
    arts = applymod.fetch_artifacts(spec, spec["java"])
    geheim = core.secrets()["forwarding"]
    gewijzigd = []
    for svc in diensten(cfg):
        if alleen and svc not in alleen:
            continue
        changed = []
        jar = jars["velocity" if svc == "velocity" else "paper"]
        if _put_if_changed(cfg, svc, st, "/server.jar", open(jar, "rb").read()):
            changed.append(os.path.basename(jar))
            if svc != "velocity":   # splits hebben geen internet: Paperclip kan Mojang-jar en libraries niet halen
                d = paperclip_bestanden(jar, spec["java"])
                for sub in ("cache", "libraries", "versions"):
                    if os.path.isdir(os.path.join(d, sub)):
                        push_tree(cfg, svc, os.path.join(d, sub), f"/{sub}", exclude=(), label=f"{svc} {sub}")
        # plugins: precies de gewenste jars; losse jars gaan naar /plugins-uit (zoals op de box)
        target = svc
        want = {os.path.basename(a["pad"]): a for a in arts.values() if target in a["op"]}
        remote = {f["name"]: f for f in (ls(cfg, svc, "/plugins") or []) if f["is_file"] and f["name"].endswith(".jar")}
        known = st.setdefault("bestanden", {}).setdefault(svc, {})
        weg = [n for n in remote if n not in want]
        if weg:
            mkdir(cfg, svc, "/", "plugins-uit")
            delete(cfg, svc, "/plugins-uit", [n for n in weg])  # oude kopie met dezelfde naam eerst weg
            rename(cfg, svc, "/", [(f"plugins/{n}", f"plugins-uit/{n}") for n in weg])
            for n in weg:
                known.pop(f"/plugins/{n}", None)
            changed.append("weg: " + ", ".join(weg))
        for name, a in want.items():
            data = open(a["pad"], "rb").read()
            if _put_if_changed(cfg, svc, st, f"/plugins/{name}", data, force=name not in remote):
                changed.append(f"plugin {name}")
        if svc == "velocity":
            cur = read(cfg, svc, "/velocity.toml")
            if _put_if_changed(cfg, svc, st, "/velocity.toml", velocity_toml(cfg, cur.decode() if cur else None).encode()):
                changed.append("velocity.toml")
            if _put_if_changed(cfg, svc, st, "/forwarding.secret", geheim.encode()):
                changed.append("forwarding-geheim")
        else:
            port = intern_port(cfg, svc)
            props = backend_props(cfg, spec, svc, port)
            r = _patch_remote(cfg, svc, "/server.properties", lambda p: cf.props_set(p, props))
            if r is None:
                write(cfg, svc, "/server.properties", "\n".join(f"{k}={v}" for k, v in props.items()) + "\n")
                r = True
            if r:
                changed.append("server.properties")
            write(cfg, svc, "/eula.txt", "# geaccepteerd door de eigenaar (Jelle) via mc install --eula-akkoord\neula=true\n")

            def pg(p):
                _ys(p, ["proxies", "velocity", "enabled"], "true", "true")
                _ys(p, ["proxies", "velocity", "online-mode"], "true", "true")
                _ys(p, ["proxies", "velocity", "secret"], cf.yaml_quote(geheim), geheim)
            if _patch_remote(cfg, svc, "/config/paper-global.yml", pg):
                changed.append("paper-global.yml")
            for f, keys in ((spec.get("patches") or {}).get(svc) or {}).items():
                def patch(p, f=f, keys=keys):
                    if f.endswith(".json"):
                        cf.json_set(p, keys)
                    else:
                        for k, val in keys.items():
                            _ys(p, k.split("."), applymod._yaml_literal(val), applymod._yaml_plain(val))
                try:
                    r = _patch_remote(cfg, svc, "/" + f, patch)
                except Fout as e:
                    log(f"  {svc}: patch {f}: {e}")
                    r = False
                if r:
                    changed.append(f"patch {f}")
            if svc == "survival":
                for name, h in applymod.datapack_hashes(spec).items():
                    if (st.get("datapacks") or {}).get(name) != h:
                        src = os.path.join(REPO, "server", "datapacks", name)
                        lvl = props.get("level-name") or "world"
                        push_tree(cfg, svc, src, f"/{lvl}/datapacks/{name}", exclude=(), label=f"datapack {name}")
                        st.setdefault("datapacks", {})[name] = h
                        changed.append(f"datapack {name}")
        save_state(st)
        if changed:
            gewijzigd.append(svc)
            log(f"  {svc}: " + ", ".join(changed))
    st["spec_hash"] = applymod.spec_fingerprint()
    st["spec_commit"] = applymod._git_head()
    st["tijd"] = now_iso()
    save_state(st)
    if herstart:
        for svc in gewijzigd:
            herstart_dienst(cfg, svc, log=log)
        time.sleep(15)
        for svc in gewijzigd:
            if not wacht_op(cfg, svc, timeout=600):
                log(f"  {svc}: na 10 min nog niet klaar; kijk `mc paneel logs {svc}`")
    runtime(cfg, spec, log=log)
    return gewijzigd


def herstart_dienst(cfg, svc, log=say):
    t = toestand(cfg, svc)
    if t in ("running", "starting"):
        if svc in ("survival", "kitpvp") and toestand(cfg, "velocity") == "running":
            try:  # spelers wachten in de lobby en gaan vanzelf terug (plugin netwerk)
                command(cfg, "velocity", f"netwerk wacht {svc}")
                time.sleep(2)
            except Fout:
                pass
        power(cfg, svc, "restart")
        log(f"  {svc}: herstart")
    else:
        power(cfg, svc, "start")
        log(f"  {svc}: gestart")


def wacht_op(cfg, svc, timeout=300):
    """Wacht tot de server 'running' is en de (nieuwe) log 'Done (' zegt."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if toestand(cfg, svc) == "running":
            log = (read(cfg, svc, "/logs/latest.log") or b"").decode("utf-8", "replace")
            if re.search(r"Done \(\d", log) and "Stopping server" not in log:
                return True
        time.sleep(5)
    return False


# ── live zonder herstart: gamerules, rechten, groepen ────────────────────────────────────────────

def runtime(cfg, spec, log=say):
    """Gamerules (behalve die /admin overschreef) en LuckPerms-rechten/groepen via de console van het paneel."""
    from . import apply as applymod
    st = state()
    sent = []
    for svc in backends(cfg):
        rules = (spec.get("gamerules") or {}).get(svc) or {}
        if not rules or toestand(cfg, svc) != "running":
            continue
        raw = (read(cfg, svc, "/plugins/Extras/schakelaars.yml") or b"").decode("utf-8", "replace")
        door_admin = _admin_gamerules(raw)
        want = {k: str(v).lower() for k, v in rules.items() if k not in door_admin}
        if (st.get("gamerules") or {}).get(svc) != want:
            for k, v in want.items():
                command(cfg, svc, f"gamerule {k} {v}")
            st.setdefault("gamerules", {})[svc] = want
            sent.append(f"{svc}: {len(want)} gamerules")
    if "survival" in backends(cfg) and toestand(cfg, "survival") == "running":
        applied = st.get("rechten")
        if applied is None:
            applied = (applymod.deployed().get("rechten") or {})
        want = spec.get("rechten") or {}
        n = 0
        for group in sorted(set(want) | set(applied)):
            have_p, want_p = set(applied.get(group) or []), set(want.get(group) or [])
            if group != "default" and want_p and not have_p:
                command(cfg, "survival", f"lp creategroup {group}")
            for perm in sorted(have_p - want_p):
                command(cfg, "survival", f"lp group {group} permission unset {perm.lstrip('-')}")
                n += 1
            for perm in sorted(want_p - have_p):
                node, val = (perm[1:], "false") if perm.startswith("-") else (perm, "true")
                command(cfg, "survival", f"lp group {group} permission set {node} {val}")
                n += 1
        st["rechten"] = want
        have_g = st.get("groepen")
        if have_g is None:
            have_g = applymod.deployed().get("groepen") or {}
        want_g = applymod.groepen_gewenst(spec)
        for group in sorted(set(want_g) | set(have_g)):
            g = (spec.get("groepen") or {}).get(group) or {}
            h, w = have_g.get(group) or {}, want_g.get(group) or {}
            if group in want_g and group not in have_g and group != "default":
                command(cfg, "survival", f"lp creategroup {group}")
                if g.get("gewicht") is not None:
                    command(cfg, "survival", f"lp group {group} setweight {int(g['gewicht'])}")
            for naam in sorted(set(w) - set(h)):
                command(cfg, "survival", f"lp user {w[naam]} parent add {group}")
                n += 1
            for naam in sorted(set(h) - set(w)):
                command(cfg, "survival", f"lp user {h[naam]} parent remove {group}")
                n += 1
        st["groepen"] = want_g
        if n:
            sent.append(f"rechten/groepen: {n} wijzigingen")
    save_state(st)
    if sent:
        log("  live gezet: " + "; ".join(sent))
    return sent


def _admin_gamerules(text):
    uit, binnen = {}, False
    for regel in text.splitlines():
        if not regel.strip() or regel.lstrip().startswith("#"):
            continue
        if not regel.startswith(" "):
            binnen = regel.split(":")[0].strip() == "gamerules"
            continue
        if binnen and ":" in regel:
            k, v = regel.split(":", 1)
            uit[k.strip()] = v.strip()
    return uit


# ── bediening: status, cmd, logs, backups ──────────────────────────────────────────────────────

def cmd_met_uitvoer(cfg, svc, line, wacht=2.0):
    before = len(read(cfg, svc, "/logs/latest.log") or b"")
    command(cfg, svc, line)
    time.sleep(wacht)
    log = read(cfg, svc, "/logs/latest.log") or b""
    return log[before:].decode("utf-8", "replace") if len(log) >= before else log.decode("utf-8", "replace")


def logs(cfg, svc, n=60):
    log = (read(cfg, svc, "/logs/latest.log") or b"").decode("utf-8", "replace")
    return log.splitlines()[-n:]


def status(cfg):
    p = pcfg(cfg)
    st = {"tijd": now_iso(), "modus": "paneel", "adres": p.get("adres"), "diensten": {}}
    for svc in diensten(cfg):
        try:
            r = resources(cfg, svc)
            res = r.get("resources") or {}
            st["diensten"][svc] = {"toestand": r.get("current_state"), "ram_mb": round(res.get("memory_bytes", 0) / 1048576),
                                   "cpu": res.get("cpu_absolute"), "schijf_mb": round(res.get("disk_bytes", 0) / 1048576),
                                   "uptime_min": round(res.get("uptime", 0) / 60000)}
        except Fout as e:
            st["diensten"][svc] = {"toestand": None, "fout": str(e)[:200]}
    if p.get("adres"):
        from . import ops
        ok, why = ops.extern_check(p["adres"])
        st["extern"] = {"ok": ok, "uitleg": why}
    return st


def print_status(st):
    say(f"Adres: {st['adres'] or '—'}   (paneel)")
    for svc, i in st["diensten"].items():
        t = i.get("toestand")
        mark = "✓" if t == "running" else ("…" if t == "starting" else "✗")
        say(f"  {mark} {svc:<9} {t or '?'} · {i.get('ram_mb', '?')} MB RAM · cpu {i.get('cpu', '?')}% · "
            f"{i.get('uptime_min', '?')} min op")
    if st.get("extern"):
        say(f"Van buiten: {'bereikbaar' if st['extern']['ok'] else 'NIET bereikbaar'} ({st['extern']['uitleg']})")


def backups(cfg, svc="survival"):
    return [b["attributes"] for b in api_json(cfg, "GET", f"/servers/{sid(cfg, svc)}/backups")["data"]]


def backup(cfg, svc="survival", naam=None):
    """Paneel-backup. Limiet vol: de oudste niet-vergrendelde gaat eerst weg."""
    a = info(cfg, svc)
    limiet = (a.get("feature_limits") or {}).get("backups") or 0
    if not limiet:
        raise Fout(f"{svc}: het paneel staat geen backups toe")
    bs = [b for b in backups(cfg, svc) if b.get("completed_at")]
    if len(backups(cfg, svc)) >= limiet:
        oud = sorted((b for b in bs if not b.get("is_locked")), key=lambda b: b["created_at"])
        if not oud:
            raise Fout(f"{svc}: backuplimiet ({limiet}) vol en alles vergrendeld")
        api_json(cfg, "DELETE", f"/servers/{sid(cfg, svc)}/backups/{oud[0]['uuid']}")
    b = api_json(cfg, "POST", f"/servers/{sid(cfg, svc)}/backups",
                 {"name": naam or f"{svc}-{time.strftime('%Y%m%d-%H%M')}", "ignored": "logs\ncache\nlibraries\nversions\noud-paneel"})
    return b["attributes"]


def _niet_herhalen(lines, uren=6):
    """Dezelfde MELD niet elk uur opnieuw: pas weer na `uren`."""
    import hashlib
    d = os.path.join(STATE, "paneel-meldingen"); os.makedirs(d, exist_ok=True)
    uit = []
    for line in lines:
        f = os.path.join(d, hashlib.sha1(line.encode()).hexdigest()[:16])
        if os.path.exists(f) and time.time() - os.path.getmtime(f) < uren * 3600:
            continue
        open(f, "w").close(); uit.append(line)
    return uit


def ensure(cfg, stil=False):
    """Uurcontrole in paneel-modus: servers aan, GitHub-wijzigingen uitrollen. Lege uitvoer = alles goed."""
    from . import apply as applymod
    from . import gitops
    lines = []
    for svc in diensten(cfg):
        t = toestand(cfg, svc)
        if t == "offline":
            try:
                power(cfg, svc, "start")
                lines.append(f"MELD {svc} stond uit op het paneel en is gestart")
            except Fout as e:
                lines.append(f"MELD {svc} staat uit en starten lukt niet: {e}")
        elif t is None:
            lines.append(f"MELD paneel-API geeft geen stand voor {svc} (sleutel ingetrokken of storing?)")
    # ziet de proxy zijn backends? (alleen als alles draait; één melding per probleem per 6 uur)
    if all(toestand(cfg, x) == "running" for x in diensten(cfg)):
        try:
            uit = cmd_met_uitvoer(cfg, "velocity", "netwerk ping", wacht=7)
            for svc in [x for x in diensten(cfg) if x != "velocity"]:
                if re.search(rf"netwerk ping {svc} \([^)]*\): ONBEREIKBAAR", uit):
                    lines.append(f"MELD velocity kan {svc} niet bereiken (draait wel op het paneel)")
        except Fout:
            pass
    # GitHub-backup van survival (off-site, naast de 3 paneel-backups): 1x per dag, 's nachts (box-tijd 04-07)
    st = state()
    if time.localtime().tm_hour in (4, 5, 6, 7) and time.time() - st.get("github_backup", 0) > 20 * 3600 \
            and "survival" in diensten(cfg):
        try:
            github_backup(cfg, "survival", log=lambda s: None)
        except Exception as e:   # noqa: BLE001 - melden, niet crashen
            lines.append(f"MELD GitHub-backup van survival mislukt: {str(e)[:200]}")
    lines = _niet_herhalen(lines)
    if gitops.is_repo():
        try:
            gitops.pull()
        except Fout as e:
            lines.append(f"MELD git pull mislukt: {e}")
        dirty = gitops.git("status", "--porcelain", "--", "server", "plugins", check=False)
        if not dirty and state().get("spec_hash") != applymod.spec_fingerprint():
            try:
                deploy(cfg, log=lambda s: None)
                lines.append("MELD wijziging uit GitHub staat op het paneel")
            except Fout as e:
                lines.append(f"MELD wijziging uit GitHub niet toegepast op het paneel: {e}")
    for line in lines:
        say(line)
    if not stil and not lines:
        print_status(status(cfg))
    return 0


# ── schema's (backups op het paneel) ───────────────────────────────────────────────────────────

SCHEMA_NAAM = "mc: dagelijkse backup"
SCHEMA_TIJD = {"survival": ("0", "3"), "lobby": ("20", "3"), "kitpvp": ("40", "3"), "velocity": ("50", "3")}  # UTC


def schemas_instellen(cfg, log=say):
    """Eén schema per server: elke nacht (UTC) save-all + backup. Bij de limiet (3) gooit het paneel de oudste
    automatische backup zelf weg, dus er staan altijd de laatste 3 nachten."""
    for svc in diensten(cfg):
        base = f"/servers/{sid(cfg, svc)}/schedules"
        bestaand = [s["attributes"] for s in api_json(cfg, "GET", base)["data"]]
        if any(s["name"] == SCHEMA_NAAM for s in bestaand):
            log(f"  {svc}: schema staat er al")
            continue
        minute, hour = SCHEMA_TIJD.get(svc, ("0", "4"))
        dow = "0" if svc == "velocity" else "*"   # velocity: alleen configs, wekelijks is genoeg
        s = api_json(cfg, "POST", base, {"name": SCHEMA_NAAM, "minute": minute, "hour": hour, "day_of_month": "*",
                                         "month": "*", "day_of_week": dow, "is_active": True,
                                         "only_when_online": False})["attributes"]
        offset = 0
        if svc != "velocity":
            api_json(cfg, "POST", f"{base}/{s['id']}/tasks", {"action": "command", "payload": "save-all flush",
                                                              "time_offset": 0, "continue_on_failure": True})
            offset = 15
        api_json(cfg, "POST", f"{base}/{s['id']}/tasks", {"action": "backup",
                                                          "payload": "logs\ncache\nlibraries\nversions\noud-paneel",
                                                          "time_offset": offset, "continue_on_failure": False})
        log(f"  {svc}: schema gemaakt ({hour}:{minute.zfill(2)} UTC{', zondag' if dow == '0' else ''})")


# ── spiegel: paneel → box (voor de GitHub-backup) ──────────────────────────────────────────────

SPIEGEL_NIET = ("logs", "cache", "libraries", "versions", "oud-paneel", ".trash", ".apollo", "plugins-uit", "_upload",
                "crash-reports", ".paper-remapped")


def _remote_files(cfg, svc, d="/", grens=100 * 1024 * 1024):
    """Wat er ingepakt moet worden: (pad, grootte) van bestanden én van hele mappen < grens (het paneel geeft
    mapgroottes), alleen grotere mappen worden verder uitgesplitst. Zo blijft het bij een handvol API-aanroepen.
    Zonder logs/caches/libraries en zonder server.jar en plugin-jars (die komen uit de repo)."""
    out = []
    for f in ls(cfg, svc, d) or []:
        rel = (d.rstrip("/") + "/" + f["name"]).lstrip("/")
        if (d == "/" and (f["name"] in SPIEGEL_NIET or f["name"].startswith(("archive-", "spiegel-")))) \
                or f["name"] == "session.lock":
            continue
        if f["is_file"]:
            if not (f["name"].endswith(".jar") and rel.count("/") <= 1):
                out.append((rel, f["size"]))
        elif f["size"] > grens:
            out += _remote_files(cfg, svc, "/" + rel, grens)
        else:
            out.append((rel, f["size"]))
    return out


def spiegel(cfg, svc, local_dir=None, log=say):
    """Haal de hele server (zonder logs/caches/jars) naar de box: op het paneel inpakken in delen van < 90 MB
    (bestanden > ~100 MB lezen weigert de API), lezen, lokaal uitpakken. Opslaan staat alleen tijdens het inpakken
    uit (kort), dus de kopie is consistent."""
    import shutil
    local_dir = local_dir or core.server_dir(svc)
    files = _remote_files(cfg, svc)
    groups, cur, size = [], [], 0
    for rel, sz in sorted(files):
        if cur and size + sz > 110 * 1024 * 1024:   # wereldbestanden: gzip ≈ 70%
            groups.append(cur)
            cur, size = [], 0
        cur.append(rel)
        size += sz
    if cur:
        groups.append(cur)
    draait = toestand(cfg, svc) == "running"
    archieven = []
    if draait:
        command(cfg, svc, "save-off")
        command(cfg, svc, "save-all flush")
        time.sleep(10)
    try:
        for i, g in enumerate(groups, 1):
            voor = {f["name"] for f in ls(cfg, svc, "/") if f["name"].startswith("archive-")}
            req(cfg, "POST", f"/servers/{sid(cfg, svc)}/files/compress", body={"root": "/", "files": g}, timeout=900)
            naam = None
            for _ in range(120):
                nieuw = [f for f in ls(cfg, svc, "/") if f["name"].startswith("archive-") and f["name"] not in voor]
                if nieuw:
                    naam = nieuw[0]["name"]
                    break
                time.sleep(2)
            if not naam:
                raise Fout(f"{svc}: inpakken van deel {i} gaf geen archief")
            vorige = -1
            for _ in range(150):   # inpakken loopt op de achtergrond door: wacht tot de grootte stilstaat
                time.sleep(3)
                nu = next((f["size"] for f in ls(cfg, svc, "/") if f["name"] == naam), -1)
                if nu == vorige and nu > 0:
                    break
                vorige = nu
            doel = f"spiegel-{i:03d}.tar.gz"
            rename(cfg, svc, "/", [(naam, doel)])
            archieven.append(doel)
            log(f"  {svc}: deel {i}/{len(groups)} ingepakt")
    finally:
        if draait:
            command(cfg, svc, "save-on")
    tmp = local_dir + ".spiegel"
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp)
    tot = 0
    try:
        for a in archieven:
            data = read(cfg, svc, "/" + a)
            if data is None:
                raise Fout(f"{svc}: {a} lezen mislukt (te groot?)")
            tot += len(data)
            with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as t:
                t.extractall(tmp, filter="data")
    finally:
        delete(cfg, svc, "/", archieven)
    # de nieuwe kopie vervangt alles behalve wat het paneel niet heeft (jars, libraries: uit de repo / Paperclip)
    for rel, _ in files:
        src, dst = os.path.join(tmp, rel), os.path.join(local_dir, rel)
        if os.path.exists(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if os.path.isdir(dst) and not os.path.islink(dst):
                shutil.rmtree(dst)
            os.replace(src, dst)
    shutil.rmtree(tmp, ignore_errors=True)
    log(f"  {svc}: {len(files)} bestanden/mappen in {len(archieven)} delen ({tot // 1048576} MB) naar {local_dir}")
    return len(files)


def github_backup(cfg, svc="survival", log=say):
    """Spiegel de server naar de box, maak er een backup-tar van en zet die als release in de backup-repo."""
    from . import offsite
    from . import ops
    spiegel(cfg, svc, log=log)
    path = ops.backup(cfg, "paneel", svc=svc)
    tag, removed = offsite.push(cfg, path)
    st = state(); st["github_backup"] = time.time(); save_state(st)
    log(f"  op GitHub: {tag}" + (f" (weg: {', '.join(removed)})" if removed else ""))
    return path, tag
