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

import java.math.BigInteger;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Runecraft implements OpLocTrigger, UseLocTrigger {

	private static final int LAW_ROBE_RUNEPRODUCTION_POINTS_PER_RUNE = 10000;
	private static final int LAW_ROBE_RUNEPRODUCTION_POINTS_PER_PERCENT = 100;
	private static final String LAW_ROBE_RUNEPRODUCTION_CACHE_PREFIX = "law_robe_runecraft_bonus_";
	private static final int CHAOS_AMULET_YIELD_POINTS_PER_RUNE = 10000;
	private static final int CHAOS_AMULET_YIELD_POINTS_PER_PERCENT = 100;
	private static final String CHAOS_AMULET_YIELD_CACHE_KEY = "chaos_amulet_weighted_rune_bonus";
	private static final int[] CHAOS_AMULET_BONUS_RUNES = {
		ItemId.MIND_RUNE.id(),
		ItemId.CHAOS_RUNE.id(),
		ItemId.DEATH_RUNE.id(),
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
		return getAltarDef(player, obj) != null && hasRuneStone(player);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		craftRunesAtAltar(player, obj);
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

		craftRunesAtAltar(player, obj);
	}

	private void craftRunesAtAltar(Player player, GameObject obj) {
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
		int processedStoneCount = 0;
		int baseRuneCount = 0;
		final int runeMultiplier = getRuneMultiplier(player, def.getRuneId());
		for (int loop = 0; loop < repeatTimes; ++loop) {
			Item stone = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(ItemId.RUNE_STONE.id(), Optional.of(false)));
			if (stone == null) {
				break;
			}

			if (player.getCarriedItems().remove(stone) == -1) {
				break;
			}
			final int craftedRunes = runeMultiplier;
			player.getCarriedItems().getInventory().add(new Item(def.getRuneId(), craftedRunes));
			baseRuneCount += craftedRunes;
			++processedStoneCount;
		}

		if (processedStoneCount > 0) {
			final int actionExperience = calculateDiminishingActionExperience(
				def.getExp(), processedStoneCount, runeMultiplier);
			player.incExp(Skill.RUNECRAFT.id(), actionExperience, true);
			addLawRobeBonusRunes(player, def.getRuneId(), baseRuneCount);
			addChaosAmuletBonusRunes(player, def.getRuneId(), baseRuneCount);
		}
	}

	private static int calculateDiminishingActionExperience(
			final int configuredExperience,
			final int processedStoneCount,
			final int runeMultiplier
	) {
		if (configuredExperience <= 0 || processedStoneCount <= 0 || runeMultiplier <= 0) {
			return 0;
		}

		final BigInteger baseActionExperience = BigInteger.valueOf(
			(long) configuredExperience * processedStoneCount);
		final BigInteger doubledBaseExperience = baseActionExperience.shiftLeft(1);
		final int denominatorExponent = runeMultiplier - 1;
		final BigInteger roundedExperience;
		if (denominatorExponent >= doubledBaseExperience.bitLength()) {
			// Remaining fractional batches total less than half an internal XP unit.
			roundedExperience = doubledBaseExperience;
		} else {
			final BigInteger denominator = BigInteger.ONE.shiftLeft(denominatorExponent);
			final BigInteger numerator = doubledBaseExperience.multiply(denominator)
				.subtract(baseActionExperience);
			roundedExperience = numerator.add(denominator.shiftRight(1)).divide(denominator);
		}
		return roundedExperience.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
	}

	private void addLawRobeBonusRunes(final Player player, final int runeId, final int baseRuneCount) {
		final int bonusPercent = getLawRobeRunecraftBonusPercent(player);
		if (bonusPercent <= 0 || baseRuneCount <= 0) {
			return;
		}

		final String cacheKey = LAW_ROBE_RUNEPRODUCTION_CACHE_PREFIX + runeId;
		final int storedPoints = player.getCache().hasKey(cacheKey) ? Math.max(0, player.getCache().getInt(cacheKey)) : 0;
		final int earnedPoints = baseRuneCount * bonusPercent * LAW_ROBE_RUNEPRODUCTION_POINTS_PER_PERCENT;
		final int totalPoints = storedPoints + earnedPoints;
		final int bonusRunes = totalPoints / LAW_ROBE_RUNEPRODUCTION_POINTS_PER_RUNE;
		final int remainingPoints = totalPoints % LAW_ROBE_RUNEPRODUCTION_POINTS_PER_RUNE;

		if (remainingPoints > 0) {
			player.getCache().set(cacheKey, remainingPoints);
		} else if (player.getCache().hasKey(cacheKey)) {
			player.getCache().remove(cacheKey);
		}
		if (bonusRunes <= 0) {
			return;
		}
		player.getCarriedItems().getInventory().add(new Item(runeId, bonusRunes));
		player.message("@gre@Your law robes produce " + bonusRunes + " extra rune" + (bonusRunes == 1 ? "." : "s."));
	}

	private int getLawRobeRunecraftBonusPercent(final Player player) {
		return Math.max(0, player.getCarriedItems().getEquipment().getLawRobeTierTotal() * 2);
	}

	private void addChaosAmuletBonusRunes(final Player player, final int runeId, final int baseRuneCount) {
		if (runeId != ItemId.CHAOS_RUNE.id() || baseRuneCount <= 0) {
			return;
		}
		final int bonusPercent = player.getCarriedItems().getEquipment().getChaosAmuletYieldBonusPercent();
		final int[] weights = player.getCarriedItems().getEquipment().getChaosAmuletBonusRuneWeights();
		if (bonusPercent <= 0 || weights.length != CHAOS_AMULET_BONUS_RUNES.length) {
			return;
		}

		final int storedPoints = player.getCache().hasKey(CHAOS_AMULET_YIELD_CACHE_KEY)
			? Math.max(0, player.getCache().getInt(CHAOS_AMULET_YIELD_CACHE_KEY))
			: 0;
		final int earnedPoints = baseRuneCount * bonusPercent * CHAOS_AMULET_YIELD_POINTS_PER_PERCENT;
		final int totalPoints = storedPoints + earnedPoints;
		final int bonusRunes = totalPoints / CHAOS_AMULET_YIELD_POINTS_PER_RUNE;
		final int remainingPoints = totalPoints % CHAOS_AMULET_YIELD_POINTS_PER_RUNE;

		if (remainingPoints > 0) {
			player.getCache().set(CHAOS_AMULET_YIELD_CACHE_KEY, remainingPoints);
		} else if (player.getCache().hasKey(CHAOS_AMULET_YIELD_CACHE_KEY)) {
			player.getCache().remove(CHAOS_AMULET_YIELD_CACHE_KEY);
		}
		if (bonusRunes <= 0) {
			return;
		}

		final int[] bonusCounts = new int[CHAOS_AMULET_BONUS_RUNES.length];
		for (int i = 0; i < bonusRunes; i++) {
			final int bonusRuneIndex = rollChaosAmuletBonusRuneIndex(weights);
			if (bonusRuneIndex >= 0) {
				bonusCounts[bonusRuneIndex]++;
			}
		}
		for (int i = 0; i < bonusCounts.length; i++) {
			if (bonusCounts[i] > 0) {
				player.getCarriedItems().getInventory().add(new Item(CHAOS_AMULET_BONUS_RUNES[i], bonusCounts[i]));
			}
		}
		player.message("@gre@Your chaos Bangle weaves " + bonusRunes + " bonus rune" + (bonusRunes == 1 ? "." : "s."));
	}

	private int rollChaosAmuletBonusRuneIndex(final int[] weights) {
		int totalWeight = 0;
		for (int weight : weights) {
			totalWeight += Math.max(0, weight);
		}
		if (totalWeight <= 0) {
			return -1;
		}
		final int roll = DataConversions.random(1, totalWeight);
		int runningWeight = 0;
		for (int i = 0; i < weights.length; i++) {
			runningWeight += Math.max(0, weights[i]);
			if (roll <= runningWeight) {
				return i;
			}
		}
		return weights.length - 1;
	}

	private boolean hasRuneStone(Player player) {
		return player.getCarriedItems().getInventory().countId(ItemId.RUNE_STONE.id(), Optional.of(false)) > 0;
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
