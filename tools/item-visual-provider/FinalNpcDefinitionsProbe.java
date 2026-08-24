import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Emits final post-override NPC and animation definitions without reinterpreting them. */
public final class FinalNpcDefinitionsProbe {
	private static final int AUTHENTIC_ANIMATION_STRIDE = 27;
	private static final int AUTHENTIC_ANIMATION_DISCONTINUITY = 1998;
	private static final int AUTHENTIC_ANIMATION_RESUME = 3300;

	private FinalNpcDefinitionsProbe() {
	}

	public static void main(String[] args) {
		EntityHandler.load(true);
		Map<String, Integer> authenticBases = authenticAnimationBases();
		System.out.println("NPC_CATALOG\t" + EntityHandler.npcCount());
		for (int index = 0; index < EntityHandler.npcCount(); index++) {
			NPCDef npc = EntityHandler.getNpcDef(index);
			StringBuilder sprites = new StringBuilder();
			for (int sprite : npc.sprites) {
				if (sprites.length() > 0) {
					sprites.append(',');
				}
				sprites.append(sprite);
			}
			System.out.println("NPC\t" + index
				+ "\t" + npc.id
				+ "\t" + encode(npc.name)
				+ "\t" + encode(npc.description)
				+ "\t" + encode(npc.getCommand1())
				+ "\t" + encode(npc.getCommand2())
				+ "\t" + npc.attack
				+ "\t" + npc.strength
				+ "\t" + npc.hits
				+ "\t" + npc.defense
				+ "\t" + npc.attackable
				+ "\t" + sprites
				+ "\t" + npc.hairColour
				+ "\t" + npc.topColour
				+ "\t" + npc.bottomColour
				+ "\t" + npc.skinColour
				+ "\t" + npc.camera1
				+ "\t" + npc.camera2
				+ "\t" + npc.walkModel
				+ "\t" + npc.combatModel
				+ "\t" + npc.combatSprite);
		}

		System.out.println("ANIMATION_CATALOG\t" + EntityHandler.animationCount());
		for (int index = 0; index < EntityHandler.animationCount(); index++) {
			AnimationDef animation = EntityHandler.getAnimationDef(index);
			Integer authenticBase = authenticBases.get(animation.name.toLowerCase(Locale.ROOT));
			System.out.println("ANIMATION\t" + index
				+ "\t" + encode(animation.name)
				+ "\t" + encode(animation.category)
				+ "\t" + animation.getCharColour()
				+ "\t" + animation.getBlueMask()
				+ "\t" + animation.getGenderModel()
				+ "\t" + animation.hasA()
				+ "\t" + animation.hasF()
				+ "\t" + authenticBase);
		}
	}

	/** Mirrors the client's authentic entity loader, including duplicate-name reuse. */
	private static Map<String, Integer> authenticAnimationBases() {
		Map<String, Integer> bases = new LinkedHashMap<>();
		int nextBase = 0;
		for (int index = 0; index < EntityHandler.animationCount(); index++) {
			AnimationDef animation = EntityHandler.getAnimationDef(index);
			String key = animation.name.toLowerCase(Locale.ROOT);
			if (bases.containsKey(key)) {
				continue;
			}
			bases.put(key, nextBase);
			nextBase += AUTHENTIC_ANIMATION_STRIDE;
			if (nextBase == AUTHENTIC_ANIMATION_DISCONTINUITY) {
				nextBase = AUTHENTIC_ANIMATION_RESUME;
			}
		}
		return bases;
	}

	private static String encode(String value) {
		if (value == null) {
			return "~";
		}
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}
