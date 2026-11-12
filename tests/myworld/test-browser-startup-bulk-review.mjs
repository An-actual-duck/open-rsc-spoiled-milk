// Independent exact-commit JS lease review; no filesystem mounts/browser/network.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash,webcrypto} from 'node:crypto';
const commit=process.argv[2];if(!/^[0-9a-f]{40}$/.test(commit||''))throw Error('Exact source commit required');
const source=execFileSync('git',['show',commit+':tools/browser-gameplay/web/browser-bulk-staging.js'],{encoding:'utf8'});
const {createBulkStaging}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
const raw=new Uint8Array([1,2,3]),hash=createHash('sha256').update(raw).digest('hex');
const config={manifestBytes:3,manifestSha256:hash,sourceBundleBytes:3,sourceBundleSha256:hash,preparedBytes:3,preparedSha256:hash};
const first='Cache/whole-ram/manifest.json';let checks=0,failed=[];
const check=(name,fn)=>{try{fn();checks++;}catch(error){failed.push(name+': '+error.message);}};
const make=(overrides={})=>createBulkStaging({enabled:true,assetPrefix:'/assets/test-alpha/',config,crypto:webcrypto,
 fetcher:async()=>new Response(raw,{headers:{'content-length':'3'}}),addFile:()=>{},removeFile:()=>{},...overrides});
const cleanup=make({removeFile:()=>{throw Error('cleanup marker');}});await cleanup.prepare(first);
check('release cleanup error surfaced',()=>assert.throws(()=>cleanup.release(first,true),/cleanup marker/));
check('release marks terminal failure',()=>assert.equal(cleanup.stats.failed,true));
check('failed remove retains exact ownership',()=>assert.deepEqual([cleanup.stats.mountedBytes,cleanup.stats.ownedBytes,cleanup.stats.files],[3,3,0]));
try{await cleanup.prepare('Cache/whole-ram/source.bin');failed.push('failed release allowed next prepare');}catch{checks++;}
const primary=make({addFile:()=>{throw Error('primary marker');},removeFile:()=>{throw Error('cleanup marker');}});
try{await primary.prepare(first);failed.push('failed mount unexpectedly prepared');}catch(error){check('cleanup preserves original prepare failure',()=>assert.match(error.message,/primary marker/));}
check('failed prepare retains unresolved mount ledger',()=>assert.deepEqual([primary.stats.failed,primary.stats.mountedBytes,primary.stats.ownedBytes],[true,3,3]));
let digestResolve,mounted=0,removed=0;
const pending=make({crypto:{subtle:{digest:()=>new Promise(resolve=>digestResolve=resolve)}},addFile:()=>mounted++,removeFile:()=>removed++});
const preparing=pending.prepare(first);for(let i=0;i<20&&!digestResolve;i++)await new Promise(resolve=>setImmediate(resolve));
assert(digestResolve,'deferred actual digest reached');pending.dispose();digestResolve(new Uint8Array(Buffer.from(hash,'hex')).buffer);
try{await preparing;failed.push('stale digest mounted after dispose');}catch{checks++;}
check('late digest cannot mount or retain bytes',()=>assert.deepEqual([mounted,removed,pending.stats.mountedBytes,pending.stats.ownedBytes],[0,0,0,0]));
const leased=make({removeFile:()=>removed++});await leased.prepare(first);leased.dispose();
check('active lease persists honestly until close/release',()=>assert.deepEqual([leased.stats.mountedBytes,leased.stats.ownedBytes,leased.stats.deferredCloseCleanup],[3,3,true]));
leased.release(first,true);check('late close cleans without advancing completion',()=>assert.deepEqual([leased.stats.mountedBytes,leased.stats.ownedBytes,leased.stats.files,leased.stats.completedBytes],[0,0,0,0]));
if(failed.length){console.error('FAIL exact '+commit+' '+checks+' passed; '+failed.join('\n'));process.exitCode=1;}
else console.log('PASS exact '+commit+' '+checks+' independent failed-cleanup/primary-error/late-digest/disposed-lease checks; no browser/network');
