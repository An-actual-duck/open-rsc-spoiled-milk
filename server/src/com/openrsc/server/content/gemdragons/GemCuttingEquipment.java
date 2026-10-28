package com.openrsc.server.content.gemdragons;

import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;
import com.openrsc.server.model.container.Item;

/** Equipment is sampled at the actual cut resolution, after the batch delay. */
public final class GemCuttingEquipment {
    private GemCuttingEquipment() { }
    public static boolean preventsFailure(Player player) {
        return player.getConfig().WANT_MYWORLD
            && player.getCarriedItems().getEquipment().hasEquipped(MyWorldItemId.GEM_CUTTER_GLOVES);
    }
    public static boolean duplicates(Player player) {
        return player.getConfig().WANT_MYWORLD
            && player.getCarriedItems().getEquipment().hasEquipped(MyWorldItemId.GEM_CUTTERS_MONOCLE)
            && bonusRoll(player.getWorld().getServer().getCombatRandom());
    }
    /** Preserve ordinary one-slot crafting; bonus uses standard owner-ground overflow. */
    public static boolean awardSuccessfulCut(Player player, int cutGemId) {
        player.getCarriedItems().getInventory().add(new Item(cutGemId,1),true);
        boolean bonus=duplicates(player);
        if (bonus) player.getCarriedItems().getInventory().add(new Item(cutGemId,1),true);
        return bonus;
    }
    public static boolean bonusRoll(GameRandom random) { return random.nextInt(100) < 5; }
}
