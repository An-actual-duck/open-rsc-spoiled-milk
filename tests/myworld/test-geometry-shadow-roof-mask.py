#!/usr/bin/env python3
"""Declared-roof receiver metadata/cache policy; build client first."""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[2]
jar=ROOT / "Client_Base/Open_RSC_Client.jar"
with tempfile.TemporaryDirectory(prefix="geometry-roof-mask-") as work:
    subprocess.run(["javac","-source","8","-target","8","-cp",str(jar),"-d",work,
                    str(ROOT / "tests/myworld/GeometryShadowRoofMaskFixture.java")],check=True)
    subprocess.run(["java","-cp",work+":"+str(jar),"orsc.GeometryShadowRoofMaskFixture"],check=True)
