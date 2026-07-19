package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/** Immutable diagnostic projection of one authoritative legacy packed point. */
public final class LayeredCoordinateParitySnapshot {
	private final int packedX;
	private final int packedY;
	private final WorldLocation location;
	private final WorldRegionKey regionKey;
	private final WorldMapSectorId terrainSectorId;
	private final int viewGridDistance;
	private final int viewTileRadius;
	private final WorldRegionWindow visibilityWindow;
	private final boolean roundTripExact;

	private LayeredCoordinateParitySnapshot(
		int packedX,
		int packedY,
		WorldLocation location,
		WorldRegionKey regionKey,
		WorldMapSectorId terrainSectorId,
		int viewGridDistance,
		int viewTileRadius,
		WorldRegionWindow visibilityWindow,
		boolean roundTripExact) {
		this.packedX = packedX;
		this.packedY = packedY;
		this.location = location;
		this.regionKey = regionKey;
		this.terrainSectorId = terrainSectorId;
		this.viewGridDistance = viewGridDistance;
		this.viewTileRadius = viewTileRadius;
		this.visibilityWindow = visibilityWindow;
		this.roundTripExact = roundTripExact;
	}

	public static LayeredCoordinateParitySnapshot capture(Point point) {
		return captureInternal(point, -1);
	}

	public static LayeredCoordinateParitySnapshot capture(
		final Point point,
		final int viewGridDistance) {
		if (viewGridDistance < 0) {
			throw new IllegalArgumentException("View grid distance must not be negative");
		}
		return captureInternal(point, viewGridDistance);
	}

	private static LayeredCoordinateParitySnapshot captureInternal(
		final Point point,
		final int viewGridDistance) {
		Objects.requireNonNull(point, "point");
		WorldLocation location = LegacyPackedPointAdapter.fromLegacyPoint(point);
		Point reconstructed = LegacyPackedPointAdapter.toLegacyPoint(location);
		int tileRadius = viewGridDistance < 0
			? -1 : Math.multiplyExact(viewGridDistance, 8);
		return new LayeredCoordinateParitySnapshot(
			point.getX(),
			point.getY(),
			location,
			WorldRegionKey.from(location),
			WorldMapSectorId.from(location),
			viewGridDistance,
			tileRadius,
			tileRadius < 0 ? null : WorldRegionWindow.around(location, tileRadius),
			reconstructed.getX() == point.getX() && reconstructed.getY() == point.getY());
	}

	public int getPackedX() {
		return packedX;
	}

	public int getPackedY() {
		return packedY;
	}

	public WorldLocation getLocation() {
		return location;
	}

	public WorldRegionKey getRegionKey() {
		return regionKey;
	}

	public WorldMapSectorId getTerrainSectorId() {
		return terrainSectorId;
	}

	public int getViewGridDistance() {
		return viewGridDistance;
	}

	public int getViewTileRadius() {
		return viewTileRadius;
	}

	public WorldRegionWindow getVisibilityWindow() {
		if (visibilityWindow == null) {
			throw new IllegalStateException(
				"Diagnostic snapshot was captured without visibility-window context");
		}
		return visibilityWindow;
	}

	public boolean isRoundTripExact() {
		return roundTripExact;
	}

	public String toCompactString() {
		WorldCoordinate coordinate = location.getCoordinate();
		return "packed=(" + packedX + ',' + packedY + ") layered=("
			+ coordinate.getX() + ',' + coordinate.getY() + ",L" + coordinate.getLevel()
			+ ") space=" + location.getWorldSpace().getValue()
			+ " region=(" + regionKey.getRegionX() + ',' + regionKey.getRegionY() + ")"
			+ " sector=(" + terrainSectorId.getSectorX() + ',' + terrainSectorId.getSectorY() + ")"
			+ " local=(" + coordinate.getLocalX() + ',' + coordinate.getLocalY() + ")"
			+ (visibilityWindow == null ? "" : " viewRegions=("
				+ visibilityWindow.getMinRegionX() + ',' + visibilityWindow.getMinRegionY()
				+ ".." + visibilityWindow.getMaxRegionX() + ','
				+ visibilityWindow.getMaxRegionY() + ")")
			+ " roundTrip=" + (roundTripExact ? "OK" : "FAIL");
	}

	public String toJson() {
		return toJson(false);
	}

	public String toJsonWithVisibilityWindow() {
		getVisibilityWindow();
		return toJson(true);
	}

	private String toJson(final boolean includeVisibilityWindow) {
		WorldCoordinate coordinate = location.getCoordinate();
		StringBuilder out = new StringBuilder(includeVisibilityWindow ? 560 : 320);
		out.append('{');
		out.append("\"legacy\":{\"x\":").append(packedX)
			.append(",\"y\":").append(packedY).append("},");
		out.append("\"layered\":{\"worldSpace\":\"")
			.append(jsonEscape(location.getWorldSpace().getValue()))
			.append("\",\"x\":").append(coordinate.getX())
			.append(",\"y\":").append(coordinate.getY())
			.append(",\"level\":").append(coordinate.getLevel()).append("},");
		out.append("\"region\":{\"x\":").append(regionKey.getRegionX())
			.append(",\"y\":").append(regionKey.getRegionY()).append("},");
		out.append("\"terrainSector\":{\"x\":").append(terrainSectorId.getSectorX())
			.append(",\"y\":").append(terrainSectorId.getSectorY()).append("},");
		out.append("\"local\":{\"x\":").append(coordinate.getLocalX())
			.append(",\"y\":").append(coordinate.getLocalY()).append("},");
		if (includeVisibilityWindow) {
			out.append("\"visibilityWindow\":{\"worldSpace\":\"")
				.append(jsonEscape(visibilityWindow.getWorldSpace().getValue()))
				.append("\",\"level\":").append(visibilityWindow.getLevel())
				.append(",\"gridDistance\":").append(viewGridDistance)
				.append(",\"tileRadius\":").append(viewTileRadius)
				.append(",\"minRegionX\":").append(visibilityWindow.getMinRegionX())
				.append(",\"minRegionY\":").append(visibilityWindow.getMinRegionY())
				.append(",\"maxRegionX\":").append(visibilityWindow.getMaxRegionX())
				.append(",\"maxRegionY\":").append(visibilityWindow.getMaxRegionY())
				.append(",\"regionCount\":").append(visibilityWindow.getRegionCount())
				.append("},");
		}
		out.append("\"roundTripExact\":").append(roundTripExact).append('}');
		return out.toString();
	}

	static String jsonEscape(String value) {
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
}
