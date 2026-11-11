import {createTextKernel}from './kernel.mjs';
const kernel=createTextKernel(),result={completed:false,publicChanged:false,scope:'Isolated actual CheerpJ synchronous TEXT ABI/cost only; no game, GPU or gameplay FPS',events:[],rows:[],kernel:kernel.stats};window.textRasterProbe=result;
const status=document.getElementById('status'),output=document.getElementById('result');
document.getElementById('start').onclick=async()=>{document.getElementById('start').disabled=true;try{
 result.manifest=await fetch('./stage-manifest.json',{cache:'no-store'}).then(r=>r.json());
 await new Promise((resolve,reject)=>{const s=document.createElement('script');s.src='https://cjrtnc.leaningtech.com/5.0/loader.js';s.onload=resolve;s.onerror=reject;document.head.append(s);});
 await cheerpjInit({version:8,disableScaling:true,natives:{Java_orsc_graphics_two_LiteralOverlayReference_raster:kernel.native,
  async Java_orsc_graphics_two_LiteralOverlayReference_report(lib,text){const row=JSON.parse(String(text));if(row.phase==='result')result.rows.push(row);else result.events.push(row);status.textContent=row.phase+': '+(row.mode||'')+' '+(row.glyphs??'');}
 }});
 const code=await cheerpjRunMain('orsc.graphics.two.LiteralOverlayReference','/app/probe.jar');if(code!==0||kernel.stats.poisoned)throw Error('Java exit '+code+' / '+kernel.stats.error);
 if(!result.events.some(x=>x.phase==='exact-pass')||result.rows.length!==30)throw Error('Missing exact gate/timing cohorts');result.completed=true;status.textContent='Complete: exact gate passed; inspect timing limits';
 }catch(error){result.error=String(error);status.textContent='FAILED: '+error;}output.textContent=JSON.stringify(result,null,2);};
