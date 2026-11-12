/** Real immutable complete source-bound stage; CPU/filesystem verification only. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {pathToFileURL,fileURLToPath} from 'node:url';
import {loadWholeZone,createWholeZoneSelection} from '../../tools/browser-gameplay/web/browser-whole-zone.js';
import {WHOLE_ZONE_SOURCE} from '../../tools/browser-gameplay/web/browser-whole-zone-policy.js';
import {createWorkerStatsSnapshot,WORKER_SAMPLE_FIELDS} from '../../tools/browser-gameplay/web/browser-render-worker-stats.js';
const directory=process.argv[2];if(!directory)throw new Error('Explicit immutable staged web directory required');
const base=pathToFileURL(fs.realpathSync(directory)+'/'),config=(await import(new URL('browser-whole-zone-config.js',base))).default;
const raw=JSON.parse(fs.readFileSync(new URL('whole-zone/manifest.json',base))),stats={},requested=[];let textures=0;
const catalog=await loadWholeZone({...config,baseURL:new URL('whole-zone/',base),mapIdentity:WHOLE_ZONE_SOURCE.manifestSha256,
  settings:new Int32Array(raw.settings),materials:new Int32Array(raw.materialMetadata),stats,bounded:true,
  fetcher:async url=>{requested.push(url.pathname);const bytes=fs.readFileSync(fileURLToPath(url));return new Response(bytes,{headers:{'content-length':String(bytes.byteLength)}});},
  uploadTexture:async()=>{textures++;},uploadChunk:()=>assert.fail('Metadata admission uploaded full geometry'),finishGpu:()=>assert.fail('Metadata admission claimed full GPU fence')});
assert.equal(catalog.chunks.length,1400);assert.equal(textures,raw.textures.length);assert.equal(stats.pinnedChunks,0);assert.equal(stats.ready,false);assert.equal(stats.startupGpuCompleted,false);assert.equal(stats.catalogAdmitted,true);
assert.equal(stats.manifestSha256,config.manifestSha256);assert(!requested.some(p=>raw.chunks.some(c=>p.endsWith('/'+c.path))));
const selection=createWholeZoneSelection(catalog,stats),rows=catalog.objectCells.filter(c=>c.cellX>=0&&c.cellX<10&&c.cellY>=22&&c.cellY<32).flatMap(c=>[c.plane,c.cellX,c.cellY,parseInt(c.canonicalContentKey.slice(8),16)|0,parseInt(c.canonicalContentKey.slice(0,8),16)|0,0]);
selection.frame(Int32Array.from([1,1,0,0,0,2,13,2,127,127,127,...catalog.settings,1,0,0]),new Int32Array(rows));const draws=selection.merge(new Int32Array(),0);
assert.equal(draws.length/8,120);assert.equal(stats.ready,false,'Selection granted GPU readiness without allocations');
const worker={wholeZone:stats,...Object.fromEntries(WORKER_SAMPLE_FIELDS.map(k=>[k,[]]))};createWorkerStatsSnapshot(worker)();
console.log(JSON.stringify({passed:true,catalogChunks:catalog.chunks.length,uploadedGeometry:0,texturesValidated:textures,currentRequired:draws.length/8,wholeZoneStatFields:Object.keys(stats).length,catalogSha256:config.manifestSha256,scope:'CPU source/catalogue/asset/material validation and exact selection metadata only; no actual GPU residency/performance proof'}));
