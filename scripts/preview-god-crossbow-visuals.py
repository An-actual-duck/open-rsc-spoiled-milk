#!/usr/bin/env python3
"""Export an offline native-scale held/effect review page into the art category."""
from pathlib import Path
import json,shutil,subprocess
ROOT=Path(__file__).resolve().parents[1]
out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','art','god-crossbow-equipped','--mkdir'],cwd=ROOT,text=True).strip())
out=out/'bolt-v5';out.mkdir(exist_ok=True)
author=ROOT/'dev/myworld/assets/authoring/equipment/god-crossbows'
for name in ['inputs','saradomin','zamorak','guthix']:
 dest=out/name;dest.mkdir(exist_ok=True)
 for p in (author/name).glob('*.png'):shutil.copyfile(p,dest/p.name)
 if name!='inputs':
  for p in (author/'bolt-v5'/name).glob('*.png'):shutil.copyfile(p,dest/p.name)
for god in ['saradomin','zamorak','guthix']:
 shutil.copyfile(ROOT/f'dev/myworld/assets/authoring/items/god-crossbows/selected-mythic-v1/{god}-crossbow.png',out/god/'icon.png')
html='''<!doctype html><meta charset="utf-8"><title>God crossbows — held palettes and holy bolts</title>
<style>body{background:#151a20;color:#e6e9ec;font:16px system-ui;margin:28px}h1{font-size:24px}main{display:flex;flex-wrap:wrap;gap:18px}.card{background:#222a33;border:1px solid #435361;border-radius:10px;padding:14px;text-align:center}canvas,img{image-rendering:pixelated}canvas{display:block;background:#171e26;margin:10px auto}button,select{font:inherit;padding:6px;background:#344352;color:white;border:1px solid #67798a}small{color:#aebdcb}#pose{min-width:180px}</style>
<h1>God crossbows: limb shape review</h1><p>Native pixels, enlarged 4×. Loaded bolts retain their exact original frame geometry. Front-diagonal limb mounts now meet the forward stock.</p>
<p><select id="pose"><option value="cycle">Cycle all five directions</option><option value="0">Front</option><option value="1">Front diagonal</option><option value="2">Side</option><option value="3">Rear diagonal</option><option value="4">Rear</option></select> <select id="frame"><option value="animate">Animate frames</option><option value="0">Frame 1</option><option value="1">Frame 2</option><option value="2">Frame 3</option></select> <button id="pause">Pause</button></p><main></main>
<p><small>Shot effects are set aside while the held designs are reviewed. No combat numbers change.</small></p>
<script>
const names=['inputs','saradomin','zamorak','guthix'],images={};let tick=0,paused=false;
for(const name of names){images[name]=[];for(let i=0;i<15;i++){const im=new Image();im.src=name+'/'+String(i).padStart(2,'0')+'.png';images[name].push(im)}
const card=document.createElement('section');card.className='card';card.innerHTML='<h2>'+({inputs:'Basic crossbow',saradomin:'Saradomin',zamorak:'Zamorak',guthix:'Guthix'})[name]+'</h2>'+(name==='inputs'?'<div style="height:96px"></div>':'<img width="144" height="96" src="'+name+'/icon.png">')+'<canvas id="held-'+name+'" width="256" height="184"></canvas>'+'<p>Held detail review</p>';document.querySelector('main').append(card);
if(name!=='inputs'){images[name].bolt=new Image();images[name].bolt.src=name+'/bolt.png';images[name].impact=new Image();images[name].impact.src=name+'/impact.png';}}
function render(){for(const name of names){const c=document.getElementById('held-'+name),ctx=c.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.clearRect(0,0,c.width,c.height);const dir=document.getElementById('pose').value;const direction=dir==='cycle'?Math.floor(tick/18)%5:Number(dir);const fixed=document.getElementById('frame').value;const f=direction*3+(fixed==='animate'?Math.floor(tick/2)%3:Number(fixed));const im=images[name][f];if(im.complete&&im.naturalWidth)ctx.drawImage(im,0,40,64,46,0,0,256,184);
if(document.getElementById('effect-'+name)){const e=document.getElementById('effect-'+name),x=e.getContext('2d');x.imageSmoothingEnabled=false;x.clearRect(0,0,256,96);const step=tick%20;if(step<12){const s=images[name].bolt;if(s.complete&&s.naturalWidth)x.drawImage(s,(step%4)*32,0,32,16,step*12,22,96,48)}else if(step<18){const s=images[name].impact;if(s.complete&&s.naturalWidth)x.drawImage(s,(step-12)*32,0,32,32,144,0,96,96)}}}}
setInterval(()=>{if(!paused)tick++;render()},100);document.getElementById('pause').onclick=()=>{paused=!paused;document.getElementById('pause').textContent=paused?'Resume':'Pause'};
</script>'''
(out/'index.html').write_text(html)
print(out/'index.html')
