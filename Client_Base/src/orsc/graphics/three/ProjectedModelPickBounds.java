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

    /** Conservative fixed-point bounds for model faces before per-vertex projection. */
    static final class CameraBounds {
        private static final boolean INTEGER_ROUNDING = Boolean.getBoolean("spoiledmilk.browserIntegerCameraBounds");
        private final int[] world = new int[6];
        private final double[] low = new double[3], high = new double[3];
        private final int[] sine = new int[3], cosine = new int[3];
        private final boolean[] rotated = new boolean[3];
        private int x, y, z, near, scale, viewportY, mouseX, mouseY;
        private boolean usable;

        void set(int x, int y, int z, int rotateZ, int rotateY, int rotateX, int shift, int near,
                 int viewportY, int mouseX, int mouseY, int[] table) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.near = near;
            this.viewportY = viewportY;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            usable = near > 0 && shift >= 0 && shift < 31 && angle(0, rotateZ, table)
                && angle(1, rotateY, table) && angle(2, rotateX, table);
            scale = usable ? 1 << shift : 0;
        }

        private boolean angle(int axis, int angle, int[] table) {
            rotated[axis] = angle != 0;
            if (!rotated[axis]) return true;
            if (table == null || angle < 0 || angle > table.length - 1025) return false;
            sine[axis] = table[angle];
            cosine[axis] = table[angle + 1024];
            return Math.abs((double) sine[axis]) <= 32768 && Math.abs((double) cosine[axis]) <= 32768;
        }

        boolean isUsable() {
            return usable;
        }

        boolean missesMouse(RSModel model) {
            if (!usable || !model.copyWorldFaceBounds(world)
                || !translate(0, x) || !translate(1, y) || !translate(2, z)
                || !rotate(0, 0, 1, false) || !rotate(1, 0, 2, false) || !rotate(2, 1, 2, true)) return false;
            if (low[2] < near && high[2] >= near) return false;
            double x0 = low[0] * scale, x1 = high[0] * scale;
            double y0 = low[1] * scale, y1 = high[1] * scale;
            if (!fitsInt(x0, x1) || !fitsInt(y0, y1)) return false;
            if (low[2] >= near) {
                double x0Depth = x0 < 0 ? low[2] : high[2];
                double x1Depth = x1 < 0 ? high[2] : low[2];
                double y0Depth = y0 < 0 ? low[2] : high[2];
                double y1Depth = y1 < 0 ? high[2] : low[2];
                if (INTEGER_ROUNDING) {
                    // The camera operations have proved integral coordinates and positive depth.
                    x0 = floorQuotient((int) x0, (int) x0Depth);
                    x1 = ceilQuotient((int) x1, (int) x1Depth);
                    y0 = floorQuotient((int) y0, (int) y0Depth);
                    y1 = ceilQuotient((int) y1, (int) y1Depth);
                } else {
                    x0 = Math.floor(x0 / x0Depth);
                    x1 = Math.ceil(x1 / x1Depth);
                    y0 = Math.floor(y0 / y0Depth);
                    y1 = Math.ceil(y1 / y1Depth);
                }
            }
            y0 += viewportY;
            y1 += viewportY;
            if (!fitsInt(y0, y1)) return false;
            int pickScale = Math.max(1, model.getPickBoundsScale());
            double padX = padding((int) x0, (int) x1, pickScale);
            double padY = padding((int) y0, (int) y1, pickScale);
            return mouseX < x0 - padX || mouseX > x1 + padX || mouseY < y0 - padY || mouseY > y1 + padY;
        }

        private boolean translate(int axis, int offset) {
            low[axis] = (double) world[axis * 2] - offset;
            high[axis] = (double) world[axis * 2 + 1] - offset;
            return low[axis] <= high[axis] && fitsInt(low[axis], high[axis]);
        }

        private boolean rotate(int rotation, int first, int second, boolean invert) {
            if (!rotated[rotation]) return true;
            int s = invert ? -sine[rotation] : sine[rotation];
            int c = cosine[rotation];
            double firstLow = minimum(first, c) + minimum(second, s);
            double firstHigh = maximum(first, c) + maximum(second, s);
            double secondLow = minimum(first, -s) + minimum(second, c);
            double secondHigh = maximum(first, -s) + maximum(second, c);
            if (!fitsInt(firstLow, firstHigh) || !fitsInt(secondLow, secondHigh)) return false;
            if (INTEGER_ROUNDING) {
                // The literal model projection uses the same signed fixed-point shift.
                low[first] = (int) firstLow >> 15;
                high[first] = (int) firstHigh >> 15;
                low[second] = (int) secondLow >> 15;
                high[second] = (int) secondHigh >> 15;
            } else {
                low[first] = Math.floor(firstLow / 32768);
                high[first] = Math.floor(firstHigh / 32768);
                low[second] = Math.floor(secondLow / 32768);
                high[second] = Math.floor(secondHigh / 32768);
            }
            return true;
        }

        private static int floorQuotient(int numerator, int depth) {
            int whole = numerator / depth;
            return numerator < 0 && numerator % depth != 0 ? whole - 1 : whole;
        }

        private static int ceilQuotient(int numerator, int depth) {
            int whole = numerator / depth;
            return numerator > 0 && numerator % depth != 0 ? whole + 1 : whole;
        }

        private double minimum(int axis, int coefficient) {
            return (coefficient < 0 ? high[axis] : low[axis]) * coefficient;
        }

        private double maximum(int axis, int coefficient) {
            return (coefficient < 0 ? low[axis] : high[axis]) * coefficient;
        }

        private static boolean fitsInt(double min, double max) {
            return min >= Integer.MIN_VALUE && max <= Integer.MAX_VALUE;
        }
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
