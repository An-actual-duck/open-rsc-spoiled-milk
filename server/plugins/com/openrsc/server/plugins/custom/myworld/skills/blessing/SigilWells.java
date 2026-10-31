package com.openrsc.server.plugins.custom.myworld.skills.blessing;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericSigilItemId;
import com.openrsc.server.content.cleric.ClericSigilMaterial;
import com.openrsc.server.content.cleric.ClericSigilProductionCatalog;
import com.openrsc.server.content.cleric.ClericWellInteraction;
import com.openrsc.server.content.cleric.ClericWellSigil;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import java.util.Optional;

/** Purpose-well production uses the altar's level/yield/XP curve, without a god or Devotion charge. */
public final class SigilWells implements OpLocTrigger, UseLocTrigger {
    private static boolean isWell(Player player, GameObject object) {
        return player != null && player.getConfig().WANT_MYWORLD && object != null
            && ClericWellInteraction.isWell(object.getID());
    }
    @Override public boolean blockOpLoc(Player player, GameObject object, String command) {
        return isWell(player, object) && "Dip sigils".equalsIgnoreCase(command);
    }
    @Override public void onOpLoc(Player player, GameObject object, String command) {
        if (!blockOpLoc(player, object, command)) return;
        synchronized (player) {
            if (!ClericWellInteraction.canInteract(player, object) || SigilProduction.isTooFatigued(player,"infuse sigils")) return;
            Inventory inventory = player.getCarriedItems().getInventory();
            synchronized (inventory.getItems()) {
                boolean found = false, noted = false;
                for (ClericWellSigil sigil : ClericWellSigil.values()) {
                    if (!sigil.acceptsWell(object.getID())) continue;
                    if (inventory.countId(sigil.getRawItemId(), Optional.of(false)) > 0) {
                        found = true;
                        infuse(player, object, sigil);
                    }
                    noted |= inventory.countId(sigil.getRawItemId(), Optional.of(true)) > 0;
                }
                if (!found) player.message(noted ? "Unnote your sigils before dipping them."
                    : "You need uninfused " + ClericWellSigil.Purpose.forWell(object.getID()).getName() + " sigils to dip here.");
            }
        }
    }
    @Override public boolean blockUseLoc(Player player, GameObject object, Item item) {
        return isWell(player, object) && item != null;
    }
    @Override public void onUseLoc(Player player, GameObject object, Item item) {
        if (!blockUseLoc(player, object, item)) return;
        synchronized (player) {
            if (!ClericWellInteraction.canInteract(player, object)) return;
            Inventory inventory = player.getCarriedItems().getInventory();
            synchronized (inventory.getItems()) {
                boolean currentInstance = false;
                for (Item current : inventory.getItems()) if (current == item && current.getAmount() > 0) { currentInstance = true; break; }
                if (!currentInstance) return;
                if (item.getNoted()) { player.message("Unnote your sigils before dipping them."); return; }
                ClericWellSigil sigil = ClericWellSigil.fromAny(item.getCatalogId());
                if (sigil == null) {
                    try {
                        ClericSigilItemId.fromItemId(item.getCatalogId());
                        player.message("God sigils are blessed at their god's altar.");
                    } catch (IllegalArgumentException notSigil) {
                        player.message("Only uninfused Grace or Blight sigils can be dipped here.");
                    }
                    return;
                }
                if (item.getCatalogId() == sigil.getInfusedItemId()) { player.message("That sigil is already infused."); return; }
                if (!sigil.acceptsWell(object.getID())) {
                    player.message(sigil.getPurpose().getName() + " sigils need " + sigil.getPurpose().getDestination() + "."); return;
                }
                infuse(player, object, sigil);
            }
        }
    }
    /** Caller holds player and inventory locks; each material is one exact all-matching exchange. */
    private static void infuse(Player player, GameObject object, ClericWellSigil sigil) {
        if (!ClericWellInteraction.canInteract(player, object) || !sigil.acceptsWell(object.getID())) return;
        ClericSigilMaterial material = sigil.getMaterial();
        int level = player.getSkills().getLevel(Skill.BLESSING.id());
        int multiplier = ClericSigilProductionCatalog.getOutputMultiplier(level, material);
        if (multiplier == 0) { player.message("You need a Blessing level of " + material.getBlessingLevel() + " to infuse " + material.getKey() + " sigils."); return; }
        if (SigilProduction.isTooFatigued(player,"infuse sigils")) return;
        Inventory inventory = player.getCarriedItems().getInventory();
        int inputCount = inventory.countId(sigil.getRawItemId(), Optional.of(false));
        if (inputCount <= 0) return;
        final int outputCount, experience;
        try {
            outputCount = ClericSigilProductionCatalog.getBlessedOutputCount(inputCount, multiplier);
            experience = ClericSigilProductionCatalog.getDiminishingInternalExperience(material.getBaseBlessingExperience(),inputCount,multiplier);
        } catch (ArithmeticException | IllegalArgumentException overflow) {
            player.message("There are too many sigils to infuse safely at once. Split them into a smaller batch."); return;
        }
        Item input = new Item(sigil.getRawItemId(),inputCount,false), output = new Item(sigil.getInfusedItemId(),outputCount,false);
        if (!inventory.canReplaceAllCatalogStacked(input,output)) { player.message("Your inventory cannot hold the infused " + material.getKey() + " sigils."); return; }
        if (!ClericWellInteraction.canInteract(player,object) || !inventory.replaceAllCatalogStacked(input,output,true)) {
            player.message("The infusion could not be completed; your sigils were not changed."); return;
        }
        player.incExp(Skill.BLESSING.id(),experience,true);
        player.message("You infuse " + inputCount + " " + material.getKey() + " " + sigil.getPurpose().getName() + " sigils into " + outputCount + ".");
    }
}
