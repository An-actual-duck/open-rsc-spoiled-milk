package com.openrsc.server.model.combat;

/** Actor-owned attack accounting; callers invoke commit only for a performed attack. */
public final class StaggerState {
  public static final int RECOVERY_ATTACKS = 5;
  private int pendingAttacks;
  private int recoveryAttacks;

  public synchronized boolean apply(int attacks) {
    if (attacks < 1 || pendingAttacks != 0 || recoveryAttacks != 0) return false;
    pendingAttacks = attacks;
    return true;
  }

  /** Immutable receipt: impacts and secondary hits must reuse, never recommit. */
  public synchronized AttackCommitment commit() {
    if (pendingAttacks > 0) {
      pendingAttacks--;
      recoveryAttacks = RECOVERY_ATTACKS;
      return AttackCommitment.staggered();
    }
    if (recoveryAttacks > 0) recoveryAttacks--;
    return AttackCommitment.normal();
  }

  public synchronized int pendingAttacks() {
    return pendingAttacks;
  }

  public synchronized int recoveryAttacks() {
    return recoveryAttacks;
  }

  public synchronized void clear() {
    pendingAttacks = 0;
    recoveryAttacks = 0;
  }
}
