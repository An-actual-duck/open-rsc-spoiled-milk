package orsc.graphics.two;

import java.util.Arrays;

/** Render-thread-owned transport metadata only. Literal RGB/T planes and clears
 * remain in WorldOverlayCoverage. No pixel comparison, packing or copied plane. */
final class WorldOverlayDeltaTransport {
    static final int ROWS_PER_BLOCK=16, MAX_ROWS=32768, MAX_BANDS=4;
    private int[] currentPixels, acknowledgedPixels;
    private int width,height,acknowledgedWidth,acknowledgedHeight;
    private long generation,sequence,acknowledgedGeneration=-1,acknowledgedSequence=-1;
    private boolean[] touched,acknowledgedTouched;
    private boolean active;
    private int blockPixels,lastBlock=-1,lastStart,lastEnd,writeMarks,spanMarks;
    private Plan pending;

    void beginFrame() { active=false;pending=null;lastBlock=-1; }
    void beginFresh(int[] pixels,int width,int height,long generation,long sequence) {
        active=false;pending=null;lastBlock=-1;writeMarks=spanMarks=0;
        if(width<1||height<1||height>MAX_ROWS||(long)width*height>WorldOverlayCoverage.MAX_PIXELS){invalidate();return;}
        if(pixels!=acknowledgedPixels||width!=acknowledgedWidth||height!=acknowledgedHeight)clearHistory();
        currentPixels=pixels;this.width=width;this.height=height;this.generation=generation;this.sequence=sequence;
        int blocks=(height+ROWS_PER_BLOCK-1)/ROWS_PER_BLOCK;
        if(touched==null||touched.length!=blocks)touched=new boolean[blocks];else Arrays.fill(touched,false);
        blockPixels=width*ROWS_PER_BLOCK;active=true;
    }
    private void clearHistory(){acknowledgedPixels=null;acknowledgedTouched=null;acknowledgedWidth=acknowledgedHeight=0;acknowledgedGeneration=acknowledgedSequence=-1;}
    void forgetAcknowledgedFrame(){clearHistory();pending=null;}
    void invalidate(){clearHistory();active=false;pending=null;lastBlock=-1;currentPixels=null;}
    private boolean beforeWrite(){if(!active)return false;if(pending!=null){invalidate();return false;}return true;}
    void mark(int index) {
        if(!beforeWrite())return;
        if(lastBlock>=0&&index>=lastStart&&index<lastEnd)return;
        if(index<0||index>=width*height){invalidate();return;}
        int block=index/blockPixels;
        if(!touched[block]){touched[block]=true;writeMarks++;}
        lastBlock=block;lastStart=block*blockPixels;lastEnd=lastStart+blockPixels;
    }
    void markSpan(int start,int end) {
        if(!beforeWrite()||end<=start)return;
        if(spanMarks<Integer.MAX_VALUE)spanMarks++;
        if(start<0||end>width*height){invalidate();return;}
        int first=start/blockPixels,last=(end-1)/blockPixels;
        for(int block=first;block<=last;block++)if(!touched[block]){touched[block]=true;writeMarks++;}
        lastBlock=last;lastStart=last*blockPixels;lastEnd=lastStart+blockPixels;
    }
    Plan prepare(int[] pixels,int width,int height,long generation,long sequence,boolean freshWorld) {
        if(!active||!freshWorld||pixels!=currentPixels||width!=this.width||height!=this.height
            ||generation!=this.generation||sequence!=this.sequence){invalidate();return null;}
        if(generation<0||sequence<0||generation>9007199254740991L||sequence>9007199254740991L){invalidate();return null;}
        boolean history=pixels==acknowledgedPixels&&width==acknowledgedWidth&&height==acknowledgedHeight
            &&acknowledgedTouched!=null&&generation>acknowledgedGeneration&&sequence>acknowledgedSequence;
        int[] bands=new int[MAX_BANDS*2];int count=0,rows=0,touchedRows=0;boolean open=false,tooMany=false;
        for(int block=0;block<touched.length;block++){
            int start=block*ROWS_PER_BLOCK,end=Math.min(height,start+ROWS_PER_BLOCK);
            if(touched[block])touchedRows+=end-start;
            boolean selected=touched[block]||history&&acknowledgedTouched[block];
            if(selected){rows+=end-start;if(!open){if(count==MAX_BANDS){tooMany=true;break;}bands[count*2]=start;count++;open=true;}bands[count*2-1]=end;}
            else open=false;
        }
        // Fragmentation is a full fallback. Recompute bounded current dirty-row
        // count only when the union scan stopped early; no pixel plane is read.
        if(tooMany){touchedRows=0;for(int block=0;block<touched.length;block++)if(touched[block])touchedRows+=Math.min(ROWS_PER_BLOCK,height-block*ROWS_PER_BLOCK);}
        boolean partial=history&&!tooMany&&(long)rows*5<=(long)height*3;
        if(!partial){bands=new int[]{0,height};rows=height;count=1;}else bands=Arrays.copyOf(bands,count*2);
        pending=new Plan(this,width,height,generation,sequence,partial,
            partial?acknowledgedGeneration:-1,partial?acknowledgedSequence:-1,bands,touched.clone(),
            new int[]{writeMarks,spanMarks,touchedRows,rows,count,partial?1:0});
        return pending;
    }
    void acknowledge(Plan plan,int[] pixels,int width,int height,long generation,long sequence) {
        if(plan==null||plan.owner!=this||plan!=pending||!active||pixels!=currentPixels
            ||width!=this.width||height!=this.height||generation!=this.generation||sequence!=this.sequence
            ||generation!=plan.generation||sequence!=plan.sequence){invalidate();throw new IllegalStateException("Stale/out-of-order overlay transport acknowledgement");}
        acknowledgedPixels=pixels;acknowledgedWidth=width;acknowledgedHeight=height;
        acknowledgedGeneration=generation;acknowledgedSequence=sequence;acknowledgedTouched=plan.touched;
        pending=null;
    }
    static final class Plan {
        final WorldOverlayDeltaTransport owner;final int width,height;
        final long generation,sequence,baseGeneration,baseSequence;final boolean partial;
        final int[] bands,work;final boolean[] touched;
        Plan(WorldOverlayDeltaTransport owner,int width,int height,long generation,long sequence,
            boolean partial,long baseGeneration,long baseSequence,int[] bands,boolean[] touched,int[] work){
            this.owner=owner;this.width=width;this.height=height;this.generation=generation;this.sequence=sequence;
            this.partial=partial;this.baseGeneration=baseGeneration;this.baseSequence=baseSequence;this.bands=bands;this.touched=touched;this.work=work;
        }
    }
}
