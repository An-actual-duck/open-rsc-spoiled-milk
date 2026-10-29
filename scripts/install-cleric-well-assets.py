#!/usr/bin/env python3
"""Install approved game-owned native textures; --check is read-only."""
import argparse, gzip, hashlib, importlib.util, json, struct, zipfile
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'dev/myworld/cleric-wells'
sha=lambda b:hashlib.sha256(b).hexdigest()
spec=importlib.util.spec_from_file_location('coatings_generator',ROOT/'scripts/generate-battleaxe-coatings.py')
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
NAMES=['ichor-water','divine-water-water','ichor-stone-seep','ichor-dirt-seep','divine-water-stone-seep','divine-water-dirt-seep']

def unpack(raw):
 p=raw.index(0)+1;assert raw[p:p+2]==bytes([0,1]);p+=2;n=raw[p]+1;p+=1
 palette=[tuple(raw[x:x+3]) for x in range(p,p+n*3,3)];p+=n*3
 w,h,use,x,y,bw,bh=struct.unpack_from('>HHBhhHH',raw,p);p+=13
 pixels=[palette[i] for i in raw[p:]];assert len(pixels)==w*h
 return (w,h,use,x,y,bw,bh),pixels

def classic(meta,pixels):
 return struct.pack('>iiBiiii',*meta)+b''.join(bytes([0,*p]) for p in pixels)

def custom(name,image):
 w,h=image.size;pix=list(image.convert('RGB').getdata());pal=sorted(set(pix));assert len(pal)<=256
 indices={c:i for i,c in enumerate(pal)}
 return name.encode()+b'\0'+bytes([0,1,len(pal)-1])+b''.join(bytes(c) for c in pal)+struct.pack('>HHBhhHH',w,h,0,0,0,w,h)+bytes(indices[c] for c in pix)

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');args=ap.parse_args()
 def emit(path,data):
  p=ROOT/path;data=data.encode() if isinstance(data,str) else data
  if args.check:assert p.read_bytes()==data,path
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 approved=json.loads((OUT/'manifest.json').read_text())
 for entry in approved['candidates']:
  for file,expected in entry['files'].items():assert sha((OUT/file).read_bytes())==expected,'Approved candidate changed: '+file
 # Preserve all preexisting serialized OSAR entries. New textures append at67.
 path=ROOT/'Client_Base/Cache/video/Custom_Sprites.osar';groups=g.archive(gzip.decompress(path.read_bytes()))
 textures=dict(next(entries for name,entries in groups if name=='textures'))
 desired={str(67+i):custom(str(67+i),Image.open(OUT/'candidates'/f'{name}.png')) for i,name in enumerate(NAMES)}
 original={(name,k):sha(v) for name,entries in groups for k,v in entries if not(name=='textures' and k in desired)}
 assert set(textures)-set(desired)==set(map(str,range(67))), 'Existing texture domain changed'
 if args.check:assert all(textures.get(k)==v for k,v in desired.items())
 else:
  groups=[(name,[(k,v) for k,v in entries if not(name=='textures' and k in desired)]+(list(desired.items()) if name=='textures' else [])) for name,entries in groups]
  data=bytes([len(groups)])+b''.join(name.encode()+b'\0'+struct.pack('>H',len(entries))+b''.join(v for _,v in entries) for name,entries in groups)
  path.write_bytes(gzip.compress(data,mtime=0))
 after=g.archive(gzip.decompress(path.read_bytes()))
 assert {(name,k):sha(v) for name,entries in after for k,v in entries if not(name=='textures' and k in desired)}==original
 textures=dict(next(entries for name,entries in after if name=='textures'))
 # Classic supplemental records are safely outside legacy3225.. texture / GUI ranges.
 native={str(4100+i-55):classic(*unpack(textures[str(i)])) for i in range(55,73)}
 path=ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc'
 with zipfile.ZipFile(path) as z:
  infos=z.infolist();before={i.filename:sha(z.read(i)) for i in infos if i.filename not in native}
  owned={i.filename:z.read(i) for i in infos if i.filename in native}
 if args.check:assert owned==native
 elif owned!=native:
  assert not owned,'Refusing to replace already installed native extension; review explicitly'
  with zipfile.ZipFile(path,'a') as z:
   for name,raw in native.items():
    info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
 with zipfile.ZipFile(path) as z:
  assert {i.filename:sha(z.read(i)) for i in z.infolist() if i.filename not in native}==before
  assert all(z.read(k)==v for k,v in native.items())
 # Definitions are emitted into marker regions, preserving preexisting bytes.
 def region(path,marker,text):
  p=ROOT/path;data=p.read_bytes();suffix=' -->' if marker.startswith('<!--') else '';start=('\n'+marker+' START'+suffix+'\n').encode();end=(marker+' END'+suffix+'\n').encode()
  if start in data:
   a=data.index(start);b=data.index(end,a)+len(end);updated=data[:a]+start+text.encode()+end+data[b:]
  else:raise AssertionError('Missing integration marker '+path)
  emit(path,updated)
 texturesjava=''.join('\t\ttextures.add(new TextureDef("existing-custom-texture-%d", "", %d));\n'%(i,4100+i-55) for i in range(55,67))
 texturesjava+=''.join('\t\ttextures.add(new TextureDef("%s", "", %d));\n'%(name,4112+i) for i,name in enumerate(NAMES))
 region('Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java','\t\t// CLERIC WELL TEXTURES',texturesjava)
 tilejava=''.join('\t\ttiles.add(new TileDef(%d, 3, 1));\n'%i for i in [67,68])
 region('Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java','\t\t// CLERIC WELL FLOORS',tilejava)
 walljava='';wallxml=''
 for i,(name,texture) in enumerate([('Ichor-seeping stone wall',69),('Ichor-seeping dirt wall',70),('Divine-water-seeping stone wall',71),('Divine-water-seeping dirt wall',72)]):
  for tall,h in [(False,192),(True,275)]:
   title=('High '+name[0].lower()+name[1:]) if tall else name
   desc=('Deep-purple ichor' if texture<71 else 'Divine water')+' seeps through cracks to the ground.'
   walljava+='\t\tdoors.add(new DoorDef(%s, %s, "WalkTo", "Examine", 1, 0, %d, %d, %d, i++));\n'%(json.dumps(title),json.dumps(desc),h,texture,texture)
   wallxml+='  <DoorDef>\n    <name>%s</name>\n    <description>%s</description>\n    <command1>WalkTo</command1><command2>Examine</command2>\n    <modelVar1>%d</modelVar1><modelVar2>%d</modelVar2><modelVar3>%d</modelVar3>\n    <doorType>1</doorType><unknown>0</unknown>\n  </DoorDef>\n'%(title,desc,h,texture,texture)
 region('Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java','\t\t// CLERIC WELL WALLS',walljava)
 region('server/conf/server/defs/TileDef.xml','<!-- CLERIC WELL FLOORS', ''.join('  <TileDef><colour>%d</colour><unknown>3</unknown><objectType>1</objectType></TileDef>\n'%i for i in [67,68]))
 region('server/conf/server/defs/DoorDef.xml','<!-- CLERIC WELL WALLS',wallxml)
 evidence={'status':'Owner-approved art installed offline; private gameplay and shoreline interaction pending','textures':{name:{'textureIndex':67+i,'nativeSprite':4112+i,'pngSha256':sha((OUT/'candidates'/f'{name}.png').read_bytes())} for i,name in enumerate(NAMES)},'floorValues':{'ichor':27,'divineWater':28},'wallIds':list(range(220,228)),'preservedCustomEntries':{n+':'+k:v for (n,k),v in original.items()},'preservedAuthenticEntries':before,'supplementalOriginalCustomTextures':{str(i):{'nativeSprite':4100+i-55,'serializedSha256':sha(textures[str(i)])} for i in range(55,67)}}
 emit('dev/myworld/cleric-wells/installation.json',json.dumps(evidence,indent=2)+'\n')
 print('PASS73 textures in both modes;6 approved palettes;18 native records;2 blocked floors;8 walls; preexisting payloads preserved')
if __name__=='__main__':main()
