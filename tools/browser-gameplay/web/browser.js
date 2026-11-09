const started = performance.now();
import {createTransport} from './transport.js';
const status = document.querySelector('#status');
const options = new URLSearchParams(location.search);
const javaVersion = Number(options.get('java') || '8');
if (![8, 11, 17].includes(javaVersion)) throw new Error('Private probe supports Java 8, 11 or 17');
window.browserProof = {events: [], started, renderer: 'AWT software / direct framebuffer', mode: options.get('mode') || 'software', javaVersion, wasmGC: options.has('wasmgc'), runtimeVersion: window.browserRuntimeVersion};
let stopped = false;
let gpu;
function publish() {
  if (gpu) window.browserProof.gpu = gpu.stats;
  if (!stopped) parent.postMessage({type: 'browser-session-proof', proof: window.browserProof, status: status.textContent}, location.origin);
}
function finish(reason) {
  if (stopped) return;
  publish(); stopped = true; transport.dispose(); gpu?.dispose();
  document.activeElement?.blur?.();
  parent.postMessage({type: 'browser-session-stop', reason}, location.origin);
}
window.addEventListener('blur', () => setTimeout(() => {
  if (!document.hasFocus() && !stopped) parent.postMessage({type: 'browser-session-focus-left'}, location.origin);
}, 0));
document.addEventListener('visibilitychange', () => {
  if (document.hidden) finish('Stopped because the test tab was hidden.');
});
window.addEventListener('pagehide', () => { stopped = true; transport.dispose(); gpu?.dispose(); });
// Escape is an escape hatch, never captured by Java. Other browser shortcuts
// retain the vendor's documented default behavior (no overrideShortcuts).
window.addEventListener('keydown', event => {
  if (event.key === 'Escape') { event.stopImmediatePropagation(); finish('Stopped with Escape.'); }
}, true);
function report(message) {
  const event = {milliseconds: Math.round(performance.now() - started), message};
  window.browserProof.events.push(event); status.textContent = message; console.log('BROWSER_PROOF', JSON.stringify(event)); publish();
}
const transport = createTransport(WebSocket, report);
window.browserTransport = transport;
const natives = {
  async Java_orsc_browser_BrowserBootstrap_report(lib, message) { report(message); },
  async Java_orsc_browser_BrowserBootstrap_reportState(lib, mode, fps, loadingPercent) {
    const state = {milliseconds: Math.round(performance.now() - started), mode, fps, loadingPercent};
    if (window.browserProof.state?.mode !== mode) console.log('BROWSER_PROOF_STATE', JSON.stringify(state));
    window.browserProof.state = state;
    (window.browserProof.samples ||= []).push(state);
    if (window.browserProof.samples.length > 300) window.browserProof.samples.shift();
    status.textContent = `${window.browserProof.mode === 'webgl' ? 'Experimental WebGL client' : 'Software client'} · ${mode} · ${fps} reported Java loop frames/s · private loopback world`;
    publish();
  },
  async Java_orsc_browser_BrowserBootstrap_wsOpen() { return transport.open(); },
  async Java_orsc_browser_BrowserBootstrap_wsAvailable(lib, id) { return transport.available(id); },
  async Java_orsc_browser_BrowserBootstrap_wsRead(lib, id, maximum) { return transport.read(id, maximum); },
  async Java_orsc_browser_BrowserBootstrap_wsWrite(lib, id, bytes) { transport.write(id, bytes); },
  async Java_orsc_browser_BrowserBootstrap_wsClose(lib, id) { transport.close(id); },
};
try {
  if (window.browserProof.mode === 'webgl') {
    const {createBrowserWebGL} = await import('./webgl.js');
    const canvas = document.createElement('canvas'); canvas.id = 'browser-webgl';
    canvas.setAttribute('aria-hidden', 'true');
    Object.assign(canvas.style, {position: 'absolute', pointerEvents: 'none', display: 'none', zIndex: '1'});
    gpu = createBrowserWebGL(canvas, report);
    window.browserProof.renderer = 'WebGL2 resident world / software UI and entities (experimental)';
    Object.assign(natives, gpu.natives);
    const presentName = 'Java_orsc_BrowserWebGLBridge_present';
    const present = natives[presentName];
    if (typeof present !== 'function') throw new Error('WebGL bridge has no frame presenter');
    natives[presentName] = async (...args) => {
      const result = await present(...args);
      // AWT's canvas includes its titlebar. The game framebuffer is bottom
      // aligned within that canvas; keep pointer input on the original AWT.
      const display = document.querySelector('#display');
      const target = [...display.querySelectorAll('canvas')].filter(c => c !== canvas)
        .sort((a, b) => b.width * b.height - a.width * a.height)[0];
      if (target && canvas.parentElement) {
        const box = target.getBoundingClientRect(), parentBox = display.getBoundingClientRect();
        const sx = box.width / target.width, sy = box.height / target.height;
        const width = Number(args[1]), height = Number(args[2]);
        Object.assign(canvas.style, {
          display: 'block', left: (box.left - parentBox.left + (target.width - width) * sx / 2) + 'px',
          top: (box.top - parentBox.top + (target.height - height) * sy) + 'px',
          width: width * sx + 'px', height: height * sy + 'px',
        });
      }
      return result;
    };
  }
  await new Promise((resolve, reject) => {
    const script = document.createElement('script'); script.src = `https://cjrtnc.leaningtech.com/${window.browserRuntimeVersion}/loader.js`;
    script.onload = resolve; script.onerror = () => reject(new Error('Could not load browser Java runtime')); document.head.append(script);
  });
  await cheerpjInit({version: javaVersion, ...(options.has('wasmgc') ? {wasmGC: true} : {}), disableScaling: true,
    enableInputMethods: false, clipboardMode: 'java', overrideShortcuts: () => false,
    javaProperties: ['user.dir=/files'], natives});
  report('Browser Java ready');
  const display = document.querySelector('#display');
  cheerpjCreateDisplay(1000, 620, display);
  if (gpu) { display.style.position = 'relative'; display.append(gpu.canvas); }
  const code = await cheerpjRunMain('orsc.browser.BrowserBootstrap', '/app/browser-spike.jar:/app/client.jar', window.browserProof.mode);
  window.browserProof.ended = true; report('Java program stopped (exit ' + code + '); see console for compatibility results');
  finish('Java program stopped (exit ' + code + '). See console for compatibility results.');
} catch (error) { report('Bootstrap error: ' + error); console.error(error); finish('Bootstrap error: ' + error); }
