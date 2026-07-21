package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.Server;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.event.rsc.ImmediateEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AuthoredConstructionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AuthoredPlacementRestorationState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.PackedSource;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.SpatialReference;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.SpatialRole;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementProposal;
import com.openrsc.server.util.NamedThreadFactory;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class GameEventHandler {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final GameTickEventStore eventStore = new GameTickEventStore();
	private final ConcurrentHashMap<String, Integer> eventsCounts = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> eventsDurations = new ConcurrentHashMap<>();
	private final Server server;
	private ThreadPoolExecutor executor;

	public GameEventHandler(final Server server) {
		this.server = server;
	}

	public void load() {
		if (shouldExecuteDirectly()) {
			return;
		}

		final int maxThreads;
		if (getServer().getConfig().WANT_THREADING__BREAK_PID_PRIORITY) {
			// can be slightly faster if we don't care which order events are done in (you always should care!)
			// TODO: currently also causes issues with scenery breaking from having two players accessing it
			maxThreads = (Runtime.getRuntime().availableProcessors() * 2) / (Server.serversList.size() > 0 ? Server.serversList.size() : 1);
		} else {
			// single thread events so that PID order is always respected.
			maxThreads = 1;
		}
		executor = new ThreadPoolExecutor(Math.max(1, maxThreads / 2), maxThreads, Long.MAX_VALUE, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory(getServer().getName() + " : EventHandler", getServer().getConfig()));
		executor.prestartAllCoreThreads();
	}

	public final Server getServer() {
		return server;
	}

	public void unload() {
		// Process any events still in the queue.
		processEvents();

		if (executor != null) {
			executor.shutdown();
			try {
				final boolean terminationResult = executor.awaitTermination(1, TimeUnit.MINUTES);
				if (!terminationResult) {
					LOGGER.error("GameEventHandler thread pool termination failed");
				}
			} catch (final InterruptedException e) {
				LOGGER.error("GameEventHandler thread pool termination interrupted", e);
			}
		}

		cleanupEvents();
	}

	private void processEvents() {
		processNonPlayerEvents();
		getServer().getWorld().getPlayers().forEach(this::runPlayerEvents);
	}

	public void cleanupEvents() {
		eventStore.getTrackedEvents().forEach(event -> {
			incrementCounts(event);
			if (event.shouldRemove()) {
				eventStore.remove(event);
			}
		});
		eventsCounts.clear();
		eventsDurations.clear();
	}

	public long processNonPlayerEvents() {
		return getServer().bench(() -> {
			if (shouldExecuteDirectly()) {
				executeDirectly(eventStore.getNonPlayerEvents(), "processNonPlayerEvents()");
				return;
			}

			try {
				executor.invokeAll(eventStore.getNonPlayerEvents());
			} catch (final Exception e) {
				LOGGER.error("Exception while executing GameEventHandler processNonPlayerEvents()", e);
			}
		});
	}

	public long runPlayerEvents(final Player player) {
		return getServer().bench(() -> processEvents(player));
	}

	private void incrementCounts(GameTickEvent event) {
		eventsCounts.put(event.getDescriptor(),
			eventsCounts.containsKey(event.getDescriptor()) ?
				eventsCounts.get(event.getDescriptor()) + 1 :
				1);
		eventsDurations.put(event.getDescriptor(),
			eventsDurations.containsKey(event.getDescriptor()) ?
				eventsDurations.get(event.getDescriptor()) + event.getLastEventDuration() :
				event.getLastEventDuration());
	}

	public void processEvents(final Player player) {
		if (shouldExecuteDirectly()) {
			executeDirectly(eventStore.getPlayerEvents(player.getUsernameHash()), "processEvents(" + player.getUsername() + ")");
			return;
		}

		try {
			executor.invokeAll(eventStore.getPlayerEvents(player.getUsernameHash()));
		} catch (final Exception e) {
			LOGGER.error("Exception while executing GameEventHandler processEvents()", e);
		}
	}

	private boolean shouldExecuteDirectly() {
		return !getServer().getConfig().WANT_THREADING__BREAK_PID_PRIORITY;
	}

	private void executeDirectly(final Collection<GameTickEvent> events, final String context) {
		for (final GameTickEvent event : events) {
			try {
				event.call();
			} catch (final Exception e) {
				LOGGER.error("Exception while executing GameEventHandler " + context, e);
			}
		}
	}

	public void submit(final Runnable r, final String descriptor) {
		add(new ImmediateEvent(getServer().getWorld(), descriptor) {
			@Override
			public void action() {
				try {
					r.run();
				} catch (final Throwable e) {
					LOGGER.error("Exception while executing GameEventHandler submit()", e);
				}
			}
		});
	}

	public boolean add(final GameTickEvent event) {
		return eventStore.add(event);
	}

	public boolean addOrUpdate(final GameTickEvent event) {
		return eventStore.addOrUpdate(event);
	}

	public boolean has(final GameTickEvent event) {
		return eventStore.eventIsContained(event);
	}

	public final String buildProfilingDebugInformation(final boolean forInGame) {
		int countAllEvents = 0;
		long durationAllEvents = 0;
		String newLine = forInGame ? "%" : "\r\n";

		final HashMap<String, Integer> eventsCounts = getEventsCounts();
		final HashMap<String, Long> eventsDurations = getEventsDurations();

		// Calculate Totals
		for (Map.Entry<String, Integer> eventEntry : eventsCounts.entrySet())
			countAllEvents += eventEntry.getValue();
		//for (Map.Entry<String, Long> eventEntry : eventsDurations.entrySet())
		//	durationAllEvents += eventEntry.getValue();

		// Sort the Events Hashmap
		List<Map.Entry<String, Long>> mapEntries = new LinkedList<>(eventsDurations.entrySet());
		mapEntries.sort((prev, next) -> {
			long prevDuration = eventsDurations.get(prev.getKey());
			long nextDuration = eventsDurations.get(next.getKey());

			if (prevDuration == nextDuration) {
				int prevCount = eventsCounts.get(prev.getKey());
				int nextCount = eventsCounts.get(next.getKey());

				if (prevCount == nextCount)
					return 0;
				return prevCount < nextCount ? 1 : -1;
			}
			return prevDuration < nextDuration ? 1 : -1;
		});
		eventsDurations.clear();
		//HashMap<String, Long> sortedHashMap = new LinkedHashMap<>();
		for (Map.Entry<String, Long> entry : mapEntries)
			eventsDurations.put(entry.getKey(), entry.getValue());
		//eventsDurations.clear();
		//eventsDurations.putAll(sortedHashMap);

		StringBuilder s = new StringBuilder();
		int idx = 0;
		if (!forInGame) {
			s.append("========================").append(newLine);
			s.append("===     Events       ===").append(newLine);
			s.append("========================").append(newLine);
		}
		for (Map.Entry<String, Long> entry : eventsDurations.entrySet()) {
			// Only display first few elements of the hashmap
			if (forInGame && idx++ >= 15) {
				break;
			}
			final String eventName = entry.getKey();
			final long eventTime = entry.getValue();
			final int eventCount = eventsCounts.get(entry.getKey());
			s.append(eventName).append(" : ")
				.append(eventTime / 1000000).append("ms").append(" : ")
				.append(eventTime / 1000).append("us").append(" : ")
				.append(eventCount).append(newLine);
		}

		if (!forInGame) {
			s.append("========================").append(newLine);
			s.append("=== Incoming Packets ===").append(newLine);
			s.append("========================").append(newLine);
			for (Map.Entry<Integer, Integer> entry : getServer().getIncomingCountPerPacketOpcode().entrySet()) {
				final int incomingPacketId = entry.getKey();
				final int incomingCount = entry.getValue();
				final long incomingTime = getServer().getIncomingTimePerPacketOpcode().get(incomingPacketId);
				s.append("Packet ID: ").append(incomingPacketId).append(" : ")
					.append(incomingTime / 1000000).append("ms").append(" : ")
					.append(incomingTime / 1000).append("us").append(" : ")
					.append(incomingCount).append(newLine);
			}
			s.append("========================").append(newLine);
			s.append("=== Outgoing Packets ===").append(newLine);
			s.append("========================").append(newLine);
			for (Map.Entry<Integer, Integer> entry : getServer().getOutgoingCountPerPacketOpcode().entrySet()) {
				final int outgoingPacketId = entry.getKey();
				final int outgoingCount = entry.getValue();
				final long outgoingTime = getServer().getOutgoingTimePerPacketOpcode().get(outgoingPacketId);
				final long outgoingPayloadBytes = getServer().getOutgoingPayloadBytesPerPacketOpcode()
					.getOrDefault(outgoingPacketId, 0L);
				s.append("Packet ID: ").append(outgoingPacketId).append(" : ")
					.append(outgoingTime / 1000000).append("ms").append(" : ")
					.append(outgoingTime / 1000).append("us").append(" : ")
					.append(outgoingCount).append(" : ")
					.append(outgoingPayloadBytes).append(" payload bytes").append(newLine);
			}
			final long visibilitySamples = Math.max(1L, getServer().getLastVisibilitySnapshotSamples());
			s.append("========================").append(newLine);
			s.append("=== Visibility Snapshot ===").append(newLine);
			s.append("========================").append(newLine);
			s.append("Time: ")
				.append(getServer().getLastVisibilitySnapshotDuration() / 1000000).append("ms").append(" : ")
				.append(getServer().getLastVisibilitySnapshotDuration() / 1000).append("us").append(newLine);
			s.append("Average visible: players=")
				.append(getServer().getLastVisiblePlayersTotal() / visibilitySamples)
				.append(", npcs=").append(getServer().getLastVisibleNpcsTotal() / visibilitySamples)
				.append(", scenery=").append(getServer().getLastVisibleSceneryTotal() / visibilitySamples)
				.append(", walls=").append(getServer().getLastVisibleWallObjectsTotal() / visibilitySamples)
				.append(", groundItems=").append(getServer().getLastVisibleGroundItemsTotal() / visibilitySamples)
				.append(newLine);
			s.append("Max visible: players=")
				.append(getServer().getLastVisiblePlayersMax())
				.append(", npcs=").append(getServer().getLastVisibleNpcsMax())
				.append(", scenery=").append(getServer().getLastVisibleSceneryMax())
				.append(", walls=").append(getServer().getLastVisibleWallObjectsMax())
				.append(", groundItems=").append(getServer().getLastVisibleGroundItemsMax())
				.append(newLine);
			s.append("Cache: region requests=")
				.append(getServer().getLastVisibilityRegionCacheRequests())
				.append(", hits=").append(getServer().getLastVisibilityRegionCacheHits())
				.append(", misses=").append(getServer().getLastVisibilityRegionCacheMisses())
				.append("; object requests=").append(getServer().getLastVisibilityObjectCacheRequests())
				.append(", hits=").append(getServer().getLastVisibilityObjectCacheHits())
				.append(", misses=").append(getServer().getLastVisibilityObjectCacheMisses())
				.append(", clears=").append(getServer().getLastVisibilityObjectCacheClears())
				.append(", entriesCleared=").append(getServer().getLastVisibilityObjectCacheEntriesCleared())
				.append("; objectSnapshot requests=").append(getServer().getLastVisibilityObjectSnapshotCacheRequests())
				.append(", hits=").append(getServer().getLastVisibilityObjectSnapshotCacheHits())
				.append(", misses=").append(getServer().getLastVisibilityObjectSnapshotCacheMisses())
				.append("; tickSnapshot requests=").append(getServer().getLastVisibilityTickSnapshotCacheRequests())
				.append(", hits=").append(getServer().getLastVisibilityTickSnapshotCacheHits())
				.append(", misses=").append(getServer().getLastVisibilityTickSnapshotCacheMisses())
				.append(newLine);
			s.append("Shadow snapshot: time=")
				.append(getServer().getLastVisibilityShadowDuration() / 1000000).append("ms")
				.append(", samples=").append(getServer().getLastVisibilityShadowSamples())
				.append(", mismatches=").append(getServer().getLastVisibilityShadowMismatchSamples())
				.append(", players=").append(getServer().getLastVisibilityShadowPlayerMismatches())
				.append(", npcs=").append(getServer().getLastVisibilityShadowNpcMismatches())
				.append(", objects=").append(getServer().getLastVisibilityShadowGameObjectMismatches())
				.append(", groundItems=").append(getServer().getLastVisibilityShadowGroundItemMismatches())
				.append(", maxMobRegions=").append(getServer().getLastVisibilityShadowMobRegionsMax())
				.append(", maxObjectRegions=").append(getServer().getLastVisibilityShadowObjectRegionsMax())
				.append(newLine);
		}

		final boolean forcedGc = getServer().getConfig().WANT_FORCE_GC_ON_PROFILING;
		if (forcedGc) {
			System.gc();
		}
		final String totalMemory = DataConversions.formatBytes(Runtime.getRuntime().totalMemory());
		final String freeMemory = DataConversions.formatBytes(Runtime.getRuntime().freeMemory());
		final String usedMemory = DataConversions.formatBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
		final String memoryMode = forcedGc ? " after forced GC" : " live";

		final String returnString = (
				"Tick: " + getServer().getConfig().GAME_TICK + "ms, Server: " + (getServer().getLastTickDuration() / 1000000) + "ms " + (getServer().getLastIncomingPacketsDuration() / 1000000) + "ms " + (getServer().getLastEventsDuration() / 1000000) + "ms " + (getServer().getLastOutgoingPacketsDuration() / 1000000) + "ms" + newLine +
				"Game Updater: " + (getServer().getLastWorldUpdateDuration() / 1000000) + "ms " + (getServer().getLastProcessPlayersDuration() / 1000000) + "ms " + (getServer().getLastProcessNpcsDuration() / 1000000) + "ms " + (getServer().getLastProcessMessageQueuesDuration() / 1000000) + "ms " + (getServer().getLastUpdateClientsDuration() / 1000000) + "ms " + (getServer().getLastDoCleanupDuration() / 1000000) + "ms " + (getServer().getLastExecuteWalkToActionsDuration() / 1000000) + "ms " + newLine +
				"NPC idle throttle skipped: " + getServer().getLastNpcIdleThrottleSkipped() + newLine +
				"Events: " + countAllEvents + ", NPCs: " + getServer().getWorld().getNpcs().size() + ", Players: " + getServer().getWorld().getPlayers().size() + ", Shops: " + getServer().getWorld().getShops().size() + newLine +
				"Threads: " + Thread.activeCount() + ", Memory" + memoryMode + ": Total: " + totalMemory + ", Free: " + freeMemory + ", Used: " + usedMemory + newLine +
				/*"Player Atk Map: " + getWorld().getPlayersUnderAttack().size() + ", NPC Atk Map: " + getWorld().getNpcsUnderAttack().size() + ", Quests: " + getWorld().getQuests().size() + ", Mini Games: " + getWorld().getMiniGames().size() + newLine +*/
				s.toString()
		);

		if (!forInGame) {
			LOGGER.info(returnString);
		}

		return returnString.substring(0, Math.min(returnString.length(), 1999)); // Limit to 2000 characters for Discord.
	}

	public HashMap<String, Integer> getEventsCounts() {
		return new LinkedHashMap<>(eventsCounts);
	}

	public HashMap<String, Long> getEventsDurations() {
		return new LinkedHashMap<>(eventsDurations);
	}

	public List<GameTickEvent> getEvents() {
		return new ArrayList<>(eventStore.getTrackedEvents());
	}

	/**
	 * Detaches one bounded, non-reflective scheduler snapshot for an exact
	 * refinement proposal. Legacy ownership is only a position hint; exact or
	 * global scope must be declared by the event implementation.
	 */
	public LayeredPackedRegionEventOwnershipInventory
		captureLayeredPackedRegionEventOwnershipInventory(
			final LayeredPackedRegionRetirementRefinementProposal proposal,
			final long observedAtTick,
			final int maximumEvents,
			final int maximumSpatialReferences) {
		LayeredPackedRegionRetirementRefinementProposal checked =
			Objects.requireNonNull(proposal, "proposal");
		if (observedAtTick < checked.getSafetyObservedAtTick()
			|| observedAtTick < checked.getCensusObservedAtTick()) {
			throw new IllegalArgumentException(
				"Event ownership snapshot is older than its proposal");
		}

		List<PackedSource> packedSources = new ArrayList<PackedSource>(
			checked.getCandidateSourceCount());
		for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
			source : checked.getCandidates()) {
			packedSources.add(PackedSource.of(
				source.getPackedRegionX(), source.getPackedRegionY()));
		}

		GameTickEventStore.RegistrationSnapshot registrationSnapshot =
			eventStore.getTrackedEventRegistrationSnapshot();
		List<GameTickEventStore.RegisteredEvent> liveRegistrations =
			registrationSnapshot.getRegistrations();
		if (maximumEvents < 0
			|| maximumEvents
				> LayeredPackedRegionEventOwnershipInventory.MAXIMUM_EVENTS
			|| liveRegistrations.size() > maximumEvents) {
			throw new IllegalArgumentException(
				"Event ownership snapshot exceeds its event budget");
		}
		List<EventState> eventStates =
			new ArrayList<EventState>(liveRegistrations.size());
		for (int ordinal = 0; ordinal < liveRegistrations.size(); ordinal++) {
			GameTickEventStore.RegisteredEvent registration =
				Objects.requireNonNull(
					liveRegistrations.get(ordinal),
					"liveRegistrations[" + ordinal + "]");
			GameTickEvent event = Objects.requireNonNull(
				registration.getEvent(),
				"liveRegistrations[" + ordinal + "].event");
			eventStates.add(detachEventState(
				event, ordinal, registration.getRegistrationSequence()));
		}
		return LayeredPackedRegionEventOwnershipInventory.inventory(
			checked.getGeneration(), observedAtTick,
			registrationSnapshot.getSchedulerInstanceIdentity(),
			packedSources, eventStates,
			checked.getCandidateSourceCount(), maximumEvents,
			maximumSpatialReferences);
	}

	private EventState detachEventState(
		final GameTickEvent event,
		final int ordinal,
		final long registrationSequence) {
		Mob owner = event.getOwner();
		OwnerKind ownerKind = owner == null
			? OwnerKind.NONE
			: owner instanceof Player
				? OwnerKind.PLAYER
				: owner instanceof Npc ? OwnerKind.NPC : OwnerKind.NONE;
		GameTickEventSpatialAffinity affinity = Objects.requireNonNull(
			event.getSpatialAffinity(), "event spatial affinity");
		AttributionKind attribution;
		List<SpatialReference> references =
			new ArrayList<SpatialReference>();
		switch (affinity.getScope()) {
			case EXACT_SPATIAL:
				attribution = AttributionKind.EXACT_SPATIAL;
				for (GameTickEventSpatialAffinity.Reference reference
					: affinity.getReferences()) {
					references.add(SpatialReference.of(
						SpatialRole.valueOf(reference.getRole().name()),
						reference.getX(), reference.getY()));
				}
				break;
			case NON_SPATIAL_GLOBAL:
				attribution = AttributionKind.NON_SPATIAL_GLOBAL;
				break;
			case UNSPECIFIED:
				if (owner != null) {
					attribution = AttributionKind.OWNER_POSITION_HINT;
					references.add(SpatialReference.of(
						SpatialRole.OWNER_CURRENT_POSITION,
						owner.getX(), owner.getY()));
				} else {
					attribution = AttributionKind.UNATTRIBUTED;
				}
				break;
			default:
				throw new IllegalStateException(
					"Unhandled event spatial-affinity scope");
		}
		return EventState.of(
			ordinal, registrationSequence, ownerKind, attribution,
			event.isRunning(),
			event.getTicksBeforeRun(), event.getTimesRan(), references,
			detachEventRestorationState(Objects.requireNonNull(
				event.getRestorationState(), "event restoration state")));
	}

	private EventRestorationState detachEventRestorationState(
		final GameTickEventRestorationState state) {
		if (state.getKind()
			== GameTickEventRestorationState.Kind.UNAVAILABLE) {
			return EventRestorationState.unavailable();
		}
		GameTickEventRestorationState.SceneryState scenery =
			Objects.requireNonNull(state.getScenery(), "restoration scenery");
		GameTickEventRestorationState.AuthoredPlacementState authored =
			scenery.getAuthoredPlacement();
		AuthoredPlacementRestorationState detachedAuthored = authored == null
			? null
			: AuthoredPlacementRestorationState.of(
				authored.getGeneration(), authored.getPackedRegionX(),
				authored.getPackedRegionY(), authored.getSourceOrdinal(),
				AuthoredConstructionKind.valueOf(
					authored.getConstructionKind().name()));
		SceneryRestorationState detachedScenery = SceneryRestorationState.of(
			scenery.getObjectId(), scenery.getPermanentObjectId(),
			scenery.getX(), scenery.getY(), scenery.getDirection(),
			scenery.getType(), scenery.getOwner(),
			scenery.getRuntimeAttributeCount(), detachedAuthored);
		switch (state.getKind()) {
			case SCENERY_SPAWN:
				return EventRestorationState.scenerySpawn(
					detachedScenery, state.isForceFullBlock());
			case SCENERY_REMOVE:
				return EventRestorationState.sceneryRemove(detachedScenery);
			default:
				throw new IllegalStateException(
					"Unhandled event restoration-state kind");
		}
	}

	public boolean hasEvent(Class<? extends GameTickEvent> type) {
		return eventStore.hasEvent(type);
	}

	public Collection<GameTickEvent> getEvents(Class<? extends GameTickEvent> type) {
		return eventStore.getEvents(type);
	}

	public Collection<GameTickEvent> getPlayerEvents(final Player player) {
		return eventStore.getPlayerEvents(player);
	}

	public void remove(final GameTickEvent event) {
		eventStore.remove(event);
	}
}
