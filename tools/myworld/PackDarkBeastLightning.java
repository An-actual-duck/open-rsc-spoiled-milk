import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Deterministic packing of approved art with the approved common foot baseline. */
public final class PackDarkBeastLightning {
	public static void main(String[] args) throws Exception {
		File root = new File(args.length == 0 ? "." : args[0]);
		String folder = "dev/myworld/assets/sprites/npcs/";
		BufferedImage base = ImageIO.read(new File(root, folder + "dark-beast-lightning/approved-base.png"));
		BufferedImage strip = ImageIO.read(new File(root, folder + "dark-beast-lightning/approved-charge-strip.png"));
		if (base.getWidth()!=720 || base.getHeight()!=330 || strip.getWidth()!=2172 || strip.getHeight()!=724)
			throw new IllegalArgumentException("Unexpected approved source dimensions");
		BufferedImage out = new BufferedImage(840,330,BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0,0,720,330,base.getRGB(0,0,720,330,null,0,720),0,720);
		// Browser comparison: charge at (10,18), reference at (10,10), scale 1/7.
		// Sample at destination pixel centres, nearest-neighbour; never auto-fit bounds.
		for(int frame=0;frame<3;frame++) for(int y=0;y<110;y++) for(int x=0;x<120;x++) {
			int sx=(int)Math.floor((x+0.5)*7), sy=(int)Math.floor((y-8+0.5)*7);
			if(sx>=0 && sx<724 && sy>=0 && sy<724)
				out.setRGB(720+x,frame*110+y,strip.getRGB(frame*724+sx,sy));
		}
		ImageIO.write(out,"png",new File(root,folder+"slayer-movement-preview/dark-beast.png"));
	}
}
