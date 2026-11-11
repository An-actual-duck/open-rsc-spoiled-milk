/** Pure validation of bounded transport metadata. Returned data owns only small
 * arrays/tokens, never the borrowed RGB/T typed arrays. No graphics calls. */
export function validateOverlayDelta({width:w,height:h,generation,frameSequence,reset,partial,baseGeneration,baseFrameSequence,bands,work,rgb,transmittance},history,resourcesReady,maxTextureSize=32768,allowObservation=false){
  const token=v=>Number.isSafeInteger(v)&&v>=0;
  if(!Number.isSafeInteger(w)||!Number.isSafeInteger(h)||w<1||h<1||w*h>2097152||h>32768
    ||w>32768||!Number.isInteger(maxTextureSize)||maxTextureSize<1||w>maxTextureSize||h>maxTextureSize
    ||typeof reset!=='boolean'||typeof partial!=='boolean'||!token(generation)||!token(frameSequence))throw new Error('Invalid overlay delta dimensions/token');
  if(!(rgb instanceof Uint8Array)||rgb.length!==w*h*4||!(transmittance instanceof Float32Array)||transmittance.length!==w*h)throw new Error('Invalid overlay delta borrowed planes');
  if(!bands||!Number.isSafeInteger(bands.length)||bands.length%2||bands.length>8
    ||!work||work.length!==6&&(!allowObservation||work.length<15))throw new Error('Invalid overlay delta metadata shape');
  const ownedBands=Array.from(bands),ownedWork=Array.from(work);
  if(ownedBands.some(v=>!Number.isSafeInteger(v))||ownedWork.some(v=>!Number.isSafeInteger(v)||v<0))throw new Error('Invalid overlay delta metadata numbers');
  let tileObservation;
  if(ownedWork.length!==6){
    const cells=Math.ceil(w/32)*Math.ceil(h/16),wordCount=Math.ceil(cells/16);
    if(ownedWork.length!==15+wordCount||ownedWork[6]!==1||cells>16384||ownedWork[13]!==Math.ceil(w/32)||ownedWork[14]!==Math.ceil(h/16)
      ||ownedWork[7]>ownedWork[9]||ownedWork[9]>cells||ownedWork[8]>ownedWork[10]||ownedWork[10]>w*h
      ||ownedWork[11]>2147483647||ownedWork[12]>2147483647)throw new Error('Invalid overlay tile observation');
    let count=0,area=0;const columns=ownedWork[13];
    for(let id=0;id<wordCount*16;id++){
      const bits=ownedWork[15+(id>>4)];if(bits>65535)throw new Error('Invalid overlay tile bitmap');
      if((bits&(1<<(id&15)))!==0){if(id>=cells)throw new Error('Invalid overlay tile padding');count++;area+=Math.min(32,w-(id%columns)*32)*Math.min(16,h-Math.floor(id/columns)*16);}
    }
    if(count!==ownedWork[9]||area!==ownedWork[10])throw new Error('Invalid overlay tile footprint receipt');
    tileObservation={scheme:1,tileWidth:32,tileHeight:16,currentTiles:ownedWork[7],currentArea:ownedWork[8],unionTiles:count,unionArea:area,markerMisses:ownedWork[11],spanGroups:ownedWork[12],columns,rows:ownedWork[14],unionWords:ownedWork.slice(15)};
  }
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
  return {width:w,height:h,token:{generation,frameSequence},partial,bands:ownedBands,rows,work:ownedWork,tileObservation};
}
