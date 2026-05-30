#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
SMITHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java"

EXPECTED_MODERN_WEAPON_OPTIONS = [0, 2, 3, 4, 5, 6, 7, 21, 8, 9]
EXPECTED_MODERN_ARMOR_OPTIONS = [0, 1, 2, 3, 4, 5, 6]
REMOVED_RANGED_MOULD_OPTIONS = [1, 18, 20]


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def extract_method(text: str, method_name: str) -> str:
    match = re.search(rf"\n\tprivate .* {method_name}\([^)]*\) \{{", text)
    if not match:
        fail(f"Could not find method {method_name}")
    start = match.start()
    brace_start = text.index("{", match.start())
    depth = 0
    for index in range(brace_start, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[start:index + 1]
    fail(f"Could not parse method {method_name}")


def ensure_modern_production_grid(text: str) -> None:
    create_session = extract_method(text, "createSmithingProductionSession")
    for option in EXPECTED_MODERN_WEAPON_OPTIONS:
        snippet = f"addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), {option});"
        if snippet not in create_session:
            fail(f"Modern smithing production UI missing weapon/tool option {option}")
    for option in EXPECTED_MODERN_ARMOR_OPTIONS:
        snippet = f"addModernArmorRecipe(recipes, player, item.getCatalogId(), {option});"
        if snippet not in create_session:
            fail(f"Modern smithing production UI missing armor option {option}")
    for option in REMOVED_RANGED_MOULD_OPTIONS:
        snippet = f"addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), {option});"
        if snippet in create_session:
            fail(f"Modern smithing production UI should not include ranged mould option {option}")


def ensure_production_start_accepts_grid_recipes(text: str) -> None:
    get_def = extract_method(text, "getProductionRecipeDef")
    expected_array = "int[] weaponIds = {0, 2, 3, 4, 5, 6, 7, 21, 8, 9, 10};"
    if expected_array not in get_def:
        fail("Modern smithing production start should accept all visible weapon/tool recipes, including shears and maces")
    for option in REMOVED_RANGED_MOULD_OPTIONS:
        if re.search(rf"\b{option}\b", expected_array):
            fail(f"Production start should not accept removed ranged mould option {option}")


def ensure_zero_id_outputs_are_valid(text: str) -> None:
    if text.count("return def.itemID >= 0 ? def : null;") < 2:
        fail("Smithing recipe helpers must allow item id 0, needed for iron mace")
    if "return def.itemID > 0 ? def : null;" in text:
        fail("Smithing recipe helpers still reject item id 0")
    if "case IRON_BAR:\n\t\t\t\treturn ItemId.IRON_MACE.id();" not in text:
        fail("Modern mace mapping should include iron mace")


def ensure_paladin_shields_are_smithable(text: str) -> None:
    if "private int getModernPaladinShieldId(int barId)" not in text:
        fail("Modern smithing should expose the paladin shield line")
    for snippet in (
        "case TIN_BAR:\n\t\t\t\treturn ItemId.TIN_KITE_SHIELD.id();",
        "case IRON_BAR:\n\t\t\t\treturn ItemId.IRON_KITE_SHIELD.id();",
        "case RUNITE_BAR:\n\t\t\t\treturn ItemId.RUNE_KITE_SHIELD.id();",
    ):
        if snippet not in text:
            fail(f"Modern paladin shield mapping missing expected snippet: {snippet}")
    if '"Paladin Shield (3 bars)"' not in text:
        fail("Legacy smithing menu should call the kite replacement Paladin Shield")


def ensure_intentional_ranged_outputs_are_crafting_only(text: str) -> None:
    forbidden = (
        "getModernArrowHeadsId",
        "getModernDartTipsId",
        "case 18:",
        "case 20:",
    )
    for snippet in forbidden:
        if snippet in text:
            fail(f"Ranged mould output should stay out of Smithing: {snippet}")


def main() -> None:
    text = SMITHING.read_text(encoding="utf-8")
    ensure_modern_production_grid(text)
    ensure_production_start_accepts_grid_recipes(text)
    ensure_zero_id_outputs_are_valid(text)
    ensure_paladin_shields_are_smithable(text)
    ensure_intentional_ranged_outputs_are_crafting_only(text)
    print("PASS: smithing production coverage validated")


if __name__ == "__main__":
    main()
