package com.openrsc.server.constants.custom;

import java.util.HashMap;
import java.util.Map;

public enum InterfaceOptions {

	SWAP_CERT(0), // market cert
	SWAP_NOTE(1), // bank cert
	BANK_SWAP(2),
	BANK_INSERT(3),
	INVENTORY_INSERT(4),
	INVENTORY_SWAP(5),
	CANCEL_BATCH(6),
	IRONMAN_MODE(7),
	BANK_PIN(8),
	UNUSED(9), // 9 unused
	AUCTION(10),
	CLAN(11),
	PARTY(12),
	POINTS(13),
	PRODUCTION_SELECT_RECIPE(14),
	PRODUCTION_QUANTITY_DOWN_ONE(15),
	PRODUCTION_QUANTITY_DOWN_FIVE(16),
	PRODUCTION_QUANTITY_UP_ONE(17),
	PRODUCTION_QUANTITY_UP_FIVE(18),
	PRODUCTION_START(19),
	PRODUCTION_CLOSE(20),
	AUTO_CAST_SPELL(21),
	CAST_SUMMON(22);

	private int option;

	private static final Map<Integer, InterfaceOptions> byId = new HashMap<Integer, InterfaceOptions>();

	static {
		for (InterfaceOptions option : InterfaceOptions.values()) {
			if (byId.put(option.id(), option) != null) {
				throw new IllegalArgumentException("duplicate id: " + option.id());
			}
		}
	}

	public static InterfaceOptions getById(Integer id) {
		return byId.getOrDefault(id, null);
	}

	InterfaceOptions(int option) {
		this.option = option;
	}

	public int id() {
		return this.option;
	}
}
