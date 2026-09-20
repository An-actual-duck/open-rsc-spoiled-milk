package orsc;

/** Server-driven stab/spike poses; all timing follows actual attack decisions. */
public final class AbyssalDemonPose {
	private AbyssalDemonPose() { }
	public static boolean apply(ORSCharacter npc, int code, long now) {
		if (npc == null || npc.npcId != 870 || code < 82 || code > 85) return false;
		// Repeated rising pose refreshes its fallback expiry without restarting the motion.
		if (code != 83 || npc.abyssalPoseCode != code) npc.abyssalPoseStartedMillis = now;
		npc.abyssalPoseCode = code;
		npc.abyssalPoseExpiresMillis = code == 84 ? 0 : now + (code == 85 ? 640 : 2000);
		return true;
	}
	public static int frame(ORSCharacter npc, long now) {
		if (npc == null || npc.npcId != 870 || now >= npc.abyssalPoseExpiresMillis) return -1;
		long elapsed = Math.max(0, now - npc.abyssalPoseStartedMillis);
		if (npc.abyssalPoseCode == 82) return elapsed < 80 ? 18 : 19;
		if (npc.abyssalPoseCode == 83) return 20;
		if (npc.abyssalPoseCode == 85) return elapsed < 80 ? 15 : elapsed < 240 ? 16 : 17;
		return -1;
	}
}
