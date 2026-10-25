"""server/server.json: de gewenste stand van de server, in Git. Elke commit is een serverversie.

Wat erin staat (versies, plugins met vaste versie en hash, instellingen, patches) zet `mc apply` op de box.
Wat er níet in staat: geheimen, de wereld, de whitelist (data/spelers.json op de box) en box-gegevens als
geheugen en poorten (data/config.json).
"""
import os
import re

from . import downloads as dl
from .base import REPO, Fout, read_json, write_json

SPEC = os.path.join(REPO, "server", "server.json")
TARGETS = ("survival", "lobby", "velocity")


def path():
    return SPEC


def load(p=None):
    s = read_json(p or SPEC)
    if s is None:
        raise Fout(f"{p or SPEC} ontbreekt")
    validate(s)
    return s


def save(s, p=None):
    validate(s)
    write_json(p or SPEC, s)


def validate(s):
    errs = []
    for k in ("minecraft", "paper_build", "velocity", "java", "plugins"):
        if k not in s:
            errs.append(f"sleutel '{k}' ontbreekt")
    names = set()
    for i, p in enumerate(s.get("plugins", [])):
        where = f"plugins[{i}] ({p.get('naam', '?')})"
        if not p.get("naam"):
            errs.append(f"{where}: naam ontbreekt")
        if p.get("naam", "").lower() in names:
            errs.append(f"{where}: dubbele naam")
        names.add(p.get("naam", "").lower())
        op = p.get("op") or []
        if not op or any(t not in TARGETS for t in op):
            errs.append(f"{where}: 'op' moet een lijst uit {TARGETS} zijn")
        bron = p.get("bron")
        if bron == "modrinth":
            for k in ("project", "versie", "bestand", "url", "sha512"):
                if not p.get(k):
                    errs.append(f"{where}: '{k}' ontbreekt")
        elif bron == "url":
            if not (p.get("url") and p.get("sha256") and p.get("bestand")):
                errs.append(f"{where}: url, bestand en sha256 zijn verplicht")
        elif bron == "eigen":
            pad = p.get("pad", "")
            if not re.fullmatch(r"plugins/[a-z0-9][a-z0-9-]*", pad):
                errs.append(f"{where}: pad moet plugins/<naam> zijn")
            elif not os.path.isdir(os.path.join(REPO, pad)):
                errs.append(f"{where}: map {pad} bestaat niet")
        else:
            errs.append(f"{where}: bron moet modrinth, url of eigen zijn")
        if bron != "eigen" and not re.fullmatch(r"[A-Za-z0-9._+\-]+\.jar", p.get("bestand", "x.jar")):
            errs.append(f"{where}: vreemde bestandsnaam")
    for group, perms in (s.get("rechten") or {}).items():
        if not re.fullmatch(r"[a-z0-9_-]+", group) or not isinstance(perms, list):
            errs.append(f"rechten: groep '{group}' moet kleine letters zijn met een lijst permissies")
        for perm in perms if isinstance(perms, list) else []:
            if not re.fullmatch(r"[A-Za-z0-9_.*\-]+", perm):
                errs.append(f"rechten.{group}: vreemde permissie '{perm}'")
    for svc, rules in (s.get("gamerules") or {}).items():
        if svc not in ("survival", "lobby"):
            errs.append(f"gamerules: onbekende server {svc}")
        for k in rules:
            if not re.fullmatch(r"[A-Za-z0-9_:]+", k):
                errs.append(f"gamerules.{svc}: vreemde naam '{k}'")
    for i, w in enumerate(s.get("wachtlijst") or []):
        if not (w.get("project") and w.get("op") and w.get("reden")):
            errs.append(f"wachtlijst[{i}]: project, op en reden zijn verplicht")
    for svc, files in (s.get("patches") or {}).items():
        if svc not in TARGETS:
            errs.append(f"patches: onbekende server {svc}")
        for f in files:
            if ".." in f or f.startswith("/"):
                errs.append(f"patches: onveilig pad {f}")
    if errs:
        raise Fout("server.json klopt niet:\n  " + "\n  ".join(errs))


def plugins_for(s, target):
    return [p for p in s["plugins"] if target in p["op"]]


def resolve_modrinth(project, mc_version, target="survival"):
    """Kies een versie: eerst één die deze MC-versie noemt, anders de nieuwste van dezelfde jaargang (26.x),
    gemarkeerd als niet-officieel. De staging-test beslist dan of hij werkt."""
    platform = "velocity" if target == "velocity" else "paper"
    info = dl.modrinth_latest(project, platform, mc_version)
    officieel = True
    if not info and platform == "paper":
        major = mc_version.split(".")[0]
        info = _modrinth_same_major(project, major)
        officieel = False
    if not info:
        raise Fout(f"geen versie van '{project}' voor {platform} {mc_version}")
    return {"bron": "modrinth", "project": project, "versie": info["version"], "bestand": info["bestand"],
            "url": info["url"], "sha512": info["sha512"], "officieel": officieel}


def _modrinth_same_major(project, major):
    import json
    from urllib.parse import quote
    from .base import http_json
    for loader in ("paper", "bukkit", "spigot"):
        versions = http_json(f"https://api.modrinth.com/v2/project/{quote(project)}/version?loaders="
                             + quote(json.dumps([loader])))
        for vtype in ("release", "beta"):
            for v in versions:
                if v.get("version_type") == vtype and any(g.split(".")[0] == major for g in v["game_versions"]):
                    f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
                    return {"version": v["version_number"], "url": f["url"], "bestand": f["filename"],
                            "sha512": f["hashes"]["sha512"]}
    return None


def jar_name(p):
    return p["bestand"] if p["bron"] != "eigen" else f"{os.path.basename(p['pad'])}.jar"


def modrinth_title(project):
    from urllib.parse import quote
    from .base import http_json
    try:
        return http_json(f"https://api.modrinth.com/v2/project/{quote(project)}").get("title") or project
    except Fout:
        return project
