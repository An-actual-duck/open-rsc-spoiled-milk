package com.openrsc.server.combat;

import com.openrsc.server.content.monsterslayer.GiantFrogCombat;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcAttackStyleProfile;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.combat.*;
import java.util.ArrayList;
import java.util.List;

/** Deterministic timing, source-specific immunity and explicit-stat regression. */
final class CurrentGiantFrogCharacterization {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
			effectsAndStats(harness);
			System.out.println("PASS giant frog timing, immunity, stats and combat admission");
			Npc frog = harness.npc(863, 440, 440);
			Player player = harness.player("frog range", 443, 440);
			for (int x = 440; x <= 443; x++) harness.openTile(x, 440);
			frog.setChasing(player);
			frog.setCombatTimer();
			java.lang.reflect.Method attempt = frog.getBehavior().getClass()
				.getDeclaredMethod("tryProjectileAttack", long.class);
			attempt.setAccessible(true);
			check((Boolean) attempt.invoke(frog.getBehavior(), harness.clock().currentTimeMillis()), "cooldown must hold ranged position");
			check(frog.finishedPath(), "cooldown must not path to melee");
			harness.clock().advanceMillis(10_000);
			check((Boolean) attempt.invoke(frog.getBehavior(), harness.clock().currentTimeMillis()), "in-range frog shoots");
			check(frog.finishedPath(), "shot must not path to melee");
			System.out.println("PASS giant frog in-range shooting and cooldown positioning");
			Player drinker = harness.player("frog drink", 446, 440);
			drinker.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			com.openrsc.server.model.container.Item bottle = new com.openrsc.server.model.container.Item(3318);
			Object plugin = Class.forName("com.openrsc.server.plugins.custom.myworld.itemactions.SlimeSolvent").newInstance();
			com.openrsc.server.plugins.triggers.OpInvTrigger solventHandler =
				(com.openrsc.server.plugins.triggers.OpInvTrigger) plugin;
			com.openrsc.server.plugins.triggers.OpInvTrigger genericDrinkHandler =
				(com.openrsc.server.plugins.triggers.OpInvTrigger) Class.forName(
					"com.openrsc.server.plugins.authentic.itemactions.Drinkables").newInstance();
			check(solventHandler.blockOpInv(drinker, 0, bottle, "Drink"), "solvent owns drink action");
			check(!genericDrinkHandler.blockOpInv(drinker, 0, bottle, "Drink"), "generic drink handler must not also run for solvent");
			check(genericDrinkHandler.blockOpInv(drinker, 0,
				new com.openrsc.server.model.container.Item(com.openrsc.server.constants.ItemId.BEER.id()), "Drink"),
				"ordinary drinks retain generic handler");
			check("Slimy frog spit begone!".equals(bottle.getDef(harness.world()).getDescription()), "solvent examine flavor");
			java.lang.reflect.Method drink = plugin.getClass().getMethod("onOpInv", Player.class,
				Integer.class, com.openrsc.server.model.container.Item.class, String.class);
			drink.invoke(plugin, drinker, 0, bottle, "Drink");
			check(!GiantFrogCombat.protectedBySolvent(drinker), "absent item cannot grant immunity");
			check(drinker.getCarriedItems().getInventory().add(bottle), "fixture accepts solvent item");
			drink.invoke(plugin, drinker, 0, bottle, "Drink");
			check(GiantFrogCombat.protectedBySolvent(drinker), "owned bottle grants immunity");
			check(drinker.getCarriedItems().getInventory().countId(3318) == 0, "bottle consumed exactly once");
			harness.clock().advanceMillis(1_000);
			drink.invoke(plugin, drinker, 0, bottle, "Drink");
			List<ActiveStatusEntry> rows = new ArrayList<>();
			GiantFrogCombat.appendStatuses(drinker, rows);
			check(rows.get(0).getRemainingSeconds() == 599, "replayed stale action cannot refresh immunity");
			System.out.println("PASS Slime Solvent inventory consumption and stale-action rejection");
		}
	}
	static void effectsAndStats(CurrentCombatHarness harness) {
		Npc frog = harness.npc(863, 440, 440);
		Player player = harness.player("frog victim", 443, 440);
		check(NpcAttackStyleProfile.forNpc(frog) == NpcAttackStyleProfile.PURE_RANGED, "ranged-only profile");
		check(frog.getRangedOffense() == 30, "explicit ranged offense");
		check(frog.getMeleeDefense() == 10 && frog.getRangedDefense() == 20
			&& frog.getMagicDefense() == 35, "explicit ordered defenses");
		check(frog.getDef().getAtt() == 1 && frog.getDef().getDef() == 1, "legacy stats are not the modern values");
		GiantFrogCombat.onSpitImpact(frog, player, 0);
		check(!GiantFrogCombat.attacksBlocked(player) && player.getCurrentPoisonPower() == 0, "miss has no effect");
		GiantFrogCombat.onSpitImpact(frog, player, 1);
		check(GiantFrogCombat.attacksBlocked(player), "spit blocks attacks");
		for (CombatStyle style : CombatStyle.values()) {
			for (CombatEligibilityPhase phase : CombatEligibilityPhase.values()) {
				if (phase == CombatEligibilityPhase.COMPATIBILITY) continue;
				check(CombatEligibility.evaluate(CombatEligibilityRequest.builder(player, frog, phase, style).build())
					.getReason() == CombatEligibilityReason.SOURCE_SLIMED, "all attack styles blocked at admission and commit");
			}
		}
		check(player.getCurrentPoisonPower() == 10, "small frog poison");
		List<ActiveStatusEntry> statuses = new ArrayList<>();
		GiantFrogCombat.appendStatuses(player, statuses);
		check(statuses.size() == 1 && statuses.get(0).getRemainingSeconds() == 10, "10-second HUD timer");
		harness.clock().advanceMillis(9_000);
		GiantFrogCombat.onSpitImpact(frog, player, 1);
		harness.clock().advanceMillis(9_999);
		check(GiantFrogCombat.attacksBlocked(player), "reapplication refreshes rather than stacks");
		harness.clock().advanceMillis(1);
		check(!GiantFrogCombat.attacksBlocked(player), "exact expiry");
		GiantFrogCombat.onSpitImpact(frog, player, 1);
		GiantFrogCombat.applySolvent(player);
		check(!GiantFrogCombat.attacksBlocked(player) && player.getCurrentPoisonPower() == 10, "solvent clears attack lock but preserves existing frog poison");
		player.curePoison();
		harness.server().getGameEventHandler().cleanupEvents();
		GiantFrogCombat.onSpitImpact(frog, player, 1);
		check(!GiantFrogCombat.attacksBlocked(player) && player.getCurrentPoisonPower() == 10, "solvent blocks attack lock but allows fresh frog poison");
		statuses.clear();
		GiantFrogCombat.appendStatuses(player, statuses);
		check(statuses.size() == 1 && statuses.get(0).getRemainingSeconds() == 600, "600-second solvent HUD");
		harness.clock().advanceMillis(599_999);
		check(GiantFrogCombat.protectedBySolvent(player), "solvent active until expiry");
		harness.clock().advanceMillis(1);
		check(!GiantFrogCombat.protectedBySolvent(player), "solvent expires at ten minutes");
		GiantFrogCombat.onSpitImpact(frog, player, 1);
		check(GiantFrogCombat.attacksBlocked(player) && player.getCurrentPoisonPower() == 10, "effects return after expiry");
		player.curePoison();
		harness.server().getGameEventHandler().cleanupEvents();
		Npc other = harness.npc(9, 444, 440);
		player.applyPoison(40, 40, other);
		GiantFrogCombat.applySolvent(player);
		check(player.getCurrentPoisonPower() == 40, "solvent is not a general antipoison");
	}
	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
