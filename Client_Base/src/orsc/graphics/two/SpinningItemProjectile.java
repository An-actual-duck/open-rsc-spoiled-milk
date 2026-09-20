package orsc.graphics.two;

import com.openrsc.client.model.Sprite;
import orsc.graphics.RendererSpriteTransform;

/** Nearest-neighbour rotation of the real item icon, generated once at load time. */
public final class SpinningItemProjectile {
	private SpinningItemProjectile() { }
	public static int fill(Sprite source, RendererSpriteTransform tint, Sprite[] frames) {
		if (source == null || source.getWidth() == 0 || source.getHeight() == 0) return 0;
		int count = Math.min(16, frames.length);
		int size = (int) Math.ceil(Math.hypot(source.getWidth(), source.getHeight())) + 2;
		double center = (size - 1) / 2.0;
		for (int f = 0; f < count; f++) {
			int[] pixels = new int[size * size];
			double angle = f * Math.PI * 4 / count; // Two rotations during flight.
			double cos = Math.cos(angle), sin = Math.sin(angle);
			for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
				double dx = x - center, dy = y - center;
				int sx = (int) Math.round(dx * cos + dy * sin + (source.getWidth() - 1) / 2.0);
				int sy = (int) Math.round(-dx * sin + dy * cos + (source.getHeight() - 1) / 2.0);
				if (sx >= 0 && sy >= 0 && sx < source.getWidth() && sy < source.getHeight())
					pixels[y * size + x] = tint.apply(source.getPixels()[sy * source.getWidth() + sx]);
			}
			frames[f] = new Sprite(pixels, size, size);
			frames[f].setSomething(size, size);
		}
		return count;
	}
}
