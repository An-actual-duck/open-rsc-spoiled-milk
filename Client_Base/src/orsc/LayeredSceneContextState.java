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
	static final int SYNTHETIC_DEEP_PROTOCOL_VERSION = 2;
	static final int UNIFORM_NATIVE_LAYERED_PROTOCOL_VERSION =
		NativeLayeredTerrainSnapshot.UNIFORM_PAGE_PROTOCOL_VERSION;
	static final int NATIVE_LAYERED_PROTOCOL_VERSION =
		NativeLayeredTerrainSnapshot.PROTOCOL_VERSION;
	static final int LEVEL_STRIDE = 944;
	static final int LEGACY_PLANE_COUNT = 4;
	static final int MAX_LEGACY_X = Short.MAX_VALUE;
	static final String LEGACY_PROJECTION = "legacy-packed-y-v1";
	static final String SYNTHETIC_DEEP_PROJECTION =
		"synthetic-deep-fixture-v1";
	static final String NATIVE_LAYERED_PROJECTION =
		NativeLayeredTerrainSnapshot.PROJECTION_ID;
	static final int SYNTHETIC_DEEP_LEVEL = -2;
	static final int SYNTHETIC_DEEP_MIN_X = 440;
	static final int SYNTHETIC_DEEP_MAX_X = 460;
	static final int SYNTHETIC_DEEP_MIN_Y = 590;
	static final int SYNTHETIC_DEEP_MAX_Y = 610;

	private static final Pattern VALID_WORLD_SPACE =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final String GLOBAL_WORLD_SPACE = "global";

	private boolean established;
	private int protocolVersion;
	private int sequence;
	private int serverTick;
	private String worldSpace = "";
	private String projectionId = "";
	private int logicalX;
	private int logicalY;
	private int logicalLevel;
	private int legacyX;
	private int legacyY;
	private NativeLayeredTerrainSnapshot nativeTerrainSnapshot;
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
		return accept(
			incomingProtocolVersion,
			incomingSequence,
			incomingServerTick,
			incomingWorldSpace,
			LEGACY_PROJECTION,
			incomingLogicalX,
			incomingLogicalY,
			incomingLogicalLevel,
			incomingLegacyX,
			incomingLegacyY);
	}

	ApplyResult accept(
		int incomingProtocolVersion,
		int incomingSequence,
		int incomingServerTick,
		String incomingWorldSpace,
		String incomingProjectionId,
		int incomingLogicalX,
		int incomingLogicalY,
		int incomingLogicalLevel,
		int incomingLegacyX,
		int incomingLegacyY) {
		return acceptWithTerrain(
			incomingProtocolVersion,
			incomingSequence,
			incomingServerTick,
			incomingWorldSpace,
			incomingProjectionId,
			incomingLogicalX,
			incomingLogicalY,
			incomingLogicalLevel,
			incomingLegacyX,
			incomingLegacyY,
			null);
	}

	ApplyResult acceptNative(
		int incomingProtocolVersion,
		int incomingSequence,
		int incomingServerTick,
		String incomingWorldSpace,
		String incomingProjectionId,
		int incomingLogicalX,
		int incomingLogicalY,
		int incomingLogicalLevel,
		int incomingLegacyX,
		int incomingLegacyY,
		NativeLayeredTerrainSnapshot incomingTerrainSnapshot) {
		return acceptWithTerrain(
			incomingProtocolVersion,
			incomingSequence,
			incomingServerTick,
			incomingWorldSpace,
			incomingProjectionId,
			incomingLogicalX,
			incomingLogicalY,
			incomingLogicalLevel,
			incomingLegacyX,
			incomingLegacyY,
			incomingTerrainSnapshot);
	}

	private ApplyResult acceptWithTerrain(
		int incomingProtocolVersion,
		int incomingSequence,
		int incomingServerTick,
		String incomingWorldSpace,
		String incomingProjectionId,
		int incomingLogicalX,
		int incomingLogicalY,
		int incomingLogicalLevel,
		int incomingLegacyX,
		int incomingLegacyY,
		NativeLayeredTerrainSnapshot incomingTerrainSnapshot) {
		if (incomingProtocolVersion != PROTOCOL_VERSION
			&& incomingProtocolVersion
				!= SYNTHETIC_DEEP_PROTOCOL_VERSION
			&& incomingProtocolVersion
				!= UNIFORM_NATIVE_LAYERED_PROTOCOL_VERSION
			&& incomingProtocolVersion
				!= NATIVE_LAYERED_PROTOCOL_VERSION) {
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
		if (incomingProjectionId == null
			|| incomingProjectionId.length() == 0
			|| (incomingProtocolVersion == PROTOCOL_VERSION
				&& !LEGACY_PROJECTION.equals(incomingProjectionId))
			|| (incomingProtocolVersion
						== SYNTHETIC_DEEP_PROTOCOL_VERSION
					&& !SYNTHETIC_DEEP_PROJECTION.equals(
						incomingProjectionId))
			|| (isNativeProtocol(incomingProtocolVersion)
					&& !NATIVE_LAYERED_PROJECTION.equals(
						incomingProjectionId))) {
			throw new IllegalArgumentException(
				"Invalid layered scene-context projection: "
					+ incomingProjectionId);
		}
		if (isNativeProtocol(incomingProtocolVersion)) {
			if (incomingTerrainSnapshot == null
				|| incomingTerrainSnapshot.getProtocolVersion()
					!= incomingProtocolVersion
				|| !incomingTerrainSnapshot.covers(
					incomingWorldSpace,
					incomingLogicalLevel,
					incomingLogicalX,
					incomingLogicalY)) {
				throw new IllegalArgumentException(
					"Native layered scene-context has no terrain for its receipt");
			}
		} else if (incomingTerrainSnapshot != null) {
			throw new IllegalArgumentException(
				"Legacy layered scene-context cannot carry native terrain");
		}
		requireLegacyReceipt(
			incomingWorldSpace,
			incomingProjectionId,
			incomingLogicalX,
			incomingLogicalY,
			incomingLogicalLevel,
			incomingLegacyX,
			incomingLegacyY,
			incomingTerrainSnapshot);

		boolean scopeChanged = established
			&& (!worldSpace.equals(incomingWorldSpace)
				|| logicalLevel != incomingLogicalLevel
				|| !projectionId.equals(incomingProjectionId)
				|| !sameNativeTerrainScope(
					nativeTerrainSnapshot, incomingTerrainSnapshot));
		protocolVersion = incomingProtocolVersion;
		sequence = incomingSequence;
		serverTick = incomingServerTick;
		worldSpace = incomingWorldSpace;
		projectionId = incomingProjectionId;
		logicalX = incomingLogicalX;
		logicalY = incomingLogicalY;
		logicalLevel = incomingLogicalLevel;
		legacyX = incomingLegacyX;
		legacyY = incomingLegacyY;
		nativeTerrainSnapshot = incomingTerrainSnapshot;
		awaitingInitialReceipt = true;
		established = true;
		acceptedContexts++;
		if (scopeChanged) {
			scopeChanges++;
		}
		return new ApplyResult(
			scopeChanged,
			compatibilityPlane(logicalLevel, projectionId),
			SYNTHETIC_DEEP_PROJECTION.equals(projectionId),
			nativeTerrainSnapshot);
	}

	void acceptLegacyPlayerPosition(int packedX, int packedY) {
		requireEstablished();
		if (!GLOBAL_WORLD_SPACE.equals(worldSpace)) {
			throw new IllegalStateException(
				"Legacy Player position cannot represent world space: "
					+ worldSpace);
		}
		DecodedLegacyPosition decoded =
			decodeCompatibilityPosition(
				packedX,
				packedY,
				logicalLevel,
				projectionId,
				worldSpace,
				nativeTerrainSnapshot);
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
		if (LEGACY_PROJECTION.equals(projectionId)) {
			return worldSpace + ":" + logicalLevel + ":" + sequence;
		}
		if (nativeTerrainSnapshot != null) {
			return worldSpace + ":" + logicalLevel + ":"
				+ projectionId + ":"
				+ nativeTerrainSnapshot.scopeIdentity()
				+ ":" + sequence;
		}
		return worldSpace + ":" + logicalLevel + ":"
			+ projectionId + ":" + sequence;
	}

	String summary() {
		if (!established) {
			return "layer client waiting";
		}
		return "layer client " + worldSpace
			+ " " + logicalX + "," + logicalY + ",L" + logicalLevel
			+ " via " + projectionId
			+ " seq " + sequence
			+ " tick " + serverTick
			+ " legacy " + legacyX + "," + legacyY
			+ (nativeTerrainSnapshot == null
				? "" : " " + nativeTerrainSnapshot.summary())
			+ " contexts/positions/scopes "
			+ acceptedContexts + "/" + acceptedPlayerPositions + "/" + scopeChanges;
	}

	private static boolean sameNativeTerrainScope(
		NativeLayeredTerrainSnapshot left,
		NativeLayeredTerrainSnapshot right) {
		if (left == null || right == null) {
			return left == right;
		}
		if (left.getProtocolVersion()
				== NativeLayeredTerrainSnapshot.PROTOCOL_VERSION
			&& right.getProtocolVersion()
				== NativeLayeredTerrainSnapshot.PROTOCOL_VERSION) {
			return left.packageIdentity().equals(right.packageIdentity());
		}
		return left.equals(right);
	}

	private static boolean isNativeProtocol(int version) {
		return version == UNIFORM_NATIVE_LAYERED_PROTOCOL_VERSION
			|| version == NATIVE_LAYERED_PROTOCOL_VERSION;
	}

	void reset() {
		established = false;
		protocolVersion = 0;
		sequence = 0;
		serverTick = 0;
		worldSpace = "";
		projectionId = "";
		logicalX = 0;
		logicalY = 0;
		logicalLevel = 0;
		legacyX = 0;
		legacyY = 0;
		nativeTerrainSnapshot = null;
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
		String projectionId,
		int logicalX,
		int logicalY,
		int logicalLevel,
		int legacyX,
		int legacyY,
		NativeLayeredTerrainSnapshot nativeTerrainSnapshot) {
		if (!GLOBAL_WORLD_SPACE.equals(worldSpace)) {
			throw new IllegalArgumentException(
				"Legacy scene packets cannot represent world space: "
					+ worldSpace);
		}
		if (NATIVE_LAYERED_PROJECTION.equals(projectionId)) {
			if (nativeTerrainSnapshot == null
				|| !nativeTerrainSnapshot.covers(
					worldSpace, logicalLevel, logicalX, logicalY)
				|| legacyX != logicalX
				|| legacyY != logicalY) {
				throw new IllegalArgumentException(
					"Native layered scene-context receipt mismatch");
			}
			return;
		}
		if (SYNTHETIC_DEEP_PROJECTION.equals(projectionId)) {
			if (logicalLevel != SYNTHETIC_DEEP_LEVEL
				|| logicalX < SYNTHETIC_DEEP_MIN_X
				|| logicalX > SYNTHETIC_DEEP_MAX_X
				|| logicalY < SYNTHETIC_DEEP_MIN_Y
				|| logicalY > SYNTHETIC_DEEP_MAX_Y
				|| legacyX != logicalX
				|| legacyY != logicalY) {
				throw new IllegalArgumentException(
					"Synthetic deep scene-context receipt mismatch");
			}
			return;
		}
		if (!LEGACY_PROJECTION.equals(projectionId)) {
			throw new IllegalArgumentException(
				"Unsupported layered scene-context projection: "
					+ projectionId);
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

	private static DecodedLegacyPosition decodeCompatibilityPosition(
		int packedX,
		int packedY,
		int expectedLevel,
		String projectionId,
		String worldSpace,
		NativeLayeredTerrainSnapshot nativeTerrainSnapshot) {
		if (NATIVE_LAYERED_PROJECTION.equals(projectionId)) {
			if (nativeTerrainSnapshot == null
				|| !nativeTerrainSnapshot.covers(
					worldSpace, expectedLevel, packedX, packedY)) {
				throw new IllegalArgumentException(
					"Native layered movement left its terrain page");
			}
			return new DecodedLegacyPosition(
				packedX, packedY, expectedLevel);
		}
		if (SYNTHETIC_DEEP_PROJECTION.equals(projectionId)) {
			if (expectedLevel != SYNTHETIC_DEEP_LEVEL
				|| packedX < SYNTHETIC_DEEP_MIN_X
				|| packedX > SYNTHETIC_DEEP_MAX_X
				|| packedY < SYNTHETIC_DEEP_MIN_Y
				|| packedY > SYNTHETIC_DEEP_MAX_Y) {
				throw new IllegalArgumentException(
					"Synthetic deep movement left its compatibility bounds");
			}
			return new DecodedLegacyPosition(
				packedX, packedY, SYNTHETIC_DEEP_LEVEL);
		}
		return decodeLegacy(packedX, packedY);
	}

	private static int compatibilityPlane(
		int level,
		String projectionId) {
		if (NATIVE_LAYERED_PROJECTION.equals(projectionId)) {
			return 0;
		}
		if (SYNTHETIC_DEEP_PROJECTION.equals(projectionId)) {
			if (level != SYNTHETIC_DEEP_LEVEL) {
				throw new IllegalArgumentException(
					"Synthetic deep projection requires level -2");
			}
			return 0;
		}
		return legacyPlaneForLevel(level);
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
		private final boolean syntheticDeepFixture;
		private final NativeLayeredTerrainSnapshot nativeTerrainSnapshot;

		private ApplyResult(
			boolean scopeChanged,
			int legacyPlane,
			boolean syntheticDeepFixture,
			NativeLayeredTerrainSnapshot nativeTerrainSnapshot) {
			this.scopeChanged = scopeChanged;
			this.legacyPlane = legacyPlane;
			this.syntheticDeepFixture = syntheticDeepFixture;
			this.nativeTerrainSnapshot = nativeTerrainSnapshot;
		}

		boolean isScopeChanged() {
			return scopeChanged;
		}

		int getLegacyPlane() {
			return legacyPlane;
		}

		boolean isSyntheticDeepFixture() {
			return syntheticDeepFixture;
		}

		NativeLayeredTerrainSnapshot getNativeTerrainSnapshot() {
			return nativeTerrainSnapshot;
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
