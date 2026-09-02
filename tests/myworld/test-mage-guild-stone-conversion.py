#!/usr/bin/env python3
import json
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORE_JAR = ROOT / "server/core.jar"
CREDITS = ROOT / "server/src/com/openrsc/server/content/MageGuildStoneCredits.java"
FRUMSCONE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/yanille/WizardFrumscone.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
DEFAULT = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/defaults/Default.java"
NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefs.json"
ACTIVE_PLACEMENTS = ROOT / (
    "server/world-builder/packages/"
    "d037a81117d359bd1e92147ced077f566e2ce6fdaa424e949f8bf6f83e6c3b2b/"
    "package/placements/global/lm1.json"
)

PLAYER_STUB = r"""
package com.openrsc.server.model.entity.player;
import com.openrsc.server.model.Cache;
public final class Player {
    public static final class Config { public boolean WANT_MYWORLD; }
    private final Cache cache;
    private final Config config = new Config();
    public Player(Cache cache, boolean enabled) {
        this.cache = cache;
        this.config.WANT_MYWORLD = enabled;
    }
    public Cache getCache() { return cache; }
    public Config getConfig() { return config; }
}
"""

NPC_STUB = r"""
package com.openrsc.server.model.entity.npc;
import com.openrsc.server.external.NPCLoc;
public final class Npc {
    private final int id;
    private final NPCLoc loc;
    private final boolean authored;
    public Npc(int id, int x, int y, boolean authored) {
        int packedY = y + 3 * 944;
        this.id = id;
        this.loc = new NPCLoc(id, x, packedY, x, x, packedY, packedY);
        this.authored = authored;
    }
    public int getID() { return id; }
    public NPCLoc getLoc() { return loc; }
    public Object getAuthoredPlacementIdentity() { return authored ? this : null; }
}
"""

HARNESS = r"""
import com.openrsc.server.content.MageGuildStoneCredits;
import com.openrsc.server.model.Cache;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
public final class MageGuildStoneCreditsHarness {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Cache persisted = new Cache();
        Player player = new Player(persisted, true);
        MageGuildStoneCredits.awardEligibleKill(player, new Npc(516, 604, 751, true));
        MageGuildStoneCredits.awardEligibleKill(player, new Npc(516, 620, 753, true));
        check(MageGuildStoneCredits.getCredits(player) == 2, "eligible kills were not credited");
        MageGuildStoneCredits.awardEligibleKill(player, new Npc(516, 603, 751, true));
        MageGuildStoneCredits.awardEligibleKill(player, new Npc(516, 604, 751, false));
        MageGuildStoneCredits.awardEligibleKill(player, new Npc(203, 604, 751, true));
        MageGuildStoneCredits.awardEligibleKill(new Player(new Cache(), false), new Npc(516, 604, 751, true));
        check(MageGuildStoneCredits.getCredits(player) == 2, "ineligible kill was credited");
        Player reloaded = new Player(persisted, true);
        check(MageGuildStoneCredits.getCredits(reloaded) == 2, "credits were not persistent");
        check(MageGuildStoneCredits.spendCredits(reloaded, 2), "valid spend failed");
        check(MageGuildStoneCredits.getCredits(reloaded) == 0, "spend deducted wrong amount");
        check(!MageGuildStoneCredits.spendCredits(reloaded, 1), "overspend succeeded");
    }
}
"""


class MageGuildStoneConversionTest(unittest.TestCase):
    def test_active_package_has_only_the_eligible_authored_magic_zombies(self):
        package = json.loads(ACTIVE_PLACEMENTS.read_text(encoding="utf-8"))
        local = [
            npc for npc in package["npcs"]
            if 604 <= npc["start"]["x"] <= 620
            and 751 <= npc["start"]["y"] <= 753
        ]
        self.assertEqual(len(local), 26)
        self.assertEqual({npc["npcId"] for npc in local}, {516})
        self.assertTrue(all(npc["placementId"].startswith("world-builder.authored.npc.lm1.") for npc in local))

    def test_credit_ledger_is_persistent_and_spawn_qualified(self):
        self.assertTrue(CORE_JAR.exists(), "server/core.jar must be built first")
        with tempfile.TemporaryDirectory(prefix="mage-guild-credits-") as temp:
            out = Path(temp)
            player = out / "com/openrsc/server/model/entity/player/Player.java"
            npc = out / "com/openrsc/server/model/entity/npc/Npc.java"
            harness = out / "MageGuildStoneCreditsHarness.java"
            player.parent.mkdir(parents=True)
            npc.parent.mkdir(parents=True)
            player.write_text(PLAYER_STUB, encoding="utf-8")
            npc.write_text(NPC_STUB, encoding="utf-8")
            harness.write_text(textwrap.dedent(HARNESS), encoding="utf-8")
            subprocess.run([
                "javac", "-source", "8", "-target", "8", "-cp", str(CORE_JAR),
                "-d", str(out), str(player), str(npc), str(CREDITS), str(harness),
            ], cwd=ROOT, check=True)
            subprocess.run([
                "java", "-cp", os.pathsep.join((str(out), str(CORE_JAR))),
                "MageGuildStoneCreditsHarness",
            ], cwd=ROOT, check=True)

    def test_exchange_uses_true_noted_stone_and_rolls_back_failures(self):
        source = FRUMSCONE.read_text(encoding="utf-8")
        for fragment in (
            "countId(ItemId.RUNE_STONE.id(), Optional.of(true))",
            "new Item(ItemId.RUNE_STONE.id(), quantity, true)",
            "new Item(ItemId.RUNE_STONE.id(), 1, false)",
            "quantity == notedStone",
            "quantity > usableSlots",
            "MageGuildStoneCredits.spendCredits(player, quantity)",
            "inventory.add(notedInput)",
            '"Convert 1", "Convert 5", "Convert all I can"',
        ):
            self.assertIn(fragment, source)
        self.assertNotIn("ZOMBIE_EYE", source)
        self.assertNotIn("BLUE_DRAGON_SCALE", source)
        self.assertNotIn("GroundItem", source)

        npc_defs = json.loads(NPC_DEFS.read_text(encoding="utf-8"))["npcs"]
        frumscone = next(npc for npc in npc_defs if npc["id"] == 515)
        self.assertEqual(frumscone["command"], "Convert Stone")
        self.assertEqual(frumscone["command2"], "")
        self.assertIn("MageGuildStoneCredits.awardEligibleKill(player, n);", DEFAULT.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
