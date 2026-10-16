package orsc;

/**
 * Ground packets are relative to the last player packet, even when death has
 * deferred loading its terrain. The ordinary arrays hold just this epoch's
 * announcements; removals and chunk unloads therefore need no parallel journal.
 */
final class GroundItemAreaEpoch {

    private boolean pending;

    /** The server is replacing its known set. Discard the previous set now. */
    int begin() {
        pending = true;
        return 0;
    }

    int cancel() {
        pending = false;
        return 0;
    }

    /**
     * Consume the pending set once, translating from the old local base to the
     * completed area's base. An unannounced hard load still discards old items.
     * No ID/tile deduplication: identical drops are independent entities.
     */
    int complete(int count, int[] x, int[] z, int[] ids, int[] heights,
                 boolean[] noted, int baseDX, int baseDZ, int localSize) {
        if (!pending) {
            return 0;
        }
        pending = false;
        int retained = 0;
        for (int i = 0; i < count; i++) {
            int shiftedX = x[i] - baseDX;
            int shiftedZ = z[i] - baseDZ;
            if (shiftedX < 0 || shiftedZ < 0
                    || shiftedX >= localSize || shiftedZ >= localSize) {
                continue;
            }
            x[retained] = shiftedX;
            z[retained] = shiftedZ;
            ids[retained] = ids[i];
            heights[retained] = heights[i];
            noted[retained] = noted[i];
            retained++;
        }
        return retained;
    }
}
