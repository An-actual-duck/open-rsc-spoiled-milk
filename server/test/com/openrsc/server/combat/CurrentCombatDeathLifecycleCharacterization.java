package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable pre-migration A05.5 death and compatibility policies. */
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
		target.addDeathListener(new NpcLootEvent(harness.world(),
			target.getLocation(), target.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				listeners.incrementAndGet();
				killedAtListener.set(target.killed);
				removedAtListener.set(target.isRemoved());
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

		victim.killedBy(killer);
		assertEquals(1, harness.countOutgoingPackets(
			victim, OpcodeOut.SEND_DEATH),
			"duplicate player death emits no second death packet");

		final GameTickEvent reset = harness.findEvent("Reset Killed");
		assertNotNull(reset, "player death schedules its legacy killed reset");
		reset.run();
		assertFalse(victim.killed,
			"player respawn delay releases the legacy killed guard");
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

		target.killedBy(killer);
		assertEquals(2, plugin.blockCalls.get(),
			"maintained plugin-owned compatibility accepts another decision");
		assertEquals(0, listeners.get(),
			"repeat plugin-owned decision still bypasses ordinary listeners");
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
}
