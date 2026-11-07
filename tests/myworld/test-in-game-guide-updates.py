#!/usr/bin/env python3
"""Current mechanics/catalog cross-checks plus actual native client row/filter rendering."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def check_metadata():
    guide = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java").read_text()
    client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
    constants = (ROOT / "server/src/com/openrsc/server/constants/custom/MyWorldItemId.java").read_text()
    ids = {name: int(value) for name, value in re.findall(r"public static final int (\w+) = (\d+);", constants)}
    crafting = (ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java").read_text()
    recipes = re.findall(r'new HideArmorRecipe\(materialId, "([^"]+)", \d+, (\d+),\s*'
                         r'MyWorldItemId\.(\w+_COIF)', crafting)
    assert len(recipes) >= 6
    for family, level, item in recipes:
        if family == "King black dragon hide":
            continue
        pattern = f'addLeatherGuide({ids[item]}, "{level}", "{family} armor");'
        assert pattern in guide, "Missing recipe-backed guide family: " + pattern

    mechanism = (ROOT / "server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/Tourist_Trap_Mechanism.java").read_text()
    assert "getCurrentLevel(player, Skill.CRAFTING.id()) < 10" in mechanism
    assert "getCurrentLevel(player, Skill.SMITHING.id()) < 20" in mechanism
    assert "attachFeathersToPrototype(player, item1, item2);" in mechanism
    assert "makeDartTip(player, obj);" in mechanism
    assert '{"10 Crafting", "20 Smithing", "Ability to defeat a level 47 enemy"}' in client
    registrar = (ROOT / "server/plugins/com/openrsc/server/plugins/shared/QuestRewardRegistrar.java").read_text()
    tourist = registrar.split("// 43 - TOURIST_TRAP", 1)[1].split("// 44 - WATCHTOWER", 1)[0]
    assert "new XPReward(Skill.NONE, 600, 600)" in tourist
    assert "Choose two experience rewards from Agility, Crafting, Smithing and Thieving: (Lvl + 1)*150 each" in client

    offering = (ROOT / "server/src/com/openrsc/server/content/OfferingExperience.java").read_text()
    assert re.search(r"case BONES:\s*case ASHES:\s*return 100;", offering)
    for name, percent in (("BIG_BONES", 120), ("BAT_BONES", 130), ("DEMON_ASH", 140), ("DRAGON_BONES", 160)):
        assert re.search(r"case " + name + r":(?:(?!case).)*return " + str(percent) + r";", offering, re.S)
    assert "scaled by offering type" in guide
    print("PASS guide metadata matches current hide recipes, Tourist Trap dispatch/rewards and offering scaling")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--render", type=Path, required=True)
    args = parser.parse_args()
    check_metadata()
    java = Path(os.environ.get("JAVA_HOME", "/home/justin/jdk8u482-b08")) / "bin"
    jar = ROOT / "Client_Base/Open_RSC_Client.jar"
    assert jar.is_file(), "Build the actual client first"
    args.render.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="guide-updates-") as directory:
        classes = Path(directory)
        wire = classes / "schema5.bin"
        lines = (ROOT / "tests/myworld/fixtures/cleric-schema5-payload.hex").read_text().splitlines()
        wire.write_bytes(bytes.fromhex("".join(line for line in lines if not line.startswith("#"))))
        sources = [ROOT / "tests/myworld" / (name + ".java") for name in (
            "GuideUpdateClientFixture", "ClericThreeTierClientFixture", "ClericThreeTierClientRenderFixture", "ClericSpellbookUiFixture")]
        subprocess.run([str(java / "javac"), "-J-Xmx128m", "-cp", str(jar), "-d", str(classes)] + list(map(str, sources)), check=True)
        subprocess.run([str(java / "java"), "-Xmx512m", "-XX:+UseSerialGC", "-Djava.awt.headless=true",
                        "-Dspoiled_milk.opengl_sprite_overlay=true", "-Dspoiled_milk.opengl_sprite_overlay_mode=phased",
                        "-Dspoiledmilk.openglWorldUiReplay=true", "-cp", os.pathsep.join([str(classes), str(jar)]),
                        "orsc.GuideUpdateClientFixture", str(ROOT), str(wire), str(args.render.resolve())], check=True, cwd=ROOT)


if __name__ == "__main__":
    main()
