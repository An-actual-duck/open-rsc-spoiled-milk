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

  /** Cosmic remains the sole enabled modern source; its authored values and RNG are unchanged. */
  public static AttackRoll cosmic(Mob source, int maximum, IntSupplier ordinaryRoll) {
    double chance = source instanceof Player ? ((Player) source).getCosmicRobeCritChance() : 0;
    return resolve(
        maximum,
        chance,
        AttackRoll.Critical.COSMIC,
        () -> source.getWorld().getServer().getCombatRandom().nextDouble(),
        ordinaryRoll);
  }
}
