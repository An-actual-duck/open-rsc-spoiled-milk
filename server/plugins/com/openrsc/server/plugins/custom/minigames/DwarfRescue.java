package com.openrsc.server.plugins.custom.minigames;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.CarriedItems;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import com.openrsc.server.plugins.triggers.UseInvTrigger;

import static com.openrsc.server.plugins.Functions.getCurrentLevel;
import static com.openrsc.server.plugins.Functions.give;
import static com.openrsc.server.plugins.RuneScript.*;

public class DwarfRescue implements TalkNpcTrigger, OpInvTrigger {
	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (item.getCatalogId() == ItemId.DWARF_SMITHY_NOTE.id()) {
			mes("the note reads....");
			ActionSender.sendBox(player, "How to obtain dragon armour% %"
				+ "Complete Dwarf Youth Rescue to access the lava forge% "
				+ "Repair the lava forge with 100 Black dragon scales and 1,000,000 coins% %"
				+ "Dragon bar:% %"
				+ "1 Dragon metal scrap% "
				+ "6 Dragon sulfur% %"
				+ "Purified Rune Bar:% %"
				+ "1 Runite bar% "
				+ "14 Dragon sulfur% %"
				+ "Dragon plate armour, helms, shields, and weapons can be smithed at a normal anvil% "
				+ "Chipped dragon scales and dragon metal chains are no longer required", true);
		}
	}

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return item.getCatalogId() == ItemId.DWARF_SMITHY_NOTE.id();
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (npc.getID() == NpcId.GRAMAT.id()) {
			int stage = player.getCache().hasKey("miniquest_dwarf_youth_rescue") ? player.getCache().getInt("miniquest_dwarf_youth_rescue") : -1;
			switch (stage) {
				case -1:
					npcsay("what is a dwarf to do", "my son has ignored my warnings", "now he is in danger");
					if (player.getQuestStage(Quests.DWARF_CANNON) == -1) {
						npcsay( ".." + player.getUsername() + "!",
							"maybe you could help us again",
							"my son has wandered into our new construction zone",
							"could you see to his safe return");
						say("where should I look for him");
						npcsay("just inside the mines there is a ladder",
							"he's somewhere down there");
						player.getCache().set("miniquest_dwarf_youth_rescue", 0);
					}
					break;
				case 0:
					npcsay("please hurry", "my son is in danger");
					break;
				case 1:
					npcsay("my son told me how you helped him",
						"i'm eternally grateful",
						"he said you have his teddy");
					if (ifheld(ItemId.TEDDY.id(), 1)) {
						say("i do, and i fixed it");
						mes("You hand over the teddy");
						delay(3);
						player.getCache().set("miniquest_dwarf_youth_rescue", 2);
						remove(ItemId.TEDDY.id(), 1);
						npcsay("yet again you've proven a friend to us",
							"i will talk to our best smithy",
							"he works at the new lava forge deep underground",
							"as our ally you will have access to its power",
							"please take this and read it");
						mes("Gramat hands you a note");
						give(player, ItemId.DWARF_SMITHY_NOTE.id(), 1);
						delay(3);
						npcsay("if you follow the steps on the note",
							"you will be rewarded in combat");
						mes("You have completed the dwarf youth rescue miniquest!");
					} else {
						say("i do, but it's damaged",
							"let me repair it first");
						npcsay("he loves that teddy",
							"and i love him",
							"sew it with some needle and thread",
							"then return to me");
					}
					break;
				case 2:
					npcsay("thank you for rescuing my son",
						"you are a hero among us dwarves");
					break;
			}
		} else if (npc.getID() == NpcId.DWARVEN_SMITHY.id()) {
			int stage = player.getCache().hasKey("miniquest_dwarf_youth_rescue") ? player.getCache().getInt("miniquest_dwarf_youth_rescue") : -1;
			if (stage == 2) {
				if (player.getCache().hasKey("myworld_lava_forge_repaired")
					&& player.getCache().getInt("myworld_lava_forge_repaired") == 1) {
					npcsay("oi " + player.getUsername(),
						"Gramat told me about you",
						"this forge is yours to use",
						"dragon metal scrap and dragon sulfur can be smelted into bars");
				} else {
					npcsay("oi " + player.getUsername(),
						"Gramat told me about you",
						"the forge is damaged but you have permission to repair it",
						"black dragon scales and gold should stabilize its heat");
				}
			} else
				npcsay("this is our reason for digging",
					"it's the latest in dwarven technology",
					"this furnace uses the intense heat of lava",
					"our enemies will suffer from its forgings");
		} else if (npc.getID() == NpcId.DWARVEN_YOUTH.id()) {
			int stage = player.getCache().hasKey("miniquest_dwarf_youth_rescue") ? player.getCache().getInt("miniquest_dwarf_youth_rescue") : -1;
			if (stage < 1) {
				if (ifheld(ItemId.TEDDY_HEAD.id(), 1)
					&& ifheld(ItemId.TEDDY_BOTTOM.id(), 1)) {
					npcsay("have you found teddy?");
					say("well.. yes?");
					npcsay("teddy! i'm so happy!",
						"let me see him!");
					say("it's too dangerous here",
						"let's go back first");
					npcsay("ok. i have extra runes",
						"please give teddy to my father");
					player.teleport(271, 3339, true);
					say("i'd better repair this",
						"i bet i could sew it",
						"with a needle and some thread");
					player.getCache().set("miniquest_dwarf_youth_rescue",1);
				} else {
					npcsay("please help me",
						"i want to return to father",
						"but I've lost my teddy",
						"i can't leave him behind");
				}
			}
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return npc.getID() == NpcId.GRAMAT.id() || npc.getID() == NpcId.DWARVEN_SMITHY.id() || npc.getID() == NpcId.DWARVEN_YOUTH.id();
	}
}
