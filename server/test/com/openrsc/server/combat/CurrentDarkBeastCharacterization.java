package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.monsterslayer.DarkBeastCombat;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcCombatProfile;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.net.rsc.handlers.ItemActionHandler;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import java.util.ArrayList;
import java.util.List;

/** Real scheduler, combat paths, inventory plugin and lifecycle boundaries. */
final class CurrentDarkBeastCharacterization {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			Npc n = h.npc(869,440,440);
			Player p = player(h,"static target",441,440);
			Player edge = player(h,"static edge",444,444);
			Player late = player(h,"static late",445,440);
			check(n.getLevel(Skill.HITS.id())==200 && n.getMeleeOffense()==110 && n.getMagicOffense()==210
				&& n.getMeleeDefense()==120 && n.getMagicDefense()==120 && n.getRangedDefense()==100,"modern stats");
			check(NpcCombatProfile.resolve(n).isMeleeOnly(),"lightning does not enable ordinary magic projectiles");
			DarkBeastCombat.startCharge(n);
			check(DarkBeastCombat.markCount(p)==1 && DarkBeastCombat.markCount(edge)==1
				&& DarkBeastCombat.markCount(late)==0,"radius four acquisition");
			List<ActiveStatusEntry> statuses=new ArrayList<>(); DarkBeastCombat.appendStatuses(p,statuses);
			check(statuses.size()==1 && statuses.get(0).getRemainingSeconds()==7,"status ten ticks rounded to seconds");
			check(n.getUpdateFlags().getCombatEffect().get().getEffectType()==78,"initial pulse");
			late.setLocation(Point.location(441,441));
			p.setLocation(Point.location(470,470));
			DarkBeastCombat.wipe(edge);
			check(DarkBeastCombat.mitigate(n,20)==10 && DarkBeastCombat.mitigate(n,1)==0,"half damage rounded down");
			// Both melee owners hold without an attack, even after target leaves acquisition range.
			int swings=n.getHitsMade();
			new PvmMeleeEvent(h.world(),n,p).run();
			new CombatEvent(h.world(),n,p).run();
			check(n.getHitsMade()==swings && n.finishedPath(),"charge holds attack and path");
			for(int mode=0;mode<2;mode++) {
				int before=n.getLevel(Skill.HITS.id());
				Object event=mode==0 ? new PvmMeleeEvent(h.world(),p,n) : new CombatEvent(h.world(),p,n);
				CurrentCombatHarness.invokePrivate(event,"inflictDamage",new Class<?>[]{Mob.class,Mob.class,int.class,boolean.class},p,n,20,false);
				check(n.getLevel(Skill.HITS.id())==before-10,"melee settlement and presentation mitigated");
			}
			for(int i=0;i<10;i++) h.advanceOneCombatTick();
			check(DarkBeastCombat.charging(n) && p.getUpdateFlags().getCombatEffect().get()==null,"no early discharge");
			h.advanceOneCombatTick();
			check(!DarkBeastCombat.charging(n) && DarkBeastCombat.markCount(p)==0,"discharge at tick ten");
			check(p.getUpdateFlags().getCombatEffect().get().getEffectType()==CombatEffect.THUNDER_STRIKE,"distant marked player struck");
			check(edge.getUpdateFlags().getCombatEffect().get()==null && late.getUpdateFlags().getCombatEffect().get()==null,"wipe and late arrival safe");
			check(n.getUpdateFlags().getCombatEffect().get().getEffectType()==80 && DarkBeastCombat.mitigate(n,20)==20,"release frame and resistance ends");
			threshold(h);
			mitigation(h);
			wipes(h);
			cancellation(h);
			System.out.println("PASS Dark beast stats, timed poses, marking, range escape, late arrivals, resistance, threshold, wipes and lifecycle");
		}
	}
	private static Player player(CurrentCombatHarness h,String name,int x,int y) throws Exception {
		Player p=h.player(name,x,y); h.recordOutgoingPackets(p);
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),250,250,false); return p;
	}
	private static void finish(CurrentCombatHarness h) throws Exception { for(int i=0;i<12;i++) h.advanceOneCombatTick(); }
	private static void threshold(CurrentCombatHarness h) throws Exception {
		Npc n=h.npc(869,480,480); Player p=player(h,"static half",481,480);
		h.random().scriptInts(99); check(!DarkBeastCombat.tryAttack(n,p),"random non-charge decision");
		n.getSkills().setLevel(Skill.HITS.id(),100);
		check(DarkBeastCombat.tryAttack(n,p),"half health overrides random cooldown");
		DarkBeastCombat.wipe(p); finish(h);
		h.random().scriptInts(99); check(!DarkBeastCombat.tryAttack(n,p),"threshold once per lifetime");
		n.advanceCombatLifecycle();
		check(DarkBeastCombat.tryAttack(n,p),"fresh lifetime resets threshold"); DarkBeastCombat.wipe(p); finish(h);
		Npc random=h.npc(869,484,480); h.random().scriptInts(0);
		check(DarkBeastCombat.tryAttack(random,p),"random charge at full health"); DarkBeastCombat.wipe(p); finish(h);
	}
	private static void wipes(CurrentCombatHarness h) throws Exception {
		Npc a=h.npc(869,500,500), b=h.npc(869,502,500); Player p=player(h,"static wipes",501,500);
		p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
		OpInvTrigger plugin=(OpInvTrigger)Class.forName("com.openrsc.server.plugins.custom.myworld.itemactions.StaticDischargeWipe").newInstance();
		p.getCarriedItems().getInventory().add(new Item(3330));
		while(p.getCarriedItems().getInventory().size()<p.getCarriedItems().getInventory().getCapacity()) p.getCarriedItems().getInventory().add(new Item(3318));
		plugin.onOpInv(p,0,p.getCarriedItems().getInventory().get(0),"Wipe");
		check(p.getCarriedItems().getInventory().get(0).getCatalogId()==3330,"no mark wastes no use");
		for(int next:new int[]{3331,3332,ItemId.EMPTY_VIAL.id()}) {
			DarkBeastCombat.startCharge(a); DarkBeastCombat.startCharge(b);
			check(DarkBeastCombat.markCount(p)==2,"overlapping beasts mark independently");
			Item item=p.getCarriedItems().getInventory().get(0);
			check((Boolean)CurrentCombatHarness.invokePrivate(new ItemActionHandler(),"isCombatConsumableAction",
				new Class<?>[]{Item.class,String.class,Player.class},item,"Wipe",p),"wipe usable during combat");
			plugin.onOpInv(p,0,new Item(item.getCatalogId()),"Wipe"); check(DarkBeastCombat.markCount(p)==2,"forged item rejected");
			plugin.onOpInv(p,0,item,"Wipe");
			check(DarkBeastCombat.markCount(p)==0 && p.getCarriedItems().getInventory().get(0).getCatalogId()==next,"one use clears all marks in full inventory");
			finish(h); check(p.getLevel(Skill.HITS.id())==250,"wiped marks do not strike");
		}
		DarkBeastCombat.startCharge(a); check(DarkBeastCombat.markCount(p)==1,"wipe grants no immunity");
		DarkBeastCombat.wipe(p); finish(h);
	}
	private static void mitigation(CurrentCombatHarness h) throws Exception {
		Npc n=h.npc(869,510,510); Player p=player(h,"static damage",511,510);
		DarkBeastCombat.startCharge(n);
		for(int type:new int[]{1,2}) {
			n.getSkills().setLevel(Skill.HITS.id(),130);
			Object event=new com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent(h.world(),p,n,20,type,false);
			CurrentCombatHarness.invokePrivate(event,"projectileDamage",new Class<?>[0]);
			check(n.getLevel(Skill.HITS.id())==120,"ranged/magic primary damage halved");
		}
		Object[] events={new PvmMeleeEvent(h.world(),p,n),new CombatEvent(h.world(),p,n),
			new com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent(h.world(),p,n,20,1,false)};
		for(Object event:events) for(String method:new String[]{"inflictAuxiliaryMagicDamage","inflictAuxiliaryTrueDamage"}) {
			n.getSkills().setLevel(Skill.HITS.id(),130);
			CurrentCombatHarness.invokePrivate(event,method,new Class<?>[]{Mob.class,Mob.class,int.class},p,n,20);
			check(n.getLevel(Skill.HITS.id())==120,"bonus proc damage halved: "+method);
		}
		n.getSkills().setLevel(Skill.HITS.id(),10);
		CurrentCombatHarness.invokePrivate(new com.openrsc.server.net.rsc.handlers.SpellHandler(),"applyGodSpellSecondaryDamage",
			new Class<?>[]{Player.class,Mob.class,int.class},p,n,12);
		check(n.getLevel(Skill.HITS.id())==4,"god spell mitigation precedes lethal compatibility branch");
		DarkBeastCombat.wipe(p); finish(h);
	}
	private static void cancellation(CurrentCombatHarness h) throws Exception {
		Npc n=h.npc(869,520,520); Player p=player(h,"static cancel",521,520);
		DarkBeastCombat.startCharge(n); n.getSkills().setLevel(Skill.HITS.id(),0); finish(h);
		check(DarkBeastCombat.markCount(p)==0 && p.getLevel(Skill.HITS.id())==250,"source death cancels");
		n.getSkills().setLevel(Skill.HITS.id(),130); DarkBeastCombat.startCharge(n); p.advanceCombatLifecycle(); finish(h);
		check(p.getLevel(Skill.HITS.id())==250 && DarkBeastCombat.markCount(p)==0,"player new lifetime cancels");
		DarkBeastCombat.startCharge(n); p.setLoggedIn(false); finish(h);
		check(p.getLevel(Skill.HITS.id())==250,"logout cancels strike");
	}
	private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
