// CheerpJ5 input drops buttons>2; DOM middle uses buttons=4. Relay ONLY this
// gesture into ordinary AWT BUTTON2; never patch vendor code or page-wide input.
export function createBrowserMiddleMouseInput(win = window, doc = document, {logicalExtent=null}={}) {
  const queue = [], listeners = [], stats = {presses:0, moves:0, releases:0, cancels:0, coalesced:0};
  let waiting, active, stopped = false;
  const allowed = () => typeof win.browserWorldInputAllowed !== 'function' || win.browserWorldInputAllowed();
  function push(packet) {
    if (waiting) { const resolve = waiting; waiting = undefined; resolve(Int32Array.from(packet)); return; }
    if (packet[0] === 2 && queue.at(-1)?.[0] === 2) { queue[queue.length-1] = packet; stats.coalesced++; }
    else if (queue.length < 64) queue.push(packet);
    else { queue.length = 0; queue.push([0,0,0,0]); releaseCapture(); stats.cancels++; }
  }
  function viewport() {
    const canvas = doc.querySelector('#browser-webgl');
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    if (!(rect.width > 0 && rect.height > 0 && canvas.width > 0 && canvas.height > 0)) return;
    const logical=logicalExtent?logicalExtent():canvas;
    if(!logical||!Number.isInteger(logical.width)||!Number.isInteger(logical.height)||logical.width<1||logical.height<1||logical.width*logical.height>2097152)return;
    return {canvas, rect, width:logical.width, height:logical.height};
  }
  function point(event, area) {
    const x = Math.round((event.clientX-area.rect.left)*area.width/area.rect.width);
    const y = Math.round((event.clientY-area.rect.top)*area.height/area.rect.height);
    if (!Number.isFinite(x) || !Number.isFinite(y) || Math.abs(x)>65536 || Math.abs(y)>65536) return;
    return [x,y,(event.shiftKey?1:0)|(event.ctrlKey?2:0)|(event.altKey?4:0)|(event.metaKey?8:0)];
  }
  function swallow(event) { event.preventDefault(); event.stopImmediatePropagation(); }
  function releaseCapture() {
    const old = active; active = undefined;
    try { old?.target.releasePointerCapture?.(old.id); } catch {}
  }
  function cancel() {
    if (!active) return;
    releaseCapture(); stats.cancels++; push([0,0,0,0]);
  }
  function down(event) {
    if (stopped || event.button !== 1 || !allowed()) return;
    const area = viewport(); if (!area) return;
    const r=area.rect;
    if (event.clientX<r.left || event.clientX>=r.right || event.clientY<r.top || event.clientY>=r.bottom) return;
    const p=point(event,area); if (!p) return;
    swallow(event); cancel();
    active={id:event.pointerId,target:event.target};
    try { event.target.setPointerCapture?.(event.pointerId); } catch {}
    stats.presses++; stats.lastPressButton=event.button; stats.lastPressButtons=event.buttons; push([1,...p]);
  }
  function move(event) {
    if (!active || event.pointerId !== active.id) return;
    swallow(event);
    if (!allowed() || !(event.buttons&4)) { cancel(); return; }
    const area=viewport(), p=area && point(event,area);
    if (!p) { cancel(); return; }
    stats.moves++; push([2,...p]);
  }
  function up(event) {
    if (!active || event.pointerId !== active.id || event.button !== 1) return;
    swallow(event);
    const area=viewport(), p=area && point(event,area);
    releaseCapture(); stats.releases++; push(p ? [3,...p] : [0,0,0,0]);
  }
  function compatibility(event) {
    // Suppress browser autoscroll/paste and compatibility duplicates, not left/right clicks.
    if (event.button !== 1) return;
    const area=viewport(), r=area?.rect;
    if (active || (r && event.clientX>=r.left && event.clientX<r.right && event.clientY>=r.top && event.clientY<r.bottom)) swallow(event);
  }
  function listen(target,name,handler) { target.addEventListener(name,handler,{capture:true,passive:false}); listeners.push([target,name,handler]); }
  listen(win,'pointerdown',down); listen(win,'pointermove',move); listen(win,'pointerup',up);
  const lost = event => { if(active && event.pointerId===active.id)cancel(); };
  listen(win,'pointercancel',lost); listen(win,'lostpointercapture',lost);
  for (const name of ['mousedown','mouseup','auxclick','click']) listen(win,name,compatibility);
  listen(win,'blur',cancel);
  listen(doc,'visibilitychange',()=>{ if(doc.visibilityState==='hidden')cancel(); });
  listen(win,'browser-world-readinesschange',()=>{ if(!allowed())cancel(); });
  listen(win,'browser-display-change',cancel);
  return {
    stats,
    natives: { async Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent() {
      if (stopped) return Int32Array.from([-1,0,0,0]);
      if (queue.length) return Int32Array.from(queue.shift());
      if (waiting) throw new Error('Only one browser middle-input consumer is allowed');
      return new Promise(resolve=>{waiting=resolve;});
    } },
    dispose() {
      if(stopped)return;
      cancel(); stopped=true;
      for(const [target,name,handler] of listeners)target.removeEventListener(name,handler,true);
      queue.length=0;
      if(waiting){const resolve=waiting;waiting=undefined;resolve(Int32Array.from([-1,0,0,0]));}
    },
  };
}
