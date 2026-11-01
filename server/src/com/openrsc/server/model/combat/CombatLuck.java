package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Explicit semantic rolls, never a Random interceptor or a replay of an attack/effect. */
public final class CombatLuck {
  private CombatLuck() {}

  /** Only the actual player beneficiary owns robe luck; NPCs never inherit an owner's equipment. */
  public static double chance(Mob beneficiary) {
    return beneficiary instanceof Player ? ((Player) beneficiary).getCombatLuckChance() : 0;
  }

  public static int higher(Mob beneficiary, GameRandom random, IntSupplier candidate) {
    return higher(chance(beneficiary), random::nextDouble, candidate);
  }

  /**
   * The supplier is one complete pure damage or defense candidate, including its existing biases.
   */
  public static int higher(double luck, DoubleSupplier random, IntSupplier candidate) {
    validateLuck(luck);
    int first = candidate.getAsInt();
    return luck > 0 && random.getAsDouble() < luck ? Math.max(first, candidate.getAsInt()) : first;
  }

  public static boolean roll(Mob beneficiary, double probability, GameRandom random) {
    return favorable(
        chance(beneficiary),
        probability,
        random::nextDouble,
        () -> random.nextDouble() < probability);
  }

  /**
   * Keeps the caller's original RNG provider and comparison exactly, including zero-luck behavior.
   */
  public static boolean favorable(
      Mob beneficiary, double probability, DoubleSupplier random, BooleanSupplier candidate) {
    return favorable(chance(beneficiary), probability, random, candidate);
  }

  public static boolean favorable(
      double luck, double probability, DoubleSupplier random, BooleanSupplier candidate) {
    validateLuck(luck);
    if (Double.isNaN(probability) || probability < 0 || probability > 1)
      throw new IllegalArgumentException("Invalid combat chance");
    boolean first = candidate.getAsBoolean();
    if (luck > 0 && probability > 0 && probability < 1 && random.getAsDouble() < luck) {
      boolean second = candidate.getAsBoolean();
      return first || second;
    }
    return first;
  }

  /**
   * Exact independent favorable-check probability, useful to authored help and deterministic tests.
   */
  public static double effectiveProbability(double probability, double luck) {
    validateLuck(luck);
    if (Double.isNaN(probability) || probability < 0 || probability > 1)
      throw new IllegalArgumentException("Invalid combat chance");
    return probability + luck * probability * (1 - probability);
  }

  private static void validateLuck(double luck) {
    if (Double.isNaN(luck) || luck < 0 || luck > 0.5)
      throw new IllegalArgumentException("Combat luck must be within0..50 percent");
  }
}
