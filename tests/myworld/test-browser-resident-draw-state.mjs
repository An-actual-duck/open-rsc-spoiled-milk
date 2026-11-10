#!/usr/bin/env node
// Exact GL draw-state differential against frozen pre-optimization backend.
// An in-memory API oracle, not hardware pixel or performance evidence.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {createBrowserWebGL as current} from '../../tools/browser-gameplay/web/webgl.js';
const root=path.resolve(new URL('../..',import.meta.url).pathname);
const out=execFileSync('python3',['scripts/workspace-layout.py','path','tests','browser-resident-draw-state','--mkdir'],{cwd:root,encoding:'utf8'}).trim();
const oldSource=execFileSync('git',['show','ee876dbe895d914a9df78088cec2ca35362ffc04:tools/browser-gameplay/web/webgl.js'],{cwd:root,encoding:'utf8'}).replace(/from '\.\/([^']+)'/g,(_,file)=>`from '${pathToFileURL(path.join(root,'tools/browser-gameplay/web',file)).href}'`);
const oldFile=path.join(out,'original-webgl.mjs');fs.writeFileSync(oldFile,oldSource);
const {createBrowserWebGL:original}=await import(pathToFileURL(oldFile));
function mockGL(){const counts={};
let next=1,active=1000,currentProgram=null,currentVAO=null,lastSubData=null;
const constants={NO_ERROR:0,TEXTURE0:1000,TEXTURE1:1001,TEXTURE2:1002,TEXTURE3:1003,TEXTURE4:1004,TEXTURE5:1005,TEXTURE6:1006,TEXTURE7:1007};
const buffers=new Map(),bindings=new Map(),textures=new Map(),enabled=new Set(),uniforms=new Map(),calls=[];
const copy=a=>typeof a==='number'?new Uint8Array(a):new Uint8Array(a.buffer,a.byteOffset,a.byteLength).slice();
let gl,failure=null,pendingError=0;const owned=new Map();
const allocationKinds={createBuffer:"buffer",createVertexArray:"vao",createTexture:"texture",createShader:"shader",createProgram:"program",createFramebuffer:"framebuffer"};
const deletionKinds={deleteBuffer:"buffer",deleteVertexArray:"vao",deleteTexture:"texture",deleteShader:"shader",deleteProgram:"program",deleteFramebuffer:"framebuffer"};
const resources=()=>JSON.stringify([...owned].map(([kind,values])=>[kind,values.size]));
const functions={
  fenceSync:()=>({}),deleteSync(){},flush(){},clientWaitSync:()=>gl.CONDITION_SATISFIED,getExtension:()=>null,getError:()=>{const e=pendingError;pendingError=0;return e;},checkFramebufferStatus:()=>gl.FRAMEBUFFER_COMPLETE,getShaderParameter:()=>true,getProgramParameter:()=>true,getAttribLocation:()=>0,getUniformLocation:(p,n)=>n,
  createBuffer:()=>({id:next++}),createVertexArray:()=>({id:next++}),createTexture:()=>({id:next++}),createShader:()=>({id:next++}),createProgram:()=>({id:next++}),createFramebuffer:()=>({id:next++}),
  bindVertexArray:v=>currentVAO=v,bindBuffer:(target,b)=>bindings.set(target,b),bufferData:(target,data)=>buffers.set(bindings.get(target),copy(data)),
  bufferSubData:(target,offset,data)=>{const source=copy(data);buffers.get(bindings.get(target)).set(source,offset);lastSubData={target,offset,bytes:source};},
  activeTexture:v=>active=v,bindTexture:(target,t)=>textures.set(active,t),useProgram:p=>currentProgram=p,
  enable:c=>enabled.add(c),disable:c=>enabled.delete(c),isEnabled:c=>enabled.has(c),
  uniform1f:(n,v)=>uniforms.set(n,v),uniform1i:(n,v)=>uniforms.set(n,v),
  drawElements:(mode,count,type,offset)=>calls.push({mode,count,type,offset,program:currentProgram?.id,vao:currentVAO?.id,uniforms:[...uniforms].sort(([a],[b])=>a.localeCompare(b)),enabled:[...enabled].sort((a,b)=>a-b),textures:[...textures].map(([unit,texture])=>[unit,texture?.id??null]).sort((a,b)=>a[0]-b[0])}),
  getParameter:p=>p===gl.MAX_TEXTURE_SIZE?4096:p===gl.ACTIVE_TEXTURE?active:p===gl.TEXTURE_BINDING_2D?textures.get(active)||null:p===gl.CURRENT_PROGRAM?currentProgram:p===gl.VERTEX_ARRAY_BINDING?currentVAO:p===gl.ARRAY_BUFFER_BINDING?bindings.get(gl.ARRAY_BUFFER)||null:p===gl.VIEWPORT?[0,0,2,2]:p===gl.COLOR_CLEAR_VALUE?[0,0,0,1]:p===gl.COLOR_WRITEMASK?[true,true,true,true]:p===gl.DEPTH_WRITEMASK?true:p===gl.VENDOR?'in-memory test double':p===gl.RENDERER?'no hardware claim':0
};
gl=new Proxy(functions,{get:(o,k)=>{if(/^[A-Z0-9_]+$/.test(k))return constants[k]??=2000+Object.keys(constants).length;return(...args)=>{counts[k]=(counts[k]||0)+1;if(failure?.method===k){const mode=failure.mode;failure=null;if(mode==='null')return null;if(mode==='error')pendingError=gl.OUT_OF_MEMORY;else throw new Error('Injected '+k);}
const kind=allocationKinds[k],deleted=deletionKinds[k];const value=k in o?o[k](...args):undefined;if(kind&&value){if(!owned.has(kind))owned.set(kind,new Set());owned.get(kind).add(value);}if(deleted&&args[0])assert.equal(owned.get(deleted)?.delete(args[0]),true,'unknown/double resource delete '+k);return value;};}});

 return {gl,calls,counts,resources};
}
async function run(create){
 const m=mockGL(),{gl,calls,counts}=m;
 const canvas={getContext:()=>gl,addEventListener(){},removeEventListener(){},width:4,height:4};
 const b=create(canvas),n=b.natives;
 const v='#version 120\nattribute vec3 aPosition;void main(){gl_Position=vec4(aPosition,1.0);}';
 const f='#version 120\nvoid main(){vec4 color=vec4(1.0);gl_FragColor = color;}';
 await n.Java_orsc_BrowserWebGLBridge_initialize(null,v,f);await n.Java_orsc_BrowserWebGLBridge_initializeLighting(null,v,f);await n.Java_orsc_BrowserWebGLBridge_initializeSky();
 const light=new Float32Array(81);light[0]=1;light[1]=1;light[3]=4;light[11]=1;light[12]=0;
 await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);await n.Java_orsc_BrowserWebGLBridge_uploadLightingMask(null,5,1,1,new Uint8Array([255,0,0,255]),new Float32Array(4));
 await n.Java_orsc_BrowserWebGLBridge_skyFrame(null,new Float32Array(20),new Float32Array(4096));
 for(let id=7;id<10;id++)await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,id,1,1,new Int32Array([-1]),id===8);
 const vertices=new Float32Array(28*6),indices=new Int32Array([0,1,2,3,4,5]);
 for(let id=1;id<=8;id++){
  const batches=[];for(let kind=0;kind<=5;kind++){batches.push(id%2===0?7:9,0,3,kind,0,8,3,3,kind,1,999,0,3,kind,0);}
  await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,id,vertices,indices,Int32Array.from(batches));
 }
 const matrix=new Float32Array(48),u=new Float32Array(30),all=[];
 const draw=async(rows)=>{calls.length=0;await n.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,1,rows.length/8,rows.length/8,0,0,0,1,4,4,3]));await n.Java_orsc_BrowserWebGLBridge_present(null,4,4,true,matrix,u,Int32Array.from(rows),new Int32Array(16));all.push(structuredClone(calls));};
 // Repeated shared values, each changed offset, negative and mixed planes,
 // repeated VAOs, absent textures and all material-kind/alpha order combinations.
 for(let frame=0;frame<8;frame++){
  const rows=[];for(let id=1;id<=8;id++)rows.push(id,frame%2?0:id*128,frame%3?0:-id*128,frame%2?512:0,frame%3?1024:0,63,frame===0?0:(id%3)-1,0);
  if(frame%2)rows.push(...rows.slice(0,8),...rows.slice(0,8));await draw(rows);
  // Every frame must start fresh after unrelated external shader/VAO/texture state.
  gl.useProgram(null);gl.bindVertexArray(null);gl.activeTexture(gl.TEXTURE7);gl.bindTexture(gl.TEXTURE_2D,null);
  if(frame===2)await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,1,1,new Int32Array([0x123456]),true);
  if(frame===3)await n.Java_orsc_BrowserWebGLBridge_deleteTextures(null,new Int32Array([9]));
  if(frame===4)await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,9,1,1,new Int32Array([0xabcdef]),false);
  if(frame===5){light[12]=-1;await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);}
 }
 await draw([]);
 const beforeDispose={...counts};b.dispose();return {all,counts:beforeDispose,draws:all.reduce((n,x)=>n+x.length,0)};
}
const a=await run(original),b=await run(current);assert.ok(a.draws>1000);assert.deepEqual(b.all,a.all,'Per-draw effective GL state/order changed');
for(const name of ['uniform1f','uniform1i','activeTexture','bindTexture','bindVertexArray'])assert.ok(b.counts[name]<a.counts[name],name+' was not reduced');
const result={draws:b.draws,identical:true,originalCalls:a.counts,candidateCalls:b.counts,scope:'Mock GL per-draw state only; no hardware/FPS claim'};
fs.writeFileSync(path.join(out,'result.json'),JSON.stringify(result,null,2)+'\n');console.log('PASS '+b.draws+' exact ordered draws, uniforms/VAOs/textures/capabilities across alpha/kind/offset/plane/texture mutations, empty frame and intervening external state; no GPU/FPS claim');

if(process.argv.includes('--prepare-hardware')){
 const run=process.argv.find(x=>x.startsWith('--hardware-run='))?.split('=')[1]||'hardware-1';if(!/^[a-z0-9-]+$/.test(run))throw Error('Invalid hardware run');const target=path.join(out,run);if(fs.existsSync(target))throw Error('Hardware fixture exists; preserve evidence');fs.mkdirSync(target);
 for(const file of fs.readdirSync(path.join(root,'tools/browser-gameplay/web')))if(file.endsWith('.js'))fs.copyFileSync(path.join(root,'tools/browser-gameplay/web',file),path.join(target,file));
 fs.copyFileSync(path.join(root,'tests/myworld/browser-resident-draw-state-hardware.html'),path.join(target,'index.html'));
 fs.writeFileSync(path.join(target,'original-webgl.mjs'),oldSource.replaceAll(pathToFileURL(path.join(root,'tools/browser-gameplay/web')).href+'/', './'));
 console.log('Prepared hardware-only fixture: '+target);
}
