package com.openrsc.server.model.combat;

/** Immutable identity of one actual roll; reaching the maximum does not imply a critical hit. */
public final class AttackRoll {
  public enum Critical {
    NONE,
    COSMIC
  }

  private final int damage;
  private final Critical critical;

  private AttackRoll(int damage, Critical critical) {
    this.damage = Math.max(0, damage);
    this.critical = critical;
  }

  public static AttackRoll normal(int damage) {
    return new AttackRoll(damage, Critical.NONE);
  }

  public static AttackRoll critical(int damage, Critical provenance) {
    return new AttackRoll(damage, java.util.Objects.requireNonNull(provenance));
  }

  public int getDamage() {
    return damage;
  }

  public boolean isCritical() {
    return critical != Critical.NONE;
  }

  public Critical getCritical() {
    return critical;
  }

  public AttackRoll withDamage(int damage) {
    return new AttackRoll(damage, critical);
  }

  public AttackRoll committed(AttackCommitment commitment) {
    return commitment.isSuppressed() ? normal(0) : this;
  }
}
