"""Backups buiten de box: elke dag de nieuwste wereldbackup als release-bestand in een privé GitHub-repo.

Waarom releases: tot 2 GB per bestand, geen 100 MB-grens en geen Git-geschiedenis die groeit; oude dagen gaan
er gewoon weer af. Groter dan 1,9 GB: in delen binnen dezelfde release, met een manifest (sha256 van het geheel en
van elk deel); `mc restore github` voegt ze samen en controleert alles. Oude releases met één bestand werken ook. Alles via de GitHub-API op poort 443, met hetzelfde token als `mc git token`.
"""
import hashlib
import io
import json
import os
import re
import time
import urllib.error
import urllib.request
from urllib.parse import quote

from . import gitops
from .base import BACKUPS, GB, STATE, UA, Fout, file_hash, now_iso, read_json, say, write_json

API = "https://api.github.com"
DEFAULT_REPO = "BurggraafJ/grok-minecraft-backups"
LIMIT = int(1.95 * GB)                 # GitHub: elk release-bestand < 2 GiB
DEEL = int(1.9 * GB)                   # grootste deel; grotere backups gaan in delen in één release
MANIFEST = ".manifest.json"


def repo(cfg):
    return (cfg.get("offsite") or {}).get("repo") or DEFAULT_REPO


def keep(cfg):
    return int((cfg.get("offsite") or {}).get("dagen") or 7)


def _headers(extra=None):
    t = gitops.token()
    if not t:
        raise Fout("nog geen GitHub-token (mc git token); backups gaan nu alleen naar de box")
    h = {"Authorization": f"Bearer {t}", "Accept": "application/vnd.github+json", "User-Agent": UA,
         "X-GitHub-Api-Version": "2022-11-28"}
    h.update(extra or {})
    return h


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **kw):
        return None


def _call(method, url, body=None, headers=None, timeout=60):
    data = json.dumps(body).encode() if isinstance(body, (dict, list)) else body
    req = urllib.request.Request(url, data=data, method=method, headers=_headers(headers))
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        if e.code == 404 and method == "GET":
            return None
        detail = e.read().decode("utf-8", "replace")[:300]
        raise Fout(f"GitHub {method} {url.split('/repos/')[-1]}: HTTP {e.code} {detail}")
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        raise Fout(f"GitHub onbereikbaar: {e}")


def releases(cfg):
    out = []
    page = 1
    while True:
        batch = _call("GET", f"{API}/repos/{repo(cfg)}/releases?per_page=100&page={page}") or []
        out += [r for r in batch if r["tag_name"].startswith("dag-") and not r.get("draft")]
        if len(batch) < 100:
            break
        page += 1
    return sorted(out, key=lambda r: r["tag_name"], reverse=True)


def _newest_local():
    plain = sorted(n for n in os.listdir(BACKUPS) if re.fullmatch(r"survival-\d{8}-\d{6}\.tar\.gz", n)) \
        if os.path.isdir(BACKUPS) else []
    return os.path.join(BACKUPS, plain[-1]) if plain else None


def deel_grootte(cfg):
    """Grootste deel per release-bestand (GitHub: < 2 GiB per bestand). Standaard 1,9 GB; lager kan voor tests."""
    mb = (cfg.get("offsite") or {}).get("deel_mb")
    return int(mb) * 2**20 if mb else DEEL


def plan_delen(path, deel):
    """Eén keer door het bestand: sha256 van het geheel en van elk deel. Geeft het manifest (dict)."""
    name = os.path.basename(path)
    size = os.path.getsize(path)
    totaal = hashlib.sha256()
    delen = []
    with open(path, "rb") as f:
        n = 0
        while True:
            h = hashlib.sha256()
            got = 0
            while got < deel:
                chunk = f.read(min(1 << 20, deel - got))
                if not chunk:
                    break
                h.update(chunk); totaal.update(chunk); got += len(chunk)
            if not got and delen:
                break
            n += 1
            delen.append({"naam": name if size <= deel else f"{name}.deel{n:03d}", "grootte": got, "sha256": h.hexdigest()})
            if got < deel:
                break
    return {"versie": 1, "bestand": name, "grootte": size, "sha256": totaal.hexdigest(), "deel_grootte": deel,
            "delen": delen, "gemaakt": now_iso()}


class _Stuk:
    """Leesbaar venster op een deel van een bestand (voor het uploaden van één deel zonder het te kopiëren)."""

    def __init__(self, f, offset, length):
        self.f, self.left = f, length
        f.seek(offset)

    def read(self, n=-1):
        if self.left <= 0:
            return b""
        n = self.left if n is None or n < 0 else min(n, self.left)
        b = self.f.read(n)
        self.left -= len(b)
        return b


def _upload(cfg, rel, naam, body, size, ctype):
    r = repo(cfg)
    url = f"https://uploads.github.com/repos/{r}/releases/{rel['id']}/assets?name={quote(naam)}"
    return _call("POST", url, body=body, headers={"Content-Type": ctype, "Content-Length": str(size)}, timeout=3600)


def _assets(cfg, rel):
    return _call("GET", f"{API}/repos/{repo(cfg)}/releases/{rel['id']}/assets?per_page=100") or []


def push(cfg, path=None, tag=None, deel=None):
    """Zet de nieuwste (of de gegeven) backup als release 'dag-JJJJMMDD' neer; dezelfde dag wordt vervangen.

    Groter dan één deel (standaard 1,9 GB)? Dan gaat hij in delen `<naam>.deel001`, `.deel002`, … in dezelfde
    release, met `<naam>.manifest.json` (sha256 van het geheel en van elk deel). `fetch` voegt ze weer samen.
    Eerst als concept (draft) uploaden en controleren; pas dan gaat de oude release van die dag weg en wordt het
    concept gepubliceerd, zodat een mislukte upload nooit de vorige backup kost."""
    path = path or _newest_local()
    if not path:
        raise Fout("er is nog geen backup om weg te zetten (mc backup)")
    deel = int(deel or deel_grootte(cfg))
    if deel > LIMIT:
        raise Fout(f"deelgrootte {deel // 2**20} MB is groter dan wat GitHub per bestand toestaat")
    name = os.path.basename(path)
    if not tag:
        day = re.search(r"-(\d{8})-", name).group(1)
        tag = f"dag-{day}"
    day = tag.split("-", 1)[-1]
    titel = f"Wereld {day[:4]}-{day[4:6]}-{day[6:]}" if re.fullmatch(r"\d{8}", day) else tag
    man = plan_delen(path, deel)
    size = man["grootte"]
    r = repo(cfg)
    rel = _call("POST", f"{API}/repos/{r}/releases", {
        "tag_name": f"{tag}-concept-{int(time.time())}", "name": titel, "draft": True, "prerelease": False,
        "body": f"Backup `{name}` ({size // 2**20} MB, {len(man['delen'])} deel/delen, sha256 `{man['sha256']}`), "
                f"gemaakt door de Minecraft Bot. Terugzetten: `mc restore github:{day}` (voegt de delen samen en "
                f"controleert de hash)."})
    try:
        with open(path, "rb") as f:
            offset = 0
            for d in man["delen"]:
                for poging in range(3):
                    try:
                        _upload(cfg, rel, d["naam"], _Stuk(f, offset, d["grootte"]), d["grootte"], "application/octet-stream")
                        break
                    except Fout:
                        for a in _assets(cfg, rel):   # half geüpload deel weg, dan opnieuw
                            if a["name"] == d["naam"]:
                                _call("DELETE", f"{API}/repos/{r}/releases/assets/{a['id']}")
                        if poging == 2:
                            raise
                        time.sleep(10 * (poging + 1))
                offset += d["grootte"]
        mbytes = json.dumps(man, indent=1).encode()
        _upload(cfg, rel, name + MANIFEST, mbytes, len(mbytes), "application/json")
        op = {a["name"]: a for a in _assets(cfg, rel)}
        for d in man["delen"]:
            a = op.get(d["naam"])
            if not a or a.get("state") != "uploaded" or a.get("size") != d["grootte"]:
                raise Fout(f"deel {d['naam']} staat niet (volledig) op GitHub")
    except BaseException:
        try:
            _call("DELETE", f"{API}/repos/{r}/releases/{rel['id']}")
        except Fout:
            pass
        raise
    old = _call("GET", f"{API}/repos/{r}/releases/tags/{tag}")
    if old:
        _delete(cfg, old)
    _call("PATCH", f"{API}/repos/{r}/releases/{rel['id']}", {"draft": False, "tag_name": tag})
    removed = prune(cfg) if tag.startswith("dag-") else []
    if tag.startswith("dag-"):
        write_json(os.path.join(STATE, "offsite.json"), {"laatste": tag, "bestand": name, "tijd": now_iso(), "fout": None,
                                                         "delen": len(man["delen"]), "sha256": man["sha256"]})
    return tag, removed


def _delete(cfg, rel):
    r = repo(cfg)
    _call("DELETE", f"{API}/repos/{r}/releases/{rel['id']}")
    try:
        _call("DELETE", f"{API}/repos/{r}/git/refs/tags/{rel['tag_name']}")
    except Fout:
        pass


def prune(cfg):
    removed = []
    for rel in releases(cfg)[keep(cfg):]:
        _delete(cfg, rel)
        removed.append(rel["tag_name"])
    return removed


def _download_url(asset):
    # het API-adres stuurt door naar opslag die zelf ondertekend is: daar géén Authorization meesturen
    opener = urllib.request.build_opener(_NoRedirect)
    req = urllib.request.Request(asset["url"], headers=_headers({"Accept": "application/octet-stream"}))
    try:
        opener.open(req, timeout=60)
        raise Fout("GitHub gaf geen downloadadres")
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 303, 307, 308):
            raise Fout(f"download van GitHub mislukt: HTTP {e.code}")
        return e.headers["Location"]


def _stream(asset, out, hashes):
    """Download één asset achter aan `out`, werk alle hashes bij; geeft het aantal bytes."""
    n = 0
    with urllib.request.urlopen(urllib.request.Request(_download_url(asset), headers={"User-Agent": UA}), timeout=600) as r:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            out.write(chunk); n += len(chunk)
            for h in hashes:
                h.update(chunk)
    return n


def manifest_van(rel):
    """(manifest-asset, [overige assets]) van een release; manifest None bij een oude backup (één bestand)."""
    assets = rel.get("assets") or []
    man = next((a for a in assets if a["name"].endswith(MANIFEST)), None)
    return man, [a for a in assets if a is not man]


def kies(cfg, which="nieuwste", tag=None):
    if tag:
        rel = _call("GET", f"{API}/repos/{repo(cfg)}/releases/tags/{tag}")
        if not rel:
            raise Fout(f"geen release {tag} in {repo(cfg)}")
        return rel
    rels = releases(cfg)
    if not rels:
        raise Fout(f"geen backups op GitHub ({repo(cfg)})")
    rel = rels[0] if which in ("nieuwste", "laatste", "") else next((x for x in rels if x["tag_name"] == f"dag-{which}"), None)
    if not rel or not rel.get("assets"):
        raise Fout(f"geen backup voor {which} op GitHub; er zijn: {', '.join(x['tag_name'][4:] for x in rels)}")
    return rel


def fetch(cfg, which="nieuwste", tag=None, dest_dir=None, log=None):
    """Download een backup van GitHub naar data/backups (of dest_dir). which: 'nieuwste' of JJJJMMDD. Geeft het pad.

    Met manifest: alle delen achter elkaar in één bestand, elk deel en het geheel tegen de sha256 uit het
    manifest gecontroleerd; klopt er iets niet, dan geen bestand (Fout). Zonder manifest (oude backups): het ene
    bestand, gecontroleerd op grootte."""
    rel = kies(cfg, which, tag)
    dest_dir = dest_dir or BACKUPS
    os.makedirs(dest_dir, exist_ok=True)
    man_asset, rest = manifest_van(rel)
    if not man_asset:
        asset = rest[0]
        dest = os.path.join(dest_dir, asset["name"])
        if os.path.exists(dest) and os.path.getsize(dest) == asset["size"]:
            return dest
        tmp = dest + ".part"
        with open(tmp, "wb") as f:
            _stream(asset, f, [])
        if os.path.getsize(tmp) != asset["size"]:
            os.remove(tmp)
            raise Fout("download onvolledig")
        os.replace(tmp, dest)
        return dest
    buf = io.BytesIO()
    _stream(man_asset, buf, [])
    man = json.loads(buf.getvalue().decode())
    dest = os.path.join(dest_dir, os.path.basename(man["bestand"]))
    if os.path.exists(dest) and os.path.getsize(dest) == man["grootte"] and file_hash(dest, "sha256") == man["sha256"]:
        return dest
    by_name = {a["name"]: a for a in rest}
    tmp = dest + ".part"
    totaal = hashlib.sha256()
    with open(tmp, "wb") as f:
        for i, d in enumerate(man["delen"], 1):
            a = by_name.get(d["naam"])
            if not a:
                os.remove(tmp) if os.path.exists(tmp) else None
                raise Fout(f"deel {d['naam']} ontbreekt in release {rel['tag_name']}")
            for poging in range(3):
                start = f.tell()
                h = hashlib.sha256()
                t0 = totaal.copy()
                try:
                    n = _stream(a, f, [h, totaal])
                except (urllib.error.URLError, TimeoutError, OSError, Fout):
                    n = -1
                if n == d["grootte"] and h.hexdigest() == d["sha256"]:
                    break
                f.seek(start); f.truncate(); totaal = t0       # dit deel opnieuw
                if poging == 2:
                    f.close(); os.remove(tmp)
                    raise Fout(f"deel {d['naam']} komt niet goed binnen (grootte/sha256 klopt niet)")
            if log:
                log(f"  deel {i}/{len(man['delen'])} binnen en klopt ({d['grootte'] // 2**20} MB)")
    if os.path.getsize(tmp) != man["grootte"] or totaal.hexdigest() != man["sha256"]:
        os.remove(tmp)
        raise Fout("samengevoegde backup klopt niet met het manifest (sha256); niets teruggezet")
    os.replace(tmp, dest)
    return dest


def due(cfg):
    """Moet de dagelijkse backup naar GitHub? (token aanwezig, >20 uur geleden, en een nieuwere lokale backup)."""
    if not gitops.token():
        return False
    st = read_json(os.path.join(STATE, "offsite.json"), {})
    last_try = st.get("poging", 0)
    if time.time() - last_try < 20 * 3600:
        return False
    newest = _newest_local()
    return bool(newest) and os.path.basename(newest) != st.get("bestand")


def mark_attempt():
    path = os.path.join(STATE, "offsite.json")
    st = read_json(path, {})
    st["poging"] = time.time()
    write_json(path, st)


def lijst(cfg):
    for rel in releases(cfg):
        man, rest = manifest_van(rel)
        if man:
            naam = man["name"][:-len(MANIFEST)]
            tot = sum(a.get("size", 0) for a in rest)
            say(f"  {rel['tag_name'][4:]}  {naam}  {tot // 2**20} MB in {len(rest)} deel/delen (sha256 in manifest)")
        else:
            a = (rest or [{}])[0]
            say(f"  {rel['tag_name'][4:]}  {a.get('name', '?')}  {a.get('size', 0) // 2**20} MB")
