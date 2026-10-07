package com.openrsc.server.plugins.authentic.skills.agility;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.openrsc.server.plugins.Functions.*;

public class GnomeAgilityCourse implements OpLocTrigger {

	private static final int BALANCE_LOG = 655;
	private static final int NET = 647;
	private static final int WATCH_TOWER = 648;
	private static final int ROPE_SWING = 650;
	private static final int LANDING = 649;
	private static final int SECOND_NET = 653;
	private static final int PIPE = 654;

	//private static int[] obstacleOrder = {BALANCE_LOG, NET, WATCH_TOWER, ROPE_SWING, LANDING, SECOND_NET, PIPE};
	private static Set<Integer> obstacles = new HashSet<Integer>(Arrays.asList(BALANCE_LOG, NET, WATCH_TOWER, ROPE_SWING, LANDING, SECOND_NET));
	private static Integer lastObstacle = PIPE;

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return inArray(obj.getID(), BALANCE_LOG, NET, WATCH_TOWER, ROPE_SWING, LANDING, SECOND_NET, PIPE);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (config().WANT_FATIGUE) {
			if (config().STOP_SKILLING_FATIGUED >= 1
				&& player.getFatigue() >= player.MAX_FATIGUE && !inArray(obj.getID(), WATCH_TOWER, ROPE_SWING, LANDING)) {
				player.message("you are too tired to train");
				return;
			}
		}
		Npc gnomeTrainer;
		switch (obj.getID()) {
			case BALANCE_LOG:
				player.message("you stand on the slippery log");
				boundaryTeleport(player, Point.location(692, 494));
				delay();
				player.teleport(692, 495, 0, false);
				delay();
				boundaryTeleport(player, Point.location(692, 496));
				delay();
				boundaryTeleport(player, Point.location(692, 497));
				delay();
				boundaryTeleport(player, Point.location(692, 498));
				delay();
				boundaryTeleport(player, Point.location(692, 499));
				delay();
				player.message("and walk across");
				player.incExp(Skill.AGILITY.id(), 30, true);
				AgilityUtils.completedObstacle(player, obj.getID(), obstacles, lastObstacle, 150, ItemId.TIER_1_AGILITY_POUCH.id());
				return;
			case NET:
				gnomeTrainer = ifnearvisnpc(player, NpcId.GNOME_TRAINER_STARTINGNET.id(), 10);
				if (gnomeTrainer != null && !AgilityUtils.hasDoneObstacle(player, NET, obstacles)) {
					npcsay(player, gnomeTrainer, "move it, move it, move it");
				}
				player.message("you climb the net");
				delay(3);
				// Hard course destinations use geographic Y and an explicit level.
				// Native layered Points do not decode the old 944-per-floor Y packing.
				player.teleport(692, 504, 1, false);
				player.message("and pull yourself onto the platform");
				player.incExp(Skill.AGILITY.id(), 30, true);
				AgilityUtils.completedObstacle(player, obj.getID(), obstacles, lastObstacle, 150, ItemId.TIER_1_AGILITY_POUCH.id());
				return;
			case WATCH_TOWER:
				gnomeTrainer = ifnearvisnpc(player, NpcId.GNOME_TRAINER_PLATFORM.id(), 10);
				if (gnomeTrainer != null && !AgilityUtils.hasDoneObstacle(player, WATCH_TOWER, obstacles)) {
					npcsay(player, gnomeTrainer, "that's it, straight up, no messing around");
				}
				player.message("you pull yourself up the tree");
				delay(2);
				// The upper platform is (690..691, 507); (693, 506) is invisible floor.
				player.teleport(691, 507, 2, false);
				player.message("to the platform above");
				player.incExp(Skill.AGILITY.id(), 30, true);
				AgilityUtils.completedObstacle(player, obj.getID(), obstacles, lastObstacle, 150, ItemId.TIER_1_AGILITY_POUCH.id());
				return;
			case ROPE_SWING:
				player.message("you reach out and grab the rope swing");
				delay(2);
				player.message("you hold on tight");
				delay(4);
				player.teleport(685, 508, 2, false);
				player.message("and swing to the oppisite platform");
				player.incExp(Skill.AGILITY.id(), 30, true);
				AgilityUtils.completedObstacle(player, obj.getID(), obstacles, lastObstacle, 150, ItemId.TIER_1_AGILITY_POUCH.id());
				return;
			case LANDING:
				player.message("you hang down from the tower");
				delay(2);
				player.teleport(683, 506, 0, false);
				player.message("and drop to the floor");
				say(player, null, "ooof");
				player.incExp(Skill.AGILITY.id(), 30, true);
				AgilityUtils.completedObstacle(player, obj.getID(), obstacles, lastObstacle, 150, ItemId.TIER_1_AGILITY_POUCH.id());
				return;
			case SECOND_NET:
				gnomeTrainer = ifnearvisnpc(player, NpcId.GNOME_TRAINER_ENDINGNET.id(), 10);
				if (gnomeTrainer != null && !AgilityUtils.hasDoneObstacle(player, SECOND_NET, obstacles)) {
					npcsay(player, gnomeTrainer, "my granny can move faster than you");
				}
				player.message("you take a few steps back");
				delay();
				player.setLocation(Point.location(683, 505));
				player.message("and run towards the net");
				delay();
				player.teleport(683, 501, 0, false);
				player.incExp(Skill.AGILITY.id(), 30, true);
				AgilityUtils.completedObstacle(player, obj.getID(), obstacles, lastObstacle, 150, ItemId.TIER_1_AGILITY_POUCH.id());
				return;
			case PIPE:
				mes("you squeeze into the pipe");
				delay(3);
				mes("and shuffle down into it");
				delay(3);
				player.teleport(683, 494, 0, false);
				gnomeTrainer = ifnearvisnpc(player, NpcId.GNOME_TRAINER_ENTRANCE.id(), 10);
				if (gnomeTrainer != null && !AgilityUtils.hasDoneObstacle(player, PIPE, obstacles)) {
					npcsay(player, gnomeTrainer, "that's the way, well done");
				}
				player.incExp(Skill.AGILITY.id(), 30, true);
				AgilityUtils.completedObstacle(player, obj.getID(), obstacles, lastObstacle, 150, ItemId.TIER_1_AGILITY_POUCH.id());
				return;
		}
	}
}
