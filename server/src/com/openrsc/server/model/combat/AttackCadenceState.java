package com.openrsc.server.model.combat;

/** Actor-wide one-shot extra delays. Ordinary style/base/Slow deadlines stay with their owners. */
public final class AttackCadenceState {
  public enum Source {
    WHIP,
    STICKY
  }

  private int tokens;
  private long due = Long.MIN_VALUE;
  private boolean latched;
  private int accountedSlow = -1;

  public synchronized boolean addDelay(long now, Source source) {
    int bit = 1 << source.ordinal();
    if ((tokens & bit) != 0) return false;
    tokens |= bit;
    if (latched) due = plus(Math.max(now, due), 1);
    return true;
  }

  /**
   * Only after ordinary readiness/path/resource admission, never from general eligibility polls.
   */
  public synchronized boolean ready(long now) {
    return ready(now, 0, 0);
  }

  public synchronized boolean ready(long now, int currentSlow, int capturedSlow) {
    if (currentSlow < 0 || capturedSlow < 0)
      throw new IllegalArgumentException("Negative Slow tier");
    accountedSlow = Math.max(accountedSlow, capturedSlow);
    int additionalSlow = Math.max(0, currentSlow - accountedSlow);
    accountedSlow = Math.max(accountedSlow, currentSlow);
    if (!latched && (tokens != 0 || additionalSlow > 0)) {
      due = plus(now, Integer.bitCount(tokens) + (long) additionalSlow);
      latched = true;
    } else if (latched && additionalSlow > 0) {
      due = plus(Math.max(now, due), additionalSlow);
    }
    return !latched || now >= due;
  }

  /** Actual attack commitment consumes its delay receipt, never a readiness poll. */
  public synchronized void committed() {
    tokens = 0;
    due = Long.MIN_VALUE;
    latched = false;
    accountedSlow = -1;
  }

  public synchronized boolean has(Source source) {
    return (tokens & (1 << source.ordinal())) != 0;
  }

  public synchronized long due() {
    return due;
  }

  public synchronized void clear() {
    committed();
  }

  private static long plus(long base, long value) {
    return base > Long.MAX_VALUE - value ? Long.MAX_VALUE : base + value;
  }
}
