package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.event.rsc.*;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.*;

/** NPC-owned hostile god slots. No party, raw-coordinate, or already-in-combat requirement. */
public final class ClericHostileRuntime {
  private static final String KEY="cleric_hostile_state";
  private ClericHostileRuntime() {}

  static ClericSupportCasting.CastResult cast(Player caster,ClericSpellDefinition spell) {
    synchronized(caster) {
      int power=spell.resolveEffectRank(caster.getCarriedItems().getEquipment().getHolyPower());
      ClericEffectRankDefinition<?> definition=ClericEffectCatalog.get(spell.getId(),power);
      List<Npc> candidates=new ArrayList<Npc>();
      for(Npc npc:caster.getViewArea().getNpcsInView())
        if(eligible(caster,npc,spell.getRadius())) candidates.add(npc);
      if(candidates.isEmpty())return result(ClericSupportCasting.Outcome.NO_USEFUL_RECIPIENT,0);
      List<Item> costs=new ArrayList<Item>();
      for(ClericResourceCost cost:spell.getResourceCosts())costs.add(new Item(cost.getItemId(),cost.getCount(),false));
      final int[] applied={0};
      boolean spent=caster.getCarriedItems().removeWithStateChange(costs.toArray(new Item[0]),false,()->{
        for(Npc npc:candidates) synchronized(npc) {
          if(!eligible(caster,npc,spell.getRadius()))continue;
          State state=state(npc,true);
          ClericEffectOrigin origin=ClericEffectOrigin.hostile(caster.getTransientEffectSessionToken(),
              npc.getWorldLocation().getWorldSpace(),npc.getWorldLocation().getCoordinate().getLevel());
          Source source=new Source(caster,npc);
          state.origins.put(origin,source);
          if(state.registry.apply(definition,origin,state.validator).isUseful()) {
            applied[0]++;
            if(spell.getPresentation().hasOnEntityAnimation())
              npc.getUpdateFlags().setCombatEffect(new com.openrsc.server.model.entity.update.CombatEffect(npc,spell.getPresentation().getOnEntityAnimationId()));
            state.pruneOrigins();
            state.ensureTick();
          } else state.origins.remove(origin);
        }
        return applied[0]>0;
      });
      if(spent)ActionSender.sendInventory(caster);
      return result(spent?ClericSupportCasting.Outcome.SUCCESS:
          applied[0]==0&&allIneffective(caster,candidates,definition)?ClericSupportCasting.Outcome.NO_USEFUL_RECIPIENT:
          ClericSupportCasting.Outcome.INSUFFICIENT_SIGILS,applied[0]);
    }
  }
  private static boolean allIneffective(Player caster,List<Npc> candidates,ClericEffectRankDefinition<?> definition) {
    for(Npc npc:candidates)if(eligible(caster,npc,6)) {
      State state=state(npc,false);
      if(state==null)return false;
      ClericEffectOrigin origin=ClericEffectOrigin.hostile(caster.getTransientEffectSessionToken(),
          npc.getWorldLocation().getWorldSpace(),npc.getWorldLocation().getCoordinate().getLevel());
      state.origins.put(origin,new Source(caster,npc));
      boolean useful=state.registry.preview(definition,origin,state.validator).isUseful();
      state.origins.remove(origin);
      if(useful)return false;
    }
    return true;
  }
  private static ClericSupportCasting.CastResult result(ClericSupportCasting.Outcome outcome,int count) {
    return new ClericSupportCasting.CastResult(outcome,count);
  }
  public static boolean eligible(Player caster,Npc npc,int radius) {
    if(caster==null||npc==null||ClericSupportCasting.isPvpContext(caster))return false;
    if(!CombatEligibility.evaluate(CombatEligibilityRequest.builder(caster,npc,CombatEligibilityPhase.COMMIT,CombatStyle.MAGIC)
        .currentParticipants(true).registration(true).sameSpatialDomain(true).summonRules(true).playerAttackRules(true).build()).isAllowed())return false;
    if(!caster.getWorldLocation().getWorldSpace().equals(npc.getWorldLocation().getWorldSpace())
        ||caster.getWorldLocation().getCoordinate().getLevel()!=npc.getWorldLocation().getCoordinate().getLevel())return false;
    int dx=Math.abs(caster.getWorldLocation().getCoordinate().getX()-npc.getWorldLocation().getCoordinate().getX());
    int dy=Math.abs(caster.getWorldLocation().getCoordinate().getY()-npc.getWorldLocation().getCoordinate().getY());
    return Math.max(dx,dy)<=radius && PathValidation.checkPath(caster.getWorld(),caster.getWorldLocation(),npc.getWorldLocation(),false);
  }
  public static ClericHostileMagnitude effect(Npc npc,ClericEffectFamily family) {
    State state=state(npc,false);
    if(state==null)return null;
    Optional<ClericEffectEntry> entry=state.registry.get(family,state.validator);
    return entry.isPresent()?(ClericHostileMagnitude)entry.get().getDefinition().getMagnitude():null;
  }

  public static int reduceMagicDefense(Npc npc,int base) {
    ClericHostileMagnitude effect=effect(npc,ClericEffectFamily.SARADOMIN_HOSTILE);
    return reducePercent(base,effect==null?0:effect.magicDefenseReduction());
  }
  public static int reduceMaximumMelee(Npc npc,int maximum) {
    ClericHostileMagnitude effect=effect(npc,ClericEffectFamily.SARADOMIN_HOSTILE);
    return reducePercent(maximum,effect==null?0:effect.maximumMeleeReduction());
  }
  private static int reducePercent(int base,int percent) {
    return Math.max(0,base-(int)Math.min(Integer.MAX_VALUE,((long)Math.max(0,base)*percent+99)/100));
  }
  public static int poisonCleanse(Npc npc,int base) {
    ClericHostileMagnitude effect=effect(npc,ClericEffectFamily.GUTHIX_HOSTILE);
    return base<=0?base:Math.max(1,reducePercent(base,effect==null?0:effect.poisonWeakness()));
  }
  /** Exactly one family amplification at settlement, without child effects or reflection recursion. */
  public static DamageRequest amplify(DamageRequest request) {
    if(!(request.getTarget() instanceof Npc)||request.getResolvedDamage()<=0)return request;
    ClericHostileMagnitude effect=effect((Npc)request.getTarget(),ClericEffectFamily.GUTHIX_HOSTILE);
    if(effect==null)return request;
    int percent=0;
    SecondaryEffectPolicy policy=SecondaryEffectPolicy.forStableKey(request.getEffectKey());
    if("generic-poison".equals(request.getEffectKey()))percent=effect.poisonWeakness();
    else if(policy!=null&&policy.getFamily()==SecondaryEffectPolicy.Family.REFLECTION)percent=effect.recoilWeakness();
    else if(com.openrsc.server.content.Summoning.isSummon(request.getSource()))percent=effect.summonWeakness();
    if(percent<=0)return request;
    int damage=(int)Math.min(Integer.MAX_VALUE,(long)request.getResolvedDamage()+((long)request.getResolvedDamage()*percent+99)/100);
    return request.withResolvedDamage(damage);
  }
  private static State state(Npc npc,boolean install) {
    synchronized(npc) {
      State state=npc.getAttribute(KEY,null);
      if(state!=null&&!state.life.matches(npc)) {npc.removeAttribute(KEY);state=null;}
      if(state==null&&install){state=new State(npc);npc.setAttribute(KEY,state);}
      return state;
    }
  }
  private static final class Source {
    final Player caster;
    final CombatParticipantSnapshot casterLife,targetLife;
    Source(Player caster,Npc target){this.caster=caster;casterLife=CombatParticipantSnapshot.capture(caster);targetLife=CombatParticipantSnapshot.capture(target);}
    boolean current(Npc target,ClericEffectOrigin origin) {
      return casterLife.matches(caster)&&targetLife.matches(target)&&caster.getTransientEffectSessionToken()==origin.getCasterSession()
          &&origin.matchesScope(caster.getWorldLocation().getWorldSpace(),caster.getWorldLocation().getCoordinate().getLevel())
          &&origin.matchesScope(target.getWorldLocation().getWorldSpace(),target.getWorldLocation().getCoordinate().getLevel())
          &&caster.isLoggedIn()&&!caster.isRemoved()&&!caster.isUnregistering()&&caster.getLevel(Skill.HITS.id())>0
          &&!ClericSupportCasting.isPvpContext(caster)&&target.getWorld().getNpcByUUID(target.getUUID())==target
          &&!target.isRemoved()&&!target.isUnregistering()&&!target.isRespawning()&&!target.killed&&target.getLevel(Skill.HITS.id())>0;
    }
  }
  private static final class State {
    final Npc npc;
    final CombatParticipantSnapshot life;
    final ClericEffectRegistry registry;
    final Map<ClericEffectOrigin,Source> origins=new IdentityHashMap<ClericEffectOrigin,Source>();
    final ClericEffectOriginValidator validator;
    boolean ticking;
    State(Npc npc) {
      this.npc=npc;life=CombatParticipantSnapshot.capture(npc);
      registry=new ClericEffectRegistry(ClericEffectClock.game(npc.getWorld().getServer().getGameClock(),npc.getConfig().GAME_TICK));
      validator=origin->{Source source=origins.get(origin);return source!=null&&source.current(npc,origin);};
    }
    void pruneOrigins(){Set<ClericEffectOrigin> active=Collections.newSetFromMap(new IdentityHashMap<ClericEffectOrigin,Boolean>());
      for(ClericEffectEntry entry:registry.snapshot(validator))active.add(entry.getOrigin());
      origins.keySet().retainAll(active);
    }
    void ensureTick(){if(ticking)return;ticking=true;
      npc.getWorld().getServer().getGameEventHandler().addOrUpdate(new GameTickEvent(npc.getWorld(),npc,1,"Cleric Hostile Effects",DuplicationStrategy.ONE_PER_MOB){
        public void run(){synchronized(npc){
          if(!life.matches(npc)||registry.size(validator)==0){NonCorporealState.clearOwned(npc,State.this);ticking=false;stop();return;}
          pruneOrigins();
          ClericHostileMagnitude phase=effect(npc,ClericEffectFamily.SARADOMIN_HOSTILE);
          if(phase!=null&&phase.phaseChance()>0) NonCorporealState.tryEnter(npc,State.this,phase.phaseChance(),phase.phaseTicks(),
              ()->effect(npc,ClericEffectFamily.SARADOMIN_HOSTILE)!=null);
        }}
      });
    }
  }
}
