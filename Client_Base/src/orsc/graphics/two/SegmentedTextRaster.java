package orsc.graphics.two;

import java.util.ArrayList;
import java.util.Arrays;

/** Private contiguous-TEXT owner. Captures plots once, never reconstructs UI. */
public final class SegmentedTextRaster {
    public static final int MAX_TEXTS=256,MAX_GLYPHS=4096,MAX_FONT_BYTES=8*1024*1024,MAX_WORK=2*1024*1024;
    public interface Backend { int raster(int[] header,int[] texts,int[] plots,byte[] fonts,int[] c,float[] t,double generation,double sequence); }
    public interface Owner {
        boolean eligible();
        WorldOverlayCoverage.Frame frame();
        boolean current(WorldOverlayCoverage.Frame frame);
        void committed(WorldOverlayCoverage.Frame frame,boolean wrote);
        void poison(String reason);
    }
    public interface Replay { void plots(byte[] font,int[] plots,int left,int top,int right,int bottom); }
    private static volatile Backend installed;
    public static void install(Backend backend){installed=backend;}
    public static boolean available(){return installed!=null;}
    private final Owner owner;
    private final ArrayList<Text> pending=new ArrayList<Text>();
    private WorldOverlayCoverage.Frame frame;
    private int glyphCount,fontBytes,work;
    // Saturating cumulative scalar observations; exported only at low frequency.
    private final int[] stats=new int[8]; // attempts,commits,nativeCalls,texts,glyphs,rejections,literalFallbacks,poisonedFrames
    private static final class Text {
        final byte[] font;final int[] plots;final int l,t,r,b;final Replay replay;
        Text(byte[] font,int[] plots,int l,int t,int r,int b,Replay replay){this.font=font;this.plots=plots;this.l=l;this.t=t;this.r=r;this.b=b;this.replay=replay;}
    }
    public SegmentedTextRaster(Owner owner){this.owner=owner;}
    private void add(int at,int n){stats[at]=n>Integer.MAX_VALUE-stats[at]?Integer.MAX_VALUE:stats[at]+n;}
    public int[] work(){return stats.clone();}
    public int pendingTexts(){return pending.size();}
    private static int validatedWork(byte[] f,int[] p,int n,int l,int t,int r,int b,int width,int height){
        if(l<0||t<0||r<l||b<t||r>width||b>height)return -1;int work=0;
        for(int i=0;i<n;i++){
            int at=i*5,x=p[at],y=p[at+1],a=p[at+3],aa=p[at+4];
            if(x < -32768||x>32768||y < -32768||y>32768||a<0||a>f.length-8||(aa!=0&&aa!=1))return -1;
            int w=f[a+3],h=f[a+4],gx=x+f[a+5],gy=y-f[a+6],src=(f[a]<<14)+(f[a+1]<<7)+f[a+2],stride=w;
            if(gy<t){int lost=t-gy;src+=lost*w;h-=lost;gy=t;}
            if(gy+h>=b)h-=1+gy+h-b;
            if(gx<l){int lost=l-gx;src+=lost;w-=lost;gx=l;}
            if(gx+w>=r)w-=1+gx+w-r;
            if(w<=0||h<=0)continue;
            if(src<0||stride<0||(long)src+(long)(h-1)*stride+w>f.length||gx<0||gy<0||gx+w>width||gy+h>height)return -1;
            work+=w*h;if(work>MAX_WORK)return -1;
        }return work;
    }
    public boolean record(byte[] font,int[] plots,int count,int l,int t,int r,int b,Replay replay){
        if(installed==null||!owner.eligible()){flush();return false;}
        WorldOverlayCoverage.Frame now=owner.frame();
        if(now==null||!now.valid||!now.transparent||now.mode!=WorldOverlayCoverage.MODE_WORLD||now.deltaTransportOwner()!=null){flush();return false;}
        if(frame!=null&&!same(now)){flush();now=owner.frame();}
        if(font==null||plots==null||replay==null||count<0||count>MAX_GLYPHS||plots.length<count*5||font.length>MAX_FONT_BYTES){flush();return false;}
        byte[] captured=font.clone();int[] scalar=Arrays.copyOf(plots,count*5);
        int selected=validatedWork(captured,scalar,count,l,t,r,b,now.width,now.height);
        if(selected<0){flush();return false;}
        if(pending.size()==MAX_TEXTS||count>MAX_GLYPHS-glyphCount||captured.length>MAX_FONT_BYTES-fontBytes||selected>MAX_WORK-work){flush();now=owner.frame();}
        frame=now;pending.add(new Text(captured,scalar,l,t,r,b,replay));glyphCount+=count;fontBytes+=captured.length;work+=selected;return true;
    }
    private boolean same(WorldOverlayCoverage.Frame now){return frame.ownerPixels==now.ownerPixels&&frame.transmittance==now.transmittance&&frame.width==now.width&&frame.height==now.height&&frame.generation==now.generation&&frame.frameSequence==now.frameSequence;}
    private void clear(){pending.clear();frame=null;glyphCount=fontBytes=work=0;}
    private void failed(String reason){add(7,1);clear();owner.poison(reason);}
    public void flush(){
        if(pending.isEmpty())return;add(0,1);
        if(frame==null||!owner.current(frame)){failed("Segmented TEXT lost live C/T owner before flush");return;}
        final int result;
        try{
            int[] header={1,frame.width,frame.height,pending.size(),glyphCount,fontBytes,work};
            int[] texts=new int[pending.size()*8],plots=new int[glyphCount*5];byte[] fonts=new byte[fontBytes];int n=0,fo=0,go=0;
            for(Text text:pending){int at=n++*8;texts[at]=fo;texts[at+1]=text.font.length;texts[at+2]=go;texts[at+3]=text.plots.length/5;texts[at+4]=text.l;texts[at+5]=text.t;texts[at+6]=text.r;texts[at+7]=text.b;
                System.arraycopy(text.font,0,fonts,fo,text.font.length);System.arraycopy(text.plots,0,plots,go*5,text.plots.length);fo+=text.font.length;go+=text.plots.length/5;}
            add(2,1);result=installed.raster(header,texts,plots,fonts,frame.ownerPixels,frame.transmittance,frame.generation,frame.frameSequence);
        }catch(Throwable error){failed("Segmented TEXT callback/preparation failed: "+error);return;}
        if(result==0){add(5,1);for(Text text:pending){text.replay.plots(text.font,text.plots,text.l,text.t,text.r,text.b);add(6,1);}clear();return;}
        if((result!=1&&result!=2)||!owner.current(frame)){failed("Segmented TEXT nontransactional failure/status/owner");return;}
        add(1,1);add(3,pending.size());add(4,glyphCount);owner.committed(frame,result==2);clear();
    }
}
