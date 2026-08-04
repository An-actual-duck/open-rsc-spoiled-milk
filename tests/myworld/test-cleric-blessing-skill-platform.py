#!/usr/bin/env python3
"""Validate the append-only Blessing skill, persistence, and protocol boundary."""

import re
import shutil
import sqlite3
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def require_order(source: str, snippets: tuple[str, ...], message: str) -> None:
    cursor = -1
    for snippet in snippets:
        next_cursor = source.find(snippet, cursor + 1)
        require(next_cursor >= 0, f"{message}: missing {snippet}")
        require(next_cursor > cursor, f"{message}: out of order at {snippet}")
        cursor = next_cursor


def apply_sqlite_patch(database: sqlite3.Connection, relative: str) -> None:
    sql = read(relative).replace("_PREFIX_", "")
    database.executescript(sql)


def assert_sqlite_migration() -> None:
    source = ROOT / "server/inc/sqlite/myworld_seed.db"
    with tempfile.TemporaryDirectory(prefix="blessing-migration-") as temp_dir:
        migrated = Path(temp_dir) / "existing.db"
        shutil.copy2(source, migrated)
        database = sqlite3.connect(migrated)
        try:
            database.execute(
                "INSERT INTO players (id, username, pass, quest_points) VALUES (?, ?, ?, ?)",
                (42, "migration", "unused", 37),
            )
            for table in ("curstats", "experience", "maxstats", "capped_experience"):
                database.execute(f"INSERT INTO {table} (playerID) VALUES (42)")
            apply_sqlite_patch(
                database,
                "server/database/sqlite/patches/2026_05_14_add_summoning_skill.sql",
            )
            apply_sqlite_patch(
                database,
                "server/database/sqlite/patches/2026_08_03_add_blessing_skill.sql",
            )

            require(database.execute(
                "SELECT blessing FROM curstats WHERE playerID=42"
            ).fetchone()[0] == 1, "existing current Blessing level must migrate to 1")
            require(database.execute(
                "SELECT blessing FROM maxstats WHERE playerID=42"
            ).fetchone()[0] == 1, "existing base Blessing level must migrate to 1")
            require(database.execute(
                "SELECT blessing FROM experience WHERE playerID=42"
            ).fetchone()[0] == 0, "existing Blessing XP must migrate to zero")
            require(database.execute(
                "SELECT blessing FROM capped_experience WHERE playerID=42"
            ).fetchone()[0] is None, "existing Blessing cap date must migrate to null")
            require(database.execute(
                "SELECT quest_points FROM players WHERE id=42"
            ).fetchone()[0] == 37, "skill migration must not alter Quest Points")

            database.execute("UPDATE curstats SET blessing=12 WHERE playerID=42")
            database.execute("UPDATE maxstats SET blessing=12 WHERE playerID=42")
            database.execute("UPDATE experience SET blessing=7332 WHERE playerID=42")
            database.execute("UPDATE capped_experience SET blessing=123456 WHERE playerID=42")
            database.commit()
        finally:
            database.close()

        database = sqlite3.connect(migrated)
        try:
            require(database.execute(
                "SELECT c.blessing, m.blessing, e.blessing, x.blessing, p.quest_points "
                "FROM curstats c JOIN maxstats m USING(playerID) "
                "JOIN experience e USING(playerID) JOIN capped_experience x USING(playerID) "
                "JOIN players p ON p.id=c.playerID WHERE c.playerID=42"
            ).fetchone() == (12, 12, 7332, 123456, 37),
                "Blessing and Quest Points must survive an SQLite close/reopen round trip")

            for table in ("curstats", "experience", "maxstats", "capped_experience"):
                database.execute(f"INSERT INTO {table} (playerID) VALUES (43)")
            require(database.execute(
                "SELECT c.blessing, m.blessing, e.blessing, x.blessing "
                "FROM curstats c JOIN maxstats m USING(playerID) "
                "JOIN experience e USING(playerID) JOIN capped_experience x USING(playerID) "
                "WHERE c.playerID=43"
            ).fetchone() == (1, 1, 0, None),
                "new rows must receive safe Blessing defaults")
        finally:
            database.close()


def main() -> None:
    skills = read("server/src/com/openrsc/server/constants/Skills.java")
    skill_alias = read("server/src/com/openrsc/server/constants/Skill.java")
    model_skills = read("server/src/com/openrsc/server/model/Skills.java")
    game_database = read("server/src/com/openrsc/server/database/GameDatabase.java")
    queries = read("server/src/com/openrsc/server/database/impl/mysql/MySqlQueries.java")
    action_sender = read("server/src/com/openrsc/server/net/rsc/ActionSender.java")
    stat_struct = read("server/src/com/openrsc/server/net/rsc/struct/outgoing/StatInfoStruct.java")
    custom_generator = read(
        "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"
    )
    packet_handler = read("Client_Base/src/orsc/PacketHandler.java")
    client = read("Client_Base/src/orsc/mudclient.java")
    guide = read("Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java")
    hiscore_policy = read("server/src/com/openrsc/server/constants/HiscoreSkills.java")
    player = read("server/src/com/openrsc/server/model/entity/player/Player.java")
    item_effects = read("server/src/com/openrsc/server/content/EnchantingItemEffects.java")
    mysql_core = read("server/database/mysql/core.sql")
    sqlite_core = read("server/database/sqlite/core.sqlite")

    require(
        'new SkillDef("Blessing", "Blessing", 1, 99, SkillDef.EXP_CURVE.ORIGINAL, skillIndex++)'
        in skills,
        "Blessing must use the original 1-99 curve and level-one minimum",
    )
    require_order(
        skills,
        ('new SkillDef("Summoning"', 'new SkillDef("Blessing"'),
        "server skill identity must append Blessing after Summoning",
    )
    require("BLESSING = new Skill(Skills.BLESSING)" in skill_alias,
            "stable Skill.BLESSING lookup is missing")
    require("new int[getWorld().getServer().getConstants().getSkills().getSkillsCount()]" in model_skills,
            "runtime skill arrays must remain registry-sized")
    require("skill.getMinLevel() == 1" in game_database and "experience = 0" in game_database,
            "new-player skill initialization must derive level-one zero-XP defaults")

    require_order(
        client,
        ('addSkill("Summoning", "Summon");', 'addSkill("Blessing");'),
        "client protocol order must append Blessing",
    )
    require("sortDisplayedSkillsByName(displayedSkills);" in client,
            "player-facing skills must remain alphabetically sorted")
    require('skillGuideChosen.equalsIgnoreCase("Blessing")' in client,
            "Blessing guide/highscore selection is missing")
    require("populateBlessingGuide();" in guide,
            "Blessing must have a bounded skill-guide platform")
    require("Blessing cape unlocked" not in guide,
            "C04 must not add a Blessing skill cape")

    for field in ("currentBlessing", "maxBlessing", "experienceBlessing"):
        require(field in stat_struct and field in action_sender and field in custom_generator,
                f"maintained stat transport is missing {field}")
    require_order(
        custom_generator,
        (
            "builder.writeInt(si.experienceSummoning);",
            "builder.writeInt(si.experienceBlessing);",
            "builder.writeByte((byte) si.questPoints);",
        ),
        "Quest Points must follow the complete custom experience array",
    )
    require("private void loadQuestPoints()" in packet_handler
            and "mc.setQuestPoints(packetsIncoming.getUnsignedByte());" in packet_handler,
            "client Quest Points must remain a separate field")
    require('"Quest Points:@yel@" + this.questPoints' in client,
            "stats panel must render separate Quest Points state")

    legacy_generators = tuple(
        ROOT.glob("server/src/com/openrsc/server/net/rsc/generators/impl/Payload*Generator.java")
    )
    for path in legacy_generators:
        if path.name == "PayloadCustomGenerator.java":
            continue
        require("Blessing" not in path.read_text(encoding="utf-8"),
                f"legacy packet layout must not gain Blessing fields: {path.name}")

    require("HiscoreSkills.countsTowardOverall(skill)" in model_skills,
            "server totals must use shared hiscore eligibility")
    require('!name.equals("blessing")' not in hiscore_policy,
            "Blessing must count toward overall totals/highscores")
    require("hiscoreSkillTop = new String[hiscoreSkillCount]" in queries,
            "per-skill highscores must remain registry-sized")
    require("getSkillIndex(args[1].toLowerCase())" in read(
        "server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
    ), "admin skill commands must retain dynamic name lookup")

    require("|| skillId == Skill.BLESSING.id();" in item_effects,
            "production-skill Mind-necklace XP modifiers must include Blessing")
    require("Skill.BLESSING.id()" not in re.search(
        r"POTION_INSIGHT_SKILLS\s*=\s*\{(?P<body>.*?)\};", player, re.S
    ).group("body"), "C04 must not add a Blessing level potion")
    require("Skill.BLESSING.id()" not in re.search(
        r"private boolean isCombatXpSkill.*?\n\t\}", player, re.S
    ).group(0), "Blessing must remain a non-combat XP skill")

    for schema_name, schema in (("MySQL", mysql_core), ("SQLite", sqlite_core)):
        require(schema.count("`blessing`") == 4,
                f"{schema_name} core schema must define all four Blessing fields")
    assert_sqlite_migration()

    for relative in (
        "Client_Base/src/orsc/Config.java",
        "server/myworld.conf",
        "server/myworld-host.conf",
        "release/world-builder-v2/world-builder-runtime.conf",
    ):
        require("10051" in read(relative), f"custom protocol version drift: {relative}")

    blessing_plugins = []
    for path in (ROOT / "server/plugins").rglob("*.java"):
        if "Skill.BLESSING" in path.read_text(encoding="utf-8"):
            blessing_plugins.append(str(path.relative_to(ROOT)))
    require(blessing_plugins == [
        "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/blessing/SigilProduction.java"
    ], "Blessing gameplay must remain limited to the approved C05 sigil production path")

    print("PASS: Blessing is append-only, persistent, ranked, and Quest-Points safe")


if __name__ == "__main__":
    main()
