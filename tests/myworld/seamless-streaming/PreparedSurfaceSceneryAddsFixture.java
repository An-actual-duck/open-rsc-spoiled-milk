package orsc;

import java.util.Arrays;
import java.util.List;
import orsc.graphics.three.World;

public final class PreparedSurfaceSceneryAddsFixture {
	private static final int BASE_X = 48, BASE_Z = 48;
	private static final int PLAYER_X = 60, PLAYER_Z = 50;

	public static void main(String[] args) {
		World.SurfaceSceneryPlacement first = row(253, 137, 59, 4, 0);
		World.SurfaceSceneryPlacement second = row(709, 120, 51, 1, 0);
		World.SurfaceSceneryPlacement north = row(0, 135, 36, 0, 1);
		World.SurfaceSceneryPlacement east = row(1, 135, 36, 1, 1);
		PreparedSurfaceSceneryAdds plan = prepare(first, second, north, east);
		require(plan != null && plan.moves(0, BASE_Z, BASE_X, BASE_Z), "prepared basis");
		require(!plan.moves(0, 0, BASE_X, BASE_Z) && !plan.at(BASE_X, 0), "reject the wrong terrain origin");
		byte[] exact = packet(new int[]{60000, -4, 31, 2}, legacy(first), legacy(second));
		require(match(plan, exact, false), "accept exact object order with a departing removal");
		require(match(plan, packet(legacy(north), legacy(east)), true), "keep both walls at the same tile in order");
		require(!match(plan, packet(legacy(second), legacy(first)), false), "reject reordered additions");
		require(!match(plan, packet(legacy(first)), false), "reject absent addition");
		require(!match(plan, packet(legacy(first), legacy(second), legacy(second)), false), "reject extra addition");
		require(!match(plan, packet(new int[]{60000, 5, 31, 2}, legacy(first), legacy(second)), false), "retain an in-window removal");
		require(!match(plan, packet(new int[]{0xff00, -4, 31, 2}, legacy(first), legacy(second)), false), "retain a region clear");
		require(!match(plan, Arrays.copyOf(exact, exact.length - 1), false), "reject a truncated packet");
		require(!match(plan, packet(legacy(first), new int[]{second.id, 120, 51, 0}), false), "reject a direction change");
		require(prepare(first, row(10, 137, 59, 1, 0)) == null, "reject repeated object tile");
		require(prepare(north, row(10, 135, 36, 0, 1)) == null, "reject repeated wall side");
		require(prepare(row(10, 95, 70, 0, 0)) == null, "reject a retained tile");
		require(prepare(row(10, 144, 70, 0, 0)) == null, "reject an out-of-window tile");

		LegacyStaticSceneDeltaBatch legacy = new LegacyStaticSceneDeltaBatch(false);
		legacy.addTileUpdate(60000, -4, 31, 2);
		legacy.addTileUpdate(first.id, 137, 59, 4);
		legacy.addTileUpdate(second.id, 120, 51, 1);
		List<LegacyStaticSceneDeltaBatch.Record> expected = legacy.survivingAdds();
		List<LegacyStaticSceneDeltaBatch.Record> actual = plan.additions(false);
		for (int i = 0; i < expected.size(); i++) {
			LegacyStaticSceneDeltaBatch.Record a = actual.get(i), b = expected.get(i);
			require(a.getId() == b.getId() && a.getX() == b.getX() && a.getZ() == b.getZ()
				&& a.getDirection() == b.getDirection(), "exact legacy row");
		}
		System.out.println("Prepared scenery additions match legacy order and reject stale or ambiguous packets.");
	}

	private static PreparedSurfaceSceneryAdds prepare(World.SurfaceSceneryPlacement... rows) {
		return PreparedSurfaceSceneryAdds.from(0, BASE_Z, BASE_X, BASE_Z, Arrays.asList(rows));
	}

	private static World.SurfaceSceneryPlacement row(int id, int x, int z, int dir, int type) {
		return new World.SurfaceSceneryPlacement(id, x + BASE_X, z + BASE_Z, dir, type);
	}

	private static int[] legacy(World.SurfaceSceneryPlacement row) {
		return new int[]{row.id, row.x - BASE_X, row.z - BASE_Z, row.direction};
	}

	private static boolean match(PreparedSurfaceSceneryAdds plan, byte[] packet, boolean wall) {
		return plan.matches(packet, 1, packet.length, PLAYER_X, PLAYER_Z, wall);
	}

	private static byte[] packet(int[]... rows) {
		byte[] packet = new byte[1 + rows.length * 5];
		int i = 1;
		for (int[] row : rows) {
			packet[i++] = (byte) (row[0] >> 8);
			packet[i++] = (byte) row[0];
			packet[i++] = (byte) (row[1] - PLAYER_X);
			packet[i++] = (byte) (row[2] - PLAYER_Z);
			packet[i++] = (byte) row[3];
		}
		return packet;
	}

	private static void require(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
