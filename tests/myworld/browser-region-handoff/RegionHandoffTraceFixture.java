package orsc;

/** Executes actual existing boundary hooks; no World, UI, JMX, server or listener. */
public final class RegionHandoffTraceFixture {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        boolean expected=Boolean.parseBoolean(args[0]);
        check(BrowserRegionHandoffTrace.ENABLED==expected,"private policy gate");
        BoundaryLoadingDiagnostics.initialize();
        long start=BoundaryLoadingDiagnostics.now();
        BoundaryLoadingDiagnostics.beginContextTransition(8,17,44,111,623,-1,start);
        BoundaryLoadingDiagnostics.updateDestination(-1,2,12,true,false);
        BoundaryLoadingDiagnostics.recordPhase("context","header-decode",start,2000L);
        BoundaryLoadingDiagnostics.recordPhase("client-region","baseline",0L,7000000L);
        BoundaryLoadingDiagnostics.recordSceneBaselinePacket(3,44,17,4,0,1,23);
        BoundaryLoadingDiagnostics.recordPacket(143,100,start,9000L);
        BoundaryLoadingDiagnostics.recordRegionTransition(false,true,48,-48,12,3,1,32);
        BoundaryLoadingDiagnostics.recordAtomicActivationProgress(true,true,true,45);
        BoundaryLoadingDiagnostics.recordPresentationRelease(2,true,166,123456);
        if(expected)BrowserRegionHandoffTrace.bridge(25,3,8,0L,2000000L);
        double[] first=BrowserRegionHandoffTrace.snapshot();
        check(first[0]==1&&first[1]==(expected?1:0)&&first[5]==12,"snapshot header");
        check(!BoundaryLoadingDiagnostics.isEnabled(),"browser does not enable desktop/JMX trace");
        if(!expected){check(start==0L&&first.length==8&&first[2]==0,"OFF has no clocks/events");}
        else {
            check(start>0&&first[4]==10&&first[2]==10&&first[3]==0,"actual hooks emitted ten rows");
            check(first[8+2]==1&&first[8+1]==17,"source context and kind");
            check(first[8+11]==-1,"signed destination retained");
            check(first[8+3*12+3]==16&&first[8+3*12+5]==-1,"baseline phase with unknown start is explicit");
            check(first[8+3*12+6]==7d,"nanosecond duration exported in milliseconds");
            first[8+7]=-999;check(BrowserRegionHandoffTrace.snapshot()[8+7]==8,"owned snapshot copy");
            for(int n=0;n<600;n++)BoundaryLoadingDiagnostics.beginContextTransition(100+n,8,n,0,0,n%2==0?-1:10,BoundaryLoadingDiagnostics.now());
            double[] wrapped=BrowserRegionHandoffTrace.snapshot();
            check(wrapped.length==8+512*12&&wrapped[4]==512&&wrapped[3]==98,"bounded producer wrap");
            for(int n=0;n<512;n++)check(wrapped[8+n*12]==99+n,"chronological ring order");
            for(int n=0;n<200;n++)BoundaryLoadingDiagnostics.recordPacket(143,200,BoundaryLoadingDiagnostics.now(),100);
            double before=BrowserRegionHandoffTrace.snapshot()[2];BoundaryLoadingDiagnostics.recordPacket(143,200,0,100);
            check(before==BrowserRegionHandoffTrace.snapshot()[2],"steady packet budget bounds diagnostics");
            for(int n=0;n<100;n++)BrowserRegionHandoffTrace.bridge(n,2,1,0,100);
            double after=BrowserRegionHandoffTrace.snapshot()[2];BrowserRegionHandoffTrace.bridge(500,3,1,0,100);
            check(after==BrowserRegionHandoffTrace.snapshot()[2],"bridge events bounded per context");
        }
        System.out.println("PASS "+checks+" existing-hook/browser trace checks, enabled="+expected);
    }
}
