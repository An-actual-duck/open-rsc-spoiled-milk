package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.content.monsterslayer.BansheeCombat;
import com.openrsc.server.content.monsterslayer.CockatriceCombat;
import com.openrsc.server.content.monsterslayer.DarkBeastCombat;
import com.openrsc.server.content.monsterslayer.GiantFrogCombat;
import com.openrsc.server.content.monsterslayer.TerrorDogCombat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Starter candidates: one full protection item per distinct preparation counter. */
public final class MonsterSlayerTaskSupplies {
	private MonsterSlayerTaskSupplies() { }

	public static List<Integer> itemIds(MonsterSlayerDefinitions.Task task) {
		if (task == null) throw new IllegalArgumentException("A Slayer task is required");
		Set<Integer> items = new LinkedHashSet<Integer>();
		for (MonsterSlayerHazard hazard : task.getHazards()) {
			switch (hazard) {
			case FROG_SPIT:
			case STICKY_FLESH: items.add(GiantFrogCombat.SOLVENT_ITEM_ID); break;
			case BLOOD_DRINKING: items.add(com.openrsc.server.content.monsterslayer.StirgeCombat.BUG_OFF); break;
            case STONY_GLARE: items.add(CockatriceCombat.EYE_DROPS_ID); break;
			case BANSHEE_WAIL: items.add(BansheeCombat.EARPLUGS_ID); break;
			case FEEDING_FRENZY: items.add(TerrorDogCombat.TREATS_ID); break;
			case MARKED_LIGHTNING: items.add(DarkBeastCombat.WIPE_ID); break;
			default: break; // Ordinary hazards and strategy advice do not require a gimmick counter.
			}
		}
		return Collections.unmodifiableList(new ArrayList<Integer>(items));
	}
}
