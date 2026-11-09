import assert from 'node:assert/strict';
import {createBrowserLighting} from '../../tools/browser-gameplay/web/browser-lighting.js';

// Protocol/state/resource test only. Real shader pixels require the serialized
// manager GPU window and are explicitly not claimed by this instrumented API.
function mockGL(){
  let next=1000;const state=new Map(),enabled=new Set(),textures=new Map(),objects=new Set(),uniforms=new Map(),draws=[];
  const names=['NO_ERROR','MAX_TEXTURE_SIZE','ACTIVE_TEXTURE','TEXTURE_BINDING_2D','DRAW_FRAMEBUFFER_BINDING','READ_FRAMEBUFFER_BINDING','VIEWPORT','CURRENT_PROGRAM','VERTEX_ARRAY_BINDING','ARRAY_BUFFER_BINDING','COLOR_WRITEMASK','DEPTH_WRITEMASK','DEPTH_FUNC','DEPTH_CLEAR_VALUE','POLYGON_OFFSET_FACTOR','POLYGON_OFFSET_UNITS','TEXTURE_2D','TEXTURE0','TEXTURE3','TEXTURE5','TEXTURE7','VERTEX_SHADER','FRAGMENT_SHADER','COMPILE_STATUS','LINK_STATUS','TEXTURE_MIN_FILTER','TEXTURE_MAG_FILTER','LINEAR','NEAREST','TEXTURE_WRAP_S','TEXTURE_WRAP_T','CLAMP_TO_EDGE','TEXTURE_COMPARE_MODE','COMPARE_REF_TO_TEXTURE','TEXTURE_COMPARE_FUNC','LEQUAL','DEPTH_COMPONENT24','DEPTH_COMPONENT','UNSIGNED_INT','FRAMEBUFFER','DRAW_FRAMEBUFFER','READ_FRAMEBUFFER','DEPTH_ATTACHMENT','NONE','FRAMEBUFFER_COMPLETE','RGBA8','RGBA','UNSIGNED_BYTE','ARRAY_BUFFER','ELEMENT_ARRAY_BUFFER','FLOAT','DEPTH_BUFFER_BIT','DEPTH_TEST','BLEND','CULL_FACE','SCISSOR_TEST','POLYGON_OFFSET_FILL','LESS','TRIANGLES'];
  const gl={};for(let n=0;n<names.length;n++)gl[names[n]]=n+1;
  gl.TEXTURE0=200;gl.TEXTURE3=203;gl.TEXTURE5=205;gl.TEXTURE7=207;
  state.set(gl.MAX_TEXTURE_SIZE,4096);state.set(gl.ACTIVE_TEXTURE,gl.TEXTURE0);state.set(gl.VIEWPORT,[1,2,320,200]);state.set(gl.COLOR_WRITEMASK,[true,true,true,true]);state.set(gl.DEPTH_WRITEMASK,false);state.set(gl.DEPTH_FUNC,gl.LEQUAL);state.set(gl.DEPTH_CLEAR_VALUE,.7);state.set(gl.POLYGON_OFFSET_FACTOR,0);state.set(gl.POLYGON_OFFSET_UNITS,0);
  gl.getParameter=p=>p===gl.TEXTURE_BINDING_2D?(textures.get(state.get(gl.ACTIVE_TEXTURE))||null):(state.has(p)?state.get(p):null);
  gl.activeTexture=u=>state.set(gl.ACTIVE_TEXTURE,u);gl.bindTexture=(_,t)=>textures.set(state.get(gl.ACTIVE_TEXTURE),t);
  for(const noun of ['Shader','Program','Texture','Framebuffer','VertexArray']){gl['create'+noun]=()=>{const o={id:next++,noun};objects.add(o);return o;};gl['delete'+noun]=o=>{if(o)assert(objects.delete(o),'double deletion '+noun);};}
  for(const name of ['shaderSource','compileShader','attachShader','bindAttribLocation','linkProgram','texParameteri','drawBuffers','readBuffer','framebufferTexture2D','enableVertexAttribArray','vertexAttribPointer','clear'])gl[name]=()=>{};
  gl.texImage2D=(...args)=>{gl.lastUpload=args;};gl.getShaderParameter=gl.getProgramParameter=()=>true;gl.getShaderInfoLog=gl.getProgramInfoLog=()=>'';gl.checkFramebufferStatus=()=>gl.FRAMEBUFFER_COMPLETE;gl.getError=()=>gl.NO_ERROR;
  gl.getUniformLocation=(_,name)=>name;for(const name of ['uniform1i','uniform1f'])gl[name]=(location,value)=>uniforms.set(location,value);gl.uniformMatrix4fv=(location,transpose,matrix)=>uniforms.set(location,Array.from(matrix));
  gl.bindFramebuffer=(target,value)=>{if(target===gl.FRAMEBUFFER||target===gl.DRAW_FRAMEBUFFER)state.set(gl.DRAW_FRAMEBUFFER_BINDING,value);if(target===gl.FRAMEBUFFER||target===gl.READ_FRAMEBUFFER)state.set(gl.READ_FRAMEBUFFER_BINDING,value);};
  gl.useProgram=p=>state.set(gl.CURRENT_PROGRAM,p);gl.bindVertexArray=v=>state.set(gl.VERTEX_ARRAY_BINDING,v);gl.bindBuffer=(target,value)=>{if(target===gl.ARRAY_BUFFER)state.set(gl.ARRAY_BUFFER_BINDING,value);};gl.viewport=(...v)=>state.set(gl.VIEWPORT,v);
  gl.colorMask=(...v)=>state.set(gl.COLOR_WRITEMASK,v);gl.depthMask=v=>state.set(gl.DEPTH_WRITEMASK,v);gl.depthFunc=v=>state.set(gl.DEPTH_FUNC,v);gl.clearDepth=v=>state.set(gl.DEPTH_CLEAR_VALUE,v);gl.polygonOffset=(factor,units)=>{state.set(gl.POLYGON_OFFSET_FACTOR,factor);state.set(gl.POLYGON_OFFSET_UNITS,units);};
  gl.enable=v=>enabled.add(v);gl.disable=v=>enabled.delete(v);gl.isEnabled=v=>enabled.has(v);gl.drawElements=(...args)=>draws.push({args,alpha:uniforms.get('uAlphaTest'),matrix:uniforms.get('uLightMatrix'),vao:state.get(gl.VERTEX_ARRAY_BINDING)});
  return {gl,state,uniforms,draws,objects,textures};
}
const m=mockGL(),{gl}=m,stats={};const chunk={vbo:{id:1},ibo:{id:2},batches:new Int32Array([10,0,3,3,1,20,3,3,4,1])};
const chunks=new Map([[1,chunk],[2,{vbo:{id:3},ibo:{id:4},batches:new Int32Array([10,0,3,4,1])}]]),textures=new Map([[10,{texture:{id:5},revision:1}],[20,{texture:{id:6},revision:1}]]);
const lighting=createBrowserLighting(gl,{program:{id:20},stats,chunks,textures,adaptShader:x=>x});lighting.initialize('production depth vertex','production depth fragment');
const values=new Float32Array(82);values[0]=values[1]=1;values[2]=2;values[3]=2048;values[4]=4096;values[5]=1;values[6]=1;values[9]=10000;values[10]=20000;values[11]=1;values[12]=0;values[13]=values[14]=1;values[16]=1/2048;values[17]=1/4096;values[18]=4;values[19]=6;values[80]=1;values[81]=20;
for(const at of [32,48,64])for(let i=0;i<4;i++)values[at+i*5]=1;
for(const [at,bounds]of [[20,[0,0,.1,.2]],[24,[10,20,.3,.4]],[28,[30,40,.5,.6]]])values.set(bounds,at);
const pixels=new Int8Array([10,20,30,-1]);for(const unit of [2,5,7])lighting.uploadMask(unit,1,1,pixels,[0,0,1,1]);assert.equal(stats.lightingMaskBytes,12);
const draws=new Int32Array([1,0,0,10000,20000,0,0,0,2,0,0,10000,20000,0,0,2]);
const viewport=m.state.get(gl.VIEWPORT).slice(),depthFunc=m.state.get(gl.DEPTH_FUNC);lighting.prepare(values,draws);assert.equal(stats.shadowNearDraws,3);assert.equal(stats.shadowFarDraws,1,'static far includes animated role/alpha');assert.equal(m.draws.length,4,'hidden roof was incorrectly visibility filtered');assert.deepEqual(m.state.get(gl.VIEWPORT),viewport);assert.equal(m.state.get(gl.DEPTH_FUNC),depthFunc);assert.deepEqual(m.state.get(gl.COLOR_WRITEMASK),[true,true,true,true]);
values[9]=-123;lighting.bindReceiver();values[9]=10000;lighting.bindReceiverPlane(0);assert.equal(m.uniforms.get('uGeometryRoofReceivers'),1);lighting.bindReceiverPlane(1);assert.equal(m.uniforms.get('uGeometryRoofReceivers'),0);assert.equal(m.uniforms.get('uGeometryShadowWorldOffsetX'),10000);assert.equal(m.uniforms.get('uTerrainDefinitionFieldEnabled'),1);assert.equal(m.uniforms.get('uGlowMaskEnabled'),1);
assert.equal(stats.shadowResourceBytes,80*1024*1024);values[6]=0;lighting.prepare(values,draws);assert.equal(stats.shadowFarDraws,0,'unchanged far cache redrawn');
const visibility=draws.slice();visibility[5]=63;lighting.prepare(values,visibility);assert.equal(stats.shadowFarDraws,0,'camera roof visibility invalidates physical shadow');
const animatedMoved=draws.slice();animatedMoved[9]=256;lighting.prepare(values,animatedMoved);assert.equal(stats.shadowFarDraws,0,'animated scenery invalidates static far cache');
textures.get(10).revision=2;lighting.prepare(values,draws);assert.equal(stats.shadowFarDraws,1,'changed static cutout failed to invalidate far');
textures.get(20).revision=2;lighting.prepare(values,draws);assert.equal(stats.shadowFarDraws,0,'animated alpha footprint leaked into static far cache');
values[6]=values[7]=1;lighting.prepare(values,draws);lighting.bindReceiver();assert.equal(m.uniforms.get('uPreviousFarGeometryShadow'),1);assert.equal(stats.shadowResourceBytes,144*1024*1024);values[6]=values[7]=0;lighting.prepare(values,draws);assert.equal(stats.shadowResourceBytes,80*1024*1024);
values[1]=0;values[15]=-1;values[9]=-3072;values[10]=56789;lighting.prepare(values,draws);lighting.bindReceiver();assert.equal(m.uniforms.get('uGeometryShadowWorldOffsetX'),-3072);assert.equal(m.uniforms.get('uGeometryShadowWorldOffsetZ'),56789);assert.equal(stats.shadowResourceBytes,0);assert.equal(m.uniforms.get('uGeometryShadows'),0);assert(m.textures.get(gl.TEXTURE3),'inactive shadow comparison sampler incomplete');assert.equal(m.uniforms.get('uGlowMaskEnabled'),1,'negative lighting removed glows');
assert.throws(()=>lighting.uploadMask(1,1,1,pixels,[0,0,1,1]),/schema/);assert.throws(()=>lighting.prepare(new Float32Array(2),draws),/schema/);
lighting.dispose();lighting.dispose();assert.equal(stats.lightingMaskBytes,0);assert.equal(stats.shadowResourceBytes,0);assert.equal(stats.shadowFallbackBytes,0);assert.equal(m.objects.size,0,'owned GL resources leaked');
console.log('PASS browser lighting API/state/resource controls: mask bytes, borrowed arrays, hidden roof casters, far alpha/role exclusions, far history, negative suppression and full cleanup. No actual GPU or FPS claim.');

// Failed replacement leaves the original map owned and accounted for. Test
// each refusal followed by retry and eventual disposal, with no double delete.
for(const failure of ['resize','budget','framebuffer','storage']){
  const n=mockGL(),gpu=n.gl,resourceStats={},module=createBrowserLighting(gpu,{program:{id:1},stats:resourceStats,chunks:new Map(),textures:new Map(),adaptShader:x=>x});module.initialize('vertex','fragment');
  const low=new Float32Array(81);low[0]=low[1]=1;low[3]=1024;low[16]=1/1024;low[18]=12;for(let i=0;i<4;i++)low[32+i*5]=1;
  module.prepare(low,new Int32Array());const bytes=resourceStats.shadowResourceBytes,objects=n.objects.size;
  const next=low.slice();next[3]=2048;
  if(failure==='resize')next[3]=0;
  if(failure==='budget'){next[3]=8192;n.state.set(gpu.MAX_TEXTURE_SIZE,8192);}
  if(failure==='framebuffer')gpu.checkFramebufferStatus=()=>-999;
  if(failure==='storage')gpu.getError=()=>0x0505;
  assert.throws(()=>module.prepare(next,new Int32Array()),/resource limit|incomplete|storage error/);
  assert.equal(resourceStats.shadowResourceBytes,bytes,failure+' changed accounted map on failure');assert.equal(n.objects.size,objects,failure+' leaked or deleted existing GL objects');
  gpu.checkFramebufferStatus=()=>gpu.FRAMEBUFFER_COMPLETE;gpu.getError=()=>gpu.NO_ERROR;next[3]=2048;
  module.prepare(next,new Int32Array());assert.equal(resourceStats.shadowResourceBytes,16*1024*1024,failure+' retry allocation failed');
  module.dispose();module.dispose();assert.equal(resourceStats.shadowResourceBytes,0,failure+' disposed count invalid');assert.equal(n.objects.size,0,failure+' retry/disposal leaked resources');
}
console.log('PASS shadow replacement refusal/storage/FBO failure, retry and idempotent cleanup controls.');
