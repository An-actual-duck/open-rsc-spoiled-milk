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
		require("Slimy frog spit begone!".equals(EntityHandler.getItemDef(3318).getDescription()),
			"Slime Solvent client examine matches approved flavor text");
		for (int dose = 3; dose >= 1; dose--) {
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
			require(npc.isAttackable() == (preview == SlayerMovementPreview.GIANT_FROG) && npc.getCamera1() == preview.cameraWidth()
				&& npc.getCamera2() == preview.cameraHeight(), "NPC presentation and harmlessness");
			File source = loader.findFirstFile(new String[]{"dev/myworld/assets/sprites/npcs/slayer-movement-preview"}, preview.assetName + ".png");
			BufferedImage image = loader.readAssetImage(source);
			Entry entry = loader.loadExternalNpcDirectionSheet(source, preview.animationName(), preview.columnWidths(), 3);
			require(entry != null && image != null, "Missing " + preview.assetName);
			EntityHandler.activateSlayerPreviewVisual(preview);
			require(npc.sprites[0] == EntityHandler.getSlayerPreviewAnimationId(preview), "activation");
			require(EntityHandler.getAnimationDef(npc.sprites[0]).hasA() == (preview == SlayerMovementPreview.GIANT_FROG), "only frog combat enabled");
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
