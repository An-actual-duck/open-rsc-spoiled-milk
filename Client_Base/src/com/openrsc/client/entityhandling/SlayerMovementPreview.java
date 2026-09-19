package com.openrsc.client.entityhandling;

/** Owner-approved artwork fixtures; not production Slayer combat definitions. */
public enum SlayerMovementPreview {
	GIANT_FROG(863, "Giant frog", "giant-frog", 100, 100, 100, 100, 100, 100),
	COCKATRICE(864, "Cockatrice", "cockatrice", 100, 100, 100, 100, 100, 100),
	BANSHEE(865, "Banshee", "banshee", 100, 100, 100, 100, 100),
	NAGA(866, "Naga", "naga", 100, 100, 100, 100, 100, 100, 128),
	TERROR_DOG(867, "Terror dog", "terror-dog", 100, 100, 100, 100, 100, 100),
	BLOODVELD(868, "Bloodveld", "bloodveld", 120, 120, 120, 120, 120, 120),
	DARK_BEAST(869, "Dark beast", "dark-beast", 100, 100, 100, 100, 100, 100),
	ABYSSAL_DEMON(870, "Abyssal demon", "abyssal-demon", 100, 100, 100, 100, 100, 112);

	public final int npcId;
	public final String displayName;
	public final String assetName;
	private final int[] columns;
	private static final SlayerMovementPreview[] LOOKUP = values();

	SlayerMovementPreview(int npcId, String displayName, String assetName, int... columns) {
		this.npcId = npcId;
		this.displayName = displayName;
		this.assetName = assetName;
		this.columns = columns;
	}

	public String animationName() { return "slayer-preview-" + assetName; }
	public boolean combatEnabled() { return this == GIANT_FROG || this == COCKATRICE; }
	public int[] columnWidths() { return columns.clone(); }
	public int frameHeight() { return this == BLOODVELD ? 110 : 100; }
	// Uniform 2.4 world-unit scale per native pixel, including the Bloodveld's
	// larger presentation canvas. No per-direction rescaling or pixel rewriting.
	public int cameraWidth() { return columns[0] * 12 / 5; }
	public int cameraHeight() { return frameHeight() * 12 / 5; }

	public int movementFrame(int stepFrame, int walkModel, boolean moving) {
		if (!moving) return 0;
		int phase = Math.max(0, stepFrame) / Math.max(1, walkModel);
		// Frog: crouch -> kick -> land. Others: idle -> left -> idle -> right.
		if (this == GIANT_FROG) return phase % 3;
		int beat = phase % 4;
		return beat == 1 ? 1 : beat == 3 ? 2 : 0;
	}

	public static SlayerMovementPreview forNpc(int id) {
		int index = id - GIANT_FROG.npcId;
		return index >= 0 && index < LOOKUP.length ? LOOKUP[index] : null;
	}

	public static boolean isAnimation(String name) {
		for (SlayerMovementPreview preview : LOOKUP) {
			if (preview.animationName().equalsIgnoreCase(name)) return true;
		}
		return false;
	}
}
