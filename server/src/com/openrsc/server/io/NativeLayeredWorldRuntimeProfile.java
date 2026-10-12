package com.openrsc.server.io;

import java.util.Locale;

/** Ownership/capability selection, separate from explicit distribution review. */
public enum NativeLayeredWorldRuntimeProfile {
	NATIVE_ADDITIVE("native-additive", false),
	NATIVE_REPLACEMENT("native-replacement", true),
	FIXTURE_ADDITIVE("fixture-additive", false),
	PRESERVATION_R64_REPLACEMENT("preservation-r64-replacement", true),
	SPOILED_MILK_REPLACEMENT("spoiled-milk-replacement", true),
	SPOILED_MILK_BUILDER_DRAFT("spoiled-milk-builder-draft", true),
	SPOILED_MILK_WORLD_BUILDER_EXPORT("spoiled-milk-world-builder-export", true),
	// Historical spelling remains a compatibility alias for generic installation.
	SPOILED_MILK_EDITOR_INSTALLED("spoiled-milk-editor-installed", true),
	SPOILED_MILK_INSTALLED_REVIEW("spoiled-milk-installed-review", true);

	public static final String DEFAULT_ID = "native-additive";
	public static final String PRESERVATION_PACKAGE_ID =
		"rsc-remastered.preservation-r64-parity-review";
	public static final String PRESERVATION_PACKAGE_VERSION = "0.4.0";
	public static final String PRESERVATION_MANIFEST_SHA256 =
		"560dae205d13c2034b38f52d8bb6841ee56c245fadc8e9d18361ace1346cd73f";
	public static final String SPOILED_MILK_PACKAGE_ID =
		"rsc-remastered.spoiled-milk-layered-world";
	public static final String SPOILED_MILK_PACKAGE_VERSION = "0.5.0";
	public static final String SPOILED_MILK_MANIFEST_SHA256 =
		"9d59b96ca5a615b6830507e627a262022ff56ac464b0b37a14ec5e2fcfaa761e";

	private final String id;
	private final boolean replacement;
	NativeLayeredWorldRuntimeProfile(String id, boolean replacement) {
		this.id = id;
		this.replacement = replacement;
	}
	public static NativeLayeredWorldRuntimeProfile fromConfiguration(String requested) {
		String value = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
		if ("world-builder-installed".equals(value)) return SPOILED_MILK_EDITOR_INSTALLED;
		for (NativeLayeredWorldRuntimeProfile profile : values()) {
			if (profile.id.equals(value)) return profile;
		}
		throw new IllegalArgumentException("Unknown native layered world runtime profile: " + requested);
	}
	public String getId() { return id; }
	public boolean replacesLegacyBasePopulation() { return replacement; }
	public boolean skipsLegacyTerrainArchive() { return replacement; }
	public boolean requiresConfiguredManifestSha256() {
		return this == NATIVE_REPLACEMENT || this == SPOILED_MILK_EDITOR_INSTALLED
			|| this == SPOILED_MILK_INSTALLED_REVIEW || this == SPOILED_MILK_WORLD_BUILDER_EXPORT;
	}
	public boolean isGenericRuntime() {
		return this == NATIVE_ADDITIVE || this == NATIVE_REPLACEMENT || this == SPOILED_MILK_EDITOR_INSTALLED;
	}
	public void validate(NativeLayeredWorldPackageCatalog catalog) {
		NativeLayeredWorldRuntimeContract.validatePresentation(catalog);
		if (isGenericRuntime()) NativeLayeredWorldRuntimeContract.validate(catalog, replacement);
		else NativeLayeredWorldDistributionPolicy.validate(this, catalog);
	}
	public void validateConfiguredManifest(NativeLayeredWorldPackageCatalog catalog, String configured) {
		if (isGenericRuntime() || requiresConfiguredManifestSha256()) {
			NativeLayeredWorldRuntimeContract.validateConfiguredManifest(catalog, configured, requiresConfiguredManifestSha256());
		}
	}
}
