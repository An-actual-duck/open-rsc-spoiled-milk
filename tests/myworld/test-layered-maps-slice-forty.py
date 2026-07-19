#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAYER_SAVE = ROOT / "server/src/com/openrsc/server/login/PlayerSaveRequest.java"
LOGIN_REQUEST = ROOT / "server/src/com/openrsc/server/login/LoginRequest.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceFortyTest(unittest.TestCase):
    def test_manager_owns_one_checked_ledger_without_region_authority(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")

        self.assertIn(
            "private final LayeredRegionInterestOwnershipLedger\n"
            "\t\tlayeredRegionInterestOwnershipLedger;",
            manager,
        )
        self.assertIn("layeredRegionInterestOwnershipLedger.clear();", manager)
        self.assertIn("openLayeredRegionInterestOwner(", manager)
        self.assertIn("synchronizeLayeredRegionInterestOwner(", manager)
        self.assertIn("closeLayeredRegionInterestOwner(", manager)
        self.assertIn("getLayeredRegionInterestOwnerSnapshot(", manager)
        self.assertIn("getLayeredRegionInterestOwnershipSnapshot(", manager)

        ownership_block = manager.split(
            "/** Opens one dormant owner", 1
        )[1].split(
            "/**\n\t * Compares one bounded logical interest change", 1
        )[0]
        self.assertIn("synchronized (layeredRegionLifecycleLock)", ownership_block)
        self.assertNotIn("getRegion(", ownership_block)
        self.assertNotIn("registerPackedRegion", ownership_block)
        self.assertNotIn("unregisterPackedRegion", ownership_block)
        self.assertNotIn(".unload(", ownership_block)
        self.assertNotIn("LayeredRegionInterestOwnershipLedger", path_validation)
        observer_writes = observer.split("private static Status write(", 1)[1]
        self.assertIn("InterestOwnershipMetadata", observer_writes)
        self.assertNotIn("getRegion(", observer_writes)
        self.assertNotIn(".unload(", observer_writes)
        self.assertIn("return getRegion(x, y).getTileValue(", manager)

    def test_player_session_opens_syncs_and_finally_closes_one_opaque_owner(self):
        player = PLAYER.read_text(encoding="utf-8")
        player_save = PLAYER_SAVE.read_text(encoding="utf-8")
        login_request = LOGIN_REQUEST.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "LayeredRegionInterestOwnershipLedger.OwnerToken "
            "layeredInterestOwner;",
            player,
        )
        self.assertIn("openOrSynchronizeLayeredInterestOwner(currentWindow);", player)
        self.assertIn("closeLayeredInterestOwner();", player)
        self.assertIn(
            "!currentWindow.equals(layeredInterestOwnerWindow)",
            player,
        )
        self.assertIn("synchronizeLayeredInterestOwnerIfOpen(window);", player)
        self.assertIn("snapshot.requireWindow(getLayeredVisibilityWindow());", player)
        self.assertIn("loadedPlayer.setLoggedIn(true);", login_request)
        self.assertIn("getPlayer().setLoggedIn(false);", player_save)
        self.assertLess(
            player_save.index("getPlayer().remove();"),
            player_save.index("getPlayer().setLoggedIn(false);"),
        )
        self.assertIn(
            "### Slice 40: Checked Player-session interest ownership shadow",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
