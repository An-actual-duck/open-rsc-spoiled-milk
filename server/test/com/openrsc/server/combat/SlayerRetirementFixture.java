package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.content.production.*;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.Cache;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.*;
import java.nio.file.Paths;
import java.util.*;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.*;

/** Real definitions/production plus all historical task boundaries and optional-boss state. */
public final class SlayerRetirementFixture {
	private static final MonsterSlayerData.ReferenceCatalog CATALOG = new MonsterSlayerData.ReferenceCatalog() {
		public boolean npcExists(int id) { return true; }
		public boolean npcAttackable(int id) { return true; }
		public boolean npcSpawned(int id) { return true; }
		public boolean itemExists(int id) { return true; }
	};
	private static MonsterSlayerData data(boolean tower, boolean historical) {
		return historical ? MonsterSlayerData.loadHistorical(Paths.get("conf/server/defs/extras/MonsterSlayer.json"), CATALOG, tower)
			: MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"), CATALOG, tower);
	}
	public static void main(String[] args) throws Exception {
		for (boolean tower : new boolean[]{false,true}) {
			MonsterSlayerData current = data(tower,false);
			for (Contact contact : current.getContactsInChallengeOrder()) {
				for (Task task : concat(contact.getMandatoryTasks(),contact.getRepeatableTasks()))
					check(!Arrays.asList("bear","giant","moss_giant","ice_giant","fire_giant","ogre","jogre").contains(task.getFamilyKey()),"retired family absent");
				check(current.getShop(contact.getKey()).getCapacityUpgrade().getCost().get(contact.getChallenge())
					== data(tower,true).getShop(contact.getKey()).getCapacityUpgrade().getCost().get(contact.getChallenge()),"backpack price preserved");
			}
			check(current.getTask("falador.black_unicorns")!=null,"black unicorn task retained");
			migrations(data(tower,true),current);
			if (tower) { migrations(data(false,true),current); migrations(data(false,false),current); }
		}
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			materials(h);
			bosses(h,data(true,false));
		}
		System.out.println("PASS Slayer retirement: historical migrations, preserved balances/ranks/items, retired routes, boss opt-in/access/cancel/payout");
	}
	private static List<Task> concat(List<Task> a,List<Task> b) { List<Task> all=new ArrayList<>(a);all.addAll(b);return all; }
	private static MonsterSlayerState.Snapshot seed(MonsterSlayerData data,int tier,int cursor,String active,int kills) {
		Map<String,Integer> counts=new LinkedHashMap<>(); int i=0;
		for(Contact contact:data.getContactsInChallengeOrder()) counts.put(contact.getKey(),i++<tier?contact.getMandatoryTasks().size():i-1==tier?cursor:0);
		MonsterSlayerBalances balances=MonsterSlayerBalances.zero();
		for(MonsterSlayerChallenge challenge:MonsterSlayerChallenge.values()) balances=balances.credit(challenge,321);
		return MonsterSlayerState.create(2,MonsterSlayerRank.fromCode(tier+1),balances,counts,active,kills,500L,3,1,MonsterSlayerState.LegacyStatus.NONE,0,data);
	}
	private static void migrations(MonsterSlayerData old,MonsterSlayerData current) {
		int tier=0;
		for(Contact contact:old.getContactsInChallengeOrder()) {
			for(int cursor=0;cursor<contact.getMandatoryTasks().size();cursor++) for(boolean active:new boolean[]{false,true}) {
				Task task=contact.getMandatoryTasks().get(cursor);
				verifyMigration(old,current,seed(old,tier,cursor,active?task.getKey():null,active?1:0));
			}
			for(Task task:contact.getRepeatableTasks()) verifyMigration(old,current,seed(old,tier+1,0,task.getKey(),0));
			tier++;
		}
		verifyMigration(old,current,seed(old,6,0,null,0));
	}
	private static void verifyMigration(MonsterSlayerData old,MonsterSlayerData current,MonsterSlayerState.Snapshot before) {
		Cache cache=new Cache(); MonsterSlayerState.write(cache,old,before); cache.store("unrelated","keep");
		Map<String,Object> original=new LinkedHashMap<>(cache.getCacheMap());
		MonsterSlayerState.Snapshot after=MonsterSlayerState.read(cache,current);
		check(original.equals(cache.getCacheMap()),"migration proposal read-only");
		check(after.getRank().isAtLeast(before.getRank()),"earned rank retained");
		check(after.getTasksCompleted()==before.getTasksCompleted() && after.getInventoryUpgrades()==before.getInventoryUpgrades(),"no unearned completion/entitlement");
		for(MonsterSlayerChallenge c:MonsterSlayerChallenge.values()) check(after.getBalances().get(c)==before.getBalances().get(c),"no migration payout");
		boolean retired=before.getActiveTaskKey()!=null && current.getTask(before.getActiveTaskKey())==null;
		check(Objects.equals(after.getActiveTaskKey(),retired?null:before.getActiveTaskKey()),"active assignment preserved or retired");
		check(after.getActiveKills()==(retired?0:before.getActiveKills()),"active kill count");
		MonsterSlayerState.write(cache,current,after);
		Map<String,Object> persisted=new LinkedHashMap<>(cache.getCacheMap());
		MonsterSlayerState.write(cache,current,MonsterSlayerState.read(cache,current));
		check(persisted.equals(cache.getCacheMap()) && "keep".equals(cache.getString("unrelated")),"idempotent migration");
	}
	private static void materials(CurrentCombatHarness h)throws Exception {
		Player owner=h.player("legacy hides",440,440), other=h.player("other hides",441,440);
		owner.getClientLimitations().maxItemId=Integer.MAX_VALUE;
		Object rack=Class.forName("com.openrsc.server.plugins.custom.skills.crafting.TanningRack").getConstructor().newInstance();
		Method find=rack.getClass().getDeclaredMethod("getProcess",int.class);find.setAccessible(true);
		Class<?> craftType=Class.forName("com.openrsc.server.plugins.authentic.skills.crafting.Crafting"); Object craft=craftType.getConstructor().newInstance();
		Method session=craftType.getDeclaredMethod("createLeatherProductionSession",Player.class,Item.class);session.setAccessible(true);
		Method produce=craftType.getMethod("beginProductionFromInterface",Player.class,ProductionSession.class,int.class,int.class);
		int bound=0;
		for(int[] range:new int[][]{{1807,1816},{1875,1884},{1895,1904},{1915,1919}}) for(int id=range[0];id<=range[1];id++) {
			ItemDefinition def=h.server().getEntityHandler().getItemDef(id);
			check(def!=null && def.isUntradable() && def.isNoteable(),"legacy definition bound "+id);bound++;
			for(boolean noted:new boolean[]{false,true}) {
				check(new Item(id,1,noted).getDef(h.world()).isUntradable(),"notes also bound");
				GroundItem item=new GroundItem(h.world(),id,440,440,1,owner,noted);
				check(!item.isInvisibleTo(owner) && item.isInvisibleTo(other),"ground remains owner-only");
			}
		}
		check(bound==35,"exact retired definition count");
		owner.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);
		owner.getCarriedItems().getInventory().add(new Item(ItemId.NEEDLE.id()));
		owner.getCarriedItems().getInventory().add(new Item(ItemId.THREAD.id(),5));
		for(int[] family:new int[][]{{1807,1808,1875},{1809,1810,1895},{1811,1812,1900},{1813,1814,1915},{1815,1816,1880}}) {
			check(find.invoke(rack,family[0])==null && session.invoke(craft,owner,new Item(family[1]))==null,"retired production absent");
			owner.getCarriedItems().getInventory().add(new Item(family[1],4));
			for(int id=family[2];id<family[2]+5;id++) {
				ProductionSession stale=new ProductionSession(ProductionSession.TYPE_CRAFTING,"retired",family[1],Collections.singletonList(new ProductionRecipe(id,1,1,1,true,true)));
				check(!(Boolean)produce.invoke(null,owner,stale,id,1),"stale recipe blocked");
			}
			check(owner.getCarriedItems().getInventory().countId(family[1])==4,"retained material not consumed");
		}
		for(int id:new int[]{ItemId.COW_HIDE.id(),ItemId.UNICORN_HIDE.id(),ItemId.BEAR_HIDE.id(),ItemId.BLACK_UNICORN_HIDE.id()})
			check(!h.server().getEntityHandler().getItemDef(id).isUntradable() && find.invoke(rack,id)!=null,"passive/black unicorn materials retained");
		for(int npcId=0;npcId<900;npcId++) {
			com.openrsc.server.content.DropTable table=h.world().getNpcDrops().getDropTable(npcId);
			if(table==null)continue;
			for(Item item:table.clone(table.getDescription()).invariableItems(owner)) check(item.getCatalogId()<1807 || item.getCatalogId()>1816,"no retired guaranteed hides on any variant");
		}
	}
	private static void bosses(CurrentCombatHarness h,MonsterSlayerData data)throws Exception {
		h.installMonsterSlayerData(data);
		Player p=h.player("boss opts",443,440); MonsterSlayerState.write(p.getCache(),data,seed(data,6,0,null,0));
		MonsterSlayerTaskService service=new MonsterSlayerTaskService(data);
		MonsterSlayerContactService contacts=new MonsterSlayerContactService(data,service,bound->bound-1);
		check(!contacts.previewTask(p,"legends").getFamilyKey().equals("elder_green_dragon"),"default random pool excludes bosses");
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.MINING.id(),79,79,false);
		for(MonsterSlayerBossTasks.Boss boss:MonsterSlayerBossTasks.Boss.values()) {
			check(!boss.optedIn(p) && !MonsterSlayerBossTasks.eligible(p,boss.taskKey()),"default exclusion");
			MonsterSlayerBossTasks.choose(p,data,boss,true);check(!boss.optedIn(p),"inaccessible opt-in denied");
		}
		p.getCache().store("miniquest_dwarf_youth_rescue",true); h.server().getConfig().WANT_CUSTOM_QUESTS=true;
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.MINING.id(),80,80,false);
		for(MonsterSlayerBossTasks.Boss boss:MonsterSlayerBossTasks.Boss.values()) {
			check(!MonsterSlayerBossTasks.eligible(p,boss.taskKey()),"access does not opt in");
			MonsterSlayerBossTasks.choose(p,data,boss,true); check(boss.optedIn(p),"independent persistent opt-in");
			check(contacts.previewTask(p,"legends").getKey().equals(boss.taskKey()),"opted-in boss participates in equal-weight pool");
			check(service.assignRepeatable(p,"legends",boss.taskKey()).isAccepted(),"boss assignment");
			long before=MonsterSlayerState.read(p.getCache(),data).getBalances().get(MonsterSlayerChallenge.HERO);
			check(service.creditEligibleKill(p,boss.npcId).isAccepted(),"one kill completion");
			check(MonsterSlayerState.read(p.getCache(),data).getBalances().get(MonsterSlayerChallenge.HERO)==before+80,"80 Hero points");
			service.assignRepeatable(p,"legends",boss.taskKey());
			MonsterSlayerBossTasks.choose(p,data,boss,false);
			check(!boss.optedIn(p) && MonsterSlayerState.read(p.getCache(),data).getActiveTaskKey()==null,"opt-out cancels matching task");
			check(MonsterSlayerState.read(p.getCache(),data).getBalances().get(MonsterSlayerChallenge.HERO)==before+80,"cancellation no rewards");
		}
		MonsterSlayerBossTasks.choose(p,data,MonsterSlayerBossTasks.Boss.ELDER_DRAGON,true);
		check(contacts.previewTask(p,"legends").getKey().equals(MonsterSlayerBossTasks.Boss.ELDER_DRAGON.taskKey()),"boss preview before access loss");
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.MINING.id(),79,80,false);
		check(!MonsterSlayerBossTasks.eligible(p,MonsterSlayerBossTasks.Boss.ELDER_DRAGON.taskKey()),"assignment rechecks current access");
		boolean denied=false;
		try { service.assignRepeatable(p,"legends",MonsterSlayerBossTasks.Boss.ELDER_DRAGON.taskKey()); } catch(IllegalArgumentException expected){denied=true;}
		check(denied,"direct boss assignment also gated");
		contacts.requestTask(p,"legends");
		MonsterSlayerState.Snapshot selected=MonsterSlayerState.read(p.getCache(),data);
		check(selected.getActiveTaskKey()!=null && !selected.getActiveTaskKey().equals(MonsterSlayerBossTasks.Boss.ELDER_DRAGON.taskKey()),"stale preview cannot assign inaccessible boss");
		MonsterSlayerState.write(p.getCache(),data,MonsterSlayerState.cancelTask(selected,data,selected.getActiveTaskKey()));
		MonsterSlayerBossTasks.choose(p,data,MonsterSlayerBossTasks.Boss.BALROG,true);
		MonsterSlayerBossTasks.choose(p,data,MonsterSlayerBossTasks.Boss.ELDER_DRAGON,false);
		check(MonsterSlayerBossTasks.Boss.BALROG.optedIn(p),"one opt-out preserves the other boss preference");
		service.assignRepeatable(p,"legends","legends.black_dragons.repeatable");
		MonsterSlayerBossTasks.choose(p,data,MonsterSlayerBossTasks.Boss.BALROG,false);
		check("legends.black_dragons.repeatable".equals(MonsterSlayerState.read(p.getCache(),data).getActiveTaskKey()),"unrelated task retained");
		check(data.getTask("legends.king_black_dragon").getPointReward()==60,"mandatory KBD unchanged");
	}
	private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
