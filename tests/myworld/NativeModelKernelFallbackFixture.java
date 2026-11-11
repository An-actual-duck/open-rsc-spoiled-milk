package orsc.graphics.three;

import java.security.MessageDigest;

/** Differential run against frozen RSModel and candidate; invalid calls retain
 * both the wrapped Java exception and all partially written array contents. */
public final class NativeModelKernelFallbackFixture {
    public static void main(String[] args) throws Exception {
        System.setProperty("spoiledmilk.browserBenchmark", "true");
        System.setProperty("spoiledmilk.browserNativeModelKernel", "true");
        BrowserNativeModelKernel.installForPrivateBrowser();
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (int scenario = 0; scenario < 40; scenario++) {
            int[][] arrays = new int[8][5];
            for (int n = 0; n < arrays.length; n++) for (int v = 0; v < arrays[n].length; v++) arrays[n][v] = 100 * n + v;
            int[] camera = new int[]{0, 0, 0, 9, 8, 0, 0, 0};
            RSModel model = NativeModelKernelReferenceFixture.model(5, arrays);
            switch (scenario % 10) {
                case 0: model.vertHead = 6; break;
                case 1: NativeModelKernelReferenceFixture.field(model,"vertXTransform",null); arrays[0] = null; break;
                case 2: model.vertexParam6 = arrays[3] = arrays[0]; break;
                case 3: model.vertexParam2 = arrays[4] = arrays[3]; break;
                case 4: camera[4] = 0; arrays[2][2] = 0; break;
                case 5: camera[4] = -7; arrays[2][2] = 0; break;
                case 6: model.vertHead = 0; break;
                case 7: model.vertHead = -1; break;
                case 8: model.vertXRot = arrays[5] = new int[2]; break;
                default: model.vertexParam2 = arrays[4] = null; break;
            }
            // Inputs that throw in the unchanged trig setup must also remain so.
            if (scenario >= 20) camera[5] = scenario % 2 == 0 ? -1 : 2048;
            String result;
            try { NativeModelKernelReferenceFixture.rotate(model, camera); result = "ok"; }
            catch (Throwable error) {
                if (error instanceof UnsatisfiedLinkError) throw new AssertionError("Unsupported input reached JNI", error);
                result = error.getClass().getName() + ":" + error.getMessage();
            }
            hash.update(result.getBytes("UTF-8"));
            for (int[] a : arrays) {
                if (a == null) { hash.update((byte)0); continue; }
                hash.update((byte)1);
                for (int v : a) { hash.update((byte)(v>>>24));hash.update((byte)(v>>>16));hash.update((byte)(v>>>8));hash.update((byte)v); }
            }
        }
        StringBuilder result = new StringBuilder(); for (byte b : hash.digest()) result.append(String.format("%02x", b & 255));
        System.out.println("FALLBACK_DIGEST=" + result);
    }
}
