import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createSurfaceAlignment,surfaceAlignmentPolicy} from '../../tools/browser-gameplay/web/browser-surface-alignment.js';
const base='325bf42b60fe411705f9f14c0a3ef6debabe24a7';
const source=execFileSync('git',['show',base+':tools/browser-gameplay/web/browser.js'],{encoding:'utf8'});
const start=source.indexOf('function alignWorldSurface(width,height){');let braces=0,end=start;
for(let n=source.indexOf('{',start);n<source.length;n++){if(source[n]==='{')braces++;if(source[n]==='}'&&--braces===0){end=n+1;break;}}
const original=source.slice(start,end);assert.match(original,/querySelectorAll/);
class Events {
  listeners=new Map();style={};
  addEventListener(type,callback){const list=this.listeners.get(type)||[];list.push(callback);this.listeners.set(type,list);}
  removeEventListener(type,callback){this.listeners.set(type,(this.listeners.get(type)||[]).filter(value=>value!==callback));}
  fire(type){for(const callback of this.listeners.get(type)||[])callback({type});}
}
function environment(){
  const win=new Events(),doc=new Events(),display=new Events(),canvas=new Events(),viewport=new Events();
  let rectReads=0,scans=0;const observers=[],mutations=[];
  const state={scale:1,left:20,top:40,parentLeft:0,parentTop:0,animations:[]};win.devicePixelRatio=1;
  win.ResizeObserver=class {nodes=new Set();constructor(callback){this.callback=callback;observers.push(this);}observe(node){this.nodes.add(node);}unobserve(node){this.nodes.delete(node);}disconnect(){this.nodes.clear();}};
  win.MutationObserver=class {active=false;constructor(callback){this.callback=callback;mutations.push(this);}observe(){this.active=true;}disconnect(){this.active=false;}};
  function awt(width=960,height=560){const target={width,height,isConnected:true,style:{},getBoundingClientRect(){rectReads++;return {left:state.left,top:state.top,width:target.width*state.scale,height:target.height*state.scale};}};return target;}
  const targets=[awt()];canvas.width=960;canvas.height=540;canvas.parentElement=display;
  display.querySelectorAll=()=>{scans++;return [...targets,canvas];};display.getBoundingClientRect=()=>{rectReads++;return {left:state.parentLeft,top:state.parentTop};};
  doc.querySelector=name=>name==='#display'?display:viewport;doc.documentElement={};doc.getAnimations=()=>state.animations;
  let finishFonts;doc.fonts=new Events();doc.fonts.ready=new Promise(resolve=>finishFonts=resolve);
  const layout={get scale(){return state.scale;},setSize(){}};
  return {win,doc,display,canvas,layout,state,targets,awt,observers,mutations,finishFonts,
    measure:()=>({rectReads,scans}),notify(record){for(const observer of mutations)if(observer.active)observer.callback([record]);},resize(node){for(const observer of observers)if(observer.nodes.has(node))observer.callback([]);}};
}
const old=environment(),live=environment(),cached=environment();
const oldAlign=Function('document','canvas','displayLayout','updateWorldCover','return ('+original+');')(old.doc,old.canvas,old.layout,()=>{});
const control=createSurfaceAlignment({window:live.win,document:live.doc,display:live.display,canvas:live.canvas,layout:()=>live.layout,control:true});
const candidate=createSurfaceAlignment({window:cached.win,document:cached.doc,display:cached.display,canvas:cached.canvas,layout:()=>cached.layout,cached:true,control:true});
let checks=0;
function step(name,mutation=()=>{},w=960,h=540){for(const env of [old,live,cached])mutation(env);oldAlign(w,h);control.align(w,h);candidate.align(w,h);assert.deepEqual(live.canvas.style,old.canvas.style,name+' live differs from frozen');assert.deepEqual(cached.canvas.style,old.canvas.style,name+' cached differs from frozen');checks+=2;}
step('initial titlebar');assert.equal(cached.canvas.style.top,'60px');
for(let n=0;n<100;n++)step('steady'+n);
assert.equal(candidate.stats.layoutReads,2);assert.equal(candidate.stats.targetScans,1);assert.equal(candidate.stats.cacheHits,100);
step('scaled resize',env=>{env.state.scale=1.25;env.win.fire('resize');});
step('ancestor scroll',env=>{env.state.left=141;env.state.parentLeft=50;env.win.fire('scroll');});
step('fullscreen',env=>{env.state.top=0;env.doc.fire('fullscreenchange');});
step('DPR',env=>{env.win.devicePixelRatio=2;});
step('AWT dimension',env=>{env.targets[0].width=800;env.targets[0].height=620;env.notify({target:env.targets[0],attributeName:'width'});},800,600);
step('display event',env=>{env.state.scale=.75;env.win.fire('browser-display-change');},800,600);
step('logical size only',()=>{},640,480);
for(const attributeName of ['id','data-layout','class','hidden','style'])step('ancestor '+attributeName,env=>{env.state.left+=3;env.notify({target:env.display,attributeName});});
step('stylesheet text',env=>{env.state.top+=5;env.notify({target:{nodeType:3},type:'characterData'});});
step('canvas replacement',env=>{env.targets[0].isConnected=false;env.targets[0]=env.awt(1200,700);env.notify({target:env.display,type:'childList'});});
step('RO target',env=>{env.state.top+=2;env.resize(env.targets[0]);});
step('late font metrics',env=>{env.state.left+=2;env.doc.fonts.fire('loadingdone');});
step('stylesheet resource load',env=>{env.state.left+=4;env.doc.fire('load');});
const reads=candidate.stats.layoutReads;
step('owned backing dimensions',env=>{env.canvas.width=1920;env.canvas.height=1080;env.notify({target:env.canvas,attributeName:'width'});});
assert.equal(candidate.stats.layoutReads,reads,'GPU backing changed logical layout');
step('owned exact style commit',env=>env.notify({target:env.canvas,attributeName:'style'}));assert.equal(candidate.stats.layoutReads,reads,'own CSS write dirtied itself');
step('externally changed GPU CSS',env=>{env.canvas.style.left='-999px';env.notify({target:env.canvas,attributeName:'style'});});
// Preexisting A plus later B: A ending must never consume B's motion identity.
for(const env of [old,live,cached])env.state.animations=[{playState:'running'}];candidate.queueCached(false);candidate.align(960,540);candidate.queueCached(true);
step('enable during A');
step('B starts',env=>{env.state.animations.push({playState:'running'});env.doc.fire('animationstart');env.state.left++;});
step('A ends while B runs',env=>{env.state.animations[0].playState='finished';env.doc.fire('animationend');env.state.left++;});
const movingReads=candidate.stats.layoutReads;step('B still moving',env=>env.state.left++);assert.equal(candidate.stats.layoutReads,movingReads+2,'unfinished B stopped live alignment');
step('B ends',env=>{env.state.animations[1].playState='finished';env.doc.fire('animationend');});const endedReads=candidate.stats.layoutReads;step('animation settled');assert.equal(candidate.stats.layoutReads,endedReads);
// Live OFF target replacement, then cached ON, must attach the new target.
candidate.queueCached(false);step('live switch');step('replace while live',env=>{env.targets[0].isConnected=false;env.targets[0]=env.awt(960,560);env.notify({target:env.display,type:'childList'});});candidate.queueCached(true);step('reenable after replacement');assert(cached.observers.some(observer=>observer.nodes.has(cached.targets[0])));
const beforeDispose={...cached.canvas.style};candidate.dispose();candidate.dispose();for(const observer of cached.mutations)observer.callback([{target:cached.display}]);cached.finishFonts();await Promise.resolve();assert.deepEqual(cached.canvas.style,beforeDispose);assert(cached.observers.every(observer=>observer.nodes.size===0));assert([...cached.win.listeners.values()].every(list=>list.length===0));assert.throws(()=>candidate.align(960,540),/disposed/);
control.dispose();
const serialized=environment();serialized.state.left=20.123456789;
serialized.canvas.style=new Proxy({},{set(target,key,value){target[key]=typeof value==='string'&&value.endsWith('px')?Number(Number(value.slice(0,-2)).toPrecision(6))+'px':value;return true;}});
const serializedModule=createSurfaceAlignment({window:serialized.win,document:serialized.doc,display:serialized.display,canvas:serialized.canvas,layout:()=>serialized.layout,cached:true});
serializedModule.align(960,540);assert.equal(serialized.canvas.style.left,'20.1235px');const serializedReads=serializedModule.stats.layoutReads;
for(let n=0;n<10;n++){serialized.notify({target:serialized.canvas,attributeName:'style'});serializedModule.align(960,540);}
assert.equal(serializedModule.stats.layoutReads,serializedReads,'normalized CSS own write caused an invalidation loop');assert.equal(serializedModule.stats.cacheHits,10);serializedModule.dispose();
const unsupported=environment();delete unsupported.win.MutationObserver;const fallback=createSurfaceAlignment({window:unsupported.win,document:unsupported.doc,display:unsupported.display,canvas:unsupported.canvas,cached:true});fallback.align(960,540);fallback.align(960,540);assert.equal(fallback.stats.active,false);assert.equal(fallback.stats.layoutReads,4);fallback.dispose();
for(const mode of ['software','webgl'])assert.equal(surfaceAlignmentPolicy(mode,new URLSearchParams('alignment=on')),false);
assert.equal(surfaceAlignmentPolicy('webgl',new URLSearchParams('benchmark=1&alignment=on')),true);assert.equal(surfaceAlignmentPolicy('webgl',new URLSearchParams('benchmark=1')),false);assert.throws(()=>surfaceAlignmentPolicy('webgl',new URLSearchParams('benchmark=1&alignment=bad')),/alignment/);
console.log(JSON.stringify({pass:true,checks,frozenSource:base,originalFunctionSha256:createHash('sha256').update(original).digest('hex'),oldReads:old.measure(),liveReads:live.measure(),cachedReads:cached.measure(),scope:'CPU frozen-source DOM/CSS/layout/input-bounds oracle only; no browser/GPU or FPS claim'}));
