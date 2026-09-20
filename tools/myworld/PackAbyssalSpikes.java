import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Lossless packing only: approved base pixels and approved AoE pixels are immutable. */
public final class PackAbyssalSpikes {
	public static void main(String[] args) throws Exception {
		File root = new File(args.length == 0 ? "." : args[0]);
		String folder = "dev/myworld/assets/sprites/npcs/";
		BufferedImage base = ImageIO.read(new File(root, folder + "abyssal-spikes/approved-base.png"));
		BufferedImage strip = ImageIO.read(new File(root, folder + "abyssal-spikes/approved-spikes-strip.png"));
		if (base.getWidth() != 612 || base.getHeight() != 300 || strip.getWidth() != 432 || strip.getHeight() != 112)
			throw new IllegalArgumentException("Unexpected approved source dimensions");
		BufferedImage out = new BufferedImage(756, 336, BufferedImage.TYPE_INT_ARGB);
		for (int row = 0; row < 3; row++) {
			out.setRGB(0, row*112+6, 612, 100, base.getRGB(0,row*100,612,100,null,0,612),0,612);
			out.setRGB(612, row*112, 144, 112, strip.getRGB(row*144,0,144,112,null,0,144),0,144);
		}
		ImageIO.write(out, "png", new File(root, folder + "slayer-movement-preview/abyssal-demon.png"));
	}
}
