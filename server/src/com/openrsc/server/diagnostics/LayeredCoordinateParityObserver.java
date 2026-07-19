package com.openrsc.server.diagnostics;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyPackedVisibilityCoverageComparison;
import com.openrsc.server.model.world.coordinate.LayeredCoordinateParitySnapshot;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionInterestDelta;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in, non-authoritative JSONL observer for private layered-coordinate parity tests. */
public final class LayeredCoordinateParityObserver {
	public static final String EVENT_SCHEMA = "layered-map-parity-event-v10";
	public static final String LOG_ROOT_PROPERTY = "openrsc.layeredParityLogRoot";
	private static final int MAX_TRACE_PACKED_CELLS = 4096;
	private static final int MAX_TRACE_REGIONS_PER_WINDOW = 4096;
	private static final int MAX_TRACE_TRAVERSAL_STEPS = 16;

	private static final Logger LOGGER = LogManager.getLogger(LayeredCoordinateParityObserver.class);
	private static final Map<TraceKey, TraceState> TRACES =
		new ConcurrentHashMap<TraceKey, TraceState>();

	private LayeredCoordinateParityObserver() {
	}

	public static Status start(
		int playerId,
		long usernameHash,
		Point current,
		int viewGridDistance,
		TileSnapshotSource tileSnapshotSource,
		TileParitySource tileParitySource,
		TileNeighborhoodSource tileNeighborhoodSource,
		AdjacentCollisionSource adjacentCollisionSource,
		TraversalCollisionSource traversalCollisionSource,
		RegionResidencySource regionResidencySource) {
		Objects.requireNonNull(tileSnapshotSource, "tileSnapshotSource");
		Objects.requireNonNull(tileParitySource, "tileParitySource");
		Objects.requireNonNull(tileNeighborhoodSource, "tileNeighborhoodSource");
		Objects.requireNonNull(adjacentCollisionSource, "adjacentCollisionSource");
		Objects.requireNonNull(traversalCollisionSource, "traversalCollisionSource");
		Objects.requireNonNull(regionResidencySource, "regionResidencySource");
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState created = new TraceState(
			key, logPath(key), viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource);
		TraceState state = TRACES.putIfAbsent(key, created);
		boolean newlyStarted = state == null;
		if (newlyStarted) {
			state = created;
		} else if (state.viewGridDistance != viewGridDistance) {
			throw new IllegalArgumentException(
				"Active trace view distance does not match the current server configuration");
		}
		return write(state, newlyStarted ? "start" : "snapshot", null, current, null, null);
	}

	public static Status snapshot(int playerId, long usernameHash, Point current) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		return state == null ? Status.disabled(logPath(new TraceKey(playerId, usernameHash)))
			: write(state, "snapshot", null, current, null, null);
	}

	public static Status mark(int playerId, long usernameHash, Point current, String label) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		return state == null ? Status.disabled(logPath(new TraceKey(playerId, usernameHash)))
			: write(state, "marker", null, current, null, sanitizeLabel(label));
	}

	public static Status stop(int playerId, long usernameHash, Point current) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState state = TRACES.get(key);
		if (state == null) {
			return Status.disabled(logPath(key));
		}
		Status status = write(state, "stop", null, current, null, null);
		TRACES.remove(key, state);
		return status.asDisabled();
	}

	public static Status status(int playerId, long usernameHash) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState state = TRACES.get(key);
		if (state == null) {
			return Status.disabled(logPath(key));
		}
		synchronized (state) {
			return state.status(true);
		}
	}

	public static void onLocationChanged(
		int playerId,
		long usernameHash,
		Point previous,
		Point current,
		boolean teleported) {
		if (previous == null || current == null
			|| (previous.getX() == current.getX() && previous.getY() == current.getY())) {
			return;
		}
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		if (state != null) {
			write(state, teleported ? "teleport" : "move", previous, current,
				Boolean.valueOf(teleported), null);
		}
	}

	public static void onSession(int playerId, long usernameHash, Point current, boolean loggedIn) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		if (state != null && current != null) {
			write(state, loggedIn ? "login" : "logout", null, current, null, null);
		}
	}

	private static Status write(
		TraceState state,
		String eventType,
		Point previous,
		Point current,
		Boolean teleported,
		String label) {
		Objects.requireNonNull(current, "current");
		synchronized (state) {
			try {
				LayeredCoordinateParitySnapshot to =
					LayeredCoordinateParitySnapshot.capture(current, state.viewGridDistance);
				LayeredCoordinateParitySnapshot from = previous == null
					? null : LayeredCoordinateParitySnapshot.capture(
						previous, state.viewGridDistance);
				updateRecentTraversal(state, eventType, from, to, teleported);
				LegacyPackedVisibilityCoverageComparison coverage =
					LegacyPackedVisibilityCoverageComparison.compare(
						current,
						state.viewGridDistance,
						MAX_TRACE_PACKED_CELLS,
						MAX_TRACE_REGIONS_PER_WINDOW);
				TileSnapshotMetadata tileSnapshot = Objects.requireNonNull(
					state.tileSnapshotSource.capture(to.getRegionKey()),
					"tileSnapshotSource result");
				if (!to.getRegionKey().equals(tileSnapshot.getLogicalRegionKey())) {
					throw new IllegalStateException(
						"Tile snapshot metadata key differs from the current logical region");
				}
				TileParityMetadata tileParity = null;
				TileNeighborhoodMetadata tileNeighborhood = null;
				AdjacentCollisionMetadata adjacentCollision = null;
				RecentTraversalMetadata recentTraversal = null;
				WorldRegionInterestDelta interestDelta = from == null ? null
					: WorldRegionInterestDelta.between(
						from.getVisibilityWindow(), to.getVisibilityWindow(),
						MAX_TRACE_REGIONS_PER_WINDOW);
				RegionResidencyMetadata regionResidency = null;
				if (capturesTileComparisons(eventType)) {
					tileParity = Objects.requireNonNull(
						state.tileParitySource.capture(current),
						"tileParitySource result");
					if (!to.getLocation().equals(tileParity.getLogicalLocation())) {
						throw new IllegalStateException(
							"Tile parity metadata location differs from the current location");
					}
					tileNeighborhood = Objects.requireNonNull(
						state.tileNeighborhoodSource.capture(current),
						"tileNeighborhoodSource result");
					if (!to.getLocation().equals(tileNeighborhood.getCenter())) {
						throw new IllegalStateException(
							"Tile neighborhood metadata center differs from the current location");
					}
					adjacentCollision = Objects.requireNonNull(
						state.adjacentCollisionSource.capture(current),
						"adjacentCollisionSource result");
					if (!to.getLocation().equals(adjacentCollision.getCenter())) {
						throw new IllegalStateException(
							"Adjacent collision metadata center differs from the current location");
					}
				}
				if (capturesRecentTraversal(eventType)
					&& state.recentTraversal.size() > 1) {
					List<WorldLocation> route = Collections.unmodifiableList(
						new ArrayList<WorldLocation>(state.recentTraversal));
					recentTraversal = Objects.requireNonNull(
						state.traversalCollisionSource.capture(
							route,
							state.recentTraversalDroppedStepCount,
							state.recentTraversalDiscontinuityCount),
						"traversalCollisionSource result");
					if (!route.get(0).equals(recentTraversal.getSource())
						|| !route.get(route.size() - 1).equals(
							recentTraversal.getDestination())
						|| recentTraversal.getStepCount() != route.size() - 1
						|| recentTraversal.getDroppedStepCount()
							!= state.recentTraversalDroppedStepCount
						|| recentTraversal.getDiscontinuityCount()
							!= state.recentTraversalDiscontinuityCount) {
						throw new IllegalStateException(
							"Recent traversal metadata differs from the observed route");
					}
				}
				if (capturesRegionResidency(eventType, interestDelta)) {
					WorldRegionWindow previousWindow = from == null
						? to.getVisibilityWindow() : from.getVisibilityWindow();
					regionResidency = Objects.requireNonNull(
						state.regionResidencySource.capture(
							previousWindow, to.getVisibilityWindow(),
							MAX_TRACE_REGIONS_PER_WINDOW),
						"regionResidencySource result");
					WorldRegionInterestDelta expected = WorldRegionInterestDelta.between(
						previousWindow, to.getVisibilityWindow(),
						MAX_TRACE_REGIONS_PER_WINDOW);
					regionResidency.requireMatches(expected);
				}
				long nextSequence = state.sequence + 1L;
				String line = eventJson(
					state.key, nextSequence, System.currentTimeMillis(), eventType,
					teleported, label, from, to, interestDelta, coverage, tileSnapshot,
					tileParity, tileNeighborhood, adjacentCollision, recentTraversal,
					regionResidency);
				Files.createDirectories(state.path.getParent());
				try (BufferedWriter writer = Files.newBufferedWriter(
					state.path,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND)) {
					writer.write(line);
					writer.newLine();
				}
				state.sequence = nextSequence;
				state.lastSnapshot = to;
				state.lastError = null;
				if ("marker".equals(eventType)) {
					resetRecentTraversal(state, to.getLocation());
				}
			} catch (RuntimeException | IOException failure) {
				state.lastError = failure.getClass().getSimpleName() + ": " + safeMessage(failure);
				LOGGER.error("Layered coordinate parity observer could not write {}", state.path, failure);
			}
			return state.status(true);
		}
	}

	private static String eventJson(
		TraceKey key,
		long sequence,
		long timestamp,
		String eventType,
		Boolean teleported,
		String label,
		LayeredCoordinateParitySnapshot from,
		LayeredCoordinateParitySnapshot to,
		WorldRegionInterestDelta interestDelta,
		LegacyPackedVisibilityCoverageComparison coverage,
		TileSnapshotMetadata tileSnapshot,
		TileParityMetadata tileParity,
		TileNeighborhoodMetadata tileNeighborhood,
		AdjacentCollisionMetadata adjacentCollision,
		RecentTraversalMetadata recentTraversal,
		RegionResidencyMetadata regionResidency) {
		StringBuilder out = new StringBuilder(1024);
		out.append('{');
		field(out, "schema", EVENT_SCHEMA).append(',');
		field(out, "eventType", eventType).append(',');
		out.append("\"sequence\":").append(sequence).append(',');
		out.append("\"timestampEpochMillis\":").append(timestamp).append(',');
		out.append("\"player\":{\"databaseId\":").append(key.playerId)
			.append(",\"usernameHash\":\"").append(Long.toUnsignedString(key.usernameHash))
			.append("\"},");
		out.append("\"label\":");
		if (label == null) {
			out.append("null");
		} else {
			quoted(out, label);
		}
		out.append(",\"teleported\":");
		out.append(teleported == null ? "null" : teleported.toString());
		out.append(",\"from\":").append(
			from == null ? "null" : from.toJsonWithVisibilityWindow());
		out.append(",\"to\":").append(to.toJsonWithVisibilityWindow());
		out.append(",\"delta\":");
		if (from == null) {
			out.append("null");
		} else {
			WorldCoordinate before = from.getLocation().getCoordinate();
			WorldCoordinate after = to.getLocation().getCoordinate();
			out.append("{\"x\":").append(after.getX() - before.getX())
				.append(",\"y\":").append(after.getY() - before.getY())
				.append(",\"level\":").append(after.getLevel() - before.getLevel())
				.append('}');
		}
		out.append(",\"interestDelta\":");
		if (interestDelta == null) {
			out.append("null");
		} else {
			appendInterestDelta(out, interestDelta);
		}
		out.append(",\"packedCoverage\":");
		appendPackedCoverage(out, coverage);
		out.append(",\"tileSnapshot\":");
		appendTileSnapshot(out, tileSnapshot);
		out.append(",\"tileParity\":");
		if (tileParity == null) {
			out.append("null");
		} else {
			appendTileParity(out, tileParity);
		}
		out.append(",\"tileNeighborhood\":");
		if (tileNeighborhood == null) {
			out.append("null");
		} else {
			appendTileNeighborhood(out, tileNeighborhood);
		}
		out.append(",\"adjacentCollision\":");
		if (adjacentCollision == null) {
			out.append("null");
		} else {
			appendAdjacentCollision(out, adjacentCollision);
		}
		out.append(",\"recentTraversal\":");
		if (recentTraversal == null) {
			out.append("null");
		} else {
			appendRecentTraversal(out, recentTraversal);
		}
		out.append(",\"regionResidency\":");
		if (regionResidency == null) {
			out.append("null");
		} else {
			appendRegionResidency(out, regionResidency);
		}
		out.append(",\"roundTripExact\":")
			.append(to.isRoundTripExact() && (from == null || from.isRoundTripExact()));
		return out.append('}').toString();
	}

	private static void appendInterestDelta(
		final StringBuilder out,
		final WorldRegionInterestDelta delta) {
		out.append('{');
		out.append("\"previousRegionCount\":")
			.append(delta.getExited().size() + delta.getRetained().size()).append(',');
		out.append("\"currentRegionCount\":")
			.append(delta.getEntered().size() + delta.getRetained().size()).append(',');
		out.append("\"enteredCount\":").append(delta.getEntered().size()).append(',');
		out.append("\"retainedCount\":").append(delta.getRetained().size()).append(',');
		out.append("\"exitedCount\":").append(delta.getExited().size()).append(',');
		out.append("\"worldSpaceChanged\":").append(delta.changesWorldSpace()).append(',');
		out.append("\"levelChanged\":").append(delta.changesLevel()).append(',');
		out.append("\"noOp\":").append(delta.isNoOp()).append(',');
		out.append("\"enteredKeys\":");
		appendRegionKeys(out, delta.getEntered());
		out.append(",\"exitedKeys\":");
		appendRegionKeys(out, delta.getExited());
		out.append('}');
	}

	private static void appendRegionResidency(
		final StringBuilder out,
		final RegionResidencyMetadata residency) {
		out.append('{');
		out.append("\"mirrorVersion\":").append(residency.getMirrorVersion()).append(',');
		out.append("\"previousRegionCount\":")
			.append(residency.getPreviousRegionCount()).append(',');
		out.append("\"currentRegionCount\":")
			.append(residency.getCurrentRegionCount()).append(',');
		out.append("\"enteredCount\":").append(residency.getEnteredCount()).append(',');
		out.append("\"retainedCount\":").append(residency.getRetainedCount()).append(',');
		out.append("\"exitedCount\":").append(residency.getExitedCount()).append(',');
		out.append("\"worldSpaceChanged\":")
			.append(residency.isWorldSpaceChanged()).append(',');
		out.append("\"levelChanged\":").append(residency.isLevelChanged()).append(',');
		out.append("\"noOp\":").append(residency.isNoOp()).append(',');
		out.append("\"residentCurrentCount\":")
			.append(residency.getResidentCurrentCount()).append(',');
		out.append("\"partialCurrentCount\":")
			.append(residency.getPartialCurrentCount()).append(',');
		out.append("\"missingCurrentCount\":")
			.append(residency.getMissingCurrentCount()).append(',');
		out.append("\"unsupportedCurrentCount\":")
			.append(residency.getUnsupportedCurrent().size()).append(',');
		out.append("\"loadCandidateCount\":")
			.append(residency.getLoadCandidates().size()).append(',');
		out.append("\"releaseCandidateCount\":")
			.append(residency.getReleaseCandidates().size()).append(',');
		out.append("\"loadCandidates\":");
		appendRegionResidencyCandidates(out, residency.getLoadCandidates());
		out.append(",\"releaseCandidates\":");
		appendRegionResidencyCandidates(out, residency.getReleaseCandidates());
		out.append(",\"unsupportedCurrent\":");
		appendRegionResidencyCandidates(out, residency.getUnsupportedCurrent());
		out.append('}');
	}

	private static void appendRegionResidencyCandidates(
		final StringBuilder out,
		final List<RegionResidencyCandidateMetadata> candidates) {
		out.append('[');
		boolean first = true;
		for (RegionResidencyCandidateMetadata candidate : candidates) {
			if (!first) {
				out.append(',');
			}
			first = false;
			WorldRegionKey key = candidate.getLogicalRegionKey();
			out.append("{\"logicalRegion\":{\"worldSpace\":\"")
				.append(jsonEscape(key.getWorldSpace().getValue()))
				.append("\",\"level\":").append(key.getLevel())
				.append(",\"x\":").append(key.getRegionX())
				.append(",\"y\":").append(key.getRegionY()).append("},");
			field(out, "interestState", candidate.getInterestState().name()).append(',');
			field(out, "residencyState", candidate.getResidencyState().name()).append(',');
			out.append("\"sourceCount\":").append(candidate.getSourceCount()).append(',');
			out.append("\"residentSourceCount\":")
				.append(candidate.getResidentSourceCount()).append(',');
			out.append("\"missingSourceCount\":")
				.append(candidate.getMissingSourceCount()).append(',');
			out.append("\"supportedTileCount\":")
				.append(candidate.getSupportedTileCount()).append(',');
			out.append("\"residentTileCount\":")
				.append(candidate.getResidentTileCount()).append(',');
			out.append("\"legacyCoverageComplete\":")
				.append(candidate.isLegacyCoverageComplete()).append('}');
		}
		out.append(']');
	}

	private static void appendTileParity(
		final StringBuilder out,
		final TileParityMetadata parity) {
		WorldLocation location = parity.getLogicalLocation();
		WorldCoordinate coordinate = location.getCoordinate();
		out.append('{');
		out.append("\"logicalLocation\":{\"worldSpace\":\"")
			.append(jsonEscape(location.getWorldSpace().getValue()))
			.append("\",\"x\":").append(coordinate.getX())
			.append(",\"y\":").append(coordinate.getY())
			.append(",\"level\":").append(coordinate.getLevel()).append("},");
		out.append("\"legacyPackedAddress\":");
		Point packedAddress = parity.getLegacyPackedAddress();
		if (packedAddress == null) {
			out.append("null");
		} else {
			out.append("{\"x\":").append(packedAddress.getX())
				.append(",\"y\":").append(packedAddress.getY()).append('}');
		}
		out.append(",\"legacyRepresentable\":")
			.append(parity.isLegacyRepresentable()).append(',');
		out.append("\"packedSourcePresent\":")
			.append(parity.isPackedSourcePresent()).append(',');
		out.append("\"missingPackedSource\":")
			.append(parity.isMissingPackedSource()).append(',');
		out.append("\"comparable\":").append(parity.isComparable()).append(',');
		out.append("\"exact\":").append(parity.isExact()).append('}');
	}

	private static void appendTileNeighborhood(
		final StringBuilder out,
		final TileNeighborhoodMetadata neighborhood) {
		WorldLocation center = neighborhood.getCenter();
		WorldCoordinate coordinate = center.getCoordinate();
		out.append('{');
		out.append("\"center\":{\"worldSpace\":\"")
			.append(jsonEscape(center.getWorldSpace().getValue()))
			.append("\",\"x\":").append(coordinate.getX())
			.append(",\"y\":").append(coordinate.getY())
			.append(",\"level\":").append(coordinate.getLevel()).append("},");
		out.append("\"cellCount\":").append(neighborhood.getCellCount()).append(',');
		out.append("\"legacyRepresentableCount\":")
			.append(neighborhood.getLegacyRepresentableCount()).append(',');
		out.append("\"unsupportedCount\":")
			.append(neighborhood.getUnsupportedCount()).append(',');
		out.append("\"packedSourcePresentCount\":")
			.append(neighborhood.getPackedSourcePresentCount()).append(',');
		out.append("\"missingPackedSourceCount\":")
			.append(neighborhood.getMissingPackedSourceCount()).append(',');
		out.append("\"comparableCount\":")
			.append(neighborhood.getComparableCount()).append(',');
		out.append("\"exactCount\":").append(neighborhood.getExactCount()).append(',');
		out.append("\"complete\":").append(neighborhood.isComplete()).append(',');
		out.append("\"exact\":").append(neighborhood.isExact()).append('}');
	}

	private static void appendAdjacentCollision(
		final StringBuilder out,
		final AdjacentCollisionMetadata collision) {
		WorldLocation center = collision.getCenter();
		WorldCoordinate coordinate = center.getCoordinate();
		out.append('{');
		out.append("\"center\":{\"worldSpace\":\"")
			.append(jsonEscape(center.getWorldSpace().getValue()))
			.append("\",\"x\":").append(coordinate.getX())
			.append(",\"y\":").append(coordinate.getY())
			.append(",\"level\":").append(coordinate.getLevel()).append("},");
		out.append("\"directionCount\":").append(collision.getDirections().size()).append(',');
		out.append("\"logicalDecisionAvailableCount\":")
			.append(collision.getLogicalDecisionAvailableCount()).append(',');
		out.append("\"packedDecisionAvailableCount\":")
			.append(collision.getPackedDecisionAvailableCount()).append(',');
		out.append("\"comparableCount\":").append(collision.getComparableCount()).append(',');
		out.append("\"passabilityExactCount\":")
			.append(collision.getPassabilityExactCount()).append(',');
		out.append("\"blockingReasonExactCount\":")
			.append(collision.getBlockingReasonExactCount()).append(',');
		out.append("\"requiredStatesExactCount\":")
			.append(collision.getRequiredStatesExactCount()).append(',');
		out.append("\"allComparable\":").append(collision.isAllComparable()).append(',');
		out.append("\"allPassabilityExact\":")
			.append(collision.isAllPassabilityExact()).append(',');
		out.append("\"allBlockingReasonsExact\":")
			.append(collision.isAllBlockingReasonsExact()).append(',');
		out.append("\"allRequiredStatesExact\":")
			.append(collision.isAllRequiredStatesExact()).append(',');
		out.append("\"directions\":[");
		boolean first = true;
		for (AdjacentDirectionMetadata direction : collision.getDirections()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			appendAdjacentDirection(out, direction);
		}
		out.append("]}");
	}

	private static void appendAdjacentDirection(
		final StringBuilder out,
		final AdjacentDirectionMetadata direction) {
		WorldLocation destination = direction.getDestination();
		WorldCoordinate coordinate = destination.getCoordinate();
		out.append('{');
		out.append("\"offset\":{\"x\":").append(direction.getOffsetX())
			.append(",\"y\":").append(direction.getOffsetY()).append("},");
		out.append("\"destination\":{\"worldSpace\":\"")
			.append(jsonEscape(destination.getWorldSpace().getValue()))
			.append("\",\"x\":").append(coordinate.getX())
			.append(",\"y\":").append(coordinate.getY())
			.append(",\"level\":").append(coordinate.getLevel()).append("},");
		out.append("\"requiredCellCount\":")
			.append(direction.getRequiredCellCount()).append(',');
		out.append("\"exactRequiredStateCount\":")
			.append(direction.getExactRequiredStateCount()).append(',');
		out.append("\"requiredStatesExact\":")
			.append(direction.areRequiredStatesExact()).append(',');
		out.append("\"logicalDecisionAvailable\":")
			.append(direction.isLogicalDecisionAvailable()).append(',');
		out.append("\"packedDecisionAvailable\":")
			.append(direction.isPackedDecisionAvailable()).append(',');
		out.append("\"logicalPassable\":");
		appendNullableBoolean(out, direction.getLogicalPassable());
		out.append(",\"packedPassable\":");
		appendNullableBoolean(out, direction.getPackedPassable());
		out.append(",\"logicalBlockingReason\":");
		appendNullableReason(out, direction.getLogicalBlockingReason());
		out.append(",\"packedBlockingReason\":");
		appendNullableReason(out, direction.getPackedBlockingReason());
		out.append(",\"comparable\":").append(direction.isComparable()).append(',');
		out.append("\"passabilityExact\":")
			.append(direction.isPassabilityExact()).append(',');
		out.append("\"blockingReasonExact\":")
			.append(direction.isBlockingReasonExact()).append('}');
	}

	private static void appendRecentTraversal(
		final StringBuilder out,
		final RecentTraversalMetadata traversal) {
		out.append('{');
		out.append("\"source\":");
		appendWorldLocation(out, traversal.getSource());
		out.append(",\"destination\":");
		appendWorldLocation(out, traversal.getDestination());
		out.append(",\"stepCount\":").append(traversal.getStepCount()).append(',');
		out.append("\"droppedStepCount\":")
			.append(traversal.getDroppedStepCount()).append(',');
		out.append("\"discontinuityCount\":")
			.append(traversal.getDiscontinuityCount()).append(',');
		out.append("\"logicalDecisionAvailableCount\":")
			.append(traversal.getLogicalDecisionAvailableCount()).append(',');
		out.append("\"packedDecisionAvailableCount\":")
			.append(traversal.getPackedDecisionAvailableCount()).append(',');
		out.append("\"comparableCount\":")
			.append(traversal.getComparableCount()).append(',');
		out.append("\"passabilityExactCount\":")
			.append(traversal.getPassabilityExactCount()).append(',');
		out.append("\"blockingReasonExactCount\":")
			.append(traversal.getBlockingReasonExactCount()).append(',');
		out.append("\"requiredStatesExactCount\":")
			.append(traversal.getRequiredStatesExactCount()).append(',');
		out.append("\"logicalPassable\":");
		appendNullableBoolean(out, traversal.getLogicalPassable());
		out.append(",\"packedPassable\":");
		appendNullableBoolean(out, traversal.getPackedPassable());
		out.append(",\"comparable\":").append(traversal.isComparable()).append(',');
		out.append("\"passabilityExact\":")
			.append(traversal.isPassabilityExact()).append(',');
		out.append("\"allStepsComparable\":")
			.append(traversal.areAllStepsComparable()).append(',');
		out.append("\"allStepPassabilitiesExact\":")
			.append(traversal.areAllStepPassabilitiesExact()).append(',');
		out.append("\"allStepBlockingReasonsExact\":")
			.append(traversal.areAllStepBlockingReasonsExact()).append(',');
		out.append("\"allRequiredStatesExact\":")
			.append(traversal.areAllRequiredStatesExact()).append(',');
		out.append("\"firstLogicalBlockedStepIndex\":");
		appendNullableInteger(out, traversal.getFirstLogicalBlockedStepIndex());
		out.append(",\"firstPackedBlockedStepIndex\":");
		appendNullableInteger(out, traversal.getFirstPackedBlockedStepIndex());
		out.append(",\"firstPassabilityMismatchStepIndex\":");
		appendNullableInteger(out, traversal.getFirstPassabilityMismatchStepIndex());
		out.append(",\"firstBlockingReasonMismatchStepIndex\":");
		appendNullableInteger(out, traversal.getFirstBlockingReasonMismatchStepIndex());
		out.append(",\"steps\":[");
		boolean first = true;
		for (TraversalStepMetadata step : traversal.getSteps()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			appendTraversalStep(out, step);
		}
		out.append("]}");
	}

	private static void appendTraversalStep(
		final StringBuilder out,
		final TraversalStepMetadata step) {
		out.append('{');
		out.append("\"index\":").append(step.getIndex()).append(',');
		out.append("\"source\":");
		appendWorldLocation(out, step.getSource());
		out.append(",\"offset\":{\"x\":").append(step.getOffsetX())
			.append(",\"y\":").append(step.getOffsetY()).append("},");
		out.append("\"destination\":");
		appendWorldLocation(out, step.getDestination());
		out.append(",\"requiredCellCount\":")
			.append(step.getRequiredCellCount()).append(',');
		out.append("\"exactRequiredStateCount\":")
			.append(step.getExactRequiredStateCount()).append(',');
		out.append("\"requiredStatesExact\":")
			.append(step.areRequiredStatesExact()).append(',');
		out.append("\"logicalDecisionAvailable\":")
			.append(step.isLogicalDecisionAvailable()).append(',');
		out.append("\"packedDecisionAvailable\":")
			.append(step.isPackedDecisionAvailable()).append(',');
		out.append("\"logicalPassable\":");
		appendNullableBoolean(out, step.getLogicalPassable());
		out.append(",\"packedPassable\":");
		appendNullableBoolean(out, step.getPackedPassable());
		out.append(",\"logicalBlockingReason\":");
		appendNullableReason(out, step.getLogicalBlockingReason());
		out.append(",\"packedBlockingReason\":");
		appendNullableReason(out, step.getPackedBlockingReason());
		out.append(",\"comparable\":").append(step.isComparable()).append(',');
		out.append("\"passabilityExact\":")
			.append(step.isPassabilityExact()).append(',');
		out.append("\"blockingReasonExact\":")
			.append(step.isBlockingReasonExact()).append('}');
	}

	private static void appendWorldLocation(
		final StringBuilder out,
		final WorldLocation location) {
		WorldCoordinate coordinate = location.getCoordinate();
		out.append("{\"worldSpace\":\"")
			.append(jsonEscape(location.getWorldSpace().getValue()))
			.append("\",\"x\":").append(coordinate.getX())
			.append(",\"y\":").append(coordinate.getY())
			.append(",\"level\":").append(coordinate.getLevel()).append('}');
	}

	private static void appendNullableInteger(
		final StringBuilder out,
		final Integer value) {
		out.append(value == null ? "null" : value.toString());
	}

	private static void appendNullableBoolean(
		final StringBuilder out,
		final Boolean value) {
		out.append(value == null ? "null" : value.toString());
	}

	private static void appendNullableReason(
		final StringBuilder out,
		final AdjacentBlockingReason reason) {
		if (reason == null) {
			out.append("null");
		} else {
			quoted(out, reason.name());
		}
	}

	private static void appendTileSnapshot(
		final StringBuilder out,
		final TileSnapshotMetadata snapshot) {
		WorldRegionKey key = snapshot.getLogicalRegionKey();
		out.append('{');
		out.append("\"logicalRegion\":{\"worldSpace\":\"")
			.append(jsonEscape(key.getWorldSpace().getValue()))
			.append("\",\"level\":").append(key.getLevel())
			.append(",\"x\":").append(key.getRegionX())
			.append(",\"y\":").append(key.getRegionY()).append("},");
		out.append("\"sourceFragmentCount\":")
			.append(snapshot.getSourceFragmentCount()).append(',');
		out.append("\"missingSourceRegionCount\":")
			.append(snapshot.getMissingSourceRegionCount()).append(',');
		out.append("\"supportedTileCount\":")
			.append(snapshot.getSupportedTileCount()).append(',');
		out.append("\"targetTileCount\":")
			.append(snapshot.getTargetTileCount()).append(',');
		out.append("\"complete\":").append(snapshot.isComplete()).append(',');
		field(out, "fingerprint", snapshot.getFingerprint());
		out.append('}');
	}

	private static void appendPackedCoverage(
		final StringBuilder out,
		final LegacyPackedVisibilityCoverageComparison coverage) {
		out.append('{');
		out.append("\"minPackedRegionX\":").append(coverage.getMinPackedRegionX()).append(',');
		out.append("\"minPackedRegionY\":").append(coverage.getMinPackedRegionY()).append(',');
		out.append("\"maxPackedRegionX\":").append(coverage.getMaxPackedRegionX()).append(',');
		out.append("\"maxPackedRegionY\":").append(coverage.getMaxPackedRegionY()).append(',');
		out.append("\"packedCellCount\":").append(coverage.getPackedCellCount()).append(',');
		out.append("\"unsupportedPackedCellCount\":")
			.append(coverage.getUnsupportedPackedCellCount()).append(',');
		out.append("\"expectedKeyCount\":")
			.append(coverage.getExpectedLogicalKeys().size()).append(',');
		out.append("\"packedCoverageKeyCount\":")
			.append(coverage.getPackedCoverageKeys().size()).append(',');
		out.append("\"missingKeyCount\":")
			.append(coverage.getMissingLogicalKeys().size()).append(',');
		out.append("\"extraKeyCount\":")
			.append(coverage.getExtraPackedCoverageKeys().size()).append(',');
		out.append("\"exact\":").append(coverage.isExactCoverage()).append(',');
		out.append("\"missingKeys\":");
		appendRegionKeys(out, coverage.getMissingLogicalKeys());
		out.append(",\"extraKeys\":");
		appendRegionKeys(out, coverage.getExtraPackedCoverageKeys());
		out.append('}');
	}

	private static void appendRegionKeys(
		final StringBuilder out,
		final Iterable<WorldRegionKey> keys) {
		out.append('[');
		boolean first = true;
		for (WorldRegionKey key : keys) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"worldSpace\":\"")
				.append(jsonEscape(key.getWorldSpace().getValue()))
				.append("\",\"level\":").append(key.getLevel())
				.append(",\"x\":").append(key.getRegionX())
				.append(",\"y\":").append(key.getRegionY()).append('}');
		}
		out.append(']');
	}

	private static StringBuilder field(StringBuilder out, String name, String value) {
		quoted(out, name).append(':');
		return quoted(out, value);
	}

	private static StringBuilder quoted(StringBuilder out, String value) {
		return out.append('"').append(jsonEscape(value)).append('"');
	}

	private static String jsonEscape(String value) {
		StringBuilder out = new StringBuilder(value.length() + 8);
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\b':
					out.append("\\b");
					break;
				case '\f':
					out.append("\\f");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					if (character < 0x20) {
						out.append(String.format("\\u%04x", (int) character));
					} else {
						out.append(character);
					}
			}
		}
		return out.toString();
	}

	private static String sanitizeLabel(String label) {
		if (label == null) {
			throw new NullPointerException("label");
		}
		String trimmed = label.trim();
		if (trimmed.isEmpty() || trimmed.length() > 64 || !trimmed.matches("[A-Za-z0-9._-]+")) {
			throw new IllegalArgumentException(
				"Marker must be 1-64 letters, digits, dots, underscores, or hyphens.");
		}
		return trimmed;
	}

	private static void updateRecentTraversal(
		final TraceState state,
		final String eventType,
		final LayeredCoordinateParitySnapshot from,
		final LayeredCoordinateParitySnapshot to,
		final Boolean teleported) {
		WorldLocation current = to.getLocation();
		if ("start".equals(eventType) || "teleport".equals(eventType)
			|| "login".equals(eventType) || Boolean.TRUE.equals(teleported)) {
			resetRecentTraversal(state, current);
			return;
		}
		if ("move".equals(eventType) && from != null) {
			appendRecentTraversalStep(state, from.getLocation(), current);
			return;
		}
		if (("marker".equals(eventType) || "stop".equals(eventType))
			&& !state.recentTraversal.isEmpty()
			&& !state.recentTraversal.get(state.recentTraversal.size() - 1)
				.equals(current)) {
			state.recentTraversalDiscontinuityCount++;
			restartRecentTraversal(state, current);
		}
	}

	private static void appendRecentTraversalStep(
		final TraceState state,
		final WorldLocation source,
		final WorldLocation destination) {
		WorldCoordinate from = source.getCoordinate();
		WorldCoordinate to = destination.getCoordinate();
		int offsetX = Math.subtractExact(to.getX(), from.getX());
		int offsetY = Math.subtractExact(to.getY(), from.getY());
		boolean adjacent = source.getWorldSpace().equals(destination.getWorldSpace())
			&& from.getLevel() == to.getLevel()
			&& offsetX >= -1 && offsetX <= 1
			&& offsetY >= -1 && offsetY <= 1
			&& (offsetX != 0 || offsetY != 0);
		if (!adjacent) {
			state.recentTraversalDiscontinuityCount++;
			restartRecentTraversal(state, destination);
			return;
		}
		if (state.recentTraversal.isEmpty()) {
			state.recentTraversal.add(source);
		} else if (!state.recentTraversal.get(
			state.recentTraversal.size() - 1).equals(source)) {
			state.recentTraversalDiscontinuityCount++;
			restartRecentTraversal(state, source);
		}
		state.recentTraversal.add(destination);
		if (state.recentTraversal.size() > MAX_TRACE_TRAVERSAL_STEPS + 1) {
			state.recentTraversal.remove(0);
			state.recentTraversalDroppedStepCount++;
		}
	}

	private static void resetRecentTraversal(
		final TraceState state,
		final WorldLocation current) {
		state.recentTraversalDroppedStepCount = 0;
		state.recentTraversalDiscontinuityCount = 0;
		restartRecentTraversal(state, current);
	}

	private static void restartRecentTraversal(
		final TraceState state,
		final WorldLocation current) {
		state.recentTraversal.clear();
		state.recentTraversal.add(current);
	}

	private static boolean capturesTileComparisons(final String eventType) {
		return "start".equals(eventType)
			|| "marker".equals(eventType)
			|| "teleport".equals(eventType)
			|| "stop".equals(eventType);
	}

	private static boolean capturesRecentTraversal(final String eventType) {
		return "marker".equals(eventType) || "stop".equals(eventType);
	}

	private static boolean capturesRegionResidency(
		final String eventType,
		final WorldRegionInterestDelta interestDelta) {
		return !"move".equals(eventType)
			|| (interestDelta != null && !interestDelta.isNoOp());
	}

	private static String safeMessage(Throwable failure) {
		String message = failure.getMessage();
		return message == null ? "no detail" : message.replace('\n', ' ').replace('\r', ' ');
	}

	private static Path logPath(TraceKey key) {
		String configured = System.getProperty(
			LOG_ROOT_PROPERTY, Paths.get("logs", "layered-map-parity").toString());
		Path root = Paths.get(configured).toAbsolutePath().normalize();
		return root.resolve("player-" + key.playerId + '-'
			+ Long.toUnsignedString(key.usernameHash) + ".jsonl");
	}

	static void resetForTests() {
		TRACES.clear();
	}

	/** Supplies one bounded, detached tile-snapshot summary to an active trace. */
	@FunctionalInterface
	public interface TileSnapshotSource {
		TileSnapshotMetadata capture(WorldRegionKey logicalRegionKey);
	}

	/** Supplies one bounded current-tile comparison to selected trace events. */
	@FunctionalInterface
	public interface TileParitySource {
		TileParityMetadata capture(Point current);
	}

	/** Supplies one bounded 3x3 tile-neighborhood summary to selected events. */
	@FunctionalInterface
	public interface TileNeighborhoodSource {
		TileNeighborhoodMetadata capture(Point current);
	}

	/** Supplies all eight adjacent tile-mask comparisons to selected events. */
	@FunctionalInterface
	public interface AdjacentCollisionSource {
		AdjacentCollisionMetadata capture(Point current);
	}

	/** Supplies one bounded comparison for a recently observed walking route. */
	@FunctionalInterface
	public interface TraversalCollisionSource {
		RecentTraversalMetadata capture(
			List<WorldLocation> route,
			int droppedStepCount,
			int discontinuityCount);
	}

	/** Supplies one bounded interest/residency comparison to selected events. */
	@FunctionalInterface
	public interface RegionResidencySource {
		RegionResidencyMetadata capture(
			WorldRegionWindow previousWindow,
			WorldRegionWindow currentWindow,
			int maximumRegionsPerWindow);
	}

	/** Immutable observer-facing aggregate; never a Region load/unload command. */
	public static final class RegionResidencyMetadata {
		private final long mirrorVersion;
		private final int previousRegionCount;
		private final int currentRegionCount;
		private final int enteredCount;
		private final int retainedCount;
		private final int exitedCount;
		private final boolean worldSpaceChanged;
		private final boolean levelChanged;
		private final boolean noOp;
		private final int residentCurrentCount;
		private final int partialCurrentCount;
		private final int missingCurrentCount;
		private final List<RegionResidencyCandidateMetadata> loadCandidates;
		private final List<RegionResidencyCandidateMetadata> releaseCandidates;
		private final List<RegionResidencyCandidateMetadata> unsupportedCurrent;

		private RegionResidencyMetadata(
			final long mirrorVersion,
			final int previousRegionCount,
			final int currentRegionCount,
			final int enteredCount,
			final int retainedCount,
			final int exitedCount,
			final boolean worldSpaceChanged,
			final boolean levelChanged,
			final boolean noOp,
			final int residentCurrentCount,
			final int partialCurrentCount,
			final int missingCurrentCount,
			final List<RegionResidencyCandidateMetadata> loadCandidates,
			final List<RegionResidencyCandidateMetadata> releaseCandidates,
			final List<RegionResidencyCandidateMetadata> unsupportedCurrent) {
			this.mirrorVersion = mirrorVersion;
			this.previousRegionCount = previousRegionCount;
			this.currentRegionCount = currentRegionCount;
			this.enteredCount = enteredCount;
			this.retainedCount = retainedCount;
			this.exitedCount = exitedCount;
			this.worldSpaceChanged = worldSpaceChanged;
			this.levelChanged = levelChanged;
			this.noOp = noOp;
			this.residentCurrentCount = residentCurrentCount;
			this.partialCurrentCount = partialCurrentCount;
			this.missingCurrentCount = missingCurrentCount;
			this.loadCandidates = Collections.unmodifiableList(
				new ArrayList<RegionResidencyCandidateMetadata>(loadCandidates));
			this.releaseCandidates = Collections.unmodifiableList(
				new ArrayList<RegionResidencyCandidateMetadata>(releaseCandidates));
			this.unsupportedCurrent = Collections.unmodifiableList(
				new ArrayList<RegionResidencyCandidateMetadata>(unsupportedCurrent));
		}

		public static RegionResidencyMetadata of(
			final long mirrorVersion,
			final int previousRegionCount,
			final int currentRegionCount,
			final int enteredCount,
			final int retainedCount,
			final int exitedCount,
			final boolean worldSpaceChanged,
			final boolean levelChanged,
			final boolean noOp,
			final int residentCurrentCount,
			final int partialCurrentCount,
			final int missingCurrentCount,
			final List<RegionResidencyCandidateMetadata> loadCandidates,
			final List<RegionResidencyCandidateMetadata> releaseCandidates,
			final List<RegionResidencyCandidateMetadata> unsupportedCurrent) {
			Objects.requireNonNull(loadCandidates, "loadCandidates");
			Objects.requireNonNull(releaseCandidates, "releaseCandidates");
			Objects.requireNonNull(unsupportedCurrent, "unsupportedCurrent");
			if (mirrorVersion < 0L || previousRegionCount < 1 || currentRegionCount < 1
				|| enteredCount < 0 || retainedCount < 0 || exitedCount < 0
				|| enteredCount + retainedCount != currentRegionCount
				|| exitedCount + retainedCount != previousRegionCount
				|| residentCurrentCount < 0 || partialCurrentCount < 0
				|| missingCurrentCount < 0
				|| residentCurrentCount + partialCurrentCount + missingCurrentCount
					+ unsupportedCurrent.size() != currentRegionCount
				|| loadCandidates.size()
					!= partialCurrentCount + missingCurrentCount
				|| releaseCandidates.size() > exitedCount
				|| noOp != (enteredCount == 0 && exitedCount == 0)) {
				throw new IllegalArgumentException(
					"Invalid Region residency aggregate counts");
			}
			Set<WorldRegionKey> candidateKeys = new java.util.HashSet<WorldRegionKey>();
			int partialLoads = 0;
			int missingLoads = 0;
			for (RegionResidencyCandidateMetadata candidate : loadCandidates) {
				requireCandidate(candidate, candidateKeys);
				if (candidate.getInterestState() == RegionInterestState.EXITED
					|| (candidate.getResidencyState() != RegionResidencyState.MISSING
						&& candidate.getResidencyState()
							!= RegionResidencyState.PARTIAL)) {
					throw new IllegalArgumentException("Invalid Region load candidate");
				}
				if (candidate.getResidencyState() == RegionResidencyState.PARTIAL) {
					partialLoads++;
				} else {
					missingLoads++;
				}
			}
			if (partialLoads != partialCurrentCount
				|| missingLoads != missingCurrentCount) {
				throw new IllegalArgumentException(
					"Region load candidate states differ from aggregate counts");
			}
			for (RegionResidencyCandidateMetadata candidate : releaseCandidates) {
				requireCandidate(candidate, candidateKeys);
				if (candidate.getInterestState() != RegionInterestState.EXITED
					|| (candidate.getResidencyState() != RegionResidencyState.RESIDENT
						&& candidate.getResidencyState()
							!= RegionResidencyState.PARTIAL)) {
					throw new IllegalArgumentException("Invalid Region release candidate");
				}
			}
			for (RegionResidencyCandidateMetadata candidate : unsupportedCurrent) {
				requireCandidate(candidate, candidateKeys);
				if (candidate.getInterestState() == RegionInterestState.EXITED
					|| candidate.getResidencyState()
						!= RegionResidencyState.UNSUPPORTED) {
					throw new IllegalArgumentException(
						"Invalid unsupported current Region");
				}
			}
			return new RegionResidencyMetadata(
				mirrorVersion, previousRegionCount, currentRegionCount,
				enteredCount, retainedCount, exitedCount, worldSpaceChanged,
				levelChanged, noOp, residentCurrentCount, partialCurrentCount,
				missingCurrentCount, loadCandidates, releaseCandidates,
				unsupportedCurrent);
		}

		private static void requireCandidate(
			final RegionResidencyCandidateMetadata candidate,
			final Set<WorldRegionKey> candidateKeys) {
			Objects.requireNonNull(candidate, "candidate");
			if (!candidateKeys.add(candidate.getLogicalRegionKey())) {
				throw new IllegalArgumentException(
					"Region residency candidate keys must be unique");
			}
		}

		private void requireMatches(final WorldRegionInterestDelta delta) {
			if (previousRegionCount
					!= delta.getRetained().size() + delta.getExited().size()
				|| currentRegionCount
					!= delta.getEntered().size() + delta.getRetained().size()
				|| enteredCount != delta.getEntered().size()
				|| retainedCount != delta.getRetained().size()
				|| exitedCount != delta.getExited().size()
				|| worldSpaceChanged != delta.changesWorldSpace()
				|| levelChanged != delta.changesLevel()
				|| noOp != delta.isNoOp()) {
				throw new IllegalStateException(
					"Region residency metadata differs from the observed interest delta");
			}
			for (RegionResidencyCandidateMetadata candidate : loadCandidates) {
				requireCandidateInterest(delta, candidate);
			}
			for (RegionResidencyCandidateMetadata candidate : releaseCandidates) {
				if (!delta.getExited().contains(candidate.getLogicalRegionKey())) {
					throw new IllegalStateException(
						"Region release candidate is not an exited interest key");
				}
			}
			for (RegionResidencyCandidateMetadata candidate : unsupportedCurrent) {
				requireCandidateInterest(delta, candidate);
			}
		}

		private static void requireCandidateInterest(
			final WorldRegionInterestDelta delta,
			final RegionResidencyCandidateMetadata candidate) {
			boolean expected = candidate.getInterestState() == RegionInterestState.ENTERED
				? delta.getEntered().contains(candidate.getLogicalRegionKey())
				: candidate.getInterestState() == RegionInterestState.RETAINED
					&& delta.getRetained().contains(candidate.getLogicalRegionKey());
			if (!expected) {
				throw new IllegalStateException(
					"Region candidate interest state differs from the observed delta");
			}
		}

		public long getMirrorVersion() { return mirrorVersion; }
		public int getPreviousRegionCount() { return previousRegionCount; }
		public int getCurrentRegionCount() { return currentRegionCount; }
		public int getEnteredCount() { return enteredCount; }
		public int getRetainedCount() { return retainedCount; }
		public int getExitedCount() { return exitedCount; }
		public boolean isWorldSpaceChanged() { return worldSpaceChanged; }
		public boolean isLevelChanged() { return levelChanged; }
		public boolean isNoOp() { return noOp; }
		public int getResidentCurrentCount() { return residentCurrentCount; }
		public int getPartialCurrentCount() { return partialCurrentCount; }
		public int getMissingCurrentCount() { return missingCurrentCount; }
		public List<RegionResidencyCandidateMetadata> getLoadCandidates() {
			return loadCandidates;
		}
		public List<RegionResidencyCandidateMetadata> getReleaseCandidates() {
			return releaseCandidates;
		}
		public List<RegionResidencyCandidateMetadata> getUnsupportedCurrent() {
			return unsupportedCurrent;
		}
	}

	public enum RegionInterestState {
		ENTERED,
		RETAINED,
		EXITED
	}

	public enum RegionResidencyState {
		RESIDENT,
		PARTIAL,
		MISSING,
		UNSUPPORTED
	}

	/** Immutable AI-readable evidence for one noteworthy logical Region. */
	public static final class RegionResidencyCandidateMetadata {
		private final WorldRegionKey logicalRegionKey;
		private final RegionInterestState interestState;
		private final RegionResidencyState residencyState;
		private final int sourceCount;
		private final int residentSourceCount;
		private final long supportedTileCount;
		private final long residentTileCount;
		private final boolean legacyCoverageComplete;

		private RegionResidencyCandidateMetadata(
			final WorldRegionKey logicalRegionKey,
			final RegionInterestState interestState,
			final RegionResidencyState residencyState,
			final int sourceCount,
			final int residentSourceCount,
			final long supportedTileCount,
			final long residentTileCount,
			final boolean legacyCoverageComplete) {
			this.logicalRegionKey = logicalRegionKey;
			this.interestState = interestState;
			this.residencyState = residencyState;
			this.sourceCount = sourceCount;
			this.residentSourceCount = residentSourceCount;
			this.supportedTileCount = supportedTileCount;
			this.residentTileCount = residentTileCount;
			this.legacyCoverageComplete = legacyCoverageComplete;
		}

		public static RegionResidencyCandidateMetadata of(
			final WorldRegionKey logicalRegionKey,
			final RegionInterestState interestState,
			final RegionResidencyState residencyState,
			final int sourceCount,
			final int residentSourceCount,
			final long supportedTileCount,
			final long residentTileCount,
			final boolean legacyCoverageComplete) {
			Objects.requireNonNull(logicalRegionKey, "logicalRegionKey");
			Objects.requireNonNull(interestState, "interestState");
			Objects.requireNonNull(residencyState, "residencyState");
			if (sourceCount < 0 || residentSourceCount < 0
				|| residentSourceCount > sourceCount || supportedTileCount < 0L
				|| residentTileCount < 0L || residentTileCount > supportedTileCount) {
				throw new IllegalArgumentException("Invalid Region residency candidate counts");
			}
			boolean validState;
			switch (residencyState) {
				case RESIDENT:
					validState = sourceCount > 0 && residentSourceCount == sourceCount
						&& supportedTileCount > 0L
						&& residentTileCount == supportedTileCount;
					break;
				case PARTIAL:
					validState = residentSourceCount > 0
						&& residentSourceCount < sourceCount
						&& residentTileCount > 0L
						&& residentTileCount < supportedTileCount;
					break;
				case MISSING:
					validState = sourceCount > 0 && residentSourceCount == 0
						&& supportedTileCount > 0L && residentTileCount == 0L;
					break;
				case UNSUPPORTED:
					validState = sourceCount == 0 && residentSourceCount == 0
						&& supportedTileCount == 0L && residentTileCount == 0L
						&& !legacyCoverageComplete;
					break;
				default:
					validState = false;
			}
			if (!validState) {
				throw new IllegalArgumentException(
					"Region residency state differs from its source/tile counts");
			}
			return new RegionResidencyCandidateMetadata(
				logicalRegionKey, interestState, residencyState, sourceCount,
				residentSourceCount, supportedTileCount, residentTileCount,
				legacyCoverageComplete);
		}

		public WorldRegionKey getLogicalRegionKey() { return logicalRegionKey; }
		public RegionInterestState getInterestState() { return interestState; }
		public RegionResidencyState getResidencyState() { return residencyState; }
		public int getSourceCount() { return sourceCount; }
		public int getResidentSourceCount() { return residentSourceCount; }
		public int getMissingSourceCount() { return sourceCount - residentSourceCount; }
		public long getSupportedTileCount() { return supportedTileCount; }
		public long getResidentTileCount() { return residentTileCount; }
		public boolean isLegacyCoverageComplete() { return legacyCoverageComplete; }
	}

	/** Immutable observer-facing eight-direction summary; no tile masks. */
	public static final class AdjacentCollisionMetadata {
		public static final int DIRECTION_COUNT = 8;

		private final WorldLocation center;
		private final List<AdjacentDirectionMetadata> directions;

		private AdjacentCollisionMetadata(
			final WorldLocation center,
			final List<AdjacentDirectionMetadata> directions) {
			this.center = center;
			this.directions = Collections.unmodifiableList(
				new ArrayList<AdjacentDirectionMetadata>(directions));
		}

		public static AdjacentCollisionMetadata of(
			final WorldLocation center,
			final List<AdjacentDirectionMetadata> directions) {
			Objects.requireNonNull(center, "center");
			Objects.requireNonNull(directions, "directions");
			if (directions.size() != DIRECTION_COUNT) {
				throw new IllegalArgumentException(
					"Adjacent collision metadata must contain eight directions");
			}
			int index = 0;
			for (int offsetY = -1; offsetY <= 1; offsetY++) {
				for (int offsetX = -1; offsetX <= 1; offsetX++) {
					if (offsetX == 0 && offsetY == 0) {
						continue;
					}
					AdjacentDirectionMetadata direction = Objects.requireNonNull(
						directions.get(index), "directions[" + index + "]");
					if (direction.getOffsetX() != offsetX
						|| direction.getOffsetY() != offsetY) {
						throw new IllegalArgumentException(
							"Adjacent collision direction order mismatch at index " + index);
					}
					WorldCoordinate coordinate = center.getCoordinate();
					WorldLocation expected = new WorldLocation(
						center.getWorldSpace(),
						new WorldCoordinate(
							Math.addExact(coordinate.getX(), offsetX),
							Math.addExact(coordinate.getY(), offsetY),
							coordinate.getLevel()));
					if (!expected.equals(direction.getDestination())) {
						throw new IllegalArgumentException(
							"Adjacent collision destination mismatch at index " + index);
					}
					index++;
				}
			}
			return new AdjacentCollisionMetadata(center, directions);
		}

		public WorldLocation getCenter() {
			return center;
		}

		public List<AdjacentDirectionMetadata> getDirections() {
			return directions;
		}

		public int getLogicalDecisionAvailableCount() {
			return count(DirectionStatus.LOGICAL_AVAILABLE);
		}

		public int getPackedDecisionAvailableCount() {
			return count(DirectionStatus.PACKED_AVAILABLE);
		}

		public int getComparableCount() {
			return count(DirectionStatus.COMPARABLE);
		}

		public int getPassabilityExactCount() {
			return count(DirectionStatus.PASSABILITY_EXACT);
		}

		public int getBlockingReasonExactCount() {
			return count(DirectionStatus.REASON_EXACT);
		}

		public int getRequiredStatesExactCount() {
			return count(DirectionStatus.REQUIRED_STATES_EXACT);
		}

		public boolean isAllComparable() {
			return getComparableCount() == DIRECTION_COUNT;
		}

		public boolean isAllPassabilityExact() {
			return getPassabilityExactCount() == DIRECTION_COUNT;
		}

		public boolean isAllBlockingReasonsExact() {
			return getBlockingReasonExactCount() == DIRECTION_COUNT;
		}

		public boolean isAllRequiredStatesExact() {
			return getRequiredStatesExactCount() == DIRECTION_COUNT;
		}

		private int count(final DirectionStatus status) {
			int count = 0;
			for (AdjacentDirectionMetadata direction : directions) {
				if (status.matches(direction)) {
					count++;
				}
			}
			return count;
		}
	}

	/** Immutable metadata for one adjacent direction. */
	public static final class AdjacentDirectionMetadata {
		private final int offsetX;
		private final int offsetY;
		private final WorldLocation destination;
		private final int requiredCellCount;
		private final int exactRequiredStateCount;
		private final Boolean logicalPassable;
		private final Boolean packedPassable;
		private final AdjacentBlockingReason logicalBlockingReason;
		private final AdjacentBlockingReason packedBlockingReason;

		private AdjacentDirectionMetadata(
			final int offsetX,
			final int offsetY,
			final WorldLocation destination,
			final int requiredCellCount,
			final int exactRequiredStateCount,
			final Boolean logicalPassable,
			final AdjacentBlockingReason logicalBlockingReason,
			final Boolean packedPassable,
			final AdjacentBlockingReason packedBlockingReason) {
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.destination = destination;
			this.requiredCellCount = requiredCellCount;
			this.exactRequiredStateCount = exactRequiredStateCount;
			this.logicalPassable = logicalPassable;
			this.logicalBlockingReason = logicalBlockingReason;
			this.packedPassable = packedPassable;
			this.packedBlockingReason = packedBlockingReason;
		}

		public static AdjacentDirectionMetadata of(
			final int offsetX,
			final int offsetY,
			final WorldLocation destination,
			final int requiredCellCount,
			final int exactRequiredStateCount,
			final Boolean logicalPassable,
			final AdjacentBlockingReason logicalBlockingReason,
			final Boolean packedPassable,
			final AdjacentBlockingReason packedBlockingReason) {
			Objects.requireNonNull(destination, "destination");
			if (offsetX < -1 || offsetX > 1 || offsetY < -1 || offsetY > 1
				|| (offsetX == 0 && offsetY == 0)) {
				throw new IllegalArgumentException("Invalid adjacent collision direction");
			}
			int expectedCells = offsetX == 0 || offsetY == 0 ? 2
				: offsetX == 1 && offsetY == -1 ? 5 : 4;
			if (requiredCellCount != expectedCells
				|| exactRequiredStateCount < 0
				|| exactRequiredStateCount > requiredCellCount) {
				throw new IllegalArgumentException(
					"Adjacent collision required-state counts are inconsistent");
			}
			validateDecision(logicalPassable, logicalBlockingReason, "logical");
			validateDecision(packedPassable, packedBlockingReason, "packed");
			return new AdjacentDirectionMetadata(
				offsetX, offsetY, destination, requiredCellCount,
				exactRequiredStateCount, logicalPassable, logicalBlockingReason,
				packedPassable, packedBlockingReason);
		}

		private static void validateDecision(
			final Boolean passable,
			final AdjacentBlockingReason reason,
			final String label) {
			if ((passable == null) != (reason == null)) {
				throw new IllegalArgumentException(
					label + " adjacent decision availability is inconsistent");
			}
			if (passable != null
				&& (passable.booleanValue()
					!= (reason == AdjacentBlockingReason.NONE))) {
				throw new IllegalArgumentException(
					label + " adjacent passability differs from its reason");
			}
		}

		public int getOffsetX() { return offsetX; }
		public int getOffsetY() { return offsetY; }
		public WorldLocation getDestination() { return destination; }
		public int getRequiredCellCount() { return requiredCellCount; }
		public int getExactRequiredStateCount() { return exactRequiredStateCount; }
		public boolean areRequiredStatesExact() {
			return exactRequiredStateCount == requiredCellCount;
		}
		public boolean isLogicalDecisionAvailable() { return logicalPassable != null; }
		public boolean isPackedDecisionAvailable() { return packedPassable != null; }
		public Boolean getLogicalPassable() { return logicalPassable; }
		public Boolean getPackedPassable() { return packedPassable; }
		public AdjacentBlockingReason getLogicalBlockingReason() {
			return logicalBlockingReason;
		}
		public AdjacentBlockingReason getPackedBlockingReason() {
			return packedBlockingReason;
		}
		public boolean isComparable() {
			return logicalPassable != null && packedPassable != null;
		}
		public boolean isPassabilityExact() {
			return isComparable() && logicalPassable.equals(packedPassable);
		}
		public boolean isBlockingReasonExact() {
			return isComparable() && logicalBlockingReason == packedBlockingReason;
		}
	}

	/** Trace-stable mirror of dormant adjacent collision reasons. */
	public enum AdjacentBlockingReason {
		NONE,
		CURRENT_AXES,
		CURRENT_X,
		CURRENT_Y,
		ADJACENT_AXES,
		ADJACENT_X,
		ADJACENT_Y,
		ADJACENT_X_CORRIDOR,
		ADJACENT_Y_CORRIDOR,
		DESTINATION_AXES,
		DESTINATION_X,
		DESTINATION_Y,
		DESTINATION_X_CORRIDOR,
		DESTINATION_Y_CORRIDOR,
		DESTINATION_DIAGONAL,
		SIDE_DIAGONAL,
		DIAGONAL_PASS_THROUGH
	}

	/** Immutable bounded recent-route summary; no tile masks or payloads. */
	public static final class RecentTraversalMetadata {
		private final List<TraversalStepMetadata> steps;
		private final int droppedStepCount;
		private final int discontinuityCount;

		private RecentTraversalMetadata(
			final List<TraversalStepMetadata> steps,
			final int droppedStepCount,
			final int discontinuityCount) {
			this.steps = Collections.unmodifiableList(
				new ArrayList<TraversalStepMetadata>(steps));
			this.droppedStepCount = droppedStepCount;
			this.discontinuityCount = discontinuityCount;
		}

		public static RecentTraversalMetadata of(
			final List<TraversalStepMetadata> steps,
			final int droppedStepCount,
			final int discontinuityCount) {
			Objects.requireNonNull(steps, "steps");
			if (steps.isEmpty() || steps.size() > MAX_TRACE_TRAVERSAL_STEPS
				|| droppedStepCount < 0 || discontinuityCount < 0) {
				throw new IllegalArgumentException(
					"Recent traversal metadata counts are out of bounds");
			}
			WorldLocation expectedSource = null;
			for (int index = 0; index < steps.size(); index++) {
				TraversalStepMetadata step = Objects.requireNonNull(
					steps.get(index), "steps[" + index + "]");
				if (step.getIndex() != index) {
					throw new IllegalArgumentException(
						"Recent traversal step index mismatch at " + index);
				}
				if (expectedSource != null
					&& !expectedSource.equals(step.getSource())) {
					throw new IllegalArgumentException(
						"Recent traversal is discontinuous at step " + index);
				}
				expectedSource = step.getDestination();
			}
			return new RecentTraversalMetadata(
				steps, droppedStepCount, discontinuityCount);
		}

		public List<TraversalStepMetadata> getSteps() { return steps; }
		public int getStepCount() { return steps.size(); }
		public int getDroppedStepCount() { return droppedStepCount; }
		public int getDiscontinuityCount() { return discontinuityCount; }
		public WorldLocation getSource() { return steps.get(0).getSource(); }
		public WorldLocation getDestination() {
			return steps.get(steps.size() - 1).getDestination();
		}
		public int getLogicalDecisionAvailableCount() {
			return count(TraversalStepStatus.LOGICAL_AVAILABLE);
		}
		public int getPackedDecisionAvailableCount() {
			return count(TraversalStepStatus.PACKED_AVAILABLE);
		}
		public int getComparableCount() {
			return count(TraversalStepStatus.COMPARABLE);
		}
		public int getPassabilityExactCount() {
			return count(TraversalStepStatus.PASSABILITY_EXACT);
		}
		public int getBlockingReasonExactCount() {
			return count(TraversalStepStatus.REASON_EXACT);
		}
		public int getRequiredStatesExactCount() {
			return count(TraversalStepStatus.REQUIRED_STATES_EXACT);
		}
		public Boolean getLogicalPassable() {
			return routePassable(true);
		}
		public Boolean getPackedPassable() {
			return routePassable(false);
		}
		public boolean isComparable() {
			return getLogicalPassable() != null && getPackedPassable() != null;
		}
		public boolean isPassabilityExact() {
			return isComparable()
				&& getLogicalPassable().equals(getPackedPassable());
		}
		public boolean areAllStepsComparable() {
			return getComparableCount() == steps.size();
		}
		public boolean areAllStepPassabilitiesExact() {
			return getPassabilityExactCount() == steps.size();
		}
		public boolean areAllStepBlockingReasonsExact() {
			return getBlockingReasonExactCount() == steps.size();
		}
		public boolean areAllRequiredStatesExact() {
			return getRequiredStatesExactCount() == steps.size();
		}
		public Integer getFirstLogicalBlockedStepIndex() {
			return firstIndex(TraversalStepStatus.LOGICAL_BLOCKED);
		}
		public Integer getFirstPackedBlockedStepIndex() {
			return firstIndex(TraversalStepStatus.PACKED_BLOCKED);
		}
		public Integer getFirstPassabilityMismatchStepIndex() {
			return firstIndex(TraversalStepStatus.PASSABILITY_MISMATCH);
		}
		public Integer getFirstBlockingReasonMismatchStepIndex() {
			return firstIndex(TraversalStepStatus.REASON_MISMATCH);
		}

		private Boolean routePassable(final boolean logical) {
			boolean passable = true;
			for (TraversalStepMetadata step : steps) {
				Boolean decision = logical
					? step.getLogicalPassable() : step.getPackedPassable();
				if (decision == null) {
					return null;
				}
				passable &= decision.booleanValue();
			}
			return Boolean.valueOf(passable);
		}

		private int count(final TraversalStepStatus status) {
			int count = 0;
			for (TraversalStepMetadata step : steps) {
				if (status.matches(step)) {
					count++;
				}
			}
			return count;
		}

		private Integer firstIndex(final TraversalStepStatus status) {
			for (int index = 0; index < steps.size(); index++) {
				if (status.matches(steps.get(index))) {
					return Integer.valueOf(index);
				}
			}
			return null;
		}
	}

	/** Immutable observer-facing decision for one retained traversal step. */
	public static final class TraversalStepMetadata {
		private final int index;
		private final WorldLocation source;
		private final int offsetX;
		private final int offsetY;
		private final WorldLocation destination;
		private final int requiredCellCount;
		private final int exactRequiredStateCount;
		private final Boolean logicalPassable;
		private final Boolean packedPassable;
		private final AdjacentBlockingReason logicalBlockingReason;
		private final AdjacentBlockingReason packedBlockingReason;

		private TraversalStepMetadata(
			final int index,
			final WorldLocation source,
			final int offsetX,
			final int offsetY,
			final WorldLocation destination,
			final int requiredCellCount,
			final int exactRequiredStateCount,
			final Boolean logicalPassable,
			final AdjacentBlockingReason logicalBlockingReason,
			final Boolean packedPassable,
			final AdjacentBlockingReason packedBlockingReason) {
			this.index = index;
			this.source = source;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.destination = destination;
			this.requiredCellCount = requiredCellCount;
			this.exactRequiredStateCount = exactRequiredStateCount;
			this.logicalPassable = logicalPassable;
			this.logicalBlockingReason = logicalBlockingReason;
			this.packedPassable = packedPassable;
			this.packedBlockingReason = packedBlockingReason;
		}

		public static TraversalStepMetadata of(
			final int index,
			final WorldLocation source,
			final int offsetX,
			final int offsetY,
			final WorldLocation destination,
			final int requiredCellCount,
			final int exactRequiredStateCount,
			final Boolean logicalPassable,
			final AdjacentBlockingReason logicalBlockingReason,
			final Boolean packedPassable,
			final AdjacentBlockingReason packedBlockingReason) {
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(destination, "destination");
			if (index < 0 || offsetX < -1 || offsetX > 1
				|| offsetY < -1 || offsetY > 1
				|| (offsetX == 0 && offsetY == 0)) {
				throw new IllegalArgumentException("Invalid traversal step identity");
			}
			WorldCoordinate coordinate = source.getCoordinate();
			WorldLocation expected = new WorldLocation(
				source.getWorldSpace(),
				new WorldCoordinate(
					Math.addExact(coordinate.getX(), offsetX),
					Math.addExact(coordinate.getY(), offsetY),
					coordinate.getLevel()));
			if (!expected.equals(destination)) {
				throw new IllegalArgumentException(
					"Traversal step destination differs from its offset");
			}
			int expectedCells = offsetX == 0 || offsetY == 0 ? 2
				: offsetX == 1 && offsetY == -1 ? 5 : 4;
			if (requiredCellCount != expectedCells
				|| exactRequiredStateCount < 0
				|| exactRequiredStateCount > requiredCellCount) {
				throw new IllegalArgumentException(
					"Traversal step required-state counts are inconsistent");
			}
			validateTraversalDecision(
				logicalPassable, logicalBlockingReason, "logical");
			validateTraversalDecision(
				packedPassable, packedBlockingReason, "packed");
			return new TraversalStepMetadata(
				index, source, offsetX, offsetY, destination,
				requiredCellCount, exactRequiredStateCount,
				logicalPassable, logicalBlockingReason,
				packedPassable, packedBlockingReason);
		}

		private static void validateTraversalDecision(
			final Boolean passable,
			final AdjacentBlockingReason reason,
			final String label) {
			if ((passable == null) != (reason == null)
				|| (passable != null && passable.booleanValue()
					!= (reason == AdjacentBlockingReason.NONE))) {
				throw new IllegalArgumentException(
					label + " traversal decision is inconsistent");
			}
		}

		public int getIndex() { return index; }
		public WorldLocation getSource() { return source; }
		public int getOffsetX() { return offsetX; }
		public int getOffsetY() { return offsetY; }
		public WorldLocation getDestination() { return destination; }
		public int getRequiredCellCount() { return requiredCellCount; }
		public int getExactRequiredStateCount() { return exactRequiredStateCount; }
		public boolean areRequiredStatesExact() {
			return exactRequiredStateCount == requiredCellCount;
		}
		public boolean isLogicalDecisionAvailable() { return logicalPassable != null; }
		public boolean isPackedDecisionAvailable() { return packedPassable != null; }
		public Boolean getLogicalPassable() { return logicalPassable; }
		public Boolean getPackedPassable() { return packedPassable; }
		public AdjacentBlockingReason getLogicalBlockingReason() {
			return logicalBlockingReason;
		}
		public AdjacentBlockingReason getPackedBlockingReason() {
			return packedBlockingReason;
		}
		public boolean isComparable() {
			return logicalPassable != null && packedPassable != null;
		}
		public boolean isPassabilityExact() {
			return isComparable() && logicalPassable.equals(packedPassable);
		}
		public boolean isBlockingReasonExact() {
			return isComparable()
				&& logicalBlockingReason == packedBlockingReason;
		}
	}

	private enum TraversalStepStatus {
		LOGICAL_AVAILABLE {
			boolean matches(TraversalStepMetadata s) {
				return s.isLogicalDecisionAvailable();
			}
		},
		PACKED_AVAILABLE {
			boolean matches(TraversalStepMetadata s) {
				return s.isPackedDecisionAvailable();
			}
		},
		COMPARABLE {
			boolean matches(TraversalStepMetadata s) { return s.isComparable(); }
		},
		PASSABILITY_EXACT {
			boolean matches(TraversalStepMetadata s) {
				return s.isPassabilityExact();
			}
		},
		REASON_EXACT {
			boolean matches(TraversalStepMetadata s) {
				return s.isBlockingReasonExact();
			}
		},
		REQUIRED_STATES_EXACT {
			boolean matches(TraversalStepMetadata s) {
				return s.areRequiredStatesExact();
			}
		},
		LOGICAL_BLOCKED {
			boolean matches(TraversalStepMetadata s) {
				return Boolean.FALSE.equals(s.getLogicalPassable());
			}
		},
		PACKED_BLOCKED {
			boolean matches(TraversalStepMetadata s) {
				return Boolean.FALSE.equals(s.getPackedPassable());
			}
		},
		PASSABILITY_MISMATCH {
			boolean matches(TraversalStepMetadata s) {
				return s.isComparable() && !s.isPassabilityExact();
			}
		},
		REASON_MISMATCH {
			boolean matches(TraversalStepMetadata s) {
				return s.isComparable() && !s.isBlockingReasonExact();
			}
		};

		abstract boolean matches(TraversalStepMetadata step);
	}

	private enum DirectionStatus {
		LOGICAL_AVAILABLE {
			boolean matches(AdjacentDirectionMetadata d) {
				return d.isLogicalDecisionAvailable();
			}
		},
		PACKED_AVAILABLE {
			boolean matches(AdjacentDirectionMetadata d) {
				return d.isPackedDecisionAvailable();
			}
		},
		COMPARABLE {
			boolean matches(AdjacentDirectionMetadata d) { return d.isComparable(); }
		},
		PASSABILITY_EXACT {
			boolean matches(AdjacentDirectionMetadata d) { return d.isPassabilityExact(); }
		},
		REASON_EXACT {
			boolean matches(AdjacentDirectionMetadata d) { return d.isBlockingReasonExact(); }
		},
		REQUIRED_STATES_EXACT {
			boolean matches(AdjacentDirectionMetadata d) { return d.areRequiredStatesExact(); }
		};

		abstract boolean matches(AdjacentDirectionMetadata direction);
	}

	/** Immutable observer-facing neighborhood counts; no tile payloads. */
	public static final class TileNeighborhoodMetadata {
		public static final int CELL_COUNT = 9;

		private final WorldLocation center;
		private final int legacyRepresentableCount;
		private final int packedSourcePresentCount;
		private final int missingPackedSourceCount;
		private final int comparableCount;
		private final int exactCount;
		private final boolean complete;
		private final boolean exact;

		private TileNeighborhoodMetadata(
			final WorldLocation center,
			final int legacyRepresentableCount,
			final int packedSourcePresentCount,
			final int missingPackedSourceCount,
			final int comparableCount,
			final int exactCount,
			final boolean complete,
			final boolean exact) {
			this.center = center;
			this.legacyRepresentableCount = legacyRepresentableCount;
			this.packedSourcePresentCount = packedSourcePresentCount;
			this.missingPackedSourceCount = missingPackedSourceCount;
			this.comparableCount = comparableCount;
			this.exactCount = exactCount;
			this.complete = complete;
			this.exact = exact;
		}

		public static TileNeighborhoodMetadata of(
			final WorldLocation center,
			final int legacyRepresentableCount,
			final int packedSourcePresentCount,
			final int missingPackedSourceCount,
			final int comparableCount,
			final int exactCount,
			final boolean complete,
			final boolean exact) {
			Objects.requireNonNull(center, "center");
			if (legacyRepresentableCount < 0 || legacyRepresentableCount > CELL_COUNT
				|| packedSourcePresentCount < 0
				|| packedSourcePresentCount > legacyRepresentableCount
				|| missingPackedSourceCount < 0
				|| comparableCount < 0 || comparableCount > CELL_COUNT
				|| exactCount < 0 || exactCount > comparableCount) {
				throw new IllegalArgumentException(
					"Tile neighborhood metadata counts are inconsistent");
			}
			if (packedSourcePresentCount + missingPackedSourceCount
				!= legacyRepresentableCount) {
				throw new IllegalArgumentException(
					"Neighborhood source counts differ from representable cells");
			}
			if (comparableCount != packedSourcePresentCount) {
				throw new IllegalArgumentException(
					"Neighborhood comparability differs from present sources");
			}
			if (complete != (legacyRepresentableCount == CELL_COUNT
				&& packedSourcePresentCount == CELL_COUNT)) {
				throw new IllegalArgumentException(
					"Neighborhood completeness differs from source counts");
			}
			if (exact != (comparableCount == CELL_COUNT && exactCount == CELL_COUNT)) {
				throw new IllegalArgumentException(
					"Neighborhood parity differs from exact counts");
			}
			return new TileNeighborhoodMetadata(
				center,
				legacyRepresentableCount,
				packedSourcePresentCount,
				missingPackedSourceCount,
				comparableCount,
				exactCount,
				complete,
				exact);
		}

		public WorldLocation getCenter() {
			return center;
		}

		public int getCellCount() {
			return CELL_COUNT;
		}

		public int getLegacyRepresentableCount() {
			return legacyRepresentableCount;
		}

		public int getUnsupportedCount() {
			return CELL_COUNT - legacyRepresentableCount;
		}

		public int getPackedSourcePresentCount() {
			return packedSourcePresentCount;
		}

		public int getMissingPackedSourceCount() {
			return missingPackedSourceCount;
		}

		public int getComparableCount() {
			return comparableCount;
		}

		public int getExactCount() {
			return exactCount;
		}

		public boolean isComplete() {
			return complete;
		}

		public boolean isExact() {
			return exact;
		}
	}

	/** Immutable observer-facing current-tile parity metadata; no tile payloads. */
	public static final class TileParityMetadata {
		private final WorldLocation logicalLocation;
		private final Point legacyPackedAddress;
		private final boolean packedSourcePresent;
		private final boolean missingPackedSource;
		private final boolean comparable;
		private final boolean exact;

		private TileParityMetadata(
			final WorldLocation logicalLocation,
			final Point legacyPackedAddress,
			final boolean packedSourcePresent,
			final boolean missingPackedSource,
			final boolean comparable,
			final boolean exact) {
			this.logicalLocation = logicalLocation;
			this.legacyPackedAddress = legacyPackedAddress;
			this.packedSourcePresent = packedSourcePresent;
			this.missingPackedSource = missingPackedSource;
			this.comparable = comparable;
			this.exact = exact;
		}

		public static TileParityMetadata of(
			final WorldLocation logicalLocation,
			final Point legacyPackedAddress,
			final boolean packedSourcePresent,
			final boolean missingPackedSource,
			final boolean comparable,
			final boolean exact) {
			Objects.requireNonNull(logicalLocation, "logicalLocation");
			boolean legacyRepresentable = legacyPackedAddress != null;
			if (packedSourcePresent && !legacyRepresentable) {
				throw new IllegalArgumentException(
					"Unsupported logical tile cannot have a packed source");
			}
			if (missingPackedSource != (legacyRepresentable && !packedSourcePresent)) {
				throw new IllegalArgumentException(
					"Missing-source status differs from packed representability");
			}
			if (comparable != packedSourcePresent) {
				throw new IllegalArgumentException(
					"Comparability differs from packed source presence");
			}
			if (exact && !comparable) {
				throw new IllegalArgumentException(
					"An uncomparable tile cannot report exact parity");
			}
			return new TileParityMetadata(
				logicalLocation,
				legacyPackedAddress,
				packedSourcePresent,
				missingPackedSource,
				comparable,
				exact);
		}

		public WorldLocation getLogicalLocation() {
			return logicalLocation;
		}

		/** Returns null when the logical tile has no legacy packed address. */
		public Point getLegacyPackedAddress() {
			return legacyPackedAddress;
		}

		public boolean isLegacyRepresentable() {
			return legacyPackedAddress != null;
		}

		public boolean isPackedSourcePresent() {
			return packedSourcePresent;
		}

		public boolean isMissingPackedSource() {
			return missingPackedSource;
		}

		public boolean isComparable() {
			return comparable;
		}

		public boolean isExact() {
			return exact;
		}
	}

	/** Immutable observer-facing metadata; tile payloads never enter JSONL. */
	public static final class TileSnapshotMetadata {
		private final WorldRegionKey logicalRegionKey;
		private final int sourceFragmentCount;
		private final int missingSourceRegionCount;
		private final int supportedTileCount;
		private final int targetTileCount;
		private final boolean complete;
		private final String fingerprint;

		private TileSnapshotMetadata(
			final WorldRegionKey logicalRegionKey,
			final int sourceFragmentCount,
			final int missingSourceRegionCount,
			final int supportedTileCount,
			final int targetTileCount,
			final boolean complete,
			final String fingerprint) {
			this.logicalRegionKey = logicalRegionKey;
			this.sourceFragmentCount = sourceFragmentCount;
			this.missingSourceRegionCount = missingSourceRegionCount;
			this.supportedTileCount = supportedTileCount;
			this.targetTileCount = targetTileCount;
			this.complete = complete;
			this.fingerprint = fingerprint;
		}

		public static TileSnapshotMetadata of(
			final WorldRegionKey logicalRegionKey,
			final int sourceFragmentCount,
			final int missingSourceRegionCount,
			final int supportedTileCount,
			final int targetTileCount,
			final boolean complete,
			final String fingerprint) {
			Objects.requireNonNull(logicalRegionKey, "logicalRegionKey");
			Objects.requireNonNull(fingerprint, "fingerprint");
			if (sourceFragmentCount < 0
				|| missingSourceRegionCount < 0
				|| missingSourceRegionCount > sourceFragmentCount
				|| supportedTileCount < 0
				|| targetTileCount != WorldRegionKey.REGION_SIZE * WorldRegionKey.REGION_SIZE
				|| supportedTileCount > targetTileCount) {
				throw new IllegalArgumentException(
					"Tile snapshot metadata counts are inconsistent");
			}
			if (complete != (supportedTileCount == targetTileCount)) {
				throw new IllegalArgumentException(
					"Tile snapshot completeness differs from its tile counts");
			}
			if (!fingerprint.matches("[0-9a-f]{64}")) {
				throw new IllegalArgumentException(
					"Tile snapshot fingerprint must be lowercase SHA-256");
			}
			return new TileSnapshotMetadata(
				logicalRegionKey,
				sourceFragmentCount,
				missingSourceRegionCount,
				supportedTileCount,
				targetTileCount,
				complete,
				fingerprint);
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public int getSourceFragmentCount() {
			return sourceFragmentCount;
		}

		public int getMissingSourceRegionCount() {
			return missingSourceRegionCount;
		}

		public int getSupportedTileCount() {
			return supportedTileCount;
		}

		public int getTargetTileCount() {
			return targetTileCount;
		}

		public boolean isComplete() {
			return complete;
		}

		public String getFingerprint() {
			return fingerprint;
		}
	}

	private static final class TraceKey {
		final int playerId;
		final long usernameHash;

		TraceKey(int playerId, long usernameHash) {
			if (playerId < 0) {
				throw new IllegalArgumentException("playerId must be non-negative");
			}
			this.playerId = playerId;
			this.usernameHash = usernameHash;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof TraceKey)) {
				return false;
			}
			TraceKey key = (TraceKey) other;
			return playerId == key.playerId && usernameHash == key.usernameHash;
		}

		@Override
		public int hashCode() {
			return 31 * playerId + (int) (usernameHash ^ (usernameHash >>> 32));
		}
	}

	private static final class TraceState {
		final TraceKey key;
		final Path path;
		final int viewGridDistance;
		final TileSnapshotSource tileSnapshotSource;
		final TileParitySource tileParitySource;
		final TileNeighborhoodSource tileNeighborhoodSource;
		final AdjacentCollisionSource adjacentCollisionSource;
		final TraversalCollisionSource traversalCollisionSource;
		final RegionResidencySource regionResidencySource;
		final List<WorldLocation> recentTraversal =
			new ArrayList<WorldLocation>(MAX_TRACE_TRAVERSAL_STEPS + 1);
		int recentTraversalDroppedStepCount;
		int recentTraversalDiscontinuityCount;
		long sequence;
		LayeredCoordinateParitySnapshot lastSnapshot;
		String lastError;

		TraceState(
			TraceKey key,
			Path path,
			int viewGridDistance,
			TileSnapshotSource tileSnapshotSource,
			TileParitySource tileParitySource,
			TileNeighborhoodSource tileNeighborhoodSource,
			AdjacentCollisionSource adjacentCollisionSource,
			TraversalCollisionSource traversalCollisionSource,
			RegionResidencySource regionResidencySource) {
			if (viewGridDistance < 0) {
				throw new IllegalArgumentException("View grid distance must not be negative");
			}
			Math.multiplyExact(viewGridDistance, 8);
			this.key = key;
			this.path = path;
			this.viewGridDistance = viewGridDistance;
			this.tileSnapshotSource = tileSnapshotSource;
			this.tileParitySource = tileParitySource;
			this.tileNeighborhoodSource = tileNeighborhoodSource;
			this.adjacentCollisionSource = adjacentCollisionSource;
			this.traversalCollisionSource = traversalCollisionSource;
			this.regionResidencySource = regionResidencySource;
		}

		Status status(boolean enabled) {
			return new Status(enabled, path, sequence, lastSnapshot, lastError);
		}
	}

	public static final class Status {
		private final boolean enabled;
		private final Path path;
		private final long recordCount;
		private final LayeredCoordinateParitySnapshot lastSnapshot;
		private final String error;

		private Status(
			boolean enabled,
			Path path,
			long recordCount,
			LayeredCoordinateParitySnapshot lastSnapshot,
			String error) {
			this.enabled = enabled;
			this.path = path;
			this.recordCount = recordCount;
			this.lastSnapshot = lastSnapshot;
			this.error = error;
		}

		static Status disabled(Path path) {
			return new Status(false, path, 0L, null, null);
		}

		Status asDisabled() {
			return new Status(false, path, recordCount, lastSnapshot, error);
		}

		public boolean isEnabled() {
			return enabled;
		}

		public Path getPath() {
			return path;
		}

		public long getRecordCount() {
			return recordCount;
		}

		public LayeredCoordinateParitySnapshot getLastSnapshot() {
			return lastSnapshot;
		}

		public String getError() {
			return error;
		}
	}
}
