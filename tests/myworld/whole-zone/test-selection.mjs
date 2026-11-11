/** Independent exact-source selection proof with a real bounded bake. No GL. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const argv=process.argv.slice(2),option=n=>{const at=argv.indexOf(n);if(at<0||!argv[at+1])throw Error('Required '+n);return argv[at+1];};
const root=option('--source-root'),cataloguePath=option('--catalogue'),out=option('--out');
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const policyPath=path.join(root,'tools/browser-gameplay/web/browser-whole-zone-policy.js'),modulePath=path.join(root,'tools/browser-gameplay/web/browser-whole-zone.js');
const policy=fs.readFileSync(policyPath,'utf8'),source=fs.readFileSync(modulePath,'utf8');
const url=s=>'data:text/javascript;base64,'+Buffer.from(s).toString('base64');
const importLine="from './browser-whole-zone-policy.js'";assert.equal(source.split(importLine).length,2);
const module=await import(url(source.replace(importLine,`from '${url(policy)}'`)));
const raw=fs.readFileSync(cataloguePath),catalogue=JSON.parse(raw);let checks=0;const ok=(...a)=>{assert(...a);checks++;};
const metadata=(epoch=1)=>Int32Array.from([1,1,0,0,0,2,13,2,127,127,127,...catalogue.settings,epoch,0,0]);
const split=k=>[parseInt(k.slice(8),16)|0,parseInt(k.slice(0,8),16)|0];
const rows=catalogue.objectCells.flatMap(c=>[c.plane,c.cellX,c.cellY,...split(c.canonicalContentKey),0]);
const expected=catalogue.chunks.filter(c=>c.sourceKind==='world'||c.plane===0).map(c=>c.id).sort((a,b)=>a-b);
const ids=v=>Array.from(v).filter((_,n)=>n%8===0).sort((a,b)=>a-b);
function select(mutator=()=>{},input=new Int32Array(),epoch=1){const stats={},selection=module.createWholeZoneSelection(catalogue,stats),r=Int32Array.from(rows);mutator(r);selection.frame(metadata(epoch),r);return {draws:selection.merge(input,0),stats,selection};}
const first=select();assert.deepEqual(ids(first.draws),expected);checks++;ok(first.stats.currentPinnedDraws===expected.length);ok(first.stats.currentLocalDraws===0);ok(first.stats.verifiedObjectCells===catalogue.objectCells.length);
const object=catalogue.chunks.find(c=>c.sourceKind==='objects'&&c.plane===0);ok(!!object);const rowIndex=catalogue.objectCells.findIndex(c=>c.plane===object.plane&&c.cellX===object.cellX&&c.cellY===object.cellY)*6;
const local=Int32Array.from([31,0,0,0,0,127,0,1]);
for(const reason of [1,2]){const r=select(rows=>{rows[rowIndex+5]=reason;},local);assert.deepEqual(ids(r.draws),expected.filter(id=>id!==object.id).concat(31).sort((a,b)=>a-b));checks++;ok(r.stats[reason===1?'mutatedObjectCells':'animatedObjectCells']===1);}
// Destroying the final object must suppress its baseline; empty has no live draw.
const empty=select(r=>{r.set(split(catalogue.objectCells[rowIndex/6].emptyCanonicalContentKey),rowIndex+3);r[rowIndex+5]=1;});ok(!ids(empty.draws).includes(object.id));
const baseline=select(r=>{for(let at=5;at<r.length;at+=6)r[at]=3;});assert.deepEqual(ids(baseline.draws),expected);checks++;
assert.throws(()=>select(r=>{r[rowIndex+3]^=1;}),/key mismatch/);checks++;
assert.throws(()=>{const s=module.createWholeZoneSelection(catalogue,{});s.frame(metadata(),Int32Array.from(rows.slice(6)));s.merge(new Int32Array(),0);},/Missing/);checks++;
assert.throws(()=>{const s=module.createWholeZoneSelection(catalogue,{});s.frame(metadata(),Int32Array.from(rows.concat(rows.slice(0,6))));},/authority/);checks++;
assert.throws(()=>{const s=module.createWholeZoneSelection(catalogue,{});s.frame(metadata(2),Int32Array.from(rows));s.merge(new Int32Array(),0);s.frame(metadata(1),Int32Array.from(rows));},/backwards/);checks++;
assert.throws(()=>first.selection.merge(new Int32Array(),0),/fresh/);checks++;
assert.throws(()=>select(()=>{},Int32Array.from([1000000,0,0,0,0,0,0,0])),/Pinned/);checks++;
// Zero color masks still retain world chunks as possible geometry shadow casters.
{const s=module.createWholeZoneSelection(catalogue,{}),v=metadata();v[8]=v[9]=v[10]=0;s.frame(v,Int32Array.from(rows));assert.deepEqual(ids(s.merge(new Int32Array(),0)),expected);checks++;}
// Signed level is independent of classic compatibility plane; field17 declares non-global scope.
for(const level of [-1,-2,10,0]){const stats={},s=module.createWholeZoneSelection(catalogue,stats),v=metadata();v[1]=0;v[2]=level;v[17]=level===0?1:0;s.frame(v,new Int32Array());ok(s.merge(local,level)===local);ok(stats.currentPinnedDraws===0);}
assert.throws(()=>{const s=module.createWholeZoneSelection(catalogue,{}),v=metadata();v[1]=0;s.frame(v,new Int32Array());s.merge(local,0);},/inactive|scope/);checks++;
assert.throws(()=>{const s=module.createWholeZoneSelection(catalogue,{}),v=metadata();v[17]=1;s.frame(v,Int32Array.from(rows));},/scope|contract|overworld/);checks++;
// Loader must reject an incomplete pilot, even though selection can be isolated.
assert.throws(()=>module.validateWholeZoneManifest(catalogue),/complete|Incomplete|scope|roster|identity mismatch/);checks++;
const result={passed:true,checks,moduleSha256:sha(source),policySha256:sha(policy),catalogueSha256:sha(raw),catalogue:cataloguePath,sourceRoot:root,scope:'CPU actual selection API on partial source-joined pilot; tests real pinned ID use/mutation/animated/empty/authority/epochs/shadow-zero masks. No Java upload skipping, full catalog readiness, geometry or GPU/performance proof.'};
fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result,null,2));
