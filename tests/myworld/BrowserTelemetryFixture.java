package orsc;

import java.util.*;

/** The bulk browser profile must read the actual production counters without modifying them. */
public final class BrowserTelemetryFixture {
    public static void main(String[] args) {
        System.setProperty("spoiledmilk.rendererTelemetry","true");
        System.setProperty("spoiledmilk.rendererTelemetryInterval","1");
        System.setProperty("spoiledmilk.rendererTelemetryCountersOnly","true");
        System.setProperty("spoiledmilk.rendererSlowFrameMs","1000000");
        if(RenderTelemetry.optionalRuntimeLoad(()->{throw new ArrayIndexOutOfBoundsException("injected unavailable OS service");})!=-1.0D)throw new AssertionError("Optional OS failure lost unavailable marker");
        if(RenderTelemetry.optionalRuntimeLoad(()->.25D)!=.25D)throw new AssertionError("Normal OS load changed");
        for(final Error fatal:new Error[]{new OutOfMemoryError("injected"),new ThreadDeath()}) {
            try{RenderTelemetry.optionalRuntimeLoad(()->{throw fatal;});throw new AssertionError("Fatal VM error swallowed");}catch(Error actual){if(actual!=fatal)throw actual;}
        }
        String[] names=BrowserTelemetryData.names().split("\\|");double[] before=BrowserTelemetryData.snapshot();
        RenderTelemetry.recordScene3DPhases(11,22,33,44,55);RenderTelemetry.recordSceneRender(77);
        RenderTelemetry.recordClientLoop(120,10,20,5,85,1,0,256,false);
        RenderTelemetry.recordFrame(90,1,2,3,84,64,64,1,"fixture","browser-profile-unit");
        double[] after=BrowserTelemetryData.snapshot();Map<String,Long> expected=new HashMap<String,Long>();
        long[] totals={120,10,20,5,85,77,11,22,33,44,55,90,1,2,3,84};
        for(int n=0;n<totals.length;n++)expected.put(names[n],totals[n]);
        expected.put("clientLoopUpdateCountStats",1L);
        if(names.length>32||after.length!=names.length*2||before.length!=after.length)throw new AssertionError("Bulk schema bounds changed");
        for(int n=0;n<names.length;n++) {
            long total=expected.containsKey(names[n])?expected.get(names[n]):0;
            if(after[n*2]-before[n*2]!=(expected.containsKey(names[n])?1:0)||after[n*2+1]-before[n*2+1]!=total)throw new AssertionError("Counter export differs for "+names[n]);
        }
        double[] copy=BrowserTelemetryData.snapshot();after[0]=-1;
        if(!Arrays.equals(copy,BrowserTelemetryData.snapshot()))throw new AssertionError("Export borrowed mutable production counters");
        System.out.println("PASS "+names.length+" actual cumulative stage/update-count counters, bulk schema, coherent snapshot and owned array checks");
    }
}
