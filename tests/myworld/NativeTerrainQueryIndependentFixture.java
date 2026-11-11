package orsc;

import com.openrsc.client.model.Tile;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Independent ordered-long-bounds reference; no candidate lookup/contains code reused. */
public final class NativeTerrainQueryIndependentFixture {
    private static final String SHA="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PAYLOAD="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static int checks,queries;
    private static boolean diagnostics;
    private static final MessageDigest DIGEST;
    static {try{DIGEST=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new AssertionError(e);}}
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void add(int v){DIGEST.update(ByteBuffer.allocate(4).putInt(v).array());}
    private static void add(String v){try{DIGEST.update(v.getBytes("UTF-8"));DIGEST.update((byte)0);}catch(Exception e){throw new AssertionError(e);}}
    private static long floor(long n,long d){long q=n/d;return n<0&&n%d!=0?q-1:q;}
    private static final class Spec {
        final int size,x,y,wire;final long minX,minY;final byte[] expected;final NativeLayeredTerrainChunk chunk;
        Spec(int size,int x,int y,boolean available,boolean wide,int seed,String encoding){
            this.size=size;this.x=x;this.y=y;wire=wide?11:10;minX=(long)x*size;minY=(long)y*size;
            expected=new byte[size*size*wire];new Random(seed).nextBytes(expected);
            byte[] input=expected.clone();
            chunk=available?NativeLayeredTerrainChunk.available(size,x,y,(int)floor(minX,48),(int)floor(minY,48),encoding,PAYLOAD,input):NativeLayeredTerrainChunk.voidChunk(size,x,y);
            Arrays.fill(input,(byte)0); // Construction must own its source bytes.
        }
        Spec(Spec source,byte[] replacement){size=source.size;x=source.x;y=source.y;wire=source.wire;minX=source.minX;minY=source.minY;expected=replacement.clone();chunk=source.chunk;}
        boolean contains(int wx,int wy){return wx>=minX&&wx<=minX+size-1&&wy>=minY&&wy<=minY+size-1;}
        int[] values(int wx,int wy){
            int offset=(int)(((wx-minX)*size+(wy-minY))*wire),at=offset;
            int elevation=wire==11?((expected[at++]&255)<<8)|(expected[at++]&255):expected[at++]&255;
            int[] result={elevation,expected[at++],expected[at++],expected[at++],expected[at++],expected[at++],0};
            result[6]=((expected[at++]&255)<<24)|((expected[at++]&255)<<16)|((expected[at++]&255)<<8)|(expected[at]&255);return result;
        }
    }
    private static int[] values(Tile t){return new int[]{t.groundElevation,t.groundTexture,t.groundOverlay,t.roofTexture,t.verticalWall,t.horizontalWall,t.diagonalWalls};}
    private static String error(Runnable action){try{action.run();return "NO_EXCEPTION";}catch(RuntimeException e){return e.getClass().getName()+":"+e.getMessage();}}
    private static void query(NativeLayeredTerrainSnapshot s,Spec[] specs,int x,int y){
        Spec found=null;for(Spec spec:specs)if(spec.chunk.isAvailable()&&spec.contains(x,y)){found=spec;break;}
        boolean covered=found!=null;check(s.covers("global",s.getLevel(),x,y)==covered,"ordered reference coverage at"+x+","+y);
        check(!s.covers("elsewhere",s.getLevel(),x,y)&&!s.covers(null,s.getLevel(),x,y)&&!s.covers("global",s.getLevel()+1,x,y),"scope identity");
        add(x);add(y);add(covered?1:0);
        if(covered){int[] want=found.values(x,y);Tile actual=s.createTile(x,y);check(Arrays.equals(values(actual),want),"first available tile values");
            check(s.getGroundElevation(x,y)==want[0]&&s.getGroundOverlay(x,y)==(want[2]&255),"scalar getters");for(int v:values(actual))add(v);
            actual.groundElevation=-999;check(s.createTile(x,y).groundElevation==want[0],"returned Tile not retained mutable storage");
        }else{
            String a=error(()->s.createTile(x,y)),b=error(()->s.getGroundElevation(x,y)),c=error(()->s.getGroundOverlay(x,y));
            check(a.startsWith("java.lang.IllegalArgumentException:")&&a.equals(b)&&b.equals(c),"missing query exception semantics");add(a);add(b);add(c);
        }queries++;
    }
    private static Spec[] roster(int protocol,int centerX,int centerY,int variant){
        int presentation=protocol==4?24:48,radius=protocol>=9?2:1,width=radius*2+1;Spec[] result=new Spec[width*width];int n=0;
        for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++){
            int size=variant<2?presentation:variant==2?24:new int[]{1,2,3,4,6,8,12,16,24,48}[n%10];
            boolean available=variant==0||n%3!=0;boolean wide=(n&1)==0;
            result[n]=new Spec(size,centerX+dx,centerY+dy,available,wide,1000+variant*99+n,wide?NativeLayeredTerrainChunk.RAW_ENCODING_V2:NativeLayeredTerrainChunk.RAW_ENCODING);n++;
        }return result;
    }
    private static NativeLayeredTerrainSnapshot snapshot(int protocol,int centerX,int centerY,int level,Spec[] specs){
        NativeLayeredTerrainChunk[] input=new NativeLayeredTerrainChunk[specs.length];for(int n=0;n<input.length;n++)input[n]=specs[n].chunk;
        NativeLayeredTerrainSnapshot result=new NativeLayeredTerrainSnapshot(protocol,"independent","1.0.0",SHA,protocol==4?24:48,"global",level,centerX,centerY,protocol>=9?2:1,input);
        Arrays.fill(input,null);metadata(result,specs);return result;
    }
    private static void metadata(NativeLayeredTerrainSnapshot s,Spec[] specs){
        if(!diagnostics)return;
        try{
            boolean expected=specs.length>0;for(Spec spec:specs){expected&=spec.size==s.getPresentationChunkSize();check(((Integer)NativeLayeredTerrainChunk.class.getMethod("getSize").invoke(spec.chunk)).intValue()==spec.size,"chunk size diagnostic");}
            check(((Boolean)NativeLayeredTerrainSnapshot.class.getMethod("isTerrainQueryIndexEligible").invoke(s)).booleanValue()==expected,"eligibility includes EVERY void/available slot");
            check(((Integer)NativeLayeredTerrainSnapshot.class.getMethod("getTerrainQueryIndexDimension").invoke(s)).intValue()==(specs.length==0?0:s.getChunkRadius()*2+1),"index dimensions");
            check(((Boolean)NativeLayeredTerrainSnapshot.class.getMethod("isBrowserTerrainQueryIndexEnabled").invoke(null)).booleanValue()==Boolean.getBoolean("spoiledmilk.browserTerrainQueryIndex"),"private startup policy");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void windows(){
        for(int protocol=4;protocol<=10;protocol++)for(int[] center:centers(protocol))for(int variant=0;variant<4;variant++){
            Spec[] specs=roster(protocol,center[0],center[1],variant);NativeLayeredTerrainSnapshot s=snapshot(protocol,center[0],center[1],-7,specs);
            add(s.scopeIdentity());add(s.summary());add(s.packageIdentity());check(s.getProtocolVersion()==protocol&&s.getLevel()==-7&&s.getWorldSpace().equals("global"),"metadata identity");
            int available=0;for(Spec spec:specs){if(spec.chunk.isAvailable())available++;check(s.isChunkAvailable(spec.x,spec.y)==spec.chunk.isAvailable(),"slot availability");}
            check(s.getAvailableChunkCount()==available&&s.getChunkSlotCount()==specs.length,"roster counts");
            List<Integer> xs=new ArrayList<Integer>(),ys=new ArrayList<Integer>();
            for(Spec spec:specs){xs.add((int)spec.minX-1);xs.add((int)spec.minX);xs.add((int)(spec.minX+spec.size-1));xs.add((int)(spec.minX+spec.size));ys.add((int)spec.minY-1);ys.add((int)spec.minY);ys.add((int)(spec.minY+spec.size-1));ys.add((int)(spec.minY+spec.size));}
            for(int i=0;i<xs.size();i++)for(int j=0;j<ys.size();j+=Math.max(1,ys.size()/12))query(s,specs,xs.get(i),ys.get(j));
            Random random=new Random(protocol*37+variant);for(int n=0;n<200;n++)query(s,specs,random.nextInt(500)-250,random.nextInt(500)-250);
            if(protocol>=9){NativeLayeredTerrainSnapshot inner=s.toAtomicActivationInnerWindow();Spec[] inside=new Spec[9];int n=0;for(int x=1;x<=3;x++)for(int y=1;y<=3;y++)inside[n++]=specs[x*5+y];
                add(inner.scopeIdentity());for(int i=0;i<inside.length;i++)query(inner,inside,(int)inside[i].minX,(int)inside[i].minY);
            }
        }
    }
    private static int[][] centers(int protocol){int size=protocol==4?24:48,radius=protocol>=9?2:1;
        int min=(int)-floor(-(long)Integer.MIN_VALUE,size)+radius,max=(int)floor((long)Integer.MAX_VALUE-size+1,size)-radius;
        return new int[][]{{0,0},{-1,-1},{2,-3},{min,max},{max,min}};
    }
    private static void extremeChunks(){
        for(int size:new int[]{1,2,3,4,6,8,12,16,24,48}){
            int min=(int)(-floor(-(long)Integer.MIN_VALUE,size)),max=(int)floor((long)Integer.MAX_VALUE-size+1,size);
            for(int cx:new int[]{min,-1,0,max})for(int cy:new int[]{min,-1,0,max})for(boolean available:new boolean[]{false,true}){
                Spec spec=new Spec(size,cx,cy,available,true,size+cx,NativeLayeredTerrainChunk.RAW_ENCODING_V2);
                for(long x:new long[]{Integer.MIN_VALUE,spec.minX-1,spec.minX,spec.minX+size-1,spec.minX+size,Integer.MAX_VALUE})for(long y:new long[]{Integer.MIN_VALUE,spec.minY-1,spec.minY,spec.minY+size-1,spec.minY+size,Integer.MAX_VALUE}){
                    if(x<Integer.MIN_VALUE||x>Integer.MAX_VALUE||y<Integer.MIN_VALUE||y>Integer.MAX_VALUE)continue;
                    check(spec.chunk.covers((int)x,(int)y)==spec.contains((int)x,(int)y),"extreme inclusive bounds");add(spec.chunk.covers((int)x,(int)y)?1:0);
                    if(available&&spec.contains((int)x,(int)y)){check(Arrays.equals(values(spec.chunk.createTile((int)x,(int)y)),spec.values((int)x,(int)y)),"extreme tile");add(spec.chunk.groundElevation((int)x,(int)y));add(spec.chunk.groundOverlay((int)x,(int)y));}
                    else add(error(()->spec.chunk.createTile((int)x,(int)y)));
                }
            }
            final int s=size;add(error(()->NativeLayeredTerrainChunk.voidChunk(s,Integer.MIN_VALUE,Integer.MAX_VALUE)));
        }
    }
    private static void uniform(){
        for(int[] center:new int[][]{{0,0},{-1,-2},{123,-42}})for(int size:new int[]{1,16,24,48}){
            NativeLayeredTerrainSnapshot s=new NativeLayeredTerrainSnapshot("independent","1.0.0",SHA,size,"global",-10,center[0],center[1],NativeLayeredTerrainSnapshot.UNIFORM_ENCODING,PAYLOAD,254,255,128,129,130,131,0x89abcdef);
            metadata(s,new Spec[0]);
            long minX=(long)center[0]*48,minY=(long)center[1]*48;
            for(int dx:new int[]{-1,0,1,47,48})for(int dy:new int[]{-1,0,1,47,48}){
                int x=(int)minX+dx,y=(int)minY+dy;boolean want=dx>=0&&dx<48&&dy>=0&&dy<48;check(s.covers("global",-10,x,y)==want,"uniform coverage");
                if(want){check(s.getGroundElevation(x,y)==254&&s.getGroundOverlay(x,y)==128,"uniform scalars");for(int v:values(s.createTile(x,y)))add(v);}
                else {add(error(()->s.createTile(x,y)));add(error(()->s.getGroundElevation(x,y)));add(error(()->s.getGroundOverlay(x,y)));}
            }add(s.scopeIdentity());add(s.summary());
        }
    }
    private static void merges(){
        for(boolean wide:new boolean[]{false,true}){
            Spec[] visual=new Spec[25],structural=new Spec[25],expected=new Spec[25];int n=0;
            for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++){
                boolean inner=Math.max(Math.abs(dx),Math.abs(dy))<2,available=n%7!=0;
                visual[n]=new Spec(48,dx-1,dy-1,available,wide,n+700,wide?NativeLayeredTerrainChunk.VISUAL_ENCODING_V2:NativeLayeredTerrainChunk.VISUAL_ENCODING);
                structural[n]=new Spec(48,dx-1,dy-1,available&&!inner,wide,n+900,wide?NativeLayeredTerrainChunk.STRUCTURAL_ENCODING_V2:NativeLayeredTerrainChunk.STRUCTURAL_ENCODING);
                byte[] result=visual[n].expected.clone();int visualBytes=wide?4:3;
                if(!inner)for(int at=0;at<result.length;at+=visual[n].wire)System.arraycopy(structural[n].expected,at+visualBytes,result,at+visualBytes,7);
                expected[n]=new Spec(visual[n],result);n++;
            }
            NativeLayeredTerrainSnapshot v=snapshot(9,-1,-1,2,visual),s=snapshot(10,-1,-1,2,structural),merged=NativeLayeredTerrainSnapshot.mergePresentation(v,s);
            add(merged.scopeIdentity());for(Spec spec:expected){query(merged,expected,(int)spec.minX,(int)spec.minY);query(merged,expected,(int)spec.minX+47,(int)spec.minY+47);}
            check(v.getProtocolVersion()==9&&s.getProtocolVersion()==10,"merge inputs immutable");
            add(error(()->NativeLayeredTerrainSnapshot.mergePresentation(s,v)));
            add(error(()->NativeLayeredTerrainSnapshot.mergePresentation(v,null)));
        }
    }
    private static void constructionFailures(){
        Spec[] specs=roster(5,0,0,0);NativeLayeredTerrainChunk[] valid=new NativeLayeredTerrainChunk[9];for(int n=0;n<9;n++)valid[n]=specs[n].chunk;
        NativeLayeredTerrainChunk[][] rows={null,new NativeLayeredTerrainChunk[0],valid.clone(),valid.clone()};rows[2][3]=null;rows[3][3]=valid[0];
        for(NativeLayeredTerrainChunk[] input:rows)add(error(()->new NativeLayeredTerrainSnapshot(5,"independent","1.0.0",SHA,48,"global",-1,0,0,1,input)));
        for(int protocol:new int[]{0,3,11})add(error(()->new NativeLayeredTerrainSnapshot(protocol,"independent","1.0.0",SHA,48,"global",-1,0,0,1,valid)));
        for(int size:new int[]{-1,0,5,49})add(error(()->NativeLayeredTerrainChunk.voidChunk(size,0,0)));
        for(byte[] data:new byte[][]{null,new byte[10],new byte[48*48*10-1]})add(error(()->NativeLayeredTerrainChunk.available(48,0,0,0,0,NativeLayeredTerrainChunk.RAW_ENCODING,PAYLOAD,data)));
        add(error(()->NativeLayeredTerrainChunk.available(48,0,0,1,0,NativeLayeredTerrainChunk.RAW_ENCODING,PAYLOAD,new byte[48*48*10])));
        add(error(()->NativeLayeredTerrainChunk.available(48,0,0,0,0,"wrong",PAYLOAD,new byte[48*48*10])));
        add(error(()->NativeLayeredTerrainChunk.available(48,0,0,0,0,NativeLayeredTerrainChunk.RAW_ENCODING,"bad",new byte[48*48*10])));
    }
    public static void main(String[] args){diagnostics=args.length>0;windows();extremeChunks();uniform();merges();constructionFailures();StringBuilder text=new StringBuilder();for(byte b:DIGEST.digest())text.append(String.format("%02x",b&255));System.out.println("SNAPSHOT "+text);System.err.println("PASS independent ordered terrain reference "+checks+" checks; queries="+queries+"; CPU only");}
}
