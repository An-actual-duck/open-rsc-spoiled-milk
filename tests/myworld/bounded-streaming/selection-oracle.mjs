import {frozenBrowserModules} from '../browser-source-import.mjs';
/** Independent catalogue filter oracle for actual selection API. No GPU/performance claim. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
const args=process.argv.slice(2),option=n=>{const at=args.indexOf(n);if(at<0||!args[at+1])throw Error('Required '+n);return path.resolve(args[at+1]);};
const root=option('--source-root'),cataloguePath=option('--catalogue'),out=option('--out');
const sha=v=>crypto.createHash('sha256').update(v).digest('hex');
const web=path.join(root,'tools/browser-gameplay/web'),source=fs.readFileSync(path.join(web,'browser-whole-zone.js'),'utf8'),policy=fs.readFileSync(path.join(web,'browser-whole-zone-policy.js'),'utf8');
const data=v=>'data:text/javascript;base64,'+Buffer.from(v).toString('base64');
const importLine="from './browser-whole-zone-policy.js'";
assert.equal(source.split(importLine).length,2);
const frozen=frozenBrowserModules(web),api=await import(frozen.url('browser-whole-zone.js'));
const raw=fs.readFileSync(cataloguePath),catalogue=api.validateWholeZoneManifest(JSON.parse(raw));
let checks=0,cases=0;const tested=new Set(),capacityRows=[];
const resourceById=new Map(catalogue.chunks.map(c=>[c.id,c])),textureBytes=catalogue.textures.reduce((n,t)=>n+t.bytes,0),localReserveAssumptionBytes=53*1024*1024;
const equal=(a,b,label)=>{assert.deepEqual(Array.from(a),Array.from(b),label);checks++;};
const keyWords=s=>[parseInt(s.slice(8),16)|0,parseInt(s.slice(0,8),16)|0];
const cellKey=c=>`${c.plane}:${c.cellX}:${c.cellY}`;
const ordered=(a,b)=>a.plane-b.plane||a.cellX-b.cellX||a.cellY-b.cellY;
function oracle(v,rows,local){
 const authority=new Map(rows.map(r=>[`${r[0]}:${r[1]}:${r[2]}`,r]));
 const selected=catalogue.chunks.filter(c=>c.sourceKind==='world'?
  Math.abs(c.cellX-v[5])<=2&&Math.abs(c.cellY-v[6])<=2:
  c.plane===0&&c.cellX>=2*(v[5]-2)&&c.cellX<2*(v[5]+3)&&c.cellY>=2*(v[6]-2)&&c.cellY<2*(v[6]+3)&&[0,3].includes(authority.get(cellKey(c))?.[5]));
 selected.sort((a,b)=>(a.sourceKind==='objects')-(b.sourceKind==='objects')||ordered(a,b));
 return Int32Array.from(selected.flatMap(c=>{tested.add(c.id);return[c.id,c.originX-v[3],c.originZ-v[4],v[3],v[4],v[8+c.plane]&c.visibilityMask,c.plane,c.role];}).concat(Array.from(local)));
}
function run(cx,cy,{reason=0,masks=[127,127,127],local=new Int32Array(),base=[0,0],mutateKey=false,auditCapacity=false}={}){
 const v=Int32Array.from([1,1,0,...base,cx,cy,2,...masks,...catalogue.settings,1,0,0]);
 const rows=catalogue.objectCells.filter(c=>c.cellX>=2*(cx-2)&&c.cellX<2*(cx+3)&&c.cellY>=2*(cy-2)&&c.cellY<2*(cy+3)).map(c=>[c.plane,c.cellX,c.cellY,...keyWords(c.canonicalContentKey),reason]);
 if(mutateKey&&rows.length)rows[0][3]^=1;
 const stats={},selection=api.createWholeZoneSelection(catalogue,stats);selection.frame(v,Int32Array.from(rows.flat()));
 const actual=selection.merge(local,0),expected=oracle(v,rows,local);equal(actual,expected,`literal draws ${cx}:${cy} reason ${reason}`);
 assert.equal(stats.currentPinnedDraws,expected.length/8-local.length/8);checks++;
 assert.equal(stats.currentLocalDraws,local.length/8);checks++;cases++;
 if(auditCapacity){let geometryBytes=0;for(let at=0;at<actual.length;at+=8){const c=resourceById.get(actual[at]);geometryBytes+=c.vertexCount*112+c.indexCount*4;}capacityRows.push({centerX:cx,centerY:cy,selectedChunks:actual.length/8,selectedGeometryBytes:geometryBytes,texturesBytes:textureBytes,immutableBytes:geometryBytes+textureBytes,withAssumedLocalReserveBytes:geometryBytes+textureBytes+localReserveAssumptionBytes});}
 return {selection,v,rows};
}
// Every source world-cell center exercises catalogue joins, sparse edges and all cells.
const centers=new Map(catalogue.worldCells.map(c=>[`${c.cellX}:${c.cellY}`,[c.cellX,c.cellY]]));
for(const [cx,cy]of centers.values())run(cx,cy,{masks:[55,3,3],auditCapacity:true});
const local=Int32Array.from([17,123,-456,789,-100,127,0,1]);
for(const [cx,cy]of [...centers.values()].filter((_,n)=>n%37===0))for(const reason of [1,2,3])run(cx,cy,{reason,local,base:[24576,-12288],masks:[0,3,64]});
// Complete mutation/restoration, including mixed animated cells, must never revive baseline.
const anchor=catalogue.objectCells.find(c=>c.plane===0&&c.placementCount>0&&c.resourceId!==null);assert(anchor);
const cx=Math.floor(anchor.cellX/2),cy=Math.floor(anchor.cellY/2);
for(const reason of [0,1,2,1,3,0])run(cx,cy,{reason,local,mutateKey:reason===1});
for(const signed of [-1,-2,10]){const stats={},s=api.createWholeZoneSelection(catalogue,stats),v=Int32Array.from([1,0,signed,0,0,cx,cy,2,127,127,127,...catalogue.settings,1,0,0]);s.frame(v,new Int32Array());assert.equal(s.merge(local,signed),local);checks++;assert.equal(stats.currentPinnedDraws,0);checks++;}
{const {selection,v,rows}=run(cx,cy);assert.throws(()=>selection.merge(local,0),/fresh/);checks++;v[15]=0;assert.throws(()=>selection.frame(v,Int32Array.from(rows.flat())),/backwards/);checks++;}
const capacityAudit={centers:capacityRows.length,textureBytes,localReserveAssumptionBytes,hiddenRoofMasks:[55,3,3],maximum:capacityRows.reduce((a,b)=>a.immutableBytes>b.immutableBytes?a:b),exceeds256MiB:capacityRows.filter(r=>r.immutableBytes>256*1024*1024).length,exceeds512MiB:capacityRows.filter(r=>r.immutableBytes>512*1024*1024).length,withAssumedLocalReserveExceeds256MiB:capacityRows.filter(r=>r.withAssumedLocalReserveBytes>256*1024*1024).length,withAssumedLocalReserveExceeds512MiB:capacityRows.filter(r=>r.withAssumedLocalReserveBytes>512*1024*1024).length,maximumSelectedChunks:Math.max(...capacityRows.map(r=>r.selectedChunks)),rows:capacityRows,scope:'Exact immutable geometry derivation from actually selected IDs at every catalogue center plus complete textures; hidden-roof masks55/3/3 preserve world shadow geometry. Local53MiB is a separately declared assumption, not an allocation measurement. Excludes queues/transients/framebuffers/shadows/driver/Worker/VM.'};
const result={passed:true,checks,dependencySha256:frozen.hashes,cases,sourceRoot:root,sourceCommit:execFileSync('git',['-C',root,'rev-parse','HEAD'],{encoding:'utf8'}).trim(),sourceStatus:execFileSync('git',['-C',root,'status','--short','--','tools/browser-gameplay/web/browser-whole-zone.js'],{encoding:'utf8'}).trim(),sourceSha256:sha(source),policySha256:sha(policy),catalogueSha256:sha(raw),catalogueChunks:catalogue.chunks.length,catalogueWorldCells:catalogue.worldCells.length,catalogueObjectCells:catalogue.objectCells.length,uniqueSelectedChunks:tested.size,gpuAllocatedChunks:0,capacityAudit,scope:'Actual selection API versus independent literal catalogue filter, complete admitted catalogue, every source-cell center and mutation/animation/restoration/roof/rebase/signed fallback. No GPU allocation, pixels, gameplay packets, or performance evidence.'};
fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({...result,capacityAudit:{...capacityAudit,rows:undefined}}));
