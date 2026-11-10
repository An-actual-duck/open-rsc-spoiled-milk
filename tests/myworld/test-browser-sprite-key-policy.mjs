import assert from 'node:assert/strict';
import fs from 'node:fs';
import {browserSpriteKeyBenchmarkControls as policy} from '../../tools/browser-gameplay/web/browser-sprite-key-policy.js';
for(const [mode,query,wanted] of [['software','',null],['software','benchmark&spritekeylimbs=on',null],['webgl','spritekeylimbs=on',null],['webgl','',null],['webgl','benchmark',false],['webgl','benchmark&spritekeylimbs=off',false],['webgl','benchmark&spritekeylimbs=on',true]]){
 const r=policy(mode,new URLSearchParams(query));assert.equal(r.requested,wanted);assert.deepEqual(r.properties,wanted===null?[]:['spoiledmilk.browserSpriteKeyLimbs='+wanted]);
}
assert.throws(()=>policy('webgl',new URLSearchParams('benchmark&spritekeylimbs=maybe')),/Invalid private/);
const bootstrap=fs.readFileSync(new URL('../../tools/browser-gameplay/src/orsc/browser/BrowserBootstrap.java',import.meta.url),'utf8');assert.equal((bootstrap.match(/reportSpriteKeyPolicy\(/g)||[]).length,2,'Exactly declaration + one startup report');assert(bootstrap.indexOf('reportSpriteKeyPolicy(Boolean')>bootstrap.indexOf('BrowserWebGLBridge.install()'),'Receipt must read installed production policy');
assert(bootstrap.includes('Boolean.parseBoolean(System.getProperty("spoiledmilk.browserSpriteKeyLimbs", "true"))'),'Ordinary requested receipt must use actual default true');
console.log('PASS startup-only sprite key policy, ordinary/software isolation, private default OFF, ordinary no override and one-time installed-cache receipt');
