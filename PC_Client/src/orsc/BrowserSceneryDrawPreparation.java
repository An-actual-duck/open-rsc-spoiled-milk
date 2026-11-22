package orsc;

import java.util.Arrays;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame.ChunkMesh;

/** CPU-only packing for one exact renderer configuration in the detached scenery VM. */
public final class BrowserSceneryDrawPreparation {
    static final int CONTEXT_HEADER = 5;
    private static final int PACKET_HEADER = 8;
    private static final int MAX_DRAW_WORDS = 256 * 1024;

    private final OpenGLWorldChunkRenderer packing =
        new OpenGLWorldChunkRenderer(null, null, null, true, true, false, false, true, true, false);
    private int[] context;
    private Renderer3DFrame frame;

    public int[] prepare(ChunkMesh mesh, int[] incoming) {
        if (incoming.length < CONTEXT_HEADER || incoming[0] <= 0 || incoming[1] != 0
            || incoming[3] != RendererLightingSettings.Mode.DIRECTIONAL.ordinal()
            || (incoming.length - CONTEXT_HEADER) % 4 != 0 || incoming.length > CONTEXT_HEADER + 2048 * 4) return null;
        if (!Arrays.equals(context, incoming)) {
            RendererFogSettings.Mode[] fog = RendererFogSettings.Mode.values();
            RendererGeometrySettings.Mode[] geometry = RendererGeometrySettings.Mode.values();
            if (incoming[2] < 0 || incoming[2] >= fog.length || incoming[4] < 0 || incoming[4] >= geometry.length) return null;
            RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);
            RendererFogSettings.setMode(fog[incoming[2]]);
            RendererGeometrySettings.setMode(geometry[incoming[4]]);
            frame = Renderer3DFrame.forSceneryPacking(incoming, CONTEXT_HEADER);
            context = incoming;
        }
        return packet(packing, frame, mesh, incoming[0]);
    }

    static int[] packet(OpenGLWorldChunkRenderer packing, Renderer3DFrame frame, ChunkMesh mesh, int epoch) {
        if (mesh.getVertexCount() == 0 || PACKET_HEADER + mesh.getVertexCount() * 13L > MAX_DRAW_WORDS) return null;
        int[] vertices = packing.copyBackendCompactChunkVertices(mesh, frame);
        if (vertices == null) return null;
        BrowserChunkMaterials.Buffers materials = BrowserChunkMaterials.pack(packing, frame, mesh);
        int words = PACKET_HEADER + vertices.length + materials.indices.length + materials.batches.length;
        if (words > MAX_DRAW_WORDS) return null;
        long signature = mesh.getStorageSignature();
        int[] result = new int[words];
        int[] header = {epoch, mesh.getOriginWorldX(), mesh.getOriginWorldZ(), (int) signature,
            (int) (signature >>> 32), vertices.length, materials.indices.length, materials.batches.length};
        int offset = 0;
        for (int[] block : new int[][] {header, vertices, materials.indices, materials.batches}) {
            System.arraycopy(block, 0, result, offset, block.length);
            offset += block.length;
        }
        return result;
    }
}
