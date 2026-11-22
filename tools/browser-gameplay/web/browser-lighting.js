import {createBrowserShadows} from './browser-shadows.js';

/** Terrain/roof/glow textures use the byte-identical production Java builders.
 * All input arrays are copied or uploaded before a native bridge call returns. */
export function createBrowserLighting(gl,options) {
  const {program,stats,ownedFrame}=options,shadows=createBrowserShadows(gl,options),masks=new Map(),locations=new Map();
  const MAX_MASK_BYTES=8*1024*1024;let values=null,disposed=false;
  stats.lightingMaskBytes=0;stats.lightingMaskBudgetBytes=MAX_MASK_BYTES;
  function loc(name){if(!locations.has(name))locations.set(name,gl.getUniformLocation(program,name));return locations.get(name);}
  function initialize(vertex,fragment){shadows.initialize(vertex,fragment);}
  function uploadMask(unit,width,height,input,bounds){
    if(disposed||![2,5,7].includes(unit)||width<1||height<1||width>1024||height>1024||input.length!==width*height*4||bounds.length!==4)throw new Error('Browser lighting mask schema invalid');
    const bytes=width*height*4,old=masks.get(unit);if(stats.lightingMaskBytes-(old?old.bytes:0)+bytes>MAX_MASK_BYTES||width>(ownedFrame?.active?ownedFrame.maxTextureSize:gl.getParameter(gl.MAX_TEXTURE_SIZE))||height>(ownedFrame?.active?ownedFrame.maxTextureSize:gl.getParameter(gl.MAX_TEXTURE_SIZE)))throw new Error('Browser lighting mask budget exceeded');
    const active=ownedFrame?.active?null:gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0+unit);const binding=ownedFrame?.active?null:gl.getParameter(gl.TEXTURE_BINDING_2D),texture=old?old.texture:gl.createTexture();
    try {
      gl.bindTexture(gl.TEXTURE_2D,texture);const filter=unit===5?gl.NEAREST:gl.LINEAR;
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,filter);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,filter);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      const bytesView=ArrayBuffer.isView(input)?new Uint8Array(input.buffer,input.byteOffset,input.byteLength):new Uint8Array(input);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,bytesView);
      stats.lightingMaskBytes+=bytes-(old?old.bytes:0);stats.lightingMaskUploads=(stats.lightingMaskUploads||0)+1;masks.set(unit,{texture,bytes,width,height,bounds:new Float32Array(bounds)});
    } finally {if(!ownedFrame?.active){gl.bindTexture(gl.TEXTURE_2D,binding);gl.activeTexture(active);}else gl.activeTexture(gl.TEXTURE0);}
  }
  function prepare(input,draws){if(disposed)throw new Error('Browser lighting disposed');values=new Float32Array(input);shadows.prepare(values,draws);}
  function bindMask(unit,enabled,prefix,at){
    const mask=masks.get(unit),active=enabled&&mask;gl.uniform1i(loc(prefix+'Enabled'),active?1:0);if(!active)return;
    gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,mask.texture);
    for(const [suffix,index]of [['MinX',0],['MinZ',1],['InvSpanX',2],['InvSpanZ',3]])gl.uniform1f(loc(prefix+suffix),values[at+index]);
  }
  function bindReceiver(){
    if(!values)return;shadows.bindReceiver();gl.uniform1i(loc('uGlowMask'),2);bindMask(2,values[14]===1,'uGlowMask',28);
    gl.uniform1i(loc('uTerrainDefinitionFieldEnabled'),values[13]===1&&masks.has(7)?1:0);
    if(values[13]===1&&masks.has(7)){
      gl.activeTexture(gl.TEXTURE7);gl.bindTexture(gl.TEXTURE_2D,masks.get(7).texture);gl.uniform1i(loc('uTerrainDefinitionField'),7);
      for(const [name,n]of [['uTerrainDefinitionMinX',24],['uTerrainDefinitionMinZ',25],['uTerrainDefinitionInvWidth',26],['uTerrainDefinitionInvHeight',27]])gl.uniform1f(loc(name),values[n]);
    }
    gl.uniform1i(loc('uGeometryRoofMask'),5);
    if(values[11]===1&&masks.has(5)){
      gl.activeTexture(gl.TEXTURE5);gl.bindTexture(gl.TEXTURE_2D,masks.get(5).texture);
      for(const [suffix,n]of [['MinX',20],['MinZ',21],['InvSpanX',22],['InvSpanZ',23]])gl.uniform1f(loc('uGeometryRoof'+suffix),values[n]);
    }
    gl.activeTexture(gl.TEXTURE0);
  }
  function bindReceiverPlane(plane){gl.uniform1i(loc('uGeometryRoofReceivers'),values&&values[1]===1&&values[11]===1&&values[12]===plane&&masks.has(5)?1:0);}
  function dispose(){if(disposed)return;shadows.dispose();for(const mask of masks.values())gl.deleteTexture(mask.texture);masks.clear();stats.lightingMaskBytes=0;disposed=true;}
  return {initialize,uploadMask,prepare,bindReceiver,bindReceiverPlane,dispose};
}
