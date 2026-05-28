package com.openrsc.server.event.rsc.impl.combat;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.PoisonProcChance;
import com.openrsc.server.content.PoisonPower;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Equipment.EquipmentSlot;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcBehavior;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.PidShuffler;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;

public class CombatEvent extends GameTickEvent {

	private final Mob attackerMob, defenderMob;
	private int roundNumber = 0;
	boolean isPvPCombat = false;
	boolean forceTwoTickRounds = false;
	private double attackerDelayCarry = 0.0D;
	private double defenderDelayCarry = 0.0D;

	public CombatEvent(World world, Mob attacker, Mob defender) {
		super(world, null, 0, "Combat Event", DuplicationStrategy.ONE_PER_MOB);
		this.attackerMob = attacker;
		this.defenderMob = defender;
		//Reset retreat timers so it is possible to use spells if a retreating enemy attacks someone new before their timer expires.
		attackerMob.resetRanAwayTimer();
		defenderMob.resetRanAwayTimer();
		if (attackerMob.isPlayer() && defenderMob.isPlayer()) this.isPvPCombat = true;

		if (attackerMob.isNpc() && defenderMob.isPlayer()) forceTwoTickRounds = true;

		if (isPvPCombat) {
			//Dueling in RSC was always 2-2.
			if (((Player) attackerMob).getDuel().isDueling()) {
				forceTwoTickRounds = true;
			} else if (attackerMob.getConfig().SHUFFLE_PID_ORDER) {
				for (int curPid : PidShuffler.pidProcessingOrder) {
					Player p = getWorld().getPlayer(curPid);
					if (p == attackerMob) {
						//Attacker has lower PID, so we go to 2-2.
						forceTwoTickRounds = true;
						break;
					}
					if (p == defenderMob) {
						//Defender has lower PID, so this combat encounter is 3-1.
						break;
					}
				}
			} else {
				for (Player p : getWorld().getPlayers()) {
					if (p == attackerMob) {
						//Attacker has lower PID, so we go to 2-2.
						forceTwoTickRounds = true;
						break;
					}
					if (p == defenderMob) {
						//Defender has lower PID, so this combat encounter is 3-1.
						break;
					}
				}
			}
		}



		attacker.getWorld().getServer().getCombatScriptLoader().checkAndExecuteOnStartCombatScript(attacker, defender);
		if (attacker.isNpc()) {
			((Npc) attacker).setExecutedAggroScript(false);
		} else if (defender.isNpc()) {
			((Npc) defender).setExecutedAggroScript(false);
		}
	}

	private void onDeath(Mob killed, Mob killer) {

		/* Commented out useless codeblock. Can be put back if these plugins are implemented some day. 2021-03-05
		if (killer.isPlayer() && killed.isNpc()) {
			// this interface doesn't even exist anymore, so this code block is dead, never returns. 2021-03-05
			if (killed.getWorld().getServer().getPluginHandler().handlePlugin((Player)killer, "PlayerKilledNpc", new Object[]{((Player) killer), ((Npc) killed)})) {
				return;
			}
		} else if (killer.isPlayer() && killed.isPlayer()) {
			// no default action currently, so this code block is dead, never returns. 2021-03-05
			if (killed.getWorld().getServer().getPluginHandler().handlePlugin((Player)killer, "PlayerKilledPlayer", new Object[]{((Player) killer), ((Player) killed)})) {
				return;
			}
		}
		*/

		killed.setLastCombatState(CombatState.LOST);
		killer.setLastCombatState(CombatState.WON);

		if (killer.isPlayer()) {
			((Player) killer).applyDeathAmuletBurst(killed);
		}

			if (killed.isPlayer() && killer.isPlayer()) {
				int[] skillsDist = new int[Skill.maxId(Skill.MELEE.name(), Skill.HITS.name()) + 1];

			Player playerKiller = (Player) killer;
			Player playerKilled = (Player) killed;

			int exp = Formulae.combatExperience(playerKilled);
				skillsDist[Skill.MELEE.id()] = 3;
				skillsDist[Skill.HITS.id()] = 1;
				playerKiller.incExp(skillsDist, exp, true);
			}

		// If `killed` is an NPC, xp distribution is handled by Npc.handleXpDistribution()

		killer.setKillType(KillType.COMBAT);
		killed.killedBy(killer);
		if (killer.isPlayer()) {
			updateParty((Player)killer);
		}
	}

	public final void run() {
		//In RSC combat against an NPC, the tick delay is dependent on if the defending mob is a player.
		//If it is, then the tick delay is always 2 ticks.
		//If it isn't, then there is a 3 tick delay after the attacker's round, and a 1 tick delay after the defender's round.
		//In PvP combat in the Wilderness, each combat encounter is assigned 3-1 or 2-2 tick cycles. This seems to be based on PID, based on footage before Jagex implemented PID shuffling in 2016.
		//In duels, combat was *always* 2-2.

		int delayTicks = 0;
		Mob hitter, target = null;

		if (roundNumber++ % 2 == 0) {
			hitter = attackerMob;
			target = defenderMob;
			delayTicks = 3;
		} else {
			hitter = defenderMob;
			target = attackerMob;
			delayTicks = 1;
		}

		if (forceTwoTickRounds) delayTicks = 2;
		setDelayTicks(getAdjustedMeleeDelayTicks(hitter, delayTicks));

		if (!combatCanContinue()) {
			hitter.setLastCombatState(CombatState.ERROR);
			target.setLastCombatState(CombatState.ERROR);
			resetCombat();
		} else {
			hitter.faceCombat(target);

			if (hitter.isNpc() && ((Npc)hitter).getBehavior().shouldRetreat(((Npc)hitter)) && target.getHitsMade() >= 3) {
				//Authentically, retreating enemies retreat on their turn but before they do damage.
				((Npc)hitter).getBehavior().retreat();
				return;
			}

			//if(hitter.isNpc() && target.isPlayer() || target.isNpc() && hitter.isPlayer()) {
			int damage;
			if (getWorld().getServer().getConfig().OSRS_COMBAT_MELEE) {
				damage = OSRSCombatFormula.Melee.doMeleeDamage(hitter, target);
			} else {
				damage = CombatFormula.doMeleeDamage(hitter, target);
			}
			if (hitter.isPlayer()) {
				damage = applyPlayerMeleeDamageBuff((Player) hitter, damage);
			}
			if (hitter.consumeOgreStaggerDebuff() || hitter.consumeStartleDebuff()) {
				damage = 0;
			}

			inflictDamage(hitter, target, damage);
			applyWeaponPoison(hitter, target, damage);
			applyChaosAmuletSecondHit(hitter, target, damage);
			if (target.isPlayer()) {
				final Player targetPlayer = (Player) target;
				final double recoilChance = targetPlayer.getCarriedItems().getEquipment().getChaosRecoilChance();
				if (recoilChance > 0.0D) {
					final double recoilRoll = DataConversions.getRandom().nextDouble();
					final int divisor = targetPlayer.getCarriedItems().getEquipment().getChaosRecoilDamageDivisor();
					final int reflectedDamage = damage / divisor + ((damage > 0) ? 1 : 0);
					final boolean proc = recoilRoll < recoilChance;
					if (proc && reflectedDamage > 0) {
						inflictJewelryEffectDamage(target, hitter, reflectedDamage);
					}
				}
			}
			hitter.consumeAttackBasedDebuffs();
			if (hitter.isPlayer()) {
				((Player) hitter).consumeLeatherSetAttackBuffs();
			}
		}
	}

	private void applyChaosAmuletSecondHit(final Mob hitter, final Mob target, final int baseDamage) {
		if (!hitter.isPlayer() || baseDamage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Player player = (Player) hitter;
		final double procChance = player.getCarriedItems().getEquipment().getChaosAmuletSecondHitChance();
		if (procChance <= 0.0D) {
			return;
		}
		final double roll = DataConversions.getRandom().nextDouble();
		if (roll >= procChance) {
			return;
		}
		final Mob splashTarget = selectChaosAmuletSplashTarget(player, target);
		if (splashTarget == null) {
			return;
		}
		final int extraDamage = Math.max(1, (int) Math.ceil(baseDamage / 2.0D));
		inflictJewelryEffectDamage(hitter, splashTarget, extraDamage);
	}

	private void inflictJewelryEffectDamage(final Mob hitter, final Mob target, final int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addCombatDamage((Player) hitter, Math.min(damage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, hitter);
		}
	}

	private int applyFrostbiteReflection(final Mob hitter, final Mob target, int incomingDamage) {
		if (incomingDamage <= 0) {
			return incomingDamage;
		}
		final Mob source = hitter.consumeFrostbiteSource();
		if (source == null) {
			return incomingDamage;
		}
		final int reflectedDamage = Math.max(1, (int) Math.ceil(incomingDamage / 2.0D));
		inflictFrostbiteReflectedDamage(source, hitter, reflectedDamage);
		return Math.max(0, incomingDamage - reflectedDamage);
	}

	private void inflictFrostbiteReflectedDamage(final Mob source, final Mob target, final int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Mob creditedSource = source != null ? source : target;
		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && creditedSource.isPlayer()) {
			((Npc) target).addMageDamage((Player) creditedSource, Math.min(damage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, creditedSource);
		}
	}

	private Mob selectChaosAmuletSplashTarget(final Player player, final Mob primaryTarget) {
		if (!primaryTarget.isNpc()) {
			return primaryTarget;
		}
		final java.util.ArrayList<Npc> candidates = new java.util.ArrayList<Npc>();
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (npc != null && !npc.isRemoved() && npc.getSkills().getLevel(Skill.HITS.id()) > 0
				&& npc.withinRange(primaryTarget.getLocation(), 4)) {
				candidates.add(npc);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		return candidates.get(DataConversions.random(0, candidates.size() - 1));
	}

	private void applyWeaponPoison(final Mob hitter, final Mob target, final int damage) {
		if (!hitter.isPlayer() || damage <= 0) {
			return;
		}
		final Player player = (Player) hitter;
		player.removeAttribute("dragon_breath_armor_proc");
		final Item weapon = player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_MAINHAND.getIndex());
		final int poisonWeaponId = weapon == null ? -1 : weapon.getCatalogId();
		final int weaponMaxPower = PoisonPower.getWeaponMaxPoisonPower(poisonWeaponId);
		final int styleArmorMaxPower = player.getMeleePoisonArmorMaxPower();
		final int breathArmorMaxPower = player.hasFullBlackDragonSet() ? 30 : (player.hasFullKingBlackDragonSet() ? 40 : 0);
		final int armorMaxPower = styleArmorMaxPower + breathArmorMaxPower;
		final int totalMaxPower = weaponMaxPower + armorMaxPower;
		if (totalMaxPower <= 0) {
			return;
		}

		int appliedPoisonPower = 0;
		if (weaponMaxPower > 0 && PoisonProcChance.rollWeapon(player, target, poisonWeaponId)) {
			appliedPoisonPower = Math.max(appliedPoisonPower, PoisonPower.getWeaponAppliedPoisonPower(poisonWeaponId));
		}
		if (styleArmorMaxPower > 0 && PoisonProcChance.rollArmor(player, target, "melee")) {
			appliedPoisonPower = Math.max(appliedPoisonPower, player.getMeleePoisonArmorAppliedPower());
		}
		if (player.hasFullBlackDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			appliedPoisonPower = Math.max(appliedPoisonPower, 15);
			player.setAttribute("dragon_breath_armor_proc", "black");
		} else if (player.hasFullKingBlackDragonSet() && DataConversions.getRandom().nextDouble() < 0.60D) {
			appliedPoisonPower = Math.max(appliedPoisonPower, 20);
			player.setAttribute("dragon_breath_armor_proc", "kbd");
		}
		if (appliedPoisonPower <= 0) {
			return;
		}

		target.applyPoison(appliedPoisonPower, totalMaxPower);
		if (target.isNpc()) {
			player.message("@gr3@You @gr2@have @gr1@poisioned @gr2@the " + ((Npc) target).getDef().name + "!");
		}
	}

	private int getAdjustedMeleeDelayTicks(Mob hitter, int baseDelayTicks) {
		double multiplier = getCombatSpeedMultiplier(hitter);
		if (multiplier == 1.0D) {
			return baseDelayTicks;
		}

		double effectiveDelay = baseDelayTicks / multiplier;
		if (hitter == attackerMob) {
			effectiveDelay += attackerDelayCarry;
		} else if (hitter == defenderMob) {
			effectiveDelay += defenderDelayCarry;
		}

		int adjustedDelay = Math.max(1, (int) Math.floor(effectiveDelay));
		double carry = Math.max(0.0D, effectiveDelay - adjustedDelay);
		if (hitter == attackerMob) {
			attackerDelayCarry = carry;
		} else if (hitter == defenderMob) {
			defenderDelayCarry = carry;
		}
		return adjustedDelay;
	}

	private double getCombatSpeedMultiplier(Mob hitter) {
		double multiplier = 1.0D;
		if (hitter.isPlayer()) {
			Player player = (Player) hitter;
			multiplier = getWeaponSpeedMultiplier(player) * player.getPotionAttackSpeedMultiplier() * player.getLeatherSetAttackSpeedMultiplier();
		}
		if (hitter.getEarthAttackSpeedDebuffPercent() > 0) {
			multiplier *= Math.max(0.10D, 1.0D - (hitter.getEarthAttackSpeedDebuffPercent() / 100.0D));
		}
		return multiplier;
	}

	private double getWeaponSpeedMultiplier(Player player) {
		int weaponSpeed = player.getCarriedItems().getEquipment().getWeaponSpeed();
		double weaponMultiplier;
		switch (weaponSpeed) {
			case 1:
				weaponMultiplier = 0.8D;
				break;
			case 2:
				weaponMultiplier = 0.9D;
				break;
			case 4:
				weaponMultiplier = 1.1D;
				break;
			case 5:
				weaponMultiplier = 1.2D;
				break;
			case 3:
			default:
				weaponMultiplier = 1.0D;
				break;
		}
		return weaponMultiplier * player.getArmorSpeedMultiplier();
	}

	private void inflictDamage(final Mob hitter, final Mob target, int damage) {
		hitter.incHitsMade();
		damage = Summoning.applySummonOutgoingDamage(hitter, damage);

		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			// side effects that may occur during combat (like poison) are regardless protect
			hitter.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatSideEffectScript(hitter, target);

			if (hitter.isNpc()) {
				// If the hitter is an NPC, we want to check and execute their combat script
				// However if the player has the paralyze prayer on, we just want to return
				// so that the NPC is stopped from damaging the player.
				if (!targetPlayer.getConfig().WANT_MYWORLD && targetPlayer.getPrayers().isPrayerActivated(Prayers.PARALYZE_MONSTER)) {
					return;
				} else {
					hitter.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatScript(hitter, target);
				}
			}
		}

		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			damage = targetPlayer.applyRobeDamageMitigation(damage);
			damage = targetPlayer.applyPotionMeleeDamageReduction(damage);
			if (hitter.isNpc()) {
				damage = Summoning.applySummonDamageAbsorption(targetPlayer, hitter, damage);
			}
		}
		damage = applyFrostbiteReflection(hitter, target, damage);

		// Reduce targets hits by supplied damage amount.
		int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_STANDARD, damage));
		if (target.isNpc() && hitter.isPlayer()) {
			Npc n = (Npc) target;
			Player player = ((Player) hitter);
			damage = Math.min(damage, lastHits);
			n.addCombatDamage(player, damage);
			player.applyBloodAmuletLifesteal(damage);
		}

		// Update players sound and party.
		if (target.isPlayer()) {
			sendSound((Player)target, hitter, damage > 0);
			ActionSender.sendStat((Player)target, Skill.HITS.id());
			updateParty((Player)target);
			applyChaosRobeReflect((Player) target, hitter, damage);
		}
		if (hitter.isPlayer()) {
			sendSound((Player)hitter, target, damage > 0);
			updateParty((Player)hitter);
		}

		if (target.getSkills().getLevel(Skill.HITS.id()) > 0) {

			// NPCs can run special combat scripts.
			// Custom: Ring of Life execution
			boolean ringOfLifeScript = false;
			if (target.isPlayer()) {
				Player player = (Player)target;
				ringOfLifeScript = !player.getDuel().isDuelActive() && player.checkRingOfLife(hitter);
			}
			if (target.isNpc() || ringOfLifeScript) {
				target.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatScript(hitter, target);
			}
			applyLeatherSetOnHitEffects(hitter, target, damage);
		}

		// Mob has <= 0 hits.
		else {
			onDeath(target, hitter);
		}
	}

	private void applyChaosRobeReflect(final Player defender, final Mob attacker, final int damage) {
		if (damage <= 0 || attacker.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final double reflectPercent = defender.getChaosRobeReflectPercent();
		if (reflectPercent <= 0.0D) {
			return;
		}
		final int reflectedDamage = Math.max(1, (int) Math.ceil(damage * reflectPercent));
		attacker.getSkills().subtractLevel(Skill.HITS.id(), reflectedDamage, false);
		attacker.getUpdateFlags().setDamage(new Damage(attacker, reflectedDamage));
		attacker.getUpdateFlags().addHitSplat(new HitSplat(attacker, HitSplat.TYPE_ARMOR_PROC, reflectedDamage));
		if (attacker.isPlayer()) {
			ActionSender.sendStat((Player) attacker, Skill.HITS.id());
		}
		if (attacker.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(attacker, defender);
		}
	}

	private int inflictAuxiliaryMagicDamage(final Mob hitter, final Mob target, int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return 0;
		}
		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			damage = targetPlayer.applyRobeDamageMitigation(damage);
			damage = targetPlayer.applyPotionMagicDamageReduction(damage);
		}
		if (damage <= 0) {
			return 0;
		}

		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		final int damageDealt = Math.min(damage, lastHits);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addMageDamage((Player) hitter, damageDealt);
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
			applyChaosRobeReflect((Player) target, hitter, damage);
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, hitter);
		}
		return damageDealt;
	}

	private void inflictAuxiliaryTrueDamage(final Mob hitter, final Mob target, int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		if (target.isPlayer()) {
			damage = ((Player) target).applyRobeDamageMitigation(damage);
		}
		if (damage <= 0) {
			return;
		}

		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addCombatDamage((Player) hitter, Math.min(damage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
			applyChaosRobeReflect((Player) target, hitter, damage);
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, hitter);
		}
	}

	private void applyLeatherSetOnHitEffects(final Mob hitter, final Mob target, final int damage) {
		if (!hitter.isPlayer() || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Player player = (Player) hitter;
		final int intimidatePercent = player.getBearHideIntimidatePercent();
		if (intimidatePercent > 0 && DataConversions.getRandom().nextDouble() < player.getBearHideIntimidateProcChance()) {
			target.applyBearIntimidateDebuff(intimidatePercent);
		}
		if (player.getGoblinEnragedProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < player.getGoblinEnragedProcChance()) {
			player.activateGoblinEnraged();
		}
		if (player.getGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < player.getGiantBruteForceProcChance()) {
			player.activateGiantBruteForce();
		}
		if (player.getMossGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < player.getMossGiantBruteForceProcChance()) {
			player.activateMossGiantBruteForce();
		}
		if (player.getIceGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < player.getIceGiantBruteForceProcChance()) {
			player.activateIceGiantBruteForce();
		}
		if (player.getFireGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < player.getFireGiantBruteForceProcChance()) {
			player.activateFireGiantBruteForce();
		}
		if (player.hasFullOgreSet() && player.isOgreStaggeringBlowReady()
			&& DataConversions.getRandom().nextDouble() < player.getOgreStaggeringBlowProcChance()) {
			target.applyOgreStaggerDebuff();
			player.activateOgreStaggeringBlowCooldown();
		}
		final int smokePercent = player.getBabyDragonSmokeAccuracyDebuffPercent();
		if (smokePercent > 0 && DataConversions.getRandom().nextDouble() < player.getBabyDragonSmokeProcChance()) {
			hitter.getUpdateFlags().setProjectile(new Projectile(hitter, target, Projectile.BLOW_SMOKE));
			target.applySmokeAccuracyDebuff(smokePercent);
		}
		final int infernalMaxHit = player.getInfernalFireProcMaxHit();
		final int infernalPieces = player.getInfernalArmorPieceCount();
		if (infernalMaxHit > 0) {
			final double infernalChance = player.getInfernalFireProcChance();
			final double infernalRoll = DataConversions.getRandom().nextDouble();
			final boolean infernalProc = infernalRoll < infernalChance;
			int procDamage = 0;
			int procDamageDealt = 0;
			if (infernalProc) {
				target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.infernalEffectForMaxHit(infernalMaxHit)));
				procDamage = DataConversions.random(0, infernalMaxHit);
				procDamageDealt = inflictAuxiliaryMagicDamage(hitter, target, procDamage);
				target.applyInfernalFireDefenseDebuff(player.getInfernalFireDefenseDebuffPercent());
			}
			CombatEffectUtil.sendInfernalProcDebug(player, "pvp_melee", target, damage, infernalPieces,
				infernalMaxHit, infernalRoll, infernalChance, infernalProc, procDamage, procDamageDealt);
		} else if (infernalPieces > 0) {
			CombatEffectUtil.sendInfernalProcDebug(player, "pvp_melee", target, damage, infernalPieces,
				infernalMaxHit, -1.0D, 0.0D, false, 0, 0);
		}
		if (player.hasFullBlueDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			target.applyDragonWaterMaxHitDebuff(10);
		}
		if (player.hasFullEarthDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			target.applyDragonEarthAttackSpeedDebuff(6);
		}
		if (player.hasFullRedDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			target.applyDragonFireDefenseDebuff(6);
		}
		if ("black".equals(player.getAttribute("dragon_breath_armor_proc", ""))
			|| "kbd".equals(player.getAttribute("dragon_breath_armor_proc", ""))) {
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.DRAGON_BREATH));
		}
		if (player.hasFullBlackDragonSet() && "black".equals(player.getAttribute("dragon_breath_armor_proc", ""))) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
		}
		if (player.hasFullKingBlackDragonSet() && "kbd".equals(player.getAttribute("dragon_breath_armor_proc", ""))) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			switch (DataConversions.random(0, 2)) {
				case 0:
					target.applyDragonWaterMaxHitDebuff(10);
					break;
				case 1:
					target.applyDragonEarthAttackSpeedDebuff(6);
					break;
				default:
					target.applyDragonFireDefenseDebuff(6);
					break;
			}
		}
	}

	private int applyPlayerMeleeDamageBuff(final Player player, final int damage) {
		if (damage <= 0) {
			return damage;
		}
		return Math.max(0, (int) Math.floor(damage * player.getLeatherSetMeleeDamageMultiplier()));
	}

	// Players in combat with an NPC will receive unique NPC
	// sounds dependent on npc type. Against Player is always combat2
	private void sendSound(Player player, Mob mob, boolean damaged) {
		String combatSound;
		boolean isNpc = mob.isNpc();
		boolean isPlayer = mob.isPlayer();
		if (isPlayer || DataConversions.inArray(Constants.ARMOR_NPCS, ((Npc)mob).getID())) {
			combatSound = damaged ? "combat2b" : "combat2a";
		} else if (isNpc && DataConversions.inArray(Constants.UNDEAD_NPCS, ((Npc)mob).getID())) {
			combatSound = damaged ? "combat3b" : "combat3a";
		} else {
			combatSound = damaged ? "combat1b" : "combat1a";
		}

		ActionSender.sendSound(player, combatSound);
	}

	private void updateParty(Player player) {
		if (getWorld().getServer().getConfig().WANT_PARTIES) {
			if(player.getParty() != null){
				player.getParty().sendParty();
			}
		}
	}

	public void resetCombat() {
		if (running) {
			if (defenderMob != null) {
				if (defenderMob.isPlayer()) {
					Player player = (Player) defenderMob;
					player.resetAll();
				}

				//defenderMob.setBusy(false);
				defenderMob.setOpponent(null);
				defenderMob.setCombatEvent(null);
				defenderMob.setHitsMade(0);
				defenderMob.setSprite(4);
				defenderMob.setCombatTimer();
				defenderMob.face(defenderMob.getX(), defenderMob.getY() - 1);
			}
			if (attackerMob != null) {
				if (attackerMob.isPlayer()) {
					Player player = (Player) attackerMob;
					player.resetAll();
				}

				//attackerMob.setBusy(false);
				attackerMob.setOpponent(null);
				attackerMob.setCombatEvent(null);
				attackerMob.setHitsMade(0);
				attackerMob.setSprite(4);
				attackerMob.setCombatTimer();
				attackerMob.face(attackerMob.getX(), attackerMob.getY() - 1);
			}
		} else {
			// combat event was reset while combat event wasn't running.
			// possible race condition; we will want to clean most things up if this happens.
			if (defenderMob != null) {
				defenderMob.setOpponent(null);
				defenderMob.setCombatEvent(null);
				defenderMob.setHitsMade(0);
				if (defenderMob.getSprite() > 7) {
					defenderMob.setSprite(4);
					defenderMob.face(defenderMob.getX(), defenderMob.getY() - 1);
				}
			}
			if (attackerMob != null) {
				attackerMob.setOpponent(null);
				attackerMob.setCombatEvent(null);
				attackerMob.setHitsMade(0);
				if (attackerMob.getSprite() > 7) {
					attackerMob.setSprite(4);
					attackerMob.face(attackerMob.getX(), attackerMob.getY() - 1);
				}
			}
		}
		stop();
	}

	private boolean combatCanContinue() {
		boolean removed = attackerMob.isRemoved() || defenderMob.isRemoved();
		boolean nextToVictim = attackerMob.getLocation().equals(defenderMob.getLocation());
		if (defenderMob.isNpc() && attackerMob.isNpc()) {
			return !removed && nextToVictim && running;
		}
		boolean bothLoggedIn = (attackerMob.isPlayer() && ((Player) attackerMob).loggedIn())
			|| (defenderMob.isPlayer() && ((Player) defenderMob).loggedIn());
		boolean respawning = (attackerMob.isNpc() && ((Npc)attackerMob).isRespawning())
			|| (defenderMob.isNpc() && ((Npc)defenderMob).isRespawning());
		return bothLoggedIn && !removed && !respawning && nextToVictim && running;
	}

	public Mob getAttacker() {
		return attackerMob;
	}

	public Mob getVictim() {
		return defenderMob;
	}

}
