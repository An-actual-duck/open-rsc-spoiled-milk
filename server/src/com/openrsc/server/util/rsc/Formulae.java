package com.openrsc.server.util.rsc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.external.*;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;

import java.security.InvalidParameterException;

import static com.openrsc.server.plugins.Functions.getCurrentLevel;
import static com.openrsc.server.plugins.Functions.getMaxLevel;

public final class Formulae {

		public static final int[] arrowIDs = {ItemId.POISON_DRAGON_ARROWS.id(), ItemId.DRAGON_ARROWS.id(), ItemId.ICE_ARROWS.id(),
			ItemId.POISON_RUNE_ARROWS.id(), ItemId.RUNE_ARROWS.id(), ItemId.ORICHALCUM_ARROWS.id(),
			ItemId.POISON_ADAMANTITE_ARROWS.id(), ItemId.ADAMANTITE_ARROWS.id(), ItemId.TITAN_STEEL_ARROWS.id(),
			ItemId.POISON_MITHRIL_ARROWS.id(), ItemId.MITHRIL_ARROWS.id(), ItemId.POISON_STEEL_ARROWS.id(),
			ItemId.STEEL_ARROWS.id(), ItemId.POISON_IRON_ARROWS.id(), ItemId.IRON_ARROWS.id(),
			ItemId.POISON_BRONZE_ARROWS.id(), ItemId.BRONZE_ARROWS.id(), ItemId.COPPER_ARROWS.id(), ItemId.TIN_ARROWS.id()};
		public static final int[] boltIDs = {ItemId.POISON_DRAGON_BOLTS.id(), ItemId.DRAGON_BOLTS.id(),
			ItemId.POISON_RUNE_BOLTS.id(), ItemId.RUNE_BOLTS.id(), ItemId.POISON_ORICHALCUM_BOLTS.id(),
			ItemId.ORICHALCUM_BOLTS.id(), ItemId.POISON_ADAMANTITE_BOLTS.id(), ItemId.ADAMANTITE_BOLTS.id(),
			ItemId.POISON_TITAN_STEEL_BOLTS.id(), ItemId.TITAN_STEEL_BOLTS.id(), ItemId.POISON_MITHRIL_BOLTS.id(),
			ItemId.MITHRIL_BOLTS.id(), ItemId.POISON_STEEL_BOLTS.id(), ItemId.STEEL_BOLTS.id(),
			ItemId.POISON_IRON_BOLTS.id(), ItemId.IRON_BOLTS.id(), ItemId.OYSTER_PEARL_BOLTS.id(),
			ItemId.POISON_BRONZE_BOLTS.id(), ItemId.BRONZE_BOLTS.id(), ItemId.POISON_COPPER_BOLTS.id(),
			ItemId.COPPER_BOLTS.id(), ItemId.POISON_CROSSBOW_BOLTS.id(), ItemId.CROSSBOW_BOLTS.id()};
	private static final int[] herbDropIDs = {ItemId.UNIDENTIFIED_GUAM_LEAF.id(), ItemId.UNIDENTIFIED_MARRENTILL.id(),
		ItemId.UNIDENTIFIED_TARROMIN.id(), ItemId.UNIDENTIFIED_HARRALANDER.id(), ItemId.UNIDENTIFIED_RANARR_WEED.id(),
		ItemId.UNIDENTIFIED_IRIT_LEAF.id(), ItemId.UNIDENTIFIED_AVANTOE.id(), ItemId.UNIDENTIFIED_KWUARM.id(),
		ItemId.UNIDENTIFIED_CADANTINE.id(), ItemId.UNIDENTIFIED_DWARF_WEED.id()};
	private static final int[] herbDropWeights = {33, 25, 19, 14, 11, 8, 6, 5, 4, 3}; //128
	public static final int[] miningAxeIDs = {ItemId.RUNE_PICKAXE.id(), ItemId.ORICHALCUM_PICKAXE.id(),
		ItemId.ADAMANTITE_PICKAXE.id(), ItemId.TITAN_STEEL_PICKAXE.id(), ItemId.MITHRIL_PICKAXE.id(),
		ItemId.STEEL_PICKAXE.id(), ItemId.IRON_PICKAXE.id(), ItemId.BRONZE_PICKAXE.id(),
		ItemId.COPPER_PICKAXE.id(), ItemId.TIN_PICKAXE.id()};
	public static final int[] miningAxeLvls = {70, 62, 54, 46, 38, 30, 22, 15, 8, 1};
	public static final int[] harvestingShearsIDs = {ItemId.RUNE_SHEARS.id(), ItemId.ORICHALCUM_SHEARS.id(),
		ItemId.ADAMANTITE_SHEARS.id(), ItemId.TITAN_STEEL_SHEARS.id(), ItemId.MITHRIL_SHEARS.id(),
		ItemId.STEEL_SHEARS.id(), ItemId.IRON_SHEARS.id(), ItemId.BRONZE_SHEARS.id(),
		ItemId.COPPER_SHEARS.id(), ItemId.SHEARS.id()};
	public static final int[] harvestingShearsLvls = {70, 62, 54, 46, 38, 30, 22, 15, 8, 1};
	private static final int[] gemDropIDs = {ItemId.NOTHING.id(), ItemId.UNCUT_SAPPHIRE.id(), ItemId.UNCUT_EMERALD.id(),
		ItemId.UNCUT_RUBY.id(), ItemId.UNCUT_DIAMOND.id(), ItemId.LOOP_KEY_HALF.id(), ItemId.TOOTH_KEY_HALF.id(), ItemId.NOTHING_REROLL.id()};
	private static final int[] gemDropWeights = {63, 32, 16, 8, 4, 2, 2, 1}; //128
	private static final int[] rareDropIDs = {ItemId.NOTHING.id(), ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id()};
	private static final int[] rareDropWeights = {124, 4}; //128
		public static final int[] throwingIDs = {ItemId.TIN_THROWING_KNIFE.id(), ItemId.COPPER_THROWING_KNIFE.id(),
			ItemId.BRONZE_THROWING_KNIFE.id(), ItemId.IRON_THROWING_KNIFE.id(), ItemId.STEEL_THROWING_KNIFE.id(),
			ItemId.BLACK_THROWING_KNIFE.id(), ItemId.MITHRIL_THROWING_KNIFE.id(), ItemId.TITAN_STEEL_THROWING_KNIFE.id(),
			ItemId.ADAMANTITE_THROWING_KNIFE.id(), ItemId.ORICHALCUM_THROWING_KNIFE.id(), ItemId.RUNE_THROWING_KNIFE.id(),
			ItemId.POISONED_TIN_THROWING_KNIFE.id(), ItemId.POISONED_COPPER_THROWING_KNIFE.id(),
			ItemId.POISONED_BRONZE_THROWING_KNIFE.id(), ItemId.POISONED_IRON_THROWING_KNIFE.id(),
			ItemId.POISONED_STEEL_THROWING_KNIFE.id(), ItemId.POISONED_BLACK_THROWING_KNIFE.id(),
			ItemId.POISONED_MITHRIL_THROWING_KNIFE.id(), ItemId.POISONED_TITAN_STEEL_THROWING_KNIFE.id(),
			ItemId.POISONED_ADAMANTITE_THROWING_KNIFE.id(), ItemId.POISONED_ORICHALCUM_THROWING_KNIFE.id(),
			ItemId.POISONED_RUNE_THROWING_KNIFE.id(), ItemId.TIN_THROWING_DART.id(), ItemId.COPPER_THROWING_DART.id(),
			ItemId.BRONZE_THROWING_DART.id(), ItemId.IRON_THROWING_DART.id(), ItemId.STEEL_THROWING_DART.id(),
			ItemId.MITHRIL_THROWING_DART.id(), ItemId.TITAN_STEEL_THROWING_DART.id(), ItemId.ADAMANTITE_THROWING_DART.id(),
			ItemId.ORICHALCUM_THROWING_DART.id(), ItemId.RUNE_THROWING_DART.id(), ItemId.POISONED_TIN_THROWING_DART.id(),
			ItemId.POISONED_COPPER_THROWING_DART.id(), ItemId.POISONED_BRONZE_THROWING_DART.id(),
			ItemId.POISONED_IRON_THROWING_DART.id(), ItemId.POISONED_STEEL_THROWING_DART.id(),
			ItemId.POISONED_MITHRIL_THROWING_DART.id(), ItemId.POISONED_TITAN_STEEL_THROWING_DART.id(),
			ItemId.POISONED_ADAMANTITE_THROWING_DART.id(), ItemId.POISONED_ORICHALCUM_THROWING_DART.id(),
			ItemId.POISONED_RUNE_THROWING_DART.id()};

	public static final int[] fishingToolIDs = {ItemId.BLOOD_FISHING_ROD.id(), ItemId.MAGIC_FISHING_ROD.id(),
		ItemId.EBONY_FISHING_ROD.id(), ItemId.YEW_FISHING_ROD.id(), ItemId.MAPLE_FISHING_ROD.id(),
		ItemId.PALM_FISHING_ROD.id(), ItemId.WILLOW_FISHING_ROD.id(), ItemId.OAK_FISHING_ROD.id(),
		ItemId.PINE_FISHING_ROD.id(), ItemId.OILY_FISHING_ROD.id(), ItemId.LOBSTER_POT.id(), ItemId.HARPOON.id(),
		ItemId.FLY_FISHING_ROD.id(), ItemId.BIG_NET.id(), ItemId.FISHING_ROD.id(), ItemId.NET.id()};

	public static int[] unidentifiedHerbs = {
		ItemId.UNIDENTIFIED_GUAM_LEAF.id(),
		ItemId.UNIDENTIFIED_MARRENTILL.id(),
		ItemId.UNIDENTIFIED_TARROMIN.id(),
		ItemId.UNIDENTIFIED_HARRALANDER.id(),
		ItemId.UNIDENTIFIED_RANARR_WEED.id(),
		ItemId.UNIDENTIFIED_IRIT_LEAF.id(),
		ItemId.UNIDENTIFIED_AVANTOE.id(),
		ItemId.UNIDENTIFIED_KWUARM.id(),
		ItemId.UNIDENTIFIED_CADANTINE.id(),
		ItemId.UNIDENTIFIED_DWARF_WEED.id(),
		ItemId.UNIDENTIFIED_TORSTOL.id(),
		ItemId.UNIDENTIFIED_SNAKE_WEED.id(),
		ItemId.UNIDENTIFIED_ARDRIGAL.id(),
		ItemId.UNIDENTIFIED_SITO_FOIL.id(),
		ItemId.UNIDENTIFIED_VOLENCIA_MOSS.id(),
		ItemId.UNIDENTIFIED_ROGUES_PURSE.id()
	};

	/**
	 * Cubic P2P boundaries. MinX, MinY - MaxX, MaxY
	 */
	private static final java.awt.Point[][] F2PWILD_LOCS = {
		{new java.awt.Point(48, 96), new java.awt.Point(335, 142)},
		{new java.awt.Point(144, 190), new java.awt.Point(576, 622)}};
	// 622, 144, 576, 190
	private static final java.awt.Point[][] P2P_LOCS = {{new java.awt.Point(436, 432), new java.awt.Point(719, 906)},
		{new java.awt.Point(48, 96), new java.awt.Point(335, 142)},
		{new java.awt.Point(343, 567), new java.awt.Point(457, 432)},
		{new java.awt.Point(203, 3206), new java.awt.Point(233, 3265)},
		{new java.awt.Point(397, 525), new java.awt.Point(441, 579),},
		{new java.awt.Point(431, 0), new java.awt.Point(1007, 1007)},
		{new java.awt.Point(335, 734), new java.awt.Point(437, 894)}};
	// trawler: 297, 720
	public static final int[] woodcuttingAxeIDs = {ItemId.DRAGON_WOODCUTTING_AXE.id(), ItemId.RUNE_AXE.id(),
		ItemId.ORICHALCUM_AXE.id(), ItemId.ADAMANTITE_AXE.id(), ItemId.TITAN_STEEL_AXE.id(),
		ItemId.MITHRIL_AXE.id(), ItemId.BLACK_AXE.id(), ItemId.STEEL_AXE.id(), ItemId.IRON_AXE.id(),
		ItemId.BRONZE_AXE.id(), ItemId.COPPER_AXE.id(), ItemId.TIN_AXE.id()};
	public static final int[] woodcuttingAxeLvls = {80, 70, 62, 54, 46, 38, 30, 30, 22, 15, 8, 1};

	public static boolean isGatheringTool(int itemId) {
		return DataConversions.inArray(miningAxeIDs, itemId)
			|| DataConversions.inArray(woodcuttingAxeIDs, itemId)
			|| DataConversions.inArray(harvestingShearsIDs, itemId)
			|| DataConversions.inArray(fishingToolIDs, itemId);
	}

	public static int adjustedSideRewardWeightForToolTier(int rewardTier, int toolTier, int baseWeight) {
		if (baseWeight <= 0 || rewardTier > toolTier + 2) {
			return 0;
		}
		if (rewardTier <= toolTier) {
			return baseWeight;
		}
		int divisor = rewardTier == toolTier + 1 ? 2 : 4;
		return Math.max(1, baseWeight / divisor);
	}

	/**
	 * The one and only method for getting face direction
	 *
	 * @param you
	 * @param x
	 * @param y
	 * @return
	 */
	public static int getDirection(Mob you, int x, int y) {
		int deltaX = (you.getX() - x);
		int deltaY = (you.getY() - y);
		if (deltaX < 0) {
			if (deltaY > 0) {
				return 1; // North-West
			}
			if (deltaY == 0) {
				return 2; // West
			}
			return 3; // South-West
		}
		if (deltaX > 0) {
			if (deltaY < 0) {
				return 5; // South-East
			}
			if (deltaY == 0) {
				return 6; // East
			}
			return 7; // North-East
		}

		if (deltaY > 0) {
			return 0; // North
		}
		if (deltaY < 0) {
			return 4; // South
		}

		return -1;
	}

	/**
	 * Decide if the food we are cooking should be burned or not Gauntlets of
	 * Cooking. These gauntlets lowers lvl to burn of lobs, sword and shark
	 */
	public static boolean burnFood(Player player, int foodId, int cookingLevel) {
		//gauntlets of cooking effective on lobsters, swordfish and shark
		//chef: Wearing them means you will burn your lobsters, swordfish and shark less
		final boolean gauntletBonus = player.getCarriedItems().getEquipment().hasEquipped(ItemId.GAUNTLETS_OF_COOKING.id())
			&& player.getCache().getInt("famcrest_gauntlets") == Gauntlets.COOKING.id();
		int bonusLevel = gauntletBonus ? (foodId == ItemId.RAW_SWORDFISH.id() ? 6 :
				foodId == ItemId.RAW_LOBSTER.id() || foodId == ItemId.RAW_SHARK.id() ? 11 : 0) : 0;
		int effectiveLevel = cookingLevel + bonusLevel;
		int levelReq = player.getWorld().getServer().getEntityHandler().getItemCookingDef(foodId).getReqLevel();
		//if not on def file from cooking training table, level stop failing
		//is usually 35 since player can cook item
		int levelStopFail = player.getWorld().getServer().getEntityHandler().getItemPerfectCookingDef(foodId) != null ?
			player.getWorld().getServer().getEntityHandler().getItemPerfectCookingDef(foodId).getReqLevel() : levelReq + 35;
		return !Formulae.calcProductionSuccessfulLegacy(levelReq, effectiveLevel, true, levelStopFail);
	}

	public static boolean goodWine(int cookingLevel) {
		return Formulae.calcProductionSuccessfulLegacy(35, cookingLevel, true, 70);
	}

	private static double addPrayers(Mob source, int prayer1, int prayer2, int prayer3) {
		if (source.isPlayer()) {
			Player sourcePlayer = (Player) source;
			if (sourcePlayer.getPrayers().isPrayerActivated(prayer3)) {
				return 1.15D;
			}
			if (sourcePlayer.getPrayers().isPrayerActivated(prayer2)) {
				return 1.1D;
			}
			if (sourcePlayer.getPrayers().isPrayerActivated(prayer1)) {
				return 1.05D;
			}
		}
		return 0.0D;
	}

	/**
	 * Should the spell cast or fail?
	 */
	public static boolean castSpell(SpellDef def, int magicLevel, int magicEquip) {
		int levelDiff = magicLevel - def.getReqLevel();

		if (magicEquip >= 30 && levelDiff >= 5)
			return true;
		if (magicEquip >= 25 && levelDiff >= 6)
			return true;
		if (magicEquip >= 20 && levelDiff >= 7)
			return true;
		if (magicEquip >= 15 && levelDiff >= 8)
			return true;
		if (magicEquip >= 10 && levelDiff >= 9)
			return true;
		if (levelDiff < 0) {
			return false;
		}
		if (levelDiff >= 10) {
			return true;
		}
		return DataConversions.random(0, (levelDiff + 2) * 2) != 0;
	}

	/**
	 * Calculate how much experience a Mob gives
	 * OG RSC FORMULA vs NPC: Math.floor((2*(att+str+def)+hits)/7) * 2 + 20
	 * OG RSC FORMULA vs Player: Math.floor(combat_level) + 10
	 */
	public static int combatExperience(Mob mob) {
		if (mob.isNpc()) {
			return (mob.getCombatLevel(true) * 2) + 20;
		} else {
			return mob.getCombatLevel(false) + 10;
		}
	}

	/**
	 * Calculate experience done on a per hit & damage made
	 * OG RSC only gave if attacker is on Ranged and Mob was Player
	 * And on RSC era days would have given small amount per successful hit
	 * Best found fit through points: Math.round((27 * damage - 3) / 5.0)
	 * However, RSC+ client show not a constant, doing average of averages
	 * seems fit 16/3 per each 1 damage.
	 *
	 * Since server does not keep track of /3 has to be simulated
	 * by roll.
	 * Value returned is already set as server experience
	 */
	public static int rangedHitExperience(Mob mob, int damageMade) {
		// ranged vs npc is not per hit but per mob kill, see combatExperience
		// except retro rsc where it gave some xp per ranged hit
		if (mob.isNpc() && !mob.getWorld().getServer().getConfig().RANGED_GIVES_XP_HIT) {
			return 0;
		} else {
			int constrainedDmg = Math.min(mob.getSkills().getLevel(Skill.HITS.id()), damageMade);
			int totalXP = 16 * constrainedDmg;
			int baseXP = totalXP / 3;
			int remainder = totalXP % 12;
			int sendXP;
			if (remainder == 0) {
				sendXP = baseXP;
			} else if (remainder <= 6) {
				sendXP = baseXP + (DataConversions.random(0,2) == 0 ? 1 : 0);
			} else {
				sendXP = baseXP + (DataConversions.random(0,2) == 0 ? 0 : 1);
			}
			return sendXP;
		}
	}

	/**
	 * Should the pot crack?
	 */
	public static boolean crackPot(int requiredLvl, int craftingLvl) {
		int levelStopFail = requiredLvl + 8;
		return !Formulae.calcProductionSuccessfulLegacy(requiredLvl, craftingLvl, true, levelStopFail);
	}

	/**
	 * Should the golden item (bowl) break?
	 */
	public static boolean breakGoldenItem(int requiredLvl, int smithingLvl) {
		int levelStopFail = requiredLvl + 30;
		return !Formulae.calcProductionSuccessfulLegacy(requiredLvl, smithingLvl, true, levelStopFail);
	}

	/**
	 * Should the gem be smashed?
	 */
	public static boolean smashGem(int gemId, int requiredLvl, int craftingLvl) {
		int[] SEMIPRECIOUS = {ItemId.UNCUT_OPAL.id(), ItemId.UNCUT_JADE.id(), ItemId.UNCUT_RED_TOPAZ.id()};

		if (!DataConversions.inArray(SEMIPRECIOUS, gemId))
			return false;

		int levelStopFail = requiredLvl + 89;
		return !Formulae.calcProductionSuccessfulLegacy(requiredLvl, craftingLvl, true, levelStopFail);
	}

	/**
	 * Should the web be cut? ~50%
	 */
	public static boolean cutWeb() {
		return DataConversions.random(0, 4) <= 1;
	}

	/**
	 * Decide if we fall off the obstacle or not
	 */
	// TODO: This should be moved to the appropriate plugin class.
	public static boolean failCalculation(final Player player, final int skill, final int reqLevel) {
		final int levelDiff = player.getSkills().getLevel(skill) - reqLevel;
		if (levelDiff < 0) return false;
		if (levelDiff >= 20) return true;
		return DataConversions.random(0, levelDiff + 1) != 0;
	}

	/**
	 * Decide if a gathering skill operation was successful
	 */
	public static boolean calcGatheringSuccessfulLegacy(int levelReq, int skillLevel) {
		return calcGatheringSuccessfulLegacy(levelReq, skillLevel, 0);
	}

	public static boolean calcGatheringSuccessfulLegacy(int levelReq, int skillLevel, int equipmentBonus) {
		int roll = DataConversions.random(1, 128);

		if (skillLevel < levelReq)
			return false;

		// 128 is already guaranteed to fail
		// 1 is already guaranteed to be successful
		// using 127 as the min in order for threshold to not be able to hit 128 for a guaranteed chance to fail
		int threshold = Math.min(127, Math.max(1, skillLevel + equipmentBonus + 40 - (int) (levelReq * 1.5)));
		return roll <= threshold;
	}

	public static int calcGatheringEffectiveLevel(int skillLevel, int toolTier) {
		return skillLevel + Math.max(0, toolTier - 1) * 5;
	}

	public static int calcGatheringYieldMin(int resourceUnlockLevel, int effectiveLevel) {
		int stage = Math.max(0, (effectiveLevel - resourceUnlockLevel) / 10);
		return 1 + (stage / 2);
	}

	public static int calcGatheringYieldMax(int resourceUnlockLevel, int effectiveLevel) {
		int stage = Math.max(0, (effectiveLevel - resourceUnlockLevel) / 10);
		return calcGatheringYieldMin(resourceUnlockLevel, effectiveLevel) + (stage % 2);
	}

	public static int calcGatheringYield(int resourceUnlockLevel, int skillLevel, int toolTier) {
		int effectiveLevel = calcGatheringEffectiveLevel(skillLevel, toolTier);
		int minYield = calcGatheringYieldMin(resourceUnlockLevel, effectiveLevel);
		int maxYield = calcGatheringYieldMax(resourceUnlockLevel, effectiveLevel);
		if (minYield >= maxYield) {
			return minYield;
		}

		int progress = Math.max(0, effectiveLevel - resourceUnlockLevel) % 10;
		return DataConversions.random(1, 10) <= progress + 1 ? maxYield : minYield;
	}

	public static boolean calcProductionSuccessfulLegacy(int levelReq, int skillLevel, boolean stopsFailing, int levelStopFail) {
		return calcProductionSuccessfulLegacy(levelReq, skillLevel, stopsFailing, levelStopFail, 1);
	}

	public static boolean calcProductionSuccessfulLegacy(int levelReq, int skillLevel, boolean stopsFailing, int levelStopFail, int minFailChance) {
		int roll = DataConversions.random(1, 256);

		if (skillLevel < levelReq)
			return false;

		// min chance is 64/256
		// skillLevel is the effective one
		int maxThreshold = stopsFailing ? 256 : 256 - minFailChance;
		int threshold = Math.min(maxThreshold, (int) Math.floor(64 + (skillLevel - 1) * (19200.0D / (levelStopFail * 98))));
		return roll <= threshold;
	}

	/**
	 * Calculates the chance of succeeding at a skilling event
	 * @param low
	 * @param high
	 * @param level
	 * @return percent chance of success
	 */
	public static double interp(double low, double high, int level) {
		// 99 & 98 numbers should *not* be adjusted for level cap > 99
		int value = (int)(Math.floor(low*(99-level)/98) + Math.floor(high*(level-1)/98) + 1);
		return Math.min(Math.max(value / 256D, 0), 1);
	}

	/**
	 * Calculates the chance of outcomes for a skilling event with multiple outcomes (e.g., tuna, swordfish, or nothing)
	 * @param bounds the low, high, and levelReq for each competing outcome result.
	 *               Order matters; highest level events must come first.
	 * @param level the player's level when attempting the skilling success event
	 * @param index the index of the skilling event currently being checked
	 * @return percent chance of success
	 */
	public static double cascadeInterp(SkillSuccessRate[] bounds, int level, int index) {
		double rate = 1D;
		for (int boundsIndex = 0; boundsIndex < bounds.length; boundsIndex++) {
			if (boundsIndex == index) {
				rate = rate * interp(bounds[boundsIndex].lowRate, bounds[boundsIndex].highRate, level);
				return rate;
			}
			if (level >= bounds[boundsIndex].requiredLevel) {
				rate = rate * (1 - interp(bounds[boundsIndex].lowRate, bounds[boundsIndex].highRate, level));
			}
		}
		return 0;
	}

	/**
	 * Calculate a mobs combat level based on their stats
	 */
	public static int getCombatlevel(Mob mob, int[] stats, boolean isSpecial) {
		if (mob.isPlayer() && mob.getConfig().WANT_MYWORLD) {
			int mainCombat = Math.max(stats[Skill.MELEE.id()], Math.max(stats[Skill.RANGED.id()], stats[Skill.MAGIC.id()]));
			int supportAverage = (stats[Skill.SUMMONING.id()] + stats[Skill.PRAYER.id()] + stats[Skill.HITS.id()]) / 3;
			return mainCombat + supportAverage;
		}
		int accountRanged = (mob.getConfig().COMBAT_LEVEL_NON_MELEE_MASK & 0x1);
		int accountMagic = (mob.getConfig().COMBAT_LEVEL_NON_MELEE_MASK & 0x2) >> 1;
		int accountPrayer = (mob.getConfig().COMBAT_LEVEL_NON_MELEE_MASK & 0x4) >> 2;
		int attackStat = mob.isPlayer() ? stats[Skill.MELEE.id()] : stats[Skill.ATTACK.id()];
		int defenseStat = mob.isPlayer() ? stats[Skill.MELEE.id()] : stats[Skill.DEFENSE.id()];
		int strengthStat = mob.isPlayer() ? stats[Skill.MELEE.id()] : stats[Skill.STRENGTH.id()];
		if (mob.getConfig().DIVIDED_GOOD_EVIL) {
			return getCombatLevel(attackStat, defenseStat,
				strengthStat,stats[Skill.HITS.id()],
				(stats[Skill.GOODMAGIC.id()] + stats[Skill.EVILMAGIC.id()]) * accountMagic,
				(stats[Skill.PRAYGOOD.id()] + stats[Skill.PRAYEVIL.id()]) * accountPrayer,
				(stats[Skill.RANGED.id()]) * accountRanged, true, isSpecial);
		} else {
			return getCombatLevel(attackStat, defenseStat,
				strengthStat,stats[Skill.HITS.id()],
				(stats[Skill.MAGIC.id()]) * accountMagic,
				(stats[Skill.PRAYER.id()]) * accountPrayer,
				(stats[Skill.RANGED.id()]) * accountRanged, false, isSpecial);
		}
	}

	/**
	 * Calculate a mobs combat level based on their stats
	 * isSpecial considers hits as half as important in cb level calc
	 * compared to the other melee stats, used in npc xp given
	 */
	public static int getCombatLevel(int att, int def, int str, int hits, int magic, int pray, int range, boolean isCombined, boolean isSpecial) {
		// OG RSC combat level to use with xp calc (for npc): (2 * (att + str + def) + hits) / 7
		// OG RSC combat level to use with xp calc (for player) - seems to be regular well known combat level formula
		int multiplier = isSpecial ? 2 : 1;
		int divider = isCombined ? 2 : 1; // if on good/magic we have to divide by 2 see below
		double attack = multiplier * (att + str);
		double defense = multiplier * def + hits;
		double mage = pray + magic;
		mage /= (8D * divider);
		double ranged = multiplier * range;

		double level;

		if (attack < ranged * 1.5D) {
			level = (isSpecial ? (2 * defense + 3 * ranged) / 14D : (2 * defense + 3 * ranged) / 8D) + mage;
		} else {
			level = (isSpecial ? (attack + defense) / 7D : (attack + defense) / 4D) + mage;
		}

		return (int) Math.floor(level);
	}

	/**
	 * Gets the empty jug ID
	 */
	public static int getEmptyJug(int fullJug) {
		switch (ItemId.getById(fullJug)) {
			case BUCKET_OF_WATER:
				return ItemId.BUCKET.id();
			case JUG_OF_WATER:
				return ItemId.JUG.id();
			case BOWL_OF_WATER:
				return ItemId.BOWL.id();
			default:
				return ItemId.NOTHING.id();
		}
	}

	/**
	 * Chance to cut cacti is 75% success
	 */
	public static boolean cutCacti() { // Check is for FAIL, not SUCCESS.
		return DataConversions.random(0, 100) > 75;
	}

	/**
	 * Check what height we are currently at on the map
	 */
	private static int getHeight(int y) {
		return (int) (y / 944);
	}

	/**
	 * Check what height we are currently at on the map
	 */
	public static int getHeight(Point location) {
		return getHeight(location.getY());
	}

	public static String getLvlDiffColour(int lvlDiff) {
		if (lvlDiff < -9) {
			return "@red@";
		} else if (lvlDiff < -6) {
			return "@or3@";
		} else if (lvlDiff < -3) {
			return "@or2@";
		} else if (lvlDiff < 0) {
			return "@or1@";
		} else if (lvlDiff > 9) {
			return "@gre@";
		} else if (lvlDiff > 6) {
			return "@gr3@";
		} else if (lvlDiff > 3) {
			return "@gr2@";
		} else if (lvlDiff > 0) {
			return "@gr1@";
		}
		return "@whi@";
	}

	public static int getNewY(int currentY, boolean up) {
		int height = getHeight(currentY);
		int newHeight;
		if (up) {
			if (height == 3) { // 3
				newHeight = 0;
			} else if (height >= 2) { // 2
				return currentY;

			} else {
				newHeight = height + 1;
			}
		} else {
			if (height == 0) {
				newHeight = 3; // 3
			} else if (height >= 3) { // 3
				return currentY;
			} else {
				newHeight = height - 1;
			}
		}
		return (newHeight * 944) + (currentY % 944);
	}

	public static boolean isP2P(boolean f2pwildy, Object... objs) {
		int x = -1;
		int y = -1;
		if (objs.length == 1) {
			Object obj = objs[0];
			if (obj instanceof GameObjectLoc) {
				x = ((GameObjectLoc) obj).getX();
				y = ((GameObjectLoc) obj).getY();
			} else if ((obj instanceof ItemLoc)) {
				x = ((ItemLoc) obj).x;
				y = ((ItemLoc) obj).y;
			} else if (obj instanceof NPCLoc) {
				x = ((NPCLoc) obj).startX;
				y = ((NPCLoc) obj).startY;
			}
		} else {
			if (objs[0] instanceof Integer && objs[1] instanceof Integer) {
				x = (Integer) objs[0];
				y = (Integer) objs[1];
			}
		}

		if (x == -1)
			return false;
		if (!f2pwildy) {
			for (java.awt.Point[] p2pLoc : P2P_LOCS) {
				for (int ele = 0; ele < 4; ele++) {
					if (x >= p2pLoc[0].getX() && x <= p2pLoc[1].getX()
						&& y >= p2pLoc[0].getY() + ((ele) * 944) && y <= p2pLoc[1].getY() + ((ele) * 944))
						return true;
				}
			}
		} else {
			for (java.awt.Point[] f2pwildLoc : F2PWILD_LOCS) {
				for (int ele = 0; ele < 4; ele++) {
					if (x >= f2pwildLoc[0].getX() && x <= f2pwildLoc[1].getX()
						&& y >= f2pwildLoc[0].getY() + ((ele) * 944)
						&& y <= f2pwildLoc[1].getY() + ((ele) * 944))
						return true;
				}
			}
		}
		return false;
	}

	public static boolean isF2PLocation(Point location) {
		for (java.awt.Point[] p2pLoc : P2P_LOCS) {
			for (int ele = 0; ele < 4; ele++) {
				if (location.getX() >= p2pLoc[0].getX() && location.getX() <= p2pLoc[1].getX()
					&& location.getY() >= p2pLoc[0].getY() + ((ele) * 944)
					&& location.getY() <= p2pLoc[1].getY() + ((ele) * 944))
					return false;
			}
		}
		for (java.awt.Point[] f2pwildLoc : F2PWILD_LOCS) {
			for (int ele = 0; ele < 4; ele++) {
				if (location.getX() >= f2pwildLoc[0].getX() && location.getX() <= f2pwildLoc[1].getX()
					&& location.getY() >= f2pwildLoc[0].getY() + ((ele) * 944)
					&& location.getY() <= f2pwildLoc[1].getY() + ((ele) * 944)) {
					return true;
				}
			}
		}
		return true;
	}

	public static boolean isGeneralMeat(Item item) {
		return DataConversions.inArray(new int[]{ItemId.COOKEDMEAT.id(), ItemId.RAW_CHICKEN.id(),
			ItemId.RAW_BEAR_MEAT.id(), ItemId.RAW_RAT_MEAT.id(), ItemId.RAW_BEEF.id()}, item.getCatalogId());
	}

	/**
	 * Should the fire light or fail?
	 */
	public static boolean lightLogs(int firemakingLvl) {
		return Formulae.calcProductionSuccessfulLegacy(1, firemakingLvl, true, 60);
	}

	public static boolean lightCustomLogs(FiremakingDef def, int firemakingLvl) {
		int levelReq = def.getRequiredLevel();
		//from normal logs, level stop failing is 60 since start
		int levelStopFail = levelReq + 59;
		return Formulae.calcProductionSuccessfulLegacy(levelReq, firemakingLvl, true, levelStopFail);
	}

	/**
	 * Should getting regular logs succeed? (Retro)
	 * */
	public static boolean chopLogs(int woodcuttingLvl) {
		return Formulae.calcProductionSuccessfulLegacy(1, woodcuttingLvl, true, 60);
	}

	public static int getLevelsToReduceAttackKBD(Player player) {
		int levels = 0;
		int currLvl = getCurrentLevel(player, Skill.RANGED.id());
		int maxLvl = getMaxLevel(player, Skill.RANGED.id());
		int ratio = currLvl * 100 / maxLvl;
		if (currLvl <= 3) {
			return 0;
		}
		if (ratio >= 81)
			levels = (int) (maxLvl * 0.3);
		else if (ratio >= 61)
			levels = (int) (maxLvl * 0.2);
		else if (ratio >= 41)
			levels = (int) (maxLvl * 0.15);
		else if (ratio >= 31)
			levels = (int) (maxLvl * 0.1);
		else if (ratio >= 21)
			levels = (int) (maxLvl * 0.075);
		else if (ratio >= 16)
			levels = (int) (maxLvl * 0.05);
		else if (ratio >= 11)
			levels = (int) (maxLvl * 0.025);
		else
			levels = 1;

		return levels;
	}

	/**
	 * Should the arrow be dropped or disappear
	 */
	public static boolean loseArrow(int damage) {
		return DataConversions.random(0, 6) != 0;
	}

	public static int getRepeatTimes(Player player, int skill) {
		int maxStat = player.getSkills().getMaxStat(skill); // Number of time repeats is based on your highest level using this method
		if (maxStat <= 10)
			return 10;
		if (maxStat <= 19)
			return 12;
		if (maxStat <= 29)
			return 14;
		if (maxStat <= 39)
			return 16;
		if (maxStat <= 49)
			return 20;
		if (maxStat <= 59)
			return 24;
		if (maxStat <= 69)
			return 32;
		if (maxStat <= 79)
			return 40;
		if (maxStat <= 89)
			return 48;
		if (maxStat <= 95)
			return 56;
		if (maxStat <= 99)
			return 64;
		return 1000;
	}

	/**
	 * Must consume equal length lists.
	 **/
	public static int weightedRandomChoice(int[] list, int[] weights) {
		return weightedRandomChoice(list, weights, 0);
	}

	private static int weightedRandomChoice(int[] list, int[] weights, int defaultReturn) throws InvalidParameterException {
		if(list.length != weights.length) {
			throw new InvalidParameterException("weightedRandomChoice ID list and weights must be of equal length");
		}

		int total = 0;
		for (int x : weights)
			total += x;
		int r = DataConversions.random(0, total - 1);
		total = 0;
		for (int i = 0; i < list.length; i++) {
			if (r >= total && r < (total + weights[i])) {
				return list[i];
			}
			total += weights[i];
		}
		return defaultReturn;
	}

	public static int getSplendorBoost(int amount) {
		int boost = amount * 9;
		return Math.min(boost, 1000);
	}

	public static int calculateGemDrop(Player player) throws InvalidParameterException {
		int roll1 = weightedRandomChoice(gemDropIDs, gemDropWeights, ItemId.NOTHING.id());
		if (roll1 != ItemId.NOTHING_REROLL.id())
			return roll1;
		return calculateRareDrop(player);
	}

	public static int calculateRareDrop(Player player) throws InvalidParameterException {
		return weightedRandomChoice(rareDropIDs, rareDropWeights, ItemId.NOTHING.id());
	}

	public static int calculateHerbDrop() throws InvalidParameterException {
		return weightedRandomChoice(herbDropIDs, herbDropWeights, ItemId.UNIDENTIFIED_GUAM_LEAF.id());
	}

	public static boolean isUnidHerb(Item item) {
		return DataConversions.inArray(unidentifiedHerbs, item.getCatalogId());
	}

}
