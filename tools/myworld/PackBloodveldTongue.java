import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Packs owner-approved art; fixed nearest-neighbor scale, no creative pixel edits. */
public final class PackBloodveldTongue {
	public static void main(String[] args) throws Exception {
		File root = new File(args.length == 0 ? "." : args[0]);
		String folder = "dev/myworld/assets/sprites/npcs/";
		BufferedImage base = ImageIO.read(new File(root, folder + "bloodveld-tongue/approved-base.png"));
		BufferedImage strip = ImageIO.read(new File(root, folder + "bloodveld-tongue/approved-tongue-strip.png"));
		if (base.getWidth() != 720 || base.getHeight() != 330 || strip.getWidth() != 2172 || strip.getHeight() != 724)
			throw new IllegalArgumentException("Unexpected approved source dimensions");
		BufferedImage out = new BufferedImage(940, 330, BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0, 0, 720, 330, base.getRGB(0, 0, 720, 330, null, 0, 720), 0, 720);
		// Shared 4.8:1 scale from comparison preview. Same baseline and body anchor;
		// 220-pixel attack canvas accommodates tongue without shrinking the body.
		// The renderer centers variable-width canvases: include 50 pixels of left
		// compensation plus six pixels to align the source body's rear with side view.
		int[] origin = {120, 660, 1490};
		int[] width = {470, 780, 680};
		for (int frame = 0; frame < 3; frame++) {
			for (int y = 0; y < 110; y++) for (int x = 0; x < 220; x++) {
				int sx = (int)Math.floor((x - 58) * 4.8), sy = (int)Math.floor(y * 4.8 + 10);
				if (sx >= 0 && sx < width[frame] && sy < strip.getHeight())
					out.setRGB(720+x, frame*110+y, strip.getRGB(origin[frame]+sx, sy));
			}
		}
		ImageIO.write(out, "png", new File(root, folder + "slayer-movement-preview/bloodveld.png"));
	}
}
