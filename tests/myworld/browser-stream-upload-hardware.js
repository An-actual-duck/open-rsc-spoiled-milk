import {createBrowserWebGL} from './webgl.js';
const result={completed:false,passed:false,scope:'Production-shader WebGL streaming error policy; no gameplay/image/FPS claim',cases:[]};window.hardwareResult=result;
function assert(ok,why){if(!ok)throw new Error(why);}
const text=async name=>{const r=await fetch('./oracle/'+name);if(!r.ok)throw new Error('Missing shader '+name);return r.text();};
try{
 const [vertex,fragment,depthVertex,depthFragment]=await Promise.all(['world.vert','world.frag','depth.vert','depth.frag'].map(text));
 for(const kind of ['chunk','atlasPixels','atlasDescriptors','sky','coverage']){
  const canvas=document.createElement('canvas');canvas.width=canvas.height=2;document.body.append(canvas);
  const raw=canvas.getContext('webgl2',{alpha:true,antialias:false,depth:true,preserveDrawingBuffer:false});assert(raw,'WebGL2 unavailable');
  const ext=raw.getExtension('WEBGL_debug_renderer_info');assert(ext,'No hardware adapter identity');const renderer=raw.getParameter(ext.UNMASKED_RENDERER_WEBGL);
  assert(/AMD|Radeon/i.test(renderer)&&/ANGLE/i.test(renderer)&&/OpenGL/i.test(renderer)&&!/SwiftShader|llvmpipe|softpipe|software|WARP/i.test(renderer),'Physical AMD ANGLE OpenGL required: '+renderer);result.renderer=renderer;
  let fault=null,draws=0,queryCreated=0,queryDeleted=0;const live=new Set();
  const gl=new Proxy(raw,{get(target,key){const value=target[key];if(typeof value!=='function')return value;return(...args)=>{
   if(fault===key){fault=null;if(key==='bufferSubData')target.bufferSubData(target.ARRAY_BUFFER,0x7fffffff,new Uint8Array(4));else target.texSubImage2D(target.TEXTURE_2D,0,0,0,-1,1,target.RGBA,target.UNSIGNED_BYTE,new Uint8Array(4));}
   if(key==='drawArrays'||key==='drawElements')draws++;
   if(/^delete(?:Texture|Buffer|VertexArray|Shader|Program|Framebuffer|Sync|Query)$/.test(key)&&args[0])assert(live.delete(args[0]),'Unknown/double GL deletion '+key);
   if(key==='createQuery')queryCreated++;if(key==='deleteQuery'&&args[0])queryDeleted++;
   const out=value.apply(target,args);if(/^create(?:Texture|Buffer|VertexArray|Shader|Program|Framebuffer|Query)$/.test(key)||key==='fenceSync')if(out)live.add(out);return out;
  };}});
  const backend=createBrowserWebGL({width:2,height:2,getContext:()=>gl,addEventListener(){},removeEventListener(){}},()=>{},{benchmarkControl:true,streamUploadValidation:true,ownedState:true}),n=backend.natives;
  await n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);await n.Java_orsc_BrowserWebGLBridge_initializeLighting(null,depthVertex,depthFragment);await n.Java_orsc_BrowserWebGLBridge_initializeSky();
  const vertices=new Float32Array(84);for(let i=0;i<3;i++){vertices[i*28+3]=vertices[i*28+4]=vertices[i*28+5]=vertices[i*28+6]=1;vertices[i*28+19]=1;vertices[i*28+21]=1;}
  const indices=new Int32Array([0,1,2]),batches=new Int32Array([7,0,3,1,0]);await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,1,vertices,indices,batches);await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,1,1,new Int32Array([-1]),false);
  await n.Java_orsc_BrowserWebGLBridge_uploadSkyGeometry(null,new Float32Array([0,0,0,0,1,0,0,0,0,1,0,0]),new Int32Array([0,3,0,3,0,1,3,0,1,3,0,1]),1);
  const sky=new Float32Array(20);sky[0]=1;sky[3]=1;for(const at of [4,9,14,19])sky[at]=1;const palette=new Float32Array(4096);palette.fill(1);
  const light=new Float32Array(81);light[0]=1;light[1]=1;light[3]=16;light[5]=16;light[16]=1/16;for(const at of [32,37,42,47])light[at]=1;await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);
  const matrix=new Float32Array(48);for(let base=0;base<48;base+=16)for(const at of [0,5,10,15])matrix[base+at]=1;
  const uniforms=new Float32Array(30);uniforms[0]=uniforms[1]=uniforms[2]=uniforms[3]=uniforms[4]=uniforms[5]=uniforms[6]=1;uniforms[29]=0;
  async function coverage(seq){await n.Java_orsc_BrowserWebGLBridge_overlayFrame(null,2,2,1,1,seq,0,false,new Float32Array(4));}
  async function present(){await n.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,1,1,1,0,0,0,1,2,2,1]));return n.Java_orsc_BrowserWebGLBridge_present(null,2,2,true,matrix,uniforms,new Int32Array([1,0,0,0,0,2,0,0]),new Int32Array(4));}
  await n.Java_orsc_BrowserWebGLBridge_skyFrame(null,sky,palette);await coverage(1);await present();await n.Java_orsc_BrowserWebGLBridge_frameWork(null,new Float32Array(14),new Int32Array(14));
  const warm={clock:performance.now(),javaFrames:backend.stats.javaFrames,javaWorldFrames:backend.stats.javaWorldFrames,draws,worldDrawCalls:backend.stats.drawCalls,skyDraws:backend.stats.skyDrawCalls,shadowDraws:backend.stats.shadowNearDraws,retainedBytes:backend.stats.retainedResourceBytes,activeChunks:backend.stats.activeChunks,overlayMode:backend.stats.overlayCoverageMode,validationPasses:backend.stats.streamUploadValidationPassed,queryCreated,queryDeleted};
  assert(warm.javaFrames===1&&warm.javaWorldFrames===1&&warm.draws>0&&warm.worldDrawCalls>0&&warm.skyDraws>0&&warm.shadowDraws>0&&warm.retainedBytes>0&&warm.activeChunks===1&&warm.overlayMode===1&&warm.validationPasses===1,'Warm production passes were inactive: '+JSON.stringify(warm));
  const fresh=backend.stats.javaFrames,last=backend.stats.lastFrame,queriesBefore=queryCreated;draws=0;
  if(kind==='chunk'){fault='bufferSubData';await n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,1,vertices,indices,batches);}
  if(kind==='sky'){fault='texSubImage2D';await n.Java_orsc_BrowserWebGLBridge_skyFrame(null,sky,palette);}
  if(kind.startsWith('atlas')){await n.Java_orsc_BrowserWebGLBridge_planWorldSprites(null,1,1,new Int32Array([1,1,1]));fault=kind==='atlasPixels'?'texSubImage2D':'bufferSubData';await n.Java_orsc_BrowserWebGLBridge_commitWorldSprites(null,1,1,new Uint8Array([1,2,3,255]),new Float32Array(20));}
  if(kind==='coverage')fault='texSubImage2D';await coverage(2);
  let error='';try{await present();}catch(e){error=String(e);}assert(/Streaming upload GL error/.test(error),'Queued actual GL error not rejected: '+error);assert(draws===0,'Drew before rejected batch');assert(queryCreated===queriesBefore,'Rejected batch created timer query');assert(backend.stats.javaFrames===fresh&&backend.stats.lastFrame===last,'Published failed frame freshness');assert(backend.stats.backendFailed&&backend.stats.worldReadiness.phase==='error','Failure not sticky');
  let blocked=false;try{backend.queueStreamUploadValidation(false);}catch{blocked=true;}assert(blocked,'Mode flip revived backend');blocked=false;try{await n.Java_orsc_BrowserWebGLBridge_skyFrame(null,sky,palette);}catch{blocked=true;}assert(blocked,'Native call revived backend');
  backend.dispose();backend.dispose();assert(live.size===0,'Leaked GL handles: '+live.size);result.cases.push({kind,warm,queryCreated,queryDeleted,ownedState:true,zeroDraws:true,freshnessUnchanged:true,sticky:true,resourcesAfterDispose:live.size,error});raw.getExtension('WEBGL_lose_context')?.loseContext();canvas.remove();
 }
 result.passed=true;result.completed=true;
}catch(e){result.error=String(e?.stack||e);result.completed=true;}
document.querySelector('#status').textContent=JSON.stringify(result,null,2);
