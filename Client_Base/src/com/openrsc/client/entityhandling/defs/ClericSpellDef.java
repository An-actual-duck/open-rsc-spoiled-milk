package com.openrsc.client.entityhandling.defs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable Cleric spell metadata supplied by the authoritative server catalog. */
public final class ClericSpellDef {
  private final List<ClericCostDef> costs;
  private final int stableCode;
  private final String stableKey;
  private final String name;
  private final String description;
  private final String alignment;
  private final int worshipLevel;
  private final int spellTier;
  private final int radius;
  private final boolean affectsCaster;
  private final int spellbookIconItemId;
  private final String iconAssetKey;
  private final int stoneSigilItemId;
  private final int stoneSigilCount;
  private final int silverSigilItemId;
  private final int silverSigilCount;
  private final int casterIconItemId;
  private final int casterAnimationId;
  private final List<ClericEffectRankDef> effectRanks;

  public ClericSpellDef(
      int stableCode,
      String stableKey,
      String name,
      String description,
      String alignment,
      int worshipLevel,
      int spellTier,
      int radius,
      boolean affectsCaster,
      int spellbookIconItemId,
      int stoneSigilItemId,
      int stoneSigilCount,
      int silverSigilItemId,
      int silverSigilCount,
      int casterIconItemId,
      int casterAnimationId) {
    this(
        stableCode,
        stableKey,
        name,
        description,
        alignment,
        worshipLevel,
        spellTier,
        radius,
        affectsCaster,
        spellbookIconItemId,
        stoneSigilItemId,
        stoneSigilCount,
        silverSigilItemId,
        silverSigilCount,
        casterIconItemId,
        casterAnimationId,
        "",
        Collections.<ClericEffectRankDef>emptyList());
  }

  public ClericSpellDef(
      int stableCode,
      String stableKey,
      String name,
      String description,
      String alignment,
      int worshipLevel,
      int spellTier,
      int radius,
      boolean affectsCaster,
      int spellbookIconItemId,
      int stoneSigilItemId,
      int stoneSigilCount,
      int silverSigilItemId,
      int silverSigilCount,
      int casterIconItemId,
      int casterAnimationId,
      String iconAssetKey,
      List<ClericEffectRankDef> effectRanks) {
    this(
        stableCode,
        stableKey,
        name,
        description,
        alignment,
        worshipLevel,
        spellTier,
        radius,
        affectsCaster,
        spellbookIconItemId,
        legacyCosts(stoneSigilItemId, stoneSigilCount, silverSigilItemId, silverSigilCount),
        casterIconItemId,
        casterAnimationId,
        iconAssetKey,
        effectRanks);
  }

  private static List<ClericCostDef> legacyCosts(
      int stone, int stoneCount, int silver, int silverCount) {
    ArrayList<ClericCostDef> costs = new ArrayList<ClericCostDef>();
    costs.add(new ClericCostDef(stone, stoneCount));
    if (silverCount > 0) costs.add(new ClericCostDef(silver, silverCount));
    return costs;
  }

  public ClericSpellDef(
      int stableCode,
      String stableKey,
      String name,
      String description,
      String alignment,
      int worshipLevel,
      int spellTier,
      int radius,
      boolean affectsCaster,
      int spellbookIconItemId,
      List<ClericCostDef> costs,
      int casterIconItemId,
      int casterAnimationId,
      String iconAssetKey,
      List<ClericEffectRankDef> effectRanks) {
    if (stableCode < 0
        || stableCode > 65535
        || isBlank(stableKey)
        || isBlank(name)
        || isBlank(description)
        || isBlank(alignment)) {
      throw new IllegalArgumentException("Cleric spell identity and display metadata are required");
    }
    if (worshipLevel < 1
        || worshipLevel > 255
        || spellTier < 1
        || spellTier > 3
        || radius < 1
        || radius > 32
        || spellbookIconItemId < 0
        || spellbookIconItemId > 65535
        || casterIconItemId < -1
        || casterAnimationId < -1) {
      throw new IllegalArgumentException("Invalid Cleric spell presentation metadata");
    }
    if (iconAssetKey == null
        || (!iconAssetKey.isEmpty() && !iconAssetKey.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
        || effectRanks == null
        || effectRanks.size() > 6) {
      throw new IllegalArgumentException("Invalid Cleric icon or effect-rank metadata");
    }
    if (costs == null || costs.isEmpty() || costs.size() > orsc.ClericBlessingContract.MAX_COSTS)
      throw new IllegalArgumentException("Invalid Cleric cost vector");
    java.util.Set<Integer> itemIds = new java.util.HashSet<Integer>();
    for (ClericCostDef cost : costs)
      if (cost == null || !itemIds.add(cost.getItemId()))
        throw new IllegalArgumentException("Duplicate Cleric cost item");
    this.costs = Collections.unmodifiableList(new ArrayList<ClericCostDef>(costs));
    ArrayList<ClericEffectRankDef> rankCopy =
        new ArrayList<ClericEffectRankDef>(effectRanks.size());
    for (int index = 0; index < effectRanks.size(); index++) {
      ClericEffectRankDef rank = effectRanks.get(index);
      if (rank == null || rank.getRank() != index + 1) {
        throw new IllegalArgumentException("Cleric effect ranks must be complete and contiguous");
      }
      if (!rankCopy.isEmpty()) {
        ClericEffectRankDef first = rankCopy.get(0);
        if (rank.getPresentationKind() != first.getPresentationKind()
            || rank.getComponents().size() != first.getComponents().size())
          throw new IllegalArgumentException("Inconsistent Cleric rank components");
        for (int component = 0; component < first.getComponents().size(); component++)
          if (first.getComponents().get(component).getKind()
              != rank.getComponents().get(component).getKind())
            throw new IllegalArgumentException("Inconsistent Cleric component order");
      }
      rankCopy.add(rank);
    }
    this.stableCode = stableCode;
    this.stableKey = stableKey;
    this.name = name;
    this.description = description;
    this.alignment = alignment;
    this.worshipLevel = worshipLevel;
    this.spellTier = spellTier;
    this.radius = radius;
    this.affectsCaster = affectsCaster;
    this.spellbookIconItemId = spellbookIconItemId;
    this.iconAssetKey = iconAssetKey;
    this.stoneSigilItemId = costs.get(0).getItemId();
    this.stoneSigilCount = costs.get(0).getCount();
    this.silverSigilItemId = costs.size() > 1 ? costs.get(1).getItemId() : -1;
    this.silverSigilCount = costs.size() > 1 ? costs.get(1).getCount() : 0;
    this.casterIconItemId = casterIconItemId;
    this.casterAnimationId = casterAnimationId;
    this.effectRanks = Collections.unmodifiableList(rankCopy);
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public boolean hasResources(java.util.function.IntUnaryOperator inventory) {
    for (ClericCostDef cost : costs)
      if (inventory.applyAsInt(cost.getItemId()) < cost.getCount()) return false;
    return true;
  }

  public List<ClericCostDef> getCosts() {
    return costs;
  }

  public int getStableCode() {
    return stableCode;
  }

  public String getStableKey() {
    return stableKey;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getAlignment() {
    return alignment;
  }

  public int getWorshipLevel() {
    return worshipLevel;
  }

  public int getSpellTier() {
    return spellTier;
  }

  public int getRadius() {
    return radius;
  }

  public boolean affectsCaster() {
    return affectsCaster;
  }

  public int getSpellbookIconItemId() {
    return spellbookIconItemId;
  }

  public String getIconAssetKey() {
    return iconAssetKey;
  }

  public int getStoneSigilItemId() {
    return stoneSigilItemId;
  }

  public int getStoneSigilCount() {
    return stoneSigilCount;
  }

  public int getSilverSigilItemId() {
    return silverSigilItemId;
  }

  public int getSilverSigilCount() {
    return silverSigilCount;
  }

  public int getCasterIconItemId() {
    return casterIconItemId;
  }

  public boolean hasCasterIcon() {
    return casterIconItemId >= 0;
  }

  /** Compatibility accessor for the original presentation wire field name. */
  public int getCasterAnimationId() {
    return getOnEntityAnimationId();
  }

  /** Compatibility predicate for the original presentation wire field name. */
  public boolean hasCasterAnimation() {
    return hasOnEntityAnimation();
  }

  public int getOnEntityAnimationId() {
    return casterAnimationId;
  }

  public boolean hasOnEntityAnimation() {
    return casterAnimationId >= 0;
  }

  public List<ClericEffectRankDef> getEffectRanks() {
    return effectRanks;
  }

  public ClericEffectRankDef getEffectRank(int rank) {
    return rank <= 0 || rank > effectRanks.size() ? null : effectRanks.get(rank - 1);
  }
}
