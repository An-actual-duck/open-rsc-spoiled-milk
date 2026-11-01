package com.openrsc.server.plugins.custom.myworld.skills.blessing;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.plugins.authentic.skills.smithing.ShiloFurnaceAccess;
import com.openrsc.server.content.cleric.ClericMetalSigils;
import com.openrsc.server.content.cleric.ClericSigilMaterial;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.openrsc.server.plugins.Functions.*;

/** Normal furnace moulding, followed by the separate chisel/carving and charging steps. */
public final class SigilMoulding {
    private static final ClericSigilMaterial[] METALS = {ClericSigilMaterial.SILVER, ClericSigilMaterial.MOONSILVER};
    private SigilMoulding() {}

    public static void rememberFurnace(Player player, GameObject object) {
        if (object != null && (object.getID() == SceneryId.FURNACE.id() || object.getID() == SceneryId.FURNACE_UNDERGROUND_PASS.id()))
            player.setAttribute("sigil_mould_furnace", object);
    }

    public static boolean openInterface(Player player) {
        if (!player.getConfig().WANT_MYWORLD || !player.isUsingCustomClient() || !atFurnace(player)) return false;
        List<ProductionRecipe> recipes = new ArrayList<ProductionRecipe>();
        for (ClericSigilMaterial material : METALS) {
            int bar = ClericMetalSigils.barFor(material);
            recipes.add(new ProductionRecipe(material.getSourceItemId(), material.getCraftingLevel(), 1,
                ClericMetalSigils.BLANKS_PER_BAR, player.getSkills().getLevel(Skill.CRAFTING.id()) >= material.getCraftingLevel(),
                hasMould(player) && player.getCarriedItems().getInventory().countId(bar, Optional.of(false)) > 0,
                new int[]{bar}, new int[]{-1}, new int[]{1}));
        }
        ProductionSession session = new ProductionSession(ProductionSession.TYPE_CRAFTING,
            "Choose blank sigils to mould", ClericMetalSigils.SIGIL_MOULD, recipes);
        player.setAttribute("production_session", session);
        player.setAttribute("production_starter", (ProductionStarter) SigilMoulding::beginProductionFromInterface);
        ActionSender.showProductionInterface(player, session);
        return true;
    }

    public static boolean beginProductionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
        if (!player.getConfig().WANT_MYWORLD || session == null || !session.isType(ProductionSession.TYPE_CRAFTING)
            || session != player.getAttribute("production_session", null)
            || session == player.getAttribute("sigil_mould_used_session", null)
            || session.getInputItemId() != ClericMetalSigils.SIGIL_MOULD || session.getRecipeByItemId(itemId) == null
            || quantity < 1) return false;
        ClericSigilMaterial material = materialForBlank(itemId);
        if (material == null || !canMould(player, material, true)) return false;
        int count = Math.min(quantity, player.getCarriedItems().getInventory().countId(ClericMetalSigils.barFor(material), Optional.of(false)));
        player.setAttribute("sigil_mould_used_session", session);
        startbatch(player, count);
        while (!ifinterrupted() && !isbatchcomplete()) {
            if (!canMould(player, material, true)) { stopbatch(); break; }
            ActionSender.sendActionProgressBar(player, material.getSourceItemId(), 3);
            delay(3);
            if (ifinterrupted() || !mouldOne(player, material)) { stopbatch(); break; }
            player.incExp(Skill.CRAFTING.id(), material == ClericSigilMaterial.SILVER ? 10 : 15, true);
            player.message("You pour the metal into the mould and make three blank sigils");
            updatebatch();
        }
        return true;
    }

    /** Final admission and exact-instance conversion share the inventory lock. */
    public static boolean mouldOne(Player player, ClericSigilMaterial material) {
        if (material == null || material == ClericSigilMaterial.STONE) return false;
        Inventory inventory = player.getCarriedItems().getInventory();
        synchronized (inventory.getItems()) {
            if (!canMould(player, material, false)) return false;
            int index = inventory.getLastIndexById(ClericMetalSigils.barFor(material), Optional.of(false));
            Item bar = inventory.get(index);
            return inventory.replaceExactWithCopies(bar, material.getSourceItemId(), ClericMetalSigils.BLANKS_PER_BAR, true);
        }
    }

    private static boolean canMould(Player player, ClericSigilMaterial material, boolean explain) {
        Inventory inventory = player.getCarriedItems().getInventory();
        String failure = null;
        if (!player.getConfig().WANT_MYWORLD) return false;
        if (!atFurnace(player)) failure = "Move back to the furnace to mould sigils";
        else if (player.getSkills().getLevel(Skill.CRAFTING.id()) < material.getCraftingLevel())
            failure = "You need level " + material.getCraftingLevel() + " Crafting to mould these sigils";
        else if (!hasMould(player)) failure = "You need a sigil mould to make blank sigils";
        else if (inventory.countId(ClericMetalSigils.barFor(material), Optional.of(false)) < 1) failure = "You need an unnoted metal bar to make blank sigils";
        else if (player.getConfig().WANT_FATIGUE && player.getConfig().STOP_SKILLING_FATIGUED >= 2 && player.getFatigue() >= player.MAX_FATIGUE)
            failure = "You are too tired to mould sigils";
        else if (player.getClientLimitations().maxItemId < material.getSourceItemId()
            || (player.getConfig().RESTRICT_ITEM_ID >= 0 && player.getConfig().RESTRICT_ITEM_ID < material.getSourceItemId()))
            failure = "Your current world or client cannot receive these blank sigils";
        else if (!inventory.canHold(new Item(material.getSourceItemId(), ClericMetalSigils.BLANKS_PER_BAR), 1))
            failure = "You need two free inventory spaces to make three blank sigils";
        if (failure != null && explain) player.message(failure);
        return failure == null;
    }

    private static boolean atFurnace(Player player) {
        GameObject furnace = player.getAttribute("sigil_mould_furnace", null);
        return furnace != null && !furnace.isRemoved() && furnace.getWorld() == player.getWorld()
            && (furnace.getID() == SceneryId.FURNACE.id() || furnace.getID() == SceneryId.FURNACE_UNDERGROUND_PASS.id())
            && (player.withinRange(furnace, 1) || player.withinRange90Deg(furnace, 2))
            && (!ShiloFurnaceAccess.isShiloFurnace(furnace) || ShiloFurnaceAccess.isUnlocked(player));
    }

    private static boolean hasMould(Player player) {
        return player.getCarriedItems().getInventory().countId(ClericMetalSigils.SIGIL_MOULD, Optional.of(false)) > 0;
    }

    private static ClericSigilMaterial materialForBlank(int itemId) {
        for (ClericSigilMaterial material : METALS) if (material.getSourceItemId() == itemId) return material;
        return null;
    }
}
