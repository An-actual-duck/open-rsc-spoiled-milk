package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executable A05.4 specifications for representative damage families that
 * intentionally remain outside the resolved-damage transaction.
 */
final class CurrentCombatSecondaryDamageCharacterization {
	private CurrentCombatSecondaryDamageCharacterization() {
	}

	static void compatibilityHelperAndDamageOverTime(
			final CurrentCombatHarness harness) throws Exception {
		final Player attacker = harness.player("helper source", 620, 620);
		final Npc lethal = npcWithHits(harness, 621, 620, 5);
		lethal.setShouldRespawn(false);
		lethal.setOpponent(attacker);
		lethal.addCombatDamage(attacker, 5);
		final AtomicInteger callbacks = new AtomicInteger();
		final AtomicBoolean presentationVisibleAtDeath = new AtomicBoolean();
		lethal.addDeathListener(new NpcLootEvent(
			harness.world(), lethal.getLocation(), lethal.getID(), 1,
			ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				callbacks.incrementAndGet();
				presentationVisibleAtDeath.set(
					lethal.getUpdateFlags().getDamage().get() != null
						|| !lethal.getUpdateFlags().getHitSplats().isEmpty());
			}
		});

		final int actualDamage = lethal.damageAndGetActualDamage(
			7, HitSplat.TYPE_POISON);
		assertEquals(5, actualDamage,
			"compatibility helper caps factual lethal damage");
		assertEquals(1, callbacks.get(),
			"compatibility helper death callback cardinality");
		assertFalse(presentationVisibleAtDeath.get(),
			"compatibility helper preserves death-before-presentation order");
		assertHit(lethal, 7, HitSplat.TYPE_POISON,
			"compatibility helper preserves displayed overkill and requested hitsplat");

		final Player poisoned = harness.player("poison victim", 624, 620);
		new PoisonEvent(harness.world(), poisoned, 40, null).run();
		assertEquals(36, poisoned.getLevel(Skill.HITS.id()),
			"poison pulse damage");
		assertHit(poisoned, 4, HitSplat.TYPE_POISON,
			"poison pulse presentation");
		assertEquals(37, poisoned.getPoisonDamage(),
			"poison power drains before damage settlement");

	}

	static void projectileSecondaryContributionPolicies(
			final CurrentCombatHarness harness) throws Exception {
		final Player caster = harness.player("secondary caster", 630, 620);
		caster.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 40, false);
		final Npc chainTarget = npcWithHits(harness, 631, 620, 20);
		final ProjectileEvent magicEvent = new ProjectileEvent(
			harness.world(), caster, chainTarget, 0, 1, false);

		invoke(magicEvent, "inflictChainLightningDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			caster, chainTarget, Integer.valueOf(7));
		assertEquals(13, chainTarget.getLevel(Skill.HITS.id()),
			"chain-lightning damage");
		assertHit(chainTarget, 7, HitSplat.TYPE_ARMOR_PROC,
			"chain-lightning presentation");
		assertEquals(7, contribution(
			chainTarget, caster, "getMageDamageInfoBy"),
			"magic chain-lightning contribution style");
		assertEquals(0, contribution(
			chainTarget, caster, "getCombatDamageInfoBy"),
			"chain lightning does not become melee contribution");
		assertEquals(20, caster.getLevel(Skill.HITS.id()),
			"chain lightning has no local lifesteal");
		assertFalse(chainTarget.isChasing(),
			"direct chain helper does not establish aggro");

		final Npc magicTarget = npcWithHits(harness, 633, 620, 20);
		invoke(magicEvent, "inflictAuxiliaryMagicDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			caster, magicTarget, Integer.valueOf(6));
		assertHit(magicTarget, 6, HitSplat.TYPE_ARMOR_PROC,
			"auxiliary magic presentation");
		assertEquals(6, contribution(
			magicTarget, caster, "getMageDamageInfoBy"),
			"auxiliary magic contribution style");

		final Npc trueTarget = npcWithHits(harness, 635, 620, 20);
		invoke(magicEvent, "inflictAuxiliaryTrueDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			caster, trueTarget, Integer.valueOf(5));
		assertHit(trueTarget, 5, HitSplat.TYPE_ARMOR_PROC,
			"auxiliary true presentation");
		assertEquals(5, contribution(
			trueTarget, caster, "getCombatDamageInfoBy"),
			"auxiliary true damage retains combat contribution style");
	}

	static void auxiliarySettlementAcrossEvents(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		int x = 660;
		for (AuxiliaryPath path : AuxiliaryPath.values()) {
			final Player source = harness.player(
				"aux settle " + path.ordinal(), x, 620);
			source.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 20, 40, false);
			final Npc magicTarget = npcWithHits(harness, x + 1, 620, 20);
			final Object magicEvent = auxiliaryEvent(
				path, harness, source, magicTarget);
			final int returnedDamage = inflictAuxiliaryMagic(
				magicEvent, source, magicTarget, 6);

			assertEquals(6, returnedDamage,
				path + " auxiliary Magic return value");
			assertEquals(14, magicTarget.getLevel(Skill.HITS.id()),
				path + " auxiliary Magic target Hits");
			assertHit(magicTarget, 6, HitSplat.TYPE_ARMOR_PROC,
				path + " auxiliary Magic presentation");
			assertEquals(6, contribution(
				magicTarget, source, "getMageDamageInfoBy"),
				path + " auxiliary Magic contribution");
			assertEquals(0, contribution(
				magicTarget, source, "getCombatDamageInfoBy"),
				path + " auxiliary Magic excludes combat contribution");
			assertEquals(20, source.getLevel(Skill.HITS.id()),
				path + " auxiliary Magic applies no local lifesteal");

			final Player lethalSource = harness.player(
				"aux lethal " + path.ordinal(), x, 623);
			final Npc trueTarget = npcWithHits(harness, x + 1, 623, 5);
			trueTarget.setShouldRespawn(false);
			final AtomicInteger deaths = deathCounter(harness, trueTarget);
			final Object trueEvent = auxiliaryEvent(
				path, harness, lethalSource, trueTarget);
			inflictAuxiliaryTrue(trueEvent, lethalSource, trueTarget, 7);

			assertEquals(0, trueTarget.getLevel(Skill.HITS.id()),
				path + " auxiliary true lethal Hits");
			assertHit(trueTarget, 7, HitSplat.TYPE_ARMOR_PROC,
				path + " auxiliary true displayed overkill");
			assertEquals(5, contribution(
				trueTarget, lethalSource, "getCombatDamageInfoBy"),
				path + " auxiliary true capped combat contribution");
			assertEquals(0, contribution(
				trueTarget, lethalSource, "getMageDamageInfoBy"),
				path + " auxiliary true excludes Magic contribution");
			assertEquals(1, deaths.get(),
				path + " auxiliary true terminal callback cardinality");
			assertTrue(trueTarget.isUnregistering(),
				path + " auxiliary true terminal unregister");
			x += 4;
		}

		final java.util.List<DamageResult> results =
			CurrentCombatCharacterizationTest.observedDamageResults(harness);
		assertEquals(6, results.size(),
			"one transaction result per positive auxiliary hit");
		int resultIndex = 0;
		for (AuxiliaryPath path : AuxiliaryPath.values()) {
			assertAuxiliaryResult(results.get(resultIndex++),
				path.magicEffectKey, CombatStyle.MAGIC, 6, 6, 0,
				path + " auxiliary Magic transaction");
			assertAuxiliaryResult(results.get(resultIndex++),
				path.trueEffectKey, CombatStyle.MELEE, 7, 5, 2,
				path + " auxiliary true transaction");
		}
	}

	static void auxiliaryMitigationAcrossEvents(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		int x = 680;
		for (AuxiliaryPath path : AuxiliaryPath.values()) {
			final Npc source = harness.npc(NpcId.GREATER_DEMON.id(), x, 620);
			final Player magicTarget = harness.player(
				"aux magic pot " + path.ordinal(), x + 1, 620);
			magicTarget.activateMagicResistancePotion(50, 60_000L);
			magicTarget.activateMeleeResistancePotion(50, 60_000L);
			final Object magicEvent = auxiliaryEvent(
				path, harness, source, magicTarget);
			final int magicDamage = inflictAuxiliaryMagic(
				magicEvent, source, magicTarget, 8);

			assertEquals(4, magicDamage,
				path + " auxiliary Magic potion-adjusted return");
			assertEquals(36, magicTarget.getLevel(Skill.HITS.id()),
				path + " auxiliary Magic applies Magic potion only");
			assertHit(magicTarget, 4, HitSplat.TYPE_ARMOR_PROC,
				path + " mitigated auxiliary Magic presentation");

			final Player trueTarget = harness.player(
				"aux true pot " + path.ordinal(), x + 1, 623);
			trueTarget.activateMagicResistancePotion(50, 60_000L);
			trueTarget.activateMeleeResistancePotion(50, 60_000L);
			final Object trueEvent = auxiliaryEvent(
				path, harness, source, trueTarget);
			inflictAuxiliaryTrue(trueEvent, source, trueTarget, 8);

			assertEquals(32, trueTarget.getLevel(Skill.HITS.id()),
				path + " auxiliary true ignores potion reductions");
			assertHit(trueTarget, 8, HitSplat.TYPE_ARMOR_PROC,
				path + " auxiliary true presentation after robe-only path");

			final Player blockedMagicTarget = harness.player(
				"aux blocked " + path.ordinal(), x + 1, 626);
			blockedMagicTarget.activateMagicResistancePotion(100, 60_000L);
			final Object blockedMagicEvent = auxiliaryEvent(
				path, harness, source, blockedMagicTarget);
			assertEquals(0, inflictAuxiliaryMagic(
				blockedMagicEvent, source, blockedMagicTarget, 8),
				path + " fully mitigated auxiliary Magic return");
			assertNoHit(blockedMagicTarget,
				path + " fully mitigated auxiliary Magic");

			final Player zeroTrueTarget = harness.player(
				"aux zero " + path.ordinal(), x + 1, 629);
			final Object zeroTrueEvent = auxiliaryEvent(
				path, harness, source, zeroTrueTarget);
			inflictAuxiliaryTrue(zeroTrueEvent, source, zeroTrueTarget, 0);
			assertNoHit(zeroTrueTarget,
				path + " zero auxiliary true");
			x += 4;
		}
		assertEquals(6, CurrentCombatCharacterizationTest
			.observedDamageResults(harness).size(),
			"ineffective auxiliary helpers publish no transaction results");
	}

	static void delayedSpellSecondaryHelperPolicy(
			final CurrentCombatHarness harness) throws Exception {
		final Player caster = harness.player("area caster", 650, 620);
		caster.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 40, false);
		final Npc target = npcWithHits(harness, 651, 620, 20);
		final SpellHandler handler = new SpellHandler();

		final int applied = ((Integer) invoke(handler,
			"applyGodSpellSecondaryDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			caster, target, Integer.valueOf(6))).intValue();
		assertEquals(6, applied,
			"god/Iban delayed secondary reports capped helper damage");
		assertEquals(14, target.getLevel(Skill.HITS.id()),
			"god/Iban delayed secondary damage");
		assertHit(target, 6, HitSplat.TYPE_STANDARD,
			"god/Iban delayed secondary uses the standard helper hitsplat");
		assertEquals(6, contribution(
			target, caster, "getMageDamageInfoBy"),
			"god/Iban delayed secondary Magic contribution");
		assertTrue(target.isChasing(),
			"god/Iban delayed secondary establishes NPC chase");
		assertEquals(20, caster.getLevel(Skill.HITS.id()),
			"per-target delayed helper does not apply local lifesteal");
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int x, final int y, final int hits) {
		final Npc npc = harness.npc(NpcId.GREATER_DEMON.id(), x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, hits, false);
		return npc;
	}

	private static AtomicInteger deathCounter(
			final CurrentCombatHarness harness, final Npc npc) {
		final AtomicInteger deaths = new AtomicInteger();
		npc.addDeathListener(new NpcLootEvent(
			harness.world(), npc.getLocation(), npc.getID(), 1,
			ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				deaths.incrementAndGet();
			}
		});
		return deaths;
	}

	private static int contribution(final Npc target, final Player source,
			final String method) throws Exception {
		@SuppressWarnings("unchecked")
		final Pair<Integer, Long> info = (Pair<Integer, Long>)
			CurrentCombatHarness.invokePrivate(target, method,
				new Class<?>[] {java.util.UUID.class}, source.getUUID());
		return info.getLeft().intValue();
	}

	private static Object auxiliaryEvent(final AuxiliaryPath path,
			final CurrentCombatHarness harness, final Mob source,
			final Mob target) {
		switch (path) {
			case RECIPROCAL_MELEE:
				return new CombatEvent(harness.world(), source, target);
			case PVM_MELEE:
				return new PvmMeleeEvent(harness.world(), source, target);
			case PROJECTILE:
				return new ProjectileEvent(
					harness.world(), source, target, 0, 1, false);
			default:
				throw new AssertionError("Unhandled auxiliary path " + path);
		}
	}

	private static int inflictAuxiliaryMagic(final Object event,
			final Mob source, final Mob target, final int damage)
			throws Exception {
		return ((Integer) invoke(event, "inflictAuxiliaryMagicDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			source, target, Integer.valueOf(damage))).intValue();
	}

	private static void inflictAuxiliaryTrue(final Object event,
			final Mob source, final Mob target, final int damage)
			throws Exception {
		invoke(event, "inflictAuxiliaryTrueDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			source, target, Integer.valueOf(damage));
	}

	private static Object invoke(final Object target, final String method,
			final Class<?>[] parameterTypes, final Object... arguments)
			throws Exception {
		return CurrentCombatHarness.invokePrivate(
			target, method, parameterTypes, arguments);
	}

	private static void assertHit(final Mob target, final int amount,
			final int type, final String label) {
		assertTrue(target.getUpdateFlags().getDamage().get() != null,
			label + " damage update");
		assertEquals(amount,
			target.getUpdateFlags().getDamage().get().getDamage(),
			label + " damage amount");
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			label + " hitsplat cardinality");
		assertEquals(amount,
			target.getUpdateFlags().getHitSplats().get(0).getAmount(),
			label + " hitsplat amount");
		assertEquals(type,
			target.getUpdateFlags().getHitSplats().get(0).getType(),
			label + " hitsplat type");
	}

	private static void assertNoHit(final Mob target, final String label) {
		assertEquals(40, target.getLevel(Skill.HITS.id()),
			label + " target Hits");
		assertTrue(target.getUpdateFlags().getDamage().get() == null,
			label + " has no damage update");
		assertEquals(0, target.getUpdateFlags().getHitSplats().size(),
			label + " has no hitsplat");
	}

	private static void assertAuxiliaryResult(final DamageResult result,
			final String effectKey, final CombatStyle style,
			final int resolvedDamage, final int actualDamage,
			final int overkillDamage, final String label) {
		assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
			result.getStatus(), label + " status");
		assertEquals(DamageRequest.SourceCategory.OWNED_EFFECT,
			result.getRequest().getSourceCategory(), label + " category");
		assertEquals(effectKey, result.getRequest().getEffectKey(),
			label + " stable identity");
		assertEquals(style, result.getRequest().getStyle(),
			label + " style");
		assertTrue(result.getRequest().getEventId() != null,
			label + " event identity");
		assertEquals(HitSplat.TYPE_ARMOR_PROC,
			result.getRequest().getHitSplatType(), label + " hitsplat type");
		assertEquals(resolvedDamage, result.getRequest().getResolvedDamage(),
			label + " resolved damage");
		assertEquals(actualDamage, result.getActualDamage(),
			label + " factual damage");
		assertEquals(actualDamage, result.getLegacyDamageDealt(),
			label + " legacy post-hit value");
		assertEquals(overkillDamage, result.getOverkillDamage(),
			label + " overkill");
		assertEquals(overkillDamage > 0, result.isTargetTerminal(),
			label + " terminal outcome");
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
			throw new AssertionError(message + ": expected " + expected
				+ " but was " + actual);
		}
	}

	private static void assertEquals(final boolean expected,
			final boolean actual, final String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected
				+ " but was " + actual);
		}
	}

	private static void assertEquals(final Object expected,
			final Object actual, final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ " but was " + actual);
		}
	}

	private enum AuxiliaryPath {
		RECIPROCAL_MELEE(
			"reciprocal-melee-auxiliary-magic",
			"reciprocal-melee-auxiliary-true"),
		PVM_MELEE(
			"pvm-melee-auxiliary-magic",
			"pvm-melee-auxiliary-true"),
		PROJECTILE(
			"projectile-auxiliary-magic",
			"projectile-auxiliary-true");

		private final String magicEffectKey;
		private final String trueEffectKey;

		AuxiliaryPath(final String magicEffectKey,
				final String trueEffectKey) {
			this.magicEffectKey = magicEffectKey;
			this.trueEffectKey = trueEffectKey;
		}
	}
}
