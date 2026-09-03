package orsc;

/** Resolves the sole pre-login terrain authority for adaptive and installed clients. */
public final class WorldBuilderTerrainBootstrap {
	private WorldBuilderTerrainBootstrap() {
	}

	public static boolean isNativeOnly() {
		return WorldBuilderClientProfile.current().isStrictAdaptiveTerrain()
			|| WorldBuilderInstalledClientProfile.current().isEnabled();
	}

	public static String mapIdentity() {
		if (WorldBuilderClientProfile.current().isStrictAdaptiveTerrain()) {
			return WorldBuilderClientProfile.current().strictAdaptiveMapIdentity();
		}
		return WorldBuilderInstalledClientProfile.current().mapIdentity();
	}
}
