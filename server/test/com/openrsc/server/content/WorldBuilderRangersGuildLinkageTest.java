package com.openrsc.server.content;

import com.openrsc.server.Server;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Skills;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.util.rsc.DataConversions;

/** Exercises the stale Skills caller against current Core on production precedence. */
public final class WorldBuilderRangersGuildLinkageTest {
	private WorldBuilderRangersGuildLinkageTest() { }

	public static void main(String[] arguments) throws Exception {
		assertSource(Skills.class, "world-builder-managed-runtime.jar");
		assertSource(RangersGuildPoints.class, "core.jar");

		Server server = new Server("myworld.conf");
		World world = server.getWorld();
		Player player = new Player(world,
			DataConversions.usernameToHash("rangerslinkagetest"));
		player.setClientVersion((short) 10052);
		player.setClientLimitations(ClientLimitations.forVersion(10052));

		int magic = Skill.MAGIC.id();
		int experienceBefore = player.getSkills().getExperience(magic);
		int pointsBefore = RangersGuildPoints.getPoints(player);
		player.getSkills().addExperience(magic, 1);

		int experienceAfter = player.getSkills().getExperience(magic);
		if (experienceAfter != experienceBefore + 1) {
			throw new AssertionError("experience award did not complete exactly once");
		}
		if (RangersGuildPoints.getPoints(player) != pointsBefore) {
			throw new AssertionError("ordinary experience must not award Rangers Guild points");
		}
		System.out.println("PASS: managed-runtime Skills links to current kill-based Rangers Guild points");
	}

	private static void assertSource(Class<?> type, String expectedArchive) {
		String source = type.getProtectionDomain().getCodeSource().getLocation().toString();
		if (!source.endsWith(expectedArchive)) {
			throw new AssertionError(type.getName() + " loaded from " + source);
		}
	}
}
