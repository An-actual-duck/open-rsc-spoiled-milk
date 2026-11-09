import com.openrsc.server.GameStateUpdater;
import com.openrsc.server.Server;
import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LayeredSpatialWindowKey;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.VisibilitySnapshot;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Listener/DB-free unit fixture. Exercises real Player reset and updater cache methods.
 * Unsafe supplies only the dependency graph, never gameplay or scene-ready receipts. */
public final class StaticSceneResetCacheFixture {
    static final sun.misc.Unsafe UNSAFE;
    static { try { Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);UNSAFE=(sun.misc.Unsafe)f.get(null); }catch(Exception e){throw new ExceptionInInitializerError(e);} }
    static Object blank(Class<?> type)throws Exception{return UNSAFE.allocateInstance(type);}
    static void field(Object owner,Class<?> type,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Method method(String name)throws Exception {Method m=GameStateUpdater.class.getDeclaredMethod(name,Player.class,VisibilitySnapshot.class);m.setAccessible(true);return m;}
    static final class ProjectionOnlyRegionManager extends RegionManager {
        ProjectionOnlyRegionManager(){super(null);}
        @Override public Point toRuntimeCompatibilityPoint(WorldLocation location){return Point.location(location.getCoordinate().getX(),location.getCoordinate().getY());}
    }
    static void position(Player p,int x,int y,int level)throws Exception{
        field(p,Entity.class,"location",new AtomicReference<Point>(Point.location(x,y)));
        field(p,Entity.class,"worldLocation",new AtomicReference<WorldLocation>(WorldLocation.global(new WorldCoordinate(x,y,level))));
    }
    static VisibilitySnapshot snapshot(Player p,boolean layered,long version)throws Exception {
        List empty=Collections.emptyList();
        return layered?new VisibilitySnapshot(empty,empty,empty,empty,empty,empty,1,1,LayeredSpatialWindowKey.around(p.getWorldLocation(),48),version)
            :new VisibilitySnapshot(empty,empty,empty,empty,empty,empty,1,1,900L,version);
    }
    public static void main(String[] args)throws Exception {
        Server server=(Server)blank(Server.class);ServerConfiguration config=new ServerConfiguration();config.WANT_SYNC_SCENE_BASELINE=true;config.OBJECT_VIEW_DISTANCE=8;
        field(server,Server.class,"config",config);
        World world=(World)blank(World.class);field(world,World.class,"regionManager",blank(ProjectionOnlyRegionManager.class));
        Player player=(Player)blank(Player.class);field(player,Entity.class,"world",world);
        field(player,Entity.class,"attributes",new ConcurrentHashMap<String,Object>());
        field(player,Player.class,"localObjects",new LinkedHashSet<GameObject>());field(player,Player.class,"localWallObjects",new LinkedHashSet<GameObject>());field(player,Player.class,"localGroundItems",new LinkedHashSet<>());
        player.setClientVersion(10053);player.setAttribute("unrelated",42);
        Class<?> summaryType=Class.forName("com.openrsc.server.GameStateUpdater$SceneBaselineSummary");Object summary=blank(summaryType);
        field(summary,summaryType,"protocolVersion",8);field(summary,summaryType,"scenery",1);field(summary,summaryType,"walls",1);
        field(summary,summaryType,"sceneryPageCursor",1);field(summary,summaryType,"wallsPageCursor",1);
        player.setAttribute("scene_baseline_summary",summary);
        GameStateUpdater updater=new GameStateUpdater(server);Method store=method("storeStaticSceneScanKey"),skip=method("canSkipStaticSceneScan");
        for(boolean layered:new boolean[]{false,true}){
            position(player,502,460,0);VisibilitySnapshot same=snapshot(player,layered,7);
            player.getLocalGameObjects().add((GameObject)blank(GameObject.class));player.getLocalWallObjects().add((GameObject)blank(GameObject.class));
            store.invoke(updater,player,same);require((Boolean)skip.invoke(updater,player,same),"steady complete baseline should skip scan");
            // This is the production boundary called by a same-position teleport.
            // Position, world snapshot and completed summary remain identical.
            player.resetLocalObjectState();
            require(player.getLocalGameObjects().isEmpty()&&player.getLocalWallObjects().isEmpty(),"local delivery sets not reset");
            require(!(Boolean)skip.invoke(updater,player,same),"same-position reset incorrectly skips scenery resend (layered="+layered+")");
            require(player.getAttribute("unrelated",0)==42,"unrelated metadata removed");
            require(player.getAttribute("scene_baseline_summary")==summary,"summary authority changed");
            store.invoke(updater,player,same);require((Boolean)skip.invoke(updater,player,same),"rescan key should become cacheable again");
            require(!(Boolean)skip.invoke(updater,player,snapshot(player,layered,8)),"changed scenery version must scan");
            position(player,550,460,0);require(!(Boolean)skip.invoke(updater,player,snapshot(player,layered,7)),"region/position change must scan");
            if(layered){position(player,502,460,-1);require(!(Boolean)skip.invoke(updater,player,snapshot(player,true,7)),"signed-level change must scan");}
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("PASS same-position reset invalidates static scan; steady/version/region/level behavior preserved");
    }
}
