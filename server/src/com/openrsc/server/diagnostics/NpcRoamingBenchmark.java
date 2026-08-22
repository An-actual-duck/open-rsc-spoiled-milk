package com.openrsc.server.diagnostics;

import com.openrsc.server.Server;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.runtime.DeterministicGameRandom;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Listener-free deterministic workload over production NPC roaming, collision,
 * layered player discovery, movement, and client publication paths.
 */
public final class NpcRoamingBenchmark {
	private static final int BLOCKING_SCENERY_ID = 0;
	private static final int CLUSTER_SPACING = 40;
	private static final int NPC_SPACING = 3;

	private final Server server;
	private final int requestedNpcs;
	private final int clientVersion;
	private final List<CohortNpc> npcs = new ArrayList<CohortNpc>();
	private final List<Player> players = new ArrayList<Player>();
	private int backgroundNpcsRemoved;
	private int sceneryCreated;
	private long cadencePreparations;

	public NpcRoamingBenchmark(final Server server, final int requestedNpcs,
			final int clientVersion) {
		if (server == null || requestedNpcs < 12 || requestedNpcs % 4 != 0) {
			throw new IllegalArgumentException(
				"NPC roaming benchmark requires a server and a positive multiple of four >= 12");
		}
		this.server = server;
		this.requestedNpcs = requestedNpcs;
		this.clientVersion = clientVersion;
	}

	public void initialize() {
		isolateBenchmarkNpcCohort();
		DataConversions.getRandom().setSeed(
			((DeterministicGameRandom)server.getCombatRandom()).getSeed()
				^ 0x524F414D4E50434CL);
		final int baseX = server.getConfig().RESPAWN_LOCATION_X + 20;
		final int baseY = server.getConfig().RESPAWN_LOCATION_Y - 20;
		final int perCohort = requestedNpcs / 4;
		for (int cohort = 0; cohort < 4; cohort++) {
			final int clusterX = baseX + (cohort % 2) * CLUSTER_SPACING;
			final int clusterY = baseY + (cohort / 2) * CLUSTER_SPACING;
			createPlayers(cohort, clusterX, clusterY);
			for (int index = 0; index < perCohort; index++) {
				final int x = clusterX + (index % 4) * NPC_SPACING;
				final int y = clusterY + (index / 4) * NPC_SPACING;
				openArea(x, y);
				final Npc npc = new Npc(server.getWorld(), NpcId.MAN.id(),
					x, y, 1);
				npc.setAttribute("benchmark_npc_roaming", true);
				server.getWorld().registerNpc(npc);
				npcs.add(new CohortNpc(npc, cohort != 1));
				if (cohort == 3) {
					addBlockingScenery(x + 1, y);
				}
			}
		}
		server.getGameEventHandler().add(new DriverEvent());
	}

	private void isolateBenchmarkNpcCohort() {
		final List<Npc> background = new ArrayList<Npc>(server.getWorld().getNpcs());
		for (Npc npc : background) server.getWorld().unregisterNpc(npc);
		backgroundNpcsRemoved = background.size();
	}

	private void createPlayers(final int cohort, final int x, final int y) {
		// Cohort 2 proves same-coordinate players on another layer are ignored.
		final int count = cohort == 0 ? 8 : 1;
		final int level = cohort == 2 ? -1 : 0;
		for (int index = 0; index < count; index++) {
			final int px = x + (index % 4);
			final int py = y + 6 + (index / 4);
			final Player player = new Player(server.getWorld(),
				DataConversions.usernameToHash("roam" + cohort + "p" + index));
			player.setAttribute("dummyplayer", true);
			player.setAttribute("benchmark_npc_roaming_player", true);
			player.setClientVersion(clientVersion);
			player.setClientLimitations(ClientLimitations.forVersion(clientVersion));
			player.setMale((index & 1) == 0);
			player.getSettings().setAppearance(new PlayerAppearance(
				index % 10, index % 15, (index * 3) % 15, index % 5, 1, 2));
			final WorldLocation location = WorldLocation.global(
				new WorldCoordinate(px, py, level));
			player.setInitialLayeredLocation(location);
			server.getWorld().getPlayers().add(player);
			player.updateRegion();
			player.setBusy(false);
			player.setLoggedIn(true);
			players.add(player);
		}
	}

	private void openArea(final int x, final int y) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				server.getWorld().getTile(x + dx, y + dy).traversalMask = 0;
			}
		}
	}

	private void addBlockingScenery(final int x, final int y) {
		server.getWorld().registerGameObject(new GameObject(
			server.getWorld(), Point.location(x, y), BLOCKING_SCENERY_ID, 0, 0));
		sceneryCreated++;
	}

	private void prepareCadence() {
		for (CohortNpc entry : npcs) {
			entry.npc.getBehavior().prepareBenchmarkRoamCadence(entry.due);
			cadencePreparations++;
		}
	}

	public String buildSummary() {
		int due = 0;
		int notDue = 0;
		int moved = 0;
		int correctLevel = 0;
		long locationHash = 17L;
		final List<Long> locations = new ArrayList<Long>();
		for (CohortNpc entry : npcs) {
			if (entry.due) due++; else notDue++;
			if (entry.npc.hasMoved()) moved++;
			if (entry.npc.getWorldLocation().getCoordinate().getLevel() == 0) correctLevel++;
			locations.add(((long)entry.npc.getX() << 32)
				^ (entry.npc.getY() & 0xffffffffL));
		}
		Collections.sort(locations);
		for (Long location : locations) locationHash = locationHash * 31 + location;
		final long randomDraws = ((DeterministicGameRandom)server.getCombatRandom())
			.getDrawCount();
		final boolean valid = npcs.size() == requestedNpcs
			&& due == requestedNpcs * 3 / 4
			&& notDue == requestedNpcs / 4
			&& correctLevel == requestedNpcs
			&& sceneryCreated == requestedNpcs / 4;
		final String signature = npcs.size() + "-" + players.size() + "-"
			+ due + "-" + notDue + "-" + sceneryCreated + "-"
			+ cadencePreparations + "-" + randomDraws;
		return " npcRoamingNpcs=" + npcs.size()
			+ " npcRoamingPlayers=" + players.size()
			+ " npcRoamingDue=" + due
			+ " npcRoamingNotDue=" + notDue
			+ " npcRoamingMovedAtEnd=" + moved
			+ " npcRoamingScenery=" + sceneryCreated
			+ " npcRoamingBackgroundNpcsRemoved=" + backgroundNpcsRemoved
			+ " npcRoamingLocationHash=" + Long.toUnsignedString(locationHash)
			+ " npcRoamingRandomDraws=" + randomDraws
			+ " npcRoamingDeterminism=" + signature
			+ " npcRoamingInvariant=" + (valid ? "pass" : "fail");
	}

	private final class DriverEvent extends GameTickEvent {
		private DriverEvent() {
			super(server.getWorld(), null, 0, "NPC Roaming Benchmark Driver",
				DuplicationStrategy.ONE_PER_SERVER);
		}

		@Override
		public void run() {
			prepareCadence();
		}
	}

	private static final class CohortNpc {
		private final Npc npc;
		private final boolean due;

		private CohortNpc(final Npc npc, final boolean due) {
			this.npc = npc;
			this.due = due;
		}
	}
}
