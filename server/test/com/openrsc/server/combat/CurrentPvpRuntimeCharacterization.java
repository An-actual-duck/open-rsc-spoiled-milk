package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.content.Slow;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.cleric.ClericResourceCost;
import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.runtime.ClericHostileRuntime;
import com.openrsc.server.content.cleric.runtime.ClericSupportCasting;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.AttackRoll;
import com.openrsc.server.model.combat.PlayerPvpCombat;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.AttackHandler;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.openrsc.server.combat.CurrentPvpFoundationCharacterization.areaPlayers;
import static com.openrsc.server.combat.CurrentPvpFoundationCharacterization.checkCase;
import static com.openrsc.server.combat.CurrentPvpFoundationCharacterization.player;
import static com.openrsc.server.combat.CurrentPvpFoundationCharacterization.require;

/** Real packet requests, volleys, resource receipts and player effect lifetimes. */
final class CurrentPvpRuntimeCharacterization {
    static void register(CurrentCombatHarness h,PvpSqliteLog logs,List<String> failures)throws Exception {
        checkCase(h,"pvp_attack_packet_ignores_retreat_and_switches_styles",CurrentPvpRuntimeCharacterization::packetStyles,failures);
        checkCase(h,"pvp_retaliation_uses_selected_combat_styles",CurrentPvpRuntimeCharacterization::retaliationStyles,failures);
        checkCase(h,"pvp_shuriken_volley_conserves_ammo_and_rechecks_allies",CurrentPvpRuntimeCharacterization::volley,failures);
        checkCase(h,"pvp_support_summon_absorbs_player_damage",CurrentPvpRuntimeCharacterization::absorption,failures);
        checkCase(h,"pvp_projectile_kill_style_and_logs_once",x->projectileDeath(x,logs),failures);
        checkCase(h,"pvp_curse_tier_precedence_and_logout",CurrentPvpRuntimeCharacterization::curseLifetime,failures);
        checkCase(h,"pvp_retarg_cannot_bypass_melee_cadence",CurrentPvpRuntimeCharacterization::cadence,failures);
        checkCase(h,"pvp_coated_battleaxe_applies_shared_slow",CurrentPvpRuntimeCharacterization::coating,failures);
        checkCase(h,"pvp_thunder_children_preserve_kill_style",x->thunder(x,logs),failures);
        checkCase(h,"pvp_friend_exclusions_are_source_owned",CurrentPvpRuntimeCharacterization::friends,failures);
    }
    private static void request(Player source,Player target)throws Exception {
        TargetMobStruct payload=new TargetMobStruct();payload.setOpcode(OpcodeIn.PLAYER_ATTACK);payload.serverIndex=target.getIndex();
        new AttackHandler().process(payload,source);
    }
    private static void fundMagic(CurrentCombatHarness h,Player p) {
        p.getClientLimitations().maxItemId=65535;
        for(Map.Entry<Integer,Integer> rune:h.server().getEntityHandler().getSpellDef(Spells.WIND_STRIKE).getRunesRequired())
            require(p.getCarriedItems().getInventory().add(new Item(rune.getKey(),50)),"fund magic fixture");
        p.setAutoCastSpell(Spells.WIND_STRIKE);
    }
    private static void packetStyles(CurrentCombatHarness h)throws Exception {
        Player a=player(h,"packet a",180,200,false),b=player(h,"packet b",181,200,false),c=player(h,"packet c",181,201,false);
        b.setRanAwayTimer();request(a,b);
        require(a.getWalkToAction()!=null && a.getAttackTransaction().getPending().getTarget()==b,"manual packet can approach a player carrying a legacy retreat timer");
        require(a.commitPendingMeleeAttack(b),"default plugin's production commit starts melee");
        require(a.getPvmMeleeEvent()!=null,"melee committed");
        h.equip(a,ItemId.SHORTBOW.id(),1);request(a,c);
        require(a.getWalkToAction()!=null,"player melee can switch to ranged via packet");a.getWalkToAction().execute();
        require(a.getRangeEvent()!=null && a.getRangeEvent().getTarget()==c && a.getPvmMeleeEvent()==null,"ranged replaces only outgoing melee");
        fundMagic(h,a);request(a,b);
        require(a.getMagicCombatEvent()!=null && a.getMagicCombatEvent().getTarget()==b && a.getRangeEvent()==null,"autocast replaces outgoing ranged through packet");
    }
    private static void retaliationStyles(CurrentCombatHarness h)throws Exception {
        Player a=player(h,"ret style a",180,200,false),b=player(h,"ret style b",181,200,true),c=player(h,"ret style c",181,201,false);
        h.equip(b,ItemId.SHORTBOW.id(),1);PlayerPvpCombat.retaliate(b,a);
        require(b.getRangeEvent()!=null && b.getRangeEvent().getTarget()==a,"bow retaliates with ranged");
        PlayerPvpCombat.retaliate(b,c);require(b.getRangeEvent().getTarget()==a,"ranged target is not stolen");b.resetAll();b.resetRange();
        fundMagic(h,b);PlayerPvpCombat.retaliate(b,c);
        require(b.getMagicCombatEvent()!=null && b.getMagicCombatEvent().getTarget()==c,"autocast retaliates with magic");
    }
    private static void volley(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"volley");Player extra=player(h,"volley x",182,201,false);
        h.equip(p[0],ItemId.TIN_SHURIKEN.id(),5);ThrowingEvent throwing=new ThrowingEvent(h.world(),p[0],1,p[1]);p[0].setThrowingEvent(throwing);
        throwing.run();List<ProjectileEvent> shots=new ArrayList<>();
        for(GameTickEvent event:h.server().getGameEventHandler().getEvents())if(event instanceof ProjectileEvent && event.getOwner()==p[0])shots.add((ProjectileEvent)event);
        require(shots.size()==3,"real volley launches three distinct projectiles");
        require(p[0].getCarriedItems().getEquipment().get(p[0].getCarriedItems().getEquipment().searchEquipmentForItem(ItemId.TIN_SHURIKEN.id())).getAmount()==2,"one volley spends exactly three shurikens");
        Set<UUID> recipients=new HashSet<>();int automatic=0;
        p[2].setClan(p[0].getClan());extra.setLocation(Point.location(182,427),false);
        for(ProjectileEvent shot:shots) {
            recipients.add(((Mob)CurrentCombatHarness.readPrivateField(shot,"opponent")).getUUID());
            require(shot.getProjectileResourceLedger().getItemCosts().get(0).getRemovedAmount()==1,"each sibling owns one ammo receipt");
            if(shot.getLaunchSnapshot().getSpecification().isAutomaticTarget())automatic++;
            shot.action();shot.action();
        }
        require(recipients.size()==3 && automatic==2,"only the two collateral throws carry automatic targeting provenance");
        require(p[2].getLevel(Skill.HITS.id())==250 && extra.getLevel(Skill.HITS.id())==250,"new clan ally and safe retreat cannot receive pending throws");
    }
    private static void absorption(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"absorb");Npc summon=h.npc(3,180,201);
        summon.setAttribute("myworld_summon_owner",p[1].getUsernameHash());summon.setAttribute("myworld_summon_kind","unicorn");
        summon.setAttribute("myworld_summon_damage_absorbing",true);summon.setAttribute("myworld_summon_absorb_percent",30);
        summon.setAttribute("myworld_summon_current_hits",50);summon.setAttribute("myworld_summon_max_hits",50);p[1].setAttribute("myworld_manual_summon",summon);
        require(Summoning.applySummonDamageAbsorption(p[1],p[0],10)==7,"support summon absorbs the same thirty percent from a player");
        require(Summoning.getSummonCurrentHits(summon)==47,"absorbed damage is paid from summon health");
    }
    private static void projectileDeath(CurrentCombatHarness h,PvpSqliteLog logs)throws Exception {
        Player a=player(h,"kill shot a",180,200,false),b=player(h,"kill shot b",181,200,false);
        b.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),2,250,false);a.setKillType(KillType.COMBAT);
        int before=logs.count(),deaths=logs.deathCount(),xp=a.getSkills().getExperience(Skill.MELEE.id());
        ProjectileEvent shot=new ProjectileEvent(h.world(),a,b,ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_MAGIC,5,1).build());
        shot.action();shot.action();
        require(b.killed && a.getKillType()==KillType.MAGIC,"magic impact records its actual kill style");
        require(logs.count()==before+1 && logs.deathCount()==deaths+1,"duplicate impact persists one feed and one death log");
        require(a.getSkills().getExperience(Skill.MELEE.id())==xp,"magic kill cannot award stale melee kill XP");
    }
    private static ClericSupportCasting.Outcome cast(Player caster,ClericSpellId id) {
        caster.getClientLimitations().maxItemId=65535;caster.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),163,163,false);
        ClericSpellDefinition spell=ClericSpellCatalog.get(id);
        for(ClericResourceCost cost:spell.getResourceCosts())require(caster.getCarriedItems().getInventory().add(new Item(cost.getItemId(),cost.getCount())),"fund curse");
        return ClericSupportCasting.cast(caster,spell).getOutcome();
    }
    private static void curseLifetime(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"lifetime");
        require(cast(p[0],ClericSpellId.SARADOMIN_UNMAKING)==ClericSupportCasting.Outcome.SUCCESS,"tier three applies");
        require(cast(p[0],ClericSpellId.SARADOMIN_CENSURE)==ClericSupportCasting.Outcome.NO_USEFUL_RECIPIENT,"lower tier cannot replace a stronger curse");
        require(ClericHostileRuntime.reduceMagicDefense(p[1],100)==75,"tier three magnitude persists");
        h.logout(p[0]);require(ClericHostileRuntime.effect(p[1],ClericEffectFamily.SARADOMIN_HOSTILE)==null,"caster logout invalidates player curse");
    }
    private static void cadence(CurrentCombatHarness h)throws Exception {
        Player a=player(h,"cadence a",180,200,false),b=player(h,"cadence b",181,200,false),c=player(h,"cadence c",180,201,false);
        a.startCombat(b);h.advanceOneCombatTick();
        Object remaining=a.getAttribute("combat_slow_deadline_MELEE",null);
        require(remaining!=null,"ordinary melee commitments retain a cadence deadline");
        a.startCombat(c);h.advanceOneCombatTick();
        require(a.getAttribute("combat_slow_deadline_MELEE",null)==remaining,"manual target switch cannot advance the attack deadline");
    }
    private static void coating(CurrentCombatHarness h)throws Exception {
        Player[] p=areaPlayers(h,"coating");h.equip(p[0],3411,1);
        com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent event=new com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent(h.world(),p[0],p[1]);
        for(int i=0;i<3;i++)CurrentCombatHarness.invokePrivate(event,"inflictDamage",
            new Class<?>[]{Mob.class,Mob.class,int.class,boolean.class,boolean.class,int.class,AttackRoll.class},p[0],p[1],2,false,false,3411,AttackRoll.normal(2));
        require(Slow.power(p[1])==12 && Slow.tier(p[1])==1,"coating builds the same 4 Slow power per positive primary hit to cap 12");
        p[1].setLocation(Point.location(181,427),false);int before=Slow.power(p[1]);
        com.openrsc.server.content.coatings.BattleaxeCoatings.primaryHit(p[0],p[1],3411,2);
        require(Slow.power(p[1])==before,"coating cannot apply another hit effect across the safe boundary");
    }
    private static void thunder(CurrentCombatHarness h,PvpSqliteLog logs)throws Exception {
        Player[] p=areaPlayers(h,"thunder");p[2].getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1,250,false);
        int before=logs.count();p[0].setKillType(KillType.COMBAT);
        for(int i=0;i<30&&!p[2].killed;i++)com.openrsc.server.content.monsterslayer.SlayerRewardCombat.thunderSplash(p[0],p[1],3,100);
        require(p[2].killed && logs.count()==before+1 && p[0].getKillType()==KillType.MAGIC,"real Thunder secondary kill has one Magic PK record");
        require(p[3].getLevel(Skill.HITS.id())==250 && p[4].getLevel(Skill.HITS.id())==250,"Thunder excludes party and clan allies");
    }

    private static void friends(CurrentCombatHarness h)throws Exception {
        Player a=player(h,"friends a",180,200,false),b=player(h,"friends b",181,200,false),primary=player(h,"friends c",180,201,false);
        a.getSocial().addFriend(b.getUsernameHash(),1,b.getUsername(),"");
        require(CombatEffectUtil.arePvpAllies(a,b) && !CombatEffectUtil.arePvpAllies(b,a),"friendship excludes outgoing effects only");
        require(!CombatEffectUtil.isPlayerOwnedPvpSplashTarget(a,primary,b),"own friend is excluded from splash");
        require(CombatEffectUtil.isPlayerOwnedPvpSplashTarget(b,primary,a),"being added cannot grant immunity from the other player's splash");
        require(PlayerPvpCombat.secondaryDamage(a,primary,b,5,CombatStyle.MELEE,"pvm-melee-scythe-cleave",UUID.randomUUID(),AttackRoll.normal(5))==0,"own friend receives no area damage");
        require(PlayerPvpCombat.secondaryDamage(b,primary,a,5,CombatStyle.MELEE,"pvm-melee-scythe-cleave",UUID.randomUUID(),AttackRoll.normal(5))==5,"reverse splash still settles normal damage");
        require(cast(a,ClericSpellId.GUTHIX_UNEASE)==ClericSupportCasting.Outcome.SUCCESS,"hostile cast still has an unrelated enemy recipient");
        require(ClericHostileRuntime.effect(b,ClericEffectFamily.GUTHIX_HOSTILE)==null,"own friend is excluded from hostile Cleric abilities");
        require(cast(b,ClericSpellId.GUTHIX_UNEASE)==ClericSupportCasting.Outcome.SUCCESS
                && ClericHostileRuntime.effect(a,ClericEffectFamily.GUTHIX_HOSTILE)!=null,"reverse hostile Cleric ability is not blocked by its victim's friend list");
        a.getSocial().removeFriend(b.getUsernameHash());
        ProjectileLaunchSpecification automatic=ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_SHURIKEN,7,2).automaticTarget(true).build();
        ProjectileEvent outward=new ProjectileEvent(h.world(),a,b,automatic),inward=new ProjectileEvent(h.world(),b,a,automatic);
        a.getSocial().addFriend(b.getUsernameHash(),1,b.getUsername(),"");
        int aHp=a.getLevel(Skill.HITS.id()),bHp=b.getLevel(Skill.HITS.id());
        outward.action();inward.action();
        require(b.getLevel(Skill.HITS.id())==bHp && a.getLevel(Skill.HITS.id())==aHp-7,"pending automatic throws recheck the attacker's list without reverse immunity");
        require(PlayerPvpCombat.eligible(a,b,CombatStyle.MELEE),"explicit primary attacks remain available through the secondary menu");
        a.getSocial().removeFriend(b.getUsernameHash());
        require(!CombatEffectUtil.arePvpAllies(a,b),"removing a friend restores automatic targeting eligibility");
    }

}
