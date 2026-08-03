package com.openrsc.server.content.worldedit;

import com.openrsc.server.model.entity.player.Group;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Applies Builder-only session state after an ordinary authenticated login. */
public final class WorldBuilderPlayerSession {
	private static final String BINDING_PENDING_ATTRIBUTE =
		"adaptive_world_builder_binding_pending";
	private static final String BINDING_COMPLETE_ATTRIBUTE =
		"adaptive_world_builder_binding_complete";
	private static final Logger LOGGER = LogManager.getLogger(WorldBuilderPlayerSession.class);

	private WorldBuilderPlayerSession() {
	}

	public static void activate(Player player) {
		if (!player.getConfig().WORLD_BUILDER_MODE) {
			return;
		}
		if (!WorldBuilderMode.isBuilderAccount(player.getUsername()) || player.getGroupID() != Group.ADMIN) {
			LOGGER.error("Refusing World Builder session for an unauthorized player identity");
			player.message(player.getConfig().MESSAGE_PREFIX + "World Builder authorization failed.");
			return;
		}
		player.setCacheInvulnerable(true);
		if (AdaptiveWorldBuilderRuntimeIdentity.isAdaptive(player.getConfig())) {
			WorldLocation initial =
				AdaptiveWorldBuilderRuntimeIdentity.initialLocation(player.getConfig());
			player.teleportLayered(initial, false);
			player.setAttribute(BINDING_PENDING_ATTRIBUTE, Boolean.TRUE);
			player.message(player.getConfig().MESSAGE_PREFIX
				+ "Verifying the isolated Builder runtime before authoring.");
			return;
		}
		WorldEditorAccessService.open(player);
	}

	public static void bind(Player player, String suppliedToken) {
		if (player == null
			|| !AdaptiveWorldBuilderRuntimeIdentity.isAdaptive(player.getConfig())
			|| !WorldBuilderMode.isBuilderAccount(player.getUsername())
			|| player.getGroupID() != Group.ADMIN
			|| !Boolean.TRUE.equals(player.getAttribute(
				BINDING_PENDING_ATTRIBUTE, Boolean.FALSE))
			|| Boolean.TRUE.equals(player.getAttribute(
				BINDING_COMPLETE_ATTRIBUTE, Boolean.FALSE))) {
			refuse(player, "Adaptive Builder binding was not expected.");
			return;
		}
		AdaptiveWorldBuilderRuntimeSession session = player.getWorld()
			.getServer().getAdaptiveWorldBuilderRuntimeSession();
		if (session == null || suppliedToken == null
			|| !MessageDigest.isEqual(
				session.getToken().getBytes(StandardCharsets.US_ASCII),
				suppliedToken.getBytes(StandardCharsets.US_ASCII))) {
			refuse(player,
				"Adaptive Builder client/server/package binding failed.");
			return;
		}
		player.removeAttribute(BINDING_PENDING_ATTRIBUTE);
		player.setAttribute(BINDING_COMPLETE_ATTRIBUTE, Boolean.TRUE);
		if (!WorldEditorAccessService.open(player)) {
			refuse(player, "Adaptive Builder editor authorization failed.");
		}
	}

	private static void refuse(Player player, String message) {
		LOGGER.error(message);
		if (player != null) {
			player.message(player.getConfig().MESSAGE_PREFIX + message);
			player.getWorld().getServer().getWorldEditorSessions().closeFor(player);
		}
	}
}
