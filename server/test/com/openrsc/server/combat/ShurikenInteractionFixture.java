package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.states.HostileState;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.GameObjectAction;
import com.openrsc.server.net.rsc.handlers.GroundItemTake;
import com.openrsc.server.net.rsc.handlers.NpcTalkTo;
import com.openrsc.server.net.rsc.handlers.WalkRequest;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.net.rsc.struct.incoming.TargetObjectStruct;
import com.openrsc.server.net.rsc.struct.incoming.TargetPositionStruct;
import com.openrsc.server.net.rsc.struct.incoming.WalkStruct;
import com.openrsc.server.plugins.Functions;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import java.util.ArrayList;
import java.util.List;

/** Real client packet order, movement, action dispatch and plugin lifecycle; no live data or sockets. */
public final class ShurikenInteractionFixture {
    private static int checks;
    private static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<String>();
        try (CurrentCombatHarness h = new CurrentCombatHarness(); PvpSqliteLog logs = new PvpSqliteLog(h.server())) {
            h.installDefaultPlugin(Class.forName("com.openrsc.server.plugins.authentic.defaults.Default").newInstance());
            h.installPlugin(TalkNpcTrigger.class, new Conversation());
            for (boolean defeated : new boolean[]{false, true}) for (String interaction : new String[]{"pickup", "door", "talk"}) {
                String label = interaction + (defeated ? " after target removal" : " while throwing");
                int logged = logs.deathCount();
                try {
                    scenario(h, interaction, defeated);
                    check(logs.deathCount() - logged == (interaction.equals("pickup") ? 1 : 0), "pickup logging completed in isolated SQLite");
                    System.out.println("PASS " + label);
                } catch (AssertionError failure) {
                    failures.add(label + ": " + failure.getMessage());
                    System.out.println("FAIL " + label + ": " + failure.getMessage());
                }
            }
            guards(h);
        }
        System.out.println("Shuriken interaction checks=" + checks + " failures=" + failures.size());
        if (!failures.isEmpty()) throw new AssertionError(failures.toString());
    }

    private static void scenario(CurrentCombatHarness h, String interaction, boolean defeated) throws Exception {
        h.openCombatProjectileRectangle(599, 606, 646, 651);
        Player player = h.player("interact", 600, 650);
        player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
        player.getCache().store("setting_auto_retaliate", true);
        h.equip(player, ItemId.TIN_SHURIKEN.id(), 20);
        h.recordOutgoingPackets(player);
        Npc enemy = h.npc(NpcId.MOSS_GIANT.id(), 600, 647);
        GroundItem drop = null;
        GameObject door = null;
        Npc speaker = null;
        try {
            enemy.startCombat(player);
            ThrowingEvent throwing = player.getThrowingEvent();
            check(throwing != null && player.isHostile(), "real NPC engagement starts hostile shuriken retaliation");
            throwing.run();
            check(player.getCarriedItems().getEquipment().get(player.getCarriedItems().getEquipment()
                .searchEquipmentForItem(ItemId.TIN_SHURIKEN.id())).getAmount() < 20, "fixture actually throws shuriken");
            PvmMeleeEvent incoming = enemy.getPvmMeleeEvent();
            check(incoming != null && incoming.isRunning(), "NPC owns an independent counterattack");
            if (defeated) {
                enemy.getSkills().setLevel(Skill.HITS.id(), 0, false);
                h.world().unregisterNpc(enemy);
                player.setAttribute("can_range_again", 0L);
                throwing.run();
                check(!player.isRanging() && !player.inCombat(), "target removal finishes the throwing event");
                check(player.isHostile(), "post-kill stale hostility reproduces the reported state");
            }

            if (interaction.equals("pickup")) {
                drop = new GroundItem(h.world(), ItemId.COINS.id(), 603, 650, 1, player);
                h.world().registerItem(drop);
            } else if (interaction.equals("door")) {
                door = new GameObject(h.world(), Point.location(604, 650), SceneryId.DOOR_BANK_CLOSED.id(), 0, 0);
                h.world().registerGameObject(door);
            } else {
                speaker = h.npc(NpcId.BANKER.id(), 604, 650);
            }
            Point start = player.getLocation();
            walk(player, OpcodeIn.WALK_TO_ENTITY, Point.location(603, 650));
            check(!player.finishedPath(), "interaction approach packet is accepted without a preliminary ground click");
            check(!player.isHostile() && !player.isRanging(), "interaction walk releases outgoing attack and hostility");
            check(player.getLocation().equals(start), "packet queues movement rather than teleporting");
            if (!defeated) check(enemy.getPvmMeleeEvent() == incoming && incoming.isRunning(), "interaction never stops the incoming NPC attack");
            if (drop != null) {
                TargetPositionStruct click = new TargetPositionStruct();
                click.setOpcode(OpcodeIn.GROUND_ITEM_TAKE); click.coordinate = drop.getLocation(); click.itemId = drop.getID();
                new GroundItemTake().process(click, player);
            } else if (door != null) {
                TargetObjectStruct click = new TargetObjectStruct();
                click.setOpcode(OpcodeIn.OBJECT_COMMAND); click.coordObject = door.getLocation();
                new GameObjectAction().process(click, player);
            } else {
                TargetMobStruct click = new TargetMobStruct();
                click.setOpcode(OpcodeIn.NPC_TALK_TO); click.serverIndex = speaker.getIndex();
                new NpcTalkTo().process(click, player);
            }
            WalkToAction action = player.getWalkToAction();
            check(action != null, "interaction handler creates its real walk-to action");
            for (int step = 0; step < 8 && !action.isExecuted(); step++) {
                player.getWalkingQueue().processNextMovement();
                h.server().getGameUpdater().executeWalkToActions(player);
            }
            check(action.isExecuted(), "single interaction click walks to and activates its target");
            for (int tick = 0; tick < 8; tick++) h.advanceOneCombatTick();
            if (drop != null) check(drop.isRemoved() && player.getCarriedItems().getInventory().countId(ItemId.COINS.id()) == 1,
                "real default pickup adds the drop exactly once");
            if (door != null) check(player.getViewArea().getGameObject(door.getLocation()).getID() == SceneryId.DOOR_BANK_OPEN.id(),
                "real default door handler opens the approached door");
            if (speaker != null) check(speaker.getUpdateFlags().getChatMessage() != null
                && speaker.getUpdateFlags().getChatMessage().getMessageString().equals("Hello there."), "NPC conversation produces real speech");
            check(!player.isBusy(), "interaction script releases busy state");
            check(!player.isCurrentThrowingEvent(throwing), "old shuriken event never resumes after interaction");
        } finally {
            if (drop != null && !drop.isRemoved()) h.world().unregisterItem(drop);
            if (door != null) {
                GameObject current = player.getViewArea().getGameObject(door.getLocation());
                if (current != null) h.world().unregisterGameObject(current);
            }
            h.logout(player);
            if (speaker != null && !speaker.isRemoved()) h.world().unregisterNpc(speaker);
            if (!enemy.isRemoved()) h.world().unregisterNpc(enemy);
            for (GameTickEvent event : new ArrayList<GameTickEvent>(h.server().getGameEventHandler().getEvents())) event.stop();
            h.server().getGameEventHandler().cleanupEvents();
        }
    }

    private static void guards(CurrentCombatHarness h) throws Exception {
        h.openCombatProjectileRectangle(599, 606, 646, 651);
        Player player = h.player("walk guards", 600, 650);
        Npc enemy = h.npc(NpcId.MOSS_GIANT.id(), 600, 647);
        player.setHostile(enemy, HostileState.HostilityType.ATTACKED);
        h.server().getConfig().WANT_MYWORLD = false;
        walk(player, OpcodeIn.WALK_TO_ENTITY, Point.location(603, 650));
        check(player.finishedPath() && player.isHostile(), "legacy combat still rejects interaction walking");
        h.server().getConfig().WANT_MYWORLD = true;
        player.clearHostility(); player.setBusy(true);
        walk(player, OpcodeIn.WALK_TO_ENTITY, Point.location(603, 650));
        check(player.finishedPath() && player.isBusy(), "genuinely busy player remains blocked");
        player.setBusy(false); player.setHostile(enemy, HostileState.HostilityType.ATTACKED);
        walk(player, OpcodeIn.WALK_TO_POINT, Point.location(603, 650));
        check(!player.finishedPath() && !player.isHostile(), "ordinary ground walking retains its existing behavior");
    }

    private static void walk(Player player, OpcodeIn opcode, Point destination) throws Exception {
        WalkStruct movement = new WalkStruct(); movement.setOpcode(opcode); movement.firstStep = destination;
        new WalkRequest().process(movement, player);
    }

    public static final class Conversation implements TalkNpcTrigger {
        public boolean blockTalkNpc(Player player, Npc npc) { return npc.getID() == NpcId.BANKER.id(); }
        public void onTalkNpc(Player player, Npc npc) { Functions.npcsay(player, npc, "Hello there."); }
    }
}
