package orsc;

import java.lang.reflect.*;
import java.util.*;
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
    static NativeLayeredTerrainSnapshot snapshot(int level) {
        NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[9]; int at=0;
        for(int x=4;x<=6;x++) for(int y=10;y<=12;y++) chunks[at++]=NativeLayeredTerrainChunk.voidChunk(48,x,y);
        return new NativeLayeredTerrainSnapshot(8,"fixture","1.0.0",HASH,48,"global",level,5,11,1,chunks);
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
        Config.F_CACHE_DIR=args[0];
        MudClientGraphics surface=new MudClientGraphics(64,64,1); Scene scene=new Scene(surface,4,16,4);
        World world=new World(scene,surface); BrowserWholeOverworldRam reader=admission();
        WholeOverworldRamRegistry.install(reader);
        // Existing committed L1 carrier values from the reported crash. Only setup
        // bypasses loading; the return itself calls the actual public scope setter.
        NativeLayeredTerrainSnapshot upper=snapshot(1), ground=snapshot(0);
        set(world,"syntheticDeepFixtureTerrain",true); set(world,"nativeLayeredTerrainSnapshot",upper);
        set(world,"syntheticDeepFixtureOffsetX",2304); set(world,"syntheticDeepFixtureOffsetZ",832);
        world.setLayeredTerrainScope(false,ground,2304,1776);
        check(world.getWholeOverworldSourceSnapshot()==ground,"Return commits incoming L0 source");
        check(world.getWholeRamBasis().offsetTilesZ==1776,"Return commits incoming aligned carrier, never rounds upper carrier");
        check(reader.diagnostics()[9]==0,"Admission-only fixture did not claim a prepared activation");
        System.out.println("PASS checks="+checks+" synthetic-admission-only=true");
    }
}
