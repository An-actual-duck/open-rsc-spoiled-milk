package com.openrsc.server.content.cleric;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;

/** Server-owned Hallowed Strikes classification; never infer gameplay type from display names. */
public final class UndeadClassification {
  private UndeadClassification() {}

  public static boolean legacy(int id) {
    for (int candidate : Constants.UNDEAD_NPCS) if (candidate == id) return true;
    return false;
  }

  public static boolean isUndead(Mob mob) {
    if (!(mob instanceof Npc)) return false;
    Npc npc = (Npc) mob;
    NPCDef definition = npc.getDef();
    if (definition.undead != null) return definition.undead;
    return legacy(npc.getID()) || npc.getAttribute("isUndead", false);
  }
}
