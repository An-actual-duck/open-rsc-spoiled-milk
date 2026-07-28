package com.openrsc.server.model.world.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detached static-scene content for the visual-only ring surrounding the
 * authoritative client scene.
 *
 * <p>The records in this snapshot never grant collision, interaction, or
 * lifecycle authority. They are an exact current-world presentation copy
 * outside the active three-by-three sector square and inside the resident
 * five-by-five sector square.</p>
 */
public final class StaticScenePresentationSnapshot {
	private final int centerSectorX;
	private final int centerSectorY;
	private final int outerRadius;
	private final int innerRadius;
	private final long objectVersion;
	private final List<Record> scenery;
	private final List<Record> walls;

	public StaticScenePresentationSnapshot(
		final int centerSectorX,
		final int centerSectorY,
		final int outerRadius,
		final int innerRadius,
		final long objectVersion,
		final List<Record> scenery,
		final List<Record> walls) {
		this.centerSectorX = centerSectorX;
		this.centerSectorY = centerSectorY;
		this.outerRadius = outerRadius;
		this.innerRadius = innerRadius;
		this.objectVersion = objectVersion;
		this.scenery = Collections.unmodifiableList(
			new ArrayList<Record>(scenery));
		this.walls = Collections.unmodifiableList(
			new ArrayList<Record>(walls));
	}

	public int getCenterSectorX() {
		return centerSectorX;
	}

	public int getCenterSectorY() {
		return centerSectorY;
	}

	public int getOuterRadius() {
		return outerRadius;
	}

	public int getInnerRadius() {
		return innerRadius;
	}

	public long getObjectVersion() {
		return objectVersion;
	}

	public List<Record> getScenery() {
		return scenery;
	}

	public List<Record> getWalls() {
		return walls;
	}

	/** Immutable wire-facing copy of one presentation-only static object. */
	public static final class Record {
		private final int id;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;

		public Record(
			final int id,
			final int x,
			final int y,
			final int direction,
			final int type) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
		}

		public int getId() {
			return id;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getDirection() {
			return direction;
		}

		public int getType() {
			return type;
		}
	}
}
