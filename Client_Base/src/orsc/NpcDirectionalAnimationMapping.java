package orsc;

/** Shared camera-relative direction contract for three-frame NPC animations. */
final class NpcDirectionalAnimationMapping {
	static final int FRAMES_PER_DIRECTION = 3;
	static final int DIRECTION_COLUMNS = 5;
	static final int COMBAT_COLUMN = 5;

	private NpcDirectionalAnimationMapping() {
	}

	static int sourceDirection(final int cameraRelativeDirection) {
		final int direction = cameraRelativeDirection & 7;
		if (direction == 5) {
			return 3;
		}
		if (direction == 6) {
			return 2;
		}
		if (direction == 7) {
			return 1;
		}
		return direction;
	}

	static boolean mirrors(final int cameraRelativeDirection) {
		final int direction = cameraRelativeDirection & 7;
		return direction >= 5;
	}

	static int frameOffset(final int cameraRelativeDirection, final int frame) {
		if (frame < 0 || frame >= FRAMES_PER_DIRECTION) {
			throw new IllegalArgumentException("NPC direction frame must be between 0 and 2");
		}
		return sourceDirection(cameraRelativeDirection) * FRAMES_PER_DIRECTION + frame;
	}

	static int combatFrameOffset(final int frame) {
		if (frame < 0 || frame >= FRAMES_PER_DIRECTION) {
			throw new IllegalArgumentException("NPC combat frame must be between 0 and 2");
		}
		return COMBAT_COLUMN * FRAMES_PER_DIRECTION + frame;
	}
}
