import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../../tools/browser-gameplay/web/browser-geometry-transfer-policy.js',import.meta.url),'utf8');
const {browserGeometryTransferPolicy:policy}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
for(const mode of ['webgl','software'])for(const query of ['', 'geometrytransfer=on','geometrytransfer=off','geometrytransfer=garbage'])
  assert.deepEqual(policy(mode,new URLSearchParams(query)),{properties:[],requested:null});
for(const mode of ['software','mesh-probe'])assert.deepEqual(policy(mode,new URLSearchParams('benchmark&geometrytransfer=on')),{properties:[],requested:null});
for(const [query,enabled]of [['benchmark',false],['benchmark&geometrytransfer=off',false],['benchmark&geometrytransfer=on',true]])
  assert.deepEqual(policy('webgl',new URLSearchParams(query)),{properties:['spoiledmilk.browserGeometryTransferReuse='+enabled],requested:enabled});
assert.throws(()=>policy('webgl',new URLSearchParams('benchmark&geometrytransfer=bad')),/Invalid private/);
console.log('PASS private geometry-transfer policy, default OFF and ordinary/software query isolation');
