// Independent production-module race test. No browser/GL and no game overrides.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const root=process.argv[2];
const {createDisplayModes}=await import('data:text/javascript;base64,'+Buffer.from(fs.readFileSync(path.join(root,'tools/browser-gameplay/web/browser-display-modes.js'),'utf8')).toString('base64'));
class Target {
  listeners=new Map(); dataset={};
  addEventListener(n,f){this.listeners.set(n,[...(this.listeners.get(n)||[]),f]);}
  removeEventListener(n,f){this.listeners.set(n,(this.listeners.get(n)||[]).filter(v=>v!==f));}
  fire(type){for(const f of this.listeners.get(type)||[])f({type});}
}
const doc=new Target(),win={location:{search:''}},player={},dialog=new Target(),confirm=new Target(),cancel=new Target(),message={};
doc.body={dataset:{}}; doc.fullscreenEnabled=true;
dialog.open=false;dialog.showModal=()=>dialog.open=true;dialog.close=()=>dialog.open=false;
const pending=[];let exits=0;
player.requestFullscreen=()=>new Promise(resolve=>pending.push(()=>{doc.fullscreenElement=player;doc.fire('fullscreenchange');resolve();}));
doc.exitFullscreen=async()=>{exits++;doc.fullscreenElement=null;doc.fire('fullscreenchange');};
const modes=createDisplayModes({window:win,document:doc,player,dialog,confirm,cancel,message});
modes.request(2); // First runtime requests fullscreen; promise has not completed.
modes.dismiss(); // Runtime is discarded/restarted; same outer player remains.
modes.request(2); // New runtime makes its own valid request.
assert.equal(pending.length,2);
pending[1]();await new Promise(r=>setImmediate(r));
assert.equal(doc.fullscreenElement,player,'replacement request owns fullscreen');
pending[0]();await new Promise(r=>setImmediate(r));
assert.equal(doc.fullscreenElement,player,'late discarded request must not exit replacement fullscreen');
assert.equal(exits,0,'obsolete completion cannot issue an exit against a newer request');
modes.dispose();console.log('PASS old fullscreen completion cannot cancel replacement display ownership');
