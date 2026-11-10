import {createBrowserWebGL as original} from './original-webgl.mjs';
import {createBrowserWebGL as current} from './webgl.js';
const proof=window.colorFrustumProof={completed:false,passed:false,cases:[],errors:[],uploadedChunks:0,comparedBytes:0,changedBytes:0,coloredComponents:0,culledChunkFrames:0};
const require=(v,why)=>{if(!v)throw Error(why);};
const fetchText=async name=>{const r=await fetch('./shaders/'+name);if(!r.ok)throw Error('Missing frozen shader '+name);return r.text();};
const identity=()=>Float32Array.from([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]);
function multiply(a,b){const m=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++){let n=0;for(let k=0;k<4;k++)n+=a[k*4+r]*b[c*4+k];m[c*4+r]=n;}return m;}
function camera(frame){
 const m=identity();if(frame%4===0){m[0]=m[5]=frame%8===0?1:.2;return m;}
 const angle=(frame%11-5)*.09,c=Math.cos(angle),s=Math.sin(angle),near=frame%7===0?19.99:.1,far=400;
 const p=Float32Array.from([1.2,0,0,0,0,1.6,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0]);
 return multiply(p,Float32Array.from([c,0,s,0,0,1,0,0,-s,0,c,0,0,0,-20,1]));
}
function geometry(id,frame=0){
 const center=id===1?0:id===2&&frame%5===0?0:10+id*.4,half=id===1?.7:.25;
 const z=id===1&&frame%13===0?[-1.5,.2,0]:[.01*(id%7),.01*(id%7),.01*(id%7)];
 const v=new Float32Array(84),xy=[[center-half,-half],[center+half,-half],[center,half]];
 for(let i=0;i<3;i++){v.set([xy[i][0],xy[i][1],z[i]],i*28);v.set([.3+id%3*.2,.4+id%4*.1,.8,.9,i===1?1:0,i===2?1:0],i*28+3);v.set([.6,.5,.7],i*28+15);v[i*28+19]=1;v[i*28+21]=id%6;}
 return v;
}
try{
 const [vertex,fragment,depthVertex,depthFragment]=await Promise.all(['world.vert','world.frag','depth.vert','depth.frag'].map(fetchText));
 const sessions=[];
 for(const [id,create]of [['original',original],['current',current]]){
  const canvas=document.getElementById(id),backend=create(canvas),n=backend.natives,gl=canvas.getContext('webgl2');
  await n.Java_orsc_BrowserWebGLBridge_initialize(null,vertex,fragment);await n.Java_orsc_BrowserWebGLBridge_initializeLighting(null,depthVertex,depthFragment);await n.Java_orsc_BrowserWebGLBridge_initializeSky();
  await n.Java_orsc_BrowserWebGLBridge_skyFrame(null,new Float32Array(20),new Float32Array(4096));
  for(const [tex,pixels,alpha]of [[7,[0xff8844ff,0x44ff88ff,0x8844ffff,0xffffffff],false],[8,[0xff0000ff,0,0,0x00ff00ff],true],[9,[0x9988ff88,0x00ff0088,0xff998866,0x8899ff88],true]])await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,tex,2,2,new Int32Array(pixels),alpha);
  for(let chunk=1;chunk<=101;chunk++)await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,chunk,geometry(chunk),new Int32Array([0,1,2]),new Int32Array([7+chunk%3,0,3,chunk%6,chunk%3?1:0]));
  sessions.push({canvas,backend,n,gl});
 }
 require(sessions[1].backend.stats.colorFrustumCulling===true,'Exact production shader did not authorize color culling');
 const ext=sessions[1].gl.getExtension('WEBGL_debug_renderer_info');proof.renderer=ext?sessions[1].gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):sessions[1].backend.stats.renderer;
 for(let frame=0;frame<64;frame++){
  const width=frame<32?192:320,height=frame<32?128:192,views=[];
  for(const session of sessions){
   const {n,gl,backend}=session;
   await n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,1,geometry(1,frame),new Int32Array([0,1,2]),new Int32Array([8,0,3,1,1]));
   await n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,2,geometry(2,frame),new Int32Array(frame%2?[2,1,0]:[0,1,2]),new Int32Array([9,0,3,2,1]));
   await n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,101,geometry(101,frame),new Int32Array([0,1,2]),new Int32Array([7,0,3,5,0]));
   if(frame===20)await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,2,2,new Int32Array([0xff88ff88,0,0x99ff4488,0x4488ff88]),true);
   const matrices=new Float32Array(48),clip=camera(frame),world=identity();
   const large=frame%16===15?1048576:frame%16===14?-1048576:0;if(large){clip.set(identity());clip[12]=-large;}
   matrices.set(clip);matrices.set(world,16);matrices.set(identity(),32);
   const light=new Float32Array(81);light[0]=light[1]=1;light[3]=light[4]=64;light[5]=1;light[6]=1;light[11]=1;light[16]=light[17]=1/64;light[18]=light[19]=1;light[15]=0;
   const shadow=identity();shadow[0]=shadow[5]=.02;light.set(shadow,32);light.set(shadow,48);light.set(shadow,64);
   await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);
   const values=new Float32Array(30);values[0]=values[1]=values[2]=values[3]=1;values[4]=values[5]=values[6]=1;values[11]=1;values[29]=frame%3-1;
   const draws=[];for(let id=1;id<=101;id++)draws.push(id,large,0,frame%2*256,frame%3*256,63,id%3-1,id===101?2:0);
   const render=async()=>{
    await n.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,1,101,101,0,0,0,1,width,height,3]));
    await n.Java_orsc_BrowserWebGLBridge_present(null,width,height,true,matrices,values,new Int32Array(draws),new Int32Array(width*height).fill(0x01000000));
   };
   for(let warm=0;warm<(frame===0||frame===32?30:1);warm++)await render();
   const deadline=performance.now()+10000;
   while(backend.stats.worldReadiness.phase!=='ready'&&performance.now()<deadline){
    // WebGL fence visibility needs a new browser task, not only resolved-Promise microtasks.
    gl.finish();await new Promise(resolve=>setTimeout(resolve,0));await render();
   }
   proof.lastSource={backend:session.canvas.id,frame,uploadedChunks:backend.stats.uploadedChunks,readiness:{...backend.stats.worldReadiness}};
   const pixels=new Uint8Array(width*height*4);gl.readPixels(0,0,width,height,gl.RGBA,gl.UNSIGNED_BYTE,pixels);require(gl.getError()===gl.NO_ERROR,'Actual GPU error at frame '+frame);
   require(backend.stats.uploadedChunks===101&&backend.stats.worldReadiness.phase==='ready','Incomplete residency/readiness: '+JSON.stringify(proof.lastSource));
   require(backend.stats.shadowNearDraws===101&&backend.stats.shadowFarDraws===100,'Offscreen shadow casters or animated far exclusions changed');
   views.push(pixels);
  }
  let mismatches=0;for(let n=0;n<views[0].length;n++){if(views[0][n]!==views[1][n])mismatches++;if(n%4!==3&&views[0][n]!==0)proof.coloredComponents++;}
  proof.comparedBytes+=views[0].length;proof.changedBytes+=mismatches;proof.culledChunkFrames+=sessions[1].backend.stats.colorCulledChunkDraws;
  proof.cases.push({frame,width,height,mismatches,colorCulled:sessions[1].backend.stats.colorCulledChunkDraws,nearCasters:101,farCasters:100});require(mismatches===0,'Frame '+frame+' differs in '+mismatches+' actual GPU bytes');
 }
 proof.uploadedChunks=sessions[1].backend.stats.uploadedChunks;require(proof.culledChunkFrames>100,'Culling was never meaningfully exercised');require(proof.coloredComponents>1000,'No meaningful visible color geometry');
 for(const s of sessions){s.backend.dispose();require(s.backend.stats.gpuBytes===0&&s.backend.stats.shadowResourceBytes===0,'Resources leaked on dispose');}
 proof.passed=true;
}catch(error){proof.errors.push(String(error.stack||error));}
finally{proof.completed=true;}
