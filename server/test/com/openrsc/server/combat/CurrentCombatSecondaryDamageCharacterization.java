package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.impl.BurnEvent;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
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

		final Player burning = harness.player("burn victim", 626, 620);
		new BurnEvent(harness.world(), burning, 3, 2).run();
		assertEquals(37, burning.getLevel(Skill.HITS.id()),
			"burn pulse damage");
		assertHit(burning, 3, HitSplat.TYPE_STANDARD,
			"burn remains a standard compatibility-helper hitsplat");
		assertEquals(3, burning.getCache().getInt("burn_damage"),
			"burn cache is updated before helper settlement");
		assertEquals(1, burning.getCache().getInt("burn_pulses"),
			"burn pulse count is decremented before helper settlement");
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

	static void reflectionAttributionPolicies(
			final CurrentCombatHarness harness) throws Exception {
		final Player frostbiteSource = harness.player(
			"frostbite source", 640, 620);
		final Npc frostbiteTarget = npcWithHits(harness, 641, 620, 5);
		frostbiteTarget.setShouldRespawn(false);
		final AtomicInteger frostbiteDeaths = deathCounter(
			harness, frostbiteTarget);
		final ProjectileEvent frostbiteEvent = new ProjectileEvent(
			harness.world(), frostbiteSource, frostbiteTarget, 0, 1, false);

		invoke(frostbiteEvent, "inflictFrostbiteReflectedDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			frostbiteSource, frostbiteTarget, Integer.valueOf(7));
		assertHit(frostbiteTarget, 7, HitSplat.TYPE_ARMOR_PROC,
			"Frostbite reflected presentation");
		assertEquals(5, contribution(
			frostbiteTarget, frostbiteSource, "getMageDamageInfoBy"),
			"Frostbite credits its originating player as Magic damage");
		assertEquals(1, frostbiteDeaths.get(),
			"Frostbite terminal callback cardinality");

		final Player thornsOwner = harness.player("thorns owner", 644, 620);
		final Npc thornsAttacker = npcWithHits(harness, 645, 620, 5);
		thornsAttacker.setShouldRespawn(false);
		final AtomicInteger thornsDeaths = deathCounter(
			harness, thornsAttacker);
		final ProjectileEvent thornsEvent = new ProjectileEvent(
			harness.world(), thornsAttacker, thornsOwner, 0, 1, false);

		invoke(thornsEvent, "inflictClericThornsDamage",
			new Class<?>[] {int.class}, Integer.valueOf(7));
		assertHit(thornsAttacker, 7, HitSplat.TYPE_ARMOR_PROC,
			"Cleric Thorns presentation");
		assertEquals(5, contribution(
			thornsAttacker, thornsOwner, "getCombatDamageInfoBy"),
			"Cleric Thorns credits the protected player as combat damage");
		assertEquals(0, contribution(
			thornsAttacker, thornsOwner, "getMageDamageInfoBy"),
			"Cleric Thorns is not Magic contribution");
		assertEquals(1, thornsDeaths.get(),
			"Cleric Thorns terminal callback cardinality");
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
}
