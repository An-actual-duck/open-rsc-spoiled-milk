#!/usr/bin/env python3
"""Validate the approved C12 Cleric on-entity animation contract."""

from __future__ import annotations

import struct
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
CLIENT = ROOT / "Client_Base/src"
ASSETS = ROOT / "dev/myworld/assets/animations/on-entity"


ANIMATIONS = {
    "MEND": (67, "heal-alt/heal-alt.png", 1728, 64, 27, 48),
    "FERVOR": (68, "fist/fist.png", 672, 64, 14, 48),
    "PURIFY": (69, "cleanse 2/cleanse 2.png", 816, 64, 17, 48),
    "RESTORE": (70, "cleanse/cleanse.png", 816, 64, 17, 48),
    "WARD": (71, "wall-shield/Buff n Debuff P4 04.png", 1472, 64, 23, 48),
    "GREATER_MEND": (72, "greater-heal-alt/greater-heal-alt.png", 1216, 64, 19, 48),
    "ZEAL": (73, "Holy VFX 03/Holy VFX 03(64x80).png", 1600, 80, 25, 48),
    "THORNS": (74, "thorns/thorns.png", 672, 32, 21, 32),
    "AEGIS": (75, "Holy VFX 04/Holy VFX 04(48x48).png", 816, 48, 17, 48),
    "RALLY": (76, "sword-clash/sword-clash.png", 704, 64, 11, 48),
    "RESPITE": (77, "heart-pop/heart-pop.png", 864, 64, 18, 48),
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def png_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}")
    require(data[12:16] == b"IHDR", f"missing PNG IHDR: {path}")
    return struct.unpack(">II", data[16:24])


def validate_sources() -> None:
    effect = read(SERVER / "model/entity/update/CombatEffect.java")
    catalog = read(SERVER / "content/cleric/ClericSpellCatalog.java")
    presentation = read(SERVER / "content/cleric/ClericSpellPresentation.java")
    casting = read(SERVER / "content/cleric/runtime/ClericSupportCasting.java")
    transaction = read(SERVER / "content/cleric/ClericCastTransaction.java")
    updater = read(SERVER / "GameStateUpdater.java")
    client = read(CLIENT / "orsc/mudclient.java")
    client_def = read(CLIENT / "com/openrsc/client/entityhandling/defs/ClericSpellDef.java")
    animation_catalog = read(CLIENT / "orsc/graphics/two/CombatEffectAnimationCatalog.java")
    build = read(ROOT / "Client_Base/build.xml")

    for spell, (effect_id, path, _width, _height, frames, max_size) in ANIMATIONS.items():
        require(f"public static final int CLERIC_{spell} = {effect_id};" in effect,
                f"server effect identity drift: {spell}")
        require(f"COMBAT_EFFECT_CLERIC_{spell} = {effect_id};" in client,
                f"client effect identity drift: {spell}")
        require(f"case {spell}:" in catalog and f"return {effect_id};" in catalog,
                f"spell presentation mapping drift: {spell}")
        require(f'"{path}"' in animation_catalog,
                f"client sheet mapping drift: {spell}")
        require(f", {frames}, {max_size});" in animation_catalog,
                f"compact animation metadata drift: {spell}")

    require("public static final int COMBAT_EFFECT_COUNT = 77;" in client,
            "client combat-effect capacity drift")
    require('"cleric-mend"' in client and '"cleric-respite"' in client,
            "client effect-name parity missing")
    require("case UNIFY:" in catalog and "return ClericSpellPresentation.NONE;" in catalog,
            "Unify must remain animationless")
    require("hasOnEntityAnimation" in presentation and "getOnEntityAnimationId" in presentation,
            "server presentation lacks recipient-animation semantics")
    require("hasOnEntityAnimation" in client_def and "getOnEntityAnimationId" in client_def,
            "client presentation lacks recipient-animation semantics")
    require("application.commit();\n\t\t\trecipient.getUpdateFlags().setCombatEffect" in casting,
            "animation must follow the successful gameplay application")
    require("return application.isUseful();" in casting,
            "animation wrapper no longer preserves ineffective-recipient filtering")
    require("if (useful.isEmpty())" in transaction,
            "wholly ineffective casts can reach the resource/visual commit")
    require("applicationCommit.run();" in casting and "return false" not in casting[
            casting.index("removeWithStateChange"):casting.index("removeWithStateChange") + 300],
            "resource boundary no longer encloses the visual/application commit")
    require("new Projectile" not in casting and "setProjectile" not in casting,
            "Cleric presentation incorrectly introduces a projectile")
    require("updatesMain.add((byte) combatEffect.getEffectType());" in updater,
            "maintained on-entity packet identity width drift")
    require("if (isCustomClient)" in updater[
            updater.index("Non authentic type 10") - 100:updater.index("Non authentic type 10") + 500],
            "authentic clients can receive custom on-entity effect updates")
    require('public static final String ON_ENTITY = "on-entity";' in animation_catalog,
            "on-entity animation category missing")
    require('PROJECTILE_MOVING' not in animation_catalog[
            animation_catalog.index('define(definitions, 67'):animation_catalog.index(
                'LinkedHashMap<Integer, Definition[]> sequences')],
            "a Cleric sheet is configured as a moving projectile")
    require('<include name="animations/**/*.png"' in build,
            "animation assets are omitted from client packaging")

    for spell, (_effect_id, path, width, height, _frames, _max_size) in ANIMATIONS.items():
        asset = ASSETS / path
        require(asset.is_file(), f"animation asset missing: {spell}: {path}")
        require(png_dimensions(asset) == (width, height),
                f"animation sheet dimensions drift: {spell}")


JAVA_FIXTURE = r"""
package test;

import java.util.Map;
import orsc.graphics.two.CombatEffectAnimationCatalog;

public final class ClericAnimationCatalogFixture {
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	public static void main(String[] args) {
		int[] ids = {67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77};
		String[] paths = {
			"heal-alt/heal-alt.png", "fist/fist.png", "cleanse 2/cleanse 2.png",
			"cleanse/cleanse.png", "wall-shield/Buff n Debuff P4 04.png",
			"greater-heal-alt/greater-heal-alt.png",
			"Holy VFX 03/Holy VFX 03(64x80).png", "thorns/thorns.png",
			"Holy VFX 04/Holy VFX 04(48x48).png", "sword-clash/sword-clash.png",
			"heart-pop/heart-pop.png"
		};
		int[] frames = {27, 14, 17, 17, 23, 19, 25, 21, 17, 11, 18};
		int[] sizes = {48, 48, 48, 48, 48, 48, 48, 32, 48, 48, 48};
		Map<Integer, CombatEffectAnimationCatalog.Definition> definitions =
			CombatEffectAnimationCatalog.getDefinitions();
		for (int index = 0; index < ids.length; index++) {
			CombatEffectAnimationCatalog.Definition definition = definitions.get(ids[index]);
			check(definition != null, "missing Cleric definition " + ids[index]);
			check(CombatEffectAnimationCatalog.ON_ENTITY.equals(definition.getCategory()),
				"non-entity category " + ids[index]);
			check(paths[index].equals(definition.getSheetPath()), "sheet drift " + ids[index]);
			check(definition.getRows() == 1 && definition.getFirstFrame() == 0,
				"unexpected sheet layout " + ids[index]);
			check(definition.getColumns() == frames[index]
				&& definition.getFrameCount() == frames[index], "frame drift " + ids[index]);
			check(definition.getMaxTargetSize() == sizes[index], "size drift " + ids[index]);
			check(CombatEffectAnimationCatalog.getSequence(ids[index]).length == 1,
				"Cleric effect should use one sheet " + ids[index]);
		}
	}
}
"""


def run_compiled_catalog_fixture() -> None:
    catalog = CLIENT / "orsc/graphics/two/CombatEffectAnimationCatalog.java"
    with tempfile.TemporaryDirectory(prefix="cleric-animation-") as temporary:
        base = Path(temporary)
        source = base / "test/ClericAnimationCatalogFixture.java"
        classes = base / "classes"
        source.parent.mkdir(parents=True)
        classes.mkdir()
        source.write_text(textwrap.dedent(JAVA_FIXTURE), encoding="utf-8")
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(classes), str(catalog), str(source)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes), "test.ClericAnimationCatalogFixture"],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_sources()
    run_compiled_catalog_fixture()
    print("Cleric C12 on-entity animation checks passed")


if __name__ == "__main__":
    main()
