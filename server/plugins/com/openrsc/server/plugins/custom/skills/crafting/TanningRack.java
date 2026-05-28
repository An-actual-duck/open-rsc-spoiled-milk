package com.openrsc.server.plugins.custom.skills.crafting;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public final class TanningRack implements OpLocTrigger, UseLocTrigger {

	private static final MaterialProcess[] PROCESSES = {
		new MaterialProcess(ItemId.COW_HIDE.id(), ItemId.LEATHER.id(), 1, 10),
		new MaterialProcess(ItemId.GOBLIN_HIDE.id(), ItemId.GOBLIN_LEATHER.id(), 1, 10),
		new MaterialProcess(ItemId.UNICORN_HIDE.id(), ItemId.UNICORN_LEATHER.id(), 2, 15),
		new MaterialProcess(ItemId.BEAR_HIDE.id(), ItemId.BEAR_LEATHER.id(), 2, 15),
		new MaterialProcess(ItemId.BLACK_UNICORN_HIDE.id(), ItemId.BLACK_UNICORN_LEATHER.id(), 2, 15),
		new MaterialProcess(ItemId.SCORPION_CARAPACE.id(), ItemId.CURED_SCORPION_CARAPACE.id(), 2, 15),
		new MaterialProcess(ItemId.WOLF_HIDE.id(), ItemId.WOLF_LEATHER.id(), 3, 20),
		new MaterialProcess(ItemId.SPIDER_CARAPACE.id(), ItemId.CURED_SPIDER_CARAPACE.id(), 3, 20),
		new MaterialProcess(ItemId.GIANT_HIDE.id(), ItemId.GIANT_LEATHER.id(), 3, 20),
		new MaterialProcess(ItemId.OGRE_HIDE.id(), ItemId.OGRE_LEATHER.id(), 4, 25),
		new MaterialProcess(ItemId.BABY_DRAGON_HIDE.id(), ItemId.BABY_DRAGON_LEATHER.id(), 4, 25),
		new MaterialProcess(ItemId.MAGIC_SPIDER_CARAPACE.id(), ItemId.CURED_MAGIC_SPIDER_CARAPACE.id(), 5, 30),
		new MaterialProcess(ItemId.MOSS_GIANT_HIDE.id(), ItemId.MOSS_GIANT_LEATHER.id(), 5, 30),
		new MaterialProcess(ItemId.ICE_GIANT_HIDE.id(), ItemId.ICE_GIANT_LEATHER.id(), 5, 30),
		new MaterialProcess(ItemId.DEMON_HIDE.id(), ItemId.DEMON_LEATHER.id(), 6, 35),
		new MaterialProcess(ItemId.HELLHOUND_HIDE.id(), ItemId.HELLHOUND_LEATHER.id(), 7, 40),
		new MaterialProcess(ItemId.FIRE_GIANT_HIDE.id(), ItemId.FIRE_GIANT_LEATHER.id(), 7, 40),
		new MaterialProcess(ItemId.BLUE_DRAGON_HIDE.id(), ItemId.BLUE_DRAGON_LEATHER.id(), 7, 40),
		new MaterialProcess(ItemId.DRAGON_HIDE.id(), ItemId.DRAGON_LEATHER.id(), 7, 40),
		new MaterialProcess(ItemId.RED_DRAGON_HIDE.id(), ItemId.RED_DRAGON_LEATHER.id(), 8, 45),
		new MaterialProcess(ItemId.BLACK_DEMON_HIDE.id(), ItemId.BLACK_DEMON_LEATHER.id(), 8, 45),
		new MaterialProcess(ItemId.BLACK_DRAGON_HIDE.id(), ItemId.BLACK_DRAGON_LEATHER.id(), 9, 50),
		new MaterialProcess(ItemId.BALROG_HIDE.id(), ItemId.BALROG_LEATHER.id(), 9, 50),
		new MaterialProcess(ItemId.KING_BLACK_DRAGON_HIDE.id(), ItemId.KING_BLACK_DRAGON_LEATHER.id(), 10, 55)
	};

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!isTanningRack(obj)) {
			return;
		}

		List<MaterialProcess> available = getAvailableProcesses(player);
		if (available.isEmpty()) {
			player.playerServerMessage(MessageType.QUEST, "You have no hides or carapaces ready to tan.");
			return;
		}

		player.playerServerMessage(MessageType.QUEST, "What would you like to tan?");
		String[] options = available.stream()
			.map(process -> player.getWorld().getServer().getEntityHandler().getItemDef(process.inputId).getName())
			.toArray(String[]::new);
		int choice = multi(player, options);
		if (choice < 0 || choice >= available.size()) {
			return;
		}

		beginTanning(player, available.get(choice));
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isTanningRack(obj) && "Tan".equalsIgnoreCase(command);
	}

	@Override
	public void onUseLoc(Player player, GameObject obj, Item item) {
		MaterialProcess process = getProcess(item.getCatalogId());
		if (process == null || !isTanningRack(obj)) {
			return;
		}
		beginTanning(player, process);
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return isTanningRack(obj) && getProcess(item.getCatalogId()) != null;
	}

	private void beginTanning(Player player, MaterialProcess process) {
		if (checkFatigue(player)) {
			return;
		}

		if (getCurrentLevel(player, Skill.CRAFTING.id()) < process.levelRequired) {
			player.playerServerMessage(MessageType.QUEST,
				"You need level " + process.levelRequired + " Crafting to tan that material.");
			return;
		}

		int repeat = player.getConfig().BATCH_PROGRESSION
			? player.getCarriedItems().getInventory().countId(process.inputId, Optional.of(false))
			: 1;
		if (repeat < 1) {
			return;
		}

		player.playerServerMessage(MessageType.QUEST, "You stretch the material across the tanning rack.");
		Item source = new Item(process.inputId);
		thinkbubble(source);
		int completed = 0;
		for (int loop = 0; loop < repeat; loop++) {
			if (player.getCarriedItems().remove(source) == -1) {
				break;
			}
			player.getCarriedItems().getInventory().add(new Item(process.outputId));
			completed++;
		}

		if (completed > 0) {
			player.playerServerMessage(MessageType.QUEST, "You finish preparing " + completed + " piece" + (completed == 1 ? "" : "s") + " into usable material.");
			player.incExp(Skill.CRAFTING.id(), process.experience * completed, true);
		}
	}

	private List<MaterialProcess> getAvailableProcesses(Player player) {
		List<MaterialProcess> available = new ArrayList<>();
		for (MaterialProcess process : PROCESSES) {
			if (player.getCarriedItems().getInventory().countId(process.inputId, Optional.of(false)) > 0) {
				available.add(process);
			}
		}
		return available;
	}

	private MaterialProcess getProcess(int inputId) {
		for (MaterialProcess process : PROCESSES) {
			if (process.inputId == inputId) {
				return process;
			}
		}
		return null;
	}

	private boolean isTanningRack(GameObject obj) {
		return obj.getID() == SceneryId.FRAME.id();
	}

	private boolean checkFatigue(Player player) {
		if (player.getConfig().WANT_FATIGUE
			&& player.getConfig().STOP_SKILLING_FATIGUED >= 2
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			player.message("You are too tired to tan hides");
			return true;
		}
		return false;
	}

	private static int tierLevel(int tier) {
		switch (tier) {
			case 1:
				return 1;
			case 2:
				return 8;
			case 3:
				return 15;
			case 4:
				return 22;
			case 5:
				return 30;
			case 6:
				return 38;
			case 7:
				return 46;
			case 8:
				return 54;
			case 9:
				return 62;
			case 10:
				return 70;
			default:
				return 1;
		}
	}

	private static final class MaterialProcess {
		private final int inputId;
		private final int outputId;
		private final int levelRequired;
		private final int experience;

		private MaterialProcess(int inputId, int outputId, int tier, int experience) {
			this.inputId = inputId;
			this.outputId = outputId;
			this.levelRequired = tierLevel(tier);
			this.experience = experience;
		}
	}
}
