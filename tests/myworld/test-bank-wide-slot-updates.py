#!/usr/bin/env python3
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
CUSTOM_GENERATOR = ROOT / "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
CLIENT_CONFIG = ROOT / "Client_Base/src/orsc/Config.java"
MYWORLD_CONF = ROOT / "server/myworld.conf"
MYWORLD_HOST_CONF = ROOT / "server/myworld-host.conf"


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label} missing expected snippet: {snippet}")


def forbid(text: str, snippet: str, label: str) -> None:
    if snippet in text:
        raise SystemExit(f"FAIL: {label} contains forbidden snippet: {snippet}")


def main() -> None:
    generator = CUSTOM_GENERATOR.read_text(encoding="utf-8")
    handler = PACKET_HANDLER.read_text(encoding="utf-8")
    client_config = CLIENT_CONFIG.read_text(encoding="utf-8")
    myworld_conf = MYWORLD_CONF.read_text(encoding="utf-8")
    myworld_host_conf = MYWORLD_HOST_CONF.read_text(encoding="utf-8")

    require(generator, "builder.writeShort(bu.slot);", "Custom bank update slot encoding")
    forbid(generator, "builder.writeByte((byte) bu.slot);", "Custom bank update byte slot encoding")
    update_bank = re.search(
        r"private void updateBank\(\) \{(?P<body>.*?)\n\t\}",
        handler,
        re.S,
    )
    if update_bank is None:
        raise SystemExit("FAIL: Client bank update handler missing")
    require(update_bank.group("body"), "int slot = packetsIncoming.getShort();", "Client bank update slot decoding")
    forbid(update_bank.group("body"), "int slot = packetsIncoming.getUnsignedByte();", "Client bank update byte slot decoding")

    require(client_config, "CLIENT_VERSION = 10049;", "Client protocol version")
    require(myworld_conf, "client_version: 10049", "MyWorld server protocol version")
    require(myworld_host_conf, "client_version: 10049", "Hosted MyWorld server protocol version")

    print("PASS: custom bank update slots support values above 255")


if __name__ == "__main__":
    main()
