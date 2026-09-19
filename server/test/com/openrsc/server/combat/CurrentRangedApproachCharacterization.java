package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.CollisionFlag;

/** Exercises actual projectile events and their authoritative walking queue. */
final class CurrentRangedApproachCharacterization {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			for (int npcId : new int[]{865, 3}) {
				for (boolean throwing : new boolean[]{false, true}) {
					approach(h, npcId, throwing);
				}
			}
			System.out.println("PASS ranged approach: Banshee and ordinary NPC, bow and throwing, range stop and blocked path cleanup");
		}
	}

	private static void approach(CurrentCombatHarness h, int npcId, boolean throwing) throws Exception {
		h.openCombatProjectileRectangle(440, 452, 440, 452);
		Player p = h.player("approach" + npcId + throwing, 440, 440);
		Npc target = h.npc(npcId, 451, 451);
		int weapon = throwing ? ItemId.BRONZE_THROWING_KNIFE.id() : ItemId.SHORTBOW.id();
		h.equip(p, weapon, throwing ? 50 : 1);
		if (!throwing) h.equip(p, ItemId.TIN_ARROWS.id(), 50);
		int radius = throwing ? RangeUtils.getThrowingAttackRadius(weapon) : RangeUtils.getBowAttackRadius(weapon);
		// The direct diagonal is blocked, but the actual walker can slide along X.
		h.world().getTile(441, 441).traversalMask = CollisionFlag.FULL_BLOCK;
		check(PathValidation.checkAdjacent(p, 440, 440, 441, 440), "authoritative step is open");
		check(p.nextStep(440, 440, target) == null, "legacy check incorrectly rejects open step");
		RangeEvent bow = new RangeEvent(h.world(), p, 0, target);
		ThrowingEvent thrown = new ThrowingEvent(h.world(), p, 0, target);
		if (throwing) p.setThrowingEvent(thrown); else p.setRangeEvent(bow);
		if (throwing) thrown.run(); else bow.run();
		check(throwing ? p.getThrowingEvent() == thrown : p.getRangeEvent() == bow, "open approach keeps attack alive");
		check(!p.finishedPath(), "open approach queues movement");
		check(p.getAttribute("can_range_again", 0L) == 0L, "no shot before reaching range");
		check(p.getProjectileRadius() == radius, "request and actual weapon range agree");
		// Step the real queue until the event stops it to attack, not to melee.
		for (int i = 0; i < 15 && !p.finishedPath(); i++) {
			p.getWalkingQueue().processNextMovement();
			if (throwing) thrown.run(); else bow.run();
		}
		check(p.finishedPath(), "movement stops in firing range");
		check(p.withinRange(target, radius) && !p.withinRange(target, 1), "stops outside melee reach");
		check(p.getAttribute("can_range_again", 0L) > h.server().getCurrentTick(), "actually launches a shot after approach");
		p.resetRange();
		h.logout(p);
		target.remove();

		h.openCombatProjectileRectangle(460, 472, 440, 441);
		Player blocked = h.player("blocked" + npcId + throwing, 460, 440);
		Npc blockedTarget = h.npc(npcId, 471, 440);
		h.equip(blocked, weapon, 50);
		h.world().getTile(461, 440).traversalMask = CollisionFlag.FULL_BLOCK;
		// There may already be a client-supplied walk when the event fails.
		blocked.walkToEntity(471, 440);
		if (throwing) {
			ThrowingEvent event = new ThrowingEvent(h.world(), blocked, 0, blockedTarget);
			blocked.setThrowingEvent(event);
			event.run();
			check(blocked.getThrowingEvent() == null, "blocked throw cancels");
		} else {
			RangeEvent event = new RangeEvent(h.world(), blocked, 0, blockedTarget);
			blocked.setRangeEvent(event);
			event.run();
			check(blocked.getRangeEvent() == null, "blocked bow cancels");
		}
		check(blocked.finishedPath(), "failed attack cannot leave a walk to the enemy queued");
		h.logout(blocked);
		blockedTarget.remove();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
