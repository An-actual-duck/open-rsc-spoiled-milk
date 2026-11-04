package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.cleric.ClericResourceCost;
import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.runtime.ClericCriticalRuntime;
import com.openrsc.server.content.cleric.runtime.ClericDirectCombatRuntime;
import com.openrsc.server.content.cleric.runtime.ClericHostileRuntime;
import com.openrsc.server.content.cleric.runtime.ClericSupportCasting;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.AttackRoll;
import com.openrsc.server.model.combat.AttackState;
import com.openrsc.server.model.combat.ChainLightningTraversalPolicy;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.HellsInfernoNpcSplash;
import com.openrsc.server.model.combat.NonCorporealState;
import com.openrsc.server.model.combat.PlayerPvpCombat;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.SplinterTargetSelectionPolicy;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.GameStateUpdater;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.netty.buffer.ByteBuf;
import static com.openrsc.server.combat.CurrentPvpFoundationCharacterization.areaPlayers;
import static com.openrsc.server.combat.CurrentPvpFoundationCharacterization.checkCase;
import static com.openrsc.server.combat.CurrentPvpFoundationCharacterization.require;

/** Player recipients of the same authored PvE effects, with actual death and wire adapters. */
final class CurrentPvpParityCharacterization {
    static void register(CurrentCombatHarness h,PvpSqliteLog logs,List<String> failures)throws Exception {
        checkCase(h,"pvp_chain_and_splinter_select_legal_enemies",CurrentPvpParityCharacterization::selectors,failures);
        checkCase(h,"pvp_blood_robes_and_splinter_settle_player_children",CurrentPvpParityCharacterization::projectileChildren,failures);
        checkCase(h,"pvp_terminal_damage_rewards_and_logs_once",x->terminal(x,logs),failures);
        checkCase(h,"pvp_death_amulet_bursts_on_player_kills",x->deathBurst(x,logs),failures);
        checkCase(h,"pvp_summons_assist_and_retaliation_targets_owner",CurrentPvpParityCharacterization::summonAssist,failures);
        checkCase(h,"pvp_summon_projectiles_revalidate_owner_and_allies",CurrentPvpParityCharacterization::summonShots,failures);
        checkCase(h,"pvp_summon_kills_credit_the_owner_once",x->summonKill(x,logs),failures);
        checkCase(h,"pvp_saradomin_curse_and_phase_wire",CurrentPvpParityCharacterization::phaseWire,failures);
        checkCase(h,"pvp_guthix_weakness_uses_existing_values",CurrentPvpParityCharacterization::guthix,failures);
        checkCase(h,"pvp_zamorak_crits_stagger_and_execute",x->crit(x,logs),failures);
        checkCase(h,"pvp_ignores_legacy_retreat_immunity",CurrentPvpParityCharacterization::retreat,failures);
    }
    private static Object call(Object o,String name,Class<?>[] types,Object...args)throws Exception {
        return CurrentCombatHarness.invokePrivate(o,name,types,args);
    }
    private static void field(Object o,String name,Object value)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    private static void selectors(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"pvp select");
        require(ChainLightningTraversalPolicy.selectCombatant(p[0],p[1],h.random())==p[2],"chain selects the only unrelated legal player");
        require(SplinterTargetSelectionPolicy.selectCombatant(p[0],p[1],h.random())==p[2],"Splinter selects the only unrelated legal player");
        Npc anchor=h.npc(3,181,199);
        require(ChainLightningTraversalPolicy.selectCombatant(p[0],anchor,h.random())==null,"NPC chain roots never acquire players");
        require(SplinterTargetSelectionPolicy.selectCombatant(p[0],anchor,h.random())==null,"NPC Splinter roots never acquire players");
        p[2].setLocation(Point.location(180,427),false);
        require(ChainLightningTraversalPolicy.selectCombatant(p[0],p[1],h.random())==null,"chain excludes safe and friendly candidates");
    }
    private static void projectileChildren(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"pvp child");
        h.equip(p[0],ItemId.BLOOD_WOOL_WIZARD_HAT.id(),1);
        h.equip(p[0],ItemId.BLOOD_WOOL_ROBE_TOP.id(),1);
        h.equip(p[0],ItemId.BLOOD_WOOL_ROBE_SKIRT.id(),1);
        ProjectileEvent event=new ProjectileEvent(h.world(),p[0],p[1],0,1,false);
        field(event,"bloodSpell",true);
        call(event,"applyBloodRobeSplash",new Class<?>[]{Player.class,int.class},p[0],100);
        require(p[2].getLevel(Skill.HITS.id())<250,"Blood robe splash damages a player child");
        int hp=p[2].getLevel(Skill.HITS.id());
        field(event,"secondaryEffectDamage",8);
        call(event,"applySplinter",new Class<?>[0]);
        require(p[2].getLevel(Skill.HITS.id())==hp-4,"Splinter uses the existing half-damage payload");
        require(p[3].getLevel(Skill.HITS.id())==250 && p[4].getLevel(Skill.HITS.id())==250,"neither effect damages party/clan allies");
        final List<Player> selected=new ArrayList<>();
        HellsInfernoNpcSplash.applyPlayers(p[0],p[1],9,(target,damage)->{require(damage==5,"Inferno preserves upward half rounding");selected.add(target);});
        require(selected.size()==1 && selected.get(0)==p[2],"Inferno dispatch uses the same legal player recipients");
    }
    private static DamageResult melee(CurrentCombatHarness h,Mob a,Mob b,int damage,AttackRoll roll)throws Exception {
        return (DamageResult)call(new PvmMeleeEvent(h.world(),a,b),"inflictDamage",
            new Class<?>[]{Mob.class,Mob.class,int.class,boolean.class,boolean.class,int.class,AttackRoll.class},
            a,b,damage,false,false,-1,roll);
    }
    private static void terminal(CurrentCombatHarness h,PvpSqliteLog logs)throws Exception {
        Player[] p=areaPlayers(h,"pvp fatal");
        p[1].getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),5,250,false);
        h.equip(p[0],ItemId.DEATH_WOOL_WIZARD_HAT.id(),1);
        h.equip(p[0],ItemId.DEATH_WOOL_ROBE_TOP.id(),1);
        h.equip(p[0],ItemId.DEATH_WOOL_ROBE_SKIRT.id(),1);
        h.equip(p[0],3090,1);
        int before=logs.count(),kills=p[0].getKills(),deaths=p[1].getDeaths();
        int meleeXp=p[0].getSkills().getExperience(Skill.MELEE.id()),hitsXp=p[0].getSkills().getExperience(Skill.HITS.id()),deathLogs=logs.deathCount();
        DamageResult result=melee(h,p[0],p[1],105,AttackRoll.normal(105));
        require(result.isTargetTerminal() && p[1].killed,"real primary damage completes player death");
        require(p[0].getKills()==kills+1 && p[1].getDeaths()==deaths+1 && logs.count()==before+1,"one kill, death and persisted PK log");
        require(p[1].getDeathLifecycleSnapshot().getContext().getKiller()==p[0],"death records the real player source");
        require(p[0].getSkills().getExperience(Skill.MELEE.id())>meleeXp && p[0].getSkills().getExperience(Skill.HITS.id())>hitsXp,"melee player kills retain melee and Hits XP");
        require(logs.deathCount()==deathLogs+1,"real death record reaches SQLite too");
        meleeXp=p[0].getSkills().getExperience(Skill.MELEE.id());hitsXp=p[0].getSkills().getExperience(Skill.HITS.id());
        require(p[2].getLevel(Skill.HITS.id())<250 && p[3].getLevel(Skill.HITS.id())==250 && p[4].getLevel(Skill.HITS.id())==250,
            "terminal overkill splash still hits the enemy and excludes allies");
        require(CombatEffectUtil.findPlayerOwnedPvpSplashTargets(p[0],p[1],2).contains(p[2]),"same-tick effects retain the death location after respawn movement");
        Item ring=p[0].getCarriedItems().getEquipment().getEquippedRingItem();
        int charge=EnchantingItemEffects.getDeathRingChargePoints(p[0],ring);
        require(charge==EnchantingItemEffects.getDeathRingChargePerNpcKill(),"player kills give the same Death Ring charge");
        int ground=p[0].getViewArea().getItemsInView().size();
        p[1].killedBy(p[0]);
        require(p[0].getKills()==kills+1 && logs.count()==before+1 && p[0].getViewArea().getItemsInView().size()==ground,
            "duplicate terminal callbacks cannot repeat rewards, drops or logs");
        require(EnchantingItemEffects.getDeathRingChargePoints(p[0],ring)==charge,"duplicate terminal callback cannot repeat charge");
        require(p[0].getSkills().getExperience(Skill.MELEE.id())==meleeXp && p[0].getSkills().getExperience(Skill.HITS.id())==hitsXp,"duplicate terminal callback cannot repeat kill XP");
    }
    private static void deathBurst(CurrentCombatHarness h,PvpSqliteLog logs)throws Exception {
        Player[] p=areaPlayers(h,"pvp burst");h.equip(p[0],1728,1);
        Item amulet=p[0].getCarriedItems().getEquipment().getEquippedWristItem();
        int required=EnchantingItemEffects.getDeathAmuletBurstChargeRequiredPoints();
        int gain=EnchantingItemEffects.getDeathAmuletBurstChargePointsForNpc(p[1].getCombatLevel());
        EnchantingItemEffects.setDeathAmuletBurstChargePoints(p[0],amulet,Math.max(0,required-gain));
        p[1].getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1,250,false);
        int before=logs.count();melee(h,p[0],p[1],2,AttackRoll.normal(2));
        require(logs.count()==before+1 && p[2].getLevel(Skill.HITS.id())<250,"a real player kill can release the charged Death Amulet burst");
        require(p[3].getLevel(Skill.HITS.id())==250 && p[4].getLevel(Skill.HITS.id())==250,"Death Amulet excludes party and clan");
    }
    private static Npc summon(CurrentCombatHarness h,Player owner) {
        Npc summon=h.npc(3,182,200);
        summon.setAttribute("myworld_summon_owner",owner.getUsernameHash());
        summon.setAttribute("myworld_summon_kind","bear");
        summon.relatedMob=owner;
        return summon;
    }
    private static void summonAssist(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"pvp sum");Npc summon=summon(h,p[0]);
        require(Summoning.canSummonAttack(summon,p[1]),"summon may hit its owner's legal enemy");
        require(!Summoning.canSummonAttack(summon,p[3])&&!Summoning.canSummonAttack(summon,p[4]),"summon excludes party and clan");
        p[0].startCombat(p[1]);melee(h,p[0],p[1],4,AttackRoll.normal(4));
        require(staticCall("resolveSummonAssistTarget",new Class<?>[]{Player.class,Npc.class},p[0],summon)==p[1],"owner damage and engagement select the same player for assist");
        p[1].getCache().store("setting_auto_retaliate",true);
        staticCall("assistOwnerTarget",new Class<?>[]{Player.class,Npc.class},p[0],summon);
        require(summon.getPvmMeleeEvent()!=null && summon.getPvmMeleeEvent().getTarget()==p[1],"summon starts its independent melee event against a player");
        melee(h,summon,p[1],4,AttackRoll.normal(4));
        require(p[1].getOutgoingCombatTarget()==p[0],"the victim retaliates toward the player owner, not the protected summon");
    }
    private static Object staticCall(String method,Class<?>[] types,Object...args)throws Exception {
        Method m=Summoning.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);
    }
    private static void summonShots(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"pvp sshot");Npc summon=summon(h,p[0]);
        ProjectileLaunchSpecification shot=ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.SUMMON_MAGIC,6,1).build();
        new ProjectileEvent(h.world(),summon,p[1],shot).action();
        require(p[1].getLevel(Skill.HITS.id())==244,"summon projectile uses normal PvE damage against a player");
        ProjectileEvent pending=new ProjectileEvent(h.world(),summon,p[1],shot);
        p[1].setParty(p[0].getParty());pending.action();
        require(p[1].getLevel(Skill.HITS.id())==244,"summon projectile rechecks a new party relationship");
        p[1].setParty(null);pending=new ProjectileEvent(h.world(),summon,p[1],shot);
        p[0].setLocation(Point.location(180,427),false);pending.action();
        require(p[1].getLevel(Skill.HITS.id())==244,"summon projectile cannot bypass its owner's Wilderness boundary");
    }
    private static void summonKill(CurrentCombatHarness h,PvpSqliteLog logs)throws Exception {
        Player[] p=areaPlayers(h,"pvp skill");Npc summon=summon(h,p[0]);
        p[1].getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),2,250,false);
        int before=logs.count(),kills=p[0].getKills();
        melee(h,summon,p[1],5,AttackRoll.normal(5));
        require(p[1].killed && p[1].getDeathLifecycleSnapshot().getContext().getKiller()==p[0],"summon death adapter credits the owner");
        require(p[0].getKills()==kills+1 && logs.count()==before+1,"summon kill updates owner and SQLite exactly once");
    }
    private static void fundCast(Player caster,ClericSpellId id)throws Exception {
        caster.getClientLimitations().maxItemId=65535;
        caster.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),163,163,false);
        ClericSpellDefinition spell=ClericSpellCatalog.get(id);
        for(ClericResourceCost cost:spell.getResourceCosts())require(caster.getCarriedItems().getInventory().add(new Item(cost.getItemId(),cost.getCount())),"fixture funds sigils");
        require(ClericSupportCasting.cast(caster,spell).getOutcome()==ClericSupportCasting.Outcome.SUCCESS,"actual hostile cast succeeds: "+id);
    }
    @SuppressWarnings("unchecked")
    private static Boolean phasePacket(Player viewer,Player target)throws Exception {
        int opcode=ActionSender.getGenerator(viewer).fromOpcodeEnum(OpcodeOut.SEND_UPDATE_PLAYERS,viewer).getOpcode();
        Boolean result=null;
        for(Packet packet:(List<Packet>)CurrentCombatHarness.readPrivateField(viewer,"outgoingPackets")) {
            if(packet.getID()!=opcode)continue;
            ByteBuf data=packet.getBuffer().duplicate();int count=data.readUnsignedShort();
            for(int i=0;i<count;i++) {
                int index=data.readUnsignedShort(),type=data.readUnsignedByte();
                require(type==ClericBlessingContract.PLAYER_PHASE_UPDATE,"controlled phase packet contains only phase records, saw "+type);
                int flag=data.readUnsignedByte();if(index==target.getIndex())result=flag==1;
            }
        }
        return result;
    }
    private static void phaseWire(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"pvp phase");fundCast(p[0],ClericSpellId.SARADOMIN_UNMAKING);
        require(ClericHostileRuntime.reduceMagicDefense(p[1],100)==75,"P6 Magical Allergy retains 25% reduction");
        require(ClericDirectCombatRuntime.maximum(p[1],p[0],20)==17,"P6 Atrophy retains 15% maximum reduction");
        require(ClericHostileRuntime.effect(p[3],ClericEffectFamily.SARADOMIN_HOSTILE)==null && ClericHostileRuntime.effect(p[4],ClericEffectFamily.SARADOMIN_HOSTILE)==null,"hostile casts exclude both ally types");
        h.random().scriptDoubles(0.0,0.0,0.0,0.0);
        for(int i=0;i<3&&!NonCorporealState.isActive(p[1]);i++)h.advanceOneCombatTick();
        require(NonCorporealState.isActive(p[1]),"actual hostile tick enters player non-corporeal state");
        p[0].getLocalPlayers().clear();p[0].getLocalPlayers().add(p[1]);
        p[0].requiresAppearanceUpdateFor(p[1]);p[0].removeAttribute("native_terrain_self_appearance_pending");
        for(Player actor:h.world().getPlayers())actor.getUpdateFlags().reset();h.recordOutgoingPackets(p[0]);
        new GameStateUpdater(h.server()).updatePlayerAppearances(p[0]);
        require(Boolean.TRUE.equals(phasePacket(p[0],p[1])),"wire transmits translucent phase for another player");
        DamageResult blocked=h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(p[0],p[1],DamageRequest.SourceCategory.ACTOR,"pvm-melee-primary",5).style(CombatStyle.MELEE).build());
        require(blocked.isNonCorporealBlocked(),"nonmagical PvP damage is blocked while phased");
        require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(p[0],p[1],DamageRequest.SourceCategory.ACTOR,"projectile-player-magic-primary",5).style(CombatStyle.MAGIC).build()).getActualDamage()==5,"Magic retains its exception in PvP");
        for(int i=0;i<4;i++)h.advanceOneCombatTick();
        for(Player actor:h.world().getPlayers())actor.getUpdateFlags().reset();new GameStateUpdater(h.server()).updatePlayerAppearances(p[0]);
        require(Boolean.FALSE.equals(phasePacket(p[0],p[1])),"wire restores solid rendering after phase duration");
        p[1].setLocation(Point.location(181,427),false);
        require(ClericHostileRuntime.effect(p[1],ClericEffectFamily.SARADOMIN_HOSTILE)==null,"leaving Wilderness invalidates the curse");
    }
    private static void guthix(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"pvp dread");fundCast(p[0],ClericSpellId.GUTHIX_TERROR);
        require(ClericHostileRuntime.poisonCleanse(p[1],3)==1,"P6 poison weakness uses the same rounded cleansing reduction");
        DamageResult poison=h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(p[0],p[1],DamageRequest.SourceCategory.DOT,"generic-poison",10).build());
        require(poison.getActualDamage()==14,"P6 poison damage retains 35% with upward rounding");
        Npc summon=summon(h,p[0]);
        DamageResult sum=h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(summon,p[1],DamageRequest.SourceCategory.ACTOR,"pvm-melee-primary",10).style(CombatStyle.MELEE).build());
        require(sum.getActualDamage()==13,"P6 summon weakness retains existing fractional carry");
    }
    private static void crit(CurrentCombatHarness h,PvpSqliteLog logs)throws Exception {
        Player[] p=areaPlayers(h,"pvp malice");fundCast(p[0],ClericSpellId.ZAMORAK_RUIN);
        h.random().scriptDoubles(0.0,0.0);
        AttackRoll roll=ClericCriticalRuntime.primaryRoll(p[0],p[1],10,true,CombatStyle.MELEE,()->1);
        require(roll.getDamage()==10 && roll.getCritical()==AttackRoll.Critical.AUTHORED,"curse converts a player primary roll to maximum damage");
        melee(h,p[0],p[1],10,roll);
        require(AttackState.pendingStaggers(p[1])==1,"critical hit can stagger a player");
        require(AttackState.commitAttack(p[1]).isSuppressed() && AttackState.recoveryAttacks(p[1])==5,"stagger negates one attack and starts five-attack recovery");
        for(int i=0;i<5;i++)require(!AttackState.commitAttack(p[1]).isSuppressed(),"recovery attacks remain uninterrupted");
        require(AttackState.recoveryAttacks(p[1])==0,"all five attempts consume recovery");
        p[1].getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),20,100,false);
        h.random().scriptDoubles(0.0);int before=logs.count();
        melee(h,p[0],p[1],10,AttackRoll.critical(10,AttackRoll.Critical.AUTHORED));
        require(p[1].killed && logs.count()==before+1,"execution retains the primary player-death adapter and one log");
    }
    private static void retreat(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"pvp timer");p[1].setRanAwayTimer();
        require(PlayerPvpCombat.eligible(p[0],p[1],CombatStyle.MELEE) && PlayerPvpCombat.eligible(p[0],p[1],CombatStyle.MAGIC),"an old PvM retreat timer cannot grant immunity from PvP");
    }
}
