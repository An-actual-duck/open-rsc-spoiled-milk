import {createBrowserOverlay} from './browser-overlay.js';
const proof=window.overlayDeltaProof={completed:false,passed:false,scope:'Actual full-versus-delta production overlay shader output against literal Java RGB/T exports; GPU readbacks diagnostic only',frames:[],bytesCompared:0,errors:[]};
let full,delta,gl,program,vs,fs,vao;
function assert(ok,why){if(!ok)throw new Error(why);}
try{
 const options=new URLSearchParams(location.search),alphaValue=options.get('alpha')||'false';
 assert(['true','false'].includes(alphaValue),'alpha must be true or false');
 const alpha=alphaValue==='true',scale=Number(options.get('scale')||1),odd=options.get('odd')==='1',retainedResize=options.get('retainedresize')!=='off';
 assert(Number.isFinite(scale)&&scale>=1&&scale<=4,'scale must be between1and4');
 const canvas=document.querySelector('#gpu');gl=canvas.getContext('webgl2',{alpha,antialias:false,depth:true,preserveDrawingBuffer:false,powerPreference:'high-performance'});assert(gl,'WebGL2 unavailable');
 proof.configuration={alpha,scale,odd,retainedResize,context:gl.getContextAttributes()};proof.deferredOutputChecks=0;

 const ext=gl.getExtension('WEBGL_debug_renderer_info');proof.renderer=gl.getParameter(ext?ext.UNMASKED_RENDERER_WEBGL:gl.RENDERER);proof.vendor=gl.getParameter(ext?ext.UNMASKED_VENDOR_WEBGL:gl.VENDOR);
 function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);assert(gl.getShaderParameter(s,gl.COMPILE_STATUS),gl.getShaderInfoLog(s));return s;}
 vs=shader(gl.VERTEX_SHADER,'#version 300 es\nprecision highp float;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0,1);}');
 fs=shader(gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;precision highp int;out vec4 color;void main(){ivec2 p=ivec2(gl_FragCoord.xy);color=vec4(float((p.x*13+p.y*7)%256),float((p.x*3+p.y*19)%256),float((p.x*23+p.y*5)%256),255.0)/255.0;}');
 program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);assert(gl.getProgramParameter(program,gl.LINK_STATUS),gl.getProgramInfoLog(program));vao=gl.createVertexArray();
 const fullStats={},deltaStats={};full=createBrowserOverlay(gl,{stats:fullStats,retainOpaqueFull:true,physicalOutput:true});delta=createBrowserOverlay(gl,{stats:deltaStats,retainOpaqueFull:true,physicalOutput:true,deltaTransport:true,maxTextureSize:gl.getParameter(gl.MAX_TEXTURE_SIZE)});
 const manifest=await(await fetch('./data/manifest.json')).json();assert(manifest.schema===1&&manifest.frames.length>30,'Missing bounded Java plane corpus');
 function pattern(){gl.useProgram(program);gl.bindVertexArray(vao);gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);gl.disable(gl.SCISSOR_TEST);gl.depthMask(false);gl.drawArrays(gl.TRIANGLES,0,3);gl.depthMask(true);}
 function draw(module,f,rgb,t,isDelta){
  const w=f.width,h=f.height;
  if(isDelta&&f.delta)module.prepareDelta(w,h,f.generation,f.frameSequence,f.reset,f.partial,f.baseGeneration,f.baseFrameSequence,new Int32Array(f.bands),new Int32Array(f.work),rgb,t);
  else module.prepare(w,h,f.mode,f.generation,f.frameSequence,f.fades,f.reset,t);
  if(isDelta&&f.delta&&(f.name==='first-world'||f.name==='moving-vfx-3')){rgb.fill(255);t.fill(0);proof.borrowMutationChecks=(proof.borrowMutationChecks||0)+1;}
  const extra=retainedResize&&(f.mode===2||f.mode===3)?2:0;
  const desired={width:Math.floor(w*scale)+(odd?1:0)+extra,height:Math.floor(h*scale)+(odd?1:0)+extra};
  // Match presenter order: declaration -> choose logical/physical output ->
  // resize drawingbuffer -> begin selected output. Retained BASE/FADE may
  // deliberately defer requested output changes and keep their prior backing.
  const output=module.outputSize(w,h,desired.width,desired.height);
  if(canvas.width!==output.width)canvas.width=output.width;if(canvas.height!==output.height)canvas.height=output.height;
  gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,null);gl.bindFramebuffer(gl.READ_FRAMEBUFFER,null);gl.viewport(0,0,output.width,output.height);gl.disable(gl.SCISSOR_TEST);gl.colorMask(true,true,true,true);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);
  const retained=module.begin(w,h,output.width,output.height);if(!retained)pattern();module.draw(w,h,rgb);
  const out=new Uint8Array(output.width*output.height*4);gl.readPixels(0,0,output.width,output.height,gl.RGBA,gl.UNSIGNED_BYTE,out);assert(gl.getError()===gl.NO_ERROR,'Readback GL error '+f.name);return {pixels:out,output,desired};
 }
 for(const f of manifest.frames){
  const rgb=new Uint8Array(await(await fetch('./data/'+f.id+'-rgb.bin')).arrayBuffer());
  const t=f.mode===0?null:new Float32Array(await(await fetch('./data/'+f.id+'-t.bin')).arrayBuffer());
  const reference=draw(full,f,rgb,t,false),candidate=draw(delta,f,rgb,t,true),expected=reference.pixels,actual=candidate.pixels;let changed=0,maxError=0;
  assert(reference.output.width===candidate.output.width&&reference.output.height===candidate.output.height&&reference.output.deferred===candidate.output.deferred,'Output selection mismatch '+f.name);
  if(candidate.output.deferred){proof.deferredOutputChecks++;assert(f.mode===2||f.mode===3,'Unexpected output deferral '+f.name);}
  for(let n=0;n<expected.length;n++){const error=Math.abs(expected[n]-actual[n]);if(error)changed++;maxError=Math.max(maxError,error);}
  proof.bytesCompared+=expected.length;proof.frames.push({name:f.name,mode:f.mode,partial:f.partial,bands:f.bands,logical:[f.width,f.height],desired:[candidate.desired.width,candidate.desired.height],output:[candidate.output.width,candidate.output.height],deferred:candidate.output.deferred===true,changedBytes:changed,maxError});assert(!changed,'Pixel mismatch '+f.name+': '+changed+' bytes');
 }
 if(retainedResize)assert(proof.deferredOutputChecks>=2,'Retained output deferral was not exercised');
 assert((proof.borrowMutationChecks||0)===2,'Borrow mutation proof did not run');
 assert(deltaStats.overlayDeltaPartialDeclarations>10,'No actual partial transport');assert(deltaStats.overlayDeltaUploadedBytes<deltaStats.overlayDeltaFullEquivalentBytes,'No eligible bandwidth reduction');assert(deltaStats.overlayUploadBytes<fullStats.overlayUploadBytes,'Fallback/full transport erased total bandwidth reduction');
 proof.fullStats={...fullStats};proof.deltaStats={...deltaStats};proof.passed=true;
}catch(error){proof.errors.push(String(error.stack||error));}finally{
 try{full?.dispose();delta?.dispose();if(gl){if(vao)gl.deleteVertexArray(vao);if(program)gl.deleteProgram(program);if(vs)gl.deleteShader(vs);if(fs)gl.deleteShader(fs);}}catch(error){proof.passed=false;proof.errors.push('cleanup: '+String(error));}
 proof.completed=true;document.querySelector('#status').textContent=JSON.stringify(proof,null,2);
}
