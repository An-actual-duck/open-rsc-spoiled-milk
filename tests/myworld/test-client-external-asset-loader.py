#!/usr/bin/env python3
"""Exercise external client asset discovery, decoding, and frame parity."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
OWNER = ROOT / "Client_Base/src/orsc/ClientExternalAssetLoader.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
REMASTERED = ROOT / "Client_Base/src/orsc/remastered/RemasteredSpriteResolver.java"


FIXTURE = r"""
package orsc;

import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class AssetAnchorTemplate {
}

public final class ClientExternalAssetLoaderFixture {
	private static final int RED = 0xCC2200;
	private static final int GREEN = 0x22CC00;
	private static final int BLUE = 0x0033DD;
	private static final int YELLOW = 0xDDCC00;
	private static final int CYAN = 0x00CCDD;
	private static final int MAGENTA = 0xCC00DD;

	private ClientExternalAssetLoaderFixture() {
	}

	public static void main(String[] args) throws Exception {
		Path root = new File(args[0]).toPath();
		String scenario = args[1];
		if ("paths".equals(scenario)) {
			pathsAndPrecedence(root);
		} else if ("pixels".equals(scenario)) {
			pixelsItemsEquipmentAndIcons(root);
		} else if ("frames".equals(scenario)) {
			frameOrderAndSlicing(root);
		} else if ("npc-directions".equals(scenario)) {
			npcDirectionSheetAndMirroring(root);
		} else if ("foundry-asset".equals(scenario)) {
			actualFoundryDragonAsset(root);
		} else if ("king-black-dragon-asset".equals(scenario)) {
			actualKingBlackDragonAsset(root);
		} else if ("jar".equals(scenario)) {
			packagedJarInventoryAndReads(root);
		} else if ("diagnostics".equals(scenario)) {
			packagedJarDiagnostics(root);
		} else {
			throw new AssertionError("unknown scenario " + scenario);
		}
	}

	private static void pathsAndPrecedence(Path root) throws Exception {
		Path working = root.resolve("workspace/client");
		Files.createDirectories(working);
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(
			working, ClientExternalAssetLoaderFixture.class);
		Path[] bases = loader.getAssetCandidateBases("dev/path", "output/path");
		assertEquals(6, bases.length, "candidate count");
		assertPath(working.resolve("dev/path"), bases[0], "working candidate");
		assertPath(working.resolve("../dev/path"), bases[1], "parent candidate");
		assertPath(working.resolve("../Core-Framework/dev/path"), bases[2], "sibling candidate");
		assertPath(working.resolve("output/path"), bases[3], "second root working candidate");

		Path sibling = working.resolve("../Core-Framework/dev/path/second.png").normalize();
		Path legacy = working.resolve("output/path/first.png").normalize();
		writeImage(sibling, solid(1, 1, opaque(BLUE)));
		writeImage(legacy, solid(1, 1, opaque(RED)));
		File selected = loader.findFirstFile(
			new String[] {"dev/path", "output/path"}, "first.png", "second.png");
		assertPath(sibling, selected.toPath(), "relative-root precedence before later legacy root");

		Path primary = working.resolve("dev/path/first.png");
		writeImage(primary, solid(1, 1, opaque(GREEN)));
		selected = loader.findFirstFile(
			new String[] {"dev/path", "output/path"}, "first.png", "second.png");
		assertPath(primary, selected.toPath(), "working path and spelling precedence");

		File missing = loader.findFirstFile(
			new String[] {"missing/dev", "missing/output"}, "preferred.png", "fallback.png");
		assertPath(working.resolve("missing/dev/preferred.png"), missing.toPath(), "missing fallback identity");

		Path directory = working.resolve("../Core-Framework/dev/equipment/hood/numbered").normalize();
		Files.createDirectories(directory);
		File selectedDirectory = loader.findFirstDirectory(
			new String[] {"dev/equipment", "output/equipment"}, "hood", "numbered");
		assertPath(directory, selectedDirectory.toPath(), "nested directory precedence");

		File mapped = working.resolve("dev/myworld/assets/icons/test.png").toFile();
		assertEquals("myworld-assets/icons/test.png", loader.getEmbeddedAssetResource(mapped),
			"development resource mapping");
		assertEquals(null, loader.getEmbeddedAssetResource(working.resolve("output/icons/test.png").toFile()),
			"legacy output is not an embedded resource alias");
		assertTrue(!loader.assetFileExists(missing), "missing file stays missing");
		assertTrue(!loader.assetDirectoryExists(missing.getParentFile()), "missing directory stays missing");
		assertEquals(null, loader.readAssetImage(missing), "missing read falls back to null");
	}

	private static void pixelsItemsEquipmentAndIcons(Path root) throws Exception {
		Path working = root.resolve("pixel-work");
		Files.createDirectories(working);
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(
			working, ClientExternalAssetLoaderFixture.class);

		BufferedImage itemImage = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 1; y <= 2; y++) {
			itemImage.setRGB(2, y, 0xFF000000);
			itemImage.setRGB(3, y, opaque(RED));
		}
		Path itemPath = working.resolve(
			"dev/myworld/assets/sprites/items/inventory-ground/fixture.png");
		writeImage(itemPath, itemImage);
		ItemDef item = new ItemDef("Fixture", "Fixture", 0, "external-png:fixture@14x10", 0);
		Sprite first = loader.getExternalItemSprite(item);
		Sprite second = loader.getExternalItemSprite(item);
		assertTrue(first != null && first == second, "item cache reuses sprite identity");
		assertSpriteMetadata(first, 48, 32, "item");
		assertBounds(first, 19, 11, 10, 10, "item crop and requested dimensions");
		assertEquals(50, count(first, 0x010101), "opaque black replacement");
		assertEquals(50, count(first, RED), "item nearest-neighbor red pixels");

		ItemDef invalidSize = new ItemDef("Fixture", "Fixture", 0,
			"external-png:fixture@badxsize", 1);
		Sprite defaultSized = loader.getExternalItemSprite(invalidSize);
		assertBounds(defaultSized, 9, 1, 30, 30, "malformed dimensions use legacy defaults");
		assertEquals(null, loader.getExternalItemSprite(
			new ItemDef("Bad", "Bad", 0, "external-png:../fixture", 2)), "path separators rejected");
		assertEquals(null, loader.getExternalItemSprite(
			new ItemDef("Canonical", "Canonical", 0, "items:1", 3)), "canonical sprite ignored");

		BufferedImage threshold = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		threshold.setRGB(0, 0, 0x3F112233);
		threshold.setRGB(1, 1, 0x40112233);
		BufferedImage cropped = loader.cropVisibleImage(threshold);
		assertEquals(1, cropped.getWidth(), "crop alpha threshold width");
		assertEquals(1, cropped.getHeight(), "crop alpha threshold height");
		assertEquals(0, loader.getExternalSpritePixel(0x3FFFFFFF, 64), "transparent normalization");
		assertEquals(0x010101, loader.getExternalSpritePixel(0xFF000000, 64), "black normalization");

		BufferedImage equipment = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		equipment.setRGB(0, 0, 0x3FFFFFFF);
		equipment.setRGB(1, 0, 0x40000000);
		equipment.setRGB(0, 1, opaque(BLUE));
		equipment.setRGB(1, 1, opaque(RED));
		Path equipmentPath = working.resolve("equipment.png");
		writeImage(equipmentPath, equipment);
		Frame frame = loader.loadExternalEquipmentFrame(equipmentPath.toFile(), 17, 29);
		assertEquals(2, frame.getWidth(), "equipment width");
		assertEquals(2, frame.getHeight(), "equipment height");
		assertEquals(17, frame.getOffsetX(), "equipment x offset");
		assertEquals(29, frame.getOffsetY(), "equipment y offset");
		assertEquals(64, frame.getBoundWidth(), "equipment bound width");
		assertEquals(102, frame.getBoundHeight(), "equipment bound height");
		assertArray(new int[] {0, 0x010101, BLUE, RED}, frame.getPixels(), "equipment pixels");

		BufferedImage world = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
		world.setRGB(0, 0, 0x7FFF0000);
		world.setRGB(1, 0, opaque(BLUE));
		Path worldPath = working.resolve("world.png");
		writeImage(worldPath, world);
		Sprite worldSprite = loader.loadExternalWorldSprite(worldPath.toFile(), 10);
		assertSpriteMetadata(worldSprite, 64, 64, "world");
		assertEquals(25, count(worldSprite, BLUE), "world nearest-neighbor pixels");
		assertEquals(0, count(worldSprite, RED), "world alpha 127 removed at output threshold");

		BufferedImage icon = solid(12, 10, 0xFF112233);
		fill(icon, 4, 3, 4, 4, opaque(GREEN));
		Sprite iconSprite = loader.createExternalIconSprite(icon, 8, icon.getRGB(0, 0));
		assertSpriteMetadata(iconSprite, 64, 64, "icon");
		assertEquals(64, count(iconSprite, GREEN), "icon background removal and nearest scale");
		assertEquals(0, count(iconSprite, 0x112233), "sheet background omitted");

		BufferedImage prayerSheet = solid(300, 200, 0xFF181818);
		int[] colors = new int[] {RED, GREEN, BLUE, YELLOW, CYAN};
		fill(prayerSheet, 20, 20, 30, 30, opaque(colors[0]));
		fill(prayerSheet, 120, 20, 30, 30, opaque(colors[1]));
		fill(prayerSheet, 220, 20, 30, 30, opaque(colors[2]));
		fill(prayerSheet, 70, 130, 30, 30, opaque(colors[3]));
		fill(prayerSheet, 190, 130, 30, 30, opaque(colors[4]));
		Path prayerPath = working.resolve("prayer.png");
		writeImage(prayerPath, prayerSheet);
		Sprite[] prayers = loader.loadExternalPrayerIconSheet(prayerPath.toFile());
		assertEquals(5, prayers.length, "prayer sheet icon count");
		for (int i = 0; i < colors.length; i++) {
			assertEquals(colors[i], onlyNonzeroColor(prayers[i]), "prayer frame order " + i);
		}
		Sprite prayer = loader.loadExternalPrayerIconFile(prayerPath.toFile());
		assertTrue(prayer != null, "single prayer icon decoder");

		Path emptyPath = working.resolve("empty.png");
		writeImage(emptyPath, new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));
		assertEquals(null, loader.loadExternalItemSprite(emptyPath.toFile()), "transparent item fallback");
		Path corrupt = working.resolve("corrupt.png");
		Files.write(corrupt, new byte[] {1, 2, 3, 4});
		assertEquals(null, loader.readAssetImage(corrupt.toFile()), "corrupt image read fallback");
		assertEquals(null, loader.loadExternalWorldSprite(corrupt.toFile(), 16), "corrupt world fallback");
	}

	private static void frameOrderAndSlicing(Path root) throws Exception {
		Path working = root.resolve("frame-work");
		Path frames = working.resolve("dev/myworld/assets/legacy animation folder/Projectiles/order");
		writeImage(frames.resolve("frame10.png"), solid(4, 4, opaque(BLUE)));
		writeImage(frames.resolve("nested/frame2.png"), solid(2, 2, opaque(RED)));
		writeImage(frames.resolve("alpha.png"), solid(3, 3, opaque(GREEN)));
		Files.write(frames.resolve("ignored.txt"), new byte[] {1});
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(
			working, ClientExternalAssetLoaderFixture.class);
		Sprite[] ordered = new Sprite[4];
		int loaded = loader.loadAnimationDirectoryFrames(frames.toFile(), ordered, 32, 0);
		assertEquals(3, loaded, "nested animation frame count");
		assertEquals(RED, onlyNonzeroColor(ordered[0]), "numeric frame 2 first");
		assertEquals(BLUE, onlyNonzeroColor(ordered[1]), "numeric frame 10 second");
		assertEquals(GREEN, onlyNonzeroColor(ordered[2]), "unnumbered frame last");
		assertBounds(ordered[0], 24, 24, 16, 16, "shared source maximum sizing");
		assertBounds(ordered[1], 16, 16, 32, 32, "largest source sizing");
		assertBounds(ordered[2], 20, 20, 24, 24, "intermediate source sizing");

		int[] colors = new int[] {RED, GREEN, BLUE, YELLOW, CYAN, MAGENTA};
		BufferedImage grid = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < colors.length; i++) {
			fill(grid, (i % 3) * 2, (i / 3) * 2, 2, 2, opaque(colors[i]));
		}
		Path gridPath = working.resolve("grid.png");
		writeImage(gridPath, grid);
		Sprite[] gridFrames = new Sprite[5];
		gridFrames[0] = new Sprite(new int[] {MAGENTA}, 1, 1);
		loaded = loader.appendExternalAnimationGridSheetFrames(
			gridPath.toFile(), gridFrames, 16, 3, 2, 2, 3, 1);
		assertEquals(4, loaded, "grid append count");
		assertEquals(MAGENTA, gridFrames[0].getPixel(0), "grid preserves prior frame");
		assertEquals(BLUE, onlyNonzeroColor(gridFrames[1]), "grid first selected frame");
		assertEquals(YELLOW, onlyNonzeroColor(gridFrames[2]), "grid row-major order");
		assertEquals(CYAN, onlyNonzeroColor(gridFrames[3]), "grid final selected frame");
		assertEquals(1, loader.appendExternalAnimationGridSheetFrames(
			gridPath.toFile(), new Sprite[2], 16, 4, 2, 1, 1), "invalid grid leaves append index");

		Sprite[] nativeFrames = new Sprite[3];
		loaded = loader.appendExternalAnimationNativeGridSheetFrames(
			gridPath.toFile(), nativeFrames, 3, 2, 1, 3);
		assertEquals(3, loaded, "native grid frame count");
		assertSpriteMetadata(nativeFrames[0], 2, 2, "native frame");
		assertEquals(GREEN, onlyNonzeroColor(nativeFrames[0]), "native grid first frame");
		assertEquals(BLUE, onlyNonzeroColor(nativeFrames[1]), "native grid second frame");
		assertEquals(YELLOW, onlyNonzeroColor(nativeFrames[2]), "native grid third frame");

		BufferedImage strip = new BufferedImage(6, 2, BufferedImage.TYPE_INT_ARGB);
		fill(strip, 0, 0, 2, 2, opaque(RED));
		fill(strip, 2, 0, 2, 2, opaque(GREEN));
		fill(strip, 4, 0, 2, 2, opaque(BLUE));
		Path stripPath = working.resolve("strip.png");
		writeImage(stripPath, strip);
		Sprite[] stripFrames = new Sprite[3];
		loaded = loader.loadExternalAnimationSheetFrames(stripPath.toFile(), stripFrames, 12, 3);
		assertEquals(3, loaded, "strip frame count");
		assertEquals(RED, onlyNonzeroColor(stripFrames[0]), "strip first frame");
		assertEquals(GREEN, onlyNonzeroColor(stripFrames[1]), "strip second frame");
		assertEquals(BLUE, onlyNonzeroColor(stripFrames[2]), "strip third frame");

		Path singleStripFolder = working.resolve("single-strip");
		writeImage(singleStripFolder.resolve("sheet.png"), strip);
		Sprite[] automaticStrip = new Sprite[3];
		loaded = loader.loadAnimationDirectoryFrames(singleStripFolder.toFile(), automaticStrip, 12, 3);
		assertEquals(3, loaded, "single-file configured strip count");
		assertEquals(BLUE, onlyNonzeroColor(automaticStrip[2]), "single-file strip ordering");
	}

	private static void npcDirectionSheetAndMirroring(Path root) throws Exception {
		Path working = root.resolve("npc-direction-work");
		Files.createDirectories(working);
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(
			working, ClientExternalAssetLoaderFixture.class);
		int frameWidth = 2;
		int frameHeight = 3;
		BufferedImage sheet = new BufferedImage(
			6 * frameWidth + 1, 3 * frameHeight, BufferedImage.TYPE_INT_ARGB);
		int[] expected = new int[18];
		for (int direction = 0; direction < 6; direction++) {
			for (int frame = 0; frame < 3; frame++) {
				int color = ((direction + 1) << 16) | ((frame + 1) << 8) | 0x55;
				expected[direction * 3 + frame] = color;
				fill(sheet, direction * frameWidth, frame * frameHeight,
					frameWidth, frameHeight, opaque(color));
			}
		}
		Path sheetPath = working.resolve("foundry-dragon.png");
		writeImage(sheetPath, sheet);
		Entry entry = loader.loadExternalNpcDirectionSheet(
			sheetPath.toFile(), "foundrydragon", 6, 3);
		assertTrue(entry != null, "native NPC direction sheet loads");
		assertEquals(Entry.TYPE.NPC, entry.getType(), "NPC sprite entry type");
		assertEquals(18, entry.getFrames().length, "NPC direction frame count");
		for (int frame = 0; frame < expected.length; frame++) {
			assertEquals(expected[frame], onlyNonzeroColor(entry.getFrames()[frame].getSprite()),
				"column-major NPC frame " + frame);
			int expectedWidth = frame / 3 == 5 ? frameWidth + 1 : frameWidth;
			assertSpriteMetadata(entry.getFrames()[frame].getSprite(), expectedWidth, frameHeight,
				"native NPC frame " + frame);
		}

		int[] expectedSources = new int[] {0, 1, 2, 3, 4, 3, 2, 1};
		for (int direction = 0; direction < expectedSources.length; direction++) {
			assertEquals(expectedSources[direction],
				NpcDirectionalAnimationMapping.sourceDirection(direction),
				"camera-relative source direction " + direction);
			assertEquals(direction >= 5,
				NpcDirectionalAnimationMapping.mirrors(direction),
				"camera-relative mirror flag " + direction);
			assertEquals(expectedSources[direction] * 3 + 2,
				NpcDirectionalAnimationMapping.frameOffset(direction, 2),
				"camera-relative frame offset " + direction);
		}
		assertEquals(15, NpcDirectionalAnimationMapping.combatFrameOffset(0),
			"combat column begins after five direction columns");
		assertEquals(17, NpcDirectionalAnimationMapping.combatFrameOffset(2),
			"combat column contains all three fire-breath frames");

		sheet.setRGB(sheet.getWidth() - 1, 0, opaque(RED));
		writeImage(sheetPath, sheet);
		Entry remainderEntry = loader.loadExternalNpcDirectionSheet(
			sheetPath.toFile(), "foundrydragon", 6, 3);
		assertTrue(remainderEntry != null, "last-column remainder belongs to combat frames");
		assertEquals(1, count(remainderEntry.getFrames()[15].getSprite(), RED),
			"combat frame preserves opaque final-column artwork");
	}

	private static void actualFoundryDragonAsset(Path root) throws Exception {
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(
			root, ClientExternalAssetLoaderFixture.class);
		File source = root.resolve(
			"dev/myworld/assets/sprites/npcs/foundry-dragon/foundry-dragon-sprite-sheet.png")
			.toFile();
		int[] widths = {228, 203, 263, 215, 218, 338};
		Entry entry = loader.loadExternalNpcDirectionSheet(source, "foundrydragon", widths, 3);
		assertTrue(entry != null, "tracked Foundry Dragon sheet decodes");
		assertEquals(18, entry.getFrames().length, "tracked Foundry Dragon frame count");
		for (int frame = 0; frame < entry.getFrames().length; frame++) {
			int expectedWidth = widths[frame / 3];
			assertSpriteMetadata(entry.getFrames()[frame].getSprite(), expectedWidth, 163,
				"tracked Foundry Dragon native frame " + frame);
			assertTrue(nonzeroPixels(entry.getFrames()[frame].getSprite()) > 0,
				"tracked Foundry Dragon frame contains artwork " + frame);
		}
		assertEquals(0, count(entry.getFrames()[0].getSprite(), 0x6bee36),
			"tracked Foundry Dragon sheet must not include green guide marks");
	}

	private static void actualKingBlackDragonAsset(Path root) throws Exception {
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(
			root, ClientExternalAssetLoaderFixture.class);
		File source = root.resolve(
			"dev/myworld/assets/sprites/npcs/king-black-dragon/king-black-dragon-sprite-sheet.png")
			.toFile();
		int[] widths = {230, 209, 263, 222, 208, 333};
		Entry entry = loader.loadExternalNpcDirectionSheet(source, "kingblackdragon", widths, 3, 0x6BEE36);
		assertTrue(entry != null, "tracked King Black Dragon sheet decodes");
		assertEquals(18, entry.getFrames().length, "tracked King Black Dragon frame count");
		for (int frame = 0; frame < entry.getFrames().length; frame++) {
			int expectedWidth = widths[frame / 3];
			assertSpriteMetadata(entry.getFrames()[frame].getSprite(), expectedWidth, 163,
				"tracked King Black Dragon native frame " + frame);
			assertTrue(nonzeroPixels(entry.getFrames()[frame].getSprite()) > 0,
				"tracked King Black Dragon frame contains artwork " + frame);
			assertEquals(0, count(entry.getFrames()[frame].getSprite(), 0x6BEE36),
				"tracked King Black Dragon guide pixels must be transparent " + frame);
		}
	}

	private static void packagedJarInventoryAndReads(Path root) throws Exception {
		Path jar = root.resolve("assets.jar");
		createAssetJar(jar);
		TrackingUrlClassLoader classLoader = new TrackingUrlClassLoader(
			new URL[] {jar.toUri().toURL()});
		Class<?> anchor = Class.forName("orsc.AssetAnchorTemplate", true, classLoader);
		Path working = root.resolve("packaged-working");
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(working, anchor);

		File icon = working.resolve("dev/myworld/assets/icons/icon.png").toFile();
		assertTrue(loader.assetFileExists(icon), "packaged file exists");
		BufferedImage decoded = loader.readAssetImage(icon);
		assertEquals(2, decoded.getWidth(), "packaged image width");
		assertTrue(classLoader.streamClosed, "packaged image input stream closed");

		File directory = working.resolve("dev/myworld/assets/animations/fixture").toFile();
		assertTrue(loader.assetDirectoryExists(directory), "packaged directory exists");
		Set<String> resources = loader.getEmbeddedAssetResources();
		assertTrue(resources == loader.getEmbeddedAssetResources(), "archive inventory cached");
		assertEquals(3, resources.size(), "PNG-only archive inventory");
		assertTrue(resources.contains("myworld-assets/animations/fixture/nested/frame2.png"),
			"nested packaged frame inventoried");
		assertTrue(!resources.contains("myworld-assets/ignored.txt"), "non-PNG excluded");

		File[] files = loader.getAnimationFrameFiles(directory);
		Set<String> names = new HashSet<String>();
		for (File file : files) {
			names.add(file.getPath().replace('\\', '/'));
		}
		assertEquals(2, files.length, "packaged nested frame enumeration");
		assertTrue(containsSuffix(names, "/nested/frame2.png"), "packaged nested relative path");
		assertTrue(containsSuffix(names, "/frame10.png"), "packaged root relative path");
		Sprite[] sprites = new Sprite[2];
		assertEquals(2, loader.loadAnimationDirectoryFrames(directory, sprites, 16, 0),
			"packaged directory decode");
		assertEquals(RED, onlyNonzeroColor(sprites[0]), "packaged numeric order first");
		assertEquals(BLUE, onlyNonzeroColor(sprites[1]), "packaged numeric order second");

		classLoader.close();
		Path moved = root.resolve("assets-moved.jar");
		Files.move(jar, moved, StandardCopyOption.REPLACE_EXISTING);
		Files.move(moved, jar, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void packagedJarDiagnostics(Path root) throws Exception {
		Path jar = root.resolve("broken-assets.jar");
		createAssetJar(jar);
		URLClassLoader classLoader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
		Class<?> anchor = Class.forName("orsc.AssetAnchorTemplate", true, classLoader);
		classLoader.close();
		Files.write(jar, new byte[] {1, 2, 3, 4});
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream original = System.out;
		try {
			System.setOut(new PrintStream(output));
			ClientExternalAssetLoader loader = new ClientExternalAssetLoader(root, anchor);
			assertEquals(0, loader.getEmbeddedAssetResources().size(), "broken archive empty fallback");
			assertEquals(0, loader.getEmbeddedAssetResources().size(), "broken archive cache reuse");
		} finally {
			System.setOut(original);
		}
		String diagnostic = output.toString("UTF-8");
		assertTrue(diagnostic.contains("Failed to inventory packaged external assets:"),
			"broken archive diagnostic");
		assertEquals(1, occurrences(diagnostic, "Failed to inventory packaged external assets:"),
			"broken archive diagnostic is bounded by cache");
	}

	private static void createAssetJar(Path jar) throws Exception {
		Files.createDirectories(jar.getParent());
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
			addEntry(output, "orsc/AssetAnchorTemplate.class", classBytes());
			addEntry(output, "myworld-assets/icons/icon.png", png(solid(2, 2, opaque(GREEN))));
			addEntry(output, "myworld-assets/animations/fixture/nested/frame2.png",
				png(solid(2, 2, opaque(RED))));
			addEntry(output, "myworld-assets/animations/fixture/frame10.png",
				png(solid(2, 2, opaque(BLUE))));
			addEntry(output, "myworld-assets/ignored.txt", new byte[] {1});
		}
	}

	private static byte[] classBytes() throws IOException {
		try (InputStream input = ClientExternalAssetLoaderFixture.class.getClassLoader()
				.getResourceAsStream("orsc/AssetAnchorTemplate.class")) {
			if (input == null) throw new AssertionError("anchor class bytes missing");
			return readAll(input);
		}
	}

	private static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

	private static void addEntry(JarOutputStream output, String name, byte[] contents) throws IOException {
		output.putNextEntry(new JarEntry(name));
		output.write(contents);
		output.closeEntry();
	}

	private static byte[] readAll(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = input.read(buffer)) != -1) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static BufferedImage solid(int width, int height, int argb) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		fill(image, 0, 0, width, height, argb);
		return image;
	}

	private static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
		for (int yy = y; yy < y + height; yy++) {
			for (int xx = x; xx < x + width; xx++) {
				image.setRGB(xx, yy, argb);
			}
		}
	}

	private static int opaque(int rgb) {
		return 0xFF000000 | rgb;
	}

	private static void writeImage(Path path, BufferedImage image) throws IOException {
		Files.createDirectories(path.getParent());
		if (!ImageIO.write(image, "png", path.toFile())) {
			throw new AssertionError("unable to write fixture PNG " + path);
		}
	}

	private static int count(Sprite sprite, int color) {
		int count = 0;
		for (int pixel : sprite.getPixels()) {
			if (pixel == color) count++;
		}
		return count;
	}

	private static int nonzeroPixels(Sprite sprite) {
		int count = 0;
		for (int pixel : sprite.getPixels()) {
			if (pixel != 0) count++;
		}
		return count;
	}

	private static int onlyNonzeroColor(Sprite sprite) {
		int color = 0;
		for (int pixel : sprite.getPixels()) {
			if (pixel == 0) continue;
			if (color == 0) color = pixel;
			else if (pixel != color) {
				throw new AssertionError("sprite has multiple nonzero colors: " + color + " and " + pixel);
			}
		}
		if (color == 0) throw new AssertionError("sprite has no visible color");
		return color;
	}

	private static void assertBounds(Sprite sprite, int expectedX, int expectedY,
			int expectedWidth, int expectedHeight, String label) {
		int minX = sprite.getWidth();
		int minY = sprite.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < sprite.getHeight(); y++) {
			for (int x = 0; x < sprite.getWidth(); x++) {
				if (sprite.getPixel(y * sprite.getWidth() + x) != 0) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		assertEquals(expectedX, minX, label + " x");
		assertEquals(expectedY, minY, label + " y");
		assertEquals(expectedWidth, maxX - minX + 1, label + " width");
		assertEquals(expectedHeight, maxY - minY + 1, label + " height");
	}

	private static void assertSpriteMetadata(Sprite sprite, int width, int height, String label) {
		assertTrue(sprite != null, label + " sprite exists");
		assertEquals(width, sprite.getWidth(), label + " width");
		assertEquals(height, sprite.getHeight(), label + " height");
		assertEquals(0, sprite.getXShift(), label + " x shift");
		assertEquals(0, sprite.getYShift(), label + " y shift");
		assertTrue(!sprite.requiresShift(), label + " shift flag");
		assertEquals(width, sprite.getSomething1(), label + " bound width");
		assertEquals(height, sprite.getSomething2(), label + " bound height");
	}

	private static boolean containsSuffix(Set<String> values, String suffix) {
		for (String value : values) {
			if (value.endsWith(suffix)) return true;
		}
		return false;
	}

	private static int occurrences(String value, String fragment) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(fragment, index)) >= 0) {
			count++;
			index += fragment.length();
		}
		return count;
	}

	private static void assertPath(Path expected, Path actual, String label) {
		assertEquals(expected.normalize().toAbsolutePath(), actual.normalize().toAbsolutePath(), label);
	}

	private static void assertArray(int[] expected, int[] actual, String label) {
		if (!Arrays.equals(expected, actual)) {
			throw new AssertionError(label + ": expected=" + Arrays.toString(expected)
				+ " actual=" + Arrays.toString(actual));
		}
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
		}
	}

	private static final class TrackingUrlClassLoader extends URLClassLoader {
		boolean streamClosed;

		TrackingUrlClassLoader(URL[] urls) {
			super(urls, null);
		}

		@Override
		public InputStream getResourceAsStream(String name) {
			InputStream input = super.getResourceAsStream(name);
			if (input == null) return null;
			return new FilterInputStream(input) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					} finally {
						streamClosed = true;
					}
				}
			};
		}
	}
}
"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def verify_source_ownership() -> None:
    owner = OWNER.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    remastered = REMASTERED.read_text(encoding="utf-8")

    for contract in (
        "legacy-output, and packaged client locations",
        'DEVELOPMENT_ASSET_ROOT = "dev/myworld/assets/"',
        'EMBEDDED_ASSET_ROOT = "myworld-assets/"',
        'resolve("../Core-Framework")',
        "getAssetCandidateBases(",
        "getEmbeddedAssetResources(",
        "readAssetImage(",
        "itemSpriteCache",
        "loadExternalEquipmentFrame(",
        "loadExternalNpcDirectionSheet(",
        "loadExternalPrayerIconSheet(",
        "loadAnimationDirectoryFrames(",
        "appendExternalAnimationGridSheetFrames(",
        "appendExternalAnimationNativeGridSheetFrames(",
    ):
        require(contract in owner, f"external asset owner is missing {contract}")

    for retired in (
        "externalItemSpriteCache",
        "embeddedAssetResources",
        "private Path[] getAssetCandidateBases",
        "private String getEmbeddedAssetResource",
        "private Set<String> getEmbeddedAssetResources",
        "private BufferedImage readAssetImage",
        "private Sprite loadExternalItemSprite",
        "private orsc.graphics.two.SpriteArchive.Frame loadExternalEquipmentFrame",
        "private Sprite[] loadExternalPrayerIconSheet",
        "private BufferedImage cropVisibleImage",
        "private Sprite loadExternalWorldSprite",
    ):
        require(retired not in mudclient, f"mudclient still owns {retired}")

    for coordinator in (
        "loadExternalEquipmentSprites()",
        "loadConfiguredAnimationSheetFrames(",
        "getCombatEffectAssetName(",
        "getSpellIconAssetName(",
        "getPrayerIconAssetName(",
        "createProceduralAltarWorldSprite(",
        "this.combatEffectSprites",
        "this.projectileEffectSprites",
        "this.spellIconSprites",
    ):
        require(coordinator in mudclient, f"mudclient lost content coordination {coordinator}")

    require("ClientExternalAssetLoader" not in remastered,
            "canonical remastered resolver was coupled to the external loader")
    require("RemasteredSpriteResolver" not in owner,
            "external loader absorbed the remastered fallback contract")


def main() -> None:
    require(CLIENT_JAR.is_file(), f"build the client first: {CLIENT_JAR}")
    verify_source_ownership()
    with tempfile.TemporaryDirectory(prefix="client-external-assets-") as directory:
        temp = Path(directory)
        source = temp / "orsc/ClientExternalAssetLoaderFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-source",
                "1.8",
                "-target",
                "1.8",
                "-cp",
                str(CLIENT_JAR),
                "-d",
                str(temp),
                str(source),
            ],
            cwd=ROOT,
            check=True,
        )
        for scenario in ("paths", "pixels", "frames", "npc-directions", "jar", "diagnostics"):
            scenario_root = temp / scenario
            scenario_root.mkdir()
            subprocess.run(
                [
                    "java",
                    "-Djava.awt.headless=true",
                    "-cp",
                    f"{temp}:{CLIENT_JAR}",
                    "orsc.ClientExternalAssetLoaderFixture",
                    str(scenario_root),
                    scenario,
                ],
                cwd=ROOT,
                check=True,
            )
        subprocess.run(
            [
                "java",
                "-Djava.awt.headless=true",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.ClientExternalAssetLoaderFixture",
                str(ROOT),
                "foundry-asset",
            ],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            [
                "java",
                "-Djava.awt.headless=true",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.ClientExternalAssetLoaderFixture",
                str(ROOT),
                "king-black-dragon-asset",
            ],
            cwd=ROOT,
            check=True,
        )

    print("PASS: external asset discovery, decode, cache, frame, and packaged-JAR parity are stable")


if __name__ == "__main__":
    main()
