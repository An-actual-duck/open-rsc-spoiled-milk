#!/usr/bin/env python3
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
SERVER_SPELLS = ROOT / "server/conf/server/defs/SpellDef.xml"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
MAGIC_COMBAT_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/MagicCombatEvent.java"
ENCHANTING_EFFECTS = ROOT / "server/src/com/openrsc/server/content/EnchantingItemEffects.java"
ENCHANTING_PLUGIN = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting/Enchanting.java"
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def strip_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    return "\n".join(line.split("//", 1)[0] for line in source.splitlines())


def parse_server_spells() -> list[dict]:
    root = ET.parse(SERVER_SPELLS).getroot()
    spells: list[dict] = []
    for spell in root.findall("SpellDef"):
        runes: dict[int, int] = {}
        required = spell.find("requiredRunes")
        if required is not None:
            for entry in required.findall("entry"):
                ints = [int(node.text or "0") for node in entry.findall("int")]
                if len(ints) == 2:
                    runes[ints[0]] = ints[1]
        spells.append(
            {
                "name": spell.findtext("name", ""),
                "level": int(spell.findtext("reqLevel", "0")),
                "type": int(spell.findtext("type", "0")),
                "rune_count": int(spell.findtext("runeCount", "0")),
                "runes": runes,
            }
        )
    return spells


def parse_client_spells() -> list[dict]:
    source = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    start = source.index("private static void loadSpellDefinitions()")
    end = source.index("private static void loadDoorDefinitions()", start)
    body = source[start:end]

    spells: list[dict] = []
    runes: dict[int, int] = {}
    for statement in re.finditer(
        r"runes\.clear\(\)|runes\.put\((\d+),\s*(\d+)\);|spells\.add\(new SpellDef\(\"([^\"]+)\".*?,\s*(\d+),\s*(\d+),\s*(\d+),",
        body,
        flags=re.S,
    ):
        if statement.group(0) == "runes.clear()":
            runes = {}
            continue
        if statement.group(1) is not None:
            runes[int(statement.group(1))] = int(statement.group(2))
            continue
        spells.append(
            {
                "name": statement.group(3),
                "level": int(statement.group(4)),
                "type": int(statement.group(5)),
                "rune_count": int(statement.group(6)),
                "runes": dict(runes),
            }
        )
    return spells


def ensure_spell_costs_match() -> None:
    server_spells = parse_server_spells()
    client_spells = parse_client_spells()
    require(len(server_spells) == len(client_spells), "Client/server spell counts differ")
    for index, (server, client) in enumerate(zip(server_spells, client_spells)):
        for key in ("name", "level", "type", "rune_count", "runes"):
            require(
                server[key] == client[key],
                f"Spell {index} {key} differs: server={server[key]!r} client={client[key]!r}",
            )
        require(
            server["rune_count"] == len(server["runes"]),
            f"{server['name']} runeCount does not match required rune entries",
        )

    expected_god_costs = {
        "Eye of Guthix": {35: 3, 619: 1, 40: 2},
        "Saradomin strike": {35: 3, 619: 1, 825: 2},
        "Void of Zamorak": {35: 3, 619: 1, 41: 2},
        "Zamorak's Apocolypse": {35: 6, 619: 2, 41: 4},
        "Saradomin Soul Slash": {35: 6, 619: 2, 825: 4},
        "Claw of Guthix": {35: 6, 619: 2, 40: 4},
    }
    by_name = {spell["name"]: spell for spell in server_spells}
    for name, runes in expected_god_costs.items():
        require(name in by_name, f"Missing god spell definition: {name}")
        require(by_name[name]["runes"] == runes, f"{name} rune costs drifted")


def ensure_spell_runtime_cost_path() -> None:
    source = strip_comments(SPELL_HANDLER.read_text(encoding="utf-8"))
    auto_cast_source = strip_comments(MAGIC_COMBAT_EVENT.read_text(encoding="utf-8"))
    require(
        "Set<Entry<Integer, Integer>> runesToConsume = new HashSet<>()" in source,
        "Spell rune checks should produce an explicit consume set",
    )
    require(
        "getRuneNegationChance(player, equippedStaff, e.getKey())" in source,
        "Spell rune costs should use cloth/staff rune preservation",
    )
    require(
        "player.getCarriedItems().remove(new Item(r.getKey(), r.getValue()))" in source,
        "Spell rune costs should be removed from the consume set",
    )
    require(
        "SpellHandler.hasRequiredRunesForAutoCast(player, spellDef)" in auto_cast_source,
        "Auto-cast should check rune availability before casting",
    )
    require(
        "return true;\n\t}" in source[source.index("private boolean spellSuccessCheck") : source.index("public void process")],
        "Magic spell success checks should remain non-random",
    )


def require_regex(source: str, pattern: str, message: str) -> None:
    require(re.search(pattern, source, flags=re.S) is not None, message)


def ensure_enchanting_costs_and_gates() -> None:
    effects = strip_comments(ENCHANTING_EFFECTS.read_text(encoding="utf-8"))
    plugin = strip_comments(ENCHANTING_PLUGIN.read_text(encoding="utf-8"))

    require_regex(effects, r"public static int getRuneCostForTier\(final int tier\) \{\s*return tier > 0 \? tier \* 50 : -1;\s*\}", "Jewelry rune cost should be 50 altar runes per gem tier")
    require_regex(effects, r"public static int getStaffRuneCost\(final int tier\) \{\s*return tier > 0 \? tier \* 200 : -1;\s*\}", "Staff attunement should cost 200 altar runes per staff tier")
    require_regex(effects, r"public static int getWoolRobeRuneCost\(final int tier\) \{\s*return tier > 0 \? tier \* 100 : -1;\s*\}", "Cloth upgrade rune cost should be 100 altar runes per target tier")
    require("getStaffCosmicCost" not in effects, "Normal staff attunement should no longer require cosmic runes")
    require("WOOL_ROBE_TIER_BARS" not in plugin, "Cloth upgrades should no longer consume metal bars")
    require("getWoolRobeTierBar" not in plugin, "Cloth upgrades should no longer look up metal bars")
    require("ItemId.TIN_BAR" not in plugin, "Cloth upgrades should not keep the old metal-bar cost list")

    for snippet in (
        "final int altarLevelRequirement = EnchantingItemEffects.getAltarLevelRequirement(altarId);",
        "player.getSkills().getLevel(Skill.RUNECRAFT.id()) < altarLevelRequirement",
        "EnchantingItemEffects.getStaffEnchantingRequirementForTier(tier)",
        "EnchantingItemEffects.getJewelryEnchantingRequirementForTier(tier)",
        "EnchantingItemEffects.getTemporaryEnchantingRequirementForTier(nextTier)",
        "This robe is already bound to another altar.",
        "EnchantingItemEffects.getStaffRuneCost(tier)",
        "EnchantingItemEffects.getWoolRobeRuneCost(tier)",
    ):
        require(snippet in plugin, f"Enchanting plugin missing gate/cost guard: {snippet}")

    expected_jewelry_reqs = {
        1: 8,
        2: 18,
        3: 32,
        4: 48,
        5: 58,
    }
    for tier, level in expected_jewelry_reqs.items():
        require_regex(
            effects,
            rf"case {tier}:\s*return {level};",
            f"Jewelry tier {tier} should require Enchanting level {level}",
        )

    expected_altars = {
        "AIR_ALTAR": 1,
        "WATER_ALTAR": 1,
        "EARTH_ALTAR": 1,
        "FIRE_ALTAR": 1,
        "LIFE_ALTAR": 1,
        "MIND_ALTAR": 8,
        "BODY_ALTAR": 15,
        "CHAOS_ALTAR": 22,
        "COSMIC_ALTAR": 30,
        "NATURE_ALTAR": 38,
        "LAW_ALTAR": 46,
        "DEATH_ALTAR": 54,
        "SOUL_ALTAR": 62,
        "BLOOD_ALTAR": 70,
    }
    altar_level_method = effects[
        effects.index("public static int getAltarLevelRequirement") : effects.index("public static int getStaffRuneCost")
    ]
    for altar, level in expected_altars.items():
        require(altar in altar_level_method, f"{altar} missing from altar level requirements")
        require(f"return {level};" in altar_level_method, f"Altar level {level} missing from altar requirement method")


def main() -> int:
    ensure_spell_costs_match()
    ensure_spell_runtime_cost_path()
    ensure_enchanting_costs_and_gates()
    print("PASS: spell and enchanting costs validated")
    print("Spell definitions compared: client/server rune costs match")
    print("Enchantment costs validated: jewelry, staves, and cloth upgrades")
    return 0


if __name__ == "__main__":
    sys.exit(main())
