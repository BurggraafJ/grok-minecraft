"""Git als bron: binnenhalen wat op GitHub veranderde, en de eigen wijzigingen van de Bot vastleggen en pushen.

Alles via HTTPS (poort 443): veel Grok-boxen laten sinds oktober 2026 alleen HTTPS naar buiten, dus geen SSH.
Ophalen kan zonder sleutel (de repo is publiek). Pushen gaat met een fine-grained token van Jelle dat alleen
deze repo en de backup-repo mag schrijven (`mc git token`); het staat in data/secrets, nooit in Git of een URL.
"""
import base64
import os
import subprocess

from .base import REPO, Fout, http_json, read_text, write_text, SECRETS

TOKEN = os.path.join(SECRETS, "github_token")
BRANCH = "main"
TOKEN_URL = ("https://github.com/settings/personal-access-tokens/new?name=minecraft-bot"
             "&description=Minecraft%20Bot%3A%20server.json%2C%20plugins%20en%20backups&contents=write")


def token():
    t = (read_text(TOKEN, "") or "").strip()
    return t or os.environ.get("GITHUB_TOKEN") or None


def _auth():
    t = token()
    if not t:
        return []
    b = base64.b64encode(f"x-access-token:{t}".encode()).decode()
    return ["-c", f"http.https://github.com/.extraheader=AUTHORIZATION: basic {b}"]


def git(*args, check=True, auth=False):
    cmd = ["git", "-C", REPO, *(_auth() if auth else []), *args]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=600,
                       env=dict(os.environ, GIT_TERMINAL_PROMPT="0"))
    if check and r.returncode != 0:
        raise Fout(f"git {args[0]} mislukt: {(r.stderr or r.stdout).strip()[-400:]}")
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
    """Naam voor commits; ophalen en pushen allebei via https."""
    if not is_repo():
        return
    if not git("config", "user.name", check=False):
        git("config", "user.name", "Minecraft Bot")
        git("config", "user.email", "minecraft-bot@users.noreply.github.com")
    s = slug()
    if s:
        git("remote", "set-url", "origin", f"https://github.com/{s}.git")
        git("remote", "set-url", "--push", "origin", f"https://github.com/{s}.git")


def save_token(value, repos):
    """Controleer het token op elke repo en bewaar het (modus 600). Geeft per repo een regel."""
    value = (value or "").strip()
    if not value:
        raise Fout("geen token: zet GITHUB_TOKEN in de Secrets van de Bot (of geef hem mee) en probeer opnieuw")
    lines = []
    for repo in repos:
        try:
            d = http_json(f"https://api.github.com/repos/{repo}",
                          headers={"Authorization": f"Bearer {value}", "Accept": "application/vnd.github+json"}, tries=1)
            lines.append(f"{repo}: zichtbaar ({'privé' if d.get('private') else 'publiek'})")
        except Fout as e:
            raise Fout(f"het token kan {repo} niet zien ({e}); kies bij 'Repository access' beide repo's")
    write_text(TOKEN, value + "\n", mode=0o600)
    return lines


def dirty(paths=None):
    return bool(git("status", "--porcelain", *(["--"] + list(paths) if paths else [])))


def fetch():
    git("fetch", "--quiet", "origin", BRANCH, auth=True)
    ahead, behind = (int(x) for x in git("rev-list", "--left-right", "--count", f"HEAD...origin/{BRANCH}").split())
    return ahead, behind


def pull():
    """Haal origin/main binnen. Geeft de lijst nieuwe commits (onderwerpregels)."""
    ahead, behind = fetch()
    if not behind:
        return []
    log = git("log", "--format=%h %s", f"HEAD..origin/{BRANCH}").splitlines()
    stashed = False
    if dirty():
        git("stash", "push", "--include-untracked", "-m", "mc-pull")
        stashed = True
    try:
        if ahead:
            if subprocess.run(["git", "-C", REPO, "rebase", f"origin/{BRANCH}"], capture_output=True).returncode != 0:
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
    if not token():
        return False, "nog geen GitHub-token (mc git token)"
    r = subprocess.run(["git", "-C", REPO, *_auth(), "push", "origin", f"HEAD:{BRANCH}"], capture_output=True,
                       text=True, timeout=300, env=dict(os.environ, GIT_TERMINAL_PROMPT="0"))
    if r.returncode == 0:
        return True, "gepusht"
    err = (r.stderr or r.stdout).strip()
    if retry and ("rejected" in err or "fetch first" in err or "non-fast-forward" in err):
        pull()
        return push(retry=False)
    if "403" in err or "denied" in err.lower() or "401" in err or "Authentication failed" in err:
        return False, "het token mag niet schrijven (Contents: Read and write op deze repo) of is verlopen"
    return False, err[-300:]


def status():
    out = {"repo": slug(), "head": git("rev-parse", "--short", "HEAD", check=False), "token": bool(token())}
    try:
        out["voor"], out["achter"] = fetch()
    except Fout as e:
        out["fout"] = str(e)
    out["lokaal_gewijzigd"] = dirty()
    return out


CI_ROOD = ("failure", "timed_out", "startup_failure", "action_required")


def ci_stand(slug_=None, runs=None):
    """Stand van de CI (GitHub Actions) op main: None als alles goed is of de nieuwste run nog loopt,
    anders (korte sha, titel, url) van de nieuwste afgeronde rode run. Geannuleerde runs (een nieuwere push
    annuleert de vorige) tellen niet mee. `runs` alleen voor tests."""
    if runs is None:
        s = slug_ or slug()
        if not s:
            return None
        h = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
        t = token()
        if t:
            h["Authorization"] = f"Bearer {t}"
        runs = http_json(f"https://api.github.com/repos/{s}/actions/runs?branch={BRANCH}&event=push&per_page=10",
                         headers=h, tries=2).get("workflow_runs") or []
    for r in runs:   # nieuwste eerst
        if r.get("status") != "completed":
            return None        # loopt nog: niets zeggen, de volgende ronde ziet de uitkomst
        if r.get("conclusion") in ("cancelled", "skipped", "neutral"):
            continue
        if r.get("conclusion") in CI_ROOD:
            titel = ((r.get("display_title") or "").splitlines() or [""])[0][:80]
            return r.get("head_sha", "")[:7], titel, r.get("html_url", "")
        return None
    return None


def ci_melding(slug_=None):
    """Eén MELD-regel als de CI van main rood is, anders None (ook als GitHub even niet antwoordt)."""
    try:
        rood = ci_stand(slug_)
    except Fout:
        return None
    if not rood:
        return None
    sha, titel, url = rood
    return f"MELD CI op GitHub staat rood op main ({sha} «{titel}»): eerst repareren, zie {url}"
