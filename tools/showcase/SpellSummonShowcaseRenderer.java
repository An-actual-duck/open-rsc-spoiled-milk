package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONObject;
import orsc.enumerations.ORSCharacterDirection;
import orsc.graphics.two.MudClientGraphics;
import orsc.multiclient.ClientPort;

/** Offline compositions through the actual client actor/VFX renderer. Never opens a connection. */
public final class SpellSummonShowcaseRenderer {

	private static final int WIDTH = 320;
	private static final int HEIGHT = 180;
	private static final int BACKGROUND = 0x111923;
	private static void set(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void call(Object target, String name) throws Exception {
		Method method = target.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(target);
	}

	private static int nearestClothing(mudclient client, int target) throws Exception {
		Field field = mudclient.class.getDeclaredField("playerClothingColors");
		field.setAccessible(true);
		int[] colors = (int[]) field.get(client);
		int best = 0, distance = Integer.MAX_VALUE;
		for (int i = 0; i < colors.length; i++) {
			int r = (colors[i] >> 16 & 255) - (target >> 16 & 255);
			int g = (colors[i] >> 8 & 255) - (target >> 8 & 255);
			int b = (colors[i] & 255) - (target & 255);
			int score = r * r + g * g + b * b;
			if (score < distance) { distance = score; best = i; }
		}
		return best;
	}

	private static ORSCharacter actor(JSONObject scene) {
		ORSCharacter actor = new ORSCharacter();
		actor.colourHair = 1;
		actor.colourSkin = 0;
		actor.colourTop = 2;
		actor.colourBottom = 4;
		actor.layerAnimation[0] = 1;
		actor.layerAnimation[1] = 2;
		actor.layerAnimation[2] = 3;
		JSONArray gear = scene.getJSONArray("gear");
		for (int i = 0; i < gear.length(); i++) {
			JSONObject item = gear.getJSONObject(i);
			actor.layerAnimation[item.getInt("layer")] = item.getInt("appearanceId");
		}
		actor.direction = scene.getString("kind").equals("buff")
			? ORSCharacterDirection.NORTH : ORSCharacterDirection.WEST;
		return actor;
	}

	public static void main(String[] args) throws Exception {
		Path root = Paths.get(args[0]).toAbsolutePath();
		Path output = Paths.get(args[1]).toAbsolutePath();
		Path cache = output.resolve("runtime-cache");
		JSONObject plan = new JSONObject(new String(Files.readAllBytes(Paths.get(args[2])), StandardCharsets.UTF_8));
		ClientPort port = (ClientPort) Proxy.newProxyInstance(ClientPort.class.getClassLoader(),
			new Class<?>[]{ClientPort.class}, (proxy, method, values) -> {
				if (method.getName().equals("getCacheLocation")) return cache.toString();
				if (method.getReturnType() == boolean.class) return false;
				if (method.getReturnType() == int.class) return 0;
				return null;
			});
		osConfig.F_ANDROID_BUILD = true;
		mudclient client = new mudclient(port);
		Config.F_CACHE_DIR = cache.toString();
		Config.S_WANT_CUSTOM_SPRITES = true;
		Config.S_WANT_CUSTOM_LANDSCAPE = true;
		Config.S_SHOW_FLOATING_NAMETAGS = false;
		EntityHandler.load(true);
		MudClientGraphics surface = new MudClientGraphics(WIDTH, HEIGHT, 5000);
		client.setSurface(surface);
		if (!surface.fillSpriteTree()) throw new IllegalStateException("Missing isolated sprite archive");
		set(client, "externalAssetLoader", new ClientExternalAssetLoader(root, SpellSummonShowcaseRenderer.class));
		call(client, "loadExternalEquipmentSprites");
		call(client, "loadExternalCombatEffectSprites");
		JSONArray results = new JSONArray();
		JSONArray scenes = plan.getJSONArray("scenes");
		for (int s = 0; s < scenes.length(); s++) {
			JSONObject scene = scenes.getJSONObject(s);
			String key = scene.getString("key"), kind = scene.getString("kind");
			Path frames = output.resolve("frames").resolve(key);
			Files.createDirectories(frames);
			ORSCharacter player = actor(scene);
			player.colourTop = nearestClothing(client, 0x808080);
			player.colourBottom = nearestClothing(client, 0x404040);
			client.localPlayer = player;
			set(client, "players", new ORSCharacter[]{player});
			set(client, "cameraRotation", 0);
			ORSCharacter npc = new ORSCharacter();
			npc.npcId = kind.equals("summon") ? 0 : 104;
			npc.direction = kind.equals("summon") ? ORSCharacterDirection.SOUTH_EAST : ORSCharacterDirection.EAST;
			set(client, "npcs", new ORSCharacter[]{npc});
			int effect = scene.getInt("effectId");
			int duration = client.getCombatEffectDuration(effect);
			if (duration <= 0) throw new IllegalStateException("Unloaded effect: " + key);
			int arrival = kind.equals("summon") ? client.getCombatEffectDuration(28) : 0;
			int end = 600 + duration * 20 + (kind.equals("summon") ? arrival * 20 + 1500 : 900);
			JSONArray timeline = new JSONArray();
			int[] previous = null;
			String previousPart = "";
			int rendered = 0;
			for (int ms = 0; ms < end; ms += 20) {
				Arrays.fill(surface.pixelData, BACKGROUND);
				set(client, "frameCounter", ms / 20);
				int elapsed = (ms - 600) / 20;
				player.combatEffectType = 0;
				player.combatEffectTime = 0;
				npc.combatEffectType = 0;
				npc.combatEffectTime = 0;
				String part = "";
				if (ms == 600 && kind.equals("buff")) client.applyCombatEffectUpdate(player, effect);
				if (ms >= 600 && elapsed < duration) {
					if (kind.equals("spell")) {
						npc.combatEffectType = effect;
						npc.combatEffectTime = duration - elapsed;
					} else if (kind.equals("summon")) {
						player.combatEffectType = effect;
						player.combatEffectTime = duration - elapsed;
					} else {
						int frame = elapsed / 3;
						for (int component : ClericBlessingContract.buffAnimationSequence(effect)) {
							int count = client.getCombatEffectFrameCount(component);
							if (frame < count) { part = client.getCombatEffectName(component); break; }
							frame -= count;
						}
					}
				}
				if (kind.equals("buff")) {
					client.drawPlayer(0, 128, 34, 64, 102, 0, 20, 100);
				} else if (kind.equals("spell")) {
					client.drawPlayer(0, 30, 34, 64, 102, 0, 20, 100);
					client.drawNPC(0, 218, 20, 64, 116, 0, 20, 100);
				} else {
					client.drawPlayer(0, 40, 34, 64, 102, 0, 20, 100);
					int arrived = elapsed - duration;
					if (ms >= 600 && arrived >= 0) {
						if (arrived < arrival) {
							npc.combatEffectType = 28;
							npc.combatEffectTime = arrival - arrived;
						}
						int walked = Math.max(0, Math.min(40, arrived - arrival));
						npc.stepFrame = walked > 0 && walked < 40
							? ((walked / 6) % 4) * Math.max(1, EntityHandler.getNpcDef(0).getWalkModel()) : 0;
						client.drawNPC(0, 194 - walked / 3, 44, 96, 92, 0, 20, 100);
					}
				}
				if (previous != null && Arrays.equals(previous, surface.pixelData) && part.equals(previousPart)) {
					JSONObject last = timeline.getJSONObject(timeline.length() - 1);
					last.put("durationMs", last.getInt("durationMs") + 20);
				} else {
					String filename = String.format("%04d.png", rendered++);
					BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
					image.setRGB(0, 0, WIDTH, HEIGHT, surface.pixelData, 0, WIDTH);
					ImageIO.write(image, "png", frames.resolve(filename).toFile());
					timeline.put(new JSONObject().put("file", "frames/" + key + "/" + filename)
						.put("durationMs", 20).put("component", part));
					previous = surface.pixelData.clone();
					previousPart = part;
				}
				if (kind.equals("buff") && player.clericBuffPlayback != null) player.clericBuffPlayback.tick();
			}
			results.put(new JSONObject().put("key", key).put("effectId", effect)
				.put("nativeEffectTicks", duration).put("nativeFrames", client.getCombatEffectFrameCount(effect))
				.put("arrivalEffectTicks", arrival).put("timeline", timeline));
			System.out.println(key + ": " + rendered + " distinct frames, native effect " + duration + " ticks");
		}
		Files.write(output.resolve("render-timeline.json"), new JSONObject().put("scenes", results)
			.toString(2).getBytes(StandardCharsets.UTF_8));
		System.exit(0);
	}
}
