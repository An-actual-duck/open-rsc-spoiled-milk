#!/usr/bin/env python3
"""Review-only native pixel candidates. No production cache writes/IDs allocated."""
from pathlib import Path
import hashlib,json,struct,subprocess,zipfile
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
CACHE=ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc'
sha=lambda b:hashlib.sha256(b).hexdigest()
for folder in ['source','candidates','masks','review','aseprite']:(OUT/folder).mkdir(exist_ok=True)
records={}
with zipfile.ZipFile(CACHE) as z:
 for idx,name in [(1,'water'),(2,'stone'),(29,'dirt')]:
  raw=z.read(str(3225+idx));w,h=struct.unpack('>ii',raw[:8]);pix=struct.unpack('>'+str(w*h)+'I',raw[25:]);im=Image.new('RGB',(w,h));im.putdata([((v>>16)&255,(v>>8)&255,v&255) for v in pix]);path=OUT/'source'/f'{name}-{idx}.png';im.save(path)
  records[name]={'textureIndex':idx,'archiveEntry':str(3225+idx),'dimensions':[w,h],'entrySha256':sha(raw),'pngSha256':sha(path.read_bytes()),'headerHex':raw[:25].hex()}

def seep_mask(base,material):
 # Upright view maps native U=0 to wall base, U=1 to top.
 n=base.width;m=Image.new('L',(n,n));d=ImageDraw.Draw(m)
 # Different lengths and staggered origins avoid a painted vertical stripe grid.
 streams=[(.18,.16),(.49,.41),(.80,.28)]
 for j,(rx,ry) in enumerate(streams):
  cx=int(rx*n);sy=int(ry*n)
  # Follow existing darkest crack pixels over a short horizontal fissure.
  pts=[]
  for x in range(cx-int(n*.09),cx+int(n*.09)+1):
   y=min(range(max(1,sy-4),min(n-1,sy+5)),key=lambda y:sum(base.getpixel((x,y))))
   pts.append((x,y))
  d.line(pts,fill=150,width=1)
  # Anchor flow to crack, descend with small angular jogs, never upward.
  top=min(pts,key=lambda p:abs(p[0]-cx));x,y=top;pts=[top]
  for step in range(1,8):
   y=top[1]+(n-1-top[1])*step//7
   x=cx+([0,1,1,0,-1,0,1,0][step])*(1 if n==64 else 2)
   pts.append((x,y))
  d.line(pts,fill=215,width=1 if n==64 else 2)
  # A one-pixel edge catches light; small base wetting, not broad material tint.
  for x,y in pts[1:]:d.point((x,y),fill=255)
  d.line([(pts[-1][0]-2,n-1),(pts[-1][0]+2,n-1)],fill=200,width=1)
 return m

entries=[]
for liquid in ['ichor','divine-water']:
 base=Image.open(OUT/'source/water-1.png').convert('RGB');result=base.copy()
 # One-to-one palette substitution retains every ripple boundary and position.
 colors=sorted(set(base.getdata()),key=lambda c:(sum(c),c));palette={}
 for c in colors:
  t=colors.index(c)/max(1,len(colors)-1)
  lo,hi=((33,12,64),(112,66,163)) if liquid=='ichor' else ((107,161,198),(207,237,250))
  palette[c]=tuple(round(a+(b-a)*t) for a,b in zip(lo,hi))
 result.putdata([palette[c] for c in base.getdata()]);name=liquid+'-water';result.save(OUT/'candidates'/f'{name}.png')
 mask=Image.new('L',base.size,255);mask.save(OUT/'masks'/f'{name}.png');entries.append((name,'water',base,result,mask))
 for material,idx in [('stone',2),('dirt',29)]:
  native=Image.open(OUT/'source'/f'{material}-{idx}.png').convert('RGB');upright=native.transpose(Image.Transpose.ROTATE_90);mask=seep_mask(upright,material);changed=upright.copy()
  for y in range(upright.height):
   for x in range(upright.width):
    a=mask.getpixel((x,y))
    if a:
     rgb=(131,74,177) if liquid=='ichor' else (167,220,247)
     if a==255:rgb=(164,111,205) if liquid=='ichor' else (218,242,255)
     old=upright.getpixel((x,y));changed.putpixel((x,y),tuple((old[c]*(255-a)+rgb[c]*a)//255 for c in range(3)))
  result=changed.transpose(Image.Transpose.ROTATE_270);mask=mask.transpose(Image.Transpose.ROTATE_270);name=f'{liquid}-{material}-seep';result.save(OUT/'candidates'/f'{name}.png');mask.save(OUT/'masks'/f'{name}.png');entries.append((name,material,native,result,mask))
# Aseprite stores original source + separately editable replacement pixels.
lua=['local root=app.params["root"]','local names={'+','.join('"'+e[0]+'"' for e in entries)+'}', 'for _,name in ipairs(names) do', ' local material=string.find(name,"stone") and "stone-2" or (string.find(name,"dirt") and "dirt-29" or "water-1")', ' local source=app.open(root.."/source/"..material..".png")', ' source.layers[1].name="Native source (unchanged)"', ' local candidate=app.open(root.."/candidates/"..name..".png")', ' local image=Image(candidate.cels[1].image)', ' app.activeSprite=source', ' local layer=source:newLayer();layer.name="Controlled recolor / seep pixels"', ' local mask=app.open(root.."/masks/"..name..".png")', ' local maskImage=mask.cels[1].image', ' for y=0,image.height-1 do for x=0,image.width-1 do', '  local p=maskImage:getPixel(x,y)', '  if app.pixelColor.rgbaR(p)==0 then image:putPixel(x,y,app.pixelColor.rgba(0,0,0,0)) end', ' end end', ' source:newCel(layer,1,image,Point(0,0))', ' source:saveAs(root.."/aseprite/"..name..".aseprite")', ' source:close();candidate:close();mask:close()', 'end']
(OUT/'author.lua').write_text('\n'.join(lua)+'\n')
subprocess.run(['aseprite','-b','--script-param',f'root={OUT}','--script',str(OUT/'author.lua')],check=True)
for name,material,base,result,mask in entries:
 path=OUT/'review'/f'{name}-roundtrip.png';subprocess.run(['aseprite','-b',str(OUT/'aseprite'/f'{name}.aseprite'),'--save-as',str(path)],check=True,stdout=subprocess.DEVNULL)
 assert list(Image.open(path).convert('RGB').getdata())==list(result.getdata()),name
 if material!='water':
  assert all(a or b==c for a,b,c in zip(mask.getdata(),base.getdata(),result.getdata())),name
  up=mask.transpose(Image.Transpose.ROTATE_90);assert max(up.crop((0,up.height-1,up.width,up.height)).getdata())>0
 # Original, native candidate, upright 3x wall preview.
 upright=result if material=='water' else result.transpose(Image.Transpose.ROTATE_90)
 preview=Image.new('RGB',(640,450),'#23262c');d=ImageDraw.Draw(preview);d.text((12,8),name+' | source / native / upright nearest 3x',fill='white');preview.paste(base,(12,32));preview.paste(result,(160,32));preview.paste(upright.resize((upright.width*3,upright.height*3),Image.Resampling.NEAREST),(250,32));preview.save(OUT/'review'/f'{name}-comparison.png')
manifest={'status':'REVIEW CANDIDATES ONLY - not installed/approved','sourceArchive':str(CACHE.relative_to(ROOT)),'sourceArchiveSha256':sha(CACHE.read_bytes()),'sources':records,'candidates':[]}
for name,material,base,result,mask in entries:
 manifest['candidates'].append({'name':name,'source':material,'changedPixels':sum(a!=b for a,b in zip(base.getdata(),result.getdata())),'maskPixels':sum(bool(v) for v in mask.getdata()),'files':{f'{folder}/{name}.{ext}':sha((OUT/folder/f'{name}.{ext}').read_bytes()) for folder,ext in [('candidates','png'),('masks','png'),('aseprite','aseprite')]}})
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
html='<!doctype html><meta charset="utf-8"><title>Cleric well texture candidates</title><style>body{background:#181b21;color:#eee;font:16px sans-serif;max-width:1100px;margin:30px auto}img{image-rendering:pixelated;max-width:100%}section{background:#23262c;padding:15px;margin:20px 0}a{color:#afd9ff}</style><h1>Cleric well textures — review candidates</h1><p>Original native patterns; deep-purple Ichor and pale-blue Divine Water. Six editable Aseprite files. No runtime definitions or map changes. Native software context below is an offline rendering, not in-game acceptance.</p>'
for name,*_ in entries:html+=f'<section><h2>{name}</h2><img src="{name}-comparison.png"><p><a href="../aseprite/{name}.aseprite">Editable source</a> · <a href="../candidates/{name}.png">Native pixels</a></p></section>'
html+='<h2>Native software wall / floor context</h2><p>192- and275-unit walls; unchanged geometry. View both faces/axes in-game before production acceptance.</p><div id="contexts">'+''.join(f'<img src="{l}-{m}-{h}-context.png">' for l in ['ichor','divine-water'] for m in ['stone','dirt'] for h in [192,275])+'</div>'
(OUT/'review/index.html').write_text(html)
print('PASS six native candidate masks, source preservation and exact Aseprite pixel round trips')
