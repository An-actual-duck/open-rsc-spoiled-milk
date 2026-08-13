#!/usr/bin/env python3
"""Guard B11 compatibility labels and proof-before-prune decisions."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def main() -> None:
    payload_manager = read(
        "server/src/com/openrsc/server/net/rsc/PayloadProcessorManager.java"
    )
    known_players = read(
        "server/src/com/openrsc/server/net/rsc/handlers/KnownPlayersHandler.java"
    )
    require(
        "bind(OpcodeIn.KNOWN_PLAYERS, KnownPlayersHandler.class);" in payload_manager,
        "Known-player opcode binding was removed",
    )
    require(
        "KNOWN_PLAYERS, KnownPlayersHandler.class); // TODO" not in payload_manager,
        "Implemented KnownPlayersHandler is still labeled unfinished",
    )
    for statement in (
        "player.ensureKnownPlayerCapacity(payload.playerCount);",
        "player.knownPlayersCount = payload.playerCount;",
        "player.knownPlayerPids[i] = payload.playerServerIndex[i];",
        "player.knownPlayerAppearanceIds[i] = payload.playerServerAppearanceId[i];",
    ):
        require(statement in known_players, f"Known-player cache behavior changed: {statement}")

    active_java_roots = (
        "Client_Base/src",
        "PC_Client/src",
        "server/src",
        "server/plugins",
        "tools/world-builder/src",
    )
    generated_stub_labels = []
    archive_dump_calls = []
    for root_name in active_java_roots:
        for source_path in (ROOT / root_name).rglob("*.java"):
            source = source_path.read_text(encoding="utf-8")
            if "TODO Auto-generated method stub" in source:
                generated_stub_labels.append(source_path.relative_to(ROOT).as_posix())
            if ".dump(" in source:
                archive_dump_calls.append(source_path.relative_to(ROOT).as_posix())
    require(not generated_stub_labels, f"Generated-stub labels remain: {generated_stub_labels}")
    require(not archive_dump_calls, f"Active Java still calls an archive dump method: {archive_dump_calls}")

    bank = read("server/src/com/openrsc/server/model/container/Bank.java")
    duel = read("server/src/com/openrsc/server/model/entity/player/Duel.java")
    trade = read("server/src/com/openrsc/server/model/entity/player/Trade.java")
    bank_pin = read("Client_Base/src/com/openrsc/interfaces/misc/BankPinInterface.java")
    world = read("Client_Base/src/orsc/graphics/three/World.java")
    require("Server-side\n\t * bank storage has no tab state" in bank, "Bank no-op contract is missing")
    require(duel.count("PlayerDuelHandler sends offer updates explicitly") == 2, "Duel no-op ownership drifted")
    require(trade.count("PlayerTradeHandler sends offer updates explicitly") == 2, "Trade no-op ownership drifted")
    require("duel-offer capacity is validated by the handler" in duel, "Duel capacity no-op is unlabeled")
    require("trade-offer capacity is validated by the handler" in trade, "Trade capacity no-op is unlabeled")
    require("no bank-PIN recovery request is defined" in bank_pin, "Passive bank-PIN control is unlabeled")
    require("return mapPointX;" in world and "return mapPointZ;" in world, "World map accessors changed")

    jcontent = read("server/src/com/openrsc/server/io/JContent.java")
    jcontent_file = read("server/src/com/openrsc/server/io/JContentFile.java")
    for source_name, source in (("JContent", jcontent), ("JContentFile", jcontent_file)):
        require("void dump(" not in source, f"{source_name}.dump returned")
        require("DataOutputStream" not in source, f"{source_name} retained dump-only imports")
        require("FileOutputStream" not in source, f"{source_name} retained dump-only imports")
        require("public void close()" in source, f"{source_name} reader lifecycle changed")
    require("public boolean open(" in jcontent and "public JContentFile unpack(" in jcontent, "JContent reader API changed")
    require("public byte readByte()" in jcontent_file and "public String readString()" in jcontent_file, "JContentFile reader API changed")

    spotbugs = read("config/static-analysis/baseline/spotbugs.txt")
    for finding in (
        "server|DE_MIGHT_IGNORE|2|com.openrsc.server.io.JContentFile|dump",
        "server|DE_MIGHT_IGNORE|2|com.openrsc.server.io.JContent|dump",
        "server|REC_CATCH_EXCEPTION|2|com.openrsc.server.io.JContentFile|dump",
        "server|REC_CATCH_EXCEPTION|2|com.openrsc.server.io.JContent|dump",
    ):
        require(finding not in spotbugs, f"Removed dump finding remains in baseline: {finding}")

    build_xml = read("Client_Base/build.xml")
    require('name="src" location="src"' in build_xml, "Client_Base source root changed")
    require('name="pc_client" location="../PC_Client/src"' in build_xml, "PC_Client source root changed")
    require('srcdir="${src}:${pc_client}"' in build_xml, "Active client roots are no longer built together")
    require("ACTIVE DESKTOP CLIENT ROOTS" in build_xml, "Active client roots are not labeled at the build definition")

    removed_ide_paths = (
        "Client_Base/.idea/.name",
        "Client_Base/.idea/ant.xml",
        "Client_Base/.idea/client.iml",
        "Client_Base/.idea/misc.xml",
        "Client_Base/.idea/modules.xml",
        "Client_Base/.idea/modules/Client.iml",
        "Client_Base/.idea/vcs.xml",
        "Client_Base/Client.iml",
        "Client_Base/PC Client.iml",
        "server/Game Server.iml",
    )
    for path in removed_ide_paths:
        require(not (ROOT / path).exists(), f"Superseded active-root IDE metadata returned: {path}")
    require("*.iml" in read(".gitignore").splitlines(), "Generated IntelliJ modules are not ignored")

    server_config = read("server/src/com/openrsc/server/ServerConfiguration.java")
    updater = read("server/src/com/openrsc/server/GameStateUpdater.java")
    require("public boolean DISABLE_NPC_LOCATION_CACHE;" in server_config, "Semantic NPC-cache diagnostic flag is missing")
    require("@Deprecated\n\tpublic boolean BREAK_NPC_LOCATION_CACHE;" in server_config, "Historical NPC-cache field compatibility alias is missing")
    require('tryReadBool("break_npc_location_cache")' in server_config, "Historical NPC-cache config key changed")
    require("BREAK_NPC_LOCATION_CACHE = DISABLE_NPC_LOCATION_CACHE;" in server_config, "NPC-cache alias is not initialized consistently")
    require(updater.count("getConfig().DISABLE_NPC_LOCATION_CACHE") == 2, "GameStateUpdater does not use the semantic NPC-cache flag")
    require("getConfig().BREAK_NPC_LOCATION_CACHE" not in updater, "Internal code reused the misleading NPC-cache alias")

    compatibility_consumers = {
        "OLD_QUEST_MECHANICS": (
            "server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/ManPhoenix.java",
            "server/plugins/com/openrsc/server/plugins/authentic/quests/free/ShieldOfArrav.java",
        ),
        "OLD_SKILL_DEFS": (
            "server/src/com/openrsc/server/external/EntityHandler.java",
            "server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java",
        ),
    }
    for field, paths in compatibility_consumers.items():
        require(f"public boolean {field};" in server_config, f"Active compatibility field was removed: {field}")
        require(any(field in read(path) for path in paths), f"No active consumer remains for {field}")

    require("public boolean OLD_PRAY_XP;" not in server_config, "Retired prayer-XP compatibility field returned")
    require('tryReadBool("old_pray_xp")' not in server_config, "Retired prayer-XP config reader returned")
    for config_path in ("server/myworld.conf", "server/myworld-host.conf"):
        require("old_pray_xp:" not in read(config_path), f"Retired prayer-XP key returned: {config_path}")

    open_rsc = read("PC_Client/src/orsc/OpenRSC.java")
    legacy_scaling = read("Client_Base/src/orsc/LegacySoftwareScalingSettings.java")
    render_surface = read("Client_Base/src/orsc/RenderSurfaceSettings.java")
    for key in ("scaling_type", "ui_scale", "scaling_scalar"):
        require(f'"{key}"' in legacy_scaling, f"Software scaling migration key changed: {key}")
    require("LegacySoftwareScalingSettings.loadFromClientSettings(props);" in open_rsc,
            "Desktop launcher no longer loads software scaling compatibility")
    require("active software-presenter scaling compatibility state" in legacy_scaling,
            "Software scaling bridge is mislabeled")
    require("persisted-setting migration" in render_surface, "Hidden render modes are not labeled as migration aliases")
    for mode in ("CLASSIC", "VGA", "WIDE_PLUS", "HD", "FULL_HD"):
        require(f"{mode}(" in render_surface, f"Persisted render-surface alias was removed: {mode}")

    protocol_impl = ROOT / "server/src/com/openrsc/server/net/rsc"
    for version in (38, 69, 115, 140, 177, 196, 198, 199, 201, 202, 203, 235):
        require((protocol_impl / f"parsers/impl/Payload{version}Parser.java").is_file(), f"Protocol {version} parser was pruned")
        require((protocol_impl / f"generators/impl/Payload{version}Generator.java").is_file(), f"Protocol {version} generator was pruned")
    require((protocol_impl / "parsers/impl/PayloadCustomParser.java").is_file(), "Custom protocol parser was pruned")
    require((protocol_impl / "generators/impl/PayloadCustomGenerator.java").is_file(), "Custom protocol generator was pruned")

    plugin_loader = read("server/src/com/openrsc/server/plugins/io/PluginJarLoader.java")
    plugin_handler = read("server/src/com/openrsc/server/plugins/handler/PluginHandler.java")
    require("Class.forName(" in plugin_loader, "Plugin reflection discovery boundary changed")
    require('getMethod("init", Server.class)' in plugin_handler, "Plugin initialization reflection changed")

    mysql_deferred = ROOT / "server/database/mysql/depreciated"
    expected_mysql_helpers = {
        "add_custom_items.sql",
        "add_custom_npcs.sql",
        "add_custom_objects.sql",
        "remove_custom_npcs.sql",
        "remove_custom_objects.sql",
    }
    require(
        {path.name for path in mysql_deferred.glob("*.sql")} == expected_mysql_helpers,
        "Deferred MySQL helper inventory changed without operator review",
    )
    require((ROOT / "legacy/README.md").is_file(), "Explicit legacy archive policy was removed")

    client_port = read("Client_Base/src/orsc/multiclient/ClientPort.java")
    require("void playSound(byte[] soundData" in client_port, "Platform audio compatibility hook was pruned")

    document = read("docs/myworld/info/compatibility-and-prune-proof-b11.md")
    for statement in (
        "The desktop client is one product built from two active source roots",
        "Removed With Complete Reference Proof",
        "Retained Because Proof Is Incomplete",
        "MySQL operator/migration inventory",
        "The whole `legacy/` tree also remains unchanged",
        "For a clean IntelliJ setup",
    ):
        require(statement in document, f"B11 proof documentation is missing: {statement}")

    print("PASS: B11 compatibility labels, preservation boundaries, and proved prunes are guarded")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError) as error:
        print(f"FAIL: {error}")
        raise SystemExit(1)
