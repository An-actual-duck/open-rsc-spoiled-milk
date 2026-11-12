import assert from 'node:assert/strict';
import {webcrypto,createHash} from 'node:crypto';
import {createStartupProgress,createStartupReceiver,startupPresentation} from '../../tools/browser-gameplay/web/browser-startup-progress.js';
import {verifyPublicArchives} from '../../tools/browser-gameplay/web/browser-public-alpha.js';
let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;};let clock=0;const sent=[];
const child=createStartupProgress({now:()=>clock,emit:p=>sent.push(p)}),rendered=[];
const parent=createStartupReceiver({now:()=>clock,render:p=>rendered.push(p)});
eq(child.update('opening'),true);eq(parent.accept(sent.at(-1)),true);eq(rendered.at(-1).value,null);
clock=200;eq(child.update('data',{bytes:50,totalBytes:100,totalFiles:3}),true);eq(parent.accept(sent.at(-1)),true);
clock=250;eq(child.update('data',{writtenBytes:25,workMillis:42}),true);eq(sent.at(-1).bytes,50);eq(parent.accept(sent.at(-1)),true);assert.match(rendered.at(-1).detail,/loaded.*prepared/);checks++;
for(const changes of [{bytes:49},{writtenBytes:24},{totalBytes:101},{files:4},{bytes:101},{bytes:NaN},{workMillis:Infinity}])eq(child.update('data',changes),false);
eq(child.update('assets'),false);eq(parent.accept(sent.at(-1)),false,'duplicate rejected');
for(const value of [null,{}, {...sent.at(-1),sequence:4,elapsedMillis:-1},{...sent.at(-1),sequence:4,writtenBytes:101},{...sent.at(-1),sequence:4,stage:'untrusted label'}])eq(parent.accept(value),false);
for(let i=0;i<200;i++){clock++;assert(child.update('data',{bytes:50,writtenBytes:25}));parent.accept(sent.at(-1));}checks+=200;
eq(child.stats.records.length,128);eq(parent.stats.records.length,128);eq(parent.stats.latest.receivedMillis,450);
eq(parent.finish('streaming'),false);eq(child.finish('LOGIN'),true);eq(parent.finish('LOGIN'),true);eq(child.update('client'),false);eq(parent.accept({...sent.at(-1),sequence:999}),false);eq(parent.stats.elapsedMillis,450);
const closed=createStartupProgress();closed.close();eq(closed.update('opening'),false);eq(closed.finish('LOGIN'),false);
eq(startupPresentation({stage:'data',bytes:0,writtenBytes:50,totalBytes:100,totalFiles:3,files:1}).value,50);
const jar=new Uint8Array(3*1048576+17).fill(9),boot=new Uint8Array([1,2]);const sha=x=>createHash('sha256').update(x).digest('hex');
const deployment={publicAlpha:true,config:{assetPrefix:'/assets/test/',clientJarBytes:jar.length,clientJarSha256:sha(jar),bootstrapJarBytes:boot.length,bootstrapJarSha256:sha(boot)}};
const updates=[],fetches=[];let released=0,cancelled=0;
function response(raw,truncate=false,overflow=false){let offset=0;return {ok:true,headers:{get:()=>null},body:{getReader(){return {async read(){if(offset>=raw.length)return {done:true};let value=raw.subarray(offset,offset+524288);offset+=value.length;if(truncate&&offset>=raw.length)return {done:true};if(overflow&&offset>=raw.length)value=new Uint8Array(raw.length+1);return {done:false,value};},async cancel(){cancelled++;},releaseLock(){released++;}};}}};}
await verifyPublicArchives(deployment,{crypto:webcrypto,fetcher:async(url,opts)=>{fetches.push([url,opts]);return response(url.endsWith('client.jar')?jar:boot);},onProgress:p=>updates.push(p),now:()=>clock++});
eq(updates[0].bytes,0);eq(updates.at(-1),{bytes:jar.length+boot.length,totalBytes:jar.length+boot.length,files:2,totalFiles:2});eq(released,2);eq(cancelled,0);assert(updates.length<10);checks++;eq(fetches[0][1],{credentials:'omit',cache:'default',redirect:'error'});
for(const [truncate,overflow]of [[true,false],[false,true]]){await assert.rejects(()=>verifyPublicArchives(deployment,{crypto:webcrypto,fetcher:async()=>response(jar,truncate,overflow)}));checks++;}
eq(cancelled,1,'overrun cancels stream before digest');
const bad=new Uint8Array(jar);bad[0]=0;await assert.rejects(()=>verifyPublicArchives(deployment,{crypto:webcrypto,fetcher:async()=>response(bad)}));checks++;
await verifyPublicArchives(deployment,{crypto:webcrypto,fetcher:async url=>({ok:true,headers:{get:()=>null},arrayBuffer:async()=>(url.endsWith('client.jar')?jar:boot).buffer}),onProgress:()=>{throw Error('display failure');}});checks++;
console.log(`PASS ${checks} startup observer monotonic/bounded/closed, separate received/written work and streamed archive integrity/progress checks; no browser/network`);
