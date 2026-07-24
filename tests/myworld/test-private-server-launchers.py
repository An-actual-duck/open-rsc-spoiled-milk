#!/usr/bin/env python3
import unittest
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

    def test_windows_launchers_and_cache_target_private_port(self):
        client = (PRIVATE / "client.bat").read_text(encoding="utf-8")
        server = (PRIVATE / "server.bat").read_text(encoding="utf-8")
        cache = (
            ROOT / "Client_Base/Cache/port.txt"
        ).read_text(encoding="utf-8")
        self.assertIn("localhost:43615", client)
        self.assertNotIn("43605", client)
        self.assertIn("echo 43615", server)
        self.assertNotIn("43605", server)
        self.assertEqual("43615\n", cache)

    def test_plan_records_discarded_live_target_and_correction(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "the first attempted schema-v58 route was discarded",
            plan,
        )
        self.assertIn("Private-route correction:", plan)


if __name__ == "__main__":
    unittest.main()
