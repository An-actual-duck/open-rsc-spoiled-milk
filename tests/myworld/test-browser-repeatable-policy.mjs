import assert from 'node:assert/strict';
import {repeatableFixturePolicy} from '../../tools/browser-gameplay/web/browser-repeatable-policy.js';
for(const mode of ['software','webgl','mesh-probe']) {
 const result=repeatableFixturePolicy(mode,new URLSearchParams());
 assert.deepEqual(result.properties,[]);assert.equal(result.requested,null);assert.equal(result.tcpPort,43629);assert.equal(result.websocket,'ws://127.0.0.1:43529/');
}
const selected=repeatableFixturePolicy('webgl',new URLSearchParams('benchmark=1&fixture=repeatable-v1'));
assert.deepEqual(selected.properties,['spoiledmilk.browserRepeatableFixture=repeatable-v1']);assert.equal(selected.tcpPort,43649);assert.equal(selected.websocket,'ws://127.0.0.1:43549/');
const combat=repeatableFixturePolicy('webgl',new URLSearchParams('benchmark=1&fixture=renderer-combat-v1'));
assert.deepEqual(combat.properties,['spoiledmilk.browserRepeatableFixture=renderer-combat-v1']);assert.equal(combat.tcpPort,43639);assert.equal(combat.websocket,'ws://127.0.0.1:43539/');
for(const query of ['fixture=renderer-combat-v1','benchmark=1&fixture=unknown'])assert.throws(()=>repeatableFixturePolicy('webgl',new URLSearchParams(query)),/explicit private/);
assert.throws(()=>repeatableFixturePolicy('software',new URLSearchParams('benchmark=1&fixture=renderer-combat-v1')),/explicit private/);
for(const [mode,query] of [['software','benchmark=1&fixture=repeatable-v1'],['webgl','fixture=repeatable-v1'],['webgl','benchmark=1&fixture=owner'],['webgl','benchmark=1&fixture=']])assert.throws(()=>repeatableFixturePolicy(mode,new URLSearchParams(query)),/explicit private/);
console.log('PASS repeatable fixture requires private benchmark, routes only dedicated loopback ports, and leaves ordinary/software unchanged');
