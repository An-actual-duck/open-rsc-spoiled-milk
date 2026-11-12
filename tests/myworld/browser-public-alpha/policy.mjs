import assert from 'node:assert/strict';
import {webcrypto,createHash}from'node:crypto';
import {resolveBrowserDeployment,verifyPublicArchives,browserCapabilityProblem,PUBLIC_ORIGIN,PUBLIC_WEBSOCKET}from'../../../tools/browser-gameplay/web/browser-public-alpha.js';
import {repeatableFixturePolicy}from'../../../tools/browser-gameplay/web/browser-repeatable-policy.js';
import {wholeZonePolicy}from'../../../tools/browser-gameplay/web/browser-whole-zone-policy.js';
import {wholeRamPolicy}from'../../../tools/browser-gameplay/web/browser-whole-ram.js';
import {renderWorkerPolicy}from'../../../tools/browser-gameplay/web/browser-render-worker-protocol.js';
const jar=new Uint8Array([1,2,3]),boot=new Uint8Array([5,6]);const sha=b=>createHash('sha256').update(b).digest('hex');
const config={schemaVersion:1,origin:PUBLIC_ORIGIN,websocket:PUBLIC_WEBSOCKET,preset:'alpha-v1',buildId:'alpha-test1',assetPrefix:'/assets/alpha-test1/',clientJarSha256:sha(jar),clientJarBytes:jar.length,bootstrapJarSha256:sha(boot),bootstrapJarBytes:boot.length};
const at=(origin=PUBLIC_ORIGIN,search='')=>({origin,search});let checks=0;
const result=resolveBrowserDeployment(config,at());assert.equal(result.publicAlpha,true);assert.equal(result.assetRoot,'/app/assets/alpha-test1/');assert.equal(result.websocket,PUBLIC_WEBSOCKET);checks+=3;
for(const search of ['?endpoint=wss://evil','?username=owner&password=secret','?benchmark=1','?fixture=repeatable-v1','?mode=software','?wholezone=off','?runtime=4.3','?view=fit&view=embedded','?view=unknown','?view=fit&clonetemplates=on']){assert.throws(()=>resolveBrowserDeployment(config,at(PUBLIC_ORIGIN,search)));checks++;}
for(const origin of ['http://play.rscspoiledmilk.com','https://play.rscspoiledmilk.com.evil','https://rscspoiledmilk.com','https://play.rscspoiledmilk.com:444','null','http://127.0.0.1:8238']){assert.throws(()=>resolveBrowserDeployment(config,at(origin)));checks++;}
for(const change of [{websocket:'ws://play.rscspoiledmilk.com:43505/'},{websocket:'wss://play.rscspoiledmilk.com:43505/?token=x'},{websocket:'wss://user:pass@play.rscspoiledmilk.com:43505/'},{websocket:'wss://evil/game'},{assetPrefix:'/assets/../'},{clientJarBytes:0},{bootstrapJarSha256:'bad'},{buildId:'../secret'},{extra:'unknown'},{preset:'alpha-v2'}]){assert.throws(()=>resolveBrowserDeployment({...config,...change},at()));checks++;}
assert.equal(resolveBrowserDeployment(config,at(PUBLIC_ORIGIN,'?view=embedded')).options.get('geometryformat'),'compact');checks++;
const o=result.options;assert.equal(o.has('fixture'),false);assert.equal(o.get('gpuresidency'),'bounded');assert.equal(o.get('gpubudget'),'512');assert.equal(o.get('loadprofile'),'off');assert.equal(o.get('baselinereuse'),'off');assert.equal(o.get('clonetemplates'),'off');assert.equal(renderWorkerPolicy('webgl',o).enabled,true);assert.equal(wholeZonePolicy('webgl',o).enabled,true);assert.equal(wholeRamPolicy('webgl',o).enabled,true);checks+=9;
for(const origin of ['http://127.0.0.1:8238','http://private']){const privateMode=resolveBrowserDeployment(null,at(origin,'?benchmark=1&fixture=repeatable-v1'));assert.equal(privateMode.publicAlpha,false);assert.equal(privateMode.options.get('fixture'),'repeatable-v1');assert.equal(privateMode.assetRoot,'/app/');checks+=3;}
assert.throws(()=>resolveBrowserDeployment(null,at()));checks++;
assert.equal(repeatableFixturePolicy('webgl',new URLSearchParams('benchmark=1&fixture=repeatable-v1')).websocket,'ws://127.0.0.1:43549/');checks++;
let calls=[];const fetcher=async(url,options)=>{calls.push([url,options]);const raw=url.endsWith('/client.jar')?jar:boot;return{ok:true,headers:{get:()=>String(raw.length)},arrayBuffer:async()=>raw.buffer.slice(0)}};
await verifyPublicArchives(result,{fetcher,crypto:webcrypto});assert.deepEqual(calls.map(c=>c[0]),['/assets/alpha-test1/client.jar','/assets/alpha-test1/browser-spike.jar']);assert.ok(calls.every(c=>c[1].credentials==='omit'&&c[1].redirect==='error'));checks+=2;
await assert.rejects(()=>verifyPublicArchives(result,{fetcher:async()=>({ok:false}),crypto:webcrypto}));checks++;
await assert.rejects(()=>verifyPublicArchives(result,{fetcher:async()=>({ok:true,headers:{get:()=>null},arrayBuffer:async()=>new Uint8Array([0,0,0]).buffer}),crypto:webcrypto}));checks++;
await verifyPublicArchives({publicAlpha:false},{fetcher:()=>{throw Error('private archive path changed')}});checks++;
assert.match(browserCapabilityProblem({isSecureContext:false}),/secure/);assert.match(browserCapabilityProblem({isSecureContext:true}),/browser/);checks+=2;
console.log('PASS '+checks+' public origin/endpoint/query/preset/archive integrity/capability and unchanged private-policy checks; no network/browser');
