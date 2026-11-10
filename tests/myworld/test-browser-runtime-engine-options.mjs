#!/usr/bin/env -S node --experimental-vm-modules
// CPU-only option/metadata/error guards. The JVM is deliberately NOT simulated.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const [stage,variant]=process.argv.slice(2);
const expected=variant==='java17-js'?{version:17}:{version:8,wasmGC:true};
const helperSource=fs.readFileSync(stage+'/web/browser-native-call-dispatch.js','utf8');
const source=fs.readFileSync(stage+'/web/browser-native-runtime-probe.js','utf8');
async function sample(query,failInit=false){
  let options,runArgs,loaderRequests=0;
  const console=Object.fromEntries(['debug','log','info','warn','error'].map(name=>[name,()=>{}]));
  const window={addEventListener(){}};
  const context=vm.createContext({window,location:{search:query},navigator:{userAgent:'CPU fixture; no real browser'},URLSearchParams,console,
    setTimeout:()=>1,clearTimeout(){},
    document:{createElement:()=>({}),head:{append(script){loaderRequests++;queueMicrotask(()=>script.onload());}}},
    cheerpjInit:async value=>{options=value;if(failInit)throw Error('injected init failure');},
    cheerpjRunMain:(...args)=>{runArgs=args;return new Promise(()=>{});},
  });
  const helper=new vm.SourceTextModule(helperSource,{context});
  const module=new vm.SourceTextModule(source,{context});
  await module.link(name=>{assert.equal(name,'./browser-native-call-dispatch.js');return helper;});
  const evaluation=module.evaluate();evaluation.catch(()=>{});
  for(let n=0;n<40;n++)await Promise.resolve();
  return {proof:window.nativeRuntimeProof,options,runArgs,loaderRequests};
}
const valid=await sample('?mode=values');
assert(valid.options);
const actual=Object.fromEntries(Object.entries(valid.options).filter(([name])=>name!=='natives'));
assert.deepEqual(actual,expected);
assert.equal(typeof valid.options.natives.Java_orsc_browser_NativeDispatchProbe_voidAsync,'function');
assert.equal(typeof valid.options.natives.Java_orsc_browser_NativeDispatchProbe_voidSync,'function');
assert.deepEqual(valid.runArgs,['orsc.browser.NativeDispatchProbe','/app/probe.jar','values']);
assert.equal(valid.proof.activation.initResolved,true);
assert.equal(valid.proof.activation.probePassed,false);
assert.equal(valid.proof.completed,false,'CPU test must never pretend that Java completed1903 checks');
assert.equal(valid.proof.activation.actualEngineObserved,null);
assert.equal(valid.proof.activation.actualJavaVersionObserved,null);
assert.equal(valid.proof.requestedRuntime.javaVersion,expected.version);
assert.equal(valid.proof.requestedRuntime.wasmGC,expected.wasmGC===true);
for(const query of ['?mode=values&wasmgc=off','?wasmgc','?wasmGC=true','?java=8','?engine=js','?runtime=4.3','?mode=values&mode=sync-fatal']){
  const invalid=await sample(query);
  assert.equal(invalid.options,undefined);assert.equal(invalid.runArgs,undefined);assert.equal(invalid.loaderRequests,0);
  assert.equal(invalid.proof.completed,true);assert.equal(invalid.proof.passed,false);
  assert.match(invalid.proof.errors[0].message,/Runtime options are fixed/);
}
const failed=await sample('?mode=values',true);
assert.equal(failed.proof.activation.initResolved,false);assert.equal(failed.proof.completed,true);assert.equal(failed.proof.passed,false);
assert.equal(failed.runArgs,undefined);assert.match(failed.proof.errors[0].message,/injected init failure/);
for(const mode of ['async-fatal','sync-fatal','thenable-fatal']){
  const selected=await sample('?mode='+mode);assert.equal(selected.runArgs[2],mode);assert.equal(selected.proof.mode,mode);assert.equal(selected.proof.completed,false);
}
console.log('PASS '+variant+' exact init options, explicit requested/observed distinction, forbidden runtime query includingwasmgc=off, bounded failure and unchanged4-mode dispatch; CPU only');
