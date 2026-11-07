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
    return build(definitions, resourceName, "all");
  }

  /** A guide-only god-line filter; spell metadata and spellbook ordering are unchanged. */
  public static List<Entry> build(
      List<ClericSpellDef> definitions, IntFunction<String> resourceName, String alignment) {
    if (definitions == null || definitions.isEmpty()) return Collections.emptyList();
    ArrayList<ClericSpellDef> ordered = new ArrayList<ClericSpellDef>(definitions);
    Collections.sort(
        ordered,
        (a, b) -> {
          int level = Integer.compare(a.getWorshipLevel(), b.getWorshipLevel());
          return level != 0 ? level : Integer.compare(a.getStableCode(), b.getStableCode());
        });
    ArrayList<Entry> entries = new ArrayList<Entry>();
    if ("all".equals(alignment)) addOverview(entries);
    for (ClericSpellDef d : ordered) {
      if ("all".equals(alignment) || d.getAlignment().equals(alignment))
        addPages(entries, format(d, resourceName));
    }
    return Collections.unmodifiableList(entries);
  }

  private static void addPages(List<Entry> entries, Entry entry) {
      List<List<String>> pages = new ArrayList<List<String>>();
      List<String> page = new ArrayList<String>();
      for (List<String> block : entry.blocks) {
        if (block.size() > 12) {
          if (!page.isEmpty()) { pages.add(page); page = new ArrayList<String>(); }
          for (int start = 0; start < block.size();) {
            List<String> continuation = new ArrayList<String>();
            if (start > 0) continuation.add(block.get(0));
            int end = Math.min(block.size(), start + 12 - continuation.size());
            continuation.addAll(block.subList(start, end));
            pages.add(continuation);
            start = end;
          }
          continue;
        }
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

  private static void addOverview(List<Entry> entries) {
    DocumentLines tiers = new DocumentLines();
    add(tiers, "Worship unlocks Cleric spells. Holy Power determines their power level.");
    add(tiers, "Hostile spells can affect enemy players in the Wilderness. Party members, clanmates and players on your friends list are excluded. Each target must be legally attackable.");
    add(tiers, "Adding a friend protects them from your area attacks and curses; it does not protect you from theirs. Deliberate direct attacks are separate.");
    add(tiers, "A spell's tier sets its maximum strength, not its minimum strength.");
    add(tiers, "Tier 1 allows power levels 1-2; Tier 2 allows 1-4; Tier 3 allows 1-6.");
    add(tiers, "Each new spell tier adds two possible power levels. You still need enough Holy Power to reach them.");
    addPages(entries, new Entry(-1, "Cleric spells: tiers and power", "", "", "", tiers));

    DocumentLines power = new DocumentLines();
    add(power, "Holy Power combines your current Worship level with your strongest equipped holy staff bonus.");
    add(power, "Reaching a threshold raises the spell's power level, up to its tier's cap.");
    for (int rank = 1; rank <= 6; rank++)
      add(power, "Power " + rank + ": " + ClericBlessingContract.powerThreshold(rank - 1) + " Holy Power");
    add(power, "Tables below list values in power-level order (P1/P2/etc.).");
    addPages(entries, new Entry(-2, "Holy Power thresholds", "", "", "", power));

    DocumentLines lines = new DocumentLines();
    add(lines, "Saradomin, Guthix and Zamorak have distinct god lines.");
    add(lines, "Within a god's blessing or curse line, each higher-tier spell includes the effects from the tiers below.");
    add(lines, "God sigils select the line. Grace fuels blessings; Blight fuels curses. They are consumed together when casting.");
    add(lines, "Unify and Greater Unify use Grace alone. Their teleport distance is fixed and does not scale with Holy Power.");
    add(lines, "Unify follows teleport restrictions: no use at Wilderness level 20 or higher, during a duel, or in other teleport-restricted places. Caster, recipients and landing places must all qualify.");
    add(lines, "Choose a god line above to filter its spells, or All for the full guide. Scroll for each spell's targets, sigil cost and effect values.");
    addPages(entries, new Entry(-3, "God lines and sigils", "", "", "", lines));

    DocumentLines stacking = new DocumentLines();
    add(stacking, "Only one blessing from each god can be active on you.");
    add(stacking, "The same rule applies to each god's curses on an enemy.");
    add(stacking, "Different Clerics share these limits; extra casts do not stack.");
    add(stacking, "Higher tiers replace lower tiers. A lower tier never replaces a higher tier.");
    add(stacking, "Within the same tier, stronger power replaces weaker power. Equal strength refreshes the duration.");
    add(stacking, "Saradomin, Guthix and Zamorak effects can coexist.");
    addPages(entries, new Entry(-4, "Which effects can be active?", "", "", "", stacking));
  }

  private static Entry format(ClericSpellDef d, IntFunction<String> names) {
    String header =
        "Lvl " + d.getWorshipLevel() + "  " + d.getName() + " (Tier " + d.getSpellTier() + ")";
    String area =
        (d.isHostile() ? "Area: enemies within " : "Area: party allies within ")
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
      add(lines, "Follows teleport restrictions, including no use at Wilderness level 20 or higher or during a duel. Caster, recipients and landing places must qualify.");
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
                      || c.getKind() == orsc.ClericBlessingContract.CRITICAL_EXECUTION
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
      for (ClericComponentDef component : d.getEffectRanks().get(0).getComponents()) {
        if (component.getKind() == ClericBlessingContract.POISON_WEAKNESS) {
          add(lines, "Poison weakness increases poison damage and slows cleansing. Adjustments round up; at least 1 cleansing remains per pulse.");
        } else if (component.getKind() == ClericBlessingContract.NON_CORPOREAL) {
          add(lines, "Non-corporeal enemies cannot deal or receive non-magical damage. Magical damage still works in both directions.");
          add(lines, "Poison keeps cleansing normally while non-corporeal. Blocked poison damage is not dealt later.");
        } else if (component.getKind() == ClericBlessingContract.CRITICAL_VULNERABILITY) {
          add(lines, "A critical forces an attack to its maximum damage. A normal maximum roll does not count as a critical. Any eligible player's main melee, bow, throwing or Magic attack can benefit.");
          add(lines, "Offhand, extra hits, summons, cannon, poison and recoil do not trigger this critical chain. Cosmic robes can improve these chances.");
        } else if (component.getKind() == ClericBlessingContract.CRITICAL_STAGGER) {
          add(lines, "Stagger negates the next attack. Its shared protection lasts for five performed, unstaggered attacks; recasting or another attacker cannot reset it.");
        } else if (component.getKind() == ClericBlessingContract.CRITICAL_EXECUTION) {
          add(lines, "Execution checks health before the triggering hit. Bosses are immune to execution, but can still be susceptible to critical hits and Stagger. A normal killing critical needs no execution roll.");
          add(lines, "Some quest enemies and special spell encounters are protected from execution too. Their normal combat rules still apply.");
        }
      }
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
