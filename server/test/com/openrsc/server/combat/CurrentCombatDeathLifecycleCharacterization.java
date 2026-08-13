package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerBalances;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.death.DeathContext;
import com.openrsc.server.model.entity.death.DeathLifecycleSnapshot;
import com.openrsc.server.model.entity.death.DeathLifecycleState;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.nio.file.Paths;

/** Executable A05.5 death-lifecycle and compatibility policies. */
final class CurrentCombatDeathLifecycleCharacterization {
	private CurrentCombatDeathLifecycleCharacterization() {
	}

	static void ordinaryNpcDeathPolicies(final CurrentCombatHarness harness) {
		final Player killer = harness.player("death lifecycle npc", 610, 760);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 611, 760);
		target.setShouldRespawn(false);
		final int targetHits = target.getLevel(Skill.HITS.id());
		target.addCombatDamage(killer, targetHits);
		target.getSkills().setLevel(Skill.HITS.id(), 0);
		final int killsBefore = killer.getNpcKills();
		final int meleeExperienceBefore = killer.getSkills()
			.getExperience(Skill.MELEE.id());
		final AtomicInteger listeners = new AtomicInteger();
		final AtomicBoolean killedAtListener = new AtomicBoolean();
		final AtomicBoolean removedAtListener = new AtomicBoolean();
		final AtomicBoolean reentered = new AtomicBoolean();
		final long combatLifecycleAtBegin = target.getCombatLifecycle();
		target.addDeathListener(new NpcLootEvent(harness.world(),
			target.getLocation(), target.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				listeners.incrementAndGet();
				killedAtListener.set(target.killed);
				removedAtListener.set(target.isRemoved());
				if (reentered.compareAndSet(false, true)) {
					target.killedBy(killer);
				}
			}
		});

		target.killedBy(killer);

		assertEquals(1, listeners.get(),
			"ordinary NPC death listener cardinality");
		assertFalse(killedAtListener.get(),
			"ordinary NPC listener precedes legacy killed projection");
		assertFalse(removedAtListener.get(),
			"ordinary NPC listener precedes removal");
		assertTrue(target.killed,
			"ordinary NPC removal sets legacy killed projection");
		assertTrue(target.isUnregistering(),
			"ordinary non-respawning NPC queues terminal unregister");
		final DeathLifecycleSnapshot terminal =
			target.getDeathLifecycleSnapshot();
		assertEquals(DeathLifecycleState.DEAD, terminal.getState(),
			"ordinary NPC lifecycle reaches dead after removal");
		assertEquals(1L, terminal.getDuplicateAttempts(),
			"reentrant NPC death is rejected exactly once");
		assertNotNull(terminal.getContext(),
			"ordinary NPC retains its completed death identity");
		assertTrue(terminal.getContext().getKiller() == killer,
			"ordinary NPC death identity retains the direct killer");
		assertEquals(combatLifecycleAtBegin,
			terminal.getContext().getTargetCombatLifecycle(),
			"ordinary NPC death captures the pre-removal combat generation");
		assertEquals(killsBefore + 1, killer.getNpcKills(),
			"ordinary NPC awards one kill counter");
		assertTrue(killer.getSkills().getExperience(Skill.MELEE.id())
			> meleeExperienceBefore,
			"ordinary NPC distributes contribution experience before removal");

		target.killedBy(killer);
		assertEquals(1, listeners.get(),
			"post-removal duplicate cannot replay an NPC listener");
		assertEquals(killsBefore + 1, killer.getNpcKills(),
			"post-removal duplicate cannot replay kill rewards");

		final Npc respawning = harness.npc(
			NpcId.GREATER_DEMON.id(), 612, 760);
		final int respawningHits = respawning.getLevel(Skill.HITS.id());
		respawning.addCombatDamage(killer, respawningHits);
		respawning.getSkills().setLevel(Skill.HITS.id(), 0);
		respawning.killedBy(killer);
		final DeathLifecycleSnapshot waiting =
			respawning.getDeathLifecycleSnapshot();
		assertEquals(DeathLifecycleState.RESPAWNING, waiting.getState(),
			"ordinary respawning NPC retains terminal ownership during delay");
		final DeathContext completedContext = waiting.getContext();
		final GameTickEvent respawn = harness.findEvent("Respawn NPC");
		assertNotNull(respawn, "ordinary NPC schedules its production respawn");
		respawn.run();
		final DeathLifecycleSnapshot alive =
			respawning.getDeathLifecycleSnapshot();
		assertEquals(DeathLifecycleState.ALIVE, alive.getState(),
			"NPC respawn creates a new live death generation");
		assertEquals(waiting.getLifecycleId() + 1L, alive.getLifecycleId(),
			"NPC respawn advances death generation exactly once");
		assertTrue(alive.getContext() == null,
			"NPC respawn clears the completed death identity");
		assertFalse(respawning.completeDeathLifecycleRespawn(completedContext),
			"stale NPC respawn context cannot reset the new lifetime");
	}

	static void failedNpcDeathCannotReplay(final CurrentCombatHarness harness) {
		final Player killer = harness.player("failed death owner", 620, 770);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 621, 770);
		target.setShouldRespawn(false);
		final int targetHits = target.getLevel(Skill.HITS.id());
		target.addCombatDamage(killer, targetHits);
		target.getSkills().setLevel(Skill.HITS.id(), 0);
		final int killsBefore = killer.getNpcKills();
		final AtomicInteger listeners = new AtomicInteger();
		target.addDeathListener(new NpcLootEvent(harness.world(),
			target.getLocation(), target.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				listeners.incrementAndGet();
				throw new IllegalStateException(
					"deliberate death-listener fixture failure");
			}
		});

		boolean propagated = false;
		try {
			target.killedBy(killer);
		} catch (final IllegalStateException expected) {
			propagated = true;
		}

		assertTrue(propagated,
			"death-listener failure retains existing propagation");
		assertTrue(target.killed,
			"failed optional NPC callback still completes removal");
		assertTrue(target.isUnregistering(),
			"failed optional NPC callback queues terminal unregister");
		assertEquals(DeathLifecycleState.DEAD,
			target.getDeathLifecycleSnapshot().getState(),
			"failed optional NPC callback retains terminal ownership");
		assertEquals(1, listeners.get(),
			"failed optional NPC listener executes once");
		assertEquals(killsBefore + 1, killer.getNpcKills(),
			"rewards preceding the failed listener execute once");

		target.killedBy(killer);
		assertEquals(1, listeners.get(),
			"later lethal request cannot replay a failed listener");
		assertEquals(killsBefore + 1, killer.getNpcKills(),
			"later lethal request cannot replay preceding rewards");
	}

	/**
	 * Exercises the production Npc.killedBy -> processLegacyDeath Slayer hook,
	 * including both contributor orders and failures that must not alter the
	 * existing XP/drop/listener/removal/respawn lifecycle.
	 */
	static void monsterSlayerCreditFailureIsolation(final CurrentCombatHarness harness)
			throws Exception {
		final MonsterSlayerData data = MonsterSlayerData.load(Paths.get(
			"conf", "server", "defs", "extras", "MonsterSlayer.json"),
			acceptingSlayerCatalog());
		harness.installMonsterSlayerTaskService(new MonsterSlayerTaskService(data));
		assertCorruptAndValidContributors(harness, data, true);
		assertCorruptAndValidContributors(harness, data, false);
		assertOverflowDoesNotAbortDeath(harness, data, false);
		assertOverflowDoesNotAbortDeath(harness, data, true);
		assertRejectedCallbacksRemainHarmless(harness, data);
		monsterSlayerSingleWinnerSelection(harness, data);
	}

	/** Covers the Slayer-only single-winner policy without changing XP or loot ownership. */
	private static void monsterSlayerSingleWinnerSelection(final CurrentCombatHarness harness,
			final MonsterSlayerData data) throws Exception {
		final Player lower = slayerTaskHolder(harness, data, "msslower", 780, 790);
		final Player higher = slayerTaskHolder(harness, data, "msshigher", 781, 790);
		Npc higherWins = harness.npc(62, 782, 790);
		higherWins.setShouldRespawn(false);
		higherWins.addCombatDamage(lower, 3);
		higherWins.addRangeDamage(higher, 5);
		higherWins.getSkills().setLevel(Skill.HITS.id(), 0);
		higherWins.killedBy(higher);
		assertEquals(0, slayerKills(lower, data), "lower-damage eligible player receives no Slayer credit");
		assertEquals(1, slayerKills(higher, data), "higher-damage eligible player receives Slayer credit");

		final Player eligible = slayerTaskHolder(harness, data, "msseligible", 790, 790);
		final Player ineligible = harness.player("mssineligible", 791, 790);
		Npc eligibleWins = harness.npc(62, 792, 790);
		eligibleWins.setShouldRespawn(false);
		eligibleWins.addMageDamage(eligible, 2);
		eligibleWins.addCombatDamage(ineligible, 9);
		eligibleWins.getSkills().setLevel(Skill.HITS.id(), 0);
		eligibleWins.killedBy(ineligible);
		assertEquals(1, slayerKills(eligible, data), "eligible task holder defeats ineligible high-damage owner");
		assertFalse(ineligible.getCache().hasKey("monster_slayer_active_kills"),
			"ineligible high-damage owner receives no Slayer state");

		final Player tieA = slayerTaskHolder(harness, data, "msstiea", 800, 790);
		final Player tieB = slayerTaskHolder(harness, data, "msstieb", 801, 790);
		Npc tie = harness.npc(62, 802, 790);
		tie.setShouldRespawn(false);
		tie.addSummonDamage(tieA, 4);
		tie.addCombatDamage(tieB, 4);
		tie.getSkills().setLevel(Skill.HITS.id(), 0);
		tie.killedBy(tieA);
		Player tieWinner = tieA.getUUID().compareTo(tieB.getUUID()) < 0 ? tieA : tieB;
		Player tieLoser = tieWinner == tieA ? tieB : tieA;
		assertEquals(1, slayerKills(tieWinner, data), "UUID ordering resolves equal Slayer damage deterministically");
		assertEquals(0, slayerKills(tieLoser, data), "equal-damage Slayer tie has exactly one winner");

		final Player solo = slayerTaskHolder(harness, data, "msssolo", 810, 790);
		Npc soloTarget = harness.npc(62, 811, 790);
		soloTarget.setShouldRespawn(false);
		soloTarget.addSummonDamage(solo, 1);
		soloTarget.getSkills().setLevel(Skill.HITS.id(), 0);
		soloTarget.killedBy(solo);
		assertEquals(1, slayerKills(solo, data), "one eligible summon contributor behaves normally");

		final Player nearby = slayerTaskHolder(harness, data, "mssnearby", 820, 790);
		final Player zeroDamage = slayerTaskHolder(harness, data, "msszero", 821, 790);
		final Player tooFar = slayerTaskHolder(harness, data, "mssfar", 850, 790);
		final Player offline = slayerTaskHolder(harness, data, "mssoffline", 822, 790);
		Npc guarded = harness.npc(62, 823, 790);
		guarded.setShouldRespawn(false);
		guarded.addCombatDamage(nearby, 3);
		guarded.addCombatDamage(tooFar, 9);
		guarded.addRangeDamage(offline, 8);
		harness.logout(offline);
		guarded.getSkills().setLevel(Skill.HITS.id(), 0);
		guarded.killedBy(nearby);
		assertEquals(1, slayerKills(nearby, data), "nearby valid contributor remains sole Slayer winner");
		assertEquals(0, slayerKills(zeroDamage, data), "eligible non-contributor receives no Slayer credit");
		assertEquals(0, slayerKills(tooFar, data), "out-of-range contributor receives no Slayer credit");
		assertEquals(0, slayerKills(offline, data), "offline contributor receives no Slayer credit");
	}

	private static Player slayerTaskHolder(final CurrentCombatHarness harness,
			final MonsterSlayerData data, final String name, final int x, final int y) {
		Player player = harness.player(name, x, y);
		MonsterSlayerState.write(player.getCache(), data,
			activeRatState(data, MonsterSlayerBalances.zero(), 0, 0L));
		return player;
	}

	private static int slayerKills(final Player player, final MonsterSlayerData data) {
		return MonsterSlayerState.read(player.getCache(), data).getActiveKills();
	}

	/** Verifies only an accepted non-final Slayer credit emits a progress message. */
	static void monsterSlayerProgressMessagesFollowCreditedKills(
			final CurrentCombatHarness harness) throws Exception {
		final MonsterSlayerData data = MonsterSlayerData.load(Paths.get(
			"conf", "server", "defs", "extras", "MonsterSlayer.json"),
			acceptingSlayerCatalog());
		harness.installMonsterSlayerTaskService(new MonsterSlayerTaskService(data));

		final Player progressing = harness.player("msprogress", 760, 790);
		MonsterSlayerState.write(progressing.getCache(), data, activeRatState(data,
			MonsterSlayerBalances.zero(), 38, 0L));
		harness.recordOutgoingPackets(progressing);
		killForSlayerProgress(harness, progressing, 62, 761);
		assertEquals(39, MonsterSlayerState.read(progressing.getCache(), data).getActiveKills(),
			"credited kill records non-final task progress");
		assertEquals(1, harness.countOutgoingPackets(progressing,
			OpcodeOut.SEND_SERVER_MESSAGE), "non-final credited kill emits one progress message");

		killForSlayerProgress(harness, progressing, 62, 762);
		assertEquals(1L, MonsterSlayerState.read(progressing.getCache(), data).getTasksCompleted(),
			"final credited kill completes the task");
		assertEquals(1, harness.countOutgoingPackets(progressing,
			OpcodeOut.SEND_SERVER_MESSAGE), "final completion does not replace its existing message path");

		final Player unrelated = harness.player("msunrelated", 764, 790);
		MonsterSlayerState.write(unrelated.getCache(), data, activeRatState(data,
			MonsterSlayerBalances.zero(), 0, 0L));
		harness.recordOutgoingPackets(unrelated);
		killForSlayerProgress(harness, unrelated, 19, 765);
		assertEquals(0, harness.countOutgoingPackets(unrelated,
			OpcodeOut.SEND_SERVER_MESSAGE), "unrelated kill emits no Slayer progress message");
	}

	private static void killForSlayerProgress(final CurrentCombatHarness harness,
			final Player player, final int npcId, final int x) {
		final Npc target = harness.npc(npcId, x, 790);
		target.setShouldRespawn(false);
		target.addCombatDamage(player, Math.max(1, target.getDef().getHits()));
		target.getSkills().setLevel(Skill.HITS.id(), 0);
		target.killedBy(player);
	}

	private static void assertCorruptAndValidContributors(final CurrentCombatHarness harness,
			final MonsterSlayerData data, final boolean corruptFirst) throws Exception {
		final int x = corruptFirst ? 700 : 710;
		final Player valid = harness.player("msv" + x, x, 790);
		final Player corrupt = harness.player("msc" + x, x + 1, 790);
		assertFalse(valid.getUUID().equals(corrupt.getUUID()), "fixture contributors have distinct identities");
		MonsterSlayerState.write(valid.getCache(), data, activeRatState(data,
			MonsterSlayerBalances.zero(), 39, 0L));
		assertEquals(MonsterSlayerState.TaskResult.Reason.COMPLETED,
			MonsterSlayerState.recordEligibleKill(MonsterSlayerState.read(valid.getCache(), data), data, 62)
				.getReason(), "fixture task accepts a goblin before the NPC lifecycle");
		corrupt.getCache().store("monster_slayer_balance_fledgling", "corrupt-evidence");
		corrupt.getCache().store("unrelated_corrupt_evidence", "retain");
		final Map<String, Object> rawCorrupt = new LinkedHashMap<String, Object>(
			corrupt.getCache().getCacheMap());
		final Npc rat = harness.npc(62, x + 2, 790);
		final int hits = Math.max(1, rat.getDef().getHits());
		if (corruptFirst) {
			rat.addCombatDamage(corrupt, hits);
			rat.addCombatDamage(valid, hits);
		} else {
			rat.addCombatDamage(valid, hits);
			rat.addCombatDamage(corrupt, hits);
		}
		@SuppressWarnings("unchecked")
		final List<java.util.UUID> contributors = (List<java.util.UUID>) CurrentCombatHarness.invokePrivate(
			rat, "getAllDamageDealerIds", new Class<?>[0]);
		assertTrue(contributors.contains(valid.getUUID()), "fixture records valid contributor");
		assertTrue(contributors.contains(corrupt.getUUID()), "fixture records corrupt contributor");
		assertTrue(harness.world().getPlayers().contains(valid), "valid contributor is present in world list");
		assertTrue(harness.world().getPlayerByUUID(valid.getUUID()) == valid, "valid contributor resolves from world");
		assertFalse(valid.isRemoved(), "valid contributor is live");
		assertTrue(valid.getSkills().getLevel(Skill.HITS.id()) > 0, "valid contributor has hits");
		assertTrue(rat.sharesSpatialDomain(valid), "valid fixture contributor shares spatial domain");
		assertTrue(rat.getLocation().withinRange(valid.getLocation(), 16), "valid fixture contributor is nearby");
		rat.getSkills().setLevel(Skill.HITS.id(), 0);
		final int validXp = valid.getSkills().getExperience(Skill.MELEE.id());
		final AtomicInteger listeners = new AtomicInteger();
		rat.addDeathListener(new NpcLootEvent(harness.world(), rat.getLocation(), rat.getID(),
			1, ItemId.COINS.id()) {
			@Override public void onLootNpcDeath(Player player, Npc npc) { listeners.incrementAndGet(); }
		});
		rat.killedBy(valid);
		final MonsterSlayerState.Snapshot completed = MonsterSlayerState.read(valid.getCache(), data);
		assertEquals(1L, completed.getTasksCompleted(), "valid contributor completes once active="
			+ completed.getActiveTaskKey() + " kills=" + completed.getActiveKills());
		assertEquals(2L, completed.getBalances().get(MonsterSlayerChallenge.FLEDGLING),
			"valid contributor receives native points");
		assertEquals(rawCorrupt, corrupt.getCache().getCacheMap(),
			"quarantined contributor raw cache remains exact");
		assertTrue(valid.getSkills().getExperience(Skill.MELEE.id()) > validXp,
			"Slayer corruption cannot interrupt XP");
		assertEquals(1, listeners.get(), "Slayer corruption cannot interrupt loot listener");
		assertTrue(rat.isRespawning(), "Slayer corruption cannot interrupt respawn scheduling");
		assertNotNull(harness.findEvent("Respawn NPC"), "production respawn event remains scheduled");
	}

	private static void assertOverflowDoesNotAbortDeath(final CurrentCombatHarness harness,
			final MonsterSlayerData data, final boolean lifetimeOverflow) throws Exception {
		final int x = lifetimeOverflow ? 720 : 730;
		final Player player = harness.player("mso" + x, x, 790);
		final MonsterSlayerBalances balances = lifetimeOverflow
			? MonsterSlayerBalances.zero() : balancesAtCap();
		MonsterSlayerState.write(player.getCache(), data, activeRatState(data, balances, 39,
			lifetimeOverflow ? Long.MAX_VALUE : 0L));
		final Map<String, Object> before = new LinkedHashMap<String, Object>(player.getCache().getCacheMap());
		final Npc rat = harness.npc(62, x + 1, 790);
		final int hits = Math.max(1, rat.getDef().getHits());
		rat.addCombatDamage(player, hits);
		rat.getSkills().setLevel(Skill.HITS.id(), 0);
		final AtomicInteger listeners = new AtomicInteger();
		rat.addDeathListener(new NpcLootEvent(harness.world(), rat.getLocation(), rat.getID(),
			1, ItemId.COINS.id()) {
			@Override public void onLootNpcDeath(Player ignored, Npc npc) { listeners.incrementAndGet(); }
		});
		final int xp = player.getSkills().getExperience(Skill.MELEE.id());
		rat.killedBy(player);
		assertEquals(before, player.getCache().getCacheMap(), "overflow writes are atomic");
		assertTrue(player.getSkills().getExperience(Skill.MELEE.id()) > xp,
			"overflow cannot interrupt XP");
		assertEquals(1, listeners.get(), "overflow cannot interrupt listener");
		assertTrue(rat.isRespawning(), "overflow cannot interrupt respawn");
	}

	private static void assertRejectedCallbacksRemainHarmless(final CurrentCombatHarness harness,
			final MonsterSlayerData data) throws Exception {
		final Player player = harness.player("msreject", 740, 790);
		MonsterSlayerState.write(player.getCache(), data, activeRatState(data,
			MonsterSlayerBalances.zero(), 0, 0L));
		final Npc goblin = harness.npc(19, 741, 790);
		goblin.setShouldRespawn(false);
		goblin.addCombatDamage(player, Math.max(1, goblin.getDef().getHits()));
		goblin.getSkills().setLevel(Skill.HITS.id(), 0);
		goblin.killedBy(player);
		assertEquals(0, MonsterSlayerState.read(player.getCache(), data).getActiveKills(),
			"wrong family is harmless");
		goblin.killedBy(player);
		assertEquals(0, MonsterSlayerState.read(player.getCache(), data).getActiveKills(),
			"duplicate callback is harmless");
		final Player noTask = harness.player("msnotask", 745, 790);
		final Npc rat = harness.npc(19, 746, 790);
		rat.setShouldRespawn(false);
		rat.addCombatDamage(noTask, Math.max(1, rat.getDef().getHits()));
		rat.getSkills().setLevel(Skill.HITS.id(), 0);
		rat.killedBy(noTask);
		assertFalse(noTask.getCache().hasKey("monster_slayer_active_kills"),
			"no task writes no Slayer progression");
	}

	private static MonsterSlayerState.Snapshot activeRatState(final MonsterSlayerData data,
			final MonsterSlayerBalances balances, final int kills, final long completions) {
		final Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) {
			cursors.put(contact.getKey(), 0);
		}
		return MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING, balances, cursors,
			"falador.goblins", kills, completions, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
	}

	private static MonsterSlayerBalances balancesAtCap() {
		final Map<MonsterSlayerChallenge, Long> amounts =
			new LinkedHashMap<MonsterSlayerChallenge, Long>();
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) amounts.put(challenge, 0L);
		amounts.put(MonsterSlayerChallenge.FLEDGLING, MonsterSlayerBalances.MAX_BALANCE);
		return MonsterSlayerBalances.of(amounts);
	}

	private static MonsterSlayerData.ReferenceCatalog acceptingSlayerCatalog() {
		return new MonsterSlayerData.ReferenceCatalog() {
			public boolean npcExists(int npcId) { return true; }
			public boolean npcAttackable(int npcId) { return true; }
			public boolean npcSpawned(int npcId) { return true; }
			public boolean itemExists(int itemId) { return true; }
		};
	}

	static void playerDeathPolicies(final CurrentCombatHarness harness)
			throws Exception {
		final Player victim = harness.player(
			"death lifecycle player", 630, 760);
		final Npc killer = harness.npc(NpcId.GREATER_DEMON.id(), 631, 760);
		harness.recordOutgoingPackets(victim);
		victim.applyPoison(120, killer);
		victim.getSkills().setLevel(Skill.HITS.id(), 0);
		final long combatLifecycleBefore = victim.getCombatLifecycle();

		victim.killedBy(killer);

		assertTrue(victim.killed,
			"player remains guarded during the respawn delay");
		assertTrue(victim.getCombatLifecycle() > combatLifecycleBefore,
			"player death advances combat ownership lifecycle");
		assertEquals(0, victim.getCurrentPoisonPower(),
			"player death clears poison state");
		assertEquals(victim.getSkills().getMaxStat(Skill.HITS.id()),
			victim.getLevel(Skill.HITS.id()),
			"player death normalizes Hits at the configured respawn");
		assertEquals(1, harness.countOutgoingPackets(
			victim, OpcodeOut.SEND_DEATH),
			"player death packet cardinality");
		final DeathLifecycleSnapshot waiting = victim.getDeathLifecycleSnapshot();
		assertEquals(DeathLifecycleState.RESPAWNING, waiting.getState(),
			"player lifecycle remains respawning during killed guard");
		assertTrue(waiting.getContext().getKiller() == killer,
			"player lifecycle retains its direct killer identity");

		victim.killedBy(killer);
		assertEquals(1, harness.countOutgoingPackets(
			victim, OpcodeOut.SEND_DEATH),
			"duplicate player death emits no second death packet");

		final GameTickEvent reset = harness.findEvent("Reset Killed");
		assertNotNull(reset, "player death schedules its legacy killed reset");
		reset.run();
		assertFalse(victim.killed,
			"player respawn delay releases the legacy killed guard");
		final DeathLifecycleSnapshot alive = victim.getDeathLifecycleSnapshot();
		assertEquals(DeathLifecycleState.ALIVE, alive.getState(),
			"player killed reset creates a live death generation");
		assertEquals(waiting.getLifecycleId() + 1L, alive.getLifecycleId(),
			"player respawn advances death generation exactly once");
		assertTrue(alive.getContext() == null,
			"player respawn clears the completed death identity");
		assertFalse(victim.completeDeathLifecycleRespawn(waiting.getContext()),
			"stale player reset cannot alter the new lifetime");
	}

	static void pluginOwnedNpcCompatibility(final CurrentCombatHarness harness)
			throws Exception {
		final Player killer = harness.player("plugin death owner", 650, 760);
		final Npc target = harness.npc(NpcId.COUNT_DRAYNOR.id(), 651, 760);
		target.setShouldRespawn(false);
		final RecordingKillPlugin plugin = new RecordingKillPlugin(target);
		harness.installPlugin(KillNpcTrigger.class, plugin);
		final AtomicInteger listeners = new AtomicInteger();
		target.addDeathListener(new NpcLootEvent(harness.world(),
			target.getLocation(), target.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				listeners.incrementAndGet();
			}
		});
		target.getSkills().setLevel(Skill.HITS.id(), 0);

		target.killedBy(killer);

		assertEquals(1, plugin.blockCalls.get(),
			"plugin-owned death performs one synchronous block decision");
		assertFalse(target.killed,
			"plugin-owned death does not claim the legacy killed projection");
		assertFalse(target.isRemoved(),
			"plugin-owned death leaves removal to its quest plugin");
		assertEquals(0, listeners.get(),
			"plugin-owned death bypasses ordinary reward listeners");
		assertEquals(0, killer.getNpcKills(),
			"plugin-owned death bypasses ordinary kill rewards");
		assertEquals(DeathLifecycleState.ALIVE,
			target.getDeathLifecycleSnapshot().getState(),
			"plugin-owned NPC remains outside ordinary lifecycle authority");
		assertTrue(target.getDeathLifecycleSnapshot().getContext() == null,
			"plugin-owned NPC has no misleading ordinary death identity");

		target.killedBy(killer);
		assertEquals(2, plugin.blockCalls.get(),
			"maintained plugin-owned compatibility accepts another decision");
		assertEquals(0, listeners.get(),
			"repeat plugin-owned decision still bypasses ordinary listeners");
	}

	static void playerTutorialAndLoggedOutCompatibility(
			final CurrentCombatHarness harness) throws Exception {
		final Player tutorial = harness.player(
			"tutorial death lifecycle", 200, 730);
		final Npc peter = harness.npc(NpcId.PETER_SKIPPIN.id(), 201, 730);
		harness.recordOutgoingPackets(tutorial);
		tutorial.getSkills().setLevel(Skill.HITS.id(), 0);
		final long tutorialGeneration = tutorial.getDeathLifecycleSnapshot()
			.getLifecycleId();

		tutorial.killedBy(peter);

		assertFalse(tutorial.killed,
			"Peter Skippin tutorial death immediately releases killed guard");
		assertEquals(DeathLifecycleState.ALIVE,
			tutorial.getDeathLifecycleSnapshot().getState(),
			"tutorial survivor returns to a live death lifecycle");
		assertEquals(tutorialGeneration + 1L,
			tutorial.getDeathLifecycleSnapshot().getLifecycleId(),
			"tutorial survivor advances death identity exactly once");
		assertTrue(tutorial.getDeathLifecycleSnapshot().getContext() == null,
			"tutorial survivor clears the discarded death identity");
		assertEquals(1, harness.countOutgoingPackets(
			tutorial, OpcodeOut.SEND_DEATH),
			"tutorial skip retains its legacy death packet");

		final Player loggedOut = harness.player(
			"logged out death lifecycle", 660, 760);
		loggedOut.setLoggedIn(false);
		final long loggedOutGeneration = loggedOut.getDeathLifecycleSnapshot()
			.getLifecycleId();
		loggedOut.killedBy(peter);
		assertFalse(loggedOut.killed,
			"logged-out player death remains an intentional no-op");
		assertEquals(DeathLifecycleState.ALIVE,
			loggedOut.getDeathLifecycleSnapshot().getState(),
			"logged-out no-op acquires no death ownership");
		assertEquals(loggedOutGeneration,
			loggedOut.getDeathLifecycleSnapshot().getLifecycleId(),
			"logged-out no-op does not advance death identity");
	}

	private static final class RecordingKillPlugin implements KillNpcTrigger {
		private final Npc expected;
		private final AtomicInteger blockCalls = new AtomicInteger();

		private RecordingKillPlugin(final Npc expected) {
			this.expected = expected;
		}

		@Override
		public boolean blockKillNpc(final Player player, final Npc npc) {
			assertTrue(npc == expected,
				"fixture kill plugin receives the plugin-owned NPC");
			blockCalls.incrementAndGet();
			return true;
		}

		@Override
		public void onKillNpc(final Player player, final Npc npc) {
			// The action is deliberately scheduler-owned; only the synchronous
			// compatibility decision is part of this fixture.
		}
	}

	private static void assertNotNull(final Object value,
			final String message) {
		assertTrue(value != null, message);
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
				+ ", got " + actual);
		}
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
