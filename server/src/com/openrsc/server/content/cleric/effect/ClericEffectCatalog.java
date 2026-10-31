package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.*;
import java.util.*;

/** One composite snapshot per god, with a common60-second lifetime. */
public final class ClericEffectCatalog {
  private static final Map<
          ClericSpellId, List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>>
      BY_SPELL =
          new EnumMap<
              ClericSpellId, List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>>(
              ClericSpellId.class);
  private static final List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ALL;

  static {
    List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> all =
        new ArrayList<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>();
    for (ClericSpellDefinition spell : ClericSpellCatalog.getAll())
      if (spell.getId().isBlessing()) {
        List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ranks =
            new ArrayList<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>();
        ClericEffectFamily family =
            spell.getAlignment() == ClericAlignment.SARADOMIN
                ? ClericEffectFamily.SARADOMIN_BLESSING
                : spell.getAlignment() == ClericAlignment.GUTHIX
                    ? ClericEffectFamily.GUTHIX_BLESSING
                    : ClericEffectFamily.ZAMORAK_BLESSING;
        for (int power = 1; power <= spell.getEffectRankCount(); power++)
          ranks.add(
              new ClericEffectRankDefinition<ClericBlessingMagnitude>(
                  spell.getId(),
                  family,
                  power,
                  spell.getSpellTier(),
                  ClericEffectDuration.seconds(60),
                  ClericEffectCounterKind.NONE,
                  0,
                  new ClericBlessingMagnitude(spell.getAlignment(), spell.getSpellTier(), power)));
        BY_SPELL.put(spell.getId(), Collections.unmodifiableList(ranks));
        all.addAll(ranks);
      }
    ALL = Collections.unmodifiableList(all);
  }

  private ClericEffectCatalog() {}

  public static boolean hasTimedEffect(ClericSpellId id) {
    return id != null && BY_SPELL.containsKey(id);
  }

  public static List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> getAll() {
    return ALL;
  }

  public static List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> getRanks(
      ClericSpellId id) {
    List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> list = BY_SPELL.get(id);
    return list == null
        ? Collections.<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>emptyList()
        : list;
  }

  public static ClericEffectRankDefinition<? extends ClericEffectMagnitude> get(
      ClericSpellId id, int rank) {
    List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> list = getRanks(id);
    if (rank < 1 || rank > list.size()) throw new IllegalArgumentException("Unknown effect rank");
    return list.get(rank - 1);
  }
}
