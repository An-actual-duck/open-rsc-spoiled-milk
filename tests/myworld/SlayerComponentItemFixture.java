import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;

public final class SlayerComponentItemFixture {
	private static final java.util.Map<Integer, String[]> approved = new java.util.HashMap<>();
	public static void main(String[] args) {
		for (String arg : args) { String[] fields = arg.split("\\|"); approved.put(Integer.parseInt(fields[0]), fields); }
		orsc.Config.S_WANT_BANK_NOTES = true;
		orsc.Config.S_WANT_CERT_AS_NOTES = true;
		EntityHandler.load(true);
		check(3333, "Giant frog hide", "A slick hide from a giant frog.", 69, 0x378B83, false);
		check(3334, "Banshee hide", "A pale, ghostly hide from a banshee.", 69, 0xA58CAB, false);
		check(3335, "Naga hide", "A supple hide covered in naga scales.", 69, 0x56753E, false);
		check(3336, "Terror dog hide", "A tough hide from a terror dog.", 69, 0x655951, false);
		check(3337, "Bloodveld hide", "A soft, fleshy hide from a bloodveld.", 69, 0x9B8065, false);
		check(3338, "Dark beast hide", "A thick, dark hide from a dark beast.", 69, 0x393225, false);
		check(3339, "Cockatrice Feathers", "Feathers that twitch at the slightest touch.", 176, 0x8F9B5B, true);
		check(3340, "Slimey Residue", "Sticky abyssal flesh that refuses to hold its shape.", 262, 0x62798C, true);
		check(3399, "Ectoplasm", "A cold, quivering remnant of a banshee's spirit.", 262, 0xB5A4CF, true);
		check(3341, "Sticky Saliva Gland", "A giant frog's gland, still oozing sticky saliva.", 116, 0x80A65B, false);
		check(3342, "Cockatrice Eye", "A lifeless eye with an unsettling stare.", 116, 0xB9A65B, false);
		check(3343, "Frozen Tear", "A banshee's sorrow, crystallized into a single tear.", 74, 0x99CDDD, false);
		check(3344, "Terror Fang", "A sharp fang from a terror dog.", 145, 0xD6C5A0, false);
		check(3345, "Leach Tongue", "A bloodveld's tongue, disturbingly elastic.", 103, 0xAF6262, false);
		check(3346, "Lightning Horn", "A dark beast's horn, prickling with static.", 145, 0xD8BC61, false);
		check(3347, "Abyssal Vertibrae", "A segment of an abyssal demon's spine, slick with residue.", 20, 0x91A4B2, false);
		check(3348, "Abyssal Rib", "A curved abyssal rib, held together by clinging flesh.", 137, 0x8199AA, false);
		if (approved.size() != 16) throw new AssertionError("Missing approved material mappings");
		System.out.println("PASS: 17 Slayer materials, approved artwork, effective prices, flags and notes; retired Banshee hide preserved");
	}
	private static void check(int id, String name, String description, int sprite, int mask, boolean stackable) {
		ItemDef item = EntityHandler.getItemDef(id);
		String location = "items:" + sprite;
		int price = 0;
		if (approved.containsKey(id)) { sprite = -1; mask = 0; location = approved.get(id)[1]; price = Integer.parseInt(approved.get(id)[2]); }
		if (item == null || item.id != id || !name.equals(item.getName())
			|| !description.equals(item.getDescription()) || item.spriteID != sprite
			|| !location.equals(item.spriteLocation) || item.getPictureMask() != mask
			|| item.stackable != stackable || item.wieldable || item.untradeable
			|| item.membersItem || item.noteable == stackable || item.basePrice != price)
			throw new AssertionError("Component mismatch: " + id);
		if (!stackable) {
			ItemDef note = EntityHandler.getItemDef(id, true);
			if (note == null || !note.stackable || !name.equals(note.getName()))
				throw new AssertionError("Missing note form: " + id);
		}
	}
}
