package com.openrsc.server.plugins;

import com.openrsc.server.Server;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerGuildAccess;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.DataConversions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Runs in an isolated JVM because Influence changes the server skill registry. */
public final class MonsterSlayerInfluenceGuildAccessTest {
	private MonsterSlayerInfluenceGuildAccessTest() { }

	public static void main(String[] args) throws Exception {
		java.nio.file.Path configuration = Paths.get("influence-myworld.conf");
		String source = new String(Files.readAllBytes(configuration), StandardCharsets.UTF_8);
		Files.write(configuration, source.replace("influence_instead_qp: false", "influence_instead_qp: true").getBytes(StandardCharsets.UTF_8));
		Server server = new Server(configuration.toString());
		assertTrue(server.getConfig().INFLUENCE_INSTEAD_QP, "Influence mode fixture enabled");
		assertTrue(Skill.INFLUENCE.id() >= 0, "Influence has a registered skill id");
		Player player = new Player(server.getWorld(), DataConversions.usernameToHash("slayerinfluence"));
		player.getSkills().setLevel(Skill.INFLUENCE.id(), 19, false, false);
		assertFalse(MonsterSlayerGuildAccess.allows(player, 3), "Champions rejects Influence level 19");
		player.getSkills().setLevel(Skill.INFLUENCE.id(), 20, false, false);
		assertTrue(MonsterSlayerGuildAccess.allows(player, 3), "Champions accepts Influence level 20");
		System.out.println("Monster Slayer Influence guild access: PASS");
	}

	private static void assertTrue(boolean value, String label) { if (!value) throw new AssertionError(label); }
	private static void assertFalse(boolean value, String label) { assertTrue(!value, label); }
}
