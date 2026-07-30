package orsc.remastered;

import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.RendererTransparency;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RemasteredSpriteResolver {
	private static final String RESOURCE_ROOT = "myworld-assets/remastered-sprites/";
	private static final String DEVELOPMENT_ROOT = "dev/myworld/assets/remastered-sprites";

	private final Map<String, Sprite> loaded = new HashMap<String, Sprite>();
	private final Set<String> invalid = new HashSet<String>();
	private final Set<String> loggedFailures = new HashSet<String>();
	private final RemasteredAnimationKeyCache animationKeyCache =
		new RemasteredAnimationKeyCache();
	private long selectedCount;
	private long disabledFallbackCount;
	private long absentFallbackCount;
	private long invalidFallbackCount;
	private long decodeCount;

	public synchronized Sprite resolve(String key, Sprite canonical) {
		return resolveKey(key, canonical);
	}

	public synchronized Sprite resolve(AnimationDef animation, int frame, Sprite canonical) {
		return resolveKey(animationKeyCache.key(animation, frame), canonical);
	}

	private Sprite resolveKey(String key, Sprite canonical) {
		if (canonical == null) {
			return null;
		}
		if (!RemasteredSpriteSettings.isEnabled()) {
			disabledFallbackCount++;
			return canonical;
		}
		RemasteredSpriteCatalog.Entry entry = RemasteredSpriteCatalog.get(key);
		if (entry == null) {
			absentFallbackCount++;
			return canonical;
		}
		if (!matchesCanonical(entry, canonical) || invalid.contains(key)) {
			invalidFallbackCount++;
			if (!matchesCanonical(entry, canonical)) {
				logOnce(key, "canonical contract mismatch");
			}
			return canonical;
		}
		Sprite override = loaded.get(key);
		if (override == null) {
			override = load(entry, canonical);
			if (override == null) {
				invalid.add(key);
				invalidFallbackCount++;
				return canonical;
			}
			loaded.put(key, override);
		}
		selectedCount++;
		return override;
	}

	public synchronized String diagnostics() {
		return "enabled=" + RemasteredSpriteSettings.isEnabled()
			+ " catalog=" + RemasteredSpriteCatalog.size()
			+ " loaded=" + loaded.size()
			+ " invalid=" + invalid.size()
			+ " selected=" + selectedCount
			+ " fallbackDisabled=" + disabledFallbackCount
			+ " fallbackAbsent=" + absentFallbackCount
			+ " fallbackInvalid=" + invalidFallbackCount
			+ " decodes=" + decodeCount
			+ " revision=" + RemasteredSpriteCatalog.revision();
	}

	public synchronized Set<String> loadedKeys() {
		return Collections.unmodifiableSet(new HashSet<String>(loaded.keySet()));
	}

	private boolean matchesCanonical(RemasteredSpriteCatalog.Entry entry, Sprite canonical) {
		return canonical.getWidth() == entry.getPixelWidth()
			&& canonical.getHeight() == entry.getPixelHeight()
			&& canonical.requiresShift() == entry.requiresShift()
			&& canonical.getXShift() == entry.getShiftX()
			&& canonical.getYShift() == entry.getShiftY()
			&& canonical.getSomething1() == entry.getBoundWidth()
			&& canonical.getSomething2() == entry.getBoundHeight();
	}

	private Sprite load(RemasteredSpriteCatalog.Entry entry, Sprite canonical) {
		try {
			BufferedImage image = read(entry.getPng());
			if (image == null) {
				logOnce(entry.getKey(), "packaged PNG is missing: " + entry.getPng());
				return null;
			}
			if (image.getWidth() != entry.getPixelWidth() || image.getHeight() != entry.getPixelHeight()) {
				logOnce(entry.getKey(), "PNG dimensions changed: " + image.getWidth() + "x" + image.getHeight());
				return null;
			}
			int pixelCount = image.getWidth() * image.getHeight();
			int[] pixels = new int[pixelCount];
			image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = normalizePixel(pixels[i], entry.getAlphaThreshold());
			}
			Sprite override = new Sprite(pixels, image.getWidth(), image.getHeight());
			override.setRequiresShift(canonical.requiresShift());
			override.setShift(canonical.getXShift(), canonical.getYShift());
			override.setSomething(canonical.getSomething1(), canonical.getSomething2());
			override.setName(canonical.getID(), canonical.getPackageName());
			decodeCount++;
			return override;
		} catch (IOException | RuntimeException exception) {
			logOnce(entry.getKey(), "decode failed: " + exception.getMessage());
			return null;
		}
	}

	private BufferedImage read(String relativePath) throws IOException {
		Path userDirectory = Paths.get(System.getProperty("user.dir", ".")).normalize();
		Path[] candidates = new Path[] {
			userDirectory.resolve(DEVELOPMENT_ROOT).resolve(relativePath).normalize(),
			userDirectory.resolve("..").resolve(DEVELOPMENT_ROOT).resolve(relativePath).normalize()
		};
		for (Path candidate : candidates) {
			File file = candidate.toFile();
			if (file.isFile()) {
				return ImageIO.read(file);
			}
		}
		String resource = RESOURCE_ROOT + relativePath;
		try (InputStream input = RemasteredSpriteResolver.class.getClassLoader().getResourceAsStream(resource)) {
			return input == null ? null : ImageIO.read(input);
		}
	}

	private int normalizePixel(int argb, int alphaThreshold) {
		int alpha = argb >>> 24;
		if (alpha < alphaThreshold) {
			return RendererTransparency.TRANSPARENT_SAMPLE;
		}
		int rgb = argb & RendererTransparency.RGB_MASK;
		return rgb == RendererTransparency.TRANSPARENT_SAMPLE
			? RendererTransparency.OPAQUE_BLACK_REPLACEMENT
			: rgb;
	}

	private void logOnce(String key, String reason) {
		if (loggedFailures.add(key)) {
			System.out.println("[remastered-sprites] " + key + " fallback: " + reason);
		}
	}
}
