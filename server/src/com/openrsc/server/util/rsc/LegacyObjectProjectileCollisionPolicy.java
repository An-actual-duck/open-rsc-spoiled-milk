package com.openrsc.server.util.rsc;

import java.util.Objects;

/** Pure extraction of the legacy dynamic projectile-clipping classifier. */
public final class LegacyObjectProjectileCollisionPolicy {
	private LegacyObjectProjectileCollisionPolicy() { }

	public static boolean allowsSceneryProjectileClip(
		final String name,
		final int width,
		final int height,
		final String[] allowedNames) {
		String checkedName = Objects.requireNonNull(name, "name");
		String[] checkedAllowedNames = Objects.requireNonNull(
			allowedNames, "allowedNames");
		String lowercaseName = checkedName.toLowerCase();
		if (lowercaseName.contains("tree")) {
			return true;
		}
		for (String allowedName : checkedAllowedNames) {
			String checkedAllowedName = Objects.requireNonNull(
				allowedName, "allowedNames entry");
			if (!checkedName.equalsIgnoreCase("tree")
				&& height == 1 && width == 1
				&& !lowercaseName.equalsIgnoreCase("chest")) {
				return true;
			}
			if (lowercaseName.equalsIgnoreCase(checkedAllowedName)) {
				return true;
			}
		}
		return false;
	}

	public static boolean allowsBoundaryProjectileClip(
		final String name,
		final String[] allowedNames) {
		String lowercaseName = Objects.requireNonNull(name, "name").toLowerCase();
		for (String allowedName : Objects.requireNonNull(
				allowedNames, "allowedNames")) {
			if (lowercaseName.equalsIgnoreCase(
					Objects.requireNonNull(
						allowedName, "allowedNames entry"))) {
				return true;
			}
		}
		return false;
	}
}
