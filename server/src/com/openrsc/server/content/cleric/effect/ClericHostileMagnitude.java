package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericAlignment;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import java.util.*;

/** Cast-time snapshot; the registry owns its common expiry and replacement precedence. */
public final class ClericHostileMagnitude implements ClericCompositeMagnitude {
  private final ClericAlignment god;
  private final int tier, power;

  public ClericHostileMagnitude(ClericAlignment god, int tier, int power) {
    if ((god != ClericAlignment.GUTHIX && god != ClericAlignment.SARADOMIN)
        || tier < 1
        || tier > 3
        || power < 1
        || power > tier * 2) throw new IllegalArgumentException("Invalid hostile Cleric snapshot");
    this.god = god;
    this.tier = tier;
    this.power = power;
  }

  public ClericAlignment getGod() {
    return god;
  }

  public int getTier() {
    return tier;
  }

  public int getPower() {
    return power;
  }

  public int poisonWeakness() {
    return god == ClericAlignment.GUTHIX ? 10 + 5 * (power - 1) : 0;
  }

  public int summonWeakness() {
    return god == ClericAlignment.GUTHIX && tier >= 2 ? 10 + 5 * (power - 1) : 0;
  }

  public int recoilWeakness() {
    return god == ClericAlignment.GUTHIX && tier >= 3 ? 10 + 5 * (power - 1) : 0;
  }

  public int magicDefenseReduction() {
    return god == ClericAlignment.SARADOMIN ? 10 + 3 * (power - 1) : 0;
  }

  public int maximumMeleeReduction() {
    return god == ClericAlignment.SARADOMIN && tier >= 2 ? 5 + 2 * (power - 1) : 0;
  }

  public int phaseChance() {
    return god == ClericAlignment.SARADOMIN && tier >= 3 ? 5 + 2 * (power - 1) : 0;
  }

  public int phaseTicks() {
    return power <= 3 ? 1 : power <= 5 ? 2 : 3;
  }

  public List<ClericBlessingComponent> components() {
    List<ClericBlessingComponent> result = new ArrayList<ClericBlessingComponent>();
    if (god == ClericAlignment.GUTHIX) {
      result.add(
          new ClericBlessingComponent(
              ClericBlessingContract.POISON_WEAKNESS, poisonWeakness(), 0, 0));
      if (tier >= 2)
        result.add(
            new ClericBlessingComponent(
                ClericBlessingContract.SUMMON_WEAKNESS, summonWeakness(), 0, 0));
      if (tier >= 3)
        result.add(
            new ClericBlessingComponent(
                ClericBlessingContract.RECOIL_WEAKNESS, recoilWeakness(), 0, 0));
    } else {
      result.add(
          new ClericBlessingComponent(
              ClericBlessingContract.MAGIC_DEFENSE_REDUCTION, magicDefenseReduction(), 0, 0));
      if (tier >= 2)
        result.add(
            new ClericBlessingComponent(
                ClericBlessingContract.MAX_MELEE_REDUCTION, maximumMeleeReduction(), 0, 0));
      if (tier >= 3)
        result.add(
            new ClericBlessingComponent(
                ClericBlessingContract.NON_CORPOREAL, phaseChance(), phaseTicks(), 1));
    }
    return Collections.unmodifiableList(result);
  }
}
