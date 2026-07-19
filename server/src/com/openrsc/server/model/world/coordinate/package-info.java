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
 * permits that comparison only in bounded private diagnostics.
 * Packed storage and existing runtime decisions remain authoritative.</p>
 */
package com.openrsc.server.model.world.coordinate;
