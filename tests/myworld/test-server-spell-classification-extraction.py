#!/usr/bin/env python3
"""Characterize pure server spell classification before and after extraction."""

from __future__ import annotations

import re
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_JAR = ROOT / "server/core.jar"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
CLASSIFICATION = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellClassification.java"
VALIDATION = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellValidationRules.java"

EXPECTED: dict[str, tuple[int, str]] = {
    "isAirSpell": (82, "1ef5ecd10634a5ea0fb55618646c3ba11487f1d33213fdd35a722fa939f9ba06"),
    "isWaterSpell": (82, "ecba9ce635abec1cef93d2f8c085f6bf2ae2431e19a58e4cee722e1357474472"),
    "isEarthSpell": (82, "ea00e3be6b52c63079c8d51e6e31a476e51c09b6c6e5afc9e2f716f9621a71d5"),
    "isFireSpell": (82, "b3f0377bd7b40cd6f1810d9269a4150e9dc3aad1083e79aad5ca303bf03e42f4"),
    "isMindSpell": (82, "aeda2931816422067265e2a90a85bc41d88a7f4705492b7f4afb26cf04dea692"),
    "isThunderSpell": (82, "bccdbb86ddfedcca2fe929c594a4ffe0c94c3d69f5c3a8c97d03f2e3a9fee36f"),
    "isAcidSpell": (82, "90d32809776a90d11a7fa0bb3298445e5b427298b0d8ca128ebb937427033816"),
    "isIceSpell": (82, "03bb4fc13bc8266b7de4874800ea80c990c1041610a41f17b9f8db3b901e4323"),
    "isWoodSpell": (82, "d2298047f9282105e3ca7c1063c890599b6aedebc41c8b51af5f1af6e0d93db8"),
    "isGuthixGodSpell": (82, "a21163cd9833577c483160f7d2a846809b9866221831c58ee9bc6419aca2cf08"),
    "isSaradominGodSpell": (82, "22289f03fbc1764786237e064d3dd7acb728e835e07cd7bda7e5821adbc4a0cd"),
    "isZamorakGodSpell": (82, "25bdd5499503c668091d8c5188ae786e7a00bd17b2ceecd8564590812aa2896e"),
    "isAdvancedGodSpell": (82, "b4df9db4af62c94da757dfe1ec3a1a5d8a1dfddf4ee6122825aece3483fffd43"),
    "isHealSpell": (82, "64942a4d0d8b414cbb1cf20222d9723a7d1847f48192bd82f65476cf753be59b"),
    "isTeleportSpell": (82, "6f6c8ebc41fb1e5d825de96a8a59d3ed585ace5794e0b43021e64b1d43cb9ccc"),
    "getSpellProjectileVisual": (82, "fe456787129e2e48afaf43a9af3155528ba1f054c43a48b1b00ac75317c25cc2"),
    "getSpellImpactEffect": (82, "cf0db1b21976d4c122a5a4666d8bdaa0633b441ad0c402804b3c01d3e98b5e40"),
    "getGodSpellProjectileVisual": (82, "d76fac97e09d5fd1b3ea6f023a2872a5c09602f964805e5d4df2aff5c4add57b"),
    "getGodSpellImpactEffect": (82, "f8b7e7c089da0898a5ee8ec73656389575b398022d0e0e0440b8159ef3307cd3"),
    "getHealCombatEffect": (82, "456253b748b51b0c43ef70c3016934cfb2b77ad07c440ba046d47e4f07a8bad3"),
    "getElementalSpellTier": (82, "f049e962c86db3452baa4f2c2772a06e131b7b2540c88cdceccf724e0f8ed371"),
    "getWindAccuracyDebuffPercent": (82, "e1ff4a0700d8f8d2df881737426029a6b1de1202431f106fd58fad9695949399"),
    "getWaterMaxHitDebuffPercent": (82, "e1ff4a0700d8f8d2df881737426029a6b1de1202431f106fd58fad9695949399"),
    "getEarthAttackSpeedDebuffPercent": (82, "569f3fba753ec6885d2a4368fc170d7894e2e9b166da0d1cfa19ca528d599b16"),
    "getFireDefenseDebuffPercent": (82, "569f3fba753ec6885d2a4368fc170d7894e2e9b166da0d1cfa19ca528d599b16"),
    "getDualElementProcChancePercent": (82, "b5fb25ea4a49efbe5bbd7a91e3c146f2c8ad5bbb1cb0c3adc9718cb8df865748"),
    "getStartleProcChancePercent": (82, "52da6f0c74f72222f20d1fd8dc62c512ee1ade4749365f98a45273b579d49776"),
    "getAcidPoisonPower": (82, "34bb066130a2412deb99c5c5d673985103ff3cd3be43547ce1a7b62695740ad9"),
    "getFrostbiteProcChancePercent": (82, "158e8a7bc299588d2d466ec6ec193c79fc2711861c5c3e1996bcccc1aa2567ec"),
    "getSplinterProcChancePercent": (82, "7a07abcb6a52543b2e0934d2588b8b54c60fef635941a7a3012fb7c44ae3e0dc"),
    "getSpellDamageCapPercent": (82, "a0aab51bcc26faf4392e3a3e00e9be08d7afe3d60b689f7bdbc66867055b173a"),
    "isBoostSpell": (82, "2ec0f21a21983887f55dbbce2f93010f248f6abc047a310499fbb2fe9a9b7504"),
    "doesRingMatchSpellElement": (12382, "bc98ce292b4653faffcc327e3374893d68b22967d7a82d8661cbe56851307d15"),
    "shouldShowSpellProjectile": (3444, "70d611761e1349e6eb0e67d8a34d58f4213b730c562e22a0a0607efd6f86d1ae"),
    "isMobCastOpcode": (98, "9e6c6fe1c19ba0358ce77d157b7c90684f321094699419f1a746d12fa17f5629"),
}

FIXTURE = r"""
package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.Spells;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.net.rsc.enums.OpcodeIn;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SpellClassificationFixture {
	private static final String[] BOOLEAN_METHODS = {
		"isAirSpell", "isWaterSpell", "isEarthSpell", "isFireSpell", "isMindSpell",
		"isThunderSpell", "isAcidSpell", "isIceSpell", "isWoodSpell",
		"isGuthixGodSpell", "isSaradominGodSpell", "isZamorakGodSpell",
		"isAdvancedGodSpell", "isHealSpell", "isTeleportSpell", "isBoostSpell"
	};
	private static final String[] INTEGER_METHODS = {
		"getSpellProjectileVisual", "getSpellImpactEffect", "getGodSpellProjectileVisual",
		"getGodSpellImpactEffect", "getHealCombatEffect", "getElementalSpellTier",
		"getWindAccuracyDebuffPercent", "getWaterMaxHitDebuffPercent",
		"getEarthAttackSpeedDebuffPercent", "getFireDefenseDebuffPercent",
		"getDualElementProcChancePercent", "getStartleProcChancePercent",
		"getAcidPoisonPower", "getFrostbiteProcChancePercent", "getSplinterProcChancePercent"
	};

	private SpellClassificationFixture() {
	}

	public static void main(String[] args) throws Exception {
		for (String methodName : BOOLEAN_METHODS) {
			fingerprintSpellMethod(methodName);
		}
		for (String methodName : INTEGER_METHODS) {
			fingerprintSpellMethod(methodName);
		}
		fingerprintSpellMethod("getSpellDamageCapPercent");
		fingerprintRingMatches();
		fingerprintProjectileVisibility();
		fingerprintOpcodes();
		validateRules();
	}

	private static void fingerprintSpellMethod(String methodName) throws Exception {
		Method method = SpellClassification.class.getDeclaredMethod(methodName, Spells.class);
		method.setAccessible(true);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		int count = 0;
		append(digest, "null:" + method.invoke(null, new Object[] {null}));
		count++;
		for (Spells spell : Spells.values()) {
			append(digest, spell.name() + ":" + method.invoke(null, spell));
			count++;
		}
		result(methodName, count, digest);
	}

	private static void fingerprintRingMatches() throws Exception {
		Method method = SpellClassification.class.getDeclaredMethod(
			"doesRingMatchSpellElement", int.class, Spells.class);
		method.setAccessible(true);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		int count = 0;
		for (int ringId = 1600; ringId <= 1750; ringId++) {
			append(digest, ringId + ":null:" + method.invoke(null, ringId, null));
			count++;
			for (Spells spell : Spells.values()) {
				append(digest, ringId + ":" + spell.name() + ":"
					+ method.invoke(null, ringId, spell));
				count++;
			}
		}
		result("doesRingMatchSpellElement", count, digest);
	}

	private static void fingerprintProjectileVisibility() throws Exception {
		Method method = SpellClassification.class.getDeclaredMethod(
			"shouldShowSpellProjectile", Spells.class, int.class);
		method.setAccessible(true);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		int count = 0;
		for (int impact = -1; impact <= 40; impact++) {
			append(digest, "null:" + impact + ":" + method.invoke(null, null, impact));
			count++;
			for (Spells spell : Spells.values()) {
				append(digest, spell.name() + ":" + impact + ":"
					+ method.invoke(null, spell, impact));
				count++;
			}
		}
		result("shouldShowSpellProjectile", count, digest);
	}

	private static void fingerprintOpcodes() throws Exception {
		Method method = SpellClassification.class.getDeclaredMethod("isMobCastOpcode", OpcodeIn.class);
		method.setAccessible(true);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		int count = 0;
		append(digest, "null:" + method.invoke(null, new Object[] {null}));
		count++;
		for (OpcodeIn opcode : OpcodeIn.values()) {
			append(digest, opcode.name() + ":" + method.invoke(null, opcode));
			count++;
		}
		result("isMobCastOpcode", count, digest);
	}

	private static void validateRules() {
		expect(SpellValidationRules.magicSkillId(false, false) == Skill.MAGIC.id(),
			"unified magic skill");
		expect(SpellValidationRules.magicSkillId(false, true) == Skill.MAGIC.id(),
			"unified evil spell skill");
		expect(SpellValidationRules.magicSkillId(true, false) == Skill.GOODMAGIC.id(),
			"divided good magic skill");
		expect(SpellValidationRules.magicSkillId(true, true) == Skill.EVILMAGIC.id(),
			"divided evil magic skill");

		expectAutoCast(SpellValidationRules.AutoCastRejection.MISSING_SELECTION,
			null, false, 0, true, false, 1, 99);
		expectAutoCast(SpellValidationRules.AutoCastRejection.NON_COMBAT,
			Spells.WIND_STRIKE, false, 2, true, false, 1, 99);
		expectAutoCast(SpellValidationRules.AutoCastRejection.NON_COMBAT,
			Spells.WIND_STRIKE, true, 0, true, false, 1, 99);
		expectAutoCast(SpellValidationRules.AutoCastRejection.MEMBERS_ONLY,
			Spells.WIND_STRIKE, true, 2, true, false, 1, 99);
		expectAutoCast(SpellValidationRules.AutoCastRejection.LEVEL_TOO_LOW,
			Spells.WIND_STRIKE, true, 2, false, false, 1, 2);
		expectAutoCast(SpellValidationRules.AutoCastRejection.NONE,
			Spells.WIND_STRIKE, true, 2, true, true, 2, 2);

		expectDefinition(SpellValidationRules.CastDefinitionRejection.MISSING_SELECTION,
			null, false, true, false, 1, 99);
		expectDefinition(SpellValidationRules.CastDefinitionRejection.MISSING_DEFINITION,
			Spells.WIND_STRIKE, false, true, false, 1, 99);
		expectDefinition(SpellValidationRules.CastDefinitionRejection.MEMBERS_ONLY,
			Spells.WIND_STRIKE, true, true, false, 1, 99);
		expectDefinition(SpellValidationRules.CastDefinitionRejection.LEVEL_TOO_LOW,
			Spells.WIND_STRIKE, true, false, false, 1, 2);
		expectDefinition(SpellValidationRules.CastDefinitionRejection.NONE,
			Spells.WIND_STRIKE, true, true, true, 2, 2);

		expect(SpellValidationRules.requiresTeleportValidation(
			OpcodeIn.CAST_ON_SELF, 0, Spells.VARROCK_TELEPORT), "self teleport validation");
		expect(!SpellValidationRules.requiresTeleportValidation(
			OpcodeIn.CAST_ON_SELF, 2, Spells.VARROCK_TELEPORT), "combat spell is not teleport");
		expect(!SpellValidationRules.requiresTeleportValidation(
			OpcodeIn.CAST_ON_NPC, 0, Spells.VARROCK_TELEPORT), "targeted teleport opcode");
		expect(!SpellValidationRules.requiresTeleportValidation(
			OpcodeIn.CAST_ON_SELF, 0, Spells.WIND_STRIKE), "non-teleport self spell");
		expect(!SpellValidationRules.requiresTeleportValidation(
			OpcodeIn.CAST_ON_SELF, 0, null), "missing teleport selection");

		expect(!SpellValidationRules.isDuelActionBlocked(OpcodeIn.CAST_ON_SELF, false),
			"inactive duel");
		expect(!SpellValidationRules.isDuelActionBlocked(OpcodeIn.PLAYER_CAST_PVP, true),
			"duel opponent cast");
		expect(SpellValidationRules.isDuelActionBlocked(OpcodeIn.CAST_ON_SELF, true),
			"duel non-opponent cast");
	}

	private static void expectAutoCast(
		SpellValidationRules.AutoCastRejection expected,
		Spells spell,
		boolean hasDefinition,
		int spellType,
		boolean membersSpell,
		boolean memberWorld,
		int currentLevel,
		int requiredLevel) {
		expect(SpellValidationRules.validateAutoCast(
			spell, hasDefinition, spellType, membersSpell, memberWorld, currentLevel, requiredLevel) == expected,
			"auto-cast rejection " + expected);
	}

	private static void expectDefinition(
		SpellValidationRules.CastDefinitionRejection expected,
		Spells spell,
		boolean hasDefinition,
		boolean membersSpell,
		boolean memberWorld,
		int currentLevel,
		int requiredLevel) {
		expect(SpellValidationRules.validateDefinition(
			spell, hasDefinition, membersSpell, memberWorld, currentLevel, requiredLevel) == expected,
			"cast-definition rejection " + expected);
	}

	private static void expect(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
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
    with tempfile.TemporaryDirectory(prefix="server-spell-classification-") as temp:
        temp_path = Path(temp)
        source = temp_path / "com/openrsc/server/net/rsc/handlers/SpellClassificationFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            ["javac", "-cp", str(SERVER_JAR), "-d", temp, str(source)],
            cwd=ROOT,
            check=True,
        )
        result = subprocess.run(
            ["java", "-cp", f"{temp}:{SERVER_JAR}",
             "com.openrsc.server.net.rsc.handlers.SpellClassificationFixture"],
            cwd=ROOT / "server",
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise AssertionError(
                "Spell classification fixture failed:\n"
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )

    observed: dict[str, tuple[int, str]] = {}
    for line in result.stdout.splitlines():
        if not line.startswith("RESULT "):
            continue
        _, name, count, digest = line.split()
        observed[name] = (int(count), digest)
    return observed


def assert_source_boundaries() -> None:
    handler = SPELL_HANDLER.read_text(encoding="utf-8")
    classification = CLASSIFICATION.read_text(encoding="utf-8")
    validation = VALIDATION.read_text(encoding="utf-8")

    for method in EXPECTED:
        if method not in classification:
            raise AssertionError(f"SpellClassification does not own {method}")
        if re.search(rf"\n\tprivate (?:static )?[^\n]+ {method}\(", handler):
            raise AssertionError(f"SpellHandler retains classification wrapper {method}")

    for forbidden in (
        "Player", "ActionSender", "getGameEventHandler", "checkAndRemoveRunes",
        "canTeleport", "remove(new Item", "setSuspiciousPlayer",
    ):
        if forbidden in classification:
            raise AssertionError(f"SpellClassification owns side effect or player state: {forbidden}")

    for forbidden in (
        "Player", "ActionSender", "getGameEventHandler", "checkAndRemoveRunes",
        "canTeleport", "player.message", "setSuspiciousPlayer",
    ):
        if forbidden in validation:
            raise AssertionError(f"SpellValidationRules owns side effect or player state: {forbidden}")

    for retained in (
        "checkAndRemoveRunes", "canTeleport", "getGameEventHandler()",
        "SpellClassification", "SpellValidationRules", "player.message",
    ):
        if retained not in handler:
            raise AssertionError(f"SpellHandler lost orchestration responsibility: {retained}")

    for consumed in (
        "SpellClassification.isMobCastOpcode(opcode)",
        "SpellClassification.isHealSpell(payload.spell)",
        "SpellClassification.isTeleportSpell(payload.spell)",
        "SpellClassification.isBoostSpell(payload.spell)",
        "SpellClassification.getSpellDamageCapPercent(spellEnum)",
        "SpellClassification.getGodSpellProjectileVisual(spellEnum)",
        "SpellClassification.getSpellProjectileVisual(spellEnum)",
        "SpellClassification.isBloodSpell(spell)",
    ):
        if consumed not in handler:
            raise AssertionError(f"SpellHandler does not consume extracted decision: {consumed}")


def main() -> None:
    observed = run_fixture()
    assert_source_boundaries()
    if not EXPECTED:
        for name, (count, digest) in observed.items():
            print(f'    "{name}": ({count}, "{digest}"),')
        raise AssertionError("Record the published-main spell classification fingerprints")
    if observed != EXPECTED:
        changed = sorted(
            name for name in set(observed) & set(EXPECTED)
            if observed[name] != EXPECTED[name]
        )
        raise AssertionError(
            "Spell classification fingerprint drift: "
            f"missing={sorted(set(EXPECTED) - set(observed))} "
            f"extra={sorted(set(observed) - set(EXPECTED))} changed={changed}"
        )
    print("PASS: spell classifications preserve published-main values and validation boundaries")


if __name__ == "__main__":
    main()
