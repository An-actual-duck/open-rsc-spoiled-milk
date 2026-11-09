import assert from 'node:assert/strict';
import fs from 'node:fs';
const source=fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-middle-mouse.js',import.meta.url),'utf8');
const {createBrowserMiddleMouseInput}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
class Target {
  listeners=new Map();
  addEventListener(name,f){const list=this.listeners.get(name)||[];list.push(f);this.listeners.set(name,list);}
  removeEventListener(name,f){this.listeners.set(name,(this.listeners.get(name)||[]).filter(x=>x!==f));}
  fire(name,data={}){const event={button:1,buttons:4,pointerId:7,clientX:150,clientY:100,target:this,
    preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;},...data};
    for(const f of this.listeners.get(name)||[])f(event);return event;}
  setPointerCapture(id){this.capture=id;}
  releasePointerCapture(id){if(this.capture===id)this.capture=undefined;}
}
const win=new Target(),doc=new Target();doc.visibilityState='visible';
const canvas={width:960,height:540,getBoundingClientRect:()=>({left:100,top:50,right:580,bottom:320,width:480,height:270})};
doc.querySelector=()=>canvas;
let allowed=true;win.browserWorldInputAllowed=()=>allowed;
const relay=createBrowserMiddleMouseInput(win,doc),next=()=>relay.natives.Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent();
for(const button of [0,2])assert.equal(win.fire('pointerdown',{button,buttons:button===0?1:2}).stopped,undefined);
assert.equal(win.fire('pointerdown',{clientX:99}).stopped,undefined);
let pending=next();assert.equal(win.fire('pointerdown').stopped,true);
assert.deepEqual(Array.from(await pending),[1,100,100,0]);
assert.equal(relay.stats.lastPressButton,1);assert.equal(relay.stats.lastPressButtons,4);
for(let i=0;i<100;i++)win.fire('pointermove',{clientX:160+i/10,clientY:110,shiftKey:true});
win.fire('pointerup',{buttons:0,clientX:170,clientY:110});
assert.deepEqual(Array.from(await next()),[2,140,120,1]);
assert.deepEqual(Array.from(await next()),[3,140,120,0]);assert.equal(relay.stats.coalesced,99);
assert.equal(win.capture,undefined);
for(const name of ['mousedown','mouseup','auxclick','click'])assert.equal(win.fire(name).stopped,true);
assert.equal(win.fire('auxclick',{button:2}).stopped,undefined);
win.fire('pointerdown');await next();win.fire('lostpointercapture',{pointerId:8});
assert.equal(relay.stats.cancels,0);win.fire('lostpointercapture');assert.deepEqual(Array.from(await next()),[0,0,0,0]);
for(const lose of [()=>win.fire('blur'),()=>{doc.visibilityState='hidden';doc.fire('visibilitychange');doc.visibilityState='visible';},
  ()=>{allowed=false;win.fire('browser-world-readinesschange');},()=>win.fire('pointermove',{buttons:0})]){
  allowed=true;win.fire('pointerdown');await next();lose();assert.deepEqual(Array.from(await next()),[0,0,0,0]);
}
allowed=false;const presses=relay.stats.presses;assert.equal(win.fire('pointerdown').stopped,undefined);assert.equal(relay.stats.presses,presses);
allowed=true;pending=next();await assert.rejects(next(),/Only one/);relay.dispose();assert.deepEqual(Array.from(await pending),[-1,0,0,0]);
relay.dispose();assert.deepEqual(Array.from(await next()),[-1,0,0,0]);
assert.equal([...win.listeners.values()].reduce((n,list)=>n+list.length,0),0);
assert.equal([...doc.listeners.values()].reduce((n,list)=>n+list.length,0),0);
console.log('PASS middle-only DOM relay: real button1/buttons4, scaled coordinates, bounded coalescing, no vendor duplicate, cancel/gate/disposal');
