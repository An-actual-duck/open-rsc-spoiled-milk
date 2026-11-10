package orsc.graphics.three;

/** Frame-local rejection only. These bounds never replace polygon picking. */
final class ProjectedModelPickBounds {
    private static final int EXPANDED_PADDING = 14;

    static boolean couldContainMouse(RSModel model, int viewportY, int mouseX, int mouseY) {
        if (model == null || model.vertHead <= 0 || model.vertexParam6 == null
                || model.vertexParam2 == null || model.vertHead > model.vertexParam6.length
                || model.vertHead > model.vertexParam2.length) return true;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int vertex = 0; vertex < model.vertHead; vertex++) {
            int x = model.vertexParam6[vertex];
            // Preserve the actual face helper's int addition, including wrap.
            int y = viewportY + model.vertexParam2[vertex];
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int scale = Math.max(1, model.getPickBoundsScale());
        double paddingX = padding(minX, maxX, scale);
        double paddingY = padding(minY, maxY, scale);
        return mouseX >= (double) minX - paddingX && mouseX <= (double) maxX + paddingX
            && mouseY >= (double) minY - paddingY && mouseY <= (double) maxY + paddingY;
    }

    private static double padding(int min, int max, int scale) {
        if (scale <= 1) return 2;
        // A face's mathematical span cannot exceed the model's. The old signed
        // int multiply may wrap, but its positive result cannot exceed this
        // nonnegative mathematical product. Outward rounding covers division.
        // Products exceeding exact double integer precision yield enormous
        // bounds covering every possible int mouse coordinate, never rejection.
        return Math.max(EXPANDED_PADDING, Math.ceil(((double) max - min) * (scale - 1) / 2.0));
    }

    static boolean hasOnlyRotatedVertexIndices(RSModel model, int[] indices, int count) {
        if (model == null || indices == null || count <= 0 || count > indices.length) return false;
        for (int vertex = 0; vertex < count; vertex++) {
            int index = indices[vertex];
            // Valid spare-capacity accesses and every malformed input retain
            // the original face helper rather than changing its semantics.
            if (index < 0 || index >= model.vertHead) return false;
        }
        return true;
    }
}
