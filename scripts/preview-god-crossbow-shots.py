#!/usr/bin/env python3
"""Offline prayer-bolt flight study, native sprites with small animated trails."""
from pathlib import Path
import shutil,subprocess
ROOT=Path(__file__).resolve().parents[1]
out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','art','god-crossbow-prayer-shots','--mkdir'],cwd=ROOT,text=True).strip())/'study-v3';out.mkdir(exist_ok=True)
author=ROOT/'dev/myworld/assets/authoring/equipment/god-crossbows/shots-v3'
for god in ['saradomin','zamorak','guthix']:shutil.copyfile(author/god/'bolt.png',out/(god+'.png'))
shutil.copyfile(author/'inputs/bolt-basic.png',out/'basic-bolt.png')
(out/'index.html').write_text('''<!doctype html><meta charset="utf-8"><title>God crossbows — prayer bolt trails</title>
<style>body{font:16px system-ui;background:#171d24;color:#e5e9ed;margin:30px}h1{font-size:25px}main{display:flex;gap:18px;flex-wrap:wrap}.card{padding:16px;background:#222b35;border:1px solid #485562;border-radius:9px}h2{margin:0;font-size:20px}canvas{display:block;background:#18212b;border-radius:5px;margin:10px 0}img,canvas{image-rendering:pixelated}small{color:#aab8c7}button{padding:7px;font:inherit;background:#34465a;color:white;border:1px solid #738299}label{padding-right:20px}</style>
<h1>Prayer shots: the existing bolt, with a small animated trail</h1>
<p>Stronger trails, brighter yellow for Saradomin, and darker brown for Guthix. The original bolt shape stays fixed.</p>
<p>Original bolt reference: <img src="basic-bolt.png" width="76" height="64" style="vertical-align:middle"> <small>4× enlargement</small></p>
<p><button id="pause">Pause</button> <label><input type="checkbox" id="trail" checked> Show trails</label><label><input type="checkbox" id="reverse"> Fire the other way</label></p><main></main>
<p><small>Offline flight preview; no activation animation or impact burst in this study. First panels use a 32-pixel frame; lower panels enlarge the same pixels 4×.</small></p>
<script>
const gods=['saradomin','zamorak','guthix'],images={};let paused=false,t=0;
for(const god of gods){const im=new Image();im.src=god+'.png';images[god]=im;const c=document.createElement('section');c.className='card';c.innerHTML='<h2>'+god[0].toUpperCase()+god.slice(1)+'</h2><small>'+({saradomin:'Gold bolt · pale sparks',zamorak:'Charcoal bolt · red wisps',guthix:'Wood bolt · green motes'})[god]+'</small><p>Native-size flight</p><canvas id="native-'+god+'" width="340" height="120"></canvas><p>4× pixel inspection</p><canvas id="large-'+god+'" width="340" height="210"></canvas>';document.querySelector('main').append(c)}
function draw(god,id,scale){const c=document.getElementById(id+'-'+god),x=c.getContext('2d');x.imageSmoothingEnabled=false;x.clearRect(0,0,c.width,c.height);const image=images[god];if(!image.complete||!image.naturalWidth)return;const phase=Math.floor(t/90)%6;const p=(t%1900)/1400;if(p>1)return;
const reverse=document.getElementById('reverse').checked;const trails=document.getElementById('trail').checked;
let cx=id==='native'?50+p*230:128+p*75,cy=id==='native'?84-p*62:142-p*50;if(reverse)cx=c.width-cx;
x.save();x.translate(Math.round(cx),Math.round(cy));if(reverse)x.scale(-1,1);
if(trails)x.drawImage(image,phase*32,0,32,32,Math.round(-16*scale),Math.round(-16*scale),32*scale,32*scale);
else x.drawImage(image,phase*32+10,4,19,16,Math.round(-6*scale),Math.round(-12*scale),19*scale,16*scale);
x.restore()}
function loop(){if(!paused)t+=16;for(const g of gods){draw(g,'native',1);draw(g,'large',4)}requestAnimationFrame(loop)}requestAnimationFrame(loop);document.getElementById('pause').onclick=()=>{paused=!paused;document.getElementById('pause').textContent=paused?'Resume':'Pause'};
</script>''')
print(out/'index.html')
