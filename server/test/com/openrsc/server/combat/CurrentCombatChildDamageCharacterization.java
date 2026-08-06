package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable pre-migration A05.4C policy specifications. */
final class CurrentCombatChildDamageCharacterization {
	private static final int DRAGONSTONE_BLOOD_AMULET = 1733;
	private static final int DRAGONSTONE_CHAOS_AMULET = 1723;
	private static final int DRAGONSTONE_DEATH_AMULET = 1728;
	private static final int SAPPHIRE_DEATH_AMULET = 1724;
	private static final int DRAGONSTONE_DEATH_RING = 3090;
	private static final long SUMMON_ASSIST_WINDOW_MS = 60_000L;

	private CurrentCombatChildDamageCharacterization() {
	}

	static void chainLightningPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		int x = 740;
		for (MeleeChildPath path : MeleeChildPath.values()) {
			final Player source = harness.player(
				"chain " + path.ordinal(), x, 620);
			source.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 20, 40, false);
			final Npc primary = npcWithHits(harness, x + 1, 620, 20);
			final Npc child = npcWithHits(harness, x + 2, 620, 5);
			child.setShouldRespawn(false);
			final DeathObservation death = observeDeath(harness, child, source);
			final Object event = meleeEvent(path, harness, source, primary);
			invoke(event, "inflictJewelryEffectDamage",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				source, child, Integer.valueOf(7));

			assertHit(child, 7, HitSplat.TYPE_ARMOR_PROC,
				path + " chain displayed overkill");
			assertEquals(5, contribution(child, source,
				"getCombatDamageInfoBy"), path + " chain combat contribution");
			assertEquals(0, contribution(child, source,
				"getMageDamageInfoBy"), path + " chain excludes Magic contribution");
			assertEquals(20, source.getLevel(Skill.HITS.id()),
				path + " chain has no local lifesteal");
			assertFalse(child.isChasing(), path + " chain has no child aggro");
			assertDeath(death, 1, true, path + " chain child death order");
			x += 8;
		}

		final Player caster = harness.player("chain projectile", 760, 620);
		final Npc magicPrimary = npcWithHits(harness, 761, 620, 20);
		final Npc magicChild = npcWithHits(harness, 762, 620, 20);
		final ProjectileEvent magic = new ProjectileEvent(
			harness.world(), caster, magicPrimary, 0, 1, false);
		invoke(magic, "inflictChainLightningDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			caster, magicChild, Integer.valueOf(7));
		assertHit(magicChild, 7, HitSplat.TYPE_ARMOR_PROC,
			"projectile Magic chain");
		assertEquals(7, contribution(magicChild, caster,
			"getMageDamageInfoBy"), "projectile chain Magic contribution");

		final Npc rangePrimary = npcWithHits(harness, 765, 620, 20);
		final Npc rangeChild = npcWithHits(harness, 766, 620, 5);
		rangeChild.setShouldRespawn(false);
		final DeathObservation rangeDeath = observeDeath(
			harness, rangeChild, caster);
		final ProjectileEvent ranged = new ProjectileEvent(
			harness.world(), caster, rangePrimary, 0, 2, false);
		invoke(ranged, "inflictChainLightningDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			caster, rangeChild, Integer.valueOf(8));
		assertEquals(5, contribution(rangeChild, caster,
			"getRangeDamageInfoBy"), "projectile chain capped Ranged contribution");
		assertDeath(rangeDeath, 1, true,
			"projectile chain direct child death order");

		final Player mitigated = harness.player("chain mitigation", 770, 620);
		mitigated.activateMagicResistancePotion(50, 60_000L);
		invoke(magic, "inflictChainLightningDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			caster, mitigated, Integer.valueOf(8));
		assertEquals(36, mitigated.getLevel(Skill.HITS.id()),
			"projectile chain retains style-specific potion mitigation");
		assertHit(mitigated, 4, HitSplat.TYPE_ARMOR_PROC,
			"projectile chain mitigated player presentation");

		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player selector = harness.player("chain selector", 780, 620);
			final Npc anchor = npcWithHits(harness, 781, 620, 20);
			final Npc valid = npcWithHits(harness, 782, 620, 20);
			final Npc otherLevel = npcWithHits(harness, 782,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 620, 20);
			final Npc selected = (Npc) invoke(
				new PvmMeleeEvent(harness.world(), selector, anchor),
				"selectChaosChainLightningTarget",
				new Class<?>[] {Player.class, Mob.class}, selector, anchor);
			assertTrue(selected == valid,
				"chain selection keeps the same-layer in-range child");
			assertEquals(20, otherLevel.getLevel(Skill.HITS.id()),
				"chain selection excludes the matching cross-level coordinate");

			harness.equip(selector, DRAGONSTONE_CHAOS_AMULET, 1);
			installGuardDog(harness, selector, 779, 621);
			invoke(new PvmMeleeEvent(harness.world(), selector, anchor),
				"applyChaosAmuletChainLightning",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				selector, anchor, Integer.valueOf(10));
			assertEquals(20, valid.getLevel(Skill.HITS.id()),
				"guard-dog suppression prevents chain child damage");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
		assertNoTransactions(harness, "pre-migration chain lightning");
	}

	static void splinterPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player caster = harness.player("splinter caster", 800, 620);
			caster.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 20, 40, false);
			final Npc primary = npcWithHits(harness, 801, 620, 20);
			final Npc valid = npcWithHits(harness, 802, 620, 20);
			final Npc distant = npcWithHits(harness, 806, 620, 20);
			final Npc otherLevel = npcWithHits(harness, 802,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 620, 20);
			final Npc summon = npcWithHits(harness, 800, 621, 20);
			summon.setAttribute("myworld_summon_owner", caster.getUsernameHash());
			final ProjectileEvent event = new ProjectileEvent(
				harness.world(), caster, primary, 9, 1, true);
			setField(event, "secondaryEffectDamage", Integer.valueOf(9));

			assertTrue(invoke(event, "selectSplinterTarget",
				new Class<?>[] {Player.class, Mob.class}, caster, primary) == valid,
				"Splinter selects only the same-level in-radius non-summon NPC");
			invoke(event, "applySplinter", new Class<?>[0]);
			assertEquals(15, valid.getLevel(Skill.HITS.id()),
				"Splinter uses ceil half of the resolved primary damage");
			assertHit(valid, 5, HitSplat.TYPE_ARMOR_PROC,
				"Splinter child presentation");
			assertEquals(5, contribution(valid, caster,
				"getMageDamageInfoBy"), "Splinter Magic contribution");
			assertTrue(valid.isChasing(), "Splinter starts eligible child chase");
			assertEquals(20, caster.getLevel(Skill.HITS.id()),
				"Splinter has no local lifesteal");
			assertEquals(20, distant.getLevel(Skill.HITS.id()),
				"Splinter excludes distant NPCs");
			assertEquals(20, otherLevel.getLevel(Skill.HITS.id()),
				"Splinter excludes cross-level NPCs");
			assertEquals(20, summon.getLevel(Skill.HITS.id()),
				"Splinter excludes summons");

			final Player suppressed = harness.player("splinter guarded", 820, 620);
			final Npc suppressedPrimary = npcWithHits(harness, 821, 620, 20);
			final Npc suppressedChild = npcWithHits(harness, 822, 620, 20);
			installGuardDog(harness, suppressed, 819, 621);
			final ProjectileEvent suppressedEvent = new ProjectileEvent(
				harness.world(), suppressed, suppressedPrimary, 9, 1, true);
			setField(suppressedEvent, "secondaryEffectDamage", Integer.valueOf(9));
			invoke(suppressedEvent, "applySplinter", new Class<?>[0]);
			assertEquals(20, suppressedChild.getLevel(Skill.HITS.id()),
				"guard-dog suppression prevents Splinter damage");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
		assertNoTransactions(harness, "pre-migration Splinter");
	}

	static void bloodRobeSplashPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player caster = harness.player("blood splash", 840, 620);
			caster.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 20, 40, false);
			equipBloodRobes(harness, caster);
			final Npc primary = npcWithHits(harness, 841, 620, 20);
			final Npc valid = npcWithHits(harness, 842, 620, 20);
			final Npc distant = npcWithHits(harness, 846, 620, 20);
			final Npc otherLevel = npcWithHits(harness, 842,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 620, 20);
			final Npc summon = npcWithHits(harness, 840, 621, 20);
			summon.setAttribute("myworld_summon_owner", caster.getUsernameHash());
			final ProjectileEvent event = new ProjectileEvent(
				harness.world(), caster, primary, 0, 1, false);
			setField(event, "bloodSpell", Boolean.TRUE);
			invoke(event, "applyBloodRobeSplash",
				new Class<?>[] {Player.class, int.class},
				caster, Integer.valueOf(100));

			assertEquals(14, valid.getLevel(Skill.HITS.id()),
				"Blood robe splash percentage and child damage");
			assertHit(valid, 6, HitSplat.TYPE_ARMOR_PROC,
				"Blood robe child presentation");
			assertEquals(6, contribution(valid, caster,
				"getMageDamageInfoBy"), "Blood robe Magic contribution");
			assertTrue(caster.hasRecentSummonAssistEngagement(
				valid, SUMMON_ASSIST_WINDOW_MS),
				"Blood robe splash records summon-owner assist engagement");
			assertFalse(valid.isChasing(), "Blood robe splash adds no child aggro");
			assertEquals(20, caster.getLevel(Skill.HITS.id()),
				"Blood robe splash has no local lifesteal");
			assertEquals(20, distant.getLevel(Skill.HITS.id()),
				"Blood robe splash excludes distant NPCs");
			assertEquals(20, otherLevel.getLevel(Skill.HITS.id()),
				"Blood robe splash excludes cross-level NPCs");
			assertEquals(20, summon.getLevel(Skill.HITS.id()),
				"Blood robe splash excludes summons");

			final Npc lethal = npcWithHits(harness, 850, 620, 5);
			lethal.setShouldRespawn(false);
			final DeathObservation death = observeDeath(harness, lethal, caster);
			invoke(event, "inflictBloodRobeSplashDamage",
				new Class<?>[] {Player.class, Npc.class, int.class},
				caster, lethal, Integer.valueOf(7));
			assertEquals(5, contribution(lethal, caster,
				"getMageDamageInfoBy"), "Blood robe capped lethal contribution");
			assertEquals(KillType.MAGIC, caster.getKillType(),
				"Blood robe child kill type");
			assertDeath(death, 1, true, "Blood robe child death order");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
		assertNoTransactions(harness, "pre-migration Blood robe splash");
	}

	static void deathRobeSplashPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		int x = 880;
		for (DeathRobePath path : DeathRobePath.values()) {
			final Player caster = harness.player(
				"death robe " + path.ordinal(), x, 620);
			caster.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 20, 40, false);
			equipDeathRobes(harness, caster);
			final Npc primary = npcWithHits(harness, x + 1, 620, 20);
			final Npc valid = npcWithHits(harness, x + 2, 620, 20);
			final Npc distant = npcWithHits(harness, x + 6, 620, 20);
			final Object event = deathRobeEvent(
				path, harness, caster, primary);
			invoke(event, "applyDeathRobeOverkillSplash",
				new Class<?>[] {Player.class, Npc.class, int.class},
				caster, primary, Integer.valueOf(100));

			assertEquals(14, valid.getLevel(Skill.HITS.id()),
				path + " Death robe splash percentage");
			assertHit(valid, 6, HitSplat.TYPE_ARMOR_PROC,
				path + " Death robe child presentation");
			assertEquals(6, contribution(valid, caster,
				path.contributionMethod), path + " Death robe contribution style");
			assertTrue(caster.hasRecentSummonAssistEngagement(
				valid, SUMMON_ASSIST_WINDOW_MS),
				path + " Death robe summon-owner assist engagement");
			assertFalse(valid.isChasing(), path + " Death robe adds no child aggro");
			assertEquals(20, caster.getLevel(Skill.HITS.id()),
				path + " Death robe has no local lifesteal");
			assertEquals(20, distant.getLevel(Skill.HITS.id()),
				path + " Death robe excludes distant NPCs");

			final Player lethalCaster = harness.player(
				"death lethal " + path.ordinal(), x, 630);
			equipDeathRobes(harness, lethalCaster);
			final Npc lethalPrimary = npcWithHits(harness, x + 1, 630, 20);
			final Npc lethalChild = npcWithHits(harness, x + 2, 630, 5);
			lethalChild.setShouldRespawn(false);
			final DeathObservation death = observeDeath(
				harness, lethalChild, lethalCaster);
			invoke(deathRobeEvent(path, harness, lethalCaster, lethalPrimary),
				"applyDeathRobeOverkillSplash",
				new Class<?>[] {Player.class, Npc.class, int.class},
				lethalCaster, lethalPrimary, Integer.valueOf(120));
			assertEquals(path.killType, lethalCaster.getKillType(),
				path + " Death robe child kill type");
			assertDeath(death, 1, true,
				path + " Death robe child death order");
			x += 18;
		}
		assertNoTransactions(harness, "pre-migration Death robe splash");
	}

	static void scytheCleavePolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player player = harness.player("scythe child", 940, 620);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 40, false);
		harness.equip(player, DRAGONSTONE_BLOOD_AMULET, 1);
		final Npc primary = npcWithHits(harness, 941, 620, 20);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), player, primary);

		final Npc zero = npcWithHits(harness, 940, 621, 20);
		invoke(event, "inflictScytheCleaveDamage",
			new Class<?>[] {Player.class, Npc.class, int.class},
			player, zero, Integer.valueOf(0));
		assertHit(zero, 0, HitSplat.TYPE_STANDARD,
			"Scythe zero-hit presentation");
		assertTrue(zero.getPvmMeleeEvent() != null,
			"Scythe zero hit still establishes child aggro");

		final Npc nonlethal = npcWithHits(harness, 939, 620, 20);
		invoke(event, "inflictScytheCleaveDamage",
			new Class<?>[] {Player.class, Npc.class, int.class},
			player, nonlethal, Integer.valueOf(6));
		assertEquals(14, nonlethal.getLevel(Skill.HITS.id()),
			"Scythe positive child Hits");
		assertEquals(6, contribution(nonlethal, player,
			"getCombatDamageInfoBy"), "Scythe combat contribution");
		assertTrue(player.hasRecentSummonAssistEngagement(
			nonlethal, SUMMON_ASSIST_WINDOW_MS),
			"Scythe records summon-owner assist engagement");
		assertEquals(21, player.getLevel(Skill.HITS.id()),
			"Scythe applies the tier-five Blood Amulet lifesteal after damage");
		assertTrue(nonlethal.getLastOpponent() == player,
			"Scythe establishes last opponent before child aggro");
		assertTrue(nonlethal.getPvmMeleeEvent() != null,
			"Scythe positive hit establishes child aggro");

		final Npc lethal = npcWithHits(harness, 940, 630, 5);
		lethal.setShouldRespawn(false);
		final DeathObservation death = observeDeath(harness, lethal, player);
		invoke(event, "inflictScytheCleaveDamage",
			new Class<?>[] {Player.class, Npc.class, int.class},
			player, lethal, Integer.valueOf(7));
		assertHit(lethal, 7, HitSplat.TYPE_STANDARD,
			"Scythe lethal displayed overkill");
		assertEquals(5, contribution(lethal, player,
			"getCombatDamageInfoBy"), "Scythe capped lethal contribution");
		assertEquals(22, player.getLevel(Skill.HITS.id()),
			"Scythe lethal child lifesteal uses capped damage");
		assertEquals(KillType.COMBAT, player.getKillType(),
			"Scythe child kill type");
		assertDeath(death, 1, true, "Scythe child death order");

		final Npc otherLevel = npcWithHits(harness, 940,
			LegacyPackedPointAdapter.LEVEL_STRIDE + 620, 20);
		assertFalse(((Boolean) invoke(event, "isValidScytheCleaveTarget",
			new Class<?>[] {Player.class, Npc.class, Npc.class},
			player, primary, otherLevel)).booleanValue(),
			"Scythe excludes matching cross-level coordinates");
		assertNoTransactions(harness, "pre-migration Scythe cleave");
	}

	static void deathAmuletPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player player = harness.player("death amulet", 970, 620);
			player.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 20, 40, false);
			harness.equip(player, DRAGONSTONE_DEATH_AMULET, 1);
			final Npc killed = npcWithHits(harness, 971, 620, 1);
			final Npc valid = npcWithHits(harness, 970, 621, 40);
			final Npc distant = npcWithHits(harness, 975, 620, 40);
			final Npc otherLevel = npcWithHits(harness, 970,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 621, 40);
			final Npc summon = npcWithHits(harness, 969, 620, 40);
			summon.setAttribute("myworld_summon_owner", player.getUsernameHash());
			primeDeathAmulet(player, DRAGONSTONE_DEATH_AMULET, killed);
			player.applyDeathAmuletBurst(killed);

			final int burstDamage = 40 - valid.getLevel(Skill.HITS.id());
			assertTrue(burstDamage >= 10 && burstDamage <= 20,
				"Death Amulet retains its tier-five 10..20 damage roll");
			assertHit(valid, burstDamage, HitSplat.TYPE_ARMOR_PROC,
				"Death Amulet child presentation");
			assertEquals(burstDamage, contribution(valid, player,
				"getCombatDamageInfoBy"), "Death Amulet combat contribution");
			assertFalse(player.hasRecentSummonAssistEngagement(
				valid, SUMMON_ASSIST_WINDOW_MS),
				"Death Amulet intentionally records no summon assist");
			assertFalse(valid.isChasing(), "Death Amulet adds no child aggro");
			assertEquals(20, player.getLevel(Skill.HITS.id()),
				"Death Amulet has no local lifesteal");
			assertEquals(40, distant.getLevel(Skill.HITS.id()),
				"Death Amulet excludes distant NPCs");
			assertEquals(40, otherLevel.getLevel(Skill.HITS.id()),
				"Death Amulet excludes cross-level NPCs");
			assertEquals(40, summon.getLevel(Skill.HITS.id()),
				"Death Amulet excludes summons");
			assertEquals(0, deathAmuletCharge(player,
				DRAGONSTONE_DEATH_AMULET),
				"Death Amulet spends exactly one completed burst charge");

			final Player suppressed = harness.player("death amulet guard", 990, 620);
			harness.equip(suppressed, DRAGONSTONE_DEATH_AMULET, 1);
			final Npc suppressedKilled = npcWithHits(harness, 991, 620, 1);
			final Npc suppressedChild = npcWithHits(harness, 990, 621, 40);
			primeDeathAmulet(suppressed, DRAGONSTONE_DEATH_AMULET,
				suppressedKilled);
			final int chargeBefore = deathAmuletCharge(
				suppressed, DRAGONSTONE_DEATH_AMULET);
			installGuardDog(harness, suppressed, 989, 621);
			suppressed.applyDeathAmuletBurst(suppressedKilled);
			assertEquals(chargeBefore, deathAmuletCharge(
				suppressed, DRAGONSTONE_DEATH_AMULET),
				"Death Amulet suppression occurs before charge mutation");
			assertEquals(40, suppressedChild.getLevel(Skill.HITS.id()),
				"guard-dog suppression prevents Death Amulet burst");

			final Player lethalPlayer = harness.player("death amulet lethal", 970, 650);
			harness.equip(lethalPlayer, SAPPHIRE_DEATH_AMULET, 1);
			final Npc lethalKilled = npcWithHits(harness, 971, 650, 1);
			final Npc lethalChild = npcWithHits(harness, 970, 651, 1);
			lethalChild.setShouldRespawn(false);
			final DeathObservation death = observeDeath(
				harness, lethalChild, lethalPlayer);
			primeDeathAmulet(lethalPlayer, SAPPHIRE_DEATH_AMULET,
				lethalKilled);
			lethalPlayer.applyDeathAmuletBurst(lethalKilled);
			assertEquals(1, contribution(lethalChild, lethalPlayer,
				"getCombatDamageInfoBy"),
				"Death Amulet lethal contribution is capped to child Hits");
			assertDeath(death, 1, true,
				"Death Amulet publishes presentation before child death");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
		assertNoTransactions(harness, "pre-migration Death Amulet");
	}

	static void deathRingPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player player = harness.player("death ring", 700, 700);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 40, false);
		harness.equip(player, DRAGONSTONE_DEATH_RING, 1);
		final com.openrsc.server.model.container.Item ring = player
			.getCarriedItems().getEquipment().getEquippedRingItem();
		EnchantingItemEffects.setDeathRingChargePoints(
			player, ring, 100);

		final Npc distant = npcWithHits(harness, 720, 700, 20);
		assertFalse(player.applyDeathRingChargeHit(distant),
			"Death Ring nonlethal result");
		assertEquals(10, distant.getLevel(Skill.HITS.id()),
			"Death Ring helper retains caller-owned range eligibility");
		assertHit(distant, 10, HitSplat.TYPE_ARMOR_PROC,
			"Death Ring child presentation");
		assertEquals(10, contribution(distant, player,
			"getCombatDamageInfoBy"), "Death Ring combat contribution");
		assertTrue(player.hasRecentSummonAssistEngagement(
			distant, SUMMON_ASSIST_WINDOW_MS),
			"Death Ring records summon-owner assist engagement");
		assertFalse(distant.isChasing(), "Death Ring adds no local child aggro");
		assertEquals(20, player.getLevel(Skill.HITS.id()),
			"Death Ring has no local lifesteal");
		assertEquals(100, EnchantingItemEffects.getDeathRingChargePoints(
			player, ring), "Death Ring hit does not consume stored charge");

		final Npc lethal = npcWithHits(harness, 722, 700, 5);
		lethal.setShouldRespawn(false);
		final DeathObservation death = observeDeath(harness, lethal, player);
		assertTrue(player.applyDeathRingChargeHit(lethal),
			"Death Ring reports lethal settlement to its caller");
		assertEquals(0, death.count.get(),
			"Death Ring helper does not own terminal child death");
		assertHit(lethal, 10, HitSplat.TYPE_ARMOR_PROC,
			"Death Ring lethal displayed overkill");
		lethal.setLastCombatState(CombatState.LOST);
		player.setKillType(KillType.COMBAT);
		lethal.killedBy(player);
		assertDeath(death, 1, true,
			"Death Ring caller-owned death follows child presentation");

		final Npc summon = npcWithHits(harness, 724, 700, 20);
		summon.setAttribute("myworld_summon_owner", player.getUsernameHash());
		assertFalse(player.applyDeathRingChargeHit(summon),
			"Death Ring rejects summon targets before settlement");
		assertEquals(20, summon.getLevel(Skill.HITS.id()),
			"Death Ring leaves rejected summons unchanged");
		assertNoTransactions(harness, "pre-migration Death Ring");
	}

	private static Object meleeEvent(final MeleeChildPath path,
			final CurrentCombatHarness harness, final Mob source,
			final Mob target) {
		return path == MeleeChildPath.RECIPROCAL
			? new CombatEvent(harness.world(), source, target)
			: new PvmMeleeEvent(harness.world(), source, target);
	}

	private static Object deathRobeEvent(final DeathRobePath path,
			final CurrentCombatHarness harness, final Player source,
			final Npc target) {
		switch (path) {
			case RECIPROCAL:
				return new CombatEvent(harness.world(), source, target);
			case PVM:
				return new PvmMeleeEvent(harness.world(), source, target);
			case PROJECTILE_MAGIC:
				return new ProjectileEvent(
					harness.world(), source, target, 0, 1, false);
			case PROJECTILE_RANGED:
				return new ProjectileEvent(
					harness.world(), source, target, 0, 2, false);
			default:
				throw new AssertionError("Unhandled Death robe path " + path);
		}
	}

	private static void equipBloodRobes(final CurrentCombatHarness harness,
			final Player player) throws Exception {
		harness.equip(player, ItemId.BLOOD_WOOL_WIZARD_HAT.id(), 1);
		harness.equip(player, ItemId.BLOOD_WOOL_ROBE_TOP.id(), 1);
		harness.equip(player, ItemId.BLOOD_WOOL_ROBE_SKIRT.id(), 1);
	}

	private static void equipDeathRobes(final CurrentCombatHarness harness,
			final Player player) throws Exception {
		harness.equip(player, ItemId.DEATH_WOOL_WIZARD_HAT.id(), 1);
		harness.equip(player, ItemId.DEATH_WOOL_ROBE_TOP.id(), 1);
		harness.equip(player, ItemId.DEATH_WOOL_ROBE_SKIRT.id(), 1);
	}

	private static void primeDeathAmulet(final Player player,
			final int itemId, final Npc killed) {
		final com.openrsc.server.model.container.Item amulet = player
			.getCarriedItems().getEquipment().getEquippedWristItem();
		final int gained = EnchantingItemEffects
			.getDeathAmuletBurstChargePointsForNpc(killed.getNPCCombatLevel());
		EnchantingItemEffects.setDeathAmuletBurstChargePoints(
			player, amulet, Math.max(0, EnchantingItemEffects
				.getDeathAmuletBurstChargeRequiredPoints() - gained));
		assertEquals(itemId, amulet.getCatalogId(),
			"expected Death Amulet equipment identity");
	}

	private static int deathAmuletCharge(final Player player,
			final int itemId) {
		final com.openrsc.server.model.container.Item amulet = player
			.getCarriedItems().getEquipment().getEquippedWristItem();
		assertEquals(itemId, amulet.getCatalogId(),
			"Death Amulet charge item identity");
		return EnchantingItemEffects.getDeathAmuletBurstChargePoints(
			player, amulet);
	}

	private static void installGuardDog(final CurrentCombatHarness harness,
			final Player owner, final int x, final int y) {
		final Npc guard = npcWithHits(harness, x, y, 20);
		guard.setAttribute("myworld_summon_owner", owner.getUsernameHash());
		guard.setAttribute("myworld_summon_kind", "guard_dog");
		guard.setAttribute("myworld_summon_current_hits", 20);
		owner.setAttribute("myworld_manual_summon", guard);
		assertTrue(Summoning.isPlayerAreaEffectSuppressed(owner),
			"guard-dog fixture activates area-effect suppression");
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int x, final int y, final int hits) {
		final Npc npc = harness.npc(NpcId.GREATER_DEMON.id(), x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, hits, false);
		return npc;
	}

	private static DeathObservation observeDeath(
			final CurrentCombatHarness harness, final Npc npc,
			final Player source) {
		final DeathObservation observation = new DeathObservation();
		npc.addDeathListener(new NpcLootEvent(
			harness.world(), npc.getLocation(), npc.getID(), 1,
			ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				observation.count.incrementAndGet();
				observation.presentationVisible.set(
					npc.getUpdateFlags().getDamage().get() != null
						&& !npc.getUpdateFlags().getHitSplats().isEmpty());
			}
		});
		return observation;
	}

	private static int contribution(final Npc target, final Player source,
			final String method) throws Exception {
		@SuppressWarnings("unchecked")
		final Pair<Integer, Long> info = (Pair<Integer, Long>) invoke(
			target, method, new Class<?>[] {java.util.UUID.class},
			source.getUUID());
		return info.getLeft().intValue();
	}

	private static Object invoke(final Object target, final String method,
			final Class<?>[] parameterTypes, final Object... arguments)
			throws Exception {
		return CurrentCombatHarness.invokePrivate(
			target, method, parameterTypes, arguments);
	}

	private static void setField(final Object target, final String fieldName,
			final Object value) throws Exception {
		final Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void assertNoTransactions(
			final CurrentCombatHarness harness, final String label) {
		final List<DamageResult> results = CurrentCombatCharacterizationTest
			.observedDamageResults(harness);
		assertEquals(0, results.size(), label + " transaction cardinality");
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

	private static void assertDeath(final DeathObservation observation,
			final int expectedCount, final boolean presentationVisible,
			final String label) {
		assertEquals(expectedCount, observation.count.get(),
			label + " callback cardinality");
		assertEquals(presentationVisible, observation.presentationVisible.get(),
			label + " presentation visibility");
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

	private enum MeleeChildPath {
		RECIPROCAL,
		PVM
	}

	private enum DeathRobePath {
		RECIPROCAL("getCombatDamageInfoBy", KillType.COMBAT),
		PVM("getCombatDamageInfoBy", KillType.COMBAT),
		PROJECTILE_MAGIC("getMageDamageInfoBy", KillType.MAGIC),
		PROJECTILE_RANGED("getRangeDamageInfoBy", KillType.RANGED);

		private final String contributionMethod;
		private final KillType killType;

		DeathRobePath(final String contributionMethod,
				final KillType killType) {
			this.contributionMethod = contributionMethod;
			this.killType = killType;
		}
	}

	private static final class DeathObservation {
		private final AtomicInteger count = new AtomicInteger();
		private final AtomicBoolean presentationVisible = new AtomicBoolean();
	}
}
