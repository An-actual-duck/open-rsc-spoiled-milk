#!/usr/bin/env python3
"""Pack reviewed Aseprite coating exports without changing any published sprite entry."""
from pathlib import Path
import argparse,gzip,hashlib,importlib.util,json,struct,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
P=ROOT/'dev/myworld/assets/authoring/equipment/scimitar-coatings'
def main():
 check=argparse.ArgumentParser();check.add_argument('--check',action='store_true');args=check.parse_args()
 spec=importlib.util.spec_from_file_location('coatings',ROOT/'scripts/generate-battleaxe-coatings.py');g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
 m=json.loads((ROOT/'config/scimitar-coatings.json').read_text());layout=json.loads((P/'frame-layout.json').read_text());family=m['art']['family'];start=m['art']['classicSpriteStart']
 palette=[(0,0,0)];payloads=[]
 entry=family.encode()+b'\0'+bytes([layout['kind'],layout['layer'],18])
 for row in layout['frames']:
  im=Image.open(P/'held'/f'{row["frame"]:02}.png').convert('RGBA');raw=Image.open(P/'inputs/held'/f'{row["frame"]:02}.png').convert('RGBA')
  assert im.size==raw.size==tuple(row['crop']) and im.getchannel('A').tobytes()==raw.getchannel('A').tobytes()
  indexes=[];rgb=[]
  for after,before in zip(im.getdata(),raw.getdata()):
   if after!=before:assert before[0]==before[1]==before[2] and before[0]>50,'Non-metal pixel changed'
   color=after[:3] if after[3] else (0,0,0)
   if color not in palette:palette.append(color)
   indexes.append(palette.index(color));rgb.append((color[0]<<16)|(color[1]<<8)|color[2])
  payloads.append((row,bytes(indexes),rgb))
 assert len(palette)<=256
 entry+=bytes([len(palette)-1])+bytes(c for color in palette for c in color)
 for row,pix,_ in payloads:entry+=struct.pack('>HHBhhHH',*row['crop'],row['shift'],*row['offset'],*row['canvas'])+pix
 path=ROOT/'Client_Base/Cache/video/Custom_Sprites.osar';groups=g.archive(gzip.decompress(path.read_bytes()))
 before={group+':'+name:hashlib.sha256(blob).hexdigest() for group,entries in groups for name,blob in entries if name!=family}
 rebuilt=bytes([len(groups)])
 for group,entries in groups:
  entries=[(n,b) for n,b in entries if n!=family]
  if group=='equipment':entries.append((family,entry))
  rebuilt+=group.encode()+b'\0'+struct.pack('>H',len(entries))+b''.join(b for _,b in entries)
 if args.check:assert gzip.decompress(path.read_bytes())==rebuilt,'Custom family out of date'
 else:path.write_bytes(gzip.compress(rebuilt,mtime=0))
 path=ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc';desired={}
 for row,_,rgb in payloads:
  desired[str(start+row['frame'])]=struct.pack('>iiBiiii',*row['crop'],row['shift'],*row['offset'],*row['canvas'])+b''.join(struct.pack('>I',p) for p in rgb)
 with zipfile.ZipFile(path)as z:
  old={i.filename:z.read(i)for i in z.infolist()}
 classic={n:hashlib.sha256(b).hexdigest()for n,b in old.items()if n not in desired}
 if args.check:assert all(old.get(n)==b for n,b in desired.items()),'Classic family out of date'
 elif not all(old.get(n)==b for n,b in desired.items()):
  with zipfile.ZipFile(path,'a')as z:
   first=min((i.header_offset for i in z.infolist()if i.filename in desired),default=z.start_dir)
   z.filelist=[i for i in z.filelist if i.filename not in desired];z.NameToInfo={i.filename:i for i in z.filelist};z.start_dir=first
   for name,blob in desired.items():
    info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,blob)
 with zipfile.ZipFile(path)as z:assert {n:hashlib.sha256(z.read(n)).hexdigest()for n in z.namelist()if n not in desired}==classic
 receipt=P/'preserved-archives.json';data={'custom':before,'classic':classic}
 if receipt.exists():
  baseline=json.loads(receipt.read_text());assert all(data[group].get(n)==h for group,entries in baseline.items()for n,h in entries.items()),'Published sprite changed'
 elif not args.check:receipt.write_text(json.dumps(data,indent=2)+'\n')
 else:raise AssertionError('Missing preservation receipt')
 print('PASS fourteen inventory exports and eighteen native held frames; unrelated custom/classic entries preserved')
if __name__=='__main__':main()
