"""Kleine, regelgebaseerde patchers voor server.properties, Paper-YAML en velocity.toml.

Ze wijzigen alleen de sleutels die de tool beheert en laten de rest (en het commentaar) staan, zodat een
nieuwere Paper- of Velocity-versie zijn eigen standaardwaarden kan blijven toevoegen.
"""
import re

from .base import Fout, read_text, write_text


# ── server.properties ──────────────────────────────────────────────────────────────────────────

def _unescape(v):
    return re.sub(r"\\(.)", r"\1", v)


def props_read(path):
    out = {}
    for line in (read_text(path, "") or "").splitlines():
        s = line.strip()
        if not s or s[0] in "#!" or "=" not in s:
            continue
        k, v = s.split("=", 1)
        out[k.strip()] = _unescape(v)
    return out


def props_set(path, updates):
    """Zet sleutels in een .properties-bestand; voegt ontbrekende toe. Geeft True als er iets veranderde."""
    lines = (read_text(path, "") or "").splitlines()
    todo = {k: str(v) for k, v in updates.items()}
    changed = False
    for i, line in enumerate(lines):
        s = line.strip()
        if not s or s[0] in "#!" or "=" not in s:
            continue
        k, v = s.split("=", 1)
        k = k.strip()
        if k in todo:
            if _unescape(v) != todo[k]:
                lines[i] = f"{k}={todo[k]}"
                changed = True
            del todo[k]
    for k, v in todo.items():
        lines.append(f"{k}={v}")
        changed = True
    if changed:
        write_text(path, "\n".join(lines) + "\n")
    return changed


# ── YAML (alleen scalars in blokstijl, zoals Paper ze schrijft) ────────────────────────────────

_YAML_KEY = re.compile(r"^(\s*)([A-Za-z0-9_.\-]+|'[^']*'|\"[^\"]*\"):(\s*)(.*)$")


def yaml_quote(s):
    return "'" + str(s).replace("'", "''") + "'"


def yaml_get(path, keypath):
    for _i, _indent, keys, value in _yaml_walk((read_text(path, "") or "").splitlines()):
        if keys == list(keypath):
            v = value.split(" #", 1)[0].strip()
            if v.startswith("'") and v.endswith("'"):
                return v[1:-1].replace("''", "'")
            if v.startswith('"') and v.endswith('"'):
                return v[1:-1]
            return v
    return None


def _yaml_walk(lines):
    stack = []  # (indent, key)
    for i, line in enumerate(lines):
        if not line.strip() or line.lstrip().startswith("#") or line.lstrip().startswith("- "):
            continue
        m = _YAML_KEY.match(line)
        if not m:
            continue
        indent = len(m.group(1))
        key = m.group(2).strip("'\"")
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        yield i, indent, [k for _, k in stack], m.group(4)


def yaml_set(path, keypath, literal):
    """Zet een bestaande scalar (literal is al YAML, bv. true of 'tekst'). Fout als de sleutel ontbreekt."""
    lines = (read_text(path, "") or "").splitlines()
    for i, indent, keys, value in _yaml_walk(lines):
        if keys == list(keypath):
            if value.strip() == literal:
                return False
            lines[i] = " " * indent + f"{keypath[-1]}: {literal}"
            write_text(path, "\n".join(lines) + "\n")
            return True
    raise Fout(f"{'.'.join(keypath)} niet gevonden in {path}")


# ── TOML (velocity.toml) ───────────────────────────────────────────────────────────────────────

_SECTION = re.compile(r"^\s*\[([A-Za-z0-9_.\-]+)\]\s*$")


def _section_range(lines, section):
    """(start, end): regels van de sectie, zonder de kop. section None = bovenaan, vóór de eerste kop."""
    if section is None:
        end = next((i for i, l in enumerate(lines) if _SECTION.match(l)), len(lines))
        return 0, end
    for i, l in enumerate(lines):
        m = _SECTION.match(l)
        if m and m.group(1) == section:
            end = next((j for j in range(i + 1, len(lines)) if _SECTION.match(lines[j])), len(lines))
            return i + 1, end
    return None


def toml_get(path, section, key):
    lines = (read_text(path, "") or "").splitlines()
    rng = _section_range(lines, section)
    if not rng:
        return None
    pat = re.compile(r"^\s*" + re.escape(key) + r"\s*=\s*(.*)$")
    for l in lines[rng[0]:rng[1]]:
        m = pat.match(l)
        if m:
            v = m.group(1).strip()
            return v[1:-1] if v.startswith('"') and v.endswith('"') else v
    return None


def toml_set(path, section, key, literal):
    lines = (read_text(path, "") or "").splitlines()
    rng = _section_range(lines, section)
    if rng is None:
        lines += ["", f"[{section}]", f"{key} = {literal}"]
        write_text(path, "\n".join(lines) + "\n")
        return True
    pat = re.compile(r"^\s*" + re.escape(key) + r"\s*=")
    for i in range(*rng):
        if pat.match(lines[i]):
            new = f"{key} = {literal}"
            if lines[i].strip() == new:
                return False
            lines[i] = new
            write_text(path, "\n".join(lines) + "\n")
            return True
    lines.insert(rng[1] if section is None else rng[0], f"{key} = {literal}")
    write_text(path, "\n".join(lines) + "\n")
    return True


def toml_replace_section(path, section, body):
    """Vervang de hele inhoud van [section] door body (lijst regels)."""
    lines = (read_text(path, "") or "").splitlines()
    rng = _section_range(lines, section)
    new_body = list(body) + [""]
    if rng is None:
        lines += ["", f"[{section}]"] + new_body
    else:
        if lines[rng[0]:rng[1]] == new_body:
            return False
        lines[rng[0]:rng[1]] = new_body
    write_text(path, "\n".join(lines) + "\n")
    return True


def toml_str(s):
    return '"' + str(s).replace("\\", "\\\\").replace('"', '\\"') + '"'
