package com.openrsc.server.content.npccombat;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;

/** Modern authority and attack decisions. Legacy is an explicit compatibility boundary only. */
public final class CanonicalNpcCombat {
  private CanonicalNpcCombat(){}
  private static final String STATE="canonical_npc_attack_state_v1";
  public static NpcCombatDefinition definition(Npc npc){
    if(npc==null||!npc.getConfig().WANT_MYWORLD||Summoning.isSummon(npc))return null;
    NpcCombatCatalog catalog=npc.getWorld().getServer().getNpcCombatCatalog();
    NpcCombatDefinition d=catalog.get(npc.getID());
    if(d==null&&catalog.compatibilityPolicy(npc.getID())==null)
      throw new IllegalStateException("NPC has no canonical or explicit compatibility record: "+npc.getID());
    return d;
  }
  public static boolean generic(Mob mob){
    if(!(mob instanceof Npc))return false;NpcCombatDefinition d=definition((Npc)mob);return d!=null&&d.genericDispatch;
  }
  /** Keep original dice for intentionally excluded minigames/summons/legacy actors. */
  public static int defenseRollMinimum(Mob source,Mob target){
    for(Mob mob:new Mob[]{source,target}){
      if(mob!=null&&!mob.getConfig().WANT_MYWORLD)return 1;
      if(mob instanceof Npc&&definition((Npc)mob)==null)return 1;
    }
    return 0;
  }
  private static final class State {
    final CombatParticipantSnapshot life;
    long nextTick;
    CombatParticipantSnapshot target;
    CombatStyle selected;
    State(Npc n){life=CombatParticipantSnapshot.capture(n);}
  }
  private static State state(Npc n){
    State s=n.getAttribute(STATE,null);if(s==null||!s.life.matches(n)){s=new State(n);n.setAttribute(STATE,s);}return s;
  }
  public static boolean ready(Npc n){
    return n.getWorld().getServer().getCurrentTick()>=state(n).nextTick
      && com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.attackReady(n)
      && !com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.recovering(n);
  }
  /** A decision persists until commitment or eligibility/target changes, never rerolled per poll. */
  public static CombatStyle select(Npc n,Mob target,boolean meleeEligible,boolean shotEligible){
    NpcCombatDefinition d=definition(n);if(d==null||!d.genericDispatch||target==null||!ready(n))return null;
    State s=state(n);boolean same=s.target!=null&&s.target.matches(target);
    if(same&&s.selected!=null&&eligible(s.selected,meleeEligible,shotEligible))return s.selected;
    s.selected=null;s.target=CombatParticipantSnapshot.capture(target);
    int total=0,count=0;CombatStyle only=null;
    for(CombatStyle style:d.styles())if(eligible(style,meleeEligible,shotEligible)){total+=d.weight(style);count++;only=style;}
    if(count==0)return null;
    if(count==1){s.selected=only;return only;}
    int roll=n.getWorld().getServer().getCombatRandom().nextInt(total);
    for(CombatStyle style:d.styles())if(eligible(style,meleeEligible,shotEligible)){roll-=d.weight(style);if(roll<0){s.selected=style;return style;}}
    throw new IllegalStateException("Invalid canonical attack weight total");
  }
  private static boolean eligible(CombatStyle style,boolean melee,boolean shot){return style==CombatStyle.MELEE?melee:shot;}
  public static void committed(Mob source,int delayTicks){
    if(!(source instanceof Npc)||!generic(source))return;Npc n=(Npc)source;State s=state(n);
    s.nextTick=n.getWorld().getServer().getCurrentTick()+Math.max(1,delayTicks);s.selected=null;s.target=null;
  }
}
