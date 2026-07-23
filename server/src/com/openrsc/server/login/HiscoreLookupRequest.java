package com.openrsc.server.login;

import com.openrsc.server.Server;
import com.openrsc.server.database.struct.HiscoreEntry;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fetches hiscore rankings from the database off the game tick, on the
 * login thread, and replies to the requesting player. Rankings are only
 * read on demand while the hiscores interface is open - nothing is cached
 * server side.
 */
public class HiscoreLookupRequest extends LoginExecutorProcess {
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	public static final int OVERALL_ID = 255;

	private final Server server;
	private final Player player;
	private final int skillId;

	public HiscoreLookupRequest(final Server server, final Player player, final int skillId) {
		this.server = server;
		this.player = player;
		this.skillId = skillId;
	}

	public final Player getPlayer() {
		return player;
	}

	public final Server getServer() {
		return server;
	}

	protected void processInternal() {
		try {
			if (!getPlayer().loggedIn()) {
				return;
			}

			// Persist the requester's latest state right here on the login
			// thread so the rankings read back below always include it.
			try {
				getServer().getPlayerService().savePlayer(getPlayer());
			} catch (final Exception e) {
				LOGGER.error("Hiscore pre-save failed for " + getPlayer() + ": ", e);
			}

			final HiscoreEntry[] entries;
			final int ownRank;
			final int ownLevel;
			final long ownExperience;
			if (skillId == OVERALL_ID) {
				ownLevel = getPlayer().getSkills().getHiscoreTotalLevel();
				ownExperience = getPlayer().getSkills().getHiscoreTotalExperience();
				entries = getServer().getDatabase().queryHiscoreOverallTop();
				ownRank = getServer().getDatabase().queryHiscoreOverallRank(getPlayer().getDatabaseID(), ownLevel, ownExperience);
			} else {
				ownLevel = getPlayer().getSkills().getMaxStat(skillId);
				ownExperience = getPlayer().getSkills().getExperience(skillId) & 0xffffffffL;
				entries = getServer().getDatabase().queryHiscoreSkillTop(skillId);
				ownRank = getServer().getDatabase().queryHiscoreSkillRank(getPlayer().getDatabaseID(), skillId, ownExperience);
			}

			ActionSender.sendHiscores(getPlayer(), skillId, ownRank, ownLevel, ownExperience, entries);
		} catch (final Exception e) {
			LOGGER.error("Error fetching hiscores for " + getPlayer() + ": ", e);
		}
	}
}
