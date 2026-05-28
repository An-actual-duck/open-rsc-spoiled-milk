package com.openrsc.server.event.rsc.impl.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.openrsc.server.constants.ItemId.*;

public class CombatFormula {
	/**
	 * Logger instance
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * Gets a dice roll for melee damage for a single attack
	 * The result is an int sourced from randomness effectively from 0.5 - maxHit.
	 * Max hit is fractional, so it is more rare than other values in most circumstances.
	 * 0 rolls are biased against in a similar way.
	 * @param source      The mob doing the damage
	 * @return The randomized value.
	 */
	private static int calculateMeleeDamage(final Mob source) {
		return rollDamage(source, offenseToMaxHit(source, source.getMeleeOffense()));
	}

	/**
	 * Gets a dice roll for ranged damage for a single attack
	 * The result is an int sourced from randomness effectively from 0.5 - maxHit.
	 * Max hit is fractional, so it is more rare than other values in most circumstances.
	 * 0 rolls are biased against in a similar way.
	 * @param source      The mob doing the damage
	 * @param arrowId	  The type of ranged ammunition
	 * @return The randomized value.
	 */
	private static int calculateRangedDamage(final Mob source, final int bowId, final int arrowId) {
		return rollDamage(source, offenseToMaxHit(source, source.getRangedOffense()));
	}

	/**
	 * Gets a dice roll for magic damage for a single attack.
	 * @param spellPower      The max hit of the spell
	 * @return The randomized value.
	 */
	public static int calculateMagicDamage(final double spellPower) {
		//Given that melee max hit is fractional, it was likely that spell power values ending in "5" were supposed to hit their max hit more often.
		//TODO: More research to see if that was the case. For now, we can just make it uniform after flooring.
		return DataConversions.getRandom().nextInt((int)Math.floor(spellPower) + 1);
	}

	public static int calculateMagicDamage(final Mob source, final Mob victim, final double spellPower) {
		int spellMax = Math.max(1, (int) Math.ceil(spellPower));
		int offenseBonus = offenseBonus(source, source.getMagicOffense());
		int defenseMax = defenseToMitigation(victim.getMagicDefense());
		int attackMax = applyOutgoingMaxHitDebuff(source, spellMax + offenseBonus);
		int damage = applyMitigationRoll(source, victim, attackMax, defenseMax);
		damage = applyMyWorldPrayerModifiers(source, victim, damage, PrayerCatalog.CombatStyle.MAGIC);
		return applyDamageMultiplier(source, damage);
	}

	public static int calculateMagicDamage(final Mob source, final Mob victim, final double spellPower, final double maxHitPercent) {
		int spellMax = Math.max(1, (int) Math.ceil(spellPower));
		int offenseBonus = offenseBonus(source, source.getMagicOffense());
		int attackMax = applyOutgoingMaxHitDebuff(source, spellMax + offenseBonus);
		int defenseMax = defenseToMitigation(victim.getMagicDefense());
		int cappedAttackMax = Math.max(1, (int) Math.ceil(attackMax * maxHitPercent));
		int damage = applyMitigationRoll(source, victim, attackMax, defenseMax, cappedAttackMax);
		damage = applyMyWorldPrayerModifiers(source, victim, damage, PrayerCatalog.CombatStyle.MAGIC);
		return applyDamageMultiplier(source, damage);
	}

	/**
	 * Gets a dice roll for magic damage (god spells) for a single attack
	 *
	 * @param source      The player casting this spell
	 * @return The randomized value.
	 */
	public static int calculateGodSpellDamage(final Player source) {
		return calculateMagicDamage(getGodSpellMax(source, false));
	}

	public static int getGodSpellMax(final Player source, final boolean advancedSpell) {
		int[] godCapes = new int[] {
			ZAMORAK_CAPE.id(),
			SARADOMIN_CAPE.id(),
			GUTHIX_CAPE.id()
		};

		//Authentically, players only receive Charge benefit if they have a god cape equipped.
		boolean hasCapeEquipped = false;
		for (int capeId : godCapes) {
			if (source.getCarriedItems().getEquipment().hasEquipped(capeId)) {
				hasCapeEquipped = true;
				break;
			}
		}
		boolean hasChargeBenefit = source.isCharged() && hasCapeEquipped;
		if (advancedSpell) {
			return hasChargeBenefit ? 32 : 24;
		}
		return hasChargeBenefit ? 25 : 18;
	}

	public static int calculateGodSpellDamage(final Player source, final Mob victim) {
		return calculateGodSpellDamage(source, victim, null);
	}

	public static int calculateGodSpellDamage(final Player source, final Mob victim, final Spells spell) {
		final boolean advancedSpell = spell == Spells.ZAMORAKS_APOCOLYPSE
			|| spell == Spells.SARADOMIN_SOUL_SLASH
			|| spell == Spells.CLAW_OF_GUTHIX;
		int godSpellMax = getGodSpellMax(source, advancedSpell);

		return calculateMagicDamage(source, victim, godSpellMax);
	}

	/**
	 * Gets a dice roll for magic damage (iban blast) for a single attack
	 *
	 * @return The randomized value.
	 */
	public static int calculateIbanSpellDamage() {
		// TODO: Remove this code and roll it into calculateMagicDamage
		// Source for max damage: http://web.archive.org/web/20041226185618/http://www.rsinn.com/forum/showthread.php?t=2469
		return calculateMagicDamage(15);
	}

	public static int calculateIbanSpellDamage(final Player source, final Mob victim) {
		return calculateMagicDamage(source, victim, 15);
	}

	/**
	 * Calculates an accuracy check (base method)
	 *
	 * @param accuracy            The accuracy term
	 * @param defence             The defence term
	 * @return True if the attack is a hit, false if the attack is a miss
	 */
	private static boolean calculateAccuracy(final double accuracy, final double defence) {
		double hitChance;
		if (accuracy > defence) {
			hitChance = 1 - ((defence + 2) / (2 * (accuracy + 1)));
		} else {
			hitChance = (accuracy) / (2 * (defence + 1));
		}

		double rand = Math.random();
		boolean didHit = rand <= hitChance;

		return didHit;
	}


	/**
	 * Calculates an accuracy check (melee)
	 *
	 * @param source             The attacking mob.
	 * @param victim             The mob being attacked.
	 * @return True if the attack is a hit, false if the attack is a miss
	 */
	private static boolean calculateMeleeAccuracy(final Mob source, final Mob victim) {
		return calculateAccuracy(getMeleeAccuracy(source), getMeleeDefence(victim));
	}

	/**
	 * Calculates an accuracy check (ranged)
	 *
	 * @param source             The attacking mob.
	 * @param bowId				 The type of ranged weapon being wielded
	 * @param victim             The mob being attacked.
	 * @return True if the attack is a hit, false if the attack is a miss
	 */
	private static boolean calculateRangedAccuracy(final Mob source, final int bowId, final Mob victim) {
		return calculateAccuracy(getRangedAccuracy(source, bowId), getMeleeDefence(victim));
	}

	/**
	 * Gets the damage dealt for a specific attack. Includes accuracy checks.
	 *
	 * @param source             The attacking mob.
	 * @param victim             The mob being attacked.
	 * @return The amount to hit.
	 */
	public static int doMeleeDamage(final Mob source, final Mob victim) {
		int damage = applyMitigationRoll(source, victim, offenseToMaxHit(source, source.getMeleeOffense()), defenseToMitigation(victim.getMeleeDefense()));
		damage = applyMyWorldPrayerModifiers(source, victim, damage, PrayerCatalog.CombatStyle.MELEE);
		damage = applyDamageMultiplier(source, damage);
		if (victim instanceof Player) {
			((Player) victim).updateDamageAndBlockedDamageTracking(source, damage, 0);
		}
		return damage;
	}

	/**
	 * Gets the damage dealt for a specific attack. Includes accuracy checks.
	 *
	 * @param source             The attacking mob.
	 * @param bowId				 The ranged ammo being wielded
	 * @param arrowId			 The ranged weapon being wielded
	 * @param victim             The mob being attacked.
	 * @return The amount to hit.
	 */
	public static int doRangedDamage(final Mob source, final int bowId, final int arrowId, final Mob victim, final boolean skillCape) {
		int attackMax = offenseToMaxHit(source, source.getRangedOffense());
		if (skillCape) {
			attackMax *= 2;
		}
		int damage = applyMitigationRoll(source, victim, attackMax, defenseToMitigation(victim.getRangedDefense()));
		damage = applyMyWorldPrayerModifiers(source, victim, damage, PrayerCatalog.CombatStyle.RANGED);
		return applyDamageMultiplier(source, damage);
	}

	private static int applyMyWorldPrayerModifiers(final Mob source, final Mob victim, int damage, final PrayerCatalog.CombatStyle combatStyle) {
		if (damage <= 0) {
			return damage;
		}
		if (source instanceof Player && ((Player) source).getConfig().WANT_MYWORLD) {
			final int offenseBonus = ((Player) source).getPrayers().getOffenseBonusPercent(combatStyle);
			if (offenseBonus > 0) {
				damage = (int) Math.ceil(damage * (100.0D + offenseBonus) / 100.0D);
			}
		}
		if (victim instanceof Player && ((Player) victim).getConfig().WANT_MYWORLD) {
			final int defenseReduction = ((Player) victim).getPrayers().getDefenseReductionPercent(combatStyle);
			if (defenseReduction > 0) {
				damage = (int) Math.floor(damage * (100.0D - defenseReduction) / 100.0D);
			}
		}
		return Math.max(0, damage);
	}

	private static int offenseToMaxHit(final Mob source, final int totalOffense) {
		double scaled = totalOffense / 7.0D;
		int bonus = roundsOffenseUp(source) ? (int) Math.ceil(scaled) : (int) Math.floor(scaled);
		return applyOutgoingMaxHitDebuff(source, Math.max(1, 1 + bonus));
	}

	private static int offenseBonus(final Mob source, final int totalOffense) {
		double scaled = totalOffense / 7.0D;
		return Math.max(0, roundsOffenseUp(source) ? (int) Math.ceil(scaled) : (int) Math.floor(scaled));
	}

	private static boolean roundsOffenseUp(final Mob source) {
		return source == null || !source.isNpc();
	}

	private static int defenseToMitigation(final int totalDefense) {
		return Math.max(0, (int) Math.ceil(totalDefense / 7.0D));
	}

	private static int rollDamage(final Mob source, final int maxHit) {
		if (maxHit <= 0) {
			return 0;
		}
		int roll = DataConversions.random(1, maxHit);
		if (source != null && source.getWindLowRollBiasChance() > 0.0D && roll > 1
			&& DataConversions.getRandom().nextDouble() < source.getWindLowRollBiasChance()) {
			roll -= 1;
		}
		if (source != null && source.getDamageRollHighBiasChance() > 0.0D && roll < maxHit
			&& DataConversions.getRandom().nextDouble() < source.getDamageRollHighBiasChance()) {
			roll += 1;
		}
		return roll;
	}

	private static int applyOutgoingMaxHitDebuff(final Mob source, final int attackMax) {
		if (source == null || attackMax <= 0 || source.getWaterMaxHitDebuffPercent() <= 0) {
			return attackMax;
		}
		return Math.max(1, (int) Math.floor(attackMax * source.getWaterMaxHitMultiplier()));
	}

	private static int rollNpcDamage(final Mob source, final int maxHit) {
		if (maxHit <= 0) {
			return 0;
		}
		double biasedRoll = Math.pow(DataConversions.getRandom().nextDouble(), 1.5D) * (maxHit + 1);
		int roll = Math.min(maxHit, (int) Math.floor(biasedRoll));
		if (source != null && source.getWindLowRollBiasChance() > 0.0D && roll > 1
			&& DataConversions.getRandom().nextDouble() < source.getWindLowRollBiasChance()) {
			roll -= 1;
		}
		return roll;
	}

	private static int applyMitigationRoll(final int attackMax, final int defenseMax) {
		return applyMitigationRoll(attackMax, defenseMax, false);
	}

	private static int applyMitigationRoll(final int attackMax, final int defenseMax, final boolean npcAttacker) {
		int offenseRoll = npcAttacker ? rollNpcDamage(null, attackMax) : rollDamage(null, attackMax);
		int defenseRoll = defenseMax <= 0 ? 0 : DataConversions.random(1, defenseMax);
		return Math.max(offenseRoll - defenseRoll, 0);
	}

	private static int applyMitigationRoll(final Mob source, final Mob victim, final int attackMax, final int defenseMax) {
		int offenseRoll = rollIncomingDamage(source, victim, attackMax);
		int defenseRoll = defenseMax <= 0 ? 0 : DataConversions.random(1, defenseMax);
		defenseRoll = maybeRerollDefenseRoll(victim, defenseRoll, defenseMax);
		return Math.max(offenseRoll - defenseRoll, 0);
	}

	private static int applyMitigationRoll(final Mob source, final Mob victim, final int attackMax, final int defenseMax, final int damageCap) {
		int offenseRoll = rollIncomingDamage(source, victim, attackMax);
		int defenseRoll = defenseMax <= 0 ? 0 : DataConversions.random(1, defenseMax);
		defenseRoll = maybeRerollDefenseRoll(victim, defenseRoll, defenseMax);
		return Math.max(Math.min(offenseRoll, damageCap) - defenseRoll, 0);
	}

	private static int rollIncomingDamage(final Mob source, final Mob victim, final int attackMax) {
		int rollCount = 1;
		if (victim instanceof Player) {
			rollCount += ((Player) victim).getLawRobeExtraDamageRolls();
		}
		int totalRoll = 0;
		for (int i = 0; i < rollCount; i++) {
			totalRoll += source != null && source.isNpc()
				? rollNpcDamage(source, attackMax)
				: rollDamage(source, attackMax);
		}
		return Math.max(0, totalRoll / Math.max(1, rollCount));
	}

	private static int maybeRerollDefenseRoll(final Mob victim, final int defenseRoll, final int defenseMax) {
		if (!(victim instanceof Player) || defenseMax <= 0) {
			return defenseRoll;
		}
		final double rerollChance = ((Player) victim).getCosmicRobeDefenseRerollChance();
		if (rerollChance <= 0.0D || DataConversions.getRandom().nextDouble() >= rerollChance) {
			return defenseRoll;
		}
		return Math.max(defenseRoll, DataConversions.random(1, defenseMax));
	}

	private static int applyDamageMultiplier(final Mob source, final int damage) {
		if (damage <= 0 || !(source instanceof Player)) {
			return damage;
		}
		final double multiplier = ((Player) source).getDeathAmuletDamageBonusMultiplier();
		if (multiplier <= 1.0D) {
			return damage;
		}
		return (int) Math.ceil(damage * multiplier);
	}

	/**
	 * Gets the melee max roll of the attacking mob.
	 * @param source             The attacking mob.
	 * @return The max hit
	 */
	private static int getMeleeDamage(final Mob source) {
		final int styleBonus = styleBonus(source, 2);
		final double prayerBonus = addPrayers(source, Prayers.BURST_OF_STRENGTH,
			Prayers.SUPERHUMAN_STRENGTH,
			Prayers.ULTIMATE_STRENGTH);

		final int bonusConstant = source.isPlayer() ? 8 : 0;
		final int damageSkill = CombatEffectUtil.remapLegacyPlayerMeleeStat(source, Skill.STRENGTH.id());
		final double maxRoll = (Math.floor(source.getSkills().getLevel(damageSkill) * prayerBonus) + bonusConstant + styleBonus) * (source.getWeaponPowerPoints() + 64);
		return (int)maxRoll;
	}

	/**
	 * Gets the ranged max roll of the attacking mob.
	 *
	 * @param source             The attacking mob.
	 * @return The max hit
	 */
	private static int getRangedDamage(final Mob source, final int bowId, final int arrowId) {
		final int bonusConstant = source.isPlayer() ? 8 : 0; //NPCs can't range authentically - this should be considered if custom content implements this.
		final int power = source.getConfig().RETRO_RANGED_DAMAGE ? rangedPowerRetro(bowId) : rangedPower(arrowId);
		final double maxRoll = (source.getSkills().getLevel(Skill.RANGED.id()) + bonusConstant) * (power + 1 + 64);
		return (int)maxRoll;
	}

	/**
	 * Gets the melee defence of the defending mob
	 *
	 * @param defender             The defending mob.
	 * @return The melee defence
	 */
	private static double getMeleeDefence(final Mob defender) {
		final int styleBonus = styleBonus(defender, 1);
		final double prayerBonus = addPrayers(defender, Prayers.THICK_SKIN,
			Prayers.ROCK_SKIN,
			Prayers.STEEL_SKIN);
		final int bonusConstant = defender.isPlayer() ? 8 : 0;
		final int defenseSkill = CombatEffectUtil.remapLegacyPlayerMeleeStat(defender, Skill.DEFENSE.id());
		final double defense = (Math.floor(defender.getSkills().getLevel(defenseSkill) * prayerBonus) + bonusConstant + styleBonus) * (defender.getArmourPoints() + 64);
		return defense;
	}


	/**
	 * Gets the ranged accuracy of the attacking mob
	 *
	 * @param attacker             The attacking mob.
	 * @param bowId				   The ranged weapon being wielded
	 * @return The ranged accuracy
	 */
	private static double getRangedAccuracy(final Mob attacker, final int bowId) {
		final int bonusConstant = attacker.isPlayer() ? 8 : 0; //NPCs can't range authentically - this should be considered if custom content implements this.
		return (attacker.getSkills().getLevel(Skill.RANGED.id()) + bonusConstant) * (rangedAim(bowId) + 1 + 64);
	}

	/**
	 * Gets the melee accuracy of the attacking mob
	 *
	 * @param attacker             The attacking mob.
	 * @return The melee accuracy
	 */
	private static double getMeleeAccuracy(final Mob attacker) {
		final int styleBonus = styleBonus(attacker, 0);
		final double prayerBonus = addPrayers(attacker, Prayers.CLARITY_OF_THOUGHT,
			Prayers.IMPROVED_REFLEXES,
			Prayers.INCREDIBLE_REFLEXES);

		final int bonusConstant = attacker.isPlayer() ? 8 : 0;
		final double accuracy = (Math.floor(attacker.getSkills().getLevel(Skill.ATTACK.id()) * prayerBonus) + bonusConstant + styleBonus) * (attacker.getWeaponAimPoints() + 64);

		return accuracy;
	}

	/**
	 * Gets the amount of skill points to be added for a specific skill based on style bonus
	 *
	 * @param attacker             The attacking mob.
	 * @return The amount of skill points to add for combat style
	 */
	protected static int styleBonus(final Mob attacker, final int skill) {
		if (attacker.isNpc())
			return 0;
		if (attacker.isPlayer() && ((Player) attacker).getConfig().WANT_MYWORLD) {
			return 0;
		}

		final int style = attacker.getCombatStyle();
		if (style == Skills.CONTROLLED_MODE)
			return 1;

		return (skill == Skill.ATTACK.id() && style == Skills.ACCURATE_MODE) || (skill == Skill.DEFENSE.id() && style == Skills.DEFENSIVE_MODE)
			|| (skill == Skill.STRENGTH.id() && style == Skills.AGGRESSIVE_MODE) ? 3 : 0;
	}

	/**
	 * Get the prayer multiplier for the context mob's skill
	 *
	 * @param source             The context mob.
	 * @return A multiplier to modify the context mob's relevant stat to the prayers.
	 */
	protected static double addPrayers(final Mob source, final int prayer1, final int prayer2, final int prayer3) {
		if (source.isPlayer()) {
			final Player sourcePlayer = (Player) source;
			if (sourcePlayer.getConfig().WANT_MYWORLD) {
				return 1.0D;
			}
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
		return 1.0D;
	}

	/**
	 * Returns a power to associate with each bow (pre-Fletching version)
	 *
	 * Uses values from the old projectile.txt file included with configXX.jag.
	 */
	private static int rangedPowerRetro(final int bowId) {
		switch (ItemId.getById(bowId)) {
			case SHORTBOW:
				return 14;
			case LONGBOW:
				return 20;
			case CROSSBOW:
			case PHOENIX_CROSSBOW:
				return 22;
			default:
				return 0;
		}
	}

	/**
	 * Returns a power to associate with each arrow (post-Fletching version)
	 */
	private static int rangedPower(final int arrowId) {
		/**
		 * We don't have good data for throwing knives,
		 * so everything besides rune knives is a guess based on
		 * arrows increasing by 5 per tier.
		 * Rune spear should be accurate since the stats were leaked.
		 * Note circa 14th May 2023: even this might be wrong now. The arrow data should now be pretty accurate, but thrown items may need re-review.
		 * All the values were scaled back by 5 based on extensive ranged data fitting into the new formula.
		 * Iron, steel, and adamantite darts are also guesses.
		 */
		switch (ItemId.getById(arrowId)) {
			case BRONZE_THROWING_DART:
			case POISONED_BRONZE_THROWING_DART:
			case BRONZE_ARROWS:
			case POISON_BRONZE_ARROWS:
				return 15;
			case IRON_THROWING_DART:
			case POISONED_IRON_THROWING_DART:
				return 17;
			case IRON_ARROWS:
			case POISON_IRON_ARROWS:
			case CROSSBOW_BOLTS:
			case POISON_CROSSBOW_BOLTS:
			case OYSTER_PEARL_BOLTS:
				return 20;
			case STEEL_THROWING_DART:
			case POISONED_STEEL_THROWING_DART:
				return 22;
			case STEEL_ARROWS:
			case POISON_STEEL_ARROWS:
			case MITHRIL_THROWING_DART:
			case POISONED_MITHRIL_THROWING_DART:
			case BRONZE_THROWING_KNIFE:
			case POISONED_BRONZE_THROWING_KNIFE:
				return 25;
			case ADAMANTITE_THROWING_DART:
			case POISONED_ADAMANTITE_THROWING_DART:
				return 27;
			case RUNE_THROWING_DART:
			case POISONED_RUNE_THROWING_DART:
			case MITHRIL_ARROWS:
			case POISON_MITHRIL_ARROWS:
			case IRON_THROWING_KNIFE:
			case POISONED_IRON_THROWING_KNIFE:
				return 30;
			case ADAMANTITE_ARROWS:
			case POISON_ADAMANTITE_ARROWS:
			case STEEL_THROWING_KNIFE:
			case POISONED_STEEL_THROWING_KNIFE:
			case BLACK_THROWING_KNIFE:
			case POISONED_BLACK_THROWING_KNIFE:
				return 35;
			case RUNE_ARROWS:
			case POISON_RUNE_ARROWS:
			case MITHRIL_THROWING_KNIFE:
			case POISONED_MITHRIL_THROWING_KNIFE:
				return 40;
			case ADAMANTITE_THROWING_KNIFE:
			case POISONED_ADAMANTITE_THROWING_KNIFE:
				return 45;
			case RUNE_THROWING_KNIFE:
			case POISONED_RUNE_THROWING_KNIFE:
			case DRAGON_ARROWS:
			case POISON_DRAGON_ARROWS:
			case DRAGON_BOLTS:
			case POISON_DRAGON_BOLTS:
				return 50;
			default:
				return 0;
		}
	}

	/**
	 * Returns an aim to associate with each ranged item
	 */
	private static int rangedAim(final int bowId) {
		/**
		 * We have limited pre-Fletching "aim" information for
		 * the shortbow, longbow, and crossbow in configXX.jag
		 * from 2001.
		 *
		 * We are using known information about how ranged weapon
		 * power scales by 5 per tier.
		 *
		 * We probably have a good guess for the base accuracy of
		 * darts.
		 *
		 * For spears and knives, we can only make wild guesses
		 * (people didn't throw enough of them). Rune spear was
		 * leaked.
		 */
		switch (ItemId.getById(bowId)) {
			case SHORTBOW:
				return 10;
			case CROSSBOW:
			case PHOENIX_CROSSBOW:
				return 12;
			case LONGBOW:
			case OAK_SHORTBOW:
				return 15;
			case WILLOW_SHORTBOW:
			case OAK_LONGBOW:
				return 20;
			case BRONZE_THROWING_DART:
			case POISONED_BRONZE_THROWING_DART:
			case MAPLE_SHORTBOW:
			case WILLOW_LONGBOW:
				return 25;
			case IRON_THROWING_DART:
			case POISONED_IRON_THROWING_DART:
			case BRONZE_THROWING_KNIFE:
			case POISONED_BRONZE_THROWING_KNIFE:
			case YEW_SHORTBOW:
			case MAPLE_LONGBOW:
				return 30;
			case STEEL_THROWING_DART:
			case POISONED_STEEL_THROWING_DART:
			case IRON_THROWING_KNIFE:
			case POISONED_IRON_THROWING_KNIFE:
			case MAGIC_SHORTBOW:
			case YEW_LONGBOW:
				return 35;
			case MITHRIL_THROWING_DART:
			case POISONED_MITHRIL_THROWING_DART:
			case BLACK_THROWING_KNIFE:
			case POISONED_BLACK_THROWING_KNIFE:
			case STEEL_THROWING_KNIFE:
			case POISONED_STEEL_THROWING_KNIFE:
			case MAGIC_LONGBOW:
			case DRAGON_CROSSBOW:
				return 40;
			case ADAMANTITE_THROWING_DART:
			case POISONED_ADAMANTITE_THROWING_DART:
			case MITHRIL_THROWING_KNIFE:
			case POISONED_MITHRIL_THROWING_KNIFE:
				return 45;
			case RUNE_THROWING_DART:
			case POISONED_RUNE_THROWING_DART:
			case ADAMANTITE_THROWING_KNIFE:
			case POISONED_ADAMANTITE_THROWING_KNIFE:
				return 50;
			case RUNE_THROWING_KNIFE:
			case POISONED_RUNE_THROWING_KNIFE:
				return 55;
			default:
				return 0;
		}
	}
}
