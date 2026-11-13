#!/usr/bin/env python3
"""Replay real travel callbacks on the installed map without opening a server."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--out", type=Path, required=True, help="Fresh directory under output/tests/")
parser.add_argument("--baseline-plugins", type=Path, help="Optional preserved pre-fix plugins JAR")
args = parser.parse_args()
out = args.out.resolve()
if not out.is_relative_to((ROOT / "output/tests").resolve()) or out.exists():
    parser.error("--out must be a fresh descendant of output/tests/")
baseline = args.baseline_plugins.resolve() if args.baseline_plugins else None
if baseline and not baseline.is_file():
    parser.error("Baseline JAR is missing")
out.mkdir(parents=True)


def production_sources():
    digest = hashlib.sha256()
    paths = sorted(path for root in (ROOT / "server/src", ROOT / "server/plugins")
                   for path in root.rglob("*.java"))
    for path in paths:
        digest.update(str(path.relative_to(ROOT)).encode() + b"\0")
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    return {"javaFiles": len(paths), "sha256": digest.hexdigest()}


tested_sources = production_sources()


def run(command, log, cwd=ROOT, expected=None):
    with (out / log).open("w") as stream:
        subprocess.run(command, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT, check=True)
    text = (out / log).read_text()
    if expected is not None and expected not in text:
        raise AssertionError(f"Missing completion receipt in {log}")


run(["nice", "-n", "10", "bash", "scripts/build-server.sh"], "build.log")
run([sys.executable, str(ROOT / "tests/myworld/prepare-layer-transition-audit-fixture.py"),
     str(out / "runtime")], "prepare.log")
classes = out / "classes"
classes.mkdir()
run(["nice", "-n", "10", "javac", "-cp", "server/core.jar:server/plugins.jar:server/lib/*",
     "-d", str(classes), "server/test/com/openrsc/server/io/LayerTransitionAuditIntegrationTest.java"],
    "compile-fixture.log")
lib = ROOT / "server/lib"
prefix = [str(lib / name) for name in (
    "log4j-api-2.17.0.jar", "log4j-core-2.17.0.jar", "log4j-iostreams-2.17.0.jar")]


def execute(plugins, mode):
    # Production logging dependencies precede the fat core's old bundled copy.
    cp = ":".join(prefix + [str(classes), str(ROOT / "server/core-gameplay-overlay.jar"),
                           str(ROOT / "server/core.jar"), str(plugins), str(lib / "*")])
    command = ["nice", "-n", "10", "java", "-Xmx768m", "-XX:+UseSerialGC", "-cp", cp,
               "com.openrsc.server.io.LayerTransitionAuditIntegrationTest"]
    if mode == "baseline":
        command.append("baseline")
    run(command, mode + ".log", out / "runtime", "Layer transition audit cases=")
    return json.loads((out / "runtime" / (mode + "-results.json")).read_text())


before = execute(baseline, "baseline") if baseline else None
after = execute(ROOT / "server/plugins.jar", "candidate")
assert after and all(row["passed"] for row in after)
if before:
    assert [r["label"] for r in before] == [r["label"] for r in after]
    assert any(not r["passed"] for r in before)
assert production_sources() == tested_sources, "Production Java changed during replay"
receipt = re.search(r"Layer transition audit cases=(\d+) checks=(\d+) failures=(\d+)",
                    (out / "candidate.log").read_text())
assert receipt and int(receipt[1]) == len(after) and int(receipt[3]) == 0
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
report = {
    "passed": True,
    "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
    "sourceWorktreeStatus": subprocess.check_output(
        ["git", "status", "--porcelain", "--", "server/src", "server/plugins",
         "server/test/com/openrsc/server/io/LayerTransitionAuditIntegrationTest.java"],
        cwd=ROOT, text=True).strip(),
    "productionSources": tested_sources,
    "cases": len(after),
    "checks": int(receipt[2]),
    "baselinePluginsSha256": sha(baseline) if baseline else None,
    "baselineFailures": sum(not row["passed"] for row in before) if before else None,
    "artifacts": {name: sha(ROOT / "server" / name)
                  for name in ("core.jar", "plugins.jar", "core-gameplay-overlay.jar")},
    "fixtureSourceSha256": sha(ROOT / "server/test/com/openrsc/server/io/LayerTransitionAuditIntegrationTest.java"),
    "sourceWorld": json.loads((out / "runtime/source-world.json").read_text()),
    "scope": "Offline actual callback/Player transitions; original terrain and placements. "
             "Script waits elided, menus scripted, Arhein temporarily occupied for the ship branch. "
             "No listeners, accounts, database initialization, client or GPU. No full packet/approach proof.",
}
(out / "validation.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
