// The outer document owns lifecycle controls; vendor input handlers live in a
// separate disposable frame. Same-origin is needed for CheerpJ storage/files;
// this sandbox is lifecycle containment, not an untrusted-code security boundary.
import {createDisplayModes} from './browser-display-modes.js';
const start = document.querySelector('#start'), stop = document.querySelector('#stop');
const status = document.querySelector('#status'), session = document.querySelector('#session');
const welcome = document.querySelector('#welcome');
let frame;
window.browserProof = {events: [], lifecycle: 'stopped'};
const displayModes=createDisplayModes({window,document,player:document.querySelector('#player'),
  dialog:document.querySelector('#display-confirm'),confirm:document.querySelector('#display-enter'),
  cancel:document.querySelector('#display-cancel'),message:document.querySelector('#display-message'),
  onReturn:()=>{if(frame)frame.focus({preventScroll:true});},onChange:state=>{
    window.browserProof.presentation=state;
    frame?.contentWindow.dispatchEvent(new Event('browser-display-change'));
  }});
// Called only by the currently owned same-origin runtime. Old/discarded frames
// cannot request display changes, and no message may create a new session.
window.browserSessionDisplayRequest=(source,action)=>{
  if(!frame||source!==frame.contentWindow)return null;
  if(action===1||action===2)displayModes.request(action);
  return displayModes.stats;
};
function showRecovery(message){
  welcome.hidden=false;status.textContent=message;
  start.hidden=false;start.disabled=false;start.textContent='Restart game';
  stop.hidden=false;stop.disabled=false;stop.textContent='Close game';
}
function end(reason, focusStart = false) {
  if (!frame) return;
  const current = frame; frame = null;
  // Stop owns the parent receipt too. Child postMessage cleanup is queued and
  // must remain rejected once this frame is discarded; it cannot clear our
  // last structured clone synchronously. Preserve historical counts only.
  window.browserProof.stopped = true;
  if (window.browserProof.wholeRamJava) window.browserProof.wholeRamJava = {...window.browserProof.wholeRamJava, sourceReady:false, preparedReady:false, disposed:true};
  if (window.browserProof.wholeRamResidency) window.browserProof.wholeRamResidency = {...window.browserProof.wholeRamResidency, sourceReady:false, preparedReady:false, gpuReady:false, ready:false, disposed:true};
  if (window.browserProof.gpu?.wholeZone) window.browserProof.gpu.wholeZone = {...window.browserProof.gpu.wholeZone, rawSourceCPUReady:false, preparedCPUReady:false};
  // Best effort app transport cleanup before discarding all runtime listeners,
  // timers, workers and focus state belonging to the embedded document.
  try {
    if (current.contentWindow.browserSessionDispose) current.contentWindow.browserSessionDispose();
    else current.contentWindow.browserTransport?.dispose();
  } catch {}
  current.remove();
  displayModes.dismiss();
  void displayModes.exit();
  window.browserProof.lifecycle = 'stopped';
  window.browserProof.stopReason = reason;
  welcome.hidden=false;
  status.textContent = 'The game has closed. You can play again when ready.';
  start.textContent='Play again';start.hidden=false;start.disabled=false;
  stop.hidden=true;stop.disabled=true;
  if (focusStart && document.hasFocus()) start.focus({preventScroll: true});
}
start.addEventListener('click', () => {
  if(frame)end('Restarting game.');
  frame = document.createElement('iframe'); frame.id = 'runtime';
  frame.title = 'Spoiled Milk';
  frame.setAttribute('sandbox', 'allow-scripts allow-same-origin');
  frame.setAttribute('allow', "fullscreen 'none'; clipboard-read 'none'; clipboard-write 'none'; keyboard-map 'none'");
  frame.src = './runtime.html' + location.search;
  window.browserProof = {events: [], lifecycle: 'starting', focus: 'runtime',presentation:displayModes.stats};
  welcome.hidden=false;start.disabled=true;start.hidden=true;
  stop.disabled=false;stop.hidden=false;stop.textContent='Cancel';
  status.textContent='Starting game…';
  session.append(frame);
  // Start is an explicit user gesture. Transfer its focus into the new frame;
  // disabling the focused Start button otherwise creates a spurious blur.
  frame.focus({preventScroll: true});
});
stop.addEventListener('click', () => end('Game closed.', true));
window.addEventListener('message', event => {
  if (!frame || event.source !== frame.contentWindow || event.origin !== location.origin) return;
  if (event.data?.type === 'browser-session-proof') {
    Object.assign(window.browserProof, event.data.proof);
    window.browserProof.lifecycle = 'running';
    // Startup/recovery is transient. The game owns its login and initial world
    // loading cover; normal travel never restores the outer startup screen.
    const proof=event.data.proof;
    const fatal=(proof.gpu?.worldReadiness?.phase==='error'?'Area preparation failed.':null)||proof.gpu?.lastError||(proof.gpu?.contextLost?'Graphics connection lost.':null)||proof.wholeRamError;
    if(fatal)showRecovery('The game could not continue. You can restart or close it.');
    else if(proof.state?.mode==='LOGIN'||proof.state?.mode==='GAME')welcome.hidden=true;
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
