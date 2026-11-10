// Pure same-artifact policy relay; no runtime, game server or GPU.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const root=new URL('../../',import.meta.url);
const source=fs.readFileSync(new URL('tools/browser-gameplay/web/browser-benchmark-controls.js',root),'utf8');
const {browserPreparationBenchmarkControls:controls,browserOwnedStateBenchmarkPolicy:ownedState}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
for(const mode of ['software','input-probe','webgl'])for(const query of ['', 'frameprep=off&animated=off','frameprep=invalid&animated=invalid']){
 const actual=controls(mode,new URLSearchParams(query));assert.deepEqual(actual,{properties:[],requested:null});
}
assert.deepEqual(controls('software',new URLSearchParams('benchmark&frameprep=off&animated=off')),{properties:[],requested:null});
for(const f of [undefined,'on','off'])for(const a of [undefined,'on','off']){
 const query=new URLSearchParams('benchmark');if(f)query.set('frameprep',f);if(a)query.set('animated',a);
 const actual=controls('webgl',query),frame=f!=='off',animated=a!=='off';
 assert.deepEqual(actual.requested,{framePreparation:frame,animatedBufferReuse:animated});
 assert.deepEqual(actual.properties,['spoiledmilk.browserFramePreparation='+frame,'spoiledmilk.browserAnimatedBufferReuse='+animated]);
}
for(const name of ['frameprep','animated'])for(const invalid of ['false','OFF','0','maybe']){
 assert.throws(()=>controls('webgl',new URLSearchParams('benchmark&'+name+'='+invalid)),/Invalid private/);
}
for(const mode of ['webgl','software','input-probe'])for(const query of ['', 'ownedstate=on', 'ownedstate=off', 'ownedstate=bad'])assert.equal(ownedState(mode,new URLSearchParams(query)),mode==='webgl','Ordinary URLs must select verified WebGL ownership and ignore private overrides');
assert.equal(ownedState('software',new URLSearchParams('benchmark&ownedstate=on')),false);
for(const [policy,expected]of [['',false],['on',true],['off',false]])assert.equal(ownedState('webgl',new URLSearchParams('benchmark&ownedstate='+policy)),expected);
assert.throws(()=>ownedState('webgl',new URLSearchParams('benchmark&ownedstate=bad')),/Invalid private ownedstate/);
const browser=fs.readFileSync(new URL('tools/browser-gameplay/web/browser.js',root),'utf8');
assert.match(browser,/browserPreparationBenchmarkControls\(window\.browserProof\.mode, options\)/);
assert.match(browser,/javaProperties: \['user\.dir=\/files', \.\.\.preparationControls\.properties,/);
assert.match(browser,/browserOwnedStateBenchmarkPolicy\(window\.browserProof\.mode,options\)/);
assert.match(browser,/opaqueProof:options\.get\('alpha-proof'\)==='1',ownedState/);
assert.match(browser,/preparationRequested:preparationControls\.requested/);
const receiver=browser.match(/async Java_orsc_browser_BrowserBootstrap_reportPreparation\(lib, enabled, active, animated\) \{([\s\S]*?)\n  \},/);
assert.ok(receiver,'actual native receiver signature');const context={window:{browserProof:{}}};vm.createContext(context);
const report=vm.runInContext('(async function(lib,enabled,active,animated){'+receiver[1]+'})',context);
await report(null,true,false,true);assert.equal(context.window.browserProof.preparationControls.framePreparationEnabled,true);
assert.equal(context.window.browserProof.preparationControls.framePreparationActive,false);
assert.equal(context.window.browserProof.preparationControls.animatedBufferReuseEnabled,true);
const bootstrap=fs.readFileSync(new URL('tools/browser-gameplay/src/orsc/browser/BrowserBootstrap.java',root),'utf8');
assert.match(bootstrap,/public static native void reportPreparation\(boolean enabled,boolean active,boolean animated\)/);
assert.match(bootstrap,/if\(webgl && Boolean\.getBoolean\("spoiledmilk\.browserBenchmark"\)\)/);
assert.match(bootstrap,/GraphicsController\.isBrowserFramePreparationEnabled\(\)/);
assert.match(bootstrap,/surface!=null && surface\.isBrowserFramePreparationActive\(\)/);
assert.match(bootstrap,/Boolean\.parseBoolean\(System\.getProperty\("spoiledmilk\.browserAnimatedBufferReuse","true"\)\)/);
assert.match(bootstrap,/actual\.setCameraZoomSetting\(75\);actual\.cameraRotation=128;/);
console.log('PASS private WebGL benchmark controls, on/off defaults, normal URL isolation, native receipt and unchanged camera contract');
