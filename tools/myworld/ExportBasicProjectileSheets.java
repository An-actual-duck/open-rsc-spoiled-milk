import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;
import orsc.graphics.two.SpriteArchive.Subspace;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.SpriteArchive.Workspace;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Reproduces the client's legacy eight-angle knife and shuriken projectiles as PNG sheets. */
public final class ExportBasicProjectileSheets {

	private static final int CELL_SIZE = 64;
	private static final int FRAME_COUNT = 8;
	private static final int TARGET_SIZE = 52;
	private static final int IRON_MASK = 0xEEEEED;

	private ExportBasicProjectileSheets() {
	}

	public static void main(String[] args) throws Exception {
		Path root = args.length == 0 ? Paths.get(".").toAbsolutePath().normalize()
			: Paths.get(args[0]).toAbsolutePath().normalize();
		Path animationRoot = root.resolve("dev/myworld/assets/runtime/animations/projectile-moving");

		BufferedImage knife = loadThrowingKnife(root);
		writeRotationSheet(knife,
			animationRoot.resolve("throwing-knife-basic/throwing-knife-basic.png"));

		BufferedImage shuriken = readRequiredImage(root.resolve(
			"dev/myworld/assets/runtime/sprites/items/inventory-ground/shuriken-thrown.png"));
		writeRotationSheet(shuriken,
			animationRoot.resolve("shuriken-basic/shuriken-basic.png"));
	}

	private static BufferedImage loadThrowingKnife(Path root) {
		File archive = root.resolve("Client_Base/Cache/video/Custom_Sprites.osar").toFile();
		Workspace workspace = new Unpacker().unpackArchive(archive);
		if (workspace == null) {
			throw new IllegalStateException("Could not unpack " + archive);
		}
		Subspace items = workspace.getSubspaceByName("items");
		if (items == null) {
			throw new IllegalStateException("Custom sprite archive has no items subspace");
		}
		Entry knifeEntry = null;
		for (Entry entry : items.getEntryList()) {
			if ("80".equals(entry.getID())) {
				knifeEntry = entry;
				break;
			}
		}
		if (knifeEntry == null || knifeEntry.getFrames().length == 0) {
			throw new IllegalStateException("Custom sprite archive has no items:80 throwing-knife source");
		}
		Frame frame = knifeEntry.getFrames()[0];
		BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < frame.getHeight(); y++) {
			for (int x = 0; x < frame.getWidth(); x++) {
				int pixel = frame.getPixels()[y * frame.getWidth() + x];
				if (pixel != 0) {
					image.setRGB(x, y, 0xFF000000 | applyMask(pixel, IRON_MASK));
				}
			}
		}
		return image;
	}

	private static int applyMask(int sourcePixel, int mask) {
		int red = sourcePixel >> 16 & 0xFF;
		int green = sourcePixel >> 8 & 0xFF;
		int blue = sourcePixel & 0xFF;
		if (red == green && green == blue) {
			red = ((mask >> 16 & 0xFF) * red) >> 8;
			green = ((mask >> 8 & 0xFF) * green) >> 8;
			blue = ((mask & 0xFF) * blue) >> 8;
		}
		return red << 16 | green << 8 | blue;
	}

	private static BufferedImage readRequiredImage(Path path) throws IOException {
		BufferedImage image = ImageIO.read(path.toFile());
		if (image == null) {
			throw new IOException("Could not read " + path);
		}
		return image;
	}

	private static void writeRotationSheet(BufferedImage source, Path destination) throws IOException {
		BufferedImage cropped = cropVisible(source);
		BufferedImage sheet = new BufferedImage(CELL_SIZE * FRAME_COUNT, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sheetGraphics = sheet.createGraphics();
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			sheetGraphics.drawImage(rotate(cropped, frame * 45.0D), frame * CELL_SIZE, 0, null);
		}
		sheetGraphics.dispose();
		Files.createDirectories(destination.getParent());
		if (!ImageIO.write(sheet, "png", destination.toFile())) {
			throw new IOException("No PNG writer for " + destination);
		}
	}

	private static BufferedImage rotate(BufferedImage source, double degrees) {
		int targetWidth = TARGET_SIZE;
		int targetHeight = Math.max(1, source.getHeight() * targetWidth / Math.max(1, source.getWidth()));
		if (targetHeight > TARGET_SIZE) {
			targetHeight = TARGET_SIZE;
			targetWidth = Math.max(1, source.getWidth() * targetHeight / Math.max(1, source.getHeight()));
		}

		BufferedImage scaled = new BufferedImage(CELL_SIZE, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D scaleGraphics = scaled.createGraphics();
		configurePixelArt(scaleGraphics);
		scaleGraphics.drawImage(source, (CELL_SIZE - targetWidth) / 2, (CELL_SIZE - targetHeight) / 2,
			targetWidth, targetHeight, null);
		scaleGraphics.dispose();

		BufferedImage rotated = new BufferedImage(CELL_SIZE, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D rotateGraphics = rotated.createGraphics();
		configurePixelArt(rotateGraphics);
		rotateGraphics.rotate(Math.toRadians(degrees), CELL_SIZE / 2.0D, CELL_SIZE / 2.0D);
		rotateGraphics.drawImage(scaled, 0, 0, null);
		rotateGraphics.dispose();
		return rotated;
	}

	private static void configurePixelArt(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
	}

	private static BufferedImage cropVisible(BufferedImage source) {
		int minX = source.getWidth();
		int minY = source.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				if ((source.getRGB(x, y) >>> 24) >= 64) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < minX || maxY < minY) {
			throw new IllegalArgumentException("Projectile source image has no visible pixels");
		}
		return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}
}
