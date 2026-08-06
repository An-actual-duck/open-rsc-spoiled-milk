package orsc;

/** Disabled profile used by isolated native terrain protocol fixtures. */
public final class WorldBuilderClientProfile {
	private static final WorldBuilderClientProfile CURRENT =
		new WorldBuilderClientProfile();

	private WorldBuilderClientProfile() {
	}

	public static WorldBuilderClientProfile current() {
		return CURRENT;
	}

	public boolean isStrictAdaptiveTerrain() {
		return false;
	}

	public String strictAdaptiveMapIdentity() {
		throw new IllegalStateException("fixture has no adaptive terrain");
	}

	public void requireNativePackageIdentity(
		String packageId, String packageVersion, String manifestSha256) {
		// Fixed-profile protocol fixtures intentionally have no adaptive binding.
	}
}
