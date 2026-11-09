/** DOM cover and input boundary; does not stop Java updates or GPU presentation. */
export function createWorldCover({document,window,display,onGateChange=()=>{}}) {
  const cover=document.createElement('div');cover.id='browser-world-loading';cover.setAttribute('role','status');cover.setAttribute('aria-live','polite');
  Object.assign(cover.style,{position:'absolute',display:'none',zIndex:'2',background:'#101820',color:'#eef4f7',alignItems:'center',justifyContent:'center',flexDirection:'column',font:'16px system-ui',textAlign:'center',pointerEvents:'auto'});
  const title=document.createElement('strong'),detail=document.createElement('div');detail.style.marginTop='12px';cover.append(title,detail);display.append(cover);
  const stats={blocked:false,transitions:0,blockedPointerEvents:0,blockedKeydowns:0};
  let blocked=false,disposed=false;
  function within(event){const point=event.changedTouches?.[0]||event.touches?.[0]||event;const r=cover.getBoundingClientRect();return point.clientX>=r.left&&point.clientX<r.right&&point.clientY>=r.top&&point.clientY<r.bottom;}
  function gate(event){
    if(!blocked||disposed)return;
    if(event.type==='keydown') {
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
  function update(state,style,error){
    if(disposed)return;
    if(style)for(const name of ['left','top','width','height'])cover.style[name]=style[name];
    const phase=error?'error':state?.phase||'login',next=phase==='loading'||phase==='error';
    const heading=phase==='error'?'Unable to load the world':'Loading world…';if(title.textContent!==heading)title.textContent=heading;
    const message=phase==='error'?(error||state?.reason||'Graphics could not finish loading.')+' Stop and restart the private test to retry.':state?.required>state?.uploaded?`Preparing terrain and objects (${state.uploaded} / ${state.required})`:state?.reason||'Preparing graphics';if(detail.textContent!==message)detail.textContent=message;
    cover.style.display=next?'flex':'none';cover.setAttribute('aria-busy',next?'true':'false');
    if(next!==blocked){blocked=next;stats.blocked=next;stats.transitions++;onGateChange();}
  }
  function attach(){if(cover.parentElement!==display)display.append(cover);}
  function dispose(){disposed=true;for(const type of types)document.removeEventListener(type,gate,true);cover.remove();if(blocked){blocked=false;stats.blocked=false;stats.transitions++;onGateChange();}}
  return {stats,update,attach,dispose,get blocksWorldInput(){return blocked;}};
}
