package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.net.Packet;
import com.openrsc.server.plugins.authentic.defaults.Default;
import com.openrsc.server.plugins.authentic.npcs.rimmington.MasterCrafter;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actual trigger dispatch and ordinary stair travel; no listeners, accounts or map edits. */
public final class CraftingGuildCaveAccessFixture {
    private static int checks;
    private static final String REQUIREMENT = "You need level 70 Crafting to enter the caves below the guild";
    private static final String WARNING = "Gem dragons lurk down there. Be prepared for a dangerous fight";

    private CraftingGuildCaveAccessFixture() { }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static WorldLocation at(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }
    private static GameObject stair(CurrentCombatHarness h, int id, int x, int y, int level) {
        GameObject object = new GameObject(h.world(), Point.location(x, y), id, 2, 0);
        object.setInitialWorldLocation(at(x, y, level));
        return object;
    }
    @SuppressWarnings("unchecked")
    private static List<Packet> packets(Player player) throws Exception {
        Field field = Player.class.getDeclaredField("outgoingPackets");
        field.setAccessible(true);
        return (List<Packet>) field.get(player);
    }
    private static boolean sent(Player player, String text) throws Exception {
        for (Packet packet : packets(player)) {
            if (packet.getBuffer().toString(StandardCharsets.ISO_8859_1).contains(text)) return true;
        }
        return false;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, PluginTask> contexts() throws Exception {
        Field field = PluginTask.class.getDeclaredField("tasksMap");
        field.setAccessible(true);
        return (Map<String, PluginTask>) field.get(null);
    }
    private static List<String> dispatch(CurrentCombatHarness h, Player player, GameObject object,
                                         String command, String expectedOwner, Npc speakingNpc) throws Exception {
        for (GameTickEvent event : new ArrayList<GameTickEvent>(h.server().getGameEventHandler().getEvents())) {
            if (event instanceof PluginTickEvent) event.stop();
        }
        h.server().getGameEventHandler().cleanupEvents();
        packets(player).clear();
        boolean blockedDefault = h.server().getPluginHandler().handlePlugin(
            OpLocTrigger.class, player, new Object[]{player, object, command}, null);
        check(blockedDefault == expectedOwner.equals("MasterCrafter"), "exclusive trigger ownership " + expectedOwner);
        List<PluginTickEvent> actions = new ArrayList<PluginTickEvent>();
        for (GameTickEvent event : h.server().getGameEventHandler().getEvents()) {
            if (event instanceof PluginTickEvent) {
                actions.add((PluginTickEvent) event);
            }
        }
        check(actions.size() == 1, "exactly one scheduled " + expectedOwner + " action");
        check(actions.get(0).getDescriptor().equals(expectedOwner + ".onOpLoc"), "expected callback owns the sole action");
        final List<String> speech = new ArrayList<String>();
        // Execute the actual callback selected by PluginHandler. Only dialogue waits
        // are suppressed; routing, skill checks, packets and Ladders are production.
        PluginTask context = new PluginTask(h.world(), player, "OpLoc", new Object[0]) {
            public int action() { return 0; }
            public synchronized void pause(int ticks) {
                if (speakingNpc != null && speakingNpc.getUpdateFlags().getChatMessage() != null) {
                    speech.add(speakingNpc.getUpdateFlags().getChatMessage().getMessageString());
                }
            }
        };
        Map<String, PluginTask> contexts = contexts();
        contexts.put(Thread.currentThread().getName(), context);
        try { check(actions.get(0).getPluginTask().action() == 1, "selected callback completed"); }
        finally {
            contexts.remove(Thread.currentThread().getName());
            actions.get(0).stop();
            h.server().getGameEventHandler().cleanupEvents();
        }
        return speech;
    }
    private static void level(Player player, int permanent, int current) {
        player.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(), current, permanent, false);
    }
    private static void move(Player player, WorldLocation destination) {
        player.resetAll();
        player.teleportLayered(destination, false);
        player.setBusy(false);
    }
    public static void main(String[] args) throws Exception {
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
            MasterCrafter master = new MasterCrafter();
            h.installPlugin(OpLocTrigger.class, master);
            h.installDefaultPlugin(new Default());
            h.server().getConfig().WORLD_BUILDER_MODE = false;
            Player player = h.player("craft gate", 339, 614);
            h.recordOutgoingPackets(player);
            GameObject down = stair(h, 42, 338, 614, 0);
            check(master.blockOpLoc(player, down, "go down"), "surface entrance owned");
            check(!master.blockOpLoc(player, down, "examine"), "other action excluded");
            check(!master.blockOpLoc(player, stair(h, 41, 338, 614, 0), "go down"), "other object excluded");
            check(!master.blockOpLoc(player, stair(h, 42, 339, 614, 0), "go down"), "neighbouring stair excluded");
            for (int layer : new int[]{-1, -2, 1, 2}) {
                check(!master.blockOpLoc(player, stair(h, 42, 338, 614, layer), "go down"), "same coordinates on level " + layer + " excluded");
            }
            Method locationMatch = MasterCrafter.class.getDeclaredMethod("isCaveStairLocation", WorldLocation.class);
            locationMatch.setAccessible(true);
            check(!(Boolean) locationMatch.invoke(null, new WorldLocation(new WorldSpaceId("test-instance"), new WorldCoordinate(338, 614, 0))), "another worldspace cannot alias entrance");
            for (int[] levels : new int[][]{{1,1},{69,69},{69,99}}) {
                move(player, at(339,614,0));level(player, levels[0], levels[1]);
                dispatch(h,player,down,"go down","MasterCrafter",null);
                check(player.getWorldLocation().equals(at(339,614,0)), "underlevel and boosted underlevel remain outside");
                check(sent(player,REQUIREMENT), "fallback states exact level requirement");
                check(sent(player,WARNING), "fallback warns of dangerous gem dragons");
            }
            for (int[] levels : new int[][]{{70,70},{71,71},{70,1}}) {
                move(player,at(339,614,0));level(player,levels[0],levels[1]);
                dispatch(h,player,down,"go down","MasterCrafter",null);
                check(player.getWorldLocation().equals(at(337,614,-1)), "qualified or drained crafter uses one normal downward transition");
                check(sent(player,WARNING), "qualified entry has immediate danger warning");
                check(!sent(player,REQUIREMENT), "qualified entry has no refusal");
            }
            level(player,1,1);
            move(player,at(337,614,-1));GameObject up=stair(h,41,338,614,-1);
            dispatch(h,player,up,"go up","Default",null);
            check(player.getWorldLocation().equals(at(341,614,0)), "low-level player can freely leave by ordinary ascent");
            check(!sent(player,REQUIREMENT), "exit has no Crafting requirement");
            move(player,at(339,614,-1));check(!master.blockOpLoc(player,down,"go down"), "player from another level cannot claim surface entrance");
            move(player,at(339,614,0));Npc otherLevel=h.npc(NpcId.MASTER_CRAFTER.id(),348,606+3*944);
            List<String> unrelatedSpeech=dispatch(h,player,down,"go down","MasterCrafter",otherLevel);
            check(unrelatedSpeech.isEmpty(), "master at the same coordinates on another level does not speak");
            check(sent(player,REQUIREMENT)&&sent(player,WARNING), "other-level master does not hide fallback requirement and danger");
            move(player,at(339,614,0));Npc npc=h.npc(NpcId.MASTER_CRAFTER.id(),348,606);
            List<String> speech=dispatch(h,player,down,"go down","MasterCrafter",npc);
            check(player.getWorldLocation().equals(at(339,614,0)), "master refuses underlevel entry without moving player");
            check(speech.contains(REQUIREMENT)&&speech.contains(WARNING), "master speaks level requirement and danger warning");
            check(master.blockTalkNpc(player,npc), "existing master Talk-to ownership retained");
            System.out.println("PASS Crafting Guild cave access: " + checks + " boundary, trigger, dialogue and ordinary stair assertions");
        }
    }
}
