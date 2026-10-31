package com.openrsc.server.model.combat;

/** Decision owned by one committed attack, not a mutable last-attack flag on a mob. */
public final class AttackCommitment {
    private static final AttackCommitment NORMAL = new AttackCommitment(false);
    private static final AttackCommitment STAGGERED = new AttackCommitment(true);
    private final boolean suppressed;
    private AttackCommitment(boolean suppressed) { this.suppressed = suppressed; }
    public static AttackCommitment normal() { return NORMAL; }
    public static AttackCommitment staggered() { return STAGGERED; }
    public boolean isSuppressed() { return suppressed; }
    public int resolveDamage(int rolledDamage) { return suppressed ? 0 : rolledDamage; }
    public boolean resolveCritical(boolean rolledCritical) { return !suppressed && rolledCritical; }
}
