package orsc;

import orsc.graphics.three.Renderer3DFrame;

/**
 * Render-thread-only environment from the dequeued immutable frame. No server
 * clock/settings/location is changed. The presenter always restores its prior
 * binding in finally, including on context loss or draw failure; producer and
 * other threads cannot inherit a previous underground frame.
 */
final class RendererFrameLighting {
    private static final ThreadLocal<Renderer3DFrame> FRAME = new ThreadLocal<Renderer3DFrame>();

    private RendererFrameLighting() { }

    static Renderer3DFrame bind(Renderer3DFrame frame) {
        Renderer3DFrame previous = FRAME.get();
        if (frame == null) FRAME.remove(); else FRAME.set(frame);
        return previous;
    }

    static void restore(Renderer3DFrame previous) {
        if (previous == null) FRAME.remove(); else FRAME.set(previous);
    }

    static boolean isUnderground() {
        Renderer3DFrame frame = FRAME.get();
        return frame != null && frame.isUnderground();
    }
}
