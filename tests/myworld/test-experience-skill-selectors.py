#!/usr/bin/env python3
"""Regression coverage for runtime-sized Experience Config skill selectors."""

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
INTERFACES = ROOT / "Client_Base/src/com/openrsc/interfaces/misc"
SELECTOR = INTERFACES / "ExperienceSkillSelector.java"
SELECTOR_USERS = (
    INTERFACES / "ExperienceConfigInterface.java",
    INTERFACES / "PointInterface.java",
    INTERFACES / "PointsToGpInterface.java",
)
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def compile_and_run_selector_regression() -> None:
    harness = """
package com.openrsc.interfaces.misc;

public final class ExperienceSkillSelectorRegression {
    public static void main(String[] args) {
        int[] skillIds = ExperienceSkillSelector.buildSelectableSkillIds(22);
        if (skillIds.length != 21) throw new AssertionError("expected 21 selectable skills");
        for (int row = 0; row < skillIds.length; row++) {
            int skillId = skillIds[row];
            if (skillId == 11) throw new AssertionError("retired Firemaking was selectable");
            int expected = row < 11 ? row : row + 1;
            if (skillId != expected) throw new AssertionError("row mapping drift at " + row);
            if (ExperienceSkillSelector.resolveSkillId(skillIds, row) != expected) {
                throw new AssertionError("selection mapping drift at " + row);
            }
        }
        if (ExperienceSkillSelector.resolveSkillId(skillIds, 17) != 18) {
            throw new AssertionError("Enchanting row did not map to skill 18");
        }
        if (ExperienceSkillSelector.resolveSkillId(skillIds, 18) != 19) {
            throw new AssertionError("Harvest row did not map to skill 19");
        }
        if (ExperienceSkillSelector.resolveSkillId(skillIds, 19) != 20) {
            throw new AssertionError("Summoning row did not map to skill 20");
        }
        if (ExperienceSkillSelector.resolveSkillId(skillIds, 20) != 21) {
            throw new AssertionError("Blessing row did not map to skill 21");
        }
        if (ExperienceSkillSelector.resolveSkillId(skillIds, -1) != -1
                || ExperienceSkillSelector.resolveSkillId(skillIds, 21) != -1) {
            throw new AssertionError("out-of-range rows must be rejected");
        }
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="experience-skill-selector-") as temp_dir:
        temp = Path(temp_dir)
        harness_path = temp / "ExperienceSkillSelectorRegression.java"
        harness_path.write_text(harness, encoding="utf-8")
        subprocess.run(
            ["javac", "-d", str(temp), str(SELECTOR), str(harness_path)],
            check=True,
        )
        subprocess.run(
            [
                "java",
                "-cp",
                str(temp),
                "com.openrsc.interfaces.misc.ExperienceSkillSelectorRegression",
            ],
            check=True,
        )


def audit_selector_users() -> None:
    for path in SELECTOR_USERS:
        source = path.read_text(encoding="utf-8")
        label = path.name
        require(
            "ExperienceSkillSelector.buildSelectableSkillIds(mudclient.skillCount)" in source,
            f"{label} must build its selector from the runtime skill count",
        )
        require(
            "Math.max(1, selectableSkillIds.length)" in source,
            f"{label} must size its Panel list from the selectable rows",
        )
        require(
            "for (int row = 0; row < selectableSkillIds.length; row++)" in source,
            f"{label} must populate only selectable rows",
        )
        require(
            "ExperienceSkillSelector.resolveSkillId(selectableSkillIds, selectedRow)" in source,
            f"{label} must map the displayed row back to a protocol skill ID",
        )
        require(
            "addScrollingList(x + 95, y + 34, 160, height - 40, 20, 2, false)" not in source,
            f"{label} still has the crashing fixed 20-entry capacity",
        )
        require(
            "mc.selectedSkill = index;" not in source,
            f"{label} still treats a displayed row as a skill ID",
        )


def audit_reset_control() -> None:
    interface_source = SELECTOR_USERS[0].read_text(encoding="utf-8")
    client_source = CLIENT.read_text(encoding="utf-8")
    require(
        "mc.resetExperienceCounter();" in interface_source,
        "Experience Config Reset must use the complete counter reset",
    )
    require(
        "mc.getRecentSkill()" not in interface_source
        and "mc.setPlayerStatXpGained(mc.selectedSkill" not in interface_source,
        "Experience Config Reset must not clear only the currently displayed skills",
    )
    required_reset_steps = (
        "public void resetExperienceCounter()",
        "this.totalXpGainedStartTime = resetTime;",
        "this.playerXpGainedTotal = 0;",
        "this.xpPerHour = 0;",
        "this.xpPerHourCount = 0;",
        "Arrays.fill(this.playerStatXpGained, 0L);",
        "Arrays.fill(this.xpGainedStartTime, resetTime);",
    )
    for step in required_reset_steps:
        require(step in client_source, f"Experience counter reset is missing: {step}")


def main() -> None:
    compile_and_run_selector_regression()
    audit_selector_users()
    audit_reset_control()
    print("PASS: Experience Config safely maps 22 skill IDs and resets all counters")


if __name__ == "__main__":
    main()
