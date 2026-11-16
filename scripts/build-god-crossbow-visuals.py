#!/usr/bin/env python3
"""Pack native Aseprite-held exports, preserving every unrelated archive entry."""
from pathlib import Path
import argparse,gzip,hashlib,importlib.util,json,shutil,struct,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
AUTHOR=ROOT/'dev/myworld/assets/authoring/equipment/god-crossbows'
GODS=('saradomin','zamorak','guthix')
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    layout=json.loads((AUTHOR/'frame-layout.json').read_text());assert len(layout['frames'])==15
    approved=json.loads((AUTHOR/'approved-held.json').read_text())
    spec=importlib.util.spec_from_file_location('archive_codec',ROOT/'scripts/generate-battleaxe-coatings.py');codec=importlib.util.module_from_spec(spec);spec.loader.exec_module(codec)
    custom=ROOT/'Client_Base/Cache/video/Custom_Sprites.osar';groups=codec.archive(gzip.decompress(custom.read_bytes()))
    owned={g+'crossbow'for g in GODS};entries={};classic={}
    before={'custom':{group+':'+n:hashlib.sha256(b).hexdigest()for group,rows in groups for n,b in rows if n not in owned}}
    for index,god in enumerate(GODS):
        palette=[(0,0,0)];encoded=[]
        for row in layout['frames']:
            f=row['frame'];source=AUTHOR/approved['source']/god/f'{f:02}.png';im=Image.open(source).convert('RGBA');anchor=Image.open(AUTHOR/'inputs'/f'{f:02}.png').convert('RGBA')
            assert hashlib.sha256(source.read_bytes()).hexdigest()==approved['frames'][f'{god}/{f:02}.png'],'Approved held pixels changed'
            assert im.size==anchor.size==tuple(row['canvas'])
            # Approved limb shapes have new cropped bounds; the full canvas/grip is unchanged.
            x,y,right,bottom=im.getbbox();row=dict(row,crop=[right-x,bottom-y],offset=[x,y],shift=True)
            runtime=ROOT/f'dev/myworld/assets/runtime/sprites/equipment/god-crossbows/{god}/{f:02}.png'
            if args.check:assert runtime.read_bytes()==source.read_bytes()
            else:runtime.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,runtime)
            x,y=row['offset'] if row['shift'] else (0,0);w,h=row['crop'];crop=im.crop((x,y,x+w,y+h));pixels=[];rgb=[]
            for r,g,b,a in crop.get_flattened_data():
                color=(r,g,b)if a else (0,0,0)
                assert a in (0,255),'Held art must have binary alpha'
                if color not in palette:palette.append(color)
                pixels.append(palette.index(color));rgb.append((color[0]<<16)|(color[1]<<8)|color[2])
            encoded.append((row,bytes(pixels)))
            classic[str(4200+index*27+f)]=struct.pack('>iiBiiii',w,h,row['shift'],x,y,*row['canvas'])+b''.join(struct.pack('>I',v)for v in rgb)
        assert len(palette)<=256
        blob=(god+'crossbow').encode()+b'\0'+bytes([layout['kind'],layout['layer'],15,len(palette)-1])+b''.join(bytes(c)for c in palette)
        for row,pixels in encoded:blob+=struct.pack('>HHBhhHH',*row['crop'],row['shift'],*row['offset'],*row['canvas'])+pixels
        entries[god+'crossbow']=blob
        for name in ('bolt',):
            source=AUTHOR/'shots-v3'/god/(name+'.png');dest=ROOT/f'dev/myworld/assets/runtime/animations/projectile-moving/god-bolts/{god}/{name}.png'
            if args.check:assert source.read_bytes()==dest.read_bytes()
            else:dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
    rebuilt=bytes([len(groups)])
    for group,rows in groups:
        if group=='equipment':
            oldnames={n for n,b in rows};rows=[(n,entries.get(n,b))for n,b in rows]+[(n,b)for n,b in entries.items()if n not in oldnames]
        rebuilt+=group.encode()+b'\0'+struct.pack('>H',len(rows))+b''.join(b for n,b in rows)
    if args.check:assert gzip.decompress(custom.read_bytes())==rebuilt
    else:custom.write_bytes(gzip.compress(rebuilt,mtime=0))
    authentic=ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc'
    with zipfile.ZipFile(authentic)as z:old={n:z.read(n)for n in z.namelist()}
    before['classic']={n:hashlib.sha256(b).hexdigest()for n,b in old.items()if n not in classic}
    receipt=AUTHOR/'archive-preservation.json'
    if receipt.exists():
        baseline=json.loads(receipt.read_text());assert all(before[group].get(n)==h for group,rows in baseline.items()for n,h in rows.items()),'Unrelated sprite changed'
    else:
        assert not args.check and not any(n in old for n in classic),'Reserved classic range collision'
        receipt.write_text(json.dumps(before,indent=2)+'\n')
    if args.check:assert all(old.get(n)==b for n,b in classic.items())
    elif any(old.get(n)!=b for n,b in classic.items()):
        with zipfile.ZipFile(authentic,'w')as z:
            for n,b in {**old,**classic}.items():
                info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,b)
    print('PASS 45 held poses, native anchors, 3 bolt/trail sheets and preserved unrelated archive payloads')
if __name__=='__main__':main()
