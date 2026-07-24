#!/usr/bin/env python3
"""Validate in-game hiscore protocol, ranking, and request-safety guardrails."""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(source: str, snippet: str, message: str) -> None:
    if snippet not in source:
        fail(message)


def require_pattern(source: str, pattern: str, message: str) -> re.Match[str]:
    match = re.search(pattern, source, re.MULTILINE | re.DOTALL)
    if match is None:
        fail(message)
    return match


def main() -> None:
    host_config = read("server/myworld-host.conf")
    private_config = read("server/myworld.conf")
    server_config = read("server/src/com/openrsc/server/ServerConfiguration.java")
    action_sender = read("server/src/com/openrsc/server/net/rsc/ActionSender.java")
    custom_generator = read(
        "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"
    )
    custom_parser = read(
        "server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java"
    )
    request_handler = read(
        "server/src/com/openrsc/server/net/rsc/handlers/HiscoreRequestHandler.java"
    )
    lookup = read("server/src/com/openrsc/server/login/HiscoreLookupRequest.java")
    queries = read(
        "server/src/com/openrsc/server/database/impl/mysql/MySqlQueries.java"
    )
    database = read(
        "server/src/com/openrsc/server/database/impl/mysql/MySqlGameDatabase.java"
    )
    skill_policy = read("server/src/com/openrsc/server/constants/HiscoreSkills.java")
    client_data = read(
        "Client_Base/src/com/openrsc/interfaces/misc/HiscoreData.java"
    )
    client_packets = read("Client_Base/src/orsc/PacketHandler.java")
    client_opcodes = read("Client_Base/src/orsc/net/Opcodes.java")
    mudclient = read("Client_Base/src/orsc/mudclient.java")
    client_ui = read(
        "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
    )

    for config_name, config in (
        ("hosted", host_config),
        ("private", private_config),
    ):
        require(
            config,
            "want_hiscores: true",
            f"{config_name} MyWorld config must enable in-game hiscores",
        )
    require(
        server_config,
        'WANT_HISCORES = tryReadBool("want_hiscores").orElse(false);',
        "server configuration must default hiscores off outside opted-in worlds",
    )
    require(
        action_sender,
        "server.getConfig().WANT_HISCORES ? 1 : 0",
        "server config packet must advertise the hiscore capability",
    )
    require(
        client_packets,
        'props.setProperty("S_WANT_HISCORES", wantHiscores == 1 ? "true" : "false");',
        "client must consume the server hiscore capability",
    )

    require(
        custom_parser,
        "case 154:\n\t\t\t\topcode = OpcodeIn.HISCORE_REQUEST;",
        "custom inbound opcode 154 must remain the hiscore request",
    )
    require(
        client_opcodes,
        "HISCORE_REQUEST(154)",
        "client and server must agree on hiscore request opcode 154",
    )
    require(
        custom_generator,
        "put(OpcodeOut.SEND_HISCORES, 155);",
        "custom outbound opcode 155 must remain the hiscore response",
    )
    require(
        client_packets,
        'put(155, "SEND_HISCORES");',
        "client and server must agree on hiscore response opcode 155",
    )
    require(
        custom_parser,
        "return packet.getLength() == 1;",
        "hiscore request packets must remain exactly one byte",
    )
    require(
        client_packets,
        "if (count > HiscoreData.MAX_ENTRIES)",
        "client must cap server-provided hiscore row counts",
    )

    server_cooldown = int(
        require_pattern(
            request_handler,
            r"REQUEST_COOLDOWN_MS\s*=\s*(\d+)",
            "server hiscore request cooldown is missing",
        ).group(1)
    )
    client_retry = int(
        require_pattern(
            client_data,
            r"REQUEST_RETRY_MS\s*=\s*(\d+)",
            "client hiscore retry interval is missing",
        ).group(1)
    )
    if server_cooldown < 5000:
        fail("hiscore database scans must be throttled to at most one per five seconds")
    if client_retry < server_cooldown:
        fail("client retry interval must not be shorter than the server cooldown")
    for guard in (
        "WANT_HISCORES",
        "player.isUsingCustomClient()",
        "skillId >= player.getWorld().getServer().getConstants().getSkills().getSkillsCount()",
    ):
        require(request_handler, guard, f"hiscore request guard missing: {guard}")

    if "savePlayer(" in lookup:
        fail("read-only hiscore requests must never trigger a full player save")
    require(
        lookup,
        "if (getPlayer().loggedIn())",
        "hiscore response must recheck the session after asynchronous queries",
    )

    require(
        queries,
        'ORDER BY `xp` DESC, p.`id` ASC LIMIT 100',
        "skill hiscore rows must use stable experience/id ordering",
    )
    require(
        queries,
        'ORDER BY `lvl` DESC, `xp` DESC, p.`id` ASC LIMIT 100',
        "overall hiscore rows must use stable level/experience/id ordering",
    )
    require(
        queries,
        'maskedExp + " = ? AND p.`id` < ?',
        "skill rank must break experience ties by player id",
    )
    require(
        queries,
        'totalExpExpr + " = ? AND p.`id` < ?',
        "overall rank must break complete ties by player id",
    )
    require_pattern(
        database,
        r"queryHiscoreSkillRank.*?setInt\(1, playerDatabaseId\);"
        r".*?setLong\(2, experience\);"
        r".*?setLong\(3, experience\);"
        r".*?setInt\(4, playerDatabaseId\);",
        "skill-rank prepared-statement bindings must match tie-aware SQL",
    )
    require_pattern(
        database,
        r"queryHiscoreOverallRank.*?setInt\(1, playerDatabaseId\);"
        r".*?setInt\(2, totalLevel\);"
        r".*?setInt\(3, totalLevel\);"
        r".*?setLong\(4, totalExperience\);"
        r".*?setLong\(5, totalExperience\);"
        r".*?setInt\(6, playerDatabaseId\);",
        "overall-rank prepared-statement bindings must match tie-aware SQL",
    )

    for hidden_skill in ("defense", "strength", "fletching", "firemaking"):
        require(
            skill_policy,
            f'!name.equals("{hidden_skill}")',
            f"overall hiscores must exclude hidden/retired {hidden_skill}",
        )
    require(
        mudclient,
        'setSkillGuideChosen("Overall")',
        "stats panel must expose the overall hiscores view",
    )
    require(
        client_ui,
        'hiscoresView ? "Guide" : "Hiscores"',
        "skill guide must retain its guide/hiscores toggle",
    )

    print("PASS: in-game hiscore protocol, rankings, and request safety validated")


if __name__ == "__main__":
    main()
