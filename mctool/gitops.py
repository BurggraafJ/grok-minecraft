"""Git als bron: binnenhalen wat op GitHub veranderde, en de eigen wijzigingen van de Bot vastleggen en pushen.

Ophalen gaat via https (de repo is publiek, geen sleutel nodig). Pushen gaat via een deploy key die alleen
voor deze repo geldt: `mc git sleutel` maakt hem en toont de publieke helft die Jelle één keer toevoegt.
"""
import os
import subprocess

from .base import REPO, SECRETS, Fout, read_text

KEY = os.path.join(SECRETS, "deploy_key")
BRANCH = "main"


def git(*args, check=True, env=None):
    e = dict(os.environ)
    if os.path.exists(KEY):
        e["GIT_SSH_COMMAND"] = f"ssh -i {KEY} -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o BatchMode=yes"
    e.update(env or {})
    r = subprocess.run(["git", "-C", REPO, *args], capture_output=True, text=True, env=e, timeout=300)
    if check and r.returncode != 0:
        raise Fout(f"git {' '.join(args)} mislukt: {(r.stderr or r.stdout).strip()[-400:]}")
    return r.stdout.strip()


def is_repo():
    return os.path.isdir(os.path.join(REPO, ".git"))


def slug():
    url = git("remote", "get-url", "origin", check=False)
    for prefix in ("https://github.com/", "git@github.com:"):
        if url.startswith(prefix):
            return url[len(prefix):].rstrip("/").removesuffix(".git")
    return None


def setup():
    """Naam voor commits en een push-url via SSH (ophalen blijft https)."""
    if not is_repo():
        return
    if not git("config", "user.name", check=False):
        git("config", "user.name", "Minecraft Bot")
        git("config", "user.email", "minecraft-bot@users.noreply.github.com")
    s = slug()
    if s:
        git("remote", "set-url", "--push", "origin", f"git@github.com:{s}.git")


def ensure_key():
    if not os.path.exists(KEY):
        os.makedirs(SECRETS, exist_ok=True)
        r = subprocess.run(["ssh-keygen", "-t", "ed25519", "-N", "", "-C", "minecraft-bot deploy key", "-f", KEY],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise Fout(f"ssh-keygen mislukt: {r.stderr.strip()}")
        os.chmod(KEY, 0o600)
    return read_text(KEY + ".pub").strip()


def dirty(paths=None):
    return bool(git("status", "--porcelain", *(["--"] + list(paths) if paths else [])))


def fetch():
    git("fetch", "--quiet", "origin", BRANCH)
    ahead, behind = (int(x) for x in git("rev-list", "--left-right", "--count", f"HEAD...origin/{BRANCH}").split())
    return ahead, behind


def pull():
    """Haal origin/main binnen. Geeft de lijst nieuwe commits (onderwerpregels)."""
    ahead, behind = fetch()
    if not behind:
        return []
    log = git("log", "--format=%h %s", f"HEAD..origin/{BRANCH}").splitlines()
    if dirty():
        git("stash", "push", "--include-untracked", "-m", "mc-pull")
        stashed = True
    else:
        stashed = False
    try:
        if ahead:
            r = subprocess.run(["git", "-C", REPO, "rebase", f"origin/{BRANCH}"], capture_output=True, text=True)
            if r.returncode != 0:
                git("rebase", "--abort", check=False)
                raise Fout("eigen commits botsen met GitHub; los het op met git (rebase op origin/main)")
        else:
            git("merge", "--ff-only", f"origin/{BRANCH}")
    finally:
        if stashed:
            git("stash", "pop", check=False)
    return log


def commit(message, paths):
    git("add", "--", *paths)
    if not git("diff", "--cached", "--name-only"):
        return None
    git("commit", "-m", message)
    return git("rev-parse", "--short", "HEAD")


def push(retry=True):
    """Push naar GitHub. Geeft (ok, uitleg)."""
    if not os.path.exists(KEY):
        return False, "nog geen deploy key (mc git sleutel)"
    r = subprocess.run(["git", "-C", REPO, "push", "origin", f"HEAD:{BRANCH}"], capture_output=True, text=True,
                       env=dict(os.environ, GIT_SSH_COMMAND=f"ssh -i {KEY} -o IdentitiesOnly=yes "
                                                             f"-o StrictHostKeyChecking=accept-new -o BatchMode=yes"),
                       timeout=120)
    if r.returncode == 0:
        return True, "gepusht"
    err = (r.stderr or r.stdout).strip()
    if retry and ("rejected" in err or "fetch first" in err or "non-fast-forward" in err):
        pull()
        return push(retry=False)
    if "Permission denied" in err or "denied" in err or "ERROR: The key" in err:
        return False, "GitHub kent de deploy key nog niet (of zonder schrijfrecht)"
    return False, err[-300:]


def status():
    out = {"repo": slug(), "head": git("rev-parse", "--short", "HEAD", check=False), "sleutel": os.path.exists(KEY)}
    try:
        out["voor"], out["achter"] = fetch()
    except Fout as e:
        out["fout"] = str(e)
    out["lokaal_gewijzigd"] = dirty()
    return out
