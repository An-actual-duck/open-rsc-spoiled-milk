#!/usr/bin/env python3
"""Verify the single-client PvP controller against production players and party effects."""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT / 'output/tests/pvp-readiness/teammates')
args = parser.parse_args()
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
java = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
classpath = ':'.join(str(ROOT / p) for p in ['server/core.jar', 'server/plugins.jar', 'server/lib/*'])
with tempfile.TemporaryDirectory(prefix='private-pvp-teammates-') as folder:
    classes = Path(folder)
    subprocess.run([
        str(java / 'javac'), '-proc:none', '-cp', classpath, '-sourcepath', str(ROOT / 'server/test'),
        '-d', str(classes), str(ROOT / 'tests/myworld/PrivatePvpServer.java'),
        str(ROOT / 'server/test/com/openrsc/server/combat/PrivatePvpTeammatesFixture.java'),
    ], check=True)
    subprocess.run([
        str(java / 'java'), '-Xmx768m', '-XX:+UseSerialGC', '-Djava.awt.headless=true',
        '-cp', str(classes) + ':' + classpath,
        'com.openrsc.server.combat.PrivatePvpTeammatesFixture', str(output),
    ], cwd=ROOT / 'server', check=True)
