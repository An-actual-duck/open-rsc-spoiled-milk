import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {createBrowserShadows as current} from '../../tools/browser-gameplay/web/browser-shadows.js';
const BASE='f2cca708956167e15eadebc4b437fab896eaa40e';
const original=execFileSync('git',['show',`${BASE}:tools/browser-gameplay/web/browser-shadows.js`],{encoding:'utf8'});
assert.equal(createHash('sha256').update(original).digest('hex'),'86e84a2958b6de3dd859801d08c7793459718178b8bdc12e20e2dc2ddab0a068');
const {createBrowserShadows:before}=await import('data:text/javascript;base64,'+Buffer.from(original).toString('base64'));
// Reuse the established GL API double without executing its lighting scenarios.
// The oracle is the frozen ORIGINAL module, not a reimplementation of rendering.
const lightingTest=readFileSync(new URL('./test-browser-lighting.mjs',import.meta.url),'utf8');
const mockSource=lightingTest.slice(lightingTest.indexOf('function mockGL(){'),lightingTest.indexOf('\nconst m=mockGL()'));
assert(mockSource.startsWith('function mockGL(){')&&mockSource.includes('return {gl,state,uniforms,draws,objects,textures};'));
const mockGL=Function('assert',mockSource+'\nreturn mockGL;')(assert);
const normalize=value=>ArrayBuffer.isView(value)?Array.from(value):Array.isArray(value)?value.map(normalize):value&&typeof value==='object'?Object.fromEntries(Object.entries(value).map(([k,v])=>[k,normalize(v)])):value;
function fixture(create,{owned=false,nullLocation=false,failure=null}={}){
 const m=mockGL(),gl=m.gl,commands=[],queries=[];let queryThrows=failure==='lookup',shaderTests=0,errorOnce=failure==='storage';
 const shaderParameter=gl.getShaderParameter;gl.getShaderParameter=(...args)=>{shaderTests++;return failure==='vertex-compile'?false:failure==='fragment-compile'&&shaderTests===2?false:shaderParameter(...args);};
 if(failure==='link')gl.getProgramParameter=()=>false;
 const rawError=gl.getError;gl.getError=()=>{if(errorOnce){errorOnce=false;return 0x505;}return rawError();};
 gl.getUniformLocation=(p,name)=>{queries.push([p?.id,name]);if(queryThrows&&name==='uGlobalOffsetX'){queryThrows=false;throw Error('Injected uniform lookup failure');}return nullLocation&&name==='uAlphaTest'?null:name;};
 for(const [name,fn]of Object.entries(gl))if(typeof fn==='function'&&name!=='getUniformLocation')gl[name]=(...args)=>{let result;try{result=fn(...args);}catch(error){commands.push([name,normalize(args),{throws:String(error)}]);throw error;}commands.push([name,normalize(args),normalize(result)]);return result;};
 const chunks=new Map([[1,{vbo:{id:1},ibo:{id:2},batches:new Int32Array([10,0,3,3,1,20,3,3,4,1])}],[2,{vbo:{id:3},ibo:{id:4},batches:new Int32Array([10,0,3,4,1])}]]);
 const textures=new Map([[10,{texture:{id:5},revision:1}],[20,{texture:{id:6},revision:1}]]),stats={};
 const ownedFrame={active:owned,maxTextureSize:4096,target(){gl.bindFramebuffer(gl.FRAMEBUFFER,null);}};
 const module=create(gl,{program:{id:20},stats,chunks,textures,adaptShader:x=>x,ownedFrame});
 return {...m,commands,queries,chunks,textures,stats,ownedFrame,module};
}
function values(){const a=new Float32Array(82);a[0]=a[1]=1;a[3]=64;a[4]=128;a[5]=1;a[6]=1;a[9]=10000;a[10]=20000;a[16]=1/64;a[17]=1/128;a[18]=4;a[19]=6;a[80]=1;a[81]=20;for(const at of [32,48,64])for(let n=0;n<4;n++)a[at+n*5]=1;return a;}
const draws=()=>new Int32Array([1,0,0,10000,20000,0,0,0,2,256,-128,10000,20000,0,0,2]);
function execute(create,options={}){
 const f=fixture(create,options),{module,stats,queries,chunks,textures,ownedFrame}=f,a=values(),d=draws(),frames=[];
 let error=null;try{module.initialize('production depth vertex','production depth fragment');}catch(e){error=String(e);}
 if(error){module.dispose();module.dispose();return {commands:f.commands,queries,stats,objects:f.objects.size,error};}
 const present=(tag)=>{const q=queries.length;let failure=null;try{module.prepare(a,d);module.bindReceiver();}catch(e){failure=String(e);}frames.push({tag,queryCount:queries.length-q,near:stats.shadowNearDraws,far:stats.shadowFarDraws,error:failure});};
 present('initial');a[6]=0;present('far-cache-hit');
 d[5]=63;present('visibility-does-not-invalidate-far');
 d[9]+=128;present('animated-move');
 textures.get(10).revision++;present('static-alpha-revision');
 a[6]=a[7]=1;present('far-history');a[6]=a[7]=0;present('history-retire');
 a[15]=-1;present('underground');a[15]=-4;present('deeper-underground');a[15]=0;present('surface-return');
 a[1]=0;present('quality-off');a[1]=1;a[3]=128;a[4]=256;present('quality-resize');
 chunks.get(1).vbo={id:100};present('chunk-vbo-replacement');
 ownedFrame.active=!ownedFrame.active;present('ownership-transition');
 d[7]=2;present('animated-role-transition');d[7]=0;
 a[9]=-30000;a[10]=-70000;present('negative-rebase');
 module.dispose();module.dispose();assert.equal(f.objects.size,0,'module leaked resource after scenario');assert.equal(stats.shadowResourceBytes,0);
 return {commands:f.commands,queries,stats,objects:f.objects.size,frames,error};
}
let totalCommands=0,totalBaseline=0,totalCached=0,baselineDepth=0,cachedDepth=0;const depthNames=new Set(['uTexture','uLightMatrix','uGlobalOffsetX','uGlobalOffsetZ','uAlphaTest']);
for(const owned of [false,true])for(const nullLocation of [false,true])for(const failure of [null,'vertex-compile','fragment-compile','link','storage','lookup']){
 const options={owned,nullLocation,failure},old=execute(before,options),next=execute(current,options);
 assert.deepEqual(next.commands,old.commands,'GL command/value/order/resource drift '+JSON.stringify(options));assert.deepEqual(next.stats,old.stats);assert.equal(next.error,old.error);assert.equal(next.objects,0);
 if(!next.error){assert.deepEqual(next.frames.map(({queryCount,...rest})=>rest),old.frames.map(({queryCount,...rest})=>rest));const warm=next.frames.find(x=>x.tag===(failure?'visibility-does-not-invalidate-far':'far-cache-hit'));assert.equal(warm.queryCount,0,'depth/receiver locations were requeried after first warm frame');if(!failure)assert.equal(next.queries.filter(x=>x[1]==='uAlphaTest').length,1,'including null locations, query only once');if(!failure)assert.equal(next.frames.find(x=>x.tag==='far-cache-hit').far,0,'far cache was not actually exercised');const names=new Set(next.queries.filter(x=>['uLightMatrix','uGlobalOffsetX','uGlobalOffsetZ','uAlphaTest','uTexture'].includes(x[1])).map(x=>x[1]));assert.equal(names.size,5);}
 totalCommands+=next.commands.length;totalBaseline+=old.queries.length;totalCached+=next.queries.length;baselineDepth+=old.queries.filter(x=>depthNames.has(x[1])).length;cachedDepth+=next.queries.filter(x=>depthNames.has(x[1])).length;
}
// Disposal rejects further use; a newly linked module in the SAME GL context
// must query its new program, not reuse locations from the disposed program.
{const f=fixture(current),a=values(),d=draws();f.module.initialize('vertex','fragment');f.module.prepare(a,d);const first=f.queries.filter(x=>depthNames.has(x[1]));assert.equal(first.length,5);f.module.dispose();assert.throws(()=>f.module.prepare(a,d),/disposed/);const n=f.queries.length;
 const second=current(f.gl,{program:{id:21},stats:{},chunks:f.chunks,textures:f.textures,adaptShader:x=>x});second.initialize('vertex','fragment');second.prepare(a,d);const q=f.queries.slice(n).filter(x=>depthNames.has(x[1]));assert.equal(q.length,5);assert.notEqual(q[0][0],first[0][0]);second.dispose();second.dispose();assert.equal(f.objects.size,0);}
// Source lifetime guard: both receiver and depth caches stay separate; clearing
// the depth map is part of disposal even after failed initialization.
const source=readFileSync(new URL('../../tools/browser-gameplay/web/browser-shadows.js',import.meta.url),'utf8');assert(source.includes('depthLocations.clear();'));assert.equal((source.match(/getUniformLocation\(depthProgram/g)||[]).length,1);
console.log(JSON.stringify({pass:true,baseline:BASE,baselineSha256:createHash('sha256').update(original).digest('hex'),cases:24,identicalNonLookupCommands:totalCommands,baselineLocationQueries:totalBaseline,cachedLocationQueries:totalCached,baselineDepthQueries:baselineDepth,cachedDepthQueries:cachedDepth,newProgramLifetime:true,scope:'Frozen old/current CPU GL command/value/resource oracle only; no pixels or FPS acceptance'}));
