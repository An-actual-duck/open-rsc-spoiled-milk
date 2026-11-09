/** Experimental resident-world WebGL2 backend. Receives ordinary Java-produced scene frames. */
export function adaptResidentShader(source, fragment) {
  if (typeof source !== 'string' || source.length > 131072 || !source.startsWith('#version 120')) throw new Error('Expected bounded production GLSL120 shader');
  let text = source.replace('#version 120', '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2DShadow;');
  text = text.replace(/\battribute\b/g, 'in').replace(/\bvarying\b/g, fragment ? 'in' : 'out').replace(/\btexture2D\(/g, 'texture(');
  text = text.replace(/shadow2D\((.*)\)\.r/g, 'texture($1)');
  if (fragment) text = text.replace('precision highp sampler2DShadow;', 'precision highp sampler2DShadow;\nout vec4 browserFragment;').replace(/gl_FragColor = color;/g, 'if (color.a < 0.01) discard; browserFragment = color;').replace(/\bgl_FragColor\b/g, 'browserFragment');
  return text;
}
const FLOAT_UNIFORMS=['uBrightness','uContrast','uGamma','uSaturation','uToneRed','uToneGreen','uToneBlue','uToneBlend','uLightDirectionX','uLightDirectionY','uLightDirectionZ','uLightAmbient','uLightIntensity','uFogStart','uFogEnd','uFogRed','uFogGreen','uFogBlue','uTerrainReliefStrength','uObjectReliefStrength','uTerrainVariationStrength','uTerrainVariationTolerance','uTerrainVariationTargetRed','uTerrainVariationTargetGreen','uTerrainVariationTargetBlue'];
const ATTRIBUTES=[['aPosition',3,0],['aMaterialColor',4,3],['aTexCoord',2,7],['aRawMaterialColor',3,15],['aBaseLegacyLight',1,14],['aNormal',3,18],['aModelKind',1,21],['aMaterialFamily',1,22],['aTerrainVariationMask',1,23],['aTerrainBlendColor',3,24],['aTerrainBlendStrength',1,27]];
const MAX_GPU_BYTES=256*1024*1024, MAX_CHUNKS=512, MAX_PIXELS=2097152;
function typed(value, Type) { return value instanceof Type ? value : new Type(value); }
export function createBrowserWebGL(canvas, report=()=>{}) {
  const gl=canvas.getContext('webgl2',{alpha:false,antialias:false,depth:true,preserveDrawingBuffer:true,powerPreference:'high-performance'});
  if(!gl) throw new Error('WebGL2 unavailable: browser GPU proof requires WebGL2');
  const stats={backend:'WebGL2 resident production geometry',javaFrames:0,javaWorldFrames:0,uploadedChunks:0,reusedChunkDraws:0,uploadedBytes:0,gpuBytes:0,drawCalls:0,triangles:0,contextLost:false,limitations:['Geometry shadows not yet activated','Software overlay uses black chroma key; sprite depth/black edges are not equivalent','Sky backdrop not yet ported','Terrain valley field and glow masks pending'],frameSamples:[]};
  const chunks=new Map(),textures=new Map(),programs=[],shaders=[],baseTextures=[];let program,overlayProgram,overlayTexture,emptyVAO,disposed=false,last=0;
  const uniforms=new Map();
  function alive(){if(disposed||stats.contextLost)throw new Error('Browser GPU backend unavailable; restart the private session');}
  function lost(event){event.preventDefault();stats.contextLost=true;report('WebGL context lost; stop and restart the private session');}
  canvas.addEventListener('webglcontextlost',lost);
  function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const e=gl.getShaderInfoLog(s);gl.deleteShader(s);throw new Error(e);}shaders.push(s);return s;}
  function link(v,f){const p=gl.createProgram();gl.attachShader(p,shader(gl.VERTEX_SHADER,v));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,f));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p));programs.push(p);return p;}
  function loc(name){if(!uniforms.has(name))uniforms.set(name,gl.getUniformLocation(program,name));return uniforms.get(name);}
  function i(name,value){gl.uniform1i(loc(name),value);} function f(name,value){gl.uniform1f(loc(name),value);}
  function check(){const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('WebGL error 0x'+error.toString(16));}
  function allocate(bytes){if(bytes<0||stats.gpuBytes+bytes>MAX_GPU_BYTES)throw new Error('Browser GPU resource budget exceeded');stats.gpuBytes+=bytes;stats.uploadedBytes+=bytes;}
  function textureParameters(filter=gl.NEAREST){gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,filter);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,filter);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);}
  const natives={
    async Java_orsc_BrowserWebGLBridge_initialize(lib,vertex,fragment){
      alive();if(program)throw new Error('WebGL backend initialized twice');
      program=link(adaptResidentShader(vertex,false),adaptResidentShader(fragment,true));
      overlayProgram=link('#version 300 es\nprecision highp float;out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0,1);}', '#version 300 es\nprecision highp float;uniform sampler2D image;uniform bool world;in vec2 uv;out vec4 color;void main(){vec4 s=texture(image,uv);if(world&&max(s.r,max(s.g,s.b))<0.001)discard;color=vec4(s.bgr,1.0);}');
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
      const ext=gl.getExtension('WEBGL_debug_renderer_info');stats.vendor=gl.getParameter(ext?ext.UNMASKED_VENDOR_WEBGL:gl.VENDOR);stats.renderer=gl.getParameter(ext?ext.UNMASKED_RENDERER_WEBGL:gl.RENDERER);
    },
    async Java_orsc_BrowserWebGLBridge_uploadChunk(lib,id,vertexInput,indexInput,batchInput){
      alive();if(chunks.size>=MAX_CHUNKS||chunks.has(id))throw new Error('Browser chunk cache limit/identity violation');
      const vertices=typed(vertexInput,Float32Array),indices=typed(indexInput,Int32Array),batches=new Int32Array(batchInput);
      if(vertices.length%28||vertices.length/28>524288||indices.length>1572864||batches.length%5)throw new Error('Invalid browser chunk dimensions');
      const count=vertices.length/28;for(const index of indices)if(index<0||index>=count)throw new Error('Invalid browser chunk index');
      for(let b=0;b<batches.length;b+=5)if(batches[b+1]<0||batches[b+2]<0||batches[b+1]+batches[b+2]>indices.length)throw new Error('Invalid browser material range');
      const bytes=vertices.byteLength+indices.byteLength;allocate(bytes);
      const vao=gl.createVertexArray(),vbo=gl.createBuffer(),ibo=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,vbo);gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.STATIC_DRAW);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ibo);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,indices,gl.STATIC_DRAW);
      for(const [name,size,offset] of ATTRIBUTES){const at=gl.getAttribLocation(program,name);if(at>=0){gl.enableVertexAttribArray(at);gl.vertexAttribPointer(at,size,gl.FLOAT,false,28*4,offset*4);}}
      chunks.set(id,{vao,vbo,ibo,batches,bytes});stats.uploadedChunks++;check();
      // No Java array is retained: bufferData copied geometry; metadata was explicitly copied.
    },
    async Java_orsc_BrowserWebGLBridge_uploadTexture(lib,id,width,height,pixelInput){
      alive();if(width<1||height<1||width*height>262144||textures.size>2048)throw new Error('Browser texture limit exceeded');
      const pixels=typed(pixelInput,Int32Array);if(pixels.length!==width*height)throw new Error('Browser texture dimensions mismatch');
      const rgba=new Uint8Array(pixels.length*4);for(let n=0;n<pixels.length;n++){const c=pixels[n];rgba[n*4]=c>>>24;rgba[n*4+1]=c>>>16;rgba[n*4+2]=c>>>8;rgba[n*4+3]=c;}
      const old=textures.get(id);if(old){gl.deleteTexture(old.texture);stats.gpuBytes-=old.bytes;}
      const bytes=rgba.length;allocate(bytes);const texture=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);textureParameters();gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,rgba);textures.set(id,{texture,bytes});check();
    },
    async Java_orsc_BrowserWebGLBridge_present(lib,width,height,world,matrixInput,uniformInput,drawInput,pixelInput){
      alive();const matrices=typed(matrixInput,Float32Array),u=typed(uniformInput,Float32Array),draws=typed(drawInput,Int32Array),pixels=typed(pixelInput,Int32Array);
      if(width<1||height<1||width*height>MAX_PIXELS||pixels.length!==width*height||matrices.length!==32||u.length!==30||draws.length%6||draws.length>256*6)throw new Error('Browser frame bounds violated');
      if(canvas.width!==width)canvas.width=width;if(canvas.height!==height)canvas.height=height;
      gl.viewport(0,0,width,height);gl.disable(gl.SCISSOR_TEST);gl.disable(gl.CULL_FACE);gl.disable(gl.BLEND);gl.depthMask(true);gl.clearDepth(1);
      gl.clearColor(world&&u[29]>=0?u[15]:0,world&&u[29]>=0?u[16]:0,world&&u[29]>=0?u[17]:0,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
      let calls=0,triangles=0;
      if(world){
        gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.useProgram(program);gl.uniformMatrix4fv(loc('uProjectionMatrix'),false,matrices.subarray(0,16));gl.uniformMatrix4fv(loc('uWorldViewMatrix'),false,matrices.subarray(16));
        FLOAT_UNIFORMS.forEach((name,n)=>f(name,u[n]));i('uTerrainDefinitionMode',u[25]);i('uTerrainVariationEnabled',u[26]);i('uRemasterLightingEnabled',u[27]);i('uFogEnabled',u[28]);
        for(const kind of [1,2,3,5,4,0])for(let at=0;at<draws.length;at+=6){
          const chunk=chunks.get(draws[at]);if(!chunk)throw new Error('Draw references absent browser chunk');gl.bindVertexArray(chunk.vao);
          f('uChunkOffsetX',draws[at+1]);f('uChunkOffsetZ',draws[at+2]);f('uTerrainWorldOffsetX',draws[at+3]);f('uTerrainWorldOffsetZ',draws[at+4]);
          const b=chunk.batches;for(let n=0;n<b.length;n+=5){if(b[n+3]!==kind)continue;const tex=textures.get(b[n]);i('uTextureEnabled',tex?1:0);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,tex?tex.texture:baseTextures[0]);gl.drawElements(gl.TRIANGLES,b[n+2],gl.UNSIGNED_INT,b[n+1]*4);calls++;triangles+=b[n+2]/3;}
          stats.reusedChunkDraws++;
        }
        stats.javaWorldFrames++;
      }
      gl.disable(gl.DEPTH_TEST);gl.depthMask(false);gl.useProgram(overlayProgram);gl.bindVertexArray(emptyVAO);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,overlayTexture);
      gl.pixelStorei(gl.UNPACK_ALIGNMENT,4);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array(pixels.buffer,pixels.byteOffset,pixels.byteLength));
      gl.uniform1i(gl.getUniformLocation(overlayProgram,'image'),0);gl.uniform1i(gl.getUniformLocation(overlayProgram,'world'),world?1:0);gl.drawArrays(gl.TRIANGLES,0,3);gl.depthMask(true);check();
      const now=performance.now();stats.javaFrames++;stats.drawCalls=calls;stats.triangles=triangles;stats.activeChunks=draws.length/6;stats.lastFrame=now;
      if(last){stats.frameSamples.push(now-last);if(stats.frameSamples.length>600)stats.frameSamples.shift();}last=now;
    },
    async Java_orsc_BrowserWebGLBridge_reset(){
      alive();for(const x of chunks.values()){gl.deleteVertexArray(x.vao);gl.deleteBuffer(x.vbo);gl.deleteBuffer(x.ibo);}for(const x of textures.values())gl.deleteTexture(x.texture);chunks.clear();textures.clear();stats.gpuBytes=0;
    }
  };
  function dispose(){if(disposed)return;for(const x of chunks.values()){gl.deleteVertexArray(x.vao);gl.deleteBuffer(x.vbo);gl.deleteBuffer(x.ibo);}for(const x of textures.values())gl.deleteTexture(x.texture);for(const p of programs)gl.deleteProgram(p);for(const s of shaders)gl.deleteShader(s);for(const t of baseTextures)gl.deleteTexture(t);if(overlayTexture)gl.deleteTexture(overlayTexture);if(emptyVAO)gl.deleteVertexArray(emptyVAO);canvas.removeEventListener('webglcontextlost',lost);disposed=true;stats.disposed=true;}
  return {natives,stats,dispose,canvas};
}
