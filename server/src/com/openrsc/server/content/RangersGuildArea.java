package com.openrsc.server.content;

import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldTileBounds;

/** Level-qualified locations owned by the Rangers Guild activity. */
public final class RangersGuildArea {
	private static final WorldTileBounds BASEMENT = new WorldTileBounds(
		WorldLocation.global(new WorldCoordinate(484, 456, -1)),
		WorldLocation.global(new WorldCoordinate(515, 483, -1)));
	private static final WorldLocation ENTRANCE_DOOR =
		LegacyPackedPointAdapter.fromPackedValues(495, 463);

	private RangersGuildArea() {
	}

	public static boolean containsBasement(final WorldLocation location) {
		return location != null && BASEMENT.contains(location);
	}

	public static boolean isEntranceDoor(final WorldLocation location) {
		return ENTRANCE_DOOR.equals(location);
	}

}
