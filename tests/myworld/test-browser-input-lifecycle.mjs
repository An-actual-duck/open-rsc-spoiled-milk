#!/usr/bin/env node
// Real headless Chrome DOM/input lifecycle, with a mocked CDN/Java runtime.
// Never attaches to an existing browser or generates physical desktop input.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import {spawn, execFileSync} from 'node:child_process';
const root = path.resolve(import.meta.dirname, '../..');
const suite = execFileSync('python3', ['scripts/workspace-layout.py', 'path', 'tests', 'browser-input-lifecycle', '--mkdir'], {cwd: root, encoding: 'utf8'}).trim();
const run = path.join(suite, String(Date.now())); fs.mkdirSync(run);
const web = path.join(root, 'tools/browser-gameplay/web');
const server = http.createServer((req, res) => {
  const name = new URL(req.url, 'http://localhost').pathname.slice(1) || 'index.html';
  if (!/^[a-z.-]+$/.test(name) || !fs.existsSync(path.join(web, name))) { res.writeHead(404); res.end(); return; }
  res.setHeader('Content-Type', name.endsWith('.js') ? 'text/javascript' : 'text/html');
  res.end(fs.readFileSync(path.join(web, name)));
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/?mode=software`;
const profile = path.join(run, 'chrome-profile');
const browser = spawn('/usr/bin/google-chrome', ['--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-dev-shm-usage', '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--window-size=1200,950', 'about:blank'], {stdio: 'ignore'});
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let ws, sequence = 0, loads = 0; const pending = new Map(), events = [];
function call(method, params = {}) { return new Promise((resolve, reject) => { const id = ++sequence; pending.set(id, {resolve, reject}); ws.send(JSON.stringify({id, method, params})); }); }
async function evaluate(expression) { const r = await call('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true}); if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails)); return r.result.value; }
async function wait(expression) { for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await delay(30); } throw new Error('Timeout: ' + expression); }
async function click(expression) {
  const r = await evaluate(`(()=>{const e=${expression},r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`);
  await call('Input.dispatchMouseEvent', {type: 'mousePressed', ...r, button: 'left', clickCount: 1});
  await call('Input.dispatchMouseEvent', {type: 'mouseReleased', ...r, button: 'left', clickCount: 1});
}
const mock = `
window.cheerpjInit=async options=>{window.testOptions=options};
window.cheerpjCreateDisplay=(w,h,e)=>{const input=document.createElement('input');input.id='java-input';e.append(input)};
window.cheerpjRunMain=async()=>new Promise((resolve,reject)=>{
  window.testExit=resolve;window.testFail=()=>reject(new Error('controlled runtime failure'));
  window.testOptions.natives.Java_orsc_browser_BrowserBootstrap_reportState(null,'LOGIN',50,100);
  setInterval(()=>{parent.testTicks=(parent.testTicks||0)+1},20);
});`;
try {
  let targets;
  for (let i = 0; i < 80; i++) {
    try { const port = Number(fs.readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0]); targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); if (targets.length) break; } catch {}
    await delay(100);
  }
  assert.ok(targets?.length);
  ws = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  ws.onmessage = event => {
    const value = JSON.parse(event.data);
    if (value.id) { const p = pending.get(value.id); pending.delete(value.id); value.error ? p.reject(new Error(JSON.stringify(value.error))) : p.resolve(value.result); }
    else if (value.method === 'Fetch.requestPaused') {
      loads++;
      call('Fetch.fulfillRequest', {requestId: value.params.requestId, responseCode: 200, responseHeaders: [{name: 'Content-Type', value: 'text/javascript'},{name:'Access-Control-Allow-Origin',value:'*'}], body: Buffer.from(mock).toString('base64')}).catch(console.error);
    } else events.push(value);
  };
  await call('Page.enable'); await call('Runtime.enable');
  await call('Fetch.enable', {patterns: [{urlPattern: 'https://cjrtnc.leaningtech.com/*'}]});
  await call('Page.navigate', {url});
  await wait('!!document.querySelector("#start")'); await delay(150);
  assert.equal(await evaluate('document.querySelectorAll("iframe").length'), 0); assert.equal(loads, 0);
  async function start() {
    await click('document.querySelector("#start")');
    await wait('!!document.querySelector("#runtime")?.contentWindow.testExit');
    assert.deepEqual(await evaluate('(()=>{const f=document.querySelector("#runtime"),o=f.contentWindow.testOptions;return [f.sandbox.value,f.allow,o.enableInputMethods,o.clipboardMode,o.overrideShortcuts({ctrlKey:true,key:"f"})]})()'), ['allow-scripts allow-same-origin', "fullscreen 'none'; clipboard-read 'none'; clipboard-write 'none'; keyboard-map 'none'", false, 'java', false]);
  }
  async function closeFromRecovery() {
    // There is no running-game toolbar. A fatal condition exposes a transient
    // recovery surface, whose visible Close button tears down the owned frame.
    await evaluate(`document.querySelector("#runtime").contentWindow.eval('parent.postMessage({type:"browser-session-proof",proof:{gpu:{worldReadiness:{phase:"error"}}}},location.origin)')`);
    await wait('!document.querySelector("#welcome").hidden');
    await click('document.querySelector("#stop")');
  }
  await start();
  assert.equal(await evaluate('document.querySelector("#welcome").hidden'),true);
  // Genuine browser focus/key events inside the nested document remain usable.
  await evaluate('document.querySelector("#runtime").contentDocument.querySelector("input").focus()');
  await call('Input.dispatchKeyEvent', {type: 'keyDown', key:'a',code:'KeyA',text:'a',windowsVirtualKeyCode:65});
  await call('Input.dispatchKeyEvent', {type: 'keyUp', key:'a',code:'KeyA',windowsVirtualKeyCode:65});
  assert.equal(await evaluate('document.querySelector("#runtime").contentDocument.querySelector("input").value'), 'a');
  await delay(80); assert.equal(await evaluate('document.querySelectorAll("iframe").length'), 1);
  await call('Input.dispatchKeyEvent', {type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
  await call('Input.dispatchKeyEvent', {type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
  await delay(100); assert.equal(await evaluate('document.querySelectorAll("#runtime").length'), 1);
  await closeFromRecovery();
  await wait('!document.querySelector("#runtime")');
  const ticks = await evaluate('testTicks'); await delay(120); assert.equal(await evaluate('testTicks'), ticks);
  await start(); await evaluate('document.querySelector("#runtime").contentWindow.testExit(0)');
  await wait('!document.querySelector("#runtime")'); assert.match(await evaluate('browserProof.stopReason'), /program stopped/);
  await start(); await evaluate('document.querySelector("#runtime").contentWindow.testFail()');
  await wait('!document.querySelector("#runtime")'); assert.match(await evaluate('browserProof.stopReason'), /controlled runtime failure/);
  await start();
  await evaluate('document.querySelector("#runtime").contentDocument.querySelector("input").focus()');
  await evaluate('(()=>{const input=document.createElement("input");input.id="outside";document.body.append(input);input.focus()})()');
  await delay(100);
  assert.equal(await evaluate('document.querySelectorAll("iframe").length'), 1);
  // Vendor startup may post a focus departure while its nested contexts settle.
  // It must remain advisory even after the ordinary runtime state is available.
  await evaluate(`document.querySelector("#runtime").contentWindow.eval('parent.postMessage({type:"browser-session-focus-left"},location.origin)')`);
  await delay(100);
  assert.equal(await evaluate('document.querySelectorAll("iframe").length'), 1);
  assert.equal(await evaluate('browserProof.focus'), 'outside-runtime');
  await call('Input.dispatchKeyEvent', {type:'keyDown',key:'b',code:'KeyB',text:'b',windowsVirtualKeyCode:66});
  await call('Input.dispatchKeyEvent', {type:'keyUp',key:'b',code:'KeyB',windowsVirtualKeyCode:66});
  assert.equal(await evaluate('document.querySelector("#outside").value'), 'b');
  await closeFromRecovery();
  await wait('!document.querySelector("#runtime")');
  await start();
  const alternate = await call('Target.createTarget', {url:'about:blank'});
  await call('Target.activateTarget', {targetId:alternate.targetId});
  await wait('browserProof.visibility==="hidden"');
  assert.equal(await evaluate('document.querySelectorAll("#runtime").length'), 1);
  await call('Target.closeTarget', {targetId:alternate.targetId});
  await call('Target.activateTarget', {targetId:targets.find(t=>t.type==='page').id});
  await wait('browserProof.visibility==="visible"');
  assert.equal(await evaluate('document.querySelectorAll("#runtime").length'), 1);
  await closeFromRecovery();
  await wait('!document.querySelector("#runtime")');
  // Removed/stale contexts cannot send trusted stop commands into a new session.
  await start(); await evaluate('window.postMessage({type:"browser-session-stop",reason:"untrusted parent"},location.origin)');
  await delay(80); assert.equal(await evaluate('document.querySelectorAll("iframe").length'), 1);
  await closeFromRecovery();
  const result = {passed:true, runtime:'mocked; actual browser DOM/CDP input', loads, tests:['explicit start','frame sandbox/options','nested input','Escape preservation','timer teardown','Stop','Java exit','bootstrap failure','advisory focus departure','outer input while session remains alive','actual tab hide/restore survival','restart/stale message rejection'], desktopGlobalKeyboard:'NOT_TESTED; reported failure not reproduced'};
  fs.writeFileSync(path.join(run, 'result.json'), JSON.stringify(result, null, 2));
  console.log('PASS browser input lifecycle', run);
} finally {
  fs.writeFileSync(path.join(run, 'events.json'), JSON.stringify(events));
  try { if (ws?.readyState === WebSocket.OPEN) await call('Browser.close'); } catch {}
  ws?.close(); if (browser.exitCode === null) browser.kill('SIGTERM'); server.close();
}
