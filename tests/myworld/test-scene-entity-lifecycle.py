#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java"
INSTANCE_STORE = ROOT / "Client_Base" / "src" / "orsc" / "ClientSceneInstanceStore.java"
SERVER_UPDATER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "GameStateUpdater.java"
PLAYER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "player" / "Player.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"retired {description} still present: {needle}")


def main() -> None:
    text = CLIENT.read_text(encoding="utf-8")
    instance_store = INSTANCE_STORE.read_text(encoding="utf-8")
    updater = SERVER_UPDATER.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    require(
        text,
        "if (hardAreaLoad || heightOffsetChanged) {\n\t\t\t\t\t\tthis.retainPendingAreaLoadStaticScene();\n\t\t\t\t\t}",
        "transition-aware hard area/plane object cache reset",
    )
    require(
        instance_store,
        "if (!gameObjectPendingAreaLoad[readIndex]) {\n\t\t\t\tcontinue;\n\t\t\t}",
        "old-area scenery rejection during hard loads",
    )
    require(
        instance_store,
        "if (!wallObjectPendingAreaLoad[readIndex]) {\n\t\t\t\tcontinue;\n\t\t\t}",
        "old-area wall rejection during hard loads",
    )
    require(
        text,
        "if (hardAreaLoad || heightOffsetChanged) {\n\t\t\t\t\t\tthis.resetGroundItemsForHardAreaLoad();",
        "hard area/plane ground item cache reset",
    )
    require(
        updater,
        "if (!isWithinAuthoritativeSceneWindow(playerToUpdate, o)",
        "bounded scenery cache for all clients",
    )
    require(
        updater,
        "candidate == null || !candidate.sharesSpatialDomain(viewer)",
        "layer-aware scene-domain boundary",
    )
    require(
        updater,
        "new ItemLoc(groundItem.getID() + 32768, offsetX, offsetY",
        "single ground item removal packet",
    )
    forbid(updater, "getLocationsToClear()", "shared region-clear queue use")
    forbid(updater, "respawnTime = -1", "ground item region-wide range removal")
    forbid(player, "locationsToClear", "player shared region-clear queue")
    print("PASS: scene entity lifecycle uses bounded caches and entity-specific removals")


if __name__ == "__main__":
    main()
