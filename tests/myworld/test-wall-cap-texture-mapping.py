#!/usr/bin/env python3
"""Actual wall producers, lifecycle and raster/mesh/clipped texture mapping checks."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "Client_Base/build"
assert (BUILD / "orsc/graphics/three/WallTextureMapping.class").is_file(), "Build the maintained client first: ./scripts/build-client.sh"
if len(sys.argv) not in (1, 3):
    raise SystemExit("Usage: test-wall-cap-texture-mapping.py [AUTHENTIC_TEXTURE_DIRECTORY FRESH_DIAGNOSTIC_OUTPUT]")
with tempfile.TemporaryDirectory(prefix="wall-cap-texture-tests-") as directory:
    subprocess.run(["javac", "-cp", str(BUILD), "-d", directory,
                    str(ROOT / "tests/myworld/WallCapTextureMappingTest.java")], check=True, cwd=ROOT)
    subprocess.run(["java", "-Xmx384m", "-Djava.awt.headless=true", "-cp", directory + ":" + str(BUILD),
                    "orsc.graphics.three.WallCapTextureMappingTest", *sys.argv[1:]], check=True, cwd=ROOT)
