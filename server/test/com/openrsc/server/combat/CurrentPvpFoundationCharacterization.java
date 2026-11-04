package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import java.lang.reflect.Method;
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

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<String>();
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
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
        }
        if (!failures.isEmpty()) throw new AssertionError("PvP foundation failures: " + failures);
        System.out.println("PASS: 12 PvP foundation scenarios");
    }

    private interface Scenario { void run(CurrentCombatHarness h) throws Exception; }
    private static void checkCase(CurrentCombatHarness h,String name, Scenario scenario, List<String> failures) throws Exception {
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

    private static Player player(CurrentCombatHarness h, String name, int x, int y, boolean retaliate) {
        h.openCombatProjectileRectangle(x-2,x+2,y-2,y+2);
        Player p=h.player(name,x,y);
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

    private static Player[] areaPlayers(CurrentCombatHarness h,String name) {
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

    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
