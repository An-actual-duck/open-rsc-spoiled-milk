package orsc.enumerations;

public enum ORSCharacterDirection {
	NORTH(0, 0, -1, 128),
	NORTH_WEST(1, 1, -1, 96),
	WEST(2, 1, 0, 64),
	SOUTH_WEST(3, 1, 1, 32),
	SOUTH(4, 0, 1, 0),
	SOUTH_EAST(5, -1, 1, 224),
	EAST(6, -1, 0, 192),
	NORTH_EAST(7, -1, -1, 160),
	COMBAT_NORTH(8, 0, -1, 128),
	COMBAT_NORTH_WEST(9, 1, -1, 96),
	COMBAT_WEST(10, 1, 0, 64),
	COMBAT_SOUTH_WEST(11, 1, 1, 32),
	COMBAT_SOUTH(12, 0, 1, 0),
	COMBAT_SOUTH_EAST(13, -1, 1, 224),
	COMBAT_EAST(14, -1, 0, 192),
	COMBAT_NORTH_EAST(15, -1, -1, 160);
	private static final ORSCharacterDirection[] rsDir_Lookup;

	static {
		int max = 0;
		for (ORSCharacterDirection c : values())
			max = Math.max(max, c.rsDir + 1);
		rsDir_Lookup = new ORSCharacterDirection[max];
		for (ORSCharacterDirection c : values())
			rsDir_Lookup[c.rsDir] = c;
	}

	public final int x0, z0;
	public final int rsDir;
	public final int rotation;

	private ORSCharacterDirection(int rsDir, int x0, int z0, int rotation) {
		this.rsDir = rsDir;
		this.x0 = x0;
		this.z0 = z0;
		this.rotation = rotation;
	}

	public static ORSCharacterDirection lookup(int rsDir) {
		if (rsDir >= 0 && rsDir < rsDir_Lookup.length)
			return rsDir_Lookup[rsDir];
		for (ORSCharacterDirection c : values())
			if (c.rsDir == rsDir)
				return c;
		System.out.println("Lookup fail: " + rsDir);
		return null;
	}

	public boolean isCombat() {
		return rsDir >= COMBAT_NORTH.rsDir && rsDir <= COMBAT_NORTH_EAST.rsDir;
	}

	public int combatBaseDirection() {
		return isCombat() ? rsDir - COMBAT_NORTH.rsDir : rsDir;
	}
}
