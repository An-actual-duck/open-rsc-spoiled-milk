// The outer document owns lifecycle controls; vendor input handlers live in a
// separate disposable frame. Same-origin is needed for CheerpJ storage/files;
// this sandbox is lifecycle containment, not an untrusted-code security boundary.
const start = document.querySelector('#start'), stop = document.querySelector('#stop');
const status = document.querySelector('#status'), session = document.querySelector('#session');
let frame;
window.browserProof = {events: [], lifecycle: 'stopped'};
function end(reason, focusStart = false) {
  if (!frame) return;
  const current = frame; frame = null;
  // Best effort app transport cleanup before discarding all runtime listeners,
  // timers, workers and focus state belonging to the embedded document.
  try {
    if (current.contentWindow.browserSessionDispose) current.contentWindow.browserSessionDispose();
    else current.contentWindow.browserTransport?.dispose();
  } catch {}
  current.remove();
  window.browserProof.lifecycle = 'stopped';
  window.browserProof.stopReason = reason;
  status.textContent = reason + '\nSession discarded. Choose Start to retry.';
  start.disabled = false; stop.disabled = true;
  if (focusStart && document.hasFocus()) start.focus({preventScroll: true});
}
start.addEventListener('click', () => {
  if (frame) return;
  frame = document.createElement('iframe'); frame.id = 'runtime';
  frame.title = 'Disposable private Java client';
  frame.setAttribute('sandbox', 'allow-scripts allow-same-origin');
  frame.setAttribute('allow', "fullscreen 'none'; clipboard-read 'none'; clipboard-write 'none'; keyboard-map 'none'");
  frame.src = './runtime.html' + location.search;
  window.browserProof = {events: [], lifecycle: 'starting', focus: 'runtime'};
  start.disabled = true; stop.disabled = false;
  status.textContent = 'Starting disposable browser Java session…';
  session.append(frame);
  // Start is an explicit user gesture. Transfer its focus into the new frame;
  // disabling the focused Start button otherwise creates a spurious blur.
  frame.focus({preventScroll: true});
});
stop.addEventListener('click', () => end('Stopped by user.', true));
window.addEventListener('message', event => {
  if (!frame || event.source !== frame.contentWindow || event.origin !== location.origin) return;
  if (event.data?.type === 'browser-session-proof') {
    Object.assign(window.browserProof, event.data.proof);
    window.browserProof.lifecycle = 'running';
    status.textContent = event.data.status;
  } else if (event.data?.type === 'browser-session-stop') end(String(event.data.reason));
  else if (event.data?.type === 'browser-session-focus-left') {
    // The runtime creates/focuses nested contexts during startup. Blur is not
    // a lifecycle boundary: destroying Java here also destroys valid startup.
    window.browserProof.focus = 'outside-runtime';
  }
});
// Input belongs to the focused embedded document. Merely moving focus (including
// to another app or our Stop button) must not discard a live game session.
window.addEventListener('blur', () => {
  if (frame) window.browserProof.focus = 'outside-runtime';
});
window.addEventListener('focus', () => {
  if (frame) window.browserProof.focus = 'page';
});
document.addEventListener('visibilitychange', () => {
  window.browserProof.visibility = document.visibilityState;
});
window.addEventListener('pagehide', () => end('Stopped because the page closed.'));
