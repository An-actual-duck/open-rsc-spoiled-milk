package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Structural/capability checks without product identities or vanilla ID ceilings. */
public final class NativeLayeredWorldRuntimeContract {
	private NativeLayeredWorldRuntimeContract() {}

	static void validatePresentation(NativeLayeredWorldPackageCatalog catalog) {
		if (catalog == null) throw new IllegalArgumentException(
			"Native layered world runtime profile requires a package catalog");
		for (NativeLayeredWorldPackage loaded : catalog.getPackages()) {
			if (loaded.getPresentationChunkSize() != 24) throw new IllegalStateException(
				"Native layered runtime requires 24-tile presentation chunks");
		}
	}

	public static void validate(NativeLayeredWorldPackageCatalog catalog, boolean replacement) {
		validatePresentation(catalog);
		// Parsing already verifies declared scopes, supported encodings, finite
		// budgets, payload hashes, terrain for placements/roam, and catalog overlap.
		for (NativeLayeredWorldPackage loaded : catalog.getPackages()) {
			if (!loaded.getWorldSpaceKinds().keySet().equals(Collections.singleton("global"))
				|| !"static".equals(loaded.getWorldSpaceKinds().get("global"))) {
				throw new IllegalStateException("Unsupported runtime capability: current client requires global static world space");
			}
			if (loaded.getTerrainSectorCount() < 1) throw new IllegalStateException(
				"Native runtime requires at least one authored terrain sector per package");
			if (replacement) {
				Set<Integer> levels = new HashSet<Integer>();
				for (NativeLayeredPlacementSet set : loaded.getPlacementSets().values()) {
					if (!WorldSpaceId.GLOBAL.equals(set.getWorldSpace()) || !levels.add(set.getLevel())) {
						throw new IllegalStateException("Replacement requires exactly one explicit placement set per declared level");
					}
				}
				if (!levels.equals(loaded.getDeclaredLevels(WorldSpaceId.GLOBAL))) {
					throw new IllegalStateException("Replacement placement coverage is incomplete; declare empty sets for intentionally empty levels");
				}
			}
		}
	}

	public static void validateConfiguredManifest(NativeLayeredWorldPackageCatalog catalog,
		String configured, boolean required) {
		String value = configured == null ? "" : configured.trim();
		if (!required && value.isEmpty()) return;
		if (!value.matches("[0-9a-f]{64}")) throw new IllegalStateException(
			"Selected native runtime requires a valid configured manifest SHA-256");
		if (!value.equals(catalog.getPrimaryPackage().getManifestSha256())) throw new IllegalStateException(
			"Selected native package does not match the configured manifest SHA-256");
	}
}
