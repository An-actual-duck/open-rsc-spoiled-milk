import assert from 'node:assert/strict';
import {browserVertexOnlyPolicy as vertices,browserFinalModelProfilePolicy as profile} from '../../tools/browser-gameplay/web/browser-benchmark-controls.js';
for(const [policy,key,property]of [[vertices,'vertexonly','browserVertexOnlyUpdates'],[profile,'finalprofile','browserFinalModelProfile']]){
 for(const mode of ['software','input-probe','webgl'])for(const q of ['',key+'=on',key+'=invalid'])assert.deepEqual(policy(mode,new URLSearchParams(q)),{properties:[],enabled:false});
 assert.deepEqual(policy('software',new URLSearchParams('benchmark&'+key+'=on')),{properties:[],enabled:false});
 for(const mode of ['on','off'])assert.deepEqual(policy('webgl',new URLSearchParams('benchmark&'+key+'='+mode)),{properties:['spoiledmilk.'+property+'='+(mode==='on')],enabled:mode==='on'});
 assert.equal(policy('webgl',new URLSearchParams('benchmark')).enabled,false);
 assert.throws(()=>policy('webgl',new URLSearchParams('benchmark&'+key+'=invalid')));
}
console.log('PASS independent private controls, default OFF, ordinary/software isolation and invalid-input rejection');
