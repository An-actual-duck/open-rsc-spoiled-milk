#!/usr/bin/env python3
"""Portable native-pixel review of installed game-owned coating assets, no game launch."""
import base64,hashlib,html,importlib.util,io,json
from pathlib import Path
from PIL import Image,ImageOps
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('coatings',ROOT/'scripts/generate-battleaxe-coatings.py');gen=importlib.util.module_from_spec(spec);spec.loader.exec_module(gen)
out=ROOT/'output/battleaxe-coatings-implementation-review';out.mkdir(parents=True,exist_ok=True)
manifest=json.loads(gen.SPEC.read_text());entries=gen.art.archive_entries(ROOT/'Client_Base/Cache/video/Custom_Sprites.osar')
definitions={r['id']:r for r in json.loads((ROOT/'server/conf/server/defs/BattleaxeCoatingItemDefs.json').read_text())['items']}
def uri(image):
 b=io.BytesIO();image.save(b,format='PNG');return 'data:image/png;base64,'+base64.b64encode(b.getvalue()).decode()
cards=[];sheets=[];pairs=[]
for v in manifest['variants']:
 baseid=v['baseId'];raw=entries['items','272'if baseid==2752 else '84']['frames'][0][0]
 base=gen.art.recolor(raw,v['inventoryMask']);coated=gen.art.recolor(Image.open(ROOT/f'dev/myworld/assets/sprites/items/inventory-ground/viscous-battleaxe-{baseid}.png').convert('RGBA'),v['inventoryMask'])
 title=html.escape(definitions[v['coatedId']]['name'])
 cards.append(f'<article><h3>{title}</h3><p>Base{baseid} → coated{v["coatedId"]}; poison tier{v["tier"]}</p>'+''.join(f'<figure><img width="48" height="32" src="{uri(im)}"><img width="192" height="128" src="{uri(im)}"><figcaption>{label}: native and nearest4×</figcaption></figure>'for label,im in [('Original',base),('Coated',coated)])+'</article>')
 pairs.extend([(str(baseid)+' base',base),(str(v['coatedId'])+' coated',coated)])
 hp=[]
 for n,((raw,_),(new,_))in enumerate(zip(entries['equipment','battleaxe']['frames'],entries['equipment','viscousbattleaxe']['frames'])):
  hp.extend([(f'{n} original',gen.art.recolor(raw,v['heldMask'])),(f'{n} coated',gen.art.recolor(new,v['heldMask']))])
 sheet=gen.art.sheet(hp,2);mirrored=gen.art.sheet([(label,ImageOps.mirror(im))for label,im in hp],2)
 sheets.append(f'<details><summary>{title}: all18 native frames and mirrors</summary><p>Frames0–14 are five direction/walk groups of three;15–17 are attacks. Each pair is original/coated. Exact native canvases remain64×102 or84×102. These are asset-plane comparisons, not equipped-body/camera occlusion acceptance.</p><img class="sheet" src="{uri(sheet)}"><img class="sheet" src="{uri(mirrored)}"></details>')
 sheet.save(out/f'held-{baseid}.png');mirrored.save(out/f'held-{baseid}-mirrors.png')
contact=gen.art.sheet(pairs,4);contact.save(out/'inventory-contact.png')
bottle=Image.open(ROOT/'dev/myworld/assets/sprites/items/inventory-ground/viscous-coating.png').convert('RGBA')
page='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Battleaxe coating implementation review</title><style>body{background:#192027;color:#edf1f3;font:16px system-ui;margin:28px;max-width:1500px}h1{color:#79d9c9}article{display:inline-block;border:1px solid #53626c;padding:12px;margin:8px}figure{display:inline-block;margin:8px}figure img{margin:5px;vertical-align:middle}img{image-rendering:pixelated;background:repeating-conic-gradient(#303942 0 25%,#242d35 0 50%) 0/12px 12px}.sheet{max-width:100%;height:auto}summary{cursor:pointer;padding:12px}figcaption,p{font-size:14px}.warning{color:#ffd28b}</style><h1>Viscous battleaxes — private candidate review</h1><p class="warning">Owner visual acceptance and private gameplay remain pending. No server/client was launched for this review.</p><p>One single-use coating; all15 battleaxes retain their original geometry, metal interiors and handle. Teal marks only the reviewed existing blade-edge pixels. White/Grey inherit their base black held tint; their inventory tints remain White/Grey. No unrelated sprite entry changed.</p><h2>One dose</h2>'''+f'<img width="48" height="32" src="{uri(bottle)}"><img width="192" height="128" src="{uri(bottle)}">'+''.join(cards)+'<h2>Held directions and attacks</h2>'+''.join(sheets)+'<p>Reproduce: python3 scripts/review-battleaxe-coatings.py. Paired Java fixture separately verifies actual classic/named selection and software/OpenGL texture mask paths. No existing offline full-character compositor was found; hand/body/camera overlap requires private-game review.</p></html>'
(out/'review.html').write_text(page)
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n'for p in sorted(out.iterdir())if p.is_file()and p.name!='SHA256SUMS'))
print(out/'review.html')
