import assert from 'node:assert/strict';
import {createBrowserOverlay} from '../../tools/browser-gameplay/web/browser-overlay.js';
function fakeGL(){
 const gl={},objects=new Set(),state=new Map(),enabled=new Set(),bindings=new Map(),attachments=new Map(),draws=[];let id=1;
 const names=['NO_ERROR','MAX_TEXTURE_SIZE','ACTIVE_TEXTURE','TEXTURE_BINDING_2D','CURRENT_PROGRAM','VERTEX_ARRAY_BINDING','VIEWPORT','DEPTH_WRITEMASK','COLOR_WRITEMASK','DEPTH_TEST','BLEND','CULL_FACE','SCISSOR_TEST','STENCIL_TEST','RASTERIZER_DISCARD','DITHER','SAMPLE_ALPHA_TO_COVERAGE','SAMPLE_COVERAGE','VERTEX_SHADER','FRAGMENT_SHADER','COMPILE_STATUS','LINK_STATUS','TEXTURE_2D','NEAREST','CLAMP_TO_EDGE','TEXTURE_MIN_FILTER','TEXTURE_MAG_FILTER','TEXTURE_WRAP_S','TEXTURE_WRAP_T','RGBA8','DRAW_FRAMEBUFFER','READ_FRAMEBUFFER','DRAW_FRAMEBUFFER_BINDING','READ_FRAMEBUFFER_BINDING','COLOR_ATTACHMENT0','FRAMEBUFFER_COMPLETE','TRIANGLES','R32F','RED','RGBA','FLOAT','UNSIGNED_BYTE','UNPACK_ALIGNMENT','BLEND_SRC_RGB','BLEND_DST_RGB','BLEND_SRC_ALPHA','BLEND_DST_ALPHA','BLEND_EQUATION_RGB','BLEND_EQUATION_ALPHA','FUNC_ADD','ONE','ZERO','ONE_MINUS_SRC_ALPHA'];names.forEach((n,i)=>gl[n]=i+1);gl.TEXTURE0=100;
 state.set(gl.MAX_TEXTURE_SIZE,4096);state.set(gl.ACTIVE_TEXTURE,104);state.set(gl.CURRENT_PROGRAM,{external:true});state.set(gl.VERTEX_ARRAY_BINDING,{external:true});state.set(gl.DRAW_FRAMEBUFFER_BINDING,{external:'draw'});state.set(gl.READ_FRAMEBUFFER_BINDING,{external:'read'});state.set(gl.VIEWPORT,[7,9,20,30]);state.set(gl.DEPTH_WRITEMASK,true);state.set(gl.COLOR_WRITEMASK,[false,true,false,true]);for(const n of ['DEPTH_TEST','BLEND','CULL_FACE','SCISSOR_TEST','STENCIL_TEST','DITHER','SAMPLE_ALPHA_TO_COVERAGE','SAMPLE_COVERAGE'])enabled.add(gl[n]);
 function live(o){assert(!o||o.external||objects.has(o),'binding deleted resource');}
 for(const noun of ['Shader','Program','Texture','VertexArray','Framebuffer']){gl['create'+noun]=()=>{const o={noun,id:id++};objects.add(o);return o;};gl['delete'+noun]=o=>{if(!o)return;assert(objects.delete(o),'double deletion '+noun);if(noun==='Texture')for(const [k,v]of bindings)if(v===o)bindings.set(k,null);if(noun==='Framebuffer')for(const p of [gl.DRAW_FRAMEBUFFER_BINDING,gl.READ_FRAMEBUFFER_BINDING])if(state.get(p)===o)state.set(p,null);};}
 gl.getParameter=p=>p===gl.TEXTURE_BINDING_2D?bindings.get(state.get(gl.ACTIVE_TEXTURE))||null:state.get(p);gl.activeTexture=v=>state.set(gl.ACTIVE_TEXTURE,v);gl.bindTexture=(_,o)=>{live(o);bindings.set(state.get(gl.ACTIVE_TEXTURE),o);};gl.bindVertexArray=o=>{live(o);state.set(gl.VERTEX_ARRAY_BINDING,o);};gl.useProgram=o=>{live(o);state.set(gl.CURRENT_PROGRAM,o);};gl.bindFramebuffer=(target,o)=>{live(o);state.set(target===gl.DRAW_FRAMEBUFFER?gl.DRAW_FRAMEBUFFER_BINDING:gl.READ_FRAMEBUFFER_BINDING,o);};
 gl.framebufferTexture2D=(_target,_attachment,_type,o)=>attachments.set(state.get(gl.DRAW_FRAMEBUFFER_BINDING),o);gl.checkFramebufferStatus=()=>gl.FRAMEBUFFER_COMPLETE;
 for(const n of ['shaderSource','compileShader','attachShader','linkProgram','texParameteri','texStorage2D'])gl[n]=()=>{};gl.getShaderParameter=gl.getProgramParameter=()=>true;gl.getShaderInfoLog=gl.getProgramInfoLog=()=>'';gl.getUniformLocation=(_p,name)=>name;const uniforms=new Map();gl.uniform1i=(p,v)=>uniforms.set(p,v);gl.uniform2f=(p,...v)=>uniforms.set(p,v);gl.getError=()=>gl.NO_ERROR;
 gl.isEnabled=c=>enabled.has(c);gl.enable=c=>enabled.add(c);gl.disable=c=>enabled.delete(c);gl.depthMask=v=>state.set(gl.DEPTH_WRITEMASK,v);gl.colorMask=(...v)=>state.set(gl.COLOR_WRITEMASK,v);gl.viewport=(...v)=>state.set(gl.VIEWPORT,v);
 gl.copyTexSubImage2D=()=>{const tex=gl.getParameter(gl.TEXTURE_BINDING_2D),fb=state.get(gl.READ_FRAMEBUFFER_BINDING);assert.notEqual(attachments.get(fb),tex,'capture texture feedback');gl.lastCopy={tex,fb};};
 gl.pixelStorei=()=>{};gl.texImage2D=(_a,_b,format,w,h,_border,_format,_type,data)=>{const tex=gl.getParameter(gl.TEXTURE_BINDING_2D);tex.format=format;tex.pixels=data?.slice();};gl.texSubImage2D=(_a,_b,_x,_y,_w,_h,_format,_type,data)=>{gl.getParameter(gl.TEXTURE_BINDING_2D).pixels=data.slice();};gl.blendFuncSeparate=(...v)=>[gl.BLEND_SRC_RGB,gl.BLEND_DST_RGB,gl.BLEND_SRC_ALPHA,gl.BLEND_DST_ALPHA].forEach((p,n)=>state.set(p,v[n]));gl.blendEquationSeparate=(rgb,alpha)=>{state.set(gl.BLEND_EQUATION_RGB,rgb);state.set(gl.BLEND_EQUATION_ALPHA,alpha);};gl.readPixels=()=>{throw new Error('Unexpected CPU readback');};
 gl.drawArrays=()=>{const tex=bindings.get(gl.TEXTURE0),fb=state.get(gl.DRAW_FRAMEBUFFER_BINDING);assert.notEqual(attachments.get(fb),tex,'draw texture feedback');draws.push({tex,fb,count:uniforms.get('fadeCount'),transparent:uniforms.get('transparent'),blend:enabled.has(gl.BLEND),blendRgb:[state.get(gl.BLEND_SRC_RGB),state.get(gl.BLEND_DST_RGB)]});};return {gl,state,objects,bindings,enabled,draws,attachments};
}
const x=fakeGL(),stats={},m=createBrowserOverlay(x.gl,{stats});
const t=new Float32Array([1,.5,0,.25]),rgb=new Uint8Array(16);rgb[0]=128;
m.prepare(2,2,1,1,1,0,true,t);t.fill(0);const mask=[...x.objects].find(o=>o.format===x.gl.R32F);assert.deepEqual([...mask.pixels],[1,.5,0,.25],'borrowed T escaped synchronous upload');
assert.equal(m.begin(2,2),false);m.draw(2,2,rgb);assert.equal(stats.retainedCaptures,1);const heldFull=x.gl.lastCopy.tex,heldCopies=stats.retainedCaptures;m.hold(2,2);assert.equal(x.draws.at(-1).tex,heldFull);assert.equal(stats.retainedCaptures,heldCopies,'stream hold copied pixels again');assert.equal(stats.overlayCoverageBytes,16);assert.equal(stats.overlayRgbBytes,16);
assert.deepEqual(x.draws.at(-2).blendRgb,[x.gl.ONE,x.gl.ONE_MINUS_SRC_ALPHA]);assert.equal(x.draws.at(-2).transparent,1);
function frame(mode,seq,fades=0,generation=1,reset=false){m.prepare(2,2,mode,generation,seq,fades,reset,new Float32Array(4).fill(1));const retained=m.begin(2,2);m.draw(2,2,rgb);return retained;}
let before=x.draws.length;assert.equal(frame(3,2,1),true);assert.deepEqual(x.draws.slice(before).map(d=>d.count),[1,0,0]);
before=x.draws.length;assert.equal(frame(3,2,1),true);assert.deepEqual(x.draws.slice(before).map(d=>d.count),[0,0],'duplicate frame refaded FULL');
before=x.draws.length;assert.equal(frame(2,3),true);assert.equal(x.draws.length-before,2,'BASE replay included prior delta twice');
assert.equal(frame(0,4),false);assert.equal(x.draws.at(-1).transparent,0);assert.equal(x.draws.at(-1).blend,false);assert.equal(stats.retainedResourceBytes,0);
assert.equal(frame(1,1,0,0,true),false,'new pixel-owner epoch did not reset retained token');
assert.throws(()=>m.prepare(2,2,1,0,2,0,false,new Float32Array(3)),/plane/);assert.throws(()=>m.prepare(2,2,4,0,2,0,false,new Float32Array(4)),/mode/);
m.prepare(2,2,1,0,2,0,false,new Float32Array(4));assert.throws(()=>m.hold(2,2),/partially/);assert.throws(()=>m.prepare(2,2,1,0,2,0,false,new Float32Array(4)),/consumed/);assert.throws(()=>m.begin(3,2),/declaration/);m.begin(2,2);m.draw(2,2,rgb);
m.dispose();m.dispose();assert.equal(x.objects.size,0);assert.equal(stats.overlayCoverageBytes,0);assert.equal(stats.overlayRgbBytes,0);assert.throws(()=>m.begin(2,2),/unavailable/);
const f=fakeGL(),failed=createBrowserOverlay(f.gl);failed.prepare(2,2,1,1,1,0,false,new Float32Array(4));failed.begin(2,2);f.gl.texImage2D=()=>{throw new Error('upload failure');};assert.throws(()=>failed.draw(2,2,rgb),/upload/);failed.dispose();assert.equal(f.objects.size,0);
const large=fakeGL(),budget={},bounded=createBrowserOverlay(large.gl,{stats:budget});
for(const [w,h,seq]of [[2048,1024,1],[1024,2048,2]]){bounded.prepare(w,h,1,seq,seq,0,false,new Float32Array(w*h));assert.equal(bounded.begin(w,h),false);bounded.draw(w,h,new Uint8Array(w*h*4));assert.equal(budget.overlayCompositeResourceBytes,32*1024*1024);assert(budget.overlayCompositePeakBytes<=32*1024*1024,'combined C/T/retained replacement exceeded32MiB');}
assert.equal(budget.overlayCompositePeakBytes,32*1024*1024);bounded.dispose();assert.equal(large.objects.size,0);
// Browser opt-in retains actual opaque game UI too; standalone opaque behavior above is unchanged.
const opaque=fakeGL(),opaqueStats={},opaqueOverlay=createBrowserOverlay(opaque.gl,{stats:opaqueStats,retainOpaqueFull:true});
for(let seq=1;seq<=3;seq++){
 opaqueOverlay.prepare(2,2,seq===1?1:0,seq===1?3:0,seq,0,false,new Float32Array(4));opaqueOverlay.begin(2,2);opaqueOverlay.draw(2,2,new Uint8Array(16));
 const source=opaque.gl.lastCopy.tex,objects=opaque.objects.size,copies=opaqueStats.retainedCaptures;opaqueOverlay.hold(2,2);assert.equal(opaque.draws.at(-1).tex,source);assert.equal(opaqueStats.retainedCaptures,copies);assert.equal(opaque.objects.size,objects);
 assert.equal(opaqueStats.retainedResourceBytes,32);assert(opaqueStats.overlayCompositeResourceBytes<=128);
}
opaqueOverlay.prepare(2,2,1,4,4,0,false,new Float32Array(4));opaqueOverlay.begin(2,2);opaqueOverlay.draw(2,2,new Uint8Array(16));opaqueOverlay.hold(2,2);opaqueOverlay.dispose();assert.equal(opaque.objects.size,0);
// Physical FULL/BASE replacement peak includes BOTH old/new pairs plus logical C/T.
const physical=fakeGL(),physicalStats={},physicalOverlay=createBrowserOverlay(physical.gl,{stats:physicalStats,retainOpaqueFull:true,physicalOutput:true});
for(const [ow,oh,seq]of [[2048,1024,1],[1024,2048,2]]){
 physicalOverlay.prepare(2048,1024,1,seq,seq,0,false,new Float32Array(2097152));const chosen=physicalOverlay.outputSize(2048,1024,ow,oh);physical.gl.viewport(0,0,ow,oh);physicalOverlay.begin(2048,1024,chosen.width,chosen.height);physicalOverlay.draw(2048,1024,new Uint8Array(8388608));assert.equal(physicalStats.overlayRgbBytes,8388608);assert.equal(physicalStats.overlayCoverageBytes,8388608);assert.equal(physicalStats.retainedResourceBytes,16777216);
}
assert.equal(physicalStats.overlayCompositePeakBudgetBytes,48*1024*1024);assert.equal(physicalStats.overlayCompositePeakBytes,48*1024*1024,'physical replacement peak omitted old/new pair');physicalOverlay.dispose();assert.equal(physical.objects.size,0);
// Bounded admission can reject a proposed frame AFTER C upload. A reset must
// retain the previous complete world+actors+UI until actual geometry is ready.
const streamed=fakeGL(),streamStats={},streamOverlay=createBrowserOverlay(streamed.gl,{stats:streamStats,retainOpaqueFull:true,deferRetainedReset:true});
streamOverlay.prepare(2,2,1,7,1,0,true,new Float32Array(4));streamOverlay.begin(2,2);streamOverlay.draw(2,2,rgb);
const oldFull=streamed.gl.lastCopy.tex,captures=streamStats.retainedCaptures;
streamOverlay.prepare(2,2,1,1,1,0,true,new Float32Array(4));streamOverlay.holdPrepared(2,2);
assert.equal(streamed.draws.at(-1).tex,oldFull);assert.equal(streamStats.retainedCaptures,captures);assert.equal(streamStats.retainedValid,true);
streamOverlay.prepare(2,2,1,1,2,0,false,new Float32Array(4));streamOverlay.holdPrepared(2,2);assert.equal(streamed.draws.at(-1).tex,oldFull);
streamOverlay.prepare(2,2,1,1,3,0,false,new Float32Array(4));streamOverlay.begin(2,2);streamOverlay.draw(2,2,rgb);assert.equal(streamStats.retainedCaptures,captures+1);
streamOverlay.prepare(2,2,3,1,4,1,false,new Float32Array(4));assert.equal(streamOverlay.retainedOutput,true);assert.equal(streamOverlay.begin(2,2),true);streamOverlay.draw(2,2,rgb);
streamOverlay.dispose();assert.equal(streamed.objects.size,0);
console.log('PASS coverage upload ownership, premultiplied C+T blend selection, exact retained FULL/BASE caller tokens, duplicate sequencing, owner reset, opaque fallback, bounds and owned cleanup. GL API double; no GPU proof.');
