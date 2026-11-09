#!/usr/bin/env node
// GPU API contract only, with an in-memory GL double. Not a shader/image/FPS test.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createBrowserWebGL,atlasTexelCenter} from '../../tools/browser-gameplay/web/webgl.js';
const reference=process.argv[2];
if(reference)for(const row of fs.readFileSync(reference,'utf8').trim().split('\n')) {
  const [position,size,unit,extent,expected]=row.split('\t').map(Number);
  assert.equal(atlasTexelCenter(position,size,unit,extent),Math.fround(expected),'sprite UV differs from actual native atlas reference');
}
let next=1,active=1000,currentProgram=null,currentVAO=null,lastSubData=null;
const constants={NO_ERROR:0,TEXTURE0:1000,TEXTURE1:1001,TEXTURE2:1002,TEXTURE3:1003,TEXTURE4:1004,TEXTURE5:1005,TEXTURE6:1006,TEXTURE7:1007};
const buffers=new Map(),bindings=new Map(),textures=new Map(),enabled=new Set(),uniforms=new Map(),calls=[];
const copy=a=>typeof a==='number'?new Uint8Array(a):new Uint8Array(a.buffer,a.byteOffset,a.byteLength).slice();
let gl;
const functions={
  getExtension:()=>null,getError:()=>0,getShaderParameter:()=>true,getProgramParameter:()=>true,getAttribLocation:()=>0,getUniformLocation:(p,n)=>n,
  createBuffer:()=>({id:next++}),createVertexArray:()=>({id:next++}),createTexture:()=>({id:next++}),createShader:()=>({id:next++}),createProgram:()=>({id:next++}),createFramebuffer:()=>({id:next++}),
  bindVertexArray:v=>currentVAO=v,bindBuffer:(target,b)=>bindings.set(target,b),bufferData:(target,data)=>buffers.set(bindings.get(target),copy(data)),
  bufferSubData:(target,offset,data)=>{const source=copy(data);buffers.get(bindings.get(target)).set(source,offset);lastSubData={target,offset,bytes:source};},
  activeTexture:v=>active=v,bindTexture:(target,t)=>textures.set(active,t),useProgram:p=>currentProgram=p,
  enable:c=>enabled.add(c),disable:c=>enabled.delete(c),isEnabled:c=>enabled.has(c),
  uniform1f:(n,v)=>uniforms.set(n,v),uniform1i:(n,v)=>uniforms.set(n,v),
  drawElements:(mode,count,type,offset)=>calls.push({count,offset,cutoff:uniforms.get('uBrowserAlphaCutoff'),blend:enabled.has(gl.BLEND),texture:uniforms.get('uTextureEnabled')}),
  getParameter:p=>p===gl.MAX_TEXTURE_SIZE?4096:p===gl.ACTIVE_TEXTURE?active:p===gl.TEXTURE_BINDING_2D?textures.get(active)||null:p===gl.CURRENT_PROGRAM?currentProgram:p===gl.VERTEX_ARRAY_BINDING?currentVAO:p===gl.ARRAY_BUFFER_BINDING?bindings.get(gl.ARRAY_BUFFER)||null:p===gl.VIEWPORT?[0,0,2,2]:p===gl.COLOR_WRITEMASK?[true,true,true,true]:p===gl.DEPTH_WRITEMASK?true:p===gl.VENDOR?'in-memory test double':p===gl.RENDERER?'no hardware claim':0
};
gl=new Proxy(functions,{get:(o,k)=>k in o?o[k]:/^[A-Z0-9_]+$/.test(k)?(constants[k]??=2000+Object.keys(constants).length):()=>{}});
const backend=createBrowserWebGL({getContext:()=>gl,addEventListener(){},removeEventListener(){},width:2,height:2});const n=backend.natives;
const v='#version 120\nattribute vec3 aPosition;void main(){gl_Position=vec4(aPosition,1.0);}';
const f='#version 120\nvoid main(){vec4 color=vec4(1.0);gl_FragColor = color;}';
assert.equal(await n.Java_orsc_BrowserWebGLBridge_initialize(null,v,f),4096);
await n.Java_orsc_BrowserWebGLBridge_initializeLighting(null,v,f);await n.Java_orsc_BrowserWebGLBridge_initializeSky();
const light=new Float32Array(81);light[0]=1;await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);
await n.Java_orsc_BrowserWebGLBridge_skyFrame(null,new Float32Array(20),new Float32Array(4096));
const vertices=new Float32Array(84),indices=new Int32Array([0,1,2]),batches=new Int32Array([7,0,3,1,0]);
await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,1,1,new Int32Array([-1]),false);
await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,1,vertices,indices,batches);batches[4]=1;
const resident=backend.stats.gpuBytes;
const matrix=new Float32Array(48),values=new Float32Array(30),draws=new Int32Array([1,0,0,0,0,2,0,0]);
async function frame(){calls.length=0;await n.Java_orsc_BrowserWebGLBridge_present(null,2,2,true,matrix,values,draws,new Int32Array(4));return calls.slice();}
assert.deepEqual(await frame(),[{count:3,offset:0,cutoff:.5,blend:false,texture:1}]);
await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,1,1,new Int32Array([-1]),true);
assert.equal(backend.stats.gpuBytes,resident);
assert.deepEqual(await frame(),[{count:3,offset:0,cutoff:0,blend:true,texture:1}],'alpha class did not immediately follow current texture');
const replacement=new Float32Array(84);replacement[0]=9;const nextBatches=new Int32Array([7,0,3,1,1]);
await n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,1,replacement,indices,nextBatches);nextBatches[2]=0;replacement[0]=100;
assert.equal(backend.stats.gpuBytes,resident);assert.equal(backend.stats.repackedChunks,1);assert.equal((await frame())[0].count,3,'repack retained borrowed batch metadata');
await assert.rejects(()=>n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,1,new Float32Array(112),indices,nextBatches),/shape changed/);assert.equal(backend.stats.gpuBytes,resident);
await n.Java_orsc_BrowserWebGLBridge_deleteTextures(null,new Int32Array([7]));assert.deepEqual(await frame(),[{count:3,offset:0,cutoff:.5,blend:false,texture:0}]);
await n.Java_orsc_BrowserWebGLBridge_beginWorldSprites();const quad=new Float32Array(20);for(let i=0;i<4;i++){quad[i*5+3]=i===0||i===3?1:0;quad[i*5+4]=i<2?0:1;}
await n.Java_orsc_BrowserWebGLBridge_uploadWorldSprite(null,3,5,quad,new Uint8Array(60));const sampled=new Float32Array(lastSubData.bytes.buffer);
for(let i=0;i<4;i++){assert.equal(sampled[i*5+3],atlasTexelCenter(0,3,quad[i*5+3],2048));assert.equal(sampled[i*5+4],atlasTexelCenter(0,5,quad[i*5+4],2048));}
await n.Java_orsc_BrowserWebGLBridge_deleteChunks(null,new Int32Array([1]));assert.equal(backend.stats.gpuBytes,0);backend.dispose();backend.dispose();
console.log('PASS native atlas UV samples, copied repack metadata, fixed resident accounting, alpha-class refresh/removal and mirrored NPOT sprite UV API contracts. No real GPU/image/FPS claim.');
