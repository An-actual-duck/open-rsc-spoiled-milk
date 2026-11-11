import assert from 'node:assert/strict';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
const [modulePath,corpusPath]=process.argv.slice(2);
const {validateJournal,replayJournal,createOverlayJournalObserver,observedOverlayDelta}=await import(pathToFileURL(modulePath));
const corpus=JSON.parse(fs.readFileSync(corpusPath,'utf8'));let checks=0;
const check=(value,why)=>{checks++;assert(value,why);};
function input(frame){const bits=Uint32Array.from(frame.tBits);return {width:frame.width,height:frame.height,generation:frame.generation,sequence:frame.sequence,c:Int32Array.from(frame.c),t:new Float32Array(bits.buffer),header:Int32Array.from(frame.header),ops:Int32Array.from(frame.ops),glyphs:Int32Array.from(frame.glyphs),fonts:Int8Array.from(frame.fonts),runs:Int32Array.from(frame.runs),stats:Int32Array.from(frame.stats)};}
function bytes(array){return Buffer.from(new Uint8Array(array.buffer,array.byteOffset,array.byteLength));}
function nativeArgs(value){return ['lib',value.width,value.height,value.generation,value.sequence,false,false,-1,-1,new Int32Array([0,value.height]),new Int32Array(6),value.c,value.t,value.header,value.ops,value.glyphs,value.fonts,value.runs,value.stats];}
function noBorrowed(stats,value){const borrowed=new Set([value.c,value.t,value.header,value.ops,value.glyphs,value.fonts,value.runs,value.stats]),seen=new Set();function visit(node){if(!node||typeof node!=='object'||seen.has(node))return;check(!borrowed.has(node),'Diagnostic state retained borrowed source');seen.add(node);for(const item of Object.values(node))visit(item);}visit(stats);}
check(corpus.frames.length>=8,'Actual Java corpus is nonvacuous');
for(const frame of corpus.frames){
 const value=input(frame),originalC=Buffer.from(bytes(value.c)),originalT=Buffer.from(bytes(value.t));
 const replay=replayJournal(validateJournal(value));check(bytes(replay.c).equals(originalC),'Actual Java C equals separate JS replay');check(bytes(replay.t).equals(originalT),'Actual Java raw T bits equal separate replay');
 check(bytes(value.c).equals(originalC)&&bytes(value.t).equals(originalT),'Replay never alters borrowed authoritative planes');
 const stats={},observer=createOverlayJournalObserver(stats);observer.consume(value);check(observer.counters.matched===1&&observer.counters.mismatched===0&&observer.counters.rejected===0,'Actual Java match counters');
 const args=nativeArgs(value);let calls=0;const result=observedOverlayDelta(observer,(...forwarded)=>{calls++;assert.equal(forwarded.length,13);assert.strictEqual(forwarded[11],value.c);assert.strictEqual(forwarded[12],value.t);return 72;})(...args);
 check(calls===1&&result===72,'Observed success presents original exactly once with same borrowed views');
 noBorrowed(stats,value);
}
for(const mode of ['offset-views','raw-buffers','shared-disjoint-planes','wide-tokens']){
 const value=input(corpus.frames[0]);
 if(mode==='offset-views')for(const name of ['c','t','header','ops','glyphs','fonts','runs','stats']){const old=value[name],offset=name==='fonts'?3:12,view=new old.constructor(new ArrayBuffer(old.byteLength+32),offset,old.length);new Uint8Array(view.buffer,view.byteOffset,view.byteLength).set(new Uint8Array(old.buffer,old.byteOffset,old.byteLength));value[name]=view;}
 if(mode==='raw-buffers')for(const name of ['c','t','header','ops','glyphs','fonts','runs','stats'])value[name]=value[name].buffer;
 if(mode==='shared-disjoint-planes'){const size=value.c.byteLength,buffer=new ArrayBuffer(size*2),c=new Int32Array(buffer,0,value.c.length),t=new Float32Array(buffer,size,value.t.length);c.set(value.c);new Uint8Array(buffer,size).set(new Uint8Array(value.t.buffer));value.c=c;value.t=t;}
 if(mode==='wide-tokens'){value.generation=Number.MAX_SAFE_INTEGER;value.header[12]=2097151;value.header[13]=-1;value.sequence=4294967301;value.header[14]=1;value.header[15]=5;}
 const observer=createOverlayJournalObserver({});observer.consume(value);check(observer.counters.matched===1&&observer.counters.rejected===0,'Exact adapted/view/epoch case '+mode);
}
{
 const value=input(corpus.frames[0]),bits=new Uint32Array(value.t.buffer);bits[0]=0x80000000;bits[1]=0x7fc12345;
 const before=Buffer.from(bytes(value.t)),observer=createOverlayJournalObserver({});observer.consume(value);
 check(observer.counters.mismatched===1&&observer.counters.last.mismatches>=2,'Comparison checks raw T bits including negative zero and NaN payload');
 check(bytes(value.t).equals(before),'Mismatch never patches authoritative T');
}
for(const kind of ['invalid-header','invalid-op-count','late-glyph','late-rect','generation','short-T','plane-alias','metadata-alias','font-short','run-hole']){
 const value=input(corpus.frames[0]),beforeC=Buffer.from(bytes(value.c)),beforeT=Buffer.from(bytes(value.t));
 if(kind==='invalid-header')value.header[0]=99;
 if(kind==='invalid-op-count')value.header[4]=0x7fffffff;
 if(kind==='late-glyph')value.glyphs[value.glyphs.length-2]=0x7fffffff;
 if(kind==='late-rect')value.ops[12]=-1;
 if(kind==='generation')value.generation++;
 if(kind==='short-T')value.t=value.t.subarray(0,value.t.length-1);
 if(kind==='plane-alias')value.t=new Float32Array(value.c.buffer);
 if(kind==='metadata-alias')value.stats=new Int32Array(value.c.buffer,0,32);
 if(kind==='font-short')value.fonts=value.fonts.subarray(1);
 if(kind==='run-hole')value.runs[0]=1;
 assert.throws(()=>validateJournal(value),undefined,kind);checks++;
 check(bytes(value.c).equals(beforeC),'Malformed metadata did not touch C');
 if(!['short-T','plane-alias'].includes(kind))check(bytes(value.t).equals(beforeT),'Malformed metadata did not touch T');
 let calls=0;const observer=createOverlayJournalObserver({});observedOverlayDelta(observer,(...args)=>{calls++;assert.strictEqual(args[11],value.c);return 19;})(...nativeArgs(value));
 check(calls===1&&observer.counters.rejected===1,'Malformed observer still presents original exactly once');
}
// Shape/cap rejection must not materialize input iterables or allocate resolved metadata.
{
 const value=input(corpus.frames[0]);value.header[4]=0x7fffffff;let allocations=0,iterations=0;const Native=globalThis.Int32Array;
 value.stats[Symbol.iterator]=function*(){iterations++;throw Error('Unexpected metadata iterator');};
 try{globalThis.Int32Array=new Proxy(Native,{construct(target,args){allocations++;return Reflect.construct(target,args);}});assert.throws(()=>validateJournal(value));}
 finally{globalThis.Int32Array=Native;}
 check(allocations===0&&iterations===0,'Invalid count rejected before metadata allocation/enumeration');
}
// A genuine bounded view can carry hostile methods; bounded index semantics must
// never consult these iterators/subarray overrides merely to copy diagnostics.
{
 const value=input(corpus.frames[0]);let iterations=0,subarrays=0;
 value.stats[Symbol.iterator]=function*(){iterations++;throw Error('Do not invoke input iterator');};
 value.runs.subarray=()=>{subarrays++;throw Error('Do not invoke input subarray');};
 value.ops.subarray=()=>{subarrays++;throw Error('Do not invoke input subarray');};
 const observer=createOverlayJournalObserver({});observer.consume(value);
 check(iterations===0&&subarrays===0&&observer.counters.matched===1,'Bounded diagnostics ignore hostile input methods');
}
for(const observer of [{consume(){throw Error('observer failure');},failure(){}},{consume(){throw Error('observer failure');},failure(){throw Error('failure recorder failed');}}]){
 let calls=0;const value=input(corpus.frames[0]);const wrapped=observedOverlayDelta(observer,()=>{calls++;return 33;});check(wrapped(...nativeArgs(value))===33&&calls===1,'Thrown observer cannot prevent/duplicate original');
 const originalError=new Error('Original presentation failed');calls=0;assert.throws(()=>observedOverlayDelta(observer,()=>{calls++;throw originalError;})(...nativeArgs(value)),e=>e===originalError);check(calls===1,'Original exception is preserved without retry');
 calls=0;await assert.rejects(observedOverlayDelta(observer,()=>{calls++;return Promise.reject(originalError);})(...nativeArgs(value)),e=>e===originalError);check(calls===1,'Original asynchronous rejection is preserved without retry');
}
for(const plane of ['C','T']){
 const value=input(corpus.frames[0]),stats={},observer=createOverlayJournalObserver(stats),args=nativeArgs(value);let calls=0;
 const name=plane==='C'?'Int32Array':'Float32Array',Native=globalThis[name];
 try{globalThis[name]=new Proxy(Native,{construct(target,args){if(args[0]===value.c.length)throw Error('Injected replay '+plane+' allocation failure');return Reflect.construct(target,args);}});
  const result=observedOverlayDelta(observer,()=>{calls++;return 44;})(...args);check(result===44&&calls===1&&observer.counters.rejected===1,'Actual replay allocation failure still presents original exactly once');
 }finally{globalThis[name]=Native;}
 noBorrowed(stats,value);
}
console.log(`PASS independent actual-Java replay/rawbits, validation caps and original exactly-once isolation: ${checks} checks; no GPU`);
