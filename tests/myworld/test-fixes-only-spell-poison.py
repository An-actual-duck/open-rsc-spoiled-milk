#!/usr/bin/env python3
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / 'server'
with tempfile.TemporaryDirectory(prefix='fixes-spell-poison-') as temp:
    classpath = os.pathsep.join([str(SERVER / 'core.jar'), str(SERVER / 'lib/*')])
    source = SERVER / 'test/com/openrsc/server/combat/FixesOnlySpellPoisonFixture.java'
    subprocess.run(['javac', '-cp', classpath, '-d', temp, str(source)], check=True)
    subprocess.run(['java', '-cp', temp + os.pathsep + classpath,
                    'com.openrsc.server.combat.FixesOnlySpellPoisonFixture'], check=True)
