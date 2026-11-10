/** Exact-ID sprite residency. One existing RGBA8 atlas; no extra texture on compaction.
 * Java IDs identify owned exact-content cache entries, never a probabilistic hash.
 * All current IDs are pinned before eviction. Borrowed arrays are consumed inline. */
export function createBrowserSpriteAtlas(gl,{texture,vbo,stats={},extent=2048,maxSprites=1024,deferOrCheck=null}={}) {
  if(!Number.isInteger(extent)||extent<1||extent>2048||!Number.isInteger(maxSprites)||maxSprites<1||maxSprites>1024)throw new Error('Invalid sprite atlas bounds');
  const MAX_RESIDENT=4096;
  let entries=new Map(),free=[{x:0,y:0,w:extent,h:extent}],epoch=0,sequence=0,pending=null,failed=false,disposed=false;
  Object.assign(stats,{spriteResidentImages:0,spriteDrawReuses:0,spriteUploadedImages:0,spriteUploadedBytes:0,spriteCompactions:0,spriteEvictions:0,spriteDescriptorUploads:0,spritePlanCalls:0,spriteCommitCalls:0,spriteSourceImageBytes:0,spriteDescriptorBytes:0});
  function alive(){if(disposed||failed)throw new Error('Sprite atlas unavailable; restart session');}
  function check(){const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('Sprite atlas GL error '+error);}
  function allocate(rectangles,w,h){
    let best=-1;for(let n=0;n<rectangles.length;n++){const r=rectangles[n];if(r.w>=w&&r.h>=h&&(best<0||r.w*r.h<rectangles[best].w*rectangles[best].h))best=n;}
    if(best<0)return null;const r=rectangles.splice(best,1)[0];
    if(r.w>w)rectangles.push({x:r.x+w,y:r.y,w:r.w-w,h});
    if(r.h>h)rectangles.push({x:r.x,y:r.y+h,w:r.w,h:r.h-h});
    return{x:r.x,y:r.y,w,h};
  }
  function token(e,s){if(!Number.isSafeInteger(e)||e<1||!Number.isSafeInteger(s)||s<1)throw new Error('Invalid sprite frame token');}
  function plan(e,s,input){alive();try{
    token(e,s);if(pending)throw new Error('Uncommitted sprite plan');if(e<epoch||(e===epoch&&s<=sequence))throw new Error('Stale sprite plan');
    const data=input instanceof Int32Array?input:new Int32Array(input);
    if(data.length%3||data.length>maxSprites*3)throw new Error('Sprite descriptor bounds exceeded');
    const current=new Map(),order=[];
    for(let n=0;n<data.length;n+=3){const id=data[n],w=data[n+1],h=data[n+2];if(id<1||w<1||h<1||w>extent||h>extent)throw new Error('Sprite image bounds exceeded');const prior=current.get(id);if(prior&&(prior.w!==w||prior.h!==h))throw new Error('Sprite ID dimensions changed');current.set(id,{w,h});order.push(id);}
    if(e===epoch)for(const[id,d]of current){const old=entries.get(id);if(old&&(old.w!==d.w||old.h!==d.h))throw new Error('Resident sprite ID dimensions changed');}
    let next=e===epoch?new Map(entries):new Map(),rectangles=e===epoch?free.map(r=>({...r})):[{x:0,y:0,w:extent,h:extent}],evictions=0,compacted=false;
    const missing=[];
    for(const[id,d]of current){if(next.has(id))continue;
      let rect=allocate(rectangles,d.w,d.h);
      while((!rect||next.size>=MAX_RESIDENT)&&next.size){let victim;for(const key of next.keys())if(!current.has(key)){victim=key;break;}if(victim===undefined)break;rectangles.push(next.get(victim));next.delete(victim);evictions++;if(!rect)rect=allocate(rectangles,d.w,d.h);}
      if(!rect||next.size>=MAX_RESIDENT||rectangles.length>MAX_RESIDENT*2){compacted=true;break;}next.set(id,rect);missing.push(id);
    }
    if(compacted){
      next=new Map();rectangles=[{x:0,y:0,w:extent,h:extent}];missing.length=0;
      // Allocation order is independent of draw order. Current quads retain exact source order.
      const sorted=[...current].sort((a,b)=>b[1].h-a[1].h||b[1].w-a[1].w||a[0]-b[0]);
      for(const[id,d]of sorted){const rect=allocate(rectangles,d.w,d.h);if(!rect)throw new Error('Current sprites exceed single atlas capacity');next.set(id,rect);missing.push(id);}
    }
    // Touch active images after inactive entries so the Map remains an LRU inventory.
    for(const id of current.keys()){const value=next.get(id);next.delete(id);next.set(id,value);}
    let bytes=0;for(const id of missing){const d=current.get(id);bytes+=d.w*d.h*4;}if(bytes>extent*extent*4)throw new Error('Sprite upload exceeds atlas byte bound');
    let sourceBytes=0;for(const id of order){const d=current.get(id);sourceBytes+=d.w*d.h*4;}
    pending={e,s,next,rectangles,missing,order,bytes,evictions,compacted,hits:order.length-missing.length,sourceBytes};
    stats.spritePlanCalls++;
    return Int32Array.from(missing); // owns its result; never retain input or Java frame storage
  }catch(error){failed=true;throw error;}}
  function center(position,size,unit){const first=Math.fround((position+.5)/extent),last=Math.fround((position+size-.5)/extent);return Math.fround(first+Math.fround(Math.fround(last-first)*Math.fround(unit)));}
  function commit(e,s,pixelInput,quadInput){alive();try{
    token(e,s);if(!pending||e!==pending.e||s!==pending.s)throw new Error('Sprite commit does not match current plan');
    const pixels=pixelInput instanceof Uint8Array?pixelInput:new Uint8Array(pixelInput),quads=new Float32Array(quadInput);
    if(pixels.length!==pending.bytes||quads.length!==pending.order.length*20||quads.some(v=>!Number.isFinite(v)))throw new Error('Sprite commit dimensions invalid');
    for(let at=0;at<pending.order.length;at++){const r=pending.next.get(pending.order[at]);for(let n=0;n<4;n++){const base=at*20+n*5;quads[base+3]=center(r.x,r.w,quads[base+3]);quads[base+4]=center(r.y,r.h,quads[base+4]);}}
    const active=gl.getParameter(gl.ACTIVE_TEXTURE),array=gl.getParameter(gl.ARRAY_BUFFER_BINDING);gl.activeTexture(gl.TEXTURE0);const binding=gl.getParameter(gl.TEXTURE_BINDING_2D),alignment=gl.getParameter(gl.UNPACK_ALIGNMENT);
    try{gl.bindTexture(gl.TEXTURE_2D,texture);gl.pixelStorei(gl.UNPACK_ALIGNMENT,4);let offset=0;for(const id of pending.missing){const r=pending.next.get(id),length=r.w*r.h*4;gl.texSubImage2D(gl.TEXTURE_2D,0,r.x,r.y,r.w,r.h,gl.RGBA,gl.UNSIGNED_BYTE,pixels.subarray(offset,offset+length));offset+=length;}gl.bindBuffer(gl.ARRAY_BUFFER,vbo);if(quads.length)gl.bufferSubData(gl.ARRAY_BUFFER,0,quads);if(deferOrCheck)deferOrCheck(check);else check();}
    finally{gl.bindBuffer(gl.ARRAY_BUFFER,array);gl.bindTexture(gl.TEXTURE_2D,binding);gl.pixelStorei(gl.UNPACK_ALIGNMENT,alignment);gl.activeTexture(active);}
    entries=pending.next;free=pending.rectangles;epoch=e;sequence=s;
    stats.spriteDrawReuses+=pending.hits;stats.spriteUploadedImages+=pending.missing.length;stats.spriteUploadedBytes+=pending.bytes;stats.spriteCompactions+=pending.compacted?1:0;stats.spriteEvictions+=pending.evictions;stats.spriteDescriptorUploads++;stats.spriteCommitCalls++;stats.spriteDescriptorBytes+=quads.byteLength;stats.spriteSourceImageBytes+=pending.sourceBytes;stats.spriteFrameGroups=pending.order.length;stats.spriteResidentImages=entries.size;
    const count=pending.order.length;pending=null;return count;
  }catch(error){failed=true;throw error;}}
  function assertCommitted(){alive();if(pending){failed=true;throw new Error('Sprite plan was not committed before presentation');}}
  function dispose(){if(disposed)return;disposed=true;entries.clear();free=[];pending=null;stats.spriteResidentImages=0;}
  return{plan,commit,assertCommitted,dispose};
}
