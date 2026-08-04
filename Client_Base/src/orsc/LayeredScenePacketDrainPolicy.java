package orsc;

/**
 * Bounds normal and atomic packet drains for native layered scenes.
 *
 * <p>Legacy gameplay deliberately retains the authentic one-packet-per-client
 * tick cadence. A native layered session has a greater steady-state packet
 * volume because movement snapshots and scene-residency messages share the
 * ordered stream. Letting that stream consume only one packet per frame can
 * make its receive backlog grow without bound, delaying an already-applied
 * server movement or teleport indefinitely. Layered ordinary play therefore
 * gets a small, hard-bounded drain budget. Atomic activation gets a larger
 * bounded budget for its context, Player receipt, exact scene fence, and paged
 * static presentation, and still stops while the radius-two terrain halo is
 * being built.</p>
 */
final class LayeredScenePacketDrainPolicy {
	static final int LEGACY_NORMAL_PACKET_LIMIT = 1;
	static final int LAYERED_NORMAL_PACKET_LIMIT = 4;
	static final int ATOMIC_ACTIVATION_PACKET_LIMIT = 32;

	private LayeredScenePacketDrainPolicy() {
	}

	static boolean shouldReadNext(
		int processedPackets,
		boolean activationBurst,
		boolean activationPending,
		boolean terrainHaloBuildPending,
		boolean layeredSession) {
		if (processedPackets < 0) {
			throw new IllegalArgumentException(
				"Processed packet count cannot be negative");
		}
		if (activationPending && terrainHaloBuildPending) {
			return false;
		}
		if (!activationBurst) {
			return processedPackets < (layeredSession
				? LAYERED_NORMAL_PACKET_LIMIT
				: LEGACY_NORMAL_PACKET_LIMIT);
		}
		return activationPending
			&& processedPackets < ATOMIC_ACTIVATION_PACKET_LIMIT;
	}
}
