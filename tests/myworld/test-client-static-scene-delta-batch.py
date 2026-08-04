#!/usr/bin/env python3
"""Regression coverage for linear legacy static-scene packet planning."""

from pathlib import Path
import subprocess
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "Client_Base/src/orsc/LegacyStaticSceneDeltaBatch.java"
HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"


FIXTURE = r"""
package orsc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LegacyStaticSceneDeltaBatchFixture {
	private static final class Entry {
		private final int id;
		private final int x;
		private final int z;
		private final int direction;

		private Entry(int id, int x, int z, int direction) {
			this.id = id;
			this.x = x;
			this.z = z;
			this.direction = direction;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Entry)) {
				return false;
			}
			Entry entry = (Entry) other;
			return id == entry.id && x == entry.x && z == entry.z
				&& direction == entry.direction;
		}

		@Override
		public String toString() {
			return id + "@" + x + "," + z + ":" + direction;
		}
	}

	private static final class Operation {
		private final boolean region;
		private final Entry entry;

		private Operation(boolean region, Entry entry) {
			this.region = region;
			this.entry = entry;
		}
	}

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkRecord(
            LegacyStaticSceneDeltaBatch.Record record,
            int id,
            int x,
            int z,
            int direction,
            String message) {
        check(record.getId() == id
                && record.getX() == x
                && record.getZ() == z
                && record.getDirection() == direction,
            message);
    }

    public static void main(String[] args) {
        LegacyStaticSceneDeltaBatch scenery =
            new LegacyStaticSceneDeltaBatch(false);
        scenery.addTileUpdate(100, 80, 88, 1);
        scenery.addTileUpdate(101, 80, 88, 3);
        scenery.addTileUpdate(102, 81, 88, 2);

        check(scenery.removesExisting(80, 88, 7),
            "scenery replacement must ignore direction");
        check(!scenery.removesExisting(82, 88, 0),
            "untouched scenery must remain");
        List<LegacyStaticSceneDeltaBatch.Record> sceneryAdds =
            scenery.survivingAdds();
        check(sceneryAdds.size() == 2,
            "only the final add per scenery tile should survive");
        checkRecord(sceneryAdds.get(0), 101, 80, 88, 3,
            "superseding scenery record");
        checkRecord(sceneryAdds.get(1), 102, 81, 88, 2,
            "independent scenery record order");

        LegacyStaticSceneDeltaBatch clears =
            new LegacyStaticSceneDeltaBatch(false);
        clears.addTileUpdate(200, 17, 17, 0);
        clears.addTileUpdate(201, 24, 24, 0);
        clears.addRegionClear(2, 2);
        clears.addTileUpdate(202, 18, 18, 0);
        clears.addTileUpdate(
            LegacyStaticSceneDeltaBatch.REMOVE_ID, 24, 24, 0);

        check(clears.removesExisting(16, 23, 0),
            "region clear must remove every existing object in its 8x8 area");
        check(clears.removesExisting(24, 24, 0),
            "explicit removal must remove an existing object");
        check(!clears.removesExisting(15, 23, 0),
            "adjacent region must remain untouched");
        List<LegacyStaticSceneDeltaBatch.Record> clearAdds =
            clears.survivingAdds();
        check(clearAdds.size() == 1,
            "region and explicit clears must discard only earlier additions");
        checkRecord(clearAdds.get(0), 202, 18, 18, 0,
            "add after a region clear must survive");

        LegacyStaticSceneDeltaBatch walls =
            new LegacyStaticSceneDeltaBatch(true);
        walls.addTileUpdate(300, -9, -9, 1);
        walls.addTileUpdate(301, -9, -9, 2);
        check(walls.removesExisting(-9, -9, 1),
            "matching wall direction must be replaced");
        check(walls.removesExisting(-9, -9, 2),
            "second wall direction must be replaced independently");
        check(!walls.removesExisting(-9, -9, 3),
            "unmentioned wall direction must remain");
        List<LegacyStaticSceneDeltaBatch.Record> wallAdds =
            walls.survivingAdds();
        check(wallAdds.size() == 2,
            "different wall directions may coexist on one tile");
        checkRecord(wallAdds.get(0), 300, -9, -9, 1,
            "first directional wall");
        checkRecord(wallAdds.get(1), 301, -9, -9, 2,
            "second directional wall");

        walls.addRegionClear(-2, -2);
        walls.addTileUpdate(302, -9, -9, 3);
        wallAdds = walls.survivingAdds();
        check(wallAdds.size() == 1,
            "negative-coordinate region clear must remove earlier wall adds");
        checkRecord(wallAdds.get(0), 302, -9, -9, 3,
            "wall add after region clear");

		Random random = new Random(0x51A71CL);
		for (int trial = 0; trial < 500; trial++) {
			boolean directional = (trial & 1) != 0;
			LegacyStaticSceneDeltaBatch batch =
				new LegacyStaticSceneDeltaBatch(directional);
			List<Entry> initial = new ArrayList<Entry>();
			for (int index = 0; index < random.nextInt(24); index++) {
				initial.add(new Entry(
					100 + random.nextInt(20),
					random.nextInt(65) - 32,
					random.nextInt(65) - 32,
					random.nextInt(4)));
			}
			List<Operation> operations = new ArrayList<Operation>();
			for (int index = 0; index < random.nextInt(36); index++) {
				if (random.nextInt(5) == 0) {
					Entry region = new Entry(
						LegacyStaticSceneDeltaBatch.REMOVE_ID,
						random.nextInt(9) - 4,
						random.nextInt(9) - 4,
						0);
					operations.add(new Operation(true, region));
					batch.addRegionClear(region.x, region.z);
				} else {
					Entry update = new Entry(
						random.nextInt(4) == 0
							? LegacyStaticSceneDeltaBatch.REMOVE_ID
							: 200 + random.nextInt(20),
						random.nextInt(65) - 32,
						random.nextInt(65) - 32,
						random.nextInt(4));
					operations.add(new Operation(false, update));
					batch.addTileUpdate(
						update.id, update.x, update.z, update.direction);
				}
			}

			List<Entry> expected = new ArrayList<Entry>(initial);
			for (Operation operation : operations) {
				for (int index = expected.size() - 1; index >= 0; index--) {
					Entry existing = expected.get(index);
					boolean remove = operation.region
						? existing.x >> 3 == operation.entry.x
							&& existing.z >> 3 == operation.entry.z
						: existing.x == operation.entry.x
							&& existing.z == operation.entry.z
							&& (!directional
								|| existing.direction == operation.entry.direction);
					if (remove) {
						expected.remove(index);
					}
				}
				if (!operation.region
						&& operation.entry.id
							!= LegacyStaticSceneDeltaBatch.REMOVE_ID) {
					expected.add(operation.entry);
				}
			}

			List<Entry> actual = new ArrayList<Entry>();
			for (Entry existing : initial) {
				if (!batch.removesExisting(
						existing.x, existing.z, existing.direction)) {
					actual.add(existing);
				}
			}
			for (LegacyStaticSceneDeltaBatch.Record record :
					batch.survivingAdds()) {
				actual.add(new Entry(
					record.getId(), record.getX(), record.getZ(),
					record.getDirection()));
			}
			check(expected.equals(actual),
				"batch diverged from sequential packet semantics at trial "
					+ trial + " expected=" + expected + " actual=" + actual);
		}
    }
}
"""


def main() -> None:
    handler = HANDLER.read_text(encoding="utf-8")
    required = (
        "new LegacyStaticSceneDeltaBatch(false)",
        "new LegacyStaticSceneDeltaBatch(true)",
        "applyLegacyGameObjectBatch(batch);",
        "applyLegacyWallObjectBatch(batch);",
        "batch.survivingAdds()",
    )
    for snippet in required:
        if snippet not in handler:
            raise AssertionError(f"packet handler batch integration missing: {snippet}")

    with tempfile.TemporaryDirectory(prefix="legacy-scene-delta-batch-") as name:
        temp = Path(name)
        fixture = temp / "orsc/LegacyStaticSceneDeltaBatchFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            ["javac", "-d", str(temp), str(SOURCE), str(fixture)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run(
            ["java", "-cp", str(temp),
             "orsc.LegacyStaticSceneDeltaBatchFixture"],
            check=True,
            cwd=ROOT,
        )

    print("PASS: legacy scenery/wall deltas batch linearly with exact packet semantics")


if __name__ == "__main__":
    main()
