package com.openrsc.server.diagnostics;

import com.openrsc.server.Server;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.AttackIntent;
import com.openrsc.server.model.combat.AttackTransactionResult;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.PlayerAttackTransaction;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.triggers.TimedEventTrigger;
import com.openrsc.server.runtime.DeterministicGameRandom;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicitly enabled, listener-free workload over production combat, scheduler,
 * NPC processing, player update, and plugin-dispatch paths.
 *
 * <p>The fixture owns setup only. Damage, retaliation, event cadence, plugin
 * execution, and tick ordering remain the production implementations.</p>
 */
public final class ActiveCombatBenchmark {
	private static final int NPC_ID = NpcId.GIANT.id();
	private static final int COMBAT_LEVEL = 60;
	private static final int SURVIVAL_HITS = 10_000;
	private static final int TILE_SPACING = 4;
	private static final int INTERACTION_PLAYERS_PER_TICK_DIVISOR = 16;

	private final Server server;
	private final int requestedPairs;
	private final int clientVersion;
	private final List<Pair> pairs = new ArrayList<Pair>();
	private long pluginDispatches;
	private int nextInteractionPlayer;
	private int backgroundNpcsRemoved;

	public ActiveCombatBenchmark(final Server server, final int requestedPairs,
			final int clientVersion) {
		if (server == null || requestedPairs <= 0) {
			throw new IllegalArgumentException(
				"active combat benchmark requires a server and positive pair count");
		}
		this.server = server;
		this.requestedPairs = requestedPairs;
		this.clientVersion = clientVersion;
	}

	public void initialize() {
		isolateBenchmarkNpcCohort();
		// World population has completed. From this point onward, keep legacy
		// random consumers (not yet migrated to GameRandom) replayable too.
		DataConversions.getRandom().setSeed(
			((DeterministicGameRandom)server.getCombatRandom()).getSeed()
				^ 0x6C65676163794C52L);
		final ClientLimitations limitations = ClientLimitations.forVersion(clientVersion);
		final int columns = Math.max(1,
			(int)Math.ceil(Math.sqrt(requestedPairs)));
		final int baseX = server.getConfig().RESPAWN_LOCATION_X + 24;
		final int baseY = server.getConfig().RESPAWN_LOCATION_Y + 24;

		for (int index = 0; index < requestedPairs; index++) {
			final int playerX = baseX + (index % columns) * TILE_SPACING;
			final int playerY = baseY + (index / columns) * TILE_SPACING;
			final int npcX = playerX + 1;
			openTile(playerX, playerY);
			openTile(npcX, playerY);

			final Player player = new Player(server.getWorld(),
				DataConversions.usernameToHash("cb" + index));
			player.setAttribute("dummyplayer", true);
			player.setAttribute("benchmark_active_combat_player", true);
			player.setClientVersion(clientVersion);
			player.setClientLimitations(limitations);
			player.setMale((index & 1) == 0);
			player.getSettings().setAppearance(new PlayerAppearance(
				index % 10, (index * 2) % 15, (index * 3) % 15,
				index % 5, 1, 2));
			player.setInitialLocation(Point.location(playerX, playerY));
			setSurvivalCombatStats(player);
			server.getWorld().getPlayers().add(player);
			player.updateRegion();
			player.setBusy(false);
			player.setLoggedIn(true);

			final Npc npc = new Npc(server.getWorld(), NPC_ID,
				npcX, playerY, 0);
			npc.setAttribute("benchmark_active_combat_npc", true);
			npc.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), SURVIVAL_HITS, SURVIVAL_HITS, false);
			server.getWorld().registerNpc(npc);
			pairs.add(new Pair(player, npc));
			requestMeleeAttack(player, npc);
		}

		server.getGameEventHandler().add(new DriverEvent());
	}

	private void isolateBenchmarkNpcCohort() {
		final List<Npc> backgroundNpcs =
			new ArrayList<Npc>(server.getWorld().getNpcs());
		for (final Npc npc : backgroundNpcs) {
			server.getWorld().unregisterNpc(npc);
		}
		backgroundNpcsRemoved = backgroundNpcs.size();
	}

	private void requestMeleeAttack(final Player player, final Npc npc) {
		final AttackIntent intent = player.getAttackTransaction().issue(
			npc, CombatStyle.MELEE,
			AttackIntent.Channel.MELEE, AttackIntent.Source.MANUAL, null);
		final AttackTransactionResult result = player.getAttackTransaction()
			.commit(intent, new PlayerAttackTransaction.CommitAction() {
				@Override
				public boolean commit() {
					player.startCombat(npc);
					return player.getPvmMeleeEvent() != null
						&& player.getPvmMeleeEvent().isRunning()
						&& player.getPvmMeleeEvent().getTarget() == npc;
				}
			});
		if (!result.isCommitted()) {
			throw new IllegalStateException(
				"Benchmark combat transaction did not commit for "
					+ player.getUsername() + ": " + result.getStatus()
					+ "/" + result.getReason() + "/"
					+ result.getEligibilityReason());
		}
	}

	private void dispatchRepresentativePluginInteractions() {
		if (server.getCurrentTick() < 2 || pairs.isEmpty()) {
			return;
		}
		final int dispatchCount = Math.max(1,
			(int)Math.ceil(pairs.size()
				/ (double)INTERACTION_PLAYERS_PER_TICK_DIVISOR));
		for (int count = 0; count < dispatchCount; count++) {
			final Player player = pairs.get(nextInteractionPlayer).player;
			nextInteractionPlayer = (nextInteractionPlayer + 1) % pairs.size();
			server.getPluginHandler().handlePlugin(TimedEventTrigger.class,
				player, new Object[] {player});
			pluginDispatches++;
		}
	}

	private void openTile(final int x, final int y) {
		server.getWorld().getTile(x, y).traversalMask = 0;
		server.getWorld().getTile(x, y).projectileAllowed = true;
		server.getWorld().getTile(x, y).originalProjectileAllowed = true;
	}

	private static void setSurvivalCombatStats(final Player player) {
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.MELEE.id(), COMBAT_LEVEL, COMBAT_LEVEL, false);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.RANGED.id(), COMBAT_LEVEL, COMBAT_LEVEL, false);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.MAGIC.id(), COMBAT_LEVEL, COMBAT_LEVEL, false);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), SURVIVAL_HITS, SURVIVAL_HITS, false);
	}

	public String buildSummary() {
		long playerHits = 0L;
		long npcHits = 0L;
		int engagedPairs = 0;
		int livePairs = 0;
		long outcomeHash = 17L;
		for (Pair pair : pairs) {
			final int currentPlayerHits = pair.player.getSkills()
				.getLevel(Skill.HITS.id());
			final int currentNpcHits = pair.npc.getSkills()
				.getLevel(Skill.HITS.id());
			playerHits += currentPlayerHits;
			npcHits += currentNpcHits;
			if (!pair.player.isRemoved() && !pair.npc.isRemoved()
					&& currentPlayerHits > 0 && currentNpcHits > 0) {
				livePairs++;
			}
			if (pair.player.getPvmMeleeEvent() != null
					&& pair.player.getPvmMeleeEvent().isRunning()
					&& pair.player.getPvmMeleeEvent().getTarget() == pair.npc) {
				engagedPairs++;
			}
			outcomeHash = outcomeHash * 31L + currentPlayerHits;
			outcomeHash = outcomeHash * 31L + currentNpcHits;
		}

		final String randomState = server.getCombatRandom()
			instanceof DeterministicGameRandom
			? ((DeterministicGameRandom)server.getCombatRandom()).describeState()
			: server.getCombatRandom().describeState();
		final long randomDraws = server.getCombatRandom()
			instanceof DeterministicGameRandom
			? ((DeterministicGameRandom)server.getCombatRandom()).getDrawCount()
			: -1L;
		final boolean valid = pairs.size() == requestedPairs
			&& livePairs == requestedPairs && engagedPairs == requestedPairs;
		final String deterministicSignature = pairs.size() + "-" + livePairs
			+ "-" + engagedPairs + "-" + pluginDispatches + "-"
			+ playerHits + "-" + npcHits + "-" + randomDraws;
		return " activeCombatPairs=" + pairs.size()
			+ " activeCombatBackgroundNpcsRemoved=" + backgroundNpcsRemoved
			+ " activeCombatLivePairs=" + livePairs
			+ " activeCombatEngagedPairs=" + engagedPairs
			+ " activeCombatPluginDispatches=" + pluginDispatches
			+ " activeCombatPlayerHits=" + playerHits
			+ " activeCombatNpcHits=" + npcHits
			+ " activeCombatDistributionHash=" + Long.toUnsignedString(outcomeHash)
			+ " activeCombatRandomDraws=" + randomDraws
			+ " activeCombatDeterminism=" + deterministicSignature
			+ " activeCombatRandom=" + randomState
			+ " activeCombatInvariant=" + (valid ? "pass" : "fail");
	}

	private final class DriverEvent extends GameTickEvent {
		private DriverEvent() {
			super(server.getWorld(), null, 1,
				"Active Combat Benchmark Driver", DuplicationStrategy.ONE_PER_SERVER);
		}

		@Override
		public void run() {
			dispatchRepresentativePluginInteractions();
		}
	}

	private static final class Pair {
		private final Player player;
		private final Npc npc;

		private Pair(final Player player, final Npc npc) {
			this.player = player;
			this.npc = npc;
		}
	}
}
