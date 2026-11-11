package orsc.graphics.three;

import orsc.NativeLayeredTerrainSnapshot;

/** Private immutable CPU preparation owner; local network activation remains authoritative. */
public final class WholeOverworldRamRegistry {
    private static final boolean ENABLED=Boolean.getBoolean("spoiledmilk.browserBenchmark")&&Boolean.getBoolean("spoiledmilk.browserWholeZone")&&Boolean.getBoolean("spoiledmilk.browserWholeRam");
    public interface Provider {
        boolean accepts(World world,NativeLayeredTerrainSnapshot snapshot);
        boolean acceptsCaptured(NativeLayeredTerrainSnapshot snapshot,int capturedOffsetTilesX,int capturedOffsetTilesZ,boolean includeRoofGeometry);
        boolean activate(World world,int plane,int sectionX,int sectionY);
        Renderer3DWorldChunkFrame queryFrame(World world,int logicalSectionX,int logicalSectionY,int capturedOffsetTilesX,int capturedOffsetTilesZ);
        void avoidedBuild();
    }
    private static volatile Provider provider;
    private WholeOverworldRamRegistry(){}
    public static boolean isEnabled(){return ENABLED;}
    public static void install(Provider value){if(!ENABLED||value==null)throw new IllegalStateException("Private whole-RAM provider gate");provider=value;}
    public static void clear(){provider=null;}
    static Provider forWorld(World world,NativeLayeredTerrainSnapshot snapshot){Provider p=provider;return ENABLED&&p!=null&&p.accepts(world,snapshot)?p:null;}
    static Provider forCaptured(NativeLayeredTerrainSnapshot snapshot,int offsetX,int offsetZ,boolean includeRoofGeometry){Provider p=provider;return ENABLED&&p!=null&&p.acceptsCaptured(snapshot,offsetX,offsetZ,includeRoofGeometry)?p:null;}
}
