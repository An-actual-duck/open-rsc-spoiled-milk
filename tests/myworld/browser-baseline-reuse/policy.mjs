import assert from 'node:assert/strict';
import {baselineReusePolicy} from '../../../tools/browser-gameplay/web/browser-baseline-reuse-policy.js';
let checks=0;
const full='benchmark=fixture&wholezone=on&wholeram=on&staticscene=on&baselinereuse=on';
for(const mode of ['software','webgl'])for(const value of ['', 'off','on','bad']) {
 const q=new URLSearchParams(full);if(value)q.set('baselinereuse',value);else q.delete('baselinereuse');
 if(value==='bad'||value==='on'&&mode!=='webgl')assert.throws(()=>baselineReusePolicy(mode,q));
 else assert.deepEqual(baselineReusePolicy(mode,q),{enabled:value==='on',properties:value==='on'?['spoiledmilk.browserBaselineReuse=true']:[]});checks++;
}
for(const name of ['benchmark','wholezone','wholeram','staticscene']){
 const q=new URLSearchParams(full);q.delete(name);assert.throws(()=>baselineReusePolicy('webgl',q));checks++;
}
console.log(`PASS ${checks} actual startup policy admission checks`);
