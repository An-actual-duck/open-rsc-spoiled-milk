package com.openrsc.server.model.combat;

/** One offensive deadline shared across styles. Extra delays are added once, never multiplied. */
public final class AttackCadenceState {
    private long due = Long.MIN_VALUE;
    private long pending;
    public synchronized void addDelay(long now, int ticks) {
        if (ticks <= 0) return;
        if (due > now) due = plus(due, ticks);
        else pending = plus(pending, ticks);
    }
    public synchronized boolean ready(long now) {
        if (due <= now && pending > 0) {
            due = plus(now, pending);
            pending = 0;
        }
        return now >= due;
    }
    public synchronized void commit(long now, int adjustedTicks) {
        due = Math.max(due, plus(plus(now, Math.max(1, adjustedTicks)), pending));
        pending = 0;
    }
    public synchronized long due() { return due; }
    public synchronized void clear() { due = Long.MIN_VALUE; pending = 0; }
    private static long plus(long base, long value) {
        return base > Long.MAX_VALUE - value ? Long.MAX_VALUE : base + value;
    }
}
