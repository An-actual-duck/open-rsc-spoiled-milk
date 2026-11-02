package orsc;

/** Bounded cosmetic playback only: one active sequence and at most one pending per god. */
public class ClericEffectPlayback {
    private final boolean curse;
    protected ClericEffectPlayback(boolean curse) { this.curse = curse; }
    private int god(int id) { return curse ? ClericBlessingContract.curseAnimationGod(id) : ClericBlessingContract.buffAnimationGod(id); }
    private int tier(int id) { return (curse ? ClericBlessingContract.curseAnimationSequence(id) : ClericBlessingContract.buffAnimationSequence(id)).length; }
    private final int[] pending = new int[3];
    private final int[] durations = new int[3];
    private int count, active, remaining;

    public int getEffectType() { return active; }
    public int getRemainingTicks() { return remaining; }
    public int getPendingCount() { return count; }

    public void enqueue(int effect, int duration) {
        int god = god(effect);
        if (god < 0 || duration <= 0) return;
        if (remaining <= 0) { active = effect; remaining = duration; return; }
        for (int i = 0; i < count; i++) {
            if (god(pending[i]) != god) continue;
            // Refreshes coalesce without erasing an already queued tier upgrade.
            if (tier(effect) >=
                    tier(pending[i])) {
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
