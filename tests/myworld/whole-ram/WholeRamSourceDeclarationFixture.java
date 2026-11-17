package orsc;

import org.json.JSONObject;

/** Size admission accepts a new authenticated source while retaining strict bounds. */
public final class WholeRamSourceDeclarationFixture {
    private static JSONObject bundle(long raw) {
        return new JSONObject().put("path", "source.bin").put("headerBytes", 16)
            .put("fileCount", 1797).put("rawBytes", raw).put("bytes", raw + 16);
    }
    private static void reject(JSONObject value) {
        try {
            BrowserWholeOverworldRam.sourceRawBytes(value);
            throw new AssertionError("Admitted invalid source declaration: " + value);
        } catch (IllegalStateException expected) { }
    }
    public static void main(String[] args) {
        for (int raw : new int[] {46101718, 46111318}) {
            if (BrowserWholeOverworldRam.sourceRawBytes(bundle(raw)) != raw)
                throw new AssertionError("Source size must follow the admitted manifest");
        }
        reject(bundle(0)); reject(bundle(-1)); reject(bundle(64L * 1024 * 1024));
        reject(bundle(Long.MAX_VALUE)); reject(bundle(46111318).put("bytes", 46111318));
        reject(bundle(46111318).put("path", "../source.bin"));
        reject(bundle(46111318).put("headerBytes", 12));
        reject(bundle(46111318).put("fileCount", 1796));
        System.out.println("PASS 10 source-size admission cases: current/previous sizes and malformed bounds/roster");
    }
}
