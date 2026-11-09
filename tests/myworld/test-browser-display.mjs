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
const doc=new Target(),player={},select=new Target(),button=new Target(),notice={textContent:''};doc.body={dataset:{}};doc.fullscreenEnabled=true;win.location={search:''};
let requests=0,exits=0;player.requestFullscreen=async()=>{requests++;doc.fullscreenElement=player;doc.fire('fullscreenchange');};doc.exitFullscreen=async()=>{exits++;doc.fullscreenElement=null;doc.fire('fullscreenchange');};
const modes=createDisplayModes({window:win,document:doc,player,select,button,notice});assert.equal(modes.stats.mode,'fit');
button.fire('click');assert.equal(requests,1,'fullscreen API requested synchronously within click activation');await Promise.resolve();assert.equal(modes.stats.mode,'fullscreen');
await doc.exitFullscreen();assert.equal(modes.stats.mode,'fit','Escape/fullscreenchange returns without runtime teardown');assert.equal(requests,1,'no automatic reentry');
select.value='embedded';select.fire('change');assert.equal(modes.stats.baseMode,'embedded');
button.fire('click');await Promise.resolve();await modes.exit();assert.equal(exits,2,'Stop explicitly exits owned fullscreen');
player.requestFullscreen=async()=>{throw new Error('denied')};button.fire('click');await new Promise(r=>setImmediate(r));assert.match(notice.textContent,/could not/);assert.equal(modes.stats.mode,'embedded');modes.dispose();
console.log('PASS display fit/aspect/full-resolution bounds, L/R/wheel CSS coordinate normalization, independent middle relay, mode/gesture/denial/Escape/Stop policy; no hardware proof');
