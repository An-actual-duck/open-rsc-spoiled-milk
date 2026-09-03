package com.openrsc.server.net.rsc;

import com.openrsc.server.Server;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.DataConversions;

import java.lang.reflect.Field;
import java.nio.file.Paths;

/** Exercises capacity derivation with the same class precedence as production. */
public final class WorldBuilderInventoryOverlayTest {
	private WorldBuilderInventoryOverlayTest() { }

	public static void main(String[] arguments) throws Exception {
		String[] overlayClasses = {
			"com.openrsc.server.model.container.BankPreset",
			"com.openrsc.server.model.container.Inventory",
			"com.openrsc.server.net.rsc.handlers.BankHandler",
			"com.openrsc.server.net.rsc.handlers.ItemEquip",
			"com.openrsc.server.net.rsc.handlers.ItemUnequip",
			"com.openrsc.server.net.rsc.handlers.ItemUseOnGroundItem",
			"com.openrsc.server.net.rsc.handlers.ItemUseOnItem",
			"com.openrsc.server.net.rsc.handlers.ItemUseOnNpc",
			"com.openrsc.server.net.rsc.handlers.ItemUseOnObject",
			"com.openrsc.server.net.rsc.handlers.PlayerTradeHandler"
		};
		for (String className : overlayClasses) {
			assertSource(Class.forName(className), "core-gameplay-overlay.jar");
		}
		assertSource(World.class, "world-builder-managed-runtime.jar");

		Server server = new Server("myworld.conf");
		World world = server.getWorld();
		MonsterSlayerData data = MonsterSlayerData.load(
			Paths.get("conf", "server", "defs", "extras", "MonsterSlayer.json"),
			new MonsterSlayerData.ReferenceCatalog() {
				public boolean npcExists(int id) { return true; }
				public boolean npcAttackable(int id) { return true; }
				public boolean npcSpawned(int id) { return true; }
				public boolean itemExists(int id) { return true; }
			});
		Field dataField = World.class.getDeclaredField("monsterSlayerData");
		dataField.setAccessible(true);
		dataField.set(world, data);

		assertCapacity(world, "overlaydevduck", 1, 31);
		assertCapacity(world, "overlayfrankthetank", 63, 40);
		System.out.println("PASS: World Builder runtime preserves 31-slot and 40-slot entitlements");
	}

	private static void assertCapacity(World world, String username, int mask, int expected) {
		Player player = new Player(world, DataConversions.usernameToHash(username));
		player.setClientVersion((short) 10052);
		if (!InventoryCapacityPackets.isSupported(player)) {
			throw new AssertionError(username + " should negotiate expanded inventory packets");
		}
		player.getCache().store("monster_slayer_inventory_upgrades", mask);
		int actual = player.getCarriedItems().getInventory().getCapacity();
		if (actual != expected) {
			throw new AssertionError(username + " capacity: expected " + expected + ", got " + actual);
		}
	}

	private static void assertSource(Class<?> type, String expectedArchive) {
		String source = type.getProtectionDomain().getCodeSource().getLocation().toString();
		if (!source.endsWith(expectedArchive)) {
			throw new AssertionError(type.getName() + " loaded from " + source);
		}
	}
}
