package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.constants.Skill;
import java.util.function.BooleanSupplier;

/** Actor-lifetime phase state. Intangibility is evaluated at harm settlement, not attack launch. */
public final class NonCorporealState {
  private static final String KEY="non_corporeal_state";
  private final CombatParticipantSnapshot lifetime;
  private Object owner;
  private BooleanSupplier valid;
  private long untilTick;
  private boolean needsCorporealAttempt;
  private long lastEntryRollTick=Long.MIN_VALUE;
  private NonCorporealState(Mob mob){lifetime=CombatParticipantSnapshot.capture(mob);}
  private static NonCorporealState state(Mob mob,boolean install) {
    NonCorporealState state=mob.getAttribute(KEY,null);
    if(state!=null&&!state.lifetime.matches(mob)){mob.removeAttribute(KEY);state=null;}
    if(state==null&&install){state=new NonCorporealState(mob);mob.setAttribute(KEY,state);}
    return state;
  }
  private boolean active(Mob mob) {
    if(mob.isRemoved()||mob.killed||mob.getLevel(Skill.HITS.id())<=0||valid==null||!valid.getAsBoolean()) {
      owner=null;valid=null;untilTick=0;return false;
    }
    return mob.getWorld().getServer().getCurrentTick()<untilTick;
  }
  public static boolean isActive(Mob mob) {
    if(mob==null)return false;
    synchronized(mob){NonCorporealState state=state(mob,false);return state!=null&&state.active(mob);}
  }
  /** One chance roll per eligible tick. Recasts/source replacements never reset the rearm receipt. */
  public static boolean tryEnter(Mob mob,Object owner,int chancePercent,int ticks,BooleanSupplier valid) {
    if(mob==null||owner==null||valid==null||chancePercent<1||chancePercent>100||ticks<1)
      throw new IllegalArgumentException("Invalid phase entry");
    synchronized(mob) {
      NonCorporealState state=state(mob,true);
      long tick=mob.getWorld().getServer().getCurrentTick();
      if(state.active(mob)||state.needsCorporealAttempt||state.lastEntryRollTick==tick||!valid.getAsBoolean())return false;
      state.lastEntryRollTick=tick;
      if(mob.getWorld().getServer().getCombatRandom().nextDouble()*100>=chancePercent)return false;
      state.owner=owner;state.valid=valid;state.untilTick=tick+ticks;state.needsCorporealAttempt=true;
      return true;
    }
  }
  /** Invoked exactly once at an admitted attack commit, never from polls, impacts or extra victims. */
  public static void attackCommitted(Mob mob) {
    synchronized(mob){NonCorporealState state=state(mob,false);if(state!=null&&!state.active(mob))state.needsCorporealAttempt=false;}
  }
  public static void clearOwned(Mob mob,Object owner) {
    synchronized(mob){NonCorporealState state=state(mob,false);if(state!=null&&state.owner==owner){state.owner=null;state.valid=null;state.untilTick=0;}}
  }
  public static boolean blocks(Mob source,Mob target,CombatHarmNature nature) {
    return nature==CombatHarmNature.NONMAGICAL && (isActive(source)||isActive(target));
  }
}
