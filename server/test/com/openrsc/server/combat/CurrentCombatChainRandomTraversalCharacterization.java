package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Executable pre-migration A07.4 chain and random-single specifications. */
final class CurrentCombatChainRandomTraversalCharacterization {
	private static final int DRAGONSTONE_CHAOS_NECKLACE = 1652;

	private CurrentCombatChainRandomTraversalCharacterization() {
	}

	static void chainTraversalPolicies(final CurrentCombatHarness harness)
			throws Exception {
		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			chainRevisitsAndCap(harness);
			chainContinuesFromDeadChild(harness);
			chainEmptySelectionDrawOrder(harness);
			chainSelectionBoundaries(harness);
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
	}

	static void splinterSelectionPolicies(final CurrentCombatHarness harness)
			throws Exception {
		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			splinterProcAndSelectionDrawOrder(harness);
			splinterEmptySelectionDrawOrder(harness);
			splinterSelectionBoundaries(harness);
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
	}

	private static void chainRevisitsAndCap(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player source = harness.player("chain cycle", 100, 850);
		harness.equip(source, DRAGONSTONE_CHAOS_NECKLACE, 1);
		final Npc primary = npcWithHits(harness, 101, 850, 20);
		final Npc child = npcWithHits(harness, 102, 850, 20);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), source, primary);
		harness.random().reset(0xA074L);
		harness.random().scriptDoubles(0.0D, 0.0D, 0.0D, 0.0D)
			.scriptInts(0, 0, 0, 0);
		invoke(event, "applyChaosAmuletChainLightning",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			source, primary, Integer.valueOf(16));

		final List<DamageResult> results = results(harness);
		assertEquals(3, results.size(), "chain uses its exact three-hop cap");
		assertTarget(results.get(0), child, 8,
			"first chain hop selects the only non-anchor child");
		assertTarget(results.get(1), primary, 4,
			"second chain hop may revisit the original primary");
		assertTarget(results.get(2), child, 2,
			"third chain hop may revisit the first child");
		assertEquals(16, primary.getLevel(Skill.HITS.id()),
			"primary receives the repeated-target hop");
		assertEquals(10, child.getLevel(Skill.HITS.id()),
			"child receives both first and third hops");
		assertEquals(
			"seed=41076 draws=[double=0.0, int(1)=0, double=0.0, "
				+ "int(1)=0, double=0.0, int(1)=0]",
			harness.random().describeState(),
			"each capped hop draws chance before one target index");
	}

	private static void chainContinuesFromDeadChild(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player source = harness.player("chain death", 120, 850);
		harness.equip(source, DRAGONSTONE_CHAOS_NECKLACE, 1);
		final Npc primary = npcWithHits(harness, 121, 850, 20);
		final Npc child = npcWithHits(harness, 122, 850, 1);
		child.setShouldRespawn(false);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), source, primary);
		harness.random().reset(0xA075L);
		harness.random().scriptDoubles(
			0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D)
			.scriptInts(0, 0, 0, 0, 0, 0);
		invoke(event, "applyChaosAmuletChainLightning",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			source, primary, Integer.valueOf(16));

		final List<DamageResult> results = results(harness);
		assertEquals(2, results.size(),
			"dead child is unavailable when traversal returns to primary");
		assertTarget(results.get(0), child, 8,
			"first hop settles lethal child damage");
		assertTrue(results.get(0).isTargetTerminal(),
			"first hop records terminal child result");
		assertTarget(results.get(1), primary, 4,
			"traversal continues from the dead child's location");
		assertTrue(harness.random().describeState().contains("int(130)=0"),
			"lethal child callbacks retain their RNG position before traversal resumes");
	}

	private static void chainEmptySelectionDrawOrder(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player source = harness.player("chain empty", 130, 850);
		harness.equip(source, DRAGONSTONE_CHAOS_NECKLACE, 1);
		final Npc primary = npcWithHits(harness, 131, 850, 20);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), source, primary);
		harness.random().reset(0xA07CL);
		harness.random().scriptDoubles(0.0D).scriptInts(0);
		invoke(event, "applyChaosAmuletChainLightning",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			source, primary, Integer.valueOf(16));
		assertTrue(results(harness).isEmpty(),
			"empty chain selection applies no child damage");
		assertEquals("seed=41084 draws=[double=0.0]",
			harness.random().describeState(),
			"empty chain selection consumes chance but no target index");
	}

	private static void chainSelectionBoundaries(
			final CurrentCombatHarness harness) throws Exception {
		harness.openRectangle(140, 148, 850, 852);
		final Player source = harness.player("chain geometry", 140, 850);
		final Npc anchor = npcWithHits(harness, 141, 850, 20);
		final Npc valid = npcWithHits(harness, 143, 850, 20);
		final Npc otherLevel = npcWithHits(harness, 143,
			LegacyPackedPointAdapter.LEVEL_STRIDE + 850, 20);
		harness.world().getTile(142, 850).projectileAllowed = false;
		harness.world().getTile(142, 850).traversalMask =
			(byte) CollisionFlag.FULL_BLOCK;
		assertFalse(PathValidation.checkPath(harness.world(),
			anchor.getWorldLocation(), valid.getWorldLocation(), false),
			"chain fixture installs a blocked line of effect");

		final Object[] selectors = {
			new CombatEvent(harness.world(), source, anchor),
			new PvmMeleeEvent(harness.world(), source, anchor),
			new ProjectileEvent(harness.world(), source, anchor, 0, 1, false)
		};
		for (Object selector : selectors) {
			final Mob selected = selectChain(selector, source, anchor);
			assertTrue(selected == valid,
				selector.getClass().getSimpleName()
					+ " keeps view/layer membership but ignores walls");
		}
		assertEquals(20, otherLevel.getLevel(Skill.HITS.id()),
			"matching coordinate on another level remains outside the view");

		final Player removedAnchorOwner = harness.player(
			"chain removed anchor", 160, 850);
		final Npc removedAnchor = npcWithHits(harness, 161, 850, 20);
		final Npc afterRemovedAnchor = npcWithHits(harness, 162, 850, 20);
		final PvmMeleeEvent removedAnchorEvent = new PvmMeleeEvent(
			harness.world(), removedAnchorOwner, removedAnchor);
		removedAnchor.remove();
		harness.random().reset(0xA076L);
		harness.random().scriptInts(0);
		assertTrue(selectChain(removedAnchorEvent, removedAnchorOwner,
			removedAnchor) == afterRemovedAnchor,
			"removed anchor still supplies its last location");
		afterRemovedAnchor.remove();
		assertTrue(selectChain(removedAnchorEvent, removedAnchorOwner,
			removedAnchor) == null,
			"removed candidates are excluded without an index draw");

		final Player respawnOwner = harness.player(
			"chain respawn", 180, 850);
		final Npc respawnAnchor = npcWithHits(harness, 181, 850, 20);
		final Npc respawning = npcWithHits(harness, 182, 850, 20);
		setRespawning(respawning, true);
		final PvmMeleeEvent respawnEvent = new PvmMeleeEvent(
			harness.world(), respawnOwner, respawnAnchor);
		harness.random().reset(0xA077L);
		harness.random().scriptInts(0);
		assertTrue(selectChain(respawnEvent, respawnOwner, respawnAnchor)
			== respawning,
			"visible respawning candidates remain current chain compatibility");

		final Player removedSource = harness.player(
			"chain removed source", 200, 850);
		final Npc removedSourceAnchor = npcWithHits(harness, 201, 850, 20);
		npcWithHits(harness, 202, 850, 20);
		final PvmMeleeEvent removedSourceEvent = new PvmMeleeEvent(
			harness.world(), removedSource, removedSourceAnchor);
		removedSource.remove();
		assertLayeredRemovedSourceFailure(removedSourceEvent,
			"selectChaosChainLightningTarget", removedSource,
			removedSourceAnchor,
			"removed chain source retains layered membership fail-fast");
	}

	private static void splinterProcAndSelectionDrawOrder(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player source = harness.player("splinter rng", 240, 870);
		final Npc primary = npcWithHits(harness, 241, 870, 20);
		final Npc first = npcWithHits(harness, 242, 870, 20);
		final Npc second = npcWithHits(harness, 241, 871, 20);
		final List<Npc> ordered = viewOrder(source, first, second);
		assertEquals(2, ordered.size(), "Splinter fixture candidate order");
		final long seed = findProcThenSecondIndexSeed();
		final Random expected = new Random(seed);
		expected.nextDouble();
		final int selectedIndex = expected.nextInt(2);
		assertEquals(1, selectedIndex,
			"fixture seed selects the second candidate after proc roll");

		final ProjectileEvent event = new ProjectileEvent(
			harness.world(), source, primary, 9, 1, false);
		setField(event, "secondaryEffectDamage", Integer.valueOf(9));
		setField(event, "splinterProcChancePercent", Integer.valueOf(100));
		DataConversions.getRandom().setSeed(seed);
		invoke(event, "applySplinterOnHitEffect", new Class<?>[0]);

		final List<DamageResult> results = results(harness);
		assertEquals(1, results.size(), "one successful Splinter child");
		assertTarget(results.get(0), ordered.get(1), 5,
			"Splinter draws proc before candidate index");
		assertEquals(expected.nextInt(10_000),
			DataConversions.getRandom().nextInt(10_000),
			"successful Splinter consumes exactly proc and index draws");
	}

	private static void splinterEmptySelectionDrawOrder(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player source = harness.player("splinter empty", 260, 870);
		final Npc primary = npcWithHits(harness, 261, 870, 20);
		final ProjectileEvent event = new ProjectileEvent(
			harness.world(), source, primary, 9, 1, false);
		setField(event, "secondaryEffectDamage", Integer.valueOf(9));
		setField(event, "splinterProcChancePercent", Integer.valueOf(100));
		final long seed = 0xA078L;
		final Random expected = new Random(seed);
		expected.nextDouble();
		DataConversions.getRandom().setSeed(seed);
		invoke(event, "applySplinterOnHitEffect", new Class<?>[0]);
		assertTrue(results(harness).isEmpty(),
			"empty Splinter selection applies no child damage");
		assertEquals(expected.nextInt(10_000),
			DataConversions.getRandom().nextInt(10_000),
			"empty Splinter selection consumes proc but no index draw");
	}

	private static void splinterSelectionBoundaries(
			final CurrentCombatHarness harness) throws Exception {
		harness.openRectangle(280, 288, 870, 872);
		final Player source = harness.player("splinter geometry", 280, 870);
		final Npc primary = npcWithHits(harness, 281, 870, 20);
		final Npc valid = npcWithHits(harness, 283, 870, 20);
		final Npc otherLevel = npcWithHits(harness, 283,
			LegacyPackedPointAdapter.LEVEL_STRIDE + 870, 20);
		harness.world().getTile(282, 870).projectileAllowed = false;
		harness.world().getTile(282, 870).traversalMask =
			(byte) CollisionFlag.FULL_BLOCK;
		assertFalse(PathValidation.checkPath(harness.world(),
			primary.getWorldLocation(), valid.getWorldLocation(), false),
			"Splinter fixture installs a blocked line of effect");
		final ProjectileEvent event = new ProjectileEvent(
			harness.world(), source, primary, 9, 1, false);
		DataConversions.getRandom().setSeed(0xA079L);
		assertTrue(selectSplinter(event, source, primary) == valid,
			"Splinter keeps view/layer membership but ignores walls");
		assertEquals(20, otherLevel.getLevel(Skill.HITS.id()),
			"Splinter excludes matching coordinate on another level");

		final Player removedAnchorOwner = harness.player(
			"splinter removed anchor", 300, 870);
		final Npc removedAnchor = npcWithHits(harness, 301, 870, 20);
		final Npc afterRemovedAnchor = npcWithHits(harness, 302, 870, 20);
		final ProjectileEvent removedAnchorEvent = new ProjectileEvent(
			harness.world(), removedAnchorOwner, removedAnchor, 9, 1, false);
		removedAnchor.remove();
		DataConversions.getRandom().setSeed(0xA07AL);
		assertTrue(selectSplinter(removedAnchorEvent, removedAnchorOwner,
			removedAnchor) == afterRemovedAnchor,
			"removed Splinter primary still supplies its last location");
		afterRemovedAnchor.remove();
		assertTrue(selectSplinter(removedAnchorEvent, removedAnchorOwner,
			removedAnchor) == null,
			"removed Splinter candidates are excluded without a draw");

		final Player respawnOwner = harness.player(
			"splinter respawn", 320, 870);
		final Npc respawnPrimary = npcWithHits(harness, 321, 870, 20);
		final Npc respawning = npcWithHits(harness, 322, 870, 20);
		setRespawning(respawning, true);
		final ProjectileEvent respawnEvent = new ProjectileEvent(
			harness.world(), respawnOwner, respawnPrimary, 9, 1, false);
		DataConversions.getRandom().setSeed(0xA07BL);
		assertTrue(selectSplinter(respawnEvent, respawnOwner, respawnPrimary)
			== respawning,
			"visible respawning candidates remain Splinter compatibility");

		final Player removedSource = harness.player(
			"splinter removed source", 340, 870);
		final Npc removedSourcePrimary = npcWithHits(harness, 341, 870, 20);
		npcWithHits(harness, 342, 870, 20);
		final ProjectileEvent removedSourceEvent = new ProjectileEvent(
			harness.world(), removedSource, removedSourcePrimary, 9, 1, false);
		removedSource.remove();
		assertLayeredRemovedSourceFailure(removedSourceEvent,
			"selectSplinterTarget", removedSource, removedSourcePrimary,
			"removed Splinter source retains layered membership fail-fast");
	}

	private static Mob selectChain(final Object event, final Player player,
			final Mob anchor) throws Exception {
		return (Mob) invoke(event, "selectChaosChainLightningTarget",
			new Class<?>[] {Player.class, Mob.class}, player, anchor);
	}

	private static Npc selectSplinter(final ProjectileEvent event,
			final Player player, final Mob primary) throws Exception {
		return (Npc) invoke(event, "selectSplinterTarget",
			new Class<?>[] {Player.class, Mob.class}, player, primary);
	}

	private static void assertLayeredRemovedSourceFailure(final Object event,
			final String method, final Player source, final Mob anchor,
			final String message) throws Exception {
		try {
			invoke(event, method, new Class<?>[] {Player.class, Mob.class},
				source, anchor);
			throw new AssertionError(message + ": expected failure");
		} catch (InvocationTargetException expected) {
			assertTrue(expected.getCause() instanceof IllegalStateException,
				message + ": expected layered authority failure");
		}
	}

	private static void setRespawning(final Npc npc, final boolean value)
			throws Exception {
		CurrentCombatHarness.invokePrivate(npc, "setRespawning",
			new Class<?>[] {boolean.class}, Boolean.valueOf(value));
	}

	private static List<Npc> viewOrder(final Player owner,
			final Npc first, final Npc second) {
		final List<Npc> ordered = new ArrayList<>();
		for (Npc candidate : owner.getViewArea().getNpcsInView()) {
			if (candidate == first || candidate == second) {
				ordered.add(candidate);
			}
		}
		return ordered;
	}

	private static long findProcThenSecondIndexSeed() {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final Random ordered = new Random(seed);
			ordered.nextDouble();
			final int afterProc = ordered.nextInt(2);
			final int beforeProc = new Random(seed).nextInt(2);
			if (afterProc == 1 && beforeProc == 0) {
				return seed;
			}
		}
		throw new AssertionError("No discriminating Splinter RNG seed found");
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int x, final int y, final int hits) {
		final Npc npc = harness.npc(NpcId.GREATER_DEMON.id(), x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, Math.max(1, hits), false);
		return npc;
	}

	private static void setField(final Object target, final String fieldName,
			final Object value) throws Exception {
		final Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object invoke(final Object target, final String method,
			final Class<?>[] parameterTypes, final Object... arguments)
			throws Exception {
		return CurrentCombatHarness.invokePrivate(
			target, method, parameterTypes, arguments);
	}

	private static List<DamageResult> results(
			final CurrentCombatHarness harness) {
		return CurrentCombatCharacterizationTest.observedDamageResults(harness);
	}

	private static void assertTarget(final DamageResult result,
			final Mob target, final int resolvedDamage, final String message) {
		assertTrue(result.getRequest().getTarget() == target,
			message + " target identity");
		assertEquals(resolvedDamage, result.getRequest().getResolvedDamage(),
			message + " resolved damage");
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

	private static void assertEquals(final String expected, final String actual,
			final String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected=" + expected
				+ " actual=" + actual);
		}
	}
}
