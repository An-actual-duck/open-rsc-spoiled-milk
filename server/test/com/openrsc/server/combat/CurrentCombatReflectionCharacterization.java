package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.DivineRetribution;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.effect.ClericEffectCatalog;
import com.openrsc.server.content.cleric.effect.ClericEffectClock;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigins;
import com.openrsc.server.content.cleric.effect.ClericEffectRankDefinition;
import com.openrsc.server.content.cleric.effect.ClericEffectRegistry;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.content.party.PartyRank;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable A05.4B specifications for the distinct reflection policies. */
final class CurrentCombatReflectionCharacterization {
	private static final int DRAGONSTONE_RING_OF_RECOIL = 1696;

	private CurrentCombatReflectionCharacterization() {
	}

	static void frostbitePolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		int x = 700;
		for (ReflectionEventPath path : ReflectionEventPath.values()) {
			final Player source = harness.player(
				"frost src " + path.ordinal(), x, 620);
			final Npc attacker = npcWithHits(harness, x + 1, 620, 20);
			final Player defender = harness.player(
				"frost def " + path.ordinal(), x + 2, 620);
			attacker.applyFrostbiteDebuff(source);
			final Object event = path.event(harness, attacker, defender, 0);

			final int pendingDamage = ((Integer) invoke(event,
				"applyFrostbiteReflection",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				attacker, defender, Integer.valueOf(9))).intValue();
			assertEquals(4, pendingDamage,
				path + " Frostbite reduces the pending hit by reflected damage");
			assertEquals(15, attacker.getLevel(Skill.HITS.id()),
				path + " Frostbite reflected Hits");
			assertHit(attacker, 5, HitSplat.TYPE_ARMOR_PROC,
				path + " Frostbite presentation");
			assertEquals(5, contribution(
				attacker, source, "getMageDamageInfoBy"),
				path + " Frostbite Magic attribution");
			assertEquals(0, contribution(
				attacker, source, "getCombatDamageInfoBy"),
				path + " Frostbite excludes combat contribution");
			assertReflectionResult(latestResult(harness),
				path.frostbiteEffectKey(), CombatStyle.MAGIC, event,
				source, attacker, 5, 5, 0, false,
				path + " Frostbite transaction");

			final int resultsBeforeRepeat = observedResults(harness).size();
			final int repeatedDamage = ((Integer) invoke(event,
				"applyFrostbiteReflection",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				attacker, defender, Integer.valueOf(9))).intValue();
			assertEquals(9, repeatedDamage,
				path + " Frostbite consumes exactly one pending reflection");
			assertEquals(15, attacker.getLevel(Skill.HITS.id()),
				path + " consumed Frostbite does not reflect twice");
			assertEquals(resultsBeforeRepeat, observedResults(harness).size(),
				path + " consumed Frostbite publishes no second transaction");

			final Player lethalSource = harness.player(
				"frost kill " + path.ordinal(), x, 623);
			final Npc lethalAttacker = npcWithHits(
				harness, x + 1, 623, 3);
			lethalAttacker.setShouldRespawn(false);
			final Player lethalDefender = harness.player(
				"frost victim " + path.ordinal(), x + 2, 623);
			final AtomicInteger deaths = deathCounter(harness, lethalAttacker);
			lethalAttacker.applyFrostbiteDebuff(lethalSource);
			final Object lethalEvent = path.event(
				harness, lethalAttacker, lethalDefender, 0);
			final int lethalPending = ((Integer) invoke(lethalEvent,
				"applyFrostbiteReflection",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				lethalAttacker, lethalDefender, Integer.valueOf(9))).intValue();
			assertEquals(4, lethalPending,
				path + " lethal Frostbite preserves pending-hit reduction");
			assertHit(lethalAttacker, 5, HitSplat.TYPE_ARMOR_PROC,
				path + " lethal Frostbite displayed overkill");
			assertEquals(3, contribution(
				lethalAttacker, lethalSource, "getMageDamageInfoBy"),
				path + " lethal Frostbite caps contribution");
			assertEquals(1, deaths.get(),
				path + " Frostbite death callback cardinality");
			assertReflectionResult(latestResult(harness),
				path.frostbiteEffectKey(), CombatStyle.MAGIC, lethalEvent,
				lethalSource, lethalAttacker, 5, 3, 2, true,
				path + " lethal Frostbite transaction");

			final Player playerAttacker = harness.player(
				"frost player " + path.ordinal(), x + 1, 626);
			final Player playerSource = harness.player(
				"frost packet " + path.ordinal(), x, 626);
			final Npc playerDefender = npcWithHits(
				harness, x + 2, 626, 20);
			harness.recordOutgoingPackets(playerAttacker);
			playerAttacker.applyFrostbiteDebuff(playerSource);
			final Object playerEvent = path.event(
				harness, playerAttacker, playerDefender, 0);
			final int statPacketsBeforeReflection = harness.countOutgoingPackets(
				playerAttacker, OpcodeOut.SEND_STAT);
			invoke(playerEvent, "applyFrostbiteReflection",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				playerAttacker, playerDefender, Integer.valueOf(3));
			assertEquals(statPacketsBeforeReflection + 1,
				harness.countOutgoingPackets(
					playerAttacker, OpcodeOut.SEND_STAT),
				path + " Frostbite retains one player Hits stat packet");
			assertReflectionResult(latestResult(harness),
				path.frostbiteEffectKey(), CombatStyle.MAGIC, playerEvent,
				playerSource, playerAttacker, 2, 2, 0, false,
				path + " player Frostbite transaction");
			x += 5;
		}
		assertEquals(9, observedResults(harness).size(),
			"Frostbite publishes one transaction for each effective reflection");
	}

	static void clericThornsPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		int x = 720;
		for (ReflectionEventPath path : ReflectionEventPath.values()) {
			final Player supportCaster = harness.player(
				"thorn cast " + path.ordinal(), x, 620);
			final Player defender = harness.player(
				"thorn def " + path.ordinal(), x + 1, 620);
			party(supportCaster, defender);
			applyThorns(supportCaster, defender, 4);
			defender.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 5, 40, false);

			final Npc attacker = npcWithHits(harness, x + 2, 620, 1);
			attacker.setShouldRespawn(false);
			final AtomicInteger attackerDeaths = deathCounter(harness, attacker);
			final AtomicBoolean primarySettledBeforeThorns =
				new AtomicBoolean();
			attacker.addDeathListener(new NpcLootEvent(
				harness.world(), attacker.getLocation(), attacker.getID(), 1,
				ItemId.COINS.id()) {
				@Override
				public void onLootNpcDeath(final Player ignoredPlayer,
						final Npc ignoredNpc) {
					primarySettledBeforeThorns.set(
						defender.getLevel(Skill.HITS.id()) == 0
							&& defender.getUpdateFlags().getDamage().get() != null);
				}
			});

			forceNextLegacyIntBelow(75);
			final Object event = path.event(harness, attacker, defender, 7);
			final int resultsBeforeHit = observedResults(harness).size();
			path.invokePrimary(event, attacker, defender, 7);
			assertEquals(1, attackerDeaths.get(),
				path + " Thorns attacker death callback cardinality");
			assertTrue(primarySettledBeforeThorns.get(),
				path + " Thorns follows primary settlement and established healing");
			assertHit(attacker, 1, HitSplat.TYPE_ARMOR_PROC,
				path + " Thorns presentation");
			assertEquals(1, contribution(
				attacker, defender, "getCombatDamageInfoBy"),
				path + " Thorns combat attribution");
			assertEquals(0, contribution(
				attacker, defender, "getMageDamageInfoBy"),
				path + " Thorns excludes Magic contribution");
			assertTrue(defender.killed,
				path + " simultaneous primary victim death is retained");
			final java.util.List<DamageResult> results = observedResults(harness);
			assertEquals(resultsBeforeHit + 2, results.size(),
				path + " primary hit and Thorns publish exactly once each");
			assertTrue(results.get(resultsBeforeHit).getRequest().getTarget()
				== defender, path + " primary transaction precedes Thorns");
			assertReflectionResult(results.get(resultsBeforeHit + 1),
				path.thornsEffectKey(), CombatStyle.MELEE, event,
				defender, attacker, 1, 1, 0, true,
				path + " Thorns transaction");
			x += 4;
		}
		assertEquals(6, observedResults(harness).size(),
			"three primary hits and three Thorns reflections are observed");
	}

	static void meleeJewelryRecoilPolicies(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		int x = 740;
		for (MeleeReflectionPath path : MeleeReflectionPath.values()) {
			final Player defender = harness.player(
				"recoil own " + path.ordinal(), x, 620);
			final Npc attacker = npcWithHits(harness, x + 1, 620, 3);
			attacker.setShouldRespawn(false);
			final AtomicInteger deaths = deathCounter(harness, attacker);
			final Object event = path.event(harness, attacker, defender);

			invoke(event, "inflictMeleeJewelryRecoilDamage",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				defender, attacker, Integer.valueOf(5));
			assertHit(attacker, 5, HitSplat.TYPE_ARMOR_PROC,
				path + " melee jewelry displayed overkill");
			assertEquals(3, contribution(
				attacker, defender, "getCombatDamageInfoBy"),
				path + " melee jewelry combat attribution");
			assertEquals(0, contribution(
				attacker, defender, "getMageDamageInfoBy"),
				path + " melee jewelry excludes Magic contribution");
			assertEquals(1, deaths.get(),
				path + " melee jewelry death callback cardinality");
			assertReflectionResult(latestResult(harness),
				path.recoilEffectKey(), CombatStyle.MELEE, event,
				defender, attacker, 5, 3, 2, true,
				path + " melee jewelry transaction");

			final Npc chainTarget = npcWithHits(
				harness, x + 2, 620, 20);
			invoke(event, "inflictChainLightningDamage",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				defender, chainTarget, Integer.valueOf(2));
			assertReflectionResult(latestResult(harness),
				path.chainEffectKey(), CombatStyle.MELEE, event,
				defender, chainTarget, 2, 2, 0, false,
				path + " chain-lightning A05.4C transaction");

			final Player playerAttacker = harness.player(
				"recoil player " + path.ordinal(), x + 2, 623);
			harness.recordOutgoingPackets(playerAttacker);
			invoke(event, "inflictMeleeJewelryRecoilDamage",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				defender, playerAttacker, Integer.valueOf(2));
			assertEquals(1, harness.countOutgoingPackets(
				playerAttacker, OpcodeOut.SEND_STAT),
				path + " melee recoil retains one player Hits stat packet");
			assertReflectionResult(latestResult(harness),
				path.recoilEffectKey(), CombatStyle.MELEE, event,
				defender, playerAttacker, 2, 2, 0, false,
				path + " player melee recoil transaction");
			x += 3;
		}
		assertEquals(6, observedResults(harness).size(),
			"melee recoil and separately owned chain hits are observed");
	}

	static void projectileRecoilPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player recoilOwner = harness.player("projectile recoil", 750, 620);
		harness.equip(recoilOwner, DRAGONSTONE_RING_OF_RECOIL, 1);
		final Player savedCaster = harness.player("recoil saved", 751, 620);
		harness.equip(savedCaster, ItemId.RING_OF_LIFE.id(), 1);
		savedCaster.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 4, 40, false);
		final int initialX = savedCaster.getX();
		final int initialY = savedCaster.getY();
		forceNextLegacyDoubleBelow(0.90D);
		final ProjectileEvent savedEvent = new ProjectileEvent(
			harness.world(), savedCaster, recoilOwner, 8, 2, false);
		invoke(savedEvent, "recoilDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			recoilOwner, savedCaster, Integer.valueOf(8));
		assertEquals(2, savedCaster.getLevel(Skill.HITS.id()),
			"projectile recoil applies before Ring of Life threshold check");
		assertTrue(savedCaster.getX() != initialX || savedCaster.getY() != initialY,
			"projectile recoil retains nonlethal Ring of Life escape");
		assertReflectionResult(latestResult(harness),
			"projectile-jewelry-recoil", null, savedEvent,
			recoilOwner, savedCaster, 2, 2, 0, false,
			"nonlethal projectile recoil transaction");

		final Player plainOwner = harness.player("recoil plain", 752, 620);
		harness.equip(plainOwner, DRAGONSTONE_RING_OF_RECOIL, 1);
		final Player plainCaster = harness.player("recoil no packet", 753, 620);
		harness.recordOutgoingPackets(plainCaster);
		forceNextLegacyDoubleBelow(0.90D);
		final ProjectileEvent plainEvent = new ProjectileEvent(
			harness.world(), plainCaster, plainOwner, 8, 2, false);
		invoke(plainEvent, "recoilDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			plainOwner, plainCaster, Integer.valueOf(8));
		assertEquals(0, harness.countOutgoingPackets(
			plainCaster, OpcodeOut.SEND_STAT),
			"projectile recoil retains its absence of a player stat packet");
		assertReflectionResult(latestResult(harness),
			"projectile-jewelry-recoil", null, plainEvent,
			plainOwner, plainCaster, 2, 2, 0, false,
			"plain projectile recoil transaction");

		final Player rangedOwner = harness.player("recoil range", 754, 620);
		harness.equip(rangedOwner, DRAGONSTONE_RING_OF_RECOIL, 1);
		final Npc rangedCaster = npcWithHits(harness, 755, 620, 1);
		rangedCaster.setShouldRespawn(false);
		final Npc rangedUnrelatedTarget = npcWithHits(
			harness, 756, 620, 20);
		final RangeEvent activeRange = new RangeEvent(
			harness.world(), rangedOwner, 1L, rangedUnrelatedTarget);
		rangedOwner.setRangeEvent(activeRange);
		final AtomicInteger rangedDeaths = deathCounter(harness, rangedCaster);
		forceNextLegacyDoubleBelow(0.90D);
		final ProjectileEvent rangedEvent = new ProjectileEvent(
			harness.world(), rangedCaster, rangedOwner, 8, 2, false);
		invoke(rangedEvent, "recoilDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			rangedOwner, rangedCaster, Integer.valueOf(8));
		assertHit(rangedCaster, 2, HitSplat.TYPE_ARMOR_PROC,
			"ranged projectile recoil presentation");
		assertEquals(1, rangedDeaths.get(),
			"ranged projectile recoil death callback cardinality");
		assertTrue(rangedOwner.getRangeEvent() == null,
			"lethal ranged projectile recoil resets defender range");
		assertEquals(0, contribution(
			rangedCaster, rangedOwner, "getCombatDamageInfoBy"),
			"projectile recoil records no combat contribution");
		assertEquals(0, contribution(
			rangedCaster, rangedOwner, "getMageDamageInfoBy"),
			"projectile recoil records no Magic contribution");
		assertReflectionResult(latestResult(harness),
			"projectile-jewelry-recoil", null, rangedEvent,
			rangedOwner, rangedCaster, 2, 1, 1, true,
			"ranged projectile recoil transaction");

		final Player magicOwner = harness.player("recoil magic", 758, 620);
		harness.equip(magicOwner, DRAGONSTONE_RING_OF_RECOIL, 1);
		final Npc magicCaster = npcWithHits(harness, 759, 620, 1);
		magicCaster.setShouldRespawn(false);
		final Npc magicUnrelatedTarget = npcWithHits(
			harness, 760, 620, 20);
		final RangeEvent retainedRange = new RangeEvent(
			harness.world(), magicOwner, 1L, magicUnrelatedTarget);
		magicOwner.setRangeEvent(retainedRange);
		forceNextLegacyDoubleBelow(0.90D);
		final ProjectileEvent magicEvent = new ProjectileEvent(
			harness.world(), magicCaster, magicOwner, 8, 1, false);
		invoke(magicEvent, "recoilDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			magicOwner, magicCaster, Integer.valueOf(8));
		assertTrue(magicOwner.getRangeEvent() == retainedRange,
			"lethal Magic projectile recoil does not use the ranged reset branch");
		assertReflectionResult(latestResult(harness),
			"projectile-jewelry-recoil", null, magicEvent,
			magicOwner, magicCaster, 2, 1, 1, true,
			"Magic projectile recoil transaction");
		assertEquals(4, observedResults(harness).size(),
			"projectile recoil publishes one result per effective reflection");
		magicOwner.resetRange();
	}

	static void divineRetributionPolicies(
			final CurrentCombatHarness harness) throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player defender = divineDefender(
			harness, "divine owner", 765, 620);
		final Npc attacker = npcWithHits(harness, 766, 620, 15);
		attacker.setShouldRespawn(false);
		final AtomicInteger deaths = deathCounter(harness, attacker);
		forceNextLegacyDoubleBelow(0.70D);
		final DivineRetribution.Result result = DivineRetribution.apply(
			defender, attacker, 20);
		assertTrue(result.didProc(), "Divine Retribution proc result");
		assertEquals(40, result.getDamage(),
			"Divine Retribution returns twice incoming damage");
		assertTrue(result.killedAttacker(),
			"Divine Retribution returns terminal attacker fact");
		assertHit(attacker, 40, HitSplat.TYPE_ARMOR_PROC,
			"Divine Retribution displayed overkill");
		assertEquals(CombatEffect.DIVINE_RETRIBUTION,
			attacker.getUpdateFlags().getCombatEffect().get().getEffectType(),
			"Divine Retribution presentation effect");
		assertEquals(15, contribution(
			attacker, defender, "getCombatDamageInfoBy"),
			"Divine Retribution combat attribution");
		assertEquals(0, deaths.get(),
			"Divine Retribution returns death without invoking a caller adapter");
		assertReflectionResult(latestResult(harness),
			"divine-retribution", CombatStyle.MELEE, null,
			defender, attacker, 40, 15, 25, true,
			"Divine Retribution transaction");
		attacker.killedBy(defender);
		assertEquals(1, deaths.get(),
			"Divine Retribution caller owns the death adapter");

		final Player playerAttacker = harness.player(
			"divine packet", 768, 620);
		harness.recordOutgoingPackets(playerAttacker);
		forceNextLegacyDoubleBelow(0.20D);
		final DivineRetribution.Result playerResult = DivineRetribution.apply(
			defender, playerAttacker, 5);
		assertTrue(playerResult.didProc(),
			"Divine Retribution player-attacker fixture procs");
		assertEquals(1, harness.countOutgoingPackets(
			playerAttacker, OpcodeOut.SEND_STAT),
			"Divine Retribution retains one player Hits stat packet");
		assertReflectionResult(latestResult(harness),
			"divine-retribution", CombatStyle.MELEE, null,
			defender, playerAttacker, 10, 10, 0, false,
			"player Divine Retribution transaction");

		final Player simultaneousDefender = divineDefender(
			harness, "divine both", 770, 620);
		simultaneousDefender.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 5, 40, false);
		final Npc simultaneousAttacker = npcWithHits(harness, 771, 620, 8);
		simultaneousAttacker.setShouldRespawn(false);
		final Npc simultaneousUnrelatedTarget = npcWithHits(
			harness, 772, 620, 20);
		final AtomicInteger simultaneousDeaths = deathCounter(
			harness, simultaneousAttacker);
		final AtomicBoolean victimSettledBeforeReflection =
			new AtomicBoolean();
		simultaneousAttacker.addDeathListener(new NpcLootEvent(
			harness.world(), simultaneousAttacker.getLocation(),
			simultaneousAttacker.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				victimSettledBeforeReflection.set(
					simultaneousDefender.getLevel(Skill.HITS.id()) == 0);
			}
		});
		final RangeEvent outgoingRange = new RangeEvent(
			harness.world(), simultaneousDefender, 1L,
			simultaneousUnrelatedTarget);
		simultaneousDefender.setRangeEvent(outgoingRange);
		forceNextLegacyDoubleBelow(0.20D);
		final ProjectileEvent simultaneousEvent = new ProjectileEvent(
			harness.world(), simultaneousAttacker, simultaneousDefender,
			7, 2, false);
		final int resultsBeforeHit = observedResults(harness).size();
		invoke(simultaneousEvent, "projectileDamage", new Class<?>[0]);
		assertEquals(1, simultaneousDeaths.get(),
			"Divine simultaneous attacker death callback cardinality");
		assertTrue(victimSettledBeforeReflection.get(),
			"Divine Retribution follows incoming-hit settlement");
		assertTrue(simultaneousDefender.getRangeEvent() == null,
			"projectile caller retains Divine ranged reset");
		assertTrue(simultaneousDefender.killed,
			"projectile caller retains simultaneous victim death");
		final java.util.List<DamageResult> results = observedResults(harness);
		assertEquals(resultsBeforeHit + 2, results.size(),
			"projectile hit and Divine reflection publish exactly once each");
		assertTrue(results.get(resultsBeforeHit).getRequest().getTarget()
			== simultaneousDefender,
			"incoming projectile transaction precedes Divine Retribution");
		assertReflectionResult(results.get(resultsBeforeHit + 1),
			"divine-retribution", CombatStyle.MELEE, null,
			simultaneousDefender, simultaneousAttacker, 10, 8, 2, true,
			"simultaneous Divine Retribution transaction");
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

	private static void applyThorns(final Player caster,
			final Player defender, final int rank) {
		final ClericEffectRegistry registry = new ClericEffectRegistry(
			ClericEffectClock.game(
				defender.getWorld().getServer().getGameClock(),
				defender.getConfig().GAME_TICK));
		defender.installTransientEffectState(registry);
		final ClericEffectRankDefinition<?> definition =
			ClericEffectCatalog.get(ClericSpellId.THORNS, rank);
		assertTrue(registry.apply(definition,
			ClericEffectOrigins.current(caster, defender),
			ClericEffectOrigins.validatorFor(defender)).isUseful(),
			"Thorns fixture application");
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

	private static java.util.List<DamageResult> observedResults(
			final CurrentCombatHarness harness) {
		return CurrentCombatCharacterizationTest.observedDamageResults(harness);
	}

	private static DamageResult latestResult(
			final CurrentCombatHarness harness) {
		final java.util.List<DamageResult> results = observedResults(harness);
		if (results.isEmpty()) {
			throw new AssertionError("Expected an observed damage result");
		}
		return results.get(results.size() - 1);
	}

	private static void assertReflectionResult(final DamageResult result,
			final String effectKey, final CombatStyle style,
			final Object event, final Mob source, final Mob target,
			final int resolvedDamage, final int actualDamage,
			final int overkillDamage, final boolean terminal,
			final String label) {
		final DamageRequest request = result.getRequest();
		assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
			result.getStatus(), label + " applied status");
		assertEquals(DamageRequest.SourceCategory.OWNED_EFFECT,
			request.getSourceCategory(), label + " source category");
		assertEquals(effectKey, request.getEffectKey(), label + " effect key");
		assertEquals(style, request.getStyle(), label + " style");
		assertTrue(request.getSource() == source, label + " source identity");
		assertTrue(request.getTarget() == target, label + " target identity");
		assertEquals(event == null ? null : ((GameTickEvent) event).getUUID(),
			request.getEventId(), label + " event identity");
		assertEquals(HitSplat.TYPE_ARMOR_PROC, request.getHitSplatType(),
			label + " hitsplat type");
		assertEquals(resolvedDamage, request.getResolvedDamage(),
			label + " resolved damage");
		assertEquals(actualDamage, result.getActualDamage(),
			label + " actual damage");
		assertEquals(Math.min(resolvedDamage,
			result.getHitsBefore()), result.getLegacyDamageDealt(),
			label + " legacy damage");
		assertEquals(overkillDamage, result.getOverkillDamage(),
			label + " overkill damage");
		assertEquals(Boolean.valueOf(terminal),
			Boolean.valueOf(result.isTargetTerminal()), label + " terminal fact");
	}

	private static void forceNextLegacyDoubleBelow(final double threshold) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextDouble() < threshold) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy double seed found");
	}

	private static void forceNextLegacyIntBelow(final int threshold) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextInt(100) < threshold) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy integer seed found");
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

	private static void assertEquals(final int expected, final int actual,
			final String message) {
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

	private enum ReflectionEventPath {
		RECIPROCAL_MELEE,
		PVM_MELEE,
		PROJECTILE;

		private Object event(final CurrentCombatHarness harness,
				final Mob attacker, final Mob defender, final int damage) {
			switch (this) {
				case RECIPROCAL_MELEE:
					return new CombatEvent(harness.world(), attacker, defender);
				case PVM_MELEE:
					return new PvmMeleeEvent(
						harness.world(), attacker, defender);
				case PROJECTILE:
					return new ProjectileEvent(
						harness.world(), attacker, defender, damage, 1, false);
				default:
					throw new AssertionError("Unhandled path " + this);
			}
		}

		private void invokePrimary(final Object event, final Mob attacker,
				final Mob defender, final int damage) throws Exception {
			if (this == PROJECTILE) {
				invoke(event, "projectileDamage", new Class<?>[0]);
				return;
			}
			invoke(event, "inflictDamage",
				new Class<?>[] {Mob.class, Mob.class, int.class},
				attacker, defender, Integer.valueOf(damage));
		}

		private String frostbiteEffectKey() {
			return effectKey("frostbite-reflection");
		}

		private String thornsEffectKey() {
			return effectKey("cleric-thorns");
		}

		private String effectKey(final String suffix) {
			switch (this) {
				case RECIPROCAL_MELEE:
					return "reciprocal-melee-" + suffix;
				case PVM_MELEE:
					return "pvm-melee-" + suffix;
				case PROJECTILE:
					return "projectile-" + suffix;
				default:
					throw new AssertionError("Unhandled path " + this);
			}
		}
	}

	private enum MeleeReflectionPath {
		RECIPROCAL_MELEE,
		PVM_MELEE;

		private Object event(final CurrentCombatHarness harness,
				final Mob attacker, final Mob defender) {
			return this == RECIPROCAL_MELEE
				? new CombatEvent(harness.world(), attacker, defender)
				: new PvmMeleeEvent(harness.world(), attacker, defender);
		}

		private String recoilEffectKey() {
			return this == RECIPROCAL_MELEE
				? "reciprocal-melee-jewelry-recoil"
				: "pvm-melee-jewelry-recoil";
		}

		private String chainEffectKey() {
			return this == RECIPROCAL_MELEE
				? "reciprocal-melee-chain-lightning"
				: "pvm-melee-chain-lightning";
		}
	}
}
