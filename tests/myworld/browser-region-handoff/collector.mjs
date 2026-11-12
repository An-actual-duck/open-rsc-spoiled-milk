import assert from 'node:assert/strict';
import {createRegionHandoffTraceCollector} from '../../../tools/browser-gameplay/web/browser-region-handoff-profile.js';
let checks=0;const check=(v)=>{checks++;assert(v);};
const names='unmapped|context.header-decode|client-region.total';
const gpu={stats:{worldReadiness:{epoch:3,phase:'ready'},javaWorldFrames:123,heldWorldFrames:9,lastFrame:88,renderWorker:{lastSubmittedPacket:12,lastCompletedPacket:11,lastCompletedFrame:4}}};
let stopped=false;const c=createRegionHandoffTraceCollector({enabled:true,gpu:()=>gpu,clock:()=>100,stopped:()=>stopped});
function packet(last,count=Math.min(last,512)){
 const a=[1,1,last,last-count,count,12,10000,17];
 for(let n=last-count+1;n<=last;n++)a.push(n,17,3,2,n,-1,7,0,0,0,0,0);
 return a;
}
const raw=packet(2);c.report(null,names,raw);check(c.stats.events.length===2&&c.stats.lastOrdinal===2);raw[15]=999;
check(c.stats.events[0].payload[0]===0&&c.stats.events[0].phase==='client-region.total');
check(c.stats.observations[0].snapshotJavaMs===10000&&c.stats.observations[0].receivedBrowserMs===100&&c.stats.observations[0].gpu.sourceEpoch===3);
c.report(null,names,packet(2));check(c.stats.events.length===2);
let before=JSON.stringify(c.stats);for(const mutate of [a=>a[0]=2,a=>a[1]=0,a=>a[2]=1,a=>a[3]=1,a=>a[4]=513,a=>a[5]=11,a=>a[6]=NaN,a=>a[8]=0,a=>a[9]=-1,a=>a[10]=10,a=>a[11]=99,a=>a[12]=-1,a=>a[13]=-.5,a=>a[14]=-1,a=>a[15]=Infinity]){
 const bad=packet(3);mutate(bad);assert.throws(()=>c.report(null,names,bad));checks++;check(JSON.stringify(c.stats)===before);
}
assert.throws(()=>c.report(null,'changed',packet(3)));checks++;check(JSON.stringify(c.stats)===before);
for(let last=514;last<=4098;last+=512)c.report(null,names,packet(last));
check(c.stats.events.length===2048&&c.stats.lastOrdinal===4098&&c.stats.missingEvents===0);
const gap=packet(5000,1);c.report(null,names,gap);check(c.stats.missingEvents===901);
for(let n=0;n<140;n++)c.report(null,names,gap);check(c.stats.observations.length===128);
const arrayLike=Object.assign({length:gap.length,[Symbol.iterator](){throw new Error('borrowed Java array must be indexed');}},Object.fromEntries(gap.map((x,i)=>[i,x])));c.report(null,names,arrayLike);checks++;
before=JSON.stringify(c.stats);stopped=true;c.report(null,'invalid',null);check(JSON.stringify(c.stats)===before);
assert.throws(()=>createRegionHandoffTraceCollector().report(null,names,packet(1)));checks++;
console.log(JSON.stringify({passed:true,checks,scope:'Numeric boundary observer/schema/ownership/bounds only; no browser/GPU/timing proof'}));
