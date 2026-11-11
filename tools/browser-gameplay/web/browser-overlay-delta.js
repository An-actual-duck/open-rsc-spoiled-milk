/** Pure validation of bounded transport metadata. Returned data owns only small
 * arrays/tokens, never the borrowed RGB/T typed arrays. No graphics calls. */
export function validateOverlayDelta({width:w,height:h,generation,frameSequence,reset,partial,baseGeneration,baseFrameSequence,bands,work,rgb,transmittance},history,resourcesReady,maxTextureSize=32768){
  const token=v=>Number.isSafeInteger(v)&&v>=0;
  if(!Number.isSafeInteger(w)||!Number.isSafeInteger(h)||w<1||h<1||w*h>2097152||h>32768
    ||w>32768||!Number.isInteger(maxTextureSize)||maxTextureSize<1||w>maxTextureSize||h>maxTextureSize
    ||typeof reset!=='boolean'||typeof partial!=='boolean'||!token(generation)||!token(frameSequence))throw new Error('Invalid overlay delta dimensions/token');
  if(!(rgb instanceof Uint8Array)||rgb.length!==w*h*4||!(transmittance instanceof Float32Array)||transmittance.length!==w*h)throw new Error('Invalid overlay delta borrowed planes');
  if(!bands||!Number.isSafeInteger(bands.length)||bands.length%2||bands.length>8
    ||!work||work.length!==6)throw new Error('Invalid overlay delta metadata shape');
  const ownedBands=Array.from(bands),ownedWork=Array.from(work);
  if(ownedBands.some(v=>!Number.isSafeInteger(v))||ownedWork.some(v=>!Number.isSafeInteger(v)||v<0))throw new Error('Invalid overlay delta metadata numbers');
  let rows=0,end=0;
  for(let n=0;n<ownedBands.length;n+=2){const lo=ownedBands[n],hi=ownedBands[n+1];if(lo<0||hi<=lo||hi>h||n&&lo<=end||lo%16||hi!==h&&hi%16)throw new Error('Invalid overlay delta band ordering/range');rows+=hi-lo;end=hi;}
  if(ownedWork[2]>h||ownedWork[3]!==rows||ownedWork[4]!==ownedBands.length/2||ownedWork[5]!==Number(partial))throw new Error('Invalid overlay delta work receipt');
  if(partial){
    if(reset||!resourcesReady||rows*5>h*3||!token(baseGeneration)||!token(baseFrameSequence)
      ||!history||history.width!==w||history.height!==h||history.generation!==baseGeneration||history.frameSequence!==baseFrameSequence
      ||generation<=baseGeneration||frameSequence<=baseFrameSequence)throw new Error('Overlay delta lost acknowledged texture history');
  }else {
    if(baseGeneration!==-1||baseFrameSequence!==-1||ownedBands.length!==2||ownedBands[0]!==0||ownedBands[1]!==h)throw new Error('Invalid overlay delta full fallback');
  }
  return {width:w,height:h,token:{generation,frameSequence},partial,bands:ownedBands,rows,work:ownedWork};
}
