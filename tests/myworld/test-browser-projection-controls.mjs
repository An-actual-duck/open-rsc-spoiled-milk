import assert from 'node:assert/strict';
import {browserProjectionBenchmarkControls as controls} from '../../tools/browser-gameplay/web/browser-projection-benchmark-controls.js';
for(const mode of ['software','webgl'])for(const query of ['', 'projectionreuse=on','projectionreuse=off','projectionreuse=bad'])assert.deepEqual(controls(mode,new URLSearchParams(query)),{properties:[],requested:null});
for(const [query,expected]of [['benchmark=1',false],['benchmark=1&projectionreuse=off',false],['benchmark=1&projectionreuse=on',true]])assert.deepEqual(controls('webgl',new URLSearchParams(query)),{properties:['spoiledmilk.browserProjectionReuse='+expected],requested:expected});
assert.throws(()=>controls('webgl',new URLSearchParams('benchmark=1&projectionreuse=invalid')),/on or off/);
assert.deepEqual(controls('software',new URLSearchParams('benchmark=1&projectionreuse=on')),{properties:[],requested:null});
console.log('PASS startup-only projection reuse policy, ordinary/software URL isolation and invalid benchmark refusal');
