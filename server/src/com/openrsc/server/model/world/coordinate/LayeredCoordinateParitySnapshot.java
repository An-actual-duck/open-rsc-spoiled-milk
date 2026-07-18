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
	private final boolean roundTripExact;

	private LayeredCoordinateParitySnapshot(
		int packedX,
		int packedY,
		WorldLocation location,
		WorldRegionKey regionKey,
		WorldMapSectorId terrainSectorId,
		boolean roundTripExact) {
		this.packedX = packedX;
		this.packedY = packedY;
		this.location = location;
		this.regionKey = regionKey;
		this.terrainSectorId = terrainSectorId;
		this.roundTripExact = roundTripExact;
	}

	public static LayeredCoordinateParitySnapshot capture(Point point) {
		Objects.requireNonNull(point, "point");
		WorldLocation location = LegacyPackedPointAdapter.fromLegacyPoint(point);
		Point reconstructed = LegacyPackedPointAdapter.toLegacyPoint(location);
		return new LayeredCoordinateParitySnapshot(
			point.getX(),
			point.getY(),
			location,
			WorldRegionKey.from(location),
			WorldMapSectorId.from(location),
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
			+ " roundTrip=" + (roundTripExact ? "OK" : "FAIL");
	}

	public String toJson() {
		WorldCoordinate coordinate = location.getCoordinate();
		StringBuilder out = new StringBuilder(320);
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
