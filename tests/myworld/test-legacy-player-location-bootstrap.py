#!/usr/bin/env python3
"""Create a fresh synthetic native world + SQLite rows, then exercise full logins."""
import argparse,hashlib,json,os,pathlib,re,subprocess,sys,uuid
root=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--reproduce',action='store_true');a=p.parse_args()
out=root/'output'/('player-location-bootstrap-'+uuid.uuid4().hex[:10]);out.mkdir(parents=True);runtime=out/'runtime'
subprocess.run([sys.executable,str(root/'tests/myworld/prepare-cleric-well-fixture.py'),str(runtime)],check=True,stdout=subprocess.DEVNULL)
package=runtime/'package';manifest={'schemaVersion':1,'packageType':'layered-world','coordinateModel':'signed-layered-v1','packageId':'location-login-fixture','packageVersion':'1.0.0','storage':{'sectorSize':48,'presentationChunkSize':24},'worldSpaces':[{'id':'global','kind':'static'}],'levels':[],'terrainSectors':[],'placementSets':[]}
for level in [0,1,2,-1,-3]:
    name=f'login-terrain-{level}.json';tile={'elevation':0,'texture':93,'overlay':3,'roof':0,'verticalWall':0,'horizontalWall':0,'diagonalWall':0}
    data=json.dumps({'schemaVersion':1,'encoding':'rle-layered-sector-v1','size':48,'tileOrder':'x-major-y-minor','runs':[{'count':2304,'tile':tile}]}).encode();(package/name).write_bytes(data)
    manifest['levels'].append({'worldSpace':'global','level':level,'name':f'Login {level}','role':'test'})
    manifest['terrainSectors'].append({'worldSpace':'global','level':level,'sectorX':2,'sectorY':14,'encoding':'rle-layered-sector-v1','path':name,'sha256':hashlib.sha256(data).hexdigest()})
    placement=f'login-placements-{level}.json';data=json.dumps({'schemaVersion':5,'encoding':'layered-world-placements-v5','worldSpace':'global','level':level,'npcs':[],'groundItems':[],'scenery':[],'boundaries':[]}).encode();(package/placement).write_bytes(data)
    manifest['placementSets'].append({'id':f'login-placements-{level}'.replace('--','-minus-'),'worldSpace':'global','level':level,'encoding':'layered-world-placements-v5','path':placement,'sha256':hashlib.sha256(data).hexdigest()})
data=json.dumps(manifest).encode();(package/'manifest.json').write_bytes(data);config=(runtime/'myworld.conf').read_text()
for key,value in {'layered_native_terrain_manifest_sha256':hashlib.sha256(data).hexdigest(),'respawn_location_x':'110','respawn_location_y':'678'}.items():
    config=re.sub(r'(?m)^([ \t]*)'+key+r':.*$',lambda m:f'{m[1]}{key}: {value}',config)
(runtime/'myworld.conf').write_text(config)
env=os.environ.copy();env['LOCATION_TEST_JDBC']='jdbc:sqlite:'+str(out/'location-test.sqlite')
cp=os.pathsep.join(str(x) for x in [root/'output/combat-test/test-classes',root/'output/combat-test/core-classes',*sorted((root/'server/lib').glob('*.jar'))])
java=pathlib.Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin/java';log=out/'login.log'
with log.open('w') as f:
    result=subprocess.run([str(java),'-Xmx512m','-XX:+UseSerialGC','-cp',cp,'com.openrsc.server.combat.LegacyPlayerLocationBootstrapFixture',*(['reproduce'] if a.reproduce else [])],cwd=runtime,env=env,stdout=f,stderr=subprocess.STDOUT,timeout=120)
text=log.read_text();marker='REPRODUCED ordinary account load:' if a.reproduce else 'PASS legacy player location bootstrap:'
assert result.returncode==0 and marker in text and 'java.lang.AssertionError' not in text and 'Exception while executing GameTickEvent' not in text,text[-8000:]
for line in text.splitlines():
    if marker in line:print(line[line.index(marker):])
print('Evidence:',out)
