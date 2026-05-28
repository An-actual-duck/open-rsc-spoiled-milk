package com.openrsc.server.content;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Summoning {
	private static final String MANUAL_SUMMON_KEY = "myworld_manual_summon";
	private static final String ARMOR_SUMMON_KEY = "myworld_armor_summon";
	private static final String SUMMON_OWNER_KEY = "myworld_summon_owner";
	private static final String SUMMON_KIND_KEY = "myworld_summon_kind";
	private static final String SUMMON_SOURCE_KEY = "myworld_summon_source";
	private static final String SUMMON_ABSORB_PERCENT_KEY = "myworld_summon_absorb_percent";
	private static final String SUMMON_DAMAGE_ABSORBING_KEY = "myworld_summon_damage_absorbing";
	private static final String SUMMON_MAX_HIT_KEY = "myworld_summon_max_hit";
	private static final String SUMMON_TRAIT_KEY = "myworld_summon_trait";
	private static final String SUMMON_EXHAUSTED_KEY = "myworld_summon_exhausted";
	private static final String SUMMON_INVULNERABLE_KEY = "myworld_summon_invulnerable";
	private static final String SUMMON_NEXT_ATTACK_TICK_KEY = "myworld_summon_next_attack_tick";
	private static final String SUMMON_PRAYER_BONUS_KEY = "myworld_summon_prayer_bonus";
	private static final String RAT_AWAITING_ITEM_KEY = "myworld_rat_awaiting_item";
	private static final String RAT_NPC_KEY = "myworld_rat_note_npc";
	private static final String CAMEL_AWAITING_ITEM_KEY = "myworld_camel_awaiting_item";
	private static final String CAMEL_NPC_KEY = "myworld_camel_bank_npc";
	private static final String SOURCE_MANUAL = "manual";
	private static final String SOURCE_ARMOR = "armor";
	private static final String KIND_GIANT_SPIDER = "giant_spider";
	private static final String KIND_IMP = "imp";
	private static final String KIND_BEAR = "bear";
	private static final String KIND_UNICORN = "unicorn";
	private static final String KIND_GIANT_BAT = "giant_bat";
	private static final String KIND_RAT = "rat";
	private static final String KIND_ANIMATED_AXE = "animated_axe";
	private static final String KIND_BLACK_UNICORN = "black_unicorn";
	private static final String KIND_GHOST = "ghost";
	private static final String KIND_CAMEL = "camel";
	private static final String KIND_OTHERWORLDLY_BEING = "otherworldly_being";
	private static final String KIND_GREATER_DEMON = "greater_demon";
	private static final String KIND_SPIRIT_WOLF = "spirit_wolf";
	private static final String KIND_SPIRIT_HELLHOUND = "spirit_hellhound";
	private static final String TRAIT_NONE = "";
	private static final String TRAIT_TANK = "tank";
	private static final String TRAIT_EVASIVE = "evasive";
	private static final String TRAIT_RELENTLESS = "relentless";
	private static final String TRAIT_FEAR = "fear";
	private static final String TRAIT_SPELL_ECHO = "spell_echo";
	private static final String TRAIT_HELLFIRE = "hellfire";
	private static final String TRAIT_SPIRIT_HELLFIRE = "spirit_hellfire";
	private static final String ATTACK_STYLE_MELEE = "melee";
	private static final String ATTACK_STYLE_RANGED = "ranged";
	private static final String ATTACK_STYLE_MAGIC = "magic";
	private static final String ATTACK_STYLE_MELEE_MAGIC = "melee_magic";
	private static final int FOLLOW_RADIUS = 1;
	private static final int CATCH_UP_DISTANCE = 6;
	private static final int SUMMON_PROJECTILE_RANGE = 5;
	private static final int SUMMON_ATTACK_DELAY_TICKS = 3;
	private static final int UTILITY_RAT_NPC_ID = 241;
	private static final int NO_DURATION_LIMIT = -1;
	private static final int UNICORN_PRAYER_BONUS = 10;
	private static final int SUPPORT_UPKEEP_MS = 60000;
	private static final int SUPPORT_DURATION_SECONDS = SUPPORT_UPKEEP_MS / 1000;
	private static final int SUMMON_CHARGE_MS = 5000;
	private static final SummonProfile GIANT_SPIDER_PROFILE = combatProfile(
		"Broodling Spider", 1, 15, NpcId.GIANT_SPIDER_LVL8.id(), KIND_GIANT_SPIDER, 2, 10, 1, 24, 30, TRAIT_NONE,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.BODY_RUNE.id(), 1)
	);
	private static final SummonProfile IMP_PROFILE = supportProfile(
		"Mischief Imp", 7, 30, NpcId.IMP.id(), KIND_IMP, 0,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.BODY_RUNE.id(), 1),
		cost(ItemId.ASHES.id(), 1)
	);
	private static final SummonProfile BEAR_PROFILE = combatProfile(
		"Ironhide Bear", 14, 55, NpcId.BEAR_LVL24.id(), KIND_BEAR, 14, 8, 2, 30, 60, TRAIT_TANK,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.BODY_RUNE.id(), 1),
		cost(ItemId.NATURE_RUNE.id(), 1),
		cost(ItemId.BONES.id(), 1)
	);
	private static final SummonProfile UNICORN_PROFILE = supportProfile(
		"Sacred Unicorn", 20, 80, NpcId.UNICORN.id(), KIND_UNICORN, UNICORN_PRAYER_BONUS,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.BODY_RUNE.id(), 1),
		cost(ItemId.COSMIC_RUNE.id(), 1),
		cost(ItemId.BONES.id(), 1)
	);
	private static final SummonProfile GIANT_BAT_PROFILE = combatProfile(
		"Duskwind Bat", 26, 110, NpcId.GIANT_BAT.id(), KIND_GIANT_BAT, 7, 9, 4, 18, 30, TRAIT_EVASIVE,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.AIR_RUNE.id(), 1),
		cost(ItemId.BODY_RUNE.id(), 1),
		cost(ItemId.NATURE_RUNE.id(), 1),
		cost(ItemId.BAT_BONES.id(), 1)
	);
	private static final SummonProfile RAT_PROFILE = utilityProfile(
		"Pack Rat", 33, 145, UTILITY_RAT_NPC_ID, KIND_RAT,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.LAW_RUNE.id(), 2),
		cost(ItemId.BODY_RUNE.id(), 1),
		cost(ItemId.NATURE_RUNE.id(), 1),
		cost(ItemId.BONES.id(), 1)
	);
	private static final SummonProfile ANIMATED_AXE_PROFILE = combatProfile(
		"Bound Battleaxe", 39, 185, NpcId.ANIMATED_AXE.id(), KIND_ANIMATED_AXE, 8, 10, 6, 16, 25, TRAIT_RELENTLESS,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.COSMIC_RUNE.id(), 2),
		cost(ItemId.IRON_BATTLE_AXE.id(), 1)
	);
	private static final SummonProfile BLACK_UNICORN_PROFILE = supportProfile(
		"Mourning Unicorn", 45, 230, NpcId.BLACK_UNICORN.id(), KIND_BLACK_UNICORN, 0,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.BODY_RUNE.id(), 1),
		cost(ItemId.COSMIC_RUNE.id(), 1),
		cost(ItemId.BONES.id(), 1)
	);
	private static final SummonProfile GHOST_PROFILE = combatProfile(
		"Restless Shade", 51, 280, NpcId.GHOST.id(), KIND_GHOST, 9, 9, 4, 18, 20, TRAIT_FEAR,
		cost(ItemId.LIFE_RUNE.id(), 2),
		cost(ItemId.COSMIC_RUNE.id(), 3),
		cost(ItemId.SOUL_RUNE.id(), 1),
		cost(ItemId.ASHES.id(), 1)
	);
	private static final SummonProfile CAMEL_PROFILE = utilityProfile(
		"Delivery Camel", 58, 335, NpcId.CAMEL.id(), KIND_CAMEL,
		cost(ItemId.LIFE_RUNE.id(), 1),
		cost(ItemId.BODY_RUNE.id(), 2),
		cost(ItemId.LAW_RUNE.id(), 2),
		cost(ItemId.NATURE_RUNE.id(), 2),
		cost(ItemId.BONES.id(), 1)
	);
	private static final SummonProfile OTHERWORLDLY_BEING_PROFILE = combatProfile(
		"Astral Wraith", 64, 395, NpcId.OTHERWORLDLY_BEING.id(), KIND_OTHERWORLDLY_BEING, 10, 8, 7, 14, 25, TRAIT_SPELL_ECHO,
		cost(ItemId.LIFE_RUNE.id(), 2),
		cost(ItemId.COSMIC_RUNE.id(), 4),
		cost(ItemId.SOUL_RUNE.id(), 1),
		cost(ItemId.BONES.id(), 1)
	);
	private static final SummonProfile GREATER_DEMON_PROFILE = combatProfile(
		"Abyssal Demon", 70, 460, NpcId.GREATER_DEMON.id(), KIND_GREATER_DEMON, 18, 7, 9, 12, 35, TRAIT_HELLFIRE,
		cost(ItemId.LIFE_RUNE.id(), 3),
		cost(ItemId.BLOOD_RUNE.id(), 1),
		cost(ItemId.SOUL_RUNE.id(), 1),
		cost(ItemId.DEMON_ASH.id(), 1)
	);
	private static final SummonProfile SPIRIT_WOLF_PROFILE = armorCombatProfile(
		"Spirit Wolf", NpcId.GREY_WOLF.id(), KIND_SPIRIT_WOLF, 3, TRAIT_NONE
	);
	private static final SummonProfile SPIRIT_HELLHOUND_PROFILE = armorCombatProfile(
		"Spirit Hellhound", NpcId.HELLHOUND.id(), KIND_SPIRIT_HELLHOUND, 6, TRAIT_SPIRIT_HELLFIRE
	);
	private static final SummonProfile[] SUMMON_PROFILES = {
		GIANT_SPIDER_PROFILE, IMP_PROFILE, BEAR_PROFILE, UNICORN_PROFILE,
		GIANT_BAT_PROFILE, RAT_PROFILE, ANIMATED_AXE_PROFILE, BLACK_UNICORN_PROFILE,
		GHOST_PROFILE, CAMEL_PROFILE, OTHERWORLDLY_BEING_PROFILE, GREATER_DEMON_PROFILE
	};
	private static final SummonProfile[] ARMOR_SUMMON_PROFILES = {
		SPIRIT_WOLF_PROFILE, SPIRIT_HELLHOUND_PROFILE
	};

	private Summoning() {
	}

	private static Cost cost(final int itemId, final int amount) {
		return new Cost(itemId, amount);
	}

	private static int internalExperience(final int displayedExperience) {
		return Math.max(0, displayedExperience * 4);
	}

	private static SummonProfile combatProfile(final String name, final int level, final int displayedExperience, final int npcId,
											   final String kind, final int baseHits, final int hitsGrowthInterval,
											   final int baseMaxHit, final int maxHitGrowthInterval,
											   final int absorbPercent, final String trait,
											   final Cost... costs) {
		return new SummonProfile(name, level, displayedExperience, npcId, kind, SummonRole.COMBAT, true, NO_DURATION_LIMIT,
			20, 20, 20, 20, baseHits, baseHits, hitsGrowthInterval, baseMaxHit, maxHitGrowthInterval,
			absorbPercent, 0, true, trait, costs);
	}

	private static SummonProfile supportProfile(final String name, final int level, final int displayedExperience, final int npcId,
												final String kind, final int prayerBonus,
												final Cost... costs) {
		return new SummonProfile(name, level, displayedExperience, npcId, kind, SummonRole.SUPPORT, false, SUPPORT_DURATION_SECONDS,
			1, 1, 1, 1, 1, 1, 0, 0, 0, 0, prayerBonus, false, TRAIT_NONE, costs);
	}

	private static SummonProfile utilityProfile(final String name, final int level, final int displayedExperience, final int npcId,
												final String kind, final Cost... costs) {
		return new SummonProfile(name, level, displayedExperience, npcId, kind, SummonRole.UTILITY, false, 60,
			1, 1, 1, 1, 3, 3, 0, 0, 0, 0, 0, false, TRAIT_NONE, costs);
	}

	private static SummonProfile armorCombatProfile(final String name, final int npcId, final String kind,
													final int maxHit, final String trait) {
		return new SummonProfile(name, 1, 0, npcId, kind, SummonRole.COMBAT, true, NO_DURATION_LIMIT,
			20, 20, 20, 20, 1, 1, 0, maxHit, 0, 0, 0, false, trait);
	}

	public static void summonTestBear(final Player owner) {
		spawnManualSummon(owner, BEAR_PROFILE);
		owner.message("@gre@A summoned bear arrives to assist you.");
	}

	public static void summonTestRat(final Player owner) {
		spawnManualSummon(owner, RAT_PROFILE);
		owner.message("@gre@A summoned pack rat waits for your command.");
	}

	public static void summonTestCamel(final Player owner) {
		spawnManualSummon(owner, CAMEL_PROFILE);
		owner.message("@gre@A summoned camel waits to carry one item to your bank.");
	}

	public static void summonTestUnicorn(final Player owner) {
		spawnManualSummon(owner, UNICORN_PROFILE);
		owner.message("@gre@A summoned unicorn blesses your prayers.");
	}

	public static void castSummon(final Player owner, final int summonIndex) {
		if (summonIndex < 0 || summonIndex >= SUMMON_PROFILES.length) {
			owner.message("That summon is not available.");
			return;
		}
		final SummonProfile profile = SUMMON_PROFILES[summonIndex];
		if (!hasSummoningLevel(owner, profile)) {
			owner.message("You need level " + profile.level + " Summoning to summon a " + profile.name + ".");
			return;
		}
		if (!hasSummonCosts(owner, profile)) {
			owner.message("You do not have the required items to summon a " + profile.name + ".");
			return;
		}
		startSummonCharge(owner, profile);
	}

	private static void startSummonCharge(final Player owner, final SummonProfile profile) {
		final int startingHits = owner.getSkills().getLevel(Skill.HITS.id());
		owner.message("@gre@You begin summoning a " + profile.name + "...");
		owner.getUpdateFlags().setCombatEffect(new CombatEffect(owner, CombatEffect.SUMMON));
		owner.getWorld().getServer().getGameEventHandler().add(new GameTickEvent(
			owner.getWorld(), owner, getChargeTicks(owner), "MyWorld Summon Charge", DuplicationStrategy.ONE_PER_MOB) {
			@Override
			public void run() {
				if (!owner.loggedIn() || owner.isRemoved() || owner.getSkills().getLevel(Skill.HITS.id()) <= 0) {
					clearSummonChargeEffect(owner);
					stop();
					return;
				}
				if (owner.getSkills().getLevel(Skill.HITS.id()) < startingHits) {
					owner.message("@red@Your summoning is interrupted.");
					clearSummonChargeEffect(owner);
					stop();
					return;
				}
				if (!hasSummonCosts(owner, profile)) {
					owner.message("You do not have the required items to summon a " + profile.name + ".");
					clearSummonChargeEffect(owner);
					stop();
					return;
				}
				consumeSummonCosts(owner, profile);
				awardSummoningExperience(owner, profile);
				spawnManualSummon(owner, profile);
				owner.message("@gre@A summoned " + profile.name.toLowerCase() + " appears.");
				stop();
			}
		});
	}

	private static void clearSummonChargeEffect(final Player owner) {
		owner.getUpdateFlags().setCombatEffect(new CombatEffect(owner, CombatEffect.NONE));
	}

	private static boolean hasSummonCosts(final Player owner, final SummonProfile profile) {
		for (Map.Entry<Integer, Integer> entry : profile.costs.entrySet()) {
			final int requiredAmount = getPotentialSummonCostAmount(owner, entry.getKey(), entry.getValue());
			if (owner.getCarriedItems().getInventory().countId(entry.getKey(), Optional.of(false)) < requiredAmount) {
				return false;
			}
		}
		return true;
	}

	private static void awardSummoningExperience(final Player owner, final SummonProfile profile) {
		final int summoning = Skill.SUMMONING.id();
		if (summoning >= 0 && profile.experience > 0) {
			owner.incExp(summoning, profile.experience, true);
		}
	}

	private static boolean hasSummoningLevel(final Player owner, final SummonProfile profile) {
		final int summoning = Skill.SUMMONING.id();
		return summoning < 0 || owner.getSkills().getMaxStat(summoning) >= profile.level;
	}

	private static void consumeSummonCosts(final Player owner, final SummonProfile profile) {
		for (Map.Entry<Integer, Integer> entry : profile.costs.entrySet()) {
			final int requiredAmount = getRequiredSummonCostAmount(owner, entry.getKey(), entry.getValue());
			if (requiredAmount > 0) {
				owner.getCarriedItems().remove(new Item(entry.getKey(), requiredAmount, false), false);
			}
		}
		ActionSender.sendInventory(owner);
	}

	private static int getPotentialSummonCostAmount(final Player owner, final int itemId, final int amount) {
		if (!EnchantingItemEffects.isAltarRune(itemId)) {
			return amount;
		}
		return getRunePreservationChance(owner, itemId) >= 1.0D ? 0 : amount;
	}

	private static int getRequiredSummonCostAmount(final Player owner, final int itemId, final int amount) {
		if (!EnchantingItemEffects.isAltarRune(itemId)) {
			return amount;
		}
		return shouldPreserveRuneCost(owner, itemId) ? 0 : amount;
	}

	private static boolean shouldPreserveRuneCost(final Player owner, final int runeId) {
		final double chance = getRunePreservationChance(owner, runeId);
		return chance > 0.0D && DataConversions.getRandom().nextDouble() < chance;
	}

	private static double getRunePreservationChance(final Player owner, final int runeId) {
		double chance = owner.getCarriedItems().getEquipment().getWoolRobeRunePreservationChance(runeId);
		final Item equippedStaff = getEquippedMainHand(owner);
		if (equippedStaff != null) {
			chance += EnchantingItemEffects.getStaffRunePreservationChance(equippedStaff.getCatalogId(), runeId);
		}
		return Math.min(1.0D, chance);
	}

	private static Item getEquippedMainHand(final Player owner) {
		if (owner.getConfig().WANT_EQUIPMENT_TAB) {
			return owner.getCarriedItems().getEquipment().get(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex());
		}
		synchronized (owner.getCarriedItems().getInventory().getItems()) {
			for (Item item : owner.getCarriedItems().getInventory().getItems()) {
				if (item == null || !item.isWielded()) {
					continue;
				}
				if (item.getDef(owner.getWorld()).getWieldPosition() == Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex()) {
					return item;
				}
			}
		}
		return null;
	}

	public static void dismissManualSummon(final Player owner) {
		final Npc summon = owner.getAttribute(MANUAL_SUMMON_KEY, null);
		if (summon == null) {
			clearManualSummonState(owner);
			return;
		}
		finishManualSummon(owner, summon, true);
	}

	private static void dismissArmorSummon(final Player owner) {
		final Npc summon = owner.getAttribute(ARMOR_SUMMON_KEY, null);
		if (summon == null) {
			owner.removeAttribute(ARMOR_SUMMON_KEY);
			return;
		}
		finishArmorSummon(owner, summon, true);
	}

	private static void finishManualSummon(final Player owner, final Npc summon, final boolean removeSummon) {
		if (removeSummon && summon != null && !summon.isRemoved()) {
			summon.remove();
		}
		final Npc activeSummon = owner.getAttribute(MANUAL_SUMMON_KEY, null);
		if (activeSummon == summon) {
			clearManualSummonState(owner);
		}
	}

	private static void finishArmorSummon(final Player owner, final Npc summon, final boolean removeSummon) {
		if (removeSummon && summon != null && !summon.isRemoved()) {
			summon.remove();
		}
		final Npc activeSummon = owner.getAttribute(ARMOR_SUMMON_KEY, null);
		if (activeSummon == summon) {
			owner.removeAttribute(ARMOR_SUMMON_KEY);
		}
	}

	private static void finishSummon(final Player owner, final Npc summon, final boolean removeSummon) {
		if (summon != null && SOURCE_ARMOR.equals(summon.getAttribute(SUMMON_SOURCE_KEY, SOURCE_MANUAL))) {
			finishArmorSummon(owner, summon, removeSummon);
		} else {
			finishManualSummon(owner, summon, removeSummon);
		}
	}

	private static void clearManualSummonState(final Player owner) {
		owner.removeAttribute(MANUAL_SUMMON_KEY);
		owner.removeAttribute(RAT_AWAITING_ITEM_KEY);
		owner.removeAttribute(RAT_NPC_KEY);
		owner.removeAttribute("myworld_rat_note_choices");
		owner.removeAttribute(CAMEL_AWAITING_ITEM_KEY);
		owner.removeAttribute(CAMEL_NPC_KEY);
		ActionSender.sendStats(owner);
		ActionSender.sendEquipmentStats(owner);
	}

	public static void dismissAll(final Player owner) {
		dismissManualSummon(owner);
		dismissArmorSummon(owner);
	}

	public static void syncArmorSummon(final Player owner) {
		if (owner == null || owner.isRemoved() || !owner.loggedIn()) {
			return;
		}
		final SummonProfile desiredProfile = getDesiredArmorSummonProfile(owner);
		final Npc activeSummon = owner.getAttribute(ARMOR_SUMMON_KEY, null);
		if (desiredProfile == null) {
			if (activeSummon != null) {
				owner.message("@red@Your spirit companion fades.");
				finishArmorSummon(owner, activeSummon, true);
			}
			return;
		}
		if (activeSummon != null && !activeSummon.isRemoved()
			&& desiredProfile.kind.equals(activeSummon.getAttribute(SUMMON_KIND_KEY, ""))) {
			return;
		}
		if (activeSummon != null) {
			finishArmorSummon(owner, activeSummon, true);
		}
		spawnArmorSummon(owner, desiredProfile);
		owner.message("@gre@A " + desiredProfile.name.toLowerCase() + " companion appears.");
	}

	private static SummonProfile getDesiredArmorSummonProfile(final Player owner) {
		if (owner.hasFullHellhoundSet()) {
			return SPIRIT_HELLHOUND_PROFILE;
		}
		if (owner.hasFullWolfSet()) {
			return SPIRIT_WOLF_PROFILE;
		}
		return null;
	}

	public static boolean isOwnedUtilityRat(final Player player, final Npc npc) {
		return isOwnedSummon(player, npc)
			&& KIND_RAT.equals(npc.getAttribute(SUMMON_KIND_KEY, ""))
			&& SOURCE_MANUAL.equals(npc.getAttribute(SUMMON_SOURCE_KEY, ""));
	}

	public static boolean isOwnedUtilityCamel(final Player player, final Npc npc) {
		return isOwnedSummon(player, npc)
			&& KIND_CAMEL.equals(npc.getAttribute(SUMMON_KIND_KEY, ""))
			&& SOURCE_MANUAL.equals(npc.getAttribute(SUMMON_SOURCE_KEY, ""));
	}

	public static boolean isOwnedUtilitySummon(final Player player, final Npc npc) {
		return isOwnedUtilityRat(player, npc) || isOwnedUtilityCamel(player, npc);
	}

	public static boolean isOwnedSummon(final Player player, final Npc npc) {
		if (npc == null || npc.isRemoved()) {
			return false;
		}
		final Long ownerHash = npc.getAttribute(SUMMON_OWNER_KEY, -1L);
		return ownerHash == player.getUsernameHash();
	}

	public static boolean isSummon(final Npc npc) {
		return npc != null && npc.getAttribute(SUMMON_OWNER_KEY, -1L) > 0L;
	}

	public static boolean isSummon(final Mob mob) {
		return mob != null && mob.isNpc() && isSummon((Npc) mob);
	}

	public static boolean isArmorSummon(final Npc npc) {
		return isSummon(npc) && SOURCE_ARMOR.equals(npc.getAttribute(SUMMON_SOURCE_KEY, ""));
	}

	public static boolean hasImpProtection(final Player player) {
		final Npc summon = player.getAttribute(MANUAL_SUMMON_KEY, null);
		return summon != null
			&& !summon.isRemoved()
			&& isOwnedSummon(player, summon)
			&& KIND_IMP.equals(summon.getAttribute(SUMMON_KIND_KEY, ""));
	}

	public static boolean tryAutoBuryDrop(final Player owner, final int itemId, final int amount) {
		if (owner == null || amount <= 0 || !hasBlackUnicorn(owner) || !isPrayerDrop(itemId)) {
			return false;
		}
		final int xp = getPrayerDropExperience(itemId) * amount * 2;
		if (xp > 0) {
			owner.incExp(Skill.PRAYER.id(), xp, true);
			owner.message("@gre@Your black unicorn sanctifies the " + getPrayerDropName(owner, itemId, amount) + ".");
		}
		return true;
	}

	public static void creditSummonProjectileDamage(final Mob caster, final Mob opponent, final int damage, final int type) {
		if (!isSummon(caster) || damage <= 0 || opponent == null || !opponent.isNpc()) {
			return;
		}
		final Player owner = ((Npc) caster).getWorld().getPlayer(caster.getAttribute(SUMMON_OWNER_KEY, -1L));
		if (owner == null) {
			return;
		}
		final Npc targetNpc = (Npc) opponent;
		if (type == 1 || type == 4) {
			targetNpc.addMageDamage(owner, damage);
		} else if (type == 2 || type == 5) {
			targetNpc.addRangeDamage(owner, damage);
		} else {
			targetNpc.addCombatDamage(owner, damage);
		}
	}

	public static int applySummonOutgoingDamage(final Mob hitter, final int damage) {
		if (damage <= 0 || !isSummon(hitter)) {
			return damage;
		}
		final int maxHit = ((Npc) hitter).getAttribute(SUMMON_MAX_HIT_KEY, 0);
		if (maxHit <= 0) {
			return damage;
		}
		return Math.min(damage, maxHit);
	}

	public static int getPrayerBonus(final Player player) {
		final Npc summon = player.getAttribute(MANUAL_SUMMON_KEY, null);
		if (summon == null || summon.isRemoved() || !isOwnedSummon(player, summon)) {
			return 0;
		}
		return summon.getAttribute(SUMMON_PRAYER_BONUS_KEY, 0);
	}

	public static int applySummonDamageAbsorption(final Player owner, final Mob attacker, final int damage) {
		if (owner == null || attacker == null || damage <= 0 || !attacker.isNpc()) {
			return damage;
		}
		final Npc summon = owner.getAttribute(MANUAL_SUMMON_KEY, null);
		if (summon == null || summon.isRemoved() || !isOwnedSummon(owner, summon)) {
			return damage;
		}
		if (!summon.getAttribute(SUMMON_DAMAGE_ABSORBING_KEY, false)) {
			return damage;
		}
		if (!summon.withinRange(owner, CATCH_UP_DISTANCE + 1)) {
			return damage;
		}
		final String trait = summon.getAttribute(SUMMON_TRAIT_KEY, TRAIT_NONE);
		if (TRAIT_FEAR.equals(trait) && DataConversions.getRandom().nextDouble() < 0.20D) {
			owner.message("@gre@Your ghost frightens the attacker.");
			return 0;
		}
		if (TRAIT_EVASIVE.equals(trait) && DataConversions.getRandom().nextDouble() < 0.10D) {
			owner.message("@gre@Your giant bat evades the blow.");
			return damage;
		}
		final int absorbPercent = summon.getAttribute(SUMMON_ABSORB_PERCENT_KEY, 0);
		final int summonHits = summon.getSkills().getLevel(Skill.HITS.id());
		if (absorbPercent <= 0 || summonHits <= 0) {
			return damage;
		}

		int absorbed = (int) Math.ceil(damage * (absorbPercent / 100.0D));
		absorbed = Math.max(1, Math.min(damage, absorbed));
		absorbed = Math.min(absorbed, summonHits);
		if (absorbed <= 0) {
			return damage;
		}

		summon.getSkills().subtractLevel(Skill.HITS.id(), absorbed, false);
		summon.getUpdateFlags().setDamage(new Damage(summon, absorbed));
		summon.getUpdateFlags().addHitSplat(new HitSplat(summon, HitSplat.TYPE_STANDARD, absorbed));

		if (summon.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			summon.setAttribute(SUMMON_EXHAUSTED_KEY, true);
			summon.resetCombatEvent();
			owner.message("@red@Your summoned " + getSummonDisplayName(summon) + " absorbs a final blow and disappears.");
		}
		return damage - absorbed;
	}

	public static boolean applySummonOnHitEffects(final Mob hitter, final Mob target, final int standardDamage) {
		if (!isSummon(hitter) || target == null || target.isRemoved()
			|| target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}
		final Npc summon = (Npc) hitter;
		final String trait = summon.getAttribute(SUMMON_TRAIT_KEY, TRAIT_NONE);
		int bonusDamage = 0;
		boolean magicDamage = false;
		if (TRAIT_RELENTLESS.equals(trait) && DataConversions.getRandom().nextDouble() < 0.15D) {
			final int maxHit = summon.getAttribute(SUMMON_MAX_HIT_KEY, 0);
			bonusDamage = DataConversions.random(0, Math.max(1, maxHit / 2));
		} else if (TRAIT_SPELL_ECHO.equals(trait) && DataConversions.getRandom().nextDouble() < 0.15D) {
			bonusDamage = DataConversions.random(0, 3);
			magicDamage = true;
		} else if (TRAIT_HELLFIRE.equals(trait) && DataConversions.getRandom().nextDouble() < 0.10D) {
			final int maxHit = 8;
			bonusDamage = DataConversions.random(0, maxHit);
			magicDamage = true;
			target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.infernalEffectForMaxHit(maxHit)));
			target.applyInfernalFireDefenseDebuff(6);
		} else if (TRAIT_SPIRIT_HELLFIRE.equals(trait) && DataConversions.getRandom().nextDouble() < 0.05D) {
			final int maxHit = 8;
			bonusDamage = DataConversions.random(0, maxHit);
			magicDamage = true;
			target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.infernalEffectForMaxHit(maxHit)));
			target.applyInfernalFireDefenseDebuff(6);
		}
		if (bonusDamage <= 0) {
			return false;
		}
		return inflictSummonBonusDamage(summon, target, bonusDamage, magicDamage);
	}

	public static void openRatUtility(final Player player, final Npc rat) {
		if (!isOwnedUtilityRat(player, rat)) {
			return;
		}
		player.setAttribute(RAT_AWAITING_ITEM_KEY, true);
		player.setAttribute(RAT_NPC_KEY, rat);
		player.message("Select an inventory item for the rat to turn into certs.");
	}

	public static void openCamelUtility(final Player player, final Npc camel) {
		if (!isOwnedUtilityCamel(player, camel)) {
			return;
		}
		player.setAttribute(CAMEL_AWAITING_ITEM_KEY, true);
		player.setAttribute(CAMEL_NPC_KEY, camel);
		player.message("Select one inventory item for the camel to deposit in your bank.");
	}

	public static void openUtilitySummon(final Player player, final Npc summon) {
		if (isOwnedUtilityRat(player, summon)) {
			openRatUtility(player, summon);
		} else if (isOwnedUtilityCamel(player, summon)) {
			openCamelUtility(player, summon);
		}
	}

	public static boolean handleSummonCommand(final Player player, final Npc summon, final String command) {
		if (!isOwnedSummon(player, summon)) {
			return false;
		}
		if (isArmorSummon(summon)) {
			player.message("Your spirit companion is bound to your armor.");
			return true;
		}
		if ("Dismiss".equalsIgnoreCase(command)) {
			dismissManualSummon(player);
			player.message("You dismiss your summon.");
			return true;
		}
		if (isOwnedUtilitySummon(player, summon)) {
			openUtilitySummon(player, summon);
			return true;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	public static boolean handleRatMenuReply(final Player player, final int option) {
		final List<ItemChoice> choices = player.getAttribute("myworld_rat_note_choices", null);
		final Npc rat = player.getAttribute("myworld_rat_note_npc", null);
		if (choices == null || rat == null) {
			return false;
		}
		player.removeAttribute("myworld_rat_note_choices");
		player.removeAttribute("myworld_rat_note_npc");
		if (option < 0 || option >= choices.size()) {
			return true;
		}
		if (!isOwnedUtilityRat(player, rat)) {
			player.message("The rat is no longer here.");
			return true;
		}
		final ItemChoice choice = choices.get(option);
		convertInventoryItemToNotes(player, choice.catalogId);
		dismissManualSummon(player);
		return true;
	}

	public static boolean handleRatItemSelection(final Player player, final Item item) {
		if (!player.getAttribute(RAT_AWAITING_ITEM_KEY, false)) {
			return false;
		}
		player.removeAttribute(RAT_AWAITING_ITEM_KEY);
		final Npc rat = player.getAttribute(RAT_NPC_KEY, null);
		player.removeAttribute(RAT_NPC_KEY);
		if (rat == null || !isOwnedUtilityRat(player, rat)) {
			player.message("The rat is no longer here.");
			return true;
		}
		if (item == null || item.getNoted()) {
			player.message("The rat can't turn that into certs.");
			return true;
		}
		if (!item.getDef(player.getWorld()).isNoteable()) {
			player.message("The rat can't turn that into certs.");
			return true;
		}
		convertInventoryItemToNotes(player, item.getCatalogId());
		dismissManualSummon(player);
		return true;
	}

	public static boolean handleCamelItemSelection(final Player player, final Item item) {
		if (!player.getAttribute(CAMEL_AWAITING_ITEM_KEY, false)) {
			return false;
		}
		player.removeAttribute(CAMEL_AWAITING_ITEM_KEY);
		final Npc camel = player.getAttribute(CAMEL_NPC_KEY, null);
		player.removeAttribute(CAMEL_NPC_KEY);
		if (camel == null || !isOwnedUtilityCamel(player, camel)) {
			player.message("The camel is no longer here.");
			return true;
		}
		depositInventorySlotToBank(player, item);
		dismissManualSummon(player);
		return true;
	}

	private static void spawnManualSummon(final Player owner, final SummonProfile profile) {
		dismissManualSummon(owner);
		final Point spawnLocation = adjacentTo(owner);
		final Npc summon = new Npc(owner.getWorld(), profile.npcId, spawnLocation.getX(), spawnLocation.getY());
		summon.setShouldRespawn(false);
		summon.relatedMob = owner;
		summon.setAttribute(SUMMON_OWNER_KEY, owner.getUsernameHash());
		summon.setAttribute(SUMMON_KIND_KEY, profile.kind);
		summon.setAttribute(SUMMON_SOURCE_KEY, SOURCE_MANUAL);
		summon.setAttribute(SUMMON_ABSORB_PERCENT_KEY, profile.absorbPercent);
		summon.setAttribute(SUMMON_DAMAGE_ABSORBING_KEY, profile.damageAbsorbing);
		summon.setAttribute(SUMMON_MAX_HIT_KEY, getScaledMaxHit(owner, profile));
		summon.setAttribute(SUMMON_TRAIT_KEY, profile.trait);
		summon.setAttribute(SUMMON_PRAYER_BONUS_KEY, profile.prayerBonus);
		applySummonProfile(owner, summon, profile);
		summon.getUpdateFlags().setCombatEffect(new CombatEffect(summon, getSummonArrivalEffect(profile)));
		owner.getWorld().registerNpc(summon);
		owner.setAttribute(MANUAL_SUMMON_KEY, summon);
		ActionSender.sendStats(owner);
		ActionSender.sendEquipmentStats(owner);
		startSummonRuntime(owner, summon, profile);
	}

	private static int getSummonArrivalEffect(final SummonProfile profile) {
		if (profile.role == SummonRole.UTILITY) {
			return CombatEffect.SUMMON_UTILITY;
		}
		if (profile.role == SummonRole.SUPPORT) {
			return CombatEffect.SUMMON_SUPPORT;
		}
		return CombatEffect.SUMMON_COMBAT;
	}

	private static void spawnArmorSummon(final Player owner, final SummonProfile profile) {
		final Point spawnLocation = adjacentTo(owner);
		final Npc summon = new Npc(owner.getWorld(), profile.npcId, spawnLocation.getX(), spawnLocation.getY());
		summon.setShouldRespawn(false);
		summon.relatedMob = owner;
		summon.setAttribute(SUMMON_OWNER_KEY, owner.getUsernameHash());
		summon.setAttribute(SUMMON_KIND_KEY, profile.kind);
		summon.setAttribute(SUMMON_SOURCE_KEY, SOURCE_ARMOR);
		summon.setAttribute(SUMMON_ABSORB_PERCENT_KEY, 0);
		summon.setAttribute(SUMMON_DAMAGE_ABSORBING_KEY, false);
		summon.setAttribute(SUMMON_MAX_HIT_KEY, profile.baseMaxHit);
		summon.setAttribute(SUMMON_TRAIT_KEY, profile.trait);
		summon.setAttribute(SUMMON_PRAYER_BONUS_KEY, 0);
		summon.setAttribute(SUMMON_INVULNERABLE_KEY, true);
		applySummonProfile(owner, summon, profile);
		owner.getWorld().registerNpc(summon);
		owner.setAttribute(ARMOR_SUMMON_KEY, summon);
		startSummonRuntime(owner, summon, profile);
	}

	private static String getSummonDisplayName(final Npc summon) {
		if (summon == null) {
			return "summon";
		}
		final String kind = summon.getAttribute(SUMMON_KIND_KEY, "");
		for (SummonProfile profile : SUMMON_PROFILES) {
			if (profile.kind.equals(kind)) {
				return profile.name.toLowerCase();
			}
		}
		for (SummonProfile profile : ARMOR_SUMMON_PROFILES) {
			if (profile.kind.equals(kind)) {
				return profile.name.toLowerCase();
			}
		}
		return "summon";
	}

	private static void applySummonProfile(final Player owner, final Npc summon, final SummonProfile profile) {
		summon.getSkills().setTemporaryLevelAndMaxStat(Skill.ATTACK.id(), profile.attack, profile.attack, false);
		summon.getSkills().setTemporaryLevelAndMaxStat(Skill.DEFENSE.id(), profile.defense, profile.defense, false);
		summon.getSkills().setTemporaryLevelAndMaxStat(Skill.RANGED.id(), profile.ranged, profile.ranged, false);
		summon.getSkills().setTemporaryLevelAndMaxStat(Skill.STRENGTH.id(), profile.strength, profile.strength, false);
		final int summonHits = profile.role == SummonRole.COMBAT ? getScaledHits(owner, profile) : profile.hits;
		summon.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), summonHits, summonHits, false);
	}

	private static int getScaledHits(final Player owner, final SummonProfile profile) {
		int hits = scaleFromSummoningLevel(owner, profile, profile.baseHits, profile.hitsGrowthInterval);
		if (profile.role == SummonRole.COMBAT) {
			final int bonusPercent = owner.getCarriedItems().getEquipment().getLifeNecklaceSummonHealthPercent();
			if (bonusPercent > 0) {
				hits += Math.max(1, (int) Math.ceil(hits * (bonusPercent / 100.0D)));
			}
		}
		return hits;
	}

	private static int getScaledMaxHit(final Player owner, final SummonProfile profile) {
		int maxHit = scaleFromSummoningLevel(owner, profile, profile.baseMaxHit, profile.maxHitGrowthInterval);
		if (profile.role == SummonRole.COMBAT) {
			maxHit += owner.getCarriedItems().getEquipment().getLifeAmuletSummonMaxDamageBonus();
		}
		return maxHit;
	}

	private static int scaleFromSummoningLevel(final Player owner, final SummonProfile profile, final int baseValue, final int interval) {
		if (interval <= 0) {
			return Math.max(1, baseValue);
		}
		final int summoning = Skill.SUMMONING.id();
		final int summoningLevel = summoning < 0 ? profile.level : owner.getSkills().getMaxStat(summoning);
		final int levelsBeyondRequirement = Math.max(0, summoningLevel - profile.level);
		return Math.max(1, baseValue + (levelsBeyondRequirement / interval));
	}

	private static void startSummonRuntime(final Player owner, final Npc summon, final SummonProfile profile) {
		owner.getWorld().getServer().getGameEventHandler().add(new GameTickEvent(owner.getWorld(), summon, 1, "MyWorld Summon Runtime", DuplicationStrategy.ONE_PER_MOB) {
			private int ticksRemaining = profile.durationTicks > 0 ? getDurationTicks(owner, profile) : profile.durationTicks;

			@Override
			public void run() {
				setDelayTicks(1);
				if (!owner.loggedIn() || owner.isRemoved() || summon.isRemoved()
					|| summon.getAttribute(SUMMON_EXHAUSTED_KEY, false)
					|| (!summon.getAttribute(SUMMON_INVULNERABLE_KEY, false)
						&& summon.getSkills().getLevel(Skill.HITS.id()) <= 0)) {
					finishSummon(owner, summon, !summon.isRemoved());
					stop();
					return;
				}
				if (owner.getSkills().getLevel(Skill.HITS.id()) <= 0) {
					finishSummon(owner, summon, true);
					stop();
					return;
				}
				if (KIND_IMP.equals(profile.kind) && owner.getOpponent() != null
					&& ownerIsActivelyAttacking(owner, owner.getOpponent())) {
					owner.message("@red@Your imp leaves as you start fighting.");
					finishSummon(owner, summon, true);
					stop();
					return;
				}

				keepNearOwner(owner, summon);
				if (profile.durationTicks > 0 && --ticksRemaining <= 0) {
					if (profile.role == SummonRole.SUPPORT && consumeLifeRune(owner)) {
						ticksRemaining = getDurationTicks(owner, profile);
						owner.message("@gre@A life rune sustains your summon.");
					} else {
						if (profile.role == SummonRole.SUPPORT) {
							owner.message("@red@Your summon fades away without a life rune to sustain it.");
						}
						finishSummon(owner, summon, true);
						stop();
						return;
					}
				}
				if (profile.combatAssist) {
					assistOwnerTarget(owner, summon);
				}
			}
		});
	}

	private static void keepNearOwner(final Player owner, final Npc summon) {
		if (!summon.withinRange(owner, CATCH_UP_DISTANCE)) {
			Point destination = adjacentTo(owner);
			summon.resetPath();
			summon.teleport(destination.getX(), destination.getY());
			summon.face(owner);
			return;
		}
		if (!summon.inCombat() && !summon.withinRange(owner, FOLLOW_RADIUS)) {
			Point destination = adjacentTo(owner);
			summon.walkToEntity(destination.getX(), destination.getY());
		}
	}

	private static Point adjacentTo(final Player owner) {
		final int[][] offsets = {
			{1, 0}, {-1, 0}, {0, 1}, {0, -1},
			{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
		};
		final int initialOffset = DataConversions.random(0, offsets.length - 1);
		for (int attempt = 0; attempt < offsets.length; attempt++) {
			final int[] offset = offsets[(initialOffset + attempt) % offsets.length];
			final int x = owner.getX() + offset[0];
			final int y = owner.getY() + offset[1];
			if (isValidAdjacentSummonTile(owner, x, y)) {
				return Point.location(x, y);
			}
		}
		return owner.getLocation();
	}

	private static boolean isValidAdjacentSummonTile(final Player owner, final int x, final int y) {
		if ((owner.getWorld().getTile(x, y).traversalMask & CollisionFlag.FULL_BLOCK) != 0) {
			return false;
		}
		if (!PathValidation.checkAdjacentDistance(owner.getWorld(), owner.getX(), owner.getY(), x, y, true, false)) {
			return false;
		}
		return !isSummonSpawnTileOccupied(owner, x, y);
	}

	private static boolean isSummonSpawnTileOccupied(final Player owner, final int x, final int y) {
		for (Player player : owner.getViewArea().getPlayersInView()) {
			if (!player.isRemoved() && player.getX() == x && player.getY() == y) {
				return true;
			}
		}
		for (Npc npc : owner.getViewArea().getNpcsInView()) {
			if (!npc.isRemoved() && !npc.isRespawning() && npc.getX() == x && npc.getY() == y) {
				return true;
			}
		}
		return false;
	}

	private static void assistOwnerTarget(final Player owner, final Npc summon) {
		final Mob target = owner.getOpponent();
		if (target == null || target.isRemoved() || target == summon || target == owner) {
			stopSummonAssist(summon);
			return;
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			stopSummonAssist(summon);
			return;
		}
		if (!ownerIsActivelyAttacking(owner, target)) {
			stopSummonAssist(summon);
			return;
		}
		if (summon.getPvmMeleeEvent() != null && summon.getPvmMeleeEvent().isRunning() && summon.getPvmMeleeEvent().getTarget() == target) {
			return;
		}
		if (!summon.withinRange(owner, FOLLOW_RADIUS)) {
			Point destination = adjacentTo(owner);
			summon.teleport(destination.getX(), destination.getY());
		}
		if (trySummonProjectileAttack(owner, summon, target)) {
			return;
		}
		summon.startCombat(target);
	}

	private static boolean trySummonProjectileAttack(final Player owner, final Npc summon, final Mob target) {
		final String style = getSummonAttackStyle(summon);
		if (ATTACK_STYLE_MELEE.equals(style)) {
			return false;
		}
		if (ATTACK_STYLE_MELEE_MAGIC.equals(style)
			&& summon.withinRange(target, 1)
			&& DataConversions.getRandom().nextDouble() >= 0.50D) {
			return false;
		}

		final int distance = Math.max(Math.abs(summon.getX() - target.getX()), Math.abs(summon.getY() - target.getY()));
		if (distance > SUMMON_PROJECTILE_RANGE) {
			return !ATTACK_STYLE_MELEE_MAGIC.equals(style);
		}
		if (!PathValidation.checkPath(summon.getWorld(), summon.getLocation(), target.getLocation())) {
			return !ATTACK_STYLE_MELEE_MAGIC.equals(style);
		}
		final long currentTick = summon.getWorld().getServer().getCurrentTick();
		final long nextAttackTick = summon.getAttribute(SUMMON_NEXT_ATTACK_TICK_KEY, 0L);
		if (currentTick < nextAttackTick) {
			return true;
		}

		summon.resetPath();
		summon.face(target);
		summon.setCombatTimer();
		summon.setAttribute(SUMMON_NEXT_ATTACK_TICK_KEY, currentTick + SUMMON_ATTACK_DELAY_TICKS);

		final int maxHit = Math.max(1, summon.getAttribute(SUMMON_MAX_HIT_KEY, 1));
		final int damage = DataConversions.random(0, maxHit);
		final int projectileType = ATTACK_STYLE_RANGED.equals(style) ? 2 : 1;
		summon.getWorld().getServer().getGameEventHandler().add(
			new ProjectileEvent(summon.getWorld(), summon, target, damage, projectileType, false)
		);
		return true;
	}

	private static String getSummonAttackStyle(final Npc summon) {
		final String kind = summon.getAttribute(SUMMON_KIND_KEY, "");
		if (KIND_GIANT_BAT.equals(kind) || KIND_GHOST.equals(kind)) {
			return ATTACK_STYLE_RANGED;
		}
		if (KIND_OTHERWORLDLY_BEING.equals(kind)) {
			return ATTACK_STYLE_MAGIC;
		}
		if (KIND_GREATER_DEMON.equals(kind)) {
			return ATTACK_STYLE_MELEE_MAGIC;
		}
		return ATTACK_STYLE_MELEE;
	}

	private static void stopSummonAssist(final Npc summon) {
		if (summon.getPvmMeleeEvent() != null || summon.getOpponent() != null) {
			summon.resetCombatEvent();
			summon.setOpponent(null);
			summon.setLastOpponent(null);
			if (summon.getSprite() > 7) {
				summon.setSprite(4);
			}
		}
	}

	private static boolean ownerIsActivelyAttacking(final Player owner, final Mob target) {
		final PvmMeleeEvent meleeEvent = owner.getPvmMeleeEvent();
		if (meleeEvent != null && meleeEvent.isRunning() && meleeEvent.getTarget() == target) {
			return true;
		}
		final RangeEvent rangeEvent = owner.getRangeEvent();
		if (rangeEvent != null && rangeEvent.isRunning() && rangeEvent.getTarget() == target) {
			return true;
		}
		final ThrowingEvent throwingEvent = owner.getThrowingEvent();
		if (throwingEvent != null && throwingEvent.isRunning() && throwingEvent.getTarget() == target) {
			return true;
		}
		final MagicCombatEvent magicEvent = owner.getMagicCombatEvent();
		return magicEvent != null && magicEvent.isRunning() && magicEvent.getTarget() == target;
	}

	private static List<ItemChoice> getNoteableInventoryChoices(final Player player) {
		final Inventory inventory = player.getCarriedItems().getInventory();
		final Map<Integer, ItemChoice> byCatalogId = new LinkedHashMap<>();
		synchronized (inventory.getItems()) {
			for (Item item : inventory.getItems()) {
				if (item == null || item.getNoted()) {
					continue;
				}
				final ItemDefinition def = item.getDef(player.getWorld());
				if (def == null || !def.isNoteable()) {
					continue;
				}
				ItemChoice current = byCatalogId.get(item.getCatalogId());
				if (current == null) {
					byCatalogId.put(item.getCatalogId(), new ItemChoice(item.getCatalogId(), def.getName(), item.getAmount()));
				} else {
					current.amount += item.getAmount();
				}
			}
		}
		return new ArrayList<ItemChoice>(byCatalogId.values());
	}

	private static void convertInventoryItemToNotes(final Player player, final int catalogId) {
		final Inventory inventory = player.getCarriedItems().getInventory();
		final int amount = inventory.countId(catalogId, Optional.of(false));
		if (amount <= 0) {
			player.message("You don't have that item anymore.");
			return;
		}
		final ItemDefinition def = player.getWorld().getServer().getEntityHandler().getItemDef(catalogId);
		if (def == null || !def.isNoteable()) {
			player.message("The rat can't turn that into certs.");
			return;
		}
		long removedItemId = player.getCarriedItems().remove(new Item(catalogId, amount, false), false);
		if (removedItemId == -1) {
			player.message("The rat couldn't gather those items.");
			ActionSender.sendInventory(player);
			return;
		}
		inventory.add(new Item(catalogId, amount, true), false);
		ActionSender.sendInventory(player);
		player.message("The rat turns your " + def.getName() + " into certs and disappears.");
	}

	private static void depositInventorySlotToBank(final Player player, final Item selectedItem) {
		if (selectedItem == null) {
			player.message("The camel can't find that item.");
			return;
		}
		final Item inventoryItem = selectedItem;
		if (inventoryItem == null) {
			player.message("You don't have that item anymore.");
			return;
		}
		final ItemDefinition def = inventoryItem.getDef(player.getWorld());
		if (def == null) {
			player.message("The camel can't carry that item.");
			return;
		}
		final int amount = (def.isStackable() || inventoryItem.getNoted()) ? inventoryItem.getAmount() : 1;
		final Item bankItem = new Item(inventoryItem.getCatalogId(), amount, inventoryItem.getNoted());
		if (!player.getBank().canHold(bankItem)) {
			player.message("Your bank is too full for the camel to deposit that.");
			return;
		}
		final long removedItemId = player.getCarriedItems().remove(
			new Item(inventoryItem.getCatalogId(), amount, inventoryItem.getNoted(), inventoryItem.getItemId()), false);
		if (removedItemId == -1) {
			player.message("The camel couldn't gather that item.");
			ActionSender.sendInventory(player);
			return;
		}
		if (!player.getBank().add(bankItem, false)) {
			player.getCarriedItems().getInventory().add(bankItem, false);
			player.message("Your bank is too full for the camel to deposit that.");
			ActionSender.sendInventory(player);
			return;
		}
		ActionSender.sendInventory(player);
		player.message("The camel deposits your " + def.getName() + " and disappears.");
	}

	private static boolean inflictSummonBonusDamage(final Npc summon, final Mob target, int damage, final boolean magicDamage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}
		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			damage = targetPlayer.applyRobeDamageMitigation(damage);
			if (magicDamage) {
				damage = targetPlayer.applyPotionMagicDamageReduction(damage);
			} else {
				damage = targetPlayer.applyPotionMeleeDamageReduction(damage);
			}
		}
		if (damage <= 0) {
			return false;
		}
		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		final int damageDealt = Math.min(damage, lastHits);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc()) {
			final long ownerHash = summon.getAttribute(SUMMON_OWNER_KEY, -1L);
			final Player owner = summon.getWorld().getPlayer(ownerHash);
			if (owner != null) {
				if (magicDamage) {
					((Npc) target).addMageDamage(owner, damageDealt);
				} else {
					((Npc) target).addCombatDamage(owner, damageDealt);
				}
			}
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		return target.getSkills().getLevel(Skill.HITS.id()) <= 0;
	}

	private static boolean consumeLifeRune(final Player owner) {
		if (shouldPreserveRuneCost(owner, ItemId.LIFE_RUNE.id())) {
			return true;
		}
		final boolean consumed = owner.getCarriedItems().remove(new Item(ItemId.LIFE_RUNE.id(), 1), false) != -1;
		if (consumed) {
			ActionSender.sendInventory(owner);
		}
		return consumed;
	}

	private static boolean hasBlackUnicorn(final Player player) {
		final Npc summon = player.getAttribute(MANUAL_SUMMON_KEY, null);
		return summon != null
			&& !summon.isRemoved()
			&& isOwnedSummon(player, summon)
			&& KIND_BLACK_UNICORN.equals(summon.getAttribute(SUMMON_KIND_KEY, ""));
	}

	private static boolean isPrayerDrop(final int itemId) {
		final ItemId item = ItemId.getById(itemId);
		if (item == null) {
			return false;
		}
		switch (item) {
			case BONES:
			case BAT_BONES:
			case BIG_BONES:
			case DRAGON_BONES:
			case ASHES:
			case DEMON_ASH:
				return true;
			default:
				return false;
		}
	}

	private static int getPrayerDropExperience(final int itemId) {
		final ItemId item = ItemId.getById(itemId);
		if (item == null) {
			return 0;
		}
		switch (item) {
			case BONES:
			case ASHES:
				return 15;
			case DEMON_ASH:
				return 80;
			case BAT_BONES:
				return 18;
			case BIG_BONES:
				return 50;
			case DRAGON_BONES:
				return 240;
			default:
				return 0;
		}
	}

	private static String getPrayerDropName(final Player owner, final int itemId, final int amount) {
		final ItemDefinition def = owner.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		final String name = def == null ? "remains" : def.getName();
		return amount == 1 ? name : amount + " " + name;
	}

	private static int getChargeTicks(final Player owner) {
		final int gameTick = Math.max(1, owner.getConfig().GAME_TICK);
		return Math.max(1, (SUMMON_CHARGE_MS + gameTick - 1) / gameTick);
	}

	private static int getDurationTicks(final Player owner, final SummonProfile profile) {
		int durationSeconds = profile.durationTicks;
		if (profile.role == SummonRole.SUPPORT) {
			final int bonusPercent = owner.getCarriedItems().getEquipment().getLifeRingSupportDurationPercent();
			if (bonusPercent > 0) {
				durationSeconds += Math.max(1, (int) Math.ceil(durationSeconds * (bonusPercent / 100.0D)));
			}
		}
		return Math.max(1, (durationSeconds * 1000) / Math.max(1, owner.getConfig().GAME_TICK));
	}

	private static final class ItemChoice {
		private final int catalogId;
		private final String name;
		private int amount;

		private ItemChoice(final int catalogId, final String name, final int amount) {
			this.catalogId = catalogId;
			this.name = name;
			this.amount = amount;
		}
	}

	private enum SummonRole {
		COMBAT,
		SUPPORT,
		UTILITY
	}

	private static final class Cost {
		private final int itemId;
		private final int amount;

		private Cost(final int itemId, final int amount) {
			this.itemId = itemId;
			this.amount = amount;
		}
	}

	private static final class SummonProfile {
		private final String name;
		private final int level;
		private final int experience;
		private final int npcId;
		private final String kind;
		private final SummonRole role;
		private final boolean combatAssist;
		private final int durationTicks;
		private final int attack;
		private final int defense;
		private final int ranged;
		private final int strength;
		private final int hits;
		private final int baseHits;
		private final int hitsGrowthInterval;
		private final int baseMaxHit;
		private final int maxHitGrowthInterval;
		private final int absorbPercent;
		private final int prayerBonus;
		private final boolean damageAbsorbing;
		private final String trait;
		private final Map<Integer, Integer> costs;

		private SummonProfile(final String name, final int level, final int displayedExperience, final int npcId,
							  final String kind, final SummonRole role, final boolean combatAssist,
							  final int durationTicks, final int attack, final int defense,
							  final int ranged, final int strength, final int hits,
							  final int baseHits, final int hitsGrowthInterval, final int baseMaxHit,
							  final int maxHitGrowthInterval, final int absorbPercent,
							  final int prayerBonus, final boolean damageAbsorbing, final String trait, final Cost... costs) {
			this.name = name;
			this.level = level;
			this.experience = internalExperience(displayedExperience);
			this.npcId = npcId;
			this.kind = kind;
			this.role = role;
			this.combatAssist = combatAssist;
			this.durationTicks = durationTicks;
			this.attack = attack;
			this.defense = defense;
			this.ranged = ranged;
			this.strength = strength;
			this.hits = hits;
			this.baseHits = baseHits;
			this.hitsGrowthInterval = hitsGrowthInterval;
			this.baseMaxHit = baseMaxHit;
			this.maxHitGrowthInterval = maxHitGrowthInterval;
			this.absorbPercent = absorbPercent;
			this.prayerBonus = prayerBonus;
			this.damageAbsorbing = damageAbsorbing;
			this.trait = trait;
			this.costs = new LinkedHashMap<Integer, Integer>();
			for (Cost cost : costs) {
				this.costs.put(cost.itemId, this.costs.getOrDefault(cost.itemId, 0) + cost.amount);
			}
		}
	}
}
