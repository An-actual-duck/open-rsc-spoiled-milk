import fs from 'node:fs';
import assert from 'node:assert/strict';
import {validateJournal,replayJournal,createOverlayJournalObserver,observedOverlayDelta} from '../../tools/browser-gameplay/web/browser-overlay-journal.js';
let checks=0;function ok(x,m){checks++;assert.ok(x,m);}function throws(f,m){checks++;assert.throws(f,m);}
function make(){
 const w=16,h=12,font=new Int8Array(32);font.set([0,0,9,3,2,0,2]);font.set([0,30,31,128,229,-26],9);
 const ops=new Int32Array(32);ops.set([2,0,0,0,16,12,0,0,w,h,1,0x987654,256]);ops.set([1,1,0,font.length,0,1,0,0,w,h,1],16);
 const input={width:w,height:h,generation:1,sequence:2,c:new Int32Array(w*h),t:new Float32Array(w*h),header:new Int32Array([1,w,h,1,2,1,1,font.length,0,w*h+6,1,1,0,1,0,2]),ops,glyphs:new Int32Array([2,5,0xfedcba,0,1]),fonts:font,runs:new Int32Array([0,2,1,1,1,w*h+6]),stats:new Int32Array(32)};
 const r=replayJournal(validateJournal(input));input.c.set(r.c);input.t.set(r.t);return input;
}
const bits=a=>Array.from(new Uint32Array(a.buffer,a.byteOffset,a.length));
const input=make(),before=[Array.from(input.c),bits(input.t)];let observer=createOverlayJournalObserver();observer.consume(input);ok(observer.counters.matched===1);ok(observer.counters.last.rasterReplayComplete);ok(!('c' in observer.counters.last));assert.deepEqual([Array.from(input.c),bits(input.t)],before);checks++;
for(const change of [p=>p.header[0]=2,p=>p.header[15]=1,p=>p.header[9]--,p=>p.ops[16+4]=1,p=>p.ops[16+3]=999,p=>p.glyphs[3]=999,p=>p.runs[1]=1,p=>p.runs=new Int32Array(0),p=>p.stats[0]=-1,p=>p.ops=new Proxy(p.ops,{}),p=>p.fonts=new SharedArrayBuffer(32),p=>p.c=new Int32Array(4),p=>{p.t=new Float32Array(p.c.buffer);},p=>p.header[1]=15]){
 const p=make();change(p);const old=[Array.from(p.c),p.t instanceof Float32Array?bits(p.t):null];throws(()=>validateJournal(p));assert.deepEqual([Array.from(p.c),p.t instanceof Float32Array?bits(p.t):null],old);checks++;
}
const incomplete=make();incomplete.header[3]=0;incomplete.header[8]=1;observer.consume(incomplete);ok(observer.counters.incomplete===1&&observer.counters.last.mismatches===null);throws(()=>replayJournal(validateJournal(incomplete)));
const malformed=make();malformed.glyphs[3]=2147483647;observer.consume(malformed);ok(observer.counters.rejected===1);
// Adapter accepts genuine zero-copy ArrayBuffer payloads; exported metadata is owned.
const raw=make();for(const k of ['c','t','header','ops','glyphs','fonts','runs','stats'])raw[k]=raw[k].buffer;ok(validateJournal(raw).c.buffer===raw.c);
const offset=make();const base=new ArrayBuffer(offset.c.byteLength+64);const view=new Int32Array(base,16,offset.c.length);view.set(offset.c);offset.c=view;ok(validateJournal(offset).c===view);
const args=[{},16,12,1,2,false,false,0,0,new Int32Array(0),new Int32Array(0),input.c,input.t,input.header,input.ops,input.glyphs,input.fonts,input.runs,input.stats];
for(const bad of [{consume(){throw Error('observer allocation');},failure(){throw Error('report failure');}}, {consume(){},failure(){}}]){
 let calls=0;const wrapped=observedOverlayDelta(bad,(...a)=>{calls++;ok(a.length===13&&a[11]===input.c&&a[12]===input.t);return 'same';});ok(wrapped(...args)==='same'&&calls===1);
 const e=Error('original');calls=0;throws(()=>observedOverlayDelta(bad,()=>{calls++;throw e;})(...args),e);ok(calls===1);
 calls=0;await assert.rejects(observedOverlayDelta(bad,()=>{calls++;return Promise.reject(e);})(...args),e);checks++;ok(calls===1);
}
observer=createOverlayJournalObserver();observedOverlayDelta(observer,()=>{})(...args);ok(observer.counters.matched===1);
console.log(`PASS ${checks} isolated journal schema/no-write/alias/lifecycle/delegation checks (source-bound Java corpus is a separate gate)`);

if(process.argv[2]){
 const corpus=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));let words=0;
 for(const raw of corpus.frames){
  const p={...raw};for(const key of ['c','header','ops','glyphs','runs','stats'])p[key]=new Int32Array(raw[key]);p.fonts=new Int8Array(raw.fonts);p.t=new Float32Array(new Uint32Array(raw.tBits).buffer);
  const plan=validateJournal(p),replay=replayJournal(plan);assert.deepEqual(Array.from(replay.c),raw.c);assert.deepEqual(bits(replay.t),raw.tBits);words+=p.c.length*2;
  const o=createOverlayJournalObserver();o.consume(p);assert.equal(o.counters.matched,1);assert.equal(o.counters.last.mismatches,0);
 }
 console.log(`PASS actual Java literal corpus: ${corpus.frames.length} frames, ${words} exact C/raw-T words`);
}
