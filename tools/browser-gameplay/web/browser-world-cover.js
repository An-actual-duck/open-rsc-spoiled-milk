import {worldPreparationMessage} from './browser-world-preparation.js';
/** DOM cover and input boundary; does not stop Java updates or GPU presentation. */
export function createWorldCover({document,window,display,onGateChange=()=>{},now=()=>performance.now()}) {
  const cover=document.createElement('div');cover.id='browser-world-loading';cover.setAttribute('role','status');cover.setAttribute('aria-live','polite');
  Object.assign(cover.style,{position:'absolute',display:'none',zIndex:'2',background:'#101820',color:'#eef4f7',alignItems:'center',justifyContent:'center',flexDirection:'column',font:'16px system-ui',textAlign:'center',pointerEvents:'auto'});
  const title=document.createElement('strong'),detail=document.createElement('div');detail.style.marginTop='12px';cover.append(title,detail);display.append(cover);
  const meter=document.createElement('progress'),elapsed=document.createElement('div'),help=document.createElement('div');
  meter.style.marginTop='16px';meter.style.width='min(360px,80%)';meter.setAttribute('aria-label','Current world preparation stage');
  elapsed.style.marginTop='10px';elapsed.style.fontSize=help.style.fontSize='13px';help.style.marginTop='8px';help.style.maxWidth='400px';cover.append(meter,elapsed,help);meter.hidden=elapsed.hidden=help.hidden=true;
  const stats={blocked:false,transitions:0,blockedPointerEvents:0,blockedKeydowns:0};
  let blocked=false,keyboardBlocked=false,disposed=false,started=null,timer=null;
  function tick(){if(started===null||disposed)return;const seconds=Math.max(0,Math.floor((now()-started)/1000));elapsed.textContent='Elapsed: '+seconds+' seconds';help.hidden=seconds<30;help.textContent='The first world load can take longer on slower devices. Keep this tab open while preparation finishes.';}
  function stopTimer(){if(timer!==null){window.clearInterval?.(timer);timer=null;}started=null;}

  function within(event){const point=event.changedTouches?.[0]||event.touches?.[0]||event;const r=cover.getBoundingClientRect();return point.clientX>=r.left&&point.clientX<r.right&&point.clientY>=r.top&&point.clientY<r.bottom;}
  function gate(event){
    if(!blocked||disposed)return;
    if(event.type==='keydown') {
      if(!keyboardBlocked)return;
      // This boundary is game input, never browser/window shortcut ownership.
      if(event.ctrlKey||event.metaKey||event.altKey)return;
      if(!(event.key?.length===1||/^Arrow(Up|Down|Left|Right)$/.test(event.key||'')))return;
      if(!display.contains(event.target)&&!display.contains(document.activeElement))return;
    }else if(!within(event))return;
    if(event.type==='keydown')stats.blockedKeydowns++;else stats.blockedPointerEvents++;
    event.preventDefault();event.stopImmediatePropagation();
  }
  const types=['pointerdown','mousedown','click','dblclick','contextmenu','wheel','touchstart','keydown'];
  for(const type of types)document.addEventListener(type,gate,{capture:true,passive:false});
  function update(state,style,error,preparation){
    if(disposed)return;
    if(style)for(const name of ['left','top','width','height'])cover.style[name]=style[name];
    const phase=error?'error':state?.phase||'login',streaming=phase==='streaming',next=phase==='loading'||phase==='error'||streaming;
    keyboardBlocked=next&&(!streaming||state?.blocksKeyboard!==false);
    const heading=phase==='error'?'Unable to load the world':streaming?'Updating area…':'Loading world…';if(title.textContent!==heading)title.textContent=heading;
    const message=phase==='error'?(preparation?.failureCode?worldPreparationMessage(preparation,state):'The area could not be prepared. Restart the game to retry.'):phase==='loading'?worldPreparationMessage(preparation,state):state?.required>state?.uploaded?`Preparing terrain and objects (${state.uploaded} / ${state.required})`:'Preparing nearby area';if(detail.textContent!==message)detail.textContent=message;
    meter.hidden=phase!=='loading';elapsed.hidden=phase!=='loading';help.hidden=true;
    if(phase==='loading'){
      if(started===null){started=now();if(typeof window.setInterval==='function')timer=window.setInterval(tick,1000);}
      tick();
      const count=preparation&&preparation.stage<6&&preparation.total>0?preparation:null;
      if(count){meter.max=count.total;meter.value=count.completed;}else meter.removeAttribute?.('value');
    }else stopTimer();
    Object.assign(cover.style,{background:streaming?'transparent':'#101820',pointerEvents:streaming?'none':'auto',justifyContent:streaming?'flex-end':'center',font:streaming?'12px system-ui':'16px system-ui'});
    title.style.display=streaming?'none':'';detail.style.background=streaming?'#101820cc':'';detail.style.padding=streaming?'4px 8px':'';
    cover.style.display=next?'flex':'none';cover.setAttribute('aria-busy',next?'true':'false');
    if(next!==blocked){blocked=next;stats.blocked=next;stats.transitions++;onGateChange();}
  }
  function attach(){if(cover.parentElement!==display)display.append(cover);}
  function dispose(){if(disposed)return;disposed=true;stopTimer();for(const type of types)document.removeEventListener(type,gate,true);cover.remove();if(blocked){blocked=false;stats.blocked=false;stats.transitions++;onGateChange();}}
  return {stats,update,attach,dispose,get blocksWorldInput(){return blocked;}};
}
