package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.PoisonPower;
import com.openrsc.server.content.monsterslayer.*;
import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.event.rsc.impl.combat.*;
import com.openrsc.server.event.rsc.impl.projectile.*;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.combat.*;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;

/** Production definitions, transactions, schedulers and primary impact hooks. */
public final class SlayerRewardsFixture {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			definitions(h); shops(h); whip(h); bow(h); dagger(h); pendant(h); thunder(h);
			System.out.println("PASS Slayer rewards: all recipes, requirements, whip immunity, bow launch ownership/fractions, dagger cadence/poison, pendant immunity and thunder selection/scaling");
		}
	}
	private static void definitions(CurrentCombatHarness h) throws Exception {
		int[] ids = {3350,3351,3352,3353,3354,3355};
		int[] levels = {70,62,54,30,30,0};
		for (int i = 0; i < ids.length; i++) {
			ItemDefinition d = h.server().getEntityHandler().getItemDef(ids[i]);
			check(d.isWieldable() && !d.isUntradable() && !d.isStackable(), "equipment semantics " + ids[i]);
			check(d.getRequiredLevel() == levels[i], "required level " + ids[i]);
			Player p = h.player("requirement" + i, 400 + i, 400);
			if (levels[i] > 0) {
				p.getSkills().setLevel(d.getRequiredSkillIndex(), levels[i] - 1);
				p.getSkills().setTemporaryLevelAndMaxStat(d.getRequiredSkillIndex(), levels[i] - 1, levels[i] - 1, false);
				check(!p.getCarriedItems().getEquipment().ableToEquip(new Item(ids[i])), "below requirement rejected");
				p.getSkills().setLevel(d.getRequiredSkillIndex(), levels[i]);
				p.getSkills().setTemporaryLevelAndMaxStat(d.getRequiredSkillIndex(), levels[i], levels[i], false);
			}
			check(p.getCarriedItems().getEquipment().ableToEquip(new Item(ids[i])), "requirement sufficient");
		}
		check(h.server().getEntityHandler().getItemDef(3350).getMeleeOffense() == 72, "whip tier ten");
		check(h.server().getEntityHandler().getItemDef(3350).getWeaponSpeed() == 5, "whip dagger speed");
		check(h.server().getEntityHandler().getItemDef(3351).getMagicOffense() == 48, "staff tier nine");
		check(h.server().getEntityHandler().getItemDef(3352).getRangedOffense() == 40 && RangeUtils.isBow(3352), "bow tier eight and recognized");
		for (int arrow = 0; arrow < h.server().getEntityHandler().items.size(); arrow++)
			check(RangeUtils.canFire(3352, arrow) == RangeUtils.canFire(2125, arrow), "bow arrow parity " + arrow);
		check(h.server().getEntityHandler().getItemDef(3353).getMeleeOffense() == 9, "dagger tier five");
	}
	private static void shops(CurrentCombatHarness h) throws Exception {
		MonsterSlayerData data = MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"), new MonsterSlayerData.ReferenceCatalog() {
			public boolean npcExists(int id) { return true; }
			public boolean npcAttackable(int id) { return true; }
			public boolean npcSpawned(int id) { return true; }
			public boolean itemExists(int id) { return h.server().getEntityHandler().getItemDef(id) != null; }
		});
		h.installMonsterSlayerData(data);
		String[] shop = {"legends", "heroes", "champions", "champions", "brimhaven"};
		String[] reward = {"abyssal_whip", "thunder_spire_staff", "leaching_bow", "dagger_of_terror", "sullen_pendant"};
		int[] outputs = {3350,3351,3352,3353,3355}, costs = {210,200,85,85,105};
		int[][] inputs = {{3348,3347,3340},{3346,636},{3345,2113},{3344},{3343}};
		int[][] amounts = {{1,10,50},{1,1},{2,1},{1},{1}};
		MonsterSlayerShopService service = new MonsterSlayerShopService(data);
		for (int i = 0; i < shop.length; i++) {
			Player p = h.player("reward buyer" + i, 410 + i, 400);
			p.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			Map<MonsterSlayerChallenge,Long> balances = new EnumMap<>(MonsterSlayerChallenge.class);
			for (MonsterSlayerChallenge c : MonsterSlayerChallenge.values()) balances.put(c, (long)costs[i]);
			Map<String,Integer> cursors = new LinkedHashMap<>();
			for (MonsterSlayerDefinitions.Contact c : data.getContactsInChallengeOrder()) cursors.put(c.getKey(), 0);
			MonsterSlayerState.write(p.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING,
				MonsterSlayerBalances.of(balances), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
			check(!service.redeem(p, shop[i], shop[i]+"."+reward[i], 1).isSuccessful(), "missing components refused");
			for (int j = 0; j < inputs[i].length; j++) p.getCarriedItems().getInventory().add(new Item(inputs[i][j], amounts[i][j]));
			check(service.redeem(p, shop[i], shop[i]+"."+reward[i], 1).isSuccessful(), "recipe purchase " + reward[i]);
			check(p.getCarriedItems().getInventory().countId(outputs[i]) == 1, "one output");
			for (int id : inputs[i]) check(p.getCarriedItems().getInventory().countId(id) == 0, "consumed exact components");
			check(!service.redeem(p, shop[i], shop[i]+"."+reward[i], 1).isSuccessful(), "no duplicate grant");
		}
	}
	private static void whip(CurrentCombatHarness h) throws Exception {
		Player a = h.player("whip a", 430, 430), b = h.player("whip b", 430, 431);
		Npc n = h.npc(3, 431, 430);
		h.equip(a, 3350, 1); h.equip(b,3350,1);
		h.random().reset(1); h.random().scriptInts(0);
		SlayerRewardCombat.whipHit(a,n,0); check(!SlayerRewardCombat.whipBlocked(n), "zero cannot delay");
		SlayerRewardCombat.whipHit(a,n,1); check(SlayerRewardCombat.whipBlocked(n), "proc delays target");
		check(SlayerCombatEffects.attacksBlocked(n), "attack scheduler gate");
		check(!CombatEligibility.evaluate(CombatEligibilityRequest.builder(n,a,CombatEligibilityPhase.COMMIT,CombatStyle.MELEE).build()).isAllowed(), "admission gate");
		for (int i = 1; i <= 3; i++) {
			h.advanceOneCombatTick(); h.random().reset(1); h.random().scriptInts(0);
			SlayerRewardCombat.whipHit(b,n,1); check(!SlayerRewardCombat.whipBlocked(n), "shared immunity tick " + i);
		}
		h.advanceOneCombatTick(); h.random().reset(1); h.random().scriptInts(0);
		SlayerRewardCombat.whipHit(b,n,1); check(SlayerRewardCombat.whipBlocked(n), "can proc after immunity");
		n.advanceCombatLifecycle(); check(!SlayerRewardCombat.whipBlocked(n), "no lock inherited by new life");
		check(!SlayerRewardCombat.legalSecondary(a,b), "PvP off");
		Method hit = PvmMeleeEvent.class.getDeclaredMethod("inflictDamage",Mob.class,Mob.class,int.class,boolean.class,boolean.class);
		hit.setAccessible(true);
		n.getSkills().setTemporaryLevelAndMaxStat(3,100,100,false);
		PvmMeleeEvent melee = new PvmMeleeEvent(h.world(),a,n);
		h.random().reset(1); h.random().scriptInts(0);
		hit.invoke(melee,a,n,1,false,true); check(!SlayerRewardCombat.whipBlocked(n),"offhand cannot proc whip");
		h.random().reset(1); h.random().scriptInts(0);
		hit.invoke(melee,a,n,1,false,false); check(SlayerRewardCombat.whipBlocked(n),"real primary melee procs whip");
	}
	private static void bow(CurrentCombatHarness h) throws Exception {
		Player p = h.player("bow leach", 450,450); p.getSkills().setLevel(3,10);
		for (int i=0; i<4; i++) SlayerRewardCombat.leachingBowHit(p,1);
		check(p.getLevel(3)==10,"fraction not rounded up");
		SlayerRewardCombat.leachingBowHit(p,1); check(p.getLevel(3)==11,"fraction eventually heals");
		SlayerRewardCombat.leachingBowHit(p,1000); check(p.getLevel(3)==p.getSkills().getMaxStat(3),"heal capped");
		p.getSkills().setLevel(3,10); SlayerRewardCombat.leachingBowHit(p,1); check(p.getLevel(3)==10,"overheal not banked");
		p.advanceCombatLifecycle(); SlayerRewardCombat.leachingBowHit(p,4); check(p.getLevel(3)==10,"fraction lifetime reset");
		p.advanceCombatLifecycle(); h.equip(p,3352,1);
		Npc n=h.npc(3,452,450); n.getSkills().setLevel(3,100);
		ProjectileEvent shot=new ProjectileEvent(h.world(),p,n,ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_BOW,10,2).presentation(2,0,false).build());
		h.equip(p,656,1); shot.action(); check(p.getLevel(3)==12,"launch bow retains effect after swap");
		shot.action(); check(p.getLevel(3)==12,"duplicate impact not healed twice");
		ProjectileEvent normal=new ProjectileEvent(h.world(),p,n,ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_BOW,10,2).presentation(2,0,false).build());
		h.equip(p,3352,1); normal.action(); check(p.getLevel(3)==12,"equipping after launch grants nothing");
	}
	private static void dagger(CurrentCombatHarness h) throws Exception {
		Player p=h.player("terror dagger",470,470); Npc n=h.npc(3,471,470);
		p.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		for(int id:new int[]{3353,3354}) {
			h.equip(p,id,1);
			for(Object event:new Object[]{new PvmMeleeEvent(h.world(),p,n),new CombatEvent(h.world(),p,n)}) {
				Method m=event.getClass().getDeclaredMethod("getAdjustedMeleeDelayTicks",Mob.class,int.class); m.setAccessible(true);
				check((Integer)m.invoke(event,p,4)==1,"one tick melee cadence " + id);
			}
		}
		check(PoisonPower.getWeaponMaxPoisonPower(3354)==50 && PoisonPower.getWeaponAppliedPoisonPower(3354)==20,"tier five poison");
		Class<?> plugin=Class.forName("com.openrsc.server.plugins.authentic.itemactions.InvItemPoisoning");
		Object instance=plugin.getConstructor().newInstance();
		p.getCarriedItems().getInventory().add(new Item(3353));
		p.getCarriedItems().getInventory().add(new Item(ItemId.WEAPON_POISON.id()));
		plugin.getMethod("onUseInv",Player.class,Integer.class,Item.class,Item.class).invoke(instance,p,0,new Item(ItemId.WEAPON_POISON.id()),new Item(3353));
		check(p.getCarriedItems().getInventory().countId(3354)==1,"existing poison action converts dagger");
	}
	private static void pendant(CurrentCombatHarness h) throws Exception {
		Player p=h.player("sullen wearer",480,480); h.equip(p,3355,1);
		Npc n=h.npc(3,481,480); n.getSkills().setTemporaryLevelAndMaxStat(3,100,100,false);
		h.random().reset(1); h.random().scriptInts(0,9);
		SlayerRewardCombat.pendantHit(p,n,0); check(n.getLevel(3)==100,"zero incoming damage no proc");
		SlayerRewardCombat.pendantHit(p,n,1); check(n.getLevel(3)==90,"ten percent max HP retaliation");
		h.random().reset(1); h.random().scriptInts(0,0); SlayerRewardCombat.pendantHit(p,n,1); check(n.getLevel(3)==89,"one percent lower bound");
		Npc boss=h.npc(477,482,480); int before=boss.getLevel(3);
		h.random().reset(1); h.random().scriptInts(0,9); SlayerRewardCombat.pendantHit(p,boss,1); check(boss.getLevel(3)==before,"KBD immune");
		Player other=h.player("pvp disabled",483,480); before=other.getLevel(3);
		SlayerRewardCombat.pendantHit(p,other,1); check(other.getLevel(3)==before,"no PvP retaliation");
		Method hit=PvmMeleeEvent.class.getDeclaredMethod("inflictDamage",Mob.class,Mob.class,int.class,boolean.class,boolean.class);
		hit.setAccessible(true); PvmMeleeEvent melee=new PvmMeleeEvent(h.world(),n,p);
		before=n.getLevel(3); h.random().reset(1); h.random().scriptInts(0,9);
		hit.invoke(melee,n,p,1,false,true); check(n.getLevel(3)==before,"offhand cannot trigger pendant");
		h.random().reset(1); h.random().scriptInts(0,9);
		hit.invoke(melee,n,p,1,false,false); check(n.getLevel(3)==before-10,"real primary melee triggers pendant");
		before=n.getLevel(3); h.random().reset(1); h.random().scriptInts(0,9);
		new ProjectileEvent(h.world(),n,p,ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.NPC_MAGIC,1,1).presentation(1,0,false).build()).action();
		check(n.getLevel(3)==before-10,"primary magic projectile triggers pendant");
	}
	private static void thunder(CurrentCombatHarness h) throws Exception {
		Player p=h.player("spire caster",490,490); h.equip(p,3351,1);
		check(SlayerRewardCombat.thunderTier(p,Spells.THUNDER_BALL)==1 && SlayerRewardCombat.thunderTier(p,Spells.THUNDER_SPLASH)==2
			&& SlayerRewardCombat.thunderTier(p,Spells.THUNDER_STRIKE)==3,"spell tiers");
		check(SlayerRewardCombat.thunderCap(3,.8)==1 && SlayerRewardCombat.thunderCap(1,.4)==.4,"only top cap removed");
		check(SlayerRewardCombat.thunderSecondaryPower(1,100)==15 && SlayerRewardCombat.thunderSecondaryPower(2,100)==25
			&& SlayerRewardCombat.thunderSecondaryPower(3,100)==40,"splash powers");
		Npc primary=h.npc(3,493,490), near=h.npc(3,494,490), far=h.npc(3,496,490), outside=h.npc(3,497,490);
		check(!SlayerRewardCombat.thunderTarget(p,primary,primary,3),"primary excluded");
		check(SlayerRewardCombat.thunderTarget(p,primary,near,1) && !SlayerRewardCombat.thunderTarget(p,primary,far,2)
			&& SlayerRewardCombat.thunderTarget(p,primary,far,3) && !SlayerRewardCombat.thunderTarget(p,primary,outside,3),"impact centered radii");
		check(!SlayerRewardCombat.thunderTarget(p,primary,h.player("no splash pvp",494,490),3),"PvP excluded");
		check(!SlayerRewardCombat.thunderTarget(p,primary,h.npc(95,494,490),3),"unattackable NPC excluded");
		ProjectileLaunchSpecification spec=ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_MAGIC,5,1).thunderSpire(3,100).build();
		check(spec.getThunderSpireTier()==3 && spec.getThunderSpirePower()==100,"launch spell snapshot");
		for(Npc n:new Npc[]{primary,near,far,outside}) n.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
		h.random().reset(1); h.random().scriptInts(10,0,10,0,10,0,10,0);
		new ProjectileEvent(h.world(),p,primary,spec).action();
		check(primary.getLevel(3)==995,"staff primary not hit by splash again");
		check(near.getLevel(3)<1000 && far.getLevel(3)<1000,"real thunder projectile splashes nearby NPCs");
		check(outside.getLevel(3)==1000,"outside radius not damaged");
	}
	private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
