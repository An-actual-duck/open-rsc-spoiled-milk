#!/usr/bin/env python3
"""Regression coverage for metadata-only persistent bank item pins."""

from pathlib import Path
import subprocess
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[2]
LAYOUT = ROOT / "server/src/com/openrsc/server/model/container/BankPinLayout.java"
BANK = ROOT / "server/src/com/openrsc/server/model/container/Bank.java"
DATABASE = ROOT / "server/src/com/openrsc/server/database/GameDatabase.java"
PLAYER_SERVICE = ROOT / "server/src/com/openrsc/server/service/PlayerService.java"
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
CUSTOM_GENERATOR = ROOT / "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"
CUSTOM_PARSER = ROOT / "server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java"
INTERFACE_OPTIONS = ROOT / "server/src/com/openrsc/server/constants/custom/InterfaceOptions.java"
INTERFACE_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java"
CLIENT_BANK = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/BankInterface.java"
CUSTOM_BANK = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label} missing expected snippet: {snippet}")


def run_layout_harness() -> None:
    harness = textwrap.dedent(
        """
        import com.openrsc.server.model.container.BankPinLayout;
        import java.util.*;

        public final class BankPinLayoutHarness {
            private static void check(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }

            private static Map<Integer, Integer> pins(int... values) {
                Map<Integer, Integer> result = new TreeMap<>();
                for (int i = 0; i < values.length; i += 2) {
                    result.put(values[i], values[i + 1]);
                }
                return result;
            }

            public static void main(String[] args) {
                BankPinLayout.Layout occupied = BankPinLayout.build(
                    Arrays.asList(10, 20, 30), pins(1, 20), 10);
                check(occupied.getSlots().size() == 3, "occupied pin adds no slot");
                check(occupied.getSlots().get(1).isPinned(), "occupied pin flag");
                check(occupied.getSlots().get(1).getSourceIndex() == 1, "occupied source");

                BankPinLayout.Layout empty = BankPinLayout.build(
                    Arrays.asList(10, 30), pins(1, 20), 10);
                check(empty.getSlots().size() == 3, "empty pin reserves capacity");
                check(empty.getSlots().get(1).getCatalogId() == 20, "placeholder catalog");
                check(empty.getSlots().get(1).getSourceIndex() == -1, "placeholder owns no item");

                BankPinLayout.Layout refilled = BankPinLayout.build(
                    Arrays.asList(10, 30, 20), pins(1, 20), 10);
                check(refilled.getSlots().size() == 3, "refill consumes reserved slot");
                check(refilled.getSlots().get(1).getSourceIndex() == 2, "deposit refills pin");
                check(refilled.getSlots().get(2).getCatalogId() == 30, "other order preserved");

                BankPinLayout.Layout unpinned = BankPinLayout.build(
                    Arrays.asList(10, 30), Collections.emptyMap(), 10);
                check(unpinned.getSlots().size() == 2, "empty unpin compacts");

                Map<Integer, Integer> shifted = BankPinLayout.shiftAfterRemoval(
                    pins(1, 20, 3, 40), 0);
                check(shifted.equals(pins(0, 20, 2, 40)), "unrelated removal shifts pins");

                BankPinLayout.Rearrangement swapped = BankPinLayout.rearrange(
                    Arrays.asList(10, 30), pins(1, 20), 10, 1, 0, false);
                check(swapped != null && swapped.getPins().equals(pins(0, 20)),
                    "placeholder swap keeps pin");
                check(swapped.getSourceOrder().equals(Arrays.asList(0, 1)),
                    "placeholder swap keeps real ownership");

                BankPinLayout.Rearrangement inserted = BankPinLayout.rearrange(
                    Arrays.asList(10, 30), pins(1, 20), 10, 1, 2, true);
                check(inserted != null && inserted.getPins().equals(pins(2, 20)),
                    "placeholder insert keeps pin");

                String serialized = BankPinLayout.serialize(pins(0, 10, 2, 20));
                check(BankPinLayout.parse(serialized).equals(pins(0, 10, 2, 20)),
                    "persistent metadata round trip");
                check(BankPinLayout.parse("v1|bad,1:x,2:20").equals(pins(2, 20)),
                    "malformed metadata is bounded");

                BankPinLayout.Layout normalized = BankPinLayout.build(
                    Arrays.asList(10), pins(-1, 30, 0, 10, 4, 40, 1, 10), 3);
                check(normalized.getPins().equals(pins(0, 10)),
                    "invalid, sparse, and duplicate pins normalize");

                System.out.println("PASS: BankPinLayout deterministic lifecycle");
            }
        }
        """
    )
    with tempfile.TemporaryDirectory(prefix="bank-pin-test-") as tmp:
        tmp_path = Path(tmp)
        harness_path = tmp_path / "BankPinLayoutHarness.java"
        harness_path.write_text(harness, encoding="utf-8")
        subprocess.run(
            ["javac", "-d", str(tmp_path), str(LAYOUT), str(harness_path)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run(
            ["java", "-cp", str(tmp_path), "BankPinLayoutHarness"],
            check=True,
            cwd=ROOT,
        )


def main() -> None:
    run_layout_harness()

    bank = BANK.read_text(encoding="utf-8")
    database = DATABASE.read_text(encoding="utf-8")
    player_service = PLAYER_SERVICE.read_text(encoding="utf-8")
    action_sender = ACTION_SENDER.read_text(encoding="utf-8")
    custom_generator = CUSTOM_GENERATOR.read_text(encoding="utf-8")
    custom_parser = CUSTOM_PARSER.read_text(encoding="utf-8")
    options = INTERFACE_OPTIONS.read_text(encoding="utf-8")
    option_handler = INTERFACE_HANDLER.read_text(encoding="utf-8")
    client_bank = CLIENT_BANK.read_text(encoding="utf-8")
    custom_bank = CUSTOM_BANK.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")

    require(bank, 'ITEM_PINS_CACHE_KEY = "bank_item_pins"', "pin cache key")
    require(bank, "BankPinLayout.serialize(pinnedSlots)", "pin persistence")
    require(player_service, "loaded.getBank().loadPinnedSlotsFromCache();", "pin load order")
    require(database, "player.getBank().size()", "real-only bank persistence")
    require(bank, "return item == null ? 0 : item.getAmount();", "metadata placeholder amount")
    require(bank, "return list.size();", "real-only owned size")
    require(bank, "for (Item i : list)", "real-only owned counts")
    require(bank, "hasEmptyPin(item.getCatalogId())", "reserved-slot capacity")
    require(bank, "getUsedSlotCount()", "pin-aware capacity")
    require(bank, "getDisplayItem(int slot)", "pin-safe slot item access")

    require(options, "BANK_ITEM_PIN(23)", "dedicated item pin action")
    require(custom_parser, "case BANK_ITEM_PIN:", "pin packet parser")
    require(option_handler, "pinDisplaySlot(payload.slot, payload.id)", "stale-safe Pin")
    require(option_handler, "unpinDisplaySlot(payload.slot, payload.id)", "stale-safe Unpin")

    require(action_sender, "List<Bank.DisplaySlot> displaySlots", "virtual bank packet")
    require(custom_generator, "if (b.includesPinMetadata)", "versioned pin flag encoding")
    require(packet_handler, "packetsIncoming.getUnsignedByte() == 1", "pin flag decoding")
    require(client_bank, "public boolean isPlaceholder()", "client placeholder state")
    require(custom_bank, 'bankItem.isPinned() ? "Unpin" : "Pin"', "Pin and Unpin menu")
    require(custom_bank, "bankItems.get(selectedBankSlot).isPlaceholder() || i <= 0",
            "placeholder withdrawal guard")
    require(custom_bank, 'drawString("P"', "pin marker")
    require(custom_bank, 'bankItem.isPinned() ? "Pinned: " : ""', "pinned tooltip")
    require(custom_bank, "for (BankItem item : bankItems)", "filter/wealth placeholder safety")
    require(custom_bank, "if (filtersActive())", "filtered rearrangement guard")

    print("PASS: persistent bank item pinning ownership, protocol, and UI contracts")


if __name__ == "__main__":
    main()
