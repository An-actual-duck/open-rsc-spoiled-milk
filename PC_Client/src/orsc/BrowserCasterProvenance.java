package orsc;

import java.util.Arrays;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Exact full-source dependency comparison. No long values cross the JS seam. */
final class BrowserCasterProvenance {
    private final int[] fields = new int[256 * 6 + 2048 * 5];
    private final long[] signatures = new long[256 + 2048];
    private int[] committedFields = new int[0];
    private long[] committedSignatures = new long[0];
    private int revision;private boolean initialized;

    int update(Renderer3DFrame frame) {
        int f = 0, s = 0;
        for (Renderer3DWorldChunkFrame.ChunkMesh chunk : frame.getWorldChunkFrame().getChunks()) {
            if (chunk.getChunkRole() == Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS) continue;
            if (f + 6 > fields.length || s == signatures.length) throw new IllegalStateException("Browser caster source exceeds bound");
            signatures[s++] = chunk.getStorageSignature();
            fields[f++] = chunk.getOriginWorldX(); fields[f++] = chunk.getOriginWorldZ();
            fields[f++] = chunk.getPlane(); fields[f++] = chunk.getChunkRole();
            fields[f++] = chunk.getVertexCount(); fields[f++] = chunk.getIndexCount();
        }
        for (Renderer3DTextureData texture : frame.getTextures()) {
            if (texture == null) continue;
            if (f + 5 > fields.length || s == signatures.length) throw new IllegalStateException("Browser caster textures exceed bound");
            boolean far = RendererShadowCasterPolicy.isFarCaster(texture);
            fields[f++] = texture.getTextureId(); fields[f++] = texture.getWidth(); fields[f++] = texture.getHeight();
            fields[f++] = texture.hasTransparency() ? 1 : 0; fields[f++] = far ? 1 : 0;
            // Opaque RGB changes cannot alter depth. Cutout bytes, shape, presence
            // and source material IDs can, and remain exact dependencies.
            signatures[s++] = texture.hasTransparency() && far ? texture.getSignature() : 0L;
        }
        boolean same = f == committedFields.length && s == committedSignatures.length;
        for (int n = 0; same && n < f; n++) same = fields[n] == committedFields[n];
        for (int n = 0; same && n < s; n++) same = signatures[n] == committedSignatures[n];
        if (!initialized || !same) {
            initialized=true;
            if (revision == Integer.MAX_VALUE) throw new IllegalStateException("Browser source provenance exhausted");
            revision++;
            committedFields = Arrays.copyOf(fields, f);
            committedSignatures = Arrays.copyOf(signatures, s);
        }
        return revision;
    }
}
