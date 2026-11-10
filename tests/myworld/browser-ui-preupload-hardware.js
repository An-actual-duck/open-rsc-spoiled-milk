import {createBrowserWebGL as original} from './original/webgl.js';
import {createBrowserWebGL as current} from './webgl.js';
import {initializeSession,prepareFrame,finishFrame,cases} from './browser-owned-scenario.mjs';
const proof=window.uiPreuploadProof={completed:false,passed:false,cases:[],errors:[],comparedBytes:0,changedBytes:0,coloredComponents:0,transitions:0,scope:'Actual production shader/native sky isolated pipeline differential, no CheerpJ/gameplay/FPS claim'};
const require=(value,why)=>{if(!value)throw Error(why);};
const reject=async(p,pattern)=>{try{await p;}catch(e){require(pattern.test(String(e)),String(e));return;}throw Error('Expected rejection '+pattern);};
const text=async name=>(await fetch('./shaders/'+name)).text();
const binary=async(name,Type)=>new Type(await(await fetch('./sky/'+name)).arrayBuffer());
try{
 const [vertex,fragment,depthVertex,depthFragment]=await Promise.all(['world.vert','world.frag','depth.vert','depth.frag'].map(text)),shaders={vertex,fragment,depthVertex,depthFragment};
 const geometry=await binary('geometry.bin',Float32Array),ranges=await binary('ranges.bin',Int32Array),skies=[];
 for(let i=0;i<3;i++)skies.push({geometry,ranges,values:await binary('values'+i+'.bin',Float32Array),palette:await binary('palette'+i+'.bin',Float32Array)});
 const sessions=[];
 for(const [name,create,uiPreupload,benchmarkControl]of [['original',original,false,false],['off',current,false,false],['on',current,true,false],['mixed',current,false,true]]){
  const canvas=document.getElementById(name),backend=create(canvas,()=>{},{ownedState:true,streamUploadValidation:true,uiPreupload,benchmarkControl}),gl=canvas.getContext('webgl2'),session=await initializeSession(backend,shaders,skies);sessions.push({...session,name,canvas,gl});
 }
 const ext=sessions[2].gl.getExtension('WEBGL_debug_renderer_info');proof.renderer=ext?sessions[2].gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):sessions[2].backend.stats.renderer;
 // Give real GL fence visibility browser tasks; repeated resolved Promises do not suffice.
 for(let warm=0;warm<32;warm++){for(const session of sessions){const render=await prepareFrame(session,{name:'warm',mode:1,world:true,seq:0,g:0,w:192,h:128});await render();await finishFrame(session);session.gl.finish();}await new Promise(r=>setTimeout(r,0));}
 proof.warmSourceDiagnostics=sessions.map(s=>({mode:s.name,declaration:s.readinessDeclaration,createdSources:s.fixtureResources,uploadedChunks:s.backend.stats.uploadedChunks,worldSpriteGroups:s.backend.stats.worldSpriteGroups,spriteFrameGroups:s.backend.stats.spriteFrameGroups,spriteResidentImages:s.backend.stats.spriteResidentImages,lightingMasks:s.backend.stats.lightingMaskBytes,readiness:s.backend.stats.worldReadiness}));
 for(const session of sessions)require(session.backend.stats.worldReadiness.phase==='ready','Incomplete warm readiness '+session.name+JSON.stringify(session.backend.stats.worldReadiness));
 proof.readyChunks=sessions.map(s=>({mode:s.name,uploaded:s.backend.stats.uploadedChunks,ready:s.backend.stats.worldReadiness.phase}));
 for(const test of cases()){
  const views=[],metadata=[];
  for(const s of sessions){const {backend,gl}=s,before=backend.stats.uiPreuploadActive,render=await prepareFrame(s,test);
   if(s.name==='mixed'){backend.queueUiPreupload(!before);require(backend.stats.uiPreuploadActive===before,'Policy changed inside preparation');}
   await render();
   if(s.name!=='original')require(backend.stats.presentedUiPreupload===before,'Presented UI upload policy mixed across passes');
   const data=new Uint8Array(test.w*test.h*4);gl.readPixels(0,0,test.w,test.h,gl.RGBA,gl.UNSIGNED_BYTE,data);
   require(gl.getError()===gl.NO_ERROR,'GPU error '+s.name+'/'+test.name);require(gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING)===null&&gl.getParameter(gl.READ_FRAMEBUFFER_BINDING)===null,'Nondefault FBO leaked '+s.name);
   require(gl.getParameter(gl.COLOR_WRITEMASK).every(Boolean),'Color mask leaked '+s.name);require(!gl.isEnabled(gl.SCISSOR_TEST),'Scissor leaked '+s.name);
   require(backend.stats.uploadedChunks===3,'Geometry residency changed');
   if(test.world&&test.mode===1&&!(test.level<0))require(backend.stats.shadowNearDraws===3,'Shadow casters missing');
   views.push(data);metadata.push({mode:s.name,presented:backend.stats.presentedUiPreupload,skyDraws:backend.stats.skyDrawCalls,near:backend.stats.shadowNearDraws,far:backend.stats.shadowFarDraws,sprites:backend.stats.worldSpriteGroups});
   await finishFrame(s);
   if(s.name==='mixed'){require(backend.stats.uiPreuploadActive===!before,'Pending policy not committed');proof.transitions++;}
  }
  let mismatches=0;for(let mode=1;mode<views.length;mode++)for(let p=0;p<views[0].length;p++)if(views[mode][p]!==views[0][p])mismatches++;
  for(let p=0;p<views[0].length;p++)if(p%4!==3&&views[0][p])proof.coloredComponents++;
  proof.comparedBytes+=views[0].length*3;proof.changedBytes+=mismatches;proof.cases.push({name:test.name,width:test.w,height:test.h,mismatches,metadata});require(mismatches===0,'GPU color mismatch '+test.name+': '+mismatches);await new Promise(r=>setTimeout(r,0));
 }
 // Inject a real INVALID_VALUE into a same-size RGB upload; valid work must not draw afterward.
 proof.rgbUploadFaults=[];
 for(const streaming of [true,false]){
  const canvas=document.createElement('canvas'),backend=current(canvas,()=>{},{ownedState:true,streamUploadValidation:streaming,uiPreupload:true,benchmarkControl:true}),gl=canvas.getContext('webgl2'),session=await initializeSession(backend,shaders,skies);
  const warm=await prepareFrame(session,cases()[1]);await warm();await finishFrame(session);require(gl.getError()===gl.NO_ERROR,'Fault warm frame invalid');
  const fresh=backend.stats.javaFrames,last=backend.stats.lastFrame,render=await prepareFrame(session,{...cases()[1],seq:5,g:2});
  const upload=gl.texSubImage2D.bind(gl),originalMethods=new Map(),counts={};let faulted=false,armed=true;
  for(const name of ['drawArrays','drawElements','clear','copyTexSubImage2D','createQuery','beginQuery','getQueryParameter','getParameter','getShaderParameter','getProgramParameter']){const original=gl[name].bind(gl);originalMethods.set(name,original);counts[name]=0;gl[name]=(...args)=>{if(faulted)counts[name]++;return original(...args);};}
  gl.texSubImage2D=(...args)=>{if(armed&&args[4]===192&&args[5]===128&&args[6]===gl.RGBA){armed=false;faulted=true;upload(gl.TEXTURE_2D,0,0,0,-1,1,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array(4));}return upload(...args);};
  let error='';try{await render();}catch(e){error=String(e);}require(faulted,'RGB fault injection was not exercised');require(/Streaming upload GL error|Coverage GL error/.test(error),'Actual RGB error was not rejected '+error);
  const receipt={streaming,error,counts:{...counts},freshBefore:fresh,freshAfter:backend.stats.javaFrames,backendFailed:backend.stats.backendFailed,phase:backend.stats.worldReadiness.phase};
  require(Object.values(counts).every(v=>v===0),'Draw/query occurred after actual RGB fault '+JSON.stringify(receipt));require(backend.stats.javaFrames===fresh&&backend.stats.lastFrame===last,'Failed prefix published a frame');require(backend.stats.backendFailed&&backend.stats.worldReadiness.phase==='error','RGB error not sticky');
  for(const name of ['queueUiPreupload','queueOwnedState','queueStreamUploadValidation']){let blocked=false;try{backend[name](false);}catch{blocked=true;}require(blocked,'Failed RGB backend accepted '+name);}
  await reject(finishFrame(session),/unavailable/);require(Object.values(counts).every(v=>v===0),'Later native/mode request drew after poison');
  gl.texSubImage2D=upload;for(const [name,original]of originalMethods)gl[name]=original;backend.dispose();backend.dispose();proof.rgbUploadFaults.push(receipt);
 }
 require(proof.coloredComponents>100000,'Blank image proof');require(proof.transitions===42,'Live UI upload transitions not exercised');
 for(const s of sessions){s.backend.dispose();s.backend.dispose();for(const key of ['gpuBytes','shadowResourceBytes','skyResourceBytes','overlayCoverageBytes','overlayRgbBytes','retainedResourceBytes'])require(s.backend.stats[key]===0,'Resource ledger leaked '+s.name+':'+key);}
 // Actual browser async shader authority: duplicate initialization and Stop mid-digest.
 const c=document.createElement('canvas'),stopped=current(c,()=>{},{ownedState:true,streamUploadValidation:true,uiPreupload:true,benchmarkControl:true}),n=stopped.natives,pending=n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);
 const duplicate=n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);stopped.dispose();await reject(duplicate,/initialized twice/);await reject(pending,/backend unavailable/);stopped.dispose();proof.stopInitRace=true;
 // Context loss is a genuine WebGL event, not a CPU-only imitation.
 const lostCanvas=document.createElement('canvas'),lost=current(lostCanvas,()=>{},{ownedState:true,streamUploadValidation:true,uiPreupload:true,benchmarkControl:true});await lost.natives.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);const lostGL=lostCanvas.getContext('webgl2'),lose=lostGL.getExtension('WEBGL_lose_context');require(lose,'Loss extension missing');lose.loseContext();
 const deadline=performance.now()+5000;while(!lost.stats.contextLost&&performance.now()<deadline)await new Promise(r=>setTimeout(r,5));require(lost.stats.contextLost,'No actual context-loss event');let blocked=false;try{lost.queueUiPreupload(false);}catch{blocked=true;}require(blocked,'Lost owned backend accepted next policy');lost.dispose();proof.contextLoss=true;
 require(proof.rgbUploadFaults.length===2,'Missing actual RGB fault modes');proof.passed=true;
}catch(error){proof.errors.push(String(error.stack||error));}
finally{proof.completed=true;}
