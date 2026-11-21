import assert from 'node:assert/strict';
import {createBoundedResidency} from '../../../tools/browser-gameplay/web/browser-bounded-residency.js';
import {createLiveCellDraws} from '../../../tools/browser-gameplay/web/browser-live-cell-draws.js';
import {createWholeZoneSelection} from '../../../tools/browser-gameplay/web/browser-whole-zone.js';
import {createBoundedInputGate} from '../../../tools/browser-gameplay/web/browser-bounded-input.js';
import {wholeZonePolicy} from '../../../tools/browser-gameplay/web/browser-whole-zone-policy.js';

const tile = (id, cellX, role = 0) => ({id, cellX, cellY: 0, plane: 0, role,
  sourceKind: role ? 'objects' : 'world', originX: 0, originZ: 0, visibilityMask: 127,
  vertexCount: 3, indexCount: 3, batchIntCount: 5, bytes: 384});
const cells = [tile(1000000, 0), tile(1000001, 1), tile(1000002, 2), tile(1000003, 3)];
const catalog = {chunks: cells, objectCells: [], settings: [0, 0, 0, 0]};
const resident = new Map([[cells[0].id, {}], [cells[1].id, {}]]);
const frames = [], requests = [], errors = [], stats = {};
const residency = createBoundedResidency({catalog, stats, retainRelevantJobs: true, resident,
  totalBytes: () => 348 * resident.size, capacityBytes: 2048, optionalReserveBytes: 0,
  evict: id => resident.delete(id), clock: () => 0, schedule: fn => frames.push(fn),
  onError: error => errors.push(String(error)),
  prepare: (cell, signal) => new Promise(resolve => {
    requests.push({id: cell.id, signal, resolve});
    signal.addEventListener('abort', resolve, {once: true});
  }),
  createUpload: cell => ({lastStepBytes: 112, step() { resident.set(cell.id, {}); return true; }, dispose() {}})});
const selection = createWholeZoneSelection(catalog, {}, {liveCells: true});
const live = createLiveCellDraws(catalog, resident, stats), none = new Int32Array();
const local = new Int32Array([15, 0, 0, 0, 0, 127, 0, 1]);

function frame(center, scene, travel, drawCenter = center) {
  const scope = new Int32Array([1, 1, 0, 0, 0, center, 0, 2, 127, 127, 127,
    ...catalog.settings, scene, 0, 0, travel, drawCenter, 0]);
  selection.frame(scope, none);
  const draws = selection.merge(local, 0);
  const {required, optional} = live.plan(draws, center, 0, []);
  const ready = residency.select(required, optional, selection.generationKey);
  return ready ? [...live.published(draws)].filter((_, index) => index % 8 === 0) : null;
}

const settle = () => new Promise(resolve => setImmediate(resolve));
assert.deepEqual(frame(0, 1, 1), [cells[0].id, cells[1].id, 15]);
frames.shift()();
assert.equal(requests[0].id, cells[2].id);
assert.deepEqual(frame(0, 2, 1, 1), [cells[0].id, cells[1].id, 15]);
assert.equal(stats.framesWithPendingOuterDraws, 2);
assert.equal(requests[0].signal.aborted, false, 'scene revision should preserve shared optional work');
assert.equal(frame(1, 3, 1), null, 'interactive coverage still requires the missing ground');
assert.equal(requests[0].signal.aborted, false, 'an optional upload becomes required without restarting');
assert.deepEqual(frame(0, 4, 1), [cells[0].id, cells[1].id, 15]);
assert.equal(requests[0].signal.aborted, false);
requests[0].resolve();
await settle();
assert.deepEqual(frame(1, 5, 1), [cells[0].id, cells[1].id, cells[2].id, 15]);
while (frames.length && requests.length < 2) frames.shift()();
assert.equal(requests[1].id, cells[3].id);
assert.deepEqual(frame(0, 6, 1), [cells[0].id, cells[1].id, cells[2].id, 15]);
assert.equal(requests[1].signal.aborted, true, 'reversal cancels a cell outside the desired area');
await settle();
assert.equal(resident.has(cells[3].id), false);
frame(1, 7, 1);
while (frames.length && requests.length < 3) frames.shift()();
assert.equal(requests[2].id, cells[3].id);
frame(1, 8, 2);
assert.equal(requests[2].signal.aborted, true, 'relocation invalidates unfinished work');
await settle();
assert.deepEqual(errors, []);
residency.dispose();

const threeCells = cells.slice(0, 3);
const allocated = new Map(threeCells.slice(0, 2).map(cell => [cell.id, {}]));
const allocationFrames = [];
let pendingBytes = 0;
const allocatedResidency = createBoundedResidency({catalog: {chunks: threeCells}, resident: allocated,
  totalBytes: () => allocated.size * 348 + pendingBytes, totalCount: () => allocated.size + Number(pendingBytes > 0),
  capacityBytes: 1044, maxChunks: 3, optionalReserveBytes: 0, maxTickSteps: 1, retainRelevantJobs: true,
  clock: () => 0, schedule: fn => allocationFrames.push(fn), prepare: () => new Uint8Array(),
  evict: () => assert.fail('Protected coverage should not be evicted'),
  createUpload: cell => ({lastStepBytes: 0, get allocatedBytes() { return pendingBytes; },
    step() {
      if (!pendingBytes) { pendingBytes = 348; return false; }
      pendingBytes = 0;
      allocated.set(cell.id, {});
      return true;
    },
    dispose() { assert.fail('Allocated overlap should be retained'); }})});
const all = threeCells.map(cell => cell.id);
assert.equal(allocatedResidency.select(all.slice(0, 2), [all[2]], 'travel'), true);
allocationFrames.shift()();
await settle();
allocationFrames.shift()();
allocationFrames.shift()();
assert.equal(pendingBytes, 348);
assert.equal(allocatedResidency.select(all, [], 'travel'), false, 'allocated cell can be promoted at capacity');
allocationFrames.shift()();
assert.equal(allocatedResidency.ready, true);
assert.equal(allocated.size, 3);
allocatedResidency.dispose();

const networkStats = {}, networkFrames = [];
const networkResident = new Map([[all[0], {}], [all[1], {}]]);
let attempts = 0;
const networkResidency = createBoundedResidency({catalog: {chunks: threeCells}, stats: networkStats,
  resident: networkResident, totalBytes: () => networkResident.size * 348, optionalReserveBytes: 0,
  retainRelevantJobs: true, clock: () => 0, schedule: fn => networkFrames.push(fn),
  evict: id => networkResident.delete(id), onError: error => assert.fail(String(error)),
  async prepare() { if (++attempts === 1) throw new Error('Missing outer bytes'); return new Uint8Array(); },
  createUpload: cell => ({lastStepBytes: 112, step() { networkResident.set(cell.id, {}); return true; }, dispose() {}})});
assert.equal(networkResidency.select(all.slice(0, 2), [all[2]], 'travel'), true);
networkFrames.shift()();
await settle();
assert.equal(networkStats.optionalPrepareFailures, 1);
assert.equal(networkResidency.ready, true);
assert.equal(networkResident.has(all[2]), false);
assert.equal(networkResidency.select(all, [], 'travel'), false);
networkFrames.shift()();
await settle();
assert.equal(attempts, 2);
assert.equal(networkResidency.ready, true, 'required coverage can retry a previously missing outer cell');
networkResidency.dispose();

const requireGround = createLiveCellDraws({chunks: [tile(1000005, 2, 1)]}, resident, {});
assert.deepEqual(requireGround.plan(new Int32Array([1000005, 0, 0, 0, 0, 127, 0, 1]), 0, 0, []).required,
  [1000005], 'interactive scenery shares its terrain sector protection');
const gate = createBoundedInputGate({liveCells: true});
const metadata = new Int32Array(21);
assert.equal(gate.declare(metadata, none, 6), true);
assert.equal(gate.acknowledge({frameId: 6}, {coverageReady: true, worldReady: true}), false);
assert.equal(gate.acknowledge({frameId: 7}, {coverageReady: true, worldReady: true}), true);
metadata[19]++;
const fartherScenery = new Int32Array([0, 4, 0, 88, 77, 1]);
assert.equal(gate.declare(metadata, fartherScenery, 7), false, 'outer cells cannot block clicks on installed ground');
const nearbyScenery = new Int32Array([0, 2, 0, 88, 77, 1]);
assert.equal(gate.declare(metadata, nearbyScenery, 7), true, 'interactive scenery still needs a matching ACK');
metadata[18]++;
assert.equal(gate.declare(metadata, none, 7), true);
assert.throws(() => wholeZonePolicy('webgl', new URLSearchParams('livecells=on')), /bounded/);
assert.equal(wholeZonePolicy('webgl', new URLSearchParams(
  'benchmark&wholezone=on&renderworker=on&overlay=coverage&gpuresidency=bounded&livecells=on')).liveCells, true);
console.log('PASS live outer draw publication, required coverage, stable travel, reversal, relocation, and input ACKs');
