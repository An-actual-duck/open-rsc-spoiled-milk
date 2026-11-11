package orsc.graphics.two;

import java.util.ArrayList;

/** Test-owned literal glyph reference over arbitrary prior C/T, not a UI rerender. */
final class SegmentedTextPacketReference {
    private static final class Glyph {
        int source,destination,width,height,sourceStep,destinationStep,color,aa;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static int raster(int[] header,int[] texts,int[] plots,byte[] fonts,int[] c,float[] t,double generation,double sequence){
        require((header.length==7||header.length==11)&&header[0]==1,"native header");int width=header[1],height=header[2],count=header[3];
        if(header.length==11)require(header[7]>0&&header[8]>0&&header[9]==1&&header[10]==1,"native active owner/segment/mode");
        require(width>0&&height>0&&(long)width*height==c.length&&c.length==t.length,"native owner shape");
        require(count>=0&&count<=256&&texts.length==count*8&&header[4]>=0&&header[4]<=4096&&plots.length==header[4]*5&&fonts.length==header[5],"native bounded packet shape");
        require(generation>=0&&sequence>=0&&generation==Math.rint(generation)&&sequence==Math.rint(sequence),"native epochs");
        ArrayList<Glyph> ready=new ArrayList<Glyph>();int nextFont=0,nextGlyph=0,work=0;
        for(int n=0;n<count;n++){
            int at=n*8,base=texts[at],length=texts[at+1],first=texts[at+2],glyphs=texts[at+3],left=texts[at+4],top=texts[at+5],right=texts[at+6],bottom=texts[at+7];
            require(base==nextFont&&length>=0&&base+length<=fonts.length&&first==nextGlyph&&glyphs>=0&&first+glyphs<=header[4],"native immutable source partition");
            require(left>=0&&top>=0&&right>=left&&bottom>=top&&right<=width&&bottom<=height,"native clip");nextFont+=length;nextGlyph+=glyphs;
            for(int i=first;i<first+glyphs;i++){
                int p=i*5,address=plots[p+3],aa=plots[p+4];require(address>=0&&address+6<length&&(aa==0||aa==1),"native font header");
                int originalWidth=fonts[base+address+3],gw=originalWidth,gh=fonts[base+address+4];
                int x=plots[p]+fonts[base+address+5],y=plots[p+1]-fonts[base+address+6],source=(fonts[base+address]<<14)+(fonts[base+address+1]<<7)+fonts[base+address+2];
                if(y<top){source+=(top-y)*originalWidth;gh-=top-y;y=top;}
                if(y+gh>=bottom)gh-=1+y+gh-bottom;
                if(x<left){source+=left-x;gw-=left-x;x=left;}
                if(x+gw>=right)gw-=1+x+gw-right;
                if(gw<=0||gh<=0)continue;
                require(source>=0&&originalWidth>=gw&&(long)source+(long)(gh-1)*originalWidth+gw<=length,"native source footprint");
                require(x>=0&&y>=0&&x+gw<=width&&y+gh<=height,"native destination footprint");work+=gw*gh;require(work<=2097152,"native bounded work");
                Glyph g=new Glyph();g.source=base+source;g.destination=x+y*width;g.width=gw;g.height=gh;g.sourceStep=originalWidth-gw;g.destinationStep=width-gw;g.color=plots[p+2];g.aa=aa;ready.add(g);
            }
        }
        require(nextFont==fonts.length&&nextGlyph==header[4]&&work==header[6],"native aggregate source/work");
        boolean wrote=false;
        for(Glyph g:ready){int src=g.source,dst=g.destination;
            for(int row=0;row<g.height;row++){
                for(int col=0;col<g.width;col++,dst++){
                    int alpha=fonts[src++]&255;
                    if(g.aa==0){if(alpha!=0){c[dst]=g.color;t[dst]=0f;wrote=true;}}
                    else if(alpha>30){
                        if(alpha>=230){c[dst]=g.color;t[dst]=0f;}
                        else{int inv=256-alpha,old=c[dst];c[dst]=(((g.color&0xff00ff)*alpha+(old&0xff00ff)*inv)&0xff00ff00)+((inv*(old&0xff00)+alpha*(g.color&0xff00))&0xff0000)>>8;t[dst]*=inv*(1f/256f);}
                        wrote=true;
                    }
                }src+=g.sourceStep;dst+=g.destinationStep;
            }
        }
        return wrote?2:1;
    }
}
