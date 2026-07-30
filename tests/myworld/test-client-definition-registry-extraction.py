#!/usr/bin/env python3
"""Characterize client definition indexes, catalogs, and fallback behavior."""

from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
REGISTRY = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/ClientDefinitionRegistry.java"
PRAYER_BOOKS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/PrayerBookDefinitions.java"
FALLBACKS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/ClientDefinitionFallbackDiagnostics.java"

EXPECTED: dict[str, tuple[int, str]] = {
    "npcs": (845, "66d8165a4e8ca7e8559d8c70d8302c97f68595b421145addc3b5a15e74a14539"),
    "items": (3293, "5268b0a9f1ace7f3cba5886c20a4e85a2aa7c73d3e21c7b04906075a0a345a86"),
    "textures": (55, "f2641fe74967a839ad0c6121e5e67d4016cb05666aaf3e4774ed989ea3c8d951"),
    "animations": (1060, "2315c562283f5b0dc21d7af97d9a5920f857c1ca36ce2bd59532e2d26c18fd6d"),
    "projectiles": (41, "c79c74303e8ce5c5c345640227a0023ccbc63285f0a652f404794c35c1d5385f"),
    "guiParts": (55, "fa64061ea784a19baf69fa49c11262e8a0294cb25858b45a1e1751a6ffbde408"),
    "crowns": (5, "cdfe0e9ccf0037bfbb4183f36795e2a2ca2603e8eeee2bcedcb97f8b8d11e774"),
    "spells": (46, "89b6173c83cced0c332c75566ea24e09adfe9ccef3f1ebcb3416d17cdc418fcb"),
    "prayers-saradomin": (16, "4c360a83e3f8e19ed83516852c7127c98d3dfd28f774f67ef90b701946491520"),
    "tiles": (26, "a8b4c871d4dee459ac027405533983ef4baa25d8cbc6744aeaed4a046d312204"),
    "doors": (214, "399ac9b9dbd5f68f89deca97b3e75c1b3c60ffbd6a6107dadf5397f5d3c2886a"),
    "elevations": (6, "bb07d92231318705f2b9b9838b676b2cb78fe11ad01d40dd678f644b459bb265"),
    "objects": (1332, "9d4809e75b9aae1c883a8e05711e20bf8997c030c58e83607a56c95648d3226c"),
    "models": (459, "727dba404ab78527b20c62c4bd058610b087346166566acb24db0430011a52b0"),
    "bankTemplates": (2, "3bca944c9b7466fb6cba8a6542a214a4d7cd69af59a1c4296c58ddaa4304b1fc"),
    "prayers-zamorak": (16, "948045525d172f41e7b6464f3cb7a072dd96b829c63a015facd277aeb9d3210e"),
    "prayers-guthix": (16, "4477899b4368612f6068a0d9a0eeabdbafd78a638a240b2ca2e0f8456bfbcd75"),
}

FIXTURE = r"""
package com.openrsc.client.entityhandling;

import com.openrsc.client.entityhandling.defs.EntityDef;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.defs.SpriteDef;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ClientDefinitionRegistryFixture {
	private interface Lookup {
		Object get(int index);
	}

	private ClientDefinitionRegistryFixture() {
	}

	public static void main(String[] args) throws Exception {
		EntityHandler.load(true);

		result("npcs", EntityHandler.npcCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getNpcDef(index); }
		});
		result("items", EntityHandler.itemCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getItemDef(index); }
		});
		result("textures", EntityHandler.textureCount(), new Lookup() {
			public Object get(int index) { return legacyCatalog("textures").get(index); }
		});
		result("animations", EntityHandler.animationCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getAnimationDef(index); }
		});
		result("projectiles", EntityHandler.projectilesCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.projectiles.get(index); }
		});
		result("guiParts", EntityHandler.GUIparts.size(), new Lookup() {
			public Object get(int index) { return EntityHandler.GUIparts.get(index); }
		});
		result("crowns", EntityHandler.crownCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.crowns.get(index); }
		});
		result("spells", EntityHandler.spellCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getSpellDef(index); }
		});
		result("prayers-saradomin", EntityHandler.prayerCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getPrayerDef(index); }
		});
		result("tiles", EntityHandler.tileCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getTileDef(index); }
		});
		result("doors", EntityHandler.doorCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getDoorDef(index); }
		});
		result("elevations", EntityHandler.elevationCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getElevationDef(index); }
		});
		result("objects", EntityHandler.objectCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getObjectDef(index); }
		});
		result("models", EntityHandler.getModelCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getModelName(index); }
		});
		result("bankTemplates", 2, new Lookup() {
			public Object get(int index) { return index == 0 ? EntityHandler.noteDef : EntityHandler.certificateDef; }
		});

		assertStableIds();
		assertFallbacks();

		EntityHandler.setPrayerBook("zamorak");
		result("prayers-zamorak", EntityHandler.prayerCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getPrayerDef(index); }
		});
		if (!"ZAMORAK".equals(EntityHandler.getActivePrayerBook())) {
			throw new AssertionError("Zamorak prayer book did not become active");
		}
		EntityHandler.setPrayerBook("guthix");
		result("prayers-guthix", EntityHandler.prayerCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.getPrayerDef(index); }
		});
		EntityHandler.setPrayerBook("unknown");
		if (!"SARADOMIN".equals(EntityHandler.getActivePrayerBook()) || EntityHandler.prayerCount() != 16) {
			throw new AssertionError("Unknown prayer book did not retain Saradomin fallback behavior");
		}
	}

	private static void assertStableIds() {
		for (int i = 0; i < EntityHandler.itemCount(); i++) {
			ItemDef item = EntityHandler.getItemDef(i);
			if (item == null || item.id != i) {
				throw new AssertionError("Item index drift at " + i);
			}
		}
		assertSpriteIds("projectile", EntityHandler.projectilesCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.projectiles.get(index); }
		});
		assertSpriteIds("GUI part", EntityHandler.GUIparts.size(), new Lookup() {
			public Object get(int index) { return EntityHandler.GUIparts.get(index); }
		});
		assertSpriteIds("crown", EntityHandler.crownCount(), new Lookup() {
			public Object get(int index) { return EntityHandler.crowns.get(index); }
		});
	}

	@SuppressWarnings("unchecked")
	private static List<Object> legacyCatalog(String fieldName) {
		try {
			Field field = EntityHandler.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			return (List<Object>) field.get(null);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("Unable to inspect legacy catalog " + fieldName, failure);
		}
	}

	private static void assertSpriteIds(String label, int count, Lookup lookup) {
		for (int i = 0; i < count; i++) {
			SpriteDef sprite = (SpriteDef) lookup.get(i);
			if (sprite == null || sprite.id != i) {
				throw new AssertionError(label + " index drift at " + i);
			}
		}
	}

	private static void assertFallbacks() throws Exception {
		if (EntityHandler.getNpcDef(Integer.MAX_VALUE) != EntityHandler.getNpcDef(825)) {
			throw new AssertionError("Malformed NPC ID did not use NPC 825");
		}
		if (EntityHandler.getAnimationDef(Integer.MAX_VALUE) != EntityHandler.getAnimationDef(0)) {
			throw new AssertionError("Malformed animation ID did not use animation 0");
		}
		if (EntityHandler.getObjectDef(Integer.MAX_VALUE) != EntityHandler.getObjectDef(4)) {
			throw new AssertionError("Malformed object ID did not use object 4");
		}

		PrintStream originalErr = System.err;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		try {
			System.setErr(new PrintStream(captured, true, "UTF-8"));
			ItemDef first = EntityHandler.getItemDef(999999);
			ItemDef duplicate = EntityHandler.getItemDef(999999);
			ItemDef noted = EntityHandler.getItemDef(-999999);
			if (first.id != 1544 || duplicate.id != 1544 || noted.id != 1544) {
				throw new AssertionError("Malformed item ID did not use item 1544");
			}
		} finally {
			System.setErr(originalErr);
		}
		String diagnostics = new String(captured.toByteArray(), StandardCharsets.UTF_8).trim();
		String[] lines = diagnostics.isEmpty() ? new String[0] : diagnostics.split("\\R");
		if (lines.length != 2) {
			throw new AssertionError("Expected two deduplicated item fallback lines, got " + lines.length);
		}
		for (String line : lines) {
			if (!line.startsWith("CLIENT_ITEM_DEF_FALLBACK requestedId=")
				|| !line.contains(" reason=out-of-range itemCount=3293")) {
				throw new AssertionError("Malformed fallback diagnostic: " + line);
			}
		}
	}

	private static void result(String name, int count, Lookup lookup) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (int i = 0; i < count; i++) {
			append(digest, Integer.toString(i));
			appendValue(digest, lookup.get(i));
		}
		System.out.println("RESULT " + name + " " + count + " " + hex(digest.digest()));
	}

	private static void appendValue(MessageDigest digest, Object value) throws Exception {
		if (value == null) {
			append(digest, "<null>");
			return;
		}
		Class<?> type = value.getClass();
		if (type.isArray()) {
			append(digest, type.getName());
			int length = Array.getLength(value);
			append(digest, Integer.toString(length));
			for (int i = 0; i < length; i++) {
				appendValue(digest, Array.get(value, i));
			}
			return;
		}
		if (value instanceof Map) {
			List<Map.Entry<?, ?>> entries = new ArrayList<Map.Entry<?, ?>>(((Map<?, ?>) value).entrySet());
			Collections.sort(entries, new Comparator<Map.Entry<?, ?>>() {
				public int compare(Map.Entry<?, ?> left, Map.Entry<?, ?> right) {
					return String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey()));
				}
			});
			for (Map.Entry<?, ?> entry : entries) {
				appendValue(digest, entry.getKey());
				appendValue(digest, entry.getValue());
			}
			return;
		}
		if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character) {
			append(digest, type.getName());
			append(digest, String.valueOf(value));
			return;
		}

		append(digest, type.getName());
		List<Field> fields = new ArrayList<Field>();
		for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers())) {
					fields.add(field);
				}
			}
		}
		Collections.sort(fields, new Comparator<Field>() {
			public int compare(Field left, Field right) {
				String leftName = left.getDeclaringClass().getName() + "." + left.getName();
				String rightName = right.getDeclaringClass().getName() + "." + right.getName();
				return leftName.compareTo(rightName);
			}
		});
		for (Field field : fields) {
			field.setAccessible(true);
			append(digest, field.getDeclaringClass().getName() + "." + field.getName());
			appendValue(digest, field.get(value));
		}
	}

	private static void append(MessageDigest digest, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update((byte) (bytes.length >>> 24));
		digest.update((byte) (bytes.length >>> 16));
		digest.update((byte) (bytes.length >>> 8));
		digest.update((byte) bytes.length);
		digest.update(bytes);
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}
}
"""


def require_source_boundaries() -> None:
    handler = ENTITY_HANDLER.read_text(encoding="utf-8")
    registry = REGISTRY.read_text(encoding="utf-8")
    prayer_books = PRAYER_BOOKS.read_text(encoding="utf-8")
    fallbacks = FALLBACKS.read_text(encoding="utf-8")

    required_handler = (
        "private static final ClientDefinitionRegistry REGISTRY",
        "REGISTRY.mutableItems()",
        "REGISTRY.item(id)",
        "PRAYER_BOOKS.setPrayerBook(prayerBook)",
        "MyWorldItemOverrides.apply(items);",
        "loadNpcDefinitions1();",
        "loadAnimationDefinitions();",
        "loadSpellDefinitions();",
    )
    for snippet in required_handler:
        if snippet not in handler:
            raise AssertionError(f"EntityHandler missing ownership boundary: {snippet}")
    for forbidden in ("loggedItemFallbacks", "System.err.println", "new ArrayList<"):
        if forbidden in handler:
            raise AssertionError(f"EntityHandler still owns registry/diagnostic state: {forbidden}")
    if "Weak Magic Power" in handler or "Corrosive Aura" in handler:
        raise AssertionError("EntityHandler still authors prayer-book entries")

    for catalog in (
        "npcs", "items", "textures", "animations", "projectiles", "guiParts",
        "crowns", "spells", "prayers", "tiles", "doors", "elevations", "objects", "models",
    ):
        if catalog not in registry:
            raise AssertionError(f"ClientDefinitionRegistry missing {catalog} catalog")
    if "ClientDefinitionFallbackDiagnostics" not in registry:
        raise AssertionError("Registry does not delegate fallback diagnostics")
    ordinary_prayers = prayer_books.count("\t\taddPrayerDefinition(")
    special_prayers = prayer_books.count("\t\taddSpecialPrayerDefinition(")
    if (ordinary_prayers, special_prayers) != (45, 3):
        raise AssertionError(
            "Prayer book owner must retain 45 ordinary and 3 specialty definitions"
        )
    if "CLIENT_ITEM_DEF_FALLBACK" not in fallbacks or "loggedItemFallbacks" not in fallbacks:
        raise AssertionError("Fallback diagnostics do not own bounded item logging")


def run_fixture() -> dict[str, tuple[int, str]]:
    subprocess.run([str(ROOT / "scripts/build-client.sh")], cwd=ROOT, check=True)
    with tempfile.TemporaryDirectory(prefix="client-definition-registry-") as temp:
        temp_path = Path(temp)
        source = temp_path / "com/openrsc/client/entityhandling/ClientDefinitionRegistryFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            ["javac", "-cp", str(CLIENT_JAR), "-d", temp, str(source)],
            cwd=ROOT,
            check=True,
        )
        result = subprocess.run(
            ["java", "-cp", f"{temp}:{CLIENT_JAR}", "com.openrsc.client.entityhandling.ClientDefinitionRegistryFixture"],
            cwd=ROOT / "Client_Base",
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise AssertionError(
                "Client definition registry fixture failed:\n"
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )

    observed: dict[str, tuple[int, str]] = {}
    for line in result.stdout.splitlines():
        if not line.startswith("RESULT "):
            continue
        _, name, count, digest = line.split()
        observed[name] = (int(count), digest)
    return observed


def main() -> None:
    require_source_boundaries()
    observed = run_fixture()
    if not EXPECTED:
        for name, (count, digest) in observed.items():
            print(f'{name}: ({count}, "{digest}"),')
        raise AssertionError("Record the published-main definition fingerprints in EXPECTED")
    if observed != EXPECTED:
        missing = sorted(set(EXPECTED) - set(observed))
        extra = sorted(set(observed) - set(EXPECTED))
        changed = sorted(name for name in set(observed) & set(EXPECTED) if observed[name] != EXPECTED[name])
        changed_values = {name: observed[name] for name in changed}
        raise AssertionError(
            "Client definition fingerprint drift: "
            f"missing={missing} extra={extra} changed={changed} observed={changed_values}"
        )
    print("PASS: client registry extraction preserves all definition indexes and fingerprints")


if __name__ == "__main__":
    main()
