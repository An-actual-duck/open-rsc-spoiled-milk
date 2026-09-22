package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.SecondaryEffectPolicy;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.constants.SpellDamages;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.model.entity.EntityType;

/** Full-set policies for the new leather families. No effect is inherited from borrowed art. */
public final class SlayerLeatherEffects {
	private static final String CHARGE = "slayer_leather_charge", STICKY = "slayer_leather_sticky";
	private SlayerLeatherEffects() { }

	public static boolean hasSet(Player player, int coif) {
		if (player == null || !player.getConfig().WANT_MYWORLD
			|| !player.getConfig().WANT_CUSTOM_LEATHER) return false;
		for (int id = coif; id < coif + 5; id++)
			if (!player.getCarriedItems().getEquipment().hasEquipped(id)) return false;
		return true;
	}

	public static boolean ferocious(Player player) {
		return hasSet(player, MyWorldItemId.TERROR_DOG_COIF);
	}

	public static int storageHump(Player player, int healing) {
		return hasSet(player, MyWorldItemId.UGTHANKI_COIF)
			? foodHealing(player, healing) : healing;
	}

	public static int foodHealing(Player player, int healing) {
		double nature = player.getCarriedItems().getEquipment().getNatureFoodHealingBonus();
		if (hasSet(player, MyWorldItemId.UGTHANKI_COIF))
			return (int)Math.round(Math.max(0, healing) * (1.20D + nature));
		return nature > 0 ? (int)Math.ceil(healing * (1.0D + nature)) : healing;
	}

	public static int carapaceCleanse(Player player) {
		if (!player.getConfig().WANT_MYWORLD || !player.getConfig().WANT_CUSTOM_LEATHER) return 0;
		if (player.getCarriedItems().getEquipment().hasFullMagicSpiderCarapaceSet()) return 5;
		if (player.getCarriedItems().getEquipment().hasFullSpiderCarapaceSet()) return 3;
		return player.getCarriedItems().getEquipment().hasFullScorpionCarapaceSet() ? 2 : 0;
	}

	private static long tick(Mob mob) { return mob.getWorld().getServer().getCurrentTick(); }
	private static final class Sticky {
		final CombatParticipantSnapshot lifetime;
		long blockedUntil = Long.MIN_VALUE;
		Sticky(Mob mob) { lifetime = CombatParticipantSnapshot.capture(mob); }
	}
	/** Called only at an otherwise-ready attack: delay that attempt one tick, never movement. */
	public static boolean delayAttack(Mob mob) {
		Sticky sticky = mob.getAttribute(STICKY, null);
		if (sticky == null || !sticky.lifetime.matches(mob)) return false;
		if (sticky.blockedUntil == Long.MIN_VALUE) sticky.blockedUntil = tick(mob) + 1;
		if (tick(mob) < sticky.blockedUntil) return true;
		mob.removeAttribute(STICKY);
		return false;
	}
	private static void stickyHit(Player wearer, Mob attacker) {
		if (!hasSet(wearer, MyWorldItemId.GIANT_FROG_COIF) || attacker == null || attacker == wearer
			|| attacker.getLevel(Skill.HITS.id()) <= 0 || attacker.isRemoved()) return;
		Sticky existing = attacker.getAttribute(STICKY, null);
		if (existing != null && existing.lifetime.matches(attacker)) return;
		if (wearer.getWorld().getServer().getCombatRandom().nextInt(100) < 10)
			attacker.setAttribute(STICKY, new Sticky(attacker));
	}
	private static final class Charge {
		final CombatParticipantSnapshot lifetime;
		int power;
		Charge(Player player) { lifetime = CombatParticipantSnapshot.capture(player); }
	}
	public static void equipmentChanged(Player player) {
		if (!hasSet(player, MyWorldItemId.DARK_BEAST_COIF)) player.removeAttribute(CHARGE);
	}
	public static int charge(Player player) {
		equipmentChanged(player);
		Charge charge = player.getAttribute(CHARGE, null);
		return charge != null && charge.lifetime.matches(player) ? charge.power : 0;
	}
	private static void chargedHit(Player player) {
		if (!hasSet(player, MyWorldItemId.DARK_BEAST_COIF)) return;
		Charge charge = player.getAttribute(CHARGE, null);
		if (charge == null || !charge.lifetime.matches(player)) {
			charge = new Charge(player); player.setAttribute(CHARGE, charge);
		}
		charge.power += 1 + player.getWorld().getServer().getCombatRandom().nextInt(3);
		if (charge.power < 30) return;
		charge.power = 0;
		player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.THUNDER_SPLASH));
		if (Summoning.isPlayerAreaEffectSuppressed(player)) return;
		for (Npc npc : player.getViewArea().getNpcsInView()) discharge(player, npc);
		if (player.getConfig().WANT_PVP)
			for (Player target : player.getViewArea().getPlayersInView()) discharge(player, target);
	}
	private static void discharge(Player player, Mob target) {
		if (target == player || !player.withinRange(target, 2) || !SlayerRewardCombat.legalSecondary(player, target)) return;
		double power = player.getWorld().getServer().getConstants().getSpellDamages()
			.getSpellDamage(Spells.THUNDER_SPLASH, target.isNpc() ? EntityType.NPC : EntityType.PLAYER,
				SpellDamages.MagicType.MODERNMAGIC);
		int damage = CombatFormula.calculateSecondaryMagicDamage(player, target, power, .60D);
		if (target instanceof Player) {
			Player victim = (Player)target;
			damage = victim.applyPotionMagicDamageReduction(victim.applyRobeDamageMitigation(damage, NpcMagicElement.THUNDER));
		}
		damage = DarkBeastCombat.mitigate(target, damage);
		target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.THUNDER_SPLASH));
		DamageResult result = target.getWorld().getServer().getResolvedDamageTransaction().apply(
			DamageRequest.resolvedLegacy(player, target, DamageRequest.SourceCategory.OWNED_EFFECT,
				SecondaryEffectPolicy.ELECTRICALLY_CHARGED.getStableKey(), damage).style(CombatStyle.MAGIC)
				.hitSplatType(HitSplat.TYPE_ARMOR_PROC).build());
		if (target instanceof Npc) {
			Npc npc = (Npc)target; npc.addMageDamage(player, result.getActualDamage());
			if (!result.isTargetTerminal() && !npc.isChasing() && !npc.inCombat()) npc.setChasing(player);
		} else ActionSender.sendStat((Player)target, Skill.HITS.id());
		if (result.isTargetTerminal()) target.killedBy(player);
	}

	/** Apply once in the elemental mitigation path, after other existing resistance. */
	public static int coldBlooded(Player player, int damage, NpcMagicElement element) {
		if (damage <= 0 || (element != NpcMagicElement.FIRE && element != NpcMagicElement.ICE)
			|| !hasSet(player, MyWorldItemId.NAGA_COIF)) return damage;
		return Math.max(1, (int)Math.ceil(damage * .80D));
	}

	/** Once per settled HP loss, including owned periodic, secondary and summon damage. */
	public static void afterDamage(DamageResult result) {
		if (result.getActualDamage() <= 0) return;
		Mob source = result.getRequest().getSource(), target = result.getRequest().getTarget();
		// Retaliation only on live wearers hit directly; owned procs/DOT cannot loop.
		if (target instanceof Player && source != null && source != target && !result.isTargetTerminal()
			&& result.getRequest().getSourceCategory() == DamageRequest.SourceCategory.ACTOR) {
			Player wearer = (Player)target;
			if (wearer.loggedIn() && !wearer.isRemoved() && !wearer.killed) {
				stickyHit(wearer, source);
				chargedHit(wearer);
			}
		}
		Player owner = source instanceof Player ? (Player)source
			: source instanceof Npc ? Summoning.getSummonOwner((Npc)source) : null;
		if (owner == null || owner == target || !owner.loggedIn() || owner.isRemoved()
			|| owner.killed || owner.getLevel(Skill.HITS.id()) <= 0
			|| !hasSet(owner, MyWorldItemId.BLOODVELD_COIF)) return;
		int hp = owner.getLevel(Skill.HITS.id());
		if (hp >= owner.getHealingMaximumHits()) return;
		owner.getSkills().setLevel(Skill.HITS.id(), hp + 1);
		owner.getUpdateFlags().addHitSplat(new HitSplat(owner, HitSplat.TYPE_HEAL, 1));
		ActionSender.sendStat(owner, Skill.HITS.id());
		if (owner.getParty() != null) owner.getParty().sendParty();
	}
}
