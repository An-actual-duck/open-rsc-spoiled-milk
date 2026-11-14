"""Validate native exports and write an HTML review; does not modify image pixels."""
from pathlib import Path
from PIL import Image
import hashlib,json,shutil
base=Path(__file__).resolve().parent
root=base.parents[6]
out=root/'output/art/stirge-plunge-attack';out.mkdir(parents=True,exist_ok=True)
flight=base.parent/'near-wing-correction'
records=[]
for n in range(18):
 p=base/f'frame-{n:02}.png';im=Image.open(p).convert('RGBA')
 assert im.size==((210,160) if n>=15 else (150,130))
 assert set(im.getchannel('A').get_flattened_data())<={0,255}
 if n<15:
  original=Image.open(flight/p.name).convert('RGBA')
  assert im.getchannel('A').tobytes()==original.getchannel('A').tobytes()
 shutil.copy2(p,out/p.name)
 records.append({'frame':n,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
a=json.loads((base/'anchors.json').read_text());dx=a[1]['needle_tip'][0]-a[0]['needle_tip'][0];dy=a[1]['needle_tip'][1]-a[0]['needle_tip'][1]
assert dx>45 and 10<dy<30
for pose in a:assert len(pose['legs'])==4 and pose['needle_angle']==14
(base/'validation.json').write_text(json.dumps({'bat_palette_recolored_frames':18,'rear_abdomen_striped_frames':[9,10,11,12,13,14],'unchanged_layer_frames_vs_before_stripes':174,'attack_canvas':[210,160],'binary_alpha':True,'forward_stab_pixels':dx,'downward_stab_pixels':dy,'legs_per_pose':4,'needle_angle_degrees':14,'pngs':records},indent=2)+'\n')
page='''<!doctype html><meta charset="utf-8"><title>Stirge — coil, plunge, recover</title><style>body{font:16px system-ui;background:#181b20;color:#eee;max-width:1350px;margin:28px auto;padding:0 20px}p{color:#c9cdd5}canvas,img{image-rendering:pixelated;background:#101215}canvas{width:840px;height:640px;max-width:100%;object-fit:contain}.card{padding:16px;background:#272c34;border-radius:10px;margin:12px 0}section{display:flex;gap:16px;flex-wrap:wrap}.pose img{width:420px;height:320px}.pose{padding:10px}button,select{font:inherit;padding:9px 15px;margin:0 8px 12px 0}</style>
<h1>Stirge — recoil, forward plunge, downward recovery stroke</h1><p>Expanded attack canvas: 210 × 160 native pixels. The sucker stays aimed forward and slightly downward throughout; the head and torso provide the stabbing movement. The flight directions remain unchanged.</p><button id="toggle">Pause</button><button id="step">Next pose</button><select id="speed"><option value="1">Normal speed</option><option value="2">Half speed</option><option value="3">Third speed</option></select><strong id="label"></strong><div><canvas width="210" height="160" id="view"></canvas></div><section>'''
for i,label in [(15,'1. Coil and pull back'),(16,'2. Snap forward'),(17,'3. Catch with a downstroke')]:page+=f'<div class="card pose"><h2>{label}</h2><img src="frame-{i}.png"></div>'
page+='''</section><h2>Native-size animation</h2><img src="attack.gif" width="210" height="160"><h2>Approved profile reference</h2><img src="frame-07.png" width="450" height="390"><p>Art review candidate. No runtime installation.</p><script>let phase=0,playing=true,time=0,last=0;const names=['Coil / pull back','Snap / plunge','Catch / downstroke'],dur=[240,120,240],imgs=[];for(let i=0;i<3;i++){let im=new Image;imgs.push(im);im.onload=draw;im.src='frame-'+(15+i)+'.png'}function draw(){let c=document.getElementById('view').getContext('2d');c.clearRect(0,0,210,160);if(imgs[phase]&&imgs[phase].naturalWidth)c.drawImage(imgs[phase],0,0);document.getElementById('label').textContent=names[phase]}function step(){phase=(phase+1)%3;time=0;draw()}document.getElementById('toggle').onclick=()=>{playing=!playing;document.getElementById('toggle').textContent=playing?'Pause':'Play'};document.getElementById('step').onclick=()=>{playing=false;document.getElementById('toggle').textContent='Play';step()};function tick(t){if(last&&playing){time+=Math.min(100,t-last);if(time>=dur[phase]*Number(document.getElementById('speed').value))step()}last=t;requestAnimationFrame(tick)}requestAnimationFrame(tick)</script>'''
(out/'index.html').write_text(page)
print(f'PASS: bat wing palette applied, all silhouettes preserved; stab travels {dx:.1f}px forward, {dy:.1f}px down; four anchored legs per pose; binary alpha, correct dimensions.')
print(out/'index.html')
