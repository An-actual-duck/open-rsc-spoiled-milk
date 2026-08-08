package com.openrsc.server.login;

import com.openrsc.server.Server;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Used to verify save players on the Login thread
 */
public class PlayerSaveRequest extends LoginExecutorProcess {
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final Server server;
	private final Player player;
	private final boolean logout;
	private final long queuedAtNanos;

	public PlayerSaveRequest(final Server server, final Player player, boolean logout) {
		this.server = server;
		this.player = player;
		this.logout = logout;
		this.queuedAtNanos = System.nanoTime();
	}

	public final Player getPlayer() {
		return player;
	}

	public final Server getServer() {
		return server;
	}

	protected void processInternal() {
//		LOGGER.info("Saved player " + player.getUsername() + "");
		final long processStartedAtNanos = System.nanoTime();
		try {
			boolean success = getServer().getPlayerService().savePlayer(player);
			if (success && this.logout) {
				logoutSaveSuccess();
			} else if (this.logout) {
				LOGGER.error("Logout save failed for {}; retaining the live session for retry.",
					player.getUsername());
			}
		} catch (final RuntimeException ex) {
			LOGGER.error("Error saving player; retaining the live session for retry.", ex);
		} finally {
			// A failed save must not remove a live player or their target-owned
			// effects. Clear only request state so a later autosave/logout can retry.
			getPlayer().setSaving(false);
			if (this.logout) {
				getPlayer().setLoggingOut(false);
			}
			getServer().recordPlayerSaveTiming(this.logout,
				processStartedAtNanos - queuedAtNanos,
				System.nanoTime() - processStartedAtNanos);
		}
	}

	public void logoutSaveSuccess() {
		getServer().getGameEventHandler().getPlayerEvents(getPlayer()).forEach(GameTickEvent::stop);

		getServer().getPacketFilter().removeLoggedInPlayer(getPlayer().getCurrentIP(), getPlayer().getUsernameHash());

		getPlayer().remove(); // remove player from region
		getServer().getWorld().getPlayers().remove(getPlayer()); // remove player from player list
		getServer().getWorld().removePlayer(getPlayer().getUsernameHash()); // remove player by hash in case they were not found in region
		getPlayer().setLoggedIn(false);

		LOGGER.info("Removed player " + getPlayer().getUsername());

		updateFriendsLists();
	}

	private void updateFriendsLists() {
		final World world = getPlayer().getWorld();
		for (Player other : world.getPlayers()) {
			other.getSocial().alertOfLogout(getPlayer());
		}

		world.getClanManager().checkAndUnattachFromClan(getPlayer());
		world.getPartyManager().checkAndUnattachFromParty(getPlayer());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PlayerSaveRequest request = (PlayerSaveRequest) o;
		//We need to check both logout and player in both equals and hashCode because otherwise logouts may not start (like if there is already an auto-save happening on the same tick)
		return logout == request.logout && Objects.equals(player, request.player);
	}

	@Override
	public int hashCode() {
		//We need to check both logout and player in both equals and hashCode because otherwise logouts may not start (like if there is already an auto-save happening on the same tick)
		return Objects.hash(player, logout);
	}
}
