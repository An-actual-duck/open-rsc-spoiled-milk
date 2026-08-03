#!/usr/bin/env python3
"""Verify the Cleric C03 equipment-stat and compatibility contract."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_JAR = ROOT / "server/core.jar"
SOURCE_DEFS = ROOT / "tools/generators/item-overrides/30-magic-weapons-and-hybrids.json"
GENERATED_DEFS = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
PLAYER_SERVICE = ROOT / "server/src/com/openrsc/server/service/PlayerService.java"
INVENTORY = ROOT / "server/src/com/openrsc/server/model/container/Inventory.java"
COMBAT_FORMULA = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
GENERATOR_DIR = ROOT / "server/src/com/openrsc/server/net/rsc/generators/impl"

BLESSED_FIRST_IDS = (2228, 3152, 3162)
ORDINARY_STAFF_IDS = (100, 2131, 1764, 1769, 2136, 1774, 1779, 2141, 1784, 2146)
ORDINARY_MAGIC = (8, 12, 16, 24, 28, 32, 40, 44, 48, 56)
BLESSED_MAGIC = (4, 6, 8, 12, 14, 16, 20, 22, 24, 28)
GOD_STAFF_IDS = (1216, 1217, 1218)
MODERN_GENERATORS = (
    "PayloadCustomGenerator",
    "Payload69Generator",
    "Payload115Generator",
    "Payload140Generator",
    "Payload177Generator",
    "Payload196Generator",
    "Payload198Generator",
    "Payload199Generator",
    "Payload201Generator",
    "Payload202Generator",
    "Payload203Generator",
    "Payload235Generator",
)
DIRECT_EXTENDED_GENERATORS = (
    "PayloadCustomGenerator",
    "Payload69Generator",
    "Payload115Generator",
    "Payload140Generator",
    "Payload177Generator",
    "Payload203Generator",
    "Payload235Generator",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def item_map(path: Path) -> dict[int, dict[str, object]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {int(item["id"]): item for item in data["items"]}


def verify_definition_parity() -> None:
    source = item_map(SOURCE_DEFS)
    generated = item_map(GENERATED_DEFS)

    for item_id, expected in zip(ORDINARY_STAFF_IDS, ORDINARY_MAGIC):
        require(source[item_id].get("magicOffense") == expected,
                f"ordinary staff {item_id} source Magic Power drifted")
        require(generated[item_id].get("magicOffense") == expected,
                f"ordinary staff {item_id} generated Magic Power drifted")

    for first_id in BLESSED_FIRST_IDS:
        for tier, expected in enumerate(BLESSED_MAGIC):
            item_id = first_id + tier
            require(source[item_id].get("magicOffense") == expected,
                    f"blessed staff {item_id} source Magic Power is not half-tier")
            require(generated[item_id].get("magicOffense") == expected,
                    f"blessed staff {item_id} generated Magic Power disagrees")

    for item_id in GOD_STAFF_IDS:
        require(source[item_id].get("magicOffense") == 28,
                f"god staff {item_id} source Magic Power must be 28")
        require(generated[item_id].get("magicOffense") == 28,
                f"god staff {item_id} generated Magic Power must be 28")


def verify_source_boundaries() -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    action_sender = ACTION_SENDER.read_text(encoding="utf-8")
    player_service = PLAYER_SERVICE.read_text(encoding="utf-8")
    inventory = INVENTORY.read_text(encoding="utf-8")
    combat_formula = COMBAT_FORMULA.read_text(encoding="utf-8")
    spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")

    for snippet in (
        "public int getHolyPower()",
        "player.getConfig().WANT_EQUIPMENT_TAB",
        "if (item.isWielded())",
        "Math.max(strongest, getHolyPower(item))",
        "EquipmentStatCalculator.holyPowerForItem(item.getCatalogId())",
    ):
        require(snippet in equipment, f"equipment-mode Holy Power boundary missing: {snippet}")
    require(equipment.count("ActionSender.sendEquipmentStats(player, request.item.getDef(player.getWorld()).getWieldPosition())") >= 2,
            "equip and unequip must both refresh derived Holy Power")
    require("struct.holyPowerPoints = player.getCarriedItems().getEquipment().getHolyPower();" in action_sender,
            "server packet must derive Holy Power from current equipment")

    require("loadPlayerEquipment(loaded);" in player_service,
            "equipment-tab relog path no longer restores equipment")
    require("player.getCarriedItems().setEquipment(equipment);" in player_service,
            "loaded equipment is not installed on the player")
    require("if (!player.getConfig().WANT_EQUIPMENT_TAB)" in inventory
            and "item.getItemStatus().setWielded(true);" in inventory,
            "legacy inventory-equipment relog path no longer restores wield state")
    require("sendStats(player);\n                sendEquipmentStats(player);" in action_sender,
            "login bootstrap must send equipment-derived stats")

    require("source.getMagicOffense()" in combat_formula
            and "victim.getMagicDefense()" in combat_formula,
            "offensive spell calculation no longer uses Magic offense/defense")
    require("calculateGodSpellDamage(getPlayer(), affectedMob, spellEnum)" in spell_handler,
            "god spells no longer use the established offensive damage path")
    require("getHolyPower" not in combat_formula and "getHolyPower" not in spell_handler,
            "Holy Power must not affect offensive god spells")

    for snippet in (
        '"Holy Power"',
        "new int[equipmentStatNames.length]",
        "getBlessedStaffTier(item.id)",
        "return this.getMyWorldStaffMagicOffenseByTier(blessedTier) / 2;",
        "case 1216:",
        "return 28;",
    ):
        require(snippet in client, f"client equipment parity boundary missing: {snippet}")
    require("for (int eq = 5; eq < mc.playerStatEquipment.length; ++eq)" in packet_handler,
            "short compatibility packets must clear unsupported extended stats")
    require("int intCount = Math.max(0, (length - 5) / 4);" in packet_handler,
            "client must retain length-aware equipment packet parsing")

    for name in DIRECT_EXTENDED_GENERATORS:
        source = (GENERATOR_DIR / f"{name}.java").read_text(encoding="utf-8")
        require("builder.writeInt(es.magicPowerPoints);\n\t\t\t\t\tbuilder.writeInt(es.holyPowerPoints);" in source,
                f"{name} does not append Holy Power after Magic Power")
    payload38 = (GENERATOR_DIR / "Payload38Generator.java").read_text(encoding="utf-8")
    require("holyPowerPoints" not in payload38,
            "authentic 38 equipment packet shape must remain unchanged")


FIXTURE = r"""
package com.openrsc.server.model.container;

import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.generators.PayloadGenerator;
import com.openrsc.server.net.rsc.generators.impl.*;
import com.openrsc.server.net.rsc.struct.outgoing.EquipmentStatsStruct;

public final class ClericHolyPowerEquipmentFixture {
	private ClericHolyPowerEquipmentFixture() {
	}

	public static void main(String[] args) {
		int[] firstIds = {2228, 3152, 3162};
		for (int firstId : firstIds) {
			for (int tier = 0; tier < 10; tier++) {
				check(EquipmentStatCalculator.holyPowerForItem(firstId + tier) == tier + 1,
					"wrong blessed Holy Power for " + (firstId + tier));
			}
		}

		int[] godStaves = {1216, 1217, 1218};
		for (int itemId : godStaves) {
			check(EquipmentStatCalculator.holyPowerForItem(itemId) == 11,
				"wrong god-staff Holy Power for " + itemId);
		}

		int[] ordinaryStaves = {100, 2131, 1764, 1769, 2136, 1774, 1779, 2141, 1784, 2146};
		for (int itemId : ordinaryStaves) {
			check(EquipmentStatCalculator.holyPowerForItem(itemId) == 0,
				"ordinary staff gained Holy Power: " + itemId);
		}
		for (int itemId = 3137; itemId <= 3151; itemId++) {
			check(EquipmentStatCalculator.holyPowerForItem(itemId) == 0,
				"blessed armor gained Holy Power: " + itemId);
		}
		check(EquipmentStatCalculator.holyPowerForItem(-1) == 0, "negative ID gained Holy Power");
		check(EquipmentStatCalculator.holyPowerForItem(0) == 0, "empty ID gained Holy Power");
		check(EquipmentStatCalculator.holyPowerForItem(2227) == 0, "lower range boundary leaked");
		check(EquipmentStatCalculator.holyPowerForItem(2238) == 0, "upper range boundary leaked");

		EquipmentStatsStruct stats = stats();
		checkExtended("custom", new PayloadCustomGenerator(), stats);
		checkExtended("69", new Payload69Generator(), stats);
		checkExtended("115", new Payload115Generator(), stats);
		checkExtended("140", new Payload140Generator(), stats);
		checkExtended("177", new Payload177Generator(), stats);
		checkExtended("196", new Payload196Generator(), stats);
		checkExtended("198", new Payload198Generator(), stats);
		checkExtended("199", new Payload199Generator(), stats);
		checkExtended("201", new Payload201Generator(), stats);
		checkExtended("202", new Payload202Generator(), stats);
		checkExtended("203", new Payload203Generator(), stats);
		checkExtended("235", new Payload235Generator(), stats);

		Packet authentic = new Payload38Generator().generate(stats(), null);
		check(authentic.getLength() == 6, "authentic 38 packet shape changed");
		for (int expected = 1; expected <= 5; expected++) {
			check(authentic.readUnsignedByte() == expected, "authentic legacy stat changed");
		}
		check(authentic.readUnsignedByte() == 9, "authentic hiding stat changed");
		check(authentic.getReadableBytes() == 0, "authentic packet gained trailing fields");

		System.out.println("PASS: Cleric Holy Power equipment mapping and packets validated");
	}

	private static EquipmentStatsStruct stats() {
		EquipmentStatsStruct stats = new EquipmentStatsStruct();
		stats.setOpcode(OpcodeOut.SEND_EQUIPMENT_STATS);
		stats.armourPoints = 1;
		stats.weaponAimPoints = 2;
		stats.weaponPowerPoints = 3;
		stats.magicPoints = 4;
		stats.prayerPoints = 5;
		stats.rangedPoints = 6;
		stats.magicPowerPoints = 7;
		stats.holyPowerPoints = 8;
		stats.hidingPoints = 9;
		return stats;
	}

	private static void checkExtended(String name, PayloadGenerator<OpcodeOut> generator,
		EquipmentStatsStruct stats) {
		Packet packet = generator.generate(stats, null);
		check(packet != null, name + " returned no equipment packet");
		check(packet.getLength() == 37, name + " equipment packet has wrong length " + packet.getLength());
		for (int expected = 1; expected <= 5; expected++) {
			check(packet.readUnsignedByte() == expected, name + " legacy stat changed");
		}
		for (int expected = 1; expected <= 8; expected++) {
			check(packet.readInt() == expected, name + " extended stat order changed at " + expected);
		}
		check(packet.getReadableBytes() == 0, name + " equipment packet has trailing data");
	}

	private static void check(boolean value, String message) {
		if (!value) {
			throw new AssertionError(message);
		}
	}
}
"""


def run_compiled_fixture() -> None:
    subprocess.run([str(ROOT / "scripts/build-server.sh")], cwd=ROOT, check=True)
    with tempfile.TemporaryDirectory(prefix="cleric-holy-power-equipment-") as raw_temp:
        temp = Path(raw_temp)
        source = temp / "com/openrsc/server/model/container/ClericHolyPowerEquipmentFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            ["javac", "-cp", str(SERVER_JAR), "-d", str(temp), str(source)],
            cwd=ROOT,
            check=True,
        )
        result = subprocess.run(
            ["java", "-cp", f"{temp}:{SERVER_JAR}",
             "com.openrsc.server.model.container.ClericHolyPowerEquipmentFixture"],
            cwd=ROOT / "server",
            text=True,
            capture_output=True,
        )
        require(result.returncode == 0,
                "compiled Holy Power fixture failed:\n" + result.stdout + result.stderr)
        print(result.stdout.strip())


def main() -> None:
    verify_definition_parity()
    verify_source_boundaries()
    run_compiled_fixture()
    print("PASS: Cleric C03 equipment foundation validated")


if __name__ == "__main__":
    main()
