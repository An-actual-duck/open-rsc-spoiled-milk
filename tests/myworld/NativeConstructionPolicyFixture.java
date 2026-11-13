package orsc.graphics.three;
import java.lang.reflect.*;
/** Assert the actual builder selects exactly the independently requested path. */
public final class NativeConstructionPolicyFixture {
    public static void main(String[] args) throws Exception {
        boolean browser = Boolean.getBoolean("spoiledmilk.browserBenchmark");
        boolean publicBrowser = Boolean.getBoolean("spoiledmilk.browserPublicAlpha");
        boolean region = browser && Boolean.getBoolean("spoiledmilk.browserRegionBuild");
        boolean nativeMode = !browser && !publicBrowser;
        boolean index = region || (nativeMode && Boolean.getBoolean("spoiledmilk.nativeConstructionVertexIndex"));
        boolean primitive = region || (nativeMode && Boolean.getBoolean("spoiledmilk.nativePrimitiveWorldBuild"));
        if (BrowserRegionBuild.isEnabled() != region || BrowserRegionBuild.useConstructionIndex() != index
            || BrowserRegionBuild.usePrimitiveWorldBuild() != primitive) throw new AssertionError("Policy isolation");
        Class<?> type = Class.forName("orsc.graphics.three.World$WorldGpuChunkMeshBuilder");
        Constructor<?> ctor = type.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,boolean.class);
        ctor.setAccessible(true); Object builder = ctor.newInstance(0,0,0,0,0,false);
        int checked = 0;
        for (Field field : type.getDeclaredFields()) if (field.getName().endsWith("Primitive")) {
            field.setAccessible(true);
            if ((field.get(builder) != null) != primitive) throw new AssertionError(field.getName());
            checked++;
        }
        if (checked != 10) throw new AssertionError("Accumulator coverage " + checked);
        System.out.println("PASS native index=" + index + " primitives=" + primitive + " browserHash=" + region);
    }
}
