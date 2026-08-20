package com.openrsc.server.model;

import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.external.DoorDef;

import java.util.Locale;

/**
 * Classifies scenery under the combat-projectile cover contract.
 *
 * <p>Movement-blocking scenery is transparent by default. Structural walls
 * and closed doors/gates block every combat projectile; fence, palisade, and
 * railing forms block enemy projectiles but are transparent to player-allied
 * attacks.</p>
 */
public final class CombatProjectileCollision {
	public enum Cover { NONE, STRUCTURAL, ENEMY_ONLY_FENCE }
	private CombatProjectileCollision() {
	}

	public static boolean blocksScenery(final GameObjectDef definition) {
		return sceneryCover(definition) != Cover.NONE;
	}

	public static Cover sceneryCover(final GameObjectDef definition) {
		if (definition == null) {
			return Cover.NONE;
		}

		final String name = normalize(definition.getName());
		final String description = normalize(definition.getDescription());
		final String model = normalize(definition.getObjectModel());

		if (isFence(name, description)) {
			return Cover.ENEMY_ONLY_FENCE;
		}

		if (isStructuralWallName(name)) {
			return Cover.STRUCTURAL;
		}

		if (!isDoorOrGate(name, model)) {
			return Cover.NONE;
		}

		return isOpenDoorOrGate(definition, description, model)
			? Cover.NONE : Cover.STRUCTURAL;
	}

	public static Cover boundaryCover(final DoorDef definition) {
		if (definition == null || definition.getDoorType() != 1) {
			return Cover.NONE;
		}
		final String name = normalize(definition.getName());
		final String description = normalize(definition.getDescription());
		return isFence(name, description)
			? Cover.ENEMY_ONLY_FENCE : Cover.STRUCTURAL;
	}

	private static boolean isFence(
			final String name, final String description) {
		return containsWord(name, "fence")
			|| containsWord(description, "fence")
			|| containsWord(name, "palisade")
			|| containsWord(description, "palisade")
			|| name.contains("railing")
			|| description.contains("railing");
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
