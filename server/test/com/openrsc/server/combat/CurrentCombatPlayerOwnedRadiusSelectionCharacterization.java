package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.PlayerOwnedNpcRadiusSelection;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Executable A07.3 player-owned NPC radius-selection specification. */
final class CurrentCombatPlayerOwnedRadiusSelectionCharacterization {
	private CurrentCombatPlayerOwnedRadiusSelectionCharacterization() {
	}

	static void ordinarySplashAndTerminalBurstPolicies(
			final CurrentCombatHarness harness) {
		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			characterizePrimaryCenteredSelection(harness);
			characterizeFixedCenterAndSuppression(harness);
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
	}

	private static void characterizePrimaryCenteredSelection(
			final CurrentCombatHarness harness) {
		final int y = 810;
		harness.openRectangle(840, 848, y, y + 3);
		final Player owner = harness.player("radius splash", 840, y);
		final Npc primary = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			841, y, 20);
		final Npc first = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			843, y, 20);
		final Npc second = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			842, y + 1, 20);
		final Npc distant = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			847, y, 20);
		final Npc nonAttackable = npcWithHits(harness, NpcId.BOB.id(),
			842, y + 2, 20);
		final Npc dead = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			841, y + 1, 0);
		final Npc summon = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			840, y + 1, 20);
		summon.setAttribute("myworld_summon_owner", owner.getUsernameHash());
		final Npc otherLevel = npcWithHits(harness,
			NpcId.GREATER_DEMON.id(), 843,
			LegacyPackedPointAdapter.LEVEL_STRIDE + y, 20);

		harness.world().getTile(842, y).projectileAllowed = false;
		harness.world().getTile(842, y).traversalMask =
			(byte) CollisionFlag.FULL_BLOCK;
		assertFalse(PathValidation.checkPath(harness.world(),
			primary.getWorldLocation(), first.getWorldLocation(), false),
			"fixture must place hard cover between center and recipient");

		final List<Npc> expectedViewOrder = new ArrayList<>();
		for (Npc candidate : owner.getViewArea().getNpcsInView()) {
			if (candidate == first || candidate == second) {
				expectedViewOrder.add(candidate);
			}
		}
		assertEquals(2, expectedViewOrder.size(),
			"fixture exposes both valid recipients");

		final PlayerOwnedNpcRadiusSelection selection =
			PlayerOwnedNpcRadiusSelection.aroundPrimary(owner, primary, 2);
		final List<Npc> snapshot = selection.snapshotViewOrder();
		assertIdentityOrder(expectedViewOrder, snapshot,
			"snapshot preserves view order and current filters");
		assertFalse(snapshot.contains(primary), "primary target is excluded");
		assertFalse(snapshot.contains(distant), "radius excludes distant NPC");
		assertFalse(snapshot.contains(nonAttackable),
			"non-attackable NPC is excluded");
		assertFalse(snapshot.contains(dead), "dead NPC is excluded");
		assertFalse(snapshot.contains(summon), "summon NPC is excluded");
		assertFalse(snapshot.contains(otherLevel),
			"view membership excludes another map level");
		assertTrue(snapshot.contains(first),
			"ordinary radius selection intentionally ignores walls");

		final List<Npc> compatibility = CombatEffectUtil
			.findPlayerOwnedNpcSplashTargets(owner, primary, 2);
		assertIdentityOrder(snapshot, compatibility,
			"legacy utility facade retains eager snapshot semantics");

		final Iterator<Npc> live = PlayerOwnedNpcRadiusSelection
			.aroundPrimary(owner, primary, 2).iterator();
		final Npc yielded = live.next();
		final Npc later = yielded == first ? second : first;
		later.getSkills().setLevel(Skill.HITS.id(), 0);
		final List<Npc> remaining = drain(live);
		assertFalse(remaining.contains(later),
			"lazy iteration revalidates a later candidate after child work");
		assertTrue(snapshot.contains(later),
			"an eager snapshot is unaffected by later candidate state");

		later.getSkills().setLevel(Skill.HITS.id(), 20);
		final Iterator<Npc> movingCenter = PlayerOwnedNpcRadiusSelection
			.aroundPrimary(owner, primary, 2).iterator();
		movingCenter.next();
		primary.setLocation(Point.location(848, y));
		final List<Npc> afterCenterMove = drain(movingCenter);
		assertEquals(1, afterCenterMove.size(),
			"primary-centered selection re-evaluates its moving center");
		assertTrue(afterCenterMove.get(0) == distant,
			"moving center can make a later view candidate eligible");
	}

	private static void characterizeFixedCenterAndSuppression(
			final CurrentCombatHarness harness) {
		final int y = 830;
		harness.openRectangle(860, 878, y, y + 3);
		final Player owner = harness.player("radius burst", 860, y);
		final Npc killed = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			861, y, 0);
		final Npc first = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			862, y, 20);
		final Npc second = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			861, y + 1, 20);
		final Point center = owner.getLocation();
		final Iterator<Npc> fixed = PlayerOwnedNpcRadiusSelection
			.aroundFixedPoint(owner, center, killed, 2).iterator();
		final Npc yielded = fixed.next();
		owner.setLocation(Point.location(878, y));
		final List<Npc> remaining = drain(fixed);
		assertEquals(1, remaining.size(),
			"fixed-center terminal selection retains later recipient");
		assertTrue(remaining.get(0) == (yielded == first ? second : first),
			"fixed-center terminal selection retains captured center");

		final Player guarded = harness.player("radius guarded", 870, y);
		final Npc guardedPrimary = npcWithHits(harness,
			NpcId.GREATER_DEMON.id(), 871, y, 20);
		npcWithHits(harness, NpcId.GREATER_DEMON.id(), 872, y, 20);
		installGuardDog(harness, guarded, 869, y + 1);
		assertTrue(PlayerOwnedNpcRadiusSelection
			.aroundPrimary(guarded, guardedPrimary, 2)
			.snapshotViewOrder().isEmpty(),
			"Guard Dog suppresses the complete player-owned selection");
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int npcId, final int x, final int y, final int hits) {
		final Npc npc = harness.npc(npcId, x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, Math.max(1, hits), false);
		return npc;
	}

	private static void installGuardDog(final CurrentCombatHarness harness,
			final Player owner, final int x, final int y) {
		final Npc guard = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			x, y, 20);
		guard.setAttribute("myworld_summon_owner", owner.getUsernameHash());
		guard.setAttribute("myworld_summon_kind", "guard_dog");
		guard.setAttribute("myworld_summon_current_hits", 20);
		owner.setAttribute("myworld_manual_summon", guard);
		assertTrue(Summoning.isPlayerAreaEffectSuppressed(owner),
			"guard-dog fixture activates area-effect suppression");
	}

	private static List<Npc> drain(final Iterator<Npc> iterator) {
		final List<Npc> results = new ArrayList<>();
		while (iterator.hasNext()) {
			results.add(iterator.next());
		}
		return results;
	}

	private static void assertIdentityOrder(final List<Npc> expected,
			final List<Npc> actual, final String message) {
		assertEquals(expected.size(), actual.size(), message + " size");
		for (int index = 0; index < expected.size(); index++) {
			assertTrue(expected.get(index) == actual.get(index),
				message + " at index " + index);
		}
	}

	private static void assertTrue(final boolean condition,
			final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition,
			final String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(final int expected, final int actual,
			final String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected=" + expected
				+ " actual=" + actual);
		}
	}
}
