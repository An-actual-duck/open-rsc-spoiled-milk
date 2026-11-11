package orsc.graphics.two;

import java.util.Arrays;

/** Bounded writer coverage only. Never reads, packs or compares RGB/T pixels. */
final class WorldOverlayTileTracker {
    static final int TILE_WIDTH=32,TILE_HEIGHT=16,MAX_TILES=16384,MAX_RECTANGLES=8;
    private static final int[][] COARSEN={{1,1},{2,2},{4,2},{8,2}};
    private boolean[] touched;
    private int width,height,columns,rows,lastStart=-1,lastEnd,misses,spanGroups;
    private int lastRow=-1,rowStart,rowEnd,pixelCount;
    boolean begin(int width,int height) {
        this.width=width;this.height=height;lastStart=lastRow=-1;misses=spanGroups=0;
        if(width<1||height<1||width>32768||height>32768||(long)width*height>WorldOverlayCoverage.MAX_PIXELS){touched=null;return false;}
        columns=(width+31)/32;rows=(height+15)/16;
        if(width<1||height<1||(long)columns*rows>MAX_TILES){touched=null;return false;}
        int count=columns*rows;
        pixelCount=width*height;
        if(touched==null||touched.length!=count)touched=new boolean[count];else Arrays.fill(touched,false);
        return true;
    }
    boolean mark(int index) {
        if(touched==null)return true;
        if(index<0||index>=pixelCount)return false;
        if(lastStart>=0&&index>=lastStart&&index<lastEnd)return true;
        if(misses<Integer.MAX_VALUE)misses++;
        int y;
        if(lastRow>=0&&index>=rowStart&&index<rowEnd)y=lastRow;
        else if(lastRow>=0&&index>=rowEnd&&index-rowEnd<width){y=lastRow+1;rowStart=rowEnd;rowEnd+=width;}
        else{y=index/width;rowStart=y*width;rowEnd=rowStart+width;}
        lastRow=y;
        int x=index-rowStart,tx=x>>>5;
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
        lastStart=lastRow=-1;return true;
    }
    private void markRow(int y,int lo,int hi){markTiles(y>>>4,lo>>>5,(hi-1)>>>5);}
    private void markTiles(int ty,int lo,int hi){
        if(spanGroups<Integer.MAX_VALUE)spanGroups++;
        int base=ty*columns;for(int tx=lo;tx<=hi;tx++)touched[base+tx]=true;
    }
    boolean[] snapshot(){return touched==null?null:touched.clone();}
    boolean supported(){return touched!=null;}
    int deriveRows(boolean[] rowBlocks){
        Arrays.fill(rowBlocks,false);int marked=0;
        if(touched!=null)for(int y=0;y<rows;y++)for(int x=0;x<columns;x++)if(touched[y*columns+x]){rowBlocks[y]=true;marked++;break;}
        return marked;
    }

    /** At most eight disjoint rectangles; fixed coarsening replaces expensive merge search. */
    int[] rectangles(boolean[] previous,int rowUploadArea) {
        if(touched==null||rowUploadArea==0)return null;
        for(int[] scale:COARSEN) {
            int[] result=rectanglesAtScale(previous,scale[0],scale[1]);
            if(result==null)continue;
            int area=0;for(int n=0;n<result.length;n+=4)area+=result[n+2]*result[n+3];
            // Require useful transfer savings before replacing the cheap row fallback.
            if((long)area*5<=(long)width*height*3&&(long)area*5<=(long)rowUploadArea*4)return result;
        }
        return null;
    }
    private int[] rectanglesAtScale(boolean[] previous,int scaleX,int scaleY) {
        int cw=32*scaleX,ch=16*scaleY,cols=(width+cw-1)/cw,count=0;
        int[] out=new int[MAX_RECTANGLES*4];
        for(int y=0;y<(height+ch-1)/ch;y++) {
            int start=-1;
            for(int x=0;x<=cols;x++) {
                boolean selected=false;
                if(x<cols)for(int fy=y*scaleY;fy<Math.min(rows,(y+1)*scaleY)&&!selected;fy++)
                    for(int fx=x*scaleX;fx<Math.min(columns,(x+1)*scaleX);fx++){
                        int id=fy*columns+fx;if(touched[id]||previous!=null&&previous[id]){selected=true;break;}
                    }
                if(selected){if(start<0)start=x;continue;}
                if(start<0)continue;
                int left=start*cw,right=Math.min(width,x*cw),top=y*ch,bottom=Math.min(height,top+ch),match=-1;
                for(int n=0;n<count;n++)if(out[n*4]==left&&out[n*4+2]==right-left&&out[n*4+1]+out[n*4+3]==top){match=n;break;}
                if(match<0){if(count==MAX_RECTANGLES)return null;match=count++;out[match*4]=left;out[match*4+1]=top;out[match*4+2]=right-left;}
                out[match*4+3]=bottom-out[match*4+1];start=-1;
            }
        }
        return Arrays.copyOf(out,count*4);
    }
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
