/**
 * Signed layered-location contract for staged server migration.
 *
 * <p>Legacy conversion stays explicit through {@link
 * com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter}. Slice 4
 * permits the existing {@code Area} model to expose a checked layered snapshot;
 * packed storage and existing runtime decisions remain authoritative.</p>
 */
package com.openrsc.server.model.world.coordinate;
