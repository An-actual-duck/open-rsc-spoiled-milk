package orsc;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Future;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** CPU-only admission regression. Synthetic void pages, not cooked-data readiness proof. */
public final class WholeRamLayerReturnFixture {
    static int checks;
    static final String HASH = String.join("", Collections.nCopies(64, "a"));
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static Field field(Class<?> type, String name) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); return f; }
    static void set(Object owner, String name, Object value) throws Exception { field(owner.getClass(),name).set(owner,value); }
    static Object get(Object owner, String name) throws Exception { return field(owner.getClass(),name).get(owner); }
    static NativeLayeredTerrainSnapshot snapshot(int level) { return snapshot(level,1,HASH); }
    static NativeLayeredTerrainSnapshot snapshot(int level,int radius,String hash) {
        NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[(radius*2+1)*(radius*2+1)]; int at=0;
        for(int x=5-radius;x<=5+radius;x++) for(int y=11-radius;y<=11+radius;y++) chunks[at++]=NativeLayeredTerrainChunk.voidChunk(48,x,y);
        return new NativeLayeredTerrainSnapshot(radius==1?8:9,"fixture","1.0.0",hash,48,"global",level,5,11,radius,chunks);
    }
    static BrowserWholeOverworldRam admission() throws Exception {
        // Constructor validates a full cooked delivery. Here inject only the exact
        // immutable source-admission inputs to isolate the transition, never activate.
        Class<?> u=Class.forName("sun.misc.Unsafe"); Field f=field(u,"theUnsafe"); Object unsafe=f.get(null);
        BrowserWholeOverworldRam result=(BrowserWholeOverworldRam)u.getMethod("allocateInstance",Class.class).invoke(unsafe,BrowserWholeOverworldRam.class);
        set(result,"ready",true); set(result,"manifestSha",HASH); set(result,"sectors",new HashMap<String,String>());
        set(result,"hiddenRoofs",false); return result;
    }
    public static void main(String[] args) throws Exception {
        Config.F_CACHE_DIR=args[0]; Config.S_WANT_CUSTOM_SPRITES=true;
        com.openrsc.client.entityhandling.EntityHandler.load(true);
        MudClientGraphics surface=new MudClientGraphics(64,64,1); Scene scene=new Scene(surface,4,16,4);
        World world=new World(scene,surface); BrowserWholeOverworldRam reader=admission();
        boolean enabled=WholeOverworldRamRegistry.isEnabled();
        if(enabled)WholeOverworldRamRegistry.install(reader);
        // Existing committed L1 carrier values from the reported crash. Only setup
        // bypasses loading; the return itself calls the actual public scope setter.
        NativeLayeredTerrainSnapshot upper=snapshot(1), ground=snapshot(0);
        set(world,"syntheticDeepFixtureTerrain",true); set(world,"nativeLayeredTerrainSnapshot",upper);
        set(world,"syntheticDeepFixtureOffsetX",2304); set(world,"syntheticDeepFixtureOffsetZ",832);
        world.setLayeredTerrainScope(false,ground,2304,1776);
        check(world.getWholeOverworldSourceSnapshot()==ground,"Return commits incoming L0 source");
        check(world.getWholeRamBasis().offsetTilesZ==1776,"Return commits incoming aligned carrier, never rounds upper carrier");
        check(((Integer)get(reader,"activations"))==0,"Admission-only fixture did not claim a prepared activation");
        for(int level:new int[]{1,-1,10,1}) {
            NativeLayeredTerrainSnapshot other=snapshot(level);
            int offset=level==1?832:level==-1?2720:-7808;
            world.setLayeredTerrainScope(false,other,2304,offset);
            check(world.getWholeOverworldSourceSnapshot()==other,"Non-ground source committed exactly");
            check(((Integer)get(world,"syntheticDeepFixtureOffsetZ"))==offset,"Non-ground carrier preserved exactly");
            check(!reader.accepts(world,other),"Non-ground source takes native fallback");
            NativeLayeredTerrainSnapshot halo=snapshot(level,2,HASH);
            Future<World.NativeLayeredTerrainHaloPrebuildResult> pending=world.preloadNativeLayeredTerrainHalo(halo,2304,offset);
            World.NativeLayeredTerrainHaloPrebuildResult late=pending.get();
            check(late!=null,"Actual background fallback preparation completed");
            long oldRevision=((java.util.concurrent.atomic.AtomicLong)get(world,"worldEditorTerrainRevision")).get();
            world.setLayeredTerrainScope(false,ground,2304,1776);
            check(world.getWholeOverworldSourceSnapshot()==ground,"Return source commits after fallback");
            check(world.getWholeRamBasis().offsetTilesZ==1776,"Return uses incoming aligned basis");
            check(((java.util.concurrent.atomic.AtomicLong)get(world,"worldEditorTerrainRevision")).get()>oldRevision,"Return advances publication revision");
            check(!world.publishNativeLayeredTerrainHalo(late,halo,2304,offset),"Late actual old-layer result cannot publish");
            check(world.getWholeOverworldSourceSnapshot()==ground,"Rejected late result leaves active ground unchanged");
        }
        check(!reader.acceptsCaptured(ground,2304,832,true),"Captured unaligned carrier takes literal fallback");
        check(!reader.acceptsCaptured(ground,2304,1776,false),"Roof option mismatch takes literal fallback");
        boolean strict=false; try{new WholeRamBasis(2304,832);}catch(IllegalStateException expected){strict=true;}
        check(strict,"Genuine misaligned WholeRamBasis remains strictly refused");
        if(enabled){
            world.setLayeredTerrainScope(false,upper,2304,832);
            boolean refused=false;
            try{world.setLayeredTerrainScope(false,snapshot(0,1,HASH.replace('a','b')),2304,1776);}catch(IllegalStateException expected){refused=expected.getMessage().contains("manifest changed");}
            check(refused,"Wrong source identity fails before incoming scope publication");
            check(world.getWholeOverworldSourceSnapshot()==upper,"Wrong source did not commit terrain");
            check(((Integer)get(world,"syntheticDeepFixtureOffsetZ"))==832,"Wrong source did not partially commit carrier");
            world.setLayeredTerrainScope(false,ground,2304,1776);
            // Captured admission only decides whether to clear retained meshes;
            // activation still checks the current World before any reset/copy.
            Map patches=(Map)get(world,"worldEditorTerrainPatches"); patches.put("fixture",null);
            long revision=world.getWholeOverworldSourceRevision();
            Object frame=get(world,"renderer3DWorldChunkFrame");
            boolean patched=false;
            try{world.loadSections(2544,2304,0);}catch(RuntimeException expected){patched=expected.getCause()!=null&&expected.getCause().toString().contains("unbaked terrain editor patches");}
            check(patched,"Actual loadSections retains current-World patch refusal");
            check(world.getWholeOverworldSourceRevision()==revision&&get(world,"renderer3DWorldChunkFrame")==frame,"Patch refusal precedes active publication/reset");
            patches.clear();
            boolean ownership=false;
            try{reader.accepts(world,ground);}catch(IllegalStateException expected){ownership=expected.getMessage().contains("native activation unsupported");}
            check(ownership,"Captured precommit acceptance does not bypass active native/browser ownership guard");
        }
        WholeOverworldRamRegistry.clear();
        world.setLayeredTerrainScope(false,upper,2304,832);
        world.setLayeredTerrainScope(false,ground,2304,1776);
        check(world.getWholeOverworldSourceSnapshot()==ground,"Absent provider always uses actual native fallback");
        check(((Integer)get(reader,"activations"))==0,"No cooked activation or readiness claim");
        System.out.println("PASS checks="+checks+" enabled="+enabled+" synthetic-admission-only=true");
    }
}
