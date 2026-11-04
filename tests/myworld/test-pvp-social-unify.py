#!/usr/bin/env python3
"""Run native Unify teleports and the actual client player-menu ordering checks."""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT / 'output/tests/pvp-readiness/social-unify')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
java = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
classpath = ':'.join(str(ROOT / p) for p in ['server/core.jar', 'server/plugins.jar', 'server/lib/*'])
with tempfile.TemporaryDirectory(prefix='pvp-social-unify-') as folder:
    temp = Path(folder)
    classes = temp / 'classes'
    classes.mkdir()
    runtime = temp / 'runtime'
    subprocess.run(['python3', str(ROOT / 'tests/myworld/prepare-cleric-well-fixture.py'), str(runtime)], check=True)
    subprocess.run([
        str(java / 'javac'), '-proc:none', '-cp', classpath, '-sourcepath', str(ROOT / 'server/test'),
        '-d', str(classes), str(ROOT / 'server/test/com/openrsc/server/combat/ClericUnifyTeleportFixture.java'),
    ], check=True)
    subprocess.run([
        str(java / 'java'), '-Xmx768m', '-XX:+UseSerialGC', '-Djava.awt.headless=true',
        '-cp', str(classes) + ':' + classpath, 'com.openrsc.server.combat.ClericUnifyTeleportFixture',
    ], cwd=runtime, check=True)
    client = ROOT / 'Client_Base/Open_RSC_Client.jar'
    subprocess.run([str(java / 'javac'), '-cp', str(client), '-d', str(classes),
                    str(ROOT / 'tests/myworld/PvpSocialMenuClientFixture.java')], check=True)
    subprocess.run([str(java / 'java'), '-Xmx256m', '-Djava.awt.headless=true',
                    '-cp', str(classes) + ':' + str(client), 'orsc.PvpSocialMenuClientFixture'], check=True)
