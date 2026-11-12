import assert from 'node:assert/strict';
import {createBoundedInputGate} from '../../tools/browser-gameplay/web/browser-bounded-input.js';
const gate=createBoundedInputGate(),v=Int32Array.from([1,1,0,0,0,2,13,2,127,127,127,0,0,1,0,1,0,0]),rows=Int32Array.from([0,4,26,123,456,0]);
assert.equal(gate.blocked,false);assert.equal(gate.declare(v,rows,10),true);assert.equal(gate.blocked,true);
// A ready ACK for the OLD scene races with Java's newer source declaration.
gate.acknowledge({frameId:10},{coverageReady:true,worldReady:true});assert.equal(gate.blocked,true);
gate.acknowledge({frameId:11},{coverageReady:false,worldReady:false});assert.equal(gate.blocked,true,'held ACK allowed world clicks');
gate.acknowledge({frameId:12},{coverageReady:true,worldReady:true});assert.equal(gate.blocked,false);
assert.equal(gate.declare(v,rows,12),false);v[8]=0;assert.equal(gate.declare(v,rows,12),false,'camera roof masks invalidated unchanged residency');
rows[5]=1;assert.equal(gate.declare(v,rows,12),true);rows[5]=0;assert.equal(gate.declare(v,rows,13),true);
gate.acknowledge({frameId:13},{coverageReady:true,worldReady:true});assert.equal(gate.blocked,true,'older ready mutation ACK cleared newer restoration gate');
gate.acknowledge({frameId:14},{coverageReady:true,worldReady:false});assert.equal(gate.blocked,true);
gate.acknowledge({frameId:14},{coverageReady:true,worldReady:true});assert.equal(gate.blocked,false);
for(const [index,value]of [[2,-1],[2,10],[17,1],[5,50],[15,9]]){v[index]=value;assert(gate.declare(v,rows,14));assert(gate.blocked);gate.acknowledge({frameId:15},{coverageReady:true,worldReady:true});assert.equal(gate.blocked,false);}
console.log('PASS bounded pointer gate blocks producer/source versus held-image ACK race, stale ready ACKs, mutation/restoration and signed/world scope; no hardware input test.');
