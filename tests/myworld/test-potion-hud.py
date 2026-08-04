#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DRINKABLES = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/Drinkables.java"
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
GENERATOR = ROOT / "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"
VALIDATOR = ROOT / "server/src/com/openrsc/server/net/rsc/PayloadValidator.java"
OPCODES = ROOT / "server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java"
STRUCT = ROOT / "server/src/com/openrsc/server/net/rsc/struct/outgoing/ActivePotionEffectsStruct.java"
PACKETS = ROOT / "Client_Base/src/orsc/PacketHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    player = PLAYER.read_text(encoding="utf-8")
    drinkables = DRINKABLES.read_text(encoding="utf-8")
    sender = ACTION_SENDER.read_text(encoding="utf-8")
    generator = GENERATOR.read_text(encoding="utf-8")
    validator = VALIDATOR.read_text(encoding="utf-8")
    opcodes = OPCODES.read_text(encoding="utf-8")
    struct = STRUCT.read_text(encoding="utf-8")
    packets = PACKETS.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")

    require(opcodes, "SEND_ACTIVE_POTION_EFFECTS", "missing potion HUD opcode")
    require(validator, "ActivePotionEffectsStruct.class", "missing potion payload validation")
    require(generator, "put(OpcodeOut.SEND_ACTIVE_POTION_EFFECTS, 152)", "missing custom opcode mapping")
    require(generator, "Math.max(0, Math.min(32, potionEffects.count))", "server packet count is not bounded")
    require(generator, "potionEffects.totalCount - potionEffectCount",
            "server packet does not report omitted status entries")
    require(generator, "builder.writeShort(Math.min(65535, potionEffectOverflow))",
            "server packet does not bound its overflow count")
    require(generator, "builder.writeShort(potionEffects.itemIds[i])", "packet omits consumed potion identity")
    require(generator, "builder.writeInt(potionEffects.remainingSeconds[i])", "packet omits remaining duration")
    require(struct, "int[] itemIds", "potion payload lacks item IDs")
    require(struct, "int[] remainingSeconds", "potion payload lacks durations")
    require(struct, "int totalCount", "potion payload lacks total status count")

    require(sender, "if (!player.isUsingCustomClient())", "legacy clients must not receive the custom packet")
    require(sender, "player.getActivePotionEffectStatuses()", "packet does not derive from server state")
    if sender.count("sendActivePotionEffects(player);") < 1:
        fail("login does not synchronize active potion effects")

    for family in (
        "brawn", "deftness", "insight_skills", "skiller", "warrior",
        "stat_reduction_protection", "poison_protection",
    ):
        require(player, f'"{family}"', f"active potion audit omits {family}")
    require(player, "MAX_REPORTED_ACTIVE_POTION_EFFECTS = 64",
            "server status inventory does not permit bounded overflow reporting")
    require(player, "(remainingMs + 999L) / 1000L", "server countdown does not round positive time safely")
    require(player, 'getCache().store("potion_" + key + "_xp_item_id", itemId)',
            "persistent XP brew timer does not retain its display identity")
    require(player, "private volatile long poisonProtectionExpiresAt = 0L;",
            "poison-protection expiry is not atomic across game and packet threads")
    if "lastAntidote" in player or "poisonProtectionTime" in player:
        fail("poison protection still splits one timer across non-atomic fields")

    require(drinkables, "applyHerblawPotionEffect(player, effect, item.getCatalogId())",
            "tiered potion identity is not passed to the active effect")
    require(drinkables, "ActionSender.sendActivePotionEffects(player)",
            "successful drinks do not refresh the HUD snapshot")
    require(drinkables, "player.activatePotionOfBrawn(effect.percent, duration, itemId)",
            "skill potion activation loses exact consumed variant")
    require(drinkables, "player.activateSkillerBrew(effect.percent, duration, itemId)",
            "XP brew activation loses exact consumed variant")

    require(packets, "else if (opcode == 152) updateActivePotionEffects(length);",
            "client does not dispatch length-aware status snapshots")
    require(packets, "if (count > 32)", "client does not reject oversized snapshots")
    require(packets, "length >= encodedEntryBytes + 2",
            "client does not accept the optional overflow suffix")
    require(client, "MAX_ACTIVE_POTION_EFFECTS = 32", "client status capacity was not expanded")
    require(client, "POTION_HUD_ROWS_PER_COLUMN = 8", "multiple effects are not laid out in bounded columns")
    require(client, "POTION_HUD_Y + row * POTION_HUD_ROW_HEIGHT", "potion rows can overlap")
    require(client, "itemDef.getName()", "hover does not show the exact item name")
    require(client, "compactActivePotionEffects(System.currentTimeMillis())",
            "expired effects do not disappear without another packet")
    require(client, "this.clearActivePotionEffects();", "logout/reconnect does not clear stale client state")
    require(client, '"+" + this.activePotionEffectOverflowCount + " more effects"',
            "client does not disclose omitted status entries")

    print("PASS: expanded bounded server-authoritative status HUD wiring validated")


if __name__ == "__main__":
    main()
