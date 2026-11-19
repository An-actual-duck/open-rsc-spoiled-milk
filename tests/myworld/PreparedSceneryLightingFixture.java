package orsc.graphics.three;

import java.lang.reflect.Field;
import java.util.Arrays;

public final class PreparedSceneryLightingFixture {
    private static final String[] MODEL_PLANES = {
        "faceNormX", "faceNormY", "faceNormZ", "scenePolyNormalShift",
        "faceDiffuseLight", "vertDiffuseLight", "vertXTransform", "vertYTransform", "vertZTransform"
    };

    private static void require(boolean match, String message) {
        if (!match) throw new AssertionError(message);
    }

    private static RSModel placed(int id, int direction, int location, int shape, int light, boolean cache) {
        RSModel model = new RSModel(5, 5);
        model.insertVertex(-52, 0, -24);
        model.insertVertex(48, -18, -24);
        model.insertVertex(70, 0, 52);
        model.insertVertex(-32, 0, 42);
        model.insertVertex(12 + shape, -93, 10);
        model.insertFace(4, new int[]{0, 1, 2, 3}, -900, Scene.TRANSPARENT, false);
        model.insertFace(3, new int[]{0, 1, 4}, 4, -1051, false);
        model.insertFace(3, new int[]{1, 2, 4}, -20, 2, false);
        model.insertFace(3, new int[]{2, 3, 4}, Scene.TRANSPARENT, -1051, false);
        model.insertFace(3, new int[]{3, 0, 4}, -32, -1051, false);
        model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
        model.setRenderer3DGlowEmitter(0xee9900, 200, 100);
        model.addRotation(0, direction * 32, 0);
        model.setDiffuseLightAndColor(-50 + light, -10, -50, 48, 48, true, 117);
        model.setTranslate(location * 101 - 6000, -location * 5, 2251 - location * 53);
        if (cache) model.markPreparedSceneryLighting(id, direction);
        return model;
    }

    private static Renderer3DWorldChunkFrame.ChunkMesh build(RSModel model) {
        return RSModel.buildRenderer3DObjectChunkMesh(0, 5, 6, 768, 1536, new RSModel[]{model}, 1);
    }

    private static void compare(int id, int direction, int location, int shape, int light) throws Exception {
        RSModel literal = placed(id, direction, location, shape, light, false);
        RSModel reuse = placed(id, direction, location, shape, light, true);
        Renderer3DWorldChunkFrame.ChunkMesh a = build(literal), b = build(reuse);
        require(a.getStorageSignature() == b.getStorageSignature(), "Static mesh attributes changed");
        require(a.getShadowCasterCount() == 1 && a.getGlowEmitterCount() == 1, "Missing shadow/glow fixture");
        require(a.getShadowCasterInventorySignature() == b.getShadowCasterInventorySignature(), "Shadow changed");
        require(a.getVertexCount() == b.getVertexCount() && a.getIndexCount() == b.getIndexCount(), "Count changed");
        for (int i = 0; i < a.getVertexCount(); i++) {
            for (int axis = 0; axis < 3; axis++)
                require(a.getVertexCoord(i * 3 + axis) == b.getVertexCoord(i * 3 + axis), "Coordinate changed");
            require(a.getVertexLight(i) == b.getVertexLight(i), "Vertex light changed");
            require(Float.floatToRawIntBits(a.getVertexTextureU(i)) == Float.floatToRawIntBits(b.getVertexTextureU(i)), "U changed");
            require(Float.floatToRawIntBits(a.getVertexTextureV(i)) == Float.floatToRawIntBits(b.getVertexTextureV(i)), "V changed");
        }
        for (int i = 0; i < a.getIndexCount(); i++) require(a.getIndex(i) == b.getIndex(i), "Index changed");
        for (String name : MODEL_PLANES) {
            Field field = RSModel.class.getDeclaredField(name);
            field.setAccessible(true);
            require(Arrays.equals((int[]) field.get(literal), (int[]) field.get(reuse)), name + " changed");
        }
    }

    public static void main(String[] args) throws Exception {
        for (int direction = 0; direction < 8; direction++) {
            for (int placement = 0; placement < 7; placement++)
                compare(35, direction, placement, 0, 0);
            compare(35, direction, 10, 2, 0);
            compare(35, direction, 11, 2, 1);
            compare(35, direction, 12, 2, 1);
        }
        int[] exact = RSModel.preparedSceneryLightingCounts();
        require(exact[0] == 56 && exact[1] == 24, "Unexpected hit and invalidation counts");
        for (int id = 100; id < 200; id++) compare(id, id % 8, id, id % 5, 0);
        int[] bounded = RSModel.preparedSceneryLightingCounts();
        require(bounded[3] == 64, "Cache exceeded its entry bound");
        System.out.println("PASS 180 literal/reused model, mesh, shadow and glow cases, "
            + exact[0] + " translated hits, " + exact[1] + " cold or changed inputs");
    }
}
