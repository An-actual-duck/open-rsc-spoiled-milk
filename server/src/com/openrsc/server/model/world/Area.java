package com.openrsc.server.model.world;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldArea;
import com.openrsc.server.model.world.coordinate.WorldLocation;

public class Area {

	private int minX, maxX, minY, maxY;

	private String name;

	public Area(int minX, int maxX, int minY, int maxY) {
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
	}

	public Area(int minX, int maxX, int minY, int maxY, String areaName) {
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
		this.setName(areaName);
	}

	public int getMinX() {
		return minX;
	}

	public void setMinX(int minX) {
		this.minX = minX;
	}

	public int getMaxX() {
		return maxX;
	}

	public void setMaxX(int maxX) {
		this.maxX = maxX;
	}

	public int getMinY() {
		return minY;
	}

	public void setMinY(int minY) {
		this.minY = minY;
	}

	public int getMaxY() {
		return maxY;
	}

	public void setMaxY(int maxY) {
		this.maxY = maxY;
	}

	public boolean inBounds(int x, int y) {
		return x > minX && x < maxX && y > minY && y < maxY;
	}

	public boolean inBounds(Point p) {

		int x = p.getX();
		int y = p.getY();

		return x > minX && x < maxX && y > minY && y < maxY;
	}

	/**
	 * Tests a layered location against this legacy global area's open bounds.
	 */
	public boolean inBounds(WorldLocation location) {
		return toWorldArea().contains(location);
	}

	/**
	 * Returns a checked layered snapshot of the area's current packed bounds.
	 *
	 * <p>A legacy area whose boundaries cross packed planes cannot represent one
	 * layered rectangle and is rejected instead of being silently flattened.</p>
	 */
	public WorldArea toWorldArea() {
		return new WorldArea(
			LegacyPackedPointAdapter.fromPackedValues(minX, minY),
			LegacyPackedPointAdapter.fromPackedValues(maxX, maxY));
	}

	public String getName() {
		return name;
	}

	public void setName(String areaName) {
		this.name = areaName;
	}
}
