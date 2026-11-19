    // Private diagnostic overlay. Reuses only camera-independent canonical scenery lighting.
    private static final int PREPARED_LIGHTING_CAP = 64;
    private static final java.util.Map<Integer, PreparedSceneryLight> PREPARED_LIGHTING =
        new java.util.LinkedHashMap<Integer, PreparedSceneryLight>(64, 0.75f, true);
    private static int preparedLightingHits, preparedLightingMisses, preparedLightingRefusals;
    private int preparedSceneryKey = -1;

    public void markPreparedSceneryLighting(int objectId, int direction) {
        if (objectId >= 0 && objectId < (1 << 20) && direction >= 0 && direction < 8
            && !this.interactionOnly) {
            preparedSceneryKey = (objectId << 3) | direction;
        }
    }

    public static synchronized int[] preparedSceneryLightingCounts() {
        return new int[]{preparedLightingHits, preparedLightingMisses,
            preparedLightingRefusals, PREPARED_LIGHTING.size()};
    }

    private boolean hasPreparedSceneryInputs() {
        return !dontComputeDiffuse && vertHead > 0 && vertHead <= 512
            && faceHead > 0 && faceHead <= 512
            && vertXTransform != vertYTransform && vertYTransform != vertZTransform
            && vertZTransform != vertXTransform
            && vertDiffuseLight != vertXTransform && vertDiffuseLight != vertYTransform
            && vertDiffuseLight != vertZTransform
            && faceNormX != faceNormY && faceNormY != faceNormZ && faceNormX != faceNormZ;
    }

    private void computePreparedSceneryNormals() {
        if (preparedSceneryKey < 0) {
            computeNormals((byte) 14);
            return;
        }
        synchronized (RSModel.class) {
            if (!hasPreparedSceneryInputs()) {
                preparedLightingRefusals++;
                computeNormals((byte) 14);
                return;
            }
            PreparedSceneryLight entry = PREPARED_LIGHTING.get(preparedSceneryKey);
            if (entry != null && entry.matches(this)) {
                entry.apply(this);
                preparedLightingHits++;
                return;
            }
            preparedLightingMisses++;
            PreparedSceneryLight candidate = new PreparedSceneryLight(this);
            computeNormals((byte) 14);
            if (!candidate.bounded) return;
            candidate.captureOutput(this);
            if (entry == null && PREPARED_LIGHTING.size() == PREPARED_LIGHTING_CAP) {
                PREPARED_LIGHTING.remove(PREPARED_LIGHTING.keySet().iterator().next());
            }
            PREPARED_LIGHTING.put(preparedSceneryKey, candidate);
        }
    }

    private static final class PreparedSceneryLight {
        final int[] xyz, topology, inputFaces, inputVertices;
        final int xLight, yLight, zLight, lightMagnitude, lightScale;
        final boolean bounded;
        int[] normalX, normalY, normalZ, faceLight, vertexLight;

        PreparedSceneryLight(RSModel model) {
            xLight = model.diffuseDirX;
            yLight = model.diffuseDirY;
            zLight = model.diffuseDirZ;
            lightMagnitude = model.diffuseMag;
            lightScale = model.diffuseParam2;
            int indices = 0;
            for (int face = 0; face < model.faceHead; face++) {
                int count = model.faceIndexCount[face];
                if (count < 3 || count > 1024 - indices) {
                    indices = -1;
                    break;
                }
                indices += count;
            }
            bounded = indices >= 0;
            xyz = bounded ? new int[model.vertHead * 3] : null;
            topology = bounded ? new int[model.faceHead + indices] : null;
            inputFaces = bounded ? Arrays.copyOf(model.faceDiffuseLight, model.faceHead) : null;
            inputVertices = bounded ? Arrays.copyOf(model.vertDiffuseLight, model.vertHead) : null;
            if (!bounded) return;
            for (int i = 0; i < model.vertHead; i++) {
                xyz[i * 3] = model.vertXTransform[i] - model.vertXTransform[0];
                xyz[i * 3 + 1] = model.vertYTransform[i] - model.vertYTransform[0];
                xyz[i * 3 + 2] = model.vertZTransform[i] - model.vertZTransform[0];
            }
            int cursor = 0;
            for (int face = 0; face < model.faceHead; face++) {
                int count = model.faceIndexCount[face];
                topology[cursor++] = count;
                System.arraycopy(model.faceIndices[face], 0, topology, cursor, count);
                cursor += count;
            }
        }

        boolean matches(RSModel model) {
            if (inputFaces.length != model.faceHead || inputVertices.length != model.vertHead
                || xLight != model.diffuseDirX || yLight != model.diffuseDirY || zLight != model.diffuseDirZ
                || lightMagnitude != model.diffuseMag || lightScale != model.diffuseParam2) return false;
            int cursor = 0;
            for (int face = 0; face < model.faceHead; face++) {
                int count = model.faceIndexCount[face];
                if (inputFaces[face] != model.faceDiffuseLight[face] || topology[cursor++] != count) return false;
                for (int i = 0; i < count; i++) {
                    if (topology[cursor++] != model.faceIndices[face][i]) return false;
                }
            }
            for (int i = 0; i < model.vertHead; i++) {
                if (inputVertices[i] != model.vertDiffuseLight[i]
                    || xyz[i * 3] != model.vertXTransform[i] - model.vertXTransform[0]
                    || xyz[i * 3 + 1] != model.vertYTransform[i] - model.vertYTransform[0]
                    || xyz[i * 3 + 2] != model.vertZTransform[i] - model.vertZTransform[0]) return false;
            }
            return true;
        }

        void captureOutput(RSModel model) {
            normalX = Arrays.copyOf(model.faceNormX, model.faceHead);
            normalY = Arrays.copyOf(model.faceNormY, model.faceHead);
            normalZ = Arrays.copyOf(model.faceNormZ, model.faceHead);
            faceLight = Arrays.copyOf(model.faceDiffuseLight, model.faceHead);
            vertexLight = Arrays.copyOf(model.vertDiffuseLight, model.vertHead);
        }

        void apply(RSModel model) {
            System.arraycopy(normalX, 0, model.faceNormX, 0, model.faceHead);
            System.arraycopy(normalY, 0, model.faceNormY, 0, model.faceHead);
            System.arraycopy(normalZ, 0, model.faceNormZ, 0, model.faceHead);
            System.arraycopy(faceLight, 0, model.faceDiffuseLight, 0, model.faceHead);
            System.arraycopy(vertexLight, 0, model.vertDiffuseLight, 0, model.vertHead);
            Arrays.fill(model.scenePolyNormalShift, 0, model.faceHead, -1);
        }
    }
