package orsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Bounded presentation events; time is monotonic milliseconds, independent of client FPS. */
public final class FloatingHitSplatState {
    public static final int MAX_ACTIVE = 6, MAX_PENDING = 12;
    public static final int ROW_GAP = 14, MAX_RISE = 64;
    public static final long LIFETIME_MS = 2100, FADE_MS = 450, PENDING_LIFETIME_MS = 1200;
    public static final double RISE_PER_SECOND = 16, PUSH_PER_SECOND = 110;
    private final List<Entry> active = new ArrayList<Entry>();
    private final List<Entry> pending = new ArrayList<Entry>();
    private long previous = -1, sequence, receiveBatch;
    private boolean legacyPending;
    private int legacyAmount;

    public static long now() { return System.nanoTime() / 1_000_000L; }

    public static int priority(int type) {
        switch (type) {
            case ORSCharacter.HIT_SPLAT_STANDARD: return 0;
            case ORSCharacter.HIT_SPLAT_HEAL: return 1;
            case ORSCharacter.HIT_SPLAT_POISON: return 2;
            case ORSCharacter.HIT_SPLAT_SUMMON: return 3;
            case ORSCharacter.HIT_SPLAT_RECOIL: return 4;
            default: return 5;
        }
    }

    public void legacyDamage(int amount) { legacyPending = true; legacyAmount = amount; }

    public void typed(int type, int amount, long now) {
        // The paired classic damage update is not a second damage event.
        legacyPending = false;
        offer(type, amount, now);
    }

    public void offer(int type, int amount, long now) {
        if (type < 0 || type >= ORSCharacter.HIT_SPLAT_COUNT) return;
        if (pending.size() == MAX_PENDING) pending.remove(0);
        pending.add(new Entry(type, Math.max(0, amount), now, sequence++, receiveBatch));
    }

    public List<Entry> advance(long now) {
        if (legacyPending) {
            offer(ORSCharacter.HIT_SPLAT_STANDARD, legacyAmount, now);
            legacyPending = false;
        }
        double seconds = previous < 0 ? 0 : Math.max(0, now - previous) / 1000.0;
        previous = now;
        pending.removeIf(e -> now - e.received > PENDING_LIFETIME_MS);
        // Events collected before the next update/render form one receipt batch; packet order is incidental.
        Collections.sort(pending, Comparator.comparingLong((Entry e) -> e.batch)
            .thenComparingInt(e -> priority(e.type)).thenComparingLong(e -> e.sequence));
        receiveBatch++;
        double youngerRise = -1;
        for (int i = active.size() - 1; i >= 0; i--) {
            Entry e = active.get(i);
            double required = youngerRise >= 0 ? youngerRise + ROW_GAP : (!pending.isEmpty() ? ROW_GAP : 0);
            double natural = e.rise + RISE_PER_SECOND * seconds;
            e.rise = Math.max(natural, Math.min(required, e.rise + PUSH_PER_SECOND * seconds));
            youngerRise = e.rise;
        }
        active.removeIf(e -> now - e.started >= LIFETIME_MS || e.rise >= MAX_RISE);
        if (!pending.isEmpty() && (active.isEmpty() || active.get(active.size() - 1).rise >= ROW_GAP)) {
            if (active.size() == MAX_ACTIVE) active.remove(0);
            Entry e = pending.remove(0);
            e.started = now;
            active.add(e);
        }
        return Collections.unmodifiableList(active);
    }

    public boolean isActive() { return legacyPending || !pending.isEmpty() || !active.isEmpty(); }
    public int pendingCount() { return pending.size(); }
    public void clear() { active.clear(); pending.clear(); legacyPending = false; previous = -1; }

    public static final class Entry {
        public final int type, amount;
        public final long received, sequence, batch;
        public long started;
        public double rise;
        private Entry(int type, int amount, long received, long sequence, long batch) {
            this.type = type; this.amount = amount; this.received = received; this.sequence = sequence; this.batch = batch;
        }
        public int opacity(long now) {
            double time = Math.min(1, Math.max(0, (LIFETIME_MS - (now - started)) / (double) FADE_MS));
            double height = Math.min(1, Math.max(0, (MAX_RISE - rise) / (double) ROW_GAP));
            return (int) Math.round(255 * Math.min(time, height));
        }
    }
}
