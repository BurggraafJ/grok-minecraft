"""Paden, uitvoer, downloads en de box meten. Alleen standaardbibliotheek (Python 3.9+)."""
import contextlib
import fcntl
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request

UA = "grok-minecraft/1.0 (+https://github.com/BurggraafJ/grok-minecraft)"
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOME = os.path.abspath(os.environ.get("MC_HOME", "/workspace/minecraft"))
DATA = os.path.join(HOME, "data")
TOOLS = os.path.join(HOME, "tools")
SERVERS = os.path.join(DATA, "servers")
STATE = os.path.join(DATA, "state")
SECRETS = os.path.join(DATA, "secrets")
BACKUPS = os.path.join(DATA, "backups")
JARS = os.path.join(DATA, "jars")
BUILDS = os.path.join(DATA, "builds")
PLUGSRC = os.path.join(DATA, "plugins-src")
CONFIG = os.path.join(DATA, "config.json")
SPELERS = os.path.join(DATA, "spelers.json")
EVENTS = os.path.join(STATE, "events.log")

GB = 1024 ** 3
MB = 1024 ** 2


class Fout(Exception):
    """Een fout met een uitleg in gewone taal; de CLI print hem zonder traceback."""


def say(msg=""):
    print(msg, flush=True)


def now_iso():
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def stamp():
    return time.strftime("%Y%m%d-%H%M%S", time.gmtime())


def ensure_dirs():
    for d in (DATA, TOOLS, SERVERS, STATE, SECRETS, BACKUPS, JARS, BUILDS, PLUGSRC):
        os.makedirs(d, exist_ok=True)
    os.chmod(SECRETS, 0o700)


# ── bestanden ──────────────────────────────────────────────────────────────────────────────────

def read_json(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default
    except json.JSONDecodeError as e:
        raise Fout(f"{path} is geen geldige JSON ({e}); herstel of verwijder het bestand")


def write_json(path, obj, mode=None):
    write_text(path, json.dumps(obj, indent=2, ensure_ascii=False) + "\n", mode)


def write_text(path, text, mode=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = f"{path}.tmp{os.getpid()}"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(text)
    if mode is not None:
        os.chmod(tmp, mode)
    os.replace(tmp, path)


def read_text(path, default=None):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    except FileNotFoundError:
        return default


def tail(path, n=40):
    try:
        with open(path, "rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            f.seek(max(0, size - 200_000))
            lines = f.read().decode("utf-8", "replace").splitlines()
        return lines[-n:]
    except FileNotFoundError:
        return []


def dir_size(path):
    total = 0
    for root, _dirs, files in os.walk(path):
        for name in files:
            try:
                total += os.lstat(os.path.join(root, name)).st_size
            except OSError:
                pass
    return total


def human(n):
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if abs(n) < 1024 or unit == "TB":
            return f"{n:.0f} {unit}" if unit in ("B", "KB") else f"{n:.1f} {unit}"
        n /= 1024


@contextlib.contextmanager
def lock(name, wait=True, timeout=600):
    """Bestandslock in data/state, zodat CLI en supervisor niet tegelijk aan dezelfde dienst werken."""
    os.makedirs(STATE, exist_ok=True)
    f = open(os.path.join(STATE, f"{name}.lock"), "w")
    deadline = time.time() + timeout
    while True:
        try:
            fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
            break
        except BlockingIOError:
            if not wait or time.time() > deadline:
                f.close()
                raise Fout(f"{name} is bezet door een ander mc-proces; probeer het zo opnieuw")
            time.sleep(0.5)
    try:
        yield
    finally:
        fcntl.flock(f, fcntl.LOCK_UN)
        f.close()


# ── netwerk ────────────────────────────────────────────────────────────────────────────────────

def http(url, timeout=60, tries=3, headers=None):
    h = {"User-Agent": UA}
    h.update(headers or {})
    last = None
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            last = e
            if e.code in (400, 401, 403, 404):
                break
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
            last = e
        time.sleep(2 * (attempt + 1))
    raise Fout(f"download mislukt: {url} ({last})")


def http_json(url, **kw):
    return json.loads(http(url, **kw).decode("utf-8"))


def download(url, dest, sha256=None, sha512=None, sha1=None):
    """Download naar dest (atomair) en controleer de hash. Bestaat dest al met de goede hash: niets doen."""
    want = [(name, val) for name, val in (("sha256", sha256), ("sha512", sha512), ("sha1", sha1)) if val]
    if os.path.exists(dest) and want and all(file_hash(dest, n) == v.lower() for n, v in want):
        return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    h = {"User-Agent": UA}
    last = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=120) as r, open(tmp, "wb") as f:
                shutil.copyfileobj(r, f, 1024 * 1024)
            break
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
            last = e
            time.sleep(3 * (attempt + 1))
    else:
        raise Fout(f"download mislukt: {url} ({last})")
    for name, val in want:
        got = file_hash(tmp, name)
        if got != val.lower():
            os.remove(tmp)
            raise Fout(f"hash klopt niet voor {os.path.basename(dest)}: {name} {got[:16]}… verwacht {val[:16]}…")
    os.replace(tmp, dest)
    return dest


def file_hash(path, algo="sha256"):
    h = hashlib.new(algo)
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def extract_tar(archive, dest_dir):
    """Pak een tar.gz uit zonder paden buiten dest_dir toe te laten."""
    os.makedirs(dest_dir, exist_ok=True)
    real = os.path.realpath(dest_dir)
    with tarfile.open(archive) as t:
        for m in t.getmembers():
            target = os.path.realpath(os.path.join(dest_dir, m.name))
            if not (target == real or target.startswith(real + os.sep)):
                raise Fout(f"onveilig pad in {archive}: {m.name}")
            if m.issym() or m.islnk():
                link = os.path.realpath(os.path.join(os.path.dirname(target), m.linkname))
                if not link.startswith(real + os.sep):
                    raise Fout(f"onveilige link in {archive}: {m.name}")
        if hasattr(tarfile, "data_filter"):
            t.extractall(dest_dir, filter="data")
        else:
            t.extractall(dest_dir)


# ── de box ─────────────────────────────────────────────────────────────────────────────────────

def system():
    """('linux' of 'mac', 'x64' of 'aarch64')."""
    s = platform.system().lower()
    osn = {"linux": "linux", "darwin": "mac"}.get(s)
    m = platform.machine().lower()
    arch = {"x86_64": "x64", "amd64": "x64", "aarch64": "aarch64", "arm64": "aarch64"}.get(m)
    if not osn or not arch:
        raise Fout(f"niet ondersteund platform: {s}/{m}")
    return osn, arch


def _first_int(path):
    try:
        v = open(path).read().split()[0]
        return None if v == "max" else int(v)
    except (OSError, ValueError, IndexError):
        return None


def meminfo():
    """(totaal, beschikbaar) in bytes, met de cgroup-limiet meegewogen (containers liegen in /proc/meminfo)."""
    total = avail = None
    try:
        for line in open("/proc/meminfo"):
            k, v = line.split(":", 1)
            if k == "MemTotal":
                total = int(v.split()[0]) * 1024
            elif k == "MemAvailable":
                avail = int(v.split()[0]) * 1024
    except OSError:
        pass
    if total is None:  # macOS (alleen voor lokaal testen)
        try:
            total = int(subprocess.run(["sysctl", "-n", "hw.memsize"], capture_output=True, text=True).stdout)
        except (OSError, ValueError):
            total = 8 * GB
        avail = total // 2
    limit = _first_int("/sys/fs/cgroup/memory.max") or _first_int("/sys/fs/cgroup/memory/memory.limit_in_bytes")
    if limit and limit < total:
        used = _first_int("/sys/fs/cgroup/memory.current") or _first_int("/sys/fs/cgroup/memory/memory.usage_in_bytes") or 0
        total = limit
        avail = min(avail or limit, max(0, limit - used))
    return total, (avail if avail is not None else total // 2)


def cpus():
    n = len(os.sched_getaffinity(0)) if hasattr(os, "sched_getaffinity") else (os.cpu_count() or 1)
    try:
        quota, period = open("/sys/fs/cgroup/cpu.max").read().split()
        if quota != "max":
            n = min(n, max(1, int(int(quota) / int(period))))
    except (OSError, ValueError):
        pass
    return n


def disk(path=None):
    """(vrij, totaal) in bytes voor de schijf waar MC_HOME op staat."""
    p = path or HOME
    while not os.path.exists(p):
        p = os.path.dirname(p)
    u = shutil.disk_usage(p)
    return u.free, u.total


def proc_alive(pid):
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    # een eigen kindproces dat klaar is: oogsten (anders blijft het als zombie 'levend')
    try:
        if os.waitpid(pid, os.WNOHANG)[0] == pid:
            return False
    except ChildProcessError:
        pass
    # een zombie telt niet als levend
    try:
        with open(f"/proc/{pid}/stat") as f:
            return f.read().split(") ", 1)[1].split()[0] != "Z"
    except (OSError, IndexError):
        r = subprocess.run(["ps", "-o", "stat=", "-p", str(pid)], capture_output=True, text=True)
        return bool(r.stdout.strip()) and not r.stdout.strip().startswith("Z")


def proc_cmdline(pid):
    try:
        with open(f"/proc/{pid}/cmdline", "rb") as f:
            return f.read().replace(b"\0", b" ").decode("utf-8", "replace")
    except OSError:
        r = subprocess.run(["ps", "-o", "command=", "-p", str(pid)], capture_output=True, text=True)
        return r.stdout.strip()


def proc_rss(pid):
    try:
        for line in open(f"/proc/{pid}/status"):
            if line.startswith("VmRSS:"):
                return int(line.split()[1]) * 1024
    except OSError:
        r = subprocess.run(["ps", "-o", "rss=", "-p", str(pid)], capture_output=True, text=True)
        try:
            return int(r.stdout.strip()) * 1024
        except ValueError:
            return 0
    return 0


def python_ok():
    return sys.version_info >= (3, 9)
