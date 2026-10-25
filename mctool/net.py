"""Praten met de servers: RCON (Paper), de serverlijst-ping (iedereen), een testlogin (alleen offline-test) en de
console van een dienst via zijn FIFO (Velocity heeft geen RCON)."""
import errno
import hashlib
import json
import os
import re
import socket
import struct
import time
import zlib

from .base import Fout

_COLOR = re.compile(r"§[0-9a-fk-orx]", re.I)


def disabled_plugins(raw_plugins_output):
    """Namen die Paper rood toont in de uitvoer van `plugins`: geladen maar uitgeschakeld."""
    return re.findall(r"§c([A-Za-z0-9_.\-]+)", raw_plugins_output or "")


def strip_colors(s):
    return _COLOR.sub("", s or "")


# ── RCON ───────────────────────────────────────────────────────────────────────────────────────

class Rcon:
    def __init__(self, port, password, host="127.0.0.1", timeout=10):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self._send(1, 3, password)
        while True:
            rid, typ, _ = self._recv()
            if rid == -1:
                self.close()
                raise Fout("RCON-wachtwoord geweigerd")
            if rid == 1 and typ == 2:
                break

    def _send(self, rid, typ, body):
        data = struct.pack("<ii", rid, typ) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise Fout("RCON-verbinding gesloten")
            buf += chunk
        return buf

    def _recv(self):
        (length,) = struct.unpack("<i", self._read(4))
        data = self._read(length)
        rid, typ = struct.unpack("<ii", data[:8])
        return rid, typ, data[8:-2].decode("utf-8", "replace")

    def cmd(self, command, raw=False):
        """Voer een commando uit. Lange antwoorden komen in meerdere pakketten; lees door tot het stil is."""
        self._send(2, 2, command)
        rid, _typ, body = self._recv()
        while rid != 2:
            rid, _typ, body = self._recv()
        out = [body]
        if len(body.encode("utf-8")) >= 4000:
            self.sock.settimeout(0.5)
            try:
                while True:
                    rid, _typ, more = self._recv()
                    if rid == 2:
                        out.append(more)
            except (socket.timeout, Fout):
                pass
        text = "".join(out)
        return text if raw else strip_colors(text)

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def rcon(port, password, command, timeout=10, raw=False):
    r = Rcon(port, password, timeout=timeout)
    try:
        return r.cmd(command, raw=raw)
    finally:
        r.close()


# ── Minecraft-protocol (minimaal) ──────────────────────────────────────────────────────────────

def varint(n):
    n &= 0xFFFFFFFF
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out += bytes([b | 0x80])
        else:
            return out + bytes([b])


def read_varint_bytes(data, pos=0):
    num = shift = 0
    while True:
        if pos >= len(data):
            raise ValueError("varint te kort")
        b = data[pos]
        pos += 1
        num |= (b & 0x7F) << shift
        if not b & 0x80:
            break
        shift += 7
        if shift > 35:
            raise ValueError("varint te lang")
    if num & 0x80000000:
        num -= 1 << 32
    return num, pos


def mc_string(s):
    b = s.encode("utf-8")
    return varint(len(b)) + b


def read_string(data, pos):
    n, pos = read_varint_bytes(data, pos)
    return data[pos:pos + n].decode("utf-8", "replace"), pos + n


class Conn:
    def __init__(self, host, port, timeout=8):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.threshold = -1

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("verbinding gesloten")
            buf += chunk
        return buf

    def _read_varint(self):
        num = shift = 0
        while True:
            b = self._read(1)[0]
            num |= (b & 0x7F) << shift
            if not b & 0x80:
                return num
            shift += 7
            if shift > 35:
                raise ValueError("varint te lang")

    def send(self, pid, payload=b""):
        body = varint(pid) + payload
        if self.threshold >= 0:
            if len(body) >= self.threshold:
                body = varint(len(body)) + zlib.compress(body)
            else:
                body = varint(0) + body
        self.sock.sendall(varint(len(body)) + body)

    def recv(self):
        length = self._read_varint()
        data = self._read(length)
        if self.threshold >= 0:
            dlen, pos = read_varint_bytes(data)
            data = zlib.decompress(data[pos:]) if dlen else data[pos:]
        pid, pos = read_varint_bytes(data)
        return pid, data[pos:]

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def _handshake(c, host, port, protocol, next_state):
    c.send(0x00, varint(protocol) + mc_string(host) + struct.pack(">H", port) + varint(next_state))


def status(host, port, protocol=-1, timeout=6):
    """Serverlijst-ping. Geeft het status-JSON met extra sleutel 'ms'."""
    t0 = time.time()
    c = Conn(host, port, timeout)
    try:
        _handshake(c, host, port, protocol, 1)
        c.send(0x00)
        pid, data = c.recv()
        if pid != 0x00:
            raise Fout(f"onverwacht antwoord op status-ping (pakket {pid})")
        text, _ = read_string(data, 0)
        d = json.loads(text)
        d["ms"] = round((time.time() - t0) * 1000)
        return d
    finally:
        c.close()


def describe(d):
    """Korte tekst uit een status-JSON."""
    p = d.get("players") or {}
    names = ", ".join(s.get("name", "?") for s in (p.get("sample") or []))
    v = (d.get("version") or {}).get("name", "?")
    return f"{p.get('online', '?')}/{p.get('max', '?')} spelers{(' (' + names + ')') if names else ''}, versie {v}, {d.get('ms', '?')} ms"


def offline_uuid(name):
    b = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    b[6] = (b[6] & 0x0F) | 0x30
    b[8] = (b[8] & 0x3F) | 0x80
    return bytes(b)


def _parse_known_packs(payload):
    try:
        n, pos = read_varint_bytes(payload)
        if not 0 < n < 64:
            return False
        names = []
        for _ in range(n):
            ns, pos = read_string(payload, pos)
            _id, pos = read_string(payload, pos)
            _ver, pos = read_string(payload, pos)
            names.append(ns)
        return pos == len(payload) and "minecraft" in names
    except (ValueError, IndexError):
        return False


def login_test(host, port, name, protocol, window=8.0):
    """Log in als offline-speler en kijk of de backend achter Velocity de configuratiefase start.

    Alleen bruikbaar als Velocity in offline-test staat (CI). Geeft (ok, uitleg, sporen).
    """
    c = Conn(host, port, timeout=window)
    trace = []
    try:
        _handshake(c, host, port, protocol, 2)
        c.send(0x00, mc_string(name) + offline_uuid(name))
        while True:  # login-fase
            pid, data = c.recv()
            trace.append(f"login:{pid:#04x}:{len(data)}")
            if pid == 0x00:
                return False, "geweigerd bij login: " + read_string(data, 0)[0][:300], trace
            if pid == 0x01:
                return False, "server vraagt encryptie (online-mode staat aan)", trace
            if pid == 0x03:
                c.threshold, _ = read_varint_bytes(data)
            elif pid == 0x04:
                msg_id, _ = read_varint_bytes(data)
                c.send(0x02, varint(msg_id) + b"\x00")
            elif pid == 0x02:
                c.send(0x03)  # Login Acknowledged → configuratie
                break
        packets = total = 0
        deadline = time.time() + window
        c.sock.settimeout(1.0)
        while time.time() < deadline:
            try:
                pid, data = c.recv()
            except socket.timeout:
                if packets >= 3:
                    break
                continue
            except (ConnectionError, OSError):
                return False, "verbinding verbroken in de configuratiefase (backend weigert?)", trace
            packets += 1
            total += len(data)
            trace.append(f"config:{pid:#04x}:{len(data)}")
            text = strip_colors(re.sub(rb"[^\x20-\x7e]+", b" ", data).decode())
            if "nable to connect" in text or "orwarding" in text or "ould not connect" in text:
                return False, "Velocity kon de backend niet bereiken: " + text.strip()[:300], trace
            if pid == 0x04 and len(data) == 8:
                c.send(0x04, data)
            elif _parse_known_packs(data):
                c.send(0x07, data)
            if total > 4000:
                break
        if packets >= 3:
            return True, f"backend antwoordt in de configuratiefase ({packets} pakketten, {total} bytes)", trace
        return False, f"te weinig antwoord van de backend ({packets} pakketten)", trace
    finally:
        c.close()


# ── console via FIFO ───────────────────────────────────────────────────────────────────────────

def console_send(fifo, line):
    try:
        fd = os.open(fifo, os.O_WRONLY | os.O_NONBLOCK)
    except OSError as e:
        if e.errno in (errno.ENXIO, errno.ENOENT):
            raise Fout("de dienst draait niet (geen console)")
        raise
    try:
        os.write(fd, (line.strip() + "\n").encode("utf-8"))
    finally:
        os.close(fd)


def port_open(port, host="127.0.0.1", timeout=1.0):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False
