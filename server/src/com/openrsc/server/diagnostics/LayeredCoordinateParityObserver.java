package com.openrsc.server.diagnostics;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyPackedVisibilityCoverageComparison;
import com.openrsc.server.model.world.coordinate.LayeredCoordinateParitySnapshot;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcBoundaryRequirementProjection;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcContainmentAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPopulationOutcome;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredProvenanceObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionCohortAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionCohortAttribution;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionTopologyAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionDynamicObjectPreservationRecord;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerEventContinuityAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationBoundaryObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventTargetObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionPreservationBurdenAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementReadiness;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementProposal;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementReassessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementSafetyAssessment;
import com.openrsc.server.model.world.coordinate.LayeredRegionInterestOwnershipLedger;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementDecisionArbiter;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementEligibilityLedger;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionInterestDelta;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;
import com.openrsc.server.model.world.region.LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionAuthoredCollisionVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionAuthoredSourceStateVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionReloadRecipe;
import com.openrsc.server.model.world.region.LayeredPackedRegionSourceAbsencePreflight;
import com.openrsc.server.model.world.region.LayeredPackedRegionTerrainVerificationBatch;
import com.openrsc.server.model.world.region.LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in, non-authoritative JSONL observer for private layered-coordinate parity tests. */
public final class LayeredCoordinateParityObserver {
	public static final String EVENT_SCHEMA = "layered-map-parity-event-v55";
	public static final String PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v54";
	public static final String LOG_ROOT_PROPERTY = "openrsc.layeredParityLogRoot";
	private static final int MAX_TRACE_PACKED_CELLS = 4096;
	private static final int MAX_TRACE_REGIONS_PER_WINDOW = 4096;
	private static final int MAX_TRACE_RETIREMENT_CANDIDATES = 4096;
	private static final int MAX_TRACE_PACKED_RETIREMENT_SOURCES =
		MAX_TRACE_RETIREMENT_CANDIDATES
			* LayeredPackedRegionRetirementReadiness
				.MAX_PACKED_SOURCES_PER_LOGICAL_REGION;
	private static final int MAX_TRACE_RETIREMENT_REGIONS =
		MAX_TRACE_REGIONS_PER_WINDOW * 3;
	private static final int MAX_TRACE_ATTRIBUTION_EDGES =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_ATTRIBUTION_BRIDGE_PLACEMENTS =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_TOPOLOGY_SOURCES =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_TOPOLOGY_RELATIONSHIPS =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_DEPENDENCY_SEMANTICS_SELECTED_SOURCES =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_DEPENDENCY_SEMANTICS_SUPPORT_SOURCES =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_DEPENDENCY_SEMANTICS_INCOMING_OWNERS =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_DEPENDENCY_SEMANTICS_INCOMING_PLACEMENTS =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_ACTIVE_NPC_INSTANCES =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_ACTIVE_NPC_RELEVANT_DETAILS =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_ACTIVE_NPC_BOUNDARY_REQUIREMENTS =
		MAX_TRACE_ACTIVE_NPC_RELEVANT_DETAILS;
	private static final int MAX_TRACE_RETIREMENT_REFINEMENT_CANDIDATES =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_RETIREMENT_REFINEMENT_SUPPORT =
		MAX_TRACE_PACKED_RETIREMENT_SOURCES;
	private static final int MAX_TRACE_DYNAMIC_OBJECT_PRESERVATION_RECORDS =
		LayeredPackedRegionDynamicObjectPreservationRecord.MAXIMUM_DYNAMIC_OBJECTS;
	private static final int MAX_TRACE_EVENT_OWNERSHIP_EVENTS =
		LayeredPackedRegionEventOwnershipInventory.MAXIMUM_EVENTS;
	private static final int MAX_TRACE_EVENT_OWNERSHIP_REFERENCES =
		LayeredPackedRegionEventOwnershipInventory.MAXIMUM_SPATIAL_REFERENCES;
	private static final int MAX_TRACE_EVENT_TARGET_RECORDS =
		LayeredPackedRegionEventTargetObservation.MAXIMUM_TARGET_RECORDS;
	private static final int MAX_TRACE_EVENT_ATOMIC_TARGET_RECORDS =
		LayeredPackedRegionEventAtomicTargetRevalidation.MAXIMUM_RECORDS;
	private static final int MAX_TRACE_EVENT_RECOVERY_CANDIDATES =
		4096;
	private static final int MAX_TRACE_NPC_OWNER_EVENT_CONTINUITY_DETAILS =
		LayeredPackedRegionNpcOwnerEventContinuityAssessment.MAXIMUM_DETAILS;
	private static final int MAX_TRACE_NPC_OWNER_PRESERVATION_OWNERS =
		LayeredPackedRegionNpcOwnerPreservationRequirements
			.MAXIMUM_OWNER_REQUIREMENTS;
	private static final int MAX_TRACE_NPC_OWNER_PRESERVATION_EVENT_LINKS =
		LayeredPackedRegionNpcOwnerPreservationRequirements
			.MAXIMUM_EVENT_LINKS;
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
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			syntheticInterestOwnershipSource());
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, null, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource,
			packedRegionAuthoredReconstructionTopologySource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			packedRegionAuthoredReconstructionDependencySemanticsSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource,
			packedRegionAuthoredReconstructionTopologySource,
			packedRegionAuthoredReconstructionDependencySemanticsSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			packedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			packedRegionActiveNpcResidencySource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource,
			packedRegionAuthoredReconstructionTopologySource,
			packedRegionAuthoredReconstructionDependencySemanticsSource,
			packedRegionActiveNpcResidencySource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			packedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			packedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			packedRegionRetirementRefinementReassessmentSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource,
			packedRegionAuthoredReconstructionTopologySource,
			packedRegionAuthoredReconstructionDependencySemanticsSource,
			packedRegionActiveNpcResidencySource,
			packedRegionRetirementRefinementReassessmentSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			packedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			packedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			packedRegionRetirementRefinementReassessmentSource,
		PackedRegionPreservationBurdenSource
			packedRegionPreservationBurdenSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource,
			packedRegionAuthoredReconstructionTopologySource,
			packedRegionAuthoredReconstructionDependencySemanticsSource,
			packedRegionActiveNpcResidencySource,
			packedRegionRetirementRefinementReassessmentSource,
			packedRegionPreservationBurdenSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			packedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			packedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			packedRegionRetirementRefinementReassessmentSource,
		PackedRegionPreservationBurdenSource
			packedRegionPreservationBurdenSource,
		PackedRegionDynamicObjectPreservationSource
			packedRegionDynamicObjectPreservationSource) {
		return start(
			playerId, usernameHash, current, viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource, packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource,
			packedRegionAuthoredReconstructionTopologySource,
			packedRegionAuthoredReconstructionDependencySemanticsSource,
			packedRegionActiveNpcResidencySource,
			packedRegionRetirementRefinementReassessmentSource,
			packedRegionPreservationBurdenSource,
			packedRegionDynamicObjectPreservationSource, null);
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
		RegionResidencySource regionResidencySource,
		InterestOwnershipSource interestOwnershipSource,
		RegionRetirementSource regionRetirementSource,
		RegionRetirementDecisionSource regionRetirementDecisionSource,
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			packedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			packedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			packedRegionRetirementRefinementReassessmentSource,
		PackedRegionPreservationBurdenSource
			packedRegionPreservationBurdenSource,
		PackedRegionDynamicObjectPreservationSource
			packedRegionDynamicObjectPreservationSource,
		PackedRegionEventOwnershipSource packedRegionEventOwnershipSource) {
		Objects.requireNonNull(tileSnapshotSource, "tileSnapshotSource");
		Objects.requireNonNull(tileParitySource, "tileParitySource");
		Objects.requireNonNull(tileNeighborhoodSource, "tileNeighborhoodSource");
		Objects.requireNonNull(adjacentCollisionSource, "adjacentCollisionSource");
		Objects.requireNonNull(traversalCollisionSource, "traversalCollisionSource");
		Objects.requireNonNull(regionResidencySource, "regionResidencySource");
		Objects.requireNonNull(interestOwnershipSource, "interestOwnershipSource");
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState created = new TraceState(
			key, logPath(key), viewGridDistance, tileSnapshotSource,
			tileParitySource, tileNeighborhoodSource, adjacentCollisionSource,
			traversalCollisionSource, regionResidencySource,
			interestOwnershipSource, regionRetirementSource,
			regionRetirementDecisionSource,
			packedRegionRetirementSafetySource,
			packedRegionAuthoredConstructionSource,
			packedRegionAuthoredProvenanceSource,
			packedRegionAuthoredReconstructionSource,
			packedRegionAuthoredReconstructionCohortSource,
			packedRegionAuthoredReconstructionCohortAttributionSource,
			packedRegionAuthoredReconstructionTopologySource,
			packedRegionAuthoredReconstructionDependencySemanticsSource,
			packedRegionActiveNpcResidencySource,
			packedRegionRetirementRefinementReassessmentSource,
			packedRegionPreservationBurdenSource,
			packedRegionDynamicObjectPreservationSource,
			packedRegionEventOwnershipSource);
		TraceState state = TRACES.putIfAbsent(key, created);
		boolean newlyStarted = state == null;
		if (newlyStarted) {
			state = created;
		} else if (state.viewGridDistance != viewGridDistance) {
			throw new IllegalArgumentException(
				"Active trace view distance does not match the current server configuration");
		}
		return write(
			state, newlyStarted ? "start" : "snapshot", null, current, null, null,
			null);
	}

	public static Status snapshot(int playerId, long usernameHash, Point current) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		return state == null ? Status.disabled(logPath(new TraceKey(playerId, usernameHash)))
			: write(state, "snapshot", null, current, null, null, null);
	}

	public static Status mark(int playerId, long usernameHash, Point current, String label) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		return state == null ? Status.disabled(logPath(new TraceKey(playerId, usernameHash)))
			: write(state, "marker", null, current, null, sanitizeLabel(label), null);
	}

	public static Status recoverNoOp(
		int playerId,
		long usernameHash,
		Point current) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState state = TRACES.get(key);
		return state == null ? Status.disabled(logPath(key))
			: write(state, "recovery-noop", null, current, null, null, null);
	}

	public static Status preserveNoOp(
		int playerId,
		long usernameHash,
		Point current) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState state = TRACES.get(key);
		return state == null ? Status.disabled(logPath(key))
			: write(state, "preservation-noop", null, current, null, null, null);
	}

	public static Status stop(int playerId, long usernameHash, Point current) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState state = TRACES.get(key);
		if (state == null) {
			return Status.disabled(logPath(key));
		}
		Status status = write(state, "stop", null, current, null, null, null);
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
		onLocationChanged(
			playerId, usernameHash, previous, current, teleported, null);
	}

	public static void onLocationChanged(
		int playerId,
		long usernameHash,
		Point previous,
		Point current,
		boolean teleported,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange) {
		if (previous == null || current == null
			|| (previous.getX() == current.getX() && previous.getY() == current.getY())) {
			return;
		}
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		if (state != null) {
			write(state, teleported ? "teleport" : "move", previous, current,
				Boolean.valueOf(teleported), null, ownershipChange);
		}
	}

	public static void onSession(int playerId, long usernameHash, Point current, boolean loggedIn) {
		onSession(playerId, usernameHash, current, loggedIn, null, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource,
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
			null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			currentPackedRegionAuthoredReconstructionTopologySource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource,
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
			currentPackedRegionAuthoredReconstructionTopologySource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			currentPackedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource,
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
			currentPackedRegionAuthoredReconstructionTopologySource,
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
			null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			currentPackedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			currentPackedRegionActiveNpcResidencySource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource,
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
			currentPackedRegionAuthoredReconstructionTopologySource,
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
			currentPackedRegionActiveNpcResidencySource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			currentPackedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			currentPackedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			currentPackedRegionRetirementRefinementReassessmentSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource,
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
			currentPackedRegionAuthoredReconstructionTopologySource,
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
			currentPackedRegionActiveNpcResidencySource,
			currentPackedRegionRetirementRefinementReassessmentSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			currentPackedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			currentPackedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			currentPackedRegionRetirementRefinementReassessmentSource,
		PackedRegionPreservationBurdenSource
			currentPackedRegionPreservationBurdenSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource,
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
			currentPackedRegionAuthoredReconstructionTopologySource,
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
			currentPackedRegionActiveNpcResidencySource,
			currentPackedRegionRetirementRefinementReassessmentSource,
			currentPackedRegionPreservationBurdenSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			currentPackedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			currentPackedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			currentPackedRegionRetirementRefinementReassessmentSource,
		PackedRegionPreservationBurdenSource
			currentPackedRegionPreservationBurdenSource,
		PackedRegionDynamicObjectPreservationSource
			currentPackedRegionDynamicObjectPreservationSource) {
		onSession(
			playerId, usernameHash, current, loggedIn, ownershipChange,
			currentInterestOwnershipSource, currentRegionRetirementSource,
			currentRegionRetirementDecisionSource,
			currentPackedRegionRetirementSafetySource,
			currentPackedRegionAuthoredConstructionSource,
			currentPackedRegionAuthoredProvenanceSource,
			currentPackedRegionAuthoredReconstructionSource,
			currentPackedRegionAuthoredReconstructionCohortSource,
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
			currentPackedRegionAuthoredReconstructionTopologySource,
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
			currentPackedRegionActiveNpcResidencySource,
			currentPackedRegionRetirementRefinementReassessmentSource,
			currentPackedRegionPreservationBurdenSource,
			currentPackedRegionDynamicObjectPreservationSource, null);
	}

	public static void onSession(
		int playerId,
		long usernameHash,
		Point current,
		boolean loggedIn,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		InterestOwnershipSource currentInterestOwnershipSource,
		RegionRetirementSource currentRegionRetirementSource,
		RegionRetirementDecisionSource currentRegionRetirementDecisionSource,
		PackedRegionRetirementSafetySource
			currentPackedRegionRetirementSafetySource,
		PackedRegionAuthoredConstructionSource
			currentPackedRegionAuthoredConstructionSource,
		PackedRegionAuthoredProvenanceSource
			currentPackedRegionAuthoredProvenanceSource,
		PackedRegionAuthoredReconstructionSource
			currentPackedRegionAuthoredReconstructionSource,
		PackedRegionAuthoredReconstructionCohortSource
			currentPackedRegionAuthoredReconstructionCohortSource,
		PackedRegionAuthoredReconstructionCohortAttributionSource
			currentPackedRegionAuthoredReconstructionCohortAttributionSource,
		PackedRegionAuthoredReconstructionTopologySource
			currentPackedRegionAuthoredReconstructionTopologySource,
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			currentPackedRegionAuthoredReconstructionDependencySemanticsSource,
		PackedRegionActiveNpcResidencySource
			currentPackedRegionActiveNpcResidencySource,
		PackedRegionRetirementRefinementReassessmentSource
			currentPackedRegionRetirementRefinementReassessmentSource,
		PackedRegionPreservationBurdenSource
			currentPackedRegionPreservationBurdenSource,
		PackedRegionDynamicObjectPreservationSource
			currentPackedRegionDynamicObjectPreservationSource,
		PackedRegionEventOwnershipSource currentPackedRegionEventOwnershipSource) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		if (state != null && current != null) {
			synchronized (state) {
				if (loggedIn && currentInterestOwnershipSource != null) {
					state.interestOwnershipSource = currentInterestOwnershipSource;
				}
				if (loggedIn && currentRegionRetirementSource != null) {
					state.regionRetirementSource = currentRegionRetirementSource;
				}
				if (loggedIn && currentRegionRetirementDecisionSource != null) {
					state.regionRetirementDecisionSource =
						currentRegionRetirementDecisionSource;
				}
				if (loggedIn
					&& currentPackedRegionRetirementSafetySource != null) {
					state.packedRegionRetirementSafetySource =
						currentPackedRegionRetirementSafetySource;
				}
				if (loggedIn
					&& currentPackedRegionAuthoredConstructionSource != null) {
					state.packedRegionAuthoredConstructionSource =
						currentPackedRegionAuthoredConstructionSource;
				}
				if (loggedIn
					&& currentPackedRegionAuthoredProvenanceSource != null) {
					state.packedRegionAuthoredProvenanceSource =
						currentPackedRegionAuthoredProvenanceSource;
				}
				if (loggedIn
					&& currentPackedRegionAuthoredReconstructionSource != null) {
					state.packedRegionAuthoredReconstructionSource =
						currentPackedRegionAuthoredReconstructionSource;
				}
				if (loggedIn
					&& currentPackedRegionAuthoredReconstructionCohortSource != null) {
					state.packedRegionAuthoredReconstructionCohortSource =
						currentPackedRegionAuthoredReconstructionCohortSource;
				}
				if (loggedIn
					&& currentPackedRegionAuthoredReconstructionCohortAttributionSource
						!= null) {
					state.packedRegionAuthoredReconstructionCohortAttributionSource =
						currentPackedRegionAuthoredReconstructionCohortAttributionSource;
				}
				if (loggedIn
					&& currentPackedRegionAuthoredReconstructionTopologySource
						!= null) {
					state.packedRegionAuthoredReconstructionTopologySource =
						currentPackedRegionAuthoredReconstructionTopologySource;
				}
				if (loggedIn
					&& currentPackedRegionAuthoredReconstructionDependencySemanticsSource
						!= null) {
					state.packedRegionAuthoredReconstructionDependencySemanticsSource =
						currentPackedRegionAuthoredReconstructionDependencySemanticsSource;
				}
				if (loggedIn
					&& currentPackedRegionActiveNpcResidencySource != null) {
					state.packedRegionActiveNpcResidencySource =
						currentPackedRegionActiveNpcResidencySource;
				}
				if (loggedIn
					&& currentPackedRegionRetirementRefinementReassessmentSource
						!= null) {
					state.packedRegionRetirementRefinementReassessmentSource =
						currentPackedRegionRetirementRefinementReassessmentSource;
				}
				if (loggedIn
					&& currentPackedRegionPreservationBurdenSource != null) {
					state.packedRegionPreservationBurdenSource =
						currentPackedRegionPreservationBurdenSource;
				}
				if (loggedIn
					&& currentPackedRegionDynamicObjectPreservationSource != null) {
					state.packedRegionDynamicObjectPreservationSource =
						currentPackedRegionDynamicObjectPreservationSource;
				}
				if (loggedIn && currentPackedRegionEventOwnershipSource != null) {
					state.packedRegionEventOwnershipSource =
						currentPackedRegionEventOwnershipSource;
				}
				write(
					state, loggedIn ? "login" : "logout", null, current, null, null,
					ownershipChange);
			}
		}
	}

	private static Status write(
		TraceState state,
		String eventType,
		Point previous,
		Point current,
		Boolean teleported,
		String label,
		LayeredRegionInterestOwnershipLedger.Change ownershipChange) {
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
				List<WorldRegionKey> retirementTransitionKeys =
					updateRetirementCandidates(state, ownershipChange);
				RegionResidencyMetadata regionResidency = null;
				InterestOwnershipMetadata interestOwnership = null;
				RegionRetirementMetadata regionRetirement = null;
				RegionRetirementDecisionMetadata regionRetirementDecisions = null;
				LayeredPackedRegionRetirementSafetyAssessment
					packedRegionRetirementSafety = null;
				LayeredPackedRegionAuthoredConstructionObservation
					packedRegionAuthoredConstruction = null;
				LayeredPackedRegionAuthoredProvenanceObservation
					packedRegionAuthoredProvenance = null;
				LayeredPackedRegionAuthoredReconstructionObservation
					packedRegionAuthoredReconstruction = null;
				LayeredPackedRegionAuthoredReconstructionCohortAnalysis
					packedRegionAuthoredReconstructionCohort = null;
				LayeredPackedRegionAuthoredReconstructionCohortAttribution
					packedRegionAuthoredReconstructionCohortAttribution = null;
				LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
					packedRegionAuthoredReconstructionTopology = null;
				LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
					packedRegionAuthoredReconstructionDependencySemantics = null;
				LayeredPackedRegionActiveNpcResidencyObservation
					packedRegionActiveNpcResidency = null;
				LayeredPackedRegionActiveNpcContainmentAssessment
					packedRegionActiveNpcContainment = null;
				LayeredPackedRegionActiveNpcBoundaryRequirementProjection
					packedRegionActiveNpcBoundaryRequirements = null;
				LayeredPackedRegionRetirementRefinementProposal
					packedRegionRetirementRefinement = null;
				RetirementRefinementReassessmentMetadata
					packedRegionRetirementRefinementReassessment = null;
				LayeredPackedRegionPreservationBurdenAssessment
					packedRegionPreservationBurden = null;
				LayeredPackedRegionDynamicObjectPreservationRecord
					packedRegionDynamicObjectPreservation = null;
				LayeredPackedRegionEventOwnershipInventory
					packedRegionEventOwnership = null;
				LayeredPackedRegionNpcOwnerEventContinuityAssessment
					packedRegionNpcOwnerEventContinuity = null;
				LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
					packedRegionNpcOwnerPreservationBoundary = null;
				PackedRegionNpcOwnerPreservationNoOpMetadata
					packedRegionNpcOwnerPreservationNoOp = null;
				LayeredPackedRegionEventTargetObservation
					packedRegionEventTargets = null;
				LayeredPackedRegionEventAtomicTargetRevalidation
					packedRegionEventAtomicTargetRevalidation = null;
				PackedRegionEventRecoveryNoOpMetadata
					packedRegionEventRecoveryNoOp = null;
				LayeredPackedRegionRetirementRefinementProposal pendingAtEventStart =
					state.pendingPackedRegionRetirementRefinement;
				LayeredPackedRegionRetirementRefinementProposal
					preservationBurdenProposal = pendingAtEventStart;
				LayeredPackedRegionRetirementRefinementProposal pendingAfterEvent =
					pendingAtEventStart;
				boolean hadPendingRefinementAtEventStart = pendingAtEventStart != null;
				if (pendingAtEventStart != null
					&& state.packedRegionRetirementRefinementReassessmentSource
						!= null) {
					LayeredPackedRegionRetirementRefinementReassessment reassessment =
						state.packedRegionRetirementRefinementReassessmentSource
							.captureIfFresh(
								pendingAtEventStart,
								MAX_TRACE_RETIREMENT_REFINEMENT_CANDIDATES,
								MAX_TRACE_RETIREMENT_REFINEMENT_SUPPORT,
								MAX_TRACE_ACTIVE_NPC_INSTANCES,
								MAX_TRACE_ACTIVE_NPC_RELEVANT_DETAILS,
								MAX_TRACE_ACTIVE_NPC_BOUNDARY_REQUIREMENTS);
					if (reassessment == null) {
						packedRegionRetirementRefinementReassessment =
							RetirementRefinementReassessmentMetadata.deferred(
								pendingAtEventStart);
					} else {
						preservationBurdenProposal = reassessment.getNextProposal();
						boolean retainNext =
							!reassessment.isRefinementConvergedAtObservation();
						pendingAfterEvent = retainNext
							? reassessment.getNextProposal() : null;
						packedRegionRetirementRefinementReassessment =
							RetirementRefinementReassessmentMetadata.observed(
								pendingAtEventStart, reassessment, retainNext);
					}
				}
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
				if (capturesInterestOwnership(eventType, interestDelta)) {
					interestOwnership = ownershipChange == null
						? Objects.requireNonNull(
							state.interestOwnershipSource.capture(
								to.getVisibilityWindow(),
								MAX_TRACE_REGIONS_PER_WINDOW),
							"interestOwnershipSource result")
						: InterestOwnershipMetadata.fromChange(ownershipChange);
					interestOwnership.requireMatches(
						eventType, from == null ? null : from.getVisibilityWindow(),
						to.getVisibilityWindow(), ownershipChange != null);
				}
				if (state.regionRetirementSource != null
					&& capturesRegionRetirement(eventType, interestDelta)) {
					List<WorldRegionKey> trackedCandidates =
						Collections.unmodifiableList(new ArrayList<WorldRegionKey>(
							state.retirementCandidates));
					regionRetirement = Objects.requireNonNull(
						state.regionRetirementSource.capture(
							Collections.unmodifiableList(retirementTransitionKeys),
							trackedCandidates, state.retirementCandidateDroppedCount,
							MAX_TRACE_RETIREMENT_REGIONS),
						"regionRetirementSource result");
					regionRetirement.requireMatches(
						retirementTransitionKeys, trackedCandidates,
						state.retirementCandidateDroppedCount);
				}
				if (regionRetirement != null
					&& state.regionRetirementDecisionSource != null) {
					List<LayeredRegionRetirementEligibilityLedger.Snapshot>
						decisionCandidates = updateRetirementDecisionCandidates(
							state, regionRetirement);
					regionRetirementDecisions = Objects.requireNonNull(
						state.regionRetirementDecisionSource.capture(
							decisionCandidates,
							state.retirementDecisionCandidateDroppedCount,
							MAX_TRACE_RETIREMENT_CANDIDATES),
						"regionRetirementDecisionSource result");
					regionRetirementDecisions.requireMatches(
						decisionCandidates,
						state.retirementDecisionCandidateDroppedCount);
					if (state.packedRegionRetirementSafetySource != null) {
						packedRegionRetirementSafety = Objects.requireNonNull(
							state.packedRegionRetirementSafetySource.capture(
								regionRetirementDecisions.getPackedSourceReadiness(),
								MAX_TRACE_PACKED_RETIREMENT_SOURCES),
							"packedRegionRetirementSafetySource result");
						if (state.packedRegionAuthoredConstructionSource != null) {
							packedRegionAuthoredConstruction = Objects.requireNonNull(
								state.packedRegionAuthoredConstructionSource.capture(
									packedRegionRetirementSafety,
									MAX_TRACE_PACKED_RETIREMENT_SOURCES),
								"packedRegionAuthoredConstructionSource result");
						}
						if (state.packedRegionAuthoredProvenanceSource != null) {
							packedRegionAuthoredProvenance = Objects.requireNonNull(
								state.packedRegionAuthoredProvenanceSource.capture(
									packedRegionRetirementSafety),
								"packedRegionAuthoredProvenanceSource result");
						}
						if (state.packedRegionAuthoredReconstructionSource != null) {
							packedRegionAuthoredReconstruction = Objects.requireNonNull(
								state.packedRegionAuthoredReconstructionSource.capture(
									packedRegionRetirementSafety,
									MAX_TRACE_PACKED_RETIREMENT_SOURCES,
									MAX_TRACE_PACKED_RETIREMENT_SOURCES),
								"packedRegionAuthoredReconstructionSource result");
						}
						if (state.packedRegionAuthoredReconstructionCohortSource
							!= null) {
							packedRegionAuthoredReconstructionCohort =
								Objects.requireNonNull(
									state.packedRegionAuthoredReconstructionCohortSource
										.capture(
											packedRegionRetirementSafety,
											MAX_TRACE_PACKED_RETIREMENT_SOURCES,
											MAX_TRACE_PACKED_RETIREMENT_SOURCES),
									"packedRegionAuthoredReconstructionCohortSource result");
						}
						if (packedRegionAuthoredReconstructionCohort != null
							&& state
								.packedRegionAuthoredReconstructionCohortAttributionSource
								!= null) {
							packedRegionAuthoredReconstructionCohortAttribution =
								Objects.requireNonNull(
									state
										.packedRegionAuthoredReconstructionCohortAttributionSource
										.capture(
											packedRegionAuthoredReconstructionCohort,
											MAX_TRACE_ATTRIBUTION_EDGES,
											MAX_TRACE_ATTRIBUTION_BRIDGE_PLACEMENTS),
									"packedRegionAuthoredReconstructionCohortAttributionSource result");
						}
						if (packedRegionAuthoredReconstructionCohort != null
							&& state.packedRegionAuthoredReconstructionTopologySource
								!= null) {
							packedRegionAuthoredReconstructionTopology =
								Objects.requireNonNull(
									state.packedRegionAuthoredReconstructionTopologySource
										.capture(
											packedRegionAuthoredReconstructionCohort,
											MAX_TRACE_TOPOLOGY_SOURCES,
											MAX_TRACE_TOPOLOGY_RELATIONSHIPS),
									"packedRegionAuthoredReconstructionTopologySource result");
						}
						if (state
							.packedRegionAuthoredReconstructionDependencySemanticsSource
							!= null) {
							packedRegionAuthoredReconstructionDependencySemantics =
								Objects.requireNonNull(
									state
										.packedRegionAuthoredReconstructionDependencySemanticsSource
										.capture(
											packedRegionRetirementSafety,
											MAX_TRACE_DEPENDENCY_SEMANTICS_SELECTED_SOURCES,
											MAX_TRACE_DEPENDENCY_SEMANTICS_SUPPORT_SOURCES,
											MAX_TRACE_DEPENDENCY_SEMANTICS_INCOMING_OWNERS,
											MAX_TRACE_DEPENDENCY_SEMANTICS_INCOMING_PLACEMENTS),
									"packedRegionAuthoredReconstructionDependencySemanticsSource result");
						}
						if (state.packedRegionActiveNpcResidencySource != null) {
							packedRegionActiveNpcResidency = Objects.requireNonNull(
								state.packedRegionActiveNpcResidencySource.capture(
									packedRegionRetirementSafety,
									MAX_TRACE_ACTIVE_NPC_INSTANCES,
									MAX_TRACE_ACTIVE_NPC_RELEVANT_DETAILS),
								"packedRegionActiveNpcResidencySource result");
							packedRegionActiveNpcContainment =
								LayeredPackedRegionActiveNpcContainmentAssessment.assess(
									packedRegionActiveNpcResidency);
							packedRegionActiveNpcBoundaryRequirements =
								LayeredPackedRegionActiveNpcBoundaryRequirementProjection
									.project(
										packedRegionActiveNpcResidency,
										MAX_TRACE_ACTIVE_NPC_BOUNDARY_REQUIREMENTS);
							if (packedRegionAuthoredReconstructionCohort != null) {
								packedRegionRetirementRefinement =
									LayeredPackedRegionRetirementRefinementProposal
										.propose(
											packedRegionRetirementSafety,
											packedRegionAuthoredReconstructionCohort,
											packedRegionActiveNpcBoundaryRequirements,
											MAX_TRACE_RETIREMENT_REFINEMENT_CANDIDATES,
											MAX_TRACE_RETIREMENT_REFINEMENT_SUPPORT);
							}
						}
						}
					}
				if (!hadPendingRefinementAtEventStart
					&& packedRegionRetirementRefinement != null
					&& packedRegionRetirementRefinement.getCandidateSourceCount() > 0) {
					pendingAfterEvent = packedRegionRetirementRefinement;
					preservationBurdenProposal = packedRegionRetirementRefinement;
				}
				if (preservationBurdenProposal != null
					&& state.packedRegionPreservationBurdenSource != null) {
					packedRegionPreservationBurden = Objects.requireNonNull(
						state.packedRegionPreservationBurdenSource.capture(
							preservationBurdenProposal,
							MAX_TRACE_RETIREMENT_REFINEMENT_CANDIDATES),
						"packedRegionPreservationBurdenSource result");
					requirePreservationBurdenMatchesProposal(
						preservationBurdenProposal,
						packedRegionPreservationBurden);
				}
				if (preservationBurdenProposal != null
					&& state.packedRegionDynamicObjectPreservationSource != null) {
					packedRegionDynamicObjectPreservation = Objects.requireNonNull(
						state.packedRegionDynamicObjectPreservationSource.capture(
							preservationBurdenProposal,
							MAX_TRACE_RETIREMENT_REFINEMENT_CANDIDATES,
							MAX_TRACE_DYNAMIC_OBJECT_PRESERVATION_RECORDS),
						"packedRegionDynamicObjectPreservationSource result");
					requireDynamicObjectPreservationMatchesProposal(
						preservationBurdenProposal,
						packedRegionDynamicObjectPreservation);
				}
				if (preservationBurdenProposal != null
					&& state.packedRegionEventOwnershipSource != null) {
					packedRegionEventOwnership = Objects.requireNonNull(
						state.packedRegionEventOwnershipSource.capture(
							preservationBurdenProposal,
							MAX_TRACE_EVENT_OWNERSHIP_EVENTS,
							MAX_TRACE_EVENT_OWNERSHIP_REFERENCES),
						"packedRegionEventOwnershipSource result");
					requireEventOwnershipMatchesProposal(
						preservationBurdenProposal, packedRegionEventOwnership);
					packedRegionNpcOwnerEventContinuity =
						state.packedRegionEventOwnershipSource
							.captureNpcOwnerContinuity(
								preservationBurdenProposal,
								packedRegionEventOwnership,
								MAX_TRACE_RETIREMENT_REFINEMENT_CANDIDATES,
								MAX_TRACE_ACTIVE_NPC_INSTANCES,
								MAX_TRACE_ACTIVE_NPC_RELEVANT_DETAILS,
								MAX_TRACE_NPC_OWNER_EVENT_CONTINUITY_DETAILS);
					if (packedRegionNpcOwnerEventContinuity != null) {
						LayeredPackedRegionNpcOwnerPreservationRequirements
							preservationRequirements =
								LayeredPackedRegionNpcOwnerPreservationRequirements
									.derive(
										packedRegionEventOwnership,
										packedRegionNpcOwnerEventContinuity,
										MAX_TRACE_NPC_OWNER_PRESERVATION_OWNERS,
										MAX_TRACE_NPC_OWNER_PRESERVATION_EVENT_LINKS);
						packedRegionNpcOwnerPreservationBoundary =
							Objects.requireNonNull(
								state.packedRegionEventOwnershipSource
									.captureNpcOwnerPreservationBoundary(
										preservationRequirements,
										MAX_TRACE_NPC_OWNER_PRESERVATION_OWNERS),
								"packedRegionEventOwnershipSource NPC owner preservation boundary");
						if ("preservation-noop".equals(eventType)) {
							packedRegionNpcOwnerPreservationNoOp =
								Objects.requireNonNull(
									state.packedRegionEventOwnershipSource
										.captureNpcOwnerPreservationNoOp(
											preservationRequirements,
											MAX_TRACE_NPC_OWNER_PRESERVATION_OWNERS),
									"packedRegionEventOwnershipSource NPC owner preservation no-op");
						}
					}
					packedRegionEventTargets =
						state.packedRegionEventOwnershipSource.captureTargets(
							packedRegionEventOwnership,
							MAX_TRACE_EVENT_TARGET_RECORDS);
					if (packedRegionEventTargets != null) {
						requireEventTargetsMatchInventory(
							packedRegionEventOwnership,
							packedRegionEventTargets);
					}
					packedRegionEventAtomicTargetRevalidation =
						state.packedRegionEventOwnershipSource
							.captureAtomicTargetRevalidation(
								packedRegionEventOwnership,
								MAX_TRACE_EVENT_ATOMIC_TARGET_RECORDS);
					if (packedRegionEventAtomicTargetRevalidation != null) {
						requireAtomicEventTargetsMatchInventory(
							packedRegionEventOwnership,
							packedRegionEventAtomicTargetRevalidation);
					}
					if ("recovery-noop".equals(eventType)) {
						packedRegionEventRecoveryNoOp = Objects.requireNonNull(
							state.packedRegionEventOwnershipSource
								.captureRecoveryNoOp(
									packedRegionEventOwnership,
									MAX_TRACE_EVENT_RECOVERY_CANDIDATES),
							"packedRegionEventOwnershipSource recovery result");
					}
				}
				long nextSequence = state.sequence + 1L;
				String line = eventJson(
					state.key, nextSequence, System.currentTimeMillis(), eventType,
					teleported, label, from, to, interestDelta, coverage, tileSnapshot,
					tileParity, tileNeighborhood, adjacentCollision, recentTraversal,
					regionResidency, interestOwnership, regionRetirement,
					regionRetirementDecisions, packedRegionRetirementSafety,
					packedRegionAuthoredConstruction,
					packedRegionAuthoredProvenance,
					packedRegionAuthoredReconstruction,
					packedRegionAuthoredReconstructionCohort,
					packedRegionAuthoredReconstructionCohortAttribution,
					packedRegionAuthoredReconstructionTopology,
					packedRegionAuthoredReconstructionDependencySemantics,
					packedRegionActiveNpcResidency,
					packedRegionActiveNpcContainment,
					packedRegionActiveNpcBoundaryRequirements,
					packedRegionRetirementRefinement,
					packedRegionRetirementRefinementReassessment,
					packedRegionPreservationBurden,
					packedRegionDynamicObjectPreservation,
					packedRegionEventOwnership,
					packedRegionNpcOwnerEventContinuity,
					packedRegionNpcOwnerPreservationBoundary,
					packedRegionNpcOwnerPreservationNoOp,
					packedRegionEventTargets,
					packedRegionEventAtomicTargetRevalidation,
					packedRegionEventRecoveryNoOp);
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
				if (regionRetirement != null) {
					pruneCanceledRetirementCandidates(state, regionRetirement);
				}
				if (regionRetirementDecisions != null) {
					pruneRefusedRetirementDecisionCandidates(
						state, regionRetirementDecisions);
				}
				state.pendingPackedRegionRetirementRefinement = pendingAfterEvent;
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
		RegionResidencyMetadata regionResidency,
		InterestOwnershipMetadata interestOwnership,
		RegionRetirementMetadata regionRetirement,
		RegionRetirementDecisionMetadata regionRetirementDecisions,
		LayeredPackedRegionRetirementSafetyAssessment
			packedRegionRetirementSafety,
		LayeredPackedRegionAuthoredConstructionObservation
			packedRegionAuthoredConstruction,
		LayeredPackedRegionAuthoredProvenanceObservation
			packedRegionAuthoredProvenance,
		LayeredPackedRegionAuthoredReconstructionObservation
			packedRegionAuthoredReconstruction,
		LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			packedRegionAuthoredReconstructionCohort,
		LayeredPackedRegionAuthoredReconstructionCohortAttribution
			packedRegionAuthoredReconstructionCohortAttribution,
		LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
			packedRegionAuthoredReconstructionTopology,
		LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
			packedRegionAuthoredReconstructionDependencySemantics,
		LayeredPackedRegionActiveNpcResidencyObservation
			packedRegionActiveNpcResidency,
		LayeredPackedRegionActiveNpcContainmentAssessment
			packedRegionActiveNpcContainment,
		LayeredPackedRegionActiveNpcBoundaryRequirementProjection
			packedRegionActiveNpcBoundaryRequirements,
		LayeredPackedRegionRetirementRefinementProposal
			packedRegionRetirementRefinement,
		RetirementRefinementReassessmentMetadata
			packedRegionRetirementRefinementReassessment,
		LayeredPackedRegionPreservationBurdenAssessment
			packedRegionPreservationBurden,
		LayeredPackedRegionDynamicObjectPreservationRecord
			packedRegionDynamicObjectPreservation,
		LayeredPackedRegionEventOwnershipInventory packedRegionEventOwnership,
		LayeredPackedRegionNpcOwnerEventContinuityAssessment
			packedRegionNpcOwnerEventContinuity,
		LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
			packedRegionNpcOwnerPreservationBoundary,
		PackedRegionNpcOwnerPreservationNoOpMetadata
			packedRegionNpcOwnerPreservationNoOp,
		LayeredPackedRegionEventTargetObservation packedRegionEventTargets,
		LayeredPackedRegionEventAtomicTargetRevalidation
			packedRegionEventAtomicTargetRevalidation,
		PackedRegionEventRecoveryNoOpMetadata
			packedRegionEventRecoveryNoOp) {
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
		out.append(",\"interestOwnership\":");
		if (interestOwnership == null) {
			out.append("null");
		} else {
			appendInterestOwnership(out, interestOwnership);
		}
		out.append(",\"regionRetirement\":");
		if (regionRetirement == null) {
			out.append("null");
		} else {
			appendRegionRetirement(out, regionRetirement);
		}
		out.append(",\"regionRetirementDecisions\":");
		if (regionRetirementDecisions == null) {
			out.append("null");
		} else {
			appendRegionRetirementDecisions(out, regionRetirementDecisions);
		}
		out.append(",\"packedRegionRetirementReadiness\":");
		if (regionRetirementDecisions == null) {
			out.append("null");
		} else {
			appendPackedRegionRetirementReadiness(
				out, regionRetirementDecisions.getPackedSourceReadiness());
		}
		out.append(",\"packedRegionRetirementSafety\":");
		if (packedRegionRetirementSafety == null) {
			out.append("null");
		} else {
			appendPackedRegionRetirementSafety(
				out, packedRegionRetirementSafety);
		}
		out.append(",\"packedRegionAuthoredConstruction\":");
		if (packedRegionAuthoredConstruction == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredConstruction(
				out, packedRegionAuthoredConstruction);
		}
		out.append(",\"packedRegionAuthoredProvenance\":");
		if (packedRegionAuthoredProvenance == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredProvenance(
				out, packedRegionAuthoredProvenance);
		}
		out.append(",\"packedRegionAuthoredReconstruction\":");
		if (packedRegionAuthoredReconstruction == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredReconstruction(
				out, packedRegionAuthoredReconstruction);
		}
		out.append(",\"packedRegionAuthoredReconstructionCohort\":");
		if (packedRegionAuthoredReconstructionCohort == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredReconstructionCohort(
				out, packedRegionAuthoredReconstructionCohort);
		}
		out.append(",\"packedRegionAuthoredReconstructionCohortAttribution\":");
		if (packedRegionAuthoredReconstructionCohortAttribution == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredReconstructionCohortAttribution(
				out, packedRegionAuthoredReconstructionCohortAttribution);
		}
		out.append(",\"packedRegionAuthoredReconstructionTopology\":");
		if (packedRegionAuthoredReconstructionTopology == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredReconstructionTopology(
				out, packedRegionAuthoredReconstructionTopology);
		}
		out.append(",\"packedRegionAuthoredReconstructionDependencySemantics\":");
		if (packedRegionAuthoredReconstructionDependencySemantics == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredReconstructionDependencySemantics(
				out, packedRegionAuthoredReconstructionDependencySemantics);
		}
		out.append(",\"packedRegionActiveNpcResidency\":");
		if (packedRegionActiveNpcResidency == null) {
			out.append("null");
		} else {
			appendPackedRegionActiveNpcResidency(
				out, packedRegionActiveNpcResidency);
		}
		out.append(",\"packedRegionActiveNpcContainment\":");
		if (packedRegionActiveNpcContainment == null) {
			out.append("null");
		} else {
			appendPackedRegionActiveNpcContainment(
				out, packedRegionActiveNpcContainment);
		}
		out.append(",\"packedRegionActiveNpcBoundaryRequirements\":");
		if (packedRegionActiveNpcBoundaryRequirements == null) {
			out.append("null");
		} else {
			appendPackedRegionActiveNpcBoundaryRequirements(
				out, packedRegionActiveNpcBoundaryRequirements);
		}
		out.append(",\"packedRegionRetirementRefinement\":");
		if (packedRegionRetirementRefinement == null) {
			out.append("null");
		} else {
			appendPackedRegionRetirementRefinement(
				out, packedRegionRetirementRefinement);
		}
		out.append(",\"packedRegionRetirementRefinementReassessment\":");
		if (packedRegionRetirementRefinementReassessment == null) {
			out.append("null");
		} else {
			appendPackedRegionRetirementRefinementReassessment(
				out, packedRegionRetirementRefinementReassessment);
		}
		out.append(",\"packedRegionPreservationBurden\":");
		if (packedRegionPreservationBurden == null) {
			out.append("null");
		} else {
			appendPackedRegionPreservationBurden(
				out, packedRegionPreservationBurden);
		}
		out.append(",\"packedRegionDynamicObjectPreservation\":");
		if (packedRegionDynamicObjectPreservation == null) {
			out.append("null");
		} else {
			appendPackedRegionDynamicObjectPreservation(
				out, packedRegionDynamicObjectPreservation);
		}
		out.append(",\"packedRegionEventOwnership\":");
		if (packedRegionEventOwnership == null) {
			out.append("null");
		} else {
			appendPackedRegionEventOwnership(out, packedRegionEventOwnership);
		}
		out.append(",\"packedRegionNpcOwnerEventContinuity\":");
		if (packedRegionNpcOwnerEventContinuity == null) {
			out.append("null");
		} else {
			appendPackedRegionNpcOwnerEventContinuity(
				out, packedRegionNpcOwnerEventContinuity);
		}
		out.append(",\"packedRegionNpcOwnerPreservationBoundary\":");
		if (packedRegionNpcOwnerPreservationBoundary == null) {
			out.append("null");
		} else {
			appendPackedRegionNpcOwnerPreservationBoundary(
				out, packedRegionNpcOwnerPreservationBoundary);
		}
		out.append(",\"packedRegionNpcOwnerPreservationNoOp\":");
		if (packedRegionNpcOwnerPreservationNoOp == null) {
			out.append("null");
		} else {
			appendPackedRegionNpcOwnerPreservationNoOp(
				out, packedRegionNpcOwnerPreservationNoOp);
		}
		out.append(",\"packedRegionEventTargets\":");
		if (packedRegionEventTargets == null) {
			out.append("null");
		} else {
			appendPackedRegionEventTargets(out, packedRegionEventTargets);
		}
		out.append(",\"packedRegionEventAtomicTargetRevalidation\":");
		if (packedRegionEventAtomicTargetRevalidation == null) {
			out.append("null");
		} else {
			appendPackedRegionEventAtomicTargetRevalidation(
				out, packedRegionEventAtomicTargetRevalidation);
		}
		out.append(",\"packedRegionEventRecoveryNoOp\":");
		if (packedRegionEventRecoveryNoOp == null) {
			out.append("null");
		} else {
			appendPackedRegionEventRecoveryNoOp(
				out, packedRegionEventRecoveryNoOp);
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

	private static void appendInterestOwnership(
		final StringBuilder out,
		final InterestOwnershipMetadata ownership) {
		out.append('{');
		out.append("\"ledgerVersion\":")
			.append(ownership.getLedgerVersion()).append(',');
		out.append("\"ownerSequence\":")
			.append(ownership.getOwnerSequence()).append(',');
		out.append("\"ownerOpen\":").append(ownership.isOwnerOpen()).append(',');
		out.append("\"openOwnerCount\":")
			.append(ownership.getOpenOwnerCount()).append(',');
		out.append("\"referencedRegionCount\":")
			.append(ownership.getReferencedRegionCount()).append(',');
		out.append("\"ownedRegionCount\":")
			.append(ownership.getOwnedRegionCount()).append(',');
		out.append("\"minimumReferenceCount\":");
		appendNullableInteger(out, ownership.getMinimumReferenceCount());
		out.append(",\"maximumReferenceCount\":");
		appendNullableInteger(out, ownership.getMaximumReferenceCount());
		out.append(",\"enteredCount\":").append(ownership.getEnteredCount());
		out.append(",\"retainedCount\":").append(ownership.getRetainedCount());
		out.append(",\"exitedCount\":").append(ownership.getExitedCount());
		out.append(",\"globallyAcquiredCount\":")
			.append(ownership.getGloballyAcquiredCount());
		out.append(",\"sharedAcquisitionCount\":")
			.append(ownership.getSharedAcquisitionCount());
		out.append(",\"globallyReleasedCount\":")
			.append(ownership.getGloballyReleasedCount());
		out.append(",\"sharedReleaseCount\":")
			.append(ownership.getSharedReleaseCount());
		out.append(",\"noOp\":").append(ownership.isNoOp());
		out.append(",\"transitions\":");
		appendInterestOwnershipTransitions(out, ownership.getTransitions());
		out.append('}');
	}

	private static void appendInterestOwnershipTransitions(
		final StringBuilder out,
		final List<InterestOwnershipTransitionMetadata> transitions) {
		out.append('[');
		boolean first = true;
		for (InterestOwnershipTransitionMetadata transition : transitions) {
			if (!first) {
				out.append(',');
			}
			first = false;
			WorldRegionKey key = transition.getLogicalRegionKey();
			out.append("{\"logicalRegion\":{\"worldSpace\":\"")
				.append(jsonEscape(key.getWorldSpace().getValue()))
				.append("\",\"level\":").append(key.getLevel())
				.append(",\"x\":").append(key.getRegionX())
				.append(",\"y\":").append(key.getRegionY()).append("},");
			field(out, "interestState", transition.getInterestState().name())
				.append(',');
			out.append("\"previousReferenceCount\":")
				.append(transition.getPreviousReferenceCount()).append(',');
			out.append("\"currentReferenceCount\":")
				.append(transition.getCurrentReferenceCount()).append('}');
		}
		out.append(']');
	}

	private static void appendRegionRetirement(
		final StringBuilder out,
		final RegionRetirementMetadata retirement) {
		out.append('{');
		out.append("\"transitionRegionCount\":")
			.append(retirement.getTransitionRegionCount()).append(',');
		out.append("\"trackedCandidateCount\":")
			.append(retirement.getTrackedCandidateCount()).append(',');
		out.append("\"droppedCandidateCount\":")
			.append(retirement.getDroppedCandidateCount()).append(',');
		out.append("\"observedRegionCount\":")
			.append(retirement.getEntries().size()).append(',');
		out.append("\"pinnedCount\":")
			.append(retirement.getPinnedCount()).append(',');
		out.append("\"coolingDownCount\":")
			.append(retirement.getCoolingDownCount()).append(',');
		out.append("\"retirementEligibleCount\":")
			.append(retirement.getRetirementEligibleCount()).append(',');
		out.append("\"notResidentCount\":")
			.append(retirement.getNotResidentCount()).append(',');
		out.append("\"unsupportedCount\":")
			.append(retirement.getUnsupportedCount()).append(',');
		out.append("\"untrackedCount\":")
			.append(retirement.getUntrackedCount()).append(',');
		out.append("\"entries\":[");
		boolean first = true;
		for (RegionRetirementEntryMetadata entry : retirement.getEntries()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			WorldRegionKey key = entry.getLogicalRegionKey();
			out.append("{\"logicalRegion\":{\"worldSpace\":\"")
				.append(jsonEscape(key.getWorldSpace().getValue()))
				.append("\",\"level\":").append(key.getLevel())
				.append(",\"x\":").append(key.getRegionX())
				.append(",\"y\":").append(key.getRegionY()).append("},");
			out.append("\"transition\":").append(entry.isTransition()).append(',');
			out.append("\"trackedCandidate\":")
				.append(entry.isTrackedCandidate()).append(',');
			out.append("\"ownershipVersion\":")
				.append(entry.getOwnershipVersion()).append(',');
			out.append("\"residencyMirrorVersion\":")
				.append(entry.getResidencyMirrorVersion()).append(',');
			out.append("\"observedAtTick\":")
				.append(entry.getObservedAtTick()).append(',');
			out.append("\"minimumCooldownTicks\":")
				.append(entry.getMinimumCooldownTicks()).append(',');
			out.append("\"referenceCount\":")
				.append(entry.getReferenceCount()).append(',');
			out.append("\"legacySupported\":")
				.append(entry.isLegacySupported()).append(',');
			out.append("\"sourceCount\":")
				.append(entry.getSourceCount()).append(',');
			out.append("\"residentSourceCount\":")
				.append(entry.getResidentSourceCount()).append(',');
			field(out, "state", entry.getRetirementState().name()).append(',');
			out.append("\"releasedAtOwnershipVersion\":");
			appendNullableLong(out, entry.getReleasedAtOwnershipVersion());
			out.append(",\"releasedAtTick\":");
			appendNullableLong(out, entry.getReleasedAtTick());
			out.append(",\"eligibleAtTick\":");
			appendNullableLong(out, entry.getEligibleAtTick());
			out.append(",\"remainingCooldownTicks\":")
				.append(entry.getRemainingCooldownTicks()).append(',');
			out.append("\"retirementEligible\":")
				.append(entry.isRetirementEligible()).append('}');
		}
		out.append("]}");
	}

	private static void appendRegionRetirementDecisions(
		final StringBuilder out,
		final RegionRetirementDecisionMetadata decisions) {
		out.append('{');
		out.append("\"candidateCount\":")
			.append(decisions.getCandidateCount()).append(',');
		out.append("\"droppedCandidateCount\":")
			.append(decisions.getDroppedCandidateCount()).append(',');
		out.append("\"eligibleCount\":")
			.append(decisions.getEligibleCount()).append(',');
		out.append("\"refusedCount\":")
			.append(decisions.getRefusedCount()).append(',');
		out.append("\"entries\":[");
		boolean first = true;
		for (RegionRetirementDecisionEntryMetadata entry
			: decisions.getEntries()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			WorldRegionKey key = entry.getLogicalRegionKey();
			out.append("{\"logicalRegion\":{\"worldSpace\":\"")
				.append(jsonEscape(key.getWorldSpace().getValue()))
				.append("\",\"level\":").append(key.getLevel())
				.append(",\"x\":").append(key.getRegionX())
				.append(",\"y\":").append(key.getRegionY()).append("},");
			out.append("\"candidateOwnershipVersion\":")
				.append(entry.getCandidateOwnershipVersion()).append(',');
			out.append("\"currentOwnershipVersion\":")
				.append(entry.getCurrentOwnershipVersion()).append(',');
			out.append("\"candidateResidencyMirrorVersion\":")
				.append(entry.getCandidateResidencyMirrorVersion()).append(',');
			out.append("\"currentResidencyMirrorVersion\":")
				.append(entry.getCurrentResidencyMirrorVersion()).append(',');
			out.append("\"observedAtTick\":")
				.append(entry.getObservedAtTick()).append(',');
			out.append("\"candidateReleasedAtOwnershipVersion\":");
			appendNullableLong(
				out, entry.getCandidateReleasedAtOwnershipVersion());
			out.append(",\"currentReleasedAtOwnershipVersion\":");
			appendNullableLong(
				out, entry.getCurrentReleasedAtOwnershipVersion());
			out.append(",\"candidateReleasedAtTick\":");
			appendNullableLong(out, entry.getCandidateReleasedAtTick());
			out.append(",\"currentReleasedAtTick\":");
			appendNullableLong(out, entry.getCurrentReleasedAtTick());
			out.append(",\"candidateEligibleAtTick\":");
			appendNullableLong(out, entry.getCandidateEligibleAtTick());
			out.append(",\"currentEligibleAtTick\":");
			appendNullableLong(out, entry.getCurrentEligibleAtTick());
			out.append(',');
			field(out, "currentRetirementState",
				entry.getCurrentRetirementState().name()).append(',');
			field(out, "decisionState", entry.getDecisionState().name())
				.append(',');
			out.append("\"eligible\":").append(entry.isEligible()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionRetirementReadiness(
		final StringBuilder out,
		final LayeredPackedRegionRetirementReadiness readiness) {
		out.append('{');
		out.append("\"observedAtTick\":")
			.append(readiness.getObservedAtTick()).append(',');
		out.append("\"ownershipVersion\":")
			.append(readiness.getOwnershipVersion()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(readiness.getResidencyMirrorVersion()).append(',');
		out.append("\"logicalDecisionCount\":")
			.append(readiness.getLogicalDecisionCount()).append(',');
		out.append("\"sourceCount\":")
			.append(readiness.getSourceCount()).append(',');
		out.append("\"readySourceCount\":")
			.append(readiness.getReadySourceCount()).append(',');
		out.append("\"blockedSourceCount\":")
			.append(readiness.getBlockedSourceCount()).append(',');
		out.append("\"entries\":[");
		boolean first = true;
		for (LayeredPackedRegionRetirementReadiness.SourceReadiness source
			: readiness.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"coveredLogicalRegions\":");
			appendRegionKeys(out, source.getCoveredLogicalRegions());
			out.append(",\"missingLogicalDecisions\":");
			appendRegionKeys(out, source.getMissingLogicalDecisions());
			out.append(",\"refusedLogicalDecisions\":");
			appendRegionKeys(out, source.getRefusedLogicalDecisions());
			out.append(",\"partialResidencyLogicalDecisions\":");
			appendRegionKeys(
				out, source.getPartialResidencyLogicalDecisions());
			out.append(",\"spansLevels\":")
				.append(source.spansLevels()).append(',');
			field(out, "sourceState", source.getSourceState().name());
			out.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionRetirementSafety(
		final StringBuilder out,
		final LayeredPackedRegionRetirementSafetyAssessment assessment) {
		out.append('{');
		out.append("\"observedAtTick\":")
			.append(assessment.getObservedAtTick()).append(',');
		out.append("\"readinessObservedAtTick\":")
			.append(assessment.getReadinessObservedAtTick()).append(',');
		out.append("\"ownershipVersion\":")
			.append(assessment.getOwnershipVersion()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(assessment.getResidencyMirrorVersion()).append(',');
		out.append("\"sourceCount\":")
			.append(assessment.getSourceCount()).append(',');
		out.append("\"contentQuiescentSourceCount\":")
			.append(assessment.getContentQuiescentSourceCount()).append(',');
		out.append("\"lifecycleReadySourceCount\":")
			.append(assessment.getLifecycleReadySourceCount()).append(',');
		out.append("\"blockedSourceCount\":")
			.append(assessment.getBlockedSourceCount()).append(',');
		out.append("\"entries\":[");
		boolean first = true;
		for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment source
			: assessment.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			field(out, "readinessState", source.getReadinessState().name())
				.append(',');
			out.append("\"resident\":").append(source.isResident()).append(',');
			out.append("\"tileStorageAvailable\":")
				.append(source.isTileStorageAvailable()).append(',');
			out.append("\"regionReloadSupported\":")
				.append(source.isRegionReloadSupported()).append(',');
			out.append("\"playerCount\":")
				.append(source.getPlayerCount()).append(',');
			out.append("\"npcCount\":")
				.append(source.getNpcCount()).append(',');
			out.append("\"objectCount\":")
				.append(source.getObjectCount()).append(',');
			out.append("\"groundItemCount\":")
				.append(source.getGroundItemCount()).append(',');
			out.append("\"contentQuiescent\":")
				.append(source.isContentQuiescent()).append(',');
			out.append("\"lifecycleReady\":")
				.append(source.isLifecycleReady()).append(',');
			out.append("\"blockers\":[");
			boolean firstBlocker = true;
			for (LayeredPackedRegionRetirementSafetyAssessment.Blocker blocker
				: source.getBlockers()) {
				if (!firstBlocker) {
					out.append(',');
				}
				firstBlocker = false;
				quoted(out, blocker.name());
			}
			out.append("]}");
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredConstruction(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredConstructionObservation observation) {
		out.append('{');
		out.append("\"generation\":")
			.append(observation.getGeneration()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(observation.getSafetyObservedAtTick()).append(',');
		out.append("\"readinessObservedAtTick\":")
			.append(observation.getReadinessObservedAtTick()).append(',');
		out.append("\"inventorySourceCount\":")
			.append(observation.getInventorySourceCount()).append(',');
		out.append("\"inventorySceneryCount\":")
			.append(observation.getInventorySceneryCount()).append(',');
		out.append("\"inventoryBoundaryCount\":")
			.append(observation.getInventoryBoundaryCount()).append(',');
		out.append("\"inventoryNpcSpawnCount\":")
			.append(observation.getInventoryNpcSpawnCount()).append(',');
		out.append("\"inventoryGroundItemSpawnCount\":")
			.append(observation.getInventoryGroundItemSpawnCount()).append(',');
		out.append("\"inventoryHarvestingSceneryCount\":")
			.append(observation.getInventoryHarvestingSceneryCount()).append(',');
		out.append("\"inventoryAuthoredConstructionCount\":")
			.append(observation.getInventoryAuthoredConstructionCount()).append(',');
		out.append("\"sourceCount\":")
			.append(observation.getSourceCount()).append(',');
		out.append("\"authoredSourceCount\":")
			.append(observation.getAuthoredSourceCount()).append(',');
		out.append("\"sceneryCount\":")
			.append(observation.getSceneryCount()).append(',');
		out.append("\"boundaryCount\":")
			.append(observation.getBoundaryCount()).append(',');
		out.append("\"npcSpawnCount\":")
			.append(observation.getNpcSpawnCount()).append(',');
		out.append("\"groundItemSpawnCount\":")
			.append(observation.getGroundItemSpawnCount()).append(',');
		out.append("\"harvestingSceneryCount\":")
			.append(observation.getHarvestingSceneryCount()).append(',');
		out.append("\"authoredConstructionCount\":")
			.append(observation.getAuthoredConstructionCount()).append(',');
		out.append("\"originCountsOnly\":true,");
		out.append("\"reconstructionManifest\":false,");
		out.append("\"entries\":[");
		boolean first = true;
		for (LayeredPackedRegionAuthoredConstructionObservation.SourceObservation
			source : observation.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"sceneryCount\":")
				.append(source.getSceneryCount()).append(',');
			out.append("\"boundaryCount\":")
				.append(source.getBoundaryCount()).append(',');
			out.append("\"npcSpawnCount\":")
				.append(source.getNpcSpawnCount()).append(',');
			out.append("\"groundItemSpawnCount\":")
				.append(source.getGroundItemSpawnCount()).append(',');
			out.append("\"harvestingSceneryCount\":")
				.append(source.getHarvestingSceneryCount()).append(',');
			out.append("\"authoredConstructionCount\":")
				.append(source.getAuthoredConstructionCount()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredProvenance(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredProvenanceObservation observation) {
		out.append('{');
		out.append("\"generation\":")
			.append(observation.getGeneration()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(observation.getSafetyObservedAtTick()).append(',');
		out.append("\"runtimeObservedAtTick\":")
			.append(observation.getRuntimeObservedAtTick()).append(',');
		out.append("\"sourceCount\":")
			.append(observation.getSourceCount()).append(',');
		out.append("\"manifestPlacementCount\":")
			.append(observation.getManifestPlacementCount()).append(',');
		out.append("\"expectedPlacementCount\":")
			.append(observation.getExpectedPlacementCount()).append(',');
		out.append("\"supersededManifestIdentityCount\":")
			.append(observation.getSupersededManifestIdentityCount()).append(',');
		out.append("\"supersededRuntimeInstanceCount\":")
			.append(observation.getSupersededRuntimeInstanceCount()).append(',');
		out.append("\"matchedIdentityCount\":")
			.append(observation.getMatchedIdentityCount()).append(',');
		out.append("\"absentIdentityCount\":")
			.append(observation.getAbsentIdentityCount()).append(',');
		out.append("\"duplicateIdentityCount\":")
			.append(observation.getDuplicateIdentityCount()).append(',');
		out.append("\"runtimeInstanceCount\":")
			.append(observation.getRuntimeInstanceCount()).append(',');
		out.append("\"activeRuntimeInstanceCount\":")
			.append(observation.getActiveRuntimeInstanceCount()).append(',');
		out.append("\"inactiveRuntimeInstanceCount\":")
			.append(observation.getInactiveRuntimeInstanceCount()).append(',');
		out.append("\"atAuthoredSourceInstanceCount\":")
			.append(observation.getAtAuthoredSourceInstanceCount()).append(',');
		out.append("\"awayFromAuthoredSourceInstanceCount\":")
			.append(observation.getAwayFromAuthoredSourceInstanceCount()).append(',');
		out.append("\"replacementObjectInstanceCount\":")
			.append(observation.getReplacementObjectInstanceCount()).append(',');
		out.append("\"staleGenerationInstanceCount\":")
			.append(observation.getStaleGenerationInstanceCount()).append(',');
		out.append("\"unrecognizedIdentityInstanceCount\":")
			.append(observation.getUnrecognizedIdentityInstanceCount()).append(',');
		out.append("\"expectedSceneryCount\":")
			.append(observation.getExpectedSceneryCount()).append(',');
		out.append("\"expectedBoundaryCount\":")
			.append(observation.getExpectedBoundaryCount()).append(',');
		out.append("\"expectedNpcSpawnCount\":")
			.append(observation.getExpectedNpcSpawnCount()).append(',');
		out.append("\"expectedGroundItemSpawnCount\":")
			.append(observation.getExpectedGroundItemSpawnCount()).append(',');
		out.append("\"expectedHarvestingSceneryCount\":")
			.append(observation.getExpectedHarvestingSceneryCount()).append(',');
		out.append("\"runtimeSceneryCount\":")
			.append(observation.getRuntimeSceneryCount()).append(',');
		out.append("\"runtimeBoundaryCount\":")
			.append(observation.getRuntimeBoundaryCount()).append(',');
		out.append("\"runtimeNpcSpawnCount\":")
			.append(observation.getRuntimeNpcSpawnCount()).append(',');
		out.append("\"runtimeGroundItemSpawnCount\":")
			.append(observation.getRuntimeGroundItemSpawnCount()).append(',');
		out.append("\"runtimeHarvestingSceneryCount\":")
			.append(observation.getRuntimeHarvestingSceneryCount()).append(',');
		out.append("\"anomalyDetailCount\":")
			.append(observation.getAnomalyDetailCount()).append(',');
		out.append("\"droppedAnomalyDetailCount\":")
			.append(observation.getDroppedAnomalyDetailCount()).append(',');
		out.append("\"populationSupersessionDetailCount\":")
			.append(observation.getPopulationSupersessionDetailCount()).append(',');
		out.append("\"droppedPopulationSupersessionDetailCount\":")
			.append(observation.getDroppedPopulationSupersessionDetailCount())
			.append(',');
		out.append("\"identityMetadataOnly\":true,");
		out.append("\"entityRegistry\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"anomalyDetails\":[");
		boolean firstAnomaly = true;
		for (LayeredPackedRegionAuthoredProvenanceObservation.AnomalyDetail
			detail : observation.getAnomalyDetails()) {
			if (!firstAnomaly) {
				out.append(',');
			}
			firstAnomaly = false;
			appendPackedRegionAuthoredProvenanceAnomaly(out, detail);
		}
		out.append("],");
		out.append("\"populationSupersessions\":[");
		boolean firstSupersession = true;
		for (LayeredPackedRegionAuthoredPopulationOutcome.Supersession
			supersession : observation.getPopulationSupersessions()) {
			if (!firstSupersession) {
				out.append(',');
			}
			firstSupersession = false;
			appendPackedRegionAuthoredPopulationSupersession(
				out, supersession);
		}
		out.append("],");
		out.append("\"entries\":[");
		boolean first = true;
		for (LayeredPackedRegionAuthoredProvenanceObservation.SourceObservation
			source : observation.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"manifestPlacementCount\":")
				.append(source.getManifestPlacementCount()).append(',');
			out.append("\"expectedPlacementCount\":")
				.append(source.getExpectedPlacementCount()).append(',');
			out.append("\"supersededManifestIdentityCount\":")
				.append(source.getSupersededManifestIdentityCount()).append(',');
			out.append("\"supersededRuntimeInstanceCount\":")
				.append(source.getSupersededRuntimeInstanceCount()).append(',');
			out.append("\"matchedIdentityCount\":")
				.append(source.getMatchedIdentityCount()).append(',');
			out.append("\"absentIdentityCount\":")
				.append(source.getAbsentIdentityCount()).append(',');
			out.append("\"duplicateIdentityCount\":")
				.append(source.getDuplicateIdentityCount()).append(',');
			out.append("\"runtimeInstanceCount\":")
				.append(source.getRuntimeInstanceCount()).append(',');
			out.append("\"activeRuntimeInstanceCount\":")
				.append(source.getActiveRuntimeInstanceCount()).append(',');
			out.append("\"inactiveRuntimeInstanceCount\":")
				.append(source.getInactiveRuntimeInstanceCount()).append(',');
			out.append("\"atAuthoredSourceInstanceCount\":")
				.append(source.getAtAuthoredSourceInstanceCount()).append(',');
			out.append("\"awayFromAuthoredSourceInstanceCount\":")
				.append(source.getAwayFromAuthoredSourceInstanceCount()).append(',');
			out.append("\"replacementObjectInstanceCount\":")
				.append(source.getReplacementObjectInstanceCount()).append(',');
			out.append("\"staleGenerationInstanceCount\":")
				.append(source.getStaleGenerationInstanceCount()).append(',');
			out.append("\"unrecognizedIdentityInstanceCount\":")
				.append(source.getUnrecognizedIdentityInstanceCount()).append(',');
			out.append("\"expectedSceneryCount\":")
				.append(source.getExpectedSceneryCount()).append(',');
			out.append("\"expectedBoundaryCount\":")
				.append(source.getExpectedBoundaryCount()).append(',');
			out.append("\"expectedNpcSpawnCount\":")
				.append(source.getExpectedNpcSpawnCount()).append(',');
			out.append("\"expectedGroundItemSpawnCount\":")
				.append(source.getExpectedGroundItemSpawnCount()).append(',');
			out.append("\"expectedHarvestingSceneryCount\":")
				.append(source.getExpectedHarvestingSceneryCount()).append(',');
			out.append("\"runtimeSceneryCount\":")
				.append(source.getRuntimeSceneryCount()).append(',');
			out.append("\"runtimeBoundaryCount\":")
				.append(source.getRuntimeBoundaryCount()).append(',');
			out.append("\"runtimeNpcSpawnCount\":")
				.append(source.getRuntimeNpcSpawnCount()).append(',');
			out.append("\"runtimeGroundItemSpawnCount\":")
				.append(source.getRuntimeGroundItemSpawnCount()).append(',');
			out.append("\"runtimeHarvestingSceneryCount\":")
				.append(source.getRuntimeHarvestingSceneryCount()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredReconstruction(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredReconstructionObservation observation) {
		out.append('{');
		out.append("\"generation\":")
			.append(observation.getGeneration()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(observation.getSafetyObservedAtTick()).append(',');
		out.append("\"recipeSourceCount\":")
			.append(observation.getRecipeSourceCount()).append(',');
		out.append("\"recipeManifestPlacementCount\":")
			.append(observation.getRecipeManifestPlacementCount()).append(',');
		out.append("\"recipeSupersededPlacementCount\":")
			.append(observation.getRecipeSupersededPlacementCount()).append(',');
		out.append("\"recipeReconstructionPlacementCount\":")
			.append(observation.getRecipeReconstructionPlacementCount()).append(',');
		out.append("\"sourceCount\":")
			.append(observation.getSourceCount()).append(',');
		out.append("\"authoredSourceCount\":")
			.append(observation.getAuthoredSourceCount()).append(',');
		out.append("\"manifestPlacementCount\":")
			.append(observation.getManifestPlacementCount()).append(',');
		out.append("\"supersededPlacementCount\":")
			.append(observation.getSupersededPlacementCount()).append(',');
		out.append("\"reconstructionPlacementCount\":")
			.append(observation.getReconstructionPlacementCount()).append(',');
		out.append("\"crossSourcePlacementCount\":")
			.append(observation.getCrossSourcePlacementCount()).append(',');
		out.append("\"affectedSourceReferenceCount\":")
			.append(observation.getAffectedSourceReferenceCount()).append(',');
		out.append("\"requirementSourceCount\":")
			.append(observation.getRequirementSourceCount()).append(',');
		out.append("\"selectedRequirementSourceCount\":")
			.append(observation.getSelectedRequirementSourceCount()).append(',');
		out.append("\"missingRequirementSourceCount\":")
			.append(observation.getMissingRequirementSourceCount()).append(',');
		out.append("\"selectionDependencyClosed\":")
			.append(observation.isSelectionDependencyClosed()).append(',');
		out.append("\"identityMetadataOnly\":true,");
		out.append("\"entityRegistry\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"entries\":[");
		boolean first = true;
		for (LayeredPackedRegionAuthoredReconstructionObservation.SourceObservation
			source : observation.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"manifestPlacementCount\":")
				.append(source.getManifestPlacementCount()).append(',');
			out.append("\"supersededPlacementCount\":")
				.append(source.getSupersededPlacementCount()).append(',');
			out.append("\"reconstructionPlacementCount\":")
				.append(source.getReconstructionPlacementCount()).append(',');
			out.append("\"crossSourcePlacementCount\":")
				.append(source.getCrossSourcePlacementCount()).append(',');
			out.append("\"affectedSourceReferenceCount\":")
				.append(source.getAffectedSourceReferenceCount()).append(',');
			out.append("\"requirementSourceCount\":")
				.append(source.getRequirementSourceCount()).append(',');
			out.append("\"selectedRequirementSourceCount\":")
				.append(source.getSelectedRequirementSourceCount()).append(',');
			out.append("\"missingRequirementSourceCount\":")
				.append(source.getMissingRequirementSourceCount()).append(',');
			out.append("\"dependencyClosed\":")
				.append(source.isDependencyClosed()).append('}');
		}
		out.append("],\"requirements\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionObservation
			.RequirementObservation requirement : observation.getRequirements()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(requirement.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(requirement.getPackedRegionY()).append(',');
			out.append("\"selectedSafetySource\":")
				.append(requirement.isSelectedSafetySource()).append(',');
			out.append("\"authoredRecipeSource\":")
				.append(requirement.isAuthoredRecipeSource()).append(',');
			out.append("\"ownerSourceCount\":")
				.append(requirement.getOwnerSourceCount()).append(',');
			out.append("\"placementReferenceCount\":")
				.append(requirement.getPlacementReferenceCount()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredReconstructionCohort(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis analysis) {
		out.append('{');
		out.append("\"generation\":")
			.append(analysis.getGeneration()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(analysis.getSafetyObservedAtTick()).append(',');
		out.append("\"seedSourceCount\":")
			.append(analysis.getSeedSourceCount()).append(',');
		out.append("\"cohortSourceCount\":")
			.append(analysis.getCohortSourceCount()).append(',');
		out.append("\"expandedAuthoredSourceCount\":")
			.append(analysis.getExpandedAuthoredSourceCount()).append(',');
		out.append("\"authoredContentSourceCount\":")
			.append(analysis.getAuthoredContentSourceCount()).append(',');
		out.append("\"reconstructionPlacementCount\":")
			.append(analysis.getReconstructionPlacementCount()).append(',');
		out.append("\"crossSourcePlacementCount\":")
			.append(analysis.getCrossSourcePlacementCount()).append(',');
		out.append("\"affectedSourceReferenceCount\":")
			.append(analysis.getAffectedSourceReferenceCount()).append(',');
		out.append("\"requirementSourceCount\":")
			.append(analysis.getRequirementSourceCount()).append(',');
		out.append("\"cohortRequirementSourceCount\":")
			.append(analysis.getCohortRequirementSourceCount()).append(',');
		out.append("\"externalSupportRequirementSourceCount\":")
			.append(analysis.getExternalSupportRequirementSourceCount())
			.append(',');
		out.append("\"maximumExpansionRound\":")
			.append(analysis.getMaximumExpansionRound()).append(',');
		out.append("\"authoredClosureComplete\":")
			.append(analysis.isAuthoredClosureComplete()).append(',');
		out.append("\"fullySelfContained\":")
			.append(analysis.isFullySelfContained()).append(',');
		out.append("\"identityMetadataOnly\":true,");
		out.append("\"entityRegistry\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"entries\":[");
		boolean first = true;
		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis source : analysis.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			field(out, "role", source.getRole().name()).append(',');
			out.append("\"expansionRound\":")
				.append(source.getExpansionRound()).append(',');
			out.append("\"recipeSourcePresent\":")
				.append(source.isRecipeSourcePresent()).append(',');
			out.append("\"authoredContentPresent\":")
				.append(source.hasAuthoredContent()).append(',');
			out.append("\"reconstructionPlacementCount\":")
				.append(source.getReconstructionPlacementCount()).append(',');
			out.append("\"crossSourcePlacementCount\":")
				.append(source.getCrossSourcePlacementCount()).append(',');
			out.append("\"affectedSourceReferenceCount\":")
				.append(source.getAffectedSourceReferenceCount()).append(',');
			out.append("\"requirementSourceCount\":")
				.append(source.getRequirementSourceCount()).append(',');
			out.append("\"cohortRequirementSourceCount\":")
				.append(source.getCohortRequirementSourceCount()).append(',');
			out.append("\"externalSupportRequirementSourceCount\":")
				.append(source.getExternalSupportRequirementSourceCount())
				.append(',');
			out.append("\"dependencySelfContained\":")
				.append(source.isDependencySelfContained()).append('}');
		}
		out.append("],\"requirements\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.RequirementAnalysis requirement : analysis.getRequirements()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"packedRegionX\":")
				.append(requirement.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(requirement.getPackedRegionY()).append(',');
			out.append("\"cohortSource\":")
				.append(requirement.isCohortSource()).append(',');
			out.append("\"recipeSourcePresent\":")
				.append(requirement.isRecipeSourcePresent()).append(',');
			out.append("\"authoredContentPresent\":")
				.append(requirement.hasAuthoredContent()).append(',');
			out.append("\"externalSupportRequired\":")
				.append(requirement.isExternalSupportRequired()).append(',');
			out.append("\"ownerSourceCount\":")
				.append(requirement.getOwnerSourceCount()).append(',');
			out.append("\"placementReferenceCount\":")
				.append(requirement.getPlacementReferenceCount()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredReconstructionCohortAttribution(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredReconstructionCohortAttribution
			attribution) {
		out.append('{');
		out.append("\"generation\":")
			.append(attribution.getGeneration()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(attribution.getSafetyObservedAtTick()).append(',');
		out.append("\"kindCount\":")
			.append(attribution.getKindCount()).append(',');
		out.append("\"edgeCount\":")
			.append(attribution.getEdgeCount()).append(',');
		out.append("\"bridgePlacementCount\":")
			.append(attribution.getBridgePlacementCount()).append(',');
		out.append("\"placementCount\":")
			.append(attribution.getPlacementCount()).append(',');
		out.append("\"crossSourcePlacementCount\":")
			.append(attribution.getCrossSourcePlacementCount()).append(',');
		out.append("\"affectedSourceReferenceCount\":")
			.append(attribution.getAffectedSourceReferenceCount()).append(',');
		out.append("\"crossSourceReferenceCount\":")
			.append(attribution.getCrossSourceReferenceCount()).append(',');
		out.append("\"expansionFrontierReferenceCount\":")
			.append(attribution.getExpansionFrontierReferenceCount()).append(',');
		out.append("\"externalSupportReferenceCount\":")
			.append(attribution.getExternalSupportReferenceCount()).append(',');
		out.append("\"selfEdgeCount\":")
			.append(attribution.getSelfEdgeCount()).append(',');
		out.append("\"expansionFrontierEdgeCount\":")
			.append(attribution.getExpansionFrontierEdgeCount()).append(',');
		out.append("\"externalSupportEdgeCount\":")
			.append(attribution.getExternalSupportEdgeCount()).append(',');
		out.append("\"identityMetadataOnly\":true,");
		out.append("\"entityRegistry\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"kinds\":[");
		boolean first = true;
		for (LayeredPackedRegionAuthoredReconstructionCohortAttribution
			.KindAttribution kind : attribution.getKinds()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			field(out, "constructionKind", kind.getConstructionKind().name())
				.append(',');
			field(out, "dependencyKind", kind.getDependencyKind().name())
				.append(',');
			out.append("\"placementCount\":")
				.append(kind.getPlacementCount()).append(',');
			out.append("\"crossSourcePlacementCount\":")
				.append(kind.getCrossSourcePlacementCount()).append(',');
			out.append("\"affectedSourceReferenceCount\":")
				.append(kind.getAffectedSourceReferenceCount()).append(',');
			out.append("\"crossSourceReferenceCount\":")
				.append(kind.getCrossSourceReferenceCount()).append(',');
			out.append("\"expansionFrontierReferenceCount\":")
				.append(kind.getExpansionFrontierReferenceCount()).append(',');
			out.append("\"externalSupportReferenceCount\":")
				.append(kind.getExternalSupportReferenceCount()).append('}');
		}
		out.append("],\"edges\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionCohortAttribution
			.EdgeAttribution edge : attribution.getEdges()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"ownerPackedRegionX\":")
				.append(edge.getOwnerPackedRegionX()).append(',');
			out.append("\"ownerPackedRegionY\":")
				.append(edge.getOwnerPackedRegionY()).append(',');
			out.append("\"ownerExpansionRound\":")
				.append(edge.getOwnerExpansionRound()).append(',');
			out.append("\"requiredPackedRegionX\":")
				.append(edge.getRequiredPackedRegionX()).append(',');
			out.append("\"requiredPackedRegionY\":")
				.append(edge.getRequiredPackedRegionY()).append(',');
			out.append("\"requiredExpansionRound\":")
				.append(edge.getRequiredExpansionRound()).append(',');
			out.append("\"selfReference\":")
				.append(edge.isSelfReference()).append(',');
			out.append("\"cohortSource\":")
				.append(edge.isCohortSource()).append(',');
			out.append("\"expansionFrontier\":")
				.append(edge.isExpansionFrontier()).append(',');
			out.append("\"externalSupportRequired\":")
				.append(edge.isExternalSupportRequired()).append(',');
			out.append("\"placementReferenceCount\":")
				.append(edge.getPlacementReferenceCount()).append(',');
			out.append("\"constructionKindReferences\":[");
			boolean firstKind = true;
			for (LayeredPackedRegionAuthoredReconstructionCohortAttribution
				.TypedReferenceAttribution reference
					: edge.getConstructionKindReferences()) {
				if (!firstKind) { out.append(','); }
				firstKind = false;
				out.append('{');
				field(out, "kind", reference.getKindName()).append(',');
				out.append("\"referenceCount\":")
					.append(reference.getReferenceCount()).append('}');
			}
			out.append("],\"dependencyKindReferences\":[");
			firstKind = true;
			for (LayeredPackedRegionAuthoredReconstructionCohortAttribution
				.TypedReferenceAttribution reference
					: edge.getDependencyKindReferences()) {
				if (!firstKind) { out.append(','); }
				firstKind = false;
				out.append('{');
				field(out, "kind", reference.getKindName()).append(',');
				out.append("\"referenceCount\":")
					.append(reference.getReferenceCount()).append('}');
			}
			out.append("]}");
		}
		out.append("],\"bridgePlacements\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionCohortAttribution
			.BridgePlacementAttribution bridge
				: attribution.getBridgePlacements()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"identityGeneration\":")
				.append(bridge.getIdentityGeneration()).append(',');
			out.append("\"ownerPackedRegionX\":")
				.append(bridge.getOwnerPackedRegionX()).append(',');
			out.append("\"ownerPackedRegionY\":")
				.append(bridge.getOwnerPackedRegionY()).append(',');
			out.append("\"ownerExpansionRound\":")
				.append(bridge.getOwnerExpansionRound()).append(',');
			out.append("\"sourceOrdinal\":")
				.append(bridge.getSourceOrdinal()).append(',');
			field(out, "constructionKind",
				bridge.getConstructionKind().name()).append(',');
			field(out, "dependencyKind",
				bridge.getDependencyKind().name()).append(',');
			out.append("\"authoredDefinitionId\":")
				.append(bridge.getAuthoredDefinitionId()).append(',');
			out.append("\"constructedEntityId\":")
				.append(bridge.getConstructedEntityId()).append(',');
			out.append("\"minimumPackedRegionX\":")
				.append(bridge.getMinimumPackedRegionX()).append(',');
			out.append("\"maximumPackedRegionX\":")
				.append(bridge.getMaximumPackedRegionX()).append(',');
			out.append("\"minimumPackedRegionY\":")
				.append(bridge.getMinimumPackedRegionY()).append(',');
			out.append("\"maximumPackedRegionY\":")
				.append(bridge.getMaximumPackedRegionY()).append(',');
			out.append("\"affectedSourceCount\":")
				.append(bridge.getAffectedSourceCount()).append(',');
			out.append("\"cohortRequirementSourceCount\":")
				.append(bridge.getCohortRequirementSourceCount()).append(',');
			out.append("\"expansionFrontierSourceCount\":")
				.append(bridge.getExpansionFrontierSourceCount()).append(',');
			out.append("\"externalSupportRequirementSourceCount\":")
				.append(bridge.getExternalSupportRequirementSourceCount())
				.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredReconstructionTopology(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
			topology) {
		out.append('{');
		out.append("\"generation\":").append(topology.getGeneration()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(topology.getSafetyObservedAtTick()).append(',');
		out.append("\"recipeSourceCount\":")
			.append(topology.getRecipeSourceCount()).append(',');
		out.append("\"authoredSourceCount\":")
			.append(topology.getAuthoredSourceCount()).append(',');
		out.append("\"kindCount\":")
			.append(topology.getKindCount()).append(',');
		out.append("\"weakComponentCount\":")
			.append(topology.getWeakComponentCount()).append(',');
		out.append("\"strongComponentCount\":")
			.append(topology.getStrongComponentCount()).append(',');
		out.append("\"directedEdgeCount\":")
			.append(topology.getDirectedEdgeCount()).append(',');
		out.append("\"selfEdgeCount\":")
			.append(topology.getSelfEdgeCount()).append(',');
		out.append("\"crossSourceDirectedEdgeCount\":")
			.append(topology.getCrossSourceDirectedEdgeCount()).append(',');
		out.append("\"authoredDependencyReferenceCount\":")
			.append(topology.getAuthoredDependencyReferenceCount()).append(',');
		out.append("\"crossSourceAuthoredReferenceCount\":")
			.append(topology.getCrossSourceAuthoredReferenceCount()).append(',');
		out.append("\"externalSupportSourceCount\":")
			.append(topology.getExternalSupportSourceCount()).append(',');
		out.append("\"externalSupportEdgeCount\":")
			.append(topology.getExternalSupportEdgeCount()).append(',');
		out.append("\"externalSupportReferenceCount\":")
			.append(topology.getExternalSupportReferenceCount()).append(',');
		out.append("\"forwardCohortSourceCount\":")
			.append(topology.getForwardCohortSourceCount()).append(',');
		out.append("\"forwardAuthoredSourceCount\":")
			.append(topology.getForwardAuthoredSourceCount()).append(',');
		out.append("\"touchedWeakComponentCount\":")
			.append(topology.getTouchedWeakComponentCount()).append(',');
		out.append("\"conservativeConnectedSourceCount\":")
			.append(topology.getConservativeConnectedSourceCount()).append(',');
		out.append("\"incomingOnlySourceCount\":")
			.append(topology.getIncomingOnlySourceCount()).append(',');
		out.append("\"directIncomingEdgeCount\":")
			.append(topology.getDirectIncomingEdgeCount()).append(',');
		out.append("\"directIncomingReferenceCount\":")
			.append(topology.getDirectIncomingReferenceCount()).append(',');
		out.append("\"conservativeConnectedEdgeCount\":")
			.append(topology.getConservativeConnectedEdgeCount()).append(',');
		out.append("\"conservativeConnectedReferenceCount\":")
			.append(topology.getConservativeConnectedReferenceCount()).append(',');
		out.append("\"largestWeakComponentSourceCount\":")
			.append(topology.getLargestWeakComponentSourceCount()).append(',');
		out.append("\"largestStrongComponentSourceCount\":")
			.append(topology.getLargestStrongComponentSourceCount()).append(',');
		out.append("\"cyclicStrongComponentCount\":")
			.append(topology.getCyclicStrongComponentCount()).append(',');
		out.append("\"forwardDependencyClosed\":")
			.append(topology.isForwardDependencyClosed()).append(',');
		out.append("\"forwardCohortWeaklyClosed\":")
			.append(topology.isForwardCohortWeaklyClosed()).append(',');
		out.append("\"identityMetadataOnly\":true,");
		out.append("\"entityRegistry\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"sources\":[");
		boolean first = true;
		for (LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
			.SourceTopology source : topology.getSources()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"reconstructionPlacementCount\":")
				.append(source.getReconstructionPlacementCount()).append(',');
			out.append("\"weakComponentOrdinal\":")
				.append(source.getWeakComponentOrdinal()).append(',');
			out.append("\"strongComponentOrdinal\":")
				.append(source.getStrongComponentOrdinal()).append(',');
			out.append("\"forwardCohortSource\":")
				.append(source.isForwardCohortSource()).append(',');
			out.append("\"conservativeConnectedSource\":")
				.append(source.isConservativeConnectedSource()).append(',');
			out.append("\"incomingOnlySource\":")
				.append(source.isIncomingOnlySource()).append('}');
		}
		out.append("],\"kinds\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
			.KindTopology kind : topology.getKinds()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			field(out, "constructionKind", kind.getConstructionKind().name())
				.append(',');
			field(out, "dependencyKind", kind.getDependencyKind().name())
				.append(',');
			out.append("\"authoredDependencyReferenceCount\":")
				.append(kind.getAuthoredDependencyReferenceCount()).append(',');
			out.append("\"crossSourceAuthoredReferenceCount\":")
				.append(kind.getCrossSourceAuthoredReferenceCount()).append(',');
			out.append("\"externalSupportReferenceCount\":")
				.append(kind.getExternalSupportReferenceCount()).append(',');
			out.append("\"directIncomingReferenceCount\":")
				.append(kind.getDirectIncomingReferenceCount()).append(',');
			out.append("\"conservativeConnectedReferenceCount\":")
				.append(kind.getConservativeConnectedReferenceCount()).append('}');
		}
		out.append("],\"weakComponents\":");
		appendPackedRegionAuthoredReconstructionTopologyComponents(
			out, topology.getWeakComponents());
		out.append(",\"strongComponents\":");
		appendPackedRegionAuthoredReconstructionTopologyComponents(
			out, topology.getStrongComponents());
		out.append('}');
	}

	private static void
		appendPackedRegionAuthoredReconstructionTopologyComponents(
			final StringBuilder out,
			final List<LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
				.ComponentTopology> components) {
		out.append('[');
		boolean first = true;
		for (LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
			.ComponentTopology component : components) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"ordinal\":").append(component.getOrdinal()).append(',');
			out.append("\"sourceCount\":")
				.append(component.getSourceCount()).append(',');
			out.append("\"edgeCount\":")
				.append(component.getEdgeCount()).append(',');
			out.append("\"referenceCount\":")
				.append(component.getReferenceCount()).append(',');
			out.append("\"forwardCohort\":")
				.append(component.containsForwardCohort()).append(',');
			out.append("\"conservativeConnected\":")
				.append(component.isConservativeConnected()).append('}');
		}
		out.append(']');
	}

	private static void
		appendPackedRegionAuthoredReconstructionDependencySemantics(
			final StringBuilder out,
			final
				LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
					analysis) {
		out.append('{');
		out.append("\"generation\":").append(analysis.getGeneration()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(analysis.getSafetyObservedAtTick()).append(',');
		out.append("\"selectedSourceCount\":")
			.append(analysis.getSelectedSourceCount()).append(',');
		out.append("\"selectedAuthoredReplaySourceCount\":")
			.append(analysis.getSelectedAuthoredReplaySourceCount()).append(',');
		out.append("\"selectedContentEmptySourceCount\":")
			.append(analysis.getSelectedContentEmptySourceCount()).append(',');
		out.append("\"replayPlacementCount\":")
			.append(analysis.getReplayPlacementCount()).append(',');
		out.append("\"outboundSupportSourceCount\":")
			.append(analysis.getOutboundSupportSourceCount()).append(',');
		out.append("\"externalOutboundSupportSourceCount\":")
			.append(analysis.getExternalOutboundSupportSourceCount()).append(',');
		out.append("\"outboundSupportReferenceCount\":")
			.append(analysis.getOutboundSupportReferenceCount()).append(',');
		out.append("\"externalOutboundSupportReferenceCount\":")
			.append(analysis.getExternalOutboundSupportReferenceCount()).append(',');
		out.append("\"incomingOwnerSourceCount\":")
			.append(analysis.getIncomingOwnerSourceCount()).append(',');
		out.append("\"incomingPlacementCount\":")
			.append(analysis.getIncomingPlacementCount()).append(',');
		out.append("\"incomingReferenceCount\":")
			.append(analysis.getIncomingReferenceCount()).append(',');
		out.append("\"kindCount\":").append(analysis.getKindCount()).append(',');
		out.append("\"sourceLocalReplay\":true,");
		out.append("\"spatialReachPreserved\":true,");
		out.append("\"activeInstanceEvidence\":false,");
		out.append("\"entityRegistry\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"selectedSources\":[");
		boolean first = true;
		for (LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
			.SelectedSource source : analysis.getSelectedSources()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"recipeSourcePresent\":")
				.append(source.isRecipeSourcePresent()).append(',');
			out.append("\"authoredContentPresent\":")
				.append(source.hasAuthoredContent()).append(',');
			out.append("\"replayPlacementCount\":")
				.append(source.getReplayPlacementCount()).append('}');
		}
		out.append("],\"outboundSupportSources\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
			.SupportSource source : analysis.getOutboundSupportSources()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"selectedSource\":")
				.append(source.isSelectedSource()).append(',');
			out.append("\"externalSupportSource\":")
				.append(source.isExternalSupportSource()).append(',');
			out.append("\"recipeSourcePresent\":")
				.append(source.isRecipeSourcePresent()).append(',');
			out.append("\"authoredContentPresent\":")
				.append(source.hasAuthoredContent()).append(',');
			out.append("\"ownerSourceCount\":")
				.append(source.getOwnerSourceCount()).append(',');
			out.append("\"placementReferenceCount\":")
				.append(source.getPlacementReferenceCount()).append(',');
			out.append("\"staticFootprintReferenceCount\":")
				.append(source.getStaticFootprintReferenceCount()).append(',');
			out.append("\"potentialMobileReferenceCount\":")
				.append(source.getPotentialMobileReferenceCount()).append(',');
			out.append("\"anchorOnlyReferenceCount\":")
				.append(source.getAnchorOnlyReferenceCount()).append('}');
		}
		out.append("],\"incomingOwners\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
			.IncomingOwner owner : analysis.getIncomingOwners()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"packedRegionX\":")
				.append(owner.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(owner.getPackedRegionY()).append(',');
			out.append("\"ownerReplayPlacementCount\":")
				.append(owner.getOwnerReplayPlacementCount()).append(',');
			out.append("\"incomingPlacementCount\":")
				.append(owner.getIncomingPlacementCount()).append(',');
			out.append("\"selectedSourceReferenceCount\":")
				.append(owner.getSelectedSourceReferenceCount()).append(',');
			out.append("\"staticFootprintPlacementCount\":")
				.append(owner.getStaticFootprintPlacementCount()).append(',');
			out.append("\"potentialMobilePlacementCount\":")
				.append(owner.getPotentialMobilePlacementCount()).append(',');
			out.append("\"anchorOnlyPlacementCount\":")
				.append(owner.getAnchorOnlyPlacementCount()).append('}');
		}
		out.append("],\"kinds\":[");
		first = true;
		for (LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
			.KindSemantics kind : analysis.getKinds()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			field(out, "constructionKind", kind.getConstructionKind().name())
				.append(',');
			field(out, "dependencyKind", kind.getDependencyKind().name())
				.append(',');
			field(out, "semantics", kind.getSemantics().name()).append(',');
			out.append("\"replayPlacementCount\":")
				.append(kind.getReplayPlacementCount()).append(',');
			out.append("\"outboundSupportReferenceCount\":")
				.append(kind.getOutboundSupportReferenceCount()).append(',');
			out.append("\"externalOutboundSupportReferenceCount\":")
				.append(kind.getExternalOutboundSupportReferenceCount()).append(',');
			out.append("\"incomingPlacementCount\":")
				.append(kind.getIncomingPlacementCount()).append(',');
			out.append("\"incomingReferenceCount\":")
				.append(kind.getIncomingReferenceCount()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionActiveNpcResidency(
		final StringBuilder out,
		final LayeredPackedRegionActiveNpcResidencyObservation observation) {
		out.append('{');
		out.append("\"generation\":").append(observation.getGeneration())
			.append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(observation.getSafetyObservedAtTick()).append(',');
		out.append("\"censusObservedAtTick\":")
			.append(observation.getCensusObservedAtTick()).append(',');
		out.append("\"selectedSourceCount\":")
			.append(observation.getSelectedSourceCount()).append(',');
		out.append("\"observedInstanceCount\":")
			.append(observation.getObservedInstanceCount()).append(',');
		out.append("\"activeInstanceCount\":")
			.append(observation.getActiveInstanceCount()).append(',');
		out.append("\"inactiveInstanceCount\":")
			.append(observation.getInactiveInstanceCount()).append(',');
		out.append("\"activeRecognizedInstanceCount\":")
			.append(observation.getActiveRecognizedInstanceCount()).append(',');
		out.append("\"activeUnrecognizedInstanceCount\":")
			.append(observation.getActiveUnrecognizedInstanceCount()).append(',');
		out.append("\"uniqueActiveRecognizedIdentityCount\":")
			.append(observation.getUniqueActiveRecognizedIdentityCount()).append(',');
		out.append("\"duplicateActiveRecognizedIdentityInstanceCount\":")
			.append(observation
				.getDuplicateActiveRecognizedIdentityInstanceCount()).append(',');
		out.append("\"relevantActiveInstanceCount\":")
			.append(observation.getRelevantActiveInstanceCount()).append(',');
		out.append("\"irrelevantActiveInstanceCount\":")
			.append(observation.getIrrelevantActiveInstanceCount()).append(',');
		out.append("\"selectedOwnerInsideCount\":")
			.append(observation.getSelectedOwnerInsideCount()).append(',');
		out.append("\"selectedOwnerOutsideCount\":")
			.append(observation.getSelectedOwnerOutsideCount()).append(',');
		out.append("\"externalOwnerInsideCount\":")
			.append(observation.getExternalOwnerInsideCount()).append(',');
		out.append("\"unresolvedInsideCount\":")
			.append(observation.getUnresolvedInsideCount()).append(',');
		out.append("\"unresolvedClaimedSelectedOwnerOutsideCount\":")
			.append(observation
				.getUnresolvedClaimedSelectedOwnerOutsideCount()).append(',');
		out.append("\"inactiveRelevantInstanceCount\":")
			.append(observation.getInactiveRelevantInstanceCount()).append(',');
		out.append("\"inactiveIrrelevantInstanceCount\":")
			.append(observation.getInactiveIrrelevantInstanceCount()).append(',');
		out.append("\"pointInTimeCensus\":true,");
		out.append("\"activeInstanceEvidence\":true,");
		out.append("\"entityRegistry\":false,");
		out.append("\"arrivalGate\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"identityStatuses\":[");
		boolean first = true;
		for (LayeredPackedRegionActiveNpcResidencyObservation.IdentityStatusCount
			status : observation.getIdentityStatuses()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			field(out, "status", status.getStatus().name()).append(',');
			out.append("\"activeInstanceCount\":")
				.append(status.getActiveInstanceCount()).append('}');
		}
		out.append("],\"relevantActiveInstances\":[");
		first = true;
		for (LayeredPackedRegionActiveNpcResidencyObservation.InstanceEvidence
			evidence : observation.getRelevantActiveInstances()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"identity\":");
			appendActiveNpcAuthoredIdentity(out, evidence);
			out.append(",\"runtimeNpcId\":")
				.append(evidence.getRuntimeNpcId()).append(',');
			out.append("\"currentPackedRegionX\":")
				.append(evidence.getCurrentPackedRegionX()).append(',');
			out.append("\"currentPackedRegionY\":")
				.append(evidence.getCurrentPackedRegionY()).append(',');
			field(out, "identityStatus", evidence.getIdentityStatus().name())
				.append(',');
			out.append("\"expectedRuntimeNpcId\":");
			if (evidence.getExpectedRuntimeNpcId() == null) {
				out.append("null");
			} else {
				out.append(evidence.getExpectedRuntimeNpcId().intValue());
			}
			out.append(',');
			field(out, "classification", evidence.getClassification().name())
				.append('}');
		}
		out.append("]}");
	}

	private static void appendActiveNpcAuthoredIdentity(
		final StringBuilder out,
		final LayeredPackedRegionActiveNpcResidencyObservation.InstanceEvidence
			evidence) {
		if (!evidence.hasAuthoredIdentity()) {
			out.append("null");
			return;
		}
		out.append("{\"generation\":").append(evidence.getIdentityGeneration())
			.append(',');
		out.append("\"packedRegionX\":")
			.append(evidence.getIdentityPackedRegionX()).append(',');
		out.append("\"packedRegionY\":")
			.append(evidence.getIdentityPackedRegionY()).append(',');
		out.append("\"sourceOrdinal\":")
			.append(evidence.getIdentitySourceOrdinal()).append(',');
		field(out, "constructionKind",
			evidence.getIdentityConstructionKind().name()).append('}');
	}

	private static void appendPackedRegionActiveNpcContainment(
		final StringBuilder out,
		final LayeredPackedRegionActiveNpcContainmentAssessment assessment) {
		out.append('{');
		out.append("\"generation\":").append(assessment.getGeneration())
			.append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(assessment.getSafetyObservedAtTick()).append(',');
		out.append("\"censusObservedAtTick\":")
			.append(assessment.getCensusObservedAtTick()).append(',');
		out.append("\"selectedSourceCount\":")
			.append(assessment.getSelectedSourceCount()).append(',');
		out.append("\"activeInstanceCount\":")
			.append(assessment.getActiveInstanceCount()).append(',');
		out.append("\"relevantActiveInstanceCount\":")
			.append(assessment.getRelevantActiveInstanceCount()).append(',');
		out.append("\"selectedOwnerInsideCount\":")
			.append(assessment.getSelectedOwnerInsideCount()).append(',');
		out.append("\"sameSourceSelectedOwnerInsideCount\":")
			.append(assessment.getSameSourceSelectedOwnerInsideCount()).append(',');
		out.append("\"crossSourceSelectedOwnerInsideCount\":")
			.append(assessment.getCrossSourceSelectedOwnerInsideCount()).append(',');
		out.append("\"currentInsideCount\":")
			.append(assessment.getCurrentInsideCount()).append(',');
		out.append("\"activePreservationRequiredInstanceCount\":")
			.append(assessment.getActivePreservationRequiredInstanceCount())
			.append(',');
		out.append("\"relevantDuplicateIdentityInstanceCount\":")
			.append(assessment.getRelevantDuplicateIdentityInstanceCount())
			.append(',');
		out.append("\"blockingConditionCount\":")
			.append(assessment.getBlockingConditionCount()).append(',');
		out.append("\"blockingEvidenceCount\":")
			.append(assessment.getBlockingEvidenceCount()).append(',');
		out.append("\"boundaryContained\":")
			.append(assessment.isBoundaryContained()).append(',');
		out.append("\"pointInTimeOnly\":true,");
		out.append("\"containmentEvidence\":true,");
		out.append("\"entityPreservationRequired\":")
			.append(assessment.isEntityPreservationRequired()).append(',');
		out.append("\"lifecycleReady\":false,");
		out.append("\"entityRegistry\":false,");
		out.append("\"arrivalGate\":false,");
		out.append("\"lifecycleAuthority\":false,");
		out.append("\"blockers\":[");
		boolean first = true;
		for (LayeredPackedRegionActiveNpcContainmentAssessment.BlockerCount
			blocker : assessment.getBlockers()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			field(out, "kind", blocker.getKind().name()).append(',');
			out.append("\"instanceCount\":")
				.append(blocker.getInstanceCount()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionActiveNpcBoundaryRequirements(
		final StringBuilder out,
		final LayeredPackedRegionActiveNpcBoundaryRequirementProjection
			projection) {
		out.append('{');
		out.append("\"generation\":").append(projection.getGeneration())
			.append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(projection.getSafetyObservedAtTick()).append(',');
		out.append("\"censusObservedAtTick\":")
			.append(projection.getCensusObservedAtTick()).append(',');
		out.append("\"selectedSourceCount\":")
			.append(projection.getSelectedSourceCount()).append(',');
		out.append("\"boundaryContainedNow\":")
			.append(projection.isBoundaryContainedNow()).append(',');
		out.append("\"selectedOwnerOutsideInstanceCount\":")
			.append(projection.getSelectedOwnerOutsideInstanceCount()).append(',');
		out.append("\"externalOwnerInsideInstanceCount\":")
			.append(projection.getExternalOwnerInsideInstanceCount()).append(',');
		out.append("\"expandableBoundaryInstanceCount\":")
			.append(projection.getExpandableBoundaryInstanceCount()).append(',');
		out.append("\"uniqueRequiredSourceCount\":")
			.append(projection.getUniqueRequiredSourceCount()).append(',');
		out.append("\"unresolvedInsideInstanceCount\":")
			.append(projection.getUnresolvedInsideInstanceCount()).append(',');
		out.append("\"unresolvedClaimedSelectedOwnerOutsideInstanceCount\":")
			.append(
				projection
					.getUnresolvedClaimedSelectedOwnerOutsideInstanceCount())
			.append(',');
		out.append("\"relevantInactiveInstanceCount\":")
			.append(projection.getRelevantInactiveInstanceCount()).append(',');
		out.append("\"relevantDuplicateIdentityInstanceCount\":")
			.append(projection.getRelevantDuplicateIdentityInstanceCount())
			.append(',');
		out.append("\"hardBlockingConditionCount\":")
			.append(projection.getHardBlockingConditionCount()).append(',');
		out.append("\"hardBlockingEvidenceCount\":")
			.append(projection.getHardBlockingEvidenceCount()).append(',');
		out.append("\"freshSafetyAssessmentRequired\":")
			.append(projection.isFreshSafetyAssessmentRequired()).append(',');
		out.append("\"freshNpcCensusRequired\":")
			.append(projection.isFreshNpcCensusRequired()).append(',');
		out.append("\"selectionMutated\":")
			.append(projection.isSelectionMutated()).append(',');
		out.append("\"boundaryClosureProved\":")
			.append(projection.isBoundaryClosureProved()).append(',');
		out.append("\"entityRegistry\":")
			.append(projection.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":")
			.append(projection.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(projection.isLifecycleAuthority()).append(',');
		out.append("\"requirements\":[");
		boolean firstRequirement = true;
		for (LayeredPackedRegionActiveNpcBoundaryRequirementProjection
			.SourceRequirement requirement : projection.getRequirements()) {
			if (!firstRequirement) { out.append(','); }
			firstRequirement = false;
			out.append('{');
			out.append("\"packedRegionX\":")
				.append(requirement.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(requirement.getPackedRegionY()).append(',');
			out.append("\"selectedOwnerCurrentSourceInstanceCount\":")
				.append(
					requirement.getSelectedOwnerCurrentSourceInstanceCount())
				.append(',');
			out.append("\"externalOwnerAuthoredSourceInstanceCount\":")
				.append(
					requirement.getExternalOwnerAuthoredSourceInstanceCount())
				.append(',');
			out.append("\"boundaryInstanceCount\":")
				.append(requirement.getBoundaryInstanceCount()).append(',');
			out.append("\"reasons\":[");
			boolean firstReason = true;
			for (LayeredPackedRegionActiveNpcBoundaryRequirementProjection
				.ReasonCount reason : requirement.getReasons()) {
				if (!firstReason) { out.append(','); }
				firstReason = false;
				out.append('{');
				field(out, "reason", reason.getReason().name()).append(',');
				out.append("\"instanceCount\":")
					.append(reason.getInstanceCount()).append('}');
			}
			out.append("]}");
		}
		out.append("]}");
	}

	private static void appendPackedRegionRetirementRefinement(
		final StringBuilder out,
		final LayeredPackedRegionRetirementRefinementProposal proposal) {
		out.append('{');
		out.append("\"generation\":").append(proposal.getGeneration())
			.append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(proposal.getSafetyObservedAtTick()).append(',');
		out.append("\"censusObservedAtTick\":")
			.append(proposal.getCensusObservedAtTick()).append(',');
		out.append("\"originalSafetySourceCount\":")
			.append(proposal.getOriginalSafetySourceCount()).append(',');
		out.append("\"authoredCohortSourceCount\":")
			.append(proposal.getAuthoredCohortSourceCount()).append(',');
		out.append("\"expandedAuthoredSourceCount\":")
			.append(proposal.getExpandedAuthoredSourceCount()).append(',');
		out.append("\"activeNpcRequirementSourceCount\":")
			.append(proposal.getActiveNpcRequirementSourceCount()).append(',');
		out.append("\"candidateSourceCount\":")
			.append(proposal.getCandidateSourceCount()).append(',');
		out.append("\"addedCandidateSourceCount\":")
			.append(proposal.getAddedCandidateSourceCount()).append(',');
		out.append("\"activeNpcAndAuthoredOverlapSourceCount\":")
			.append(proposal.getActiveNpcAndAuthoredOverlapSourceCount())
			.append(',');
		out.append("\"externalSupportRequirementSourceCount\":")
			.append(proposal.getExternalSupportRequirementSourceCount())
			.append(',');
		out.append("\"supportPromotedToCandidateSourceCount\":")
			.append(proposal.getSupportPromotedToCandidateSourceCount())
			.append(',');
		out.append("\"hardBlockingConditionCount\":")
			.append(proposal.getHardBlockingConditionCount()).append(',');
		out.append("\"hardBlockingEvidenceCount\":")
			.append(proposal.getHardBlockingEvidenceCount()).append(',');
		out.append("\"boundaryContainedAtInput\":")
			.append(proposal.isBoundaryContainedAtInput()).append(',');
		out.append("\"nonExpandableHardBlockers\":")
			.append(proposal.hasNonExpandableHardBlockers()).append(',');
		out.append("\"freshSafetyAssessmentRequired\":")
			.append(proposal.isFreshSafetyAssessmentRequired()).append(',');
		out.append("\"freshNpcCensusRequired\":")
			.append(proposal.isFreshNpcCensusRequired()).append(',');
		out.append("\"reassessmentRequired\":")
			.append(proposal.isReassessmentRequired()).append(',');
		out.append("\"candidateSelectionMutated\":")
			.append(proposal.isCandidateSelectionMutated()).append(',');
		out.append("\"fixedPointClosureProved\":")
			.append(proposal.isFixedPointClosureProved()).append(',');
		out.append("\"loadRequest\":")
			.append(proposal.isLoadRequest()).append(',');
		out.append("\"entityRegistry\":")
			.append(proposal.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":")
			.append(proposal.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(proposal.isLifecycleAuthority()).append(',');
		out.append("\"candidates\":[");
		boolean first = true;
		for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
			candidate : proposal.getCandidates()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"packedRegionX\":")
				.append(candidate.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(candidate.getPackedRegionY()).append(',');
			out.append("\"originalSafetySource\":")
				.append(candidate.isOriginalSafetySource()).append(',');
			out.append("\"authoredCohortSource\":")
				.append(candidate.isAuthoredCohortSource()).append(',');
			out.append("\"authoredExpansionRound\":");
			if (candidate.getAuthoredExpansionRound() == null) {
				out.append("null");
			} else {
				out.append(candidate.getAuthoredExpansionRound().intValue());
			}
			out.append(',');
			out.append("\"externalStaticSupportSource\":")
				.append(candidate.isExternalStaticSupportSource()).append(',');
			out.append("\"staticSupportOwnerSourceCount\":")
				.append(candidate.getStaticSupportOwnerSourceCount()).append(',');
			out.append("\"staticSupportPlacementReferenceCount\":")
				.append(candidate.getStaticSupportPlacementReferenceCount())
				.append(',');
			out.append("\"selectedOwnerCurrentSourceInstanceCount\":")
				.append(
					candidate.getSelectedOwnerCurrentSourceInstanceCount())
				.append(',');
			out.append("\"externalOwnerAuthoredSourceInstanceCount\":")
				.append(
					candidate.getExternalOwnerAuthoredSourceInstanceCount())
				.append(',');
			out.append("\"activeNpcBoundaryInstanceCount\":")
				.append(candidate.getActiveNpcBoundaryInstanceCount()).append(',');
			out.append("\"activeNpcBoundarySource\":")
				.append(candidate.isActiveNpcBoundarySource()).append(',');
			out.append("\"addedBeyondOriginalSafety\":")
				.append(candidate.isAddedBeyondOriginalSafety()).append(',');
			out.append("\"freshSafetyEvidenceRequired\":")
				.append(candidate.isFreshSafetyEvidenceRequired()).append(',');
			out.append("\"freshNpcCensusRequired\":")
				.append(candidate.isFreshNpcCensusRequired()).append('}');
		}
		out.append("],\"externalSupportRequirements\":[");
		first = true;
		for (LayeredPackedRegionRetirementRefinementProposal.SupportRequirement
			support : proposal.getExternalSupportRequirements()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"packedRegionX\":")
				.append(support.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(support.getPackedRegionY()).append(',');
			out.append("\"ownerSourceCount\":")
				.append(support.getOwnerSourceCount()).append(',');
			out.append("\"placementReferenceCount\":")
				.append(support.getPlacementReferenceCount()).append(',');
			out.append("\"candidateSource\":")
				.append(support.isCandidateSource()).append(',');
			out.append("\"externalStaticSupportRequired\":")
				.append(support.isExternalStaticSupportRequired()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionRetirementRefinementReassessment(
		final StringBuilder out,
		final RetirementRefinementReassessmentMetadata metadata) {
		out.append('{');
		field(out, "status", metadata.status()).append(',');
		out.append("\"attempted\":true,");
		out.append("\"deferredNotNewer\":")
			.append(metadata.isDeferred()).append(',');
		out.append("\"previousGeneration\":")
			.append(metadata.previousProposal.getGeneration()).append(',');
		out.append("\"previousSafetyObservedAtTick\":")
			.append(metadata.previousProposal.getSafetyObservedAtTick()).append(',');
		out.append("\"previousCensusObservedAtTick\":")
			.append(metadata.previousProposal.getCensusObservedAtTick()).append(',');
		out.append("\"pendingBeforeCandidateSourceCount\":")
			.append(metadata.previousProposal.getCandidateSourceCount()).append(',');
		out.append("\"pendingAfterCandidateSourceCount\":")
			.append(metadata.pendingAfterCandidateSourceCount()).append(',');
		out.append("\"pendingRetained\":")
			.append(metadata.pendingRetained).append(',');
		out.append("\"reassessment\":");
		if (metadata.reassessment == null) {
			out.append("null");
		} else {
			appendPackedRegionRetirementRefinementReassessmentResult(
				out, metadata.reassessment);
		}
		out.append('}');
	}

	private static void appendPackedRegionRetirementRefinementReassessmentResult(
		final StringBuilder out,
		final LayeredPackedRegionRetirementRefinementReassessment reassessment) {
		out.append('{');
		out.append("\"generation\":").append(reassessment.getGeneration())
			.append(',');
		out.append("\"previousSafetyObservedAtTick\":")
			.append(reassessment.getPreviousSafetyObservedAtTick()).append(',');
		out.append("\"previousCensusObservedAtTick\":")
			.append(reassessment.getPreviousCensusObservedAtTick()).append(',');
		out.append("\"reassessmentSafetyObservedAtTick\":")
			.append(reassessment.getReassessmentSafetyObservedAtTick()).append(',');
		out.append("\"reassessmentCensusObservedAtTick\":")
			.append(reassessment.getReassessmentCensusObservedAtTick()).append(',');
		out.append("\"previousCandidateSourceCount\":")
			.append(reassessment.getPreviousCandidateSourceCount()).append(',');
		out.append("\"reassessedSourceCount\":")
			.append(reassessment.getReassessedSourceCount()).append(',');
		out.append("\"retainedCandidateSourceCount\":")
			.append(reassessment.getRetainedCandidateSourceCount()).append(',');
		out.append("\"nextCandidateSourceCount\":")
			.append(reassessment.getNextCandidateSourceCount()).append(',');
		out.append("\"newCandidateSourceCount\":")
			.append(reassessment.getNewCandidateSourceCount()).append(',');
		out.append("\"nextExternalSupportRequirementSourceCount\":")
			.append(reassessment.getNextExternalSupportRequirementSourceCount())
			.append(',');
		out.append("\"hardBlockingConditionCount\":")
			.append(reassessment.getHardBlockingConditionCount()).append(',');
		out.append("\"hardBlockingEvidenceCount\":")
			.append(reassessment.getHardBlockingEvidenceCount()).append(',');
		out.append("\"lifecycleReadyEvidenceSourceCount\":")
			.append(reassessment.getLifecycleReadyEvidenceSourceCount()).append(',');
		out.append("\"retirementReadinessEvidence\":")
			.append(reassessment.getFreshSafety()
				.hasRetirementReadinessEvidence()).append(',');
		out.append("\"freshEvidenceAligned\":")
			.append(reassessment.isFreshEvidenceAligned()).append(',');
		out.append("\"candidateSetStableAtObservation\":")
			.append(reassessment.isCandidateSetStableAtObservation()).append(',');
		out.append("\"furtherRefinementRequired\":")
			.append(reassessment.isFurtherRefinementRequired()).append(',');
		out.append("\"nonExpandableHardBlockers\":")
			.append(reassessment.hasNonExpandableHardBlockers()).append(',');
		out.append("\"refinementConvergedAtObservation\":")
			.append(reassessment.isRefinementConvergedAtObservation()).append(',');
		out.append("\"allReassessedSourcesLifecycleReadyEvidence\":")
			.append(reassessment.isAllReassessedSourcesLifecycleReadyEvidence())
			.append(',');
		out.append("\"pointInTimeOnly\":")
			.append(reassessment.isPointInTimeOnly()).append(',');
		out.append("\"candidateSelectionMutated\":")
			.append(reassessment.isCandidateSelectionMutated()).append(',');
		out.append("\"fixedPointLifecycleClosureProved\":")
			.append(reassessment.isFixedPointLifecycleClosureProved()).append(',');
		out.append("\"loadRequest\":")
			.append(reassessment.isLoadRequest()).append(',');
		out.append("\"entityRegistry\":")
			.append(reassessment.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":")
			.append(reassessment.isArrivalGate()).append(',');
		out.append("\"retirementCommitToken\":")
			.append(reassessment.isRetirementCommitToken()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(reassessment.isLifecycleAuthority()).append(',');
		out.append("\"freshSafety\":");
		appendPackedRegionRetirementSafety(out, reassessment.getFreshSafety());
		out.append(",\"newCandidates\":[");
		boolean first = true;
		for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource candidate
			: reassessment.getNewCandidates()) {
			if (!first) { out.append(','); }
			first = false;
			out.append("{\"packedRegionX\":")
				.append(candidate.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(candidate.getPackedRegionY()).append('}');
		}
		out.append("],\"nextProposal\":");
		appendPackedRegionRetirementRefinement(
			out, reassessment.getNextProposal());
		out.append('}');
	}

	private static void appendPackedRegionPreservationBurden(
		final StringBuilder out,
		final LayeredPackedRegionPreservationBurdenAssessment assessment) {
		out.append('{');
		out.append("\"observedAtTick\":")
			.append(assessment.getObservedAtTick()).append(',');
		out.append("\"safetyObservedAtTick\":")
			.append(assessment.getSafetyObservedAtTick()).append(',');
		out.append("\"retirementReadinessEvidence\":")
			.append(assessment.hasRetirementReadinessEvidence()).append(',');
		out.append("\"sourceCount\":")
			.append(assessment.getSourceCount()).append(',');
		out.append("\"burdenSatisfiedSourceCount\":")
			.append(assessment.getBurdenSatisfiedSourceCount()).append(',');
		out.append("\"blockedSourceCount\":")
			.append(assessment.getBlockedSourceCount()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(assessment.isPointInTimeOnly()).append(',');
		out.append("\"candidateSelectionMutated\":")
			.append(assessment.isCandidateSelectionMutated()).append(',');
		out.append("\"preservationPerformed\":")
			.append(assessment.isPreservationPerformed()).append(',');
		out.append("\"reloadRequest\":")
			.append(assessment.isReloadRequest()).append(',');
		out.append("\"entityRegistry\":")
			.append(assessment.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":")
			.append(assessment.isArrivalGate()).append(',');
		out.append("\"teardownTransaction\":")
			.append(assessment.isTeardownTransaction()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(assessment.isLifecycleAuthority()).append(',');
		out.append("\"familySummaries\":[");
		boolean first = true;
		for (LayeredPackedRegionPreservationBurdenAssessment.FamilySummary summary
			: assessment.getFamilySummaries()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			field(out, "family", summary.getFamily().name()).append(',');
			field(out, "policy", summary.getFamily().getPolicy().name())
				.append(',');
			out.append("\"completeSourceCount\":")
				.append(summary.getCompleteSourceCount()).append(',');
			out.append("\"partialSourceCount\":")
				.append(summary.getPartialSourceCount()).append(',');
			out.append("\"unavailableSourceCount\":")
				.append(summary.getUnavailableSourceCount()).append(',');
			out.append("\"blockedSourceCount\":")
				.append(summary.getBlockedSourceCount()).append(',');
			out.append("\"knownObservedInstanceCount\":")
				.append(summary.getKnownObservedInstanceCount()).append('}');
		}
		out.append("],\"sources\":[");
		first = true;
		for (LayeredPackedRegionPreservationBurdenAssessment.SourceAssessment source
			: assessment.getSources()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"safetyContentQuiescent\":")
				.append(source.isSafetyContentQuiescent()).append(',');
			out.append("\"safetyLifecycleReady\":")
				.append(source.isSafetyLifecycleReady()).append(',');
			out.append("\"blockedFamilyCount\":")
				.append(source.getBlockedFamilyCount()).append(',');
			out.append("\"burdenSatisfiedAtObservation\":")
				.append(source.isBurdenSatisfiedAtObservation()).append(',');
			out.append("\"families\":[");
			boolean firstFamily = true;
			for (LayeredPackedRegionPreservationBurdenAssessment.FamilyAssessment family
				: source.getFamilies()) {
				if (!firstFamily) { out.append(','); }
				firstFamily = false;
				out.append('{');
				field(out, "family", family.getFamily().name()).append(',');
				field(out, "policy", family.getPolicy().name()).append(',');
				field(out, "evidenceCompleteness",
					family.getEvidenceCompleteness().name()).append(',');
				out.append("\"observedInstanceCount\":")
					.append(family.getObservedInstanceCount()).append(',');
				out.append("\"preservationSupported\":")
					.append(family.isPreservationSupported()).append(',');
				out.append("\"reloadSupported\":")
					.append(family.isReloadSupported()).append(',');
				out.append("\"burdenSatisfiedAtObservation\":")
					.append(family.isBurdenSatisfiedAtObservation()).append(',');
				out.append("\"blockers\":[");
				boolean firstBlocker = true;
				for (LayeredPackedRegionPreservationBurdenAssessment.Blocker blocker
					: family.getBlockers()) {
					if (!firstBlocker) { out.append(','); }
					firstBlocker = false;
					quoted(out, blocker.name());
				}
				out.append("]}");
			}
			out.append("]}");
		}
		out.append("]}");
	}

	private static void requirePreservationBurdenMatchesProposal(
		final LayeredPackedRegionRetirementRefinementProposal proposal,
		final LayeredPackedRegionPreservationBurdenAssessment assessment) {
		if (proposal.getCandidateSourceCount() != assessment.getSourceCount()) {
			throw new IllegalStateException(
				"Preservation burden source count differs from its proposal");
		}
		for (int index = 0; index < proposal.getCandidateSourceCount(); index++) {
			LayeredPackedRegionRetirementRefinementProposal.CandidateSource candidate =
				proposal.getCandidates().get(index);
			LayeredPackedRegionPreservationBurdenAssessment.SourceAssessment source =
				assessment.getSources().get(index);
			if (candidate.getPackedRegionX() != source.getPackedRegionX()
				|| candidate.getPackedRegionY() != source.getPackedRegionY()) {
				throw new IllegalStateException(
					"Preservation burden source order differs from its proposal");
			}
		}
	}

	private static void appendPackedRegionDynamicObjectPreservation(
		final StringBuilder out,
		final LayeredPackedRegionDynamicObjectPreservationRecord record) {
		out.append('{');
		out.append("\"proposalGeneration\":")
			.append(record.getProposalGeneration()).append(',');
		out.append("\"observedAtTick\":")
			.append(record.getObservedAtTick()).append(',');
		out.append("\"sourceCount\":")
			.append(record.getSourceCount()).append(',');
		out.append("\"dynamicObjectCount\":")
			.append(record.getDynamicObjectCount()).append(',');
		out.append("\"objectsWithRuntimeAttributesCount\":")
			.append(record.getObjectsWithRuntimeAttributesCount()).append(',');
		out.append("\"constructorStateCompleteObjectCount\":")
			.append(record.getConstructorStateCompleteObjectCount()).append(',');
		out.append("\"standaloneRestorationCompleteObjectCount\":")
			.append(record.getStandaloneRestorationCompleteObjectCount()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(record.isPointInTimeOnly()).append(',');
		out.append("\"detachedPrimitiveCopy\":")
			.append(record.isDetachedPrimitiveCopy()).append(',');
		out.append("\"runtimeAttributesCaptured\":")
			.append(record.isRuntimeAttributesCaptured()).append(',');
		out.append("\"eventOwnershipCaptured\":")
			.append(record.isEventOwnershipCaptured()).append(',');
		out.append("\"preservationPerformed\":")
			.append(record.isPreservationPerformed()).append(',');
		out.append("\"reloadRequest\":")
			.append(record.isReloadRequest()).append(',');
		out.append("\"entityRegistry\":")
			.append(record.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":")
			.append(record.isArrivalGate()).append(',');
		out.append("\"teardownTransaction\":")
			.append(record.isTeardownTransaction()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(record.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean firstSource = true;
		for (LayeredPackedRegionDynamicObjectPreservationRecord.SourceRecord source
			: record.getSources()) {
			if (!firstSource) { out.append(','); }
			firstSource = false;
			out.append('{');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"regionPresent\":")
				.append(source.isRegionPresent()).append(',');
			out.append("\"dynamicObjectCount\":")
				.append(source.getDynamicObjectCount()).append(',');
			out.append("\"objects\":[");
			boolean firstObject = true;
			for (LayeredPackedRegionDynamicObjectPreservationRecord
				.DynamicObjectRecord object : source.getDynamicObjects()) {
				if (!firstObject) { out.append(','); }
				firstObject = false;
				out.append('{');
				out.append("\"sourceOrdinal\":")
					.append(object.getSourceOrdinal()).append(',');
				out.append("\"objectId\":")
					.append(object.getObjectId()).append(',');
				out.append("\"permanentObjectId\":")
					.append(object.getPermanentObjectId()).append(',');
				out.append("\"packedX\":")
					.append(object.getX()).append(',');
				out.append("\"packedY\":")
					.append(object.getY()).append(',');
				out.append("\"direction\":")
					.append(object.getDirection()).append(',');
				out.append("\"type\":")
					.append(object.getType()).append(',');
				out.append("\"ownerPresent\":")
					.append(object.hasOwner()).append(',');
				out.append("\"runtimeAttributeCount\":")
					.append(object.getRuntimeAttributeCount()).append(',');
				out.append("\"constructorStateComplete\":")
					.append(object.isConstructorStateComplete()).append(',');
				out.append("\"standaloneRestorationComplete\":")
					.append(object.isStandaloneRestorationComplete()).append('}');
			}
			out.append("]}");
		}
		out.append("]}");
	}

	private static void requireDynamicObjectPreservationMatchesProposal(
		final LayeredPackedRegionRetirementRefinementProposal proposal,
		final LayeredPackedRegionDynamicObjectPreservationRecord record) {
		if (proposal.getGeneration() != record.getProposalGeneration()
			|| proposal.getCandidateSourceCount() != record.getSourceCount()) {
			throw new IllegalStateException(
				"Dynamic-object preservation record differs from its proposal");
		}
		for (int index = 0; index < proposal.getCandidateSourceCount(); index++) {
			LayeredPackedRegionRetirementRefinementProposal.CandidateSource candidate =
				proposal.getCandidates().get(index);
			LayeredPackedRegionDynamicObjectPreservationRecord.SourceRecord source =
				record.getSources().get(index);
			if (candidate.getPackedRegionX() != source.getPackedRegionX()
				|| candidate.getPackedRegionY() != source.getPackedRegionY()) {
				throw new IllegalStateException(
					"Dynamic-object record source order differs from its proposal");
			}
		}
	}

	private static void appendPackedRegionEventOwnership(
		final StringBuilder out,
		final LayeredPackedRegionEventOwnershipInventory inventory) {
		out.append('{');
		out.append("\"proposalGeneration\":")
			.append(inventory.getProposalGeneration()).append(',');
		out.append("\"observedAtTick\":")
			.append(inventory.getObservedAtTick()).append(',');
		out.append("\"sourceCount\":").append(inventory.getSourceCount()).append(',');
		out.append("\"eventCount\":").append(inventory.getEventCount()).append(',');
		out.append("\"spatialReferenceCount\":")
			.append(inventory.getSpatialReferenceCount()).append(',');
		out.append("\"exactSpatialEventCount\":")
			.append(inventory.getExactSpatialEventCount()).append(',');
		out.append("\"ownerPositionHintEventCount\":")
			.append(inventory.getOwnerPositionHintEventCount()).append(',');
		out.append("\"npcOwnerIdentityCapturedEventCount\":")
			.append(inventory.getNpcOwnerIdentityCapturedEventCount()).append(',');
		out.append("\"nonSpatialGlobalEventCount\":")
			.append(inventory.getNonSpatialGlobalEventCount()).append(',');
		out.append("\"unattributedEventCount\":")
			.append(inventory.getUnattributedEventCount()).append(',');
		out.append("\"candidateRelatedEventCount\":")
			.append(inventory.getCandidateRelatedEventCount()).append(',');
		out.append("\"registrationIdentityCapturedEventCount\":")
			.append(inventory.getRegistrationIdentityCapturedEventCount())
			.append(',');
		out.append("\"registrationIdentityCaptured\":")
			.append(inventory.isRegistrationIdentityCaptured()).append(',');
		out.append("\"registrationIdentityComplete\":")
			.append(inventory.isRegistrationIdentityComplete()).append(',');
		out.append("\"schedulerInstanceIdentityCaptured\":")
			.append(inventory.isSchedulerInstanceIdentityCaptured()).append(',');
		field(out, "schedulerInstanceIdentity",
			inventory.getSchedulerInstanceIdentity()).append(',');
		out.append("\"restorationStateAvailableEventCount\":")
			.append(inventory.getRestorationStateAvailableEventCount()).append(',');
		out.append("\"detachedCallbackPayloadCompleteEventCount\":")
			.append(inventory.getDetachedCallbackPayloadCompleteEventCount())
			.append(',');
		out.append("\"executionSemanticsCapturedEventCount\":")
			.append(inventory.getExecutionSemanticsCapturedEventCount())
			.append(',');
		out.append("\"executionSemanticsCaptured\":")
			.append(inventory.isExecutionSemanticsCaptured()).append(',');
		out.append("\"executionSemanticsComplete\":")
			.append(inventory.isExecutionSemanticsComplete()).append(',');
		out.append("\"atomicTimingCapturedEventCount\":")
			.append(inventory.getAtomicTimingCapturedEventCount()).append(',');
		out.append("\"atomicTimingCaptured\":")
			.append(inventory.isAtomicTimingCaptured()).append(',');
		out.append("\"atomicTimingComplete\":")
			.append(inventory.isAtomicTimingComplete()).append(',');
		out.append("\"targetBindingRequirementCapturedEventCount\":")
			.append(inventory.getTargetBindingRequirementCapturedEventCount())
			.append(',');
		out.append("\"targetBindingRequirementCaptured\":")
			.append(inventory.isTargetBindingRequirementCaptured()).append(',');
		out.append("\"targetBindingRequirementComplete\":")
			.append(inventory.isTargetBindingRequirementComplete()).append(',');
		out.append("\"targetBindingCompleteEventCount\":")
			.append(inventory.getTargetBindingCompleteEventCount()).append(',');
		out.append("\"targetBindingComplete\":")
			.append(inventory.isTargetBindingComplete()).append(',');
		out.append("\"arrivalOrderingCapturedEventCount\":")
			.append(inventory.getArrivalOrderingCapturedEventCount()).append(',');
		out.append("\"arrivalOrderingCaptured\":")
			.append(inventory.isArrivalOrderingCaptured()).append(',');
		out.append("\"arrivalOrderingComplete\":")
			.append(inventory.isArrivalOrderingComplete()).append(',');
		out.append("\"generationBindingRequirementCapturedEventCount\":")
			.append(inventory
				.getGenerationBindingRequirementCapturedEventCount()).append(',');
		out.append("\"generationBindingRequirementCaptured\":")
			.append(inventory.isGenerationBindingRequirementCaptured()).append(',');
		out.append("\"generationBindingRequirementComplete\":")
			.append(inventory.isGenerationBindingRequirementComplete()).append(',');
		out.append("\"generationBindingCompleteEventCount\":")
			.append(inventory.getGenerationBindingCompleteEventCount()).append(',');
		out.append("\"generationBindingComplete\":")
			.append(inventory.isGenerationBindingComplete()).append(',');
		out.append("\"idempotencyRequirementCapturedEventCount\":")
			.append(inventory
				.getIdempotencyRequirementCapturedEventCount()).append(',');
		out.append("\"idempotencyRequirementCaptured\":")
			.append(inventory.isIdempotencyRequirementCaptured()).append(',');
		out.append("\"idempotencyRequirementComplete\":")
			.append(inventory.isIdempotencyRequirementComplete()).append(',');
		out.append("\"candidateAttributionComplete\":")
			.append(inventory.isCandidateAttributionComplete()).append(',');
		out.append("\"restorationStateCompleteEventCount\":")
			.append(inventory.getRestorationStateCompleteEventCount()).append(',');
		out.append("\"pointInTimeOnly\":").append(inventory.isPointInTimeOnly()).append(',');
		out.append("\"detachedPrimitiveCopy\":")
			.append(inventory.isDetachedPrimitiveCopy()).append(',');
		out.append("\"callbackStateCaptured\":")
			.append(inventory.isCallbackStateCaptured()).append(',');
		out.append("\"schedulerIdentityCaptured\":")
			.append(inventory.isSchedulerIdentityCaptured()).append(',');
		out.append("\"preservationPerformed\":")
			.append(inventory.isPreservationPerformed()).append(',');
		out.append("\"reloadRequest\":").append(inventory.isReloadRequest()).append(',');
		out.append("\"eventCancellation\":")
			.append(inventory.isEventCancellation()).append(',');
		out.append("\"eventReschedule\":")
			.append(inventory.isEventReschedule()).append(',');
		out.append("\"entityRegistry\":").append(inventory.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":").append(inventory.isArrivalGate()).append(',');
		out.append("\"teardownTransaction\":")
			.append(inventory.isTeardownTransaction()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(inventory.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean firstSource = true;
		for (LayeredPackedRegionEventOwnershipInventory.SourceRecord source
			: inventory.getSources()) {
			if (!firstSource) { out.append(','); }
			firstSource = false;
			out.append('{');
			out.append("\"packedRegionX\":").append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":").append(source.getPackedRegionY()).append(',');
			out.append("\"exactSpatialEventCount\":")
				.append(source.getExactSpatialEventCount()).append(',');
			appendIntegerList(out, "exactSpatialEventOrdinals",
				source.getExactSpatialEventOrdinals());
			out.append(',');
			out.append("\"ownerPositionHintEventCount\":")
				.append(source.getOwnerPositionHintEventCount()).append(',');
			appendIntegerList(out, "ownerPositionHintEventOrdinals",
				source.getOwnerPositionHintEventOrdinals());
			out.append(',');
			out.append("\"restorationStateEventCount\":")
				.append(source.getRestorationStateEventCount()).append(',');
			appendIntegerList(out, "restorationStateEventOrdinals",
				source.getRestorationStateEventOrdinals());
			out.append(',');
			out.append("\"unattributedEventCount\":")
				.append(source.getUnattributedEventCount()).append(',');
			out.append("\"attributionComplete\":")
				.append(source.isAttributionComplete()).append('}');
		}
		out.append("],\"events\":[");
		boolean firstEvent = true;
		for (LayeredPackedRegionEventOwnershipInventory.EventRecord event
			: inventory.getEvents()) {
			if (!firstEvent) { out.append(','); }
			firstEvent = false;
			out.append('{');
			out.append("\"snapshotOrdinal\":").append(event.getSnapshotOrdinal()).append(',');
			out.append("\"registrationSequence\":")
				.append(event.getRegistrationSequence()).append(',');
			field(out, "ownerKind", event.getOwnerKind().name()).append(',');
			out.append("\"npcOwnerIdentity\":");
			if (event.getNpcOwnerIdentity() == null) {
				out.append("null");
			} else {
				LayeredPackedRegionEventOwnershipInventory.NpcOwnerIdentity
					identity = event.getNpcOwnerIdentity();
				out.append('{');
				out.append("\"generation\":")
					.append(identity.getGeneration()).append(',');
				out.append("\"packedRegionX\":")
					.append(identity.getPackedRegionX()).append(',');
				out.append("\"packedRegionY\":")
					.append(identity.getPackedRegionY()).append(',');
				out.append("\"sourceOrdinal\":")
					.append(identity.getSourceOrdinal()).append(',');
				out.append("\"runtimeNpcId\":")
					.append(identity.getRuntimeNpcId()).append('}');
			}
			out.append(',');
			field(out, "attributionKind", event.getAttributionKind().name()).append(',');
			out.append("\"running\":").append(event.isRunning()).append(',');
			out.append("\"ticksBeforeRun\":").append(event.getTicksBeforeRun()).append(',');
			out.append("\"timesRan\":").append(event.getTimesRan()).append(',');
			out.append("\"atomicTimingCaptured\":")
				.append(event.isAtomicTimingCaptured()).append(',');
			out.append("\"candidateRelated\":").append(event.isCandidateRelated()).append(',');
			out.append("\"attributionComplete\":").append(event.isAttributionComplete()).append(',');
			out.append("\"restorationStateComplete\":")
				.append(event.isRestorationStateComplete()).append(',');
			out.append("\"restorationState\":");
			appendEventRestorationState(
				out, event.getRestorationState(),
				event.isAtomicTimingCaptured(),
				inventory.getProposalGeneration());
			out.append(',');
			appendIntegerList(out, "candidateSourceOrdinals",
				event.getCandidateSourceOrdinals());
			out.append(",\"spatialReferences\":[");
			boolean firstReference = true;
			for (LayeredPackedRegionEventOwnershipInventory.SpatialReference reference
				: event.getSpatialReferences()) {
				if (!firstReference) { out.append(','); }
				firstReference = false;
				out.append('{');
				field(out, "role", reference.getRole().name()).append(',');
				out.append("\"packedX\":").append(reference.getX()).append(',');
				out.append("\"packedY\":").append(reference.getY()).append('}');
			}
			out.append("]}");
		}
		out.append("]}");
	}

	private static void appendPackedRegionNpcOwnerEventContinuity(
		final StringBuilder out,
		final LayeredPackedRegionNpcOwnerEventContinuityAssessment assessment) {
		out.append('{');
		out.append("\"generation\":").append(assessment.getGeneration())
			.append(',');
		out.append("\"eventObservedAtTick\":")
			.append(assessment.getEventObservedAtTick()).append(',');
		out.append("\"censusObservedAtTick\":")
			.append(assessment.getCensusObservedAtTick()).append(',');
		out.append("\"selectedSourceCount\":")
			.append(assessment.getSelectedSourceCount()).append(',');
		out.append("\"proposalRelatedEventCount\":")
			.append(assessment.getProposalRelatedEventCount()).append(',');
		out.append("\"relatedOwnerPositionHintEventCount\":")
			.append(assessment.getRelatedOwnerPositionHintEventCount())
			.append(',');
		out.append("\"npcOwnerPositionHintEventCount\":")
			.append(assessment.getNpcOwnerPositionHintEventCount()).append(',');
		out.append("\"capturedNpcOwnerIdentityCount\":")
			.append(assessment.getCapturedNpcOwnerIdentityCount()).append(',');
		out.append("\"uniquelyMatchedActiveOwnerCount\":")
			.append(assessment.getUniquelyMatchedActiveOwnerCount()).append(',');
		out.append("\"continuityEligibleEventCount\":")
			.append(assessment.getContinuityEligibleEventCount()).append(',');
		out.append("\"preservationUnprovedEventCount\":")
			.append(assessment.getPreservationUnprovedEventCount()).append(',');
		out.append("\"hardBlockerEventCount\":")
			.append(assessment.getHardBlockerEventCount()).append(',');
		out.append("\"exactSelectionAligned\":")
			.append(assessment.isExactSelectionAligned()).append(',');
		out.append("\"ownerPreservationProved\":")
			.append(assessment.isOwnerPreservationProved()).append(',');
		out.append("\"allRelatedOwnerContinuityReadyAtObservation\":")
			.append(assessment
				.isAllRelatedOwnerContinuityReadyAtObservation()).append(',');
		out.append("\"firstUnmetRegistrationSequence\":");
		if (assessment.getFirstUnmetRegistrationSequence() == null) {
			out.append("null");
		} else {
			out.append(assessment.getFirstUnmetRegistrationSequence());
		}
		out.append(",\"firstUnmetOutcome\":");
		if (assessment.getFirstUnmetOutcome() == null) {
			out.append("null");
		} else {
			quoted(out, assessment.getFirstUnmetOutcome().name());
		}
		out.append(",\"pointInTimeOnly\":")
			.append(assessment.isPointInTimeOnly()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(assessment.isRuntimeHandleRetained()).append(',');
		out.append("\"preservationPerformed\":")
			.append(assessment.isPreservationPerformed()).append(',');
		out.append("\"eventReschedule\":")
			.append(assessment.isEventReschedule()).append(',');
		out.append("\"entityRegistry\":")
			.append(assessment.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":")
			.append(assessment.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(assessment.isLifecycleAuthority()).append(',');
		out.append("\"events\":[");
		boolean first = true;
		for (LayeredPackedRegionNpcOwnerEventContinuityAssessment
			.EventAssessment event : assessment.getEvents()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"snapshotOrdinal\":")
				.append(event.getSnapshotOrdinal()).append(',');
			out.append("\"registrationSequence\":")
				.append(event.getRegistrationSequence()).append(',');
			field(out, "ownerKind", event.getOwnerKind().name()).append(',');
			out.append("\"npcOwnerIdentityCaptured\":")
				.append(event.isNpcOwnerIdentityCaptured()).append(',');
			field(out, "outcome", event.getOutcome().name()).append(',');
			out.append("\"activeIdentityMatchCount\":")
				.append(event.getActiveIdentityMatchCount()).append(',');
			out.append("\"matchedIdentityStatus\":");
			if (event.getMatchedIdentityStatus() == null) {
				out.append("null");
			} else {
				quoted(out, event.getMatchedIdentityStatus().name());
			}
			out.append(",\"matchedClassification\":");
			if (event.getMatchedClassification() == null) {
				out.append("null");
			} else {
				quoted(out, event.getMatchedClassification().name());
			}
			out.append(",\"uniqueActiveOwnerMatch\":")
				.append(event.isUniqueActiveOwnerMatch()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionNpcOwnerPreservationBoundary(
		final StringBuilder out,
		final LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
			observation) {
		out.append('{');
		out.append("\"generation\":").append(observation.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(observation.getRequirementsObservedAtTick()).append(',');
		out.append("\"boundaryObservedAtTick\":")
			.append(observation.getBoundaryObservedAtTick()).append(',');
		field(out, "schedulerInstanceIdentity",
			observation.getSchedulerInstanceIdentity()).append(',');
		out.append("\"selectedSourceCount\":")
			.append(observation.getSelectedSourceCount()).append(',');
		out.append("\"proposalRelatedEventCount\":")
			.append(observation.getProposalRelatedEventCount()).append(',');
		out.append("\"relatedOwnerPositionHintEventCount\":")
			.append(observation.getRelatedOwnerPositionHintEventCount())
			.append(',');
		out.append("\"npcOwnerEventCount\":")
			.append(observation.getNpcOwnerEventCount()).append(',');
		out.append("\"separateNonNpcOwnerEventCount\":")
			.append(observation.getSeparateNonNpcOwnerEventCount())
			.append(',');
		out.append("\"preservationRequiredEventCount\":")
			.append(observation.getPreservationRequiredEventCount())
			.append(',');
		out.append("\"previouslyEligibleEventCount\":")
			.append(observation.getPreviouslyEligibleEventCount()).append(',');
		out.append("\"npcHardBlockerEventCount\":")
			.append(observation.getNpcHardBlockerEventCount()).append(',');
		out.append("\"requiredOwnerCount\":")
			.append(observation.getRequiredOwnerCount()).append(',');
		out.append("\"relatedEventLinkCount\":")
			.append(observation.getRelatedEventLinkCount()).append(',');
		out.append("\"supportingEventLinkCount\":")
			.append(observation.getSupportingEventLinkCount()).append(',');
		out.append("\"requiredEventLinkCount\":")
			.append(observation.getRequiredEventLinkCount()).append(',');
		out.append("\"schedulerInstanceMatched\":")
			.append(observation.isSchedulerInstanceMatched()).append(',');
		out.append("\"registrationSetComplete\":")
			.append(observation.isRegistrationSetComplete()).append(',');
		out.append("\"eventExecutionBoundaryCount\":")
			.append(observation.getEventExecutionBoundaryCount()).append(',');
		out.append("\"eventTimingBoundaryCount\":")
			.append(observation.getEventTimingBoundaryCount()).append(',');
		out.append("\"worldRegistrationBoundaryHeld\":")
			.append(observation.isWorldRegistrationBoundaryHeld()).append(',');
		out.append("\"npcLifecycleBoundaryCount\":")
			.append(observation.getNpcLifecycleBoundaryCount()).append(',');
		out.append("\"regionAbsenceQuiescenceHeld\":")
			.append(observation.isRegionAbsenceQuiescenceHeld()).append(',');
		out.append("\"exactReferenceOwnerCount\":")
			.append(observation.getExactReferenceOwnerCount()).append(',');
		field(out, "reason", observation.getReason().name()).append(',');
		out.append("\"referenceBoundaryComplete\":")
			.append(observation.isReferenceBoundaryComplete()).append(',');
		out.append("\"preservationScopeReadyAtBoundary\":")
			.append(observation.isPreservationScopeReadyAtBoundary())
			.append(',');
		out.append("\"pointInTimeOnly\":")
			.append(observation.isPointInTimeOnly()).append(',');
		out.append("\"preservationFactEstablished\":")
			.append(observation.isPreservationFactEstablished()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(observation.isRuntimeHandleRetained()).append(',');
		out.append("\"preservationPerformed\":")
			.append(observation.isPreservationPerformed()).append(',');
		out.append("\"eventReschedule\":")
			.append(observation.isEventReschedule()).append(',');
		out.append("\"entityRegistry\":")
			.append(observation.isEntityRegistry()).append(',');
		out.append("\"arrivalGate\":")
			.append(observation.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(observation.isLifecycleAuthority()).append(',');
		out.append("\"owners\":[");
		boolean first = true;
		for (LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
			.OwnerEvidence owner : observation.getOwners()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"generation\":").append(owner.getGeneration())
				.append(',');
			out.append("\"packedRegionX\":")
				.append(owner.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(owner.getPackedRegionY()).append(',');
			out.append("\"sourceOrdinal\":")
				.append(owner.getSourceOrdinal()).append(',');
			out.append("\"runtimeNpcId\":")
				.append(owner.getRuntimeNpcId()).append(',');
			out.append("\"requiredEventLinkCount\":")
				.append(owner.getRequiredEventLinkCount()).append(',');
			out.append("\"validatedEventLinkCount\":")
				.append(owner.getValidatedEventLinkCount()).append(',');
			out.append("\"worldIdentityMatchCount\":")
				.append(owner.getWorldIdentityMatchCount()).append(',');
			field(out, "outcome", owner.getOutcome().name());
			out.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionNpcOwnerPreservationNoOp(
		final StringBuilder out,
		final PackedRegionNpcOwnerPreservationNoOpMetadata diagnostic) {
		out.append('{');
		field(out, "reason", diagnostic.getReason()).append(',');
		out.append("\"generation\":").append(diagnostic.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(diagnostic.getRequirementsObservedAtTick()).append(',');
		out.append("\"selectedSourceCount\":")
			.append(diagnostic.getSelectedSourceCount()).append(',');
		out.append("\"requiredEventLinkCount\":")
			.append(diagnostic.getRequiredEventLinkCount()).append(',');
		out.append("\"requiredOwnerCount\":")
			.append(diagnostic.getRequiredOwnerCount()).append(',');
		out.append("\"ownerScopeEntered\":")
			.append(diagnostic.isOwnerScopeEntered()).append(',');
		out.append("\"sourceLifecycleInvoked\":")
			.append(diagnostic.isSourceLifecycleInvoked()).append(',');
		out.append("\"absentSourceCount\":")
			.append(diagnostic.getAbsentSourceCount()).append(',');
		out.append("\"reconstructedSourceCount\":")
			.append(diagnostic.getReconstructedSourceCount()).append(',');
		out.append("\"preservedConsumerInvoked\":")
			.append(diagnostic.isPreservedConsumerInvoked()).append(',');
		out.append("\"sourceAbsencePreflight\":");
		if (diagnostic.getSourceAbsencePreflight() == null) {
			out.append("null");
		} else {
			appendPackedRegionSourceAbsencePreflight(
				out, diagnostic.getSourceAbsencePreflight());
		}
		out.append(',');
		out.append("\"sourceReloadRecipe\":");
		if (diagnostic.getSourceReloadRecipe() == null) {
			out.append("null");
		} else {
			appendPackedRegionSourceReloadRecipe(
				out, diagnostic.getSourceReloadRecipe());
		}
		out.append(',');
		out.append("\"sourceTerrainVerification\":");
		if (diagnostic.getSourceTerrainVerification() == null) {
			out.append("null");
		} else {
			appendPackedRegionTerrainVerificationBatch(
				out, diagnostic.getSourceTerrainVerification());
		}
		out.append(',');
		out.append("\"sourceAuthoredCollisionVerification\":");
		if (diagnostic.getSourceAuthoredCollisionVerification() == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredCollisionVerificationBatch(
				out, diagnostic.getSourceAuthoredCollisionVerification());
		}
		out.append(',');
		out.append(
			"\"sourceAuthoredCollisionApplicationVerification\":");
		if (diagnostic
				.getSourceAuthoredCollisionApplicationVerification() == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredCollisionApplicationVerificationBatch(
				out, diagnostic
					.getSourceAuthoredCollisionApplicationVerification());
		}
		out.append(',');
		out.append("\"sourceAuthoredStateVerification\":");
		if (diagnostic.getSourceAuthoredStateVerification() == null) {
			out.append("null");
		} else {
			appendPackedRegionAuthoredSourceStateVerificationBatch(
				out, diagnostic.getSourceAuthoredStateVerification());
		}
		out.append(',');
		out.append("\"sourceTransactionalAuthoredStateVerification\":");
		if (diagnostic
				.getSourceTransactionalAuthoredStateVerification() == null) {
			out.append("null");
		} else {
			appendPackedRegionTransactionalAuthoredSourceVerificationBatch(
				out, diagnostic
					.getSourceTransactionalAuthoredStateVerification());
		}
		out.append(',');
		out.append("\"preservationEstablishedForConsumedWork\":false,");
		out.append("\"preservationPerformed\":false,");
		out.append("\"sourceAbsencePerformed\":false,");
		out.append("\"sourceReconstructionPerformed\":false,");
		out.append("\"regionMutationPerformed\":false,");
		out.append("\"runtimeHandleRetained\":false,");
		out.append("\"arrivalGate\":false,");
		out.append("\"visibilityReleased\":false,");
		out.append("\"lifecycleAuthority\":false}");
	}

	private static void appendPackedRegionSourceAbsencePreflight(
		final StringBuilder out,
		final LayeredPackedRegionSourceAbsencePreflight preflight) {
		out.append('{');
		out.append("\"generation\":").append(preflight.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(preflight.getRequirementsObservedAtTick()).append(',');
		out.append("\"observedAtTick\":")
			.append(preflight.getObservedAtTick()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(preflight.getResidencyMirrorVersion()).append(',');
		out.append("\"sourceCount\":").append(preflight.getSourceCount())
			.append(',');
		out.append("\"readySourceCount\":")
			.append(preflight.getReadySourceCount()).append(',');
		out.append("\"blockedSourceCount\":")
			.append(preflight.getBlockedSourceCount()).append(',');
		out.append("\"absenceReadyAtObservation\":")
			.append(preflight.isAbsenceReadyAtObservation()).append(',');
		out.append("\"totals\":{");
		out.append("\"players\":").append(preflight.getPlayerCount())
			.append(',');
		out.append("\"npcs\":").append(preflight.getNpcCount()).append(',');
		out.append("\"authoredObjects\":")
			.append(preflight.getAuthoredObjectCount()).append(',');
		out.append("\"dynamicObjects\":")
			.append(preflight.getDynamicObjectCount()).append(',');
		out.append("\"groundItems\":")
			.append(preflight.getGroundItemCount()).append(',');
		out.append("\"collisionProductTiles\":")
			.append(preflight.getCollisionProductTileCount()).append("},");
		out.append("\"blockerSummaries\":[");
		boolean first = true;
		for (LayeredPackedRegionSourceAbsencePreflight.BlockerSummary summary
			: preflight.getBlockerSummaries()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			field(out, "blocker", summary.getBlocker().name()).append(',');
			out.append("\"blockedSourceCount\":")
				.append(summary.getBlockedSourceCount()).append('}');
		}
		out.append("],\"pointInTimeOnly\":")
			.append(preflight.isPointInTimeOnly()).append(',');
		out.append("\"sourceAbsencePerformed\":")
			.append(preflight.isSourceAbsencePerformed()).append(',');
		out.append("\"sourceReconstructionPerformed\":")
			.append(preflight.isSourceReconstructionPerformed()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(preflight.isRuntimeHandleRetained()).append(',');
		out.append("\"regionRegistryMutated\":")
			.append(preflight.isRegionRegistryMutated()).append(',');
		out.append("\"residencyMirrorMutated\":")
			.append(preflight.isResidencyMirrorMutated()).append(',');
		out.append("\"visibilityCacheMutated\":")
			.append(preflight.isVisibilityCacheMutated()).append(',');
		out.append("\"arrivalGate\":")
			.append(preflight.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(preflight.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		first = true;
		for (LayeredPackedRegionSourceAbsencePreflight.SourceAssessment source
			: preflight.getSources()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"tileStorageAvailable\":")
				.append(source.isTileStorageAvailable()).append(',');
			out.append("\"playerCount\":").append(source.getPlayerCount())
				.append(',');
			out.append("\"npcCount\":").append(source.getNpcCount())
				.append(',');
			out.append("\"authoredObjectCount\":")
				.append(source.getAuthoredObjectCount()).append(',');
			out.append("\"dynamicObjectCount\":")
				.append(source.getDynamicObjectCount()).append(',');
			out.append("\"groundItemCount\":")
				.append(source.getGroundItemCount()).append(',');
			out.append("\"collisionProductTileCount\":")
				.append(source.getCollisionProductTileCount()).append(',');
			out.append("\"absenceReadyAtObservation\":")
				.append(source.isAbsenceReadyAtObservation()).append(',');
			out.append("\"blockers\":[");
			boolean firstBlocker = true;
			for (LayeredPackedRegionSourceAbsencePreflight.Blocker blocker
				: source.getBlockers()) {
				if (!firstBlocker) { out.append(','); }
				firstBlocker = false;
				quoted(out, blocker.name());
			}
			out.append("]}");
		}
		out.append("]}");
	}

	private static void appendPackedRegionSourceReloadRecipe(
		final StringBuilder out,
		final LayeredPackedRegionReloadRecipe recipe) {
		out.append('{');
		out.append("\"generation\":").append(recipe.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(recipe.getRequirementsObservedAtTick()).append(',');
		out.append("\"observedAtTick\":")
			.append(recipe.getObservedAtTick()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(recipe.getResidencyMirrorVersion()).append(',');
		out.append("\"authoredGeneration\":")
			.append(recipe.getAuthoredGeneration()).append(',');
		out.append("\"sourceCount\":").append(recipe.getSourceCount())
			.append(',');
		out.append("\"authoredSourceCount\":")
			.append(recipe.getAuthoredSourceCount()).append(',');
		out.append("\"emptyAuthoredSourceCount\":")
			.append(recipe.getEmptyAuthoredSourceCount()).append(',');
		out.append("\"authoredPlacementCount\":")
			.append(recipe.getAuthoredPlacementCount()).append(',');
		out.append("\"manifestPlacementCount\":")
			.append(recipe.getManifestPlacementCount()).append(',');
		out.append("\"supersededPlacementCount\":")
			.append(recipe.getSupersededPlacementCount()).append(',');
		out.append("\"affectedSourceReferenceCount\":")
			.append(recipe.getAffectedSourceReferenceCount()).append(',');
		out.append("\"unresolvedTotals\":{");
		out.append("\"players\":").append(recipe.getPlayerCount())
			.append(',');
		out.append("\"npcs\":").append(recipe.getNpcCount()).append(',');
		out.append("\"dynamicObjects\":")
			.append(recipe.getDynamicObjectCount()).append(',');
		out.append("\"groundItems\":")
			.append(recipe.getGroundItemCount()).append(',');
		out.append("\"collisionProductTiles\":")
			.append(recipe.getCollisionProductTileCount()).append("},");
		out.append("\"pointInTimeOnly\":")
			.append(recipe.isPointInTimeOnly()).append(',');
		out.append("\"detachedDefinitionComplete\":")
			.append(recipe.isDetachedDefinitionComplete()).append(',');
		out.append("\"executableReload\":")
			.append(recipe.isExecutableReload()).append(',');
		out.append("\"regionContainerCreated\":")
			.append(recipe.isRegionContainerCreated()).append(',');
		out.append("\"sourceAbsencePerformed\":")
			.append(recipe.isSourceAbsencePerformed()).append(',');
		out.append("\"sourceReconstructionPerformed\":")
			.append(recipe.isSourceReconstructionPerformed()).append(',');
		out.append("\"authoredReplayPerformed\":")
			.append(recipe.isAuthoredReplayPerformed()).append(',');
		out.append("\"collisionRebuildPerformed\":")
			.append(recipe.isCollisionRebuildPerformed()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(recipe.isRuntimeHandleRetained()).append(',');
		out.append("\"regionRegistryMutated\":")
			.append(recipe.isRegionRegistryMutated()).append(',');
		out.append("\"residencyMirrorMutated\":")
			.append(recipe.isResidencyMirrorMutated()).append(',');
		out.append("\"visibilityCacheMutated\":")
			.append(recipe.isVisibilityCacheMutated()).append(',');
		out.append("\"arrivalGate\":")
			.append(recipe.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(recipe.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean first = true;
		for (LayeredPackedRegionReloadRecipe.SourceRecipe source
			: recipe.getSources()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"tileStorageAvailableAtObservation\":")
				.append(source.isTileStorageAvailableAtObservation())
				.append(',');
			out.append("\"playerCountAtObservation\":")
				.append(source.getPlayerCountAtObservation()).append(',');
			out.append("\"npcCountAtObservation\":")
				.append(source.getNpcCountAtObservation()).append(',');
			out.append("\"dynamicObjectCountAtObservation\":")
				.append(source.getDynamicObjectCountAtObservation())
				.append(',');
			out.append("\"groundItemCountAtObservation\":")
				.append(source.getGroundItemCountAtObservation())
				.append(',');
			out.append("\"collisionProductTileCountAtObservation\":")
				.append(source.getCollisionProductTileCountAtObservation())
				.append(',');
			out.append("\"authoredSourceDeclared\":")
				.append(source.isAuthoredSourceDeclared()).append(',');
			out.append("\"emptyAuthoredReplay\":")
				.append(source.isEmptyAuthoredReplay()).append(',');
			out.append("\"manifestPlacementCount\":")
				.append(source.getManifestPlacementCount()).append(',');
			out.append("\"supersededPlacementCount\":")
				.append(source.getSupersededPlacementCount()).append(',');
			out.append("\"authoredPlacementCount\":")
				.append(source.getAuthoredPlacementCount()).append(',');
			out.append("\"affectedSourceReferenceCount\":")
				.append(source.getAffectedSourceReferenceCount()).append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionTerrainVerificationBatch(
		final StringBuilder out,
		final LayeredPackedRegionTerrainVerificationBatch verification) {
		out.append('{');
		out.append("\"generation\":").append(verification.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(verification.getRequirementsObservedAtTick()).append(',');
		out.append("\"observedAtTick\":")
			.append(verification.getObservedAtTick()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(verification.getResidencyMirrorVersion()).append(',');
		out.append("\"authoredGeneration\":")
			.append(verification.getAuthoredGeneration()).append(',');
		out.append("\"sourceCount\":")
			.append(verification.getSourceCount()).append(',');
		out.append("\"verifiedTileCount\":")
			.append(verification.getVerifiedTileCount()).append(',');
		out.append("\"terrainBlockedTileCount\":")
			.append(verification.getTerrainBlockedTileCount()).append(',');
		out.append("\"terrainCollisionMaskTileCount\":")
			.append(verification.getTerrainCollisionMaskTileCount())
			.append(',');
		out.append("\"terrainProjectileBlockedTileCount\":")
			.append(verification.getTerrainProjectileBlockedTileCount())
			.append(',');
		out.append("\"sealedBaseTraversalTileCount\":")
			.append(verification.getSealedBaseTraversalTileCount())
			.append(',');
		out.append("\"disposableRegionConstructionCount\":")
			.append(verification.getDisposableRegionConstructionCount())
			.append(',');
		out.append("\"disposableTerrainApplyCount\":")
			.append(verification.getDisposableTerrainApplyCount())
			.append(',');
		out.append("\"usableRegionContainerCount\":")
			.append(verification.getUsableRegionContainerCount()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(verification.isPointInTimeOnly()).append(',');
		out.append("\"detachedSummaryOnly\":")
			.append(verification.isDetachedSummaryOnly()).append(',');
		out.append("\"allSourcesVerified\":")
			.append(verification.isAllSourcesVerified()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(verification.isRuntimeHandleRetained()).append(',');
		out.append("\"sourceAbsencePerformed\":")
			.append(verification.isSourceAbsencePerformed()).append(',');
		out.append("\"sourceReconstructionPerformed\":")
			.append(verification.isSourceReconstructionPerformed())
			.append(',');
		out.append("\"terrainAppliedToRuntimeSource\":")
			.append(verification.isTerrainAppliedToRuntimeSource())
			.append(',');
		out.append("\"authoredReplayPerformed\":")
			.append(verification.isAuthoredReplayPerformed()).append(',');
		out.append("\"dynamicCollisionRebuildPerformed\":")
			.append(verification.isDynamicCollisionRebuildPerformed())
			.append(',');
		out.append("\"activeFamilyPreservationPerformed\":")
			.append(verification.isActiveFamilyPreservationPerformed())
			.append(',');
		out.append("\"regionRegistryMutated\":")
			.append(verification.isRegionRegistryMutated()).append(',');
		out.append("\"residencyMirrorMutated\":")
			.append(verification.isResidencyMirrorMutated()).append(',');
		out.append("\"visibilityCacheMutated\":")
			.append(verification.isVisibilityCacheMutated()).append(',');
		out.append("\"arrivalGate\":")
			.append(verification.isArrivalGate()).append(',');
		out.append("\"visibilityReleased\":")
			.append(verification.isVisibilityReleased()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(verification.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean first = true;
		for (LayeredPackedRegionTerrainVerificationBatch.SourceVerification
			source : verification.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append('{');
			out.append("\"sourceOrdinal\":")
				.append(source.getSourceOrdinal()).append(',');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"verifiedTileCount\":")
				.append(source.getVerifiedTileCount()).append(',');
			out.append("\"terrainBlockedTileCount\":")
				.append(source.getTerrainBlockedTileCount()).append(',');
			out.append("\"terrainCollisionMaskTileCount\":")
				.append(source.getTerrainCollisionMaskTileCount()).append(',');
			out.append("\"terrainProjectileBlockedTileCount\":")
				.append(source.getTerrainProjectileBlockedTileCount())
				.append(',');
			out.append("\"sealedBaseTraversalTileCount\":")
				.append(source.getSealedBaseTraversalTileCount()).append(',');
			field(out, "terrainFingerprintSha256",
				source.getTerrainFingerprintSha256());
			out.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredCollisionVerificationBatch(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredCollisionVerificationBatch
			verification) {
		out.append('{');
		out.append("\"generation\":").append(verification.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(verification.getRequirementsObservedAtTick()).append(',');
		out.append("\"observedAtTick\":")
			.append(verification.getObservedAtTick()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(verification.getResidencyMirrorVersion()).append(',');
		out.append("\"authoredGeneration\":")
			.append(verification.getAuthoredGeneration()).append(',');
		out.append("\"sourceCount\":")
			.append(verification.getSourceCount()).append(',');
		out.append("\"replayPlacementCount\":")
			.append(verification.getReplayPlacementCount()).append(',');
		out.append("\"authoredObjectFootprintCount\":")
			.append(verification.getAuthoredObjectFootprintCount())
			.append(',');
		out.append("\"definitionBackedObjectCount\":")
			.append(verification.getDefinitionBackedObjectCount()).append(',');
		out.append("\"specialCollisionlessObjectCount\":")
			.append(verification.getSpecialCollisionlessObjectCount())
			.append(',');
		out.append("\"zeroContributionObjectCount\":")
			.append(verification.getZeroContributionObjectCount()).append(',');
		out.append("\"crossSourceCollisionObjectCount\":")
			.append(verification.getCrossSourceCollisionObjectCount())
			.append(',');
		out.append("\"collisionBeyondAuthoredDependencyObjectCount\":")
			.append(verification
				.getCollisionBeyondAuthoredDependencyObjectCount())
			.append(',');
		out.append("\"contributionTileReferenceCount\":")
			.append(verification.getContributionTileReferenceCount())
			.append(',');
		out.append("\"requiredRegionReferenceCount\":")
			.append(verification.getRequiredRegionReferenceCount())
			.append(',');
		out.append("\"uniqueRequiredRegionReferenceCount\":")
			.append(verification.getUniqueRequiredRegionReferenceCount())
			.append(',');
		field(out, "fingerprintSha256",
			verification.getFingerprintSha256()).append(',');
		out.append("\"disposableRegionConstructionCount\":")
			.append(verification.getDisposableRegionConstructionCount())
			.append(',');
		out.append("\"disposableTerrainApplyCount\":")
			.append(verification.getDisposableTerrainApplyCount())
			.append(',');
		out.append("\"disposableObjectMembershipApplyCount\":")
			.append(verification
				.getDisposableObjectMembershipApplyCount())
			.append(',');
		out.append("\"usableRegionContainerCount\":")
			.append(verification.getUsableRegionContainerCount()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(verification.isPointInTimeOnly()).append(',');
		out.append("\"detachedSummaryOnly\":")
			.append(verification.isDetachedSummaryOnly()).append(',');
		out.append("\"allSourcesVerified\":")
			.append(verification.isAllSourcesVerified()).append(',');
		out.append("\"runtimeDefinitionCapturePerformed\":")
			.append(verification.isRuntimeDefinitionCapturePerformed())
			.append(',');
		out.append("\"collisionFootprintDerivationPerformed\":")
			.append(verification.isCollisionFootprintDerivationPerformed())
			.append(',');
		out.append("\"collisionApplied\":")
			.append(verification.isCollisionApplied()).append(',');
		out.append("\"collisionRegistrationAttached\":")
			.append(verification.isCollisionRegistrationAttached())
			.append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(verification.isRuntimeHandleRetained()).append(',');
		out.append("\"sourceAbsencePerformed\":")
			.append(verification.isSourceAbsencePerformed()).append(',');
		out.append("\"sourceReconstructionPerformed\":")
			.append(verification.isSourceReconstructionPerformed())
			.append(',');
		out.append("\"terrainAppliedToRuntimeSource\":")
			.append(verification.isTerrainAppliedToRuntimeSource())
			.append(',');
		out.append("\"npcMembershipApplied\":")
			.append(verification.isNpcMembershipApplied()).append(',');
		out.append("\"groundItemMembershipApplied\":")
			.append(verification.isGroundItemMembershipApplied()).append(',');
		out.append("\"schedulerStateRestored\":")
			.append(verification.isSchedulerStateRestored()).append(',');
		out.append("\"activeFamilyPreservationPerformed\":")
			.append(verification.isActiveFamilyPreservationPerformed())
			.append(',');
		out.append("\"regionRegistryMutated\":")
			.append(verification.isRegionRegistryMutated()).append(',');
		out.append("\"residencyMirrorMutated\":")
			.append(verification.isResidencyMirrorMutated()).append(',');
		out.append("\"visibilityCacheMutated\":")
			.append(verification.isVisibilityCacheMutated()).append(',');
		out.append("\"arrivalGate\":")
			.append(verification.isArrivalGate()).append(',');
		out.append("\"visibilityReleased\":")
			.append(verification.isVisibilityReleased()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(verification.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean first = true;
		for (
			LayeredPackedRegionAuthoredCollisionVerificationBatch
				.SourceVerification source
			: verification.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append('{');
			out.append("\"sourceOrdinal\":")
				.append(source.getSourceOrdinal()).append(',');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"replayPlacementCount\":")
				.append(source.getReplayPlacementCount()).append(',');
			out.append("\"authoredObjectFootprintCount\":")
				.append(source.getAuthoredObjectFootprintCount()).append(',');
			out.append("\"definitionBackedObjectCount\":")
				.append(source.getDefinitionBackedObjectCount()).append(',');
			out.append("\"specialCollisionlessObjectCount\":")
				.append(source.getSpecialCollisionlessObjectCount())
				.append(',');
			out.append("\"zeroContributionObjectCount\":")
				.append(source.getZeroContributionObjectCount()).append(',');
			out.append("\"crossSourceCollisionObjectCount\":")
				.append(source.getCrossSourceCollisionObjectCount())
				.append(',');
			out.append("\"collisionBeyondAuthoredDependencyObjectCount\":")
				.append(source
					.getCollisionBeyondAuthoredDependencyObjectCount())
				.append(',');
			out.append("\"contributionTileReferenceCount\":")
				.append(source.getContributionTileReferenceCount())
				.append(',');
			out.append("\"requiredRegionReferenceCount\":")
				.append(source.getRequiredRegionReferenceCount()).append(',');
			out.append("\"uniqueRequiredRegionCount\":")
				.append(source.getUniqueRequiredRegionCount()).append(',');
			field(out, "terrainFingerprintSha256",
				source.getTerrainFingerprintSha256()).append(',');
			field(out, "authoredReplayFingerprintSha256",
				source.getAuthoredReplayFingerprintSha256()).append(',');
			field(out, "definitionCaptureFingerprintSha256",
				source.getDefinitionCaptureFingerprintSha256()).append(',');
			field(out, "collisionFootprintFingerprintSha256",
				source.getCollisionFootprintFingerprintSha256());
			out.append('}');
		}
		out.append("]}");
	}

	private static void
		appendPackedRegionAuthoredCollisionApplicationVerificationBatch(
			final StringBuilder out,
			final
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					verification) {
		out.append('{');
		out.append("\"generation\":").append(verification.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(verification.getRequirementsObservedAtTick()).append(',');
		out.append("\"observedAtTick\":")
			.append(verification.getObservedAtTick()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(verification.getResidencyMirrorVersion()).append(',');
		out.append("\"authoredGeneration\":")
			.append(verification.getAuthoredGeneration()).append(',');
		out.append("\"sourceCount\":")
			.append(verification.getSourceCount()).append(',');
		out.append("\"replayPlacementCount\":")
			.append(verification.getReplayPlacementCount()).append(',');
		out.append("\"authoredObjectFootprintCount\":")
			.append(verification.getAuthoredObjectFootprintCount())
			.append(',');
		out.append("\"contributionTileReferenceCount\":")
			.append(verification.getContributionTileReferenceCount())
			.append(',');
		out.append("\"uniqueContributionTileReferenceCount\":")
			.append(verification.getUniqueContributionTileReferenceCount())
			.append(',');
		out.append("\"requiredRegionReferenceCount\":")
			.append(verification.getRequiredRegionReferenceCount())
			.append(',');
		out.append("\"uniqueRequiredRegionReferenceCount\":")
			.append(verification.getUniqueRequiredRegionReferenceCount())
			.append(',');
		out.append("\"preApplicationDisposableRegionConstructionCount\":")
			.append(verification
				.getPreApplicationDisposableRegionConstructionCount())
			.append(',');
		out.append("\"disposableCollisionRegionConstructionCount\":")
			.append(verification
				.getDisposableCollisionRegionConstructionCount())
			.append(',');
		out.append("\"totalDisposableRegionConstructionCount\":")
			.append(verification.getTotalDisposableRegionConstructionCount())
			.append(',');
		out.append("\"disposableTerrainApplyCount\":")
			.append(verification.getDisposableTerrainApplyCount())
			.append(',');
		out.append("\"disposableObjectMembershipApplyCount\":")
			.append(verification
				.getDisposableObjectMembershipApplyCount())
			.append(',');
		out.append("\"collisionApplicationCount\":")
			.append(verification.getCollisionApplicationCount()).append(',');
		out.append("\"heldBoundaryCount\":")
			.append(verification.getHeldBoundaryCount()).append(',');
		out.append("\"verifiedRegionTileCount\":")
			.append(verification.getVerifiedRegionTileCount()).append(',');
		out.append("\"blockingSceneryContributionCount\":")
			.append(verification.getBlockingSceneryContributionCount())
			.append(',');
		out.append("\"dynamicCollisionContributionCount\":")
			.append(verification.getDynamicCollisionContributionCount())
			.append(',');
		out.append("\"dynamicProjectileContributionCount\":")
			.append(verification.getDynamicProjectileContributionCount())
			.append(',');
		field(out, "baselineFingerprintSha256",
			verification.getBaselineFingerprintSha256()).append(',');
		field(out, "fingerprintSha256",
			verification.getFingerprintSha256()).append(',');
		out.append("\"usableRegionContainerCount\":")
			.append(verification.getUsableRegionContainerCount()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(verification.isPointInTimeOnly()).append(',');
		out.append("\"detachedSummaryOnly\":")
			.append(verification.isDetachedSummaryOnly()).append(',');
		out.append("\"allSourcesVerified\":")
			.append(verification.isAllSourcesVerified()).append(',');
		out.append("\"runtimeDefinitionCapturePerformed\":")
			.append(verification.isRuntimeDefinitionCapturePerformed())
			.append(',');
		out.append("\"collisionFootprintDerivationPerformed\":")
			.append(verification.isCollisionFootprintDerivationPerformed())
			.append(',');
		out.append("\"collisionAppliedToDisposableRegions\":")
			.append(verification.isCollisionAppliedToDisposableRegions())
			.append(',');
		out.append("\"collisionRegistrationAttached\":")
			.append(verification.isCollisionRegistrationAttached())
			.append(',');
		out.append("\"runtimeCollisionApplied\":")
			.append(verification.isRuntimeCollisionApplied()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(verification.isRuntimeHandleRetained()).append(',');
		out.append("\"sourceAbsencePerformed\":")
			.append(verification.isSourceAbsencePerformed()).append(',');
		out.append("\"sourceReconstructionPerformed\":")
			.append(verification.isSourceReconstructionPerformed())
			.append(',');
		out.append("\"terrainAppliedToRuntimeSource\":")
			.append(verification.isTerrainAppliedToRuntimeSource())
			.append(',');
		out.append("\"authoredObjectMembershipAppliedToRuntimeSource\":")
			.append(verification
				.isAuthoredObjectMembershipAppliedToRuntimeSource())
			.append(',');
		out.append("\"npcMembershipApplied\":")
			.append(verification.isNpcMembershipApplied()).append(',');
		out.append("\"groundItemMembershipApplied\":")
			.append(verification.isGroundItemMembershipApplied()).append(',');
		out.append("\"schedulerStateRestored\":")
			.append(verification.isSchedulerStateRestored()).append(',');
		out.append("\"activeFamilyPreservationPerformed\":")
			.append(verification.isActiveFamilyPreservationPerformed())
			.append(',');
		out.append("\"regionRegistryMutated\":")
			.append(verification.isRegionRegistryMutated()).append(',');
		out.append("\"residencyMirrorMutated\":")
			.append(verification.isResidencyMirrorMutated()).append(',');
		out.append("\"visibilityCacheMutated\":")
			.append(verification.isVisibilityCacheMutated()).append(',');
		out.append("\"arrivalGate\":")
			.append(verification.isArrivalGate()).append(',');
		out.append("\"visibilityReleased\":")
			.append(verification.isVisibilityReleased()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(verification.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean first = true;
		for (
			LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
				.SourceVerification source
			: verification.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append('{');
			out.append("\"sourceOrdinal\":")
				.append(source.getSourceOrdinal()).append(',');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"replayPlacementCount\":")
				.append(source.getReplayPlacementCount()).append(',');
			out.append("\"authoredObjectFootprintCount\":")
				.append(source.getAuthoredObjectFootprintCount()).append(',');
			out.append("\"contributionTileReferenceCount\":")
				.append(source.getContributionTileReferenceCount())
				.append(',');
			out.append("\"uniqueContributionTileCount\":")
				.append(source.getUniqueContributionTileCount()).append(',');
			out.append("\"requiredRegionReferenceCount\":")
				.append(source.getRequiredRegionReferenceCount()).append(',');
			out.append("\"uniqueRequiredRegionCount\":")
				.append(source.getUniqueRequiredRegionCount()).append(',');
			out.append("\"disposableRegionConstructionCount\":")
				.append(source.getDisposableRegionConstructionCount())
				.append(',');
			out.append("\"collisionApplicationCount\":")
				.append(source.getCollisionApplicationCount()).append(',');
			out.append("\"heldBoundaryCount\":")
				.append(source.getHeldBoundaryCount()).append(',');
			out.append("\"verifiedRegionTileCount\":")
				.append(source.getVerifiedRegionTileCount()).append(',');
			out.append("\"blockingSceneryContributionCount\":")
				.append(source.getBlockingSceneryContributionCount())
				.append(',');
			out.append("\"dynamicCollisionContributionCount\":")
				.append(source.getDynamicCollisionContributionCount())
				.append(',');
			out.append("\"dynamicProjectileContributionCount\":")
				.append(source.getDynamicProjectileContributionCount())
				.append(',');
			field(out, "terrainFingerprintSha256",
				source.getTerrainFingerprintSha256()).append(',');
			field(out, "authoredReplayFingerprintSha256",
				source.getAuthoredReplayFingerprintSha256()).append(',');
			field(out, "definitionCaptureFingerprintSha256",
				source.getDefinitionCaptureFingerprintSha256()).append(',');
			field(out, "collisionFootprintFingerprintSha256",
				source.getCollisionFootprintFingerprintSha256()).append(',');
			field(out, "appliedCollisionFingerprintSha256",
				source.getAppliedCollisionFingerprintSha256());
			out.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionAuthoredSourceStateVerificationBatch(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredSourceStateVerificationBatch
			verification) {
		out.append('{');
		out.append("\"generation\":").append(verification.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(verification.getRequirementsObservedAtTick()).append(',');
		out.append("\"observedAtTick\":")
			.append(verification.getObservedAtTick()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(verification.getResidencyMirrorVersion()).append(',');
		out.append("\"authoredGeneration\":")
			.append(verification.getAuthoredGeneration()).append(',');
		out.append("\"sourceCount\":")
			.append(verification.getSourceCount()).append(',');
		out.append("\"replayPlacementCount\":")
			.append(verification.getReplayPlacementCount()).append(',');
		out.append("\"authoredObjectFootprintCount\":")
			.append(verification.getAuthoredObjectFootprintCount())
			.append(',');
		out.append("\"contributionTileReferenceCount\":")
			.append(verification.getContributionTileReferenceCount())
			.append(',');
		out.append("\"uniqueContributionTileReferenceCount\":")
			.append(verification.getUniqueContributionTileReferenceCount())
			.append(',');
		out.append("\"requiredRegionReferenceCount\":")
			.append(verification.getRequiredRegionReferenceCount())
			.append(',');
		out.append("\"uniqueRequiredRegionReferenceCount\":")
			.append(verification.getUniqueRequiredRegionReferenceCount())
			.append(',');
		out.append("\"preCombinedDisposableRegionConstructionCount\":")
			.append(verification
				.getPreCombinedDisposableRegionConstructionCount())
			.append(',');
		out.append("\"combinedDisposableRegionConstructionCount\":")
			.append(verification
				.getCombinedDisposableRegionConstructionCount())
			.append(',');
		out.append("\"totalDisposableRegionConstructionCount\":")
			.append(verification.getTotalDisposableRegionConstructionCount())
			.append(',');
		out.append("\"combinedSupportRegionCount\":")
			.append(verification.getCombinedSupportRegionCount())
			.append(',');
		out.append("\"preCombinedTerrainApplyCount\":")
			.append(verification.getPreCombinedTerrainApplyCount())
			.append(',');
		out.append("\"combinedTerrainApplyCount\":")
			.append(verification.getCombinedTerrainApplyCount()).append(',');
		out.append("\"totalTerrainApplyCount\":")
			.append(verification.getTotalTerrainApplyCount()).append(',');
		out.append("\"preCombinedObjectMembershipApplyCount\":")
			.append(verification
				.getPreCombinedObjectMembershipApplyCount())
			.append(',');
		out.append("\"combinedObjectMembershipApplicationCount\":")
			.append(verification
				.getCombinedObjectMembershipApplicationCount())
			.append(',');
		out.append("\"combinedObjectMembershipBoundaryCount\":")
			.append(verification
				.getCombinedObjectMembershipBoundaryCount())
			.append(',');
		out.append("\"combinedCollisionApplicationCount\":")
			.append(verification.getCombinedCollisionApplicationCount())
			.append(',');
		out.append("\"combinedCollisionBoundaryCount\":")
			.append(verification.getCombinedCollisionBoundaryCount())
			.append(',');
		out.append("\"combinedVerifiedRegionTileCount\":")
			.append(verification.getCombinedVerifiedRegionTileCount())
			.append(',');
		out.append("\"combinedBlockingSceneryContributionCount\":")
			.append(verification
				.getCombinedBlockingSceneryContributionCount())
			.append(',');
		out.append("\"combinedDynamicCollisionContributionCount\":")
			.append(verification
				.getCombinedDynamicCollisionContributionCount())
			.append(',');
		out.append("\"combinedDynamicProjectileContributionCount\":")
			.append(verification
				.getCombinedDynamicProjectileContributionCount())
			.append(',');
		field(out, "baselineFingerprintSha256",
			verification.getBaselineFingerprintSha256()).append(',');
		field(out, "fingerprintSha256",
			verification.getFingerprintSha256()).append(',');
		out.append("\"usableRegionContainerCount\":")
			.append(verification.getUsableRegionContainerCount()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(verification.isPointInTimeOnly()).append(',');
		out.append("\"detachedSummaryOnly\":")
			.append(verification.isDetachedSummaryOnly()).append(',');
		out.append("\"allSourcesVerified\":")
			.append(verification.isAllSourcesVerified()).append(',');
		out.append("\"runtimeDefinitionCapturePerformed\":")
			.append(verification.isRuntimeDefinitionCapturePerformed())
			.append(',');
		out.append("\"collisionFootprintDerivationPerformed\":")
			.append(verification.isCollisionFootprintDerivationPerformed())
			.append(',');
		out.append("\"terrainAppliedToCombinedDisposableSourceRegions\":")
			.append(verification
				.isTerrainAppliedToCombinedDisposableSourceRegions())
			.append(',');
		out.append(
			"\"authoredObjectMembershipAppliedToCombinedDisposableSourceRegions\":")
			.append(verification
				.isAuthoredObjectMembershipAppliedToCombinedDisposableSourceRegions())
			.append(',');
		out.append("\"collisionAppliedToSameDisposableRegionUnions\":")
			.append(verification
				.isCollisionAppliedToSameDisposableRegionUnions())
			.append(',');
		out.append("\"collisionRegistrationAttached\":")
			.append(verification.isCollisionRegistrationAttached())
			.append(',');
		out.append("\"runtimeCollisionApplied\":")
			.append(verification.isRuntimeCollisionApplied()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(verification.isRuntimeHandleRetained()).append(',');
		out.append("\"sourceAbsencePerformed\":")
			.append(verification.isSourceAbsencePerformed()).append(',');
		out.append("\"sourceReconstructionPerformed\":")
			.append(verification.isSourceReconstructionPerformed())
			.append(',');
		out.append("\"terrainAppliedToRuntimeSource\":")
			.append(verification.isTerrainAppliedToRuntimeSource())
			.append(',');
		out.append("\"authoredObjectMembershipAppliedToRuntimeSource\":")
			.append(verification
				.isAuthoredObjectMembershipAppliedToRuntimeSource())
			.append(',');
		out.append("\"npcMembershipApplied\":")
			.append(verification.isNpcMembershipApplied()).append(',');
		out.append("\"groundItemMembershipApplied\":")
			.append(verification.isGroundItemMembershipApplied()).append(',');
		out.append("\"schedulerStateRestored\":")
			.append(verification.isSchedulerStateRestored()).append(',');
		out.append("\"activeFamilyPreservationPerformed\":")
			.append(verification.isActiveFamilyPreservationPerformed())
			.append(',');
		out.append("\"regionRegistryMutated\":")
			.append(verification.isRegionRegistryMutated()).append(',');
		out.append("\"residencyMirrorMutated\":")
			.append(verification.isResidencyMirrorMutated()).append(',');
		out.append("\"visibilityCacheMutated\":")
			.append(verification.isVisibilityCacheMutated()).append(',');
		out.append("\"arrivalGate\":")
			.append(verification.isArrivalGate()).append(',');
		out.append("\"visibilityReleased\":")
			.append(verification.isVisibilityReleased()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(verification.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean first = true;
		for (
			LayeredPackedRegionAuthoredSourceStateVerificationBatch
				.SourceVerification source
			: verification.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append('{');
			out.append("\"sourceOrdinal\":")
				.append(source.getSourceOrdinal()).append(',');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"replayPlacementCount\":")
				.append(source.getReplayPlacementCount()).append(',');
			out.append("\"authoredObjectFootprintCount\":")
				.append(source.getAuthoredObjectFootprintCount()).append(',');
			out.append("\"contributionTileReferenceCount\":")
				.append(source.getContributionTileReferenceCount())
				.append(',');
			out.append("\"uniqueContributionTileCount\":")
				.append(source.getUniqueContributionTileCount()).append(',');
			out.append("\"requiredRegionReferenceCount\":")
				.append(source.getRequiredRegionReferenceCount()).append(',');
			out.append("\"uniqueRequiredRegionCount\":")
				.append(source.getUniqueRequiredRegionCount()).append(',');
			out.append("\"disposableRegionConstructionCount\":")
				.append(source.getDisposableRegionConstructionCount())
				.append(',');
			out.append("\"supportRegionCount\":")
				.append(source.getSupportRegionCount()).append(',');
			out.append("\"objectMembershipApplicationCount\":")
				.append(source.getObjectMembershipApplicationCount())
				.append(',');
			out.append("\"objectMembershipBoundaryCount\":")
				.append(source.getObjectMembershipBoundaryCount())
				.append(',');
			out.append("\"collisionApplicationCount\":")
				.append(source.getCollisionApplicationCount()).append(',');
			out.append("\"collisionBoundaryCount\":")
				.append(source.getCollisionBoundaryCount()).append(',');
			out.append("\"verifiedRegionTileCount\":")
				.append(source.getVerifiedRegionTileCount()).append(',');
			out.append("\"blockingSceneryContributionCount\":")
				.append(source.getBlockingSceneryContributionCount())
				.append(',');
			out.append("\"dynamicCollisionContributionCount\":")
				.append(source.getDynamicCollisionContributionCount())
				.append(',');
			out.append("\"dynamicProjectileContributionCount\":")
				.append(source.getDynamicProjectileContributionCount())
				.append(',');
			field(out, "terrainFingerprintSha256",
				source.getTerrainFingerprintSha256()).append(',');
			field(out, "authoredReplayFingerprintSha256",
				source.getAuthoredReplayFingerprintSha256()).append(',');
			field(out, "definitionCaptureFingerprintSha256",
				source.getDefinitionCaptureFingerprintSha256()).append(',');
			field(out, "collisionFootprintFingerprintSha256",
				source.getCollisionFootprintFingerprintSha256()).append(',');
			field(out, "appliedCollisionFingerprintSha256",
				source.getAppliedCollisionFingerprintSha256()).append(',');
			field(out, "finalStateFingerprintSha256",
				source.getFinalStateFingerprintSha256());
			out.append('}');
		}
		out.append("]}");
	}

	private static void
		appendPackedRegionTransactionalAuthoredSourceVerificationBatch(
			final StringBuilder out,
			final
				LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
					verification) {
		out.append('{');
		out.append("\"generation\":").append(verification.getGeneration())
			.append(',');
		out.append("\"requirementsObservedAtTick\":")
			.append(verification.getRequirementsObservedAtTick()).append(',');
		out.append("\"observedAtTick\":")
			.append(verification.getObservedAtTick()).append(',');
		out.append("\"residencyMirrorVersion\":")
			.append(verification.getResidencyMirrorVersion()).append(',');
		out.append("\"authoredGeneration\":")
			.append(verification.getAuthoredGeneration()).append(',');
		out.append("\"sourceCount\":")
			.append(verification.getSourceCount()).append(',');
		out.append("\"replayPlacementCount\":")
			.append(verification.getReplayPlacementCount()).append(',');
		out.append("\"authoredObjectFootprintCount\":")
			.append(verification.getAuthoredObjectFootprintCount())
			.append(',');
		out.append("\"contributionTileReferenceCount\":")
			.append(verification.getContributionTileReferenceCount())
			.append(',');
		out.append("\"requiredRegionReferenceCount\":")
			.append(verification.getRequiredRegionReferenceCount())
			.append(',');
		out.append("\"uniqueRequiredRegionReferenceCount\":")
			.append(verification.getUniqueRequiredRegionReferenceCount())
			.append(',');
		out.append(
			"\"preTransactionalDisposableRegionConstructionCount\":")
			.append(verification
				.getPreTransactionalDisposableRegionConstructionCount())
			.append(',');
		out.append("\"transactionalDisposableRegionConstructionCount\":")
			.append(verification
				.getTransactionalDisposableRegionConstructionCount())
			.append(',');
		out.append("\"totalDisposableRegionConstructionCount\":")
			.append(verification.getTotalDisposableRegionConstructionCount())
			.append(',');
		out.append("\"transactionalSupportRegionCount\":")
			.append(verification.getTransactionalSupportRegionCount())
			.append(',');
		out.append("\"objectCollisionTransactionCount\":")
			.append(verification.getObjectCollisionTransactionCount())
			.append(',');
		out.append("\"objectCollisionTransactionBoundaryCount\":")
			.append(verification
				.getObjectCollisionTransactionBoundaryCount())
			.append(',');
		out.append("\"disposableCacheInvalidationCount\":")
			.append(verification.getDisposableCacheInvalidationCount())
			.append(',');
		out.append("\"collisionRegistrationCount\":")
			.append(verification.getCollisionRegistrationCount()).append(',');
		out.append("\"collisionRegistrationContributionCount\":")
			.append(verification
				.getCollisionRegistrationContributionCount())
			.append(',');
		out.append("\"collisionRegistrationRegionReferenceCount\":")
			.append(verification
				.getCollisionRegistrationRegionReferenceCount())
			.append(',');
		out.append("\"transactionalVerifiedRegionTileCount\":")
			.append(verification.getTransactionalVerifiedRegionTileCount())
			.append(',');
		out.append("\"transactionalBlockingSceneryContributionCount\":")
			.append(verification
				.getTransactionalBlockingSceneryContributionCount())
			.append(',');
		out.append("\"transactionalDynamicCollisionContributionCount\":")
			.append(verification
				.getTransactionalDynamicCollisionContributionCount())
			.append(',');
		out.append("\"transactionalDynamicProjectileContributionCount\":")
			.append(verification
				.getTransactionalDynamicProjectileContributionCount())
			.append(',');
		field(out, "baselineFingerprintSha256",
			verification.getBaselineFingerprintSha256()).append(',');
		field(out, "fingerprintSha256",
			verification.getFingerprintSha256()).append(',');
		out.append("\"usableRegionContainerCount\":")
			.append(verification.getUsableRegionContainerCount()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(verification.isPointInTimeOnly()).append(',');
		out.append("\"detachedSummaryOnly\":")
			.append(verification.isDetachedSummaryOnly()).append(',');
		out.append("\"allSourcesVerified\":")
			.append(verification.isAllSourcesVerified()).append(',');
		out.append("\"runtimeDefinitionCapturePerformed\":")
			.append(verification.isRuntimeDefinitionCapturePerformed())
			.append(',');
		out.append("\"collisionFootprintDerivationPerformed\":")
			.append(verification.isCollisionFootprintDerivationPerformed())
			.append(',');
		out.append(
			"\"objectCollisionTransactionAppliedToDisposableRegions\":")
			.append(verification
				.isObjectCollisionTransactionAppliedToDisposableRegions())
			.append(',');
		out.append(
			"\"collisionRegistrationAttachedToDisposableObjects\":")
			.append(verification
				.isCollisionRegistrationAttachedToDisposableObjects())
			.append(',');
		out.append("\"disposableCacheInvalidationOnly\":")
			.append(verification.isDisposableCacheInvalidationOnly())
			.append(',');
		out.append("\"runtimeCollisionApplied\":")
			.append(verification.isRuntimeCollisionApplied()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(verification.isRuntimeHandleRetained()).append(',');
		out.append("\"sourceAbsencePerformed\":")
			.append(verification.isSourceAbsencePerformed()).append(',');
		out.append("\"sourceReconstructionPerformed\":")
			.append(verification.isSourceReconstructionPerformed())
			.append(',');
		out.append("\"terrainAppliedToRuntimeSource\":")
			.append(verification.isTerrainAppliedToRuntimeSource())
			.append(',');
		out.append("\"authoredObjectMembershipAppliedToRuntimeSource\":")
			.append(verification
				.isAuthoredObjectMembershipAppliedToRuntimeSource())
			.append(',');
		out.append("\"npcMembershipApplied\":")
			.append(verification.isNpcMembershipApplied()).append(',');
		out.append("\"groundItemMembershipApplied\":")
			.append(verification.isGroundItemMembershipApplied()).append(',');
		out.append("\"schedulerStateRestored\":")
			.append(verification.isSchedulerStateRestored()).append(',');
		out.append("\"activeFamilyPreservationPerformed\":")
			.append(verification.isActiveFamilyPreservationPerformed())
			.append(',');
		out.append("\"runtimeCacheInvalidated\":")
			.append(verification.isRuntimeCacheInvalidated()).append(',');
		out.append("\"regionRegistryMutated\":")
			.append(verification.isRegionRegistryMutated()).append(',');
		out.append("\"residencyMirrorMutated\":")
			.append(verification.isResidencyMirrorMutated()).append(',');
		out.append("\"visibilityCacheMutated\":")
			.append(verification.isVisibilityCacheMutated()).append(',');
		out.append("\"arrivalGate\":")
			.append(verification.isArrivalGate()).append(',');
		out.append("\"visibilityReleased\":")
			.append(verification.isVisibilityReleased()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(verification.isLifecycleAuthority()).append(',');
		out.append("\"sources\":[");
		boolean first = true;
		for (
			LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
				.SourceVerification source
			: verification.getSources()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append('{');
			out.append("\"sourceOrdinal\":")
				.append(source.getSourceOrdinal()).append(',');
			out.append("\"packedRegionX\":")
				.append(source.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(source.getPackedRegionY()).append(',');
			out.append("\"replayPlacementCount\":")
				.append(source.getReplayPlacementCount()).append(',');
			out.append("\"authoredObjectFootprintCount\":")
				.append(source.getAuthoredObjectFootprintCount()).append(',');
			out.append("\"contributionTileReferenceCount\":")
				.append(source.getContributionTileReferenceCount())
				.append(',');
			out.append("\"requiredRegionReferenceCount\":")
				.append(source.getRequiredRegionReferenceCount()).append(',');
			out.append("\"uniqueRequiredRegionCount\":")
				.append(source.getUniqueRequiredRegionCount()).append(',');
			out.append("\"disposableRegionConstructionCount\":")
				.append(source.getDisposableRegionConstructionCount())
				.append(',');
			out.append("\"supportRegionCount\":")
				.append(source.getSupportRegionCount()).append(',');
			out.append("\"objectCollisionTransactionCount\":")
				.append(source.getObjectCollisionTransactionCount())
				.append(',');
			out.append("\"objectCollisionTransactionBoundaryCount\":")
				.append(source.getObjectCollisionTransactionBoundaryCount())
				.append(',');
			out.append("\"disposableCacheInvalidationCount\":")
				.append(source.getDisposableCacheInvalidationCount())
				.append(',');
			out.append("\"collisionRegistrationCount\":")
				.append(source.getCollisionRegistrationCount()).append(',');
			out.append("\"collisionRegistrationContributionCount\":")
				.append(source.getCollisionRegistrationContributionCount())
				.append(',');
			out.append("\"collisionRegistrationRegionReferenceCount\":")
				.append(source
					.getCollisionRegistrationRegionReferenceCount())
				.append(',');
			out.append("\"verifiedRegionTileCount\":")
				.append(source.getVerifiedRegionTileCount()).append(',');
			out.append("\"blockingSceneryContributionCount\":")
				.append(source.getBlockingSceneryContributionCount())
				.append(',');
			out.append("\"dynamicCollisionContributionCount\":")
				.append(source.getDynamicCollisionContributionCount())
				.append(',');
			out.append("\"dynamicProjectileContributionCount\":")
				.append(source.getDynamicProjectileContributionCount())
				.append(',');
			field(out, "terrainFingerprintSha256",
				source.getTerrainFingerprintSha256()).append(',');
			field(out, "authoredReplayFingerprintSha256",
				source.getAuthoredReplayFingerprintSha256()).append(',');
			field(out, "definitionCaptureFingerprintSha256",
				source.getDefinitionCaptureFingerprintSha256()).append(',');
			field(out, "collisionFootprintFingerprintSha256",
				source.getCollisionFootprintFingerprintSha256()).append(',');
			field(out, "appliedCollisionFingerprintSha256",
				source.getAppliedCollisionFingerprintSha256()).append(',');
			field(out, "collisionRegistrationFingerprintSha256",
				source.getCollisionRegistrationFingerprintSha256()).append(',');
			field(out, "finalStateFingerprintSha256",
				source.getFinalStateFingerprintSha256());
			out.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionEventTargets(
		final StringBuilder out,
		final LayeredPackedRegionEventTargetObservation observation) {
		out.append('{');
		out.append("\"proposalGeneration\":")
			.append(observation.getProposalGeneration()).append(',');
		out.append("\"eventInventoryObservedAtTick\":")
			.append(observation.getEventInventoryObservedAtTick()).append(',');
		out.append("\"targetObservedAtTick\":")
			.append(observation.getTargetObservedAtTick()).append(',');
		field(out, "schedulerInstanceIdentity",
			observation.getSchedulerInstanceIdentity()).append(',');
		out.append("\"targetCount\":")
			.append(observation.getTargetCount()).append(',');
		out.append("\"availableTargetCount\":")
			.append(observation.getAvailableTargetCount()).append(',');
		out.append("\"unavailableTargetCount\":")
			.append(observation.getUnavailableTargetCount()).append(',');
		out.append("\"objectBoundaryClassifiedTargetCount\":")
			.append(observation.getObjectBoundaryClassifiedTargetCount())
			.append(',');
		out.append("\"availableTargetObjectBoundaryClassificationComplete\":")
			.append(observation
				.isAvailableTargetObjectBoundaryClassificationComplete())
			.append(',');
		out.append("\"noOpSuccessCount\":")
			.append(observation.getNoOpSuccessCount()).append(',');
		out.append("\"mutationPreconditionSatisfiedCount\":")
			.append(observation.getMutationPreconditionSatisfiedCount())
			.append(',');
		out.append("\"refusedTargetCount\":")
			.append(observation.getRefusedTargetCount()).append(',');
		out.append("\"outcomeCountComplete\":")
			.append(observation.isOutcomeCountComplete()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(observation.isPointInTimeOnly()).append(',');
		out.append("\"atomicWithEventInventory\":")
			.append(observation.isAtomicWithEventInventory()).append(',');
		out.append("\"readOnlyTargetLookupPerformed\":")
			.append(observation.isReadOnlyTargetLookupPerformed()).append(',');
		out.append("\"runtimeTargetClassificationPerformed\":")
			.append(observation.isRuntimeTargetClassificationPerformed())
			.append(',');
		out.append("\"atomicWithMutation\":")
			.append(observation.isAtomicWithMutation()).append(',');
		out.append("\"runtimeRevalidationPerformed\":")
			.append(observation.isRuntimeRevalidationPerformed()).append(',');
		out.append("\"entityHandleRetained\":")
			.append(observation.isEntityHandleRetained()).append(',');
		out.append("\"achievedStateClaimed\":")
			.append(observation.isAchievedStateClaimed()).append(',');
		out.append("\"commitToken\":")
			.append(observation.isCommitToken()).append(',');
		out.append("\"mutationPerformed\":")
			.append(observation.isMutationPerformed()).append(',');
		out.append("\"executableRestoration\":")
			.append(observation.isExecutableRestoration()).append(',');
		out.append("\"arrivalGate\":")
			.append(observation.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(observation.isLifecycleAuthority()).append(',');
		out.append("\"targets\":[");
		boolean first = true;
		for (LayeredPackedRegionEventTargetObservation.TargetRecord target
			: observation.getTargets()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"snapshotOrdinal\":")
				.append(target.getSnapshotOrdinal()).append(',');
			out.append("\"registrationSequence\":")
				.append(target.getRegistrationSequence()).append(',');
			out.append("\"packedX\":").append(target.getX()).append(',');
			out.append("\"packedY\":").append(target.getY()).append(',');
			out.append("\"regionAvailable\":")
				.append(target.isRegionAvailable()).append(',');
			out.append("\"slotObjectCount\":")
				.append(target.getSlotObjectCount()).append(',');
			out.append("\"exactRestorationSceneryCount\":")
				.append(target.getExactRestorationSceneryCount()).append(',');
			out.append("\"exactAuthoredIdentityCount\":")
				.append(target.getExactAuthoredIdentityCount()).append(',');
			out.append("\"objectBoundaryHeldDuringClassification\":")
				.append(target.isObjectBoundaryHeldDuringClassification())
				.append(',');
			field(out, "observedTargetState",
				target.getObservedTargetState().name()).append(',');
			field(out, "decisionOutcome",
				target.getDecisionOutcome().name()).append(',');
			field(out, "decisionReason",
				target.getDecisionReason().name());
			out.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionEventAtomicTargetRevalidation(
		final StringBuilder out,
		final LayeredPackedRegionEventAtomicTargetRevalidation observation) {
		out.append('{');
		out.append("\"proposalGeneration\":")
			.append(observation.getProposalGeneration()).append(',');
		out.append("\"eventInventoryObservedAtTick\":")
			.append(observation.getEventInventoryObservedAtTick()).append(',');
		out.append("\"revalidationObservedAtTick\":")
			.append(observation.getRevalidationObservedAtTick()).append(',');
		field(out, "schedulerInstanceIdentity",
			observation.getSchedulerInstanceIdentity()).append(',');
		out.append("\"recordCount\":")
			.append(observation.getRecordCount()).append(',');
		out.append("\"outerFenceAcceptedCount\":")
			.append(observation.getOuterFenceAcceptedCount()).append(',');
		out.append("\"outerFenceRefusedCount\":")
			.append(observation.getOuterFenceRefusedCount()).append(',');
		out.append("\"lifecycleChangeDetectedCount\":")
			.append(observation.getLifecycleChangeDetectedCount()).append(',');
		out.append("\"runtimeTargetLookupPerformedCount\":")
			.append(observation.getRuntimeTargetLookupPerformedCount()).append(',');
		out.append("\"runtimeRevalidationPerformedCount\":")
			.append(observation.getRuntimeRevalidationPerformedCount()).append(',');
		out.append("\"contractRefusedCount\":")
			.append(observation.getContractRefusedCount()).append(',');
		out.append("\"noOpContractSatisfiedCount\":")
			.append(observation.getNoOpContractSatisfiedCount()).append(',');
		out.append("\"mutationPreconditionContractSatisfiedCount\":")
			.append(observation
				.getMutationPreconditionContractSatisfiedCount()).append(',');
		out.append("\"outerOutcomeCountComplete\":")
			.append(observation.isOuterOutcomeCountComplete()).append(',');
		out.append("\"acceptedContractOutcomeCountComplete\":")
			.append(observation
				.isAcceptedContractOutcomeCountComplete()).append(',');
		out.append("\"pointInTimeOnly\":")
			.append(observation.isPointInTimeOnly()).append(',');
		out.append("\"atomicWithEventInventory\":")
			.append(observation.isAtomicWithEventInventory()).append(',');
		out.append("\"runtimeTargetLookupPerformed\":")
			.append(observation.isRuntimeTargetLookupPerformed()).append(',');
		out.append("\"runtimeRevalidationPerformed\":")
			.append(observation.isRuntimeRevalidationPerformed()).append(',');
		out.append("\"atomicWithMutation\":")
			.append(observation.isAtomicWithMutation()).append(',');
		out.append("\"entityHandleRetained\":")
			.append(observation.isEntityHandleRetained()).append(',');
		out.append("\"achievedStateClaimed\":")
			.append(observation.isAchievedStateClaimed()).append(',');
		out.append("\"commitToken\":")
			.append(observation.isCommitToken()).append(',');
		out.append("\"mutationPerformed\":")
			.append(observation.isMutationPerformed()).append(',');
		out.append("\"executableRestoration\":")
			.append(observation.isExecutableRestoration()).append(',');
		out.append("\"arrivalGate\":")
			.append(observation.isArrivalGate()).append(',');
		out.append("\"lifecycleAuthority\":")
			.append(observation.isLifecycleAuthority()).append(',');
		out.append("\"records\":[");
		boolean first = true;
		for (LayeredPackedRegionEventAtomicTargetRevalidation.Record record
			: observation.getRecords()) {
			if (!first) { out.append(','); }
			first = false;
			out.append('{');
			out.append("\"snapshotOrdinal\":")
				.append(record.getSnapshotOrdinal()).append(',');
			out.append("\"registrationSequence\":")
				.append(record.getRegistrationSequence()).append(',');
			out.append("\"packedX\":").append(record.getX()).append(',');
			out.append("\"packedY\":").append(record.getY()).append(',');
			field(out, "outerFenceReason",
				record.getOuterFenceReason().name()).append(',');
			out.append("\"outerFenceAccepted\":")
				.append(record.isOuterFenceAccepted()).append(',');
			out.append("\"operationInvoked\":")
				.append(record.isOperationInvoked()).append(',');
			out.append("\"lifecycleVersionBeforeOperation\":");
			appendNullableLong(
				out, record.getLifecycleVersionBeforeOperation());
			out.append(",\"lifecycleVersionAfterOperation\":");
			appendNullableLong(
				out, record.getLifecycleVersionAfterOperation());
			out.append(",\"timingStableAcrossOperation\":")
				.append(record.isTimingStableAcrossOperation()).append(',');
			out.append("\"lifecycleChangeDetected\":")
				.append(record.isLifecycleChangeDetected()).append(',');
			out.append("\"runtimeTargetLookupPerformed\":")
				.append(record.isRuntimeTargetLookupPerformed()).append(',');
			out.append("\"runtimeRevalidationPerformed\":")
				.append(record.isRuntimeRevalidationPerformed()).append(',');
			out.append("\"target\":");
			LayeredPackedRegionEventAtomicTargetRevalidation.TargetEvidence
				target = record.getTarget();
			if (target == null) {
				out.append("null");
			} else {
				out.append('{');
				out.append("\"regionAvailable\":")
					.append(target.isRegionAvailable()).append(',');
				out.append("\"slotObjectCount\":")
					.append(target.getSlotObjectCount()).append(',');
				out.append("\"exactRestorationSceneryCount\":")
					.append(target.getExactRestorationSceneryCount()).append(',');
				out.append("\"exactAuthoredIdentityCount\":")
					.append(target.getExactAuthoredIdentityCount()).append(',');
				out.append("\"objectBoundaryHeldDuringClassification\":")
					.append(target
						.isObjectBoundaryHeldDuringClassification()).append(',');
				field(out, "observedTargetState",
					target.getObservedTargetState().name()).append(',');
				field(out, "targetOutcome",
					target.getTargetOutcome().name()).append(',');
				field(out, "targetReason",
					target.getTargetReason().name()).append(',');
				field(out, "contractOutcome",
					target.getContractOutcome().name()).append(',');
				field(out, "contractReason",
					target.getContractReason().name());
				out.append('}');
			}
			out.append('}');
		}
		out.append("]}");
	}

	private static void appendPackedRegionEventRecoveryNoOp(
		final StringBuilder out,
		final PackedRegionEventRecoveryNoOpMetadata diagnostic) {
		out.append('{');
		field(out, "reason", diagnostic.getReason()).append(',');
		field(out, "preparationReason", diagnostic.getPreparationReason())
			.append(',');
		out.append("\"lifecycleReason\":");
		if (diagnostic.getLifecycleReason() == null) {
			out.append("null");
		} else {
			quoted(out, diagnostic.getLifecycleReason());
		}
		out.append(",\"proposalGeneration\":")
			.append(diagnostic.getProposalGeneration()).append(',');
		out.append("\"inventoryEventCount\":")
			.append(diagnostic.getInventoryEventCount()).append(',');
		out.append("\"recoveryCandidateCount\":")
			.append(diagnostic.getRecoveryCandidateCount()).append(',');
		out.append("\"proposalRelatedEventCount\":")
			.append(diagnostic.getProposalRelatedEventCount()).append(',');
		out.append("\"recoveryCompleteEventCount\":")
			.append(diagnostic.getRecoveryCompleteEventCount()).append(',');
		out.append("\"recoveryIncompleteEventCount\":")
			.append(diagnostic.getRecoveryIncompleteEventCount()).append(',');
		out.append("\"incompleteOwnerPositionHintEventCount\":")
			.append(diagnostic.getIncompleteOwnerPositionHintEventCount())
			.append(',');
		out.append("\"incompleteExactSpatialEventCount\":")
			.append(diagnostic.getIncompleteExactSpatialEventCount())
			.append(',');
		out.append("\"firstIncompleteRegistrationSequence\":");
		if (diagnostic.getFirstIncompleteRegistrationSequence() == null) {
			out.append("null");
		} else {
			out.append(
				diagnostic.getFirstIncompleteRegistrationSequence().longValue());
		}
		out.append(",\"firstIncompleteOwnerKind\":");
		if (diagnostic.getFirstIncompleteOwnerKind() == null) {
			out.append("null");
		} else {
			quoted(out, diagnostic.getFirstIncompleteOwnerKind());
		}
		out.append(",\"firstIncompleteAttributionKind\":");
		if (diagnostic.getFirstIncompleteAttributionKind() == null) {
			out.append("null");
		} else {
			quoted(out, diagnostic.getFirstIncompleteAttributionKind());
		}
		out.append(",\"firstIncompleteRecoveryRequirement\":");
		if (diagnostic.getFirstIncompleteRecoveryRequirement() == null) {
			out.append("null");
		} else {
			quoted(out, diagnostic.getFirstIncompleteRecoveryRequirement());
		}
		out.append(",\"preflightComplete\":")
			.append(diagnostic.isPreflightComplete()).append(',');
		out.append("\"futureSnapshotCount\":")
			.append(diagnostic.getFutureSnapshotCount()).append(',');
		out.append("\"runtimeVerificationCount\":")
			.append(diagnostic.getRuntimeVerificationCount()).append(',');
		out.append("\"mutationOperationCount\":")
			.append(diagnostic.getMutationOperationCount()).append(',');
		out.append("\"terminalEventConsumptionCount\":")
			.append(diagnostic.getTerminalEventConsumptionCount()).append(',');
		out.append("\"reconstructionInvoked\":")
			.append(diagnostic.isReconstructionInvoked()).append(',');
		out.append("\"recoveryInvoked\":")
			.append(diagnostic.isRecoveryInvoked()).append(',');
		out.append("\"contractuallyReadyForFirstVisibility\":")
			.append(diagnostic.isContractuallyReadyForFirstVisibility())
			.append(',');
		out.append("\"freshInventoryRetryRequired\":")
			.append(diagnostic.isFreshInventoryRetryRequired()).append(',');
		out.append("\"verificationOnly\":true,");
		out.append("\"noOpReconstruction\":true,");
		out.append("\"regionMutationAllowed\":")
			.append(diagnostic.isRegionMutationAllowed()).append(',');
		out.append("\"overdueConsumptionAllowed\":")
			.append(diagnostic.isOverdueConsumptionAllowed()).append(',');
		out.append("\"regionLoadingPerformed\":")
			.append(diagnostic.isRegionLoadingPerformed()).append(',');
		out.append("\"retryPerformed\":")
			.append(diagnostic.isRetryPerformed()).append(',');
		out.append("\"arrivalGate\":")
			.append(diagnostic.isArrivalGate()).append(',');
		out.append("\"visibilityReleased\":")
			.append(diagnostic.isVisibilityReleased()).append(',');
		out.append("\"runtimeHandleRetained\":")
			.append(diagnostic.isRuntimeHandleRetained());
		out.append('}');
	}

	private static void appendEventRestorationState(
		final StringBuilder out,
		final LayeredPackedRegionEventOwnershipInventory.EventRestorationState
			state,
		final boolean atomicTimingCaptured,
		final long reconstructionGeneration) {
		if (state.getKind()
			== LayeredPackedRegionEventOwnershipInventory.RestorationKind
				.UNAVAILABLE) {
			out.append("null");
			return;
		}
		LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState
			scenery = state.getScenery();
		out.append('{');
		field(out, "kind", state.getKind().name()).append(',');
		out.append("\"forceFullBlock\":")
			.append(state.isForceFullBlock()).append(',');
		field(out, "targetBindingEvidence",
			state.getTargetBindingEvidence().name()).append(',');
		out.append("\"detachedCallbackPayloadComplete\":")
			.append(state.isDetachedCallbackPayloadComplete()).append(',');
		field(out, "executionSemantics",
			state.getExecutionSemantics().name()).append(',');
		field(out, "timeProgressionPolicy",
			state.getTimeProgressionPolicy().name()).append(',');
		out.append("\"executionSemanticsCaptured\":")
			.append(state.isExecutionSemanticsCaptured()).append(',');
		out.append("\"atomicTimingCaptured\":")
			.append(atomicTimingCaptured).append(',');
		field(out, "targetSubject", state.getTargetSubject().name()).append(',');
		field(out, "bindingEvidence", state.getBindingEvidence().name())
			.append(',');
		field(out, "targetConflictPolicy",
			state.getTargetConflictPolicy().name()).append(',');
		out.append("\"targetBindingRequirementCaptured\":")
			.append(state.isTargetBindingRequirementCaptured()).append(',');
		out.append("\"targetBindingComplete\":")
			.append(state.isTargetBindingComplete()).append(',');
		field(out, "arrivalOrderingRequirement",
			state.getArrivalOrderingRequirement().name()).append(',');
		out.append("\"arrivalOrderingCaptured\":")
			.append(state.isArrivalOrderingCaptured()).append(',');
		field(out, "generationBindingRequirement",
			state.getGenerationBindingRequirement().name()).append(',');
		out.append("\"generationBindingRequirementCaptured\":")
			.append(state.isGenerationBindingRequirementCaptured()).append(',');
		out.append("\"generationBindingComplete\":")
			.append(state.isGenerationBindingComplete(reconstructionGeneration))
			.append(',');
		field(out, "desiredState", state.getDesiredState().name()).append(',');
		field(out, "idempotencyPolicy",
			state.getIdempotencyPolicy().name()).append(',');
		field(out, "mutationPrecondition",
			state.getMutationPrecondition().name()).append(',');
		out.append("\"idempotencyRequirementCaptured\":")
			.append(state.isIdempotencyRequirementCaptured()).append(',');
		out.append("\"schedulerIdentityCaptured\":")
			.append(state.isSchedulerIdentityCaptured()).append(',');
		out.append("\"targetBindingLookupPerformed\":")
			.append(state.isTargetBindingLookupPerformed()).append(',');
		out.append("\"standaloneRestorationComplete\":")
			.append(state.isStandaloneRestorationComplete()).append(',');
		out.append("\"scenery\":{");
		out.append("\"objectId\":").append(scenery.getObjectId()).append(',');
		out.append("\"permanentObjectId\":")
			.append(scenery.getPermanentObjectId()).append(',');
		out.append("\"packedX\":").append(scenery.getX()).append(',');
		out.append("\"packedY\":").append(scenery.getY()).append(',');
		out.append("\"direction\":").append(scenery.getDirection()).append(',');
		out.append("\"type\":").append(scenery.getType()).append(',');
		out.append("\"ownerPresent\":").append(scenery.hasOwner()).append(',');
		out.append("\"runtimeAttributeCount\":")
			.append(scenery.getRuntimeAttributeCount()).append(',');
		out.append("\"constructorStateComplete\":true,");
		out.append("\"authoredPlacement\":");
		LayeredPackedRegionEventOwnershipInventory
			.AuthoredPlacementRestorationState authored =
				scenery.getAuthoredPlacement();
		if (authored == null) {
			out.append("null");
		} else {
			out.append('{');
			out.append("\"generation\":")
				.append(authored.getGeneration()).append(',');
			out.append("\"packedRegionX\":")
				.append(authored.getPackedRegionX()).append(',');
			out.append("\"packedRegionY\":")
				.append(authored.getPackedRegionY()).append(',');
			out.append("\"sourceOrdinal\":")
				.append(authored.getSourceOrdinal()).append(',');
			field(out, "constructionKind",
				authored.getConstructionKind().name());
			out.append('}');
		}
		out.append("}}");
	}

	private static void appendIntegerList(
		final StringBuilder out,
		final String name,
		final List<Integer> values) {
		quoted(out, name).append(':').append('[');
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) { out.append(','); }
			out.append(values.get(index).intValue());
		}
		out.append(']');
	}

	private static void requireEventOwnershipMatchesProposal(
		final LayeredPackedRegionRetirementRefinementProposal proposal,
		final LayeredPackedRegionEventOwnershipInventory inventory) {
		if (proposal.getGeneration() != inventory.getProposalGeneration()
			|| proposal.getCandidateSourceCount() != inventory.getSourceCount()) {
			throw new IllegalStateException(
				"Event ownership inventory differs from its proposal");
		}
		for (int index = 0; index < proposal.getCandidateSourceCount(); index++) {
			LayeredPackedRegionRetirementRefinementProposal.CandidateSource candidate =
				proposal.getCandidates().get(index);
			LayeredPackedRegionEventOwnershipInventory.SourceRecord source =
				inventory.getSources().get(index);
			if (candidate.getPackedRegionX() != source.getPackedRegionX()
				|| candidate.getPackedRegionY() != source.getPackedRegionY()) {
				throw new IllegalStateException(
					"Event ownership source order differs from its proposal");
			}
		}
	}

	private static void requireEventTargetsMatchInventory(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final LayeredPackedRegionEventTargetObservation observation) {
		if (inventory.getProposalGeneration()
				!= observation.getProposalGeneration()
			|| inventory.getObservedAtTick()
				!= observation.getEventInventoryObservedAtTick()
			|| !inventory.getSchedulerInstanceIdentity().equals(
				observation.getSchedulerInstanceIdentity())
			|| inventory.getRestorationStateAvailableEventCount()
				!= observation.getTargetCount()
			|| !observation
				.isAvailableTargetObjectBoundaryClassificationComplete()) {
			throw new IllegalStateException(
				"Event target observation differs from its inventory");
		}
		int targetIndex = 0;
		for (LayeredPackedRegionEventOwnershipInventory.EventRecord event
			: inventory.getEvents()) {
			LayeredPackedRegionEventOwnershipInventory.EventRestorationState
				restoration = event.getRestorationState();
			if (restoration.getKind()
				== LayeredPackedRegionEventOwnershipInventory.RestorationKind
					.UNAVAILABLE) {
				continue;
			}
			LayeredPackedRegionEventTargetObservation.TargetRecord target =
				observation.getTargets().get(targetIndex++);
			LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState
				scenery = restoration.getScenery();
			if (event.getSnapshotOrdinal() != target.getSnapshotOrdinal()
				|| event.getRegistrationSequence()
					!= target.getRegistrationSequence()
				|| scenery.getX() != target.getX()
				|| scenery.getY() != target.getY()) {
				throw new IllegalStateException(
					"Event target order differs from its restoration record");
			}
		}
	}

	private static void requireAtomicEventTargetsMatchInventory(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final LayeredPackedRegionEventAtomicTargetRevalidation observation) {
		if (inventory.getProposalGeneration()
				!= observation.getProposalGeneration()
			|| inventory.getObservedAtTick()
				!= observation.getEventInventoryObservedAtTick()
			|| !inventory.getSchedulerInstanceIdentity().equals(
				observation.getSchedulerInstanceIdentity())
			|| inventory.getRestorationStateAvailableEventCount()
				!= observation.getRecordCount()
			|| !observation.isOuterOutcomeCountComplete()
			|| !observation.isAcceptedContractOutcomeCountComplete()) {
			throw new IllegalStateException(
				"Atomic event target revalidation differs from its inventory");
		}
		int recordIndex = 0;
		for (LayeredPackedRegionEventOwnershipInventory.EventRecord event
			: inventory.getEvents()) {
			LayeredPackedRegionEventOwnershipInventory.EventRestorationState
				restoration = event.getRestorationState();
			if (restoration.getKind()
				== LayeredPackedRegionEventOwnershipInventory.RestorationKind
					.UNAVAILABLE) {
				continue;
			}
			LayeredPackedRegionEventAtomicTargetRevalidation.Record record =
				observation.getRecords().get(recordIndex++);
			LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState
				scenery = restoration.getScenery();
			if (event.getSnapshotOrdinal() != record.getSnapshotOrdinal()
				|| event.getRegistrationSequence()
					!= record.getRegistrationSequence()
				|| scenery.getX() != record.getX()
				|| scenery.getY() != record.getY()) {
				throw new IllegalStateException(
					"Atomic target order differs from its restoration record");
			}
		}
	}

	private static void appendPackedRegionAuthoredPopulationSupersession(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredPopulationOutcome.Supersession
			supersession) {
		out.append('{');
		field(out, "collisionKind", supersession.getCollisionKind().name())
			.append(',');
		out.append("\"predecessor\":");
		appendPackedRegionAuthoredPopulationPlacement(
			out, supersession.getPredecessor());
		out.append(',');
		out.append("\"successor\":");
		appendPackedRegionAuthoredPopulationPlacement(
			out, supersession.getSuccessor());
		out.append('}');
	}

	private static void appendPackedRegionAuthoredPopulationPlacement(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredPopulationOutcome.PlacementMetadata
			placement) {
		out.append('{');
		out.append("\"generation\":")
			.append(placement.getGeneration()).append(',');
		out.append("\"packedRegionX\":")
			.append(placement.getPackedRegionX()).append(',');
		out.append("\"packedRegionY\":")
			.append(placement.getPackedRegionY()).append(',');
		out.append("\"sourceOrdinal\":")
			.append(placement.getSourceOrdinal()).append(',');
		field(out, "constructionKind",
			placement.getConstructionKind().name()).append(',');
		out.append("\"authoredDefinitionId\":")
			.append(placement.getAuthoredDefinitionId()).append(',');
		out.append("\"constructedEntityId\":")
			.append(placement.getConstructedEntityId()).append(',');
		out.append("\"packedX\":")
			.append(placement.getPackedX()).append(',');
		out.append("\"packedY\":")
			.append(placement.getPackedY()).append(',');
		out.append("\"direction\":")
			.append(placement.getDirection()).append(',');
		out.append("\"objectType\":")
			.append(placement.getObjectType()).append('}');
	}

	private static void appendPackedRegionAuthoredProvenanceAnomaly(
		final StringBuilder out,
		final LayeredPackedRegionAuthoredProvenanceObservation.AnomalyDetail
			detail) {
		out.append('{');
		field(out, "anomalyKind", detail.getAnomalyKind().name()).append(',');
		out.append("\"generation\":")
			.append(detail.getGeneration()).append(',');
		out.append("\"packedRegionX\":")
			.append(detail.getPackedRegionX()).append(',');
		out.append("\"packedRegionY\":")
			.append(detail.getPackedRegionY()).append(',');
		out.append("\"sourceOrdinal\":")
			.append(detail.getSourceOrdinal()).append(',');
		field(out, "constructionKind",
			detail.getConstructionKind().name()).append(',');
		out.append("\"manifestRecognized\":")
			.append(detail.isManifestRecognized()).append(',');
		appendNullableInteger(out, "authoredDefinitionId",
			detail.isManifestRecognized(), detail.getAuthoredDefinitionId());
		out.append(',');
		appendNullableInteger(out, "expectedConstructedEntityId",
			detail.isManifestRecognized(),
			detail.getExpectedConstructedEntityId());
		out.append(',');
		appendNullableInteger(out, "packedX",
			detail.isManifestRecognized(), detail.getPackedX());
		out.append(',');
		appendNullableInteger(out, "packedY",
			detail.isManifestRecognized(), detail.getPackedY());
		out.append(',');
		out.append("\"runtimeObserved\":")
			.append(detail.isRuntimeObserved()).append(',');
		appendNullableInteger(out, "runtimeEntityId",
			detail.isRuntimeObserved(), detail.getRuntimeEntityId());
		out.append(',');
		appendNullableInteger(out, "currentPackedRegionX",
			detail.isRuntimeObserved(), detail.getCurrentPackedRegionX());
		out.append(',');
		appendNullableInteger(out, "currentPackedRegionY",
			detail.isRuntimeObserved(), detail.getCurrentPackedRegionY());
		out.append(',');
		quoted(out, "runtimeActive").append(':');
		if (detail.isRuntimeObserved()) {
			out.append(detail.isRuntimeActive());
		} else {
			out.append("null");
		}
		out.append(',');
		out.append("\"runtimeInstanceCount\":")
			.append(detail.getRuntimeInstanceCount()).append(',');
		out.append("\"replacementObjectInstanceCount\":")
			.append(detail.getReplacementObjectInstanceCount()).append('}');
	}

	private static void appendNullableInteger(
		final StringBuilder out,
		final String name,
		final boolean present,
		final int value) {
		quoted(out, name).append(':');
		if (present) {
			out.append(value);
		} else {
			out.append("null");
		}
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

	private static void appendNullableLong(
		final StringBuilder out,
		final Long value) {
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

	private static boolean capturesInterestOwnership(
		final String eventType,
		final WorldRegionInterestDelta interestDelta) {
		return !"move".equals(eventType)
			|| (interestDelta != null && !interestDelta.isNoOp());
	}

	private static boolean capturesRegionRetirement(
		final String eventType,
		final WorldRegionInterestDelta interestDelta) {
		return capturesInterestOwnership(eventType, interestDelta);
	}

	private static List<WorldRegionKey> updateRetirementCandidates(
		final TraceState state,
		final LayeredRegionInterestOwnershipLedger.Change ownershipChange) {
		if (ownershipChange == null) {
			return Collections.emptyList();
		}
		List<WorldRegionKey> transitionKeys = new ArrayList<WorldRegionKey>();
		for (LayeredRegionInterestOwnershipLedger.Entry entry
			: ownershipChange.getEntries()) {
			if (entry.getInterestState()
				== LayeredRegionInterestOwnershipLedger.InterestState.RETAINED) {
				continue;
			}
			WorldRegionKey key = entry.getLogicalRegionKey();
			transitionKeys.add(key);
			if (entry.getCurrentReferenceCount() > 0) {
				state.retirementCandidates.remove(key);
			}
			if (entry.isGloballyReleased()
				&& !state.retirementCandidates.contains(key)) {
				if (state.retirementCandidates.size()
					>= MAX_TRACE_RETIREMENT_CANDIDATES) {
					Iterator<WorldRegionKey> oldest =
						state.retirementCandidates.iterator();
					WorldRegionKey droppedKey = oldest.next();
					oldest.remove();
					state.retirementCandidateDroppedCount = Math.addExact(
						state.retirementCandidateDroppedCount, 1L);
					if (state.retirementDecisionCandidates.remove(droppedKey)
						!= null) {
						state.retirementDecisionCandidateDroppedCount = Math.addExact(
							state.retirementDecisionCandidateDroppedCount, 1L);
					}
				}
				state.retirementCandidates.add(key);
			}
		}
		return transitionKeys;
	}

	private static void pruneCanceledRetirementCandidates(
		final TraceState state,
		final RegionRetirementMetadata retirement) {
		for (RegionRetirementEntryMetadata entry : retirement.getEntries()) {
			if (entry.isTrackedCandidate() && entry.getReleasedAtTick() == null) {
				state.retirementCandidates.remove(entry.getLogicalRegionKey());
			}
		}
	}

	private static List<LayeredRegionRetirementEligibilityLedger.Snapshot>
		updateRetirementDecisionCandidates(
			final TraceState state,
			final RegionRetirementMetadata retirement) {
		for (RegionRetirementEntryMetadata entry : retirement.getEntries()) {
			WorldRegionKey key = entry.getLogicalRegionKey();
			if (!entry.isTrackedCandidate() || !entry.isRetirementEligible()
				|| state.retirementDecisionCandidates.containsKey(key)) {
				continue;
			}
			if (state.retirementDecisionCandidates.size()
				>= MAX_TRACE_RETIREMENT_CANDIDATES) {
				Iterator<Map.Entry<WorldRegionKey,
					LayeredRegionRetirementEligibilityLedger.Snapshot>> oldest =
					state.retirementDecisionCandidates.entrySet().iterator();
				oldest.next();
				oldest.remove();
				state.retirementDecisionCandidateDroppedCount = Math.addExact(
					state.retirementDecisionCandidateDroppedCount, 1L);
			}
			state.retirementDecisionCandidates.put(key, entry.getSnapshot());
		}
		return Collections.unmodifiableList(
			new ArrayList<LayeredRegionRetirementEligibilityLedger.Snapshot>(
				state.retirementDecisionCandidates.values()));
	}

	private static void pruneRefusedRetirementDecisionCandidates(
		final TraceState state,
		final RegionRetirementDecisionMetadata decisions) {
		for (RegionRetirementDecisionEntryMetadata entry : decisions.getEntries()) {
			if (!entry.isEligible()) {
				state.retirementDecisionCandidates.remove(
					entry.getLogicalRegionKey());
			}
		}
	}

	private static InterestOwnershipSource syntheticInterestOwnershipSource() {
		return new InterestOwnershipSource() {
			@Override
			public InterestOwnershipMetadata capture(
				final WorldRegionWindow currentWindow,
				final int maximumRegionsPerWindow) {
				return InterestOwnershipMetadata.syntheticCurrent(
					currentWindow, maximumRegionsPerWindow);
			}
		};
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

	/** Supplies one same-version global-interest ownership view. */
	@FunctionalInterface
	public interface InterestOwnershipSource {
		InterestOwnershipMetadata capture(
			WorldRegionWindow currentWindow,
			int maximumRegionsPerWindow);
	}

	/** Supplies bounded retirement evidence without exposing lifecycle control. */
	@FunctionalInterface
	public interface RegionRetirementSource {
		RegionRetirementMetadata capture(
			List<WorldRegionKey> transitionKeys,
			List<WorldRegionKey> trackedCandidateKeys,
			long droppedCandidateCount,
			int maximumRegions);
	}

	/** Atomically rechecks bounded immutable candidates without lifecycle control. */
	@FunctionalInterface
	public interface RegionRetirementDecisionSource {
		RegionRetirementDecisionMetadata capture(
			List<LayeredRegionRetirementEligibilityLedger.Snapshot> candidates,
			long droppedCandidateCount,
			int maximumRegions);
	}

	/** Captures contents evidence for exact packed readiness without control. */
	@FunctionalInterface
	public interface PackedRegionRetirementSafetySource {
		LayeredPackedRegionRetirementSafetyAssessment capture(
			LayeredPackedRegionRetirementReadiness readiness,
			int maximumPackedSources);
	}

	/** Projects count-only authored origins onto exact safety sources. */
	@FunctionalInterface
	public interface PackedRegionAuthoredConstructionSource {
		LayeredPackedRegionAuthoredConstructionObservation capture(
			LayeredPackedRegionRetirementSafetyAssessment safety,
			int maximumPackedSources);
	}

	/** Compares authored identities with a bounded detached runtime census. */
	@FunctionalInterface
	public interface PackedRegionAuthoredProvenanceSource {
		LayeredPackedRegionAuthoredProvenanceObservation capture(
			LayeredPackedRegionRetirementSafetyAssessment safety);
	}

	/** Projects inert reconstruction dependency evidence without lifecycle control. */
	@FunctionalInterface
	public interface PackedRegionAuthoredReconstructionSource {
		LayeredPackedRegionAuthoredReconstructionObservation capture(
			LayeredPackedRegionRetirementSafetyAssessment safety,
			int maximumSafetySources,
			int maximumRequirementSources);
	}

	/** Expands authored dependencies while preserving support-only evidence. */
	@FunctionalInterface
	public interface PackedRegionAuthoredReconstructionCohortSource {
		LayeredPackedRegionAuthoredReconstructionCohortAnalysis capture(
			LayeredPackedRegionRetirementSafetyAssessment safety,
			int maximumCohortSources,
			int maximumRequirementSources);
	}

	/** Attributes exact cohort edges without acquiring their requirements. */
	@FunctionalInterface
	public interface PackedRegionAuthoredReconstructionCohortAttributionSource {
		LayeredPackedRegionAuthoredReconstructionCohortAttribution capture(
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
			int maximumEdges,
			int maximumBridgePlacements);
	}

	/** Compares forward closure with bounded whole-recipe graph topology. */
	@FunctionalInterface
	public interface PackedRegionAuthoredReconstructionTopologySource {
		LayeredPackedRegionAuthoredReconstructionTopologyAnalysis capture(
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
			int maximumSources,
			int maximumRelationships);
	}

	/** Separates source-local replay from potential spatial dependency evidence. */
	@FunctionalInterface
	public interface PackedRegionAuthoredReconstructionDependencySemanticsSource {
		LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
			capture(
				LayeredPackedRegionRetirementSafetyAssessment safety,
				int maximumSelectedSources,
				int maximumSupportSources,
				int maximumIncomingOwners,
				int maximumIncomingPlacements);
	}

	/** Captures detached active NPC ownership/residency evidence only. */
	@FunctionalInterface
	public interface PackedRegionActiveNpcResidencySource {
		LayeredPackedRegionActiveNpcResidencyObservation capture(
			LayeredPackedRegionRetirementSafetyAssessment safety,
			int maximumInstances,
			int maximumRelevantDetails);
	}

	/** Reassesses one retained proposal against a newer atomic diagnostic view. */
	@FunctionalInterface
	public interface PackedRegionRetirementRefinementReassessmentSource {
		LayeredPackedRegionRetirementRefinementReassessment captureIfFresh(
			LayeredPackedRegionRetirementRefinementProposal previousProposal,
			int maximumCandidateSources,
			int maximumSupportSources,
			int maximumNpcInstances,
			int maximumRelevantNpcDetails,
			int maximumActiveNpcRequirements);
	}

	/** Captures detached runtime preservation burdens for one exact proposal. */
	@FunctionalInterface
	public interface PackedRegionPreservationBurdenSource {
		LayeredPackedRegionPreservationBurdenAssessment capture(
			LayeredPackedRegionRetirementRefinementProposal proposal,
			int maximumCandidateSources);
	}

	/** Captures privacy-safe detached dynamic-object constructor records. */
	@FunctionalInterface
	public interface PackedRegionDynamicObjectPreservationSource {
		LayeredPackedRegionDynamicObjectPreservationRecord capture(
			LayeredPackedRegionRetirementRefinementProposal proposal,
			int maximumCandidateSources,
			int maximumDynamicObjects);
	}

	/** Detached verification-only NPC owner preservation refusal. */
	public static final class PackedRegionNpcOwnerPreservationNoOpMetadata {
		private final String reason;
		private final long generation;
		private final long requirementsObservedAtTick;
		private final int selectedSourceCount;
		private final int requiredEventLinkCount;
		private final int requiredOwnerCount;
		private final boolean ownerScopeEntered;
		private final boolean sourceLifecycleInvoked;
		private final int absentSourceCount;
		private final int reconstructedSourceCount;
		private final boolean preservedConsumerInvoked;
		private final LayeredPackedRegionSourceAbsencePreflight
			sourceAbsencePreflight;
		private final LayeredPackedRegionReloadRecipe sourceReloadRecipe;
		private final LayeredPackedRegionTerrainVerificationBatch
			sourceTerrainVerification;
		private final LayeredPackedRegionAuthoredCollisionVerificationBatch
			sourceAuthoredCollisionVerification;
		private final
			LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
				sourceAuthoredCollisionApplicationVerification;
		private final LayeredPackedRegionAuthoredSourceStateVerificationBatch
			sourceAuthoredStateVerification;
		private final
			LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
				sourceTransactionalAuthoredStateVerification;

		private PackedRegionNpcOwnerPreservationNoOpMetadata(
			final String reason,
			final long generation,
			final long requirementsObservedAtTick,
			final int selectedSourceCount,
			final int requiredEventLinkCount,
			final int requiredOwnerCount,
			final boolean ownerScopeEntered,
			final boolean sourceLifecycleInvoked,
			final int absentSourceCount,
			final int reconstructedSourceCount,
			final boolean preservedConsumerInvoked,
			final LayeredPackedRegionSourceAbsencePreflight
				sourceAbsencePreflight,
			final LayeredPackedRegionReloadRecipe sourceReloadRecipe,
			final LayeredPackedRegionTerrainVerificationBatch
				sourceTerrainVerification,
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				sourceAuthoredCollisionVerification,
			final
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					sourceAuthoredCollisionApplicationVerification,
			final LayeredPackedRegionAuthoredSourceStateVerificationBatch
				sourceAuthoredStateVerification,
			final
				LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
					sourceTransactionalAuthoredStateVerification) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.generation = generation;
			this.requirementsObservedAtTick = requirementsObservedAtTick;
			this.selectedSourceCount = selectedSourceCount;
			this.requiredEventLinkCount = requiredEventLinkCount;
			this.requiredOwnerCount = requiredOwnerCount;
			this.ownerScopeEntered = ownerScopeEntered;
			this.sourceLifecycleInvoked = sourceLifecycleInvoked;
			this.absentSourceCount = absentSourceCount;
			this.reconstructedSourceCount = reconstructedSourceCount;
			this.preservedConsumerInvoked = preservedConsumerInvoked;
			this.sourceAbsencePreflight = sourceAbsencePreflight;
			this.sourceReloadRecipe = sourceReloadRecipe;
			this.sourceTerrainVerification = sourceTerrainVerification;
			this.sourceAuthoredCollisionVerification =
				sourceAuthoredCollisionVerification;
			this.sourceAuthoredCollisionApplicationVerification =
				sourceAuthoredCollisionApplicationVerification;
			this.sourceAuthoredStateVerification =
				sourceAuthoredStateVerification;
			this.sourceTransactionalAuthoredStateVerification =
				sourceTransactionalAuthoredStateVerification;
			if ((!"OWNER_SCOPE_REFUSED".equals(reason)
					&& !"SOURCE_LIFECYCLE_UNAVAILABLE".equals(reason))
				|| generation < 0L || requirementsObservedAtTick < 0L
				|| selectedSourceCount < 0 || requiredEventLinkCount < 0
				|| requiredOwnerCount < 0 || absentSourceCount != 0
				|| reconstructedSourceCount != 0
				|| preservedConsumerInvoked
				|| ownerScopeEntered != sourceLifecycleInvoked
				|| ("OWNER_SCOPE_REFUSED".equals(reason)
					&& (ownerScopeEntered || sourceAbsencePreflight != null
						|| sourceReloadRecipe != null
						|| sourceTerrainVerification != null
						|| sourceAuthoredCollisionVerification != null
						|| sourceAuthoredCollisionApplicationVerification
							!= null
						|| sourceAuthoredStateVerification != null
						|| sourceTransactionalAuthoredStateVerification
							!= null))
				|| ("SOURCE_LIFECYCLE_UNAVAILABLE".equals(reason)
					&& (!ownerScopeEntered
						|| sourceAbsencePreflight == null
						|| sourceReloadRecipe == null
						|| sourceTerrainVerification == null
						|| sourceAuthoredCollisionVerification == null
						|| sourceAuthoredCollisionApplicationVerification
							== null
						|| sourceAuthoredStateVerification == null
						|| sourceTransactionalAuthoredStateVerification
							== null))
				|| (sourceAbsencePreflight != null
					&& (sourceAbsencePreflight.getGeneration() != generation
						|| sourceAbsencePreflight
							.getRequirementsObservedAtTick()
								!= requirementsObservedAtTick
						|| sourceAbsencePreflight.getSourceCount()
							!= selectedSourceCount
						|| sourceAbsencePreflight
							.isSourceAbsencePerformed()
						|| sourceAbsencePreflight
							.isSourceReconstructionPerformed()
						|| sourceAbsencePreflight.isRuntimeHandleRetained()
						|| sourceAbsencePreflight.isLifecycleAuthority()))
				|| (sourceReloadRecipe != null
					&& (sourceReloadRecipe.getGeneration() != generation
						|| sourceReloadRecipe
							.getRequirementsObservedAtTick()
								!= requirementsObservedAtTick
						|| sourceReloadRecipe.getSourceCount()
							!= selectedSourceCount
						|| sourceAbsencePreflight == null
						|| sourceReloadRecipe.getObservedAtTick()
							!= sourceAbsencePreflight.getObservedAtTick()
						|| sourceReloadRecipe.getResidencyMirrorVersion()
							!= sourceAbsencePreflight
								.getResidencyMirrorVersion()
						|| !sourceReloadRecipe
							.isDetachedDefinitionComplete()
						|| sourceReloadRecipe.isExecutableReload()
						|| sourceReloadRecipe
							.isRegionContainerCreated()
						|| sourceReloadRecipe
							.isSourceAbsencePerformed()
						|| sourceReloadRecipe
							.isSourceReconstructionPerformed()
						|| sourceReloadRecipe.isAuthoredReplayPerformed()
						|| sourceReloadRecipe
							.isCollisionRebuildPerformed()
						|| sourceReloadRecipe.isRuntimeHandleRetained()
						|| sourceReloadRecipe.isRegionRegistryMutated()
						|| sourceReloadRecipe.isResidencyMirrorMutated()
						|| sourceReloadRecipe.isVisibilityCacheMutated()
						|| sourceReloadRecipe.isArrivalGate()
						|| sourceReloadRecipe.isLifecycleAuthority()))
				|| (sourceTerrainVerification != null
					&& (sourceTerrainVerification.getGeneration()
							!= generation
						|| sourceTerrainVerification
							.getRequirementsObservedAtTick()
								!= requirementsObservedAtTick
						|| sourceTerrainVerification.getSourceCount()
							!= selectedSourceCount
						|| sourceReloadRecipe == null
						|| sourceTerrainVerification.getObservedAtTick()
							!= sourceReloadRecipe.getObservedAtTick()
						|| sourceTerrainVerification
							.getResidencyMirrorVersion()
								!= sourceReloadRecipe
									.getResidencyMirrorVersion()
						|| sourceTerrainVerification.getAuthoredGeneration()
							!= sourceReloadRecipe.getAuthoredGeneration()
						|| !sourceTerrainVerification.isAllSourcesVerified()
						|| sourceTerrainVerification
							.isRuntimeHandleRetained()
						|| sourceTerrainVerification
							.isSourceAbsencePerformed()
						|| sourceTerrainVerification
							.isSourceReconstructionPerformed()
						|| sourceTerrainVerification
							.isTerrainAppliedToRuntimeSource()
						|| sourceTerrainVerification
							.isAuthoredReplayPerformed()
						|| sourceTerrainVerification
							.isDynamicCollisionRebuildPerformed()
						|| sourceTerrainVerification
							.isActiveFamilyPreservationPerformed()
						|| sourceTerrainVerification
							.isRegionRegistryMutated()
						|| sourceTerrainVerification
							.isResidencyMirrorMutated()
						|| sourceTerrainVerification
							.isVisibilityCacheMutated()
						|| sourceTerrainVerification.isArrivalGate()
						|| sourceTerrainVerification.isVisibilityReleased()
						|| sourceTerrainVerification
							.isLifecycleAuthority()))
				|| (sourceAuthoredCollisionVerification != null
					&& (sourceAuthoredCollisionVerification.getGeneration()
							!= generation
						|| sourceAuthoredCollisionVerification
							.getRequirementsObservedAtTick()
								!= requirementsObservedAtTick
						|| sourceAuthoredCollisionVerification.getSourceCount()
							!= selectedSourceCount
						|| sourceReloadRecipe == null
						|| sourceTerrainVerification == null
						|| sourceAuthoredCollisionVerification
							.getObservedAtTick()
								!= sourceReloadRecipe.getObservedAtTick()
						|| sourceAuthoredCollisionVerification
							.getResidencyMirrorVersion()
								!= sourceReloadRecipe
									.getResidencyMirrorVersion()
						|| sourceAuthoredCollisionVerification
							.getAuthoredGeneration()
								!= sourceReloadRecipe
									.getAuthoredGeneration()
						|| sourceAuthoredCollisionVerification
							.getReplayPlacementCount()
								!= sourceReloadRecipe
									.getAuthoredPlacementCount()
						|| !sourceAuthoredCollisionVerification
							.isAllSourcesVerified()
						|| !sourceAuthoredCollisionVerification
							.isRuntimeDefinitionCapturePerformed()
						|| !sourceAuthoredCollisionVerification
							.isCollisionFootprintDerivationPerformed()
						|| sourceAuthoredCollisionVerification
							.isCollisionApplied()
						|| sourceAuthoredCollisionVerification
							.isCollisionRegistrationAttached()
						|| sourceAuthoredCollisionVerification
							.isRuntimeHandleRetained()
						|| sourceAuthoredCollisionVerification
							.isSourceAbsencePerformed()
						|| sourceAuthoredCollisionVerification
							.isSourceReconstructionPerformed()
						|| sourceAuthoredCollisionVerification
							.isTerrainAppliedToRuntimeSource()
						|| sourceAuthoredCollisionVerification
							.isNpcMembershipApplied()
						|| sourceAuthoredCollisionVerification
							.isGroundItemMembershipApplied()
						|| sourceAuthoredCollisionVerification
							.isSchedulerStateRestored()
						|| sourceAuthoredCollisionVerification
							.isActiveFamilyPreservationPerformed()
						|| sourceAuthoredCollisionVerification
							.isRegionRegistryMutated()
						|| sourceAuthoredCollisionVerification
							.isResidencyMirrorMutated()
						|| sourceAuthoredCollisionVerification
							.isVisibilityCacheMutated()
						|| sourceAuthoredCollisionVerification.isArrivalGate()
						|| sourceAuthoredCollisionVerification
							.isVisibilityReleased()
						|| sourceAuthoredCollisionVerification
							.isLifecycleAuthority()
						|| !collisionSourcesMatch(
							sourceReloadRecipe, sourceTerrainVerification,
							sourceAuthoredCollisionVerification)))
				|| (sourceAuthoredCollisionApplicationVerification != null
					&& (sourceAuthoredCollisionApplicationVerification
							.getGeneration() != generation
						|| sourceAuthoredCollisionApplicationVerification
							.getRequirementsObservedAtTick()
								!= requirementsObservedAtTick
						|| sourceAuthoredCollisionApplicationVerification
							.getSourceCount() != selectedSourceCount
						|| sourceReloadRecipe == null
						|| sourceAuthoredCollisionVerification == null
						|| sourceAuthoredCollisionApplicationVerification
							.getObservedAtTick()
								!= sourceReloadRecipe.getObservedAtTick()
						|| sourceAuthoredCollisionApplicationVerification
							.getResidencyMirrorVersion()
								!= sourceReloadRecipe
									.getResidencyMirrorVersion()
						|| sourceAuthoredCollisionApplicationVerification
							.getAuthoredGeneration()
								!= sourceReloadRecipe
									.getAuthoredGeneration()
						|| sourceAuthoredCollisionApplicationVerification
							.getReplayPlacementCount()
								!= sourceReloadRecipe
									.getAuthoredPlacementCount()
						|| sourceAuthoredCollisionApplicationVerification
							.getAuthoredObjectFootprintCount()
								!= sourceAuthoredCollisionVerification
									.getAuthoredObjectFootprintCount()
						|| sourceAuthoredCollisionApplicationVerification
							.getContributionTileReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getContributionTileReferenceCount()
						|| sourceAuthoredCollisionApplicationVerification
							.getRequiredRegionReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getRequiredRegionReferenceCount()
						|| sourceAuthoredCollisionApplicationVerification
							.getUniqueRequiredRegionReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getUniqueRequiredRegionReferenceCount()
						|| !sourceAuthoredCollisionApplicationVerification
							.getBaselineFingerprintSha256().equals(
								sourceAuthoredCollisionVerification
									.getFingerprintSha256())
						|| !sourceAuthoredCollisionApplicationVerification
							.isAllSourcesVerified()
						|| !sourceAuthoredCollisionApplicationVerification
							.isRuntimeDefinitionCapturePerformed()
						|| !sourceAuthoredCollisionApplicationVerification
							.isCollisionFootprintDerivationPerformed()
						|| !sourceAuthoredCollisionApplicationVerification
							.isCollisionAppliedToDisposableRegions()
						|| sourceAuthoredCollisionApplicationVerification
							.isCollisionRegistrationAttached()
						|| sourceAuthoredCollisionApplicationVerification
							.isRuntimeCollisionApplied()
						|| sourceAuthoredCollisionApplicationVerification
							.isRuntimeHandleRetained()
						|| sourceAuthoredCollisionApplicationVerification
							.isSourceAbsencePerformed()
						|| sourceAuthoredCollisionApplicationVerification
							.isSourceReconstructionPerformed()
						|| sourceAuthoredCollisionApplicationVerification
							.isTerrainAppliedToRuntimeSource()
						|| sourceAuthoredCollisionApplicationVerification
							.isAuthoredObjectMembershipAppliedToRuntimeSource()
						|| sourceAuthoredCollisionApplicationVerification
							.isNpcMembershipApplied()
						|| sourceAuthoredCollisionApplicationVerification
							.isGroundItemMembershipApplied()
						|| sourceAuthoredCollisionApplicationVerification
							.isSchedulerStateRestored()
						|| sourceAuthoredCollisionApplicationVerification
							.isActiveFamilyPreservationPerformed()
						|| sourceAuthoredCollisionApplicationVerification
							.isRegionRegistryMutated()
						|| sourceAuthoredCollisionApplicationVerification
							.isResidencyMirrorMutated()
						|| sourceAuthoredCollisionApplicationVerification
							.isVisibilityCacheMutated()
						|| sourceAuthoredCollisionApplicationVerification
							.isArrivalGate()
						|| sourceAuthoredCollisionApplicationVerification
							.isVisibilityReleased()
						|| sourceAuthoredCollisionApplicationVerification
							.isLifecycleAuthority()
						|| !collisionApplicationSourcesMatch(
							sourceAuthoredCollisionVerification,
							sourceAuthoredCollisionApplicationVerification)))
				|| (sourceAuthoredStateVerification != null
					&& (sourceAuthoredStateVerification.getGeneration()
							!= generation
						|| sourceAuthoredStateVerification
							.getRequirementsObservedAtTick()
								!= requirementsObservedAtTick
						|| sourceAuthoredStateVerification.getSourceCount()
							!= selectedSourceCount
						|| sourceReloadRecipe == null
						|| sourceAuthoredCollisionVerification == null
						|| sourceAuthoredCollisionApplicationVerification
							== null
						|| sourceAuthoredStateVerification.getObservedAtTick()
							!= sourceReloadRecipe.getObservedAtTick()
						|| sourceAuthoredStateVerification
							.getResidencyMirrorVersion()
								!= sourceReloadRecipe
									.getResidencyMirrorVersion()
						|| sourceAuthoredStateVerification
							.getAuthoredGeneration()
								!= sourceReloadRecipe
									.getAuthoredGeneration()
						|| sourceAuthoredStateVerification
							.getReplayPlacementCount()
								!= sourceReloadRecipe
									.getAuthoredPlacementCount()
						|| sourceAuthoredStateVerification
							.getAuthoredObjectFootprintCount()
								!= sourceAuthoredCollisionVerification
									.getAuthoredObjectFootprintCount()
						|| sourceAuthoredStateVerification
							.getContributionTileReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getContributionTileReferenceCount()
						|| sourceAuthoredStateVerification
							.getUniqueContributionTileReferenceCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getUniqueContributionTileReferenceCount()
						|| sourceAuthoredStateVerification
							.getRequiredRegionReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getRequiredRegionReferenceCount()
						|| sourceAuthoredStateVerification
							.getUniqueRequiredRegionReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getUniqueRequiredRegionReferenceCount()
						|| !sourceAuthoredStateVerification
							.getBaselineFingerprintSha256().equals(
								sourceAuthoredCollisionVerification
									.getFingerprintSha256())
						|| sourceAuthoredStateVerification
							.getCombinedDisposableRegionConstructionCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getDisposableCollisionRegionConstructionCount()
						|| sourceAuthoredStateVerification
							.getCombinedCollisionApplicationCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getCollisionApplicationCount()
						|| sourceAuthoredStateVerification
							.getCombinedCollisionBoundaryCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getHeldBoundaryCount()
						|| sourceAuthoredStateVerification
							.getCombinedVerifiedRegionTileCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getVerifiedRegionTileCount()
						|| sourceAuthoredStateVerification
							.getCombinedBlockingSceneryContributionCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getBlockingSceneryContributionCount()
						|| sourceAuthoredStateVerification
							.getCombinedDynamicCollisionContributionCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getDynamicCollisionContributionCount()
						|| sourceAuthoredStateVerification
							.getCombinedDynamicProjectileContributionCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getDynamicProjectileContributionCount()
						|| !sourceAuthoredStateVerification
							.isAllSourcesVerified()
						|| !sourceAuthoredStateVerification
							.isRuntimeDefinitionCapturePerformed()
						|| !sourceAuthoredStateVerification
							.isCollisionFootprintDerivationPerformed()
						|| !sourceAuthoredStateVerification
							.isTerrainAppliedToCombinedDisposableSourceRegions()
						|| !sourceAuthoredStateVerification
							.isAuthoredObjectMembershipAppliedToCombinedDisposableSourceRegions()
						|| !sourceAuthoredStateVerification
							.isCollisionAppliedToSameDisposableRegionUnions()
						|| sourceAuthoredStateVerification
							.isCollisionRegistrationAttached()
						|| sourceAuthoredStateVerification
							.isRuntimeCollisionApplied()
						|| sourceAuthoredStateVerification
							.isRuntimeHandleRetained()
						|| sourceAuthoredStateVerification
							.isSourceAbsencePerformed()
						|| sourceAuthoredStateVerification
							.isSourceReconstructionPerformed()
						|| sourceAuthoredStateVerification
							.isTerrainAppliedToRuntimeSource()
						|| sourceAuthoredStateVerification
							.isAuthoredObjectMembershipAppliedToRuntimeSource()
						|| sourceAuthoredStateVerification
							.isNpcMembershipApplied()
						|| sourceAuthoredStateVerification
							.isGroundItemMembershipApplied()
						|| sourceAuthoredStateVerification
							.isSchedulerStateRestored()
						|| sourceAuthoredStateVerification
							.isActiveFamilyPreservationPerformed()
						|| sourceAuthoredStateVerification
							.isRegionRegistryMutated()
						|| sourceAuthoredStateVerification
							.isResidencyMirrorMutated()
						|| sourceAuthoredStateVerification
							.isVisibilityCacheMutated()
						|| sourceAuthoredStateVerification.isArrivalGate()
						|| sourceAuthoredStateVerification
							.isVisibilityReleased()
						|| sourceAuthoredStateVerification
							.isLifecycleAuthority()
						|| !authoredStateSourcesMatch(
							sourceAuthoredCollisionVerification,
							sourceAuthoredCollisionApplicationVerification,
							sourceAuthoredStateVerification)))
				|| (sourceTransactionalAuthoredStateVerification != null
					&& (sourceTransactionalAuthoredStateVerification
							.getGeneration() != generation
						|| sourceTransactionalAuthoredStateVerification
							.getRequirementsObservedAtTick()
								!= requirementsObservedAtTick
						|| sourceTransactionalAuthoredStateVerification
							.getSourceCount() != selectedSourceCount
						|| sourceReloadRecipe == null
						|| sourceAuthoredCollisionVerification == null
						|| sourceAuthoredCollisionApplicationVerification
							== null
						|| sourceAuthoredStateVerification == null
						|| sourceTransactionalAuthoredStateVerification
							.getObservedAtTick()
								!= sourceReloadRecipe.getObservedAtTick()
						|| sourceTransactionalAuthoredStateVerification
							.getResidencyMirrorVersion()
								!= sourceReloadRecipe
									.getResidencyMirrorVersion()
						|| sourceTransactionalAuthoredStateVerification
							.getAuthoredGeneration()
								!= sourceReloadRecipe.getAuthoredGeneration()
						|| sourceTransactionalAuthoredStateVerification
							.getReplayPlacementCount()
								!= sourceReloadRecipe
									.getAuthoredPlacementCount()
						|| sourceTransactionalAuthoredStateVerification
							.getAuthoredObjectFootprintCount()
								!= sourceAuthoredCollisionVerification
									.getAuthoredObjectFootprintCount()
						|| sourceTransactionalAuthoredStateVerification
							.getContributionTileReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getContributionTileReferenceCount()
						|| sourceTransactionalAuthoredStateVerification
							.getRequiredRegionReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getRequiredRegionReferenceCount()
						|| sourceTransactionalAuthoredStateVerification
							.getUniqueRequiredRegionReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getUniqueRequiredRegionReferenceCount()
						|| !sourceTransactionalAuthoredStateVerification
							.getBaselineFingerprintSha256().equals(
								sourceAuthoredCollisionVerification
									.getFingerprintSha256())
						|| sourceTransactionalAuthoredStateVerification
							.getTransactionalDisposableRegionConstructionCount()
								!= sourceAuthoredStateVerification
									.getCombinedDisposableRegionConstructionCount()
						|| sourceTransactionalAuthoredStateVerification
							.getObjectCollisionTransactionCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getCollisionApplicationCount()
						|| sourceTransactionalAuthoredStateVerification
							.getObjectCollisionTransactionBoundaryCount()
								!= sourceAuthoredCollisionApplicationVerification
									.getHeldBoundaryCount()
						|| sourceTransactionalAuthoredStateVerification
							.getDisposableCacheInvalidationCount()
								!= sourceTransactionalAuthoredStateVerification
									.getObjectCollisionTransactionCount()
						|| sourceTransactionalAuthoredStateVerification
							.getCollisionRegistrationCount()
								!= sourceTransactionalAuthoredStateVerification
									.getObjectCollisionTransactionCount()
						|| sourceTransactionalAuthoredStateVerification
							.getCollisionRegistrationContributionCount()
								!= sourceAuthoredCollisionVerification
									.getContributionTileReferenceCount()
						|| sourceTransactionalAuthoredStateVerification
							.getCollisionRegistrationRegionReferenceCount()
								!= sourceAuthoredCollisionVerification
									.getRequiredRegionReferenceCount()
						|| sourceTransactionalAuthoredStateVerification
							.getTransactionalVerifiedRegionTileCount()
								!= sourceAuthoredStateVerification
									.getCombinedVerifiedRegionTileCount()
						|| sourceTransactionalAuthoredStateVerification
							.getTransactionalBlockingSceneryContributionCount()
								!= sourceAuthoredStateVerification
									.getCombinedBlockingSceneryContributionCount()
						|| sourceTransactionalAuthoredStateVerification
							.getTransactionalDynamicCollisionContributionCount()
								!= sourceAuthoredStateVerification
									.getCombinedDynamicCollisionContributionCount()
						|| sourceTransactionalAuthoredStateVerification
							.getTransactionalDynamicProjectileContributionCount()
								!= sourceAuthoredStateVerification
									.getCombinedDynamicProjectileContributionCount()
						|| !sourceTransactionalAuthoredStateVerification
							.isAllSourcesVerified()
						|| !sourceTransactionalAuthoredStateVerification
							.isRuntimeDefinitionCapturePerformed()
						|| !sourceTransactionalAuthoredStateVerification
							.isCollisionFootprintDerivationPerformed()
						|| !sourceTransactionalAuthoredStateVerification
							.isObjectCollisionTransactionAppliedToDisposableRegions()
						|| !sourceTransactionalAuthoredStateVerification
							.isCollisionRegistrationAttachedToDisposableObjects()
						|| !sourceTransactionalAuthoredStateVerification
							.isDisposableCacheInvalidationOnly()
						|| sourceTransactionalAuthoredStateVerification
							.isRuntimeCollisionApplied()
						|| sourceTransactionalAuthoredStateVerification
							.isRuntimeHandleRetained()
						|| sourceTransactionalAuthoredStateVerification
							.isSourceAbsencePerformed()
						|| sourceTransactionalAuthoredStateVerification
							.isSourceReconstructionPerformed()
						|| sourceTransactionalAuthoredStateVerification
							.isTerrainAppliedToRuntimeSource()
						|| sourceTransactionalAuthoredStateVerification
							.isAuthoredObjectMembershipAppliedToRuntimeSource()
						|| sourceTransactionalAuthoredStateVerification
							.isNpcMembershipApplied()
						|| sourceTransactionalAuthoredStateVerification
							.isGroundItemMembershipApplied()
						|| sourceTransactionalAuthoredStateVerification
							.isSchedulerStateRestored()
						|| sourceTransactionalAuthoredStateVerification
							.isActiveFamilyPreservationPerformed()
						|| sourceTransactionalAuthoredStateVerification
							.isRuntimeCacheInvalidated()
						|| sourceTransactionalAuthoredStateVerification
							.isRegionRegistryMutated()
						|| sourceTransactionalAuthoredStateVerification
							.isResidencyMirrorMutated()
						|| sourceTransactionalAuthoredStateVerification
							.isVisibilityCacheMutated()
						|| sourceTransactionalAuthoredStateVerification
							.isArrivalGate()
						|| sourceTransactionalAuthoredStateVerification
							.isVisibilityReleased()
						|| sourceTransactionalAuthoredStateVerification
							.isLifecycleAuthority()
						|| !transactionalAuthoredStateSourcesMatch(
							sourceAuthoredCollisionVerification,
							sourceAuthoredCollisionApplicationVerification,
							sourceAuthoredStateVerification,
							sourceTransactionalAuthoredStateVerification)))) {
				throw new IllegalArgumentException(
					"NPC owner preservation no-op metadata is inconsistent");
			}
		}

		public static PackedRegionNpcOwnerPreservationNoOpMetadata of(
			final String reason,
			final long generation,
			final long requirementsObservedAtTick,
			final int selectedSourceCount,
			final int requiredEventLinkCount,
			final int requiredOwnerCount,
			final boolean ownerScopeEntered,
			final boolean sourceLifecycleInvoked,
			final int absentSourceCount,
			final int reconstructedSourceCount,
			final boolean preservedConsumerInvoked,
			final LayeredPackedRegionSourceAbsencePreflight
				sourceAbsencePreflight,
			final LayeredPackedRegionReloadRecipe sourceReloadRecipe,
			final LayeredPackedRegionTerrainVerificationBatch
				sourceTerrainVerification,
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				sourceAuthoredCollisionVerification,
			final
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					sourceAuthoredCollisionApplicationVerification,
			final LayeredPackedRegionAuthoredSourceStateVerificationBatch
				sourceAuthoredStateVerification,
			final
				LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
					sourceTransactionalAuthoredStateVerification) {
			return new PackedRegionNpcOwnerPreservationNoOpMetadata(
				reason, generation, requirementsObservedAtTick,
				selectedSourceCount, requiredEventLinkCount,
				requiredOwnerCount, ownerScopeEntered,
				sourceLifecycleInvoked, absentSourceCount,
				reconstructedSourceCount, preservedConsumerInvoked,
				sourceAbsencePreflight, sourceReloadRecipe,
				sourceTerrainVerification,
				sourceAuthoredCollisionVerification,
				sourceAuthoredCollisionApplicationVerification,
				sourceAuthoredStateVerification,
				sourceTransactionalAuthoredStateVerification);
		}

		private static boolean collisionSourcesMatch(
			final LayeredPackedRegionReloadRecipe reload,
			final LayeredPackedRegionTerrainVerificationBatch terrain,
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				collision) {
			if (reload.getSourceCount() != terrain.getSourceCount()
				|| reload.getSourceCount() != collision.getSourceCount()) {
				return false;
			}
			for (int ordinal = 0; ordinal < reload.getSourceCount();
					ordinal++) {
				LayeredPackedRegionReloadRecipe.SourceRecipe recipeSource =
					reload.getSources().get(ordinal);
				LayeredPackedRegionTerrainVerificationBatch
					.SourceVerification terrainSource =
						terrain.getSources().get(ordinal);
				LayeredPackedRegionAuthoredCollisionVerificationBatch
					.SourceVerification collisionSource =
						collision.getSources().get(ordinal);
				if (terrainSource.getSourceOrdinal() != ordinal
					|| collisionSource.getSourceOrdinal() != ordinal
					|| recipeSource.getPackedRegionX()
						!= collisionSource.getPackedRegionX()
					|| recipeSource.getPackedRegionY()
						!= collisionSource.getPackedRegionY()
					|| terrainSource.getPackedRegionX()
						!= collisionSource.getPackedRegionX()
					|| terrainSource.getPackedRegionY()
						!= collisionSource.getPackedRegionY()
					|| recipeSource.getAuthoredPlacementCount()
						!= collisionSource.getReplayPlacementCount()
					|| !terrainSource.getTerrainFingerprintSha256().equals(
						collisionSource.getTerrainFingerprintSha256())) {
					return false;
				}
			}
			return true;
		}

		private static boolean collisionApplicationSourcesMatch(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				collision,
			final
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					application) {
			if (collision.getSourceCount() != application.getSourceCount()) {
				return false;
			}
			for (int ordinal = 0; ordinal < collision.getSourceCount();
					ordinal++) {
				LayeredPackedRegionAuthoredCollisionVerificationBatch
					.SourceVerification collisionSource =
						collision.getSources().get(ordinal);
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					.SourceVerification applicationSource =
						application.getSources().get(ordinal);
				if (collisionSource.getSourceOrdinal() != ordinal
					|| applicationSource.getSourceOrdinal() != ordinal
					|| collisionSource.getPackedRegionX()
						!= applicationSource.getPackedRegionX()
					|| collisionSource.getPackedRegionY()
						!= applicationSource.getPackedRegionY()
					|| collisionSource.getReplayPlacementCount()
						!= applicationSource.getReplayPlacementCount()
					|| collisionSource.getAuthoredObjectFootprintCount()
						!= applicationSource
							.getAuthoredObjectFootprintCount()
					|| collisionSource.getContributionTileReferenceCount()
						!= applicationSource
							.getContributionTileReferenceCount()
					|| collisionSource.getRequiredRegionReferenceCount()
						!= applicationSource
							.getRequiredRegionReferenceCount()
					|| collisionSource.getUniqueRequiredRegionCount()
						!= applicationSource.getUniqueRequiredRegionCount()
					|| !collisionSource.getTerrainFingerprintSha256().equals(
						applicationSource.getTerrainFingerprintSha256())
					|| !collisionSource
						.getAuthoredReplayFingerprintSha256().equals(
							applicationSource
								.getAuthoredReplayFingerprintSha256())
					|| !collisionSource
						.getDefinitionCaptureFingerprintSha256().equals(
							applicationSource
								.getDefinitionCaptureFingerprintSha256())
					|| !collisionSource
						.getCollisionFootprintFingerprintSha256().equals(
							applicationSource
								.getCollisionFootprintFingerprintSha256())) {
					return false;
				}
			}
			return true;
		}

		private static boolean authoredStateSourcesMatch(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				collision,
			final
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					application,
			final LayeredPackedRegionAuthoredSourceStateVerificationBatch
				state) {
			if (collision.getSourceCount() != application.getSourceCount()
				|| collision.getSourceCount() != state.getSourceCount()) {
				return false;
			}
			for (int ordinal = 0; ordinal < collision.getSourceCount();
					ordinal++) {
				LayeredPackedRegionAuthoredCollisionVerificationBatch
					.SourceVerification collisionSource =
						collision.getSources().get(ordinal);
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					.SourceVerification applicationSource =
						application.getSources().get(ordinal);
				LayeredPackedRegionAuthoredSourceStateVerificationBatch
					.SourceVerification stateSource =
						state.getSources().get(ordinal);
				if (collisionSource.getSourceOrdinal() != ordinal
					|| applicationSource.getSourceOrdinal() != ordinal
					|| stateSource.getSourceOrdinal() != ordinal
					|| collisionSource.getPackedRegionX()
						!= stateSource.getPackedRegionX()
					|| collisionSource.getPackedRegionY()
						!= stateSource.getPackedRegionY()
					|| collisionSource.getReplayPlacementCount()
						!= stateSource.getReplayPlacementCount()
					|| collisionSource.getAuthoredObjectFootprintCount()
						!= stateSource.getAuthoredObjectFootprintCount()
					|| collisionSource.getContributionTileReferenceCount()
						!= stateSource.getContributionTileReferenceCount()
					|| applicationSource.getUniqueContributionTileCount()
						!= stateSource.getUniqueContributionTileCount()
					|| collisionSource.getRequiredRegionReferenceCount()
						!= stateSource.getRequiredRegionReferenceCount()
					|| collisionSource.getUniqueRequiredRegionCount()
						!= stateSource.getUniqueRequiredRegionCount()
					|| applicationSource
						.getDisposableRegionConstructionCount()
							!= stateSource
								.getDisposableRegionConstructionCount()
					|| applicationSource.getCollisionApplicationCount()
						!= stateSource.getCollisionApplicationCount()
					|| applicationSource.getHeldBoundaryCount()
						!= stateSource.getCollisionBoundaryCount()
					|| applicationSource.getVerifiedRegionTileCount()
						!= stateSource.getVerifiedRegionTileCount()
					|| applicationSource
						.getBlockingSceneryContributionCount()
							!= stateSource
								.getBlockingSceneryContributionCount()
					|| applicationSource
						.getDynamicCollisionContributionCount()
							!= stateSource
								.getDynamicCollisionContributionCount()
					|| applicationSource
						.getDynamicProjectileContributionCount()
							!= stateSource
								.getDynamicProjectileContributionCount()
					|| !collisionSource.getTerrainFingerprintSha256().equals(
						stateSource.getTerrainFingerprintSha256())
					|| !collisionSource
						.getAuthoredReplayFingerprintSha256().equals(
							stateSource
								.getAuthoredReplayFingerprintSha256())
					|| !collisionSource
						.getDefinitionCaptureFingerprintSha256().equals(
							stateSource
								.getDefinitionCaptureFingerprintSha256())
					|| !collisionSource
						.getCollisionFootprintFingerprintSha256().equals(
							stateSource
								.getCollisionFootprintFingerprintSha256())
					|| !applicationSource
						.getAppliedCollisionFingerprintSha256().equals(
							stateSource
								.getAppliedCollisionFingerprintSha256())) {
					return false;
				}
			}
			return true;
		}

		private static boolean transactionalAuthoredStateSourcesMatch(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				collision,
			final
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					application,
			final LayeredPackedRegionAuthoredSourceStateVerificationBatch
				state,
			final
				LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
					transactional) {
			if (collision.getSourceCount() != application.getSourceCount()
				|| collision.getSourceCount() != state.getSourceCount()
				|| collision.getSourceCount()
					!= transactional.getSourceCount()) {
				return false;
			}
			for (int ordinal = 0; ordinal < collision.getSourceCount();
					ordinal++) {
				LayeredPackedRegionAuthoredCollisionVerificationBatch
					.SourceVerification collisionSource =
						collision.getSources().get(ordinal);
				LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
					.SourceVerification applicationSource =
						application.getSources().get(ordinal);
				LayeredPackedRegionAuthoredSourceStateVerificationBatch
					.SourceVerification stateSource =
						state.getSources().get(ordinal);
				LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
					.SourceVerification transactionalSource =
						transactional.getSources().get(ordinal);
				if (collisionSource.getSourceOrdinal() != ordinal
					|| applicationSource.getSourceOrdinal() != ordinal
					|| stateSource.getSourceOrdinal() != ordinal
					|| transactionalSource.getSourceOrdinal() != ordinal
					|| collisionSource.getPackedRegionX()
						!= transactionalSource.getPackedRegionX()
					|| collisionSource.getPackedRegionY()
						!= transactionalSource.getPackedRegionY()
					|| collisionSource.getReplayPlacementCount()
						!= transactionalSource.getReplayPlacementCount()
					|| collisionSource.getAuthoredObjectFootprintCount()
						!= transactionalSource
							.getAuthoredObjectFootprintCount()
					|| collisionSource.getContributionTileReferenceCount()
						!= transactionalSource
							.getContributionTileReferenceCount()
					|| collisionSource.getRequiredRegionReferenceCount()
						!= transactionalSource
							.getRequiredRegionReferenceCount()
					|| collisionSource.getUniqueRequiredRegionCount()
						!= transactionalSource.getUniqueRequiredRegionCount()
					|| stateSource.getDisposableRegionConstructionCount()
						!= transactionalSource
							.getDisposableRegionConstructionCount()
					|| applicationSource.getCollisionApplicationCount()
						!= transactionalSource
							.getObjectCollisionTransactionCount()
					|| applicationSource.getHeldBoundaryCount()
						!= transactionalSource
							.getObjectCollisionTransactionBoundaryCount()
					|| applicationSource.getCollisionApplicationCount()
						!= transactionalSource
							.getCollisionRegistrationCount()
					|| collisionSource.getContributionTileReferenceCount()
						!= transactionalSource
							.getCollisionRegistrationContributionCount()
					|| collisionSource.getRequiredRegionReferenceCount()
						!= transactionalSource
							.getCollisionRegistrationRegionReferenceCount()
					|| applicationSource.getVerifiedRegionTileCount()
						!= transactionalSource.getVerifiedRegionTileCount()
					|| applicationSource
						.getBlockingSceneryContributionCount()
							!= transactionalSource
								.getBlockingSceneryContributionCount()
					|| applicationSource
						.getDynamicCollisionContributionCount()
							!= transactionalSource
								.getDynamicCollisionContributionCount()
					|| applicationSource
						.getDynamicProjectileContributionCount()
							!= transactionalSource
								.getDynamicProjectileContributionCount()
					|| !collisionSource.getTerrainFingerprintSha256().equals(
						transactionalSource.getTerrainFingerprintSha256())
					|| !collisionSource
						.getAuthoredReplayFingerprintSha256().equals(
							transactionalSource
								.getAuthoredReplayFingerprintSha256())
					|| !collisionSource
						.getDefinitionCaptureFingerprintSha256().equals(
							transactionalSource
								.getDefinitionCaptureFingerprintSha256())
					|| !collisionSource
						.getCollisionFootprintFingerprintSha256().equals(
							transactionalSource
								.getCollisionFootprintFingerprintSha256())
					|| !applicationSource
						.getAppliedCollisionFingerprintSha256().equals(
							transactionalSource
								.getAppliedCollisionFingerprintSha256())
					|| !stateSource.getFinalStateFingerprintSha256().equals(
						transactionalSource
							.getFinalStateFingerprintSha256())) {
					return false;
				}
			}
			return true;
		}

		public String getReason() { return reason; }
		public long getGeneration() { return generation; }
		public long getRequirementsObservedAtTick() {
			return requirementsObservedAtTick;
		}
		public int getSelectedSourceCount() { return selectedSourceCount; }
		public int getRequiredEventLinkCount() {
			return requiredEventLinkCount;
		}
		public int getRequiredOwnerCount() { return requiredOwnerCount; }
		public boolean isOwnerScopeEntered() { return ownerScopeEntered; }
		public boolean isSourceLifecycleInvoked() {
			return sourceLifecycleInvoked;
		}
		public int getAbsentSourceCount() { return absentSourceCount; }
		public int getReconstructedSourceCount() {
			return reconstructedSourceCount;
		}
		public boolean isPreservedConsumerInvoked() {
			return preservedConsumerInvoked;
		}
		public LayeredPackedRegionSourceAbsencePreflight
			getSourceAbsencePreflight() {
			return sourceAbsencePreflight;
		}
		public LayeredPackedRegionReloadRecipe getSourceReloadRecipe() {
			return sourceReloadRecipe;
		}
		public LayeredPackedRegionTerrainVerificationBatch
			getSourceTerrainVerification() {
			return sourceTerrainVerification;
		}
		public LayeredPackedRegionAuthoredCollisionVerificationBatch
			getSourceAuthoredCollisionVerification() {
			return sourceAuthoredCollisionVerification;
		}
		public
			LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
				getSourceAuthoredCollisionApplicationVerification() {
			return sourceAuthoredCollisionApplicationVerification;
		}
		public LayeredPackedRegionAuthoredSourceStateVerificationBatch
			getSourceAuthoredStateVerification() {
			return sourceAuthoredStateVerification;
		}
		public
			LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
				getSourceTransactionalAuthoredStateVerification() {
			return sourceTransactionalAuthoredStateVerification;
		}
	}

	/** Immutable JSON-facing copy of the runtime verification-only result. */
	public static final class PackedRegionEventRecoveryNoOpMetadata {
		private final String reason;
		private final String preparationReason;
		private final String lifecycleReason;
		private final long proposalGeneration;
		private final int inventoryEventCount;
		private final int recoveryCandidateCount;
		private final int proposalRelatedEventCount;
		private final int recoveryCompleteEventCount;
		private final int recoveryIncompleteEventCount;
		private final int incompleteOwnerPositionHintEventCount;
		private final int incompleteExactSpatialEventCount;
		private final Long firstIncompleteRegistrationSequence;
		private final String firstIncompleteOwnerKind;
		private final String firstIncompleteAttributionKind;
		private final String firstIncompleteRecoveryRequirement;
		private final boolean preflightComplete;
		private final int futureSnapshotCount;
		private final int runtimeVerificationCount;
		private final int mutationOperationCount;
		private final int terminalEventConsumptionCount;
		private final boolean reconstructionInvoked;
		private final boolean recoveryInvoked;
		private final boolean contractualReadiness;
		private final boolean freshInventoryRetryRequired;

		private PackedRegionEventRecoveryNoOpMetadata(
			final String reason,
			final String preparationReason,
			final String lifecycleReason,
			final long proposalGeneration,
			final int inventoryEventCount,
			final int recoveryCandidateCount,
			final int proposalRelatedEventCount,
			final int recoveryCompleteEventCount,
			final int recoveryIncompleteEventCount,
			final int incompleteOwnerPositionHintEventCount,
			final int incompleteExactSpatialEventCount,
			final Long firstIncompleteRegistrationSequence,
			final String firstIncompleteOwnerKind,
			final String firstIncompleteAttributionKind,
			final String firstIncompleteRecoveryRequirement,
			final boolean preflightComplete,
			final int futureSnapshotCount,
			final int runtimeVerificationCount,
			final int mutationOperationCount,
			final int terminalEventConsumptionCount,
			final boolean reconstructionInvoked,
			final boolean recoveryInvoked,
			final boolean contractualReadiness,
			final boolean freshInventoryRetryRequired) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.preparationReason = Objects.requireNonNull(
				preparationReason, "preparationReason");
			this.lifecycleReason = lifecycleReason;
			this.proposalGeneration = proposalGeneration;
			this.inventoryEventCount = inventoryEventCount;
			this.recoveryCandidateCount = recoveryCandidateCount;
			this.proposalRelatedEventCount = proposalRelatedEventCount;
			this.recoveryCompleteEventCount = recoveryCompleteEventCount;
			this.recoveryIncompleteEventCount = recoveryIncompleteEventCount;
			this.incompleteOwnerPositionHintEventCount =
				incompleteOwnerPositionHintEventCount;
			this.incompleteExactSpatialEventCount =
				incompleteExactSpatialEventCount;
			this.firstIncompleteRegistrationSequence =
				firstIncompleteRegistrationSequence;
			this.firstIncompleteOwnerKind = firstIncompleteOwnerKind;
			this.firstIncompleteAttributionKind =
				firstIncompleteAttributionKind;
			this.firstIncompleteRecoveryRequirement =
				firstIncompleteRecoveryRequirement;
			this.preflightComplete = preflightComplete;
			this.futureSnapshotCount = futureSnapshotCount;
			this.runtimeVerificationCount = runtimeVerificationCount;
			this.mutationOperationCount = mutationOperationCount;
			this.terminalEventConsumptionCount = terminalEventConsumptionCount;
			this.reconstructionInvoked = reconstructionInvoked;
			this.recoveryInvoked = recoveryInvoked;
			this.contractualReadiness = contractualReadiness;
			this.freshInventoryRetryRequired = freshInventoryRetryRequired;
			boolean readyReason =
				"NO_OP_VERIFICATION_READY".equals(reason);
			if (proposalGeneration < 0L || inventoryEventCount < 0
				|| recoveryCandidateCount < 0 || futureSnapshotCount < 0
				|| proposalRelatedEventCount < 0
				|| recoveryIncompleteEventCount < 0
				|| recoveryCandidateCount != recoveryCompleteEventCount
				|| proposalRelatedEventCount
					!= recoveryCompleteEventCount
						+ recoveryIncompleteEventCount
				|| incompleteOwnerPositionHintEventCount < 0
				|| incompleteExactSpatialEventCount < 0
				|| incompleteOwnerPositionHintEventCount
					+ incompleteExactSpatialEventCount
					> recoveryIncompleteEventCount
				|| preflightComplete != (recoveryIncompleteEventCount == 0)
				|| preflightComplete
					!= (firstIncompleteRegistrationSequence == null)
				|| preflightComplete != (firstIncompleteOwnerKind == null)
				|| preflightComplete
					!= (firstIncompleteAttributionKind == null)
				|| preflightComplete
					!= (firstIncompleteRecoveryRequirement == null)
				|| firstIncompleteRegistrationSequence != null
					&& firstIncompleteRegistrationSequence.longValue() <= 0L
				|| futureSnapshotCount > recoveryCandidateCount
				|| runtimeVerificationCount < 0
				|| runtimeVerificationCount > recoveryCandidateCount
				|| mutationOperationCount != 0
				|| terminalEventConsumptionCount != 0
				|| recoveryInvoked && !reconstructionInvoked
				|| readyReason != contractualReadiness) {
				throw new IllegalArgumentException(
					"No-op recovery metadata is inconsistent");
			}
		}

		public static PackedRegionEventRecoveryNoOpMetadata of(
			final String reason,
			final String preparationReason,
			final String lifecycleReason,
			final long proposalGeneration,
			final int inventoryEventCount,
			final int recoveryCandidateCount,
			final int proposalRelatedEventCount,
			final int recoveryCompleteEventCount,
			final int recoveryIncompleteEventCount,
			final int incompleteOwnerPositionHintEventCount,
			final int incompleteExactSpatialEventCount,
			final Long firstIncompleteRegistrationSequence,
			final String firstIncompleteOwnerKind,
			final String firstIncompleteAttributionKind,
			final String firstIncompleteRecoveryRequirement,
			final boolean preflightComplete,
			final int futureSnapshotCount,
			final int runtimeVerificationCount,
			final int mutationOperationCount,
			final int terminalEventConsumptionCount,
			final boolean reconstructionInvoked,
			final boolean recoveryInvoked,
			final boolean contractualReadiness,
			final boolean freshInventoryRetryRequired) {
			return new PackedRegionEventRecoveryNoOpMetadata(
				reason, preparationReason, lifecycleReason, proposalGeneration,
				inventoryEventCount, recoveryCandidateCount,
				proposalRelatedEventCount, recoveryCompleteEventCount,
				recoveryIncompleteEventCount,
				incompleteOwnerPositionHintEventCount,
				incompleteExactSpatialEventCount,
				firstIncompleteRegistrationSequence, firstIncompleteOwnerKind,
				firstIncompleteAttributionKind,
				firstIncompleteRecoveryRequirement, preflightComplete,
				futureSnapshotCount,
				runtimeVerificationCount, mutationOperationCount,
				terminalEventConsumptionCount, reconstructionInvoked,
				recoveryInvoked, contractualReadiness,
				freshInventoryRetryRequired);
		}

		public String getReason() { return reason; }
		public String getPreparationReason() { return preparationReason; }
		public String getLifecycleReason() { return lifecycleReason; }
		public long getProposalGeneration() { return proposalGeneration; }
		public int getInventoryEventCount() { return inventoryEventCount; }
		public int getRecoveryCandidateCount() {
			return recoveryCandidateCount;
		}
		public int getProposalRelatedEventCount() {
			return proposalRelatedEventCount;
		}
		public int getRecoveryCompleteEventCount() {
			return recoveryCompleteEventCount;
		}
		public int getRecoveryIncompleteEventCount() {
			return recoveryIncompleteEventCount;
		}
		public int getIncompleteOwnerPositionHintEventCount() {
			return incompleteOwnerPositionHintEventCount;
		}
		public int getIncompleteExactSpatialEventCount() {
			return incompleteExactSpatialEventCount;
		}
		public Long getFirstIncompleteRegistrationSequence() {
			return firstIncompleteRegistrationSequence;
		}
		public String getFirstIncompleteOwnerKind() {
			return firstIncompleteOwnerKind;
		}
		public String getFirstIncompleteAttributionKind() {
			return firstIncompleteAttributionKind;
		}
		public String getFirstIncompleteRecoveryRequirement() {
			return firstIncompleteRecoveryRequirement;
		}
		public boolean isPreflightComplete() { return preflightComplete; }
		public int getFutureSnapshotCount() { return futureSnapshotCount; }
		public int getRuntimeVerificationCount() {
			return runtimeVerificationCount;
		}
		public int getMutationOperationCount() {
			return mutationOperationCount;
		}
		public int getTerminalEventConsumptionCount() {
			return terminalEventConsumptionCount;
		}
		public boolean isReconstructionInvoked() {
			return reconstructionInvoked;
		}
		public boolean isRecoveryInvoked() { return recoveryInvoked; }
		public boolean isContractuallyReadyForFirstVisibility() {
			return contractualReadiness;
		}
		public boolean isFreshInventoryRetryRequired() {
			return freshInventoryRetryRequired;
		}
		public boolean isRegionMutationAllowed() { return false; }
		public boolean isOverdueConsumptionAllowed() { return false; }
		public boolean isRegionLoadingPerformed() { return false; }
		public boolean isRetryPerformed() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isVisibilityReleased() { return false; }
		public boolean isRuntimeHandleRetained() { return false; }
	}

	/** Captures detached scheduler affinity without event mutation or handles. */
	@FunctionalInterface
	public interface PackedRegionEventOwnershipSource {
		LayeredPackedRegionEventOwnershipInventory capture(
			LayeredPackedRegionRetirementRefinementProposal proposal,
			int maximumEvents,
			int maximumSpatialReferences);

		default LayeredPackedRegionEventTargetObservation captureTargets(
			final LayeredPackedRegionEventOwnershipInventory inventory,
			final int maximumTargetRecords) {
			return null;
		}

		default LayeredPackedRegionEventAtomicTargetRevalidation
			captureAtomicTargetRevalidation(
				final LayeredPackedRegionEventOwnershipInventory inventory,
				final int maximumTargetRecords) {
			return null;
		}

		default LayeredPackedRegionNpcOwnerEventContinuityAssessment
			captureNpcOwnerContinuity(
				final LayeredPackedRegionRetirementRefinementProposal proposal,
				final LayeredPackedRegionEventOwnershipInventory inventory,
				final int maximumCandidateSources,
				final int maximumNpcInstances,
				final int maximumRelevantNpcDetails,
				final int maximumEventDetails) {
			return null;
		}

		default LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
			captureNpcOwnerPreservationBoundary(
				final LayeredPackedRegionNpcOwnerPreservationRequirements
					requirements,
				final int maximumOwners) {
			return null;
		}

		default PackedRegionNpcOwnerPreservationNoOpMetadata
			captureNpcOwnerPreservationNoOp(
				final LayeredPackedRegionNpcOwnerPreservationRequirements
					requirements,
				final int maximumOwners) {
			return null;
		}

		default PackedRegionEventRecoveryNoOpMetadata captureRecoveryNoOp(
			final LayeredPackedRegionEventOwnershipInventory inventory,
			final int maximumCandidates) {
			return null;
		}
	}

	/** Immutable ownership aggregate; never a Region retention or eviction order. */
	public static final class InterestOwnershipMetadata {
		private final long ledgerVersion;
		private final long ownerSequence;
		private final boolean ownerOpen;
		private final int openOwnerCount;
		private final int referencedRegionCount;
		private final int ownedRegionCount;
		private final Integer minimumReferenceCount;
		private final Integer maximumReferenceCount;
		private final int enteredCount;
		private final int retainedCount;
		private final int exitedCount;
		private final int globallyAcquiredCount;
		private final int sharedAcquisitionCount;
		private final int globallyReleasedCount;
		private final int sharedReleaseCount;
		private final boolean noOp;
		private final List<InterestOwnershipTransitionMetadata> transitions;
		private final WorldRegionWindow previousOwnerWindow;
		private final WorldRegionWindow currentOwnerWindow;

		private InterestOwnershipMetadata(
			final long ledgerVersion,
			final long ownerSequence,
			final boolean ownerOpen,
			final int openOwnerCount,
			final int referencedRegionCount,
			final int ownedRegionCount,
			final Integer minimumReferenceCount,
			final Integer maximumReferenceCount,
			final int enteredCount,
			final int retainedCount,
			final int exitedCount,
			final int globallyAcquiredCount,
			final int sharedAcquisitionCount,
			final int globallyReleasedCount,
			final int sharedReleaseCount,
			final boolean noOp,
			final List<InterestOwnershipTransitionMetadata> transitions,
			final WorldRegionWindow previousOwnerWindow,
			final WorldRegionWindow currentOwnerWindow) {
			Objects.requireNonNull(transitions, "transitions");
			if (ledgerVersion < 0L || ownerSequence < 1L || openOwnerCount < 0
				|| referencedRegionCount < 0 || ownedRegionCount < 0
				|| enteredCount < 0 || retainedCount < 0 || exitedCount < 0
				|| globallyAcquiredCount < 0 || sharedAcquisitionCount < 0
				|| globallyReleasedCount < 0 || sharedReleaseCount < 0
				|| globallyAcquiredCount + sharedAcquisitionCount != enteredCount
				|| globallyReleasedCount + sharedReleaseCount != exitedCount
				|| transitions.size() != enteredCount + exitedCount
				|| noOp != (enteredCount == 0 && exitedCount == 0)
				|| ownerOpen != (currentOwnerWindow != null)
				|| ownerOpen && openOwnerCount < 1
				|| ownerOpen && ownedRegionCount < 1
				|| !ownerOpen && ownedRegionCount != 0
				|| ownedRegionCount > referencedRegionCount
				|| ownedRegionCount == 0
					&& (minimumReferenceCount != null || maximumReferenceCount != null)
				|| ownedRegionCount > 0
					&& (minimumReferenceCount == null || maximumReferenceCount == null)
				|| minimumReferenceCount != null
					&& (minimumReferenceCount.intValue() < 1
						|| maximumReferenceCount.intValue()
							< minimumReferenceCount.intValue())) {
				throw new IllegalArgumentException(
					"Invalid interest ownership aggregate counts");
			}
			Set<WorldRegionKey> transitionKeys =
				new java.util.HashSet<WorldRegionKey>();
			for (InterestOwnershipTransitionMetadata transition : transitions) {
				Objects.requireNonNull(transition, "transition");
				if (!transitionKeys.add(transition.getLogicalRegionKey())) {
					throw new IllegalArgumentException(
						"Interest ownership transition keys must be unique");
				}
			}
			if (ownerOpen
				&& ownedRegionCount != currentOwnerWindow.getRegionCount()) {
				throw new IllegalArgumentException(
					"Owned Region count differs from the current owner window");
			}
			this.ledgerVersion = ledgerVersion;
			this.ownerSequence = ownerSequence;
			this.ownerOpen = ownerOpen;
			this.openOwnerCount = openOwnerCount;
			this.referencedRegionCount = referencedRegionCount;
			this.ownedRegionCount = ownedRegionCount;
			this.minimumReferenceCount = minimumReferenceCount;
			this.maximumReferenceCount = maximumReferenceCount;
			this.enteredCount = enteredCount;
			this.retainedCount = retainedCount;
			this.exitedCount = exitedCount;
			this.globallyAcquiredCount = globallyAcquiredCount;
			this.sharedAcquisitionCount = sharedAcquisitionCount;
			this.globallyReleasedCount = globallyReleasedCount;
			this.sharedReleaseCount = sharedReleaseCount;
			this.noOp = noOp;
			this.transitions = Collections.unmodifiableList(
				new ArrayList<InterestOwnershipTransitionMetadata>(transitions));
			this.previousOwnerWindow = previousOwnerWindow;
			this.currentOwnerWindow = currentOwnerWindow;
		}

		public static InterestOwnershipMetadata fromOwnerSnapshot(
			final LayeredRegionInterestOwnershipLedger.OwnerSnapshot snapshot) {
			LayeredRegionInterestOwnershipLedger.OwnerSnapshot owner =
				Objects.requireNonNull(snapshot, "snapshot");
			if (owner.getWindow() == null || owner.getReferences().isEmpty()
				|| owner.getReferences().size() != owner.getKeys().size()) {
				throw new IllegalArgumentException(
					"Open owner snapshot must contain one complete window");
			}
			Integer minimum = null;
			Integer maximum = null;
			Set<WorldRegionKey> keys = new java.util.HashSet<WorldRegionKey>();
			for (LayeredRegionInterestOwnershipLedger.OwnerReference reference
				: owner.getReferences()) {
				if (!keys.add(reference.getLogicalRegionKey())
					|| !owner.getWindow().contains(reference.getLogicalRegionKey())) {
					throw new IllegalArgumentException(
						"Owner references differ from its logical window");
				}
				int count = reference.getReferenceCount();
				minimum = minimum == null || count < minimum.intValue()
					? Integer.valueOf(count) : minimum;
				maximum = maximum == null || count > maximum.intValue()
					? Integer.valueOf(count) : maximum;
			}
			return new InterestOwnershipMetadata(
				owner.getLedgerVersion(), owner.getOwnerSequence(), true,
				owner.getOpenOwnerCount(), owner.getReferencedRegionCount(),
				owner.getReferences().size(), minimum, maximum, 0, 0, 0,
				0, 0, 0, 0, true,
				Collections.<InterestOwnershipTransitionMetadata>emptyList(),
				null, owner.getWindow());
		}

		public static InterestOwnershipMetadata fromChange(
			final LayeredRegionInterestOwnershipLedger.Change ownershipChange) {
			LayeredRegionInterestOwnershipLedger.Change change =
				Objects.requireNonNull(ownershipChange, "ownershipChange");
			int entered = 0;
			int retained = 0;
			int exited = 0;
			int globallyAcquired = 0;
			int sharedAcquisition = 0;
			int globallyReleased = 0;
			int sharedRelease = 0;
			Integer minimum = null;
			Integer maximum = null;
			List<InterestOwnershipTransitionMetadata> transitions =
				new ArrayList<InterestOwnershipTransitionMetadata>();
			for (LayeredRegionInterestOwnershipLedger.Entry entry
				: change.getEntries()) {
				switch (entry.getInterestState()) {
					case ENTERED:
						entered++;
						globallyAcquired += entry.isGloballyAcquired() ? 1 : 0;
						sharedAcquisition += entry.isSharedAcquisition() ? 1 : 0;
						transitions.add(
							InterestOwnershipTransitionMetadata.fromEntry(entry));
						break;
					case RETAINED:
						retained++;
						break;
					case EXITED:
						exited++;
						globallyReleased += entry.isGloballyReleased() ? 1 : 0;
						sharedRelease += entry.isSharedRelease() ? 1 : 0;
						transitions.add(
							InterestOwnershipTransitionMetadata.fromEntry(entry));
						break;
					default:
						throw new IllegalStateException("Unknown interest state");
				}
				if (entry.getInterestState()
					!= LayeredRegionInterestOwnershipLedger.InterestState.EXITED) {
					int count = entry.getCurrentReferenceCount();
					minimum = minimum == null || count < minimum.intValue()
						? Integer.valueOf(count) : minimum;
					maximum = maximum == null || count > maximum.intValue()
						? Integer.valueOf(count) : maximum;
				}
			}
			return new InterestOwnershipMetadata(
				change.getLedgerVersion(), change.getOwnerSequence(),
				!change.isOwnerClosed(), change.getOpenOwnerCount(),
				change.getReferencedRegionCount(), entered + retained,
				minimum, maximum, entered, retained, exited, globallyAcquired,
				sharedAcquisition, globallyReleased, sharedRelease,
				change.isNoOp(), transitions, change.getPreviousWindow(),
				change.getCurrentWindow());
		}

		private static InterestOwnershipMetadata syntheticCurrent(
			final WorldRegionWindow currentWindow,
			final int maximumRegionsPerWindow) {
			WorldRegionWindow window = Objects.requireNonNull(
				currentWindow, "currentWindow");
			int count = WorldRegionInterestDelta.materializeKeys(
				window, maximumRegionsPerWindow).size();
			return new InterestOwnershipMetadata(
				0L, 1L, true, 1, count, count, Integer.valueOf(1),
				Integer.valueOf(1), 0, 0, 0, 0, 0, 0, 0, true,
				Collections.<InterestOwnershipTransitionMetadata>emptyList(),
				null, window);
		}

		private void requireMatches(
			final String eventType,
			final WorldRegionWindow previousEventWindow,
			final WorldRegionWindow currentEventWindow,
			final boolean fromChange) {
			WorldRegionWindow current = Objects.requireNonNull(
				currentEventWindow, "currentEventWindow");
			if (!fromChange) {
				if (!ownerOpen || !current.equals(currentOwnerWindow)) {
					throw new IllegalStateException(
						"Current interest owner differs from the observed window");
				}
				return;
			}
			if ("logout".equals(eventType)) {
				if (ownerOpen || currentOwnerWindow != null
					|| !current.equals(previousOwnerWindow)) {
					throw new IllegalStateException(
						"Logout ownership change differs from the observed window");
				}
			} else if ("login".equals(eventType)) {
				if (!ownerOpen || previousOwnerWindow != null
					|| !current.equals(currentOwnerWindow)) {
					throw new IllegalStateException(
						"Login ownership change differs from the observed window");
				}
			} else if (previousEventWindow == null
				|| !ownerOpen
				|| !previousEventWindow.equals(previousOwnerWindow)
				|| !current.equals(currentOwnerWindow)) {
				throw new IllegalStateException(
					"Movement ownership change differs from observed windows");
			}
		}

		public long getLedgerVersion() {
			return ledgerVersion;
		}

		public long getOwnerSequence() {
			return ownerSequence;
		}

		public boolean isOwnerOpen() {
			return ownerOpen;
		}

		public int getOpenOwnerCount() {
			return openOwnerCount;
		}

		public int getReferencedRegionCount() {
			return referencedRegionCount;
		}

		public int getOwnedRegionCount() {
			return ownedRegionCount;
		}

		public Integer getMinimumReferenceCount() {
			return minimumReferenceCount;
		}

		public Integer getMaximumReferenceCount() {
			return maximumReferenceCount;
		}

		public int getEnteredCount() {
			return enteredCount;
		}

		public int getRetainedCount() {
			return retainedCount;
		}

		public int getExitedCount() {
			return exitedCount;
		}

		public int getGloballyAcquiredCount() {
			return globallyAcquiredCount;
		}

		public int getSharedAcquisitionCount() {
			return sharedAcquisitionCount;
		}

		public int getGloballyReleasedCount() {
			return globallyReleasedCount;
		}

		public int getSharedReleaseCount() {
			return sharedReleaseCount;
		}

		public boolean isNoOp() {
			return noOp;
		}

		public List<InterestOwnershipTransitionMetadata> getTransitions() {
			return transitions;
		}
	}

	/** One bounded entered/exited Region reference-count transition. */
	public static final class InterestOwnershipTransitionMetadata {
		private final WorldRegionKey logicalRegionKey;
		private final LayeredRegionInterestOwnershipLedger.InterestState interestState;
		private final int previousReferenceCount;
		private final int currentReferenceCount;

		private InterestOwnershipTransitionMetadata(
			final WorldRegionKey logicalRegionKey,
			final LayeredRegionInterestOwnershipLedger.InterestState interestState,
			final int previousReferenceCount,
			final int currentReferenceCount) {
			this.logicalRegionKey = Objects.requireNonNull(
				logicalRegionKey, "logicalRegionKey");
			this.interestState = Objects.requireNonNull(
				interestState, "interestState");
			if (interestState
				== LayeredRegionInterestOwnershipLedger.InterestState.RETAINED
				|| previousReferenceCount < 0 || currentReferenceCount < 0
				|| interestState
					== LayeredRegionInterestOwnershipLedger.InterestState.ENTERED
					&& currentReferenceCount != previousReferenceCount + 1
				|| interestState
					== LayeredRegionInterestOwnershipLedger.InterestState.EXITED
					&& currentReferenceCount != previousReferenceCount - 1) {
				throw new IllegalArgumentException(
					"Invalid interest ownership transition");
			}
			this.previousReferenceCount = previousReferenceCount;
			this.currentReferenceCount = currentReferenceCount;
		}

		private static InterestOwnershipTransitionMetadata fromEntry(
			final LayeredRegionInterestOwnershipLedger.Entry entry) {
			return new InterestOwnershipTransitionMetadata(
				entry.getLogicalRegionKey(), entry.getInterestState(),
				entry.getPreviousReferenceCount(), entry.getCurrentReferenceCount());
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public LayeredRegionInterestOwnershipLedger.InterestState getInterestState() {
			return interestState;
		}

		public int getPreviousReferenceCount() {
			return previousReferenceCount;
		}

		public int getCurrentReferenceCount() {
			return currentReferenceCount;
		}
	}

	/** Immutable bounded cooldown aggregate; never an eviction queue. */
	public static final class RegionRetirementMetadata {
		private final int transitionRegionCount;
		private final int trackedCandidateCount;
		private final long droppedCandidateCount;
		private final int pinnedCount;
		private final int coolingDownCount;
		private final int retirementEligibleCount;
		private final int notResidentCount;
		private final int unsupportedCount;
		private final int untrackedCount;
		private final List<RegionRetirementEntryMetadata> entries;

		private RegionRetirementMetadata(
			final int transitionRegionCount,
			final int trackedCandidateCount,
			final long droppedCandidateCount,
			final int pinnedCount,
			final int coolingDownCount,
			final int retirementEligibleCount,
			final int notResidentCount,
			final int unsupportedCount,
			final int untrackedCount,
			final List<RegionRetirementEntryMetadata> entries) {
			if (transitionRegionCount < 0 || trackedCandidateCount < 0
				|| droppedCandidateCount < 0L || pinnedCount < 0
				|| coolingDownCount < 0 || retirementEligibleCount < 0
				|| notResidentCount < 0 || unsupportedCount < 0
				|| untrackedCount < 0
				|| pinnedCount + coolingDownCount + retirementEligibleCount
					+ notResidentCount + unsupportedCount + untrackedCount
					!= entries.size()) {
				throw new IllegalArgumentException(
					"Invalid Region retirement aggregate counts");
			}
			this.transitionRegionCount = transitionRegionCount;
			this.trackedCandidateCount = trackedCandidateCount;
			this.droppedCandidateCount = droppedCandidateCount;
			this.pinnedCount = pinnedCount;
			this.coolingDownCount = coolingDownCount;
			this.retirementEligibleCount = retirementEligibleCount;
			this.notResidentCount = notResidentCount;
			this.unsupportedCount = unsupportedCount;
			this.untrackedCount = untrackedCount;
			this.entries = Collections.unmodifiableList(
				new ArrayList<RegionRetirementEntryMetadata>(entries));
		}

		public static RegionRetirementMetadata fromSnapshots(
			final List<LayeredRegionRetirementEligibilityLedger.Snapshot> snapshots,
			final List<WorldRegionKey> transitionKeys,
			final List<WorldRegionKey> trackedCandidateKeys,
			final long droppedCandidateCount) {
			Objects.requireNonNull(snapshots, "snapshots");
			LinkedHashSet<WorldRegionKey> transitions = checkedRegionKeys(
				transitionKeys, "transitionKeys");
			LinkedHashSet<WorldRegionKey> tracked = checkedRegionKeys(
				trackedCandidateKeys, "trackedCandidateKeys");
			LinkedHashSet<WorldRegionKey> expected =
				new LinkedHashSet<WorldRegionKey>(transitions);
			expected.addAll(tracked);
			if (snapshots.size() != expected.size()) {
				throw new IllegalArgumentException(
					"Retirement snapshots differ from requested Region keys");
			}

			List<RegionRetirementEntryMetadata> entries =
				new ArrayList<RegionRetirementEntryMetadata>(snapshots.size());
			int pinned = 0;
			int cooling = 0;
			int eligible = 0;
			int notResident = 0;
			int unsupported = 0;
			int untracked = 0;
			Iterator<WorldRegionKey> expectedKeys = expected.iterator();
			for (LayeredRegionRetirementEligibilityLedger.Snapshot snapshot
				: snapshots) {
				LayeredRegionRetirementEligibilityLedger.Snapshot checked =
					Objects.requireNonNull(snapshot, "snapshot");
				WorldRegionKey expectedKey = expectedKeys.next();
				if (!expectedKey.equals(checked.getLogicalRegionKey())) {
					throw new IllegalArgumentException(
						"Retirement snapshots do not preserve requested order");
				}
				RegionRetirementEntryMetadata entry =
					RegionRetirementEntryMetadata.fromSnapshot(
						checked, transitions.contains(expectedKey),
						tracked.contains(expectedKey));
				entries.add(entry);
				switch (entry.getRetirementState()) {
					case PINNED:
						pinned++;
						break;
					case COOLING_DOWN:
						cooling++;
						break;
					case RETIREMENT_ELIGIBLE:
						eligible++;
						break;
					case NOT_RESIDENT:
						notResident++;
						break;
					case UNSUPPORTED:
						unsupported++;
						break;
					case UNTRACKED:
						untracked++;
						break;
					default:
						throw new IllegalStateException(
							"Unknown Region retirement state");
				}
			}
			return new RegionRetirementMetadata(
				transitions.size(), tracked.size(), droppedCandidateCount,
				pinned, cooling, eligible, notResident, unsupported, untracked,
				entries);
		}

		private static RegionRetirementMetadata empty(
			final long droppedCandidateCount) {
			return fromSnapshots(
				Collections.<LayeredRegionRetirementEligibilityLedger.Snapshot>emptyList(),
				Collections.<WorldRegionKey>emptyList(),
				Collections.<WorldRegionKey>emptyList(), droppedCandidateCount);
		}

		private static LinkedHashSet<WorldRegionKey> checkedRegionKeys(
			final List<WorldRegionKey> keys,
			final String label) {
			Objects.requireNonNull(keys, label);
			LinkedHashSet<WorldRegionKey> unique =
				new LinkedHashSet<WorldRegionKey>(keys);
			if (unique.size() != keys.size() || unique.contains(null)) {
				throw new IllegalArgumentException(
					label + " must contain unique non-null Region keys");
			}
			return unique;
		}

		private void requireMatches(
			final List<WorldRegionKey> transitionKeys,
			final List<WorldRegionKey> trackedCandidateKeys,
			final long expectedDroppedCandidateCount) {
			LinkedHashSet<WorldRegionKey> transitions = checkedRegionKeys(
				transitionKeys, "transitionKeys");
			LinkedHashSet<WorldRegionKey> tracked = checkedRegionKeys(
				trackedCandidateKeys, "trackedCandidateKeys");
			if (transitionRegionCount != transitions.size()
				|| trackedCandidateCount != tracked.size()
				|| droppedCandidateCount != expectedDroppedCandidateCount) {
				throw new IllegalStateException(
					"Region retirement metadata differs from observer candidates");
			}
			LinkedHashSet<WorldRegionKey> observed =
				new LinkedHashSet<WorldRegionKey>();
			for (RegionRetirementEntryMetadata entry : entries) {
				WorldRegionKey key = entry.getLogicalRegionKey();
				if (!observed.add(key)
					|| entry.isTransition() != transitions.contains(key)
					|| entry.isTrackedCandidate() != tracked.contains(key)) {
					throw new IllegalStateException(
						"Region retirement entry flags differ from observer candidates");
				}
			}
			LinkedHashSet<WorldRegionKey> expected =
				new LinkedHashSet<WorldRegionKey>(transitions);
			expected.addAll(tracked);
			if (!observed.equals(expected)) {
				throw new IllegalStateException(
					"Region retirement entries omit requested candidates");
			}
		}

		public int getTransitionRegionCount() {
			return transitionRegionCount;
		}

		public int getTrackedCandidateCount() {
			return trackedCandidateCount;
		}

		public long getDroppedCandidateCount() {
			return droppedCandidateCount;
		}

		public int getPinnedCount() {
			return pinnedCount;
		}

		public int getCoolingDownCount() {
			return coolingDownCount;
		}

		public int getRetirementEligibleCount() {
			return retirementEligibleCount;
		}

		public int getNotResidentCount() {
			return notResidentCount;
		}

		public int getUnsupportedCount() {
			return unsupportedCount;
		}

		public int getUntrackedCount() {
			return untrackedCount;
		}

		public List<RegionRetirementEntryMetadata> getEntries() {
			return entries;
		}
	}

	/** Immutable bounded arbiter evidence; never an eviction or unload order. */
	public static final class RegionRetirementDecisionMetadata {
		private final long droppedCandidateCount;
		private final int eligibleCount;
		private final List<RegionRetirementDecisionEntryMetadata> entries;
		private final LayeredPackedRegionRetirementReadiness
			packedSourceReadiness;

		private RegionRetirementDecisionMetadata(
			final long droppedCandidateCount,
			final int eligibleCount,
			final List<RegionRetirementDecisionEntryMetadata> entries,
			final LayeredPackedRegionRetirementReadiness packedSourceReadiness) {
			Objects.requireNonNull(entries, "entries");
			this.packedSourceReadiness = Objects.requireNonNull(
				packedSourceReadiness, "packedSourceReadiness");
			if (droppedCandidateCount < 0L || eligibleCount < 0
				|| eligibleCount > entries.size()
				|| packedSourceReadiness.getLogicalDecisionCount()
					!= entries.size()) {
				throw new IllegalArgumentException(
					"Invalid Region retirement decision aggregate counts");
			}
			Set<WorldRegionKey> unique = new java.util.HashSet<WorldRegionKey>();
			for (RegionRetirementDecisionEntryMetadata entry : entries) {
				if (entry == null || !unique.add(entry.getLogicalRegionKey())) {
					throw new IllegalArgumentException(
						"Region retirement decision keys must be unique");
				}
			}
			this.droppedCandidateCount = droppedCandidateCount;
			this.eligibleCount = eligibleCount;
			this.entries = Collections.unmodifiableList(
				new ArrayList<RegionRetirementDecisionEntryMetadata>(entries));
		}

		public static RegionRetirementDecisionMetadata fromDecisions(
			final List<LayeredRegionRetirementDecisionArbiter.Decision> decisions,
			final long droppedCandidateCount) {
			Objects.requireNonNull(decisions, "decisions");
			List<RegionRetirementDecisionEntryMetadata> entries =
				new ArrayList<RegionRetirementDecisionEntryMetadata>(
					decisions.size());
			int eligible = 0;
			for (LayeredRegionRetirementDecisionArbiter.Decision decision
				: decisions) {
				RegionRetirementDecisionEntryMetadata entry =
					RegionRetirementDecisionEntryMetadata.fromDecision(decision);
				entries.add(entry);
				if (entry.isEligible()) {
					eligible++;
				}
			}
			return new RegionRetirementDecisionMetadata(
				droppedCandidateCount, eligible, entries,
				LayeredPackedRegionRetirementReadiness.fromDecisions(
					decisions, MAX_TRACE_RETIREMENT_CANDIDATES,
					MAX_TRACE_PACKED_RETIREMENT_SOURCES));
		}

		private void requireMatches(
			final List<LayeredRegionRetirementEligibilityLedger.Snapshot> candidates,
			final long expectedDroppedCandidateCount) {
			Objects.requireNonNull(candidates, "candidates");
			if (droppedCandidateCount != expectedDroppedCandidateCount
				|| entries.size() != candidates.size()) {
				throw new IllegalStateException(
					"Retirement decisions differ from observer candidates");
			}
			for (int index = 0; index < candidates.size(); index++) {
				LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
					Objects.requireNonNull(candidates.get(index), "candidate");
				RegionRetirementDecisionEntryMetadata entry = entries.get(index);
				if (!candidate.getLogicalRegionKey().equals(
						entry.getLogicalRegionKey())
					|| candidate.getOwnershipVersion()
						!= entry.getCandidateOwnershipVersion()
					|| candidate.getResidencyMirrorVersion()
						!= entry.getCandidateResidencyMirrorVersion()
					|| !Objects.equals(candidate.getReleasedAtOwnershipVersion(),
						entry.getCandidateReleasedAtOwnershipVersion())
					|| !Objects.equals(candidate.getReleasedAtTick(),
						entry.getCandidateReleasedAtTick())
					|| !Objects.equals(candidate.getEligibleAtTick(),
						entry.getCandidateEligibleAtTick())) {
					throw new IllegalStateException(
						"Retirement decision does not identify its candidate");
				}
			}
		}

		public int getCandidateCount() {
			return entries.size();
		}

		public long getDroppedCandidateCount() {
			return droppedCandidateCount;
		}

		public int getEligibleCount() {
			return eligibleCount;
		}

		public int getRefusedCount() {
			return entries.size() - eligibleCount;
		}

		public List<RegionRetirementDecisionEntryMetadata> getEntries() {
			return entries;
		}

		public LayeredPackedRegionRetirementReadiness
			getPackedSourceReadiness() {
			return packedSourceReadiness;
		}
	}

	/** One AI-readable decision over an immutable retirement candidate. */
	public static final class RegionRetirementDecisionEntryMetadata {
		private final WorldRegionKey logicalRegionKey;
		private final long candidateOwnershipVersion;
		private final long currentOwnershipVersion;
		private final long candidateResidencyMirrorVersion;
		private final long currentResidencyMirrorVersion;
		private final long observedAtTick;
		private final Long candidateReleasedAtOwnershipVersion;
		private final Long currentReleasedAtOwnershipVersion;
		private final Long candidateReleasedAtTick;
		private final Long currentReleasedAtTick;
		private final Long candidateEligibleAtTick;
		private final Long currentEligibleAtTick;
		private final LayeredRegionRetirementEligibilityLedger.RetirementState
			currentRetirementState;
		private final LayeredRegionRetirementDecisionArbiter.DecisionState
			decisionState;

		private RegionRetirementDecisionEntryMetadata(
			final LayeredRegionRetirementDecisionArbiter.Decision decision) {
			LayeredRegionRetirementDecisionArbiter.Decision checked =
				Objects.requireNonNull(decision, "decision");
			this.logicalRegionKey = checked.getLogicalRegionKey();
			this.candidateOwnershipVersion =
				checked.getCandidateOwnershipVersion();
			this.currentOwnershipVersion = checked.getCurrentOwnershipVersion();
			this.candidateResidencyMirrorVersion =
				checked.getCandidateResidencyMirrorVersion();
			this.currentResidencyMirrorVersion =
				checked.getCurrentResidencyMirrorVersion();
			this.observedAtTick = checked.getObservedAtTick();
			this.candidateReleasedAtOwnershipVersion =
				checked.getCandidateReleasedAtOwnershipVersion();
			this.currentReleasedAtOwnershipVersion =
				checked.getCurrentReleasedAtOwnershipVersion();
			this.candidateReleasedAtTick = checked.getCandidateReleasedAtTick();
			this.currentReleasedAtTick = checked.getCurrentReleasedAtTick();
			this.candidateEligibleAtTick = checked.getCandidateEligibleAtTick();
			this.currentEligibleAtTick = checked.getCurrentEligibleAtTick();
			this.currentRetirementState = checked.getCurrentRetirementState();
			this.decisionState = checked.getDecisionState();
			if (candidateReleasedAtOwnershipVersion == null
				|| candidateReleasedAtTick == null
				|| candidateEligibleAtTick == null) {
				throw new IllegalArgumentException(
					"Invalid Region retirement decision evidence");
			}
		}

		private static RegionRetirementDecisionEntryMetadata fromDecision(
			final LayeredRegionRetirementDecisionArbiter.Decision decision) {
			return new RegionRetirementDecisionEntryMetadata(decision);
		}

		public WorldRegionKey getLogicalRegionKey() { return logicalRegionKey; }
		public long getCandidateOwnershipVersion() {
			return candidateOwnershipVersion;
		}
		public long getCurrentOwnershipVersion() { return currentOwnershipVersion; }
		public long getCandidateResidencyMirrorVersion() {
			return candidateResidencyMirrorVersion;
		}
		public long getCurrentResidencyMirrorVersion() {
			return currentResidencyMirrorVersion;
		}
		public long getObservedAtTick() { return observedAtTick; }
		public Long getCandidateReleasedAtOwnershipVersion() {
			return candidateReleasedAtOwnershipVersion;
		}
		public Long getCurrentReleasedAtOwnershipVersion() {
			return currentReleasedAtOwnershipVersion;
		}
		public Long getCandidateReleasedAtTick() {
			return candidateReleasedAtTick;
		}
		public Long getCurrentReleasedAtTick() { return currentReleasedAtTick; }
		public Long getCandidateEligibleAtTick() {
			return candidateEligibleAtTick;
		}
		public Long getCurrentEligibleAtTick() { return currentEligibleAtTick; }
		public LayeredRegionRetirementEligibilityLedger.RetirementState
			getCurrentRetirementState() {
			return currentRetirementState;
		}
		public LayeredRegionRetirementDecisionArbiter.DecisionState
			getDecisionState() {
			return decisionState;
		}
		public boolean isEligible() {
			return decisionState
				== LayeredRegionRetirementDecisionArbiter.DecisionState.ELIGIBLE;
		}
	}

	/** One immutable diagnostic view of a dormant retirement projection. */
	public static final class RegionRetirementEntryMetadata {
		private final LayeredRegionRetirementEligibilityLedger.Snapshot snapshot;
		private final WorldRegionKey logicalRegionKey;
		private final boolean transition;
		private final boolean trackedCandidate;
		private final long ownershipVersion;
		private final long residencyMirrorVersion;
		private final long observedAtTick;
		private final long minimumCooldownTicks;
		private final int referenceCount;
		private final boolean legacySupported;
		private final int sourceCount;
		private final int residentSourceCount;
		private final LayeredRegionRetirementEligibilityLedger.RetirementState
			retirementState;
		private final Long releasedAtOwnershipVersion;
		private final Long releasedAtTick;
		private final Long eligibleAtTick;
		private final long remainingCooldownTicks;

		private RegionRetirementEntryMetadata(
			final LayeredRegionRetirementEligibilityLedger.Snapshot snapshot,
			final boolean transition,
			final boolean trackedCandidate) {
			LayeredRegionRetirementEligibilityLedger.Snapshot checked =
				Objects.requireNonNull(snapshot, "snapshot");
			if (!transition && !trackedCandidate) {
				throw new IllegalArgumentException(
					"Retirement entry must have an observation reason");
			}
			this.snapshot = checked;
			this.logicalRegionKey = checked.getLogicalRegionKey();
			this.transition = transition;
			this.trackedCandidate = trackedCandidate;
			this.ownershipVersion = checked.getOwnershipVersion();
			this.residencyMirrorVersion = checked.getResidencyMirrorVersion();
			this.observedAtTick = checked.getObservedAtTick();
			this.minimumCooldownTicks = checked.getMinimumCooldownTicks();
			this.referenceCount = checked.getReferenceCount();
			this.legacySupported = checked.isLegacySupported();
			this.sourceCount = checked.getSourceCount();
			this.residentSourceCount = checked.getResidentSourceCount();
			this.retirementState = checked.getRetirementState();
			this.releasedAtOwnershipVersion =
				checked.getReleasedAtOwnershipVersion();
			this.releasedAtTick = checked.getReleasedAtTick();
			this.eligibleAtTick = checked.getEligibleAtTick();
			this.remainingCooldownTicks = checked.getRemainingCooldownTicks();
		}

		private static RegionRetirementEntryMetadata fromSnapshot(
			final LayeredRegionRetirementEligibilityLedger.Snapshot snapshot,
			final boolean transition,
			final boolean trackedCandidate) {
			return new RegionRetirementEntryMetadata(
				snapshot, transition, trackedCandidate);
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public boolean isTransition() {
			return transition;
		}

		public boolean isTrackedCandidate() {
			return trackedCandidate;
		}

		public long getOwnershipVersion() {
			return ownershipVersion;
		}

		public long getResidencyMirrorVersion() {
			return residencyMirrorVersion;
		}

		public long getObservedAtTick() {
			return observedAtTick;
		}

		public long getMinimumCooldownTicks() {
			return minimumCooldownTicks;
		}

		public int getReferenceCount() {
			return referenceCount;
		}

		public boolean isLegacySupported() {
			return legacySupported;
		}

		public int getSourceCount() {
			return sourceCount;
		}

		public int getResidentSourceCount() {
			return residentSourceCount;
		}

		public LayeredRegionRetirementEligibilityLedger.RetirementState
			getRetirementState() {
			return retirementState;
		}

		public Long getReleasedAtOwnershipVersion() {
			return releasedAtOwnershipVersion;
		}

		public Long getReleasedAtTick() {
			return releasedAtTick;
		}

		public Long getEligibleAtTick() {
			return eligibleAtTick;
		}

		public long getRemainingCooldownTicks() {
			return remainingCooldownTicks;
		}

		public boolean isRetirementEligible() {
			return retirementState
				== LayeredRegionRetirementEligibilityLedger.RetirementState
					.RETIREMENT_ELIGIBLE;
		}

		private LayeredRegionRetirementEligibilityLedger.Snapshot getSnapshot() {
			return snapshot;
		}
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

	private static final class RetirementRefinementReassessmentMetadata {
		final LayeredPackedRegionRetirementRefinementProposal previousProposal;
		final LayeredPackedRegionRetirementRefinementReassessment reassessment;
		final boolean pendingRetained;

		private RetirementRefinementReassessmentMetadata(
			final LayeredPackedRegionRetirementRefinementProposal previousProposal,
			final LayeredPackedRegionRetirementRefinementReassessment reassessment,
			final boolean pendingRetained) {
			this.previousProposal = Objects.requireNonNull(
				previousProposal, "previousProposal");
			this.reassessment = reassessment;
			this.pendingRetained = pendingRetained;
		}

		static RetirementRefinementReassessmentMetadata deferred(
			final LayeredPackedRegionRetirementRefinementProposal previousProposal) {
			return new RetirementRefinementReassessmentMetadata(
				previousProposal, null, true);
		}

		static RetirementRefinementReassessmentMetadata observed(
			final LayeredPackedRegionRetirementRefinementProposal previousProposal,
			final LayeredPackedRegionRetirementRefinementReassessment reassessment,
			final boolean pendingRetained) {
			return new RetirementRefinementReassessmentMetadata(
				previousProposal,
				Objects.requireNonNull(reassessment, "reassessment"),
				pendingRetained);
		}

		boolean isDeferred() { return reassessment == null; }

		int pendingAfterCandidateSourceCount() {
			if (isDeferred()) {
				return previousProposal.getCandidateSourceCount();
			}
			return pendingRetained
				? reassessment.getNextCandidateSourceCount() : 0;
		}

		String status() {
			if (isDeferred()) {
				return "DEFERRED_NOT_NEWER";
			}
			if (reassessment.isFurtherRefinementRequired()) {
				return reassessment.hasNonExpandableHardBlockers()
					? "EXPANDED_AND_HARD_BLOCKED" : "EXPANDED";
			}
			return reassessment.hasNonExpandableHardBlockers()
				? "HARD_BLOCKED" : "STABLE";
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
		InterestOwnershipSource interestOwnershipSource;
		RegionRetirementSource regionRetirementSource;
		RegionRetirementDecisionSource regionRetirementDecisionSource;
		PackedRegionRetirementSafetySource packedRegionRetirementSafetySource;
		PackedRegionAuthoredConstructionSource
			packedRegionAuthoredConstructionSource;
		PackedRegionAuthoredProvenanceSource
			packedRegionAuthoredProvenanceSource;
		PackedRegionAuthoredReconstructionSource
			packedRegionAuthoredReconstructionSource;
		PackedRegionAuthoredReconstructionCohortSource
			packedRegionAuthoredReconstructionCohortSource;
		PackedRegionAuthoredReconstructionCohortAttributionSource
			packedRegionAuthoredReconstructionCohortAttributionSource;
		PackedRegionAuthoredReconstructionTopologySource
			packedRegionAuthoredReconstructionTopologySource;
		PackedRegionAuthoredReconstructionDependencySemanticsSource
			packedRegionAuthoredReconstructionDependencySemanticsSource;
		PackedRegionActiveNpcResidencySource
			packedRegionActiveNpcResidencySource;
		PackedRegionRetirementRefinementReassessmentSource
			packedRegionRetirementRefinementReassessmentSource;
		PackedRegionPreservationBurdenSource
			packedRegionPreservationBurdenSource;
		PackedRegionDynamicObjectPreservationSource
			packedRegionDynamicObjectPreservationSource;
		PackedRegionEventOwnershipSource packedRegionEventOwnershipSource;
		LayeredPackedRegionRetirementRefinementProposal
			pendingPackedRegionRetirementRefinement;
		final List<WorldLocation> recentTraversal =
			new ArrayList<WorldLocation>(MAX_TRACE_TRAVERSAL_STEPS + 1);
		final LinkedHashSet<WorldRegionKey> retirementCandidates =
			new LinkedHashSet<WorldRegionKey>();
		final LinkedHashMap<WorldRegionKey,
			LayeredRegionRetirementEligibilityLedger.Snapshot>
				retirementDecisionCandidates = new LinkedHashMap<WorldRegionKey,
					LayeredRegionRetirementEligibilityLedger.Snapshot>();
		long retirementCandidateDroppedCount;
		long retirementDecisionCandidateDroppedCount;
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
			RegionResidencySource regionResidencySource,
			InterestOwnershipSource interestOwnershipSource,
			RegionRetirementSource regionRetirementSource,
			RegionRetirementDecisionSource regionRetirementDecisionSource,
			PackedRegionRetirementSafetySource
				packedRegionRetirementSafetySource,
			PackedRegionAuthoredConstructionSource
				packedRegionAuthoredConstructionSource,
			PackedRegionAuthoredProvenanceSource
				packedRegionAuthoredProvenanceSource,
			PackedRegionAuthoredReconstructionSource
				packedRegionAuthoredReconstructionSource,
			PackedRegionAuthoredReconstructionCohortSource
				packedRegionAuthoredReconstructionCohortSource,
			PackedRegionAuthoredReconstructionCohortAttributionSource
				packedRegionAuthoredReconstructionCohortAttributionSource,
			PackedRegionAuthoredReconstructionTopologySource
				packedRegionAuthoredReconstructionTopologySource,
			PackedRegionAuthoredReconstructionDependencySemanticsSource
				packedRegionAuthoredReconstructionDependencySemanticsSource,
			PackedRegionActiveNpcResidencySource
				packedRegionActiveNpcResidencySource,
			PackedRegionRetirementRefinementReassessmentSource
				packedRegionRetirementRefinementReassessmentSource,
			PackedRegionPreservationBurdenSource
				packedRegionPreservationBurdenSource,
			PackedRegionDynamicObjectPreservationSource
				packedRegionDynamicObjectPreservationSource,
			PackedRegionEventOwnershipSource packedRegionEventOwnershipSource) {
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
			this.interestOwnershipSource = interestOwnershipSource;
			this.regionRetirementSource = regionRetirementSource;
			this.regionRetirementDecisionSource = regionRetirementDecisionSource;
			this.packedRegionRetirementSafetySource =
				packedRegionRetirementSafetySource;
			this.packedRegionAuthoredConstructionSource =
				packedRegionAuthoredConstructionSource;
			this.packedRegionAuthoredProvenanceSource =
				packedRegionAuthoredProvenanceSource;
			this.packedRegionAuthoredReconstructionSource =
				packedRegionAuthoredReconstructionSource;
			this.packedRegionAuthoredReconstructionCohortSource =
				packedRegionAuthoredReconstructionCohortSource;
			this.packedRegionAuthoredReconstructionCohortAttributionSource =
				packedRegionAuthoredReconstructionCohortAttributionSource;
			this.packedRegionAuthoredReconstructionTopologySource =
				packedRegionAuthoredReconstructionTopologySource;
			this.packedRegionAuthoredReconstructionDependencySemanticsSource =
				packedRegionAuthoredReconstructionDependencySemanticsSource;
			this.packedRegionActiveNpcResidencySource =
				packedRegionActiveNpcResidencySource;
			this.packedRegionRetirementRefinementReassessmentSource =
				packedRegionRetirementRefinementReassessmentSource;
			this.packedRegionPreservationBurdenSource =
				packedRegionPreservationBurdenSource;
			this.packedRegionDynamicObjectPreservationSource =
				packedRegionDynamicObjectPreservationSource;
			this.packedRegionEventOwnershipSource =
				packedRegionEventOwnershipSource;
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
