package orsc.graphics.three;

/** Private diagnostic totals. Owners 0 unknown, 1 delta, 2 baseline, 3 canonical.
 * Phases: clone, transform, bounds, normals-inclusive, diffuse, placement-light.
 * Normals includes diffuse; clone includes any source finalization. Never sum them.
 * The transition ordinal is NOT a server/source epoch. Correlate with load receipts.
 */
public final class BrowserFinalModelProfile {
    public static final boolean ENABLED=Boolean.getBoolean("spoiledmilk.browserBenchmark")&&Boolean.getBoolean("spoiledmilk.browserFinalModelProfile");
    private static final long[][] COUNTS=ENABLED?new long[4][12]:null;
    private static long transition;
    private BrowserFinalModelProfile(){}
    public static long start(){return ENABLED?System.nanoTime():0;}
    public static synchronized void elapsed(int owner,int phase,long start){
        if(!ENABLED)return;
        if(owner<0||owner>3||phase<0||phase>5)throw new IllegalArgumentException("Diagnostic owner/phase");
        COUNTS[owner][phase*2]++;COUNTS[owner][phase*2+1]+=Math.max(0,System.nanoTime()-start);
    }
    public static synchronized void transition(){if(ENABLED)transition++;}
    public static synchronized double[] snapshot(){
        double[] result=new double[51];result[0]=1;result[1]=ENABLED?1:0;result[2]=transition;
        if(ENABLED)for(int o=0;o<4;o++)for(int p=0;p<12;p++)result[3+o*12+p]=p%2==0?COUNTS[o][p]:COUNTS[o][p]/1000000d;
        return result;
    }
}
