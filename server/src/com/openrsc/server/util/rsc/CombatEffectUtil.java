package com.openrsc.server.util.rsc;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.combat.CombatEligibility;
import com.openrsc.server.model.combat.CombatEligibilityPhase;
import com.openrsc.server.model.combat.CombatEligibilityRequest;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.PlayerOwnedNpcRadiusSelection;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CombatEffectUtil {
	public static final int HELLS_INFERNO_MAX_HIT = 18;
	public static final int HELLS_INFERNO_SPLASH_RADIUS = 2;

	private CombatEffectUtil() {
	}

	public static int remapLegacyPlayerMeleeStat(Mob mob, int skillId) {
		if (mob != null && mob.isPlayer() && isLegacyMeleeStat(skillId)) {
			return Skill.MELEE.id();
		}
		return skillId;
	}

	public static int[] remapLegacyPlayerMeleeStats(Mob mob, int... skillIds) {
		return Arrays.stream(skillIds)
			.map(skillId -> remapLegacyPlayerMeleeStat(mob, skillId))
			.distinct()
			.toArray();
	}

	public static void sendInfernalProcDebug(Player player, String source, Mob target, int damage, int pieces,
											 int maxHit, double roll, double chance, boolean procced,
											 int procDamageRoll, int procDamageDealt) {
	}

	public static int hellsInfernoSplashDamage(final int primaryDamageDealt) {
		return primaryDamageDealt <= 0 ? 0 : (primaryDamageDealt / 2) + (primaryDamageDealt % 2);
	}

	public static List<Npc> findPlayerOwnedNpcSplashTargets(final Player player,
			final Npc primaryTarget, final int radius) {
		if (player == null || primaryTarget == null || radius < 0
				|| Summoning.isPlayerAreaEffectSuppressed(player)) {
			return Collections.emptyList();
		}
		return PlayerOwnedNpcRadiusSelection
			.aroundPrimary(player, primaryTarget, radius)
			.snapshotViewOrder();
	}

	public static List<Player> findPlayerOwnedPvpSplashTargets(final Player player,
			final Player primaryTarget, final int radius) {
		if (player == null || primaryTarget == null || radius < 0
			|| player.getDuel().isDuelActive()
			|| Summoning.isPlayerAreaEffectSuppressed(player)) {
			return Collections.emptyList();
		}
        final com.openrsc.server.model.Point center = playerAreaCenter(player,primaryTarget);
        if (center == null) return Collections.emptyList();
		final List<Player> targets = new ArrayList<>();
		for (Player candidate : player.getViewArea().getPlayersInView()) {
			if (!isPlayerOwnedPvpSplashTarget(player,primaryTarget,candidate)
				|| !candidate.withinRange(center, radius)) {
				continue;
			}
			targets.add(candidate);
		}
		return targets;
	}

	/** Friendship is checked by persistent clan identity, including reloaded clan objects. */
	public static boolean arePvpAllies(Player source,Player target) {
		if(source==null||target==null)return false;
		if(source.getParty()!=null&&source.getParty()==target.getParty())return true;
		return source.getClan()!=null && target.getClan()!=null
			&&(source.getClan()==target.getClan()
				|| source.getClan().getClanID()>0
					&& source.getClan().getClanID()==target.getClan().getClanID());
	}

	/** NPC-primary attacks never admit player collateral; every recipient must remain legal. */
	public static boolean isPlayerOwnedPvpSplashTarget(Player source,Mob primary,Mob candidate) {
        final com.openrsc.server.model.world.coordinate.WorldLocation origin = playerAreaLocation(source,primary);
		if(source==null || origin==null || !(primary instanceof Player) || !(candidate instanceof Player)
			|| candidate==source || candidate==primary || !source.getConfig().WANT_PVP
			|| source.getDuel().isDuelActive() || ((Player)candidate).getDuel().isDuelActive()
            || !source.getWorldLocation().getWorldSpace().equals(origin.getWorldSpace())
            || source.getWorldLocation().getCoordinate().getLevel()!=origin.getCoordinate().getLevel()
            || !primary.getWorld().getRegionManager().toRuntimeCompatibilityPoint(origin).inWilderness()
			|| !source.getLocation().inWilderness() || !candidate.getLocation().inWilderness()
			|| arePvpAllies(source,(Player)candidate)
			|| Summoning.isPlayerAreaEffectSuppressed(source))return false;
		return CombatEligibility.evaluate(CombatEligibilityRequest.builder(source,candidate,
			CombatEligibilityPhase.COMPATIBILITY,CombatStyle.MAGIC)
			.currentParticipants(true).registration(true).sameSpatialDomain(true)
			.summonRules(true).playerAttackRules(true).build()).isAllowed();
	}

    /** A same-tick killing hit keeps its impact center after the victim is moved to respawn. */
    public static com.openrsc.server.model.world.coordinate.WorldLocation playerAreaLocation(Player source,Mob primary) {
        if (source==null || primary==null) return null;
        if (!(primary instanceof Player) || !primary.killed) return primary.getWorldLocation();
        com.openrsc.server.model.entity.death.DeathContext death=primary.getDeathLifecycleSnapshot().getContext();
        if (death==null || death.getStartTick()!=source.getWorld().getServer().getCurrentTick()) return null;
        Mob killer=death.getKiller();
        if (killer!=source && (!(killer instanceof Npc) || Summoning.getSummonOwner((Npc)killer)!=source)) return null;
        return death.getLocation();
    }

    public static com.openrsc.server.model.Point playerAreaCenter(Player source,Mob primary) {
        com.openrsc.server.model.world.coordinate.WorldLocation location=playerAreaLocation(source,primary);
        return location==null ? null : primary.getWorld().getRegionManager().toRuntimeCompatibilityPoint(location);
    }

	private static boolean isLegacyMeleeStat(int skillId) {
		return skillId == Skill.ATTACK.id()
			|| skillId == Skill.DEFENSE.id()
			|| skillId == Skill.STRENGTH.id();
	}
}
