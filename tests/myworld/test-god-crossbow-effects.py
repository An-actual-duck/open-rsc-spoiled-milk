#!/usr/bin/env python3
"""Offline integration of holy-bolt effects. Build the server first."""
from pathlib import Path
import hashlib,json,os,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/god-crossbow-effects'/str(time.time_ns())
BASE.mkdir(parents=True)
classes=BASE/'classes';classes.mkdir()
def run(argv,cwd=ROOT,name='step'):
    result=subprocess.run([str(x) for x in argv],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (BASE/(name+'.log')).write_text(result.stdout)
    if result.returncode:
        print('\n'.join(result.stdout.splitlines()[-30:]));raise SystemExit(result.returncode)
    for line in result.stdout.splitlines():
        if 'PASS ' in line:print(line)
    return result
server=ROOT/'server/core.jar'
for jar in (server,):
    if not jar.is_file():raise SystemExit('Build required: '+str(jar))
run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',BASE/'runtime'],name='prepare')
# Flat synthetic Wilderness sectors; fixtures never load public maps/accounts.
package=BASE/'runtime/package';manifest=json.loads((package/'manifest.json').read_text())
terrain=json.loads((package/'terrain-0.json').read_text())
terrain['runs']=[{'count':48*48,'tile':terrain['runs'][0]['tile']}]
raw=json.dumps(terrain,sort_keys=True).encode()
for sx,sy in [(3,4),(3,8)]:
    (package/f'terrain-pvp-{sx}-{sy}.json').write_bytes(raw)
    manifest['terrainSectors'].append({'worldSpace':'global','level':0,'sectorX':sx,'sectorY':sy,'encoding':'rle-layered-sector-v1','path':f'terrain-pvp-{sx}-{sy}.json','sha256':hashlib.sha256(raw).hexdigest()})
raw=json.dumps(manifest,sort_keys=True).encode();(package/'manifest.json').write_bytes(raw)
import re
config=BASE/'runtime/myworld.conf'
config.write_text(re.sub(r'(?m)^(\s*layered_native_terrain_manifest_sha256:).*$',lambda m:m[1]+' '+hashlib.sha256(raw).hexdigest(),config.read_text()))
cp=':'.join(str(ROOT/p) for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/GodCrossbowEffectsFixture.java'],name='compile-server-test')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.GodCrossbowEffectsFixture'],cwd=BASE/'runtime',name='server')
(BASE/'receipt.json').write_text(json.dumps({'passed':True,'serverSha256':hashlib.sha256(server.read_bytes()).hexdigest(),'liveServerTouched':False},indent=2)+'\n')
print("Evidence:",BASE)
