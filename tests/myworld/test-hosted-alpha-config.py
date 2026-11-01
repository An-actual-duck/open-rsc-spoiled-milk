#!/usr/bin/env python3
import sys
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HOSTED_CONFIG = ROOT / "server" / "myworld-host.conf"
RUN_HOSTED = ROOT / "scripts" / "run-hosted-server.sh"
INIT_HOSTED = ROOT / "scripts" / "init-hosted-sqlite.sh"
GITIGNORE = ROOT / ".gitignore"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, description: str) -> None:
    if snippet not in text:
        fail(f"Missing {description}: {snippet!r}")


def main() -> None:
    hosted = HOSTED_CONFIG.read_text(encoding="utf-8")
    require(hosted, "db_name: spoiled_milk_alpha", "hosted alpha database")
    require(hosted, "server_name: Spoiled Milk", "hosted world name")
    require(hosted, "server_port: 43605", "hosted TCP game port")
    require(hosted, "want_feature_websockets: false", "websocket listener disabled")
    require(hosted, "max_players: 25", "limited alpha player cap")
    require(hosted, "max_players_per_ip: 3", "limited alpha per-IP player cap")
    # The installed source pair is authoritative; the shell's historical pin
    # remains an explicit fallback, not the current hosted map identity.
    selection = subprocess.run(
        [sys.executable, str(ROOT / "scripts/installed-map-selection.py"), str(ROOT)],
        capture_output=True, text=True,
    )
    if selection.returncode:
        fail("Installed source pair is invalid: " + selection.stdout + selection.stderr)
    manifest = json.loads(selection.stdout)["manifestSha256"]
    require(hosted, "layered_native_terrain_manifest_sha256: " + manifest,
            "source-selected World Builder manifest identity")

    runner = RUN_HOSTED.read_text(encoding="utf-8")
    require(runner, "myworld_live_database_path", "hosted DB path resolution")
    require(runner, "myworld_require_live_database_link", "external hosted DB link guard")
    require(
        runner,
        "layered_world_enable_hosted_installed_profile",
        "validated installed-primary handoff without a fixed manifest override",
    )
    require(runner, "Refusing to create a fresh hosted database during server startup.", "hosted DB startup reset guard")
    require(runner, "-DconfFile=myworld-host", "hosted config launch")

    initializer = INIT_HOSTED.read_text(encoding="utf-8")
    require(initializer, "myworld_seed.db", "seed database source")
    require(initializer, "spoiled_milk_alpha.db", "hosted alpha database target")
    require(initializer, "Leaving it untouched.", "non-destructive existing DB guard")

    gitignore = GITIGNORE.read_text(encoding="utf-8")
    require(gitignore, "server/inc/sqlite/spoiled_milk_alpha.db", "ignored live alpha DB")

    print("PASS: hosted alpha config and launch guardrails validated")


if __name__ == "__main__":
    main()
