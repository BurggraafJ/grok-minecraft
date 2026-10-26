"""Unit-tests zonder netwerk en zonder Java. Draai: python3 -m unittest discover -s tests"""
import json
import os
import shutil
import socket
import struct
import sys
import tempfile
import threading
import time
import unittest
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIX = os.path.join(ROOT, "tests", "fixtures")
TMP = tempfile.mkdtemp(prefix="mctest-")
os.environ["MC_HOME"] = TMP
sys.path.insert(0, ROOT)

from mctool import configfiles as cf  # noqa: E402
from mctool import core, net, ops, spec  # noqa: E402
from mctool import apply as applymod  # noqa: E402
from mctool.base import BACKUPS, GB, Fout  # noqa: E402


def copy_fixture(name):
    dst = os.path.join(TMP, "fx-" + name)
    shutil.copy(os.path.join(FIX, name), dst)
    return dst


class ConfigFiles(unittest.TestCase):
    def test_properties(self):
        p = copy_fixture("server.properties")
        self.assertEqual(cf.props_read(p)["level-type"], "minecraft:flat")
        self.assertTrue(cf.props_set(p, {"server-port": 30067, "online-mode": "false", "nieuw": "x"}))
        self.assertFalse(cf.props_set(p, {"server-port": 30067}))
        d = cf.props_read(p)
        self.assertEqual((d["server-port"], d["online-mode"], d["nieuw"]), ("30067", "false", "x"))
        self.assertTrue(open(p).read().startswith("#Minecraft server properties"))

    def test_paper_yaml(self):
        p = copy_fixture("paper-global.yml")
        self.assertEqual(cf.yaml_get(p, ["proxies", "velocity", "enabled"]), "false")
        self.assertTrue(cf.yaml_set(p, ["nieuw-bovenaan"], "true"))  # ontbrekende sleutel bovenaan: toegevoegd
        self.assertEqual(cf.yaml_get(p, ["nieuw-bovenaan"]), "true")
        self.assertTrue(cf.yaml_set(p, ["proxies", "velocity", "enabled"], "true"))
        self.assertTrue(cf.yaml_set(p, ["proxies", "velocity", "secret"], cf.yaml_quote("ab'c")))
        self.assertEqual(cf.yaml_get(p, ["proxies", "velocity", "secret"]), "ab'c")
        self.assertEqual(cf.yaml_get(p, ["proxies", "bungee-cord", "online-mode"]), "true")  # buur ongemoeid
        self.assertFalse(cf.yaml_set(p, ["proxies", "velocity", "enabled"], "true"))
        with self.assertRaises(Fout):
            cf.yaml_set(p, ["proxies", "bestaat-niet"], "1")

    def test_velocity_toml(self):
        p = copy_fixture("velocity.toml")
        self.assertTrue(cf.toml_set(p, None, "bind", cf.toml_str("127.0.0.1:25565")))
        self.assertTrue(cf.toml_set(p, "advanced", "login-ratelimit", "0"))
        cf.toml_replace_section(p, "servers", ['survival = "127.0.0.1:30067"', 'try = ["survival", "lobby"]'])
        cf.toml_replace_section(p, "forced-hosts", [])
        self.assertEqual(cf.toml_get(p, None, "bind"), "127.0.0.1:25565")
        self.assertEqual(cf.toml_get(p, "advanced", "login-ratelimit"), "0")
        self.assertEqual(cf.toml_get(p, "servers", "survival"), "127.0.0.1:30067")
        text = open(p).read()
        self.assertNotIn("factions", text)
        self.assertNotIn("lobby.example.com", text)
        self.assertEqual(cf.toml_get(p, "query", "enabled"), "false")  # secties erna intact


class Memory(unittest.TestCase):
    def test_plan(self):
        p16 = core.memory_plan(16 * GB)
        self.assertTrue(4096 <= p16["survival"] <= 5120)   # 16 GB-box: survival, lobby, kitpvp, velocity en staging
        self.assertIn("kitpvp", p16)
        self.assertLessEqual(sum(p16.values()) + 5 * 450, 16 * 1024 - 3072)
        p16s = core.memory_plan(16 * GB, gedeeld=True)
        self.assertLess(p16s["survival"], p16["survival"])
        self.assertEqual(core.memory_plan(64 * GB)["survival"], 6144)
        self.assertGreaterEqual(core.memory_plan(6 * GB)["survival"], 1536)


class Protocol(unittest.TestCase):
    def test_varint_roundtrip(self):
        for n in (0, 1, 127, 128, 255, 25565, 2097151, 2147483647, -1):
            self.assertEqual(net.read_varint_bytes(net.varint(n))[0], n)

    def test_offline_uuid(self):
        # bekend: OfflinePlayer:Notch → b50ad385-829d-3141-a216-7e7d7539ba7f
        self.assertEqual(net.offline_uuid("Notch").hex(), "b50ad385829d3141a2167e7d7539ba7f")

    def test_disabled_plugins(self):
        raw = "§fServer Plugins (3):\n Bukkit Plugins:\n §8- §aChunky§r, §cCoreProtect§r, §aEssentials"
        self.assertEqual(net.disabled_plugins(raw), ["CoreProtect"])

    def test_rcon_against_fake_server(self):
        srv = socket.socket()
        srv.bind(("127.0.0.1", 0))
        srv.listen(1)
        port = srv.getsockname()[1]

        def serve():
            c, _ = srv.accept()

            def rd():
                n = struct.unpack("<i", c.recv(4))[0]
                b = b""
                while len(b) < n:
                    b += c.recv(n - len(b))
                return struct.unpack("<ii", b[:8]) + (b[8:-2].decode(),)

            def wr(rid, typ, body):
                d = struct.pack("<ii", rid, typ) + body.encode() + b"\0\0"
                c.sendall(struct.pack("<i", len(d)) + d)
            rid, typ, body = rd()
            wr(rid if body == "geheim" else -1, 2, "")
            rid, typ, body = rd()
            wr(rid, 0, "§aEr zijn 0 spelers: ")
            c.close()
        t = threading.Thread(target=serve, daemon=True)
        t.start()
        self.assertEqual(net.rcon(port, "geheim", "list"), "Er zijn 0 spelers: ")
        t.join(2)
        srv.close()


class Spec(unittest.TestCase):
    def test_repo_spec_is_valid(self):
        s = spec.load()
        self.assertTrue(any(p["bron"] == "eigen" for p in s["plugins"]))
        names = [p["naam"] for p in s["plugins"]]
        for must in ("ViaVersion", "ViaBackwards", "LuckPerms", "EssentialsX", "VaultUnlocked", "startersdorp"):
            self.assertIn(must, names)

    def test_validation_errors(self):
        s = spec.load()
        s["plugins"].append({"naam": "Kapot", "bron": "modrinth", "op": ["nergens"]})
        with self.assertRaises(Fout) as e:
            spec.validate(s)
        self.assertIn("Kapot", str(e.exception))
        s = spec.load()
        s["plugins"].append({"naam": "x", "bron": "eigen", "pad": "../etc", "op": ["survival"]})
        with self.assertRaises(Fout):
            spec.validate(s)

    def test_datapack_paths(self):
        s = spec.load()
        self.assertEqual(s["datapacks"]["survival"], ["server/datapacks/startersdorp"])
        s["datapacks"] = {"survival": ["../../etc"]}
        with self.assertRaises(Fout):
            spec.validate(s)

    def test_plugin_name_from_jar(self):
        jar = os.path.join(TMP, "p.jar")
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("plugin.yml", "name: Voorbeeld\nversion: '1'\nmain: a.B\n")
        self.assertEqual(applymod.plugin_name(jar), "Voorbeeld")


class Backups(unittest.TestCase):
    def test_prune(self):
        os.makedirs(BACKUPS, exist_ok=True)
        for f in os.listdir(BACKUPS):
            os.remove(os.path.join(BACKUPS, f))
        t = time.time()
        names = []
        for day in range(1, 11):
            for hour in (1, 12, 23):
                n = f"survival-202610{day:02d}-{hour:02d}0000.tar.gz"
                open(os.path.join(BACKUPS, n), "w").write("x")
                names.append(n)
        for i in range(7):
            open(os.path.join(BACKUPS, f"survival-2026100{i + 1}-050000-pre-apply.tar.gz"), "w").write("x")
        ops.prune({"backup": {"uur": 5, "dag": 3, "label": 2, "max_gb": 20}})
        left = sorted(os.listdir(BACKUPS))
        plain = [n for n in left if "pre-" not in n]
        self.assertEqual(len([n for n in left if "pre-apply" in n]), 2)
        self.assertIn(names[-1], plain)               # nieuwste blijft
        self.assertLessEqual(len(plain), 5 + 3)
        self.assertNotIn(names[0], plain)             # oudste gaat weg
        del t



class PaneelTest(unittest.TestCase):
    def test_members_sluit_logs_en_plugin_jars_uit_maar_niet_libs(self):
        import tempfile
        from mctool import paneel
        d = tempfile.mkdtemp()
        for rel in ("logs/latest.log", "server.jar", "plugins/x.jar", "plugins/LuckPerms/libs/a.jar",
                    "plugins/WorldGuard/cache/c.txt", "world/region/r.0.0.mca", "server.properties"):
            os.makedirs(os.path.dirname(os.path.join(d, rel)) or d, exist_ok=True)
            open(os.path.join(d, rel), "w").close()
        names = sorted(r for r, _ in paneel._members(d, set(paneel.UITSLUIT)))
        self.assertEqual(names, ["plugins/LuckPerms/libs/a.jar", "plugins/WorldGuard/cache/c.txt",
                                 "server.properties", "world/region/r.0.0.mca"])

    def test_admin_gamerules(self):
        from mctool import paneel
        self.assertEqual(paneel._admin_gamerules("aan: true\ngamerules:\n  keep_inventory: true\nx: 1\n"),
                         {"keep_inventory": "true"})


if __name__ == "__main__":
    unittest.main()
