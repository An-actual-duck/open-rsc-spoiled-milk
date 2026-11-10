// Shared deterministic full-pipeline inputs for CPU state and actual-GPU proofs.
export const identity=()=>Float32Array.from([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]);
export function geometry(id){const a=new Float32Array(84);for(let n=0;n<3;n++){a.set([n===0?-.8:n===1?.8:0,n===2?.8:-.6,.05*id],n*28);a.set([.5,.7,.8,1,n===1?1:0,n===2?1:0],n*28+3);a.set([.6,.5,.7],n*28+15);a[n*28+19]=1;}return a;}
export function simpleSky(){const geometry=Float32Array.from([-1,-1,.8,0,1,-1,.8,1,0,1,.8,2,-1,-1,.7,3,1,-1,.7,3,0,1,.7,3,-1,-1,.6,4,1,-1,.6,4,0,1,.6,4,-1,-1,.5,5,1,-1,.5,5,0,1,.5,5]),ranges=Int32Array.from([0,3,0,3,3,1,6,3,1,9,3,1]),values=new Float32Array(20),palette=new Float32Array(4096);values.set([1,1,1,6]);values.set(identity(),4);for(let i=0;i<6;i++)palette.set([.1+i*.08,.2,.35, i<3?1:.18],i*4);return [{geometry,ranges,values,palette}];}
export function cases(){const result=[{name:'opaque-login',mode:0,world:false,seq:1,g:0,w:192,h:128},{name:'world-initial',mode:1,world:true,seq:2,g:1,w:192,h:128}];let seq=3,g=1;for(let count=1;count<=16;count++){result.push({name:'world-'+count,mode:1,world:true,seq:seq++,g:++g,w:192,h:128},{name:'fade-'+count,mode:3,count,world:true,seq:seq++,g,w:192,h:128});if(count===1||count===16){result.push({...result[result.length-1],name:'duplicate-fade-'+count},{name:'retained-base-'+count,mode:2,world:true,seq:seq++,g,w:192,h:128});}}
 result.push({name:'resized-world',mode:1,world:true,seq:seq++,g:++g,w:256,h:160},{name:'resized-fade',mode:3,count:2,world:true,seq:seq++,g,w:256,h:160},{name:'underground',mode:1,world:true,seq:seq++,g:++g,w:256,h:160,level:-1},{name:'opaque-reset',mode:0,world:false,seq:seq++,g:++g,w:192,h:128});return result;}
export async function initializeSession(backend,shaders,skies=simpleSky()){
 const n=backend.natives;
 await n.Java_orsc_BrowserWebGLBridge_initialize(null,shaders.vertex,shaders.fragment);await n.Java_orsc_BrowserWebGLBridge_initializeLighting(null,shaders.depthVertex,shaders.depthFragment);await n.Java_orsc_BrowserWebGLBridge_initializeSky();
 const s=skies[0];await n.Java_orsc_BrowserWebGLBridge_uploadSkyGeometry(null,s.geometry,s.ranges,s.values[3]);
 await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,2,2,new Int32Array([-1,0xff8877ff,0x8877ffff,-1]),false);
 await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,8,2,2,new Int32Array([-1,0,0x4488ff88,0xff8822ff]),true);
 for(let id=1;id<=3;id++)await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,id,geometry(id),new Int32Array([0,1,2]),new Int32Array([id===2?8:7,0,3,id===3?5:1,id===2?1:0]));
 for(const unit of [2,5,7])await n.Java_orsc_BrowserWebGLBridge_uploadLightingMask(null,unit,2,2,new Uint8Array([255,255,255,255,128,128,128,255,255,255,255,255,200,200,200,255]),new Float32Array([-1,-1,.5,.5]));
 return {backend,n,skies,index:0,fixtureResources:{chunkIds:[1,2,3],textureIds:[7,8],spriteIds:[1]}};
}
export async function prepareFrame(session,test){
 const {n,skies}=session,index=session.index++,w=test.w,h=test.h,level=test.level||0;
 const sky=skies[index%skies.length],sv=new Float32Array(sky.values);if(level<0||!test.world)sv[0]=0;
 await n.Java_orsc_BrowserWebGLBridge_skyFrame(null,sv,sky.palette);
 const light=new Float32Array(81);light[0]=1;light[1]=test.world&&level>=0?1:0;light[3]=light[4]=64;light[5]=1;light[6]=index%2;light[11]=light[13]=light[14]=1;light[15]=level;light[16]=light[17]=1/64;light[18]=light[19]=1;light.set([-1,-1,.5,.5],20);light.set([-1,-1,.5,.5],24);light.set([-1,-1,.5,.5],28);const shadow=identity();shadow[0]=shadow[5]=.8;light.set(shadow,32);light.set(shadow,48);light.set(shadow,64);
 await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);
 const quad=Float32Array.from([w*.35,h*.35,-.2,0,0,w*.55,h*.35,-.2,1,0,w*.55,h*.6,-.2,1,1,w*.35,h*.6,-.2,0,1]);
 const needed=await n.Java_orsc_BrowserWebGLBridge_planWorldSprites(null,1,index+1,new Int32Array([1,2,2]));await n.Java_orsc_BrowserWebGLBridge_commitWorldSprites(null,1,index+1,needed.length?new Uint8Array([220,80,120,255,100,240,50,170,10,30,255,120,200,200,40,255]):new Uint8Array(0),quad);
 const matrices=new Float32Array(48);matrices.set(identity());matrices.set(identity(),16);const ortho=identity();ortho[0]=2/w;ortho[5]=-2/h;ortho[12]=-1;ortho[13]=1;matrices.set(ortho,32);
 const values=new Float32Array(30);values.set([1,1,1,1,1,1,1]);values[11]=1;values[15]=.08;values[16]=.12;values[17]=.18;values[25]=2;values[26]=values[27]=1;values[29]=level;
 const draws=new Int32Array([1,0,0,0,0,63,0,0,2,0,0,0,0,63,0,2,3,0,0,0,0,63,1,0]),pixels=new Int32Array(w*h),t=new Float32Array(w*h).fill(1);
 for(let y=4;y<Math.min(28,h);y++)for(let x=5;x<Math.min(80,w);x++){const at=y*w+x;pixels[at]=test.mode===0?0x00c06020:0x00202020;t[at]=test.mode===0?0:.5;}
 await n.Java_orsc_BrowserWebGLBridge_overlayFrame(null,w,h,test.mode,test.g,test.seq,test.count||0,test.mode===0,t);
 session.readinessDeclaration=[1,2,1,3,3,0,1,1,1,w,h,2];
 return async()=>{await n.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array(session.readinessDeclaration));await n.Java_orsc_BrowserWebGLBridge_present(null,w,h,test.world,matrices,values,draws,pixels);};
}
export async function finishFrame(session){await session.n.Java_orsc_BrowserWebGLBridge_frameWork(null,new Float64Array(14),new Int32Array(14));}
