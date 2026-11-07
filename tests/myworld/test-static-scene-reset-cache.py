#!/usr/bin/env python3
"""Actual Player reset/updater scan-cache regression; no server listener or DB.
Run scripts/build-server.sh first so core.jar reflects the maintained source.
"""
from pathlib import Path
import os
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="static-scene-reset-") as work:
    core = str(ROOT / "server/core.jar")
    libs = str(ROOT / "server/lib/*")
    subprocess.run(["javac", "-source", "8", "-target", "8", "-cp", os.pathsep.join([core, libs]),
                    "-d", work, str(ROOT / "tests/myworld/StaticSceneResetCacheFixture.java")], check=True)
    run = subprocess.run(["java", "-cp", os.pathsep.join([work, libs, core]), "StaticSceneResetCacheFixture"],
                         cwd=work, capture_output=True, text=True)
    if run.returncode != 0 or "PASS same-position reset" not in run.stdout:
        raise AssertionError(run.stdout + run.stderr)
    print(run.stdout.strip())
