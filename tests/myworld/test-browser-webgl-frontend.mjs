#!/usr/bin/env -S node --experimental-vm-modules
// Browser frontend API contract with mocked Java/WebGL transport only.
// No browser, hardware rendering, network or performance claim.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
let clock = 500, gpuDisposed = 0, transportDisposed = 0, configured;
const status = {textContent:''}, messages = [];
const target = {width:960,height:560,getBoundingClientRect:()=>({left:20,top:40,width:960,height:560})};
const display = {style:{},querySelectorAll:()=>[target,canvas],getBoundingClientRect:()=>({left:0,top:0}),append:()=>{canvas.parentElement=display;}};
const canvas = {style:{},setAttribute(){}};
const stats = {lastError:null,lastFrame:0};
const window = {browserRuntimeVersion:'5.0',addEventListener(){}};
const context = vm.createContext({window,location:{search:'?mode=webgl',origin:'http://127.0.0.1'},parent:{postMessage:m=>messages.push(m)},
  URLSearchParams,performance:{now:()=>clock},console,
  document:{querySelector:s=>s==='#status'?status:display,createElement:s=>s==='canvas'?canvas:{},head:{append:s=>queueMicrotask(()=>s.onload())},addEventListener(){}},
  queueMicrotask,setTimeout,WebSocket:class{},
  cheerpjInit:async o=>{configured=o;},cheerpjCreateDisplay(){},cheerpjRunMain:()=>new Promise(()=>{})});
const transportModule = new vm.SyntheticModule(['createTransport'],function(){this.setExport('createTransport',()=>({dispose:()=>transportDisposed++}));},{context});
const gpuModule = new vm.SyntheticModule(['createBrowserWebGL'],function(){this.setExport('createBrowserWebGL',()=>({canvas,stats,dispose:()=>gpuDisposed++,natives:{
  Java_orsc_BrowserWebGLBridge_present:async(lib,width,height)=>{canvas.width=width;canvas.height=height;stats.lastFrame=clock;},
}}));},{context});
const source = fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser.js',import.meta.url),'utf8');
const module = new vm.SourceTextModule(source,{context,importModuleDynamically:async specifier=>{assert.equal(specifier,'./webgl.js');await gpuModule.link(()=>{});await gpuModule.evaluate();return gpuModule;}});
await module.link(specifier=>{assert.equal(specifier,'./transport.js');return transportModule;});
module.evaluate();
for(let i=0;i<30&&!configured;i++)await new Promise(resolve=>setImmediate(resolve));
assert.ok(configured,'actual frontend did not initialize Java options');
assert.equal(canvas.style.pointerEvents,'none');
assert.equal(configured.enableInputMethods,false);
await configured.natives.Java_orsc_BrowserWebGLBridge_present(null,960,540);
assert.equal(canvas.style.left,'20px');assert.equal(canvas.style.top,'60px');
assert.equal(canvas.style.width,'960px');assert.equal(canvas.style.height,'540px');
assert.equal(canvas.style.display,'block');
stats.lastError='controlled shader error';clock=2000;
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportState(null,'GAME',60,100);
assert.match(status.textContent,/controlled shader error/);assert.match(status.textContent,/1500 ms ago/);
await configured.natives.Java_orsc_browser_BrowserBootstrap_report(null,'ordinary successful update');
assert.match(status.textContent,/controlled shader error/);
window.browserSessionDispose();window.browserSessionDispose();
assert.equal(gpuDisposed,1);assert.equal(transportDisposed,1);
assert.ok(messages.some(m=>m.proof?.gpu?.lastError==='controlled shader error'));
console.log('PASS mocked frontend contract: native merge, AWT alignment, pointer transparency, sticky GPU errors/age and idempotent Stop disposal; no hardware proof');
