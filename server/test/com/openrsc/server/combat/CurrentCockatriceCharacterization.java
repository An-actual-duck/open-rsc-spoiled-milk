package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.monsterslayer.*;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcAttackStyleProfile;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.net.Packet;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import java.util.ArrayList;
import java.util.List;

/** Deterministic encounter coverage using the real scheduler, inventory and combat. */
public final class CurrentCockatriceCharacterization {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			Npc bird = h.npc(864, 440, 440);
			Player p = h.player("glare victim", 441, 440);
			h.recordOutgoingPackets(p);
			check(bird.getMeleeOffense() == 40 && bird.getMeleeDefense() == 35
				&& bird.getRangedDefense() == 40 && bird.getMagicDefense() == 30, "explicit modern stats");
			check(bird.getDef().getAtt() == 1 && bird.getDef().getStr() == 1, "legacy placeholders are not offense");
			check(NpcAttackStyleProfile.forNpc(bird) == NpcAttackStyleProfile.MELEE, "melee only");
			CockatriceCombat.onMeleeSwing(bird, p, true);
			check(!CockatriceCombat.attacksBlocked(p), "suppressed swing cannot glare");
			PvmMeleeEvent event = new PvmMeleeEvent(h.world(), bird, p);
			bird.setPvmMeleeEvent(event);
			event.run();
			check(CockatriceCombat.attacksBlocked(p), "real melee swing applies glare");
			check(event.getDelayTicks() * h.server().getConfig().GAME_TICK < CockatriceCombat.GLARE_MILLIS,
				"ordinary attack cadence refreshes before arm expiry");
			check(CockatriceCombat.movementBlocked(p), "legs initially locked");
			p.walk(442, 440);
			p.getWalkingQueue().processNextMovement();
			check(p.getX() == 441 && !p.finishedPath(), "frozen movement preserves queued escape");
			CockatriceCombat.onMeleeSwing(bird, p, false);
			for (CombatStyle style : CombatStyle.values()) for (CombatEligibilityPhase phase : CombatEligibilityPhase.values()) {
				if (phase == CombatEligibilityPhase.COMPATIBILITY) continue;
				check(CombatEligibility.evaluate(CombatEligibilityRequest.builder(p, bird, phase, style).build())
					.getReason() == CombatEligibilityReason.SOURCE_STONY_GLARE, "all attack styles gated");
			}
			h.advanceOneCombatTick();
			check(!CockatriceCombat.movementBlocked(p) && CockatriceCombat.attacksBlocked(p), "one tick releases only legs");
			check(messages(p, CockatriceCombat.RELEASE_MESSAGE) == 0, "no early release in application tick");
			h.advanceOneCombatTick();
			check(messages(p, CockatriceCombat.RELEASE_MESSAGE) == 1, "exact flavor text once despite simultaneous glare");
			h.advanceOneCombatTick();
			check(messages(p, CockatriceCombat.RELEASE_MESSAGE) == 1, "no repeated release text");
			List<ActiveStatusEntry> rows = new ArrayList<>();
			CockatriceCombat.onMeleeSwing(bird, p, false);
			CockatriceCombat.appendStatuses(p, rows);
			check(rows.size() == 1 && rows.get(0).getRemainingSeconds() == 10, "glare HUD timer");
			h.clock().advanceMillis(9_000);
			CockatriceCombat.onMeleeSwing(bird, p, false);
			h.clock().advanceMillis(9_999);
			check(CockatriceCombat.attacksBlocked(p), "refresh sustains lock");
			h.clock().advanceMillis(1);
			check(!CockatriceCombat.attacksBlocked(p) && !CockatriceCombat.movementBlocked(p), "exact expiry");
			System.out.println("PASS Cockatrice melee integration, modern stats, one-tick release, refresh and attack admission");
			doses(h, bird);
		}
	}
	private static void doses(CurrentCombatHarness h, Npc bird) throws Exception {
		Player p = h.player("eye drops", 441, 441);
		h.recordOutgoingPackets(p);
		p.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		OpInvTrigger drops = (OpInvTrigger) Class.forName("com.openrsc.server.plugins.custom.myworld.itemactions.EyeDrops").newInstance();
		OpInvTrigger generic = (OpInvTrigger) Class.forName("com.openrsc.server.plugins.authentic.itemactions.Drinkables").newInstance();
		Item absent = new Item(3321);
		drops.onOpInv(p, 0, absent, "Apply");
		check(!CockatriceCombat.protectedByEyeDrops(p), "absent item cannot protect");
		p.getCarriedItems().getInventory().add(absent);
		while (p.getCarriedItems().getInventory().size() < p.getCarriedItems().getInventory().getCapacity())
			check(p.getCarriedItems().getInventory().add(new Item(3318)), "fill inventory");
		long identity = p.getCarriedItems().getInventory().get(0).getItemId();
		CockatriceCombat.onMeleeSwing(bird, p, false);
		Npc frog = h.npc(863, 442, 441);
		GiantFrogCombat.onSpitImpact(frog, p, 1);
		for (int expected : new int[]{3322, 3323, ItemId.EMPTY_VIAL.id()}) {
			Item bottle = p.getCarriedItems().getInventory().get(0);
			check(drops.blockOpInv(p, 0, bottle, "Apply") && !generic.blockOpInv(p, 0, bottle, "Apply"), "one plugin owns apply");
			java.lang.reflect.Method allowed = com.openrsc.server.net.rsc.handlers.ItemActionHandler.class
				.getDeclaredMethod("isCombatConsumableAction", Item.class, String.class, Player.class);
			allowed.setAccessible(true);
			check((Boolean) allowed.invoke(new com.openrsc.server.net.rsc.handlers.ItemActionHandler(), bottle, "Apply", p), "apply allowed during combat");
			drops.onOpInv(p, 0, bottle, "Apply");
			check(p.getCarriedItems().getInventory().get(0).getCatalogId() == expected, "3 to 2 to 1 to vial");
			check(p.getCarriedItems().getInventory().get(0).getItemId() == identity, "same bottle identity/slot");
			check(CockatriceCombat.protectedByEyeDrops(p) && !CockatriceCombat.attacksBlocked(p)
				&& !CockatriceCombat.movementBlocked(p), "counter cures both locks");
			check(GiantFrogCombat.attacksBlocked(p) && p.getCurrentPoisonPower() == 10, "other effects remain");
			CockatriceCombat.onMeleeSwing(bird, p, false);
			check(!CockatriceCombat.attacksBlocked(p), "protected glare ignored");
			h.clock().advanceMillis(1_000);
			drops.onOpInv(p, 0, bottle, "Apply");
			List<ActiveStatusEntry> rows = new ArrayList<>();
			CockatriceCombat.appendStatuses(p, rows);
			check(rows.size() == 1 && rows.get(0).getRemainingSeconds() == 599, "stale action cannot refresh or consume");
		}
		check(!drops.blockOpInv(p, 0, p.getCarriedItems().getInventory().get(0), "Apply"), "no fourth use");
		h.advanceOneCombatTick();
		check(messages(p, CockatriceCombat.RELEASE_MESSAGE) == 0, "cure suppresses stale release text");
		h.clock().advanceMillis(600_000);
		CockatriceCombat.onMeleeSwing(bird, p, false);
		check(CockatriceCombat.attacksBlocked(p), "protection expires");
		GiantFrogCombat.applySolvent(p);
		check(CockatriceCombat.attacksBlocked(p), "solvent does not cure glare");
		System.out.println("PASS Eye Drops three uses, full inventory, stale actions, immunity, expiry, effect isolation");
	}
	@SuppressWarnings("unchecked")
	private static int messages(Player p, String text) throws Exception {
		List<Packet> packets = (List<Packet>) CurrentCombatHarness.readPrivateField(p, "outgoingPackets");
		int count = 0;
		for (Packet packet : packets) if (packet.getBuffer().toString(java.nio.charset.StandardCharsets.ISO_8859_1).contains(text)) count++;
		return count;
	}
	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
