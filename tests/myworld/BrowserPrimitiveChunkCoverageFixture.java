package orsc;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import com.openrsc.client.model.Sector;
import orsc.graphics.three.*;

/** Independent frozen/current source corpus; no GPU, server, or per-frame counters. */
public final class BrowserPrimitiveChunkCoverageFixture {
    static final String SHA=String.join("",Collections.nCopies(64,"a"));
    static int checks;
    static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    static NativeLayeredTerrainSnapshot snapshot(int protocol,int level,int cx,int cy,boolean wide,boolean irregular,boolean holes,String space){
        int declared=protocol==NativeLayeredTerrainSnapshot.LEGACY_CHUNKED_PROTOCOL_VERSION?24:48;
        int radius=protocol==NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION||protocol==NativeLayeredTerrainSnapshot.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION?2:1;
        NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[(radius*2+1)*(radius*2+1)];int at=0;
        for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++){
            int size=irregular&&at%2==0?(declared==48?24:48):declared,x=cx+dx,y=cy+dy;
            if(holes&&(dx+dy)%3==0){chunks[at++]=NativeLayeredTerrainChunk.voidChunk(size,x,y);continue;}
            byte[] bytes=new byte[size*size*(wide?11:10)];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)(n*17+x*7+y*13+at*5);
            chunks[at++]=NativeLayeredTerrainChunk.available(size,x,y,Math.floorDiv(x*size,48),Math.floorDiv(y*size,48),wide?NativeLayeredTerrainChunk.RAW_ENCODING_V2:NativeLayeredTerrainChunk.RAW_ENCODING,SHA,bytes);Arrays.fill(bytes,(byte)3);
        }
        NativeLayeredTerrainSnapshot result=new NativeLayeredTerrainSnapshot(protocol,"fixture","1.0.0",SHA,declared,space,level,cx,cy,radius,chunks);
        Arrays.fill(chunks,null);return result;
    }
    static int height(NativeLayeredTerrainSnapshot s,int x,int y)throws Exception{
        try{return(Integer)s.getClass().getMethod("getGroundElevationOrUnavailable",int.class,int.class).invoke(s,x,y);}
        catch(NoSuchMethodException original){return s.covers(s.getWorldSpace(),s.getLevel(),x,y)?s.getGroundElevation(x,y):Integer.MIN_VALUE;}
        catch(InvocationTargetException e){throw(Exception)e.getCause();}
    }
    interface Read {int value()throws Exception;}
    static void record(DataOutputStream out,Read read)throws Exception{try{int value=read.value();out.writeBoolean(true);out.writeInt(value);}catch(RuntimeException error){out.writeBoolean(false);out.writeUTF(error.getClass().getName());out.writeUTF(String.valueOf(error.getMessage()));}}
    static World world(NativeLayeredTerrainSnapshot active,NativeLayeredTerrainSnapshot visual)throws Exception{
        World w=(World)BrowserResidentInputsFixture.unsafe().allocateInstance(World.class);BrowserResidentInputsFixture.set(w,"nativeLayeredTerrainSnapshot",active);BrowserResidentInputsFixture.set(w,"symmetricNativeLayeredTerrainVisualSnapshot",visual);
        BrowserResidentInputsFixture.set(w,"nativeLayeredTerrainAppliedSectionX",active==null?0:active.getCurrentChunkX());BrowserResidentInputsFixture.set(w,"nativeLayeredTerrainAppliedSectionY",active==null?0:active.getCurrentChunkY());
        Sector[] sectors=new Sector[9];for(int n=0;n<9;n++)sectors[n]=new Sector();BrowserResidentInputsFixture.set(w,"sectors",sectors);return w;
    }
    static void snapshotCorpus(DataOutputStream out)throws Exception{
        int[] protocols={NativeLayeredTerrainSnapshot.LEGACY_CHUNKED_PROTOCOL_VERSION,NativeLayeredTerrainSnapshot.PROTOCOL_VERSION,NativeLayeredTerrainSnapshot.RESIDENT_PROTOCOL_VERSION,NativeLayeredTerrainSnapshot.READINESS_PROTOCOL_VERSION,NativeLayeredTerrainSnapshot.ATOMIC_ACTIVATION_PROTOCOL_VERSION,NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,NativeLayeredTerrainSnapshot.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION};
        Random random=new Random(944821);
        for(int protocol:protocols)for(boolean wide:new boolean[]{false,true})for(boolean irregular:new boolean[]{false,true}){
            NativeLayeredTerrainSnapshot s=snapshot(protocol,-7,-3,4,wide,irregular,true,"global");
            for(int x=-300;x<=300;x+=7)for(int y=-260;y<=360;y+=11){final int px=x,py=y;record(out,()->height(s,px,py));}
            for(int n=0;n<100;n++){final int x=random.nextInt(),y=random.nextInt();record(out,()->height(s,x,y));}
        }
        // Regular grids at both largest accepted signed chunk centers.
        for(int size:new int[]{24,48}){
            int protocol=size==24?NativeLayeredTerrainSnapshot.LEGACY_CHUNKED_PROTOCOL_VERSION:NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,radius=size==24?1:2;
            int max=(int)Math.floorDiv((long)Integer.MAX_VALUE-size+1,size)-radius,min=(int)-Math.floorDiv(-(long)Integer.MIN_VALUE,size)+radius;
            for(int cx:new int[]{min,max})for(boolean wide:new boolean[]{false,true}){
                NativeLayeredTerrainSnapshot s=snapshot(protocol,-1,cx,cx,wide,false,false,"global");
                for(int x:new int[]{Integer.MIN_VALUE,Integer.MIN_VALUE+47,Integer.MAX_VALUE-47,Integer.MAX_VALUE})for(int y:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE})record(out,()->height(s,x,y));
            }
        }
        // A regular grid may legally mix legacy/wide encodings per chunk.
        NativeLayeredTerrainSnapshot narrow=snapshot(NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,0,0,0,false,false,false,"global"),wideGrid=snapshot(NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,0,0,0,true,false,false,"global");
        NativeLayeredTerrainChunk[] blended=((NativeLayeredTerrainChunk[])BrowserResidentInputsFixture.get(narrow,"chunks")).clone(),wideChunks=(NativeLayeredTerrainChunk[])BrowserResidentInputsFixture.get(wideGrid,"chunks");for(int n=0;n<blended.length;n+=3)blended[n]=wideChunks[n];
        NativeLayeredTerrainSnapshot encodings=new NativeLayeredTerrainSnapshot(NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,"fixture","1.0.0",SHA,48,"global",0,0,0,2,blended);
        for(int x=-100;x<150;x+=3)for(int y=-100;y<150;y+=5){final int px=x,py=y;record(out,()->height(encodings,px,py));}
        // Preserve constructor error classes/messages for rejected shapes too.
        for(NativeLayeredTerrainChunk[] invalid:new NativeLayeredTerrainChunk[][]{null,new NativeLayeredTerrainChunk[1],new NativeLayeredTerrainChunk[25]})record(out,()->new NativeLayeredTerrainSnapshot(NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,"fixture","1.0.0",SHA,48,"global",0,0,0,2,invalid).getChunkSlotCount());
        NativeLayeredTerrainChunk[] reversed=blended.clone();Collections.reverse(Arrays.asList(reversed));record(out,()->new NativeLayeredTerrainSnapshot(NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,"fixture","1.0.0",SHA,48,"global",0,0,0,2,reversed).getChunkSlotCount());
        NativeLayeredTerrainSnapshot uniform=new NativeLayeredTerrainSnapshot("fixture","1.0.0",SHA,24,"global",-4,-2,3,NativeLayeredTerrainSnapshot.UNIFORM_ENCODING,SHA,255,1,2,3,4,5,6);
        for(int x=-110;x<-40;x++)for(int y=130;y<210;y++) {final int px=x,py=y;record(out,()->height(uniform,px,py));}
    }
    static void worldCorpus(DataOutputStream out)throws Exception{
        Random random=new Random(212109);int protocol=NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION;
        for(boolean mixed:new boolean[]{false,true})for(boolean wide:new boolean[]{false,true}){
            NativeLayeredTerrainSnapshot visual=snapshot(protocol,-3,-2,3,wide,mixed,true,"global"),active=visual.toAtomicActivationInnerWindow();World w=world(active,visual);
            for(int pass=0;pass<5;pass++){
                if(pass==1){Sector[] sectors=(Sector[])BrowserResidentInputsFixture.get(w,"sectors");for(int n=0;n<9;n++){sectors[n]=Sector.blankLoaded();for(int x=0;x<48;x++)for(int y=0;y<48;y++)sectors[n].getTile(x,y).groundElevation=(n*7+x*11+y*3)&65535;}}
                if(pass==2){Sector[] sectors=(Sector[])BrowserResidentInputsFixture.get(w,"sectors");for(Sector s:sectors)for(int x=0;x<48;x++)for(int y=0;y<48;y++)s.getTile(x,y).groundElevation+=103;}
                if(pass==3)BrowserResidentInputsFixture.set(w,"symmetricNativeLayeredTerrainVisualSnapshot",snapshot(protocol,-4,-2,3,wide,mixed,true,"other"));
                if(pass==4){BrowserResidentInputsFixture.set(w,"syntheticDeepFixtureOffsetX",Integer.MAX_VALUE);BrowserResidentInputsFixture.set(w,"syntheticDeepFixtureOffsetZ",Integer.MIN_VALUE);}
                for(int n=0;n<500;n++){final int x=random.nextInt(46080)-12288,y=random.nextInt(46080)-12288;record(out,()->w.getPresentationTerrainElevation(x,y));record(out,()->w.isPresentationTerrainFaceTile(x>>7,y>>7)?1:0);}
                for(int x:new int[]{Integer.MIN_VALUE,-128,-1,0,18303,18304,Integer.MAX_VALUE})for(int y:new int[]{-128,0,Integer.MAX_VALUE})record(out,()->w.getPresentationTerrainElevation(x,y));
            }
            BrowserResidentInputsFixture.set(w,"nativeLayeredTerrainAppliedSectionX",Integer.MAX_VALUE);record(out,()->w.getPresentationTerrainElevation(-256,256));
            BrowserResidentInputsFixture.set(w,"nativeLayeredTerrainSnapshot",null);record(out,()->w.getPresentationTerrainElevation(500,600));
        }
    }
    static void uniformWorldCorpus(DataOutputStream out)throws Exception{
        NativeLayeredTerrainSnapshot uniform=new NativeLayeredTerrainSnapshot("fixture","1.0.0",SHA,24,"global",-4,-2,3,NativeLayeredTerrainSnapshot.UNIFORM_ENCODING,SHA,255,1,2,3,4,5,6);
        Random random=new Random(88221);
        for(NativeLayeredTerrainSnapshot visual:new NativeLayeredTerrainSnapshot[]{null,uniform}){
            World w=world(uniform,visual);Sector[] sectors=(Sector[])BrowserResidentInputsFixture.get(w,"sectors");
            for(int phase=0;phase<3;phase++){
                if(phase==1)for(int n=0;n<sectors.length;n++){sectors[n]=Sector.blankLoaded();for(int x=0;x<48;x++)for(int y=0;y<48;y++)sectors[n].getTile(x,y).groundElevation=1234+n;}
                if(phase==2)sectors[0]=null;
                for(int n=0;n<300;n++){final int x=random.nextInt(45000)-12000,y=random.nextInt(45000)-12000;record(out,()->w.getPresentationTerrainElevation(x,y));record(out,()->w.isPresentationTerrainFaceTile(x>>7,y>>7)?1:0);}
            }
        }
    }
    static void chunkCorpus(DataOutputStream out)throws Exception {
        String[] encodings={NativeLayeredTerrainChunk.UNIFORM_ENCODING,NativeLayeredTerrainChunk.RLE_ENCODING,NativeLayeredTerrainChunk.RAW_ENCODING,
            NativeLayeredTerrainChunk.VISUAL_ENCODING,NativeLayeredTerrainChunk.STRUCTURAL_ENCODING,NativeLayeredTerrainChunk.PRESENTATION_ENCODING,
            NativeLayeredTerrainChunk.UNIFORM_ENCODING_V2,NativeLayeredTerrainChunk.RLE_ENCODING_V2,NativeLayeredTerrainChunk.RAW_ENCODING_V2,
            NativeLayeredTerrainChunk.VISUAL_ENCODING_V2,NativeLayeredTerrainChunk.STRUCTURAL_ENCODING_V2,NativeLayeredTerrainChunk.PRESENTATION_ENCODING_V2};
        for(int size:new int[]{1,2,3,4,6,8,12,16,24,48}) {
            int low=(int)-Math.floorDiv(-(long)Integer.MIN_VALUE,size),high=(int)Math.floorDiv((long)Integer.MAX_VALUE-size+1,size);
            int[] centers={low,low+1,-3,-1,0,1,3,high-1,high};
            for(int cx:centers)for(String encoding:encodings) {
                int cy=centers[Math.floorMod(cx,centers.length)];
                int wire=NativeLayeredTerrainChunk.wireBytesForEncoding(encoding);
                byte[] source=new byte[size*size*wire];for(int n=0;n<source.length;n++)source[n]=(byte)(n*17+cx*7+cy*13);
                NativeLayeredTerrainChunk chunk=NativeLayeredTerrainChunk.available(size,cx,cy,Math.floorDiv(cx*size,48),Math.floorDiv(cy*size,48),encoding,SHA,source);
                Arrays.fill(source,(byte)77);
                long minX=(long)cx*size,minY=(long)cy*size;
                int[] xs={(int)(minX-1),(int)minX,(int)(minX+size-1),(int)(minX+size),Integer.MIN_VALUE,Integer.MAX_VALUE,0};
                int[] ys={(int)(minY-1),(int)minY,(int)(minY+size-1),(int)(minY+size),Integer.MIN_VALUE,Integer.MAX_VALUE,0};
                for(int x:xs)for(int y:ys) {
                    out.writeBoolean(chunk.covers(x,y));
                    final int px=x,py=y;
                    record(out,()->chunk.groundElevation(px,py));record(out,()->chunk.groundOverlay(px,py));
                }
                for(int[] point:new int[][]{{(int)minX,(int)minY},{(int)(minX+size),(int)minY}}) {
                    final int px=point[0],py=point[1];record(out,()->chunk.createTile(px,py).groundElevation);
                }
                NativeLayeredTerrainChunk empty=NativeLayeredTerrainChunk.voidChunk(size,cx,cy);
                for(int x:xs)for(int y:ys)out.writeBoolean(empty.covers(x,y));
                record(out,()->empty.createTile((int)minX,(int)minY).groundElevation);
            }
        }
        for(int size:new int[]{0,-1,5,49,Integer.MIN_VALUE,Integer.MAX_VALUE})record(out,()->NativeLayeredTerrainChunk.available(size,Integer.MAX_VALUE,Integer.MIN_VALUE,0,0,null,null,null).getChunkX());
        for(int x:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE})record(out,()->NativeLayeredTerrainChunk.available(48,x,0,0,0,null,null,null).getChunkX());
        for(int y:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE})record(out,()->NativeLayeredTerrainChunk.available(48,0,y,0,0,null,null,null).getChunkY());
        for(String encoding:new String[]{null,"bad"})record(out,()->NativeLayeredTerrainChunk.available(48,0,0,0,0,encoding,null,null).getChunkX());
        for(String sha:new String[]{null,"bad",SHA.toUpperCase()})record(out,()->NativeLayeredTerrainChunk.available(48,0,0,0,0,NativeLayeredTerrainChunk.RAW_ENCODING,sha,null).getChunkX());
        for(byte[] data:new byte[][]{null,new byte[0],new byte[23039]})record(out,()->NativeLayeredTerrainChunk.available(48,0,0,0,0,NativeLayeredTerrainChunk.RAW_ENCODING,SHA,data).getChunkX());
        for(int[] sector:new int[][]{{1,0},{0,-1}})record(out,()->NativeLayeredTerrainChunk.available(48,0,0,sector[0],sector[1],NativeLayeredTerrainChunk.RAW_ENCODING,SHA,new byte[23040]).getChunkX());
    }
    static long count(String name)throws Exception{Field f=NativeLayeredTerrainChunk.class.getDeclaredField(name);f.setAccessible(true);return f.getLong(null);}
    static void resetCounts()throws Exception{for(String name:new String[]{"fixtureCoversCalls","fixtureLongMultiplications","fixtureIntegerHits"}){Field f=NativeLayeredTerrainChunk.class.getDeclaredField(name);f.setAccessible(true);f.setLong(null,0);}}
    static void controls()throws Exception {
        boolean selected=Boolean.getBoolean("spoiledmilk.browserPrimitiveChunkCoverage"),available=false,enabled=false;
        Method getter=null;try{getter=NativeLayeredTerrainChunk.class.getMethod("isBrowserPrimitiveChunkCoverageEnabled");available=true;enabled=(Boolean)getter.invoke(null);}catch(NoSuchMethodException frozen){}
        check(enabled==(available&&selected),"Actual startup getter differs");
        NativeLayeredTerrainChunk regular=NativeLayeredTerrainChunk.voidChunk(48,-1,1);resetCounts();
        for(int n=0;n<100;n++)check(regular.covers(-48,48),"Regular boundary missed");
        long regularLong=count("fixtureLongMultiplications"),regularFast=count("fixtureIntegerHits");
        check(count("fixtureCoversCalls")==100&&regularLong==(enabled?0:200)&&regularFast==(enabled?100:0),"Regular operation path differs");
        NativeLayeredTerrainChunk edge=NativeLayeredTerrainChunk.voidChunk(1,Integer.MAX_VALUE,0);resetCounts();
        check(edge.covers(Integer.MAX_VALUE,0),"Exclusive overflow edge missed");
        check(count("fixtureLongMultiplications")==2&&count("fixtureIntegerHits")==0,"Unrepresentable exclusive edge bypassed long authority");
        Renderer3DSettings.setBrowserPresentationAvailable(false);resetCounts();check(regular.covers(-48,48),"Native regular boundary missed");
        check(count("fixtureLongMultiplications")==2&&count("fixtureIntegerHits")==0,"Native used optional browser comparisons");
        if(getter!=null)check(!(Boolean)getter.invoke(null),"Native receipt inferred browser capability");
        Renderer3DSettings.setBrowserPresentationAvailable(true);
        if(getter!=null){System.setProperty("spoiledmilk.browserPrimitiveChunkCoverage",String.valueOf(!selected));check((Boolean)getter.invoke(null)==selected,"Immutable startup flag changed late");System.setProperty("spoiledmilk.browserPrimitiveChunkCoverage",String.valueOf(selected));}
        System.out.println("test-only coverage operations: regularChecks=100 longMultiplications="+regularLong+" integerHits="+regularFast+" exclusiveOverflowLong=2 nativeLong=2");
    }
    public static void main(String[] args)throws Exception{
        Renderer3DSettings.setBrowserPresentationAvailable(true);try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){snapshotCorpus(out);worldCorpus(out);uniformWorldCorpus(out);chunkCorpus(out);}controls();System.out.println("PASS frozen raw Chunk/Snapshot/real World coverage and height corpus and "+checks+" structural/control checks; no GPU/FPS claim");
    }
}
