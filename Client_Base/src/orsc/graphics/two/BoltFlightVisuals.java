package orsc.graphics.two;

/** Angle convention and perspective scale used by the existing rotated-sprite renderer. */
public final class BoltFlightVisuals {
    private BoltFlightVisuals() { }
    public static int angle(int dx, int dy) {
        return (int)Math.round(Math.atan2(-(double)dy, (double)dx) * 128.0 / Math.PI) & 255;
    }
    /** Owner-selected orientation of the bolt/trail artwork relative to flight. */
    public static int artAngle(int dx, int dy) {
        return (angle(dx,dy) + 128) & 255;
    }
    public static int scale(int perspectiveShift, int depth) {
        return (int)Math.max(1, Math.min(1024, (128L << perspectiveShift) / Math.max(1, depth)));
    }
}
