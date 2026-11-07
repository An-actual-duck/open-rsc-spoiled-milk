#!/usr/bin/env python3
"""Actual resident-object builder coordinate/rebase regression; no GL context."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
jar = ROOT / "Client_Base/Open_RSC_Client.jar"
with tempfile.TemporaryDirectory(prefix="geometry-shadow-coordinates-") as work:
    subprocess.run(["javac", "-source", "8", "-target", "8", "-cp", str(jar), "-d", work,
                    str(ROOT / "PC_Client/src/orsc/GeometryShadowCoordinates.java"),
                    str(ROOT / "tests/myworld/GeometryShadowCoordinatesFixture.java")], check=True)
    subprocess.run(["java", "-cp", work + ":" + str(jar), "orsc.GeometryShadowCoordinatesFixture"], check=True)
