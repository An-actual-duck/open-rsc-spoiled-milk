/** Private prepared system-RAM experiment. No cache-count or raw-only readiness shortcut. */
const HASH=/^[0-9a-f]{64}$/;
export const WHOLE_RAM_SOURCE_BYTES=46101718;
export const WHOLE_RAM_BUNDLE_BYTES=46101734;
export const WHOLE_RAM_PREPARED_MAX=1536*1024*1024;
function integer(n,min,max,label){if(!Number.isSafeInteger(n)||n<min||n>max)throw new Error('Whole RAM '+label+' bounds');return n;}
function hash(value,label){if(typeof value!=='string'||!HASH.test(value))throw new Error('Whole RAM '+label+' SHA256');return value;}
export function wholeRamPolicy(mode,options){
  const value=options.get('wholeram');if(value!==null&&!['on','off'].includes(value))throw new Error('Invalid private wholeram policy');const enabled=value==='on';
  if(enabled&&(mode!=='webgl'||!options.has('benchmark')||options.get('wholezone')!=='on'||options.get('renderworker')!=='on'||options.get('overlay')!=='coverage'||!['off',null].includes(options.get('overlaydelta'))))throw new Error('Whole RAM requires private whole-zone Worker WebGL full coverage');
  return {requested:enabled,enabled,properties:enabled?['spoiledmilk.browserWholeRam=true']:[]};
}
export function validateWholeRamConfig(c,gpu){
  if(!c||c.schemaVersion!==1||!gpu)throw new Error('Private prepared RAM catalogue is not staged');
  hash(c.manifestSha256,'manifest');hash(c.clientJarSha256,'client');hash(c.gpuCatalogSha256,'GPU catalogue');hash(c.sourceBundleSha256,'source bundle');hash(c.preparedSha256,'prepared payload');
  integer(c.manifestBytes,1,4*1024*1024,'manifest bytes');integer(c.preparedBytes,1,WHOLE_RAM_PREPARED_MAX,'prepared bytes');integer(c.retainedPrimitiveBytes,1,Number.MAX_SAFE_INTEGER,'prepared primitive bytes');integer(c.retainedPrimitiveBytes+c.sourceBundleBytes,1,Number.MAX_SAFE_INTEGER,'expected runtime primitive bytes');
  if(typeof c.hiddenRoofs!=='boolean')throw new Error('Whole RAM roof option binding');
  if(c.clientJarSha256!==gpu.clientJarSha256||c.gpuCatalogSha256!==gpu.manifestSha256||c.sourceBundleBytes!==WHOLE_RAM_BUNDLE_BYTES||c.rawFiles!==1797||c.recipes!==445||c.queryCells!==1330)throw new Error('Whole RAM source/client/GPU/prepared scope mismatch');
  return Object.freeze({schemaVersion:1,manifestSha256:c.manifestSha256,manifestBytes:c.manifestBytes,clientJarSha256:c.clientJarSha256,gpuCatalogSha256:c.gpuCatalogSha256,sourceBundleSha256:c.sourceBundleSha256,sourceBundleBytes:c.sourceBundleBytes,preparedSha256:c.preparedSha256,preparedBytes:c.preparedBytes,retainedPrimitiveBytes:c.retainedPrimitiveBytes,expectedRuntimePrimitiveBytes:c.retainedPrimitiveBytes+c.sourceBundleBytes,rawFiles:1797,recipes:445,queryCells:1330,hiddenRoofs:c.hiddenRoofs});
}
function bytes(lo,hi){const value=(lo>>>0)+hi*4294967296;return Number.isSafeInteger(value)?value:null;}
export function decodeWholeRamWork(input,requested){
  if(input?.length===0)return {requested:requested===true,pending:true,sourceReady:false,preparedReady:false};
  if(!input||input.length!==18)throw new Error('Whole RAM diagnostics shape');const v=[];
  for(let n=0;n<18;n++)v.push(integer(input[n],n===7||n===12?-2147483648:0,2147483647,'diagnostic word'));
  if(v[0]!==1||v[1]>1||v[2]>1||v[3]>1797||v[4]>WHOLE_RAM_SOURCE_BYTES||v[5]>445||v[6]>1330)throw new Error('Whole RAM diagnostics scope');
  const retained=bytes(v[7],v[8]),copied=bytes(v[12],v[13]);
  if(v[1]&&(v[3]!==1797||v[4]!==WHOLE_RAM_SOURCE_BYTES))throw new Error('Whole RAM raw source readiness without full source');
  if(v[2]&&(!v[1]||v[5]!==445||v[6]!==1330||retained===null||retained<=WHOLE_RAM_BUNDLE_BYTES))throw new Error('Whole RAM prepared readiness without complete useful CPU data');
  return {requested:requested===true,pending:false,sourceReady:v[1]===1,preparedReady:v[2]===1,rawFiles:v[3],rawBytes:v[4],rawPayloadBytes:v[4],sourceBundleBytes:v[1]?WHOLE_RAM_BUNDLE_BYTES:0,recipes:v[5],queryCells:v[6],retainedPrimitiveBytes:retained,retainedPrimitiveBytesLow:v[7]>>>0,retainedPrimitiveBytesHigh:v[8],activations:v[9],recipeHits:v[10],modelsInstantiated:v[11],mutableBytesCopied:copied,mutableBytesCopiedLow:v[12]>>>0,mutableBytesCopiedHigh:v[13],gpuBuildsAvoided:v[14],sourceValidateCount:v[15],prepareMilliseconds:v[16],activationMilliseconds:v[17],counterSaturated:[9,10,11,14,15,16,17].some(n=>v[n]===2147483647),scope:'Actual Java source retention/prepared recipes/query cells and use; primitive-byte ledger excludes boxed objects, VM/filesystem/driver heap. Staged files alone do not establish RAM readiness.'};
}
