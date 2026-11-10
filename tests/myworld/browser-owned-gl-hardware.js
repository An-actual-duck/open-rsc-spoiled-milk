import {createBrowserWebGL as original} from './original/webgl.js';
import {createBrowserWebGL as current} from './webgl.js';
import {initializeSession,prepareFrame,finishFrame,cases} from './browser-owned-scenario.mjs';
const proof=window.ownedStateProof={completed:false,passed:false,cases:[],errors:[],comparedBytes:0,changedBytes:0,coloredComponents:0,transitions:0,scope:'Actual production shader/native sky isolated pipeline differential, no CheerpJ/gameplay/FPS claim'};
const require=(value,why)=>{if(!value)throw Error(why);};
const reject=async(p,pattern)=>{try{await p;}catch(e){require(pattern.test(String(e)),String(e));return;}throw Error('Expected rejection '+pattern);};
const text=async name=>(await fetch('./shaders/'+name)).text();
const binary=async(name,Type)=>new Type(await(await fetch('./sky/'+name)).arrayBuffer());
try{
 const [vertex,fragment,depthVertex,depthFragment]=await Promise.all(['world.vert','world.frag','depth.vert','depth.frag'].map(text)),shaders={vertex,fragment,depthVertex,depthFragment};
 const geometry=await binary('geometry.bin',Float32Array),ranges=await binary('ranges.bin',Int32Array),skies=[];
 for(let i=0;i<3;i++)skies.push({geometry,ranges,values:await binary('values'+i+'.bin',Float32Array),palette:await binary('palette'+i+'.bin',Float32Array)});
 const sessions=[];
 for(const [name,create,ownedState,benchmarkControl]of [['original',original,false,false],['off',current,false,false],['on',current,true,false],['mixed',current,false,true]]){
  const canvas=document.getElementById(name),backend=create(canvas,()=>{},{ownedState,benchmarkControl}),gl=canvas.getContext('webgl2'),session=await initializeSession(backend,shaders,skies);sessions.push({...session,name,canvas,gl});
 }
 const ext=sessions[2].gl.getExtension('WEBGL_debug_renderer_info');proof.renderer=ext?sessions[2].gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):sessions[2].backend.stats.renderer;
 // Give real GL fence visibility browser tasks; repeated resolved Promises do not suffice.
 for(let warm=0;warm<32;warm++){for(const session of sessions){const render=await prepareFrame(session,{name:'warm',mode:1,world:true,seq:0,g:0,w:192,h:128});await render();await finishFrame(session);session.gl.finish();}await new Promise(r=>setTimeout(r,0));}
 for(const session of sessions)require(session.backend.stats.worldReadiness.phase==='ready','Incomplete warm readiness '+session.name+JSON.stringify(session.backend.stats.worldReadiness));
 proof.readyChunks=sessions.map(s=>({mode:s.name,uploaded:s.backend.stats.uploadedChunks,ready:s.backend.stats.worldReadiness.phase}));
 for(const test of cases()){
  const views=[],metadata=[];
  for(const s of sessions){const {backend,gl}=s,before=backend.stats.ownedStateActive,render=await prepareFrame(s,test);
   if(s.name==='mixed'){backend.queueOwnedState(!before);require(backend.stats.ownedStateActive===before,'Policy changed inside preparation');}
   await render();
   if(s.name!=='original')require(backend.stats.presentedOwnedState===before,'Presented ownership mixed across passes');
   const data=new Uint8Array(test.w*test.h*4);gl.readPixels(0,0,test.w,test.h,gl.RGBA,gl.UNSIGNED_BYTE,data);
   require(gl.getError()===gl.NO_ERROR,'GPU error '+s.name+'/'+test.name);require(gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING)===null&&gl.getParameter(gl.READ_FRAMEBUFFER_BINDING)===null,'Nondefault FBO leaked '+s.name);
   require(gl.getParameter(gl.COLOR_WRITEMASK).every(Boolean),'Color mask leaked '+s.name);require(!gl.isEnabled(gl.SCISSOR_TEST),'Scissor leaked '+s.name);
   require(backend.stats.uploadedChunks===3,'Geometry residency changed');
   if(test.world&&test.mode===1&&!(test.level<0))require(backend.stats.shadowNearDraws===3,'Shadow casters missing');
   views.push(data);metadata.push({mode:s.name,presented:backend.stats.presentedOwnedState,skyDraws:backend.stats.skyDrawCalls,near:backend.stats.shadowNearDraws,far:backend.stats.shadowFarDraws,sprites:backend.stats.worldSpriteGroups});
   await finishFrame(s);
   if(s.name==='mixed'){require(backend.stats.ownedStateActive===!before,'Pending policy not committed');proof.transitions++;}
  }
  let mismatches=0;for(let mode=1;mode<views.length;mode++)for(let p=0;p<views[0].length;p++)if(views[mode][p]!==views[0][p])mismatches++;
  for(let p=0;p<views[0].length;p++)if(p%4!==3&&views[0][p])proof.coloredComponents++;
  proof.comparedBytes+=views[0].length*3;proof.changedBytes+=mismatches;proof.cases.push({name:test.name,width:test.w,height:test.h,mismatches,metadata});require(mismatches===0,'GPU color mismatch '+test.name+': '+mismatches);await new Promise(r=>setTimeout(r,0));
 }
 require(proof.coloredComponents>100000,'Blank image proof');require(proof.transitions===42,'Live policy transitions not exercised');
 for(const s of sessions){s.backend.dispose();s.backend.dispose();for(const key of ['gpuBytes','shadowResourceBytes','skyResourceBytes','overlayCoverageBytes','overlayRgbBytes','retainedResourceBytes'])require(s.backend.stats[key]===0,'Resource ledger leaked '+s.name+':'+key);}
 // Actual browser async shader authority: duplicate initialization and Stop mid-digest.
 const c=document.createElement('canvas'),stopped=current(c,()=>{},{ownedState:true,benchmarkControl:true}),n=stopped.natives,pending=n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);
 const duplicate=n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);stopped.dispose();await reject(duplicate,/initialized twice/);await reject(pending,/backend unavailable/);stopped.dispose();proof.stopInitRace=true;
 // Context loss is a genuine WebGL event, not a CPU-only imitation.
 const lostCanvas=document.createElement('canvas'),lost=current(lostCanvas,()=>{},{ownedState:true,benchmarkControl:true});await lost.natives.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);const lostGL=lostCanvas.getContext('webgl2'),lose=lostGL.getExtension('WEBGL_lose_context');require(lose,'Loss extension missing');lose.loseContext();
 const deadline=performance.now()+5000;while(!lost.stats.contextLost&&performance.now()<deadline)await new Promise(r=>setTimeout(r,5));require(lost.stats.contextLost,'No actual context-loss event');let blocked=false;try{lost.queueOwnedState(false);}catch{blocked=true;}require(blocked,'Lost owned backend accepted next policy');lost.dispose();proof.contextLoss=true;
 proof.passed=true;
}catch(error){proof.errors.push(String(error.stack||error));}
finally{proof.completed=true;}
