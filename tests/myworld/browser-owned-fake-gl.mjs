// State/resource oracle only. It does not rasterize, compile shaders or measure FPS.
import assert from 'node:assert/strict';
export function fakeGL(){
 const constants={NO_ERROR:0,TEXTURE0:1000,TEXTURE1:1001,TEXTURE2:1002,TEXTURE3:1003,TEXTURE4:1004,TEXTURE5:1005,TEXTURE6:1006,TEXTURE7:1007,COLOR_BUFFER_BIT:16384,DEPTH_BUFFER_BIT:256},resources=new Map(),state=new Map(),caps=new Set(),tex=new Map(),buffers=new Map(),attached=new Map(),uniforms=new Map(),attributes=new Map(),draws=[],queries=[];
 let next=1,active=1000,program=null,vao=null,draw=null,read=null,pendingError=0,failure=null,errors=0;
 const copy=v=>ArrayBuffer.isView(v)?Array.from(new Uint8Array(v.buffer,v.byteOffset,v.byteLength)):v;
 let gl;
 const key=(p)=>p?.id||null;
 function snapshot(op,args){
  const src=program?.shaders.map(s=>s.source).join('\n')||'';
  const samples={};for(const match of src.matchAll(/uniform\s+(?:highp\s+)?(?:[iu]?sampler2D(?:Shadow)?)\s+(\w+)/g)){const name=match[1],unit=uniforms.get(program)?.get(name)||0; samples[name]=key(tex.get(gl.TEXTURE0+unit));if(draw&&attached.get(draw)===tex.get(gl.TEXTURE0+unit))throw Error('Sampler DRAW feedback '+name);}
  draws.push({op,args,program:src,draw:key(draw),viewport:state.get(gl.VIEWPORT)?.slice(),samples,
   uniforms:[...(uniforms.get(program)||[])].sort((a,b)=>a[0].localeCompare(b[0])),
   depth:caps.has(gl.DEPTH_TEST)?[state.get(gl.DEPTH_FUNC),state.get(gl.DEPTH_WRITEMASK)]:false,
   blend:caps.has(gl.BLEND)?[state.get(gl.BLEND_SRC_RGB),state.get(gl.BLEND_DST_RGB),state.get(gl.BLEND_SRC_ALPHA),state.get(gl.BLEND_DST_ALPHA),state.get(gl.BLEND_EQUATION_RGB),state.get(gl.BLEND_EQUATION_ALPHA)]:false,
   colorMask:state.get(gl.COLOR_WRITEMASK)?.slice(),cull:caps.has(gl.CULL_FACE)?state.get(gl.CULL_FACE_MODE):false,
   dither:caps.has(gl.DITHER),scissor:caps.has(gl.SCISSOR_TEST),polygon:caps.has(gl.POLYGON_OFFSET_FILL)?[state.get(gl.POLYGON_OFFSET_FACTOR),state.get(gl.POLYGON_OFFSET_UNITS)]:false});
 }
 const api={
 getExtension:()=>null,getError:()=>{errors++;const e=pendingError;pendingError=0;return e;},getShaderParameter:()=>true,getProgramParameter:()=>true,getAttribLocation:()=>0,getUniformLocation:(p,n)=>({p,n}),shaderSource:(s,v)=>s.source=v,attachShader:(p,s)=>p.shaders.push(s),
 createShader:()=>({id:next++}),createProgram:()=>({id:next++,shaders:[]}),createBuffer:()=>({id:next++}),createVertexArray:()=>({id:next++}),createTexture:()=>({id:next++}),createFramebuffer:()=>({id:next++}),
 useProgram:p=>program=p,bindVertexArray:v=>vao=v,bindBuffer:(t,v)=>buffers.set(t,v),bufferData:(t,v)=>{const b=buffers.get(t);if(b)b.bytes=copy(v);},bufferSubData:(t,o,v)=>{assert.ok(buffers.get(t),'No destination buffer');},
 vertexAttribPointer:(...args)=>{if(!attributes.has(vao))attributes.set(vao,[]);attributes.get(vao).push(args);},
 activeTexture:u=>active=u,bindTexture:(t,v)=>tex.set(active,v),texImage2D:(...a)=>{const t=tex.get(active);assert.ok(t,'Missing upload texture');t.shape=[a[3],a[4]];t.bytes=copy(a[8]);},texSubImage2D:(...a)=>{assert.ok(tex.get(active),'Missing update texture');},texStorage2D:(t,l,f,w,h)=>{tex.get(active).shape=[w,h];},
 bindFramebuffer:(t,v)=>{if(t===gl.FRAMEBUFFER||t===gl.DRAW_FRAMEBUFFER)draw=v;if(t===gl.FRAMEBUFFER||t===gl.READ_FRAMEBUFFER)read=v;},framebufferTexture2D:(t,a,target,v)=>attached.set(t===gl.READ_FRAMEBUFFER?read:draw,v),checkFramebufferStatus:()=>gl.FRAMEBUFFER_COMPLETE,
 copyTexSubImage2D:(...args)=>{assert.ok(tex.get(active),'No copy destination');assert.notEqual(attached.get(read),tex.get(active),'READ/copy feedback');draws.push({op:'copy',source:key(read),destination:key(tex.get(active)),args});},
 uniform1f:(l,v)=>setUniform(l,v),uniform1i:(l,v)=>setUniform(l,v),uniform2f:(l,...v)=>setUniform(l,v),uniform3f:(l,...v)=>setUniform(l,v),uniform4f:(l,...v)=>setUniform(l,v),uniformMatrix4fv:(l,b,v)=>setUniform(l,Array.from(v)),
 enable:c=>caps.add(c),disable:c=>caps.delete(c),isEnabled:c=>{queries.push('enabled');return caps.has(c);},
 viewport:(...v)=>state.set(gl.VIEWPORT,v),colorMask:(...v)=>state.set(gl.COLOR_WRITEMASK,v),clearColor:(...v)=>state.set(gl.COLOR_CLEAR_VALUE,v),clearDepth:v=>state.set(gl.DEPTH_CLEAR_VALUE,v),depthMask:v=>state.set(gl.DEPTH_WRITEMASK,v),depthFunc:v=>state.set(gl.DEPTH_FUNC,v),cullFace:v=>state.set(gl.CULL_FACE_MODE,v),polygonOffset:(f,u)=>{state.set(gl.POLYGON_OFFSET_FACTOR,f);state.set(gl.POLYGON_OFFSET_UNITS,u);},pixelStorei:(p,v)=>state.set(p,v),
 blendFunc:(s,d)=>api.blendFuncSeparate(s,d,s,d),blendFuncSeparate:(a,b,c,d)=>{[gl.BLEND_SRC_RGB,gl.BLEND_DST_RGB,gl.BLEND_SRC_ALPHA,gl.BLEND_DST_ALPHA].forEach((p,n)=>state.set(p,[a,b,c,d][n]));},blendEquation:e=>api.blendEquationSeparate(e,e),blendEquationSeparate:(a,b)=>{state.set(gl.BLEND_EQUATION_RGB,a);state.set(gl.BLEND_EQUATION_ALPHA,b);},
 clear:mask=>draws.push({op:'clear',draw:key(draw),mask,color:state.get(gl.COLOR_CLEAR_VALUE)?.slice(),colorMask:state.get(gl.COLOR_WRITEMASK)?.slice(),depth:state.get(gl.DEPTH_WRITEMASK),scissor:caps.has(gl.SCISSOR_TEST)}),drawArrays:(...args)=>snapshot('arrays',args),drawElements:(...args)=>snapshot('elements',args),
 getParameter:p=>{queries.push(p);if(p===gl.MAX_TEXTURE_SIZE)return 4096;if(p===gl.ACTIVE_TEXTURE)return active;if(p===gl.CURRENT_PROGRAM)return program;if(p===gl.VERTEX_ARRAY_BINDING)return vao;if(p===gl.ARRAY_BUFFER_BINDING)return buffers.get(gl.ARRAY_BUFFER)||null;if(p===gl.TEXTURE_BINDING_2D)return tex.get(active)||null;if(p===gl.FRAMEBUFFER_BINDING||p===gl.DRAW_FRAMEBUFFER_BINDING)return draw;if(p===gl.READ_FRAMEBUFFER_BINDING)return read;if(p===gl.VENDOR||p===gl.RENDERER)return 'CPU state oracle, no hardware claim';return state.get(p)??0;},
 fenceSync:()=>({}),clientWaitSync:()=>gl.CONDITION_SATISFIED,deleteSync(){},flush(){}
 };
 function setUniform(l,v){assert.ok(l?.p,'Missing uniform location');assert.equal(program,l.p,'Uniform applied to wrong program');if(!uniforms.has(l.p))uniforms.set(l.p,new Map());uniforms.get(l.p).set(l.n,v);}
 const allocation={createShader:'shader',createProgram:'program',createBuffer:'buffer',createVertexArray:'vao',createTexture:'texture',createFramebuffer:'fbo'},deletion={deleteShader:'shader',deleteProgram:'program',deleteBuffer:'buffer',deleteVertexArray:'vao',deleteTexture:'texture',deleteFramebuffer:'fbo'};
 gl=new Proxy(api,{get:(target,name)=>{if(/^[A-Z0-9_]+$/.test(name))return constants[name]??=Array.from(name).reduce((a,c)=>(a*33+c.charCodeAt(0))%1000000007,2000);return(...args)=>{if(failure?.method===name){const mode=failure.mode;failure=null;if(mode==='null')return null;if(mode==='error')pendingError=gl.OUT_OF_MEMORY;else throw Error('Injected '+name);}const result=target[name]?.(...args);if(allocation[name]&&result){if(!resources.has(allocation[name]))resources.set(allocation[name],new Set());resources.get(allocation[name]).add(result);}if(deletion[name]&&args[0])assert.equal(resources.get(deletion[name])?.delete(args[0]),true,'Unknown/double delete '+name);return result;};}});
 caps.add(gl.DITHER);api.viewport(0,0,192,128);api.colorMask(true,true,true,true);api.clearColor(0,0,0,0);api.depthMask(true);api.depthFunc(gl.LESS);api.blendFunc(gl.ONE,gl.ZERO);api.blendEquation(gl.FUNC_ADD);state.set(gl.UNPACK_ALIGNMENT,4);
 return {gl,draws,queries,resources,state,caps,get errors(){return errors;},inject(method,mode='throw'){failure={method,mode};},canvas(){const listeners=new Map();return {width:192,height:128,getContext:()=>gl,addEventListener:(n,f)=>listeners.set(n,f),removeEventListener:n=>listeners.delete(n),lose(){listeners.get('webglcontextlost')?.({preventDefault(){}});}};}};
}
