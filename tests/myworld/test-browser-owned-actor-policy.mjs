import assert from 'node:assert/strict';
import {browserOwnedActorPixelPolicy as policy} from '../../tools/browser-gameplay/web/browser-owned-actor-policy.js';
for(const [mode,query,wanted,override] of [
  ['software','benchmark&actorpixels=on',false,false],['webgl','actorpixels=on',false,false],
  ['webgl','',false,false],['webgl','benchmark',false,true],
  ['webgl','benchmark&actorpixels=off',false,true],['webgl','benchmark&actorpixels=on',true,true]]) {
  const result=policy(mode,new URLSearchParams(query));
  assert.equal(result.requested,wanted);
  assert.deepEqual(result.properties,override?['spoiledmilk.browserOwnedActorPixels='+wanted]:[]);
}
assert.throws(()=>policy('webgl',new URLSearchParams('benchmark&actorpixels=maybe')),/Invalid private/);
console.log('PASS actor pixels private default-OFF policy, software/ordinary isolation');
