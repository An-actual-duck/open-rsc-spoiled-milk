import assert from 'node:assert/strict';
import {privatePreparationCandidates} from '../../tools/browser-gameplay/web/browser-preparation-candidates.js';
import {browserGeometryTransferPolicy} from '../../tools/browser-gameplay/web/browser-geometry-transfer-policy.js';
for(const factory of [privatePreparationCandidates,browserGeometryTransferPolicy])for(const mode of ['software','webgl'])assert.deepEqual(factory(mode,new URLSearchParams('regionprep=on&descriptorwrite=on&geometrytransfer=on&halolookup=on&sceneryprojection=on&chunkcoverage=on')).properties,[]);
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&regionprep=on&descriptorwrite=off')).requested,{region:true,descriptor:false,halo:false,sceneryProjection:false,chunkCoverage:false,overlayDeltaTransport:false,terrainQueryIndex:false});
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1')).properties,['spoiledmilk.browserOverlayJournalObserve=false','spoiledmilk.browserRegionPreparation=false','spoiledmilk.browserResidentDescriptorWriting=false','spoiledmilk.browserHaloHeightLookup=false','spoiledmilk.browserSceneryProjectionWork=false','spoiledmilk.browserPrimitiveChunkCoverage=false','spoiledmilk.browserOverlayDeltaTransport=false','spoiledmilk.browserOverlayRectMode=off','spoiledmilk.browserTerrainQueryIndex=false']);
assert.deepEqual(browserGeometryTransferPolicy('webgl',new URLSearchParams('benchmark=1&geometrytransfer=on&halolookup=on&sceneryprojection=on')).properties,['spoiledmilk.browserGeometryTransferReuse=true']);
for(const query of ['benchmark=1&regionprep=bad','benchmark=1&descriptorwrite=bad','benchmark=1&halolookup=bad','benchmark=1&sceneryprojection=bad','benchmark=1&chunkcoverage=bad','benchmark=1&overlaydelta=bad'])assert.throws(()=>privatePreparationCandidates('webgl',new URLSearchParams(query)),/Invalid private/);
console.log('PASS private startup candidate controls leave ordinary/software untouched and reject malformed policies');

assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&sceneryprojection=on')).requested,{region:false,descriptor:false,halo:false,sceneryProjection:true,chunkCoverage:false,overlayDeltaTransport:false,terrainQueryIndex:false});
assert(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&sceneryprojection=on')).properties.includes('spoiledmilk.browserSceneryProjectionWork=true'));

assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&chunkcoverage=on')).requested,{region:false,descriptor:false,halo:false,sceneryProjection:false,chunkCoverage:true,overlayDeltaTransport:false,terrainQueryIndex:false});
assert(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&chunkcoverage=on')).properties.includes('spoiledmilk.browserPrimitiveChunkCoverage=true'));
assert.deepEqual(privatePreparationCandidates('software',new URLSearchParams('benchmark=1&chunkcoverage=on')).properties,[]);

assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&overlaydelta=on')).requested,{region:false,descriptor:false,halo:false,sceneryProjection:false,chunkCoverage:false,overlayDeltaTransport:true,terrainQueryIndex:false});
assert(privatePreparationCandidates('webgl',new URLSearchParams('benchmark=1&overlaydelta=on')).properties.includes('spoiledmilk.browserOverlayDeltaTransport=true'));
for(const mode of ['webgl','software'])assert.deepEqual(privatePreparationCandidates(mode,new URLSearchParams('overlaydelta=on')).properties,[]);
assert.deepEqual(privatePreparationCandidates('software',new URLSearchParams('benchmark=1&overlaydelta=on')).properties,[]);

const observed=privatePreparationCandidates('webgl',new URLSearchParams('benchmark&overlaydelta=on&overlayrect=observe'));
assert.equal(observed.rectMode,'observe');assert(observed.properties.includes('spoiledmilk.browserOverlayRectMode=observe'));
for(const query of ['benchmark&overlayrect=observe','benchmark&overlayrect=bad'])assert.throws(()=>privatePreparationCandidates('webgl',new URLSearchParams(query)),/overlayrect/);

assert.equal(privatePreparationCandidates('webgl',new URLSearchParams('benchmark&overlaydelta=on&overlayrect=on')).rectMode,'on');

const terrain=privatePreparationCandidates('webgl',new URLSearchParams('benchmark&terrainquery=on'));
assert.equal(terrain.requested.terrainQueryIndex,true);assert(terrain.properties.includes('spoiledmilk.browserTerrainQueryIndex=true'));
assert.throws(()=>privatePreparationCandidates('webgl',new URLSearchParams('benchmark&terrainquery=bad')),/Invalid private terrainquery/);
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('terrainquery=on')).properties,[]);
assert.deepEqual(privatePreparationCandidates('software',new URLSearchParams('benchmark&terrainquery=on')).properties,[]);

const journal=privatePreparationCandidates('webgl',new URLSearchParams('benchmark&overlaydelta=on&overlayjournal=observe'));
assert.equal(journal.journalObserve,true);assert(journal.properties.includes('spoiledmilk.browserOverlayJournalObserve=true'));
for(const query of ['benchmark&overlayjournal=observe','benchmark&overlaydelta=on&overlayjournal=bad'])assert.throws(()=>privatePreparationCandidates('webgl',new URLSearchParams(query)),/overlayjournal/);
assert.deepEqual(privatePreparationCandidates('software',new URLSearchParams('benchmark&overlaydelta=on&overlayjournal=observe')).properties,[]);
assert.deepEqual(privatePreparationCandidates('webgl',new URLSearchParams('overlaydelta=on&overlayjournal=observe')).properties,[]);
