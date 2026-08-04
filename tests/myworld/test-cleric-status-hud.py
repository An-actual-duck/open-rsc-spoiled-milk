#!/usr/bin/env python3
"""Compile and validate the C08B mixed-status packet, model, and assets."""

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


def validate_sources() -> None:
    sender = read(SERVER / "net/rsc/ActionSender.java")
    generator = read(SERVER / "net/rsc/generators/impl/PayloadCustomGenerator.java")
    collector = read(SERVER / "content/status/ClericActiveStatusCollector.java")
    catalog = read(SERVER / "content/cleric/ClericSpellCatalog.java")
    presentation = read(SERVER / "content/cleric/ClericSpellPresentation.java")
    packet_handler = read(CLIENT / "orsc/PacketHandler.java")
    mudclient = read(CLIENT / "orsc/mudclient.java")
    build = read(ROOT / "Client_Base/build.xml")

    prefix_parts = (
        "builder.writeByte((byte) potionEffectCount)",
        "builder.writeShort(potionEffects.itemIds[i])",
        "builder.writeInt(potionEffects.remainingSeconds[i])",
        "builder.writeShort(Math.min(65535, potionEffectOverflow))",
        "builder.writeByte((byte) ActivePotionEffectsStruct.EXTENSION_VERSION)",
    )
    positions = [generator.index(part) for part in prefix_parts]
    require(positions == sorted(positions), "opcode 152 legacy prefix order drift")
    for field in ("identityKinds", "stableIdentities", "ranks", "counterKinds",
                  "remainingCounters"):
        require(field in sender and field in generator,
                f"opcode 152 trailer omits {field}")
    require("if (!player.isUsingCustomClient())" in sender,
            "authentic clients can receive the maintained status packet")
    login = sender[sender.index("static void sendLogin(Player player)"):]
    require(login.index("sendClericSpellbook(player);")
            < login.index("sendActivePotionEffects(player);"),
            "login sends enriched statuses before their validating Cleric catalog")
    for path in (SERVER / "net/rsc/generators/impl").glob("Payload*Generator.java"):
        if path.name != "PayloadCustomGenerator.java":
            require("SEND_ACTIVE_POTION_EFFECTS" not in read(path),
                    f"authentic generator exposes mixed status packet: {path.name}")

    require("instanceof ClericEffectRegistry" in collector,
            "Cleric status collection is not guarded by the optional registry type")
    require("installTransientEffectState" not in collector + sender,
            "C08B made the incomplete Cleric registry reachable")
    require("ClericEffectCatalog.getRanks" in generator,
            "catalog rank presentation does not derive from typed effect authority")
    require("SCHEMA_VERSION = 2" in catalog,
            "Cleric catalog schema was not advanced")
    require("id == ClericSpellId.RESPITE" in catalog and 'return "";' in catalog,
            "Respite no longer retains its explicit sigil fallback")
    require("casterIconItemId" in presentation and "casterAnimationId" in presentation,
            "optional later visual hooks were removed")
    require("ClericSpellPresentation.NONE, ClericSpellPresentation.NONE" in catalog,
            "C08B assigned an unapproved caster icon or animation")
    require("ActiveStatusPacketDecoder.decode(payload)" in packet_handler,
            "client does not defensively decode the complete status payload")
    require("ActiveStatusHudModel activeStatusHud" in mudclient,
            "mixed timer/counter state remains embedded in mudclient")
    require("getClericHoverText()" in mudclient and "getCounterBadge()" in mudclient,
            "Cleric hover or counter presentation is not rendered")
    require("POTION_HUD_ROWS_PER_COLUMN = 8" in mudclient,
            "eight-row HUD layout changed")
    require('"+" + snapshot.getOverflowCount() + " more effects"' in mudclient,
            "mixed HUD overflow disclosure is missing")

    icons = ROOT / "dev/myworld/assets/sprites/UI/cleric"
    mapped = {
        "mend", "unify", "fervor", "purify", "restore", "ward",
        "greater-mend", "zeal", "thorns", "aegis", "rally",
    }
    for key in mapped:
        require((icons / f"{key}.png").is_file(), f"packaged Cleric icon missing: {key}")
    require((icons / "respite.png").is_file(),
            "supplied but intentionally unmapped Respite source asset is missing")
    require('<include name="sprites/**/*.png"' in build,
            "Cleric PNG directory is not included by client packaging")
    require("on-entity" not in catalog + presentation + collector,
            "C08B assigned an unapproved animation sheet")

    for surface in (
        CLIENT / "orsc/Config.java",
        ROOT / "server/myworld.conf",
        ROOT / "server/myworld-host.conf",
        ROOT / "release/world-builder-v2/world-builder-runtime.conf",
    ):
        require("10051" in read(surface),
                f"maintained protocol version drift: {surface.relative_to(ROOT)}")


SERVER_FIXTURE = r"""
package test;

import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.content.status.ActiveStatusInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ActiveStatusInventoryFixture {
	private interface Action { void run(); }
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
	private static void reject(Action action, String message) {
		try { action.run(); throw new AssertionError(message); }
		catch (IllegalArgumentException expected) { }
	}
	private static ActiveStatusEntry item(String key, int id, int seconds) {
		return ActiveStatusEntry.item(key, id, seconds);
	}
	public static void main(String[] args) {
		List<String> expected = Arrays.asList(
			"cleric:healing_pulses", "cleric:protection", "potion:brawn",
			"potion:deftness", "cleric:fervor", "cleric:rally",
			"potion:stat_reduction_protection", "cleric:thorns", "cleric:zeal",
			"potion:magic_resistance", "potion:melee_resistance",
			"potion:poison_protection", "potion:ranged_resistance",
			"potion:regeneration", "cleric:respite", "potion:insight",
			"potion:insight_skills", "potion:luck", "potion:notation",
			"potion:skiller", "potion:speed", "potion:warrior");
		check(ActiveStatusInventory.getAuthoredOrder().equals(expected),
			"authored mixed priority drift");

		ArrayList<ActiveStatusEntry> reversed = new ArrayList<ActiveStatusEntry>();
		for (int index = expected.size() - 1; index >= 0; index--) {
			reversed.add(item(expected.get(index), 100 + index, 10 + index));
		}
		ActiveStatusInventory ordered = ActiveStatusInventory.select(reversed);
		for (int index = 0; index < expected.size(); index++) {
			check(ordered.getVisible().get(index).getStableKey().equals(expected.get(index)),
				"priority mismatch at " + index);
		}

		for (final int size : new int[] {31, 32, 33, 64}) {
			ArrayList<ActiveStatusEntry> entries = new ArrayList<ActiveStatusEntry>();
			for (int index = 0; index < size; index++) {
				entries.add(item(String.format("future:%02d", index), index, index + 1));
			}
			ActiveStatusInventory inventory = ActiveStatusInventory.select(entries);
			check(inventory.getVisible().size() == Math.min(32, size),
				"visible bound drift at " + size);
			check(inventory.getOverflowCount() == Math.max(0, size - 32),
				"overflow drift at " + size);
			check(entries.size() == size, "selection removed gameplay state");
		}
		ArrayList<ActiveStatusEntry> sixtyFive = new ArrayList<ActiveStatusEntry>();
		for (int index = 0; index < 65; index++) {
			sixtyFive.add(item("future:" + index, index, 10));
		}
		reject(new Action() { public void run() {
			ActiveStatusInventory.select(sixtyFive);
		} }, "65-entry collection must be rejected");

		List<ActiveStatusEntry> timersA = Arrays.asList(
			item("potion:warrior", 1, 1), item("potion:brawn", 2, 999));
		List<ActiveStatusEntry> timersB = Arrays.asList(
			item("potion:warrior", 1, 999), item("potion:brawn", 2, 1));
		check(ActiveStatusInventory.select(timersA).getVisible().get(0).getStableKey()
			.equals(ActiveStatusInventory.select(timersB).getVisible().get(0).getStableKey()),
			"timer changes reordered statuses");

		ActiveStatusEntry cleric = ActiveStatusEntry.cleric("cleric:protection", 5,
			3300, 30, 3, ActiveStatusEntry.CounterKind.CHARGES, 6);
		check(cleric.getRank() == 3 && cleric.getRemainingCounter() == 6,
			"Cleric counter snapshot drift");
		reject(new Action() { public void run() {
			ActiveStatusEntry.cleric("cleric:bad", 5, 3300, 30, 3,
				ActiveStatusEntry.CounterKind.NONE, 1);
		} }, "NONE counter accepted a value");
	}
}
"""


CLIENT_FIXTURE = r"""
package test;

import com.openrsc.client.entityhandling.defs.ClericEffectRankDef;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import orsc.ActiveStatusHudModel;
import orsc.ActiveStatusPacketDecoder;
import orsc.ClericSpellbookCatalog;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ActiveStatusHudFixture {
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
	private static ClericEffectRankDef rank(int code, int rank) {
		int kind;
		int counter;
		int initial;
		int primary;
		int secondary = 0;
		switch (code) {
			case 0: case 6:
				kind = 1; counter = 2; initial = 3;
				primary = code == 0 ? new int[] {1, 2, 3, 3}[rank - 1] : rank + 1;
				break;
			case 2:
				kind = 2; counter = 0; initial = 0;
				primary = new int[] {5, 10, 15, 20}[rank - 1]; secondary = 1;
				break;
			case 5: case 9:
				kind = 3; counter = 1;
				primary = code == 5 ? 25 : 50;
				initial = code == 5 ? rank * 2 : rank;
				break;
			case 7:
				kind = 4; counter = 0; initial = 0;
				primary = new int[] {5, 8, 11, 15}[rank - 1]; break;
			case 8:
				kind = 5; counter = 0; initial = 0;
				primary = new int[] {5, 8, 11, 15}[rank - 1]; break;
			case 10:
				kind = 6; counter = 0; initial = 0; primary = 20;
				secondary = new int[] {55, 60, 65, 70}[rank - 1]; break;
			case 11:
			default:
				kind = 7; counter = 0; initial = 0;
				primary = new int[] {10, 15, 20, 25}[rank - 1]; break;
		}
		return new ClericEffectRankDef(rank, 30_000, kind, counter, initial,
			primary, secondary);
	}
	private static ClericSpellDef spell(int code) {
		String[] names = {"Mend", "Unify", "Fervor", "Purify", "Restore", "Ward",
			"Greater Mend", "Zeal", "Thorns", "Aegis", "Rally", "Respite"};
		String[] keys = {"mend", "unify", "fervor", "purify", "restore", "ward",
			"greater-mend", "zeal", "thorns", "aegis", "rally", ""};
		List<ClericEffectRankDef> ranks = new ArrayList<ClericEffectRankDef>();
		if (code != 1 && code != 3 && code != 4) {
			for (int value = 1; value <= (code == 0 ? 3 : 4); value++) {
				ranks.add(rank(code, value));
			}
		}
		return new ClericSpellDef(code, "cleric." + code, names[code], "Description",
			"neutral", 1, 1, 2, false, 3300 + code, 3300, 1, 3308, 0,
			-1, -1, keys[code], ranks);
	}
	private static ClericSpellbookCatalog catalog() {
		ArrayList<ClericSpellDef> definitions = new ArrayList<ClericSpellDef>();
		for (int code = 0; code < 12; code++) definitions.add(spell(code));
		ClericSpellbookCatalog catalog = new ClericSpellbookCatalog();
		catalog.replace(2, definitions);
		return catalog;
	}
	private static byte[] packet(int[][] prefix, int overflow, int[][] trailer) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeByte(prefix.length);
		for (int[] row : prefix) { out.writeShort(row[0]); out.writeInt(row[1]); }
		if (overflow >= 0) out.writeShort(overflow);
		if (trailer != null) {
			out.writeByte(1); out.writeByte(trailer.length);
			for (int[] row : trailer) {
				out.writeByte(row[0]); out.writeShort(row[1]); out.writeByte(row[2]);
				out.writeByte(row[3]); out.writeShort(row[4]);
			}
		}
		return bytes.toByteArray();
	}
	private static void fallback(byte[] bytes, String label) {
		ActiveStatusPacketDecoder.DecodedSnapshot decoded =
			ActiveStatusPacketDecoder.decode(bytes);
		check(decoded.getEntries().size() == 4, label + " (prefix lost)");
		ActiveStatusHudModel fallbackModel = new ActiveStatusHudModel();
		fallbackModel.replace(decoded, catalog(), 1_000L);
		for (ActiveStatusHudModel.Row row : fallbackModel.snapshot(1_000L).getRows()) {
			check(!row.isCleric(), label);
		}
	}
	public static void main(String[] args) throws Exception {
		ClericSpellbookCatalog catalog = catalog();
		int[][] prefix = {{50, 10}, {3302, 30}, {3305, 30}, {3300, 30}};
		int[][] trailer = {{0, 50, 0, 0, 0}, {1, 2, 3, 0, 0},
			{1, 5, 3, 1, 6}, {1, 0, 2, 2, 2}};
		byte[] valid = packet(prefix, 7, trailer);
		ActiveStatusPacketDecoder.DecodedSnapshot decoded =
			ActiveStatusPacketDecoder.decode(valid);
		check(decoded.isEnriched() && decoded.getOverflowCount() == 7,
			"valid mixed trailer rejected");
		ActiveStatusHudModel model = new ActiveStatusHudModel();
		model.replace(decoded, catalog, 1_000L);
		ActiveStatusHudModel.Snapshot snapshot = model.snapshot(2_500L);
		check(snapshot.getRows().size() == 4 && snapshot.getOverflowCount() == 7,
			"mixed snapshot size/overflow drift");
		check(snapshot.getRows().get(0).getRemainingSeconds() == 9,
			"client countdown rounding drift");
		check(!snapshot.getRows().get(0).isCleric(), "item row became Cleric");
		check(snapshot.getRows().get(1).getClericHoverText().equals(
			"Fervor III — 15% chance to raise offense roll by 1"), "Fervor hover drift");
		check(snapshot.getRows().get(2).getCounterBadge().equals("6H"),
			"charge badge drift");
		check(snapshot.getRows().get(2).getClericHoverText().equals(
			"Ward III — 25% reduction — 6 protected hits remaining"), "Ward hover drift");
		check(snapshot.getRows().get(3).getCounterBadge().equals("2P"),
			"pulse badge drift");
		check(snapshot.getRows().get(3).getClericHoverText().equals(
			"Mend II — 2 Hits per pulse — 2 healing pulses remaining"), "Mend hover drift");
		check(snapshot.getRows().get(2).getRemainingCounter() == 6
			&& model.snapshot(20_000L).getRows().get(1).getRemainingCounter() == 6,
			"client decremented authoritative counter");

		byte[] legacyNoOverflow = packet(prefix, -1, null);
		check(!ActiveStatusPacketDecoder.decode(legacyNoOverflow).isEnriched(),
			"legacy no-suffix prefix rejected");
		byte[] legacyOverflow = packet(prefix, 5, null);
		check(ActiveStatusPacketDecoder.decode(legacyOverflow).getOverflowCount() == 5,
			"legacy overflow suffix rejected");

		int extension = 3 + prefix.length * 6;
		byte[] unsupported = valid.clone(); unsupported[extension] = 9;
		fallback(unsupported, "unsupported version did not fall back");
		byte[] mismatch = valid.clone(); mismatch[extension + 1] = 3;
		fallback(mismatch, "count mismatch did not fall back");
		fallback(Arrays.copyOf(valid, valid.length - 1), "truncation did not fall back");
		fallback(Arrays.copyOf(valid, valid.length + 1), "trailing garbage did not fall back");
		int firstClericRecord = extension + 2 + 7;
		byte[] unknown = valid.clone();
		unknown[firstClericRecord + 1] = 0; unknown[firstClericRecord + 2] = 31;
		fallback(unknown, "unknown identity was partially enriched");
		byte[] invalidRank = valid.clone(); invalidRank[firstClericRecord + 3] = 0;
		fallback(invalidRank, "invalid rank was partially enriched");
		byte[] badCounter = valid.clone();
		int wardRecord = firstClericRecord + 7;
		badCounter[wardRecord + 4] = 0;
		fallback(badCounter, "counter kind/count mismatch was partially enriched");

		model.replace(decoded, new ClericSpellbookCatalog(), 1_000L);
		check(!model.snapshot(1_000L).getRows().get(1).isCleric(),
			"missing catalog did not retain safe prefix fallback");
		model.clear();
		check(model.snapshot(1_000L).getRows().isEmpty()
			&& model.snapshot(1_000L).getOverflowCount() == 0,
			"logout/reconnect clear retained state");
		model.replace(ActiveStatusPacketDecoder.decode(packet(
			new int[][] {{50, 1}}, 0, new int[][] {{0, 50, 0, 0, 0}})),
			catalog, 1_000L);
		check(model.snapshot(2_000L).getRows().isEmpty(),
			"local timer expiry did not compact the HUD");

		check(spell(10).getEffectRank(2).getSecondaryMagnitude() == 60,
			"Rally catalog magnitude drift");
		check(spell(11).getEffectRank(4).getPrimaryMagnitude() == 25,
			"Respite catalog magnitude drift");
		check(spell(11).getIconAssetKey().isEmpty(),
			"Respite should use the sigil fallback");
	}
}
"""


def compile_and_run(fixture_source: str, fixture_name: str, sources: list[Path]) -> None:
    with tempfile.TemporaryDirectory(prefix="cleric-c08b-") as temporary:
        temp = Path(temporary)
        fixture = temp / "test" / f"{fixture_name}.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(fixture_source), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            ["javac", "-d", str(classes), *(str(path) for path in sources), str(fixture)],
            check=True,
        )
        subprocess.run(["java", "-cp", str(classes), f"test.{fixture_name}"], check=True)


def main() -> None:
    validate_sources()
    compile_and_run(
        SERVER_FIXTURE,
        "ActiveStatusInventoryFixture",
        [
            SERVER / "content/status/ActiveStatusEntry.java",
            SERVER / "content/status/ActiveStatusInventory.java",
        ],
    )
    compile_and_run(
        CLIENT_FIXTURE,
        "ActiveStatusHudFixture",
        [
            CLIENT / "com/openrsc/client/entityhandling/defs/ClericEffectRankDef.java",
            CLIENT / "com/openrsc/client/entityhandling/defs/ClericSpellDef.java",
            CLIENT / "orsc/ClericSpellbookCatalog.java",
            CLIENT / "orsc/ActiveStatusPacketDecoder.java",
            CLIENT / "orsc/ActiveStatusHudModel.java",
        ],
    )
    print("PASS: C08B mixed Cleric/potion status HUD contract validated")


if __name__ == "__main__":
    main()
