package orsc.browser.probe;

/** Browser-only primitive-row borrowing gate. No game, window, server or GPU. */
public final class BrowserNestedArrayProbe {
    private static native boolean inspect(int[][] rows, int stage);
    private static native void report(boolean passed, int checks, String detail);
    private static int checks;
    private static void require(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        try {
            int[] shared = {-1, Integer.MAX_VALUE, Integer.MIN_VALUE};
            int[] first = {1, 2, 3};
            int[][] rows = {first, shared, null, shared, new int[0], new int[]{42}, new int[64]};
            require(inspect(rows, 0), "Nested array representation not supported");
            require(first[1] == 0x12345678 && first[0] == 1 && first[2] == 3, "First primitive row writeback");
            require(shared[0] == -123456789 && rows[3][0] == -123456789, "Shared primitive identity/writeback");
            require(shared[1] == Integer.MAX_VALUE && shared[2] == Integer.MIN_VALUE, "Shared row sentinels changed");
            require(rows[2] == null && rows[4].length == 0 && rows[5][0] == 42, "Null/empty/variable rows changed");
            require(rows[6][63] == -7654321 && rows[6][0] == 0, "Long row tail writeback");
            rows[0] = new int[]{9, 8};
            require(inspect(rows, 1), "Replacement row not visible through same outer array");
            require(rows[0][0] == 777 && rows[0][1] == 8, "Replacement row writeback");
            require(first[0] == 1 && first[1] == 0x12345678, "Old row retained/reused instead of replacement");
            rows[2] = new int[]{7}; rows[1] = new int[]{6, 5, 4};
            require(inspect(rows, 2), "Null replacement/shared-identity break not visible");
            require(rows[2][0] == 888 && rows[1][2] == 999, "Replaced primitive rows writeback");
            require(shared[0] == -123456789 && rows[3] == shared, "Old shared row changed after replacement");
            int[][] unsupported = {new int[]{3, 4}, null};
            require(!inspect(unsupported, 3), "Unsupported shape accepted");
            require(unsupported[0][0] == 3 && unsupported[0][1] == 4, "Unsupported shape partially wrote");
            require(!inspect(null, 3), "Null outer array accepted");
            for (int n = 0; n < rows.length; n++) rows[n] = null;
            require(inspect(rows, 4), "Cleared outer rows remained cached");
            report(true, checks, "Actual int[][] indexing, repeated int[] identity, shared writeback, row replacement, no-write rejection and clearing verified");
        } catch (Throwable error) {
            report(false, checks, String.valueOf(error));
            throw error;
        }
    }
}
