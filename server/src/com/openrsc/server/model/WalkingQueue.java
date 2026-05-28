package com.openrsc.server.model;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.region.Region;

/**
 * A WalkingQueue stores steps the client needs to walk and allows
 * this queue of steps to be modified.
 * The class will also process these steps when processNextMovement()
 * is called. This should be called once per server cycle.
 */
public class WalkingQueue {
	private boolean DEBUG = false;
	private Mob mob;

	public Path path;
	public boolean playerWasWalking;

	public WalkingQueue(Mob entity) {
		this.mob = entity;
	}

   /**
    * Handles logic to run when the player finishes walking. A player has finished walking if they 
	* were walking and then their path becomes null or empty.
    */ 
	private void handlePlayerFinishedWalking() {
		if (playerWasWalking) {
			Player currentPlayer = mob.isPlayer() ? (Player)mob : null;

			// Only track finished walking status of players.
			if (currentPlayer != null && !currentPlayer.isBusy()) {
				Point targetTile = currentPlayer.getLastTileClicked();

				if (targetTile != null) {
					Region region = currentPlayer.getWorld().getRegionManager().getRegion(targetTile);

					// Target would be the other player currentPlayer clicked on.
					Player target = region.getPlayer(targetTile.getX(), targetTile.getY(), currentPlayer, false);

					if (target != null && target != currentPlayer) {
						// Is current player within 1 tile of target?
						boolean targetWithinOneTile = currentPlayer.withinRange(targetTile, 1);

						// Face the other player. This will have no effect if player_blocking config is disabled.
						if (targetWithinOneTile) {
							currentPlayer.face(target);
						}
					}
					
					// Reset lastTileClicked so the player doesn't re-face the last player they clicked on.
					currentPlayer.setLastTileClicked(null);
				}
			}
		}

		playerWasWalking = false;
	}

	/**
	 * Processes the next player's movement.
	 */
	public void processNextMovement() {
		if (path == null) {
			handlePlayerFinishedWalking();
			return;
		} else if (path.isEmpty()) {
			handlePlayerFinishedWalking();
			reset();
			return;
		}

		// Player is walking if path is not null or empty.
		playerWasWalking = true;

		Point walkPoint = path.poll();

		if (mob.getAttribute("blink", false)) {
			if (path.size() >= 1) {
				walkPoint = path.getLastPoint();
				((Player) mob).teleport(walkPoint.getX(), walkPoint.getY(), false);
			}
			return;
		}

		int destX = walkPoint.getX();
		int destY = walkPoint.getY();
		int startX = mob.getX();
		int startY = mob.getY();
		if (!PathValidation.checkAdjacent(mob, startX, startY, destX, destY)) {
			reset();
			if (DEBUG && mob.isPlayer()) System.out.println("Failed adjacent check, not pathing.");
			return;
		}

		if (mob.isNpc()) {
			Npc npc = (Npc) mob;
			NPCLoc loc = npc.getLoc();
			Point currentPoint = Point.location(startX, startY);
			Point destinationPoint = Point.location(destX, destY);
			boolean summonedNpc = Summoning.isSummon(npc);
			boolean useRoamBounds = !summonedNpc && !npc.inCombat() && !npc.isChasing();
			boolean currentInBounds = useRoamBounds ? npc.inRoamBounds(currentPoint)
				: currentPoint.inBounds(loc.minX(), loc.minY(), loc.maxX(), loc.maxY());
			boolean destinationInBounds = useRoamBounds ? npc.inRoamBounds(destinationPoint)
				: destinationPoint.inBounds(loc.minX(), loc.minY(), loc.maxX(), loc.maxY());
			if (summonedNpc || destinationInBounds || (destX == 0 && destY == 0)) {
				mob.face(Point.location(destX, destY));
				mob.setLocation(Point.location(destX, destY));
			} else {
				reset();
			}
		}
		else {
			Player player = (Player) mob;
			player.face(Point.location(destX, destY));
			player.setLocation(Point.location(destX, destY));
			player.stepIncrementActivity();
		}

	}

	public Point getNextMovement() {
		if (path == null || path.isEmpty()) {
			return mob.getLocation();
		}
		Point destPoint = path.getNextPoint();
		Point curPoint = mob.getLocation();
		if (!PathValidation.checkAdjacent(mob, curPoint.getX(), curPoint.getY(), destPoint.getX(), destPoint.getY())) {
			return curPoint;
		} else {
			return destPoint;
		}
	}

	public void reset() {
		path = null;
		if (this.mob.isPlayer()) {
			if (this.mob.getDropItemEvent() != null) {
				this.mob.runDropEvent(true);
			}
		}
		if (this.mob.getTalkToNpcEvent() != null) {
			this.mob.runTalkToNpcEvent();
		}
	}

	public boolean finished() {
		return path == null || path.isEmpty();
	}

	public void setPath(Path path) {
		this.path = path;
	}
}
