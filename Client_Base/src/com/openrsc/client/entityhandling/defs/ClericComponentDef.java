package com.openrsc.client.entityhandling.defs;

import orsc.ClericBlessingContract;

/** Checked numeric presentation data, never executable formulas from the server. */
public final class ClericComponentDef {
  private final int kind, magnitude, parameter1, parameter2;

  public ClericComponentDef(int kind, int magnitude, int parameter1, int parameter2) {
    if (kind < ClericBlessingContract.REGENERATION
        || kind > ClericBlessingContract.REFLECTION
        || magnitude < 1
        || magnitude > 65535
        || parameter1 < 0
        || parameter1 > 65535
        || parameter2 < 0
        || parameter2 > 65535) throw new IllegalArgumentException("Invalid blessing component");
    boolean valid =
        kind == ClericBlessingContract.HEALING_PULSE
            ? parameter1 > 0 && parameter2 > 0 && parameter2 <= 60
            : kind == ClericBlessingContract.STAT_RESTORATION
                ? parameter1 > 0 && parameter2 == 0
                : kind == ClericBlessingContract.LIFESTEAL
                    ? magnitude <= 100 && parameter1 > 0 && parameter1 <= 100 && parameter2 == 0
                    : parameter1 == 0 && parameter2 == 0;
    if (!valid || (kind >= ClericBlessingContract.DEFENSE && magnitude > 100))
      throw new IllegalArgumentException("Invalid blessing component units");
    this.kind = kind;
    this.magnitude = magnitude;
    this.parameter1 = parameter1;
    this.parameter2 = parameter2;
  }

  public int getKind() {
    return kind;
  }

  public int getMagnitude() {
    return magnitude;
  }

  public int getParameter1() {
    return parameter1;
  }

  public int getParameter2() {
    return parameter2;
  }

  private static String seconds(int milliseconds) {
    return milliseconds % 1000 == 0
        ? Integer.toString(milliseconds / 1000)
        : java.math.BigDecimal.valueOf(milliseconds, 3).stripTrailingZeros().toPlainString();
  }

  public String describe() {
    switch (kind) {
      case ClericBlessingContract.REGENERATION:
        return "Natural Hits regeneration at " + magnitude + "% of normal";
      case ClericBlessingContract.HEALING_PULSE:
        return magnitude + " Hits every " + seconds(parameter1) + " sec, " + parameter2 + " pulses";
      case ClericBlessingContract.POISON_CLEANSING:
        return "Extra " + magnitude + " poison cleansing per poison pulse";
      case ClericBlessingContract.STAT_RESTORATION:
        return "Restore "
            + magnitude
            + " point to each drained non-Hits stat every "
            + seconds(parameter1)
            + " sec";
      case ClericBlessingContract.DEFENSE:
        return "+" + magnitude + "% melee, ranged and Magic defense";
      case ClericBlessingContract.MAX_DAMAGE:
        return "+" + magnitude + "% maximum direct damage";
      case ClericBlessingContract.DEALT_DAMAGE:
        return "+" + magnitude + "% total direct damage dealt";
      case ClericBlessingContract.LIFESTEAL:
        return magnitude + "% lifesteal strictly below " + parameter1 + "% Hits";
      case ClericBlessingContract.REFLECTION:
        return "Reflect " + magnitude + "% direct damage";
      default:
        throw new IllegalStateException("Unsupported blessing component");
    }
  }
}
