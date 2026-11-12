/** Exercise actual bounded resource reader with independent stream producers. */
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const args=process.argv.slice(2),option=n=>{const at=args.indexOf(n);if(at<0||!args[at+1])throw Error('Required '+n);return path.resolve(args[at+1]);};
const root=option('--source-root'),out=option('--out'),web=path.join(root,'tools/browser-gameplay/web');
const source=fs.readFileSync(path.join(web,'browser-whole-zone.js'),'utf8'),policy=fs.readFileSync(path.join(web,'browser-whole-zone-policy.js'),'utf8');
const data=v=>'data:text/javascript;base64,'+Buffer.from(v).toString('base64');
const {fetchWholeZoneBytes}=await import(data(source.replace("from './browser-whole-zone-policy.js'",`from '${data(policy)}'`)));
let checks=0;
function stream(parts,{advertised=null,failAt=-1,ok=true,status=200}={}){
 let at=0;const observed={cancelled:0,released:0,reads:0,fetches:0};
 return {observed,fetcher:async()=>{observed.fetches++;return {ok,status,headers:{get:()=>advertised},body:{getReader:()=>({read:async()=>{observed.reads++;if(at===failAt)throw Error('producer failure');return at<parts.length?{done:false,value:parts[at++]}:{done:true};},cancel:async()=>{observed.cancelled++;},releaseLock:()=>{observed.released++;}})}};}};
}
const bytes=Uint8Array.from({length:257},(_,n)=>(n*71+3)&255);
for(const boundary of [0,1,15,16,128,256,257]){
 const s=stream([bytes.slice(0,boundary),bytes.slice(boundary)],{advertised:'257'});
 const actual=await fetchWholeZoneBytes('https://fixture.invalid/chunk',257,{fetcher:s.fetcher});
 assert.deepEqual(new Uint8Array(actual),bytes);assert.equal(s.observed.released,1);assert.equal(s.observed.cancelled,0);checks+=3;
}
for(const [parts,options,error]of [
 [[bytes.slice(0,256)],{},/Truncated/],
 [[bytes,Uint8Array.of(1)],{},/exceeds/],
 [[new Int32Array(257)],{},/exceeds/],
 [[bytes],{failAt:0},/producer failure/]
]){const s=stream(parts,options);await assert.rejects(fetchWholeZoneBytes('https://fixture.invalid/chunk',257,{fetcher:s.fetcher}),error);assert.equal(s.observed.cancelled,1);assert.equal(s.observed.released,1);checks+=3;}
for(const options of [{advertised:'258'},{advertised:'NaN'},{ok:false,status:503}]){const s=stream([bytes],options);await assert.rejects(fetchWholeZoneBytes('https://fixture.invalid/chunk',257,{fetcher:s.fetcher}),/length mismatch|HTTP/);assert.equal(s.observed.reads,0);checks+=2;}
for(const expected of [0,-1,1.5,1025,NaN]){const s=stream([bytes]);await assert.rejects(fetchWholeZoneBytes('https://fixture.invalid/chunk',expected,{maximum:1024,fetcher:s.fetcher}),/bounds/);assert.equal(s.observed.fetches,0);checks+=2;}
// Abort after response headers/first payload, while read() is pending. Fetch transport
// owns abort delivery; the reader must unwind, cancel, and release its lock.
{
 const controller=new AbortController();let cancel=0,release=0,reads=0,signalSeen=false;
 const fetcher=async(_,options)=>{signalSeen=options.signal===controller.signal;return {ok:true,headers:{get:()=>null},body:{getReader:()=>({read:async()=>{if(reads++===0)return{done:false,value:bytes.slice(0,10)};return new Promise((_,reject)=>options.signal.addEventListener('abort',()=>reject(options.signal.reason),{once:true}));},cancel:async()=>{cancel++;},releaseLock:()=>{release++;}})}};};
 const pending=fetchWholeZoneBytes('https://fixture.invalid/chunk',257,{fetcher,signal:controller.signal});
 while(reads<2)await Promise.resolve();controller.abort(new Error('midfetch cancellation'));
 await assert.rejects(pending,/midfetch cancellation/);assert(signalSeen);assert.equal(cancel,1);assert.equal(release,1);checks+=4;
}
const result={passed:true,checks,sourceRoot:root,sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),scope:'Actual resource byte-reader boundary, malformed response, prefetch cap and midfetch abort cleanup. Synthetic transport only; scheduler cancellation, network stack, GPU and timing remain separate.'};
fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
