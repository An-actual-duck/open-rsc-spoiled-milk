package orsc.graphics.three;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.data.DataOperations;
import orsc.Config;
import orsc.graphics.two.MudClientGraphics;

/** Offline native decoder/registry/renderer proof, not owner aesthetic approval. */
public final class TowerRuinsNativeHarness {
    private static final String[] MODELS = {"sm_broken_table", "sm_destroyed_barrel",
        "sm_fallen_pillar", "sm_dilapidated_bookshelf", "sm_ruined_books"};
    private static final int[] VERTICES = {140, 190, 62, 202, 170};
    private static final int[] FACES = {100, 135, 41, 149, 127};
    private static final int[] HEIGHTS = {90, 89, 69, 183, 43};
    public static void main(String[] args) throws Exception {
        byte[] archive = Files.readAllBytes(Paths.get(args[0]));
        byte[] body = Arrays.copyOfRange(archive, 6, archive.length);
        EntityHandler.load(true);
        if (EntityHandler.getModelCount() + 24 >= 1000)
            throw new AssertionError("Model cache capacity exhausted");
        Config.S_WANT_CUSTOM_SPRITES = true;
        Files.createDirectories(Paths.get(args[1]));
        java.lang.reflect.Field vertical = RSModel.class.getDeclaredField("vertY");
        vertical.setAccessible(true);
        for (int asset = 0; asset < MODELS.length; asset++) {
            String name = MODELS[asset];
            if (!name.equals(EntityHandler.getObjectDef(1340 + asset).getObjectModel()))
                throw new AssertionError("Registry mismatch " + name);
            int offset = DataOperations.getDataFileOffset(name + ".ob3", body);
            if (offset == 0) throw new AssertionError("Missing native model " + name);
            RSModel decoded = new RSModel(body, offset, true);
            if (decoded.vertHead != VERTICES[asset] || decoded.faceHead != FACES[asset])
                throw new AssertionError("Wrong native mesh size " + name);
            int minY = 0, maxY = Integer.MIN_VALUE;
            int[] ys = (int[]) vertical.get(decoded);
            for (int i = 0; i < decoded.vertHead; i++) {
                minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
            }
            if (minY != -HEIGHTS[asset] || maxY != 0)
                throw new AssertionError("Native ground/height mismatch " + name);
            // Denser sweep covers the previously flickering barrel base/loose wood.
            for (int angle = 0; angle < 1024; angle += asset == 1 ? 64 : 256) {
                MudClientGraphics graphics = new MudClientGraphics(512, 384, 1);
                Arrays.fill(graphics.pixelData, 0x8d987e);
                Scene scene = new Scene(graphics, 8, 2048, 4);
                scene.setMidpoints(192, true, 512, 256, 192, 9, 256);
                scene.fogLandscapeDistance = 4000; scene.fogEntityDistance = 4000;
                RSModel model = new RSModel(body, offset, true);
                model.setDiffuseLightAndColor(-50, -10, -50, 48, 48, true, -74);
                scene.addModel(model);
                scene.setCamera(0, -HEIGHTS[asset]/2, 0, 912, angle + 128, 0, 700);
                scene.forceLegacyWorldRasterOnce(); scene.endScene(0);
                int changed = 0;
                for (int pixel : graphics.pixelData) if (pixel != 0x8d987e) changed++;
                if (changed < 300) throw new AssertionError("No usable native render " + name);
                BufferedImage image = new BufferedImage(512, 384, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, 512, 384, graphics.pixelData, 0, 512);
                ImageIO.write(image, "png", Paths.get(args[1], name + "-" + angle + ".png").toFile());
                System.out.println(name + " angle=" + angle + " visiblePixels=" + changed);
            }
        }
        System.out.println("PASS five native models, registries, ground/height and 32 actual Scene renders; modelCount="
            + EntityHandler.getModelCount());
    }
}
