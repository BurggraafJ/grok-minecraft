"""`mc apply`: zet de box gelijk aan server/server.json.

Volgorde: alles downloaden en bouwen (geen downtime) → testen op staging met een kopie van de wereld → backup →
live zetten met de lobby als wachtkamer → vastleggen in data/state/deployed.json. Faalt de test, dan blijft live
ongemoeid.
"""
import glob
import json
import os
import re
import shutil
import subprocess
import time
import zipfile

from . import configfiles as cf
from . import core
from . import net
from . import downloads as dl
from . import ops
from . import spec as specmod
from .base import (BUILDS, JARS, REPO, STATE, TOOLS, Fout, file_hash, lock, now_iso, read_json, say, tail,
                   write_json)

DEPLOYED = os.path.join(STATE, "deployed.json")


def deployed():
    return read_json(DEPLOYED, {"plugins": {"survival": {}, "lobby": {}, "velocity": {}}, "instellingen": {}})


# ── artefacten ─────────────────────────────────────────────────────────────────────────────────

def build_plugin(pad, mc_version, java_feature):
    """Bouw een eigen plugin (Gradle) en geef het pad naar de jar in data/builds."""
    src = os.path.join(REPO, pad)
    naam = os.path.basename(pad)
    out = os.path.join(BUILDS, f"{naam}.jar")
    api = dl.paper_api_version(mc_version)
    stamp_path = out + ".bron"
    fingerprint = _source_hash(src, api, java_feature)
    if os.path.exists(out) and (read_json(stamp_path, {}) or {}).get("hash") == fingerprint:
        return out
    gradle = dl.ensure_gradle()
    env = dict(os.environ, JAVA_HOME=dl.java_home(java_feature), GRADLE_USER_HOME=os.path.join(TOOLS, "gradle-home"))
    say(f"  {naam} bouwen (paper-api {api})…")
    r = subprocess.run([gradle, "--no-daemon", "-q", "clean", "build", f"-PpaperApiVersion={api}",
                        f"-PmcVersion={mc_version}", f"-PjavaRelease={java_feature}"],
                       cwd=src, env=env, capture_output=True, text=True, timeout=1800)
    if r.returncode != 0:
        raise Fout(f"bouwen van {naam} mislukt:\n" + "\n".join((r.stdout + r.stderr).splitlines()[-30:]))
    jars = [j for j in glob.glob(os.path.join(src, "build", "libs", "*.jar"))
            if not re.search(r"-(sources|javadoc|plain)\.jar$", j)]
    if not jars:
        raise Fout(f"{naam}: geen jar in build/libs")
    os.makedirs(BUILDS, exist_ok=True)
    shutil.copy2(max(jars, key=os.path.getmtime), out)
    write_json(stamp_path, {"hash": fingerprint, "tijd": now_iso()})
    return out


def _source_hash(src, *extra):
    """Vingerafdruk van de broncode (zonder build-uitvoer), zodat een ongewijzigde plugin niet opnieuw bouwt."""
    import hashlib
    h = hashlib.sha256("|".join(map(str, extra)).encode())
    for root, dirs, files in os.walk(src):
        dirs[:] = sorted(d for d in dirs if d not in ("build", ".gradle"))
        for f in sorted(files):
            p = os.path.join(root, f)
            h.update(os.path.relpath(p, src).encode())
            with open(p, "rb") as fh:
                h.update(fh.read())
    return h.hexdigest()


def fetch_artifacts(spec, java_feature, build=True):
    """Download en bouw alle plugins uit de spec. Geeft {naam: {'pad', 'sha256', 'op', 'pluginnaam'}}."""
    out = {}
    for p in spec["plugins"]:
        if p["bron"] == "modrinth":
            path = dl.download(p["url"], os.path.join(JARS, "plugins", p["bestand"]), sha512=p["sha512"])
        elif p["bron"] == "url":
            path = dl.download(p["url"], os.path.join(JARS, "plugins", p["bestand"]), sha256=p["sha256"])
        else:
            path = build_plugin(p["pad"], spec["minecraft"], java_feature) if build else \
                os.path.join(BUILDS, f"{os.path.basename(p['pad'])}.jar")
        out[p["naam"]] = {"pad": path, "sha256": file_hash(path), "op": p["op"], "pluginnaam": plugin_name(path),
                          "officieel": p.get("officieel", True)}
    return out


def plugin_name(jar):
    """De naam uit plugin.yml of paper-plugin.yml (zo heet hij in de serverlog)."""
    try:
        with zipfile.ZipFile(jar) as z:
            for f in ("paper-plugin.yml", "plugin.yml", "velocity-plugin.json"):
                if f in z.namelist():
                    text = z.read(f).decode("utf-8", "replace")
                    if f.endswith(".json"):
                        return json.loads(text).get("name") or json.loads(text).get("id")
                    m = re.search(r"^name:\s*['\"]?([^'\"\n#]+?)['\"]?\s*$", text, re.M)
                    if m:
                        return m.group(1).strip()
    except (zipfile.BadZipFile, OSError, ValueError):
        pass
    return None


# ── plan ───────────────────────────────────────────────────────────────────────────────────────

def settings_for(spec, svc):
    s = dict((spec.get("instellingen") or {}).get(svc if svc != "staging" else "survival") or {})
    return {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in s.items()}


def plan(cfg, spec, arts):
    dep = deployed()
    changes = {"paper": False, "velocity": False, "survival": [], "lobby": [], "velocity_plugins": []}
    p = cfg.get("paper") or {}
    if p.get("version") != spec["minecraft"] or p.get("build") != spec["paper_build"]:
        changes["paper"] = True
    v = cfg.get("velocity") or {}
    if v.get("version") != spec["velocity"]["versie"] or v.get("build") != spec["velocity"]["build"]:
        changes["velocity"] = True
    for target, key in (("survival", "survival"), ("lobby", "lobby"), ("velocity", "velocity_plugins")):
        want = {n: a["sha256"] for n, a in arts.items() if target in a["op"]}
        have = {n: d["sha256"] for n, d in (dep["plugins"].get(target) or {}).items()}
        for n in sorted(set(want) | set(have)):
            if n not in have:
                changes[key].append(f"+ {n}")
            elif n not in want:
                changes[key].append(f"- {n}")
            elif want[n] != have[n]:
                changes[key].append(f"~ {n}")
    for svc in ("survival", "lobby"):
        props = cf.props_read(os.path.join(core.server_dir(svc), "server.properties"))
        diff = [k for k, val in settings_for(spec, svc).items() if props.get(k) != val]
        if diff:
            changes[svc].append("instellingen: " + ", ".join(diff))
        for f, keys in ((spec.get("patches") or {}).get(svc) or {}).items():
            full = os.path.join(core.server_dir(svc), f)
            if os.path.exists(full):
                for k, val in keys.items():
                    if cf.yaml_get(full, k.split(".")) != _yaml_plain(val):
                        changes[svc].append(f"patch {f}: {k}")
    if (spec.get("motd") or cfg["motd"]) != cfg["motd"]:
        changes["velocity_plugins"].append("motd")
    return changes


def _yaml_plain(v):
    return str(v).lower() if isinstance(v, bool) else str(v)


def _yaml_literal(v):
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    return cf.yaml_quote(v)


# ── synchroniseren (terwijl de dienst uit staat) ───────────────────────────────────────────────

def sync_plugins(svc, arts, target):
    """Zet precies de gewenste jars in plugins/; losse jars die niet in de spec staan gaan opzij."""
    pdir = os.path.join(core.server_dir(svc), "plugins")
    os.makedirs(pdir, exist_ok=True)
    want = {os.path.basename(a["pad"]): a for a in arts.values() if target in a["op"]}
    aside = os.path.join(STATE, "losse-plugins", svc)
    for j in glob.glob(os.path.join(pdir, "*.jar")):
        name = os.path.basename(j)
        if name in want and file_hash(j) == want[name]["sha256"]:
            continue
        if name not in want:
            os.makedirs(aside, exist_ok=True)
            shutil.move(j, os.path.join(aside, name))
        else:
            os.remove(j)
    for name, a in want.items():
        dst = os.path.join(pdir, name)
        if not os.path.exists(dst):
            shutil.copy2(a["pad"], dst)


def apply_settings(cfg, spec, svc):
    props = settings_for(spec, svc)
    if props:
        cf.props_set(os.path.join(core.server_dir(svc), "server.properties"), props)
    pending = []
    for f, keys in ((spec.get("patches") or {}).get("survival" if svc == "staging" else svc) or {}).items():
        full = os.path.join(core.server_dir(svc), f)
        if not os.path.exists(full):
            pending.append(f)
            continue
        for k, val in keys.items():
            cf.yaml_set(full, k.split("."), _yaml_literal(val))
    return pending


# ── beoordelen ─────────────────────────────────────────────────────────────────────────────────

ERROR_PATTERNS = [r"Error occurred while enabling", r"Could not load '?plugins", r"Could not load plugin",
                  r"Unsupported API version", r"is not compatible", r"Encountered an unexpected exception",
                  r"Failed to load plugin", r"Exception in thread \"Server thread\""]


def evaluate(cfg, svc, arts, target):
    """Zijn alle verwachte plugins aan? Kijkt in de serverlog én in `plugins` (rood = uitgeschakeld)."""
    log = tail(core.latest_log(svc), 4000)
    text = "\n".join(log)
    problems, warnings = [], []
    expected = {}
    for name, a in arts.items():
        if target == "staging":
            if not set(a["op"]) & {"survival", "lobby", "staging"}:
                continue
        elif target not in a["op"]:
            continue
        expected[a["pluginnaam"] or name] = name
    try:
        raw = core.rcon(cfg, svc, "plugins", raw=True, timeout=10)
        for pn in net.disabled_plugins(raw):
            if pn in expected:
                why = re.findall(r"\[" + re.escape(pn) + r"\] (.*(?:not supported|unable to start|incompatible|Unsupported).*)", text)
                problems.append(f"{expected[pn]} staat uit" + (f": {why[-1][:160]}" if why else ""))
    except (Fout, OSError):
        pass
    for pn, name in expected.items():
        if not re.search(r"Enabling " + re.escape(pn) + r"\b", text):
            problems.append(f"{name} ({pn}) is niet geladen")
    for line in log:
        if any(re.search(p, line) for p in ERROR_PATTERNS):
            problems.append(line.strip()[-300:])
        elif "WARN" in line and re.search(r"(unsupported|not supported|outdated|incompatible)", line, re.I):
            warnings.append(line.strip()[-200:])
    return list(dict.fromkeys(problems)), warnings[:10]


# ── live zonder herstart: gamerules en rechten ──────────────────────────────────────────────

def _gamerule_value(out):
    m = re.search(r"(?:set to|is currently set to)[:\s]+(\S+)", out)
    return m.group(1).strip().rstrip(".").lower() if m else None


def runtime_plan(cfg, spec):
    """Wat er live nog anders is dan de spec (alleen als de server draait). Geeft een lijst acties."""
    actions = []
    for svc in ("survival", "lobby"):
        rules = (spec.get("gamerules") or {}).get(svc) or {}
        if rules and core.ready(cfg, svc):
            for k, v in rules.items():
                want = str(v).lower()
                try:
                    have = _gamerule_value(core.rcon(cfg, svc, f"gamerule {k}"))
                except (Fout, OSError):
                    have = None
                if have != want:
                    actions.append((svc, f"gamerule {k} {want}", f"gamerule {k} → {want}"))
    applied = deployed().get("rechten") or {}
    want = spec.get("rechten") or {}
    if (want or applied) and core.ready(cfg, "survival"):
        for group in sorted(set(want) | set(applied)):
            have_p, want_p = set(applied.get(group) or []), set(want.get(group) or [])
            if group != "default" and want_p and not have_p:
                actions.append(("survival", f"lp creategroup {group}", f"groep {group} aangemaakt"))
            for perm in sorted(want_p - have_p):
                actions.append(("survival", f"lp group {group} permission set {perm} true", f"{group}: +{perm}"))
            for perm in sorted(have_p - want_p):
                actions.append(("survival", f"lp group {group} permission unset {perm}", f"{group}: -{perm}"))
    return actions


def runtime_apply(cfg, spec, actions):
    done = []
    for svc, command, label in actions:
        out = core.rcon(cfg, svc, command, timeout=20)
        if re.search(r"(Unknown|Incorrect argument|Invalid|error)", out, re.I):
            core.event("MELD", f"{svc}: `{command}` gaf: {out.strip()[:200]}")
        else:
            done.append(label)
    if spec.get("rechten") is not None:
        dep = deployed()
        dep["rechten"] = spec.get("rechten") or {}
        write_json(DEPLOYED, dep)
    return done


# ── staging ────────────────────────────────────────────────────────────────────────────────────

def prepare_staging(cfg, spec, arts, paper_jar):
    """Verse staging: kopie van survival (wereld, configs, plugins), met de nieuwe jar en plugins."""
    core.stop(cfg, "staging")
    sdir = core.server_dir("staging")
    shutil.rmtree(sdir, ignore_errors=True)
    src = core.server_dir("survival")
    live = core.ready(cfg, "survival")
    if live:
        core.rcon(cfg, "survival", "save-off")
        core.rcon(cfg, "survival", "save-all flush", timeout=120)
    try:
        core.copy_tree(src, sdir, ignore=("logs", "session.lock"))
    finally:
        if live:
            core.rcon(cfg, "survival", "save-on")
    core.configure_paper(cfg, "staging")
    staging_arts = {n: dict(a, op=list(set(a["op"]) | ({"staging"} if set(a["op"]) & {"survival", "lobby"} else set())))
                    for n, a in arts.items()}
    sync_plugins("staging", staging_arts, "staging")
    apply_settings(cfg, spec, "staging")
    cf.props_set(os.path.join(sdir, "server.properties"), {"server-port": cfg["poorten"]["staging"],
                                                          "rcon.port": cfg["rcon"]["staging"], "motd": "STAGING"})
    admins = [s for s in core.spelers()["spelers"] if s.get("admin")]
    try:
        ops_list = json.load(open(os.path.join(src, "ops.json")))
    except (OSError, ValueError):
        ops_list = []
    write_json(os.path.join(sdir, "whitelist.json"),
               [{"uuid": o["uuid"], "name": o["name"]} for o in ops_list if o["name"].lower() in {a["naam"].lower() for a in admins}])
    cfg["staging_override"] = {"jar": paper_jar}
    return staging_arts


def staging_test(cfg, spec, arts, paper_jar, keep=False):
    say("  staging-test: kopie van de wereld met de nieuwe versie en plugins…")
    staging_arts = prepare_staging(cfg, spec, arts, paper_jar)
    core.set_desired("staging", "up")
    core.hold("staging", 1800)
    try:
        core.start(cfg, "staging")
        ok = core.wait_ready(cfg, "staging", 900)
        problems, warnings = evaluate(cfg, "staging", staging_arts, "staging") if ok else (["staging start niet"], [])
        if not ok:
            problems += core._tail_log("staging", 15)
        return (ok and not problems), problems, warnings
    finally:
        if not keep:
            core.stop(cfg, "staging")
            core.set_desired("staging", "down")
        core.unhold("staging")
        cfg.pop("staging_override", None)


# ── apply ──────────────────────────────────────────────────────────────────────────────────────

def apply(cfg, spec=None, zonder_staging=False, nu=False, dry_run=False, first=False):
    """Zet de box gelijk aan de spec. Geeft een lijst regels met wat er gebeurde."""
    spec = spec or specmod.load()
    report = []
    with lock("apply"):
        dl.ensure_java(spec["java"])
        cfg["java"] = spec["java"]
        paper = dl.fill_build("paper", spec["minecraft"], spec["paper_build"])
        velocity = dl.fill_build("velocity", spec["velocity"]["versie"], spec["velocity"]["build"])
        paper_jar = dl.ensure_jar(paper)
        dl.ensure_jar(velocity)
        arts = fetch_artifacts(spec, spec["java"])
        changes = plan(cfg, spec, arts)
        touched = [k for k in ("survival", "lobby", "velocity_plugins") if changes[k]]
        if not (changes["paper"] or changes["velocity"] or touched):
            actions = runtime_plan(cfg, spec)
            if not actions or dry_run:
                return [f"live te zetten: {', '.join(a[2] for a in actions)}"] if actions else \
                    ["niets te doen: de box is gelijk aan server.json"]
            return ["live gezet zonder herstart: " + ", ".join(runtime_apply(cfg, spec, actions))]
        report.append("wijzigingen: " + _describe(changes))
        if dry_run:
            return report
        unofficial = [n for n, a in arts.items() if not a["officieel"]]
        if unofficial:
            report.append("let op, niet officieel voor deze versie (getest op staging): " + ", ".join(unofficial))
        paper_touch = changes["paper"] or changes["survival"] or changes["lobby"]
        if paper_touch and not zonder_staging and not first and os.path.exists(os.path.join(core.server_dir("survival"), "server.properties")):
            ok, problems, warnings = staging_test(cfg, spec, arts, paper_jar)
            if not ok:
                raise Fout("staging-test mislukt; live is niet aangeraakt:\n  " + "\n  ".join(problems[:15]))
            report.append("staging-test ok" + (f" (waarschuwingen: {'; '.join(warnings[:3])})" if warnings else ""))
        if paper_touch and not first and core.ready(cfg, "survival"):
            ops.backup(cfg, "pre-apply")
            report.append("backup pre-apply gemaakt")
        new_paper = {k: paper[k] for k in ("version", "build", "name", "sha256", "java", "flags")}
        new_vel = {k: velocity[k] for k in ("version", "build", "name", "sha256", "java")}
        dep = deployed()
        pending = {}
        for svc in ("lobby", "survival"):
            if not (changes["paper"] or changes[svc]):
                continue

            def between(svc=svc):
                cfg["paper"] = new_paper
                cfg["mc"] = spec["minecraft"]
                core.save(cfg)
                sync_plugins(svc, arts, svc)
                pending[svc] = apply_settings(cfg, spec, svc)
            if core.running(svc) or not first:
                ops.restart(cfg, svc, between=between)
            else:
                between()
            dep["plugins"][svc] = {n: {"sha256": a["sha256"], "bestand": os.path.basename(a["pad"])}
                                   for n, a in arts.items() if svc in a["op"]}
            if core.running(svc):
                problems, warnings = evaluate(cfg, svc, arts, svc)
                if problems:
                    core.event("MELD", f"{svc} draait, maar met problemen na apply: {problems[0][:200]}")
                    report.append(f"{svc}: problemen: " + "; ".join(problems[:3]))
            report.append(f"{svc} bijgewerkt")
        for svc, files in pending.items():
            if files and core.running(svc):  # nieuwe plugin: config bestond nog niet; nu wel, dus nog één keer
                ops.restart(cfg, svc, between=lambda svc=svc: apply_settings(cfg, spec, svc))
                report.append(f"{svc}: patches toegepast op {', '.join(files)}")
        if changes["velocity"] or changes["velocity_plugins"]:
            cfg["motd"] = spec.get("motd") or cfg["motd"]

            def vbetween():
                cfg["velocity"] = new_vel
                core.save(cfg)
                core.configure_velocity(cfg)
                sync_plugins("velocity", arts, "velocity")
            if core.running("velocity") and not first:
                online = sum(len(core.players(cfg, s) or []) for s in ("survival", "lobby"))
                if online and not nu:
                    vbetween_deferred(cfg, new_vel, arts)
                    report.append(f"velocity: wordt bijgewerkt zodra niemand online is ({online} nu)")
                    vel_done = False
                else:
                    ops.restart(cfg, "velocity", between=vbetween)
                    report.append("velocity bijgewerkt")
                    vel_done = True
            else:
                vbetween()
                vel_done = True
            if vel_done:
                dep["plugins"]["velocity"] = {n: {"sha256": a["sha256"], "bestand": os.path.basename(a["pad"])}
                                              for n, a in arts.items() if "velocity" in a["op"]}
        dep["spec_commit"] = _git_head()
        dep["tijd"] = now_iso()
        dep["rechten"] = deployed().get("rechten") or {}
        write_json(DEPLOYED, dep)
        core.save(cfg)
        actions = runtime_plan(cfg, spec)
        if actions:
            report.append("live gezet: " + ", ".join(runtime_apply(cfg, spec, actions)))
    return report


def vbetween_deferred(cfg, new_vel, arts):
    write_json(os.path.join(STATE, "velocity-wacht.json"), {"velocity": new_vel, "tijd": now_iso()})


def _describe(c):
    parts = []
    if c["paper"]:
        parts.append("Paper-versie")
    if c["velocity"]:
        parts.append("Velocity-versie")
    for k, label in (("survival", "survival"), ("lobby", "lobby"), ("velocity_plugins", "velocity")):
        if c[k]:
            parts.append(f"{label}: {', '.join(c[k])}")
    return "; ".join(parts)


def _git_head():
    r = subprocess.run(["git", "-C", REPO, "rev-parse", "--short", "HEAD"], capture_output=True, text=True)
    return r.stdout.strip() or None


# ── updates ────────────────────────────────────────────────────────────────────────────────────

def update_check(spec):
    rows = []
    latest_same = dl.fill_build("paper", spec["minecraft"])
    if latest_same and latest_same["build"] != spec["paper_build"]:
        rows.append(("paper", f"build {spec['paper_build']}", f"build {latest_same['build']}", "zelfde Minecraft-versie"))
    newest = dl.fill_latest("paper")
    if newest["version"] != spec["minecraft"]:
        rows.append(("minecraft", spec["minecraft"], newest["version"], "nieuwe Minecraft-versie: alleen op Jelles woord"))
    vel = dl.fill_latest("velocity")
    if (vel["version"], vel["build"]) != (spec["velocity"]["versie"], spec["velocity"]["build"]):
        rows.append(("velocity", f"{spec['velocity']['versie']} ({spec['velocity']['build']})", f"{vel['version']} ({vel['build']})", ""))
    for w in spec.get("wachtlijst") or []:
        try:
            new = specmod.resolve_modrinth(w["project"], spec["minecraft"], w["op"][0])
            if new["officieel"]:
                rows.append((f"wachtlijst: {w.get('naam', w['project'])}", "—", new["versie"],
                             f"nu beschikbaar voor {spec['minecraft']}: mc plugin add {w['project']} --op {','.join(w['op'])}"))
        except Fout:
            pass
    for p in spec["plugins"]:
        if p["bron"] != "modrinth":
            continue
        try:
            target = "velocity" if p["op"] == ["velocity"] else "survival"
            new = specmod.resolve_modrinth(p["project"], spec["minecraft"], target)
        except Fout as e:
            rows.append((p["naam"], p["versie"], "?", str(e)))
            continue
        if new["versie"] != p["versie"]:
            rows.append((p["naam"], p["versie"], new["versie"], "" if new["officieel"] else "niet officieel voor deze versie"))
    return rows


def update_bump(spec, what, mc=None):
    """Zet nieuwere versies in de spec (nog niet live). what: paper, velocity, plugins of alles."""
    changed = []
    if what in ("paper", "alles") or mc:
        b = dl.fill_build("paper", mc or spec["minecraft"])
        if not b:
            raise Fout(f"Paper {mc or spec['minecraft']} heeft geen stabiele build")
        if (b["version"], b["build"]) != (spec["minecraft"], spec["paper_build"]):
            changed.append(f"Paper {spec['minecraft']}#{spec['paper_build']} → {b['version']}#{b['build']}")
            spec["minecraft"], spec["paper_build"] = b["version"], b["build"]
            spec["java"] = max(spec["java"], b["java"])
    if what in ("velocity", "alles"):
        v = dl.fill_latest("velocity")
        if (v["version"], v["build"]) != (spec["velocity"]["versie"], spec["velocity"]["build"]):
            changed.append(f"Velocity {spec['velocity']['versie']} → {v['version']}")
            spec["velocity"] = {"versie": v["version"], "build": v["build"]}
            spec["java"] = max(spec["java"], v["java"])
    if what in ("plugins", "alles") or mc:
        for p in spec["plugins"]:
            if p["bron"] != "modrinth":
                continue
            target = "velocity" if p["op"] == ["velocity"] else "survival"
            new = specmod.resolve_modrinth(p["project"], spec["minecraft"], target)
            if new["versie"] != p["versie"]:
                changed.append(f"{p['naam']} {p['versie']} → {new['versie']}")
                p.update(new)
    return changed
