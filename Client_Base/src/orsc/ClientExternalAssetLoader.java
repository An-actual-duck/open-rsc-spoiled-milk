package orsc;

import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers and decodes maintained external PNG assets from development,
 * legacy-output, and packaged client locations. Content catalogs, destination
 * sprite indexes, procedural fallbacks, and animation timing remain with the
 * client coordinator.
 */
final class ClientExternalAssetLoader {
	static final String DEVELOPMENT_ASSET_ROOT = "dev/myworld/assets/";
	static final String EMBEDDED_ASSET_ROOT = "myworld-assets/";
	private static final String EXTERNAL_ITEM_PREFIX = "external-png:";
	private static final int PRAYER_ICON_ALPHA_THRESHOLD = 128;
	private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");
	private static final String[] ITEM_ASSET_PATHS = new String[] {
		"dev/myworld/assets/sprites/items/inventory-ground/agility-pouches",
		"dev/myworld/assets/sprites/items/inventory-ground/tools",
		"dev/myworld/assets/sprites/items/inventory-ground/weapons",
		"dev/myworld/assets/sprites/items/inventory-ground/resources/sigils",
		"dev/myworld/assets/sprites/items/inventory-ground/resources",
		"dev/myworld/assets/sprites/items/inventory-ground",
		"output/pngs"
	};

	private final Path userDirectory;
	private final Class<?> resourceAnchor;
	private final Map<String, Sprite> itemSpriteCache = new HashMap<String, Sprite>();
	private Set<String> embeddedAssetResources;

	ClientExternalAssetLoader() {
		this(Paths.get(System.getProperty("user.dir")), mudclient.class);
	}

	ClientExternalAssetLoader(Path userDirectory, Class<?> resourceAnchor) {
		if (userDirectory == null || resourceAnchor == null) {
			throw new IllegalArgumentException("External asset loader requires a working directory and resource anchor");
		}
		this.userDirectory = userDirectory.normalize();
		this.resourceAnchor = resourceAnchor;
	}

	Path[] getAssetCandidateBases(String... relativePaths) {
		ArrayList<Path> candidates = new ArrayList<Path>();
		for (String relativePath : relativePaths) {
			candidates.add(this.userDirectory.resolve(relativePath).normalize());
			candidates.add(this.userDirectory.resolve("../").resolve(relativePath).normalize());
			candidates.add(this.userDirectory.resolve("../Core-Framework").resolve(relativePath).normalize());
		}
		return candidates.toArray(new Path[candidates.size()]);
	}

	File findFirstFile(String[] relativePaths, String... candidateNames) {
		Path[] candidateBases = getAssetCandidateBases(relativePaths);
		for (Path basePath : candidateBases) {
			for (String candidateName : candidateNames) {
				File candidate = basePath.resolve(candidateName).toFile();
				if (assetFileExists(candidate)) {
					return candidate;
				}
			}
		}
		return candidateBases[0].resolve(candidateNames[0]).toFile();
	}

	File findFirstDirectory(String[] relativePaths, String... childPaths) {
		Path[] candidateBases = getAssetCandidateBases(relativePaths);
		for (Path basePath : candidateBases) {
			Path candidatePath = basePath;
			for (String childPath : childPaths) {
				candidatePath = candidatePath.resolve(childPath);
			}
			File candidate = candidatePath.toFile();
			if (assetDirectoryExists(candidate)) {
				return candidate;
			}
		}
		Path fallback = candidateBases[0];
		for (String childPath : childPaths) {
			fallback = fallback.resolve(childPath);
		}
		return fallback.toFile();
	}

	String getEmbeddedAssetResource(File sourceFile) {
		String path = sourceFile.getPath().replace('\\', '/');
		int rootIndex = path.indexOf(DEVELOPMENT_ASSET_ROOT);
		if (rootIndex < 0) {
			return null;
		}
		return EMBEDDED_ASSET_ROOT + path.substring(rootIndex + DEVELOPMENT_ASSET_ROOT.length());
	}

	Set<String> getEmbeddedAssetResources() {
		if (this.embeddedAssetResources != null) {
			return this.embeddedAssetResources;
		}
		this.embeddedAssetResources = new HashSet<String>();
		try {
			URL location = this.resourceAnchor.getProtectionDomain().getCodeSource().getLocation();
			File archive = new File(location.toURI());
			if (!archive.isFile()) {
				return this.embeddedAssetResources;
			}
			try (JarFile jar = new JarFile(archive)) {
				Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements()) {
					String name = entries.nextElement().getName();
					if (name.startsWith(EMBEDDED_ASSET_ROOT)
						&& name.toLowerCase(Locale.ENGLISH).endsWith(".png")) {
						this.embeddedAssetResources.add(name);
					}
				}
			}
		} catch (Exception failure) {
			System.out.println("Failed to inventory packaged external assets: " + failure.getMessage());
		}
		return this.embeddedAssetResources;
	}

	boolean assetFileExists(File sourceFile) {
		if (sourceFile.isFile()) {
			return true;
		}
		String resource = getEmbeddedAssetResource(sourceFile);
		return resource != null && getResourceClassLoader().getResource(resource) != null;
	}

	boolean assetDirectoryExists(File sourceFolder) {
		if (sourceFolder.isDirectory()) {
			return true;
		}
		String resource = getEmbeddedAssetResource(sourceFolder);
		if (resource == null) {
			return false;
		}
		String prefix = resource.endsWith("/") ? resource : resource + "/";
		for (String embeddedResource : getEmbeddedAssetResources()) {
			if (embeddedResource.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	BufferedImage readAssetImage(File sourceFile) throws IOException {
		if (sourceFile.isFile()) {
			return ImageIO.read(sourceFile);
		}
		String resource = getEmbeddedAssetResource(sourceFile);
		if (resource == null) {
			return null;
		}
		try (InputStream input = getResourceClassLoader().getResourceAsStream(resource)) {
			return input == null ? null : ImageIO.read(input);
		}
	}

	private ClassLoader getResourceClassLoader() {
		ClassLoader classLoader = this.resourceAnchor.getClassLoader();
		return classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
	}

	Sprite getExternalItemSprite(ItemDef item) {
		if (item == null || item.getSpriteLocation() == null) {
			return null;
		}
		String spriteLocation = item.getSpriteLocation();
		if (!spriteLocation.startsWith(EXTERNAL_ITEM_PREFIX)) {
			return null;
		}
		String assetSpec = spriteLocation.substring(EXTERNAL_ITEM_PREFIX.length());
		if (assetSpec.length() == 0 || assetSpec.contains("/") || assetSpec.contains("\\")) {
			return null;
		}
		String assetName = assetSpec;
		int targetWidth = 46;
		int targetHeight = 30;
		int sizeIndex = assetSpec.indexOf('@');
		if (sizeIndex > 0) {
			assetName = assetSpec.substring(0, sizeIndex);
			String[] dimensions = assetSpec.substring(sizeIndex + 1).split("x");
			if (dimensions.length == 2) {
				try {
					targetWidth = Math.max(1, Math.min(46, Integer.parseInt(dimensions[0])));
					targetHeight = Math.max(1, Math.min(30, Integer.parseInt(dimensions[1])));
				} catch (NumberFormatException ignored) {
					targetWidth = 46;
					targetHeight = 30;
				}
			}
		}
		if (assetName.length() == 0) {
			return null;
		}
		Sprite cachedSprite = this.itemSpriteCache.get(assetSpec);
		if (cachedSprite != null) {
			return cachedSprite;
		}
		String fileName = assetName.endsWith(".png") ? assetName : assetName + ".png";
		Sprite loadedSprite = loadExternalItemSprite(
			findFirstFile(ITEM_ASSET_PATHS, fileName), targetWidth, targetHeight);
		if (loadedSprite != null) {
			this.itemSpriteCache.put(assetSpec, loadedSprite);
		}
		return loadedSprite;
	}

	Sprite loadExternalItemSprite(File sourceFile) {
		return loadExternalItemSprite(sourceFile, 46, 30);
	}

	Sprite loadExternalItemSprite(File sourceFile, int maxTargetWidth, int maxTargetHeight) {
		if (!assetFileExists(sourceFile)) {
			return null;
		}
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null) {
				return null;
			}
			BufferedImage cropped = cropVisibleImage(source);
			if (cropped == null) {
				return null;
			}
			BufferedImage scaled = new BufferedImage(48, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = createNearestNeighborGraphics(scaled);
			int targetWidth = maxTargetWidth;
			int targetHeight = Math.max(1, (cropped.getHeight() * targetWidth) / Math.max(1, cropped.getWidth()));
			if (targetHeight > maxTargetHeight) {
				targetHeight = maxTargetHeight;
				targetWidth = Math.max(1, (cropped.getWidth() * targetHeight) / Math.max(1, cropped.getHeight()));
			}
			int drawX = (48 - targetWidth) / 2;
			int drawY = (32 - targetHeight) / 2;
			graphics.drawImage(cropped, drawX, drawY, targetWidth, targetHeight, null);
			graphics.dispose();

			int[] pixels = new int[48 * 32];
			scaled.getRGB(0, 0, 48, 32, pixels, 0, 48);
			normalizePixels(pixels, 64);
			return createSprite(pixels, 48, 32);
		} catch (IOException failure) {
			System.out.println("Failed to load external item sprite " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return null;
		}
	}

	Sprite loadExternalInterfaceSprite(File sourceFile, int targetWidth, int targetHeight) {
		if (!assetFileExists(sourceFile)) {
			return null;
		}
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null) {
				return null;
			}
			BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = createNearestNeighborGraphics(scaled);
			graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
			graphics.dispose();
			int[] pixels = new int[targetWidth * targetHeight];
			scaled.getRGB(0, 0, targetWidth, targetHeight, pixels, 0, targetWidth);
			normalizePixels(pixels, 64);
			return createSprite(pixels, targetWidth, targetHeight);
		} catch (IOException failure) {
			System.out.println("Failed to load external interface sprite " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return null;
		}
	}

	Frame loadExternalEquipmentFrame(File sourceFile, int offsetX, int offsetY) {
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null) {
				return null;
			}
			int width = source.getWidth();
			int height = source.getHeight();
			Frame frame = new Frame(
				width, height, true, offsetX, offsetY, 64, 102);
			int[] pixels = frame.getPixels();
			source.getRGB(0, 0, width, height, pixels, 0, width);
			normalizePixels(pixels, 64);
			return frame;
		} catch (IOException failure) {
			System.out.println("Failed to load external equipment frame " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return null;
		}
	}

	/**
	 * Loads the native-size NPC sheet contract used by the Foundry Dragon. Each
	 * column is one camera-relative direction and contains its three animation
	 * frames from top to bottom. The supplied 1465px sheet has five 244px
	 * directional columns and a 245px combat column; the final remainder pixel
	 * belongs to that last column so no artwork is rescaled or cropped.
	 */
	Entry loadExternalNpcDirectionSheet(File sourceFile, String spriteName,
			int directionColumns, int framesPerDirection) {
		if (spriteName == null || spriteName.length() == 0
			|| directionColumns <= 0 || framesPerDirection <= 0) {
			return null;
		}
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null) {
				return null;
			}
			int frameWidth = source.getWidth() / directionColumns;
			int frameHeight = source.getHeight() / framesPerDirection;
			int trailingWidth = source.getWidth() - frameWidth * directionColumns;
			if (frameWidth <= 0 || frameHeight <= 0
				|| source.getHeight() != frameHeight * framesPerDirection
				|| trailingWidth < 0 || trailingWidth > 1) {
				return null;
			}

			Entry entry = new Entry(spriteName, Entry.TYPE.NPC, null,
				directionColumns * framesPerDirection);
			for (int direction = 0; direction < directionColumns; direction++) {
				int directionFrameWidth = frameWidth
					+ (direction == directionColumns - 1 ? trailingWidth : 0);
				for (int frameIndex = 0; frameIndex < framesPerDirection; frameIndex++) {
					BufferedImage sourceFrame = source.getSubimage(
						direction * frameWidth, frameIndex * frameHeight,
						directionFrameWidth, frameHeight);
					Frame frame = new Frame(directionFrameWidth, frameHeight, false, 0, 0,
						directionFrameWidth, frameHeight);
					sourceFrame.getRGB(0, 0, directionFrameWidth, frameHeight,
						frame.getPixels(), 0, directionFrameWidth);
					normalizePixels(frame.getPixels(), 64);
					entry.getFrames()[direction * framesPerDirection + frameIndex] = frame;
				}
			}
			return entry;
		} catch (IOException failure) {
			System.out.println("Failed to load external NPC direction sheet "
				+ sourceFile.getPath() + ": " + failure.getMessage());
			return null;
		}
	}

	Sprite loadExternalPrayerIconFile(File sourceFile) {
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null) {
				return null;
			}
			return createExternalIconSprite(source, 64, getSheetBackgroundArgb(source));
		} catch (IOException failure) {
			System.out.println("Failed to load prayer icon " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return null;
		}
	}

	Sprite[] loadExternalPrayerIconSheet(File sourceFile) {
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null) {
				return new Sprite[0];
			}
			int backgroundArgb = getSheetBackgroundArgb(source);
			ArrayList<Rectangle> regions = findPrayerIconRegions(source, backgroundArgb);
			Sprite[] sprites = new Sprite[Math.min(5, regions.size())];
			for (int i = 0; i < sprites.length; i++) {
				Rectangle region = regions.get(i);
				sprites[i] = createExternalIconSprite(
					source.getSubimage(region.x, region.y, region.width, region.height), 64, backgroundArgb);
			}
			return sprites;
		} catch (IOException failure) {
			System.out.println("Failed to load prayer icon sheet " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return new Sprite[0];
		}
	}

	private ArrayList<Rectangle> findPrayerIconRegions(BufferedImage source, int backgroundArgb) {
		int rowGap = Math.max(6, source.getHeight() / 40);
		int columnGap = Math.max(6, source.getWidth() / 80);
		ArrayList<int[]> rowRanges = findContentRanges(getContentRows(source, backgroundArgb), rowGap);
		if (rowRanges.size() != 2) {
			return getFallbackPrayerIconRegions(source, backgroundArgb);
		}

		ArrayList<Rectangle> regions = new ArrayList<Rectangle>();
		for (int rowIndex = 0; rowIndex < rowRanges.size(); rowIndex++) {
			int[] rowRange = rowRanges.get(rowIndex);
			ArrayList<int[]> columnRanges = findContentRanges(
				getContentColumns(source, rowRange[0], rowRange[1], backgroundArgb), columnGap);
			int expectedColumns = rowIndex == 0 ? 3 : 2;
			if (columnRanges.size() != expectedColumns) {
				return getFallbackPrayerIconRegions(source, backgroundArgb);
			}
			for (int[] columnRange : columnRanges) {
				regions.add(expandAndTrimIconRegion(source,
					new Rectangle(columnRange[0], rowRange[0], columnRange[1] - columnRange[0] + 1,
						rowRange[1] - rowRange[0] + 1), backgroundArgb));
			}
		}
		return regions;
	}

	private ArrayList<Rectangle> getFallbackPrayerIconRegions(BufferedImage source, int backgroundArgb) {
		ArrayList<Rectangle> regions = new ArrayList<Rectangle>();
		int halfHeight = source.getHeight() / 2;
		int topCellWidth = Math.max(1, source.getWidth() / 3);
		for (int column = 0; column < 3; column++) {
			int x1 = (source.getWidth() * column) / 3;
			int x2 = (source.getWidth() * (column + 1)) / 3;
			regions.add(expandAndTrimIconRegion(
				source, new Rectangle(x1, 0, x2 - x1, halfHeight), backgroundArgb));
		}
		for (int column = 0; column < 2; column++) {
			int x1 = Math.max(0, (source.getWidth() - 2 * topCellWidth) / 2 + column * topCellWidth);
			int x2 = Math.min(source.getWidth(), x1 + topCellWidth);
			regions.add(expandAndTrimIconRegion(source,
				new Rectangle(x1, halfHeight, x2 - x1, source.getHeight() - halfHeight), backgroundArgb));
		}
		return regions;
	}

	private Rectangle expandAndTrimIconRegion(BufferedImage source, Rectangle region, int backgroundArgb) {
		int padding = 2;
		int x1 = Math.max(0, region.x - padding);
		int y1 = Math.max(0, region.y - padding);
		int x2 = Math.min(source.getWidth() - 1, region.x + region.width - 1 + padding);
		int y2 = Math.min(source.getHeight() - 1, region.y + region.height - 1 + padding);
		int minX = x2;
		int minY = y2;
		int maxX = x1;
		int maxY = y1;
		boolean found = false;
		for (int y = y1; y <= y2; y++) {
			for (int x = x1; x <= x2; x++) {
				if (isIconContentPixel(source.getRGB(x, y), backgroundArgb)) {
					if (x < minX) minX = x;
					if (y < minY) minY = y;
					if (x > maxX) maxX = x;
					if (y > maxY) maxY = y;
					found = true;
				}
			}
		}
		if (!found) {
			return new Rectangle(x1, y1, x2 - x1 + 1, y2 - y1 + 1);
		}
		minX = Math.max(0, minX - padding);
		minY = Math.max(0, minY - padding);
		maxX = Math.min(source.getWidth() - 1, maxX + padding);
		maxY = Math.min(source.getHeight() - 1, maxY + padding);
		return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private boolean[] getContentRows(BufferedImage source, int backgroundArgb) {
		boolean[] rows = new boolean[source.getHeight()];
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				if (isIconContentPixel(source.getRGB(x, y), backgroundArgb)) {
					rows[y] = true;
					break;
				}
			}
		}
		return rows;
	}

	private boolean[] getContentColumns(BufferedImage source, int y1, int y2, int backgroundArgb) {
		boolean[] columns = new boolean[source.getWidth()];
		for (int x = 0; x < source.getWidth(); x++) {
			for (int y = y1; y <= y2; y++) {
				if (isIconContentPixel(source.getRGB(x, y), backgroundArgb)) {
					columns[x] = true;
					break;
				}
			}
		}
		return columns;
	}

	private ArrayList<int[]> findContentRanges(boolean[] hasContent, int minGapToSplit) {
		ArrayList<int[]> ranges = new ArrayList<int[]>();
		int start = -1;
		int lastContent = -1;
		int blankRun = 0;
		for (int i = 0; i < hasContent.length; i++) {
			if (hasContent[i]) {
				if (start == -1) {
					start = i;
				}
				lastContent = i;
				blankRun = 0;
			} else if (start != -1) {
				blankRun++;
				if (blankRun >= minGapToSplit) {
					ranges.add(new int[] {start, lastContent});
					start = -1;
					lastContent = -1;
					blankRun = 0;
				}
			}
		}
		if (start != -1) {
			ranges.add(new int[] {start, lastContent});
		}
		return ranges;
	}

	int getSheetBackgroundArgb(BufferedImage source) {
		return source.getRGB(0, 0);
	}

	private boolean isIconContentPixel(int argb, int backgroundArgb) {
		int alpha = argb >>> 24;
		if (alpha < PRAYER_ICON_ALPHA_THRESHOLD) {
			return false;
		}
		int backgroundAlpha = backgroundArgb >>> 24;
		if (backgroundAlpha >= 64 && Math.abs(alpha - backgroundAlpha) <= 8) {
			int red = argb >> 16 & 0xFF;
			int green = argb >> 8 & 0xFF;
			int blue = argb & 0xFF;
			int backgroundRed = backgroundArgb >> 16 & 0xFF;
			int backgroundGreen = backgroundArgb >> 8 & 0xFF;
			int backgroundBlue = backgroundArgb & 0xFF;
			if (Math.abs(red - backgroundRed) <= 8
				&& Math.abs(green - backgroundGreen) <= 8
				&& Math.abs(blue - backgroundBlue) <= 8) {
				return false;
			}
		}
		return true;
	}

	Sprite createExternalIconSprite(BufferedImage source, int maxTargetSize, int backgroundArgb) {
		BufferedImage prepared = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int argb = source.getRGB(x, y);
				if (isIconContentPixel(argb, backgroundArgb)) {
					prepared.setRGB(x, y, argb);
				}
			}
		}
		BufferedImage cropped = cropVisibleImage(prepared);
		if (cropped == null) {
			return null;
		}
		BufferedImage scaled = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = createNearestNeighborGraphics(scaled);
		int targetWidth = maxTargetSize;
		int targetHeight = Math.max(1, (cropped.getHeight() * targetWidth) / Math.max(1, cropped.getWidth()));
		if (targetHeight > maxTargetSize) {
			targetHeight = maxTargetSize;
			targetWidth = Math.max(1, (cropped.getWidth() * targetHeight) / Math.max(1, cropped.getHeight()));
		}
		int drawX = (64 - targetWidth) / 2;
		int drawY = (64 - targetHeight) / 2;
		graphics.drawImage(cropped, drawX, drawY, targetWidth, targetHeight, null);
		graphics.dispose();

		int[] pixels = new int[64 * 64];
		scaled.getRGB(0, 0, 64, 64, pixels, 0, 64);
		normalizePixels(pixels, PRAYER_ICON_ALPHA_THRESHOLD);
		return createSprite(pixels, 64, 64);
	}

	int loadAnimationDirectoryFrames(File sourceFolder, Sprite[] targetFrames,
			int maxTargetSize, int sheetFrameCount) {
		File[] frameFiles = getAnimationFrameFiles(sourceFolder);
		if (frameFiles.length == 0) {
			return 0;
		}
		if (sheetFrameCount > 1 && frameFiles.length == 1) {
			return loadExternalAnimationSheetFrames(
				frameFiles[0], targetFrames, maxTargetSize, sheetFrameCount);
		}
		Arrays.sort(frameFiles, new Comparator<File>() {
			@Override
			public int compare(File left, File right) {
				int leftNumber = getFirstNumber(left.getName());
				int rightNumber = getFirstNumber(right.getName());
				if (leftNumber != rightNumber) {
					return leftNumber - rightNumber;
				}
				return left.getName().compareTo(right.getName());
			}
		});

		int sourceMaxSize = getLargestAnimationFrameSize(frameFiles);
		int loadedFrames = 0;
		for (File frameFile : frameFiles) {
			if (loadedFrames >= targetFrames.length) {
				break;
			}
			Sprite sprite = loadExternalAnimationFrame(frameFile, maxTargetSize, sourceMaxSize);
			if (sprite != null) {
				targetFrames[loadedFrames++] = sprite;
			}
		}
		return loadedFrames;
	}

	int appendExternalAnimationGridSheetFrames(File sourceFile, Sprite[] targetFrames, int maxTargetSize,
			int columns, int rows, int frameCount, int loadedFrames) {
		return appendExternalAnimationGridSheetFrames(sourceFile, targetFrames, maxTargetSize,
			columns, rows, 0, frameCount, loadedFrames);
	}

	int appendExternalAnimationGridSheetFrames(File sourceFile, Sprite[] targetFrames, int maxTargetSize,
			int columns, int rows, int firstFrameIndex, int frameCount, int loadedFrames) {
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null || columns <= 0 || rows <= 0 || firstFrameIndex < 0 || frameCount <= 0
				|| firstFrameIndex + frameCount > columns * rows) {
				return loadedFrames;
			}
			int frameWidth = source.getWidth() / columns;
			int frameHeight = source.getHeight() / rows;
			if (frameWidth <= 0 || frameHeight <= 0 || frameWidth * columns != source.getWidth()
				|| frameHeight * rows != source.getHeight()) {
				return loadedFrames;
			}
			int sourceMaxSize = Math.max(frameWidth, frameHeight);
			for (int frameIndex = 0; frameIndex < frameCount && loadedFrames < targetFrames.length; frameIndex++) {
				int sheetFrameIndex = firstFrameIndex + frameIndex;
				BufferedImage frame = source.getSubimage(
					(sheetFrameIndex % columns) * frameWidth,
					(sheetFrameIndex / columns) * frameHeight, frameWidth, frameHeight);
				Sprite sprite = createExternalAnimationSprite(frame, maxTargetSize, sourceMaxSize);
				if (sprite != null) {
					targetFrames[loadedFrames++] = sprite;
				}
			}
			return loadedFrames;
		} catch (IOException failure) {
			System.out.println("Failed to load external animation sheet " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return loadedFrames;
		}
	}

	int appendExternalAnimationNativeGridSheetFrames(File sourceFile, Sprite[] targetFrames,
			int columns, int rows, int firstFrameIndex, int frameCount) {
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null || columns <= 0 || rows <= 0 || firstFrameIndex < 0 || frameCount <= 0
				|| firstFrameIndex + frameCount > columns * rows) {
				return 0;
			}
			int frameWidth = source.getWidth() / columns;
			int frameHeight = source.getHeight() / rows;
			if (frameWidth <= 0 || frameHeight <= 0 || frameWidth * columns != source.getWidth()
				|| frameHeight * rows != source.getHeight()) {
				return 0;
			}
			int loadedFrames = 0;
			for (int frameIndex = 0; frameIndex < frameCount && loadedFrames < targetFrames.length; frameIndex++) {
				int sheetFrameIndex = firstFrameIndex + frameIndex;
				BufferedImage frame = source.getSubimage(
					(sheetFrameIndex % columns) * frameWidth,
					(sheetFrameIndex / columns) * frameHeight, frameWidth, frameHeight);
				int[] pixels = new int[frameWidth * frameHeight];
				frame.getRGB(0, 0, frameWidth, frameHeight, pixels, 0, frameWidth);
				normalizePixels(pixels, 64);
				targetFrames[loadedFrames++] = createSprite(pixels, frameWidth, frameHeight);
			}
			return loadedFrames;
		} catch (IOException failure) {
			System.out.println("Failed to load static projectile sheet " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return 0;
		}
	}

	int loadExternalAnimationSheetFrames(File sourceFile, Sprite[] targetFrames,
			int maxTargetSize, int frameCount) {
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null || frameCount <= 0) {
				return 0;
			}
			int frameWidth = source.getWidth() / frameCount;
			if (frameWidth <= 0 || frameWidth * frameCount > source.getWidth()) {
				return 0;
			}
			int sourceMaxSize = Math.max(frameWidth, source.getHeight());
			int loadedFrames = 0;
			for (int i = 0; i < frameCount && i < targetFrames.length; i++) {
				BufferedImage frame = source.getSubimage(i * frameWidth, 0, frameWidth, source.getHeight());
				Sprite sprite = createExternalAnimationSprite(frame, maxTargetSize, sourceMaxSize);
				if (sprite != null) {
					targetFrames[loadedFrames++] = sprite;
				}
			}
			return loadedFrames;
		} catch (IOException failure) {
			System.out.println("Failed to load external animation sheet " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return 0;
		}
	}

	File[] getAnimationFrameFiles(File sourceFolder) {
		ArrayList<File> frameFiles = new ArrayList<File>();
		collectAnimationFrameFiles(sourceFolder, frameFiles);
		return frameFiles.toArray(new File[frameFiles.size()]);
	}

	private void collectAnimationFrameFiles(File sourceFolder, ArrayList<File> frameFiles) {
		File[] files = sourceFolder.listFiles();
		if (files == null) {
			String resource = getEmbeddedAssetResource(sourceFolder);
			if (resource == null) {
				return;
			}
			String prefix = resource.endsWith("/") ? resource : resource + "/";
			for (String embeddedResource : getEmbeddedAssetResources()) {
				if (embeddedResource.startsWith(prefix)
					&& embeddedResource.toLowerCase(Locale.ENGLISH).endsWith(".png")) {
					frameFiles.add(new File(sourceFolder, embeddedResource.substring(prefix.length())));
				}
			}
			return;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				collectAnimationFrameFiles(file, frameFiles);
			} else if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".png")) {
				frameFiles.add(file);
			}
		}
	}

	private int getLargestAnimationFrameSize(File[] frameFiles) {
		int largest = 1;
		for (File frameFile : frameFiles) {
			try {
				BufferedImage source = readAssetImage(frameFile);
				if (source != null) {
					largest = Math.max(largest, Math.max(source.getWidth(), source.getHeight()));
				}
			} catch (IOException ignored) {
				// Preserve best-effort sizing; individual frame decode reports failures.
			}
		}
		return largest;
	}

	private int getFirstNumber(String value) {
		Matcher matcher = FIRST_NUMBER.matcher(value);
		if (!matcher.find()) {
			return Integer.MAX_VALUE;
		}
		return Integer.parseInt(matcher.group(1));
	}

	private Sprite loadExternalAnimationFrame(File sourceFile, int maxTargetSize, int sourceMaxSize) {
		try {
			BufferedImage source = readAssetImage(sourceFile);
			return source == null ? null : createExternalAnimationSprite(source, maxTargetSize, sourceMaxSize);
		} catch (IOException failure) {
			System.out.println("Failed to load external animation frame " + sourceFile.getPath()
				+ ": " + failure.getMessage());
			return null;
		}
	}

	private Sprite createExternalAnimationSprite(BufferedImage source, int maxTargetSize, int sourceMaxSize) {
		BufferedImage scaled = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = createNearestNeighborGraphics(scaled);
		int boundedTargetSize = Math.max(1, Math.min(64, maxTargetSize));
		int scaleBase = sourceMaxSize > 0 ? sourceMaxSize : Math.max(source.getWidth(), source.getHeight());
		int targetWidth = Math.max(1, (source.getWidth() * boundedTargetSize) / Math.max(1, scaleBase));
		int targetHeight = Math.max(1, (source.getHeight() * boundedTargetSize) / Math.max(1, scaleBase));
		if (targetHeight > boundedTargetSize) {
			targetHeight = boundedTargetSize;
			targetWidth = Math.max(1, (source.getWidth() * targetHeight) / Math.max(1, source.getHeight()));
		}
		if (targetWidth > boundedTargetSize) {
			targetWidth = boundedTargetSize;
			targetHeight = Math.max(1, (source.getHeight() * targetWidth) / Math.max(1, source.getWidth()));
		}
		int drawX = (64 - targetWidth) / 2;
		int drawY = (64 - targetHeight) / 2;
		graphics.drawImage(source, drawX, drawY, targetWidth, targetHeight, null);
		graphics.dispose();

		int[] pixels = new int[64 * 64];
		scaled.getRGB(0, 0, 64, 64, pixels, 0, 64);
		normalizePixels(pixels, 64);
		return createSprite(pixels, 64, 64);
	}

	Sprite loadExternalWorldSprite(File sourceFile, int maxTargetSize) {
		if (!assetFileExists(sourceFile)) {
			return null;
		}
		try {
			BufferedImage source = readAssetImage(sourceFile);
			if (source == null) {
				return null;
			}
			BufferedImage cropped = cropVisibleImage(source);
			if (cropped == null) {
				return null;
			}
			BufferedImage scaled = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = createNearestNeighborGraphics(scaled);
			int targetWidth = maxTargetSize;
			int targetHeight = Math.max(1, (cropped.getHeight() * targetWidth) / Math.max(1, cropped.getWidth()));
			if (targetHeight > maxTargetSize) {
				targetHeight = maxTargetSize;
				targetWidth = Math.max(1, (cropped.getWidth() * targetHeight) / Math.max(1, cropped.getHeight()));
			}
			int drawX = (64 - targetWidth) / 2;
			int drawY = (64 - targetHeight) / 2;
			graphics.drawImage(cropped, drawX, drawY, targetWidth, targetHeight, null);
			graphics.dispose();

			int[] pixels = new int[64 * 64];
			scaled.getRGB(0, 0, 64, 64, pixels, 0, 64);
			normalizePixels(pixels, 128);
			return createSprite(pixels, 64, 64);
		} catch (IOException failure) {
			System.out.println("Failed to load external world sprite: " + failure.getMessage());
			return null;
		}
	}

	BufferedImage cropVisibleImage(BufferedImage source) {
		int minX = source.getWidth();
		int minY = source.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				if (((source.getRGB(x, y) >>> 24) & 0xFF) >= 64) {
					if (x < minX) minX = x;
					if (y < minY) minY = y;
					if (x > maxX) maxX = x;
					if (y > maxY) maxY = y;
				}
			}
		}
		if (maxX < minX || maxY < minY) {
			return null;
		}
		return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	int getExternalSpritePixel(int argb, int alphaThreshold) {
		int alpha = argb >>> 24;
		if (alpha < alphaThreshold) {
			return 0;
		}
		int rgb = argb & 0xFFFFFF;
		return rgb == 0 ? 0x010101 : rgb;
	}

	private void normalizePixels(int[] pixels, int alphaThreshold) {
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = getExternalSpritePixel(pixels[i], alphaThreshold);
		}
	}

	private Graphics2D createNearestNeighborGraphics(BufferedImage target) {
		Graphics2D graphics = target.createGraphics();
		graphics.setRenderingHint(
			RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		return graphics;
	}

	private Sprite createSprite(int[] pixels, int width, int height) {
		Sprite sprite = new Sprite(pixels, width, height);
		sprite.setShift(0, 0);
		sprite.setRequiresShift(false);
		sprite.setSomething(width, height);
		return sprite;
	}
}
