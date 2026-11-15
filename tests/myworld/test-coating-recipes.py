#!/usr/bin/env python3
"""Offline production loot delivery and disk/JAR item icons; no live state."""
from pathlib import Path
import subprocess,time,json,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/coating-recipes'/str(time.time_ns());BASE.mkdir(parents=True)
classes=BASE/'classes';classes.mkdir()
def run(argv,name,cwd=ROOT):
 r=subprocess.run([str(x)for x in argv],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 (BASE/(name+'.log')).write_text(r.stdout)
 if r.returncode:print('\n'.join(r.stdout.splitlines()[-35:]));raise SystemExit(r.returncode)
 for line in r.stdout.splitlines():
  if 'PASS 'in line:print(line)
run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',BASE/'runtime'],'prepare')
cp=':'.join(str(ROOT/p)for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/CoatingRecipesFixture.java'],'compile-server')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.CoatingRecipesFixture'],'server',BASE/'runtime')
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/BattleaxeCoatingsFixture.java'],'compile-viscous')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.BattleaxeCoatingsFixture'],'viscous',BASE/'runtime')
print('Evidence:',BASE)
