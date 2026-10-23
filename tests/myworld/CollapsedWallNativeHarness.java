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

/** Offline smoke using the actual maintained client decoder and software renderer. */
public final class CollapsedWallNativeHarness {
    public static void main(String[] args) throws Exception {
        byte[] archive = Files.readAllBytes(Paths.get(args[0]));
        byte[] body = Arrays.copyOfRange(archive, 6, archive.length);
        int offset = DataOperations.getDataFileOffset("sm_collapsed_wall.ob3", body);
        if (offset == 0) throw new AssertionError("Missing native model");
        EntityHandler.load(true);
        if (!"sm_collapsed_wall".equals(EntityHandler.getObjectDef(1339).getObjectModel()))
            throw new AssertionError("Definition registry mismatch");
        if (EntityHandler.getModelCount() + 24 >= 1000)
            throw new AssertionError("Model cache capacity exhausted");
        RSModel decoded = new RSModel(body, offset, true);
        if (decoded.vertHead != 130 || decoded.faceHead != 91)
            throw new AssertionError("Wrong native mesh size");
        int minY = 0;
        java.lang.reflect.Field vertical = RSModel.class.getDeclaredField("vertY");
        vertical.setAccessible(true);
        for (int y : (int[]) vertical.get(decoded)) {
            if (y > 0) throw new AssertionError("Native model below ground");
            minY = Math.min(minY, y);
        }
        if (minY != -69) throw new AssertionError("Wrong native height: " + minY);
        Config.S_WANT_CUSTOM_SPRITES = true;
        Files.createDirectories(Paths.get(args[1]));
        for (int rotation = 0; rotation < 1024; rotation += 256) {
            MudClientGraphics graphics = new MudClientGraphics(512, 384, 1);
            Arrays.fill(graphics.pixelData, 0x8d987e);
            Scene scene = new Scene(graphics, 8, 1024, 4);
            scene.setMidpoints(192, true, 512, 256, 192, 9, 256);
            scene.fogLandscapeDistance = 4000;
            scene.fogEntityDistance = 4000;
            RSModel model = new RSModel(body, offset, true);
            // Exact ordinary scenery lighting used by mudclient's object path.
            model.setDiffuseLightAndColor(-50, -10, -50, 48, 48, true, -74);
            scene.addModel(model);
            scene.setCamera(0, -25, 0, 912, rotation + 128, 0, 700);
            scene.forceLegacyWorldRasterOnce();
            scene.endScene(0);
            int changed = 0;
            for (int pixel : graphics.pixelData) if (pixel != 0x8d987e) changed++;
            if (changed < 500) throw new AssertionError("Native renderer produced no usable model: " + changed);
            BufferedImage image = new BufferedImage(512, 384, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 512, 384, graphics.pixelData, 0, 512);
            ImageIO.write(image, "png", Paths.get(args[1], "native-" + rotation + ".png").toFile());
            System.out.println("Native renderer angle " + rotation + ": " + changed + " visible pixels");
        }
        System.out.println("PASS native RSModel decode, registry, capacity, ground and four software renders; modelCount="
            + EntityHandler.getModelCount());
    }
}
