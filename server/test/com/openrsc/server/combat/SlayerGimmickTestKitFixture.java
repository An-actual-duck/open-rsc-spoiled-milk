package com.openrsc.server.combat;

import com.openrsc.server.content.monsterslayer.SlayerGimmickTestKit;
import com.openrsc.server.content.monsterslayer.GiantFrogCombat;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Group;

public final class SlayerGimmickTestKitFixture {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			Player player = h.player("slayer kit", 440, 440);
			player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			SlayerGimmickTestKit.grant(player, new String[0]);
			check(player.getCarriedItems().getInventory().size() == 0, "non-admin denied");
			player.setGroupID(Group.ADMIN);
			SlayerGimmickTestKit.grant(player, new String[] {"extra"});
			check(player.getCarriedItems().getInventory().size() == 0, "invalid arguments denied");
			player.getClientLimitations().maxItemId = 1000;
			SlayerGimmickTestKit.grant(player, new String[0]);
			check(player.getCarriedItems().getInventory().size() == 0, "old client denied without dropping supplies");
			player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			h.server().getConfig().WANT_MYWORLD = false;
			SlayerGimmickTestKit.grant(player, new String[0]);
			check(player.getCarriedItems().getInventory().size() == 0, "other worlds denied");
			h.server().getConfig().WANT_MYWORLD = true;
			SlayerGimmickTestKit.grant(player, new String[0]);
			check(player.getCarriedItems().getInventory().countId(3318) == 1, "one full three-dose solvent granted");
			check(player.getCarriedItems().getInventory().countId(3321) == 1, "one full three-use eye drops granted");
			check(player.getCarriedItems().getInventory().countId(3324) == 1, "one three-use wax earplugs granted");
			check(player.getCarriedItems().getInventory().countId(3327) == 1, "one three-use Dog Treats granted");
			check(!GiantFrogCombat.protectedBySolvent(player), "grant does not drink supplies");
			SlayerGimmickTestKit.grant(player, new String[0]);
			check(player.getCarriedItems().getInventory().countId(3318) == 2, "repeat command restocks one bottle");
			check(player.getCarriedItems().getInventory().countId(3321) == 2, "repeat command restocks eye drops");
			check(player.getCarriedItems().getInventory().countId(3324) == 2, "repeat command restocks earplugs");
			check(player.getCarriedItems().getInventory().countId(3327) == 2, "repeat command restocks Dog Treats");
			while (player.getCarriedItems().getInventory().size() < player.getCarriedItems().getInventory().getCapacity()) {
				check(player.getCarriedItems().getInventory().add(new Item(3318)), "fill fixture");
			}
			int before = player.getCarriedItems().getInventory().size();
			SlayerGimmickTestKit.grant(player, new String[0]);
			check(player.getCarriedItems().getInventory().size() == before, "insufficient space grants no partial kit");
			System.out.println("PASS Slayer gimmick kit: authorization, arguments, compatibility, counts, repeat grants, capacity and no auto-use");
		}
	}
	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
