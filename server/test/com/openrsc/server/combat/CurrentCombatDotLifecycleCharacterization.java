package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.CorrosiveAura;
import com.openrsc.server.content.ElderGreenDragonArmorEffect;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.BurnEvent;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.ElderGreenDragonSpecialAttacks;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.combat.scripts.all.NpcPoisonPlayerScript;
import com.openrsc.server.event.rsc.impl.combat.scripts.all.PlayerPoisonScript;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.login.PlayerSaveRequest;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.death.DeathLifecycleSnapshot;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.util.rsc.DataConversions;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * A08.2 executable record of the current generic poison/burn lifecycle.
 *
 * <p>These fixtures intentionally characterize compatibility behavior,
 * including boundaries that need an explicit policy decision before A08 moves
 * ownership or settlement authority. They must not be read as approving those
 * boundaries.</p>
 */
final class CurrentCombatDotLifecycleCharacterization {
	private static final int DRAGONSTONE_NECKLACE_OF_LEACH = 1672;
	private static final int DRAGONSTONE_NECKLACE_OF_CLEANSING = 1657;

	private CurrentCombatDotLifecycleCharacterization() {
	}

	static void poisonStackingOwnershipAndCadence(
			final CurrentCombatHarness harness) throws Exception {
		final Player first = harness.player("dot poison first", 600, 680);
		final Player latest = harness.player("dot poison latest", 602, 680);
		final Npc target = npcWithHits(harness, 601, 680, 80);

		target.applyPoison(20, 40, first);
		final PoisonEvent event = target.getAttribute("poisonEvent", null);
		assertNotNull(event, "first poison application schedules an event");
		assertEquals(20, target.getCurrentPoisonPower(),
			"first poison current power");
		assertEquals(40, target.getPoisonMaxPower(),
			"first poison ceiling");
		assertEquals(first.getUUID(), poisonOwner(event),
			"first poison source");
		assertEquals(8L, event.getTicksBeforeRun(),
			"first poison full countdown");
		assertEquals(1, eventCount(harness, target, "Poison Event"),
			"first poison event cardinality");

		harness.advanceOneCombatTick();
		final long countdownBeforeReapply = event.getTicksBeforeRun();
		target.applyPoison(15, 60, latest);
		assertSame(event, target.getAttribute("poisonEvent", null),
			"poison reapplication reuses the active event");
		assertEquals(35, target.getCurrentPoisonPower(),
			"poison power adds below the ceiling");
		assertEquals(60, target.getPoisonMaxPower(),
			"higher incoming ceiling replaces the old ceiling");
		assertEquals(latest.getUUID(), poisonOwner(event),
			"latest accepted player application owns poison");
		assertEquals(countdownBeforeReapply, event.getTicksBeforeRun(),
			"ordinary poison reapplication preserves countdown");

		target.applyPoison(100, 20, first);
		assertEquals(60, target.getCurrentPoisonPower(),
			"poison accumulation caps at retained ceiling");
		assertEquals(60, target.getPoisonMaxPower(),
			"lower incoming maximum cannot shrink ceiling");
		assertEquals(first.getUUID(), poisonOwner(event),
			"effective capped application transfers current ownership");

		target.applyPoison(1, 1, latest);
		assertEquals(60, target.getCurrentPoisonPower(),
			"application at ceiling cannot inflate poison");
		assertEquals(latest.getUUID(), poisonOwner(event),
			"current compatibility transfers ownership even with no power increase");
		target.applyPoison(1, 1, null);
		assertNull(poisonOwner(event),
			"unattributed capped application clears player ownership");
		assertEquals(1, eventCount(harness, target, "Poison Event"),
			"reapplication never duplicates the scheduler event");

		target.curePoison();
		assertFalse(event.isRunning(), "cure stops the active poison event");
		assertNull(target.getAttribute("poisonEvent", null),
			"cure removes poison event ownership");
		assertEquals(0, target.getCurrentPoisonPower(),
			"cure clears current poison power");
		assertEquals(0, target.getPoisonMaxPower(),
			"cure clears poison ceiling");
	}

	static void poisonPulseLeachAndSourceAvailability(
			final CurrentCombatHarness harness) throws Exception {
		final String sourceName = "dot leach owner";
		final Player source = harness.player(sourceName, 610, 680);
		harness.equip(source, DRAGONSTONE_NECKLACE_OF_LEACH, 1);
		setHits(source, 20, 40);
		final Npc target = npcWithHits(harness, 611, 680, 80);
		target.applyPoison(40, 40, source);
		final PoisonEvent event = target.getAttribute("poisonEvent", null);

		event.run();
		assertEquals(76, target.getLevel(Skill.HITS.id()),
			"power 40 pulse deals four factual damage");
		assertEquals(37, target.getCurrentPoisonPower(),
			"poison power drains before pulse settlement");
		assertEquals(24, source.getLevel(Skill.HITS.id()),
			"live Dragonstone Leach heals factual poison damage");
		assertHit(target, 4, HitSplat.TYPE_POISON,
			"generic poison presentation");
		assertFalse(target.hasDamageBy(source),
			"generic poison currently records no NPC contribution");

		setHits(source, 20, 40);
		harness.logout(source);
		event.run();
		assertEquals(73, target.getLevel(Skill.HITS.id()),
			"poison continues while its player source is offline");
		assertEquals(34, target.getCurrentPoisonPower(),
			"offline-source pulse still drains poison power");
		assertEquals(20, source.getLevel(Skill.HITS.id()),
			"offline source receives no Leach healing");

		final Player relogged = harness.player(sourceName, 612, 680);
		harness.equip(relogged, DRAGONSTONE_NECKLACE_OF_LEACH, 1);
		setHits(relogged, 20, 40);
		assertEquals(source.getUUID(), relogged.getUUID(),
			"username-derived poison source identity survives relog");
		event.run();
		assertEquals(70, target.getLevel(Skill.HITS.id()),
			"relogged-source poison keeps ticking");
		assertEquals(23, relogged.getLevel(Skill.HITS.id()),
			"live replacement session resumes Leach from current equipment");
		assertFalse(target.hasDamageBy(relogged),
			"relogged owner still receives no poison contribution");
	}

	static void poisonThresholdsAndLiveDecayEquipment(
			final CurrentCombatHarness harness) throws Exception {
		final Npc belowThreshold = npcWithHits(harness, 614, 680, 20);
		belowThreshold.applyPoison(9, 9);
		final PoisonEvent belowEvent = belowThreshold.getAttribute(
			"poisonEvent", null);
		belowEvent.run();
		assertEquals(20, belowThreshold.getLevel(Skill.HITS.id()),
			"power nine cures before damage");
		assertFalse(belowEvent.isRunning(),
			"power nine cure stops poison event");

		final Npc threshold = npcWithHits(harness, 615, 680, 20);
		threshold.applyPoison(10, 10);
		final PoisonEvent thresholdEvent = threshold.getAttribute(
			"poisonEvent", null);
		thresholdEvent.run();
		assertEquals(19, threshold.getLevel(Skill.HITS.id()),
			"power ten deals one damage");
		assertEquals(7, threshold.getCurrentPoisonPower(),
			"power ten drains below cure threshold after settlement");
		assertTrue(thresholdEvent.isRunning(),
			"below-threshold remainder waits until next pulse to cure");
		thresholdEvent.run();
		assertEquals(19, threshold.getLevel(Skill.HITS.id()),
			"next pulse cures without additional damage");

		final Npc nineteen = npcWithHits(harness, 616, 680, 20);
		nineteen.applyPoison(19, 19);
		final PoisonEvent nineteenEvent = nineteen.getAttribute(
			"poisonEvent", null);
		nineteenEvent.run();
		assertEquals(19, nineteen.getLevel(Skill.HITS.id()),
			"power nineteen uses integer-division one damage");
		assertEquals(16, nineteen.getCurrentPoisonPower(),
			"power nineteen ordinary decay");

		final Npc twenty = npcWithHits(harness, 617, 680, 20);
		twenty.applyPoison(20, 20);
		final PoisonEvent twentyEvent = twenty.getAttribute(
			"poisonEvent", null);
		twentyEvent.run();
		assertEquals(18, twenty.getLevel(Skill.HITS.id()),
			"power twenty deals two damage");
		assertEquals(17, twenty.getCurrentPoisonPower(),
			"power twenty ordinary decay");

		final Player source = harness.player("dot decay src", 618, 680);
		final Player cleansed = harness.player("dot decay tgt", 619, 680);
		setHits(cleansed, 40, 40);
		cleansed.applyPoison(30, 30, source);
		final PoisonEvent liveEquipmentEvent = cleansed.getAttribute(
			"poisonEvent", null);
		liveEquipmentEvent.run();
		assertEquals(27, cleansed.getCurrentPoisonPower(),
			"unequipped poison uses base three-power decay");
		assertEquals(37, cleansed.getLevel(Skill.HITS.id()),
			"power thirty deals three damage");
		harness.equip(cleansed, DRAGONSTONE_NECKLACE_OF_CLEANSING, 1);
		liveEquipmentEvent.run();
		assertEquals(19, cleansed.getCurrentPoisonPower(),
			"newly equipped tier-five Cleansing adds five decay");
		assertEquals(35, cleansed.getLevel(Skill.HITS.id()),
			"live decay equipment does not change pre-drain pulse damage");
		assertEquals(source.getUUID(), poisonOwner(liveEquipmentEvent),
			"target decay equipment does not change poison source");
	}

	static void poisonPersistenceAndOrphanCompatibility(
			final CurrentCombatHarness harness) {
		final Player restored = harness.player(
			"dot poison restore", 620, 680, player -> {
				player.getCache().set("poisoned", 47);
				player.getCache().set("poisoned_max", 80);
			});
		final PoisonEvent restoredEvent = restored.getAttribute(
			"poisonEvent", null);
		assertNotNull(restoredEvent,
			"complete legacy poison cache restores one runtime event");
		assertEquals(47, restored.getCurrentPoisonPower(),
			"restored poison current power");
		assertEquals(80, restored.getPoisonMaxPower(),
			"restored poison ceiling");
		assertNull(poisonOwner(restoredEvent),
			"legacy poison cache restores without guessed source ownership");
		assertEquals(8L, restoredEvent.getTicksBeforeRun(),
			"restored poison starts a fresh full countdown");
		assertEquals(1, eventCount(harness, restored, "Poison Event"),
			"restored poison event cardinality");

		restored.curePoison();
		assertFalse(restored.getCache().hasKey("poisoned"),
			"normal cure removes persisted current poison");
		assertFalse(restored.getCache().hasKey("poisoned_max"),
			"normal cure removes persisted poison ceiling");

		final Player maximumFallback = harness.player(
			"dot poison legacy max", 622, 680,
			player -> player.getCache().set("poisoned", 31));
		assertEquals(31, maximumFallback.getCurrentPoisonPower(),
			"legacy poison without maximum restores current power");
		assertEquals(31, maximumFallback.getPoisonMaxPower(),
			"legacy poison without maximum uses current as ceiling");

		final Player orphan = harness.player("dot poison orphan", 624, 680);
		orphan.getCache().set("poisoned", 25);
		orphan.getCache().set("poisoned_max", 50);
		orphan.removeAttribute("poisonEvent");
		orphan.curePoison();
		assertTrue(orphan.getCache().hasKey("poisoned"),
			"current orphan cure leaves legacy poison current cache behind");
		assertTrue(orphan.getCache().hasKey("poisoned_max"),
			"current orphan cure leaves legacy poison maximum cache behind");
		assertEquals(0, orphan.getCurrentPoisonPower(),
			"orphan cure still clears runtime poison power");
	}

	static void poisonFactualDamageBoundaries(
			final CurrentCombatHarness harness) throws Exception {
		final Player source = harness.player("dot actual src", 626, 680);
		harness.equip(source, DRAGONSTONE_NECKLACE_OF_LEACH, 1);

		setHits(source, 20, 40);
		final Player tenacious = harness.player("dot tenacity", 627, 680);
		for (ItemId item : new ItemId[] {
			ItemId.GOBLIN_HIDE_COIF,
			ItemId.GOBLIN_HIDE_GLOVES,
			ItemId.GOBLIN_HIDE_BOOTS,
			ItemId.GOBLIN_HIDE_CHAPS,
			ItemId.GOBLIN_HIDE_CUIRASS
		}) {
			harness.equip(tenacious, item.id(), 1);
		}
		setHits(tenacious, 5, 40);
		forceNextLegacyRandomBelow(0.05D);
		tenacious.applyPoison(70, 70, source);
		final PoisonEvent tenacityEvent = tenacious.getAttribute(
			"poisonEvent", null);
		tenacityEvent.run();
		assertEquals(1, tenacious.getLevel(Skill.HITS.id()),
			"Goblin Tenacity reduces lethal poison to leave one Hit");
		assertEquals(24, source.getLevel(Skill.HITS.id()),
			"Leach uses four factual post-Tenacity damage");
		assertEquals(4,
			tenacious.getUpdateFlags().getHitSplats().get(0).getAmount(),
			"poison presentation uses post-Tenacity damage");

		setHits(source, 20, 40);
		final Npc overkill = npcWithHits(harness, 628, 680, 2);
		overkill.setShouldRespawn(false);
		overkill.setOpponent(source);
		overkill.applyPoison(40, 40, source);
		final PoisonEvent overkillEvent = overkill.getAttribute(
			"poisonEvent", null);
		overkillEvent.run();
		assertTrue(overkill.isUnregistering(),
			"overkill poison completes ordinary NPC death");
		assertEquals(22, source.getLevel(Skill.HITS.id()),
			"Leach caps healing at two factual overkill damage");
		assertEquals(4,
			overkill.getUpdateFlags().getHitSplats().get(0).getAmount(),
			"compatibility hitsplat retains requested overkill amount");

		setHits(source, 20, 40);
		final Npc zero = npcWithHits(harness, 629, 680, 1);
		zero.getSkills().setLevel(Skill.HITS.id(), 0);
		zero.applyPoison(40, 40, source);
		final PoisonEvent zeroEvent = zero.getAttribute("poisonEvent", null);
		zeroEvent.run();
		assertEquals(20, source.getLevel(Skill.HITS.id()),
			"zero factual poison damage provides no Leach");
		assertEquals(0, zero.getCurrentPoisonPower(),
			"zero-Hits opponentless poison is cured by lethal compatibility path");
	}

	static void corruptLegacyAndRuntimeStateBoundaries(
			final CurrentCombatHarness harness) {
		final Player nonnumericPoison = harness.player(
			"dot bad poison", 660, 680);
		harness.logout(nonnumericPoison);
		nonnumericPoison.getCache().store("poisoned", "not-a-number");
		assertThrows(NumberFormatException.class,
			() -> nonnumericPoison.setLoggedIn(true),
			"nonnumeric poison currently aborts session restoration");
		final PoisonEvent failedRestore = nonnumericPoison.getAttribute(
			"poisonEvent", null);
		assertNotNull(failedRestore,
			"failed nonnumeric poison restore leaves an event marker");
		assertTrue(failedRestore.isRunning(),
			"failed nonnumeric poison restore leaves a scheduled event");
		assertFalse(nonnumericPoison.loggedIn(),
			"failed nonnumeric poison restore never completes login state");
		nonnumericPoison.curePoison();

		final Player negativePoison = harness.player(
			"dot negative", 662, 680,
			player -> player.getCache().set("poisoned", -5));
		final PoisonEvent negativeEvent = negativePoison.getAttribute(
			"poisonEvent", null);
		assertEquals(-5, negativePoison.getCurrentPoisonPower(),
			"negative legacy poison restores without validation");
		assertEquals(0, negativePoison.getPoisonMaxPower(),
			"negative legacy maximum is independently clamped to zero");
		assertThrows(IllegalArgumentException.class, negativeEvent::run,
			"negative poison fails when its first pulse validates power");
		assertTrue(negativeEvent.isRunning(),
			"failed negative poison pulse leaves event running");
		for (int tick = 0; tick < 8; tick++) {
			negativeEvent.tick();
		}
		assertEquals(1, negativeEvent.call().intValue(),
			"scheduler boundary reports the invalid poison callback failure");
		assertFalse(negativeEvent.isRunning(),
			"scheduler callback failure stops the invalid poison event");
		negativePoison.curePoison();

		final Player invertedPoison = harness.player(
			"dot inverted", 664, 680, player -> {
				player.getCache().set("poisoned", 80);
				player.getCache().set("poisoned_max", 20);
			});
		assertEquals(80, invertedPoison.getCurrentPoisonPower(),
			"legacy current above maximum is not clamped");
		assertEquals(20, invertedPoison.getPoisonMaxPower(),
			"legacy inverted maximum remains lower than current");
		invertedPoison.curePoison();

		final Player orphanMaximum = harness.player(
			"dot orphan max", 666, 680,
			player -> player.getCache().set("poisoned_max", 40));
		assertNull(orphanMaximum.getAttribute("poisonEvent", null),
			"orphan maximum does not restore poison event");
		assertTrue(orphanMaximum.getCache().hasKey("poisoned_max"),
			"orphan maximum remains stale after login");

		final Npc overflow = npcWithHits(harness, 668, 680, 20);
		overflow.applyPoison(Integer.MAX_VALUE, Integer.MAX_VALUE);
		final PoisonEvent overflowEvent = overflow.getAttribute(
			"poisonEvent", null);
		overflow.applyPoison(1, Integer.MAX_VALUE);
		assertEquals(Integer.MIN_VALUE, overflow.getCurrentPoisonPower(),
			"additive poison power currently overflows signed integer range");
		assertThrows(IllegalArgumentException.class, overflowEvent::run,
			"overflowed poison fails on pulse validation");
		overflow.curePoison();

		final Npc wrongPoisonAttribute = npcWithHits(harness, 670, 680, 20);
		wrongPoisonAttribute.setAttribute("poisonEvent", "wrong-type");
		assertThrows(ClassCastException.class, wrongPoisonAttribute::curePoison,
			"wrong poison attribute type currently aborts cleanup");
		wrongPoisonAttribute.removeAttribute("poisonEvent");
		wrongPoisonAttribute.curePoison();

		final Player nonnumericBurn = harness.player("dot bad burn", 672, 680);
		harness.logout(nonnumericBurn);
		nonnumericBurn.getCache().store("burn_damage", "not-a-number");
		nonnumericBurn.getCache().set("burn_pulses", 3);
		assertThrows(NumberFormatException.class,
			() -> nonnumericBurn.setLoggedIn(true),
			"nonnumeric burn currently aborts session restoration");
		assertNull(nonnumericBurn.getAttribute("burnEvent", null),
			"failed burn parsing occurs before event registration");

		final Npc wrongBurnAttribute = npcWithHits(harness, 674, 680, 20);
		wrongBurnAttribute.setAttribute("burnEvent", "wrong-type");
		assertThrows(ClassCastException.class, wrongBurnAttribute::extinguish,
			"wrong burn attribute type currently aborts cleanup");
		wrongBurnAttribute.removeAttribute("burnEvent");
		wrongBurnAttribute.extinguish();
	}

	static void failedLogoutSaveBoundary(
			final CurrentCombatHarness harness) {
		final Player player = harness.player("dot failed logout save", 675, 680);
		player.applyPoison(40, 40);
		final PoisonEvent poison = player.getAttribute("poisonEvent", null);
		assertNotNull(poison,
			"logout-save fixture begins with active persisted poison");
		player.setSaving(true);
		player.setLoggingOut(true);

		// Harness players intentionally have no database row. The production
		// service reaches its missing-row error path without a write. Its current
		// MessageFormat error path throws before PlayerSaveRequest can clear the
		// flags or apply its documented failed-save logout policy.
		assertThrows(IllegalArgumentException.class,
			() -> new PlayerSaveRequest(harness.server(), player, true).process(),
			"missing-row logout save escapes before request cleanup");

		assertTrue(player.loggedIn(),
			"failed logout save leaves the live session present by current behavior");
		assertTrue(player.isSaving(),
			"failed logout save leaves saving state stuck");
		assertTrue(player.isLoggingOut(),
			"failed logout save leaves logout state stuck");
		assertTrue(poison.isRunning(),
			"failed logout save leaves owner-bound poison running");
		assertTrue(harness.world().getPlayers().contains(player),
			"failed logout save leaves the player in the world list");
		player.setSaving(false);
		player.setLoggingOut(false);
		player.curePoison();
	}

	static void repeatedRelogEventCardinality(
			final CurrentCombatHarness harness) {
		final String name = "dot relog";
		PoisonEvent priorPoison = null;
		BurnEvent priorBurn = null;
		for (int session = 0; session < 3; session++) {
			final Player player = harness.player(name, 676, 680, restored -> {
				restored.getCache().set("poisoned", 40);
				restored.getCache().set("poisoned_max", 60);
				restored.getCache().set("burn_damage", 2);
				restored.getCache().set("burn_pulses", 3);
			});
			final PoisonEvent poison = player.getAttribute("poisonEvent", null);
			final BurnEvent burn = player.getAttribute("burnEvent", null);
			assertNotNull(poison, "relog poison event session " + session);
			assertNotNull(burn, "relog burn event session " + session);
			assertEquals(1, eventCount(harness, player, "Poison Event"),
				"one poison event in relog session " + session);
			assertEquals(1, eventCount(harness, player, "Burn Event"),
				"one burn event in relog session " + session);
			assertEquals(8L, poison.getTicksBeforeRun(),
				"poison relog resets full countdown in session " + session);
			assertEquals(8L, burn.getTicksBeforeRun(),
				"burn relog resets full countdown in session " + session);
			if (priorPoison != null) {
				assertNotSame(priorPoison, poison,
					"fresh session creates a new poison event");
				assertNotSame(priorBurn, burn,
					"fresh session creates a new burn event");
			}
			priorPoison = poison;
			priorBurn = burn;
			harness.logout(player);
			assertFalse(poison.isRunning(),
				"logout stops poison event in session " + session);
			assertFalse(burn.isRunning(),
				"logout stops burn event in session " + session);
			assertEquals(0, eventCount(harness, player, "Poison Event"),
				"logout removes poison scheduler entry in session " + session);
			assertEquals(0, eventCount(harness, player, "Burn Event"),
				"logout removes burn scheduler entry in session " + session);
		}
	}

	static void duplicateSchedulerAndMixedBurnBoundaries(
			final CurrentCombatHarness harness) {
		final Npc poisoned = npcWithHits(harness, 718, 680, 40);
		poisoned.applyPoison(40, 40);
		final PoisonEvent canonicalPoison = poisoned.getAttribute(
			"poisonEvent", null);
		final PoisonEvent duplicatePoison = new PoisonEvent(
			harness.world(), poisoned, 20, null);
		assertTrue(harness.server().getGameEventHandler().add(duplicatePoison),
			"poison scheduler currently admits a duplicate stream");
		assertEquals(2, eventCount(harness, poisoned, "Poison Event"),
			"duplicate poison scheduler cardinality");
		canonicalPoison.run();
		duplicatePoison.run();
		assertEquals(34, poisoned.getLevel(Skill.HITS.id()),
			"both admitted poison streams independently deal damage");
		assertSame(canonicalPoison, poisoned.getAttribute("poisonEvent", null),
			"duplicate poison remains detached from canonical mob attribute");
		assertEquals(37, poisoned.getCurrentPoisonPower(),
			"canonical poison power hides duplicate stream power");
		duplicatePoison.stop();
		poisoned.curePoison();

		final Player burning = harness.player("dot duplicate burn", 720, 680);
		burning.applyBurn(3, 2);
		final BurnEvent canonicalBurn = burning.getAttribute("burnEvent", null);
		final BurnEvent duplicateBurn = new BurnEvent(
			harness.world(), burning, 7, 4);
		assertFalse(harness.server().getGameEventHandler().add(duplicateBurn),
			"burn scheduler rejects a second stream for the same target");
		assertEquals(1, eventCount(harness, burning, "Burn Event"),
			"duplicate burn scheduler cardinality");
		assertSame(canonicalBurn, burning.getAttribute("burnEvent", null),
			"rejected duplicate burn cannot replace canonical attribute");
		burning.extinguish();

		final Player zeroDamage = harness.player(
			"dot zero burn damage", 722, 680, player -> {
				player.getCache().set("burn_damage", 0);
				player.getCache().set("burn_pulses", 3);
			});
		final BurnEvent zeroDamageEvent = zeroDamage.getAttribute(
			"burnEvent", null);
		assertNotNull(zeroDamageEvent,
			"zero-damage mixed burn cache schedules before validation");
		zeroDamageEvent.run();
		assertNull(zeroDamage.getAttribute("burnEvent", null),
			"zero-damage mixed burn clears on first pulse");
		assertFalse(zeroDamage.getCache().hasKey("burn_damage"),
			"zero-damage mixed burn clears damage cache");
		assertFalse(zeroDamage.getCache().hasKey("burn_pulses"),
			"zero-damage mixed burn clears pulse cache");

		final Player zeroPulses = harness.player(
			"dot zero burn pulses", 724, 680, player -> {
				player.getCache().set("burn_damage", 7);
				player.getCache().set("burn_pulses", 0);
			});
		final BurnEvent zeroPulseEvent = zeroPulses.getAttribute(
			"burnEvent", null);
		assertNotNull(zeroPulseEvent,
			"zero-pulse mixed burn cache schedules before validation");
		zeroPulseEvent.run();
		assertNull(zeroPulses.getAttribute("burnEvent", null),
			"zero-pulse mixed burn clears on first pulse");

		final Player negativeBurn = harness.player(
			"dot negative burn", 726, 680, player -> {
				player.getCache().set("burn_damage", -7);
				player.getCache().set("burn_pulses", -3);
			});
		final BurnEvent negativeBurnEvent = negativeBurn.getAttribute(
			"burnEvent", null);
		assertNotNull(negativeBurnEvent,
			"negative burn cache schedules before validation");
		negativeBurnEvent.run();
		assertNull(negativeBurn.getAttribute("burnEvent", null),
			"negative burn clears on first pulse without damage");
	}

	static void legacyPvpPoisonAndPositiveBurnBoundaries(
			final CurrentCombatHarness harness) throws Exception {
		final PlayerPoisonScript legacyPvp = new PlayerPoisonScript();
		final Player attacker = harness.player("dot legacy attacker", 728, 680);
		final Player victim = harness.player("dot legacy victim", 729, 680);
		harness.equip(attacker, ItemId.POISONED_RUNE_DAGGER.id(), 1);

		assertTrue(harness.server().getConfig().WANT_MYWORLD,
			"combat harness starts in My World mode");
		assertFalse(legacyPvp.shouldExecute(attacker, victim),
			"legacy PvP poison is disabled in My World mode");

		harness.server().getConfig().WANT_MYWORLD = false;
		try {
			final Player noWeapon = harness.player(
				"dot legacy no weapon", 730, 680);
			assertFalse(legacyPvp.shouldExecute(noWeapon, victim),
				"legacy PvP poison requires a wielded poisoned item");
			forceNextLegacyIntBelow(1, 4, 1);
			assertTrue(legacyPvp.shouldExecute(attacker, victim),
				"legacy PvP poison succeeds with its deterministic one-in-four roll");
			legacyPvp.executeScript(attacker, victim);
			assertEquals(48, victim.getCurrentPoisonPower(),
				"legacy PvP poison applied power");
			assertEquals(48, victim.getPoisonMaxPower(),
				"legacy PvP poison maximum power");
			assertNull(poisonOwner(victim.getAttribute("poisonEvent", null)),
				"legacy PvP poison does not retain the applying player");
			victim.curePoison();
			victim.setAntidoteProtection();
			legacyPvp.executeScript(attacker, victim);
			assertNull(victim.getAttribute("poisonEvent", null),
				"legacy PvP execution honors target antidote protection");
		} finally {
			harness.server().getConfig().WANT_MYWORLD = true;
		}

		final Player largePulseTarget = harness.player(
			"dot large burn target", 732, 680);
		setHits(largePulseTarget, 40, 40);
		largePulseTarget.applyBurn(39, Integer.MAX_VALUE);
		final BurnEvent largePulse = largePulseTarget.getAttribute(
			"burnEvent", null);
		largePulse.run();
		assertEquals(1, largePulseTarget.getLevel(Skill.HITS.id()),
			"large valid burn pulse applies its configured positive damage");
		assertEquals(Integer.MAX_VALUE - 1,
			largePulseTarget.getCache().getInt("burn_pulses"),
			"large valid burn pulse decrements without overflow");
		assertEquals(39, largePulseTarget.getCache().getInt("burn_damage"),
			"large valid burn pulse persists its configured damage");
		largePulseTarget.extinguish();

		final Player maxDamageTarget = harness.player(
			"dot max burn target", 734, 680);
		harness.recordOutgoingPackets(maxDamageTarget);
		final Npc maxDamageOpponent = harness.npc(
			NpcId.GREATER_DEMON.id(), 735, 680);
		maxDamageTarget.setOpponent(maxDamageOpponent);
		maxDamageTarget.applyBurn(Integer.MAX_VALUE, 1);
		final BurnEvent maxDamage = maxDamageTarget.getAttribute("burnEvent", null);
		maxDamage.run();
		assertEquals(255,
			maxDamageTarget.getUpdateFlags().getHitSplats().get(0).getAmount(),
			"maximum positive burn damage clamps its visible hitsplat");
		assertTrue(maxDamageTarget.killed,
			"maximum positive burn damage reaches ordinary player death handling");
		assertNull(maxDamageTarget.getAttribute("burnEvent", null),
			"terminal maximum burn cleanup clears its event");
	}

	static void burnReplacementPersistenceAndCleanup(
			final CurrentCombatHarness harness) throws Exception {
		final Player target = harness.player("dot burn replace", 630, 680);
		setHits(target, 40, 40);
		target.applyBurn(3, 2);
		final BurnEvent first = target.getAttribute("burnEvent", null);
		assertNotNull(first, "generic burn application schedules an event");
		assertEquals(8L, first.getTicksBeforeRun(),
			"generic burn initial countdown");
		harness.advanceOneCombatTick();
		assertEquals(7L, first.getTicksBeforeRun(),
			"generic burn countdown advances normally");

		target.applyBurn(5, 4);
		final BurnEvent replacement = target.getAttribute("burnEvent", null);
		assertNotSame(first, replacement,
			"generic burn reapplication replaces the event");
		assertFalse(first.isRunning(),
			"generic burn reapplication stops the prior event");
		assertEquals(8L, replacement.getTicksBeforeRun(),
			"generic burn replacement resets countdown");
		assertEquals(0, target.getBurnDamage(),
			"current replacement ordering clears the new burn damage");
		assertEquals(0, target.getBurnPulseCount(),
			"current replacement ordering clears the new burn pulses");
		assertTrue(replacement.isRunning(),
			"zero-state replacement marker reports itself running");
		assertEquals(0, eventCount(harness, target, "Burn Event"),
			"ONE_PER_MOB rejects replacement while stopped prior event awaits cleanup");
		assertEquals(40, target.getLevel(Skill.HITS.id()),
			"unregistered zero-state replacement deals no damage");
		target.extinguish();
		assertFalse(replacement.isRunning(), "extinguish stops generic burn");
		assertNull(target.getAttribute("burnEvent", null),
			"explicit extinguish clears the orphan replacement marker");
		assertFalse(target.getCache().hasKey("burn_damage"),
			"extinguish clears persisted burn damage");
		assertFalse(target.getCache().hasKey("burn_pulses"),
			"extinguish clears persisted burn pulses");

		final Player pulseTarget = harness.player("dot burn pulse", 631, 680);
		setHits(pulseTarget, 40, 40);
		pulseTarget.applyBurn(5, 4);
		final BurnEvent pulseEvent = pulseTarget.getAttribute("burnEvent", null);
		pulseEvent.run();
		assertEquals(35, pulseTarget.getLevel(Skill.HITS.id()),
			"first generic burn pulse deals configured damage");
		assertEquals(3, pulseTarget.getCache().getInt("burn_pulses"),
			"generic burn cache decrements before settlement");
		assertEquals(5, pulseTarget.getCache().getInt("burn_damage"),
			"generic burn cache retains configured damage");
		assertHit(pulseTarget, 5, HitSplat.TYPE_STANDARD,
			"generic burn uses compatibility standard presentation");
		pulseTarget.extinguish();

		final Player restored = harness.player(
			"dot burn restore", 632, 680, player -> {
				player.getCache().set("burn_damage", 4);
				player.getCache().set("burn_pulses", 3);
			});
		final BurnEvent restoredEvent = restored.getAttribute("burnEvent", null);
		assertNotNull(restoredEvent,
			"complete legacy burn pair restores one event");
		assertEquals(8L, restoredEvent.getTicksBeforeRun(),
			"restored generic burn starts a full countdown");
		assertEquals(1, eventCount(harness, restored, "Burn Event"),
			"restored generic burn event cardinality");
		restoredEvent.run();
		assertEquals(36, restored.getLevel(Skill.HITS.id()),
			"restored generic burn deals cached damage");
		assertEquals(2, restored.getCache().getInt("burn_pulses"),
			"restored burn persists decremented pulses");

		final Player partial = harness.player(
			"dot burn partial", 634, 680,
			player -> player.getCache().set("burn_damage", 7));
		assertNull(partial.getAttribute("burnEvent", null),
			"partial legacy burn pair does not schedule an event");
		assertTrue(partial.getCache().hasKey("burn_damage"),
			"current partial legacy burn cache remains stale");

		final Npc removed = npcWithHits(harness, 636, 680, 20);
		removed.setShouldRespawn(false);
		removed.applyBurn(5, 2);
		final BurnEvent removedEvent = removed.getAttribute("burnEvent", null);
		removed.remove();
		assertTrue(removed.isUnregistering(),
			"NPC removal fixture reaches terminal unregistering state");
		assertSame(removedEvent, removed.getAttribute("burnEvent", null),
			"current NPC removal leaves generic burn attached");
		removedEvent.run();
		assertEquals(15, removed.getLevel(Skill.HITS.id()),
			"generic burn can tick after NPC removal begins");
		assertTrue(removedEvent.isRunning(),
			"removed-NPC generic burn retains remaining pulses");
	}

	static void elderBurnSourceAvailability(
			final CurrentCombatHarness harness) throws Exception {
		final Player armorSource = harness.player("dot armor src", 650, 680);
		final Npc armorTarget = npcWithHits(harness, 651, 680, 20);
		ElderGreenDragonArmorEffect.applyBurn(armorSource, armorTarget);
		final GameTickEvent armorBurn = armorTarget.getAttribute(
			"elder_green_dragon_armor_burn_event", null);
		assertNotNull(armorBurn, "Elder armor burn fixture is active");
		harness.logout(armorSource);
		armorBurn.run();
		assertEquals(20, armorTarget.getLevel(Skill.HITS.id()),
			"Elder armor burn deals no pulse after source logout");
		assertFalse(armorBurn.isRunning(),
			"Elder armor burn stops after source logout");
		assertNull(armorTarget.getAttribute(
			"elder_green_dragon_armor_burn_event", null),
			"Elder armor burn clears target state after source logout");

		final Npc dragon = harness.npc(
			NpcId.ELDER_GREEN_DRAGON.id(), 654, 680);
		dragon.setShouldRespawn(false);
		final Player bossTarget = harness.player("dot boss burn", 655, 680);
		setHits(bossTarget, 40, 40);
		applyElderBossBurn(harness.world(), dragon, bossTarget);
		final GameTickEvent bossBurn = findOwnedEvent(
			harness, bossTarget, "Elder Green Dragon Burn");
		assertNotNull(bossBurn, "Elder boss burn fixture is active");
		dragon.remove();
		harness.world().unregisterNpc(dragon);
		assertTrue(dragon.isRemoved(),
			"Elder boss burn source leaves world membership");
		bossBurn.run();
		assertEquals(40, bossTarget.getLevel(Skill.HITS.id()),
			"Elder boss burn deals no pulse after source removal");
		assertFalse(bossBurn.isRunning(),
			"Elder boss burn stops after source removal");
		assertFalse(bossTarget.getAttribute(
			"elder_green_dragon_burn_active", false),
			"Elder boss burn clears active marker after source removal");
		assertNull(bossTarget.getAttribute(
			"elder_green_dragon_burn_source", null),
			"Elder boss burn clears source after source removal");
	}

	static void targetDeathAndLethalAttributionBoundaries(
			final CurrentCombatHarness harness) throws Exception {
		final Player victim = harness.player("dot death victim", 640, 680);
		final Npc killer = harness.npc(NpcId.GREATER_DEMON.id(), 641, 680);
		harness.recordOutgoingPackets(victim);
		victim.applyPoison(40, 40, killer);
		victim.applyBurn(3, 2);
		victim.getSkills().setLevel(Skill.HITS.id(), 0);
		victim.killedBy(killer);
		assertNull(victim.getAttribute("poisonEvent", null),
			"player death clears poison event");
		assertNull(victim.getAttribute("burnEvent", null),
			"player death clears generic burn event");
		assertFalse(victim.getCache().hasKey("poisoned"),
			"player death clears poison cache");
		assertFalse(victim.getCache().hasKey("poisoned_max"),
			"player death clears poison maximum cache");
		assertFalse(victim.getCache().hasKey("burn_damage"),
			"player death clears generic burn damage cache");
		assertFalse(victim.getCache().hasKey("burn_pulses"),
			"player death clears generic burn pulse cache");

		final Player poisonOwner = harness.player("dot toxin", 644, 680);
		harness.equip(poisonOwner, DRAGONSTONE_NECKLACE_OF_LEACH, 1);
		setHits(poisonOwner, 20, 40);
		final Player opponent = harness.player("dot rival", 646, 680);
		final Npc creditedTarget = npcWithHits(harness, 645, 680, 4);
		creditedTarget.setShouldRespawn(false);
		creditedTarget.setOpponent(opponent);
		final int ownerKillsBefore = poisonOwner.getNpcKills();
		final int opponentKillsBefore = opponent.getNpcKills();
		creditedTarget.applyPoison(40, 40, poisonOwner);
		final PoisonEvent creditedEvent = creditedTarget.getAttribute(
			"poisonEvent", null);
		creditedEvent.run();
		assertTrue(creditedTarget.isUnregistering(),
			"lethal poison enters ordinary NPC removal with an opponent");
		assertEquals(ownerKillsBefore, poisonOwner.getNpcKills(),
			"poison source currently receives no lethal NPC credit");
		assertEquals(opponentKillsBefore + 1, opponent.getNpcKills(),
			"current opponent receives lethal poison NPC credit");
		assertEquals(24, poisonOwner.getLevel(Skill.HITS.id()),
			"poison source still receives factual-damage Leach");
		assertFalse(creditedTarget.hasDamageBy(poisonOwner),
			"lethal poison still records no source contribution");

		setHits(poisonOwner, 20, 40);
		final Npc opponentless = npcWithHits(harness, 648, 680, 4);
		opponentless.setShouldRespawn(false);
		opponentless.applyPoison(40, 40, poisonOwner);
		final PoisonEvent opponentlessEvent = opponentless.getAttribute(
			"poisonEvent", null);
		opponentlessEvent.run();
		assertEquals(4, opponentless.getLevel(Skill.HITS.id()),
			"opponentless lethal helper leaves NPC Hits unchanged");
		assertFalse(opponentless.isUnregistering(),
			"opponentless lethal poison does not remove the NPC");
		assertEquals(0, opponentless.getCurrentPoisonPower(),
			"opponentless lethal poison is nevertheless cured");
		assertEquals(24, poisonOwner.getLevel(Skill.HITS.id()),
			"opponentless lethal compatibility result still drives Leach");
	}

	static void playerTargetLethalAttributionBoundaries(
			final CurrentCombatHarness harness) throws Exception {
		final Player poisonOwner = harness.player(
			"dot pvp poison owner", 680, 680);
		final Npc engagedNpc = harness.npc(
			NpcId.GREATER_DEMON.id(), 682, 680);
		final Player playerOwnedVictim = harness.player(
			"dot pvp owned victim", 681, 680);
		harness.recordOutgoingPackets(playerOwnedVictim);
		setHits(playerOwnedVictim, 4, 40);
		playerOwnedVictim.setOpponent(engagedNpc);
		playerOwnedVictim.applyPoison(40, 40, poisonOwner);
		final PoisonEvent ownedEvent = playerOwnedVictim.getAttribute(
			"poisonEvent", null);
		ownedEvent.run();
		final DeathLifecycleSnapshot ownedDeath =
			playerOwnedVictim.getDeathLifecycleSnapshot();
		assertTrue(ownedDeath.getContext().getKiller() == engagedNpc,
			"player-owned lethal poison currently credits the victim's opponent");
		assertTrue(ownedDeath.getContext().getKiller() != poisonOwner,
			"player-owned lethal poison does not credit its durable owner");

		final Npc poisonNpc = harness.npc(
			NpcId.DUNGEON_SPIDER.id(), 684, 680);
		final Npc unrelatedNpc = harness.npc(
			NpcId.GREATER_DEMON.id(), 686, 680);
		final Player npcOwnedVictim = harness.player(
			"dot npc owned victim", 685, 680);
		harness.recordOutgoingPackets(npcOwnedVictim);
		setHits(npcOwnedVictim, 4, 40);
		npcOwnedVictim.setOpponent(unrelatedNpc);
		npcOwnedVictim.applyPoison(40, 40, poisonNpc);
		final PoisonEvent npcOwnedEvent = npcOwnedVictim.getAttribute(
			"poisonEvent", null);
		assertNull(poisonOwner(npcOwnedEvent),
			"NPC poison source identity is discarded at application");
		npcOwnedEvent.run();
		final DeathLifecycleSnapshot npcOwnedDeath =
			npcOwnedVictim.getDeathLifecycleSnapshot();
		assertTrue(npcOwnedDeath.getContext().getKiller() == unrelatedNpc,
			"NPC-sourced lethal poison currently credits an unrelated opponent");
		assertTrue(npcOwnedDeath.getContext().getKiller() != poisonNpc,
			"NPC-sourced lethal poison cannot retain its applying NPC");

		final Npc environmentalOpponent = harness.npc(
			NpcId.GREATER_DEMON.id(), 688, 680);
		final Player environmentalVictim = harness.player(
			"dot environment victim", 687, 680);
		harness.recordOutgoingPackets(environmentalVictim);
		setHits(environmentalVictim, 4, 40);
		environmentalVictim.setOpponent(environmentalOpponent);
		environmentalVictim.applyPoison(40, 40);
		final PoisonEvent environmentalEvent = environmentalVictim.getAttribute(
			"poisonEvent", null);
		environmentalEvent.run();
		assertTrue(environmentalVictim.getDeathLifecycleSnapshot()
				.getContext().getKiller() == environmentalOpponent,
			"unattributed lethal poison currently credits the victim's opponent");
	}

	static void coreCombatPoisonProducerParity(
			final CurrentCombatHarness harness) throws Exception {
		final int weaponId = ItemId.POISONED_RUNE_DAGGER.id();

		final Player reciprocalSource = harness.player(
			"dot reciprocal source", 690, 680);
		harness.equip(reciprocalSource, weaponId, 1);
		final Npc reciprocalMiss = npcWithHits(harness, 691, 680, 40);
		final CombatEvent reciprocal = new CombatEvent(
			harness.world(), reciprocalSource, reciprocalMiss);
		invokePoisonProducer(reciprocal, CombatEvent.class,
			reciprocalSource, reciprocalMiss, 0);
		assertNull(reciprocalMiss.getAttribute("poisonEvent", null),
			"zero-damage reciprocal melee cannot poison");
		final Npc reciprocalHit = npcWithHits(harness, 692, 680, 40);
		invokePoisonProducer(reciprocal, CombatEvent.class,
			reciprocalSource, reciprocalHit, 1);
		assertWeaponPoison(reciprocalHit, reciprocalSource,
			"reciprocal melee poison");

		final Player pvmSource = harness.player("dot pvm source", 694, 680);
		harness.equip(pvmSource, weaponId, 1);
		final Npc pvmMiss = npcWithHits(harness, 695, 680, 40);
		final PvmMeleeEvent pvm = new PvmMeleeEvent(
			harness.world(), pvmSource, pvmMiss);
		invokePoisonProducer(pvm, PvmMeleeEvent.class, pvmSource, pvmMiss, 0);
		assertNull(pvmMiss.getAttribute("poisonEvent", null),
			"zero-damage PvM melee cannot poison");
		final Npc pvmHit = npcWithHits(harness, 696, 680, 40);
		invokePoisonProducer(pvm, PvmMeleeEvent.class, pvmSource, pvmHit, 1);
		assertWeaponPoison(pvmHit, pvmSource, "PvM melee poison");

		final Player projectileSource = harness.player(
			"dot projectile source", 698, 680);
		final Npc projectileMiss = npcWithHits(harness, 699, 680, 40);
		final ProjectileEvent missedProjectile = new ProjectileEvent(
			harness.world(), projectileSource, projectileMiss, 0, 2, false,
			weaponId);
		invokeNoArg(missedProjectile, ProjectileEvent.class,
			"applyWeaponPoison");
		assertNull(projectileMiss.getAttribute("poisonEvent", null),
			"zero-damage projectile cannot poison");
		final Npc projectileHit = npcWithHits(harness, 700, 680, 40);
		final ProjectileEvent hitProjectile = new ProjectileEvent(
			harness.world(), projectileSource, projectileHit, 1, 2, false,
			weaponId);
		invokeNoArg(hitProjectile, ProjectileEvent.class,
			"applyWeaponPoison");
		assertWeaponPoison(projectileHit, projectileSource,
			"projectile weapon poison");

		final Player acidSource = harness.player("dot acid source", 702, 680);
		final Npc acidMiss = npcWithHits(harness, 703, 680, 40);
		final ProjectileEvent missedAcid = acidProjectile(
			harness.world(), acidSource, acidMiss, 0);
		invokeNoArg(missedAcid, ProjectileEvent.class,
			"applyDualElementOnHitEffects");
		assertNull(acidMiss.getAttribute("poisonEvent", null),
			"zero-damage dual-element Acid cannot poison");
		final Npc acidHit = npcWithHits(harness, 704, 680, 40);
		forceNextLegacyRandomBelow(0.25D);
		final ProjectileEvent hitAcid = acidProjectile(
			harness.world(), acidSource, acidHit, 1);
		invokeNoArg(hitAcid, ProjectileEvent.class,
			"applyDualElementOnHitEffects");
		assertEquals(40, acidHit.getCurrentPoisonPower(),
			"dual-element Acid applied power");
		assertEquals(40, acidHit.getPoisonMaxPower(),
			"dual-element Acid maximum power");
		assertEquals(acidSource.getUUID(), poisonOwner(
			acidHit.getAttribute("poisonEvent", null)),
			"dual-element Acid source");
	}

	static void namedPoisonProducerParity(
			final CurrentCombatHarness harness) throws Exception {
		final SpellHandler spellHandler = new SpellHandler();
		final Player guthixSource = harness.player(
			"dot guthix source", 706, 680);
		final Npc primary = npcWithHits(harness, 707, 680, 40);
		invokeGuthixPoison(spellHandler, guthixSource, primary, false, true);
		assertEquals(20, primary.getCurrentPoisonPower(),
			"primary Guthix poison applied power");
		assertEquals(40, primary.getPoisonMaxPower(),
			"primary Guthix poison maximum power");
		assertEquals(guthixSource.getUUID(), poisonOwner(
			primary.getAttribute("poisonEvent", null)),
			"primary Guthix poison source");

		final Npc secondaryMiss = npcWithHits(harness, 708, 680, 40);
		forceNextLegacyRandomAtLeast(0.50D);
		invokeGuthixPoison(
			spellHandler, guthixSource, secondaryMiss, true, false);
		assertNull(secondaryMiss.getAttribute("poisonEvent", null),
			"failed secondary Guthix roll cannot poison");
		final Npc secondaryHit = npcWithHits(harness, 709, 680, 40);
		forceNextLegacyRandomBelow(0.50D);
		invokeGuthixPoison(
			spellHandler, guthixSource, secondaryHit, true, false);
		assertEquals(20, secondaryHit.getCurrentPoisonPower(),
			"successful advanced secondary Guthix applied power");
		assertEquals(40, secondaryHit.getPoisonMaxPower(),
			"successful advanced secondary Guthix maximum power");

		final Player auraSource = harness.player("dot aura source", 711, 680);
		auraSource.setPrayerBook(PrayerCatalog.GodLine.GUTHIX);
		harness.equip(auraSource, ItemId.GUTHIX_MACE.id(), 1);
		auraSource.getPrayers().setPrayer(Prayers.CORROSIVE_AURA, true, false);
		final Npc auraTarget = npcWithHits(harness, 712, 680, 40);
		assertFalse(CorrosiveAura.apply(auraSource, auraTarget, 0),
			"zero incoming damage cannot trigger Corrosive Aura");
		assertNull(auraTarget.getAttribute("poisonEvent", null),
			"failed Corrosive Aura leaves no poison state");
		assertTrue(CorrosiveAura.apply(auraSource, auraTarget, 1),
			"eligible Corrosive Aura application succeeds");
		assertEquals(10, auraTarget.getCurrentPoisonPower(),
			"full-health Corrosive Aura applied power");
		assertEquals(10, auraTarget.getPoisonMaxPower(),
			"first Corrosive Aura maximum power");
		assertEquals(auraSource.getUUID(), poisonOwner(
			auraTarget.getAttribute("poisonEvent", null)),
			"Corrosive Aura source");

		final NpcPoisonPlayerScript npcPoison = new NpcPoisonPlayerScript();
		final Npc spider = harness.npc(NpcId.DUNGEON_SPIDER.id(), 714, 680);
		final Player protectedVictim = harness.player(
			"dot protected victim", 715, 680);
		protectedVictim.setAntidoteProtection();
		assertFalse(npcPoison.shouldExecute(spider, protectedVictim),
			"antidote blocks NPC poison before its random roll");
		assertNull(protectedVictim.getAttribute("poisonEvent", null),
			"blocked NPC poison leaves no state");
		final Player npcVictim = harness.player("dot npc victim", 716, 680);
		forceNextLegacyIntAtLeast(90);
		assertTrue(npcPoison.shouldExecute(spider, npcVictim),
			"eligible poison NPC succeeds on a deterministic roll");
		npcPoison.executeScript(spider, npcVictim);
		assertEquals(38, npcVictim.getCurrentPoisonPower(),
			"default NPC poison applied power");
		assertNull(poisonOwner(npcVictim.getAttribute("poisonEvent", null)),
			"NPC poison is currently unattributed");
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int x, final int y, final int hits) {
		final Npc npc = harness.npc(NpcId.GREATER_DEMON.id(), x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, hits, false);
		return npc;
	}

	private static void setHits(final Player player, final int current,
			final int maximum) {
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), current, maximum, false);
	}

	private static UUID poisonOwner(final PoisonEvent event) {
		try {
			return (UUID) CurrentCombatHarness.readPrivateField(
				event, "poisonOwnerId");
		} catch (final ReflectiveOperationException failure) {
			throw new AssertionError("Unable to read poison owner", failure);
		}
	}

	private static void assertWeaponPoison(final Npc target,
			final Player source, final String label) {
		assertEquals(40, target.getCurrentPoisonPower(),
			label + " applied power");
		assertEquals(100, target.getPoisonMaxPower(),
			label + " maximum power");
		assertEquals(source.getUUID(), poisonOwner(
			target.getAttribute("poisonEvent", null)), label + " source");
	}

	private static ProjectileEvent acidProjectile(final World world,
			final Player source, final Npc target, final int damage) {
		return new ProjectileEvent(world, source, target, damage, 1, false,
			0, 0, 0, 0, 0, 0, false, 0, 40, 0, 0);
	}

	private static void invokePoisonProducer(final Object event,
			final Class<?> owner, final Mob source, final Mob target,
			final int damage) throws Exception {
		final Method method = owner.getDeclaredMethod("applyWeaponPoison",
			Mob.class, Mob.class, int.class);
		method.setAccessible(true);
		method.invoke(event, source, target, damage);
	}

	private static void invokeNoArg(final Object event, final Class<?> owner,
			final String methodName) throws Exception {
		final Method method = owner.getDeclaredMethod(methodName);
		method.setAccessible(true);
		method.invoke(event);
	}

	private static void invokeGuthixPoison(final SpellHandler handler,
			final Player source, final Mob target, final boolean advanced,
			final boolean primary) throws Exception {
		final Method method = SpellHandler.class.getDeclaredMethod(
			"applyGuthixGodSpellPoison", Player.class, Mob.class,
			boolean.class, boolean.class);
		method.setAccessible(true);
		method.invoke(handler, source, target, advanced, primary);
	}

	private static void applyElderBossBurn(final World world,
			final Npc dragon, final Player target) throws Exception {
		final Method method = ElderGreenDragonSpecialAttacks.class
			.getDeclaredMethod("applyBurn", World.class, Npc.class, Player.class);
		method.setAccessible(true);
		method.invoke(null, world, dragon, target);
	}

	private static void forceNextLegacyRandomBelow(final double threshold) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextDouble() < threshold) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy random seed found");
	}

	private static void forceNextLegacyRandomAtLeast(final double threshold) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextDouble() >= threshold) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy random seed found");
	}

	private static void forceNextLegacyIntAtLeast(final int threshold) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextInt(100) >= threshold) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy integer seed found");
	}

	private static void forceNextLegacyIntBelow(final int low, final int high,
			final int expected) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (low + candidate.nextInt(high - low + 1) == expected) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy integer seed found");
	}

	private static GameTickEvent findOwnedEvent(
			final CurrentCombatHarness harness, final Object owner,
			final String descriptor) {
		for (GameTickEvent event : harness.server().getGameEventHandler().getEvents()) {
			if (event.isRunning() && event.getOwner() == owner
					&& descriptor.equals(event.getDescriptor())) {
				return event;
			}
		}
		return null;
	}

	private static int eventCount(final CurrentCombatHarness harness,
			final Object owner, final String descriptor) {
		int count = 0;
		for (GameTickEvent event : harness.server().getGameEventHandler().getEvents()) {
			if (event.isRunning() && event.getOwner() == owner
					&& descriptor.equals(event.getDescriptor())) {
				count++;
			}
		}
		return count;
	}

	private static void assertHit(final Npc target, final int amount,
			final int type, final String label) {
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			label + " hitsplat count");
		assertEquals(amount,
			target.getUpdateFlags().getHitSplats().get(0).getAmount(),
			label + " amount");
		assertEquals(type,
			target.getUpdateFlags().getHitSplats().get(0).getType(),
			label + " type");
	}

	private static void assertHit(final Player target, final int amount,
			final int type, final String label) {
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			label + " hitsplat count");
		assertEquals(amount,
			target.getUpdateFlags().getHitSplats().get(0).getAmount(),
			label + " amount");
		assertEquals(type,
			target.getUpdateFlags().getHitSplats().get(0).getType(),
			label + " type");
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

	private static void assertNull(final Object value, final String message) {
		assertTrue(value == null, message + ": expected null, got " + value);
	}

	private static void assertNotNull(final Object value, final String message) {
		assertTrue(value != null, message + ": expected a value");
	}

	private static void assertThrows(
			final Class<? extends Throwable> expected,
			final ThrowingAction action, final String message) {
		try {
			action.run();
		} catch (final Throwable failure) {
			if (expected.isInstance(failure)) {
				return;
			}
			throw new AssertionError(message + ": expected "
				+ expected.getSimpleName() + ", got "
				+ failure.getClass().getSimpleName(), failure);
		}
		throw new AssertionError(message + ": expected "
			+ expected.getSimpleName());
	}

	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	private static void assertSame(final Object expected, final Object actual,
			final String message) {
		// Event-object identity is the lifecycle ownership fact under test.
		assertTrue(expected == actual, message + ": expected same instance");
	}

	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	private static void assertNotSame(final Object first, final Object second,
			final String message) {
		// Replacement must create a different lifecycle object, not an equal value.
		assertTrue(first != second, message + ": expected different instances");
	}

	private static void assertEquals(final long expected, final long actual,
			final String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private interface ThrowingAction {
		void run() throws Exception;
	}
}
