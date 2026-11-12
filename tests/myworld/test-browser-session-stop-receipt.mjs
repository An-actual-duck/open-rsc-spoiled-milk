#!/usr/bin/env -S node --experimental-vm-modules
// Actual outer session module with queued structured-cloned messages; no browser/GPU.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source=fs.readFileSync(process.argv[2]||new URL('../../tools/browser-gameplay/web/session.js',import.meta.url),'utf8');
let checks=0;
function equal(a,b,message){assert.deepEqual(a,b,message);checks++;}
async function harness(){
 const listeners={},tasks=[],frames=[];let removed=0,disposed=0,exited=0,focused=0;const displayRequests=[];
 const controls=Object.fromEntries(['start','stop','status','session','player','welcome','display-confirm','display-enter','display-cancel','display-message','release-label','runtime-credit','download-client','startup-progress','startup-detail'].map(k=>[k,{setAttribute(name,value){this[name]=value;},removeAttribute(name){delete this[name];},addEventListener(type,fn){this[type]=fn;},focus(){focused++;}}]));
 controls.session.append=f=>frames.push(f);
 const window={addEventListener:(name,fn)=>listeners[name]=fn};
 const document={querySelector:s=>controls[s.slice(1)],hasFocus:()=>true,addEventListener(){},createElement(){const f={setAttribute(){},focus(){},remove(){removed++;},contentWindow:{dispatchEvent(){}}};return f;}};
 const context=vm.createContext({window,document,URL,URLSearchParams,location:{search:'?wholeram=on',origin:'http://private'},performance:{now:()=>Date.now()},Event:class{constructor(type){this.type=type;}}});
 const display=new vm.SyntheticModule(['createDisplayModes'],function(){this.setExport('createDisplayModes',()=>({stats:{mode:'fit'},dismiss(){},request(action){displayRequests.push(action);},exit(){exited++;}}));},{context});
 const policy=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-public-alpha.js',import.meta.url),'utf8'),{context});
 const startupModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-startup-progress.js',import.meta.url),'utf8'),{context});
 const config=new vm.SyntheticModule(['default'],function(){this.setExport('default',null);},{context});
 const module=new vm.SourceTextModule(source,{context,initializeImportMeta(meta){meta.url='http://private/assets/test/session.js';}});await module.link(spec=>{if(spec==='./browser-startup-progress.js')return startupModule;if(spec==='./browser-public-alpha.js')return policy;if(spec==='./browser-deployment-config.js')return config;assert.equal(spec,'./browser-display-modes.js');return display;});await module.evaluate();
 function send(f,data,origin='http://private'){const copied=structuredClone(data);tasks.push(()=>listeners.message({source:f.contentWindow,origin,data:copied}));}
 function drain(){while(tasks.length)tasks.shift()();}
 function start(){controls.start.click();const f=frames.at(-1);f.contentWindow.browserSessionDispose=()=>{disposed++;send(f,{type:'browser-session-proof',proof:{wholeRamJava:{sourceReady:false,preparedReady:false,disposed:true}}});};return f;}
 return {window,controls,listeners,send,drain,start,displayRequests,counts:()=>[removed,disposed,exited,focused]};
}
const ready=()=>({wholeRamRequested:true,wholeRamJava:{sourceReady:true,preparedReady:true,rawFiles:1797,rawBytes:46101718,recipes:445,queryCells:1330,activations:8,retainedPrimitiveBytes:275990134},wholeRamResidency:{requested:true,sourceReady:true,preparedReady:true,gpuReady:true,ready:true,disposed:false,sameGpuCatalog:true},gpu:{wholeZone:{ready:true,pinnedChunks:1474,rawSourceCPUReady:true,preparedCPUReady:true}}});
function stopped(h){const p=h.window.browserProof;equal(p.lifecycle,'stopped');equal(p.stopped,true);equal(p.wholeRamJava.sourceReady,false);equal(p.wholeRamJava.preparedReady,false);equal(p.wholeRamJava.disposed,true);equal(p.wholeRamJava.activations,8);equal(p.wholeRamJava.retainedPrimitiveBytes,275990134);equal(p.wholeRamResidency.ready,false);equal(p.wholeRamResidency.gpuReady,false);equal(p.wholeRamResidency.disposed,true);equal(p.gpu.wholeZone.rawSourceCPUReady,false);equal(p.gpu.wholeZone.preparedCPUReady,false);equal(p.gpu.wholeZone.pinnedChunks,1474,'Historical resource count is preserved, not interpreted as live ownership');}
const h=await harness(),first=h.start(),child=ready();h.send(first,{type:'browser-session-proof',proof:child,status:'ready'});h.drain();equal(h.window.browserProof.wholeRamResidency.ready,true);child.wholeRamJava.activations=99;equal(h.window.browserProof.wholeRamJava.activations,8,'Parent proof is a structured clone');
h.controls.stop.click();stopped(h);equal(h.counts(),[1,1,1,1]);h.drain();stopped(h);equal(h.counts(),[1,1,1,1],'Queued final child proof cannot revive or redispose the frame');
const second=h.start();equal(h.window.browserProof.lifecycle,'starting');equal(h.window.browserProof.wholeRamJava,undefined);h.send(first,{type:'browser-session-proof',proof:ready()});h.send(first,{type:'browser-session-stop',reason:'old delayed exit'});h.send(second,{type:'browser-session-proof',proof:ready()},'http://other');h.drain();equal(h.window.browserProof.lifecycle,'starting','Old frame and foreign-origin filters remain strict');
h.send(second,{type:'browser-session-proof',proof:ready()});h.drain();second.contentWindow.browserSessionDispose=()=>{throw Error('cleanup failed');};h.send(second,{type:'browser-session-stop',reason:'Java exited'});h.drain();stopped(h);equal(h.window.browserProof.stopReason,'Java exited');equal(h.counts()[0],2,'Cleanup error still removes child and clears parent readiness');
const third=h.start();h.send(third,{type:'browser-session-proof',proof:ready()});h.drain();h.listeners.pagehide();stopped(h);h.drain();stopped(h);
const ordinary=await harness(),plain=ordinary.start();ordinary.send(plain,{type:'browser-session-proof',proof:{state:{mode:'LOGIN'}}});ordinary.drain();ordinary.controls.stop.click();ordinary.drain();equal(ordinary.window.browserProof.lifecycle,'stopped');equal(ordinary.window.browserProof.wholeRamJava,undefined,'Ordinary Stop does not invent RAM state');equal(ordinary.window.browserProof.wholeRamResidency,undefined);
// Transient startup and fatal recovery replace permanent toolbar chrome.
const lifecycle=await harness(),game=lifecycle.start();equal(lifecycle.controls.welcome.hidden,false);equal(lifecycle.controls.start.hidden,true);equal(lifecycle.controls.stop.hidden,false);
lifecycle.send(game,{type:'browser-session-proof',proof:{state:{mode:'LOGIN'}}});lifecycle.drain();equal(lifecycle.controls.welcome.hidden,true,'login has no permanent outer toolbar');
lifecycle.window.browserSessionDisplayRequest(game.contentWindow,1);equal(lifecycle.displayRequests,[1]);
lifecycle.window.browserSessionDisplayRequest({},2);equal(lifecycle.displayRequests,[1],'foreign/stale window cannot change display mode');
lifecycle.send(game,{type:'browser-session-proof',proof:{state:{mode:'GAME'},gpu:{worldReadiness:{phase:'streaming'}}}});lifecycle.drain();equal(lifecycle.controls.welcome.hidden,true,'normal travel does not restore startup screen');
lifecycle.listeners.blur();equal(lifecycle.window.browserProof.lifecycle,'running','blur is not a stop');
lifecycle.send(game,{type:'browser-session-proof',proof:{state:{mode:'GAME'},gpu:{worldReadiness:{phase:'error'}}}});lifecycle.drain();equal(lifecycle.controls.welcome.hidden,false);equal(lifecycle.controls.start.textContent,'Restart game');equal(lifecycle.controls.stop.textContent,'Close game');
const replacement=lifecycle.start();equal(lifecycle.counts()[0],1,'retry disposes old frame once');equal(lifecycle.window.browserProof.lifecycle,'starting');
equal(lifecycle.window.browserSessionDisplayRequest(game.contentWindow,2),null,'discarded frame cannot change display after retry');
lifecycle.send(game,{type:'browser-session-stop',reason:'delayed old fatal'});lifecycle.drain();equal(lifecycle.window.browserProof.lifecycle,'starting');
lifecycle.send(replacement,{type:'browser-session-stop',reason:'bootstrap failed'});lifecycle.drain();equal(lifecycle.controls.welcome.hidden,false);equal(lifecycle.controls.start.hidden,false);equal(lifecycle.controls.start.disabled,false);equal(lifecycle.controls.stop.hidden,true);
// Progress comes only from current owner, closes at login/fatal/Stop, and never masks travel.
const progressHarness=await harness(),progressFrame=progressHarness.start();
const progress=(sequence,stage,bytes=0,totalBytes=null)=>({version:1,sequence,stage,bytes,totalBytes,writtenBytes:null,files:0,totalFiles:null,elapsedMillis:sequence*10,workMillis:null});
progressHarness.send(progressFrame,{type:'browser-session-startup',progress:progress(1,'archives',50,100)});progressHarness.drain();
equal(progressHarness.controls.status.textContent,'Loading and checking game files…');equal(progressHarness.controls['startup-progress'].value,50);equal(progressHarness.controls['startup-progress'].max,100);
progressHarness.send(progressFrame,{type:'browser-session-startup',progress:progress(2,'runtime-download')});progressHarness.drain();equal(progressHarness.controls['startup-progress'].value,undefined,'unknown totals are indeterminate');
progressHarness.send(progressFrame,{type:'browser-session-startup',progress:progress(3,'archives',80,100)});progressHarness.drain();equal(progressHarness.controls.status.textContent,'Loading game runtime…','regressive phases rejected');
progressHarness.send(progressFrame,{type:'browser-session-startup',progress:progress(4,'client')},'https://evil');progressHarness.drain();equal(progressHarness.controls.status.textContent,'Loading game runtime…');
progressHarness.send(progressFrame,{type:'browser-session-proof',proof:{state:{mode:'LOGIN'}}});progressHarness.drain();equal(progressHarness.window.browserProof.startupParent.complete,true);equal(progressHarness.controls['startup-progress'].hidden,true);
progressHarness.send(progressFrame,{type:'browser-session-startup',progress:progress(5,'client')});progressHarness.drain();equal(progressHarness.controls.welcome.hidden,true);equal(progressHarness.controls.status.textContent,'Loading game runtime…','late startup cannot affect login or travel');
const retryProgress=progressHarness.start();progressHarness.send(progressFrame,{type:'browser-session-startup',progress:progress(6,'client')});progressHarness.drain();equal(progressHarness.controls.status.textContent,'Opening game…','stale generation cannot alter retry');
progressHarness.send(retryProgress,{type:'browser-session-proof',proof:{connectionError:true}});progressHarness.drain();const fatalStatus=progressHarness.controls.status.textContent;
progressHarness.send(retryProgress,{type:'browser-session-startup',progress:progress(1,'archives',10,100)});progressHarness.drain();equal(progressHarness.controls.status.textContent,fatalStatus,'late progress cannot hide fatal recovery');
const html=fs.readFileSync(new URL('../../tools/browser-gameplay/web/index.html',import.meta.url),'utf8');assert.ok(!/<header|<select|<details/i.test(html),'no permanent toolbar or engineering detail section');checks++;
console.log('PASS '+checks+' queued-message parent Stop/exit/pagehide readiness, historical counts, cleanup failure and old-session/origin filter checks; no browser/GPU proof');
