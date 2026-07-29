/**
 * Signed layered-location contract for staged server migration.
 *
 * <p>Legacy conversion stays explicit through {@link
 * com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter}. Slice 4
 * permits the existing {@code Area} model to expose a checked layered snapshot,
 * and Slice 5 permits {@code RegionManager} to calculate a
 * {@code WorldRegionKey}. Slice 6 adds a read-only directed transition
 * projection from existing object telepoints. Slice 7 separates logical map-
 * sector identity from offset legacy terrain entry names. Slice 8 exposes
 * checked static-placement locations and inclusive NPC roaming bounds. Slice
 * 11 adds an opt-in parity snapshot consumed only by private diagnostics,
 * Slices 12 and 13 add checked Player location and logical-region mirrors, and
 * Slice 14 projects legacy Player persistence values without changing their
 * database representation. Slice 15 defines a read-only level-qualified
 * logical visibility window without adopting it for packed lookup or caches,
 * and Slice 16 maintains a checked Player shadow of that projection for
 * private diagnostics without making it authoritative. Slice 17 defines a
 * deterministic, allocation-budgeted logical interest delta without adopting
 * it for runtime lookup, packets, or client residency. Slice 18 permits that
 * delta only in bounded private diagnostics; normal runtime authorities remain
 * unchanged. Slice 19 projects complete logical-key coverage for one legacy
 * packed region cell without rekeying or splitting runtime storage. Slice 20
 * compares the union of one packed visibility candidate window with its signed
 * logical window without querying regions or adopting either result. Slice 21
 * permits that comparison only in bounded private diagnostics. Slice 22
 * partitions one packed cell into exact contiguous logical tile fragments
 * without reading or replacing runtime Region storage. Slice 23 inverts those
 * fragments into complete, partial, or unsupported legacy assembly plans for
 * one logical key without copying runtime tiles. Slice 24 resolves an exact
 * packed source cell and local index for one logical region-local tile without
 * reading a runtime TileValue. Slice 25 permits RegionManager to copy those
 * values into a detached logical tile snapshot while packed collision and
 * storage stay authoritative. Slice 26 exposes bounded snapshot metadata only
 * through private diagnostics, and Slice 27 stores immutable full-fidelity
 * logical tile state inside that snapshot while retaining fresh legacy copies
 * as a compatibility bridge. Slice 28 compares one such state with its direct
 * packed source through a dormant, non-mutating RegionManager projection.
 * Slice 29 emits bounded metadata from that comparison only through opt-in
 * private diagnostics. Slice 30 extends the dormant comparison across one
 * checked 3x3 tile neighborhood without collision or pathing adoption. Slice
 * 31 exposes only that neighborhood's bounded counts through selected opt-in
 * private diagnostic events. Slice 32 compares one dormant adjacent logical
 * and packed tile-mask decision without changing movement or PathValidation.
 * Slice 33 emits all eight such comparisons only through bounded opt-in
 * private diagnostics. Slice 34 composes the same dormant decision across an
 * already expanded, bounded adjacent-step route without selecting or executing
 * a path. Slice 35 exposes only the latest bounded observed walking segment
 * through opt-in private diagnostics. Slice 36 mirrors packed Region lifecycle
 * as checked logical residency, Slice 37 compares that residency with dormant
 * interest deltas, and Slice 38 emits bounded comparison evidence only through
 * private diagnostics. Slice 39 defines opaque process-local interest owners
 * and shared logical-region reference counts, and Slice 40 maintains one such
 * checked owner per Player session without consuming its results. Slice 41
 * emits bounded owner and global/shared reference-transition evidence through
 * private diagnostics only. Slice 42 projects global releases through a
 * conservative tick-based retirement cooldown without authorizing lifecycle
 * changes. Slice 43 emits bounded transition and recent-release cooldown
 * evidence through private diagnostics only. Packed storage and existing
 * runtime decisions remain authoritative. Slices 44-50 validate and expose
 * bounded retirement decisions, packed-source readiness, and read-only content
 * safety while retaining the absent reload boundary. Slice 51 permits the
 * whole-world populator to freeze count-only authored construction origins per
 * packed source without exposing placement definitions or adopting Region
 * teardown/reconstruction. Slice 52 projects only those immutable origin
 * counts onto the exact private retirement-safety sources while explicitly
 * denying reconstruction-manifest semantics. Slices 53-62 add detached
 * authored identities, final-live population outcomes, conservative
 * dependency envelopes, inert reconstruction recipes, and bounded private
 * evidence without a runtime reconstruction consumer. Slice 63 analyzes the
 * fixed-point authored cohort and separates empty external support coordinates
 * without granting loading, teardown, or lifecycle authority. Slice 65
 * attributes that cohort's exact owner-to-requirement edges and cross-source
 * placement bridges by construction and dependency kind without changing the
 * conservative envelopes or granting lifecycle authority. Slices 67-78 add
 * detached topology, dependency-semantics, active-NPC containment, boundary-
 * requirement, and refinement evidence plus private diagnostics. Slice 79
 * reassesses one exact proposed candidate set against strictly newer atomic
 * evidence while keeping candidate-set convergence separate from retirement
 * or lifecycle authority. Slice 80 permits the runtime to peek that exact
 * candidate selection without loading absent Regions or manufacturing
 * retirement readiness. Slice 81 composes a strictly newer candidate
 * observation, authored cohort, NPC census, and reassessment behind one
 * same-tick private runtime seam without exposing it to diagnostics yet.</p>
 */
package com.openrsc.server.model.world.coordinate;
