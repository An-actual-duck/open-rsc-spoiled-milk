/** Independent literal cardinal geometry and fresh-timestamp oracle. No GL/browser. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';import assert from 'node:assert/strict';
const args=process.argv.slice(2),option=n=>{const i=args.indexOf(n);if(i<0||!args[i+1])throw Error('Required '+n);return path.resolve(args[i+1]);};
const root=option('--source-root'),out=option('--out'),web=path.join(root,'tools/browser-gameplay/web'),names=['browser-bounded-prefetch.js','browser-bounded-frame-gap.js','webgl.js'];
const source=Object.fromEntries(names.map(n=>[n,fs.readFileSync(path.join(web,n),'utf8')])),data=s=>'data:text/javascript;base64,'+Buffer.from(s).toString('base64');
const {createBoundedPrefetch}=await import(data(source[names[0]])),{createBoundedFrameGap}=await import(data(source[names[1]]));let checks=0,cases=0;
const check=(v,label)=>{assert(v,label);checks++;},equal=(a,b,label)=>{assert.deepEqual(a,b,label);checks++;};
let next=1000000;const chunks=[];
for(let role=0;role<=1;role++)for(let plane=0;plane<=2;plane++)for(let x=-16;x<=16;x++)for(let y=-16;y<=16;y++)chunks.push({id:next++,role,plane,cellX:x,cellY:y});
const scope=(x,y)=>Int32Array.from([1,1,0,0,0,x,y,2,55,3,3,0,0,1,0,1,0,0]);
const sorted=ids=>Array.from(ids).sort((a,b)=>a-b),byId=new Map(chunks.map(c=>[c.id,c]));
function expected(catalog,x,y){
 return catalog.chunks.filter(c=>{if(c.role===1&&c.plane!==0)return false;const dx=c.cellX-x*(c.role===1?2:1),dy=c.cellY-y*(c.role===1?2:1);
  return c.role===0?(Math.abs(dx)===3&&Math.abs(dy)<=2||Math.abs(dy)===3&&Math.abs(dx)<=2):
   ((dx>=-6&&dx<=-5||dx>=6&&dx<=7)&&dy>=-4&&dy<=5||(dy>=-6&&dy<=-5||dy>=6&&dy<=7)&&dx>=-4&&dx<=5);}).map(c=>c.id);
}
for(const catalog of [{chunks},{chunks:chunks.filter(c=>c.id%3!==0)}]){
 const prefetch=createBoundedPrefetch(catalog);
 for(const [x,y]of [[0,0],[1,0],[1,1],[1,1],[-3,-2],[-3,-2],[8,8],[-9,-9],[0,0]]){
  const actual=prefetch.plan(scope(x,y)),wanted=expected(catalog,x,y);equal(sorted(actual),sorted(wanted),'literal cardinal geometry matches available source IDs');check(new Set(actual).size===actual.length&&actual.length<=140,'no duplicate or excess prefetch descriptors');cases++;
  check(prefetch.plan(scope(x,y))===actual,'stationary plan preserves heading and descriptor identity');
  for(const id of actual){const c=byId.get(id),scale=c.role===1?2:1,dx=c.cellX-x*scale,dy=c.cellY-y*scale;check(!(dx>=-2*scale&&dx<3*scale&&dy>=-2*scale&&dy<3*scale),'prefetch never invents active drawable membership');}
 }
 prefetch.plan(Int32Array.from([1,0,-1]));const reset=prefetch.plan(scope(0,0));equal(sorted(reset),sorted(expected(catalog,0,0)),'signed/inactive reset retains only next active source-space plan');
}
{
 const p=createBoundedPrefetch({chunks});check(p.plan(scope(0,0)).length===140,'full initial cardinal ring has actual140 IDs');p.plan(scope(1,0));const east=p.plan(scope(1,0));const first=byId.get(east[0]);check(first.role===0&&first.cellX===4,'east travel prioritizes entering side');p.plan(scope(1,1));const north=byId.get(p.plan(scope(1,1))[0]);check(north.role===0&&north.cellY===4,'camera-independent source north travel changes priority');
}
{
 const stats={},gap=createBoundedFrameGap(stats),previousFresh=1000,firstHeld=1027,resume=5265;
 for(let n=0;n<159;n++)gap.held(firstHeld+n*26.5,n===0?previousFresh:0);
 equal(stats.pendingHeldMilliseconds,158*26.5,'159 held images retain exact elapsed pending interval');check(stats.completedHeldIntervals===0,'held images are not completed fresh samples');gap.resumed(resume);
 equal(stats.completedHeldIntervals,1,'one completed cold held interval');equal(stats.lastHeldIntervalMilliseconds,resume-firstHeld,'cold interval duration uses first held timestamp');equal(stats.lastHeldFreshGapMilliseconds,resume-previousFresh,'cold fresh gap includes every held millisecond and prior partial frame');check(stats.maxHeldFreshGapMilliseconds>4200,'cold multi-second gap cannot become a28ms tail');check(stats.pendingHeldMilliseconds===0,'resume releases pending interval');
 gap.resumed(resume+16);equal(stats.completedHeldIntervals,1,'ordinary fresh frame cannot duplicate held interval');gap.held(6000,5980);gap.held(6020,0);gap.resumed(6040);equal(stats.completedHeldIntervals,2,'warm second held interval separately completed');equal(stats.lastHeldFreshGapMilliseconds,60,'warm fresh-gap exact');equal(stats.maxHeldFreshGapMilliseconds,4265,'later warm frames do not erase cold maximum');
}
{
 const stats={},gap=createBoundedFrameGap(stats);gap.held(10,0);gap.held(40,0);gap.resumed(50);equal(stats.lastHeldIntervalMilliseconds,40,'startup hold interval still measured');equal(stats.lastHeldFreshGapMilliseconds,0,'startup does not invent unavailable prior fresh frame');
}
// Direct helper tests cannot establish its call-site wiring. Review a closed
// current source body as an additional explicit source guard, not runtime proof.
{
 const code=source['webgl.js'],start=code.indexOf('if(capacity.bounded&&!boundedCoverage(draws))'),end=code.indexOf('// Resize/policy commits',start);check(start>=0&&end>start,'bounded presentation hold body exists');const hold=code.slice(start,end);
 check(hold.includes('boundedFrameGap.held(performance.now(),lastWorld)')&&!/lastWorld\s*=/.test(hold),'bounded held return preserves prior fresh clock');
 const nativeHold=code.slice(code.indexOf('async Java_orsc_BrowserWebGLBridge_holdWorld()'),code.indexOf('async Java_orsc_BrowserWebGLBridge_initialize('));check(nativeHold.includes('boundedFrameGap?.held(performance.now(),lastWorld)'),'source loading hold reports same helper');
 const resume=code.indexOf('boundedFrameGap?.resumed(now)'),sample=code.indexOf('stats.worldFrameSamples.push(now-lastWorld)',resume);check(resume>=0&&sample>resume,'fresh sample receives resumed held clock');
}
const result={passed:true,checks,cases,sourceRoot:root,sourceSha256:Object.fromEntries(Object.entries(source).map(([n,s])=>[n,crypto.createHash('sha256').update(s).digest('hex')])),coldHeldFrames:159,expectedColdFreshGapMilliseconds:4265,scope:'Actual cardinal-prefetch API versus independent literal source-cell inequalities, sparse rosters/heading/inactive reset; actual held-gap helper versus literal timestamps. Closed caller-source guards supplement helper tests. No native/browser rendering, GPU, real frame timings or FPS acceptance.'};fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
