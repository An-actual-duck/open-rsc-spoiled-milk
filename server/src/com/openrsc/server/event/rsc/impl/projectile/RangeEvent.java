package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.CombatEngagementTerminalReason;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.handler.PluginHandler;
import com.openrsc.server.plugins.triggers.PlayerRangeNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerRangePlayerTrigger;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

public class RangeEvent extends GameTickEvent {
	private final Player player;
	private final ServerConfiguration config;
	private final PluginHandler pluginHandler;
	private boolean deliveredFirstProjectile;
	private Mob target;

	public RangeEvent(final World world, final Player owner, final long tickDelay, final Mob target) {
		super(world, owner, tickDelay, "Range Event", DuplicationStrategy.ONE_PER_MOB);
		this.player = owner;
		this.target = target;
		this.config = world.getServer().getConfig();
		this.pluginHandler = world.getServer().getPluginHandler();
	}

	public boolean equals(Object o) {
		if (o instanceof RangeEvent) {
			RangeEvent e = (RangeEvent) o;
			return e.belongsTo(getOwner());
		}
		return false;
	}

	public Mob getTarget() {
		return target;
	}

	public void reTarget(final Mob mob) {
		target = mob;
		player.setRangeEvent(this);
		setDelayTicks(2);
		long currentTick = player.getWorld().getServer().getCurrentTick();
		if (player.getAttribute("can_range_again", 0L) > currentTick + 1) {
			player.setAttribute("can_range_again", currentTick + 1);
		}
	}

	public void restart() {
		running = true;
	}

	public void run() {
		if (!running) return;
		if (!player.isCurrentRangeEvent(this)) {
			stop();
			return;
		}

		long currentTick = player.getWorld().getServer().getCurrentTick();
		if (player.getAttribute("can_range_again", 0L) > currentTick) return;
		if (!com.openrsc.server.content.Slow.ready(player, com.openrsc.server.content.Slow.Cadence.RANGED)) return;

		final int weaponId = player.getRangeEquip();

		if (weaponId == -1 ||
			(player.inCombat() && !(target.isNpc()
				&& player.getOpponent() != null
				&& player.getOpponent().isNpc()
				&& !player.getOpponent().equals(target))) ||
			!player.loggedIn() ||
			target.getSkills().getLevel(Skill.HITS.id()) <= 0 ||
			(target.isPlayer() && (!((Player) target).loggedIn() || !player.checkAttack(target, true))) ||
			!player.withinRange(target)) {
			player.terminateRangeEvent(
				this, CombatEngagementTerminalReason.EVENT_ENDED);
			return;
		}

		final int radius = RangeUtils.getBowAttackRadius(weaponId);
		if (com.openrsc.server.content.monsterslayer.SlayerCombatEffects.attacksBlocked(player)) return;
		final int approachRadius = RangeUtils.getApproachRadius(radius);

		if (!player.withinRange(target, radius)) {
			if (!RangeUtils.queueRangedApproach(player, target)) {
				reset(ProjectileFailureReason.CANT_GET_CLOSE_ENOUGH);
				return;
			}
			return;
		}
		if (!player.withinRange(target, approachRadius) && !player.finishedPath()) {
			return;
		}

		if (!player.finishedPath()) player.resetPath();

		if (!PathValidation.checkCombatProjectilePath(
			player.getWorld(), player.getWorldLocation(),
			target.getWorldLocation())) {
			reset(ProjectileFailureReason.CANT_GET_CLEAR_SHOT);
			return;
		}

		if (config.WANT_RANGED_FACE_PLAYER) {
			player.face(target.getX(), target.getY()); // Player faces victim when ranging
		} else {
			player.face(player.getX() + 1, player.getY() - 1); // Authentic player always faced NW
		}

		if (target.isPlayer()) {
			final Player playerTarget = (Player) target;

			if (!playerTarget.getConfig().WANT_MYWORLD && playerTarget.getPrayers().isPrayerActivated(Prayers.PROTECT_FROM_MISSILES)) {
        player.message("Player has a protection from missiles prayer active!");
				// TODO Should range event be reset here?
				return;
			}

			if (pluginHandler.handlePlugin(PlayerRangePlayerTrigger.class, player, new Object[]{getOwner(), target})) {
				reset(ProjectileFailureReason.HANDLED_BY_PLUGIN);
				return;
			}
		} else {
			if (pluginHandler.handlePlugin(PlayerRangeNpcTrigger.class, getPlayerOwner(), new Object[]{getOwner(), target})) {
				reset(ProjectileFailureReason.HANDLED_BY_PLUGIN);
				return;
			}
		}

		final int ammoId;
		final boolean isCrossbow = RangeUtils.isCrossbow(weaponId);

    final int availableAmmo =
        config.WANT_EQUIPMENT_TAB
            ? takeAmmoFromEquipment(weaponId, isCrossbow, false)
            : takeAmmoFromInventory(weaponId, isCrossbow, false);
    if (availableAmmo == -1) return;
    if (com.openrsc.server.content.monsterslayer.SlayerLeatherEffects.delayAttack(player, com.openrsc.server.content.Slow.Cadence.RANGED)) return;
		if (config.WANT_EQUIPMENT_TAB) {
      ammoId = takeAmmoFromEquipment(weaponId, isCrossbow, true);
		} else {
      ammoId = takeAmmoFromInventory(weaponId, isCrossbow, true);
		}

		if (ammoId == -1) {
			ActionSender.sendSound(player, "outofammo");
			reset(ProjectileFailureReason.OUT_OF_AMMO);
			return;
		}
			boolean skillCape = SkillCapes.shouldActivate(player, ItemId.RANGED_CAPE);
		int delay = 3;
		if (skillCape) {
      player.playerServerMessage(
          MessageType.QUEST,
          "@gre@Your Ranged cape activates, letting you shoot two arrows at once!");
			delay = 1;
		}

    final com.openrsc.server.model.combat.AttackCommitment attack =
        com.openrsc.server.model.combat.AttackState.commitAttack(player);
    final com.openrsc.server.model.combat.AttackRoll attackRoll =
        attack.isSuppressed()
            ? com.openrsc.server.model.combat.AttackRoll.normal(0)
            : RangeUtils.rollRangedDamage(player, weaponId, ammoId, target, skillCape);
    final int damage = attackRoll.getDamage();
    final int dragonBreathDamage =
        attack.isSuppressed()
            ? 0
            : getWorld().getServer().getConfig().OSRS_COMBAT_RANGED
                ? 0
                : CombatFormula.rollDragonRangedBreathDamage(player, weaponId, ammoId, skillCape);

		final ProjectileResourceLedger resourceLedger =
			ProjectileResourceLedger.trackedLaunch(
				ProjectileLaunchSpecification.Producer.PLAYER_BOW);
		resourceLedger.recordItemCost(ammoId, 1, 1,
			config.WANT_EQUIPMENT_TAB
				? ProjectileResourceLedger.ItemSource.EQUIPMENT
				: ProjectileResourceLedger.ItemSource.INVENTORY,
			ProjectileResourceLedger.Preservation.NONE);
		final int rangedExperienceBefore =
			player.getSkills().getExperience(Skill.RANGED.id());
		int baseRangedExperience = 0;
		if ((target.isPlayer() || getWorld().getServer().getConfig().RANGED_GIVES_XP_HIT) && damage > 0) {
			baseRangedExperience = Formulae.rangedHitExperience(target, damage);
			player.incExp(Skill.RANGED.id(), baseRangedExperience, true);
		}

		RangeUtils.applyDragonFireBreath(player, target, deliveredFirstProjectile);
		final ProjectileResourceLedger.RecoveryDestination recovery =
			RangeUtils.settleProjectileRecovery(
				getWorld(), player, target, damage, ammoId);
		resourceLedger.recordRecovery(ammoId,
			recovery == ProjectileResourceLedger.RecoveryDestination.NOT_RECOVERED
				? 0 : 1,
			recovery, target.getWorldLocation());
		resourceLedger.recordExperience(Skill.RANGED.id(),
			baseRangedExperience,
			player.getSkills().getExperience(Skill.RANGED.id())
				- rangedExperienceBefore,
			ProjectileResourceLedger.ExperienceBasis.RANGED_HIT);
		resourceLedger.seal();

		getOwner().setKillType(KillType.RANGED);
		deliveredFirstProjectile = true;

        final com.openrsc.server.content.Slow.Interval interval = RangeUtils.getAdjustedRangePlan(player, delay);
        final int adjustedDelay = interval.ticks;
		com.openrsc.server.content.Slow.commit(player, com.openrsc.server.content.Slow.Cadence.RANGED, adjustedDelay, interval.tier);
        setDelayTicks(adjustedDelay);

    player.setAttribute("can_range_again", getWorld().getServer().getCurrentTick() + adjustedDelay);
        ActionSender.sendSound(player, "shoot");
    getWorld()
        .getServer()
        .getGameEventHandler()
        .add(
            new ProjectileEvent(
                getWorld(),
                player,
                target,
                ProjectileLaunchSpecification.builder(
                        ProjectileLaunchSpecification.Producer.PLAYER_BOW, damage, 2)
                    .chase(true)
                    .poisonWeaponId(ammoId)
                    .presentation(isCrossbow ? Projectile.BOLT : Projectile.ARROW, 0, true)
                    .dragonBreathDamage(dragonBreathDamage)
                    .duplicationStrategy(DuplicationStrategy.ONE_PER_MOB)
                    .attackCommitment(attack)
                    .attackRoll(attackRoll)
                    .build(),
                resourceLedger));
	}

  private int takeAmmoFromInventory(final int weaponId, final boolean isCrossbow, boolean consume) {
		final int[] ammoIds = isCrossbow ? Formulae.boltIDs : Formulae.arrowIDs;

		final Inventory inventory = player.getCarriedItems().getInventory();

		for (final int ammoId : ammoIds) {
			final int slot = inventory.getLastIndexById(ammoId);

			if (slot == -1) {
				continue;
			}

			final Item ammo = inventory.get(slot);

			if (ammo == null) { // This shouldn't happen
				continue;
			}

			if (!config.MEMBER_WORLD && ammoId != ItemId.BRONZE_ARROWS.id() && ammoId != ItemId.CROSSBOW_BOLTS.id()) {
				reset(ProjectileFailureReason.NOT_ENOUGH_AMMO_ARROWS);
				return -1;
			}

			if (!isCrossbow && !RangeUtils.canFire(weaponId, ammoId)) {
				reset(ProjectileFailureReason.ARROWS_TOO_POWERFUL);
				return -1;
			}

			final Item item = new Item(ammo.getCatalogId(), 1, false, ammo.getItemId());
      if (consume) player.getCarriedItems().remove(item);
			return ammoId;
		}

		return -1;
	}

  private int takeAmmoFromEquipment(final int weaponId, final boolean isCrossbow, boolean consume) {
		final Equipment equipment = player.getCarriedItems().getEquipment();

		final Item ammo = equipment.getAmmoItem();

		if (ammo == null) {
			reset(ProjectileFailureReason.NO_AMMO_EQUIPPED);
			return -1;
		}

		final ItemDefinition itemDef = ammo.getDef(getWorld());

		if (itemDef == null) {
			reset(ProjectileFailureReason.NO_AMMO_EQUIPPED);
			return -1;
		}

		if (isCrossbow && itemDef.getWearableId() == RangeUtils.WEARABLE_ARROWS_ID) {
			reset(ProjectileFailureReason.CANT_FIRE_ARROWS_WITH_CROSSBOW);
			return -1;
		}

		if (!isCrossbow && itemDef.getWearableId() == RangeUtils.WEARABLE_BOLTS_ID) {
			reset(ProjectileFailureReason.CANT_FIRE_BOLTS_WITH_BOW);
			return -1;
		}

		final int ammoId = ammo.getCatalogId();

		if (!RangeUtils.canFire(weaponId, ammoId)) {
			if (ammoId == ItemId.DRAGON_BOLTS.id() || ammoId == ItemId.POISON_DRAGON_BOLTS.id()) {
				reset(ProjectileFailureReason.BOLTS_TOO_POWERFUL);
				return -1;
			}

			if (ammoId == ItemId.DRAGON_CROSSBOW.id()) {
				reset(ProjectileFailureReason.BOLTS_WONT_FIT_DRAGON_CROSSBOW);
				return -1;
			}

			if (ammoId == ItemId.DRAGON_LONGBOW.id()) {
				reset(ProjectileFailureReason.ARROWS_WONT_FIT_DRAGON_LONGBOW);
				return -1;
			}

			reset(ProjectileFailureReason.ARROWS_TOO_POWERFUL);
			return -1;
		}

    if (consume) {
      equipment.remove(ammo, 1);
      ActionSender.updateEquipmentSlot(player, 12);
    }
		return ammoId;
	}

	private void reset(final ProjectileFailureReason reason) {
		if (reason != null) {
			final String message = reason.getText();
			if (message != null) {
				player.message(message);
			}
		}
		player.terminateRangeEvent(
			this, CombatEngagementTerminalReason.EVENT_ENDED);
	}
}
