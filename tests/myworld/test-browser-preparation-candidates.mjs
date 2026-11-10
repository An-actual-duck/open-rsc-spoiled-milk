import assert from 'node:assert/strict';
import {privatePreparationCandidates} from '../../tools/browser-gameplay/web/browser-preparation-candidates.js';
import {browserGeometryTransferPolicy} from '../../tools/browser-gameplay/web/browser-geometry-transfer-policy.js';
for(const factory of [privatePreparationCandidates,browserGeometryTransferPolicy])for(const mode of ['software','webgl'])assert.deepEqual(factory(mode,new URLSearchParams('regionprep=on&descriptorwrite=on&geometrytransfer=on&halolookup=on&sceneryprojection=on')).properties,[]);
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&regionprep=on&descriptorwrite=off')).requested,{region:true,descriptor:false,halo:false,sceneryProjection:false});
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1')).properties,['spoiledmilk.browserRegionPreparation=false','spoiledmilk.browserResidentDescriptorWriting=false','spoiledmilk.browserHaloHeightLookup=false','spoiledmilk.browserSceneryProjectionWork=false']);
assert.deepEqual(browserGeometryTransferPolicy('webgl',new URLSearchParams('benchmark=1&geometrytransfer=on&halolookup=on&sceneryprojection=on')).properties,['spoiledmilk.browserGeometryTransferReuse=true']);
for(const query of ['benchmark=1&regionprep=bad','benchmark=1&descriptorwrite=bad','benchmark=1&halolookup=bad','benchmark=1&sceneryprojection=bad'])assert.throws(()=>privatePreparationCandidates('webgl',new URLSearchParams(query)),/Invalid private/);
console.log('PASS private startup candidate controls leave ordinary/software untouched and reject malformed policies');

assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&sceneryprojection=on')).requested,{region:false,descriptor:false,halo:false,sceneryProjection:true});
assert(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&sceneryprojection=on')).properties.includes('spoiledmilk.browserSceneryProjectionWork=true'));
