/** Versioned numeric metadata only; no executable expressions cross the wire. */
public final class ClericBlessingContract {
  public static final int SCHEMA_VERSION = 5;
  public static final int CLIENT_VERSION = 10056;
  public static final int MAX_DEFINITIONS = 32;
  public static final int MAX_COSTS = 4;
  public static final int MAX_COMPONENTS = 9;
  public static final int BLESSING_PRESENTATION = 8;
  public static final int REGENERATION = 1;
  public static final int HEALING_PULSE = 2;
  public static final int POISON_CLEANSING = 3;
  public static final int STAT_RESTORATION = 4;
  public static final int DEFENSE = 5;
  public static final int MAX_DAMAGE = 6;
  public static final int DEALT_DAMAGE = 7;
  public static final int LIFESTEAL = 8;
  public static final int REFLECTION = 9;
  public static final int UNDEAD_DAMAGE = 10;
  public static final int POISON_WEAKNESS = 11;
  public static final int SUMMON_WEAKNESS = 12;
  public static final int RECOIL_WEAKNESS = 13;
  public static final int MAGIC_DEFENSE_REDUCTION = 14;
  public static final int MAX_MELEE_REDUCTION = 15;
  public static final int NON_CORPOREAL = 16;
  public static final int CRITICAL_VULNERABILITY = 17;
  public static final int CRITICAL_STAGGER = 18;
  public static final int CRITICAL_EXECUTION = 19;
  public static final int LAST_COMPONENT_KIND = CRITICAL_EXECUTION;
  public static final int HOSTILE_PRESENTATION = 9;

  public static String componentName(int kind) {
    switch (kind) {
      case REGENERATION:
        return "Quickened Pulse";
      case HEALING_PULSE:
        return "Mending Flesh";
      case POISON_CLEANSING:
      case STAT_RESTORATION:
        return "Sap Blood";
      case DEFENSE:
        return "Barkskin";
      case MAX_DAMAGE:
        return "Unnatural Might";
      case DEALT_DAMAGE:
        return "Bloodrage";
      case LIFESTEAL:
        return "Blood Hunger";
      case REFLECTION:
        return "Thorns";
      case UNDEAD_DAMAGE:
        return "Hallowed Strikes";
      case POISON_WEAKNESS:
        return "Toxiphobia";
      case SUMMON_WEAKNESS:
        return "Agrizoophobia";
      case RECOIL_WEAKNESS:
        return "Hematophobia";
      case MAGIC_DEFENSE_REDUCTION:
        return "Magical Allergy";
      case MAX_MELEE_REDUCTION:
        return "Muscular Atrophy";
      case NON_CORPOREAL:
        return "Molecular Antipode";
      case CRITICAL_VULNERABILITY:
        return "Expose Weakness";
      case CRITICAL_STAGGER:
        return "Weak Legs";
      case CRITICAL_EXECUTION:
        return "Weak Constitution";
      default:
        throw new IllegalArgumentException("Unknown Cleric component");
    }
  }

  private static String seconds(int millis) {
    return java.math.BigDecimal.valueOf(millis, 3).stripTrailingZeros().toPlainString();
  }

  public static String describeComponent(int kind, int amount, int first, int second) {
    return componentName(kind) + ": " + describeComponentDetail(kind, amount, first, second);
  }

  public static String describeComponentDetail(int kind, int amount, int first, int second) {
    String text;
    switch (kind) {
      case REGENERATION:
        text = "Natural Hits regeneration at " + amount + "% of normal";
        break;
      case HEALING_PULSE:
        text = amount + " Hits every " + seconds(first) + " sec, " + second + " delayed pulses";
        break;
      case POISON_CLEANSING:
        text = "Extra " + amount + " poison cleansing per existing poison pulse";
        break;
      case STAT_RESTORATION:
        text =
            "Restore "
                + amount
                + " point to each drained stat other than Hits every "
                + seconds(first)
                + " sec";
        break;
      case DEFENSE:
        text = "+" + amount + "% ranged defense";
        break;
      case MAX_DAMAGE:
        text = "+" + amount + "% maximum direct melee damage";
        break;
      case DEALT_DAMAGE:
        text = "+" + amount + "% direct melee damage dealt";
        break;
      case LIFESTEAL:
        text = amount + "% direct-hit lifesteal strictly below " + first + "% Hits";
        break;
      case REFLECTION:
        text = "Reflect " + amount + "% direct damage received";
        break;
      case UNDEAD_DAMAGE:
        text = "+" + amount + "% direct melee, ranged and Magic attack damage against undead";
        break;
      case POISON_WEAKNESS:
        text = "+" + amount + "% poison damage and reduced cleansing at the existing pulse";
        break;
      case SUMMON_WEAKNESS:
        text = "+" + amount + "% damage received from summons";
        break;
      case RECOIL_WEAKNESS:
        text = "+" + amount + "% reflected and recoil damage received";
        break;
      case MAGIC_DEFENSE_REDUCTION:
        text = "-" + amount + "% Magic defense";
        break;
      case MAX_MELEE_REDUCTION:
        text = "-" + amount + "% maximum melee damage";
        break;
      case NON_CORPOREAL:
        text =
            amount
                + "% entry chance per eligible tick; "
                + first
                + " game ticks non-corporeal, then "
                + second
                + " corporeal attack to rearm";
        break;
      case CRITICAL_VULNERABILITY:
        text = amount + "% chance for primary melee, ranged or Magic attacks to hit their maximum";
        break;
      case CRITICAL_STAGGER:
        text = amount + "% chance per critical to stagger for " + first + " attack(s); then immunity for " + second + " attacks";
        break;
      case CRITICAL_EXECUTION:
        text = amount + "% execution chance per critical at or below " + first + "% Hits; ordinary enemies only";
        break;
      default:
        throw new IllegalArgumentException("Unknown Cleric component");
    }
    return text;
  }

  public static String describeComponentTable(
      int kind, String values, String intervals, int first, int second) {
    return componentName(kind)
        + ": "
        + describeComponentTableDetail(kind, values, intervals, first, second);
  }

  public static String describeComponentTableDetail(
      int kind, String values, String intervals, int first, int second) {
    String text;
    switch (kind) {
      case REGENERATION:
        text = "natural Hits regeneration (% of normal): " + values;
        break;
      case HEALING_PULSE:
        text =
            "Hits per pulse: "
                + values
                + "; "
                + second
                + " delayed pulses every "
                + seconds(first)
                + " sec";
        break;
      case POISON_CLEANSING:
        text = "extra poison cleansing per pulse: " + values;
        break;
      case STAT_RESTORATION:
        text =
            "restore "
                + values
                + " point to each drained stat other than Hits every "
                + intervals
                + " sec";
        break;
      case DEFENSE:
        text = "ranged defense (%): +" + values;
        break;
      case MAX_DAMAGE:
        text = "maximum direct melee damage (%): +" + values;
        break;
      case DEALT_DAMAGE:
        text = "direct melee damage dealt (%): +" + values;
        break;
      case LIFESTEAL:
        text = "direct-hit lifesteal (%): " + values + "; strictly below " + first + "% Hits";
        break;
      case REFLECTION:
        text = "direct damage received reflected (%): " + values;
        break;
      case UNDEAD_DAMAGE:
        text = "direct melee/ranged/Magic attack damage against undead (%): +" + values;
        break;
      case POISON_WEAKNESS:
        text = "poison weakness (%): " + values;
        break;
      case SUMMON_WEAKNESS:
        text = "summon damage weakness (%): " + values;
        break;
      case RECOIL_WEAKNESS:
        text = "recoil/reflection weakness (%): " + values;
        break;
      case MAGIC_DEFENSE_REDUCTION:
        text = "Magic defense reduction (%): " + values;
        break;
      case MAX_MELEE_REDUCTION:
        text = "maximum melee damage reduction (%): " + values;
        break;
      case NON_CORPOREAL:
        text =
            "entry chance per eligible tick (%): "
                + values
                + "; non-corporeal game ticks: "
                + intervals
                + "; then one corporeal attack attempt to rearm";
        break;
      case CRITICAL_VULNERABILITY:
        text = "primary melee/ranged/Magic critical chance (%): " + values;
        break;
      case CRITICAL_STAGGER:
        text = "stagger chance per critical (%): " + values + "; " + first + " attack(s), then " + second + " unstaggered attacks of immunity";
        break;
      case CRITICAL_EXECUTION:
        text = "execution chance per eligible critical (%): " + values + "; pre-hit Hits threshold (%): " + intervals + "; ordinary enemies only";
        break;
      default:
        throw new IllegalArgumentException("Unknown Cleric component");
    }
    return text;
  }

  private static final int[] POWER_THRESHOLDS = {0, 20, 45, 75, 115, 160};

  public static int powerThreshold(int index) {
    if (index < 0 || index >= POWER_THRESHOLDS.length)
      throw new IllegalArgumentException("Invalid power rank index");
    return POWER_THRESHOLDS[index];
  }

  public static int[] powerThresholds(int count) {
    if (count < 1 || count > POWER_THRESHOLDS.length)
      throw new IllegalArgumentException("Invalid power cap");
    return java.util.Arrays.copyOf(POWER_THRESHOLDS, count);
  }

  private ClericBlessingContract() {}

  public static boolean supports(int clientVersion) {
    return clientVersion >= CLIENT_VERSION && clientVersion < 20000;
  }
}
