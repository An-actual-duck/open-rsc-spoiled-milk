package com.openrsc.server.content.npccombat;

import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.entity.npc.NpcAttackStyleProfile;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import java.util.*;

/** Immutable, complete modern values. Zero is a value, never a fallback request. */
public final class NpcCombatDefinition {
  public final int id;
  public final String name;
  private final int[] power, defense, weights;
  private final Set<CombatStyle> styles;
  private final Set<String> tags;
  private final List<NpcMagicElement> elements;
  public final int holyPower, projectileRange;
  public final boolean boss, genericDispatch;
  public final CombatStyle primary;
  public final PrayerCatalog.GodLine magicGod;
  public final String rangedVisual;
  private final String metadataJson;

  NpcCombatDefinition(int id, String name, int[] power, int[] defense, int holyPower,
      boolean boss, CombatStyle primary, Set<CombatStyle> styles, int[] weights,
      int range, boolean generic, List<NpcMagicElement> elements,
      PrayerCatalog.GodLine god, String visual, Set<String> tags, String metadata) {
    this.id=id;this.name=name;this.power=power.clone();this.defense=defense.clone();
    this.holyPower=holyPower;this.boss=boss;this.primary=primary;
    this.styles=Collections.unmodifiableSet(EnumSet.copyOf(styles));
    this.weights=weights.clone();this.projectileRange=range;this.genericDispatch=generic;
    this.elements=Collections.unmodifiableList(new ArrayList<>(elements));
    this.magicGod=god;this.rangedVisual=visual;
    this.tags=Collections.unmodifiableSet(new HashSet<>(tags));this.metadataJson=metadata;
  }
  private static int index(CombatStyle style) {
    switch(style){case MELEE:return 0;case RANGED:return 1;case MAGIC:return 2;default:throw new IllegalArgumentException("Not an NPC primary style: "+style);}
  }
  public int power(CombatStyle style){return power[index(style)];}
  public int defense(CombatStyle style){return defense[index(style)];}
  public int weight(CombatStyle style){return weights[index(style)];}
  public boolean uses(CombatStyle style){return styles.contains(style);}
  public Set<CombatStyle> styles(){return styles;}
  public Set<String> tags(){return tags;}
  public List<NpcMagicElement> elements(){return elements;}
  /** Detached copy: changing inspection metadata cannot mutate live combat. */
  public org.json.JSONObject metadata(){return new org.json.JSONObject(metadataJson);}
  public NpcAttackStyleProfile genericProfile(){
    if(styles.size()==1)return uses(CombatStyle.MAGIC)?NpcAttackStyleProfile.PURE_MAGIC:
        uses(CombatStyle.RANGED)?NpcAttackStyleProfile.PURE_RANGED:NpcAttackStyleProfile.MELEE;
    return uses(CombatStyle.RANGED)?NpcAttackStyleProfile.MELEE_RANGED:NpcAttackStyleProfile.MELEE_MAGIC;
  }
}
