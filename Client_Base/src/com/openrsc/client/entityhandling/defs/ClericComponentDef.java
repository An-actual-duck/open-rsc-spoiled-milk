package com.openrsc.client.entityhandling.defs;

import orsc.ClericBlessingContract;

/** Checked numeric presentation data, never executable formulas from the server. */
public final class ClericComponentDef {
  private final int kind, magnitude, parameter1, parameter2;

  public ClericComponentDef(int kind, int magnitude, int parameter1, int parameter2) {
    if (kind < ClericBlessingContract.REGENERATION
        || kind > ClericBlessingContract.LAST_COMPONENT_KIND
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
                    : kind == ClericBlessingContract.NON_CORPOREAL ? parameter1>=1 && parameter1<=3 && parameter2==1
                    : kind == ClericBlessingContract.CRITICAL_STAGGER ? parameter1==1 && parameter2==5
                    : kind == ClericBlessingContract.CRITICAL_EXECUTION ? parameter1>=1 && parameter1<=100 && parameter2==0
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

  public String getName() { return ClericBlessingContract.componentName(kind); }
  public String describe() {
    return ClericBlessingContract.describeComponent(kind,magnitude,parameter1,parameter2);
  }
}
