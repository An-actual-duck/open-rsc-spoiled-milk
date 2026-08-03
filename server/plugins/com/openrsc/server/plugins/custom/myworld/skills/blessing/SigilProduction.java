package com.openrsc.server.plugins.custom.myworld.skills.blessing;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.content.cleric.ClericAlignment;
import com.openrsc.server.content.cleric.ClericSigilItemId;
import com.openrsc.server.content.cleric.ClericSigilMaterial;
import com.openrsc.server.content.cleric.ClericSigilProductionCatalog;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.ifinterrupted;
import static com.openrsc.server.plugins.Functions.isbatchcomplete;
import static com.openrsc.server.plugins.Functions.startbatch;
import static com.openrsc.server.plugins.Functions.stopbatch;
import static com.openrsc.server.plugins.Functions.thinkbubble;
import static com.openrsc.server.plugins.Functions.updatebatch;

/** C05 carving and altar conversion for the launch stone/silver sigils. */
public final class SigilProduction implements UseInvTrigger, UseLocTrigger {
	private static final String PRODUCTION_TITLE = "Choose a sigil to carve";

	@Override
	public boolean blockUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		if (!isEnabled(player) || item1 == null || item2 == null
			|| item1.getNoted() || item2.getNoted()) {
			return false;
		}
		return isChiselAndSource(item1.getCatalogId(), item2.getCatalogId())
			|| isChiselAndSource(item2.getCatalogId(), item1.getCatalogId());
	}

	@Override
	public void onUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		if (!blockUseInv(player, invIndex, item1, item2)) {
			return;
		}
		final int sourceItemId = item1.getCatalogId() == ItemId.CHISEL.id()
			? item2.getCatalogId() : item1.getCatalogId();
		final ClericSigilMaterial material;
		try {
			material = ClericSigilProductionCatalog.fromSourceItemId(sourceItemId);
		} catch (IllegalArgumentException ignored) {
			return;
		}
		openCarvingInterface(player, material);
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return isEnabled(player) && obj != null && item != null && !item.getNoted()
			&& PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY()) != null
			&& isUnblessedSigil(item.getCatalogId());
	}

	@Override
	public void onUseLoc(Player player, GameObject obj, Item item) {
		if (!blockUseLoc(player, obj, item)) {
			return;
		}
		final PrayerCatalog.GodLine altarGod = PrayerCatalog.getGodLineForAltar(
			obj.getID(), obj.getX(), obj.getY());
		final ClericSigilItemId unblessed = ClericSigilProductionCatalog.fromUnblessedItemId(
			item.getCatalogId());
		final PrayerCatalog.GodLine chargedGod = getChargedGod(unblessed.getAlignment(), altarGod);
		if (chargedGod == null) {
			player.message("This sigil can only be blessed at an altar of "
				+ formatAlignment(unblessed.getAlignment()) + ".");
			return;
		}
		blessAllMatching(player, unblessed, chargedGod);
	}

	public static boolean beginProductionFromInterface(Player player, ProductionSession session,
			int itemId, int quantity) {
		if (!isEnabled(player) || session == null || !session.isType(ProductionSession.TYPE_CRAFTING)) {
			return false;
		}
		if (quantity < 1) {
			player.message("Choose at least one sigil to carve");
			return false;
		}
		final ClericSigilMaterial material;
		final ClericSigilItemId output;
		try {
			material = ClericSigilProductionCatalog.fromSourceItemId(session.getInputItemId());
			output = ClericSigilProductionCatalog.fromUnblessedItemId(itemId);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
		if (output.getMaterial() != material || session.getRecipeByItemId(itemId) == null) {
			return false;
		}
		if (!canCarve(player, material, true)) {
			return false;
		}
		final int available = player.getCarriedItems().getInventory().countId(
			material.getSourceItemId(), Optional.of(false));
		final int makeCount = Math.min(quantity, available);
		if (makeCount < 1) {
			player.message("You need more material to carve that sigil");
			return false;
		}

		startbatch(player, makeCount);
		new SigilProduction().carveBatch(player, material, output, makeCount);
		return true;
	}

	private void openCarvingInterface(Player player, ClericSigilMaterial material) {
		if (!player.isUsingCustomClient()) {
			player.message("Your client cannot display the sigil crafting interface.");
			return;
		}
		final int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		final boolean materialsMet = hasChisel(player)
			&& player.getCarriedItems().getInventory().countId(
				material.getSourceItemId(), Optional.of(false)) > 0;
		final List<ProductionRecipe> recipes = new ArrayList<ProductionRecipe>();
		for (ClericSigilItemId identity
				: ClericSigilProductionCatalog.getUnblessedIdentities(material)) {
			recipes.add(new ProductionRecipe(
				identity.getItemId(),
				material.getCraftingLevel(),
				1,
				1,
				level >= material.getCraftingLevel(),
				materialsMet));
		}
		final ProductionSession session = new ProductionSession(
			ProductionSession.TYPE_CRAFTING,
			PRODUCTION_TITLE,
			material.getSourceItemId(),
			recipes);
		player.setAttribute("production_session", session);
		player.setAttribute("production_starter", (ProductionStarter) SigilProduction::beginProductionFromInterface);
		ActionSender.showProductionInterface(player, session);
	}

	private void carveBatch(Player player, ClericSigilMaterial material,
			ClericSigilItemId output, int requestedCount) {
		int completed = 0;
		while (!ifinterrupted() && !isbatchcomplete() && completed < requestedCount) {
			if (!canCarve(player, material, completed == 0)) {
				stopbatch();
				break;
			}
			final Inventory inventory = player.getCarriedItems().getInventory();
			final int sourceIndex = inventory.getLastIndexById(
				material.getSourceItemId(), Optional.of(false));
			final Item source = inventory.get(sourceIndex);
			if (source == null) {
				stopbatch();
				break;
			}

			thinkbubble(new Item(ItemId.CHISEL.id()));
			delay(2);
			if (ifinterrupted() || !canCarve(player, material, false)) {
				stopbatch();
				break;
			}
			if (!inventory.replaceExact(source, new Item(output.getItemId(), 1), true)) {
				player.message("The sigil could not be carved; your materials were not changed.");
				stopbatch();
				break;
			}
			player.playSound("chisel");
			player.message("You carve " + getItemName(player, output.getItemId()) + ".");
			player.incExp(
				Skill.CRAFTING.id(),
				ClericSigilProductionCatalog.toInternalExperience(
					material.getBaseCraftingExperience()),
				true);
			completed++;
			updatebatch();
		}
	}

	private void blessAllMatching(Player player, ClericSigilItemId unblessed,
			PrayerCatalog.GodLine chargedGod) {
		final ClericSigilMaterial material = unblessed.getMaterial();
		if (player.getSkills().getLevel(Skill.BLESSING.id()) < material.getBlessingLevel()) {
			player.message("You need a Blessing level of " + material.getBlessingLevel()
				+ " to bless this sigil.");
			return;
		}
		if (isTooFatigued(player, "bless sigils")) {
			return;
		}

		synchronized (player) {
			final Inventory inventory = player.getCarriedItems().getInventory();
			final int inputCount = inventory.countId(unblessed.getItemId(), Optional.of(false));
			if (inputCount <= 0) {
				return;
			}
			final int blessingLevel = player.getSkills().getLevel(Skill.BLESSING.id());
			final int multiplier = ClericSigilProductionCatalog.getOutputMultiplier(
				blessingLevel, material);
			final int outputCount = ClericSigilProductionCatalog.getBlessedOutputCount(
				inputCount, multiplier);
			final ClericSigilItemId blessed = ClericSigilItemId.get(
				material, unblessed.getAlignment(), true);
			final Item sourceItems = new Item(unblessed.getItemId(), inputCount, false);
			final Item outputItems = new Item(blessed.getItemId(), outputCount, false);
			if (!inventory.canReplaceAllCatalogStacked(sourceItems, outputItems)) {
				player.message("You do not have enough inventory capacity to bless those sigils.");
				return;
			}
			if (!Devotion.canSpendDevotionHalfOfferingUnits(player, chargedGod, inputCount)) {
				player.message("You do not have enough devotion to " + formatGodLine(chargedGod)
					+ " to bless all " + inputCount + " sigils.");
				return;
			}
			if (!Devotion.trySpendDevotionHalfOfferingUnits(
					player,
					chargedGod,
					inputCount,
					() -> inventory.replaceAllCatalogStacked(sourceItems, outputItems, true))) {
				player.message("The blessing could not be completed; nothing was changed.");
				return;
			}

			final int blessingExperience = ClericSigilProductionCatalog.getDiminishingInternalExperience(
				material.getBaseBlessingExperience(), inputCount, multiplier);
			player.incExp(Skill.BLESSING.id(), blessingExperience, true);
			player.message("The altar blesses " + inputCount + " "
				+ (inputCount == 1 ? "sigil" : "sigils") + " into " + outputCount + ".");
			player.message("Your devotion to " + formatGodLine(chargedGod) + " is now "
				+ Devotion.formatExactDevotion(player, chargedGod) + ".");
		}
	}

	private static boolean canCarve(Player player, ClericSigilMaterial material,
			boolean sendFailureMessage) {
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < material.getCraftingLevel()) {
			if (sendFailureMessage) {
				player.message("You need a Crafting level of " + material.getCraftingLevel()
					+ " to carve this sigil.");
			}
			return false;
		}
		if (!hasChisel(player)) {
			if (sendFailureMessage) {
				player.message("You need a chisel to carve sigils.");
			}
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(
				material.getSourceItemId(), Optional.of(false)) <= 0) {
			if (sendFailureMessage) {
				player.message("You need more material to carve that sigil.");
			}
			return false;
		}
		return !isTooFatigued(player, "carve sigils");
	}

	private static boolean isTooFatigued(Player player, String action) {
		if (player.getConfig().WANT_FATIGUE
			&& player.getConfig().STOP_SKILLING_FATIGUED >= 2
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			player.message("You are too tired to " + action + ".");
			return true;
		}
		return false;
	}

	private static boolean hasChisel(Player player) {
		return player.getCarriedItems().getInventory().countId(
			ItemId.CHISEL.id(), Optional.of(false)) > 0;
	}

	private static boolean isChiselAndSource(int possibleChisel, int possibleSource) {
		if (possibleChisel != ItemId.CHISEL.id()) {
			return false;
		}
		try {
			ClericSigilProductionCatalog.fromSourceItemId(possibleSource);
			return true;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static boolean isUnblessedSigil(int itemId) {
		try {
			ClericSigilProductionCatalog.fromUnblessedItemId(itemId);
			return true;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static PrayerCatalog.GodLine getChargedGod(ClericAlignment alignment,
			PrayerCatalog.GodLine altarGod) {
		if (alignment == ClericAlignment.NEUTRAL) {
			return altarGod;
		}
		if (alignment == ClericAlignment.SARADOMIN && altarGod == PrayerCatalog.GodLine.SARADOMIN) {
			return altarGod;
		}
		if (alignment == ClericAlignment.GUTHIX && altarGod == PrayerCatalog.GodLine.GUTHIX) {
			return altarGod;
		}
		if (alignment == ClericAlignment.ZAMORAK && altarGod == PrayerCatalog.GodLine.ZAMORAK) {
			return altarGod;
		}
		return null;
	}

	private static boolean isEnabled(Player player) {
		return player != null && player.getConfig().WANT_MYWORLD;
	}

	private static String getItemName(Player player, int itemId) {
		return player.getWorld().getServer().getEntityHandler().getItemDef(itemId).getName().toLowerCase();
	}

	private static String formatAlignment(ClericAlignment alignment) {
		String lower = alignment.getKey();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static String formatGodLine(PrayerCatalog.GodLine godLine) {
		String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
