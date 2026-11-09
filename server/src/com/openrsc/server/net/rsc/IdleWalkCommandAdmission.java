package com.openrsc.server.net.rsc;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** GameThread-owned, bounded intake for ordinary idle ground walking only. */
public final class IdleWalkCommandAdmission {
	public static final int PER_PLAYER_POLL = 2;
	public static final int PER_PLAYER_TICK = 8;
	public static final int GLOBAL_PER_POLL = 8;
	public static final long SOFT_BUDGET_NANOS = 2_000_000L;
	private final LongSupplier budgetClock;
	private final Consumer<Player> visitor = this::visit;
	private int nextIndex;
	private int remaining;
	private int startIndex;
	private long pollStartedNanos;
	private boolean upperPass;
	private boolean timeExhausted;

	public IdleWalkCommandAdmission() {
		this(System::nanoTime);
	}

	/** Deterministic budget-clock seam; gameplay clocks and cadence are independent. */
	IdleWalkCommandAdmission(final LongSupplier budgetClock) {
		this.budgetClock = Objects.requireNonNull(budgetClock, "budgetClock");
	}

	/** Rotates admission priority without changing authoritative movement order. */
	public int poll(final World world) {
		if (!world.getServer().getConfig().WANT_MYWORLD
			|| !world.getServer().getConfig().WANT_CUSTOM_WALK_SPEED
			|| world.getPlayers().count() == 0) return 0;
		remaining = GLOBAL_PER_POLL;
		timeExhausted = false;
		startIndex = nextIndex;
		pollStartedNanos = budgetClock.getAsLong();
		// Cached visitor and two live passes: no per-poll snapshots, sorting or callbacks allocated.
		upperPass = true;
		world.getPlayers().forEachLive(visitor);
		upperPass = false;
		world.getPlayers().forEachLive(visitor);
		return GLOBAL_PER_POLL - remaining;
	}

	private void visit(final Player player) {
		if (upperPass ? player.getIndex() >= startIndex : player.getIndex() < startIndex) admit(player);
	}

	private void admit(final Player player) {
		if (remaining <= 0 || timeExhausted) return;
		for (int allowance = 0; allowance < PER_PLAYER_POLL && remaining > 0; allowance++) {
			// The first eligible command always progresses, even after a slow scan or command.
			// A single command may exceed this soft budget; never interrupt a handler midway.
			if (remaining < GLOBAL_PER_POLL
				&& budgetClock.getAsLong() - pollStartedNanos >= SOFT_BUDGET_NANOS) {
				timeExhausted = true;
				return;
			}
			int consumed = player.processIdleWalkCommands(1);
			if (consumed == 0) return;
			remaining -= consumed;
			nextIndex = player.getIndex() + 1;
		}
	}
}
