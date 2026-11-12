import {createOverlayStaging} from './browser-overlay-staging.js';
import {withOverlayUnpack} from './browser-overlay-unpack.js';
import {validateOverlayDelta} from './browser-overlay-delta.js';
import {createBrowserRetainedFrame} from './browser-retained-frame.js';
/** Optional exact-coverage compositor. RGB is the ordinary software result over
 * black; transmittance is the surviving world coefficient. No world readback. */
export function createBrowserOverlay(gl,{stats={},ownedFrame=null,deferOrCheck=null,retainOpaqueFull=false,deferRetainedReset=false,physicalOutput=false,deltaTransport=false,overlayRectMode='off',measurePacking=false,maxTextureSize=0}={}) {
  if(!['off','observe','on','packed'].includes(overlayRectMode)||overlayRectMode!=='off'&&!deltaTransport)throw new Error('Invalid overlay rectangle transport mode');
  const rectEnabled=['on','packed'].includes(overlayRectMode);
  const staging=overlayRectMode==='packed'?createOverlayStaging({stats,measure:measurePacking===true}):null;
  const MAX_PIXELS=2097152, UNIT=8;
  const retained=createBrowserRetainedFrame(gl,{stats,ownedFrame,reserveBytes:extra=>reserveCombined(extra)});
  let outputWidth=0,outputHeight=0;
  let deltaHistory=null;
  let deferredReset=false;
  const deltaMaxTextureSize=maxTextureSize||ownedFrame?.maxTextureSize||0;
  Object.assign(stats,{overlayDeltaTransportEnabled:deltaTransport===true,overlayDeltaRectDeclarations:0,overlayDeltaDeclarations:0,overlayDeltaPartialDeclarations:0,overlayDeltaFullDeclarations:0,overlayDeltaPresentedFrames:0,overlayDeltaUploadedBytes:0,overlayDeltaFullEquivalentBytes:0,overlayDeltaUploadCalls:0,overlayUploadBytes:0,overlayUploadCalls:0,overlaySourceViewSpanBytes:0,overlayDeltaSourceViewSpanBytes:0});

  let program,vertex,fragment,vao,rgb,coverage,width=0,height=0,tWidth=0,tHeight=0,pending,active,failed=false,disposed=false;
  Object.assign(stats,{overlayCoverageBytes:0,overlayRgbBytes:0,overlayCoverageFrames:0,overlayCompositePeakBudgetBytes:(physicalOutput?48:32)*1024*1024,overlayCompositePeakBytes:0,overlayCompositeResourceBytes:0});
  function check(){const e=gl.getError();if(e!==gl.NO_ERROR)throw new Error('Coverage GL error 0x'+e.toString(16));}
  function dimensions(w,h){if(!Number.isInteger(w)||!Number.isInteger(h)||w<1||h<1||w*h>MAX_PIXELS||physicalOutput&&(w>32768||h>32768)||w>(ownedFrame?.active?ownedFrame.maxTextureSize:gl.getParameter(gl.MAX_TEXTURE_SIZE))||h>(ownedFrame?.active?ownedFrame.maxTextureSize:gl.getParameter(gl.MAX_TEXTURE_SIZE)))throw new Error('Coverage dimensions exceed bounds');}
  function alive(){if(failed||disposed)throw new Error('Coverage backend unavailable; restart session');}
  function shader(type,source){const s=gl.createShader(type);if(!s)throw new Error('Coverage shader allocation failed');try{gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s));return s;}catch(e){gl.deleteShader(s);throw e;}}
  function initialize(){alive();if(program)return;try{
    vertex=shader(gl.VERTEX_SHADER,'#version 300 es\nprecision highp float;out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0,1);}');
    fragment=shader(gl.FRAGMENT_SHADER,physicalOutput?'#version 300 es\nprecision highp float;precision highp int;uniform sampler2D image;uniform sampler2D transmittance;uniform bool transparent;uniform vec2 logicalSize;uniform vec2 outputSize;out vec4 color;void main(){ivec2 dst=ivec2(gl_FragCoord.xy),extent=ivec2(outputSize),source=ivec2(logicalSize);ivec2 at=ivec2(((2*dst.x+1)*source.x)/(2*extent.x),((2*(extent.y-1-dst.y)+1)*source.y)/(2*extent.y));vec3 rgb=texelFetch(image,at,0).bgr;float t=transparent?clamp(texelFetch(transmittance,at,0).r,0.0,1.0):0.0;color=vec4(rgb,1.0-t);}':'#version 300 es\nprecision highp float;uniform sampler2D image;uniform sampler2D transmittance;uniform bool transparent;in vec2 uv;out vec4 color;void main(){vec3 rgb=texture(image,uv).bgr;float t=transparent?clamp(texture(transmittance,uv).r,0.0,1.0):0.0;color=vec4(rgb,1.0-t);}');
    program=gl.createProgram();if(!program)throw new Error('Coverage program allocation failed');gl.attachShader(program,vertex);gl.attachShader(program,fragment);gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));vao=gl.createVertexArray();if(!vao)throw new Error('Coverage VAO allocation failed');check();
  }catch(e){failed=true;throw e;}}
  function resourceBytes(){return stats.overlayRgbBytes+stats.overlayCoverageBytes+stats.retainedResourceBytes;}
  function reserveCombined(extra){const peak=resourceBytes()+extra;if(peak>stats.overlayCompositePeakBudgetBytes)throw new Error('Coverage combined texture peak budget exceeded');stats.overlayCompositePeakBytes=Math.max(stats.overlayCompositePeakBytes,peak);}
  function account(){stats.overlayCompositeResourceBytes=resourceBytes();reserveCombined(0);}
  function textureParameters(){for(const p of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,p,gl.NEAREST);for(const p of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,p,gl.CLAMP_TO_EDGE);}
  let unpackScopeActive=false;
  function scopedUpload(w,upload){
    return withOverlayUnpack(gl,w,ownedFrame,()=>{unpackScopeActive=true;try{return upload();}finally{unpackScopeActive=false;}});
  }
  function textureUpload(old,oldW,oldH,w,h,format,type,data,unit,bands=null,rectangles=null,packedOffsets=null){
    if(bands&&bands.length===0||rectangles&&rectangles.length===0)return old;
    if(rectEnabled&&!unpackScopeActive)return scopedUpload(w,()=>textureUpload(old,oldW,oldH,w,h,format,type,data,unit,bands,rectangles,packedOffsets));
    const previousActive=ownedFrame?.active?null:gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0+unit);let restore=ownedFrame?.active?null:gl.getParameter(gl.TEXTURE_BINDING_2D),next=old,committed=false;
    try{if(!next||oldW!==w||oldH!==h){reserveCombined(w*h*4);next=gl.createTexture();if(!next)throw new Error('Coverage texture allocation failed');}gl.bindTexture(gl.TEXTURE_2D,next);textureParameters();gl.pixelStorei(gl.UNPACK_ALIGNMENT,4);
      if(next===old){
        if(rectangles){const channels=type===gl.UNSIGNED_BYTE?4:1;for(let n=0;n<rectangles.length;n+=4){const x=rectangles[n],y=rectangles[n+1],rw=rectangles[n+2],rh=rectangles[n+3];const from=(packedOffsets?packedOffsets[n/4]:y*w+x)*channels,to=from+(packedOffsets?rw*rh:(rh-1)*w+rw)*channels;gl.texSubImage2D(gl.TEXTURE_2D,0,x,y,rw,rh,format===gl.R32F?gl.RED:gl.RGBA,type,data.subarray(from,to),0);}}
        else if(bands){const channels=type===gl.UNSIGNED_BYTE?4:1;for(let n=0;n<bands.length;n+=2){const y=bands[n],end=bands[n+1];gl.texSubImage2D(gl.TEXTURE_2D,0,0,y,w,end-y,format===gl.R32F?gl.RED:gl.RGBA,type,data.subarray(y*w*channels,end*w*channels));}}
        else gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,w,h,format===gl.R32F?gl.RED:gl.RGBA,type,data);
      }else gl.texImage2D(gl.TEXTURE_2D,0,format,w,h,0,format===gl.R32F?gl.RED:gl.RGBA,type,data);
      if(next===old&&format===gl.R32F&&deferOrCheck)deferOrCheck(check);else check();committed=true;
      let submittedRows=h;if(bands){submittedRows=0;for(let n=0;n<bands.length;n+=2)submittedRows+=bands[n+1]-bands[n];}
      let submittedPixels=submittedRows*w;if(rectangles){submittedPixels=0;for(let n=0;n<rectangles.length;n+=4)submittedPixels+=rectangles[n+2]*rectangles[n+3];}
      let sourceBytes=submittedPixels*4;if(rectangles&&!packedOffsets){sourceBytes=0;for(let n=0;n<rectangles.length;n+=4)sourceBytes+=((rectangles[n+3]-1)*w+rectangles[n+2])*4;}
      stats.overlaySourceViewSpanBytes+=sourceBytes;
      stats.overlayUploadBytes+=submittedPixels*4;stats.overlayUploadCalls+=rectangles?rectangles.length/4:bands?bands.length/2:1;
      if(next!==old&&old){if(restore===old)restore=next;gl.deleteTexture(old);}return next;
    }catch(e){if(next===old)failed=true;throw e;}finally{if(!ownedFrame?.active){gl.bindTexture(gl.TEXTURE_2D,restore);gl.activeTexture(previousActive);}else gl.activeTexture(gl.TEXTURE0);if(!committed&&next&&next!==old)gl.deleteTexture(next);}
  }
  function invalidateDeltaHistory(){deltaHistory=null;}
  function prepareDelta(w,h,generation,frameSequence,reset,partial,baseGeneration,baseFrameSequence,bands,work,bytes,transmittance){
    alive();if(!deltaTransport)throw new Error('Overlay delta transport is unavailable');
    if(pending||active)throw new Error('Overlay delta declaration was not consumed');
    // Validate both complete borrowed planes and ALL small metadata before any
    // graphics query, allocation, upload or mutation. Only metadata is retained.
    const plan=validateOverlayDelta({width:w,height:h,generation,frameSequence,reset,partial,baseGeneration,baseFrameSequence,bands,work,rgb:bytes,transmittance},deltaHistory,
      !!rgb&&!!coverage&&width===w&&height===h&&tWidth===w&&tHeight===h,deltaMaxTextureSize,overlayRectMode);
    retained.validateCaptureToken(plan.token,{willReset:reset||width!==w||height!==h});
    try{
      const packed=staging&&plan.rectangles?staging.pack(plan,bytes,transmittance):null;
      dimensions(w,h);
      if(reset||width!==w||height!==h)retained.reset();
      const selected=plan.partial&&!plan.rectangles?plan.bands:null;
      const upload=()=>{
        coverage=textureUpload(coverage,tWidth,tHeight,w,h,gl.R32F,gl.FLOAT,packed?packed.transmittance:transmittance,UNIT,selected,plan.rectangles,packed?.offsets);tWidth=w;tHeight=h;stats.overlayCoverageBytes=w*h*4;
        rgb=textureUpload(rgb,width,height,w,h,gl.RGBA8,gl.UNSIGNED_BYTE,packed?packed.rgb:bytes,0,selected,plan.rectangles,packed?.offsets);width=w;height=h;stats.overlayRgbBytes=w*h*4;
      };
      if(rectEnabled&&plan.area>0)scopedUpload(packed?0:w,upload);else upload();
      account();
      pending={w,h,mode:1,token:plan.token,fades:0,delta:plan};
      stats.overlayDeclaration={width:w,height:h,mode:1,generation,frameSequence,fades:0,reset};
      stats.overlayDeltaDeclarations++;stats[plan.partial?'overlayDeltaPartialDeclarations':'overlayDeltaFullDeclarations']++;
      let sourceBytes=plan.area*8;if(plan.rectangles&&!packed){sourceBytes=0;for(let n=0;n<plan.rectangles.length;n+=4)sourceBytes+=((plan.rectangles[n+3]-1)*w+plan.rectangles[n+2])*8;}
      stats.overlayDeltaSourceViewSpanBytes+=sourceBytes;
      stats.overlayDeltaUploadedBytes+=plan.area*8;stats.overlayDeltaFullEquivalentBytes+=w*h*8;stats.overlayDeltaUploadCalls+=plan.rectangles?plan.rectangles.length/2:plan.bands.length;
      if(plan.rectangles)stats.overlayDeltaRectDeclarations++;
      stats.overlayDeltaLast={transport:plan.rectangles?'rectangle':plan.partial?'row':'full',rectangleMode:overlayRectMode,tileObservation:plan.tileObservation,partial:plan.partial,bands:plan.rectangles?[]:plan.bands.slice(),rectangles:plan.rectangles?.slice()||[],uploadedPixels:plan.area,newBlocks:plan.work[0],spanMarks:plan.work[1],touchedRows:plan.work[2],uploadedRows:plan.rectangles?null:plan.rows,bandCount:plan.rectangles?0:plan.bands.length/2,rectangleCount:plan.rectangles?plan.rectangles.length/4:0,sourceViewSpanBytes:sourceBytes,logicalTexelBytes:plan.area*8,bytes:plan.area*8};
    }catch(error){failed=true;deltaHistory=null;throw error;}
  }
  function prepare(w,h,mode,generation,frameSequence,fades,reset,transmittance){
    if(deltaTransport)invalidateDeltaHistory();
    alive();dimensions(w,h);if(pending)throw new Error('Coverage declaration was not consumed');
    if(![0,1,2,3].includes(mode)||!Number.isSafeInteger(generation)||generation<0||!Number.isSafeInteger(frameSequence)||frameSequence<0||!Number.isInteger(fades)||fades<0||fades>16||(mode===3?fades<1:fades!==0))throw new Error('Invalid coverage mode/token');
    if(mode!==0&&(!(transmittance instanceof Float32Array)||transmittance.length!==w*h))throw new Error('Invalid coverage transmittance plane');
    // A resized retained FULL cannot define the new viewport; release it BEFORE C/T replacements.
    if(reset||width!==w||height!==h){if(deferRetainedReset&&mode===1)deferredReset=true;else retained.reset();}
    if(mode!==0){coverage=textureUpload(coverage,tWidth,tHeight,w,h,gl.R32F,gl.FLOAT,transmittance,UNIT);tWidth=w;tHeight=h;stats.overlayCoverageBytes=w*h*4;}
    account();stats.overlayDeclaration={width:w,height:h,mode,generation,frameSequence,fades,reset};pending={w,h,mode,token:{generation,frameSequence},fades};
  }
  /** Called after parent clear, before any new world draw. */
  function outputSize(w,h,desiredWidth=w,desiredHeight=h){
    alive();if(!pending||pending.w!==w||pending.h!==h)throw new Error('Missing/current output declaration');
    dimensions(desiredWidth,desiredHeight);
    if(!physicalOutput&&(desiredWidth!==w||desiredHeight!==h))throw new Error('Physical output is disabled');
    if((pending.mode===2||pending.mode===3)&&outputWidth&&outputHeight)return {width:outputWidth,height:outputHeight,deferred:desiredWidth!==outputWidth||desiredHeight!==outputHeight};
    return {width:desiredWidth,height:desiredHeight,deferred:false};
  }
  function begin(w,h,outWidth=w,outHeight=h){alive();if(!pending||pending.w!==w||pending.h!==h)throw new Error('Missing/current coverage declaration');if(physicalOutput)dimensions(outWidth,outHeight);if(!physicalOutput&&(outWidth!==w||outHeight!==h))throw new Error('Physical output is disabled');if(deferredReset){retained.reset();deferredReset=false;}active=pending;active.outputWidth=outWidth;active.outputHeight=outHeight;pending=null;
    outputWidth=outWidth;outputHeight=outHeight;
    try{if(active.mode===3)retained.drawFaded(outWidth,outHeight,active.fades,active.token);else if(active.mode===2)retained.drawBase(outWidth,outHeight,active.token);}catch(e){failed=true;throw e;}
    return active.mode===2||active.mode===3;
  }
  function draw(w,h,bytes){
    alive();if(!active||active.w!==w||active.h!==h||!(bytes instanceof Uint8Array)||bytes.length!==w*h*4)throw new Error('Coverage RGB frame mismatch');initialize();
    if(!active.delta){rgb=textureUpload(rgb,width,height,w,h,gl.RGBA8,gl.UNSIGNED_BYTE,bytes,0);width=w;height=h;stats.overlayRgbBytes=w*h*4;}
    if(ownedFrame?.active)ownedFrame.target();
    const old=ownedFrame?.active?null:{program:gl.getParameter(gl.CURRENT_PROGRAM),vao:gl.getParameter(gl.VERTEX_ARRAY_BINDING),active:gl.getParameter(gl.ACTIVE_TEXTURE),depth:gl.isEnabled(gl.DEPTH_TEST),blend:gl.isEnabled(gl.BLEND),depthMask:gl.getParameter(gl.DEPTH_WRITEMASK),srcRgb:gl.getParameter(gl.BLEND_SRC_RGB),dstRgb:gl.getParameter(gl.BLEND_DST_RGB),srcAlpha:gl.getParameter(gl.BLEND_SRC_ALPHA),dstAlpha:gl.getParameter(gl.BLEND_DST_ALPHA),eqRgb:gl.getParameter(gl.BLEND_EQUATION_RGB),eqAlpha:gl.getParameter(gl.BLEND_EQUATION_ALPHA)};
    gl.activeTexture(gl.TEXTURE0);const oldRgb=ownedFrame?.active?null:gl.getParameter(gl.TEXTURE_BINDING_2D);gl.activeTexture(gl.TEXTURE0+UNIT);const oldCoverage=ownedFrame?.active?null:gl.getParameter(gl.TEXTURE_BINDING_2D);
    try{gl.useProgram(program);gl.bindVertexArray(vao);gl.disable(gl.DEPTH_TEST);gl.depthMask(false);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,rgb);gl.activeTexture(gl.TEXTURE0+UNIT);gl.bindTexture(gl.TEXTURE_2D,coverage||rgb);gl.uniform1i(gl.getUniformLocation(program,'image'),0);gl.uniform1i(gl.getUniformLocation(program,'transmittance'),UNIT);gl.uniform1i(gl.getUniformLocation(program,'transparent'),active.mode!==0?1:0);if(physicalOutput){gl.uniform2f(gl.getUniformLocation(program,'logicalSize'),w,h);gl.uniform2f(gl.getUniformLocation(program,'outputSize'),active.outputWidth,active.outputHeight);}
      if(active.mode!==0){gl.enable(gl.BLEND);gl.blendEquationSeparate(gl.FUNC_ADD,gl.FUNC_ADD);gl.blendFuncSeparate(gl.ONE,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);}else gl.disable(gl.BLEND);gl.drawArrays(gl.TRIANGLES,0,3);check();
    }catch(e){failed=true;throw e;}finally{if(ownedFrame?.active){gl.disable(gl.BLEND);gl.depthMask(true);gl.activeTexture(gl.TEXTURE0);}else{gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,oldRgb);gl.activeTexture(gl.TEXTURE0+UNIT);gl.bindTexture(gl.TEXTURE_2D,oldCoverage);gl.activeTexture(old.active);gl.useProgram(old.program);gl.bindVertexArray(old.vao);old.depth?gl.enable(gl.DEPTH_TEST):gl.disable(gl.DEPTH_TEST);old.blend?gl.enable(gl.BLEND):gl.disable(gl.BLEND);gl.depthMask(old.depthMask);gl.blendFuncSeparate(old.srcRgb,old.dstRgb,old.srcAlpha,old.dstAlpha);gl.blendEquationSeparate(old.eqRgb,old.eqAlpha);}}
    try{if(active.mode!==0||retainOpaqueFull){if(active.mode===0&&stats.overlayCoverageMode!==0)retained.reset();retained.capture(active.outputWidth,active.outputHeight,active.token);}else retained.reset();account();}catch(e){failed=true;throw e;}stats.overlayCoverageFrames++;stats.overlayCoverageMode=active.mode;
    if(active.delta){deltaHistory={width:w,height:h,...active.token};stats.overlayDeltaPresentedFrames++;}
    active=null;
  }
  /** Region streaming replays the last FULL output; no pending Java delta is consumed. */
  function hold(w,h){alive();if(deltaTransport)invalidateDeltaHistory();if(pending||active)throw new Error('Cannot hold a partially declared overlay');retained.drawFull(w,h);}
  /** Bounded residency rejects the proposed frame before any world clear. Its
   * C declaration was consumed but never captured; replay FULL keeps UI/actors
   * coherent with the held world. Reset remains latched until a fresh frame. */
  function holdPrepared(w,h){alive();if(!deferRetainedReset||!pending||active||pending.mode!==1)throw new Error('Cannot retain bounded proposal');pending=null;if(stats.retainedValid)retained.drawFull(w,h);else stats.boundedStartupHolds=(stats.boundedStartupHolds||0)+1;}
  function dispose(){if(disposed)return;staging?.dispose();deltaHistory=null;retained.dispose();if(rgb)gl.deleteTexture(rgb);if(coverage)gl.deleteTexture(coverage);if(vao)gl.deleteVertexArray(vao);if(program)gl.deleteProgram(program);if(vertex)gl.deleteShader(vertex);if(fragment)gl.deleteShader(fragment);rgb=coverage=vao=program=vertex=fragment=null;pending=active=null;stats.overlayCoverageBytes=stats.overlayRgbBytes=stats.overlayCompositeResourceBytes=0;disposed=true;}
  return {prepare,prepareDelta,invalidateDeltaHistory,outputSize,begin,draw,hold,holdPrepared,dispose,get retainedOutput(){return pending?.mode===2||pending?.mode===3;},get canChangeOutput(){return !!pending&&(pending.mode===0||pending.mode===1);},get opaqueOutput(){return pending?.mode===0;}};
}
