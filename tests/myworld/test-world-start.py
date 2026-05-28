#!/usr/bin/env python3
"""Validate MyWorld first-login spawn policy."""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MYWORLD_CONF = ROOT / "server" / "myworld.conf"
LOGIN_HANDLER = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "net"
    / "rsc"
    / "LoginPacketHandler.java"
)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_conf_bool(key: str, value: bool) -> None:
    text = MYWORLD_CONF.read_text(encoding="utf-8")
    expected = "true" if value else "false"
    if not re.search(rf"^\s*{re.escape(key)}:\s*{expected}\b", text, re.MULTILINE):
        fail(f"server/myworld.conf must keep {key}: {expected}")


def main() -> None:
    require_conf_bool("arrive_lumbridge", True)

    login_text = LOGIN_HANDLER.read_text(encoding="utf-8")
    required = (
        "firstTimeLocation = Point.location(server.getConfig().RESPAWN_LOCATION_X, "
        "server.getConfig().RESPAWN_LOCATION_Y);"
    )
    if required not in login_text:
        fail("LoginPacketHandler no longer sends arrive_lumbridge players to the respawn point")

    if "firstTimeLocation = Point.location(216, 744);" not in login_text:
        fail("LoginPacketHandler tutorial-island fallback changed; review Tutorial Island repurpose assumptions")

    print("PASS: MyWorld first-login spawn policy validated")


if __name__ == "__main__":
    main()
