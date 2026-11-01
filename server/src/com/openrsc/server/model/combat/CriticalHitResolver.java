package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Source-independent critical roll resolution; no damage-max inference or mutable actor flag. */
public final class CriticalHitResolver {
  private CriticalHitResolver() {}

  public static AttackRoll resolve(
      int maximum,
      double chance,
      AttackRoll.Critical provenance,
      DoubleSupplier random,
      IntSupplier ordinaryRoll) {
    if (Double.isNaN(chance) || chance < 0 || chance > 1 || provenance == null)
      throw new IllegalArgumentException("Invalid critical chance/provenance");
    if (maximum > 0
        && chance > 0
        && provenance != AttackRoll.Critical.NONE
        && rollChance(chance, random)) return AttackRoll.critical(maximum, provenance);
    return AttackRoll.normal(ordinaryRoll.getAsInt());
  }

  public static boolean rollChance(double chance, DoubleSupplier random) {
    if (Double.isNaN(chance) || chance < 0 || chance > 1)
      throw new IllegalArgumentException("Invalid critical chance");
    return chance > 0 && random.getAsDouble() < chance;
  }

  /** Current ordinary attacks have no enabled critical chance; normal maxima remain noncritical. */
  public static AttackRoll ordinary(Mob source,int maximum,IntSupplier ordinaryRoll) {
    return AttackRoll.normal(ordinaryRoll.getAsInt());
  }

  /** Retained zero-critical compatibility path; luck belongs to the named roll inside the supplier. */
  @Deprecated
  public static AttackRoll cosmic(Mob source,int maximum,IntSupplier ordinaryRoll) {
    return ordinary(source,maximum,ordinaryRoll);
  }

  /** Future authored critical sources can benefit without enabling any current critical provider. */
  public static AttackRoll resolve(Mob beneficiary,int maximum,double chance,AttackRoll.Critical provenance,
      com.openrsc.server.runtime.GameRandom random,IntSupplier ordinaryRoll) {
    if(Double.isNaN(chance)||chance<0||chance>1||provenance==null)throw new IllegalArgumentException("Invalid critical chance/provenance");
    if(maximum>0&&chance>0&&provenance!=AttackRoll.Critical.NONE&&CombatLuck.roll(beneficiary,chance,random))
      return AttackRoll.critical(maximum,provenance);
    return AttackRoll.normal(ordinaryRoll.getAsInt());
  }
}
