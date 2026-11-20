import assert from 'node:assert/strict';
import {
  cameraWorldPosition, createOverworldFlyover, moveWorldCamera,
  persistentWorldPolicy, terrainCells, walkingReplayOffset, walkingResumeOffset
} from '../../tools/browser-gameplay/web/browser-persistent-world.js';
import {createWholeZoneSelection} from '../../tools/browser-gameplay/web/browser-whole-zone.js';

function matrices(x, z) {
  const result = new Float32Array(48);
  // An orthonormal 90 degree yaw, with world coordinate translation applied.
  result.set([0,0,1,0, 0,1,0,0, -1,0,0,0, z,0,-x,1],16);
  return result;
}
const scale = 128;
assert.deepEqual(cameraWorldPosition(matrices(12,-40),200,300), [212,260]);
assert.deepEqual(cameraWorldPosition(moveWorldCamera(matrices(12,-40),140,80),200,300), [352,340]);
assert.deepEqual(cameraWorldPosition(matrices(-36,-40),248,300), [212,260]);
assert.deepEqual(terrainCells(143.99*scale,644*scale),terrainCells(144.01*scale,644*scale));

const admission = {publicAlpha:false,renderWorker:{enabled:true},wholeZone:{enabled:true,residency:'full'},wholeRam:{enabled:true}};
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on'),admission).enabled,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on'),{...admission,publicAlpha:true}));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on'),{...admission,wholeZone:{enabled:true,residency:'bounded'}}));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('flyover=on'),admission));
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on&preparedwalk=on'),admission).preparedWalk,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('preparedwalk=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&flyover=on&preparedwalk=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&preparedwalk=on'),{...admission,publicAlpha:true}));

const flightStats = {};
const flight = createOverworldFlyover(flightStats);
for(let t=0;t<=21000;t+=25)flight(matrices(142*scale,644*scale),0,0,t);
assert.equal(flightStats.flyoverBoundaryCrossings,3);

const terrain={plane:0,role:0,sourceKind:'world',cellX:2,cellY:13,id:1000000,originX:96*scale,originZ:624*scale,visibilityMask:127};
const farTerrain={...terrain,cellX:5,id:1000001,originX:240*scale};
const object={...terrain,role:1,sourceKind:'objects',cellX:4,cellY:26,id:1000002,canonicalContentKey:'0000000000000001'};
const catalogue={chunks:[terrain,farTerrain,object],objectCells:[object],settings:[0,0,0,0]};
const state=(x,base,epoch)=>Int32Array.from([1,1,0,base,0,x,13,2,127,127,127,0,0,0,0,epoch,0,0]);
const rows=Int32Array.from([0,4,26,1,0,1]);
const ids=draws=>Array.from(draws).filter((_,i)=>i%8===0);
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on&terrainlookahead=on'),admission).terrainLookahead,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('terrainlookahead=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&terrainlookahead=on'),{...admission,publicAlpha:true}));
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on&quietwalk=on'),admission).quietWalk,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('quietwalk=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&quietwalk=on'),{...admission,publicAlpha:true}));
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on&livewalk=on'),admission).liveWalk,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('livewalk=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&livewalk=on&flyover=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&livewalk=on'),{...admission,publicAlpha:true}));
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on&workerreplay=on'),admission).workerReplay,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('workerreplay=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&workerreplay=on&flyover=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&workerreplay=on'),{...admission,publicAlpha:true}));
assert.deepEqual(walkingReplayOffset([100,200,1],[228,200],128),[64,0]);
assert.deepEqual(walkingReplayOffset([100,200,1],[100,72],1024),[0,-128]);
assert.deepEqual(walkingReplayOffset([100,200,1],[228,328],-10),[0,0]);
assert.equal(walkingReplayOffset([100,200,1],[100,200],20),null);
assert.equal(walkingReplayOffset([100,200,1],[613,200],20),null);
assert.equal(walkingReplayOffset(null,[228,200],20),null);
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on&workerreplay=on&walkbuffer=on'),admission).walkBuffer,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&walkbuffer=on'),admission));
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&workerreplay=on&walkbuffer=on'),{...admission,publicAlpha:true}));
assert.deepEqual(walkingResumeOffset([150,100],[130,100],[1,0],0),[20,0]);
assert.deepEqual(walkingResumeOffset([150,100],[160,100],[1,0],50),[0,0]);
assert.deepEqual(walkingResumeOffset([150,100],[130,100],[1,0],240),[10,0]);
assert.deepEqual(walkingResumeOffset([150,100],[130,100],[1,0],300),[0,0]);
assert.deepEqual(walkingResumeOffset([150,100],[0,100],[1,0],0),[0,0]);
assert.equal(persistentWorldPolicy(new URLSearchParams('persistentscene=on&workerreplay=on&walkstabilize=on'),admission).walkStabilize,true);
assert.throws(() => persistentWorldPolicy(new URLSearchParams('persistentscene=on&walkstabilize=on'),admission));
const selection=createWholeZoneSelection(catalogue,{}, {persistent:true});
selection.frame(state(2,0,1),rows);
const before=ids(selection.merge(new Int32Array(),0,matrices(143*scale,644*scale)));
selection.frame(state(3,48*scale,2),rows);
const after=ids(selection.merge(new Int32Array(),0,matrices((145-48)*scale,644*scale)));
assert.deepEqual(before,[terrain.id,farTerrain.id]);
assert.deepEqual(after,before);

const detached=createWholeZoneSelection(catalogue,{}, {persistent:true,flyover:true});
detached.frame(state(2,0,1),new Int32Array());
assert.deepEqual(ids(detached.merge(new Int32Array(),0,matrices(142*scale,644*scale))),[terrain.id,farTerrain.id,object.id]);
console.log('PASS persistent camera projection, local admission, region rebase, flight, and live scenery authority');
