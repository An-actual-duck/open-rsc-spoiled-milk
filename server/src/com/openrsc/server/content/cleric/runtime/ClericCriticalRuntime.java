package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.effect.ClericHostileMagnitude;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerBossTasks;
import com.openrsc.server.content.monsterslayer.SlayerRewardBosses;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;
import java.util.function.IntSupplier;

/** NPC-owned critical vulnerability. Never replays an attack or a secondary effect. */
public final class ClericCriticalRuntime {
  private ClericCriticalRuntime() {}

  private static boolean eligible(Mob source, Mob target, CombatStyle style) {
    return source instanceof Player && target instanceof Npc
        && !ClericSupportCasting.isPvpContext((Player) source)
        && CombatEligibility.evaluate(CombatEligibilityRequest.builder(source, target,
            CombatEligibilityPhase.COMMIT, style)
            .currentParticipants(true).registration(true).sameSpatialDomain(true)
            .summonRules(true).playerAttackRules(true).build()).isAllowed();
  }

  /** Launch-time primary roll. Other players benefit from the victim's same god slot. */
  public static AttackRoll primaryRoll(Mob source, Mob target, int maximum,
      boolean primary, CombatStyle style, IntSupplier ordinary) {
    ClericHostileMagnitude effect = primary && source instanceof Player && target instanceof Npc
        ? ClericHostileRuntime.effect((Npc) target,ClericEffectFamily.ZAMORAK_HOSTILE) : null;
    if (effect == null || !eligible(source,target,style))
      return CriticalHitResolver.ordinary(source,maximum,ordinary);
    return CriticalHitResolver.resolve(source,maximum,effect.criticalChance()/100.0D,
        AttackRoll.Critical.AUTHORED,source.getWorld().getServer().getCombatRandom(),ordinary);
  }

  private static boolean primary(DamageRequest request) {
    if (request.getSourceCategory()!=DamageRequest.SourceCategory.ACTOR) return false;
    switch(request.getEffectKey()) {
      case "pvm-melee-primary":
      case "reciprocal-melee-primary":
        return request.getStyle()==CombatStyle.MELEE;
      case "projectile-player-ranged-primary":
      case "projectile-player-thrown-primary":
        return request.getStyle()==CombatStyle.RANGED || request.getStyle()==CombatStyle.THROWING;
      case "projectile-player-magic-primary":
      case "projectile-iban-primary":
        return request.getStyle()==CombatStyle.MAGIC;
      default:
        return false;
    }
  }

  /** Existing explicit percentage-damage boss policy plus independently authored boss tasks. */
  public static boolean executionImmune(Npc target) {
    if (SlayerRewardBosses.isBoss(target) || target.isPluginOwnedDeathCompatibility()) return true;
    for (MonsterSlayerBossTasks.Boss boss:MonsterSlayerBossTasks.Boss.values())
      if(boss.npcId==target.getID()) return true;
    // Existing quest transformation and restricted-spell encounters are not ordinary enemies.
    if(target.getID()==NpcId.SHAPESHIFTER_HUMAN.id()
        || target.getID()==NpcId.SHAPESHIFTER_SPIDER.id()
        || target.getID()==NpcId.SHAPESHIFTER_BEAR.id()
        || target.getID()==NpcId.SHAPESHIFTER_WOLF.id()
        || target.getID()==NpcId.SALARIN_THE_TWISTED.id()) return true;
    return false;
  }

  /**
   * Called once after phase rejection and before the existing Hits mutation.
   * Execution uses pre-hit HP, keeps the same request/attribution and lets the
   * original producer own its ordinary death/loot/XP path. Natural killing hits
   * consume neither execution nor Stagger RNG. Recovery never lives in a curse.
   */
  public static DamageRequest beforeSettlement(DamageRequest request) {
    if (!primary(request) || request.isAttackSuppressed() || request.getResolvedDamage()<=0
        || request.getAttackRoll().getDamage()<=0
        || request.getAttackRoll().getCritical()!=AttackRoll.Critical.AUTHORED
        || !eligible(request.getSource(),request.getTarget(),request.getStyle())) return request;
    Npc target=(Npc)request.getTarget();
    synchronized(target) {
      int hp=target.getLevel(Skill.HITS.id());
      if(request.getResolvedDamage()>=hp) return request;
      ClericHostileMagnitude effect=ClericHostileRuntime.effect(target,ClericEffectFamily.ZAMORAK_HOSTILE);
      if(effect==null) return request;
      GameRandom random=target.getWorld().getServer().getCombatRandom();
      if(effect.executionChance()>0 && !executionImmune(target)
          && (long)hp*100L<=(long)target.getSkills().getMaxStat(Skill.HITS.id())*effect.executionThreshold()
          && CombatLuck.roll(request.getSource(),effect.executionChance()/100.0D,random))
        return request.withResolvedDamage(hp);
      if(effect.staggerChance()>0 && AttackState.pendingStaggers(target)==0
          && AttackState.recoveryAttacks(target)==0
          && CombatLuck.roll(request.getSource(),effect.staggerChance()/100.0D,random))
        AttackState.stagger(target,1);
      return request;
    }
  }
}
