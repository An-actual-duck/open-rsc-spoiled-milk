package orsc;

import orsc.graphics.three.Renderer3DTextureData;

/** Cached far depth must not depend on a material's moving alpha footprint. */
final class RendererShadowCasterPolicy {
    private RendererShadowCasterPolicy() { }
    static boolean isFarCaster(Renderer3DTextureData texture) {
        return texture == null || !texture.hasAnimatedAlpha();
    }
}
