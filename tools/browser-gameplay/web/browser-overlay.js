import {createBrowserRetainedFrame} from './browser-retained-frame.js';
/** Optional exact-coverage compositor. RGB is the ordinary software result over
 * black; transmittance is the surviving world coefficient. No world readback. */
export function createBrowserOverlay(gl,{stats={}}={}) {
  const MAX_PIXELS=2097152, UNIT=8;
  const retained=createBrowserRetainedFrame(gl,{stats});
  let program,vertex,fragment,vao,rgb,coverage,width=0,height=0,tWidth=0,tHeight=0,pending,active,failed=false,disposed=false;
  Object.assign(stats,{overlayCoverageBytes:0,overlayRgbBytes:0,overlayCoverageFrames:0,overlayCompositePeakBudgetBytes:32*1024*1024,overlayCompositePeakBytes:0,overlayCompositeResourceBytes:0});
  function check(){const e=gl.getError();if(e!==gl.NO_ERROR)throw new Error('Coverage GL error 0x'+e.toString(16));}
  function dimensions(w,h){if(!Number.isInteger(w)||!Number.isInteger(h)||w<1||h<1||w*h>MAX_PIXELS||w>gl.getParameter(gl.MAX_TEXTURE_SIZE)||h>gl.getParameter(gl.MAX_TEXTURE_SIZE))throw new Error('Coverage dimensions exceed bounds');}
  function alive(){if(failed||disposed)throw new Error('Coverage backend unavailable; restart session');}
  function shader(type,source){const s=gl.createShader(type);if(!s)throw new Error('Coverage shader allocation failed');try{gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s));return s;}catch(e){gl.deleteShader(s);throw e;}}
  function initialize(){alive();if(program)return;try{
    vertex=shader(gl.VERTEX_SHADER,'#version 300 es\nprecision highp float;out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0,1);}');
    fragment=shader(gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;uniform sampler2D image;uniform sampler2D transmittance;uniform bool transparent;in vec2 uv;out vec4 color;void main(){vec3 rgb=texture(image,uv).bgr;float t=transparent?clamp(texture(transmittance,uv).r,0.0,1.0):0.0;color=vec4(rgb,1.0-t);}');
    program=gl.createProgram();if(!program)throw new Error('Coverage program allocation failed');gl.attachShader(program,vertex);gl.attachShader(program,fragment);gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));vao=gl.createVertexArray();if(!vao)throw new Error('Coverage VAO allocation failed');check();
  }catch(e){failed=true;throw e;}}
  function resourceBytes(){return stats.overlayRgbBytes+stats.overlayCoverageBytes+stats.retainedResourceBytes;}
  function reserveCombined(extra){const peak=resourceBytes()+extra;if(peak>stats.overlayCompositePeakBudgetBytes)throw new Error('Coverage combined texture peak budget exceeded');stats.overlayCompositePeakBytes=Math.max(stats.overlayCompositePeakBytes,peak);}
  function account(){stats.overlayCompositeResourceBytes=resourceBytes();reserveCombined(0);}
  function textureParameters(){for(const p of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,p,gl.NEAREST);for(const p of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,p,gl.CLAMP_TO_EDGE);}
  function textureUpload(old,oldW,oldH,w,h,format,type,data,unit){
    const previousActive=gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0+unit);let restore=gl.getParameter(gl.TEXTURE_BINDING_2D),next=old,committed=false;
    try{if(!next||oldW!==w||oldH!==h){reserveCombined(w*h*4);next=gl.createTexture();if(!next)throw new Error('Coverage texture allocation failed');}gl.bindTexture(gl.TEXTURE_2D,next);textureParameters();gl.pixelStorei(gl.UNPACK_ALIGNMENT,4);
      if(next===old)gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,w,h,format===gl.R32F?gl.RED:gl.RGBA,type,data);else gl.texImage2D(gl.TEXTURE_2D,0,format,w,h,0,format===gl.R32F?gl.RED:gl.RGBA,type,data);check();committed=true;if(next!==old&&old){if(restore===old)restore=next;gl.deleteTexture(old);}return next;
    }catch(e){if(next===old)failed=true;throw e;}finally{gl.bindTexture(gl.TEXTURE_2D,restore);gl.activeTexture(previousActive);if(!committed&&next&&next!==old)gl.deleteTexture(next);}
  }
  function prepare(w,h,mode,generation,frameSequence,fades,reset,transmittance){
    alive();dimensions(w,h);if(pending)throw new Error('Coverage declaration was not consumed');
    if(![0,1,2,3].includes(mode)||!Number.isSafeInteger(generation)||generation<0||!Number.isSafeInteger(frameSequence)||frameSequence<0||!Number.isInteger(fades)||fades<0||fades>16||(mode===3?fades<1:fades!==0))throw new Error('Invalid coverage mode/token');
    if(mode!==0&&(!(transmittance instanceof Float32Array)||transmittance.length!==w*h))throw new Error('Invalid coverage transmittance plane');
    // A resized retained FULL cannot define the new viewport; release it BEFORE C/T replacements.
    if(reset||width!==w||height!==h)retained.reset();
    if(mode!==0){coverage=textureUpload(coverage,tWidth,tHeight,w,h,gl.R32F,gl.FLOAT,transmittance,UNIT);tWidth=w;tHeight=h;stats.overlayCoverageBytes=w*h*4;}
    account();stats.overlayDeclaration={width:w,height:h,mode,generation,frameSequence,fades,reset};pending={w,h,mode,token:{generation,frameSequence},fades};
  }
  /** Called after parent clear, before any new world draw. */
  function begin(w,h){alive();if(!pending||pending.w!==w||pending.h!==h)throw new Error('Missing/current coverage declaration');active=pending;pending=null;
    try{if(active.mode===3)retained.drawFaded(w,h,active.fades,active.token);else if(active.mode===2)retained.drawBase(w,h,active.token);}catch(e){failed=true;throw e;}
    return active.mode===2||active.mode===3;
  }
  function draw(w,h,bytes){
    alive();if(!active||active.w!==w||active.h!==h||!(bytes instanceof Uint8Array)||bytes.length!==w*h*4)throw new Error('Coverage RGB frame mismatch');initialize();
    rgb=textureUpload(rgb,width,height,w,h,gl.RGBA8,gl.UNSIGNED_BYTE,bytes,0);width=w;height=h;stats.overlayRgbBytes=w*h*4;
    const old={program:gl.getParameter(gl.CURRENT_PROGRAM),vao:gl.getParameter(gl.VERTEX_ARRAY_BINDING),active:gl.getParameter(gl.ACTIVE_TEXTURE),depth:gl.isEnabled(gl.DEPTH_TEST),blend:gl.isEnabled(gl.BLEND),depthMask:gl.getParameter(gl.DEPTH_WRITEMASK),srcRgb:gl.getParameter(gl.BLEND_SRC_RGB),dstRgb:gl.getParameter(gl.BLEND_DST_RGB),srcAlpha:gl.getParameter(gl.BLEND_SRC_ALPHA),dstAlpha:gl.getParameter(gl.BLEND_DST_ALPHA),eqRgb:gl.getParameter(gl.BLEND_EQUATION_RGB),eqAlpha:gl.getParameter(gl.BLEND_EQUATION_ALPHA)};
    gl.activeTexture(gl.TEXTURE0);const oldRgb=gl.getParameter(gl.TEXTURE_BINDING_2D);gl.activeTexture(gl.TEXTURE0+UNIT);const oldCoverage=gl.getParameter(gl.TEXTURE_BINDING_2D);
    try{gl.useProgram(program);gl.bindVertexArray(vao);gl.disable(gl.DEPTH_TEST);gl.depthMask(false);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,rgb);gl.activeTexture(gl.TEXTURE0+UNIT);gl.bindTexture(gl.TEXTURE_2D,coverage||rgb);gl.uniform1i(gl.getUniformLocation(program,'image'),0);gl.uniform1i(gl.getUniformLocation(program,'transmittance'),UNIT);gl.uniform1i(gl.getUniformLocation(program,'transparent'),active.mode!==0?1:0);
      if(active.mode!==0){gl.enable(gl.BLEND);gl.blendEquationSeparate(gl.FUNC_ADD,gl.FUNC_ADD);gl.blendFuncSeparate(gl.ONE,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);}else gl.disable(gl.BLEND);gl.drawArrays(gl.TRIANGLES,0,3);check();
    }catch(e){failed=true;throw e;}finally{gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,oldRgb);gl.activeTexture(gl.TEXTURE0+UNIT);gl.bindTexture(gl.TEXTURE_2D,oldCoverage);gl.activeTexture(old.active);gl.useProgram(old.program);gl.bindVertexArray(old.vao);old.depth?gl.enable(gl.DEPTH_TEST):gl.disable(gl.DEPTH_TEST);old.blend?gl.enable(gl.BLEND):gl.disable(gl.BLEND);gl.depthMask(old.depthMask);gl.blendFuncSeparate(old.srcRgb,old.dstRgb,old.srcAlpha,old.dstAlpha);gl.blendEquationSeparate(old.eqRgb,old.eqAlpha);}
    try{if(active.mode!==0){if(stats.retainedResourceBytes===0)reserveCombined(w*h*8);retained.capture(w,h,active.token);}else retained.reset();account();}catch(e){failed=true;throw e;}stats.overlayCoverageFrames++;stats.overlayCoverageMode=active.mode;active=null;
  }
  function dispose(){if(disposed)return;retained.dispose();if(rgb)gl.deleteTexture(rgb);if(coverage)gl.deleteTexture(coverage);if(vao)gl.deleteVertexArray(vao);if(program)gl.deleteProgram(program);if(vertex)gl.deleteShader(vertex);if(fragment)gl.deleteShader(fragment);rgb=coverage=vao=program=vertex=fragment=null;pending=active=null;stats.overlayCoverageBytes=stats.overlayRgbBytes=stats.overlayCompositeResourceBytes=0;disposed=true;}
  return {prepare,begin,draw,dispose};
}
