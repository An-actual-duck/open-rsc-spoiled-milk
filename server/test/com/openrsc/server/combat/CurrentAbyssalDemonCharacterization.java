package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.monsterslayer.*;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcCombatProfile;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.PayloadProcessorManager;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import java.util.ArrayList;
import java.util.List;

/** Real combat settlement, scheduler, action dispatch, consumable and pose contracts. */
public final class CurrentAbyssalDemonCharacterization {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			trapAndActions(h);
			solvent(h);
			attacks(h);
			lifecycle(h);
			System.out.println("PASS Abyssal stats, one-tick stabs, radius-one immediate spikes, four-tick recovery, full-action trap, shared immunity, solvent and lifecycle");
		}
	}
	private static Player player(CurrentCombatHarness h, String name, int x, int y) throws Exception {
		Player p=h.player(name,x,y); h.recordOutgoingPackets(p);
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),2000,2000,false);
		p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
		return p;
	}
	private static void trapAndActions(CurrentCombatHarness h) throws Exception {
		Npc n=h.npc(870,440,440), second=h.npc(870,440,441);
		Player p=player(h,"flesh trap",441,440);
		check(n.getLevel(Skill.HITS.id())==250 && n.getMeleeOffense()==125
			&& n.getMeleeDefense()==110 && n.getRangedDefense()==220 && n.getMagicDefense()==220,"modern stats");
		check(n.getDef().getAtt()==1 && n.getDef().getStr()==1 && NpcCombatProfile.resolve(n).isMeleeOnly(),"no legacy stat adapter or projectile attack");
		AbyssalDemonCombat.onDamage(n,p,0);
		check(!AbyssalDemonCombat.actionsBlocked(p),"miss does not trap");
		p.walk(442,440);
		AbyssalDemonCombat.onDamage(n,p,1);
		check(p.finishedPath() && AbyssalDemonCombat.actionsBlocked(p),"queued movement cancelled");
		for(CombatStyle style:CombatStyle.values()) for(CombatEligibilityPhase phase:CombatEligibilityPhase.values()) {
			if(phase==CombatEligibilityPhase.COMPATIBILITY) continue;
			check(CombatEligibility.evaluate(CombatEligibilityRequest.builder(p,n,phase,style).build()).getReason()
				==CombatEligibilityReason.SOURCE_ABYSSAL_TRAP,"attack admission blocked");
		}
		for(OpcodeIn opcode:new OpcodeIn[]{OpcodeIn.WALK_TO_POINT,OpcodeIn.NPC_ATTACK,OpcodeIn.ITEM_COMMAND,
			OpcodeIn.ITEM_USE_ITEM,OpcodeIn.CAST_ON_SELF,OpcodeIn.CAST_ON_NPC,OpcodeIn.BLINK,
			OpcodeIn.ITEM_EQUIP_FROM_INVENTORY,OpcodeIn.PRAYER_ACTIVATED,OpcodeIn.PRAYER_DEACTIVATED,
			OpcodeIn.QUESTION_DIALOG_ANSWER,OpcodeIn.COMMAND,OpcodeIn.INTERFACE_OPTIONS,OpcodeIn.GROUND_ITEM_TAKE,
			OpcodeIn.BANK_LOAD_PRESET,OpcodeIn.PLAYER_INIT_TRADE_REQUEST,OpcodeIn.GAME_SETTINGS_CHANGED}) {
			check(PayloadProcessorManager.blockedByAbyssalTrap(opcode,p),"all gameplay action categories blocked: "+opcode);
			AbstractStruct<OpcodeIn> payload=new AbstractStruct<OpcodeIn>(){}; payload.setOpcode(opcode);
			check(PayloadProcessorManager.processed(payload,p),"discarded before handler, no malformed disconnect");
		}
		for(OpcodeIn opcode:new OpcodeIn[]{OpcodeIn.HEARTBEAT,OpcodeIn.CHAT_MESSAGE,OpcodeIn.KNOWN_PLAYERS,
			OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE,OpcodeIn.LAYERED_TERRAIN_READY,OpcodeIn.REPORT_ABUSE})
			check(!PayloadProcessorManager.blockedByAbyssalTrap(opcode,p),"non-gameplay/transport remains usable");
		p.walk(442,440); p.getWalkingQueue().processNextMovement();
		check(p.getX()==441,"server movement guard");
		List<ActiveStatusEntry> rows=new ArrayList<>(); AbyssalDemonCombat.appendStatuses(p,rows);
		check(rows.size()==1 && rows.get(0).getRemainingSeconds()==3,"three second status");
		long start=h.server().getCurrentTick();
		for(int i=0;i<5;i++) {
			h.advanceOneCombatTick();
			AbyssalDemonCombat.onDamage(second,p,1);
			check(AbyssalDemonCombat.actionsBlocked(p)==(i<4),"unrefreshed three second expiry");
		}
		check(messages(p,AbyssalDemonCombat.TRAP_MESSAGE)==1,"no reapplication spam");
		// First free server tick plus the next are immune, even to another demon.
		check(h.server().getCurrentTick()==start+5,"first unlocked tick");
		AbyssalDemonCombat.onDamage(n,p,1);
		check(!AbyssalDemonCombat.actionsBlocked(p),"first immunity tick");
		h.advanceOneCombatTick();
		check(messages(p,AbyssalDemonCombat.RELEASE_MESSAGE)==1,"one release announcement");
		AbyssalDemonCombat.onDamage(second,p,1);
		check(!AbyssalDemonCombat.actionsBlocked(p),"second immunity tick");
		h.advanceOneCombatTick();
		AbyssalDemonCombat.onDamage(second,p,1);
		check(AbyssalDemonCombat.actionsBlocked(p),"new trap after two free ticks");
		check(messages(p,AbyssalDemonCombat.TRAP_MESSAGE)==2,"new trap announced once");
		p.advanceCombatLifecycle();
		check(!AbyssalDemonCombat.actionsBlocked(p),"trap cannot cross player lifetimes");
	}
	private static void solvent(CurrentCombatHarness h) throws Exception {
		Npc n=h.npc(870,460,460), second=h.npc(870,460,461);
		Player p=player(h,"abyssal solvent",461,460);
		OpInvTrigger plugin=(OpInvTrigger)Class.forName("com.openrsc.server.plugins.custom.myworld.itemactions.SlimeSolvent").newInstance();
		p.getCarriedItems().getInventory().add(new Item(3318));
		Item bottle=p.getCarriedItems().getInventory().get(0);
		AbyssalDemonCombat.onDamage(n,p,1);
		plugin.onOpInv(p,0,bottle,"Drink");
		check(p.getCarriedItems().getInventory().get(0)==bottle && !GiantFrogCombat.protectedBySolvent(p),"cannot drink solvent while trapped");
		for(int i=0;i<6;i++) h.advanceOneCombatTick();
		plugin.onOpInv(p,0,bottle,"Drink");
		check(p.getCarriedItems().getInventory().get(0).getCatalogId()==3319 && GiantFrogCombat.protectedBySolvent(p),"drink during escape window, three-use bottle");
		AbyssalDemonCombat.onDamage(n,p,0);
		check(solventSeconds(p)==600,"miss does not drain");
		Object event=new PvmMeleeEvent(h.world(),n,p);
		CurrentCombatHarness.invokePrivate(event,"inflictDamage",new Class<?>[]{Mob.class,Mob.class,int.class,boolean.class},n,p,10,false);
		check(solventSeconds(p)==595 && !AbyssalDemonCombat.actionsBlocked(p),"resolved damaging stab drains exactly five seconds");
		AbyssalDemonCombat.onDamage(second,p,1);
		check(solventSeconds(p)==590,"separate demons drain shared timer");
		GiantFrogCombat.drainSolvent(p,589000);
		AbyssalDemonCombat.onDamage(n,p,1);
		check(!GiantFrogCombat.protectedBySolvent(p) && !AbyssalDemonCombat.actionsBlocked(p),"last protected hit clamps timer at zero");
		AbyssalDemonCombat.onDamage(n,p,1);
		check(AbyssalDemonCombat.actionsBlocked(p),"next hit traps");
		p.advanceCombatLifecycle();
		GiantFrogCombat.applySolvent(p);
		Npc frog=h.npc(863,462,460);
		GiantFrogCombat.onSpitImpact(frog,p,1);
		check(!GiantFrogCombat.attacksBlocked(p) && p.getCurrentPoisonPower()>0 && solventSeconds(p)==600,"frog protection unchanged, no frog erosion, poison remains");
	}
	private static int solventSeconds(Player p) {
		List<ActiveStatusEntry> rows=new ArrayList<>(); GiantFrogCombat.appendStatuses(p,rows);
		return rows.isEmpty()?0:rows.get(0).getRemainingSeconds();
	}
	private static void attacks(CurrentCombatHarness h) throws Exception {
		Npc n=h.npc(870,480,480);
		Player p=player(h,"abyssal cadence",481,480);
		// Select genuinely open native terrain, not legacy compatibility tiles.
		boolean open=false;
		for(int x=480;x<520 && !open;x++) for(int y=440;y<480 && !open;y++) {
			n.setLocation(Point.location(x,y)); p.setLocation(Point.location(x+1,y));
			com.openrsc.server.model.world.coordinate.WorldLocation diagonalAt=h.world().getRegionManager()
				.fromRuntimeCompatibilityPoint(Point.location(x+1,y+1),n.getWorldLocation(),false);
			open=com.openrsc.server.model.PathValidation.checkEnemyCombatProjectilePath(h.world(),n.getWorldLocation(),p.getWorldLocation())
				&& com.openrsc.server.model.PathValidation.checkEnemyCombatProjectilePath(h.world(),n.getWorldLocation(),diagonalAt)
				&& com.openrsc.server.model.PathValidation.checkAdjacentDistance(n,p,true,false);
		}
		check(open,"open native test area found");
		GiantFrogCombat.applySolvent(p);
		PvmMeleeEvent event=new PvmMeleeEvent(h.world(),n,p); n.setPvmMeleeEvent(event);
		for(int i=0;i<4;i++) {
			int before=n.getHitsMade();
			h.random().scriptInts(99);
			event.run();
			check(event.getDelayTicks()==1 && n.getHitsMade()==before+1,"one stab per tick");
			check(n.getUpdateFlags().getCombatEffect().get().getEffectType()==85,"stab animation synced");
			event.run(); check(n.getHitsMade()==before+1,"duplicate callback cannot double stab");
			h.advanceOneCombatTick();
		}
		Player diagonal=player(h,"spikes diagonal",n.getX()+1,n.getY()+1), far=player(h,"spikes far",n.getX()+2,n.getY());
		List<Player> hit=new ArrayList<>();
		long start=h.server().getCurrentTick();
		AbyssalDemonCombat.startSpikes(n,(victim,damage)->{
			hit.add(victim);
			try { CurrentCombatHarness.invokePrivate(event,"inflictDamage",
				new Class<?>[]{Mob.class,Mob.class,int.class,boolean.class},n,victim,10,false); }
			catch(Exception e) { throw new RuntimeException(e); }
		});
		check(hit.contains(p) && hit.contains(diagonal) && !hit.contains(far) && hit.size()==2,"immediate radius one, including diagonals");
		check(AbyssalDemonCombat.actionsBlocked(diagonal),"AoE uses same trap settlement");
		check(n.getUpdateFlags().getCombatEffect().get().getEffectType()==82,"spike start pose");
		int hits=n.getHitsMade();
		for(int i=0;i<4;i++) {
			event.run(); check(n.getHitsMade()==hits,"no stabs during four tick recovery");
			check(AbyssalDemonCombat.recovering(n),"cannot chase while submerged");
			h.advanceOneCombatTick();
			if(i==2) check(n.getUpdateFlags().getCombatEffect().get().getEffectType()==83,"rise after two extended ticks");
		}
		check(h.server().getCurrentTick()==start+4 && !AbyssalDemonCombat.recovering(n),"resume exactly at tick four");
		event.run(); check(n.getHitsMade()==hits+1,"melee resumes after recovery");
		h.advanceOneCombatTick();
		check(n.getUpdateFlags().getCombatEffect().get().getEffectType()==85,"cleanup cannot overwrite fresh stab");
	}
	private static void lifecycle(CurrentCombatHarness h) throws Exception {
		Npc n=h.npc(870,500,500); Player p=player(h,"abyssal lifetime",501,500);
		AbyssalDemonCombat.onDamage(n,p,1); p.setLoggedIn(false);
		check(!AbyssalDemonCombat.actionsBlocked(p),"logout clears active trap eligibility");
		p.advanceCombatLifecycle(); p.setLoggedIn(true);
		for(int i=0;i<8;i++) h.advanceOneCombatTick();
		check(messages(p,AbyssalDemonCombat.RELEASE_MESSAGE)==0,"no stale release after logout/lifetime change");
		AbyssalDemonCombat.startSpikes(n,(victim,damage)->{});
		n.getSkills().setLevel(Skill.HITS.id(),0); h.advanceOneCombatTick();
		check(n.getUpdateFlags().getCombatEffect().get().getEffectType()==84,"death cancels pose");
		n.advanceCombatLifecycle(); n.getSkills().setLevel(Skill.HITS.id(),250);
		check(!AbyssalDemonCombat.recovering(n),"new NPC lifetime has no stale recovery");
		Player exact=player(h,"exact three seconds",501,501);
		AbyssalDemonCombat.onDamage(n,exact,1);
		h.clock().advanceMillis(2999); check(AbyssalDemonCombat.actionsBlocked(exact),"not released early");
		h.clock().advanceMillis(1); check(!AbyssalDemonCombat.actionsBlocked(exact),"exact three seconds, not three ticks");
	}
	@SuppressWarnings("unchecked")
	private static int messages(Player p,String text) throws Exception {
		List<Packet> packets=(List<Packet>)CurrentCombatHarness.readPrivateField(p,"outgoingPackets");
		int count=0;
		for(Packet packet:packets) if(packet.getBuffer().toString(java.nio.charset.StandardCharsets.ISO_8859_1).contains(text)) count++;
		return count;
	}
	private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
