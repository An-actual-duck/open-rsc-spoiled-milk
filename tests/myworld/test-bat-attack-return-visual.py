#!/usr/bin/env python3
"""Verify bat drain travel with the built client; no window, world, assets or accounts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--client-jar", type=Path, default=ROOT / "Client_Base/Open_RSC_Client.jar")
parser.add_argument("--output", type=Path, default=ROOT / "output/tests/duskwing-attack-return/client")
args = parser.parse_args()
jar, output = args.client_jar.resolve(), args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
java = Path(os.environ.get("JAVA_HOME", "/home/justin/jdk8u482-b08")) / "bin"
sheet = "legacy-animations/Projectiles/summon-bat-vampirism-reverse/summon-bat-vampirism-reverse.png"
sheet_bytes = (ROOT / "dev/myworld/assets/runtime" / sheet).read_bytes()
with zipfile.ZipFile(jar) as archive:
    assert archive.read("myworld-assets/" + sheet) == sheet_bytes, "built client must preserve the approved bat sheet"
source = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
for arguments in ("var5, var8", "var7, var10", "var6, var9"):
    assert source.count(f"getProjectileTravelCoordinate(projectileDef, {arguments}, var3.projectileRange)") == 2, \
        "both NPC and player projectile loops must use visual travel on all three axes"
assert source.count("var3.projectileMirrored = shouldMirrorProjectile(projectileDef, var16, var3);") == 2
assert source.count("getProjectileSceneSpriteIndex(projectileDef, var3.projectileRange,") == 2, \
    "frame animation must continue using original lifetime rather than reversed positional progress"
subprocess.run([str(java / "javac"), "-J-Xmx128m", "-cp", str(jar), "-d", str(output),
                str(ROOT / "tests/myworld/BatAttackReturnClientFixture.java")], check=True)
result = subprocess.run([str(java / "java"), "-Xmx256m", "-XX:+UseSerialGC", "-Djava.awt.headless=true",
                         "-cp", str(output) + ":" + str(jar), "orsc.BatAttackReturnClientFixture", str(jar)],
                        text=True, capture_output=True)
(output / "runtime.log").write_text(result.stdout + result.stderr)
print(result.stdout, end="")
if result.returncode:
    print(result.stderr, end="")
    raise SystemExit(result.returncode)
assert "PASS " in result.stdout
(output / "receipt.json").write_text(json.dumps({"clientJar": str(jar),
    "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "passed": True,
    "batSheetSha256": hashlib.sha256(sheet_bytes).hexdigest(),
    "scope": "actual client helpers and packet parser; headless, no graphical owner review"}, indent=2) + "\n")
