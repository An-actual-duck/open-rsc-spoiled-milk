import {createBrowserOverlay} from './browser-overlay.js';
import {createOwnedBrowserFrameState} from './browser-owned-state.js';
import {createBrowserSpriteAtlas} from './browser-sprite-atlas.js';
const proof=window.overlayDeltaProof={schema:2,completed:false,passed:false,scope:'Actual unchanged production shader: FULL/ROW/direct minimal-view/packed pixel equality; diagnostic GPU readbacks, not FPS',frames:[],bytesCompared:0,errors:[]};
let gl,program,vs,fs,vao,atlas,atlasTexture,atlasVbo,readFbo,sentinel,pbo;const modules=[];
function assert(ok,why){if(!ok)throw new Error(why);}
function canonical(){for(const[p,v]of [[gl.UNPACK_ROW_LENGTH,0],[gl.UNPACK_SKIP_ROWS,0],[gl.UNPACK_SKIP_PIXELS,0],[gl.UNPACK_ALIGNMENT,4],[gl.UNPACK_FLIP_Y_WEBGL,false],[gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,false]])gl.pixelStorei(p,v);gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,null);}
function unpackState(){return [gl.UNPACK_ROW_LENGTH,gl.UNPACK_SKIP_ROWS,gl.UNPACK_SKIP_PIXELS,gl.UNPACK_ALIGNMENT,gl.UNPACK_FLIP_Y_WEBGL,gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,gl.PIXEL_UNPACK_BUFFER_BINDING].map(p=>gl.getParameter(p));}
function rowPlan(f){
 if(!f.delta||f.work.length===6)return f;
 const work=f.work.slice(),cols=work[13],rows=work[14],bands=[];let open=false,total=0;
 for(let y=0;y<rows;y++){
  let touched=false;for(let x=0;x<cols;x++){const id=y*cols+x;if((work[15+(id>>4)]&(1<<(id&15)))!==0){touched=true;break;}}
  if(touched){const lo=y*16,hi=Math.min(f.height,lo+16);total+=hi-lo;if(!open)bands.push(lo,hi);else bands[bands.length-1]=hi;open=true;}else open=false;
 }
 const history=f.partial,partial=history&&bands.length<=8&&total*5<=f.height*3;
 work[6]=1;work[3]=partial?total:f.height;work[4]=partial?bands.length/2:1;work[5]=Number(partial);
 return {...f,partial,bands:partial?bands:[0,f.height],work,baseGeneration:partial?f.baseGeneration:-1,baseFrameSequence:partial?f.baseFrameSequence:-1};
}
try{
 const options=new URLSearchParams(location.search),scale=Number(options.get('scale')||1),odd=options.get('odd')==='1',owned=options.get('owned')!=='false',hostile=options.get('hostile')==='1';
 assert((options.get('alpha')||'true')==='true','This oracle requires game-style alpha:true');
 assert(Number.isFinite(scale)&&scale>=1&&scale<=4,'Invalid physical scale');assert(!owned||!hostile,'Hostile external state is only valid for the scoped contract');
 const canvas=document.querySelector('#gpu');gl=canvas.getContext('webgl2',{alpha:true,antialias:false,depth:true,preserveDrawingBuffer:false,powerPreference:'high-performance'});assert(gl,'WebGL2 unavailable');
 const ext=gl.getExtension('WEBGL_debug_renderer_info');proof.renderer=gl.getParameter(ext?ext.UNMASKED_RENDERER_WEBGL:gl.RENDERER);proof.vendor=gl.getParameter(ext?ext.UNMASKED_VENDOR_WEBGL:gl.VENDOR);
 proof.configuration={alpha:true,scale,odd,owned,hostile,context:gl.getContextAttributes()};proof.borrowMutationChecks=proof.hostileRestoreChecks=proof.followOnAtlasChecks=proof.followOnFullChecks=proof.deferredOutputChecks=0;
 const owner=owned?createOwnedBrowserFrameState(gl,{enabled:true,ownOverlayUnpack:true}):null;
 function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);assert(gl.getShaderParameter(s,gl.COMPILE_STATUS),gl.getShaderInfoLog(s));return s;}
 vs=shader(gl.VERTEX_SHADER,'#version 300 es\nprecision highp float;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float((gl_VertexID)&2));gl_Position=vec4(p*2.0-1.0,0,1);}');
 fs=shader(gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;precision highp int;out vec4 color;void main(){ivec2 p=ivec2(gl_FragCoord.xy);color=vec4(float((p.x*13+p.y*7)%256),float((p.x*3+p.y*19)%256),float((p.x*23+p.y*5)%256),255.0)/255.0;}');
 program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);assert(gl.getProgramParameter(program,gl.LINK_STATUS),gl.getProgramInfoLog(program));vao=gl.createVertexArray();
 for(const[name,mode]of [['full','off'],['row','observe'],['direct','on'],['packed','packed']]){
  const stats={},module=createBrowserOverlay(gl,{stats,ownedFrame:owner,retainOpaqueFull:true,physicalOutput:true,deltaTransport:name!=='full',overlayRectMode:mode,maxTextureSize:gl.getParameter(gl.MAX_TEXTURE_SIZE)});
  modules.push({name,module,stats});
 }
 canonical();atlasTexture=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,atlasTexture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,8,8,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
 atlasVbo=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,atlasVbo);gl.bufferData(gl.ARRAY_BUFFER,80,gl.DYNAMIC_DRAW);atlas=createBrowserSpriteAtlas(gl,{texture:atlasTexture,vbo:atlasVbo,extent:8,maxSprites:1,ownedFrame:owner});
 readFbo=gl.createFramebuffer();sentinel=gl.createTexture();pbo=gl.createBuffer();let atlasEpoch=0;
 function readTexture(texture,w,h){gl.bindFramebuffer(gl.FRAMEBUFFER,readFbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);assert(gl.checkFramebufferStatus(gl.FRAMEBUFFER)===gl.FRAMEBUFFER_COMPLETE,'Follow-on FBO incomplete');const out=new Uint8Array(w*h*4);gl.readPixels(0,0,w,h,gl.RGBA,gl.UNSIGNED_BYTE,out);gl.bindFramebuffer(gl.FRAMEBUFFER,null);return out;}
 function followOn(){
  const state=unpackState();assert(state.every((v,n)=>v===[0,0,0,4,false,false,null][n]),'Unpack state leaked into following upload');
  const bytes=Uint8Array.from({length:24},(_,n)=>(n*29+17)%256);atlasEpoch++;atlas.plan(atlasEpoch,1,new Int32Array([atlasEpoch,3,2]));atlas.commit(atlasEpoch,1,bytes,new Float32Array(20));
  assert(readTexture(atlasTexture,3,2).every((v,n)=>v===bytes[n]),'Following actual atlas upload differs');proof.followOnAtlasChecks++;
  const storage=new Uint8Array(64),view=new Uint8Array(storage.buffer,1,60);for(let n=0;n<60;n++)view[n]=(n*13+91)%256;
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,sentinel);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,5,3,0,gl.RGBA,gl.UNSIGNED_BYTE,view);
  assert(readTexture(sentinel,5,3).every((v,n)=>v===view[n]),'Following full raw upload differs');proof.followOnFullChecks++;assert(gl.getError()===gl.NO_ERROR,'Follow-on GL error');
 }
 function pattern(){gl.useProgram(program);gl.bindVertexArray(vao);gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);gl.disable(gl.SCISSOR_TEST);gl.depthMask(false);gl.drawArrays(gl.TRIANGLES,0,3);gl.depthMask(true);}
 const manifest=await(await fetch('./data/manifest.json')).json();assert(manifest.schema===1&&manifest.frames.length>100,'Missing literal Java rectangle corpus');proof.manifestFrameCount=manifest.frames.length;
 function draw(entry,f,sourceRgb,sourceT){
  const {module,name}=entry,w=f.width,h=f.height,packet=name==='row'?rowPlan(f):f;
  // Offset1 exercises unaligned Uint8 views; T offset12 remains Float32-aligned.
  const rgb=new Uint8Array(new ArrayBuffer(sourceRgb.length+3),1,sourceRgb.length);rgb.set(sourceRgb);
  let t=null;if(sourceT){t=new Float32Array(new ArrayBuffer(sourceT.byteLength+12),12,sourceT.length);new Uint8Array(t.buffer,12,t.byteLength).set(new Uint8Array(sourceT.buffer,sourceT.byteOffset,sourceT.byteLength));}
  const stress=hostile&&(name==='direct'||name==='packed')&&packet.delta;
  canonical();let prior;
  if(stress){gl.pixelStorei(gl.UNPACK_ROW_LENGTH,7);gl.pixelStorei(gl.UNPACK_SKIP_ROWS,1);gl.pixelStorei(gl.UNPACK_SKIP_PIXELS,1);gl.pixelStorei(gl.UNPACK_ALIGNMENT,8);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,true);gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,pbo);prior=unpackState();}
  if(name!=='full'&&packet.delta)module.prepareDelta(w,h,packet.generation,packet.frameSequence,packet.reset,packet.partial,packet.baseGeneration,packet.baseFrameSequence,new Int32Array(packet.bands),new Int32Array(packet.work),rgb,t);
  else module.prepare(w,h,packet.mode,packet.generation,packet.frameSequence,packet.fades,packet.reset,t);
  if(stress){assert(unpackState().every((v,n)=>v===prior[n]),'Scoped hostile state was not restored');proof.hostileRestoreChecks++;canonical();}
  if((name==='direct'||name==='packed')&&packet.delta&&(f.name==='first-world'||f.name==='moving-vfx-3')){rgb.fill(255);t.fill(0);proof.borrowMutationChecks++;}
  const extra=f.mode===2||f.mode===3?2:0,desired={width:Math.floor(w*scale)+(odd?1:0)+extra,height:Math.floor(h*scale)+(odd?1:0)+extra};
  const output=module.outputSize(w,h,desired.width,desired.height);if(canvas.width!==output.width)canvas.width=output.width;if(canvas.height!==output.height)canvas.height=output.height;
  gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,output.width,output.height);gl.disable(gl.SCISSOR_TEST);gl.colorMask(true,true,true,true);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);if(owner)owner.begin(output.width,output.height,0,0,0);
  const retained=module.begin(w,h,output.width,output.height);if(!retained)pattern();module.draw(w,h,rgb);
  const pixels=new Uint8Array(output.width*output.height*4);gl.readPixels(0,0,output.width,output.height,gl.RGBA,gl.UNSIGNED_BYTE,pixels);assert(gl.getError()===gl.NO_ERROR,'Frame GL error '+name+'/'+f.name);
  if(name==='direct'||name==='packed')if(f.name==='first-world'||f.name==='moving-vfx-3'||f.name==='odd-sparse-tall-0'||f.name==='fragment-full')followOn();
  return {pixels,output};
 }
 for(const f of manifest.frames){
  const rgb=new Uint8Array(await(await fetch('./data/'+f.id+'-rgb.bin')).arrayBuffer()),t=f.mode===0?null:new Float32Array(await(await fetch('./data/'+f.id+'-t.bin')).arrayBuffer());
  let reference;const comparisons=[];
  for(const entry of modules){const actual=draw(entry,f,rgb,t);if(entry.name==='full'){reference=actual;continue;}
   assert(actual.output.width===reference.output.width&&actual.output.height===reference.output.height&&actual.output.deferred===reference.output.deferred,'Output dimensions/defer mismatch '+entry.name+'/'+f.name);
   let changed=0;for(let n=0;n<actual.pixels.length;n++)if(actual.pixels[n]!==reference.pixels[n])changed++;
   proof.bytesCompared+=actual.pixels.length;comparisons.push({variant:entry.name,changedBytes:changed});assert(changed===0,'Pixel mismatch '+entry.name+'/'+f.name+': '+changed);
  }
  if(reference.output.deferred)proof.deferredOutputChecks++;
  proof.frames.push({name:f.name,logical:[f.width,f.height],output:[reference.output.width,reference.output.height],deferred:reference.output.deferred===true,comparisons});
 }
 // An actual held-frame replay consumes no new Java source or delta declaration.
 let heldReference;proof.heldComparisons=0;
 for(const entry of modules){gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,canvas.width,canvas.height);gl.clear(gl.COLOR_BUFFER_BIT);entry.module.hold(canvas.width,canvas.height);const out=new Uint8Array(canvas.width*canvas.height*4);gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,out);if(!heldReference)heldReference=out;else {assert(out.every((v,n)=>v===heldReference[n]),'Held FULL mismatch '+entry.name);proof.heldComparisons++;}assert(gl.getError()===gl.NO_ERROR,'Held GL error');}
 assert(proof.borrowMutationChecks===4,'Post-upload borrow mutation checks missing');assert(proof.deferredOutputChecks>=2,'Retained resize deferrals missing');assert(proof.followOnAtlasChecks>=4&&proof.followOnFullChecks>=4,'Following upload checks missing');
 for(const entry of modules){proof[entry.name+'Stats']={...entry.stats};if(entry.name==='direct'||entry.name==='packed')assert(entry.stats.overlayDeltaRectDeclarations>10,'Rectangle path not exercised '+entry.name);}
 assert(proof.packedStats.overlayGatheredBytes>0&&proof.packedStats.overlayDeltaSourceViewSpanBytes===proof.packedStats.overlayDeltaUploadedBytes,'Packed API spans are not tight logical texels');
 assert(proof.directStats.overlayDeltaSourceViewSpanBytes>=proof.directStats.overlayDeltaUploadedBytes,'Direct span counter inconsistent');
 proof.passed=true;
}catch(error){proof.errors.push(String(error)+(error.stack?'\n'+String(error.stack):''));}
finally{
 try{for(const entry of modules)entry.module.dispose();atlas?.dispose();if(gl){for(const x of [atlasTexture,sentinel])if(x)gl.deleteTexture(x);for(const x of [atlasVbo,pbo])if(x)gl.deleteBuffer(x);if(readFbo)gl.deleteFramebuffer(readFbo);if(vao)gl.deleteVertexArray(vao);if(program)gl.deleteProgram(program);if(vs)gl.deleteShader(vs);if(fs)gl.deleteShader(fs);}}catch(error){proof.passed=false;proof.errors.push('cleanup: '+String(error));}
 proof.completed=true;document.querySelector('#status').textContent=JSON.stringify(proof,null,2);
}
