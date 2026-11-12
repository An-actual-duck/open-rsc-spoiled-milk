package orsc.graphics.three;

/** Private browser-only source/catalog bridge. No global classic RSModel ownership. */
public final class WholeOverworldZoneRegistry {
    private static final boolean ENABLED=Boolean.getBoolean("spoiledmilk.browserBenchmark")
        &&Boolean.getBoolean("spoiledmilk.browserWholeZone");
    public interface Resolver {
        Renderer3DWorldChunkFrame.ChunkMesh resolveStatic(int plane,int cellX,int cellY,long canonicalKey,
            int presentationBaseX,int presentationBaseZ);
    }
    private static volatile Resolver resolver;
    private WholeOverworldZoneRegistry() {}
    public static boolean isEnabled(){return ENABLED;}
    public static boolean isStaticSceneReuseEnabled(){return ENABLED&&Boolean.getBoolean("spoiledmilk.browserStaticSceneReuse");}
    public static void install(Resolver next){if(!ENABLED)throw new IllegalStateException("Whole-zone resolver requires private browser mode");resolver=next;}
    public static void clear(){resolver=null;}
    public static Renderer3DWorldChunkFrame.ChunkMesh resolveStatic(int plane,int cellX,int cellY,long key,int baseX,int baseZ){
        Resolver current=resolver;return ENABLED&&current!=null?current.resolveStatic(plane,cellX,cellY,key,baseX,baseZ):null;
    }
}
