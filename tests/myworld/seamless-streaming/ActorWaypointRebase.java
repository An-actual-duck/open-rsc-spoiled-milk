package orsc;

import orsc.enumerations.ORSCharacterDirection;

/** Focused check of the actor state carried through an ordinary origin shift. */
public final class ActorWaypointRebase {
	private static final int TILE_SIZE = 128;
	private static final int SECTION_UNITS = 48 * TILE_SIZE;

	public static void main(String[] args) {
		verify(9, 1, SECTION_UNITS, 0);
		verify(8, 0, -SECTION_UNITS, SECTION_UNITS);
		verify(2, 5, 0, -SECTION_UNITS);
		verify(7, 6, SECTION_UNITS, 0);
		System.out.println("PASS actor world position, wrapped and ordinary waypoints, and animation state survive rebasing");
	}

	private static void verify(int read, int write, int offsetX, int offsetZ) {
		ORSCharacter actor = new ORSCharacter();
		actor.waypointIndexNext = read;
		actor.waypointIndexCurrent = write;
		actor.currentX = 8192;
		actor.currentZ = 11776;
		actor.stepFrame = 29;
		actor.direction = ORSCharacterDirection.SOUTH;
		actor.animationNext = 4;
		actor.movementLastFrameMillis = 800;
		actor.movementPixelRemainder = 0.375;
		for (int slot = 0; slot < actor.waypointsX.length; slot++) {
			actor.waypointsX[slot] = actor.currentX + (slot + 1) * 8;
			actor.waypointsZ[slot] = actor.currentZ - (slot + 1) * 12;
		}
		int[] beforeX = actor.waypointsX.clone();
		int[] beforeZ = actor.waypointsZ.clone();

		actor.rebaseMovementPosition(offsetX, offsetZ);

		require(actor.currentX + offsetX == 8192 && actor.currentZ + offsetZ == 11776,
			"Actor moved in world space during an origin shift");
		int slot = read;
		while (slot != (write + 1) % beforeX.length) {
			require(actor.waypointsX[slot] + offsetX == beforeX[slot]
					&& actor.waypointsZ[slot] + offsetZ == beforeZ[slot],
				"Pending waypoint changed in world space at slot " + slot);
			slot = (slot + 1) % beforeX.length;
		}
		require(actor.waypointsX[write] + offsetX == beforeX[write]
				&& actor.waypointsZ[write] + offsetZ == beforeZ[write],
			"Last authoritative waypoint changed in world space");
		require(actor.waypointIndexNext == read && actor.waypointIndexCurrent == write
				&& actor.stepFrame == 29 && actor.direction == ORSCharacterDirection.SOUTH
				&& actor.animationNext == 4 && actor.movementLastFrameMillis == 800
				&& actor.movementPixelRemainder == 0.375,
			"Origin shift changed ordering, facing, walk phase, or interpolation");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
