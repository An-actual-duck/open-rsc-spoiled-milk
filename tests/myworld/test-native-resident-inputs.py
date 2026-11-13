#!/usr/bin/env python3
"""Run native and browser input-group parity against a complete built client."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-jar", required=True, type=Path)
    args = parser.parse_args()
    jar = args.client_jar.resolve(strict=True)
    suite = Path(subprocess.check_output(["python3", "scripts/workspace-layout.py",
        "path", "tests", "native-resident-inputs", "--mkdir"], cwd=ROOT, text=True).strip())
    out = suite / str(time.time_ns()); out.mkdir()
    classes = out / "classes"; classes.mkdir()
    fixture = ROOT / "tests/myworld/BrowserResidentInputsFixture.java"
    commands = []

    def run(label, command):
        commands.append(command)
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
        (out / (label + ".stdout")).write_text(result.stdout)
        (out / (label + ".stderr")).write_text(result.stderr)
        result.check_returncode()
        return result.stdout

    run("compile", ["javac", "-source", "8", "-target", "8", "-cp", str(jar),
        "-d", str(classes), str(fixture)])
    results = []
    # Browser OFF must stay OFF even when the separate native opt-in is ON.
    for backend, selected, other in [("native", None, False), ("native", True, False), ("native", False, True),
                                      ("browser", True, False), ("browser", False, True)]:
        native = selected if backend == "native" else other
        browser = selected if backend == "browser" else other
        label = backend + "-" + str(selected)
        command = ["java", "-Xmx512m", "-Djava.awt.headless=true",
            "-Dspoiledmilk.browserResidentInputCache=" + str(browser).lower(),
            "-Dspoiledmilk.openglWorldMesh=true",
            "-Dspoiledmilk.openglWorldChunksTexturedVisible=true",
            "-Dspoiledmilk.openglWorldChunksReplacementComposite=true",
            "-Dspoiledmilk.openglWorldChunksResidentObjects=true",
            "-cp", str(classes) + ":" + str(jar), "orsc.BrowserResidentInputsFixture"]
        if native is not None:
            command.insert(3, "-Dspoiledmilk.nativeResidentInputCache=" + str(native).lower())
        if backend == "native": command.append("native")
        output = run(label, command)
        assert output.startswith("PASS "), output
        results.append(dict(backend=backend, selected=selected, output=output.strip()))
    report = dict(passed=True, sourceCommit=subprocess.check_output(["git", "rev-parse", "HEAD"],
        cwd=ROOT, text=True).strip(), clientJar=str(jar),
        jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
        fixtureSha256=hashlib.sha256(fixture.read_bytes()).hexdigest(),
        java=subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True),
        commands=commands, results=results,
        scope="Complete client JAR, exact production uncached input/mesh differential; "
              "actual native/browser reuse, animation, terrain, definitions, membership, "
              "door rotation, signed level/rebase, reset, backend loss and descriptor bound. "
              "No GPU or performance claim.")
    (out / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print("PASS native/browser resident input parity:", out / "result.json")


if __name__ == "__main__":
    main()
