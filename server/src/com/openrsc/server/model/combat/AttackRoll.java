package com.openrsc.server.model.combat;

/** Immutable identity of one actual roll; reaching the maximum does not imply a critical hit. */
public final class AttackRoll {
  public enum Critical {
    NONE,
    COSMIC,
    AUTHORED
  }

  private final int damage;
  private final Critical critical;
  private final int woundRoll, woundMaximum;

  private AttackRoll(int damage, Critical critical) {
    this(damage, critical, 0, 0);
  }

  private AttackRoll(int damage, Critical critical, int woundRoll, int woundMaximum) {
    this.damage = Math.max(0, damage);
    this.woundRoll = woundRoll;
    this.woundMaximum = woundMaximum;
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
    return new AttackRoll(damage, critical, woundRoll, woundMaximum);
  }

  /** Comparable pre-percentage damage and ceiling captured by the actual primary formula. */
  public AttackRoll withWoundBasis(int roll, int maximum) {
    return new AttackRoll(damage, critical, Math.max(0, roll), Math.max(0, maximum));
  }

  public int getWoundRoll() { return woundRoll; }
  public int getWoundMaximum() { return woundMaximum; }

  public AttackRoll committed(AttackCommitment commitment) {
    return commitment.isSuppressed() ? normal(0) : this;
  }
}
