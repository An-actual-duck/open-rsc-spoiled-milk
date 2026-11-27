#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBJECT_DEFS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
HARVESTING_DEFS = ROOT / "server/conf/server/defs/extras/ObjectHarvesting.xml"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
WORLD_POPULATOR = ROOT / "server/src/com/openrsc/server/database/WorldPopulator.java"
HARVESTING_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java"
FORMULAE = ROOT / "server/src/com/openrsc/server/util/rsc/Formulae.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
SCENE = ROOT / "Client_Base/src/orsc/graphics/three/Scene.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"

LEGACY_HARVESTABLES = (
    ("Wheat", 72, 29, 72),
    ("Banana tree", 183, 249, 88),
    ("Potato", 191, 348, 72),
    ("flax", 313, 675, 40),
    ("Pineapple tree", 430, 861, 176),
)

OBJECT_TIERS_AND_YIELD_BASELINES = (
    (72, 1, 1),     # Grain
    (183, 5, 30),   # Banana tree
    (191, 1, 1),    # Potato
    (313, 1, 1),   # Flax
    (430, 8, 54),   # Pineapple tree
    (1243, 3, 15),  # Lemon tree
    (1244, 4, 22),  # Lime tree
    (1245, 5, 30),  # Apple tree
    (1246, 6, 38),  # Orange tree
    (1247, 8, 54),  # Grapefruit tree
    (1248, 5, 30),  # Banana palm
    (1249, 9, 62),  # Coconut palm
    (1250, 8, 54),  # Papaya palm
    (1251, 8, 54),  # Pineapple plant
    (1256, 2, 8),   # Redberry bush
    (1257, 4, 22),  # Cadavaberry bush
    (1258, 6, 38),  # Dwellberry bush
    (1259, 7, 46),  # Jangerberry bush
    (1260, 9, 62),  # Whiteberry bush
    (1265, 1, 1),   # Potato
    (1266, 1, 1),   # Onion
    (1267, 2, 8),   # Garlic
    (1268, 3, 15),  # Tomato
    (1269, 4, 22),  # Corn
    (1262, 1, 1),   # Cabbage
    (1263, 5, 30),  # Red cabbage
    (1264, 7, 46),  # White pumpkin
    (1282, 7, 46),  # Sugar cane
    (1283, 6, 38),  # Grape vine
    (1293, 10, 70),  # Dragonfruit tree
    (1327, 1, 1),   # Event pumpkin
    (1275, 1, 1),   # Lily's pumpkin
)

CLIP_REWARD_SNIPPETS = (
    "ItemId.UNIDENTIFIED_GUAM_LEAF.id(), 1, 1, 50",
    "ItemId.UNIDENTIFIED_MARRENTILL.id(), 2, 8, 60",
    "ItemId.UNIDENTIFIED_TARROMIN.id(), 3, 15, 72",
    "ItemId.UNIDENTIFIED_HARRALANDER.id(), 4, 22, 96",
    "ItemId.UNIDENTIFIED_RANARR_WEED.id(), 5, 30, 122",
    "ItemId.UNIDENTIFIED_IRIT_LEAF.id(), 6, 38, 194",
    "ItemId.UNIDENTIFIED_AVANTOE.id(), 7, 46, 246",
    "ItemId.UNIDENTIFIED_KWUARM.id(), 8, 54, 312",
    "ItemId.UNIDENTIFIED_CADANTINE.id(), 8, 54, 480",
    "ItemId.UNIDENTIFIED_DWARF_WEED.id(), 10, 70, 768",
    "ItemId.UNIDENTIFIED_TORSTOL.id(), 10, 70, 960",
    "ItemId.SEAWEED.id(), 4, 22, 84",
    "ItemId.EDIBLE_SEAWEED.id(), 4, 22, 84",
    "ItemId.LIMPWURT_ROOT.id(), 6, 38, 144",
    "ItemId.SNAPE_GRASS.id(), 8, 54, 328",
)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        raise AssertionError(message)


def main() -> None:
    object_defs = OBJECT_DEFS.read_text(encoding="utf-8")
    harvesting_defs = HARVESTING_DEFS.read_text(encoding="utf-8")
    skill_guide = SKILL_GUIDE.read_text(encoding="utf-8")
    world_populator = WORLD_POPULATOR.read_text(encoding="utf-8")
    harvesting_plugin = HARVESTING_PLUGIN.read_text(encoding="utf-8")
    formulae = FORMULAE.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    scene = SCENE.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    entity_handler = ENTITY_HANDLER.read_text(encoding="utf-8")

    for name, object_id, produce_id, experience in LEGACY_HARVESTABLES:
        start = object_defs.index(f"<name>{name}</name>")
        definition = object_defs[start:object_defs.index("</GameObjectDef>", start)]
        require(definition, "<command2>Harvest</command2>", f"{name} must expose the Harvest action")
        require(
            harvesting_defs,
            f"<int>{object_id}</int>\n\t\t<ObjectHarvestingDef>",
            f"Object {object_id} is missing its Harvesting definition",
        )
        entry_start = harvesting_defs.index(f"<int>{object_id}</int>")
        entry = harvesting_defs[entry_start:harvesting_defs.index("</ObjectHarvestingDef>", entry_start)]
        require(entry, f"<prodId>{produce_id}</prodId>", f"Object {object_id} has the wrong produce")
        require(entry, f"<exp>{experience}</exp>", f"Object {object_id} has the wrong Harvesting XP")

    for object_id, minimum_tier, yield_baseline in OBJECT_TIERS_AND_YIELD_BASELINES:
        entry_start = harvesting_defs.index(f"<int>{object_id}</int>")
        entry = harvesting_defs[entry_start:harvesting_defs.index("</ObjectHarvestingDef>", entry_start)]
        require(
            entry,
            f"<minimumShearsTier>{minimum_tier}</minimumShearsTier>",
            f"Object {object_id} must retain its minimum shears tier",
        )
        require(
            entry,
            f"<yieldBaselineLevel>{yield_baseline}</yieldBaselineLevel>",
            f"Object {object_id} must retain its yield baseline",
        )

    for snippet in CLIP_REWARD_SNIPPETS:
        require(harvesting_plugin, snippet, f"Clip reward must retain its tier, yield baseline and XP: {snippet}")

    if "<command2>pick</command2>" in object_defs.lower():
        raise AssertionError("A generic scenery Pick action still bypasses Harvesting")
    for stale_client_label in ('"pick"', '"Pick Banana"', '"Pick pineapple"'):
        if stale_client_label in entity_handler:
            raise AssertionError(f"Client object definitions still expose legacy {stale_client_label} harvesting")
    require(entity_handler, '"Wheat", "nice ripe looking wheat", "WalkTo", "Harvest"', "Client wheat action must mirror Harvest")
    require(entity_handler, '"Banana tree", "A tree with nice ripe bananas growing on it", "WalkTo", "Harvest"', "Client banana action must mirror Harvest")
    require(entity_handler, '"Banana tree", "There are no bananas left on the tree", "WalkTo", "Examine"', "Client empty banana tree must not expose Harvest")
    require(entity_handler, '"Potato", "A potato plant", "WalkTo", "Harvest"', "Client potato action must mirror Harvest")
    require(entity_handler, 'Config.S_BATCH_PROGRESSION ? "Harvest" : "WalkTo"', "Client flax primary action must not use legacy Pick")
    require(entity_handler, 'Config.S_BATCH_PROGRESSION ? "Examine" : "Harvest"', "Client flax secondary action must mirror Harvest")
    require(entity_handler, '"Pineapple tree", "A tree with nice ripe pineapples growing on it", "WalkTo", "Harvest"', "Client pineapple action must mirror Harvest")
    require(entity_handler, '"Pineapple tree", "There are no pineapples left on the tree", "WalkTo", "Examine"', "Client empty pineapple tree must not expose Harvest")
    require(skill_guide, 'addHarvestProduce(29, 1, "Grain")', "Harvesting guide is missing grain")
    require(skill_guide, 'addHarvestProduce(675, 1, "Flax")', "Harvesting guide is missing flax")
    require(skill_guide, 'addHarvestProduce(422, 1, "Event Pumpkin (bonus XP)")', "Harvesting guide is missing event pumpkins")
    require(skill_guide, 'addHarvestProduce(933, 10, "Torstol (rare chance)")', "Harvesting guide is missing torstol")
    require(harvesting_plugin, "new ClippedProduce(ItemId.UNIDENTIFIED_TORSTOL.id(), 10, 70, 960)", "Harvesting herb table is missing torstol")
    require(formulae, "ItemId.UNIDENTIFIED_TORSTOL.id()", "Harvesting herb roll table is missing torstol")
    require(formulae, "32, 25, 19, 14, 11, 8, 6, 5, 4, 3, 1", "Harvesting herb roll weights must include rare torstol")
    require(harvesting_defs, "<int>1327</int>", "Regular event pumpkins are missing a Harvesting definition")
    require(harvesting_defs, "<prodId>422</prodId>\n\t\t\t<exp>360</exp>", "Event pumpkins must grant their bonus Harvesting XP")

    if "prodId == ItemId.TOMATO.id()" in harvesting_plugin:
        raise AssertionError("Tomatoes must use the same damaged-ground depleted visual as onions")
    require(packet_handler, "def.getType() == 0 && isHarvestCommand(def.getCommand1())", "Small harvestable click-bound override is missing")
    require(packet_handler, "model.setPickBoundsScale(3);", "Small harvestable click bounds must be expanded")
    require(mudclient, "applyExpandedGameObjectPickBounds(objectID, model);", "Region-shifted objects must keep expanded pick bounds")
    require(scene, "var6.getPickBoundsScale() > 1", "Scene picking does not honor expanded model bounds")
    require(scene, "MIN_EXPANDED_PICK_PADDING_PIXELS", "Expanded pick bounds need a minimum screen-space padding")

    for item in ("CABBAGE", "CADAVABERRIES", "GRAPES", "UNIDENTIFIED_GUAM_LEAF", "REDBERRIES",
                 "ONION", "TOMATO", "PUMPKIN", "SNAPE_GRASS", "WHITE_BERRIES", "SEAWEED",
                 "DWELLBERRIES", "JANGERBERRIES"):
        require(world_populator, f"itemId == ItemId.{item}.id()", f"Ground {item} must route through Harvesting")
    for quest_item in ("DOOGLE_LEAVES", "NIGHTSHADE"):
        if f"itemId == ItemId.{quest_item}.id()" in world_populator:
            raise AssertionError(f"Quest item {quest_item} must remain a ground pickup")

    print("legacy crop harvesting checks passed")


if __name__ == "__main__":
    main()
