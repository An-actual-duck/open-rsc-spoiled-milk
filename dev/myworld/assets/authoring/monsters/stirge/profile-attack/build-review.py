"""Validate Aseprite exports and make the local Firefox review page (no image edits)."""
from pathlib import Path
import json, hashlib, shutil
from PIL import Image
root=Path(__file__).resolve().parents[7]
base=Path(__file__).resolve().parent
old=base.parent/'near-wing-correction'
out=root/'output/art/stirge-profile-attack'
out.mkdir(parents=True,exist_ok=True)
changes=[];frames=[]
for n in range(18):
    name=f'frame-{n:02}.png'
    a=Image.open(old/name).convert('RGBA'); b=Image.open(base/name).convert('RGBA')
    assert a.size==b.size==((97 if n>=15 else 150),130)
    assert set(b.getchannel('A').get_flattened_data())<={0,255}
    if a.tobytes()!=b.tobytes():changes.append(n)
    if n>=15:shutil.copy2(old/name,out/f'before-{n:02}.png')
    shutil.copy2(base/name,out/name)
    frames.append({'frame':n,'sha256':hashlib.sha256((base/name).read_bytes()).hexdigest()})
assert changes==[15,16,17],changes
shutil.copy2(base/'stirge-profile-attack-sheet.png',out/'stirge-profile-attack-sheet.png')
(base/'validation.json').write_text(json.dumps({'changed_frames':changes,'unchanged_flight_frames':15,'binary_alpha':True,'flight_canvas':[150,130],'attack_canvas':[97,130],'native_exports':frames},indent=2)+'\n')
page='''<!doctype html><meta charset="utf-8"><title>Stirge profile attack</title><style>
body{background:#181b20;color:#eee;font:16px system-ui;max-width:1280px;margin:30px auto;padding:0 20px}p{color:#c7ccd4}section{display:flex;gap:20px;flex-wrap:wrap}.card{background:#272c34;padding:16px;border-radius:10px;margin:12px 0}canvas,img{image-rendering:pixelated;background:#101215}canvas{width:388px;height:520px}.phase img{width:291px;height:390px}button,select{font:inherit;padding:8px 14px;margin:0 10px 10px 0}.sheet{width:900px;max-width:100%}</style>
<h1>Stirge — rebuilt profile attack</h1><p>Three poses: wind up, plunge, stabilize. The body and head articulate separately at the collar; all four legs travel with the thorax. The wing stroke follows the profile silhouette.</p>
<button id="play">Pause</button><button id="next">Next pose</button><select id="speed"><option value="1">Normal speed</option><option value="2">Half speed</option></select><strong id="phase"></strong>
<section><div class="card"><h2>Previous attack</h2><canvas id="before" width="97" height="130"></canvas></div><div class="card"><h2>New profile attack</h2><canvas id="after" width="97" height="130"></canvas></div></section>
<section>'''
for n,label in [(15,'1 · Wind up'),(16,'2 · Plunge'),(17,'3 · Stabilize')]:page+=f'<div class="card phase"><h2>{label}</h2><img src="frame-{n:02}.png"></div>'
page+='''</section><h2>Approved flight profile reference</h2><img src="frame-07.png" width="600" height="520"><h2>Complete sheet</h2><img class="sheet" src="stirge-profile-attack-sheet.png"><p>Review candidate only; no game installation.</p>
<script>const ims={};let p=0,run=true,elapsed=0,last=0;const labels=['Wind up','Plunge','Stabilize'],dur=[220,120,200];for(const prefix of ['before-','frame-'])for(let n=15;n<18;n++){let im=new Image;ims[prefix+n]=im;im.onload=draw;im.src=prefix+n+'.png'}function draw(){for(const [id,prefix]of [['before','before-'],['after','frame-']]){let ctx=document.getElementById(id).getContext('2d');ctx.clearRect(0,0,97,130);let im=ims[prefix+(15+p)];if(im&&im.naturalWidth)ctx.drawImage(im,0,0)}document.getElementById('phase').textContent=labels[p]}function step(){p=(p+1)%3;elapsed=0;draw()}document.getElementById('play').onclick=()=>{run=!run;document.getElementById('play').textContent=run?'Pause':'Play'};document.getElementById('next').onclick=()=>{run=false;document.getElementById('play').textContent='Play';step()};function tick(t){if(last&&run){elapsed+=Math.min(100,t-last);if(elapsed>=dur[p]*Number(document.getElementById('speed').value))step()}last=t;requestAnimationFrame(tick)}requestAnimationFrame(tick);</script>'''
(out/'index.html').write_text(page)
print('PASS: only attack frames 15–17 changed; all 15 flight frames identical; canvas sizes and binary alpha retained.')
print(out/'index.html')
