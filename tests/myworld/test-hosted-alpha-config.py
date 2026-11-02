#!/usr/bin/env python3
import sys
import json
import subprocess
import os
import re
import shutil
import tempfile
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



def test_actual_hosted_guard() -> None:
    # Run the real shell preflight against immutable copies, without launching a
    # server, opening a DB, or modifying the selected source map.
    selected = json.loads(subprocess.check_output(
        [sys.executable, str(ROOT / "scripts/installed-map-selection.py"), str(ROOT)],
        text=True,
    ))
    with tempfile.TemporaryDirectory(prefix="hosted-manifest-guard-") as temp:
        fixture = Path(temp)
        for relative in (
            "server/build.xml", "server/myworld-host.conf",
            "server/world-builder-configs/primary.json",
            "Client_Base/world-builder-configs/installed-client.json",
            "scripts/installed-map-selection.py",
        ):
            target = fixture / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, target)
        for role in ("serverPackage", "clientPackage"):
            source = Path(selected[role])
            shutil.copytree(source, fixture / source.relative_to(ROOT))
        config = fixture / "server/myworld-host.conf"
        original = config.read_text()
        profile = fixture / "Client_Base/world-builder-configs/installed-client.json"
        profile_bytes = profile.read_bytes()
        primary = fixture / "server/world-builder-configs/primary.json"
        primary_bytes = primary.read_bytes()

        def run_guard():
            return subprocess.run(
                ["bash", "-euc", 'source "$1"; myworld_require_hosted_conf myworld-host',
                 "fixture", str(ROOT / "scripts/lib/myworld-common.sh")],
                cwd=fixture, env={**os.environ, "ROOT_DIR": str(fixture)},
                text=True, capture_output=True, timeout=30,
            )

        result = run_guard()
        if result.returncode:
            fail("actual selected-map hosted guard failed: " + result.stdout + result.stderr)
        for key, wrong in (
            ("layered_native_terrain_manifest_sha256", "0" * 64),
            ("server_port", "43623"),
            ("db_name", "myworld_dev"),
            ("want_layered_native_terrain_atomic_activation", "false"),
        ):
            config.write_text(re.sub(
                r"(?m)^([ \t]*" + re.escape(key) + r":)[^\n]*",
                lambda match: match[1] + " " + wrong, original,
            ))
            if run_guard().returncode == 0:
                fail("actual hosted guard accepted invalid " + key)
        config.write_text(original)
        profile.write_text("{malformed")
        if run_guard().returncode == 0:
            fail("malformed selector was accepted")
        profile.write_bytes(profile_bytes)
        primary.unlink()
        if run_guard().returncode == 0:
            fail("missing PRIMARY was accepted")
        primary.symlink_to(ROOT / "server/world-builder-configs/primary.json")
        if run_guard().returncode == 0:
            fail("symlink PRIMARY was accepted")
        primary.unlink()
        primary.write_bytes(primary_bytes)
        if run_guard().returncode:
            fail("restored valid hosted selection was rejected")
    print("PASS actual hosted guard: selected map accepted; bad identity, port, DB, gate and selectors rejected")


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

    test_actual_hosted_guard()
    print("PASS: hosted alpha config and launch guardrails validated")


if __name__ == "__main__":
    main()
