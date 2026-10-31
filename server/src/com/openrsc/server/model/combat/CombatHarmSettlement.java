package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

/** Explicit adapter for legacy immediate hostile harm; genuine script/environment HP APIs remain unchanged. */
public final class CombatHarmSettlement {
  private CombatHarmSettlement() {}
  public static DamageResult immediate(Mob source,Mob target,int damage,CombatHarmNature nature,String key) {
    DamageRequest request=DamageRequest.resolvedLegacy(source,target,DamageRequest.SourceCategory.ACTOR,key,Math.max(0,damage))
        .harmNature(nature).build();
    DamageResult result=target.getWorld().getServer().getResolvedDamageTransaction().apply(request);
    if(target instanceof Player) {
      if(result.getActualDamage()>0)target.setAttribute("last_damage_taken_at",System.currentTimeMillis());
      ActionSender.sendStat((Player)target,Skill.HITS.id());
    }
    if(result.isTargetTerminal())target.killedBy(source);
    return result;
  }
}
