package orsc;

/** Resolves the sole pre-login terrain authority for adaptive and installed clients. */
public final class WorldBuilderTerrainBootstrap {
	private static final class Resolved {
		private static final WorldBuilderTerrainBootstrap PLAN = resolve();
	}
	private final boolean nativeOnly;
	private final String identity;
	private final String provenance;

	private WorldBuilderTerrainBootstrap() {
		this(false, null, "classic");
	}

	private WorldBuilderTerrainBootstrap(boolean nativeOnly, String identity,
		String provenance) {
		this.nativeOnly = nativeOnly;
		this.identity = identity;
		this.provenance = provenance;
	}

	private static WorldBuilderTerrainBootstrap resolve() {
		WorldBuilderClientProfile adaptive = WorldBuilderClientProfile.current();
		WorldBuilderInstalledClientProfile installed = WorldBuilderInstalledClientProfile.current();
		boolean strict = adaptive.isStrictAdaptiveTerrain();
		if (strict) return new WorldBuilderTerrainBootstrap(true,
			adaptive.strictAdaptiveMapIdentity(), "strict-adaptive"
				+ (installed.isEnabled() ? " (installed profile not selected)" : ""));
		if (installed.isEnabled()) return new WorldBuilderTerrainBootstrap(true,
			installed.mapIdentity(), "installed-client");
		return new WorldBuilderTerrainBootstrap();
	}

	public static boolean isNativeOnly() {
		return Resolved.PLAN.nativeOnly;
	}

	public static String mapIdentity() {
		if (!Resolved.PLAN.nativeOnly) {
			throw new IllegalStateException("Classic client has no native map identity");
		}
		return Resolved.PLAN.identity;
	}

	public static String report() {
		return "client-map-source=" + Resolved.PLAN.provenance
			+ " identity=" + (Resolved.PLAN.nativeOnly ? Resolved.PLAN.identity : "legacy-archive");
	}
}
