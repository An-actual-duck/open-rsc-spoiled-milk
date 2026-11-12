#!/usr/bin/env python3
"""Run actual full-JAR boundary hooks and pure observer validation, without a UI."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
p = argparse.ArgumentParser()
p.add_argument('--jar', type=Path, required=True)
p.add_argument('--out', type=Path, required=True)
a = p.parse_args()
jar = a.jar.resolve()
out = a.out.resolve()
out.mkdir(parents=True, exist_ok=False)
classes = out / 'classes'
classes.mkdir()
fixture = ROOT / 'tests/myworld/browser-region-handoff/RegionHandoffTraceFixture.java'
source_files = [ROOT / 'Client_Base/src/orsc/BrowserRegionHandoffTrace.java',
                ROOT / 'Client_Base/src/orsc/BoundaryLoadingDiagnostics.java', fixture,
                ROOT / 'tools/browser-gameplay/web/browser-region-handoff-profile.js']

def run(args, name):
    r = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    (out / (name + '.stdout')).write_text(r.stdout)
    (out / (name + '.stderr')).write_text(r.stderr)
    if r.returncode:
        raise RuntimeError(name + ' failed; see preserved stdout/stderr')
    print(name, r.stdout.strip())
    return r.stdout

run(['javac', '-source', '8', '-target', '8', '-cp', str(jar), '-d', str(classes), str(fixture)], 'compile-fixture')
for benchmark, profile in [(False, False), (False, True), (True, False), (True, True)]:
    run(['java', '-Dspoiledmilk.browserBenchmark=' + str(benchmark).lower(),
         '-Dspoiledmilk.browserLoadProfile=' + str(profile).lower(), '-cp', str(classes) + ':' + str(jar),
         'orsc.RegionHandoffTraceFixture', str(benchmark and profile).lower()],
        'policy-' + str(int(benchmark)) + str(int(profile)))
run(['node', '--experimental-default-type=module', 'tests/myworld/browser-region-handoff/collector.mjs'], 'collector')
report = {'passed': True, 'fullJar': str(jar), 'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
          'testOnlyClassesCompiled': True, 'browserStarted': False, 'gpuExecuted': False,
          'productionClassOverlay': False,
          'sources': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in source_files}}
(out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
