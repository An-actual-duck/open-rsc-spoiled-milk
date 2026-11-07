#!/usr/bin/env node
// Disposable Chrome + CDP capture. Does not attach to the owner's browser profile.
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';

const [runArg, mode = 'native-probe', secondsArg = '90'] = process.argv.slice(2);
if (!runArg || !['native-probe', 'mesh-probe', 'software'].includes(mode)) throw new Error('Usage: inspect.mjs RUN [native-probe|mesh-probe|software] [seconds]');
const run = path.resolve(runArg), seconds = Math.min(300, Math.max(5, Number(secondsArg)));
const httpPort = Number(process.argv.find(arg => arg.startsWith('--port='))?.split('=')[1] || '8079');
const login = process.argv.includes('--login');
const javaVersion = process.argv.find(arg => arg.startsWith('--java='))?.split('=')[1] || '8';
const wasmGC = process.argv.includes('--wasmgc');
const runtime = process.argv.find(arg => arg.startsWith('--runtime='))?.split('=')[1] || '5.0';
if (!['5.0', '4.3'].includes(runtime)) throw new Error('Choose --runtime=5.0 or 4.3');
if (!['8', '11', '17'].includes(javaVersion)) throw new Error('Choose --java=8, 11 or 17');
if (!fs.existsSync(path.join(run, 'stage.json'))) throw new Error('Use a staged private run');
const capture = path.join(run, `${mode}-r${runtime}-j${javaVersion}${wasmGC ? '-gc' : ''}-${Date.now()}`); fs.mkdirSync(capture);
const profile = path.join(capture, 'chrome-profile');
const browser = spawn('/usr/bin/google-chrome', ['--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-dev-shm-usage',
  '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--window-size=1200,950', 'about:blank'], {stdio: 'ignore'});
const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
let ws, seq = 0; const pending = new Map();
let gameCapture;
let debugArmed = false;
const log = fs.createWriteStream(path.join(capture, 'events.jsonl'));
function call(method, params = {}) {
  return new Promise((resolve, reject) => { const id = ++seq; pending.set(id, {resolve, reject}); ws.send(JSON.stringify({id, method, params})); });
}
try {
  let targets;
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      const port = Number(fs.readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0]);
      targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); if (targets.length) break;
    } catch {}
    await delay(250);
  }
  if (!targets?.length) throw new Error('Dedicated test browser did not start');
  ws = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  ws.onmessage = event => {
    const value = JSON.parse(event.data);
    if (value.id) {
      const request = pending.get(value.id); pending.delete(value.id);
      if (value.error) request?.reject(new Error(JSON.stringify(value.error))); else request?.resolve(value.result);
    } else {
      log.write(JSON.stringify(value) + '\n');
      if (value.method === 'Debugger.paused') {
        (async () => {
          if (value.params.data?.type === 'string' && String(value.params.data.value).startsWith('CheerpJ')) { await call('Debugger.resume'); return; }
          await call('Debugger.setPauseOnExceptions', {state:'none'});
          fs.writeFileSync(path.join(capture, 'paused-exception.json'), JSON.stringify(value.params, null, 2));
          for (const [index, frame] of value.params.callFrames.slice(0, 8).entries()) {
            const source = await call('Debugger.getScriptSource', {scriptId:frame.location.scriptId});
            fs.writeFileSync(path.join(capture, `exception-frame-${index}.js`), source.scriptSource);
          }
          await call('Debugger.resume');
        })().catch(console.error);
      }

      if (!gameCapture && value.method === 'Runtime.consoleAPICalled' && value.params.args[0]?.value === 'BROWSER_PROOF_STATE') {
        const state = JSON.parse(value.params.args[1].value);
        if (state.mode === 'GAME' && process.argv.includes('--debug-exceptions') && !debugArmed) { debugArmed=true; call('Debugger.enable').then(()=>call('Debugger.setPauseOnExceptions', {state:'all'})); }
        if (state.mode === 'GAME') gameCapture = call('Page.captureScreenshot', {format: 'png'}).then(image => fs.writeFileSync(path.join(capture, 'initial-game-state.png'), Buffer.from(image.data, 'base64'))).catch(error => fs.writeFileSync(path.join(capture, 'initial-game-state-capture-error.txt'), String(error)));
      }
    }
  };
  ws.onclose = () => { for (const request of pending.values()) request.reject(new Error('Dedicated test browser closed')); pending.clear(); };
  for (const domain of ['Runtime', 'Log', 'Page', 'Network']) await call(domain + '.enable');
  await call('Page.navigate', {url: `http://127.0.0.1:${httpPort}/?mode=${mode}&runtime=${runtime}&java=${javaVersion}${wasmGC ? '&wasmgc=1' : ''}`});
  async function screenshot(name) {
    const image = await call('Page.captureScreenshot', {format: 'png'});
    fs.writeFileSync(path.join(capture, name + '.png'), Buffer.from(image.data, 'base64'));
  }
  async function click(x, y) {
    await call('Input.dispatchMouseEvent', {type: 'mousePressed', x, y, button: 'left', clickCount: 1});
    await call('Input.dispatchMouseEvent', {type: 'mouseReleased', x, y, button: 'left', clickCount: 1});
    await delay(300);
  }
  async function type(text) {
    for (const key of text) {
      const keycode = key.toUpperCase().charCodeAt(0);
      const code = /[0-9]/.test(key) ? 'Digit' + key : 'Key' + key.toUpperCase();
      await call('Input.dispatchKeyEvent', {type: 'keyDown', key, code, text: key, unmodifiedText: key, windowsVirtualKeyCode: keycode, nativeVirtualKeyCode: keycode});
      await call('Input.dispatchKeyEvent', {type: 'keyUp', key, code, windowsVirtualKeyCode: keycode, nativeVirtualKeyCode: keycode});
      await delay(20);
    }
  }
  let didLogin = false, didAccept = false;
  for (let i = 0; i < seconds; i++) {
    await delay(1000);
    const readiness = login && !didLogin && i >= 18 ? await call('Runtime.evaluate', {expression: '(window.browserProof?.state?.fps || 0) >= 10', returnByValue: true}) : null;
    if (readiness?.result.value) {
      didLogin = true;
      await screenshot('login-ready');
      const result = await call('Runtime.evaluate', {expression: 'JSON.stringify(Array.from(document.querySelectorAll("canvas")).map(c=>{const r=c.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}}).sort((a,b)=>b.w*b.h-a.w*a.h)[0])', returnByValue: true});
      const r = JSON.parse(result.result.value); fs.writeFileSync(path.join(capture, 'canvas-rect.json'), JSON.stringify(r));
      await click(r.x + r.w / 2 + 100, r.y + r.h / 2 + 123);
      await click(r.x + r.w / 2 - 116, r.y + r.h / 2 + 98); await type('browserdemo');
      await click(r.x + r.w / 2 - 46, r.y + r.h / 2 + 146); await type('BrowserProof13');
      await screenshot('login-form');
      await click(r.x + r.w / 2 + 154, r.y + r.h / 2 + 83);
    }
    if (process.argv.includes('--accept-character') && didLogin && !didAccept) {
      const mode = await call('Runtime.evaluate', {expression: 'window.browserProof?.state?.mode', returnByValue: true});
      if (mode.result.value === 'GAME') { await screenshot('character-design'); await click(275, 491); didAccept = true; }
    }
    if (login && i === 35) await screenshot('after-login');
    if (i % 10 === 9) {
      const snapshot = await call('Runtime.evaluate', {expression: 'JSON.stringify({proof:window.browserProof&&{state:window.browserProof.state,events:window.browserProof.events},text:document.body.innerText.slice(-600),canvases:document.querySelectorAll("canvas").length})', returnByValue: true});
      console.log(i + 1, snapshot.result.value);
    }
  }
  const snapshot = await call('Runtime.evaluate', {expression: 'JSON.stringify({proof:window.browserProof,text:document.body.innerText,resources:performance.getEntriesByType("resource").map(r=>({name:r.name,duration:r.duration,transfer:r.transferSize})),canvases:Array.from(document.querySelectorAll("canvas")).map(c=>({width:c.width,height:c.height}))})', returnByValue: true});
  fs.writeFileSync(path.join(capture, 'page.json'), snapshot.result.value);
  const page = JSON.parse(snapshot.result.value);
  fs.writeFileSync(path.join(capture, 'outcome.json'), JSON.stringify({captureCompleted: true, mode, runtime, javaVersion: Number(javaVersion), wasmGC,
    probeReportedBlocked: page.proof?.events.some(event => event.message.includes(':blocked:')) || false,
    javaProgramEnded: page.proof?.ended || false,
    observedClientModes: [...new Set((page.proof?.samples || []).map(sample => sample.mode))],
    gameplayAcceptance: 'NOT_ACCEPTED', movementAcceptance: 'NOT_ACCEPTED', gpuRendererAcceptance: 'NOT_ACCEPTED',
    note: 'Exit 0 means capture completed; inspect compatibility results. A GAME state is not proof of visible world rendering.'}, null, 2));
  const image = await call('Page.captureScreenshot', {format: 'png'});
  fs.writeFileSync(path.join(capture, 'screenshot.png'), Buffer.from(image.data, 'base64'));
  fs.writeFileSync(path.join(capture, 'browser-version.json'), JSON.stringify(await call('Browser.getVersion'), null, 2));
  if (gameCapture) await gameCapture;
  console.log('Capture:', capture);
} finally {
  try { if (ws?.readyState === WebSocket.OPEN) await call('Browser.close'); } catch {}
  ws?.close(); log.end();
  // The PID belongs to the process created above, never an existing client/server.
  if (browser.exitCode === null) browser.kill('SIGTERM');
}
