import assert from 'node:assert/strict';
import {cloneTemplatePolicy} from '../../../tools/browser-gameplay/web/browser-clone-template-policy.js';
let checks=0;const full='benchmark=1&wholezone=on&wholeram=on&staticscene=on&clonetemplates=on';
for(const mode of ['webgl','software'])for(const option of ['', 'on','off','bad']){const q=new URLSearchParams(full);if(option)q.set('clonetemplates',option);else q.delete('clonetemplates');if(option==='bad'||option==='on'&&mode!=='webgl')assert.throws(()=>cloneTemplatePolicy(mode,q));else assert.deepEqual(cloneTemplatePolicy(mode,q),{enabled:option==='on',properties:option==='on'?['spoiledmilk.browserCloneTemplates=true']:[]});checks++;}
for(const missing of ['benchmark','wholezone','wholeram','staticscene']){const q=new URLSearchParams(full);q.delete(missing);assert.throws(()=>cloneTemplatePolicy('webgl',q));checks++;}
console.log('PASS',checks,'actual startup clone template policy checks');
