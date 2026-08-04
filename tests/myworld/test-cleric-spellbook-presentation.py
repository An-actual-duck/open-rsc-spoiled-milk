#!/usr/bin/env python3
"""Validate C06 Cleric metadata transport, client presentation, and boundaries."""

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
CLIENT = ROOT / "Client_Base/src"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def validate_wiring() -> None:
    legacy_spells = read(SERVER / "constants/Spells.java")
    custom_generator = read(SERVER / "net/rsc/generators/impl/PayloadCustomGenerator.java")
    custom_parser = read(SERVER / "net/rsc/parsers/impl/PayloadCustomParser.java")
    sender = read(SERVER / "net/rsc/ActionSender.java")
    handler = read(SERVER / "net/rsc/handlers/InterfaceOptionHandler.java")
    casting = read(SERVER / "content/cleric/runtime/ClericSupportCasting.java")
    options = read(SERVER / "constants/custom/InterfaceOptions.java")
    packet_handler = read(CLIENT / "orsc/PacketHandler.java")
    mudclient = read(CLIENT / "orsc/mudclient.java")

    require("CLERIC" not in legacy_spells,
            "Cleric identities must not enter the compatibility-sensitive Magic enum")
    require("put(OpcodeOut.SEND_CLERIC_SPELLBOOK, 158)" in custom_generator,
            "custom Cleric catalog opcode is missing")
    require("case SEND_CLERIC_SPELLBOOK:" in custom_generator,
            "custom Cleric catalog payload is missing")
    for field in (
        "getStableCode()", "getStableKey()", "getDisplayName()",
        "getEffectDescription()", "getWorshipLevel()", "getSpellTier()",
        "getRadius()", "affectsCaster()", "getSpellbookIconItemId()",
        "getCasterIconItemId()", "getCasterAnimationId()",
    ):
        require(field in custom_generator, f"catalog transport omits {field}")
    require("sendClericSpellbook(player);" in sender,
            "login does not send authoritative Cleric metadata")
    require("CAST_CLERIC_SPELL(26)" in options,
            "stable immediate-cast interface option is missing")
    require("case CAST_CLERIC_SPELL:" in custom_parser,
            "custom parser does not accept the immediate cast request")

    method = handler.split("private void handleClericSpellCastRequest", 1)[1].split(
        "private void handleAutoCastSpell", 1)[0]
    for boundary in (
        "player.isUsingCustomClient()", "WANT_MYWORLD", "ClericSpellCatalog.fromCode",
        "ClericSupportCasting.isPvpContext(player)", "getMaxStat(Skill.PRAYER.id())",
    ):
        require(boundary in method, f"cast request omits {boundary} boundary")
    require("ClericSupportCasting.cast(player, definition)" in method,
            "C07 cast transaction is not connected to the C06 request")
    for pvp_boundary in ("USES_PK_MODE", "inWilderness()", "isDuelActive()"):
        require(pvp_boundary in casting,
                f"shared C07 PvP boundary omits {pvp_boundary}")
    for spell in ("UNIFY", "PURIFY", "RESTORE"):
        require(f"spellId == ClericSpellId.{spell}" in casting,
                f"approved Cleric runtime omits {spell}")
    for forbidden in ("remove(", "addExperience", "incExp", "setLevel", "setSkill"):
        require(forbidden not in method,
                f"C06 request unexpectedly mutates gameplay through {forbidden}")

    require("else if (opcode == 158) updateClericSpellbook();" in packet_handler,
            "client does not dispatch the authoritative catalog")
    require("replaceClericSpellbook(schemaVersion, definitions)" in packet_handler,
            "client does not validate and replace its catalog snapshot")
    for label in ('"Spells"', '"Mage"', '"Cleric"'):
        require(label in mudclient, f"spellbook UI omits {label}")
    require("spellsSubtab = SPELLS_SUBTAB_MAGE" in mudclient,
            "Mage is not the fresh-session default")
    require("switchSpellsSubtab(clickedSubtab)" in mudclient,
            "spell subtab choice is not retained")
    require("activateClericSpell(clericSpellCode)" in mudclient,
            "Cleric icon/text click does not cast immediately")
    require("putByte(INTERFACE_OPTION_CAST_CLERIC_SPELL)" in mudclient,
            "client does not submit the stable Cleric cast request")
    require("this.playerStatBase[5] >= definition.getWorshipLevel()" in mudclient,
            "client Worship gate does not match the server's trained-level gate")
    for name in (
        '"Mend"', '"Unify"', '"Fervor"', '"Purify"', '"Restore"', '"Ward"',
        '"Greater Mend"', '"Zeal"', '"Thorns"', '"Aegis"', '"Rally"', '"Respite"',
    ):
        require(name not in mudclient,
                f"client duplicates authoritative spell metadata for {name}")

    for generator in (SERVER / "net/rsc/generators/impl").glob("Payload*Generator.java"):
        if generator.name != "PayloadCustomGenerator.java":
            require("SEND_CLERIC_SPELLBOOK" not in read(generator),
                    f"authentic generator depends on Cleric metadata: {generator.name}")
    for parser in (SERVER / "net/rsc/parsers/impl").glob("Payload*Parser.java"):
        if parser.name != "PayloadCustomParser.java":
            require("CAST_CLERIC_SPELL" not in read(parser),
                    f"authentic parser accepts Cleric casts: {parser.name}")

    version_surfaces = (
        CLIENT / "orsc/Config.java",
        ROOT / "server/myworld.conf",
        ROOT / "server/myworld-host.conf",
        ROOT / "release/world-builder-v2/world-builder-runtime.conf",
    )
    for surface in version_surfaces:
        require("10051" in read(surface),
                f"maintained-client protocol version drift: {surface.relative_to(ROOT)}")


FIXTURE = r"""
package test;

import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import com.openrsc.server.content.cleric.ClericSpellPresentation;
import orsc.ClericSpellbookCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ClericSpellbookPresentationFixture {
	private interface Action { void run(); }

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void reject(Action action, String message) {
		try {
			action.run();
			throw new AssertionError("Expected rejection: " + message);
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static ClericSpellDef definition(int code, String key) {
		return new ClericSpellDef(code, key, "Name " + code, "Description " + code,
			"neutral", 1 + code, 1, 2, false, 3300, 3300, 1, 3308, 0, -1, -1);
	}

	public static void main(String[] args) {
		ClericSpellDef first = definition(0, "cleric.first");
		ClericSpellDef second = definition(1, "cleric.second");
		ClericSpellbookCatalog catalog = new ClericSpellbookCatalog();
		catalog.replace(ClericSpellbookCatalog.SCHEMA_VERSION, Arrays.asList(first, second));
		check(catalog.size() == 2, "catalog size drift");
		check(catalog.get(0) == first && catalog.get(1) == second, "stable code lookup drift");
		check(catalog.get(-1) == null && catalog.get(2) == null, "unknown code must be absent");
		try {
			catalog.snapshot().clear();
			throw new AssertionError("snapshot must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected.
		}
		reject(new Action() { public void run() {
			catalog.replace(3, Arrays.asList(first));
		} }, "unknown schema");
		reject(new Action() { public void run() {
			catalog.replace(2, Arrays.asList(second));
		} }, "out-of-order code");
		reject(new Action() { public void run() {
			catalog.replace(2, Arrays.asList(first, definition(1, "cleric.first")));
		} }, "duplicate key");
		List<ClericSpellDef> oversized = new ArrayList<ClericSpellDef>();
		for (int i = 0; i <= ClericSpellbookCatalog.MAX_DEFINITIONS; i++) {
			oversized.add(definition(i, "cleric." + i));
		}
		reject(new Action() { public void run() {
			catalog.replace(2, oversized);
		} }, "oversized catalog");

		final int[] calls = {0, 0};
		ClericSpellPresentation.Hooks hooks = new ClericSpellPresentation.Hooks() {
			public void showCasterIcon(int itemId) { calls[0] += itemId; }
			public void showCasterAnimation(int animationId) { calls[1] += animationId; }
		};
		ClericSpellPresentation absent = new ClericSpellPresentation(3300, -1, -1);
		absent.dispatch(hooks);
		check(calls[0] == 0 && calls[1] == 0, "absent visuals must be a no-op");
		ClericSpellPresentation configured = new ClericSpellPresentation(3300, 3300, 77);
		configured.dispatch(hooks);
		check(calls[0] == 3300 && calls[1] == 77, "configured visual hooks did not dispatch");
		reject(new Action() { public void run() {
			new ClericSpellPresentation(3300, -2, -1);
		} }, "invalid optional identifier");
	}
}
"""


def run_compiled_fixture() -> None:
    sources = [
        CLIENT / "com/openrsc/client/entityhandling/defs/ClericEffectRankDef.java",
        CLIENT / "com/openrsc/client/entityhandling/defs/ClericSpellDef.java",
        CLIENT / "orsc/ClericSpellbookCatalog.java",
        SERVER / "content/cleric/ClericSpellPresentation.java",
    ]
    with tempfile.TemporaryDirectory(prefix="cleric-presentation-") as temporary:
        temp = Path(temporary)
        fixture = temp / "test/ClericSpellbookPresentationFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            ["javac", "-d", str(classes), *(str(source) for source in sources), str(fixture)],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes), "test.ClericSpellbookPresentationFixture"],
            check=True,
        )


def main() -> None:
    validate_wiring()
    run_compiled_fixture()
    print("Cleric spellbook presentation checks passed")


if __name__ == "__main__":
    main()
