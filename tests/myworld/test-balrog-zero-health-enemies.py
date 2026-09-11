#!/usr/bin/env python3
"""Run the equipped Balrog set regression against the built server in isolation."""
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
TESTS = SERVER / "test/com/openrsc/server/combat"


def main():
    assert (SERVER / "core.jar").is_file(), "Build server/core.jar first"
    with tempfile.TemporaryDirectory(prefix="balrog-regression-") as temp:
        classpath = os.pathsep.join([str(SERVER / "core.jar"), str(SERVER / "lib/*")])
        sources = [TESTS / (name + ".java") for name in (
            "CurrentCombatHarness", "MutableGameClock", "SeededGameRandom", "BalrogZeroHealthRegression")]
        subprocess.run(["javac", "-cp", classpath, "-d", temp, *map(str, sources)], check=True)
        subprocess.run(["java", "-cp", os.pathsep.join([temp, classpath]),
                        "com.openrsc.server.combat.BalrogZeroHealthRegression"], cwd=SERVER, check=True)


if __name__ == "__main__":
    main()
