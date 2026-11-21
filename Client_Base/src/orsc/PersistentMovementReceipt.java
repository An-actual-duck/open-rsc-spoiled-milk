package orsc;

/** Orders authoritative movement within a travel lifetime independently of terrain revisions. */
final class PersistentMovementReceipt {
	static final int PROTOCOL_VERSION = 3;
	private int generation;
	private int anchor;
	private int sequence;

	boolean accept(int incomingGeneration, int incomingAnchor, int incomingSequence, int activeScene) {
		if (incomingGeneration <= 0 || incomingAnchor <= 0 || incomingGeneration < generation) {
			return false;
		}
		if (incomingGeneration == generation) {
			if (incomingAnchor != anchor || incomingSequence - sequence <= 0) return false;
		} else if (activeScene != incomingAnchor) {
			return false;
		}
		generation = incomingGeneration;
		anchor = incomingAnchor;
		sequence = incomingSequence;
		return true;
	}

	int generation() { return generation; }

	void reset() {
		generation = 0;
		anchor = 0;
		sequence = 0;
	}
}
