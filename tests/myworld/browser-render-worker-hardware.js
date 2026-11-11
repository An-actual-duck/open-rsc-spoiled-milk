import {createBrowserWebGL as original} from './original/webgl.js';
import {createBrowserWebGL as current} from './webgl.js';
import {createBrowserRenderWorker} from './browser-render-worker.js';
import {initializeSession,prepareFrame,cases,geometry} from './browser-owned-scenario.mjs';
const proof=window.renderWorkerProof={completed:false,passed:false,cases:[],errors:[],comparedBytes:0,changedBytes:0,coloredComponents:0,scope:'Actual same production shader/main58 vs candidate main vs OffscreenCanvasWorker RGBA oracle. No CheerpJ/gameplay/input/FPS/scanout proof.'};
const check=(v,m)=>{if(!v)throw Error(m);},text=async p=>(await fetch(p)).text(),binary=async(p,T)=>new T(await(await fetch(p)).arrayBuffer()),wait=()=>new Promise(r=>setTimeout(r,0));
let activePattern=false;
function patternT(w,h){const a=new Float32Array(w*h),v=[0,.125,.33333334,.5,.75,.875,1];for(let y=0;y<h;y++)for(let x=0;x<w;x++)a[y*w+x]=v[(x+2*y)%7];return a;}
function patternC(w,h){const a=new Int32Array(w*h);for(let y=0;y<h;y++)for(let x=0;x<w;x++)a[y*w+x]=x%7===0?0:((x*7%64)<<16)|((y*11%64)<<8)|((x+y)*13%64);return a;}
let dpr=1,rect={width:192,height:128},sessions=[],originalDpr;
function cloneSky(skies){return skies.map(s=>Object.fromEntries(Object.entries(s).map(([k,v])=>[k,new v.constructor(v)])));}
function setLayout(w,h,d=1){rect={width:w,height:h};dpr=d;dispatchEvent(new Event('browser-display-change'));for(const s of sessions)s.backend.invalidateLayout?.();}
function instrument(session){
 const raw=session.n,counts={},inputs=new Map();session.inputs=inputs;session.commandCounts=counts;
 session.n=Object.fromEntries(Object.entries(raw).map(([name,fn])=>[name,async(...args)=>{
  if(activePattern&&name.endsWith('_overlayFrame'))args[8]=patternT(args[1],args[2]);
  if(activePattern&&name.endsWith('_present'))args[7]=patternC(args[1],args[2]);
  counts[name]=(counts[name]||0)+1;
  for(const a of args)if(ArrayBuffer.isView(a)&&a.byteLength&&!inputs.has(a))inputs.set(a,new Uint8Array(a.buffer,a.byteOffset,a.byteLength).slice());
  if(name.endsWith('_present'))session.lastPresent=args.map(a=>ArrayBuffer.isView(a)?new a.constructor(a):a);
  if(name.endsWith('_overlayFrame'))session.lastOverlay=args.map(a=>ArrayBuffer.isView(a)?new a.constructor(a):a);
  return fn(...args);
 }]));return session;
}
async function finish(s,test){if(test.world)await s.n.Java_orsc_BrowserWebGLBridge_frameWork(null,new Float32Array(14),new Int32Array(14));if(s.worker)await s.backend.drain();}
async function read(s){
 if(s.worker){const r=await s.backend.readPixels();check(r.pixels instanceof Uint8Array&&r.pixels.length===r.width*r.height*4,'Worker owned readback shape');return r;}
 const {gl,canvas}=s,pixels=new Uint8Array(canvas.width*canvas.height*4);gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,pixels);check(gl.getError()===gl.NO_ERROR,'Main GPU readback error');return {width:canvas.width,height:canvas.height,pixels};
}
function compare(name,expected,actual){check(expected.width===actual.width&&expected.height===actual.height,'Output size mismatch '+name);let changed=0;for(let i=0;i<expected.pixels.length;i++){if(expected.pixels[i]!==actual.pixels[i]){changed++;if(!proof.firstMismatch)proof.firstMismatch={name,index:i,expected:expected.pixels[i],actual:actual.pixels[i]};}if(i%4!==3&&expected.pixels[i])proof.coloredComponents++;}proof.comparedBytes+=expected.pixels.length;proof.changedBytes+=changed;check(changed===0,'Exact RGBA mismatch '+name+':'+changed);}
async function frame(test,{layout=[test.w,test.h,1],pattern=false}={}){
 proof.caseInProgress=test.name;activePattern=pattern;setLayout(...layout);const views=[];
 for(const s of sessions){
  const render=await prepareFrame(s,test);await render();await finish(s,test);const pixels=await read(s);views.push(pixels);
  check(!s.backend.stats.lastError&&!s.backend.stats.contextLost,'Backend failed '+s.name);
 }
 compare(test.name+'/candidate-main',views[0],views[1]);compare(test.name+'/worker',views[0],views[2]);
 const stats=sessions[2].backend.stats,worker=stats.renderWorker;
 check(worker.completedFrames===worker.submittedFrames&&worker.completedPackets===worker.submittedPackets,'Queued frames falsely treated as completed');check(worker.maxInFlight<=2&&worker.droppedFrames===0,'Worker queue capacity/drop');
 activePattern=false;proof.cases.push({name:test.name,logical:[test.w,test.h],output:[views[0].width,views[0].height],layout:{...rect,dpr},physical:stats.physicalOutput,completedFrame:worker.lastCompletedFrame,completedPackets:worker.completedPackets,atlasImages:stats.spriteResidentImages,skyDraws:stats.skyDrawCalls,shadowNearDraws:stats.shadowNearDraws,mutatedReturnedBytes:worker.oracleMutatedReturnedBytes||0});await wait();return views;
}
try{
 originalDpr=Object.getOwnPropertyDescriptor(window,'devicePixelRatio');Object.defineProperty(window,'devicePixelRatio',{configurable:true,get:()=>dpr});
 const shaders={};for(const [name,file]of Object.entries({vertex:'world.vert',fragment:'world.frag',depthVertex:'depth.vert',depthFragment:'depth.frag'}))shaders[name]=await text('./shaders/'+file);
 const vertices=await binary('./sky/geometry.bin',Float32Array),ranges=await binary('./sky/ranges.bin',Int32Array),skies=[];for(let i=0;i<3;i++)skies.push({geometry:vertices,ranges,values:await binary('./sky/values'+i+'.bin',Float32Array),palette:await binary('./sky/palette'+i+'.bin',Float32Array)});
 for(const [name,create,worker]of [['original',original,false],['current',current,false],['worker',createBrowserRenderWorker,true]]){
  const canvas=document.getElementById(name);canvas.getBoundingClientRect=()=>({x:0,y:0,left:0,top:0,right:rect.width,bottom:rect.height,...rect});
  const backend=await create(canvas,()=>{},{ownedState:true,benchmarkControl:true,streamUploadValidation:true,physicalOutputControl:true,physicalOutput:true,oracle:true,mutateReturned:true}),gl=worker?null:canvas.getContext('webgl2');
  const shell=instrument({backend,n:backend.natives,name,worker,canvas,gl});const initialized=await initializeSession({...backend,natives:shell.n},shaders,cloneSky(skies));Object.assign(shell,initialized);shell.backend=backend;shell.n=initialized.n;sessions.push(shell);
 }
 const gl=sessions[0].gl,debug=gl.getExtension('WEBGL_debug_renderer_info');proof.renderer=debug?gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):sessions[0].backend.stats.renderer;proof.vendor=debug?gl.getParameter(debug.UNMASKED_VENDOR_WEBGL):sessions[0].backend.stats.vendor;check(!/SwiftShader|llvmpipe|softpipe|software|WARP/i.test(proof.renderer),'Software GPU fallback rejected');check(/AMD|Radeon/i.test(proof.renderer),'Expected owner Radeon hardware, got '+proof.renderer);proof.workerRenderer=sessions[2].backend.stats.renderer;check(/AMD|Radeon/i.test(proof.workerRenderer)&&!/SwiftShader|llvmpipe|softpipe|software|WARP/i.test(proof.workerRenderer),'Worker hardware fallback rejected '+proof.workerRenderer);
 for(let n=0;n<32;n++)await frame({name:'warm-'+n,mode:1,world:true,g:0,seq:0,w:192,h:128});proof.warmFrames=32;proof.cases=[];proof.comparedBytes=proof.changedBytes=proof.coloredComponents=0;
 for(const s of sessions)check(s.backend.stats.worldReadiness.phase==='ready','World source never ready '+s.name);
 // Existing source-bound complete pipeline: full C/T, retained/fades, sky/depth,
 // textures/masks, atlas reuse and viewport changes. No replayed toy shader.
 for(const test of cases())await frame(test);
 for(const [name,w,h,cw,ch,d]of [['physical-owner',960,540,1843.5555555555552,1037,1],['odd-two-times',197,131,394,262,1],['odd-fractional',197,131,311,207,1],['dpr-two',197,131,197,131,2]]){
  const views=await frame({name,mode:1,world:true,g:500+proof.cases.length,seq:500+proof.cases.length,w,h},{layout:[cw,ch,d]});
  const wanted=name==='physical-owner'?[1843,1037]:name==='odd-fractional'?[311,207]:[394,262];check(views[2].width===wanted[0]&&views[2].height===wanted[1],'Physical metrics/DPR fell back to scale1 '+name);check(sessions[2].backend.stats.physicalOutput.devicePixelRatio===d,'Worker lost explicit DPR');
 }
 await frame({name:'full-CT-hole-alpha-grid',mode:1,world:true,g:590,seq:590,w:197,h:131},{layout:[394,262,1],pattern:true});
 // Production opaque coverage has NULL T, unlike the older standalone fixture.
 for(const s of sessions){const originalOverlay=s.n.Java_orsc_BrowserWebGLBridge_overlayFrame;s.n.Java_orsc_BrowserWebGLBridge_overlayFrame=(...a)=>{a[8]=null;return originalOverlay(...a);};s.restoreOpaque=()=>s.n.Java_orsc_BrowserWebGLBridge_overlayFrame=originalOverlay;}
 await frame({name:'opaque-null-T',mode:0,world:false,g:600,seq:600,w:192,h:128});for(const s of sessions)s.restoreOpaque();
 // Real resource prefixes, replacement/deletion/recreation and atlas epoch barrier.
 for(const s of sessions){await s.n.Java_orsc_BrowserWebGLBridge_deleteTextures(null,new Int32Array([7]));await s.n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,2,2,new Int32Array([0x44bbffff,-1,0xbb44ffff,-1]),false);for(const id of [1,3])await s.n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,id,geometry(id),new Int32Array([0,1,2]),new Int32Array([7,0,3,id===3?5:1,0]));await s.n.Java_orsc_BrowserWebGLBridge_deleteChunks(null,new Int32Array([3]));await s.n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,3,geometry(3),new Int32Array([0,1,2]),new Int32Array([7,0,3,5,0]));}
 await frame({name:'resource-recreate',mode:1,world:true,g:601,seq:601,w:192,h:128});
 for(const s of sessions){for(const method of ['planWorldSprites','commitWorldSprites']){const name='Java_orsc_BrowserWebGLBridge_'+method,call=s.n[name];s.n[name]=(lib,e,seq,...args)=>call(lib,2,seq,...args);}}
 const stable=await frame({name:'atlas-epoch-two',mode:1,world:true,g:602,seq:602,w:192,h:128});
 const worker=sessions[2].backend;check(worker.stats.renderWorker.oracleMutatedReturnedBytes>0&&worker.stats.renderWorker.reusedBuffers>0,'Returned owned buffers not actually mutated/reused');const poolMutated=worker.mutateReturnedBuffers(0x5a);check(poolMutated>0,'No returned pool available for adversarial mutation');proof.returnedPoolMutatedBytes=poolMutated;
 // Mutate ALL borrowed producer arrays after ACK, while the worker also mutates
 // every actual returned snapshot before pooling. Do not resend unchanged
 // sky/lighting/texture/masks/atlas: persistent backend ownership must be real.
 for(const s of sessions){for(const a of s.inputs.keys())new Uint8Array(a.buffer,a.byteOffset,a.byteLength).fill(0xa5);}
 const retained=[];
 for(const s of sessions){const before={...s.commandCounts},p=s.lastPresent.map(a=>ArrayBuffer.isView(a)?new a.constructor(a):a),o=s.lastOverlay.map(a=>ArrayBuffer.isView(a)?new a.constructor(a):a);o[4]=603;o[5]=603;await s.n.Java_orsc_BrowserWebGLBridge_overlayFrame(...o);await s.n.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,1,3,3,0,1,1,1,192,128,2]));await s.n.Java_orsc_BrowserWebGLBridge_present(...p);await finish(s,{world:true});retained.push(await read(s));for(const name of ['skyFrame','lightingFrame','uploadTexture','uploadSkyGeometry','uploadLightingMask','commitWorldSprites'])check((s.commandCounts['Java_orsc_BrowserWebGLBridge_'+name]||0)===(before['Java_orsc_BrowserWebGLBridge_'+name]||0),'Resource resend masked ownership '+s.name+'/'+name);}
 for(let i=0;i<3;i++)compare('post-ACK-resource-lifetime-'+i,stable[i],retained[i]);compare('post-ACK-main-worker',retained[0],retained[2]);proof.borrowedAndReturnedLifetime=true;
 check(proof.coloredComponents>100000,'Blank color proof');proof.finalWorker={...worker.stats.renderWorker};proof.completedMeans='Backend endFrameWork/terminal presentation completed and oracle readPixels captured; not compositor scanout/GPU-fence timing';
 const disposal=await worker.disposeAndConfirm();check(disposal.disposed&&disposal.gpuBytes===0&&disposal.spriteResourceBytes===0,'Worker actual disposal ledger did not clear');proof.confirmedWorkerDisposal=disposal;
 for(const s of sessions.filter(s=>!s.worker)){s.backend.dispose();s.backend.dispose();for(const key of ['gpuBytes','spriteResourceBytes','shadowResourceBytes','skyResourceBytes','overlayCoverageBytes','overlayRgbBytes','retainedResourceBytes'])check(s.backend.stats[key]===0,'Main ledger leak '+s.name+'/'+key);}
 proof.passed=true;
}catch(error){proof.errors.push(String(error)+'\n'+String(error.stack||''));const s=sessions.find(x=>x.worker);if(s)proof.failureWorkerState={queue:s.backend.stats.renderWorker,physical:s.backend.stats.physicalOutput,lastError:s.backend.stats.lastError};}
finally{for(const s of sessions)try{s.backend.dispose();}catch(_){}if(originalDpr)Object.defineProperty(window,'devicePixelRatio',originalDpr);proof.completed=true;document.getElementById('status').textContent=JSON.stringify(proof,null,2);}
