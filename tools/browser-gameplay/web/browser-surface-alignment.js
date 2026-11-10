/** Align the GPU/cover surface with the unchanged AWT input canvas.
 * Standalone/ordinary default remains the literal live-measurement control. */
export function surfaceAlignmentPolicy(mode,options) {
  if(mode!=='webgl'||!options.has('benchmark'))return false;
  const value=options.get('alignment');
  if(value!==null&&value!=='on'&&value!=='off')throw new Error('alignment must be on or off');
  return value==='on';
}
export function createSurfaceAlignment({window:win,document:doc,display,canvas,layout=()=>null,cached=false,control=false}) {
  const Resize=win.ResizeObserver||globalThis.ResizeObserver,Mutation=win.MutationObserver||globalThis.MutationObserver;
  const supported=typeof Resize==='function'&&typeof Mutation==='function'&&typeof doc.getAnimations==='function';
  const stats={requested:cached,active:cached&&supported,observerSupport:supported,alignments:0,cacheHits:0,targetScans:0,layoutReads:0,styleCommits:0,invalidations:0,policyTransitions:0};
  let dirty=true,disposed=false,width=0,height=0,dpr=0,target=null,pending=null,animationFallback=false,lastStyle=null,initialAnimations=[],observing=false;
  const listeners=[];
  function invalidate(){if(!disposed){dirty=true;stats.invalidations++;}}
  function listen(owner,type,callback,options){owner?.addEventListener?.(type,callback,options);listeners.push([owner,type,callback,options]);}
  function isOwned(record){
    const node=record.target;
    if(node===canvas)return record.attributeName==='style'&&['display','left','top','width','height'].every(key=>canvas.style[key]===lastStyle?.[key]);
    return false;
  }
  const resize=supported?new Resize(invalidate):null;
  const mutation=supported?new Mutation(records=>{
    for(const record of records){
      // Backing pixel dimensions change independently of logical CSS bounds.
      if(record.target===canvas&&['width','height'].includes(record.attributeName))continue;
      if(!isOwned(record)){invalidate();break;}
    }
  }):null;
  function startObserving(){
    if(!supported||observing)return;observing=true;
    resize.observe(display);const viewport=doc.querySelector?.('#viewport');if(viewport)resize.observe(viewport);if(target)resize.observe(target);
    mutation.observe(doc.documentElement||display,{subtree:true,childList:true,attributes:true,characterData:true});
    for(const event of ['resize','browser-display-change','scroll'])listen(win,event,invalidate,true);
    for(const event of ['fullscreenchange','load'])listen(doc,event,invalidate,true);
    for(const event of ['loadingdone','loadingerror'])listen(doc.fonts,event,invalidate);
    doc.fonts?.ready?.then(()=>{if(stats.active)invalidate();},()=>{if(stats.active)invalidate();});
    const motion=()=>{refreshAnimations();invalidate();};
    for(const event of ['transitionrun','transitionend','transitioncancel','animationstart','animationend','animationcancel'])listen(doc,event,motion,true);
    refreshAnimations();
  }
  function refreshAnimations(){
    // Use actual animation identity/state rather than counting unmatched start/
    // end events. An old animation ending cannot hide newer overlapping motion.
    try {initialAnimations=doc.getAnimations();if(initialAnimations.length>64){initialAnimations=[];animationFallback=true;}}
    catch(error){initialAnimations=[];animationFallback=true;}
  }
  function stopObserving(){
    resize?.disconnect();mutation?.disconnect();observing=false;
    for(const [owner,type,callback,options]of listeners)owner?.removeEventListener?.(type,callback,options);
    listeners.length=0;initialAnimations=[];animationFallback=false;
  }
  if(stats.active)startObserving();
  function chooseTarget(){
    stats.targetScans++;
    const selected=[...display.querySelectorAll('canvas')].filter(element=>element!==canvas).sort((a,b)=>b.width*b.height-a.width*a.height)[0];
    if(selected!==target&&resize&&observing){if(target)resize.unobserve(target);if(selected)resize.observe(selected);}
    target=selected;return selected;
  }
  function align(w,h){
    if(disposed)throw new Error('Surface alignment disposed');
    stats.alignments++;
    if(pending!==null){stats.requested=pending;stats.active=pending&&supported;pending=null;stats.policyTransitions++;dirty=true;if(stats.active)startObserving();else stopObserving();}
    const nextDpr=win.devicePixelRatio||1;
    if(w!==width||h!==height||nextDpr!==dpr){width=w;height=h;dpr=nextDpr;dirty=true;}
    const initiallyAnimating=initialAnimations.length>0&&initialAnimations.some(animation=>animation.playState==='running'||animation.pending);
    if(!initiallyAnimating)initialAnimations=[];
    if(target?.isConnected===false)dirty=true;
    if(stats.active&&!dirty&&!animationFallback&&!initiallyAnimating){stats.cacheHits++;return false;}
    // This is the original alignment expression/order; no alternate sizing,
    // projection, input scaling or pixel rounding is introduced.
    const selected=chooseTarget();
    const currentLayout=layout();currentLayout?.setSize(w,h);
    if(selected&&canvas.parentElement){
      const box=selected.getBoundingClientRect(),parentBox=display.getBoundingClientRect(),scale=currentLayout?.scale||1;
      stats.layoutReads+=2;
      const sx=box.width/selected.width/scale,sy=box.height/selected.height/scale;
      lastStyle={display:'block',left:((box.left-parentBox.left)/scale+(selected.width-w)*sx/2)+'px',top:((box.top-parentBox.top)/scale+(selected.height-h)*sy)+'px',width:w*sx+'px',height:h*sy+'px'};
      Object.assign(canvas.style,lastStyle);stats.styleCommits++;
    }
    // Missing AWT target must be retried even before mutation delivery.
    dirty=!selected||!canvas.parentElement;return true;
  }
  function queueCached(enabled){
    if(disposed||!control||typeof enabled!=='boolean')throw new Error('Surface alignment control is private benchmark only');
    pending=enabled;
  }
  function dispose(){
    if(disposed)return;disposed=true;stopObserving();
    target=null;lastStyle=null;
  }
  return {stats,align,invalidate,queueCached,dispose};
}
