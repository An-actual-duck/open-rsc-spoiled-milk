#!/usr/bin/env python3
"""Characterize pure equipment stat tables before and after extraction."""

from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_JAR = ROOT / "server/core.jar"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
STAT_CALCULATOR = ROOT / "server/src/com/openrsc/server/model/container/EquipmentStatCalculator.java"
SLOT_RULES = ROOT / "server/src/com/openrsc/server/model/container/EquipmentSlotRules.java"

EXPECTED: dict[str, tuple[int, str]] = {
    "getHolyPowerForItem": (4002, "c3592c03bfc4ca0a617c19769251d08de4f4981368eb56d61ada08f3c677e432"),
    "getGodEquipmentNaturalPrayerBonus": (4002, "751d228b94591005dd8b95fe0f92ac90ba6991b8eb918f7c86a86ca2b112daaf"),
    "getGodEquipmentResourceCost": (4002, "2b4706cde2e881bd58ddfc32e4f4aaf734ab230473f8f676e66487cd8e40f276"),
    "getGodEquipmentTargetWeaponAim": (4002, "555f20a1f84e426ef3e5e918fb5d53ea23891e6e50b8dcd9122b0a924ae3ab6e"),
    "getGodEquipmentTargetWeaponPower": (4002, "2b413aa3ab97fec20a45d353b6556c1e6c7f8799da8e8a1ab3084340f1b224a2"),
    "getGodEquipmentTargetArmour": (4002, "adb7235b300627e2e29eaf5ff20b9e76f8b06298696cfa825dcf9126c9acb5af"),
    "getGodEquipmentTargetMeleeOffense": (4002, "dc57f16f70ad47a9fdb8288957a20ca61dd6699a1a124dbf6ef0f76022054bea"),
    "getGodEquipmentTargetMeleeDefense": (4002, "85836837393b8a9fbbf831156ae02a96328312189910c8fb916532f88db55df0"),
    "getGodEquipmentTargetRangedDefense": (4002, "4c947308bdfbe0b945bbd48c68b1680bba89d1e8361cdc0e6d21fe1007ac0f18"),
    "getGodEquipmentTargetMagicDefense": (4002, "c5c1191ba4e98a0982f7f519c9b65069ef4ec0e6c2af6c497058e7fabe32ab7a"),
    "getGodEquipmentTargetMagic": (4002, "0867364c350ce6dc8914b26d66599601db02d95b3c6695bbc99aff90b8bfaa76"),
    "getBlessedWoolBaseMagicDefense": (4002, "c7783840877a575d0a8e9df84956b6c4f68d9ccaeeae04218633ddd8d2647ea9"),
    "getBlessedWoolTargetMagicDefense": (4002, "78752b34c7f6a65b64b2e1cbac33b9860ff4e8a40efbf2aa4c6fc4738d36ebe3"),
    "getLegacyRangedOffense": (2431, "e9f8f428252a0fc738c819e16721b314736fd4e4bac8b1a11e7e74dfc1633499"),
}

FIXTURE = r"""
package com.openrsc.server.model.container;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Equipment.EquipmentSlot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

public final class EquipmentCalculationFixture {
	private interface Lookup {
		int get(int itemId);
	}

	private EquipmentCalculationFixture() {
	}

	public static void main(String[] args) throws Exception {
		result("getHolyPowerForItem", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.holyPowerForItem(id); }
		});
		result("getGodEquipmentNaturalPrayerBonus", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentNaturalPrayerBonus(id); }
		});
		result("getGodEquipmentResourceCost", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentResourceCost(id); }
		});
		result("getGodEquipmentTargetWeaponAim", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetWeaponAim(id); }
		});
		result("getGodEquipmentTargetWeaponPower", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetWeaponPower(id); }
		});
		result("getGodEquipmentTargetArmour", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetArmour(id); }
		});
		result("getGodEquipmentTargetMeleeOffense", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetMeleeOffense(id); }
		});
		result("getGodEquipmentTargetMeleeDefense", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetMeleeDefense(id); }
		});
		result("getGodEquipmentTargetRangedDefense", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetRangedDefense(id); }
		});
		result("getGodEquipmentTargetMagicDefense", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetMagicDefense(id); }
		});
		result("getGodEquipmentTargetMagic", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.godEquipmentTargetMagic(id); }
		});
		result("getBlessedWoolBaseMagicDefense", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.blessedWoolBaseMagicDefense(id); }
		});
		result("getBlessedWoolTargetMagicDefense", new Lookup() {
			public int get(int id) { return EquipmentStatCalculator.blessedWoolTargetMagicDefense(id); }
		});

		ItemId[] itemIds = ItemId.values().clone();
		Arrays.sort(itemIds, new Comparator<ItemId>() {
			public int compare(ItemId left, ItemId right) {
				return Integer.compare(left.id(), right.id());
			}
		});
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (ItemId itemId : itemIds) {
			append(digest, itemId.name() + ":" + itemId.id() + ":"
				+ EquipmentStatCalculator.legacyRangedOffense(itemId.id()));
		}
		result("getLegacyRangedOffense", itemIds.length, digest);
		assertArithmetic();
		assertSlotRules();
	}

	private static void result(String name, Lookup lookup) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		int count = 0;
		for (int itemId = -1; itemId <= 4000; itemId++) {
			append(digest, itemId + ":" + lookup.get(itemId));
			count++;
		}
		result(name, count, digest);
	}

	private static void assertArithmetic() {
		if (EquipmentStatCalculator.displayedOffense(30, 5, 8) != 27
			|| EquipmentStatCalculator.displayedOffense(3, -4, 8) != -5
			|| EquipmentStatCalculator.combatOffense(-5) != 0
			|| EquipmentStatCalculator.combatOffense(27) != 27
			|| EquipmentStatCalculator.armorPowerPenalty(4, 8) != 32) {
			throw new AssertionError("Equipment arithmetic changed");
		}
	}

	private static void assertSlotRules() {
		for (EquipmentSlot left : EquipmentSlot.values()) {
			for (EquipmentSlot right : EquipmentSlot.values()) {
				boolean handFoot = left == EquipmentSlot.SLOT_GLOVES || left == EquipmentSlot.SLOT_BOOTS;
				boolean bodyLeg = right == EquipmentSlot.SLOT_CHAIN_BODY
					|| right == EquipmentSlot.SLOT_PLATE_BODY
					|| right == EquipmentSlot.SLOT_PLATE_LEGS
					|| right == EquipmentSlot.SLOT_SKIRT;
				boolean reverseHandFoot = right == EquipmentSlot.SLOT_GLOVES || right == EquipmentSlot.SLOT_BOOTS;
				boolean reverseBodyLeg = left == EquipmentSlot.SLOT_CHAIN_BODY
					|| left == EquipmentSlot.SLOT_PLATE_BODY
					|| left == EquipmentSlot.SLOT_PLATE_LEGS
					|| left == EquipmentSlot.SLOT_SKIRT;
				boolean expected = handFoot && bodyLeg || reverseHandFoot && reverseBodyLeg;
				if (EquipmentSlotRules.allowsHandFootArmorOverlap(left.getIndex(), right.getIndex()) != expected) {
					throw new AssertionError("Hand/foot overlap changed for " + left + " and " + right);
				}
			}
		}
		if (!EquipmentSlotRules.bowConflictsWithOffhand(true, 4, false, 3)
			|| !EquipmentSlotRules.bowConflictsWithOffhand(false, 3, true, 4)
			|| EquipmentSlotRules.bowConflictsWithOffhand(false, 4, false, 3)
			|| EquipmentSlotRules.allowsHandFootArmorOverlap(-1, 1)
			|| !EquipmentSlotRules.isOffenseSlot(4, EquipmentSlot.SLOT_MAINHAND)
			|| EquipmentSlotRules.isOffenseSlot(13, EquipmentSlot.SLOT_MAINHAND)
			|| !EquipmentSlotRules.isMajorArmorPenaltySlot(8)
			|| EquipmentSlotRules.isMajorArmorPenaltySlot(13)
			|| !EquipmentSlotRules.isArmorSlot(3)
			|| EquipmentSlotRules.isArmorSlot(12)) {
			throw new AssertionError("Equipment slot rules changed");
		}
	}

	private static void result(String name, int count, MessageDigest digest) {
		System.out.println("RESULT " + name + " " + count + " " + hex(digest.digest()));
	}

	private static void append(MessageDigest digest, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update((byte) (bytes.length >>> 24));
		digest.update((byte) (bytes.length >>> 16));
		digest.update((byte) (bytes.length >>> 8));
		digest.update((byte) bytes.length);
		digest.update(bytes);
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}
}
"""


def run_fixture() -> dict[str, tuple[int, str]]:
    subprocess.run([str(ROOT / "scripts/build-server.sh")], cwd=ROOT, check=True)
    with tempfile.TemporaryDirectory(prefix="server-equipment-calculation-") as temp:
        temp_path = Path(temp)
        source = temp_path / "com/openrsc/server/model/container/EquipmentCalculationFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            ["javac", "-cp", str(SERVER_JAR), "-d", temp, str(source)],
            cwd=ROOT,
            check=True,
        )
        result = subprocess.run(
            ["java", "-cp", f"{temp}:{SERVER_JAR}",
             "com.openrsc.server.model.container.EquipmentCalculationFixture"],
            cwd=ROOT / "server",
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise AssertionError(
                "Equipment calculation fixture failed:\n"
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )

    observed: dict[str, tuple[int, str]] = {}
    for line in result.stdout.splitlines():
        if not line.startswith("RESULT "):
            continue
        _, name, count, digest = line.split()
        observed[name] = (int(count), digest)
    return observed


def require_source_boundaries() -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    stat_calculator = STAT_CALCULATOR.read_text(encoding="utf-8")
    slot_rules = SLOT_RULES.read_text(encoding="utf-8")

    for snippet in (
        "EquipmentStatCalculator.godEquipmentResourceCost(itemId)",
        "EquipmentStatCalculator.legacyRangedOffense(item.getCatalogId())",
        "EquipmentStatCalculator.displayedOffense(",
        "EquipmentSlotRules.bowConflictsWithOffhand(",
        "EquipmentSlotRules.allowsHandFootArmorOverlap(",
    ):
        if snippet not in equipment:
            raise AssertionError(f"Equipment is missing extracted calculation boundary: {snippet}")
    for forbidden in ("case BRONZE_THROWING_DART:",):
        if forbidden in equipment:
            raise AssertionError(f"Equipment still owns pure stat table: {forbidden}")
    for forbidden in ("Player", "ActionSender", "EquipRequest", "UnequipRequest"):
        if forbidden in stat_calculator or forbidden in slot_rules:
            raise AssertionError(f"Pure equipment calculator depends on mutation owner: {forbidden}")
    if "void equipItem" in stat_calculator or "void equipItem" in slot_rules:
        raise AssertionError("Extracted calculators must not own equipment mutation")


def main() -> None:
    require_source_boundaries()
    observed = run_fixture()
    if not EXPECTED:
        for name, (count, digest) in observed.items():
            print(f'    "{name}": ({count}, "{digest}"),')
        raise AssertionError("Record the published-main equipment calculation fingerprints")
    if observed != EXPECTED:
        changed = sorted(
            name for name in set(observed) & set(EXPECTED)
            if observed[name] != EXPECTED[name]
        )
        raise AssertionError(
            "Equipment calculation fingerprint drift: "
            f"missing={sorted(set(EXPECTED) - set(observed))} "
            f"extra={sorted(set(observed) - set(EXPECTED))} changed={changed}"
        )
    print("PASS: equipment calculation tables match the approved fingerprints")


if __name__ == "__main__":
    main()
