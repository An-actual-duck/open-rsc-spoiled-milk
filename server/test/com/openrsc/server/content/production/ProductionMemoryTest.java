package com.openrsc.server.content.production;

import com.openrsc.server.model.Cache;
import com.openrsc.server.net.rsc.struct.outgoing.ProductionInterfaceStruct;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionMemoryTest {
	@Test
	void preferenceDefaultsOffAndPersistsAsAccountCacheBoolean() {
		Cache cache = new Cache();

		assertFalse(ProductionMemory.isEnabled(cache));
		cache.store(ProductionMemory.PREFERENCE_CACHE_KEY, true);
		assertTrue(ProductionMemory.isEnabled(cache));
	}

	@Test
	void anvilAndFurnaceRoutesRemainIndependentAndContainNoQuantity() {
		Cache cache = new Cache();
		ProductionMemory.storeRoute(cache, "anvil", Arrays.asList(169, 126));
		ProductionMemory.storeRoute(cache, "furnace", Arrays.asList(283, 172, 294));

		assertEquals(Arrays.asList(169, 126), ProductionMemory.loadRoute(cache, "anvil"));
		assertEquals(Arrays.asList(283, 172, 294), ProductionMemory.loadRoute(cache, "furnace"));
		assertEquals(2, ProductionMemory.loadRoute(cache, "anvil").size());
		assertEquals(3, ProductionMemory.loadRoute(cache, "furnace").size());
	}

	@Test
	void corruptOrOversizedSavedRoutesFallBackToNoSelection() {
		Cache cache = new Cache();
		cache.store(ProductionMemory.ROUTE_CACHE_PREFIX + "anvil", "169,not-an-item");
		cache.store(ProductionMemory.ROUTE_CACHE_PREFIX + "furnace", "1,2,3,4,5,6,7,8,9");

		assertEquals(Collections.emptyList(), ProductionMemory.loadRoute(cache, "anvil"));
		assertEquals(Collections.emptyList(), ProductionMemory.loadRoute(cache, "furnace"));
	}

	@Test
	void onlyProductionTypesParticipate() {
		for (int type = ProductionSession.TYPE_SMITHING;
			 type <= ProductionSession.TYPE_FURNACE_MATERIAL; type++) {
			assertTrue(ProductionMemory.isRememberable(session(type, "Production", type)));
		}
		assertFalse(ProductionMemory.isRememberable(session(
			ProductionSession.TYPE_TELEPORT_DESTINATION, "Teleport", 1)));
		assertTrue(ProductionMemory.isRememberable(session(
			ProductionSession.TYPE_RANGERS_REDEMPTION_CATEGORY, "Redeem category", 1)));
		assertTrue(ProductionMemory.isRememberable(session(
			ProductionSession.TYPE_RANGERS_REDEMPTION, "Redeem", 1)));
		assertTrue(ProductionMemory.isRememberable(session(
			ProductionSession.TYPE_MONSTER_SLAYER_REDEMPTION, "Slayer", 1)));
		assertTrue(ProductionMemory.isRememberable(session(
			ProductionSession.TYPE_MONSTER_SLAYER_SHOP_CATEGORY, "Slayer shops", 1)));
	}

	@Test
	void workstationKeysAreStableWhileOtherActivitiesRemainSeparate() {
		assertEquals("anvil", ProductionMemory.activityKey(session(
			ProductionSession.TYPE_SMITHING_MATERIAL, "Choose metal", -1)));
		assertEquals("furnace", ProductionMemory.activityKey(session(
			ProductionSession.TYPE_FURNACE_CATEGORY, "Choose category", -1)));
		assertFalse(ProductionMemory.activityKey(session(
			ProductionSession.TYPE_CRAFTING, "Cut the gem", 160))
			.equals(ProductionMemory.activityKey(session(
				ProductionSession.TYPE_CRAFTING, "Shape the leather", 160))));
	}

	@Test
	void pointShopKeysKeepRangersSeparateAndSlayerPickerStable() {
		ProductionSession rangers = new ProductionSession(ProductionSession.TYPE_RANGERS_REDEMPTION,
			"Redeem", -1, 0, Collections.singletonList(new ProductionRecipe(10, 1, 1, 1, true, true)),
			"rangers-guild-points", null);
		ProductionSession falador = new ProductionSession(ProductionSession.TYPE_MONSTER_SLAYER_REDEMPTION,
			"Slayer", -1, 0, Collections.singletonList(new ProductionRecipe(10, 1, 1, 1, true, true)),
			"monster-slayer-shop:falador", null);
		ProductionSession portSarim = new ProductionSession(ProductionSession.TYPE_MONSTER_SLAYER_REDEMPTION,
			"Slayer", -1, 0, Collections.singletonList(new ProductionRecipe(10, 1, 1, 1, true, true)),
			"monster-slayer-shop:port_sarim", null);
		assertEquals("rangers-guild-points", ProductionMemory.activityKey(rangers));
		assertEquals("monster-slayer-shop:falador", ProductionMemory.activityKey(falador));
		assertFalse(ProductionMemory.activityKey(falador).equals(ProductionMemory.activityKey(portSarim)));
		ProductionSession picker = new ProductionSession(ProductionSession.TYPE_MONSTER_SLAYER_SHOP_CATEGORY,
			"Monster Slayer rewards", -1, 0,
			Collections.singletonList(new ProductionRecipe(0, 1, 1, 1, true, true)),
			"monster-slayer-shops", null);
		assertEquals("monster-slayer-shops", ProductionMemory.activityKey(picker));
		assertTrue(ProductionMemory.isPicker(picker));
	}

	@Test
	void slayerRankPickerRestoresDeepestShopAndBackReturnsToPicker() {
		FakeContext context = new FakeContext();
		ProductionSession picker = singleRecipeSession(
			ProductionSession.TYPE_MONSTER_SLAYER_SHOP_CATEGORY, "Monster Slayer rewards", -1, 5, true, true);
		ProductionSession heroShop = singleRecipeSession(
			ProductionSession.TYPE_MONSTER_SLAYER_REDEMPTION, "Monster Slayer Hero Shop", -1, 3206, true, true);
		ProductionStarter finalStarter = (p, session, itemId, quantity) -> true;
		ProductionStarter pickerStarter = (p, session, itemId, quantity) -> {
			context.setAttribute("production_session", heroShop);
			context.setAttribute("production_starter", finalStarter);
			assertTrue(ProductionMemory.prepareDisplay(context, null, heroShop).isSuppressed());
			return true;
		};
		context.getCache().store(ProductionMemory.PREFERENCE_CACHE_KEY, true);
		ProductionMemory.storeRoute(context.getCache(), ProductionMemory.activityKey(picker),
			Arrays.asList(5, 3206));
		context.setAttribute("production_session", picker);
		context.setAttribute("production_starter", pickerStarter);

		ProductionMemory.Display restored = ProductionMemory.prepareDisplay(context, null, picker);
		assertEquals(heroShop, restored.getSession());
		assertEquals(3206, restored.getSelectedRecipeId());
		assertTrue((restored.getUiFlags() & ProductionMemory.UI_FLAG_CAN_GO_BACK) != 0);
		assertEquals(picker, ProductionMemory.back(context));
		ProductionMemory.Display parent = ProductionMemory.prepareDisplay(context, null, picker);
		assertEquals(picker, parent.getSession());
		assertEquals(5, parent.getSelectedRecipeId());
	}

	@Test
	void generatedCacheKeysRespectTheExistingMysqlLimit() {
		Cache cache = new Cache();
		ProductionSession generic = session(ProductionSession.TYPE_CRAFTING,
			"A deliberately long production activity title", 1234);
		ProductionMemory.storeRoute(cache, ProductionMemory.activityKey(generic), Arrays.asList(100, 200));

		for (String key : cache.getCacheMap().keySet()) {
			assertTrue(key.length() <= 32, key);
		}
	}

	@Test
	void displaySelectionFallsBackSafelyAndQuantityAlwaysStartsAtOne() {
		ProductionSession session = session(ProductionSession.TYPE_SMITHING,
			"Choose an item", 169);

		ProductionInterfaceStruct remembered = ProductionInterfaceStruct.open(session, 200, 7);
		assertEquals(200, remembered.selectedRecipeId);
		assertEquals(1, remembered.selectedQuantity);
		assertEquals(7, remembered.uiFlags);

		ProductionInterfaceStruct unavailable = ProductionInterfaceStruct.open(session, 9999, 3);
		assertEquals(100, unavailable.selectedRecipeId);
		assertEquals(1, unavailable.selectedQuantity);
	}

	@Test
	void rememberedAnvilRouteRestoresDeepestScreenAndBackRestoresParent() {
		FakeContext context = new FakeContext();
		ProductionSession anvil = singleRecipeSession(
			ProductionSession.TYPE_SMITHING_MATERIAL, "Choose metal", -1, 10, true, true);
		ProductionSession itemScreen = singleRecipeSession(
			ProductionSession.TYPE_SMITHING, "Choose item", 10, 20, true, false);
		ProductionStarter finalStarter = (p, session, itemId, quantity) -> true;
		ProductionStarter anvilStarter = (p, session, itemId, quantity) -> {
			context.setAttribute("production_session", itemScreen);
			context.setAttribute("production_starter", finalStarter);
			assertTrue(ProductionMemory.prepareDisplay(context, null, itemScreen).isSuppressed());
			return true;
		};
		context.getCache().store(ProductionMemory.PREFERENCE_CACHE_KEY, true);
		ProductionMemory.storeRoute(context.getCache(), "anvil", Arrays.asList(10, 20));
		context.setAttribute("production_session", anvil);
		context.setAttribute("production_starter", anvilStarter);

		ProductionMemory.Display restored = ProductionMemory.prepareDisplay(context, null, anvil);
		assertEquals(itemScreen, restored.getSession());
		assertEquals(20, restored.getSelectedRecipeId());
		assertEquals(ProductionMemory.UI_FLAG_REMEMBER_SUPPORTED
			| ProductionMemory.UI_FLAG_REMEMBER_ENABLED
			| ProductionMemory.UI_FLAG_CAN_GO_BACK
			| ProductionMemory.UI_FLAG_KEEP_OPEN_SUPPORTED, restored.getUiFlags());

		assertEquals(anvil, ProductionMemory.back(context));
		ProductionMemory.Display parent = ProductionMemory.prepareDisplay(context, null, anvil);
		assertEquals(anvil, parent.getSession());
		assertEquals(10, parent.getSelectedRecipeId());
		assertEquals(0, parent.getUiFlags() & ProductionMemory.UI_FLAG_CAN_GO_BACK);
	}

	@Test
	void unavailablePickerStepStopsAtNearestParentWithoutInvokingStarter() {
		FakeContext context = new FakeContext();
		ProductionSession furnace = singleRecipeSession(
			ProductionSession.TYPE_FURNACE_CATEGORY, "Choose category", -1, 30, true, false);
		AtomicBoolean invoked = new AtomicBoolean(false);
		context.getCache().store(ProductionMemory.PREFERENCE_CACHE_KEY, true);
		ProductionMemory.storeRoute(context.getCache(), "furnace", Arrays.asList(30, 40));
		context.setAttribute("production_session", furnace);
		context.setAttribute("production_starter", (ProductionStarter) (p, session, itemId, quantity) -> {
			invoked.set(true);
			return true;
		});

		ProductionMemory.Display restored = ProductionMemory.prepareDisplay(context, null, furnace);
		assertEquals(furnace, restored.getSession());
		assertEquals(30, restored.getSelectedRecipeId());
		assertFalse(invoked.get());
		assertEquals(0, restored.getUiFlags() & ProductionMemory.UI_FLAG_CAN_GO_BACK);
	}

	@Test
	void noLongerLevelPermittedSelectionFallsBackToSessionDefault() {
		FakeContext context = new FakeContext();
		ProductionSession session = new ProductionSession(ProductionSession.TYPE_CRAFTING,
			"Choose recipe", 70, Arrays.asList(
				new ProductionRecipe(71, 1, 1, 1, true, true),
				new ProductionRecipe(72, 50, 1, 1, false, true)));
		String activityKey = ProductionMemory.activityKey(session);
		context.getCache().store(ProductionMemory.PREFERENCE_CACHE_KEY, true);
		ProductionMemory.storeRoute(context.getCache(), activityKey, Collections.singletonList(72));
		context.setAttribute("production_session", session);
		context.setAttribute("production_starter", (ProductionStarter) (p, s, id, quantity) -> true);

		ProductionMemory.Display restored = ProductionMemory.prepareDisplay(context, null, session);
		assertEquals(71, restored.getSelectedRecipeId());
	}

	@Test
	void onlySuccessfulFinalStartRecordsTheNavigatedRoute() {
		FakeContext context = new FakeContext();
		ProductionSession furnace = singleRecipeSession(
			ProductionSession.TYPE_FURNACE_CATEGORY, "Choose category", -1, 50, true, true);
		ProductionSession recipeScreen = singleRecipeSession(
			ProductionSession.TYPE_CRAFTING, "Choose recipe", 50, 60, true, true);
		ProductionStarter pickerStarter = (p, session, itemId, quantity) -> true;
		ProductionStarter finalStarter = (p, session, itemId, quantity) -> true;
		context.getCache().store(ProductionMemory.PREFERENCE_CACHE_KEY, true);
		context.setAttribute("production_session", furnace);
		context.setAttribute("production_starter", pickerStarter);
		ProductionMemory.prepareDisplay(context, null, furnace);

		ProductionMemory.beginStart(context, furnace, 50);
		context.setAttribute("production_session", recipeScreen);
		context.setAttribute("production_starter", finalStarter);
		ProductionMemory.prepareDisplay(context, null, recipeScreen);
		ProductionMemory.finishStart(context, furnace, 50, true);
		assertEquals(Collections.emptyList(), ProductionMemory.loadRoute(context.getCache(), "furnace"));

		ProductionMemory.beginStart(context, recipeScreen, 60);
		ProductionMemory.finishStart(context, recipeScreen, 60, false);
		assertEquals(Collections.emptyList(), ProductionMemory.loadRoute(context.getCache(), "furnace"));

		ProductionMemory.beginStart(context, recipeScreen, 60);
		ProductionMemory.finishStart(context, recipeScreen, 60, true);
		assertEquals(Arrays.asList(50, 60), ProductionMemory.loadRoute(context.getCache(), "furnace"));
	}

	private ProductionSession session(int type, String title, int inputItemId) {
		return new ProductionSession(type, title, inputItemId, Arrays.asList(
			new ProductionRecipe(100, 1, 1, 1, true, true),
			new ProductionRecipe(200, 5, 2, 1, true, true)));
	}

	private ProductionSession singleRecipeSession(int type, String title, int inputItemId,
		int itemId, boolean levelMet, boolean materialsMet) {
		return new ProductionSession(type, title, inputItemId, Collections.singletonList(
			new ProductionRecipe(itemId, 1, 1, 1, levelMet, materialsMet)));
	}

	private static final class FakeContext implements ProductionMemory.Context {
		private final Cache cache = new Cache();
		private final Map<String, Object> attributes = new HashMap<>();

		@Override
		public Cache getCache() {
			return cache;
		}

		@Override
		public Object getAttribute(String key) {
			return attributes.get(key);
		}

		@Override
		public void setAttribute(String key, Object value) {
			attributes.put(key, value);
		}

		@Override
		public void removeAttribute(String key) {
			attributes.remove(key);
		}
	}
}
