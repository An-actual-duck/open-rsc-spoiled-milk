#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
COMBAT_EFFECT = ROOT / "server/src/com/openrsc/server/model/entity/update/CombatEffect.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
SUMMON_CHARGE_ASSET_DIR = ROOT / "dev/myworld/assets/animations/On Player/summon"
SUMMON_ARRIVAL_ASSET_DIRS = [
    ROOT / "dev/myworld/assets/animations/on summon/summon-combat",
    ROOT / "dev/myworld/assets/animations/on summon/summon-support",
    ROOT / "dev/myworld/assets/animations/on summon/summon-utility",
]


def main() -> int:
    failures: list[str] = []
    client = CLIENT.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    server_effect = COMBAT_EFFECT.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")

    if "public static final int SUMMON = 26;" not in server_effect:
        failures.append("server CombatEffect.SUMMON must stay effect type 26 for the on-player charge effect")
    for constant in (
        "public static final int SUMMON_COMBAT = 27;",
        "public static final int SUMMON_SUPPORT = 28;",
        "public static final int SUMMON_UTILITY = 29;",
    ):
        if constant not in server_effect:
            failures.append(f"server CombatEffect missing {constant}")
    if "public static final int COMBAT_EFFECT_SUMMON = 26;" not in client:
        failures.append("client COMBAT_EFFECT_SUMMON must stay effect type 26 for the on-player charge effect")
    for constant in (
        "public static final int COMBAT_EFFECT_SUMMON_COMBAT = 27;",
        "public static final int COMBAT_EFFECT_SUMMON_SUPPORT = 28;",
        "public static final int COMBAT_EFFECT_SUMMON_UTILITY = 29;",
    ):
        if constant not in client:
            failures.append(f"client missing {constant}")
    combat_effect_count = re.search(r"public static final int COMBAT_EFFECT_COUNT = (\d+);", client)
    if not combat_effect_count or int(combat_effect_count.group(1)) < 29:
        failures.append("client combat effect range must include the summon effects through type 29")
    if 'public static final int spriteProjectileEffectBase = 53000;' not in client:
        failures.append("projectile effect base must stay clear of the expanded combat effect sprite range")
    if not re.search(r'combatEffectNames\s*=\s*new String\[\]\s*\{.*"summon".*"summon-combat".*"summon-support".*"summon-utility"', client, re.S):
        failures.append("client combatEffectNames must include summon charge and arrival effects")
    if 'getExternalAnimationFolder("on summon", effectName)' not in client:
        failures.append("client must load summon arrival animations from the on summon asset category")
    if "SUMMON_CHARGE_EFFECT_TICKS = 256" not in client:
        failures.append("summon charge effect should last the five-second charge window")
    for expected in (
        "SUMMON_CHARGE_LOOP_START_FRAME = 1",
        "SUMMON_CHARGE_LOOP_END_FRAME = 5",
        "SUMMON_CHARGE_FRAME_TICKS = 3",
        "SUMMON_CHARGE_LOOP_CYCLE_REDUCTION = 1",
        "SUMMON_CHARGE_LOOP_X_OFFSET = -5",
        "SUMMON_ARRIVAL_CIRCLE_Y_OFFSET_PERCENT = 34",
    ):
        if expected not in client:
            failures.append(f"summon charge animation loop missing {expected}")
    if "getSummonChargeFrameSequenceLength(duration, frameCount)" not in client:
        failures.append("summon charge should use a logical frame sequence for loop and finish timing")
    if "getSummonChargeLoopCycles(duration, frameCount)" not in client:
        failures.append("summon charge should explicitly control complete loop cycles")
    if "getCombatEffectScreenXOffset(effectType, frame, width)" not in client:
        failures.append("summon charge loop frames should support screen x alignment")
    if "getCombatEffectScreenYOffset(effectType, frame, height)" not in client:
        failures.append("summon arrival effects should support screen y alignment")
    if "getSummonChargeCurrentFrame(effectTime, duration, frameCount)" not in client:
        failures.append("summon charge must loop its hold frames instead of stretching all frames")
    if client.count("int frame = getCombatEffectCurrentFrame(") < 4:
        failures.append("all combat effect draw paths should use the shared current-frame helper")
    if "shouldHideSummonDuringArrival" not in client or "getSummonArrivalRevealFrame" not in client:
        failures.append("client must hide the summon model until the arrival animation reveal frame")
    if "if (effectType > 0)" not in packet_handler:
        failures.append("clearing a combat effect with type 0 must not detach and continue the old animation")
    if "new CombatEffect(owner, CombatEffect.SUMMON)" not in summoning:
        failures.append("summoning start must emit the on-player charge effect")
    if "new CombatEffect(owner, CombatEffect.NONE)" not in summoning:
        failures.append("summoning interruption/completion must clear the on-player charge effect")
    success_section = re.search(
        r"consumeSummonCosts\(owner, profile\);.*?spawnManualSummon\(owner, profile\);(.*?)owner\.message",
        summoning,
        re.S,
    )
    if success_section and "clearSummonChargeEffect(owner)" in success_section.group(1):
        failures.append("successful summoning should let the client finish the charge animation naturally")
    if "getSummonArrivalEffect(profile)" not in summoning:
        failures.append("summoned NPCs must receive a role-specific arrival combat effect")
    if "SUMMON_CHARGE_MS + gameTick - 1" not in summoning:
        failures.append("server summon charge should round up to the configured five-second window")
    if not SUMMON_CHARGE_ASSET_DIR.is_dir():
        failures.append("summon charge animation asset folder is missing")
    elif not list(SUMMON_CHARGE_ASSET_DIR.glob("*.png")):
        failures.append("summon charge animation asset folder has no png frames")
    for asset_dir in SUMMON_ARRIVAL_ASSET_DIRS:
        if not asset_dir.is_dir():
            failures.append(f"summon arrival animation asset folder is missing: {asset_dir.relative_to(ROOT)}")
        elif not list(asset_dir.glob("*.png")):
            failures.append(f"summon arrival animation asset folder has no png frames: {asset_dir.relative_to(ROOT)}")

    if failures:
        print("FAIL:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("PASS: summoning charge and arrival animations are wired")
    return 0


if __name__ == "__main__":
    sys.exit(main())
