import assert from 'node:assert/strict';
import {createBrowserOverlay} from '../../tools/browser-gameplay/web/browser-overlay.js';
import {validateOverlayDelta} from '../../tools/browser-gameplay/web/browser-overlay-delta.js';
import {fakeGL} from './browser-overlay-delta-gl-double.mjs';
const W=8,H=128;
function planes(rows=[]){const rgb=new Uint8Array(W*H*4),t=new Float32Array(W*H).fill(1);for(const y of rows)for(let x=0;x<W;x++){rgb[(y*W+x)*4]=37+y;t[y*W+x]=.25;}return{rgb,t};}
function declare(m,p,{gen=1,seq=1,partial=false,baseGen=-1,baseSeq=-1,bands=[0,H],reset=false,work=null}={}){let rows=0;for(let n=0;n<bands.length;n+=2)rows+=bands[n+1]-bands[n];m.prepareDelta(W,H,gen,seq,reset,partial,baseGen,baseSeq,new Int32Array(bands),new Int32Array(work||[rows/16,0,rows,rows,bands.length/2,Number(partial)]),p.rgb,p.t);}
const f=fakeGL(),stats={},m=createBrowserOverlay(f.gl,{stats,deltaTransport:true,maxTextureSize:4096,deferOrCheck:check=>check()}),first=planes([2]);declare(m,first);m.begin(W,H);m.draw(W,H,first.rgb);
const firstUploads=f.uploads.slice(0,2);assert.equal(firstUploads.length,2);assert.equal(firstUploads[0].buffer,first.t.buffer);assert.equal(firstUploads[1].buffer,first.rgb.buffer,'RGB was repacked/copied before API');
const second=planes([70]),beforeCalls=f.uploads.length,beforeChecks=f.checks;
declare(m,second,{gen:2,seq:2,partial:true,baseGen:1,baseSeq:1,bands:[0,16,64,80]});assert.equal(f.checks-beforeChecks,2,'Validation repeated per band');
assert.equal(f.uploads.length-beforeCalls,4);assert(f.uploads.slice(beforeCalls).every(u=>u.buffer===second.rgb.buffer||u.buffer===second.t.buffer),'Band data was copied instead of viewed');
m.begin(W,H);const callsBeforeDraw=f.uploads.length;m.draw(W,H,second.rgb);assert.equal(f.uploads.length,callsBeforeDraw,'Delta draw reuploaded RGB');
const rgbTexture=[...f.objects].find(o=>o.format===f.gl.RGBA8&&o.pixels),tTexture=[...f.objects].find(o=>o.format===f.gl.R32F);assert.deepEqual(rgbTexture.pixels,second.rgb);assert.deepEqual(tTexture.pixels,second.t);
// Mutating borrowed arrays and metadata after the synchronous declaration must
// not change resident textures or the already-owned command description.
const third=planes([90]),expectedRgb=third.rgb.slice(),expectedT=third.t.slice(),bands=new Int32Array([64,96]);m.prepareDelta(W,H,3,3,false,true,2,2,bands,new Int32Array([1,0,16,32,1,1]),third.rgb,third.t);bands.fill(0);third.rgb.fill(255);third.t.fill(0);m.begin(W,H);m.draw(W,H,third.rgb);assert.deepEqual(rgbTexture.pixels,expectedRgb);assert.deepEqual(tTexture.pixels,expectedT);
const p=planes();declare(m,p,{gen:4,seq:4,partial:true,baseGen:3,baseSeq:3,bands:[80,96],work:[0,0,0,16,1,1]});m.begin(W,H);m.draw(W,H,p.rgb);
const zeroUploads=f.uploads.length;declare(m,p,{gen:5,seq:5,partial:true,baseGen:4,baseSeq:4,bands:[],work:[0,0,0,0,0,1]});m.begin(W,H);m.draw(W,H,p.rgb);assert.equal(f.uploads.length,zeroUploads,'Zero-touch delta uploaded pixels');
const history={width:W,height:H,generation:5,frameSequence:5},valid={width:W,height:H,generation:6,frameSequence:6,reset:false,partial:true,baseGeneration:5,baseFrameSequence:5,bands:[0,16],work:[1,0,16,16,1,1],rgb:p.rgb,transmittance:p.t};
const mutations=[{width:0},{height:32769},{generation:NaN},{frameSequence:Infinity},{reset:1},{partial:1},{rgb:new Uint8Array(1)},{transmittance:new Float32Array(1)},{bands:[0,16,16,32]},{bands:[-16,0]},{bands:[1,16]},{bands:[0,144]},{bands:[0,16,32,48,64,80,96,112,112,128]},{bands:[0,80],work:[1,0,16,80,1,1]},{baseGeneration:4},{baseFrameSequence:4},{generation:5},{frameSequence:5},{work:[-1,0,16,16,1,1]},{work:[1,0,16,16,2,1]}];
for(const change of mutations){const b=f.calls;assert.throws(()=>validateOverlayDelta({...valid,...change},history,true));assert.equal(f.calls,b,'Pure validation called graphics');const candidate={...valid,...change};assert.throws(()=>m.prepareDelta(candidate.width,candidate.height,candidate.generation,candidate.frameSequence,candidate.reset,candidate.partial,candidate.baseGeneration,candidate.baseFrameSequence,candidate.bands,candidate.work,candidate.rgb,candidate.transmittance));assert.equal(f.calls,b,'Invalid metadata touched graphics');}
for(const [gen,seq]of [[4,5],[5,4]]){const before=f.calls;assert.throws(()=>declare(m,p,{gen,seq}),/stale/);assert.equal(f.calls,before,'Stale full token touched graphics');}
// A new owner/reset is allowed to restart epochs, then a full same-frame replay
// retains the existing API semantics rather than being labelled a stale frame.
declare(m,p,{gen:1,seq:1,reset:true});m.begin(W,H);m.draw(W,H,p.rgb);declare(m,p,{gen:1,seq:1});m.begin(W,H);m.draw(W,H,p.rgb);
declare(m,p,{gen:5,seq:5});m.begin(W,H);m.draw(W,H,p.rgb);
m.invalidateDeltaHistory();assert.throws(()=>declare(m,p,{gen:6,seq:6,partial:true,baseGen:5,baseSeq:5,bands:[]}),/history/);declare(m,p,{gen:6,seq:6});m.begin(W,H);m.draw(W,H,p.rgb);m.hold(W,H);assert.throws(()=>declare(m,p,{gen:7,seq:7,partial:true,baseGen:6,baseSeq:6,bands:[]}),/history/);
const beforeHeld=f.calls;assert.throws(()=>declare(m,p,{gen:5,seq:5}),/stale/);assert.equal(f.calls,beforeHeld,'Stale full token after held frame touched graphics');
// A fallback fade advances retained FULL even though partial history is gone.
m.prepare(W,H,3,6,7,1,false,p.t);m.begin(W,H);m.draw(W,H,p.rgb);const beforeFallback=f.calls;assert.throws(()=>declare(m,p,{gen:6,seq:6}),/stale/);assert.equal(f.calls,beforeFallback,'Stale full token after fallback touched graphics');
for(const w of [8192,32769]){const before=f.calls;assert.throws(()=>m.prepareDelta(w,1,8,8,false,false,-1,-1,new Int32Array([0,1]),new Int32Array([0,0,0,1,1,0]),new Uint8Array(w*4),new Float32Array(w)),/dimensions/);assert.equal(f.calls,before,'Invalid wide/short dimensions queried graphics');}
const failed=fakeGL(),bad=createBrowserOverlay(failed.gl,{deltaTransport:true,maxTextureSize:4096});declare(bad,p);bad.begin(W,H);bad.draw(W,H,p.rgb);failed.gl.failRgb=true;assert.throws(()=>declare(bad,second,{gen:2,seq:2,partial:true,baseGen:1,baseSeq:1,bands:[0,16,64,80]}),/second-plane/);assert.throws(()=>bad.begin(W,H),/unavailable/);bad.dispose();assert.equal(failed.objects.size,0);
m.dispose();assert.equal(f.objects.size,0);assert(stats.overlayDeltaPartialDeclarations>=4);assert(stats.overlayDeltaUploadedBytes<stats.overlayDeltaFullEquivalentBytes);
console.log('PASS bounded delta metadata before ANY GL, synchronous source/subarray ownership, exact resident RGB/T, zero touches, history invalidation, second-plane failure poison/cleanup and per-plane validation; GL double only');
