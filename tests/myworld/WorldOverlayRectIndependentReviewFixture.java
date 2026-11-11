package orsc.graphics.two;
import java.util.Arrays;
import java.util.Random;

/** Checks coverage obligations, not the candidate rectangle grouping algorithm. */
public final class WorldOverlayRectIndependentReviewFixture {
 private static int checks,rectangles,rows,full;
 private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static boolean[] tiles(int width,int height,boolean[] pixels){
  int columns=(width+31)/32;boolean[] result=new boolean[columns*((height+15)/16)];
  for(int i=0;i<pixels.length;i++)if(pixels[i])result[(i/width/16)*columns+(i%width)/32]=true;
  return result;
 }
 private static void validate(WorldOverlayDeltaTransport.Plan plan,int width,int height,boolean[] union){
  int scheme=plan.work.length>6?plan.work[6]:0;boolean[] uploaded=new boolean[width*height];
  if(scheme==2){
   rectangles++;check(plan.partial&&plan.work[3]==0&&plan.bands.length%4==0&&plan.bands.length<=32,"bounded partial rectangles");
   check(plan.work[4]==plan.bands.length/4,"rectangle work count");int area=0;
   for(int n=0;n<plan.bands.length;n+=4){int x=plan.bands[n],y=plan.bands[n+1],w=plan.bands[n+2],h=plan.bands[n+3];
    check(x>=0&&y>=0&&w>0&&h>0&&x+w<=width&&y+h<=height,"rectangle bounds");
    check(x%32==0&&y%16==0&&(x+w==width||(x+w)%32==0)&&(y+h==height||(y+h)%16==0),"tile aligned edges");
    area+=w*h;for(int py=y;py<y+h;py++)for(int px=x;px<x+w;px++){int at=py*width+px;check(!uploaded[at],"disjoint rectangles");uploaded[at]=true;}
   }
   check((long)area*5<=(long)width*height*3,"rectangle area threshold");
  }else{
   if(plan.partial)rows++;else full++;
   check(plan.bands.length%2==0,"row/full bands");
   for(int n=0;n<plan.bands.length;n+=2)for(int y=plan.bands[n];y<plan.bands[n+1];y++)Arrays.fill(uploaded,y*width,(y+1)*width,true);
  }
  int columns=(width+31)/32;
  for(int i=0;i<uploaded.length;i++)if(union[(i/width/16)*columns+(i%width)/32])check(uploaded[i],"every current/acknowledged writer tile uploaded");
 }
 public static void main(String[] args){
  System.setProperty("spoiledmilk.browserOverlayRectMode","on");Random random=new Random(0x375196);
  for(int width:new int[]{33,65,137})for(int height:new int[]{33,65,127}){
   WorldOverlayDeltaTransport transport=new WorldOverlayDeltaTransport();int[] pixels=new int[width*height];
   boolean[] previous=new boolean[((width+31)/32)*((height+15)/16)];
   for(int frame=1;frame<=45;frame++){
    transport.beginFrame();transport.beginFresh(pixels,width,height,frame,frame);boolean[] written=new boolean[pixels.length];
    int count=frame%5==0?0:frame%5==1?1:frame%5==2?2:frame%5==3?8:35;
    for(int n=0;n<count;n++){
     int at=random.nextInt(pixels.length);
     if((n&1)==0){transport.mark(at);written[at]=true;}
     else{int end=Math.min(pixels.length,at+1+random.nextInt(width+2));transport.markSpan(at,end);Arrays.fill(written,at,end,true);}
    }
    boolean[] current=tiles(width,height,written),union=current.clone();for(int n=0;n<union.length;n++)union[n]|=previous[n];
    WorldOverlayDeltaTransport.Plan plan=transport.prepare(pixels,width,height,frame,frame,true);check(plan!=null,"supported writer plan");validate(plan,width,height,union);
    transport.acknowledge(plan,pixels,width,height,frame,frame);previous=current;
   }
  }
  check(rectangles>0&&full>0,"both rectangle and full paths exercised");
  System.out.println("PASS independent rectangle footprint coverage/disjointness/bounds/budget/ACK checks="+checks+" rectanglePlans="+rectangles+" rowPlans="+rows+" fullPlans="+full+"; CPU only");
 }
}
