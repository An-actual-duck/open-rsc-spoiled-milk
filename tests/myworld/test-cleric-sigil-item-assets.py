#!/usr/bin/env python3
"""Validate inert Cleric sigil identities, artwork, packaging, and fallbacks."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import tempfile
import textwrap
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
CLIENT_ITEMS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
LEGACY_ITEM_IDS = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
SIGIL_ITEM_IDS = ROOT / "server/src/com/openrsc/server/content/cleric/ClericSigilItemId.java"
SERVER_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
SERVER_OVERRIDES = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
ASSET_LOADER = ROOT / "Client_Base/src/orsc/ClientExternalAssetLoader.java"
CLIENT_BUILD = ROOT / "Client_Base/build.xml"
ASSET_DIR = ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources/sigils"
SERVER_CLERIC_ROOT = ROOT / "server/src/com/openrsc/server/content/cleric"

SIGILS = (
    (3293, "UNBLESSED_STONE_SARADOMIN_SIGIL", "Unblessed stone sigil of Saradomin",
     "A carved stone sigil awaiting Saradomin's blessing", 443, "unblessed-sara-sigil@28x25"),
    (3294, "BLESSED_STONE_SARADOMIN_SIGIL", "Stone sigil of Saradomin",
     "A stone sigil blessed by Saradomin", 443, "blessed-sara-sigil@28x25"),
    (3295, "UNBLESSED_STONE_GUTHIX_SIGIL", "Unblessed stone sigil of Guthix",
     "A carved stone sigil awaiting Guthix's blessing", 443, "unblessed-guth-sigil@28x25"),
    (3296, "BLESSED_STONE_GUTHIX_SIGIL", "Stone sigil of Guthix",
     "A stone sigil blessed by Guthix", 443, "blessed-guth-sigil@28x25"),
    (3297, "UNBLESSED_STONE_ZAMORAK_SIGIL", "Unblessed stone sigil of Zamorak",
     "A carved stone sigil awaiting Zamorak's blessing", 443, "unblessed-zam-sigil@28x25"),
    (3298, "BLESSED_STONE_ZAMORAK_SIGIL", "Stone sigil of Zamorak",
     "A stone sigil blessed by Zamorak", 443, "blessed-zam-sigil@28x25"),
    (3299, "UNBLESSED_STONE_NEUTRAL_SIGIL", "Unblessed neutral stone sigil",
     "A carved neutral stone sigil awaiting a blessing", 443, "unblessed-neutral-sigil@28x25"),
    (3300, "BLESSED_STONE_NEUTRAL_SIGIL", "Neutral stone sigil",
     "A neutral stone sigil blessed at a god altar", 443, "blessed-neutral-sigil@28x25"),
    (3301, "UNBLESSED_SILVER_SARADOMIN_SIGIL", "Unblessed silver sigil of Saradomin",
     "A carved silver sigil awaiting Saradomin's blessing", 134, "silver-unblessed-sara-sigil@24x21"),
    (3302, "BLESSED_SILVER_SARADOMIN_SIGIL", "Silver sigil of Saradomin",
     "A silver sigil blessed by Saradomin", 134, "silver-blessed-sara-sigil@24x21"),
    (3303, "UNBLESSED_SILVER_GUTHIX_SIGIL", "Unblessed silver sigil of Guthix",
     "A carved silver sigil awaiting Guthix's blessing", 134, "silver-unblessed-guth-sigil@24x21"),
    (3304, "BLESSED_SILVER_GUTHIX_SIGIL", "Silver sigil of Guthix",
     "A silver sigil blessed by Guthix", 134, "silver-blessed-guth-sigil@24x21"),
    (3305, "UNBLESSED_SILVER_ZAMORAK_SIGIL", "Unblessed silver sigil of Zamorak",
     "A carved silver sigil awaiting Zamorak's blessing", 134, "silver-unblessed-zam-sigil@24x21"),
    (3306, "BLESSED_SILVER_ZAMORAK_SIGIL", "Silver sigil of Zamorak",
     "A silver sigil blessed by Zamorak", 134, "silver-blessed-zam-sigil@24x21"),
    (3307, "UNBLESSED_SILVER_NEUTRAL_SIGIL", "Unblessed neutral silver sigil",
     "A carved neutral silver sigil awaiting a blessing", 134, "silver-unblessed-neutral-sigil@24x21"),
    (3308, "BLESSED_SILVER_NEUTRAL_SIGIL", "Neutral silver sigil",
     "A neutral silver sigil blessed at a god altar", 134, "silver-blessed-neutral-sigil@24x21"),
)

SOURCE_HASHES = {
    "stone.png": "387da42b21bd3bef4640c9ade132ee44707d5ee238f876576395deb0931afc75",
    "unblessed-sara-sigil.png": "02f96b0bdae5682f68675a08145c3003963c471ab20b5989df98f3b3646886b3",
    "blessed-sara-sigil.png": "d85e8101251fdf40571910f30b1f4f4a1c4a0eeea5cd01da8f92c78c51212927",
    "unblessed-guth-sigil.png": "1b343020349006201335182d68969f38bd21e370e427d26ca57629567e9b2160",
    "blessed-guth-sigil.png": "18649360697a88bd1d737c4d68c622de590e1b40e4238559fb7bf2c8df38e2aa",
    "unblessed-zam-sigil.png": "7b215d0e54774f7fa4d015ac6c95352bd1275fe41a22c8f4495819efd7cae628",
    "blessed-zam-sigil.png": "1aade7dfe74426dde9e4b33a9aa99a3e87d9cc70636805440eec01323928c9bd",
    "unblessed-neutral-sigil.png": "3e2f4b0a53aaed522d104dcc86d46801cd1f4aa881998fedf70a077e7767845c",
    "blessed-neutral-sigil.png": "2d96ad98466468c22f00446b35672d88337d72824f07b308ddfe04bb7b30e9c0",
}


SERVER_IDENTITY_FIXTURE = r"""
package com.openrsc.server.content.cleric;

public final class ClericSigilItemIdentityFixture {
	private static final ClericAlignment[] ALIGNMENTS = {
		ClericAlignment.SARADOMIN, ClericAlignment.SARADOMIN,
		ClericAlignment.GUTHIX, ClericAlignment.GUTHIX,
		ClericAlignment.ZAMORAK, ClericAlignment.ZAMORAK,
		ClericAlignment.NEUTRAL, ClericAlignment.NEUTRAL,
		ClericAlignment.SARADOMIN, ClericAlignment.SARADOMIN,
		ClericAlignment.GUTHIX, ClericAlignment.GUTHIX,
		ClericAlignment.ZAMORAK, ClericAlignment.ZAMORAK,
		ClericAlignment.NEUTRAL, ClericAlignment.NEUTRAL
	};

	private ClericSigilItemIdentityFixture() {
	}

	public static void main(String[] args) {
		ClericSigilItemId[] identities = ClericSigilItemId.values();
		check(identities.length == 16, "sigil identity count drift");
		for (int index = 0; index < identities.length; index++) {
			ClericSigilItemId identity = identities[index];
			int expectedItemId = 3293 + index;
			check(identity.getItemId() == expectedItemId, "item ID drift " + identity);
			check(ClericSigilItemId.fromItemId(expectedItemId) == identity,
				"item lookup drift " + identity);
			check(identity.getMaterial() == (index < 8
				? ClericSigilMaterial.STONE : ClericSigilMaterial.SILVER),
				"material drift " + identity);
			check(identity.getAlignment() == ALIGNMENTS[index], "alignment drift " + identity);
			check(identity.isBlessed() == (index % 2 == 1), "blessing-state drift " + identity);
		}
		try {
			ClericSigilItemId.fromItemId(3292);
			throw new AssertionError("unknown item ID was accepted");
		} catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_definitions_and_scope() -> None:
    payload = json.loads(SERVER_ITEMS.read_text(encoding="utf-8"))["items"]
    entries = {int(entry["id"]): entry for entry in payload}
    require(max(entries) == 3308, "sigils must occupy the next contiguous server item range")
    require(set(range(3293, 3309)) <= entries.keys(), "server sigil definition range is incomplete")

    legacy_constants = LEGACY_ITEM_IDS.read_text(encoding="utf-8")
    require("public static final int maxCustom = 3309;" in legacy_constants,
            "exclusive server item bound must include all sigils")
    constants = SIGIL_ITEM_IDS.read_text(encoding="utf-8")
    require("legacy {@code ItemId}" in constants and "JVM method-size" in constants,
            "Cleric identity ownership must document the legacy enum compatibility boundary")
    client = CLIENT_ITEMS.read_text(encoding="utf-8")
    require("addClericSigilDefinitions();" in client, "client sigil definitions are not loaded")

    for item_id, constant, name, description, sprite_id, asset_spec in SIGILS:
        require(re.search(rf"{constant}\({item_id},\s*ClericSigilMaterial\.", constants) is not None,
                f"missing stable Cleric sigil item identity {constant}")
        entry = entries[item_id]
        require(entry["name"] == name, f"server name drift for {item_id}")
        require(entry["description"] == description, f"server description drift for {item_id}")
        inert = {
            "command": "", "isStackable": 1 if item_id % 2 == 0 else 0, "isUntradable": 0,
            "isWearable": 0, "isNoteable": 0, "basePrice": 0,
        }
        for field, expected in inert.items():
            require(entry.get(field) == expected,
                    f"server inert field {field} drift for {item_id}: {entry.get(field)!r}")
        require(f"setCustomItemDefinition({item_id}, new ItemDef(" in client,
                f"missing ID-addressed client definition {item_id}")
        require(f'"external-png:{asset_spec}"' in client,
                f"missing client asset identity for {item_id}")
        require(re.search(
            rf"setCustomItemDefinition\({item_id}, new ItemDef\(.*?\n\s*0, {sprite_id}, "
            rf'"external-png:{re.escape(asset_spec)}"',
            client,
            re.DOTALL,
        ) is not None, f"canonical fallback drift for {item_id}")

    overrides = json.loads(SERVER_OVERRIDES.read_text(encoding="utf-8"))["items"]
    require(not any(3293 <= int(entry["id"]) <= 3308 for entry in overrides),
            "new item identities belong in ItemDefsCustom, not the override layer")

    for source in (ROOT / "server/src", ROOT / "server/plugins"):
        for path in source.rglob("*.java"):
            if path == SIGIL_ITEM_IDS:
                continue
            text = path.read_text(encoding="utf-8")
            for _, constant, *_ in SIGILS:
                require(f"ClericSigilItemId.{constant}" not in text,
                        f"C02 item became reachable from production code: {path.relative_to(ROOT)}")

    loader = ASSET_LOADER.read_text(encoding="utf-8")
    sigil_path = '"dev/myworld/assets/sprites/items/inventory-ground/resources/sigils"'
    require(sigil_path in loader, "external loader does not search the maintained sigil folder")
    require(loader.index(sigil_path) < loader.index(
        '"dev/myworld/assets/sprites/items/inventory-ground/resources"'),
        "specific sigil folder must precede the general resource folder")
    require("return getSurface().spriteSelect(item);" in (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8"),
            "missing external-item fallback must still delegate to canonical sprite selection")
    require('<include name="sprites/**/*.png"/>' in CLIENT_BUILD.read_text(encoding="utf-8"),
            "client build no longer packages maintained item PNGs")


def validate_source_assets() -> None:
    actual_pngs = {path.name for path in ASSET_DIR.glob("*.png")}
    expected_pngs = set(SOURCE_HASHES) | {
        f"silver-{name}" for name in SOURCE_HASHES if name != "stone.png"
    }
    require(actual_pngs == expected_pngs,
            f"sigil PNG inventory drift: expected {sorted(expected_pngs)}, got {sorted(actual_pngs)}")
    for name, expected_hash in SOURCE_HASHES.items():
        actual_hash = hashlib.sha256((ASSET_DIR / name).read_bytes()).hexdigest()
        require(actual_hash == expected_hash, f"supplied source sprite changed: {name}")


def build_and_run_server_identity_fixture() -> None:
    sources = sorted(str(path) for path in SERVER_CLERIC_ROOT.glob("*.java"))
    with tempfile.TemporaryDirectory(prefix="cleric-sigil-identities-") as temporary:
        temp = Path(temporary)
        source = temp / "com/openrsc/server/content/cleric/ClericSigilItemIdentityFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(SERVER_IDENTITY_FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), *sources, str(source)], check=True)
        subprocess.run(
            ["java", "-cp", str(classes),
             "com.openrsc.server.content.cleric.ClericSigilItemIdentityFixture"],
            check=True,
        )


FIXTURE = r"""
package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.model.Sprite;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class ClericSigilItemAssetFixture {
	private static final int[] IDS = {
		3293, 3294, 3295, 3296, 3297, 3298, 3299, 3300,
		3301, 3302, 3303, 3304, 3305, 3306, 3307, 3308
	};
	private static final String[] NAMES = {
		"Unblessed stone sigil of Saradomin", "Stone sigil of Saradomin",
		"Unblessed stone sigil of Guthix", "Stone sigil of Guthix",
		"Unblessed stone sigil of Zamorak", "Stone sigil of Zamorak",
		"Unblessed neutral stone sigil", "Neutral stone sigil",
		"Unblessed silver sigil of Saradomin", "Silver sigil of Saradomin",
		"Unblessed silver sigil of Guthix", "Silver sigil of Guthix",
		"Unblessed silver sigil of Zamorak", "Silver sigil of Zamorak",
		"Unblessed neutral silver sigil", "Neutral silver sigil"
	};
	private static final String[] ASSETS = {
		"unblessed-sara-sigil@28x25", "blessed-sara-sigil@28x25",
		"unblessed-guth-sigil@28x25", "blessed-guth-sigil@28x25",
		"unblessed-zam-sigil@28x25", "blessed-zam-sigil@28x25",
		"unblessed-neutral-sigil@28x25", "blessed-neutral-sigil@28x25",
		"silver-unblessed-sara-sigil@24x21", "silver-blessed-sara-sigil@24x21",
		"silver-unblessed-guth-sigil@24x21", "silver-blessed-guth-sigil@24x21",
		"silver-unblessed-zam-sigil@24x21", "silver-blessed-zam-sigil@24x21",
		"silver-unblessed-neutral-sigil@24x21", "silver-blessed-neutral-sigil@24x21"
	};
	private static final String[] SOURCE_FILES = {
		"unblessed-sara-sigil.png", "blessed-sara-sigil.png",
		"unblessed-guth-sigil.png", "blessed-guth-sigil.png",
		"unblessed-zam-sigil.png", "blessed-zam-sigil.png",
		"unblessed-neutral-sigil.png", "blessed-neutral-sigil.png"
	};

	private ClericSigilItemAssetFixture() {
	}

	public static void main(String[] args) throws Exception {
		Path root = Paths.get(args[0]);
		Path assets = root.resolve("dev/myworld/assets/sprites/items/inventory-ground/resources/sigils");
		EntityHandler.load(true);
		check(EntityHandler.itemCount() == 3309, "client item count drift");

		ClientExternalAssetLoader local = new ClientExternalAssetLoader(root, mudclient.class);
		ClientExternalAssetLoader packaged = new ClientExternalAssetLoader(
			root.resolve("missing-runtime-root"), mudclient.class);
		for (int index = 0; index < IDS.length; index++) {
			ItemDef item = EntityHandler.getItemDef(IDS[index]);
			check(item != null && item.id == IDS[index], "client identity drift " + IDS[index]);
			check(NAMES[index].equals(item.getName()), "client name drift " + IDS[index]);
			check(item.getSpriteID() == (index < 8 ? 443 : 134), "fallback sprite drift " + IDS[index]);
			check(("external-png:" + ASSETS[index]).equals(item.getSpriteLocation()),
				"asset location drift " + IDS[index]);
			check(item.isStackable() == (index % 2 == 1) && !item.isWieldable() && !item.membersItem
				&& !item.untradeable && !item.noteable && item.getBasePrice() == 0,
				"inert client flags drift " + IDS[index]);
			Sprite localSprite = local.getExternalItemSprite(item);
			Sprite packagedSprite = packaged.getExternalItemSprite(item);
			check(localSprite != null, "development asset did not load " + IDS[index]);
			check(packagedSprite != null, "packaged asset did not load " + IDS[index]);
			int[] bounds = visibleBounds(localSprite);
			if (index < 8) {
				check(bounds[2] <= 28 && bounds[3] <= 25, "stone render bound drift " + IDS[index]);
			} else {
				check(bounds[2] <= 24 && bounds[3] <= 21, "silver render bound drift " + IDS[index]);
			}
		}

		ItemDef missing = new ItemDef("Missing", "Missing", "", 0, 443,
			"external-png:missing-cleric-sigil", true, false, 0, 0,
			false, false, false, 99999);
		check(local.getExternalItemSprite(missing) == null,
			"missing external image must return null for canonical fallback");
		check(missing.getSpriteID() == 443, "missing-image fixture lost canonical fallback identity");

		Map<Integer, Integer> silver = silverPalette();
		for (String sourceName : SOURCE_FILES) {
			BufferedImage stone = ImageIO.read(assets.resolve(sourceName).toFile());
			BufferedImage metal = ImageIO.read(assets.resolve("silver-" + sourceName).toFile());
			check(stone != null && metal != null, "PNG decode failed " + sourceName);
			check(stone.getWidth() == 28 && stone.getHeight() == 25, "stone dimensions drift " + sourceName);
			check(metal.getWidth() == 28 && metal.getHeight() == 25, "silver source dimensions drift " + sourceName);
			check(stone.getColorModel().hasAlpha() && metal.getColorModel().hasAlpha(),
				"sigil alpha channel missing " + sourceName);
			int preservedSymbolPixels = 0;
			int visiblePixels = 0;
			for (int y = 0; y < stone.getHeight(); y++) {
				for (int x = 0; x < stone.getWidth(); x++) {
					int original = stone.getRGB(x, y);
					int expected = silver.containsKey(original) ? silver.get(original) : original;
					check(metal.getRGB(x, y) == expected,
						"silver derivation changed a non-substrate pixel in " + sourceName
							+ " at " + x + "," + y);
					if ((original >>> 24) != 0) {
						visiblePixels++;
						if (!silver.containsKey(original)) {
							preservedSymbolPixels++;
						}
					}
				}
			}
			check(visiblePixels > 0 && preservedSymbolPixels > 0,
				"missing visible substrate or symbol pixels " + sourceName);
			check((stone.getRGB(0, 0) >>> 24) == 0 && (metal.getRGB(0, 0) >>> 24) == 0,
				"transparent corner drift " + sourceName);
		}
		System.out.println("Cleric sigil runtime definitions, assets, packaging, and fallbacks passed");
	}

	private static Map<Integer, Integer> silverPalette() {
		Map<Integer, Integer> values = new HashMap<Integer, Integer>();
		values.put(0xFF414A56, 0xFF373737);
		values.put(0xFF5B5762, 0xFF4B4B4B);
		values.put(0xFF5F6670, 0xFF5F5F5F);
		values.put(0xFF6F7883, 0xFF737373);
		values.put(0xFF818691, 0xFFBEBEBE);
		values.put(0xFF8F98A3, 0xFFD7D7D7);
		values.put(0xFF9FA8B3, 0xFFEBEBEB);
		values.put(0xFFB5AEC3, 0xFFFFFFFF);
		return values;
	}

	private static int[] visibleBounds(Sprite sprite) {
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
		check(maxX >= minX && maxY >= minY, "empty loaded sprite");
		return new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
"""


def build_and_run_runtime_fixture() -> None:
    subprocess.run([str(ROOT / "scripts/build-client.sh")], cwd=ROOT, check=True)
    required_resources = {
        f"myworld-assets/sprites/items/inventory-ground/resources/sigils/{path.name}"
        for path in ASSET_DIR.glob("*.png")
    }
    with zipfile.ZipFile(CLIENT_JAR) as archive:
        packaged = set(archive.namelist())
    missing = sorted(required_resources - packaged)
    require(not missing, "client jar is missing sigil assets: " + ", ".join(missing))

    with tempfile.TemporaryDirectory(prefix="cleric-sigil-assets-") as temporary:
        temp = Path(temporary)
        source = temp / "orsc/ClericSigilItemAssetFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            ["javac", "-cp", str(CLIENT_JAR), "-d", str(temp), str(source)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", f"{temp}:{CLIENT_JAR}",
             "orsc.ClericSigilItemAssetFixture", str(ROOT)],
            cwd=ROOT / "Client_Base",
            check=True,
        )


def main() -> None:
    validate_definitions_and_scope()
    validate_source_assets()
    build_and_run_server_identity_fixture()
    build_and_run_runtime_fixture()
    print("PASS: Cleric C02 stable item and asset foundation checks passed")


if __name__ == "__main__":
    main()
