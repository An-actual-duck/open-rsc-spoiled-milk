import {createPhysicalOutputSizer} from './browser-physical-output.js';
import {createStreamUploadValidation} from './browser-upload-validation.js';
import {createOwnedBrowserFrameState} from './browser-owned-state.js';
import {createBrowserSpriteAtlas} from './browser-sprite-atlas.js';
import {createWorldReadiness} from './browser-world-ready.js';
import {createBrowserOverlay} from './browser-overlay.js';
import {createBrowserSky} from './browser-sky.js';
import {createBrowserLighting} from './browser-lighting.js';
import {buildChunkColorBounds,hasResidentPositionContract,isChunkOutsideColorFrustum} from './browser-color-frustum.js';
/** Experimental resident-world WebGL2 backend. Receives ordinary Java-produced scene frames. */
export function adaptResidentShader(source, fragment) {
  if (typeof source !== 'string' || source.length > 131072 || !source.startsWith('#version 120')) throw new Error('Expected bounded production GLSL120 shader');
  let text = source.replace('#version 120', '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2DShadow;');
  text = text.replace(/\battribute\b/g, 'in').replace(/\bvarying\b/g, fragment ? 'in' : 'out').replace(/\btexture2D\(/g, 'texture(');
  text = text.replace(/shadow2D\((.*)\)\.r/g, 'texture($1)');
  if (fragment) text = text.replace('precision highp sampler2DShadow;', 'precision highp sampler2DShadow;\nout vec4 browserFragment;\nuniform float uBrowserAlphaCutoff;').replace(/gl_FragColor = color;/g, 'if (color.a <= uBrowserAlphaCutoff) discard; browserFragment = color;').replace(/\bgl_FragColor\b/g, 'browserFragment');
  return text;
}
/** Native atlas endpoints address texel centers, including mirrored and one-pixel regions. */
export function atlasTexelCenter(position,size,unit,extent) {
  const first=Math.fround((position+.5)/extent),last=Math.fround((position+size-.5)/extent);
  return Math.fround(first+Math.fround(Math.fround(last-first)*Math.fround(unit)));
}
const FLOAT_UNIFORMS=['uBrightness','uContrast','uGamma','uSaturation','uToneRed','uToneGreen','uToneBlue','uToneBlend','uLightDirectionX','uLightDirectionY','uLightDirectionZ','uLightAmbient','uLightIntensity','uFogStart','uFogEnd','uFogRed','uFogGreen','uFogBlue','uTerrainReliefStrength','uObjectReliefStrength','uTerrainVariationStrength','uTerrainVariationTolerance','uTerrainVariationTargetRed','uTerrainVariationTargetGreen','uTerrainVariationTargetBlue'];
const ATTRIBUTES=[['aPosition',3,0],['aMaterialColor',4,3],['aTexCoord',2,7],['aRawMaterialColor',3,15],['aBaseLegacyLight',1,14],['aNormal',3,18],['aModelKind',1,21],['aMaterialFamily',1,22],['aTerrainVariationMask',1,23],['aTerrainBlendColor',3,24],['aTerrainBlendStrength',1,27]];
const MAX_GPU_BYTES=256*1024*1024, MAX_CHUNKS=512, MAX_PIXELS=2097152;
function typed(value, Type) { return value instanceof Type ? value : new Type(value); }
/** Normalize alpha without touching RGB, including after legacy SRC_ALPHA blending. */
export function normalizeOpaqueCanvas(gl,known=null) {
  const mask=known?known.colorMask:gl.getParameter(gl.COLOR_WRITEMASK),color=known?known.clearColor:gl.getParameter(gl.COLOR_CLEAR_VALUE),scissor=known?known.scissor:gl.isEnabled(gl.SCISSOR_TEST);
  try{gl.disable(gl.SCISSOR_TEST);gl.colorMask(false,false,false,true);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);}
  finally{gl.colorMask(...mask);gl.clearColor(...color);if(scissor)gl.enable(gl.SCISSOR_TEST);}
}
export function createBrowserWebGL(canvas, report=()=>{}, options={}) {
  const gl=canvas.getContext('webgl2',{alpha:true,antialias:false,depth:true,preserveDrawingBuffer:false,powerPreference:'high-performance'});
  if(!gl) throw new Error('WebGL2 unavailable: browser GPU proof requires WebGL2');
  const stats={worldFrameSamples:[],gpuWorkSamples:[],jsSubmissionSamples:[],javaBridgeWorkSamples:[],backend:'WebGL2 resident production geometry',javaFrames:0,javaWorldFrames:0,heldWorldFrames:0,uploadedChunks:0,reusedChunkDraws:0,uploadedBytes:0,gpuBytes:0,resourceBudgetScope:"256MiB chunk/texture pixels; separate bounded16MiB sprite atlas, <=8MiB marker overlay plus optional <=32MiB combined C/T/retained texture peak, <=160MiB shadow maps and <=8MiB masks and separately bounded <=2MiB sky peak; excludes driver/program overhead",drawCalls:0,triangles:0,contextLost:false,limitations:['Marker UI lacks partial-alpha reconstruction unless coverage mode is enabled','Browser/device coverage and broader gameplay parity pending'],frameSamples:[]};
  const chunks=new Map(),textures=new Map(),programs=[],shaders=[],baseTextures=[];let program,overlayProgram,overlayTexture,emptyVAO,spriteProgram,spriteTexture,spriteVBO,spriteIBO,spriteVAO,disposed=false,last=0,lastWorld=0,lighting,lightingValues,sky,overlay;
  const colorVisible=new Uint8Array(256),colorCullScratch=new Float64Array(18);let colorCullingSupported=false;
  const ownedFrame=options.ownedState===true||options.benchmarkControl===true?createOwnedBrowserFrameState(gl,{enabled:options.ownedState===true}):null;
  let physicalOutput=options.physicalOutput===true,pendingPhysicalOutput=null;
  const physicalControl=options.benchmarkControl===true&&options.physicalOutputControl===true;
  const physicalCapability=physicalOutput||physicalControl;
  const outputSizer=createPhysicalOutputSizer(canvas,{enabled:physicalCapability,maxTextureSize:physicalCapability?(ownedFrame?.maxTextureSize||gl.getParameter(gl.MAX_TEXTURE_SIZE)):4096});
  stats.physicalOutputRequested=physicalOutput;
  stats.physicalOutputActive=physicalOutput;stats.physicalOutputWorldFramePolicies=[];stats.physicalOutputTransitions=[];
  if(physicalCapability)stats.resourceBudgetScope=stats.resourceBudgetScope.replace('<=32MiB combined','<=48MiB combined')+'; physical drawingbuffer <=1920x1080 with color+depth driver storage additional';
  let pendingOwnedState=null;
  stats.ownedStateActive=!!ownedFrame?.active;stats.ownedStateBenchmarkControl=options.benchmarkControl===true;stats.worldFramePolicies=[];stats.worldFrameSpriteGroups=[];stats.ownedStateTransitions=[];
  function queueOwnedState(enabled){alive();if(options.benchmarkControl!==true||typeof enabled!=='boolean')throw new Error('Private benchmark owned-state control unavailable');pendingOwnedState=enabled;stats.pendingOwnedState=enabled;return {queued:enabled,active:!!ownedFrame?.active};}
  function commitOwnedState(){if(pendingOwnedState===null)return;const before=!!ownedFrame.active;ownedFrame.active=pendingOwnedState;pendingOwnedState=null;stats.pendingOwnedState=null;stats.ownedStateActive=ownedFrame.active;stats.ownedStateTransitions.push({afterJavaFrame:stats.javaFrames,afterWorldFrame:stats.javaWorldFrames,before,enabled:ownedFrame.active,milliseconds:performance.now()});if(stats.ownedStateTransitions.length>64)stats.ownedStateTransitions.shift();}
  const worldReady=createWorldReadiness(gl,{stats});
  const uploadValidation=createStreamUploadValidation(gl,{stats,enabled:options.streamUploadValidation===true,poison:error=>worldReady.fail(String(error))});
  const gpuTimer=gl.getExtension("EXT_disjoint_timer_query_webgl2"),pendingQueries=[];let activeTimerQuery=null;
  let spriteAtlas,spriteDraws=[];const SPRITE_ATLAS=2048;
  const uniforms=new Map();
  function alive(){if(disposed||stats.contextLost||stats.backendFailed)throw new Error('Browser GPU backend unavailable; restart the private session');}
  function lost(event){event.preventDefault();stats.contextLost=true;uploadValidation.fail(new Error('Graphics context lost'));worldReady.fail('Graphics context lost. Stop and restart to retry.');report('WebGL context lost; stop and restart the private session');}
  canvas.addEventListener('webglcontextlost',lost);
  function shader(type,source){const s=gl.createShader(type);if(!s)throw new Error('WebGL shader allocation failed');gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const e=gl.getShaderInfoLog(s);gl.deleteShader(s);throw new Error(e);}shaders.push(s);return s;}
  function link(v,f){const p=gl.createProgram();if(!p)throw new Error('WebGL program allocation failed');programs.push(p);gl.attachShader(p,shader(gl.VERTEX_SHADER,v));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,f));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p));return p;}
  function loc(name){if(!uniforms.has(name))uniforms.set(name,gl.getUniformLocation(program,name));return uniforms.get(name);}
  function i(name,value){gl.uniform1i(loc(name),value);} function f(name,value){gl.uniform1f(loc(name),value);}
  function check(){const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('WebGL error 0x'+error.toString(16));}
  function reserve(bytes){if(!Number.isSafeInteger(bytes)||bytes<0||stats.gpuBytes+bytes>MAX_GPU_BYTES)throw new Error('Browser GPU resource budget exceeded');}
  function textureParameters(filter=gl.NEAREST){gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,filter);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,filter);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);}
  function chunkData(vertexInput,indexInput,batchInput) {
    const vertices=typed(vertexInput,Float32Array),indices=typed(indexInput,Int32Array),batches=new Int32Array(batchInput);
    if(vertices.length%28||vertices.length/28>524288||indices.length>1572864||batches.length%5)throw new Error('Invalid browser chunk dimensions');
    const count=vertices.length/28;for(const index of indices)if(index<0||index>=count)throw new Error('Invalid browser chunk index');
    for(let b=0;b<batches.length;b+=5)if(batches[b+1]<0||batches[b+2]<0||batches[b+1]+batches[b+2]>indices.length)throw new Error('Invalid browser material range');
    return {vertices,indices,batches,bounds:colorCullingSupported?buildChunkColorBounds(vertices):null};
  }
  const natives={
    async Java_orsc_BrowserWebGLBridge_worldProgress(lib,metadata){alive();worldReady.declare(metadata);},
    async Java_orsc_BrowserWebGLBridge_regionTransition(lib,soft){alive();worldReady.beginTransition(soft===true);},
    async Java_orsc_BrowserWebGLBridge_holdWorld(){alive();uploadValidation.beforeDraw();gl.bindSampler(0,null);if(!overlay)throw new Error('Region retention requires coverage compositor');if(ownedFrame?.active)ownedFrame.begin(canvas.width,canvas.height,0,0,0);overlay.hold(canvas.width,canvas.height);worldReady.held();gl.flush();stats.heldWorldFrames++;},
    async Java_orsc_BrowserWebGLBridge_initialize(lib,vertex,fragment){
      alive();if(program)throw new Error('WebGL backend initialized twice');
      program=link(adaptResidentShader(vertex,false),adaptResidentShader(fragment,true));
      const supported=options.colorFrustumCulling!==false&&(await hasResidentPositionContract(vertex));
      alive(); // Stop/context loss may happen while the exact shader digest is pending.
      colorCullingSupported=supported;
      stats.colorFrustumCulling=colorCullingSupported;
      overlayProgram=link('#version 300 es\nprecision highp float;out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0,1);}', '#version 300 es\nprecision highp float;uniform sampler2D image;uniform bool world;in vec2 uv;out vec4 color;void main(){vec4 s=texture(image,uv);if(world&&s.a>0.003&&s.a<0.005)discard;color=vec4(s.bgr,1.0);}');
      spriteProgram=link('#version 300 es\nprecision highp float;in vec3 position;in vec2 texCoord;uniform mat4 projection;out vec2 uv;void main(){uv=texCoord;gl_Position=projection*vec4(position,1);}', '#version 300 es\nprecision highp float;in vec2 uv;uniform sampler2D image;uniform vec3 tone;out vec4 color;void main(){color=texture(image,uv);if(color.a<=0.0)discard;color.rgb*=tone;}');
      spriteTexture=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,spriteTexture);textureParameters();gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,SPRITE_ATLAS,SPRITE_ATLAS,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
      spriteVAO=gl.createVertexArray();spriteVBO=gl.createBuffer();spriteIBO=gl.createBuffer();gl.bindVertexArray(spriteVAO);gl.bindBuffer(gl.ARRAY_BUFFER,spriteVBO);gl.bufferData(gl.ARRAY_BUFFER,1024*20*4,gl.STREAM_DRAW);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,spriteIBO);
      const spriteIndices=new Uint32Array(1024*6);for(let n=0;n<1024;n++)spriteIndices.set([0,1,2,0,2,3].map(x=>x+n*4),n*6);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,spriteIndices,gl.STATIC_DRAW);
      for(const [name,size,offset]of [['position',3,0],['texCoord',2,3]]){const at=gl.getAttribLocation(spriteProgram,name);gl.enableVertexAttribArray(at);gl.vertexAttribPointer(at,size,gl.FLOAT,false,20,offset*4);}
      stats.spriteResourceBytes=SPRITE_ATLAS*SPRITE_ATLAS*4+1024*20*4+1024*6*4;
      spriteAtlas=createBrowserSpriteAtlas(gl,{texture:spriteTexture,vbo:spriteVBO,stats,ownedFrame,deferOrCheck:uploadValidation.deferOrCheck});
      emptyVAO=gl.createVertexArray();overlayTexture=gl.createTexture();
      gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,overlayTexture);textureParameters();
      gl.useProgram(program);
      for(const name of ['uGeometryShadows','uShadowMaskEnabled','uGlowMaskEnabled','uRawMaterialMode','uTerrainDefinitionFieldEnabled','uGeometryRoofReceivers'])i(name,0);
      for(const [name,unit] of [['uTexture',0],['uShadowMask',1],['uGlowMask',2],['uNearGeometryShadow',3],['uFarGeometryShadowMap',4],['uGeometryRoofMask',5],['uPreviousFarGeometryShadowMap',6],['uTerrainDefinitionField',7]])i(name,unit);
      for(let unit=0;unit<8;unit++){
        gl.activeTexture(gl.TEXTURE0+unit);const t=gl.createTexture();baseTextures.push(t);gl.bindTexture(gl.TEXTURE_2D,t);
        if([3,4,6].includes(unit)) {gl.texImage2D(gl.TEXTURE_2D,0,gl.DEPTH_COMPONENT24,1,1,0,gl.DEPTH_COMPONENT,gl.UNSIGNED_INT,new Uint32Array([0xffffffff]));gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_COMPARE_MODE,gl.COMPARE_REF_TO_TEXTURE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_COMPARE_FUNC,gl.LEQUAL);}
        else gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([255,255,255,255]));
        textureParameters();
      }
      gl.activeTexture(gl.TEXTURE0);check();report('WebGL2 production resident shader ready');
      const ext=gl.getExtension('WEBGL_debug_renderer_info');stats.contextAttributes=gl.getContextAttributes();stats.vendor=gl.getParameter(ext?ext.UNMASKED_VENDOR_WEBGL:gl.VENDOR);stats.renderer=gl.getParameter(ext?ext.UNMASKED_RENDERER_WEBGL:gl.RENDERER);
      lighting=createBrowserLighting(gl,{program,stats,chunks,textures,adaptShader:adaptResidentShader,ownedFrame});
      sky=createBrowserSky(gl,{stats,ownedFrame,deferOrCheck:uploadValidation.deferOrCheck});
      return gl.getParameter(gl.MAX_TEXTURE_SIZE);
    },
    async Java_orsc_BrowserWebGLBridge_initializeLighting(lib,vertex,fragment){alive();if(!lighting)throw new Error('Browser lighting initialized before world program');lighting.initialize(vertex,fragment);},
    async Java_orsc_BrowserWebGLBridge_uploadLightingMask(lib,unit,width,height,rgba,bounds){alive();lighting.uploadMask(unit,width,height,rgba,bounds);},
    async Java_orsc_BrowserWebGLBridge_lightingFrame(lib,values){alive();lightingValues=new Float32Array(values);},
    async Java_orsc_BrowserWebGLBridge_initializeSky(){alive();sky.initialize();},
    async Java_orsc_BrowserWebGLBridge_uploadSkyGeometry(lib,geometry,ranges,paletteCount){alive();sky.uploadGeometry(geometry,ranges,paletteCount);},
    async Java_orsc_BrowserWebGLBridge_skyFrame(lib,values,palette){alive();sky.prepare(values,palette);stats.skyEnabled=values[0]===1;},
    async Java_orsc_BrowserWebGLBridge_overlayFrame(lib,width,height,mode,generation,frameSequence,fades,reset,transmittance){
      alive();if(!overlay){overlay=createBrowserOverlay(gl,{stats,ownedFrame,deferOrCheck:uploadValidation.deferOrCheck,retainOpaqueFull:true,physicalOutput:physicalCapability});stats.overlayCoverageEnabled=true;stats.limitations[0]='Coverage UI follows legacy RGB/T; small integer-rounding differences remain possible';}
      try{overlay.prepare(width,height,mode,generation,frameSequence,fades,reset,mode===0?null:typed(transmittance,Float32Array));}catch(e){stats.backendFailed=true;throw e;}
    },
    async Java_orsc_BrowserWebGLBridge_uploadChunk(lib,id,vertexInput,indexInput,batchInput){
      alive();if(chunks.size>=MAX_CHUNKS||chunks.has(id))throw new Error('Browser chunk cache limit/identity violation');
      const {vertices,indices,batches,bounds}=chunkData(vertexInput,indexInput,batchInput);
      const bytes=vertices.byteLength+indices.byteLength;reserve(bytes);
      const previousVAO=ownedFrame?.active?null:gl.getParameter(gl.VERTEX_ARRAY_BINDING),previousArray=ownedFrame?.active?null:gl.getParameter(gl.ARRAY_BUFFER_BINDING);
      let vao,vbo,ibo,committed=false;
      try {
        vao=gl.createVertexArray();vbo=gl.createBuffer();ibo=gl.createBuffer();
        if(!vao||!vbo||!ibo)throw new Error('WebGL chunk allocation failed');
        gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,vbo);gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.STATIC_DRAW);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ibo);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,indices,gl.STATIC_DRAW);
        for(const [name,size,offset] of ATTRIBUTES){const at=gl.getAttribLocation(program,name);if(at>=0){gl.enableVertexAttribArray(at);gl.vertexAttribPointer(at,size,gl.FLOAT,false,28*4,offset*4);}}
        check();chunks.set(id,{vao,vbo,ibo,batches,bounds,bytes,vertexBytes:vertices.byteLength,indexBytes:indices.byteLength});
        stats.gpuBytes+=bytes;stats.uploadedBytes+=bytes;stats.uploadedChunks++;committed=true;
      } finally {
        if(!ownedFrame?.active){gl.bindVertexArray(previousVAO);gl.bindBuffer(gl.ARRAY_BUFFER,previousArray);}
        if(!committed){if(vao)gl.deleteVertexArray(vao);if(vbo)gl.deleteBuffer(vbo);if(ibo)gl.deleteBuffer(ibo);}
      }
      // No Java array is retained: bufferData copied geometry; metadata was explicitly copied.
    },
    async Java_orsc_BrowserWebGLBridge_replaceChunk(lib,id,vertexInput,indexInput,batchInput){
      alive();const chunk=chunks.get(id);if(!chunk)throw new Error('Repack references absent GPU chunk');
      const {vertices,indices,batches,bounds}=chunkData(vertexInput,indexInput,batchInput);
      if(vertices.byteLength!==chunk.vertexBytes||indices.byteLength!==chunk.indexBytes)throw new Error('Immutable chunk shape changed during repack');
      const previousVAO=ownedFrame?.active?null:gl.getParameter(gl.VERTEX_ARRAY_BINDING),previousArray=ownedFrame?.active?null:gl.getParameter(gl.ARRAY_BUFFER_BINDING);
      try {
        gl.bindVertexArray(chunk.vao);gl.bindBuffer(gl.ARRAY_BUFFER,chunk.vbo);gl.bufferSubData(gl.ARRAY_BUFFER,0,vertices);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,chunk.ibo);gl.bufferSubData(gl.ELEMENT_ARRAY_BUFFER,0,indices);uploadValidation.deferOrCheck(check);
        chunk.batches=batches;chunk.bounds=bounds;stats.uploadedBytes+=vertices.byteLength+indices.byteLength;stats.repackedChunks=(stats.repackedChunks||0)+1;
      } catch(error){stats.backendFailed=true;throw error;}
      finally{if(!ownedFrame?.active){gl.bindVertexArray(previousVAO);gl.bindBuffer(gl.ARRAY_BUFFER,previousArray);}}
    },
    async Java_orsc_BrowserWebGLBridge_deleteTextures(lib,input){alive();for(const id of typed(input,Int32Array)){const old=textures.get(id);if(!old)throw new Error('Removal references absent browser texture');gl.deleteTexture(old.texture);stats.gpuBytes-=old.bytes;textures.delete(id);for(const chunk of chunks.values())for(let n=0;n<chunk.batches.length;n+=5)if(chunk.batches[n]===id)chunk.batches[n+4]=0;}},
    async Java_orsc_BrowserWebGLBridge_uploadTexture(lib,id,width,height,pixelInput,hasTransparency){
      alive();const old=textures.get(id);
      if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width*height>262144||(!old&&textures.size>=2048))throw new Error('Browser texture limit exceeded');
      const pixels=typed(pixelInput,Int32Array);if(pixels.length!==width*height)throw new Error('Browser texture dimensions mismatch');
      const rgba=new Uint8Array(pixels.length*4);for(let n=0;n<pixels.length;n++){const c=pixels[n];rgba[n*4]=c>>>24;rgba[n*4+1]=c>>>16;rgba[n*4+2]=c>>>8;rgba[n*4+3]=c;}
      const bytes=rgba.length,reuse=old&&old.width===width&&old.height===height;
      if(!reuse)reserve(bytes); // Keep the old resource valid until replacement allocation succeeds; bound peak storage.
      const previousActive=ownedFrame?.active?null:gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0);
      let previousTexture=ownedFrame?.active?null:gl.getParameter(gl.TEXTURE_BINDING_2D),texture=reuse?old.texture:null,committed=false;
      try {
        if(!texture)texture=gl.createTexture();if(!texture)throw new Error('WebGL texture allocation failed');
        gl.bindTexture(gl.TEXTURE_2D,texture);textureParameters();
        if(reuse)gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,width,height,gl.RGBA,gl.UNSIGNED_BYTE,rgba);
        else gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,rgba);
        check();textures.set(id,{texture,bytes,width,height,revision:(old?.revision||0)+1,hasTransparency:hasTransparency===true});
        if(!reuse){stats.gpuBytes+=bytes-(old?.bytes||0);if(old){if(previousTexture===old.texture)previousTexture=texture;gl.deleteTexture(old.texture);}}
        stats.uploadedBytes+=bytes;committed=true;
        // Alpha classification follows accepted texture data while bounded Java repacking catches up.
        if(!old||old.hasTransparency!==(hasTransparency===true))for(const chunk of chunks.values())for(let n=0;n<chunk.batches.length;n+=5)if(chunk.batches[n]===id)chunk.batches[n+4]=hasTransparency?1:0;
      } catch(error){if(reuse)stats.backendFailed=true;throw error;}
      finally{if(!ownedFrame?.active){gl.bindTexture(gl.TEXTURE_2D,previousTexture);gl.activeTexture(previousActive);}if(!committed&&!reuse&&texture)gl.deleteTexture(texture);}

    },
    async Java_orsc_BrowserWebGLBridge_present(lib,width,height,world,matrixInput,uniformInput,drawInput,pixelInput){
      alive();stats.presentedOwnedState=!!ownedFrame?.active;if(spriteAtlas)spriteAtlas.assertCommitted();const submitStart=performance.now();
      // Validate BEFORE timer allocation or ANY shadow/retained/sky/world draw.
      uploadValidation.beforeDraw();
      if(gpuTimer){while(pendingQueries.length&&gl.getQueryParameter(pendingQueries[0],gl.QUERY_RESULT_AVAILABLE)){const q=pendingQueries.shift();if(!gl.getParameter(gpuTimer.GPU_DISJOINT_EXT)){stats.gpuWorkSamples.push(gl.getQueryParameter(q,gl.QUERY_RESULT)/1e6);if(stats.gpuWorkSamples.length>(stats.measurement?4096:600))stats.gpuWorkSamples.shift();}gl.deleteQuery(q);}}
      const timerQuery=gpuTimer&&pendingQueries.length<8?gl.createQuery():null;if(timerQuery){activeTimerQuery=timerQuery;gl.beginQuery(gpuTimer.TIME_ELAPSED_EXT,timerQuery);}
      const matrices=typed(matrixInput,Float32Array),u=typed(uniformInput,Float32Array),draws=typed(drawInput,Int32Array),pixels=typed(pixelInput,Int32Array);
      if(width<1||height<1||width*height>MAX_PIXELS||pixels.length!==width*height||matrices.length!==48||u.length!==30||draws.length%8||draws.length>256*8)throw new Error('Browser frame bounds violated');
      // Resize/policy commits are presentation-only and happen before ANY pass.
      // Retained fades/BASE and held regions keep the exact previous backing.
      if(pendingPhysicalOutput!==null&&overlay?.canChangeOutput&&(overlay.opaqueOutput||world&&worldReady.sourceComplete)){
        physicalOutput=pendingPhysicalOutput;pendingPhysicalOutput=null;stats.pendingPhysicalOutput=null;stats.physicalOutputActive=physicalOutput;
        stats.physicalOutputTransitions.push({enabled:physicalOutput,beforeJavaFrame:stats.javaFrames,milliseconds:performance.now()});if(stats.physicalOutputTransitions.length>64)stats.physicalOutputTransitions.shift();
      }
      stats.presentedPhysicalOutput=physicalOutput;
      const measuredOutput=physicalCapability?outputSizer.size(width,height):null;
      const desiredOutput=physicalOutput?measuredOutput:{width,height};
      let output=desiredOutput;
      if(physicalCapability&&overlay)output=overlay.outputSize(width,height,output.width,output.height);
      const outputWidth=output.width,outputHeight=output.height;
      stats.physicalOutput={enabled:physicalOutput,logicalWidth:width,logicalHeight:height,width:outputWidth,height:outputHeight,deferred:output.deferred===true,devicePixelRatio:globalThis.window?.devicePixelRatio||1,cssWidth:measuredOutput?.cssWidth??null,cssHeight:measuredOutput?.cssHeight??null,requestedWidth:measuredOutput?.width??width,requestedHeight:measuredOutput?.height??height,requestedScale:measuredOutput?.requestedScale??1,capped:physicalOutput&&measuredOutput?.capped===true,drawingbufferColorDepthEstimateBytes:outputWidth*outputHeight*8};
      if(canvas.width!==outputWidth)canvas.width=outputWidth;if(canvas.height!==outputHeight)canvas.height=outputHeight;
      if(!lightingValues)throw new Error('Browser frame missing lighting declaration');
      if(ownedFrame?.active)ownedFrame.begin(outputWidth,outputHeight,world&&u[29]>=0?u[15]:0,world&&u[29]>=0?u[16]:0,world&&u[29]>=0?u[17]:0);
      lighting.prepare(lightingValues,draws);
      gl.viewport(0,0,outputWidth,outputHeight);gl.disable(gl.SCISSOR_TEST);gl.disable(gl.CULL_FACE);gl.disable(gl.BLEND);gl.depthMask(true);gl.clearDepth(1);
      gl.clearColor(world&&u[29]>=0?u[15]:0,world&&u[29]>=0?u[16]:0,world&&u[29]>=0?u[17]:0,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
      const retainedWorld=overlay?overlay.begin(width,height,outputWidth,outputHeight):false;
      if(!retainedWorld)sky.draw();
      let calls=0,triangles=0;stats.colorCulledChunkDraws=stats.colorVisibleChunkDraws=0;
      if(world&&!retainedWorld){
        let culled=0;
        for(let at=0;at<draws.length;at+=8){
          const chunk=chunks.get(draws[at]);
          const outside=colorCullingSupported&&chunk&&isChunkOutsideColorFrustum(chunk.bounds,matrices,width,height,draws[at+1],draws[at+2],colorCullScratch);
          colorVisible[at/8]=outside?0:1;if(outside)culled++;
        }
        stats.colorCulledChunkDraws=culled;stats.colorVisibleChunkDraws=draws.length/8-culled;
        gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);if(ownedFrame?.active)gl.depthMask(true);gl.useProgram(program);gl.uniformMatrix4fv(loc('uProjectionMatrix'),false,matrices.subarray(0,16));gl.uniformMatrix4fv(loc('uWorldViewMatrix'),false,matrices.subarray(16,32));
        FLOAT_UNIFORMS.forEach((name,n)=>f(name,u[n]));i('uTerrainDefinitionMode',u[25]);i('uTerrainVariationEnabled',u[26]);i('uRemasterLightingEnabled',u[27]);i('uFogEnabled',u[28]);lighting.bindReceiver();
        // The color pass owns these bindings until the sprite pass. Cache only
        // within this invocation: lighting/upload/context work may change GL state
        // between frames, so no prior-frame state is trusted.
        let residentVAO,residentTexture,textureEnabled,chunkX,chunkZ,terrainX,terrainZ,receiverPlane;
        gl.activeTexture(gl.TEXTURE0);
        for(const transparent of [false,true]) {
          if(transparent){gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);}else gl.disable(gl.BLEND);
          f('uBrowserAlphaCutoff',transparent?0:0.5);
          for(const kind of [1,2,3,5,4,0]) {
          if(kind===4||kind===5){gl.enable(gl.CULL_FACE);gl.cullFace(gl.BACK);}else gl.disable(gl.CULL_FACE);
          if(kind===2){gl.enable(gl.POLYGON_OFFSET_FILL);gl.polygonOffset(-1,-1);}else gl.disable(gl.POLYGON_OFFSET_FILL);
          for(let at=0;at<draws.length;at+=8){
          if(!colorVisible[at/8]||!(draws[at+5]&(1<<kind)))continue;const chunk=chunks.get(draws[at]);if(!chunk)throw new Error('Draw references absent browser chunk');
          if(residentVAO!==chunk.vao){gl.bindVertexArray(chunk.vao);residentVAO=chunk.vao;}
          if(chunkX!==draws[at+1]){chunkX=draws[at+1];f('uChunkOffsetX',chunkX);}
          if(chunkZ!==draws[at+2]){chunkZ=draws[at+2];f('uChunkOffsetZ',chunkZ);}
          if(terrainX!==draws[at+3]){terrainX=draws[at+3];f('uTerrainWorldOffsetX',terrainX);}
          if(terrainZ!==draws[at+4]){terrainZ=draws[at+4];f('uTerrainWorldOffsetZ',terrainZ);}
          if(receiverPlane!==draws[at+6]){receiverPlane=draws[at+6];lighting.bindReceiverPlane(receiverPlane);}
          const b=chunk.batches;for(let n=0;n<b.length;n+=5){if(b[n+3]!==kind||(b[n+4]===1)!==transparent)continue;const tex=textures.get(b[n]),enabled=tex?1:0,texture=tex?tex.texture:baseTextures[0];
            if(textureEnabled!==enabled){i('uTextureEnabled',enabled);textureEnabled=enabled;}
            if(residentTexture!==texture){gl.bindTexture(gl.TEXTURE_2D,texture);residentTexture=texture;}
            gl.drawElements(gl.TRIANGLES,b[n+2],gl.UNSIGNED_INT,b[n+1]*4);calls++;triangles+=b[n+2]/3;}
          stats.reusedChunkDraws++;
        }
        }
        }
        gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);gl.disable(gl.POLYGON_OFFSET_FILL);gl.polygonOffset(0,0);
        if(spriteDraws.length){
          gl.useProgram(spriteProgram);gl.bindVertexArray(spriteVAO);gl.uniformMatrix4fv(gl.getUniformLocation(spriteProgram,'projection'),false,matrices.subarray(32,48));gl.uniform3f(gl.getUniformLocation(spriteProgram,'tone'),u[4],u[5],u[6]);gl.uniform1i(gl.getUniformLocation(spriteProgram,'image'),0);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,spriteTexture);
          gl.depthMask(false);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
          gl.drawElements(gl.TRIANGLES,spriteDraws.length*6,gl.UNSIGNED_INT,0);gl.disable(gl.BLEND);gl.depthMask(true);
        }
        stats.worldSpriteGroups=spriteDraws.length;
        stats.javaWorldFrames++;
        stats.presentedStreamUploadValidation=uploadValidation.active;
        (stats.streamUploadValidationWorldFramePolicies??=[]).push(uploadValidation.active?1:0);if(stats.streamUploadValidationWorldFramePolicies.length>(stats.measurement?4096:600))stats.streamUploadValidationWorldFramePolicies.shift();
      }
      // RGBA drawingbuffer is required for WebGL copy into retained RGBA8 FULL.
      // Make it visually opaque before UI; legacy world blends can lower alpha.
      const alphaProof=options.opaqueProof&&world&&stats.javaWorldFrames>=100&&!stats.opaqueAlphaProof;
      let beforeAlpha;
      if(alphaProof){beforeAlpha=new Uint8Array(outputWidth*outputHeight*4);gl.readPixels(0,0,outputWidth,outputHeight,gl.RGBA,gl.UNSIGNED_BYTE,beforeAlpha);}
      normalizeOpaqueCanvas(gl,ownedFrame?.active?ownedFrame:null);
      if(alphaProof){const after=new Uint8Array(beforeAlpha.length);gl.readPixels(0,0,outputWidth,outputHeight,gl.RGBA,gl.UNSIGNED_BYTE,after);let changedRgb=0,beforeNonOpaque=0,afterNonOpaque=0;for(let n=0;n<after.length;n+=4){for(let c=0;c<3;c++)if(beforeAlpha[n+c]!==after[n+c])changedRgb++;if(beforeAlpha[n+3]!==255)beforeNonOpaque++;if(after[n+3]!==255)afterNonOpaque++;}stats.opaqueAlphaProof={pixels:outputWidth*outputHeight,changedRgb,beforeNonOpaque,afterNonOpaque,diagnosticReadback:true};}
      if(overlay)overlay.draw(width,height,new Uint8Array(pixels.buffer,pixels.byteOffset,pixels.byteLength));
      else {
        gl.disable(gl.DEPTH_TEST);if(ownedFrame?.active){gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);}gl.depthMask(false);gl.useProgram(overlayProgram);gl.bindVertexArray(emptyVAO);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,overlayTexture);
        gl.pixelStorei(gl.UNPACK_ALIGNMENT,4);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array(pixels.buffer,pixels.byteOffset,pixels.byteLength));
        gl.uniform1i(gl.getUniformLocation(overlayProgram,'image'),0);gl.uniform1i(gl.getUniformLocation(overlayProgram,'world'),world?1:0);gl.drawArrays(gl.TRIANGLES,0,3);gl.depthMask(true);check();
      }
      if(alphaProof){const final=new Uint8Array(outputWidth*outputHeight*4);gl.readPixels(0,0,outputWidth,outputHeight,gl.RGBA,gl.UNSIGNED_BYTE,final);let count=0;for(let n=3;n<final.length;n+=4)if(final[n]!==255)count++;stats.opaqueAlphaProof.finalNonOpaque=count;check();}
      if(timerQuery){gl.endQuery(gpuTimer.TIME_ELAPSED_EXT);pendingQueries.push(timerQuery);activeTimerQuery=null;}
      const now=performance.now();stats.jsSubmissionSamples.push(now-submitStart);if(stats.jsSubmissionSamples.length>(stats.measurement?4096:600))stats.jsSubmissionSamples.shift();
      if(world&&!retainedWorld){if(lastWorld){stats.worldFrameSamples.push(now-lastWorld);stats.worldFramePolicies.push(stats.presentedOwnedState);stats.worldFrameSpriteGroups.push(spriteDraws.length);stats.physicalOutputWorldFramePolicies.push(physicalOutput?1:0);if(stats.worldFrameSamples.length>(stats.measurement?4096:600)){stats.worldFrameSamples.shift();stats.worldFramePolicies.shift();stats.worldFrameSpriteGroups.shift();stats.physicalOutputWorldFramePolicies.shift();}}lastWorld=now;}else lastWorld=0;
      worldReady.presented({world:world&&!retainedWorld,width,height,textures:textures.size,sprites:spriteDraws.length,lighting:!!lightingValues});
      stats.javaFrames++;stats.drawCalls=calls;stats.triangles=triangles;stats.activeChunks=draws.length/8;stats.lastFrame=now;
      if(last){stats.frameSamples.push(now-last);if(stats.frameSamples.length>(stats.measurement?4096:600))stats.frameSamples.shift();}last=now;
    },
    async Java_orsc_BrowserWebGLBridge_frameWork(lib,values,poseInput){alive();const p=typed(poseInput,Int32Array);if(p.length!==14)throw new Error("Invalid frame pose schema");stats.pose={cameraOffset:Array.from(p.subarray(0,3)),rotation:Array.from(p.subarray(3,6)),center:Array.from(p.subarray(6,8)),viewport:Array.from(p.subarray(8,10)),perspectiveShift:p[10],fogStart:p[11],fogEnd:p[12],signedLevel:p[13]};stats.spriteCompositeCache={enabled:values[10]===1,hits:values[11],misses:values[12],bytes:values[13]};stats.javaSkyExportMs=values[9];stats.cpuDepthFramePresent=values[7]===1;stats.javaLightingExportMs=values[8];stats.javaCacheEntries=values[5];stats.javaCacheResidentBytes=values[6];stats.javaBridgeWorkSamples.push(Array.from(values));if(stats.javaBridgeWorkSamples.length>(stats.measurement?4096:600))stats.javaBridgeWorkSamples.shift();uploadValidation.frameComplete();commitOwnedState();},
    async Java_orsc_BrowserWebGLBridge_deleteChunks(lib,input){
      alive();const ids=typed(input,Int32Array);for(const id of ids){const x=chunks.get(id);if(!x)throw new Error('Eviction references absent GPU chunk');gl.deleteVertexArray(x.vao);gl.deleteBuffer(x.vbo);gl.deleteBuffer(x.ibo);stats.gpuBytes-=x.bytes;chunks.delete(id);}stats.evictedChunks=(stats.evictedChunks||0)+ids.length;
    },
    async Java_orsc_BrowserWebGLBridge_planWorldSprites(lib,epoch,sequence,images){alive();try{return spriteAtlas.plan(epoch,sequence,typed(images,Int32Array));}catch(error){stats.backendFailed=true;throw error;}},
    async Java_orsc_BrowserWebGLBridge_commitWorldSprites(lib,epoch,sequence,pixels,quads){alive();try{spriteDraws.length=spriteAtlas.commit(epoch,sequence,typed(pixels,Uint8Array),typed(quads,Float32Array));}catch(error){stats.backendFailed=true;throw error;}}

  };
  function dispose(){if(disposed)return;outputSizer.dispose();uploadValidation.dispose();if(activeTimerQuery){try{gl.endQuery(gpuTimer.TIME_ELAPSED_EXT);}finally{gl.deleteQuery(activeTimerQuery);activeTimerQuery=null;}}pendingOwnedState=null;pendingPhysicalOutput=null;stats.pendingPhysicalOutput=null;stats.pendingOwnedState=null;worldReady.dispose();if(spriteAtlas)spriteAtlas.dispose();if(lighting)lighting.dispose();if(sky)sky.dispose();if(overlay)overlay.dispose();for(const x of chunks.values()){gl.deleteVertexArray(x.vao);gl.deleteBuffer(x.vbo);gl.deleteBuffer(x.ibo);}for(const x of textures.values())gl.deleteTexture(x.texture);for(const p of programs)gl.deleteProgram(p);for(const s of shaders)gl.deleteShader(s);for(const t of baseTextures)gl.deleteTexture(t);for(const q of pendingQueries)gl.deleteQuery(q);if(overlayTexture)gl.deleteTexture(overlayTexture);if(spriteTexture)gl.deleteTexture(spriteTexture);if(spriteVBO)gl.deleteBuffer(spriteVBO);if(spriteIBO)gl.deleteBuffer(spriteIBO);if(spriteVAO)gl.deleteVertexArray(spriteVAO);if(emptyVAO)gl.deleteVertexArray(emptyVAO);chunks.clear();textures.clear();programs.length=shaders.length=baseTextures.length=pendingQueries.length=0;stats.gpuBytes=0;stats.spriteResourceBytes=0;canvas.removeEventListener('webglcontextlost',lost);disposed=true;stats.disposed=true;}
  for(const name of Object.keys(natives)) {
    const call=natives[name];natives[name]=async(...args)=>{
      try{uploadValidation.assertAlive();return await call(...args);}catch(error){if(uploadValidation.active)uploadValidation.fail(error);worldReady.fail(String(error));stats.lastError=String(error);stats.lastErrorAt=performance.now();report('Browser GPU error: '+error);throw error;}
    };
  }
  function queuePhysicalOutput(enabled){alive();if(!physicalControl||typeof enabled!=='boolean')throw new Error('Private physical output control unavailable');pendingPhysicalOutput=enabled;stats.pendingPhysicalOutput=enabled;return {queued:enabled,active:physicalOutput};}
  function queueStreamUploadValidation(enabled){alive();if(options.benchmarkControl!==true)throw new Error('Private benchmark control unavailable');uploadValidation.queue(enabled);}
  return {natives,stats,dispose,canvas,...(options.benchmarkControl===true?{queueOwnedState,queueStreamUploadValidation,queuePhysicalOutput}:{})};
}
