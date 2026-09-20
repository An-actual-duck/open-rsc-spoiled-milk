package orsc;

/** Pose-only NPC effect codes, mirrored by server DarkBeastCombat. */
public final class DarkBeastChargePose {
	private DarkBeastChargePose() { }
	public static boolean isPose(ORSCharacter npc,int code) {
		return npc != null && npc.npcId == 869 && code >= 78 && code <= 81;
	}
	public static boolean apply(ORSCharacter npc,int code,long now) {
		if (!isPose(npc,code)) return false;
		npc.darkBeastPose = Math.min(2,code-78);
		// Pulses refreshed by the server each tick. Grace covers packet jitter;
		// explicit cancellation prevents stale charge art, expiry is a fallback.
		npc.darkBeastPoseExpiresMillis = code==81 ? 0 : now+(code==80 ? 640 : 1000);
		return true;
	}
}
