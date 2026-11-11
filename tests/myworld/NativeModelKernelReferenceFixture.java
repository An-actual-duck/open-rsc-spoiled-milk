package orsc.graphics.three;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Random;
import orsc.MiscFunctions;
import orsc.util.FastMath;

/** Runs the real frozen RSModel, not a second handwritten arithmetic formula. */
public final class NativeModelKernelReferenceFixture {
    static final int[] EDGE = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1073741824,
        -32769, -32768, -1, 0, 1, 32767, 32768, 1073741824, Integer.MAX_VALUE};
    static final int[] ANGLE = {0, 1, 255, 256, 511, 512, 767, 768, 1023};

    static void field(RSModel m, String name, Object value) throws Exception {
        Field f = RSModel.class.getDeclaredField(name); f.setAccessible(true); f.set(m, value);
    }

    static RSModel model(int count, int[][] arrays) throws Exception {
        RSModel m = new RSModel(Math.max(1, arrays[0].length), 0);
        m.vertHead = count; m.m_Yb = 0;
        field(m, "vertXTransform", arrays[0]); field(m, "vertYTransform", arrays[1]);
        field(m, "vertZTransform", arrays[2]);
        m.vertexParam6 = arrays[3]; m.vertexParam2 = arrays[4];
        m.vertXRot = arrays[5]; m.vertYRot = arrays[6]; m.vertZRot = arrays[7];
        for (String name : new String[]{"minX", "minY", "minZ"}) field(m, name, Integer.MIN_VALUE);
        for (String name : new String[]{"maxX", "maxY", "maxZ"}) field(m, name, Integer.MAX_VALUE);
        MiscFunctions.frustumMinX = MiscFunctions.frustumMaxX = 0;
        MiscFunctions.frustumMinY = MiscFunctions.frustumMaxY = 0;
        MiscFunctions.frustumNearZ = MiscFunctions.frustumFarZ = 0;
        return m;
    }

    static void rotate(RSModel m, int[] args) {
        m.rotate1024(args[1], args[3], args[0], (byte)-122, args[2], args[6], args[5], args[7], args[4]);
    }

    static int[][] cloneArrays(int[][] arrays) {
        int[][] copy = new int[arrays.length][];
        for (int n = 0; n < arrays.length; n++) copy[n] = arrays[n].clone();
        return copy;
    }

    static void writeArrays(DataOutputStream out, int[][] arrays) throws Exception {
        for (int[] array : arrays) { out.writeInt(array.length); for (int value : array) out.writeInt(value); }
    }

    static int[][] readArrays(DataInputStream in) throws Exception {
        int[][] a = new int[8][];
        for (int n = 0; n < a.length; n++) { a[n] = new int[in.readInt()]; for (int v = 0; v < a[n].length; v++) a[n][v] = in.readInt(); }
        return a;
    }

    public static void main(String[] args) throws Exception {
        Random random = new Random(40271983);
        int cases = args.length > 1 ? Integer.parseInt(args[1]) : 6000;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))) {
            out.writeInt(0x4e4b3031); out.writeInt(cases);
            for (int trial = 0; trial < cases; trial++) {
                int count = 1 + random.nextInt(64), length = count + 3;
                int[][] arrays = new int[8][length];
                for (int n = 0; n < arrays.length; n++) for (int v = 0; v < length; v++)
                    arrays[n][v] = trial % 3 == 0 ? EDGE[random.nextInt(EDGE.length)] : random.nextInt();
                if (trial % 5 == 0) for (int n = 0; n < 3; n++) for (int v = 0; v < count; v++) arrays[n][v] = random.nextInt(65537) - 32768;
                int[] camera = new int[8];
                boolean uniform = args.length > 2 && args[2].equals("batch");
                int cameraTrial = uniform ? trial / 256 : trial;
                Random cameraRandom = uniform ? new Random(13043 + cameraTrial) : random;
                for (int n = 0; n < 3; n++) camera[n] = cameraTrial % 2 == 0 ? EDGE[cameraRandom.nextInt(EDGE.length)] : cameraRandom.nextInt();
                camera[3] = cameraTrial % 4 == 0 ? cameraRandom.nextInt() : cameraRandom.nextInt(97) - 32;
                camera[4] = new int[]{1, 8, 50, 1024, Integer.MAX_VALUE}[cameraTrial % 5];
                camera[5] = cameraTrial % 8 < 4 ? 0 : ANGLE[cameraRandom.nextInt(ANGLE.length)];
                camera[6] = cameraTrial % 4 < 2 ? 0 : ANGLE[cameraRandom.nextInt(ANGLE.length)];
                camera[7] = cameraTrial % 2 == 0 ? 0 : ANGLE[cameraRandom.nextInt(ANGLE.length)];
                int[][] expected = cloneArrays(arrays);
                rotate(model(count, expected), camera);
                out.writeInt(count); for (int v : camera) out.writeInt(v);
                int rz = camera[5], ry = camera[6], rx = camera[7];
                for (int angle : new int[]{rz, ry, rx}) {
                    out.writeInt(angle == 0 ? 0 : FastMath.trigTable1024[angle]);
                    out.writeInt(angle == 0 ? 0 : FastMath.trigTable1024[angle + 1024]);
                }
                writeArrays(out, arrays); writeArrays(out, expected);
            }
        }
        System.out.println("FROZEN_REFERENCE_CASES=" + cases);
    }
}
