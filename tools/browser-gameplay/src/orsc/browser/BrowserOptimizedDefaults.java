package orsc.browser;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Capability admission before Scene/Terrain initialization. No rendering code. */
final class BrowserOptimizedDefaults {
    static final String BATCH="spoiledmilk.browserBatchedModelKernel", TERRAIN="spoiledmilk.browserTerrainQueryIndex";
    interface Capability { boolean available(boolean batch); }
    static final class Resolution {
        final boolean[] requested,available;
        Resolution(boolean[] requested,boolean[] available){this.requested=requested;this.available=available;}
    }
    static Capability capabilities(final ClassLoader loader) {
        return new Capability(){public boolean available(boolean batch){
            try {
                if(batch){
                    Class<?> scene=Class.forName("orsc.graphics.three.Scene",false,loader);
                    Method getter=scene.getMethod("isBrowserBatchedModelKernelEnabled");
                    if(getter.getReturnType()!=boolean.class||Modifier.isStatic(getter.getModifiers()))return false;
                    Class<?> kernel=Class.forName("orsc.graphics.three.BrowserBatchedModelKernel",false,loader);
                    Method transform=kernel.getDeclaredMethod("transformPacket",int.class,int[][].class,int[].class,int[].class,int[].class,int[].class);
                    return transform.getReturnType()==boolean.class&&Modifier.isStatic(transform.getModifiers())&&Modifier.isNative(transform.getModifiers());
                }
                Class<?> snapshot=Class.forName("orsc.NativeLayeredTerrainSnapshot",false,loader);
                Method getter=snapshot.getMethod("isBrowserTerrainQueryIndexEnabled");
                Class<?> chunk=Class.forName("orsc.NativeLayeredTerrainChunk",false,loader);
                Method size=chunk.getMethod("getSize");
                return getter.getReturnType()==boolean.class&&Modifier.isStatic(getter.getModifiers())
                    &&size.getReturnType()==int.class&&!Modifier.isStatic(size.getModifiers());
            } catch(ReflectiveOperationException unsupported){return false;}
            catch(LinkageError unsupported){return false;}
            catch(SecurityException unsupported){return false;}
        }};
    }
    static Resolution apply(boolean webgl,boolean benchmark,Capability capabilities) {
        boolean[] requested=new boolean[2],available=new boolean[2];
        if(!webgl)return new Resolution(requested,available); // software keys stay UNSET
        String[] keys={BATCH,TERRAIN};
        for(int n=0;n<2;n++){
            String value=System.getProperty(keys[n],benchmark?"false":"true");
            if(!"true".equals(value)&&!"false".equals(value))throw new IllegalArgumentException("Invalid optimized renderer policy");
            requested[n]=Boolean.parseBoolean(value);available[n]=capabilities.available(n==0);
            if(benchmark&&requested[n]&&!available[n])throw new IllegalStateException("Requested candidate is absent from this fixture JAR: "+keys[n]);
        }
        // Validate both before writing either; no client classes initialized yet.
        for(int n=0;n<2;n++)System.setProperty(keys[n],String.valueOf(requested[n]&&available[n]));
        return new Resolution(requested,available);
    }
    static boolean[] actual(Object scene,Resolution resolution,int index) {
        if(!resolution.available[index])return new boolean[]{false,false};
        try {
            return index==0?BenchmarkCandidatePolicies.inspectInstance(scene,"isBrowserBatchedModelKernelEnabled")
                :BenchmarkCandidatePolicies.inspect("orsc.NativeLayeredTerrainSnapshot","isBrowserTerrainQueryIndexEnabled");
        } catch(RuntimeException incompatible){return new boolean[]{false,false};}
        catch(LinkageError incompatible){return new boolean[]{false,false};}
    }
}
