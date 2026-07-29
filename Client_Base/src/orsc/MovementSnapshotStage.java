package orsc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MovementSnapshotStage {
	private MovementSnapshotTarget localTarget;
	private final Map<Integer, MovementSnapshotTarget> playerTargets = new HashMap<Integer, MovementSnapshotTarget>();
	private final Map<Integer, MovementSnapshotTarget> npcTargets = new HashMap<Integer, MovementSnapshotTarget>();
	private int protocolVersion = 0;
	private int serverTick = 0;
	private int sequence = 0;
	private int packets = 0;
	private int records = 0;
	private long lastPacketMillis = 0L;

	void reset() {
		localTarget = null;
		playerTargets.clear();
		npcTargets.clear();
		protocolVersion = 0;
		serverTick = 0;
		sequence = 0;
		packets = 0;
		records = 0;
		lastPacketMillis = 0L;
	}

	Result replaceFromSnapshot(Frame frame, mudclient mc) {
		if (frame == null) {
			return Result.waiting(packets);
		}
		localTarget = frame.localTarget;
		playerTargets.clear();
		npcTargets.clear();
		for (MovementSnapshotTarget target : frame.playerTargets) {
			playerTargets.put(target.serverIndex, target);
		}
		for (MovementSnapshotTarget target : frame.npcTargets) {
			npcTargets.put(target.serverIndex, target);
		}
		protocolVersion = frame.protocolVersion;
		serverTick = frame.serverTick;
		sequence = frame.sequence;
		packets++;
		records += frame.recordCount();
		lastPacketMillis = System.currentTimeMillis();
		return compareToVisibleCache(mc);
	}

	private Result compareToVisibleCache(mudclient mc) {
		Result result = new Result(
			true,
			protocolVersion,
			serverTick,
			sequence,
			packets,
			records,
			playerTargets.size(),
			npcTargets.size(),
			lastPacketMillis);
		if (localTarget != null) {
			compareTarget(result, mc, mc == null ? null : mc.getLocalPlayer(), localTarget, 0);
		}
		for (MovementSnapshotTarget target : playerTargets.values()) {
			compareTarget(result, mc, findVisiblePlayer(mc, target.serverIndex), target, 1);
		}
		for (MovementSnapshotTarget target : npcTargets.values()) {
			compareTarget(result, mc, findVisibleNpc(mc, target.serverIndex), target, 2);
		}
		return result;
	}

	private static ORSCharacter findVisiblePlayer(mudclient mc, int serverIndex) {
		if (mc == null) {
			return null;
		}
		for (int i = 0; i < mc.getPlayerCount(); i++) {
			ORSCharacter player = mc.getPlayer(i);
			if (player != null && player.serverIndex == serverIndex) {
				return player;
			}
		}
		return null;
	}

	private static ORSCharacter findVisibleNpc(mudclient mc, int serverIndex) {
		if (mc == null) {
			return null;
		}
		for (int i = 0; i < mc.getNpcCount(); i++) {
			ORSCharacter npc = mc.getNpc(i);
			if (npc != null && npc.serverIndex == serverIndex) {
				return npc;
			}
		}
		return null;
	}

	private static void compareTarget(Result result, mudclient mc, ORSCharacter character, MovementSnapshotTarget target, int category) {
		if (mc == null || character == null) {
			result.mismatches++;
			result.setFirstMismatch(target, category, "missing");
			return;
		}
		result.checked++;
		int waypointIndex = character.waypointIndexCurrent;
		int tileSize = Math.max(1, mc.getTileSize());
		int targetWorldX = mc.getMidRegionBaseX() + ((character.waypointsX[waypointIndex] - 64) / tileSize);
		int targetWorldZ = mc.getMidRegionBaseZ() + ((character.waypointsZ[waypointIndex] - 64) / tileSize);
		boolean positionMismatch = targetWorldX != target.worldX || targetWorldZ != target.worldZ;
		boolean directionMismatch = character.animationNext != target.direction;
		if (positionMismatch || directionMismatch) {
			result.mismatches++;
			String actual = targetWorldX + "," + targetWorldZ + ":" + character.animationNext;
			if (category == 2) {
				actual += " | " + mc.describeCustomNpcMovementDebug(target.serverIndex);
			}
			result.setFirstMismatch(target, category, actual);
		}
	}

	static final class Frame {
		private final int protocolVersion;
		private final int serverTick;
		private final int sequence;
		private MovementSnapshotTarget localTarget;
		private final List<MovementSnapshotTarget> playerTargets = new ArrayList<MovementSnapshotTarget>();
		private final List<MovementSnapshotTarget> npcTargets = new ArrayList<MovementSnapshotTarget>();

		Frame(int protocolVersion, int serverTick, int sequence) {
			this.protocolVersion = protocolVersion;
			this.serverTick = serverTick;
			this.sequence = sequence;
		}

		void setLocal(int worldX, int worldZ, int direction) {
			localTarget = new MovementSnapshotTarget(0, worldX, worldZ, direction);
		}

		void addPlayer(int serverIndex, int worldX, int worldZ, int direction) {
			playerTargets.add(new MovementSnapshotTarget(serverIndex, worldX, worldZ, direction));
		}

		void addNpc(int serverIndex, int worldX, int worldZ, int direction) {
			npcTargets.add(new MovementSnapshotTarget(serverIndex, worldX, worldZ, direction));
		}

		private int recordCount() {
			return (localTarget == null ? 0 : 1) + playerTargets.size() + npcTargets.size();
		}
	}

	static final class Result {
		final boolean ready;
		final int protocolVersion;
		final int serverTick;
		final int sequence;
		final int packets;
		final int records;
		final int players;
		final int npcs;
		final long lastPacketMillis;
		int checked = 0;
		int mismatches = 0;
		String firstMismatch = "";

		private Result(
			boolean ready,
			int protocolVersion,
			int serverTick,
			int sequence,
			int packets,
			int records,
			int players,
			int npcs,
			long lastPacketMillis) {
			this.ready = ready;
			this.protocolVersion = protocolVersion;
			this.serverTick = serverTick;
			this.sequence = sequence;
			this.packets = packets;
			this.records = records;
			this.players = players;
			this.npcs = npcs;
			this.lastPacketMillis = lastPacketMillis;
		}

		private static Result waiting(int packets) {
			return new Result(false, 0, 0, 0, packets, 0, 0, 0, 0L);
		}

		private void setFirstMismatch(MovementSnapshotTarget target, int category, String actual) {
			if (firstMismatch.length() != 0) {
				return;
			}
			String type = category == 0 ? "l" : (category == 1 ? "p" : "n");
			firstMismatch = type + "#" + target.serverIndex
				+ " e " + target.worldX + "," + target.worldZ + ":" + target.direction
				+ " a " + actual;
		}
	}

	private static final class MovementSnapshotTarget {
		private final int serverIndex;
		private final int worldX;
		private final int worldZ;
		private final int direction;

		private MovementSnapshotTarget(int serverIndex, int worldX, int worldZ, int direction) {
			this.serverIndex = serverIndex;
			this.worldX = worldX;
			this.worldZ = worldZ;
			this.direction = direction;
		}
	}
}
