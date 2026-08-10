#!/usr/bin/env python3
"""Regression guards for the compact Social Party tab and retired share controls."""
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8")
PARTY_INTERFACE = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/party/PartyInterface.java").read_text(
    encoding="utf-8")
ONLINE_LIST = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/OnlineListInterface.java").read_text(
    encoding="utf-8")
PARTY_HANDLER = (ROOT / "server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java").read_text(
    encoding="utf-8")
COMMANDS = (ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java").read_text(
    encoding="utf-8")
PARTY_INVITE = (ROOT / "server/src/com/openrsc/server/content/party/PartyInvite.java").read_text(
    encoding="utf-8")


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label}: missing {snippet!r}")


def method(text: str, signature: str, next_signature: str) -> str:
    start = text.index(signature)
    end = text.index(next_signature, start)
    return text[start:end]


def main() -> None:
    social = method(CLIENT, "\t// social tab\n\tprivate void drawUiTab5", "\n\t// spells menu")
    require(social, '"Friends"', "Friends Social tab")
    require(social, '"Clan"', "Clan Social tab")
    require(social, '"Party"', "Party Social tab")
    if '"Ignore"' in social:
        raise SystemExit("FAIL: retired Ignore Social tab remains")
    require(social, "this.drawPartySocialTab(var3, var4, var5, var6, var1);", "Party Social content")
    require(CLIENT, "private void drawPartySocialTab", "compact Party renderer")
    party_renderer = method(CLIENT, "\tprivate void drawPartySocialTab", "\n\tprivate void drawPartySocialButton")
    for retired_header in ('"Party: @cla@"', '"Leader: @yel@"'):
        if retired_header in party_renderer:
            raise SystemExit(f"FAIL: redundant party header remains: {retired_header}")
    require(party_renderer, "final int rowY = panelY + 43 + member * 16;", "top-aligned party rows")
    for label in ('"Invite"', '"Manage"', '"Leave"'):
        require(CLIENT, label, f"Party action {label}")
    require(CLIENT, 'this.showIgnoredList();', "ignored-list Social setting")

    require(ONLINE_LIST, "public void showIgnoredUsers", "ignored list presenter")
    require(ONLINE_LIST, 'rightClickMenu.createOption("Remove from list"', "ignored-list remove action")
    require(ONLINE_LIST, "getClient().removeIgnore(username);", "ignored-list removal wiring")

    if (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/PartyGUI.java").exists():
        raise SystemExit("FAIL: retired floating PartyGUI remains")
    for text, label in ((CLIENT, "client"), (PARTY_INTERFACE, "party interface"),
                        (COMMANDS, "player commands"), (PARTY_INVITE, "party invitation")):
        if "shareexp" in text.lower() or "share exp" in text.lower():
            raise SystemExit(f"FAIL: retired XP-sharing control remains in {label}")
    if "XP Shared" in PARTY_INTERFACE:
        raise SystemExit("FAIL: legacy XP-share column remains in party setup")

    party_handler = method(PARTY_HANDLER, "\tprivate void handleParty", "\n\tprivate void handlePoints")
    kick = method(party_handler, "\t\t\tcase KICK_PLAYER:", "\t\t\tcase RANK_PLAYER:")
    require(kick, "if (!player.getParty().isAllowed(0, player))", "server kick permission check")
    require(kick, 'player.message("You are not allowed to kick from this party.");', "kick denial message")

    print("PASS: Social Party tab, ignored-list relocation, and party permission cleanup are guarded")


if __name__ == "__main__":
    main()
