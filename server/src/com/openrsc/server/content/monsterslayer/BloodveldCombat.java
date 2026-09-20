package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ActionSender;

/** Durable melee predator; its tongue repositions, never deals ranged damage. */
public final class BloodveldCombat {
	public static final int NPC_ID = 868, RANGE = 5;
	private static final String NEXT = "bloodveld_next_attack_tick";
	private BloodveldCombat() { }
	public static boolean isBloodveld(Mob mob) {
		return mob instanceof Npc && mob.getConfig().WANT_MYWORLD && mob.getID() == NPC_ID;
	}
	public static boolean attackReady(Mob mob) {
		return mob.getWorld().getServer().getCurrentTick() >= mob.<Long>getAttribute(NEXT, -1L);
	}
	public static void recordAttack(Mob mob, int ticks) {
		mob.setAttribute(NEXT, mob.getWorld().getServer().getCurrentTick() + ticks);
	}
	public static void lifesteal(Mob source, int damage) {
		if (!isBloodveld(source) || source.isRemoved() || source.getLevel(Skill.HITS.id()) <= 0 || damage <= 0) return;
		int hp = source.getLevel(Skill.HITS.id());
		int heal = Math.min(damage / 2, source.getSkills().getMaxStat(Skill.HITS.id()) - hp);
		if (heal <= 0) return;
		source.getSkills().setLevel(Skill.HITS.id(), hp + heal);
		source.getUpdateFlags().addHitSplat(new HitSplat(source, HitSplat.TYPE_HEAL, heal));
	}
	private static boolean valid(Npc npc, Player player) {
		return !npc.isRemoved() && !npc.isRespawning() && !player.isRemoved() && player.loggedIn()
			&& !player.killed && npc.getLevel(Skill.HITS.id()) > 0 && player.getLevel(Skill.HITS.id()) > 0
			&& npc.sharesSpatialDomain(player) && npc.withinRange(player, RANGE);
	}
	/** Trace every movement edge, not merely projectile sight: fences and corners block pulls. */
	public static WorldLocation pullDestination(Npc npc, Player player) {
		if (!valid(npc, player) || npc.withinRange(player, 1)) return null;
		if (!PathValidation.checkEnemyCombatProjectilePath(npc.getWorld(), npc.getWorldLocation(), player.getWorldLocation())) return null;
		WorldLocation current = player.getWorldLocation();
		Point point = player.getLocation();
		try {
			while (Math.max(Math.abs(point.getX()-npc.getX()), Math.abs(point.getY()-npc.getY())) > 1) {
				Point next = Point.location(point.getX() + Integer.signum(npc.getX() - point.getX()),
					point.getY() + Integer.signum(npc.getY() - point.getY()));
				WorldLocation location = npc.getWorld().getRegionManager().fromRuntimeCompatibilityPoint(next, current, false);
				if (!PathValidation.checkAdjacent(player, point, next)) return null;
				current = location;
				point = next;
			}
			if (!PathValidation.checkAdjacent(player, point, npc.getLocation())) return null;
			return current;
		} catch (IllegalArgumentException | IllegalStateException invalid) { return null; }
	}
	/** True means hold position (including cooldown); false permits normal approach. */
	public static boolean tryPull(Npc npc, Mob target) {
		if (!isBloodveld(npc) || !(target instanceof Player)) return false;
		Player player = (Player) target;
		if (pullDestination(npc, player) == null) return false;
		npc.resetPath(); npc.face(player);
		if (!attackReady(npc)) return true;
		recordAttack(npc, 3);
		npc.setCombatTimer();
		if (npc.consumeOgreStaggerDebuff() || npc.consumeStartleDebuff()) return true;
		// Existing blank projectile packet triggers the client attack pose, without a missile.
		player.getUpdateFlags().setProjectile(new Projectile(npc, player, Projectile.BLANK));
		CombatParticipantSnapshot source = CombatParticipantSnapshot.capture(npc);
		CombatParticipantSnapshot victim = CombatParticipantSnapshot.capture(player);
		WorldLocation launch = player.getWorldLocation();
		npc.getWorld().getServer().getGameEventHandler().add(new GameTickEvent(npc.getWorld(), npc, 1,
			"Bloodveld tongue pull", DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override public void run() {
				stop();
				if (!source.matches(npc) || !victim.matches(player) || !launch.equals(player.getWorldLocation())) return;
				WorldLocation destination = pullDestination(npc, player);
				if (destination == null) return;
				player.resetPath();
				if (player.getConfig().WANT_LAYERED_PLAYER_LOCATION_AUTHORITY) player.setLayeredLocation(destination, true);
				else player.setLocation(player.getWorld().getRegionManager().toRuntimeCompatibilityPoint(destination), true);
				// Clear local-client waypoint interpolation, as an ordinary teleport does,
				// but retain the encounter instead of terminating combat ownership.
				ActionSender.sendWorldInfo(player);
				player.message("The Bloodveld's tongue pulls you into reach!");
			}
		});
		return true;
	}
}
