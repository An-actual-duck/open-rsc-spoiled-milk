#!/usr/bin/env python3
"""Synthetic walking field plus exact installed course landing geometry. No accounts/listeners."""
from pathlib import Path
import subprocess,sys,json,hashlib,re
from installed_world_package import ROOT,package_root,manifest
out=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(ROOT/'tests/myworld/prepare-cleric-well-fixture.py'),str(out)],check=True)
package=out/'package';data=json.loads((package/'manifest.json').read_text());source=package_root('server');installed=manifest('server')
landings=[(692,493),(487,555),(298,131),(591,765)]
sectors={(x//48+dx,y//48+dy) for x,y in landings for dx in [-1,0,1] for dy in [-1,0,1]}
for entry in installed['terrainSectors']:
 if entry['worldSpace']=='global' and entry['level']==0 and (entry['sectorX'],entry['sectorY']) in sectors:
  raw=(source/entry['path']).read_bytes();assert hashlib.sha256(raw).hexdigest()==entry['sha256']
  name=f"course-{entry['sectorX']}-{entry['sectorY']}.bin";(package/name).write_bytes(raw)
  data['terrainSectors'].append(dict(entry,path=name))
scenery=[];boundaries=[]
for entry in installed['placementSets']:
 if entry['worldSpace']!='global' or entry['level']!=0:continue
 raw=(source/entry['path']).read_bytes();assert hashlib.sha256(raw).hexdigest()==entry['sha256'];placements=json.loads(raw)
 for kind,dest in [('scenery',scenery),('boundaries',boundaries)]:
  dest.extend(obj for obj in placements[kind] if any(abs(obj['position']['x']-x)<=8 and abs(obj['position']['y']-y)<=8 for x,y in landings))
entry=next(x for x in data['placementSets'] if x['worldSpace']=='global' and x['level']==0)
placements=json.loads((package/entry['path']).read_text());placements['scenery'].extend(scenery);placements['boundaries'].extend(boundaries)
raw=json.dumps(placements,sort_keys=True).encode();(package/entry['path']).write_bytes(raw);entry['sha256']=hashlib.sha256(raw).hexdigest()
raw=json.dumps(data,sort_keys=True).encode();(package/'manifest.json').write_bytes(raw)
config=out/'myworld.conf';config.write_text(re.sub(r'(?m)^(\s*layered_native_terrain_manifest_sha256:).*$',lambda m:m[1]+' '+hashlib.sha256(raw).hexdigest(),config.read_text()))
