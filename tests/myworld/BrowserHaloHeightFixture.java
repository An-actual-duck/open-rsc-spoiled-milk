package orsc;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import com.openrsc.client.model.Sector;
import orsc.graphics.three.*;

/** Independent frozen/current source corpus; no GPU, server, or per-frame counters. */
public final class BrowserHaloHeightFixture {
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
    static void controls()throws Exception{
        Field counter=NativeLayeredTerrainChunk.class.getDeclaredField("fixtureCoversCalls");counter.setAccessible(true);
        NativeLayeredTerrainSnapshot regular=snapshot(NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,-1,0,0,true,false,false,"global");
        counter.setInt(null,0);int legacy=regular.covers("global",-1,96,96)?regular.getGroundElevation(96,96):Integer.MIN_VALUE;int originalChecks=counter.getInt(null);check(originalChecks==51,"Original regular scan count changed");
        World w=world(regular.toAtomicActivationInnerWindow(),regular);counter.setInt(null,0);
        int vertex=(Integer)BrowserResidentInputsFixture.call(w,"presentationVertexElevation",new Class<?>[]{int.class,int.class,NativeLayeredTerrainSnapshot.class,NativeLayeredTerrainSnapshot.class},144,144,BrowserResidentInputsFixture.get(w,"nativeLayeredTerrainSnapshot"),regular);
        int worldChecks=counter.getInt(null);check(vertex==legacy*3,"World halo height differs from primitive source");
        int directChecks=-1,fallbackChecks=-1;
        try{
            Method direct=NativeLayeredTerrainSnapshot.class.getMethod("getGroundElevationOrUnavailable",int.class,int.class);
            counter.setInt(null,0);check((Integer)direct.invoke(regular,96,96)==legacy,"Regular primitive value changed");directChecks=counter.getInt(null);check(directChecks==1,"Regular grid performed a chunk scan");
            NativeLayeredTerrainSnapshot mixed=snapshot(NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,-1,0,0,true,true,false,"global");
            check(!(Boolean)BrowserResidentInputsFixture.get(mixed,"regularHeightLookup"),"Mixed-size shape incorrectly eligible");counter.setInt(null,0);direct.invoke(mixed,10000,10000);fallbackChecks=counter.getInt(null);check(fallbackChecks==25,"Irregular accepted shape skipped original scan");
            boolean enabled=(Boolean)World.class.getMethod("isBrowserHaloHeightLookupEnabled").invoke(null);check(worldChecks==(enabled?1:51),"World did not follow actual private lookup policy");
            Renderer3DSettings.setBrowserPresentationAvailable(false);counter.setInt(null,0);
            BrowserResidentInputsFixture.call(w,"presentationVertexElevation",new Class<?>[]{int.class,int.class,NativeLayeredTerrainSnapshot.class,NativeLayeredTerrainSnapshot.class},144,144,BrowserResidentInputsFixture.get(w,"nativeLayeredTerrainSnapshot"),regular);check(counter.getInt(null)==51,"Native World used optional primitive path");
            check(!(Boolean)World.class.getMethod("isBrowserHaloHeightLookupEnabled").invoke(null),"Native path enabled browser lookup");
            Renderer3DSettings.setBrowserPresentationAvailable(true);check(((Boolean)World.class.getMethod("isBrowserHaloHeightLookupEnabled").invoke(null))==Boolean.getBoolean("spoiledmilk.browserHaloHeightLookup"),"Actual browser policy differs");
        }catch(NoSuchMethodException frozen){check(worldChecks==51,"Frozen World did not use original scans");}
        Sector[] sectors=(Sector[])BrowserResidentInputsFixture.get(w,"sectors");sectors[0]=Sector.blankLoaded();sectors[0].getTile(5,5).groundElevation=123;counter.setInt(null,0);
        int loaded=(Integer)BrowserResidentInputsFixture.call(w,"presentationVertexElevation",new Class<?>[]{int.class,int.class,NativeLayeredTerrainSnapshot.class,NativeLayeredTerrainSnapshot.class},5,5,BrowserResidentInputsFixture.get(w,"nativeLayeredTerrainSnapshot"),regular);check(loaded==369&&counter.getInt(null)==0,"Loaded live sector did not take precedence");
        System.out.println("test-only chunk coverage calls: old="+originalChecks+" direct="+directChecks+" irregular="+fallbackChecks+" actualWorld="+worldChecks);
    }
    public static void main(String[] args)throws Exception{
        Renderer3DSettings.setBrowserPresentationAvailable(true);try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){snapshotCorpus(out);worldCorpus(out);uniformWorldCorpus(out);}controls();System.out.println("PASS raw snapshot/real World height corpus and "+checks+" structural/control checks; no GPU/FPS claim");
    }
}
