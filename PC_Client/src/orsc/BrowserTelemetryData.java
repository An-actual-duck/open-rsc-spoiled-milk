package orsc;

import java.lang.reflect.Field;

/** Small opt-in cumulative profile export. No formatted full telemetry snapshots. */
public final class BrowserTelemetryData {
    private static final String[] FIELDS={
        "clientLoopStats","clientLoopSleepStats","clientLoopUpdateStats","clientLoopRepositionStats","clientLoopDrawStats",
        "sceneRenderStats","sceneModelRotateStats","sceneWorldCullStats","sceneDepthExportStats","sceneLegacyDrawStats","sceneMeshExportStats",
        "frameStats","commitStats","scalarResizeStats","backingCopyStats","presentStats","setGameImageStats","sourceCopyStats","paintImmediateStats","clientLoopUpdateCountStats"
    };
    private static final Object[] STAGES=new Object[FIELDS.length];
    private static final Field[] COUNTS=new Field[FIELDS.length],TOTALS=new Field[FIELDS.length];
    private static final String NAMES=String.join("|",FIELDS);
    static {
        try {
            for(int n=0;n<FIELDS.length;n++) {
                Field f=RenderTelemetry.class.getDeclaredField(FIELDS[n]);f.setAccessible(true);STAGES[n]=f.get(null);
                COUNTS[n]=STAGES[n].getClass().getDeclaredField("count");TOTALS[n]=STAGES[n].getClass().getDeclaredField("total");
                COUNTS[n].setAccessible(true);TOTALS[n].setAccessible(true);
            }
        } catch(ReflectiveOperationException error){throw new IllegalStateException("Browser telemetry layout does not match production",error);}
    }
    private BrowserTelemetryData(){}
    public static String names(){return NAMES;}
    /** count,totalNanos pairs; doubles exactly represent practical cumulative counter ranges. */
    public static double[] snapshot() {
        if(!RenderTelemetry.isEnabled())throw new IllegalStateException("Browser profile requires opt-in telemetry before class initialization");
        double[] values=new double[FIELDS.length*2];
        synchronized(RenderTelemetry.class){
            try{for(int n=0;n<FIELDS.length;n++){values[n*2]=COUNTS[n].getLong(STAGES[n]);values[n*2+1]=TOTALS[n].getLong(STAGES[n]);}}
            catch(IllegalAccessException error){throw new IllegalStateException("Browser telemetry counters inaccessible",error);}
        }
        return values;
    }
}
