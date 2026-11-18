package orsc.graphics.three;

/** Roof receiver coverage within a 128-unit world tile. */
public final class RoofTileCoverage {
    public static final int OUTDOOR = 0;
    public static final int BELOW_ANTIDIAGONAL = 1;
    public static final int ABOVE_ANTIDIAGONAL = 2;
    public static final int BELOW_DIAGONAL = 3;
    public static final int ABOVE_DIAGONAL = 4;
    public static final int FULL = 255;

    private RoofTileCoverage() {}

    static int presenceWords(int tiles) {
        return (tiles + 63) / 64;
    }

    static int shapedWords(int tiles) {
        return presenceWords(tiles) + (tiles + 15) / 16;
    }

    static boolean validStorage(int words, int tiles) {
        return words == presenceWords(tiles) || words == shapedWords(tiles);
    }

    static void setHalf(long[] words, int tiles, int index, int shape) {
        int word = presenceWords(tiles) + (index >>> 4);
        words[word] |= (long) shape << ((index & 15) * 4);
    }

    static int shape(long[] words, int tiles, int index) {
        int word = presenceWords(tiles) + (index >>> 4);
        if (word < words.length) {
            int shape = (int) (words[word] >>> ((index & 15) * 4)) & 15;
            if (shape != 0) {
                return shape;
            }
        }
        return FULL;
    }

    public static boolean contains(int shape, int x, int z) {
        switch (shape) {
            case BELOW_ANTIDIAGONAL: return x + z <= 128;
            case ABOVE_ANTIDIAGONAL: return x + z >= 128;
            case BELOW_DIAGONAL: return x <= z;
            case ABOVE_DIAGONAL: return x >= z;
            default: return shape == FULL;
        }
    }
}
