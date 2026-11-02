package orsc;

/** Local cosmetic playback only: one active sequence and at most one pending per god. */
public final class ClericBuffPlayback {
    private final int[] pending = new int[3];
    private final int[] durations = new int[3];
    private int count, active, remaining;

    public int getEffectType() { return active; }
    public int getRemainingTicks() { return remaining; }
    public int getPendingCount() { return count; }

    public void enqueue(int effect, int duration) {
        int god = ClericBlessingContract.buffAnimationGod(effect);
        if (god < 0 || duration <= 0) return;
        if (remaining <= 0) { active = effect; remaining = duration; return; }
        for (int i = 0; i < count; i++) {
            if (ClericBlessingContract.buffAnimationGod(pending[i]) != god) continue;
            // Refreshes coalesce without erasing an already queued tier upgrade.
            if (ClericBlessingContract.buffAnimationSequence(effect).length >=
                    ClericBlessingContract.buffAnimationSequence(pending[i]).length) {
                pending[i] = effect; durations[i] = duration;
            }
            return;
        }
        // Three god identities bound this queue, regardless of packet frequency.
        if (count < pending.length) { pending[count] = effect; durations[count++] = duration; }
    }

    public void tick() {
        if (remaining <= 0 || --remaining > 0) return;
        active = 0;
        if (count == 0) return;
        active = pending[0]; remaining = durations[0];
        for (int i = 1; i < count; i++) { pending[i-1] = pending[i]; durations[i-1] = durations[i]; }
        pending[--count] = 0; durations[count] = 0;
    }

    public void clear() {
        count = active = remaining = 0;
        java.util.Arrays.fill(pending, 0);
        java.util.Arrays.fill(durations, 0);
    }
}
