package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.content.party.PartyRank;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.ElderGreenDragonSpecialAttacks;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable A05.4D owned NPC/summon damage parity specifications. */
final class CurrentCombatOwnedDamageCharacterization {
	private static final String SUMMON_OWNER_KEY = "myworld_summon_owner";
	private static final String SUMMON_KIND_KEY = "myworld_summon_kind";
	private static final String SUMMON_CURRENT_HITS_KEY =
		"myworld_summon_current_hits";
	private static final String MANUAL_SUMMON_KEY = "myworld_manual_summon";
	private static final String SUMMON_GUARD_ENEMY_KEY =
		"myworld_summon_guard_enemy";

	private CurrentCombatOwnedDamageCharacterization() {
	}

	static void hellsInfernoSplashMath(final CurrentCombatHarness harness) {
		assertEquals(18, CombatEffectUtil.HELLS_INFERNO_MAX_HIT,
			"Hell's Inferno tier-11 maximum");
		assertEquals(2, CombatEffectUtil.HELLS_INFERNO_SPLASH_RADIUS,
			"Hell's Inferno splash radius");
		assertEquals(0, CombatEffectUtil.hellsInfernoSplashDamage(-1),
			"negative primary damage cannot splash");
		assertEquals(0, CombatEffectUtil.hellsInfernoSplashDamage(0),
			"zero primary damage cannot splash");
		assertEquals(1, CombatEffectUtil.hellsInfernoSplashDamage(1),
			"one damage rounds up to one");
		assertEquals(1, CombatEffectUtil.hellsInfernoSplashDamage(2),
			"two damage halves to one");
		assertEquals(2, CombatEffectUtil.hellsInfernoSplashDamage(3),
			"odd primary damage rounds up");
		assertEquals(9, CombatEffectUtil.hellsInfernoSplashDamage(18),
			"maximum primary damage halves to nine");
	}

	static void hellsInfernoSplashPolicies(final CurrentCombatHarness harness)
			throws Exception {
		final Player meleeOwner = harness.player("inferno melee", 500, 500);
		final Npc meleePrimary = npcWithHits(harness, NpcId.CHICKEN.id(), 501, 500, 20);
		final Npc meleeSecondary = npcWithHits(harness, NpcId.CHICKEN.id(), 502, 500, 20);
		final Npc meleeDistant = npcWithHits(harness, NpcId.CHICKEN.id(), 506, 500, 20);
		final PvmMeleeEvent melee = new PvmMeleeEvent(
			harness.world(), meleeOwner, meleePrimary);
		invoke(melee, "applyHellsInfernoSplash",
			new Class<?>[] {Player.class, Npc.class, int.class},
			meleeOwner, meleePrimary, Integer.valueOf(5));
		assertEquals(17, meleeSecondary.getLevel(Skill.HITS.id()),
			"modern PvM melee splash uses half actual damage rounded up");
		assertEquals(20, meleePrimary.getLevel(Skill.HITS.id()),
			"modern PvM melee splash excludes its primary target");
		assertEquals(20, meleeDistant.getLevel(Skill.HITS.id()),
			"modern PvM melee splash respects radius two");
		assertEquals(CombatEffect.HELLS_INFERNO,
			meleeSecondary.getUpdateFlags().getCombatEffect().get().getEffectType(),
			"modern PvM melee splash presents Hell's Inferno");

		final Npc guardBlocked = npcWithHits(harness, NpcId.CHICKEN.id(), 502, 501, 20);
		installGuardDog(harness, meleeOwner, 499, 500);
		invoke(melee, "applyHellsInfernoSplash",
			new Class<?>[] {Player.class, Npc.class, int.class},
			meleeOwner, meleePrimary, Integer.valueOf(5));
		assertEquals(20, guardBlocked.getLevel(Skill.HITS.id()),
			"Guard Dog suppresses Hell's Inferno secondary damage");

		final Player projectileOwner = harness.player("inferno projectile", 600, 600);
		final Npc projectilePrimary = npcWithHits(harness, NpcId.CHICKEN.id(), 601, 600, 20);
		final Npc projectileSecondary = npcWithHits(harness, NpcId.CHICKEN.id(), 602, 600, 20);
		final ProjectileEvent projectile = new ProjectileEvent(
			harness.world(), projectileOwner, projectilePrimary, 0, 2, false);
		invoke(projectile, "applyHellsInfernoSplash",
			new Class<?>[] {Player.class, Npc.class, int.class},
			projectileOwner, projectilePrimary, Integer.valueOf(6));
		assertEquals(17, projectileSecondary.getLevel(Skill.HITS.id()),
			"projectile splash matches modern PvM melee damage");

		final Player legacyOwner = harness.player("inferno legacy", 700, 700);
		final Npc legacyPrimary = npcWithHits(harness, NpcId.CHICKEN.id(), 701, 700, 20);
		final Npc legacySecondary = npcWithHits(harness, NpcId.CHICKEN.id(), 702, 700, 20);
		final CombatEvent legacy = new CombatEvent(
			harness.world(), legacyOwner, legacyPrimary);
		invoke(legacy, "applyHellsInfernoSplash",
			new Class<?>[] {Player.class, Npc.class, int.class},
			legacyOwner, legacyPrimary, Integer.valueOf(6));
		assertEquals(17, legacySecondary.getLevel(Skill.HITS.id()),
			"legacy melee splash matches modern PvM and projectile damage");
	}

	static void balrogSplashPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Npc balrog = harness.npc(NpcId.BALROG.id(), 700, 820);
			final Player primary = harness.player("balrog primary", 701, 820);
			final Player valid = harness.player("balrog valid", 702, 820);
			valid.activateMagicResistancePotion(50, 60_000L);
			harness.recordOutgoingPackets(valid);
			final Player lethal = harness.player("balrog lethal", 701, 821);
			lethal.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 3, 40, false);
			final Player distant = harness.player("balrog distant", 706, 820);
			final Player otherLevel = harness.player("balrog level", 702,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 820);
			final Player guarded = harness.player("balrog guarded", 699, 820);
			final Npc guard = installGuardDog(harness, guarded, 698, 820);
			final Npc claimedEnemy = npcWithHits(
				harness, NpcId.GREATER_DEMON.id(), 697, 820, 20);
			claimedEnemy.setOpponent(guarded);
			guard.setAttribute(SUMMON_GUARD_ENEMY_KEY, claimedEnemy);
			final ProjectileEvent event = new ProjectileEvent(
				harness.world(), balrog, primary, 0, 1, false);
			setField(event, "impactEffectType",
				Integer.valueOf(CombatEffect.ELDER_DRAGON_FIRESHOT));

			invoke(event, "applyBalrogMagicSplash",
				new Class<?>[] {Npc.class, Player.class, int.class},
				balrog, primary, Integer.valueOf(10));

			assertEquals(38, valid.getLevel(Skill.HITS.id()),
				"Balrog splash applies fire Magic then potion mitigation");
			assertHit(valid, 2, HitSplat.TYPE_ARMOR_PROC,
				"Balrog splash presentation");
			assertEquals(2, valid.getTrackedDamage(balrog),
				"Balrog splash damage tracking");
			assertEquals(0, valid.getTrackedBlockedDamage(balrog),
				"Balrog splash records no Cleric blocked damage");
			assertEquals(CombatEffect.ELDER_DRAGON_FIRESHOT,
				valid.getUpdateFlags().getCombatEffect().get().getEffectType(),
				"Balrog splash inherits the projectile impact effect");
			assertEquals(1, harness.countOutgoingPackets(
				valid, OpcodeOut.SEND_STAT),
				"Balrog splash sends one player Hits packet");
			assertTrue(lethal.killed,
				"Balrog splash directly settles a lethal child player");
			assertEquals(40, primary.getLevel(Skill.HITS.id()),
				"Balrog splash excludes the primary target");
			assertEquals(40, distant.getLevel(Skill.HITS.id()),
				"Balrog splash excludes distant players");
			assertEquals(40, otherLevel.getLevel(Skill.HITS.id()),
				"Balrog splash excludes matching cross-level coordinates");
			assertEquals(40, guarded.getLevel(Skill.HITS.id()),
				"Balrog splash respects Guard Dog target exclusion");

			final Npc blockedBalrog = harness.npc(
				NpcId.BALROG.id(), 720, 820);
			final Player blockedPrimary = harness.player(
				"balrog block primary", 721, 820);
			final Player blocked = harness.player("balrog block", 722, 820);
			equipExaltedRune(harness, blocked);
			harness.recordOutgoingPackets(blocked);
			forceNextLegacyDoubleBelow(0.30D);
			final ProjectileEvent blockedEvent = new ProjectileEvent(
				harness.world(), blockedBalrog, blockedPrimary, 0, 1, false);
			setField(blockedEvent, "impactEffectType",
				Integer.valueOf(CombatEffect.ELDER_DRAGON_FIRESHOT));
			invoke(blockedEvent, "applyBalrogMagicSplash",
				new Class<?>[] {Npc.class, Player.class, int.class},
				blockedBalrog, blockedPrimary, Integer.valueOf(10));
			assertNoHit(blocked,
				"Balrog True Defense block emits no damage hitsplat");
			assertEquals(40, blocked.getLevel(Skill.HITS.id()),
				"Balrog True Defense block preserves Hits");
			assertEquals(CombatEffect.TRUE_DEFENSE,
				blocked.getUpdateFlags().getCombatEffect().get().getEffectType(),
				"Balrog impact effect cannot overwrite True Defense");
			assertEquals(1, harness.countOutgoingPackets(
				blocked, OpcodeOut.SEND_STAT),
				"Balrog True Defense block retains one Hits packet");
			assertEquals(-1, blocked.getTrackedDamage(blockedBalrog),
				"Balrog fully blocked splash is not damage tracked");
			final List<DamageResult> results =
				CurrentCombatCharacterizationTest.observedDamageResults(harness);
			assertEquals(2, results.size(),
				"Balrog publishes only the two eligible positive child hits");
			assertDamageResult(resultFor(results, valid,
				"projectile-balrog-magic-splash"), balrog, valid,
				"projectile-balrog-magic-splash", CombatStyle.MAGIC,
				2, 2, 0, false, HitSplat.TYPE_ARMOR_PROC, event.getUUID(),
				"Balrog mitigated child transaction");
			assertDamageResult(resultFor(results, lethal,
				"projectile-balrog-magic-splash"), balrog, lethal,
				"projectile-balrog-magic-splash", CombatStyle.MAGIC,
				5, 3, 2, true, HitSplat.TYPE_ARMOR_PROC, event.getUUID(),
				"Balrog lethal child transaction");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
	}

	static void elderGreenDragonPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Npc dragon = npcWithHits(
			harness, NpcId.ELDER_GREEN_DRAGON.id(), 740, 820, 40);
		final Player melee = harness.player("elder melee", 741, 820);
		final Player ally = harness.player("elder ally", 742, 820);
		party(melee, ally);
		harness.recordOutgoingPackets(melee);
		harness.recordOutgoingPackets(ally);
		assertEquals(6, inflictElderDamage(
			dragon, melee, 6, "MELEE", HitSplat.TYPE_STANDARD, true),
			"Elder melee sweep return value");
		assertHit(melee, 6, HitSplat.TYPE_STANDARD,
			"Elder melee sweep presentation");
		assertEquals(-1, melee.getTrackedDamage(dragon),
			"Elder formula-owned melee tracking is not duplicated");
		assertEquals(1, harness.countOutgoingPackets(
			melee, OpcodeOut.SEND_STAT),
			"Elder damage sends one player Hits packet");
		assertEquals(1, harness.countOutgoingPackets(
			melee, OpcodeOut.SEND_PARTY),
			"Elder damage refreshes the victim party view");
		assertEquals(1, harness.countOutgoingPackets(
			ally, OpcodeOut.SEND_PARTY),
			"Elder damage refreshes allied party views");
		assertTrue(melee.getLastOpponent() == dragon,
			"Elder surviving victim retains combat opponent state");

		final Player ranged = harness.player("elder ranged", 743, 820);
		ranged.activateRangedResistancePotion(50, 60_000L);
		assertEquals(4, inflictElderDamage(
			dragon, ranged, 8, "RANGED", HitSplat.TYPE_ARMOR_PROC, false),
			"Elder fireshot uses Ranged potion mitigation");
		assertEquals(4, ranged.getTrackedDamage(dragon),
			"Elder fireshot owns damage tracking when requested");

		final Player blocked = harness.player("elder blocked", 744, 820);
		equipExaltedRune(harness, blocked);
		forceNextLegacyDoubleBelow(0.30D);
		assertEquals(0, inflictElderDamage(
			dragon, blocked, 6, "MELEE", HitSplat.TYPE_STANDARD, false),
			"Elder primary style retains True Defense");
		assertHit(blocked, 0, HitSplat.TYPE_STANDARD,
			"Elder True Defense retains explicit zero presentation");
		assertEquals(0, blocked.getTrackedDamage(dragon),
			"Elder zero settlement remains tracking-visible");

		final Player burned = harness.player("elder burn", 745, 820);
		equipExaltedRune(harness, burned);
		forceNextLegacyDoubleBelow(0.30D);
		assertEquals(4, inflictElderDamage(
			dragon, burned, 4, "BURN", HitSplat.TYPE_ARMOR_PROC, false),
			"Elder burn remains excluded from True Defense");
		assertHit(burned, 4, HitSplat.TYPE_ARMOR_PROC,
			"Elder burn presentation");

		final Player saved = harness.player("elder life", 746, 820);
		harness.equip(saved, ItemId.RING_OF_LIFE.id(), 1);
		saved.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 4, 40, false);
		final int savedX = saved.getX();
		final int savedY = saved.getY();
		assertEquals(2, inflictElderDamage(
			dragon, saved, 2, "RANGED", HitSplat.TYPE_ARMOR_PROC, false),
			"Elder nonlethal Ring of Life damage");
		assertTrue(saved.getX() != savedX || saved.getY() != savedY,
			"Elder nonlethal victim retains Ring of Life check");

		final Npc reflectedDragon = npcWithHits(
			harness, NpcId.ELDER_GREEN_DRAGON.id(), 750, 820, 5);
		reflectedDragon.setShouldRespawn(false);
		final Player divine = divineDefender(harness, "elder divine", 751, 820);
		final AtomicInteger dragonDeaths = new AtomicInteger();
		final AtomicBoolean incomingVisibleAtDeath = new AtomicBoolean();
		observeNpcDeath(harness, reflectedDragon, dragonDeaths,
			incomingVisibleAtDeath, divine);
		forceNextLegacyDoubleBelow(0.20D);
		assertEquals(3, inflictElderDamage(reflectedDragon, divine, 3,
			"RANGED", HitSplat.TYPE_ARMOR_PROC, false),
			"Elder reflected hit return value");
		assertEquals(1, dragonDeaths.get(),
			"Elder caller owns reflected dragon death");
		assertTrue(incomingVisibleAtDeath.get(),
			"Elder incoming presentation precedes reflected dragon death");
		assertEquals(37, divine.getLevel(Skill.HITS.id()),
			"Elder reflection does not roll back the incoming hit");

		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player sameLevel = harness.player("elder valid level", 741, 821);
			final Player otherLevel = harness.player("elder wrong level", 741,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 821);
			assertTrue(isValidElderTarget(dragon, sameLevel, 6),
				"Elder targeting accepts same-level nearby players");
			assertFalse(isValidElderTarget(dragon, otherLevel, 6),
				"Elder targeting rejects matching cross-level coordinates");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
		final List<DamageResult> observed =
			CurrentCombatCharacterizationTest.observedDamageResults(harness);
		assertEquals(7, observed.size(),
			"Elder publishes six incoming hits and one Divine reflection");
		assertDamageResult(resultFor(observed, melee,
			"elder-green-dragon-melee-sweep"), dragon, melee,
			"elder-green-dragon-melee-sweep", CombatStyle.MELEE,
			6, 6, 0, false, HitSplat.TYPE_STANDARD, null,
			"Elder melee transaction");
		assertDamageResult(resultFor(observed, ranged,
			"elder-green-dragon-ranged-fireshot"), dragon, ranged,
			"elder-green-dragon-ranged-fireshot", CombatStyle.RANGED,
			4, 4, 0, false, HitSplat.TYPE_ARMOR_PROC, null,
			"Elder ranged transaction");
		assertDamageResult(resultFor(observed, blocked,
			"elder-green-dragon-melee-sweep"), dragon, blocked,
			"elder-green-dragon-melee-sweep", CombatStyle.MELEE,
			0, 0, 0, false, HitSplat.TYPE_STANDARD, null,
			"Elder True Defense zero transaction");
		assertDamageResult(resultFor(observed, burned,
			"elder-green-dragon-burn-pulse"), dragon, burned,
			"elder-green-dragon-burn-pulse", null,
			4, 4, 0, false, HitSplat.TYPE_ARMOR_PROC, null,
			"Elder burn transaction");
		assertDamageResult(resultFor(observed, saved,
			"elder-green-dragon-ranged-fireshot"), dragon, saved,
			"elder-green-dragon-ranged-fireshot", CombatStyle.RANGED,
			2, 2, 0, false, HitSplat.TYPE_ARMOR_PROC, null,
			"Elder Ring of Life transaction");
		assertDamageResult(resultFor(observed, divine,
			"elder-green-dragon-ranged-fireshot"), reflectedDragon, divine,
			"elder-green-dragon-ranged-fireshot", CombatStyle.RANGED,
			3, 3, 0, false, HitSplat.TYPE_ARMOR_PROC, null,
			"Elder reflected-hit incoming transaction");
		assertEquals("elder-green-dragon-ranged-fireshot",
			observed.get(5).getRequest().getEffectKey(),
			"Elder incoming damage is published before reflection");
		assertEquals("divine-retribution",
			observed.get(6).getRequest().getEffectKey(),
			"Divine reflection follows Elder incoming publication");
	}

	static void summonBonusDamagePolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player owner = harness.player("bonus owner", 780, 820);
		final Npc summon = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 781, 820, 20);
		summon.setAttribute(SUMMON_OWNER_KEY, owner.getUsernameHash());
		final Npc lethalNpc = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 782, 820, 5);
		final AtomicInteger npcDeaths = new AtomicInteger();
		observeNpcDeath(harness, lethalNpc, npcDeaths,
			new AtomicBoolean(), owner);
		assertTrue(inflictSummonBonusDamage(summon, lethalNpc, 7, false),
			"Summon bonus reports lethal NPC settlement");
		assertHit(lethalNpc, 7, HitSplat.TYPE_ARMOR_PROC,
			"Summon bonus displayed overkill");
		assertEquals(5, summonContribution(lethalNpc, owner),
			"Summon bonus caps owner contribution to target Hits");
		assertEquals(0, npcDeaths.get(),
			"Summon bonus returns lethality without owning NPC death");

		final Player magicTarget = harness.player("bonus magic", 783, 820);
		magicTarget.activateMagicResistancePotion(50, 60_000L);
		magicTarget.activateMeleeResistancePotion(75, 60_000L);
		harness.recordOutgoingPackets(magicTarget);
		assertFalse(inflictSummonBonusDamage(
			summon, magicTarget, 8, true),
			"Summon Magic bonus nonlethal result");
		assertEquals(36, magicTarget.getLevel(Skill.HITS.id()),
			"Summon Magic bonus applies only Magic potion mitigation");
		assertEquals(1, harness.countOutgoingPackets(
			magicTarget, OpcodeOut.SEND_STAT),
			"Summon Magic bonus sends one Hits packet");

		final Player meleeTarget = harness.player("bonus melee", 784, 820);
		meleeTarget.activateMagicResistancePotion(75, 60_000L);
		meleeTarget.activateMeleeResistancePotion(50, 60_000L);
		assertFalse(inflictSummonBonusDamage(
			summon, meleeTarget, 8, false),
			"Summon Melee bonus nonlethal result");
		assertEquals(36, meleeTarget.getLevel(Skill.HITS.id()),
			"Summon Melee bonus applies only Melee potion mitigation");

		final Player lethalPlayer = harness.player("bonus lethal", 785, 820);
		lethalPlayer.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 5, 40, false);
		assertTrue(inflictSummonBonusDamage(
			summon, lethalPlayer, 7, true),
			"Summon bonus reports lethal player settlement");
		assertFalse(lethalPlayer.killed,
			"Summon bonus does not own player death");

		final Npc noOwnerSummon = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 786, 820, 20);
		noOwnerSummon.setAttribute(SUMMON_OWNER_KEY, 999_999_999L);
		final Npc noOwnerTarget = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 787, 820, 20);
		assertFalse(inflictSummonBonusDamage(
			noOwnerSummon, noOwnerTarget, 4, false),
			"Offline summon-owner bonus nonlethal result");
		assertFalse(noOwnerTarget.hasDamageBy(owner),
			"Offline summon owner receives no borrowed contribution");

		final List<DamageResult> results =
			CurrentCombatCharacterizationTest.observedDamageResults(harness);
		assertEquals(5, results.size(),
			"Summon bonus publishes one result per effective hit");
		assertDamageResult(resultFor(results, lethalNpc, "summon-bonus-melee"),
			summon, lethalNpc, "summon-bonus-melee", CombatStyle.MELEE,
			7, 5, 2, true, HitSplat.TYPE_ARMOR_PROC, null,
			"Summon lethal NPC bonus transaction");
		assertDamageResult(resultFor(results, magicTarget, "summon-bonus-magic"),
			summon, magicTarget, "summon-bonus-magic", CombatStyle.MAGIC,
			4, 4, 0, false, HitSplat.TYPE_ARMOR_PROC, null,
			"Summon Magic bonus transaction");
		assertDamageResult(resultFor(results, meleeTarget, "summon-bonus-melee"),
			summon, meleeTarget, "summon-bonus-melee", CombatStyle.MELEE,
			4, 4, 0, false, HitSplat.TYPE_ARMOR_PROC, null,
			"Summon Melee bonus transaction");
		assertDamageResult(resultFor(results, lethalPlayer, "summon-bonus-magic"),
			summon, lethalPlayer, "summon-bonus-magic", CombatStyle.MAGIC,
			7, 5, 2, true, HitSplat.TYPE_ARMOR_PROC, null,
			"Summon lethal player bonus transaction");
		assertDamageResult(resultFor(results, noOwnerTarget, "summon-bonus-melee"),
			noOwnerSummon, noOwnerTarget, "summon-bonus-melee", CombatStyle.MELEE,
			4, 4, 0, false, HitSplat.TYPE_ARMOR_PROC, null,
			"Offline-owner summon bonus transaction");
	}

	private static int inflictElderDamage(final Npc dragon,
			final Player player, final int damage, final String styleName,
			final int hitSplatType, final boolean damageAlreadyTracked)
			throws Exception {
		final Class<?> styleClass = Class.forName(
			"com.openrsc.server.event.rsc.impl.combat."
				+ "ElderGreenDragonSpecialAttacks$DamageStyle");
		@SuppressWarnings({"rawtypes", "unchecked"})
		final Object style = Enum.valueOf((Class) styleClass, styleName);
		final Method method = ElderGreenDragonSpecialAttacks.class
			.getDeclaredMethod("inflictPlayerDamage", Npc.class, Player.class,
				int.class, styleClass, int.class, boolean.class);
		method.setAccessible(true);
		return ((Integer) method.invoke(null, dragon, player,
			Integer.valueOf(damage), style, Integer.valueOf(hitSplatType),
			Boolean.valueOf(damageAlreadyTracked))).intValue();
	}

	private static boolean isValidElderTarget(final Npc dragon,
			final Player player, final int radius) throws Exception {
		final Method method = ElderGreenDragonSpecialAttacks.class
			.getDeclaredMethod("isValidPlayerTarget",
				Npc.class, Player.class, int.class);
		method.setAccessible(true);
		return ((Boolean) method.invoke(null, dragon, player,
			Integer.valueOf(radius))).booleanValue();
	}

	private static boolean inflictSummonBonusDamage(final Npc summon,
			final Mob target, final int damage, final boolean magicDamage)
			throws Exception {
		final Method method = Summoning.class.getDeclaredMethod(
			"inflictSummonBonusDamage",
			Npc.class, Mob.class, int.class, boolean.class);
		method.setAccessible(true);
		return ((Boolean) method.invoke(null, summon, target,
			Integer.valueOf(damage), Boolean.valueOf(magicDamage))).booleanValue();
	}

	private static void equipExaltedRune(final CurrentCombatHarness harness,
			final Player player) throws Exception {
		for (int itemId : new int[] {
			MyWorldItemId.EXALTED_RUNE_HELMET,
			MyWorldItemId.EXALTED_RUNE_PLATE_MAIL_BODY,
			MyWorldItemId.EXALTED_RUNE_PLATE_MAIL_LEGS,
			MyWorldItemId.EXALTED_RUNE_GAUNTLETS,
			MyWorldItemId.EXALTED_RUNE_GREAVES,
			MyWorldItemId.EXALTED_RUNE_SQUARE_SHIELD}) {
			harness.equip(player, itemId, 1);
		}
	}

	private static Player divineDefender(final CurrentCombatHarness harness,
			final String name, final int x, final int y) throws Exception {
		final Player defender = harness.player(name, x, y);
		defender.setPrayerBook(PrayerCatalog.GodLine.ZAMORAK);
		harness.equip(defender, ItemId.ZAMORAK_MACE.id(), 1);
		defender.getPrayers().setPrayer(
			Prayers.DIVINE_RETRIBUTION, true, false);
		return defender;
	}

	private static Party party(final Player... players) throws Exception {
		final Party party = new Party(players[0].getWorld());
		final Constructor<PartyPlayer> constructor =
			PartyPlayer.class.getDeclaredConstructor(String.class);
		constructor.setAccessible(true);
		final Method setReference = PartyPlayer.class.getDeclaredMethod(
			"setPlayerReference", Player.class);
		setReference.setAccessible(true);
		for (Player player : players) {
			player.setParty(party);
			final PartyPlayer membership =
				constructor.newInstance(player.getUsername());
			setReference.invoke(membership, player);
			membership.setRank(party.getPlayers().isEmpty()
				? PartyRank.LEADER : PartyRank.NORMAL);
			party.getPlayers().add(membership);
			if (party.getLeader() == null) {
				party.setLeader(membership);
			}
		}
		return party;
	}

	private static Npc installGuardDog(final CurrentCombatHarness harness,
			final Player owner, final int x, final int y) {
		final Npc guard = npcWithHits(
			harness, NpcId.GUARD_DOG.id(), x, y, 20);
		guard.setAttribute(SUMMON_OWNER_KEY, owner.getUsernameHash());
		guard.setAttribute(SUMMON_KIND_KEY, "guard_dog");
		guard.setAttribute(SUMMON_CURRENT_HITS_KEY, 20);
		owner.setAttribute(MANUAL_SUMMON_KEY, guard);
		return guard;
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int npcId, final int x, final int y, final int hits) {
		final Npc npc = harness.npc(npcId, x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, hits, false);
		return npc;
	}

	private static void observeNpcDeath(final CurrentCombatHarness harness,
			final Npc npc, final AtomicInteger deaths,
			final AtomicBoolean victimPresentationVisible,
			final Player creditedPlayer) {
		npc.addDeathListener(new NpcLootEvent(
			harness.world(), npc.getLocation(), npc.getID(), 1,
			ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				deaths.incrementAndGet();
				victimPresentationVisible.set(creditedPlayer.getUpdateFlags()
					.getDamage().get() != null
					|| !creditedPlayer.getUpdateFlags().getHitSplats().isEmpty());
			}
		});
	}

	private static int summonContribution(final Npc target,
			final Player owner) throws Exception {
		final Method method = Npc.class.getDeclaredMethod(
			"getSummonDamageInfoBy", java.util.UUID.class);
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		final Pair<Integer, Long> contribution = (Pair<Integer, Long>)
			method.invoke(target, owner.getUUID());
		return contribution.getLeft().intValue();
	}

	private static void setField(final Object target, final String fieldName,
			final Object value) throws Exception {
		final Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object invoke(final Object target, final String methodName,
			final Class<?>[] parameterTypes, final Object... arguments)
			throws Exception {
		return CurrentCombatHarness.invokePrivate(
			target, methodName, parameterTypes, arguments);
	}

	private static void forceNextLegacyDoubleBelow(final double threshold) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextDouble() < threshold) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy random seed found");
	}

	private static void assertHit(final Mob target, final int damage,
			final int type, final String message) {
		assertTrue(target.getUpdateFlags().getDamage().get() != null,
			message + " damage update");
		assertEquals(damage,
			target.getUpdateFlags().getDamage().get().getDamage(),
			message + " displayed damage");
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			message + " hitsplat cardinality");
		final HitSplat hit = target.getUpdateFlags().getHitSplats().get(0);
		assertEquals(type, hit.getType(), message + " hitsplat type");
		assertEquals(damage, hit.getAmount(), message + " hitsplat damage");
	}

	private static void assertNoHit(final Mob target, final String message) {
		assertTrue(target.getUpdateFlags().getDamage().get() == null,
			message + " damage update");
		assertTrue(target.getUpdateFlags().getHitSplats().isEmpty(),
			message + " hitsplat");
	}

	private static DamageResult resultFor(final List<DamageResult> results,
			final Mob target, final String effectKey) {
		for (DamageResult result : results) {
			final DamageRequest request = result.getRequest();
			if (request.getTarget() == target
					&& effectKey.equals(request.getEffectKey())) {
				return result;
			}
		}
		throw new AssertionError("No result for " + effectKey
			+ " targeting " + target);
	}

	private static void assertDamageResult(final DamageResult result,
			final Mob source, final Mob target, final String effectKey,
			final CombatStyle style, final int resolvedDamage,
			final int actualDamage, final int overkillDamage,
			final boolean terminal, final int hitSplatType,
			final UUID eventId, final String label) {
		final DamageRequest request = result.getRequest();
		assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
			result.getStatus(), label + " status");
		assertTrue(request.getSource() == source, label + " source identity");
		assertTrue(request.getTarget() == target, label + " target identity");
		assertEquals(DamageRequest.SourceCategory.OWNED_EFFECT,
			request.getSourceCategory(), label + " category");
		assertEquals(effectKey, request.getEffectKey(), label + " stable identity");
		assertEquals(style, request.getStyle(), label + " style");
		assertEquals(eventId, request.getEventId(), label + " event identity");
		assertEquals(hitSplatType, request.getHitSplatType(),
			label + " hitsplat type");
		assertEquals(resolvedDamage, request.getResolvedDamage(),
			label + " resolved damage");
		assertEquals(actualDamage, result.getActualDamage(),
			label + " factual damage");
		assertEquals(Math.min(resolvedDamage, result.getHitsBefore()),
			result.getLegacyDamageDealt(), label + " legacy damage");
		assertEquals(overkillDamage, result.getOverkillDamage(),
			label + " overkill");
		assertEquals(terminal, result.isTargetTerminal(),
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

	private static void assertEquals(final Object expected,
			final Object actual, final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}
}
