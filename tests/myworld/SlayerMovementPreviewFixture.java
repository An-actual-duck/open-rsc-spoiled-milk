package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.SlayerMovementPreview;
import com.openrsc.client.entityhandling.defs.NPCDef;
import orsc.graphics.two.SpriteArchive.Entry;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.io.File;

/** Runs real client decoding from disk and packaged resources without login. */
public final class SlayerMovementPreviewFixture {
	private static void require(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
	public static void main(String[] args) throws Exception {
		EntityHandler.load(true);
		require(EntityHandler.projectiles.get(41).id == EntityHandler.PROJECTILE_TYPES.NAGA_SCIMITAR.id(), "scimitar wire visual registered");
		int[] iconPixels = new int[]{0, 0, 0x888888, 0, 0xabcdef, 0};
		com.openrsc.client.model.Sprite icon = new com.openrsc.client.model.Sprite(iconPixels.clone(), 3, 2);
		com.openrsc.client.model.Sprite[] spin = new com.openrsc.client.model.Sprite[32];
		require(orsc.graphics.two.SpinningItemProjectile.fill(icon,
			orsc.graphics.RendererSpriteTransform.IDENTITY, spin) == 16, "sixteen rotation frames");
		require(java.util.Arrays.equals(icon.getPixels(), iconPixels), "item artwork unchanged");
		require(!java.util.Arrays.equals(spin[0].getPixels(), spin[2].getPixels()), "visible projectile rotation");
		for (int i = 0; i < 16; i++) {
			require(spin[i].getWidth() == spin[0].getWidth(), "rotation canvas stable");
			for (int pixel : spin[i].getPixels()) require(pixel == 0 || pixel == 0x888888 || pixel == 0xabcdef, "rotation preserves palette and transparency");
		}
		require("Slimy frog spit begone!".equals(EntityHandler.getItemDef(3318).getDescription()),
			"Slime Solvent client examine matches approved flavor text");
		for (int dose = 3; dose >= 1; dose--) {
			require(("Wax earplugs (" + dose + ")").equals(EntityHandler.getItemDef(3327 - dose).getName()), "wax uses");
			require("Desolve after 10 minutes".equals(EntityHandler.getItemDef(3327 - dose).getDescription()), "wax flavor text");
			require(("Eye Drops (" + dose + ")").equals(EntityHandler.getItemDef(3324 - dose).getName()), "eye drop dose labels");
			require(("Slime Solvent (" + dose + ")").equals(EntityHandler.getItemDef(3321 - dose).getName()),
				"solvent dose labels");
			require("Slimy frog spit begone!".equals(EntityHandler.getItemDef(3321 - dose).getDescription()),
				"partial solvent examine");
		}
		ClientExternalAssetLoader loader = new ClientExternalAssetLoader(Paths.get(args[0]), SlayerMovementPreviewFixture.class);
		int count = 0;
		for (SlayerMovementPreview preview : SlayerMovementPreview.values()) {
			NPCDef npc = EntityHandler.getNpcDef(preview.npcId);
			require(npc.id == preview.npcId && npc.getName().equals(preview.displayName), "NPC identity");
			require(npc.isAttackable() == preview.combatEnabled() && npc.getCamera1() == preview.cameraWidth()
				&& npc.getCamera2() == preview.cameraHeight(), "NPC presentation and harmlessness");
			File source = loader.findFirstFile(new String[]{"dev/myworld/assets/sprites/npcs/slayer-movement-preview"}, preview.assetName + ".png");
			BufferedImage image = loader.readAssetImage(source);
			Entry entry = loader.loadExternalNpcDirectionSheet(source, preview.animationName(), preview.columnWidths(), 3);
			require(entry != null && image != null, "Missing " + preview.assetName);
			entry = preview.withCombatFrames(entry);
			if (preview == SlayerMovementPreview.NAGA) {
				require(preview.loadedFrameCount() == 21, "Naga retains both attack columns");
				for (int row = 0; row < 3; row++) {
					require(preview.projectileAttackFrame(row * 200) == 18 + row, "throw uses second attack column");
					int[] pixels = entry.getFrames()[18 + row].getPixels();
					for (int y = 0; y < 100; y++) for (int x = 0; x < 128; x++) {
						int argb = image.getRGB(600 + x, row * 100 + y);
						int expected = (argb >>> 24) < 64 ? 0 : argb & 0xffffff;
						if (expected == 0 && (argb >>> 24) >= 64) expected = 0x010101;
						require(pixels[y * 128 + x] == expected, "approved throwing pixels");
					}
				}
			}
			EntityHandler.activateSlayerPreviewVisual(preview);
			require(npc.sprites[0] == EntityHandler.getSlayerPreviewAnimationId(preview), "activation");
			require(EntityHandler.getAnimationDef(npc.sprites[0]).hasA() == preview.combatEnabled(), "approved combat enabled");
			if (preview.combatEnabled()) {
				for (int row = 0; row < 3; row++) {
					int[] pixels = entry.getFrames()[15 + row].getPixels();
					int sourceColumn = preview == SlayerMovementPreview.BANSHEE ? 2 : 5;
					int sourceWidth = preview.columnWidths()[sourceColumn];
					for (int y = 0; y < preview.frameHeight(); y++) for (int x = 0; x < sourceWidth; x++) {
						int argb = image.getRGB(sourceColumn * 100 + x, row * preview.frameHeight() + y);
						int expected = (argb >>> 24) < 64 ? 0 : argb & 0xffffff;
						if (expected == 0 && (argb >>> 24) >= 64) expected = 0x010101;
						require(pixels[y * sourceWidth + x] == expected, "approved attack pixels");
					}
				}
			}
			int nativeWidth = preview.columnWidths()[0];
			for (int direction = 0; direction < 8; direction++) {
				int column = NpcDirectionalAnimationMapping.sourceDirection(direction);
				require(column == new int[]{0,1,2,3,4,3,2,1}[direction], "direction mapping");
				require(NpcDirectionalAnimationMapping.mirrors(direction) == (direction >= 5), "mirroring");
				for (int row = 0; row < 3; row++) {
					int[] pixels = entry.getFrames()[column * 3 + row].getPixels();
					int visible = 0;
					for (int y = 0; y < preview.frameHeight(); y++) {
						for (int x = 0; x < nativeWidth; x++) {
							int argb = image.getRGB(column * nativeWidth + x, row * preview.frameHeight() + y);
							int expected = (argb >>> 24) < 64 ? 0 : argb & 0xffffff;
							if (expected == 0 && (argb >>> 24) >= 64) expected = 0x010101;
							require(pixels[y * nativeWidth + x] == expected, "pixel mismatch " + preview.assetName);
							if (expected != 0) visible++;
						}
					}
					require(visible > 0, "empty movement frame");
				}
			}
			int[] sequence = preview == SlayerMovementPreview.GIANT_FROG ? new int[]{0,1,2,0,1,2} : new int[]{0,1,0,2,0,1};
			for (int phase = 0; phase < sequence.length; phase++) {
				require(preview.movementFrame(phase * 10, 10, true) == sequence[phase], "cadence");
				require(preview.movementFrame(phase * 10, 10, false) == 0, "idle reset");
			}
			count++;
		}
		require(count == 8 && SlayerMovementPreview.forNpc(862) == null, "scope");
		System.out.println("PASS eight previews: definitions, 120 source frames, all directions, pixel fidelity, cadence and idle");
	}
}
