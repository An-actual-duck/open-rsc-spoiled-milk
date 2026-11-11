#!/usr/bin/env -S node --experimental-default-type=module
import assert from 'node:assert/strict';
import {browserLoadProfilePolicy,createBrowserLoadProfileCollector} from '../../tools/browser-gameplay/web/browser-region-load-profile.js';
let checks=0;const check=(v)=>{assert(v);checks++;};
check(browserLoadProfilePolicy('webgl',new URLSearchParams('benchmark=1&loadprofile=on')).requested);
check(browserLoadProfilePolicy('webgl',new URLSearchParams()).properties.length===0);
for(const q of ['loadprofile=on','benchmark=1&loadprofile=1']){assert.throws(()=>browserLoadProfilePolicy('webgl',new URLSearchParams(q)));checks++;}
const gpu={stats:{renderWorker:{submittedPackets:8,lastCompletedPacket:7,lastCompletedFrame:2}}};
const c=createBrowserLoadProfileCollector({enabled:true,gpu:()=>gpu,clock:()=>100});
const a=new Float64Array(36);a[0]=1;a[1]=1;a[2]=1;a[3]=2;a[4]=960;a[5]=540;a[7]=8;a[8]=3;a[33]=8;
c.report(null,a);a[8]=99;check(c.stats.last.ready===3&&c.stats.last.workerSubmittedPacket===8&&c.stats.last.workerCompletedPacket===7&&c.stats.totalReports===1);
for(let n=2;n<=140;n++){a[2]=n;a[8]=3;c.report(null,a);}check(c.stats.samples.length===128&&c.stats.last.attempt===140&&gpu.stats.loadProfileSamples===c.stats.samples);
assert.throws(()=>c.report(null,a));checks++;
for(const [index,value]of [[0,2],[1,-1],[7,300],[8,9],[13,NaN],[4,1e6]]){const bad=a.slice();bad[2]=141;bad[index]=value;const before=c.stats.totalReports;assert.throws(()=>c.report(null,bad));check(c.stats.totalReports===before);}
const raw=Object.fromEntries(Array.from(a,(v,n)=>[n,v]));raw.length=36;raw[2]=141;raw[Symbol.iterator]=()=>{throw new Error('must not iterate');};c.report(null,raw);check(c.stats.last.attempt===141);
console.log(JSON.stringify({passed:true,checks,scope:'bounded diagnostic policy/copy only; no timings or rendering claim'}));
