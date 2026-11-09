#!/usr/bin/env node
// Explicit isolated private-fixture smoke, not a performance benchmark.
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
const run=path.resolve(process.argv[2]||''),port=Number(process.argv.find(a=>a.startsWith('--port='))?.slice(7)||8113);
const statusFile=process.argv.find(a=>a.startsWith('--server-status='))?.slice(16);
const expectedCache=process.argv.find(a=>a.startsWith('--cache='))?.slice(8)||'uncached';
const stage=JSON.parse(fs.readFileSync(path.join(run,'stage.json'),'utf8'));
if(!Number.isInteger(port)||port<1||port>65535)throw new Error('Invalid loopback HTTP port');
if(stage.credentialsCopied!==false||stage.tcp!=='127.0.0.1:43629'||stage.websocket!=='127.0.0.1:43529'||!statusFile||!['cached','uncached'].includes(expectedCache))throw new Error('Requires disposable loopback fixture, status receipt and expected cache policy');
const out=path.join(run,'plain-smoke-'+Date.now());fs.mkdirSync(out);
const profile=path.join(out,'chrome-profile'),args=['--headless=new','--no-first-run','--no-default-browser-check','--disable-dev-shm-usage','--enable-gpu','--use-gl=angle','--use-angle=gl','--ozone-platform=x11','--disable-software-rasterizer','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0','--user-data-dir='+profile,'--window-size=1200,950','about:blank'];
const browser=spawn('/usr/bin/google-chrome',args,{stdio:'ignore'}),delay=ms=>new Promise(r=>setTimeout(r,ms));
const receipt={artifact:stage,plainURL:`http://127.0.0.1:${port}/?mode=webgl`,performanceAcceptance:'NOT_A_BENCHMARK',steps:[],complete:false};
let ws,sequence=0;const pending=new Map();
const eventLog=fs.createWriteStream(path.join(out,'console-events.jsonl'));
function call(method,params={}){return new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await call('Runtime.evaluate',{expression,returnByValue:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
async function until(expression,timeout=60000){const end=Date.now()+timeout;while(Date.now()<end){if(await evaluate(expression))return;await delay(500);}throw new Error('Timed out: '+expression);}
async function point(x,y,button='left'){await call('Input.dispatchMouseEvent',{type:'mouseMoved',x,y});await call('Input.dispatchMouseEvent',{type:'mousePressed',x,y,button,clickCount:1});await delay(150);await call('Input.dispatchMouseEvent',{type:'mouseReleased',x,y,button,clickCount:1});}
async function button(id){const r=JSON.parse(await evaluate(`JSON.stringify((()=>{const r=document.querySelector(${JSON.stringify('#'+id)}).getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2}})())`));await point(r.x,r.y);}
async function bounds(){return JSON.parse(await evaluate(`JSON.stringify((()=>{const f=document.querySelector('#runtime'),o=f.getBoundingClientRect();const c=[...f.contentDocument.querySelectorAll('canvas')].filter(c=>c.id!=='browser-webgl').sort((a,b)=>b.width*b.height-a.width*a.height)[0],r=c.getBoundingClientRect();return{x:o.x+r.x,y:o.y+r.y,w:r.width,h:r.height}})())`));}
async function gamePoint(x,y,button='left'){const r=await bounds();await point(r.x+x,r.y+y,button);}
async function type(text){for(const key of text){const keycode=key.toUpperCase().charCodeAt(0),code=/[0-9]/.test(key)?'Digit'+key:'Key'+key.toUpperCase();await call('Input.dispatchKeyEvent',{type:'keyDown',key,code,text:key,unmodifiedText:key,windowsVirtualKeyCode:keycode,nativeVirtualKeyCode:keycode});await call('Input.dispatchKeyEvent',{type:'keyUp',key,code,windowsVirtualKeyCode:keycode,nativeVirtualKeyCode:keycode});await delay(20);}}
async function screenshot(name){const r=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(out,name+'.png'),Buffer.from(r.data,'base64'));}
async function save(name){const p=JSON.parse(await evaluate(`JSON.stringify((()=>{const w=document.querySelector('#runtime')?.contentWindow;return{proof:w?.browserProof,observedNow:w?.performance.now(),outer:window.browserProof}})())`));fs.writeFileSync(path.join(out,name+'.json'),JSON.stringify(p,null,2));return p.proof;}
async function offline(){const end=Date.now()+15000;while(Date.now()<end){const s=JSON.parse(fs.readFileSync(statusFile,'utf8'));if(s.private===true&&s.online===false)return;await delay(300);}throw new Error('Private account still online after explicit Stop');}
async function startAndLogin(name){
  await button('start');await delay(18000);
  await until(`(()=>{const p=document.querySelector('#runtime')?.contentWindow.browserProof;return p?.state?.mode==='LOGIN'&&(p.gpu?.javaFrames||0)>=20})()`);
  const r=await bounds();await point(r.x+r.w/2+100,r.y+r.h/2+123);await point(r.x+r.w/2-116,r.y+r.h/2+98);await type('browserdemo');await point(r.x+r.w/2-46,r.y+r.h/2+146);await type('BrowserProof13');await point(r.x+r.w/2+154,r.y+r.h/2+83);
  await until(`(()=>{const w=document.querySelector('#runtime')?.contentWindow,p=w?.browserProof;return p?.state?.mode==='GAME'&&(p.gpu?.javaWorldFrames||0)>=20&&(!p.gpu.worldReadiness||p.gpu.worldReadiness.phase==='ready')&&!p.gpu.lastError&&w.performance.now()-p.gpu.lastFrame<1500})()`,90000);
  await gamePoint(480,358);
  await until(`(()=>{const w=document.querySelector('#runtime')?.contentWindow,p=w?.browserProof;return(p?.gpu?.javaWorldFrames||0)>=150&&!p.gpu.lastError&&w.performance.now()-p.gpu.lastFrame<1000})()`);
  const p=await save(name),g=p.gpu;
  if(p.benchmark||p.clientLoop!=='modern'||!p.earlyFaceCull||!p.fullResidentOwnership||g.cpuDepthFramePresent!==false||!g.overlayCoverageEnabled||g.spriteCompositeCache?.enabled!==(expectedCache==='cached'))throw new Error('Plain-link defaults did not select verified backend');
  if(!/AMD|Radeon|NVIDIA|GeForce|Intel|Apple|Mali|Adreno/i.test(g.renderer)||/swiftshader|llvmpipe|softpipe|software/i.test(g.renderer))throw new Error('Physical WebGL device not verified: '+g.renderer);
  await screenshot(name);receipt.steps.push({name,worldFrames:g.javaWorldFrames,renderer:g.renderer,lastFrame:g.lastFrame});
}
async function stop(name){if(!await evaluate(`!!document.querySelector('#runtime')&&window.browserProof.lifecycle==='running'`))throw new Error('Runtime ended before explicit Stop: '+name);await button('stop');await until(`!document.querySelector('#runtime')&&window.browserProof.lifecycle==='stopped'`);await offline();receipt.steps.push({name,iframeRemoved:true,privateAccountOffline:true});}
try{
  let target;
  for(let i=0;i<80;i++){try{const p=Number(fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0]);const ts=await(await fetch(`http://127.0.0.1:${p}/json/list`)).json();target=ts.find(t=>t.type==='page');if(target)break;}catch{}await delay(200);}
  if(!target)throw new Error('Dedicated Chrome did not start');ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});ws.onmessage=e=>{const v=JSON.parse(e.data);if(v.id){const p=pending.get(v.id);pending.delete(v.id);v.error?p?.reject(new Error(JSON.stringify(v.error))):p?.resolve(v.result);}else eventLog.write(JSON.stringify(v)+'\n');};ws.onclose=()=>{for(const p of pending.values())p.reject(new Error('Dedicated Chrome closed'));pending.clear();};
  await call('Runtime.enable');await call('Page.enable');await call('Page.navigate',{url:receipt.plainURL});
  await startAndLogin('01-plain-world');await gamePoint(945,40);await gamePoint(735,72,'right');await screenshot('02-inventory-context');
  await call('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27,nativeVirtualKeyCode:27});await call('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27,nativeVirtualKeyCode:27});
  await stop('03-normal-stop');await startAndLogin('04-normal-restart');
  const supported=await evaluate(`(()=>{const c=document.querySelector('#runtime').contentDocument.querySelector('#browser-webgl'),gl=c.getContext('webgl2'),e=gl.getExtension('WEBGL_lose_context');if(!e)return false;e.loseContext();return true})()`);if(!supported)throw new Error('Context-loss control unavailable');
  await until(`document.querySelector('#runtime')?.contentWindow.browserProof?.gpu?.contextLost===true`);await delay(1000);const lost=await save('05-context-lost');await delay(1500);const held=await save('06-context-held');if(held.gpu.javaFrames!==lost.gpu.javaFrames)throw new Error('Lost GPU backend kept submitting frames');
  await screenshot('06-context-held');receipt.steps.push({name:'context-loss-fail-closed',submittedFrames:held.gpu.javaFrames,lastError:held.gpu.lastError||null});
  await stop('07-loss-stop');await startAndLogin('08-context-restart-world');await stop('09-final-stop');receipt.complete=true;
}catch(error){receipt.error=String(error);try{await screenshot('failed');}catch{}throw error;}
finally{fs.writeFileSync(path.join(out,'result.json'),JSON.stringify(receipt,null,2));try{if(ws?.readyState===WebSocket.OPEN)await call('Browser.close');}catch{}ws?.close();eventLog.end();if(browser.exitCode===null)browser.kill('SIGTERM');console.log('Smoke evidence:',out);}
