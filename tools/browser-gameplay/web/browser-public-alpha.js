/** Frozen public deployment admission. URL parameters never choose networking or experiments. */
export const PUBLIC_ORIGIN='https://play.rscspoiledmilk.com';
export const PUBLIC_WEBSOCKET='wss://play.rscspoiledmilk.com:43505/';
export const PUBLIC_PRESET='alpha-v1';
// The existing backend's benchmark bit is an internal admission capability, not
// a public query switch or a measurement claim. Only this fixed reviewed set is used.
const PRESET=Object.freeze({mode:'webgl',benchmark:'1',cache:'lru',underlay:'initial',depth:'gpu',loop:'modern',cull:'early',ownership:'all',sprites:'cached',overlay:'coverage',ownedstate:'on',uploadvalidation:'on',physical:'on',modelpickbounds:'on',halomatch:'on',spritekeylimbs:'on',geometrytransfer:'off',regionprep:'off',descriptorwrite:'off',halolookup:'off',sceneryprojection:'off',nativecalls:'async',renderworker:'on',loadprofile:'off',regionbuild:'on',modelbatch:'on',terrainquery:'on',overlaydelta:'off',overlayrect:'off',wholezone:'on',gpuresidency:'bounded',gpubudget:'512',staticscene:'on',geometryformat:'compact',wholeram:'on',baselinereuse:'off',clonetemplates:'off',stagedlogin:'on'});
const KEYS=['schemaVersion','origin','websocket','preset','buildId','assetPrefix','clientJarSha256','clientJarBytes','bootstrapJarSha256','bootstrapJarBytes'];
const STREAMING_OPTIONS=Object.freeze({mode:'webgl',physical:'on',stagedlogin:'on'});
const STREAMING_PROPERTIES=Object.freeze([
  'browserWorldStreaming','persistentMovementPreview','persistentSurfaceCollision','surfaceSceneryCells',
  'rollingSurfaceWindow','fixedSurfaceDraw','expandedSurfaceDrawBorder','surfaceSceneryDraw','liveSurfaceHandoff',
  'browserPaceTerrainPrediction','browserSurfaceMeshWorker','browserWorkerSceneryPlacements',
  'browserSurfaceSceneryWords','browserSurfaceTerrainWords','browserSurfaceGameplayWords',
  'browserSurfaceCompatibilityWorker','browserCompactScenery','browserCompactVertexBlocks',
  'browserResidentCameraCull','browserKeepRebasedModels','browserSkipEmptyModelRemovals','browserIntegerCameraBounds'
].map(name=>'spoiledmilk.'+name+'=true').concat(
  'spoiledmilk.browserWholeZone=false','spoiledmilk.browserWholeRam=false'));
export function resolveBrowserDeployment(config,location,mode=null){
  if(mode!==null&&mode!=='chunks'&&mode!=='streaming')throw new Error('Unknown world loading mode.');
  if(config===null){
    if(location.origin===PUBLIC_ORIGIN)throw new Error('This browser build is not configured for this site. Please refresh or use the downloadable client.');
    const options=new URLSearchParams(location.search);
    const loadingChoices=/^http:\/\/127\.0\.0\.1:[0-9]+$/.test(location.origin)&&options.get('loadingchoices')==='on';
    if(mode==='streaming'&&!loadingChoices)throw new Error('World Streaming is unavailable in this build.');
    if(mode==='streaming')return {publicAlpha:false,loadingChoices,streaming:true,options:new URLSearchParams(STREAMING_OPTIONS),properties:STREAMING_PROPERTIES,assetRoot:'/app/'};
    return {publicAlpha:false,loadingChoices,streaming:false,options,properties:[],assetRoot:'/app/'};
  }
  const streamingOnly=config?.streamingOnly===true;
  const streamingAvailable=config?.worldStreaming===true;
  if(streamingOnly&&!streamingAvailable)throw new Error('Invalid World Streaming deployment.');
  const loadingChoices=streamingAvailable&&!streamingOnly;
  const expectedKeys=[...KEYS,...(streamingAvailable?['worldStreaming']:[]),...(streamingOnly?['streamingOnly']:[])];
  if(!config||Object.keys(config).sort().join('|')!==expectedKeys.sort().join('|')||config.schemaVersion!==1||config.origin!==PUBLIC_ORIGIN||config.websocket!==PUBLIC_WEBSOCKET||config.preset!==PUBLIC_PRESET||typeof config.buildId!=='string'||!/^[a-z0-9][a-z0-9-]{6,63}$/.test(config.buildId)||config.assetPrefix!=='/assets/'+config.buildId+'/'||!/^[0-9a-f]{64}$/.test(config.clientJarSha256)||!/^[0-9a-f]{64}$/.test(config.bootstrapJarSha256)||![config.clientJarBytes,config.bootstrapJarBytes].every(n=>Number.isSafeInteger(n)&&n>0&&n<=64*1024*1024))throw new Error('Invalid browser alpha deployment. Please refresh or use the downloadable client.');
  if(location.origin!==PUBLIC_ORIGIN)throw new Error('This browser alpha build must be opened on '+PUBLIC_ORIGIN+'.');
  const query=new URLSearchParams(location.search),view=query.getAll('view');
  if([...query.keys()].some(k=>k!=='view')||view.length>1||view.length===1&&!['fit','embedded'].includes(view[0]))throw new Error('Browser alpha does not accept custom startup options. Open the Play page without a query string.');
  const assetRoot='/app'+config.assetPrefix;
  if(streamingOnly&&mode==='chunks')throw new Error('This browser build uses World Streaming.');
  const streaming=streamingOnly||mode==='streaming';
  if(streaming&&!streamingAvailable)throw new Error('World Streaming is unavailable in this build.');
  return {publicAlpha:true,loadingChoices,streamingOnly,streaming,config:Object.freeze({...config}),options:new URLSearchParams(streaming?STREAMING_OPTIONS:PRESET),assetRoot,websocket:PUBLIC_WEBSOCKET,
    properties:['spoiledmilk.browserPublicAlpha=true','spoiledmilk.browserPublicOrigin='+PUBLIC_ORIGIN,'spoiledmilk.browserPublicPreset='+PUBLIC_PRESET,'spoiledmilk.browserAssetRoot='+assetRoot,...(streaming?STREAMING_PROPERTIES:[])]};
}
export function browserCapabilityProblem(win){
  if(!win.isSecureContext)return 'A secure connection is required. Open the HTTPS Play page.';
  if(typeof win.WebAssembly!=='object'||typeof win.Worker!=='function'||typeof win.OffscreenCanvas!=='function'||!win.OffscreenCanvas.prototype?.getContext)return 'This browser does not support the graphics features needed to play. Try an up-to-date Firefox or Chrome, or download the client.';
  return null;
}

/** Verify both executable archives before asking the Java runtime to open them.
 * Immutable HTTP caching lets its later Range reads reuse the same resources. */
export async function verifyPublicArchives(deployment,{fetcher=globalThis.fetch,crypto=globalThis.crypto,onProgress=()=>{},now=()=>performance.now()}={}){
  if(!deployment.publicAlpha)return;
  const c=deployment.config,totalBytes=c.clientJarBytes+c.bootstrapJarBytes;
  let received=0,files=0,lastBytes=0,lastTime=now();
  const progress=force=>{const at=now();if(force||received-lastBytes>=1048576||at-lastTime>=250){
    // Display observations must not change archive admission or startup success.
    try{onProgress({bytes:received,totalBytes,files,totalFiles:2});}catch{}
    lastBytes=received;lastTime=at;
  }};
  const invalid=()=>new Error('The game files could not be verified. Refresh the page to try again.');
  progress(true);
  for(const [name,bytes,hash]of [['client.jar',c.clientJarBytes,c.clientJarSha256],['browser-spike.jar',c.bootstrapJarBytes,c.bootstrapJarSha256]]){
    const response=await fetcher(c.assetPrefix+name,{credentials:'omit',cache:'default',redirect:'error'});
    if(!response.ok||response.headers.get('content-length')!==null&&Number(response.headers.get('content-length'))!==bytes)throw invalid();
    let raw;
    if(response.body?.getReader){
      const reader=response.body.getReader(),buffer=new Uint8Array(bytes);let offset=0;
      try{while(true){const item=await reader.read();if(item.done)break;
        if(!(item.value instanceof Uint8Array)||item.value.byteLength>bytes-offset)throw invalid();
        buffer.set(item.value,offset);offset+=item.value.byteLength;received+=item.value.byteLength;progress(false);
      }}catch(error){try{await reader.cancel();}catch{}throw error;}finally{reader.releaseLock();}
      if(offset!==bytes)throw invalid();raw=buffer.buffer;
    }else{raw=await response.arrayBuffer();if(raw.byteLength!==bytes)throw invalid();received+=raw.byteLength;progress(false);}
    const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',raw)),v=>v.toString(16).padStart(2,'0')).join('');
    if(digest!==hash)throw invalid();files++;progress(true);
  }
}
