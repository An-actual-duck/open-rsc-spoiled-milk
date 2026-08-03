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

	public void requireNativePackageIdentity(
		String packageId, String packageVersion, String manifestSha256) {
		// Fixed-profile protocol fixtures intentionally have no adaptive binding.
	}
}
