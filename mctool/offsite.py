"""Backups buiten de box: elke dag de nieuwste wereldbackup als release-bestand in een privé GitHub-repo.

Waarom releases: tot 2 GB per bestand, geen 100 MB-grens en geen Git-geschiedenis die groeit; oude dagen gaan
er gewoon weer af. Alles via de GitHub-API op poort 443, met hetzelfde token als `mc git token`.
"""
import json
import os
import re
import time
import urllib.error
import urllib.request
from urllib.parse import quote

from . import gitops
from .base import BACKUPS, GB, STATE, UA, Fout, now_iso, read_json, say, write_json

API = "https://api.github.com"
DEFAULT_REPO = "BurggraafJ/grok-minecraft-backups"
LIMIT = int(1.95 * GB)


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
        out += [r for r in batch if r["tag_name"].startswith("dag-")]
        if len(batch) < 100:
            break
        page += 1
    return sorted(out, key=lambda r: r["tag_name"], reverse=True)


def _newest_local():
    plain = sorted(n for n in os.listdir(BACKUPS) if re.fullmatch(r"survival-\d{8}-\d{6}\.tar\.gz", n)) \
        if os.path.isdir(BACKUPS) else []
    return os.path.join(BACKUPS, plain[-1]) if plain else None


def push(cfg, path=None):
    """Zet de nieuwste (of de gegeven) backup als release 'dag-JJJJMMDD' neer; dezelfde dag wordt vervangen."""
    path = path or _newest_local()
    if not path:
        raise Fout("er is nog geen backup om weg te zetten (mc backup)")
    size = os.path.getsize(path)
    if size > LIMIT:
        raise Fout(f"backup is te groot voor GitHub ({size // 2**20} MB, grens 1.95 GB); houd hem op de box")
    name = os.path.basename(path)
    day = re.search(r"-(\d{8})-", name).group(1)
    tag = f"dag-{day}"
    r = repo(cfg)
    old = _call("GET", f"{API}/repos/{r}/releases/tags/{tag}")
    if old:
        _delete(cfg, old)
    rel = _call("POST", f"{API}/repos/{r}/releases", {
        "tag_name": tag, "name": f"Wereld {day[:4]}-{day[4:6]}-{day[6:]}", "draft": False, "prerelease": False,
        "body": f"Backup `{name}` ({size // 2**20} MB), gemaakt door de Minecraft Bot. Terugzetten: `mc restore github:{day}`."})
    with open(path, "rb") as f:
        url = f"https://uploads.github.com/repos/{r}/releases/{rel['id']}/assets?name={quote(name)}"
        _call("POST", url, body=f, headers={"Content-Type": "application/gzip", "Content-Length": str(size)}, timeout=3600)
    removed = prune(cfg)
    write_json(os.path.join(STATE, "offsite.json"), {"laatste": tag, "bestand": name, "tijd": now_iso(), "fout": None})
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


def fetch(cfg, which="nieuwste"):
    """Download een backup van GitHub naar data/backups. which: 'nieuwste' of JJJJMMDD. Geeft het pad."""
    rels = releases(cfg)
    if not rels:
        raise Fout(f"geen backups op GitHub ({repo(cfg)})")
    rel = rels[0] if which in ("nieuwste", "laatste", "") else next((x for x in rels if x["tag_name"] == f"dag-{which}"), None)
    if not rel or not rel.get("assets"):
        raise Fout(f"geen backup voor {which} op GitHub; er zijn: {', '.join(x['tag_name'][4:] for x in rels)}")
    asset = rel["assets"][0]
    dest = os.path.join(BACKUPS, asset["name"])
    if os.path.exists(dest) and os.path.getsize(dest) == asset["size"]:
        return dest
    # het API-adres stuurt door naar opslag die zelf ondertekend is: daar géén Authorization meesturen
    opener = urllib.request.build_opener(_NoRedirect)
    req = urllib.request.Request(asset["url"], headers=_headers({"Accept": "application/octet-stream"}))
    try:
        opener.open(req, timeout=60)
        raise Fout("GitHub gaf geen downloadadres")
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 303, 307, 308):
            raise Fout(f"download van GitHub mislukt: HTTP {e.code}")
        location = e.headers["Location"]
    os.makedirs(BACKUPS, exist_ok=True)
    tmp = dest + ".part"
    with urllib.request.urlopen(urllib.request.Request(location, headers={"User-Agent": UA}), timeout=600) as r, \
            open(tmp, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    if os.path.getsize(tmp) != asset["size"]:
        os.remove(tmp)
        raise Fout("download onvolledig")
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
        a = (rel.get("assets") or [{}])[0]
        say(f"  {rel['tag_name'][4:]}  {a.get('name', '?')}  {a.get('size', 0) // 2**20} MB")
