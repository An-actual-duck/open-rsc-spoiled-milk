#!/usr/bin/env python3
"""Compile and run actual static-scene revision publication hooks without a browser."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--client-jar', required=True, type=Path)
args = parser.parse_args()
jar = args.client_jar.resolve()
suite = Path(subprocess.check_output(['python3', str(root / 'scripts/workspace-layout.py'),
    'path', 'tests', 'browser-static-scene-revisions', '--mkdir'], text=True).strip())
classes = suite / 'classes'
classes.mkdir(exist_ok=True)
fixture = Path(__file__).with_name('StaticSceneRevisionFixture.java')
observer_fixture = Path(__file__).with_name('StaticSceneObserverFixture.java')
bootstrap = sorted((root / 'tools/browser-gameplay/src/orsc/browser').glob('*.java'))
compile_command = ['javac', '-source', '8', '-target', '8', '-cp', str(jar), '-d', str(classes), str(fixture), str(observer_fixture)] + list(map(str, bootstrap))
subprocess.run(compile_command, check=True)
command = ['java', '-Xmx256m', '-Djava.awt.headless=true', '-cp', str(classes) + ':' + str(jar), 'orsc.StaticSceneRevisionFixture']
result = subprocess.run(command, text=True, capture_output=True, check=True)
print(result.stdout, end='')
observer = subprocess.run(['java', '-Xmx128m', '-Djava.awt.headless=true', '-cp', str(classes) + ':' + str(jar),
    'orsc.browser.StaticSceneObserverFixture'], text=True, capture_output=True, check=True)
print(observer.stdout, end='')
gates = []
for properties, expected in [([], False), (['-Dspoiledmilk.browserStaticSceneReuse=true'], False),
    (['-Dspoiledmilk.browserBenchmark=true', '-Dspoiledmilk.browserStaticSceneReuse=true'], False),
    (['-Dspoiledmilk.browserBenchmark=true', '-Dspoiledmilk.browserWholeZone=true'], False),
    (['-Dspoiledmilk.browserBenchmark=true', '-Dspoiledmilk.browserWholeZone=true', '-Dspoiledmilk.browserStaticSceneReuse=true'], True)]:
    gate = subprocess.run(['java', '-Xmx128m', '-Djava.awt.headless=true'] + properties +
        ['-cp', str(classes) + ':' + str(jar), 'orsc.StaticSceneRevisionFixture', str(expected).lower()],
        text=True, capture_output=True, check=True)
    gates.append({'properties': properties, 'expected': expected, 'stdout': gate.stdout})
    print(gate.stdout, end='')
(suite / 'result.json').write_text(json.dumps({'jar': str(jar),
    'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'fixtureSha256': hashlib.sha256(fixture.read_bytes()).hexdigest(),
    'commands': [compile_command, command], 'observer': observer.stdout, 'startupGates': gates, 'result': json.loads(result.stdout.strip()), 'stderr': result.stderr}, indent=2) + '\n')
