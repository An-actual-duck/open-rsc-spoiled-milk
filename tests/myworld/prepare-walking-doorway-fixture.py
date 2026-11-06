#!/usr/bin/env python3
"""Extend the disposable native door fixture; never reads accounts or changes authored maps."""
import hashlib,json,pathlib,subprocess,sys,re
root=pathlib.Path(__file__).resolve().parents[2]
target=pathlib.Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(root/'tests/myworld/prepare-double-door-fixture.py'),str(target)],check=True)
package=target/'package';manifest_path=package/'manifest.json';manifest=json.loads(manifest_path.read_text())
def write(name,data):
    content=json.dumps(data,sort_keys=True).encode();(package/name).write_bytes(content)
    return hashlib.sha256(content).hexdigest()
for level in (0,1,2,3,-3):
    data={'schemaVersion':4,'encoding':'layered-world-placements-v4','worldSpace':'global','level':level,'scenery':[],'boundaries':[],'npcs':[],'groundItems':[]}
    for index,direction in enumerate((0,2,4,6)):
        data['scenery'].append(dict(placementId='gate-%s-%s'%(level,direction),sceneryId=180,position=dict(x=12+index*8,y=38),direction=direction))
    for direction in (0,1,2,3):
        data['boundaries'].append(dict(placementId='walking-boundary-%s-%s'%(level,direction),boundaryId=2,position=dict(x=12+direction*8,y=42),direction=direction))
    if level==0:
        sector={'schemaVersion':1,'encoding':'uniform-layered-sector-v1','size':48,'tile':dict(elevation=0,texture=93,overlay=3,roof=0,verticalWall=0,horizontalWall=0,diagonalWall=0)}
        name='alkharid-terrain.json';digest=write(name,sector)
        manifest['terrainSectors'].append(dict(worldSpace='global',level=0,sectorX=1,sectorY=13,encoding='uniform-layered-sector-v1',path=name,sha256=digest))
        for subject,sx,sy in (('rangers',10,9),('legends',10,11)):
            name=subject+'-terrain.json';digest=write(name,sector)
            manifest['terrainSectors'].append(dict(worldSpace='global',level=0,sectorX=sx,sectorY=sy,encoding='uniform-layered-sector-v1',path=name,sha256=digest))
        data['scenery'].extend([
            dict(placementId='rangers-door',sceneryId=64,position=dict(x=495,y=463),direction=2),
            dict(placementId='legends-hall-door',sceneryId=1080,position=dict(x=512,y=540),direction=6)])
        data['scenery'].append(dict(placementId='legends-outer-gate',sceneryId=1079,position=dict(x=512,y=550),direction=2))
        data['scenery'].append(dict(placementId='alkharid-toll-gate',sceneryId=180,position=dict(x=92,y=649),direction=0))
        for ident,x,y in ((161,93,650),(162,90,650)):
            data['npcs'].append(dict(placementId='border-guard-%s'%ident,npcId=ident,start=dict(x=x,y=y),roamBounds=dict(minimum=dict(x=x,y=y),maximum=dict(x=x,y=y)),respawnSeconds=-1))
    entry=next(e for e in manifest['placementSets'] if e['level']==level)
    original=json.loads((package/entry['path']).read_text())
    for kind in ('scenery','boundaries','npcs','groundItems'):original[kind].extend(data[kind])
    entry['sha256']=write(entry['path'],original)
manifest_path.write_text(json.dumps(manifest,sort_keys=True))
sha=hashlib.sha256(manifest_path.read_bytes()).hexdigest()
config=target/'myworld.conf';text=config.read_text()
text=re.sub(r'(layered_native_terrain_manifest_sha256:\s*)[a-f0-9]{64}',lambda m:m.group(1)+sha,text)
config.write_text(text)
assert not list(target.rglob('*.db'))
print('Prepared walking passages manifest '+sha)
