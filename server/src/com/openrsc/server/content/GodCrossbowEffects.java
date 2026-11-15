package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.death.DeathContext;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Holy-bolt primary effects. Launch facts never depend on equipment at impact. */
public final class GodCrossbowEffects {
    private static final String STATE="god_crossbow_effect_state", SEEDS="god_crossbow_briar_seeds", MARKS="god_crossbow_revelation_marks";
    private static final AtomicLong EPOCH=new AtomicLong();
    public static final int MAX_FAMILIARITY=15, SEEDS_PER_BURST=5, BURST_RADIUS=1, BURST_MAX=3;
    private GodCrossbowEffects() { }

    private static final class State {
        final int weapon; final long life; long epoch=EPOCH.incrementAndGet();
        String family; int familiarity;
        State(Player player){weapon=GodCrossbows.equippedId(player);life=player.getCombatLifecycle();}
    }
    /** Immutable effect identity carried by one committed holy-bolt launch. */
    public static final class Shot {
        private final PrayerCatalog.GodLine god;
        private final long epoch,sourceLife;
        private final String family; private final UUID sourceId;
        private Shot(PrayerCatalog.GodLine god,State state,Player source){this.god=god;epoch=state.epoch;sourceLife=state.life;family=state.family;sourceId=source.getUUID();}
        public PrayerCatalog.GodLine getGod(){return god;}
    }
    private static final class Seeds {
        final WeakReference<Player> owner; final long sourceLife,targetLife; int count;
        Seeds(Player p,Mob target){owner=new WeakReference<>(p);sourceLife=p.getCombatLifecycle();targetLife=target.getCombatLifecycle();}
        boolean valid(Mob target){Player p=owner.get();return alive(p)&&p.getCombatLifecycle()==sourceLife&&target.getCombatLifecycle()==targetLife;}
    }
    private static final class KillMark {
        final long epoch,sourceLife,targetLife; final String family;
        KillMark(Shot shot,Mob target){epoch=shot.epoch;sourceLife=shot.sourceLife;targetLife=target.getCombatLifecycle();family=shot.family;}
    }

    private static boolean alive(Player player){return player!=null&&player.loggedIn()&&!player.isRemoved()&&!player.killed&&player.getLevel(Skill.HITS.id())>0;}
    public static void clear(Player player){if(player!=null)player.removeAttribute(STATE);}
    /** Equipment/cape changes preserve familiarity only while the same bow/prayer remains usable. */
    public static void equipmentChanged(Player player){state(player,false);}
    private static State state(Player player,boolean create){
        if(player==null)return null;
        State state=player.getAttribute(STATE,null);
        if(!GodCrossbows.usesHolyBolts(player)||!alive(player)){clear(player);return null;}
        if(state!=null&&(state.life!=player.getCombatLifecycle()||state.weapon!=GodCrossbows.equippedId(player))){clear(player);state=null;}
        if(state==null&&create){state=new State(player);player.setAttribute(STATE,state);}
        return state;
    }
    /** Authored Slayer families group known variants; other definitions retain their own stable type. */
    public static String enemyType(Player source,Mob target){
        if(target instanceof Player)return "player";
        if(!(target instanceof Npc))return "other:"+target.getID();
        MonsterSlayerData data=source.getWorld().getMonsterSlayerData();
        if(data!=null)for(MonsterSlayerDefinitions.Family family:data.getFamilies())
            if(family.getNpcIds().contains(target.getID()))return "slayer:"+family.getKey();
        return "npc:"+target.getID();
    }
    public static Shot capture(Player player,Mob target){
        State state=state(player,true);if(state==null||target==null)return null;
        PrayerCatalog.GodLine god=GodCrossbows.god(state.weapon);
        if(god==PrayerCatalog.GodLine.SARADOMIN){
            String family=enemyType(player,target);
            if(!family.equals(state.family)){state.family=family;state.familiarity=0;state.epoch=EPOCH.incrementAndGet();}
        }
        return new Shot(god,state,player);
    }
    public static int familiarity(Player player){State state=state(player,false);return state==null?0:state.familiarity;}
    public static int revelationPercent(Mob source,Mob target,int weapon){
        if(!(source instanceof Player)||GodCrossbows.god(weapon)!=PrayerCatalog.GodLine.SARADOMIN)return 0;
        Player player=(Player)source;State state=state(player,false);
        return state!=null&&state.weapon==weapon&&Objects.equals(state.family,enemyType(player,target))?state.familiarity:0;
    }
    /** Monotone quantile lift: no extra RNG, unchanged ceiling, before the independent defense roll. */
    public static int liftRoll(int roll,int maximum,int percent){
        if(maximum<=0)return 0;roll=Math.max(0,Math.min(maximum,roll));
        int floor=(int)((long)maximum*Math.max(0,Math.min(MAX_FAMILIARITY,percent))/100);
        return floor+(int)((long)roll*(maximum-floor)/maximum);
    }
    public static void primaryImpact(Player source,Mob target,Shot shot,AttackRoll roll,int actualDamage,boolean suppressed,UUID eventId){
        if(shot==null||suppressed||!alive(source)||!source.getUUID().equals(shot.sourceId)||source.getCombatLifecycle()!=shot.sourceLife
            ||target==null||target.isRemoved()||target.killed||!source.sharesSpatialDomain(target))return;
        if(shot.god==PrayerCatalog.GodLine.ZAMORAK){
            Bleed.primaryHit(source,target,2,roll,actualDamage,CombatStyle.RANGED);
        }else if(shot.god==PrayerCatalog.GodLine.GUTHIX){
            seed(source,target,eventId);
        }else if(shot.god==PrayerCatalog.GodLine.SARADOMIN){
            State state=state(source,false);
            if(state!=null&&state.epoch==shot.epoch&&Objects.equals(shot.family,enemyType(source,target))){
                Map<UUID,KillMark> marks=target.getAttribute(MARKS,null);
                if(marks==null){marks=new HashMap<>();target.setAttribute(MARKS,marks);}
                marks.entrySet().removeIf(e->source.getWorld().getPlayerByUUID(e.getKey())==null);
                marks.put(source.getUUID(),new KillMark(shot,target));
            }
        }
    }
    /** Authoritative kill reward hook, guarded by the accepted shot and the target death lifetime. */
    public static void creditedKill(Player player,Mob target){
        Map<UUID,KillMark> marks=target.getAttribute(MARKS,null);if(marks==null)return;
        KillMark mark=marks.remove(player.getUUID());if(marks.isEmpty())target.removeAttribute(MARKS);
        State state=state(player,false);DeathContext death=target.getDeathLifecycleSnapshot().getContext();
        if(mark==null||state==null||death==null||mark.targetLife!=death.getTargetCombatLifecycle()
            ||mark.sourceLife!=player.getCombatLifecycle()||mark.epoch!=state.epoch
            ||!Objects.equals(mark.family,state.family)||state.familiarity>=MAX_FAMILIARITY)return;
        state.familiarity++;player.message("Revelation grows to "+state.familiarity+"% familiarity.");
    }
    private static void seed(Player source,Mob primary,UUID eventId){
        Map<UUID,Seeds> seeds=primary.getAttribute(SEEDS,null);
        if(seeds==null){seeds=new HashMap<>();primary.setAttribute(SEEDS,seeds);}
        seeds.values().removeIf(counter->!counter.valid(primary));
        Seeds counter=seeds.get(source.getUUID());
        if(counter==null){counter=new Seeds(source,primary);seeds.put(source.getUUID(),counter);}
        if(++counter.count<SEEDS_PER_BURST)return;
        seeds.remove(source.getUUID());if(seeds.isEmpty())primary.removeAttribute(SEEDS);
        List<Mob> targets=new ArrayList<>();targets.add(primary);
        if(primary instanceof Npc)targets.addAll(CombatEffectUtil.findPlayerOwnedNpcSplashTargets(source,(Npc)primary,BURST_RADIUS));
        else if(primary instanceof Player)targets.addAll(CombatEffectUtil.findPlayerOwnedPvpSplashTargets(source,(Player)primary,BURST_RADIUS));
        for(Mob target:targets){
            if(!alive(source)||target.isRemoved()||target.killed||target.getLevel(Skill.HITS.id())<=0
                ||!source.sharesSpatialDomain(target)||!target.withinRange(primary.getLocation(),BURST_RADIUS)
                ||NonCorporealState.blocks(source,target,CombatHarmNature.NONMAGICAL))continue;
            if(target instanceof Player){
                if(target==primary){if(!PlayerPvpCombat.eligible(source,(Player)target,CombatStyle.RANGED))continue;}
                else if(!CombatEffectUtil.isPlayerOwnedPvpSplashTarget(source,primary,target))continue;
            }else if(!(target instanceof Npc)||Summoning.isSummon((Npc)target)||!((Npc)target).getDef().isAttackable())continue;
            int damage=CombatLuck.higher(source,source.getWorld().getServer().getCombatRandom(),
                ()->source.getWorld().getServer().getCombatRandom().nextIntInclusive(0,BURST_MAX));
            if(target instanceof Player&&target!=primary){
                PlayerPvpCombat.secondaryDamage(source,primary,(Player)target,damage,CombatStyle.RANGED,
                    SecondaryEffectPolicy.GUTHIX_BRIAR_SEED.getStableKey(),eventId,AttackRoll.normal(damage));
            }else applyBurst(source,primary,target,damage,eventId);
        }
    }
    private static void applyBurst(Player source,Mob primary,Mob target,int damage,UUID eventId){
        if(target instanceof Player){Player p=(Player)target;damage=p.applyRobeDamageMitigation(damage);damage=p.applyPotionRangedDamageReduction(damage);damage=TrueDefense.apply(p,damage);}
        damage=com.openrsc.server.content.monsterslayer.DarkBeastCombat.mitigate(target,damage);
        DamageResult result=source.getWorld().getServer().getResolvedDamageTransaction().apply(
            DamageRequest.resolvedLegacy(source,target,DamageRequest.SourceCategory.OWNED_EFFECT,
                SecondaryEffectPolicy.GUTHIX_BRIAR_SEED.getStableKey(),damage).eventId(eventId)
                .style(CombatStyle.RANGED).harmNature(CombatHarmNature.NONMAGICAL).hitSplatType(HitSplat.TYPE_STANDARD).build());
        if(target instanceof Npc){
            Npc npc=(Npc)target;npc.addCombatDamage(source,result.getActualDamage());
            if(result.isTargetTerminal()&&target!=primary){source.setKillType(KillType.RANGED);npc.killedBy(source);}
            else if(!result.isTargetTerminal()&&!npc.isChasing()&&!npc.inCombat()&&npc.getCombatState()!=CombatState.RUNNING){
                Player preferred=npc.getPreferredThreatTarget();npc.setChasing(preferred!=null?preferred:source);
            }
        }else {Player p=(Player)target;PlayerPvpCombat.recordPrimaryDamage(source,p,result.getActualDamage());ActionSender.sendStat(p,Skill.HITS.id());}
    }
    public static int seeds(Player source,Mob target){
        Map<UUID,Seeds> seeds=target.getAttribute(SEEDS,null);Seeds counter=seeds==null?null:seeds.get(source.getUUID());return counter!=null&&counter.valid(target)?counter.count:0;
    }
}
