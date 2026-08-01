#!/usr/bin/env python3
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE_JAR = SERVER / "core.jar"
AREA = SERVER / "src/com/openrsc/server/content/RangersGuildArea.java"
POINTS = SERVER / "src/com/openrsc/server/content/RangersGuildPoints.java"
SKILLS = SERVER / "src/com/openrsc/server/model/Skills.java"
PLAYER = SERVER / "src/com/openrsc/server/model/entity/player/Player.java"
NPC = SERVER / "src/com/openrsc/server/model/entity/npc/Npc.java"
RANGE_EVENT = SERVER / "src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java"
THROWING_EVENT = SERVER / "src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
PLAYER_SERVICE = SERVER / "src/com/openrsc/server/service/PlayerService.java"
GAME_DATABASE = SERVER / "src/com/openrsc/server/database/GameDatabase.java"
VENDOR = (
    SERVER
    / "plugins/com/openrsc/server/plugins/custom/npcs/RangersGuildPointsVendor.java"
)
DOOR = SERVER / "plugins/com/openrsc/server/plugins/custom/misc/RangersGuildDoor.java"


PLAYER_STUB = r"""
package com.openrsc.server.model.entity.player;

import com.openrsc.server.model.Cache;
import com.openrsc.server.model.world.coordinate.WorldLocation;

public final class Player {
    public static final class Config {
        public boolean WANT_MYWORLD;
    }

    private final Cache cache;
    private final Config config = new Config();
    private WorldLocation location;

    public Player(Cache cache, WorldLocation location, boolean myWorld) {
        this.cache = cache;
        this.location = location;
        this.config.WANT_MYWORLD = myWorld;
    }

    public Cache getCache() {
        return cache;
    }

    public Config getConfig() {
        return config;
    }

    public WorldLocation getWorldLocation() {
        return location;
    }

    public void setWorldLocation(WorldLocation location) {
        this.location = location;
    }
}
"""


SKILL_STUB = r"""
package com.openrsc.server.constants;

public final class Skill {
    public static final Skill RANGED = new Skill(4);
    public static final Skill MAGIC = new Skill(6);

    private final int id;

    private Skill(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
"""


HARNESS = r"""
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.RangersGuildArea;
import com.openrsc.server.content.RangersGuildPoints;
import com.openrsc.server.model.Cache;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

public final class RangersGuildPointsHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    public static void main(String[] arguments) {
        WorldLocation minimum = location(484, 449, -1);
        WorldLocation maximum = location(515, 478, -1);
        check(RangersGuildArea.containsBasement(minimum),
            "logical basement minimum was rejected");
        check(RangersGuildArea.containsBasement(maximum),
            "logical basement maximum was rejected");
        check(RangersGuildArea.containsBasement(
                LegacyPackedPointAdapter.fromPackedValues(484, 3281)),
            "legacy basement coordinate did not resolve to the activity");
        check(!RangersGuildArea.containsBasement(location(483, 449, -1)),
            "west-adjacent tile entered the activity");
        check(!RangersGuildArea.containsBasement(location(484, 448, -1)),
            "north-adjacent tile entered the activity");
        check(!RangersGuildArea.containsBasement(location(484, 449, 0)),
            "surface tile with matching X/Y entered the basement activity");
        check(!RangersGuildArea.containsBasement(location(484, 449, 1)),
            "upper tile with matching X/Y entered the basement activity");
        check(!RangersGuildArea.containsBasement(new WorldLocation(
                new WorldSpaceId("private.one"), new WorldCoordinate(484, 449, -1))),
            "another world space entered the global activity");

        check(RangersGuildArea.isEntranceDoor(location(495, 463, 0)),
            "surface entrance door was rejected");
        check(!RangersGuildArea.isEntranceDoor(location(495, 463, -1)),
            "underground coordinate matched the surface entrance door");

        Cache persisted = new Cache();
        Player player = new Player(persisted, minimum, true);
        RangersGuildPoints.awardFromExperience(player, Skill.RANGED.id(), 9);
        check(RangersGuildPoints.getPoints(player) == 0,
            "partial XP awarded a point too early");
        check(persisted.getInt(RangersGuildPoints.REMAINDER_CACHE_KEY) == 9,
            "partial XP was not persisted");

        RangersGuildPoints.awardFromExperience(player, Skill.RANGED.id(), 12);
        check(RangersGuildPoints.getPoints(player) == 2,
            "credited Ranged XP did not award points");
        check(persisted.getInt(RangersGuildPoints.REMAINDER_CACHE_KEY) == 1,
            "credited Ranged XP remainder was lost");

        Player reloaded = new Player(persisted, maximum, true);
        check(RangersGuildPoints.getPoints(reloaded) == 2,
            "cache-backed point balance did not survive player reconstruction");
        RangersGuildPoints.awardFromExperience(
            reloaded, Skill.MAGIC.id(), 1000);
        check(RangersGuildPoints.getPoints(reloaded) == 2,
            "non-Ranged XP awarded guild points");

        reloaded.setWorldLocation(location(484, 449, 0));
        RangersGuildPoints.awardFromExperience(
            reloaded, Skill.RANGED.id(), 1000);
        check(RangersGuildPoints.getPoints(reloaded) == 2,
            "matching coordinates on the wrong layer awarded points");
        reloaded.setWorldLocation(location(483, 449, -1));
        RangersGuildPoints.awardFromExperience(
            reloaded, Skill.RANGED.id(), 1000);
        check(RangersGuildPoints.getPoints(reloaded) == 2,
            "Ranged XP outside the activity awarded points");

        Player disabled = new Player(new Cache(), minimum, false);
        RangersGuildPoints.awardFromExperience(
            disabled, Skill.RANGED.id(), 1000);
        check(RangersGuildPoints.getPoints(disabled) == 0,
            "non-MyWorld profile awarded guild points");

        reloaded.setWorldLocation(minimum);
        RangersGuildPoints.addPoints(reloaded, Integer.MAX_VALUE);
        check(RangersGuildPoints.getPoints(reloaded) == Integer.MAX_VALUE,
            "point addition overflowed instead of saturating");
        check(!RangersGuildPoints.spendPoints(reloaded, 0),
            "zero-cost spend succeeded");
        check(RangersGuildPoints.spendPoints(reloaded, 1),
            "valid point spend failed");
        check(RangersGuildPoints.getPoints(reloaded) == Integer.MAX_VALUE - 1,
            "valid point spend deducted the wrong amount");
        check(!RangersGuildPoints.spendPoints(reloaded, Integer.MAX_VALUE),
            "oversized spend succeeded");
    }
}
"""


class RangersGuildPointsFlowTest(unittest.TestCase):
    def test_layered_area_and_cache_ledger_behavior(self):
        self.assertTrue(CORE_JAR.exists(), "server/core.jar must be built first")
        with tempfile.TemporaryDirectory(prefix="rangers-guild-points-") as temp:
            temp_path = Path(temp)
            player_stub = temp_path / "com/openrsc/server/model/entity/player/Player.java"
            skill_stub = temp_path / "com/openrsc/server/constants/Skill.java"
            harness = temp_path / "RangersGuildPointsHarness.java"
            player_stub.parent.mkdir(parents=True)
            skill_stub.parent.mkdir(parents=True)
            player_stub.write_text(PLAYER_STUB, encoding="utf-8")
            skill_stub.write_text(SKILL_STUB, encoding="utf-8")
            harness.write_text(textwrap.dedent(HARNESS), encoding="utf-8")

            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-cp",
                    str(CORE_JAR),
                    "-d",
                    str(temp_path),
                    str(player_stub),
                    str(skill_stub),
                    str(AREA),
                    str(POINTS),
                    str(harness),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    os.pathsep.join((str(temp_path), str(CORE_JAR))),
                    "RangersGuildPointsHarness",
                ],
                cwd=ROOT,
                check=True,
            )

    def test_final_credited_xp_attribution_path_is_preserved(self):
        skills = SKILLS.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        npc = NPC.read_text(encoding="utf-8")
        range_event = RANGE_EVENT.read_text(encoding="utf-8")
        throwing_event = THROWING_EVENT.read_text(encoding="utf-8")

        self.assertIn(
            "int creditedExperience = Math.max(0, exps[skill] - oldExp);",
            skills,
        )
        self.assertIn(
            "RangersGuildPoints.awardFromExperience((Player) getMob(), skill, creditedExperience);",
            skills,
        )
        self.assertIn("handleXpDistribution(mob)", npc)
        self.assertIn("awardRangedDamageShareXp(", npc)
        self.assertIn("player.incExp(primarySkill.id(),", npc)
        self.assertIn(
            "partyMemberPlayer.getSkills().addExperience(skill, playerXp);",
            player,
        )
        self.assertIn("getSkills().addExperience(skill, thisXp);", player)
        self.assertIn("player.incExp(Skill.RANGED.id()", range_event)
        self.assertIn("player.incExp(Skill.RANGED.id()", throwing_event)

    def test_balance_display_redemption_and_persistence_integrations(self):
        vendor = VENDOR.read_text(encoding="utf-8")
        player_service = PLAYER_SERVICE.read_text(encoding="utf-8")
        database = GAME_DATABASE.read_text(encoding="utf-8")

        self.assertGreaterEqual(vendor.count("RangersGuildPoints.getPoints(player)"), 3)
        for fragment in (
            "long totalCost = (long) reward.cost * (long) quantity;",
            "long totalAmount = (long) reward.amount * (long) quantity;",
            "getInventory().canHold(item)",
            "RangersGuildPoints.spendPoints(player, (int) totalCost)",
            "getInventory().add(item)",
            "RangersGuildPoints.addPoints(player, (int) totalCost)",
        ):
            self.assertIn(fragment, vendor)
        self.assertIn("savePlayerCache(player);", player_service)
        self.assertIn("querySavePlayerCache(player);", player_service)
        self.assertIn("player.getCache().getCacheMap()", database)

    def test_related_door_check_is_level_qualified(self):
        door = DOOR.read_text(encoding="utf-8")
        self.assertIn(
            "RangersGuildArea.isEntranceDoor(obj.getWorldLocation())", door
        )
        self.assertIn("findNpcInLegacyPackedArea(", door)
        self.assertNotIn("obj.getY() == DOOR_Y", door)


if __name__ == "__main__":
    unittest.main()
