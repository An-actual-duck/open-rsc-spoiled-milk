package com.openrsc.client.entityhandling.defs;

/** One unnoted resource consumed once per cast, never once per recipient. */
public final class ClericCostDef {
  private final int itemId, count;

  public ClericCostDef(int itemId, int count) {
    if (itemId < 0 || itemId > 65535 || count < 1 || count > 255)
      throw new IllegalArgumentException("Invalid Cleric casting cost");
    this.itemId = itemId;
    this.count = count;
  }

  public int getItemId() {
    return itemId;
  }

  public int getCount() {
    return count;
  }
}
