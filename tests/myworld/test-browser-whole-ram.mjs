// CPU-only policy/receipt contract; files and metadata alone never establish RAM readiness.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const source=new URL('../../tools/browser-gameplay/web/browser-whole-ram.js',import.meta.url);
const {wholeRamPolicy,validateWholeRamConfig,decodeWholeRamWork}=await import('data:text/javascript;base64,'+fs.readFileSync(source).toString('base64'));
let checks=0;
function equal(a,b){assert.deepEqual(a,b);checks++;}function rejects(f){assert.throws(f);checks++;}
const good=new URLSearchParams('benchmark&wholezone=on&renderworker=on&overlay=coverage&overlaydelta=off&wholeram=on');
equal(wholeRamPolicy('software',new URLSearchParams()).properties,[]);equal(wholeRamPolicy('webgl',good).enabled,true);
for(const [key,value]of [['wholezone','off'],['renderworker','off'],['overlay','none'],['overlaydelta','on'],['wholeram','bad']]){const q=new URLSearchParams(good);q.set(key,value);rejects(()=>wholeRamPolicy('webgl',q));}
const q=new URLSearchParams(good);q.delete('benchmark');rejects(()=>wholeRamPolicy('webgl',q));rejects(()=>wholeRamPolicy('software',good));
const config={schemaVersion:1,manifestBytes:900000,manifestSha256:'a'.repeat(64),clientJarSha256:'b'.repeat(64),gpuCatalogSha256:'c'.repeat(64),sourceBundleBytes:46101734,sourceBundleSha256:'d'.repeat(64),preparedBytes:100000,preparedSha256:'e'.repeat(64),retainedPrimitiveBytes:60000000,rawFiles:1797,recipes:445,queryCells:1330,hiddenRoofs:false};
const gpu={clientJarSha256:config.clientJarSha256,manifestSha256:config.gpuCatalogSha256};equal(validateWholeRamConfig(config,gpu).expectedRuntimePrimitiveBytes,106101734);
for(const change of [{recipes:444},{queryCells:1329},{rawFiles:1796},{preparedBytes:1536*1024*1024+1},{sourceBundleBytes:46101718},{hiddenRoofs:0},{manifestSha256:'x'},{clientJarSha256:'f'.repeat(64)}])rejects(()=>validateWholeRamConfig({...config,...change},gpu));
equal(decodeWholeRamWork([],true).preparedReady,false);const zero=[1,...Array(17).fill(0)];equal(decodeWholeRamWork(zero,true).sourceReady,false);
const raw=[1,1,0,1797,46101718,0,0,46101734,0,0,0,0,0,0,0,1,5,0];equal(decodeWholeRamWork(raw,true).preparedReady,false);equal(decodeWholeRamWork(raw,true).sourceBundleBytes,46101734);
const ready=[1,1,1,1797,46101718,445,1330,106101734,0,3,3,80,-1,1,4,1,6000,30];let receipt=decodeWholeRamWork(ready,true);equal(receipt.preparedReady,true);equal(receipt.mutableBytesCopied,8589934591);ready[9]=999;equal(receipt.activations,3);
for(const [index,value]of [[0,0],[1,2],[2,2],[3,1796],[4,46101717],[5,444],[6,1329],[7,46101734],[8,-1],[11,NaN]]){const bad=[1,1,1,1797,46101718,445,1330,106101734,0,3,3,80,0,0,4,1,6000,30];bad[index]=value;rejects(()=>decodeWholeRamWork(bad,true));}
rejects(()=>decodeWholeRamWork(Array(19).fill(0),true));const hostile={length:18,[Symbol.iterator](){throw Error('must not enumerate');}};for(let i=0;i<18;i++)hostile[i]=zero[i];equal(decodeWholeRamWork(hostile,true).preparedReady,false);
console.log('PASS '+checks+' private RAM policy, bounded partial/full readiness, split-byte and malformed receipt checks; no runtime/GPU proof');
