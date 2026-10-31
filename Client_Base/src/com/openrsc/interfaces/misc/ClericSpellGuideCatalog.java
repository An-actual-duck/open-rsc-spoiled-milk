package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.defs.*;
import java.util.*;
import java.util.function.IntFunction;
import orsc.ClericBlessingContract;
import orsc.ClericTextLayout;

/** Detailed numeric guide, derived from the same checked metadata as the HUD. */
public final class ClericSpellGuideCatalog {
  public static final int MAX_LINE_CHARACTERS = 58;

  private ClericSpellGuideCatalog() {}

  public static List<Entry> build(List<ClericSpellDef> definitions) {
    return build(definitions, id -> "item #" + id);
  }

  public static List<Entry> build(
      List<ClericSpellDef> definitions, IntFunction<String> resourceName) {
    if (definitions == null || definitions.isEmpty()) return Collections.emptyList();
    ArrayList<ClericSpellDef> ordered = new ArrayList<ClericSpellDef>(definitions);
    Collections.sort(
        ordered,
        (a, b) -> {
          int level = Integer.compare(a.getWorshipLevel(), b.getWorshipLevel());
          return level != 0 ? level : Integer.compare(a.getStableCode(), b.getStableCode());
        });
    ArrayList<Entry> entries = new ArrayList<Entry>();
    for (ClericSpellDef d : ordered) {
      Entry entry = format(d, resourceName);
      List<List<String>> pages = new ArrayList<List<String>>();
      List<String> page = new ArrayList<String>();
      for (List<String> block : entry.blocks) {
        if (!page.isEmpty() && page.size() + block.size() > 12) {
          pages.add(page);
          page = new ArrayList<String>();
        }
        page.addAll(block);
      }
      if (!page.isEmpty()) pages.add(page);
      for (int index = 0; index < pages.size(); index++)
        entries.add(
            new Entry(
                entry.code,
                entry.header
                    + (pages.size() > 1 ? " [" + (index + 1) + "/" + pages.size() + "]" : ""),
                entry.area,
                entry.mechanics,
                entry.power,
                pages.get(index)));
    }
    return Collections.unmodifiableList(entries);
  }

  private static Entry format(ClericSpellDef d, IntFunction<String> names) {
    String header =
        "Lvl " + d.getWorshipLevel() + "  " + d.getName() + " (Tier " + d.getSpellTier() + ")";
    String area =
        (d.isHostile() ? "Area: enemy NPCs within " : "Area: party allies within ")
            + d.getRadius()
            + " tiles; "
            + (d.affectsCaster() ? "includes caster" : "caster excluded");
    DocumentLines lines = new DocumentLines();
    StringBuilder cost = new StringBuilder("Per cast: ");
    for (ClericCostDef c : d.getCosts()) {
      if (cost.length() > 10) cost.append(" + ");
      cost.append(c.getCount()).append(" ").append(names.apply(c.getItemId()));
    }
    add(lines, cost.toString());
    add(lines, area);
    if (!d.getRecipientFlavor().isEmpty()) add(lines, "Recipient: " + d.getRecipientFlavor());
    if (d.getEffectRanks().isEmpty()) {
      add(lines, d.getDescription());
      add(lines, "Fixed effect: no Holy Power scaling. Stops within 2 tiles.");
    } else {
      int cap = d.getEffectRanks().size();
      add(lines, "Holy Power = current Worship + strongest applicable holy staff.");
      add(
          lines,
          "P1-P"
              + cap
              + " thresholds: "
              + orsc.ClericPowerPresentation.thresholds(cap)
              + " Holy Power; tier cap P"
              + cap
              + ".");
      add(
          lines,
          "Duration: "
              + (d.getEffectRanks().get(0).getDurationMilliseconds() / 1000)
              + " sec. Values below follow P1-P"
              + cap
              + ".");
      ArrayList<String> effectBlocks = new ArrayList<String>();
      for (int component = 0;
          component < d.getEffectRanks().get(0).getComponents().size();
          component++) {
        ClericComponentDef first = d.getEffectRanks().get(0).getComponents().get(component);
        StringBuilder values = new StringBuilder();
        StringBuilder intervals = new StringBuilder();
        for (ClericEffectRankDef rank : d.getEffectRanks()) {
          ClericComponentDef c = rank.getComponents().get(component);
          if (values.length() > 0) {
            values.append('/');
            intervals.append('/');
          }
          values.append(c.getMagnitude());
          intervals.append(
              c.getKind() == orsc.ClericBlessingContract.NON_CORPOREAL
                  ? c.getParameter1()
                  : c.getParameter1() / 1000);
        }
        String v = values.toString(), text;
        if (first.getKind() == ClericBlessingContract.STAT_RESTORATION) {
          boolean same = true;
          for (ClericEffectRankDef rank : d.getEffectRanks())
            same &= rank.getComponents().get(component).getMagnitude() == first.getMagnitude();
          if (same) v = Integer.toString(first.getMagnitude());
        }
        boolean continuation =
            component > 0
                && d.getEffectRanks()
                    .get(0)
                    .getComponents()
                    .get(component - 1)
                    .getName()
                    .equals(first.getName());
        text =
            continuation
                ? ClericBlessingContract.describeComponentTableDetail(
                    first.getKind(),
                    v,
                    intervals.toString(),
                    first.getParameter1(),
                    first.getParameter2())
                : ClericBlessingContract.describeComponentTable(
                    first.getKind(),
                    v,
                    intervals.toString(),
                    first.getParameter1(),
                    first.getParameter2());
        if (continuation) {
          int last = effectBlocks.size() - 1;
          effectBlocks.set(last, effectBlocks.get(last) + "; " + text);
        } else effectBlocks.add(text);
      }
      for (String block : effectBlocks) add(lines, block);
    }
    return new Entry(
        d.getStableCode(),
        header,
        area,
        d.getDescription(),
        d.getEffectRanks().isEmpty() ? "No Holy Power scaling" : "P1-P" + d.getEffectRanks().size(),
        lines);
  }

  private static void add(List<String> lines, String text) {
    List<String> wrapped = ClericTextLayout.wrap(text, MAX_LINE_CHARACTERS, String::length);
    lines.addAll(wrapped);
    if (lines instanceof DocumentLines) ((DocumentLines) lines).blocks.add(wrapped);
  }

  private static final class DocumentLines extends ArrayList<String> {
    final List<List<String>> blocks = new ArrayList<List<String>>();
  }

  public static final class Entry {
    private final int code;
    private final String header, area, mechanics, power;
    private final List<String> lines;
    private final List<List<String>> blocks;

    Entry(
        int code, String header, String area, String mechanics, String power, List<String> lines) {
      this.code = code;
      this.header = header;
      this.area = area;
      this.mechanics = mechanics;
      this.power = power;
      this.blocks =
          lines instanceof DocumentLines
              ? new ArrayList<List<String>>(((DocumentLines) lines).blocks)
              : Collections.singletonList(lines);
      this.lines = Collections.unmodifiableList(new ArrayList<String>(lines));
    }

    public int getStableCode() {
      return code;
    }

    public String getHeader() {
      return header;
    }

    public String getArea() {
      return area;
    }

    public String getMechanics() {
      return mechanics;
    }

    public String getHolyPower() {
      return power;
    }

    public List<String> getLines() {
      return lines;
    }
  }
}
