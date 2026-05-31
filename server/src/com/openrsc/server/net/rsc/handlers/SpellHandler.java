package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.database.impl.mysql.queries.logging.GenericLog;
import com.openrsc.server.event.MiniEvent;
import com.openrsc.server.event.rsc.impl.ObjectRemover;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.CustomProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.external.Gauntlets;
import com.openrsc.server.external.ItemSmeltingDef;
import com.openrsc.server.external.ReqOreDef;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.ActionType;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.action.WalkToPointAction;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.*;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcInteraction;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.struct.UnequipRequest;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import com.openrsc.server.plugins.SpellFailureException;
import com.openrsc.server.plugins.triggers.SpellInvTrigger;
import com.openrsc.server.plugins.triggers.SpellLocTrigger;
import com.openrsc.server.plugins.triggers.SpellNpcTrigger;
import com.openrsc.server.plugins.triggers.SpellPlayerTrigger;
import com.openrsc.server.util.rsc.CertUtil;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.Map.Entry;

import static com.openrsc.server.plugins.Functions.*;

public class SpellHandler implements PayloadProcessor<SpellStruct, OpcodeIn> {

	private static final String AMULET = "amulet";
	private static final String RING = "ring";
	private static final String NECKLACE = "necklace";
	private static final String CROWN = "crown";
	private static final String DEFAULT = "";
	private static final long HEAL_SPELL_COOLDOWN_MS = 10000L;
	private static final int TELEPORT_CHARGE_MS = 5000;

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private static boolean shouldMagicDebug(final Player player) {
		return false;
	}

	private static boolean isMobCastOpcode(final OpcodeIn opcode) {
		return opcode == OpcodeIn.CAST_ON_NPC || opcode == OpcodeIn.PLAYER_CAST_PVP;
	}

	private static String describeMob(final Mob mob) {
		if (mob == null) {
			return "none";
		}
		if (mob.isNpc()) {
			Npc npc = (Npc) mob;
			return "npc:" + npc.getID() + ":" + npc.getDef().getName() + "#" + npc.getIndex();
		}
		if (mob.isPlayer()) {
			return "player:" + ((Player) mob).getUsername() + "#" + mob.getIndex();
		}
		return "mob:" + mob.getID() + "#" + mob.getIndex();
	}

	private static String describeSpell(final Spells spellEnum, final SpellDef spell) {
		if (spell == null) {
			return String.valueOf(spellEnum);
		}
		return spellEnum + ":" + spell.getName() + ":type=" + spell.getSpellType() + ":req=" + spell.getReqLevel();
	}

	private static void magicDebug(final Player player, final String message) {
	}

	private static int getMagicId(Player player, SpellDef spell) {
		if (!player.getConfig().DIVIDED_GOOD_EVIL) {
			return Skill.MAGIC.id();
		} else {
			if (spell.isEvil()) {
				return Skill.EVILMAGIC.id();
			} else {
				return Skill.GOODMAGIC.id();
			}
		}
	}

	private static boolean canCast(Player player) {
		// Retro RSC mechanic, could rapid cast spells
		if (!player.castTimer(player.getConfig().RAPID_CAST_SPELLS)) {
			// spell timer audited, see #3199 or `flying sno/flying sno (originals only)/penuslarge1/07-11-2018 16.12.51 autocast magic on some guards for 2 hours`
			player.message("You need to wait " + player.getSpellWait() + " seconds before you can cast another spell");
			player.resetPath();
			return false;
		}
		return true;
	}

	/**
	 * Checks if player can cast spell
	 * @param player
	 * @param spell
	 * @param rollMagicCape
	 * @return The set of required runes that would be consumed or null if the next cast should be free due to Magic Cape
	 * @throws SpellFailureException when player lacks the required runes to cast spell
	 */
	public static Set<Entry<Integer, Integer>> checkSpellRunes(Player player, SpellDef spell, boolean rollMagicCape) throws SpellFailureException {
		if (rollMagicCape && SkillCapes.shouldActivate(player, ItemId.MAGIC_CAPE)) {
			player.message("You manage to cast the spell without using any runes");
			return null;
		}
		Set<Entry<Integer, Integer>> runesToConsume = new HashSet<>();
		Item equippedStaff = getEquippedStaff(player);

		for (Entry<Integer, Integer> e : spell.getRunesRequired()) {
			int amountToConsume = e.getValue();
			double preservationChance = getRuneNegationChance(player, equippedStaff, e.getKey());
			if (preservationChance > 0.0D && DataConversions.getRandom().nextDouble() < preservationChance) {
				amountToConsume = 0;
			}
			int availableRunes = player.getCarriedItems().getInventory().countId(e.getKey());
			if (availableRunes < amountToConsume) {
				player.setSuspiciousPlayer(true, "player not all reagents for spell");
				magicDebug(player, "runes_missing spell=" + spell.getName() + " rune=" + e.getKey()
					+ " need=" + amountToConsume + " have=" + availableRunes);
				player.message("You don't have all the reagents you need for this spell");
				throw new SpellFailureException("Player does not have all the reagents you need for this spell");
			}
			if (amountToConsume > 0) {
				runesToConsume.add(new AbstractMap.SimpleEntry<>(e.getKey(), amountToConsume));
			}
		}
		return runesToConsume;
	}

	public static boolean isAutoCastableSpell(Player player, Spells spellEnum, boolean message) {
		if (spellEnum == null) {
			if (message) {
				player.message("That spell cannot be auto-cast");
			}
			return false;
		}
		SpellDef spell = player.getWorld().getServer().getEntityHandler().getSpellDef(spellEnum);
		if (spell == null || spell.getSpellType() != 2) {
			if (message) {
				player.message("Only combat spells can be auto-cast");
			}
			return false;
		}
		if (spell.isMembers() && !player.getConfig().MEMBER_WORLD) {
			if (message) {
				player.message("You need to login to a members world to use this spell");
			}
			return false;
		}
		if (player.getSkills().getLevel(getMagicId(player, spell)) < spell.getReqLevel()) {
			if (message) {
				player.message("Your magic ability is not high enough for this spell.");
			}
			return false;
		}
		return true;
	}

	public static boolean hasRequiredRunesForAutoCast(Player player, SpellDef spell) {
		Item equippedStaff = getEquippedStaff(player);
		for (Entry<Integer, Integer> e : spell.getRunesRequired()) {
			double preservationChance = getRuneNegationChance(player, equippedStaff, e.getKey());
			int amountToConsume = preservationChance >= 1.0D ? 0 : e.getValue();
			if (amountToConsume > 0 && player.getCarriedItems().getInventory().countId(e.getKey()) < amountToConsume) {
				return false;
			}
		}
		return true;
	}

	public static void queueAutoCastCombatSpell(final Player player, final Mob affectedMob, final Spells spellEnum) {
		new SpellHandler().queueAutoCastCombatSpellInternal(player, affectedMob, spellEnum);
	}

	private void queueAutoCastCombatSpellInternal(final Player player, final Mob affectedMob, final Spells spellEnum) {
		if (player == null || affectedMob == null || affectedMob.isRemoved()) {
			return;
		}
		if ((player.isBusy() && !player.inCombat()) || player.isRanging()) {
			return;
		}
		if (!canCast(player)) {
			return;
		}
		OpcodeIn opcode = affectedMob.isPlayer() ? OpcodeIn.PLAYER_CAST_PVP : OpcodeIn.CAST_ON_NPC;
		SpellDef spell = spellSanityChecks(spellEnum, player, opcode);
		if (spell == null || spell.getSpellType() != 2) {
			return;
		}
		if (!spellSuccessCheck(player, spell)) {
			return;
		}
		player.resetAllExceptDueling();
		if (affectedMob.isPlayer()) {
			if (checkCastOnPlayer(player, (Player) affectedMob, spellEnum)) {
				return;
			}
		} else if (affectedMob.isNpc()) {
			if (checkCastOnNpc(player, (Npc) affectedMob, spell)) {
				return;
			}
		}
		handleMobCast(player, affectedMob, spellEnum, spell.getSpellType());
	}

	private static double getRuneNegationChance(final Player player, final Item equippedStaff, final int runeId) {
		double preservationChance = 0.0D;
		preservationChance += player.getCarriedItems().getEquipment().getWoolRobeRunePreservationChance(runeId);
		if (equippedStaff != null) {
			preservationChance += EnchantingItemEffects.getStaffRunePreservationChance(equippedStaff.getCatalogId(), runeId);
		}
		return Math.min(1.0D, preservationChance);
	}

	private static Item getEquippedNecklace(Player player) {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			return player.getCarriedItems().getEquipment().getNeckItem();
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item.isWielded()
					&& item.getDef(player.getWorld()).getWieldPosition() == Equipment.EquipmentSlot.SLOT_NECK.getIndex()) {
					return item;
				}
			}
		}
		return null;
	}

	private static Item getEquippedRing(Player player) {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			return player.getCarriedItems().getEquipment().getRingItem();
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item.isWielded()
					&& item.getDef(player.getWorld()).getWieldPosition() == Equipment.EquipmentSlot.SLOT_RING.getIndex()) {
					return item;
				}
			}
		}
		return null;
	}

	private static double applyElementalRingDamageBonus(Player player, Spells spellEnum, double baseMax) {
		return baseMax;
	}

	private static boolean doesRingMatchSpellElement(final int ringId, final Spells spellEnum) {
		if (spellEnum == null) {
			return false;
		}
		if (ringId >= 1673 && ringId <= 1677) {
			return isAirSpell(spellEnum);
		}
		if (ringId >= 1678 && ringId <= 1682) {
			return isWaterSpell(spellEnum);
		}
		if (ringId >= 1683 && ringId <= 1687) {
			return isEarthSpell(spellEnum);
		}
		if (ringId >= 1688 && ringId <= 1692) {
			return isFireSpell(spellEnum);
		}
		return false;
	}

	private static boolean isAirSpell(final Spells spellEnum) {
		return spellEnum == Spells.WIND_STRIKE
			|| spellEnum == Spells.WIND_BOLT
			|| spellEnum == Spells.WIND_BOLT_R
			|| spellEnum == Spells.WIND_BLAST
			|| spellEnum == Spells.WIND_WAVE;
	}

	private static boolean isWaterSpell(final Spells spellEnum) {
		return spellEnum == Spells.WATER_STRIKE
			|| spellEnum == Spells.WATER_BOLT
			|| spellEnum == Spells.WATER_BLAST
			|| spellEnum == Spells.WATER_WAVE;
	}

	private static boolean isEarthSpell(final Spells spellEnum) {
		return spellEnum == Spells.EARTH_STRIKE
			|| spellEnum == Spells.EARTH_BOLT
			|| spellEnum == Spells.EARTH_BLAST
			|| spellEnum == Spells.EARTH_WAVE;
	}

	private static boolean isFireSpell(final Spells spellEnum) {
		return spellEnum == Spells.FIRE_STRIKE
			|| spellEnum == Spells.FIRE_BOLT
			|| spellEnum == Spells.FIRE_BLAST
			|| spellEnum == Spells.FIRE_WAVE;
	}

	private static boolean isMindSpell(final Spells spellEnum) {
		return spellEnum == Spells.WIND_STRIKE
			|| spellEnum == Spells.WATER_STRIKE
			|| spellEnum == Spells.EARTH_STRIKE
			|| spellEnum == Spells.FIRE_STRIKE
			|| spellEnum == Spells.THUNDER_BALL
			|| spellEnum == Spells.ICICLE_SHOT
			|| spellEnum == Spells.ACID_DROP
			|| spellEnum == Spells.BRANCH_SPORE;
	}

	private static boolean isThunderSpell(final Spells spellEnum) {
		return spellEnum == Spells.THUNDER_BALL
			|| spellEnum == Spells.THUNDER_SPLASH
			|| spellEnum == Spells.THUNDER_STRIKE;
	}

	private static boolean isAcidSpell(final Spells spellEnum) {
		return spellEnum == Spells.ACID_DROP
			|| spellEnum == Spells.ACID_FROG
			|| spellEnum == Spells.ACID_GUSH;
	}

	private static boolean isIceSpell(final Spells spellEnum) {
		return spellEnum == Spells.ICICLE_SHOT
			|| spellEnum == Spells.ICE_BURST
			|| spellEnum == Spells.ICE_CRYSTAL;
	}

	private static boolean isWoodSpell(final Spells spellEnum) {
		return spellEnum == Spells.BRANCH_SPORE
			|| spellEnum == Spells.WOOD_DRILL
			|| spellEnum == Spells.BATTERING_RAM;
	}

	private static int getSpellProjectileVisual(final Spells spellEnum) {
		if (spellEnum == null) {
			return Projectile.MAGIC;
		}
		switch (spellEnum) {
			case WIND_STRIKE:
				return Projectile.WIND_ARROW;
			case EARTH_STRIKE:
				return Projectile.ROCK_THROW;
			case WATER_STRIKE:
				return Projectile.WATER_BALL;
			case FIRE_STRIKE:
				return Projectile.FIREBALL;
			case THUNDER_BALL:
				return Projectile.THUNDER_BALL;
			case ICICLE_SHOT:
				return Projectile.ICICLE_SHOT;
			case ACID_DROP:
				return Projectile.ACID_DROP;
			case BRANCH_SPORE:
				return Projectile.BRANCH_SPORE;
			default:
				return Projectile.MAGIC;
		}
	}

	private static int getSpellImpactEffect(final Spells spellEnum) {
		if (spellEnum == null) {
			return 0;
		}
		switch (spellEnum) {
			case WIND_BOLT:
				return CombatEffect.WIND_SLASH;
			case EARTH_BOLT:
				return CombatEffect.EARTH_HAMMER;
			case WATER_BOLT:
				return CombatEffect.WATER_BURST;
			case FIRE_BOLT:
				return CombatEffect.FIRE_CLAW;
			case THUNDER_SPLASH:
				return CombatEffect.THUNDER_SPLASH;
			case ICE_BURST:
				return CombatEffect.ICE_BURST;
			case ACID_FROG:
				return CombatEffect.ACID_FROG;
			case WOOD_DRILL:
				return CombatEffect.WOOD_DRILL;
			case WIND_BLAST:
				return CombatEffect.TORNADO;
			case EARTH_BLAST:
				return CombatEffect.EARTH_BURST;
			case WATER_BLAST:
				return CombatEffect.WATER_ERUPTION;
			case FIRE_BLAST:
				return CombatEffect.EXPLOSION;
			case THUNDER_STRIKE:
				return CombatEffect.THUNDER_STRIKE;
			case ICE_CRYSTAL:
				return CombatEffect.ICE_CRYSTAL;
			case ACID_GUSH:
				return CombatEffect.ACID_GUSH;
			case BATTERING_RAM:
				return CombatEffect.BATTERING_RAM;
			case WIND_WAVE:
				return CombatEffect.WIND_BEAM;
			case EARTH_WAVE:
				return CombatEffect.EARTH_IMPALE;
			case WATER_WAVE:
				return CombatEffect.WATER_VORTEX;
			case FIRE_WAVE:
				return CombatEffect.FIRE_PILLAR;
			default:
				return 0;
		}
	}

	private static int getGodSpellProjectileVisual(final Spells spellEnum) {
		return Projectile.MAGIC;
	}

	private static int getGodSpellImpactEffect(final Spells spellEnum) {
		if (spellEnum == Spells.CLAWS_OF_GUTHIX) {
			return CombatEffect.EYE_OF_GUTHIX;
		}
		if (spellEnum == Spells.CLAW_OF_GUTHIX) {
			return CombatEffect.CLAW_OF_GUTHIX;
		}
		if (spellEnum == Spells.SARADOMIN_STRIKE) {
			return CombatEffect.SARADOMIN_STRIKE;
		}
		if (spellEnum == Spells.SARADOMIN_SOUL_SLASH) {
			return CombatEffect.SARADOMIN_SOUL_SLASH;
		}
		if (spellEnum == Spells.FLAMES_OF_ZAMORAK) {
			return CombatEffect.ZAMORAKS_VOID;
		}
		if (spellEnum == Spells.ZAMORAKS_APOCOLYPSE) {
			return CombatEffect.ZAMORAKS_APOCOLYPSE;
		}
		return 0;
	}

	private static int getHealCombatEffect(final Spells spellEnum) {
		if (spellEnum == Spells.WEAK_HEAL) {
			return CombatEffect.LESSER_HEAL;
		}
		if (spellEnum == Spells.STRONG_HEAL) {
			return CombatEffect.GREATER_HEAL;
		}
		return 0;
	}

	private static String getGodSpellCastCacheKey(final Spells spellEnum, final SpellDef spell) {
		if (isGuthixGodSpell(spellEnum)) {
			return "Claws of Guthix_casts";
		}
		if (isSaradominGodSpell(spellEnum)) {
			return "Saradomin strike_casts";
		}
		if (isZamorakGodSpell(spellEnum)) {
			return "Flames of Zamorak_casts";
		}
		return spell.getName() + "_casts";
	}

	private static boolean isGuthixGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.CLAWS_OF_GUTHIX || spellEnum == Spells.CLAW_OF_GUTHIX;
	}

	private static boolean isSaradominGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.SARADOMIN_STRIKE || spellEnum == Spells.SARADOMIN_SOUL_SLASH;
	}

	private static boolean isZamorakGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.FLAMES_OF_ZAMORAK || spellEnum == Spells.ZAMORAKS_APOCOLYPSE;
	}

	private static boolean isAdvancedGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.ZAMORAKS_APOCOLYPSE
			|| spellEnum == Spells.SARADOMIN_SOUL_SLASH
			|| spellEnum == Spells.CLAW_OF_GUTHIX;
	}

	private static int getElementalSpellTier(final Spells spellEnum) {
		final double capPercent = getSpellDamageCapPercent(spellEnum);
		if (capPercent <= 0.40D) {
			return 1;
		}
		if (capPercent <= 0.60D) {
			return 2;
		}
		if (capPercent <= 0.80D) {
			return 3;
		}
		return 4;
	}

	private static int getWindAccuracyDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 5;
	}

	private static int getWaterMaxHitDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 5;
	}

	private static int getEarthAttackSpeedDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 3;
	}

	private static int getFireDefenseDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 3;
	}

	private static int getDualElementProcChancePercent(final Spells spellEnum) {
		switch (getElementalSpellTier(spellEnum)) {
			case 1:
				return 7;
			case 2:
				return 15;
			case 3:
				return 25;
			default:
				return 0;
		}
	}

	private static int getStartleProcChancePercent(final Spells spellEnum) {
		return isThunderSpell(spellEnum) ? getDualElementProcChancePercent(spellEnum) : 0;
	}

	private static int getAcidPoisonPower(final Spells spellEnum) {
		return isAcidSpell(spellEnum) ? 10 + (getElementalSpellTier(spellEnum) * 10) : 0;
	}

	private static int getFrostbiteProcChancePercent(final Spells spellEnum) {
		return isIceSpell(spellEnum) ? getDualElementProcChancePercent(spellEnum) : 0;
	}

	private static int getSplinterProcChancePercent(final Spells spellEnum) {
		return isWoodSpell(spellEnum) ? getDualElementProcChancePercent(spellEnum) : 0;
	}

	private static double getSpellDamageCapPercent(final Spells spellEnum) {
		if (spellEnum == null) {
			return 1.0D;
		}
		switch (spellEnum) {
			case WIND_STRIKE:
			case WATER_STRIKE:
			case EARTH_STRIKE:
			case FIRE_STRIKE:
			case THUNDER_BALL:
			case ICICLE_SHOT:
			case ACID_DROP:
			case BRANCH_SPORE:
				return 0.40D;
			case WIND_BOLT:
			case WATER_BOLT:
			case EARTH_BOLT:
			case FIRE_BOLT:
			case THUNDER_SPLASH:
			case ICE_BURST:
			case ACID_FROG:
			case WOOD_DRILL:
				return 0.60D;
			case WIND_BLAST:
			case WATER_BLAST:
			case EARTH_BLAST:
			case FIRE_BLAST:
			case THUNDER_STRIKE:
			case ICE_CRYSTAL:
			case ACID_GUSH:
			case BATTERING_RAM:
				return 0.80D;
			case WIND_WAVE:
			case WATER_WAVE:
			case EARTH_WAVE:
			case FIRE_WAVE:
				return 1.0D;
			default:
				return 1.0D;
		}
	}

	public static boolean checkAndRemoveRunes(Player player, SpellDef spell) {
		// check also against magic cape activation
		return checkAndRemoveRunes(player, spell, null);
	}

	public static boolean checkAndRemoveRunes(Player player, SpellDef spell, Boolean magicCapeActivated) {
		if (magicCapeActivated == null || !magicCapeActivated) {
			try {
				Set<Entry<Integer, Integer>> runesToConsume = checkSpellRunes(player, spell, magicCapeActivated == null);
				if (runesToConsume != null) {
					// remove now all the runes needed to be consumed
					for (Entry<Integer, Integer> r : runesToConsume) {
						player.getCarriedItems().remove(new Item(r.getKey(), r.getValue()));
					}
				}
			} catch (SpellFailureException re) {
				// cape did not activate and
				// player does not have all runes
				// message already displayed in checkSpellRunes
				return false;
			}
		}

		return true;
	}

	private static Item getEquippedStaff(final Player player) {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			return player.getCarriedItems().getEquipment().get(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex());
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (!item.isWielded()) {
					continue;
				}
				if (item.getDef(player.getWorld()).getWieldPosition() != Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex()) {
					continue;
				}
				return item;
			}
		}
		return null;
	}

	// Check all prohibiting factors that would prevent spell from being cast
	// If all good, return correct spell
	private SpellDef spellSanityChecks(Spells spellEnum, Player player, OpcodeIn opcode) {
		if (spellEnum == null) {
			magicDebug(player, "sanity_reject reason=missing_spell opcode=" + opcode + " enum=null");
			return null;
		}
		SpellDef spell = player.getWorld().getServer().getEntityHandler().getSpellDef(spellEnum);

		if (spell == null) {
			magicDebug(player, "sanity_reject reason=missing_spell opcode=" + opcode + " enum=" + spellEnum);
			return null;
		}

		if (spell.isMembers() && !player.getConfig().MEMBER_WORLD) {
			magicDebug(player, "sanity_reject reason=members spell=" + describeSpell(spellEnum, spell));
			player.message("You need to login to a members world to use this spell");
			player.resetPath();
			return null;
		}

		// Services.lookup(DatabaseManager.class).addQuery(new
		// GenericLog(player.getUsername() + " tried to cast spell 49 at " +
		// player.getLocation()));

		// Check player's magic level prior to allowing cast.
		if (player.getSkills().getLevel(getMagicId(player, spell)) < spell.getReqLevel()) {
			magicDebug(player, "sanity_reject reason=level spell=" + describeSpell(spellEnum, spell)
				+ " current=" + player.getSkills().getLevel(getMagicId(player, spell)));
			player.setSuspiciousPlayer(true, "player magic ability not high enough");
			player.message("Your magic ability is not high enough for this spell.");
			player.resetPath();
			return null;
		}

		// Ensure player is allowed to teleport.
		if (opcode == OpcodeIn.CAST_ON_SELF && spell.getSpellType() == 0
			&& isTeleportSpell(spellEnum) && !canTeleport(player, spell, spellEnum)) {
			magicDebug(player, "sanity_reject reason=teleport spell=" + describeSpell(spellEnum, spell));
			return null;
		}

		// You can't cast on things other than your opponent, while in a duel.
		if (opcode != OpcodeIn.PLAYER_CAST_PVP && player.getDuel().isDuelActive()) {
			magicDebug(player, "sanity_reject reason=duel spell=" + describeSpell(spellEnum, spell));
			player.message("You can't do that during a duel!");
			return null;
		}

		if (isMobCastOpcode(opcode)) {
			magicDebug(player, "sanity_ok opcode=" + opcode + " spell=" + describeSpell(spellEnum, spell));
		}
		return spell;
	}

	private boolean spellSuccessCheck(Player player, SpellDef spell) {
		return true;
	}

	public void process(SpellStruct payload, Player player) throws Exception {
		OpcodeIn opcode = payload.getOpcode();
		if (isMobCastOpcode(opcode)) {
			magicDebug(player, "packet_recv opcode=" + opcode + " spell=" + payload.spell
				+ " targetIndex=" + payload.targetIndex + " busy=" + player.isBusy()
				+ " inCombat=" + player.inCombat() + " ranging=" + player.isRanging()
				+ " customClient=" + player.isUsingCustomClient());
		}
		if ((player.isBusy() && !player.inCombat()) || player.isRanging()) {
			if (isMobCastOpcode(opcode)) {
				magicDebug(player, "packet_reject reason=busy_or_ranging busy=" + player.isBusy()
					+ " inCombat=" + player.inCombat() + " ranging=" + player.isRanging());
			}
			return;
		}
		if (!isHealSpell(payload.spell) && !canCast(player)) {
			if (isMobCastOpcode(opcode)) {
				magicDebug(player, "packet_reject reason=cast_timer wait=" + player.getSpellWait());
			}
			return;
		}

		if (opcode == null) {
			magicDebug(player, "packet_reject reason=null_opcode spell=" + payload.spell);
			return;
		}

		if (opcode == OpcodeIn.PLAYER_CAST_PVP && !player.getConfig().WANT_PVP) {
			player.message(player.getConfig().WANT_MYWORLD
				? "This is a PvM-only world"
				: "You can't attack other players on this world");
			player.resetPath();
			return;
		}

		if (opcode == OpcodeIn.CAST_ON_INVENTORY_ITEM
			&& (player.getTrade().isTradeActive() || (player.getDuel().isDuelActive() && !player.inCombat()))) {
			// prevent of changing inventory items via magic during trade & duels windows
			return;
		}

		final Mob selfHealRetaliationTarget = getSelfHealRetaliationTarget(player, payload);
		player.resetAllExceptDueling();

		if (!player.isUsingCustomClient()) {
			//int idx = Constants.spellMap.getOrDefault(payload.spell, 0);
			SpellDef spell;

			switch (opcode) {
				case CAST_ON_SELF:
					spell = spellSanityChecks(payload.spell, player, opcode);
					if (spell == null) {
						return;
					}
					if (!spellSuccessCheck(player, spell)) {
						return;
					}

					if (spell.getSpellType() == 0 && isTeleportSpell(payload.spell)) {
						handleTeleport(player, spell, payload.spell);
						return;
					} else if (isHealSpell(payload.spell)) {
						handleHeal(player, spell, payload.spell, selfHealRetaliationTarget);
						return;
					} else if (isBoostSpell(player, payload.spell)) {
						handleBoost(player, spell, payload.spell);
						return;
					}
					handleGroundCast(player, spell, payload.spell);
					break;
				case PLAYER_CAST_PVP:
					Player affectedPlayer = player.getWorld().getPlayer(payload.targetIndex);

					spell = spellSanityChecks(payload.spell, player, opcode);
					if (spell == null) {
						return;
					}

					if (!spellSuccessCheck(player, spell)) {
						return;
					}

					if (spell.getSpellType() == 1 || spell.getSpellType() == 2) {

						if (affectedPlayer == null) {
							player.resetPath();
							return;
						}

						if (checkCastOnPlayer(player, affectedPlayer, payload.spell)) return;

						handleMobCast(player, affectedPlayer, payload.spell, spell.getSpellType());
					}
					break;
				case CAST_ON_NPC:
					Npc affectedNpc = player.getWorld().getNpc(payload.targetIndex);

					spell = spellSanityChecks(payload.spell, player, opcode);
					if (spell == null) {
						return;
					}
					if (!spellSuccessCheck(player, spell)) {
						return;
					}

					if (spell.getSpellType() == 2) {

						if (affectedNpc == null) {
							magicDebug(player, "npc_cast_reject reason=missing_target targetIndex=" + payload.targetIndex
								+ " spell=" + describeSpell(payload.spell, spell));
							player.resetPath();
							return;
						}

						magicDebug(player, "npc_cast_target_ok target=" + describeMob(affectedNpc)
							+ " spell=" + describeSpell(payload.spell, spell));

						if (checkCastOnNpc(player, affectedNpc, spell)) {
							magicDebug(player, "npc_cast_reject reason=plugin_or_special_handled target=" + describeMob(affectedNpc)
								+ " spell=" + describeSpell(payload.spell, spell));
							return;
						}

						handleMobCast(player, affectedNpc, payload.spell, spell.getSpellType());
					} else {
						magicDebug(player, "npc_cast_reject reason=wrong_spell_type spell="
							+ describeSpell(payload.spell, spell));
					}
					break;
				case CAST_ON_INVENTORY_ITEM:
					// Have to throw in ugly exceptions for curse and enfeeble
					boolean runecraft = player.getConfig().WANT_RUNECRAFT;

					int invIndex = payload.targetIndex;
					Item item = player.getCarriedItems().getInventory().get(invIndex);

					spell = spellSanityChecks(payload.spell, player, opcode);
					if (spell == null) {
						return;
					}
					if (!spellSuccessCheck(player, spell)) {
						return;
					}

					if (spell.getSpellType() == 3
						|| (runecraft && (payload.spell == Spells.CURSE || payload.spell == Spells.ENFEEBLE))) {

						if (item == null) {
							player.resetPath();
							return;
						}

						// Swap these lines to allow alchemy on notes.
						/*if ((idx == 10 || idx == 28) && item.getNoted()) {*/
						if (item.getNoted()) {
							player.message("Nothing interesting happens");
							return;
						}

						// Attempt to find a spell in a plugin, otherwise use this file.
						if (player.getWorld().getServer().getPluginHandler().handlePlugin(SpellInvTrigger.class, player,
							new Object[]{player, invIndex, item.getCatalogId(), payload.spell})) {
							return;
						}
						handleItemCast(player, spell, payload.spell, item);
					}
					break;
				case CAST_ON_BOUNDARY:
					/* TODO:
					  -- 180c -- CLIENT_OPCODE_CAST_ON_BOUNDARY
					  elseif (clientOpcodeValue == 180) then
						-- standalone, doesn't require data from other opcodes
						opcodeField:add(clientCastOnBoundaryXCoord, buffer(1, 2))
						opcodeField:add(clientCastOnBoundaryYCoord, buffer(3, 2))
						opcodeField:add(clientCastOnBoundaryAlignment, buffer(5, 1))
						local spellField = opcodeField:add(clientCastOnGroundSpell, buffer(6, 2))
					 */
					player.message("@or1@This type of spell is not yet implemented.");
					break;
				case CAST_ON_SCENERY:
					int objectX = payload.targetCoord.getX();
					int objectY = payload.targetCoord.getY();

					spell = spellSanityChecks(payload.spell, player, opcode);
					if (spell == null) {
						return;
					}
					if (!spellSuccessCheck(player, spell)) {
						return;
					}

					GameObject gameObject = player.getViewArea().getGameObject(Point.location(objectX, objectY));
					if (gameObject == null) {
						return;
					}

					if (player.getWorld().getServer().getPluginHandler().handlePlugin(SpellLocTrigger.class, player,
						new Object[]{player, gameObject, spell})) {
						return;
					}

					handleChargeOrb(player, gameObject, payload.spell, spell);
					break;
				case CAST_ON_GROUND_ITEM:
					Point location = Point.location(payload.targetCoord.getX(), payload.targetCoord.getY());
					int itemId = payload.targetIndex;

					spell = spellSanityChecks(payload.spell, player, opcode);
					if (spell == null) {
						return;
					}
					if (!spellSuccessCheck(player, spell)) {
						return;
					}

					GroundItem affectedItem = player.getViewArea().getVisibleGroundItem(itemId, location, player);
					if (affectedItem == null) {
						return;
					}
					handleItemCast(player, spell, payload.spell, affectedItem);
					break;
				case CAST_ON_LAND:
					Point locationLand = Point.location(payload.targetCoord.getX(), payload.targetCoord.getY());
					spell = spellSanityChecks(payload.spell, player, opcode);
					if (spell == null) {
						return;
					}
					if (!spellSuccessCheck(player, spell)) {
						return;
					}

					handleGroundCast(player, spell, payload.spell);
					break;
				default:
					LOGGER.error("Wrong OPCODE passed to Spell Handler.");
					break;
			}

		} else {
			// Inauthentic client conveniently places Spell ID at the front for all Spell related packets.
			//int idx = Constants.spellMap.get(payload.spell);

			SpellDef spell = spellSanityChecks(payload.spell, player, opcode);
			if (spell == null) {
				return;
			}

			if (!spellSuccessCheck(player, spell)) {
				return;
			}

			switch (opcode) {
				case CAST_ON_SELF:
					if (spell.getSpellType() == 0 && isTeleportSpell(payload.spell)) {
						handleTeleport(player, spell, payload.spell);
						return;
					} else if (isHealSpell(payload.spell)) {
						handleHeal(player, spell, payload.spell, selfHealRetaliationTarget);
						return;
					} else if (isBoostSpell(player, payload.spell)) {
						handleBoost(player, spell, payload.spell);
						return;
					}
					handleGroundCast(player, spell, payload.spell);
					break;
				case PLAYER_CAST_PVP:
					if (spell.getSpellType() == 1 || spell.getSpellType() == 2) {
						Player affectedPlayer = player.getWorld().getPlayer(payload.targetIndex);
						if (affectedPlayer == null) {
							player.resetPath();
							return;
						}

						if (checkCastOnPlayer(player, affectedPlayer, payload.spell)) return;

						handleMobCast(player, affectedPlayer, payload.spell, spell.getSpellType());
					}
					break;
				case CAST_ON_NPC:
					if (spell.getSpellType() == 2) {
						Npc affectedNpc = player.getWorld().getNpc(payload.targetIndex);
						if (affectedNpc == null) {
							magicDebug(player, "npc_cast_reject reason=missing_target targetIndex=" + payload.targetIndex
								+ " spell=" + describeSpell(payload.spell, spell));
							player.resetPath();
							return;
						}

						magicDebug(player, "npc_cast_target_ok target=" + describeMob(affectedNpc)
							+ " spell=" + describeSpell(payload.spell, spell));

						if (checkCastOnNpc(player, affectedNpc, spell)) {
							magicDebug(player, "npc_cast_reject reason=plugin_or_special_handled target=" + describeMob(affectedNpc)
								+ " spell=" + describeSpell(payload.spell, spell));
							return;
						}

						handleMobCast(player, affectedNpc, payload.spell, spell.getSpellType());
					} else {
						magicDebug(player, "npc_cast_reject reason=wrong_spell_type spell="
							+ describeSpell(payload.spell, spell));
					}
					break;
				case CAST_ON_INVENTORY_ITEM:
					// Have to throw in ugly exceptions for curse and enfeeble
					boolean runecraft = player.getConfig().WANT_RUNECRAFT;

					if (spell.getSpellType() == 3
						|| (runecraft && (payload.spell == Spells.CURSE || payload.spell == Spells.ENFEEBLE))) {

						int invIndex = payload.targetIndex;
						Item item = player.getCarriedItems().getInventory().get(invIndex);
						if (item == null) {
							player.resetPath();
							return;
						}

						// Swap these lines to allow alchemy on notes.
						/*if ((idx == 10 || idx == 28) && item.getNoted()) {*/
						if (item.getNoted()) {
							player.message("Nothing interesting happens");
							return;
						}

						// Attempt to find a spell in a plugin, otherwise use this file.
						if (player.getWorld().getServer().getPluginHandler().handlePlugin(SpellInvTrigger.class, player,
							new Object[]{player, invIndex, item.getCatalogId(), payload.spell})) {
							return;
						}
						handleItemCast(player, spell, payload.spell, item);
					}
					break;
				case CAST_ON_BOUNDARY:
					player.message("@or1@This type of spell is not yet implemented.");
					break;
				case CAST_ON_SCENERY:
					int objectX = payload.targetCoord.getX();
					int objectY = payload.targetCoord.getY();
					GameObject gameObject = player.getViewArea().getGameObject(Point.location(objectX, objectY));
					if (gameObject == null) {
						return;
					}

					if (player.getWorld().getServer().getPluginHandler().handlePlugin(SpellLocTrigger.class, player,
						new Object[]{player, gameObject, spell})) {
						return;
					}

					handleChargeOrb(player, gameObject, payload.spell, spell);
					break;
				case CAST_ON_GROUND_ITEM:
					Point location = Point.location(payload.targetCoord.getX(), payload.targetCoord.getY());
					int itemId = payload.targetIndex;
					GroundItem affectedItem = player.getViewArea().getVisibleGroundItem(itemId, location, player);
					if (affectedItem == null) {
						return;
					}
					handleItemCast(player, spell, payload.spell, affectedItem);
					break;
				case CAST_ON_LAND:
					Point locationLand = Point.location(payload.targetCoord.getX(), payload.targetCoord.getY());
					handleGroundCast(player, spell, payload.spell);
					break;
				default:
					LOGGER.error("Wrong OPCODE passed to Spell Handler.");
					break;
			}
		}
	}

	private boolean checkCastOnPlayer(Player player, Player affectedPlayer, Spells spellEnum) {
		// Duel with "No Magic" selected.
		if (player.getDuel().isDuelActive() && player.getDuel().getDuelSetting(1)) {
			player.message("Magic cannot be used during this duel!");
			return true;
		}

		// Note: Blocking magic casts near mage arena is inauthentic
		// see [Logg/Tylerbeg/08-05-2018 13.53.26 more pvp mechanics slash bugs with zephyr]
		// Only ranged & melee are authentically blocked in that safespot.

		// Stop the player if they are close enough to their opponent.
		if (player.withinRange(affectedPlayer, player.getConfig().SPELL_RANGE_DISTANCE + RangeUtils.PLAYER_COMBAT_RANGE_BONUS)) {
			player.resetPath();
		}

		return player.getWorld().getServer().getPluginHandler()
				.handlePlugin(SpellPlayerTrigger.class, player, new Object[]{player, affectedPlayer, spellEnum});
	}

	private boolean checkCastOnNpc(Player player, Npc affectedNpc, SpellDef spell) {
		NpcInteraction interaction = NpcInteraction.NPC_CAST_SPELL;

		// Demon Slayer
		if (affectedNpc.getID() == NpcId.DELRITH.id()) {
			player.message("Delrith can not be attacked without the Silverlight sword");
			return true;
		}

		// Temple of Ikov
		if (affectedNpc.getID() == NpcId.LUCIEN_EDGE.id() && (player.getQuestStage(Quests.TEMPLE_OF_IKOV) == -1
				|| player.getQuestStage(Quests.TEMPLE_OF_IKOV) == -2)) {
			player.message("You have already completed this quest");
			return true;
		}
		if (affectedNpc.getID() == NpcId.LUCIEN_EDGE.id() && !player.getCarriedItems().getEquipment().hasEquipped(ItemId.PENDANT_OF_ARMADYL.id())) {
			npcsay(player, affectedNpc, "I'm sure you don't want to attack me really",
					"I am your friend");
			mes("You decide you don't want to attack Lucien really");
			delay(3);
			mes("He is your friend");
			delay(3);
			return true;
		}

		// Stop movement if the player is within range.
		if (player.withinRange(affectedNpc, player.getConfig().SPELL_RANGE_DISTANCE + RangeUtils.PLAYER_COMBAT_RANGE_BONUS)) {
			player.resetPath();
		}

		// Family Crest
		if (affectedNpc.getID() == NpcId.CHRONOZON.id()) {
			if (spell.getName().contains("blast")) {
				String elementalType = spell.getName().split(" ")[0].toLowerCase();
				player.message("chronozon weakens");
				if (!player.getAttribute("chronoz_" + elementalType, false)) {
					player.setAttribute("chronoz_" + elementalType, true);
				}
			}
		}

		NpcInteraction.setInteractions(affectedNpc, player, interaction);

		return player.getWorld().getServer().getPluginHandler()
				.handlePlugin(SpellNpcTrigger.class, player, new Object[]{player, affectedNpc});
	}

	private void finalizeSpellNoMessage(Player player, SpellDef spell) {
		SpellHandler.finalizeSpell(player, spell, null);
	}

	public static void finalizeSpell(Player player, SpellDef spell, String message) {
		finalizeSpell(player, spell, message, true);
	}

	public static void finalizeSpell(Player player, SpellDef spell, String message, boolean giveExp) {
		finalizeSpell(player, spell, message, giveExp, true);
	}

	public static void finalizeSpell(Player player, SpellDef spell, String message, boolean giveExp, boolean setCastTimer) {
		player.lastCast = System.currentTimeMillis();
		player.playSound("spellok");
		// don't display a message if message is null (example superheat)
		if (message != null) {
			player.playerServerMessage(MessageType.QUEST, message.trim().isEmpty() ? "Cast spell successfully" : message);
		}
		if (giveExp) player.incExp(getMagicId(player, spell), spell.getExp(), true);
		if (setCastTimer) {
			player.setCastTimer();
		}
	}

	public void godSpellObject(Player player, Mob affectedMob, Spells spellEnum) {
		final boolean useLegacySceneryEffect = !player.isUsingCustomClient() || spellEnum == Spells.CHARGE;
		switch (spellEnum) {
			case CLAWS_OF_GUTHIX:
			case CLAW_OF_GUTHIX:
				if (useLegacySceneryEffect) {
					GameObject guthix = new GameObject(affectedMob.getWorld(), affectedMob.getLocation(), 1142, 0, 0);
					player.getWorld().registerGameObject(guthix);
					player.getWorld().getServer().getGameEventHandler().add(new ObjectRemover(player.getWorld(), guthix, 2));
				}
				break;
			case SARADOMIN_STRIKE:
			case SARADOMIN_SOUL_SLASH:
				if (useLegacySceneryEffect) {
					GameObject sara = new GameObject(affectedMob.getWorld(), affectedMob.getLocation(), 1031, 0, 0);
					player.getWorld().registerGameObject(sara);
					player.getWorld().getServer().getGameEventHandler().add(new ObjectRemover(player.getWorld(), sara, 2));
				}
				break;
			case FLAMES_OF_ZAMORAK:
			case ZAMORAKS_APOCOLYPSE:
				if (useLegacySceneryEffect) {
					GameObject zammy = new GameObject(affectedMob.getWorld(), affectedMob.getLocation(), 1036, 0, 0);
					player.getWorld().registerGameObject(zammy);
					player.getWorld().getServer().getGameEventHandler().add(new ObjectRemover(player.getWorld(), zammy, 2));
				}
				break;
			case CHARGE:
				if (useLegacySceneryEffect) {
					GameObject charge = new GameObject(affectedMob.getWorld(), affectedMob.getLocation(), 1147, 0, 0);
					player.getWorld().registerGameObject(charge);
					player.getWorld().getServer().getGameEventHandler().add(new ObjectRemover(player.getWorld(), charge, 2));
				}
				break;
		}
	}

	private void handleGroundCast(Player player, SpellDef spell, Spells spellEnum) {
		switch (spellEnum) {
			case BONES_TO_BANANAS:
				if (!checkAndRemoveRunes(player, spell)) {
					return;
				}

				int boneCount = player.getCarriedItems().getInventory().countId(ItemId.BONES.id(), Optional.of(false));
				if (boneCount == 0) {
					player.message("You aren't holding any bones!");
					return;
				}
				for (int i = 0; i < boneCount; i++) {
					player.getCarriedItems().remove(
						player.getCarriedItems().getInventory().get(
							player.getCarriedItems().getInventory().getLastIndexById(ItemId.BONES.id(), Optional.of(false))
						)
					);
					player.getCarriedItems().getInventory().add(new Item(ItemId.BANANA.id()));
				}
				// needs verify if default message
				finalizeSpell(player, spell, DEFAULT);
				break;
			case CHARGE:
			/*if (!player.getLocation().isMembersWild()) {
				player.message("Members content can only be used in wild levels: " + World.membersWildStart + " - "
						+ World.membersWildMax);
				return;
			}*/
				if (!player.getLocation().inMageArena()) {
					if ((!player.getCache().hasKey("Flames of Zamorak_casts") && !player.getCache().hasKey("Saradomin strike_casts") && !player.getCache().hasKey("Claws of Guthix_casts"))
						||
						((player.getCache().hasKey("Saradomin strike_casts") && player.getCache().getInt("Saradomin strike_casts") < 100))
						||
						((player.getCache().hasKey("Flames of Zamorak_casts") && player.getCache().getInt("Flames of Zamorak_casts") < 100))
						||
						((player.getCache().hasKey("Claws of Guthix_casts") && player.getCache().getInt("Claws of Guthix_casts") < 100))) {
						player.message("this spell can only be used in the mage arena");
						return;
					}
				}
				if (player.getViewArea().getGameObject(player.getLocation()) != null) {
					player.message("You can't charge power here, please move to a different area");
					return;
				}
				if (!checkAndRemoveRunes(player, spell)) {
					return;
				}
				player.message("@gre@You feel charged with magic power");
				player.addCharge(6 * 60000);
				player.getCache().store("charge_remaining", 6 * 60000);
				// charge is on self
				godSpellObject(player, player, Spells.CHARGE);
				finalizeSpell(player, spell, DEFAULT);
				return;
		}
	}

	private void handleItemCast(Player player, SpellDef spell, Spells spellEnum, Item affectedItem) {
		switch (spellEnum) {

			// Enchant lvl-1 Sapphire amulet
			case ENCHANT_LVL1_AMULET:
				enchantTierOneJewelry(player, affectedItem, spell);
				break;

			// Curse or Enfeeble on talisman
			case CURSE:
			case ENFEEBLE:
				buffTalisman(player, affectedItem, spell);
				break;

			// Low level alchemy
			case LOW_LEVEL_ALCHEMY:
				lowLevelAlchemy(player, affectedItem, spell);
				break;

			// Enchant lvl-2 emerald amulet
			case ENCHANT_LVL2_AMULET:
				enchantTierTwoJewelry(player, affectedItem, spell);
				break;

			// Superheat item
			case SUPERHEAT_ITEM:
				superheatItem(player, affectedItem, spell);
				break;

			// Enchant lvl-3 ruby amulet
			case ENCHANT_LVL3_AMULET:
				enchantTierThreeJewelry(player, affectedItem, spell);
				break;

			// High level alchemy
			case ALCHEMY:
				highLevelAlchemy(player, affectedItem, spell);
				break;

			// Enchant lvl-4 diamond amulet
			case ENCHANT_LVL4_AMULET:
				enchantTierFourJewelry(player, affectedItem, spell);
				break;

			// Enchant lvl-5 dragonstone amulet
			case ENCHANT_LVL5_AMULET:
				enchantTierFiveJewelry(player, affectedItem, spell);
				break;

		}
		if (affectedItem.isWielded()) {
			player.getCarriedItems().getEquipment().unequipItem(new UnequipRequest(player, affectedItem, UnequipRequest.RequestType.CHECK_IF_EQUIPMENT_TAB, false));
		}

	}

	private void enchantTierOneJewelry(Player player, Item affectedItem, SpellDef spell) {
		if (affectedItem.getCatalogId() == ItemId.SAPPHIRE_AMULET.id()) {
			player.playerServerMessage(MessageType.QUEST, "Amulets are enchanted at elemental altars now.");
			return;
		}
		if (affectedItem.getCatalogId() == ItemId.SAPPHIRE_RING.id()) {
			player.playerServerMessage(MessageType.QUEST, "Rings are enchanted at altars now.");
			return;
		}
		if (affectedItem.getCatalogId() == ItemId.OPAL_RING.id()) {
			player.playerServerMessage(MessageType.QUEST, "Opal rings no longer have a separate enchantment path.");
			return;
		}
		player.playerServerMessage(MessageType.QUEST, "This spell can only be used on unenchanted sapphire jewelry.");
	}

	private void buffTalisman(Player player, Item item, SpellDef spell) {
		player.message("Nothing interesting happens");
	}

	private void enchantTierTwoJewelry(Player player, Item affectedItem, SpellDef spell) {
		if (affectedItem.getCatalogId() == ItemId.EMERALD_AMULET.id()) {
			player.playerServerMessage(MessageType.QUEST, "Amulets are enchanted at elemental altars now.");
			return;
		}
		if (affectedItem.getCatalogId() == ItemId.EMERALD_RING.id()) {
			player.playerServerMessage(MessageType.QUEST, "Rings are enchanted at altars now.");
			return;
		}
		player.playerServerMessage(MessageType.QUEST, "This spell can only be used on unenchanted emerald jewelry.");
	}

	private void enchantTierThreeJewelry(Player player, Item affectedItem, SpellDef spell) {
		if (affectedItem.getCatalogId() == ItemId.RUBY_AMULET.id()) {
			player.playerServerMessage(MessageType.QUEST, "Amulets are enchanted at elemental altars now.");
			return;
		}
		if (affectedItem.getCatalogId() == ItemId.RUBY_RING.id()) {
			player.playerServerMessage(MessageType.QUEST, "Rings are enchanted at altars now.");
			return;
		}
		player.playerServerMessage(MessageType.QUEST, "This spell can only be used on unenchanted ruby jewelry.");
	}

	private void enchantTierFourJewelry(Player player, Item affectedItem, SpellDef spell) {
		if (affectedItem.getCatalogId() == ItemId.DIAMOND_AMULET.id()) {
			player.playerServerMessage(MessageType.QUEST, "Amulets are enchanted at elemental altars now.");
			return;
		}
		if (affectedItem.getCatalogId() == ItemId.DIAMOND_RING.id()) {
			player.playerServerMessage(MessageType.QUEST, "Rings are enchanted at altars now.");
			return;
		}
		player.playerServerMessage(MessageType.QUEST, "This spell can only be used on unenchanted diamond jewelry.");
	}

	private void enchantTierFiveJewelry(Player player, Item affectedItem, SpellDef spell) {
		if (affectedItem.getCatalogId() == ItemId.UNENCHANTED_DRAGONSTONE_AMULET.id()) {
			player.playerServerMessage(MessageType.QUEST, "Dragonstone amulets are enchanted at altars now.");
			return;
		}
		if (affectedItem.getCatalogId() == ItemId.DRAGONSTONE_RING.id()) {
			player.playerServerMessage(MessageType.QUEST, "Rings are enchanted at altars now.");
			return;
		}
		if (affectedItem.getCatalogId() == ItemId.DRAGONSTONE_NECKLACE.id()) {
			player.playerServerMessage(MessageType.QUEST, "Necklaces are enchanted at altars now.");
			return;
		}
		player.playerServerMessage(MessageType.QUEST, "Dragonstone jewelry is enchanted at altars now.");
	}

	private void lowLevelAlchemy(Player player, Item affectedItem, SpellDef spell) {
		if (affectedItem.getCatalogId() == ItemId.COINS.id()) {
			player.playerServerMessage(MessageType.QUEST, "That's already made of gold!");
			return;
		}
		if (player.getConfig().WANT_OPENPK_POINTS) {
			for (Item starter : player.getWorld().getServer().getConstants().OPENPK_STARTER_ITEMS) {
				if (starter == null) continue;
				if (affectedItem.getCatalogId() == starter.getCatalogId()) {
					player.message("You can't alch starter items.");
					return;
				}
			}
			for (int item : player.getWorld().getServer().getConstants().OPENPK_NONSTARTER_UNALCHABLE_ITEMS) {
				if (affectedItem.getCatalogId() == item) {
					player.message("You can't alch this item.");
					return;
				}
			}
		}
		if (affectedItem.getNoted()) {
			player.message("You can't alch noted items");
			return;
		}
		if (!checkAndRemoveRunes(player, spell)) {
			return;
		}
		// ana in barrel kept but xp allowed
		if (affectedItem.getCatalogId() == ItemId.ANA_IN_A_BARREL.id()) {
			player.message("@gre@Ana: Don't you start casting spells on me!");
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.ALCHEMY));
			finalizeSpellNoMessage(player, spell);
		} else {
			if (player.getCarriedItems().remove(new Item(affectedItem.getCatalogId(), affectedItem.getAmount())) == -1) return;
			int value = (int) (affectedItem.getDef(player.getWorld()).getDefaultPrice() * 0.4D * affectedItem.getAmount());
			player.getCarriedItems().getInventory().add(new Item(ItemId.COINS.id(), value)); // 40%
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.ALCHEMY));
			finalizeSpell(player, spell, "Alchemy spell successful");
		}
	}

	private void highLevelAlchemy(Player player, Item affectedItem, SpellDef spell) {
		if (affectedItem.getCatalogId() == ItemId.COINS.id()) {
			player.playerServerMessage(MessageType.QUEST, "That's already made of gold!");
			return;
		}
		if (player.getConfig().WANT_OPENPK_POINTS) {
			for (Item starter : player.getWorld().getServer().getConstants().OPENPK_STARTER_ITEMS) {
				if (starter == null) continue;
				if (affectedItem.getCatalogId() == starter.getCatalogId()) {
					player.message("You can't alch starter items.");
					return;
				}
			}
			for (int item : player.getWorld().getServer().getConstants().OPENPK_NONSTARTER_UNALCHABLE_ITEMS) {
				if (affectedItem.getCatalogId() == item) {
					player.message("You can't alch this item.");
					return;
				}
			}
		}
		if (affectedItem.getNoted()) {
			player.message("You can't alch noted items");
			return;
		}
		if (!checkAndRemoveRunes(player, spell)) {
			return;
		}
		// ana in barrel kept but xp allowed
		if (affectedItem.getCatalogId() == ItemId.ANA_IN_A_BARREL.id()) {
			player.message("@gre@Ana: Don't you start casting spells on me!");
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.ALCHEMY));
			finalizeSpellNoMessage(player, spell);
		} else {
			if (player.getCarriedItems().remove(new Item(affectedItem.getCatalogId(), affectedItem.getAmount())) == -1) return;
			int value = (int) (affectedItem.getDef(player.getWorld()).getDefaultPrice() * 0.6D * affectedItem.getAmount());
			player.getCarriedItems().getInventory().add(new Item(ItemId.COINS.id(), value)); // 60%
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.ALCHEMY));
			finalizeSpell(player, spell, "Alchemy spell successful");
		}
	}

	private void superheatItem(Player player, Item affectedItem, SpellDef spell) {
		ItemSmeltingDef smeltingDef = affectedItem.getSmeltingDef(player.getWorld());
		if (smeltingDef == null || affectedItem.getCatalogId() == ItemId.COAL.id()) {
			player.playerServerMessage(MessageType.QUEST, "This spell can only be used on ore");
			return;
		}
		for (ReqOreDef reqOre : smeltingDef.getReqOres()) {
			if (player.getCarriedItems().getInventory().countId(reqOre.getId()) < reqOre.getAmount()) {
				if (affectedItem.getCatalogId() == ItemId.IRON_ORE.id()) {
					smeltingDef = player.getWorld().getServer().getEntityHandler().getItemSmeltingDef(9999);
					break;
				}
				if (affectedItem.getCatalogId() == ItemId.TIN_ORE.id() || affectedItem.getCatalogId() == ItemId.COPPER_ORE.id()) {
					player.playerServerMessage(MessageType.QUEST, "You also need some " + (affectedItem.getCatalogId() == ItemId.TIN_ORE.id() ? "copper" : "tin")
							+ " to make bronze");
					return;
				}
				player.playerServerMessage(MessageType.QUEST, "You need " + reqOre.getAmount() + " heaps of "
						+ player.getWorld().getServer().getEntityHandler().getItemDef(reqOre.getId()).getName().toLowerCase() + " to smelt "
						+ affectedItem.getDef(player.getWorld()).getName().toLowerCase().replaceAll("ore", ""));
				return;
			}
		}

		if (player.getSkills().getLevel(Skill.SMITHING.id()) < smeltingDef.getReqLevel()) {
			player.playerServerMessage(MessageType.QUEST, "You need to be at least level-" + smeltingDef.getReqLevel() + " smithing to smelt "
					+ player.getWorld().getServer().getEntityHandler().getItemDef(smeltingDef.barId).getName().toLowerCase().replaceAll("bar", ""));
			return;
		}
		if (!checkAndRemoveRunes(player, spell)) {
			return;
		}
		Item bar = new Item(smeltingDef.getBarId());
		if (player.getCarriedItems().remove(affectedItem) == -1) return;
		for (ReqOreDef reqOre : smeltingDef.getReqOres()) {
			int toUse = reqOre.getAmount();
			if (reqOre.getId() == ItemId.COAL.id()
				&& SkillCapes.shouldActivate(player, ItemId.SMITHING_CAPE)) {

				toUse = reqOre.getAmount()/2;
				player.message("You heat the ore using half the usual amount of coal");
			}
			for (int i = 0; i < toUse; i++) {
				player.getCarriedItems().remove(new Item(reqOre.getId()));
			}
		}
		player.playerServerMessage(MessageType.QUEST, "You make a bar of " + bar.getDef(player.getWorld()).getName().replace("bar", "").toLowerCase());
		player.getCarriedItems().getInventory().add(bar);
		player.incExp(Skill.SMITHING.id(), smeltingDef.getExp(), true);
		finalizeSpellNoMessage(player, spell);
	}

	private void handleItemCast(Player player, final SpellDef spell, Spells spellEnum, final GroundItem affectedItem) {
		player.setWalkToAction(new WalkToPointAction(player, affectedItem.getLocation(), 4) {
			public void executeInternal() {
				getPlayer().resetPath();
				if (!canCast(getPlayer()) || getPlayer().getViewArea().getVisibleGroundItem(affectedItem.getID(), getLocation(), getPlayer()) == null || affectedItem.isRemoved()) {
					return;
				}
				if (!PathValidation.checkPath(getPlayer().getWorld(), getPlayer().getLocation(), affectedItem.getLocation())) {
					getPlayer().playerServerMessage(MessageType.QUEST, "I can't see the object from here");
					getPlayer().resetPath();
					return;
				}
				getPlayer().resetAllExceptDueling();
				switch (spellEnum) {
					case TELEKINETIC_GRAB:
						if (affectedItem.isInvisibleTo(getPlayer()))
						{
							return;
						}
						// fluffs gets its own message
						// same case with ana
						int[] ungrabbableArr = {
							//scythe
							ItemId.SCYTHE.id(),
							//bunny ears
							ItemId.BUNNY_EARS.id(),
							//orbs
							ItemId.ORB_OF_LIGHT_WHITE.id(), ItemId.ORB_OF_LIGHT_BLUE.id(), ItemId.ORB_OF_LIGHT_PINK.id(), ItemId.ORB_OF_LIGHT_YELLOW.id(),
							//cat (underground pass)
							ItemId.KARDIA_CAT.id(),
							//god capes
							ItemId.ZAMORAK_CAPE.id(), ItemId.SARADOMIN_CAPE.id(), ItemId.GUTHIX_CAPE.id(),
							//holy grail
							ItemId.HOLY_GRAIL.id(),
							//large cogs
							ItemId.LARGE_COG_BLUE.id(), ItemId.LARGE_COG_BLACK.id(), ItemId.LARGE_COG_RED.id(), ItemId.LARGE_COG_PURPLE.id(),
							//staff of armadyl,
							ItemId.STAFF_OF_ARMADYL.id(),
							//ice arrows
							ItemId.ICE_ARROWS.id(),
							//Firebird Feather
							ItemId.RED_FIREBIRD_FEATHER.id(),
							//Ball of Witch's House
							ItemId.BALL.id(),
							//skull of restless ghost
							ItemId.QUEST_SKULL.id()
						};
						List<Integer> ungrabbables = new ArrayList<Integer>();
						for (int item : ungrabbableArr) {
							ungrabbables.add(item);
						}

						int groundItemId = affectedItem.getID();
						int groundItemX = affectedItem.getX();
						int groundItemY = affectedItem.getY();

						//Carved rock gems should not be able to be telegrabbed, per Shasta.
						boolean isLegendsQuestGem = (groundItemId == ItemId.OPAL.id() && groundItemX == 471 && groundItemY == 3722)
							|| (groundItemId == ItemId.EMERALD.id() && groundItemX == 474 && groundItemY == 3730)
							|| (groundItemId == ItemId.RUBY.id() && groundItemX == 471 && groundItemY == 3734)
							|| (groundItemId == ItemId.DIAMOND.id() && groundItemX == 466 && groundItemY == 3739)
							|| (groundItemId == ItemId.SAPPHIRE.id() && groundItemX == 460 && groundItemY == 3737)
							|| (groundItemId == ItemId.RED_TOPAZ.id() && groundItemX == 464 && groundItemY == 3730)
							|| (groundItemId == ItemId.JADE.id() && groundItemX == 469 && groundItemY == 3728);

						if (isLegendsQuestGem) {
							return;
						}

						if (affectedItem.getID() == ItemId.PRESENT.id()) {
							return;
						} else if (ungrabbables.contains(affectedItem.getID())) { // list of ungrabbable items sharing this message
							getPlayer().playerServerMessage(MessageType.QUEST, "I can't use telekinetic grab on this object");
							return;
						}

						if (!getPlayer().getWorld().isTelegrabEnabled()) {
							getPlayer().message("Telegrab has been disabled");
							return;
						}
						if (affectedItem.getLocation().isInSeersPartyHallUpstairs()) {
							// Only the upstairs is affected, see "RSC 2001/LAST 2 DAYS REPLAYS (ACCOUNT 1)/flying sno train - 08-05-2018 22.55.55" at 33:00
							getPlayer().message("You can't cast this spell within the vicinity of the party hall");
							return;
						}
						if (affectedItem.getLocation().isInWatchtowerPedestal()) {
							getPlayer().playerServerMessage(MessageType.QUEST, "I can't see the object from here");
							return;
						}
						if (affectedItem.getID() == ItemId.A_BLUE_WIZARDS_HAT.id()) {
							getPlayer().message("The spell fizzles as the magical hat resists your spell.");
							return;
						}
						if (affectedItem.getID() == ItemId.GERTRUDES_CAT.id()) {
							getPlayer().message("I can't use telekinetic grab on the cat");
							return;
						}
						if (affectedItem.getID() == ItemId.ANA_IN_A_BARREL.id()) {
							getPlayer().message("I can't use telekinetic grab on Ana");
							return;
						}
						//coin respawn in Rashiliyia's Tomb can't be telegrabbed
						if (affectedItem.getID() == ItemId.COINS.id() && affectedItem.getLocation().equals(new Point(358, 3626))) {
							getPlayer().message("The coins turn to dust in your inventory...");
							return;
						}
						if (affectedItem.getLocation().inBounds(97, 1428, 106, 1440)) {
							// upstairs of Varrock Museum, where drop parties were sometimes held
							getPlayer().message("Telekinetic grab cannot be used in here");
							return;
						}
						if (player.getConfig().MICE_TO_MEET_YOU_EVENT && affectedItem.getLocation().inBounds(114, 532, 115, 535) && affectedItem.getID() == ItemId.PUMPKIN.id()) {
							getPlayer().message("A strange power prevents you from telegrabbing the pumpkin.");
							delay(3);
							getPlayer().message("@yel@Death: Do NOT cast magic on my belongings!!");
							return;
						}

						if (affectedItem.getLocation().inWilderness() && !affectedItem.belongsTo(getPlayer())
							&& affectedItem.getAttribute("playerKill", false)
							&& (getPlayer().isIronMan(IronmanMode.Ironman.id()) || getPlayer().isIronMan(IronmanMode.Ultimate.id())
							|| getPlayer().isIronMan(IronmanMode.Hardcore.id()) || getPlayer().isIronMan(IronmanMode.Transfer.id()))) {
							getPlayer().message("You're an Ironman, so you can't loot items from players.");
							return;
						}
						if (!affectedItem.belongsTo(getPlayer())
							&& (getPlayer().isIronMan(IronmanMode.Ironman.id()) || getPlayer().isIronMan(IronmanMode.Ultimate.id())
							|| getPlayer().isIronMan(IronmanMode.Hardcore.id()) || getPlayer().isIronMan(IronmanMode.Transfer.id()))) {
							getPlayer().message("You're an Ironman, so you can't take items that other players have dropped.");
							return;
						}

						if (!affectedItem.belongsTo(getPlayer()) && affectedItem.getAttribute("isTransferIronmanItem", false)) {
							getPlayer().message("That belongs to a Transfer Ironman player.");
							return;
						}

						if (CertUtil.isCert(affectedItem.getID()) && getPlayer().getCertOptOut()
							&& affectedItem.getOwnerUsernameHash() != 0 && !affectedItem.belongsTo(getPlayer())) {
							getPlayer().message("You have opted out of taking certs that other players have dropped.");
							return;
						}

						if (affectedItem.isRemoved()) {
							return;
						}

						if (!checkAndRemoveRunes(getPlayer(), spell)) {
							return;
						}
						ActionSender.sendTeleBubble(getPlayer(), getLocation().getX(), getLocation().getY(), true);
						for (Player player : getPlayer().getViewArea().getPlayersInView()) {
							ActionSender.sendTeleBubble(player, getLocation().getX(), getLocation().getY(), true);
						}

						getPlayer().getWorld().unregisterItem(affectedItem);
						finalizeSpell(getPlayer(), spell, "Spell successful");
						getPlayer().getWorld().getServer().getGameLogger().addQuery(
							new GenericLog(getPlayer().getWorld(), getPlayer().getUsername() + " telegrabbed " + affectedItem.getDef().getName()
								+ " x" + affectedItem.getAmount() + " from " + affectedItem.getLocation().toString()
								+ " while standing at " + getPlayer().getLocation().toString()));
						Item item = new Item(affectedItem.getID(), affectedItem.getAmount(), affectedItem.getNoted());

						if (affectedItem.getOwnerUsernameHash() == 0 || affectedItem.getAttribute("npcdrop", false)) {
							item.setAttribute("npcdrop", true);
						}
						getPlayer().getCarriedItems().getInventory().add(item);
						break;
				}
			}
		});
	}

	private void handleMobCast(final Player player, final Mob affectedMob, Spells spellEnum, int spellType) {
		final int spellRange = player.getConfig().SPELL_RANGE_DISTANCE + RangeUtils.PLAYER_COMBAT_RANGE_BONUS;
		final SpellDef debugSpell = player.getWorld().getServer().getEntityHandler().getSpellDef(spellEnum);
		magicDebug(player, "mobcast_start spell=" + describeSpell(spellEnum, debugSpell)
			+ " target=" + describeMob(affectedMob) + " playerLoc=" + player.getLocation()
			+ " targetLoc=" + affectedMob.getLocation() + " range=" + spellRange
			+ " inRange=" + player.withinRange(affectedMob, spellRange)
			+ " inCombat=" + player.inCombat() + " opponent=" + describeMob(player.getOpponent()));
		if (player.getDuel().isDuelActive() && affectedMob.isPlayer()) {
			Player aff = (Player) affectedMob;
			if (!player.getDuel().getDuelRecipient().getUsername().toLowerCase()
				.equals(aff.getUsername().toLowerCase())) {
				magicDebug(player, "mobcast_reject reason=duel_target target=" + describeMob(affectedMob));
				return;
			}
		}
		if (player.withinRange(affectedMob, spellRange)
			&& !PathValidation.checkPath(player.getWorld(), player.getLocation(), affectedMob.getLocation())) {
			magicDebug(player, "mobcast_reject reason=no_clear_path playerLoc=" + player.getLocation()
				+ " targetLoc=" + affectedMob.getLocation());
			player.playerServerMessage(MessageType.QUEST, "I can't get a clear shot from here");
			player.resetPath();
			return;
		}

		if (affectedMob.isPlayer()) {
			Player other = (Player) affectedMob;
			boolean isInPkZone = player.getLocation().inWilderness() || player.getConfig().USES_PK_MODE;
			if (isInPkZone && !other.canBeReattacked()) {
				magicDebug(player, "mobcast_reject reason=pvp_target_reattack target=" + describeMob(affectedMob));
				player.resetPath();
				// Effectively remove the attack timer from the player casting
				// Authentic: see ticket #2579
				player.resetRanAwayTimer();
				return;
			}
			if (isInPkZone && !player.canBeReattacked()) {
				magicDebug(player, "mobcast_reject reason=pvp_self_reattack target=" + describeMob(affectedMob));
				player.resetPath();
				// TODO: ...? should probably display a message here instead of dying silently...?
				System.out.println("Killed pvp cast silently because they shot too fast");
				return;
			}
		}

		// Legacy combat blocked chase-to-cast while already fighting. MyWorld keeps combat free-form,
		// so the cast action is allowed to wait until the player reaches spell range.
		if (!player.withinRange(affectedMob, spellRange) && player.inCombat() && !player.getConfig().WANT_MYWORLD) {
			magicDebug(player, "mobcast_reject reason=legacy_incombat_range");
			return;
		}

		boolean retargetingNpcWhileInCombat = player.inCombat()
			&& affectedMob.isNpc()
			&& player.getOpponent() != null
			&& player.getOpponent().isNpc()
			&& !player.getOpponent().equals(affectedMob)
			&& !player.getDuel().isDueling();

		// Retro RSC mechanic, could not use magic if already engaged in combat
		// and spell was not personal spell (cast on self, type = 0)
		if (player.getConfig().BLOCK_USE_MAGIC_IN_COMBAT && player.inCombat() && !retargetingNpcWhileInCombat &&
			spellType != 0 && spellEnum != Spells.FEAR) {
			magicDebug(player, "mobcast_reject reason=blocked_in_combat retargeting=" + retargetingNpcWhileInCombat);
			player.message("You cannot do that whilst fighting!");
			return;
		}

		final boolean alreadyCanCast = player.withinRange(affectedMob, spellRange);
		final int spellApproachRange = alreadyCanCast ? spellRange : RangeUtils.getApproachRadius(spellRange);
		magicDebug(player, "mobcast_walk_action_set range=" + spellRange + " approachRange=" + spellApproachRange
			+ " target=" + describeMob(affectedMob)
			+ " retargetingNpcWhileInCombat=" + retargetingNpcWhileInCombat);
		player.setFollowing(affectedMob, spellApproachRange, true, true);
		player.setWalkToAction(new WalkToMobAction(player, affectedMob, spellApproachRange, false, ActionType.ATTACKMAGIC) {
			public void executeInternal() {
				magicDebug(getPlayer(), "walk_action_execute spell=" + describeSpell(spellEnum, debugSpell)
					+ " target=" + describeMob(affectedMob) + " playerLoc=" + getPlayer().getLocation()
					+ " targetLoc=" + affectedMob.getLocation()
					+ " inRange=" + getPlayer().withinRange(affectedMob, spellRange));
				if (!PathValidation.checkPath(getPlayer().getWorld(), getPlayer().getLocation(), affectedMob.getLocation())) {
					magicDebug(getPlayer(), "walk_action_reject reason=no_clear_path playerLoc=" + getPlayer().getLocation()
						+ " targetLoc=" + affectedMob.getLocation());
					getPlayer().playerServerMessage(MessageType.QUEST, "I can't get a clear shot from here");
					getPlayer().resetPath();
					return;
				}
				if (retargetingNpcWhileInCombat) {
					magicDebug(getPlayer(), "walk_action_reset_combat_for_retarget oldOpponent="
						+ describeMob(getPlayer().getOpponent()));
					getPlayer().resetCombatEvent();
				}
				getPlayer().resetFollowing();
				getPlayer().resetPath();
				SpellDef spell = getPlayer().getWorld().getServer().getEntityHandler().getSpellDef(spellEnum);
				if (!canCast(getPlayer()) || affectedMob.getSkills().getLevel(Skill.HITS.id()) <= 0) {
					magicDebug(getPlayer(), "walk_action_reject reason=cast_timer_or_dead wait=" + getPlayer().getSpellWait()
						+ " targetHits=" + affectedMob.getSkills().getLevel(Skill.HITS.id()));
					getPlayer().resetPath();
					return;
				}
				if (!getPlayer().checkAttack(affectedMob, true) && affectedMob.isPlayer()) {
					magicDebug(getPlayer(), "walk_action_reject reason=check_attack_player target=" + describeMob(affectedMob));
					getPlayer().resetPath();
					return;
				}
				if (!getPlayer().checkAttack(affectedMob, true) && affectedMob.isNpc()) {
					// Exception for certain non-attackable mobs that attack you
					// We want to make sure that player is in combat with the mob.
					boolean inCombat = getPlayer().inCombat();
					boolean isRightMob = inArray(affectedMob.getID(),
						new int[]{
							NpcId.SHAPESHIFTER_SPIDER.id(),
							NpcId.SHAPESHIFTER_BEAR.id(),
							NpcId.SHAPESHIFTER_WOLF.id(),
							NpcId.THRANTAX.id(),
							NpcId.GUARDIAN_OF_ARMADYL_FEMALE.id(),
							NpcId.GUARDIAN_OF_ARMADYL_MALE.id(),
							NpcId.OGRE_TRADER_FOOD.id(),
							NpcId.OGRE_TRADER_ROCKCAKE.id(),
							NpcId.OGRE_TRADER_FOOD.id(),
							NpcId.CITY_GUARD.id(),
							NpcId.TOBAN.id()});
					boolean shouldCastSpell = inCombat && isRightMob;
					if (!shouldCastSpell) {
						magicDebug(getPlayer(), "walk_action_reject reason=check_attack_npc target=" + describeMob(affectedMob)
							+ " inCombat=" + inCombat + " specialMob=" + isRightMob);
						getPlayer().message("I can't attack that");
						getPlayer().resetPath();
						return;
					}
				}
				boolean setChasing = true;
				Set<Entry<Integer, Integer>> necessaryRunes;
				try {
					necessaryRunes = checkSpellRunes(player, spell, true);
				} catch (SpellFailureException re) {
					// magic cape effect did not roll out and
					// player does not meet required spell runes
					// message already given out
					magicDebug(getPlayer(), "walk_action_reject reason=runes spell=" + describeSpell(spellEnum, spell));
					getPlayer().resetPath();
					return;
				}
				boolean capeActivated = necessaryRunes == null;

				if (affectedMob.isNpc()) {
					Npc n = (Npc) affectedMob;

					if (n.getID() == NpcId.DRAGON.id() || n.getID() == NpcId.KING_BLACK_DRAGON.id()) {
						getPlayer().playerServerMessage(MessageType.QUEST, "The dragon breathes fire at you");
						int percentage = 20;
						int fireDamage;
						if (getPlayer().getCarriedItems().getEquipment().hasEquipped(ItemId.ANTI_DRAGON_BREATH_SHIELD.id())) {
							if (n.getID() == NpcId.DRAGON.id()) {
								percentage = 10;
							} else if (n.getID() == NpcId.KING_BLACK_DRAGON.id()) {
								percentage = 4;
							} else {
								percentage = 0;
							}
							getPlayer().playerServerMessage(MessageType.QUEST, "Your shield prevents some of the damage from the flames");
						}
						fireDamage = (int) Math.floor(getCurrentLevel(getPlayer(), Skill.HITS.id()) * percentage / 100.0);
						getPlayer().damage(fireDamage);

						//reduce ranged level (case for KBD)
						if (n.getID() == NpcId.KING_BLACK_DRAGON.id()) {
							int newLevel = getCurrentLevel(getPlayer(), Skill.RANGED.id()) - Formulae.getLevelsToReduceAttackKBD(getPlayer());
							getPlayer().getSkills().setLevel(Skill.RANGED.id(), newLevel);
						}
					} else if (inArray(n.getID(), NpcId.KOLODION_HUMAN.id(), NpcId.KOLODION_OGRE.id(), NpcId.KOLODION_SPIDER.id(),
						NpcId.KOLODION_SOULESS.id(), NpcId.KOLODION_DEMON.id(), NpcId.BATTLE_MAGE_GUTHIX.id(),
						NpcId.BATTLE_MAGE_ZAMORAK.id(), NpcId.BATTLE_MAGE_SARADOMIN.id()) && getPlayer().getLocation().inMageArena()) {
						setChasing = false;
						getPlayer().setAttribute("maged_kolodion", true);
					}
					if (spellEnum == Spells.FEAR) {
						setChasing = false;
					}

				}
				getPlayer().resetAllExceptDueling();
				EntityType entityType = mob.isPlayer() ? EntityType.PLAYER : EntityType.NPC;
				boolean isClaws = false;
				switch (spellEnum) {
					case FEAR:
						if (!getPlayer().getConfig().HAS_FEAR_SPELL) {
							getPlayer().playerServerMessage(MessageType.QUEST, "This world does not support fear spell");
							return;
						}
						if (!affectedMob.isNpc() || !((Npc)affectedMob).getDef().isAttackable() || !affectedMob.inCombat()) {
							getPlayer().playerServerMessage(MessageType.QUEST, "This spell can only be used on monsters engaged in combat");
							return;
						}

						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}

						getPlayer().getWorld().getServer().getGameEventHandler().add(new CustomProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, 1, setChasing) {
							@Override
							public void doSpell() {
								// https://www.tip.it/runescape/times/view/615-forever-runescape-part-1
								// https://web.archive.org/web/20010410193705/http://www.geocities.com/ngrunescape/magic.html
								if (affectedMob.inCombat()) {
									((Npc)affectedMob).getBehavior().retreat();
									//This sends the message to the caster, which may not be the player in combat. Probably not correct?
									//retreat() already sends the message to the actual opponent.
									//getPlayer().message("Your opponent is retreating");
								}
							}
						});

						finalizeSpell(getPlayer(), spell, DEFAULT);
						break;

					case CONFUSE_R:
						double reduceBy = 0.02; // to date not known percentage, but possible
						int[] stats = CombatEffectUtil.remapLegacyPlayerMeleeStats(affectedMob, Skill.ATTACK.id(), Skill.DEFENSE.id());
						for (int affectedStat : stats) {
							if (affectedMob.getSkills().getLevel(affectedStat) < affectedMob.getSkills().getMaxStat(affectedStat)) {
								getPlayer().playerServerMessage(MessageType.QUEST, "Your opponent already has weakened stats");
								return;
							}
						}

						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}

						getPlayer().getWorld().getServer().getGameEventHandler().add(new CustomProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, 1, setChasing) {
							@Override
							public void doSpell() {
								for (int stat : stats) {
									int lowerBy = (int) Math.ceil((affectedMob.getSkills().getLevel(stat) * reduceBy));
									int newStat = affectedMob.getSkills().getLevel(stat) - lowerBy;
									affectedMob.getSkills().setLevel(stat, newStat);
								}

								// https://web.archive.org/web/20010410193705/http://www.geocities.com:80/ngrunescape/magic.html
								if (affectedMob.isPlayer()) {
									((Player) affectedMob).message("Your Attack and Defense have been lowered from a confuse spell!");
								}
							}
						});
						finalizeSpell(getPlayer(), spell, DEFAULT);
						return;

					/*
					 * Confuse, reduces attack by 5% Weaken, reduces strength by 5%
					 * Curse reduces defense by 5%
					 *
					 * Vulnerability, reduces defense by 10% Enfeeble, reduces
					 * strength by 10% Stun, reduces attack by 10%
					 */
					case CONFUSE:
					case WEAKEN:
					case CURSE:
					case VULNERABILITY:
					case ENFEEBLE:
					case STUN:
						double lowersBy = 0.0;
						int affectsStat = -1;
						final String message;

						if (spellEnum == Spells.CONFUSE) {
							lowersBy = 0.05;
							affectsStat = Skill.ATTACK.id();
							message = "Your attack has been reduced by a confuse spell!";
						} else if (spellEnum == Spells.WEAKEN) {
							lowersBy = 0.05;
							affectsStat = Skill.STRENGTH.id();
							message = "Your strength has been reduced by a weaken spell!";
						} else if (spellEnum == Spells.CURSE) {
							lowersBy = 0.05;
							affectsStat = Skill.DEFENSE.id();
							message = "Your defense has been reduced by a curse spell!";
						} else if (spellEnum == Spells.VULNERABILITY) {
							lowersBy = 0.10;
							affectsStat = Skill.DEFENSE.id();
							message = "Your defense has been reduced by a vulnerability spell!";
						} else if (spellEnum == Spells.ENFEEBLE) {
							lowersBy = 0.10;
							affectsStat = Skill.STRENGTH.id();
							message = "Your strength has been reduced by an enfeeble spell!";
						} else if (spellEnum == Spells.STUN) {
							lowersBy = 0.10;
							affectsStat = Skill.ATTACK.id();
							message = "Your attack has been reduced by a stun spell!";
						} else {
							message = "Undefined spell";
						}
						affectsStat = CombatEffectUtil.remapLegacyPlayerMeleeStat(affectedMob, affectsStat);
						final String statName = affectsStat == Skill.MELEE.id()
							? "melee"
							: getPlayer().getWorld().getServer().getConstants().getSkills().getSkill(affectsStat).getLongName().toLowerCase();
						final String playerMessage;
						if (spellEnum == Spells.CONFUSE) {
							playerMessage = "Your " + statName + " has been reduced by a confuse spell!";
						} else if (spellEnum == Spells.WEAKEN) {
							playerMessage = "Your " + statName + " has been reduced by a weaken spell!";
						} else if (spellEnum == Spells.CURSE) {
							playerMessage = "Your " + statName + " has been reduced by a curse spell!";
						} else if (spellEnum == Spells.VULNERABILITY) {
							playerMessage = "Your " + statName + " has been reduced by a vulnerability spell!";
						} else if (spellEnum == Spells.ENFEEBLE) {
							playerMessage = "Your " + statName + " has been reduced by an enfeeble spell!";
						} else if (spellEnum == Spells.STUN) {
							playerMessage = "Your " + statName + " has been reduced by a stun spell!";
						} else {
							playerMessage = message;
						}

						/* How much to lower the stat */
						int lowerBy = (int) Math.ceil((affectedMob.getSkills().getLevel(affectsStat) * lowersBy));
						/* New current level */
						final int newStat = affectedMob.getSkills().getLevel(affectsStat) - lowerBy;
						if (affectedMob.getSkills().getLevel(affectsStat) < affectedMob.getSkills().getMaxStat(affectsStat)) {
							final String skillName = getPlayer().getWorld().getServer().getConstants().getSkills().getSkill(affectsStat).getLongName().toLowerCase();
							getPlayer().playerServerMessage(MessageType.QUEST, "Your opponent already has weakened " + skillName);
							return;
						}
						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}
						final int stat = affectsStat;
						getPlayer().getWorld().getServer().getGameEventHandler().add(new CustomProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, 1, setChasing) {
							@Override
							public void doSpell() {
								affectedMob.getSkills().setLevel(stat, newStat);
								if (affectedMob.isPlayer()) {
									((Player) affectedMob).message(playerMessage);
								}
							}
						});
						finalizeSpell(getPlayer(), spell, DEFAULT);
						return;
					case CRUMBLE_UNDEAD:
						if (affectedMob.isPlayer()) {
							getPlayer().message("You can not use this spell on a Player");
							return;
						}
						Npc n = (Npc) affectedMob;
						String npcName = n.getDef().getName().toLowerCase();
						boolean isCrumbleTarget = npcName.contains("skeleton") || npcName.contains("zombie") || npcName.contains("ghost");
						if (!isCrumbleTarget) {
							getPlayer().playerServerMessage(MessageType.QUEST, "This spell can only be used on skeletons, zombies and ghosts");
							return;
						}
						int damaga = CombatFormula.calculateMagicDamage(getPlayer(), affectedMob, Constants.CRUMBLE_UNDEAD_MAX);
						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}
						getPlayer().getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, damaga, 1, setChasing));
						finalizeSpell(getPlayer(), spell, DEFAULT);
						return;

					case IBAN_BLAST:
						if (getPlayer().getQuestStage(Quests.UNDERGROUND_PASS) != -1) {
							getPlayer().message("you need to complete underground pass quest to cast this spell");
							return;
						}
						if (!getPlayer().getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_IBAN.id())) {
							getPlayer().message("you need the staff of iban to cast this spell");
							return;
						}
						if (getPlayer().getCache().hasKey(spell.getName() + "_casts")
							&& getPlayer().getCache().getInt(spell.getName() + "_casts") < 1) {
							getPlayer().message("you need to recharge the staff of iban");
							getPlayer().message("at iban's temple");
							return;
						}
						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}
						if (getPlayer().getCache().hasKey(spell.getName() + "_casts")) {
							int casts = getPlayer().getCache().getInt(spell.getName() + "_casts");
							getPlayer().getCache().set(spell.getName() + "_casts", casts - 1);
						}
						getPlayer().getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob,
							CombatFormula.calculateIbanSpellDamage(getPlayer(), affectedMob), 4, setChasing,
							0, 0, 0, 0, Projectile.SKULL, CombatEffect.IBAN_BLAST, false));
						finalizeSpell(getPlayer(), spell, DEFAULT);
						break;
					case CLAWS_OF_GUTHIX:
					case CLAW_OF_GUTHIX:
						isClaws = true;
					case SARADOMIN_STRIKE:
					case SARADOMIN_SOUL_SLASH:
					case FLAMES_OF_ZAMORAK:
					case ZAMORAKS_APOCOLYPSE:
						if (!getPlayer().getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_GUTHIX.id()) && isGuthixGodSpell(spellEnum)) {
							getPlayer().message("you must weild the staff of guthix to cast this spell");
							return;
						}
						if (!getPlayer().getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_SARADOMIN.id()) && isSaradominGodSpell(spellEnum)) {
							getPlayer().message("you must weild the staff of saradomin to cast this spell");
							return;
						}
						if (!getPlayer().getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_ZAMORAK.id()) && isZamorakGodSpell(spellEnum)) {
							getPlayer().message("you must weild the staff of zamorak to cast this spell");
							return;
						}

						if(getPlayer().getConfig().WANT_OPENPK_POINTS) {
							if (getPlayer().getLocation().inWilderness() && !getPlayer().getLocation().inMageArena()
								&& (getPlayer().getLocation().wildernessLevel() < getPlayer().getWorld().godSpellsStart
										|| getPlayer().getLocation().wildernessLevel() > getPlayer().getWorld().godSpellsMax)) {
							getPlayer().message("God spells can only be used in wild levels: " + getPlayer().getWorld().godSpellsStart + " - "
									+ getPlayer().getWorld().godSpellsMax);
								return;
							}
						}

						if (!getPlayer().getLocation().inMageArena()) {
							final String godSpellCastCacheKey = getGodSpellCastCacheKey(spellEnum, spell);
							if ((!getPlayer().getCache().hasKey(godSpellCastCacheKey))
								|| (getPlayer().getCache().hasKey(godSpellCastCacheKey)
								&& getPlayer().getCache().getInt(godSpellCastCacheKey) < 100)) {
								getPlayer().message("this spell can only be used in the mage arena");
								getPlayer().message("You must learn this spell first, you need "
									+ (getPlayer().getCache().hasKey(godSpellCastCacheKey)
									? (100 - getPlayer().getCache().getInt(godSpellCastCacheKey)) : "100")
									+ " more casts in the mage arena");
								return;
							}
						}
						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}
						if (getPlayer().getLocation().inMageArena()) {
							final String godSpellCastCacheKey = getGodSpellCastCacheKey(spellEnum, spell);
							if (getPlayer().getCache().hasKey(godSpellCastCacheKey)) {
								int casts = getPlayer().getCache().getInt(godSpellCastCacheKey);
								getPlayer().getCache().set(godSpellCastCacheKey, casts + 1);
								if (casts == 99) {
									getPlayer().message("Well done .. you can now use the " + spell.getName() + " outside the arena");
								}
							} else {
								getPlayer().getCache().set(godSpellCastCacheKey, 1);
							}
						}

						boolean giveExp = true;
						if (affectedMob.getRegion().getGameObject(affectedMob.getLocation(), getPlayer()) == null) {
							// Authentically, the Guthix god spell only gave XP if the opponent was not stat drained already. Just RSC things...
							if (affectedMob.getConfig().WANT_BUGGED_CLAWS_XP && isClaws && affectedMob.getSkills().getLevel(Skill.DEFENSE.id()) < affectedMob.getSkills().getMaxStat(Skill.DEFENSE.id())) {
								giveExp = false;
							}

							godSpellObject(getPlayer(), affectedMob, spellEnum);
						}
						final int godSpellProjectile = getGodSpellProjectileVisual(spellEnum);
						final int godSpellImpact = getGodSpellImpactEffect(spellEnum);
						final int primaryDamage = CombatFormula.calculateGodSpellDamage(getPlayer(), affectedMob, spellEnum);
						getPlayer().getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob,
							primaryDamage, 1, setChasing,
							0, 0, 0, 0, godSpellProjectile, godSpellImpact, godSpellImpact <= 0));
						getPlayer().getWorld().getServer().getGameEventHandler().add(new MiniEvent(getPlayer().getWorld(), getPlayer(), getPlayer().getConfig().GAME_TICK, "God spell area effect") {
							@Override
							public void action() {
								applyGodSpellAreaEffects(getPlayer(), affectedMob, spellEnum, primaryDamage);
							}
						});
						finalizeSpell(getPlayer(), spell, DEFAULT, giveExp);
						break;

					case CHILL_BOLT:
					case SHOCK_BOLT:
					case ELEMENTAL_BOLT:
					case WIND_BOLT_R:
						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}

						double maxR = getPlayer().getWorld().getServer().getConstants().getSpellDamages().getSpellDamage(spellEnum, entityType, SpellDamages.MagicType.GOODEVILMAGIC);
						maxR = applyElementalRingDamageBonus(getPlayer(), spellEnum, maxR);

						int damageR = CombatFormula.calculateMagicDamage(getPlayer(), affectedMob, maxR);

						getPlayer().getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, damageR, 1, setChasing));
						getPlayer().setKillType(KillType.MAGIC);
						finalizeSpell(getPlayer(), spell, DEFAULT);
						break;

					default:
						if (!checkAndRemoveRunes(getPlayer(), spell, capeActivated)) {
							return;
						}
						// SALARIN THE TWISTED - STRIKE SPELLS
						if (affectedMob.getID() == NpcId.SALARIN_THE_TWISTED.id() && (spellEnum == Spells.WIND_STRIKE
							|| spellEnum == Spells.WATER_STRIKE || spellEnum == Spells.EARTH_STRIKE
							|| spellEnum == Spells.FIRE_STRIKE)) {
							int firstDamage = 0;
							final int secondAdditionalDamage;
							if (spellEnum == Spells.FIRE_STRIKE) {
								firstDamage = 12;
								secondAdditionalDamage = DataConversions.getRandom().nextInt(5); // 4 // max.
							} else if (spellEnum == Spells.EARTH_STRIKE) {
								firstDamage = 11;
								secondAdditionalDamage = DataConversions.getRandom().nextInt(4); // 3 // max.
							} else if (spellEnum == Spells.WATER_STRIKE) {
								firstDamage = 10;
								secondAdditionalDamage = DataConversions.getRandom().nextInt(3); // 2 // max.
							} else {
								firstDamage = 9;
								secondAdditionalDamage = DataConversions.getRandom().nextInt(2); // 1 // max														// max.
							}
							// Shout message from NPC when being maged
							affectedMob.getUpdateFlags().setChatMessage(new ChatMessage(affectedMob, "Aaarrgh my head", getPlayer()));
							// Deal first damage
							getPlayer().getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, firstDamage, 1));
							// Deal Second Damage
							getPlayer().getWorld().getServer().getGameEventHandler().add(new MiniEvent(getPlayer().getWorld(), getPlayer(), getPlayer().getConfig().GAME_TICK, "Salarin the Twisted Strike") {
								@Override
								public void action() {
									int appliedSecondDamage = secondAdditionalDamage;
									if (affectedMob.isPlayer()) {
										appliedSecondDamage = ((Player) affectedMob).applyPotionMagicDamageReduction(appliedSecondDamage);
									}
									int secondDamageLastHits = affectedMob.getSkills().getLevel(Skill.HITS.id());
									affectedMob.getSkills().subtractLevel(Skill.HITS.id(), appliedSecondDamage, false);
									affectedMob.getUpdateFlags().setDamage(new Damage(affectedMob, appliedSecondDamage));
									if (affectedMob.isNpc() && appliedSecondDamage > 0) {
										((Npc) affectedMob).addMageDamage(getPlayer(), Math.min(appliedSecondDamage, secondDamageLastHits));
									}
									if (affectedMob.isPlayer()) {
										if (getPlayer().getConfig().WANT_PARTIES) {
											if(getPlayer().getParty() != null){
												getPlayer().getParty().sendParty();
											}
										}
									}
									if (affectedMob.getSkills().getLevel(Skill.HITS.id()) <= 0) {
										affectedMob.killedBy(getPlayer());
									}

								}
							});
							// Send finalize spell without giving XP
							getPlayer().lastCast = System.currentTimeMillis();
							getPlayer().playerServerMessage(MessageType.QUEST, "Cast spell successfully");
							// Note: it is authentic not to play the "spellok" sound when casting mind spells on Salarin. See kRiStOf/Salarin The Twisted
							getPlayer().setCastTimer();
							return;
						}

						double max = getPlayer().getWorld().getServer().getConstants().getSpellDamages().getSpellDamage(spellEnum, entityType, SpellDamages.MagicType.MODERNMAGIC);
						max = applyElementalRingDamageBonus(getPlayer(), spellEnum, max);

						// Chaos gauntlets let mind-rune spells hit at the chaos-rune spell cap.
						final boolean chaosGauntletBonus = getPlayer().getCarriedItems().getEquipment().hasEquipped(ItemId.GAUNTLETS_OF_CHAOS.id())
							&& getPlayer().getCache().getInt("famcrest_gauntlets") == Gauntlets.CHAOS.id();
						final double damageCapPercent = chaosGauntletBonus && isMindSpell(spellEnum)
							? 0.60D
							: getSpellDamageCapPercent(spellEnum);

						int damage = CombatFormula.calculateMagicDamage(getPlayer(), affectedMob, max, damageCapPercent);
						final int windAccuracyDebuffPercent = isAirSpell(spellEnum) ? getWindAccuracyDebuffPercent(spellEnum) : 0;
						final int waterMaxHitDebuffPercent = isWaterSpell(spellEnum) ? getWaterMaxHitDebuffPercent(spellEnum) : 0;
						final int earthAttackSpeedDebuffPercent = isEarthSpell(spellEnum) ? getEarthAttackSpeedDebuffPercent(spellEnum) : 0;
						final int fireDefenseDebuffPercent = isFireSpell(spellEnum) ? getFireDefenseDebuffPercent(spellEnum) : 0;
						final int startleProcChancePercent = getStartleProcChancePercent(spellEnum);
						final int acidPoisonPower = getAcidPoisonPower(spellEnum);
						final int frostbiteProcChancePercent = getFrostbiteProcChancePercent(spellEnum);
						final int splinterProcChancePercent = getSplinterProcChancePercent(spellEnum);

						final int projectileVisual = getSpellProjectileVisual(spellEnum);
						final int impactEffect = getSpellImpactEffect(spellEnum);
						magicDebug(getPlayer(), "spell_projectile_enqueue spell=" + describeSpell(spellEnum, spell)
							+ " target=" + describeMob(affectedMob) + " damage=" + damage
							+ " max=" + max + " projectile=" + projectileVisual + " impact=" + impactEffect);
						getPlayer().getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, damage, 1, setChasing,
							windAccuracyDebuffPercent, waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent,
							projectileVisual, impactEffect, impactEffect <= 0,
							startleProcChancePercent, acidPoisonPower, frostbiteProcChancePercent, splinterProcChancePercent));
						getPlayer().setKillType(KillType.MAGIC);
						finalizeSpell(getPlayer(), spell, DEFAULT);
						break;
				}
			}
		});
	}

	private boolean isBoostSpell(Player player, Spells spellEnum) {
		return spellEnum == Spells.THICK_SKIN || spellEnum == Spells.BURST_OF_STRENGTH
			|| spellEnum == Spells.CAMOFLAUGE || spellEnum == Spells.ROCK_SKIN;
	}

	private boolean isHealSpell(Spells spellEnum) {
		return spellEnum == Spells.WEAK_HEAL
			|| spellEnum == Spells.STRONG_HEAL;
	}

	private Mob getSelfHealRetaliationTarget(Player player, SpellStruct payload) {
		if (payload.getOpcode() != OpcodeIn.CAST_ON_SELF || !isHealSpell(payload.spell) || !player.getAutoRetaliate()) {
			return null;
		}

		Mob retaliationTarget = getIncomingPvmAttacker(player, player.getOpponent());
		if (retaliationTarget != null) {
			return retaliationTarget;
		}

		retaliationTarget = getIncomingPvmAttacker(player, player.getLastOpponent());
		if (retaliationTarget != null) {
			return retaliationTarget;
		}

		PvmMeleeEvent outgoingAttack = player.getPvmMeleeEvent();
		if (outgoingAttack != null && outgoingAttack.isRunning()) {
			retaliationTarget = getIncomingPvmAttacker(player, outgoingAttack.getTarget());
			if (retaliationTarget != null) {
				return retaliationTarget;
			}
		}

		Mob closestAttacker = null;
		int closestDistanceSquared = Integer.MAX_VALUE;
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			retaliationTarget = getIncomingPvmAttacker(player, npc);
			if (retaliationTarget == null) {
				continue;
			}
			int distanceSquared = getDistanceSquared(player, retaliationTarget);
			if (distanceSquared < closestDistanceSquared) {
				closestAttacker = retaliationTarget;
				closestDistanceSquared = distanceSquared;
			}
		}
		return closestAttacker;
	}

	private Mob getIncomingPvmAttacker(Player player, Mob possibleAttacker) {
		if (possibleAttacker == null || !possibleAttacker.isNpc() || possibleAttacker.isRemoved()
			|| possibleAttacker.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return null;
		}

		PvmMeleeEvent incomingAttack = possibleAttacker.getPvmMeleeEvent();
		if (incomingAttack == null || !incomingAttack.isRunning() || incomingAttack.getTarget() != player) {
			return null;
		}
		return possibleAttacker;
	}

	private int getDistanceSquared(Mob first, Mob second) {
		int dx = first.getX() - second.getX();
		int dy = first.getY() - second.getY();
		return dx * dx + dy * dy;
	}

	private void resumeAutoRetaliateAfterSelfHeal(Player player, Mob retaliationTarget) {
		if (retaliationTarget == null || !player.getAutoRetaliate()
			|| player.getSkills().getLevel(Skill.HITS.id()) <= 0
			|| retaliationTarget.isRemoved()
			|| retaliationTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}

		PvmMeleeEvent incomingAttack = retaliationTarget.getPvmMeleeEvent();
		if (incomingAttack == null || !incomingAttack.isRunning() || incomingAttack.getTarget() != player) {
			return;
		}

		PvmMeleeEvent outgoingAttack = player.getPvmMeleeEvent();
		if (outgoingAttack != null && outgoingAttack.isRunning()) {
			return;
		}

		if (!player.checkAttack(retaliationTarget, false)) {
			return;
		}
		player.startPvmCounterCombat(retaliationTarget);
	}

	private void applyGodSpellAreaEffects(final Player caster, final Mob primaryTarget, final Spells spellEnum, final int primaryDamage) {
		final boolean advancedSpell = isAdvancedGodSpell(spellEnum);
		final double secondaryDamagePercent = advancedSpell ? 0.50D : 0.25D;
		final int secondaryMax = Math.max(1, (int) Math.ceil(CombatFormula.getGodSpellMax(caster, advancedSpell) * secondaryDamagePercent));
		int totalDamage = Math.max(0, primaryDamage);

		for (Npc npc : caster.getViewArea().getNpcsInView()) {
			if (npc == primaryTarget || !isValidGodSpellAreaTarget(caster, primaryTarget, npc)) {
				continue;
			}
			final int damage = CombatFormula.calculateMagicDamage(caster, npc, secondaryMax);
			final int appliedDamage = applyGodSpellSecondaryDamage(caster, npc, damage);
			totalDamage += appliedDamage;
			applyGodSpellSpecialEffect(caster, npc, spellEnum, appliedDamage, false);
		}

		applyGodSpellSpecialEffect(caster, primaryTarget, spellEnum, primaryDamage, true);
		applyGodSpellLifesteal(caster, spellEnum, totalDamage);
	}

	private boolean isValidGodSpellAreaTarget(final Player caster, final Mob primaryTarget, final Mob possibleTarget) {
		if (possibleTarget == null || possibleTarget.isRemoved()
			|| possibleTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}
		if (!primaryTarget.getLocation().withinRange(possibleTarget.getLocation(), 2)) {
			return false;
		}
		if (possibleTarget.isNpc()) {
			return true;
		}
		if (!possibleTarget.isPlayer()) {
			return false;
		}
		Player targetPlayer = (Player) possibleTarget;
		return caster.getLocation().inWilderness() && targetPlayer.getLocation().inWilderness();
	}

	private int applyGodSpellSecondaryDamage(final Player caster, final Mob target, final int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return 0;
		}
		final int lastHits = target.getSkills().getLevel(Skill.HITS.id());
		target.damage(damage);
		final int appliedDamage = Math.min(damage, lastHits);
		if (target.isNpc()) {
			Npc npc = (Npc) target;
			npc.addMageDamage(caster, appliedDamage);
			if (!npc.isChasing() && !npc.inCombat() && npc.getCombatState() != CombatState.RUNNING) {
				npc.setChasing(caster);
			}
		}
		return appliedDamage;
	}

	private void applyGodSpellSpecialEffect(final Player caster, final Mob target, final Spells spellEnum,
											final int damage, final boolean primaryTarget) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final boolean advancedSpell = isAdvancedGodSpell(spellEnum);
		if (isGuthixGodSpell(spellEnum)) {
			applyGuthixGodSpellPoison(caster, target, advancedSpell, primaryTarget);
			return;
		}
		if (isZamorakGodSpell(spellEnum)) {
			applyZamorakWithering(target, advancedSpell);
		}
	}

	private void applyGuthixGodSpellPoison(final Player caster, final Mob target, final boolean advancedSpell,
										   final boolean primaryTarget) {
		if (primaryTarget) {
			target.applyPoison(advancedSpell ? 40 : 20, advancedSpell ? 80 : 40);
		} else {
			final double chance = advancedSpell ? 0.50D : 0.25D;
			if (DataConversions.getRandom().nextDouble() >= chance) {
				return;
			}
			target.applyPoison(advancedSpell ? 20 : 10, advancedSpell ? 40 : 20);
		}
		if (target.isNpc()) {
			caster.message("@gr3@You @gr2@have @gr1@poisioned @gr2@the " + ((Npc) target).getDef().name + "!");
		}
	}

	private void applyZamorakWithering(final Mob target, final boolean advancedSpell) {
		final int percent = advancedSpell ? 20 : 10;
		target.applyWindDebuff(percent);
		target.applyWaterMaxHitDebuff(percent);
		target.applyEarthAttackSpeedDebuff(percent);
		target.applyFireDefenseDebuff(percent);
	}

	private void applyGodSpellLifesteal(final Player caster, final Spells spellEnum, final int totalDamage) {
		if (!isSaradominGodSpell(spellEnum) || totalDamage <= 0) {
			return;
		}
		final int currentHits = caster.getSkills().getLevel(Skill.HITS.id());
		final int maxHits = caster.getSkills().getMaxStat(Skill.HITS.id());
		if (currentHits >= maxHits) {
			return;
		}
		final double lifestealPercent = isAdvancedGodSpell(spellEnum) ? 0.20D : 0.10D;
		final int healing = Math.max(1, (int) Math.floor(totalDamage * lifestealPercent));
		final int nextHits = Math.min(maxHits, currentHits + healing);
		caster.getSkills().setLevel(Skill.HITS.id(), nextHits, true);
		final int healed = nextHits - currentHits;
		if (healed > 0) {
			caster.getUpdateFlags().addHitSplat(new HitSplat(caster, HitSplat.TYPE_HEAL, healed));
			ActionSender.sendStat(caster, Skill.HITS.id());
		}
	}

	private boolean isTeleportSpell(Spells spellEnum) {
		return spellEnum == Spells.VARROCK_TELEPORT
			|| spellEnum == Spells.LUMBRIDGE_TELEPORT
			|| spellEnum == Spells.FALADOR_TELEPORT
			|| spellEnum == Spells.CAMELOT_TELEPORT
			|| spellEnum == Spells.ARDOUGNE_TELEPORT
			|| spellEnum == Spells.WATCHTOWER_TELEPORT;
	}

	private boolean canTeleport(Player player, SpellDef spell, Spells spellEnum) {
		boolean canTeleport = true;
		if (player.getLocation().wildernessLevel() >= 20 || player.getLocation().isInFisherKingRealm()
			|| player.getLocation().isInsideGrandTreeGround()
			|| (player.getLocation().inModRoom() && !player.isAdmin())) {
			player.message("A mysterious force blocks your teleport spell!");
			player.message("You can't use teleport after level 20 wilderness");
			canTeleport = false;
		}
		// if (player.getLocation().inWilderness() && System.currentTimeMillis() - player.getCombatTimer() < 10000) {
		//	player.message("You need to stay out of combat for 10 seconds before using a teleport.");
		//	return;
		//}
		else if (player.getCarriedItems().getInventory().countId(ItemId.ANA_IN_A_BARREL.id()) > 0) {
			mes("You can't teleport while holding Ana,");
			delay(3);
			mes("It's just too difficult to concentrate.");
			delay(3);
			canTeleport = false;
		}
		else if (!player.getCache().hasKey("ardougne_scroll") && spellEnum == Spells.ARDOUGNE_TELEPORT) {
			player.message("You don't know how to cast this spell yet");
			player.message("You need to do the plague city quest");
			canTeleport = false;
		}
		else if (!player.getCache().hasKey("watchtower_scroll") && spellEnum == Spells.WATCHTOWER_TELEPORT) {
			player.message("You cannot cast this spell");
			player.message("You need to finish the watchtower quest first");
			canTeleport = false;
		}
		if (player.getLocation().inModRoom()) {
			canTeleport = false;
		}
		return canTeleport;
	}

	private void handleTeleport(Player player, SpellDef spell, Spells spellEnum) {
		if (!checkAndRemoveRunes(player, spell)) {
			return;
		}
		player.resetAllExceptDueling();
		player.setBusy(true);
		player.message("You begin charging the teleport spell");
		player.getWorld().getServer().getGameEventHandler().add(new MiniEvent(player.getWorld(), player, TELEPORT_CHARGE_MS, "Teleport spell charge") {
			@Override
			public void action() {
				try {
					if (player.isRemoved() || player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
						return;
					}
					completeTeleport(player, spell, spellEnum);
				} finally {
					player.setBusy(false);
				}
			}
		});
	}

	private void completeTeleport(Player player, SpellDef spell, Spells spellEnum) {
		if (player.getLocation().inKaramja() || player.getLocation().inBrimhaven()) {
			while (player.getCarriedItems().getInventory().countId(ItemId.KARAMJA_RUM.id()) > 0) {
				player.getCarriedItems().remove(new Item(ItemId.KARAMJA_RUM.id()));
			}
		}
		if (player.getCarriedItems().hasCatalogID(ItemId.KARAMJA_RUM.id()) && (player.getLocation().inKaramja())) {
			player.getCarriedItems().remove(new Item(ItemId.KARAMJA_RUM.id()));
		}
		if (player.getCarriedItems().hasCatalogID(ItemId.PLAGUE_SAMPLE.id())) {
			player.message("the plague sample is too delicate...");
			player.message("it disintegrates in the crossing");
			while (player.getCarriedItems().getInventory().countId(ItemId.PLAGUE_SAMPLE.id()) > 0) {
				player.getCarriedItems().remove(new Item(ItemId.PLAGUE_SAMPLE.id()));
			}
		}
		switch (spellEnum) {
			case VARROCK_TELEPORT:
				player.teleport(120, 504, true);
				break;
			case LUMBRIDGE_TELEPORT:
				player.teleport(120, 648, true);
				break;
			case FALADOR_TELEPORT:
				player.teleport(312, 552, true);
				break;
			case CAMELOT_TELEPORT:
				player.teleport(456, 456, true);
				break;
			case ARDOUGNE_TELEPORT:
				player.teleport(588, 621, true);
				break;
			case WATCHTOWER_TELEPORT:
				player.teleport(493, 3525, true);
				break;
			default:
				break;
		}
		finalizeSpellNoMessage(player, spell);
	}

	private void handleBoost(Player player, SpellDef spell, Spells spellEnum) {
		switch (spellEnum) {
			case BURST_OF_STRENGTH:
			case CAMOFLAUGE:
			case ROCK_SKIN:
			case THICK_SKIN:
				double raisesBy = 0.0;
				int affectedStat = -1;
				if (spellEnum == Spells.BURST_OF_STRENGTH) {
					raisesBy = 0.05;
					affectedStat = Skill.STRENGTH.id();
				} else if (spellEnum == Spells.THICK_SKIN) {
					raisesBy = 0.05;
					affectedStat = Skill.DEFENSE.id();
				} else if (spellEnum == Spells.ROCK_SKIN) {
					raisesBy = 0.10;
					affectedStat = Skill.DEFENSE.id();
				} else if (spellEnum == Spells.CAMOFLAUGE) {
					affectedStat = Skill.NONE.id();
				}
				if (affectedStat != Skill.NONE.id()) {
					affectedStat = CombatEffectUtil.remapLegacyPlayerMeleeStat(player, affectedStat);
				}

				if (!checkAndRemoveRunes(player, spell)) {
					return;
				}
				if (affectedStat != Skill.NONE.id()) {
					/* How much to boost the stat */
					int baseStat = player.getSkills().getLevel(affectedStat) > player.getSkills().getMaxStat(affectedStat) ? player.getSkills().getMaxStat(affectedStat) : player.getSkills().getLevel(affectedStat);
					if (player.getConfig().WAIT_TO_REBOOST && !isNormalLevel(player, affectedStat)) {
						player.playerServerMessage(MessageType.QUEST, "You already have boosted " + player.getWorld().getServer().getConstants().getSkills().getSkillName(affectedStat));
						return;
					}
					int newStat = baseStat
						+ DataConversions.roundUp(player.getSkills().getMaxStat(affectedStat) * raisesBy);
					boolean sendUpdate = player.getClientLimitations().supportsSkillUpdate;
					if (newStat > player.getSkills().getLevel(affectedStat)) {
						player.getSkills().setLevel(affectedStat, newStat, sendUpdate);
						if (!sendUpdate) {
							player.getSkills().sendUpdateAll();
						}
					}
				}
				finalizeSpell(player, spell, DEFAULT);
				return;
		}
	}

	private void handleHeal(Player player, SpellDef spell, Spells spellEnum, Mob retaliationTarget) {
		try {
			final long now = System.currentTimeMillis();
			final long cooldownUntil = player.getAttribute("heal_spell_cooldown_until", 0L);
			if (cooldownUntil > now) {
				int seconds = Math.max(1, (int) Math.ceil((cooldownUntil - now) / 1000.0D));
				player.message("You need to wait " + seconds + " seconds before casting another heal spell");
				return;
			}
			final int currentHits = player.getSkills().getLevel(Skill.HITS.id());
			final int maxHits = player.getSkills().getMaxStat(Skill.HITS.id());
			if (currentHits >= maxHits) {
				player.message("You are already at full health");
				return;
			}

			if (!checkAndRemoveRunes(player, spell)) {
				return;
			}

			int healAmount = 0;
			switch (spellEnum) {
				case WEAK_HEAL:
					healAmount = 3;
					break;
				case STRONG_HEAL:
					healAmount = 12;
					break;
				default:
					return;
			}

			final int nextHits = Math.min(maxHits, currentHits + healAmount);
			player.getSkills().setLevel(Skill.HITS.id(), nextHits, true);
			final int healed = nextHits - currentHits;
			if (healed > 0) {
				player.getUpdateFlags().addHitSplat(new HitSplat(player, HitSplat.TYPE_HEAL, healed));
			}
			final int effectType = getHealCombatEffect(spellEnum);
			if (effectType > 0) {
				player.getUpdateFlags().setCombatEffect(new CombatEffect(player, effectType));
			}
			player.setAttribute("heal_spell_cooldown_until", now + HEAL_SPELL_COOLDOWN_MS);
			finalizeSpell(player, spell, DEFAULT, true, false);
		} finally {
			resumeAutoRetaliateAfterSelfHeal(player, retaliationTarget);
		}
	}

	private void handleChargeOrb(Player player, GameObject gameObject, Spells spellEnum, SpellDef spell) {
		player.playerServerMessage(MessageType.QUEST,
			"Orb charging has been retired. Use a staff on the matching altar through Enchanting instead.");
	}

}
