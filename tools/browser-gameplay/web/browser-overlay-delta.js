/** Pure validation of bounded transport metadata. Owns only metadata/tokens;
 * never borrowed RGB/T arrays. All validation precedes any graphics operation. */
export function validateOverlayDelta({width:w,height:h,generation,frameSequence,reset,partial,baseGeneration,baseFrameSequence,bands,work,rgb,transmittance},history,resourcesReady,maxTextureSize=32768,requestedMode='off'){
  const mode=requestedMode===true?'observe':requestedMode===false?'off':requestedMode;
  const token=v=>Number.isSafeInteger(v)&&v>=0;
  if(!['off','observe','on','packed'].includes(mode)||!Number.isSafeInteger(w)||!Number.isSafeInteger(h)||w<1||h<1||w*h>2097152||h>32768
    ||w>32768||!Number.isInteger(maxTextureSize)||maxTextureSize<1||w>maxTextureSize||h>maxTextureSize
    ||typeof reset!=='boolean'||typeof partial!=='boolean'||!token(generation)||!token(frameSequence))throw new Error('Invalid overlay delta dimensions/token');
  if(!(rgb instanceof Uint8Array)||rgb.length!==w*h*4||!(transmittance instanceof Float32Array)||transmittance.length!==w*h)throw new Error('Invalid overlay delta borrowed planes');
  const bandLength=bands?.length,workLength=work?.length,cells=mode==='off'?0:Math.ceil(w/32)*Math.ceil(h/16);
  if(!Number.isSafeInteger(workLength)||workLength!==6&&(mode==='off'||cells>16384||workLength!==15+Math.ceil(cells/16)))throw new Error('Invalid overlay delta metadata shape');
  const scheme=workLength===6?0:work[6],rectangle=scheme===2;
  if(![0,1,2].includes(scheme)||rectangle&&!['on','packed'].includes(mode)||!Number.isSafeInteger(bandLength)||bandLength<0
    ||bandLength%(rectangle?4:2)||bandLength>(rectangle?32:8))throw new Error('Invalid overlay delta metadata shape');
  const ownedBands=new Array(bandLength),ownedWork=new Array(workLength);
  for(let n=0;n<bandLength;n++)ownedBands[n]=bands[n];
  for(let n=0;n<workLength;n++)ownedWork[n]=work[n];
  if(ownedBands.some(v=>!Number.isSafeInteger(v))||ownedWork.some(v=>!Number.isSafeInteger(v)||v<0))throw new Error('Invalid overlay delta metadata numbers');
  let tileObservation;
  if(workLength!==6){
    const wordCount=Math.ceil(cells/16),columns=Math.ceil(w/32),rows=Math.ceil(h/16);
    if(scheme===0||ownedWork[13]!==columns||ownedWork[14]!==rows||ownedWork[7]>ownedWork[9]||ownedWork[9]>cells
      ||ownedWork[8]<ownedWork[7]||ownedWork[8]>ownedWork[7]*512||ownedWork[8]>ownedWork[10]||ownedWork[10]>w*h
      ||ownedWork[11]>2147483647||ownedWork[12]>2147483647)throw new Error('Invalid overlay tile observation');
    let count=0,area=0;
    for(let id=0;id<wordCount*16;id++){
      const bits=ownedWork[15+(id>>4)];if(bits>65535)throw new Error('Invalid overlay tile bitmap');
      if((bits&(1<<(id&15)))!==0){if(id>=cells)throw new Error('Invalid overlay tile padding');count++;area+=Math.min(32,w-(id%columns)*32)*Math.min(16,h-Math.floor(id/columns)*16);}
    }
    if(count!==ownedWork[9]||area!==ownedWork[10])throw new Error('Invalid overlay tile footprint receipt');
    tileObservation={scheme,wordBits:16,tileWidth:32,tileHeight:16,currentTiles:ownedWork[7],currentArea:ownedWork[8],unionTiles:count,unionArea:area,markerMisses:ownedWork[11],spanGroups:ownedWork[12],columns,rows,unionWords:ownedWork.slice(15)};
  }
  let rows=0,area=0,end=0;
  if(rectangle){
    if(!partial||ownedWork[3]!==0)throw new Error('Invalid overlay rectangle mode');
    for(let n=0;n<bandLength;n+=4){
      const [x,y,rw,rh]=ownedBands.slice(n,n+4),right=x+rw,bottom=y+rh;
      if(x<0||y<0||rw<1||rh<1||right>w||bottom>h||x%32||y%16||right!==w&&right%32||bottom!==h&&bottom%16
        ||n&&(y<ownedBands[n-3]||y===ownedBands[n-3]&&x<=ownedBands[n-4]))throw new Error('Invalid overlay rectangle ordering/range');
      for(let p=0;p<n;p+=4)if(x<ownedBands[p]+ownedBands[p+2]&&right>ownedBands[p]&&y<ownedBands[p+1]+ownedBands[p+3]&&bottom>ownedBands[p+1])throw new Error('Overlapping overlay rectangles');
      const source=y*w+x,last=(rh-1)*w+rw;
      if(!Number.isSafeInteger(source+last)||source+last>transmittance.length||(source+last)*4>rgb.length)throw new Error('Invalid strided overlay source bounds');
      area+=rw*rh;
    }
    // Every acknowledged/current dirty tile must be wholly covered. Metadata only.
    for(let id=0;id<cells;id++)if((ownedWork[15+(id>>4)]&(1<<(id&15)))!==0){
      const x=(id%tileObservation.columns)*32,y=Math.floor(id/tileObservation.columns)*16,right=Math.min(w,x+32),bottom=Math.min(h,y+16);
      let covered=false;for(let n=0;n<bandLength;n+=4)if(x>=ownedBands[n]&&y>=ownedBands[n+1]&&right<=ownedBands[n]+ownedBands[n+2]&&bottom<=ownedBands[n+1]+ownedBands[n+3]){covered=true;break;}
      if(!covered)throw new Error('Overlay rectangle omitted dirty tile');
    }
  }else{
    for(let n=0;n<bandLength;n+=2){const lo=ownedBands[n],hi=ownedBands[n+1];if(lo<0||hi<=lo||hi>h||n&&lo<=end||lo%16||hi!==h&&hi%16)throw new Error('Invalid overlay delta band ordering/range');rows+=hi-lo;end=hi;}
    area=rows*w;
  }
  if(ownedWork[2]>h||ownedWork[3]!==rows||ownedWork[4]!==bandLength/(rectangle?4:2)||ownedWork[5]!==Number(partial))throw new Error('Invalid overlay delta work receipt');
  if(partial){
    if(reset||!resourcesReady||area*5>w*h*3||!token(baseGeneration)||!token(baseFrameSequence)
      ||!history||history.width!==w||history.height!==h||history.generation!==baseGeneration||history.frameSequence!==baseFrameSequence
      ||generation<=baseGeneration||frameSequence<=baseFrameSequence)throw new Error('Overlay delta lost acknowledged texture history');
  }else if(rectangle||baseGeneration!==-1||baseFrameSequence!==-1||bandLength!==2||ownedBands[0]!==0||ownedBands[1]!==h)throw new Error('Invalid overlay delta full fallback');
  return {width:w,height:h,token:{generation,frameSequence},partial,bands:ownedBands,rectangles:rectangle?ownedBands:null,rows,area,work:ownedWork,tileObservation};
}
