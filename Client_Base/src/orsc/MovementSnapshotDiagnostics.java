package orsc;

/**
 * Owns movement-update/snapshot comparison state and diagnostic presentation.
 * Packet decoding and client movement mutations remain in {@link PacketHandler}.
 */
final class MovementSnapshotDiagnostics {
	private final PacketDebugState packetDebugState = new PacketDebugState();
	private final SnapshotDebugState snapshotDebugState = new SnapshotDebugState();

	Fingerprint createFingerprint(int localX, int localZ, int localDirection) {
		return new Fingerprint(localX, localZ, localDirection);
	}

	CacheParity createCacheParity() {
		return new CacheParity();
	}

	void recordMovementUpdate(Fingerprint fingerprint) {
		packetDebugState.recordMovementUpdate(fingerprint);
	}

	void recordSnapshot(
		int protocolVersion,
		int serverTick,
		int sequence,
		int localX,
		int localZ,
		int localDirection,
		int playerCount,
		int npcCount,
		int recordsRead,
		CacheParity cacheParity,
		Fingerprint snapshotFingerprint,
		MovementSnapshotStage.Result stageResult) {
		snapshotDebugState.recordPacket(
			protocolVersion,
			serverTick,
			sequence,
			localX,
			localZ,
			localDirection,
			playerCount,
			npcCount,
			recordsRead,
			cacheParity,
			packetDebugState.compareSnapshot(snapshotFingerprint),
			stageResult);
	}

	String[] summaryLines() {
		return snapshotDebugState.summaryLines();
	}

	static final class Fingerprint {
		private final int localX;
		private final int localZ;
		private final int localDirection;
		private int playerCount = 0;
		private int npcCount = 0;
		private long hash = 1469598103934665603L;

		private Fingerprint(int localX, int localZ, int localDirection) {
			this.localX = localX;
			this.localZ = localZ;
			this.localDirection = localDirection;
			this.hash = mixRecord(this.hash, 0, 0, localX, localZ, localDirection);
		}

		void addPlayer(int serverIndex, int worldX, int worldZ, int direction) {
			playerCount++;
			hash = mixRecord(hash, 1, serverIndex, worldX, worldZ, direction);
		}

		void addNpc(int serverIndex, int worldX, int worldZ, int direction) {
			npcCount++;
			hash = mixRecord(hash, 2, serverIndex, worldX, worldZ, direction);
		}

		private boolean matches(Fingerprint other) {
			return other != null
				&& localX == other.localX
				&& localZ == other.localZ
				&& localDirection == other.localDirection
				&& playerCount == other.playerCount
				&& npcCount == other.npcCount
				&& hash == other.hash;
		}

		private static long mixRecord(long hash, int kind, int serverIndex, int worldX, int worldZ, int direction) {
			hash = mix(hash, kind);
			hash = mix(hash, serverIndex);
			hash = mix(hash, worldX);
			hash = mix(hash, worldZ);
			return mix(hash, direction);
		}

		private static long mix(long hash, int value) {
			hash ^= value & 0xffffffffL;
			return hash * 1099511628211L;
		}
	}

	static final class CacheParity {
		private int checkedLocal = 0;
		private int checkedPlayers = 0;
		private int checkedNpcs = 0;
		private int missingLocal = 0;
		private int missingPlayers = 0;
		private int missingNpcs = 0;
		private int localPositionMismatches = 0;
		private int playerPositionMismatches = 0;
		private int npcPositionMismatches = 0;
		private int localDirectionMismatches = 0;
		private int playerDirectionMismatches = 0;
		private int npcDirectionMismatches = 0;
		private String firstMismatch = "";

		void checkLocal(mudclient mc, int worldX, int worldZ, int direction) {
			if (mc == null || mc.getLocalPlayer() == null) {
				missingLocal++;
				return;
			}
			checkedLocal++;
			checkCharacter(mc, mc.getLocalPlayer(), worldX, worldZ, direction, 0, mc.getLocalPlayer().serverIndex);
		}

		void checkPlayer(mudclient mc, int serverIndex, int worldX, int worldZ, int direction) {
			if (mc == null) {
				missingPlayers++;
				return;
			}
			ORSCharacter player = findVisiblePlayer(mc, serverIndex);
			if (player == null) {
				missingPlayers++;
				return;
			}
			checkedPlayers++;
			checkCharacter(mc, player, worldX, worldZ, direction, 1, serverIndex);
		}

		void checkNpc(mudclient mc, int serverIndex, int worldX, int worldZ, int direction) {
			if (mc == null) {
				missingNpcs++;
				return;
			}
			ORSCharacter npc = findVisibleNpc(mc, serverIndex);
			if (npc == null) {
				missingNpcs++;
				return;
			}
			checkedNpcs++;
			checkCharacter(mc, npc, worldX, worldZ, direction, 2, serverIndex);
		}

		private ORSCharacter findVisiblePlayer(mudclient mc, int serverIndex) {
			for (int i = 0; i < mc.getPlayerCount(); i++) {
				ORSCharacter player = mc.getPlayer(i);
				if (player != null && player.serverIndex == serverIndex) {
					return player;
				}
			}
			return null;
		}

		private ORSCharacter findVisibleNpc(mudclient mc, int serverIndex) {
			for (int i = 0; i < mc.getNpcCount(); i++) {
				ORSCharacter npc = mc.getNpc(i);
				if (npc != null && npc.serverIndex == serverIndex) {
					return npc;
				}
			}
			return null;
		}

		private void checkCharacter(
			mudclient mc,
			ORSCharacter character,
			int worldX,
			int worldZ,
			int direction,
			int category,
			int serverIndex) {
			int waypointIndex = character.waypointIndexCurrent;
			int tileSize = Math.max(1, mc.getTileSize());
			int targetWorldX = mc.getMidRegionBaseX() + ((character.waypointsX[waypointIndex] - 64) / tileSize);
			int targetWorldZ = mc.getMidRegionBaseZ() + ((character.waypointsZ[waypointIndex] - 64) / tileSize);
			boolean positionMismatch = targetWorldX != worldX || targetWorldZ != worldZ;
			boolean directionMismatch = character.animationNext != direction;
			if (positionMismatch) {
				if (category == 0) {
					localPositionMismatches++;
				} else if (category == 1) {
					playerPositionMismatches++;
				} else {
					npcPositionMismatches++;
				}
			}
			if (directionMismatch) {
				if (category == 0) {
					localDirectionMismatches++;
				} else if (category == 1) {
					playerDirectionMismatches++;
				} else {
					npcDirectionMismatches++;
				}
			}
			if ((positionMismatch || directionMismatch) && firstMismatch.length() == 0) {
				String type = category == 0 ? "l" : (category == 1 ? "p" : "n");
				firstMismatch = type + "#" + serverIndex
					+ " e " + worldX + "," + worldZ + ":" + direction
					+ " a " + targetWorldX + "," + targetWorldZ + ":" + character.animationNext;
				if (category == 2) {
					firstMismatch += " | " + mc.describeCustomNpcMovementDebug(serverIndex);
				}
			}
		}

		private int checked() {
			return checkedLocal + checkedPlayers + checkedNpcs;
		}

		private int missing() {
			return missingLocal + missingPlayers + missingNpcs;
		}

		private int positionMismatches() {
			return localPositionMismatches + playerPositionMismatches + npcPositionMismatches;
		}

		private int directionMismatches() {
			return localDirectionMismatches + playerDirectionMismatches + npcDirectionMismatches;
		}

		private int mismatches() {
			return missing() + positionMismatches() + directionMismatches();
		}
	}

	private static final class PacketDebugState {
		private Fingerprint lastMovementUpdate;
		private int movementUpdates = 0;

		private void recordMovementUpdate(Fingerprint fingerprint) {
			lastMovementUpdate = fingerprint;
			movementUpdates++;
		}

		private WireParity compareSnapshot(Fingerprint snapshotFingerprint) {
			if (snapshotFingerprint == null || lastMovementUpdate == null) {
				return WireParity.waiting(movementUpdates);
			}
			return WireParity.compared(movementUpdates, lastMovementUpdate.matches(snapshotFingerprint));
		}
	}

	private static final class WireParity {
		private final boolean hasReference;
		private final boolean matched;
		private final int movementUpdates;

		private WireParity(boolean hasReference, boolean matched, int movementUpdates) {
			this.hasReference = hasReference;
			this.matched = matched;
			this.movementUpdates = movementUpdates;
		}

		private static WireParity waiting(int movementUpdates) {
			return new WireParity(false, false, movementUpdates);
		}

		private static WireParity compared(int movementUpdates, boolean matched) {
			return new WireParity(true, matched, movementUpdates);
		}
	}

	private static final class SnapshotDebugState {
		private static final int RECENT_MOVE_CACHE_LOG_LIMIT = 5;
		private static final long MOVE_CACHE_LOG_INTERVAL_MILLIS = 10000L;
		private static final String MOVE_CACHE_LOG_PREFIX = "MOVEMENT_CACHE_RECENT";

		private int protocolVersion = 0;
		private int serverTick = 0;
		private int sequence = 0;
		private int localX = 0;
		private int localZ = 0;
		private int localDirection = 0;
		private int playerCount = 0;
		private int npcCount = 0;
		private int packets = 0;
		private int records = 0;
		private int cacheChecked = 0;
		private int parityMissingLocal = 0;
		private int parityMissingPlayers = 0;
		private int parityMissingNpcs = 0;
		private int localPositionMismatches = 0;
		private int playerPositionMismatches = 0;
		private int npcPositionMismatches = 0;
		private int localDirectionMismatches = 0;
		private int playerDirectionMismatches = 0;
		private int npcDirectionMismatches = 0;
		private int parityTotalMismatches = 0;
		private String cacheMismatchSample = "";
		private boolean wireHasReference = false;
		private boolean wireMatched = false;
		private int wireMovementUpdates = 0;
		private int wireTotalMismatches = 0;
		private boolean stageReady = false;
		private int stagePlayers = 0;
		private int stageNpcs = 0;
		private int stageChecked = 0;
		private int stageCurrentMismatches = 0;
		private int stageTotalMismatches = 0;
		private String stageMismatchSample = "";
		private long stageLastPacketMillis = 0L;
		private long lastPacketMillis = 0L;
		private final String[] recentMoveCacheLines = new String[RECENT_MOVE_CACHE_LOG_LIMIT];
		private int recentMoveCacheNext = 0;
		private int recentMoveCacheCount = 0;
		private int lastLoggedMismatchSequence = Integer.MIN_VALUE;
		private long lastLoggedMismatchMillis = Long.MIN_VALUE;

		private void recordPacket(
			int protocolVersion,
			int serverTick,
			int sequence,
			int localX,
			int localZ,
			int localDirection,
			int playerCount,
			int npcCount,
			int recordsRead,
			CacheParity parity,
			WireParity wireParity,
			MovementSnapshotStage.Result stageResult) {
			this.protocolVersion = protocolVersion;
			this.serverTick = serverTick;
			this.sequence = sequence;
			this.localX = localX;
			this.localZ = localZ;
			this.localDirection = localDirection;
			this.playerCount = playerCount;
			this.npcCount = npcCount;
			this.packets++;
			this.records += recordsRead;
			this.cacheChecked = parity.checked();
			this.parityMissingLocal = parity.missingLocal;
			this.parityMissingPlayers = parity.missingPlayers;
			this.parityMissingNpcs = parity.missingNpcs;
			this.localPositionMismatches = parity.localPositionMismatches;
			this.playerPositionMismatches = parity.playerPositionMismatches;
			this.npcPositionMismatches = parity.npcPositionMismatches;
			this.localDirectionMismatches = parity.localDirectionMismatches;
			this.playerDirectionMismatches = parity.playerDirectionMismatches;
			this.npcDirectionMismatches = parity.npcDirectionMismatches;
			this.parityTotalMismatches += parity.mismatches();
			this.cacheMismatchSample = parity.firstMismatch;
			this.wireHasReference = wireParity.hasReference;
			this.wireMatched = wireParity.matched;
			this.wireMovementUpdates = wireParity.movementUpdates;
			if (wireParity.hasReference && !wireParity.matched) {
				this.wireTotalMismatches++;
			}
			this.stageReady = stageResult.ready;
			this.stagePlayers = stageResult.players;
			this.stageNpcs = stageResult.npcs;
			this.stageChecked = stageResult.checked;
			this.stageCurrentMismatches = stageResult.mismatches;
			this.stageTotalMismatches += stageResult.mismatches;
			this.stageMismatchSample = stageResult.firstMismatch;
			this.stageLastPacketMillis = stageResult.lastPacketMillis;
			this.lastPacketMillis = System.currentTimeMillis();
			rememberMoveCacheLine(buildRecentMoveCacheLine(this.lastPacketMillis));
			if ((parity.mismatches() > 0 || stageResult.mismatches > 0)
				&& this.lastLoggedMismatchSequence != sequence
				&& (this.lastLoggedMismatchMillis == Long.MIN_VALUE
					|| this.lastPacketMillis
						- this.lastLoggedMismatchMillis
						>= MOVE_CACHE_LOG_INTERVAL_MILLIS)) {
				this.lastLoggedMismatchSequence = sequence;
				this.lastLoggedMismatchMillis = this.lastPacketMillis;
				logRecentMoveCacheLines();
			}
		}

		private String[] summaryLines() {
			if (packets <= 0) {
				return new String[] { "move snap waiting", "move cache waiting" };
			}
			long now = System.currentTimeMillis();
			String wireSummary = buildWireSummary();
			String cacheSummary = buildCacheSummary();
			String stageSummary = buildStageSummary(now);
			return new String[] {
				"move snap v" + protocolVersion
					+ " tick/seq " + serverTick + "/" + sequence
					+ " | local " + localX + "," + localZ + ":" + localDirection
					+ " | p/n " + playerCount + "/" + npcCount
					+ " | packets/records " + packets + "/" + records
					+ " | " + wireSummary
					+ " | age " + (System.currentTimeMillis() - lastPacketMillis) + "ms",
				"move cache " + cacheSummary + " | " + stageSummary
			};
		}

		private int currentCacheIssues() {
			return parityMissingLocal + parityMissingPlayers + parityMissingNpcs
				+ localPositionMismatches + playerPositionMismatches + npcPositionMismatches
				+ localDirectionMismatches + playerDirectionMismatches + npcDirectionMismatches;
		}

		private String buildWireSummary() {
			return !wireHasReference
				? "wire wait u" + wireMovementUpdates
				: (wireMatched ? "wire ok" : "wire bad total " + wireTotalMismatches);
		}

		private String buildCacheSummary() {
			int cacheIssues = currentCacheIssues();
			if (cacheIssues == 0) {
				return "cache ok c" + cacheChecked;
			}
			return "cache bad c" + cacheChecked
				+ " miss " + parityMissingLocal + "/" + parityMissingPlayers + "/" + parityMissingNpcs
				+ " pos " + localPositionMismatches + "/" + playerPositionMismatches + "/" + npcPositionMismatches
				+ " dir " + localDirectionMismatches + "/" + playerDirectionMismatches + "/" + npcDirectionMismatches
				+ " total " + parityTotalMismatches
				+ (cacheMismatchSample.length() == 0 ? "" : " | " + cacheMismatchSample);
		}

		private String buildStageSummary(long now) {
			if (!stageReady) {
				return "stage wait";
			}
			return "stage " + (stageCurrentMismatches == 0 ? "ok" : "bad")
				+ " c" + stageChecked
				+ " p/n " + stagePlayers + "/" + stageNpcs
				+ (stageCurrentMismatches == 0 ? "" : " total " + stageCurrentMismatches + "/" + stageTotalMismatches)
				+ " age " + (now - stageLastPacketMillis) + "ms"
				+ (stageMismatchSample.length() == 0 ? "" : " | " + stageMismatchSample);
		}

		private String buildRecentMoveCacheLine(long now) {
			return "seq " + sequence
				+ " tick " + serverTick
				+ " local " + localX + "," + localZ + ":" + localDirection
				+ " p/n " + playerCount + "/" + npcCount
				+ " packets/records " + packets + "/" + records
				+ " | " + buildWireSummary()
				+ " | " + buildCacheSummary()
				+ " | " + buildStageSummary(now);
		}

		private void rememberMoveCacheLine(String line) {
			recentMoveCacheLines[recentMoveCacheNext] = line;
			recentMoveCacheNext = (recentMoveCacheNext + 1) % RECENT_MOVE_CACHE_LOG_LIMIT;
			if (recentMoveCacheCount < RECENT_MOVE_CACHE_LOG_LIMIT) {
				recentMoveCacheCount++;
			}
		}

		private void logRecentMoveCacheLines() {
			logMoveCacheLine(MOVE_CACHE_LOG_PREFIX
				+ " latest mismatch; last " + recentMoveCacheCount + " move cache snapshots:");
			for (int i = 0; i < recentMoveCacheCount; i++) {
				int index = recentMoveCacheNext - recentMoveCacheCount + i;
				if (index < 0) {
					index += RECENT_MOVE_CACHE_LOG_LIMIT;
				}
				logMoveCacheLine(MOVE_CACHE_LOG_PREFIX + " " + recentMoveCacheLines[index]);
			}
		}

		private void logMoveCacheLine(String line) {
			System.out.println(line);
			ClientRuntimeLogger.log(line);
		}
	}
}
