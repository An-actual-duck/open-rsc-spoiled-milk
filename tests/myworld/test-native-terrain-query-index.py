#!/usr/bin/env python3
"""Compare the native opt-in with frozen terrain results; no game or GPU."""
import hashlib
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
BASE = "42e8c2a32eb0ecb128e1fee704a566e78dbc5702"
SOURCES = ["Client_Base/src/orsc/NativeLayeredTerrainChunk.java",
           "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java",
           "Client_Base/src/com/openrsc/client/model/Tile.java"]
POLICY = """package orsc;
public final class NativeTerrainQueryPolicyFixture {
 public static void main(String[] args) {
  boolean browser=Boolean.getBoolean("spoiledmilk.browserTerrainQueryIndex");
  boolean nativeIndex=Boolean.getBoolean("spoiledmilk.nativeTerrainQueryIndex");
  if(NativeLayeredTerrainSnapshot.isTerrainQueryIndexEnabled()!=(browser||nativeIndex)
    ||NativeLayeredTerrainSnapshot.isBrowserTerrainQueryIndexEnabled()!=browser)
   throw new AssertionError("Terrain query startup policy differs");
  System.out.println("PASS native="+nativeIndex+" browser="+browser);
 }
}
"""


def main():
    base = Path(subprocess.check_output(["python3", "scripts/workspace-layout.py",
        "path", "tests", "native-terrain-query-index", "--mkdir"], cwd=ROOT, text=True).strip())
    out = base / ("reference-" + str(time.time_ns())); out.mkdir()
    fixture = "tests/myworld/NativeTerrainQueryIndependentFixture.java"
    frozen_fixture = subprocess.check_output(["git", "show", BASE + ":" + fixture], cwd=ROOT)
    sources = {}; outputs = {}; commands = []

    def run(name, command):
        commands.append(command)
        result = subprocess.run(command, cwd=ROOT, capture_output=True)
        (out / (name + ".stdout")).write_bytes(result.stdout)
        (out / (name + ".stderr")).write_bytes(result.stderr)
        result.check_returncode()
        return result.stdout

    for label in ["baseline", "candidate"]:
        folder = out / label; folder.mkdir(); classes = folder / "classes"; classes.mkdir()
        files = []; sources[label] = {}
        for path in SOURCES:
            data = (subprocess.check_output(["git", "show", BASE + ":" + path], cwd=ROOT)
                    if label == "baseline" else (ROOT / path).read_bytes())
            target = folder / Path(path).name; target.write_bytes(data); files.append(str(target))
            sources[label][path] = hashlib.sha256(data).hexdigest()
        target = folder / Path(fixture).name; target.write_bytes(frozen_fixture); files.append(str(target))
        if label == "candidate":
            target = folder / "NativeTerrainQueryPolicyFixture.java"
            target.write_text(POLICY); files.append(str(target))
        run(label + "-compile", ["javac", "-source", "8", "-target", "8", "-d", str(classes), *files])
        modes = [(False, False)] if label == "baseline" else [(False, False), (True, False), (False, True), (True, True)]
        for native_index, browser in modes:
            name = label + "-native-" + str(native_index) + "-browser-" + str(browser)
            command = ["java", "-Xmx512m", "-Djava.awt.headless=true",
                "-Dspoiledmilk.nativeTerrainQueryIndex=" + str(native_index).lower(),
                "-Dspoiledmilk.browserTerrainQueryIndex=" + str(browser).lower(), "-cp", str(classes)]
            if label == "candidate":
                run(name + "-policy", command + ["orsc.NativeTerrainQueryPolicyFixture"])
            outputs[name] = run(name, command + ["orsc.NativeTerrainQueryIndependentFixture"]
                                + (["metadata"] if label == "candidate" else []))
    expected = next(iter(outputs.values()))
    assert expected.startswith(b"SNAPSHOT ") and len(expected.strip()) == 73
    assert all(value == expected for value in outputs.values())
    report = dict(passed=True, cpuOnly=True, baselineCommit=BASE,
        candidateHead=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        sources=sources, fixtureSha256=hashlib.sha256(frozen_fixture).hexdigest(),
        snapshot=expected.decode().strip(), commands=commands,
        scope="Frozen independent ordered bounds/Tile/scalar/exception oracle, protocols3-10, "
              "mixed/void slots, signed levels and edge integers. Default/native/browser/both policy; no FPS claim.")
    (out / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print("PASS native terrain query reference and startup policy:", out / "result.json")


if __name__ == "__main__":
    main()
