#!/usr/bin/env python3
"""Exercise built real gameplay/client classes in an isolated synthetic world."""
from pathlib import Path
import hashlib,json,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/scimitar-anticoagulant'/str(time.time_ns())
BASE.mkdir(parents=True);classes=BASE/'classes';classes.mkdir()
def run(argv,name,cwd=ROOT):
    result=subprocess.run([str(x)for x in argv],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (BASE/(name+'.log')).write_text(result.stdout)
    if result.returncode:
        print('\n'.join(result.stdout.splitlines()[-25:]));raise SystemExit(result.returncode)
    for line in result.stdout.splitlines():
        if 'PASS 'in line or line.startswith('BALANCE '):print(line)
server=ROOT/'server/core.jar';client=ROOT/'Client_Base/Open_RSC_Client.jar'
for jar in [server,client]:
    assert jar.is_file(),'Build first: '+str(jar)
run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',BASE/'runtime'],'prepare')
# Add flat, synthetic terrain spanning the actual southern Wilderness boundary.
package=BASE/'runtime/package';manifest=json.loads((package/'manifest.json').read_text())
terrain=json.loads((package/'terrain-0.json').read_text())
terrain['runs']=[{'count':48*48,'tile':terrain['runs'][0]['tile']}]
raw=json.dumps(terrain,sort_keys=True).encode();(package/'terrain-border.json').write_bytes(raw)
manifest['terrainSectors'].append({'worldSpace':'global','level':0,'sectorX':3,'sectorY':8,'encoding':'rle-layered-sector-v1','path':'terrain-border.json','sha256':hashlib.sha256(raw).hexdigest()})
raw=json.dumps(manifest,sort_keys=True).encode();(package/'manifest.json').write_bytes(raw)
import re
config=BASE/'runtime/myworld.conf'
config.write_text(re.sub(r'(?m)^(\s*layered_native_terrain_manifest_sha256:).*$',lambda m:m[1]+' '+hashlib.sha256(raw).hexdigest(),config.read_text()))

cp=':'.join(str(ROOT/p)for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/ScimitarCoatingFixture.java'],'compile-server')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.ScimitarCoatingFixture'],'runtime',BASE/'runtime')
run(['javac','-source','8','-target','8','-cp',client,'-d',classes,ROOT/'tests/myworld/java/ScimitarCoatingClientFixture.java'],'compile-client')
for custom in ['true','false']:
    run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(client),'ScimitarCoatingClientFixture',custom],'client-'+custom)
m=json.loads((ROOT/'config/scimitar-coatings.json').read_text());assert m['acquisition']is None
j=json.loads((ROOT/'server/conf/server/defs/ItemDefsCustom.json').read_text());defs={r['id']:r for r in j.get('items',j.get('item',[]))}
assert set(range(3448,3462))=={r['coated']for r in m['variants']}
for row in m['variants']:
    assert row['coated']in defs
    assert row['severity']==(1 if row['tier']<=4 else 2 if row['tier']<=8 else 3)
receipt={'passed':True,'liveServerTouched':False,'acquisition':None,'serverSha256':hashlib.sha256(server.read_bytes()).hexdigest(),'clientSha256':hashlib.sha256(client.read_bytes()).hexdigest()}
(BASE/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
print('Evidence:',BASE)
