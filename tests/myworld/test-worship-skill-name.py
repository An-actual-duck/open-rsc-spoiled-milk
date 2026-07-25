#!/usr/bin/env python3
"""Guard the Worship display rename and its Prayer compatibility boundary."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def main() -> None:
    skill_def = read("server/src/com/openrsc/server/external/SkillDef.java")
    skills = read("server/src/com/openrsc/server/constants/Skills.java")
    action_sender = read("server/src/com/openrsc/server/net/rsc/ActionSender.java")
    stat_struct = read(
        "server/src/com/openrsc/server/net/rsc/struct/outgoing/StatInfoStruct.java"
    )
    mysql_queries = read(
        "server/src/com/openrsc/server/database/impl/mysql/MySqlQueries.java"
    )
    mysql_database = read(
        "server/src/com/openrsc/server/database/impl/mysql/MySqlGameDatabase.java"
    )
    player = read("server/src/com/openrsc/server/model/entity/player/Player.java")
    item_ids = read("server/src/com/openrsc/server/constants/ItemId.java")
    client = read("Client_Base/src/orsc/mudclient.java")
    guide = read(
        "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
    )
    religious_dialogue = read(
        "server/plugins/com/openrsc/server/plugins/authentic/npcs/lumbridge/Urhney.java"
    )
    point_interface = read(
        "Client_Base/src/com/openrsc/interfaces/misc/PointInterface.java"
    )
    bank_tags = read(
        "Client_Base/src/com/openrsc/interfaces/misc/BankItemTag.java"
    )
    packet_handler = read("Client_Base/src/orsc/PacketHandler.java")
    client_items = read(
        "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
    )
    server_items = read("server/conf/server/defs/ItemDefsCustom.json")
    compatibility = read("docs/myworld/info/worship-skill-compatibility.md")

    # Presentation: every primary skill surface must use Worship.
    require('addSkill("Worship");' in client, "stats and highscores must label skill 5 Worship")
    require('addSkill("Prayer");' not in client, "client skill list must not retain the old display")
    require(
        'skillGuideChosen.equalsIgnoreCase("Worship")' in client,
        "client must construct the Worship guide tabs",
    )
    require(
        'mc.getSkillGuideChosen().equals("Worship")' in guide,
        "guide rendering must recognize Worship",
    )
    require(
        '"Ranged", "Worship", "Magic"' in packet_handler,
        "training presence must identify Worship",
    )
    require(
        'mc.getSkillGuideChosen() + " Hiscores"' in guide,
        "hiscores must use the Worship guide selection as their title",
    )
    require(
        '"Ranged", "Worship", "Magic"' in point_interface,
        "skill point selectors must identify Worship",
    )
    require(
        'PRAYER("Worship", Group.SKILLS)' in bank_tags,
        "bank skill filter must display Worship",
    )
    require("31 Worship" in client and "42 Worship" in client,
            "quest requirements must use Worship")
    require("Worship experience" in client,
            "quest rewards must use Worship experience")
    require("Worship XP" in guide and "Worship and Magic" in guide,
            "skill guide XP and level requirements must use Worship")

    # Server: display alias is separate from the stable authoritative name.
    require("String displayName;" in skill_def and "getDisplayName()" in skill_def,
            "SkillDef must own a presentation name")
    require(
        'new SkillDef("Prayer", "Prayer", "Worship", 1, 99' in skills,
        "skill 5 must retain Prayer internally and expose Worship",
    )
    require("getSkillDisplayName(int skillIndex)" in skills,
            "server must expose the skill presentation name")
    require("skill.getDisplayName().equalsIgnoreCase(skillName)" in skills,
            "skill selectors must accept Worship")
    require('"Worship cape"' in client_items and '"Worship cape"' in server_items,
            "item 1523 must display as the Worship cape")

    # Compatibility: protocol, persistence, and external identifiers remain stable.
    require('case "Prayer":' in action_sender,
            "stat packet routing must retain the Prayer discriminator")
    require("currentPrayer" in stat_struct and "experiencePrayer" in stat_struct,
            "stat protocol fields must remain Prayer-named")
    require("getShortName().toLowerCase()" in mysql_queries,
            "SQL generation must continue using the stable short name")
    require("getShortName().toLowerCase()" in mysql_database,
            "database updates must continue using the stable short name")
    require('"myworld_prayer_book"' in player,
            "prayer-book cache key must remain compatible")
    require("PRAYER_CAPE(1523)" in item_ids,
            "Worship cape must retain its existing ItemId identifier and ID")

    # Prayer terminology is still correct for prayers and their point resource.
    require('"Prayer"' in client[client.index("drawColoredStringCentered"):],
            "the prayer-book tab must retain Prayer")
    require('"Prayer: " + prayerAvailablePoints' in client,
            "prayer allocation must retain Prayer")
    require("free prayer points to activate this prayer" in client,
            "prayer point/action terminology must remain unchanged")
    require("Prayer does not drain over time" in guide,
            "prayer behavior text must retain Prayer")
    require("prayer points" in guide,
            "prayer-point guidance must retain Prayer")
    require("two years of prayer and meditation" in religious_dialogue,
            "ordinary religious dialogue must retain prayer")
    require("presentation change, not a data or protocol migration" in compatibility,
            "compatibility boundary must be documented")

    print("PASS: Worship is player-facing while Prayer compatibility and prayer terminology remain intact")


if __name__ == "__main__":
    main()
