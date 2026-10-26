#!/usr/bin/env python3
"""Offline Phase-1 review exporter. Never writes production definitions or archives.

Controlled palette/edge edits implement the handoff's isolated candidate art
workflow. Values here describe candidates, never gameplay defaults.
"""
import argparse, base64, csv, gzip, hashlib, html, io, json, re, struct
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
TIERS = {2002:1,2013:2,205:3,89:4,90:5,429:5,3118:5,2156:5,91:6,2024:7,92:8,2035:9,93:10,2752:11,3269:12}

def sha(data): return hashlib.sha256(data).hexdigest()
def read_json(path): return json.loads((ROOT/path).read_text())

def archive_entries(path):
    data=gzip.decompress(path.read_bytes()); pos=1; entries={}
    def string():
        nonlocal pos
        end=data.index(0,pos); value=data[pos:end].decode('latin1');pos=end+1;return value
    for _ in range(data[0]):
        group=string(); count=struct.unpack_from('>H',data,pos)[0];pos+=2
        for _ in range(count):
            start=pos;name=string();kind=data[pos];pos+=1;layer=None
            if kind in (1,2,3):layer=data[pos];pos+=1
            count_frames=data[pos];colors=data[pos+1]+1;pos+=2
            palette=[tuple(data[p:p+3]) for p in range(pos,pos+colors*3,3)];pos+=colors*3
            frames=[]
            for n in range(count_frames):
                w,h,use,x,y,bw,bh=struct.unpack_from('>HHBhhHH',data,pos);pos+=13
                pix=data[pos:pos+w*h];pos+=w*h
                assert len(pix)==w*h
                crop=Image.new('RGBA',(w,h));crop.putdata([(*palette[c],255 if palette[c] not in ((0,0,0),(255,0,255)) else 0) for c in pix])
                canvas=Image.new('RGBA',(bw,bh));canvas.paste(crop,(x if use else 0,y if use else 0))
                frames.append((canvas,dict(frame=n,crop=[w,h],shift=bool(use),offset=[x,y],canvas=[bw,bh],rgbaSha256=sha(canvas.tobytes()))))
            entries[group,name]=dict(frames=frames,kind=kind,layer=layer,sha256=sha(data[start:pos]))
    assert pos==len(data)
    return entries

def recolor(image,mask):
    # Legacy one-mask grayscale channel, integer division by 256, then default
    # white colourTransform; see RendererSpriteTransform.java. This is a static
    # native-scale pixel review, not a full equipped renderer simulation.
    out=image.copy();p=out.load();channels=((mask>>16)&255,(mask>>8)&255,mask&255)
    for y in range(out.height):
        for x in range(out.width):
            r,g,b,a=p[x,y]
            if not a:continue
            if r==g==b:r,g,b=[r*c>>8 for c in channels]
            p[x,y]=(r*255>>8,g*255>>8,b*255>>8,a)
    return out

def edge_candidate(image,raw,dragon=False):
    # Preserve alpha and handle. Only exposed gray metal-edge pixels are changed.
    # Dragon's red inventory blade also qualifies; no new outline pixels.
    out=image.copy();p=out.load();q=raw.load();changed=0
    for y in range(out.height):
        for x in range(out.width):
            r,g,b,a=q[x,y]
            if not a or not ((r==g==b and r>24) or (dragon and r>g*3 and r>b*3 and x>=20 and y<=19)):continue
            if not any(xx<0 or yy<0 or xx>=out.width or yy>=out.height or q[xx,yy][3]==0 for xx,yy in ((x-1,y),(x+1,y),(x,y-1),(x,y+1))):continue
            lum=max(r,g,b);p[x,y]=(max(10,lum*35//255),max(55,lum*190//255),max(50,lum*174//255),a);changed+=1
    assert out.getchannel('A').tobytes()==image.getchannel('A').tobytes()
    return out,changed

def sheet(pairs,scale=2):
    w=max(im.width for _,im in pairs)*scale;h=max(im.height for _,im in pairs)*scale
    cols=6;rows=(len(pairs)+cols-1)//cols;out=Image.new('RGB',(cols*(w+12),rows*(h+30)), '#20252b');d=ImageDraw.Draw(out)
    for i,(label,im) in enumerate(pairs):
        x=(i%cols)*(w+12)+6;y=(i//cols)*(h+30)+22
        d.text((x,y-17),label,fill='white');z=im.resize((im.width*scale,im.height*scale),Image.Resampling.NEAREST);out.paste(z,(x,y),z)
    return out

def data_uri(im):
    b=io.BytesIO();im.save(b,format='PNG');return 'data:image/png;base64,'+base64.b64encode(b.getvalue()).decode()

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--output',type=Path,default=ROOT/'output/battleaxe-coatings-review');args=ap.parse_args();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
    assert out.is_relative_to(ROOT/'output'), 'Keep candidate exports under worker output/'
    D={};provenance={}
    for path in ['server/conf/server/defs/ItemDefs.json','server/conf/server/defs/ItemDefsCustom.json','server/conf/server/defs/ItemDefsMyWorld.json']:
        j=read_json(path)
        for x in j.get('item',j.get('items',[])):
            D.setdefault(x['id'],{}).update(x);provenance.setdefault(x['id'],[]).append(path)
    roster={k:x for k,x in D.items() if re.search(r'battle\s*axe',x.get('name',''),re.I)}
    assert set(roster)==set(TIERS),f'Roster changed: audit tier classification, do not silently drop {set(roster)^set(TIERS)}'
    client=(ROOT/'Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java').read_text()
    defs={};pattern=r'(?:addItemDefinition|setCustomItemDefinition)\((?:\d+,\s*)?new ItemDef\("([^"\n]*battle [Aa]xe)",\s*"[^"]*",\s*"[^"]*",\s*\d+,\s*(\d+),\s*"items:(\d+)",\s*false,\s*true,\s*16,\s*([^,]+),[^;]*?,\s*(\d+)\)\);'
    for name,sprite,entry,mask,k in re.findall(pattern,client):
        defs[int(k)]=dict(clientName=name,spriteId=int(sprite),entry=entry,pictureMask=int(mask,0) if mask.strip()!='EXALTED_RUNE_COLOR' else 0xD8FFFF)
    assert set(roster)<=set(defs),set(roster)-set(defs)
    archive=ROOT/'Client_Base/Cache/video/Custom_Sprites.osar';before=sha(archive.read_bytes());authentic=ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc';authentic_before=sha(authentic.read_bytes());entries=archive_entries(archive)
    held=entries['equipment','battleaxe'];assert len(held['frames'])==18 and held['layer']==3
    aliases={109:0xFF6619,110:15654365,111:15658734,112:10072780,113:11717785,114:65535,115:3158064,162:16711748,710:0xb7c9d9,711:0xc86a2b,712:0x8ea6bb,713:0x5a3f7d,1049:0xd8ffff}
    inventory=[];held_html=[];matrix=[];outputs={}
    for k,x in sorted(roster.items(),key=lambda p:(p[1]['requiredLevel'],p[0])):
        c=defs[k];raw=entries['items',c['entry']]['frames'][0][0]
        base=recolor(raw,c['pictureMask']);candidate,n=edge_candidate(base,raw,dragon=k==2752)
        assert base.size==(48,32) and base.getbbox()==candidate.getbbox()
        for role,im in [('base',base),('candidate',candidate)]:
            name=f'inventory-{k}-{role}.png';im.save(out/name);outputs[name]=sha((out/name).read_bytes())
        inventory.extend([(f'{k} base',base),(f'{k} candidate',candidate)])
        hp=[];changed=[]
        for raw,meta in held['frames']:
            b=recolor(raw,aliases[x['appearanceID']]);a,num=edge_candidate(b,raw);changed.append(num)
            hp.extend([(f"f{meta['frame']} base",b),(f"f{meta['frame']} teal",a)])
        hs=sheet(hp,2);hs.save(out/f'held-{k}.png');outputs[f'held-{k}.png']=sha((out/f'held-{k}.png').read_bytes())
        mirrored=sheet([(label+' mirrored',ImageOps.mirror(im)) for label,im in hp],2);mirrored.save(out/f'held-{k}-mirrored.png');outputs[f'held-{k}-mirrored.png']=sha((out/f'held-{k}-mirrored.png').read_bytes())
        held_html.append(f'<details><summary>{k} {html.escape(x["name"])} — all18 stored frames and mirrored variants</summary><p>f0–14 directional/walk; f15–17 attack. Mirror exports preserve canvas, not camera/layer simulation.</p><img class="sheet" src="{data_uri(hs)}"><img class="sheet" src="{data_uri(mirrored)}"></details>')
        matrix.append(dict(itemId=k,name=x['name'],tier=TIERS[k],serverDefinition=x,sources=provenance[k],client=c,heldFamily='battleaxe',heldPalette=aliases[x['appearanceID']],heldPaletteHex=f'{aliases[x["appearanceID"]]:06X}',heldFrames=18,heldLayer=3,existingWeaponPoison=False,proposedCounterpart=f'Viscous {x["name"]}',counterpartId=None,status='PROPOSAL_ONLY',inventoryChangedPixels=n,heldChangedPixels=changed,inventoryBounds=list(base.getbbox()),notes=('Inventory/held palette mismatch inherited from base' if k in (2156,3118) else 'Hero quest gate must be preserved' if k==2752 else 'Outside current Equipment high-roll list' if k==3269 else '')))
    # Potion candidates deliberately retain skull poison bottle shape; dose
    # liquid bands are visual concepts and have no item/dose registration.
    raw=Image.open(ROOT/'dev/myworld/reference-library/items/consumables/potions-and-bottles/id-02202.png').convert('RGBA');potions=[]
    for dose in (3,2,1):
        im=raw.copy();p=im.load()
        for y in range(im.height):
            for x in range(im.width):
                r,g,b,a=p[x,y]
                if a and g>r*1.2 and g>b*1.2:
                    p[x,y]=(14,max(50,g*180//255),max(45,g*166//255),a) if y>=31-dose*7 else (min(255,g//2),min(255,g//2),min(255,g//2),a)
        assert im.getchannel('A').tobytes()==raw.getchannel('A').tobytes()
        im.save(out/f'coating-{dose}-dose-candidate.png');outputs[f'coating-{dose}-dose-candidate.png']=sha((out/f'coating-{dose}-dose-candidate.png').read_bytes());potions.append((f'{dose} dose concept',im))
    invsheet=sheet(inventory,4);invsheet.save(out/'inventory-contact.png');ps=sheet(potions,4);ps.save(out/'dose-contact.png')
    # Confirm all input archive bytes unchanged; full per-entry fingerprints make
    # preservation inspectable independently of image previews.
    assert sha(archive.read_bytes())==before
    assert sha(authentic.read_bytes())==authentic_before
    sourcepaths=['server/conf/server/defs/ItemDefs.json','server/conf/server/defs/ItemDefsCustom.json','server/conf/server/defs/ItemDefsMyWorld.json','dev/myworld/reference-library/items/consumables/potions-and-bottles/id-02202.png','scripts/audit-battleaxe-coatings.py','Client_Base/Cache/video/Custom_Sprites.osar','Client_Base/Cache/video/Authentic_Sprites.orsc','Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java','Client_Base/src/com/openrsc/client/entityhandling/MyWorldItemOverrides.java','Client_Base/src/orsc/graphics/RendererSpriteTransform.java','server/src/com/openrsc/server/content/PoisonPower.java','server/src/com/openrsc/server/content/Slow.java','server/src/com/openrsc/server/model/container/Equipment.java','server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java']
    manifest=dict(status='PHASE1_REVIEW_ONLY',sources={p:sha((ROOT/p).read_bytes()) for p in sourcepaths},customArchiveEntries={':'.join(k):v['sha256'] for k,v in sorted(entries.items())},battleaxes=matrix,heldFrameGeometry=[m for _,m in held['frames']],outputs=outputs,checks=dict(eligibleBattleaxes=len(matrix),customArchiveEntries=len(entries),heldFramesPerPalette=18,heldPalettes=len(matrix),inventoryPairs=len(matrix),doseConcepts=3,alphaAndBoundsPreserved=True,productionArchiveUnchanged=True),limitations=['Static native pixel approximation using current legacy grayscale tint. Not production renderer captures.','White/grey retain existing black held palette mismatch.','No equipped composites, camera projection or layered hand occlusion acceptance.','Mirrored frames are geometric previews, not proof of all direction routing.','No gameplay code/items/IDs/default balance installed.'])
    (out/'coverage.json').write_text(json.dumps(manifest,indent=2)+'\n')
    with (out/'battleaxe-matrix.csv').open('w',newline='') as f:
        fields=['id','name','tier','level','meleeOffense','speedCategory','aim','power','prayer','value','inventorySprite','inventoryPalette','appearance','heldPalette','proposedCounterpart','notes'];w=csv.DictWriter(f,fieldnames=fields,lineterminator='\n');w.writeheader()
        for a in matrix:
            x=a['serverDefinition'];w.writerow(dict(zip(fields,[a['itemId'],a['name'],a['tier'],x['requiredLevel'],x['meleeOffense'],x['weaponSpeed'],x['weaponAimBonus'],x['weaponPowerBonus'],x['prayerBonus'],x['basePrice'],a['client']['spriteId'],f"{a['client']['pictureMask']:06X}",x['appearanceID'],a['heldPaletteHex'],a['proposedCounterpart'],a['notes']])))
    cards=''.join(f'<article><h3>{k} {html.escape(roster[k]["name"])}</h3>'+''.join(f'<figure><img width="48" height="32" src="{data_uri(Image.open(out/f"inventory-{k}-{role}.png"))}"><img width="192" height="128" src="{data_uri(Image.open(out/f"inventory-{k}-{role}.png"))}"><figcaption>{role}: native1× and nearest4×</figcaption></figure>' for role in ['base','candidate'])+'</article>' for k in sorted(roster,key=lambda k:(roster[k]['requiredLevel'],k)))
    page='<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Battleaxe coating Phase1 review</title><style>body{background:#181c22;color:#eee;font:16px system-ui;margin:30px;max-width:1500px}h1{color:#7cd9c9}img{image-rendering:pixelated;background:repeating-conic-gradient(#30363e 0 25%,#252b33 0 50%) 0/12px 12px}article{border:1px solid #52616b;padding:12px;margin:12px;display:inline-block}figure{margin:8px;display:inline-block}figure img{margin:6px;vertical-align:middle}figcaption{font-size:12px}details{margin:18px 0}summary{cursor:pointer}.sheet{max-width:100%;height:auto}strong{color:#ffca7a}</style><h1>Battleaxe coating · Phase1 candidate review</h1><p><strong>Unapproved isolated candidates. No usable items, IDs or gameplay values installed.</strong></p><p>15 base/candidate inventory pairs at native48×32 and nearest4×. Metal interior, handle, canvas and alpha retained; teal edits affect existing blade-edge pixels only. These are static archive-derived previews, not screenshots from the game.</p><h2>Dose concepts</h2><p>Authentic poison52 shape; proposed teal liquid bands. Skull/readability and fill-level treatment require owner review.</p><img class="sheet" src="'+data_uri(ps)+'"><h2>Inventory / ground</h2>'+cards+'<h2>Held family, directional and attack coverage</h2><p>18 authentic battleaxe frames, MAIN_HAND layer3, all15 palettes. Includes mirrored previews. White/grey currently share black held appearance115; shown faithfully. No character composites or camera-dependent overlap validation.</p>'+''.join(held_html)+'<h2>Limits</h2><ul>'+''.join('<li>'+html.escape(v)+'</li>' for v in manifest['limitations'])+'</ul><p>Reproduce: python3 scripts/audit-battleaxe-coatings.py — outputs in output/battleaxe-coatings-review. Coverage JSON records source and output SHA256 fingerprints.</p></html>'
    (out/'review.html').write_text(page)
    (out/'SHA256SUMS').write_text(''.join(sha(p.read_bytes())+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
    print(json.dumps(manifest['checks'],indent=2));print(out/'review.html')

if __name__=='__main__':main()
