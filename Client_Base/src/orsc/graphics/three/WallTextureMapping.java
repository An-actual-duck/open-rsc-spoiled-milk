package orsc.graphics.three;

/** Existing wall-image U runs upward. Partial caps sample the complete wall's range. */
public final class WallTextureMapping {
    private WallTextureMapping() {}

    public static float start(int bottom, int top, int height) {
        validate(bottom, top, height);
        return bottom == 0 ? 0.0f : (float) bottom / height;
    }

    public static float scale(int bottom, int top, int height) {
        validate(bottom, top, height);
        // The original lower window remains an independent, complete texture.
        return bottom == 0 ? 1.0f : (float) (top - bottom) / height;
    }

    private static void validate(int bottom, int top, int height) {
        if (bottom < 0 || top <= bottom || top > height) {
            throw new IllegalArgumentException("Invalid partial wall texture height");
        }
    }

    public static void apply(float[] coordinates, float start, float scale) {
        if (start == 0.0f && scale == 1.0f) return;
        for (int i = 0; i < coordinates.length; i++) {
            coordinates[i] = start + coordinates[i] * scale;
        }
    }

    /** Virtual full-wall texture basis only; polygon vertices/picking stay unchanged. */
    static int legacyBasisCoordinate(int origin, int up, int side, int point,
                                     float start, float scale) {
        double offset = ((point == 1 ? 1.0 : 0.0) - start) / scale;
        return Math.toIntExact(Math.round((point == 2 ? side : origin) + offset * ((double) up - origin)));
    }
}
