package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericAlignment;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import java.util.*;

/** Immutable authoritative component values for one cast-time god/tier/power snapshot. */
public final class ClericBlessingMagnitude implements ClericEffectMagnitude {
  private final ClericAlignment god;
  private final int tier, power;

  public ClericBlessingMagnitude(ClericAlignment god, int tier, int power) {
    if (god == null
        || god == ClericAlignment.NEUTRAL
        || tier < 1
        || tier > 3
        || power < 1
        || power > tier * 2) throw new IllegalArgumentException("Invalid blessing snapshot");
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

  public int regenerationPercent() {
    return god == ClericAlignment.SARADOMIN ? 200 + 50 * (power - 1) : 100;
  }

  public int healingPerPulse() {
    return god == ClericAlignment.SARADOMIN && tier >= 2 ? power : 0;
  }

  public int poisonCleanse() {
    return god == ClericAlignment.GUTHIX ? power + 1 : 0;
  }

  public int restoreIntervalSeconds() {
    return god == ClericAlignment.GUTHIX ? 11 - power : 0;
  }

  public int defensePercent() {
    return god == ClericAlignment.GUTHIX && tier >= 2 ? 5 + 2 * (power - 1) : 0;
  }

  public int reflectPercent() {
    return god == ClericAlignment.GUTHIX && tier >= 3 ? power + 4 : 0;
  }

  public int maxDamagePercent() {
    return god == ClericAlignment.ZAMORAK ? 5 + 2 * (power - 1) : 0;
  }

  public int dealtDamagePercent() {
    return god == ClericAlignment.ZAMORAK && tier >= 2 ? power + 2 : 0;
  }

  public int lifestealPercent() {
    return god == ClericAlignment.ZAMORAK && tier >= 3 ? power + 4 : 0;
  }

  public List<ClericBlessingComponent> components() {
    List<ClericBlessingComponent> out = new ArrayList<ClericBlessingComponent>();
    if (god == ClericAlignment.SARADOMIN) {
      out.add(
          new ClericBlessingComponent(
              ClericBlessingContract.REGENERATION, regenerationPercent(), 0, 0));
      if (healingPerPulse() > 0)
        out.add(
            new ClericBlessingComponent(
                ClericBlessingContract.HEALING_PULSE, healingPerPulse(), 6000, 10));
    }
    if (god == ClericAlignment.GUTHIX) {
      out.add(
          new ClericBlessingComponent(
              ClericBlessingContract.POISON_CLEANSING, poisonCleanse(), 0, 0));
      out.add(
          new ClericBlessingComponent(
              ClericBlessingContract.STAT_RESTORATION, 1, restoreIntervalSeconds() * 1000, 0));
      if (defensePercent() > 0)
        out.add(
            new ClericBlessingComponent(ClericBlessingContract.DEFENSE, defensePercent(), 0, 0));
      if (reflectPercent() > 0)
        out.add(
            new ClericBlessingComponent(ClericBlessingContract.REFLECTION, reflectPercent(), 0, 0));
    }
    if (god == ClericAlignment.ZAMORAK) {
      out.add(
          new ClericBlessingComponent(ClericBlessingContract.MAX_DAMAGE, maxDamagePercent(), 0, 0));
      if (dealtDamagePercent() > 0)
        out.add(
            new ClericBlessingComponent(
                ClericBlessingContract.DEALT_DAMAGE, dealtDamagePercent(), 0, 0));
      if (lifestealPercent() > 0)
        out.add(
            new ClericBlessingComponent(
                ClericBlessingContract.LIFESTEAL, lifestealPercent(), 50, 0));
    }
    return Collections.unmodifiableList(out);
  }
}
