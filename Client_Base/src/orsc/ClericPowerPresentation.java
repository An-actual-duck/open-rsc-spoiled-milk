package orsc;

/** Authored Holy Power thresholds; effect strengths themselves always come from metadata. */
public final class ClericPowerPresentation {
  private static final int[] THRESHOLDS = ClericBlessingContract.powerThresholds(6);

  private ClericPowerPresentation() {}

  public static int rank(int combinedHolyPower, int cap) {
    if (combinedHolyPower < 0 || cap < 1 || cap > 6) return 0;
    int result = 1;
    for (int i = 1; i < cap; i++) if (combinedHolyPower >= THRESHOLDS[i]) result = i + 1;
    return result;
  }

  public static String thresholds(int cap) {
    if (cap < 1 || cap > 6) throw new IllegalArgumentException("Invalid power cap");
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < cap; i++) {
      if (i > 0) text.append('/');
      text.append(THRESHOLDS[i]);
    }
    return text.toString();
  }
}
