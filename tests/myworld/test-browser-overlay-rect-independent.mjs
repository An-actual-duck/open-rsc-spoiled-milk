import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
import {fakeGL} from './browser-overlay-delta-gl-double.mjs';
import {installUnpackSpecOracle} from './browser-overlay-unpack-spec-oracle.mjs';
const source=process.argv[2],dir=path.dirname(source);
const {createBrowserOverlay}=await import(pathToFileURL(source));
const {createOwnedBrowserFrameState}=await import(pathToFileURL(path.join(dir,'browser-owned-state.js')));
const {establishOverlayUnpack}=await import(pathToFileURL(path.join(dir,'browser-overlay-unpack.js')));
const {createBrowserSpriteAtlas}=await import(pathToFileURL(path.join(dir,'browser-sprite-atlas.js')));
const W=65,H=33,N=W*H,REGIONS=[0,0,32,16,64,32,1,1];let checks=0;
function planes(value){const raw=new Uint8Array(N*4+11),rgb=raw.subarray(3,3+N*4),back=new Float32Array(N+8),t=back.subarray(3,3+N).fill(1);rgb[4]=value;rgb[(N-1)*4]=value+1;t[1]=.25;t[N-1]=.5;return {rgb,t};}
function make(mode,owned){
 const f=fakeGL(),g=f.gl,oracle=installUnpackSpecOracle(g);
 g.FRAMEBUFFER=0x8d40;const bind=g.bindFramebuffer;g.bindFramebuffer=(target,value)=>{if(target===g.FRAMEBUFFER){bind(g.DRAW_FRAMEBUFFER,value);bind(g.READ_FRAMEBUFFER,value);}else bind(target,value);};
 g.clearDepth=()=>{};g.blendEquation=x=>g.blendEquationSeparate(x,x);g.blendFunc=(x,y)=>g.blendFuncSeparate(x,y,x,y);
 const owner=owned?createOwnedBrowserFrameState(g,{enabled:true,ownOverlayUnpack:true}):null,stats={};
 const overlay=createBrowserOverlay(g,{stats,ownedFrame:owner,deltaTransport:true,overlayRectMode:mode,maxTextureSize:4096,deferOrCheck:c=>c()});
 return {f,g,oracle,owner,stats,overlay};
}
function declaration(e,p,gen=1,rect=false){const work=rect?[1,0,17,0,2,1,2,2,513,2,513,1,0,3,3,257]:[0,0,0,H,1,0];
 e.overlay.prepareDelta(W,H,gen,gen,false,rect,rect?gen-1:-1,rect?gen-1:-1,new Int32Array(rect?REGIONS:[0,H]),new Int32Array(work),p.rgb,p.t);
}
function draw(e,p){e.owner?.begin(W,H,0,0,0);e.overlay.begin(W,H);e.overlay.draw(W,H,p.rgb);}
function hostile(e){const g=e.g;for(const [field,v]of [[g.UNPACK_ROW_LENGTH,79],[g.UNPACK_SKIP_ROWS,2],[g.UNPACK_SKIP_PIXELS,3],[g.UNPACK_ALIGNMENT,8],[g.UNPACK_FLIP_Y_WEBGL,true],[g.UNPACK_PREMULTIPLY_ALPHA_WEBGL,true]])g.pixelStorei(field,v);g.bindBuffer(g.PIXEL_UNPACK_BUFFER,{hostilePbo:true});}
function pixels(e,p){const textures=[...e.f.objects],c=textures.find(x=>x.format===e.g.RGBA8&&x.pixels),t=textures.find(x=>x.format===e.g.R32F);assert.deepEqual(c.pixels,p.rgb);assert.deepEqual(new Uint32Array(t.pixels.buffer),new Uint32Array(p.t.buffer,p.t.byteOffset,p.t.length));checks+=2;}
function atlasAfter(e){
 const g=e.g,texture=g.createTexture();g.activeTexture(g.TEXTURE0);g.bindTexture(g.TEXTURE_2D,texture);g.texImage2D(g.TEXTURE_2D,0,g.RGBA8,16,16,0,g.RGBA,g.UNSIGNED_BYTE,new Uint8Array(16*16*4));
 const atlas=createBrowserSpriteAtlas(g,{texture,vbo:null,extent:16,ownedFrame:e.owner}),rgba=Uint8Array.from({length:16},(_,i)=>i+10);
 assert.deepEqual([...atlas.plan(1,1,new Int32Array([1,2,2]))],[1]);atlas.commit(1,1,rgba,new Float32Array(20));atlas.assertCommitted();
 assert.deepEqual(texture.pixels.subarray(0,8),rgba.subarray(0,8));assert.deepEqual(texture.pixels.subarray(64,72),rgba.subarray(8,16));checks+=3;
 atlas.dispose();g.deleteTexture(texture);
}
for(const mode of ['on','packed'])for(const owned of [false,true]){
 const e=make(mode,owned),first=planes(31),second=planes(71);declaration(e,first);draw(e,first);
 // Scope must preserve arbitrary incoming unpack state when it is not owned.
 if(!owned)hostile(e);const before=e.oracle.snapshot(),at=e.oracle.uploads.length;
 declaration(e,second,2,true);assert.deepEqual(e.oracle.snapshot(),before);checks++;
 const sub=e.oracle.uploads.slice(at);assert.equal(sub.length,4);checks++;
 const span=sub.reduce((n,u)=>n+u.data.byteLength,0);assert.equal(span,mode==='packed'?513*8:1008*8);checks++;
 assert(sub.every(u=>u.offset===0));checks++;
 if(mode==='on')assert(sub.every(u=>u.data.buffer===second.rgb.buffer||u.data.buffer===second.t.buffer));
 else assert(sub.every(u=>u.data.buffer!==second.rgb.buffer&&u.data.buffer!==second.t.buffer));checks++;
 draw(e,second);pixels(e,second);
 // Declaring bad later geometry or a short view cannot query/change GL or grow staging.
 const invalid=[{regions:[0,0,32,16],count:1},{regions:[1,0,31,16,64,32,1,1],count:2},{regions:[0,0,32,16,0,0,32,16],count:2}];
 for(const bad of invalid){const calls=e.f.calls+e.oracle.trace.length,capacity=e.stats.overlayStagingRetainedBytes;
  assert.throws(()=>e.overlay.prepareDelta(W,H,3,3,false,true,2,2,new Int32Array(bad.regions),new Int32Array([1,0,17,0,bad.count,1,2,2,513,2,513,1,0,3,3,257]),second.rgb,second.t),/Invalid|omitted|Overlapping/);
  assert.equal(e.f.calls+e.oracle.trace.length,calls);assert.equal(e.stats.overlayStagingRetainedBytes,capacity);checks+=3;
 }
 {const calls=e.f.calls+e.oracle.trace.length;assert.throws(()=>e.overlay.prepareDelta(W,H,3,3,false,true,2,2,new Int32Array(REGIONS),new Int32Array([1,0,17,0,2,1,2,2,513,2,513,1,0,3,3,257]),second.rgb,second.t.subarray(1)),/planes/);assert.equal(e.f.calls+e.oracle.trace.length,calls);checks+=2;}
 // An owner exit switches to the scoped path; reentry establishes canonical state.
 if(owned){e.owner.active=false;hostile(e);const saved=e.oracle.snapshot();declaration(e,second,3,true);assert.deepEqual(e.oracle.snapshot(),saved);draw(e,second);e.owner.active=true;
  assert.equal(e.oracle.state.get(e.g.UNPACK_ROW_LENGTH),0);assert.equal(e.oracle.state.get(e.g.UNPACK_SKIP_ROWS),0);assert.equal(e.oracle.state.get(e.g.PIXEL_UNPACK_BUFFER_BINDING),null);checks+=4;
 }else establishOverlayUnpack(e.g);
 // Full overlay and then the ACTUAL sprite-atlas upload must not inherit a rectangle stride.
 declaration(e,second,owned?4:3,false);draw(e,second);pixels(e,second);atlasAfter(e);
 assert.equal(e.stats.overlayDeltaLast.logicalTexelBytes,N*8);e.overlay.dispose();assert.equal(e.f.objects.size,0);checks+=2;
}
for(const mode of ['on','packed'])for(const plane of ['RED','RGBA']){
 const e=make(mode,false),first=planes(21);declaration(e,first);draw(e,first);hostile(e);const saved=e.oracle.snapshot();e.oracle.setFailure(e.g[plane]);
 assert.throws(()=>declaration(e,planes(99),2,true),/Injected plane/);assert.deepEqual(e.oracle.snapshot(),saved);assert.throws(()=>e.overlay.begin(W,H),/unavailable/);
 assert.throws(()=>declaration(e,first,3,false),/unavailable/);e.overlay.dispose();assert.equal(e.f.objects.size,0);if(mode==='packed')assert.equal(e.stats.overlayStagingRetainedBytes,0);checks+=5;
}
console.log('PASS independent rectangle actual-module/spec transfer checks='+checks+'; scoped exact restore, canonical ownership transitions, minimal/direct vs tight views, both-plane poison/cleanup, following full overlay and actual atlas; CPU model only');
