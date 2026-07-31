package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.PlayerRangeNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerRangePlayerTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.List;

public class ThrowingEvent extends GameTickEvent {

	private static final int SHURIKEN_THROW_COUNT = 3;
	private boolean deliveredFirstProjectile;
	private Mob target;
	private final List<Mob> shurikenTargetLock = new ArrayList<>();

	public ThrowingEvent(final World world, final Player owner, final long ticksDelay, final Mob victim) {
		super(world, owner, ticksDelay, "Throwing Event", DuplicationStrategy.ONE_PER_MOB);
		this.target = victim;
	}

	public boolean equals(Object o) {
		if (o instanceof ThrowingEvent) {
			ThrowingEvent e = (ThrowingEvent) o;
			return e.belongsTo(getOwner());
		}
		return false;
	}

	public Mob getTarget() {
		return target;
	}

	public void reTarget(final Mob mob) {
		target = mob;
		shurikenTargetLock.clear();
		setDelayTicks(2);
		long currentTick = getPlayerOwner().getWorld().getServer().getCurrentTick();
		if (getPlayerOwner().getAttribute("can_range_again", 0L) > currentTick + 1) {
			getPlayerOwner().setAttribute("can_range_again", currentTick + 1);
		}
	}

	public boolean shouldAutoRetaliateRetarget(final Player player, final Mob attacker) {
		int throwingID = player.getThrowingEquip();
		if (!RangeUtils.SHURIKENS.contains(throwingID)) {
			return target == null || !target.equals(attacker);
		}
		if (isValidPrimaryTarget(player, target)) {
			return false;
		}
		int attackRadius = getAttackRadius(throwingID);
		return findLockedShurikenPrimary(player, attackRadius) == null;
	}

	public void restart() {
		running = true;
	}

	private GroundItem getFloorItem(int id, Player player, Mob floorTarget) {
		return floorTarget.getViewArea().getVisibleGroundItem(id, floorTarget.getLocation(), player);
	}

	private int getAttackRadius(final int throwingEquip) {
		return RangeUtils.getThrowingAttackRadius(throwingEquip);
	}

	@Override
	public void run() {
		final Player player = getPlayerOwner();

		long currentTick = player.getWorld().getServer().getCurrentTick();
		if (player.getAttribute("can_range_again", 0L) > currentTick) return;
		int throwingID = player.getThrowingEquip();
		if (!resolvePrimaryTarget(player, throwingID)) {
			player.resetRange();
			return;
		}

		if (!player.loggedIn() || (player.inCombat() && !(target.isNpc()
				&& player.getOpponent() != null
				&& player.getOpponent().isNpc()
				&& !player.getOpponent().equals(target)))
				|| (target.isPlayer() && !((Player) target).loggedIn())
				|| target.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| !player.checkAttack(target, true)
				|| !player.withinRange(target)) {
			player.resetRange();
			return;
		}

		final int attackRadius = getAttackRadius(throwingID);
		if (!player.withinRange(target, attackRadius)) {
			player.walkToEntity(target.getX(), target.getY());
			if (getOwner().nextStep(getOwner().getX(), getOwner().getY(), target) == null && throwingID != -1) {
				player.message("I can't get close enough");
				player.resetRange();
			}
			return;
		}
		if (!player.withinRange(target, RangeUtils.getApproachRadius(attackRadius)) && !player.finishedPath()) {
			return;
		}



		player.resetPath();
		if (!PathValidation.checkPath(getWorld(), player.getLocation(), target.getLocation())) {
			player.message("I can't get a clear shot from here");
			player.resetRange();
			return;
		}

		// Authentic player always faced NW
		player.face(player.getX() + 1, player.getY() - 1);

		if (target.isPlayer()) {
			Player playerTarget = (Player) target;
			if (!playerTarget.getConfig().WANT_MYWORLD && playerTarget.getPrayers().isPrayerActivated(Prayers.PROTECT_FROM_MISSILES)) {
				player.message("Player has a protection from missiles prayer active!");
				return;
			}
		}

		if (target.isNpc()) {
			if (target.getWorld().getServer().getPluginHandler().handlePlugin(PlayerRangeNpcTrigger.class, getPlayerOwner(), new Object[]{getOwner(), target})) {
				player.resetRange();
				return;
			}
		} else {
			if (target.getWorld().getServer().getPluginHandler().handlePlugin(PlayerRangePlayerTrigger.class, player, new Object[]{getOwner(), target})) {
				player.resetRange();
				return;
			}
		}

		if (throwingID == -1) {
			ActionSender.sendSound(player, "outofammo");
			player.message(ProjectileFailureReason.OUT_OF_AMMO.getText());
			player.resetRange();
			return;
		}

		List<Mob> throwingTargets = selectThrowingTargets(player, throwingID, attackRadius);
		if (throwingTargets.isEmpty()) {
			player.resetRange();
			return;
		}

		int throwsToConsume = RangeUtils.SHURIKENS.contains(throwingID) ? throwingTargets.size() : 1;
		int availableThrows = getAvailableThrowingCount(player, throwingID);
		if (availableThrows < 1) {
			player.resetRange();
			return;
		}
		throwsToConsume = Math.min(throwsToConsume, availableThrows);
		while (throwingTargets.size() > throwsToConsume) {
			throwingTargets.remove(throwingTargets.size() - 1);
		}
		if (!removeThrowingItems(player, throwingID, throwsToConsume)) {
			player.resetRange();
			return;
		}
		if (RangeUtils.SHURIKENS.contains(throwingID)) {
			rememberShurikenTargets(player, throwingTargets, attackRadius);
			primeShurikenAggro(player, throwingTargets);
		}
		/*if (!getPlayerOwner().getLocation().isMembersWild()) {
			getPlayerOwner().message("Members content can only be used in wild levels: "
					+ World.membersWildStart + " - " + World.membersWildMax);
			getPlayerOwner().message("You can not use this type of ranged in wilderness");
			getPlayerOwner().resetRange();
			stop();
			return;
		}*/

		boolean skillCape = SkillCapes.shouldActivate(player, ItemId.RANGED_CAPE);

		int delay = 3;
		if (skillCape) {
			player.playerServerMessage(MessageType.QUEST, "@gre@Your Ranged cape activates, letting you shoot two arrows at once!");
			delay = 1;
		}

		final boolean isShuriken = RangeUtils.SHURIKENS.contains(throwingID);
		for (int i = 0; i < throwingTargets.size(); i++) {
			applyThrowingHit(player, throwingID, throwingTargets.get(i), skillCape, i == 0 || isShuriken, i == 0);
		}
		ActionSender.sendSound(player, "shoot");

		final int adjustedDelay = RangeUtils.getAdjustedRangeDelayTicks(player, delay);
		setDelayTicks(adjustedDelay);

		player.setAttribute("can_range_again", getWorld().getServer().getCurrentTick() + adjustedDelay);
		getOwner().setKillType(KillType.RANGED);
		deliveredFirstProjectile = true;
	}

	private int getAvailableThrowingCount(Player player, int throwingID) {
		if (getWorld().getServer().getConfig().WANT_EQUIPMENT_TAB) {
			int slot = player.getCarriedItems().getEquipment().searchEquipmentForItem(throwingID);
			if (slot < 0)
				return 0;
			Item rangeType = player.getCarriedItems().getEquipment().get(slot);
			if (rangeType == null)
				return 0;
			return rangeType.getAmount();
		}

		int slot = player.getCarriedItems().getInventory().getLastIndexById(throwingID);
		if (slot < 0) {
			return 0;
		}
		Item rangeType = player.getCarriedItems().getInventory().get(slot);
		return rangeType == null ? 0 : rangeType.getAmount();
	}

	private boolean resolvePrimaryTarget(Player player, int throwingID) {
		if (isValidPrimaryTarget(player, target)) {
			return true;
		}

		if (RangeUtils.SHURIKENS.contains(throwingID)) {
			Mob lockedTarget = findLockedShurikenPrimary(player, getAttackRadius(throwingID));
			if (lockedTarget != null) {
				target = lockedTarget;
				return true;
			}
		}

		Mob opponent = player.getOpponent();
		if (isValidPrimaryTarget(player, opponent)) {
			target = opponent;
			return true;
		}

		Npc fallback = findAutoRetaliatePrimaryTarget(player);
		if (fallback == null) {
			return false;
		}
		target = fallback;
		player.setOpponent(fallback);
		player.setCombatTimer();
		return true;
	}

	private boolean isValidPrimaryTarget(Player player, Mob candidate) {
		if (candidate == null
			|| candidate.isRemoved()
			|| candidate.getSkills().getLevel(Skill.HITS.id()) <= 0
			|| candidate.isPlayer() && !((Player) candidate).loggedIn()
			|| candidate.isNpc() && Summoning.isSummon((Npc) candidate)) {
			return false;
		}
		return player.withinRange(candidate) && player.checkAttack(candidate, true);
	}

	private Npc findAutoRetaliatePrimaryTarget(Player player) {
		Npc firstThreat = null;
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (!isValidPrimaryTarget(player, npc) || !isAggroedToPlayer(npc, player)) {
				continue;
			}
			if (npc.getOpponent() == player) {
				return npc;
			}
			if (firstThreat == null) {
				firstThreat = npc;
			}
		}
		return firstThreat;
	}

	private boolean removeThrowingItems(Player player, int throwingID, int amount) {
		if (getWorld().getServer().getConfig().WANT_EQUIPMENT_TAB) {
			int slot = player.getCarriedItems().getEquipment().searchEquipmentForItem(throwingID);
			if (slot < 0)
				return false;
			Item rangeType = player.getCarriedItems().getEquipment().get(slot);
			if (rangeType == null)
				return false;
			return player.getCarriedItems().getEquipment().remove(rangeType, amount) >= 0;
		}

		int slot = player.getCarriedItems().getInventory().getLastIndexById(throwingID);
		if (slot < 0) {
			return false;
		}
		Item rangeType = player.getCarriedItems().getInventory().get(slot);
		if (rangeType == null) {
			return false;
		}
		Item toRemove = new Item(rangeType.getCatalogId(), amount, false, rangeType.getItemId());
		return player.getCarriedItems().remove(toRemove) >= 0;
	}

	private List<Mob> selectThrowingTargets(Player player, int throwingID, int attackRadius) {
		List<Mob> targets = new ArrayList<>();
		if (Summoning.isPlayerAreaEffectSuppressed(player)
			|| !RangeUtils.SHURIKENS.contains(throwingID) || !target.isNpc()) {
			targets.add(target);
			return targets;
		}

		List<Npc> candidates = getValidShurikenTargets(player, attackRadius);
		if (candidates.isEmpty()) {
			targets.add(target);
			return targets;
		}

		int aggroedCount = countAggroedShurikenTargets(candidates, player);
		boolean preferAggroed = aggroedCount >= SHURIKEN_THROW_COUNT;
		for (Npc candidate : candidates) {
			boolean aggroed = isAggroedToPlayer(candidate, player);
			if (candidate.equals(target) && (!preferAggroed || aggroed)) {
				addShurikenTarget(targets, candidate);
				break;
			}
		}

		if (preferAggroed) {
			List<Npc> aggroed = new ArrayList<>();
			for (Npc candidate : candidates) {
				if (isAggroedToPlayer(candidate, player) && !containsTarget(targets, candidate)) {
					aggroed.add(candidate);
				}
			}
			addRandomShurikenTargets(targets, aggroed);
			return targets;
		}

		addLockedShurikenTargets(player, targets, candidates, attackRadius);

		List<Npc> newTargets = new ArrayList<>();
		List<Npc> aggroedFallback = new ArrayList<>();
		for (Npc candidate : candidates) {
			if (containsTarget(targets, candidate)) {
				continue;
			}
			if (isAggroedToPlayer(candidate, player)) {
				aggroedFallback.add(candidate);
			} else {
				newTargets.add(candidate);
			}
		}

		addRandomShurikenTargets(targets, newTargets);
		addRandomShurikenTargets(targets, aggroedFallback);
		return targets;
	}

	private List<Npc> getValidShurikenTargets(Player player, int attackRadius) {
		List<Npc> candidates = new ArrayList<>();
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| Summoning.isSummon(npc)
				|| !npc.getDef().isAttackable()
				|| !player.withinRange(npc, attackRadius)
				|| !PathValidation.checkPath(getWorld(), player.getLocation(), npc.getLocation())
				|| !player.checkAttack(npc, true)) {
				continue;
			}
			candidates.add(npc);
		}
		return candidates;
	}

	private int countAggroedShurikenTargets(List<Npc> candidates, Player player) {
		int count = 0;
		for (Npc candidate : candidates) {
			if (isAggroedToPlayer(candidate, player)) {
				count++;
			}
		}
		return count;
	}

	private void addLockedShurikenTargets(Player player, List<Mob> targets, List<Npc> candidates, int attackRadius) {
		pruneShurikenTargetLock(candidates);
		for (Mob lockedTarget : shurikenTargetLock) {
			if (targets.size() >= SHURIKEN_THROW_COUNT) {
				return;
			}
			if (!lockedTarget.isNpc()) {
				continue;
			}
			Npc lockedNpc = (Npc) lockedTarget;
			if (candidates.contains(lockedNpc)) {
				addShurikenTarget(targets, lockedNpc);
			}
		}
	}

	private void addRandomShurikenTargets(List<Mob> targets, List<Npc> candidates) {
		while (targets.size() < SHURIKEN_THROW_COUNT && !candidates.isEmpty()) {
			addShurikenTarget(targets, candidates.remove(DataConversions.random(0, candidates.size() - 1)));
		}
	}

	private void addShurikenTarget(List<Mob> targets, Mob candidate) {
		if (targets.size() >= SHURIKEN_THROW_COUNT || containsTarget(targets, candidate)) {
			return;
		}
		targets.add(candidate);
	}

	private boolean containsTarget(List<? extends Mob> targets, Mob candidate) {
		for (Mob target : targets) {
			if (target.equals(candidate)) {
				return true;
			}
		}
		return false;
	}

	private Mob findLockedShurikenPrimary(Player player, int attackRadius) {
		List<Npc> candidates = getValidShurikenTargets(player, attackRadius);
		if (countAggroedShurikenTargets(candidates, player) > SHURIKEN_THROW_COUNT) {
			return null;
		}
		pruneShurikenTargetLock(candidates);
		return shurikenTargetLock.isEmpty() ? null : shurikenTargetLock.get(0);
	}

	private void pruneShurikenTargetLock(List<Npc> validTargets) {
		for (int index = shurikenTargetLock.size() - 1; index >= 0; index--) {
			Mob lockedTarget = shurikenTargetLock.get(index);
			if (!lockedTarget.isNpc()
				|| !validTargets.contains((Npc) lockedTarget)) {
				shurikenTargetLock.remove(index);
			}
		}
	}

	private boolean isAggroedToPlayer(Npc npc, Player player) {
		return npc.getOpponent() == player || npc.getPreferredThreatTarget() == player;
	}

	private void rememberShurikenTargets(Player player, List<Mob> throwingTargets, int attackRadius) {
		pruneShurikenTargetLock(getValidShurikenTargets(player, attackRadius));
		for (Mob hitTarget : throwingTargets) {
			if (!hitTarget.isNpc() || containsTarget(shurikenTargetLock, hitTarget)) {
				continue;
			}
			shurikenTargetLock.add(hitTarget);
		}
		while (shurikenTargetLock.size() > SHURIKEN_THROW_COUNT) {
			shurikenTargetLock.remove(shurikenTargetLock.size() - 1);
		}
	}

	private void primeShurikenAggro(Player player, List<Mob> throwingTargets) {
		for (Mob hitTarget : throwingTargets) {
			if (!hitTarget.isNpc()) {
				continue;
			}
			Npc npc = (Npc) hitTarget;
			if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0 || Summoning.isSummon(npc)) {
				continue;
			}
			npc.addRangeDamage(player, 0);
			npc.setLastOpponent(player);
			npc.setCombatTimer();
			if (npc.getPvmMeleeEvent() == null || !npc.getPvmMeleeEvent().isRunning()) {
				npc.startPvmCounterCombat(player);
			}
		}
	}

	private void applyThrowingHit(Player player, int throwingID, Mob hitTarget, boolean skillCape, boolean showProjectile, boolean firstProjectileThisAttack) {
		int damage = RangeUtils.doRangedDamage(player, throwingID, throwingID, hitTarget, skillCape);

		RangeUtils.applyDragonFireBreath(player, hitTarget, deliveredFirstProjectile || !firstProjectileThisAttack);
		if((hitTarget.isPlayer() || getWorld().getServer().getConfig().RANGED_GIVES_XP_HIT) && damage > 0) {
			player.incExp(Skill.RANGED.id(), Formulae.rangedHitExperience(hitTarget, damage), true);
		}

		if (Formulae.loseArrow(damage)) {
			if (!DropTable.handleRingOfAvarice(player, new Item(throwingID, 1))) {
				if (!Summoning.tryLootGoblinCollectStackableItem(player, throwingID, 1)) {
					GroundItem thrownItemOnGround = getFloorItem(throwingID, player, hitTarget);
					if (thrownItemOnGround == null || !thrownItemOnGround.getDef().isStackable()) {
						getWorld().registerItem(new GroundItem(player.getWorld(), throwingID, hitTarget.getX(), hitTarget.getY(), 1, player));
					} else {
						thrownItemOnGround.setAmount(thrownItemOnGround.getAmount() + 1);
					}
				}
			}
		}

		final int projectileType = RangeUtils.SHURIKENS.contains(throwingID)
			? Projectile.SHURIKEN
			: RangeUtils.THROWING_KNIVES.contains(throwingID)
				? Projectile.THROWING_KNIFE
				: RangeUtils.THROWING_DARTS.contains(throwingID)
					? Projectile.THROWING_DART
					: Projectile.RANGED;
		final DuplicationStrategy projectileDuplicationStrategy = RangeUtils.SHURIKENS.contains(throwingID)
			? DuplicationStrategy.ALLOW_MULTIPLE
			: DuplicationStrategy.ONE_PER_MOB;
		getWorld().getServer().getGameEventHandler().add(new ProjectileEvent(getWorld(), player, hitTarget, damage, 2,
			true, throwingID, 0, 0, 0, 0, projectileDuplicationStrategy, projectileType, 0, showProjectile));
	}
}
