package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.content.party.PartyRank;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.content.cleric.runtime.*;
import com.openrsc.server.model.container.Item;
import java.util.UUID;
import com.openrsc.server.content.Slow;
import com.openrsc.server.content.clan.Clan;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.content.monsterslayer.SlayerRewardCombat;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.CombatEligibility;
import com.openrsc.server.model.combat.CombatEligibilityPhase;
import com.openrsc.server.model.combat.CombatEligibilityRequest;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.dot.PeriodicEffectProvenance;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.WalkRequest;
import com.openrsc.server.net.rsc.struct.incoming.WalkStruct;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import java.util.ArrayList;
import java.util.List;

/** Real registered players and production combat events; no sockets or account database. */
public final class CurrentPvpFoundationCharacterization {
    private CurrentPvpFoundationCharacterization() { }
    private static int executed;

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<String>();
        try (CurrentCombatHarness h = new CurrentCombatHarness(); PvpSqliteLog logs=new PvpSqliteLog(h.server())) {
            h.server().getConfig().WANT_PVP = true;
            h.server().getConfig().SHUFFLE_PID_ORDER = false;
            h.server().getConfig().USES_PK_MODE = false;
            for (int i=0; i<20; i++) h.advanceOneCombatTick();
            checkCase(h,"directional_start_and_passive_victim", CurrentPvpFoundationCharacterization::directionalStart, failures);
            checkCase(h,"retaliation_keeps_selected_target", CurrentPvpFoundationCharacterization::retaliation, failures);
            checkCase(h,"walk_keeps_pursuer_and_grants_no_immunity", CurrentPvpFoundationCharacterization::walking, failures);
            checkCase(h,"slow_changes_only_its_owners_attack_interval", CurrentPvpFoundationCharacterization::independentCadence, failures);
            checkCase(h,"splash_excludes_party_and_clan_independently", CurrentPvpFoundationCharacterization::splashAllies, failures);
            checkCase(h,"safe_boundary_stops_ongoing_melee", CurrentPvpFoundationCharacterization::safeBoundary, failures);
            checkCase(h,"npc_primary_never_splashes_players", CurrentPvpFoundationCharacterization::npcPrimary, failures);
            checkCase(h,"projectile_checks_safety_at_impact", CurrentPvpFoundationCharacterization::projectileSafety, failures);
            checkCase(h,"player_projectiles_trigger_retaliation", CurrentPvpFoundationCharacterization::projectileRetaliation, failures);
            checkCase(h,"scythe_cleave_hits_only_enemy_players", CurrentPvpFoundationCharacterization::scytheArea, failures);
            checkCase(h,"iban_splash_hits_only_enemy_players", CurrentPvpFoundationCharacterization::ibanArea, failures);
            checkCase(h,"shuriken_keeps_three_legal_player_targets", CurrentPvpFoundationCharacterization::shurikenArea, failures);
            checkCase(h,"cleric_party_blessing_keeps_pve_values_in_wilderness", CurrentPvpFoundationCharacterization::clericBlessings, failures);
            checkCase(h,"safe_zone_blocks_all_player_owned_damage", CurrentPvpFoundationCharacterization::safeSettlement, failures);
            checkCase(h,"legacy_pk_mode_cannot_enable_myworld_safe_zone_pvp", CurrentPvpFoundationCharacterization::pkModeSafety, failures);
            checkCase(h,"safe_zone_keeps_pve_damage_and_blocks_orphan_player_dot", CurrentPvpFoundationCharacterization::pveSafety, failures);
            checkCase(h,"player_poison_cleanses_without_damage_in_safety", CurrentPvpFoundationCharacterization::poisonBoundary, failures);
            checkCase(h,"shuriken_boundary_excludes_safe_players_and_stale_locks", CurrentPvpFoundationCharacterization::shurikenBoundary, failures);
            checkCase(h,"border_splash_and_shuriken_impacts_recheck_each_player", CurrentPvpFoundationCharacterization::borderImpacts, failures);
            CurrentPvpParityCharacterization.register(h,logs,failures);
            CurrentPvpRuntimeCharacterization.register(h,logs,failures);
        }
        if (!failures.isEmpty()) throw new AssertionError("PvP foundation failures: " + failures);
        System.out.println("PASS: "+executed+" PvP foundation scenarios");
    }

    interface Scenario { void run(CurrentCombatHarness h) throws Exception; }
    static void checkCase(CurrentCombatHarness h,String name, Scenario scenario, List<String> failures) throws Exception {
        executed++;
        try {
            scenario.run(h);
            System.out.println("PASS " + name);
        } catch (AssertionError failure) {
            failures.add(name + ": " + failure.getMessage());
            System.out.println("FAIL " + name + ": " + failure.getMessage());
        } finally {
            List<Player> players=new ArrayList<Player>();
            for(Player p:h.world().getPlayers()) players.add(p);
            for(Player p:players) { p.setParty(null);p.setClan(null);h.logout(p); }
            List<Npc> npcs=new ArrayList<Npc>();for(Npc n:h.world().getNpcs())npcs.add(n);
            for(Npc n:npcs)h.world().unregisterNpc(n);
        }
    }

    static Player player(CurrentCombatHarness h, String name, int x, int y, boolean retaliate) {
        h.openCombatProjectileRectangle(x-2,x+2,y-2,y+2);
        Player p=h.player(name,x,y);
        for(Player other:h.world().getPlayers())require(other==p || !other.getUUID().equals(p.getUUID()),"fixture names must remain unique after RSC truncation: "+name);
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(0,0,0,0,1,2));
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),250,250,false);
        p.getCache().store("setting_auto_retaliate",retaliate);
        require(p.getLocation().inWilderness(),"fixture must be in Wilderness");
        return p;
    }

    private static void directionalStart(CurrentCombatHarness h) {
        Player a=player(h,"pvp start a",180,200,false), b=player(h,"pvp start b",181,200,false);
        a.startCombat(b);
        require(a.getPvmMeleeEvent()!=null && a.getCombatEvent()==null,"attacker must own a directional melee event");
        require(b.getPvmMeleeEvent()==null && !b.hasOutgoingAttack(),"auto-retaliate off must not force an outgoing attack");
        require(a.getX()==180 && b.getX()==181,"starting melee must not stack the players on one tile");
        require(b.hasIncomingAttackFrom(a),"passive victim must retain the incoming attack");
    }

    private static void retaliation(CurrentCombatHarness h) {
        Player a=player(h,"pvp ret a",180,200,false), b=player(h,"pvp ret b",181,200,true), c=player(h,"pvp ret c",181,201,false);
        a.startCombat(b);
        require(b.getPvmMeleeEvent()!=null && b.getPvmMeleeEvent().getTarget()==a,"enabled victim retaliates through its own event");
        PvmMeleeEvent selected=b.getPvmMeleeEvent();
        c.startCombat(b);
        require(b.getPvmMeleeEvent()==selected && selected.getTarget()==a,"another attacker must not steal a living selected target");
        require(b.getIncomingCombatAttackerCount()==2,"two incoming players coexist");
        b.startCombat(c);
        require(b.getPvmMeleeEvent()!=selected && b.getPvmMeleeEvent().getTarget()==c,"manual retarget replaces only the owner's event");
        require(a.getPvmMeleeEvent()!=null && c.getPvmMeleeEvent()!=null,"manual retarget must not stop either incoming opponent");
    }

    private static void walking(CurrentCombatHarness h) throws Exception {
        Player a=player(h,"pvp walk a",180,200,false), b=player(h,"pvp walk b",181,200,false);
        a.startCombat(b); b.startCombat(a);
        PvmMeleeEvent pursuer=b.getPvmMeleeEvent();
        require(pursuer!=null,"pursuer has its own attack event");
        WalkStruct walk=new WalkStruct();walk.setOpcode(OpcodeIn.WALK_TO_POINT);walk.firstStep=Point.location(179,200);
        new WalkRequest().process(walk,a);
        require(a.getPvmMeleeEvent()==null,"walking stops the walker's outgoing attack");
        require(b.getPvmMeleeEvent()==pursuer && pursuer.isRunning(),"walking must leave the pursuer's attack running");
        require(a.canBeReattacked(),"walking must not grant retreat immunity");
    }

    private static void independentCadence(CurrentCombatHarness h) throws Exception {
        Player a=player(h,"pvp pace a",180,200,false), b=player(h,"pvp pace b",181,200,false);
        a.startCombat(b);b.startCombat(a);
        PvmMeleeEvent first=a.getPvmMeleeEvent(),second=b.getPvmMeleeEvent();
        require(first!=null && second!=null && first!=second,"two independent event owners are required");
        Slow.apply(b,Slow.CAP_TWO,Slow.CAP_TWO);
        first.run();second.run();
        // PvE Slow also postpones the first commitment; let the real scheduler
        // reach that boundary before comparing the following owned intervals.
        for(int i=0;i<3;i++)h.advanceOneCombatTick();
        require(second.getDelayTicks()>first.getDelayTicks(),"Slow must delay only the afflicted player's interval: "
            +first.getDelayTicks()+" vs "+second.getDelayTicks()+"; tiers="+Slow.tier(a)+","+Slow.tier(b)
            +"; positions="+a.getLocation()+","+b.getLocation()+"; running="+first.isRunning()+","+second.isRunning());
        require(first.isRunning() && second.isRunning(),"both players keep their own attacks");
    }

    private static void splashAllies(CurrentCombatHarness h) {
        Player a=player(h,"pvp splash a",180,200,false), primary=player(h,"pvp splash b",181,200,false);
        Player party=player(h,"pvp party",181,201,false), clan=player(h,"pvp clan",182,200,false), enemy=player(h,"pvp enemy",182,201,false);
        Party group=new Party(h.world());a.setParty(group);party.setParty(group);
        Clan guild=new Clan(h.world());guild.setClanID(17);a.setClan(guild);
        Clan sameIdentity=new Clan(h.world());sameIdentity.setClanID(17);clan.setClan(sameIdentity);
        List<Player> hits=CombatEffectUtil.findPlayerOwnedPvpSplashTargets(a,primary,2);
        require(hits.contains(enemy),"unrelated eligible enemy receives splash");
        require(!hits.contains(party),"party member is excluded independently of clan");
        require(!hits.contains(clan),"matching clan identity is excluded independently of party");
        require(!hits.contains(a) && !hits.contains(primary),"self and primary are not duplicate splash targets");
    }

    private static void safeBoundary(CurrentCombatHarness h) {
        Player a=player(h,"pvp safe a",180,200,false),b=player(h,"pvp safe b",181,200,false);
        a.startCombat(b);PvmMeleeEvent attack=a.getPvmMeleeEvent();
        require(attack!=null,"directional event required");
        b.setLocation(Point.location(181,650),false);attack.run();
        require(a.getPvmMeleeEvent()==null && !attack.isRunning(),"an ongoing melee attack stops when its target leaves Wilderness");
    }

    private static void npcPrimary(CurrentCombatHarness h) {
        Player a=player(h,"pvp area a",180,200,false),b=player(h,"pvp area b",182,200,false);
        Npc npc=h.npc(3,181,200);
        require(!CombatEffectUtil.isPlayerOwnedPvpSplashTarget(a,npc,b),"NPC-primary attacks never admit player collateral");
        require(!SlayerRewardCombat.thunderTarget(a,npc,b,2),"Thunder Spire splash must keep PvM separate");
        require(CombatEffectUtil.isPlayerOwnedPvpSplashTarget(a,b,player(h,"pvp area c",182,201,false)),"explicit PvP admits unrelated legal players");
    }

    private static void projectileSafety(CurrentCombatHarness h) {
        Player a=player(h,"pvp shot a",180,425,false),b=player(h,"pvp shot b",181,426,false);
        ProjectileEvent event=new ProjectileEvent(h.world(),a,b,
            ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_BOW,4,2).build());
        int hits=b.getLevel(Skill.HITS.id());
        h.server().getConfig().WANT_PVP=false;
        try {event.action();} finally {h.server().getConfig().WANT_PVP=true;}
        require(b.getLevel(Skill.HITS.id())==hits,"an in-flight attack must not bypass a disabled PvP rule");
        ProjectileEvent boundary=new ProjectileEvent(h.world(),a,b,
            ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_BOW,4,2).build());
        b.setLocation(Point.location(181,427),false);
        require(!b.getLocation().inWilderness(),"fixture crosses the actual Wilderness boundary by one tile");
        boundary.action();
        require(b.getLevel(Skill.HITS.id())==hits,"an in-flight attack must not hurt a player who stepped into safety");
    }

    private static void projectileRetaliation(CurrentCombatHarness h) {
        Player a=player(h,"pvp bow a",180,200,false),b=player(h,"pvp bow b",182,200,true);
        ProjectileEvent event=new ProjectileEvent(h.world(),a,b,
            ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_BOW,4,2).build());
        int hits=b.getLevel(Skill.HITS.id());event.action();
        require(b.getLevel(Skill.HITS.id())<hits,"legal player projectile settles damage");
        require(b.getOutgoingCombatTarget()==a,"player projectile starts the chosen retaliation path");
    }

    static Player[] areaPlayers(CurrentCombatHarness h,String name) {
        h.openCombatProjectileRectangle(176,186,196,205);
        Player a=player(h,name+" a",180,200,false),b=player(h,name+" b",181,200,false),
            enemy=player(h,name+" e",180,201,false),ally=player(h,name+" p",179,200,false),
            clan=player(h,name+" c",180,199,false);
        Party party=new Party(h.world());a.setParty(party);ally.setParty(party);
        Clan group=new Clan(h.world());group.setClanID(24);a.setClan(group);clan.setClan(group);
        return new Player[]{a,b,enemy,ally,clan};
    }

    private static void scytheArea(CurrentCombatHarness h) throws Exception {
        Player[] p=areaPlayers(h,"pvp scy");h.equip(p[0],ItemId.TIN_SCYTHE.id(),1);
        PvmMeleeEvent event=new PvmMeleeEvent(h.world(),p[0],p[1]);
        Method method=PvmMeleeEvent.class.getDeclaredMethod("applyScythePvpCleave",Player.class,Player.class);method.setAccessible(true);
        for(int i=0;i<12;i++)require(((Integer)method.invoke(event,p[0],p[1]))==1,"only one eligible secondary scythe recipient");
        require(p[2].getLevel(Skill.HITS.id())<250,"scythe's real secondary rolls damage the enemy");
        require(p[1].getLevel(Skill.HITS.id())==250 && p[3].getLevel(Skill.HITS.id())==250
            && p[4].getLevel(Skill.HITS.id())==250,"primary is not duplicated and allies take no scythe damage");
    }

    private static void ibanArea(CurrentCombatHarness h) throws Exception {
        Player[] p=areaPlayers(h,"pvp iban");
        Method method=SpellHandler.class.getDeclaredMethod("applyIbanBlastAreaEffects",Player.class,com.openrsc.server.model.entity.Mob.class,UUID.class);method.setAccessible(true);
        SpellHandler handler=new SpellHandler();
        for(int i=0;i<16;i++)method.invoke(handler,p[0],p[1],UUID.randomUUID());
        require(p[2].getLevel(Skill.HITS.id())<250,"Iban's real secondary rolls damage the enemy");
        require(p[1].getLevel(Skill.HITS.id())==250 && p[3].getLevel(Skill.HITS.id())==250
            && p[4].getLevel(Skill.HITS.id())==250,"primary is not duplicated and allies take no Iban damage");
    }

    @SuppressWarnings("unchecked")
    private static void shurikenArea(CurrentCombatHarness h) throws Exception {
        Player[] p=areaPlayers(h,"pvp shur");Player extra=player(h,"pvp shur x",182,201,false);
        ThrowingEvent event=new ThrowingEvent(h.world(),p[0],1,p[1]);
        Method method=ThrowingEvent.class.getDeclaredMethod("selectThrowingTargets",Player.class,int.class,int.class);method.setAccessible(true);
        List<com.openrsc.server.model.entity.Mob> targets=(List<com.openrsc.server.model.entity.Mob>)method.invoke(event,p[0],ItemId.TIN_SHURIKEN.id(),6);
        require(targets.size()==3 && targets.contains(p[1]) && targets.contains(p[2]) && targets.contains(extra),"three distinct eligible player targets use the normal shuriken cap");
        require(!targets.contains(p[3])&&!targets.contains(p[4]),"shuriken selection excludes party and clan");
    }

    @SuppressWarnings("unchecked")
    private static void shurikenBoundary(CurrentCombatHarness h) throws Exception {
        h.openCombatProjectileRectangle(176,186,422,431);
        Player a=player(h,"pvp border a",180,426,false),primary=player(h,"pvp border p",181,426,false),
            secondary=player(h,"pvp border e",182,425,false);
        Player safe=h.player("pvp border s",182,427);
        safe.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),250,250,false);
        ThrowingEvent event=new ThrowingEvent(h.world(),a,1,primary);
        Method select=ThrowingEvent.class.getDeclaredMethod("selectThrowingTargets",Player.class,int.class,int.class);
        select.setAccessible(true);
        Method remember=ThrowingEvent.class.getDeclaredMethod("rememberShurikenTargets",Player.class,List.class,int.class);
        remember.setAccessible(true);
        List<com.openrsc.server.model.entity.Mob> selected=(List<com.openrsc.server.model.entity.Mob>)
            select.invoke(event,a,ItemId.TIN_SHURIKEN.id(),6);
        require(selected.size()==2 && selected.contains(primary) && selected.contains(secondary) && !selected.contains(safe),
            "an unfilled third shuriken slot must never pick the nearby safe-zone player");
        remember.invoke(event,a,selected,6);
        secondary.setLocation(Point.location(182,427),false);
        selected=(List<com.openrsc.server.model.entity.Mob>)select.invoke(event,a,ItemId.TIN_SHURIKEN.id(),6);
        require(selected.size()==1 && selected.contains(primary),"a previously locked secondary is excluded after crossing into safety");
        List<com.openrsc.server.model.entity.Mob> locks=(List<com.openrsc.server.model.entity.Mob>)
            CurrentCombatHarness.readPrivateField(event,"shurikenTargetLock");
        require(!locks.contains(secondary),"the stale safe-zone lock is pruned, not merely skipped for one volley");
        primary.setLocation(Point.location(181,427),false);
        selected=(List<com.openrsc.server.model.entity.Mob>)select.invoke(event,a,ItemId.TIN_SHURIKEN.id(),6);
        require(selected.isEmpty(),"a primary crossing into safety cannot survive the selection fallback");
        primary.setLocation(Point.location(181,426),false);a.setLocation(Point.location(180,427),false);
        selected=(List<com.openrsc.server.model.entity.Mob>)select.invoke(event,a,ItemId.TIN_SHURIKEN.id(),6);
        require(selected.isEmpty(),"a source crossing into safety cannot retain a player target");
    }

    private static void borderImpacts(CurrentCombatHarness h) throws Exception {
        h.openCombatProjectileRectangle(176,186,422,431);
        Player a=player(h,"pvp splash edge",180,426,false),primary=player(h,"pvp splash main",181,426,false),
            secondary=player(h,"pvp splash inner",180,425,false);
        Player safe=h.player("pvp splash outer",180,427);
        safe.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),250,250,false);
        Method splash=SpellHandler.class.getDeclaredMethod("applyIbanBlastAreaEffects",Player.class,
            com.openrsc.server.model.entity.Mob.class,UUID.class);
        splash.setAccessible(true);
        for(int i=0;i<16;i++)splash.invoke(new SpellHandler(),a,primary,UUID.randomUUID());
        require(secondary.getLevel(Skill.HITS.id())<250,"real border splash still hits a legal inside player");
        require(safe.getLevel(Skill.HITS.id())==250,"real border splash cannot reach a player one tile outside Wilderness");
        ProjectileLaunchSpecification shot=ProjectileLaunchSpecification.builder(
            ProjectileLaunchSpecification.Producer.PLAYER_SHURIKEN,6,2).build();
        ProjectileEvent queued=new ProjectileEvent(h.world(),a,primary,shot);
        primary.setLocation(Point.location(181,427),false);queued.action();
        require(primary.getLevel(Skill.HITS.id())==250,"an already launched shuriken cannot hit a player who steps into safety");
        primary.setLocation(Point.location(181,426),false);
        ProjectileEvent sourceMoves=new ProjectileEvent(h.world(),a,primary,shot);
        a.setLocation(Point.location(180,427),false);sourceMoves.action();
        require(primary.getLevel(Skill.HITS.id())==250,"a queued shuriken is rejected when its source crosses into safety");
        a.setLocation(Point.location(180,426),false);
        new ProjectileEvent(h.world(),a,primary,shot).action();
        require(primary.getLevel(Skill.HITS.id())==244,"the same shuriken deals its normal damage with both players inside");
    }

    private static void poisonBoundary(CurrentCombatHarness h) {
        Player a=player(h,"pvp toxin a",180,425,false), b=player(h,"pvp toxin b",181,426,false);
        b.applyPoison(50,50,a);
        com.openrsc.server.event.rsc.impl.PoisonEvent poison=b.getAttribute("poisonEvent",null);
        require(poison!=null,"fixture owns a real poison event");
        poison.run();
        require(b.getLevel(Skill.HITS.id())==245 && b.getCurrentPoisonPower()==47,
            "poison uses the PvE damage and cleansing rate in Wilderness");
        b.setLocation(Point.location(181,427),false);
        poison.run();
        require(b.getLevel(Skill.HITS.id())==245 && b.getCurrentPoisonPower()==44,
            "crossing into safety prevents damage while cleansing continues");
        h.logout(a);poison.run();
        require(b.getLevel(Skill.HITS.id())==245 && b.getCurrentPoisonPower()==41,
            "owner logout cannot erase provenance and bypass safety");
    }

    private static void safeSettlement(CurrentCombatHarness h) {
        Player a=player(h,"pvp final a",180,425,false), b=player(h,"pvp final b",181,426,false);
        Player primary=player(h,"pvp final p",181,425,false);
        DamageRequest pending=DamageRequest.resolvedLegacy(a,b,DamageRequest.SourceCategory.OWNED_EFFECT,
            "projectile-iban-area",20).style(CombatStyle.MAGIC).build();
        b.setLocation(Point.location(181,427),false);
        Party party=new Party(h.world());Clan clan=new Clan(h.world());clan.setClanID(71);
        for(int affiliation=0;affiliation<4;affiliation++) {
            a.setParty((affiliation&1)!=0?party:null);b.setParty(a.getParty());
            a.setClan((affiliation&2)!=0?clan:null);b.setClan(a.getClan());
            int splats=b.getUpdateFlags().getHitSplats().size();
            DamageResult result=h.server().getResolvedDamageTransaction().apply(pending);
            require(result.isPvpSafetyBlocked() && result.isAttackSuppressed(),"every affiliation is blocked outside Wilderness");
            require(result.getActualDamage()==0 && result.getLegacyDamageDealt()==0 && result.getOverkillDamage()==0
                && !result.isTargetTerminal(),"blocked splash cannot supply damage, overkill, lifesteal or a kill");
            require(b.getLevel(Skill.HITS.id())==250 && b.getUpdateFlags().getHitSplats().size()==splats,
                "blocked splash changes neither HP nor hitsplats");
            require(!CombatEffectUtil.isPlayerOwnedPvpSplashTarget(a,primary,b),"safe player never enters splash selection");
        }
        a.setParty(null);b.setParty(null);a.setClan(null);b.setClan(null);
        b.setLocation(Point.location(181,426),false);a.setLocation(Point.location(180,427),false);
        require(h.server().getResolvedDamageTransaction().apply(pending).isPvpSafetyBlocked(),
            "source also cannot fire across the Wilderness boundary from safety");
        a.setLocation(Point.location(180,425),false);
        DamageResult legal=h.server().getResolvedDamageTransaction().apply(pending);
        require(legal.getActualDamage()==20 && !legal.isPvpSafetyBlocked(),"same damage settles unchanged with both players in Wilderness");
        h.server().getConfig().WANT_PVP=false;
        try {
            require(h.server().getResolvedDamageTransaction().apply(pending).isPvpSafetyBlocked(),
                "disabled PvP also blocks a previously prepared indirect hit");
        } finally {h.server().getConfig().WANT_PVP=true;}
    }

    private static void pkModeSafety(CurrentCombatHarness h) {
        Player a=player(h,"pvp pk a",180,425,false),b=player(h,"pvp pk b",181,426,false);
        h.server().getConfig().USES_PK_MODE=true;
        try {
            a.setLocation(Point.location(180,427),false);b.setLocation(Point.location(181,427),false);
            require(!CombatEligibility.evaluate(CombatEligibilityRequest.builder(a,b,
                CombatEligibilityPhase.COMMIT,CombatStyle.MAGIC).playerAttackRules(true).build()).isAllowed(),
                "legacy PK mode cannot authorize a safe-zone MyWorld attack");
            require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(a,b,
                DamageRequest.SourceCategory.OWNED_EFFECT,"pvp-test-legacy-pk",9).build()).isPvpSafetyBlocked(),
                "legacy PK mode cannot authorize a safe-zone MyWorld proc");
        } finally {h.server().getConfig().USES_PK_MODE=false;}
    }

    private static void pveSafety(CurrentCombatHarness h) {
        Player a=player(h,"pvp pve a",180,425,false);a.setLocation(Point.location(180,427),false);
        Npc enemy=h.npc(3,181,427);enemy.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),50,50,false);
        require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(enemy,a,
            DamageRequest.SourceCategory.ACTOR,"pvp-test-pve-incoming",3).build()).getActualDamage()==3,
            "ordinary NPC damage still works outside Wilderness");
        require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(a,enemy,
            DamageRequest.SourceCategory.OWNED_EFFECT,"pvp-test-pve-splash",4).build()).getActualDamage()==4,
            "PvE splash still works outside Wilderness");
        Player bystander=player(h,"pvp pve b",181,425,false);
        bystander.setLocation(Point.location(181,427),false);
        enemy.setAttribute("myworld_summon_owner",a.getUsernameHash());
        require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(enemy,bystander,
            DamageRequest.SourceCategory.OWNED_EFFECT,"summon-bonus-melee",4).build()).isPvpSafetyBlocked(),
            "a player-owned NPC cannot bypass the safe-zone boundary");
        require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(null,a,
            DamageRequest.SourceCategory.DOT,"generic-poison",8)
            .periodicProvenance(PeriodicEffectProvenance.player(UUID.randomUUID())).build()).isPvpSafetyBlocked(),
            "logout cannot erase player provenance to bypass safety");
        require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(null,a,
            DamageRequest.SourceCategory.ENVIRONMENT,"pvp-test-environment",2).build()).getActualDamage()==2,
            "environmental damage still works outside Wilderness");
        a.setLocation(Point.location(180,425),false);
        require(h.server().getResolvedDamageTransaction().apply(DamageRequest.resolvedLegacy(null,a,
            DamageRequest.SourceCategory.DOT,"generic-poison",2)
            .periodicProvenance(PeriodicEffectProvenance.player(UUID.randomUUID())).build()).getActualDamage()==2,
            "owner logout preserves the PvE poison lifetime while the victim remains in Wilderness");
    }

    private static void clericBlessings(CurrentCombatHarness h) throws Exception {
        Player caster=player(h,"pvp cleric c",180,200,false),recipient=player(h,"pvp cleric r",181,200,false),
            enemy=player(h,"pvp cleric e",181,201,false);
        Party party=new Party(h.world());
        Constructor<PartyPlayer> constructor=PartyPlayer.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Method reference=PartyPlayer.class.getDeclaredMethod("setPlayerReference",Player.class);
        reference.setAccessible(true);
        for(Player p:new Player[]{caster,recipient}) {
            p.setParty(party);
            PartyPlayer member=constructor.newInstance(p.getUsername());reference.invoke(member,p);
            member.setRank(p==caster?PartyRank.LEADER:PartyRank.NORMAL);party.getPlayers().add(member);
            if(p==caster)party.setLeader(member);
        }
        caster.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),163,163,false);
        caster.getClientLimitations().maxItemId=65535;
        for(ClericSpellId id:new ClericSpellId[]{ClericSpellId.ZAMORAK_WRATH,ClericSpellId.GUTHIX_VENGEANCE}) {
            ClericSpellDefinition spell=ClericSpellCatalog.get(id);
            for(ClericResourceCost cost:spell.getResourceCosts())
                require(caster.getCarriedItems().getInventory().add(new Item(cost.getItemId(),cost.getCount())),"fixture funds sigil "+cost.getItemId());
            ClericSupportCasting.CastResult result=ClericSupportCasting.cast(caster,spell);
            require(result.getOutcome()==ClericSupportCasting.Outcome.SUCCESS
                && result.getAffectedRecipientCount()==1,"real Wilderness cast affects only the party ally: "+id+" result="+result.getOutcome()+" count="+result.getAffectedRecipientCount()+" power="+caster.getCarriedItems().getEquipment().getHolyPower());
            for(ClericResourceCost cost:spell.getResourceCosts())
                require(caster.getCarriedItems().getInventory().countId(cost.getItemId())==0,"successful cast consumes exact sigil vector");
        }
        require(ClericTimedEffectRuntime.blessing(recipient,ClericEffectFamily.ZAMORAK_BLESSING).getPower()==6,
            "holy-power thresholds retain PvE P6");
        require(ClericDirectCombatRuntime.maximum(recipient,enemy,20)==23,"P6 maximum bonus remains 15% in PvP");
        require(ClericDirectCombatRuntime.beforeDirectDamage(recipient,enemy,20).getDamage()==21,
            "P6 damage bonus keeps PvE fractional carry");
        recipient.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),40,100,false);
        require(ClericDirectCombatRuntime.afterExistingLifesteal(recipient,enemy,10).getRallyHealing()==1,
            "P6 lifesteal keeps PvE threshold and amount");
        require(ClericDirectCombatRuntime.afterExistingLifesteal(enemy,recipient,20).getThornsDamage()==2,
            "P6 thorns keeps PvE 10% reflection against players");
        require(ClericTimedEffectRuntime.blessing(enemy,ClericEffectFamily.GUTHIX_BLESSING)==null,
            "nearby enemy receives no party blessing");
        require(ClericTimedEffectRuntime.blessing(caster,ClericEffectFamily.GUTHIX_BLESSING)==null,
            "casting retains the existing no-self-buff rule");
        caster.setParty(null);
        require(ClericTimedEffectRuntime.blessing(recipient,ClericEffectFamily.GUTHIX_BLESSING)==null,
            "party departure invalidates the blessing in PvP too");
    }

    static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
