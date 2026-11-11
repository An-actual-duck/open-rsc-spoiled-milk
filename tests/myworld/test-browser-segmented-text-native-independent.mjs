import assert from 'node:assert/strict';
import fs from 'node:fs';import {pathToFileURL} from 'node:url';
const [modulePath,corpusPath]=process.argv.slice(2),{createSegmentedTextKernel}=await import(pathToFileURL(modulePath)),corpus=JSON.parse(fs.readFileSync(corpusPath));
const proto=Object.getPrototypeOf(Uint8Array.prototype),get=Object.fromEntries(['buffer','byteOffset','byteLength','length'].map(k=>[k,Object.getOwnPropertyDescriptor(proto,k).get]));
const bytes=a=>Buffer.from(new Uint8Array(get.buffer.call(a),get.byteOffset.call(a),get.byteLength.call(a)));
const words=a=>new Float32Array(Uint32Array.from(a).buffer);let checks=0,totalWords=0;
const check=(v,why)=>{checks++;assert(v,why);};
function input(p){return {h:Int32Array.from(p.header),x:Int32Array.from(p.texts),p:Int32Array.from(p.plots),f:Int8Array.from(p.fonts),c:Int32Array.from(p.beforeC),t:words(p.beforeT),g:p.generation,s:p.sequence};}
const call=(k,v)=>k.native(null,v.h,v.x,v.p,v.f,v.c,v.t,v.g,v.s);
function unchanged(k,v,status=0){const c=Buffer.from(bytes(v.c)),t=Buffer.from(bytes(v.t));check(call(k,v)===status,'Expected transactional native status');check(bytes(v.c).equals(c)&&bytes(v.t).equals(t),'Rejected packet wrote C/T');}
check(corpus.packets.length>=8,'Actual producer packet corpus nonvacuous');
const sequential=createSegmentedTextKernel();
for(const p of corpus.packets){const v=input(p);check(call(sequential,v)===p.status,'Actual producer ordered native status');check(bytes(v.c).equals(Buffer.from(Int32Array.from(p.afterC).buffer)),'Java reference C over arbitrary prior C');check(bytes(v.t).equals(Buffer.from(Uint32Array.from(p.afterT).buffer)),'Java reference raw T over arbitrary prior T');totalWords+=v.c.length+v.t.length;}
for(const mode of ['offset','buffers','shared-disjoint','hostile-own-shape','subclass']){
 const p=corpus.packets[0],v=input(p);
 if(mode==='offset')for(const name of ['h','x','p','f','c','t']){const old=v[name],off=name==='f'?3:12,view=new old.constructor(new ArrayBuffer(old.byteLength+32),off,old.length);new Uint8Array(view.buffer,view.byteOffset,view.byteLength).set(new Uint8Array(old.buffer,old.byteOffset,old.byteLength));v[name]=view;}
 if(mode==='shared-disjoint'){const size=v.c.byteLength,b=new ArrayBuffer(size*2),c=new Int32Array(b,0,v.c.length),t=new Float32Array(b,size,v.t.length);c.set(v.c);new Uint8Array(b,size).set(new Uint8Array(v.t.buffer));v.c=c;v.t=t;}
 if(mode==='subclass'){class Derived extends Int32Array {get buffer(){throw Error('shadow buffer');}get length(){throw Error('shadow length');}}v.c=new Derived(v.c);}
 if(mode==='hostile-own-shape')for(const name of ['h','x','p','f','c','t'])for(const prop of ['buffer','byteOffset','byteLength','length'])Object.defineProperty(v[name],prop,{get(){throw Error('shadow shape '+prop);}});
 const c=v.c,t=v.t;
 if(mode==='buffers')for(const name of ['h','x','p','f','c','t'])v[name]=get.buffer.call(v[name]);
 check(call(createSegmentedTextKernel(),v)===p.status,'Valid normalized type/shape '+mode);check(bytes(c).equals(Buffer.from(Int32Array.from(p.afterC).buffer))&&bytes(t).equals(Buffer.from(Uint32Array.from(p.afterT).buffer)),'Normalized views retain exact actual storage');
}
for(const kind of ['schema','fresh','mode','lateGlyph','lateClip','wrongWork','shortT','alias','hiddenAlias','fakeLength','wrongElementKind','badEpoch','sourceAlias']){
 const v=input(corpus.packets[0]);
 if(kind==='schema')v.h[0]=9;if(kind==='fresh')v.h[10]=0;if(kind==='mode')v.h[9]=2;
 if(kind==='lateGlyph')v.p[v.p.length-2]=0x7fffffff;
 if(kind==='lateClip')v.x[v.x.length-1]=0x7fffffff;
 if(kind==='wrongWork')v.h[6]++;
 if(kind==='shortT')v.t=v.t.subarray(1);
 if(kind==='alias'||kind==='hiddenAlias'){v.t=new Float32Array(v.c.buffer);if(kind==='hiddenAlias')Object.defineProperty(v.t,'buffer',{value:new ArrayBuffer(v.t.byteLength)});}
 if(kind==='fakeLength'){const c=new Int32Array(v.c.length+1);c.set(v.c);Object.defineProperty(c,'length',{value:v.c.length});v.c=c;}
 if(kind==='wrongElementKind'){const c=new Float32Array(v.c.length);Object.setPrototypeOf(c,Int32Array.prototype);v.c=c;}
 if(kind==='badEpoch')v.g=Number.MAX_SAFE_INTEGER+1;
 if(kind==='sourceAlias'){v.f=new Int8Array(get.buffer.call(v.c));v.h[5]=get.length.call(v.f);}
 unchanged(createSegmentedTextKernel(),v);
}
// Immediate duplicate is rejected without a second AA application. The Java
// producer owns full lifecycle; this is not an unbounded per-owner replay log.
{const k=createSegmentedTextKernel(),v=input(corpus.packets[0]);check(call(k,v)>0,'first monotone segment');unchanged(k,v);}
{
 const p=corpus.packets.find(p=>p.status===1),k=createSegmentedTextKernel(),a=input(p);a.c.fill(0);a.t.fill(1);a.g=5;a.s=5;
 check(call(k,a)===1,'first active owner epoch');
 const b=input(p);b.h[7]=a.h[7]+100;b.h[8]=1;b.g=1;b.s=1;b.c.fill(0);b.t.fill(1);check(call(k,b)===1,'replacement owner may start lower local epochs');
 a.h[8]++;a.g++;a.s++;a.c.fill(0);a.t.fill(1);check(call(k,a)===1,'returning owner with its next monotone segment/new base accepted');
}
{
 const p=corpus.packets.find(p=>p.status===1);check(!!p,'Export includes legitimate zero-sample batch');const v=input(p),k=createSegmentedTextKernel({allowFault:true});
 const before=Buffer.from(bytes(v.c));k.armPoisonOnce();check(call(k,v)===-1&&!bytes(v.c).equals(before),'Actual module post-write fault is fatal');v.h[8]++;unchanged(k,v,-1);
 v.g++;v.s++;v.h[8]++;v.c.fill(0);v.t.fill(1);check(call(k,v)===1,'Successful new cleared epoch recovers native poison');check(v.c.every(x=>x===0)&&v.t.every(x=>x===1),'Recovered zero-sample batch preserves fresh base');
 assert.throws(()=>createSegmentedTextKernel().armPoisonOnce());checks++;
}
for(const packet of corpus.packets){const v=input(packet),k=createSegmentedTextKernel();call(k,v);const seen=new Set(),forbidden=new Set([v.h,v.x,v.p,v.f,v.c,v.t]);function visit(x){if(!x||typeof x!=='object'||seen.has(x))return;check(!forbidden.has(x),'Kernel stats retained borrowed input');seen.add(x);for(const value of Object.values(x))visit(value);}visit(k.stats);}
console.log(`PASS independent actual segmented native Java-reference C/raw-T: ${checks} checks, ${totalWords} words; intrinsic hostile shapes, transactional rejection and poison recovery; no JNI/GPU`);
