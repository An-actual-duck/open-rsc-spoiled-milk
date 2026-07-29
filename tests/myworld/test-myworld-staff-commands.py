#!/usr/bin/env python3

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLAYER_LOGIN = ROOT / "server/plugins/com/openrsc/server/plugins/shared/PlayerLogin.java"
BLINK_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/BlinkHandler.java"
MODERATOR = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Moderator.java"
ADMINS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
EVENT = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Event.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def fail(message: str) -> None:
    raise AssertionError(message)


def require(text: str, snippet: str, label: str, failures: list[str]) -> None:
    if snippet not in text:
        failures.append(f"{label} is missing: {snippet}")


def main() -> None:
    failures: list[str] = []
    blink_handler = BLINK_HANDLER.read_text(encoding="utf-8")
    moderator = MODERATOR.read_text(encoding="utf-8")
    admins = ADMINS.read_text(encoding="utf-8")
    event = EVENT.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")

    require(
        PLAYER_LOGIN.read_text(encoding="utf-8"),
        "public boolean blockPlayerLogin(Player player) {\n\t\treturn true;",
        "PlayerLogin.java",
        failures,
    )

    require(
        blink_handler,
        "if (player.isMod() || player.isDev())",
        "BlinkHandler.java",
        failures,
    )

    require(moderator, 'command.equalsIgnoreCase("s")', "Moderator.java", failures)
    require(moderator, "return player.isMod();", "Moderator.java", failures)
    require(admins, 'command.equalsIgnoreCase("item")', "Admins.java", failures)
    require(admins, "return player.isAdmin();", "Admins.java", failures)

    for alias in ('command.equalsIgnoreCase("clickteleport")',
                  'command.equalsIgnoreCase("clicktele")',
                  'command.equalsIgnoreCase("ct")'):
        require(event, alias, "Event.java", failures)
    require(event, "enableLeftClickTeleport(player);", "Event.java", failures)
    require(event, "return player.isEvent();", "Event.java", failures)
    require(event, "tryLayeredCoordinateTeleport(player, command, args)",
            "Event.java", failures)
    require(event, "args.length == 3\n\t\t\t\t? Integer.parseInt(args[2])",
            "Event.java", failures)
    require(event, "player.getLayeredLocation().getCoordinate().getLevel()",
            "Event.java", failures)
    require(event, "player.teleportLayered(destination, true);",
            "Event.java", failures)

    require(client, 'var11.startsWith("::clickteleport ")', "mudclient.java", failures)
    require(client, 'var11.startsWith("::clicktele ")', "mudclient.java", failures)
    require(client, 'var11.startsWith("::ct ")', "mudclient.java", failures)
    require(client, "return localPlayer != null && (localPlayer.isDev() || localPlayer.isMod());", "mudclient.java", failures)

    if failures:
        fail("\n".join(failures))

    print("PASS: myworld staff command access validated")


if __name__ == "__main__":
    main()
