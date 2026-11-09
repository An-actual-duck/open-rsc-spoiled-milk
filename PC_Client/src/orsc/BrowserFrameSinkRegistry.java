package orsc;

import java.awt.image.BufferedImage;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DSettings;

/** Optional synchronous backend seam. A sink borrows its frame only for present(). */
public final class BrowserFrameSinkRegistry {
    public interface Sink {
        void present(BufferedImage image, Renderer2DFrame commands, Renderer3DFrame frame);
    }
    private static volatile Sink sink;
    private BrowserFrameSinkRegistry() { }
    public static synchronized void install(Sink next) {
        if (next != null && sink != null) throw new IllegalStateException("A browser frame sink is already installed");
        sink = next;
        Renderer3DSettings.setBrowserPresentationAvailable(next != null);
    }
    /** The registry always releases its borrowed pooled frame, including failure/null-image paths. */
    static boolean present(BufferedImage image, Renderer2DFrame commands, Renderer3DFrame frame) {
        Sink target = sink;
        if (target == null) return false;
        try {
            if (image != null) target.present(image, commands == null ? Renderer2DFrame.EMPTY : commands, frame);
        } finally {
            if (frame != null) frame.release();
        }
        return true;
    }
}
