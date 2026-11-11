package orsc.graphics.two;

import java.util.Arrays;
import java.util.Random;

/** Independent per-pixel write trace, confined to this CPU oracle. */
public final class WorldOverlayTileTrackerFixture {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static boolean[] tiles(boolean[] pixels,int w,int h){
        int cols=(w+31)/32;boolean[] result=new boolean[cols*((h+15)/16)];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(pixels[y*w+x])result[(y/16)*cols+x/32]=true;
        return result;
    }
    public static void main(String[] args) {
        Random random=new Random(718003);
        WorldOverlayTileTracker tracker=new WorldOverlayTileTracker();
        for(int sample=0;sample<250;sample++){
            int w=1+random.nextInt(151),h=1+random.nextInt(129);check(tracker.begin(w,h),"supported odd dimensions");
            boolean[] written=new boolean[w*h];
            for(int operation=0;operation<100;operation++){
                int from=random.nextInt(w*h),to=from+random.nextInt(w*h-from+1);
                if(operation%3!=0){check(tracker.mark(from),"mark accepted");written[from]=true;}
                else{check(tracker.span(from,to),"span accepted");Arrays.fill(written,from,to,true);}
                if(operation%17==0)check(Arrays.equals(tracker.snapshot(),tiles(written,w,h)),"exact tile mask from pixel writer trace");
            }
            boolean[] frozen=tracker.snapshot();check(Arrays.equals(frozen,tiles(written,w,h)),"final exact footprint");
            check(tracker.begin(w,h),"fresh frame");check(tracker.mark(0),"first low index after reset");
            int[] observation=tracker.observe(frozen);
            check(observation[1]==1&&observation[3]>=1,"current and previous union counters");
            check(Arrays.equals(frozen,tiles(written,w,h)),"old ACK snapshot not mutated by fresh marks");
        }
        check(!tracker.begin(Integer.MAX_VALUE,1),"overflowing dimensions rejected");
        check(!tracker.begin(32768,32768),"oversized dimensions rejected");
        tracker.begin(65,33);check(!tracker.mark(-1)&&!tracker.mark(65*33),"invalid pixels rejected");
        check(!tracker.span(-1,2)&&!tracker.span(0,65*33+1),"invalid spans rejected");

        // Real coverage hooks, one existing transport owner and actual ACK history.
        int w=65,h=33;int[] rgb=new int[w*h];WorldOverlayCoverage coverage=new WorldOverlayCoverage();
        coverage.beginFrame();check(coverage.begin(rgb,w,h),"world begin");coverage.opaque(rgb,0);
        WorldOverlayCoverage.Frame frame=coverage.borrow(rgb,w,h);WorldOverlayCoverage.Delta plan=frame.prepareDeltaTransport(true);
        check(!plan.partial&&plan.work()[6]==1&&plan.work()[7]==1&&plan.work()[9]==1,"first FULL reports actual single touched tile");
        frame.acknowledgeDeltaTransport(plan);
        coverage.beginFrame();coverage.begin(rgb,w,h);frame=coverage.borrow(rgb,w,h);plan=frame.prepareDeltaTransport(false);
        check(plan.work()[7]==0&&plan.work()[9]==1,"disappearing writes union previous ACK footprint");
        frame.acknowledgeDeltaTransport(plan);
        coverage.beginFrame();coverage.begin(rgb,w,h);frame=coverage.borrow(rgb,w,h);plan=frame.prepareDeltaTransport(false);
        check(plan.work()[7]==0&&plan.work()[9]==0,"empty ACK footprint replaces old rather than full upload extent");
        frame.acknowledgeDeltaTransport(plan);
        coverage.beginFrame();coverage.begin(rgb,w,h);coverage.opaque(rgb,w*h-1);frame=coverage.borrow(rgb,w,h);frame.prepareDeltaTransport(false); // no submit/ACK
        coverage.beginFrame();coverage.begin(rgb,w,h);coverage.multiply(rgb,32,128);coverage.opaqueSpan(rgb,64,67);
        frame=coverage.borrow(rgb,w,h);plan=frame.prepareDeltaTransport(false);
        boolean[] expected=new boolean[w*h];expected[32]=true;Arrays.fill(expected,64,67,true);
        boolean[] expectedTiles=tiles(expected,w,h);int[] work=plan.work();int columns=(w+31)/32;
        for(int id=0;id<expectedTiles.length;id++)check(((work[15+(id/16)]&(1<<(id%16)))!=0)==expectedTiles[id],"held/unsubmitted tiles never enter ACK union");
        check(work[13]==columns&&work[14]==(h+15)/16,"bounded diagnostic grid shape");
        System.out.println("PASS actual writer tiles checks="+checks+"; independent pixel traces, row-crossing spans, odd edges, fresh cache, ACK disappearance and held frames");
    }
}
