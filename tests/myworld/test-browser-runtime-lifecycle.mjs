#!/usr/bin/env node
// Actual CheerpJ lifecycle controls in a fresh isolated Chrome profile.
// Does not log in, mock the vendor runtime, attach to an owner's browser, or
// send OS-wide input. This is a functional check, never a GPU benchmark.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawn, execFileSync} from 'node:child_process';
const root = path.resolve(import.meta.dirname, '../..');
const [stageArg, portArg] = process.argv.slice(2), port = Number(portArg);
if (!stageArg || !Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Usage: test-browser-runtime-lifecycle.mjs STAGED-RUN LOOPBACK-HTTP-PORT');
const stage = path.resolve(stageArg), staged = JSON.parse(fs.readFileSync(path.join(stage, 'stage.json'), 'utf8'));
assert.equal(staged.credentialsCopied, false);
const category = execFileSync('python3', ['scripts/workspace-layout.py', 'path', 'tests', 'browser-input-lifecycle', '--mkdir'], {cwd:root, encoding:'utf8'}).trim();
const run = path.join(category, 'actual-runtime-' + Date.now()); fs.mkdirSync(run);
const profile = path.join(run, 'chrome-profile'), events = [], checks = [];
const browser = spawn('/usr/bin/google-chrome', ['--headless=new','--no-first-run','--no-default-browser-check','--disable-dev-shm-usage','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'--window-size=1200,950','about:blank'], {stdio:'ignore'});
const delay = ms => new Promise(resolve=>setTimeout(resolve,ms));
let ws, next = 0; const pending = new Map(); let pageTarget;
function call(method,params={}) { return new Promise((resolve,reject)=>{const id=++next;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));}); }
async function evaluate(expression) {const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
async function wait(expression, seconds=45) {const until=Date.now()+seconds*1000;while(Date.now()<until){if(await evaluate(expression))return;await delay(100);}throw new Error('Timed out: '+expression);}
async function click(expression) {
  const p=await evaluate(`(()=>{const e=${expression},r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`);
  await call('Input.dispatchMouseEvent',{type:'mousePressed',...p,button:'left',clickCount:1});
  await call('Input.dispatchMouseEvent',{type:'mouseReleased',...p,button:'left',clickCount:1});
}
async function key(key,code,value) {
  await call('Input.dispatchKeyEvent',{type:'keyDown',key,code,windowsVirtualKeyCode:value,...(key.length===1?{text:key}:{})});
  await call('Input.dispatchKeyEvent',{type:'keyUp',key,code,windowsVirtualKeyCode:value});
}
async function start() {
  await click('document.querySelector("#start")');
  await wait('window.browserProof?.state?.mode==="LOGIN" && window.browserProof.state.fps>=10');
  assert.equal(await evaluate('document.querySelectorAll("#runtime").length'),1);
  await delay(500);
  assert.equal(await evaluate('browserProof.lifecycle'),'running');
  checks.push('actual runtime initialized and reached responsive login');
}
try {
  for(let i=0;i<100;i++){
    try{const port=Number(fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0]);const targets=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json();pageTarget=targets.find(t=>t.type==='page');if(pageTarget)break;}catch{}
    await delay(100);
  }
  assert.ok(pageTarget);
  ws=new WebSocket(pageTarget.webSocketDebuggerUrl);await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
  ws.onmessage=e=>{const v=JSON.parse(e.data);if(v.id){const p=pending.get(v.id);pending.delete(v.id);if(v.error)p?.reject(new Error(JSON.stringify(v.error)));else p?.resolve(v.result);}else events.push(v);};
  await call('Page.enable');await call('Runtime.enable');
  await call('Page.navigate',{url:`http://127.0.0.1:${port}/?mode=software`});
  await wait('!!document.querySelector("#start")',10);
  assert.equal(await evaluate('document.querySelectorAll("iframe").length'),0);
  await start();
  await evaluate('(()=>{const e=document.createElement("input");e.id="outside";e.setAttribute("aria-label","Private lifecycle probe");document.querySelector("header").append(e)})()');
  await click('document.querySelector("#outside")');await key('q','KeyQ',81);
  assert.equal(await evaluate('document.querySelector("#outside").value'),'q');
  assert.equal(await evaluate('document.querySelectorAll("#runtime").length'),1);
  checks.push('ordinary outside input remains usable while Java stays alive');
  await click('document.querySelector("#stop")');await wait('!document.querySelector("#runtime")',5);
  assert.match(await evaluate('browserProof.stopReason'),/Stopped by user/);checks.push('actual Stop removes runtime');
  await start();
  await evaluate('document.querySelector("#runtime").focus()');await key('Escape','Escape',27);
  await delay(500);assert.equal(await evaluate('document.querySelectorAll("#runtime").length'),1);
  checks.push('actual Escape preserves restarted runtime');
  const other=await call('Target.createTarget',{url:'about:blank'});await call('Target.activateTarget',{targetId:other.targetId});
  await wait('browserProof.visibility==="hidden"',5);
  assert.equal(await evaluate('document.querySelectorAll("#runtime").length'),1);
  await call('Target.closeTarget',{targetId:other.targetId});await call('Target.activateTarget',{targetId:pageTarget.id});
  await wait('browserProof.visibility==="visible"',5);
  assert.equal(await evaluate('document.querySelectorAll("#runtime").length'),1);
  await wait('window.browserProof?.state?.mode==="LOGIN" && window.browserProof.state.fps>=10');
  checks.push('actual hidden/restored tab preserves responsive Java runtime');
  await click('document.querySelector("#stop")');await wait('!document.querySelector("#runtime")',5);
  const screenshot=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(run,'after-controls.png'),Buffer.from(screenshot.data,'base64'));
  fs.writeFileSync(path.join(run,'result.json'),JSON.stringify({passed:true,actualVendorRuntime:true,runtime:staged.runtime,sourceStage:stage,stage:staged,checks,desktopGlobalKeyboard:'NOT_TESTED; original reported issue was not reproduced',browser:await call('Browser.getVersion')},null,2));
  console.log('PASS actual browser runtime lifecycle',run);
} catch(error) {
  fs.writeFileSync(path.join(run,'failure.txt'),String(error.stack||error));
  try {const image=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(run,'failure.png'),Buffer.from(image.data,'base64'));fs.writeFileSync(path.join(run,'failure-state.json'),JSON.stringify(await evaluate('({proof:window.browserProof,text:document.body.innerText})'),null,2));}catch{}
  throw error;
} finally {
  fs.writeFileSync(path.join(run,'events.json'),JSON.stringify(events));
  try{if(ws?.readyState===WebSocket.OPEN)await call('Browser.close');}catch{}
  ws?.close();if(browser.exitCode===null)browser.kill('SIGTERM');
}
