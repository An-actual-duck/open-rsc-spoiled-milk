package orsc;

/**
 * Bounds the exceptional packet drain used while a protocol-v8 scene is
 * atomically activating.
 *
 * <p>Ordinary gameplay deliberately retains the legacy one-packet-per-client
 * tick cadence. A layered activation is different: its context, Player
 * receipt, exact scene fence, and paged static presentation are emitted as one
 * ordered server update. Consuming that update one packet per client tick
 * turns packet count into a visible loading pause. This policy permits a
 * bounded drain only for that already-atomic update, and stops while the
 * radius-two terrain halo is still building.</p>
 */
final class LayeredScenePacketDrainPolicy {
	static final int NORMAL_PACKET_LIMIT = 1;
	static final int ATOMIC_ACTIVATION_PACKET_LIMIT = 32;

	private LayeredScenePacketDrainPolicy() {
	}

	static boolean shouldReadNext(
		int processedPackets,
		boolean activationBurst,
		boolean activationPending,
		boolean terrainHaloBuildPending) {
		if (processedPackets < 0) {
			throw new IllegalArgumentException(
				"Processed packet count cannot be negative");
		}
		if (activationPending && terrainHaloBuildPending) {
			return false;
		}
		if (!activationBurst) {
			return processedPackets < NORMAL_PACKET_LIMIT;
		}
		return activationPending
			&& processedPackets < ATOMIC_ACTIVATION_PACKET_LIMIT;
	}
}
