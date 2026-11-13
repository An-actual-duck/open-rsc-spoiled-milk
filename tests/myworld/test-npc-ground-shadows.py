#!/usr/bin/env python3
"""Run CPU geometry and shared roof-mask checks against a complete client JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--client-jar", type=Path, required=True)
args = parser.parse_args()
jar = args.client_jar.resolve(strict=True)
base = Path(subprocess.check_output(["python3", "scripts/workspace-layout.py", "path",
    "tests", "npc-ground-shadows", "--mkdir"], cwd=ROOT, text=True).strip())
out = base / str(time.time_ns()); out.mkdir()
classes = out / "classes"; classes.mkdir()
commands = []

def run(label, command):
    commands.append(command)
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    (out / (label + ".stdout")).write_text(result.stdout)
    (out / (label + ".stderr")).write_text(result.stderr)
    result.check_returncode()

run("compile", ["javac", "-source", "8", "-target", "8", "-cp", str(jar),
    "-d", str(classes), str(ROOT / "tests/myworld/GeometryShadowRoofMaskFixture.java"),
    str(ROOT / "tests/myworld/NpcGroundShadowFixture.java")])
for fixture in ["GeometryShadowRoofMaskFixture", "NpcGroundShadowFixture"]:
    run(fixture, ["java", "-Xmx512m", "-cp", str(classes) + ":" + str(jar), "orsc." + fixture])
(out / "result.json").write_text(json.dumps(dict(passed=True, jar=str(jar),
    sha256=hashlib.sha256(jar.read_bytes()).hexdigest(), commands=commands,
    java=subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT,text=True),
    scope="CPU production mesh/shared roof policy only; native visual/performance evidence is separate."),indent=2)+"\n")
print("PASS NPC ground-shadow mesh and roof policy:",out / "result.json")
