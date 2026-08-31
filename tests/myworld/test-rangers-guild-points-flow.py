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

NPC_STUB = r"""
package com.openrsc.server.model.entity.npc;

import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.entity.player.Player;

public final class Npc {
    private final int id;
    private final NPCLoc loc;
    private final boolean authored;
    private final boolean rangedDamage;

    public Npc(int id, NPCLoc loc, boolean authored, boolean rangedDamage) {
        this.id = id;
        this.loc = loc;
        this.authored = authored;
        this.rangedDamage = rangedDamage;
    }

    public int getID() { return id; }
    public NPCLoc getLoc() { return loc; }
    public Object getAuthoredPlacementIdentity() { return authored ? this : null; }
    public boolean hasRangedDamageBy(Player player) { return rangedDamage; }
}
"""


HARNESS = r"""
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.RangersGuildArea;
import com.openrsc.server.content.RangersGuildPoints;
import com.openrsc.server.model.Cache;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.entity.npc.Npc;
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
        WorldLocation minimum = location(484, 456, -1);
        WorldLocation maximum = location(515, 483, -1);
        check(RangersGuildArea.containsBasement(minimum),
            "logical basement minimum was rejected");
        check(RangersGuildArea.containsBasement(maximum),
            "logical basement maximum was rejected");
        check(RangersGuildArea.containsBasement(
                LegacyPackedPointAdapter.fromPackedValues(484, 3288)),
            "legacy basement coordinate did not resolve to the activity");
        check(!RangersGuildArea.containsBasement(location(483, 449, -1)),
            "west-adjacent tile entered the activity");
        check(!RangersGuildArea.containsBasement(location(484, 455, -1)),
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
        Npc giant = npc(61, 491, 464, true, true);
        Npc skeleton = npc(195, 506, 466, true, true);
        Npc demon = npc(22, 495, 477, true, true);
        Npc dragon = npc(196, 497, 462, true, true);
        RangersGuildPoints.awardEligibleRangedKill(player, giant);
        RangersGuildPoints.awardEligibleRangedKill(player, skeleton);
        RangersGuildPoints.awardEligibleRangedKill(player, demon);
        RangersGuildPoints.awardEligibleRangedKill(player, dragon);
        check(RangersGuildPoints.getPoints(player) == 57,
            "weighted authored basement kills awarded the wrong total");

        Player reloaded = new Player(persisted, maximum, true);
        check(RangersGuildPoints.getPoints(reloaded) == 57,
            "cache-backed point balance did not survive player reconstruction");
        RangersGuildPoints.awardEligibleRangedKill(reloaded, npc(61, 491, 464, false, true));
        RangersGuildPoints.awardEligibleRangedKill(reloaded, npc(61, 491, 464, true, false));
        RangersGuildPoints.awardEligibleRangedKill(reloaded, npc(61, 483, 464, true, true));
        RangersGuildPoints.awardEligibleRangedKill(reloaded, npc(68, 491, 464, true, true));
        check(RangersGuildPoints.getPoints(reloaded) == 57,
            "ineligible source, style, location, or roster awarded points");

        Player disabled = new Player(new Cache(), minimum, false);
        RangersGuildPoints.awardEligibleRangedKill(disabled, giant);
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

    private static Npc npc(int id, int x, int y, boolean authored, boolean ranged) {
        int packedY = y + 3 * 944;
        return new Npc(id, new NPCLoc(id, x, packedY, x, x, packedY, packedY),
            authored, ranged);
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
            npc_stub = temp_path / "com/openrsc/server/model/entity/npc/Npc.java"
            harness = temp_path / "RangersGuildPointsHarness.java"
            player_stub.parent.mkdir(parents=True)
            skill_stub.parent.mkdir(parents=True)
            npc_stub.parent.mkdir(parents=True)
            player_stub.write_text(PLAYER_STUB, encoding="utf-8")
            skill_stub.write_text(SKILL_STUB, encoding="utf-8")
            npc_stub.write_text(NPC_STUB, encoding="utf-8")
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
                    str(npc_stub),
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

    def test_final_kill_attribution_path_is_used(self):
        skills = SKILLS.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        npc = NPC.read_text(encoding="utf-8")
        range_event = RANGE_EVENT.read_text(encoding="utf-8")
        throwing_event = THROWING_EVENT.read_text(encoding="utf-8")

        self.assertNotIn("RangersGuildPoints", skills)
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
        self.assertIn("RangersGuildPoints.awardEligibleRangedKill(owner, this);", npc)
        self.assertIn("public boolean hasRangedDamageBy", npc)

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
