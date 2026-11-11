package orsc.graphics.two;
import java.util.Arrays;
import java.util.Random;

/** Independent per-pixel coverage reference. No candidate planner algorithm reused. */
public final class WorldOverlayTileIndependentReviewFixture {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static boolean[] reference(int width,int height,boolean[] pixels){
        int columns=(width+31)/32,rows=(height+15)/16;
        boolean[] tiles=new boolean[columns*rows];
        for(int index=0;index<pixels.length;index++)if(pixels[index]){
            int y=index/width,x=index%width;tiles[(y/16)*columns+x/32]=true;
        }
        return tiles;
    }
    private static void equal(WorldOverlayTileTracker tracker,int width,int height,boolean[] pixels){
        check(Arrays.equals(tracker.snapshot(),reference(width,height,pixels)),"per-pixel tile reference "+width+"x"+height);
    }
    private static void mark(WorldOverlayTileTracker tracker,boolean[] pixels,int index){check(tracker.mark(index),"valid mark");pixels[index]=true;}
    private static void span(WorldOverlayTileTracker tracker,boolean[] pixels,int start,int end){check(tracker.span(start,end),"valid span");Arrays.fill(pixels,start,end,true);}
    private static void planTiles(WorldOverlayDeltaTransport.Plan plan,int width,int height,boolean[] expected){
        check(plan.work.length>=15,"transport carries observation metadata");
        check(plan.work[6]==1,"observe-only scheme");
        int columns=(width+31)/32,count=0,area=0;
        for(int id=0;id<expected.length;id++){
            check((((plan.work[15+id/16]>>>(id%16))&1)!=0)==expected[id],"ACK union uses writer footprint");
            if(expected[id]){count++;area+=Math.min(32,width-(id%columns)*32)*Math.min(16,height-(id/columns)*16);}
        }
        check(plan.work[9]==count&&plan.work[10]==area,"ACK union metadata exact");
    }
    private static void acknowledgement(){
        System.setProperty("spoiledmilk.browserOverlayRectMode","observe");
        int width=65,height=33;int[] pixels=new int[width*height];
        WorldOverlayDeltaTransport transport=new WorldOverlayDeltaTransport();
        boolean[] previous=new boolean[((width+31)/32)*((height+15)/16)];
        for(int frame=1;frame<=4;frame++){
            transport.beginFrame();transport.beginFresh(pixels,width,height,frame,frame);
            boolean[] current=new boolean[previous.length];
            if(frame<=2){int at=frame==1?1:2144;transport.mark(at);boolean[] values=new boolean[pixels.length];values[at]=true;current=reference(width,height,values);}
            WorldOverlayDeltaTransport.Plan plan=transport.prepare(pixels,width,height,frame,frame,true);
            boolean[] union=current.clone();for(int n=0;n<union.length;n++)union[n]|=previous[n];
            planTiles(plan,width,height,union);
            transport.acknowledge(plan,pixels,width,height,frame,frame);previous=current;
        }
        // A full first upload covers the viewport but only the ACTUAL first
        // frame writer tiles enter ACK history. The above third/fourth frames
        // therefore cannot retain all uploaded coverage as the next footprint.
        transport.beginFresh(pixels,width,height,5,5);transport.mark(0);
        WorldOverlayDeltaTransport.Plan stale=transport.prepare(pixels,width,height,5,5,true);
        transport.beginFrame();transport.beginFresh(pixels,width,height,6,6);
        try{transport.acknowledge(stale,pixels,width,height,6,6);throw new AssertionError("stale ACK accepted");}
        catch(IllegalStateException expected){checks++;}
        transport.beginFresh(pixels,width,height,7,7);transport.markSpan(64,66);
        WorldOverlayDeltaTransport.Plan fresh=transport.prepare(pixels,width,height,7,7,true);
        boolean[] values=new boolean[pixels.length];Arrays.fill(values,64,66,true);
        planTiles(fresh,width,height,reference(width,height,values));
        transport.mark(1000);check(transport.prepare(pixels,width,height,7,7,true)==null,"writer after prepare invalidates pending plan");
    }
    public static void main(String[] args){
        Random random=new Random(0x16435);
        for(int width:new int[]{1,7,31,32,33,65,137})for(int height:new int[]{1,15,16,17,35}){
            WorldOverlayTileTracker tracker=new WorldOverlayTileTracker();check(tracker.begin(width,height),"supported dimensions");
            boolean[] pixels=new boolean[width*height];
            for(int n=0;n<100;n++){
                if((n&1)==0)mark(tracker,pixels,random.nextInt(pixels.length));
                else {int lo=random.nextInt(pixels.length),hi=lo+random.nextInt(pixels.length-lo+1);span(tracker,pixels,lo,hi);}
                equal(tracker,width,height,pixels);
            }
            boolean[] old=tracker.snapshot();check(tracker.begin(width,height),"next frame");Arrays.fill(pixels,false);
            mark(tracker,pixels,random.nextInt(pixels.length));
            int[] observation=tracker.observe(old);boolean[] now=reference(width,height,pixels);int union=0,area=0,currentArea=0;
            for(int index=0;index<now.length;index++){
                int tx=index%((width+31)/32),ty=index/((width+31)/32),a=Math.min(32,width-32*tx)*Math.min(16,height-16*ty);
                boolean expected=old[index]||now[index];if(expected){union++;area+=a;}if(now[index])currentArea+=a;
                check((((observation[9+index/16]>>>(index%16))&1)!=0)==expected,"union bitmap exact");
            }
            check(observation[2]==currentArea&&observation[3]==union&&observation[4]==area,"clipped edge area/counts exact");
        }
        WorldOverlayTileTracker holes=new WorldOverlayTileTracker();check(holes.begin(65,33),"hole setup");boolean[] pixels=new boolean[65*33];
        for(int index:new int[]{1,64,65,129,1040,1104,1105,1169}){mark(holes,pixels,index);equal(holes,65,33,pixels);}
        check(holes.begin(65,33),"span setup");Arrays.fill(pixels,false);
        for(int[] bounds:new int[][]{{64,66},{129,195},{900,1200},{2144,2145}}){span(holes,pixels,bounds[0],bounds[1]);equal(holes,65,33,pixels);}
        check(!holes.mark(-1)&&!holes.mark(2145),"invalid indices rejected");
        check(!holes.span(-1,1)&&!holes.span(2140,2146),"invalid spans rejected");
        check(!holes.begin(2097152,1)&&holes.snapshot()==null,"capacity fallback has no retained tile plane");
        acknowledgement();
        System.out.println("PASS independent pixel-footprint tile reference, cached interval holes, cross-row spans, clipped edges, previous-frame union and capacity: "+checks+" checks; CPU only");
    }
}
