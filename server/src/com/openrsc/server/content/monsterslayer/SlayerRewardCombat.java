package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;

/** Opt-in primary-hit hooks. Secondary damage never calls these hooks recursively. */
public final class SlayerRewardCombat {
	private static final String WHIP = "slayer_whip_delay", BOW = "slayer_bow_fraction";
	private SlayerRewardCombat() { }
	public static int mainhand(Mob mob) {
		if (!(mob instanceof Player) || !mob.getConfig().WANT_MYWORLD) return -1;
		Player player = (Player)mob;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			Item item = player.getCarriedItems().getEquipment().get(4);
			return item == null ? -1 : item.getCatalogId();
		}
		for (Item item : player.getCarriedItems().getInventory().getItems())
			if (item.isWielded() && item.getDef(player.getWorld()).getWieldPosition() == 4) return item.getCatalogId();
		return -1;
	}
	public static boolean terrorDagger(Mob mob) {
		int id = mainhand(mob);
		return id == MyWorldItemId.DAGGER_OF_TERROR || id == MyWorldItemId.POISONED_DAGGER_OF_TERROR;
	}
	private static long tick(Mob mob) { return mob.getWorld().getServer().getCurrentTick(); }
	private static final class WhipDelay {
		final CombatParticipantSnapshot lifetime;
		final long until, immuneUntil;
		WhipDelay(Mob target) { lifetime = CombatParticipantSnapshot.capture(target); until = tick(target) + 1; immuneUntil = until + 3; }
	}
	private static WhipDelay whipState(Mob mob) {
		if (mob == null || !mob.getConfig().WANT_MYWORLD) return null;
		WhipDelay delay = mob.getAttribute(WHIP, null);
		return delay != null && delay.lifetime.matches(mob) ? delay : null;
	}
	public static boolean whipBlocked(Mob mob) {
		WhipDelay delay = whipState(mob);
		return delay != null && tick(mob) < delay.until;
	}
	public static void whipHit(Mob source, Mob target, int actualDamage) {
		if (actualDamage <= 0 || mainhand(source) != MyWorldItemId.ABYSSAL_WHIP
			|| !legalSecondary((Player)source, target)) return;
		WhipDelay old = whipState(target);
		if (old != null && tick(target) < old.immuneUntil) return;
		if (source.getWorld().getServer().getCombatRandom().nextInt(100) < 10) target.setAttribute(WHIP, new WhipDelay(target));
	}
	private static final class BowFraction {
		final CombatParticipantSnapshot lifetime;
		int fifths;
		BowFraction(Player player) { lifetime = CombatParticipantSnapshot.capture(player); }
	}
	/** Launch-owned bow effect; tiny remainders persist across swaps, never across a combat lifetime. */
	public static void leachingBowHit(Player source, int actualDamage) {
		if (source == null || !source.getConfig().WANT_MYWORLD || actualDamage <= 0
			|| !source.loggedIn() || source.killed || source.getLevel(Skill.HITS.id()) <= 0) return;
		BowFraction fraction = source.getAttribute(BOW, null);
		if (fraction == null || !fraction.lifetime.matches(source)) {
			fraction = new BowFraction(source); source.setAttribute(BOW, fraction);
		}
		int hp = source.getLevel(Skill.HITS.id()), max = source.getSkills().getMaxStat(Skill.HITS.id());
		if (hp >= max) { fraction.fifths = 0; return; }
		long total = (long)actualDamage + fraction.fifths;
		int heal = (int)Math.min(max - hp, total / 5);
		fraction.fifths = hp + heal >= max ? 0 : (int)(total % 5);
		if (heal <= 0) return;
		source.getSkills().setLevel(Skill.HITS.id(), hp + heal);
		source.getUpdateFlags().addHitSplat(new HitSplat(source, HitSplat.TYPE_HEAL, heal));
		ActionSender.sendStat(source, Skill.HITS.id());
		if (source.getParty() != null) source.getParty().sendParty();
	}
	/** Returns true when the caller must settle the attacker's death. */
	public static boolean pendantHit(Player wearer, Mob attacker, int actualDamage) {
		if (actualDamage <= 0 || wearer == null || !wearer.getConfig().WANT_MYWORLD
			|| !wearer.getCarriedItems().getEquipment().hasEquipped(MyWorldItemId.SULLEN_PENDANT)
			|| !legalSecondary(wearer, attacker)) return false;
		if (wearer.getWorld().getServer().getCombatRandom().nextInt(100) >= 10) return false;
		wearer.message("Your Sullen Pendant cries out in pain!");
		if (SlayerRewardBosses.isBoss(attacker)) { wearer.message("Is uneffected"); return false; }
		int percent = 1 + wearer.getWorld().getServer().getCombatRandom().nextInt(attacker.isPlayer() ? 5 : 10);
		int damage = Math.max(1, (int)((long)attacker.getSkills().getMaxStat(Skill.HITS.id()) * percent / 100));
		// Defense bypass is not immunity/mitigation bypass.
		damage = DarkBeastCombat.mitigate(attacker, damage);
		DamageResult result = attacker.getWorld().getServer().getResolvedDamageTransaction().apply(
			DamageRequest.resolvedLegacy(wearer, attacker, DamageRequest.SourceCategory.OWNED_EFFECT,
				SecondaryEffectPolicy.SULLEN_PENDANT.getStableKey(), damage)
				.style(CombatStyle.MAGIC).hitSplatType(HitSplat.TYPE_ARMOR_PROC).build());
		if (attacker instanceof Npc) ((Npc)attacker).addCombatDamage(wearer, result.getActualDamage());
		else if (attacker instanceof Player) ActionSender.sendStat((Player)attacker, Skill.HITS.id());
		return result.isTargetTerminal();
	}
	/** Passive/in-flight effects ignore action locks but still require live, legal participants. */
	public static boolean legalSecondary(Player source, Mob target) {
		if (source == null || target == null) return false;
		if (target instanceof Player) {
			Player victim = (Player)target;
			if (!source.getConfig().WANT_PVP || !source.getLocation().inWilderness() || !victim.getLocation().inWilderness()
				|| source.getClan() != null && source.getClan() == victim.getClan()) return false;
		}
		return CombatEligibility.evaluate(CombatEligibilityRequest.builder(source, target,
			CombatEligibilityPhase.COMPATIBILITY, CombatStyle.MAGIC)
			.currentParticipants(true).registration(true).sameSpatialDomain(true).summonRules(true)
			.playerAttackRules(true).build()).isAllowed();
	}
	public static int thunderTier(Player source, Spells spell) {
		if (mainhand(source) != MyWorldItemId.THUNDER_SPIRE_STAFF) return 0;
		return spell == Spells.THUNDER_BALL ? 1 : spell == Spells.THUNDER_SPLASH ? 2 : spell == Spells.THUNDER_STRIKE ? 3 : 0;
	}
	public static double thunderCap(int tier, double ordinaryCap) { return tier == 3 ? 1.0D : ordinaryCap; }
	public static int thunderSecondaryPower(int tier, double spellPower) {
		if (tier < 1 || tier > 3) return 0;
		return Math.max(1, (int)Math.ceil(spellPower * new double[]{0, .15, .25, .40}[tier]));
	}
	public static boolean thunderTarget(Player source, Mob primary, Mob candidate, int radius) {
		return primary != null && candidate != null && candidate != primary && radius >= 1 && radius <= 3
			&& primary.sharesSpatialDomain(candidate)
			&& primary.getLocation().withinRange(candidate.getLocation(), radius) && legalSecondary(source, candidate);
	}
	/** Runs only after an admitted primary impact; radius is centered at the impact, not the caster. */
	public static void thunderSplash(Player caster, Mob primary, int tier, double spellPower) {
		if (tier < 1 || tier > 3 || !caster.getConfig().WANT_MYWORLD || Summoning.isPlayerAreaEffectSuppressed(caster)) return;
		for (Npc npc : caster.getViewArea().getNpcsInView())
			if (thunderTarget(caster, primary, npc, tier)) thunderHit(caster, npc, tier, spellPower);
		if (caster.getConfig().WANT_PVP) for (Player player : caster.getViewArea().getPlayersInView())
			if (thunderTarget(caster, primary, player, tier)) thunderHit(caster, player, tier, spellPower);
	}
	private static void thunderHit(Player caster, Mob target, int tier, double spellPower) {
		int damage = CombatFormula.calculateSecondaryMagicDamage(caster, target, thunderSecondaryPower(tier, spellPower));
		damage = DarkBeastCombat.mitigate(target, damage);
		DamageResult result = target.getWorld().getServer().getResolvedDamageTransaction().apply(
			DamageRequest.resolvedLegacy(caster, target, DamageRequest.SourceCategory.OWNED_EFFECT,
				SecondaryEffectPolicy.THUNDER_SPIRE_SPLASH.getStableKey(), damage)
				.style(CombatStyle.MAGIC).hitSplatType(HitSplat.TYPE_STANDARD).build());
		if (target instanceof Npc) {
			Npc npc = (Npc)target; npc.addMageDamage(caster, result.getActualDamage());
			if (!npc.isChasing() && !npc.inCombat()
				&& npc.getCombatState() != com.openrsc.server.model.states.CombatState.RUNNING) npc.setChasing(caster);
		} else ActionSender.sendStat((Player)target, Skill.HITS.id());
		if (result.isTargetTerminal()) target.killedBy(caster);
	}
}
