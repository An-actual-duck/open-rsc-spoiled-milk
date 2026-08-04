package orsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans one legacy scenery or boundary packet without repeatedly scanning the
 * complete client scene for every record. PacketHandler remains responsible
 * for model, collision, and world ownership side effects.
 */
final class LegacyStaticSceneDeltaBatch {
	static final int REMOVE_ID = 60000;

	private final boolean directional;
	private final List<Record> records = new ArrayList<Record>();
	private final Set<TileKey> touchedTiles = new HashSet<TileKey>();
	private final Set<Long> clearedRegions = new HashSet<Long>();

	LegacyStaticSceneDeltaBatch(final boolean directional) {
		this.directional = directional;
	}

	void addTileUpdate(
			final int id,
			final int x,
			final int z,
			final int direction) {
		Record record = Record.tile(id, x, z, direction);
		records.add(record);
		touchedTiles.add(tileKey(record));
	}

	void addRegionClear(final int regionX, final int regionZ) {
		records.add(Record.region(regionX, regionZ));
		clearedRegions.add(regionKey(regionX, regionZ));
	}

	boolean removesExisting(
			final int x,
			final int z,
			final int direction) {
		return touchedTiles.contains(tileKey(x, z, direction))
			|| clearedRegions.contains(regionKey(x >> 3, z >> 3));
	}

	List<Record> records() {
		return Collections.unmodifiableList(records);
	}

	List<Record> survivingAdds() {
		Set<TileKey> laterTileUpdates = new HashSet<TileKey>();
		Set<Long> laterRegionClears = new HashSet<Long>();
		List<Record> reverseSurvivors = new ArrayList<Record>();
		for (int index = records.size() - 1; index >= 0; index--) {
			Record record = records.get(index);
			if (record.isRegionClear()) {
				laterRegionClears.add(regionKey(record.getX(), record.getZ()));
				continue;
			}
			TileKey tile = tileKey(record);
			boolean superseded = laterTileUpdates.contains(tile)
				|| laterRegionClears.contains(
					regionKey(record.getX() >> 3, record.getZ() >> 3));
			laterTileUpdates.add(tile);
			if (!superseded && record.getId() != REMOVE_ID) {
				reverseSurvivors.add(record);
			}
		}
		Collections.reverse(reverseSurvivors);
		return reverseSurvivors;
	}

	private TileKey tileKey(final Record record) {
		return tileKey(record.getX(), record.getZ(), record.getDirection());
	}

	private TileKey tileKey(
			final int x,
			final int z,
			final int direction) {
		return new TileKey(x, z, directional ? direction : 0);
	}

	private static long regionKey(final int x, final int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	static final class Record {
		private final int id;
		private final int x;
		private final int z;
		private final int direction;
		private final boolean regionClear;

		private Record(
				final int id,
				final int x,
				final int z,
				final int direction,
				final boolean regionClear) {
			this.id = id;
			this.x = x;
			this.z = z;
			this.direction = direction;
			this.regionClear = regionClear;
		}

		static Record tile(
				final int id,
				final int x,
				final int z,
				final int direction) {
			return new Record(id, x, z, direction, false);
		}

		static Record region(final int regionX, final int regionZ) {
			return new Record(REMOVE_ID, regionX, regionZ, 0, true);
		}

		int getId() {
			return id;
		}

		int getX() {
			return x;
		}

		int getZ() {
			return z;
		}

		int getDirection() {
			return direction;
		}

		boolean isRegionClear() {
			return regionClear;
		}
	}

	private static final class TileKey {
		private final int x;
		private final int z;
		private final int direction;

		private TileKey(final int x, final int z, final int direction) {
			this.x = x;
			this.z = z;
			this.direction = direction;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof TileKey)) {
				return false;
			}
			TileKey key = (TileKey) other;
			return x == key.x && z == key.z && direction == key.direction;
		}

		@Override
		public int hashCode() {
			int hash = x;
			hash = hash * 31 + z;
			return hash * 31 + direction;
		}
	}
}
