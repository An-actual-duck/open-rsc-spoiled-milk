package orsc.graphics.two;

import java.util.Arrays;

/** Bounded writer coverage only. Never reads, packs or compares RGB/T pixels. */
final class WorldOverlayTileTracker {
    static final int TILE_WIDTH=32,TILE_HEIGHT=16,MAX_TILES=16384;
    private boolean[] touched;
    private int width,height,columns,rows,lastStart=-1,lastEnd,misses,spanGroups;
    boolean begin(int width,int height) {
        this.width=width;this.height=height;lastStart=-1;misses=spanGroups=0;
        if(width<1||height<1||width>32768||height>32768||(long)width*height>WorldOverlayCoverage.MAX_PIXELS){touched=null;return false;}
        columns=(width+31)/32;rows=(height+15)/16;
        if(width<1||height<1||(long)columns*rows>MAX_TILES){touched=null;return false;}
        int count=columns*rows;
        if(touched==null||touched.length!=count)touched=new boolean[count];else Arrays.fill(touched,false);
        return true;
    }
    boolean mark(int index) {
        if(touched==null)return true;
        if(index<0||index>=width*height)return false;
        if(lastStart>=0&&index>=lastStart&&index<lastEnd)return true;
        if(misses<Integer.MAX_VALUE)misses++;
        int y=index/width,x=index-y*width,tx=x>>>5;
        touched[(y>>>4)*columns+tx]=true;
        // Exact ONE-row interval: a tile's multirow linear bounds include holes.
        lastStart=y*width+(tx<<5);lastEnd=Math.min((y+1)*width,lastStart+32);
        return true;
    }
    boolean span(int start,int end) {
        if(touched==null||end<=start)return true;
        if(start<0||end>width*height)return false;
        int first=start/width,last=(end-1)/width;
        if(first==last)markRow(first,start-first*width,end-first*width);
        else {
            markRow(first,start-first*width,width);markRow(last,0,end-last*width);
            // A linear span includes every column of any intervening row.
            if(last>first+1)for(int ty=(first+1)>>>4;ty<=((last-1)>>>4);ty++)markTiles(ty,0,columns-1);
        }
        lastStart=-1;return true;
    }
    private void markRow(int y,int lo,int hi){markTiles(y>>>4,lo>>>5,(hi-1)>>>5);}
    private void markTiles(int ty,int lo,int hi){
        if(spanGroups<Integer.MAX_VALUE)spanGroups++;
        int base=ty*columns;for(int tx=lo;tx<=hi;tx++)touched[base+tx]=true;
    }
    boolean[] snapshot(){return touched==null?null:touched.clone();}
    int[] observe(boolean[] previous) {
        if(touched==null)return null;
        int current=0,currentArea=0,union=0,unionArea=0;
        int[] output=new int[9+(touched.length+15)/16];
        output[0]=1; // Metadata scheme1: observed writer tiles, ROW transport remains active.
        output[5]=misses;output[6]=spanGroups;output[7]=columns;output[8]=rows;
        for(int id=0;id<touched.length;id++) {
            int tx=id%columns,ty=id/columns;
            int area=Math.min(32,width-tx*32)*Math.min(16,height-ty*16);
            if(touched[id]){current++;currentArea+=area;}
            if(touched[id]||previous!=null&&previous[id]){
                union++;unionArea+=area;output[9+(id>>>4)]|=1<<(id&15);
            }
        }
        output[1]=current;output[2]=currentArea;output[3]=union;output[4]=unionArea;
        return output;
    }
}
