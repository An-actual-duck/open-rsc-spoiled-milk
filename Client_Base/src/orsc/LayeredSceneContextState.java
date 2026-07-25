package orsc;

import java.util.regex.Pattern;

/**
 * Client-owned world-space/level identity for legacy-compatible scene packets.
 *
 * The scene context establishes a scope. Absolute legacy Player coordinates
 * then advance X/Y inside that scope through a checked compatibility codec.
 */
final class LayeredSceneContextState {
	static final int PROTOCOL_VERSION = 1;
	static final int LEVEL_STRIDE = 944;
	static final int LEGACY_PLANE_COUNT = 4;
	static final int MAX_LEGACY_X = Short.MAX_VALUE;

	private static final Pattern VALID_WORLD_SPACE =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final String GLOBAL_WORLD_SPACE = "global";

	private boolean established;
	private int protocolVersion;
	private int sequence;
	private int serverTick;
	private String worldSpace = "";
	private int logicalX;
	private int logicalY;
	private int logicalLevel;
	private int legacyX;
	private int legacyY;
	private boolean awaitingInitialReceipt;
	private int acceptedContexts;
	private int acceptedPlayerPositions;
	private int scopeChanges;

	ApplyResult accept(
		int incomingProtocolVersion,
		int incomingSequence,
		int incomingServerTick,
		String incomingWorldSpace,
		int incomingLogicalX,
		int incomingLogicalY,
		int incomingLogicalLevel,
		int incomingLegacyX,
		int incomingLegacyY) {
		if (incomingProtocolVersion != PROTOCOL_VERSION) {
			throw new IllegalArgumentException(
				"Unsupported layered scene-context protocol: "
					+ incomingProtocolVersion);
		}
		if (incomingSequence <= 0
			|| established && incomingSequence <= sequence) {
			throw new IllegalStateException(
				"Stale layered scene-context sequence: "
					+ incomingSequence + " after " + sequence);
		}
		if (incomingWorldSpace == null
			|| !VALID_WORLD_SPACE.matcher(incomingWorldSpace).matches()) {
			throw new IllegalArgumentException(
				"Invalid layered scene-context world space: "
					+ incomingWorldSpace);
		}
		requireLegacyReceipt(
			incomingWorldSpace,
			incomingLogicalX,
			incomingLogicalY,
			incomingLogicalLevel,
			incomingLegacyX,
			incomingLegacyY);

		boolean scopeChanged = established
			&& (!worldSpace.equals(incomingWorldSpace)
				|| logicalLevel != incomingLogicalLevel);
		protocolVersion = incomingProtocolVersion;
		sequence = incomingSequence;
		serverTick = incomingServerTick;
		worldSpace = incomingWorldSpace;
		logicalX = incomingLogicalX;
		logicalY = incomingLogicalY;
		logicalLevel = incomingLogicalLevel;
		legacyX = incomingLegacyX;
		legacyY = incomingLegacyY;
		awaitingInitialReceipt = true;
		established = true;
		acceptedContexts++;
		if (scopeChanged) {
			scopeChanges++;
		}
		return new ApplyResult(scopeChanged, legacyPlaneForLevel(logicalLevel));
	}

	void acceptLegacyPlayerPosition(int packedX, int packedY) {
		requireEstablished();
		if (!GLOBAL_WORLD_SPACE.equals(worldSpace)) {
			throw new IllegalStateException(
				"Legacy Player position cannot represent world space: "
					+ worldSpace);
		}
		DecodedLegacyPosition decoded = decodeLegacy(packedX, packedY);
		if (decoded.level != logicalLevel) {
			throw new IllegalStateException(
				"Legacy Player position changed layered level without context: "
					+ decoded.level + " != " + logicalLevel);
		}
		if (awaitingInitialReceipt
			&& (packedX != legacyX
				|| packedY != legacyY
				|| decoded.x != logicalX
				|| decoded.y != logicalY)) {
			throw new IllegalStateException(
				"First legacy Player position disagrees with layered context");
		}
		logicalX = decoded.x;
		logicalY = decoded.y;
		legacyX = packedX;
		legacyY = packedY;
		awaitingInitialReceipt = false;
		acceptedPlayerPositions++;
	}

	boolean matchesSequence(int expectedSequence) {
		return established && sequence == expectedSequence;
	}

	boolean hasContext() {
		return established;
	}

	int getSequence() {
		return established ? sequence : 0;
	}

	int getLogicalLevel() {
		requireEstablished();
		return logicalLevel;
	}

	String scopeIdentity() {
		if (!established) {
			return "none";
		}
		return worldSpace + ":" + logicalLevel + ":" + sequence;
	}

	String summary() {
		if (!established) {
			return "layer client waiting";
		}
		return "layer client " + worldSpace
			+ " " + logicalX + "," + logicalY + ",L" + logicalLevel
			+ " seq " + sequence
			+ " tick " + serverTick
			+ " legacy " + legacyX + "," + legacyY
			+ " contexts/positions/scopes "
			+ acceptedContexts + "/" + acceptedPlayerPositions + "/" + scopeChanges;
	}

	void reset() {
		established = false;
		protocolVersion = 0;
		sequence = 0;
		serverTick = 0;
		worldSpace = "";
		logicalX = 0;
		logicalY = 0;
		logicalLevel = 0;
		legacyX = 0;
		legacyY = 0;
		awaitingInitialReceipt = false;
		acceptedContexts = 0;
		acceptedPlayerPositions = 0;
		scopeChanges = 0;
	}

	private void requireEstablished() {
		if (!established) {
			throw new IllegalStateException(
				"Layered scene context has not been established");
		}
	}

	private static void requireLegacyReceipt(
		String worldSpace,
		int logicalX,
		int logicalY,
		int logicalLevel,
		int legacyX,
		int legacyY) {
		if (!GLOBAL_WORLD_SPACE.equals(worldSpace)) {
			throw new IllegalArgumentException(
				"Legacy scene packets cannot represent world space: "
					+ worldSpace);
		}
		if (logicalX < 0 || logicalX > MAX_LEGACY_X) {
			throw new IllegalArgumentException(
				"Layered X is outside legacy range: " + logicalX);
		}
		if (logicalY < 0 || logicalY >= LEVEL_STRIDE) {
			throw new IllegalArgumentException(
				"Layered Y is outside legacy range: " + logicalY);
		}
		int expectedLegacyY = Math.addExact(
			Math.multiplyExact(legacyPlaneForLevel(logicalLevel), LEVEL_STRIDE),
			logicalY);
		if (legacyX != logicalX || legacyY != expectedLegacyY) {
			throw new IllegalArgumentException(
				"Layered scene-context legacy receipt mismatch");
		}
	}

	private static DecodedLegacyPosition decodeLegacy(int packedX, int packedY) {
		if (packedX < 0 || packedX > MAX_LEGACY_X) {
			throw new IllegalArgumentException(
				"Packed X is outside legacy range: " + packedX);
		}
		int maxPackedY = LEVEL_STRIDE * LEGACY_PLANE_COUNT - 1;
		if (packedY < 0 || packedY > maxPackedY) {
			throw new IllegalArgumentException(
				"Packed Y is outside legacy range: " + packedY);
		}
		int plane = Math.floorDiv(packedY, LEVEL_STRIDE);
		return new DecodedLegacyPosition(
			packedX,
			Math.floorMod(packedY, LEVEL_STRIDE),
			levelForLegacyPlane(plane));
	}

	private static int legacyPlaneForLevel(int level) {
		switch (level) {
			case 0:
				return 0;
			case 1:
				return 1;
			case 2:
				return 2;
			case -1:
				return 3;
			default:
				throw new IllegalArgumentException(
					"Layered level cannot use legacy scene packets: " + level);
		}
	}

	private static int levelForLegacyPlane(int plane) {
		switch (plane) {
			case 0:
				return 0;
			case 1:
				return 1;
			case 2:
				return 2;
			case 3:
				return -1;
			default:
				throw new IllegalArgumentException(
					"Unsupported legacy plane: " + plane);
		}
	}

	static final class ApplyResult {
		private final boolean scopeChanged;
		private final int legacyPlane;

		private ApplyResult(boolean scopeChanged, int legacyPlane) {
			this.scopeChanged = scopeChanged;
			this.legacyPlane = legacyPlane;
		}

		boolean isScopeChanged() {
			return scopeChanged;
		}

		int getLegacyPlane() {
			return legacyPlane;
		}
	}

	private static final class DecodedLegacyPosition {
		private final int x;
		private final int y;
		private final int level;

		private DecodedLegacyPosition(int x, int y, int level) {
			this.x = x;
			this.y = y;
			this.level = level;
		}
	}
}
