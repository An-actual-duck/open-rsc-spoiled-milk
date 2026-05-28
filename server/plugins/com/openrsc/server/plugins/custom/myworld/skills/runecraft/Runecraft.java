package com.openrsc.server.plugins.custom.myworld.skills.runecraft;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.external.ObjectRunecraftDef;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Runecraft implements OpLocTrigger, UseLocTrigger {

	int[] RUNES = new int[] {
		ItemId.FIRE_RUNE.id(),
		ItemId.WATER_RUNE.id(),
		ItemId.AIR_RUNE.id(),
		ItemId.EARTH_RUNE.id(),
		ItemId.LIFE_RUNE.id(),
		ItemId.MIND_RUNE.id(),
		ItemId.BODY_RUNE.id(),
		ItemId.CHAOS_RUNE.id(),
		ItemId.COSMIC_RUNE.id(),
		ItemId.NATURE_RUNE.id(),
		ItemId.LAW_RUNE.id(),
		ItemId.DEATH_RUNE.id(),
		ItemId.SOUL_RUNE.id(),
		ItemId.BLOOD_RUNE.id()
	};

	final int AIR_ALTAR = 1190;
	final int MIND_ALTAR = 1192;
	final int WATER_ALTAR = 1194;
	final int EARTH_ALTAR = 1196;
	final int FIRE_ALTAR = 1198;
	final int BODY_ALTAR = 1200;
	final int COSMIC_ALTAR = 1202;
	final int CHAOS_ALTAR = 1204;
	final int NATURE_ALTAR = 1206;
	final int LAW_ALTAR = 1208;
	final int DEATH_ALTAR = 1210;
	final int BLOOD_ALTAR = 1212;
	final int SOUL_ALTAR = 1296;
	final int LIFE_ALTAR = 1321;

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return false;
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return item.getCatalogId() == ItemId.RUNE_STONE.id() && getAltarDef(player, obj) != null;
	}

	@Override
	public void onUseLoc(Player player, GameObject obj, Item item) {
		if (item.getCatalogId() != ItemId.RUNE_STONE.id()) {
			return;
		}

		final ObjectRunecraftDef def = getAltarDef(player, obj);
		if (def == null) {
			return;
		}

		if (player.getSkills().getLevel(Skill.RUNECRAFT.id()) < def.getRequiredLvl()) {
			player.message("You require more skill to use this altar.");
			return;
		}

		int repeatTimes = player.getCarriedItems().getInventory().countId(ItemId.RUNE_STONE.id(), Optional.of(false));
		if (repeatTimes <= 0) {
			player.message("You have no stone to shape.");
			return;
		}

		player.message("You channel the altar's power through the stone.");
		int successCount = 0;
		for (int loop = 0; loop < repeatTimes; ++loop) {
			Item stone = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(ItemId.RUNE_STONE.id(), Optional.of(false)));
			if (stone == null) {
				break;
			}

			player.getCarriedItems().remove(stone);
			player.getCarriedItems().getInventory().add(new Item(def.getRuneId(), getRuneMultiplier(player, def.getRuneId())));
			++successCount;
		}

		if (successCount > 0) {
			player.incExp(Skill.RUNECRAFT.id(), def.getExp() * successCount, true);
		}
	}

	public int getRuneMultiplier(Player player, int runeId) {
		int level = getCurrentLevel(player, Skill.RUNECRAFT.id());
		int requiredLevel = getRequiredLevelForRune(runeId);
		int retVal = 1;

		if (requiredLevel > 0 && level > requiredLevel) {
			retVal += (level - requiredLevel) / 10;
		}

		return retVal;
	}

	private int getRequiredLevelForRune(int runeId) {
		switch (ItemId.getById(runeId)) {
			case AIR_RUNE:
				return 1;
			case WATER_RUNE:
				return 1;
			case EARTH_RUNE:
				return 1;
			case FIRE_RUNE:
				return 1;
			case LIFE_RUNE:
				return 1;
			case MIND_RUNE:
				return 8;
			case BODY_RUNE:
				return 15;
			case CHAOS_RUNE:
				return 22;
			case COSMIC_RUNE:
				return 30;
			case NATURE_RUNE:
				return 38;
			case LAW_RUNE:
				return 46;
			case DEATH_RUNE:
				return 54;
			case SOUL_RUNE:
				return 62;
			case BLOOD_RUNE:
				return 70;
			default:
				return -1;
		}
	}

	private ObjectRunecraftDef getAltarDef(Player player, GameObject obj) {
		ObjectRunecraftDef def = player.getWorld().getServer().getEntityHandler().getObjectRunecraftDef(obj.getID());
		if (def != null) {
			return def;
		}

		switch (obj.getID()) {
			case AIR_ALTAR:
			case MIND_ALTAR:
			case WATER_ALTAR:
			case EARTH_ALTAR:
			case FIRE_ALTAR:
			case BODY_ALTAR:
			case COSMIC_ALTAR:
			case CHAOS_ALTAR:
			case NATURE_ALTAR:
			case LAW_ALTAR:
			case DEATH_ALTAR:
			case BLOOD_ALTAR:
			case SOUL_ALTAR:
				return player.getWorld().getServer().getEntityHandler().getObjectRunecraftDef(obj.getID() + 1);
			default:
				return null;
		}
	}
}
