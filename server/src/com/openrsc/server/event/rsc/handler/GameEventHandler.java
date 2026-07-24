package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.Server;
import com.openrsc.server.diagnostics.LayeredCoordinateParityObserver.PackedRegionEventRecoveryNoOpMetadata;
import com.openrsc.server.diagnostics.LayeredCoordinateParityObserver.PackedRegionNpcOwnerPreservationNoOpMetadata;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.event.rsc.ImmediateEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.OuterFenceReason;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.Record;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.TargetEvidence;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.ObservedTargetState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.TargetOutcome;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.TargetReason;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.ContractOutcome;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation.ContractReason;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.ArrivalOrderingRequirement;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AuthoredConstructionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AuthoredPlacementRestorationState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.BindingEvidence;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.DesiredState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.ExecutionSemantics;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.GenerationBindingRequirement;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.IdempotencyPolicy;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.MutationPrecondition;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.NpcOwnerIdentity;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.PackedSource;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.SpatialReference;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.SpatialRole;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationBoundaryObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.TargetConflictPolicy;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.TargetSubject;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.TimeProgressionPolicy;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementProposal;
import com.openrsc.server.model.world.region.LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionAuthoredCollisionVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionAuthoredSourceStateVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionReloadRecipe;
import com.openrsc.server.model.world.region.LayeredPackedRegionSourceAbsencePreflight;
import com.openrsc.server.model.world.region.LayeredPackedRegionSourceLifecycleBoundary;
import com.openrsc.server.model.world.region.LayeredPackedRegionTerrainVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionRuntimeAuthoredObjectObservation;
import com.openrsc.server.model.world.region.LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison;
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

		GameTickEventStore.StoreAtomicTimingSnapshot timingSnapshot =
			eventStore.getTrackedEventAtomicTimingSnapshot(observedAtTick);
		List<GameTickEventStore.AtomicTimedRegisteredEvent> liveRegistrations =
			timingSnapshot.getRegistrations();
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
			GameTickEventStore.AtomicTimedRegisteredEvent registration =
				Objects.requireNonNull(
					liveRegistrations.get(ordinal),
					"liveRegistrations[" + ordinal + "]");
			GameTickEvent event = Objects.requireNonNull(
				registration.getEvent(),
				"liveRegistrations[" + ordinal + "].event");
			eventStates.add(detachEventState(
				event, ordinal, registration.getRegistrationSequence(),
				registration.getTiming()));
		}
		return LayeredPackedRegionEventOwnershipInventory.inventory(
			checked.getGeneration(), timingSnapshot.getObservedAtTick(),
			timingSnapshot.getSchedulerInstanceIdentity(),
			packedSources, eventStates,
			checked.getCandidateSourceCount(), maximumEvents,
			maximumSpatialReferences);
	}

	/**
	 * Runs the explicit private verification-only recovery diagnostic against
	 * one exact detached inventory. No Region mutation or overdue callback
	 * consumption is permitted by the diagnostic policy.
	 */
	public PackedRegionEventRecoveryNoOpMetadata
		captureLayeredPackedRegionEventRecoveryNoOpDiagnostic(
			final LayeredPackedRegionEventOwnershipInventory inventory,
			final int maximumCandidates) {
		GameTickEventRestorationNoOpDiagnostic diagnostic =
			GameTickEventRestorationNoOpDiagnostic.capture(
			eventStore, getServer().getWorld().getRegionManager(),
			Objects.requireNonNull(inventory, "inventory"), maximumCandidates);
		return PackedRegionEventRecoveryNoOpMetadata.of(
			diagnostic.getReason().name(), diagnostic.getPreparationReason(),
			diagnostic.getLifecycleReason(), diagnostic.getProposalGeneration(),
			diagnostic.getInventoryEventCount(),
			diagnostic.getRecoveryCandidateCount(),
			diagnostic.getProposalRelatedEventCount(),
			diagnostic.getRecoveryCompleteEventCount(),
			diagnostic.getRecoveryIncompleteEventCount(),
			diagnostic.getIncompleteOwnerPositionHintEventCount(),
			diagnostic.getIncompleteExactSpatialEventCount(),
			diagnostic.getFirstIncompleteRegistrationSequence(),
			diagnostic.getFirstIncompleteOwnerKind(),
			diagnostic.getFirstIncompleteAttributionKind(),
			diagnostic.getFirstIncompleteRecoveryRequirement(),
			diagnostic.isPreflightComplete(),
			diagnostic.getFutureSnapshotCount(),
			diagnostic.getRuntimeVerificationCount(),
			diagnostic.getMutationOperationCount(),
			diagnostic.getTerminalEventConsumptionCount(),
			diagnostic.isReconstructionInvoked(),
			diagnostic.isRecoveryInvoked(),
			diagnostic.isContractuallyReadyForFirstVisibility(),
			diagnostic.isFreshInventoryRetryRequired());
	}

	/**
	 * Captures one nested scheduler/timing/World-registration boundary for the
	 * exact NPC owner requirements. The result remains a detached point-in-time
	 * observation and cannot establish preservation after the boundary returns.
	 */
	public LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
		captureLayeredPackedRegionNpcOwnerPreservationBoundary(
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				requirements,
			final int maximumOwners) {
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		final LayeredPackedRegionNpcOwnerPreservationBoundaryObservation[]
			result =
				new LayeredPackedRegionNpcOwnerPreservationBoundaryObservation[1];
		boolean sourceBoundaryEntered =
			getServer().getWorld().getRegionManager()
				.withinLayeredPackedRegionSourceLifecycleBoundary(
					checked, boundary -> {
						requireExactPackedSourceBoundary(boundary, checked);
						result[0] =
							GameTickEventNpcOwnerPreservationBoundary.capture(
								eventStore,
								getServer().getWorld().getNpcs(),
								checked, getServer().getCurrentTick(),
								maximumOwners, true);
					});
		if (!sourceBoundaryEntered) {
			result[0] = GameTickEventNpcOwnerPreservationBoundary.capture(
				eventStore, getServer().getWorld().getNpcs(), checked,
				getServer().getCurrentTick(), maximumOwners, false);
		}
		return Objects.requireNonNull(
			result[0], "NPC owner preservation boundary result");
	}

	/**
	 * Enters the real NPC-owner scope but deliberately refuses at the still
	 * unavailable source lifecycle. No source absence or preserved work runs.
	 */
	public PackedRegionNpcOwnerPreservationNoOpMetadata
		captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic(
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				requirements,
			final int maximumOwners) {
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		final GameTickEventNpcOwnerPreservationNoOpDiagnostic.Result[]
			captured =
				new GameTickEventNpcOwnerPreservationNoOpDiagnostic.Result[1];
		final LayeredPackedRegionSourceAbsencePreflight[] absencePreflight =
			new LayeredPackedRegionSourceAbsencePreflight[1];
		final LayeredPackedRegionReloadRecipe[] reloadRecipe =
			new LayeredPackedRegionReloadRecipe[1];
		final LayeredPackedRegionTerrainVerificationBatch[]
			terrainVerification =
				new LayeredPackedRegionTerrainVerificationBatch[1];
		final LayeredPackedRegionAuthoredCollisionVerificationBatch[]
			authoredCollisionVerification =
				new
					LayeredPackedRegionAuthoredCollisionVerificationBatch[1];
		final
			LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch[]
				authoredCollisionApplicationVerification =
					new
						LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch[1];
		final LayeredPackedRegionAuthoredSourceStateVerificationBatch[]
			authoredSourceStateVerification =
				new
					LayeredPackedRegionAuthoredSourceStateVerificationBatch[1];
		final
			LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch[]
				transactionalAuthoredSourceVerification =
					new
						LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch[1];
		final LayeredPackedRegionRuntimeAuthoredObjectObservation[]
			runtimeAuthoredObjectObservation =
				new LayeredPackedRegionRuntimeAuthoredObjectObservation[1];
		final
			LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[]
				runtimeAuthoredObjectBaselineComparison =
					new
						LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[1];
		boolean sourceBoundaryEntered =
			getServer().getWorld().getRegionManager()
				.withinLayeredPackedRegionSourceLifecycleBoundary(
					checked, boundary -> {
						requireExactPackedSourceBoundary(boundary, checked);
						absencePreflight[0] =
							getServer().getWorld().getRegionManager()
								.captureLayeredPackedRegionSourceAbsencePreflight(
									boundary);
						reloadRecipe[0] =
							getServer().getWorld().getRegionManager()
								.captureLayeredPackedRegionReloadRecipe(
									boundary, absencePreflight[0],
									getServer().getWorld().getWorldLoader()
										.getWorldPopulator()
										.getAuthoredReconstructionRecipe());
						terrainVerification[0] =
							LayeredPackedRegionTerrainVerificationBatch.capture(
								getServer().getWorld().getRegionManager(),
								boundary, reloadRecipe[0],
								LayeredPackedRegionTerrainVerificationBatch
									.MAXIMUM_VERIFICATION_SOURCES);
						authoredCollisionVerification[0] =
							LayeredPackedRegionAuthoredCollisionVerificationBatch
								.capture(
									getServer().getWorld().getRegionManager(),
									boundary, reloadRecipe[0],
									LayeredPackedRegionAuthoredCollisionVerificationBatch
										.MAXIMUM_VERIFICATION_SOURCES);
						authoredCollisionApplicationVerification[0] =
							LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
								.capture(
									getServer().getWorld().getRegionManager(),
									boundary, reloadRecipe[0],
									LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
										.MAXIMUM_VERIFICATION_SOURCES);
						authoredSourceStateVerification[0] =
							LayeredPackedRegionAuthoredSourceStateVerificationBatch
								.capture(
									getServer().getWorld().getRegionManager(),
									boundary, reloadRecipe[0],
									LayeredPackedRegionAuthoredSourceStateVerificationBatch
										.MAXIMUM_VERIFICATION_SOURCES);
						transactionalAuthoredSourceVerification[0] =
							LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
								.capture(
									getServer().getWorld().getRegionManager(),
									boundary, reloadRecipe[0],
									LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
										.MAXIMUM_VERIFICATION_SOURCES);
						runtimeAuthoredObjectObservation[0] =
							getServer().getWorld().getRegionManager()
								.captureLayeredPackedRegionRuntimeAuthoredObjects(
									boundary, reloadRecipe[0],
									LayeredPackedRegionRuntimeAuthoredObjectObservation
										.MAXIMUM_OBJECT_INSTANCES);
						runtimeAuthoredObjectBaselineComparison[0] =
							LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
								.compare(
									runtimeAuthoredObjectObservation[0],
									transactionalAuthoredSourceVerification[0]);
						captured[0] =
							GameTickEventNpcOwnerPreservationNoOpDiagnostic
								.capture(
									eventStore,
									getServer().getWorld().getNpcs(),
									checked, getServer().getCurrentTick(),
									maximumOwners, true);
					});
		if (!sourceBoundaryEntered) {
			captured[0] =
				GameTickEventNpcOwnerPreservationNoOpDiagnostic.capture(
					eventStore, getServer().getWorld().getNpcs(),
					checked, getServer().getCurrentTick(),
					maximumOwners, false);
		}
		GameTickEventNpcOwnerPreservationNoOpDiagnostic.Result result =
			Objects.requireNonNull(
				captured[0], "NPC owner preservation no-op result");
		return PackedRegionNpcOwnerPreservationNoOpMetadata.of(
			result.getReason().name(), result.getGeneration(),
			result.getRequirementsObservedAtTick(),
			result.getSelectedSourceCount(),
			result.getRequiredEventLinkCount(),
			result.getRequiredOwnerCount(), result.isOwnerScopeEntered(),
			result.isSourceLifecycleInvoked(),
			result.getAbsentSourceCount(),
			result.getReconstructedSourceCount(),
			result.isPreservedConsumerInvoked(), absencePreflight[0],
			reloadRecipe[0], terrainVerification[0],
			authoredCollisionVerification[0],
			authoredCollisionApplicationVerification[0],
			authoredSourceStateVerification[0],
			transactionalAuthoredSourceVerification[0],
			runtimeAuthoredObjectObservation[0],
			runtimeAuthoredObjectBaselineComparison[0]);
	}

	private void requireExactPackedSourceBoundary(
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionNpcOwnerPreservationRequirements
			requirements) {
		if (!boundary.isRegionLifecycleBoundaryHeld()
			|| !boundary.isAllSourcesResidentAtEntry()
			|| !boundary.matchesRequirements(requirements)
			|| boundary.isSourceAbsencePerformed()
			|| boundary.isSourceReconstructionPerformed()
			|| boundary.isRuntimeHandleRetained()
			|| boundary.isLifecycleAuthority()) {
			throw new IllegalStateException(
				"Packed-source lifecycle boundary differs from owner requirements");
		}
	}

	/**
	 * Revalidates every restoration-capable record from one detached inventory
	 * through the composed scheduler/Region read-only boundary.
	 */
	public LayeredPackedRegionEventAtomicTargetRevalidation
		captureLayeredPackedRegionEventAtomicTargetRevalidation(
			final LayeredPackedRegionEventOwnershipInventory inventory,
			final int maximumRecords) {
		LayeredPackedRegionEventOwnershipInventory checked =
			Objects.requireNonNull(inventory, "inventory");
		if (maximumRecords < 0
			|| maximumRecords
				> LayeredPackedRegionEventAtomicTargetRevalidation.MAXIMUM_RECORDS
			|| checked.getRestorationStateAvailableEventCount()
				> maximumRecords) {
			throw new IllegalArgumentException(
				"Atomic target revalidation exceeds its record budget");
		}
		List<Record> records = new ArrayList<Record>(
			checked.getRestorationStateAvailableEventCount());
		for (LayeredPackedRegionEventOwnershipInventory.EventRecord event
			: checked.getEvents()) {
			LayeredPackedRegionEventOwnershipInventory.EventRestorationState
				restoration = event.getRestorationState();
			if (restoration.getKind()
				== LayeredPackedRegionEventOwnershipInventory
					.RestorationKind.UNAVAILABLE) {
				continue;
			}
			LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState
				scenery = Objects.requireNonNull(
					restoration.getScenery(), "restoration scenery");
			GameTickEventStore.RestorationTargetRevalidationExecution execution =
				eventStore.withValidatedRestorationTargetRevalidation(
					getServer().getWorld().getRegionManager(),
					checked.getSchedulerInstanceIdentity(),
					event.getRegistrationSequence(),
					checked.getProposalGeneration());
			long before = execution.getLifecycleVersionBeforeOperation();
			long after = execution.getLifecycleVersionAfterOperation();
			GameTickEventRestorationTargetRevalidation runtimeTarget =
				execution.getTarget();
			TargetEvidence target = runtimeTarget == null ? null
				: TargetEvidence.evidence(
					runtimeTarget.isRegionAvailable(),
					runtimeTarget.getSlotObjectCount(),
					runtimeTarget.getExactRestorationSceneryCount(),
					runtimeTarget.getExactAuthoredIdentityCount(),
					runtimeTarget
						.isObjectBoundaryHeldDuringClassification(),
					ObservedTargetState.valueOf(
						runtimeTarget.getObservedTargetState().name()),
					TargetOutcome.valueOf(
						runtimeTarget.getTargetDecision().getOutcome().name()),
					TargetReason.valueOf(
						runtimeTarget.getTargetDecision().getReason().name()),
					ContractOutcome.valueOf(
						runtimeTarget.getContract().getOutcome().name()),
					ContractReason.valueOf(
						runtimeTarget.getContract().getReason().name()));
			records.add(Record.record(
				event.getSnapshotOrdinal(), event.getRegistrationSequence(),
				scenery.getX(), scenery.getY(),
				OuterFenceReason.valueOf(
					execution.getOuterFenceReason().name()),
				execution.isOperationInvoked(),
				before > 0L ? Long.valueOf(before) : null,
				after > 0L ? Long.valueOf(after) : null,
				execution.isTimingStableAcrossOperation(),
				execution.isRuntimeTargetLookupPerformed(), target));
		}
		return LayeredPackedRegionEventAtomicTargetRevalidation.observation(
			checked.getProposalGeneration(), checked.getObservedAtTick(),
			getServer().getCurrentTick(),
			checked.getSchedulerInstanceIdentity(), records, maximumRecords);
	}

	private EventState detachEventState(
		final GameTickEvent event,
		final int ordinal,
		final long registrationSequence,
		final GameTickEvent.AtomicTimingSnapshot timing) {
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
		EventRestorationState restorationState =
			detachEventRestorationState(Objects.requireNonNull(
				event.getRestorationState(), "event restoration state"));
		NpcOwnerIdentity npcOwnerIdentity = null;
		if (ownerKind == OwnerKind.NPC) {
			Npc npcOwner = (Npc) owner;
			LayeredAuthoredPlacementIdentity identity =
				npcOwner.getAuthoredPlacementIdentity();
			if (identity != null) {
				npcOwnerIdentity = NpcOwnerIdentity.of(
					identity.getGeneration(), identity.getPackedRegionX(),
					identity.getPackedRegionY(), identity.getSourceOrdinal(),
					identity.getConstructionKind().name(), npcOwner.getID());
			}
		}
		return EventState.of(
			ordinal, registrationSequence, ownerKind, npcOwnerIdentity,
			attribution,
			timing.isRunning(), timing.getTicksBeforeRun(),
			timing.getTimesRan(), references, restorationState,
			restorationState.isExecutionSemanticsCaptured());
	}

	private EventRestorationState detachEventRestorationState(
		final GameTickEventRestorationState state) {
		GameTickEventRestorationRequirement requirement =
			GameTickEventRestorationRequirement.from(state);
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
		ExecutionSemantics executionSemantics = ExecutionSemantics.valueOf(
			state.getExecutionSemantics().name());
		TimeProgressionPolicy timeProgressionPolicy =
			TimeProgressionPolicy.valueOf(
				state.getTimeProgressionPolicy().name());
		TargetSubject targetSubject = TargetSubject.valueOf(
			requirement.getTargetSubject().name());
		BindingEvidence bindingEvidence = BindingEvidence.valueOf(
			requirement.getBindingEvidence().name());
		TargetConflictPolicy targetConflictPolicy =
			TargetConflictPolicy.valueOf(
				requirement.getTargetConflictPolicy().name());
		ArrivalOrderingRequirement arrivalOrderingRequirement =
			ArrivalOrderingRequirement.valueOf(
				requirement.getArrivalOrderingRequirement().name());
		GenerationBindingRequirement generationBindingRequirement =
			GenerationBindingRequirement.valueOf(
				requirement.getGenerationBindingRequirement().name());
		DesiredState desiredState = DesiredState.valueOf(
			requirement.getDesiredState().name());
		IdempotencyPolicy idempotencyPolicy = IdempotencyPolicy.valueOf(
			requirement.getIdempotencyPolicy().name());
		MutationPrecondition mutationPrecondition =
			MutationPrecondition.valueOf(
				requirement.getMutationPrecondition().name());
		validateDetachedRestorationTarget(
			requirement, authored, detachedAuthored);
		switch (state.getKind()) {
			case SCENERY_SPAWN:
				return EventRestorationState.scenerySpawn(
					detachedScenery, state.isForceFullBlock(),
					executionSemantics, timeProgressionPolicy,
					targetSubject, bindingEvidence, targetConflictPolicy,
					arrivalOrderingRequirement, generationBindingRequirement,
					desiredState, idempotencyPolicy, mutationPrecondition);
			case SCENERY_REMOVE:
				return EventRestorationState.sceneryRemove(
					detachedScenery, executionSemantics,
					timeProgressionPolicy, targetSubject, bindingEvidence,
					targetConflictPolicy, arrivalOrderingRequirement,
					generationBindingRequirement, desiredState,
					idempotencyPolicy, mutationPrecondition);
			default:
				throw new IllegalStateException(
					"Unhandled event restoration-state kind");
		}
	}

	private static void validateDetachedRestorationTarget(
		final GameTickEventRestorationRequirement requirement,
		final GameTickEventRestorationState.AuthoredPlacementState authored,
		final AuthoredPlacementRestorationState detachedAuthored) {
		GameTickEventRestorationRequirement.AuthoredTarget target =
			requirement.getAuthoredTarget();
		if (target == null) {
			if (authored != null || detachedAuthored != null
				|| requirement.isTargetBindingComplete()) {
				throw new IllegalArgumentException(
					"Missing restoration target disagrees with authored state");
			}
			return;
		}
		if (authored == null || detachedAuthored == null
			|| !requirement.isTargetBindingComplete()
			|| target.getGeneration() != authored.getGeneration()
			|| target.getPackedRegionX() != authored.getPackedRegionX()
			|| target.getPackedRegionY() != authored.getPackedRegionY()
			|| target.getSourceOrdinal() != authored.getSourceOrdinal()
			|| target.getConstructionKind()
				!= authored.getConstructionKind()
			|| target.getGeneration() != detachedAuthored.getGeneration()
			|| target.getPackedRegionX()
				!= detachedAuthored.getPackedRegionX()
			|| target.getPackedRegionY()
				!= detachedAuthored.getPackedRegionY()
			|| target.getSourceOrdinal()
				!= detachedAuthored.getSourceOrdinal()
			|| !target.getConstructionKind().name().equals(
				detachedAuthored.getConstructionKind().name())) {
			throw new IllegalArgumentException(
				"Restoration target disagrees with detached authored state");
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
