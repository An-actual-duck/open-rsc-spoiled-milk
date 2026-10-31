package orsc;

import com.openrsc.client.entityhandling.defs.ClericEffectRankDef;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ClericGenericStatusHudFixture {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static ClericEffectRankDef rank(int code, int rank) {
    int kind;
    int counter;
    int initial;
    int primary;
    int secondary = 0;
    switch (code) {
      case 0:
      case 6:
        kind = 1;
        counter = 2;
        initial = 3;
        primary = code == 0 ? new int[] {1, 2, 3, 3}[rank - 1] : rank + 1;
        break;
      case 2:
        kind = 2;
        counter = 0;
        initial = 0;
        primary = new int[] {5, 10, 15, 20}[rank - 1];
        secondary = 1;
        break;
      case 5:
      case 9:
        kind = 3;
        counter = 1;
        primary = code == 5 ? 25 : 50;
        initial = code == 5 ? rank * 2 : rank;
        break;
      case 7:
        kind = 4;
        counter = 0;
        initial = 0;
        primary = new int[] {5, 8, 11, 15}[rank - 1];
        break;
      case 8:
        kind = 5;
        counter = 0;
        initial = 0;
        primary = new int[] {5, 8, 11, 15}[rank - 1];
        break;
      case 10:
        kind = 6;
        counter = 0;
        initial = 0;
        primary = 20;
        secondary = new int[] {55, 60, 65, 70}[rank - 1];
        break;
      case 11:
      default:
        kind = 7;
        counter = 0;
        initial = 0;
        primary = new int[] {10, 15, 20, 25}[rank - 1];
        break;
    }
    return new ClericEffectRankDef(rank, 30_000, kind, counter, initial, primary, secondary);
  }

  private static ClericSpellDef spell(int code) {
    String[] names = {
      "Mend",
      "Unify",
      "Fervor",
      "Purify",
      "Restore",
      "Ward",
      "Greater Mend",
      "Zeal",
      "Thorns",
      "Aegis",
      "Rally",
      "Respite"
    };
    String[] keys = {
      "mend",
      "unify",
      "fervor",
      "purify",
      "restore",
      "ward",
      "greater-mend",
      "zeal",
      "thorns",
      "aegis",
      "rally",
      "respite"
    };
    List<ClericEffectRankDef> ranks = new ArrayList<ClericEffectRankDef>();
    if (code != 1 && code != 3 && code != 4) {
      for (int value = 1; value <= (code == 0 ? 3 : 4); value++) {
        ranks.add(rank(code, value));
      }
    }
    return new ClericSpellDef(
        code + 32,
        "cleric." + code,
        names[code],
        "Description",
        "neutral",
        1,
        1,
        2,
        false,
        3300 + code,
        3300,
        1,
        3308,
        0,
        -1,
        -1,
        keys[code],
        ranks);
  }

  private static ClericSpellbookCatalog catalog() {
    ArrayList<ClericSpellDef> definitions = new ArrayList<ClericSpellDef>();
    for (int code = 0; code < 12; code++) definitions.add(spell(code));
    ClericSpellbookCatalog catalog = new ClericSpellbookCatalog();
    catalog.replace(3, definitions);
    return catalog;
  }

  private static byte[] packet(int[][] prefix, int overflow, int[][] trailer) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeByte(prefix.length);
    for (int[] row : prefix) {
      out.writeShort(row[0]);
      out.writeInt(row[1]);
    }
    if (overflow >= 0) out.writeShort(overflow);
    if (trailer != null) {
      out.writeByte(1);
      out.writeByte(trailer.length);
      for (int[] row : trailer) {
        out.writeByte(row[0]);
        out.writeShort(row[1]);
        out.writeByte(row[2]);
        out.writeByte(row[3]);
        out.writeShort(row[4]);
      }
    }
    return bytes.toByteArray();
  }

  private static void fallback(byte[] bytes, String label) {
    ActiveStatusPacketDecoder.DecodedSnapshot decoded = ActiveStatusPacketDecoder.decode(bytes);
    check(decoded.getEntries().size() == 4, label + " (prefix lost)");
    ActiveStatusHudModel fallbackModel = new ActiveStatusHudModel();
    fallbackModel.replace(decoded, catalog(), 1_000L);
    for (ActiveStatusHudModel.Row row : fallbackModel.snapshot(1_000L).getRows()) {
      check(!row.isCleric(), label);
    }
  }

  public static void main(String[] args) throws Exception {
    ClericSpellbookCatalog catalog = catalog();
    ActiveStatusHudModel frogModel = new ActiveStatusHudModel();
    frogModel.replace(
        ActiveStatusPacketDecoder.decode(
            packet(new int[][] {{3318, 10}}, 0, new int[][] {{2, 1, 0, 0, 0}})),
        catalog,
        1_000L);
    check(
        "Slimy Spit".equals(frogModel.snapshot(1_000L).getRows().get(0).getSlayerHoverText()),
        "Slayer debuff mislabeled as potion");
    check(frogModel.snapshot(11_000L).getRows().isEmpty(), "frog status expiry");
    frogModel.replace(
        ActiveStatusPacketDecoder.decode(
            packet(new int[][] {{3318, 600}}, 0, new int[][] {{0, 3318, 0, 0, 0}})),
        catalog,
        1_000L);
    check(
        "Slime Solvent".equals(frogModel.snapshot(1_000L).getRows().get(0).getSlayerHoverText()),
        "status name must not acquire bottle dose suffix");
    int[][] prefix = {{50, 10}, {3302, 30}, {3305, 30}, {3300, 30}};
    frogModel.replace(
        ActiveStatusPacketDecoder.decode(
            packet(new int[][] {{3324, 600}}, 0, new int[][] {{0, 3324, 0, 0, 0}})),
        catalog,
        1_000L);
    check(
        "Wax earplugs".equals(frogModel.snapshot(1_000L).getRows().get(0).getSlayerHoverText()),
        "earplugs dose-free name");
    frogModel.replace(
        ActiveStatusPacketDecoder.decode(
            packet(new int[][] {{3327, 600}}, 0, new int[][] {{0, 3327, 0, 0, 0}})),
        catalog,
        1_000L);
    check(
        "Dog Treats".equals(frogModel.snapshot(1_000L).getRows().get(0).getSlayerHoverText()),
        "treats dose-free name");
    frogModel.replace(
        ActiveStatusPacketDecoder.decode(
            packet(
                new int[][] {{3321, 10}, {3321, 600}},
                0,
                new int[][] {{2, 2, 0, 0, 0}, {0, 3321, 0, 0, 0}})),
        catalog,
        1_000L);
    check(
        "Stony Glare".equals(frogModel.snapshot(1_000L).getRows().get(0).getSlayerHoverText()),
        "glare label");
    check(
        "Eye Drops".equals(frogModel.snapshot(1_000L).getRows().get(1).getSlayerHoverText()),
        "eye drops dose-free label");
    frogModel.replace(
        ActiveStatusPacketDecoder.decode(
            packet(new int[][] {{3318, 3}}, 0, new int[][] {{2, 3, 0, 0, 0}})),
        catalog,
        1_000L);
    check(
        "Sticky Flesh".equals(frogModel.snapshot(1_000L).getRows().get(0).getSlayerHoverText()),
        "Abyssal full-action trap label");
    check(frogModel.snapshot(4_000L).getRows().isEmpty(), "Abyssal trap HUD expiry");
    int[][] trailer = {{0, 50, 0, 0, 0}, {1, 34, 3, 0, 0}, {1, 37, 3, 1, 6}, {1, 32, 2, 2, 2}};
    byte[] valid = packet(prefix, 7, trailer);
    ActiveStatusPacketDecoder.DecodedSnapshot decoded = ActiveStatusPacketDecoder.decode(valid);
    check(decoded.isEnriched() && decoded.getOverflowCount() == 7, "valid mixed trailer rejected");
    ActiveStatusHudModel model = new ActiveStatusHudModel();
    model.replace(decoded, catalog, 1_000L);
    ActiveStatusHudModel.Snapshot snapshot = model.snapshot(2_500L);
    check(
        snapshot.getRows().size() == 4 && snapshot.getOverflowCount() == 7,
        "mixed snapshot size/overflow drift");
    check(snapshot.getRows().get(0).getRemainingSeconds() == 9, "client countdown rounding drift");
    check(!snapshot.getRows().get(0).isCleric(), "item row became Cleric");
    check(
        snapshot
            .getRows()
            .get(1)
            .getClericHoverText()
            .equals("Fervor P3 — 15% chance to raise offense roll by 1"),
        "Fervor hover drift");
    check(snapshot.getRows().get(2).getCounterBadge().equals("6H"), "charge badge drift");
    check(
        snapshot
            .getRows()
            .get(2)
            .getClericHoverText()
            .equals("Ward P3 — 25% reduction — 6 protected hits remaining"),
        "Ward hover drift");
    check(snapshot.getRows().get(3).getCounterBadge().equals("2P"), "pulse badge drift");
    check(
        snapshot
            .getRows()
            .get(3)
            .getClericHoverText()
            .equals("Mend P2 — 2 Hits per pulse — 2 healing pulses remaining"),
        "Mend hover drift");
    check(
        snapshot.getRows().get(2).getRemainingCounter() == 6
            && model.snapshot(20_000L).getRows().get(1).getRemainingCounter() == 6,
        "client decremented authoritative counter");

    byte[] legacyNoOverflow = packet(prefix, -1, null);
    ActiveStatusHudModel slowModel = new ActiveStatusHudModel();
    byte[] slowPacket =
        packet(new int[][] {{33, 5}, {33, 4}}, 0, new int[][] {{2, 4, 0, 0, 0}, {2, 5, 0, 0, 0}});
    slowModel.replace(ActiveStatusPacketDecoder.decode(slowPacket), catalog(), 1_000L);
    check(
        "Slow 1".equals(slowModel.snapshot(1_000L).getRows().get(0).getSlayerHoverText())
            && "Slow 2".equals(slowModel.snapshot(1_000L).getRows().get(1).getSlayerHoverText()),
        "appended Slow identities must decode and display both tiers");
    check(
        !ActiveStatusPacketDecoder.decode(legacyNoOverflow).isEnriched(),
        "legacy no-suffix prefix rejected");
    byte[] legacyOverflow = packet(prefix, 5, null);
    check(
        ActiveStatusPacketDecoder.decode(legacyOverflow).getOverflowCount() == 5,
        "legacy overflow suffix rejected");

    int extension = 3 + prefix.length * 6;
    byte[] unsupported = valid.clone();
    unsupported[extension] = 9;
    fallback(unsupported, "unsupported version did not fall back");
    byte[] mismatch = valid.clone();
    mismatch[extension + 1] = 3;
    fallback(mismatch, "count mismatch did not fall back");
    fallback(Arrays.copyOf(valid, valid.length - 1), "truncation did not fall back");
    fallback(Arrays.copyOf(valid, valid.length + 1), "trailing garbage did not fall back");
    int firstClericRecord = extension + 2 + 7;
    byte[] unknown = valid.clone();
    unknown[firstClericRecord + 1] = 0;
    unknown[firstClericRecord + 2] = 31;
    fallback(unknown, "unknown identity was partially enriched");
    byte[] invalidRank = valid.clone();
    invalidRank[firstClericRecord + 3] = 0;
    fallback(invalidRank, "invalid rank was partially enriched");
    byte[] badCounter = valid.clone();
    int wardRecord = firstClericRecord + 7;
    badCounter[wardRecord + 4] = 0;
    fallback(badCounter, "counter kind/count mismatch was partially enriched");

    model.replace(decoded, new ClericSpellbookCatalog(), 1_000L);
    check(
        !model.snapshot(1_000L).getRows().get(1).isCleric(),
        "missing catalog did not retain safe prefix fallback");
    model.clear();
    check(
        model.snapshot(1_000L).getRows().isEmpty()
            && model.snapshot(1_000L).getOverflowCount() == 0,
        "logout/reconnect clear retained state");
    model.replace(
        ActiveStatusPacketDecoder.decode(
            packet(new int[][] {{50, 1}}, 0, new int[][] {{0, 50, 0, 0, 0}})),
        catalog,
        1_000L);
    check(model.snapshot(2_000L).getRows().isEmpty(), "local timer expiry did not compact the HUD");

    model.replace(
        ActiveStatusPacketDecoder.legacySnapshot(new int[] {50}, new int[] {Integer.MAX_VALUE}, 0),
        catalog,
        Long.MAX_VALUE - 5000);
    check(
        model.snapshot(Long.MAX_VALUE - 4999).getRows().get(0).getRemainingSeconds() == 5,
        "timer addition saturates without overflow");
    check(model.snapshot(Long.MAX_VALUE).getRows().isEmpty(), "saturated expiry compacts");
    check(ActiveStatusPacketDecoder.decode(null).getEntries().isEmpty(), "null packet");
    check(
        ActiveStatusPacketDecoder.decode(new byte[] {33}).getEntries().isEmpty(),
        "visible count bounded");
    check(
        ActiveStatusPacketDecoder.decode(new byte[] {1, 0}).getEntries().isEmpty(),
        "truncated prefix rejected");
    check(
        ActiveStatusPacketDecoder.legacySnapshot(null, new int[] {1}, 0).getEntries().isEmpty(),
        "null legacy arrays");
    int[] many = new int[64], times = new int[64];
    Arrays.fill(times, 1);
    check(
        ActiveStatusPacketDecoder.legacySnapshot(many, times, 2).getEntries().size() == 32,
        "legacy snapshot bound");
    check(
        ActiveStatusPacketDecoder.legacySnapshot(new int[] {-1, 65536, 50}, new int[] {1, 1, 0}, 0)
            .getEntries()
            .isEmpty(),
        "invalid legacy item IDs/timers");
    System.out.println(
        "PASS generic mixed-status HUD compatibility: "
            + checks
            + " checks (legacy prefix/corruption/counter/overflow/lifecycle, independent of retired"
            + " spell balance)");
  }
}
