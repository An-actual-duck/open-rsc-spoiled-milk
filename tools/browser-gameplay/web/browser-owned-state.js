/** Explicit private frame ownership, NOT a GL proxy or inferred state cache.
 * Only createBrowserWebGL opts in. Children retain scoped APIs by default.
 * Every color frame targets the default framebuffer; retained BASE is temporary.
 */
import {establishOverlayUnpack} from './browser-overlay-unpack.js';
export function createOwnedBrowserFrameState(gl,{enabled=true,ownOverlayUnpack=false}={}) {
  const maxTextureSize=gl.getParameter(gl.MAX_TEXTURE_SIZE);
  if(!Number.isInteger(maxTextureSize)||maxTextureSize<1)throw new Error('Invalid owned browser texture capability');
  const viewport=new Int32Array(4),clearColor=new Float32Array([0,0,0,1]),colorMask=[true,true,true,true];
  function target(){gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(...viewport);}
  function begin(width,height,r,g,b){
    viewport[2]=width;viewport[3]=height;clearColor[0]=r;clearColor[1]=g;clearColor[2]=b;
    target();
    for(const cap of [gl.SCISSOR_TEST,gl.STENCIL_TEST,gl.RASTERIZER_DISCARD,gl.CULL_FACE,gl.BLEND,gl.POLYGON_OFFSET_FILL,gl.SAMPLE_ALPHA_TO_COVERAGE,gl.SAMPLE_COVERAGE])gl.disable(cap);
    gl.enable(gl.DITHER);gl.colorMask(true,true,true,true);gl.depthMask(true);gl.clearDepth(1);gl.activeTexture(gl.TEXTURE0);
    gl.blendEquation(gl.FUNC_ADD);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
  }
  const result={gl,maxTextureSize,viewport,clearColor,colorMask,scissor:false,active:enabled===true,begin,target};
  if(ownOverlayUnpack){
    let active=enabled===true,needsUnpack=true;
    result.ownsOverlayUnpack=true;
    result.ensureOverlayUnpack=()=>{if(needsUnpack){establishOverlayUnpack(gl);needsUnpack=false;}};
    Object.defineProperty(result,'active',{get:()=>active,set:value=>{const entering=value===true&&!active;active=value===true;if(entering){needsUnpack=true;result.ensureOverlayUnpack();}},enumerable:true});
    // Establish the exclusive boundary before even initial/loading texture work.
    if(active)result.ensureOverlayUnpack();
  }
  return result;
}
