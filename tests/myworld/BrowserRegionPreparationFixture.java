package orsc;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.openrsc.client.model.Tile;
import orsc.graphics.three.*;

/** Binary output is compared with independently compiled frozen World/snapshot sources. */
public final class BrowserRegionPreparationFixture {
    static final String SHA=String.join("",Collections.nCopies(64,"a"));
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    static void set(Object o,String name,Object value)throws Exception{field(o.getClass(),name).set(o,value);}
    static Object get(Object o,String name)throws Exception{return field(o.getClass(),name).get(o);}
    static sun.misc.Unsafe unsafe()throws Exception{return (sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);}
    static Object invoke(Object o,String name,Class<?>[] types,Object... args)throws Exception{
        Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(o,args);}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw(Exception)cause;throw(Error)cause;}
    }
    static NativeLayeredTerrainSnapshot snapshot(boolean structure,boolean wide,String space,int level,int cx,int cy,int salt,boolean holes){
        int size=48;NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[25];int at=0;
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++){
            boolean inner=Math.max(Math.abs(x),Math.abs(y))<2,available=!(structure&&inner)&&!(holes&&x==2&&y==2);
            if(!available){chunks[at++]=NativeLayeredTerrainChunk.voidChunk(size,cx+x,cy+y);continue;}
            byte[] bytes=new byte[size*size*(wide?11:10)];
            for(int n=0;n<bytes.length;n++)bytes[n]=(byte)(salt+n*7+x*13+y*19);
            String encoding=inner?(wide?NativeLayeredTerrainChunk.RAW_ENCODING_V2:NativeLayeredTerrainChunk.RAW_ENCODING):structure?(wide?NativeLayeredTerrainChunk.STRUCTURAL_ENCODING_V2:NativeLayeredTerrainChunk.STRUCTURAL_ENCODING):(wide?NativeLayeredTerrainChunk.VISUAL_ENCODING_V2:NativeLayeredTerrainChunk.VISUAL_ENCODING);
            chunks[at++]=NativeLayeredTerrainChunk.available(size,cx+x,cy+y,cx+x,cy+y,encoding,SHA,bytes);
            Arrays.fill(bytes,(byte)99); // Caller mutations cannot alter immutable source chunks.
        }
        return new NativeLayeredTerrainSnapshot(structure?NativeLayeredTerrainSnapshot.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION:NativeLayeredTerrainSnapshot.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,"fixture","1.0.0",SHA,size,space,level,cx,cy,2,chunks);
    }
    static World world(NativeLayeredTerrainSnapshot active)throws Exception{
        World w=(World)unsafe().allocateInstance(World.class);set(w,"nativeLayeredTerrainSnapshot",active);set(w,"worldEditorTerrainRevision",new AtomicLong(17));set(w,"renderer3DWorldChunkFrame",Renderer3DWorldChunkFrame.EMPTY);return w;
    }
    static NativeLayeredTerrainSnapshot merge(World w,NativeLayeredTerrainSnapshot visual,NativeLayeredTerrainSnapshot structural)throws Exception{
        try{return(NativeLayeredTerrainSnapshot)invoke(w,"mergeNativePresentation",new Class<?>[]{NativeLayeredTerrainSnapshot.class,NativeLayeredTerrainSnapshot.class},visual,structural);}
        catch(NoSuchMethodException frozen){return NativeLayeredTerrainSnapshot.mergePresentation(visual,structural);}
    }
    static void record(DataOutputStream out,NativeLayeredTerrainSnapshot s)throws Exception{
        out.writeUTF(s.scopeIdentity());out.writeInt(s.getProtocolVersion());out.writeInt(s.getAvailableChunkCount());
        for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)for(int x=0;x<48;x++)for(int y=0;y<48;y++){
            int wx=(s.getCurrentChunkX()+dx)*48+x,wy=(s.getCurrentChunkY()+dy)*48+y;
            boolean present=s.covers(s.getWorldSpace(),s.getLevel(),wx,wy);out.writeBoolean(present);if(!present)continue;
            Tile t=s.createTile(wx,wy);out.writeInt(t.groundElevation);out.writeByte(t.groundTexture);out.writeByte(t.groundOverlay);out.writeByte(t.roofTexture);out.writeByte(t.verticalWall);out.writeByte(t.horizontalWall);out.writeInt(t.diagonalWalls);
        }
    }
    static boolean rejected(World w,NativeLayeredTerrainSnapshot v,NativeLayeredTerrainSnapshot s)throws Exception{try{merge(w,v,s);return false;}catch(IllegalArgumentException expected){return true;}}
    static World.NativeLayeredTerrainHaloPrebuildResult result(NativeLayeredTerrainSnapshot active,NativeLayeredTerrainSnapshot merged)throws Exception{
        Object r=unsafe().allocateInstance(World.NativeLayeredTerrainHaloPrebuildResult.class);
        set(r,"activeScopeIdentity",active.scopeIdentity());set(r,"haloScopeIdentity",merged.scopeIdentity());set(r,"haloProtocolVersion",NativeLayeredTerrainSnapshot.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION);set(r,"sourceRevision",17L);set(r,"includeRoofGeometry",!Config.C_HIDE_ROOFS);set(r,"outerChunks",Collections.emptyList());set(r,"retentionChunks",Collections.emptyList());return(World.NativeLayeredTerrainHaloPrebuildResult)r;
    }
    public static void main(String[] args)throws Exception{
        Renderer3DSettings.setBrowserPresentationAvailable(true);
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){
            for(boolean wide:new boolean[]{false,true})for(int level:new int[]{0,-1,-8}){
                NativeLayeredTerrainSnapshot v=snapshot(false,wide,"global",level,-3,7,19,true),s=snapshot(true,wide,"global",level,-3,7,31,true);World w=world(v);
                NativeLayeredTerrainSnapshot merged=merge(w,v,s);record(out,merged);record(out,merge(w,v,s));
                Tile tile=merged.createTile(-5*48,5*48);tile.groundElevation=0;record(out,merge(w,v,s));
                out.writeBoolean(rejected(w,null,s));out.writeBoolean(rejected(w,v,null));out.writeBoolean(rejected(w,s,v));
                out.writeBoolean(rejected(w,v,snapshot(true,wide,"other",level,-3,7,31,true)));
                out.writeBoolean(rejected(w,v,snapshot(true,wide,"global",level-1,-3,7,31,true)));
                out.writeBoolean(rejected(w,v,snapshot(true,wide,"global",level,-2,7,31,true)));
                out.writeBoolean(rejected(w,v,snapshot(true,!wide,"global",level,-3,7,31,true)));
                out.writeBoolean(rejected(w,v,snapshot(true,wide,"global",level,-3,7,31,false)));
                // Same scope strings with independently copied source content must miss by
                // immutable object identity; never trust a payload label as byte identity.
                NativeLayeredTerrainSnapshot changed=snapshot(false,wide,"global",level,-3,7,77,true);record(out,merge(w,changed,s));record(out,merge(w,v,s));
            }
            NativeLayeredTerrainSnapshot visual=snapshot(false,true,"global",-1,6,8,2,false),structure=snapshot(true,true,"global",-1,6,8,5,false);
            NativeLayeredTerrainSnapshot active=snapshot(false,true,"global",-1,5,8,2,false).toAtomicActivationInnerWindow();
            World w=world(active);World.NativeLayeredTerrainHaloPrebuildResult r=result(active,merge(w,visual,structure));
            out.writeBoolean(w.canAcceptPredictedNativeLayeredTerrainStructure(r,visual,structure,0,0));
            for(String n:new String[]{"sourceRevision","worldOffsetX","worldOffsetZ","includeRoofGeometry","activeScopeIdentity","haloScopeIdentity","haloProtocolVersion"}){
                Object old=get(r,n);Object bad=old instanceof Long?18L:old instanceof Integer?((Integer)old)+1:old instanceof Boolean?!((Boolean)old):"wrong";set(r,n,bad);out.writeBoolean(w.canAcceptPredictedNativeLayeredTerrainStructure(r,visual,structure,0,0));set(r,n,old);
            }
            out.writeBoolean(w.canAcceptPredictedNativeLayeredTerrainStructure(null,visual,structure,0,0));
            out.writeBoolean(w.canAcceptPredictedNativeLayeredTerrainStructure(r,null,structure,0,0));out.writeBoolean(w.canAcceptPredictedNativeLayeredTerrainStructure(r,visual,null,0,0));
            out.writeBoolean(w.canAcceptPredictedNativeLayeredTerrainStructure(r,visual,structure,1,0));out.writeBoolean(w.canAcceptPredictedNativeLayeredTerrainStructure(r,visual,structure,0,-1));
            check(get(w,"nativeLayeredTerrainSnapshot")==active,"Preparation changed active authority");
            set(w,"nativeLayeredTerrainSnapshot",visual.toAtomicActivationInnerWindow());((AtomicLong)get(w,"worldEditorTerrainRevision")).set(18);
            out.writeBoolean(w.publishPredictedNativeLayeredTerrainHalo(r,visual,structure,0,0)); // old active receipt must reject
            set(r,"activeScopeIdentity",((NativeLayeredTerrainSnapshot)get(w,"nativeLayeredTerrainSnapshot")).scopeIdentity());
            out.writeBoolean(w.publishPredictedNativeLayeredTerrainHalo(r,visual,structure,0,0));
            out.writeInt(((Renderer3DWorldChunkFrame)get(w,"symmetricRenderer3DWorldChunkFrame")).getChunkCount());
            out.writeUTF(((NativeLayeredTerrainSnapshot)get(w,"nativeLayeredTerrainSnapshot")).scopeIdentity());
        }
        // Candidate-only controls verify bounded caching and absence outside browser.
        if(!Boolean.getBoolean("fixture.frozen"))try{
            Class<?> c=Class.forName("orsc.NativePresentationMergeCache");Object cache=c.newInstance();Method m=c.getMethod("merge",NativeLayeredTerrainSnapshot.class,NativeLayeredTerrainSnapshot.class),d=c.getMethod("diagnostics"),clear=c.getMethod("clear");
            NativeLayeredTerrainSnapshot v=snapshot(false,true,"global",-1,6,8,2,false),s=snapshot(true,true,"global",-1,6,8,5,false);Object first=m.invoke(cache,v,s);
            check(m.invoke(cache,v,s)==first,"Exact immutable pair not reused");long[] stats=(long[])d.invoke(cache);check(stats[0]==1&&stats[1]==1,"One pair counters wrong");check(stats[2]==1672704,"Retained inputs/output exceed byte bound");
            try{m.invoke(cache,v,null);throw new AssertionError("Invalid pair accepted");}catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"Wrong invalid pair error");}
            check(m.invoke(cache,v,s)==first,"Invalid pair poisoned prior owner");
            List<Throwable> failures=Collections.synchronizedList(new ArrayList<Throwable>());Thread[] readers=new Thread[4];
            for(int n=0;n<readers.length;n++){readers[n]=new Thread(()->{try{for(int k=0;k<25;k++)if(m.invoke(cache,v,s)!=first)throw new AssertionError("Concurrent pair did not share one merged owner");}catch(Throwable failure){failures.add(failure);}});readers[n].start();}
            for(Thread reader:readers)reader.join();check(failures.isEmpty(),"Concurrent merge failed");check(((long[])d.invoke(cache))[1]==1,"Concurrent pair rebuilt");
            clear.invoke(cache);check(((long[])d.invoke(cache))[2]==0,"Clear retained snapshot bytes");check(m.invoke(cache,v,s)!=first,"Clear reused obsolete merged owner");
            World w=world(v);Renderer3DSettings.setBrowserPresentationAvailable(false);merge(w,v,s);check(((long[])World.class.getMethod("getBrowserRegionPreparationDiagnostics").invoke(w))[1]==0,"Native/default path populated browser cache");
            Renderer3DSettings.setBrowserPresentationAvailable(true);merge(w,v,s);merge(w,v,s);long[] selected=(long[])World.class.getMethod("getBrowserRegionPreparationDiagnostics").invoke(w);
            check(selected[1]==(Boolean.getBoolean("spoiledmilk.browserRegionPreparation")?1:0),"Actual private policy not respected");
            w.setLayeredTerrainScope(false,null,0,0);check(((long[])World.class.getMethod("getBrowserRegionPreparationDiagnostics").invoke(w))[2]==0,"Logout retained pair");
        }catch(ClassNotFoundException frozen){}
        System.out.println("PASS region merge authority/tile/rejection parity corpus and "+checks+" candidate lifecycle controls");
    }
}
