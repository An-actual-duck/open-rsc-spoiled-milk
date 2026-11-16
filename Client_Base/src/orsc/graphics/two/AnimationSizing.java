package orsc.graphics.two;

/** Release sizing: approved pixel sizes refer to an actor 128 pixels tall. */
public final class AnimationSizing {
    public static final int REFERENCE_ACTOR_HEIGHT = 128;
    private AnimationSizing() { }
    public static int onEntity(int referencePixels, int projectedActorHeight) {
        return scaled(referencePixels, projectedActorHeight, REFERENCE_ACTOR_HEIGHT);
    }
    public static int projectedEffect(int referencePixels, int projectedCanvas, int worldCanvas) {
        return scaled(referencePixels, (long)projectedCanvas * 220, (long)worldCanvas * REFERENCE_ACTOR_HEIGHT);
    }
    private static int scaled(int referencePixels,long numerator,long denominator) {
        if(referencePixels<=0||numerator<=0||denominator<=0)return 0;
        return (int)Math.min(Integer.MAX_VALUE, Math.max(1, (referencePixels*numerator+denominator/2)/denominator));
    }
}
