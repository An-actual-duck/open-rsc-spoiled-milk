#!/usr/bin/env python3
"""Validate MyWorld fishing rod-tier implementation guardrails."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
FISHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/fishing/Fishing.java"
FLETCHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/fletching/Fletching.java"
ENTITY_HANDLER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
FORMULAE = ROOT / "server/src/com/openrsc/server/util/rsc/Formulae.java"
INV_USE_ON_ITEM = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvUseOnItem.java"
GERRANT = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/portsarim/GerrantsFishingGear.java"
FISHING_GUILD = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/hemenster/FishingGuildShop.java"
HARRY = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/catherby/HarrysFishingShack.java"
FERNAHEI = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/shilo/Fernahei.java"
DRAGON_SLAYER = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/quests/free/DragonSlayer.java"
FISHING_CONTEST = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/quests/members/FishingContest.java"
WORK_ITEMS = ROOT / "docs/myworld/work-items.md"
FISHING_PLAN = ROOT / "docs/myworld/fishing-rework-plan.md"


ROD_IDS = (
    "ItemId.FISHING_ROD.id()",
    "ItemId.PINE_FISHING_ROD.id()",
    "ItemId.OAK_FISHING_ROD.id()",
    "ItemId.WILLOW_FISHING_ROD.id()",
    "ItemId.PALM_FISHING_ROD.id()",
    "ItemId.MAPLE_FISHING_ROD.id()",
    "ItemId.YEW_FISHING_ROD.id()",
    "ItemId.EBONY_FISHING_ROD.id()",
    "ItemId.MAGIC_FISHING_ROD.id()",
    "ItemId.BLOOD_FISHING_ROD.id()",
)

LOW_SHOP_RODS = ROD_IDS[:6]
HIGH_SHOP_RODS = ROD_IDS[6:]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_snippets(path: Path, snippets: tuple[str, ...], label: str) -> None:
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        if snippet not in text:
            fail(f"{label} missing expected snippet: {snippet}")


def require_rod_runtime() -> None:
    fishing_text = FISHING.read_text(encoding="utf-8")
    entity_text = ENTITY_HANDLER.read_text(encoding="utf-8")
    formulae_text = FORMULAE.read_text(encoding="utf-8")

    require_snippets(FISHING, ROD_IDS, "Fishing rod ladder")
    require_snippets(FISHING, (
        "private static final int[] MYWORLD_ROD_LEVELS = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};",
        "private static final int MYWORLD_ROD_CATCH_TIER_SPAN = 3;",
        "private static final FishingLocation[] MYWORLD_FISHING_LOCATIONS",
        "private FishingLocation(int minX, int minY, int maxX, int maxY, FishEntry[] fish)",
        "private FishingLocation getMyWorldFishingLocation(GameObject object)",
        "private List<FishEntry> getEligibleFish(FishingLocation location, int rodTier)",
        "private int getMinimumFishingRodCatchTier(int rodTier)",
        "fish.tier >= minimumTier && fish.tier <= rodTier",
        '"This rod isn\'t appropriate for any fish available here"',
        "Formulae.calcGatheringEffectiveLevel(player.getSkills().getLevel(Skill.FISHING.id()), rodTier)",
        "private FishEntry rollMyWorldFish(Player player, List<FishEntry> eligibleFish, int rodTier)",
        "private String getMyWorldExamineText(EntityHandler entityHandler, FishingLocation location)",
        "private boolean isLegacyFishingCommand(String command)",
    ), "Fishing rod/location runtime")

    for rod_id in ROD_IDS:
        if f"configureEquippableTool({rod_id}, Skill.FISHING.id()" not in entity_text:
            fail(f"Fishing rod is not configured as an equipable Fishing tool: {rod_id}")
        if rod_id not in formulae_text:
            fail(f"Fishing rod is missing from shared fishing tool IDs: {rod_id}")


def require_rod_crafting() -> None:
    require_snippets(FLETCHING, ROD_IDS, "Fishing rod crafting")
    require_snippets(FLETCHING, (
        "private static final int[] fishingRodCraftingLevels = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};",
        "private int getFishingRodResultId(final int logId)",
        '"You carefully shape the wood into a fishing rod"',
    ), "Fishing rod crafting")


def require_legacy_tool_guard() -> None:
    require_snippets(FISHING, (
        "if (player.getConfig().WANT_MYWORLD && isMyWorldFishingSpot(object)) {",
        "private boolean isRetiredNormalFishingTool(int itemId)",
        "return itemId == ItemId.NET.id()",
        "|| itemId == ItemId.BIG_NET.id()",
        "|| itemId == ItemId.FLY_FISHING_ROD.id()",
        "|| itemId == ItemId.OILY_FISHING_ROD.id()",
        "|| itemId == ItemId.LOBSTER_POT.id()",
        "|| itemId == ItemId.HARPOON.id();",
        "else if (isRetiredNormalFishingTool(item.getCatalogId())) {",
        '"You need to equip a fishing rod to fish here."',
        "if (player.getConfig().WANT_MYWORLD && isMyWorldFishingSpot(obj)\n\t\t\t&& (isFishingRod(item.getCatalogId()) || isRetiredNormalFishingTool(item.getCatalogId())))",
    ), "MyWorld legacy fishing tool guard")


def require_fishing_shops_updated() -> None:
	gerrant_text = GERRANT.read_text(encoding="utf-8")
	gerrant_shop_text = gerrant_text.split("@Override\n\tpublic boolean blockTalkNpc", 1)[0]
	guild_text = FISHING_GUILD.read_text(encoding="utf-8")
	harry_text = HARRY.read_text(encoding="utf-8")
	harry_shop_text = harry_text.split("@Override\n\tpublic boolean blockTalkNpc", 1)[0]
	fernahei_text = FERNAHEI.read_text(encoding="utf-8")
	fernahei_shop_text = fernahei_text.split("@Override\n\tpublic void onTalkNpc", 1)[0]
	low_shop_texts = {
		"Gerrant": gerrant_shop_text,
		"Harry": harry_shop_text,
		"Fernahei": fernahei_shop_text,
	}

	for shop_name, shop_text in low_shop_texts.items():
		for rod_id in LOW_SHOP_RODS:
			if rod_id not in shop_text:
				fail(f"{shop_name} should sell low/mid-tier rod: {rod_id}")
		for rod_id in HIGH_SHOP_RODS:
			if rod_id in shop_text:
				fail(f"{shop_name} should not sell Fishing Guild rod tier: {rod_id}")

	for rod_id in ROD_IDS:
		if rod_id not in guild_text:
			fail(f"Fishing Guild should sell full rod ladder item: {rod_id}")

	# Quest exceptions stay available while their quest paths still require them.
	for quest_exception in ("ItemId.LOBSTER_POT.id()", "ItemId.FISHING_BAIT.id()"):
		if quest_exception not in gerrant_shop_text:
			fail(f"Gerrant should keep explicit quest exception item: {quest_exception}")

	for retired in (
		"ItemId.NET.id()",
		"ItemId.BIG_NET.id()",
		"ItemId.FLY_FISHING_ROD.id()",
		"ItemId.OILY_FISHING_ROD.id()",
		"ItemId.HARPOON.id()",
		"ItemId.FEATHER.id()",
	):
		for shop_name, shop_text in {
			"Gerrant": gerrant_shop_text,
			"Harry": harry_shop_text,
			"Fernahei": fernahei_shop_text,
			"Fishing Guild": guild_text,
		}.items():
			if retired in shop_text:
				fail(f"{shop_name} still sells retired normal Fishing tool/source: {retired}")

	for guild_legacy in ("ItemId.FISHING_BAIT.id()", "ItemId.LOBSTER_POT.id()"):
		if guild_legacy in guild_text:
			fail(f"Fishing Guild should not sell quest-only legacy Fishing item: {guild_legacy}")
		if guild_legacy in harry_shop_text or guild_legacy in fernahei_shop_text:
			fail(f"Only Gerrant should keep current quest-only legacy Fishing item: {guild_legacy}")


def require_quest_exceptions_documented() -> None:
    require_snippets(DRAGON_SLAYER, ("ItemId.LOBSTER_POT.id()",), "Dragon Slayer fishing legacy exception")
    require_snippets(FISHING_CONTEST, (
        "ItemId.FISHING_ROD.id()",
        "ItemId.FISHING_BAIT.id()",
    ), "Fishing Contest fishing legacy exception")
    require_snippets(GERRANT, (
        "if (player.getConfig().WANT_MYWORLD) {",
        "Bring at least a Magic Fishing Rod to a lava fishing spot",
    ), "Hero's Quest lava eel MyWorld guidance")
    require_snippets(INV_USE_ON_ITEM, (
        "if (player.getConfig().WANT_MYWORLD) {",
        '"MyWorld fishing uses stronger fishing rods instead"',
    ), "Oily fishing rod MyWorld retirement")
    require_snippets(WORK_ITEMS, (
        "`Fishing Contest` still explicitly uses the baseline `Fishing Rod` plus",
        "`Dragon Slayer` still explicitly consumes a `Lobster Pot`",
        "`Hero's Quest` lava-eel guidance points MyWorld players at the",
    ), "Fishing work-items docs")
    require_snippets(FISHING_PLAN, (
        "The first gameplay pass is in place",
        "`Fishing Contest` still requires the baseline `Fishing Rod`",
        "`Dragon Slayer` still consumes a `Lobster Pot`",
        "`Hero's Quest` lava-eel fishing now uses the tier `9` `Magic Fishing Rod`",
    ), "Fishing plan docs")


def main() -> None:
    require_rod_runtime()
    require_rod_crafting()
    require_legacy_tool_guard()
    require_fishing_shops_updated()
    require_quest_exceptions_documented()
    print("PASS: fishing rod-tier implementation guardrails validated")


if __name__ == "__main__":
    main()
