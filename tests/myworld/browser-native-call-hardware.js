import {createBrowserWebGL as original} from './original/webgl.js';
import {createBrowserWebGL as control} from './webgl.js';
import {createBrowserWebGL as synchronous} from './webgl-sync.js';
import {initializeSession,prepareFrame,finishFrame,cases} from './browser-owned-scenario.mjs';
const proof=window.nativeCallProof={completed:false,passed:false,cases:[],errors:[],comparedBytes:0,changedBytes:0,coloredComponents:0,nativeModes:[],scope:'Actual production shader/native sky dispatch pixel differential; no CheerpJ/gameplay/FPS claim'};
const require=(value,why)=>{if(!value)throw Error(why);};
const text=async name=>(await fetch('./shaders/'+name)).text();
const binary=async(name,Type)=>new Type(await(await fetch('./sky/'+name)).arrayBuffer());
const reject=async(value,pattern)=>{try{await value;}catch(error){require(pattern.test(String(error)),String(error));return;}throw Error('Expected rejection '+pattern);};
try{
 const [vertex,fragment,depthVertex,depthFragment]=await Promise.all(['world.vert','world.frag','depth.vert','depth.frag'].map(text)),shaders={vertex,fragment,depthVertex,depthFragment};
 const geometry=await binary('geometry.bin',Float32Array),ranges=await binary('ranges.bin',Int32Array),skies=[];
 for(let i=0;i<3;i++)skies.push({geometry,ranges,values:await binary('values'+i+'.bin',Float32Array),palette:await binary('palette'+i+'.bin',Float32Array)});
 const sessions=[];
 for(const[name,create,mode]of [['original',original,'async'],['control',control,'async'],['sync',synchronous,'sync']]){
  const canvas=document.getElementById(name),backend=create(canvas,()=>{},{ownedState:true,streamUploadValidation:true,nativeCallDispatch:mode}),gl=canvas.getContext('webgl2');
  const session=await initializeSession(backend,shaders,skies);sessions.push({...session,name,canvas,gl});proof.nativeModes.push(name==='original'?'async':backend.stats.nativeCallDispatch.active);
  const result=backend.natives.Java_orsc_BrowserWebGLBridge_lightingFrame(null,new Float32Array(81));require((result instanceof Promise)===(mode==='async'),'Wrong actual native return type '+name);await result;
 }
 const ext=sessions[2].gl.getExtension('WEBGL_debug_renderer_info');proof.renderer=ext?sessions[2].gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):sessions[2].backend.stats.renderer;
 // Fence/readiness needs browser tasks; this fixture does not measure throughput.
 for(let warm=0;warm<32;warm++){for(const s of sessions){await(await prepareFrame(s,{name:'warm',mode:1,world:true,seq:0,g:0,w:192,h:128}))();await finishFrame(s);s.gl.finish();}await new Promise(resolve=>setTimeout(resolve,0));}
 proof.readyChunks=sessions.map(s=>({mode:s.name,uploaded:s.backend.stats.uploadedChunks,ready:s.backend.stats.worldReadiness.phase}));
 for(const s of sessions)require(s.backend.stats.worldReadiness.phase==='ready','Incomplete source readiness '+s.name+JSON.stringify(s.backend.stats.worldReadiness));
 for(const test of cases()){
  const views=[],metadata=[];
  for(const s of sessions){await(await prepareFrame(s,test))();const{backend,gl}=s,data=new Uint8Array(test.w*test.h*4);gl.readPixels(0,0,test.w,test.h,gl.RGBA,gl.UNSIGNED_BYTE,data);
   require(gl.getError()===gl.NO_ERROR,'GPU error '+s.name+'/'+test.name);require(gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING)===null&&gl.getParameter(gl.READ_FRAMEBUFFER_BINDING)===null,'Framebuffer leaked '+s.name);require(gl.getParameter(gl.COLOR_WRITEMASK).every(Boolean)&&!gl.isEnabled(gl.SCISSOR_TEST),'Output state leaked');
   require(backend.stats.uploadedChunks===3,'Residency changed');if(test.world&&test.mode===1&&!(test.level<0))require(backend.stats.shadowNearDraws===3,'Missing shadow casters');
   views.push(data);metadata.push({mode:s.name,sky:backend.stats.skyDrawCalls,near:backend.stats.shadowNearDraws,far:backend.stats.shadowFarDraws,sprites:backend.stats.worldSpriteGroups});await finishFrame(s);
  }
  let mismatches=0;for(let mode=1;mode<views.length;mode++)for(let p=0;p<views[0].length;p++)if(views[mode][p]!==views[0][p])mismatches++;
  for(let p=0;p<views[0].length;p++)if(p%4!==3&&views[0][p])proof.coloredComponents++;
  proof.comparedBytes+=views[0].length*2;proof.changedBytes+=mismatches;proof.cases.push({name:test.name,width:test.w,height:test.h,mismatches,metadata});require(mismatches===0,'GPU mismatch '+test.name);await new Promise(resolve=>setTimeout(resolve,0));
 }
 require(proof.coloredComponents>100000,'Blank image proof');
 for(const s of sessions){s.backend.dispose();s.backend.dispose();for(const key of ['gpuBytes','shadowResourceBytes','skyResourceBytes','overlayCoverageBytes','overlayRgbBytes','retainedResourceBytes'])require(s.backend.stats[key]===0,'Resource ledger leak '+s.name+':'+key);}
 const stopped=synchronous(document.createElement('canvas'),()=>{},{ownedState:true,nativeCallDispatch:'sync'}),n=stopped.natives,pending=n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment),duplicate=n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);stopped.dispose();await reject(duplicate,/initialized twice/);await reject(pending,/unavailable/);stopped.dispose();proof.stopInitRace=true;
 const lostCanvas=document.createElement('canvas'),lost=synchronous(lostCanvas,()=>{},{ownedState:true,streamUploadValidation:true,nativeCallDispatch:'sync'});await lost.natives.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);const loss=lostCanvas.getContext('webgl2').getExtension('WEBGL_lose_context');require(loss,'No actual loss extension');loss.loseContext();const deadline=performance.now()+5000;while(!lost.stats.contextLost&&performance.now()<deadline)await new Promise(resolve=>setTimeout(resolve,5));require(lost.stats.contextLost,'No actual context-loss event');let blocked=false;try{lost.natives.Java_orsc_BrowserWebGLBridge_lightingFrame(null,new Float32Array(81));}catch{blocked=true;}require(blocked,'Lost synchronous backend accepted native work');lost.dispose();proof.contextLoss=true;
 proof.passed=true;
}catch(error){proof.errors.push(String(error.stack||error));}
finally{proof.completed=true;}
