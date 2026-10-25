"""Alles wat van internet komt, altijd met een hashcontrole en altijd onder MC_HOME (overleeft een Computer Update).

Bronnen: PaperMC Fill API (Paper, Velocity), Adoptium (Java), Modrinth (plugins), services.gradle.org (Gradle),
GitHub-releases met vaste versie en hash (bore). Geen GitHub-API: de box deelt zijn IP met andere klanten.
"""
import json
import os
import re
import shutil
import subprocess
import zipfile
from urllib.parse import quote

from .base import (JARS, TOOLS, Fout, download, extract_tar, http, http_json, say, system)

FILL = "https://fill.papermc.io/v3/projects"
GOOD = ("STABLE", "RECOMMENDED")

BORE_VERSION = "v0.6.0"
BORE_SHA256 = {
    ("linux", "x64"): ("x86_64-unknown-linux-musl", "e484d1e3acba77169b773f31a5bfb34192d4b660f44a094a658a2522cd2270f7"),
    ("linux", "aarch64"): ("aarch64-unknown-linux-musl", "ffc4515f3617420b243758cf36ed6a63208d7dba76b2ec3e90d1f476a9742951"),
}


# ── Java ───────────────────────────────────────────────────────────────────────────────────────

def java_bin(feature):
    osn, _ = system()
    home = os.path.join(TOOLS, f"jdk-{feature}")
    return os.path.join(home, "Contents", "Home", "bin", "java") if osn == "mac" else os.path.join(home, "bin", "java")


def java_home(feature):
    return os.path.dirname(os.path.dirname(java_bin(feature)))


def ensure_java(feature):
    """Temurin JDK <feature> in tools/jdk-<feature>. JDK (niet JRE) zodat de Bot ook plugins kan compileren."""
    jb = java_bin(feature)
    if os.path.exists(jb):
        r = subprocess.run([jb, "-version"], capture_output=True, text=True)
        if r.returncode == 0 and f'"{feature}' in (r.stderr + r.stdout):
            return jb
    osn, arch = system()
    say(f"  Java {feature} downloaden (Temurin, {osn}/{arch})…")
    assets = http_json(f"https://api.adoptium.net/v3/assets/latest/{feature}/hotspot"
                       f"?architecture={arch}&image_type=jdk&os={osn}&vendor=eclipse")
    if not assets:
        raise Fout(f"geen Temurin JDK {feature} voor {osn}/{arch}")
    pkg = assets[0]["binary"]["package"]
    archive = os.path.join(TOOLS, "downloads", pkg["name"])
    download(pkg["link"], archive, sha256=pkg["checksum"])
    tmp = os.path.join(TOOLS, f"jdk-{feature}.tmp")
    shutil.rmtree(tmp, ignore_errors=True)
    extract_tar(archive, tmp)
    inner = [d for d in os.listdir(tmp) if not d.startswith(".")]
    if len(inner) != 1:
        raise Fout(f"onverwachte JDK-indeling: {inner}")
    home = os.path.join(TOOLS, f"jdk-{feature}")
    shutil.rmtree(home, ignore_errors=True)
    os.replace(os.path.join(tmp, inner[0]), home)
    shutil.rmtree(tmp, ignore_errors=True)
    os.remove(archive)
    if not os.path.exists(jb):
        raise Fout(f"java niet gevonden na uitpakken: {jb}")
    return jb


# ── Paper en Velocity (Fill) ───────────────────────────────────────────────────────────────────

def fill_versions(project):
    d = http_json(f"{FILL}/{project}")
    out = []
    for fam in d["versions"].values():
        out.extend(fam)
    return out


def _prerelease(v):
    return bool(re.search(r"(-rc|-pre|snapshot|alpha|beta)", v, re.I))


def fill_build(project, version, build=None):
    """Een build (standaard de nieuwste stabiele) met download-url, sha256 en Java-eis."""
    vinfo = http_json(f"{FILL}/{project}/versions/{quote(version)}")
    if build is None:
        builds = http_json(f"{FILL}/{project}/versions/{quote(version)}/builds")
        good = [b for b in builds if b.get("channel") in GOOD]
        if not good:
            return None
        b = max(good, key=lambda x: x["id"])
    else:
        b = http_json(f"{FILL}/{project}/versions/{quote(version)}/builds/{build}")
    dl = b["downloads"]["server:default"]
    java = vinfo["version"].get("java") or {}
    return {
        "project": project, "version": version, "build": b["id"], "channel": b.get("channel"),
        "url": dl["url"], "name": dl["name"], "sha256": dl["checksums"]["sha256"],
        "java": (java.get("version") or {}).get("minimum", 21),
        "flags": (java.get("flags") or {}).get("recommended", []),
        "support": (vinfo["version"].get("support") or {}).get("status"),
    }


def fill_latest(project, version=None):
    """Nieuwste stabiele build. Zonder versie: de nieuwste versie die een stabiele build heeft."""
    if version:
        b = fill_build(project, version)
        if not b:
            raise Fout(f"{project} {version} heeft (nog) geen stabiele build")
        return b
    for v in fill_versions(project):
        if _prerelease(v):
            continue
        b = fill_build(project, v)
        if b:
            return b
    raise Fout(f"geen stabiele {project}-build gevonden")


def ensure_jar(info):
    path = os.path.join(JARS, info["name"])
    if not os.path.exists(path):
        say(f"  {info['name']} downloaden…")
    download(info["url"], path, sha256=info["sha256"])
    return path


# ── Plugins (Modrinth) ─────────────────────────────────────────────────────────────────────────

LOADERS = {"paper": ["paper", "bukkit", "spigot"], "velocity": ["velocity"]}


def modrinth_latest(slug, platform, mc_version):
    """Nieuwste release (of anders beta) van een Modrinth-project voor dit platform en deze MC-versie."""
    for loader in LOADERS[platform]:
        params = "loaders=" + quote(json.dumps([loader]))
        if platform == "paper":
            params += "&game_versions=" + quote(json.dumps([mc_version]))
        try:
            versions = http_json(f"https://api.modrinth.com/v2/project/{quote(slug)}/version?{params}")
        except Fout as e:
            if "404" in str(e):
                raise Fout(f"plugin '{slug}' bestaat niet op Modrinth")
            raise
        for vtype in ("release", "beta"):
            for v in versions:
                if v.get("version_type") != vtype:
                    continue
                f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
                return {"slug": slug, "project_id": v["project_id"], "version": v["version_number"],
                        "url": f["url"], "bestand": f["filename"], "sha512": f["hashes"]["sha512"]}
    return None


def modrinth_search(text, mc_version, limit=10):
    facets = f'[["project_type:plugin"],["versions:{mc_version}"],["categories:paper","categories:bukkit","categories:velocity"]]'
    d = http_json("https://api.modrinth.com/v2/search?limit=%d&query=%s&facets=%s" % (limit, quote(text), quote(facets)))
    return [{"slug": h["slug"], "titel": h["title"], "downloads": h["downloads"], "omschrijving": h["description"][:120]}
            for h in d.get("hits", [])]


def ensure_modrinth(info):
    path = os.path.join(JARS, "plugins", info["bestand"])
    download(info["url"], path, sha512=info["sha512"])
    return path


# ── Gradle, paper-api ──────────────────────────────────────────────────────────────────────────

def ensure_gradle():
    existing = sorted(d for d in os.listdir(TOOLS) if d.startswith("gradle-")) if os.path.isdir(TOOLS) else []
    for d in reversed(existing):
        g = os.path.join(TOOLS, d, "bin", "gradle")
        if os.path.exists(g):
            return g
    cur = http_json("https://services.gradle.org/versions/current")
    sha = http(cur["checksumUrl"]).decode().strip().split()[0]
    archive = os.path.join(TOOLS, "downloads", f"gradle-{cur['version']}-bin.zip")
    say(f"  Gradle {cur['version']} downloaden…")
    download(cur["downloadUrl"], archive, sha256=sha)
    with zipfile.ZipFile(archive) as z:
        z.extractall(TOOLS)
    os.remove(archive)
    g = os.path.join(TOOLS, f"gradle-{cur['version']}", "bin", "gradle")
    os.chmod(g, 0o755)
    return g


def paper_api_version(mc_version):
    xml = http("https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml").decode()
    versions = re.findall(r"<version>([^<]+)</version>", xml)
    cands = [v for v in versions if v.startswith(mc_version + ".build.") and v.endswith("-stable")]
    if not cands:
        cands = [v for v in versions if v.startswith(mc_version + ".build.") or v.startswith(mc_version + "-R0.1")]
    if not cands:
        raise Fout(f"geen paper-api voor {mc_version} in de PaperMC-repository")

    def key(v):
        m = re.search(r"\.build\.(\d+)", v)
        return int(m.group(1)) if m else 0
    return max(cands, key=key)


# ── tunnels ────────────────────────────────────────────────────────────────────────────────────

def ensure_bore():
    path = os.path.join(TOOLS, "bore")
    if os.path.exists(path):
        return path
    key = system()
    if key not in BORE_SHA256:
        raise Fout(f"bore heeft geen build voor {key}")
    triple, sha = BORE_SHA256[key]
    archive = os.path.join(TOOLS, "downloads", f"bore-{BORE_VERSION}-{triple}.tar.gz")
    download(f"https://github.com/ekzhang/bore/releases/download/{BORE_VERSION}/bore-{BORE_VERSION}-{triple}.tar.gz",
             archive, sha256=sha)
    extract_tar(archive, TOOLS)
    os.chmod(path, 0o755)
    os.remove(archive)
    return path


def ensure_playit():
    """playit-agent (officiële release, nieuwste). Geeft het pad naar het programma."""
    path = os.path.join(TOOLS, "playit")
    if os.path.exists(path):
        return path
    osn, arch = system()
    if osn != "linux":
        raise Fout("playit draait in deze tool alleen op Linux")
    name = {"x64": "playit-linux-amd64", "aarch64": "playit-linux-aarch64"}[arch]
    download(f"https://github.com/playit-cloud/playit-agent/releases/latest/download/{name}", path)
    os.chmod(path, 0o755)
    return path
