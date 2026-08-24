import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import orsc.mudclient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Emits the final, post-override client item catalog without interpreting it. */
public final class FinalItemDefinitionsProbe {
	private FinalItemDefinitionsProbe() {
	}

	public static void main(String[] args) {
		EntityHandler.load(true);
		System.out.println("AUTHENTIC_ITEM_BASE\t" + mudclient.spriteItem);
		System.out.println("CATALOG\t" + EntityHandler.itemCount());
		for (int index = 0; index < EntityHandler.itemCount(); index++) {
			ItemDef item = EntityHandler.getItemDef(index);
			System.out.println("ITEM\t" + index
				+ "\t" + item.id
				+ "\t" + encode(item.name)
				+ "\t" + encode(item.getSpriteLocation())
				+ "\t" + item.getSpriteID()
				+ "\t" + item.getPictureMask()
				+ "\t" + item.getBlueMask());
		}
	}

	private static String encode(String value) {
		if (value == null) {
			return "~";
		}
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}
