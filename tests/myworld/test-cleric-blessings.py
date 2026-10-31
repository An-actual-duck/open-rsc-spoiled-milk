#!/usr/bin/env python3
"""Requires matching maintained client/server builds. Sequential, bounded, offline; no owner map/account changes."""
from pathlib import Path
import os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
JAVA=os.environ.get('JAVA_HOME','')
java=str(Path(JAVA)/'bin/java') if JAVA else 'java'
javac=str(Path(JAVA)/'bin/javac') if JAVA else 'javac'
def run(command,cwd=ROOT,quiet=False):
 if not quiet:return subprocess.run(command,cwd=cwd,check=True)
 completed=subprocess.run(command,cwd=cwd,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 if completed.returncode:
  print('\n'.join(completed.stdout.splitlines()[-25:]));raise SystemExit(completed.returncode)
 print('\n'.join(line for line in completed.stdout.splitlines() if 'PASS ' in line))
with tempfile.TemporaryDirectory(prefix='cleric-blessings-') as folder:
 tmp=Path(folder);classes=tmp/'classes';classes.mkdir()
 fixture=tmp/'runtime';run(['python3',str(ROOT/'tests/myworld/prepare-cleric-well-fixture.py'),str(fixture)],quiet=True)
 cp=':'.join(str(ROOT/p) for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
 run([javac,'-proc:none','-cp',cp,'-sourcepath',str(ROOT/'server/test'),'-d',str(classes),str(ROOT/'server/test/com/openrsc/server/combat/ClericBlessingsFixture.java')])
 run([java,'-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.ClericBlessingsFixture','/tmp/cleric-blessings-schema3.bin'],cwd=fixture,quiet=True)
