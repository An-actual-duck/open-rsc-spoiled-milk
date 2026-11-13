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
parser.add_argument("--gpu", action="store_true", help="Also run controlled native hardware pixel/policy checks in a hidden window")
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

compile_command = ["javac", "-source", "8", "-target", "8", "-cp", str(jar),
    "-d", str(classes), str(ROOT / "tests/myworld/GeometryShadowRoofMaskFixture.java"),
    str(ROOT / "tests/myworld/NpcGroundShadowFixture.java")]
if args.gpu:
    compile_command.append(str(ROOT / "tests/myworld/CharacterGroundShadowGpuFixture.java"))
run("compile", compile_command)
for fixture in ["GeometryShadowRoofMaskFixture", "NpcGroundShadowFixture"]:
    run(fixture, ["java", "-Xmx512m", "-cp", str(classes) + ":" + str(jar), "orsc." + fixture])
if args.gpu:
    common = Path(subprocess.check_output(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"], cwd=ROOT, text=True).strip())
    run("hardware", ["flock", "-w", "10", str(common / "ai-workspaces/renderer-gpu-benchmark.lock"),
        "java", "-Xmx512m", "-cp", str(classes) + ":" + str(jar),
        "orsc.CharacterGroundShadowGpuFixture", str(out / "gpu")])
(out / "result.json").write_text(json.dumps(dict(passed=True, jar=str(jar),
    sha256=hashlib.sha256(jar.read_bytes()).hexdigest(), commands=commands,
    java=subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT,text=True),
    hardwarePixels=args.gpu,
    scope="Production mesh/shared roof policy; optional controlled native GPU pixels. Real-world visual/performance evidence is separate."),indent=2)+"\n")
print("PASS NPC ground-shadow mesh and roof policy:",out / "result.json")
