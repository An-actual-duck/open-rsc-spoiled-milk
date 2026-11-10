/** Geometry-depth backend for the unchanged production shadow projection/receiver shader.
 * Projection policy and shader text arrive from BrowserLightingData; no scene or sun math is duplicated. */
export function createBrowserShadows(gl,{program,stats,chunks,textures,adaptShader,ownedFrame}) {
  const MAX_SHADOW_BYTES=160*1024*1024,ownedVAOs=new Map(),locations=new Map(),depthLocations=new Map();
  let depthProgram=null,depthVertex=null,depthFragment=null,whiteTexture=null,emptyDepth=null,near=null,far=null,previous=null,values=null,disposed=false,drawSignature='',previousActive=false,sourceProvenance=null,sourceProvenanceRequired=false;
  stats.shadowResourceBytes=0;stats.shadowResourceBudgetBytes=MAX_SHADOW_BYTES;stats.shadowNearDraws=stats.shadowFarDraws=0;
  function loc(name){if(!locations.has(name))locations.set(name,gl.getUniformLocation(program,name));return locations.get(name);}
  // The depth program links once per module. Cache null optimized-out locations too.
  function depthLoc(name){if(!depthLocations.has(name))depthLocations.set(name,gl.getUniformLocation(depthProgram,name));return depthLocations.get(name);}
  function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,adaptShader(source,type===gl.FRAGMENT_SHADER));gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const message=gl.getShaderInfoLog(s);gl.deleteShader(s);throw new Error('Browser shadow shader: '+message);}return s;}
  function initialize(vertex,fragment){
    if(disposed||depthProgram)throw new Error('Browser shadow initialization state invalid');
    depthVertex=compile(gl.VERTEX_SHADER,vertex);depthFragment=compile(gl.FRAGMENT_SHADER,fragment);depthProgram=gl.createProgram();
    gl.attachShader(depthProgram,depthVertex);gl.attachShader(depthProgram,depthFragment);gl.bindAttribLocation(depthProgram,0,'aPosition');gl.bindAttribLocation(depthProgram,1,'aTexCoord');gl.linkProgram(depthProgram);
    if(!gl.getProgramParameter(depthProgram,gl.LINK_STATUS))throw new Error('Browser shadow link: '+gl.getProgramInfoLog(depthProgram));
    const active=gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0);const binding=gl.getParameter(gl.TEXTURE_BINDING_2D);
    try {
      whiteTexture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,whiteTexture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([255,255,255,255]));
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
      emptyDepth=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,emptyDepth);gl.texImage2D(gl.TEXTURE_2D,0,gl.DEPTH_COMPONENT24,1,1,0,gl.DEPTH_COMPONENT,gl.UNSIGNED_INT,new Uint32Array([0xffffffff]));
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_COMPARE_MODE,gl.COMPARE_REF_TO_TEXTURE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_COMPARE_FUNC,gl.LEQUAL);
      stats.shadowFallbackBytes=8;
    } finally {gl.bindTexture(gl.TEXTURE_2D,binding);gl.activeTexture(active);}
  }
  function destroy(map){if(!map||map.deleted)return;map.deleted=true;gl.deleteFramebuffer(map.framebuffer);gl.deleteTexture(map.texture);stats.shadowResourceBytes-=map.bytes;}
  function allocate(map,size){
    if(map&&!map.deleted&&map.size===size)return map;
    const bytes=size*size*4;
    // Validate peak allocation, retaining the old valid map until replacement
    // storage and its framebuffer are complete. Failure must leave ownership
    // and accounting unchanged so retry/disposal cannot double-delete it.
    if(!Number.isInteger(size)||size<1||size>(ownedFrame?.active?ownedFrame.maxTextureSize:gl.getParameter(gl.MAX_TEXTURE_SIZE))||stats.shadowResourceBytes+bytes>MAX_SHADOW_BYTES)throw new Error('Browser production shadow resource limit');
    let texture=null,framebuffer=null;
    try {
      texture=gl.createTexture();framebuffer=gl.createFramebuffer();if(!texture||!framebuffer)throw new Error('Browser shadow allocation failed');
      gl.activeTexture(gl.TEXTURE3);gl.bindTexture(gl.TEXTURE_2D,texture);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_COMPARE_MODE,gl.COMPARE_REF_TO_TEXTURE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_COMPARE_FUNC,gl.LEQUAL);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.DEPTH_COMPONENT24,size,size,0,gl.DEPTH_COMPONENT,gl.UNSIGNED_INT,null);
      gl.bindFramebuffer(gl.FRAMEBUFFER,framebuffer);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.TEXTURE_2D,texture,0);gl.drawBuffers([gl.NONE]);gl.readBuffer(gl.NONE);
      if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw new Error('Browser shadow depth framebuffer incomplete');
      const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('Browser shadow storage error 0x'+error.toString(16));
    } catch(error) {if(framebuffer)gl.deleteFramebuffer(framebuffer);if(texture)gl.deleteTexture(texture);throw error;}
    const replacement={texture,framebuffer,size,bytes};stats.shadowResourceBytes+=bytes;destroy(map);return replacement;
  }
  function vao(id,chunk){
    let entry=ownedVAOs.get(id);if(entry&&entry.vbo===chunk.vbo&&entry.ibo===chunk.ibo)return entry.vao;
    if(entry)gl.deleteVertexArray(entry.vao);
    const made=gl.createVertexArray();gl.bindVertexArray(made);gl.bindBuffer(gl.ARRAY_BUFFER,chunk.vbo);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,chunk.ibo);
    gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,3,gl.FLOAT,false,112,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,2,gl.FLOAT,false,112,28);
    ownedVAOs.set(id,{vao:made,vbo:chunk.vbo,ibo:chunk.ibo});return made;
  }
  function drawMap(map,matrix,draws,staticOnly,excluded){
    gl.bindFramebuffer(gl.FRAMEBUFFER,map.framebuffer);gl.viewport(0,0,map.size,map.size);gl.clear(gl.DEPTH_BUFFER_BIT);
    gl.uniformMatrix4fv(depthLoc('uLightMatrix'),false,matrix);let count=0;
    for(let at=0;at<draws.length;at+=8){
      if(staticOnly&&draws[at+7]===2)continue;const id=draws[at],chunk=chunks.get(id);if(!chunk)throw new Error('Shadow draw references absent geometry');
      gl.bindVertexArray(vao(id,chunk));gl.uniform1f(depthLoc('uGlobalOffsetX'),draws[at+1]+values[9]);gl.uniform1f(depthLoc('uGlobalOffsetZ'),draws[at+2]+values[10]);
      const batches=chunk.batches;
      for(let n=0;n<batches.length;n+=5){
        if(staticOnly&&excluded.has(batches[n]))continue;if(batches[n+2]<=0)continue;
        const texture=textures.get(batches[n]);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture?texture.texture:whiteTexture);
        gl.uniform1i(depthLoc('uAlphaTest'),texture&&batches[n+4]?1:0);
        gl.drawElements(gl.TRIANGLES,batches[n+2],gl.UNSIGNED_INT,batches[n+1]*4);count++;
      }
    }
    return count;
  }
  function suspend(){destroy(near);destroy(far);destroy(previous);near=far=previous=null;previousActive=false;drawSignature='';stats.shadowNearDraws=stats.shadowFarDraws=0;stats.geometryShadowsActive=false;}
  function setSourceProvenance(version,revision,complete){
    if(disposed||version!==1||!Number.isInteger(revision)||revision<1||revision>2147483647||typeof complete!=='boolean')throw new Error('Browser shadow source provenance invalid');
    sourceProvenanceRequired=true;sourceProvenance={revision,complete};
  }
  function discardSourceProvenance(){sourceProvenance=null;}
  function prepare(input,draws){
    const currentSource=sourceProvenance;sourceProvenance=null;
    if(disposed)throw new Error('Browser shadow module disposed');values=new Float32Array(input);if(values.length<81||values[0]!==1||values.length!==81+values[80]||draws.length%8)throw new Error('Browser shadow frame schema invalid');
    for(const [id,entry]of ownedVAOs)if(!chunks.has(id)){gl.deleteVertexArray(entry.vao);ownedVAOs.delete(id);}
    if(values[1]!==1||values[15]<0){suspend();return;}
    if(sourceProvenanceRequired&&!currentSource)throw new Error('Browser shadow source provenance missing for current frame');
    if(!depthProgram)throw new Error('Browser production shadow shader is not initialized');
    const old=ownedFrame?.active?null:{draw:gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING),read:gl.getParameter(gl.READ_FRAMEBUFFER_BINDING),viewport:gl.getParameter(gl.VIEWPORT),program:gl.getParameter(gl.CURRENT_PROGRAM),vao:gl.getParameter(gl.VERTEX_ARRAY_BINDING),array:gl.getParameter(gl.ARRAY_BUFFER_BINDING),active:gl.getParameter(gl.ACTIVE_TEXTURE),mask:gl.getParameter(gl.COLOR_WRITEMASK),depthMask:gl.getParameter(gl.DEPTH_WRITEMASK),depthFunc:gl.getParameter(gl.DEPTH_FUNC),clear:gl.getParameter(gl.DEPTH_CLEAR_VALUE),depth:gl.isEnabled(gl.DEPTH_TEST),blend:gl.isEnabled(gl.BLEND),cull:gl.isEnabled(gl.CULL_FACE),scissor:gl.isEnabled(gl.SCISSOR_TEST),polygon:gl.isEnabled(gl.POLYGON_OFFSET_FILL),factor:gl.getParameter(gl.POLYGON_OFFSET_FACTOR),units:gl.getParameter(gl.POLYGON_OFFSET_UNITS)};
    try {
      gl.useProgram(depthProgram);gl.uniform1i(depthLoc('uTexture'),0);gl.enable(gl.DEPTH_TEST);gl.depthMask(true);gl.depthFunc(gl.LESS);gl.clearDepth(1);gl.colorMask(false,false,false,false);
      gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);gl.disable(gl.SCISSOR_TEST);gl.enable(gl.POLYGON_OFFSET_FILL);gl.polygonOffset(1.5,2.0);
      near=allocate(near,values[3]);const excluded=new Set(Array.from(values.subarray(81)));stats.shadowNearDraws=drawMap(near,values.subarray(32,48),draws,false,excluded);stats.shadowFarDraws=0;
      let signature='';for(let at=0;at<draws.length;at+=8){if(draws[at+7]===2)continue;for(const n of [0,1,2,3,4,6,7])signature+=draws[at+n]+',';}
      for(let at=0;at<draws.length;at+=8){if(draws[at+7]===2)continue;const batches=chunks.get(draws[at]).batches;for(let n=0;n<batches.length;n+=5)if(batches[n+4]&&!excluded.has(batches[n])){const texture=textures.get(batches[n]);signature+='|'+batches[n]+':'+(texture?texture.revision||0:0);}}
      // Full-source provenance is Java-authoritative, independent of residency
      // IDs or omitted zero-contribution chunks. Partial first-load maps must
      // still rebuild until a complete demanded GPU set has actually drawn.
      if(currentSource)signature='source1:'+currentSource.revision+(currentSource.complete?':complete':':partial:'+signature);
      const changed=signature!==drawSignature;
      if(values[4]>0){
        const rebuild=values[6]===1||changed||!far;let target=null;
        if(rebuild){
          // GPU geometry may arrive over several Java frames. Such changes are
          // scene invalidations, never a blend with a stale/incomplete far map.
          const blend=values[7]===1&&!changed&&far;
          target=allocate(previous,values[4]);previous=null;
          try{stats.shadowFarDraws=drawMap(target,values.subarray(48,64),draws,true,excluded);}catch(error){destroy(target);throw error;}
          if(blend){previous=far;previousActive=true;}else{destroy(far);previousActive=false;}
          far=target;stats.shadowFarRebuilds=(stats.shadowFarRebuilds||0)+1;
        }
        if(values[7]!==1||changed){previousActive=false;destroy(previous);previous=null;}
      }else{destroy(far);destroy(previous);far=previous=null;previousActive=false;}
      drawSignature=signature;stats.geometryShadowsActive=true;
    } finally {
      if(ownedFrame?.active){ownedFrame.target();gl.colorMask(true,true,true,true);gl.depthMask(true);gl.disable(gl.POLYGON_OFFSET_FILL);gl.polygonOffset(0,0);gl.activeTexture(gl.TEXTURE0);}else{
      gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,old.draw);gl.bindFramebuffer(gl.READ_FRAMEBUFFER,old.read);gl.viewport(...old.viewport);gl.useProgram(old.program);gl.bindVertexArray(old.vao);gl.bindBuffer(gl.ARRAY_BUFFER,old.array);gl.colorMask(...old.mask);gl.depthMask(old.depthMask);gl.depthFunc(old.depthFunc);gl.clearDepth(old.clear);gl.polygonOffset(old.factor,old.units);
      for(const [cap,on]of [[gl.DEPTH_TEST,old.depth],[gl.BLEND,old.blend],[gl.CULL_FACE,old.cull],[gl.SCISSOR_TEST,old.scissor],[gl.POLYGON_OFFSET_FILL,old.polygon]])on?gl.enable(cap):gl.disable(cap);gl.activeTexture(old.active);}
    }
  }
  function bindReceiver(){
    const active=values&&values[1]===1&&near;gl.uniform1i(loc('uGeometryShadows'),active?1:0);gl.uniform1i(loc('uFarGeometryShadow'),active&&far?1:0);gl.uniform1i(loc('uPreviousFarGeometryShadow'),active&&previousActive&&previous?1:0);gl.uniform1i(loc('uGeometryRoofReceivers'),0);
    // Shared receiver coordinates also drive terrain definition when sunlight
    // shadows are disabled (negative levels, Off and non-directional modes).
    gl.uniform1f(loc('uGeometryShadowWorldOffsetX'),values?values[9]:0);gl.uniform1f(loc('uGeometryShadowWorldOffsetZ'),values?values[10]:0);
    if(!active){
      if(emptyDepth)for(const [name,unit]of [['uNearGeometryShadow',3],['uFarGeometryShadowMap',4],['uPreviousFarGeometryShadowMap',6]]){gl.uniform1i(loc(name),unit);gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,emptyDepth);}gl.activeTexture(gl.TEXTURE0);return;
    }
    gl.uniform1i(loc('uGeometryShadowRadius'),values[5]);gl.uniformMatrix4fv(loc('uNearGeometryLight'),false,values.subarray(32,48));
    gl.uniform1f(loc('uNearGeometryTexel'),values[16]);gl.uniform1f(loc('uNearGeometryWorldTexel'),values[18]);
    if(far){gl.uniformMatrix4fv(loc('uFarGeometryLight'),false,values.subarray(48,64));gl.uniform1f(loc('uFarGeometryTexel'),values[17]);gl.uniform1f(loc('uFarGeometryWorldTexel'),values[19]);}
    if(previousActive&&previous){gl.uniformMatrix4fv(loc('uPreviousFarGeometryLight'),false,values.subarray(64,80));gl.uniform1f(loc('uFarGeometryBlend'),values[8]);}
    for(const [name,unit,map]of [['uNearGeometryShadow',3,near],['uFarGeometryShadowMap',4,far||near],['uPreviousFarGeometryShadowMap',6,previousActive&&previous?previous:near]]){gl.uniform1i(loc(name),unit);gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,map.texture);}
    gl.activeTexture(gl.TEXTURE0);
  }
  function dispose(){if(disposed)return;suspend();for(const entry of ownedVAOs.values())gl.deleteVertexArray(entry.vao);ownedVAOs.clear();if(depthProgram)gl.deleteProgram(depthProgram);if(depthVertex)gl.deleteShader(depthVertex);if(depthFragment)gl.deleteShader(depthFragment);if(whiteTexture)gl.deleteTexture(whiteTexture);if(emptyDepth)gl.deleteTexture(emptyDepth);depthLocations.clear();stats.shadowFallbackBytes=0;disposed=true;}
  return {initialize,prepare,bindReceiver,dispose,setSourceProvenance,discardSourceProvenance};
}
