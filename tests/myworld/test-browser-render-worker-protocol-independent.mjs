// Independent behavioral oracle. Requires detached, exact-source ES modules.
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
const root=process.argv[2];
const P=await import(pathToFileURL(root+'/browser-render-worker-protocol.js'));
const {createRenderWorkerTransport}=await import(pathToFileURL(root+'/browser-render-worker-transport.js'));
const {createRenderWorkerReplay,createWorkerOutputSizer}=await import(pathToFileURL(root+'/browser-render-worker-replay.js'));
let checks=0;function ok(v,m){assert.ok(v,m);checks++;}function eq(a,b,m){assert.deepEqual(a,b,m);checks++;}
const tick=()=>new Promise(r=>setImmediate(r));
const layout=()=>({epoch:1,width:1843,height:1037,dpr:1});
function fake(options={}){const sent=[];const t=createRenderWorkerTransport({layout,postMessage:(p,x)=>{sent.push(structuredClone(p,{transfer:x}));},...options});return {t,sent};}
function ack(p,more={}){return {type:'ack',version:1,id:p.id,frameId:p.frameId,layoutEpoch:p.layout.epoch,result:4096,...more};}
function command(name,args){return P.ownRenderCommand(name,args).command;}
function batch(commands,more={}){return {version:1,type:'batch',id:1,frameId:0,layout:layout(),commands,bytes:commands.reduce((n,c)=>n+P.inspectRenderCommand(c.name,c.args).bytes,0),...more};}
function backend(){const calls=[];const natives={};for(const name of Object.keys(P.RENDER_WORKER_SCHEMA)){if(name[0]!=='$')natives[P.renderNativeName(name)]=async(_,...args)=>{calls.push([name,args]);return name==='initialize'?8192:undefined;};}return {calls,natives,dispose(){calls.push(['dispose']);}};}
// Raw-bit oracle uses byte equality, including deliberately noncanonical float NaNs.
{
 const storage=new ArrayBuffer(48),words=new Uint32Array(storage);words.set([0x12345678,0x7fc01234,0x80000000,0xffc0abcd,0x3f800000,0x89abcdef]);
 const source=new Float32Array(storage,4,4),wanted=Array.from(new Uint8Array(storage,4,16));
 Object.defineProperties(source,{buffer:{get(){throw Error('own buffer getter');}},byteOffset:{value:0},byteLength:{value:0},length:{value:999999}});
 const own=P.ownRenderCommand('lightingFrame',[source]);eq(Array.from(new Uint8Array(own.command.args[0].buffer)),wanted,'intrinsic offset/raw bits');
 words.fill(0);eq(Array.from(new Uint8Array(own.command.args[0].buffer)),wanted,'mutation after capture');
 const cloned=structuredClone(own.command,{transfer:own.transfers});eq(storage.byteLength,48,'borrowed buffer not detached');eq(own.command.args[0].byteLength,0,'only owned copy transferred');eq(Array.from(new Uint8Array(cloned.args[0].buffer)),wanted,'transfer preserves bits');
}
{
 const source=new Int32Array([0x12345678,-1,0,0x7fffffff]);const own=P.ownRenderCommand('deleteChunks',[source.subarray(1,3)]);source.fill(33);eq(Array.from(own.command.args[0]),[-1,0],'integer source offset');
 assert.throws(()=>P.ownRenderCommand('lightingFrame',[new Int32Array(4)]));checks++;
 assert.throws(()=>P.inspectRenderCommand('unknown',[]));checks++;
 assert.throws(()=>P.inspectRenderCommand('lightingFrame',[new Float32Array(new SharedArrayBuffer(16))]));checks++;
}
// Slot oracle holds worker completions. Successful postMessage is never presentation.
{
 const {t,sent}=fake();await t.enqueue('holdWorld',[]);await t.enqueue('holdWorld',[]);eq(sent.length,2);eq(t.stats.completedFrames,0);eq(t.stats.maxInFlight,2);
 let third=false;const waiting=t.enqueue('holdWorld',[]).then(()=>third=true);await tick();eq(sent.length,2);eq(third,false,'third packet blocked');
 t.receive(ack(sent[0]));await waiting;eq(sent.length,3);eq(t.stats.completedFrames,1,'only actual ACK counted');
 t.receive(ack(sent[1]));t.receive(ack(sent[2]));eq(t.stats.completedFrames,3);eq(t.stats.inFlightBytes,0);t.dispose();
}
// Explicit result barrier must not resolve from submission or a malformed identity.
{
 const {t,sent}=fake();let resolved=false;const result=t.enqueue('initialize',['#version 120 v','#version 120 f']).then(x=>{resolved=true;return x;});await tick();eq(resolved,false);t.receive(ack(sent[0],{result:8192}));eq(await result,8192);t.dispose();
}
// Resource order oracle, independent of any GPU implementation or resource cache.
{
 const b=backend(),s=createWorkerOutputSizer(),r=createRenderWorkerReplay({backend:b,outputSizer:s});
 const packet=batch([command('uploadTexture',[4,1,1,new Int32Array([123]),false]),command('deleteTextures',[new Int32Array([4])]),command('uploadTexture',[4,1,1,new Int32Array([456]),false]),command('holdWorld',[])],{frameId:1});
 const completion=await r.execute(structuredClone(packet));eq(b.calls.map(x=>x[0]),['uploadTexture','deleteTextures','uploadTexture','holdWorld']);eq(completion.frameId,1);eq(completion.layoutEpoch,1);
 const size=s.size(960,540);eq([size.width,size.height],[1843,1037],'physical sizing uses explicit DOM layout and legacy aspect rounding');r.dispose();
}
// Entire batch malformed at its END must produce zero backend prefix side effects.
{
 const b=backend(),r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});const packet=batch([command('deleteChunks',[new Int32Array([5])])]);packet.commands.push({name:'lightingFrame',args:[new Int32Array(4)]});
 await assert.rejects(r.execute(packet));checks++;eq(b.calls,[]);await assert.rejects(r.execute(batch([command('initializeSky',[])])));checks++;eq(b.calls,[]);r.dispose();
}
// Backend failure prevents a false ACK and permanently poisons subsequent packets.
{
 const b=backend();b.natives[P.renderNativeName('initializeSky')]=async()=>{throw Error('backend fault');};const r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});await assert.rejects(r.execute(batch([command('initializeSky',[])])));checks++;await assert.rejects(r.execute(batch([command('holdWorld',[])],{id:2,frameId:1})));checks++;r.dispose();
}
// Disposal wakes every unresolved result and blocked slot producer.
{
 const {t,sent}=fake();const a=t.enqueue('initialize',['v','f']);a.catch(()=>{});await tick();await t.enqueue('holdWorld',[]);const blocked=t.enqueue('holdWorld',[]);blocked.catch(()=>{});await tick();eq(sent.length,2);t.dispose();await assert.rejects(a);checks++;await assert.rejects(blocked);checks++;eq(t.stats.completedFrames,0);eq(t.stats.inFlightBytes,0);
}
console.log(JSON.stringify({passed:true,checks,cpuOnly:true,actualGPU:false,actualWorker:false,scope:'owned bytes/transfer, ordered resources, bounded ACK slots, result barriers, physical metadata, rejection and cleanup'}));
