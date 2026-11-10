import {createBrowserWebGL} from './webgl.js';
import {buildWorldCutoutMips,createWorldTextureSampling} from './browser-world-textures.js';
const canvas=document.querySelector('canvas'),status=document.querySelector('#status');
const identity=()=>Float32Array.from([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]);
const transpose=a=>Float32Array.from({length:16},(_,n)=>a[(n%4)*4+Math.floor(n/4)]);
const json=async p=>(await fetch(p)).json(),bytes=async p=>new Uint8Array(await(await fetch(p)).arrayBuffer());
const roi={x:240,y:100,w:480,h:360};
let manifest,shader,vertices,indices,batches,textures,backend,replaying=false,distance='mid';
const floorCorners=[[-512,0,-512],[512,0,-512],[512,0,512],[-512,0,512]];
const wallCorners=[[-512,0,-512],[512,0,-512],[512,-256,-512],[-512,-256,-512]];
async function load(){
 if(manifest)return;
 manifest=await json('./assets/manifest.json');
 shader=await Promise.all(['world.vert','world.frag','depth.vert','depth.frag'].map(p=>fetch('./shaders/'+p).then(r=>r.text())));
 vertices=new Float32Array((await bytes('./assets/tree.vertices')).buffer);
 indices=new Int32Array((await bytes('./assets/tree.indices')).buffer);
 batches=new Int32Array((await bytes('./assets/tree.batches')).buffer);
 textures=new Map();
 for(const id of [8,6,2,17]){const meta=manifest.textures.find(t=>t.id===id);textures.set(id,{...meta,rgba:await bytes('./assets/texture-'+id+'.rgba')});}
}
function argb(rgba){const p=new Int32Array(rgba.length/4);for(let n=0;n<p.length;n++)p[n]=(rgba[n*4]<<24)|(rgba[n*4+1]<<16)|(rgba[n*4+2]<<8)|rgba[n*4+3];return p;}
// Actual production pixels tiled at128 world units (one map tile), not stretched
// across the entire fixture. Synthetic placement is explicitly not a map oracle.
function materialQuads(subject){
 const data=[],idx=[],bs=[];
 function quad(p,id){
  const first=data.length/28,t=textures.get(id);
  for(let k=0;k<4;k++){
   const row=new Array(28).fill(0);row.splice(0,3,...p[k]);row.splice(3,4,1,1,1,1);
   row[7]=((k===1||k===2)?t.width-.5:.5)/t.width;row[8]=((k>=2)?t.height-.5:.5)/t.height;
   row[14]=1;row.splice(15,3,1,1,1);row[19]=1;row[21]=1;data.push(...row);
  }
  const start=idx.length;idx.push(first,first+1,first+2,first,first+2,first+3);bs.push(id,start,6,1,0);
 }
 if(subject==='floor')for(let x=-512;x<512;x+=128)for(let z=-512;z<512;z+=128)quad([[x,0,z],[x+128,0,z],[x+128,0,z+128],[x,0,z+128]],6);
 if(subject==='wall')for(let x=-512;x<512;x+=128)for(let y=-256;y<0;y+=128)quad([[x,y,-512],[x+128,y,-512],[x+128,y+128,-512],[x,y+128,-512]],2);
 return [new Float32Array(data),new Int32Array(idx),new Int32Array(bs)];
}
async function create(mode){
 if(backend)backend.dispose();const scale=mode==='reference'?2:1;canvas.width=960*scale;canvas.height=540*scale;
 backend=createBrowserWebGL(canvas,()=>{},{worldTextureFiltering:['stable','cutout','cutout-nearest'].includes(mode)?mode:'nearest',colorFrustumCulling:false});
 const n=backend.natives;
 await n.Java_orsc_BrowserWebGLBridge_initialize(null,shader[0],shader[1]);
 await n.Java_orsc_BrowserWebGLBridge_initializeLighting(null,shader[2],shader[3]);await n.Java_orsc_BrowserWebGLBridge_initializeSky();
 for(const t of textures.values())await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,t.id,t.width,t.height,argb(t.rgba),t.transparent);
 await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,1,vertices,indices,batches);
 await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,2,...materialQuads('floor'));await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,3,...materialQuads('wall'));
 const light=new Float32Array(81);light[0]=1;await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);
 return backend;
}
function views(){return manifest.distanceViews[distance];}
async function draw(index,subject='scene'){
 if(subject===true)subject='tree';if(subject===false)subject='scene';
 const n=backend.natives,w=canvas.width,h=canvas.height,m=new Float32Array(48),u=new Float32Array(30);
 m.set(transpose(views()[index].clipRowMajor));m.set(transpose(views()[index].viewRowMajor),16);m.set(identity(),32);
 u.set([1,1,1,1,1,1,1]);u[11]=1;u[15]=.08;u[16]=.12;u[17]=.18;
 const ids={tree:[1],floor:[2],wall:[3],scene:[2,3,1]}[subject];if(!ids)throw Error('Invalid fixture subject');const ds=ids.flatMap(id=>[id,0,0,0,0,63,0,0]);
 await n.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,1,3,3,0,0,0,1,w,h,textures.size]));
 await n.Java_orsc_BrowserWebGLBridge_present(null,w,h,true,m,u,new Int32Array(ds),new Int32Array(w*h).fill(0x01000000));
 const gl=canvas.getContext('webgl2'),e=gl.getError();if(e)throw Error('Diagnostic GL error '+e);
}
function read(scale){
 const gl=canvas.getContext('webgl2'),raw=new Uint8Array(roi.w*roi.h*4*scale*scale);
 gl.readPixels(roi.x*scale,roi.y*scale,roi.w*scale,roi.h*scale,gl.RGBA,gl.UNSIGNED_BYTE,raw);
 if(scale===1)return raw;
 const result=new Uint8Array(roi.w*roi.h*4);
 for(let y=0;y<roi.h;y++)for(let x=0;x<roi.w;x++)for(let c=0;c<4;c++){
  let sum=0;for(let yy=0;yy<2;yy++)for(let xx=0;xx<2;xx++)sum+=raw[((y*2+yy)*roi.w*2+x*2+xx)*4+c];
  result[(y*roi.w+x)*4+c]=Math.round(sum/4);
 }
 return result;
}
function projectedBounds(points,index,inset=0){
 const m=views()[index].clipRowMajor,x=[],y=[];
 for(const p of points){const q=[...p,1],v=Array.from({length:4},(_,r)=>q.reduce((s,a,c)=>s+a*m[r*4+c],0));if(v[3]<=0)continue;x.push((v[0]/v[3]+1)*480);y.push((v[1]/v[3]+1)*270);}
 if(!x.length)return {x:0,y:0,w:0,h:0};
 const l=Math.max(roi.x,Math.ceil(Math.min(...x))+inset),r=Math.min(roi.x+roi.w,Math.floor(Math.max(...x))-inset),b=Math.max(roi.y,Math.ceil(Math.min(...y))+inset),t=Math.min(roi.y+roi.h,Math.floor(Math.max(...y))-inset);
 return {x:l,y:b,w:Math.max(0,r-l),h:Math.max(0,t-b)};
}
function regions(index){
 const points=[];for(let n=0;n<vertices.length;n+=28)points.push(Array.from(vertices.subarray(n,n+3)));
 return {full:roi,tree:projectedBounds(points,index,2),floor:projectedBounds([[-256,0,-128],[256,0,-128],[256,0,128],[-256,0,128]],index,4),wall:projectedBounds([[128,-256,-512],[512,-256,-512],[512,-128,-512],[128,-128,-512]],index,4)};
}
function intersection(a,b){const x=Math.max(a.x,b.x),y=Math.max(a.y,b.y);return {x,y,w:Math.max(0,Math.min(a.x+a.w,b.x+b.w)-x),h:Math.max(0,Math.min(a.y+a.h,b.y+b.h)-y)};}
function accumulate(state,image,ref,previous,prevRef,box,prevBox){
 for(let y=box.y;y<box.y+box.h;y++)for(let x=box.x;x<box.x+box.w;x++){const n=((y-roi.y)*roi.w+x-roi.x)*4;for(let c=0;c<3;c++){const e=image[n+c]-ref[n+c];state.squared+=e*e;state.samples++;}}
 if(previous){const common=intersection(box,prevBox);for(let y=common.y;y<common.y+common.h;y++)for(let x=common.x;x<common.x+common.w;x++){const n=((y-roi.y)*roi.w+x-roi.x)*4;for(let c=0;c<3;c++){const e=(image[n+c]-ref[n+c])-(previous[n+c]-prevRef[n+c]);state.temporal+=e*e;state.temporalSamples++;}}}
}
async function compare(mode,reference,subject){
 await create(mode);const states=Object.fromEntries(['full',subject].map(k=>[k,{squared:0,samples:0,temporal:0,temporalSamples:0}]));let previous=null,prevRef=null,prevRegions=null;const subjectSignals=[];
 for(let v=0;v<views().length;v++){
  await draw(v,subject);const image=read(mode==='reference'?2:1),allBoxes=regions(v),boxes={full:roi,[subject]:allBoxes[subject]};let changed=0,green=0;for(let n=0;n<image.length;n+=4){if(Math.max(Math.abs(image[n]-20),Math.abs(image[n+1]-31),Math.abs(image[n+2]-46))>5)changed++;green+=Math.max(0,image[n+1]-Math.max(image[n],image[n+2]));}if(!changed)throw Error('Empty actual subject '+subject+'/'+distance+'/'+mode+'/'+v);subjectSignals.push({view:v,coloredPixels:changed,greenSignal:green});
  if(mode==='reference')reference.push(image);
  else for(const [name,box]of Object.entries(boxes))accumulate(states[name],image,reference[v],previous,prevRef,box,prevRegions?.[name]);
  previous=image;prevRef=reference[v];prevRegions=boxes;
  if(v%8===0){status.textContent=`${distance} / ${subject} / ${mode}: view ${v+1}/${views().length}`;await new Promise(r=>setTimeout(r,0));}
 }
 const middleSignal=subjectSignals[48];
 const metrics=Object.fromEntries(Object.entries(states).map(([k,s])=>[k,{samples:s.samples,temporalSamples:s.temporalSamples,referenceRmse:s.samples?Math.sqrt(s.squared/s.samples):null,temporalResidualRmse:s.temporalSamples?Math.sqrt(s.temporal/s.temporalSamples):null}]));
 return {mode,distance,subject,frames:views().length,regionsAtMiddle:regions(48),metrics,subjectSignals,middleSignal,note:'Isolated actual subject; green signal is not alpha coverage proof',stats:{worldFrames:backend.stats.javaWorldFrames,mipBuildMs:backend.stats.worldMipBuildMilliseconds,mipUploadedBytes:backend.stats.worldMipUploadedBytes,renderer:backend.stats.renderer,vendor:backend.stats.vendor}};
}
// Captures the SAME freshly submitted drawingbuffer synchronously, before browser
// compositing may discard it. No screenshot of a later blank canvas is accepted.
window.renderSnapshot=async(mode,index=48,treeOnly=false,requestedDistance='mid')=>{
 if(!['nearest','stable','cutout','cutout-nearest','reference'].includes(mode)||!['near','mid','far'].includes(requestedDistance)||!Number.isInteger(index)||index<0||index>=96)throw Error('Invalid snapshot request');
 replaying=false;await load();distance=requestedDistance;await create(mode);await draw(index,treeOnly);
 const png=canvas.toDataURL('image/png');
 return {mode,index,distance,subject:treeOnly===true?'tree':treeOnly===false?'scene':treeOnly,width:canvas.width,height:canvas.height,png,regions:regions(index),renderer:backend.stats.renderer,sourceCommit:(await json('./stage.json')).sourceCommit};
};
async function run(options={}){
 document.querySelector('#start').disabled=true;
 try{
  await load();const gl=canvas.getContext('webgl2'),ext=gl.getExtension('WEBGL_debug_renderer_info'),renderer=gl.getParameter(ext?ext.UNMASKED_RENDERER_WEBGL:gl.RENDERER);
  if(/swiftshader|llvmpipe|software rasterizer/i.test(renderer))throw Error('Software renderer rejected: '+renderer);
  const results=[];for(const d of options.distances||['near','mid','far']){if(!['near','mid','far'].includes(d))throw Error('Invalid distance');distance=d;for(const subject of options.subjects||['tree','floor','wall']){if(!['tree','floor','wall'].includes(subject))throw Error('Invalid subject');const reference=[];for(const mode of ['reference','nearest','stable','cutout','cutout-nearest'])results.push(await compare(mode,reference,subject));}}
  const t=textures.get(17),start=performance.now();for(let n=0;n<120;n++)buildWorldCutoutMips(t.width,t.height,t.rgba);const stableCpu=(performance.now()-start)/120,cutoutCpuPlan=createWorldTextureSampling({}, {enabled:true,mode:'cutout'}),cutoutStart=performance.now();for(let n=0;n<120;n++)cutoutCpuPlan.plan(t.width,t.height,t.rgba,true);const cutoutCpu=(performance.now()-cutoutStart)/120;
  const result={pass:true,qualityAccepted:false,scope:'Production shaders/tree/palette;independent tree/floor6/wall2 passes without mutual occlusion;128-unit tiling;no shadows/fog;not FPS acceptance',userAgent:navigator.userAgent,renderer,roi,reference:'2x physical-raster NEAREST,box-downsampled;not native LWJGL pixel parity',results,animatedTexture17CpuMsPerBuild:{stable:stableCpu,cutout:cutoutCpu},source:await json('./stage.json')};
  window.textureStabilityResult=result;status.textContent=JSON.stringify(result,null,2);document.querySelector('#play').disabled=false;return result;
 }catch(error){window.textureStabilityResult={pass:false,error:String(error),stack:error.stack};status.textContent=JSON.stringify(window.textureStabilityResult);throw error;}
}
window.renderSubjectSnapshot=(mode,index,subject,requestedDistance)=>window.renderSnapshot(mode,index,subject,requestedDistance);
window.runTextureStability=run;document.querySelector('#start').onclick=()=>run();
document.querySelector('#play').onclick=async()=>{replaying=!replaying;if(!replaying)return;await load();await create(document.querySelector('#display').value);let v=0;while(replaying){await draw(v++%views().length);await new Promise(r=>setTimeout(r,33));}};
