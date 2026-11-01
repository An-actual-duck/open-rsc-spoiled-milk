#!/usr/bin/env python3
"""Real offline Zamorak casts, critical producers, shared Stagger and execution attribution."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'


def run(command, cwd=ROOT):
    result = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=180)
    if result.returncode:
        print('\n'.join(result.stdout.splitlines()[-45:]))
        raise SystemExit(result.returncode)
    for line in result.stdout.splitlines():
        if 'PASS ' in line:
            print(line)


with tempfile.TemporaryDirectory(prefix='zamorak-hostile-') as directory:
    work = Path(directory)
    runtime = work / 'runtime'
    classes = work / 'classes'
    classes.mkdir()
    run(['python3', str(ROOT / 'tests/myworld/prepare-cleric-well-fixture.py'), str(runtime)])
    cp = ':'.join(str(ROOT / path) for path in ['server/core.jar', 'server/plugins.jar', 'server/lib/*'])
    run([str(JAVA / 'javac'), '-J-Xmx512m', '-J-XX:+UseSerialGC', '-proc:none', '-cp', cp,
         '-sourcepath', str(ROOT / 'server/test'), '-d', str(classes),
         str(ROOT / 'server/test/com/openrsc/server/combat/ZamorakHostileFixture.java')])
    run([str(JAVA / 'java'), '-Xmx512m', '-XX:+UseSerialGC', '-Djava.awt.headless=true',
         '-cp', str(classes) + ':' + cp, 'com.openrsc.server.combat.ZamorakHostileFixture'], runtime)
