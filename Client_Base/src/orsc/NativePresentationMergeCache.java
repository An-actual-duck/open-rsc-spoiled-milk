package orsc;

/** One immutable visual/structural pair per World; never an authority or tile cache. */
public final class NativePresentationMergeCache {
    public static final long MAX_RETAINED_TILE_BYTES=66L*48*48*11;
    private NativeLayeredTerrainSnapshot visual,structural,merged;
    private long hits,builds,retainedTileByteUpperBound;

    public synchronized NativeLayeredTerrainSnapshot merge(NativeLayeredTerrainSnapshot nextVisual,
                                                            NativeLayeredTerrainSnapshot nextStructural) {
        if(merged!=null&&visual==nextVisual&&structural==nextStructural){hits++;return merged;}
        // Validate/build before publication. Invalid input must not poison the
        // previous exact pair, and concurrent preparation cannot build it twice.
        NativeLayeredTerrainSnapshot next=NativeLayeredTerrainSnapshot.mergePresentation(nextVisual,nextStructural);
        long bytes=tileByteUpperBound(nextVisual)+tileByteUpperBound(nextStructural)+tileByteUpperBound(next);
        if(bytes>MAX_RETAINED_TILE_BYTES)throw new IllegalStateException("Native presentation merge cache exceeds retained tile budget");
        visual=nextVisual;structural=nextStructural;merged=next;retainedTileByteUpperBound=bytes;builds++;
        return next;
    }
    private static long tileByteUpperBound(NativeLayeredTerrainSnapshot snapshot) {
        // Every accepted chunk is at most48x48 and11 bytes/tile. Count shared
        // chunks conservatively each time, including both retained source inputs.
        return (long)snapshot.getAvailableChunkCount()*snapshot.getPresentationChunkSize()*snapshot.getPresentationChunkSize()*11;
    }
    public synchronized void clear(){visual=null;structural=null;merged=null;retainedTileByteUpperBound=0;}
    /** Rare diagnostic read only: hits, builds, conservative retained tile bytes. */
    public synchronized long[] diagnostics(){return new long[]{hits,builds,retainedTileByteUpperBound};}
}
