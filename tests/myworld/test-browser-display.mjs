// Pure presentation/input policy controls. No browser/GPU or FPS claim.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const load=async name=>import('data:text/javascript;base64,'+Buffer.from(fs.readFileSync(new URL('../../tools/browser-gameplay/web/'+name,import.meta.url),'utf8')).toString('base64'));
const {fitRect,createBrowserDisplay}=await load('browser-display.js');
const {createDisplayModes}=await load('browser-display-modes.js');
class Target {
  listeners=new Map();style={};dataset={};
  addEventListener(n,f){this.listeners.set(n,[...(this.listeners.get(n)||[]),f]);}
  removeEventListener(n,f){this.listeners.set(n,(this.listeners.get(n)||[]).filter(x=>x!==f));}
  dispatchEvent(e){for(const f of this.listeners.get(e.type)||[])f(e);return true;}
  fire(type,data={}){const e={type,...data};this.dispatchEvent(e);return e;}
}
for(const scale of [.75,1,1.5]){
 const r=fitRect(960,540,960*scale,540*scale);assert.equal(r.scale,scale);assert.equal(r.left,0);assert.equal(r.top,0);
}
assert.deepEqual(fitRect(960,540,1440,1080),{scale:1.5,left:0,top:135});
assert.deepEqual(fitRect(960,540,1920,540),{scale:1,left:480,top:0});
const win=new Target(),display=new Target(),viewport={getBoundingClientRect:()=>({width:1440,height:1080})};win.devicePixelRatio=2;
display.getBoundingClientRect=()=>({left:30,top:135,width:1440,height:810});
const layout=createBrowserDisplay({window:win,document:{},display,viewport});
assert.equal(layout.stats.internalWidth,960);assert.equal(layout.stats.internalHeight,540);assert.equal(layout.stats.devicePixelRatio,2);
assert.equal(display.style.transform,'scale(1.5)');
for(const type of ['pointerdown','pointermove','pointerup','wheel','contextmenu']){
 const e=display.fire(type,{button:0,buttons:1,clientX:30+1440,clientY:135+810});
 assert.equal(e.clientX,30+960);assert.equal(e.clientY,135+540,'bottom 12px band retained');
}
const middle=display.fire('pointerdown',{button:1,buttons:4,clientX:600,clientY:300});assert.equal(middle.clientX,600,'middle relay receives original CSS coordinates');
layout.setSize(800,600);assert.equal(layout.stats.internalWidth,800);assert.equal(layout.stats.internalHeight,600);assert.equal(layout.scale,1.8);
layout.setSize(1e9,1e9);assert.equal(layout.stats.internalWidth,800);layout.dispose();layout.dispose();assert.equal([...display.listeners.values()].flat().length,0);
const doc=new Target(),player={},confirm=new Target(),cancel=new Target(),dialog=new Target(),message={textContent:''};doc.body={dataset:{}};doc.fullscreenEnabled=true;win.location={search:''};
dialog.open=false;dialog.showModal=()=>{dialog.open=true;};dialog.close=()=>{dialog.open=false;};
let requests=0,exits=0,refocus=0;player.requestFullscreen=async()=>{requests++;doc.fullscreenElement=player;doc.fire('fullscreenchange');};doc.exitFullscreen=async()=>{exits++;doc.fullscreenElement=null;doc.fire('fullscreenchange');};
const modes=createDisplayModes({window:win,document:doc,player,dialog,confirm,cancel,message,onReturn:()=>refocus++});assert.equal(modes.stats.mode,'fit');
modes.request(2);assert.equal(requests,1,'first fullscreen API attempt is synchronous, before any await');await Promise.resolve();assert.equal(modes.stats.mode,'fullscreen');assert.equal(dialog.open,false,'successful direct entry needs no extra confirmation');
await doc.exitFullscreen();assert.equal(modes.stats.mode,'fit','Escape/fullscreenchange returns without runtime teardown');assert.equal(requests,1,'no automatic reentry');
modes.request(1);assert.equal(modes.stats.baseMode,'embedded');
modes.request(2);await Promise.resolve();await modes.exit();assert.equal(exits,2,'Stop explicitly exits owned fullscreen');
player.requestFullscreen=async()=>{throw new Error('denied')};modes.request(2);await new Promise(r=>setImmediate(r));assert.equal(dialog.open,true,'lost/denied activation offers a transient fresh gesture');assert.match(message.textContent,/Enter fullscreen/);assert.equal(modes.stats.mode,'embedded');
player.requestFullscreen=async()=>{requests++;doc.fullscreenElement=player;doc.fire('fullscreenchange');};confirm.fire('click');assert.equal(requests,3,'confirmation uses its own real click before awaiting');await Promise.resolve();assert.equal(dialog.open,false);assert.equal(modes.stats.mode,'fullscreen');
await modes.exit();player.requestFullscreen=async()=>{throw new Error('denied')};modes.request(2);await new Promise(r=>setImmediate(r));const escape={preventDefault(){this.prevented=true;}};dialog.fire('cancel',escape);assert.equal(dialog.open,false);assert.ok(refocus>0);assert.equal(modes.stats.mode,'embedded');
// A fullscreen promise completing after Stop must not leave the discarded game fullscreen.
let resolve;player.requestFullscreen=()=>new Promise(r=>resolve=()=>{doc.fullscreenElement=player;doc.fire('fullscreenchange');r();});modes.request(2);modes.dismiss();resolve();await new Promise(r=>setImmediate(r));assert.equal(doc.fullscreenElement,null);assert.equal(dialog.open,false);
modes.dispose();assert.equal([...doc.listeners.values()].flat().length,0);
const unavailable=createDisplayModes({window:win,document:doc,player:{},dialog,confirm,cancel,message});unavailable.request(2);assert.equal(dialog.open,true);assert.equal(confirm.disabled,true);assert.match(message.textContent,/unavailable/);unavailable.dispose();
console.log('PASS fit/aspect/full-resolution input bounds, direct fullscreen attempt, transient gesture fallback, denial/Escape/Stop race and cleanup; no hardware proof');
