import {WIDTH as W,HEIGHT as H,MODES,validateRectangles,rowBands,pack,makeSources,metric} from './transfer.mjs';
const $=id=>document.getElementById(id);let result;const raf=()=>new Promise(r=>requestAnimationFrame(r));
const fail=message=>{throw Error(message);};
function check(gl,where){const error=gl.getError();if(error!==gl.NO_ERROR)fail(`${where}: GL ${error}`);}
function shader(gl,type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const error=gl.getShaderInfoLog(s);gl.deleteShader(s);fail(error);}return s;}
function summary(values){const s=[...values].sort((a,b)=>a-b),at=p=>s[Math.min(s.length-1,Math.floor(p*s.length))];return {n:s.length,mean:s.reduce((a,b)=>a+b,0)/s.length,p50:at(.5),p95:at(.95),p99:at(.99),max:s.at(-1)};}
async function run(){
 const started=new Date().toISOString(),gl=$('canvas').getContext('webgl2',{antialias:false,preserveDrawingBuffer:false});if(!gl)fail('WebGL2 unavailable');
 const ext=gl.getExtension('WEBGL_debug_renderer_info');if(!ext)fail('Unmasked renderer unavailable; cannot reject software fallback');
 const renderer=gl.getParameter(ext.UNMASKED_RENDERER_WEBGL),vendor=gl.getParameter(ext.UNMASKED_VENDOR_WEBGL);
 if(/llvmpipe|softpipe|swiftshader|software|swrast|lavapipe|microsoft basic/i.test(renderer+' '+vendor))fail('Software renderer rejected: '+renderer);
 const input=await fetch('./layouts.json',{cache:'no-store'}).then(r=>{if(!r.ok)fail('Missing prepared layouts');return r.json();});
 if(!input.layouts?.length||input.layouts.length>32)fail('Layout bound');for(const l of input.layouts)validateRectangles(l.rectangles);
 const textures=[],programs=[],shaders=[],fbos=[],vaos=[];let saved;
 const canonical=()=>{gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,null);gl.pixelStorei(gl.UNPACK_ALIGNMENT,4);gl.pixelStorei(gl.UNPACK_ROW_LENGTH,0);gl.pixelStorei(gl.UNPACK_SKIP_ROWS,0);gl.pixelStorei(gl.UNPACK_SKIP_PIXELS,0);gl.pixelStorei(gl.UNPACK_IMAGE_HEIGHT,0);gl.pixelStorei(gl.UNPACK_SKIP_IMAGES,0);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,false);gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL,gl.NONE);};
 const texture=(internal,format,type)=>{const t=gl.createTexture();textures.push(t);gl.bindTexture(gl.TEXTURE_2D,t);gl.texStorage2D(gl.TEXTURE_2D,1,internal,W,H);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);return {texture:t,format,type};};
 try{
 canonical();gl.activeTexture(gl.TEXTURE0);const ctex=texture(gl.RGBA8,gl.RGBA,gl.UNSIGNED_BYTE),ttex=texture(gl.R32F,gl.RED,gl.FLOAT),output=texture(gl.RGBA8UI,gl.RGBA_INTEGER,gl.UNSIGNED_BYTE);
 const fbo=gl.createFramebuffer();fbos.push(fbo);gl.bindFramebuffer(gl.FRAMEBUFFER,fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,output.texture,0);if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)fail('Integer readback framebuffer incomplete');
 const vertex=shader(gl,gl.VERTEX_SHADER,'#version 300 es\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}');
 shaders.push(vertex);const fragment=shader(gl,gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;precision highp int;uniform highp sampler2D source;uniform int plane;layout(location=0)out highp uvec4 color;void main(){vec4 v=texelFetch(source,ivec2(gl_FragCoord.xy),0);if(plane==0){color=uvec4(round(v*255.0));}else{uint n=floatBitsToUint(v.r);color=uvec4(n&255u,(n>>8u)&255u,(n>>16u)&255u,n>>24u);}}');shaders.push(fragment);
 const program=gl.createProgram();programs.push(program);gl.attachShader(program,vertex);gl.attachShader(program,fragment);gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))fail(gl.getProgramInfoLog(program));gl.useProgram(program);gl.uniform1i(gl.getUniformLocation(program,'source'),0);const planeLocation=gl.getUniformLocation(program,'plane');const vao=gl.createVertexArray();vaos.push(vao);gl.bindVertexArray(vao);gl.viewport(0,0,W,H);gl.disable(gl.BLEND);gl.disable(gl.DITHER);gl.pixelStorei(gl.PACK_ALIGNMENT,4);gl.bindBuffer(gl.PIXEL_PACK_BUFFER,null);check(gl,'setup');
 let sources,prepacked;
 // Keep one captured layout's owned sources at a time; bounded two-phase planes.
 // Prepacked control is explicitly prepared outside timing; ordinary packed work is not.
 const setup=k=>{sources=makeSources(input.layouts[k].rectangles);prepacked=sources.map(frame=>input.layouts[k].rectangles.map(r=>({c:pack(frame.c,r,4,new Uint8Array((r[2]-r[0])*(r[3]-r[1])*4)).slice(),t:pack(frame.t,r,1,new Float32Array((r[2]-r[0])*(r[3]-r[1]))).slice()})));};
 const maxArea=Math.max(...input.layouts.flatMap(l=>l.rectangles.map(r=>(r[2]-r[0])*(r[3]-r[1])))),stagingC=new Uint8Array(maxArea*4),stagingT=new Float32Array(maxArea),pixels=new Uint8Array(W*H*4);
 const upload=(mode,k,phase)=>{const layout=input.layouts[k],frame=sources[phase],rs=metric(layout.rectangles,mode).rectangles;let packingMs=0,viewMs=0;
  for(const [plane,tex,data,channels,staging]of [[0,ctex,frame.c,4,stagingC],[1,ttex,frame.t,1,stagingT]]){
   gl.bindTexture(gl.TEXTURE_2D,tex.texture);gl.pixelStorei(gl.UNPACK_ROW_LENGTH,mode==='strided'?W:0);
   for(let i=0;i<rs.length;i++){const r=rs[i],[x,y,x1,y1]=r;let view;const start=performance.now();
    if(mode==='packed'){view=pack(data,r,channels,staging);packingMs+=performance.now()-start;}
    else if(mode==='prepacked'){view=prepacked[phase][i][plane?'t':'c'];viewMs+=performance.now()-start;}
    else{view=data.subarray((y*W+x)*channels,((y1-1)*W+x1)*channels);viewMs+=performance.now()-start;}
    gl.texSubImage2D(gl.TEXTURE_2D,0,x,y,x1-x,y1-y,tex.format,tex.type,view);
   }
   gl.pixelStorei(gl.UNPACK_ROW_LENGTH,0);check(gl,`${mode} plane${plane}`); // Same per-plane policy, never per rectangle.
  }return {packingMs,viewMs};
 };
 const verify=(tex,plane,expected)=>{gl.bindTexture(gl.TEXTURE_2D,tex.texture);gl.uniform1i(planeLocation,plane);gl.drawArrays(gl.TRIANGLES,0,3);gl.readPixels(0,0,W,H,gl.RGBA_INTEGER,gl.UNSIGNED_BYTE,pixels);check(gl,'oracle read');for(let i=0;i<pixels.length;i++)if(pixels[i]!==expected[i])fail(`Exact GPU plane${plane} byte${i}: ${pixels[i]} != ${expected[i]}`);};
 const expectedT=data=>{const out=new Uint8Array(data.length*4),v=new DataView(out.buffer);for(let i=0;i<data.length;i++)v.setFloat32(i*4,data[i],true);return out;};
 $('status').textContent='Exact GPU checks (all layouts and variants)';let comparisons=0;
 for(let k=0;k<input.layouts.length;k++){setup(k);for(const mode of MODES){await raf();if(document.visibilityState!=='visible'||!document.hasFocus())fail('Not visible/focused');upload('full',k,0);upload(mode,k,1);verify(ctex,0,sources[1].c);verify(ttex,1,expectedT(sources[1].t));comparisons+=W*H*8;}}
 // No timing starts unless every C byte and every Float32 T bit matched CPU/FULL.
 const sortedLayouts=input.layouts.map((l,i)=>({i,area:metric(l.rectangles,'packed').logicalTexelBytes})).sort((a,b)=>a.area-b.area);const timedLayout=sortedLayouts[Math.floor(sortedLayouts.length/2)].i;
 setup(timedLayout);const cohorts=[];const orders=[MODES,[...MODES].reverse()];
 for(let order=0;order<orders.length;order++)for(const mode of orders[order]){
  $('status').textContent=`Timing order${order+1}/2: ${mode}`;upload('full',timedLayout,0);await raf();const cpu=[],packing=[],views=[],intervals=[];let elapsed=0,last=await raf(),wallStart=performance.now(),count=0,totalLogical=0,totalSpans=0,calls=0;
  while(elapsed<2000){const now=await raf();if(document.visibilityState!=='visible'||!document.hasFocus())fail('Focus/visibility lost; discard timing');intervals.push(now-last);last=now;const k=timedLayout,phase=(count+1)&1;const start=performance.now(),p=upload(mode,k,phase);cpu.push(performance.now()-start);packing.push(p.packingMs);views.push(p.viewMs);for(const [tex,plane]of [[ctex,0],[ttex,1]]){gl.bindTexture(gl.TEXTURE_2D,tex.texture);gl.uniform1i(planeLocation,plane);gl.drawArrays(gl.TRIANGLES,0,3);}const m=metric(input.layouts[k].rectangles,mode);totalLogical+=m.logicalTexelBytes;totalSpans+=m.sourceViewSpanBytes;calls+=m.calls;count++;elapsed=performance.now()-wallStart;
  }
  cohorts.push({order,mode,frames:count,wallMs:elapsed,apiAndPackingCpuMs:summary(cpu),packingMs:summary(packing),viewCreationMs:summary(views),rafIntervalMs:summary(intervals),calls,logicalTexelBytes:totalLogical,sourceViewSpanBytes:totalSpans,stagedBytesCopied:mode==='packed'?totalLogical:0,consumerDrawsPerFrame:2});
 }
 saved={completed:true,publicChanged:false,started,finished:new Date().toISOString(),scope:'Standalone API submission CPU/wall timing only; NOT gameplay FPS, GPU timing, bus bytes or adoption evidence',renderer,vendor,version:gl.getParameter(gl.VERSION),userAgent:navigator.userAgent,width:W,height:H,manifest:input,exactGpuComparisons:comparisons,prepackedExcludedPreparation:true,reusableStagingBytes:stagingC.byteLength+stagingT.byteLength,syntheticSourceBytes:sources.reduce((n,f)=>n+f.c.byteLength+f.t.byteLength,0),timedLayoutIndex:timedLayout,baselineResetOncePerCohortOutsideTiming:true,consumerDrawsOutsideTimedApiButIncludedInRafWall:true,cohorts};return saved;
 }finally{canonical();gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.bindVertexArray(null);gl.useProgram(null);for(const x of textures)gl.deleteTexture(x);for(const x of fbos)gl.deleteFramebuffer(x);for(const x of vaos)gl.deleteVertexArray(x);for(const x of programs)gl.deleteProgram(x);for(const x of shaders)gl.deleteShader(x);}
}
$('start').onclick=async()=>{$('start').disabled=true;try{result=await run();$('status').textContent='Complete; synthetic reference only';}catch(e){result={completed:false,error:String(e),stack:e.stack,publicChanged:false};$('status').textContent='FAILED: '+e.message;}window.textureTransferResult=result;$('result').textContent=JSON.stringify(result,null,2);$('save').disabled=false;};
$('save').onclick=()=>{const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(result,null,2)],{type:'application/json'}));a.download='texture-transfer-result.json';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);};
