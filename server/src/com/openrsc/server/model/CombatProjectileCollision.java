package com.openrsc.server.model;

import com.openrsc.server.external.GameObjectDef;

import java.util.Locale;

/**
 * Classifies scenery that is hard cover for combat projectiles.
 *
 * <p>Movement-blocking scenery is intentionally not hard cover by default:
 * rocks, trees, and similar objects are transparent. Structural
 * walls, closed doors/gates, and every fence form are hard cover.</p>
 */
public final class CombatProjectileCollision {
	private CombatProjectileCollision() {
	}

	public static boolean blocksScenery(final GameObjectDef definition) {
		if (definition == null) {
			return false;
		}

		final String name = normalize(definition.getName());
		final String description = normalize(definition.getDescription());
		final String model = normalize(definition.getObjectModel());

		if (containsWord(name, "fence")
			|| containsWord(description, "fence")
			|| containsWord(name, "palisade")
			|| containsWord(description, "palisade")) {
			return true;
		}

		if (isStructuralWallName(name)) {
			return true;
		}

		if (!isDoorOrGate(name, model)) {
			return false;
		}

		return !isOpenDoorOrGate(definition, description, model);
	}

	private static boolean isStructuralWallName(final String name) {
		return containsWord(name, "wall") || "spearwall".equals(name);
	}

	private static boolean isDoorOrGate(final String name, final String model) {
		return containsWord(name, "door")
			|| containsWord(name, "doors")
			|| containsWord(name, "gate")
			|| containsWord(name, "gates")
			|| model.contains("door")
			|| model.contains("gate");
	}

	private static boolean isOpenDoorOrGate(final GameObjectDef definition, final String description,
											final String model) {
		final String command1 = normalize(definition.command1);
		final String command2 = normalize(definition.command2);
		return "close".equals(command1)
			|| "close".equals(command2)
			|| description.contains(" is open")
			|| description.contains(" are open")
			|| model.contains("open");
	}

	private static boolean containsWord(final String value, final String word) {
		if (value.isEmpty()) {
			return false;
		}
		for (final String token : value.split("[^a-z0-9]+")) {
			if (word.equals(token)) {
				return true;
			}
		}
		return false;
	}

	private static String normalize(final String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
