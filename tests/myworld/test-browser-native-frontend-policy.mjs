#!/usr/bin/env node
// CPU source/URL contract. No browser or CheerpJ claim.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {execFileSync} from 'node:child_process';
const root=new URL('../../',import.meta.url);
const source=fs.readFileSync(new URL('tools/browser-gameplay/web/browser.js',root),'utf8');
const baseline=execFileSync('git',['show','325bf42b60fe411705f9f14c0a3ef6debabe24a7:tools/browser-gameplay/web/browser.js'],{cwd:root,encoding:'utf8'});
const policySource=source.match(/function nativeCallPolicy\(mode,parameters\)\{[\s\S]*?\n\}/)?.[0];
assert(policySource);
const policy=vm.runInNewContext('('+policySource+')');
for(const mode of ['software','webgl'])for(const benchmark of [false,true])for(const value of [null,'async','sync','invalid']){
  const query=new URLSearchParams();if(benchmark)query.set('benchmark','1');if(value!==null)query.set('nativecalls',value);
  if(mode==='webgl'&&benchmark&&value==='invalid')assert.throws(()=>policy(mode,query),/Invalid private/);
  else assert.equal(policy(mode,query),mode==='webgl'&&benchmark&&value==='sync'?'sync':'async');
}
// The ASYNC reference adds no call adapter or body helper: compare each whole
// original frontend wrapper assignment byte-for-byte against production325.
for(const prefix of ['natives[progressName]=async','natives[name]=async']){
  const original=baseline.split('\n').find(line=>line.trimStart().startsWith(prefix));
  assert(original&&source.includes(original),'Literal async wrapper changed: '+prefix);
}
const begin=baseline.indexOf('    natives[presentName] = async');
const end=baseline.indexOf('\n    };',begin)+'\n    };'.length;
assert(begin>0&&source.includes(baseline.slice(begin,end)),'Literal async present wrapper changed');
assert(source.includes("nativeCallDispatch==='sync'?'./webgl-sync.js':'./webgl.js'"));
console.log('PASS private native URL policy16 cases and literal original ASYNC progress/hold/transition/present wrapper bodies');
