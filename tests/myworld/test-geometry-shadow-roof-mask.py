#!/usr/bin/env python3
"""Test current roof geometry and receiver coverage against the client dependencies."""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[2]
jar=ROOT / "Client_Base/Open_RSC_Client.jar"
with tempfile.TemporaryDirectory(prefix="geometry-roof-mask-") as work:
    subprocess.run(["javac","-source","8","-target","8","-cp",str(jar),"-d",work,
                    str(ROOT / "Client_Base/src/orsc/graphics/three/World.java"),
                    str(ROOT / "tests/myworld/GeometryShadowRoofMaskFixture.java")],check=True)
    subprocess.run(["java","-cp",work+":"+str(jar),"orsc.GeometryShadowRoofMaskFixture"],check=True)
