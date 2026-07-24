#!/usr/bin/env python3
"""Validate Mining geodes and retired gem-seed generation."""

from pathlib import Path
import json
import math
import sys

ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
CLIENT_ITEMS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
EXTERNAL_ASSET_LOADER = ROOT / "Client_Base/src/orsc/ClientExternalAssetLoader.java"
SERVER_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
MINING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java"
WOODCUTTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/woodcutting/Woodcutting.java"
RESOURCE_SEEDS = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/gathering/ResourceSeeds.java"
GEODES = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/gathering/Geodes.java"
GEODE_SPRITE = ROOT / "dev/myworld/assets/sprites/items/inventory-ground/geode.png"
FORMULAE = ROOT / "server/src/com/openrsc/server/util/rsc/Formulae.java"
HARVESTING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java"
MINING_DEFS = ROOT / "server/conf/server/defs/extras/ObjectMining.xml"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def forbid(text: str, snippet: str, label: str) -> None:
    if snippet in text:
        fail(f"{label} should not contain retired snippet: {snippet}")


def main() -> None:
    item_id = ITEM_ID.read_text(encoding="utf-8")
    for snippet in (
        "KEY_HALF_SEED(3176)",
        "SMALL_GEODE(3177)",
        "STANDARD_GEODE(3178)",
        "LARGE_GEODE(3179)",
        "HUGE_GEODE(3180)",
        "public static final int maxCustom = 3292;",
    ):
        require(item_id, snippet, "ItemId.java")

    client_items = CLIENT_ITEMS.read_text(encoding="utf-8")
    for snippet in (
        'addResourceSeedDefinition("Key half seed"',
        'setCustomItemDefinition(3177, new ItemDef("Small geode"',
        'setCustomItemDefinition(3178, new ItemDef("Standard geode"',
        'setCustomItemDefinition(3179, new ItemDef("Large geode"',
        'setCustomItemDefinition(3180, new ItemDef("Huge geode"',
        '"external-png:geode@14x14"',
        '"external-png:geode@18x18"',
        '"external-png:geode@24x24"',
        '"external-png:geode@30x30"',
    ):
        require(client_items, snippet, "client item definitions")

    client = CLIENT.read_text(encoding="utf-8")
    for snippet in (
        'return new String[]{"Select mining focus", "Just the ore", "A few geodes", "Plenty of geodes", "Lots of geodes"};',
    ):
        require(client, snippet, "mudclient geode support")

    external_asset_loader = EXTERNAL_ASSET_LOADER.read_text(encoding="utf-8")
    for snippet in (
        "assetSpec.indexOf('@')",
        "findFirstFile(ITEM_ASSET_PATHS, fileName), targetWidth, targetHeight",
    ):
        require(external_asset_loader, snippet, "external asset loader geode support")

    server_items = json.loads(SERVER_ITEMS.read_text(encoding="utf-8"))["items"]
    names_by_id = {item["id"]: item["name"] for item in server_items}
    expected_names = {
        3176: "Key half seed",
        3177: "Small geode",
        3178: "Standard geode",
        3179: "Large geode",
        3180: "Huge geode",
    }
    for item_id, name in expected_names.items():
        if names_by_id.get(item_id) != name:
            fail(f"ItemDefsCustom missing {item_id}: {name}")

    mining = MINING.read_text(encoding="utf-8")
    for snippet in (
        "MYWORLD_MINING_GEODE_REWARDS",
        "private boolean maybeAwardMyWorldMiningGeode(Player player, GameObject rock, int nodeRequiredLevel)",
        "rollMyWorldMiningGeode(nodeRequiredLevel)",
        "maybeAwardMyWorldMiningGeode(player, rock, def.getReqLevel())",
        "private static int[] getGeodeSizeWeights(int nodeRequiredLevel)",
        "int hugeWeight = 1 + (progress * 19 / 98);",
        "int largeWeight = 9 + (progress * 21 / 98);",
        "int standardWeight = 20 + (progress * 5 / 98);",
        "int smallWeight = 100 - standardWeight - largeWeight - hugeWeight;",
        "Your cosmic amulet glimmers and another geode appears.",
        "Formulae.gatheringSideRewardChanceForFocus(getMiningFocus(player), MYWORLD_GEODE_REWARD_BASE_CHANCE)",
    ):
        require(mining, snippet, "Mining geode reward")
    forbid(mining, "maybeAwardMyWorldMiningGem", "Mining geode reward")
    forbid(mining, "getRandomGemChance", "Mining geode reward")

    woodcutting = WOODCUTTING.read_text(encoding="utf-8")
    require(woodcutting, "new SeedReward(ItemId.KEY_HALF_SEED.id(), 10, 1)", "Woodcutting key-half seed reward")
    require(
        woodcutting,
        "Formulae.gatheringSideRewardChanceForFocus(player.getCombatStyle(), MYWORLD_SEED_REWARD_BASE_CHANCE)",
        "Woodcutting seed reward chance",
    )
    for retired in (
        "new SeedReward(ItemId.SAPPHIRE_SEED.id()",
        "new SeedReward(ItemId.EMERALD_SEED.id()",
        "new SeedReward(ItemId.RUBY_SEED.id()",
        "new SeedReward(ItemId.DIAMOND_SEED.id()",
        "new SeedReward(ItemId.DRAGONSTONE_SEED.id()",
    ):
        forbid(woodcutting, retired, "Woodcutting retired gem seeds")

    resource_seeds = RESOURCE_SEEDS.read_text(encoding="utf-8")
    require(resource_seeds, "ItemId.KEY_HALF_SEED.id()", "ResourceSeeds key-half tree")
    require(resource_seeds, "YieldType.KEY_HALF", "ResourceSeeds key-half yield")
    require(resource_seeds, "You harvest a key half.", "ResourceSeeds key-half message")
    for retired in (
        "ItemId.SAPPHIRE_SEED.id(), SceneryId.RESOURCE_TREE.id()",
        "ItemId.EMERALD_SEED.id(), SceneryId.RESOURCE_TREE.id()",
        "ItemId.RUBY_SEED.id(), SceneryId.RESOURCE_TREE.id()",
        "ItemId.DIAMOND_SEED.id(), SceneryId.RESOURCE_TREE.id()",
        "ItemId.DRAGONSTONE_SEED.id(), SceneryId.RESOURCE_TREE.id()",
    ):
        forbid(resource_seeds, retired, "ResourceSeeds retired gem trees")

    harvesting = HARVESTING.read_text(encoding="utf-8")
    require(
        harvesting,
        "Formulae.gatheringSideRewardChanceForFocus(player.getCombatStyle(), MYWORLD_SEED_REWARD_BASE_CHANCE)",
        "Harvesting seed reward chance",
    )

    formulae = FORMULAE.read_text(encoding="utf-8")
    for snippet in (
        "public static double gatheringSideRewardChanceForFocus(int focus, double baseChance)",
        "case Skills.CONTROLLED_MODE:",
        "return 0.0D;",
        "case Skills.ACCURATE_MODE:",
        "return baseChance * 1.5D;",
        "case Skills.DEFENSIVE_MODE:",
        "return baseChance * 2.0D;",
        "case Skills.AGGRESSIVE_MODE:",
        "return baseChance;",
    ):
        require(formulae, snippet, "shared gathering side-reward chance")

    base_chance = 1.0 / 50.0
    highest_focus_chance = base_chance * 2.0
    if not math.isclose(highest_focus_chance, 1.0 / 25.0):
        fail("Highest geode/seed focus should be exactly 1 in 25")

    def geode_weights(level: int) -> tuple[int, int, int, int]:
        level = max(1, min(99, level))
        progress = level - 1
        huge = 1 + progress * 19 // 98
        large = 9 + progress * 21 // 98
        standard = 20 + progress * 5 // 98
        small = 100 - standard - large - huge
        return small, standard, large, huge

    if geode_weights(1) != (70, 20, 9, 1):
        fail(f"Level-1 geode weights drifted: {geode_weights(1)}")
    previous = geode_weights(1)
    for level in range(1, 100):
        weights = geode_weights(level)
        if sum(weights) != 100 or min(weights) < 1:
            fail(f"Geode weights must total 100 with every size possible at level {level}: {weights}")
        if level > 1 and (weights[2] + weights[3]) < (previous[2] + previous[3]):
            fail(f"Higher-level nodes should not reduce large/huge geode odds at level {level}")
        previous = weights

    mining_defs = MINING_DEFS.read_text(encoding="utf-8")
    for node_name in ("Stone", "Clay", "Silver", "Coal", "Gold"):
        require(mining_defs, f"<!-- {node_name} -->", "geode-eligible mining definitions")

    geodes = GEODES.read_text(encoding="utf-8")
    for snippet in (
        "Use a chisel to crack this open.",
        "You crack the geode open...",
        '"There was " + reward.description + " inside!"',
        "private static final int[] STANDARD_GEM_WEIGHTS = {35, 18, 12, 6};",
        "SMALL(ItemId.SMALL_GEODE.id(), 1500, 4000, 144, 360",
        "STANDARD(ItemId.STANDARD_GEODE.id(), 4000, 9000, 960, 2160",
        "LARGE(ItemId.LARGE_GEODE.id(), 9000, 18000, 4560, 7800",
        "HUGE(ItemId.HUGE_GEODE.id(), 18000, 35000, 9000, 15000",
        "startbatchunlimited();",
        "ActionSender.sendActionProgressBar(player, ItemId.CHISEL.id(), 3);",
        "delay(3);",
        "batchOpenGeode(player, size);",
        "private static int getRuneQuantity(GeodeSize size, int runeId)",
        "int hugeAverage = 400 - ((Math.max(1, altarLevel) - 1) * 360 / 69);",
        "case BLOOD_RUNE:",
        "return 70;",
        "private static int getGemQuantity(GeodeSize size, int gemId)",
        "case UNCUT_SAPPHIRE:",
        "baseQuantity = 10;",
        "case UNCUT_DIAMOND:",
        "baseQuantity = 3;",
        "ItemId.RUNE_STONE.id()",
        "ItemId.TOOTH_KEY_HALF.id()",
        "ItemId.LOOP_KEY_HALF.id()",
        "KEY_HALVES(new int[] {0, 1, 1, 1})",
    ):
        require(geodes, snippet, "Geodes handler")
    for excluded_gem in (
        "ItemId.UNCUT_OPAL.id()",
        "ItemId.UNCUT_JADE.id()",
        "ItemId.UNCUT_RED_TOPAZ.id()",
        "ItemId.UNCUT_DRAGONSTONE.id()",
        "ItemId.DRAGONSTONE.id()",
    ):
        forbid(geodes, excluded_gem, "Geodes jewelry-gem rewards")

    if not GEODE_SPRITE.is_file():
        fail(f"Missing active geode sprite: {GEODE_SPRITE}")

    print("PASS: Mining geode transition and retired gem seed generation look correct")


if __name__ == "__main__":
    main()
