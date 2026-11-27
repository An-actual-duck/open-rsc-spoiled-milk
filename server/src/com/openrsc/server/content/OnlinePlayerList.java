package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PlayerSettings;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.ArrayList;

/** The visibility rules shared by the online list commands and the Social sidebar. */
public final class OnlinePlayerList {
	public static final String SIDEBAR_CAPABILITY = "online-sidebar/1";

	private OnlinePlayerList() {}

	public static void send(Player viewer, boolean retroClientListsAll, boolean wantLocations, boolean sidebar) {
		ArrayList<Player> players = new ArrayList<>();
		ArrayList<String> locations = new ArrayList<>();
		boolean showLocations = viewer.isMod() && wantLocations;
		for (Player candidate : viewer.getWorld().getPlayers()) {
			if (showLocations ? candidate.getGroupID() >= viewer.getGroupID() : isVisible(viewer, candidate)) {
				players.add(candidate);
				locations.add(showLocations ? candidate.getLocation().returnLocationName() : "");
			}
		}
		ActionSender.sendOnlineList(viewer, players, locations, retroClientListsAll, sidebar);
	}

	private static boolean isVisible(Player viewer, Player candidate) {
		if (viewer.isMod()) return true;
		if (candidate.isInvisibleTo(viewer)) return false;
		byte privacy = candidate.getSettings().getPrivacySetting(
			PlayerSettings.PRIVACY_BLOCK_PRIVATE_MESSAGES, candidate.isUsingCustomClient());
		return privacy == PlayerSettings.BlockingMode.None.id()
			|| (privacy == PlayerSettings.BlockingMode.NonFriends.id() && viewer.isDefaultUser()
				&& candidate.getSocial().isFriendsWith(viewer.getUsernameHash()));
	}
}
