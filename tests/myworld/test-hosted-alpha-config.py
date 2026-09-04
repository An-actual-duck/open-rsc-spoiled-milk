#!/usr/bin/env python3
import sys
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
    require(
        hosted,
        "layered_native_terrain_manifest_sha256: "
        "f3489503d2c323e669ab3004345271c6137a58698cc8c0bf0b0fb2587eb52b29",
        "installed World Builder manifest identity",
    )

    runner = RUN_HOSTED.read_text(encoding="utf-8")
    require(runner, "myworld_live_database_path", "hosted DB path resolution")
    require(runner, "myworld_require_live_database_link", "external hosted DB link guard")
    require(
        runner,
        "OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256",
        "hosted manifest environment handoff",
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
