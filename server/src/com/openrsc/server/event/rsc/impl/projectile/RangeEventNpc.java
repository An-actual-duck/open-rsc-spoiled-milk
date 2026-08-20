package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.CombatEngagementTerminalReason;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.Formulae;

public class RangeEventNpc extends GameTickEvent {

    private final Mob victim;

    public RangeEventNpc(World world, Npc owner, Mob victim) {
        super(world, owner, 1, "Range Event NPC", DuplicationStrategy.ALLOW_MULTIPLE);
        this.victim = victim;
    }

    public Mob getTarget() {
        return victim;
    }

    public boolean equals(Object o) {
        if (o instanceof RangeEventNpc) {
            RangeEventNpc e = (RangeEventNpc) o;
            return e.belongsTo(getOwner());
        }
        return false;
    }

    public void run() {
        final Mob owner = getOwner();
        if (!owner.isCurrentRangeEventNpc(this)) {
            stop();
            return;
        }
        if ((victim.isPlayer() && !((Player) victim).loggedIn())
                || victim.getSkills().getLevel(Skill.HITS.id()) <= 0
                || !owner.withinRange(victim)) {
            terminate(CombatEngagementTerminalReason.EVENT_ENDED);
            return;
        }
        if (owner.inCombat()) {
            terminate(CombatEngagementTerminalReason.EVENT_ENDED);
            return;
        }
        if (!victim.getLocation().inBounds(((Npc) owner).getLoc().minX - 9, ((Npc) owner).getLoc().minY - 9,
                ((Npc) owner).getLoc().maxX + 9, ((Npc) owner).getLoc().maxY + 9) && owner.isNpc()) {
            terminate(CombatEngagementTerminalReason.LEASH);
            return;
        }
        if (owner.getLocation().inWilderness() && victim.getLocation().inWilderness() && isUnreachable(victim)) {
            owner.walkToEntity(victim.getX(), victim.getY());
            if (owner.nextStep(owner.getX(), owner.getY(), victim) == null) {
                Player playerTarget = (Player) victim;
                playerTarget.message("You got away");
                terminate(CombatEngagementTerminalReason.LEASH);
            }
        } else if (!owner.getLocation().inWilderness() && !victim.getLocation().inWilderness() && isUnreachable(victim)) {
            owner.walkToEntity(victim.getX(), victim.getY());
            if (owner.nextStep(owner.getX(), owner.getY(), victim) == null) {
                Player playerTarget = (Player) victim;
                playerTarget.message("You got away");
                terminate(CombatEngagementTerminalReason.LEASH);
            }
        } else if (!owner.getLocation().inWilderness() && !victim.getLocation().inWilderness() && isUnreachable(victim)) {
            Player playerTarget = (Player) victim;
            playerTarget.message("You got away");
            terminate(CombatEngagementTerminalReason.LEASH);
        } else {
            owner.resetPath();
			if (!PathValidation.checkEnemyCombatProjectilePath(
				getWorld(),
				owner.getWorldLocation(),
				victim.getWorldLocation())) {
                terminate(CombatEngagementTerminalReason.EVENT_ENDED);
                return;
            }
            owner.face(victim);
            setDelayTicks(RangeUtils.getAdjustedRangeDelayTicks(owner, 3));


                if (victim.isPlayer()) {
                    Player playerTarget = (Player) victim;
                    if (!playerTarget.getConfig().WANT_MYWORLD && playerTarget.getPrayers().isPrayerActivated(Prayers.PROTECT_FROM_MISSILES)) {
                        playerTarget.message(owner + " is trying to shoot you!");
                        terminate(CombatEngagementTerminalReason.EVENT_ENDED);
                        return;
                    }
                }
                int damage = RangeUtils.doRangedDamage(getPlayerOwner(), ItemId.LONGBOW.id(), ItemId.BRONZE_ARROWS.id(), victim, false);

                final ProjectileResourceLedger resourceLedger =
                    ProjectileResourceLedger.trackedLaunch(
                        ProjectileLaunchSpecification.Producer.LEGACY_NPC_RANGED);
                ProjectileResourceLedger.RecoveryDestination recovery =
                    ProjectileResourceLedger.RecoveryDestination.NOT_RECOVERED;
                int recoveredAmount = 0;
                if (Formulae.loseArrow(damage)) {
                    GroundItem arrows = getArrows(getPlayerOwner());
                    if (arrows == null) {
                        for (Player p : getWorld().getPlayers()) {
                            getWorld().registerItem(new GroundItem(
                                    p.getWorld(),
                                    ItemId.BRONZE_ARROWS.id(),
                                    victim.getX(),
                                    victim.getY(),
                                    1,
                                    p
                            ));
                            recoveredAmount++;
                        }
                        if (recoveredAmount > 0) {
                            recovery = ProjectileResourceLedger
                                .RecoveryDestination.LEGACY_GROUND_PER_PLAYER;
                        }
                    } else {
                        arrows.setAmount(arrows.getAmount() + 1);
                        recoveredAmount = 1;
                        recovery = ProjectileResourceLedger.RecoveryDestination
                            .GROUND_EXISTING_STACK;
                    }
                }
                resourceLedger.recordRecovery(ItemId.BRONZE_ARROWS.id(),
                    recoveredAmount, recovery, victim.getWorldLocation());
                resourceLedger.seal();
                if (victim.isPlayer() && owner.isNpc()) {
                    ((Player) victim).message(owner + " is shooting at you!");
                }
                getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(
                        getWorld(), owner, victim,
                        ProjectileLaunchSpecification.builder(
                                ProjectileLaunchSpecification.Producer.LEGACY_NPC_RANGED,
                                damage, 2)
                                .build(), resourceLedger));
                owner.setKillType(KillType.RANGED);
            }
    }

    public void terminate(final CombatEngagementTerminalReason reason) {
        final Mob owner = getOwner();
        if (owner != null) {
            owner.clearRangeEventNpcIfCurrent(this, reason);
        }
        stop();
    }

    private GroundItem getArrows(Player player) {
        return victim.getViewArea().getVisibleGroundItem(ItemId.BRONZE_ARROWS.id(), victim.getLocation(), player);
    }

    private boolean isUnreachable(Mob mob) {
        int radius = 5;
        return !getOwner().withinRange(mob, radius);
    }
}
