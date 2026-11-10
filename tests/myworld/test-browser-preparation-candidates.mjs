import assert from 'node:assert/strict';
import {privatePreparationCandidates} from '../../tools/browser-gameplay/web/browser-preparation-candidates.js';
import {browserGeometryTransferPolicy} from '../../tools/browser-gameplay/web/browser-geometry-transfer-policy.js';
for(const factory of [privatePreparationCandidates,browserGeometryTransferPolicy])for(const mode of ['software','webgl'])assert.deepEqual(factory(mode,new URLSearchParams('regionprep=on&descriptorwrite=on&geometrytransfer=on')).properties,[]);
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&regionprep=on&descriptorwrite=off')).requested,{region:true,descriptor:false});
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1')).properties,['spoiledmilk.browserRegionPreparation=false','spoiledmilk.browserResidentDescriptorWriting=false']);
assert.deepEqual(browserGeometryTransferPolicy('webgl',new URLSearchParams('benchmark=1&geometrytransfer=on')).properties,['spoiledmilk.browserGeometryTransferReuse=true']);
for(const query of ['benchmark=1&regionprep=bad','benchmark=1&descriptorwrite=bad'])assert.throws(()=>privatePreparationCandidates('webgl',new URLSearchParams(query)),/Invalid private/);
console.log('PASS private startup candidate controls leave ordinary/software untouched and reject malformed policies');
