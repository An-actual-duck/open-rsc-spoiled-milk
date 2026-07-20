package com.openrsc.server.model.entity;

import com.openrsc.server.constants.IronmanMode;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public class GroundItem extends Entity {
	/**
	 * Amount (for stackables)
	 */
	private int amount;

	/**
	 * Is item noted?
	 */
	private boolean noted;

	/**
	 * Location definition of the item
	 */
	private ItemLoc loc = null;

	/**
	 * Contains the player that the item belongs to, if any
	 */
	private long ownerUsernameHash;
	/**
	 * The time that the item was spawned
	 */
	private long spawnedTime;

	public GroundItem(final World world, final int id, final int x, final int y, final int amount, final Player owner) {
		this(world, id, x, y, amount, owner, System.currentTimeMillis());
	}

	public GroundItem(final World world, final int id, final int x, final int y, final int amount) {
		this(world, id, x, y, amount, null);
	}

	public GroundItem(final World world, final int id, final int x, final int y, final int amount, final long spawnTime) {
		this(world, id, x, y, amount, null, spawnTime);
	}

	public GroundItem(final World world, final int id, final int x, final int y, final int amount, final Player owner, final boolean noted) {
		this(world, id, x, y, amount, owner, System.currentTimeMillis(), noted);
	}

	public GroundItem(final World world, final int id, final int x, final int y, final int amount, final Player owner, final long spawnTime) {
		this(world, id, x, y, amount, owner, spawnTime, false);
	}

	public GroundItem(final World world, final int id, final int x, final int y, final int amount, final Player owner, final long spawnTime, final boolean noted) {
		super(world, EntityType.GROUND_ITEM);
		setID(id);
		setNoted(noted);
		setAmount(amount);
		this.ownerUsernameHash = owner == null ? 0 : owner.getUsernameHash();
		spawnedTime = spawnTime;
		trySetLocation(Point.location(x, y));
		if (owner != null) {
			if (owner.getIronMan() == IronmanMode.Transfer.id()) {
				// disallow everyone from picking up transfer ironman items
				this.setAttribute("isTransferIronmanItem", true);
			}
		}
	}

	public GroundItem(final World world, final ItemLoc loc) {
		super(world, EntityType.GROUND_ITEM);
		if (loc.getAuthoredPlacementIdentity() != null) {
			assignAuthoredPlacementIdentity(
				loc.getAuthoredPlacementIdentity());
		}
		this.loc = loc;
		setID(loc.id);
		setAmount(loc.amount);
		spawnedTime = System.currentTimeMillis();
		trySetLocation(Point.location(loc.x, loc.y));
	}

	public void trySetLocation(Point point) {
		if (getWorld().getServer().getConfig().RESTRICT_ITEM_ID <= ItemId.NOTHING.id()
			|| this.getID() <= getWorld().getServer().getConfig().RESTRICT_ITEM_ID) {
			setLocation(point);
		}
	}

	public boolean isOn(final int x, final int y) {
		return x == getX() && y == getY();
	}

	public boolean belongsTo(final Player player) {
		if (getAttribute("personalNpcDrop", false)) {
			return player.getUsernameHash() == ownerUsernameHash;
		}

		// This attribute marks a player death pile created when a mob killed the player.
		long killedByMobOwner = getAttribute("killedByMob", -1L);
		if (killedByMobOwner != -1L) {
			return killedByMobOwner == player.getUsernameHash();
		}

		return player.getUsernameHash() == ownerUsernameHash || ownerUsernameHash == 0;
	}

	public void remove() {
		if (getWorld().getServer().getConfig().RESTRICT_ITEM_ID <= ItemId.NOTHING.id()
			|| this.getID() <= getWorld().getServer().getConfig().RESTRICT_ITEM_ID) {
			final long authoredGeneration = !isRemoved() && loc != null
				? getWorld().removeAuthoredGroundItem(this)
				: -1L;
			if (authoredGeneration >= 0 && loc.getRespawnTime() > 0) {
				getWorld().getServer().getGameEventHandler().add(new DelayedEvent(getWorld(), null, loc.getRespawnTime() * 1000, "Respawn Ground Item") {
					public void run() {
						getWorld().registerAuthoredGroundItem(loc, authoredGeneration);
						stop();
					}
				});
			}
			super.remove();
		}
	}

	public boolean isInvisibleTo(final Player player) {
		if (getAttribute("personalNpcDrop", false) && !belongsTo(player)) {
			return true;
		}
		if (belongsTo(player))
			return false;
		if (getDef().isMembersOnly() && !getWorld().getServer().getConfig().MEMBER_WORLD)
			return true;
		if (getDef().isUntradable())
			return true;
		if (getAttribute("killerHash", -1L) == player.getUsernameHash())
			return false;
		if (getID() > player.getClientLimitations().maxItemId)
			return true;
		// If the killedByMob attribute exists, this means that the pile was dropped when another player was killed by a mob.
		// The ironman should be able to see it, but still not pick it up.
		if (player.getIronMan() != IronmanMode.None.id() && getAttribute("killedByMob", -1L) != -1) {
			return false;
		}

		// One minute and four seconds to show to all.
		return System.currentTimeMillis() - spawnedTime <= 64000;
	}

	@Override
	public String toString() {
		return "Item(" + this.getID() + ", " + this.amount + ") location = " + getLocation().toString();
	}

	public ItemDefinition getDef() {
		return getWorld().getServer().getEntityHandler().getItemDef(getID());
	}

	public ItemLoc getLoc() {
		return loc;
	}

	public long getOwnerUsernameHash() {
		return ownerUsernameHash;
	}

	public boolean getNoted() {
		return noted;
	}

	public void setNoted(final boolean noted) {
		this.noted = noted;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(final int amount) {
		if (getDef() != null) {
			if (getDef().isStackable() || getNoted()) {
				this.amount = amount;
			} else {
				this.amount = 1;
			}
		}
	}

	private long getSpawnedTime() {
		return spawnedTime;
	}
}
