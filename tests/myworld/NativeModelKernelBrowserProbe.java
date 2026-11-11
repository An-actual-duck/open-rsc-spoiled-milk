package orsc.graphics.three;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Arrays;

/** Browser-only JNI writeback contract; never opens a game, account or GPU. */
public final class NativeModelKernelBrowserProbe {
    private static native void report(boolean passed, int cases, int values, String detail);
    public static void main(String[] args) throws Exception {
        try {
            System.setProperty("spoiledmilk.browserBenchmark", "true");
            System.setProperty("spoiledmilk.browserNativeModelKernel", "true");
            BrowserNativeModelKernel.installForPrivateBrowser();
            int values = 0, cases;
            try (InputStream raw = NativeModelKernelBrowserProbe.class.getResourceAsStream("/native-model-reference.bin");
                    DataInputStream in = new DataInputStream(raw)) {
                if (in.readInt() != 0x4e4b3031) throw new AssertionError("Reference magic");
                cases = in.readInt();
                for (int trial = 0; trial < cases; trial++) {
                    int count = in.readInt(); int[] camera = new int[8];
                    for (int n = 0; n < camera.length; n++) camera[n] = in.readInt();
                    for (int n = 0; n < 6; n++) in.readInt();
                    int[][] actual = NativeModelKernelReferenceFixture.readArrays(in);
                    int[][] expected = NativeModelKernelReferenceFixture.readArrays(in);
                    NativeModelKernelReferenceFixture.rotate(NativeModelKernelReferenceFixture.model(count, actual), camera);
                    for (int n = 0; n < actual.length; n++) {
                        if (!Arrays.equals(actual[n], expected[n])) throw new AssertionError("JNI writeback mismatch case " + trial + " array " + n);
                        values += actual[n].length;
                    }
                }
            }
            long[] work = BrowserNativeModelKernel.getWork();
            if (work[0] != cases || work[1] <= 0 || work[2] != 0) throw new AssertionError("JNI did not run: " + Arrays.toString(work));
            report(true, cases, values, "Actual RSModel -> native -> same Java int[] reads match frozen reference");
        } catch (Throwable error) {
            report(false, 0, 0, String.valueOf(error));
            throw error;
        }
    }
}
