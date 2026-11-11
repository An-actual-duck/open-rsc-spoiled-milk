package orsc;

/** Bounded private transition diagnostics. Durations overlap: never sum phases. */
final class BrowserLoadProfile {
    static final int SIZE=36, PATH=3, INITIALIZE=35, ACTIVE=11, TEXTURES=12,
        GEOMETRY=13, VERTICES=14, MATERIALS=15, SUBMIT=16, SPRITES=17,
        LIGHTING=18, SKY=19, OVERLAY=20, TERMINAL=21;
    final double[] values=new double[SIZE];
    private final long started=System.nanoTime();
    private int phase=-1;private long phaseStart;
    BrowserLoadProfile(long owner,long attempt){values[0]=1;values[1]=owner;values[2]=attempt;}
    void enter(int next){long now=System.nanoTime();if(phase>=0)values[phase]+=(now-phaseStart)/1000000d;phase=next;phaseStart=now;}
    void progress(int[] data){values[6]=data[2];values[7]=data[3];values[8]=data[4];values[9]=data[5];values[10]=Math.max(0,data[3]-data[4]);}
    void elapsed(int field,long start){values[field]+=(System.nanoTime()-start)/1000000d;}
    void submitted(boolean replacement,int vertices,long bytes){values[replacement?24:23]++;values[25]+=vertices;values[26]+=bytes;}
    boolean report(long previousEpoch,int previousPath){return values[PATH]<=2||values[PATH]!=previousPath||values[INITIALIZE]>0||values[23]>0||values[24]>0||values[27]>0||values[9]>0||values[6]!=previousEpoch;}
    double[] finish(int residents,long bytes){enter(-1);values[22]=(System.nanoTime()-started)/1000000d;values[29]=residents;values[30]=bytes;values[31]=Math.max(0,values[8]-values[23]);values[34]=Math.max(0,values[7]-values[8]);return values.clone();}
}
