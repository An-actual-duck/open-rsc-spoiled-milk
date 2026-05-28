package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class BadLuckMitigation {
	private static final String CACHE_KEY_PREFIX = "bl_";
	
	private final HashMap<String, HashMap<Integer, Integer>> mitigationTable;

	public BadLuckMitigation() {
		mitigationTable = new HashMap<String, HashMap<Integer, Integer>>();
	}

	public void addItem(String dropTableId, int itemId, HashMap<Integer, Integer> rollModifiers) {
		mitigationTable.put(dropTableId + "_" + itemId, rollModifiers);
	}

	public boolean shouldMitigateBadLuck(String dropTableId, int itemId) {
		return mitigationTable.containsKey(dropTableId + "_" + itemId);
	}

	public int getRollModifier(Player player, String dropTableId, int itemId) {
		if (!shouldMitigateBadLuck(dropTableId, itemId)) {
			return 0;
		}

		String tableKey = dropTableId + "_" + itemId;

		int kills = getKills(player, tableKey);

		HashMap<Integer, Integer> rollModifiers = mitigationTable.get(tableKey);
		for (Map.Entry<Integer, Integer> entry : rollModifiers.entrySet()) {
			if (kills <= entry.getKey()) {
				return entry.getValue();
			}
		}

		return 0;
	}

	public void resetKills(Player player, String dropTableId, int itemId) {
		if (!shouldMitigateBadLuck(dropTableId, itemId)) {
			return;
		}

		String cacheKey = CACHE_KEY_PREFIX + dropTableId + "_" + itemId;
		if (player.getCache().hasKey(cacheKey)) {
			player.getCache().remove(cacheKey);
		}
	}

	public void incrementKills(Player player, String dropTableId, int itemId) {
		if (!shouldMitigateBadLuck(dropTableId, itemId)) {
			return;
		}

		String tableKey = dropTableId + "_" + itemId;
		String cacheKey = CACHE_KEY_PREFIX + tableKey;

		player.getCache().set(cacheKey, getKills(player, tableKey) + 1);
	}

	private int getKills(Player player, String tableKey) {
		String cacheKey = CACHE_KEY_PREFIX + tableKey;
		if (player.getCache().hasKey(cacheKey)) {
			return player.getCache().getInt(cacheKey);
		}

		return 0;
	}
}
