/** Public-only transfer adapter. Java still validates source/recipes/readiness. */
const ORIGIN='https://play.rscspoiledmilk.com';
const HASH=/^[0-9a-f]{64}$/;
const MAX_FILE=256*1024*1024;
let nextOwner=0;
export function createBulkStaging({enabled=false,assetPrefix,config,fetcher=globalThis.fetch,crypto=globalThis.crypto,
  addFile,removeFile,onProgress=()=>{},now=()=>performance.now()}={}) {
  const stats={version:1,enabled:enabled===true,stopped:false,failed:false,files:0,totalFiles:0,totalBytes:0,receivedBytes:0,verifiedBytes:0,completedBytes:0,
    ownedBytes:0,peakOwnedBytes:0,mountedBytes:0,mounts:0,removals:0,deferredCloseCleanup:false,cleanupFailed:false,
    scope:'One logical in-flight raw payload; excludes browser/runtime/crypto/filesystem copies. Transfer completion is not Java source or world readiness.'};
  if(!enabled)return {stats,cancelled:()=>false,prepare(){throw Error('Bulk staging is disabled');},release(){throw Error('Bulk staging is disabled');},dispose(){}};
  if(typeof assetPrefix!=='string'||!/^\/assets\/[a-z0-9][a-z0-9-]{6,63}\/$/.test(assetPrefix)||!config)throw Error('Invalid public staging scope');
  const files=[['manifest.json',config.manifestBytes,config.manifestSha256],['source.bin',config.sourceBundleBytes,config.sourceBundleSha256],['prepared.bin',config.preparedBytes,config.preparedSha256]].map(([name,bytes,hash])=>{
    if(!Number.isSafeInteger(bytes)||bytes<1||bytes>MAX_FILE||typeof hash!=='string'||!HASH.test(hash))throw Error('Public staging payload exceeds declaration/budget');
    return Object.freeze({relative:'Cache/whole-ram/'+name,bytes,hash});
  });
  stats.totalFiles=files.length;stats.totalBytes=files.reduce((n,f)=>n+f.bytes,0);
  const root='/str/browser-startup-'+(++nextOwner)+'/';let current=null,index=0,lastProgressBytes=0,lastProgressAt=now();
  function notify(force=false){const at=now();if(!force&&stats.receivedBytes-lastProgressBytes<1048576&&at-lastProgressAt<250)return;lastProgressBytes=stats.receivedBytes;lastProgressAt=at;try{onProgress({bytes:stats.receivedBytes,totalBytes:stats.totalBytes});}catch{/* Observations never authorize or fail a copy. */}}
  function alive(owner){if(stats.stopped||stats.failed||current!==owner)throw Error('Public staging cancelled');}
  function drop(owner){owner.data=null;stats.ownedBytes=0;if(current===owner)current=null;}
  function remove(owner){if(owner.mounted){try{removeFile(root+owner.file.relative);}catch(error){stats.failed=true;stats.cleanupFailed=true;stats.cleanupError=String(error).slice(0,512);throw error;}owner.mounted=false;stats.mountedBytes=0;stats.removals++;}stats.deferredCloseCleanup=false;drop(owner);}
  async function prepare(relative){
    if(stats.stopped||stats.failed)throw Error('Public staging cancelled');
    if(current)throw Error('Public staging already owns a payload');
    const file=files[index];if(!file||relative!==file.relative)throw Error('Public staging roster/order mismatch');
    if(typeof addFile!=='function'||typeof removeFile!=='function')throw Error('Public staging APIs unavailable');
    const owner={file,controller:new AbortController(),data:null,mounted:false,leased:false};current=owner;
    try{
      const url=new URL(file.relative,ORIGIN+assetPrefix);
      const response=await fetcher(url.href,{cache:'default',credentials:'omit',redirect:'error',signal:owner.controller.signal});alive(owner);
      if(!response.ok)throw Error('Public staging HTTP failure');
      const size=response.headers?.get?.('content-length');if(size!=null&&Number(size)!==file.bytes)throw Error('Public staging HTTP size mismatch');
      if(!response.body?.getReader)throw Error('Public staging bounded stream unavailable');
      const reader=response.body.getReader();owner.data=new Uint8Array(file.bytes);stats.ownedBytes=file.bytes;stats.peakOwnedBytes=Math.max(stats.peakOwnedBytes,stats.ownedBytes);let at=0;
      try{
        for(;;){const {done,value}=await reader.read();alive(owner);if(done)break;
          if(!(value instanceof Uint8Array)||at+value.byteLength>file.bytes)throw Error('Public staging payload exceeds declaration');
          owner.data.set(value,at);at+=value.byteLength;stats.receivedBytes+=value.byteLength;notify();
        }
        if(at!==file.bytes)throw Error('Public staging payload truncated');notify(true);
      }catch(error){try{await reader.cancel(error);}catch{}throw error;}finally{reader.releaseLock();}
      const digest=new Uint8Array(await crypto.subtle.digest('SHA-256',owner.data));alive(owner);
      if(Array.from(digest,x=>x.toString(16).padStart(2,'0')).join('')!==file.hash)throw Error('Public staging digest mismatch');
      stats.verifiedBytes+=file.bytes;
      // Mark the mount attempt so failed API implementations cannot leave a file.
      owner.mounted=true;stats.mountedBytes=file.bytes;addFile(root+relative,owner.data);stats.mounts++;alive(owner);
      owner.leased=true;return root;
    }catch(error){stats.failed=!stats.stopped;owner.controller.abort();if(!owner.leased)try{remove(owner);}catch{/* Keep original preparation failure and unconfirmed ownership. */}throw error;}
  }
  function release(relative,copied){
    const owner=current;if(!owner||relative!==owner.file.relative||!owner.leased)throw Error('Public staging release does not own lease');
    if(typeof copied!=='boolean')throw Error('Public staging copy completion shape');
    // Caller invokes only after Java closes its input/output, including failure.
    owner.closed=true;remove(owner);
    if(copied&&!stats.stopped&&!stats.failed){stats.completedBytes+=owner.file.bytes;stats.files++;index++;}
    else if(!copied)stats.failed=true;
  }
  return {stats,prepare,release,cancelled:()=>stats.stopped||stats.failed,
    dispose(){if(stats.stopped)return;stats.stopped=true;if(current){current.controller.abort();
      // An open Java input owns the mounted file until finally/release. Destroying
      // the disposable VM is a distinct lifetime; no immediate-free claim here.
      if(current.leased&&!current.closed)stats.deferredCloseCleanup=true;
      else if(current.mounted)try{remove(current);}catch{/* Unconfirmed removal remains in the failed ledger. */}
    }}};
}
