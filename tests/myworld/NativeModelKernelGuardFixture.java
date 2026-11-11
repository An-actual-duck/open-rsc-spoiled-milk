package orsc.graphics.three;

import java.util.Arrays;

/** Invalid storage never reaches JNI, preserving the literal Java exception path. */
public final class NativeModelKernelGuardFixture {
    private static int checks;
    private static void require(boolean condition) { checks++; if (!condition) throw new AssertionError("Guard " + checks); }
    public static void main(String[] args) throws Exception {
        int[][] a = new int[8][7];
        require(!BrowserNativeModelKernel.isBrowserNativeModelKernelEnabled());
        require(BrowserNativeModelKernel.supported(7,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],1));
        require(!BrowserNativeModelKernel.supported(0,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],1));
        require(!BrowserNativeModelKernel.supported(8,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],1));
        require(!BrowserNativeModelKernel.supported(7,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],0));
        require(!BrowserNativeModelKernel.supported(7,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],-1));
        for (int output = 3; output < 8; output++) for (int prior = 0; prior < output; prior++) {
            int[] old = a[output]; a[output] = a[prior];
            require(!BrowserNativeModelKernel.supported(7,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],1));
            a[output] = old;
        }
        for (int array = 0; array < 8; array++) {
            int[] old = a[array]; a[array] = null;
            require(!BrowserNativeModelKernel.supported(7,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],1));
            a[array] = new int[6];
            require(!BrowserNativeModelKernel.supported(7,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],1));
            a[array] = old;
        }
        require(BrowserNativeModelKernel.supported(7,a[0],a[0],a[0],a[3],a[4],a[5],a[6],a[7],1));
        System.setProperty("spoiledmilk.browserNativeModelKernel", "true");
        boolean rejected = false;
        try { BrowserNativeModelKernel.installForPrivateBrowser(); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected && !BrowserNativeModelKernel.isBrowserNativeModelKernelEnabled());
        System.setProperty("spoiledmilk.browserBenchmark", "true");
        BrowserNativeModelKernel.installForPrivateBrowser();
        require(BrowserNativeModelKernel.isBrowserNativeModelKernelEnabled());
        require(!BrowserNativeModelKernel.tryTransform(8,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],0,0,0,9,8,0,0,0,0,0,0,0,0,0));
        require(Arrays.equals(BrowserNativeModelKernel.getWork(),new long[]{0,0,1}));
        System.out.println("JAVA_GUARD_CHECKS=" + checks);
    }
}
