package com.openrsc.server.model.entity;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.handler.GameEventHandler;
import com.openrsc.server.event.rsc.impl.BurnEvent;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.event.rsc.impl.StatRestorationEvent;
import com.openrsc.server.event.rsc.impl.WaterSlowEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEventNpc;
import com.openrsc.server.model.*;
import com.openrsc.server.model.Path.PathType;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcInteraction;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.UpdateFlags;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.states.HostileState;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.DropObjTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Mob extends Entity {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	protected static final int DEFAULT_PROJECTILE_RADIUS = 5;
	public static final int ATTACK_BASED_DEBUFF_ATTACKS = 5;

	private long lastMovementTime = 0;
	private final Skills skills = new Skills(this.getWorld(), this);
	private final WalkingQueue walkingQueue = new WalkingQueue(this);
	private KillType killType = KillType.COMBAT;
	public boolean killed = false;
	private int combatStyle = com.openrsc.server.constants.Skills.CONTROLLED_MODE;
	private int burnDamage = 0;
	private int burnPulseCount = 0;
	private int windLowRollBiasPercent = 0;
	private int windDebuffAttacksRemaining = 0;
	private int bearIntimidatePercent = 0;
	private int bearIntimidateAttacksRemaining = 0;
	private int ogreStaggerAttacksRemaining = 0;
	private int startleDebuffAttacksRemaining = 0;
	private Mob startleSource = null;
	private int frostbiteDebuffAttacksRemaining = 0;
	private Mob frostbiteSource = null;
	private int smokeAccuracyDebuffPercent = 0;
	private int smokeAccuracyDebuffAttacksRemaining = 0;
	private int waterMaxHitDebuffPercent = 0;
	private int waterMaxHitDebuffAttacksRemaining = 0;
	private int dragonWaterMaxHitDebuffPercent = 0;
	private int dragonWaterMaxHitDebuffAttacksRemaining = 0;
	private int earthAttackSpeedDebuffPercent = 0;
	private int earthAttackSpeedDebuffAttacksRemaining = 0;
	private int dragonEarthAttackSpeedDebuffPercent = 0;
	private int dragonEarthAttackSpeedDebuffAttacksRemaining = 0;
	private int fireDefenseDebuffPercent = 0;
	private int fireDefenseDebuffAttacksRemaining = 0;
	private int dragonFireDefenseDebuffPercent = 0;
	private int dragonFireDefenseDebuffAttacksRemaining = 0;
	private int infernalFireDefenseDebuffPercent = 0;
	private int infernalFireDefenseDebuffAttacksRemaining = 0;
	private int poisonDamage = 0;
	private int poisonMaxPower = 0;
	private int waterSlowPercent = 0;
	private RangeEventNpc rangeEventNpc;
	private long lastRun = 0;
	private boolean teleporting;
	/**
	 * Flag to indicate that this mob will be needed to be unregistered after
	 * next update tick.
	 */
	protected boolean unregistering;
	/**
	 * The combat event instance.
	 */
	protected CombatEvent combatEvent;
	protected PvmMeleeEvent pvmMeleeEvent;
	/**
	 * Have we moved since last update?
	 */
	private boolean hasMoved;
	/**
	 * Time of last movement, used for timeout
	 */
	private long lastMovement = System.currentTimeMillis();
	private int mobSprite = 0;
	/**
	 * The stat restore event
	 */
	private StatRestorationEvent statRestorationEvent;
	/**
	 * If we are warned to move
	 */
	private boolean warnedToMove = false;
	/**
	 * Unique ID for event tracking.
	 */
	private final UUID uuid;
	/**
	 * Timer used to track start and end of combat
	 */
	private long combatTimer = 0;
	/**
	 * Timer used to track when a mob gets away
	 */
	private long ranAwayTimer = 0;
	/**
	 * Who they are in combat with
	 */
	private volatile Mob combatWith = null;
	/**
	 * Who they were last in combat with
	 */
	private volatile Mob lastCombatWith = null;
	/**
	 * Event to handle following
	 */
	private GameTickEvent followEvent;
	/**
	 * Who we are currently following (if anyone)
	 */
	private Mob following;
	/**
	 * Event to handle possessing
	 */
	private GameTickEvent possessionEvent = null;
	/**
	 * Event to handle automatic possession
	 */
	private GameTickEvent LAINEvent = null;
	/**
	 * Who we are currently possessing (if anyone)
	 */
	private Mob possessing;
	/**
	 * Name of player we are possessing (if anyone)
	 */
	public String possessingUsername;
	/**
	 * If the moderator has been alerted that the person they were posessing logged out
	 */
	public boolean knowsPossesseeLoggedOut = false;
	/**
	 * The related mob (owner, in the case of pets)
	 */
	public Mob relatedMob;
	/**
	 * How many times we have hit our opponent
	 */
	private int hitsMade = 0;
	/**
	 * The end state of the last combat encounter
	 */
	private CombatState lastCombatState = CombatState.WAITING;
	/**
	 * New simplified hostility state replacing complex combat states
	 */
	private final HostileState hostileState = new HostileState();
	/**
	 * Has the sprite changed?
	 */
	private boolean spriteChanged = false;
	/**
	 * Holds all the update flags for the appearance packet.
	 */
	private UpdateFlags updateFlags = new UpdateFlags();
	/**
	 * Used to block new requests when we are in the middle of one
	 */
	private final AtomicBoolean busy = new AtomicBoolean(false);
	/**
	 * Tiles around us that we can see
	 */
	private ViewArea viewArea = new ViewArea(this);

	private NpcInteraction npcInteraction = null;

	/**
	 * How many tiles away do we want to end the following event?
	 */
	private int endFollowRadius = -1;


	public Mob(final World world, final EntityType type) {
		super(world, type);
		statRestorationEvent = new StatRestorationEvent(getWorld(), this);
		uuid = UUID.randomUUID();
	}

	/**
	 * ABSTRACT
	 */
	public abstract int getWeaponAimPoints();

	public abstract int getWeaponPowerPoints();

	public abstract int getArmourPoints();

	public abstract int getMeleeOffense();

	public abstract int getRangedOffense();

	public abstract int getMagicOffense();

	public abstract int getMeleeDefense();

	public abstract int getRangedDefense();

	public abstract int getMagicDefense();

	public abstract double getDamageRollHighBiasChance();

	public abstract double getArmorSpeedMultiplier();

	public abstract int getCombatStyle();

	public abstract boolean stateIsInvisible();

	public abstract boolean stateIsInvulnerable();

	public static final int ELEMENTAL_DEBUFF_DURATION_TICKS = 24;

	public int getWalkingTick() {
		if (getWorld().getServer().getConfig().WANT_CUSTOM_WALK_SPEED) {
			return getWorld().getServer().getConfig().WALKING_TICK;
		}
		return isNpc()
			? getWorld().getServer().getConfig().GAME_TICK
			: getWorld().getServer().getConfig().WALKING_TICK;
	}

	/**
	 * POSITIONING AND PATHING
	 */
	public boolean isOn(final int x, final int y) {
		return x == getX() && y == getY();
	}

	public final boolean atObject(final GameObject o) {
		final Point[] boundaries = o.getObjectBoundary();
		final Point low = boundaries[0];
		final Point high = boundaries[1];
		if (o.getType() == 0) {
			if (o.getGameObjectDef().getType() == 2 || o.getGameObjectDef().getType() == 3) {
				return getX() >= low.getX() && getX() <= high.getX() && getY() >= low.getY() && getY() <= high.getY();
			} else {
				return canReach(low.getX(), high.getX(), low.getY(), high.getY())
					|| (finishedPath() && canReachDiagonal(low.getX(), high.getX(), low.getY(), high.getY()))
					|| closeSpecObject(o);
			}
		} else if (o.getType() == 1) {
			return getX() >= low.getX() && getX() <= high.getX() && getY() >= low.getY() && getY() <= high.getY();
		}
		return false;
	}

	//TODO: Verify block of special rock in tourist trap
	private boolean closeSpecObject(final GameObject o) {
		final Point[] boundaries = o.getObjectBoundary();
		final Point low = boundaries[0];
		final Point high = boundaries[1];
		final int lowXDiff = Math.abs(getX() - low.getX());
		final int highXDiff = Math.abs(getX() - high.getX());
		final int lowYDiff = Math.abs(getY() - low.getY());
		final int highYDiff = Math.abs(getY() - high.getY());

		//Runecraft objects need to be accessible from all angles
		if (o.getID() >= 1190 && o.getID() <= 1225) {
			if ((lowXDiff <= 2 || highXDiff <= 2) && (lowYDiff <= 2 || highYDiff <= 2))
				return true;
		} else if (o.getID() == 1227)
			if ((lowXDiff <= 2 || highXDiff <= 2) && (highYDiff <= 3))
				return true;
			else if (o.getID() >= 1228 && o.getID() <= 1232)
				if ((lowXDiff <= 2 || highXDiff <= 2) && (lowYDiff <= 2 || highYDiff <= 2))
					return true;
		return false;
	}

	private boolean canReach(int minX, int maxX, int minY, int maxY) {
		if (getX() >= minX && getX() <= maxX && getY() >= minY && getY() <= maxY) {
			return true;
		}
		if (minX <= getX() - 1 && maxX >= getX() - 1 && minY <= getY() && maxY >= getY()
			&& (getWorld().getTile(getX() - 1, getY()).traversalMask & CollisionFlag.WALL_WEST) == 0) {
			return true;
		}
		if (1 + getX() >= minX && getX() + 1 <= maxX && getY() >= minY && maxY >= getY()
			&& (CollisionFlag.WALL_EAST & getWorld().getTile(getX() + 1, getY()).traversalMask) == 0) {
			return true;
		}
		if (minX <= getX() && maxX >= getX() && getY() - 1 >= minY && maxY >= getY() - 1
			&& (CollisionFlag.WALL_SOUTH & getWorld().getTile(getX(), getY() - 1).traversalMask) == 0) {
			return true;
		}
		return false;
	}

	private boolean canReachDiagonal(int minX, int maxX, int minY, int maxY) {
		if (minX <= getX() && getX() <= maxX && minY <= getY() + 1 && maxY >= getY() + 1
			&& (CollisionFlag.WALL_NORTH & getWorld().getTile(getX(), getY() + 1).traversalMask) == 0) {
			return true;
		}
		if (minX <= getX() - 1 && maxX >= getX() - 1 && minY <= getY() - 1 && maxY >= getY() - 1
			&& (getWorld().getTile(getX() - 1, getY() - 1).traversalMask & CollisionFlag.WALL_SOUTH_WEST) == 0) {
			return true;
		}
		if (1 + getX() >= minX && getX() + 1 <= maxX && getY() - 1 >= minY && maxY >= getY() - 1
			&& (CollisionFlag.WALL_SOUTH_EAST & getWorld().getTile(getX() + 1, getY() - 1).traversalMask) == 0) {
			return true;
		}
		if (minX <= getX() - 1 && maxX >= getX() - 1 && minY <= getY() + 1 && maxY >= getY() + 1
			&& (getWorld().getTile(getX() - 1, getY() + 1).traversalMask & CollisionFlag.WALL_NORTH_WEST) == 0) {
			return true;
		}
		if (1 + getX() >= minX && getX() + 1 <= maxX && getY() + 1 >= minY && maxY >= getY() + 1
			&& (CollisionFlag.WALL_NORTH_EAST & getWorld().getTile(getX() + 1, getY() + 1).traversalMask) == 0) {
			return true;
		}
		return false;
	}

	// canReach EVER, not canReach this tick
	public final boolean canReach(Entity e) {
		int[] currentCoords = {getX(), getY()};
		while (currentCoords[0] != e.getX() || currentCoords[1] != e.getY()) {
			currentCoords = nextStep(currentCoords[0], currentCoords[1], e);
			if (currentCoords == null) {
				return false;
			}
		}
		return true;
	}

	public int[] nextStep(final int myX, final int myY, final Entity e) {
		if (myX == e.getX() && myY == e.getY()) {
			return new int[]{myX, myY};
		}
		int newX = myX, newY = myY;
		boolean myXBlocked = false, myYBlocked = false, newXBlocked = false, newYBlocked = false;

		if (myX > e.getX()) {
			myXBlocked = isBlocking(e, myX - 1, myY, 8); // Check right tiles
			newX = myX - 1;
		} else if (myX < e.getX()) {
			myXBlocked = isBlocking(e, myX + 1, myY, 2); // Check left tiles
			newX = myX + 1;
		}
		if (myY > e.getY()) {
			myYBlocked = isBlocking(e, myX, myY - 1, 4); // Check top tiles
			newY = myY - 1;
		} else if (myY < e.getY()) {
			myYBlocked = isBlocking(e, myX, myY + 1, 1); // Check bottom tiles
			newY = myY + 1;
		}

		// If both directions are blocked OR we are going straight and the
		// direction is blocked
		if ((myXBlocked && myYBlocked) || (myXBlocked && myY == newY) || (myYBlocked && myX == newX)) {
			return null;
		}

		if (newX > myX) {
			newXBlocked = isBlocking(e, newX, newY, 2); // Check dest tiles
			// right wall
		} else if (newX < myX) {
			newXBlocked = isBlocking(e, newX, newY, 8); // Check dest tiles left
			// wall
		}

		if (newY > myY) {
			newYBlocked = isBlocking(e, newX, newY, 1); // Check dest tiles top
			// wall
		} else if (newY < myY) {
			newYBlocked = isBlocking(e, newX, newY, 4); // Check dest tiles
			// bottom wall
		}

		// If both directions are blocked OR we are going straight and the
		// direction is blocked
		if ((newXBlocked && newYBlocked) || (newXBlocked && myY == newY) || (myYBlocked && myX == newX)) {
			return null;
		}

		// If only one direction is blocked, but it blocks both tiles
		if ((myXBlocked && newXBlocked) || (myYBlocked && newYBlocked)) {
			return null;
		}

		return new int[]{newX, newY};
	}

	private boolean isBlocking(Entity e, int x, int y, int bit) {
		int val = getWorld().getTile(x, y).traversalMask;
		if ((val & bit) != 0) {
			return true;
		}
		if ((val & 16) != 0) {
			return true;
		}
		if ((val & 32) != 0) {
			return true;
		}
		return (val & 64) != 0
			&& (e instanceof Npc || e instanceof Player || (e instanceof GroundItem && !((GroundItem) e).isOn(x, y))
			|| (e instanceof GameObject && !((GameObject) e).isOn(x, y)));
	}

	public boolean withinRange(final Entity e) {
		if (e != null) {
			return getLocation().withinRange(e.getLocation(), (getWorld().getServer().getConfig().VIEW_DISTANCE * 8) - 1);
		}
		return false;
	}

	// Authentic protocol can only show up to 15 tiles away, so for instances where distance between entities
	// determines if we will display something to a player or not, we potentially need to restrict distance further.
	// This check can go before withinRange(Entity e) to shortcircuit & not have to check twice (unless View_distance is 1)
	public boolean withinAuthenticRangeAdditionally(final Player playerToUpdate) {
		if (playerToUpdate != null) {
			if (playerToUpdate.isUsingCustomClient() || getWorld().getServer().getConfig().VIEW_DISTANCE <= 2)
				return true; // don't need additional restraint in these cases

			return getLocation().withinRange(playerToUpdate.getLocation(), 15);
		}
		return false;
	}

	public boolean withinGridRange(final Entity e) {
		if (e != null) {
			return getLocation().withinGridRange(e.getLocation(), getWorld().getServer().getConfig().VIEW_DISTANCE);
		}
		return false;
	}

	public boolean withinObjectGridRange(final Entity e) {
		if (e != null) {
			return getLocation().withinGridRange(e.getLocation(), getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE);
		}
		return false;
	}

	public boolean within4GridRange(final Entity e) {
		if (e != null) {
			return getLocation().withinGridRange(e.getLocation(), 4);
		}
		return false;
	}

	public void face(final Entity entity) {
		/* Now that NPCs are processed first, face() *should not* be used in Plugins for NPCs unless you are certain that the NPC does not move.
		This causes desyncs in clients, since they will process the changing sprite while the NPC is moving.
		Instead, use NpcInteractions if you need the player and NPC to face each other and handle other logic in their movement processing.
		*/
		if (entity != null && entity.getLocation() != null) {
			final int dir = Formulae.getDirection(this, entity.getX(), entity.getY());
			if (dir != -1) {
				setSprite(dir);
			}
		}
	}

	public void face(final int x, final int y) {
		final int dir = Formulae.getDirection(this, x, y);
		if (dir != -1) {
			setSprite(dir);
		}
	}

	public void face(final Point location) {
		final int dir = Formulae.getDirection(this, location.getX(), location.getY());
		if (dir != -1) {
			setSprite(dir);
		}
	}

	public void faceCombat(final Entity entity) {
		if (entity == null || entity.getLocation() == null) {
			return;
		}
		final int dir = Formulae.getDirection(this, entity.getX(), entity.getY());
		if (dir != -1) {
			setSprite(8 + dir);
		}
	}

	public void setFollowing(final Mob mob, final int radius) {
		setFollowing(mob, radius, true, false);
	}

	public void setFollowing(final Mob mob, final int radius, final boolean canInterrupt) {
		setFollowing(mob, radius, canInterrupt, false);
	}

	public void setFollowing(final Mob mob, final int radius, final boolean canInterrupt, final boolean stopAtEnd) {
		if (isFollowing()) resetFollowing();
		if (stopAtEnd) setEndFollowRadius(radius);
		following = mob;
		followEvent = new GameTickEvent(getWorld(), this, 0, "Mob Following Mob", DuplicationStrategy.ONE_PER_MOB) {

			public void run() {
				if (getDelayTicks() == 0) setDelayTicks(1);

				if (mob.isRemoved() || inCombat()) {
					resetFollowing();
					return;
				}

				// Handles the following cases:
				//   1. Mob is out of view range,
				//   2. Mob is removed,
				//   3. Player is busy, but not in a duel (duel should not stop following opponent), and
				//   4. Mob is not following something.
				boolean duelActive = (isPlayer() && ((Player) Mob.this).getDuel().isDuelActive());
				boolean shouldInterrupt = canInterrupt && (!duelActive && isBusy());
				//In range and adjacent, and we ran this event more than once.
				boolean shouldStopWalking = withinRange(mob, radius)
					&& PathValidation.checkAdjacentDistance(getWorld(), getX(), getY(), mob.getX(), mob.getY(), true, false)
					&& getTimesRan() >= 1;

				if (!withinRange(mob) || shouldInterrupt) {
					if (!mob.isFollowing()) {
						resetFollowing();
					}
				} else if ((radius > 0 && getWalkingQueue().getNextMovement().equals(mob.getLocation())) || shouldStopWalking) {
					//Don't allow more walking if the radius is more than 0 and the next step will move the follower onto the followee's tile.
					//Also don't allow more if the conditionals are fulfilled.
					resetPath();
				} else if (finishedPath()) {
					// We have finished the current follow path, but we need to keep walking to get to the target.
					walkToEntity(mob.getX(), mob.getY());
				}
			}
		};
		getWorld().getServer().getGameEventHandler().add(followEvent);
	}

	public void setPossessing(final Mob mob) {
		possessing = mob;
		if (mob instanceof Player) {
			possessingUsername = ((Player)mob).getUsername();
		} else {
			possessingUsername = ((Npc) possessing).getDef().getName();
		}
		possessionEvent = new GameTickEvent(getWorld(), this, 0, "Moderator possessing Mob", DuplicationStrategy.ALLOW_MULTIPLE) {
			public void run() {
				setDelayTicks(1);
				Player moderator = (Player)getOwner();
				Mob possessee = moderator.getPossessing();

				if (possessee == null || possessee.isRemoved()) {
					if (possessee instanceof Player) {
						if (!moderator.knowsPossesseeLoggedOut) {
							moderator.message("The body you possessed has left this world, but your spirit still searches for them...");
							moderator.knowsPossesseeLoggedOut = true;
						}
						Player targetPlayer = moderator.getWorld().getPlayer(DataConversions.usernameToHash(moderator.possessingUsername));
						if (targetPlayer == null)
							return;
						moderator.message("Your spirit has found @mag@" + possessingUsername + "@whi@ once again.");
						moderator.knowsPossesseeLoggedOut = false;
						setPossessing(targetPlayer);
					} else {
						if (possessingUsername != null) {
							moderator.message("Your spirit leaves the @mag@" + possessingUsername + "@whi@ as it dies...");
							moderator.setCacheInvisible(false);
							resetFollowing(false);
						} else {
							this.stop();
						}
						return;
					}
				}

				int curY = moderator.getLocation().getY();
				final Point nextPoint = possessee.getWalkingQueue().getNextMovement();
				moderator.setLocation(nextPoint, false);
				if (Math.abs(nextPoint.getY() - curY) >= 16) {
					// set_floor is necessary if climbing ladder or teleporting
					ActionSender.sendWorldInfo(moderator);
				}
			}
		};
		getWorld().getServer().getGameEventHandler().add(possessionEvent);

	}

	public void becomeLain(boolean serial, int interval) {
		if (!(this instanceof Player)) {
			return;
		}
		Player lain = (Player) this;
		lain.setCacheInvisible(true);
		lain.message("@yel@Lain: Hello, Navi.");
		lain.message("@whi@Navi: Hello, Lain.");
		if (LAINEvent != null) {
			LAINEvent.stop();
		}
		if (serial) {
			// automates ::pn command
			LAINEvent = new GameTickEvent(getWorld(), this, 0, "Lain Automatic Possession", DuplicationStrategy.ALLOW_MULTIPLE) {
				public void run() {
					setDelayTicks(interval);

					int preferredPid;
					if (lain.getPossessing() instanceof Player) {
						preferredPid = lain.getPossessing().getIndex() + 1;
					} else {
						// not currently possessing anything
						preferredPid = 0;
					}


					Player targetPlayer = lain.getWorld().getNextPlayer(preferredPid, lain.getIndex());

					if (targetPlayer == null || targetPlayer.getUsername().equals(lain.getUsername())) {
						lain.message("You are alone and disconnected, lain.");
						this.stop();
						return;
					} else {
						lain.setPossessing(targetPlayer);
					}
				}
			};
		} else {
			// random experiments lain?
			// automates ::pr command
			LAINEvent = new GameTickEvent(getWorld(), this, 0, "Lain Automatic Possession", DuplicationStrategy.ALLOW_MULTIPLE) {
				public void run() {
					setDelayTicks(interval);

					Player targetPlayer = null;
					int retries = 0;
					while ((targetPlayer == null || targetPlayer.getUsername().equals(lain.getUsername())) && retries++ < 30) {
						targetPlayer = this.getWorld().getRandomPlayer();
					}
					if (targetPlayer == null || targetPlayer.getUsername().equals(lain.getUsername())) {
						lain.message("Everyone has forgotten you, lain.");
						this.stop();
						return;
					} else {
						lain.setPossessing(targetPlayer);
					}
				}
			};
		}
		getWorld().getServer().getGameEventHandler().add(LAINEvent);
	}

	public void resetLain() {
		if (LAINEvent != null) {
			LAINEvent.stop();
			LAINEvent = null;
		}
	}

	public void setFollowingAstar(final Mob mob, final int radius) {
		setFollowingAstar(mob, radius, 20);
	}

	public void setFollowingAstar(final Mob mob, final int radius, final int depth) {
		if (isFollowing()) {
			resetFollowing();
		}
		final Mob me = this;
		following = mob;
		followEvent = new GameTickEvent(getWorld(), null, 1, "Player Following Mob", DuplicationStrategy.ONE_PER_MOB) {
			public void run() {
				if (!me.withinRange(mob) || mob.isRemoved()
					|| (me.isPlayer() && !((Player) me).getDuel().isDuelActive() && me.isBusy())) {
					if (!mob.isFollowing())
						resetFollowing();
				} else if (!me.finishedPath() && me.withinRange(mob, radius)) {
					me.resetPath();
				} else if (me.finishedPath() && !me.withinRange(mob, radius)) {
					me.walkToEntityAStar(mob.getX(), mob.getY(), depth);
				} else if (mob.isRemoved()) {
					resetFollowing();
				}
			}
		};
		getWorld().getServer().getGameEventHandler().add(followEvent);
	}

	public void resetFollowing(boolean tellLeft) {
		following = null;
		setEndFollowRadius(-1);
		if (followEvent != null) {
			followEvent.stop();
			//Need to remove this *now*, otherwise it's too late. Can only have one follow event per mob.
			GameEventHandler geh = getWorld().getServer().getGameEventHandler();
			if (geh.has(followEvent)) {
				geh.remove(followEvent);
			}
			followEvent = null;
		}

		if (LAINEvent != null) {
			LAINEvent.stop();
			LAINEvent = null;
		}

		if (possessionEvent != null) {
			if (tellLeft) {
				if (this instanceof Player) {
					if (possessing instanceof Player) {
						((Player) this).message("Your spirit has left @mag@" + possessingUsername + "@whi@ and returned to your body.");
					} else {
						((Player) this).message("Your spirit has left @mag@" + ((Npc) possessing).getDef().getName() + "@whi@ and returned to your body.");
						((Player) this).setCacheInvisible(false);
					}
				}
			}
			possessionEvent.stop();
			possessionEvent = null;
			possessing = null;
			possessingUsername = null;
		}
		resetPath();
	}

	public void resetFollowing() {
		resetFollowing(true);
	}

	public void setLocation(final Point point, boolean teleported) {
		if (!teleported && this.getLocation().isWithin1Tile(point)) {
			this.setHasMoved(true);
		} else {
			setTeleporting(true);
		}

		setLastMoved();
		setWarnedToMove(false);
		super.setLocation(point);
	}

	public void updatePosition() {
		final long now = System.currentTimeMillis();
		final boolean useWalkingTick = getWorld().getServer().getConfig().WANT_CUSTOM_WALK_SPEED
			|| getWalkingTick() != getWorld().getServer().getConfig().WALKING_TICK;
		final boolean doWalk = !useWalkingTick || now >= lastMovementTime + getWalkingTick();

		if (doWalk) {
			getWalkingQueue().processNextMovement();
			lastMovementTime = now;
		}
	}

	public void walk(final int x, final int y) {
		getWalkingQueue().reset();
		final Path path = new Path(this, PathType.WALK_TO_POINT);
		{
			path.addStep(x, y);
			path.finish();
		}
		getWalkingQueue().setPath(path);
	}

	public void walkToEntityAStar(final int x, final int y) {
		walkToEntityAStar(x, y, 20);
	}

	public void walkToEntityAStar(final int x, final int y, final int depth) {
		getWalkingQueue().reset();
		final Point mobPos = new Point(this.getX(), this.getY());
		final AStarPathfinder pathFinder = new AStarPathfinder(this.getWorld(), mobPos, new Point(x, y), depth);
		pathFinder.feedPath(new Path(this, PathType.WALK_TO_ENTITY));
		Path newPath = pathFinder.findPath();
		if (newPath == null)
			walkToEntity(x, y);
		else
			getWalkingQueue().setPath(newPath);
	}

	public void walkToEntity(final int x, final int y) {
		getWalkingQueue().reset();
		final Path path = new Path(this, PathType.WALK_TO_ENTITY);
		path.addStep(x, y);
		path.finish();
		getWalkingQueue().setPath(path);
	}

	/**
	 * COMBAT
	 */
	public void startCombat(final Mob victim) {
		Mob lock = this.getUUID().compareTo(victim.getUUID()) > 0 ? this : victim;
		synchronized (lock) {
			if (this.isPlayer() && victim.isPlayer()) {
				if (this.inCombat() || victim.inCombat()) return;
				startReciprocalCombat(victim);
				return;
			}
			startPvmCombat(victim);
		}
	}

	private void startReciprocalCombat(final Mob victim) {
			boolean gotUnderAttack = false;

			if (this.isPlayer()) {
				((Player) this).resetAll();
				((Player) this).produceUnderAttack();
			} else if (this.isNpc()) {
				((Npc) this).produceUnderAttack();
			} else {
				if (!this.isNpc()) {
					((Player) victim).produceUnderAttack();
				} else {
					((Npc) victim).produceUnderAttack();
				}
			}

			resetPath();
			resetRange();
			victim.resetPath();
			victim.resetRange();

			//victim.setBusy(true);
			// Set combat state but don't force players to face NPCs when attacked
			victim.setOpponent(this);
			victim.setCombatTimer();
			// Only force combat sprite on players who are actually engaging, not those being attacked
			if (!(victim.isPlayer() && this.isNpc())) {
				victim.faceCombat(this);
			}

			if (victim.isPlayer()) {
				Player playerVictim = (Player) victim;
				if (this.isPlayer()) {
					((Player) this).setSkulledOn(playerVictim);
				}
				// Use a less disruptive reset that preserves movement when being attacked by an NPC
				if (this.isNpc()) {
					playerVictim.resetAll(false, false);
				} else {
					playerVictim.resetAll();
				}
				gotUnderAttack = true;
				playerVictim.releaseUnderAttack();

				if (playerVictim.isSleeping()) {
					ActionSender.sendWakeUp(playerVictim, false, false);
					ActionSender.sendFatigue(playerVictim);
				}
			} else {
				if (this.isNpc()) {
					Npc attacker = (Npc) this;
					attacker.releaseUnderAttack();
				} else {
					Player attacker = (Player) this;
					attacker.releaseUnderAttack();
				}
			}

			if (victim.isNpc()) {
				Npc npcVictim = (Npc) victim;
				gotUnderAttack = true;
				npcVictim.releaseUnderAttack();
			} else {
				if (this.isNpc()) {
					Npc attacker = (Npc) this;
					attacker.releaseUnderAttack();
				} else {
					Player attacker = (Player) this;
					attacker.releaseUnderAttack();
				}
			}

			setLocation(victim.getLocation(), false);

			//setBusy(true);
			faceCombat(victim);
			setOpponent(victim);
			setCombatTimer();

			NpcInteraction interaction = NpcInteraction.NPC_ATTACK;
			if (victim.isPlayer() && this.isNpc()) {
				NpcInteraction.setInteractions(((Npc)this), ((Player)victim), interaction);
			}

			combatEvent = new CombatEvent(getWorld(), this, victim);
			victim.setCombatEvent(combatEvent);
			getWorld().getServer().getGameEventHandler().add(combatEvent);
			if (gotUnderAttack) {
				if (victim.isPlayer()) {
					// packet order is authentic here
					((Player) victim).message("You are under attack!");
					ActionSender.sendSound((Player) victim, "underattack");
				}
			}
	}

	private void startPvmCombat(final Mob victim) {
		if (this == victim || victim.isRemoved() || getSkills().getLevel(Skill.HITS.id()) <= 0 || victim.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final boolean attackerIsSummon = Summoning.isSummon(this);
		final boolean victimIsSummon = Summoning.isSummon(victim);
		if (victimIsSummon) {
			return;
		}

		if (combatEvent != null || (pvmMeleeEvent != null && pvmMeleeEvent.isRunning() && pvmMeleeEvent.getTarget().equals(victim))) {
			return;
		}

		if (pvmMeleeEvent != null) {
			resetCombatEvent();
		}

		if (this.isPlayer()) {
			((Player) this).resetAll();
			((Player) this).produceUnderAttack();
		} else if (this.isNpc()) {
			((Npc) this).produceUnderAttack();
		}

		resetPath();
		resetRange();

		boolean victimAutoRetaliates = !(this.isNpc() && victim.isPlayer()) || ((Player) victim).getAutoRetaliate();
		boolean passivePlayerVictim = this.isNpc() && victim.isPlayer() && !victimAutoRetaliates;

		boolean victimShouldAvoidCombat = false;
		if (victim.isPlayer()) {
			victimShouldAvoidCombat = ((Player) victim).shouldAvoidCombatEngagement();
		}

		if (!victim.inCombat() && victimAutoRetaliates) {
			if (!(victim.isPlayer() && this.isNpc())) {
				victim.resetPath();
				victim.resetRange();
			}
		}

		// Don't set player attacker's sprite to combat sprite - allows free movement
			if (this.isNpc() || victim.isPlayer()) {
				faceCombat(victim);
			}

			// Set hostility state instead of complex combat state
		setHostile(victim, this.isPlayer() ? HostileState.HostilityType.PROVOKED : HostileState.HostilityType.ATTACKED);
		setOpponent(victim);
		setCombatTimer();

		if (victim.isPlayer()) {
			Player playerVictim = (Player) victim;
			if (this.isPlayer()) {
				((Player) this).setSkulledOn(playerVictim);
			}
			playerVictim.releaseUnderAttack();
			if (playerVictim.isSleeping()) {
				ActionSender.sendWakeUp(playerVictim, false, false);
				ActionSender.sendFatigue(playerVictim);
			}
			// Allow combat timer for players attacked by NPCs - needed for damage exchange
			if (this.isNpc()) {
				playerVictim.setCombatTimer();
			}
		} else if (victim.isNpc()) {
			((Npc) victim).releaseUnderAttack();
		}

		if (!victim.inCombat()) {
			boolean assignVictimCombatStance = !attackerIsSummon;
			if (passivePlayerVictim) {
				assignVictimCombatStance = false;
				clearPassivePvmVictimState((Player) victim);
			}
			if (assignVictimCombatStance) {
				victim.setOpponent(this);
				victim.setCombatTimer();
				// Set victim hostility and combat state for proper animations
				if (!(victim.isPlayer() && this.isNpc())) {
					victim.setHostile(this, HostileState.HostilityType.RETALIATION);
					victim.faceCombat(this);
				} else if (victim.isPlayer() && this.isNpc()) {
					// Player attacked by NPC - allow combat animations but don't set combat sprite
					victim.setHostile(this, HostileState.HostilityType.ATTACKED);
					// Don't set sprite to combat (8/9) to prevent client walk blocking
				}
			}
		} else {
			if (!attackerIsSummon) {
				victim.setLastOpponent(this);
				victim.setCombatTimer();
			}
			if (passivePlayerVictim) {
				clearPassivePvmVictimState((Player) victim);
			}
		}

		NpcInteraction interaction = NpcInteraction.NPC_ATTACK;
		if (victim.isPlayer() && this.isNpc()) {
			NpcInteraction.setInteractions(((Npc) this), ((Player) victim), interaction);
		} else if (victim.isNpc() && this.isPlayer()) {
			NpcInteraction.setInteractions(((Npc) victim), ((Player) this), interaction);
		}

			pvmMeleeEvent = new PvmMeleeEvent(getWorld(), this, victim);
			getWorld().getServer().getGameEventHandler().addOrUpdate(pvmMeleeEvent);
		if (victim.isPlayer() && !victimShouldAvoidCombat) {
			((Player) victim).message("You are under attack!");
			ActionSender.sendSound((Player) victim, "underattack");
		}
		if (!attackerIsSummon) {
			victim.startPvmCounterCombat(this);
		}
	}

	public void startPvmCounterCombat(final Mob attacker) {
		if (Summoning.isSummon(this) || Summoning.isSummon(attacker)) {
			return;
		}
		if (combatEvent != null || (pvmMeleeEvent != null && pvmMeleeEvent.isRunning() && pvmMeleeEvent.getTarget().equals(attacker))) {
			return;
		}

		if (this.isPlayer() && attacker.isNpc() && !((Player) this).getAutoRetaliate()) {
			setLastOpponent(attacker);
			setCombatTimer();
			clearPassivePvmVictimState((Player) this);
			return;
		}

		if (this.isPlayer() && attacker.isNpc() && ((Player) this).getAutoCastSpell() != null) {
			resetPath();
			resetRange();
			if (MagicCombatEvent.start((Player) this, attacker)) {
				return;
			}
		}

		if (pvmMeleeEvent != null) {
			resetCombatEvent();
		}

		if (this.isNpc()) {
			((Npc) this).produceUnderAttack();
		}

		resetPath();
		resetRange();

			setOpponent(attacker);
		setCombatTimer();

		// Set hostility state for counter-combat
		setHostile(attacker, HostileState.HostilityType.ATTACKED);

			pvmMeleeEvent = new PvmMeleeEvent(getWorld(), this, attacker);
			getWorld().getServer().getGameEventHandler().addOrUpdate(pvmMeleeEvent);
	}

	public void resetCombatEvent() {
		if (combatEvent != null) {
			combatEvent.resetCombat();
		}
		if (pvmMeleeEvent != null) {
			pvmMeleeEvent.resetCombat(false);
		}
		// Clear hostility when combat ends
		clearHostility();
	}

	private void clearPassivePvmVictimState(final Player playerVictim) {
		playerVictim.setOpponent(null);
		playerVictim.setCombatEvent(null);
		playerVictim.setPvmMeleeEvent(null);
		playerVictim.setWalkToAction(null);
		if (playerVictim.isFollowing()) {
			playerVictim.resetFollowing();
		}
		if (playerVictim.getInteractingNpc() != null) {
			Npc interactingNpc = playerVictim.getInteractingNpc();
			if (interactingNpc.getInteractingPlayer() == playerVictim) {
				interactingNpc.setInteractingPlayer(null);
				interactingNpc.setNpcInteraction(null);
			}
			playerVictim.setInteractingNpc(null);
			playerVictim.setNpcInteraction(null);
		}
		if (playerVictim.getSprite() > 7) {
			playerVictim.setSprite(4);
			playerVictim.face(playerVictim.getX(), playerVictim.getY() - 1);
		}
	}

	public boolean checkAttack(final Mob mob, final boolean missile) {
		if (mob.isPlayer()) {
			/*if (victim.inCombat() && victim.getDuel().isDuelActive()) {
				Mob opponent = (Mob) getOpponent();
				if (opponent != null && victim.equals(opponent)) {
					return true;
				}
			}*/
			if (!missile) {
				if (!((Player)mob).canBeReattacked()) {
					return false;
				}
			}

			int myWildLvl = getLocation().wildernessLevel();
			int victimWildLvl = mob.getLocation().wildernessLevel();
			if (myWildLvl < 1 || victimWildLvl < 1) {
				//message("You can't attack other players here. Move to the wilderness");
				return false;
			}
			int combDiff = Math.abs(getCombatLevel() - mob.getCombatLevel());
			if (combDiff > myWildLvl) {
				//message("You can only attack players within " + (myWildLvl) + " levels of your own here");
				//message("Move further into the wilderness for less restrictions");
				return false;
			}
			if (combDiff > victimWildLvl) {
				//message("You can only attack players within " + (victimWildLvl) + " levels of your own here");
				//message("Move further into the wilderness for less restrictions");
				return false;
			}

			final Player victim = (Player) mob;
			if (victim.isInvulnerableTo(this) || victim.isInvisibleTo(this)) {
				victim.message("You are not allowed to attack that person");
				return false;
			}
			return true;
		} else if (mob.isNpc()) {
			Npc victim = (Npc) mob;
			if (!victim.getDef().isAttackable()) {
				return false;
			}
			return true;
		}
		return true;
	}

	public void resetRange() {
		if (rangeEventNpc != null) {
			rangeEventNpc.stop();
			rangeEventNpc = null;
		}
	}

	public void setRangeEventNpc(RangeEventNpc event) {
		if (rangeEventNpc != null) {
			rangeEventNpc.stop();
		}
		rangeEventNpc = event;
		getWorld().getServer().getGameEventHandler().add(rangeEventNpc);
	}

	/**
	 * GAME LOGIC
	 */
	public void cure() {
		curePoison();
		extinguish();
	}

	public void curePoison() {
			final Mob me = this;
			final PoisonEvent poisonEvent = getAttribute("poisonEvent", null);
			if (poisonEvent != null) {
				poisonEvent.stop();
			removeAttribute("poisonEvent");
			if (me.isPlayer()) {
				if (((Player) me).getCache().hasKey("poisoned")) {
					((Player) me).getCache().remove("poisoned");
				}
				if (((Player) me).getCache().hasKey("poisoned_max")) {
					((Player) me).getCache().remove("poisoned_max");
				}
			}
		}
		poisonMaxPower = 0;
		setPoisonDamage(0);
	}

	public void extinguish() {
		final Mob me = this;
		final BurnEvent burnEvent = getAttribute("burnEvent", null);
		if (burnEvent != null) {
			burnEvent.stop();
			removeAttribute("burnEvent");
		}
		burnDamage = 0;
		burnPulseCount = 0;
		if (me.isPlayer()) {
			Player player = (Player) me;
			player.getCache().remove("burn_damage");
			player.getCache().remove("burn_pulses");
		}
	}

	public void clearWaterSlow() {
		final WaterSlowEvent waterSlowEvent = getAttribute("waterSlowEvent", null);
		if (waterSlowEvent != null) {
			waterSlowEvent.stop();
			removeAttribute("waterSlowEvent");
		}
		waterSlowPercent = 0;
	}

	public void applyWaterSlow(int percent) {
		if (percent <= 0) {
			return;
		}
		final WaterSlowEvent existing = getAttribute("waterSlowEvent", null);
		if (existing != null) {
			existing.stop();
			removeAttribute("waterSlowEvent");
		}
		waterSlowPercent = Math.max(waterSlowPercent, percent);
		final WaterSlowEvent waterSlowEvent = new WaterSlowEvent(getWorld(), this);
		setAttribute("waterSlowEvent", waterSlowEvent);
		getWorld().getServer().getGameEventHandler().add(waterSlowEvent);
		if (isPlayer()) {
			((Player) this).message("@cya@You feel your combat pace slow under the water magic.");
		}
	}

	public void clearWindDebuff() {
		windLowRollBiasPercent = 0;
		windDebuffAttacksRemaining = 0;
	}

	public void clearSmokeAccuracyDebuff() {
		smokeAccuracyDebuffPercent = 0;
		smokeAccuracyDebuffAttacksRemaining = 0;
	}

	public void clearBearIntimidateDebuff() {
		bearIntimidatePercent = 0;
		bearIntimidateAttacksRemaining = 0;
	}

	public void clearOgreStaggerDebuff() {
		ogreStaggerAttacksRemaining = 0;
	}

	public void clearStartleDebuff() {
		startleDebuffAttacksRemaining = 0;
		startleSource = null;
	}

	public void clearFrostbiteDebuff() {
		frostbiteDebuffAttacksRemaining = 0;
		frostbiteSource = null;
	}

	public void applyWindDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		windLowRollBiasPercent = Math.max(windLowRollBiasPercent, percent);
		windDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@whi@Unsteady throws off your aim.");
		}
	}

	public void applyBearIntimidateDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		bearIntimidatePercent = Math.max(bearIntimidatePercent, percent);
		bearIntimidateAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@dre@The bear-hide aura throws off your combat pace.");
		}
	}

	public void applyOgreStaggerDebuff() {
		ogreStaggerAttacksRemaining = 1;
		if (isPlayer()) {
			((Player) this).message("@yel@A staggering blow leaves your next strike dead in your hands.");
		}
	}

	public void applyStartleDebuff(Mob source) {
		startleDebuffAttacksRemaining = 1;
		startleSource = source;
		if (isPlayer()) {
			((Player) this).message("@yel@Startle makes your next attack falter.");
		}
	}

	public void applyFrostbiteDebuff(Mob source) {
		frostbiteDebuffAttacksRemaining = 1;
		frostbiteSource = source;
		if (isPlayer()) {
			((Player) this).message("@cya@Frostbite turns your next attack against you.");
		}
	}

	public boolean consumeOgreStaggerDebuff() {
		if (ogreStaggerAttacksRemaining <= 0) {
			return false;
		}
		clearOgreStaggerDebuff();
		if (isPlayer()) {
			((Player) this).message("@yel@You fail to recover your next strike.");
		}
		return true;
	}

	public boolean consumeStartleDebuff() {
		if (startleDebuffAttacksRemaining <= 0) {
			return false;
		}
		clearStartleDebuff();
		if (isPlayer()) {
			((Player) this).message("@yel@Startle stops your attack.");
		}
		return true;
	}

	public Mob consumeFrostbiteSource() {
		if (frostbiteDebuffAttacksRemaining <= 0) {
			return null;
		}
		final Mob source = frostbiteSource;
		clearFrostbiteDebuff();
		if (isPlayer()) {
			((Player) this).message("@cya@Frostbite reflects part of your attack.");
		}
		return source;
	}

	public void applySmokeAccuracyDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		smokeAccuracyDebuffPercent = Math.max(smokeAccuracyDebuffPercent, percent);
		smokeAccuracyDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@bla@Smoke stings your eyes and throws off your aim.");
		}
	}

	public void clearWaterMaxHitDebuff() {
		waterMaxHitDebuffPercent = 0;
		waterMaxHitDebuffAttacksRemaining = 0;
	}

	public void clearDragonWaterMaxHitDebuff() {
		dragonWaterMaxHitDebuffPercent = 0;
		dragonWaterMaxHitDebuffAttacksRemaining = 0;
	}

	public void applyWaterMaxHitDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		waterMaxHitDebuffPercent = Math.max(waterMaxHitDebuffPercent, percent);
		waterMaxHitDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@cya@Dampen lowers your striking power.");
		}
	}

	public void applyDragonWaterMaxHitDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		dragonWaterMaxHitDebuffPercent = Math.max(dragonWaterMaxHitDebuffPercent, percent);
		dragonWaterMaxHitDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@cya@Icy dragon breath dampens your striking power.");
		}
	}

	public void clearEarthAttackSpeedDebuff() {
		earthAttackSpeedDebuffPercent = 0;
		earthAttackSpeedDebuffAttacksRemaining = 0;
	}

	public void clearDragonEarthAttackSpeedDebuff() {
		dragonEarthAttackSpeedDebuffPercent = 0;
		dragonEarthAttackSpeedDebuffAttacksRemaining = 0;
	}

	public void applyEarthAttackSpeedDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		earthAttackSpeedDebuffPercent = Math.max(earthAttackSpeedDebuffPercent, percent);
		earthAttackSpeedDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@gre@Slow drags at your combat pace.");
		}
	}

	public void applyDragonEarthAttackSpeedDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		dragonEarthAttackSpeedDebuffPercent = Math.max(dragonEarthAttackSpeedDebuffPercent, percent);
		dragonEarthAttackSpeedDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@gre@Earth dragon breath slows your combat pace.");
		}
	}

	public void clearFireDefenseDebuff() {
		fireDefenseDebuffPercent = 0;
		fireDefenseDebuffAttacksRemaining = 0;
	}

	public void clearDragonFireDefenseDebuff() {
		dragonFireDefenseDebuffPercent = 0;
		dragonFireDefenseDebuffAttacksRemaining = 0;
	}

	public void clearInfernalFireDefenseDebuff() {
		infernalFireDefenseDebuffPercent = 0;
		infernalFireDefenseDebuffAttacksRemaining = 0;
	}

	public void applyFireDefenseDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		fireDefenseDebuffPercent = Math.max(fireDefenseDebuffPercent, percent);
		fireDefenseDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@red@Scorch leaves your defenses exposed.");
		}
	}

	public void applyDragonFireDefenseDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		dragonFireDefenseDebuffPercent = Math.max(dragonFireDefenseDebuffPercent, percent);
		dragonFireDefenseDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@red@Dragon fire weakens your defenses.");
		}
	}

	public void applyInfernalFireDefenseDebuff(int percent) {
		if (percent <= 0) {
			return;
		}
		infernalFireDefenseDebuffPercent = Math.max(infernalFireDefenseDebuffPercent, percent);
		infernalFireDefenseDebuffAttacksRemaining = ATTACK_BASED_DEBUFF_ATTACKS;
		if (isPlayer()) {
			((Player) this).message("@red@Infernal flames weaken your defenses.");
		}
	}

	public void damage(final int damage) {
		damage(damage, HitSplat.TYPE_STANDARD);
	}

	public void damage(final int damage, final int hitSplatType) {
		if (damage > 0 && this.isPlayer()) {
			((Player) this).setAttribute("last_damage_taken_at", System.currentTimeMillis());
		}
		final int newHp = skills.getLevel(Skill.HITS.id()) - damage;
		if (newHp <= 0) {
			if (this.isPlayer()) {
				killedBy(combatWith);
			} else {
				killedBy(combatWith);
			}
		} else {
			skills.setLevel(Skill.HITS.id(), newHp);
		}
		if (this.isPlayer()) {
			Player player = (Player) this;
			ActionSender.sendStat(player, Skill.HITS.id());
		}
		getUpdateFlags().setDamage(new Damage(this, damage));
		getUpdateFlags().addHitSplat(new HitSplat(this, hitSplatType, damage));
	}

	public void startPoisonEvent() {
			final PoisonEvent existingPoisonEvent = getAttribute("poisonEvent", null);
			if (existingPoisonEvent != null) {
				existingPoisonEvent.setPoisonPower(getPoisonDamage());
				return;
			}
		final PoisonEvent poisonEvent = new PoisonEvent(getWorld(), this, getPoisonDamage());
			setAttribute("poisonEvent", poisonEvent);
			getWorld().getServer().getGameEventHandler().add(poisonEvent);
		}

	public void startBurnEvent() {
		if (getAttribute("burnEvent", null) != null) {
			extinguish();
		}
		final BurnEvent burnEvent = new BurnEvent(getWorld(), this, getBurnDamage(), getBurnPulseCount());
		setAttribute("burnEvent", burnEvent);
		getWorld().getServer().getGameEventHandler().add(burnEvent);
	}

	public void applyBurn(int burnDamage, int burnPulses) {
		setBurnDamage(burnDamage);
		setBurnPulseCount(burnPulses);
		startBurnEvent();
	}

	public void consumeAttackBasedDebuffs() {
		if (windDebuffAttacksRemaining > 0 && --windDebuffAttacksRemaining <= 0) {
			clearWindDebuff();
			if (isPlayer()) {
				((Player) this).message("@whi@Unsteady fades.");
			}
		}
		if (bearIntimidateAttacksRemaining > 0 && --bearIntimidateAttacksRemaining <= 0) {
			clearBearIntimidateDebuff();
			if (isPlayer()) {
				((Player) this).message("@dre@You steady your pace as the intimidation fades.");
			}
		}
		if (smokeAccuracyDebuffAttacksRemaining > 0 && --smokeAccuracyDebuffAttacksRemaining <= 0) {
			clearSmokeAccuracyDebuff();
			if (isPlayer()) {
				((Player) this).message("@bla@The smoke clears from your eyes.");
			}
		}
		if (waterMaxHitDebuffAttacksRemaining > 0 && --waterMaxHitDebuffAttacksRemaining <= 0) {
			clearWaterMaxHitDebuff();
			if (isPlayer()) {
				((Player) this).message("@cya@Dampen fades.");
			}
		}
		if (dragonWaterMaxHitDebuffAttacksRemaining > 0 && --dragonWaterMaxHitDebuffAttacksRemaining <= 0) {
			clearDragonWaterMaxHitDebuff();
			if (isPlayer()) {
				((Player) this).message("@cya@The icy dragon breath stops damping your striking power.");
			}
		}
		if (earthAttackSpeedDebuffAttacksRemaining > 0 && --earthAttackSpeedDebuffAttacksRemaining <= 0) {
			clearEarthAttackSpeedDebuff();
			if (isPlayer()) {
				((Player) this).message("@gre@Slow fades.");
			}
		}
		if (dragonEarthAttackSpeedDebuffAttacksRemaining > 0 && --dragonEarthAttackSpeedDebuffAttacksRemaining <= 0) {
			clearDragonEarthAttackSpeedDebuff();
			if (isPlayer()) {
				((Player) this).message("@gre@The earth dragon breath stops slowing your combat pace.");
			}
		}
		if (fireDefenseDebuffAttacksRemaining > 0 && --fireDefenseDebuffAttacksRemaining <= 0) {
			clearFireDefenseDebuff();
			if (isPlayer()) {
				((Player) this).message("@red@Scorch fades.");
			}
		}
		if (dragonFireDefenseDebuffAttacksRemaining > 0 && --dragonFireDefenseDebuffAttacksRemaining <= 0) {
			clearDragonFireDefenseDebuff();
			if (isPlayer()) {
				((Player) this).message("@red@The dragon fire stops weakening your defenses.");
			}
		}
		if (infernalFireDefenseDebuffAttacksRemaining > 0 && --infernalFireDefenseDebuffAttacksRemaining <= 0) {
			clearInfernalFireDefenseDebuff();
			if (isPlayer()) {
				((Player) this).message("@red@The infernal flames stop weakening your defenses.");
			}
		}
	}

	public void applyPoison(final int appliedPoisonPower, final int maxPoisonPower) {
		if (appliedPoisonPower <= 0 || maxPoisonPower <= 0) {
			return;
		}
			final int nextMaxPower = Math.max(getPoisonMaxPower(), maxPoisonPower);
			setPoisonMaxPower(nextMaxPower);
			setPoisonDamage(Math.min(nextMaxPower, getCurrentPoisonPower() + appliedPoisonPower));
			startPoisonEvent();
		}

	public void applyPoison(final int poisonPower) {
		applyPoison(poisonPower, poisonPower);
	}

	// part of NPC poison feature
	public int getCurrentPoisonPower() {
		final PoisonEvent poisonEvent = getAttribute("poisonEvent", null);
		if (poisonEvent == null) {
			return 0;
		} else {
			return poisonEvent.getPoisonPower();
		}
	}

	public int getPoisonMaxPower() {
		return poisonMaxPower;
	}

	public void setPoisonMaxPower(final int poisonMaxPower) {
		this.poisonMaxPower = Math.max(0, poisonMaxPower);
		if (isPlayer()) {
			Player player = (Player) this;
			player.getCache().store("poisoned_max", this.poisonMaxPower);
		}
	}

	private String describePoisonDebugTarget() {
		if (isPlayer()) {
			return "player:" + ((Player) this).getUsername();
		}
		if (isNpc()) {
			Npc npc = (Npc) this;
			return "npc:" + npc.getID() + ":\"" + npc.getDef().getName() + "\"";
		}
		return "mob";
	}

	/**
	 * Resets the update related flags
	 */
	public void resetAfterUpdate() {
		setHasMoved(false);
		resetSpriteChanged();
		getUpdateFlags().reset();
		setTeleporting(false);
	}

	/**
	 * SETTERS/GETTERS
	 */
	public int getLevel(int skillID) {
		return skills.getLevel(skillID);
	}

	public boolean finishedPath() {
		return getWalkingQueue().finished();
	}

	public RangeEventNpc getRangeEventNpc() {
		return rangeEventNpc;
	}

	public UUID getUUID() {
		return uuid;
	}

	public CombatEvent getCombatEvent() {
		return combatEvent;
	}

	public PvmMeleeEvent getPvmMeleeEvent() {
		return pvmMeleeEvent;
	}

	public void setCombatEvent(final CombatEvent combatEvent2) {
		this.combatEvent = combatEvent2;
		flagToolCombatAppearanceUpdate();
	}

	public void setPvmMeleeEvent(final PvmMeleeEvent pvmMeleeEvent) {
		this.pvmMeleeEvent = pvmMeleeEvent;
		flagToolCombatAppearanceUpdate();
	}

	public CombatState getCombatState() {
		return lastCombatState;
	}

	public void setCombatStyle(final int style) {
		combatStyle = style;
	}

	public long getCombatTimer() {
		return combatTimer;
	}

	public void setCombatTimer(final int delay) {
		combatTimer = System.currentTimeMillis() + delay;
	}

	public void setCombatTimer() {
		combatTimer = System.currentTimeMillis();
	}

	public GameTickEvent getFollowEvent() {
		return followEvent;
	}

	public Mob getFollowing() {
		return following;
	}

	public Mob getPossessing() {
		return possessing;
	}

	public boolean isLain() {
		return LAINEvent != null;
	}

	public int getHitsMade() {
		return hitsMade;
	}

	public void setHitsMade(final int i) {
		hitsMade = i;
	}

	public long getLastMoved() {
		return lastMovement;
	}

	public Mob getOpponent() {
		return combatWith;
	}

	public void setOpponent(final Mob opponent) {
		combatWith = opponent;
		flagToolCombatAppearanceUpdate();
	}

	private void flagToolCombatAppearanceUpdate() {
		if (isPlayer()) {
			((Player) this).flagToolCombatAppearanceChanged();
		}
	}

	public Mob getLastOpponent() {
		return lastCombatWith;
	}

	public void setLastOpponent(final Mob opponent) {
		lastCombatWith = opponent;
	}

	public int getSprite() {
		return mobSprite;
	}

	public void setSprite(final int x) {
		if (mobSprite != x) {
			setSpriteChanged();
			mobSprite = x;
		}
	}

	public StatRestorationEvent getStatRestorationEvent() {
		return statRestorationEvent;
	}

	public void tryResyncStatEvent() {
		statRestorationEvent.tryResyncStat();
	}

	public void tryResyncHitEvent() {
		statRestorationEvent.tryResyncHit();
	}

	public UpdateFlags getUpdateFlags() {
		return updateFlags;
	}

	public synchronized ViewArea getViewArea() {
		return viewArea;
	}

	public WalkingQueue getWalkingQueue() {
		return walkingQueue;
	}

	public boolean hasMoved() {
		return hasMoved;
	}

	public long getRanAwayTimer() {
		return ranAwayTimer;
	}

	public void incHitsMade() {
		hitsMade++;
	}

	public boolean inCombat() {
		return mobSprite >= 8 && mobSprite <= 15 && combatWith != null;
	}

	/**
	 * New simplified hostility methods replacing complex combat states
	 */
	public boolean isHostile() {
		return hostileState.isHostile();
	}

	public boolean isHostileToward(Mob target) {
		return hostileState.isHostileToward(target);
	}

	public Mob getHostileTarget() {
		return hostileState.getTarget();
	}

	public HostileState.HostilityType getHostilityType() {
		return hostileState.getHostilityType();
	}

	public void setHostile(Mob target, HostileState.HostilityType type) {
		hostileState.setHostile(target, type);
	}

	public void clearHostility() {
		hostileState.clearHostility();
	}

	public boolean hasHostilityExpired(long timeoutMs) {
		return hostileState.hasHostilityExpired(timeoutMs);
	}

	public synchronized boolean isBusy() {
		return busy.get();
	}

	public synchronized void setBusy(final boolean busy) {
		this.busy.set(busy);
	}

	public boolean isFollowing() {
		return followEvent != null && following != null;
	}

	public void setFollowing(Mob mob) {
		setFollowing(mob, 1);
	}

	public abstract void killedBy(Mob mob);

	public void resetPath() {
		getWalkingQueue().reset();
	}

	public void resetSpriteChanged() {
		spriteChanged = false;
	}

	public void setLastCombatState(final CombatState lastCombatState) {
		this.lastCombatState = lastCombatState;
	}

	private void setLastMoved() {
		lastMovement = System.currentTimeMillis();
	}

	public void setHasMoved(boolean moved) {
		this.hasMoved = moved;
	}

	public void setRanAwayTimer() {
		this.ranAwayTimer = getWorld().getServer().getCurrentTick();
	}

	public void resetRanAwayTimer() {
		this.ranAwayTimer = 0;
	}

	public void setLocation(final Point point) {
		setLocation(point, false);
	}

	public void setWarnedToMove(final boolean moved) {
		warnedToMove = moved;
	}

	private void setSpriteChanged() {
		spriteChanged = true;
	}

	public void setUpdateRequests(final UpdateFlags updateRequests) {
		this.updateFlags = updateRequests;
	}

	public boolean spriteChanged() {
		return spriteChanged;
	}

	public boolean warnedToMove() {
		return warnedToMove;
	}

	public Skills getSkills() {
		return skills;
	}

	public int getCombatLevel(final boolean isSpecial) {
		return getSkills().getCombatLevel(this, isSpecial);
	}

	public int getCombatLevel() {
		return getSkills().getCombatLevel(this, false);
	}

	public boolean isTeleporting() {
		return teleporting;
	}

	public void setTeleporting(final boolean teleporting) {
		this.teleporting = teleporting;
	}

	public boolean isUnregistering() {
		return unregistering;
	}

	public void setUnregistering(final boolean unregistering) {
		this.unregistering = unregistering;
	}

	public KillType getKillType() {
		return killType;
	}

	public void setKillType(KillType killType) {
		this.killType = killType;
	}

	public int getPoisonDamage() {
		return poisonDamage;
	}

	public void setPoisonDamage(int poisonDamage) {
		this.poisonDamage = poisonDamage;
	}

	public int getBurnDamage() {
		return burnDamage;
	}

	public void setBurnDamage(int burnDamage) {
		this.burnDamage = burnDamage;
	}

	public int getBurnPulseCount() {
		return burnPulseCount;
	}

	public void setBurnPulseCount(int burnPulseCount) {
		this.burnPulseCount = burnPulseCount;
	}

	public int getWaterSlowPercent() {
		return waterSlowPercent;
	}

	public int getWindLowRollBiasPercent() {
		int total = windLowRollBiasPercent + smokeAccuracyDebuffPercent;
		if (isPlayer()) {
			return (int) Math.floor(total * ((Player) this).getMindRobeDebuffMultiplier());
		}
		return total;
	}

	public double getWindLowRollBiasChance() {
		return Math.max(0.0D, getWindLowRollBiasPercent() / 100.0D);
	}

	public int getWaterMaxHitDebuffPercent() {
		final int total = waterMaxHitDebuffPercent + dragonWaterMaxHitDebuffPercent;
		if (isPlayer()) {
			return (int) Math.floor(total * ((Player) this).getMindRobeDebuffMultiplier());
		}
		return total;
	}

	public double getWaterMaxHitMultiplier() {
		return Math.max(0.0D, 1.0D - (getWaterMaxHitDebuffPercent() / 100.0D));
	}

	public int getEarthAttackSpeedDebuffPercent() {
		final int total = earthAttackSpeedDebuffPercent + dragonEarthAttackSpeedDebuffPercent + bearIntimidatePercent;
		if (isPlayer()) {
			return (int) Math.floor(total * ((Player) this).getMindRobeDebuffMultiplier());
		}
		return total;
	}

	public int getFireDefenseDebuffPercent() {
		final int total = fireDefenseDebuffPercent + dragonFireDefenseDebuffPercent + infernalFireDefenseDebuffPercent;
		if (isPlayer()) {
			return (int) Math.floor(total * ((Player) this).getMindRobeDebuffMultiplier());
		}
		return total;
	}

	protected int applyFireDefenseDebuffToValue(int baseDefense) {
		final int fireDebuffPercent = getFireDefenseDebuffPercent();
		if (baseDefense <= 0 || fireDebuffPercent <= 0) {
			return baseDefense;
		}
		return Math.max(0, (int) Math.floor(baseDefense * (1.0D - (fireDebuffPercent / 100.0D))));
	}

	/**
	 * Function used to drop an item after walking completes.
	 */
	protected Item dropItemEvent = null;
	protected int dropItemIndex = -1;

	public void setDropItemEvent(int index, Item item) {
		this.dropItemIndex = index;
		this.dropItemEvent = item;
	}

	public Item getDropItemEvent() {
		return this.dropItemEvent;
	}

	public void runDropEvent(boolean fromInventory) {
		// TODO: Allow npcs to use this code for drop parties?
		if (!this.isPlayer()) return; // We can only run Plugins on Players.
		final Player player = (Player) this;
		final Item item = player.getDropItemEvent();
		final int index = dropItemIndex;
		this.setDropItemEvent(-1, null);
		if (item == null) return;
		getWorld().getServer().getPluginHandler().handlePlugin(DropObjTrigger.class, player, new Object[]{player, index, item, fromInventory});
	}

	protected Player talkToNpcEvent = null;

	public void setTalkToNpcEvent(Player player) {
		this.talkToNpcEvent = player;
	}

	public Player getTalkToNpcEvent() {
		return this.talkToNpcEvent;
	}

	public void runTalkToNpcEvent() {
		Player player = getTalkToNpcEvent();
		setTalkToNpcEvent(null);
		player.getWorld().getServer().getPluginHandler().handlePlugin(TalkNpcTrigger.class, player, new Object[]{player, this});
	}

	public boolean canProjectileReach(final Mob mob) {
		if (this.isNpc()) {
			return this.withinRange(mob, DEFAULT_PROJECTILE_RADIUS);
		}

		Player player = (Player) this;
		int radius = player.getProjectileRadius();
		return player.withinRange(mob, radius);
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Mob) {
			return ((Mob)obj).getUUID().equals(uuid);
		}
		return false;
	}

	/* TODO: implement hashCode
	@Override
	public int hashCode() {
		return Objects.hash(uuid);
	}
	*/
	
	public void setNpcInteraction(NpcInteraction interaction) {
		this.npcInteraction = interaction;
	}

	public NpcInteraction getNpcInteraction() {
		return npcInteraction;
	}

	public void setEndFollowRadius(int endFollowRadius) {
		this.endFollowRadius = endFollowRadius;
	}

	public int getEndFollowRadius() {
		return endFollowRadius;
	}

}
