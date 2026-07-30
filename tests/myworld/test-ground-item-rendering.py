#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base" / "src" / "orsc" / "PacketHandler.java"
GROUND_ITEM = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "instances" / "GroundItem.java"
MUD_GRAPHICS = ROOT / "Client_Base" / "src" / "orsc" / "graphics" / "two" / "MudClientGraphics.java"
GRAPHICS = ROOT / "Client_Base" / "src" / "orsc" / "graphics" / "two" / "GraphicsController.java"
SCENE = ROOT / "Client_Base" / "src" / "orsc" / "graphics" / "three" / "Scene.java"


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
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    ground_item = GROUND_ITEM.read_text(encoding="utf-8")
    mud_graphics = MUD_GRAPHICS.read_text(encoding="utf-8")
    graphics = GRAPHICS.read_text(encoding="utf-8")
    scene = SCENE.read_text(encoding="utf-8")
    require(text, "groundItemRenderStackIndex", "per-item ground stack index")
    require(text, "assignGroundItemStackOffsets();", "stack offset assignment")
    require(text, "getGroundItemStackOffsetX", "ground item x offset helper")
    require(text, "getGroundItemStackOffsetZ", "ground item z offset helper")
    require(text, "this.groundItemRenderOrder.add(index);", "all visible ground items stay renderable")
    require(text, "getGroundItemIndexFromScenePickIndex", "ground item source index recovery from scene pick")
    require(text, "LinkedHashMap<GroundItemNameplateKey, GroundItemNameplateGroup>", "tile-grouped ground item nameplates")
    require(text, "GroundItemNameplateKey.from(groundItem)", "ground item nameplate tile grouping key")
    require(text, "new GroundItem(id, x, y, width, height, nameplateItemDef, sprite,\n\t\t\t\t\tthis.groundItemX[groundItemIndex], this.groundItemZ[groundItemIndex])", "nameplates retain ground item tile")
    require(text, "this.count > 1 ? this.name + \" (\" + this.count + \")\" : this.name", "same-name ground item count display")
    require(text, "createGroundItemNameplateBounds", "ground item nameplate collision bounds")
    forbid(text, "Collections.frequency(groundItems", "screen-position frequency name grouping")
    forbid(text, "groundItemRenderTopByTile", "single top ground item per tile collapse")
    require(ground_item, "private int tileX;", "ground item tile x for nameplates")
    require(ground_item, "private int tileZ;", "ground item tile z for nameplates")
    require(ground_item, "public boolean hasTile()", "ground item tile-backed nameplate marker")
    require(scene, "this.graphics.drawSceneEntity(", "scene forwards sprite ownership to 2d renderer")
    require(scene, "spritePickIndex,\n\t\t\t\t\t\t\tspriteAnchorIndex,\n\t\t\t\t\t\t\tspriteDrawOrder);", "scene preserves pick index with sprite ownership")
    require(graphics, "public void drawEntity(int index, int x, int y, int width, int height, int var1, int var8, int scenePickIndex)", "2d renderer accepts scene pick index")
    require(mud_graphics, "this.mudClientRef.drawItemAt(index - 40000, x, y, width, height, topPixelSkew,\n\t\t\t\t\t\t\tthis.mudClientRef.getGroundItemIndexFromScenePickIndex(scenePickIndex))", "item draw receives ground item source index")
    require(packet_handler, "private void removeGroundItemsAt(int groundItemX, int groundItemY, int groundItemID)", "ground item de-dupe helper")
    require(packet_handler, "boolean removed = false;", "single ground item removal guard")
    require(packet_handler, "if (!removed && mc.getGroundItemX(oldIndex) == groundItemX", "remove only the first matching ground item")
    require(packet_handler, "removed = true;\n\t\t\t\tcontinue;", "single matching ground item removal")
    require(packet_handler, "if ((groundItemID & 32768) != 0) {\n\t\t\t\t\tgroundItemID &= 32767;\n\t\t\t\t\tremoveGroundItemsAt(groundItemX, groundItemY, groundItemID);", "ground item removal cleanup")
    forbid(packet_handler, "removeGroundItemsAt(groundItemX, groundItemY, groundItemID);\n\t\t\t\t\tmc.setGroundItemX(mc.getGroundItemCount(), groundItemX);", "ground item add de-dupe before append")
    print("PASS: ground item rendering keeps stable stacked items visible")


if __name__ == "__main__":
    main()
