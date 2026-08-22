package com.openrsc.server.diagnostics;

import com.openrsc.server.Server;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.triggers.TimedEventTrigger;
import com.openrsc.server.runtime.DeterministicGameRandom;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Explicit listener-free workload for projectile, magic, and multi-target
 * combat. Setup is benchmark-owned; targeting, plugin checks, launch cadence,
 * projectile settlement, damage, and update serialization are production code.
 */
public final class ProjectileCombatBenchmark {
	private static final int NPC_ID = NpcId.GIANT.id();
	private static final int COMBAT_LEVEL = 80;
	private static final int SURVIVAL_HITS = 100_000;
	private static final int TILE_SPACING = 10;
	private static final int RESOURCE_COUNT = 10_000;
	private static final int INTERACTION_PLAYERS_PER_TICK_DIVISOR = 12;
	private static final Spells MAGIC_SPELL = Spells.WIND_STRIKE;

	private final Server server;
	private final int requestedGroups;
	private final int clientVersion;
	private final Family requestedFamily;
	private final List<Group> groups = new ArrayList<Group>();
	private long pluginDispatches;
	private int nextInteractionPlayer;
	private int backgroundNpcsRemoved;

	public ProjectileCombatBenchmark(final Server server,
			final int requestedGroups, final int clientVersion,
			final String family) {
		if (server == null || requestedGroups < 3) {
			throw new IllegalArgumentException(
				"projectile combat benchmark requires a server and at least three groups");
		}
		this.server = server;
		this.requestedGroups = requestedGroups;
		this.clientVersion = clientVersion;
		this.requestedFamily = Family.fromProperty(family);
	}

	public void initialize() {
		isolateBenchmarkNpcCohort();
		DataConversions.getRandom().setSeed(
			((DeterministicGameRandom)server.getCombatRandom()).getSeed()
				^ 0x50726F6A6563744CL);
		final ClientLimitations limitations = ClientLimitations.forVersion(clientVersion);
		// Custom clients advertise the live definition ceiling through their
		// handshake in production. Synthetic players have no handshake, so publish
		// only the item ceiling needed for their benchmark-owned loadouts.
		limitations.maxItemId = server.getEntityHandler().getItemCount() - 1;
		final int columns = Math.max(1,
			(int)Math.ceil(Math.sqrt(requestedGroups)));
		final int baseX = server.getConfig().RESPAWN_LOCATION_X + 24;
		final int baseY = server.getConfig().RESPAWN_LOCATION_Y + 24;

		for (int index = 0; index < requestedGroups; index++) {
			final Family family = requestedFamily;
			final int playerX = baseX + (index % columns) * TILE_SPACING;
			final int playerY = baseY + (index / columns) * TILE_SPACING;
			final Player player = createPlayer(index, playerX, playerY, limitations);
			final List<Npc> targets = createTargets(family, playerX, playerY);
			final Group group = new Group(family, player, targets);
			groups.add(group);
			startCombat(group);
		}

		server.getGameEventHandler().add(new DriverEvent());
	}

	private Player createPlayer(final int index, final int x, final int y,
			final ClientLimitations limitations) {
		openTile(x, y);
		final Player player = new Player(server.getWorld(),
			DataConversions.usernameToHash("pcb" + index));
		player.setAttribute("dummyplayer", true);
		player.setAttribute("benchmark_projectile_combat_player", true);
		player.setClientVersion(clientVersion);
		player.setClientLimitations(limitations);
		player.setMale((index & 1) == 0);
		player.getSettings().setAppearance(new PlayerAppearance(
			index % 10, (index * 2) % 15, (index * 3) % 15,
			index % 5, 1, 2));
		player.setInitialLocation(Point.location(x, y));
		setSurvivalCombatStats(player);
		server.getWorld().getPlayers().add(player);
		player.updateRegion();
		player.setBusy(false);
		player.setLoggedIn(true);
		return player;
	}

	private List<Npc> createTargets(final Family family,
			final int playerX, final int playerY) {
		final int targetCount = family == Family.MULTI_TARGET ? 3 : 1;
		final List<Npc> targets = new ArrayList<Npc>(targetCount);
		for (int targetIndex = 0; targetIndex < targetCount; targetIndex++) {
			final int x = playerX + (targetIndex == 2 ? 0 : 1);
			final int y = playerY + (targetIndex == 0 ? 0 : 1);
			openTile(x, y);
			final Npc npc = new PassiveProjectileTarget(
				server, NPC_ID, x, y);
			npc.setAttribute("benchmark_projectile_combat_npc", true);
			npc.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), SURVIVAL_HITS, SURVIVAL_HITS, false);
			server.getWorld().registerNpc(npc);
			targets.add(npc);
		}
		return targets;
	}

	private void startCombat(final Group group) {
		switch (group.family) {
			case RANGED:
				equip(group.player, ItemId.SHORTBOW.id(), 1);
				equip(group.player, ItemId.TIN_ARROWS.id(), RESOURCE_COUNT);
				final RangeEvent range = new RangeEvent(server.getWorld(),
					group.player, 1L, group.primary());
				group.player.setRangeEvent(range);
				server.getGameEventHandler().add(range);
				break;
			case MAGIC:
				addSpellResources(group.player, MAGIC_SPELL);
				group.player.setAutoCastSpell(MAGIC_SPELL);
				if (!MagicCombatEvent.start(group.player, group.primary())) {
					throw new IllegalStateException("Could not start benchmark magic combat");
				}
				break;
			case MULTI_TARGET:
				equip(group.player, ItemId.TIN_SHURIKEN.id(), RESOURCE_COUNT);
				final ThrowingEvent throwing = new ThrowingEvent(server.getWorld(),
					group.player, 1L, group.primary());
				group.player.setThrowingEvent(throwing);
				server.getGameEventHandler().add(throwing);
				break;
			default:
				throw new IllegalStateException("Unhandled benchmark family " + group.family);
		}
	}

	private void equip(final Player player, final int itemId, final int amount) {
		final Item item = new Item(itemId, amount);
		item.setWielded(true);
		if (player.getCarriedItems().getEquipment().add(item) < 0) {
			throw new IllegalStateException("Could not equip benchmark item " + itemId);
		}
	}

	private void addSpellResources(final Player player, final Spells spell) {
		final SpellDef spellDef = server.getEntityHandler().getSpellDef(spell);
		if (spellDef == null) {
			throw new IllegalStateException("Missing benchmark spell definition " + spell);
		}
		for (Map.Entry<Integer, Integer> rune : spellDef.getRunesRequired()) {
			if (!player.getCarriedItems().getInventory().add(
					new Item(rune.getKey(), RESOURCE_COUNT), false)) {
				throw new IllegalStateException(
					"Could not add benchmark rune " + rune.getKey());
			}
		}
	}

	private void isolateBenchmarkNpcCohort() {
		final List<Npc> backgroundNpcs =
			new ArrayList<Npc>(server.getWorld().getNpcs());
		for (final Npc npc : backgroundNpcs) {
			server.getWorld().unregisterNpc(npc);
		}
		backgroundNpcsRemoved = backgroundNpcs.size();
	}

	private void dispatchRepresentativePluginInteractions() {
		if (server.getCurrentTick() < 2 || groups.isEmpty()) return;
		final int dispatchCount = Math.max(1,
			(int)Math.ceil(groups.size()
				/ (double)INTERACTION_PLAYERS_PER_TICK_DIVISOR));
		for (int count = 0; count < dispatchCount; count++) {
			final Player player = groups.get(nextInteractionPlayer).player;
			nextInteractionPlayer = (nextInteractionPlayer + 1) % groups.size();
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
		int rangedGroups = 0;
		int magicGroups = 0;
		int multiGroups = 0;
		int livePlayers = 0;
		int liveTargets = 0;
		int activeGroups = 0;
		long playerHits = 0L;
		long targetHits = 0L;
		long outcomeHash = 17L;
		for (final Group group : groups) {
			switch (group.family) {
				case RANGED: rangedGroups++; break;
				case MAGIC: magicGroups++; break;
				case MULTI_TARGET: multiGroups++; break;
				default: break;
			}
			final int currentPlayerHits = group.player.getSkills()
				.getLevel(Skill.HITS.id());
			playerHits += currentPlayerHits;
			if (!group.player.isRemoved() && currentPlayerHits > 0) livePlayers++;
			if (group.isActive()) activeGroups++;
			outcomeHash = outcomeHash * 31L + currentPlayerHits;
			for (final Npc target : group.targets) {
				final int currentTargetHits = target.getSkills()
					.getLevel(Skill.HITS.id());
				targetHits += currentTargetHits;
				if (!target.isRemoved() && currentTargetHits > 0) liveTargets++;
				outcomeHash = outcomeHash * 31L + currentTargetHits;
			}
		}
		final long randomDraws = server.getCombatRandom()
			instanceof DeterministicGameRandom
			? ((DeterministicGameRandom)server.getCombatRandom()).getDrawCount() : -1L;
		final int expectedTargets = rangedGroups + magicGroups + multiGroups * 3;
		final boolean familyCountsValid =
			(requestedFamily == Family.RANGED && rangedGroups == requestedGroups)
			|| (requestedFamily == Family.MAGIC && magicGroups == requestedGroups)
			|| (requestedFamily == Family.MULTI_TARGET
				&& multiGroups == requestedGroups);
		final boolean valid = groups.size() == requestedGroups
			&& familyCountsValid
			&& livePlayers == requestedGroups && liveTargets == expectedTargets
			&& activeGroups == requestedGroups;
		final String signature = requestedFamily.propertyValue + "-"
			+ groups.size() + "-" + rangedGroups + "-"
			+ magicGroups + "-" + multiGroups + "-" + livePlayers + "-"
			+ liveTargets + "-" + activeGroups + "-" + pluginDispatches + "-"
			+ playerHits + "-" + targetHits + "-" + randomDraws;
		return " projectileCombatGroups=" + groups.size()
			+ " projectileCombatFamily=" + requestedFamily.propertyValue
			+ " projectileCombatRangedGroups=" + rangedGroups
			+ " projectileCombatMagicGroups=" + magicGroups
			+ " projectileCombatMultiGroups=" + multiGroups
			+ " projectileCombatBackgroundNpcsRemoved=" + backgroundNpcsRemoved
			+ " projectileCombatLivePlayers=" + livePlayers
			+ " projectileCombatLiveTargets=" + liveTargets
			+ " projectileCombatActiveGroups=" + activeGroups
			+ " projectileCombatPluginDispatches=" + pluginDispatches
			+ " projectileCombatPlayerHits=" + playerHits
			+ " projectileCombatTargetHits=" + targetHits
			+ " projectileCombatDistributionHash="
				+ Long.toUnsignedString(outcomeHash)
			+ " projectileCombatRandomDraws=" + randomDraws
			+ " projectileCombatDeterminism=" + signature
			+ " projectileCombatInvariant=" + (valid ? "pass" : "fail");
	}

	private final class DriverEvent extends GameTickEvent {
		private DriverEvent() {
			super(server.getWorld(), null, 1,
				"Projectile Combat Benchmark Driver",
				DuplicationStrategy.ONE_PER_SERVER);
		}

		@Override
		public void run() {
			dispatchRepresentativePluginInteractions();
		}
	}

	private enum Family {
		RANGED("ranged"),
		MAGIC("magic"),
		MULTI_TARGET("multi");

		private final String propertyValue;

		Family(final String propertyValue) {
			this.propertyValue = propertyValue;
		}

		private static Family fromProperty(final String value) {
			for (final Family family : values()) {
				if (family.propertyValue.equalsIgnoreCase(value)) return family;
			}
			throw new IllegalArgumentException(
				"Unknown projectile benchmark family " + value);
		}
	}

	private static final class Group {
		private final Family family;
		private final Player player;
		private final List<Npc> targets;

		private Group(final Family family, final Player player,
				final List<Npc> targets) {
			this.family = family;
			this.player = player;
			this.targets = targets;
		}

		private Npc primary() {
			return targets.get(0);
		}

		private boolean isActive() {
			switch (family) {
				case RANGED:
					return player.getRangeEvent() != null
						&& player.getRangeEvent().isRunning();
				case MAGIC:
					return player.getMagicCombatEvent() != null
						&& player.getMagicCombatEvent().isRunning();
				case MULTI_TARGET:
					return player.getThrowingEvent() != null
						&& player.getThrowingEvent().isRunning();
				default:
					return false;
			}
		}
	}

	/** Keeps this fixture from silently becoming a fourth, melee workload. */
	private static final class PassiveProjectileTarget extends Npc {
		private PassiveProjectileTarget(final Server server, final int id,
				final int x, final int y) {
			super(server.getWorld(), id, x, y, 0);
		}

		@Override
		public void startPvmCounterCombat(final Mob attacker) {
			// Projectile damage, threat, effects, and targeting remain production;
			// only unrelated counter-melee scheduling is excluded.
		}

		@Override
		public void setChasing(final Player player) {
			// Projectile impacts normally transition a surviving target toward
			// counter-melee; the isolated workload deliberately ends at threat state.
		}
	}
}
