package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.authentic.skills.agility.GnomeAgilityCourse;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Actual course callbacks/Player teleports on installed terrain, with only script waits elided. */
public final class GnomeAgilityLayerIntegrationTest {
    private static int checks, waited;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static WorldLocation at(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void obstacle(Server server, Player player, GnomeAgilityCourse plugin,
                                 int id, int x, int y, int level, int toX, int toY, int toLevel) {
        GameObject object = server.getWorld().getRegionManager().findNativeLayeredScenery(at(x, y, level));
        check(object != null && object.getID() == id, "installed obstacle " + id);
        check(plugin.blockOpLoc(player, object, object.getGameObjectDef().getCommand1()), "course owns obstacle " + id);
        int before = player.getSkills().getExperience(Skill.AGILITY.id());
        plugin.onOpLoc(player, object, object.getGameObjectDef().getCommand1());
        check(player.getWorldLocation().equals(at(toX, toY, toLevel)), "correct landing after obstacle " + id
            + ": expected " + at(toX, toY, toLevel) + ", got " + player.getWorldLocation());
        check(player.getSkills().getExperience(Skill.AGILITY.id()) > before, "obstacle XP remains awarded " + id);
        check(server.getWorld().getRegionManager().hasNativeLayeredTerrain(player.getWorldLocation()),
            "landing has authored terrain " + id);
        int mask = server.getWorld().getRegionManager().getTile(player.getWorldLocation()).traversalMask;
        check((mask & (CollisionFlag.FULL_BLOCK | CollisionFlag.OBJECT)) == 0,
            "landing tile interior is clear " + id + " mask=" + mask);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Server server = new Server("myworld.conf");
        Field contextField = PluginTask.class.getDeclaredField("tasksMap");
        contextField.setAccessible(true);
        Map<String, PluginTask> contexts = (Map<String, PluginTask>) contextField.get(null);
        try {
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
            server.getEntityHandler().load();
            server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
            server.getWorld().getRegionManager().populateNativeLayeredPlacements();
            final Player player = new Player(server.getWorld(), DataConversions.usernameToHash("gnomecheck"));
            player.setClientVersion(server.getConfig().CLIENT_VERSION);
            player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
            player.getClientLimitations().maxSceneryId = Integer.MAX_VALUE;
            player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
            player.setInitialLayeredLocation(at(692, 494, 0));
            server.getWorld().getPlayers().add(player);
            player.updateRegion();
            player.setLoggedIn(true);
            player.setBusy(false);
            // Keep XP awards below a level-up boundary: this fixture never opens a player database.
            player.getSkills().setTemporaryLevelAndMaxStat(Skill.AGILITY.id(), 99, 99, false);
            boolean utility=Boolean.getBoolean("agility.utility");
            if(utility){
                server.getConfig().WANT_EQUIPMENT_TAB=true;server.getConfig().WANT_FATIGUE=false;server.getConfig().SKILLING_EXP_RATE=1;
                for(int id:new int[]{3472,3473,3474}){
                    player.getCarriedItems().getInventory().add(new com.openrsc.server.model.container.Item(id));
                    int slot=player.getCarriedItems().getInventory().getLastIndexById(id);
                    com.openrsc.server.model.struct.EquipRequest equip=new com.openrsc.server.model.struct.EquipRequest(player,player.getCarriedItems().getInventory().get(slot),com.openrsc.server.model.struct.EquipRequest.RequestType.FROM_INVENTORY,false);equip.inventorySlot=slot;
                    check(player.getCarriedItems().getEquipment().equipItem(equip),"real utility equip");
                }
                player.getCache().set(com.openrsc.server.content.AgilityUtilityEquipment.BOOT_CHARGE,20);
            }
            int startXp=player.getSkills().getExperience(Skill.AGILITY.id());
            contexts.put(Thread.currentThread().getName(), new PluginTask(server.getWorld(), player, "OpLoc", new Object[0]) {
                public int action() { return 0; }
                public synchronized void pause(int ticks) { waited+=ticks; }
            });
            GnomeAgilityCourse plugin = new GnomeAgilityCourse();
            // Two laps exercise both first-use and repeated completion/pouch state.
            for (int lap = 1; lap <= 2; lap++) {
                obstacle(server, player, plugin, 655, 692, 495, 0, 692, 499, 0);
                obstacle(server, player, plugin, 647, 692, 503, 0, 692, 504, 1);
                obstacle(server, player, plugin, 648, 693, 508, 1, 693, 506, 2);
                obstacle(server, player, plugin, 650, 689, 507, 2, 685, 508, 2);
                obstacle(server, player, plugin, 649, 683, 508, 2, 683, 506, 0);
                obstacle(server, player, plugin, 653, 683, 502, 0, 683, 501, 0);
                obstacle(server, player, plugin, 654, 683, 497, 0, 683, 494, 0);
                check(player.getCarriedItems().getInventory().countId(ItemId.TIER_1_AGILITY_POUCH.id()) == lap,
                    "one normal course pouch per completed lap");
            }
            if(utility){
                check(waited==54,"gloves shorten obstacle waits while keeping dialogue timing: "+waited);
                check(player.getSkills().getExperience(Skill.AGILITY.id())-startXp==936,"two full laps grant boosted XP");
                check(com.openrsc.server.content.AgilityUtilityEquipment.bootCharge(player)==22,"two laps recharge two percent; scripted relocations never drain");
            }
            System.out.println("PASS Gnome agility native layer integration: " + checks
                + " assertions; two full course laps with real callbacks, Player transitions and installed collision");
        } finally {
            contexts.remove(Thread.currentThread().getName());
            for (GameTickEvent event : new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents())) event.stop();
            server.getGameEventHandler().cleanupEvents();
            server.getWorld().getPlayers().clear();
            server.getEntityHandler().unload();
            for (String name : new String[]{"sqlLoggingThreadPool", "sqlThreadPool", "onlineMonitorThreadPool"}) {
                Field field = Server.class.getDeclaredField(name);
                field.setAccessible(true);
                ((ExecutorService) field.get(server)).shutdownNow();
            }
            Server.serversList.remove(server.getName(), server);
        }
    }
}
