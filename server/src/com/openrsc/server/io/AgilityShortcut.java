package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

/** Versioned, immutable behavior of one authored scenery placement, never its definition. */
public final class AgilityShortcut {
	public static final String TEMPLATE = "jump-v1", ACTION = "agility-jump";
	public static final int MAX_LEVEL = 99;
	private final int requiredLevel, startChancePercent, maxChancePercent, maxChanceLevel;
	private final WorldLocation successDestination, failureDestination;

	public AgilityShortcut(int required, int start, int maximum, int maximumLevel,
		WorldLocation success, WorldLocation failure) {
		if (required < 1 || required > MAX_LEVEL || maximumLevel < required || maximumLevel > MAX_LEVEL
			|| start < 0 || maximum > 100 || start > maximum || (maximumLevel == required && start != maximum)) {
			throw new IllegalArgumentException("Agility shortcut chance settings are inconsistent");
		}
		requiredLevel = required; startChancePercent = start; maxChancePercent = maximum; maxChanceLevel = maximumLevel;
		successDestination = destination(success); failureDestination = destination(failure);
	}
	private static WorldLocation destination(WorldLocation location) {
		WorldLocation checked = Objects.requireNonNull(location, "destination");
		int x = checked.getCoordinate().getX(), y = checked.getCoordinate().getY();
		if (!"global".equals(checked.getWorldSpace().getValue()) || x < 1 || y < 1 || x > 32766 || y > 32766)
			throw new IllegalArgumentException("Shortcut landing must be inside global authored terrain");
		return checked;
	}
	/** -1 means no attempt is allowed; otherwise an exact 0..10000 basis-point chance. */
	public int chanceBasisPoints(int effectiveLevel) {
		if (effectiveLevel < requiredLevel) return -1;
		int span = maxChanceLevel - requiredLevel;
		if (span == 0) return startChancePercent * 100;
		int progress = Math.min(effectiveLevel - requiredLevel, span);
		return startChancePercent * 100 + (maxChancePercent - startChancePercent) * 100 * progress / span;
	}
	public int getRequiredLevel() { return requiredLevel; }
	public WorldLocation getSuccessDestination() { return successDestination; }
	public WorldLocation getFailureDestination() { return failureDestination; }
	public static AgilityShortcut read(JSONObject value) throws IOException {
		try {
			keys(value, "schemaVersion", "template", "action", "requiredLevel", "startChancePercent", "maxChancePercent",
				"maxChanceLevel", "successDestination", "failureDestination");
			if (integer(value, "schemaVersion") != 1 || !TEMPLATE.equals(value.get("template")) || !ACTION.equals(value.get("action")))
				throw new IllegalArgumentException("Unsupported agility shortcut schema, template or action");
			return new AgilityShortcut(integer(value, "requiredLevel"), integer(value, "startChancePercent"),
				integer(value, "maxChancePercent"), integer(value, "maxChanceLevel"),
				readDestination(value.getJSONObject("successDestination")), readDestination(value.getJSONObject("failureDestination")));
		} catch (RuntimeException exception) { throw new IOException("Invalid agilityShortcut: " + exception.getMessage(), exception); }
	}
	private static WorldLocation readDestination(JSONObject value) {
		keys(value, "worldSpace", "level", "x", "y");
		if (!"global".equals(value.get("worldSpace"))) throw new IllegalArgumentException("Unsupported destination worldSpace");
		return WorldLocation.global(new WorldCoordinate(integer(value, "x"), integer(value, "y"), integer(value, "level")));
	}
	private static int integer(JSONObject value, String name) {
		Object number = value.get(name);
		if (!(number instanceof Integer) && !(number instanceof Long)) throw new IllegalArgumentException(name + " must be an integer");
		return Math.toIntExact(((Number) number).longValue());
	}
	private static void keys(JSONObject value, String... names) {
		if (!value.keySet().equals(new HashSet<String>(Arrays.asList(names)))) throw new IllegalArgumentException("Unexpected or missing shortcut fields");
	}
}
