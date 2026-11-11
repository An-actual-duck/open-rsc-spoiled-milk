/** Bounded JS-owned scratch. A pack call borrows input views synchronously;
 * only scratch/counters survive it. Raw byte copies preserve Float32 payloads. */
export function createOverlayStaging({stats={},measure=false,allocate=bytes=>new ArrayBuffer(bytes)}={}){
  const MAX_PIXELS=2097152,MAX_CAPACITY=12*1024*1024,QUANTUM=65536;
  let buffer=null,capacity=0;
  Object.assign(stats,{overlayStagingMaxBytes:MAX_CAPACITY,overlayStagingPeakBudgetBytes:2*MAX_CAPACITY,overlayStagingRetainedBytes:0,
    overlayStagingPeakLiveBytes:0,overlayStagingAllocations:0,overlayGatheredBytes:0,overlayStagedBytes:0,
    overlayPackingMeasurements:0,overlayPackingMilliseconds:0,overlayPackingLastMilliseconds:0,overlayPackingMaxMilliseconds:0});
  function pack(plan,rgb,transmittance){
    const {width:w,height:h,rectangles:r}=plan||{};
    if(!Number.isSafeInteger(w)||!Number.isSafeInteger(h)||w<1||h<1||w>32768||h>32768||w*h>MAX_PIXELS
      ||!(rgb instanceof Uint8Array)||rgb.length!==w*h*4||!(transmittance instanceof Float32Array)||transmittance.length!==w*h
      ||!r||!Number.isSafeInteger(r.length)||r.length%4||r.length>32)throw new Error('Invalid overlay packing inputs');
    let area=0;const offsets=new Int32Array(r.length/4);
    for(let n=0;n<r.length;n+=4){
      const x=r[n],y=r[n+1],rw=r[n+2],rh=r[n+3];
      if(![x,y,rw,rh].every(Number.isSafeInteger)||x<0||y<0||rw<1||rh<1||x+rw>w||y+rh>h)throw new Error('Invalid overlay packing rectangle');
      offsets[n/4]=area;area+=rw*rh;
    }
    if(area!==plan.area||area*5>w*h*3||area*8>MAX_CAPACITY)throw new Error('Overlay packing exceeds bounded plan');
    const started=measure?performance.now():0,bytes=area*8;
    if(bytes>capacity){
      const nextCapacity=Math.min(MAX_CAPACITY,Math.max(Math.ceil(bytes/QUANTUM)*QUANTUM,capacity*2));
      const next=allocate(nextCapacity);
      if(!(next instanceof ArrayBuffer)||next.byteLength!==nextCapacity)throw new Error('Invalid overlay staging allocation');
      stats.overlayStagingPeakLiveBytes=Math.max(stats.overlayStagingPeakLiveBytes,capacity+nextCapacity);
      buffer=next;capacity=nextCapacity;stats.overlayStagingRetainedBytes=capacity;stats.overlayStagingAllocations++;
    }
    // The no-op path does not allocate an unneeded buffer or retain source data.
    if(!bytes)return {rgb:new Uint8Array(0),transmittance:new Float32Array(0),offsets,bytes:0};
    const output=new Uint8Array(buffer),tBytes=new Uint8Array(transmittance.buffer,transmittance.byteOffset,transmittance.byteLength);
    for(let n=0;n<r.length;n+=4){
      const x=r[n],y=r[n+1],rw=r[n+2],rh=r[n+3],base=offsets[n/4]*4;
      for(let row=0;row<rh;row++){
        const from=((y+row)*w+x)*4,to=from+rw*4,dest=base+row*rw*4;
        output.set(rgb.subarray(from,to),dest);output.set(tBytes.subarray(from,to),area*4+dest);
      }
    }
    stats.overlayGatheredBytes+=bytes;stats.overlayStagedBytes+=bytes;
    if(measure){const elapsed=performance.now()-started;stats.overlayPackingMeasurements++;stats.overlayPackingMilliseconds+=elapsed;stats.overlayPackingLastMilliseconds=elapsed;stats.overlayPackingMaxMilliseconds=Math.max(stats.overlayPackingMaxMilliseconds,elapsed);}
    return {rgb:new Uint8Array(buffer,0,area*4),transmittance:new Float32Array(buffer,area*4,area),offsets,bytes};
  }
  function dispose(){buffer=null;capacity=0;stats.overlayStagingRetainedBytes=0;}
  return {pack,dispose};
}
