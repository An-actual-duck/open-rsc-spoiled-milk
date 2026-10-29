#!/usr/bin/env python3
"""Prepare a fresh immutable native map fixture, never accounts/owner maps/credentials."""
from pathlib import Path
import hashlib,json,re,shutil,sys
root=Path(__file__).resolve().parents[2];target=Path(sys.argv[1]).resolve()
assert not target.exists(),'Use a fresh fixture folder'
target.mkdir(parents=True);shutil.copytree(root/'server/conf',target/'conf')
# These are checked-in SQL schema/patch files, not a player database.
shutil.copytree(root/'server/database',target/'database')
for n in ['badwords.txt','goodwords.txt','globalrules.txt','alertwords.txt']:shutil.copy2(root/'server'/n,target/n)
(target/'connections.conf').write_text('database:\n\tdb_type: MYSQL\n\tdb_host: 127.0.0.1\n\tdb_user: isolated_test\n\tdb_pass: unused\n')
package=target/'package';package.mkdir()
def write(name,value):
 data=json.dumps(value,sort_keys=True).encode();(package/name).write_bytes(data);return hashlib.sha256(data).hexdigest()
base={'elevation':0,'texture':93,'overlay':3,'roof':0,'verticalWall':0,'horizontalWall':0,'diagonalWall':0}
manifest={'schemaVersion':1,'packageType':'layered-world','coordinateModel':'signed-layered-v1','packageId':'well-test','packageVersion':'1.0.0','storage':{'sectorSize':48,'presentationChunkSize':24},'worldSpaces':[{'id':'global','kind':'static'}],'levels':[],'terrainSectors':[],'placementSets':[]}
for level in [0,2,-3]:
 manifest['levels'].append({'worldSpace':'global','level':level,'name':f'Well test {level}','role':'test'})
 runs=[];scenery=[];water={}
 if level==0:
  for id,y,overlay in [(1354,440,27),(1355,448,28)]:
   for d in range(8):
    x=440+4*d;water[x,y]=overlay;scenery.append({'placementId':f'well-{id}-{d}','sceneryId':id,'position':{'x':x,'y':y},'direction':d})
  water[440,456]=27;scenery.append({'placementId':'wall-blocked-well','sceneryId':1354,'position':{'x':440,'y':456},'direction':0})
 for x in range(432,480):
  for y in range(432,480):
   tile=dict(base)
   if (x,y) in water:tile['overlay']=water[x,y]
   if level==0:
    if (x,y)==(440,456):tile.update(verticalWall=1,horizontalWall=1)
    if (x,y)==(441,456):tile['horizontalWall']=1
    if (x,y)==(440,457):tile['verticalWall']=1
   if runs and runs[-1]['tile']==tile:runs[-1]['count']+=1
   else:runs.append({'count':1,'tile':tile})
 name=f'terrain-{level}.json';digest=write(name,{'schemaVersion':1,'encoding':'rle-layered-sector-v1','size':48,'tileOrder':'x-major-y-minor','runs':runs})
 manifest['terrainSectors'].append({'worldSpace':'global','level':level,'sectorX':9,'sectorY':9,'encoding':'rle-layered-sector-v1','path':name,'sha256':digest})
 name=f'placements-{level}.json';digest=write(name,{'schemaVersion':5,'encoding':'layered-world-placements-v5','worldSpace':'global','level':level,'npcs':[],'groundItems':[],'scenery':scenery,'boundaries':[]})
 manifest['placementSets'].append({'id':f'placements-{level}'.replace('--','-minus-'),'worldSpace':'global','level':level,'encoding':'layered-world-placements-v5','path':name,'sha256':digest})
digest=write('manifest.json',manifest);config=(root/'server/myworld.conf').read_text()
for key,value in {'layered_native_terrain_package_path':str(package),'layered_native_terrain_manifest_sha256':digest,'layered_native_world_runtime_profile':'native-replacement','want_discord_bot':'false'}.items():
 config,count=re.subn(r'(?m)^([ \t]*)'+key+r':.*$',lambda m:f'{m[1]}{key}: {value}',config)
 if not count:config+=f'\n{key}: {value}\n'
(target/'myworld.conf').write_text(config);print(target)
