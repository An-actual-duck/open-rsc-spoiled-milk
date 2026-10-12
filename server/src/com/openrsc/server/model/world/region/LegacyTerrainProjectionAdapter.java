package com.openrsc.server.model.world.region;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.LegacyLogicalTileAddress;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Exact logical-to-packed backend boundary; it never infers native identity. */
public final class LegacyTerrainProjectionAdapter {
	private final BooleanSupplier syntheticEnabled;
	private final Function<Point, TileValue> read;
	private final Function<Point, TileValue> mutable;

	public LegacyTerrainProjectionAdapter(BooleanSupplier syntheticEnabled,
		Function<Point, TileValue> read, Function<Point, TileValue> mutable) {
		this.syntheticEnabled = Objects.requireNonNull(syntheticEnabled, "syntheticEnabled");
		this.read = Objects.requireNonNull(read, "read");
		this.mutable = Objects.requireNonNull(mutable, "mutable");
	}
	public TileValue getTile(WorldLocation location) { return read.apply(project(location)); }
	public TileValue getMutableTile(WorldLocation location) { return mutable.apply(project(location)); }
	public Point project(WorldLocation location) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		if (LayeredCompatibilityPointAdapter.isSyntheticDeepLevel(checked)) {
			return LayeredCompatibilityPointAdapter.toCompatibilityPoint(checked, syntheticEnabled.getAsBoolean());
		}
		WorldRegionKey key = WorldRegionKey.from(checked);
		LegacyLogicalTileAddress address = LegacyLogicalTileAddress.resolve(key,
			Math.floorMod(checked.getCoordinate().getX(), WorldRegionKey.REGION_SIZE),
			Math.floorMod(checked.getCoordinate().getY(), WorldRegionKey.REGION_SIZE));
		if (!address.isLegacyRepresentable() || !checked.equals(address.getLogicalLocation())) {
			throw new IllegalArgumentException("Logical tile has no exact legacy terrain projection: " + checked);
		}
		return address.getLegacyPoint();
	}
}
