#!/usr/bin/env python3
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
PLUGINS = ROOT / "server/plugins/com/openrsc/server/plugins"
POLICY = SERVER / "model/entity/player/TemporaryMaximumHits.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


HARNESS = r"""
package com.openrsc.server.model.entity.player;

public final class TemporaryMaximumHitsHarness {
    private static void equal(int actual, int expected, String message) {
        if (actual != expected) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        int ordinaryMaximum = 99;
        int tenPercentBonus = TemporaryMaximumHits.percentageBonus(
            ordinaryMaximum, 10);
        equal(tenPercentBonus, 10, "99 Hits at 10 percent did not round to 109");
        int boosted = TemporaryMaximumHits.reconcileBonus(
            ordinaryMaximum, ordinaryMaximum, 0, tenPercentBonus);
        equal(boosted, 109, "Brawn activation did not grant its temporary Hits");
        equal(TemporaryMaximumHits.healingCeiling(ordinaryMaximum, tenPercentBonus), 109,
            "Brawn did not raise the healing ceiling");

        int damaged = boosted - 10;
        int healed = Math.min(
            TemporaryMaximumHits.healingCeiling(ordinaryMaximum, tenPercentBonus),
            damaged + 20);
        equal(healed, 109, "damage followed by healing stopped at base Hits");

		int levelEightyMaximum = 80;
		int levelEightyBonus = TemporaryMaximumHits.percentageBonus(
			levelEightyMaximum, 10);
		equal(levelEightyBonus, 8, "80 Hits at 10 percent did not grant 8 Hits");
		int levelEightyBoosted = TemporaryMaximumHits.reconcileBonus(
			levelEightyMaximum, levelEightyMaximum, 0, levelEightyBonus);
		equal(levelEightyBoosted, 88, "level 80 Brawn activation ceiling was wrong");
		int levelEightyDamaged = 76;
		int levelEightyHealed = Math.min(
			TemporaryMaximumHits.healingCeiling(levelEightyMaximum, levelEightyBonus),
			levelEightyDamaged + 20);
		equal(levelEightyHealed, 88,
			"level 80 damage followed by healing stopped at base Hits");

        int expiredAtFull = TemporaryMaximumHits.reconcileBonus(
            healed, ordinaryMaximum, tenPercentBonus, 0);
        equal(expiredAtFull, 99, "expiration left temporary Hits inflated");
        equal(TemporaryMaximumHits.healingCeiling(ordinaryMaximum, 0), 99,
            "expiration left the healing ceiling inflated");

        int expiredAfterDamage = TemporaryMaximumHits.reconcileBonus(
            90, ordinaryMaximum, tenPercentBonus, 0);
        equal(expiredAfterDamage, 90,
            "expiration applied Brawn's bonus as a second damage loss");
        equal(TemporaryMaximumHits.persistedHits(109, ordinaryMaximum), 99,
            "logout persisted temporary health above the ordinary ceiling");
        equal(TemporaryMaximumHits.persistedHits(90, ordinaryMaximum), 90,
            "logout subtracted Brawn from already-damaged health");

        equal(TemporaryMaximumHits.reconcileBonus(0, ordinaryMaximum, 0, 10), 0,
            "changing a bonus revived a dead player");
        equal(TemporaryMaximumHits.healingCeiling(Integer.MAX_VALUE, 10),
            Integer.MAX_VALUE, "healing ceiling overflowed");
		equal(TemporaryMaximumHits.percentageBonus(Integer.MAX_VALUE, Integer.MAX_VALUE),
			Integer.MAX_VALUE, "percentage bonus overflowed");
    }
}
"""


def compile_and_run_policy() -> None:
    with tempfile.TemporaryDirectory(prefix="potion-brawn-healing-cap-") as temp:
        harness = Path(temp) / "TemporaryMaximumHitsHarness.java"
        harness.write_text(HARNESS, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-d",
                temp,
                str(POLICY),
                str(harness),
            ],
            check=True,
        )
        subprocess.run(
            [
                "java",
                "-cp",
                temp,
                "com.openrsc.server.model.entity.player.TemporaryMaximumHitsHarness",
            ],
            check=True,
        )


def check_runtime_integrations() -> None:
    player = (SERVER / "model/entity/player/Player.java").read_text(encoding="utf-8")
    skills = (SERVER / "model/Skills.java").read_text(encoding="utf-8")
    functions = (SERVER / "plugins/Functions.java").read_text(encoding="utf-8")
    eating = (PLUGINS / "authentic/itemactions/Eating.java").read_text(
        encoding="utf-8"
    )
    restoration = (SERVER / "event/rsc/impl/StatRestorationEvent.java").read_text(
        encoding="utf-8"
    )
    database = (SERVER / "database/GameDatabase.java").read_text(encoding="utf-8")

    require(
        "TemporaryMaximumHits.reconcileBonus(" in player
        and "TemporaryMaximumHits.percentageBonus(" in player
        and "public int getHealingMaximumHits()" in player
        and "TemporaryMaximumHits.persistedHits(" in player,
        "Player does not own Brawn activation, ceiling, expiration, and persistence policy",
    )
    sync_start = player.index("private void syncHerblawSkillPotionFamily(")
    sync_end = player.index("private int getHerblawSkillPotionBonus(", sync_start)
    require(
        "setTemporaryLevelAndMaxStat" not in player[sync_start:sync_end],
        "Brawn mutated the durable Hits maximum",
    )
    require(
        "player.getHealingMaximumHits()" in functions,
        "shared potion/drink healing still uses base Hits",
    )
    require(
        eating.count("player.getHealingMaximumHits()") >= 3,
        "food healing paths do not share the boosted ceiling",
    )
    require(
        "getEquipmentAdjustedNormalLevel(skill)" in restoration
        and "getEquipmentAdjustedNormalLevel(id)" in restoration,
        "passive Hits restoration does not use the temporary normal level",
    )
    require(
        "getSkills().normalize();" in player
        and "((Player) mob).getEquipmentAdjustedNormalLevel(skill)" in skills,
        "death normalization does not respect the active temporary Hits ceiling",
    )
    require(
        "skills[i].skillLevel = player.getPersistedSkillLevel(i);" in database,
        "logout/save does not strip temporary Brawn health through Player persistence",
    )
    require(
        "syncHitsEquipmentBonuses()" in player
        and "setTemporaryLevelAndMaxStat(Skill.HITS.id()" in player,
        "equipment maximum-Hits effects no longer contribute to the ordinary runtime ceiling",
    )

    healing_sources = (
        SERVER / "content/DivineGrace.java",
        SERVER / "content/Leach.java",
        SERVER / "content/Summoning.java",
        SERVER / "net/rsc/handlers/SpellHandler.java",
        PLUGINS / "authentic/misc/Bones.java",
        PLUGINS / "authentic/npcs/MonkHealer.java",
    )
    for path in healing_sources:
        require(
            "getHealingMaximumHits()" in path.read_text(encoding="utf-8"),
            f"healing path still bypasses Brawn ceiling: {path.relative_to(ROOT)}",
        )


def main() -> None:
    compile_and_run_policy()
    check_runtime_integrations()
    print("PASS: Potion of Brawn temporary healing ceiling and lifecycle validated")


if __name__ == "__main__":
    main()
