/** Explicit held-output intervals supplement fresh-to-fresh samples. The first
 * resumed fresh frame includes every held millisecond; a held image is no sample. */
export function createBoundedFrameGap(stats){
  let start=null,lastFresh=0;
  Object.assign(stats,{completedHeldIntervals:0,maxHeldIntervalMilliseconds:0,lastHeldIntervalMilliseconds:0,
    maxHeldFreshGapMilliseconds:0,lastHeldFreshGapMilliseconds:0,pendingHeldMilliseconds:0});
  function held(now,previousFresh=0){if(start===null){start=now;lastFresh=previousFresh;}stats.pendingHeldMilliseconds=Math.max(0,now-start);}
  function resumed(now){if(start===null)return;const elapsed=Math.max(0,now-start),freshGap=lastFresh>0?Math.max(0,now-lastFresh):0;
    stats.completedHeldIntervals++;stats.lastHeldIntervalMilliseconds=elapsed;stats.maxHeldIntervalMilliseconds=Math.max(stats.maxHeldIntervalMilliseconds,elapsed);
    stats.lastHeldFreshGapMilliseconds=freshGap;stats.maxHeldFreshGapMilliseconds=Math.max(stats.maxHeldFreshGapMilliseconds,freshGap);stats.pendingHeldMilliseconds=0;start=null;
  }
  return {held,resumed};
}
