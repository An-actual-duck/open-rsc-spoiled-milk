// Production module under controlled fetch/crypto/str lifetimes. No browser or network.
import assert from 'node:assert/strict';
import {webcrypto,createHash}from'node:crypto';
import {createBulkStaging}from'../../tools/browser-gameplay/web/browser-bulk-staging.js';
let checks=0;const check=(a,b,message)=>{assert.deepEqual(a,b,message);checks++;};
const hash=b=>createHash('sha256').update(b).digest('hex');
const values=[new Uint8Array([1,2]),new Uint8Array([3,4,5]),new Uint8Array([6,7,8,9])];
const paths=['Cache/whole-ram/manifest.json','Cache/whole-ram/source.bin','Cache/whole-ram/prepared.bin'];
const config={manifestBytes:2,manifestSha256:hash(values[0]),sourceBundleBytes:3,sourceBundleSha256:hash(values[1]),preparedBytes:4,preparedSha256:hash(values[2])};
function response(data,{advertised=data.length,chunks=[data]}={}){let at=0;const receipt={cancel:0,unlock:0};return {ok:true,headers:{get:()=>String(advertised)},body:{getReader:()=>({async read(){return at<chunks.length?{done:false,value:chunks[at++]}:{done:true};},async cancel(){receipt.cancel++;},releaseLock(){receipt.unlock++;}})},receipt};}
function harness(extra={}){
 const files=new Map(),events=[],requests=[],progress=[];
 const instance=createBulkStaging({enabled:true,assetPrefix:'/assets/alpha-test1/',config,crypto:webcrypto,
  fetcher:async(url,options)=>{requests.push([url,options]);return response(values[paths.indexOf(new URL(url).pathname.split('/assets/alpha-test1/')[1])]);},
  addFile:(p,b)=>{files.set(p,b);events.push(['mount',p]);},removeFile:p=>{files.delete(p);events.push(['remove',p]);},onProgress:p=>progress.push(p),...extra});
 return {instance,files,events,requests,progress};
}
const h=harness();
for(let i=0;i<paths.length;i++){
 const prefix=await h.instance.prepare(paths[i]);check(prefix.startsWith('/str/'),true);check(h.files.get(prefix+paths[i]),values[i]);check(h.instance.stats.ownedBytes,values[i].length);check(h.instance.stats.files,i);
 await assert.rejects(()=>h.instance.prepare(paths[i]),/already owns/);checks++;
 assert.throws(()=>h.instance.release(paths[(i+1)%3],true),/does not own/);checks++;
 h.instance.release(paths[i],true);check(h.files.size,0);check(h.instance.stats.ownedBytes,0);
}
check(h.instance.stats.peakOwnedBytes,4,'only one file owned, not total9');check(h.instance.stats.receivedBytes,9);check(h.instance.stats.verifiedBytes,9);check(h.instance.stats.completedBytes,9);check(h.instance.stats.files,3);check(h.instance.stats.removals,3);
check(h.requests.every(([url,o])=>url.startsWith('https://play.rscspoiledmilk.com/assets/alpha-test1/Cache/whole-ram/')&&o.cache==='default'&&o.credentials==='omit'&&o.redirect==='error'),true);
check(h.progress.at(-1),{bytes:9,totalBytes:9});
for(const bad of ['../secret','Cache/uid.dat','https://evil/file','Cache/whole-ram/prepared.bin']){const x=harness();await assert.rejects(()=>x.instance.prepare(bad),/roster/);check(x.requests.length,0);}
for(const options of [{advertised:3},{advertised:2,chunks:[new Uint8Array([1])]}, {advertised:2,chunks:[new Uint8Array([1,2,3])]}, {advertised:2,chunks:[new Uint8Array([1,9])]}, {advertised:2,chunks:['bad']}]){
 const r=response(values[0],options),x=harness({fetcher:async()=>r});await assert.rejects(()=>x.instance.prepare(paths[0]));check(x.files.size,0);check(x.instance.stats.ownedBytes,0);check(x.instance.stats.failed,true);
}
for(const bad of [null,{...config,preparedBytes:256*1024*1024+1},{...config,preparedSha256:'bad'},{...config,sourceBundleBytes:NaN}]){assert.throws(()=>harness({config:bad}));checks++;}
let fetchResolve;const stopped=harness({fetcher:()=>new Promise(r=>fetchResolve=r)}),late=stopped.instance.prepare(paths[0]);stopped.instance.dispose();fetchResolve(response(values[0]));await assert.rejects(()=>late,/cancelled/);check(stopped.events.length,0);check(stopped.instance.stats.ownedBytes,0);
let hashResolve;const hashing=harness({crypto:{subtle:{digest:()=>new Promise(r=>hashResolve=r)}}});const pendingHash=hashing.instance.prepare(paths[0]);while(!hashResolve)await new Promise(r=>setImmediate(r));check(hashing.instance.stats.ownedBytes,2);hashing.instance.dispose();hashResolve(await webcrypto.subtle.digest('SHA-256',values[0]));await assert.rejects(()=>pendingHash,/cancelled/);check(hashing.events.length,0);check(hashing.instance.stats.ownedBytes,0);
// Stop during an open Java lease must defer unlink until caller closes/releases.
const open=harness();const openRoot=await open.instance.prepare(paths[0]);open.instance.dispose();open.instance.dispose();check(open.files.has(openRoot+paths[0]),true);check(open.instance.stats.deferredCloseCleanup,true);check(open.instance.stats.ownedBytes,2);check(open.instance.cancelled(),true);await assert.rejects(()=>open.instance.prepare(paths[1]),/cancelled/);open.instance.release(paths[0],false);check(open.files.size,0);check(open.instance.stats.ownedBytes,0);check(open.instance.stats.files,0);check(open.instance.stats.removals,1);
// Old hash completion cannot mount/delete a newer factory's session path.
let oldResolve;const shared=new Map(),old=harness({crypto:{subtle:{digest:()=>new Promise(r=>oldResolve=r)}},addFile:(p,b)=>shared.set(p,b),removeFile:p=>shared.delete(p)});const oldWait=old.instance.prepare(paths[0]);while(!oldResolve)await new Promise(r=>setImmediate(r));old.instance.dispose();const fresh=harness({addFile:(p,b)=>shared.set(p,b),removeFile:p=>shared.delete(p)});const freshRoot=await fresh.instance.prepare(paths[0]);oldResolve(await webcrypto.subtle.digest('SHA-256',values[0]));await assert.rejects(()=>oldWait);check(shared.has(freshRoot+paths[0]),true);fresh.instance.release(paths[0],true);check(shared.size,0);
let mountInstance;const mountFiles=new Map();const mounting=harness({addFile:(p,b)=>{mountFiles.set(p,b);mountInstance.dispose();},removeFile:p=>mountFiles.delete(p)});mountInstance=mounting.instance;await assert.rejects(()=>mounting.instance.prepare(paths[0]),/cancelled/);check(mountFiles.size,0);check(mounting.instance.stats.ownedBytes,0);
const observers=harness({onProgress(){throw Error('passive observer');}});await observers.instance.prepare(paths[0]);observers.instance.release(paths[0],true);check(observers.instance.stats.files,1);
// Removal failure stays fail-closed and retains unconfirmed bytes; primary failure wins.
let removeFails=true;const cleanup=harness({removeFile(){if(removeFails)throw Error('remove failed');}});await cleanup.instance.prepare(paths[0]);assert.throws(()=>cleanup.instance.release(paths[0],true),/remove failed/);check(cleanup.instance.stats.failed,true);check(cleanup.instance.stats.cleanupFailed,true);check(cleanup.instance.stats.ownedBytes,2);await assert.rejects(()=>cleanup.instance.prepare(paths[1]),/cancelled/);removeFails=false;cleanup.instance.release(paths[0],true);check(cleanup.instance.stats.ownedBytes,0);check(cleanup.instance.stats.files,0);
const primary=Error('primary mount failure');const doubleFailure=harness({addFile(){throw primary;},removeFile(){throw Error('secondary cleanup');}});await assert.rejects(()=>doubleFailure.instance.prepare(paths[0]),e=>e===primary);checks++;check(doubleFailure.instance.stats.failed,true);check(doubleFailure.instance.stats.ownedBytes,2);check(doubleFailure.instance.stats.cleanupFailed,true);doubleFailure.instance.dispose();check(doubleFailure.instance.stats.ownedBytes,2,'failed unlink never becomes a zero-byte claim');
let rejectRead;const readingReceipt={cancel:0,unlock:0};const reading=harness({fetcher:async(url,options)=>({ok:true,headers:{get:()=>2},body:{getReader:()=>({read:()=>new Promise((resolve,reject)=>{rejectRead=reject;options.signal.addEventListener('abort',()=>reject(Error('aborted')),{once:true});}),cancel:async()=>{readingReceipt.cancel++;},releaseLock(){readingReceipt.unlock++;}})}})});const waitRead=reading.instance.prepare(paths[0]);while(!rejectRead)await new Promise(r=>setImmediate(r));reading.instance.dispose();await assert.rejects(()=>waitRead);check(readingReceipt,{cancel:1,unlock:1});check(reading.instance.stats.ownedBytes,0);check(reading.events.length,0);
const many=new Uint8Array(8192),boundedProgress=[];const throttled=harness({now:()=>0,config:{...config,manifestBytes:many.length,manifestSha256:hash(many)},fetcher:async()=>response(many,{chunks:Array.from(many,()=>new Uint8Array([0]))}),onProgress:p=>boundedProgress.push(p)});await throttled.instance.prepare(paths[0]);check(boundedProgress.length,1,'small chunks do not emit thousands of diagnostic messages');throttled.instance.release(paths[0],true);
const disabled=createBulkStaging({enabled:false});check(disabled.stats.enabled,false);disabled.dispose();check(disabled.cancelled(),false);
console.log('PASS '+checks+' bulk byte/hash/roster/budget/stop/hash/mount/open-lease/restart/ownership checks; controlled APIs only');
