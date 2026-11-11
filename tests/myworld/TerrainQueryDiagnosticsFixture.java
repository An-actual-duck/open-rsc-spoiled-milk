package orsc.browser;

import java.util.Arrays;
import orsc.NativeLayeredTerrainChunk;
import orsc.NativeLayeredTerrainSnapshot;

/** Periodic metadata contract only; no hot-query counters or game/GPU startup. */
public final class TerrainQueryDiagnosticsFixture {
    private static final String HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final class World { private NativeLayeredTerrainSnapshot nativeLayeredTerrainSnapshot; }
    private static final class Missing { }
    private static void check(boolean value,String reason) { if(!value)throw new AssertionError(reason); }
    private static NativeLayeredTerrainSnapshot snapshot(boolean mixed) {
        NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[9];int at=0;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)chunks[at++]=NativeLayeredTerrainChunk.voidChunk(mixed&&x==0&&y==0?12:24,x,y);
        return new NativeLayeredTerrainSnapshot("test","1.0.0",HASH,24,"global",-7,0,0,1,chunks);
    }
    public static void main(String[] args) {
        BenchmarkCandidatePolicies.TerrainQueryDiagnostics d=new BenchmarkCandidatePolicies.TerrainQueryDiagnostics();
        double[] region={10,20,30};check(Arrays.equals(d.append(null,region),region),"null owner preserves region");
        check(d.append(new Missing(),null).length==0,"old unsupported class preserves unavailable");
        World w=new World();double[] values=d.append(w,region);
        check(values.length==8&&values[0]==10&&values[1]==20&&values[2]==30,"prefix exact");
        check(values[3]==0&&values[4]==0&&values[6]==0&&values[7]==0,"unloaded metadata");
        check(values[5]==(Boolean.getBoolean("spoiledmilk.browserTerrainQueryIndex")?1:0),"actual static enabled");
        w.nativeLayeredTerrainSnapshot=snapshot(false);values=d.append(w,null);
        check(values[3]==1&&values[4]==1&&values[6]==3&&values[7]==4,"uniform eligible grid");
        w.nativeLayeredTerrainSnapshot=snapshot(true);values=d.append(w,null);
        check(values[3]==1&&values[4]==0&&values[6]==3&&values[7]==4,"mixed grid literal fallback");
        check(d.append(new Missing(),null).length==0,"owner change drops stale getter");
        values=d.append(w,region);check(values[4]==0&&values[0]==10,"owner recapture uses current immutable snapshot");
        System.out.println("PASS bounded periodic terrain query metadata, old-candidate fallback, current snapshot/owner transitions and actual policy; CPU only");
    }
}
