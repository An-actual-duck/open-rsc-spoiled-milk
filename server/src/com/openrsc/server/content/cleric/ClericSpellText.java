package com.openrsc.server.content.cleric;

import com.openrsc.server.content.cleric.effect.ClericBlessingComponent;
import com.openrsc.server.content.cleric.effect.ClericBlessingMagnitude;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import java.util.LinkedHashSet;
import java.util.Set;

/** Authoritative approved spell titles, recipient flavor and inherited named-effect inventory. */
public final class ClericSpellText {
  private static final String[][] BUFF_NAMES = {
    {"Saradomin's Light", "Saradomin's Aura", "Saradomin's Resplendence"},
    {"Guthix's Respite", "Guthix's Protection", "Guthix's Vengeance"},
    {"Zamorak's Fervor", "Zamorak's Fury", "Zamorak's Wrath"}
  };
  private static final String[] BUFF_FLAVOR = {
    "Your attacks carry a holy light that burns the restless dead.",
    "You feel your blood rush and your spirits lift",
    "Your wounds seal up as soon as they are opened",
    "Your blood feels thick in your veins like sap. Your ailments slow to a halt",
    "Your skin grows a thick layer of bark",
    "Sharp spikes protrude from you, piercing your foes.",
    "Your muscles bulge and your mind feels sharper",
    "Your enemies seem much smaller than before",
    "Your weapon will breathe new life into you should you feel weak"
  };

  private ClericSpellText() {}

  public static String buffName(int god, int tier) {
    return BUFF_NAMES[god][tier - 1];
  }

  public static String recipientFlavor(ClericSpellId id) {
    int index = id.getCode() - 12;
    return index >= 0 && index < BUFF_FLAVOR.length ? BUFF_FLAVOR[index] : null;
  }

  public static String hostileDescription(ClericAlignment god, int tier) {
    Set<String> names = new LinkedHashSet<String>();
    for (ClericBlessingComponent c :
        new com.openrsc.server.content.cleric.effect.ClericHostileMagnitude(god, tier, 1)
            .components()) names.add(ClericBlessingContract.componentName(c.getKind()));
    return String.join("; ", names) + ". Afflicts eligible enemies.";
  }

  public static String blessingDescription(ClericAlignment god, int tier) {
    Set<String> names = new LinkedHashSet<String>();
    for (ClericBlessingComponent c : new ClericBlessingMagnitude(god, tier, 1).components())
      names.add(ClericBlessingContract.componentName(c.getKind()));
    return String.join("; ", names) + ". Includes every named component listed for this tier.";
  }
}
