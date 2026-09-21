package com.openrsc.server.combat;

import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.content.monsterslayer.*;
import com.openrsc.server.content.production.*;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.nio.file.Paths;
import java.util.*;
import java.lang.reflect.Method;
import java.util.concurrent.*;

/** Real inventory, shop definitions, equipment and application-time combat gates. */
public final class SlayerMobilityShieldFixture {
	private static final int SHIELD = MyWorldItemId.SHIELD_OF_MOBILITY, FEATHERS = MyWorldItemId.COCKATRICE_FEATHERS;
	private static final String SHOP = "port_sarim", REWARD = "port_sarim.shield_of_mobility";
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			MonsterSlayerData data = MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"),
				new MonsterSlayerData.ReferenceCatalog() {
					public boolean npcExists(int id) { return true; }
					public boolean npcAttackable(int id) { return true; }
					public boolean npcSpawned(int id) { return true; }
					public boolean itemExists(int id) { return h.server().getEntityHandler().getItemDef(id) != null; }
				});
			h.installMonsterSlayerData(data);
			ItemDefinition def = h.server().getEntityHandler().getItemDef(SHIELD);
			ItemDefinition bronze = h.server().getEntityHandler().getItemDef(124);
			check(def.isWieldable() && !def.isUntradable() && def.getWieldPosition() == 3, "tradable offhand shield");
			check(def.getMeleeDefense() == bronze.getMeleeDefense() && def.getRangedDefense() == bronze.getRangedDefense(), "tier three blocking");
			transactions(h, data);
			CurrentMonsterSlayerShopRuntimeCharacterization.runtimeTransactionsAreAtomic(h);
			combat(h);
			System.out.println("PASS Shield of Mobility: assembly transactions, ingredient protocol, prevention-only protection and solvent isolation");
		}
	}
	private static void transactions(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data);
		Player p = buyer(h, data, "shield buyer", 110);
		Inventory inv = p.getCarriedItems().getInventory();
		inv.add(new Item(FEATHERS, 499));
		check("ingredients".equals(shops.redeem(p, SHOP, REWARD, 1).getReason()), "missing ingredient refused");
		check(balance(p, data) == 110 && count(p, FEATHERS) == 499, "missing materials changes nothing");
		inv.add(new Item(FEATHERS, 1));
		while (inv.size() < inv.getCapacity()) inv.add(new Item(259));
		check(shops.redeem(p, SHOP, REWARD, 1).isSuccessful(), "consumed stack frees output slot in full inventory");
		check(count(p, SHIELD) == 1 && count(p, FEATHERS) == 0 && balance(p, data) == 0, "exact materials and native coins consumed");
		check(MonsterSlayerState.read(p.getCache(), data).getRank() == MonsterSlayerRank.FLEDGLING, "no purchase rank gate");
		check(p.getCarriedItems().getEquipment().ableToEquip(new Item(SHIELD)), "no wear level requirement");
		check(!shops.redeem(p, SHOP, REWARD, 1).isSuccessful(), "duplicate request cannot spend twice");

		Player full = buyer(h, data, "shield full", 220);
		full.getCarriedItems().getInventory().add(new Item(FEATHERS, 501));
		while (full.getCarriedItems().getInventory().size() < full.getCarriedItems().getInventory().getCapacity())
			full.getCarriedItems().getInventory().add(new Item(259));
		Item original = full.getCarriedItems().getInventory().get(0);
		check("inventory".equals(shops.redeem(full, SHOP, REWARD, 1).getReason()), "partial stack cannot free a slot");
		check(original == full.getCarriedItems().getInventory().get(0) && original.getAmount() == 501
			&& balance(full, data) == 220, "inventory rejection preserves identity, amount, order and currency");

		Player poor = buyer(h, data, "shield poor", 109);
		poor.getCarriedItems().getInventory().add(new Item(FEATHERS, 500));
		check("points".equals(shops.redeem(poor, SHOP, REWARD, 1).getReason()) && count(poor, FEATHERS) == 500, "missing coins retains materials");

		Player bulk = buyer(h, data, "shield bulk", 330);
		bulk.getCarriedItems().getInventory().add(new Item(FEATHERS, 1500));
		check(shops.redeem(bulk, SHOP, REWARD, 3).isSuccessful() && count(bulk, SHIELD) == 3
			&& count(bulk, FEATHERS) == 0 && balance(bulk, data) == 0, "batch costs and outputs multiply together");
		for (long quantity : new long[]{0, -1, Long.MAX_VALUE, Integer.MAX_VALUE})
			check(!shops.redeem(bulk, SHOP, REWARD, quantity).isSuccessful(), "invalid/overflow quantity refused");

		for (boolean throwsFailure : new boolean[]{false, true}) {
			Player rollback = buyer(h, data, "shield rollback" + throwsFailure, 110);
			rollback.getCarriedItems().getInventory().add(new Item(FEATHERS, 500));
			Item identity = rollback.getCarriedItems().getInventory().get(0);
			MonsterSlayerShopService broken = new MonsterSlayerShopService(data, (player, id, amount) -> {
				player.getCarriedItems().getInventory().add(new Item(id, amount));
				if (throwsFailure) throw new IllegalStateException("simulated failure after grant");
				return false;
			});
			check("grant".equals(broken.redeem(rollback, SHOP, REWARD, 1).getReason()), "failed grant reported");
			check(count(rollback, SHIELD) == 0 && count(rollback, FEATHERS) == 500 && balance(rollback, data) == 110
				&& identity == rollback.getCarriedItems().getInventory().get(0), "partial grant rolls back items and coins exactly");
		}

		Player concurrent = buyer(h, data, "shield concurrent", 220);
		concurrent.getCarriedItems().getInventory().add(new Item(FEATHERS, 500));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Callable<Boolean> buy = () -> { start.await(); return shops.redeem(concurrent, SHOP, REWARD, 1).isSuccessful(); };
			Future<Boolean> a = executor.submit(buy), b = executor.submit(buy);
			start.countDown();
			check(a.get(10, TimeUnit.SECONDS) != b.get(10, TimeUnit.SECONDS), "only one simultaneous request consumes the ingredients");
			check(count(concurrent, SHIELD) == 1 && balance(concurrent, data) == 110, "no concurrent duplication or overcharge");
		} finally { executor.shutdownNow(); }

		Class<?> plugin = Class.forName("com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerChallengeShops");
		Method sessionBuilder = plugin.getDeclaredMethod("session", Player.class, MonsterSlayerDefinitions.Shop.class,
			MonsterSlayerShopService.class, MonsterSlayerState.Snapshot.class);
		sessionBuilder.setAccessible(true);
		ProductionSession session = (ProductionSession)sessionBuilder.invoke(null, p, data.getShop(SHOP), shops,
			MonsterSlayerState.read(p.getCache(), data));
		ProductionRecipe recipe = session.getRecipes().stream().filter(r -> r.getItemId() == SHIELD).findFirst().get();
		check(Arrays.equals(recipe.getIngredientItemIds(), new int[]{FEATHERS})
			&& Arrays.equals(recipe.getIngredientAmounts(), new int[]{500}), "existing ingredient protocol receives the recipe");
	}
	private static void combat(CurrentCombatHarness h) throws Exception {
		Npc frog = h.npc(863, 440, 440), bird = h.npc(864, 442, 440), demon = h.npc(870, 444, 440);
		Player p = h.player("mobility protected", 441, 440);
		h.equip(p, SHIELD, 1);
		GiantFrogCombat.onSpitImpact(frog, p, 1);
		CockatriceCombat.onMeleeSwing(bird, p, false);
		AbyssalDemonCombat.onDamage(demon, p, 1);
		check(!GiantFrogCombat.attacksBlocked(p) && !CockatriceCombat.attacksBlocked(p)
			&& !CockatriceCombat.movementBlocked(p) && !AbyssalDemonCombat.actionsBlocked(p), "all three new enemy locks prevented");
		check(p.getCurrentPoisonPower() == GiantFrogCombat.POISON_POWER, "frog poison remains");
		GiantFrogCombat.applySolvent(p);
		long before = p.getCache().getLong("slayer_frog_solvent_until");
		AbyssalDemonCombat.onDamage(demon, p, 1);
		check(p.getCache().getLong("slayer_frog_solvent_until") == before, "equipped shield prevents additional solvent drain");
		h.clock().advanceMillis(1000);
		check(p.getCache().getLong("slayer_frog_solvent_until") - h.clock().currentTimeMillis() == 599_000,
			"normal solvent countdown continues");
		h.equip(p, 124, 1);
		AbyssalDemonCombat.onDamage(demon, p, 1);
		check(p.getCache().getLong("slayer_frog_solvent_until") == before - 5000, "ordinary shield does not prevent drain");
		h.equip(p, SHIELD, 1);
		check(!SlayerEquipmentEffects.preventsEnemyImmobilization(p, p), "no accidental PvP immunity");
		Player carried = h.player("shield unequipped", 448, 440);
		carried.getCarriedItems().getInventory().getItems().add(new Item(SHIELD));
		check(!SlayerEquipmentEffects.preventsEnemyImmobilization(demon, carried), "carried but unequipped shield has no effect");

		for (int effect = 0; effect < 3; effect++) {
			Player existing = h.player("existing lock" + effect, 445 + effect, 440);
			if (effect == 0) GiantFrogCombat.onSpitImpact(frog, existing, 1);
			if (effect == 1) CockatriceCombat.onMeleeSwing(bird, existing, false);
			if (effect == 2) AbyssalDemonCombat.onDamage(demon, existing, 1);
			h.equip(existing, SHIELD, 1); // Force equipment state to prove the effect is not retroactively cured.
			check(effect == 0 ? GiantFrogCombat.attacksBlocked(existing) : effect == 1
				? CockatriceCombat.attacksBlocked(existing) && CockatriceCombat.movementBlocked(existing)
				: AbyssalDemonCombat.actionsBlocked(existing), "prevention only, existing lock survives " + effect);
		}
	}
	private static Player buyer(CurrentCombatHarness h, MonsterSlayerData data, String name, long points) {
		Player p = h.player(name, 450, 450);
		p.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		Map<MonsterSlayerChallenge, Long> balances = new EnumMap<>(MonsterSlayerChallenge.class);
		balances.put(MonsterSlayerChallenge.INITIATE, points);
		Map<String, Integer> cursors = new LinkedHashMap<>();
		for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		MonsterSlayerState.write(p.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING,
			MonsterSlayerBalances.of(balances), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		return p;
	}
	private static long balance(Player p, MonsterSlayerData data) { return MonsterSlayerState.read(p.getCache(), data).getBalances().get(MonsterSlayerChallenge.INITIATE); }
	private static int count(Player p, int id) { return p.getCarriedItems().getInventory().countId(id); }
	private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
