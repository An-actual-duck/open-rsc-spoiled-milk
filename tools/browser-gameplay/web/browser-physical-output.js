/** GPU backing only. Java pixels, scene projection and input stay logical. */
export function physicalOutputRequested(mode,options){
  const value=options.get('physical');
  if(mode!=='webgl')return false;
  if(value!==null&&!['on','off'].includes(value))throw new Error('Invalid physical output policy');
  return options.has('benchmark')?value==='on':value!=='off';
}
export function physicalOutputSize(width,height,rect,dpr,maxTextureSize=4096){
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width*height>2097152||!Number.isInteger(maxTextureSize)||maxTextureSize<1)throw new Error('Invalid logical/physical output dimensions');
  const limit=Math.min(1920/width,1080/height,maxTextureSize/width,maxTextureSize/height,Math.sqrt(2097152/(width*height)));
  const valid=rect&&Number.isFinite(rect.width)&&rect.width>0&&Number.isFinite(rect.height)&&rect.height>0&&Number.isFinite(dpr)&&dpr>0;
  const requested=valid?Math.max(rect.width*dpr/width,rect.height*dpr/height):1;
  const scale=Math.min(limit,Math.max(1,Number.isFinite(requested)?requested:1));
  return {width:Math.max(1,Math.floor(width*scale)),height:Math.max(1,Math.floor(height*scale)),requestedScale:Number.isFinite(requested)?requested:1,capped:requested>limit};
}
/** Layout is measured only after display/resize/DPR invalidation, never every frame. */
export function createPhysicalOutputSizer(canvas,{enabled=false,window:win=globalThis.window,maxTextureSize=4096}={}){
  let dirty=true,rect=null,dpr=0,disposed=false;
  const invalidate=()=>{dirty=true;},observer=enabled&&typeof ResizeObserver!=='undefined'?new ResizeObserver(invalidate):null;
  if(enabled){observer?.observe(canvas);for(const event of ['resize','browser-display-change'])win?.addEventListener(event,invalidate);}
  function size(width,height){
    if(disposed)throw new Error('Physical output disposed');
    if(!enabled)return {width,height,requestedScale:1,capped:false};
    const nextDpr=win?.devicePixelRatio||1;if(nextDpr!==dpr){dpr=nextDpr;dirty=true;}
    if(dirty){rect=canvas.getBoundingClientRect?.()||null;dirty=false;}
    return {...physicalOutputSize(width,height,rect,dpr,maxTextureSize),cssWidth:rect?.width||0,cssHeight:rect?.height||0,devicePixelRatio:dpr};
  }
  return {enabled,size,dispose(){if(disposed)return;disposed=true;observer?.disconnect();if(enabled)for(const event of ['resize','browser-display-change'])win?.removeEventListener(event,invalidate);}};
}
