#!/usr/bin/env python3
import unittest
import os
import shutil
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / "scripts/private-server"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class PrivateServerLaunchersTest(unittest.TestCase):
    def test_unix_client_delegates_to_guarded_dev_target(self):
        client = (PRIVATE / "client.sh").read_text(encoding="utf-8")
        self.assertIn(
            'exec "$ROOT_DIR/scripts/run-client.sh" --dev',
            client,
        )
        self.assertNotIn("43605", client)

    def test_unix_server_uses_configured_non_public_endpoint(self):
        server = (PRIVATE / "server.sh").read_text(encoding="utf-8")
        self.assertIn("server_port:", server)
        self.assertIn(
            'printf \'%s\\n\' "$SERVER_PORT" > '
            '"$ROOT_DIR/Client_Base/Cache/port.txt"',
            server,
        )
        self.assertIn(
            "Refusing to launch a private server/client pair on public port",
            server,
        )

    def test_windows_launchers_write_private_port(self):
        client = (PRIVATE / "client.bat").read_text(encoding="utf-8")
        server = (PRIVATE / "server.bat").read_text(encoding="utf-8")
        self.assertIn("localhost:43615", client)
        self.assertNotIn("43605", client)
        self.assertIn("echo 43615", server)
        self.assertNotIn("43605", server)
        for script in (client, server):
            self.assertIn('> "%ROOT_DIR%\\Client_Base\\Cache\\port.txt" echo 43615', script)

    def run_unix_server_fixture(self, port):
        temp = tempfile.TemporaryDirectory(prefix="private-launcher-")
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        script = root / "scripts/private-server/server.sh"
        script.parent.mkdir(parents=True)
        shutil.copy2(PRIVATE / "server.sh", script)
        ant = root / "tools/vendor/apache-ant-1.10.5/bin/ant"
        ant.parent.mkdir(parents=True)
        ant.write_text('#!/bin/sh\nprintf "%s\\n" "$*" > "$LAUNCHER_FIXTURE/ant-called"\n')
        (root / "server/inc/sqlite").mkdir(parents=True)
        (root / "server/inc/sqlite/myworld_seed.db").write_bytes(b"fixture seed, never opened")
        (root / "server/myworld.conf").write_text(f"world:\n\tserver_port: {port}\n")
        cache = root / "Client_Base/Cache"
        cache.mkdir(parents=True)
        (cache / "ip.txt").write_text("public.example.invalid\n")
        (cache / "port.txt").write_text("43605\n")
        fake_bin = root / "bin"
        fake_bin.mkdir()
        java = fake_bin / "java"
        java.write_text("#!/bin/sh\nexit 0\n")
        java.chmod(0o755)
        result = subprocess.run(["sh", str(script)], text=True, capture_output=True,
                                env={**os.environ, "PATH": str(fake_bin) + os.pathsep + os.environ["PATH"],
                                     "LAUNCHER_FIXTURE": str(root)}, timeout=15)
        return root, result

    def test_unix_launch_overwrites_old_cache_with_configured_private_port(self):
        root, result = self.run_unix_server_fixture(43617)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("43617\n", (root / "Client_Base/Cache/port.txt").read_text())
        self.assertEqual("localhost\n", (root / "Client_Base/Cache/ip.txt").read_text())
        self.assertEqual("compile-and-run -DconfFile=myworld\n", (root / "ant-called").read_text())
        self.assertEqual(b"fixture seed, never opened", (root / "server/inc/sqlite/myworld_dev.db").read_bytes())

    def test_unix_launch_refuses_public_port_before_mutating_local_state(self):
        root, result = self.run_unix_server_fixture(43605)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Refusing to launch", result.stdout)
        self.assertEqual("43605\n", (root / "Client_Base/Cache/port.txt").read_text())
        self.assertEqual("public.example.invalid\n", (root / "Client_Base/Cache/ip.txt").read_text())
        self.assertFalse((root / "ant-called").exists())
        self.assertFalse((root / "server/inc/sqlite/myworld_dev.db").exists())

    def test_plan_records_discarded_live_target_and_correction(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "the first attempted schema-v58 route was discarded",
            plan,
        )
        self.assertIn("Private-route correction:", plan)


if __name__ == "__main__":
    unittest.main()
