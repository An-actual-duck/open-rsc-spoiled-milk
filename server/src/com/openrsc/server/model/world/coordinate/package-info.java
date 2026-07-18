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
 * checked static-placement locations and inclusive NPC roaming bounds. Packed
 * storage and existing runtime decisions remain authoritative.</p>
 */
package com.openrsc.server.model.world.coordinate;
