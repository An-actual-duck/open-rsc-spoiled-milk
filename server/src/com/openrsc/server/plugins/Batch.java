package com.openrsc.server.plugins;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.NpcInteraction;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

public class Batch {

	private static final int MAX_BATCH_SIZE = 30;
	private static final int UNLIMITED_BATCH_SIZE = -1;
	private Player player;
	private int current;
	private int totalBatch;
	private boolean completed;
	private volatile boolean showingProductionProgress;
	private boolean productionStepAnnounced;
	private Point location;

	/**
	 * Creates a new instance of a Batch bar.
	 * @param player The player the bar belongs to
	 */
	public Batch(Player player) {
		this.player = player;
		this.location = player.getLocation();
	}

	/**
	 * Creates a new batch bar. Call start() to send to client
	 * @param totalBatch The total repetitions of a task
	 */
	public void initialize(int totalBatch) {
		this.current = 0;
		this.totalBatch = Math.min(totalBatch, MAX_BATCH_SIZE);
		this.completed = false;
	}

	public void initializeUnlimited() {
		this.current = 0;
		this.totalBatch = UNLIMITED_BATCH_SIZE;
		this.completed = false;
	}

	public void start() {
		// Repeating actions use the actor-attached tool progress display.
	}

	public synchronized void startProductionProgress(int itemId) {
		if (totalBatch > 0) {
			showingProductionProgress = true;
			ActionSender.sendProductionProgressStart(player, itemId, totalBatch);
		}
	}

	synchronized void beginProductionStep(int ticks) {
		if (showingProductionProgress && !productionStepAnnounced) {
			productionStepAnnounced = true;
			ActionSender.sendProductionProgressStep(player, ticks * player.getConfig().GAME_TICK);
		}
	}

	public boolean isShowingProductionProgress() {
		return showingProductionProgress;
	}

	private synchronized void updateProductionProgress() {
		if (showingProductionProgress) {
			productionStepAnnounced = false;
			ActionSender.sendProductionProgressUpdate(player, current);
		}
	}

	private void finishProductionProgress() {
		if (showingProductionProgress) {
			showingProductionProgress = false;
			ActionSender.sendProductionProgressEnd(player);
		}
	}

	public synchronized void stop() {
		finishProductionProgress();
		ActionSender.sendRemoveActionProgressBar(getPlayer());
		this.completed = true;
	}

	/**
	 * Increments the current batch's progress by 1.
	 * @return Returns false when the batch is complete
	 */
	public void update() {
		int xDiff = Math.abs(this.location.getX() - getPlayer().getLocation().getX());
		int yDiff = Math.abs(this.location.getY() - getPlayer().getLocation().getY());
		/*
		Because some actions (like thieving) can take place one extra tile away from their target before the player gets close,
		we will give them one tile worth of wiggle room on the first increment before we cancel their batch.
		*/
		if (getPlayer().getNpcInteraction() == NpcInteraction.NPC_OP && current == 0 && xDiff <= 1 && yDiff <= 1) {
			this.location = getPlayer().getLocation();
		}
		if (!getPlayer().getLocation().equals(this.location)) {
			stop();
			return;
		}
		incrementBatch();
		updateProductionProgress();
		if (!isUnlimited() && getCurrentBatchProgress() == getTotalBatch()) {
			stop();
		}
	}

	public Player getPlayer() { return player; }
	private int getTotalBatch() { return totalBatch; }
	private void incrementBatch() { current++; }
	private int getCurrentBatchProgress() { return current; }
	private boolean isUnlimited() { return totalBatch == UNLIMITED_BATCH_SIZE; }
	public boolean isFirstInBatch() { return current == 0; }
	public boolean isShowingBar() { return false; }
	public boolean isComplete() { return completed; }

	public void setLocation(Point location) {
		this.location = location;
	}
}
