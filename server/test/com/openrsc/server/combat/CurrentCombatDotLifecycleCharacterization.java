package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.ElderGreenDragonArmorEffect;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.BurnEvent;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.event.rsc.impl.combat.ElderGreenDragonSpecialAttacks;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.world.World;

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

	private static void applyElderBossBurn(final World world,
			final Npc dragon, final Player target) throws Exception {
		final Method method = ElderGreenDragonSpecialAttacks.class
			.getDeclaredMethod("applyBurn", World.class, Npc.class, Player.class);
		method.setAccessible(true);
		method.invoke(null, world, dragon, target);
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
}
