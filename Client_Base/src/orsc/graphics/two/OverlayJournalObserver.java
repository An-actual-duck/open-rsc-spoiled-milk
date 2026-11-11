package orsc.graphics.two;

import java.util.ArrayList;
import java.util.Arrays;

/** Private diagnostic journal. Never changes pixels, T, writer decisions or ACKs. */
public final class OverlayJournalObserver {
    public static final int TEXT=1,BOX=2,ALPHA_BOX=3,HLINE=4,VLINE=5,BARRIER=9;
    public static final int SPRITE=1,GRADIENT=2,CIRCLE=3,MINIMAP=4,CLEAR=5,READ=6,
        MODE=7,SURFACE=8,CLIP=9,LEGACY=10,EDITOR=11,AWT=12,SOURCE_ALIAS=13,
        UNKNOWN=14,OVERFLOW=15,EXCEPTION=16,RESET=17;
    static final int MAX_OPS=4096,MAX_GLYPHS=4096,MAX_TEXTS=256,MAX_FONT_BYTES=8*1024*1024,
        MAX_WORK=2*1024*1024,OP_STRIDE=16,RUN_STRIDE=6;
    private static final int[] lifetime=new int[32];
    private int[] ops,glyphs,runs;
    private final ArrayList<byte[]> fonts=new ArrayList<byte[]>();
    private int[] owner;private float[] transmittance;private int width,height,opCount,glyphCount,textCount,fontBytes,barriers,work,runCount;
    private int runStart,runText,runRect,runGlyph,runWork;private long generation,sequence;
    private boolean active,complete,software;
    private static void add(int at,int n){if(n>0)lifetime[at]=n>Integer.MAX_VALUE-lifetime[at]?Integer.MAX_VALUE:lifetime[at]+n;}
    public void softwareOwner(){close(MODE);software=true;}
    public void begin(WorldOverlayCoverage.Frame frame){
        close(RESET);software=false;
        if(frame==null||!frame.valid||!frame.transparent||frame.mode!=WorldOverlayCoverage.MODE_WORLD
            ||frame.width<1||frame.height<1||frame.width>32768||frame.height>32768
            ||(long)frame.width*frame.height>WorldOverlayCoverage.MAX_PIXELS)return;
        if(ops==null){ops=new int[MAX_OPS*OP_STRIDE];glyphs=new int[MAX_GLYPHS*5];runs=new int[(MAX_OPS+1)*RUN_STRIDE];}
        owner=frame.ownerPixels;transmittance=frame.transmittance;width=frame.width;height=frame.height;
        generation=frame.generation;sequence=frame.frameSequence;opCount=glyphCount=textCount=fontBytes=barriers=work=runCount=0;
        runStart=runText=runRect=runGlyph=runWork=0;complete=active=true;add(0,1);
    }
    private boolean matches(int[] pixels,WorldOverlayCoverage.Frame frame){return active&&pixels==owner&&frame!=null
        &&frame.ownerPixels==owner&&frame.transmittance==transmittance&&frame.width==width&&frame.height==height
        &&frame.generation==generation&&frame.frameSequence==sequence&&frame.valid&&frame.transparent
        &&frame.mode==WorldOverlayCoverage.MODE_WORLD;}
    private boolean target(int[] pixels,int plots){
        if(active&&pixels==owner)return true;
        add(software?2:4,1);add(software?3:5,plots);return false;
    }
    public void barrier(int[] pixels,int kind,int phase){
        if(!active||pixels!=owner){add(6,1);return;}barrier(kind,phase);
    }
    private void barrier(int kind,int phase){
        complete=false;closeRun();barriers++;add(7,1);if(kind>0&&kind<=17)add(8+kind,1);
        if(opCount<MAX_OPS){int at=opCount++*OP_STRIDE;Arrays.fill(ops,at,at+OP_STRIDE,0);ops[at]=BARRIER;ops[at+1]=opCount-1;ops[at+2]=kind;ops[at+10]=phase;}else add(26,1);
        runStart=opCount;
    }
    private boolean domain(int left,int top,int right,int bottom){return left>=0&&top>=0&&right>=left&&bottom>=top&&right<=width&&bottom<=height;}
    private boolean coordinate(int n){return n>=-32768&&n<=32768;}
    private int glyphWork(byte[] font,int[] input,int count,int left,int top,int right,int bottom){
        int total=0;
        for(int n=0;n<count;n++){
            int at=n*5,x=input[at],y=input[at+1],address=input[at+3],aa=input[at+4];
            if(!coordinate(x)||!coordinate(y)||address<0||address>font.length-7||(aa!=0&&aa!=1))return -1;
            int w=font[address+3],h=font[address+4],gx=x+font[address+5],gy=y-font[address+6];
            int src=(font[address]<<14)+(font[address+1]<<7)+font[address+2],stride=w;
            if(gy<top){int lost=top-gy;src+=lost*w;h-=lost;gy=top;}
            if(gy+h>=bottom)h-=1+gy+h-bottom;
            if(gx<left){int lost=left-gx;src+=lost;w-=lost;gx=left;}
            if(gx+w>=right)w-=1+gx+w-right;
            if(w<=0||h<=0)continue;
            if(src<0||stride<0||(long)src+(long)(h-1)*stride+w>font.length
                ||gx<0||gy<0||gx+w>width||gy+h>height)return -1;
            total+=w*h;if(total>MAX_WORK)return -1;
        }return total;
    }
    public void text(int[] pixels,WorldOverlayCoverage.Frame frame,byte[] font,int[] input,int count,
        int left,int top,int right,int bottom,int phase){
        if(!target(pixels,count))return;
        add(1,1);
        if(!matches(pixels,frame)||!domain(left,top,right,bottom)||font==null||input==null||count<0
            ||count>MAX_GLYPHS||input.length<count*5){barrier(UNKNOWN,phase);return;}
        if(opCount==MAX_OPS||textCount==MAX_TEXTS||glyphCount+count>MAX_GLYPHS
            ||font.length>MAX_FONT_BYTES-fontBytes){barrier(OVERFLOW,phase);return;}
        byte[] copy=font.clone(); // Snapshot AT this TEXT, never identity-memoized.
        int selected=glyphWork(copy,input,count,left,top,right,bottom);
        if(selected<0){barrier(UNKNOWN,phase);return;}
        if(selected>MAX_WORK-work){barrier(OVERFLOW,phase);return;}
        int at=opCount++*OP_STRIDE;Arrays.fill(ops,at,at+OP_STRIDE,0);
        ops[at]=TEXT;ops[at+1]=opCount-1;ops[at+2]=fontBytes;ops[at+3]=copy.length;
        ops[at+4]=glyphCount;ops[at+5]=count;ops[at+6]=left;ops[at+7]=top;ops[at+8]=right;ops[at+9]=bottom;ops[at+10]=phase;
        System.arraycopy(input,0,glyphs,glyphCount*5,count*5);glyphCount+=count;textCount++;fontBytes+=copy.length;fonts.add(copy);
        work+=selected;runText++;runGlyph+=count;runWork+=selected;
    }
    public void rectangle(int[] pixels,WorldOverlayCoverage.Frame frame,int kind,int x,int y,int w,int h,int color,int alpha,
        int left,int top,int right,int bottom,int phase,boolean interlace){
        if(!target(pixels,0))return;
        if(!matches(pixels,frame)||!domain(left,top,right,bottom)||interlace||!coordinate(x)||!coordinate(y)
            ||w<0||h<0||w>32768||h>32768||alpha<0||alpha>256||kind<BOX||kind>VLINE){barrier(UNKNOWN,phase);return;}
        int loX=Math.max(left,x),loY=Math.max(top,y),hiX=Math.min(right,x+w),hiY=Math.min(bottom,y+h);
        int selected=Math.max(0,hiX-loX)*Math.max(0,hiY-loY);
        if(opCount==MAX_OPS||selected>MAX_WORK-work){barrier(OVERFLOW,phase);return;}
        int at=opCount++*OP_STRIDE;Arrays.fill(ops,at,at+OP_STRIDE,0);ops[at]=kind;ops[at+1]=opCount-1;
        ops[at+2]=x;ops[at+3]=y;ops[at+4]=w;ops[at+5]=h;ops[at+6]=left;ops[at+7]=top;ops[at+8]=right;ops[at+9]=bottom;
        ops[at+10]=phase;ops[at+11]=color;ops[at+12]=alpha;work+=selected;runRect++;runWork+=selected;
    }
    private void closeRun(){
        if(runText+runRect>0){if(runCount<MAX_OPS+1){int at=runCount++*RUN_STRIDE;runs[at]=runStart;runs[at+1]=opCount;
            runs[at+2]=runText;runs[at+3]=runRect;runs[at+4]=runGlyph;runs[at+5]=runWork;}else{complete=false;add(26,1);}}
        runStart=opCount;runText=runRect=runGlyph=runWork=0;
    }
    public void close(int kind){
        if(active){barrier(kind,0);add(27,1);}active=false;owner=null;transmittance=null;fonts.clear();fontBytes=0;
    }
    public Snapshot finish(int[] pixels,WorldOverlayCoverage.Frame frame){
        if(!active)return null;
        if(!matches(pixels,frame))barrier(SURFACE,0);
        closeRun();byte[] data=new byte[fontBytes];int offset=0;for(byte[] font:fonts){System.arraycopy(font,0,data,offset,font.length);offset+=font.length;}
        int[] header={1,width,height,complete?1:0,opCount,glyphCount,textCount,fontBytes,barriers,work,runCount,WorldOverlayCoverage.MODE_WORLD,
            (int)(generation>>>32),(int)generation,(int)(sequence>>>32),(int)sequence};
        Snapshot out=new Snapshot(header,Arrays.copyOf(ops,opCount*OP_STRIDE),Arrays.copyOf(glyphs,glyphCount*5),data,
            Arrays.copyOf(runs,runCount*RUN_STRIDE),lifetime.clone());
        active=false;owner=null;transmittance=null;fonts.clear();fontBytes=0;return out;
    }
    int retainedFontCopies(){return fonts.size();}
    public static final class Snapshot {
        public final int[] header,ops,glyphs,runs,stats;public final byte[] fonts;
        Snapshot(int[] h,int[] o,int[] g,byte[] f,int[] r,int[] s){header=h;ops=o;glyphs=g;fonts=f;runs=r;stats=s;}
    }
}
